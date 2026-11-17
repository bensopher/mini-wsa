package com.bensopher.miniwsa.enrichment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.bensopher.miniwsa.enrichment.ScoringFixtures.DEFAULT_PROPERTIES;
import static com.bensopher.miniwsa.enrichment.ScoringFixtures.sampleEvent;
import static org.assertj.core.api.Assertions.assertThat;

class RepeatOffenderScoreRuleTest {

    private final RepeatOffenderScoreRule rule = new RepeatOffenderScoreRule(DEFAULT_PROPERTIES);

    /** A6: the bonus needs at least 5 OTHER events in the window (6 in total, i.e. "more than 5"). */
    @ParameterizedTest
    @CsvSource({"0, 0", "4, 0", "5, 15", "50, 15"})
    void bonusAppliesFromTheThreshold(int priorEvents, int expected) {
        assertThat(rule.score(new ScoringContext(sampleEvent(), priorEvents))).isEqualTo(expected);
    }
}
