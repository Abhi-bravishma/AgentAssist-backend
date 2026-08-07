package com.agentassist.configregistry;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Result of the startup registry validation, read by the health indicator.
 * Volatile fields — written once by the validator, read by health probes.
 */
@Component
public class RegistryStatus {

    private volatile boolean checked = false;
    private volatile List<String> missingTemplateKeys = List.of();
    private volatile String error;

    void update(List<String> missingKeys) {
        this.missingTemplateKeys = List.copyOf(missingKeys);
        this.error = null;
        this.checked = true;
    }

    void markUnreachable(Exception e) {
        this.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        this.checked = true;
    }

    public boolean isChecked() {
        return checked;
    }

    public List<String> getMissingTemplateKeys() {
        return missingTemplateKeys;
    }

    public String getError() {
        return error;
    }

    public boolean isHealthy() {
        return checked && error == null && missingTemplateKeys.isEmpty();
    }
}
