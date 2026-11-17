package com.bensopher.miniwsa.enrichment;

import org.springframework.stereotype.Component;

/** Base score from the rule severity (weights on the Severity enum). */
@Component
public class SeverityScoreRule implements ScoreRule {

    @Override
    public int score(ScoringContext context) {
        return context.event().rule().severity().baseScore();
    }
}
