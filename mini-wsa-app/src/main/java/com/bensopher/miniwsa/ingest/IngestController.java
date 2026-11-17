package com.bensopher.miniwsa.ingest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
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
    IngestResponse ingest(@RequestBody JsonNode body) {
        return service.ingest(body);
    }
}
