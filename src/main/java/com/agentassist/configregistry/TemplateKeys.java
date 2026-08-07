package com.agentassist.configregistry;

import java.util.List;

/**
 * Every template key the application renders at runtime. Single source of
 * truth shared by the callers and the startup validator — adding a key here
 * makes the validator demand a PUBLISHED row for it.
 */
public final class TemplateKeys {

    private TemplateKeys() {
    }

    public static final String AI_ANALYZE_TEXT = "ai.analyze_text";
    public static final String AI_ANALYZE_CONVERSATION = "ai.analyze_conversation";
    public static final String AI_ANALYZE_CONVERSATION_WITH_CONTEXT = "ai.analyze_conversation_with_context";
    public static final String AI_ANALYZE_CONVERSATION_WITH_CHECKLIST = "ai.analyze_conversation_with_checklist";
    public static final String AI_TRANSLATE_TO_ENGLISH = "ai.translate_to_english";
    public static final String AI_TRANSLATE_FROM_ENGLISH = "ai.translate_from_english";
    public static final String AI_DETECT_LANGUAGE = "ai.detect_language";
    public static final String AI_DETECT_OPERATION = "ai.detect_operation";
    public static final String AI_OVERALL_SENTIMENT = "ai.overall_sentiment";
    public static final String AI_REGENERATE_SUGGESTIONS = "ai.regenerate_suggestions";
    public static final String AI_REGENERATE_SUGGESTIONS_WITH_CONTEXT = "ai.regenerate_suggestions_with_context";
    public static final String AI_FOLLOW_UP_CHECK = "ai.follow_up_check";
    public static final String AI_COMPLIANCE = "ai.compliance";

    public static final String CHECKLIST_PREFIX = "checklist.";
    public static final String CHECKLIST_FEE_WAIVER = "checklist.fee_waiver";
    public static final String CHECKLIST_HOME_LOAN_CLOSURE = "checklist.home_loan_closure";
    public static final String CHECKLIST_BILLING = "checklist.billing";
    public static final String CHECKLIST_POLICY = "checklist.policy";
    public static final String CHECKLIST_CLAIMS = "checklist.claims";
    public static final String CHECKLIST_TELCO = "checklist.telco";

    public static final String SYSTEM_NO_KNOWLEDGE_REPLY = "system.no_knowledge_reply";

    /** In-app RAG suggestion prompt (verbatim from bravishma-rag's agent-assist lane). */
    public static final String RAG_SUGGESTIONS = "rag.suggestions";

    /** Every key that must have a PUBLISHED default row for the app to function. */
    public static final List<String> ALL = List.of(
            AI_ANALYZE_TEXT,
            AI_ANALYZE_CONVERSATION,
            AI_ANALYZE_CONVERSATION_WITH_CONTEXT,
            AI_ANALYZE_CONVERSATION_WITH_CHECKLIST,
            AI_TRANSLATE_TO_ENGLISH,
            AI_TRANSLATE_FROM_ENGLISH,
            AI_DETECT_LANGUAGE,
            AI_DETECT_OPERATION,
            AI_OVERALL_SENTIMENT,
            AI_REGENERATE_SUGGESTIONS,
            AI_REGENERATE_SUGGESTIONS_WITH_CONTEXT,
            AI_FOLLOW_UP_CHECK,
            AI_COMPLIANCE,
            CHECKLIST_FEE_WAIVER,
            CHECKLIST_HOME_LOAN_CLOSURE,
            CHECKLIST_BILLING,
            CHECKLIST_POLICY,
            CHECKLIST_CLAIMS,
            CHECKLIST_TELCO,
            SYSTEM_NO_KNOWLEDGE_REPLY,
            RAG_SUGGESTIONS);
}
