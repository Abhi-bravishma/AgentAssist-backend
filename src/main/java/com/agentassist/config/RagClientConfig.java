package com.agentassist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration for RAG (Retrieval Augmented Generation) client.
 * Configures WebClient for communicating with the external RAG application.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagClientConfig {

    /**
     * Base URL of the RAG application.
     */
    private String baseUrl = "http://135.222.41.146:8080";

    /**
     * Request timeout in milliseconds.
     * Set higher to accommodate slow LLM responses from Ollama.
     */
    private int timeout = 120000;

    /**
     * Enable/disable RAG integration.
     */
    private boolean enabled = true;

    /**
     * API key for authentication with RAG service.
     */
    private String apiKey = "agent-assist-secret-key-change-in-production";

    /**
     * Company ID for multi-tenant document filtering.
     */
    private Long companyId = 1L;

    /**
     * Creates a WebClient bean configured for RAG API calls.
     */
    @Bean(name = "ragWebClient")
    public WebClient ragWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeout));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader("Authorization", "Bearer " + apiKey)  // Send as JWT too
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
                .build();
    }
}
