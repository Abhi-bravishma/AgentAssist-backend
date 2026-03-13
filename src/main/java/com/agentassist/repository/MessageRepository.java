package com.agentassist.repository;

import com.agentassist.model.MessageEntity;
import com.agentassist.model.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    List<MessageEntity> findByInteractionIdOrderByCreatedAtAsc(String interactionId);

    List<MessageEntity> findByInteractionIdAndSenderOrderByCreatedAtAsc(
            String interactionId,
            SenderType sender
    );

    MessageEntity findTopByInteractionIdOrderByCreatedAtDesc(String interactionId);

    int countByInteractionId(String interactionId);

    int countByInteractionIdAndSender(String interactionId, SenderType sender);

    @Query("SELECT AVG(m.sentimentScore) FROM MessageEntity m WHERE m.interactionId = :interactionId")
    Double getAverageSentiment(@Param("interactionId") String interactionId);

    @Query("SELECT m.sentimentScore FROM MessageEntity m WHERE m.interactionId = :interactionId ORDER BY m.createdAt DESC LIMIT 1")
    Double getLatestSentiment(@Param("interactionId") String interactionId);
}
