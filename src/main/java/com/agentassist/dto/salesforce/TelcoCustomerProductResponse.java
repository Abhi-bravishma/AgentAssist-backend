package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from Salesforce telco-customer-products API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelcoCustomerProductResponse {
    private boolean success;
    private TelcoContactResponse.TelcoContactInfo contact;
    private List<TelcoCustomerProduct> data;
}
