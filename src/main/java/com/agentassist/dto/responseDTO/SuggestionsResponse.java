package com.agentassist.dto.responseDTO;

import lombok.Data;

import java.util.List;

@Data
public class SuggestionsResponse {
    private List<SuggestedResponse> replies;
}
