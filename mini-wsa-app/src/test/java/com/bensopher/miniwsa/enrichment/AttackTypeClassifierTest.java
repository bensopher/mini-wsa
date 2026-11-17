package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Category;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class AttackTypeClassifierTest {

    private final AttackTypeClassifier classifier = new AttackTypeClassifier();

    @ParameterizedTest
    @CsvSource({
            "INJECTION,          SQL/Command Injection",
            "XSS,                Cross-Site Scripting",
            "PROTOCOL_VIOLATION, Protocol Anomaly",
            "DATA_LEAKAGE,       Data Exfiltration",
            "BOT,                Bot Activity",
            "DOS,                Denial of Service",
            "RATE_LIMIT,         Rate Limiting"
    })
    void mapsEachCategoryToItsAttackType(Category category, String attackType) {
        assertThat(classifier.classify(category)).isEqualTo(attackType);
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void everyCategoryHasAnAttackType(Category category) {
        assertThat(classifier.classify(category)).isNotBlank();
    }
}
