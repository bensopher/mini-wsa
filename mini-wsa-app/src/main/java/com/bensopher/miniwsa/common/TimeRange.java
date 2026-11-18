package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.common.error.FieldViolation;
import com.bensopher.miniwsa.common.error.RequestValidationException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** A half-open range {@code [from, to)} of event time (A8, A9). */
public record TimeRange(Instant from, Instant to) {

    static final String FORMAT_MESSAGE = "must be an ISO-8601 timestamp with offset, e.g. 2026-05-20T14:32:10Z";

    /**
     * Resolves the optional {@code from} and {@code to} query parameters (A8): {@code to} defaults to now and
     * {@code from} to {@code to - defaultRange}. Rejects bad formats, {@code from >= to}, and ranges longer than
     * {@code maxRange}, which bounds how much data one query can scan.
     */
    public static TimeRange resolve(String from, String to, Instant now, Duration defaultRange, Duration maxRange) {
        List<FieldViolation> violations = new ArrayList<>();
        Instant parsedFrom = parse("from", from, violations);
        Instant parsedTo = parse("to", to, violations);
        if (!violations.isEmpty()) {
            throw invalid(violations);
        }
        // The default end is echoed in the response, so it gets the same millisecond precision as receivedAt
        // rather than the system clock's nanoseconds.
        Instant end = parsedTo != null ? parsedTo : now.truncatedTo(ChronoUnit.MILLIS);
        Instant start = parsedFrom != null ? parsedFrom : end.minus(defaultRange);
        if (!start.isBefore(end)) {
            throw invalid(List.of(new FieldViolation(null, "from", "must be before 'to'", from)));
        }
        if (Duration.between(start, end).compareTo(maxRange) > 0) {
            throw invalid(List.of(new FieldViolation(null, "from",
                    "must be at most " + describe(maxRange) + " before 'to'; narrow the time range", from)));
        }
        return new TimeRange(start, end);
    }

    private static Instant parse(String name, String value, List<FieldViolation> violations) {
        if (value == null) {
            return null;
        }
        try {
            // OffsetDateTime (not LocalDateTime) so a timestamp without an offset is rejected. Postgres keeps
            // microseconds, so finer digits are dropped here rather than rounded by the driver.
            return OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException e) {
            violations.add(new FieldViolation(null, name, FORMAT_MESSAGE, value));
            return null;
        }
    }

    private static RequestValidationException invalid(List<FieldViolation> violations) {
        return new RequestValidationException("Invalid query parameters", violations);
    }

    private static String describe(Duration duration) {
        return duration.toSeconds() % 86_400 == 0 ? duration.toDays() + " days" : duration.toString();
    }
}
