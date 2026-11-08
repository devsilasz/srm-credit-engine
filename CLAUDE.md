# CLAUDE.md — SRM Credit Engine

Contexto permanente para qualquer agente que trabalhe neste repositório. Leia antes de escrever código. Em caso de conflito, **este arquivo vence o seu instinto** e o enunciado vence este arquivo.

## O que é

Motor de precificação e liquidação de recebíveis (FIDC) com caixa em BRL e USD. Desafio técnico para vaga **Pleno Java**: o alvo é Pleno completo mais itens Sênior selecionados (lista no `PROMPT.md`). Haverá **defesa ao vivo** com alteração de código. Todo código precisa ser explicável em poucas frases.

## Regras invioláveis

1. **Dinheiro e taxas são `BigDecimal`** na aplicação e `NUMERIC` no banco. `double`/`float` em valores monetários ou taxas é eliminatório. No frontend, `decimal.js` ou strings; nunca `number` para dinheiro.
2. **Golden cases C1, C2 e C3 passam ao centavo** em todo commit da `main`. Ver skill `golden-cases`.
3. **Nenhum erro com `200 OK`**, nenhuma exceção engolida. Erros saem como `ProblemDetail` (RFC 9457).
4. **Liquidação é atômica, idempotente e imutável.**
5. **Nada de volume:** sem camadas vazias, sem interface com uma implementação só "por padrão" (exceções justificadas: `PricingStrategy`, porque o enunciado exige Strategy; `FxRateProvider`, porque o mock substitui um provedor externo).
6. **Testes de integração usam Testcontainers com Postgres.** H2 é proibido.

## Premissas de domínio (ambiguidades do enunciado já decididas)

| Ambiguidade | Decisão | Justificativa |
|---|---|---|
| Unidade do prazo | Meses inteiros. `termMonths = ceil(meses entre a data da operação e o vencimento)`, calculado em `America/Sao_Paulo`. Vencimento ≤ hoje → `422`. | Bate com a premissa dos golden cases. Arredondar para cima é conservador para o fundo (mais deságio). Pergunta ao negócio: pró-rata por dias úteis (252)? |
| Taxa base | `1,00% a.m.`, configurável em `pricing.base-rate-monthly` (`0.01`). Gravada em cada liquidação. | Pergunta ao negócio: atrelada ao CDI? Quem altera e com que vigência? |
| Unidade de taxa | Taxas como fração decimal: 1% = `0.01`, spread 1,5% = `0.015`. Nunca `1.0` ou `1.5`. | É exatamente o bug nº 1 do Anexo A. |
| Spreads | Duplicata Mercantil `0.015`, Cheque Pré-datado `0.025`, cada um na sua `PricingStrategy`. | Strategy exigido pelo enunciado. |
| Fórmula | `PV = Face / (1 + base + spread)^n` | Enunciado. |
| Arredondamento | `HALF_EVEN`, 2 casas, **somente no resultado final** de cada moeda. `pow(n)` com `n` inteiro é exato; a divisão usa `MathContext.DECIMAL128`. | Premissa dos golden cases. |
| Cross-currency | `PV_BRL` arredondado a 2 casas → `PV_USD = PV_BRL / taxa`, com `DECIMAL128`, depois `HALF_EVEN` a 2 casas. | Premissa explícita do C3. |
| Direção do câmbio | `fx_rate.rate` = BRL por 1 USD (ex.: `5.4321`). USD = BRL ÷ rate. | Tabela do enunciado ("BRL/USD 5,4321"). |
| Deságio | `face − PV_BRL`, sempre em BRL. | C3 mostra deságio em R$. |
| Qual taxa de câmbio vale | A **vigente no instante da liquidação**: a mais recente com `valid_from ≤ now()` e idade ≤ `fx.max-age` (padrão `PT1H`). O id e o valor dela ficam gravados na liquidação. | Auditável e reprodutível. A taxa da data de vencimento não existe no momento da liquidação. |
| Taxa vencida ou inexistente | Liquidação em USD → `503` (`FX_RATE_UNAVAILABLE`), nada gravado. Liquidação em BRL não é afetada. | Recusar é mais seguro que liquidar com taxa errada. |
| Provedor de câmbio | `FxRateProvider` (mock) alimenta `fx_rate` via refresh no startup, `@Scheduled` (padrão a cada 10 min) e `POST /api/fx-rates/refresh`. O refresh tem timeout de 2 s, retry 3x com backoff e circuit breaker. A **liquidação não chama o provedor**: lê a taxa vigente do banco. | Se o provedor cair no meio de uma liquidação, ela não é afetada até a taxa vencer; depois, recusa com 503. |
| Atualização manual | `POST /api/fx-rates` insere uma nova linha (nunca UPDATE). | Histórico de taxas é auditável. |
| Validação de entrada | Formato, sinal e escala (`face ≤ 0`, mais de 2 casas, tipo desconhecido) → `400` por Bean Validation. Vencimento ≤ hoje → `422` (`DUE_DATE_NOT_IN_FUTURE`) na regra de negócio, com `Clock` injetado, sem `@Future`. | `400` é pedido malformado; `422` é pedido bem-formado que a regra recusa. |
| Simulação vs. liquidação | A simulação (`/quote`) não persiste. A liquidação **recalcula no servidor** e não confia em valor vindo do cliente. | Integridade. |
| Idempotência | Header `Idempotency-Key` (UUID) obrigatório. Grava `idempotency_key` UNIQUE e `request_hash` (SHA-256 do payload canônico). Mesma chave e mesmo hash → `200` com a liquidação original. Mesma chave e hash diferente → `422`. Sem header → `400`. | Padrão de mercado (Stripe/IETF draft). |
| Concorrência | `receivable.version` (`@Version`). Dois pedidos com chaves diferentes para o mesmo recebível → um vence (`201`), o outro recebe `409`. Duas requisições com a mesma chave em paralelo → a perdedora captura a violação de UNIQUE, relê e devolve `200` com a original. Rede de segurança: `UNIQUE(receivable_id)` em `settlement`. | Defesa em profundidade. |
| Imutabilidade | `settlement` sem setters na entidade, `@Immutable` e trigger PL/pgSQL que bloqueia UPDATE/DELETE. | "Alterar uma liquidação não é uma operação do sistema." |
| Tempo | `Instant` / `TIMESTAMPTZ` em UTC. Datas de vencimento são `LocalDate`. | — |
| Lote | A liquidação é unitária. O "lote" do enunciado é atendido pelo operador liquidando N itens, e cada um é independente e idempotente. | Registrar em DECISIONS.md. Pergunta ao negócio: lote deve ser tudo-ou-nada? |

