package com.uireview.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Anthropic / Claude API configuration.
 *
 * <p>Reads all Anthropic-related properties from {@code application.properties}
 * (which in turn fall back to the {@code ANTHROPIC_API_KEY} environment variable).
 * A {@link PostConstruct} lifecycle hook validates the key at startup so the
 * application fails fast with a clear message rather than producing a cryptic
 * runtime error on the first API call (Requirement 11.4).
 */
@Configuration
public class AnthropicConfig {

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Value("${anthropic.model:claude-sonnet-4-5}")
    private String model;

    @Value("${anthropic.max-tokens:4096}")
    private int maxTokens;

    @Value("${anthropic.timeout-ms:12000}")
    private long timeoutMs;

    /**
     * Validates that the Anthropic API key is present and non-blank.
     * Throws {@link IllegalStateException} to halt startup if the key is missing.
     */
    @PostConstruct
    public void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY environment variable is not set or is blank. " +
                    "The application cannot start without a valid Anthropic API key.");
        }
    }

    @Bean(name = "anthropicApiKey")
    public String anthropicApiKey() {
        return apiKey;
    }

    @Bean(name = "anthropicModel")
    public String anthropicModel() {
        return model;
    }

    @Bean(name = "anthropicMaxTokens")
    public int anthropicMaxTokens() {
        return maxTokens;
    }

    @Bean(name = "anthropicTimeoutMs")
    public long anthropicTimeoutMs() {
        return timeoutMs;
    }
}
