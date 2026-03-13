package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeArticleDTO {

    private Long id;
    private String name;
    private String type;
    private String content;
}
