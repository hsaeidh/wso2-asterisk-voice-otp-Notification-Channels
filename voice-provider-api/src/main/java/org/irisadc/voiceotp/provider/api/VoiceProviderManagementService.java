package org.irisadc.voiceotp.provider.api;

public interface VoiceProviderManagementService {
    ProviderSummary create(ProviderScope scope, ProviderCreateRequest request) throws VoiceProviderException;
    ProviderSummary get(ProviderScope scope, String name) throws VoiceProviderException;
    Page<ProviderSummary> list(ProviderScope scope, PageRequest page) throws VoiceProviderException;
    ProviderSummary update(ProviderScope scope, String name, ProviderUpdateRequest request,
                           long expectedRevision) throws VoiceProviderException;
    void delete(ProviderScope scope, String name, long expectedRevision) throws VoiceProviderException;
    ProviderConnectionTestResult testConnection(ProviderScope scope, String name) throws VoiceProviderException;
}
