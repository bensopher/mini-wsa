package com.bensopher.miniwsa.event;

import java.time.Instant;

/**
 * A validated security event. {@code timestamp} is normalized to UTC and {@code clientIp} is in
 * canonical form, so equal addresses always compare equal.
 */
public record SecurityEvent(
        String eventId,
        Instant timestamp,
        long configId,
        String policyId,
        String clientIp,
        String hostname,
        String path,
        String method,
        Integer statusCode,
        String userAgent,
        Rule rule,
        Action action,
        GeoLocation geoLocation,
        Long requestSize,
        Long responseSize) {
}
