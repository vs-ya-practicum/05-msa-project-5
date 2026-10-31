# Demonstration Data

From `realization/` in WSL, initialize source data and processing tables:

```bash
kubectl --context=minikube -n task1 exec -i task1-postgres-0 -- \
  psql -U postgres -d postgres < data/initialize.sql
```

The script stops on SQL errors. It creates missing tables, inserts fixtures without replacing existing source rows, and preserves processing results and Airflow metadata. It is initialization, not a migration mechanism for later table changes.

## Datasets and Expected Results

The `dataset` field selects a fixed demonstration dataset. It is not a business field. The processing application will accept `mixed` or `no_valid` as a run parameter.

Required business fields are order identifier, date, customer identifier, and amount. Amount must be positive. Rejected records remain in staging with their rejection reasons; rows with `is_valid = false` form the data-quality output.

| Dataset | Source records | Valid | Rejected | Expected path |
|---|---|---|---|---|
| `mixed` | 5 | 3 | 2 | Produce a summary. |
| `no_valid` | 3 | 0 | 3 | Record `no_valid_orders`; do not produce summary rows. |

Expected summary for `mixed`, scoped to each processing run:

| Order date | Order count | Total amount |
|---|---|---|
| 2026-10-01 | 2 | 150.00 |
| 2026-10-02 | 1 | 75.00 |

The `mixed` dataset rejects source record 4 for a missing customer identifier and record 5 for a negative amount. The `no_valid` dataset rejects records 6, 7, and 8 for a missing order identifier, missing date, and zero amount respectively.

## Processing Tables

| Table | Purpose |
|---|---|
| `staging.orders` | Input snapshot per run, validation flag, and rejection reasons. |
| `staging.validation_results` | Counts used to select the processing branch. |
| `reporting.order_summary` | Valid order count and total amount by date and run. |
| `reporting.run_results` | Successful business outcome of the selected branch. A failed run must not acquire a successful outcome. |

Run identifiers isolate demonstrations. A task retry uses the same run identifier; primary keys support preventing duplicate results. Application transactions and write logic will implement that behavior in the next stage.
