package com.bensopher.miniwsa.samples;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** A stored event as returned by the samples API: the ingested fields plus enrichment. Null fields are omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SampleEvent(
        String eventId,
        Instant timestamp,
        Long configId,
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
        Long responseSize,
        String attackType,
        Integer threatScore,
        Instant receivedAt) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rule(String id, String name, String message, String severity, String category) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Geo(String country, String city) {
    }
}
