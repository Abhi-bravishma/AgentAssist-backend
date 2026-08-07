package com.agentassist.service.compliance;

import com.agentassist.ai.AiProviderFactory;
import com.agentassist.dto.responseDTO.ComplianceResponse;
import com.agentassist.model.Compliance;
import com.agentassist.repository.ComplianceRepository;
import com.agentassist.service.avaya.AvayaTranscriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for performing compliance checks on agent conversations.
 * Orchestrates: Avaya transcript fetch → AI analysis → DB storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final AvayaTranscriptService transcriptService;
    private final AiProviderFactory aiProviderFactory;
    private final ComplianceRepository complianceRepository;

    /**
     * Perform a compliance check on an interaction.
     * Fetches transcript from Avaya, analyzes agent messages, and stores results.
     *
     * @param interactionId The Avaya interaction ID
     * @return Compliance check results
     */
    public ComplianceResponse performComplianceCheck(String interactionId) {
        log.info("[Compliance] Starting compliance check for interaction: {}", interactionId);
        long startTime = System.currentTimeMillis();

        try {
            // 1. Fetch agent messages from Avaya transcript
            List<String> agentMessages = transcriptService.fetchAgentMessageTexts(interactionId);

            if (agentMessages.isEmpty()) {
                log.warn("[Compliance] No agent messages found for interaction: {}", interactionId);
                return createEmptyResponse(interactionId);
            }

            log.info("[Compliance] Found {} agent messages for analysis", agentMessages.size());

            // 2. Analyze compliance using AI (Ollama)
            ComplianceResponse response = aiProviderFactory.active().analyzeCompliance(agentMessages, interactionId);

            // 3. Save results to database
            saveCompliance(response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Compliance] Completed compliance check for {} in {}ms", interactionId, duration);

            return response;

        } catch (Exception e) {
            log.error("[Compliance] Failed for interaction {}: {} - {}",
                    interactionId, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Compliance check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest compliance check results for an interaction from the database.
     *
     * @param interactionId The interaction ID
     * @return Optional containing the compliance check if found
     */
    public Optional<ComplianceResponse> getLatestComplianceCheck(String interactionId) {
        return complianceRepository.findTopByInteractionIdOrderByCheckedAtDesc(interactionId)
                .map(this::entityToResponse);
    }

    /**
     * Perform compliance check only if not already done for this interaction.
     * Returns cached result if available.
     *
     * @param interactionId The interaction ID
     * @return Compliance check results (cached or new)
     */
    public ComplianceResponse performOrGetComplianceCheck(String interactionId) {
        Optional<ComplianceResponse> existing = getLatestComplianceCheck(interactionId);
        if (existing.isPresent()) {
            log.info("[Compliance] Returning cached compliance check for: {}", interactionId);
            return existing.get();
        }
        return performComplianceCheck(interactionId);
    }

    /**
     * Save compliance check results to database.
     */
    private void saveCompliance(ComplianceResponse response) {
        Compliance entity = Compliance.builder()
                .interactionId(response.getInteractionId())
                .greeting(response.getGreeting())
                .empathy(response.getEmpathy())
                .clarity(response.getClarity())
                .productTnC(response.getProductTnC())
                .valediction(response.getValediction())
                .checkedAt(response.getCheckedAt() != null ? response.getCheckedAt() : Instant.now())
                .build();

        complianceRepository.save(entity);
        log.info("[Compliance] Saved compliance check to database for: {}", response.getInteractionId());
    }

    /**
     * Convert entity to response DTO.
     */
    private ComplianceResponse entityToResponse(Compliance entity) {
        return ComplianceResponse.builder()
                .interactionId(entity.getInteractionId())
                .greeting(entity.getGreeting())
                .empathy(entity.getEmpathy())
                .clarity(entity.getClarity())
                .productTnC(entity.getProductTnC())
                .valediction(entity.getValediction())
                .checkedAt(entity.getCheckedAt())
                .build();
    }

    /**
     * Create an empty response when no agent messages are found.
     */
    private ComplianceResponse createEmptyResponse(String interactionId) {
        return ComplianceResponse.builder()
                .interactionId(interactionId)
                .greeting(false)
                .empathy(false)
                .clarity(false)
                .productTnC(false)
                .valediction(false)
                .checkedAt(Instant.now())
                .build();
    }
}
