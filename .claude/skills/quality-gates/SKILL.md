---
name: quality-gates
description: Pirâmide de testes e quality gates do SRM Credit Engine — unitário, integração, contrato (OpenAPI), cobertura JaCoCo, carga e estresse com k6, e checagem de integridade pós-estresse. Use ao configurar o pom/CI, escrever ContractIT, criar scripts em perf/ ou gerar o relatório em docs/perf/.
---

# Quality gates

## Princípio

Cada camada existe para pegar uma classe de erro diferente. **Não escreva teste para inflar número.** Cobertura é piso, não objetivo. Teste sem asserção relevante é proibido.

| Camada | Pega |
|---|---|
| Unitário (`*Test`) | erro de cálculo, arredondamento, regra de strategy |
| Integração (`*IT`) | transação, idempotência, locking, trigger, SQL do extrato |
| Contrato (`ContractIT`) | API divergindo do OpenAPI publicado |
| E2E (Playwright, `frontend/e2e/`) | painel, liquidação e grid quebrados de ponta a ponta |
| Carga | regressão de latência no uso esperado |
| Estresse | falha suja e **corrupção de dados sob pressão** |

Não coloque limite de tempo em teste unitário ou de integração: fica instável com a JVM fria. Latência se mede na carga.

## 1. Maven: Surefire, Failsafe e JaCoCo

- Surefire roda `**/*Test.java`; Failsafe roda `**/*IT.java` (fases `integration-test` e `verify`).
- `jacoco-maven-plugin` (0.8.12):
  - `prepare-agent` (unit → `jacoco.exec`) e `prepare-agent-integration` (IT → `jacoco-it.exec`);
  - `merge` dos dois em `jacoco-merged.exec` na fase `post-integration-test`;
  - `report` sobre o merged → `target/site/jacoco-merged/`;
  - `check` sobre o merged na fase `verify`, com as regras:

```xml
<rules>
  <rule>
    <element>BUNDLE</element>
    <limits>
      <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.70</minimum></limit>
    </limits>
  </rule>
  <rule>
    <element>PACKAGE</element>
    <includes><include>br.com.srm.creditengine.domain.pricing</include></includes>
    <limits>
      <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.90</minimum></limit>
      <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.90</minimum></limit>
    </limits>
  </rule>
</rules>
```

- Exclusões permitidas **só** para bootstrap: `**/CreditEngineApplication*` e `**/config/**`. Liste-as em DECISIONS.md. Excluir service ou controller para bater a meta é proibido.
- Se a meta não bater, escreva o teste que falta para um comportamento real (caminho de erro, borda). Nunca baixe a meta.

## 2. Teste de contrato (`ContractIT`)

Dependência: `com.atlassian.oai:swagger-request-validator-core` (scope test).

1. `@SpringBootTest(webEnvironment = RANDOM_PORT)` com Testcontainers.
2. Baixa `/v3/api-docs` e cria o validador com `OpenApiInteractionValidator.createForInlineApiSpecification(json).build()`.
3. Executa **requisições reais** e valida request e response contra o spec (`report.hasErrors()` deve ser `false`; em caso de falha, imprima as mensagens). Cenários mínimos:
   - quote `200` (C1) e `422` (vencimento passado);
   - settlement `201`, `200` replay, `400` sem chave, `404`, `409` e `422` chave reutilizada;
   - statement `200` paginado e `400` (`from > to`);
   - fx-rates `201`, current `200` e refresh `503` (provedor forçado a falhar).
4. **Cobertura do contrato:** para cada operação, verifica que os status da tabela da skill `api-conventions` estão declarados no spec.

## 3. Carga — `perf/load.js`

Objetivo: provar que o uso esperado da mesa de operações cabe com folga. Executor `constant-arrival-rate`, 30 s de aquecimento (cenário separado, descartado), depois 2 min:

| Cenário | Taxa | Threshold |
|---|---|---|
| `POST /api/pricing/quote` (BRL e USD alternados) | 20 req/s | `p(95) < 150` ms, `p(99) < 300` ms |
| `GET /api/settlements` com filtros, `size=20` | 10 req/s | `p(95) < 100` ms |
| `POST /api/settlements` (recebível novo e chave nova por iteração; recebíveis criados no `setup()`) | 5 req/s | `p(95) < 300` ms |
| todos | — | `http_req_failed: rate == 0` (com `http.expectedStatuses(200, 201)`) e `checks: rate == 1` |

