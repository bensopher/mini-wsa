package com.bensopher.miniwsa.db;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationIT extends AbstractIntegrationTest {

    @Test
    void securityEventsIsADailyHypertable() {
        String interval = jdbc.queryForObject("""
                SELECT time_interval::text FROM timescaledb_information.dimensions
                WHERE hypertable_name = 'security_events' AND column_name = 'event_time'
                """, String.class);

        assertThat(interval).isEqualTo("1 day");
    }

    @Test
    void enrichmentColumnsAreNullableAndScoreIsBounded() {
        var columns = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable FROM information_schema.columns
                WHERE table_name = 'security_events' AND column_name IN ('attack_type', 'threat_score')
                ORDER BY column_name
                """);
        assertThat(columns).containsExactly(
                Map.of("column_name", "attack_type", "data_type", "text", "is_nullable", "YES"),
                Map.of("column_name", "threat_score", "data_type", "smallint", "is_nullable", "YES"));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO security_events (event_id, event_time, received_at, config_id, client_ip, path, method,
                    rule_id, severity, category, action, threat_score)
                VALUES ('e1', now(), now(), 1, '192.0.2.1', '/', 'GET', '1', 'LOW', 'BOT', 'MONITOR', 101)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void configTimeIndexExists() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'security_events' AND indexname = 'ix_security_events_config_time'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
