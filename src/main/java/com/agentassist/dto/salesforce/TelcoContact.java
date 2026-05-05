package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Telco contact data from Salesforce (Telco_Contact__c object).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelcoContact {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Contact__c")
    private String contactId;

    @JsonProperty("Plan_Name__c")
    private String planName;

    @JsonProperty("Plan_Type__c")
    private String planType;

    @JsonProperty("Daily_Data_Limit__c")
    private BigDecimal dailyDataLimit;

    @JsonProperty("Data_Used_Today__c")
    private BigDecimal dataUsedToday;

    @JsonProperty("Validity_Date__c")
    private String validityDate;

    @JsonProperty("Last_Recharge_Amount__c")
    private BigDecimal lastRechargeAmount;

    @JsonProperty("Segment__c")
    private String segment;

    @JsonProperty("Network_Status__c")
    private String networkStatus;

    @JsonProperty("Is_Roaming_Active__c")
    private boolean roamingActive;

    @JsonProperty("Roaming_Country__c")
    private String roamingCountry;

    @JsonProperty("ARPU__c")
    private BigDecimal arpu;

    @JsonProperty("Current_Plan__c")
    private String currentPlanId;

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("LastModifiedDate")
    private String lastModifiedDate;
}
