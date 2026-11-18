package com.bensopher.miniwsa.stats;

import com.bensopher.miniwsa.common.LimitsProperties;
import com.bensopher.miniwsa.common.ReadQueries;
import com.bensopher.miniwsa.common.SqlFilter;
import com.bensopher.miniwsa.stats.StatsSummary.AttackerStats;
import com.bensopher.miniwsa.stats.StatsSummary.CategoryStats;
import com.bensopher.miniwsa.stats.StatsSummary.PathStats;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregations over security_events for the summary endpoint. */
@Repository
public class StatsRepository {

    static final int TOP_N = 10;

    private final NamedParameterJdbcTemplate jdbc;

    public StatsRepository(DataSource dataSource, LimitsProperties limits) {
        this.jdbc = ReadQueries.template(dataSource, limits);
    }

    /** The four sections of a summary, read from one snapshot. */
    public record Sections(Map<String, CategoryStats> byCategory, Map<String, Long> byAction,
            List<AttackerStats> topAttackers, List<PathStats> topTargetedPaths) {
    }

    /**
     * Runs the four queries one after another in one read-only REPEATABLE READ transaction, so every section
     * sees the same snapshot and the totals agree even while ingestion is running. Running them in parallel
     * would need four connections per request and would lose that shared snapshot.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Sections load(SqlFilter filter) {
        return new Sections(byCategory(filter), byAction(filter), topAttackers(filter), topTargetedPaths(filter));
    }

    private Map<String, CategoryStats> byCategory(SqlFilter filter) {
        Map<String, CategoryStats> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT category, count(*) AS events, round(avg(threat_score)::numeric, 1) AS avg_score
                FROM security_events
                %s
                GROUP BY category
                ORDER BY events DESC, category
                """.formatted(filter.where()), filter.params(), rs -> {
            result.put(rs.getString("category"), new CategoryStats(rs.getLong("events"), average(rs)));
        });
        return result;
    }

    private Map<String, Long> byAction(SqlFilter filter) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT action, count(*) AS events
                FROM security_events
                %s
                GROUP BY action
                """.formatted(filter.where()), filter.params(), rs -> {
            result.put(rs.getString("action"), rs.getLong("events"));
        });
        return result;
    }

    private List<AttackerStats> topAttackers(SqlFilter filter) {
        // Ties are broken by address, so results are deterministic. The output column is called ip, not
        // client_ip, so ORDER BY sorts the inet column (10.0.0.9 before 10.0.0.10), not the text from host().
        return jdbc.query("""
                SELECT host(client_ip) AS ip, count(*) AS events, round(avg(threat_score)::numeric, 1) AS avg_score
                FROM security_events
                %s
                GROUP BY client_ip
                ORDER BY events DESC, client_ip
                LIMIT %d
                """.formatted(filter.where(), TOP_N), filter.params(),
                (rs, row) -> new AttackerStats(rs.getString("ip"), rs.getLong("events"), average(rs)));
    }

    /**
     * Ranks endpoints, not URLs: the query string is dropped, so /search?q=shoes and /search?q=<script> both
     * count toward /search. Otherwise every distinct attack payload would be its own entry.
     */
    private List<PathStats> topTargetedPaths(SqlFilter filter) {
        return jdbc.query("""
                SELECT split_part(path, '?', 1) AS endpoint, count(*) AS events
                FROM security_events
                %s
                GROUP BY endpoint
                ORDER BY events DESC, endpoint
                LIMIT %d
                """.formatted(filter.where(), TOP_N), filter.params(),
                (rs, row) -> new PathStats(rs.getString("endpoint"), rs.getLong("events")));
    }

    /** avg() ignores NULL scores (rows stored before scoring existed), and is NULL if every score is. */
    private static Double average(ResultSet rs) throws SQLException {
        BigDecimal average = rs.getBigDecimal("avg_score");
        return average == null ? null : average.doubleValue();
    }
}
