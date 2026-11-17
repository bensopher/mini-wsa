package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.bensopher.miniwsa.enrichment.ScoringFixtures.DEFAULT_PROPERTIES;
import static com.bensopher.miniwsa.enrichment.ScoringFixtures.context;
import static com.bensopher.miniwsa.enrichment.ScoringFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;

class SensitivePathScoreRuleTest {

    private final SensitivePathScoreRule rule = new SensitivePathScoreRule(DEFAULT_PROPERTIES);

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/ADMIN/x", "/administrator", "/api/v1/login", "/LOGIN"})
    void sensitivePathsGetTheBonus(String path) {
        assertThat(rule.score(context(event(Severity.LOW, Action.MONITOR, path)))).isEqualTo(15);
    }

    /** "/wp-admin" contains "-admin", not "/admin": matching it is a config change (add the pattern). */
    @ParameterizedTest
    @ValueSource(strings = {"/home", "/", "/api/v1/users", "/logout", "/adm", "/wp-admin/setup.php"})
    void otherPathsGetNothing(String path) {
        assertThat(rule.score(context(event(Severity.LOW, Action.MONITOR, path)))).isZero();
    }

    @Test
    void patternsAndBonusComeFromConfigurationAndIgnoreCase() {
        var properties = new ScoringProperties(
                new ScoringProperties.SensitivePath(List.of("/WP-LOGIN.php"), 25), DEFAULT_PROPERTIES.repeatOffender());
        var configured = new SensitivePathScoreRule(properties);

        assertThat(configured.score(context(event(Severity.LOW, Action.MONITOR, "/wp-login.php")))).isEqualTo(25);
        assertThat(configured.score(context(event(Severity.LOW, Action.MONITOR, "/admin")))).isZero();
    }
}
