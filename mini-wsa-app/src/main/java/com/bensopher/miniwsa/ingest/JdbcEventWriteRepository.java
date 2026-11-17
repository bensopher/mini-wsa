package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.event.SecurityEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Writes events to the security_events hypertable. */
@Repository
public class JdbcEventWriteRepository {

    /** 21 parameters per row x 500 rows stays far below Postgres's 65,535 bind-parameter limit. */
    static final int CHUNK_SIZE = 500;

    private static final String COLUMNS = """
            event_id, event_time, received_at, config_id, policy_id, client_ip, hostname, path, method, \
            status_code, user_agent, rule_id, rule_name, rule_message, severity, category, action, \
            geo_country, geo_city, request_size, response_size""";

    private static final String ROW_PLACEHOLDERS =
            "(?, ?, ?, ?, ?, CAST(? AS inet), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;

    public JdbcEventWriteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the events and returns the keys of the rows actually written; any other key already existed.
     * One multi-row INSERT per chunk, all chunks in one transaction: a batch is stored completely or not at all.
     * <p>
     * Why not {@code batchUpdate} with pgjdbc's {@code reWriteBatchedInserts}: it reports every row as
     * SUCCESS_NO_INFO, which makes duplicates impossible to tell apart. {@code RETURNING} names exactly the
     * rows that were inserted.
     */
    @Transactional
    public Set<EventKey> insert(List<SecurityEvent> events, Instant receivedAt) {
        Set<EventKey> inserted = new HashSet<>();
        for (int from = 0; from < events.size(); from += CHUNK_SIZE) {
            List<SecurityEvent> chunk = events.subList(from, Math.min(from + CHUNK_SIZE, events.size()));
            String sql = "INSERT INTO security_events (" + COLUMNS + ") VALUES "
                    + String.join(", ", Collections.nCopies(chunk.size(), ROW_PLACEHOLDERS))
                    + " ON CONFLICT (event_id, event_time) DO NOTHING RETURNING event_id, event_time";
            inserted.addAll(jdbc.query(sql,
                    (rs, row) -> new EventKey(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant()),
                    parameters(chunk, receivedAt)));
        }
        return inserted;
    }

    /** Explicit SQL types, so NULLs in optional columns need no extra metadata round trip to bind. */
    private static Object[] parameters(List<SecurityEvent> events, Instant receivedAt) {
        List<SqlParameterValue> values = new ArrayList<>(events.size() * 21);
        for (SecurityEvent e : events) {
            values.add(text(e.eventId()));
            values.add(timestamp(e.timestamp()));
            values.add(timestamp(receivedAt));
            values.add(new SqlParameterValue(Types.BIGINT, e.configId()));
            values.add(text(e.policyId()));
            values.add(text(e.clientIp()));
            values.add(text(e.hostname()));
            values.add(text(e.path()));
            values.add(text(e.method()));
            values.add(new SqlParameterValue(Types.SMALLINT, e.statusCode()));
            values.add(text(e.userAgent()));
            values.add(text(e.rule().id()));
            values.add(text(e.rule().name()));
            values.add(text(e.rule().message()));
            values.add(text(e.rule().severity().name()));
            values.add(text(e.rule().category().name()));
            values.add(text(e.action().name()));
            values.add(text(e.geoLocation() == null ? null : e.geoLocation().country()));
            values.add(text(e.geoLocation() == null ? null : e.geoLocation().city()));
            values.add(new SqlParameterValue(Types.BIGINT, e.requestSize()));
            values.add(new SqlParameterValue(Types.BIGINT, e.responseSize()));
        }
        return values.toArray();
    }

    private static SqlParameterValue text(String value) {
        return new SqlParameterValue(Types.VARCHAR, value);
    }

    /** pgjdbc binds OffsetDateTime as timestamptz; it has no reliable mapping for Instant. */
    private static SqlParameterValue timestamp(Instant instant) {
        return new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, instant.atOffset(ZoneOffset.UTC));
    }
}
