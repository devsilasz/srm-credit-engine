## Contexto
O operador precisa ver o valor líquido enquanto digita, liquidar sem medo de pagar duas vezes e conferir o histórico. O risco do Anexo A também existe no cliente: um `number` em dinheiro ou um duplo clique bastam para errar.

## Por quê (decisões)
- **Sem Redux nem estado global** — o estado do servidor fica no TanStack Query, o do formulário no React Hook Form e os filtros na URL. Não sobra nada que precise ser global.
- **O cliente não precifica** — a simulação sempre chama a API. Duplicar a fórmula em TypeScript criaria dois motores que podem divergir.
- **Dinheiro como string; `decimal.js` só onde há conta** — o `Intl.NumberFormat` recebe a string direto. O ESLint barra `parseFloat` e `toFixed`, então o erro aparece no lint, e não em produção.
- **Intenção de liquidação (AC-UI-11)** — liquidar são duas chamadas. Se a segunda falha, o novo clique reaproveita o recebível e a mesma `Idempotency-Key`. Gerar a chave no clique pagaria em dobro em um retry.
- **Botão bloqueado depois do sucesso até o formulário mudar** — um clique tardio não seria replay: criaria outro recebível e pagaria de novo. Esse caso não estava nos critérios e foi tratado aqui.
- **Liquidar exige simulação atual** — o botão só habilita quando o número na tela corresponde ao formulário, depois do debounce. O operador liquida o que está vendo.
- **Filtros na URL com `pushState`, sem React Router** — não há outra rota. Uma dependência a mais só serviria para um hook de 20 linhas.
- **Sem framework de teste unitário no frontend** — a regra de negócio está no backend. O fluxo é coberto por Playwright em `test/e2e`.
- **Identidade visual do domínio** — a simulação é um borderô (a conta em coluna) sobre um fundo de papel de segurança de cheque, e a confirmação é um carimbo. Fontes empacotadas com `@fontsource`, sem depender da rede em tempo de execução.

## O que mudou
`frontend/`: Vite, React 18, TypeScript estrito, ESLint, painel do operador e grid de liquidações.

## Como testar
```bash
cd frontend && npm ci && npm run lint && npm run build
```
Em desenvolvimento, `npm run dev` usa o CORS do backend para `localhost:5173`.

## Critérios de aceite
AC-UI-01 a AC-UI-11. A verificação automática entra em `test/e2e`.

## Riscos / fora de escopo
- Ordenação por coluna no grid ficou de fora: a API aceita `sort`, mas o enunciado pede só paginação e filtros.
- `crypto.randomUUID` exige contexto seguro: funciona em `localhost` e em HTTPS, não em HTTP por IP.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
