package com.bensopher.miniwsa.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;

/** API keys come only from configuration (env placeholders); there are no keys in code. */
@Validated
@ConfigurationProperties("miniwsa.security")
public record SecurityProperties(@NotEmpty List<@Valid ApiKey> apiKeys) {

    /** One client. {@code name} identifies it in logs; the key itself is never logged or printed. */
    public record ApiKey(@NotBlank String name, @NotBlank String key, @NotEmpty Set<Role> roles) {

        @Override
        public String toString() {
            return "ApiKey[name=" + name + ", key=****, roles=" + roles + "]";
        }
    }
}
