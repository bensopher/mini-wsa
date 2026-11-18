package com.bensopher.miniwsa.common;

import com.bensopher.miniwsa.common.error.FieldViolation;
import com.bensopher.miniwsa.common.error.RequestValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TimeRangeTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:15:30.123Z");
    private static final Duration DEFAULT = Duration.ofHours(24);
    private static final Duration MAX = Duration.ofDays(90);

    @Test
    void withNeitherBoundItIsTheLast24Hours() {
        assertThat(resolve(null, null))
                .isEqualTo(new TimeRange(Instant.parse("2026-11-17T10:15:30.123Z"), NOW));
    }

    @Test
    void theDefaultEndIsNowToTheMillisecond() {
        Instant preciseNow = Instant.parse("2026-11-18T10:15:30.123456789Z");

        assertThat(TimeRange.resolve(null, null, preciseNow, DEFAULT, MAX))
                .isEqualTo(new TimeRange(Instant.parse("2026-11-17T10:15:30.123Z"), NOW));
    }

    @Test
    void withOnlyFromItRunsUntilNow() {
        assertThat(resolve("2026-11-01T00:00:00Z", null))
                .isEqualTo(new TimeRange(Instant.parse("2026-11-01T00:00:00Z"), NOW));
    }

    @Test
    void withOnlyToItCovers24HoursBeforeIt() {
        assertThat(resolve(null, "2026-05-21T00:00:00Z"))
                .isEqualTo(new TimeRange(Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-05-21T00:00:00Z")));
    }

    @Test
    void offsetsAreNormalizedToUtcAndSubMicrosecondDigitsDropped() {
        assertThat(resolve("2026-05-20T16:00:00+02:00", "2026-05-20T15:00:00.123456789Z"))
                .isEqualTo(new TimeRange(Instant.parse("2026-05-20T14:00:00Z"),
                        Instant.parse("2026-05-20T15:00:00.123456Z")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-05-20T14:00:00", "2026-05-20", "yesterday", "1747751530"})
    void badFormatsAreRejected(String value) {
        assertThat(violations(value, null))
                .containsExactly(new FieldViolation(null, "from", TimeRange.FORMAT_MESSAGE, value));
    }

    @Test
    void bothBadBoundsAreReportedTogether() {
        assertThat(violations("x", "y")).extracting(FieldViolation::field).containsExactly("from", "to");
    }

    @Test
    void fromMustBeBeforeTo() {
        assertThat(violations("2026-05-20T14:00:00Z", "2026-05-20T14:00:00Z"))
                .containsExactly(new FieldViolation(null, "from", "must be before 'to'", "2026-05-20T14:00:00Z"));
        assertThat(violations("2026-12-01T00:00:00Z", null)).extracting(FieldViolation::field).containsExactly("from");
    }

    @Test
    void theRangeMayBeAtMost90Days() {
        assertThat(resolve("2026-01-01T00:00:00Z", "2026-04-01T00:00:00Z").to())
                .isEqualTo(Instant.parse("2026-04-01T00:00:00Z")); // exactly 90 days

        assertThat(violations("2026-01-01T00:00:00Z", "2026-04-01T00:00:00.000001Z"))
                .containsExactly(new FieldViolation(null, "from",
                        "must be at most 90 days before 'to'; narrow the time range", "2026-01-01T00:00:00Z"));
    }

    private static TimeRange resolve(String from, String to) {
        return TimeRange.resolve(from, to, NOW, DEFAULT, MAX);
    }

    private static List<FieldViolation> violations(String from, String to) {
        RequestValidationException ex = catchThrowableOfType(RequestValidationException.class, () -> resolve(from, to));
        assertThat(ex).hasMessage("Invalid query parameters");
        return ex.violations();
    }
}
