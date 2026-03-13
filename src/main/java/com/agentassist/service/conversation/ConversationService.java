package com.agentassist.service.conversation;

import com.agentassist.model.Conversation;
import com.agentassist.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository repo;

    public Conversation getOrCreate(String interactionId) {
        return repo.findByInteractionId(interactionId)
                .orElseGet(() -> {
                    try {
                        Conversation c = Conversation.builder()
                                .interactionId(interactionId)
                                .createdAt(Instant.now())
                                .build();
                        return repo.save(c);
                    } catch (DataIntegrityViolationException e) {
                        // Race condition: another thread created it first, fetch it
                        log.debug("Concurrent conversation creation, fetching existing: {}", interactionId);
                        return repo.findByInteractionId(interactionId)
                                .orElseThrow(() -> new RuntimeException("Conversation not found after race condition: " + interactionId));
                    }
                });
    }

    public void setBaseLanguage(String interactionId, String lang) {
        var conv = getOrCreate(interactionId);
        conv.setBaseLanguage(lang);
        conv.setUpdatedAt(Instant.now());
        repo.save(conv);
    }

    public String getBaseLanguage(String interactionId) {
        return repo.findByInteractionId(interactionId).map(Conversation::getBaseLanguage).orElse(null);
    }
}
