package com.agentassist.service.conversation;

import com.agentassist.dto.responseDTO.ConversationSummaryResponse;
import com.agentassist.model.MessageEntity;
import com.agentassist.model.SenderType;
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

    public List<MessageEntity> fetchUserMessages(String interactionId) {
        return repo.findByInteractionIdAndSenderOrderByCreatedAtAsc(interactionId, SenderType.customer);
    }

    public boolean isDuplicate(String interactionId, String text) {
        if (text == null) return false;
        MessageEntity last = repo.findTopByInteractionIdOrderByCreatedAtDesc(interactionId);
        if (last == null || last.getOriginalText() == null) return false;
        return last.getOriginalText().equalsIgnoreCase(text);
    }
    public ConversationSummaryResponse getDBSummary(String interactionId) {

        ConversationSummaryResponse resp = new ConversationSummaryResponse();

        Integer total = repo.countByInteractionId(interactionId);
        Integer userCount = repo.countByInteractionIdAndSender(interactionId, SenderType.customer);
        Integer agentCount = repo.countByInteractionIdAndSender(interactionId, SenderType.user);

        Double overall = repo.getAverageSentiment(interactionId);
        Double latest = repo.getLatestSentiment(interactionId);

        resp.setTotalMessages(total != null ? total : 0);
        resp.setUserMessages(userCount != null ? userCount : 0);
        resp.setAgentMessages(agentCount != null ? agentCount : 0);

        resp.setOverallSentiment(overall != null ? overall : 0.0);
        resp.setLatestSentiment(latest != null ? latest : 0.0);

        return resp;
    }

}
