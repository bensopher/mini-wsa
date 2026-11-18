package com.bensopher.miniwsa.generator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Coordinated attacks ("Operation Shadow Flood") on top of the background noise. Each wave is concentrated in time
 * and on a few clients, which is what the repeat-offender rule and the stats endpoints should make visible.
 */
final class AttackWaves {

    enum Kind { SQLI_BURST, CREDENTIAL_STUFFING, SLOW_DOS }

    /** What one wave did, printed at the end of a run so its traces can be looked up in the API. */
    record Wave(Kind kind, List<String> clients, String path, int events, Instant start, Instant end) {
    }

    record Result(List<GeneratedEvent> events, List<Wave> waves) {
    }

    private final Random random;
    private final EventFactory factory;

    AttackWaves(Random random, EventFactory factory) {
        this.random = random;
        this.factory = factory;
    }

    /**
     * Generates waves, cycling through the kinds, until exactly {@code budget} events exist; the last wave is cut
     * short if needed. Every event falls inside {@code [from, to]}.
     */
    Result generate(int budget, Instant from, Instant to) {
        List<GeneratedEvent> events = new ArrayList<>(budget);
        List<Wave> waves = new ArrayList<>();
        Kind[] kinds = Kind.values();
        for (int i = 0; events.size() < budget; i++) {
            List<GeneratedEvent> wave = switch (kinds[i % kinds.length]) {
                case SQLI_BURST -> sqliBurst(from, to);
                case CREDENTIAL_STUFFING -> credentialStuffing(from, to);
                case SLOW_DOS -> slowDos(from, to);
            };
            wave.sort(Comparator.comparing(GeneratedEvent::timestamp));
            List<GeneratedEvent> kept = wave.subList(0, Math.min(wave.size(), budget - events.size()));
            events.addAll(kept);
            waves.add(summarize(kinds[i % kinds.length], kept));
        }
        return new Result(events, waves);
    }

    /**
     * One client probes one endpoint 50-200 times within 1-3 minutes with sqlmap: a burst from the same IP to the
     * same path.
     */
    List<GeneratedEvent> sqliBurst(Instant from, Instant to) {
        Window window = window(from, to, Duration.ofMinutes(1 + random.nextInt(3)));
        String ip = Catalog.documentationIp(random);
        String path = random.nextBoolean() ? "/api/v1/search" : "/api/v1/login";
        int count = 50 + random.nextInt(151);
        List<GeneratedEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String severity = random.nextInt(5) == 0 ? "HIGH" : "CRITICAL";
            String action = random.nextInt(10) == 0 ? "ALERT" : "DENY";
            events.add(factory.create(window.randomTime(random), ip, path, "INJECTION", severity, action,
                    Catalog.SQLMAP));
        }
        return events;
    }

    /** A botnet of 20-50 clients, each trying the login endpoint 5-20 times over 5-10 minutes. */
    List<GeneratedEvent> credentialStuffing(Instant from, Instant to) {
        Window window = window(from, to, Duration.ofMinutes(5 + random.nextInt(6)));
        List<GeneratedEvent> events = new ArrayList<>();
        for (String ip : distinctIps(20 + random.nextInt(31))) {
            int attempts = 5 + random.nextInt(16);
            for (int i = 0; i < attempts; i++) {
                String category = random.nextBoolean() ? "BOT" : "RATE_LIMIT";
                String userAgent = random.nextBoolean() ? Catalog.PYTHON_REQUESTS : Catalog.BROWSERS.pick(random);
                events.add(factory.create(window.randomTime(random), ip, "/api/v1/login", category, userAgent));
            }
        }
        return events;
    }

    /** 3-5 clients sending expensive requests at a steady, low rate for 15-30 minutes, to stay under thresholds. */
    List<GeneratedEvent> slowDos(Instant from, Instant to) {
        Window window = window(from, to, Duration.ofMinutes(15 + random.nextInt(16)));
        String path = random.nextBoolean() ? "/api/v1/export" : "/";
        List<GeneratedEvent> events = new ArrayList<>();
        for (String ip : distinctIps(3 + random.nextInt(3))) {
            int count = 20 + random.nextInt(21);
            for (int i = 0; i < count; i++) {
                // Evenly spaced, with a little jitter: a steady trickle rather than a burst.
                double position = (i + random.nextDouble()) / count;
                events.add(factory.create(window.at(position), ip, path, "DOS", Catalog.BROWSERS.pick(random)));
            }
        }
        return events;
    }

    private List<String> distinctIps(int count) {
        Set<String> ips = new LinkedHashSet<>();
        while (ips.size() < count) {
            ips.add(Catalog.documentationIp(random));
        }
        return new ArrayList<>(ips);
    }

    /** A wave's time window: placed at random inside [from, to], and shortened if the range is shorter. */
    private Window window(Instant from, Instant to, Duration duration) {
        long rangeMillis = Duration.between(from, to).toMillis();
        long lengthMillis = Math.min(duration.toMillis(), rangeMillis);
        long startOffset = (long) (random.nextDouble() * (rangeMillis - lengthMillis));
        return new Window(from.plusMillis(startOffset), lengthMillis);
    }

    private record Window(Instant start, long lengthMillis) {

        Instant randomTime(Random random) {
            return at(random.nextDouble());
        }

        /** @param position 0 (start) to 1 (end) */
        Instant at(double position) {
            return start.plusMillis((long) (position * lengthMillis));
        }
    }

    private static Wave summarize(Kind kind, List<GeneratedEvent> events) {
        List<String> clients = events.stream().map(GeneratedEvent::clientIp).distinct().toList();
        return new Wave(kind, clients, events.getFirst().path(), events.size(), events.getFirst().timestamp(),
                events.getLast().timestamp());
    }
}
