package com.agentassist.dto.avaya;

import lombok.Data;
import java.util.List;

/**
 * Response from Avaya transcript API.
 */
@Data
public class AvayaTranscriptResponse {

    private List<AvayaMessage> messages;
}
