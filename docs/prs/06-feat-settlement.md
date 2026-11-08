## Contexto
A liquidação é onde dinheiro sai do fundo, e o Anexo A mostra como isso dá errado: retry paga em dobro, falha no meio deixa o recebível liquidável e o erro sai com `200`. Este PR é o coração da rubrica de domínio.

## Por quê (decisões)
- **UPDATE do recebível antes do INSERT da liquidação** — a concorrente espera o lock da linha e perde no `@Version`, que é a defesa principal. Com o INSERT primeiro, a `UNIQUE(receivable_id)` decidiria antes e o `@Version` nunca dispararia. Isso inverte a ordem citada em AC-SET-09 (R3 no `SPEC.md`).
- **`TransactionTemplate` em vez de `@Transactional`** — quem perde a corrida precisa reler a liquidação vencedora, e não dá para fazer isso na transação que acabou de falhar. Com `@Transactional` seria preciso outra classe só para ter a fronteira certa.
- **Uma só resolução de corrida (`resolveLostRace`)** — relê a chave: se ela existe é replay (ou `422` se o payload mudou); se não existe, o recebível foi pago por outra chave (`409`).
- **Só as duas `UNIQUE` contam como corrida** — qualquer outra violação (um `CHECK`, por exemplo) é bug e sai como `500`. Tratar toda `DataIntegrityViolationException` como `409` esconderia um registro incoerente.
- **Valores truncados em microssegundos** — o Postgres guarda microssegundos, e sem isso o replay devolveria um `settledAt` diferente da primeira resposta.
- **Falha injetada por trigger do próprio teste** — o teste de atomicidade exercita o código real, sem nenhum gancho de teste em produção.

## Um bug que os testes pegaram
A primeira versão falhava em cerca de 1 de 5 execuções de `sameKeyBothGetTheSameSettlement`, com `[201, 409]` em vez de `[201, 200]`. A requisição perdedora lia a chave antes do commit da vencedora e o recebível depois dele, e respondia `409` sem reler a chave. É por isso que os testes de concorrência usam `@RepeatedTest(5)`: uma execução única poderia ter passado.

## O que mudou
- `SettlementService`, `ReceivableService`, `SettlementController`, `ReceivableController`, `AssignorController`.
- `PricingEngine` normaliza a escala do face e nunca arredonda calado (`RoundingMode.UNNECESSARY`).
- `ApiExceptionHandler`: `500` genérico, com o stack trace só no log.
- Testes: `SettlementApiIT`, `SettlementConcurrencyIT`, `SettlementAtomicityIT`, `ReceivableApiIT`.

## Como testar
```bash
cd backend && ./mvnw verify
# concorrência isolada:
./mvnw verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SettlementConcurrencyIT
```

## Critérios de aceite
AC-SET-01 a AC-SET-10, 12 e 13; AC-FX-08, 09 e 10; AC-SEC-02.

## Riscos / fora de escopo
- Deadlock e esgotamento do pool saem como `500` limpo, sem nada gravado. A carga vai medir se isso aparece.
- Liquidação em lote fica fora: cada item é unitário e idempotente (`DECISIONS.md`).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
