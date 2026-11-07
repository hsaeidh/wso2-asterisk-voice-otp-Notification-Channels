package org.irisadc.voiceotp.provider.api;

public record ProviderUpdateRequest(
        String ariUrl, String username, char[] ariPassword,
        String application, String outboundEndpoint, String dialplanContext,
        String dialplanExtension, String callerId, int ringTimeoutSeconds,
        int maxCallDurationSeconds, String language, boolean enabled) {

    public ProviderUpdateRequest {
        ariPassword = ariPassword == null ? null : ariPassword.clone();
    }

    @Override
    public char[] ariPassword() {
        return ariPassword == null ? null : ariPassword.clone();
    }
}
