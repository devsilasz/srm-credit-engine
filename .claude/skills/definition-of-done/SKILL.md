---
name: definition-of-done
description: Checklist final de entrega do SRM Credit Engine — rodar ao concluir o projeto ou antes de declarar qualquer etapa grande pronta. Cobre build, docker compose de um comando, smoke tests, documentação e anti-padrões eliminatórios.
---

# Definition of Done

Execute cada item e **mostre a evidência** (comando e saída). Marque ✅ só com evidência; senão, use ❌ e corrija. O que for automatizável fica em `scripts/verify-all.sh`; o restante é conferido à mão e registrado em `docs/PROGRESS.md`.

## 1. Build e testes
- [ ] `cd backend && ./mvnw verify` verde (inclui Spotless e Testcontainers)
- [ ] `GoldenCasesTest`: 3/3
- [ ] Teste concorrente de locking: 1 sucesso e 1 `409`, repetido 5x sem flakiness (`-Dsurefire.rerunFailingTestsCount=0`)
- [ ] Teste de idempotência: mesma chave → 1 linha; chave reutilizada com payload diferente → `422`
- [ ] Teste de resiliência: provedor lento → `503` no refresh; taxa vencida → liquidação USD `503` com 0 linhas
- [ ] `ContractIT` verde
- [ ] JaCoCo: ≥ 70% de linhas no projeto e ≥ 90% de linhas e branches em `domain.pricing` (mostrar os números de `backend/target/site/jacoco-merged/jacoco.csv`)
- [ ] `cd frontend && npm ci && npm run lint && npm run build` verde

## 1b. Carga e estresse (skill `quality-gates`)
- [ ] `docs/perf/load-summary.json` existe e todos os thresholds passaram
- [ ] `docs/perf/stress-summary.json` e `stress-by-step.csv` existem; ponto de quebra registrado ou "não atingido até 800 req/s"
- [ ] `docs/perf/integrity-output.txt`: todas as consultas = 0
- [ ] Health `UP` depois do estresse, sem restart manual
- [ ] `docs/perf/REPORT.md` preenchido a partir desses arquivos (conferir: cada número do relatório aparece em algum arquivo de saída)

## 2. Um comando
- [ ] `docker compose down -v && docker compose up -d --build` sem `.env` presente
- [ ] `docker compose ps`: os 3 serviços `healthy`/`running` em até 120 s
- [ ] Nenhuma etapa manual (migrations e seed de cedentes rodam sozinhas; a taxa de câmbio entra pelo refresh no startup)

## 3. Smoke test (curl)
```bash
API=http://localhost:8080
curl -fsS $API/actuator/health
DUE=$(date -d "+3 months" +%F 2>/dev/null || date -v+3m +%F)
curl -fsS -X POST $API/api/pricing/quote -H 'Content-Type: application/json' \
  -d "{\"faceValue\":\"100000.00\",\"dueDate\":\"$DUE\",\"type\":\"DUPLICATA_MERCANTIL\",\"paymentCurrency\":\"BRL\"}"
# esperado: presentValue "92859.94", discount "7140.06"
# repetir com paymentCurrency USD → "17094.67" (taxa seed 5.4321)
```
- [ ] Criar recebível → liquidar com `Idempotency-Key: $(uuidgen)` → `201`; repetir → `200` com o mesmo id
- [ ] Liquidar o mesmo recebível com outra chave → `409`
- [ ] Sem header → `400` `application/problem+json`
- [ ] `GET $API/api/settlements?currency=BRL&page=0&size=5` → paginado
- [ ] `docker compose exec db psql -U srm -d srm -c "update settlement set present_value_brl=0"` (ajuste o nome da coluna ao schema real) → **erro do trigger**
- [ ] `curl -fsSL $API/swagger-ui.html -o /dev/null -w '%{http_code}'` → `200`
- [ ] `curl -fsS $API/actuator/prometheus | grep credit_engine_` → métricas presentes
- [ ] `curl -fsS http://localhost:3000 -o /dev/null -w '%{http_code}'` → `200`
- [ ] Frontend: Playwright em `frontend/e2e/` verde (simulação, liquidação, retry com a mesma intenção e atualização do grid); screenshot em `docs/screenshots/`

## 4. Anti-padrões eliminatórios (grep)
- [ ] Sem `double`/`float` monetário (checagem da skill `monetary-precision`)
- [ ] Sem `catch` vazio: `grep -rnE "catch \([^)]*\) *\{ *\}" backend/src/main`
- [ ] Sem concatenação de SQL: `grep -rnE "\"(SELECT|INSERT|UPDATE).*\" *\+" backend/src/main`
- [ ] Nenhuma resposta de erro com `200`
- [ ] Nenhuma classe ou interface vazia ou só repassadora: revise `application/` e `infrastructure/`

## 5. Documentação
- [ ] `README.md`: um comando, URLs, stack justificada, C4 L1/L2, ER, mapa do repo e seção "Qualidade e testes" (camadas, cobertura real e link para `docs/perf/REPORT.md`)
- [ ] `SPEC.md` ≤ 2 páginas: premissas, perguntas, precisão e resumo dos critérios
- [ ] `REVIEW.md`: ordem de severidade do `CLAUDE.md`, impacto e correção, mais prevenção sistêmica
- [ ] `DECISIONS.md`: cortes com motivo (auth, EDA, lote atômico, testes no docker build, etc.)
- [ ] `AI_USAGE.md`: esqueleto com TODOs e "Log do agente" só com fatos reais. **Nenhum caso inventado.**
- [ ] `docs/acceptance/`: coluna `Teste` preenchida; checagem de cobertura da skill `acceptance-criteria` sem IDs órfãos
- [ ] `docs/prs/`: uma descrição por branch

## 6. Relatório final
Imprima: tabela de critérios (✅/❌/MANUAL), lista de premissas marcadas "a revisar pelo autor" e as 5 perguntas mais prováveis na defesa sobre este código, cada uma com o arquivo e a linha onde está a resposta.
