package com.bensopher.miniwsa.ingest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.bensopher.miniwsa.ingest.EventPayloadBuilder.anEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class EventPayloadValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void sampleEventIsValid() {
        assertThat(violations(b -> { })).isEmpty();
    }

    @Test
    void optionalFieldsMayBeMissing() {
        EventPayload minimal = new EventPayload("e1", "2026-05-20T14:32:10Z", 1L, null, "203.0.113.42", null,
                "/", "GET", null, null, new RulePayload("1", null, null, "LOW", "BOT"), "MONITOR", null, null, null);

        assertThat(VALIDATOR.validate(minimal)).isEmpty();
    }

    @Test
    void reportsEveryMissingRequiredFieldWithItsPath() {
        EventPayload empty = new EventPayload(null, null, null, null, null, null, null, null, null, null,
                new RulePayload(null, null, null, null, null), null, null, null, null);

        assertThat(toMap(VALIDATOR.validate(empty))).containsOnly(
                entry("eventId", "must not be blank"),
                entry("timestamp", "must not be null"),
                entry("configId", "must not be null"),
                entry("clientIp", "must not be null"),
                entry("path", "must not be blank"),
                entry("method", "must not be blank"),
                entry("rule.id", "must not be blank"),
                entry("rule.severity", "must not be null"),
                entry("rule.category", "must not be null"),
                entry("action", "must not be null"));
    }

    @Test
    void ruleIsRequired() {
        assertThat(violations(b -> b.withRule = false)).containsOnly(entry("rule", "must not be null"));
    }

    @Test
    void enumsAreCaseSensitiveAndListAllowedValues() {
        assertThat(violations(b -> b.severity = "SEVERE"))
                .containsOnly(entry("rule.severity", "must be one of [CRITICAL, HIGH, MEDIUM, LOW]"));
        assertThat(violations(b -> b.category = "injection"))
                .containsOnly(entry("rule.category",
                        "must be one of [INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT]"));
        assertThat(violations(b -> b.action = "deny"))
                .containsOnly(entry("action", "must be one of [DENY, ALERT, MONITOR]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-05-20T14:32:10Z", "2026-05-20T16:32:10+02:00", "2026-05-20T14:32:10.123456Z"})
    void acceptsTimestampsWithOffset(String timestamp) {
        assertThat(violations(b -> b.timestamp = timestamp)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-05-20T14:32:10", "20/05/2026", "", "1716215530"})
    void rejectsTimestampsWithoutOffsetOrGarbage(String timestamp) {
        assertThat(violations(b -> b.timestamp = timestamp)).containsOnly(
                entry("timestamp", "must be an ISO-8601 timestamp with offset, e.g. 2026-05-20T14:32:10Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.42", "2001:db8::1", "2001:DB8:0:0:0:0:0:1", "::ffff:192.0.2.1"})
    void acceptsIpv4AndIpv6Literals(String ip) {
        assertThat(violations(b -> b.clientIp = ip)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"999.1.1.1", "010.0.0.1", "1.2.3", "example.com", "localhost", "fe80::1%eth0", "fe80::1%1", ""})
    void rejectsInvalidIpsHostnamesAndZoneIds(String ip) {
        assertThat(violations(b -> b.clientIp = ip)).containsOnly(entry("clientIp", "must be an IPv4 or IPv6 address"));
    }

    @Test
    void pathMustStartWithSlash() {
        assertThat(violations(b -> b.path = "api/v1/login")).containsOnly(entry("path", "must start with /"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PROPFIND", "X-CUSTOM", "get"})
    void acceptsAnyHttpTokenAsMethod(String method) {
        assertThat(violations(b -> b.method = method)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET POST", "GE(T", "GET\n"})
    void rejectsMethodsThatAreNotHttpTokens(String method) {
        assertThat(violations(b -> b.method = method)).containsOnly(entry("method", "must be an HTTP method token"));
    }

    @Test
    void enforcesLengthLimits() {
        assertThat(violations(b -> b.eventId = "e".repeat(129))).containsOnlyKeys("eventId");
        assertThat(violations(b -> b.ruleId = "1".repeat(33))).containsOnlyKeys("rule.id");
        assertThat(violations(b -> b.userAgent = "u".repeat(1025))).containsOnlyKeys("userAgent");
        assertThat(violations(b -> b.method = "M".repeat(17))).containsOnlyKeys("method");
    }

    @Test
    void rejectsOutOfRangeNumbers() {
        assertThat(violations(b -> b.configId = 0L)).containsOnly(entry("configId", "must be greater than 0"));
        assertThat(violations(b -> b.statusCode = 99)).containsOnlyKeys("statusCode");
        assertThat(violations(b -> b.statusCode = 600)).containsOnlyKeys("statusCode");
        assertThat(violations(b -> b.requestSize = -1L))
                .containsOnly(entry("requestSize", "must be greater than or equal to 0"));
        assertThat(violations(b -> b.responseSize = -1L)).containsOnlyKeys("responseSize");
    }

    @Test
    void countryMustBeTwoUppercaseLetters() {
        assertThat(violations(b -> b.country = "cn"))
                .containsOnly(entry("geoLocation.country", "must be an ISO 3166-1 alpha-2 code, e.g. US"));
        assertThat(violations(b -> b.country = "CHN")).containsOnlyKeys("geoLocation.country");
    }

    private static Map<String, String> violations(Consumer<EventPayloadBuilder> change) {
        EventPayloadBuilder builder = anEvent();
        change.accept(builder);
        return toMap(VALIDATOR.validate(builder.build()));
    }

    /** Field path to message. A duplicate path would throw, which also proves each field is checked once. */
    private static Map<String, String> toMap(Set<ConstraintViolation<EventPayload>> violations) {
        return violations.stream()
                .collect(Collectors.toMap(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage));
    }
}
