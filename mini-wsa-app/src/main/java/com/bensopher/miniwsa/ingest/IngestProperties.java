package com.bensopher.miniwsa.ingest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param maxBatchSize  larger batches are rejected with 413
 * @param maxFutureSkew A3: how far ahead of the server clock an event's timestamp may be
 */
@Validated
@ConfigurationProperties("miniwsa.ingest")
public record IngestProperties(@Positive int maxBatchSize, @NotNull Duration maxFutureSkew) {
}
