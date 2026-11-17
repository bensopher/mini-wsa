package com.bensopher.miniwsa.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests that carry an {@code X-API-Key} header. Without the header the request continues
 * anonymously, and the authorization rules decide (public endpoints pass, everything else gets a 401).
 * A wrong key is rejected here with a 401.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyAuthenticator authenticator;
    private final AuthenticationEntryPoint entryPoint;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public ApiKeyAuthenticationFilter(ApiKeyAuthenticator authenticator, AuthenticationEntryPoint entryPoint) {
        this.authenticator = authenticator;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presentedKey = request.getHeader(HEADER);
        if (presentedKey == null) {
            chain.doFilter(request, response);
            return;
        }
        var client = authenticator.authenticate(presentedKey);
        if (client.isEmpty()) {
            entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
            return;
        }
        var authorities = client.get().roles().stream().map(r -> new SimpleGrantedAuthority(r.authority())).toList();
        SecurityContext context = contextHolder.createEmptyContext();
        // The principal is the key's configured name, so logs identify the client without exposing the key.
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(client.get().name(), null, authorities));
        contextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
