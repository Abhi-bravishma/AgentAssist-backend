package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Credit Card data from Salesforce.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreditCard {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Card_ID__c")
    private String cardId;

    @JsonProperty("Card_Number__c")
    private String cardNumber;

    @JsonProperty("Card_Type__c")
    private String cardType;

    @JsonProperty("Card_Status__c")
    private String cardStatus;

    @JsonProperty("Credit_Limit__c")
    private BigDecimal creditLimit;

    @JsonProperty("Annual_Fee__c")
    private BigDecimal annualFee;

    @JsonProperty("NAFFL_Eligible__c")
    private boolean nafflEligible;

    @JsonProperty("Activation_Date__c")
    private String activationDate;

    @JsonProperty("Delinquency_Status__c")
    private String delinquencyStatus;

    @JsonProperty("Total_Spend__c")
    private BigDecimal totalSpend;

    @JsonProperty("Eligible_Spend_90D__c")
    private BigDecimal eligibleSpend90D;

    @JsonProperty("Eligible_Spend_12M__c")
    private BigDecimal eligibleSpend12M;

    @JsonProperty("Last_Transaction_Date__c")
    private String lastTransactionDate;

    @JsonProperty("Customer__c")
    private String customerId;

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("LastModifiedDate")
    private String lastModifiedDate;
}
