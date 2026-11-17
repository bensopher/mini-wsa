package com.bensopher.miniwsa.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of a problem's {@code errors} array. {@code index} is the position in a batch and is omitted for
 * single-object requests and query parameters; {@code field} is a dotted path such as {@code rule.severity},
 * or the parameter name.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldViolation(Integer index, String field, String message, Object rejectedValue) {
}
