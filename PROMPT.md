# PROMPT DO LOOP — SRM Credit Engine

> Como rodar, a partir da raiz deste diretório e com o Docker Desktop aberto:
>
> ```bash
> caffeinate -i claude --dangerously-skip-permissions --model claude-opus-5-5
> ```
>
> Dentro da sessão:
>
> ```text
> /loop Leia PROMPT.md e continue de docs/PROGRESS.md. Encerre o loop quando scripts/verify-all.sh sair com 0 ou só restarem itens BLOCKED.
> ```

---

Você é o engenheiro responsável por entregar o desafio técnico **SRM Credit Engine**. O enunciado completo está em `desafio-tecnico-srm-credit-engine-v2.md` na raiz do repositório. Leia esse arquivo inteiro antes de qualquer coisa, depois leia `CLAUDE.md`, todas as skills (inclusive `quality-gates`) em `.claude/skills/` e todos os critérios em `docs/acceptance/`.

## Execução autônoma

- **Este prompt é o design aprovado.** Não faça brainstorming, não peça aprovação de plano nem de design, e não use `AskUserQuestion`. Esta regra vale acima de qualquer skill de processo instalada.
- **Precedência em caso de conflito:** enunciado > `CLAUDE.md` > `PROMPT.md` > skills > `docs/acceptance/`. Registre o conflito em `SPEC.md`.
- **Estado:** ao iniciar ou retomar, leia `docs/PROGRESS.md` e `git status`, e continue de onde parou sem refazer etapa concluída. Atualize esse arquivo ao fim de cada etapa e antes de qualquer execução longa, com etapa atual, último comando, resultado e próximo passo.
- **Critério de saída:** o trabalho só termina quando `scripts/verify-all.sh`, sem flags, sair com código `0` depois de partir de `docker compose down -v`. Declarar pronto sem isso é proibido.
- **Limite de tentativas:** depois de 3 tentativas com a mesma causa, marque `BLOCKED` em `docs/PROGRESS.md` com comando, saída e hipóteses, e siga no que não depende disso. O relatório final lista todo `BLOCKED`.
- **Proibido para ficar verde:** alterar golden case, baixar meta de cobertura ou threshold, usar `@Disabled`/`skip`, excluir classe da cobertura, apagar teste ou reescrever critério para ele passar. Se um critério parecer errado, mantenha-o vermelho e registre em `SPEC.md`, na seção "Premissas (a revisar pelo autor)".

### Limites de segurança (a sessão roda sem pedir permissão)

- Escreva somente dentro deste diretório. Não instale nada globalmente (`brew`, `npm -g`) nem altere configurações do sistema, do git global ou do Claude Code.
- O único repositório remoto permitido é `devsilasz/srm-credit-engine`. **Nunca altere `devsilasz/desafio_srm`**, que é um projeto anterior, nem qualquer outro repositório.
- Proibido: `git push --force`, reescrever histórico já enviado, `gh repo delete`, mudar a visibilidade do repositório e convidar colaboradores.
- `docker compose down -v` só neste projeto. Proibido `docker system prune`, `docker volume prune` e parar contêineres de outros projetos. Não mexa no Postgres nativo desta máquina.
- Não leia nem imprima tokens ou credenciais, e não os grave em arquivos.

## Objetivo

Entregar um repositório que atenda **todo o nível Pleno** e os itens **Sênior** listados abaixo. Ele precisa subir com **um único comando** em uma máquina que tenha só Docker:

```bash
docker compose up -d --build
```

Depois disso, sem nenhum passo manual:
- Frontend: http://localhost:3000
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Métricas: http://localhost:8080/actuator/prometheus

## Regra de ouro

**Menos código, bem justificado.** A rubrica pune volume. Não crie camadas vazias, interfaces sem uma segunda implementação justificável, microserviços, mensageria nem testes de happy path em massa. Cada arquivo precisa ter um motivo que o autor consiga explicar ao vivo em 30 segundos.

## Estado inicial

Se já existir código em `backend/` (por exemplo `domain/pricing`), **continue a partir dele**: leia, valide contra as skills e corrija o que violar as regras em vez de reescrever do zero. Registre no `AI_USAGE.md` tudo que precisou ser corrigido.

## Stack (não trocar sem registrar em DECISIONS.md)

