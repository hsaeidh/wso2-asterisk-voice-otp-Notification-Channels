package org.irisadc.voiceotp.provider.core;

import org.irisadc.voiceotp.provider.api.ProviderCreateRequest;
import org.irisadc.voiceotp.provider.api.ProviderUpdateRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProviderValidator {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_.-]{1,80}");
    private static final Set<String> SUPPORTED_LANGUAGES =
            Set.of("fa", "fa-IR", "fa_IR", "en", "en-US", "en_US");
    private final AriUrlPolicy ariUrlPolicy;

    public ProviderValidator(AriUrlPolicy ariUrlPolicy) {
        this.ariUrlPolicy = ariUrlPolicy;
    }

    public String normalizeName(String name) {
        if (name == null || !NAME.matcher(name.trim()).matches()) {
            throw new IllegalArgumentException("Provider name must be 1-64 safe ASCII characters");
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public void validate(ProviderCreateRequest request, int consumedPasswordLength) {
        normalizeName(request.name());
        validateCommon(request.ariUrl(), request.username(), request.application(),
                request.outboundEndpoint(), request.dialplanContext(), request.dialplanExtension(),
                request.callerId(), request.ringTimeoutSeconds(), request.maxCallDurationSeconds(),
                request.language());
        if (consumedPasswordLength < 1 || consumedPasswordLength > 512) {
            throw new IllegalArgumentException("ARI password is required and must be at most 512 characters");
        }
    }

    public void validate(ProviderUpdateRequest request, int consumedPasswordLength) {
        validateCommon(request.ariUrl(), request.username(), request.application(),
                request.outboundEndpoint(), request.dialplanContext(), request.dialplanExtension(),
                request.callerId(), request.ringTimeoutSeconds(), request.maxCallDurationSeconds(),
                request.language());
        if (consumedPasswordLength == 0 || consumedPasswordLength > 512) {
            throw new IllegalArgumentException("ARI password must contain 1-512 characters when supplied");
        }
    }

    private void validateCommon(String ariUrl, String username, String application,
                                String endpoint, String context, String extension, String callerId,
                                int ringTimeout, int maxDuration, String language) {
        if (username == null || username.isBlank() || username.length() > 128 || hasControl(username)) {
            throw new IllegalArgumentException("ARI username is required and must be at most 128 characters");
        }
        if (application == null || !SAFE_IDENTIFIER.matcher(application).matches()) {
            throw new IllegalArgumentException("ARI application identifier is invalid");
        }
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 128
                || hasControl(endpoint) || endpoint.contains("$") || endpoint.contains("${")) {
            throw new IllegalArgumentException("Outbound endpoint is invalid");
        }
        if (context == null || !SAFE_IDENTIFIER.matcher(context).matches()) {
            throw new IllegalArgumentException("Dialplan context is invalid");
        }
        if (extension == null || extension.isBlank() || extension.length() > 80
                || hasControl(extension) || extension.contains("$") || extension.contains("${")) {
            throw new IllegalArgumentException("Dialplan extension is invalid");
        }
        if (callerId != null && (callerId.length() > 32 || hasControl(callerId))) {
            throw new IllegalArgumentException("Caller ID is invalid");
        }
        if (ringTimeout < 5 || ringTimeout > 90) {
            throw new IllegalArgumentException("Ring timeout must be between 5 and 90 seconds");
        }
        if (maxDuration < 5 || maxDuration > 180) {
            throw new IllegalArgumentException("Maximum call duration must be between 5 and 180 seconds");
        }
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("Language is not supported");
        }
        if (ariUrl == null || ariUrl.length() > 2048) {
            throw new IllegalArgumentException("ARI URL is required and must be at most 2048 characters");
        }
        try {
            URI uri = new URI(ariUrl).normalize();
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getPath() == null
                    || !uri.getPath().startsWith("/")) {
                throw new IllegalArgumentException("ARI URL is invalid");
            }
            ariUrlPolicy.validate(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("ARI URL is invalid");
        }
    }

    private static boolean hasControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
