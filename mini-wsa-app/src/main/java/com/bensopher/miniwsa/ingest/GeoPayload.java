package com.bensopher.miniwsa.ingest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GeoPayload(
        @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166-1 alpha-2 code, e.g. US") String country,
        @Size(max = 128) String city) {
}
