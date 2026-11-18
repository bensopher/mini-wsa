package com.bensopher.miniwsa.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The OpenAPI document behind /v3/api-docs and Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    static final String API_KEY_SCHEME = "apiKey";
    static final String PROBLEM_SCHEMA = "Problem";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini WSA")
                        .version("1.0.0")
                        .description("""
                                Ingests web security events, enriches them with an attack type and a threat score, \
                                and serves statistics and samples. Every endpoint needs an X-API-Key header: use \
                                Authorize with dev-ingest-key (role INGEST), dev-read-key (READ) or dev-admin-key \
                                (READ, ADMIN). Errors are RFC 9457 problems."""))
                .components(new Components()
                        // Swagger's Authorize button sends this header on every request.
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key"))
                        .addSchemas(PROBLEM_SCHEMA, problemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }

    /**
     * Every operation can answer 401, 403 and 503 in the same way, so they are added here once instead of on each
     * controller method. Endpoint-specific errors (400, 413, 415) are declared on the methods.
     */
    @Bean
    OpenApiCustomizer commonProblemResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            operation.getResponses()
                    .addApiResponse("401", problem("Missing or unknown API key"))
                    .addApiResponse("403", problem("The API key lacks the role this endpoint needs"))
                    .addApiResponse("503", problem("Overloaded, or a read query timed out. Nothing was stored; "
                            + "retry after the number of seconds in Retry-After"));
        }));
    }

    static ApiResponse problem(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
    }

    /** RFC 9457 problem, plus {@code errors} for validation failures. */
    private static Schema<?> problemSchema() {
        return new Schema<>()
                .type("object")
                .addProperty("type", new Schema<>().type("string").format("uri")
                        .example("https://github.com/bensopher/mini-wsa/errors/validation"))
                .addProperty("title", new Schema<>().type("string").example("Validation failed"))
                .addProperty("status", new Schema<>().type("integer").example(400))
                .addProperty("detail", new Schema<>().type("string")
                        .example("1 of 3 events failed validation; nothing was stored"))
                .addProperty("instance", new Schema<>().type("string").example("/v1/events/ingest"))
                .addProperty("errors", new Schema<>().type("array").items(new Schema<>()
                        .type("object")
                        .addProperty("index", new Schema<>().type("integer").example(1)
                                .description("Position in the batch; absent for single objects and query parameters"))
                        .addProperty("field", new Schema<>().type("string").example("rule.severity"))
                        .addProperty("message", new Schema<>().type("string")
                                .example("must be one of [CRITICAL, HIGH, MEDIUM, LOW]"))
                        .addProperty("rejectedValue", new Schema<>().example("SEVERE"))));
    }
}
