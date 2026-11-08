# Precificação — `AC-PRC`

| ID | Dado | Quando | Então | Tipo | Teste |
|---|---|---|---|---|---|
| AC-PRC-01 | Duplicata, face `100000.00`, n=3, BRL, base `0.01` | o motor precifica | PV `92859.94`, deságio `7140.06` (C1) | Funcional | `GoldenCasesTest#reproducesGoldenCaseToTheCent` |
| AC-PRC-02 | Cheque, face `25000.00`, n=2, BRL | o motor precifica | PV `23337.77`, deságio `1662.23` (C2) | Funcional | `GoldenCasesTest#reproducesGoldenCaseToTheCent` |
| AC-PRC-03 | Duplicata, face `100000.00`, n=3, USD, câmbio `5.4321` | o motor precifica | PV `17094.67` USD, deságio `7140.06` BRL (C3) | Funcional | `GoldenCasesTest#reproducesGoldenCaseToTheCent` |
| AC-PRC-04 | conversão com PV BRL `2.01` e `2.03`, câmbio `2` (quocientes exatos `1.005` e `1.015`) | o motor converte para USD | `1.00` e `1.02`: `HALF_EVEN` arredonda para o dígito par. Em BRL, o empate exato é impossível com face de 2 casas | Funcional | `PricingEngineTest#halfEvenOnExactTie` |
| AC-PRC-05 | C3 | o PV em BRL é convertido | a conversão usa o PV BRL **já arredondado** (`92859.94 / 5.4321`), não o valor cheio | Funcional | `PricingEngineTest#convertsRoundedPresentValue` |
| AC-PRC-06 | face `0.01`, n=1 | o motor precifica | PV com escala 2, sem `ArithmeticException` | Funcional | `PricingEngineTest#minimumFaceValueWithRepeatingDecimal` |
| AC-PRC-07 | face `1000000000.00`, n=360 | o motor precifica | PV calculado com escala 2, sem overflow (latência medida em AC-PERF-02) | Funcional | `PricingEngineTest#largeFactorKeepsPrecision` |
| AC-PRC-08 | vencimento = hoje + 3 meses | `POST /api/pricing/quote` | `termMonths = 3` na resposta | Funcional | `QuoteApiIT#exactMonths, TermCalculatorTest#roundsTermUp` |
| AC-PRC-09 | vencimento = hoje + 2 meses e 1 dia | `POST /api/pricing/quote` | `termMonths = 3` (arredonda para cima; premissa do SPEC) | Funcional | `QuoteApiIT#partialMonthRoundsUp, TermCalculatorTest#roundsTermUp` |
| AC-PRC-10 | vencimento ≤ hoje | `POST /api/pricing/quote` | `422`, `code = DUE_DATE_NOT_IN_FUTURE` | Funcional | `QuoteApiIT#dueDateNotInFuture, TermCalculatorTest#refusesDueDateNotInFuture` |
| AC-PRC-11 | face ≤ 0 ou com 3+ casas decimais | `POST /api/pricing/quote` | `400` com `errors[].field = faceValue` | Funcional | `ApiErrorsIT#invalidFaceValueIs400` |
| AC-PRC-12 | tipo de recebível desconhecido | `POST /api/pricing/quote` | `400` listando os tipos aceitos | Funcional | `ApiErrorsIT#unknownTypeListsAcceptedValues` |
| AC-PRC-13 | qualquer quote | resposta serializada | `presentValue`, `discount` e `fxRate` são **strings** JSON com escala fixa | Funcional | `QuoteApiIT#valuesAreStrings` |
| AC-PRC-14 | um novo tipo de recebível com spread próprio | dev adiciona uma `PricingStrategy` | nenhuma classe existente além do enum muda (Open/Closed — preparo para a mudança ao vivo) | Operação | MANUAL: revisar diff |
| AC-PRC-15 | qualquer quote | resposta | inclui `baseRate`, `spread`, `termMonths` e `fxRateId` (quando USD), para o operador entender o cálculo | Usabilidade | `QuoteApiIT#responseExplainsCalculation, QuoteApiIT#goldenCaseC3` |
| AC-PRC-16 | ⚠️ DECISÃO DO AUTOR: vencimento resulta em mais de 360 meses | `POST /api/pricing/quote` | `422`, `code = DUE_DATE_TOO_FAR` (limita o custo do `pow` e recusa prazo fora do uso real) | Segurança | `QuoteApiIT#dueDateTooFar, TermCalculatorTest#refusesTermAboveLimit` |
| AC-PRC-17 | ⚠️ DECISÃO DO AUTOR: PV arredondado resulta em `0.00` na moeda de pagamento (face `0.01`, USD) | `POST /api/pricing/quote` ou liquidação | `422`, `code = PRESENT_VALUE_TOO_SMALL`; nada gravado | Funcional | `QuoteApiIT#presentValueTooSmall, PricingEngineTest#refusesPresentValueRoundedToZero` |
