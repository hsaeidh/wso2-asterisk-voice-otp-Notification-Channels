package org.irisadc.voiceotp.provider.core;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exact host and port allowlist. Production deployments must additionally pin
 * resolved IP addresses at connection time to mitigate DNS rebinding.
 */
public final class AllowlistAriUrlPolicy implements AriUrlPolicy {
    private final Set<String> allowedHosts;
    private final Set<Integer> allowedPorts;

    public AllowlistAriUrlPolicy(Set<String> allowedHosts) {
        this(allowedHosts, Set.of(443, 8089));
    }

    public AllowlistAriUrlPolicy(Set<String> allowedHosts, Set<Integer> allowedPorts) {
        this.allowedHosts = allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.allowedPorts = Set.copyOf(allowedPorts);
        if (this.allowedHosts.isEmpty() || this.allowedPorts.isEmpty()
                || this.allowedPorts.stream().anyMatch(port -> port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Valid ARI host and port allowlists are required");
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
        int effectivePort = uri.getPort() == -1 ? 443 : uri.getPort();
        if (!allowedPorts.contains(effectivePort)) {
            throw new IllegalArgumentException("ARI port is not allowlisted");
        }
        String path = uri.getPath();
        if (path == null || !(path.equals("/ari") || path.startsWith("/ari/"))) {
            throw new IllegalArgumentException("ARI URL path must be under /ari");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("ARI URL must not contain user-info, query or fragment");
        }
    }
}
