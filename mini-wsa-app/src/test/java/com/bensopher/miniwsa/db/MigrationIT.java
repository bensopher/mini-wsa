package com.bensopher.miniwsa.db;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void configTimeIndexExists() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'security_events' AND indexname = 'ix_security_events_config_time'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
