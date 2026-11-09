## Contexto
A cobertura aparecia como um único número (98,5%), que mistura tudo e não diz quanto cada camada de teste cobre sozinha. O autor pediu essa evidência num arquivo, separada por tipo de teste.

## Por quê (decisões)
- **Cada camada roda sozinha, em uma execução própria** — só assim a tabela responde "se só estes testes existissem, quanto do backend seria executado?". Os percentuais se sobrepõem e não somam.
- **E2E com agente JaCoCo no contêiner, usando as classes da própria imagem** — o JaCoCo exige os bytes que rodaram, e os da imagem podem diferir dos locais.
- **Contrato medido por operação e status, além de linhas** — linhas dizem pouco sobre um contrato. O `ContractIT` grava cada combinação exercitada.
- **Relatório gerado por script** — nenhum número é digitado à mão, e os CSVs do JaCoCo ficam versionados como evidência.
- **O gerador falha se as medições não forem comparáveis** — totais de linha diferentes entre camadas, ou contagem de testes que não soma, interrompem o relatório.
- **Carga, estresse e frontend sem cobertura de linhas** — o relatório diz isso e aponta a evidência de cada um. Um agente distorceria os números de carga, e o TypeScript não é instrumentado.

## Erros encontrados na primeira medição
A primeira execução saiu com código 0, e os números estavam errados:
- **125 testes na integração, em vez de 98:** `-Dit.test=!ContractIT`, sem um include, faz o Failsafe rodar todas as classes, inclusive as unitárias. Agora é `*IT,!ContractIT`.
- **725 linhas locais contra 656 no E2E:** a extensão Java do VS Code aberta no projeto gravou em `target/classes` com outro compilador, e o Maven respondeu "Nothing to compile". O script agora sempre recompila.
- **109 testes no total, em vez de 136:** só a última linha de resumo era lida. Agora soma as duas.

## Uma lacuna real que o relatório revelou
O texto dizia que todo status fora do contrato tinha teste de integração. Conferidos um a um, `POST /api/receivables` com `400` não tinha nenhum. O teste foi adicionado.

## O que mudou
- `scripts/coverage-report.sh`, `scripts/coverage_report.py`, `docker-compose.coverage.yml`.
- `docs/coverage/`: `REPORT.md` e os CSVs.
- `ContractIT`: grava as combinações de operação e status.
- `ReceivableApiIT`: `invalidBodyIs400AndCreatesNothing`.
- `README.md`: resumo por camada e link para o relatório.

## Como testar
```bash
scripts/coverage-report.sh   # cerca de 4 minutos; precisa de Java 21 e Docker
```

## Critérios de aceite
AC-COV-01, AC-COV-02, AC-API-05.

## Riscos / fora de escopo
- O contrato não exercita todas as combinações da tabela. As que faltam estão listadas no relatório e têm teste de integração, mas o formato dessas respostas não é conferido contra o OpenAPI.
- Durante o E2E, o script para o compose em execução e o religa no fim, com os dados intactos.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
