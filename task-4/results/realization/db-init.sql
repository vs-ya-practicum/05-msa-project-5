SELECT
    to_regclass('public.products') IS NULL
        AND to_regclass('public.loyality_data') IS NULL AS fresh_database,
    to_regclass('public.products') IS NOT NULL
        AND to_regclass('public.loyality_data') IS NOT NULL AS existing_tables
\gset

\if :fresh_database
    \ir schema-all.sql
    \copy loyality_data (productSku, loyalityData) FROM '/init/loyality_data.csv' WITH (FORMAT csv)
    \echo 'Business tables created and loyalty data loaded.'
\elif :existing_tables
    \echo 'Business tables already exist; existing data preserved.'
\else
    DO $$
    BEGIN
        RAISE EXCEPTION 'Incomplete business schema: expected both products and loyality_data, or neither.';
    END
    $$;
\endif
