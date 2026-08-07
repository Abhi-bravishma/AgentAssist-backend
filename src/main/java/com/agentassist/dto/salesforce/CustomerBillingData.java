package com.agentassist.dto.salesforce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Customer billing position, built from the Salesforce Contact record.
 * <p>
 * Answers: billing summary, outstanding amount and due date, why a card is
 * restricted, and what clears it.
 * <p>
 * Due status and days overdue are computed HERE rather than left to the model.
 * The same record therefore always produces the same answer, and the model is
 * handed a conclusion instead of dates to do arithmetic on.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerBillingData {

    /** Payment position derived from the due date. */
    public enum DueStatus {
        OVERDUE,
        NOT_DUE_YET,
        NOTHING_DUE,
        UNKNOWN
    }

    /** Card position derived from how far past the due date the customer is. */
    public enum CardStatus {
        ACTIVE,
        AT_RISK,
        BLOCKED
    }

    /**
     * The Contact record carries no card status, so it is derived from the only real
     * signal available: whether anything is outstanding and how far past due it is.
     * Deriving it here rather than letting the model reason about it keeps the answer
     * deterministic - the same record always produces the same status and reason.
     */
    private static final int AT_RISK_AFTER_DAYS = 30;
    private static final int BLOCK_AFTER_DAYS = 60;

    /** Derived card position plus the reason to state to the customer. */
    public record CardPosition(CardStatus status, String reason) {}

    private String customerName;
    private String email;
    private String mobileNumber;

    private BigDecimal outstandingBalance;
    private BigDecimal minimumDue;
    private String dueDate;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal lastPayAmount;
    private String lastPayDate;
    private BigDecimal statementBalance;

    private String accountNumberMasked;
    private String cardNumberMasked;
    private String creditCard1NumberMasked;
    private String bankAccountNumberMasked;
    private String accountTypes;

    /**
     * Map the Salesforce response, masking every full account/card number so the
     * raw digits never reach a prompt or a log.
     */
    public static CustomerBillingData fromSalesforceResponse(ContactBillingResponse response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return null;
        }
        ContactBillingResponse.ContactRecord c = response.getData();

        return CustomerBillingData.builder()
                .customerName(c.getName())
                .email(c.getEmail())
                .mobileNumber(c.getMobilePhone())
                .outstandingBalance(c.getOutstandingBalance())
                .minimumDue(c.getMinimumDue())
                .dueDate(c.getDueDate())
                .currentBalance(c.getCurrentBalance())
                .availableBalance(c.getAvailableBalance())
                .lastPayAmount(c.getLastPayAmount())
                .lastPayDate(c.getLastPayDate())
                .statementBalance(c.getStatementBalance())
                .accountNumberMasked(mask(c.getAccountNumber()))
                .cardNumberMasked(mask(c.getCardNumber()))
                .creditCard1NumberMasked(mask(c.getCreditCard1Number()))
                .bankAccountNumberMasked(mask(c.getBankAccountNumber()))
                .accountTypes(c.getAccountTypes())
                .build();
    }

    /** True when the customer actually owes something. */
    public boolean hasOutstanding() {
        return outstandingBalance != null && outstandingBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Payment position. NOTHING_DUE wins over the date - a customer who owes
     * nothing is not "overdue" no matter how old the due date is.
     */
    public DueStatus dueStatus() {
        if (!hasOutstanding()) {
            return DueStatus.NOTHING_DUE;
        }
        LocalDate due = parsedDueDate();
        if (due == null) {
            return DueStatus.UNKNOWN;
        }
        return LocalDate.now().isAfter(due) ? DueStatus.OVERDUE : DueStatus.NOT_DUE_YET;
    }

    /** Days past the due date, or 0 when not overdue. */
    public long daysOverdue() {
        LocalDate due = parsedDueDate();
        if (due == null || dueStatus() != DueStatus.OVERDUE) {
            return 0;
        }
        return ChronoUnit.DAYS.between(due, LocalDate.now());
    }

    /**
     * Derive the card position from the payment position. Mirrors the agreed rule:
     * blocked once a payment is more than {@value #BLOCK_AFTER_DAYS} days overdue,
     * at risk from {@value #AT_RISK_AFTER_DAYS} days, otherwise active.
     */
    public CardPosition cardPosition() {
        if (!hasOutstanding()) {
            return new CardPosition(CardStatus.ACTIVE, "No outstanding balance on the account.");
        }
        DueStatus status = dueStatus();
        if (status == DueStatus.UNKNOWN) {
            return new CardPosition(CardStatus.ACTIVE,
                    "There is an outstanding balance but no due date on record, so no restriction applies.");
        }
        if (status == DueStatus.NOT_DUE_YET) {
            return new CardPosition(CardStatus.ACTIVE, "The payment is not due yet.");
        }
        long days = daysOverdue();
        if (days >= BLOCK_AFTER_DAYS) {
            return new CardPosition(CardStatus.BLOCKED,
                    "The outstanding balance has been unpaid for " + days + " days. Cards are blocked once a "
                    + "payment is more than " + BLOCK_AFTER_DAYS + " days overdue, and are reinstated once the "
                    + "balance is cleared.");
        }
        if (days >= AT_RISK_AFTER_DAYS) {
            return new CardPosition(CardStatus.AT_RISK,
                    "The payment is " + days + " days overdue. The card is still active but will be blocked if "
                    + "the balance is not cleared within " + BLOCK_AFTER_DAYS + " days of the due date.");
        }
        return new CardPosition(CardStatus.ACTIVE,
                "The payment is " + days + " day(s) overdue. The card remains active.");
    }

    private LocalDate parsedDueDate() {
        if (dueDate == null || dueDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dueDate.trim().substring(0, Math.min(10, dueDate.trim().length())));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Billing context for the AI. Amounts are written bare - the Salesforce record
     * carries no currency, so naming one would be inventing it.
     */
    public String toAiContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CUSTOMER BILLING DATA ===\n");
        sb.append("Customer Name: ").append(orNa(customerName)).append("\n");
        sb.append("Mobile: ").append(orNa(mobileNumber)).append("\n");
        if (accountTypes != null && !accountTypes.isBlank()) {
            sb.append("Products Held: ").append(accountTypes).append("\n");
        }
        sb.append("\n");

        sb.append("BILLING POSITION:\n");
        sb.append("  Outstanding Balance: ").append(amount(outstandingBalance)).append("\n");
        sb.append("  Minimum Due: ").append(amount(minimumDue)).append("\n");
        sb.append("  Due Date: ").append(orNa(dueDate)).append("\n");
        sb.append("  Statement Balance: ").append(amount(statementBalance)).append("\n");
        sb.append("  Current Balance: ").append(amount(currentBalance)).append("\n");
        sb.append("  Available Balance: ").append(amount(availableBalance)).append("\n");
        sb.append("  Last Payment: ").append(amount(lastPayAmount))
          .append(" on ").append(orNa(lastPayDate)).append("\n\n");

        // Conclusion computed in code - the AI must repeat this, not re-derive it
        DueStatus status = dueStatus();
        sb.append("PAYMENT STATUS (already determined - state this, do not recalculate):\n");
        switch (status) {
            case NOTHING_DUE -> sb.append("  [NOTHING DUE - the outstanding balance is zero. "
                    + "The account is settled and no payment is required.]\n");
            case OVERDUE -> sb.append("  [OVERDUE by ").append(daysOverdue())
                    .append(" day(s) - the due date of ").append(orNa(dueDate))
                    .append(" has passed and ").append(amount(outstandingBalance))
                    .append(" is still outstanding.]\n");
            case NOT_DUE_YET -> sb.append("  [NOT DUE YET - ").append(amount(outstandingBalance))
                    .append(" is payable by ").append(orNa(dueDate)).append(".]\n");
            case UNKNOWN -> sb.append("  [DUE DATE UNKNOWN - there is an outstanding balance of ")
                    .append(amount(outstandingBalance))
                    .append(" but no due date on record. Do not state a due date.]\n");
        }
        sb.append("\n");

        // Card position computed in code - the AI must state this, not invent its own rule
        CardPosition card = cardPosition();
        sb.append("CARD STATUS (already determined - state this, do not decide it yourself):\n");
        sb.append("  Status: ").append(card.status()).append("\n");
        sb.append("  Reason: ").append(card.reason()).append("\n");
        if (card.status() == CardStatus.BLOCKED) {
            sb.append("  To lift the restriction: clear the outstanding balance of ")
              .append(amount(outstandingBalance)).append(".\n");
        }
        sb.append("\n");

        sb.append("ACCOUNT IDENTIFIERS (masked - quote exactly, never complete the digits):\n");
        sb.append("  Account Number: ").append(orNa(accountNumberMasked)).append("\n");
        sb.append("  Card Number: ").append(orNa(cardNumberMasked)).append("\n");
        if (creditCard1NumberMasked != null) {
            sb.append("  Credit Card: ").append(creditCard1NumberMasked).append("\n");
        }
        if (bankAccountNumberMasked != null) {
            sb.append("  Bank Account: ").append(bankAccountNumberMasked).append("\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    /** Keep only the last 4 digits of an account or card number. */
    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return value;
        }
        return "****" + digits.substring(digits.length() - 4);
    }

    private static String amount(BigDecimal value) {
        return value == null ? "not available" : value.toPlainString();
    }

    private static String orNa(String value) {
        return (value == null || value.isBlank()) ? "not available" : value;
    }
}
