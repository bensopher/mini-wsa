package com.bensopher.miniwsa.common;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonConfigTest {

    record Numbers(Long count, Integer code, Double ratio) {
    }

    private final JsonMapper mapper = hardenedMapper(new LimitsProperties(DataSize.ofMegabytes(5),
            new LimitsProperties.Json(5, 100), Duration.ofSeconds(5)));

    @Test
    void acceptsProperlyTypedNumbers() {
        assertThat(mapper.readValue("{\"count\": 123, \"code\": 7, \"ratio\": 1.5}", Numbers.class))
                .isEqualTo(new Numbers(123L, 7, 1.5));
    }

    @Test
    void integersAcceptedForFloatingPointFields() {
        assertThat(mapper.readValue("{\"ratio\": 2}", Numbers.class).ratio()).isEqualTo(2.0);
    }

    @Test
    void rejectsStringsForNumbers() {
        assertThatThrownBy(() -> mapper.readValue("{\"count\": \"123\"}", Numbers.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"count\": \"\"}", Numbers.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"ratio\": \"1.5\"}", Numbers.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void rejectsFloatsForIntegers() {
        assertThatThrownBy(() -> mapper.readValue("{\"count\": 1.5}", Numbers.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"code\": 2.0}", Numbers.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void rejectsNestingBeyondTheLimit() {
        String depth5 = "[[[[[1]]]]]";
        String depth6 = "[[[[[[1]]]]]]";

        assertThat(mapper.readTree(depth5)).isNotNull();
        assertThatThrownBy(() -> mapper.readTree(depth6)).isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void rejectsStringsLongerThanTheLimit() {
        String tooLong = "\"" + "x".repeat(101) + "\"";

        assertThatThrownBy(() -> mapper.readTree(tooLong)).isInstanceOf(StreamConstraintsException.class);
    }

    /** Applies the two customizers the same way Spring Boot does: factory first, then mapper. */
    private static JsonMapper hardenedMapper(LimitsProperties limits) {
        JsonConfig config = new JsonConfig();
        JsonFactoryBuilder factory = JsonFactory.builder();
        config.jsonParsingLimits(limits).customize(factory);
        JsonMapper.Builder builder = JsonMapper.builder(factory.build());
        config.strictScalarCoercion().customize(builder);
        return builder.build();
    }
}
