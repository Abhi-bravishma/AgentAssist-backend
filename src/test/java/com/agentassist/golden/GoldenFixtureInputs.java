package com.agentassist.golden;

import java.util.List;

/**
 * Deterministic inputs shared by the golden-prompt capture and the later
 * registry-backed golden tests. The CONTENT of these strings is irrelevant;
 * what matters is that both sides of the comparison use exactly the same
 * values, so a fixture diff can only mean the prompt template changed.
 *
 * DO NOT edit these values once fixtures have been captured — every golden
 * file under src/test/resources/golden was rendered from them.
 */
public final class GoldenFixtureInputs {

    private GoldenFixtureInputs() {
    }

    public static final String TEXT = "I am very unhappy with the delay in my refund.";

    public static final List<String> CONVERSATION = List.of(
            "customer : I want to waive my annual fee",
            "agent : Let me check your card details",
            "customer : My card ends in 1116");

    public static final String LATEST_MESSAGE = "My card ends in 1116";

    public static final String POLICY_CONTEXT =
            "Customer Name: Maria Santos\nPolicies: 2 active (Car POL-0014, Home POL-0012)";

    public static final String CHECKLIST_CONTEXT =
            "=== CHECKLIST GUIDE ===\nCustomer Name: Maria Santos\nCard: ****1116 (Rewards)";

    public static final String OPERATION_TYPE = "FEE_WAIVER";

    public static final String PREVIOUS_SUGGESTION =
            "Hi Maria, your Rewards card (1116) qualifies for fee waiver. Call (02) 88-700-700.";

    public static final String CUSTOMER_NAME = "Maria Santos";

    public static final List<String> TRANSCRIPT = List.of(
            "Customer: I want to waive my annual fee",
            "Agent: Let me check your card details",
            "Customer: Thank you, that resolved it");

    public static final List<String> AGENT_MESSAGES = List.of(
            "Hello, how can I help you today?",
            "According to our policy the annual fee can be waived after ₱30,000 spend.",
            "Is there anything else I can assist you with?");

    public static final String INTERACTION_ID = "golden-interaction-1";
}
