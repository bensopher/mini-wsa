package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.event.SecurityEvent;

import java.time.Instant;

/**
 * The identity of a stored event. The table's unique key is (event_id, event_time), because a hypertable's
 * unique keys must include its partition column, so the same eventId with a different timestamp is a
 * different event (A4).
 */
public record EventKey(String eventId, Instant timestamp) {

    public static EventKey of(SecurityEvent event) {
        return new EventKey(event.eventId(), event.timestamp());
    }
}
