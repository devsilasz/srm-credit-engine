---
name: monetary-precision
description: Regras obrigatórias para qualquer código que calcule, armazene, serialize ou exiba dinheiro, taxas ou câmbio no SRM Credit Engine. Use antes de tocar em pricing, settlement, fx, DTOs, migrations ou frontend com valores.
---

# Precisão monetária

## Regras

1. **Tipos**
   - Java: `BigDecimal`. Nunca `double`, `float`, `Double` ou `Float` para valor, taxa, spread ou câmbio. `int` só para prazo em meses.
   - Construa `BigDecimal` com `new BigDecimal("0.015")` ou `BigDecimal.valueOf(long, scale)`. **Nunca** `new BigDecimal(0.015)` (herda o erro binário) nem `BigDecimal.valueOf(double)`.
   - Banco: `NUMERIC(19,2)` para valores, `NUMERIC(19,8)` para câmbio e `NUMERIC(9,6)` para taxa base e spread.
   - JSON: valores trafegam como **string** (`"92859.94"`). Configure com `@JsonFormat(shape = STRING)` nos records de resposta ou com um `Jackson2ObjectMapperBuilderCustomizer`. Na entrada, aceite string ou número e valide a escala (máx. 2 casas para face value).
   - Frontend: `decimal.js` ou string. Formatação com `Intl.NumberFormat('pt-BR', { style: 'currency', currency })` a partir da string, nunca fazendo conta com `number`.

2. **Arredondamento: um único lugar**
   - Toda constante e todo arredondamento ficam em `MonetaryPolicy` (`SCALE = 2`, `ROUNDING = HALF_EVEN`, `CALC = MathContext.DECIMAL128`).
   - Arredonde **só no resultado final de cada moeda**:
     ```
     factor  = (1 + base + spread).pow(n)            // exato: n inteiro, escala finita
     pvBrl   = face.divide(factor, CALC).setScale(2, HALF_EVEN)
     pvUsd   = pvBrl.divide(rate, CALC).setScale(2, HALF_EVEN)   // só se moeda = USD
     discount= face.subtract(pvBrl)                   // já tem escala 2
     ```
   - Proibido: `setScale` intermediário no `factor`, `divide` sem `MathContext` (lança `ArithmeticException` em dízima), `RoundingMode.HALF_UP` e `BigDecimal.ROUND_*` (deprecated).

3. **Comparação**: use `compareTo`, nunca `equals` (`2.0` não é `equals` a `2.00`). Nos testes, `isEqualByComparingTo` ou comparar `toPlainString()` depois do `setScale(2)`.

4. **Taxas são frações**: 1% = `0.01`. Nomes terminam com a unidade (`baseRateMonthly`, `spreadMonthly`). Uma taxa > `0.5` é suspeita e deve ser validada (rejeite no `@ConfigurationProperties` com `@DecimalMax("0.5")`).

## Checagem obrigatória antes de concluir qualquer tarefa que toque em valores

```bash
grep -rnE "\b(double|float|Double|Float)\b" backend/src/main
grep -rn "new BigDecimal([0-9]" backend/src
grep -rn "BigDecimal.valueOf([0-9]*\.[0-9]" backend/src
grep -rnE "HALF_UP|ROUND_" backend/src/main
grep -rnE "parseFloat|toFixed" frontend/src
```

Qualquer resultado relacionado a dinheiro é bug: corrija antes de seguir. Se um `double` legítimo aparecer (ex.: métrica do Micrometer), deixe um comentário de uma linha explicando.

## Casos de borda que devem ter teste

- Face com 2 casas e PV com dízima (ex.: `R$ 0,01`, prazo 1).
- Empate de half-even: use a conversão cambial (`2.01 / 2 = 1.005 → 1.00`; `2.03 / 2 = 1.015 → 1.02`). Em BRL, com spread de 1,5% ou 2,5% e face de 2 casas, o empate exato é matematicamente impossível; não procure esse caso.
- Prazo 1 e prazo 360 (fator grande, sem overflow nem perda).
- Taxa de câmbio com 8 casas.
- Face `0`, face negativo e mais de 2 casas → `400` (validação), nunca cálculo.
