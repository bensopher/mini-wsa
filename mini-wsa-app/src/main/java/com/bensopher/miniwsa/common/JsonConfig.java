package com.bensopher.miniwsa.common;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/** Hardening for the JSON mapper Spring MVC uses to read every request body. */
@Configuration
public class JsonConfig {

    /** Parser limits: deeply nested or huge strings are rejected while parsing, before they cost memory. */
    @Bean
    JsonFactoryBuilderCustomizer jsonParsingLimits(LimitsProperties limits) {
        return factory -> factory.streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(limits.json().maxNestingDepth())
                .maxStringLength(limits.json().maxStringLength())
                .build());
    }

    /**
     * Strict typing: a string is never turned into a number ("123", "") and a float never into an integer
     * (1.5). Lenient coercion would hide malformed producer output instead of reporting it.
     */
    @Bean
    JsonMapperBuilderCustomizer strictScalarCoercion() {
        return mapper -> mapper
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Float, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail));
    }
}
