# Relatório de cobertura

> Gerado por `scripts/coverage-report.sh` a partir dos CSVs desta pasta. Nenhum número aqui é digitado à mão.

Medido em 2026-11-09T01:43:08Z, no commit `16367b7`. Cada camada rodou sozinha, em uma execução separada, então cada linha da tabela responde: "se só estes testes existissem, quanto do backend seria executado?".

## Resumo por camada

| Camada | Testes | Linhas | Branches |
|---|---:|---:|---:|
| Unitário | 128 | 98,5% (646/656) | 96,0% (143/149) |
| Integração | 99 | 91,9% (603/656) | 85,2% (127/149) |
| Contrato | 16 | 89,9% (590/656) | 75,2% (112/149) |
| E2E | 1 | 78,2% (513/656) | 59,1% (88/149) |
| Unitário + integração + contrato | 243 | 99,7% (654/656) | 96,0% (143/149) |

As camadas se sobrepõem, então os percentuais não somam. A cobertura do E2E é só do backend, com o painel e o Nginx na frente. Nenhuma camada isolada cobriu mais linhas que a medição total.

Como ler: a meta de 80% faz sentido na última linha, que é o que o projeto todo garante. Cada camada isolada mede algo diferente, e o que vale olhar nela é o seu próprio alvo:

| Camada | O que ela precisa provar | Onde conferir |
|---|---|---|
| Unitário | Regras e decisões, sem banco nem rede | Tabela por classe, abaixo |
| Integração | O que só existe com banco real: transação, lock, trigger, SQL | `AC-SET`, `AC-STM` e `AC-FX`, abaixo |
| Contrato | Toda operação e status conforme o OpenAPI | Seção de contrato, abaixo |
| E2E | O fluxo do operador no navegador | `AC-UI`, abaixo |

O que roda em cada camada:

| Camada | Como | Classes |
|---|---|---|
| Unitário | JUnit e AssertJ, sem Spring nem banco | `ApiWebLayerTest`, `FxRateServiceTest`, `GoldenCasesTest`, `MockFxRateProviderTest`, `OpenApiConfigTest`, `PayloadLimitFilterTest`, `PricingEngineTest`, `QuoteServiceTest`, `ReceivableServiceTest`, `ResilientFxRateClientTest`, `SettlementEntityTest`, `SettlementServiceTest`, `StatementQueryTest`, `TermCalculatorTest` |
| Integração | Testcontainers com Postgres real, sem o `ContractIT` | `ApiErrorsIT`, `ApplicationBootIT`, `FxRateApiIT`, `FxResilienceIT`, `ObservabilityIT`, `QuoteApiIT`, `ReceivableApiIT`, `SettlementApiIT`, `SettlementAtomicityIT`, `SettlementConcurrencyIT`, `SettlementTableIT`, `StatementApiIT`, `StatementQueryPlanIT` |
| Contrato | `ContractIT`: tráfego real contra o OpenAPI publicado | `ContractIT` |
| E2E | Playwright no navegador, contra o compose, com agente JaCoCo no backend | `frontend/e2e/operator-flow.spec.ts` |

## Metas (gate do `./mvnw verify`)

O build falha abaixo destas metas. Elas valem para a linha "Unitário + integração + contrato".

| Escopo | Meta | Medido | Situação |
|---|---:|---:|---|
| Projeto, linhas | 70% | 99,7% (654/656) | ✅ |
| `domain.pricing`, linhas | 90% | 100,0% (42/42) | ✅ |
| `domain.pricing`, branches | 90% | 100,0% (14/14) | ✅ |

Excluídos da medição, por serem só bootstrap e configuração: `CreditEngineApplication` e `infrastructure/config`. Nenhum service ou controller foi excluído.

## Cobertura de contrato: operações e status

Linhas de código não dizem o que importa num contrato. Aqui, a medida é quais combinações de operação e status foram exercitadas com a resposta validada contra o OpenAPI: **29 de 29** combinações da tabela de convenções da API. Combinações fora dessa tabela também aparecem quando foram exercitadas.

| Operação | Status exercitados e validados | Declarados e não exercitados aqui |
|---|---|---|
| `GET /api/assignors` | 200 | — |
| `GET /api/fx-rates/current` | 200, 404 | — |
| `GET /api/receivables/{id}` | 200, 404 | — |
| `GET /api/settlements` | 200, 400 | — |
| `GET /api/settlements/{id}` | 200, 404 | — |
| `POST /api/fx-rates` | 201, 400, 422 | — |
| `POST /api/fx-rates/refresh` | 201, 503 | — |
| `POST /api/pricing/quote` | 200, 400, 422, 503 | — |
| `POST /api/receivables` | 201, 400, 404, 422 | — |
| `POST /api/settlements` | 200, 201, 400, 404, 409, 422, 503 | — |

Todas as combinações da tabela foram exercitadas.

## Cobertura de linhas por pacote

