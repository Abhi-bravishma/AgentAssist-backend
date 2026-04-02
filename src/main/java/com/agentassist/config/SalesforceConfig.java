package com.agentassist.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.time.Duration;

/**
 * Configuration for Salesforce API client.
 * Used for fetching customer policy and claims data.
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "salesforce")
public class SalesforceConfig {

    /**
     * Base URL of the Salesforce API.
     */
    private String baseUrl = "https://salesforce.lab.bravishma.com";

    /**
     * Request timeout in milliseconds.
     */
    private int timeout = 30000;

    /**
     * Enable/disable Salesforce integration.
     */
    private boolean enabled = true;

    /**
     * Skip SSL verification (for lab/dev environments with self-signed certs).
     */
    private boolean skipSslVerification = true;

    /**
     * Creates a WebClient bean configured for Salesforce API calls.
     */
    @Bean(name = "salesforceWebClient")
    public WebClient salesforceWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeout));

        // Skip SSL verification for lab environments with self-signed certificates
        if (skipSslVerification) {
            try {
                SslContext sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));
                log.warn("Salesforce WebClient: SSL verification DISABLED (lab/dev mode)");
            } catch (SSLException e) {
                log.error("Failed to configure insecure SSL context: {}", e.getMessage());
            }
        }

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "*/*")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(5 * 1024 * 1024)) // 5MB buffer
                .build();
    }
}
