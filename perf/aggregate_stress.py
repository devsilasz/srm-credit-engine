"""Agrega a série bruta do k6 (stress.csv) em uma linha por degrau: stress-by-step.csv."""
import csv
import sys

TARGETS = [50, 100, 200, 400, 800]
STEP_SECONDS = 60


def percentile(sorted_values, fraction):
    return sorted_values[min(int(len(sorted_values) * fraction), len(sorted_values) - 1)]


def main(source, destination):
    steps = [{"durations": [], "errors": 0} for _ in TARGETS]
    start = None
    with open(source, newline="") as raw:
        for row in csv.DictReader(raw):
            if row["metric_name"] != "http_req_duration" or row["scenario"] != "quote":
                continue
            timestamp = int(row["timestamp"])
            start = timestamp if start is None else min(start, timestamp)
            index = min((timestamp - start) // STEP_SECONDS, len(TARGETS) - 1)
            steps[index]["durations"].append(float(row["metric_value"]))
            if row["status"] != "200":
                steps[index]["errors"] += 1

    with open(destination, "w", newline="") as output:
        writer = csv.writer(output)
        writer.writerow(
            ["alvo_req_s", "requisicoes", "vazao_req_s", "p50_ms", "p95_ms", "p99_ms", "erro_pct"]
        )
        for target, step in zip(TARGETS, steps):
            durations = sorted(step["durations"])
            total = len(durations)
            if total == 0:
                writer.writerow([target, 0, "0.0", "", "", "", ""])
                continue
            writer.writerow(
                [
                    target,
                    total,
                    f"{total / STEP_SECONDS:.1f}",
                    f"{percentile(durations, 0.50):.1f}",
                    f"{percentile(durations, 0.95):.1f}",
                    f"{percentile(durations, 0.99):.1f}",
                    f"{100 * step['errors'] / total:.2f}",
                ]
            )


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
