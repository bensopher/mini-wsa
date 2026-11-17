package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs with a 1 KB limit so the tests don't have to upload megabytes. */
@TestPropertySource(properties = "miniwsa.limits.max-request-body=1KB")
class RequestBodyLimitIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

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

    @Test
    void chunkedBodyWithoutContentLengthIsCutOffAtTheLimit() throws Exception {
        String body = "[\"" + "x".repeat(2000) + "\"]";
        // A body publisher of unknown length makes the JDK client send the body chunked, with no Content-Length.
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/events/ingest"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", "dev-ingest-key")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body.getBytes())))
                .build();

        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(response.body()).contains("The request body must not exceed 1024 bytes");
        }
    }
}
