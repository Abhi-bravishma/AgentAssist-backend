package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SentimentResponse {
    private String sentiment;
    private double sentimentScore;
}
