"""Gera docs/perf/REPORT.md só a partir dos arquivos de saída reais. Sai com erro se um gate falhar."""
import csv
import json
import re
import sys
from pathlib import Path

OUT = Path(sys.argv[1])
SCENARIOS = [
    ("quote", "`POST /api/pricing/quote`", 20, {"p(95)": 150, "p(99)": 300}),
    ("statement", "`GET /api/settlements`", 10, {"p(95)": 100}),
    ("settle", "`POST /api/settlements`", 5, {"p(95)": 300}),
]
PRICING_P99_LIMIT_MS = 5
BREAK_P95_MS = 1000
BREAK_ERROR_PCT = 1
INTEGRITY_CHECKS = 6

failures = []


def read(name):
    return (OUT / name).read_text()


def load_section():
    metrics = json.loads(read("load-summary.json"))["metrics"]
    lines = [
        "| Cenário | Taxa (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Requisições | Erros | Limite |",
        "|---|---:|---:|---:|---:|---:|---:|---|",
    ]
    for key, label, rate, limits in SCENARIOS:
        duration = metrics[f"http_req_duration{{scenario:{key}}}"]
        failed = metrics[f"http_req_failed{{scenario:{key}}}"]
        results = []
        for stat, limit in limits.items():
            passed = duration[stat] < limit
            results.append(f"{'✅' if passed else '❌'} {stat} < {limit} ms")
            if not passed:
                failures.append(f"carga {key}: {stat} = {duration[stat]} ms, limite {limit} ms")
        errors = failed["passes"]  # no k6, "passes" de http_req_failed conta as falhas.
        total = failed["passes"] + failed["fails"]
        if errors:
            failures.append(f"carga {key}: {errors} erros")
        lines.append(
            f"| {label} | {rate} | {duration['p(50)']:.2f} | {duration['p(95)']:.2f} "
            f"| {duration['p(99)']:.2f} | {total} | {errors} | {'; '.join(results)} |"
        )
    return "\n".join(lines)


def pricing_timer():
    match = re.search(
        r'credit_engine_pricing_duration_seconds\{quantile="0\.99"\}\s+(\S+)',
        read("load-pricing-timer.txt"),
    )
    p99_ms = float(match.group(1)) * 1000
    passed = p99_ms < PRICING_P99_LIMIT_MS
    if not passed:
        failures.append(f"motor: p99 = {p99_ms} ms, limite {PRICING_P99_LIMIT_MS} ms")
    return (
        f"Motor de precificação (`credit_engine_pricing_duration`, janela móvel ao fim da carga): "
        f"p99 = {p99_ms:.3f} ms ({'✅' if passed else '❌'} limite {PRICING_P99_LIMIT_MS} ms)."
    )


def stress_section():
    with open(OUT / "stress-by-step.csv", newline="") as raw:
        rows = list(csv.DictReader(raw))
    lines = [
        "| Alvo (req/s) | Requisições | Vazão real (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Erro (%) |",
        "|---:|---:|---:|---:|---:|---:|---:|",
    ]
    breaking = None
    sustained = None
    for row in rows:
        lines.append(
            f"| {row['alvo_req_s']} | {row['requisicoes']} | {row['vazao_req_s']} | {row['p50_ms']} "
            f"| {row['p95_ms']} | {row['p99_ms']} | {row['erro_pct']} |"
        )
        broke = float(row["p95_ms"]) > BREAK_P95_MS or float(row["erro_pct"]) > BREAK_ERROR_PCT
        if broke and breaking is None:
            breaking = row
        if breaking is None:
            sustained = row
    if breaking:
        verdict = (
            f"**Ponto de quebra:** degrau de {breaking['alvo_req_s']} req/s "
            f"(p95 = {breaking['p95_ms']} ms, erro = {breaking['erro_pct']}%). "
            + (
                f"Maior vazão sustentada antes dele: {sustained['vazao_req_s']} req/s."
                if sustained
                else "Nenhum degrau anterior foi sustentado."
            )
        )
    else:
        verdict = (
            f"**Ponto de quebra:** não atingido até 800 req/s. Vazão real no último degrau: "
            f"{rows[-1]['vazao_req_s']} req/s."
        )
    return "\n".join(lines), verdict, rows


