# Processing Application

One image implements four separately launched commands:

| Command | Result |
|---|---|
| `load_orders` | Copies the selected source dataset into a run-specific staging snapshot using batches of up to 500 records. Resets only that run's previous processing results. |
| `validate_orders` | Records rejection reasons and validation flags, then stores counts for Airflow's branch decision. |
| `build_summary` | Aggregates valid orders by date and atomically records the summary and successful business outcome. Requires valid orders. |
| `record_no_valid_orders` | Records a successful no-report outcome. Requires zero valid orders. |

Each command requires `--run-id`. Loading accepts `--dataset mixed` or `--dataset no_valid`. A retry reuses the same run identifier. Writes commit as one transaction per command; an exception rolls them back. Database keys and replacement of results within that transaction prevent duplicates when repeating a step. This does not introduce a distributed transaction across the source and processing databases.

The source snapshot is retained in PostgreSQL between steps. Separate pods do not share process memory or local files. Runs with different identifiers have separate records. Concurrent executions of the same run and step are outside this POC's execution model; Airflow controls step sequencing.

## Credentials

The application uses `SOURCE_PASSWORD` for `source_reader` in `source` and `PROCESSING_PASSWORD` for `processing` in `processing`. Kubernetes injects these from the existing Secret; passwords are not image contents or CLI arguments. `DB_HOST` defaults to `task1-postgres`, and `DB_PORT` to `5432`.

## Controlled Failures

Only `build_summary` accepts a failure mode other than `none`:

- `first_attempt`: attempt 1 fails before report writes; subsequent attempts process normally.
- `always`: every attempt fails before report writes.
- `none`: normal processing.

Airflow will supply `--attempt-number` from the current task attempt. Failures exit with status 1, and logs include command, run identifier, attempt number, and failure mode. Full database errors and credentials are not printed.

## Build

From `realization/` in WSL:

```bash
docker build -t task1-processing:0.1.0 processing
```

The image pins Python 3.12.12 and Psycopg 3.3.6. Building the image does not execute processing or verify database results; those are demonstrated through the Kubernetes pipeline.
