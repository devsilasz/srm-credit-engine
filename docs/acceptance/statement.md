# Extrato — `AC-STM`

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-STM-01 | 30 liquidações | `GET /api/settlements?page=0&size=10` | 10 itens, `totalElements = 30`, `totalPages = 3`, ordenado por `settledAt desc` | Funcional | `StatementApiIT#paginatesNewestFirst` |
| AC-STM-02 | liquidações em BRL e USD | filtro `currency=USD` | só USD | Funcional | `StatementApiIT#filtersByCurrency` |
| AC-STM-03 | liquidações de cedentes A e B | filtro `assignorId=A` | só A; cada item traz o nome do cedente | Funcional | `StatementApiIT#filtersByAssignor` |
| AC-STM-04 | liquidações em 3 dias distintos | `from=D1&to=D2` | inclui D1 e D2 inteiros (fuso `America/Sao_Paulo`) | Funcional | `StatementApiIT#periodIsInclusiveInBusinessTimezone` |
| AC-STM-05 | — | `from > to` ou `size > 100` | `400` | Funcional | `StatementApiIT#invalidFilterIs400` |
| AC-STM-06 | — | `sort=amount;drop table` ou um campo fora da lista branca | `400`; nenhum SQL dinâmico executado | Segurança | `StatementApiIT#sortOutsideWhitelistIs400` |
| AC-STM-07 | — | revisão de código | a query do extrato usa SQL nativo com parâmetros nomeados; a migration cria índice em `(settled_at)` e em `(assignor_id, settled_at)` | Desempenho | MANUAL: ver `StatementQuery` e migration |
| AC-STM-08 | 10.000 liquidações distribuídas em 12 meses, após `ANALYZE` | `EXPLAIN` da consulta de itens e do `count`, com `assignorId` + período ≤ 7 dias | usa índice, sem Seq Scan em `settlement` (latência medida em AC-PERF-01) | Desempenho | `StatementQueryPlanIT#usesIndexForAssignorAndPeriod` |
| AC-STM-09 | qualquer item | resposta | valores como strings com escala 2 e moeda explícita por item | Funcional | `StatementApiIT#itemsCarryStringValuesAndCurrency` |
