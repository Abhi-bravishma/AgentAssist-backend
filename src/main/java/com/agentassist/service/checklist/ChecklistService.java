package com.agentassist.service.checklist;

import com.agentassist.ai.AiProviderFactory;
import com.agentassist.configregistry.BrandService;
import com.agentassist.configregistry.ConfigRegistryException;
import com.agentassist.configregistry.IntentCodes;
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
 *
 * <p>Part 2c: intents are plain STRING codes from the registry — the old
 * OperationType enum is gone. A registry-added intent flows straight through:
 * the classifier knows it (2b), project gating applies, and the dispatch below
 * falls to a generic registry-backed context (checklist template if one
 * exists, no customer data). Only intents with a dedicated Salesforce fetcher
 * have a Java branch.</p>
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

    public boolean isOperationValidForProject(String operation, String projectName) {
        // Semantics live in aa_project_intent: null/blank/unknown project or a
        // project with no rows allows everything; NONE is always allowed.
        return intentRegistryService.isIntentAllowed(operation, projectName);
    }

    /**
     * Result of checklist detection and data fetch. Operation and filtered
     * intent are registry intent CODES ({@link IntentCodes#NONE} sentinels).
     */
    public record ChecklistContext(
            String operationType,
            String checklistPrompt,
            String customerDataContext,
            CustomerCreditCardData creditCardData,
            CustomerHomeLoanData homeLoanData,
            CustomerPolicyData policyData,
            CustomerTelcoData telcoData,
            String filteredIntent,  // Intent that was detected but filtered out due to project mismatch
            CustomerBillingData billingData
    ) {
        // Backward compatible constructor (without telcoData)
        public ChecklistContext(String operationType, String checklistPrompt, String customerDataContext,
                                CustomerCreditCardData creditCardData, CustomerHomeLoanData homeLoanData,
                                CustomerPolicyData policyData) {
            this(operationType, checklistPrompt, customerDataContext, creditCardData, homeLoanData, policyData, null, null, null);
        }

        // Backward compatible constructor (without telcoData, with filteredIntent)
        public ChecklistContext(String operationType, String checklistPrompt, String customerDataContext,
                                CustomerCreditCardData creditCardData, CustomerHomeLoanData homeLoanData,
                                CustomerPolicyData policyData, String filteredIntent) {
            this(operationType, checklistPrompt, customerDataContext, creditCardData, homeLoanData, policyData, null, filteredIntent, null);
        }

        public boolean hasContext() {
            return operationType != null
                    && !IntentCodes.NONE.equals(operationType)
                    && customerDataContext != null;
        }

        public String getFullContext() {
            if (!hasContext()) return null;
            if (checklistPrompt != null && !checklistPrompt.isEmpty()) {
                return checklistPrompt + "\n\n" + customerDataContext;
            }
            return customerDataContext;
        }

        public boolean wasIntentFiltered() {
            return filteredIntent != null && !IntentCodes.NONE.equals(filteredIntent);
        }
    }

    /**
     * Detect operation type from conversation messages using AI.
     * <p>
     * The classifier validates its own answer against ACTIVE registry intents
     * (+GENERAL), so any code returned here is registry-known. GENERAL maps to
     * the NONE sentinel; everything else — including intents added purely via
     * the registry — passes straight through.
     */
    public String detectOperation(List<String> conversationMessages) {
        if (conversationMessages == null || conversationMessages.isEmpty()) {
            return IntentCodes.NONE;
        }

        // Get the latest message for primary analysis
        String latestMessage = conversationMessages.get(conversationMessages.size() - 1);

        log.info("[Checklist] Using AI to detect operation type...");
        long startTime = System.currentTimeMillis();

        try {
            String aiResult = aiProviderFactory.active().detectOperationType(conversationMessages, latestMessage);

            String operationType = (aiResult == null || IntentCodes.GENERAL.equals(aiResult))
                    ? IntentCodes.NONE
                    : aiResult;

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Checklist] AI detected operation: {} in {}ms", operationType, duration);

            return operationType;

        } catch (Exception e) {
            log.error("[Checklist] AI detection failed, returning NONE: {}", e.getMessage());
            return IntentCodes.NONE;
        }
    }

    /**
     * Build checklist context by detecting operation and fetching customer data.
     * Uses original messages (when given) for better multi-language detection;
     * project gating comes from aa_project_intent via IntentRegistryService.
     */
    public ChecklistContext buildChecklistContext(List<String> englishMessages, List<String> originalMessages,
                                                  String mobileNumber, String projectName) {
        // Use original messages for detection if available, otherwise use English
        List<String> messagesForDetection = (originalMessages != null && !originalMessages.isEmpty())
                ? originalMessages
                : englishMessages;

        String operationType = detectOperation(messagesForDetection);

        // Filter operation based on projectName
        if (!IntentCodes.NONE.equals(operationType) && !isOperationValidForProject(operationType, projectName)) {
            log.info("[Checklist] Operation {} not valid for project {}, returning NONE with filteredIntent", operationType, projectName);
            // Return NONE but track the filtered intent so we can inform the user
            return new ChecklistContext(IntentCodes.NONE, null, null, null, null, null, operationType);
        }

        if (IntentCodes.NONE.equals(operationType)) {
            return new ChecklistContext(IntentCodes.NONE, null, null, null, null, null);
        }

        if (mobileNumber == null || mobileNumber.isBlank()) {
            log.warn("[Checklist] Operation detected but no mobile number provided");
            return new ChecklistContext(operationType, tryChecklistPrompt(operationType, projectName), null, null, null, null);
        }

        return switch (operationType) {
            case IntentCodes.FEE_WAIVER -> buildFeeWaiverContext(mobileNumber, projectName);
            case IntentCodes.HOME_LOAN_CLOSURE -> buildHomeLoanClosureContext(mobileNumber);
            case IntentCodes.POLICY, IntentCodes.CLAIMS -> buildPolicyContext(mobileNumber, operationType);
            case IntentCodes.TELCO -> buildTelcoContext(mobileNumber);
            case IntentCodes.BILLING -> buildBillingContext(mobileNumber, projectName);
            // Registry-added intent with no dedicated Salesforce fetcher:
            // generic context — checklist template if one exists, no customer
            // data, so suggestions fall through to the knowledge base while
            // project gating and filtered messages still apply.
            default -> {
                log.info("[Checklist] Intent {} has no dedicated data fetcher - generic registry context", operationType);
                yield new ChecklistContext(operationType, tryChecklistPrompt(operationType, projectName), null, null, null, null);
            }
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
            return new ChecklistContext(IntentCodes.FEE_WAIVER, getChecklistPrompt(IntentCodes.FEE_WAIVER, projectName), null, null, null, null);
        }

        String customerDataContext = creditCardData.toAiContext(projectName);
        log.info("[Checklist] FEE_WAIVER context built with {} credit cards for project: {}",
                creditCardData.getCreditCards().size(), projectName);

        return new ChecklistContext(
                IntentCodes.FEE_WAIVER,
                getChecklistPrompt(IntentCodes.FEE_WAIVER, projectName),
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
            return new ChecklistContext(IntentCodes.HOME_LOAN_CLOSURE, getChecklistPrompt(IntentCodes.HOME_LOAN_CLOSURE), null, null, null, null);
        }

        String customerDataContext = homeLoanData.toAiContext();
        log.info("[Checklist] HOME_LOAN_CLOSURE context built with {} home loans", homeLoanData.getHomeLoans().size());

        return new ChecklistContext(
                IntentCodes.HOME_LOAN_CLOSURE,
                getChecklistPrompt(IntentCodes.HOME_LOAN_CLOSURE),
                customerDataContext,
                null,
                homeLoanData,
                null
        );
    }

    /**
     * Build context for policy/claims inquiry.
     */
    private ChecklistContext buildPolicyContext(String mobileNumber, String operationType) {
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
            return new ChecklistContext(IntentCodes.TELCO, getChecklistPrompt(IntentCodes.TELCO), null, null, null, null, null, null, null);
        }

        String customerDataContext = telcoData.toAiContext();
        log.info("[Checklist] TELCO context built - customer: {}, products: {}",
                telcoData.getCustomerName(),
                telcoData.getCustomerProducts() != null ? telcoData.getCustomerProducts().size() : 0);

        return new ChecklistContext(
                IntentCodes.TELCO,
                getChecklistPrompt(IntentCodes.TELCO),
                customerDataContext,
                null,
                null,
                null,
                telcoData,
                null,
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
            return new ChecklistContext(IntentCodes.BILLING, getChecklistPrompt(IntentCodes.BILLING, projectName),
                    null, null, null, null);
        }

        log.info("[Checklist] BILLING context built - customer: {}, dueStatus: {}, cardStatus: {}",
                billingData.getCustomerName(), billingData.dueStatus(), billingData.cardPosition().status());

        return new ChecklistContext(
                IntentCodes.BILLING,
                getChecklistPrompt(IntentCodes.BILLING, projectName),
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
    public String getChecklistPrompt(String operationType) {
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
    public String getChecklistPrompt(String operationType, String projectName) {
        if (operationType == null || IntentCodes.NONE.equals(operationType)) {
            return "";
        }

        String bankName = brandService.attr(projectName, BrandService.BANK_NAME);
        Map<String, String> brandVars = Map.of(
                "bank_name", bankName,
                "bank_name_upper", bankName.toUpperCase(),
                "hotline", brandService.attr(projectName, BrandService.HOTLINE),
                "loan_email", brandService.attr(projectName, BrandService.LOAN_EMAIL));

        return promptService.render(
                TemplateKeys.CHECKLIST_PREFIX + operationType.toLowerCase(),
                projectName,
                brandVars);
    }

    /**
     * Checklist prompt for intents that may not have a template yet (registry-
     * added intents): null instead of a thrown ConfigRegistryException, so the
     * flow degrades to knowledge-base suggestions rather than failing.
     */
    private String tryChecklistPrompt(String operationType, String projectName) {
        try {
            return getChecklistPrompt(operationType, projectName);
        } catch (ConfigRegistryException e) {
            log.info("[Checklist] No checklist template for intent {} - continuing without one", operationType);
            return null;
        }
    }
}
