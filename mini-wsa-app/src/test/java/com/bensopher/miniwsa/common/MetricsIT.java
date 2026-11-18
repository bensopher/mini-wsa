package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** Custom metrics, read as differences because the registry is shared by every test in this context. */
class MetricsIT extends AbstractIntegrationTest {

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void storedEventsAreCountedByCategoryWithTheirScoresAndDuplicatesSeparately() throws Exception {
        double injection = counter("miniwsa.events.ingested", "category", "INJECTION");
        double bot = counter("miniwsa.events.ingested", "category", "BOT");
        double duplicates = counter("miniwsa.events.duplicates");
        long batches = summary("miniwsa.ingest.batch.size").count();
        double batchEvents = summary("miniwsa.ingest.batch.size").totalAmount();
        long scores = summary("miniwsa.events.threat_score").count();
        double scoreTotal = summary("miniwsa.events.threat_score").totalAmount();

        ingest(Files.readString(Path.of("../examples/single-event.json")), 201);
        // Its third event repeats the single event: stored once, counted as a duplicate here.
        ingest(Files.readString(Path.of("../examples/batch-events.json")), 201);

        assertThat(counter("miniwsa.events.ingested", "category", "INJECTION")).isEqualTo(injection + 1);
        assertThat(counter("miniwsa.events.ingested", "category", "BOT")).isEqualTo(bot + 1);
        assertThat(counter("miniwsa.events.duplicates")).isEqualTo(duplicates + 1);
        assertThat(summary("miniwsa.ingest.batch.size").count()).isEqualTo(batches + 2);
        assertThat(summary("miniwsa.ingest.batch.size").totalAmount()).isEqualTo(batchEvents + 4);
        // Scores of the three stored events: 75, 20 and 40.
        assertThat(summary("miniwsa.events.threat_score").count()).isEqualTo(scores + 3);
        assertThat(summary("miniwsa.events.threat_score").totalAmount()).isEqualTo(scoreTotal + 135);
    }

    @Test
    void rejectedIngestRequestsAreCountedByReason() throws Exception {
        double validation = counter("miniwsa.ingest.rejected", "reason", "validation");
        double malformed = counter("miniwsa.ingest.rejected", "reason", "malformed");
        double tooLarge = counter("miniwsa.ingest.rejected", "reason", "too_large");

        ingest(Files.readString(Path.of("../examples/invalid-batch.json")), 400);
        ingest("{\"eventId\": ", 400);
        ingest("[" + String.join(",", Collections.nCopies(1001, "{}")) + "]", 413);
        // A bad query parameter on a read endpoint is not a rejected event.
        asReader().get().uri("/v1/stats/summary?from=nope").exchange().expectStatus().isBadRequest();

        assertThat(counter("miniwsa.ingest.rejected", "reason", "validation")).isEqualTo(validation + 1);
        assertThat(counter("miniwsa.ingest.rejected", "reason", "malformed")).isEqualTo(malformed + 1);
        assertThat(counter("miniwsa.ingest.rejected", "reason", "too_large")).isEqualTo(tooLarge + 1);
    }

    @Test
    void ingestStatsAndSamplesAreTimed() throws Exception {
        long ingests = timerCount("miniwsa.ingest.duration");
        long summaries = timerCount("miniwsa.stats.duration");
        long pages = timerCount("miniwsa.samples.duration");

        ingest(Files.readString(Path.of("../examples/single-event.json")), 201);
        asReader().get().uri("/v1/stats/summary").exchange().expectStatus().isOk();
        asReader().get().uri("/v1/events/samples").exchange().expectStatus().isOk();

        assertThat(timerCount("miniwsa.ingest.duration")).isEqualTo(ingests + 1);
        assertThat(timerCount("miniwsa.stats.duration")).isEqualTo(summaries + 1);
        assertThat(timerCount("miniwsa.samples.duration")).isEqualTo(pages + 1);
    }

    @Test
    void theAdminKeyCanReadTheCustomMetricsOverHttp() throws Exception {
        ingest(Files.readString(Path.of("../examples/invalid-batch.json")), 400);

        asAdmin().get().uri("/actuator/metrics/miniwsa.ingest.rejected").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("miniwsa.ingest.rejected")
                .jsonPath("$.availableTags[0].tag").isEqualTo("reason");
    }

    private void ingest(String body, int expectedStatus) {
        asIngester().post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }

    private double counter(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private DistributionSummary summary(String name) {
        return meterRegistry.get(name).summary();
    }

    private long timerCount(String name) {
        Timer timer = meterRegistry.find(name).timer();
        return timer == null ? 0 : timer.count();
    }
}
