package com.agentassist.dto.avaya;

import lombok.Data;

/**
 * Author information for an Avaya message.
 */
@Data
public class AvayaAuthor {

    private String type;  // "user" = agent, "customer" = customer
    private String displayName;  // Present for customers at root level
    private AvayaAuthorDetails details;
}
