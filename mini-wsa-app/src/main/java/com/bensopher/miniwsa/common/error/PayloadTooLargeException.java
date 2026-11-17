package com.bensopher.miniwsa.common.error;

/** A request body or batch over its configured limit. Rendered as a 413 problem. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String detail) {
        super(detail);
    }
}
