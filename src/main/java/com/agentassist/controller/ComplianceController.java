package com.agentassist.controller;

import com.agentassist.dto.responseDTO.ComplianceResponse;
import com.agentassist.service.compliance.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for compliance check operations.
 * Analyzes agent messages for: Greeting, Empathy, Clarity, Product T&C, Valediction.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "Agent compliance check APIs")
public class ComplianceController {

    private final ComplianceService complianceService;

    /**
     * Perform a compliance check on an interaction.
     * Fetches transcript from Avaya, analyzes agent messages using Ollama,
     * and returns true/false for each compliance metric.
     */
    @PostMapping("/{interactionId}")
    @Operation(
        summary = "Perform compliance check",
        description = "Analyzes agent messages from Avaya transcript for compliance. " +
                      "Checks: Greeting, Empathy, Clarity, Product T&C, Valediction."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Compliance check completed successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to fetch transcript or analyze")
    })
    public ResponseEntity<ComplianceResponse> performComplianceCheck(
            @Parameter(description = "Avaya interaction ID or workflow session ID")
            @PathVariable String interactionId) {

        log.info("[ComplianceController] POST /compliance/{}", interactionId);

        ComplianceResponse response = complianceService.performComplianceCheck(interactionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get the latest compliance check results for an interaction.
     * Returns cached results from database if available.
     */
    @GetMapping("/{interactionId}")
    @Operation(
        summary = "Get compliance check results",
        description = "Retrieves the latest compliance check results for an interaction from the database."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Compliance check results found"),
        @ApiResponse(responseCode = "404", description = "No compliance check found for this interaction")
    })
    public ResponseEntity<ComplianceResponse> getComplianceCheck(
            @Parameter(description = "Avaya interaction ID")
            @PathVariable String interactionId) {

        log.info("[ComplianceController] GET /compliance/{}", interactionId);

        return complianceService.getLatestComplianceCheck(interactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Perform compliance check or return cached results.
     * This is useful for avoiding duplicate analysis.
     */
    @PostMapping("/{interactionId}/cached")
    @Operation(
        summary = "Perform or get cached compliance check",
        description = "Returns cached results if available, otherwise performs new compliance check."
    )
    public ResponseEntity<ComplianceResponse> performOrGetComplianceCheck(
            @Parameter(description = "Avaya interaction ID")
            @PathVariable String interactionId) {

        log.info("[ComplianceController] POST /compliance/{}/cached", interactionId);

        ComplianceResponse response = complianceService.performOrGetComplianceCheck(interactionId);
        return ResponseEntity.ok(response);
    }
}
