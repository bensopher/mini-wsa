package com.bensopher.miniwsa.common.error;

import java.net.URI;

/** Stable {@code type} URIs, so clients can branch on the kind of error rather than on its message. */
public final class ProblemTypes {

    private static final String BASE = "https://github.com/bensopher/mini-wsa/errors/";

    public static final URI VALIDATION = URI.create(BASE + "validation");
    public static final URI MALFORMED_JSON = URI.create(BASE + "malformed-json");
    public static final URI PAYLOAD_TOO_LARGE = URI.create(BASE + "payload-too-large");
    public static final URI INTERNAL = URI.create(BASE + "internal");

    private ProblemTypes() {
    }
}
