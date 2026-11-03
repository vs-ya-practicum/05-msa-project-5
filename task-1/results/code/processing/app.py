"""Run one transactional processing step; orchestration and retries belong to Airflow."""

import argparse
import json
import os
import sys

import psycopg


def connectDatabase(database, user, passwordVariable):
    return psycopg.connect(
        host=os.environ.get("DB_HOST", "task1-postgres"),
        port=int(os.environ.get("DB_PORT", "5432")),
        dbname=database,
        user=user,
        password=os.environ[passwordVariable],
        connect_timeout=15,
    )


def loadOrders(connection, runID, dataset):
    # All writes commit together. An interrupted load leaves the prior snapshot intact.
    connection.execute("DELETE FROM reporting.order_summary WHERE run_id = %s", (runID,))
    connection.execute("DELETE FROM reporting.run_results WHERE run_id = %s", (runID,))
    connection.execute("DELETE FROM staging.validation_results WHERE run_id = %s", (runID,))
    connection.execute("DELETE FROM staging.orders WHERE run_id = %s", (runID,))
    loadedCount = 0
    with connectDatabase("source", "source_reader", "SOURCE_PASSWORD") as source:
        with source.cursor(name="orders_snapshot") as reader, connection.cursor() as writer:
            reader.execute(
                """SELECT source_record_id, dataset, order_id, order_date, customer_id, amount
                   FROM public.orders WHERE dataset = %s ORDER BY source_record_id""",
                (dataset,),
            )
            while rows := reader.fetchmany(500):
                writer.executemany(
                    """INSERT INTO staging.orders
                       (run_id, source_record_id, dataset, order_id, order_date, customer_id, amount)
                       VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                    [(runID, *row) for row in rows],
                )
                loadedCount += len(rows)
    return {"loaded_count": loadedCount, "dataset": dataset}


def validateOrders(connection, runID):
    connection.execute(
        """UPDATE staging.orders SET rejection_reasons = array_remove(ARRAY[
             CASE WHEN NULLIF(btrim(order_id), '') IS NULL THEN 'missing_order_id' END,
             CASE WHEN order_date IS NULL THEN 'missing_order_date' END,
             CASE WHEN NULLIF(btrim(customer_id), '') IS NULL THEN 'missing_customer_id' END,
             CASE WHEN amount IS NULL THEN 'missing_amount'
                  WHEN amount <= 0 THEN 'non_positive_amount' END
           ], NULL) WHERE run_id = %s""",
        (runID,),
    )
    connection.execute(
        "UPDATE staging.orders SET is_valid = cardinality(rejection_reasons) = 0 WHERE run_id = %s",
        (runID,),
    )
    counts = connection.execute(
        """SELECT count(*), count(*) FILTER (WHERE is_valid), count(*) FILTER (WHERE NOT is_valid)
           FROM staging.orders WHERE run_id = %s""",
        (runID,),
    ).fetchone()
    connection.execute(
        """INSERT INTO staging.validation_results (run_id, total_count, valid_count, rejected_count)
           VALUES (%s, %s, %s, %s)
           ON CONFLICT (run_id) DO UPDATE SET total_count = EXCLUDED.total_count,
             valid_count = EXCLUDED.valid_count, rejected_count = EXCLUDED.rejected_count""",
        (runID, *counts),
    )
    return dict(zip(("total_count", "valid_count", "rejected_count"), counts))


def readValidation(connection, runID):
    counts = connection.execute(
        "SELECT valid_count, rejected_count FROM staging.validation_results WHERE run_id = %s",
        (runID,),
    ).fetchone()
    if counts is None:
        raise ValueError("Validation results do not exist for this run")
    return counts


def recordOutcome(connection, runID, outcome, counts):
    connection.execute(
        """INSERT INTO reporting.run_results (run_id, outcome, valid_count, rejected_count)
           VALUES (%s, %s, %s, %s)
           ON CONFLICT (run_id) DO UPDATE SET outcome = EXCLUDED.outcome,
             valid_count = EXCLUDED.valid_count, rejected_count = EXCLUDED.rejected_count,
             completed_at = CURRENT_TIMESTAMP""",
        (runID, outcome, *counts),
    )
    return {"outcome": outcome, "valid_count": counts[0], "rejected_count": counts[1]}


def buildSummary(connection, runID):
    counts = readValidation(connection, runID)
    if counts[0] == 0:
        raise ValueError("Summary branch requires valid orders")
    connection.execute("DELETE FROM reporting.order_summary WHERE run_id = %s", (runID,))
    connection.execute(
        """INSERT INTO reporting.order_summary (run_id, order_date, order_count, total_amount)
           SELECT run_id, order_date, count(*), sum(amount)
           FROM staging.orders WHERE run_id = %s AND is_valid
           GROUP BY run_id, order_date""",
        (runID,),
    )
    return recordOutcome(connection, runID, "summary_produced", counts)


def recordNoValidOrders(connection, runID):
    counts = readValidation(connection, runID)
    if counts[0] != 0:
        raise ValueError("No-valid-orders branch cannot run when valid orders exist")
    connection.execute("DELETE FROM reporting.order_summary WHERE run_id = %s", (runID,))
    return recordOutcome(connection, runID, "no_valid_orders", counts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["load_orders", "validate_orders", "build_summary", "record_no_valid_orders"])
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--dataset", choices=["mixed", "no_valid"], default="mixed")
    parser.add_argument("--failure-mode", choices=["none", "first_attempt", "always"], default="none")
    parser.add_argument("--attempt-number", type=int, default=1)
    args = parser.parse_args()
    if args.attempt_number < 1:
        parser.error("--attempt-number must be positive")
    if args.failure_mode != "none" and args.command != "build_summary":
        parser.error("Demonstration failures are supported only for build_summary")

    context = {"command": args.command, "run_id": args.run_id, "attempt_number": args.attempt_number}
    print(json.dumps({**context, "event": "started", "failure_mode": args.failure_mode}), flush=True)
    try:
        if args.failure_mode == "always" or (args.failure_mode == "first_attempt" and args.attempt_number == 1):
            raise RuntimeError("Controlled demonstration failure before report writes")
        with connectDatabase("processing", "processing", "PROCESSING_PASSWORD") as connection:
            if args.command == "load_orders":
                result = loadOrders(connection, args.run_id, args.dataset)
            elif args.command == "validate_orders":
                result = validateOrders(connection, args.run_id)
            elif args.command == "build_summary":
                result = buildSummary(connection, args.run_id)
            else:
                result = recordNoValidOrders(connection, args.run_id)
        print(json.dumps({**context, "event": "committed", **result}), flush=True)
        return 0
    except Exception as error:
        # Do not print connection strings or credentials in logs.
        print(json.dumps({**context, "event": "failed", "error_type": type(error).__name__}), file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
