package com.bensopher.miniwsa.event;

import java.time.Instant;

/** A validated event plus what the service derived from it. The score is computed once, at ingest, and stored. */
public record EnrichedEvent(SecurityEvent event, String attackType, int threatScore, Instant receivedAt) {
}
