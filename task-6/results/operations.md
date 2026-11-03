# Task 6 operations

These instructions apply to phases 1 and 3 of [etl-with-tracing](./etl-with-tracing/). The application starts the existing ETL job only after an API request. Application startup and restart do not start ETL.

The commands below use Bash. Docker and Docker Compose must be available. The Docker build uses Java 17 and the Gradle wrapper inside the build container. Build and test execution remains subject to explicit user authorization under project guidance. The Phase 1 runtime checks have not yet been run.

## Configure and start the application

Run commands from `realization/task-6/results/etl-with-tracing/`.

Create the local environment file if it does not exist:

```bash
test -f .env || cp .env.example .env
```

Set the database connection values in `.env`. Keep an existing file and its local values. If host port `5432` is occupied, change `POSTGRESDB_LOCAL_PORT`. PostgreSQL still uses port `5432` inside the Compose network.

The Task 5 and Task 6 stacks publish the same monitoring ports by default. Run only one stack at a time with these mappings.

Build and start the Task 6 stack:

```bash
docker compose build
docker compose up -d
```

Wait for the application startup message before sending a launch request. View application logs with:

```bash
docker compose logs -f app
```

Press `Ctrl+C` to stop following logs. This action does not stop the container.

## Launch ETL

Send one request from the terminal:

```bash
curl -i -X POST http://localhost:8080/api/jobs/import-products
```

The endpoint needs no request body or job parameters. An accepted launch returns HTTP `202 Accepted` with this JSON structure:

```json
{
  "jobName": "importProductJob",
  "executionId": 1,
  "message": "Job launch accepted"
}
```

The execution ID varies. The response confirms launch acceptance. It does not confirm ETL completion. The job continues asynchronously, and existing logs and monitoring show its result.

| Condition | HTTP status | JSON `code` |
| --- | --- | --- |
| Job already running | `409 Conflict` | `JOB_ALREADY_RUNNING` |
| Job already completed | `409 Conflict` | `JOB_ALREADY_COMPLETED` |
| Spring Batch refuses a restart | `409 Conflict` | `JOB_RESTART_NOT_ALLOWED` |
| Executor cannot accept the job | `503 Service Unavailable` | `JOB_LAUNCH_UNAVAILABLE` |
| Unexpected launch or repository failure | `500 Internal Server Error` | `JOB_LAUNCH_FAILED` |

Each error response also contains a `message`. An accepted job that later fails during ETL still has an accepted launch response.

The application supplies the fixed identifying job parameter `input=product-data.csv` for every request. The caller does not supply this parameter. After successful completion, another launch against the same database returns `409 JOB_ALREADY_COMPLETED`. Application restart does not reset that state. Requests do not queue another ETL execution, and the application has no launch lock that makes concurrent requests wait.

Use a new demonstration database when switching from the previous implementation with empty job parameters. The fixed parameter creates a different job-instance identity. Keep the previous database and its evidence.

## Prepare demonstration scenarios

After the stack starts, prepare one scenario:

```bash
bash demo.sh normal
```

Use `failure` or `slow` instead of `normal` for the other scenarios. Each invocation creates a new database, initializes its business tables, and recreates the application container. It keeps previous databases and uses the existing application image. Rebuild the image before preparing a scenario if application code has changed.

The script prints the database name. Keep this name if you need to restart a failed job later. Scenario preparation does not start ETL. Wait for application startup, then send the terminal `curl` command shown above.

| Scenario | Expected result after the API launch |
| --- | --- |
| `normal` | Five committed products and successful completion |
| `failure` | Failure on SKU `50001` after three committed products |
| `slow` | Approximately 100 seconds of processing, then successful completion |

Prepare scenarios sequentially. Wait for the current execution to finish before preparing another scenario.

## Restart a failed job

After ETL fails, send the same launch request to attempt a restart. Spring Batch determines restart eligibility and uses saved progress. A restart creates another execution of the same job instance.

The `failure` scenario keeps failing on its configured SKU until that condition is disabled. To demonstrate recovery, use the database name printed by the original scenario:

```bash
export ETL_DATABASE="<database-name-printed-by-demo.sh>"
export BATCH_DEMO_FAIL_ON_SKU=0
export BATCH_DEMO_ITEM_DELAY_MS=0
docker compose up -d --no-deps --force-recreate --no-build app
```

Wait for application startup. Send the same terminal `curl` command. Use logs and monitoring to examine the restart result.

Do not run `demo.sh` again for this recovery check. It creates a new database. Keep the original business data and Spring Batch metadata. Do not reset tables or volumes to force a restart.

## View progress and results

