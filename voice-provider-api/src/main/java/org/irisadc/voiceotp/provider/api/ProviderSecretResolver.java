package org.irisadc.voiceotp.provider.api;

import java.util.Optional;

public interface ProviderSecretResolver {
    Optional<char[]> resolve(ProviderScope scope, String secretReference) throws VoiceProviderException;
}
