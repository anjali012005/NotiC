package io.github.anjali.notifyflow.management.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifyflow.security")
public class SecurityProperties {

    private boolean enabled = false;
    private Map<String, String> apiKeys = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(Map<String, String> apiKeys) {
        this.apiKeys = apiKeys == null ? new HashMap<>() : apiKeys;
    }

    public Optional<TenantPrincipal> resolvePrincipal(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            if (constantTimeEquals(apiKey, entry.getValue())) {
                return Optional.of(new TenantPrincipal(entry.getKey(), parseTenantId(entry.getKey()), "TENANT"));
            }
        }

        return Optional.empty();
    }

    private boolean constantTimeEquals(String presentedKey, String configuredKey) {
        return configuredKey != null && MessageDigest.isEqual(
                presentedKey.getBytes(StandardCharsets.UTF_8),
                configuredKey.getBytes(StandardCharsets.UTF_8));
    }

    private UUID parseTenantId(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
        }

        try {
            return UUID.fromString(tenantKey);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(tenantKey.getBytes());
        }
    }
}
