package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import com.bensopher.miniwsa.ingest.IngestResponse;
import com.bensopher.miniwsa.ingest.IngestResponse.IngestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Enrichment through the ingest API: what is returned and what is stored. */
class EnrichmentIT extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-05-20T14:00:00Z");

    @Test
    void sampleEventIsStoredWithItsAttackTypeAndScore75() throws Exception {
        IngestResponse response = ingest(Files.readString(Path.of("../examples/single-event.json")));

        assertThat(response.events()).singleElement()
                .isEqualTo(new IngestedEvent("evt-00132", "SQL/Command Injection", 75, false));
        assertThat(jdbc.queryForMap("SELECT attack_type, threat_score FROM security_events"))
                .containsEntry("attack_type", "SQL/Command Injection").containsEntry("threat_score", 75);
    }

    @Test
    void sixthAndSeventhEventsFromOneIpWithinTenMinutesGetTheRepeatBonus() {
        IngestResponse response = ingest(batch(IntStream.range(0, 7).mapToObj(i -> event("e" + i, i)).toList()));

        // 40 CRITICAL + 20 DENY + 15 /login = 75; +15 once 5 other events are in the window (A6).
        assertThat(scores(response)).containsExactly(75, 75, 75, 75, 75, 90, 90);
        assertThat(jdbc.queryForList("SELECT threat_score FROM security_events ORDER BY event_time", Integer.class))
                .containsExactly(75, 75, 75, 75, 75, 90, 90);
    }

    @Test
    void eventsFromEarlierRequestsCount() {
        ingest(batch(IntStream.range(0, 5).mapToObj(i -> event("e" + i, i)).toList()));

        assertThat(scores(ingest(batch(List.of(event("e5", 5), event("e6", 6)))))).containsExactly(90, 90);
    }

    @Test
    void aBatchListedOutOfOrderIsScoredByEventTime() {
        List<String> events = new ArrayList<>(IntStream.range(0, 7).mapToObj(i -> event("e" + i, i)).toList());
        Collections.reverse(events);

        assertThat(scores(ingest(batch(events)))).containsExactly(90, 90, 75, 75, 75, 75, 75);
    }

    @Test
    void eventsMoreThanTenMinutesApartDontCount() {
        IngestResponse response = ingest(batch(IntStream.range(0, 7).mapToObj(i -> event("e" + i, i * 11)).toList()));

        assertThat(scores(response)).containsOnly(75);
    }

    @Test
    void retriesAndInRequestRepeatsDoNotInflateTheCount() {
        List<String> four = IntStream.range(0, 4).mapToObj(i -> event("e" + i, i)).toList();
        ingest(batch(four));
        ingest(batch(four)); // a full retry: all duplicates

        List<String> withRepeat = new ArrayList<>(four);
        withRepeat.add(event("e3", 3)); // repeated inside the request
        withRepeat.add(event("e4", 4));
        IngestResponse response = ingest(batch(withRepeat));

        // e4 has exactly 4 other events before it: below the threshold of 5, so no bonus.
        assertThat(response.events().getLast()).isEqualTo(new IngestedEvent("e4", "SQL/Command Injection", 75, false));
    }

    private IngestResponse ingest(String body) {
        return asIngester().post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(IngestResponse.class).returnResult().getResponseBody();
    }

    private static List<Integer> scores(IngestResponse response) {
        return response.events().stream().map(IngestedEvent::threatScore).toList();
    }

    private static String batch(List<String> events) {
        return "[" + String.join(",", events) + "]";
    }

    /** The sample event (CRITICAL, DENY, /api/v1/login, IP 203.0.113.42) at T0 + the given minutes. */
    private static String event(String eventId, int minutes) {
        return """
                {"eventId": "%s", "timestamp": "%s", "configId": 14227, "clientIp": "203.0.113.42",
                 "path": "/api/v1/login", "method": "POST",
                 "rule": {"id": "950001", "severity": "CRITICAL", "category": "INJECTION"}, "action": "DENY"}
                """.formatted(eventId, T0.plus(Duration.ofMinutes(minutes)));
    }
}
