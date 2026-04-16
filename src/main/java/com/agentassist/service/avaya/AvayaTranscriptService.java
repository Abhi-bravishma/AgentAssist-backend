package com.agentassist.service.avaya;

import com.agentassist.config.AvayaConfig;
import com.agentassist.dto.avaya.AvayaMessage;
import com.agentassist.dto.avaya.AvayaTranscriptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for fetching transcripts from Avaya Infinity API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvayaTranscriptService {

    private final AvayaConfig avayaConfig;
    private final AvayaTokenService tokenService;
    private final WebClient.Builder webClientBuilder;

    /**
     * Fetch transcript messages for an interaction.
     *
     * @param interactionId The interaction ID or workflow session ID
     * @return List of all messages in the transcript
     */
    public List<AvayaMessage> fetchTranscript(String interactionId) {
        log.info("[Avaya] Fetching transcript for interaction: {}", interactionId);
        long startTime = System.currentTimeMillis();

        try {
            String token = tokenService.getAccessToken();
            String url = avayaConfig.getBaseUrl() + "/api/transcripts/v1/transcripts/" + interactionId;

            WebClient client = webClientBuilder.build();

            AvayaTranscriptResponse response = client.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        log.error("[Avaya] Client error fetching transcript: {}", clientResponse.statusCode());
                        return Mono.error(new RuntimeException("Avaya API client error: " + clientResponse.statusCode()));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        // Note: Avaya returns 500 for non-existent interaction IDs
                        log.warn("[Avaya] Server error (may indicate non-existent interaction): {}", clientResponse.statusCode());
                        return Mono.error(new RuntimeException("Avaya API server error (interaction may not exist): " + clientResponse.statusCode()));
                    })
                    .bodyToMono(AvayaTranscriptResponse.class)
                    .timeout(Duration.ofMillis(avayaConfig.getTimeout()))
                    .block();

            if (response == null || response.getMessages() == null) {
                log.warn("[Avaya] No messages found for interaction: {}", interactionId);
                return Collections.emptyList();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Avaya] Fetched {} messages for interaction {} in {}ms",
                    response.getMessages().size(), interactionId, duration);

            return response.getMessages();

        } catch (Exception e) {
            log.error("[Avaya] Failed to fetch transcript for {}: {} - {}",
                    interactionId, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Failed to fetch Avaya transcript: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch only agent messages from a transcript.
     * Agent messages have direction="out" and author.type="user"
     *
     * @param interactionId The interaction ID
     * @return List of agent messages only
     */
    public List<AvayaMessage> fetchAgentMessages(String interactionId) {
        List<AvayaMessage> allMessages = fetchTranscript(interactionId);

        List<AvayaMessage> agentMessages = allMessages.stream()
                .filter(AvayaMessage::isAgentMessage)
                .collect(Collectors.toList());

        log.info("[Avaya] Filtered {} agent messages from {} total messages",
                agentMessages.size(), allMessages.size());

        return agentMessages;
    }

    /**
     * Get agent message texts from a transcript.
     *
     * @param interactionId The interaction ID
     * @return List of agent message text contents
     */
    public List<String> fetchAgentMessageTexts(String interactionId) {
        return fetchAgentMessages(interactionId).stream()
                .map(AvayaMessage::getTextContent)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.toList());
    }
}
