package com.agentassist.service.conversation;

import com.agentassist.model.Conversation;
import com.agentassist.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Creates conversations in a separate transaction so a unique-constraint
 * failure (concurrent creation) does not poison the caller's Hibernate session.
 */
@Service
@RequiredArgsConstructor
public class ConversationCreator {

    private final ConversationRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation createNew(String interactionId) {
        Conversation c = Conversation.builder()
                .interactionId(interactionId)
                .createdAt(Instant.now())
                .build();
        return repo.save(c);
    }
}
