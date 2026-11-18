package com.bensopher.miniwsa.samples;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Samples")
class SamplesController {

    private final SamplesService service;

    SamplesController(SamplesService service) {
        this.service = service;
    }

    /** Every parameter is optional; an unknown category or action is a 400 that lists the allowed values. */
    @GetMapping("/v1/events/samples")
    @Operation(summary = "Page through stored events, newest first", description = """
            Role READ. Events with their enrichment, ordered by timestamp then eventId (descending), so pages are \
            stable. total counts every match and comes from the same snapshot as the page. Out-of-range limit or \
            offset is rejected, not clamped.""")
    @ApiResponse(responseCode = "200", description = "One page")
    @ApiResponse(responseCode = "400", description = "Invalid parameter; every problem is listed",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/Problem")))
    SamplesPage samples(
            @Parameter(description = "Only this configuration; omit for all", example = "14227")
            @RequestParam(required = false) Long configId,
            @Parameter(description = "ISO-8601 with an offset; default: to minus 24h", example = "2026-05-20T00:00:00Z")
            @RequestParam(required = false) String from,
            @Parameter(description = "ISO-8601 with an offset, exclusive; default: now", example = "2026-05-21T00:00:00Z")
            @RequestParam(required = false) String to,
            @Parameter(description = "Only this category") @RequestParam(required = false) Category category,
            @Parameter(description = "Only this action") @RequestParam(required = false) Action action,
            @Parameter(description = "Page size, 1-100 (default 20)", example = "20")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Items to skip, 0-10000 (default 0)", example = "0")
            @RequestParam(required = false) Integer offset) {
        return service.samples(configId, from, to, category, action, limit, offset);
    }
}
