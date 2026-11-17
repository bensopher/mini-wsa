package com.bensopher.miniwsa;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests: the full application on a random port, talking to a real
 * TimescaleDB. The container is a singleton started once per JVM (not {@code @Container}, which
 * would stop it after each test class), and every test starts from empty tables.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
public abstract class AbstractIntegrationTest {

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

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE security_events");
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
