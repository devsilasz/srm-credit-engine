# Câmbio e resiliência — `AC-FX`

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-FX-01 | aplicação sobe com banco vazio | startup | refresh do provedor mock grava USD-BRL `5.4321` com `source = PROVIDER` | Operação | `FxRateApiIT#startupRefreshStoresProviderRate` |
| AC-FX-02 | — | `POST /api/fx-rates` com `{pair, rate, validFrom}` | `201`, nova linha com `source = MANUAL`; nenhuma linha existente alterada | Funcional | `FxRateApiIT#manualRateIsANewRow` |
| AC-FX-03 | taxas com `valid_from` T1 < T2 ≤ agora | `GET /api/fx-rates/current` | retorna a de T2 | Funcional | `FxRateApiIT#currentIsLatestValidFrom` |
| AC-FX-04 | taxa com `valid_from` no futuro | `GET /current` / liquidação | ainda não é considerada vigente | Funcional | `FxRateApiIT#futureRateIsNotCurrent` |
| AC-FX-05 | taxa ≤ 0 ou com mais de 8 casas | `POST /api/fx-rates` | `400`/`422` | Funcional | `FxRateApiIT#refusesNonPositiveRate, #refusesRateWithTooManyDecimals` |
| AC-FX-06 | provedor com atraso > 2 s | `POST /api/fx-rates/refresh` | `503` em ≤ 2,5 s por tentativa, `Retry-After` presente, nada gravado | Operação | `FxResilienceIT#slowProviderTimesOut` |
| AC-FX-07 | provedor falha N vezes seguidas | novo refresh | o circuit breaker abre; a resposta `503` vem em < 50 ms, sem chamar o provedor; métrica de estado do CB exposta | Operação | `FxResilienceIT#repeatedFailuresOpenCircuit` |
| AC-FX-08 | taxa vigente com idade > `fx.max-age` | liquidação em USD | `503`, `code = FX_RATE_UNAVAILABLE`, 0 linhas em `settlement`, recebível segue `OPEN` | Funcional | PENDENTE |
| AC-FX-09 | taxa vencida (mesmo cenário do 08) | liquidação em BRL | `201` normalmente (BRL não depende de câmbio) | Funcional | PENDENTE |
| AC-FX-10 | provedor caiu, mas a taxa ainda está dentro do `max-age` | liquidação em USD | `201` usando a taxa vigente; o `fx_rate_id` gravado aponta para ela | Funcional | PENDENTE |
