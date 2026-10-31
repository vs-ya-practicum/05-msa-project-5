\set ON_ERROR_STOP on

\connect source
BEGIN;

-- The technical record key remains present even when business fields are invalid.
CREATE TABLE IF NOT EXISTS public.orders (
    source_record_id bigint PRIMARY KEY,
    dataset text NOT NULL CHECK (dataset IN ('mixed', 'no_valid')),
    order_id text,
    order_date date,
    customer_id text,
    amount numeric(18, 2)
);

INSERT INTO public.orders
    (source_record_id, dataset, order_id, order_date, customer_id, amount)
VALUES
    (1, 'mixed', 'order-001', '2026-10-01', 'customer-001', 100.00),
    (2, 'mixed', 'order-002', '2026-10-01', 'customer-002', 50.00),
    (3, 'mixed', 'order-003', '2026-10-02', 'customer-001', 75.00),
    (4, 'mixed', 'order-004', '2026-10-02', NULL, 20.00),
    (5, 'mixed', 'order-005', '2026-10-02', 'customer-003', -10.00),
    (6, 'no_valid', NULL, '2026-10-01', 'customer-001', 100.00),
    (7, 'no_valid', 'order-007', NULL, 'customer-002', 50.00),
    (8, 'no_valid', 'order-008', '2026-10-02', 'customer-003', 0.00)
ON CONFLICT (source_record_id) DO NOTHING;

-- Explicitly grant access as well as relying on bootstrap default privileges.
GRANT USAGE ON SCHEMA public TO source_reader;
GRANT SELECT ON public.orders TO source_reader;

COMMIT;

\connect processing
SET ROLE processing;
BEGIN;

CREATE TABLE IF NOT EXISTS staging.orders (
    run_id text NOT NULL,
    source_record_id bigint NOT NULL,
    dataset text NOT NULL,
    order_id text,
    order_date date,
    customer_id text,
    amount numeric(18, 2),
    is_valid boolean,
    rejection_reasons text[] NOT NULL DEFAULT '{}',
    PRIMARY KEY (run_id, source_record_id)
);

CREATE TABLE IF NOT EXISTS staging.validation_results (
    run_id text PRIMARY KEY,
    total_count bigint NOT NULL,
    valid_count bigint NOT NULL,
    rejected_count bigint NOT NULL,
    CHECK (total_count = valid_count + rejected_count)
);

CREATE TABLE IF NOT EXISTS reporting.order_summary (
    run_id text NOT NULL,
    order_date date NOT NULL,
    order_count bigint NOT NULL,
    total_amount numeric(18, 2) NOT NULL,
    PRIMARY KEY (run_id, order_date)
);

CREATE TABLE IF NOT EXISTS reporting.run_results (
    run_id text PRIMARY KEY,
    outcome text NOT NULL CHECK (outcome IN ('summary_produced', 'no_valid_orders')),
    valid_count bigint NOT NULL,
    rejected_count bigint NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMIT;
RESET ROLE;
