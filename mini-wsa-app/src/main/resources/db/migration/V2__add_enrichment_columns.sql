-- Nullable so rows written before enrichment existed stay valid; every new write fills both.
ALTER TABLE security_events
    ADD COLUMN attack_type  TEXT,
    ADD COLUMN threat_score SMALLINT CHECK (threat_score BETWEEN 0 AND 100);
