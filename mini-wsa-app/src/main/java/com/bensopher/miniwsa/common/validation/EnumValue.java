package com.bensopher.miniwsa.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The name of one of the constants of {@link #value()}, case-sensitive ({@code DENY}, not {@code deny}).
 * The error message lists the allowed values. Null is valid.
 */
@Documented
@Constraint(validatedBy = EnumValueValidator.class)
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface EnumValue {

    Class<? extends Enum<?>> value();

    String message() default "must be one of the allowed values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
