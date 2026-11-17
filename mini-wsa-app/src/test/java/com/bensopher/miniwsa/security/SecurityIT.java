package com.bensopher.miniwsa.security;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void healthIsPublicAndShowsComponentStatusWithoutDetails() {
        anonymous().get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components.db.status").isEqualTo("UP")
                .jsonPath("$.components.db.details").doesNotExist()
                .jsonPath("$.components.diskSpace.details").doesNotExist();
    }

    @Test
    void missingKeyIs401Problem() {
        anonymous().get().uri("/actuator/metrics").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://github.com/bensopher/mini-wsa/errors/unauthorized")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").isEqualTo("An API key is required in the X-API-Key header")
                .jsonPath("$.instance").isEqualTo("/actuator/metrics");
    }

    @Test
    void unknownKeyIs401AndIsNeverEchoed() {
        String body = withKey("guess-1234").get().uri("/actuator/metrics").exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("The API key is not valid").doesNotContain("guess-1234");
    }

    @Test
    void wrongRoleIs403Problem() {
        asIngester().get().uri("/actuator/metrics").exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://github.com/bensopher/mini-wsa/errors/forbidden")
                .jsonPath("$.detail").isEqualTo("This API key does not have the role required for this endpoint");
        asReader().get().uri("/actuator/info").exchange().expectStatus().isForbidden();
    }

    @Test
    void adminCanReadMetricsAndInfo() {
        asAdmin().get().uri("/actuator/metrics").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.names").isArray();
        asAdmin().get().uri("/actuator/info").exchange().expectStatus().isOk();
    }

    @Test
    void unmappedPathsAreDeniedEvenForAdmin() {
        anonymous().get().uri("/v1/nothing-here").exchange().expectStatus().isUnauthorized();
        asAdmin().get().uri("/v1/nothing-here").exchange().expectStatus().isForbidden();
    }

    @Test
    void containerErrorsKeepTheirStatusInsteadOfBecoming401() {
        // The firewall rejects ';' in the URL with a container-level 400 that is rendered on the ERROR
        // dispatch. If the ERROR dispatch weren't permitted, this anonymous request would get a 401.
        anonymous().get().uri("/actuator/health;jsessionid=abc").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void securityHeadersAreSetAndNoSessionIsCreated() {
        asAdmin().get().uri("/actuator/info").exchange()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().doesNotExist("Set-Cookie");
    }

    @Test
    void failuresAreCountedByReason() {
        double before = failures("invalid");

        withKey("wrong").get().uri("/actuator/metrics").exchange().expectStatus().isUnauthorized();

        assertThat(failures("invalid")).isEqualTo(before + 1);
    }

    private double failures(String reason) {
        var counter = meterRegistry.find("miniwsa.security.auth.failures").tag("reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }
}
