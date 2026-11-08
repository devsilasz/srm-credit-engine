## Contexto
Os testes do backend não dizem se o operador consegue simular, liquidar e ver o resultado na tela. Um único fluxo de ponta a ponta, contra o compose real, cobre o painel, o Nginx, a API e o banco juntos.

## Por quê (decisões)
- **Um único teste com passos** — o fluxo do operador é um só, e cada passo cita o critério que prova. Vários testes independentes multiplicariam o custo sem pegar outra classe de erro.
- **Playwright em contêiner e pacote próprio em `frontend/e2e/`** — não instala navegador na máquina, e o `npm ci` do frontend não baixa o Playwright à toa.
- **Fora do CI, em `scripts/verify-all.sh`** — o fluxo precisa do compose inteiro, e o CI já cobre o resto. O motivo fica em `DECISIONS.md`.
- **Resposta perdida simulada com `route.fetch()` seguido de `abort`** — o servidor liquida e a resposta não chega. É exatamente o cenário de pagamento em dobro do Anexo B, e um simples erro de rede não provaria nada sobre a idempotência.

## Dois bugs que o teste revelou
1. **Duplo clique no mesmo tick.** `dblclick` passava só por causa do tempo entre os eventos, com o React re-renderizando no intervalo. Com dois `button.click()` seguidos, as duas mutações criavam dois recebíveis com a mesma chave. O backend recusou a segunda com `422`, então o dinheiro estava protegido, mas o operador via erro numa liquidação concluída e sobrava um recebível órfão. Conferido no banco e nos logs. A correção é uma trava síncrona em `useSettlementIntent`, porque o `disabled` só vale depois do re-render.
2. **`crypto.randomUUID` fora de contexto seguro.** Ele só existe em HTTPS ou `localhost`. Abrindo o painel pelo IP da máquina, ninguém conseguiria liquidar, e a mensagem crua do JavaScript aparecia na tela. A correção é um fallback para `crypto.getRandomValues`, e só erro vindo da API é mostrado como está.

## O que mudou
- `frontend/e2e/`: Dockerfile, configuração e `operator-flow.spec.ts`.
- `docker-compose.yml`: perfil `e2e`.
- `useSettlementIntent`, `lib/api.ts`: as duas correções acima.
- `docs/screenshots/`: telas geradas pelo teste.
- Grid: números na serifa, para os algarismos alinharem na coluna.

## Como testar
```bash
docker compose up -d --build
docker compose --profile e2e run --rm --build e2e
```

## Critérios de aceite
AC-UI-01 a AC-UI-08 e AC-UI-11. AC-UI-09 e AC-UI-10 seguem como verificação manual.

## Riscos / fora de escopo
O teste de AC-UI-01 usa o limite de 1 s do critério, porque aqui a latência é o que se quer provar. Em máquina muito carregada, ele pode falhar por tempo.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
