package com.agentassist.dto.salesforce;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the telco validity derivation: Salesforce often has no validity field,
 * but the current plan's product carries activation/expiry dates — the
 * conclusion must be computed in Java from those, never left as "N/A" or the
 * literal "null days" for the model to parrot.
 */
class CustomerTelcoDataValidityTest {

    private static final LocalDate TODAY = LocalDate.now();

    private static TelcoCustomerProduct product(String offeringId, String type, String status,
                                                LocalDate activation, LocalDate expiry) {
        return TelcoCustomerProduct.builder()
                .name("XL Unlimited Turbo")
                .productOfferingId(offeringId)
                .type(type)
                .status(status)
                .activationDate(activation.toString())
                .expiryDate(expiry.toString())
                .build();
    }

    private static TelcoContact contact(String validityDate, String currentPlanId) {
        return TelcoContact.builder()
                .planType("Prepaid")
                .networkStatus("Active")
                .validityDate(validityDate)
                .currentPlanId(currentPlanId)
                .build();
    }

    private static CustomerTelcoData data(TelcoContact contact,
                                          List<TelcoCustomerProduct> products,
                                          TelcoPlan planDetails,
                                          List<TelcoPlan> availablePlans) {
        return CustomerTelcoData.builder()
                .customerName("Budi")
                .mobileNumber("0812000")
                .currentPlanInfo(contact)
                .customerProducts(products)
                .currentPlanDetails(planDetails)
                .availablePlans(availablePlans)
                .build();
    }

    @Test
    void derivesValidityFromProductDatesWhenSalesforceHasNoValidityField() {
        CustomerTelcoData d = data(
                contact(null, "PLAN1"),
                List.of(product("PLAN1", "Plan", "Active", TODAY.minusDays(22), TODAY.plusDays(8))),
                TelcoPlan.builder().name("Turbo").validity(null).build(),
                null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("8 day(s) remaining"), ctx);
        assertTrue(ctx.contains("VALIDITY STATUS"), ctx);
        assertTrue(ctx.contains("state this, do not recalculate"), ctx);
        // plan-details validity derived from the 30-day activation->expiry window
        assertTrue(ctx.contains("30 days (derived from activation"), ctx);
        assertFalse(ctx.contains("Validity Date: not on record"), ctx);
        assertFalse(ctx.contains("null days"), ctx);
    }

    @Test
    void expiredPlanSaysExpiredNotNa() {
        CustomerTelcoData d = data(
                contact(null, "PLAN1"),
                List.of(product("PLAN1", "Plan", "Active", TODAY.minusDays(33), TODAY.minusDays(3))),
                null, null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("EXPIRED 3 day(s) ago"), ctx);
    }

    @Test
    void contactValidityDateWinsOverProductDates() {
        CustomerTelcoData d = data(
                contact(TODAY.plusDays(5).toString(), "PLAN1"),
                List.of(product("PLAN1", "Plan", "Active", TODAY.minusDays(22), TODAY.plusDays(8))),
                null, null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("5 day(s) remaining"), ctx);
        assertTrue(ctx.contains("contact record"), ctx);
    }

    @Test
    void fallsBackToSingleActivePlanProductWithoutIdLinkage() {
        // No currentPlanId, one unambiguous active Plan product -> still derived
        CustomerTelcoData d = data(
                contact(null, null),
                List.of(
                        product("OTHER", "Plan", "Active", TODAY.minusDays(10), TODAY.plusDays(20)),
                        product("ADDON1", "Add-on", "Active", TODAY.minusDays(5), TODAY.plusDays(2))),
                null, null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("20 day(s) remaining"), ctx);
    }

