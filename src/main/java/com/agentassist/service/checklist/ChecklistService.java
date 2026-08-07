package com.agentassist.service.checklist;

import com.agentassist.ai.AiProviderFactory;
import com.agentassist.configregistry.BrandService;
import com.agentassist.configregistry.IntentRegistryService;
import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import com.agentassist.dto.salesforce.CustomerBillingData;
import com.agentassist.dto.salesforce.CustomerCreditCardData;
import com.agentassist.dto.salesforce.CustomerHomeLoanData;
import com.agentassist.dto.salesforce.CustomerPolicyData;
import com.agentassist.dto.salesforce.CustomerTelcoData;
import com.agentassist.service.salesforce.SalesforceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for detecting checklist operations (fee waiver, home loan closure)
 * and building AI context with customer-specific data.
 * Uses AI-based intent detection for accurate operation classification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final SalesforceClient salesforceClient;
    private final AiProviderFactory aiProviderFactory;
    private final PromptService promptService;
    private final BrandService brandService;
    private final IntentRegistryService intentRegistryService;

    /**
     * Operation types that can be detected from conversation.
     */
    public enum OperationType {
        FEE_WAIVER,
        HOME_LOAN_CLOSURE,
        POLICY,
        CLAIMS,
        TELCO,
        /** Statement / payment position / card restriction enquiries. */
        BILLING,
        NONE
    }

    /**
     * Project-specific operation mapping.
     * METRO (Banking): FEE_WAIVER, HOME_LOAN_CLOSURE
     * ALLIANZ (Insurance): POLICY, CLAIMS
     * TELCO (Telecommunications): TELCO
     * SCB (Banking): FEE_WAIVER
     * null/empty: All operations allowed
     */
    public boolean isOperationValidForProject(OperationType operation, String projectName) {
        // Semantics live in aa_project_intent now: null/blank/unknown project or a
        // project with no rows allows everything; NONE is always allowed.
        return intentRegistryService.isIntentAllowed(operation.name(), projectName);
    }

    /**
     * Result of checklist detection and data fetch.
     */
    public record ChecklistContext(
            OperationType operationType,
            String checklistPrompt,
            String customerDataContext,
            CustomerCreditCardData creditCardData,
            CustomerHomeLoanData homeLoanData,
            CustomerPolicyData policyData,
            CustomerTelcoData telcoData,
            OperationType filteredIntent,  // Intent that was detected but filtered out due to project mismatch
            CustomerBillingData billingData
    ) {
        // Backward compatible constructor (without telcoData)
        public ChecklistContext(OperationType operationType, String checklistPrompt, String customerDataContext,
                                CustomerCreditCardData creditCardData, CustomerHomeLoanData homeLoanData,
                                CustomerPolicyData policyData) {
            this(operationType, checklistPrompt, customerDataContext, creditCardData, homeLoanData, policyData, null, null, null);
        }

        // Backward compatible constructor (without telcoData, with filteredIntent)
        public ChecklistContext(OperationType operationType, String checklistPrompt, String customerDataContext,
                                CustomerCreditCardData creditCardData, CustomerHomeLoanData homeLoanData,
                                CustomerPolicyData policyData, OperationType filteredIntent) {
            this(operationType, checklistPrompt, customerDataContext, creditCardData, homeLoanData, policyData, null, filteredIntent, null);
        }

        // Backward compatible constructor (without billingData)
        public ChecklistContext(OperationType operationType, String checklistPrompt, String customerDataContext,
                                CustomerCreditCardData creditCardData, CustomerHomeLoanData homeLoanData,
                                CustomerPolicyData policyData, CustomerTelcoData telcoData,
                                OperationType filteredIntent) {
            this(operationType, checklistPrompt, customerDataContext, creditCardData, homeLoanData, policyData,
                    telcoData, filteredIntent, null);
        }

        public boolean hasContext() {
            return operationType != OperationType.NONE && customerDataContext != null;
        }

        public String getFullContext() {
            if (!hasContext()) return null;
            if (checklistPrompt != null && !checklistPrompt.isEmpty()) {
                return checklistPrompt + "\n\n" + customerDataContext;
            }
            return customerDataContext;
        }

        public boolean wasIntentFiltered() {
            return filteredIntent != null && filteredIntent != OperationType.NONE;
        }
    }

    /**
     * Detect operation type from conversation messages using AI.
     * AI understands context, handles any language, and catches variations
     * that regex patterns would miss.
     *
     * @param conversationMessages List of conversation messages
     * @return Detected operation type
     */
    public OperationType detectOperation(List<String> conversationMessages) {
        if (conversationMessages == null || conversationMessages.isEmpty()) {
            return OperationType.NONE;
        }

        // Get the latest message for primary analysis
        String latestMessage = conversationMessages.get(conversationMessages.size() - 1);

        log.info("[Checklist] Using AI to detect operation type...");
        long startTime = System.currentTimeMillis();

        try {
            // Call AI to detect operation type
            String aiResult = aiProviderFactory.active().detectOperationType(conversationMessages, latestMessage);

            OperationType operationType = switch (aiResult) {
                case "FEE_WAIVER" -> OperationType.FEE_WAIVER;
                case "HOME_LOAN_CLOSURE" -> OperationType.HOME_LOAN_CLOSURE;
                case "POLICY" -> OperationType.POLICY;
                case "CLAIMS" -> OperationType.CLAIMS;
                case "TELCO" -> OperationType.TELCO;
                case "BILLING" -> OperationType.BILLING;
                default -> OperationType.NONE;
            };

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Checklist] AI detected operation: {} in {}ms", operationType, duration);

            return operationType;

        } catch (Exception e) {
            log.error("[Checklist] AI detection failed, returning NONE: {}", e.getMessage());
            return OperationType.NONE;
        }
    }

    /**
     * Build checklist context by detecting operation and fetching customer data.
     *
     * @param conversationMessages All conversation messages (in English)
     * @param mobileNumber Customer's mobile number for Salesforce lookup
     * @return ChecklistContext with operation type, checklist, and customer data
     */
    public ChecklistContext buildChecklistContext(List<String> conversationMessages, String mobileNumber) {
        return buildChecklistContext(conversationMessages, null, mobileNumber, null);
    }

    /**
     * Build checklist context by detecting operation and fetching customer data.
     * Uses original messages for better multi-language detection.
     *
     * @param englishMessages All conversation messages in English
     * @param originalMessages All conversation messages in original language (for detection)
     * @param mobileNumber Customer's mobile number for Salesforce lookup
     * @return ChecklistContext with operation type, checklist, and customer data
     */
    public ChecklistContext buildChecklistContext(List<String> englishMessages, List<String> originalMessages, String mobileNumber) {
        return buildChecklistContext(englishMessages, originalMessages, mobileNumber, null);
    }

    /**
     * Build checklist context by detecting operation and fetching customer data.
     * Filters operations based on projectName:
     * - METRO: FEE_WAIVER, HOME_LOAN_CLOSURE (banking)
     * - ALLIANZ: POLICY, CLAIMS (insurance)
     * - null/empty: All operations allowed
     *
     * @param englishMessages All conversation messages in English
     * @param originalMessages All conversation messages in original language (for detection)
     * @param mobileNumber Customer's mobile number for Salesforce lookup
     * @param projectName Project/Bank name for filtering (e.g., "METRO", "ALLIANZ")
     * @return ChecklistContext with operation type, checklist, and customer data
     */
    public ChecklistContext buildChecklistContext(List<String> englishMessages, List<String> originalMessages,
                                                   String mobileNumber, String projectName) {
        // Use original messages for detection if available, otherwise use English
        List<String> messagesForDetection = (originalMessages != null && !originalMessages.isEmpty())
                ? originalMessages
                : englishMessages;

        OperationType operationType = detectOperation(messagesForDetection);

        // Filter operation based on projectName
        if (operationType != OperationType.NONE && !isOperationValidForProject(operationType, projectName)) {
            log.info("[Checklist] Operation {} not valid for project {}, returning NONE with filteredIntent", operationType, projectName);
            // Return NONE but track the filtered intent so we can inform the user
            return new ChecklistContext(OperationType.NONE, null, null, null, null, null, operationType);
        }

        if (operationType == OperationType.NONE) {
            return new ChecklistContext(OperationType.NONE, null, null, null, null, null);
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.warn("[Checklist] Operation detected but no mobile number provided");
            return new ChecklistContext(operationType, getChecklistPrompt(operationType), null, null, null, null);
        }

        return switch (operationType) {
            case FEE_WAIVER -> buildFeeWaiverContext(mobileNumber, projectName);
            case HOME_LOAN_CLOSURE -> buildHomeLoanClosureContext(mobileNumber);
            case POLICY, CLAIMS -> buildPolicyContext(mobileNumber, operationType);
            case TELCO -> buildTelcoContext(mobileNumber);
            case BILLING -> buildBillingContext(mobileNumber, projectName);
            default -> new ChecklistContext(OperationType.NONE, null, null, null, null, null);
        };
    }

    /**
     * Build context for fee waiver operation.
     * Uses project-specific checklist (METRO vs SCB).
     */
    private ChecklistContext buildFeeWaiverContext(String mobileNumber, String projectName) {
        log.info("[Checklist] Building FEE_WAIVER context for mobile: ****{}, project: {}",
                mobileNumber.length() > 4 ? mobileNumber.substring(mobileNumber.length() - 4) : "****",
                projectName != null ? projectName : "DEFAULT");

        CustomerCreditCardData creditCardData = salesforceClient.getCustomerCreditCardData(mobileNumber);

        if (creditCardData == null || creditCardData.getCreditCards() == null || creditCardData.getCreditCards().isEmpty()) {
            log.warn("[Checklist] No credit card data found for customer");
            return new ChecklistContext(OperationType.FEE_WAIVER, getChecklistPrompt(OperationType.FEE_WAIVER, projectName), null, null, null, null);
        }

        String customerDataContext = creditCardData.toAiContext(projectName);
        log.info("[Checklist] FEE_WAIVER context built with {} credit cards for project: {}",
                creditCardData.getCreditCards().size(), projectName);

        return new ChecklistContext(
                OperationType.FEE_WAIVER,
                getChecklistPrompt(OperationType.FEE_WAIVER, projectName),
                customerDataContext,
                creditCardData,
                null,
                null
        );
    }

    /**
     * Build context for home loan closure operation.
     */
    private ChecklistContext buildHomeLoanClosureContext(String mobileNumber) {
        log.info("[Checklist] Building HOME_LOAN_CLOSURE context for mobile: ****{}",
                mobileNumber.length() > 4 ? mobileNumber.substring(mobileNumber.length() - 4) : "****");

        CustomerHomeLoanData homeLoanData = salesforceClient.getCustomerHomeLoanData(mobileNumber);

        if (homeLoanData == null || homeLoanData.getHomeLoans() == null || homeLoanData.getHomeLoans().isEmpty()) {
            log.warn("[Checklist] No home loan data found for customer");
            return new ChecklistContext(OperationType.HOME_LOAN_CLOSURE, getChecklistPrompt(OperationType.HOME_LOAN_CLOSURE), null, null, null, null);
        }

        String customerDataContext = homeLoanData.toAiContext();
        log.info("[Checklist] HOME_LOAN_CLOSURE context built with {} home loans", homeLoanData.getHomeLoans().size());

        return new ChecklistContext(
                OperationType.HOME_LOAN_CLOSURE,
                getChecklistPrompt(OperationType.HOME_LOAN_CLOSURE),
                customerDataContext,
                null,
                homeLoanData,
                null
        );
    }

    /**
     * Build context for policy/claims inquiry.
     */
    private ChecklistContext buildPolicyContext(String mobileNumber, OperationType operationType) {
        log.info("[Checklist] Building {} context for mobile: ****{}",
                operationType,
                mobileNumber.length() > 4 ? mobileNumber.substring(mobileNumber.length() - 4) : "****");

        CustomerPolicyData policyData = salesforceClient.getCustomerPolicyData(mobileNumber);

        if (policyData == null) {
            log.warn("[Checklist] No policy data found for customer");
            return new ChecklistContext(operationType, getChecklistPrompt(operationType), null, null, null, null);
        }

        String customerDataContext = policyData.toAiContext();
        log.info("[Checklist] {} context built - customer: {}, policies: {}, claims: {}",
                operationType,
                policyData.getCustomerName(),
                policyData.getPolicies() != null ? policyData.getPolicies().size() : 0,
                policyData.getClaims() != null ? policyData.getClaims().size() : 0);

        return new ChecklistContext(
                operationType,
                getChecklistPrompt(operationType), // Include checklist for policy/claims
                customerDataContext,
                null,
                null,
                policyData
        );
    }

    /**
     * Build context for telco inquiries (plan info, data usage, recommendations).
     */
    private ChecklistContext buildTelcoContext(String mobileNumber) {
        log.info("[Checklist] Building TELCO context for mobile: ****{}",
                mobileNumber.length() > 4 ? mobileNumber.substring(mobileNumber.length() - 4) : "****");

        CustomerTelcoData telcoData = salesforceClient.getCustomerTelcoData(mobileNumber);

        if (telcoData == null) {
            log.warn("[Checklist] No telco data found for customer");
            return new ChecklistContext(OperationType.TELCO, getChecklistPrompt(OperationType.TELCO), null, null, null, null, null, null);
        }

        String customerDataContext = telcoData.toAiContext();
        log.info("[Checklist] TELCO context built - customer: {}, products: {}",
                telcoData.getCustomerName(),
                telcoData.getCustomerProducts() != null ? telcoData.getCustomerProducts().size() : 0);

        return new ChecklistContext(
                OperationType.TELCO,
                getChecklistPrompt(OperationType.TELCO),
                customerDataContext,
                null,
                null,
                null,
                telcoData,
                null
        );
    }

    /**
     * Build context for a billing enquiry - what is owed, when it is due, and why a
     * card is restricted.
     * <p>
     * Combines two sources: the Contact record carries the money (outstanding balance,
     * minimum due, due date), while the credit card records carry the real card status.
     * The card status is taken from Salesforce rather than inferred from how overdue the
     * payment is, so the answer matches what the bank's own systems show.
     */
    private ChecklistContext buildBillingContext(String mobileNumber, String projectName) {
        log.info("[Checklist] Building BILLING context for mobile: ****{}",
                mobileNumber.length() > 4 ? mobileNumber.substring(mobileNumber.length() - 4) : "****");

        CustomerBillingData billingData = salesforceClient.getCustomerBillingData(mobileNumber);

        if (billingData == null) {
            log.warn("[Checklist] No billing data found for customer");
            return new ChecklistContext(OperationType.BILLING, getChecklistPrompt(OperationType.BILLING, projectName),
                    null, null, null, null);
        }

        log.info("[Checklist] BILLING context built - customer: {}, dueStatus: {}, cardStatus: {}",
                billingData.getCustomerName(), billingData.dueStatus(), billingData.cardPosition().status());

        return new ChecklistContext(
                OperationType.BILLING,
                getChecklistPrompt(OperationType.BILLING, projectName),
                billingData.toAiContext(),
                null,
                null,
                null,
                null,
                null,
                billingData
        );
    }

    /**
     * Get the checklist prompt for the given operation type.
     */
    public String getChecklistPrompt(OperationType operationType) {
        return getChecklistPrompt(operationType, null);
    }

    /**
     * Get the checklist prompt for the given operation type and project.
     * <p>
     * Registry-backed: the template comes from aa_prompt_template (a project
     * override such as SCB's fee-waiver wins over the default automatically),
     * and brand details come from aa_brand_attribute so one bank's name or
     * hotline never leaks into another project's demo. Brand variables are
     * computed for every checklist; templates that don't use them simply
     * ignore the extras — exactly how the old code computed all three
     * resolve* values regardless of operation type.
     */
    public String getChecklistPrompt(OperationType operationType, String projectName) {
        if (operationType == OperationType.NONE) {
            return "";
        }

        String bankName = brandService.attr(projectName, BrandService.BANK_NAME);
        Map<String, String> brandVars = Map.of(
                "bank_name", bankName,
                "bank_name_upper", bankName.toUpperCase(),
                "hotline", brandService.attr(projectName, BrandService.HOTLINE),
                "loan_email", brandService.attr(projectName, BrandService.LOAN_EMAIL));

        return promptService.render(
                TemplateKeys.CHECKLIST_PREFIX + operationType.name().toLowerCase(),
                projectName,
                brandVars);
    }
}
