package org.irisadc.voiceotp.provider.api;

public interface ProviderUsageChecker {
    boolean hasActiveDeliveries(ProviderScope scope, String providerName);
}