**Backend:**
- Java 21, Spring Boot 3.5.x (última linha 3.x; registre em DECISIONS.md que ela está fora do suporte open source e por que não migrar para 4.x neste case), Maven com Maven Wrapper em `backend/` (`cd backend && ./mvnw`).
- PostgreSQL 16 com migrations Flyway.
- Spring Data JPA para escrita (`@Version` para optimistic locking) e `JdbcTemplate`/`NamedParameterJdbcTemplate` com SQL nativo para o extrato.
- Bean Validation e `ProblemDetail` (RFC 9457) com `@RestControllerAdvice`.
- springdoc-openapi para o Swagger, com `springdoc.api-docs.version=openapi_3_0` (o padrão do springdoc 2.8+ é 3.1, e o validador de contrato tem problemas relatados com 3.1).
- Resilience4j programático (timeout, retry e circuit breaker) no provedor de câmbio mockado.
- Actuator, Micrometer Prometheus e logs JSON (formato estruturado nativo do Spring Boot 3.4+, `logging.structured.format.console=ecs`).
- Testes com JUnit 5, AssertJ e Testcontainers (Postgres real; **H2 é proibido**). Surefire roda `*Test` (unitários) e Failsafe roda `*IT` (integração). Se o CI acusar `client version ... is too old` (Docker 29), suba para Testcontainers 2.x e registre em `AI_USAGE.md`, na seção "Log do agente".
- Contrato: `com.atlassian.oai:swagger-request-validator-core` validando respostas reais contra o OpenAPI gerado.
- Cobertura: JaCoCo com gate no `verify` (metas na seção "Testes e quality gates").
- Carga e estresse: k6 rodando em container (`grafana/k6`), sem instalação local.
- Lint com Spotless (google-java-format) no `mvn verify`.

**Frontend:**
- React 18, TypeScript (strict), Vite.
- TanStack Query para dados do servidor; React Hook Form e Zod para formulários.
- `decimal.js` para qualquer exibição ou manipulação de valor no cliente. O cliente **não precifica**: a simulação chama a API.
- Sem Redux nem estado global. Justificar no README.
- Build servido por Nginx, que faz proxy de `/api` para o backend (sem CORS em produção local).
- ESLint no CI.
- Playwright em `frontend/e2e/`, com um único fluxo do operador rodando contra o compose. Não adicione framework de teste unitário no frontend. Esse teste roda em `scripts/verify-all.sh`, não no CI (registre o motivo em DECISIONS.md).

**Infra:**
- `docker-compose.yml` com `db`, `backend` e `frontend`, healthchecks e `depends_on: condition: service_healthy`.
- Dockerfiles multi-stage. O build do backend usa `-DskipTests` (os testes rodam no CI e localmente, porque Testcontainers dentro de `docker build` exige Docker-in-Docker; registrar isso em DECISIONS.md).
- `.env.example` com as variáveis e valores padrão no compose, para funcionar sem `.env`.
- GitHub Actions, com `on: push` em qualquer branch: `backend` (`./mvnw verify` com `working-directory: backend` e upload do relatório JaCoCo como artifact) e `frontend` (`npm ci && npm run lint && npm run build`). Enquanto `frontend/package.json` não existir, pule os passos do frontend com `if: hashFiles('frontend/package.json') != ''` **em cada step** (`hashFiles` não funciona no `if` do job). Carga e estresse **não** rodam no CI (runner compartilhado gera números sem significado); rodam localmente e o relatório fica versionado. Registre isso em DECISIONS.md.

### Compose de referência (ponto de partida; ajuste se necessário)

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${DB_NAME:-srm}
      POSTGRES_USER: ${DB_USER:-srm}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-srm}
    # Sem `ports`: a 5432 pode estar ocupada na máquina. Acesso via `docker compose exec db psql`.
    volumes: [db-data:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-srm} -d ${DB_NAME:-srm}"]
      interval: 5s
      retries: 20
  backend:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${DB_NAME:-srm}
      SPRING_DATASOURCE_USERNAME: ${DB_USER:-srm}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-srm}
    ports: ["8080:8080"]
    depends_on:
      db: { condition: service_healthy }
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q UP"]
      interval: 5s
      retries: 30
  frontend:
    build: ./frontend
    ports: ["3000:80"]
    depends_on:
      backend: { condition: service_healthy }
  k6:                      # só sobe com --profile perf; não afeta o `up -d`
    image: grafana/k6:0.54.0
    profiles: ["perf"]
    environment:
      BASE_URL: http://backend:8080
    volumes: ["./perf:/scripts", "./docs/perf:/results"]
    depends_on:
      backend: { condition: service_healthy }
