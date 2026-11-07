package org.irisadc.voiceotp.provider.api;

/**
 * A null password means preserve the existing credential. A supplied password
 * can be consumed once; the internal buffer is then zeroed.
 */
public final class ProviderUpdateRequest {
    private final String ariUrl;
    private final String username;
    private char[] ariPassword;
    private final String application;
    private final String outboundEndpoint;
    private final String dialplanContext;
    private final String dialplanExtension;
    private final String callerId;
    private final int ringTimeoutSeconds;
    private final int maxCallDurationSeconds;
    private final String language;
    private final boolean enabled;

    public ProviderUpdateRequest(String ariUrl, String username, char[] ariPassword,
            String application, String outboundEndpoint, String dialplanContext,
            String dialplanExtension, String callerId, int ringTimeoutSeconds,
            int maxCallDurationSeconds, String language, boolean enabled) {
        this.ariUrl = ariUrl;
        this.username = username;
        this.ariPassword = ariPassword == null ? null : ariPassword.clone();
        this.application = application;
        this.outboundEndpoint = outboundEndpoint;
        this.dialplanContext = dialplanContext;
        this.dialplanExtension = dialplanExtension;
        this.callerId = callerId;
        this.ringTimeoutSeconds = ringTimeoutSeconds;
        this.maxCallDurationSeconds = maxCallDurationSeconds;
        this.language = language;
        this.enabled = enabled;
    }

    public String ariUrl() { return ariUrl; }
    public String username() { return username; }
    public String application() { return application; }
    public String outboundEndpoint() { return outboundEndpoint; }
    public String dialplanContext() { return dialplanContext; }
    public String dialplanExtension() { return dialplanExtension; }
    public String callerId() { return callerId; }
    public int ringTimeoutSeconds() { return ringTimeoutSeconds; }
    public int maxCallDurationSeconds() { return maxCallDurationSeconds; }
    public String language() { return language; }
    public boolean enabled() { return enabled; }

    public synchronized int passwordLength() {
        return ariPassword == null ? -1 : ariPassword.length;
    }

    public synchronized char[] consumeAriPassword() {
        if (ariPassword == null) return null;
        char[] copy = ariPassword.clone();
        java.util.Arrays.fill(ariPassword, '\0');
        ariPassword = null;
        return copy;
    }
}
