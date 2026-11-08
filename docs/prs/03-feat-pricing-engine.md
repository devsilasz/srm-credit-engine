## Contexto
O motor é o coração do sistema e tem 25% da rubrica: precisa reproduzir os golden cases ao centavo. Ele nasce como Java puro, sem Spring, para ser testado em milissegundos e explicado sem falar de framework.

## Por quê (decisões)
- **Strategy só com o spread e um `discountFactor` padrão** — cada classe tem a regra de risco do seu tipo. Um tipo com regra diferente sobrescreve o `default`, sem mexer no motor. Um `Map<tipo, spread>` seria menos código, mas não aceitaria uma regra diferente.
- **`pow` exato, sem `MathContext`** — com expoente inteiro o resultado é exato. Só a divisão usa `DECIMAL128`, e o arredondamento acontece uma vez por moeda, em `MonetaryPolicy`.
- **O PV em USD é calculado sobre o PV em BRL já arredondado** — é a premissa do C3. `PricingEngineTest#convertsRoundedPresentValue` usa a taxa `0.1` para amplificar os centavos: se a conversão usasse o valor cheio, o resultado seria `928599.41`, não `928599.40`.
- **`BusinessRuleException` com `ErrorCode` em vez de uma classe por erro** — menos código, e o mapeamento para HTTP fica num único `switch` exaustivo na API. O domínio não conhece status HTTP.
- **Falhar na subida se um tipo do enum ficar sem estratégia** — descobrir isso no deploy é melhor do que no meio de uma liquidação.
- **Prazo máximo de 360 meses e recusa de PV zero** — premissas R1 e R2 do `SPEC.md`.

## O que mudou
- `domain/pricing`: `MonetaryPolicy`, `PricingStrategy` e duas implementações, `PricingEngine`, `TermCalculator`, `PricingResult`.
- `domain/model`: enums `ReceivableType`, `ReceivableStatus` e `PaymentCurrency`.
- `domain`: `ErrorCode` e `BusinessRuleException`.
- Testes: `GoldenCasesTest`, `PricingEngineTest`, `TermCalculatorTest`.

## Como testar
```bash
cd backend && ./mvnw -q test -Dtest=GoldenCasesTest   # 3/3
cd backend && ./mvnw verify
```

## Critérios de aceite
AC-PRC-01 a AC-PRC-07; AC-PRC-08, 09, 10, 16 e 17 na regra (a verificação via API vem depois).

## Riscos / fora de escopo
A descoberta automática das estratégias pelo Spring entra junto com o primeiro uso real, em `feat/fx-rates`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
