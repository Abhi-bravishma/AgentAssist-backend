package com.agentassist.dto.responseDTO;

import lombok.Data;

@Data
public class ConversationSummaryResponse {
    private String interactionId;
    private String baseLanguage;
    private double overallSentiment;
    private double latestSentiment;
    private String summary;
    private int totalMessages;
    private int userMessages;
    private int agentMessages;

}
