# REVIEW — Anexo A (`settlement.controller.ts`)

**Veredito: não aprovar.** O endpoint calcula o valor errado em toda operação e, quando acerta o fluxo, ainda pode pagar em dobro. Os problemas estão em ordem de impacto no negócio, e os três primeiros perdem dinheiro sem nenhum erro aparente.

## 1. Taxas em unidade errada: toda duplicata é precificada a 250% a.m.

```ts
const BASE_RATE = 1.0; // taxa base mensal
const spread = receivable.type === "DUPLICATA" ? 1.5 : 2.5;
```

**Impacto.** `1.0` significa 100%, não 1%. A taxa de uma duplicata vira `1 + 1.0 + 1.5 = 3.5`. Em 3 meses, 100.000 / 3,5³ ≈ **R$ 2.332,36**, contra os R$ 92.859,94 corretos: o cedente recebe cerca de 2,3% do face. O erro é determinístico, afeta 100% das operações e não gera exceção nem log.

**Correção.** Taxas como fração (`0.01`, `0.015`), com nome que carrega a unidade (`baseRateMonthly`), e configuração que recusa valores acima de `0.5` na subida. Golden cases no CI, porque este bug não sobrevive a um único teste com valor conhecido.

## 2. Sem idempotência e sem lock: retry ou duplo clique pagam em dobro

**Impacto.** Duas requisições iguais leem o mesmo recebível, as duas calculam e as duas gravam. Nada impede a segunda: o status nem é verificado e não há `UNIQUE`. Um retry de rede, de gateway ou um duplo clique geram duas liquidações. É a causa do incidente do Anexo B.

**Correção.** Header `Idempotency-Key` obrigatório, com `UNIQUE(idempotency_key)` e hash do payload. Mesma chave e mesmo payload devolvem a liquidação original; mesma chave com outro payload é `422`. Para chaves diferentes no mesmo recebível: `@Version` como defesa principal (a perdedora recebe `409`) e `UNIQUE(receivable_id)` como rede de segurança.

## 3. Sem transação e `catch` vazio: falha no meio deixa o recebível liquidável, e responde `200`

```ts
} catch (e) {
  // se falhar aqui, o insert já rodou, então segue o jogo
}
```

**Impacto.** O INSERT é confirmado sozinho. Se o UPDATE falhar, a liquidação existe, o recebível continua em aberto e o operador vê `{ ok: true }`. O recebível segue disponível para ser liquidado de novo: é outra rota para a duplicidade do item 2, agora silenciosa. O comentário mostra que o autor viu o risco e o aceitou.

**Correção.** INSERT e UPDATE na mesma transação, com rollback total em qualquer falha. Nenhum `catch` que não trate a causa: o erro sobe e vira `500`.

## 4. SQL injection em três queries

```ts
`SELECT * FROM receivables WHERE id = ${receivableId}`
```

**Impacto.** `receivableId` e `currency` vêm do corpo e entram direto no SQL. Com `{"receivableId": "1 OR 1=1"}`, o UPDATE marca **todos** os recebíveis como `SETTLED`. Dependendo das permissões, dá para ler ou apagar dados. Fica abaixo dos itens anteriores só porque exige um ator mal-intencionado, enquanto os outros acontecem sozinhos.

**Correção.** Queries parametrizadas sempre, e tipo validado na entrada: `receivableId` numérico é rejeitado com `400` antes de chegar ao banco.

## 5. `number` (float binário) em dinheiro e `toFixed` no arredondamento

**Impacto.** `face_value`, `presentValue` e `rate` são `double`. O `toFixed` não aplica half-even e opera sobre um binário já impreciso: `(1.005).toFixed(2)` devolve `"1.00"`, porque `1.005` não existe em binário. Em volume, os centavos divergem da contabilidade do fundo.

**Correção.** Decimal exato em todo o caminho (`BigDecimal` e `NUMERIC`), `HALF_EVEN` em um único lugar e só no resultado final de cada moeda, e valores como string no JSON.

## 6. Câmbio sem vigência, sem validade e sem registro