| Interface | Address |
| --- | --- |
| Grafana dashboard | [localhost:3000/d/batch-processing](http://localhost:3000/d/batch-processing) |
| Grafana alerts | [localhost:3000/alerting/list](http://localhost:3000/alerting/list) |
| Prometheus | [localhost:9090](http://localhost:9090) |
| Kibana | [localhost:5601/app/discover](http://localhost:5601/app/discover) |

For Kibana, import [batch-processing.ndjson](./etl-with-tracing/kibana/batch-processing.ndjson) through **Stack Management → Saved Objects → Import**. Keep **Create new copies** disabled. In **Discover**, select `batch-processing-*` and a time range that includes the execution.

Find the job with `etl.job_name : "importProductJob"`. Filter by `etl.job_execution_id` to examine the execution returned by the API. The `job_finished` event shows the final status. Logs for failed jobs contain the underlying ETL exception. Request tracing connects API logs and ETL logs through `etl.traceId`, `etl.spanId`, and `etl.URI`.

Demonstration databases have independent execution IDs. Use the execution time and selected scenario together with the ID when examining logs.

## Demonstrate Request Tracing

Use [Discover](http://localhost:5601/app/discover) to inspect log records. The Lens visualization editor does not show the same record table. Select `batch-processing-*` and a time range that includes the requests, such as **Today** for a same-day demonstration.

Add these columns from the Discover field sidebar: `etl.traceId`, `etl.spanId`, `etl.URI`, `etl.thread_name`, and `etl.message`. Add `etl.job_execution_id` when comparing job executions. Search for each field in the sidebar and use its **+** button to add the column.

Elasticsearch dynamically maps the tracing fields. Use `etl.traceId.keyword`, `etl.spanId.keyword`, and `etl.URI.keyword` for exact filtering. The demonstrated index exposed all three subfields as searchable and aggregatable.

### Trace an Asynchronous Job

Prepare a new slow scenario:

```bash
bash demo.sh slow
```

Wait for application startup. Send a request without tracing headers:

```bash
curl -i -X POST http://localhost:8080/api/jobs/import-products
```

The API returns `202` with an execution ID. Find the generated trace ID in the request logs:

```bash
docker compose logs --tail=60 app
```

Filter Discover by that ID. The recorded demonstration used:

```text
etl.traceId.keyword : "8090214c891b718901167ab76a280732"
```

A new demonstration generates a different ID. API and ETL records must share that ID and `URI=/api/jobs/import-products`. Their span IDs differ. Spring Batch also creates job and step spans within the same trace. In the slow scenario, ETL records continue after the API request finishes.

During the running job, send another request with a known incoming trace:

```bash
curl -i -X POST http://localhost:8080/api/jobs/import-products \
  -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01'
```

The running-job response is `409 JOB_ALREADY_RUNNING`. Its request-entry, rejection, and request-completion logs must use the supplied trace ID and a new server span ID. The active ETL job must keep its original trace. If ETL has already completed, the response is `409 JOB_ALREADY_COMPLETED`.

Use this Discover query for the incoming trace:

```text
etl.traceId.keyword : "4bf92f3577b34da6a3ce929d0e0e4736"
```

The recorded console logs confirmed incoming trace propagation and preservation of the active ETL trace. The API-to-ETL view is saved in [01-api-etl-trace.png](./screenshots/01-api-etl-trace.png). A further view is available in [02-api-etl-trace-full.png](./screenshots/02-api-etl-trace-full.png).

### Compare Failed-Job Restart Traces

Prepare a new failure scenario:

```bash
bash demo.sh failure
```

Wait for application startup. Launch the first execution with a known trace:

```bash
curl -i -X POST http://localhost:8080/api/jobs/import-products \
  -H 'traceparent: 00-11111111111111111111111111111111-1111111111111111-01'
```

Wait until the execution has status `FAILED`. Keep the application running. Send the restart request with a different trace:

```bash
curl -i -X POST http://localhost:8080/api/jobs/import-products \
  -H 'traceparent: 00-22222222222222222222222222222222-2222222222222222-01'
```

Both requests return `202` with different execution IDs. The configured SKU failure remains active, so the restarted execution can fail again. This demonstration examines trace separation on worker reuse. Use the recovery procedure above when demonstrating successful recovery.

Search Discover with:

```text
etl.traceId.keyword : ("11111111111111111111111111111111" or "22222222222222222222222222222222")
```

Confirm that both executions use `batch-job-worker-1`. Execution `1` must use trace `11111111111111111111111111111111`; execution `2` must use trace `22222222222222222222222222222222`. Failure records must retain their execution’s trace and URI. Do not restart the application between these executions.

The operator confirmed these results for database `demo_failure_20261103140151_1232`. The Discover view is saved in [03-failed-job-restart-traces.png](./screenshots/03-failed-job-restart-traces.png).
