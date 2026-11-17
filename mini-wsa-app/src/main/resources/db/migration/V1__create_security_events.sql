CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Enum-like columns (severity, category, action) are TEXT without CHECK constraints on purpose:
-- the application validates them, and adding a category must not require a migration.
CREATE TABLE security_events (
    event_id      TEXT        NOT NULL,
    event_time    TIMESTAMPTZ NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL,
    config_id     BIGINT      NOT NULL,
    policy_id     TEXT,
    client_ip     INET        NOT NULL,
    hostname      TEXT,
    path          TEXT        NOT NULL,
    method        TEXT        NOT NULL,
    status_code   SMALLINT,
    user_agent    TEXT,
    rule_id       TEXT        NOT NULL,
    rule_name     TEXT,
    rule_message  TEXT,
    severity      TEXT        NOT NULL,
    category      TEXT        NOT NULL,
    action        TEXT        NOT NULL,
    geo_country   TEXT,
    geo_city      TEXT,
    request_size  BIGINT,
    response_size BIGINT,
    -- Hypertables require the partition column in every unique key: dedup is per (event_id, event_time) (A4)
    PRIMARY KEY (event_id, event_time)
);

-- Day-sized chunks: a time-range query only touches the chunks it needs.
SELECT create_hypertable('security_events', by_range('event_time', INTERVAL '1 day'));

-- Most queries filter by config + time range. The hypertable already has (event_time DESC) for the no-config case.
CREATE INDEX ix_security_events_config_time ON security_events (config_id, event_time DESC);
