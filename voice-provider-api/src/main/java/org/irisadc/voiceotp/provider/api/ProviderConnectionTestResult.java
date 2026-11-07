package org.irisadc.voiceotp.provider.api;

import java.time.Instant;

public record ProviderConnectionTestResult(
        boolean successful, String diagnosticCode, long latencyMillis, Instant testedAt) {
    public ProviderConnectionTestResult {
        if (latencyMillis < 0) throw new IllegalArgumentException("latencyMillis must be >= 0");
        if (diagnosticCode == null || !diagnosticCode.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("diagnosticCode must be a sanitized machine code");
        }
        if (testedAt == null) throw new IllegalArgumentException("testedAt is required");
    }
}
