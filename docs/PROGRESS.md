# PROGRESS — SRM Credit Engine

Estado do loop de `PROMPT.md`. Atualizado ao fim de cada etapa.

**Etapa atual:** concluído; só resta a revisão do autor
**Último comando anterior ao relatório de cobertura:** `scripts/verify-all.sh --skip-perf` → passos verdes (exit 2 por causa da flag). A última execução sem flags foi no commit `ccd35f2`, antes da correção do Nginx.
**Próximo passo (autor):** preencher as seções 1 a 3 de `AI_USAGE.md`, assumir os critérios em `docs/acceptance/README.md` e revisar a seção 5 do `SPEC.md`.

## Ambiente de execução do agente

- Caches de Maven e npm em `.cache/` (fora do git): `source .cache/env.sh` antes de rodar `mvnw`/`npm`.
  Motivo: o `PROMPT.md` limita a escrita a este diretório.
- `user.home` das execuções do Maven aponta para `.cache/home`, porque o Testcontainers grava `~/.testcontainers.properties`.
  A primeira execução chegou a criar esse arquivo fora do projeto; ele não existia antes e foi removido.

- O projeto está no Desktop sincronizado pelo iCloud (atributo `com.apple.file-provider-domain-id` em `~/Desktop`). A sincronização criou 130 cópias `* 2.class` em `backend/target` e quebrou o `verify` local com `TestData 2 (wrong name)`, além de dois diretórios vazios em `backend/src` (`main 2`, `test 2`), já removidos.
  Mitigação local, fora do git: `backend/target`, `frontend/node_modules` e `.cache` são symlinks para diretórios `.nosync`, e `.git` é um gitfile que aponta para `.git.nosync` (o iCloud chegou a criar `.git/index 2`). Exclusões em `.git.nosync/info/exclude`. **Recomendação ao autor:** mover o repositório para fora do Desktop/Documentos.

## Fatos para o "Log do agente" do `AI_USAGE.md`

- `PricingEngineTest#largeFactorKeepsPrecision`: o agente escreveu o valor esperado `140171.21` de cabeça, com um comentário dizendo que estava conferido. O teste falhou com `137850.54`. Um cálculo independente (Python `decimal`, prec=2000) deu `137850.5359…`: o motor estava certo e o teste, errado.
- Skill `golden-cases`: a conferência manual cita `92859,94 / 5,4321 = 17094,670…`. O valor real é `17094,6668…`. O resultado arredondado (`17094,67`) não muda.

- `SettlementConcurrencyIT#sameKeyBothGetTheSameSettlement` falhou com `[201, 409]` em 1 de 5 repetições na primeira versão de `SettlementService`, escrita pelo agente. Causa: a perdedora lia a chave antes do commit da vencedora e o recebível depois dele, e respondia `409` sem reler a chave. O `@RepeatedTest(5)` pegou o bug; uma execução única poderia ter passado.

- Etapa 8: o agente encadeou `verify | grep ... && git commit`, e o `&&` viu o código de saída do `grep`. O `verify` local tinha falhado (cópias do iCloud em `target/`), mas o commit e o merge do PR #8 seguiram. O CI passou no mesmo código. Depois disso, o `verify` passou a rodar por um script que devolve o código real do Maven.

- Etapa 11: com o symlink `target -> target.nosync`, o `fileSet` do `merge` não encontrou os `.exec`. `merge`, `report` e `check` do JaCoCo pularam e o build deu `BUILD SUCCESS` sem medir nada. Corrigido com `followSymlinks`; o upload do CI agora usa `if-no-files-found: error` para quebrar se o relatório faltar.

- Etapa 12: `crypto.randomUUID` não existe fora de contexto seguro, e o Playwright no contêiner acessa `http://frontend`. A liquidação falhava com a mensagem crua do JavaScript. Corrigido na aplicação, com fallback para `crypto.getRandomValues`.
- Etapa 12: o teste de duplo clique com `dblclick` passava só por causa do tempo entre os eventos. Com dois `button.click()` no mesmo tick, as duas mutações criavam dois recebíveis com a mesma chave. O backend recusou a segunda com `422` (a idempotência do servidor segurou o dinheiro), mas o operador via erro numa liquidação concluída e sobrava um recebível órfão. Corrigido com uma trava síncrona em `useSettlementIntent`.