Antes de rodar, popule o extrato com 10.000 liquidações via `perf/seed.sql` (para medir o SQL com volume, não com a tabela vazia). O seed distribui `settled_at` em 12 meses e entre os cedentes, respeita `present_value_brl + discount = face_value` e termina com `ANALYZE`.

```bash
docker compose up -d --build
docker compose exec -T db psql -U srm -d srm < perf/seed.sql
docker compose --profile perf run --rm k6 run \
  --summary-export=/results/load-summary.json /scripts/load.js | tee docs/perf/load-output.txt
```

## 4. Estresse — `perf/stress.js`

Objetivo: provar que o sistema **falha limpo** sob pressão e registrar até onde ele aguentou. Não é para passar thresholds de latência, e **não é preciso forçar uma quebra**.

- Quote: `ramping-arrival-rate` em degraus de 1 min, 50 → 100 → 200 → 400 → 800 req/s (`preAllocatedVUs: 200`, `maxVUs: 1000`). Exporte a série temporal com `--out csv=/results/stress.csv`. Esse arquivo fica fora do git (passa de 100 MB); gere dele o agregado versionado `docs/perf/stress-by-step.csv`, com vazão, p95 e % de erro por degrau.
- Em paralelo, um cenário de **liquidação hostil**: para cada recebível criado no `setup()`, 3 requisições simultâneas com a **mesma** chave mais 1 com chave **diferente**. Aceite só `201`, `200` (replay), `409` e, sob saturação, `503`/`500`. Conte cada status.
- Ponto de quebra = primeiro degrau em que `p95 > 1 s` **ou** a taxa de erro passa de 1%. Registre o degrau e a vazão máxima sustentada. Se nenhum degrau quebrar, registre "não atingido até 800 req/s". Os dois resultados são válidos. Não aumente a carga para procurar a quebra.
- Depois do estresse, rode a checagem de integridade e um health check (o sistema precisa se recuperar sozinho):

```bash
docker compose --profile perf run --rm k6 run --out csv=/results/stress.csv \
  --summary-export=/results/stress-summary.json /scripts/stress.js | tee docs/perf/stress-output.txt
docker compose exec -T db psql -U srm -d srm -f - < perf/integrity.sql | tee docs/perf/integrity-output.txt
curl -fsS http://localhost:8080/actuator/health
```

`perf/integrity.sql` (todas as consultas devem retornar **0**):
- recebíveis com mais de uma liquidação;
- recebíveis `SETTLED` sem liquidação, e liquidações cujo recebível não está `SETTLED` (liquidação pela metade);
- `idempotency_key` duplicada;
- liquidação em USD sem `fx_rate_id`/`fx_rate`;
- liquidação com `present_value_brl + discount <> face_value`.

## 5. Relatório — `docs/perf/REPORT.md`

Gerado **a partir dos arquivos de saída reais**, nunca de memória. Seções:

1. **Ambiente:** data, commit (`git rev-parse --short HEAD`), CPU, RAM, SO, recursos do Docker (`docker info`) e um aviso de que os números são de máquina local.
2. **Carga:** tabela por cenário com taxa, p50, p95, p99, erros e threshold (✅/❌), copiada do `load-summary.json`.
3. **Estresse:** tabela por degrau (req/s alvo, vazão real, p95, % erro) copiada de `stress-by-step.csv`, ponto de quebra ou "não atingido", comportamento na saturação (quais status apareceram) e recuperação (health após o teste).
4. **Integridade pós-estresse:** saída do `integrity.sql`, com cada consulta = 0.
5. **Gargalo observado e próximo passo:** uma ou duas frases (ex.: pool do Hikari em 10 conexões; aumentar ou aplicar backpressure). Só o que os dados mostram.
6. **Como reproduzir:** os comandos acima.

O README ganha a seção "Qualidade e testes" com a tabela de camadas, a cobertura real (do JaCoCo) e um link para `docs/perf/REPORT.md`. Não repita números no README além de um resumo de uma linha por teste.
