package com.bensopher.miniwsa.generator;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AttackWavesTest {

    private static final Instant TO = EventGeneratorTest.NOW;
    private static final Instant FROM = TO.minus(Duration.ofHours(1));

    @RepeatedTest(20)
    void aSqliBurstIsOneIpHittingOnePathAtLeast50TimesWithinThreeMinutes(RepetitionInfo repetition) {
        List<GeneratedEvent> burst = sorted(waves(repetition.getCurrentRepetition()).sqliBurst(FROM, TO));

        assertThat(burst).hasSizeBetween(50, 200);
        assertThat(burst).extracting(GeneratedEvent::clientIp).containsOnly(burst.getFirst().clientIp());
        assertThat(burst).extracting(GeneratedEvent::path).containsOnly(burst.getFirst().path());
        assertThat(burst.getFirst().path()).isIn("/api/v1/search", "/api/v1/login");
        assertThat(span(burst)).isLessThanOrEqualTo(Duration.ofMinutes(3));
        assertThat(burst).allSatisfy(e -> {
            assertThat(e.rule().category()).isEqualTo("INJECTION");
            assertThat(e.userAgent()).isEqualTo(Catalog.SQLMAP);
        });
    }

    @RepeatedTest(20)
    void credentialStuffingIsABotnetTryingTheLoginEndpoint(RepetitionInfo repetition) {
        List<GeneratedEvent> wave = sorted(waves(repetition.getCurrentRepetition()).credentialStuffing(FROM, TO));
        Map<String, Long> attemptsPerIp = wave.stream()
                .collect(Collectors.groupingBy(GeneratedEvent::clientIp, Collectors.counting()));

        assertThat(attemptsPerIp).hasSizeBetween(20, 50);
        assertThat(attemptsPerIp.values()).allSatisfy(n -> assertThat(n).isBetween(5L, 20L));
        assertThat(wave).extracting(GeneratedEvent::path).containsOnly("/api/v1/login");
        assertThat(wave).extracting(e -> e.rule().category()).containsOnly("BOT", "RATE_LIMIT");
        assertThat(span(wave)).isLessThanOrEqualTo(Duration.ofMinutes(10));
    }

    @RepeatedTest(20)
    void aSlowDosIsAFewClientsAtASteadyRateForAtLeast15Minutes(RepetitionInfo repetition) {
        List<GeneratedEvent> wave = sorted(waves(repetition.getCurrentRepetition()).slowDos(FROM, TO));
        Map<String, List<GeneratedEvent>> byIp = wave.stream().collect(Collectors.groupingBy(GeneratedEvent::clientIp));

        assertThat(byIp).hasSizeBetween(3, 5);
        assertThat(wave).extracting(e -> e.rule().category()).containsOnly("DOS");
        // Steady: each client is active across most of the wave, not in one burst.
        assertThat(byIp.values()).allSatisfy(events -> assertThat(span(events)).isGreaterThan(Duration.ofMinutes(12)));
        assertThat(span(wave)).isBetween(Duration.ofMinutes(13), Duration.ofMinutes(30));
    }

    @Test
    void wavesFillExactlyTheBudgetInsideTheRange() {
        AttackWaves.Result result = waves(1).generate(1234, FROM, TO);

        assertThat(result.events()).hasSize(1234)
                .allSatisfy(e -> assertThat(e.timestamp()).isBetween(FROM, TO))
                .allSatisfy(e -> assertThat(IngestRules.isDocumentationAddress(e.clientIp())).isTrue());
        assertThat(result.waves().stream().mapToInt(AttackWaves.Wave::events).sum()).isEqualTo(1234);
    }

    @Test
    void aWaveLongerThanTheRangeIsSqueezedIntoIt() {
        Instant shortFrom = TO.minus(Duration.ofMinutes(5));

        assertThat(waves(1).slowDos(shortFrom, TO)).allSatisfy(e -> assertThat(e.timestamp()).isBetween(shortFrom, TO));
    }

    /** Each repetition uses a different seed, so the properties are checked on 20 different waves. */
    private static AttackWaves waves(long seed) {
        Random random = new Random(seed);
        return new AttackWaves(random, new EventFactory(random, EventGeneratorTest.CONFIGS));
    }

    private static List<GeneratedEvent> sorted(List<GeneratedEvent> events) {
        return events.stream().sorted(Comparator.comparing(GeneratedEvent::timestamp)).toList();
    }

    private static Duration span(List<GeneratedEvent> events) {
        List<GeneratedEvent> inOrder = sorted(events);
        return Duration.between(inOrder.getFirst().timestamp(), inOrder.getLast().timestamp());
    }
}
