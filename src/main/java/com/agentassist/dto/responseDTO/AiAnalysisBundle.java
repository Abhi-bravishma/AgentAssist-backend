package com.agentassist.dto.responseDTO;

import lombok.Data;

import java.util.List;

@Data
public class AiAnalysisBundle {
    private double overall_sentiment_score;
    private double current_sentiment_score;
    private String current_sentiment_label;
    private String summary;
    private List<String> suggestions;
}

