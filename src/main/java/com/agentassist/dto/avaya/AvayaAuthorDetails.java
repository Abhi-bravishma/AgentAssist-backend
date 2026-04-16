package com.agentassist.dto.avaya;

import lombok.Data;

/**
 * Detailed author information for Avaya messages.
 */
@Data
public class AvayaAuthorDetails {

    private String id;
    private String displayName;
    private String email;
    private String title;
}
