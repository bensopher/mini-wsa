package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Runs with a 1 KB limit so the tests don't have to upload megabytes. */
@TestPropertySource(properties = "miniwsa.limits.max-request-body=1KB")
class RequestBodyLimitIT extends AbstractIntegrationTest {

    @Test
    void oversizedBodyIsRejectedBeforeAuthentication() {
        anonymous().post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body("x".repeat(1025))
                .exchange()
                .expectStatus().isEqualTo(413)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://github.com/bensopher/mini-wsa/errors/payload-too-large")
                .jsonPath("$.detail").isEqualTo("The request body must not exceed 1024 bytes");
    }
}
