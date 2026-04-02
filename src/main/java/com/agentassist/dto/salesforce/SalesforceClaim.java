package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Salesforce claim data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesforceClaim {
    @JsonProperty("Id")
    private String id;

    @JsonProperty("Name")
    private String claimNumber;  // CLM-xxxx

    @JsonProperty("status__c")
    private String status;  // Approved, Rejected, Pending

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("policyDetails")
    private SalesforcePolicy policyDetails;
}
