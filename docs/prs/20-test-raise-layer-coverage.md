## Contexto
O relatório por camada mostrou unitário em 11,6% de linhas, contrato em 64,4% de branches e E2E em 71,3% de linhas. O número de unitário era baixo porque só o motor tinha teste sem banco, enquanto havia lógica real testável sem ele. O autor pediu para melhorar essas métricas.

## Por quê (decisões)
- **Testar decisões, não getters** — o foco é o que não estava provado: replay, `422` e `409`, limites exatos de validade da taxa e de payload, SQL sem valor do usuário e o mapa de erro para status.
- **Corridas com intercalação montada de propósito** — `SettlementConcurrencyIT` depende do escalonamento das threads, então um caminho de corrida podia aparecer numa execução e não em outra. `SettlementServiceTest` monta cada intercalação, e o teste concorrente real continua existindo.
- **`ApiWebLayerTest` usa MockMvc standalone** — exercita rotas, validação e erros sem contexto Spring nem banco. É mais amplo que um unitário clássico: um purista chamaria de teste de fatia.
- **Contrato em 29/29** — todas as combinações da tabela de convenções passam pelo validador.
- **E2E não foi forçado a 80%** — o que falta está em caminhos que o painel não alcança por definição: erros que o formulário já barra e corridas. Chamar a API de dentro do E2E só para subir o número seria inflar a métrica.
- **`verify-all.sh` sempre recompila** — a extensão Java do VS Code grava em `target/classes` com outro compilador. Isso distorceu a medição anterior e chegou a quebrar os testes com `NoClassDefFoundError`.

## Contraprova
Cobertura alta não prova nada sozinha. 10 bugs foram reintroduzidos à mão, um por vez, e **os 10 fizeram os testes novos falharem**, entre eles o bug de corrida da etapa 6, a inversão da ordem UPDATE/INSERT e o `BRL` passando a depender do câmbio. O código foi restaurado depois de cada tentativa. Os bugs foram escolhidos à mão, então isto não substitui uma análise completa de mutação.

## O que mudou
- Testes novos: `SettlementServiceTest`, `FxRateServiceTest`, `QuoteServiceTest`, `ReceivableServiceTest`, `ResilientFxRateClientTest`, `StatementQueryTest`, `SettlementEntityTest`, `PayloadLimitFilterTest`, `OpenApiConfigTest`, `ApiWebLayerTest`.
- `ContractIT`: cinco cenários novos.
- `operator-flow.spec.ts`: liquidação em dólar, filtro de período e Swagger.
- `docs/coverage/`, `docs/perf/`, `docs/screenshots/`: resultados das novas execuções.

## Como testar
```bash
cd backend && ./mvnw test      # unitários, sem Docker
scripts/coverage-report.sh     # cobertura por camada
scripts/verify-all.sh          # tudo, com banco zerado
```

## Critérios de aceite
AC-API-03, AC-API-05, AC-COV-01, AC-COV-02, AC-UI-01 a AC-UI-08.

## Riscos / fora de escopo
- O volume de testes cresceu de 27 para 128 unitários. A rubrica pune volume sem julgamento, então vale conhecer os principais antes da defesa.
- Os testes com mock dependem da implementação. Refatorar `SettlementService` pode exigir ajustá-los, mesmo sem mudança de comportamento.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