volumes:
  db-data:
```

A imagem de runtime do backend precisa ter `wget` (use `eclipse-temurin:21-jre-alpine`). O `nginx.conf` do frontend faz `location /api/ { proxy_pass http://backend:8080; }` e `try_files $uri /index.html` para a SPA. Funciona em Mac (inclusive Apple Silicon) e Linux: não use imagens só `amd64`.

## Escopo funcional

Siga o enunciado (seções 4.1, 4.2 e 4.3) e as premissas do `CLAUDE.md`. Em resumo:

1. **Câmbio:** tabela `fx_rate` (append-only) com par, taxa `NUMERIC(19,8)`, `valid_from` e `source` (`MANUAL`/`PROVIDER`).
   - Endpoints: `POST /api/fx-rates` (manual), `POST /api/fx-rates/refresh` (integração mockada) e `GET /api/fx-rates/current?pair=USD-BRL`.
   - O `MockFxRateProvider` devolve `5.4321` por padrão, configurável (`fx.mock.rate`, `fx.mock.delay`, `fx.mock.fail`), e é envolvido por Resilience4j.
   - O refresh roda no startup e a cada 10 min.
   - A liquidação lê a taxa vigente do banco, sem chamar o provedor. Taxa vencida (> `fx.max-age`) ou inexistente → `503` e nada é gravado. Detalhes no `CLAUDE.md`.
2. **Precificação:** `POST /api/pricing/quote` (simulação, sem persistir) usando o padrão Strategy por tipo de recebível. Golden cases C1, C2 e C3 ao centavo.
3. **Recebíveis e cedentes:** cadastro mínimo (`POST /api/receivables`, `GET /api/receivables/{id}`), `GET /api/assignors` para o painel e os filtros, e seed de 2–3 cedentes via Flyway. Rotas e status completos estão na skill `api-conventions`.
4. **Liquidação:** `POST /api/settlements` com header `Idempotency-Key` obrigatório e `GET /api/settlements/{id}` (destino do `Location`).
   - Transação única. A tabela `settlement` é imutável (sem UPDATE/DELETE pela aplicação, mais um trigger no banco que bloqueia UPDATE/DELETE).
   - Grava valores, taxa de câmbio usada (valor e id), taxa base, spread, prazo, timestamps e `assignor_id` (necessário para o índice do extrato).
   - Optimistic locking em `receivable`.
   - `UNIQUE(idempotency_key)` e `UNIQUE(receivable_id)` em `settlement`.
   - Mesma chave com mesmo payload: `200` com a mesma liquidação. Mesma chave com payload diferente: `422`. Recebível já liquidado: `409`.
5. **Extrato:** `GET /api/settlements?from&to&assignorId&currency&page&size&sort` com SQL nativo, paginação server-side e índices justificados na migration.
6. **Frontend:** painel do operador com simulação em tempo real (debounce de 300 ms chamando `/quote`), botão de liquidar e grid com paginação e filtros server-side.
   - Liquidar tem duas chamadas: `POST /api/receivables` e depois `POST /api/settlements`.
   - A **intenção de liquidação** guarda `receivableId` e `Idempotency-Key` (`crypto.randomUUID()`). Se a segunda chamada falhar, um novo clique reaproveita os dois e não cria outro recebível (AC-UI-11). A intenção só é descartada após o sucesso ou quando o operador altera o formulário.

## Itens Sênior incluídos

- Optimistic locking com teste concorrente: duas threads e `CountDownLatch` liquidando o mesmo recebível; exatamente 1 sucesso e 1 `409`.
- Métricas de negócio: `credit_engine_settlements_total{currency,type,outcome}` e `credit_engine_pricing_duration` (timer).
- Logs estruturados com `settlementId`, `receivableId` e `idempotencyKey` no MDC.
- Resiliência com testes:
  - Provedor lento → timeout → refresh responde `503` e o circuit breaker abre após N falhas.
  - Taxa vencida → liquidação USD responde `503` com zero linhas em `settlement`, enquanto a liquidação BRL segue funcionando.
- CI e diagramas C4 nível 1 e 2 em Mermaid no README.

## Testes e quality gates (obrigatório)

A pirâmide e a regra de cada camada ficam na skill `quality-gates`. Resumo:

| Camada | Ferramenta | Onde | Gate |
|---|---|---|---|
| Unitário | JUnit 5 + AssertJ | `*Test`, domínio sem Spring | `mvn verify` |
| Integração | Testcontainers (Postgres) | `*IT` | `mvn verify` |
| Contrato | swagger-request-validator + `/v3/api-docs` | `ContractIT` | `mvn verify` |
| Cobertura | JaCoCo (unit + IT mesclados) | `target/site/jacoco-merged` | **≥ 70% de linhas no projeto** e **≥ 90% de linhas e branches em `domain.pricing`**; o build falha abaixo disso |
| Carga | k6 | `perf/load.js` | thresholds do k6 (o run falha se estourar) |
| Estresse | k6 + checagem de integridade SQL | `perf/stress.js` e `perf/integrity.sql` | zero duplicidade e zero liquidação pela metade após o estresse |
| E2E | Playwright | `frontend/e2e/` | fluxo do operador contra o compose, em `scripts/verify-all.sh` |
| Lint | Spotless + ESLint | CI | `mvn verify` / `npm run lint` |

**Carga e estresse precisam ser executados de verdade.** Os relatórios com os números reais ficam em `docs/perf/`. O `stress.csv` bruto não é versionado (passa do limite de arquivo do GitHub); versione o agregado `stress-by-step.csv`. É proibido escrever no README ou em qualquer documento um número que não tenha saído de um run salvo em `docs/perf/`.

**Fora de escopo** (registrar em DECISIONS.md com justificativa): autenticação real, mensageria/EDA, ADRs extensos, design para 1M tx/min, multi-tenant e outras moedas além de BRL/USD.

## Documentação obrigatória

Na raiz do repositório:

- `README.md`: o que é, como rodar (um comando), URLs, stack com justificativa, C4 L1/L2 em Mermaid, diagrama ER em Mermaid, como rodar os testes e mapa do repositório.
- `SPEC.md` (1–2 páginas): premissas, perguntas ao negócio, decisões de precisão numérica e critérios de aceite resumidos com links para `docs/acceptance/`.
- `REVIEW.md`: review do Anexo A seguindo a skill `api-conventions` e a ordem de severidade do `CLAUDE.md`.
- `DECISIONS.md`: cortes e simplificações com motivo.
- `AI_USAGE.md`: **deixe como esqueleto com seções e TODOs**. Não invente casos de erro da IA. Registre apenas fatos reais desta execução (ex.: um teste que falhou e o que estava errado) em uma seção "Log do agente", para o autor revisar e reescrever com a própria voz.
- `docs/acceptance/`: mantenha os arquivos. Para cada critério, preencha a coluna `Teste` com o caminho do teste que o cobre, ou marque `MANUAL` com o passo de verificação.

## Processo (siga na ordem; cada etapa em uma branch e um merge)

Use a skill `git-workflow`.

**Preparação (uma vez; pule o que já estiver feito):**
1. Confirme que o Docker responde (`docker info`).
2. Rode `git init -b main` e faça o commit inicial do kit: enunciado, `CLAUDE.md`, `PROMPT.md`, `.claude/`, `docs/acceptance/` e `.gitignore`.
3. Crie o remoto privado e envie: `gh repo create devsilasz/srm-credit-engine --private --source=. --remote=origin --push`. Se o repositório já existir, confira se é este histórico antes de usar.

Branches e etapas:

1. `docs/spec`: SPEC.md e revisão dos critérios em `docs/acceptance/`.
2. `chore/scaffold`: `pom.xml`, Maven Wrapper em `backend/`, Spotless, Surefire/Failsafe, aplicação mínima com um `*IT` de subida (Testcontainers) e `.github/workflows/ci.yml`. A partir daqui, todo PR passa pelo CI.
3. `feat/pricing-engine`: domínio, strategies e testes dos golden cases e de borda.
4. `feat/persistence`: migrations, entidades e repositórios.
5. `feat/fx-rates`: câmbio e resiliência.
6. `feat/settlement`: liquidação, idempotência, locking e testes de integração.
7. `feat/statement`: extrato com SQL nativo.
8. `feat/api-errors`: ProblemDetail, validação e OpenAPI completo.
9. `feat/observability`: métricas e logs.
10. `feat/frontend`: painel e grid.
11. `chore/docker-ci`: compose, Dockerfiles e gate do JaCoCo no `verify`.
12. `test/e2e`: fluxo do operador com Playwright contra o compose e screenshot em `docs/screenshots/`.
13. `test/contract`: `ContractIT` e ajustes no OpenAPI que o teste revelar.
14. `test/perf`: `perf/seed.sql`, scripts k6 de carga e estresse, `integrity.sql`, execução real e relatório em `docs/perf/`.
15. `docs/review-decisions`: REVIEW.md, DECISIONS.md, README.md (com a seção "Qualidade e testes") e esqueleto do AI_USAGE.md.
16. `chore/verify-all`: `scripts/verify-all.sh`, que automatiza o loop abaixo e a skill `definition-of-done`.

Em cada etapa:
1. Releia os critérios de `docs/acceptance/` afetados.
2. Escreva o teste antes ou junto com o código.
3. Rode `cd backend && ./mvnw verify` e, se a etapa tocar o frontend, `cd frontend && npm run lint && npm run build`. **Não avance com nada vermelho.**
4. Commits atômicos com Conventional Commits.
5. Escreva a descrição do PR em `docs/prs/NN-<branch>.md` com contexto, **porquê**, o que mudou, como testar e riscos.
6. Abra o PR de verdade: `git push -u origin <branch>` e `gh pr create --base main --body-file docs/prs/NN-<branch>.md`.
7. A partir da etapa 2, espere o CI com `gh pr checks --watch`. Se ficar vermelho, corrija na branch. Com tudo verde, rode `gh pr merge --merge --delete-branch` e atualize a `main` local.
8. Atualize `docs/PROGRESS.md`.

Se o GitHub estiver indisponível, faça `git merge --no-ff` local, registre `BLOCKED` em `docs/PROGRESS.md` e envie o que faltar quando ele voltar.

## Loop de verificação final (repita até tudo passar)

`scripts/verify-all.sh` executa os passos 1 a 6 em ordem e para na primeira falha, mostrando qual passo falhou. A flag `--skip-perf` serve só para depurar; a conclusão exige uma execução sem flags. Recrie o banco antes da carga, porque o smoke e o E2E deixam dados.

1. `cd backend && ./mvnw verify` verde, com golden cases C1, C2 e C3 passando.
2. `docker compose down -v && docker compose up -d --build`, depois aguardar os healthchecks.
3. Smoke test com curl e mostrar as saídas:
   - `GET /actuator/health` → `UP`
   - `POST /api/pricing/quote` com C1 → `92859.94`; C3 → `17094.67`
   - `POST /api/settlements` duas vezes com a mesma `Idempotency-Key` → mesma resposta e uma linha só no banco (`docker compose exec db psql ... -c "select count(*) from settlement"`)
   - Sem `Idempotency-Key` → `400`
   - Recebível inexistente → `404` com `ProblemDetail`
   - `curl -L /swagger-ui.html` → `200` (o springdoc redireciona antes)
   - `GET http://localhost:3000` → `200`
   - Playwright em `frontend/e2e/` verde: simulação, liquidação e atualização do grid
4. Rode carga e estresse contra o compose (comandos na skill `quality-gates`), depois `perf/integrity.sql`. Confirme que os thresholds de carga passaram e que a integridade pós-estresse está limpa. Atualize `docs/perf/REPORT.md`.
5. Leia `backend/target/site/jacoco-merged/jacoco.csv` e confirme as metas de cobertura com os números reais.
6. Rode `grep -rnE "\b(double|float|Double|Float)\b" backend/src/main` e confirme que **nenhuma** ocorrência envolve valores monetários ou taxas.
7. Siga a checklist da skill `definition-of-done` item por item.
8. Ao final, imprima um relatório com: critérios de aceite atendidos e pendentes, comandos executados e resultados, e decisões que o autor precisa revisar antes da defesa.

## O que você NÃO decide sozinho

Se encontrar uma ambiguidade nova que não esteja no `CLAUDE.md`, escolha a opção mais conservadora para o negócio (recusar em vez de liquidar errado), registre em `SPEC.md` na seção "Premissas (a revisar pelo autor)" e siga em frente. Não pare para perguntar.
