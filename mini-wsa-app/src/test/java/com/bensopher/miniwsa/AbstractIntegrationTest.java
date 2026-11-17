package com.bensopher.miniwsa;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

/**
 * Base class for integration tests: the full application on a random port, talking to a real
 * TimescaleDB. The container is a singleton started once per JVM (not {@code @Container}, which
 * would stop it after each test class). Every test starts from empty tables and with the clock at {@link #NOW}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    /** The server's "now" at the start of every test. */
    protected static final Instant NOW = Instant.parse("2026-11-18T10:15:30.123Z");

    @ServiceConnection
    static final PostgreSQLContainer TIMESCALE = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb:2.32.0-pg17").asCompatibleSubstituteFor("postgres"))
            .withEnv("TIMESCALEDB_TELEMETRY", "off");

    static {
        TIMESCALE.start();
    }

    @Autowired
    protected RestTestClient client;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE security_events");
        clock.set(NOW);
    }

    /** A client sending the dev ingest key (role INGEST). */
    protected RestTestClient asIngester() {
        return withKey("dev-ingest-key");
    }

    /** A client sending the dev read key (role READ). */
    protected RestTestClient asReader() {
        return withKey("dev-read-key");
    }

    /** A client sending the dev admin key (roles READ and ADMIN). */
    protected RestTestClient asAdmin() {
        return withKey("dev-admin-key");
    }

    /** A client sending no API key. */
    protected RestTestClient anonymous() {
        return client;
    }

    protected RestTestClient withKey(String apiKey) {
        return client.mutate().defaultHeader("X-API-Key", apiKey).build();
    }
}
