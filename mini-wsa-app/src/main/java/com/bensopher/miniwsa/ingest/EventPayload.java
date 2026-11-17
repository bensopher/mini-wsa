package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.common.validation.EnumValue;
import com.bensopher.miniwsa.common.validation.IpAddress;
import com.bensopher.miniwsa.common.validation.IsoInstant;
import com.bensopher.miniwsa.event.Action;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One event as received on the wire. Enums and the timestamp stay {@code String} here so validation can
 * report a precise, friendly error instead of a Jackson type-mismatch message. Required fields follow A2.
 */
public record EventPayload(
        @NotBlank @Size(max = 128) String eventId,
        @NotNull @IsoInstant String timestamp,
        @NotNull @Positive Long configId,
        @Size(max = 128) String policyId,
        @NotNull @IpAddress String clientIp,
        @Size(max = 253) String hostname,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "/.*", flags = Pattern.Flag.DOTALL, message = "must start with /")
        String path,
        // Any HTTP token, not just the standard verbs: attack traffic uses odd methods.
        @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9!#$%&'*+.^_`|~-]+", message = "must be an HTTP method token")
        String method,
        @Min(100) @Max(599) Integer statusCode,
        @Size(max = 1024) String userAgent,
        @NotNull @Valid RulePayload rule,
        @NotNull @EnumValue(Action.class) String action,
        @Valid GeoPayload geoLocation,
        @PositiveOrZero Long requestSize,
        @PositiveOrZero Long responseSize) {
}
