package com.shipspace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient used in external API integrations.
 * Provides a WebClient.Builder bean for GitHub OAuth and other external services.
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates a WebClient.Builder bean for dependency injection.
     * This builder can be customized per service as needed.
     *
     * @return a WebClient.Builder instance
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
