package com.bensopher.miniwsa.generator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Posts one batch to the ingest API. A 503 means the service is shedding load and nothing was stored, so the
 * batch is sent again after a pause, up to {@link #MAX_RETRIES} times. Resending is safe because ingestion is
 * idempotent. Other errors are not retried: a 400 won't get better by repeating it.
 */
final class IngestClient {

    static final int MAX_RETRIES = 3;

    /** How the client waits between attempts; replaced in tests so they don't actually sleep. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** The outcome of one batch: the final status ("201", "503", or "IO_ERROR" if there was no response). */
    record BatchResult(String status, int events, int duplicates, int retries, Duration latency) {
    }

    private final HttpClient http;
    private final URI ingestUri;
    private final String apiKey;
    private final JsonMapper json;
    private final Sleeper sleeper;
    private final Duration firstBackoff;

    IngestClient(HttpClient http, URI target, String apiKey, JsonMapper json, Sleeper sleeper, Duration firstBackoff) {
        this.http = http;
        this.ingestUri = target.resolve("/v1/events/ingest");
        this.apiKey = apiKey;
        this.json = json;
        this.sleeper = sleeper;
        this.firstBackoff = firstBackoff;
    }

    BatchResult send(List<GeneratedEvent> batch) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(ingestUri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(batch)))
                .build();
        int retries = 0;
        while (true) {
            long started = System.nanoTime();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                Duration latency = Duration.ofNanos(System.nanoTime() - started);
                return new BatchResult("IO_ERROR", batch.size(), 0, retries, latency);
            }
            Duration latency = Duration.ofNanos(System.nanoTime() - started);
            if (response.statusCode() == 503 && retries < MAX_RETRIES) {
                sleeper.sleep(backoff(retries, response));
                retries++;
                continue;
            }
            int duplicates = response.statusCode() == 201 ? duplicates(response.body()) : 0;
            return new BatchResult(String.valueOf(response.statusCode()), batch.size(), duplicates, retries, latency);
        }
    }

    /**
     * Exponential (1s, 2s, 4s with the default first backoff), but never shorter than the server's Retry-After:
     * the server knows best how long it needs.
     */
    private Duration backoff(int retries, HttpResponse<String> response) {
        Duration exponential = firstBackoff.multipliedBy(1L << retries);
        Duration retryAfter = response.headers().firstValue("Retry-After")
                .map(IngestClient::seconds)
                .orElse(Duration.ZERO);
        return exponential.compareTo(retryAfter) >= 0 ? exponential : retryAfter;
    }

    /** Retry-After in seconds; the HTTP-date form is ignored, leaving the exponential backoff. */
    private static Duration seconds(String value) {
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return Duration.ZERO;
        }
    }

    private int duplicates(String body) {
        JsonNode response = json.readTree(body);
        return response.path("duplicates").asInt(0);
    }
}
