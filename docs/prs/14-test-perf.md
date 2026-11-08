## Contexto
Os testes de integração provam que uma liquidação concorrente é tratada, mas não dizem o que acontece sob pressão, quando pool, threads e fila apertam. E o maior risco do estresse não é a lentidão: é corromper dado enquanto falha.

## Por quê (decisões)
- **O estresse não tem threshold de latência** — ele reprova só com status indevido ou pagamento duplo. O objetivo é falhar limpo e registrar até onde aguentou, e não forçar uma quebra.
- **Liquidação hostil: 3 requisições com a mesma chave e 1 com outra, por recebível** — é o duplo clique com retry do Anexo B, somado a outro operador liquidando o mesmo título, durante a saturação.
- **`perf/integrity.sql` depois do estresse** — o k6 vê as respostas, e só o banco diz se o dado ficou íntegro.
- **`REPORT.md` gerado por `perf/report.py`** — nenhum número é digitado à mão. O script recalcula os limites a partir dos valores e sai com erro se um gate falhar, então o relatório não disfarça um resultado ruim.
- **Fora do CI** — runner compartilhado gera números sem significado.
- **Seed em SQL com 10.000 liquidações** — medir o extrato com a tabela vazia não diz nada sobre os índices.
- **Python em contêiner para agregar e gerar o relatório** — a máquina não precisa ter Python instalado.

## O que mudou
- `perf/`: `seed.sql`, `load.js`, `stress.js`, `common.js`, `integrity.sql`, `aggregate_stress.py`, `report.py`, `run.sh`.
- `docs/perf/`: saídas reais desta execução e `REPORT.md`.

## Como testar
```bash
perf/run.sh   # cerca de 8 minutos; recria o banco
```
Os resultados estão em [`docs/perf/REPORT.md`](../perf/REPORT.md).

## Critérios de aceite
AC-PERF-01, 02, 04, 05, 06 e 07.

## Riscos / fora de escopo
- Os números são de uma máquina local, com o gerador de carga e o sistema no mesmo Docker. Servem para comparar execuções, não como capacidade de produção.
- O ponto de quebra não foi atingido até 800 req/s. Pela regra da skill `quality-gates`, a carga não foi aumentada para procurá-lo, então o limite real é desconhecido.
- A hostil roda a 5 recebíveis por segundo: testa corrida, não saturação do banco. Por isso não apareceu `500` nem `503`, e os caminhos de esgotamento do pool não foram exercitados aqui.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
