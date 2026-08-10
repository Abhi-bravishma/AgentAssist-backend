package com.agentassist.service.conversation;

import com.agentassist.model.MessageEntity;
import com.agentassist.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository repo;

    @Transactional
    public MessageEntity save(MessageEntity m) {
        return repo.save(m);
    }

    public List<MessageEntity> fetchByInteraction(String interactionId) {
        return repo.findByInteractionIdOrderByCreatedAtAsc(interactionId);
    }
}
