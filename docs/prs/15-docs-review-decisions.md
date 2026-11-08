## Contexto
O enunciado cobra `REVIEW.md`, `DECISIONS.md`, `AI_USAGE.md` e um README que seja "a cara do projeto". Tudo isso será questionado na defesa, então cada afirmação precisa ter lastro.

## Por quê (decisões)
- **`REVIEW.md` ordenado por impacto de negócio, não por categoria técnica** — SQL injection é grave, mas fica abaixo do bug de taxa, porque este erra 100% das operações sem nenhum atacante.
- **`REVIEW.md` não afirma o que o trecho não mostra** — a primeira versão dizia que o provedor era chamado no meio da liquidação, mas o Anexo A só mostra `fxService`. Virou uma pergunta para o autor do PR.
- **`AI_USAGE.md` como esqueleto** — as seções 1 a 3 são escolhas e opiniões do autor, então ficam como TODO. Só o "Log do agente" está preenchido, e só com fatos desta execução.
- **README sem números de carga** — a fonte é `docs/perf/REPORT.md`. Repetir no README seria criar uma segunda versão que envelhece.
- **Diagramas renderizados antes do merge** — os três blocos Mermaid foram renderizados com Mermaid 11 em um navegador real.

## O que mudou
- `README.md`, `REVIEW.md`, `DECISIONS.md`, `AI_USAGE.md`.
- `docker-compose.yml` e `.env.example`: `FX_MOCK_FAIL` e `FX_MOCK_DELAY` agora chegam ao backend. O README ensinava a usá-las, mas o compose não as repassava; a instrução foi testada de verdade (`503` com `Retry-After: 30`).
- `docs/acceptance/`: passos reproduzíveis nos itens `MANUAL`.
- IDs de critério nos testes e scripts que ainda não os citavam.

## Como testar
```bash
FX_MOCK_FAIL=true docker compose up -d backend
curl -i -X POST http://localhost:8080/api/fx-rates/refresh   # 503 com Retry-After
```

## Critérios de aceite
AC-SEC-04, AC-SEC-05, AC-SET-14 e AC-OPS-01, por documentação.

## Riscos / fora de escopo
O autor precisa preencher as seções 1 a 3 de `AI_USAGE.md` e revisar `docs/acceptance/README.md`, que segue marcado como rascunho.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
