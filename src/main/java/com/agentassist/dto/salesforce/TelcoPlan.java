package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Telco plan/product offering data from Salesforce (Telco_Product_Offering__c object).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelcoPlan {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Type__c")
    private String type;  // Plan, Add-on, Topup, Roaming, Offer

    @JsonProperty("Price__c")
    private BigDecimal price;

    @JsonProperty("Validity__c")
    private Integer validity;  // days

    @JsonProperty("Data_Benefit__c")
    private String dataBenefit;

    @JsonProperty("Voice_Benefit__c")
    private String voiceBenefit;

    @JsonProperty("SMS_Benefit__c")
    private String smsBenefit;

    @JsonProperty("OTT_Benefit__c")
    private boolean ottBenefit;

    @JsonProperty("OTT_Details__c")
    private String ottDetails;

    @JsonProperty("Region__c")
    private String region;  // Domestic, International

    @JsonProperty("Country__c")
    private String country;

    @JsonProperty("Eligibility__c")
    private String eligibility;  // Prepaid, Postpaid

    @JsonProperty("Recommended_For__c")
    private String recommendedFor;  // High Data Users, Heavy Callers, Travelers, New Users

    @JsonProperty("Priority__c")
    private Integer priority;

    @JsonProperty("Is_Active__c")
    private boolean active;

    @JsonProperty("Is_Recommended__c")
    private boolean recommended;

    @JsonProperty("Description__c")
    private String description;

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("LastModifiedDate")
    private String lastModifiedDate;
}
