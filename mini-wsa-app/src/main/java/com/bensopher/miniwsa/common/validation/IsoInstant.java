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

/** An ISO-8601 date-time with an explicit offset ({@code Z} or {@code +02:00}) (A3). Null is valid. */
@Documented
@Constraint(validatedBy = IsoInstantValidator.class)
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface IsoInstant {

    String message() default "must be an ISO-8601 timestamp with offset, e.g. 2026-05-20T14:32:10Z";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
