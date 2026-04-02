package com.agentassist.service.salesforce;

import com.agentassist.config.SalesforceConfig;
import com.agentassist.dto.salesforce.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Client service for communicating with Salesforce API.
 * Fetches customer policy, claims, credit card, and home loan data.
 */
@Slf4j
@Service
public class SalesforceClient {

    private static final String CLAIMS_ENDPOINT = "/api/v4/claims";
    private static final String CREDIT_CARDS_ENDPOINT = "/api/v4/credit-cards";
    private static final String HOME_LOANS_ENDPOINT = "/api/v4/home-loans";

    private final WebClient salesforceWebClient;
    private final SalesforceConfig salesforceConfig;

    public SalesforceClient(@Qualifier("salesforceWebClient") WebClient salesforceWebClient,
                            SalesforceConfig salesforceConfig) {
        this.salesforceWebClient = salesforceWebClient;
        this.salesforceConfig = salesforceConfig;
    }

    /**
     * Check if Salesforce integration is enabled.
     */
    public boolean isEnabled() {
        return salesforceConfig.isEnabled();
    }

    /**
     * Fetch customer policy and claims data from Salesforce.
     *
     * @param mobileNumber Customer's mobile number
     * @return CustomerPolicyData with filtered policy/claims info, or null if not found
     */
    public CustomerPolicyData getCustomerPolicyData(String mobileNumber) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.debug("Mobile number is empty, skipping Salesforce lookup");
            return null;
        }

        log.info("Fetching policy data from Salesforce for mobile: {}", maskMobileNumber(mobileNumber));

        try {
            SalesforceResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(CLAIMS_ENDPOINT)
                            .queryParam("mobileNumber", mobileNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(SalesforceResponse.class)
                    .block();

            if (response != null && response.isSuccess()) {
                CustomerPolicyData policyData = CustomerPolicyData.fromSalesforceResponse(response);
                if (policyData != null) {
                    log.info("Salesforce returned data for customer: {}, policies: {}, claims: {}",
                            policyData.getCustomerName(),
                            policyData.getPolicies() != null ? policyData.getPolicies().size() : 0,
                            policyData.getClaims() != null ? policyData.getClaims().size() : 0);
                }
                return policyData;
            }

            log.warn("Salesforce returned unsuccessful response or null");
            return null;

        } catch (WebClientResponseException e) {
            log.error("Salesforce API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Salesforce API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch customer credit card data from Salesforce.
     *
     * @param mobileNumber Customer's mobile number
     * @return CustomerCreditCardData with credit card info, or null if not found
     */
    public CustomerCreditCardData getCustomerCreditCardData(String mobileNumber) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.debug("Mobile number is empty, skipping Salesforce credit card lookup");
            return null;
        }

        log.info("Fetching credit card data from Salesforce for mobile: {}", maskMobileNumber(mobileNumber));

        try {
            CreditCardResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(CREDIT_CARDS_ENDPOINT)
                            .queryParam("mobileNumber", mobileNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(CreditCardResponse.class)
                    .block();

            if (response != null && response.isSuccess()) {
                CustomerCreditCardData creditCardData = CustomerCreditCardData.fromSalesforceResponse(response);
                if (creditCardData != null) {
                    log.info("Salesforce returned credit card data for customer: {}, cards: {}",
                            creditCardData.getCustomerName(),
                            creditCardData.getCreditCards() != null ? creditCardData.getCreditCards().size() : 0);
                }
                return creditCardData;
            }

            log.warn("Salesforce returned unsuccessful response or null for credit cards");
            return null;

        } catch (WebClientResponseException e) {
            log.error("Salesforce credit cards API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Salesforce credit cards API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch customer home loan data from Salesforce.
     *
     * @param mobileNumber Customer's mobile number
     * @return CustomerHomeLoanData with home loan info, or null if not found
     */
    public CustomerHomeLoanData getCustomerHomeLoanData(String mobileNumber) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.debug("Mobile number is empty, skipping Salesforce home loan lookup");
            return null;
        }

        log.info("Fetching home loan data from Salesforce for mobile: {}", maskMobileNumber(mobileNumber));

        try {
            HomeLoanResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(HOME_LOANS_ENDPOINT)
                            .queryParam("mobileNumber", mobileNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(HomeLoanResponse.class)
                    .block();

            if (response != null && response.isSuccess()) {
                CustomerHomeLoanData homeLoanData = CustomerHomeLoanData.fromSalesforceResponse(response);
                if (homeLoanData != null) {
                    log.info("Salesforce returned home loan data for customer: {}, loans: {}",
                            homeLoanData.getCustomerName(),
                            homeLoanData.getHomeLoans() != null ? homeLoanData.getHomeLoans().size() : 0);
                }
                return homeLoanData;
            }

            log.warn("Salesforce returned unsuccessful response or null for home loans");
            return null;

        } catch (WebClientResponseException e) {
            log.error("Salesforce home loans API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Salesforce home loans API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Mask mobile number for logging (show last 4 digits only).
     */
    private String maskMobileNumber(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "****";
        }
        return "****" + mobile.substring(mobile.length() - 4);
    }
}
