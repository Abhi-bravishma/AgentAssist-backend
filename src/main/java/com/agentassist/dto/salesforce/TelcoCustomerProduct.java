package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Telco customer product data from Salesforce (Telco_Customer_Product__c object).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelcoCustomerProduct {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Contact__c")
    private String contactId;

    @JsonProperty("Telco_Contact__c")
    private String telcoContactId;

    @JsonProperty("Telco_Product_Offering__c")
    private String productOfferingId;

    @JsonProperty("Type__c")
    private String type;  // Plan, Add-on, Topup

    @JsonProperty("Status__c")
    private String status;  // Active, Expired

    @JsonProperty("Activation_Date__c")
    private String activationDate;

    @JsonProperty("Expiry_Date__c")
    private String expiryDate;

    @JsonProperty("Remaining_Data__c")
    private BigDecimal remainingData;

    @JsonProperty("Purchase_Amount__c")
    private BigDecimal purchaseAmount;

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("LastModifiedDate")
    private String lastModifiedDate;
}
