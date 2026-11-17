package com.bensopher.miniwsa.enrichment;

import org.springframework.stereotype.Component;

import java.util.List;

/** Sums the contributions of every {@link ScoreRule} and caps the result at 100. */
@Component
public class ThreatScoreCalculator {

    static final int MAX_SCORE = 100;

    private final List<ScoreRule> rules;

    public ThreatScoreCalculator(List<ScoreRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public int calculate(ScoringContext context) {
        int total = rules.stream().mapToInt(rule -> rule.score(context)).sum();
        // With today's weights the maximum is 40 + 20 + 15 + 15 = 90, so the cap doesn't trigger yet. It is
        // kept because weights are configurable and rules get added.
        return Math.min(MAX_SCORE, total);
    }
}
