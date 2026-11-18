package com.bensopher.miniwsa.generator;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EventGeneratorTest {

    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final List<Long> CONFIGS = List.of(14227L, 14228L, 20001L);

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void theSameSeedGivesExactlyTheSameEvents() {
        String first = json.writeValueAsString(generator(42).generate(2000, "run1"));
        String second = json.writeValueAsString(generator(42).generate(2000, "run1"));

        assertThat(first).isEqualTo(second);
        assertThat(json.writeValueAsString(generator(43).generate(2000, "run1"))).isNotEqualTo(first);
    }

    @Test
    void everyEventPassesTheIngestRules() {
        assertThat(generator(7).generate(5000, "run1"))
                .allSatisfy(event -> assertThat(IngestRules.violations(event)).as(event.toString()).isEmpty());
    }

    @Test
    void eventsAreChronologicalWithinTheSpanAndNumberedInOrder() {
        List<GeneratedEvent> events = generator(7).generate(1000, "ab12cd34");

        assertThat(events).hasSize(1000).isSortedAccordingTo(Comparator.comparing(GeneratedEvent::timestamp));
        assertThat(events).allSatisfy(e -> assertThat(e.timestamp()).isBetween(NOW.minus(Duration.ofHours(1)), NOW));
        assertThat(events.getFirst().eventId()).isEqualTo("evt-ab12cd34-000001");
        assertThat(events.getLast().eventId()).isEqualTo("evt-ab12cd34-001000");
        assertThat(events).extracting(GeneratedEvent::configId).containsOnly(14227L, 14228L, 20001L);
    }

    @Test
    void backgroundTrafficLooksLikeAnEdgeFeed() {
        List<GeneratedEvent> events = generator(7).generate(10_000, "run1");
        var byCategory = events.stream().collect(Collectors.groupingBy(e -> e.rule().category(), Collectors.counting()));
        var byClient = events.stream().collect(Collectors.groupingBy(GeneratedEvent::clientIp, Collectors.counting()));

        // Weighted toward bots and malformed requests.
        assertThat(byCategory.get("BOT")).isGreaterThan(byCategory.get("INJECTION"));
        assertThat(byCategory.get("PROTOCOL_VIOLATION")).isGreaterThan(byCategory.get("DATA_LEAKAGE"));
        // A few hundred clients, a few of them very busy.
        assertThat(byClient).hasSizeBetween(200, BackgroundTraffic.CLIENTS);
        assertThat(byClient.values().stream().max(Long::compare).orElseThrow()).isGreaterThan(300);
        assertThat(events).anyMatch(e -> e.clientIp().startsWith("2001:db8:"));
        // Critical events are mostly blocked.
        var criticalActions = events.stream().filter(e -> e.rule().severity().equals("CRITICAL"))
                .collect(Collectors.groupingBy(GeneratedEvent::action, Collectors.counting()));
        assertThat(criticalActions.get("DENY")).isGreaterThan(criticalActions.getOrDefault("ALERT", 0L) * 5);
    }

    @Test
    void anAddressAlwaysComesFromTheSamePlace() {
        var placesByClient = generator(7).generate(5000, "run1").stream()
                .collect(Collectors.groupingBy(GeneratedEvent::clientIp,
                        Collectors.mapping(e -> String.valueOf(e.geoLocation()), Collectors.toSet())));

        assertThat(placesByClient.values()).allSatisfy(places -> assertThat(places).hasSize(1));
    }

    @Test
    void timestampsSerializeAsIsoStrings() {
        String first = json.writeValueAsString(generator(1).generate(1, "run1").getFirst());

        assertThat(first).containsPattern("\"timestamp\":\"2026-11-18T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z\"");
    }

    static EventGenerator generator(long seed) {
        return new EventGenerator(seed, CLOCK, Duration.ofHours(1), CONFIGS);
    }
}
