package com.agentassist.dto.requestDTO;

import com.agentassist.ai.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OneShotRequest {

    @NotBlank(message = "interactionId is required")
    private String interactionId;

    @NotBlank(message = "from is required")
    @Pattern(regexp = "customer|user", message = "from must be 'customer' or 'user'")
    private String from;

    @NotBlank(message = "message is required")
    @Size(max = 10000, message = "message must not exceed 10000 characters")
    private String message;

    /**
     * AI provider to use for analysis.
     * Options: "openai" (default), "ollama", "both" (comparison mode)
     */
    private ProviderType provider = ProviderType.OPENAI;
}
