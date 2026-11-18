package com.bensopher.miniwsa.samples;

import com.bensopher.miniwsa.common.LimitsProperties;
import com.bensopher.miniwsa.common.ReadQueries;
import com.bensopher.miniwsa.common.SqlFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/** Reads pages of stored events. */
@Repository
public class SamplesRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SamplesRepository(DataSource dataSource, LimitsProperties limits) {
        this.jdbc = ReadQueries.template(dataSource, limits);
    }

    /**
     * The count and the page run in one read-only REPEATABLE READ transaction, so {@code total} describes the same
     * snapshot as the items, even while ingestion is running.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SamplesPage load(SqlFilter filter, int limit, int offset) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM security_events %s".formatted(filter.where()),
                filter.params(), Long.class);
        MapSqlParameterSource params = filter.params().addValue("limit", limit).addValue("offset", offset);
        // event_id breaks ties between events with the same timestamp, so pages are stable: without it, an event
        // could appear on two pages or on none.
        List<SampleEvent> items = jdbc.query("""
                SELECT event_id, event_time, received_at, config_id, policy_id, host(client_ip) AS ip, hostname,
                       path, method, status_code, user_agent, rule_id, rule_name, rule_message, severity, category,
                       action, geo_country, geo_city, request_size, response_size, attack_type, threat_score
                FROM security_events
                %s
                ORDER BY event_time DESC, event_id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(filter.where()), params, (rs, row) -> toSample(rs));
        return new SamplesPage(total, limit, offset, items);
    }

    private static SampleEvent toSample(ResultSet rs) throws SQLException {
        String country = rs.getString("geo_country");
        String city = rs.getString("geo_city");
        return new SampleEvent(
                rs.getString("event_id"),
                rs.getObject("event_time", OffsetDateTime.class).toInstant(),
                rs.getLong("config_id"),
                rs.getString("policy_id"),
                rs.getString("ip"),
                rs.getString("hostname"),
                rs.getString("path"),
                rs.getString("method"),
                rs.getObject("status_code", Integer.class),
                rs.getString("user_agent"),
                new SampleEvent.Rule(rs.getString("rule_id"), rs.getString("rule_name"), rs.getString("rule_message"),
                        rs.getString("severity"), rs.getString("category")),
                rs.getString("action"),
                country == null && city == null ? null : new SampleEvent.Geo(country, city),
                rs.getObject("request_size", Long.class),
                rs.getObject("response_size", Long.class),
                rs.getString("attack_type"),
                rs.getObject("threat_score", Integer.class),
                rs.getObject("received_at", OffsetDateTime.class).toInstant());
    }
}
