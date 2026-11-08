## Contexto
A entrega precisa subir com um único comando numa máquina que só tem Docker. E a cobertura precisa ser um portão de verdade: um número que ninguém verifica não protege nada.

## Por quê (decisões)
- **Build com `-DskipTests`** — Testcontainers dentro do `docker build` exigiria Docker-in-Docker. Os testes rodam no CI e no `./mvnw verify`.
- **`dependency:go-offline` em camada própria** — mudar só o código reaproveita o cache e não baixa as dependências de novo.
- **Sem `ports` no Postgres** — a 5432 pode estar ocupada na máquina do avaliador, e nada fora do compose precisa falar com o banco.
- **`depends_on: service_healthy`** — o backend só sobe com o banco pronto, e o frontend só sobe com o backend pronto. Nenhuma espera manual.
- **Unit + IT mesclados no JaCoCo** — idempotência, locking e o handler de erros só rodam nos ITs. Medir só os unitários premiaria o teste errado.
- **Exclusões só para bootstrap (`CreditEngineApplication`, `config/`)** — excluir service ou controller para bater a meta seria burlar o gate.

## Um gate que passava sem medir nada
Na primeira versão, o `merge` não encontrou os `.exec` (por causa de um symlink local em `target`), e `merge`, `report` e `check` só pularam, com `BUILD SUCCESS`. O ponto cego é geral: se o `.exec` sumir por qualquer motivo, o gate não reclama. Por isso:
- o `fileSet` agora usa `followSymlinks`;
- o upload do CI usa `if-no-files-found: error`, então relatório ausente quebra o job;
- o gate foi provado subindo a meta para `0.99`: saiu com `Coverage checks have not been met` e exit 1, e a meta voltou para `0.70`.

## O que mudou
- `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `docker-compose.yml`, `.env.example`.
- JaCoCo no `pom.xml`, com `merge`, `report` e `check` no `verify`.

## Como testar
```bash
docker compose down -v && docker compose up -d --build
docker compose ps
curl -s localhost:8080/actuator/health
```
```bash
cd backend && ./mvnw verify     # relatório em target/site/jacoco-merged
```
Cobertura medida nesta branch: 95,0% de linhas no projeto; 100% de linhas e de branches em `domain.pricing`.

## Critérios de aceite
AC-COV-01, AC-COV-02, AC-OPS-01, AC-OPS-05.

## Riscos / fora de escopo
A primeira subida, sem cache, levou 3min52s nesta máquina, por baixar as dependências. O perfil `perf` do k6 só passa a ter scripts em `test/perf`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
