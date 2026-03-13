package com.agentassist.dto.responseDTO;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.util.List;

@Data
public class AiAnalysisResult {

    private String sentiment;

    @JsonAlias("sentiment_score")
    private double sentimentScore;

    private String summary;
    private List<String> suggestions;
}
