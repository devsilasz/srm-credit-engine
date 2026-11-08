#!/usr/bin/env bash
# AC-PERF-07 (health depois do estresse). Carga, estresse, integridade e relatório contra o compose, sempre a partir de um banco novo:
# o smoke e o E2E deixam dados, e a carga precisa de exatamente 10.000 liquidações seed.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p docs/perf
OUT=docs/perf

docker compose down -v --remove-orphans
docker compose up -d --build --wait

{
  echo "data=$(date -u +%FT%TZ)"
  echo "commit=$(git rev-parse --short HEAD)"
  echo "cpu=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || grep -m1 'model name' /proc/cpuinfo | cut -d: -f2 | xargs)"
  echo "ram_gb=$(( $(sysctl -n hw.memsize 2>/dev/null || awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo) / 1024 / 1024 / 1024 ))"
  echo "so=$(uname -sr)"
  echo "docker=$(docker info --format 'servidor {{.ServerVersion}}, {{.NCPU}} CPUs, {{.MemTotal}} bytes de memória')"
} > "$OUT/environment.txt"

docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U srm -d srm < perf/seed.sql

echo "== carga"
docker compose --profile perf run --rm k6 run \
  --summary-export=/results/load-summary.json /scripts/load.js | tee "$OUT/load-output.txt"
curl -fsS http://localhost:8080/actuator/prometheus \
  | grep '^credit_engine_pricing_duration_seconds' > "$OUT/load-pricing-timer.txt"

if [ "${1:-}" = "--load-only" ]; then
  exit 0
fi

echo "== estresse"
docker compose --profile perf run --rm k6 run --out csv=/results/stress.csv \
  --summary-export=/results/stress-summary.json /scripts/stress.js | tee "$OUT/stress-output.txt"

echo "== integridade"
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U srm -d srm -f - < perf/integrity.sql \
  | tee "$OUT/integrity-output.txt"
curl -fsS http://localhost:8080/actuator/health | tee "$OUT/health-after-stress.txt"
echo
curl -fsS http://localhost:8080/actuator/prometheus \
  | grep -E '^(hikaricp_connections_(timeout_total|max|pending)|tomcat_threads_(busy|config_max)|credit_engine_settlements_total|jvm_gc_pause_seconds_max)' \
  > "$OUT/metrics-after-stress.txt"

echo "== relatório"
docker run --rm -v "$PWD:/work" -w /work python:3.12-alpine \
  sh -c "python perf/aggregate_stress.py $OUT/stress.csv $OUT/stress-by-step.csv && python perf/report.py $OUT"

# A série bruta passa de 100 MB e não é versionada: o agregado por degrau já foi gerado.
rm -f "$OUT/stress.csv"