## Tipos no banco

- Valores monetários: `NUMERIC(19,2)`.
- Taxas de câmbio: `NUMERIC(19,8)`.
- Taxa base e spread: `NUMERIC(9,6)`.
- Moedas: `VARCHAR(3)` com CHECK (`'BRL','USD'`).
- Tipos de recebível: `VARCHAR(30)` com CHECK.

## Arquitetura (pacotes)

```
br.com.srm.creditengine
├── api/            controllers, DTOs (records), exception handler, OpenAPI config
├── application/    casos de uso transacionais (SettlementService, FxRateService, QuoteService)
├── domain/         pricing (strategies, engine, MonetaryPolicy), model, exceções de domínio — SEM Spring
├── infrastructure/ JPA entities/repos, StatementQuery (JdbcTemplate), MockFxRateProvider, métricas
```

O extrato atalha `api → infrastructure` (permitido pelo enunciado). O domínio de pricing é Java puro, testável sem Spring.

## Anexo A — ordem de severidade a defender

1. **Unidade das taxas** (`BASE_RATE = 1.0`, spread `1.5`): uma duplicata é precificada a 250% a.m.; em 3 meses, o PV fica em ≈ 2,3% do face (100.000 / 3,5³ ≈ 2.332,36). Erro determinístico em 100% das operações.
2. **Sem idempotência e sem lock:** retry ou duplo clique → pagamento em dobro (é a causa do Anexo B).
3. **Sem transação e `catch` vazio:** o INSERT persiste, o UPDATE falha, o recebível continua liquidável → duplicidade, e ainda responde `200`.
4. **SQL injection** em três queries (`receivableId`, `currency`).
5. **`number` (float binário)** em valores e `toFixed` (arredondamento não é half-even e opera sobre um binário já impreciso).
6. **Câmbio:** `getLatestRate` sem vigência nem validade, taxa não gravada (auditoria quebrada), PV não arredondado antes da conversão e direção da taxa implícita.
7. **Validação ausente:** recebível inexistente → TypeError/500, status já `SETTLED` não verificado, `currency` arbitrária, `term` sem unidade.
8. **Semântica HTTP:** `200` em vez de `201`, erro mascarado e corpo `{ok:true}` sem identificador.
9. **Design:** spreads hardcoded com ternário (qualquer tipo novo vira cheque), sem Strategy e lógica de negócio no controller.
10. **Operação:** zero log, zero métrica, sem timestamps nem trilha de auditoria.

A prevenção sistêmica vai no final do REVIEW: golden cases no CI, lint que proíbe `number` e `float` em campos monetários, template de PR com checklist financeira e testes de idempotência obrigatórios em endpoints de escrita.

## Comandos

```bash
cd backend && ./mvnw verify          # testes + spotless (precisa de Docker para Testcontainers)
cd backend && ./mvnw spotless:apply  # formatar
cd frontend && npm run lint && npm run build
docker compose up -d --build         # sobe tudo
docker compose down -v               # zera o banco
scripts/verify-all.sh                # critério de saída: build, compose, smoke, E2E, carga, estresse e integridade
```
