package com.bensopher.miniwsa.enrichment;

import org.springframework.stereotype.Component;

/** Bonus when the client IP has been active within the window just before this event. */
@Component
public class RepeatOffenderScoreRule implements ScoreRule {

    private final int threshold;
    private final int bonus;

    public RepeatOffenderScoreRule(ScoringProperties properties) {
        this.threshold = properties.repeatOffender().threshold();
        this.bonus = properties.repeatOffender().bonus();
    }

    @Override
    public int score(ScoringContext context) {
        // A6: "more than 5 events" = this event plus at least 5 others in the window.
        return context.priorEventsFromIpInWindow() >= threshold ? bonus : 0;
    }
}
