package com.bensopher.miniwsa.security;

import com.bensopher.miniwsa.security.ApiKeyAuthenticator.ApiClient;
import com.bensopher.miniwsa.security.SecurityProperties.ApiKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthenticatorTest {

    private final ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(new SecurityProperties(List.of(
            new ApiKey("edge-ingest", "ingest-secret", Set.of(Role.INGEST)),
            new ApiKey("soc-admin", "admin-secret", Set.of(Role.READ, Role.ADMIN)))));

    @Test
    void resolvesAKnownKeyToItsClient() {
        assertThat(authenticator.authenticate("admin-secret"))
                .contains(new ApiClient("soc-admin", Set.of(Role.READ, Role.ADMIN)));
    }

    @Test
    void rejectsUnknownKeysPrefixesAndCaseVariants() {
        assertThat(authenticator.authenticate("unknown")).isEmpty();
        assertThat(authenticator.authenticate("ingest-secre")).isEmpty();
        assertThat(authenticator.authenticate("INGEST-SECRET")).isEmpty();
        assertThat(authenticator.authenticate("")).isEmpty();
    }

    @Test
    void keyIsNeverPrinted() {
        ApiKey key = new ApiKey("edge-ingest", "ingest-secret", Set.of(Role.INGEST));

        assertThat(key.toString()).contains("edge-ingest").doesNotContain("ingest-secret");
    }
}
