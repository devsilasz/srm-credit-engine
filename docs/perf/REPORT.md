# Relatório de carga e estresse

> Gerado por `perf/report.py` a partir dos arquivos desta pasta. Nenhum número aqui é digitado à mão.
> Os números são de uma máquina local com tudo no mesmo Docker, incluindo o gerador de carga: servem para comparar execuções, não como capacidade de produção.

## Ambiente

| Item | Valor |
|---|---|
| Data (UTC) | 2026-11-08T23:27:01Z |
| Commit | `ccd35f2` |
| CPU | Apple M4 |
| RAM | 16 GB |
| SO | Darwin 25.5.0 |
| Docker | servidor 28.4.0, 10 CPUs, 8218034176 bytes de memória |

## Carga

Seed de 10.000 liquidações (`perf/seed.sql`), 30 s de aquecimento descartados e 2 min de medição (`perf/load.js`).

| Cenário | Taxa (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Requisições | Erros | Limite |
|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/pricing/quote` | 20 | 2.53 | 5.57 | 6.86 | 2401 | 0 | ✅ p(95) < 150 ms; ✅ p(99) < 300 ms |
| `GET /api/settlements` | 10 | 4.12 | 5.13 | 7.97 | 1201 | 0 | ✅ p(95) < 100 ms |
| `POST /api/settlements` | 5 | 9.27 | 12.84 | 19.78 | 601 | 0 | ✅ p(95) < 300 ms |

Motor de precificação (`credit_engine_pricing_duration`, janela móvel ao fim da carga): p99 = 0.119 ms (✅ limite 5 ms).

## Estresse

Simulação em degraus de 1 min (`perf/stress.js`). Quebra = p95 > 1000 ms ou erro > 1%.

| Alvo (req/s) | Requisições | Vazão real (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Erro (%) |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 2965 | 49.4 | 1.4 | 4.0 | 6.1 | 0.00 |
| 100 | 5842 | 97.4 | 1.0 | 2.7 | 4.1 | 0.00 |
| 200 | 11684 | 194.7 | 0.7 | 1.9 | 3.0 | 0.00 |
| 400 | 23368 | 389.5 | 0.5 | 1.5 | 2.6 | 0.00 |
| 800 | 47265 | 787.8 | 0.4 | 1.2 | 2.1 | 0.00 |

**Ponto de quebra:** não atingido até 800 req/s. Vazão real no último degrau: 787.8 req/s.

### Liquidação hostil durante a saturação

Por recebível: 3 requisições simultâneas com a mesma chave e 1 com outra chave.

| Status | Respostas |
|---|---:|
| `201` | 1501 |
| `200` | 2242 |
| `409` | 2261 |
| `500` | 0 |
| `503` | 0 |
| outro (indevido) | 0 |
| recebíveis com sucesso duplo | 0 |

Iterações descartadas pelo k6 (somando os dois cenários): 0.

### Recuperação

Health depois do estresse, sem restart manual: `{"status":"UP"}`

## Integridade pós-estresse

```text
verificacao              | total 
---------------------------------------+-------
 liquidacao_usd_sem_taxa               |     0
 liquidacao_com_soma_incoerente        |     0
 idempotency_key_duplicada             |     0
 liquidacoes_com_recebivel_nao_settled |     0
 recebiveis_settled_sem_liquidacao     |     0
 recebiveis_com_mais_de_uma_liquidacao |     0
(6 rows)
```

## Gargalo observado e próximo passo

O maior p95 da simulação foi 4.0 ms, no degrau de 50 req/s. O pool do Hikari (10 conexões) registrou 0 timeouts de aquisição, e o k6 descartou 0 iterações por falta de VU. Os dados não apontam gargalo até esta carga, então nenhum ajuste se justifica.

## Como reproduzir

```bash
perf/run.sh
```

O script recria o banco, aplica o seed, roda a carga, o estresse e a integridade, e gera este relatório. A série bruta `stress.csv` passa de 100 MB e não é versionada; `stress-by-step.csv` é o agregado dela.
