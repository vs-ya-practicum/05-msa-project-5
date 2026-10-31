"""PostgreSQL-only batch POC with separate processing pods and data-driven branches."""

from datetime import datetime, timedelta, timezone

from airflow.sdk import DAG, Param, get_current_context, task
from airflow.providers.cncf.kubernetes.operators.pod import KubernetesPodOperator
from airflow.providers.cncf.kubernetes.secret import Secret
from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.providers.smtp.notifications.smtp import SmtpNotifier
from airflow.providers.standard.operators.empty import EmptyOperator
from kubernetes.client import models as k8s


def notification(outcome):
    return SmtpNotifier(
        smtp_conn_id="task1_smtp",
        from_email="airflow@task1.local",
        to="marketing@task1.local",
        subject=f"[Task 1] {outcome}: " + "{{ dag.dag_id }} / {{ dag_run.run_id }}",
        html_content=(
            f"<p>Pipeline outcome: {outcome}</p>"
            "<p>DAG: {{ dag.dag_id }}</p>"
            "<p>Run: {{ dag_run.run_id }}</p>"
            "<p>Dataset: {{ params.dataset }}</p>"
            "<p>Failure mode: {{ params.failure_mode }}</p>"
        ),
    )


def processingTask(command, injectFailure=False):
    arguments = [command, "--run-id", "{{ run_id }}", "--attempt-number", "{{ ti.try_number }}"]
    secrets = [Secret("env", "PROCESSING_PASSWORD", "task1-postgres-credentials", "PROCESSING_PASSWORD")]
    if command == "load_orders":
        arguments.extend(["--dataset", "{{ params.dataset }}"])
        secrets.append(Secret("env", "SOURCE_PASSWORD", "task1-postgres-credentials", "SOURCE_PASSWORD"))
    if injectFailure:
        arguments.extend(["--failure-mode", "{{ params.failure_mode }}"])
    return KubernetesPodOperator(
        task_id=command,
        name=command.replace("_", "-"),
        namespace="task1",
        image="task1-processing:0.1.0",
        image_pull_policy="IfNotPresent",
        in_cluster=True,
        arguments=arguments,
        secrets=secrets,
        env_vars={"DB_HOST": "task1-postgres", "DB_PORT": "5432"},
        container_resources=k8s.V1ResourceRequirements(
            requests={"cpu": "100m", "memory": "64Mi"},
            limits={"cpu": "1", "memory": "256Mi"},
        ),
        automount_service_account_token=False,
        deferrable=False,
        get_logs=True,
        do_xcom_push=False,
        on_finish_action="keep_pod",
        log_events_on_failure=True,
        log_pod_spec_on_failure=False,
        startup_timeout_seconds=120,
        execution_timeout=timedelta(minutes=5),
        labels={"app": "task1-processing"},
    )


with DAG(
    dag_id="task1_orders",
    description="Validate orders, branch on valid data, and demonstrate retries and email notifications.",
    start_date=datetime(2026, 1, 1, tzinfo=timezone.utc),
    schedule=None,
    catchup=False,
    max_active_runs=1,
    is_paused_upon_creation=True,
    default_args={"retries": 2, "retry_delay": timedelta(seconds=15)},
    params={
        "dataset": Param("mixed", type="string", enum=["mixed", "no_valid"]),
        "failure_mode": Param("none", type="string", enum=["none", "first_attempt", "always"]),
    },
    on_success_callback=notification("SUCCESS"),
    on_failure_callback=notification("FAILURE"),
    tags=["task1", "batch", "poc"],
) as dag:
    loadOrders = processingTask("load_orders")
    validateOrders = processingTask("validate_orders")

    @task.branch(task_id="choose_path")
    def choosePath():
        context = get_current_context()
        hook = PostgresHook(postgres_conn_id="task1_processing")
        result = hook.get_first(
            "SELECT valid_count FROM staging.validation_results WHERE run_id = %s",
            parameters=(context["run_id"],),
        )
        if result is None:
            raise ValueError("Validation results are missing for this run")
        return "build_summary" if result[0] > 0 else "record_no_valid_orders"

    choose = choosePath()
    buildSummary = processingTask("build_summary", injectFailure=True)
    noValidOrders = processingTask("record_no_valid_orders")
    finish = EmptyOperator(task_id="finish", trigger_rule="none_failed_min_one_success")

    loadOrders >> validateOrders >> choose
    choose >> [buildSummary, noValidOrders] >> finish
