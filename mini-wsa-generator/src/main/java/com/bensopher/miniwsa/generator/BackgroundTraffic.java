package com.bensopher.miniwsa.generator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Everyday noise: a few hundred clients, a handful of which produce most of the events, with mixed categories
 * spread evenly over the time range.
 */
final class BackgroundTraffic {

    static final int CLIENTS = 300;

    private final Random random;
    private final EventFactory factory;
    private final Weighted<String> clients;

    BackgroundTraffic(Random random, EventFactory factory) {
        this.random = random;
        this.factory = factory;
        Set<String> addresses = new LinkedHashSet<>();
        while (addresses.size() < CLIENTS) {
            addresses.add(Catalog.documentationIp(random));
        }
        this.clients = Weighted.zipf(new ArrayList<>(addresses));
    }

    List<GeneratedEvent> generate(int count, Instant from, Instant to) {
        long spanMillis = Duration.between(from, to).toMillis();
        List<GeneratedEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant time = from.plusMillis((long) (random.nextDouble() * spanMillis));
            String category = Catalog.BACKGROUND_CATEGORIES.pick(random);
            String path = Catalog.PATHS_BY_CATEGORY.getOrDefault(category, Catalog.PATHS).pick(random);
            events.add(factory.create(time, clients.pick(random), path, category,
                    Catalog.userAgent(category, random)));
        }
        return events;
    }
}
