package com.agentassist.dto.salesforce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Combined telco customer data for AI context.
 * Contains customer info, current plan status, active products, and available plans.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTelcoData {
    private String customerName;
    private String mobileNumber;
    private TelcoContact currentPlanInfo;
    private List<TelcoCustomerProduct> customerProducts;
    private List<TelcoPlan> availablePlans;
    private TelcoPlan currentPlanDetails;

    // Indonesian Rupiah formatter
    private static final NumberFormat IDR_FORMAT = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    /**
     * Create CustomerTelcoData from API responses.
     */
    public static CustomerTelcoData fromResponses(
            TelcoContactResponse contactResponse,
            TelcoCustomerProductResponse productResponse,
            TelcoPlanResponse planResponse,
            TelcoPlan currentPlan) {

        String customerName = "Unknown";
        String mobileNumber = "";
        TelcoContact currentPlanInfo = null;

        if (contactResponse != null && contactResponse.isSuccess()) {
            if (contactResponse.getContact() != null) {
                customerName = contactResponse.getContact().getName();
                mobileNumber = contactResponse.getContact().getMobilePhone();
            }
            if (contactResponse.getData() != null && !contactResponse.getData().isEmpty()) {
                currentPlanInfo = contactResponse.getData().get(0);
            }
        }

        List<TelcoCustomerProduct> products = null;
        if (productResponse != null && productResponse.isSuccess()) {
            products = productResponse.getData();
        }

        List<TelcoPlan> plans = null;
        if (planResponse != null && planResponse.isSuccess()) {
            plans = planResponse.getData();
        }

        return CustomerTelcoData.builder()
                .customerName(customerName)
                .mobileNumber(mobileNumber)
                .currentPlanInfo(currentPlanInfo)
                .customerProducts(products)
                .availablePlans(plans)
                .currentPlanDetails(currentPlan)
                .build();
    }

    /**
     * Convert to AI-friendly text context for telco assistance.
     * All data is dynamically populated from API responses.
     */
    public String toAiContext() {
        StringBuilder sb = new StringBuilder();

        // Header with customer info
        sb.append("=== CUSTOMER TELCO DATA ===\n");
        sb.append("Customer Name: ").append(customerName).append("\n");
        sb.append("Mobile Number: ").append(mobileNumber).append("\n\n");

        // Current plan status
        sb.append("--- CURRENT PLAN STATUS ---\n");
        if (currentPlanInfo != null) {
            sb.append("Plan Type: ").append(nullSafe(currentPlanInfo.getPlanType())).append("\n");
            sb.append("Network Status: ").append(nullSafe(currentPlanInfo.getNetworkStatus())).append("\n");
            sb.append("Validity Date: ").append(nullSafe(currentPlanInfo.getValidityDate())).append("\n");
            sb.append("Segment: ").append(nullSafe(currentPlanInfo.getSegment())).append("\n");

            // Data usage
            sb.append("\nDATA USAGE:\n");
            sb.append("  Daily Data Limit: ").append(formatGB(currentPlanInfo.getDailyDataLimit())).append("\n");
            sb.append("  Data Used Today: ").append(formatGB(currentPlanInfo.getDataUsedToday())).append("\n");
            if (currentPlanInfo.getDailyDataLimit() != null && currentPlanInfo.getDataUsedToday() != null) {
                BigDecimal remaining = currentPlanInfo.getDailyDataLimit().subtract(currentPlanInfo.getDataUsedToday());
                sb.append("  Remaining Today: ").append(formatGB(remaining)).append("\n");
                double usagePercent = currentPlanInfo.getDataUsedToday()
                        .divide(currentPlanInfo.getDailyDataLimit(), 2, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();
                sb.append("  Usage: ").append(String.format("%.0f%%", usagePercent)).append("\n");
            }

            // Roaming status
            sb.append("\nROAMING STATUS:\n");
            sb.append("  Roaming Active: ").append(currentPlanInfo.isRoamingActive() ? "Yes" : "No").append("\n");
            if (currentPlanInfo.isRoamingActive() && currentPlanInfo.getRoamingCountry() != null) {
                sb.append("  Roaming Country: ").append(currentPlanInfo.getRoamingCountry()).append("\n");
            }

            // Last recharge
            sb.append("\nLAST RECHARGE:\n");
            sb.append("  Amount: ").append(formatIDR(currentPlanInfo.getLastRechargeAmount())).append("\n");
            sb.append("  ARPU: ").append(formatIDR(currentPlanInfo.getArpu())).append("\n");
        } else {
            sb.append("No current plan information available.\n");
        }

        // Current plan details (from plan lookup)
        if (currentPlanDetails != null) {
            sb.append("\n--- CURRENT PLAN DETAILS ---\n");
            sb.append("Plan Name: ").append(currentPlanDetails.getName()).append("\n");
            sb.append("Type: ").append(nullSafe(currentPlanDetails.getType())).append("\n");
            sb.append("Price: ").append(formatIDR(currentPlanDetails.getPrice())).append("\n");
            sb.append("Validity: ").append(currentPlanDetails.getValidity()).append(" days\n");
            sb.append("Data Benefit: ").append(nullSafe(currentPlanDetails.getDataBenefit())).append("\n");
            sb.append("Voice Benefit: ").append(nullSafe(currentPlanDetails.getVoiceBenefit())).append("\n");
            sb.append("SMS Benefit: ").append(nullSafe(currentPlanDetails.getSmsBenefit())).append("\n");
            if (currentPlanDetails.isOttBenefit()) {
                sb.append("OTT Benefits: ").append(nullSafe(currentPlanDetails.getOttDetails())).append("\n");
            }
        }

        // Customer's active products
        sb.append("\n--- CUSTOMER PRODUCTS ---\n");
        if (customerProducts != null && !customerProducts.isEmpty()) {
            List<TelcoCustomerProduct> activeProducts = customerProducts.stream()
                    .filter(p -> "Active".equalsIgnoreCase(p.getStatus()))
                    .collect(Collectors.toList());

            List<TelcoCustomerProduct> expiredProducts = customerProducts.stream()
                    .filter(p -> "Expired".equalsIgnoreCase(p.getStatus()))
                    .collect(Collectors.toList());

            sb.append("Active Products: ").append(activeProducts.size()).append("\n");
            sb.append("Expired Products: ").append(expiredProducts.size()).append("\n\n");

            if (!activeProducts.isEmpty()) {
                sb.append("ACTIVE PRODUCTS:\n");
                for (TelcoCustomerProduct product : activeProducts) {
                    sb.append("  - ").append(product.getName());
                    sb.append(" | Type: ").append(nullSafe(product.getType()));
                    sb.append(" | Expiry: ").append(nullSafe(product.getExpiryDate()));
                    if (product.getRemainingData() != null && product.getRemainingData().compareTo(BigDecimal.ZERO) > 0) {
                        sb.append(" | Remaining Data: ").append(formatGB(product.getRemainingData()));
                    }
                    sb.append(" | Price: ").append(formatIDR(product.getPurchaseAmount()));
                    sb.append("\n");
                }
            }
        } else {
            sb.append("No customer products found.\n");
        }

        // Available plans for recommendations
        sb.append("\n--- AVAILABLE PLANS FOR RECOMMENDATION ---\n");
        if (availablePlans != null && !availablePlans.isEmpty()) {
            // Group by type
            List<TelcoPlan> plans = availablePlans.stream()
                    .filter(p -> "Plan".equalsIgnoreCase(p.getType()) && p.isActive())
                    .sorted((a, b) -> {
                        if (a.isRecommended() != b.isRecommended()) return a.isRecommended() ? -1 : 1;
                        return Integer.compare(a.getPriority() != null ? a.getPriority() : 99,
                                               b.getPriority() != null ? b.getPriority() : 99);
                    })
                    .limit(5)
                    .collect(Collectors.toList());

            List<TelcoPlan> addons = availablePlans.stream()
                    .filter(p -> "Add-on".equalsIgnoreCase(p.getType()) && p.isActive())
                    .sorted((a, b) -> Integer.compare(a.getPriority() != null ? a.getPriority() : 99,
                                                       b.getPriority() != null ? b.getPriority() : 99))
                    .limit(5)
                    .collect(Collectors.toList());

            List<TelcoPlan> roaming = availablePlans.stream()
                    .filter(p -> "Roaming".equalsIgnoreCase(p.getType()) && p.isActive())
                    .collect(Collectors.toList());

            List<TelcoPlan> offers = availablePlans.stream()
                    .filter(p -> "Offer".equalsIgnoreCase(p.getType()) && p.isActive())
                    .collect(Collectors.toList());

            // Main Plans
            if (!plans.isEmpty()) {
                sb.append("\nMAIN PLANS (Top 5):\n");
                for (TelcoPlan plan : plans) {
                    sb.append("  ").append(plan.isRecommended() ? "⭐ " : "").append(plan.getName());
                    sb.append(" — ").append(formatIDR(plan.getPrice()));
                    sb.append(" / ").append(plan.getValidity()).append(" days\n");
                    sb.append("     Data: ").append(nullSafe(plan.getDataBenefit())).append("\n");
                    sb.append("     Voice: ").append(nullSafe(plan.getVoiceBenefit())).append("\n");
                    if (plan.isOttBenefit()) {
                        sb.append("     OTT: ").append(nullSafe(plan.getOttDetails())).append("\n");
                    }
                    sb.append("     Best for: ").append(nullSafe(plan.getRecommendedFor())).append("\n");
                }
            }

            // Add-ons
            if (!addons.isEmpty()) {
                sb.append("\nADD-ONS (Top 5):\n");
                for (TelcoPlan addon : addons) {
                    sb.append("  ").append(addon.getName());
                    sb.append(" — ").append(formatIDR(addon.getPrice()));
                    sb.append(" / ").append(addon.getValidity()).append(" days");
                    sb.append(" | ").append(nullSafe(addon.getDataBenefit()));
                    sb.append("\n");
                }
            }

            // Roaming
            if (!roaming.isEmpty()) {
                sb.append("\nROAMING PASSES:\n");
                for (TelcoPlan roam : roaming) {
                    sb.append("  ").append(roam.getName());
                    sb.append(" — ").append(formatIDR(roam.getPrice()));
                    sb.append(" / ").append(roam.getValidity()).append(" days");
                    sb.append(" | Countries: ").append(nullSafe(roam.getCountry()));
                    sb.append("\n");
                }
            }

            // Special Offers
            if (!offers.isEmpty()) {
                sb.append("\nSPECIAL OFFERS/PROMOS:\n");
                for (TelcoPlan offer : offers) {
                    sb.append("  🎁 ").append(offer.getName());
                    sb.append(" — ").append(formatIDR(offer.getPrice()));
                    sb.append(" / ").append(offer.getValidity()).append(" days\n");
                    sb.append("     ").append(nullSafe(offer.getDescription())).append("\n");
                }
            }
        } else {
            sb.append("No available plans loaded.\n");
        }

        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "N/A";
    }

    private String formatGB(BigDecimal gb) {
        if (gb == null) return "N/A";
        return String.format("%.2f GB", gb);
    }

    private String formatIDR(BigDecimal amount) {
        if (amount == null) return "N/A";
        // Format as Rp X.XXX (Indonesian style)
        return "Rp " + String.format("%,.0f", amount);
    }
}
