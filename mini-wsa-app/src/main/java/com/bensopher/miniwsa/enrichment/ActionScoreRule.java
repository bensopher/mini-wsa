package com.bensopher.miniwsa.enrichment;

import org.springframework.stereotype.Component;

/** Bonus for what the edge did with the request (weights on the Action enum). */
@Component
public class ActionScoreRule implements ScoreRule {

    @Override
    public int score(ScoringContext context) {
        return context.event().action().scoreBonus();
    }
}
