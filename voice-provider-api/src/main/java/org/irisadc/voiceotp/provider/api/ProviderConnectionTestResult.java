package org.irisadc.voiceotp.provider.api;

import java.time.Instant;

public record ProviderConnectionTestResult(
        boolean successful, String diagnosticCode, long latencyMillis, Instant testedAt) {
    public ProviderConnectionTestResult {
        if (latencyMillis < 0) throw new IllegalArgumentException("latencyMillis must be >= 0");
    }
}
