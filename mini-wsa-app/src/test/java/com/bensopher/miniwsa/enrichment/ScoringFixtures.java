package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import com.bensopher.miniwsa.event.Rule;
import com.bensopher.miniwsa.event.SecurityEvent;
import com.bensopher.miniwsa.event.Severity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Events and properties for scoring tests. */
final class ScoringFixtures {

    static final ScoringProperties DEFAULT_PROPERTIES = new ScoringProperties(
            new ScoringProperties.SensitivePath(List.of("/admin", "/login"), 15),
            new ScoringProperties.RepeatOffender(5, 15, Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(2)));

    private ScoringFixtures() {
    }

    /** The sample event from the assignment: CRITICAL, DENY, path /api/v1/login. */
    static SecurityEvent sampleEvent() {
        return event(Severity.CRITICAL, Action.DENY, "/api/v1/login");
    }

    static SecurityEvent event(Severity severity, Action action, String path) {
        return new SecurityEvent("evt-00132", Instant.parse("2026-05-20T14:32:10Z"), 14227L, null, "203.0.113.42",
                null, path, "POST", null, null, new Rule("950001", null, null, severity, Category.INJECTION),
                action, null, null, null);
    }

    static ScoringContext context(SecurityEvent event) {
        return new ScoringContext(event, 0);
    }
}
