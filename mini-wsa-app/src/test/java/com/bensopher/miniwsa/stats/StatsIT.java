package com.bensopher.miniwsa.stats;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsIT extends AbstractIntegrationTest {

    private static final String MAY_20 = "from=2026-05-20T00:00:00Z&to=2026-05-21T00:00:00Z";

    @Test
    void summaryForOneConfigHasExactCountsAveragesAndRankings() {
        seedFixture();

        asReader().get().uri("/v1/stats/summary?configId=14227&" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "configId": 14227,
                          "timeRange": { "from": "2026-05-20T00:00:00Z", "to": "2026-05-21T00:00:00Z" },
                          "totalEvents": 6,
                          "byCategory": {
                            "INJECTION": { "count": 3, "avgThreatScore": 68.3 },
                            "XSS":       { "count": 2, "avgThreatScore": 42.5 },
                            "BOT":       { "count": 1, "avgThreatScore": 20.0 }
                          },
                          "byAction": { "DENY": 3, "ALERT": 2, "MONITOR": 1 },
                          "topAttackers": [
                            { "clientIp": "203.0.113.42", "count": 3, "avgThreatScore": 70.0 },
                            { "clientIp": "198.51.100.7", "count": 2, "avgThreatScore": 40.0 },
                            { "clientIp": "192.0.2.1",    "count": 1, "avgThreatScore": 20.0 }
                          ],
                          "topTargetedPaths": [
                            { "path": "/api/v1/login", "count": 3 },
                            { "path": "/search",       "count": 2 },
                            { "path": "/home",         "count": 1 }
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void withoutConfigIdEveryConfigIsAggregatedAndConfigIdIsNull() {
        seedFixture();

        // 53.75 rounds half away from zero to 53.8.
        asReader().get().uri("/v1/stats/summary?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "configId": null,
                          "timeRange": { "from": "2026-05-20T00:00:00Z", "to": "2026-05-21T00:00:00Z" },
                          "totalEvents": 7,
                          "byCategory": {
                            "INJECTION": { "count": 4, "avgThreatScore": 53.8 },
                            "XSS":       { "count": 2, "avgThreatScore": 42.5 },
                            "BOT":       { "count": 1, "avgThreatScore": 20.0 }
                          },
                          "byAction": { "DENY": 4, "ALERT": 2, "MONITOR": 1 },
                          "topAttackers": [
                            { "clientIp": "203.0.113.42", "count": 4, "avgThreatScore": 55.0 },
                            { "clientIp": "198.51.100.7", "count": 2, "avgThreatScore": 40.0 },
                            { "clientIp": "192.0.2.1",    "count": 1, "avgThreatScore": 20.0 }
                          ],
                          "topTargetedPaths": [
                            { "path": "/api/v1/login", "count": 4 },
                            { "path": "/search",       "count": 2 },
                            { "path": "/home",         "count": 1 }
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void byCategoryIsOrderedByCountThenName() {
        seedFixture();

        String body = asReader().get().uri("/v1/stats/summary?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).containsSubsequence("\"INJECTION\"", "\"XSS\"", "\"BOT\"");
    }

    @Test
    void defaultRangeIsTheLast24HoursHalfOpen() {
        Instant dayAgo = NOW.minus(1, ChronoUnit.DAYS);
        insert("at-start", dayAgo, 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        insert("before-start", dayAgo.minus(1, ChronoUnit.MICROS), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        insert("just-before-now", NOW.minus(1, ChronoUnit.MICROS), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        insert("at-now", NOW, 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);

        asReader().get().uri("/v1/stats/summary").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timeRange.from").isEqualTo("2026-11-17T10:15:30.123Z")
                .jsonPath("$.timeRange.to").isEqualTo("2026-11-18T10:15:30.123Z")
                .jsonPath("$.totalEvents").isEqualTo(2);
    }

    @Test
    void anEmptyRangeListsEveryActionWithZero() {
        asReader().get().uri("/v1/stats/summary?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "configId": null,
                          "timeRange": { "from": "2026-05-20T00:00:00Z", "to": "2026-05-21T00:00:00Z" },
                          "totalEvents": 0,
                          "byCategory": {},
                          "byAction": { "DENY": 0, "ALERT": 0, "MONITOR": 0 },
                          "topAttackers": [],
                          "topTargetedPaths": []
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void topListsStopAtTenAndBreakTiesByAddressAndPath() {
        Instant t = Instant.parse("2026-05-20T12:00:00Z");
        for (int i = 1; i <= 12; i++) {
            insert("e" + i, t.plusSeconds(i), 1, "10.0.0." + i, "/p%02d".formatted(i), "BOT", "MONITOR", 10);
        }
        insert("e12-again", t, 1, "10.0.0.12", "/p12", "BOT", "MONITOR", 10);

        // Sorted as text, 10.0.0.10 and 10.0.0.11 would come before 10.0.0.2; as addresses they come after 10.0.0.9.
        asReader().get().uri("/v1/stats/summary?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.topAttackers[*].clientIp").isEqualTo(List.of("10.0.0.12",
                        "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5", "10.0.0.6", "10.0.0.7",
                        "10.0.0.8", "10.0.0.9"))
                .jsonPath("$.topTargetedPaths[*].path").isEqualTo(List.of("/p12",
                        "/p01", "/p02", "/p03", "/p04", "/p05", "/p06", "/p07", "/p08", "/p09"));
    }

    @Test
    void eventsWithoutAScoreCountButDontAffectTheAverage() {
        Instant t = Instant.parse("2026-05-20T12:00:00Z");
        insert("scored", t, 1, "192.0.2.1", "/", "INJECTION", "DENY", 80);
        insert("unscored", t.plusSeconds(1), 1, "192.0.2.1", "/", "INJECTION", "DENY", null);

        asReader().get().uri("/v1/stats/summary?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.byCategory.INJECTION.count").isEqualTo(2)
                .jsonPath("$.byCategory.INJECTION.avgThreatScore").isEqualTo(80.0);
    }

    @Test
    void theIngestedSampleEventIsCounted() throws Exception {
        asIngester().post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Files.readString(Path.of("../examples/single-event.json")))
                .exchange()
                .expectStatus().isCreated();

        asReader().get().uri("/v1/stats/summary?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEvents").isEqualTo(1)
                .jsonPath("$.byCategory.INJECTION.avgThreatScore").isEqualTo(75.0)
                .jsonPath("$.topAttackers[0].clientIp").isEqualTo("203.0.113.42");
    }

    @Test
    void fromMustBeBeforeTo() {
        asReader().get().uri("/v1/stats/summary?from=2026-05-21T00:00:00Z&to=2026-05-20T00:00:00Z").exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "https://github.com/bensopher/mini-wsa/errors/validation",
                          "title": "Validation failed",
                          "status": 400,
                          "detail": "Invalid query parameters",
                          "instance": "/v1/stats/summary",
                          "errors": [
                            { "field": "from", "message": "must be before 'to'", "rejectedValue": "2026-05-21T00:00:00Z" }
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void invalidParametersAreReportedByName() {
        asReader().get().uri("/v1/stats/summary?from=yesterday").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("from")
                .jsonPath("$.errors[0].message")
                .isEqualTo("must be an ISO-8601 timestamp with offset, e.g. 2026-05-20T14:32:10Z");

        asReader().get().uri("/v1/stats/summary?configId=abc").exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://github.com/bensopher/mini-wsa/errors/validation")
                .jsonPath("$.errors[0].field").isEqualTo("configId")
                .jsonPath("$.errors[0].message").isEqualTo("must be an integer")
                .jsonPath("$.errors[0].rejectedValue").isEqualTo("abc")
                .jsonPath("$.errors[0].index").doesNotExist();
    }

    @Test
    void rangesOver90DaysAreRejected() {
        asReader().get().uri("/v1/stats/summary?from=2026-01-01T00:00:00Z&to=2026-06-01T00:00:00Z").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("from")
                .jsonPath("$.errors[0].message").isEqualTo("must be at most 90 days before 'to'; narrow the time range");
    }

    @Test
    void onlyTheReadRoleMayReadStats() {
        anonymous().get().uri("/v1/stats/summary").exchange().expectStatus().isUnauthorized();
        asIngester().get().uri("/v1/stats/summary").exchange().expectStatus().isForbidden();
        asReader().get().uri("/v1/stats/summary").exchange().expectStatus().isOk();
        asAdmin().get().uri("/v1/stats/summary").exchange().expectStatus().isOk();
    }

    /**
     * Config 14227: INJECTION 75, 90, 40; XSS 40, 45; BOT 20. Config 99: INJECTION 10. Two more events fall
     * just outside the range [May 20, May 21), one on each side.
     */
    private void seedFixture() {
        insert("e1", "2026-05-20T00:00:00Z", 14227, "203.0.113.42", "/api/v1/login", "INJECTION", "DENY", 75);
        insert("e2", "2026-05-20T01:00:00Z", 14227, "203.0.113.42", "/api/v1/login", "INJECTION", "DENY", 90);
        insert("e3", "2026-05-20T02:00:00Z", 14227, "198.51.100.7", "/search", "INJECTION", "ALERT", 40);
        insert("e4", "2026-05-20T03:00:00Z", 14227, "198.51.100.7", "/search", "XSS", "ALERT", 40);
        insert("e5", "2026-05-20T04:00:00Z", 14227, "203.0.113.42", "/api/v1/login", "XSS", "DENY", 45);
        insert("e6", "2026-05-20T23:59:59.999999Z", 14227, "192.0.2.1", "/home", "BOT", "MONITOR", 20);
        insert("e7", "2026-05-20T12:00:00Z", 99, "203.0.113.42", "/api/v1/login", "INJECTION", "DENY", 10);
        insert("at-to", "2026-05-21T00:00:00Z", 14227, "203.0.113.42", "/api/v1/login", "INJECTION", "DENY", 75);
        insert("before-from", "2026-05-19T23:59:59.999999Z", 14227, "192.0.2.1", "/home", "BOT", "MONITOR", 20);
    }

    private void insert(String eventId, String time, long configId, String ip, String path, String category,
            String action, Integer score) {
        insert(eventId, Instant.parse(time), configId, ip, path, category, action, score);
    }

    /** Rows written directly, so the fixture's scores are exactly what the test says, independent of scoring. */
    private void insert(String eventId, Instant time, long configId, String ip, String path, String category,
            String action, Integer score) {
        jdbc.update("""
                INSERT INTO security_events (event_id, event_time, received_at, config_id, client_ip, path, method,
                    rule_id, severity, category, action, attack_type, threat_score)
                VALUES (?, CAST(? AS timestamptz), now(), ?, CAST(? AS inet), ?, 'GET', '1', 'LOW', ?, ?, 'test', ?)
                """, eventId, time.toString(), configId, ip, path, category, action, score);
    }
}
