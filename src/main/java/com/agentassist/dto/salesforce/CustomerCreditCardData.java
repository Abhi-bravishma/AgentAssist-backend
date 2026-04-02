package com.agentassist.dto.salesforce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Processed credit card data for AI context.
 * Contains customer info and credit card details formatted for checklist matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCreditCardData {
    private String customerName;
    private String mobileNumber;
    private List<CreditCardInfo> creditCards;

    // Constants for checklist thresholds
    private static final BigDecimal NAFFL_SPEND_90D_THRESHOLD = new BigDecimal("30000");
    private static final BigDecimal REWARDS_ANNUAL_SPEND_MIN = new BigDecimal("180000");
    private static final BigDecimal REWARDS_ANNUAL_SPEND_MAX = new BigDecimal("250000");

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditCardInfo {
        private String cardId;
        private String cardNumber;
        private String cardType;
        private String cardStatus;
        private BigDecimal creditLimit;
        private BigDecimal annualFee;
        private boolean nafflEligible;
        private String activationDate;
        private String delinquencyStatus;
        private BigDecimal totalSpend;
        private BigDecimal eligibleSpend90D;
        private BigDecimal eligibleSpend12M;
        private String lastTransactionDate;
    }

    /**
     * Convert Salesforce response to CustomerCreditCardData.
     */
    public static CustomerCreditCardData fromSalesforceResponse(CreditCardResponse response) {
        if (response == null || !response.isSuccess()) {
            return null;
        }

        String customerName = response.getCustomer() != null ? response.getCustomer().getFullName() : "Unknown";
        String mobileNumber = response.getCustomer() != null ? response.getCustomer().getPhone() : "";

        List<CreditCardInfo> cards = response.getData() != null
                ? response.getData().stream()
                    .map(card -> CreditCardInfo.builder()
                            .cardId(card.getCardId())
                            .cardNumber(card.getCardNumber())
                            .cardType(card.getCardType())
                            .cardStatus(card.getCardStatus())
                            .creditLimit(card.getCreditLimit())
                            .annualFee(card.getAnnualFee())
                            .nafflEligible(card.isNafflEligible())
                            .activationDate(card.getActivationDate())
                            .delinquencyStatus(card.getDelinquencyStatus())
                            .totalSpend(card.getTotalSpend())
                            .eligibleSpend90D(card.getEligibleSpend90D())
                            .eligibleSpend12M(card.getEligibleSpend12M())
                            .lastTransactionDate(card.getLastTransactionDate())
                            .build())
                    .toList()
                : List.of();

        return CustomerCreditCardData.builder()
                .customerName(customerName)
                .mobileNumber(mobileNumber)
                .creditCards(cards)
                .build();
    }

    /**
     * Convert to AI-friendly text context for fee waiver checklist.
     * Uses default SGD currency.
     */
    public String toAiContext() {
        return toAiContext(null);
    }

    /**
     * Convert to AI-friendly text context for fee waiver checklist.
     * Currency is determined by projectName:
     * - METRO: PHP (Philippine Peso)
     * - SCB: SGD (Singapore Dollar)
     * - Default: SGD
     */
    public String toAiContext(String projectName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CUSTOMER CREDIT CARD DATA (FEE WAIVER CONTEXT) ===\n");
        sb.append("Customer Name: ").append(customerName).append("\n");
        sb.append("Mobile: ").append(mobileNumber).append("\n\n");

        // Determine currency based on project
        NumberFormat currencyFormat;
        String currencySymbol;
        if ("METRO".equalsIgnoreCase(projectName)) {
            currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            currencySymbol = "₱";
        } else {
            // SCB and default use SGD
            currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "SG"));
            currencySymbol = "S$";
        }

        if (creditCards != null && !creditCards.isEmpty()) {
            for (int i = 0; i < creditCards.size(); i++) {
                CreditCardInfo card = creditCards.get(i);
                sb.append("--- CREDIT CARD ").append(i + 1).append(" ---\n");
                sb.append("Card Number: ").append(card.getCardNumber()).append("\n");
                sb.append("Card Type: ").append(card.getCardType()).append("\n");
                sb.append("Card Status: ").append(card.getCardStatus()).append("\n");
                sb.append("Credit Limit: ").append(formatCurrency(card.getCreditLimit(), currencyFormat)).append("\n");
                sb.append("Annual Fee: ").append(formatCurrency(card.getAnnualFee(), currencyFormat)).append("\n");
                sb.append("NAFFL Eligible: ").append(card.isNafflEligible() ? "Yes" : "No").append("\n");
                sb.append("Activation Date: ").append(card.getActivationDate() != null ? card.getActivationDate() : "N/A").append("\n");
                sb.append("Delinquency Status: ").append(card.getDelinquencyStatus()).append("\n\n");

                // Late payment context (for late fee waiver queries)
                sb.append("LATE PAYMENT STATUS:\n");
                String delinquency = card.getDelinquencyStatus();
                sb.append("  Delinquency Status: ").append(delinquency != null ? delinquency : "N/A").append("\n");
                if (delinquency != null) {
                    if ("Current".equalsIgnoreCase(delinquency) || "None".equalsIgnoreCase(delinquency)) {
                        sb.append("  [GOOD STANDING - No late payments, eligible for late fee waiver as goodwill]\n");
                    } else if ("30 Days".equalsIgnoreCase(delinquency) || "30".equals(delinquency)) {
                        sb.append("  [FIRST-TIME LATE - May qualify for one-time goodwill waiver]\n");
                    } else {
                        sb.append("  [MULTIPLE LATE PAYMENTS - Waiver less likely, may need supervisor approval]\n");
                    }
                }
                sb.append("\n");

                // Spend analysis for checklist (for annual fee waiver queries)
                sb.append("SPEND ANALYSIS (FOR ANNUAL FEE WAIVER):\n");
                sb.append("  Total Spend: ").append(formatCurrency(card.getTotalSpend(), currencyFormat)).append("\n");
                sb.append("  Spend (Last 90 Days): ").append(formatCurrency(card.getEligibleSpend90D(), currencyFormat));

                // Check against NAFFL threshold
                if (card.getEligibleSpend90D() != null) {
                    if (card.getEligibleSpend90D().compareTo(NAFFL_SPEND_90D_THRESHOLD) >= 0) {
                        sb.append(" [MEETS 90-day NAFFL requirement of ").append(currencySymbol).append("30,000]");
                    } else {
                        BigDecimal remaining = NAFFL_SPEND_90D_THRESHOLD.subtract(card.getEligibleSpend90D());
                        sb.append(" [Needs ").append(formatCurrency(remaining, currencyFormat)).append(" more for NAFFL]");
                    }
                }
                sb.append("\n");

                sb.append("  Spend (Last 12 Months): ").append(formatCurrency(card.getEligibleSpend12M(), currencyFormat));

                // Check against annual spend requirement for Rewards/Cashback
                if (card.getEligibleSpend12M() != null &&
                    ("Rewards".equalsIgnoreCase(card.getCardType()) || "Cashback".equalsIgnoreCase(card.getCardType()))) {
                    if (card.getEligibleSpend12M().compareTo(REWARDS_ANNUAL_SPEND_MIN) >= 0) {
                        sb.append(" [MEETS annual spend requirement of ").append(currencySymbol).append("180,000-").append(currencySymbol).append("250,000]");
                    } else {
                        BigDecimal remaining = REWARDS_ANNUAL_SPEND_MIN.subtract(card.getEligibleSpend12M());
                        sb.append(" [Needs ").append(formatCurrency(remaining, currencyFormat)).append(" more for annual requirement]");
                    }
                }
                sb.append("\n\n");
            }
        } else {
            sb.append("No credit cards found for this customer.\n");
        }

        return sb.toString();
    }

    private String formatCurrency(BigDecimal amount, NumberFormat format) {
        if (amount == null) return "N/A";
        return format.format(amount);
    }
}
