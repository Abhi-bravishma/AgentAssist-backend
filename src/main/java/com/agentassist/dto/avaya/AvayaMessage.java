package com.agentassist.dto.avaya;

import lombok.Data;
import java.util.List;

/**
 * Represents a single message from Avaya transcript.
 */
@Data
public class AvayaMessage {

    private String id;
    private String channel;
    private String direction;  // "out" = agent, "in" = customer
    private String type;
    private String subType;
    private String paginationId;
    private AvayaAuthor author;
    private List<AvayaMessageContent> message;
    private String languageCode;
    private String createdAt;  // Format: "2026-02-26 10:30:00.000000"

    /**
     * Check if this message is from an agent.
     * Agent messages have direction="out" and author.type="user"
     */
    public boolean isAgentMessage() {
        return "out".equalsIgnoreCase(direction)
            && author != null
            && "user".equalsIgnoreCase(author.getType());
    }

    /**
     * Get the text content of this message.
     */
    public String getTextContent() {
        if (message == null || message.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AvayaMessageContent content : message) {
            if ("text".equalsIgnoreCase(content.getType()) && content.getText() != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(content.getText());
            }
        }
        return sb.toString();
    }
}
