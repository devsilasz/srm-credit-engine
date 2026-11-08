# Não-funcionais — `AC-API`, `AC-SEC`, `AC-PERF`, `AC-OPS`

## API

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-API-01 | qualquer erro | resposta | `Content-Type: application/problem+json` com `type`, `title`, `status`, `detail` e `code` | Funcional | PENDENTE |
| AC-API-02 | exceção inesperada | resposta | `500` genérico sem stack trace nem SQL no corpo; log ERROR com stack trace | Segurança | PENDENTE |
| AC-API-03 | — | `GET /v3/api-docs` | cada endpoint documenta todos os status da tabela da skill `api-conventions` | Operação | PENDENTE (`ContractIT`) |
| AC-API-05 | API rodando | requisições reais de sucesso e de erro de cada endpoint | request e response validam contra o OpenAPI publicado, com 0 violações | Funcional | PENDENTE (`ContractIT`) |
| ~~AC-API-06~~ | ~~`docs/openapi.json` versionado~~ | ~~o spec gerado muda~~ | ~~`ContractIT` falha até o snapshot ser atualizado~~ | Operação | MANUAL: removido; o enunciado não pede, e AC-API-05 já valida o contrato real |
| AC-API-04 | — | `grep` por `catch` vazio e por `ResponseEntity.ok` em caminho de erro | nenhuma ocorrência | Operação | MANUAL: grep da skill definition-of-done |

## Segurança

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-SEC-01 | — | revisão de código | zero SQL concatenado; todo SQL nativo usa parâmetros | Segurança | MANUAL: grep |
| AC-SEC-02 | `receivableId = "1 OR 1=1"` | `POST /api/settlements` | `400` (tipo inválido), nada executado | Segurança | `SettlementApiIT#sqlInjectionAttemptIsRefused` |
| AC-SEC-03 | payload > 64 KB | qualquer POST | `413`/`400` | Segurança | PENDENTE |
| AC-SEC-04 | — | inspeção do compose | credenciais do banco vêm de variável de ambiente (com padrão só para dev); nenhum segredo real versionado | Segurança | MANUAL |
| AC-SEC-05 | ⚠️ DECISÃO DO AUTOR | — | autenticação e autorização fora do escopo, justificadas em DECISIONS.md (o que seria: OAuth2/JWT, papel OPERADOR vs. AUDITOR) | Segurança | MANUAL: DECISIONS.md |

## Desempenho (medido localmente, documentar a máquina)

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-PERF-01 | 10.000 liquidações seed | **carga:** quote 20 req/s, extrato 10 req/s e liquidação 5 req/s por 2 min | quote p95 < 150 ms e p99 < 300 ms; extrato p95 < 100 ms; liquidação p95 < 300 ms; 0 erros | Desempenho | PENDENTE (`perf/load.js` → `docs/perf/load-summary.json`) |
| AC-PERF-02 | — | timer `credit_engine_pricing_duration` durante a carga | p99 do motor < 5 ms | Desempenho | PENDENTE |
| AC-PERF-03 | `docker compose up -d --build` com cache | — | API `healthy` em < 60 s | Desempenho | MANUAL |
| AC-PERF-04 | — | **estresse:** quote em degraus 50→800 req/s | ponto de quebra (p95 > 1 s ou erro > 1%) registrado com a vazão máxima sustentada, ou "não atingido até 800 req/s" | Desempenho | PENDENTE (`perf/stress.js` → `docs/perf/REPORT.md`) |
| AC-PERF-05 | estresse com liquidações hostis (3x mesma chave + 1 chave diferente por recebível) | durante a saturação | só aparecem `201`, `200`, `409`, `503`/`500`; nenhum `2xx` indevido | Funcional | PENDENTE (`perf/stress.js`) |
| AC-PERF-06 | fim do estresse | `perf/integrity.sql` | 0 duplicidades, 0 liquidações pela metade, 0 USD sem taxa, 0 inconsistências face = PV + deságio | Funcional | PENDENTE (`docs/perf/integrity-output.txt`) |
| AC-PERF-07 | fim do estresse | `GET /actuator/health` | `UP` sem restart manual | Operação | PENDENTE |

## Cobertura

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-COV-01 | — | `./mvnw verify` | build falha se a cobertura de linhas do projeto (unit + IT) < 70% | Operação | PENDENTE (JaCoCo `check`) |
| AC-COV-02 | — | `./mvnw verify` | build falha se a cobertura de linhas ou de branches de `domain.pricing` < 90% | Operação | PENDENTE (JaCoCo `check`) |

## Operação

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-OPS-01 | máquina só com Docker, sem `.env` | `docker compose up -d --build` | os 3 serviços sobem; frontend, Swagger e health respondem sem passo manual | Operação | MANUAL: definition-of-done §2 |
| AC-OPS-02 | liquidação concluída | `GET /actuator/prometheus` | `credit_engine_settlements_total{currency,type,outcome="success"}` incrementado | Operação | PENDENTE |
| AC-OPS-03 | liquidação rejeitada (409/422/503) | métricas | o mesmo contador com o `outcome` correspondente | Operação | PENDENTE |
| AC-OPS-04 | qualquer liquidação | log | linha JSON com `settlementId`, `receivableId`, `idempotencyKey`, `outcome` e `durationMs` | Operação | PENDENTE |
| AC-OPS-05 | push em qualquer branch | GitHub Actions | jobs `backend` (`./mvnw verify`) e `frontend` (lint + build) verdes | Operação | MANUAL: aba Actions |
