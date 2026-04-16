package com.agentassist.service.avaya;

import com.agentassist.config.AvayaConfig;
import com.agentassist.dto.avaya.AvayaTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for managing Avaya OAuth tokens with caching.
 * Tokens expire in 15 minutes (900 seconds).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvayaTokenService {

    private final AvayaConfig avayaConfig;
    private final WebClient.Builder webClientBuilder;

    // Cached token
    private volatile String cachedToken;
    private volatile Instant tokenExpiry;

    // Refresh token 60 seconds before expiry
    private static final int EXPIRY_BUFFER_SECONDS = 60;

    /**
     * Get a valid access token, fetching a new one if necessary.
     *
     * @return Bearer token string
     * @throws RuntimeException if token fetch fails
     */
    public String getAccessToken() {
        // Check if we have a valid cached token
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            log.debug("[Avaya] Using cached token, expires at {}", tokenExpiry);
            return cachedToken;
        }

        // Fetch new token
        return fetchNewToken();
    }

    /**
     * Fetch a new access token from Avaya OAuth endpoint.
     */
    private synchronized String fetchNewToken() {
        // Double-check in case another thread already refreshed
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        log.info("[Avaya] Fetching new access token from {}", avayaConfig.getBaseUrl());
        long startTime = System.currentTimeMillis();

        try {
            String tokenUrl = avayaConfig.getBaseUrl() + "/auth/realms/avaya/protocol/openid-connect/token";

            WebClient client = webClientBuilder
                    .baseUrl(tokenUrl)
                    .build();

            AvayaTokenResponse response = client.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("grant_type", "client_credentials")
                            .with("client_id", avayaConfig.getClientId())
                            .with("client_secret", avayaConfig.getClientSecret()))
                    .retrieve()
                    .bodyToMono(AvayaTokenResponse.class)
                    .timeout(Duration.ofMillis(avayaConfig.getTimeout()))
                    .block();

            if (response == null || response.getAccessToken() == null) {
                throw new RuntimeException("Avaya token response is null or empty");
            }

            // Cache the token with expiry buffer
            cachedToken = response.getAccessToken();
            int expiresIn = response.getExpiresIn() > 0 ? response.getExpiresIn() : 900;
            tokenExpiry = Instant.now().plusSeconds(expiresIn - EXPIRY_BUFFER_SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Avaya] Token fetched successfully in {}ms, expires in {}s", duration, expiresIn);

            return cachedToken;

        } catch (Exception e) {
            log.error("[Avaya] Failed to fetch access token: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Failed to fetch Avaya access token: " + e.getMessage(), e);
        }
    }

    /**
     * Invalidate the cached token (useful for retry scenarios).
     */
    public void invalidateToken() {
        log.info("[Avaya] Token invalidated");
        cachedToken = null;
        tokenExpiry = null;
    }
}
