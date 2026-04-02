package com.agentassist.dto.salesforce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Processed home loan data for AI context.
 * Contains customer info and home loan details formatted for foreclosure checklist.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerHomeLoanData {
    private String customerName;
    private String mobileNumber;
    private List<HomeLoanInfo> homeLoans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HomeLoanInfo {
        private String loanId;
        private String loanAccountNumber;
        private String loanStatus;
        private BigDecimal sanctionedAmount;
        private BigDecimal outstandingAmount;
        private BigDecimal interestRate;
        private String loanStartDate;
        private String lockinPeriodEndDate;
        private boolean foreclosureAllowed;
        private BigDecimal prepaymentPenalty;
        private String legalStatus;
        private String lastPaymentDate;
        private String emiStatus;
    }

    /**
     * Convert Salesforce response to CustomerHomeLoanData.
     */
    public static CustomerHomeLoanData fromSalesforceResponse(HomeLoanResponse response) {
        if (response == null || !response.isSuccess()) {
            return null;
        }

        String customerName = response.getCustomer() != null ? response.getCustomer().getFullName() : "Unknown";
        String mobileNumber = response.getCustomer() != null ? response.getCustomer().getPhone() : "";

        List<HomeLoanInfo> loans = response.getData() != null
                ? response.getData().stream()
                    .map(loan -> HomeLoanInfo.builder()
                            .loanId(loan.getLoanId())
                            .loanAccountNumber(loan.getLoanAccountNumber())
                            .loanStatus(loan.getLoanStatus())
                            .sanctionedAmount(loan.getSanctionedAmount())
                            .outstandingAmount(loan.getOutstandingAmount())
                            .interestRate(loan.getInterestRate())
                            .loanStartDate(loan.getLoanStartDate())
                            .lockinPeriodEndDate(loan.getLockinPeriodEndDate())
                            .foreclosureAllowed(loan.isForeclosureAllowed())
                            .prepaymentPenalty(loan.getPrepaymentPenalty())
                            .legalStatus(loan.getLegalStatus())
                            .lastPaymentDate(loan.getLastPaymentDate())
                            .emiStatus(loan.getEmiStatus())
                            .build())
                    .toList()
                : List.of();

        return CustomerHomeLoanData.builder()
                .customerName(customerName)
                .mobileNumber(mobileNumber)
                .homeLoans(loans)
                .build();
    }

    /**
     * Convert to AI-friendly text context for home loan closure checklist.
     */
    public String toAiContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CUSTOMER HOME LOAN DATA (FORECLOSURE CONTEXT) ===\n");
        sb.append("Customer Name: ").append(customerName).append("\n");
        sb.append("Mobile: ").append(mobileNumber).append("\n\n");

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        LocalDate today = LocalDate.now();

        if (homeLoans != null && !homeLoans.isEmpty()) {
            for (int i = 0; i < homeLoans.size(); i++) {
                HomeLoanInfo loan = homeLoans.get(i);
                sb.append("--- HOME LOAN ").append(i + 1).append(" ---\n");
                sb.append("Loan Account Number: ").append(loan.getLoanAccountNumber()).append("\n");
                sb.append("Loan Status: ").append(loan.getLoanStatus()).append("\n");
                sb.append("Sanctioned Amount: ").append(formatCurrency(loan.getSanctionedAmount(), currencyFormat)).append("\n");
                sb.append("Outstanding Amount: ").append(formatCurrency(loan.getOutstandingAmount(), currencyFormat)).append("\n");
                sb.append("Interest Rate: ").append(loan.getInterestRate() != null ? loan.getInterestRate() + "%" : "N/A").append("\n");
                sb.append("Loan Start Date: ").append(loan.getLoanStartDate() != null ? loan.getLoanStartDate() : "N/A").append("\n");
                sb.append("EMI Status: ").append(loan.getEmiStatus()).append("\n");
                sb.append("Last Payment Date: ").append(loan.getLastPaymentDate() != null ? loan.getLastPaymentDate() : "N/A").append("\n\n");

                // Foreclosure eligibility analysis
                sb.append("FORECLOSURE ANALYSIS:\n");
                sb.append("  Foreclosure Allowed: ").append(loan.isForeclosureAllowed() ? "YES" : "NO");
                if (!loan.isForeclosureAllowed()) {
                    sb.append(" [CANNOT proceed with foreclosure]");
                }
                sb.append("\n");

                // Lock-in period check
                sb.append("  Lock-in Period End Date: ").append(loan.getLockinPeriodEndDate() != null ? loan.getLockinPeriodEndDate() : "N/A");
                if (loan.getLockinPeriodEndDate() != null) {
                    try {
                        LocalDate lockinEnd = LocalDate.parse(loan.getLockinPeriodEndDate());
                        if (today.isBefore(lockinEnd)) {
                            sb.append(" [STILL IN LOCK-IN PERIOD - penalties may apply]");
                        } else {
                            sb.append(" [Lock-in period ended - no lock-in penalties]");
                        }
                    } catch (Exception e) {
                        // Ignore date parsing errors
                    }
                }
                sb.append("\n");

                // Prepayment penalty
                sb.append("  Prepayment Penalty: ");
                if (loan.getPrepaymentPenalty() != null && loan.getPrepaymentPenalty().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append(loan.getPrepaymentPenalty()).append("%");
                    // Calculate penalty amount
                    if (loan.getOutstandingAmount() != null) {
                        BigDecimal penaltyAmount = loan.getOutstandingAmount()
                                .multiply(loan.getPrepaymentPenalty())
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                        sb.append(" (Estimated: ").append(formatCurrency(penaltyAmount, currencyFormat)).append(")");
                    }
                } else {
                    sb.append("None");
                }
                sb.append("\n");

                // Legal status
                sb.append("  Legal Status: ").append(loan.getLegalStatus());
                if (!"Clear".equalsIgnoreCase(loan.getLegalStatus())) {
                    sb.append(" [WARNING: Legal issues may affect foreclosure]");
                }
                sb.append("\n\n");

                // Total payoff summary
                if (loan.getOutstandingAmount() != null) {
                    BigDecimal totalPayoff = loan.getOutstandingAmount();
                    if (loan.getPrepaymentPenalty() != null && loan.getPrepaymentPenalty().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal penaltyAmount = loan.getOutstandingAmount()
                                .multiply(loan.getPrepaymentPenalty())
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                        totalPayoff = totalPayoff.add(penaltyAmount);
                    }
                    sb.append("ESTIMATED TOTAL PAYOFF: ").append(formatCurrency(totalPayoff, currencyFormat));
                    sb.append(" (Outstanding + Prepayment Penalty)\n");
                    sb.append("Note: Request official SOA from CLOD-ASFD@metrobank.com.ph for exact amount.\n\n");
                }
            }
        } else {
            sb.append("No home loans found for this customer.\n");
        }

        return sb.toString();
    }

    private String formatCurrency(BigDecimal amount, NumberFormat format) {
        if (amount == null) return "N/A";
        return format.format(amount);
    }
}
