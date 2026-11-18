package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient.BodyContentSpec;

import java.util.List;

class OpenApiIT extends AbstractIntegrationTest {

    @Test
    void theApiDocumentIsPublicAndDescribesEveryEndpointAndTheApiKey() {
        anonymous().get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/v1/events/ingest'].post").exists()
                .jsonPath("$.paths['/v1/stats/summary'].get").exists()
                .jsonPath("$.paths['/v1/events/samples'].get").exists()
                .jsonPath("$.components.securitySchemes.apiKey.type").isEqualTo("apiKey")
                .jsonPath("$.components.securitySchemes.apiKey.in").isEqualTo("header")
                .jsonPath("$.components.securitySchemes.apiKey.name").isEqualTo("X-API-Key")
                .jsonPath("$.security[0].apiKey").exists()
                // The actuator is operational, not part of the API description.
                .jsonPath("$.paths['/actuator/health']").doesNotExist();
    }

    @Test
    void theIngestBodyIsOneEventOrAnArrayOfAtMost1000() {
        String schema = "$.paths['/v1/events/ingest'].post.requestBody.content['application/json']";

        anonymous().get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath(schema + ".schema.oneOf[0]['$ref']").isEqualTo("#/components/schemas/EventPayload")
                .jsonPath(schema + ".schema.oneOf[1].type").isEqualTo("array")
                .jsonPath(schema + ".schema.oneOf[1].maxItems").isEqualTo(1000)
                .jsonPath(schema + ".examples.single").exists()
                .jsonPath(schema + ".examples.batch").exists();
    }

    @Test
    void everyOperationDocumentsItsProblemResponses() {
        BodyContentSpec doc = anonymous().get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody();

        for (String code : List.of("201", "400", "401", "403", "413", "415", "503")) {
            doc.jsonPath("$.paths['/v1/events/ingest'].post.responses['" + code + "']").exists();
        }
        for (String code : List.of("200", "400", "401", "403", "503")) {
            doc.jsonPath("$.paths['/v1/stats/summary'].get.responses['" + code + "']").exists();
            doc.jsonPath("$.paths['/v1/events/samples'].get.responses['" + code + "']").exists();
        }
        doc.jsonPath("$.components.schemas.Problem.properties.errors").exists();
    }

    @Test
    void swaggerUiIsPublic() {
        anonymous().get().uri("/swagger-ui.html").exchange()
                .expectStatus().isFound()
                .expectHeader().valueMatches("Location", ".*/swagger-ui/index.html");
        anonymous().get().uri("/swagger-ui/index.html").exchange()
                .expectStatus().isOk();
    }
}
