# POC Deployment

Run commands from the `realization/` directory in WSL. The selected context is `minikube`, with namespace `task1`.

## Selected Versions

| Component | Version |
|---|---|
| Airflow Helm chart | 1.22.0 |
| Airflow | 3.2.2 |
| PostgreSQL image | `postgres:16.15-bookworm` |
| Mailpit image | `axllent/mailpit:v1.27.4` |

Kubernetes 1.37 remains the user's selected environment. Airflow 3.2.2 lists Kubernetes 1.30–1.35 as tested; compatibility with this cluster is not yet demonstrated. See [Airflow prerequisites](https://airflow.apache.org/docs/apache-airflow/3.2.2/installation/prerequisites.html).

## Database Credentials

```bash
bash deployment/create-db-secrets.sh
```

The script creates random passwords in ignored files under `deployment/.local/` and applies the PostgreSQL credentials and Airflow metadata connection Secrets. Repeated execution reuses the saved passwords. Keep these files while retaining the database volume: regenerating Secrets does not change passwords in an initialized database.

## PostgreSQL

```bash
kubectl --context=minikube apply -f deployment/postgres.yaml
```

The manifest defines one PostgreSQL instance, a 2 GiB persistent volume claim, an internal Service, and first-start initialization for `source`, `processing`, and `airflow`. The `processing` database has `staging` and `reporting` schemas. The source reader receives SELECT privileges on tables subsequently created by `postgres` in the source's `public` schema; processing and Airflow have separate database owners.

Initialization runs only with an empty data directory, as documented for the [official PostgreSQL image](https://hub.docker.com/_/postgres). Editing the initialization ConfigMap does not migrate an existing database. Source and processing tables are initialized separately through [the demonstration data script](../data/README.md).

## Mailpit

```bash
kubectl --context=minikube apply -f deployment/mailpit.yaml
```

Mailpit stores demonstration messages on a 1 GiB persistent volume. Its internal SMTP endpoint is `task1-mailpit:1025`; the web interface uses port 8025. The pinned image and ports follow the [official Mailpit Docker documentation](https://mailpit.axllent.org/docs/install/docker/).

After the pod is ready, view the inbox through a local port forward:

```bash
kubectl --context=minikube -n task1 port-forward service/task1-mailpit 8025:8025
```

Open `http://localhost:8025` while this command remains running. SMTP stays inside the cluster.

## Airflow

```bash
helm upgrade --install task1-airflow apache-airflow/airflow \
  --kube-context minikube \
  --version 1.22.0 \
  --namespace task1 \
  -f deployment/airflow-values.yaml \
  --wait=hookOnly --timeout 10m
```

Use Helm 4's hook-only waiting for this configuration. Waiting for all workloads before the post-install migration hook can leave pods waiting for migrations while the migration job has not started. Check workload readiness separately after the hook jobs complete.

```bash
kubectl --context=minikube -n task1 port-forward service/task1-airflow-api-server 8080:8080
```

Open `http://localhost:8080`. The chart's initial local POC credentials are `admin` / `admin`.

These instructions describe prepared configuration, not a record of every deployment step having succeeded. Apply and verify each step separately during the guided implementation.
