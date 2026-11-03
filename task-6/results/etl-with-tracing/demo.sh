#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: bash demo.sh normal|failure|slow\n' >&2
  exit 2
fi

demo_scenario="$1"
export BATCH_DEMO_FAIL_ON_SKU=0
export BATCH_DEMO_ITEM_DELAY_MS=0

case "$demo_scenario" in
  normal)
    ;;
  failure)
    export BATCH_DEMO_FAIL_ON_SKU=50001
    ;;
  slow)
    export BATCH_DEMO_ITEM_DELAY_MS=20000
    ;;
  *)
    printf 'Unknown scenario: %s\nUsage: bash demo.sh normal|failure|slow\n' "$demo_scenario" >&2
    exit 2
    ;;
esac

demo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
demo_compose=(docker compose --project-directory "$demo_dir" --env-file "$demo_dir/.env" -f "$demo_dir/docker-compose.yml")
export ETL_DATABASE="demo_${demo_scenario}_$(date -u +%Y%m%d%H%M%S)_${RANDOM}"

printf 'Preparing %s demonstration in database %s\n' "$demo_scenario" "$ETL_DATABASE"

"${demo_compose[@]}" exec -T postgresdb sh -c \
  'exec psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$1\";"' \
  sh "$ETL_DATABASE"

"${demo_compose[@]}" run --rm --no-deps -e "PGDATABASE=$ETL_DATABASE" db-init
"${demo_compose[@]}" up -d --no-deps --force-recreate --no-build app

printf '\nScenario started: %s\nDatabase retained: %s\n' "$demo_scenario" "$ETL_DATABASE"
case "$demo_scenario" in
  normal)
    printf 'Expected: one success, five committed records, 100%% progress.\n'
    ;;
  failure)
    printf 'Expected: failure on SKU 50001 after three committed records.\n'
    printf 'Check BatchJobFailed within five minutes.\n'
    ;;
  slow)
    printf 'Expected: approximately 100 seconds of processing, then success.\n'
    printf 'Watch BatchJobTooLong after 60 seconds and before completion.\n'
    ;;
esac
printf '\nDashboard: http://localhost:3000/d/batch-processing\n'
printf 'Alerts:    http://localhost:3000/alerting/list\n'
printf 'Logs:      http://localhost:5601/app/discover\n'
