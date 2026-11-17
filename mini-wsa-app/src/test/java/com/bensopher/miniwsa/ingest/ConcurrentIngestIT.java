package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same event sent by several clients at the same moment is stored exactly once. Nothing in the Java code
 * coordinates the requests: the guarantee comes from the (event_id, event_time) primary key plus
 * INSERT ... ON CONFLICT DO NOTHING RETURNING, which makes concurrent inserters of the same key wait for each other.
 */
class ConcurrentIngestIT extends AbstractIntegrationTest {

    private static final int CLIENTS = 8;
    private static final int ROUNDS = 10;

    @Test
    void sameEventFromConcurrentRequestsIsStoredExactlyOnce() throws Exception {
        String template = Files.readString(Path.of("../examples/single-event.json"));
        RestTestClient ingester = asIngester();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int round = 0; round < ROUNDS; round++) {
                String body = template.replace("\"evt-00132\"", "\"race-" + round + "\"");
                CountDownLatch start = new CountDownLatch(1);
                List<Future<IngestResponse>> responses = new ArrayList<>();
                for (int client = 0; client < CLIENTS; client++) {
                    responses.add(pool.submit(() -> {
                        start.await(); // release all clients at once to maximize the overlap
                        return ingester.post().uri("/v1/events/ingest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body)
                                .exchange()
                                .expectStatus().isCreated()
                                .expectBody(IngestResponse.class).returnResult().getResponseBody();
                    }));
                }
                start.countDown();

                int storedByAll = 0;
                for (Future<IngestResponse> response : responses) {
                    storedByAll += response.get().stored();
                }
                assertThat(storedByAll).as("round %d: responses reporting the event as stored", round).isEqualTo(1);
            }
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM security_events", Integer.class)).isEqualTo(ROUNDS);
    }
}
