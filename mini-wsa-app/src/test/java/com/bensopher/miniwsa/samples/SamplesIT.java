package com.bensopher.miniwsa.samples;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

class SamplesIT extends AbstractIntegrationTest {

    private static final String MAY_20 = "from=2026-05-20T00:00:00Z&to=2026-05-21T00:00:00Z";

    @Test
    void anIngestedEventIsReturnedWithEveryFieldAndItsEnrichment() throws Exception {
        asIngester().post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Files.readString(Path.of("../examples/single-event.json")))
                .exchange()
                .expectStatus().isCreated();

        asReader().get().uri("/v1/events/samples?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "total": 1, "limit": 20, "offset": 0,
                          "items": [
                            {
                              "eventId": "evt-00132", "timestamp": "2026-05-20T14:32:10Z", "configId": 14227,
                              "policyId": "pol_web1", "clientIp": "203.0.113.42", "hostname": "www.example.com",
                              "path": "/api/v1/login", "method": "POST", "statusCode": 403,
                              "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                              "rule": { "id": "950001", "name": "SQL_INJECTION", "message": "SQL Injection Attack Detected",
                                        "severity": "CRITICAL", "category": "INJECTION" },
                              "action": "DENY", "geoLocation": { "country": "CN", "city": "Beijing" },
                              "requestSize": 1024, "responseSize": 256,
                              "attackType": "SQL/Command Injection", "threatScore": 75,
                              "receivedAt": "2026-11-18T10:15:30.123Z"
                            }
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void missingOptionalFieldsAreOmittedRatherThanNull() {
        insertEvent("bare", "2026-05-20T12:00:00Z", 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);

        asReader().get().uri("/v1/events/samples?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].eventId").isEqualTo("bare")
                .jsonPath("$.items[0].policyId").doesNotExist()
                .jsonPath("$.items[0].hostname").doesNotExist()
                .jsonPath("$.items[0].statusCode").doesNotExist()
                .jsonPath("$.items[0].userAgent").doesNotExist()
                .jsonPath("$.items[0].geoLocation").doesNotExist()
                .jsonPath("$.items[0].requestSize").doesNotExist()
                .jsonPath("$.items[0].rule.name").doesNotExist()
                .jsonPath("$.items[0].rule.severity").isEqualTo("LOW");
    }

    @Test
    void newestFirstWithEventIdBreakingTiesAndTotalCountingEveryMatch() {
        Instant t = Instant.parse("2026-05-20T12:00:00Z");
        insertEvent("oldest", t, 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        insertEvent("middle", t.plusSeconds(60), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        insertEvent("tie-a", t.plusSeconds(120), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        insertEvent("tie-b", t.plusSeconds(120), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        insertEvent("newest", t.plusSeconds(180), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);

        asReader().get().uri("/v1/events/samples?" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[*].eventId").isEqualTo(List.of("newest", "tie-b", "tie-a", "middle", "oldest"));

        asReader().get().uri("/v1/events/samples?limit=2&offset=1&" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(5)
                .jsonPath("$.limit").isEqualTo(2)
                .jsonPath("$.offset").isEqualTo(1)
                .jsonPath("$.items[*].eventId").isEqualTo(List.of("tie-b", "tie-a"));
    }

    @Test
    void filtersCombine() {
        Instant t = Instant.parse("2026-05-20T12:00:00Z");
        insertEvent("match", t, 14227, "192.0.2.1", "/", "INJECTION", "DENY", 70);
        insertEvent("other-config", t, 99, "192.0.2.1", "/", "INJECTION", "DENY", 70);
        insertEvent("other-category", t, 14227, "192.0.2.1", "/", "XSS", "DENY", 70);
        insertEvent("other-action", t, 14227, "192.0.2.1", "/", "INJECTION", "ALERT", 70);
        insertEvent("out-of-range", t.minus(2, ChronoUnit.DAYS), 14227, "192.0.2.1", "/", "INJECTION", "DENY", 70);

        asReader().get().uri("/v1/events/samples?configId=14227&category=INJECTION&action=DENY&" + MAY_20).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[*].eventId").isEqualTo(List.of("match"));
    }

    @Test
    void defaultsAreTheLast24HoursAndTwentyItems() {
        for (int i = 0; i < 25; i++) {
            insertEvent("recent-" + i, NOW.minus(i + 1, ChronoUnit.MINUTES), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);
        }
        insertEvent("too-old", NOW.minus(25, ChronoUnit.HOURS), 1, "192.0.2.1", "/", "BOT", "MONITOR", 10);

        asReader().get().uri("/v1/events/samples").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(25)
                .jsonPath("$.limit").isEqualTo(20)
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.items.length()").isEqualTo(20)
                .jsonPath("$.items[0].eventId").isEqualTo("recent-0");
    }

    @Test
    void outOfRangePagingIsRejectedNotClampedAndAllErrorsAreReportedTogether() {
        asReader().get().uri("/v1/events/samples?limit=101").exchange()
                .expectStatus().isBadRequest()
                .expectBody().json("""
                        {
                          "type": "https://github.com/bensopher/mini-wsa/errors/validation",
                          "title": "Validation failed",
                          "status": 400,
                          "detail": "Invalid query parameters",
                          "instance": "/v1/events/samples",
                          "errors": [ { "field": "limit", "message": "must be between 1 and 100", "rejectedValue": 101 } ]
                        }
                        """, JsonCompareMode.STRICT);

        asReader().get().uri("/v1/events/samples?limit=0").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("limit");

        asReader().get().uri("/v1/events/samples?from=nope&limit=101&offset=10001").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[*].field").isEqualTo(List.of("from", "limit", "offset"))
                .jsonPath("$.errors[2].message").isEqualTo(
                        "must be between 0 and 10000; narrow the time range or add filters to reach older events");

        asReader().get().uri("/v1/events/samples?offset=-1").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("offset");
    }

    @Test
    void unknownEnumValuesAreRejectedWithTheAllowedValues() {
        asReader().get().uri("/v1/events/samples?category=SQLI").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("category")
                .jsonPath("$.errors[0].message")
                .isEqualTo("must be one of [INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT]")
                .jsonPath("$.errors[0].rejectedValue").isEqualTo("SQLI");

        // Case-sensitive, like the ingest payload.
        asReader().get().uri("/v1/events/samples?action=deny").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("action")
                .jsonPath("$.errors[0].message").isEqualTo("must be one of [DENY, ALERT, MONITOR]");

        asReader().get().uri("/v1/events/samples?limit=ten").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("limit")
                .jsonPath("$.errors[0].message").isEqualTo("must be an integer");
    }

    @Test
    void onlyTheReadRoleMayReadSamples() {
        anonymous().get().uri("/v1/events/samples").exchange().expectStatus().isUnauthorized();
        asIngester().get().uri("/v1/events/samples").exchange().expectStatus().isForbidden();
        asReader().get().uri("/v1/events/samples").exchange().expectStatus().isOk();
        asAdmin().get().uri("/v1/events/samples").exchange().expectStatus().isOk();
    }
}
