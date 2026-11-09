# Relatório de cobertura

> Gerado por `scripts/coverage-report.sh` a partir dos CSVs desta pasta. Nenhum número aqui é digitado à mão.

Medido em 2026-11-09T01:18:26Z, no commit `e8106af`. Cada camada rodou sozinha, em uma execução separada, então cada linha da tabela responde: "se só estes testes existissem, quanto do backend seria executado?".

## Resumo por camada

| Camada | Testes | Linhas | Branches |
|---|---:|---:|---:|
| Unitário | 27 | 11,6% (76/656) | 12,1% (18/149) |
| Integração | 99 | 92,1% (604/656) | 85,2% (127/149) |
| Contrato | 11 | 86,6% (568/656) | 64,4% (96/149) |
| E2E | 1 | 71,3% (468/656) | 49,7% (74/149) |
| Unitário + integração + contrato | 137 | 98,3% (645/656) | 89,3% (133/149) |

As camadas se sobrepõem, então os percentuais não somam. A cobertura do E2E é só do backend, com o painel e o Nginx na frente. Cada camada é uma execução própria, e os caminhos de corrida dependem do escalonamento das threads. Por isso, nesta medição, uma camada isolada cobriu linhas que a medição total não cobriu: `SettlementService` (integration: 84 linhas, total: 83).

O que roda em cada camada:

| Camada | Como | Classes |
|---|---|---|
| Unitário | JUnit e AssertJ, sem Spring nem banco | `GoldenCasesTest`, `MockFxRateProviderTest`, `PricingEngineTest`, `TermCalculatorTest` |
| Integração | Testcontainers com Postgres real, sem o `ContractIT` | `ApiErrorsIT`, `ApplicationBootIT`, `FxRateApiIT`, `FxResilienceIT`, `ObservabilityIT`, `QuoteApiIT`, `ReceivableApiIT`, `SettlementApiIT`, `SettlementAtomicityIT`, `SettlementConcurrencyIT`, `SettlementTableIT`, `StatementApiIT`, `StatementQueryPlanIT` |
| Contrato | `ContractIT`: tráfego real contra o OpenAPI publicado | `ContractIT` |
| E2E | Playwright no navegador, contra o compose, com agente JaCoCo no backend | `frontend/e2e/operator-flow.spec.ts` |

## Metas (gate do `./mvnw verify`)

O build falha abaixo destas metas. Elas valem para a linha "Unitário + integração + contrato".

| Escopo | Meta | Medido | Situação |
|---|---:|---:|---|
| Projeto, linhas | 70% | 98,3% (645/656) | ✅ |
| `domain.pricing`, linhas | 90% | 100,0% (42/42) | ✅ |
| `domain.pricing`, branches | 90% | 100,0% (14/14) | ✅ |

Excluídos da medição, por serem só bootstrap e configuração: `CreditEngineApplication` e `infrastructure/config`. Nenhum service ou controller foi excluído.

## Cobertura de contrato: operações e status

Linhas de código não dizem o que importa num contrato. Aqui, a medida é quais combinações de operação e status foram exercitadas com a resposta validada contra o OpenAPI: **21 de 29** combinações da tabela de convenções da API. Combinações fora dessa tabela também aparecem quando foram exercitadas.

| Operação | Status exercitados e validados | Declarados e não exercitados aqui |
|---|---|---|
| `GET /api/assignors` | 200 | — |
| `GET /api/fx-rates/current` | 200 | 404 |
| `GET /api/receivables/{id}` | 200, 404 | — |
| `GET /api/settlements` | 200, 400 | — |
| `GET /api/settlements/{id}` | 200, 404 | — |
| `POST /api/fx-rates` | 201, 422 | 400 |
| `POST /api/fx-rates/refresh` | 503 | 201 |
| `POST /api/pricing/quote` | 200, 400, 422 | 503 |
| `POST /api/receivables` | 201 | 400, 404, 422 |
| `POST /api/settlements` | 200, 201, 400, 404, 409, 422 | 503 |

Todo status "não exercitado aqui" está declarado no OpenAPI. O comportamento dos que não passam pelo validador é coberto pelos testes de integração, mas o formato da resposta deles não é conferido contra o contrato.

## Cobertura de linhas por pacote

| Pacote | Unitário | Integração | Contrato | E2E | Unitário + integração + contrato |
|---|---:|---:|---:|---:|---:|
| `api` | 0,0% | 83,6% | 84,0% | 51,9% | 98,5% |
| `application` | 0,0% | 97,9% | 87,5% | 73,6% | 97,2% |
| `domain` | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model` | 50,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing` | 100,0% | 97,6% | 88,1% | 90,5% | 100,0% |
| `infrastructure.fx` | 19,0% | 90,5% | 64,3% | 76,2% | 92,9% |
| `infrastructure.persistence` | 0,0% | 100,0% | 94,5% | 94,5% | 100,0% |

## Cobertura de linhas por classe

