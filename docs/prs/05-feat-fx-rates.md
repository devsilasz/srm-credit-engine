## Contexto
A pergunta do enunciado é: "o que acontece se o provedor de taxa cai no meio de uma liquidação?". A resposta deste PR é que nada acontece, porque a liquidação nunca fala com o provedor. Ela lê a taxa do banco, e o provedor só alimenta o banco em segundo plano.

## Por quê (decisões)
- **Liquidação lê do banco, refresh alimenta o banco** — chamar o provedor na liquidação colocaria uma dependência externa dentro da transação financeira. Se o provedor cair, a liquidação em USD segue até a taxa vencer (`fx.max-age`) e depois recusa com `503`. BRL nunca é afetado.
- **`404` na consulta e `503` na precificação para o mesmo fato (sem taxa vigente)** — em `GET /current`, "não há taxa" é a resposta correta sobre o recurso. Em `/quote` e na liquidação, o pedido é válido e falta uma dependência, que pode voltar.
- **Ordem Retry(CircuitBreaker(TimeLimiter(chamada)))** — cada tentativa conta para o circuito, então 2 refreshes com 3 tentativas abrem o circuito. `CallNotPermittedException` não é retentada: com o circuito aberto a resposta sai na hora, sem chamar o provedor.
- **Taxa inválida do provedor (nula ou ≤ 0) conta como falha do provedor** — ela abre o circuito e nunca chega ao banco.
- **Falha no refresh da subida não derruba a aplicação** — liquidação em BRL não depende de câmbio, então seria errado impedir a aplicação de subir.
- **Estratégias registradas por `@ComponentScan` filtrado por tipo** — o domínio segue sem `@Component`. Um tipo novo só exige a classe e o valor no enum. Uma lista manual de beans obrigaria a mudar mais uma classe a cada tipo (AC-PRC-14).
- **`Clock` injetado e `MutableClock` nos testes** — envelhecer a taxa em teste não exige `sleep` nem hook de produção.
- **`BigDecimal` serializado como string em um único customizer** — anotar cada campo seria fácil de esquecer num DTO novo.

## O que mudou
- `FxRateService`, `ResilientFxRateClient`, `MockFxRateProvider`, `FxRateRefreshJob`, `FxRateController`.
- `QuoteService`, `QuoteController` e a primeira versão de `ApiExceptionHandler`.
- `ApplicationConfig`, `FxResilienceConfig`, `FxProperties`, `PricingProperties`.

## Como testar
```bash
cd backend && ./mvnw verify
```
Dá para ver a resiliência em execução com `FX_MOCK_FAIL=true` ou `FX_MOCK_DELAY=PT5S` e `POST /api/fx-rates/refresh`.

## Critérios de aceite
AC-FX-01 a AC-FX-07; AC-PRC-08, 09, 10, 13, 15, 16 e 17.

## Riscos / fora de escopo
- Os 50 ms de AC-FX-07 não têm verificação automática: a skill `quality-gates` proíbe limite de tempo em teste de integração (R4 no `SPEC.md`).
- AC-FX-08, 09 e 10 dependem da liquidação e entram na próxima etapa.
- A validação (`400` com `errors[]`) entra em `feat/api-errors`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
