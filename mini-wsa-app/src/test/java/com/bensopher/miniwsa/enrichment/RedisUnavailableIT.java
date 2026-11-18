package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import com.bensopher.miniwsa.TestClockConfig;
import com.bensopher.miniwsa.ingest.IngestResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-open: with Redis hanging or gone, ingestion still stores events (without the repeat bonus), quickly.
 * Uses its OWN Redis container, which it pauses and then stops, so the shared one is never disturbed.
 * The tests run in order because each one changes that container's state.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestClockConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisUnavailableIT {

    /** Well under Lettuce's 60s default, with room for a slow CI machine above the 500ms Redis timeout. */
    private static final Duration FAIL_OPEN_BUDGET = Duration.ofSeconds(2);
    private static final Instant T0 = Instant.parse("2026-05-20T14:00:00Z");

    static final GenericContainer<?> OWN_REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.3-alpine"))
            .withExposedPorts(6379);

    static {
        OWN_REDIS.start();
    }

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractIntegrationTest.TIMESCALE::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractIntegrationTest.TIMESCALE::getUsername);
        registry.add("spring.datasource.password", AbstractIntegrationTest.TIMESCALE::getPassword);
        registry.add("spring.data.redis.host", OWN_REDIS::getHost);
        registry.add("spring.data.redis.port", () -> OWN_REDIS.getMappedPort(6379));
    }

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void emptyTable() {
        jdbc.execute("TRUNCATE security_events");
    }

    @Test
    @Order(1)
    void withRedisUpTheSixthEventGetsTheRepeatBonus() {
        for (int i = 0; i < 5; i++) {
            ingest(event("warmup-" + i, i));
        }

        assertThat(ingest(event("sixth", 5)).events().getFirst().threatScore()).isEqualTo(90);
    }

    @Test
    @Order(2)
    void hangingRedisFailsOpenWithinTheTimeout() {
        double unavailableBefore = unavailableCount();
        OWN_REDIS.getDockerClient().pauseContainerCmd(OWN_REDIS.getContainerId()).exec();
        try {
            long started = System.nanoTime();
            IngestResponse response = ingest(event("while-paused", 6));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            // Five earlier events from this IP exist, but Redis can't be asked: stored, scored without the bonus.
            assertThat(response.stored()).isEqualTo(1);
            assertThat(response.events().getFirst().threatScore()).isEqualTo(75);
            assertThat(took).isLessThan(FAIL_OPEN_BUDGET);
            assertThat(unavailableCount()).isEqualTo(unavailableBefore + 1);
        } finally {
            OWN_REDIS.getDockerClient().unpauseContainerCmd(OWN_REDIS.getContainerId()).exec();
        }
    }

    @Test
    @Order(3)
    void stoppedRedisFailsOpenWithinTheTimeout() {
        double unavailableBefore = unavailableCount();
        OWN_REDIS.stop();

        long started = System.nanoTime();
        IngestResponse response = ingest(event("while-stopped", 7));
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(response.stored()).isEqualTo(1);
        assertThat(response.events().getFirst().threatScore()).isEqualTo(75);
        assertThat(took).isLessThan(FAIL_OPEN_BUDGET);
        assertThat(unavailableCount()).isEqualTo(unavailableBefore + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM security_events", Integer.class)).isEqualTo(1);
    }

    @Test
    @Order(4)
    void withRedisDownTheInstanceStaysReadyWhileOverallHealthReportsIt() {
        // Readiness is what a load balancer acts on. Ingestion works without Redis, so the instance stays in.
        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN")
                .jsonPath("$.components.redis.status").isEqualTo("DOWN");
    }

    private IngestResponse ingest(String body) {
        return client.post().uri("/v1/events/ingest")
                .header("X-API-Key", "dev-ingest-key")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(IngestResponse.class).returnResult().getResponseBody();
    }

    private double unavailableCount() {
        return meterRegistry.counter("miniwsa.enrichment.repeat_offender.unavailable").count();
    }

    /** The sample event (75 points without the repeat bonus) at T0 + the given minutes. */
    private static String event(String eventId, int minutes) {
        return """
                {"eventId": "%s", "timestamp": "%s", "configId": 14227, "clientIp": "203.0.113.42",
                 "path": "/api/v1/login", "method": "POST",
                 "rule": {"id": "950001", "severity": "CRITICAL", "category": "INJECTION"}, "action": "DENY"}
                """.formatted(eventId, T0.plus(Duration.ofMinutes(minutes)));
    }
}
