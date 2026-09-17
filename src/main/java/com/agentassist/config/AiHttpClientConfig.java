package com.agentassist.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Connect/read timeouts for the OpenAI calls.
 *
 * <p>Spring AI exposes no properties for this — the builder has to be handed in
 * configured (spring-projects/spring-ai#354, #5400). Left alone the request
 * inherits NO read timeout, so a connection that stalls mid-response pins a
 * Tomcat thread indefinitely: nginx gives up at 60s, the browser leaves, and the
 * thread stays put holding whatever it holds. Retries multiply it.
 *
 * <p>This customizes the auto-configured {@code RestClient.Builder} rather than
 * replacing it, and Spring AI is the only thing consuming RestClient in this
 * app — Salesforce, Avaya and the legacy RAG client are on WebClient with their
 * own timeouts and are untouched by this.</p>
 */
@Configuration
public class AiHttpClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Generous enough for a slow completion, short enough to free the thread. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    public RestClientCustomizer aiRestClientTimeouts() {
        return builder -> builder.requestFactory(
                ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT)));
    }
}
