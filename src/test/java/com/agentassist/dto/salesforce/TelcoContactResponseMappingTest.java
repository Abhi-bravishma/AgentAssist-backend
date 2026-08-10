package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins Jackson mapping against the REAL Salesforce payload shape (captured
 * from the lab for +65...2183). Two things silently broke before:
 * TelcoContactInfo lacked @JsonProperty for the PascalCase keys (customer
 * name arrived null), and the embedded currentPlan OBJECT was not mapped at
 * all (validity said "not on record" while the dates were in the payload).
 */
class TelcoContactResponseMappingTest {

    private static final String REAL_PAYLOAD = """
            {"success":true,
             "contact":{"Id":"003x","Name":"Vyankatesh Adke","MobilePhone":"+6596542183","AccountId":"001x"},
             "data":[{
               "Id":"a02x","Name":"TC-00002","Contact__c":"003x",
               "Plan_Type__c":"Prepaid","Daily_Data_Limit__c":3,"Data_Used_Today__c":2.74,
               "Last_Recharge_Amount__c":110000,"Segment__c":"High Data",
               "Network_Status__c":"Normal","Is_Roaming_Active__c":true,
               "Roaming_Country__c":"Singapore","ARPU__c":165000,
               "CreatedDate":"2026-04-29T09:56:14.000+0000",
               "LastModifiedDate":"2026-08-06T11:53:08.000+0000",
               "currentPlan":{
                 "CustomerProductId":"a08x","Name":"CP-00031","OfferingId":"a07x",
                 "Offering":"Ultra 30","Status__c":"Expired",
                 "Activation_Date__c":"2026-06-27","Expiry_Date__c":"2026-07-27",
                 "Days_Remaining":-14,"Purchase_Amount__c":699}}]}
            """;

    @Test
    void realPayloadMapsNameAndEmbeddedCurrentPlan() throws Exception {
        TelcoContactResponse r = new ObjectMapper().readValue(REAL_PAYLOAD, TelcoContactResponse.class);

        assertNotNull(r.getContact());
        assertEquals("Vyankatesh Adke", r.getContact().getName());
        assertEquals("+6596542183", r.getContact().getMobilePhone());

        TelcoContact tc = r.getData().get(0);
        assertEquals("Prepaid", tc.getPlanType());
        assertNotNull(tc.getCurrentPlan(), "embedded currentPlan object must be mapped");
        assertEquals("Ultra 30", tc.getCurrentPlan().getOfferingName());
        assertEquals("Expired", tc.getCurrentPlan().getStatus());
        assertEquals("2026-06-27", tc.getCurrentPlan().getActivationDate());
        assertEquals("2026-07-27", tc.getCurrentPlan().getExpiryDate());
        assertEquals("a07x", tc.getCurrentPlan().getOfferingId());
    }
}
