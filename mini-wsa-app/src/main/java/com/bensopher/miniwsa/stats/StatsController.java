package com.bensopher.miniwsa.stats;

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
@Tag(name = "Stats")
class StatsController {

    private final StatsService service;

    StatsController(StatsService service) {
        this.service = service;
    }

    /** {@code from} and {@code to} are bound as text so that format errors are reported like any other field error. */
    @GetMapping("/v1/stats/summary")
    @Operation(summary = "Summarize events in a time range", description = """
            Role READ. Counts and average threat scores by category, counts by action, and the top 10 attacking \
            IPs and targeted endpoints. All sections come from one database snapshot. The range is half-open \
            [from, to) in event time and may be at most 90 days (A8, A9).""")
    @ApiResponse(responseCode = "200", description = "The summary")
    @ApiResponse(responseCode = "400", description = "Invalid parameter; every problem is listed",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/Problem")))
    StatsSummary summary(
            @Parameter(description = "Only this configuration; omit for all", example = "14227")
            @RequestParam(required = false) Long configId,
            @Parameter(description = "ISO-8601 with an offset; default: to minus 24h", example = "2026-05-20T00:00:00Z")
            @RequestParam(required = false) String from,
            @Parameter(description = "ISO-8601 with an offset, exclusive; default: now", example = "2026-05-21T00:00:00Z")
            @RequestParam(required = false) String to) {
        return service.summary(configId, from, to);
    }
}
