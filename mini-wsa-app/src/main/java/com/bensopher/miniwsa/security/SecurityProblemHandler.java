package com.bensopher.miniwsa.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 401 and 403 responses. Security rejects requests before they reach Spring MVC, so the exception is handed
 * to MVC's exception resolver: the same {@code @RestControllerAdvice} renders it, in the same problem format
 * as every other error.
 */
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityProblemHandler.class);

    private final HandlerExceptionResolver exceptionResolver;
    private final MeterRegistry meterRegistry;

    public SecurityProblemHandler(HandlerExceptionResolver exceptionResolver, MeterRegistry meterRegistry) {
        this.exceptionResolver = exceptionResolver;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
        String reason = request.getHeader(ApiKeyAuthenticationFilter.HEADER) == null ? "missing" : "invalid";
        reject(reason, request, response, ex);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
        reject("forbidden", request, response, ex);
    }

    private void reject(String reason, HttpServletRequest request, HttpServletResponse response, Exception ex) {
        // Client IP and reason only: never the presented key.
        log.warn("Rejected request: reason={} clientIp={} {} {}",
                reason, request.getRemoteAddr(), request.getMethod(), request.getRequestURI());
        meterRegistry.counter("miniwsa.security.auth.failures", "reason", reason).increment();
        exceptionResolver.resolveException(request, response, null, ex);
    }
}
