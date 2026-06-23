package com.uireview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link RateLimitInterceptor} for the analyze endpoint only.
 *
 * Requirements: 8.1, 8.3
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    @Value("${rate-limit.requests-per-minute:10}")
    private int requestsPerMinute;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(requestsPerMinute))
                .addPathPatterns("/api/v1/analyze");
    }
}
