package com.bensopher.miniwsa.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatsController {

    private final StatsService service;

    StatsController(StatsService service) {
        this.service = service;
    }

    /** {@code from} and {@code to} are bound as text so that format errors are reported like any other field error. */
    @GetMapping("/v1/stats/summary")
    StatsSummary summary(@RequestParam(required = false) Long configId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return service.summary(configId, from, to);
    }
}
