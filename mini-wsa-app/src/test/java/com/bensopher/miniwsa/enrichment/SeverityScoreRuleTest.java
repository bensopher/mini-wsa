package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Severity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.bensopher.miniwsa.enrichment.ScoringFixtures.context;
import static com.bensopher.miniwsa.enrichment.ScoringFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;

class SeverityScoreRuleTest {

    @ParameterizedTest
    @CsvSource({"CRITICAL, 40", "HIGH, 30", "MEDIUM, 20", "LOW, 10"})
    void scoresTheSeverityBaseScore(Severity severity, int expected) {
        assertThat(new SeverityScoreRule().score(context(event(severity, Action.MONITOR, "/"))))
                .isEqualTo(expected);
    }
}
