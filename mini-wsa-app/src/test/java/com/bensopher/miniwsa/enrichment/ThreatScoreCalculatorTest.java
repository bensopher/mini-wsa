package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.bensopher.miniwsa.enrichment.ScoringFixtures.DEFAULT_PROPERTIES;
import static com.bensopher.miniwsa.enrichment.ScoringFixtures.event;
import static com.bensopher.miniwsa.enrichment.ScoringFixtures.sampleEvent;
import static org.assertj.core.api.Assertions.assertThat;

class ThreatScoreCalculatorTest {

    private final List<ScoreRule> productionRules = List.of(new SeverityScoreRule(), new ActionScoreRule(),
            new SensitivePathScoreRule(DEFAULT_PROPERTIES), new RepeatOffenderScoreRule(DEFAULT_PROPERTIES));
    private final ThreatScoreCalculator calculator = new ThreatScoreCalculator(productionRules);

    @Test
    void sampleEventScores75() {
        // CRITICAL 40 + DENY 20 + /api/v1/login 15
        assertThat(calculator.calculate(new ScoringContext(sampleEvent(), 0))).isEqualTo(75);
    }

    @Test
    void sampleEventFromARepeatOffenderScores90() {
        assertThat(calculator.calculate(new ScoringContext(sampleEvent(), 5))).isEqualTo(90);
    }

    @Test
    void lowSeverityMonitorWithNoBonusScores10() {
        assertThat(calculator.calculate(new ScoringContext(event(Severity.LOW, Action.MONITOR, "/home"), 0)))
                .isEqualTo(10);
    }

    @Test
    void totalIsCappedAt100() {
        List<ScoreRule> rules = new ArrayList<>(productionRules);
        rules.add(context -> 50); // a hypothetical extra rule: without it the maximum is 90
        ThreatScoreCalculator withExtraRule = new ThreatScoreCalculator(rules);

        assertThat(withExtraRule.calculate(new ScoringContext(sampleEvent(), 5))).isEqualTo(100);
    }
}
