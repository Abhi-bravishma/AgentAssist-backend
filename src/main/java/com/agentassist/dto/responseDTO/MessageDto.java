package com.agentassist.dto.responseDTO;



import lombok.Data;
import java.time.Instant;

@Data
public class MessageDto {
    private Long id;
    private String interactionId;
    private String sender; // USER / AGENT
    private String originalText;
    private String originalLanguage;
    private String englishText;
    private String sentiment;
    private Double sentimentScore;
    private Instant createdAt;
}
