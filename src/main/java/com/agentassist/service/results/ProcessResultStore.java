package com.agentassist.service.results;

import com.agentassist.dto.responseDTO.ConversationResponse;
import com.agentassist.model.ProcessResultEntity;
import com.agentassist.model.ProcessResultEntity.Status;
import com.agentassist.repository.ProcessResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persists a processing result piece by piece.
 *
 * <p>Every method is its own short transaction on purpose. The pipeline thread
 * is not transactional (see {@code ConversationProcessingService#processMessage}),
 * and each write here should hold a connection for milliseconds, not for the
 * seconds the surrounding LLM calls take.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessResultStore {

    private final ProcessResultRepository repo;
    private final ObjectMapper mapper;

    @Transactional
    public void start(String processId, String interactionId) {
        repo.save(ProcessResultEntity.builder()
                .processId(processId)
                .interactionId(interactionId)
                .status(Status.PROCESSING)
                .build());
    }

    @Transactional
    public void attachMessage(String processId, Long messageId) {
        repo.findByProcessId(processId).ifPresent(r -> {
            r.setMessageId(messageId);
            repo.save(r);
        });
    }

    @Transactional
    public void saveSentiment(String processId, Double overall, Double current, String label) {
        repo.findByProcessId(processId).ifPresent(r -> {
            r.setOverallSentiment(overall);
            r.setCurrentSentiment(current);
            r.setSentimentLabel(label);
            repo.save(r);
        });
    }

    @Transactional
    public void saveSummary(String processId, String summary) {
        repo.findByProcessId(processId).ifPresent(r -> {
            r.setSummary(summary);
            repo.save(r);
        });
    }

    @Transactional
    public void saveSuggestions(String processId, Object suggestions, Object knowledgeSources,
                                int documentsFound, boolean usedKnowledgeBase) {
        repo.findByProcessId(processId).ifPresent(r -> {
            r.setSuggestionsJson(toJson(suggestions));
            r.setKnowledgeSourcesJson(toJson(knowledgeSources));
            r.setDocumentsFound(documentsFound);
            r.setUsedKnowledgeBase(usedKnowledgeBase);
            repo.save(r);
        });
    }

    @Transactional
    public void complete(String processId, ConversationResponse response) {
        repo.findByProcessId(processId).ifPresent(r -> {
            r.setResponseJson(toJson(response));
            r.setStatus(Status.DONE);
            repo.save(r);
        });
    }

    @Transactional
    public void fail(String processId, String error) {
        repo.findByProcessId(processId).ifPresent(r -> {
            r.setError(error);
            r.setStatus(Status.FAILED);
            repo.save(r);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ProcessResultEntity> find(String processId) {
        return repo.findByProcessId(processId);
    }

    @Transactional(readOnly = true)
    public Optional<ProcessResultEntity> latest(String interactionId) {
        return repo.findTopByInteractionIdOrderByCreatedAtDesc(interactionId);
    }

    /** Stored JSON back as a tree, so endpoints return exactly what was written. */
    public JsonNode json(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(stored);
        } catch (JsonProcessingException e) {
            log.warn("[Results] Stored JSON unreadable: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[Results] Could not serialise {}: {}", value.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
