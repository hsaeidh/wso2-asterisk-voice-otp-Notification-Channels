package org.irisadc.voiceotp.provider.api;

public interface ProviderSecretStore {
    /** Stores a secret and returns an opaque reference. Implementations must not log the value. */
    String store(ProviderScope scope, String providerName, char[] secret) throws VoiceProviderException;

    /** Deletes an opaque reference. Must be idempotent. */
    void delete(ProviderScope scope, String secretReference) throws VoiceProviderException;
}
