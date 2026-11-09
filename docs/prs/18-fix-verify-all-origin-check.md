## Contexto
O PR #18 adicionou ao smoke um POST pelo Nginx com `Origin`, para o `403` não voltar. O log da execução mostrava `ok POST pelo Nginx com Origin do navegador: 400`: a verificação passava com um status errado, então não protegia nada.

## Por quê (decisões)
- **Resposta em variável, fora do argumento** — no bash 3.2 do macOS, o `{a,b}` do JSON dentro de `"$(...)"` passa por expansão de chaves antes da substituição. O curl rodava duas vezes com JSON partido, e a chamada virava `assert_eq desc 400 400 200`. Como `$2` e `$3` coincidiam, dava "ok".
- **`assert_eq` exige exatamente 3 argumentos** — corrige a classe do problema, e não só esta linha. Um argumento multiplicado agora reprova em vez de passar calado.
- **Só esta asserção tinha o problema** — as outras montam o JSON numa atribuição (`C1=$(...)`), em que não há expansão de chaves. Conferido com grep.

## O que mudou
- `scripts/verify-all.sh`: asserção e `assert_eq`.
- `AI_USAGE.md`: caso registrado no log.

## Como testar
Controles executados com as linhas reais do script:

| Cenário | Resultado |
|---|---|
| POST pela porta 3000 | `ok`, status `200`, exit 0 |
| POST na 8080 com `Origin` de outra origem | reprova: esperado `200`, obtido `403`, exit 1 |
| `assert_eq` com 4 argumentos | reprova, exit 1 |

```bash
scripts/verify-all.sh --skip-perf
```

## Critérios de aceite
AC-OPS-01.

## Riscos / fora de escopo
A correção do Nginx em si não dependia desta asserção: o E2E pela porta 3000 ficou vermelho antes dela e verde depois. Carga e estresse não foram repetidos.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
