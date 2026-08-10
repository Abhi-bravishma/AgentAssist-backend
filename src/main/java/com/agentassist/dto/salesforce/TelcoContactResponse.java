package com.agentassist.dto.salesforce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from Salesforce telco-contacts API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelcoContactResponse {
    private boolean success;
    private TelcoContactInfo contact;
    private List<TelcoContact> data;

    /**
     * Salesforce sends PascalCase keys; without explicit mappings Jackson
     * looked for "name"/"mobilePhone" and every field silently stayed null —
     * which is why the customer showed as "null" in logs and prompts.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelcoContactInfo {
        @JsonProperty("Id")
        private String id;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("MobilePhone")
        private String mobilePhone;
        @JsonProperty("AccountId")
        private String accountId;
    }
}
