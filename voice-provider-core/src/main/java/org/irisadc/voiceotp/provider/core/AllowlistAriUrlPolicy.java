package org.irisadc.voiceotp.provider.core;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Host allowlist policy. Production deployments must additionally enforce
 * DNS/IP pinning at connection time to mitigate DNS rebinding.
 */
public final class AllowlistAriUrlPolicy implements AriUrlPolicy {
    private final Set<String> allowedHosts;

    public AllowlistAriUrlPolicy(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("At least one ARI host must be allowlisted");
        }
    }

    @Override
    public void validate(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("ARI URL must use HTTPS");
        }
        if (uri.getHost() == null || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("ARI host is not allowlisted");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("ARI URL must not contain user-info, query or fragment");
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("ARI URL port is invalid");
        }
    }
}