def hostile_section():
    metrics = json.loads(read("stress-summary.json"))["metrics"]

    def count(name):
        return int(metrics.get(name, {}).get("count", 0))

    unexpected = count("hostile_unexpected_status")
    double = count("hostile_double_success")
    if unexpected:
        failures.append(f"estresse: {unexpected} respostas com status indevido")
    if double:
        failures.append(f"estresse: {double} recebíveis com sucesso duplo")
    lines = ["| Status | Respostas |", "|---|---:|"]
    for status in ("201", "200", "409", "500", "503"):
        lines.append(f"| `{status}` | {count('hostile_' + status)} |")
    lines.append(f"| outro (indevido) | {unexpected} |")
    lines.append(f"| recebíveis com sucesso duplo | {double} |")
    dropped = count("dropped_iterations")
    return "\n".join(lines), dropped


def integrity_section():
    output = read("integrity-output.txt")
    rows = re.findall(r"^\s*(\w+)\s*\|\s*(\d+)\s*$", output, flags=re.MULTILINE)
    if len(rows) != INTEGRITY_CHECKS:
        failures.append(f"integridade: esperava {INTEGRITY_CHECKS} consultas, achei {len(rows)}")
    for name, total in rows:
        if int(total) != 0:
            failures.append(f"integridade: {name} = {total}")
    return output.strip()


def health_section():
    health = read("health-after-stress.txt").strip()
    if '"status":"UP"' not in health:
        failures.append(f"health depois do estresse: {health}")
    return health


def bottleneck_section(rows, dropped):
    metrics = read("metrics-after-stress.txt")
    timeouts = sum(
        float(value)
        for value in re.findall(r"^hikaricp_connections_timeout_total\{[^}]*\}\s+(\S+)", metrics, re.M)
    )
    pool = re.search(r"^hikaricp_connections_max\{[^}]*\}\s+(\S+)", metrics, re.M)
    pool_size = int(float(pool.group(1))) if pool else "?"
    slowest = max(rows, key=lambda row: float(row["p95_ms"]))
    facts = (
        f"O maior p95 da simulação foi {slowest['p95_ms']} ms, no degrau de "
        f"{slowest['alvo_req_s']} req/s. O pool do Hikari ({pool_size} conexões) registrou "
        f"{timeouts:.0f} timeouts de aquisição, e o k6 descartou {dropped} iterações por falta de VU."
    )
    if timeouts == 0 and dropped == 0:
        return f"{facts} Os dados não apontam gargalo até esta carga, então nenhum ajuste se justifica."
    return f"{facts} O próximo passo é ajustar o pool ou aplicar backpressure, e medir de novo."


def main():
    environment = dict(line.split("=", 1) for line in read("environment.txt").splitlines())
    load_table = load_section()
    timer = pricing_timer()
    stress_table, verdict, rows = stress_section()
    hostile_table, dropped = hostile_section()
    integrity = integrity_section()
    health = health_section()

    report = f"""# Relatório de carga e estresse

> Gerado por `perf/report.py` a partir dos arquivos desta pasta. Nenhum número aqui é digitado à mão.
> Os números são de uma máquina local com tudo no mesmo Docker, incluindo o gerador de carga: servem para comparar execuções, não como capacidade de produção.

## Ambiente

| Item | Valor |
|---|---|
| Data (UTC) | {environment['data']} |
| Commit | `{environment['commit']}` |
| CPU | {environment['cpu']} |
| RAM | {environment['ram_gb']} GB |
| SO | {environment['so']} |
| Docker | {environment['docker']} |

## Carga

Seed de 10.000 liquidações (`perf/seed.sql`), 30 s de aquecimento descartados e 2 min de medição (`perf/load.js`).

{load_table}

{timer}

## Estresse

Simulação em degraus de 1 min (`perf/stress.js`). Quebra = p95 > {BREAK_P95_MS} ms ou erro > {BREAK_ERROR_PCT}%.

{stress_table}

{verdict}

### Liquidação hostil durante a saturação

Por recebível: 3 requisições simultâneas com a mesma chave e 1 com outra chave.

{hostile_table}

Iterações descartadas pelo k6 (somando os dois cenários): {dropped}.

### Recuperação

Health depois do estresse, sem restart manual: `{health}`

## Integridade pós-estresse

```text
{integrity}
```

## Gargalo observado e próximo passo

{bottleneck_section(rows, dropped)}

## Como reproduzir

```bash
perf/run.sh
```

O script recria o banco, aplica o seed, roda a carga, o estresse e a integridade, e gera este relatório. A série bruta `stress.csv` passa de 100 MB e não é versionada; `stress-by-step.csv` é o agregado dela.
"""
    (OUT / "REPORT.md").write_text(report)
    if failures:
        print("GATES REPROVADOS:")
        for failure in failures:
            print(f" - {failure}")
        sys.exit(1)
    print("Relatório gerado; todos os gates passaram.")


if __name__ == "__main__":
    main()
