package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import com.bensopher.miniwsa.event.EnrichedEvent;
import com.bensopher.miniwsa.event.Rule;
import com.bensopher.miniwsa.event.SecurityEvent;
import com.bensopher.miniwsa.event.Severity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class EnrichmentServiceTest {

    private static final Instant T0 = Instant.parse("2026-05-20T14:00:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-11-18T10:15:30.123Z");

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    /** Score = 10 x prior events, so each event's score shows which prior count it received. */
    private final ThreatScoreCalculator calculator =
            new ThreatScoreCalculator(List.of(context -> 10 * context.priorEventsFromIpInWindow()));

    @Test
    void tracksInEventTimeOrderAndReturnsInRequestOrder() {
        List<List<String>> seenByTracker = new ArrayList<>();
        RepeatOffenderTracker tracker = sorted -> {
            seenByTracker.add(sorted.stream().map(SecurityEvent::eventId).toList());
            return IntStream.range(0, sorted.size()).boxed().toList(); // 0, 1, 2 ... in time order
        };
        List<SecurityEvent> request = List.of(event("late", 2), event("early", 0), event("middle", 1));

        List<EnrichedEvent> enriched = service(tracker).enrich(request, RECEIVED_AT);

        assertThat(seenByTracker).containsExactly(List.of("early", "middle", "late"));
        assertThat(enriched).extracting(e -> e.event().eventId()).containsExactly("late", "early", "middle");
        assertThat(enriched).extracting(EnrichedEvent::threatScore).containsExactly(20, 0, 10);
    }

    @Test
    void eventsWithTheSameTimestampKeepRequestOrder() {
        List<List<String>> seenByTracker = new ArrayList<>();
        RepeatOffenderTracker tracker = sorted -> {
            seenByTracker.add(sorted.stream().map(SecurityEvent::eventId).toList());
            return IntStream.range(0, sorted.size()).boxed().toList();
        };

        service(tracker).enrich(List.of(event("b", 0), event("a", 0), event("c", 0)), RECEIVED_AT);

        assertThat(seenByTracker).containsExactly(List.of("b", "a", "c"));
    }

    @Test
    void addsAttackTypeAndReceivedAt() {
        List<EnrichedEvent> enriched = service(sorted -> List.of(0)).enrich(List.of(event("e1", 0)), RECEIVED_AT);

        assertThat(enriched).singleElement().satisfies(e -> {
            assertThat(e.attackType()).isEqualTo("Bot Activity");
            assertThat(e.receivedAt()).isEqualTo(RECEIVED_AT);
        });
    }

    @Test
    void failsOpenWhenTheTrackerIsUnavailable() {
        RepeatOffenderTracker down = sorted -> {
            throw new RedisConnectionFailureException("connection refused");
        };

        List<EnrichedEvent> enriched = service(down).enrich(List.of(event("a", 0), event("b", 1)), RECEIVED_AT);

        assertThat(enriched).extracting(EnrichedEvent::threatScore).containsExactly(0, 0);
        assertThat(meterRegistry.counter("miniwsa.enrichment.repeat_offender.unavailable").count()).isEqualTo(1);
    }

    @Test
    void anOutageIsLoggedOnceWhenItStartsAndOnceWhenItEndsButCountedOnEveryRequest(CapturedOutput output) {
        AtomicBoolean redisUp = new AtomicBoolean(false);
        RepeatOffenderTracker flaky = sorted -> {
            if (!redisUp.get()) {
                throw new RedisConnectionFailureException("connection refused");
            }
            return List.of(0);
        };
        EnrichmentService service = service(flaky);

        for (int i = 0; i < 3; i++) {
            service.enrich(List.of(event("down-" + i, i)), RECEIVED_AT);
        }
        redisUp.set(true);
        service.enrich(List.of(event("up", 3)), RECEIVED_AT);
        service.enrich(List.of(event("still-up", 4)), RECEIVED_AT);
        redisUp.set(false);
        service.enrich(List.of(event("down-again", 5)), RECEIVED_AT);

        assertThat(output.getOut().lines().filter(line -> line.contains("WARN"))).hasSize(2)
                .allSatisfy(line -> assertThat(line).contains("Repeat-offender state unavailable"));
        assertThat(output.getOut().lines().filter(line -> line.contains("available again"))).hasSize(1);
        assertThat(meterRegistry.counter("miniwsa.enrichment.repeat_offender.unavailable").count()).isEqualTo(4);
    }

    private EnrichmentService service(RepeatOffenderTracker tracker) {
        return new EnrichmentService(new AttackTypeClassifier(), calculator, tracker, meterRegistry);
    }

    private static SecurityEvent event(String eventId, int minutes) {
        return new SecurityEvent(eventId, T0.plusSeconds(60L * minutes), 1L, null, "203.0.113.42", null, "/", "GET",
                null, null, new Rule("1", null, null, Severity.LOW, Category.BOT), Action.MONITOR, null, null, null);
    }
}
