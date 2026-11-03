import { createWriteStream } from 'node:fs';
import { mkdir, rename, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import pg from 'pg';
import QueryStream from 'pg-query-stream';

type TShipment = {
    id: number;
    shipment_date: string;
    origin: string;
    destination: string;
    status: string;
};

function requireEnvironment(name: string): string {
    const value = process.env[name];
    if (!value) {
        throw new Error(`Missing environment variable: ${name}`);
    }
    return value;
}

function escapeCSV(value: string | number): string {
    const text = String(value);
    return `"${text.replaceAll('"', '""')}"`;
}

async function exportShipments(): Promise<void> {
    const timeZone = requireEnvironment('REPORT_TIME_ZONE');
    const outputDirectory = requireEnvironment('OUTPUT_DIR');
    for (const name of ['PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD']) {
        requireEnvironment(name);
    }

    const client = new pg.Client({ connectionTimeoutMillis: 10_000 });
    let temporaryPath: string | undefined;

    try {
        await client.connect();
        const dateResult = await client.query<{ export_date: string; }>(
            `SELECT to_char(
         (CURRENT_TIMESTAMP AT TIME ZONE $1)::date - 1,
         'YYYY-MM-DD'
       ) AS export_date`,
            [timeZone],
        );
        const exportDate = dateResult.rows[0].export_date;
        await mkdir(outputDirectory, { recursive: true });
        const outputPath = join(outputDirectory, `shipments-${exportDate}.csv`);
        temporaryPath = `${outputPath}.${process.pid}.${Date.now()}.tmp`;

        console.log(JSON.stringify({
            status: 'started',
            export_date: exportDate,
            time_zone: timeZone,
            output_path: outputPath,
        }));

        let rowCount = 0;
        const header = 'id,shipment_date,origin,destination,status\r\n';
        const csvTransform = new Transform({
            writableObjectMode: true,
            transform(row: TShipment, _encoding, callback) {
                if (rowCount === 0) {
                    this.push(header);
                }
                const values = [row.id, row.shipment_date, row.origin, row.destination, row.status];
                rowCount += 1;
                callback(null, `${values.map(escapeCSV).join(',')}\r\n`);
            },
            flush(callback) {
                if (rowCount === 0) {
                    this.push(header);
                }
                callback();
            },
        });

        const query = new QueryStream(
            `SELECT id, shipment_date::text AS shipment_date, origin, destination, status
       FROM shipments
       WHERE shipment_date = $1::date
       ORDER BY id`,
            [exportDate],
        );
        await pipeline(
            client.query(query),
            csvTransform,
            createWriteStream(temporaryPath, { flags: 'wx' }),
        );
        await rename(temporaryPath, outputPath);
        temporaryPath = undefined;

        console.log(JSON.stringify({
            status: 'completed',
            export_date: exportDate,
            row_count: rowCount,
            output_path: outputPath,
        }));
    } finally {
        try {
            if (temporaryPath) {
                await rm(temporaryPath, { force: true });
            }
        } finally {
            await client.end();
        }
    }
}

exportShipments().catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    console.error(JSON.stringify({ status: 'failed', message }));
    process.exitCode = 1;
});
