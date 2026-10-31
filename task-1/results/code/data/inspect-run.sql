\set ON_ERROR_STOP on
\pset pager off

\echo Validation counts
SELECT run_id, total_count, valid_count, rejected_count
FROM staging.validation_results
WHERE run_id = :'run_id';

\echo Rejected source records
SELECT source_record_id, order_id, rejection_reasons
FROM staging.orders
WHERE run_id = :'run_id' AND is_valid = false
ORDER BY source_record_id;

\echo Report summary
SELECT order_date, order_count, total_amount
FROM reporting.order_summary
WHERE run_id = :'run_id'
ORDER BY order_date;

\echo Business outcome
SELECT run_id, outcome, valid_count, rejected_count
FROM reporting.run_results
WHERE run_id = :'run_id';
