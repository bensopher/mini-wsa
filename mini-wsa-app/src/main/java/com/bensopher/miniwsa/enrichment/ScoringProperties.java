package com.bensopher.miniwsa.enrichment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Threat-score settings, so weights and thresholds change in configuration rather than code. */
@Validated
@ConfigurationProperties("miniwsa.scoring")
public record ScoringProperties(@NotNull @Valid SensitivePath sensitivePath,
                                @NotNull @Valid RepeatOffender repeatOffender) {

    /** @param patterns path substrings, matched case-insensitively (A7) */
    public record SensitivePath(@NotEmpty List<String> patterns, @PositiveOrZero int bonus) {
    }

    /** @param threshold how many other events from the same IP in the window trigger the bonus (A6) */
    public record RepeatOffender(@PositiveOrZero int threshold, @PositiveOrZero int bonus) {
    }
}
