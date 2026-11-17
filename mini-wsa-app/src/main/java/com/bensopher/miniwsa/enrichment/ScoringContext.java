package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.SecurityEvent;

/**
 * Everything a score rule may look at.
 *
 * @param priorEventsFromIpInWindow how many OTHER events from the same client IP fall in the repeat-offender
 *                                  window ending at this event's timestamp (A5, A6)
 */
public record ScoringContext(SecurityEvent event, int priorEventsFromIpInWindow) {
}
