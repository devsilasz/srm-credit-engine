"""Gera docs/coverage/REPORT.md e o resumo do README só a partir dos CSVs salvos em docs/coverage."""
import csv
import glob
import os
import re
import sys

OUT = "docs/coverage"
LAYERS = [
    ("unit", "Unitário", "JUnit e AssertJ, sem Spring nem banco"),
    ("integration", "Integração", "Testcontainers com Postgres real, sem o `ContractIT`"),
    ("contract", "Contrato", "`ContractIT`: tráfego real contra o OpenAPI publicado"),
    ("e2e", "E2E", "Playwright no navegador, contra o compose, com agente JaCoCo no backend"),
    ("all", "Unitário + integração + contrato", "`./mvnw verify`: é sobre esta linha que o gate atua"),
]
GATE_PROJECT_LINES = 70.0
GATE_PRICING = 90.0
PRICING = "domain.pricing"


def rows(layer):
    with open(f"{OUT}/{layer}.csv", newline="") as raw:
        return list(csv.DictReader(raw))


def ratio(items, counter):
    covered = sum(int(r[f"{counter}_COVERED"]) for r in items)
    total = covered + sum(int(r[f"{counter}_MISSED"]) for r in items)
    return covered, total


def pct(covered, total):
    return f"{100 * covered / total:.1f}%".replace(".", ",") if total else "n/a"


def cell(items, counter):
    covered, total = ratio(items, counter)
    return f"{pct(covered, total)} ({covered}/{total})"


def short_package(name):
    return name.replace("br.com.srm.creditengine.", "")


def summary_table(data, tests):
    lines = [
        "| Camada | Testes | Linhas | Branches |",
        "|---|---:|---:|---:|",
    ]
    for layer, label, _ in LAYERS:
        lines.append(
            f"| {label} | {tests[layer]} | {cell(data[layer], 'LINE')} | {cell(data[layer], 'BRANCH')} |"
        )
    return "\n".join(lines)


