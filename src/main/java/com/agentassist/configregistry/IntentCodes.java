package com.agentassist.configregistry;

/**
 * Intent code constants (Part 2c — the OperationType enum is gone; intents are
 * open-ended strings defined by aa_intent rows). Constants exist ONLY for the
 * codes that Java logic references: the two sentinels, and the intents with a
 * dedicated Salesforce data fetcher. A new intent added via the registry needs
 * no constant — it flows through classification, project gating, checklist
 * lookup and filtered messages as plain data.
 */
public final class IntentCodes {

    private IntentCodes() {
    }

    /** Sentinel: no operation detected (never stored in aa_intent). */
    public static final String NONE = "NONE";

    /** Classifier answer for process/FAQ questions — maps to NONE downstream. */
    public static final String GENERAL = "GENERAL";

    // Intents with dedicated Salesforce fetchers in ChecklistService:
    public static final String FEE_WAIVER = "FEE_WAIVER";
    public static final String HOME_LOAN_CLOSURE = "HOME_LOAN_CLOSURE";
    public static final String POLICY = "POLICY";
    public static final String CLAIMS = "CLAIMS";
    public static final String TELCO = "TELCO";
    public static final String BILLING = "BILLING";
}
