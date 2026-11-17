package com.bensopher.miniwsa.common;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class WebConfig {

    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(LimitsProperties limits,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        var registration = new FilterRegistrationBean<>(
                new RequestBodyLimitFilter(limits.maxRequestBody().toBytes(), exceptionResolver));
        // First in the chain (Spring Security's filter runs at -100), so an oversized body is rejected
        // before authentication or anything else does work on it.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
