package com.bensopher.miniwsa.db;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.BodyContentSpec;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Old chunks get compressed; ingestion, duplicate detection and stats must keep working on them. */
class CompressionIT extends AbstractIntegrationTest {

    @Test
    void chunksOlderThanSevenDaysAreCompressedGroupedByConfig() {
        String compressAfter = jdbc.queryForObject("""
                SELECT config ->> 'compress_after' FROM timescaledb_information.jobs
                WHERE hypertable_name = 'security_events' AND proc_name = 'policy_compression'
                """, String.class);
        assertThat(compressAfter).isEqualTo("7 days");

        assertThat(jdbc.queryForMap("""
                SELECT segmentby, orderby FROM timescaledb_information.hypertable_compression_settings
                WHERE hypertable = 'security_events'::regclass
                """)).containsEntry("segmentby", "config_id").containsEntry("orderby", "event_time DESC,event_id");
    }

    @Test
    void duplicatesAreStillDetectedAndStatsStillWorkAfterTheChunkIsCompressed() throws Exception {
        String sample = Files.readString(Path.of("../examples/single-event.json"));
        ingest(sample).jsonPath("$.stored").isEqualTo(1);

        Integer compressed = jdbc.queryForObject(
                "SELECT count(compress_chunk(c)) FROM show_chunks('security_events') c", Integer.class);
        assertThat(compressed).isEqualTo(1);

        // A retry of the same event lands in a compressed chunk: still recognized as a duplicate.
        ingest(sample).jsonPath("$.stored").isEqualTo(0).jsonPath("$.events[0].duplicate").isEqualTo(true);
        // A new event for the same day is stored next to the compressed data.
        ingest(sample.replace("evt-00132", "evt-00133")).jsonPath("$.stored").isEqualTo(1);

        asReader().get().uri("/v1/stats/summary?from=2026-05-20T00:00:00Z&to=2026-05-21T00:00:00Z").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEvents").isEqualTo(2)
                .jsonPath("$.byCategory.INJECTION.avgThreatScore").isEqualTo(75.0);
    }

    private BodyContentSpec ingest(String body) {
        return asIngester().post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody();
    }
}
