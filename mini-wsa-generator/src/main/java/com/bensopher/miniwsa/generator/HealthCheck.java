package com.bensopher.miniwsa.generator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Waits until the API is ready to take traffic, for runs started together with the app (docker compose). It polls
 * the readiness probe rather than overall health: readiness is what says "send me traffic", and it doesn't wait on
 * Redis, which ingestion can do without.
 */
final class HealthCheck {

    private final HttpClient http;
    private final URI readinessUri;

    HealthCheck(HttpClient http, URI target) {
        this.http = http;
        this.readinessUri = target.resolve("/actuator/health/readiness");
    }

    boolean waitUntilReady(Duration timeout, Duration interval) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpRequest request = HttpRequest.newBuilder(readinessUri).timeout(Duration.ofSeconds(2)).GET().build();
        while (System.nanoTime() < deadline) {
            try {
                if (http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return true;
                }
            } catch (IOException e) {
                // Not listening yet: keep polling.
            }
            Thread.sleep(interval);
        }
        return false;
    }
}
