package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A slow query and an exhausted pool both end in a fast 503 with Retry-After. Both are forced
 * deterministically (a held table lock, connections held by the test) rather than with timing tricks.
 */
@TestPropertySource(properties = "miniwsa.limits.read-query-timeout=1s")
class DatabaseBackpressureIT extends AbstractIntegrationTest {

    private static final int POOL_SIZE = 10;

    @Autowired
    DataSource dataSource;

    @Test
    void aReadQueryBlockedPastTheTimeoutIsCancelledWith503() throws Exception {
        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            try (Statement statement = locker.createStatement()) {
                // Blocks every reader of the table until this transaction ends: the stats query has to wait.
                statement.execute("LOCK TABLE security_events IN ACCESS EXCLUSIVE MODE");
            }
            long started = System.nanoTime();

            asReader().get().uri("/v1/stats/summary").exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals("Retry-After", "5")
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.type").isEqualTo("https://github.com/bensopher/mini-wsa/errors/service-unavailable")
                    .jsonPath("$.detail")
                    .isEqualTo("The query took too long and was cancelled. Narrow the time range or try again later.");

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
            locker.rollback();
        }
    }

    @Test
    void anExhaustedPoolFailsFastWith503ForReadsAndIngestion() throws Exception {
        String event = Files.readString(Path.of("../examples/single-event.json"));
        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < POOL_SIZE; i++) {
                held.add(dataSource.getConnection());
            }
            long started = System.nanoTime();

            asReader().get().uri("/v1/stats/summary").exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals("Retry-After", "5")
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("The service is temporarily overloaded. Try again shortly.");
            asIngester().post().uri("/v1/events/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals("Retry-After", "5");

            // Two requests, each waiting at most the pool's 2s connection timeout, never indefinitely.
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(6));
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
        // Nothing was stored, so the client's retry (once connections are free) stores the event.
        asIngester().post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(event)
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.stored").isEqualTo(1);
    }
}
