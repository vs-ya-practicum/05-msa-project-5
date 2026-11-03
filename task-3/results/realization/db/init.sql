CREATE TABLE shipments (
    id integer PRIMARY KEY,
    shipment_date date NOT NULL,
    origin text NOT NULL,
    destination text NOT NULL,
    status text NOT NULL
);

INSERT INTO shipments (id, shipment_date, origin, destination, status)
SELECT
    id,
    (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::date
        - CASE
            WHEN id <= 90 THEN 1
            WHEN id <= 95 THEN 0
            ELSE 2
          END,
    'Moscow',
    'Destination ' || id,
    'delivered'
FROM generate_series(1, 100) AS sample(id);
