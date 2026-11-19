package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IngestIT extends AbstractIntegrationTest {

    private static final String SAMPLE_EVENT = """
            {
              "eventId": "evt-00132",
              "timestamp": "2026-05-20T14:32:10Z",
              "configId": 14227,
              "policyId": "pol_web1",
              "clientIp": "203.0.113.42",
              "hostname": "www.example.com",
              "path": "/api/v1/login",
              "method": "POST",
              "statusCode": 403,
              "userAgent": "Mozilla/5.0",
              "rule": {
                "id": "950001",
                "name": "SQL_INJECTION",
                "message": "SQL Injection Attack Detected",
                "severity": "CRITICAL",
                "category": "INJECTION"
              },
              "action": "DENY",
              "geoLocation": { "country": "CN", "city": "Beijing" },
              "requestSize": 1024,
              "responseSize": 256
            }
            """;

    @Autowired
    JsonMapper mapper;

    @Test
    void singleEventIsStored() {
        ingest(SAMPLE_EVENT)
                .expectStatus().isCreated()
                .expectBody().json("""
                        {
                          "received": 1, "stored": 1, "duplicates": 0,
                          "receivedAt": "2026-11-18T10:15:30.123Z",
                          "events": [ { "eventId": "evt-00132", "attackType": "SQL/Command Injection",
                                        "threatScore": 75, "duplicate": false } ]
                        }
                        """, JsonCompareMode.STRICT);

        assertThat(jdbc.queryForMap(
                "SELECT event_id, host(client_ip) AS ip, severity, attack_type, threat_score FROM security_events"))
                .containsEntry("event_id", "evt-00132").containsEntry("ip", "203.0.113.42")
                .containsEntry("severity", "CRITICAL").containsEntry("attack_type", "SQL/Command Injection")
                .containsEntry("threat_score", 75);
    }

    @Test
    void batchIsStoredAndReportedInRequestOrder() {
        ingest(batch(event("c"), event("a"), event("b")))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.received").isEqualTo(3)
                .jsonPath("$.stored").isEqualTo(3)
                .jsonPath("$.events[*].eventId").isEqualTo(List.of("c", "a", "b"));

        assertThat(count()).isEqualTo(3);
    }

    @Test
    void invalidBatchIsRejectedWithIndexedErrorsAndNothingIsStored() {
        ObjectNode badSeverity = event("b");
        ((ObjectNode) badSeverity.get("rule")).put("severity", "SEVERE");
        ObjectNode badTimestamp = event("c").put("timestamp", "20/05/2026");

        ingest(batch(event("a"), badSeverity, badTimestamp))
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                        {
                          "type": "https://github.com/bensopher/mini-wsa/errors/validation",
                          "title": "Validation failed",
                          "status": 400,
                          "detail": "2 of 3 events failed validation; nothing was stored",
                          "instance": "/v1/events/ingest",
                          "errors": [
                            { "index": 1, "field": "rule.severity",
                              "message": "must be one of [CRITICAL, HIGH, MEDIUM, LOW]", "rejectedValue": "SEVERE" },
                            { "index": 2, "field": "timestamp",
                              "message": "must be an ISO-8601 timestamp with offset, e.g. 2026-05-20T14:32:10Z",
                              "rejectedValue": "20/05/2026" }
                          ]
                        }
                        """, JsonCompareMode.STRICT);

        assertThat(count()).isZero();
    }

    @Test
    void typeErrorsAreReportedWithTheirIndex() {
        ingest(batch(event("a"), event("b").put("configId", "abc")))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("1 of 2 events failed validation; nothing was stored")
                .jsonPath("$.errors").isEqualTo(List.of(Map.of(
                        "index", 1, "field", "configId", "message", "must be an integer", "rejectedValue", "abc")));
    }

    @Test
    void numbersAreStrictlyTyped() {
        ObjectNode floatForInteger = event("b").put("configId", 1.5);
        ObjectNode nestedType = event("c");
        nestedType.putArray("rule");

        ingest(batch(event("a").put("configId", "123"), floatForInteger, nestedType))
                .expectStatus().isBadRequest()
                .expectBody().json("""
                        {
                          "detail": "3 of 3 events failed validation; nothing was stored",
                          "errors": [
                            { "index": 0, "field": "configId", "message": "must be an integer", "rejectedValue": "123" },
                            { "index": 1, "field": "configId", "message": "must be an integer", "rejectedValue": 1.5 },
                            { "index": 2, "field": "rule", "message": "must be a JSON object" }
                          ]
                        }
                        """);

        assertThat(count()).isZero();
    }

    @Test
    void numbersTooLargeForTheirFieldAreValidationErrors() {
        ObjectNode hugeConfigId = event("a");
        hugeConfigId.put("configId", new BigInteger("99999999999999999999"));
        ObjectNode hugeStatus = event("b").put("statusCode", 3_000_000_000L);

        ingest(batch(hugeConfigId, hugeStatus))
                .expectStatus().isBadRequest()
                .expectBody().json("""
                        {
                          "detail": "2 of 2 events failed validation; nothing was stored",
                          "errors": [
                            { "index": 0, "field": "configId", "message": "is out of range",
                              "rejectedValue": 99999999999999999999 },
                            { "index": 1, "field": "statusCode", "message": "is out of range",
                              "rejectedValue": 3000000000 }
                          ]
                        }
                        """);

        assertThat(count()).isZero();
    }

    @Test
    void singleObjectErrorsHaveNoIndex() {
        ingest(event("a").put("clientIp", "999.1.1.1"))
                .expectStatus().isBadRequest()
                .expectBody().json("""
                        {
                          "detail": "The event failed validation; nothing was stored",
                          "errors": [ { "field": "clientIp", "message": "must be an IPv4 or IPv6 address",
                                        "rejectedValue": "999.1.1.1" } ]
                        }
                        """)
                .jsonPath("$.errors[0].index").doesNotExist();
    }

    @Test
    void nonObjectElementsAreRejected() {
        ArrayNode body = batch(event("a"));
        body.add(42);

        ingest(body).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors").isEqualTo(List.of(
                        Map.of("index", 1, "message", "must be a JSON object")));
    }

    @Test
    void emptyArrayAndScalarBodiesAreRejected() {
        ingest("[]").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail")
                .isEqualTo("The request body must be an event object or a non-empty array of events");
        ingest("42").expectStatus().isBadRequest();
        ingest("null").expectStatus().isBadRequest();
    }

    @Test
    void batchOverTheLimitIs413() {
        ArrayNode body = mapper.createArrayNode();
        IntStream.range(0, 1001).forEach(i -> body.add(event("e" + i)));

        ingest(body)
                .expectStatus().isEqualTo(413)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://github.com/bensopher/mini-wsa/errors/payload-too-large")
                .jsonPath("$.detail").isEqualTo("A batch may contain at most 1000 events; got 1001");
        assertThat(count()).isZero();
    }

    @Test
    void malformedJsonIs400WithoutEchoingTheInput() {
        String body = ingest("{\"eventId\": <script>")
                .expectStatus().isBadRequest()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("https://github.com/bensopher/mini-wsa/errors/malformed-json")
                .doesNotContain("script");
    }

    @Test
    void jsonNestedBeyondTheLimitIs400() {
        String deep = "[".repeat(25) + "]".repeat(25);

        ingest(deep).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.type").isEqualTo("https://github.com/bensopher/mini-wsa/errors/malformed-json");
    }

    @Test
    void resendingIsIdempotent() {
        ingest(batch(event("a"), event("b"))).expectStatus().isCreated();

        ingest(batch(event("a"), event("b")))
                .expectStatus().isCreated()
                .expectBody().json("""
                        {
                          "received": 2, "stored": 0, "duplicates": 2,
                          "events": [ { "eventId": "a", "duplicate": true }, { "eventId": "b", "duplicate": true } ]
                        }
                        """);
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void repeatsWithinOneRequestAreStoredOnce() {
        ingest(batch(event("a"), event("a")))
                .expectStatus().isCreated()
                .expectBody().json("""
                        {
                          "received": 2, "stored": 1, "duplicates": 1,
                          "events": [ { "eventId": "a", "duplicate": false }, { "eventId": "a", "duplicate": true } ]
                        }
                        """);
    }

    @Test
    void sameEventIdWithAnotherTimestampIsANewEvent() {
        ingest(event("a")).expectStatus().isCreated();

        ingest(event("a").put("timestamp", "2026-05-20T14:32:11Z"))
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.stored").isEqualTo(1);
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void timestampsMoreThanFiveMinutesAheadAreRejected() {
        String tooFar = NOW.plus(Duration.ofMinutes(5)).plusSeconds(1).toString();
        String withinSkew = NOW.plus(Duration.ofMinutes(5)).toString();

        ingest(event("a").put("timestamp", tooFar))
                .expectStatus().isBadRequest()
                .expectBody().json("""
                        { "errors": [ { "field": "timestamp",
                                        "message": "must not be more than 5 minutes ahead of the server clock" } ] }
                        """);
        ingest(event("b").put("timestamp", withinSkew)).expectStatus().isCreated();
    }

    @Test
    void timestampsAreNormalizedToUtcAndIpsToCanonicalForm() {
        ingest(event("a").put("timestamp", "2026-05-20T16:32:10+02:00").put("clientIp", "2001:DB8:0:0:0:0:0:1"))
                .expectStatus().isCreated();

        assertThat(jdbc.queryForMap("""
                SELECT event_time = '2026-05-20T14:32:10Z' AS utc_ok, host(client_ip) AS ip FROM security_events
                """)).containsEntry("utc_ok", true).containsEntry("ip", "2001:db8::1");
    }

    @Test
    void unknownFieldsAreIgnored() {
        ingest(event("a").put("someFutureField", "x")).expectStatus().isCreated();
    }

    @Test
    void nonJsonContentTypeIs415() {
        asIngester().post().uri("/v1/events/ingest")
                .contentType(MediaType.TEXT_PLAIN)
                .body(SAMPLE_EVENT)
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void onlyTheIngestRoleMayIngest() {
        post(anonymous(), SAMPLE_EVENT).expectStatus().isUnauthorized();
        post(asReader(), SAMPLE_EVENT).expectStatus().isForbidden();
        post(asAdmin(), SAMPLE_EVENT).expectStatus().isForbidden();
        assertThat(count()).isZero();
    }

    private ObjectNode event(String eventId) {
        return ((ObjectNode) mapper.readTree(SAMPLE_EVENT)).put("eventId", eventId);
    }

    private ArrayNode batch(JsonNode... events) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonNode event : events) {
            array.add(event);
        }
        return array;
    }

    private ResponseSpec ingest(JsonNode body) {
        return ingest(mapper.writeValueAsString(body));
    }

    private ResponseSpec ingest(String body) {
        return post(asIngester(), body);
    }

    private static ResponseSpec post(RestTestClient client, String body) {
        return client.post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM security_events", Integer.class);
    }
}
