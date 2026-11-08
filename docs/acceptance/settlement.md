# Liquidação — `AC-SET`

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-SET-01 | recebível `OPEN` | `POST /api/settlements` com `Idempotency-Key` nova | `201`, `Location: /api/settlements/{id}`, recebível vira `SETTLED`, 1 linha em `settlement` | Funcional | `SettlementApiIT#settlesOpenReceivable` |
| AC-SET-02 | liquidação criada | consulto `settlement` no banco | contém face, PV BRL, PV na moeda de pagamento, deságio, taxa base, spread, `termMonths`, `fx_rate_id` e `fx_rate` (se USD), `idempotency_key`, `request_hash` e `settled_at` (UTC) | Funcional | `SettlementApiIT#recordCarriesEverythingUsed` |
| AC-SET-03 | chave `K` já usada com o mesmo payload | repito a requisição com `K` | `200`, mesmo corpo, `Idempotent-Replayed: true`, contagem de linhas inalterada | Funcional | `SettlementApiIT#sameKeySamePayloadReplays` |
| AC-SET-04 | chave `K` já usada | envio `K` com outro `receivableId` ou outra moeda | `422`, `code = IDEMPOTENCY_KEY_REUSED`, nada gravado | Funcional | `SettlementApiIT#sameKeyOtherCurrencyIsRefused, #sameKeyOtherReceivableIsRefused` |
| AC-SET-05 | — | `POST /api/settlements` sem header ou com valor não-UUID | `400`, `code = IDEMPOTENCY_KEY_REQUIRED` | Funcional | `SettlementApiIT#missingKeyIsRefused, #malformedKeyIsRefused` |
| AC-SET-06 | recebível já `SETTLED` | liquido com chave nova | `409`, `code = RECEIVABLE_ALREADY_SETTLED` | Funcional | `SettlementApiIT#alreadySettledIsConflict` |
| AC-SET-07 | recebível `OPEN` | 2 threads liquidam ao mesmo tempo com chaves **diferentes** | exatamente 1 `201` e 1 `409`; 1 linha em `settlement` | Funcional | `SettlementConcurrencyIT#differentKeysOneWins` |
| AC-SET-08 | recebível `OPEN` | 2 threads liquidam ao mesmo tempo com a **mesma** chave | 1 `201` e 1 `200` (replay), mesmo `settlementId`; 1 linha | Funcional | `SettlementConcurrencyIT#sameKeyBothGetTheSameSettlement` |
| AC-SET-09 | falha forçada após o INSERT de `settlement` e antes do UPDATE de `receivable` | liquidação | rollback total: 0 linhas novas, recebível segue `OPEN`, resposta `500` (nunca `2xx`) | Funcional | `SettlementAtomicityIT#failureBetweenWritesRollsBackEverything (ordem real: UPDATE e depois INSERT; ver R3 no SPEC.md)` |
| AC-SET-10 | liquidação existente | `UPDATE` ou `DELETE` direto no banco | o trigger rejeita com erro | Segurança | `SettlementTableIT#triggerRejectsUpdate, #triggerRejectsDelete` |
| AC-SET-11 | — | listo os endpoints no OpenAPI | não existe `PUT`/`PATCH`/`DELETE` em `/api/settlements` | Segurança | `ContractIT#settlementsCannotBeChangedThroughTheApi` |
| AC-SET-12 | recebível inexistente | liquido | `404`, `code = RECEIVABLE_NOT_FOUND` | Funcional | `SettlementApiIT#unknownReceivableIs404` |
| AC-SET-13 | cliente envia um campo `presentValue` no corpo | liquido | o valor é ignorado e recalculado no servidor (ou o campo é rejeitado com `400`) | Segurança | `SettlementApiIT#clientValuesAreIgnored` |
| AC-SET-14 | ⚠️ DECISÃO DO AUTOR: o lote é tudo-ou-nada? | — | premissa atual: liquidação unitária, cada item é independente | Funcional | MANUAL: ver DECISIONS.md |
