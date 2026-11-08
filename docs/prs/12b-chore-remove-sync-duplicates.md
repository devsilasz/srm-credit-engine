## Contexto
O projeto está numa pasta sincronizada pelo iCloud, que cria cópias de conflito como `vite.config 2.ts`. Um `git add -A` levou 8 delas para a `main` no PR #12.

## Por quê (decisões)
- **Remoção por commit normal** — o histórico já foi enviado, e reescrevê-lo é proibido neste projeto. O erro fica visível, e o PR explica o que aconteceu.
- **Nada de código muda** — os arquivos removidos são cópias antigas dos originais, que continuam no lugar.

## O que mudou
Remove `frontend/.env 2.development`, `eslint.config 2.js`, `index 2.html`, `package 2.json`, `src/main 2.tsx`, `src/styles 2.css`, `tsconfig 2.json` e `vite.config 2.ts`.

## Como testar
```bash
git ls-files | grep -E ' [0-9]+(\.|/|$)'   # sem saída
cd frontend && npm ci && npm run lint && npm run build
```

## Critérios de aceite
Nenhum: limpeza.

## Riscos / fora de escopo
A proteção contra novas cópias é local a esta máquina (exclusões do git e diretórios `.nosync`). Quem clonar o repositório fora de uma pasta sincronizada não tem esse problema.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
