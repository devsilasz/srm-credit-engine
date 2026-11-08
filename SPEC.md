# SPEC — SRM Credit Engine

Motor de precificação e liquidação de recebíveis (FIDC) com caixa em BRL e USD. O detalhe verificável de cada regra está em [`docs/acceptance/`](docs/acceptance/README.md).

## 1. Premissas adotadas

| Ambiguidade | Decisão | Por quê |
|---|---|---|
| Unidade do prazo | Meses inteiros: `termMonths = ceil(meses entre hoje e o vencimento)` em `America/Sao_Paulo`. Vencimento ≤ hoje → `422`. | Bate com os golden cases. Arredondar para cima é conservador: mais deságio para o fundo. |
| Taxa base | `1,00% a.m.`, configurável em `pricing.base-rate-monthly` e gravada em cada liquidação. | Mudar a taxa não altera o passado. |
| Unidade das taxas | Fração decimal: 1% = `0.01`. A configuração rejeita valores acima de `0.5`. | É o bug nº 1 do Anexo A. |
| Moeda do título | O valor de face é sempre em BRL. USD existe só como moeda de pagamento. | Enunciado: "título em BRL, pagamento em USD". |
| Direção do câmbio | `fx_rate.rate` = BRL por 1 USD. USD = BRL ÷ taxa. | Tabela do enunciado ("BRL/USD 5,4321"). |
| Deságio | `face − PV_BRL`, sempre em BRL. | O C3 mostra o deságio em R$. |
| Qual taxa vale | A vigente no instante da liquidação: a mais recente com `valid_from ≤ agora` e idade ≤ `fx.max-age` (padrão `PT1H`). Id e valor ficam na liquidação. | Auditável e reprodutível. A taxa da data de vencimento ainda não existe na liquidação. |
| Taxa vencida ou ausente | USD → `503 FX_RATE_UNAVAILABLE`, nada gravado. BRL segue normal. | Recusar é mais seguro que liquidar com taxa errada. |
| Provedor cai no meio | A liquidação lê a taxa do banco e nunca chama o provedor. Ela só é afetada quando a taxa vence. | O provedor não entra no caminho crítico. |
| Simulação x liquidação | `/quote` não persiste. A liquidação recalcula tudo no servidor e ignora valores vindos do cliente. | Integridade. |
| Idempotência | `Idempotency-Key` (UUID) obrigatório. Mesma chave e payload → `200` com o original. Mesma chave e outro payload → `422`. | Padrão de mercado (Stripe, draft IETF). |
| Lote | A liquidação é unitária: o operador liquida N itens, cada um independente e idempotente. | Ver `DECISIONS.md`. |

## 2. Perguntas ao negócio

1. O prazo deve ser pró-rata por dias úteis (base 252) em vez de meses inteiros arredondados para cima?
2. A taxa base é atrelada ao CDI? Quem altera e com que vigência?
3. O "lote" do enunciado precisa ser tudo-ou-nada, ou a liquidação unitária atende à mesa?
4. Qual a validade aceitável de uma cotação de câmbio? `PT1H` é um palpite conservador.
5. A taxa de câmbio deve ser travada na simulação (com tempo de expiração) ou a da liquidação é suficiente?
6. Há títulos denominados em USD, ou USD é só moeda de pagamento?
7. Existe um estorno operacional? O sistema trata liquidação como imutável; uma correção seria um novo lançamento.
8. Há limites por cedente ou por operador? Quem pode liquidar (papéis OPERADOR e AUDITOR)?

## 3. Precisão numérica

| Camada | Valores | Câmbio | Taxa base e spread |
|---|---|---|---|
| Java | `BigDecimal` | `BigDecimal` | `BigDecimal` |
| PostgreSQL | `NUMERIC(19,2)` | `NUMERIC(19,8)` | `NUMERIC(9,6)` |
| JSON | string (`"92859.94"`) | string | string |
| Frontend | string e `decimal.js` | string | string |

Ordem do cálculo, com arredondamento só no resultado final de cada moeda:

```text
fator  = (1 + base + spread)^n                      exato: n inteiro, sem arredondar
pvBrl  = face / fator  (DECIMAL128) → HALF_EVEN, 2 casas
pvUsd  = pvBrl / taxa  (DECIMAL128) → HALF_EVEN, 2 casas   só em USD, sobre o PV já arredondado
desagio = face − pvBrl
```

Regra e constantes ficam só em `MonetaryPolicy`. `double` e `float` não aparecem em dinheiro nem em taxa.

## 4. Critérios de aceite (resumo)

| Área | O que garante | Arquivo |
|---|---|---|
| Precificação | Golden cases C1–C3 ao centavo, half-even, prazo, validação e valores como string | [`pricing.md`](docs/acceptance/pricing.md) |
| Liquidação | Atomicidade, idempotência, concorrência (1 `201` e 1 `409`) e imutabilidade por trigger | [`settlement.md`](docs/acceptance/settlement.md) |
| Câmbio | Vigência, validade, histórico append-only, timeout e circuit breaker | [`fx.md`](docs/acceptance/fx.md) |
| Extrato | Paginação, filtros, SQL nativo com índice e sort em lista branca | [`statement.md`](docs/acceptance/statement.md) |
| Usabilidade | Simulação em ≤ 1 s, erro legível, sem duplo envio e filtros na URL | [`frontend.md`](docs/acceptance/frontend.md) |
| Segurança, desempenho, operação | `ProblemDetail`, contrato OpenAPI, sem SQL concatenado, limite de payload, carga e estresse com k6, métricas e logs, um comando | [`non-functional.md`](docs/acceptance/non-functional.md) |

## 5. Premissas (a revisar pelo autor)

Ambiguidades novas encontradas durante a implementação. A regra foi escolher a opção mais conservadora para o negócio.

| # | Situação | Decisão tomada | Teste |
|---|---|---|---|
| R1 | Vencimento muito distante deixa o `pow` caro sem limite (ano 9999 ≈ 96 mil meses). | Prazo acima de 360 meses → `422 DUE_DATE_TOO_FAR`. O teto é o maior prazo dos critérios (AC-PRC-07). | AC-PRC-16 |
| R2 | PV arredondado a `0.00` (face `0.01` em USD) compraria o título de graça. | `422 PRESENT_VALUE_TOO_SMALL`, nada gravado. | AC-PRC-17 |
| R3 | **Conflito AC-SET-09 × `CLAUDE.md`.** O critério fala em falha "após o INSERT e antes do UPDATE", mas o `CLAUDE.md` faz do `@Version` a defesa principal e do `UNIQUE(receivable_id)` a rede de segurança. Com o INSERT primeiro, a `UNIQUE` bloqueia a concorrente antes de o `@Version` agir e ele nunca dispararia. | Pela precedência (`CLAUDE.md` > critérios), a ordem é UPDATE do recebível e depois INSERT da liquidação. O teste força a falha entre as duas escritas na ordem real e confere o rollback total. O texto do critério não foi alterado. | AC-SET-09 |
| R4 | **Conflito AC-FX-07 × skill `quality-gates`.** O critério pede resposta em "< 50 ms", mas a skill proíbe limite de tempo em teste de integração, porque fica instável com a JVM fria. | Pela precedência (skills > critérios), o teste prova o mecanismo: circuito `OPEN` e provedor não chamado. Os 50 ms não têm verificação automática. O texto do critério não foi alterado. | AC-FX-07 |
