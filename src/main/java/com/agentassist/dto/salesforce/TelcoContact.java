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

    /**
     * The API also embeds the current plan as an OBJECT (name, offering id,
     * status, activation/expiry). Real records often carry ONLY this — no
     * Current_Plan__c, no Validity_Date__c — so ignoring it meant "validity
     * not on record" while the dates sat right here.
     */
    @JsonProperty("currentPlan")
    private CurrentPlanRef currentPlan;

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("LastModifiedDate")
    private String lastModifiedDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrentPlanRef {

        @JsonProperty("CustomerProductId")
        private String customerProductId;

        @JsonProperty("Name")
        private String name;

        @JsonProperty("OfferingId")
        private String offeringId;

        /** Human plan name, e.g. "Ultra 30". */
        @JsonProperty("Offering")
        private String offeringName;

        @JsonProperty("Status__c")
        private String status;

        @JsonProperty("Activation_Date__c")
        private String activationDate;

        @JsonProperty("Expiry_Date__c")
        private String expiryDate;

        @JsonProperty("Purchase_Amount__c")
        private BigDecimal purchaseAmount;
    }
}
