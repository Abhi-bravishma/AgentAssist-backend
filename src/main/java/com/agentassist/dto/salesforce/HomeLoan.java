package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Home Loan data from Salesforce.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeLoan {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Loan_ID__c")
    private String loanId;

    @JsonProperty("Loan_Account_Number__c")
    private String loanAccountNumber;

    @JsonProperty("Loan_Status__c")
    private String loanStatus;

    @JsonProperty("Sanctioned_Amount__c")
    private BigDecimal sanctionedAmount;

    @JsonProperty("Outstanding_Amount__c")
    private BigDecimal outstandingAmount;

    @JsonProperty("Interest_Rate__c")
    private BigDecimal interestRate;

    @JsonProperty("Loan_Start_Date__c")
    private String loanStartDate;

    @JsonProperty("Lockin_Period_End_Date__c")
    private String lockinPeriodEndDate;

    @JsonProperty("Foreclosure_Allowed__c")
    private boolean foreclosureAllowed;

    @JsonProperty("Prepayment_Penalty__c")
    private BigDecimal prepaymentPenalty;

    @JsonProperty("Legal_Status__c")
    private String legalStatus;

    @JsonProperty("Last_Payment_Date__c")
    private String lastPaymentDate;

    @JsonProperty("EMI_Status__c")
    private String emiStatus;

    @JsonProperty("Customer__c")
    private String customerId;

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("LastModifiedDate")
    private String lastModifiedDate;
}
