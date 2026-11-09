## Contexto
O painel estava inutilizável em `http://localhost:3000`: toda simulação e toda liquidação recebiam `403 Invalid CORS request`. O autor encontrou o bug ao usar o sistema, depois de `scripts/verify-all.sh` ter saído com 0.

## Por quê (decisões)
- **`proxy_set_header Host $http_host`** — `$host` descarta a porta. O backend recebia `Host: localhost` e `Origin: http://localhost:3000`, tratava como outra origem e recusava, porque só `localhost:5173` está liberada. Com a porta preservada, o Spring reconhece a mesma origem e nem aplica CORS. Liberar `localhost:3000` no backend também resolveria nessa porta, mas continuaria quebrando quem abre o painel pelo IP da máquina.
- **E2E pela porta publicada (`host.docker.internal:3000`)** — o teste passava porque acessava `http://frontend`, na porta 80, em que `Host` e `Origin` coincidem. Agora ele faz o mesmo caminho do navegador do operador.
- **POST com `Origin` no smoke do `verify-all.sh`** — o smoke passava pelo Nginx só com um GET, e o navegador não envia `Origin` em GET. Esse caminho nunca era exercitado.

## Como o bug escapou
Três camadas de teste passavam, e nenhuma fazia o caminho real: testes de integração falam direto com o backend, o smoke usava a porta 8080 sem `Origin`, e o E2E usava outra URL. A lição: pelo menos um teste precisa entrar pela porta que o usuário usa.

## O que mudou
- `frontend/nginx.conf`: `$http_host`.
- `docker-compose.yml`: E2E pela porta publicada.
- `scripts/verify-all.sh`: POST com `Origin` no smoke.
- `AI_USAGE.md`: caso registrado no log.

## Como testar
```bash
docker compose up -d --build --wait
curl -i -X POST http://localhost:3000/api/pricing/quote -H 'Content-Type: application/json' \
  -H 'Origin: http://localhost:3000' \
  -d '{"faceValue":"100000.00","dueDate":"2030-01-15","type":"DUPLICATA_MERCANTIL","paymentCurrency":"BRL"}'
# 200; antes da correção, 403
docker compose --profile e2e run --rm --build e2e
```
O E2E com a nova URL ficou vermelho antes da correção e verde depois.

## Critérios de aceite
AC-UI-01 a AC-UI-08 e AC-UI-11, agora pela porta publicada; AC-OPS-01.

## Riscos / fora de escopo
Carga e estresse não foram repetidos: eles falam direto com `backend:8080`, sem passar pelo Nginx. `docs/perf/REPORT.md` continua sendo a execução do commit `ccd35f2`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
