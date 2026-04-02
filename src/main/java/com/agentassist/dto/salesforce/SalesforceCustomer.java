package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer data from Salesforce API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesforceCustomer {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Full_Name__c")
    private String fullName;

    @JsonProperty("Phone__c")
    private String phone;
}
