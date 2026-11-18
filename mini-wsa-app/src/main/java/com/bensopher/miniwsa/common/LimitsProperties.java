package com.bensopher.miniwsa.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limits that protect the service from oversized or deeply nested requests and from expensive queries.
 * The read-query timeout is applied in whole seconds (JDBC's unit), so it must be at least one second.
 */
@Validated
@ConfigurationProperties("miniwsa.limits")
public record LimitsProperties(@NotNull DataSize maxRequestBody, @NotNull @Valid Json json,
        @NotNull @DurationMin(seconds = 1) Duration readQueryTimeout) {

    public record Json(@Positive int maxNestingDepth, @Positive int maxStringLength) {
    }
}
