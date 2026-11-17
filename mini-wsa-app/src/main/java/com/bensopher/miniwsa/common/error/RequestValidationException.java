package com.bensopher.miniwsa.common.error;

import java.util.List;

/** A request that failed validation. Rendered as a 400 problem listing every violation. */
public class RequestValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public RequestValidationException(String detail, List<FieldViolation> violations) {
        super(detail);
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
