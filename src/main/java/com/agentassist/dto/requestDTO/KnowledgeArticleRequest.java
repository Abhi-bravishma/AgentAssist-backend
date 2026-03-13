package com.agentassist.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeArticleRequest {

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "type is required")
    private String type;

    @NotBlank(message = "content is required")
    private String content;
}
