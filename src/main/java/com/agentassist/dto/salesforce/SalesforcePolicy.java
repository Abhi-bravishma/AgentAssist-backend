package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Salesforce policy details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesforcePolicy {
    @JsonProperty("Id")
    private String id;

    @JsonProperty("Name")
    private String policyNumber;  // POL-xxxx

    @JsonProperty("policy_name__c")
    private String policyName;  // Health Secure Plus, etc.
}
