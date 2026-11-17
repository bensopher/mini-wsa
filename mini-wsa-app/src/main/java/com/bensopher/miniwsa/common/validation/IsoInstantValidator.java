package com.bensopher.miniwsa.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class IsoInstantValidator implements ConstraintValidator<IsoInstant, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            // OffsetDateTime (not LocalDateTime) so a timestamp without an offset is rejected (A3).
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
