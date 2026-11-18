package com.bensopher.miniwsa.ingest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Tag(name = "Ingest")
class IngestController {

    private final IngestService service;

    IngestController(IngestService service) {
        this.service = service;
    }

    /**
     * Accepts one event object or an array of events. The body is bound as a tree, not as EventPayload, so
     * each element can be converted separately and a type error reported with its index.
     */
    @PostMapping(path = "/v1/events/ingest", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest one event or a batch of up to 1000", description = """
            Role INGEST. All-or-nothing: if any event is invalid, nothing is stored and every error is listed with \
            its index (A1). An event already stored (same eventId and timestamp) is reported as a duplicate and \
            not stored again, so retries are safe (A4). 201 means the events are committed.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
            schema = @Schema(oneOf = {EventPayload.class, EventPayloadArray.class}),
            examples = {
                    @ExampleObject(name = "single", summary = "One event", value = SINGLE_EVENT),
                    @ExampleObject(name = "batch", summary = "A batch", value = "[" + SINGLE_EVENT + "]")}))
    @ApiResponse(responseCode = "201", description = "Stored; per-event results in request order")
    @ApiResponse(responseCode = "400", description = "Invalid events (nothing stored) or malformed JSON",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/Problem")))
    @ApiResponse(responseCode = "413", description = "More than 1000 events, or a body over 5 MB",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/Problem")))
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/Problem")))
    IngestResponse ingest(@RequestBody JsonNode body) {
        return service.ingest(body);
    }

    /** Only describes "an array of events" for the OpenAPI document. */
    @ArraySchema(schema = @Schema(implementation = EventPayload.class), maxItems = 1000)
    private interface EventPayloadArray {
    }

    private static final String SINGLE_EVENT = """
            {"eventId": "evt-00132", "timestamp": "2026-05-20T14:32:10Z", "configId": 14227, \
            "policyId": "pol_web1", "clientIp": "203.0.113.42", "hostname": "www.example.com", \
            "path": "/api/v1/login", "method": "POST", "statusCode": 403, "userAgent": "Mozilla/5.0", \
            "rule": {"id": "950001", "name": "SQL_INJECTION", "message": "SQL Injection Attack Detected", \
            "severity": "CRITICAL", "category": "INJECTION"}, "action": "DENY", \
            "geoLocation": {"country": "CN", "city": "Beijing"}, "requestSize": 1024, "responseSize": 256}""";
}
