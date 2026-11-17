package com.bensopher.miniwsa.ingest;

import java.time.Instant;
import java.util.List;

/**
 * Result of an ingest request. {@code received == stored + duplicates}, and {@code events} follows the
 * request order.
 */
public record IngestResponse(int received, int stored, int duplicates, Instant receivedAt, List<IngestedEvent> events) {

    /** {@code duplicate} is true when the same (eventId, timestamp) was already stored or appeared earlier in the request. */
    public record IngestedEvent(String eventId, boolean duplicate) {
    }
}
