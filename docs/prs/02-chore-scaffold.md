## Contexto
Antes de qualquer regra de negócio, o projeto precisa de um build reproduzível e de um portão automático. A partir deste PR, nenhum merge entra sem CI verde.

## Por quê (decisões)
- **Spring Boot 3.5.x em vez de 4.x** — a linha 3.5 é a pedida para este case; a migração para 4.x traria risco sem ganho funcional. A justificativa completa entra no `DECISIONS.md`.
- **Maven Wrapper `only-script`** — não versiona um `.jar` binário no repositório; o script baixa a versão fixada de Maven.
- **Testes de integração com Postgres real (Testcontainers), sem H2** — o sistema depende de comportamento específico do Postgres (triggers, `NUMERIC`, `UNIQUE`). Um banco diferente esconderia justamente os bugs que importam.
- **Container único e estático em `AbstractIT`** — sobe uma vez por execução; cada contexto Spring reaproveita o mesmo banco, o que corta muito o tempo de CI.
- **Passos do frontend com `if: hashFiles(...)` em cada step** — `hashFiles` não funciona no `if` do job. Assim o job não falha enquanto o frontend não existir.

## O que mudou
- `backend/`: `pom.xml`, wrapper, Surefire (`*Test`), Failsafe (`*IT`), Spotless no `verify`, aplicação mínima e `ApplicationBootIT`.
- `.github/workflows/ci.yml`: jobs `backend` e `frontend`.

## Como testar
```bash
cd backend && ./mvnw verify   # precisa de Docker; 1 teste de integração verde
```

## Critérios de aceite
AC-OPS-05 (primeira parte: CI rodando em push).

## Riscos / fora de escopo
O relatório JaCoCo ainda não existe: o gate entra em `chore/docker-ci`, e até lá o upload do artifact é ignorado.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
