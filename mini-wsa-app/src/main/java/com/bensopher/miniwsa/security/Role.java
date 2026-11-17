package com.bensopher.miniwsa.security;

/**
 * What an API key may do. Ingest and read are separate so a leaked edge key can't read data and a dashboard
 * key can't poison it.
 */
public enum Role {
    INGEST,
    READ,
    ADMIN;

    /** The Spring Security authority, so rules can use {@code hasRole("INGEST")}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
