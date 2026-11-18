package com.bensopher.miniwsa.generator;

import com.bensopher.miniwsa.generator.Catalog.RuleInfo;
import com.bensopher.miniwsa.generator.GeneratedEvent.Rule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

/** Fills in the details of one event once its time, client, path and category have been decided. */
final class EventFactory {

    private final Random random;
    private final List<Long> configIds;

    EventFactory(Random random, List<Long> configIds) {
        this.random = random;
        this.configIds = List.copyOf(configIds);
    }

    /** Severity follows the category, and the action follows the severity. */
    GeneratedEvent create(Instant time, String ip, String path, String category, String userAgent) {
        String severity = Catalog.SEVERITY_BY_CATEGORY.get(category).pick(random);
        return create(time, ip, path, category, severity, Catalog.ACTION_BY_SEVERITY.get(severity).pick(random),
                userAgent);
    }

    GeneratedEvent create(Instant time, String ip, String path, String category, String severity, String action,
            String userAgent) {
        RuleInfo rule = Catalog.RULES.get(category);
        String hostname = Catalog.HOSTNAMES.pick(random);
        boolean blocked = action.equals("DENY");
        return new GeneratedEvent(
                null,
                time.truncatedTo(ChronoUnit.MILLIS),
                configIds.get(random.nextInt(configIds.size())),
                hostname.startsWith("api.") ? "pol_api1" : "pol_web1",
                ip,
                hostname,
                path,
                path.startsWith("/api/v1/login") || path.startsWith("/checkout") ? "POST" : "GET",
                blocked ? 403 : 200,
                userAgent,
                new Rule(rule.id(), rule.name(), rule.message(), severity, category),
                action,
                Catalog.placeOf(ip),
                200L + random.nextInt(4000),
                blocked ? 100L + random.nextInt(300) : 500L + random.nextInt(50_000));
    }
}
