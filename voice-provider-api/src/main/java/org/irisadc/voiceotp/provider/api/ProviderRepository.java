package org.irisadc.voiceotp.provider.api;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository {
    ProviderRecord create(ProviderScope scope, ProviderRecord record) throws VoiceProviderException;
    Optional<ProviderRecord> find(ProviderScope scope, String normalizedName);
    List<ProviderRecord> list(ProviderScope scope);
    ProviderRecord update(ProviderScope scope, ProviderRecord record, long expectedRevision)
            throws VoiceProviderException;
    void delete(ProviderScope scope, String normalizedName, long expectedRevision)
            throws VoiceProviderException;
}
