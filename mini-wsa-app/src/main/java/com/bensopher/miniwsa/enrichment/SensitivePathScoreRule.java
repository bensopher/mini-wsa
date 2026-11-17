package com.bensopher.miniwsa.enrichment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Bonus when the path targets a sensitive area such as an admin panel or a login form. */
@Component
public class SensitivePathScoreRule implements ScoreRule {

    private final List<String> patterns;
    private final int bonus;

    public SensitivePathScoreRule(ScoringProperties properties) {
        this.patterns = properties.sensitivePath().patterns().stream()
                .map(p -> p.toLowerCase(Locale.ROOT))
                .toList();
        this.bonus = properties.sensitivePath().bonus();
    }

    @Override
    public int score(ScoringContext context) {
        // A7: case-insensitive substring match, so /ADMIN/users and /administrator also count.
        String path = context.event().path().toLowerCase(Locale.ROOT);
        return patterns.stream().anyMatch(path::contains) ? bonus : 0;
    }
}
