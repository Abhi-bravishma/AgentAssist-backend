package com.agentassist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Avaya Infinity integration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "avaya")
public class AvayaConfig {

    /**
     * Base URL for Avaya API (e.g., https://core.showmeavayacom.ec.avayacloud.com)
     */
    private String baseUrl;

    /**
     * OAuth client ID for Avaya API authentication.
     */
    private String clientId;

    /**
     * OAuth client secret for Avaya API authentication.
     */
    private String clientSecret;

    /**
     * Timeout in milliseconds for Avaya API calls.
     */
    private int timeout = 30000;
}