def package_table(data):
    packages = sorted({r["PACKAGE"] for r in data["all"]})
    header = " | ".join(label for _, label, _ in LAYERS)
    lines = [f"| Pacote | {header} |", "|---|" + "---:|" * len(LAYERS)]
    for package in packages:
        cells = []
        for layer, _, _ in LAYERS:
            items = [r for r in data[layer] if r["PACKAGE"] == package]
            cells.append(pct(*ratio(items, "LINE")))
        lines.append(f"| `{short_package(package)}` | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def class_table(data):
    index = {
        layer: {(r["PACKAGE"], r["CLASS"]): r for r in data[layer]} for layer, _, _ in LAYERS
    }
    header = " | ".join(label for _, label, _ in LAYERS)
    lines = [f"| Classe | Linhas | {header} |", "|---|---:|" + "---:|" * len(LAYERS)]
    for key in sorted(index["all"]):
        total = sum(ratio([index["all"][key]], "LINE"))
        if total == 0:
            continue
        cells = []
        for layer, _, _ in LAYERS:
            row = index[layer].get(key)
            cells.append(pct(*ratio([row], "LINE")) if row else "n/a")
        name = key[1].replace("|", "/")
        lines.append(f"| `{short_package(key[0])}.{name}` | {ratio([index['all'][key]], 'LINE')[1]} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def contract_table():
    with open(f"{OUT}/contract-operations.csv", newline="") as raw:
        entries = list(csv.DictReader(raw))
    declared = [e for e in entries if e["declared"] == "true"]
    hit = [e for e in declared if e["exercised"] == "true"]
    operations = {}
    for entry in entries:
        operations.setdefault(entry["operation"], []).append(entry)
    lines = [
        "| Operação | Status exercitados e validados | Declarados e não exercitados aqui |",
        "|---|---|---|",
    ]
    for operation in sorted(operations):
        done = [e["status"] for e in operations[operation] if e["exercised"] == "true"]
        missing = [e["status"] for e in operations[operation] if e["exercised"] != "true"]
        lines.append(
            f"| `{operation.upper().split()[0]} {operation.split()[1]}` | {', '.join(done) or '—'} | {', '.join(missing) or '—'} |"
        )
    return "\n".join(lines), len(hit), len(declared)


def criteria_table():
    areas = {}
    for path in sorted(glob.glob("docs/acceptance/*.md")):
        for line in open(path, encoding="utf-8"):
            match = re.match(r"\| (~~)?(AC-[A-Z]+)-\d+", line)
            if not match or match.group(1):
                continue
            kind = "manual" if "MANUAL" in line.rstrip().rstrip("|").split(" | ")[-1] else "auto"
            areas.setdefault(match.group(2), {"auto": 0, "manual": 0})[kind] += 1
    lines = ["| Área | Com teste automatizado | Verificação manual |", "|---|---:|---:|"]
    for area in sorted(areas):
        lines.append(f"| `{area}` | {areas[area]['auto']} | {areas[area]['manual']} |")
    auto = sum(a["auto"] for a in areas.values())
    manual = sum(a["manual"] for a in areas.values())
    lines.append(f"| **Total** | **{auto}** | **{manual}** |")
    return "\n".join(lines)


def test_classes():
    unit, integration = [], []
    for path in glob.glob("backend/src/test/java/**/*.java", recursive=True):
        name = os.path.basename(path)[:-5]
        if name.endswith("Test"):
            unit.append(name)
        elif name.endswith("IT") and name not in ("AbstractIT", "ContractIT"):
            integration.append(name)
    return sorted(unit), sorted(integration)


def check_consistency(data, tests):
    """Sai com erro se as medições não forem comparáveis: um relatório incoerente não vale como prova."""
    problems = []
    for counter in ("LINE", "BRANCH"):
        totals = {layer: ratio(data[layer], counter)[1] for layer, _, _ in LAYERS}
        if len(set(totals.values())) > 1:
            problems.append(f"totais de {counter} diferentes entre as camadas: {totals}")
    parts = int(tests["unit"]) + int(tests["integration"]) + int(tests["contract"])
    if parts != int(tests["all"]):
        problems.append(f"unit + integration + contract = {parts}, mas a medição total rodou {tests['all']}")
    if problems:
        sys.exit("Medições inconsistentes:\n - " + "\n - ".join(problems))


def above_merged(data):
    """Classes em que uma camada isolada cobriu mais que a medição total."""
    merged = {(r["PACKAGE"], r["CLASS"]): int(r["LINE_COVERED"]) for r in data["all"]}
    found = []
    for layer in ("unit", "integration", "contract"):
        for row in data[layer]:
            key = (row["PACKAGE"], row["CLASS"])
            if int(row["LINE_COVERED"]) > merged.get(key, 0):
                found.append(f"`{row['CLASS']}` ({layer}: {row['LINE_COVERED']} linhas, total: {merged.get(key, 0)})")
    return found


def main():
    data = {layer: rows(layer) for layer, _, _ in LAYERS}
    with open(f"{OUT}/tests-by-layer.csv", newline="") as raw:
        tests = {r["camada"]: r["testes"] for r in csv.DictReader(raw)}
    check_consistency(data, tests)
    variation = above_merged(data)
    variation_note = (
        "Cada camada é uma execução própria, e os caminhos de corrida dependem do escalonamento das "
        "threads. Por isso, nesta medição, uma camada isolada cobriu linhas que a medição total não "
        "cobriu: " + "; ".join(variation) + "."
        if variation
        else "Nenhuma camada isolada cobriu mais linhas que a medição total."
    )
    commit = open(f"{OUT}/commit.txt").read().strip()
    date = open(f"{OUT}/date.txt").read().strip()

    project = ratio(data["all"], "LINE")
    pricing = [r for r in data["all"] if r["PACKAGE"].endswith(PRICING)]
    pricing_lines, pricing_branches = ratio(pricing, "LINE"), ratio(pricing, "BRANCH")
    gates = [
        ("Projeto, linhas", GATE_PROJECT_LINES, project),
        ("`domain.pricing`, linhas", GATE_PRICING, pricing_lines),
        ("`domain.pricing`, branches", GATE_PRICING, pricing_branches),
    ]
    failures = []
    gate_lines = ["| Escopo | Meta | Medido | Situação |", "|---|---:|---:|---|"]
    for label, goal, (covered, total) in gates:
        passed = 100 * covered / total >= goal
        if not passed:
            failures.append(label)
        gate_lines.append(
            f"| {label} | {goal:.0f}% | {pct(covered, total)} ({covered}/{total}) | {'✅' if passed else '❌'} |"
        )

    contract, hit, declared = contract_table()
    unit, integration = test_classes()
    report = f"""# Relatório de cobertura

> Gerado por `scripts/coverage-report.sh` a partir dos CSVs desta pasta. Nenhum número aqui é digitado à mão.

Medido em {date}, no commit `{commit}`. Cada camada rodou sozinha, em uma execução separada, então cada linha da tabela responde: "se só estes testes existissem, quanto do backend seria executado?".

## Resumo por camada

{summary_table(data, tests)}

As camadas se sobrepõem, então os percentuais não somam. A cobertura do E2E é só do backend, com o painel e o Nginx na frente. {variation_note}

O que roda em cada camada:

| Camada | Como | Classes |
|---|---|---|
| Unitário | {LAYERS[0][2]} | {', '.join(f'`{c}`' for c in unit)} |
| Integração | {LAYERS[1][2]} | {', '.join(f'`{c}`' for c in integration)} |
| Contrato | {LAYERS[2][2]} | `ContractIT` |
| E2E | {LAYERS[3][2]} | `frontend/e2e/operator-flow.spec.ts` |

## Metas (gate do `./mvnw verify`)

O build falha abaixo destas metas. Elas valem para a linha "Unitário + integração + contrato".

{chr(10).join(gate_lines)}

Excluídos da medição, por serem só bootstrap e configuração: `CreditEngineApplication` e `infrastructure/config`. Nenhum service ou controller foi excluído.

## Cobertura de contrato: operações e status

Linhas de código não dizem o que importa num contrato. Aqui, a medida é quais combinações de operação e status foram exercitadas com a resposta validada contra o OpenAPI: **{hit} de {declared}** combinações da tabela de convenções da API. Combinações fora dessa tabela também aparecem quando foram exercitadas.

{contract}

Todo status "não exercitado aqui" está declarado no OpenAPI. O comportamento dos que não passam pelo validador é coberto pelos testes de integração, mas o formato da resposta deles não é conferido contra o contrato.

## Cobertura de linhas por pacote

{package_table(data)}

## Cobertura de linhas por classe

{class_table(data)}

## Cobertura dos critérios de aceite

Vale para todas as camadas, inclusive as que não medem linhas. O detalhe de cada critério está em [`docs/acceptance/`](../acceptance/README.md).

{criteria_table()}

## O que não tem cobertura de linhas

| Item | Por quê | Qual é a evidência |
|---|---|---|
| Carga e estresse | Medem latência e integridade. Um agente de cobertura distorceria os números. | [`docs/perf/REPORT.md`](../perf/REPORT.md) |
| Código do frontend | Não há framework de teste unitário no frontend, por decisão registrada em `DECISIONS.md`, e o código TypeScript não é instrumentado. | `frontend/e2e/operator-flow.spec.ts` cobre o fluxo do operador, e os critérios `AC-UI` dizem o que ele prova |

## Como reproduzir

```bash
scripts/coverage-report.sh   # precisa de Java 21 e Docker
```

Os CSVs do JaCoCo por camada (`unit.csv`, `integration.csv`, `contract.csv`, `e2e.csv` e `all.csv`) e `contract-operations.csv` ficam nesta pasta.
"""
    with open(f"{OUT}/REPORT.md", "w", encoding="utf-8") as output:
        output.write(report)

    with open("README.md", encoding="utf-8") as raw:
        readme = raw.read()
    block = f"<!-- coverage:start -->\n{summary_table(data, tests)}\n<!-- coverage:end -->"
    readme, count = re.subn(r"<!-- coverage:start -->.*?<!-- coverage:end -->", block, readme, flags=re.S)
    if count != 1:
        sys.exit("README sem os marcadores de cobertura")
    with open("README.md", "w", encoding="utf-8") as output:
        output.write(readme)

    if failures:
        sys.exit(f"Metas reprovadas: {failures}")
    print("Relatório de cobertura gerado; metas atingidas.")


if __name__ == "__main__":
    main()
