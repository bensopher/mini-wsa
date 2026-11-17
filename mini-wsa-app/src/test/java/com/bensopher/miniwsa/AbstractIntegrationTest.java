package com.bensopher.miniwsa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

/**
 * Base class for integration tests: the full application on a random port, talking to a real TimescaleDB
 * and Redis. The containers are singletons started once per JVM (not {@code @Container}, which would stop
 * them after each test class). Every test starts with empty tables, an empty Redis and the clock at {@link #NOW}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    /** The server's "now" at the start of every test. */
    protected static final Instant NOW = Instant.parse("2026-11-18T10:15:30.123Z");

    /** Shared with tests that need the database but their own Redis (see RedisUnavailableIT). */
    @ServiceConnection
    public static final PostgreSQLContainer TIMESCALE = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb:2.32.0-pg17").asCompatibleSubstituteFor("postgres"))
            .withEnv("TIMESCALEDB_TELEMETRY", "off");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.3-alpine"))
            .withExposedPorts(6379);

    static {
        Startables.deepStart(TIMESCALE, REDIS).join();
    }

    /** A client for this test's server that logs every exchange; see {@link ExchangeLoggingInterceptor}. */
    protected RestTestClient client;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    void setClient(RestTestClient serverClient) {
        this.client = serverClient.mutate().requestInterceptor(new ExchangeLoggingInterceptor()).build();
    }

    @BeforeEach
    void resetState(TestInfo test) {
        // Marks where each test starts in the output, so the exchanges logged below it can be attributed.
        log.info("=== {}.{}", getClass().getSimpleName(), test.getTestMethod().map(m -> m.getName()).orElse("?"));
        jdbc.execute("TRUNCATE security_events");
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
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
