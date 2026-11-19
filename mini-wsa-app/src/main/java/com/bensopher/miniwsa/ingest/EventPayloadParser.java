package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.common.error.FieldViolation;
import com.bensopher.miniwsa.common.error.RequestValidationException;
import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import com.bensopher.miniwsa.event.GeoLocation;
import com.bensopher.miniwsa.event.Rule;
import com.bensopher.miniwsa.event.SecurityEvent;
import com.bensopher.miniwsa.event.Severity;
import com.google.common.net.InetAddresses;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the JSON elements of an ingest request into validated events. Every element is checked and every
 * problem collected; if there is any, nothing is returned (A1: all-or-nothing).
 */
@Component
class EventPayloadParser {

    private final JsonMapper mapper;
    private final Validator validator;
    private final IngestProperties properties;

    EventPayloadParser(JsonMapper mapper, Validator validator, IngestProperties properties) {
        this.mapper = mapper;
        this.validator = validator;
        this.properties = properties;
    }

    /**
     * @param batch whether the request was an array; violations of a single-object request carry no index
     * @throws RequestValidationException listing every problem in every element
     */
    List<SecurityEvent> parse(List<JsonNode> elements, boolean batch, Instant receivedAt) {
        List<SecurityEvent> events = new ArrayList<>(elements.size());
        List<FieldViolation> violations = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            Integer index = batch ? i : null;
            List<FieldViolation> found = new ArrayList<>();
            SecurityEvent event = parseOne(elements.get(i), index, receivedAt, found);
            if (found.isEmpty()) {
                events.add(event);
            }
            violations.addAll(found);
        }
        if (!violations.isEmpty()) {
            throw new RequestValidationException(detail(violations, elements.size(), batch), violations);
        }
        return events;
    }

    private SecurityEvent parseOne(JsonNode element, Integer index, Instant receivedAt, List<FieldViolation> found) {
        if (!element.isObject()) {
            found.add(new FieldViolation(index, null, "must be a JSON object", null));
            return null;
        }
        EventPayload payload;
        try {
            // Each element is converted on its own, so a type error in element 7 is reported as index 7.
            payload = mapper.treeToValue(element, EventPayload.class);
        } catch (MismatchedInputException e) {
            found.add(typeViolation(element, index, e, expectedType(e.getTargetType())));
            return null;
        } catch (InputCoercionException e) {
            // A number too large for its field, e.g. "configId": 99999999999999999999.
            found.add(typeViolation(element, index, e, "is out of range"));
            return null;
        }
        validator.validate(payload).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> new FieldViolation(index, v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
                .forEach(found::add);
        if (!found.isEmpty()) {
            return null;
        }
        Instant timestamp = parseTimestamp(payload.timestamp());
        Duration skew = properties.maxFutureSkew();
        // A3: reject events from the future, allowing for some clock difference between edge and server.
        if (timestamp.isAfter(receivedAt.plus(skew))) {
            found.add(new FieldViolation(index, "timestamp",
                    "must not be more than " + humanize(skew) + " ahead of the server clock", payload.timestamp()));
            return null;
        }
        return toEvent(payload, timestamp);
    }

    /** The field path and a value Jackson couldn't convert, e.g. {@code "configId": "abc"}. */
    private FieldViolation typeViolation(JsonNode element, Integer index, JacksonException e, String message) {
        StringBuilder field = new StringBuilder();
        StringBuilder pointer = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                field.append(field.isEmpty() ? "" : ".").append(ref.getPropertyName());
                pointer.append('/').append(ref.getPropertyName());
            } else {
                field.append('[').append(ref.getIndex()).append(']');
                pointer.append('/').append(ref.getIndex());
            }
        }
        JsonNode rejected = element.at(pointer.toString());
        Object rejectedValue = rejected.isValueNode() ? mapper.treeToValue(rejected, Object.class) : null;
        return new FieldViolation(index, field.toString(), message, rejectedValue);
    }

    private static String expectedType(Class<?> type) {
        if (type == Long.class || type == Integer.class || type == long.class || type == int.class) {
            return "must be an integer";
        }
        if (type == String.class) {
            return "must be a string";
        }
        return "must be a JSON object";
    }

    private static String detail(List<FieldViolation> violations, int total, boolean batch) {
        if (!batch) {
            return "The event failed validation; nothing was stored";
        }
        long failed = violations.stream().map(FieldViolation::index).distinct().count();
        return failed + " of " + total + " events failed validation; nothing was stored";
    }

    private static Instant parseTimestamp(String value) {
        // Postgres stores microseconds. Truncating here keeps the (eventId, timestamp) key identical before
        // and after storage, so duplicate detection compares like with like.
        return OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    private static SecurityEvent toEvent(EventPayload p, Instant timestamp) {
        RulePayload r = p.rule();
        GeoLocation geo = p.geoLocation() == null ? null : new GeoLocation(p.geoLocation().country(), p.geoLocation().city());
        return new SecurityEvent(p.eventId(), timestamp, p.configId(), p.policyId(),
                // Canonical form (RFC 5952), so "2001:DB8:0::1" and "2001:db8::1" are the same client.
                InetAddresses.toAddrString(InetAddresses.forString(p.clientIp())),
                p.hostname(), p.path(), p.method(), p.statusCode(), p.userAgent(),
                new Rule(r.id(), r.name(), r.message(), Severity.valueOf(r.severity()), Category.valueOf(r.category())),
                Action.valueOf(p.action()), geo, p.requestSize(), p.responseSize());
    }

    private static String humanize(Duration duration) {
        return duration.toSecondsPart() == 0 && duration.toMinutes() > 0
                ? duration.toMinutes() + " minutes"
                : duration.toSeconds() + " seconds";
    }
}
