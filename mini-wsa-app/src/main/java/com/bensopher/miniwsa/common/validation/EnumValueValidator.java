package com.bensopher.miniwsa.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;

public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private List<String> allowed;

    @Override
    public void initialize(EnumValue annotation) {
        allowed = Arrays.stream(annotation.value().getEnumConstants()).map(Enum::name).toList();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || allowed.contains(value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must be one of " + allowed).addConstraintViolation();
        return false;
    }
}
