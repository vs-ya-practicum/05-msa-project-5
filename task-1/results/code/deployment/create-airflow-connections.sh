#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
umask 077

if [[ ! -f .local/postgres.env ]]; then
  printf 'Missing deployment/.local/postgres.env; run create-db-secrets.sh first.\n' >&2
  exit 1
fi

processing_password=$(sed -n 's/^PROCESSING_PASSWORD=//p' .local/postgres.env)
if [[ -z "$processing_password" ]]; then
  printf 'PROCESSING_PASSWORD is missing from the local credentials file.\n' >&2
  exit 1
fi

{
  printf 'AIRFLOW_CONN_TASK1_PROCESSING=postgresql://processing:%s@task1-postgres:5432/processing\n' "$processing_password"
  printf '%s\n' 'AIRFLOW_CONN_TASK1_SMTP={"conn_type":"smtp","host":"task1-mailpit","port":1025,"extra":{"disable_ssl":true,"disable_tls":true,"from_email":"airflow@task1.local"}}'
} > .local/airflow-connections.env

kubectl --context=minikube -n task1 create secret generic task1-airflow-connections \
  --from-env-file=.local/airflow-connections.env --dry-run=client -o yaml \
  | kubectl --context=minikube -n task1 apply -f -

printf 'Airflow processing and Mailpit connections configured without printing credentials.\n'
