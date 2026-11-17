package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Severity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.bensopher.miniwsa.enrichment.ScoringFixtures.context;
import static com.bensopher.miniwsa.enrichment.ScoringFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;

class ActionScoreRuleTest {

    @ParameterizedTest
    @CsvSource({"DENY, 20", "ALERT, 10", "MONITOR, 0"})
    void scoresTheActionBonus(Action action, int expected) {
        assertThat(new ActionScoreRule().score(context(event(Severity.LOW, action, "/")))).isEqualTo(expected);
    }
}
