package com.agentassist.dto.salesforce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filtered customer policy data for AI context.
 * Contains only relevant fields stripped of internal Salesforce IDs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPolicyData {
    private String customerName;
    private String mobileNumber;
    private List<ClaimInfo> claims;
    private List<PolicyInfo> policies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimInfo {
        private String claimNumber;
        private String status;
        private String policyNumber;
        private String policyName;
        private String createdDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyInfo {
        private String policyNumber;
        private String policyName;
        private long totalClaims;
        private long approvedClaims;
        private long rejectedClaims;
        private long pendingClaims;
    }

    /**
     * Convert Salesforce response to filtered CustomerPolicyData.
     */
    public static CustomerPolicyData fromSalesforceResponse(SalesforceResponse response) {
        if (response == null || !response.isSuccess()) {
            return null;
        }

        // Extract contact info
        String customerName = response.getContact() != null ? response.getContact().getName() : "Unknown";
        String mobileNumber = response.getContact() != null ? response.getContact().getMobilePhone() : "";

        // Extract claims
        List<ClaimInfo> claims = response.getData() != null
                ? response.getData().stream()
                    .map(claim -> ClaimInfo.builder()
                            .claimNumber(claim.getClaimNumber())
                            .status(claim.getStatus())
                            .policyNumber(claim.getPolicyDetails() != null ? claim.getPolicyDetails().getPolicyNumber() : "")
                            .policyName(claim.getPolicyDetails() != null ? claim.getPolicyDetails().getPolicyName() : "")
                            .createdDate(formatDate(claim.getCreatedDate()))
                            .build())
                    .toList()
                : List.of();

        // Extract unique policies with claim counts
        Map<String, List<ClaimInfo>> claimsByPolicy = claims.stream()
                .collect(Collectors.groupingBy(ClaimInfo::getPolicyNumber));

        List<PolicyInfo> policies = claimsByPolicy.entrySet().stream()
                .map(entry -> {
                    List<ClaimInfo> policyClaims = entry.getValue();
                    String policyName = policyClaims.isEmpty() ? "" : policyClaims.get(0).getPolicyName();
                    return PolicyInfo.builder()
                            .policyNumber(entry.getKey())
                            .policyName(policyName)
                            .totalClaims(policyClaims.size())
                            .approvedClaims(policyClaims.stream().filter(c -> "Approved".equalsIgnoreCase(c.getStatus())).count())
                            .rejectedClaims(policyClaims.stream().filter(c -> "Rejected".equalsIgnoreCase(c.getStatus())).count())
                            .pendingClaims(policyClaims.stream().filter(c -> "Pending".equalsIgnoreCase(c.getStatus())).count())
                            .build();
                })
                .toList();

        return CustomerPolicyData.builder()
                .customerName(customerName)
                .mobileNumber(mobileNumber)
                .claims(claims)
                .policies(policies)
                .build();
    }

    /**
     * Format ISO date to readable format.
     */
    private static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        // Extract just the date part (YYYY-MM-DD)
        if (isoDate.contains("T")) {
            return isoDate.substring(0, isoDate.indexOf("T"));
        }
        return isoDate;
    }

    /**
     * Convert to AI-friendly text context.
     */
    public String toAiContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CUSTOMER POLICY DATA ===\n");
        sb.append("Customer Name: ").append(customerName).append("\n");
        sb.append("Mobile: ").append(mobileNumber).append("\n\n");

        sb.append("--- POLICIES ---\n");
        if (policies != null && !policies.isEmpty()) {
            for (PolicyInfo policy : policies) {
                sb.append("Policy: ").append(policy.getPolicyNumber())
                        .append(" - ").append(policy.getPolicyName()).append("\n");
                sb.append("  Total Claims: ").append(policy.getTotalClaims())
                        .append(" (Approved: ").append(policy.getApprovedClaims())
                        .append(", Rejected: ").append(policy.getRejectedClaims())
                        .append(", Pending: ").append(policy.getPendingClaims())
                        .append(")\n");
            }
        } else {
            sb.append("No policies found.\n");
        }

        sb.append("\n--- CLAIMS ---\n");
        if (claims != null && !claims.isEmpty()) {
            for (ClaimInfo claim : claims) {
                sb.append("Claim: ").append(claim.getClaimNumber())
                        .append(" | Status: ").append(claim.getStatus())
                        .append(" | Policy: ").append(claim.getPolicyName())
                        .append(" (").append(claim.getPolicyNumber()).append(")")
                        .append(" | Date: ").append(claim.getCreatedDate())
                        .append("\n");
            }
        } else {
            sb.append("No claims found.\n");
        }

        return sb.toString();
    }
}
