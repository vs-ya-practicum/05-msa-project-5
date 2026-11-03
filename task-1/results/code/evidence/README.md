# Demonstration Evidence

Execution observations below are based on command output supplied by the user and UI/email confirmations. Screenshot files are separate evidence artifacts; filenames listed here are capture targets until the files are added.

## Scenario 1: Summary Produced

- Run identifier: `manual__2026-10-31T11:42:37.836884+00:00`.
- Parameters: `dataset=mixed`, `failure_mode=none`.
- Airflow: the user confirmed successful tasks with `record_no_valid_orders` skipped.
- Validation: 5 records, 3 valid, 2 rejected.
- Rejections: source record 4 has `missing_customer_id`; record 5 has `non_positive_amount`.
- Summary: 2026-10-01 has 2 orders totaling 150.00; 2026-10-02 has 1 order totaling 75.00.
- Business outcome: `summary_produced`, with 3 valid and 2 rejected records.
- Email: the user confirmed a SUCCESS message in Mailpit matching the run identifier.

## Scenario 2: No Valid Orders

- Run identifier: `manual__2026-10-31T11:57:22.420031+00:00`.
- Parameters: `dataset=no_valid`, `failure_mode=none`.
- Airflow: the user confirmed success with `build_summary` skipped.
- Supplied SQL output confirms 3 records, 0 valid, and 3 rejected; rejection reasons are missing order identifier, missing date, and non-positive amount.
- No summary rows; one `no_valid_orders` business outcome.

## Scenario 3: Recovery on Retry

- Run identifier: `manual__2026-10-31T12:02:32.872101+00:00`.
- Parameters: `dataset=mixed`, `failure_mode=first_attempt`.
- The user confirmed one retry followed by success, with the no-valid-orders branch skipped.
- The user confirmed the SQL inspection matched 3 valid and 2 rejected records, exactly two summary rows totaling 150.00 and 75.00, and one `summary_produced` outcome. The results were not duplicated.

## Scenario 4: Retries Exhausted

- Run identifier: `manual__2026-10-31T12:06:27.766951+00:00`.
- Parameters: `dataset=mixed`, `failure_mode=always`.
- The user confirmed the DAG failed and `build_summary` had three failed attempts: the initial attempt and two retries.
- Supplied SQL output confirms 3 valid and 2 rejected records, no summary rows, and no successful business-outcome row.
- The user confirmed receipt of the matching FAILURE email in Mailpit.

All four execution scenarios have been verified through supplied outputs and user confirmations. Screenshot packaging is tracked separately in the final results README.
