# PROGRESS — SRM Credit Engine

Estado do loop de `PROMPT.md`. Atualizado ao fim de cada etapa.

**Etapa atual:** 4 — `feat/persistence`
**Último comando:** merge do PR #3
**Próximo passo:** migrations, entidades e repositórios.

## Ambiente de execução do agente

- Caches de Maven e npm em `.cache/` (fora do git): `source .cache/env.sh` antes de rodar `mvnw`/`npm`.
  Motivo: o `PROMPT.md` limita a escrita a este diretório.
- `user.home` das execuções do Maven aponta para `.cache/home`, porque o Testcontainers grava `~/.testcontainers.properties`.
  A primeira execução chegou a criar esse arquivo fora do projeto; ele não existia antes e foi removido.

## Fatos para o "Log do agente" do `AI_USAGE.md`

- `PricingEngineTest#largeFactorKeepsPrecision`: o agente escreveu o valor esperado `140171.21` de cabeça, com um comentário dizendo que estava conferido. O teste falhou com `137850.54`. Um cálculo independente (Python `decimal`, prec=2000) deu `137850.5359…`: o motor estava certo e o teste, errado.
- Skill `golden-cases`: a conferência manual cita `92859,94 / 5,4321 = 17094,670…`. O valor real é `17094,6668…`. O resultado arredondado (`17094,67`) não muda.

## Etapas

| # | Branch | Status | PR | Observação |
|---|---|---|---|---|
| 0 | preparação (git init, remoto) | ✅ | — | `devsilasz/srm-credit-engine` privado |
| 1 | `docs/spec` | ✅ | #1 | |
| 2 | `chore/scaffold` | ✅ | #2 | CI verde com Testcontainers 1.21.4 |
| 3 | `feat/pricing-engine` | ✅ | #3 | |
| 4 | `feat/persistence` | em andamento | | |
| 5 | `feat/fx-rates` | | | |
| 6 | `feat/settlement` | | | |
| 7 | `feat/statement` | | | |
| 8 | `feat/api-errors` | | | |
| 9 | `feat/observability` | | | |
| 10 | `feat/frontend` | | | |
| 11 | `chore/docker-ci` | | | |
| 12 | `test/e2e` | | | |
| 13 | `test/contract` | | | |
| 14 | `test/perf` | | | |
| 15 | `docs/review-decisions` | | | |
| 16 | `chore/verify-all` | | | |

## BLOCKED

Nenhum.
