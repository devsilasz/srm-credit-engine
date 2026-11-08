---
name: golden-cases
description: Aferição obrigatória do motor de precificação contra os golden cases C1–C3 do enunciado. Use sempre que alterar pricing, MonetaryPolicy, strategies, câmbio, e antes de qualquer merge na main.
---

# Golden cases

## Valores de referência (enunciado, seção 4.3)

Premissas fixas: taxa base `0.01` a.m., prazo em meses inteiros, juros compostos, `HALF_EVEN` a 2 casas só no resultado final e conversão sobre o PV em BRL já arredondado.

| # | Tipo | Face (BRL) | n | Moeda | Câmbio | PV esperado | Deságio (BRL) |
|---|---|---:|---:|---|---:|---:|---:|
| C1 | DUPLICATA_MERCANTIL | 100000.00 | 3 | BRL | — | 92859.94 | 7140.06 |
| C2 | CHEQUE_PRE_DATADO | 25000.00 | 2 | BRL | — | 23337.77 | 1662.23 |
| C3 | DUPLICATA_MERCANTIL | 100000.00 | 3 | USD | 5.4321 | 17094.67 | 7140.06 |

Conferência manual (para a defesa):
- C1: 1,025³ = 1,076890625 → 100000 / 1,076890625 = 92859,941… → **92859,94**
- C2: 1,035² = 1,071225 → 25000 / 1,071225 = 23337,767… → **23337,77**
- C3: 92859,94 / 5,4321 = 17094,670… → **17094,67**

## Teste obrigatório

Arquivo: `backend/src/test/java/br/com/srm/creditengine/domain/pricing/GoldenCasesTest.java`

- Teste unitário **puro** (sem Spring), `@ParameterizedTest` com `@CsvSource` contendo exatamente as três linhas acima.
- Chama o `PricingEngine` diretamente com `termMonths` inteiro (não com datas), porque o golden case fixa o prazo.
- Valida **PV e deságio**, comparando `toPlainString()` (garante escala 2 e valor).
- Nome do teste exibe o id do caso: `@ParameterizedTest(name = "{0}: PV={6}")`.

Também é obrigatório um teste de integração da API (`QuoteApiIT`) que chame `POST /api/pricing/quote` com C1 e C3 e valide o JSON, provando que a serialização não perde precisão. Use `dueDate = hoje + n meses` para que `termMonths` resulte em `n`.

## Execução

```bash
cd backend && ./mvnw -q test -Dtest=GoldenCasesTest
```

## Quando falhar

Não ajuste o valor esperado. **O enunciado é a verdade.** Verifique nesta ordem:

1. Spread ou taxa base escritos como `1.5` em vez de `0.015`.
2. `setScale` intermediário no fator ou no PV antes da conversão errada.
3. `HALF_UP` no lugar de `HALF_EVEN`.
4. Conversão feita sobre o PV não arredondado (C3 diverge em centavos).
5. Divisão na direção errada (`* rate` em vez de `/ rate`).
6. `BigDecimal` construído a partir de `double`.

Registre a falha e a causa encontrada na seção "Log do agente" de `AI_USAGE.md`. É evidência real para o autor.
