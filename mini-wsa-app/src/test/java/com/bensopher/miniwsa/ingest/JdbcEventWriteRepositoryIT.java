package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import com.bensopher.miniwsa.event.EnrichedEvent;
import com.bensopher.miniwsa.event.SecurityEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static com.bensopher.miniwsa.ingest.TestEvents.minimalEvent;
import static com.bensopher.miniwsa.ingest.TestEvents.sampleEvent;
import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventWriteRepositoryIT extends AbstractIntegrationTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-05-20T14:32:10Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-11-18T10:15:30.123Z");

    @Autowired
    JdbcEventWriteRepository repository;

    @Test
    void storesEveryField() {
        insert(List.of(sampleEvent("evt-00132", EVENT_TIME)));

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT event_id, event_time = '2026-05-20T14:32:10Z' AS time_ok,
                       received_at = '2026-11-18T10:15:30.123Z' AS received_ok, config_id, policy_id,
                       host(client_ip) AS client_ip, hostname, path, method, status_code, user_agent, rule_id,
                       rule_name, rule_message, severity, category, action, geo_country, geo_city,
                       request_size, response_size, attack_type, threat_score
                FROM security_events
                """);
        assertThat(row).containsAllEntriesOf(Map.ofEntries(
                Map.entry("event_id", "evt-00132"), Map.entry("time_ok", true), Map.entry("received_ok", true),
                Map.entry("config_id", 14227L), Map.entry("policy_id", "pol_web1"),
                Map.entry("client_ip", "203.0.113.42"), Map.entry("hostname", "www.example.com"),
                Map.entry("path", "/api/v1/login"), Map.entry("method", "POST"), Map.entry("status_code", 403),
                Map.entry("user_agent", "Mozilla/5.0"), Map.entry("rule_id", "950001"),
                Map.entry("rule_name", "SQL_INJECTION"), Map.entry("rule_message", "SQL Injection Attack Detected"),
                Map.entry("severity", "CRITICAL"), Map.entry("category", "INJECTION"), Map.entry("action", "DENY"),
                Map.entry("geo_country", "CN"), Map.entry("geo_city", "Beijing"),
                Map.entry("request_size", 1024L), Map.entry("response_size", 256L),
                Map.entry("attack_type", "SQL/Command Injection"), Map.entry("threat_score", 75)));
    }

    @Test
    void storesMissingOptionalFieldsAsNullAndIpv6InCanonicalForm() {
        insert(List.of(minimalEvent("e1", EVENT_TIME, "2001:db8::1")));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT host(client_ip) AS ip, policy_id, status_code, geo_country, request_size FROM security_events");
        assertThat(row).containsEntry("ip", "2001:db8::1")
                .containsEntry("policy_id", null).containsEntry("status_code", null)
                .containsEntry("geo_country", null).containsEntry("request_size", null);
    }

    @Test
    void returnsOnlyTheKeysItInserted() {
        SecurityEvent first = sampleEvent("e1", EVENT_TIME);
        SecurityEvent second = sampleEvent("e2", EVENT_TIME);
        insert(List.of(first));

        var inserted = insert(List.of(first, second));

        assertThat(inserted).containsExactly(EventKey.of(second));
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void sameEventIdAtAnotherTimeIsAnotherEvent() {
        SecurityEvent original = sampleEvent("e1", EVENT_TIME);
        SecurityEvent later = sampleEvent("e1", EVENT_TIME.plusSeconds(1));

        var inserted = insert(List.of(original, later));

        assertThat(inserted).containsExactlyInAnyOrder(EventKey.of(original), EventKey.of(later));
    }

    @Test
    void insertsAcrossSeveralChunks() {
        List<SecurityEvent> events = IntStream.range(0, JdbcEventWriteRepository.CHUNK_SIZE * 2 + 1)
                .mapToObj(i -> sampleEvent("e" + i, EVENT_TIME.plusMillis(i)))
                .toList();

        var inserted = insert(events);

        assertThat(inserted).hasSize(1001);
        assertThat(count()).isEqualTo(1001);
    }

    /** Inserts the events as enriched by a fixed classifier and score; enrichment itself is tested elsewhere. */
    private Set<EventKey> insert(List<SecurityEvent> events) {
        return repository.insert(events.stream()
                .map(e -> new EnrichedEvent(e, "SQL/Command Injection", 75, RECEIVED_AT))
                .toList());
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM security_events", Integer.class);
    }
}
