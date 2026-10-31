#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
umask 077
mkdir -p .local

# Reuse saved credentials so rerunning this step does not rotate database passwords.
if [[ ! -f .local/postgres.env ]]; then
  for variable in POSTGRES_PASSWORD AIRFLOW_PASSWORD SOURCE_PASSWORD PROCESSING_PASSWORD; do
    password=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
    printf '%s=%s\n' "$variable" "$password"
  done > .local/postgres.env
fi

kubectl --context=minikube -n task1 create secret generic task1-postgres-credentials \
  --from-env-file=.local/postgres.env --dry-run=client -o yaml \
  | kubectl --context=minikube -n task1 apply -f -

airflow_password=$(sed -n 's/^AIRFLOW_PASSWORD=//p' .local/postgres.env)
printf 'connection=postgresql+psycopg2://airflow:%s@task1-postgres:5432/airflow\n' \
  "$airflow_password" > .local/airflow-metadata.env

kubectl --context=minikube -n task1 create secret generic task1-airflow-metadata \
  --from-env-file=.local/airflow-metadata.env --dry-run=client -o yaml \
  | kubectl --context=minikube -n task1 apply -f -

printf 'Database credentials saved privately in deployment/.local/ and applied to namespace task1.\n'
