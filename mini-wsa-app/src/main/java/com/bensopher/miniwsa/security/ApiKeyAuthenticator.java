package com.bensopher.miniwsa.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Resolves a presented API key to the client it belongs to. */
@Component
public class ApiKeyAuthenticator {

    /** The authenticated client: its configured name (safe to log) and roles. */
    public record ApiClient(String name, Set<Role> roles) {
    }

    private record HashedKey(byte[] sha256, ApiClient client) {
    }

    private final List<HashedKey> keys;

    public ApiKeyAuthenticator(SecurityProperties properties) {
        // Only hashes are kept in memory, so a heap dump doesn't reveal the keys.
        this.keys = properties.apiKeys().stream()
                .map(k -> new HashedKey(sha256(k.key()), new ApiClient(k.name(), Set.copyOf(k.roles()))))
                .toList();
    }

    public Optional<ApiClient> authenticate(String presentedKey) {
        byte[] presented = sha256(presentedKey);
        ApiClient match = null;
        // Compare against every key without stopping early, and compare equal-length hashes with
        // MessageDigest.isEqual (constant time), so response timing reveals nothing about the keys.
        for (HashedKey key : keys) {
            if (MessageDigest.isEqual(key.sha256(), presented)) {
                match = key.client();
            }
        }
        return Optional.ofNullable(match);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
