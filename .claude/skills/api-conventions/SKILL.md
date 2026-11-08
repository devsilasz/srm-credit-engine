---
name: api-conventions
description: Contrato HTTP do SRM Credit Engine — rotas, status codes, ProblemDetail, Idempotency-Key, paginação e OpenAPI. Use ao criar ou alterar controllers, DTOs, exception handler, ou ao escrever o REVIEW.md do Anexo A.
---

# Convenções de API

## Rotas

| Método | Rota | Sucesso | Erros |
|---|---|---|---|
| POST | `/api/pricing/quote` | `200` | `400` malformado ou inválido (face ≤ 0, mais de 2 casas, tipo desconhecido), `422` regra (vencimento ≤ hoje), `503` câmbio indisponível (USD) |
| POST | `/api/receivables` | `201` + `Location` | `400`, `404` cedente, `422` |
| GET | `/api/receivables/{id}` | `200` | `404` |
| POST | `/api/settlements` | `201` + `Location` (nova) ou `200` (replay idempotente) | `400` sem `Idempotency-Key` ou UUID inválido, `404` recebível, `409` já liquidado ou conflito de versão, `422` chave reutilizada com payload diferente, `503` câmbio |
| GET | `/api/settlements/{id}` | `200` | `404` |
| GET | `/api/settlements` | `200` paginado | `400` filtros inválidos (`from > to`, `size > 100`) |
| POST | `/api/fx-rates` | `201` | `400`, `422` (taxa ≤ 0) |
| POST | `/api/fx-rates/refresh` | `201` | `503` provedor indisponível ou circuito aberto (com `Retry-After`) |
| GET | `/api/fx-rates/current?pair=USD-BRL` | `200` | `404` sem taxa vigente |
| GET | `/api/assignors` | `200` | — |

Não existe `PUT`, `PATCH` nem `DELETE` em settlements. Isso é intencional (imutabilidade) e deve estar documentado no OpenAPI.

## Erros: `ProblemDetail` (RFC 9457)

- Um único `@RestControllerAdvice`, estendendo `ResponseEntityExceptionHandler`, e `spring.mvc.problemdetails.enabled=true`.
- Campos: `type` (URI estável, ex. `https://srm.example/errors/receivable-already-settled`), `title`, `status`, `detail` (legível pelo operador) e `instance`. Extensões: `code` (ex. `RECEIVABLE_ALREADY_SETTLED`) e `errors[]` com `{field, message}` em validação.
- Exceções de domínio → status mapeados na tabela acima. Qualquer `Exception` não mapeada → `500` com `detail` genérico, **log ERROR com stack trace** e sem vazar mensagem interna.
- Proibido: `catch` vazio, `return ResponseEntity.ok(...)` em caminho de erro e corpo `{ "ok": false }`.

## Idempotency-Key

- Header obrigatório em `POST /api/settlements`, no formato UUID.
- `request_hash` = SHA-256 do payload canônico (`receivableId|paymentCurrency`, campos em ordem fixa).
- Replay (mesma chave e mesmo hash) → `200`, mesmo corpo, header `Idempotent-Replayed: true`.
- Documente o header no OpenAPI com `@Parameter(in = HEADER, required = true)`.

## Paginação

- Query: `page` (0-based), `size` (padrão 20, máx. 100) e `sort` (lista branca: `settledAt`, `faceValue`, `presentValue`; padrão `settledAt,desc`).
- Resposta: `{ content: [...], page, size, totalElements, totalPages }`. Não exponha o `Page` do Spring diretamente.
- Filtros do extrato: `from`/`to` (`LocalDate`, inclusivos, em `America/Sao_Paulo`), `assignorId` e `currency`.
- SQL nativo com parâmetros nomeados (**nunca concatenação**). `ORDER BY` vem só da lista branca.

## OpenAPI

- `springdoc` com `@Tag` por controller, `@Operation(summary, description)` e `@ApiResponse` para **cada** status da tabela, com exemplo de `ProblemDetail`.
- Exemplos de request com os golden cases (C1 no quote).
- `/swagger-ui.html` e `/v3/api-docs` liberados.
- `springdoc.api-docs.version=openapi_3_0`, para o spec ser compatível com o validador do `ContractIT`.

## Segurança mínima (sem auth real; ver DECISIONS.md)

- Bean Validation em todos os DTOs (`@NotNull`, `@Positive`, `@Digits(integer=17, fraction=2)`). Sem `@Future`: vencimento ≤ hoje é regra de negócio (`422`), verificada com `Clock` injetado.
- Limite de payload de 64 KB com um filtro, que devolve `413` em `ProblemDetail` e também cobre corpo sem `Content-Length` (`server.tomcat.max-swallow-size` não limita corpo JSON). `size` máximo da página.
- CORS só para `http://localhost:5173` em dev. No compose, o Nginx faz proxy e CORS não é necessário.
- Nenhum stack trace ou SQL no corpo de erro.