    @Test
    void ambiguousProductsListPerProductValidityInsteadOfHidingDates() {
        // Two active plan products and no linkage id: we still never guess
        // which one is THE current plan, but the dates are sitting right there
        // - so validity is computed for EVERY dated product and the model is
        // told to state those. "Not on record" must not appear.
        CustomerTelcoData d = data(
                contact(null, null),
                List.of(
                        product("A", "Plan", "Active", TODAY.minusDays(10), TODAY.plusDays(20)),
                        product("B", "Plan", "Active", TODAY.minusDays(5), TODAY.plusDays(2))),
                null, null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("VALIDITY BY PRODUCT"), ctx);
        assertTrue(ctx.contains("state these, do not recalculate"), ctx);
        assertTrue(ctx.contains("20 day(s) remaining"), ctx);
        assertTrue(ctx.contains("2 day(s) remaining"), ctx);
        assertFalse(ctx.contains("Validity Date: not on record"), ctx);
    }

    @Test
    void expiredCurrentPlanFromNestedContactObjectSaysExpired() {
        // The live +65...2183 record: no Validity_Date__c, no Current_Plan__c
        // id - just the embedded currentPlan OBJECT whose plan is EXPIRED.
        // "Your plan expired N days ago" is exactly what validity must say.
        TelcoContact c = contact(null, null);
        c.setCurrentPlan(TelcoContact.CurrentPlanRef.builder()
                .customerProductId("CP1").offeringId("OFF1").offeringName("Ultra 30")
                .status("Expired")
                .activationDate(TODAY.minusDays(44).toString())
                .expiryDate(TODAY.minusDays(14).toString())
                .build());
        CustomerTelcoData d = data(
                c,
                List.of(
                        product("OFF1", "Plan", "Expired", TODAY.minusDays(44), TODAY.minusDays(14)),
                        product("ROAM1", "Roaming", "Active", TODAY.minusDays(5), TODAY.plusDays(2))),
                null, null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("EXPIRED 14 day(s) ago"), ctx);
        assertTrue(ctx.contains("'Ultra 30'"), ctx);
        assertTrue(ctx.contains("state this, do not recalculate"), ctx);
        // the active roaming pass still shows its own days left in the product list
        assertTrue(ctx.contains("(2 day(s) left)"), ctx);
        assertFalse(ctx.contains("not on record"), ctx);
    }

    @Test
    void perProductValidityCoversExpiredProductsToo() {
        CustomerTelcoData d = data(
                contact(null, null),
                List.of(
                        product("A", "Plan", "Active", TODAY.minusDays(10), TODAY.plusDays(20)),
                        product("B", "Plan", "Active", TODAY.minusDays(40), TODAY.minusDays(4))),
                null, null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("20 day(s) remaining"), ctx);
        assertTrue(ctx.contains("EXPIRED 4 day(s) ago"), ctx);
    }

    @Test
    void honestAbsenceWhenNoDatesAnywhere() {
        CustomerTelcoData d = data(
                contact(null, null),
                List.of(),
                TelcoPlan.builder().name("Turbo").validity(null).build(),
                List.of(TelcoPlan.builder().name("Catalog Plan").type("Plan")
                        .active(true).validity(null).build()));

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("Validity Date: not on record"), ctx);
        assertTrue(ctx.contains("Validity: not on record"), ctx);
        assertTrue(ctx.contains("validity not on record"), ctx);
        assertFalse(ctx.contains("null days"), ctx);
    }

    @Test
    void explicitSalesforceValidityStillWins() {
        CustomerTelcoData d = data(
                contact(null, "PLAN1"),
                List.of(product("PLAN1", "Plan", "Active", TODAY.minusDays(22), TODAY.plusDays(8))),
                TelcoPlan.builder().name("Turbo").validity(30).build(),
                null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("Validity: 30 days\n"), ctx);
    }

    @Test
    void activeProductsShowActivationAndDaysLeft() {
        CustomerTelcoData d = data(
                contact(null, "PLAN1"),
                List.of(product("PLAN1", "Plan", "Active", TODAY.minusDays(22), TODAY.plusDays(8))),
                null, null);

        String ctx = d.toAiContext();
        assertTrue(ctx.contains("Activated: " + TODAY.minusDays(22)), ctx);
        assertTrue(ctx.contains("(8 day(s) left)"), ctx);
    }
}
