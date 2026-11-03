# Order Processing DAG

The DAG `task1_orders` runs manually and permits one active run at a time. It starts paused; unpause it before demonstrations.

Flow: `load_orders` → `validate_orders` → `choose_path` → either `build_summary` or `record_no_valid_orders` → `finish`.

Processing steps use separate pods. The branch task reads only the validation count for the current run. `finish` requires at least one successful upstream branch and no failed branch, so a skipped alternative is allowed and an exhausted processing failure cannot become a successful DAG.

Each task allows two retries with a 15-second delay. Summary failure injection receives Airflow's current attempt number. DAG callbacks send success or final failure messages through Mailpit; intermediate retries do not send final failure emails. Completed and failed processing pods are retained for screenshot evidence, and their logs are captured by Airflow.

| Demonstration | `dataset` | `failure_mode` |
|---|---|---|
| Summary with rejected records | `mixed` | `none` |
| No valid data | `no_valid` | `none` |
| Recovery after first failure | `mixed` | `first_attempt` |
| Retries exhausted | `mixed` | `always` |

Supply these parameters through the Airflow trigger form. Use a new run identifier for each demonstration; retries automatically reuse that run identifier.

## Install Configuration

From `realization/`, first create the connection Secret:

```bash
bash deployment/create-airflow-connections.sh
```

Then publish the DAG:

```bash
kubectl --context=minikube -n task1 create configmap task1-dags \
  --from-file=task1_orders.py=dags/task1_orders.py --dry-run=client -o yaml \
  | kubectl --context=minikube -n task1 apply -f -
```

Finally apply the Helm values using the command in the [deployment instructions](../deployment/README.md). The values mount the ConfigMap and inject connection environment variables. An environment-defined connection need not appear as a stored connection in the Airflow UI.

The DAG file is mounted with `subPath` so Airflow does not scan the ConfigMap volume's internal symlink directories. Subsequent ConfigMap updates require restarting the Airflow pods that mount the file; updating the ConfigMap alone does not refresh a `subPath` mount.

## Observed Provider Versions

The deployed Airflow 3.2.2 image reported Kubernetes provider 10.17.1, PostgreSQL provider 6.7.0, and SMTP provider 3.0.1. The DAG uses these existing providers rather than installing packages dynamically at startup. See [KubernetesPodOperator parameters](https://airflow.apache.org/docs/apache-airflow-providers-cncf-kubernetes/10.17.1/_api/airflow/providers/cncf/kubernetes/operators/pod/index.html) and [SMTP connection settings](https://airflow.apache.org/docs/apache-airflow-providers-smtp/stable/connections/smtp.html).
