package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import com.bensopher.miniwsa.event.GeoLocation;
import com.bensopher.miniwsa.event.Rule;
import com.bensopher.miniwsa.event.SecurityEvent;
import com.bensopher.miniwsa.event.Severity;

import java.time.Instant;

/** Validated domain events for repository and service tests. */
final class TestEvents {

    private TestEvents() {
    }

    /** The assignment's sample event with the given id and time. */
    static SecurityEvent sampleEvent(String eventId, Instant timestamp) {
        return new SecurityEvent(eventId, timestamp, 14227L, "pol_web1", "203.0.113.42", "www.example.com",
                "/api/v1/login", "POST", 403, "Mozilla/5.0",
                new Rule("950001", "SQL_INJECTION", "SQL Injection Attack Detected", Severity.CRITICAL, Category.INJECTION),
                Action.DENY, new GeoLocation("CN", "Beijing"), 1024L, 256L);
    }

    /** Only the required fields set. */
    static SecurityEvent minimalEvent(String eventId, Instant timestamp, String clientIp) {
        return new SecurityEvent(eventId, timestamp, 1L, null, clientIp, null, "/", "GET", null, null,
                new Rule("1", null, null, Severity.LOW, Category.BOT), Action.MONITOR, null, null, null);
    }
}
