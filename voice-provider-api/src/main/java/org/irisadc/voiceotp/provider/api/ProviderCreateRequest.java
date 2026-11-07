package org.irisadc.voiceotp.provider.api;

public record ProviderCreateRequest(
        String name, String ariUrl, String username, char[] ariPassword,
        String application, String outboundEndpoint, String dialplanContext,
        String dialplanExtension, String callerId, int ringTimeoutSeconds,
        int maxCallDurationSeconds, String language, boolean enabled) {

    public ProviderCreateRequest {
        ariPassword = ariPassword == null ? null : ariPassword.clone();
    }

    @Override
    public char[] ariPassword() {
        return ariPassword == null ? null : ariPassword.clone();
    }
}
