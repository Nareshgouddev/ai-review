package com.uireview.config;

import com.uireview.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate-limiting interceptor backed by Bucket4j token buckets.
 * Each distinct IP address gets its own bucket with capacity = requestsPerMinute
 * tokens, refilled at the same rate every 60 seconds.
 *
 * Requirements: 8.1, 8.3
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded for IP: " + ip, 60L);
        }
        return true;
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; take the first value
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
