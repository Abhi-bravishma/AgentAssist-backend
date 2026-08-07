package com.agentassist.configregistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the exact semantics of the old resolveBankName/resolveHotline/
 * resolveLoanEmail switches, including the §4.6 bank_name asymmetry.
 */
class BrandServiceTest extends ConfigRegistryTestBase {

    private static final String NEUTRAL_HOTLINE =
            "the customer service number printed on the back of the card";
    private static final String NEUTRAL_LOAN_EMAIL =
            "the loan servicing email address on the customer's statement";

    @Test
    void metroHasAllThreeAttributes() {
        assertEquals("Metrobank", brandService.attr("METRO", BrandService.BANK_NAME));
        assertEquals("(02) 88-700-700", brandService.attr("METRO", BrandService.HOTLINE));
        assertEquals("CLOD-ASFD@metrobank.com.ph", brandService.attr("METRO", BrandService.LOAN_EMAIL));
    }

    @Test
    void scbHasBankNameButInheritsGlobalHotlineAndLoanEmail() {
        assertEquals("Standard Chartered", brandService.attr("SCB", BrandService.BANK_NAME));
        assertEquals(NEUTRAL_HOTLINE, brandService.attr("SCB", BrandService.HOTLINE));
        assertEquals(NEUTRAL_LOAN_EMAIL, brandService.attr("SCB", BrandService.LOAN_EMAIL));
    }

    @Test
    void nullOrBlankProjectUsesGlobalDefaults() {
        assertEquals("your bank", brandService.attr(null, BrandService.BANK_NAME));
        assertEquals("your bank", brandService.attr("  ", BrandService.BANK_NAME));
        assertEquals(NEUTRAL_HOTLINE, brandService.attr(null, BrandService.HOTLINE));
        assertEquals(NEUTRAL_LOAN_EMAIL, brandService.attr(null, BrandService.LOAN_EMAIL));
    }

    @Test
    void unknownProjectBankNameIsTheProjectItself_caseAndTrimPreserved() {
        // §4.6: the old switch's default branch returned projectName.trim()
        assertEquals("Hospitality", brandService.attr(" Hospitality ", BrandService.BANK_NAME));
        assertEquals("HOSPITALITY", brandService.attr("HOSPITALITY", BrandService.BANK_NAME));
    }

    @Test
    void unknownProjectHotlineAndLoanEmailFallBackToGlobals() {
        assertEquals(NEUTRAL_HOTLINE, brandService.attr("HOSPITALITY", BrandService.HOTLINE));
        assertEquals(NEUTRAL_LOAN_EMAIL, brandService.attr("HOSPITALITY", BrandService.LOAN_EMAIL));
    }

    @Test
    void knownProjectWithoutBankNameRowResolvesToItsOwnCode() {
        // ALLIANZ is seeded as a project but has no brand rows — the old
        // switch's default branch applied to it too.
        assertEquals("ALLIANZ", brandService.attr("ALLIANZ", BrandService.BANK_NAME));
    }

    @Test
    void projectCodeLookupIsCaseInsensitive() {
        assertEquals("Metrobank", brandService.attr("metro", BrandService.BANK_NAME));
    }

    @Test
    void unknownKeyWithNoGlobalRowThrows() {
        assertThrows(ConfigRegistryException.class,
                () -> brandService.attr("METRO", "nonexistent_key"));
    }
}
