#!/usr/bin/env bash
# Mede a cobertura de cada camada de teste em execuções separadas e gera docs/coverage/REPORT.md.
# O ambiente do compose em execução é parado durante o E2E e religado no fim, com os dados intactos.
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=docs/coverage
TARGET=backend/target
TOOLS=$TARGET/jacoco-tools
JACOCO=0.8.12
COMPOSE=(docker compose -p srm-coverage -f docker-compose.yml -f docker-compose.coverage.yml)

was_running=false
[ -n "$(docker compose ps -q 2>/dev/null)" ] && was_running=true

restore() {
  "${COMPOSE[@]}" --profile e2e down -v --rmi local --remove-orphans >/dev/null 2>&1 || true
  if [ "$was_running" = true ]; then
    docker compose up -d --wait >/dev/null 2>&1 || echo "AVISO: não consegui religar o compose"
  fi
}
trap restore EXIT

# Sempre recompila: uma IDE aberta pode ter gravado em target/classes com outro compilador, e o
# Maven responderia "Nothing to compile". As tabelas de linha mudam, e com elas os percentuais.
rm -rf "$TARGET/classes" "$TARGET/test-classes" "$TARGET/maven-status"

mkdir -p "$OUT"
echo "camada,testes" > "$OUT/tests-by-layer.csv"

# measure <camada> <argumentos do Maven>: roda só aquela camada e guarda o CSV do JaCoCo.
measure() {
  local layer="$1" log="$TARGET/coverage-$1.log"
  shift
  echo "== $layer"
  mkdir -p "$TARGET"
  rm -f "$TARGET"/*.exec
  rm -rf "$TARGET/site"
  (cd backend && ./mvnw -B verify "$@") > "$log.tmp" 2>&1 || { tail -30 "$log.tmp"; exit 1; }
  mv "$log.tmp" "$log"
  cp "$TARGET/site/jacoco-merged/jacoco.csv" "$OUT/$layer.csv"
  # Soma as linhas de resumo do Surefire e do Failsafe: a medição total tem as duas.
  local tests
  tests=$(grep -E '^\[[A-Z]+\] Tests run: [0-9]+, .*[0-9]$' "$log" \
    | sed -E 's/.*Tests run: ([0-9]+),.*/\1/' | awk '{ total += $1 } END { print total + 0 }')
  echo "$layer,$tests" >> "$OUT/tests-by-layer.csv"
  echo "   $tests testes"
}

ONLY_IT=(-Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false)
# Medições parciais não atingem a meta do gate, e nem precisam: o gate vale para a medição total.
PARTIAL=(-Djacoco.haltOnFailure=false)

measure unit -DskipITs "${PARTIAL[@]}"
# Com só a exclusão, o Failsafe rodaria todas as classes de teste, inclusive os unitários.
measure integration "${ONLY_IT[@]}" '-Dit.test=*IT,!ContractIT' "${PARTIAL[@]}"
measure contract "${ONLY_IT[@]}" -Dit.test=ContractIT "${PARTIAL[@]}"
cp "$TARGET/contract-operations.csv" "$OUT/contract-operations.csv"
measure all

echo "== e2e"
rm -rf "$TOOLS"
mkdir -p "$TOOLS/screenshots"
chmod -R 777 "$TOOLS"
(cd backend && ./mvnw -B -q dependency:copy "-Dartifact=org.jacoco:org.jacoco.agent:$JACOCO:jar:runtime" \
  -DoutputDirectory=target/jacoco-tools -Dmdep.stripVersion=true -Dmdep.stripClassifier=true)
(cd backend && ./mvnw -B -q dependency:copy "-Dartifact=org.jacoco:org.jacoco.cli:$JACOCO:jar:nodeps" \
  -DoutputDirectory=target/jacoco-tools -Dmdep.stripVersion=true -Dmdep.stripClassifier=true)
mv "$TOOLS/org.jacoco.agent-runtime.jar" "$TOOLS/jacocoagent.jar" 2>/dev/null || mv "$TOOLS/org.jacoco.agent.jar" "$TOOLS/jacocoagent.jar"
mv "$TOOLS/org.jacoco.cli-nodeps.jar" "$TOOLS/jacococli.jar" 2>/dev/null || mv "$TOOLS/org.jacoco.cli.jar" "$TOOLS/jacococli.jar"
chmod -R 777 "$TOOLS"

[ "$was_running" = true ] && docker compose stop >/dev/null
"${COMPOSE[@]}" up -d --build --wait
"${COMPOSE[@]}" --profile e2e build e2e
"${COMPOSE[@]}" --profile e2e run --rm --no-deps e2e
echo "e2e,1" >> "$OUT/tests-by-layer.csv"
# O agente só grava o .exec quando a JVM termina.
"${COMPOSE[@]}" stop backend

# As classes vêm da imagem: o JaCoCo exige exatamente os bytes que rodaram no contêiner.
"${COMPOSE[@]}" cp backend:/app/app.jar "$TOOLS/app.jar"
(cd "$TOOLS" && jar xf app.jar BOOT-INF/classes)
rm -f "$TOOLS"/BOOT-INF/classes/br/com/srm/creditengine/CreditEngineApplication*.class
rm -rf "$TOOLS"/BOOT-INF/classes/br/com/srm/creditengine/infrastructure/config
java -jar "$TOOLS/jacococli.jar" report "$TOOLS/e2e.exec" \
  --classfiles "$TOOLS/BOOT-INF/classes" --csv "$OUT/e2e.csv" >/dev/null

echo "== relatório"
git rev-parse --short HEAD > "$OUT/commit.txt"
date -u +%FT%TZ > "$OUT/date.txt"
docker run --rm -v "$PWD:/work" -w /work python:3.12-alpine python scripts/coverage_report.py
