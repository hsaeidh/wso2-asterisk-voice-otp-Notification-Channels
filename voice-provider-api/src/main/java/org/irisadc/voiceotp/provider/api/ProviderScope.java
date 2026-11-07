package org.irisadc.voiceotp.provider.api;

import java.util.Objects;

/**
 * Immutable scope resolved by a trusted WSO2 request-context adapter.
 * Never construct this value from untrusted request parameters.
 */
public record ProviderScope(String tenantDomain, String organizationId) {
    public ProviderScope {
        tenantDomain = requireText(tenantDomain, "tenantDomain");
        organizationId = requireText(organizationId, "organizationId");
    }

    public String storageKey() {
        return tenantDomain + "::" + organizationId;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
