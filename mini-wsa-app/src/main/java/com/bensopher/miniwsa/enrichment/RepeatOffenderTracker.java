package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.SecurityEvent;

import java.util.List;

/**
 * Remembers recent events per client IP. Enrichment depends on this interface, not on Redis, so the store can
 * change (an in-memory fake in tests, a stream processor's state at scale) without touching scoring.
 */
public interface RepeatOffenderTracker {

    /**
     * Records the events and returns, for each one (same position as in the input), how many OTHER events from
     * the same client IP fall in {@code [timestamp - window, timestamp]}, counting events recorded earlier and
     * earlier events of this same call.
     *
     * @param eventsSortedByTime the events, sorted by timestamp ascending
     */
    List<Integer> recordAndCount(List<SecurityEvent> eventsSortedByTime);
}
