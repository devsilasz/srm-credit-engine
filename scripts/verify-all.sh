#!/usr/bin/env bash
# Critério de saída do projeto: executa tudo, em ordem, a partir de um banco zerado, e para na
# primeira falha. `--skip-perf` serve só para depurar; a conclusão exige uma execução sem flags.
set -uo pipefail
cd "$(dirname "$0")/.."

API=http://localhost:8080
FRONT=http://localhost:3000
SKIP_PERF=false
[ "${1:-}" = "--skip-perf" ] && SKIP_PERF=true

current_step=""
step() {
  current_step="$1"
  echo
  echo "=== $current_step"
}
fail() {
  echo
  echo "FALHOU no passo: $current_step"
  echo "Motivo: $1"
  exit 1
}
trap 'fail "comando terminou com erro (linha $LINENO)"' ERR
set -E

# assert_eq <descrição> <obtido> <esperado>
assert_eq() {
  # A expansão de chaves do bash 3.2 pode multiplicar um argumento e fazer $2 e $3 coincidirem.
  [ $# -eq 3 ] || fail "assert_eq recebeu $# argumentos em vez de 3: $*"
  if [ "$2" = "$3" ]; then
    echo "ok   $1: $2"
  else
    fail "$1: esperado [$3], obtido [$2]"
  fi
}

# Lê um campo string ou numérico de um JSON de uma linha, sem depender de jq na máquina.
json_field() {
  echo "$1" | sed -nE "s/.*\"$2\":\"?([^\",}]*)\"?.*/\1/p"
}

psql_value() {
  docker compose exec -T db psql -v ON_ERROR_STOP=1 -At -U srm -d srm -c "$1"
}

post_json() {
  curl -sS -o "$BODY" -w '%{http_code} %{content_type}' -X POST "$API$1" \
    -H 'Content-Type: application/json' "${@:3}" -d "$2"
}

# Temporários ficam no projeto, e somem no fim.
WORK=.verify-tmp
BODY="$WORK/body"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

# ---------------------------------------------------------------------------------------------
step "1. Backend: ./mvnw verify (golden cases, integração, contrato, cobertura, Spotless)"
(cd backend && ./mvnw -B verify) || fail "./mvnw verify"

step "1b. Frontend: lint e build em contêiner"
docker build -q --target build -t srm-frontend-check ./frontend >/dev/null \
  || fail "npm ci ou npm run build"
docker run --rm srm-frontend-check npm run lint || fail "npm run lint"

# ---------------------------------------------------------------------------------------------
step "2. Compose: down -v e up com um comando, sem .env"
[ ! -f .env ] || fail "existe um .env local: a verificação precisa rodar sem ele"
docker compose down -v --remove-orphans
start=$(date +%s)
docker compose up -d --build --wait || fail "docker compose up"
echo "ok   serviços healthy em $(( $(date +%s) - start )) s"
docker compose ps --format '{{.Service}}: {{.Status}}'

# ---------------------------------------------------------------------------------------------
step "3. Smoke test"
assert_eq "health" "$(json_field "$(curl -fsS $API/actuator/health)" status)" "UP"

# Datas calculadas pelo Postgres: + 3 meses cai no fim do mês como no LocalDate.plusMonths do backend.
DUE=$(psql_value "SELECT ((now() AT TIME ZONE 'America/Sao_Paulo')::date + interval '3 months')::date")
QUOTE="\"faceValue\":\"100000.00\",\"dueDate\":\"$DUE\",\"type\":\"DUPLICATA_MERCANTIL\""

C1=$(curl -fsS -X POST $API/api/pricing/quote -H 'Content-Type: application/json' \
  -d "{$QUOTE,\"paymentCurrency\":\"BRL\"}")
assert_eq "quote C1 presentValue" "$(json_field "$C1" presentValue)" "92859.94"
assert_eq "quote C1 discount" "$(json_field "$C1" discount)" "7140.06"

C3=$(curl -fsS -X POST $API/api/pricing/quote -H 'Content-Type: application/json' \
  -d "{$QUOTE,\"paymentCurrency\":\"USD\"}")
assert_eq "quote C3 presentValue" "$(json_field "$C3" presentValue)" "17094.67"

RECEIVABLE=$(curl -fsS -X POST $API/api/receivables -H 'Content-Type: application/json' \
  -d "{\"assignorId\":1,\"type\":\"DUPLICATA_MERCANTIL\",\"faceValue\":\"100000.00\",\"dueDate\":\"$DUE\"}")
RECEIVABLE_ID=$(json_field "$RECEIVABLE" id)
SETTLE="{\"receivableId\":$RECEIVABLE_ID,\"paymentCurrency\":\"BRL\"}"
KEY=$(uuidgen | tr 'A-Z' 'a-z')

FIRST=$(post_json /api/settlements "$SETTLE" -H "Idempotency-Key: $KEY")
FIRST_BODY=$(cat "$BODY")
assert_eq "primeira liquidação" "${FIRST%% *}" "201"
SECOND=$(post_json /api/settlements "$SETTLE" -H "Idempotency-Key: $KEY")
SECOND_BODY=$(cat "$BODY")
assert_eq "mesma chave" "${SECOND%% *}" "200"
assert_eq "replay devolve o mesmo corpo" "$SECOND_BODY" "$FIRST_BODY"
assert_eq "uma linha só no banco" \
  "$(psql_value "SELECT count(*) FROM settlement WHERE receivable_id = $RECEIVABLE_ID")" "1"

OTHER_KEY=$(uuidgen | tr 'A-Z' 'a-z')
CONFLICT=$(post_json /api/settlements "$SETTLE" -H "Idempotency-Key: $OTHER_KEY")
assert_eq "outra chave no mesmo recebível" "${CONFLICT%% *}" "409"

MISSING=$(post_json /api/settlements "$SETTLE")
assert_eq "sem Idempotency-Key" "$MISSING" "400 application/problem+json"

UNKNOWN=$(post_json /api/settlements '{"receivableId":999999999,"paymentCurrency":"BRL"}' \
  -H "Idempotency-Key: $(uuidgen | tr 'A-Z' 'a-z')")
assert_eq "recebível inexistente" "$UNKNOWN" "404 application/problem+json"

PAGE=$(curl -fsS "$API/api/settlements?currency=BRL&page=0&size=5")
assert_eq "extrato paginado" "$(json_field "$PAGE" size)" "5"

if docker compose exec -T db psql -v ON_ERROR_STOP=1 -U srm -d srm \
  -c "UPDATE settlement SET present_value_brl = 0" >/dev/null 2>"$WORK/trigger"; then
  fail "o UPDATE direto em settlement deveria ter sido recusado pelo trigger"
fi
grep -q "tabela imutável" "$WORK/trigger" || { cat "$WORK/trigger"; fail "erro inesperado no UPDATE direto"; }
echo "ok   UPDATE direto em settlement recusado pelo trigger"

assert_eq "swagger" "$(curl -fsSL $API/swagger-ui.html -o /dev/null -w '%{http_code}')" "200"
curl -fsS $API/actuator/prometheus | grep -q '^credit_engine_settlements_total' \
  || fail "métrica credit_engine_settlements_total ausente"
echo "ok   métricas de negócio expostas"
assert_eq "frontend" "$(curl -fsS $FRONT -o /dev/null -w '%{http_code}')" "200"
assert_eq "proxy /api do Nginx" "$(curl -fsS $FRONT/api/assignors -o /dev/null -w '%{http_code}')" "200"
# O navegador só envia Origin em POST: é esse caminho que o CORS do backend pode recusar.
# Em variável: dentro de "$(...)" como argumento, o bash 3.2 expandiria as chaves deste JSON.
ORIGIN_STATUS=$(curl -sS -X POST $FRONT/api/pricing/quote \
  -H 'Content-Type: application/json' -H "Origin: $FRONT" \
  -d "{$QUOTE,\"paymentCurrency\":\"BRL\"}" -o /dev/null -w '%{http_code}')
assert_eq "POST pelo Nginx com Origin do navegador" "$ORIGIN_STATUS" "200"

step "3b. E2E: Playwright contra o compose"
docker compose --profile e2e run --rm --build e2e || fail "fluxo do operador no Playwright"

# ---------------------------------------------------------------------------------------------
if [ "$SKIP_PERF" = true ]; then
  step "4. Carga e estresse: PULADO (--skip-perf). Esta execução não conclui o projeto."
else
  step "4. Carga, estresse, integridade e relatório (recria o banco)"
  perf/run.sh || fail "perf/run.sh: threshold, integridade ou health reprovados"
  [ -s docs/perf/REPORT.md ] || fail "docs/perf/REPORT.md não foi gerado"
fi

# ---------------------------------------------------------------------------------------------
step "5. Cobertura real, lida do jacoco.csv"
CSV=backend/target/site/jacoco-merged/jacoco.csv
[ -f "$CSV" ] || fail "$CSV não existe: o gate do JaCoCo pode ter pulado sem medir"
awk -F, '
  NR > 1 {
    line_missed += $8; line_covered += $9
    if ($2 ~ /domain\.pricing$/) {
      pl_missed += $8; pl_covered += $9; pb_missed += $6; pb_covered += $7
    }
  }
  END {
    project = line_covered / (line_covered + line_missed)
    pricing_lines = pl_covered / (pl_covered + pl_missed)
    pricing_branches = pb_covered / (pb_covered + pb_missed)
    printf "projeto, linhas: %.1f%%\ndomain.pricing, linhas: %.1f%%\ndomain.pricing, branches: %.1f%%\n",
      project * 100, pricing_lines * 100, pricing_branches * 100
    exit !(project >= 0.70 && pricing_lines >= 0.90 && pricing_branches >= 0.90)
  }' "$CSV" || fail "cobertura abaixo da meta"

# ---------------------------------------------------------------------------------------------
step "6. Anti-padrões eliminatórios"
# forbid <descrição> <padrão> <caminhos...>: qualquer ocorrência reprova.
forbid() {
  local description="$1" pattern="$2"
  shift 2
  if grep -rnE "$pattern" "$@"; then
    fail "$description"
  fi
  echo "ok   sem $description"
}
forbid "double ou float no backend" '\b(double|float|Double|Float)\b' backend/src/main
forbid "BigDecimal a partir de literal numérico" 'new BigDecimal\([0-9]|BigDecimal\.valueOf\([0-9]*\.[0-9]' backend/src
forbid "HALF_UP ou ROUND_ deprecated" 'HALF_UP|ROUND_' backend/src/main
forbid "parseFloat ou toFixed no frontend" 'parseFloat|toFixed' frontend/src
forbid "catch vazio" 'catch \([^)]*\) *\{ *\}' backend/src/main
forbid "SQL concatenado" '"(SELECT|INSERT|UPDATE).*" *\+' backend/src/main
if git ls-files | grep -E ' [0-9]+(\.|/|$)'; then
  fail "cópia de conflito do iCloud versionada"
fi
echo "ok   sem cópia de conflito versionada"

echo
if [ "$SKIP_PERF" = true ]; then
  echo "Tudo verde, MAS sem carga e estresse (--skip-perf). Rode sem flags para concluir."
  exit 2
fi
echo "VERIFICAÇÃO COMPLETA: todos os passos passaram."
