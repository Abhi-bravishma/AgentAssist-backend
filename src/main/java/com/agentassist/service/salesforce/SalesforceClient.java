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
    private static final String CONTACTS_ENDPOINT = "/api/v4/contacts";
    private static final String CREDIT_CARDS_ENDPOINT = "/api/v4/credit-cards";
    private static final String HOME_LOANS_ENDPOINT = "/api/v4/home-loans";
    private static final String TELCO_CONTACTS_ENDPOINT = "/api/v4/telco-contacts";
    private static final String TELCO_CUSTOMER_PRODUCTS_ENDPOINT = "/api/v4/telco-customer-products";
    private static final String TELCO_PLANS_ENDPOINT = "/api/v4/telco-plans";

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
     * Fetch the customer's billing position (outstanding balance, minimum due, due date)
     * from the Salesforce Contact record.
     * <p>
     * Only called for BILLING enquiries - no other flow touches this endpoint. Returns
     * null on any failure so the caller falls back to normal handling.
     *
     * @param mobileNumber Customer's mobile number
     * @return CustomerBillingData, or null if unavailable
     */
    public CustomerBillingData getCustomerBillingData(String mobileNumber) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }
        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.warn("Cannot fetch billing data without a mobile number");
            return null;
        }

        log.info("Fetching billing data from Salesforce for mobile: ****{}",
                mobileNumber.length() > 4 ? mobileNumber.substring(mobileNumber.length() - 4) : "****");

        try {
            ContactBillingResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(CONTACTS_ENDPOINT)
                            .queryParam("mobileNumber", mobileNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(ContactBillingResponse.class)
                    .block();

            CustomerBillingData billingData = CustomerBillingData.fromSalesforceResponse(response);
            if (billingData != null) {
                log.info("Salesforce returned billing data for customer: {}, dueStatus: {}",
                        billingData.getCustomerName(), billingData.dueStatus());
            } else {
                log.warn("No billing data found for customer");
            }
            return billingData;

        } catch (WebClientResponseException e) {
            log.error("Salesforce contacts API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch billing data from Salesforce: {}", e.getMessage());
            return null;
        }
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

    // =====================================================
    // TELCO API METHODS
    // =====================================================

    /**
     * Fetch telco contact data from Salesforce.
     *
     * @param mobileNumber Customer's mobile number
     * @return TelcoContactResponse with contact and plan info, or null if not found
     */
    public TelcoContactResponse getTelcoContact(String mobileNumber) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.debug("Mobile number is empty, skipping Telco contact lookup");
            return null;
        }

        log.info("Fetching telco contact data for mobile: {}", maskMobileNumber(mobileNumber));

        try {
            TelcoContactResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(TELCO_CONTACTS_ENDPOINT)
                            .queryParam("mobileNumber", mobileNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(TelcoContactResponse.class)
                    .block();

            if (response != null && response.isSuccess()) {
                log.info("Telco contact data retrieved for: {}",
                        response.getContact() != null ? response.getContact().getName() : "Unknown");
                return response;
            }

            log.warn("Telco contacts API returned unsuccessful response or null");
            return null;

        } catch (WebClientResponseException e) {
            log.error("Telco contacts API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Telco contacts API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch telco customer products from Salesforce.
     *
     * @param mobileNumber Customer's mobile number
     * @return TelcoCustomerProductResponse with products, or null if not found
     */
    public TelcoCustomerProductResponse getTelcoCustomerProducts(String mobileNumber) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.debug("Mobile number is empty, skipping Telco products lookup");
            return null;
        }

        log.info("Fetching telco customer products for mobile: {}", maskMobileNumber(mobileNumber));

        try {
            TelcoCustomerProductResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(TELCO_CUSTOMER_PRODUCTS_ENDPOINT)
                            .queryParam("mobileNumber", mobileNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(TelcoCustomerProductResponse.class)
                    .block();

            if (response != null && response.isSuccess()) {
                log.info("Telco products retrieved: {} products",
                        response.getData() != null ? response.getData().size() : 0);
                return response;
            }

            log.warn("Telco customer products API returned unsuccessful response or null");
            return null;

        } catch (WebClientResponseException e) {
            log.error("Telco customer products API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Telco customer products API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch all available telco plans from Salesforce.
     *
     * @return TelcoPlanResponse with all plans, or null if error
     */
    public TelcoPlanResponse getTelcoPlans() {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        log.info("Fetching all available telco plans");

        try {
            TelcoPlanResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(TELCO_PLANS_ENDPOINT)
                            .build())
                    .retrieve()
                    .bodyToMono(TelcoPlanResponse.class)
                    .block();

            if (response != null && response.isSuccess()) {
                log.info("Telco plans retrieved: {} plans",
                        response.getData() != null ? response.getData().size() : 0);
                return response;
            }

            log.warn("Telco plans API returned unsuccessful response or null");
            return null;

        } catch (WebClientResponseException e) {
            log.error("Telco plans API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Telco plans API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch a specific telco plan by ID.
     *
     * @param planId Plan ID
     * @return TelcoPlan or null if not found
     */
    public TelcoPlan getTelcoPlanById(String planId) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        if (planId == null || planId.isBlank()) {
            log.debug("Plan ID is empty, skipping lookup");
            return null;
        }

        log.info("Fetching telco plan by ID: {}", planId);

        try {
            // The API returns the plan wrapped in a response object
            TelcoPlanResponse response = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(TELCO_PLANS_ENDPOINT + "/" + planId)
                            .build())
                    .retrieve()
                    .bodyToMono(TelcoPlanResponse.class)
                    .block();

            if (response != null && response.isSuccess() && response.getData() != null && !response.getData().isEmpty()) {
                log.info("Telco plan retrieved: {}", response.getData().get(0).getName());
                return response.getData().get(0);
            }

            // Try single plan response format
            TelcoPlan singlePlan = salesforceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(TELCO_PLANS_ENDPOINT + "/" + planId)
                            .build())
                    .retrieve()
                    .bodyToMono(TelcoPlan.class)
                    .block();

            if (singlePlan != null && singlePlan.getId() != null) {
                log.info("Telco plan retrieved (single format): {}", singlePlan.getName());
                return singlePlan;
            }

            log.warn("Telco plan not found for ID: {}", planId);
            return null;

        } catch (WebClientResponseException e) {
            log.error("Telco plan API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to call Telco plan API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch combined telco data for a customer.
     * Calls all telco APIs and combines the results.
     *
     * @param mobileNumber Customer's mobile number
     * @return CustomerTelcoData with all telco information, or null if no data found
     */
    public CustomerTelcoData getCustomerTelcoData(String mobileNumber) {
        if (!salesforceConfig.isEnabled()) {
            log.debug("Salesforce integration is disabled");
            return null;
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.debug("Mobile number is empty, skipping Telco data lookup");
            return null;
        }

        log.info("Fetching complete telco data for mobile: {}", maskMobileNumber(mobileNumber));

        // Fetch all telco data
        TelcoContactResponse contactResponse = getTelcoContact(mobileNumber);
        TelcoCustomerProductResponse productResponse = getTelcoCustomerProducts(mobileNumber);
        TelcoPlanResponse planResponse = getTelcoPlans();

        // Fetch current plan details if available
        TelcoPlan currentPlan = null;
        if (contactResponse != null && contactResponse.getData() != null && !contactResponse.getData().isEmpty()) {
            String currentPlanId = contactResponse.getData().get(0).getCurrentPlanId();
            if (currentPlanId != null && !currentPlanId.isBlank()) {
                currentPlan = getTelcoPlanById(currentPlanId);
            }
        }

        // Check if we have any data
        if (contactResponse == null && productResponse == null) {
            log.warn("No telco data found for customer");
            return null;
        }

        CustomerTelcoData telcoData = CustomerTelcoData.fromResponses(
                contactResponse, productResponse, planResponse, currentPlan);

        log.info("Complete telco data assembled for customer: {}", telcoData.getCustomerName());
        return telcoData;
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
