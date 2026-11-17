package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.Category;
import org.springframework.stereotype.Component;

/**
 * Maps a rule category to the attack type shown to analysts. The labels live on {@link Category}, so adding a
 * category or renaming an attack type is a one-line change there. This class stays separate, and separately
 * tested, because classification is its own step of enrichment and the natural place for richer logic later
 * (e.g. looking at the rule id or the path).
 */
@Component
public class AttackTypeClassifier {

    public String classify(Category category) {
        return category.attackType();
    }
}
