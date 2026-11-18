package com.bensopher.miniwsa.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SqlFilterTest {

    private static final TimeRange RANGE =
            new TimeRange(Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-05-21T00:00:00Z"));

    @Test
    void timeRangeIsHalfOpenAndBoundAsUtcOffsetDateTimes() {
        SqlFilter filter = SqlFilter.timeRange(RANGE);

        assertThat(filter.where()).isEqualTo("WHERE event_time >= :from AND event_time < :to");
        assertThat(filter.params().getValue("from")).isEqualTo(OffsetDateTime.of(2026, 5, 20, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(filter.params().getValue("to")).isEqualTo(OffsetDateTime.of(2026, 5, 21, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void suppliedFiltersAreAddedAsBindParameters() {
        SqlFilter filter = SqlFilter.timeRange(RANGE).eq(FilterColumn.CONFIG_ID, 14227L);

        assertThat(filter.where())
                .isEqualTo("WHERE event_time >= :from AND event_time < :to AND config_id = :config_id");
        assertThat(filter.params().getValue("config_id")).isEqualTo(14227L);
    }

    @Test
    void missingFiltersAddNothing() {
        SqlFilter filter = SqlFilter.timeRange(RANGE).eq(FilterColumn.CONFIG_ID, null);

        assertThat(filter.where()).isEqualTo("WHERE event_time >= :from AND event_time < :to");
        assertThat(filter.params().hasValue("config_id")).isFalse();
    }
}