- Etapa 12: `git add -A` levou para a `main` 8 cópias de conflito do iCloud (`frontend/vite.config 2.ts` e outras) no PR #12. Foram removidas por um commit normal, sem reescrever histórico, e o padrão entrou nas exclusões locais.

- Etapa 13: o `ContractIT` revelou que o schema `Problem` não estava no spec publicado: todas as respostas de erro referenciavam algo inexistente, porque o springdoc descarta os `components` do bean `OpenAPI`. Corrigido registrando o schema no `OpenApiCustomizer`.

- Pós-entrega: o autor encontrou `403` ao usar o painel em `localhost:3000`. Causa em `frontend/nginx.conf` (`$host` sem porta). O `verify-all.sh` e o E2E não pegaram porque não usavam a porta publicada com `Origin`. Detalhes em `AI_USAGE.md`.

- Pós-entrega: a asserção de `Origin` do PR #18 não testava nada (expansão de chaves no bash 3.2 fazia `$2` e `$3` coincidirem). Corrigida no PR #19, com `assert_eq` exigindo 3 argumentos.

- Pós-entrega: a pedido do autor, o histórico da `main` foi reescrito para remover a coautoria das mensagens e deixar `devsilasz` como autor e committer de todos os commits, inclusive os merges. É uma exceção autorizada à proibição de `git push --force` do `PROMPT.md`. Conteúdo e datas de cada commit não mudaram (conferido por comparação de trees). Os hashes mudaram, e as abas "Commits" dos PRs ainda mostram os commits antigos.

- Pós-entrega: a primeira medição por camada saiu com exit 0 e números errados (125 testes na integração, 725 linhas locais contra 656 no E2E, por classes gravadas em `target/classes` pela extensão Java do VS Code). O gerador agora falha se as medições não forem comparáveis. O texto do relatório também revelou que `POST /api/receivables` com `400` não tinha teste; ele foi adicionado.

## Etapas

| # | Branch | Status | PR | Observação |
|---|---|---|---|---|
| 0 | preparação (git init, remoto) | ✅ | — | `devsilasz/srm-credit-engine` privado |
| 1 | `docs/spec` | ✅ | #1 | |
| 2 | `chore/scaffold` | ✅ | #2 | CI verde com Testcontainers 1.21.4 |
| 3 | `feat/pricing-engine` | ✅ | #3 | |
| 4 | `feat/persistence` | ✅ | #4 | |
| 5 | `feat/fx-rates` | ✅ | #5 | |
| 6 | `feat/settlement` | ✅ | #6 | |
| 7 | `feat/statement` | ✅ | #7 | |
| 8 | `feat/api-errors` | ✅ | #8 | merge com `verify` local vermelho por causa do iCloud; CI verde |
| 9 | `feat/observability` | ✅ | #9 | |
| 10 | `feat/frontend` | ✅ | #10 | |
| 11 | `chore/docker-ci` | ✅ | #11 | |
| 12 | `test/e2e` | ✅ | #12 | levou cópias do iCloud; removidas no PR seguinte |
| 13 | `test/contract` | ✅ | #14 | |
| 14 | `test/perf` | ✅ | #15 | quebra não atingida até 800 req/s |
| 15 | `docs/review-decisions` | ✅ | #16 | |
| 16 | `chore/verify-all` | ✅ | #17 | `scripts/verify-all.sh` sem flags → exit 0 |
| 17 | `fix/nginx-host-header` | ✅ | #18 | `403` no painel, encontrado pelo autor; `verify-all.sh --skip-perf` verde, sem repetir carga e estresse |
| 18 | `fix/verify-all-origin-check` | ✅ | #19 | asserção do PR #18 não testava nada |
| 19 | `docs/coverage-report` | ✅ | #20 | cobertura por camada em `docs/coverage/REPORT.md`, pedido do autor |
| 20 | `test/raise-layer-coverage` | em andamento | | cobertura de unitário, contrato e E2E, pedido do autor |

## BLOCKED

Nenhum.
