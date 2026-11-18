package com.bensopher.miniwsa.generator;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One event in the ingest API's format. The generator's own copy, not the app's: it is a black-box client, so a
 * change to the app's internals can't silently change what the generator sends.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeneratedEvent(
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
        String action,
        Geo geoLocation,
        Long requestSize,
        Long responseSize) {

    public record Rule(String id, String name, String message, String severity, String category) {
    }

    public record Geo(String country, String city) {
    }

    /** IDs are assigned last, after the events are sorted, so they increase with time. */
    GeneratedEvent withEventId(String id) {
        return new GeneratedEvent(id, timestamp, configId, policyId, clientIp, hostname, path, method, statusCode,
                userAgent, rule, action, geoLocation, requestSize, responseSize);
    }
}
