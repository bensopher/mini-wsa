package com.bensopher.miniwsa.generator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Generates a run of events over {@code [now - span, now]}: background noise plus attack waves, in chronological
 * order like a live feed. Everything random comes from one seeded {@link Random}, so the same seed, clock and run
 * id give exactly the same events.
 */
public final class EventGenerator {

    private final long seed;
    private final Clock clock;
    private final Duration span;
    private final List<Long> configIds;
    private final double waveRatio;

    /** @param waveRatio the fraction of events that belong to attack waves, 0 to 1 */
    public EventGenerator(long seed, Clock clock, Duration span, List<Long> configIds, double waveRatio) {
        this.seed = seed;
        this.clock = clock;
        this.span = span;
        this.configIds = List.copyOf(configIds);
        this.waveRatio = waveRatio;
    }

    public record Run(List<GeneratedEvent> events, List<AttackWaves.Wave> waves) {
    }

    /** @param runId part of every event id, so repeated runs never collide while ids within a run stay unique */
    public Run generate(int count, String runId) {
        Random random = new Random(seed);
        EventFactory factory = new EventFactory(random, configIds);
        Instant to = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant from = to.minus(span);

        int waveEvents = (int) Math.round(count * waveRatio);
        AttackWaves.Result waves = new AttackWaves(random, factory).generate(waveEvents, from, to);
        List<GeneratedEvent> events = new ArrayList<>(waves.events());
        events.addAll(new BackgroundTraffic(random, factory).generate(count - waveEvents, from, to));
        events.sort(Comparator.comparing(GeneratedEvent::timestamp));
        List<GeneratedEvent> numbered = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            numbered.add(events.get(i).withEventId("evt-%s-%06d".formatted(runId, i + 1)));
        }
        return new Run(numbered, waves.waves());
    }
}
