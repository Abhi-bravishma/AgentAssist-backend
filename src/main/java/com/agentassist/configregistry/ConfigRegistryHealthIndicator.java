package com.agentassist.configregistry;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Surfaces the startup validation as {@code /actuator/health} detail
 * "configRegistry", so a deploy against an unseeded or unreachable registry
 * reports DOWN instead of looking healthy until the first customer message.
 */
@Component("configRegistry")
@RequiredArgsConstructor
public class ConfigRegistryHealthIndicator implements HealthIndicator {

    private final RegistryStatus status;

    @Override
    public Health health() {
        if (!status.isChecked()) {
            return Health.unknown().withDetail("reason", "validation has not run yet").build();
        }
        if (status.getError() != null) {
            return Health.down().withDetail("error", status.getError()).build();
        }
        if (!status.getMissingTemplateKeys().isEmpty()) {
            return Health.down()
                    .withDetail("missingTemplateKeys", status.getMissingTemplateKeys())
                    .build();
        }
        return Health.up().withDetail("templateKeys", TemplateKeys.ALL.size()).build();
    }
}
