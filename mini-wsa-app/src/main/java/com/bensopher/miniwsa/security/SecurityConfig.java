package com.bensopher.miniwsa.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticator authenticator,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            MeterRegistry meterRegistry) {
        SecurityProblemHandler problems = new SecurityProblemHandler(exceptionResolver, meterRegistry);
        // Created here, not as a @Component: a Filter bean would also be registered on the servlet
        // container and run twice.
        ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter(authenticator, problems);

        return http
                // Stateless API with no cookies or browser sessions, so CSRF doesn't apply.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .authorizeHttpRequests(auth -> auth
                        // Security also runs on the servlet container's error dispatch. Without this, a
                        // container-level 400/404/413 would be masked as a 401/403.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/events/ingest").hasRole(Role.INGEST.name())
                        .requestMatchers(HttpMethod.GET, "/v1/stats/**", "/v1/events/samples")
                        .hasRole(Role.READ.name())
                        .requestMatchers("/actuator/info", "/actuator/metrics/**").hasRole(Role.ADMIN.name())
                        .anyRequest().denyAll())
                .build();
    }
}
