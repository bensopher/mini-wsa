package com.bensopher.miniwsa.common;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Time-range defaults and limits shared by the read endpoints. */
@Validated
@ConfigurationProperties("miniwsa.query")
public record QueryProperties(@NotNull Duration defaultRange, @NotNull Duration maxRange) {
}
