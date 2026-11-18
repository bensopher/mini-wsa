-- Chunks older than 7 days are converted to TimescaleDB's compressed columnar format: much smaller on disk and
-- faster to aggregate. Rows are grouped by config_id (most queries filter on it) and ordered by time within it.
-- event_id is in the ordering too because it is part of the primary key: compressed batches then keep its
-- min/max, so the duplicate check for a late or retried event only decompresses batches that could hold it.
-- TimescaleDB >= 2.18 calls this the "columnstore" (enable_columnstore, add_columnstore_policy); the compress
-- names below are still supported and match most documentation.
ALTER TABLE security_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'config_id',
    timescaledb.compress_orderby   = 'event_time DESC, event_id'
);

SELECT add_compression_policy('security_events', INTERVAL '7 days');

-- No retention policy on purpose: old events (such as replayed or sample data) would silently disappear.