```ts
const rate = await fxService.getLatestRate("USD");
finalAmount = presentValue / rate;
```

**Impacto.**
- `getLatestRate` não considera vigência nem idade: uma taxa de ontem ou futura seria usada sem aviso.
- A taxa usada não é gravada, então não dá para reproduzir nem auditar o valor pago, o que o enunciado exige.
- A conversão usa o PV não arredondado, contra a premissa do C3.
- A direção da taxa é implícita: nada no código diz que é BRL por USD. Inverter a divisão passaria despercebido.
- O trecho não mostra o que `fxService` faz. Se ele consultar o provedor, a queda do provedor derruba toda liquidação em USD, e não há timeout visível limitando a espera. Uma pergunta para o autor do PR.

**Correção.** Taxa lida do banco, a mais recente com `valid_from ≤ agora` e dentro de `max-age`; se não houver, `503` e nada gravado. Valor e id da taxa ficam na liquidação. O refresh do provedor roda fora da liquidação, com timeout, retry e circuit breaker.

## 7. Validação ausente

**Impacto.**
- Recebível inexistente gera `TypeError` ao ler `receivable.type`: um `500` sem explicação.
- Recebível já `SETTLED` não é verificado.
- `currency` aceita qualquer texto: `"EUR"` é gravado como se fosse BRL, porque só `"USD"` converte.
- `receivable.term` não tem unidade definida, e o vencimento passado não é verificado.

**Correção.** Bean Validation na entrada (`400` para pedido malformado), `404` para recebível inexistente, `409` para já liquidado e `422` para regra de negócio recusada, sempre com `ProblemDetail`.

## 8. Semântica HTTP

**Impacto.** `200` em vez de `201` numa criação, sem `Location`. O corpo `{ ok: true, amount }` não traz identificador: o operador não tem como consultar, conciliar ou citar a liquidação. Erro mascarado como sucesso é o item 3 visto de fora: o cliente não tem como saber que deve parar ou tentar de novo.

**Correção.** `201` com `Location` e corpo completo, `200` só no replay idempotente, e todo erro como `application/problem+json`, com um `code` estável.

## 9. Design

**Impacto.** O ternário faz qualquer tipo novo ser precificado silenciosamente como cheque (`2.5`). A regra de negócio mora no controller, então só dá para testar subindo o HTTP e o banco.

**Correção.** Strategy por tipo de recebível, motor em código puro, e uma falha na subida se um tipo ficar sem estratégia.

## 10. Operação

**Impacto.** Zero log, zero métrica, sem timestamp nem trilha de auditoria. Quando o incidente chegar, não haverá como saber quantas liquidações foram duplicadas nem quando começaram.

**Correção.** Registro imutável com `settled_at`, taxas e chave de idempotência, protegido por trigger. Contador por `outcome` (replay e recusas inclusive), timer do motor e logs estruturados com os ids.

---

## Prevenção sistêmica

Corrigir este PR não evita o próximo. Quatro barreiras baratas evitam a classe do problema, sem travar o time:

| Barreira | O que pega | Situação neste repositório |
|---|---|---|
| Golden cases no CI, bloqueando o merge | Item 1 e qualquer regressão de cálculo | Implementado (`GoldenCasesTest`, `QuoteApiIT`) |
| Lint que proíbe `number` e `float` em campos monetários | Item 5 | Parcial: ESLint barra `parseFloat` e `toFixed`, e o `scripts/verify-all.sh` procura `double` e `float` no backend |
| Teste de idempotência obrigatório em endpoint de escrita | Itens 2 e 3 | Implementado para a liquidação (`SettlementApiIT`, `SettlementConcurrencyIT`, `SettlementAtomicityIT`) |
| Template de PR com checklist financeira: unidade das taxas, arredondamento, transação, idempotência, trilha de auditoria | Revisão às pressas numa sexta-feira | Proposta, não implementada: faz sentido com mais de uma pessoa revisando |

A checklist tem que ser curta. Uma longa vira marcação automática e deixa de proteger.
