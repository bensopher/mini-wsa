package com.bensopher.miniwsa;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class TestClockConfig {

    /** Replaces the system clock wherever the application injects a {@code Clock}. */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(AbstractIntegrationTest.NOW);
    }
}