| Classe | Linhas | Unitário | Integração | Contrato | E2E | Unitário + integração + contrato |
|---|---:|---:|---:|---:|---:|---:|
| `api.ApiExceptionHandler` | 72 | 0,0% | 100,0% | 65,3% | 19,4% | 100,0% |
| `api.ApiExceptionHandler.FieldProblem` | 1 | 0,0% | 100,0% | 100,0% | 0,0% | 100,0% |
| `api.AssignorController` | 4 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.AssignorController.AssignorResponse` | 2 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.FxRateController` | 7 | 0,0% | 100,0% | 85,7% | 42,9% | 100,0% |
| `api.FxRateController.CreateFxRateRequest` | 1 | 0,0% | 100,0% | 100,0% | 0,0% | 100,0% |
| `api.FxRateController.FxRateResponse` | 3 | 0,0% | 100,0% | 100,0% | 0,0% | 100,0% |
| `api.OpenApiConfig` | 50 | 0,0% | 20,0% | 100,0% | 20,0% | 100,0% |
| `api.PayloadLimitFilter` | 21 | 0,0% | 100,0% | 47,6% | 47,6% | 100,0% |
| `api.PayloadLimitFilter.BufferedBodyRequest` | 6 | 0,0% | 83,3% | 83,3% | 83,3% | 83,3% |
| `api.PayloadLimitFilter.BufferedBodyRequest.new ServletInputStream() {...}` | 6 | 0,0% | 50,0% | 50,0% | 50,0% | 50,0% |
| `api.QuoteController` | 6 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.QuoteController.QuoteRequest` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.QuoteController.QuoteResponse` | 15 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.ReceivableController` | 9 | 0,0% | 100,0% | 100,0% | 88,9% | 100,0% |
| `api.ReceivableController.CreateReceivableRequest` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.ReceivableController.ReceivableResponse` | 9 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.SettlementController` | 16 | 0,0% | 100,0% | 100,0% | 87,5% | 100,0% |
| `api.SettlementController.SettleRequest` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.SettlementController.SettlementResponse` | 18 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.StatementController` | 18 | 0,0% | 100,0% | 88,9% | 77,8% | 100,0% |
| `api.StatementController.StatementPage` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `application.FxRateService` | 26 | 0,0% | 100,0% | 84,6% | 73,1% | 100,0% |
| `application.Quote` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `application.QuoteService` | 14 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `application.ReceivableService` | 15 | 0,0% | 100,0% | 93,3% | 66,7% | 100,0% |
| `application.SettlementService` | 87 | 0,0% | 96,6% | 85,1% | 70,1% | 95,4% |
| `application.SettlementService.Outcome` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.BusinessRuleException` | 4 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.ErrorCode` | 15 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.FxRateSource` | 3 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.PaymentCurrency` | 5 | 80,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.ReceivableStatus` | 3 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.ReceivableType` | 3 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.ChequePreDatadoStrategy` | 4 | 100,0% | 100,0% | 75,0% | 100,0% | 100,0% |
| `domain.pricing.DuplicataMercantilStrategy` | 4 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.MonetaryPolicy` | 3 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.PricingEngine` | 21 | 100,0% | 95,2% | 90,5% | 90,5% | 100,0% |
| `domain.pricing.PricingResult` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.PricingStrategy` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.TermCalculator` | 8 | 100,0% | 100,0% | 75,0% | 75,0% | 100,0% |
| `infrastructure.fx.FxRateRefreshJob` | 11 | 0,0% | 100,0% | 81,8% | 81,8% | 100,0% |
| `infrastructure.fx.MockFxRateProvider` | 11 | 72,7% | 63,6% | 0,0% | 63,6% | 72,7% |
| `infrastructure.fx.ResilientFxRateClient` | 20 | 0,0% | 100,0% | 90,0% | 80,0% | 100,0% |
| `infrastructure.persistence.Assignor` | 4 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.FxRate` | 13 | 0,0% | 100,0% | 100,0% | 84,6% | 100,0% |
| `infrastructure.persistence.Receivable` | 19 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.Settlement` | 36 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.StatementQuery` | 52 | 0,0% | 100,0% | 86,5% | 90,4% | 100,0% |
| `infrastructure.persistence.StatementQuery.Filter` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.StatementQuery.Item` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.StatementQuery.Result` | 1 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |

## Cobertura dos critérios de aceite

Vale para todas as camadas, inclusive as que não medem linhas. O detalhe de cada critério está em [`docs/acceptance/`](../acceptance/README.md).

| Área | Com teste automatizado | Verificação manual |
|---|---:|---:|
| `AC-API` | 4 | 1 |
| `AC-COV` | 0 | 2 |
| `AC-FX` | 10 | 0 |
| `AC-OPS` | 3 | 2 |
| `AC-PERF` | 6 | 1 |
| `AC-PRC` | 16 | 1 |
| `AC-SEC` | 2 | 3 |
| `AC-SET` | 13 | 1 |
| `AC-STM` | 8 | 1 |
| `AC-UI` | 9 | 2 |
| **Total** | **71** | **14** |

## O que não tem cobertura de linhas

| Item | Por quê | Qual é a evidência |
|---|---|---|
| Carga e estresse | Medem latência e integridade. Um agente de cobertura distorceria os números. | [`docs/perf/REPORT.md`](../perf/REPORT.md) |
| Código do frontend | Não há framework de teste unitário no frontend, por decisão registrada em `DECISIONS.md`, e o código TypeScript não é instrumentado. | `frontend/e2e/operator-flow.spec.ts` cobre o fluxo do operador, e os critérios `AC-UI` dizem o que ele prova |

## Como reproduzir

```bash
scripts/coverage-report.sh   # precisa de Java 21 e Docker
```

Os CSVs do JaCoCo por camada (`unit.csv`, `integration.csv`, `contract.csv`, `e2e.csv` e `all.csv`) e `contract-operations.csv` ficam nesta pasta.
