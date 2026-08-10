package com.agentassist.repository;

import com.agentassist.model.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    List<MessageEntity> findByInteractionIdOrderByCreatedAtAsc(String interactionId);
}
