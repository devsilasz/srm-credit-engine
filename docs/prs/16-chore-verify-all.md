## Contexto
"Pronto" precisa ser um comando que qualquer pessoa rode, e não uma afirmação. Este script é o critério de saída do projeto: build, compose de um comando, smoke, E2E, carga, estresse, integridade, cobertura e anti-padrões, em ordem, a partir de um banco zerado.

## Por quê (decisões)
- **Para na primeira falha e diz qual passo falhou** — um script que continua depois de um erro produz um relatório enganoso.
- **`--skip-perf` sai com código 2, e não 0** — a flag serve só para depurar. Sem isso, uma execução parcial poderia ser confundida com a conclusão.
- **Lê a cobertura do `jacoco.csv` e falha se ele não existir** — na etapa 11, o gate do JaCoCo pulou sem medir nada. A leitura independente cobre esse ponto cego.
- **Lint e build do frontend dentro da imagem de build** — a máquina não precisa de Node.
- **Datas do smoke calculadas pelo Postgres** — `date -v+3m` não cai no fim do mês como o `plusMonths` do backend, e o C1 falharia em dias como 30 de novembro.
- **Sem `jq` e sem Python no host** — o script só usa `curl`, `sed`, `awk` e Docker, além do `./mvnw`, que precisa de Java.

## O que mudou
- `scripts/verify-all.sh`.
- `ApplicationConfig`: comentário reescrito para o grep de `float` não ter exceções.
- `docs/perf/` e `docs/screenshots/`: resultados da execução completa.

## Como testar
```bash
scripts/verify-all.sh              # cerca de 12 minutos; 0 só se tudo passar
scripts/verify-all.sh --skip-perf  # para depurar; nunca sai com 0
```
O script também foi provado no sentido contrário: com um `.env` presente, ele parou no passo 2 com código 1.

## Critérios de aceite
AC-OPS-01, AC-API-04, AC-SEC-01, AC-UI-10.

## Riscos / fora de escopo
Precisa de Java 21 para o `./mvnw`, ao contrário do `docker compose up`, que só precisa de Docker.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
