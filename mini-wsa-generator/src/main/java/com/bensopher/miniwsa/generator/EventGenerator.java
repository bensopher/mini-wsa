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
 * Generates a run of events over {@code [now - span, now]}, in chronological order, like a live feed. Everything
 * random comes from one seeded {@link Random}, so the same seed, clock and run id give exactly the same events.
 */
public final class EventGenerator {

    private final long seed;
    private final Clock clock;
    private final Duration span;
    private final List<Long> configIds;

    public EventGenerator(long seed, Clock clock, Duration span, List<Long> configIds) {
        this.seed = seed;
        this.clock = clock;
        this.span = span;
        this.configIds = List.copyOf(configIds);
    }

    /** @param runId part of every event id, so repeated runs never collide while ids within a run stay unique */
    public List<GeneratedEvent> generate(int count, String runId) {
        Random random = new Random(seed);
        EventFactory factory = new EventFactory(random, configIds);
        Instant to = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant from = to.minus(span);

        List<GeneratedEvent> events = new ArrayList<>(new BackgroundTraffic(random, factory).generate(count, from, to));
        events.sort(Comparator.comparing(GeneratedEvent::timestamp));
        List<GeneratedEvent> numbered = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            numbered.add(events.get(i).withEventId("evt-%s-%06d".formatted(runId, i + 1)));
        }
        return numbered;
    }
}
