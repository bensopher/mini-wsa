package com.bensopher.miniwsa.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** Input limits that protect the service from oversized or deeply nested requests. */
@Validated
@ConfigurationProperties("miniwsa.limits")
public record LimitsProperties(@NotNull DataSize maxRequestBody, @NotNull @Valid Json json) {

    public record Json(@Positive int maxNestingDepth, @Positive int maxStringLength) {
    }
}
