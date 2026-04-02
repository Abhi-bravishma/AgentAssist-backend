package com.agentassist.dto.responseDTO;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAnalysisBundle {
    @JsonAlias({"overallSentimentScore", "overall_sentiment", "overallSentiment"})
    private double overall_sentiment_score;

    @JsonAlias({"currentSentimentScore", "current_sentiment", "currentSentiment"})
    private double current_sentiment_score;

    @JsonAlias({"currentSentimentLabel", "sentiment_label", "sentimentLabel"})
    private String current_sentiment_label;

    private String summary;
    private List<String> suggestions;
}

