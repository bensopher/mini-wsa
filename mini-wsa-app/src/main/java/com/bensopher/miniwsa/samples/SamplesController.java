package com.bensopher.miniwsa.samples;

import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SamplesController {

    private final SamplesService service;

    SamplesController(SamplesService service) {
        this.service = service;
    }

    /** Every parameter is optional; an unknown category or action is a 400 that lists the allowed values. */
    @GetMapping("/v1/events/samples")
    SamplesPage samples(@RequestParam(required = false) Long configId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Action action,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return service.samples(configId, from, to, category, action, limit, offset);
    }
}
