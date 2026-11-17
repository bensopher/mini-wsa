package com.bensopher.miniwsa.enrichment;

/**
 * One independent contribution to an event's threat score. Each rule is a Spring bean; the calculator receives
 * all of them, so adding a rule means adding a class, and nothing else changes.
 */
public interface ScoreRule {

    /** This rule's contribution, {@code >= 0}. */
    int score(ScoringContext context);
}
