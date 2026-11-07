package org.irisadc.voiceotp.provider.api;

import java.time.Instant;

public record ProviderSummary(
        String name, String provider, String ariUrl, String username,
        String application, String outboundEndpoint, String dialplanContext,
        String dialplanExtension, String callerId, int ringTimeoutSeconds,
        int maxCallDurationSeconds, String language, boolean enabled,
        boolean secretConfigured, ScopeSource scopeSource, long revision,
        Instant createdAt, Instant updatedAt) {

    public enum ScopeSource { LOCAL, INHERITED }
}
