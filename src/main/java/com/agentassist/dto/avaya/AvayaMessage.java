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
    private String direction;  // "out" = agent/bot, "in" = customer
    private String type;       // "chat" or "system"
    private String subType;
    private String paginationId;
    private AvayaAuthor author;
    private List<AvayaMessageContent> message;
    private String languageCode;
    private String createdAt;  // Format: "2026-02-26 10:30:00.000000"
    private Boolean isBot;     // true = bot message, false = human message

    /**
     * Check if this message is from a HUMAN agent (not bot).
     * Human agent messages have: type="chat", direction="out", author.type="user", isBot=false
     */
    public boolean isAgentMessage() {
        return "chat".equalsIgnoreCase(type)              // Only chat messages, not system
            && "out".equalsIgnoreCase(direction)          // Outgoing messages
            && author != null
            && "user".equalsIgnoreCase(author.getType())  // From agent (not customer)
            && (isBot == null || !isBot);                 // Only human agents, not bots
    }

    /**
     * Check if this message is from a bot.
     */
    public boolean isBotMessage() {
        return "out".equalsIgnoreCase(direction)
            && author != null
            && "user".equalsIgnoreCase(author.getType())
            && Boolean.TRUE.equals(isBot);
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
