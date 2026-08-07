package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Raw response from Salesforce GET /api/v4/contacts?mobileNumber=...
 * <p>
 * Unlike the credit-cards endpoint (which returns a list), this returns a single
 * Contact record carrying the customer's billing position - outstanding balance,
 * minimum due and due date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactBillingResponse {

    private boolean success;
    private ContactRecord data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContactRecord {

        @JsonProperty("Id")
        private String id;

        @JsonProperty("Name")
        private String name;

        @JsonProperty("Email")
        private String email;

        @JsonProperty("MobilePhone")
        private String mobilePhone;

        // ----- Billing position -----

        @JsonProperty("OB__c")
        private BigDecimal outstandingBalance;

        @JsonProperty("Min_Due__c")
        private BigDecimal minimumDue;

        @JsonProperty("Due_Date__c")
        private String dueDate;

        @JsonProperty("Current_Balance__c")
        private BigDecimal currentBalance;

        @JsonProperty("Available_Balance__c")
        private BigDecimal availableBalance;

        @JsonProperty("Pay_Amount__c")
        private BigDecimal lastPayAmount;

        @JsonProperty("Pay_Date__c")
        private String lastPayDate;

        @JsonProperty("Account_Balance__c")
        private BigDecimal accountBalance;

        @JsonProperty("SB__c")
        private BigDecimal statementBalance;

        // ----- Account identifiers (masked before reaching the AI) -----

        @JsonProperty("Account_number__c")
        private String accountNumber;

        @JsonProperty("Card_Number__c")
        private String cardNumber;

        @JsonProperty("Credit_Card_1_Number__c")
        private String creditCard1Number;

        @JsonProperty("Bank_Account_Number__c")
        private String bankAccountNumber;

        @JsonProperty("Type__c")
        private String accountTypes;
    }
}
