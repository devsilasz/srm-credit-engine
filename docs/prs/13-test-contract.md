## Contexto
O OpenAPI é o que um consumidor lê. Se ele diverge da API real, a documentação vira um erro silencioso. Este teste valida o tráfego real contra o spec que a própria aplicação publica.

## Por quê (decisões)
- **Spec baixado de `/v3/api-docs`, sem arquivo versionado** — compara a API com o contrato que ela publica de fato. Um snapshot exigiria manutenção sem pegar outra classe de erro (por isso AC-API-06 foi removido).
- **`validate` para pedidos válidos e `validateResponse` para os inválidos de propósito** — o cenário "sem `Idempotency-Key`" viola o contrato do pedido de propósito. Só a resposta dele precisa conformar.
- **Status conferidos contra a tabela da skill `api-conventions`** — cada um é declarado no spec, inclusive os que nenhum cenário dispara.
- **Controle negativo com dinheiro em número** — um validador que não lê o corpo passaria em tudo. `validatorRejectsMoneyAsJsonNumber` prova que ele lê. A decisão vem da experiência da etapa 11, em que o gate de cobertura passou sem medir nada.

## Um defeito que o teste revelou
O schema `Problem` não estava no spec publicado. Todas as respostas de erro referenciavam um schema inexistente, e o Swagger UI as mostraria sem corpo. O springdoc descarta os `components` do bean `OpenAPI` ao montar o documento, então o schema passou a ser registrado no próprio `OpenApiCustomizer`.

## O que mudou
- `ContractIT` e a dependência `swagger-request-validator-core`.
- `OpenApiConfig`: registro do schema `Problem`.

## Como testar
```bash
cd backend && ./mvnw verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ContractIT
```

## Critérios de aceite
AC-API-03, AC-API-05, AC-SET-11.

## Riscos / fora de escopo
`413` e `500` são documentados em toda operação, mas o `ContractIT` não dispara nenhum dos dois. Eles são cobertos por `ApiErrorsIT`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
