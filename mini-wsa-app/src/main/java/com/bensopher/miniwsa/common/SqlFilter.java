package com.bensopher.miniwsa.common;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Types;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a WHERE clause from only the filters actually supplied:
 * <pre>{@code
 * SqlFilter filter = SqlFilter.timeRange(range).eq(FilterColumn.CONFIG_ID, configId);
 * jdbc.query("SELECT ... FROM security_events " + filter.where() + " GROUP BY ...", filter.params(), mapper);
 * }</pre>
 * Deliberately not {@code (:configId IS NULL OR config_id = :configId)}: Postgres plans that once for both
 * cases, so it can't use the {@code (config_id, event_time)} index for either. Every value is a bind
 * parameter, and column names come only from {@link FilterColumn}.
 */
public final class SqlFilter {

    private final List<String> predicates = new ArrayList<>();
    private final MapSqlParameterSource params = new MapSqlParameterSource();

    private SqlFilter() {
    }

    /** Every read query is bounded in event time (A9), which also lets the hypertable skip whole chunks. */
    public static SqlFilter timeRange(TimeRange range) {
        SqlFilter filter = new SqlFilter();
        filter.predicates.add("event_time >= :from AND event_time < :to");
        // pgjdbc binds OffsetDateTime as timestamptz; it has no reliable mapping for Instant.
        filter.params.addValue("from", range.from().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        filter.params.addValue("to", range.to().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        return filter;
    }

    /** Adds {@code column = value}, or nothing when the value is null (the filter wasn't supplied). */
    public SqlFilter eq(FilterColumn column, Object value) {
        if (value != null) {
            String param = column.name().toLowerCase();
            predicates.add(column.column() + " = :" + param);
            params.addValue(param, value);
        }
        return this;
    }

    public String where() {
        return "WHERE " + String.join(" AND ", predicates);
    }

    public SqlParameterSource params() {
        return params;
    }
}