| Pacote | Unitário | Integração | Contrato | E2E | Unitário + integração + contrato |
|---|---:|---:|---:|---:|---:|
| `api` | 100,0% | 83,6% | 86,6% | 66,8% | 100,0% |
| `application` | 97,9% | 97,2% | 91,0% | 73,6% | 98,6% |
| `domain` | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model` | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing` | 100,0% | 97,6% | 90,5% | 90,5% | 100,0% |
| `infrastructure.fx` | 100,0% | 90,5% | 69,0% | 76,2% | 100,0% |
| `infrastructure.persistence` | 94,5% | 100,0% | 100,0% | 98,4% | 100,0% |

## Cobertura de linhas por classe

| Classe | Linhas | Unitário | Integração | Contrato | E2E | Unitário + integração + contrato |
|---|---:|---:|---:|---:|---:|---:|
| `api.ApiExceptionHandler` | 72 | 100,0% | 100,0% | 72,2% | 19,4% | 100,0% |
| `api.ApiExceptionHandler.FieldProblem` | 1 | 100,0% | 100,0% | 100,0% | 0,0% | 100,0% |
| `api.AssignorController` | 4 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.AssignorController.AssignorResponse` | 2 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.FxRateController` | 7 | 100,0% | 100,0% | 100,0% | 42,9% | 100,0% |
| `api.FxRateController.CreateFxRateRequest` | 1 | 100,0% | 100,0% | 100,0% | 0,0% | 100,0% |
| `api.FxRateController.FxRateResponse` | 3 | 100,0% | 100,0% | 100,0% | 0,0% | 100,0% |
| `api.OpenApiConfig` | 50 | 100,0% | 20,0% | 100,0% | 100,0% | 100,0% |
| `api.PayloadLimitFilter` | 21 | 100,0% | 100,0% | 47,6% | 47,6% | 100,0% |
| `api.PayloadLimitFilter.BufferedBodyRequest` | 6 | 100,0% | 83,3% | 83,3% | 83,3% | 100,0% |
| `api.PayloadLimitFilter.BufferedBodyRequest.new ServletInputStream() {...}` | 6 | 100,0% | 50,0% | 50,0% | 50,0% | 100,0% |
| `api.QuoteController` | 6 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.QuoteController.QuoteRequest` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.QuoteController.QuoteResponse` | 15 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.ReceivableController` | 9 | 100,0% | 100,0% | 100,0% | 88,9% | 100,0% |
| `api.ReceivableController.CreateReceivableRequest` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.ReceivableController.ReceivableResponse` | 9 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.SettlementController` | 16 | 100,0% | 100,0% | 100,0% | 87,5% | 100,0% |
| `api.SettlementController.SettleRequest` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.SettlementController.SettlementResponse` | 18 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `api.StatementController` | 18 | 100,0% | 100,0% | 94,4% | 77,8% | 100,0% |
| `api.StatementController.StatementPage` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `application.FxRateService` | 26 | 100,0% | 100,0% | 100,0% | 73,1% | 100,0% |
| `application.Quote` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `application.QuoteService` | 14 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `application.ReceivableService` | 15 | 93,3% | 100,0% | 100,0% | 66,7% | 100,0% |
| `application.SettlementService` | 87 | 97,7% | 95,4% | 85,1% | 70,1% | 97,7% |
| `application.SettlementService.Outcome` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.BusinessRuleException` | 4 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.ErrorCode` | 15 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.FxRateSource` | 3 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.PaymentCurrency` | 5 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.ReceivableStatus` | 3 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.model.ReceivableType` | 3 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.ChequePreDatadoStrategy` | 4 | 100,0% | 100,0% | 75,0% | 100,0% | 100,0% |
| `domain.pricing.DuplicataMercantilStrategy` | 4 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.MonetaryPolicy` | 3 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.PricingEngine` | 21 | 100,0% | 95,2% | 90,5% | 90,5% | 100,0% |
| `domain.pricing.PricingResult` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.PricingStrategy` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `domain.pricing.TermCalculator` | 8 | 100,0% | 100,0% | 87,5% | 75,0% | 100,0% |
| `infrastructure.fx.FxRateRefreshJob` | 11 | 100,0% | 100,0% | 81,8% | 81,8% | 100,0% |
| `infrastructure.fx.MockFxRateProvider` | 11 | 100,0% | 63,6% | 0,0% | 63,6% | 100,0% |
| `infrastructure.fx.ResilientFxRateClient` | 20 | 100,0% | 100,0% | 100,0% | 80,0% | 100,0% |
| `infrastructure.persistence.Assignor` | 4 | 0,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.FxRate` | 13 | 92,3% | 100,0% | 100,0% | 84,6% | 100,0% |
| `infrastructure.persistence.Receivable` | 19 | 94,7% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.Settlement` | 36 | 97,2% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.StatementQuery` | 52 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.StatementQuery.Filter` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.StatementQuery.Item` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |
| `infrastructure.persistence.StatementQuery.Result` | 1 | 100,0% | 100,0% | 100,0% | 100,0% | 100,0% |

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
