package com.bensopher.miniwsa.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Time-range defaults and limits shared by the read endpoints, and the paging limits of samples. */
@Validated
@ConfigurationProperties("miniwsa.query")
public record QueryProperties(@NotNull Duration defaultRange, @NotNull Duration maxRange,
        @NotNull @Valid Samples samples) {

    public record Samples(@Positive int defaultLimit, @Positive int maxLimit, @Min(0) int maxOffset) {
    }
}
