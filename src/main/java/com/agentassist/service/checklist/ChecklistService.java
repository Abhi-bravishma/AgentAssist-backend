package com.agentassist.service.checklist;

import com.agentassist.ai.AiProvider;
import com.agentassist.dto.salesforce.CustomerCreditCardData;
import com.agentassist.dto.salesforce.CustomerHomeLoanData;
import com.agentassist.dto.salesforce.CustomerPolicyData;
import com.agentassist.service.salesforce.SalesforceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final AiProvider aiProvider;

    /**
     * Operation types that can be detected from conversation.
     */
    public enum OperationType {
        FEE_WAIVER,
        HOME_LOAN_CLOSURE,
        POLICY,
        CLAIMS,
        NONE
    }

    /**
     * Project-specific operation mapping.
     * METRO (Banking): FEE_WAIVER, HOME_LOAN_CLOSURE
     * ALLIANZ (Insurance): POLICY, CLAIMS
     * null/empty: All operations allowed
     */
    public boolean isOperationValidForProject(OperationType operation, String projectName) {
        if (projectName == null || projectName.isBlank()) {
            // No project filter - all operations allowed
            return true;
        }

        String project = projectName.toUpperCase().trim();

        return switch (project) {
            case "METRO" -> operation == OperationType.FEE_WAIVER
                    || operation == OperationType.HOME_LOAN_CLOSURE
                    || operation == OperationType.NONE;
            case "ALLIANZ" -> operation == OperationType.POLICY
                    || operation == OperationType.CLAIMS
                    || operation == OperationType.NONE;
            case "SCB" -> operation == OperationType.FEE_WAIVER
                    || operation == OperationType.NONE;
            default -> true; // Unknown project - allow all
        };
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
            OperationType filteredIntent  // Intent that was detected but filtered out due to project mismatch
    ) {
        // Backward compatible constructor
        public ChecklistContext(OperationType operationType, String checklistPrompt, String customerDataContext,
                                CustomerCreditCardData creditCardData, CustomerHomeLoanData homeLoanData,
                                CustomerPolicyData policyData) {
            this(operationType, checklistPrompt, customerDataContext, creditCardData, homeLoanData, policyData, null);
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
            String aiResult = aiProvider.detectOperationType(conversationMessages, latestMessage);

            OperationType operationType = switch (aiResult) {
                case "FEE_WAIVER" -> OperationType.FEE_WAIVER;
                case "HOME_LOAN_CLOSURE" -> OperationType.HOME_LOAN_CLOSURE;
                case "POLICY" -> OperationType.POLICY;
                case "CLAIMS" -> OperationType.CLAIMS;
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
     * Get the checklist prompt for the given operation type.
     */
    public String getChecklistPrompt(OperationType operationType) {
        return getChecklistPrompt(operationType, null);
    }

    /**
     * Get the checklist prompt for the given operation type and project.
     * Returns project-specific checklist when available.
     */
    public String getChecklistPrompt(OperationType operationType, String projectName) {
        // Check for project-specific checklists first
        if (projectName != null && !projectName.isBlank()) {
            String project = projectName.toUpperCase().trim();

            if ("SCB".equals(project) && operationType == OperationType.FEE_WAIVER) {
                return SCB_FEE_WAIVER_CHECKLIST;
            }
        }

        // Default checklists
        return switch (operationType) {
            case FEE_WAIVER -> FEE_WAIVER_CHECKLIST;
            case HOME_LOAN_CLOSURE -> HOME_LOAN_CLOSURE_CHECKLIST;
            case POLICY -> POLICY_CHECKLIST;
            case CLAIMS -> CLAIMS_CHECKLIST;
            case NONE -> "";
        };
    }

    // =====================================================
    // STATIC CHECKLISTS
    // =====================================================

    private static final String FEE_WAIVER_CHECKLIST = """
            === METROBANK CREDIT CARD FEE WAIVER CHECKLIST ===

            YOU ARE HELPING AN AGENT GUIDE A CUSTOMER THROUGH FEE WAIVER. USE THE CUSTOMER DATA BELOW TO GIVE SPECIFIC ADVICE.

            CHECKLIST FOR NEW CARDHOLDERS (Welcome Promos):
            ------------------------------------------------
            No Annual Fee for Life (NAFFL) Requirements:
            - Spend ₱30,000 within 90 days (for Titanium Mastercard)
            - Enroll in the Metrobank App
            - For Rewards Plus / Cashback Visa: maintain ₱180,000 – ₱250,000 annual spend

            Activation Requirement:
            - Activate the card within 60 days of approval
            - Use the card at least once

            CHECKLIST FOR EXISTING CARDHOLDERS (Manual Reversal):
            -----------------------------------------------------
            Call: (02) 88-700-700

            Options for Fee Waiver:
            1. Spend required amount (e.g., ₱20,000 within 30 days)
            2. Redeem reward points (e.g., 30,000 points = ₱2,500 fee)
            3. Enroll a bill in Bills2Pay
            4. Apply for a supplementary card (first is usually free)

            YOUR TASK:
            1. Check customer's card type and NAFFL eligibility from the data below
            2. Check their spend amounts (90-day and 12-month) against requirements
            3. Provide SPECIFIC advice based on their actual numbers
            4. If they qualify for NAFFL, tell them exactly why
            5. If they don't qualify, tell them exactly how much more they need to spend
            """;

    private static final String HOME_LOAN_CLOSURE_CHECKLIST = """
            === METROBANK HOME LOAN CLOSURE & FORECLOSURE CHECKLIST ===

            YOU ARE HELPING AN AGENT GUIDE A CUSTOMER THROUGH HOME LOAN CLOSURE. USE THE CUSTOMER DATA BELOW TO GIVE SPECIFIC ADVICE.

            LOAN PAYOFF (REDEMPTION) STEPS:
            --------------------------------
            1. Email: CLOD-ASFD@metrobank.com.ph
            2. Request Statement of Account (SOA)
            3. Provide: Customer name, Loan account number, Target payment date

            BEFORE PAYMENT - CHECK THESE:
            ------------------------------
            1. Pre-termination fee (check Prepayment_Penalty field)
            2. Lock-in period status (check Lockin_Period_End_Date)
            3. Charge-back fees (DST, mortgage registration, etc.)
            4. Legal status (must be Clear for smooth closure)

            AFTER FULL PAYMENT:
            -------------------
            - Collect Original Owner's Duplicate TCT (Transfer Certificate of Title)

            YOUR TASK:
            1. Check if foreclosure is allowed (Foreclosure_Allowed field)
            2. Check the outstanding amount and calculate with prepayment penalty
            3. Check if customer is still in lock-in period
            4. Check legal status - warn if not "Clear"
            5. Provide the exact steps with customer's actual loan account number
            6. Calculate estimated total payoff (Outstanding + Penalty)
            """;

    private static final String POLICY_CHECKLIST = """
            === INSURANCE POLICY INQUIRY CHECKLIST ===

            YOU ARE HELPING AN AGENT ASSIST A CUSTOMER WITH POLICY INQUIRIES. USE THE CUSTOMER DATA BELOW.

            ============================================================
            STEP 1: IDENTITY & POLICY VERIFICATION
            ============================================================
            From the customer data, verify:
            □ Customer Name
            □ Mobile Number
            □ Policy Number(s) - List all active policies

            If NO policies found:
            → Inform customer no policies on file
            → Suggest checking with different phone number
            → Offer to connect with sales for new policy

            ============================================================
            STEP 2: POLICY SUMMARY (Use actual data below)
            ============================================================
            For EACH policy from customer data, provide:
            □ Policy Name (e.g., Car Insurance, Travel Insurance, Home Insurance)
            □ Policy Number
            □ Claims Summary:
              - Total Claims filed
              - Approved Claims count
              - Rejected Claims count
              - Pending Claims count

            ============================================================
            STEP 3: CLAIMS OVERVIEW PER POLICY
            ============================================================
            If customer has claims, summarize:
            □ Which policies have pending claims
            □ Which policies have approved claims
            □ Any rejected claims they should know about

            ============================================================
            STEP 4: NEXT STEPS & ASSISTANCE
            ============================================================
            Offer help with:
            □ Filing a new claim
            □ Checking specific claim status
            □ Understanding policy coverage
            □ Policy renewal or upgrade

            YOUR TASK:
            1. Greet customer by name: "Hi [Customer Name],"
            2. State total number of policies found
            3. List EACH policy with:
               - Policy Name
               - Policy Number
               - Claims breakdown (X total: Y approved, Z pending, W rejected)
            4. Highlight any pending claims that need attention
            5. Ask how you can assist further
            """;

    private static final String CLAIMS_CHECKLIST = """
            === INSURANCE CLAIMS CHECKLIST ===

            YOU ARE HELPING AN AGENT GUIDE A CUSTOMER WITH CLAIMS. USE THE CUSTOMER DATA BELOW.

            ============================================================
            STEP 1: IDENTITY VERIFICATION
            ============================================================
            From the customer data, confirm:
            □ Customer Name
            □ Mobile Number
            □ Policies on file (with policy numbers)

            ============================================================
            STEP 2: EXISTING CLAIMS STATUS (Primary Task)
            ============================================================
            From the data, provide COMPLETE claims breakdown:

            For EACH claim, show:
            □ Claim Number
            □ Status (Approved / Rejected / Pending)
            □ Related Policy Name & Number
            □ Date Filed

            Summary by status:
            □ Total Claims: [count]
            □ ✅ Approved: [count] - These are settled
            □ ⏳ Pending: [count] - These are being processed
            □ ❌ Rejected: [count] - Customer may appeal

            ============================================================
            STEP 3: IF CUSTOMER WANTS TO FILE NEW CLAIM
            ============================================================
            Guide them based on policy type:

            🚗 CAR/MOTOR INSURANCE - Required documents:
            □ Photos of damage
            □ Police report / FIR (if accident)
            □ Driver's license copy
            □ Vehicle registration
            □ Other party details (if applicable)

            🏠 HOME/PROPERTY INSURANCE - Required documents:
            □ Photos/videos of damage
            □ Police report (if theft/vandalism)
            □ Repair estimates/quotes
            □ Proof of ownership

            🧳 TRAVEL INSURANCE - Required documents:
            □ Travel itinerary & tickets
            □ Medical reports (if medical claim)
            □ Police report (if theft/loss)
            □ Receipts for expenses

            🏥 LIFE/MEDICAL INSURANCE - Required documents:
            □ Medical reports & diagnosis
            □ Hospital bills & receipts
            □ Doctor's prescription

            ============================================================
            STEP 4: CLAIM PROCESS INFORMATION
            ============================================================
            Inform customer:
            □ Typical processing time: 7-14 business days
            □ Required: Bank account details for payout
            □ May need: Original receipts and invoices
            □ Helpline: Contact Customer Care for updates

            ============================================================
            STEP 5: ESCALATION TRIGGERS
            ============================================================
            🚨 Escalate to supervisor if:
            □ Injury or hospitalization involved
            □ Emergency assistance needed
            □ Customer disputes rejection
            □ High-value claim
            □ Customer is distressed

            YOUR TASK:
            1. Greet customer: "Hi [Customer Name],"
            2. State total claims found: "You have X claims on file"
            3. List EACH claim with:
               - Claim Number
               - Status (Approved/Pending/Rejected)
               - Policy it belongs to
               - Date filed
            4. Summarize: "X approved, Y pending, Z rejected"
            5. For PENDING claims - set expectation on timeline
            6. For REJECTED claims - offer to explain or escalate
            7. Ask if they want to file a NEW claim
            """;

    // =====================================================
    // STANDARD CHARTERED (SCB) CHECKLISTS
    // =====================================================

    private static final String SCB_FEE_WAIVER_CHECKLIST = """
            === STANDARD CHARTERED SINGAPORE CREDIT CARD FEE WAIVER CHECKLIST ===

            YOU ARE HELPING AN AGENT GUIDE A CUSTOMER THROUGH FEE WAIVER. USE THE CUSTOMER DATA BELOW TO GIVE SPECIFIC ADVICE.

            ============================================================
            ⚠️ IMPORTANT: FIRST DETECT WHAT TYPE OF FEE WAIVER CUSTOMER IS ASKING ABOUT
            ============================================================

            Read the customer's message carefully:
            - If they mention "LATE FEE", "late payment", "overdue", "missed payment" → Answer about LATE PAYMENT FEE WAIVER
            - If they mention "ANNUAL FEE", "yearly fee", "membership fee" → Answer about ANNUAL FEE WAIVER
            - If unclear, ASK: "Are you asking about late payment fee waiver or annual fee waiver?"

            DO NOT default to annual fee. Answer based on what customer actually asked!

            ============================================================
            ANNUAL FEE WAIVER - BY CARD TYPE
            ============================================================

            💳 SIMPLY CASH CARD:
            -----------------------------------------------------
            - Annual Fee: S$192.60 (GST inclusive)
            - Waiver Requirement: Spend S$10,000 in the past 12 months
            - Benefit: 1.5%% unlimited cashback on all eligible spend
            - Min Income: S$30,000 (Singaporean/PR) / S$60,000 (Foreigner)

            💳 SMART CARD:
            -----------------------------------------------------
            - Annual Fee: S$192.60 (GST inclusive)
            - Waiver Requirement: Spend S$1,500/month consistently (S$18,000/year)
            - Benefit: Up to 10%% cashback on dining, streaming, transport
            - Min Income: S$30,000 (Singaporean/PR) / S$60,000 (Foreigner)

            💳 REWARDS+ CARD:
            -----------------------------------------------------
            - Annual Fee: S$192.60 (GST inclusive)
            - Waiver Requirement: Spend S$12,000 in the past 12 months
            - Benefit: 4 miles per S$1 on foreign currency spend
            - Min Income: S$30,000 (Singaporean/PR) / S$60,000 (Foreigner)

            💳 UNLIMITED CARD:
            -----------------------------------------------------
            - Annual Fee: S$192.60 (GST inclusive)
            - Waiver Requirement: Spend S$15,000 in the past 12 months
            - Benefit: 1.5%% cashback + rewards points
            - Min Income: S$80,000

            ============================================================
            LATE PAYMENT FEE WAIVER
            ============================================================

            Late Payment Fee: S$100 per occurrence

            WAIVER ELIGIBILITY:
            □ First-time late payment - Usually waived as goodwill
            □ Good payment history (no late payments in past 12 months)
            □ Payment made within 7 days of due date
            □ Customer has been with SCB for 1+ years

            HOW TO REQUEST:
            1. Call 1800-747-7000 (24/7 hotline)
            2. Use SC Mobile app → Help → Request fee waiver
            3. Secure message via Online Banking

            ============================================================
            YOUR TASK - CHECK CUSTOMER DATA BELOW
            ============================================================

            STEP 1: IDENTIFY WHAT CUSTOMER IS ASKING
            -----------------------------------------
            Read their message - are they asking about LATE FEE or ANNUAL FEE?

            ============================================================
            IF CUSTOMER ASKS ABOUT LATE FEE / LATE PAYMENT FEE:
            ============================================================
            1. Check the LATE PAYMENT STATUS section in customer data
            2. Look at Delinquency Status:
               - "Current" or "None" = Good standing, first-time waiver likely
               - "30 Days" = First late, goodwill waiver possible
               - Other = Multiple late payments, less likely

            3. Respond with:
               - Their current payment status from the data
               - Whether they likely qualify for goodwill waiver
               - How to request: Call 1800-747-7000 or use SC Mobile app
               - Late fee amount: S$100 per occurrence

            ============================================================
            IF CUSTOMER ASKS ABOUT ANNUAL FEE:
            ============================================================
            1. Identify customer's card type from data
            2. Check their 12-month spend against waiver threshold:
               - Simply Cash: S$10,000
               - Smart: S$18,000 (S$1,500/month)
               - Rewards+: S$12,000
               - Unlimited: S$15,000

            3. If QUALIFIES: Tell them their spend exceeds the requirement
            4. If DOES NOT QUALIFY: Calculate exact shortfall and tell them how much more to spend

            """;
}
