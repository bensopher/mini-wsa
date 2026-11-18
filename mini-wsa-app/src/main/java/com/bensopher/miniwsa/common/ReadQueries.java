package com.bensopher.miniwsa.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/** The JDBC template every read repository uses. */
public final class ReadQueries {

    private ReadQueries() {
    }

    /**
     * A template whose statements time out after {@code miniwsa.limits.read-query-timeout}, so one expensive
     * query can't hold a pooled connection indefinitely. The driver cancels the statement, and the error handler
     * turns that into a 503. Writes don't use it: a slow insert should finish, not be cancelled halfway.
     */
    public static NamedParameterJdbcTemplate template(DataSource dataSource, LimitsProperties limits) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout((int) limits.readQueryTimeout().toSeconds());
        return new NamedParameterJdbcTemplate(template);
    }
}
