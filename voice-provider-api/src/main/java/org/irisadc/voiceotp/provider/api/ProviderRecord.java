package org.irisadc.voiceotp.provider.api;

import java.time.Instant;

/**
 * Persistence model. It contains only an opaque secret reference, never secret material.
 */
public record ProviderRecord(
        String name, String ariUrl, String username, String secretReference,
        String application, String outboundEndpoint, String dialplanContext,
        String dialplanExtension, String callerId, int ringTimeoutSeconds,
        int maxCallDurationSeconds, String language, boolean enabled,
        long revision, Instant createdAt, Instant updatedAt) {
}
