package com.agentassist.dto.avaya;

import lombok.Data;

/**
 * Content block within an Avaya message.
 */
@Data
public class AvayaMessageContent {

    private String type;  // "text", etc.
    private String text;
    private Boolean isTextOnlyEmojis;
}
