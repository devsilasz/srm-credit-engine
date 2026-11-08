## Contexto
O enunciado lista "erro respondido com `200 OK`" e "exceção engolida" como anti-padrões, e o Anexo A faz as duas coisas. O cliente da API também precisa de um formato único de erro para mostrar uma mensagem legível ao operador, em vez de JSON cru.

## Por quê (decisões)
- **`400` para pedido malformado, `422` para regra de negócio** — face negativo nem chega a ser um pedido válido. Vencimento no passado é um pedido bem-formado que a regra recusa. Por isso não há `@Future`: a regra usa o `Clock` injetado.
- **`handleExceptionInternal` como ponto único dos erros do framework** — 405, 415 e rota inexistente ganham `code` e `type` no mesmo lugar, sem um handler para cada um.
- **Mensagem de corpo inválido montada à mão** — o texto do Jackson expõe nomes de classe Java. O operador vê o campo e os valores aceitos.
- **Remoção do `@Validated` em `FxRateController`** — no nível da classe ele troca a validação nativa do Spring por um proxy AOP, que lança `ConstraintViolationException` e cairia no `500`.
- **Filtro de payload lê os bytes** — um corpo chunked não declara `Content-Length`, e `server.tomcat.max-swallow-size` não limita corpo JSON.
- **`BigDecimal` como `StringSchema` no OpenAPI** — o contrato precisa dizer o que o JSON faz. Sem isso, o `ContractIT` da etapa 13 reprovaria toda resposta com dinheiro.
- **`OpenApiCustomizer` para o corpo de erro** — o formato é único, então é descrito uma vez. Cada controller só declara o status e o motivo.

## O que mudou
- `ApiExceptionHandler` completo, `PayloadLimitFilter`, `OpenApiConfig` e anotações nos controllers.
- CORS só para o Vite (`localhost:5173`).
- `ApiErrorsIT`.

## Como testar
```bash
cd backend && ./mvnw verify
```
Com a aplicação rodando: `http://localhost:8080/swagger-ui.html`.

## Critérios de aceite
AC-API-01, AC-API-02, AC-PRC-11, AC-PRC-12, AC-SEC-03.

## Riscos / fora de escopo
Autenticação fica fora (`DECISIONS.md`). A conferência automática do contrato entra em `test/contract`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
