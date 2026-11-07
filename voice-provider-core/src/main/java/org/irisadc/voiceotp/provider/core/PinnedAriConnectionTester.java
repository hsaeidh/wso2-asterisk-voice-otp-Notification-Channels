package org.irisadc.voiceotp.provider.core;

import org.irisadc.voiceotp.provider.api.AriConnectionTester;
import org.irisadc.voiceotp.provider.api.ProviderConnectionTestResult;
import org.irisadc.voiceotp.provider.api.ProviderRecord;
import org.irisadc.voiceotp.provider.api.VoiceProviderException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLParameters;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only ARI health check. DNS is resolved once and the TCP socket connects directly
 * to one of those resolved addresses while TLS hostname verification and SNI retain
 * the configured hostname. Redirects are never followed.
 */
public final class PinnedAriConnectionTester implements AriConnectionTester {
    private final Set<String> allowedHosts;
    private final Set<Integer> allowedPorts;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public PinnedAriConnectionTester(Set<String> allowedHosts, Set<Integer> allowedPorts,
                                     int connectTimeoutMillis, int readTimeoutMillis) {
        this.allowedHosts = allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.allowedPorts = Set.copyOf(allowedPorts);
        if (this.allowedHosts.isEmpty() || this.allowedPorts.isEmpty()
                || connectTimeoutMillis < 100 || connectTimeoutMillis > 15000
                || readTimeoutMillis < 100 || readTimeoutMillis > 15000) {
            throw new IllegalArgumentException("Invalid ARI tester policy");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public ProviderConnectionTestResult test(ProviderRecord provider, char[] secret)
            throws VoiceProviderException {
        long started = System.nanoTime();
        URI uri = validate(provider.ariUrl());
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw failure("ARI_DNS_FAILED", e);
        }
        IOException last = null;
        for (InetAddress address : addresses) {
            try {
                int status = request(uri, port, address, provider.username(), secret);
                long latency = (System.nanoTime() - started) / 1_000_000L;
                if (status == 200) {
                    return new ProviderConnectionTestResult(true, "ARI_AUTHENTICATED", latency, Instant.now());
                }
                if (status == 401 || status == 403) {
                    return new ProviderConnectionTestResult(false, "ARI_AUTHENTICATION_FAILED",
                            latency, Instant.now());
                }
                return new ProviderConnectionTestResult(false, "ARI_UNEXPECTED_HTTP_STATUS",
                        latency, Instant.now());
            } catch (IOException e) {
                last = e;
            }
        }
        throw failure("ARI_CONNECTION_FAILED", last);
    }

    private URI validate(String value) throws VoiceProviderException {
        try {
            URI uri = new URI(value).normalize();
            int port = uri.getPort() == -1 ? 443 : uri.getPort();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || !allowedPorts.contains(port) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath() == null
                    || !(uri.getPath().equals("/ari") || uri.getPath().startsWith("/ari/"))) {
                throw new URISyntaxException(value, "ARI URL is not allowed");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new VoiceProviderException(VoiceProviderException.Code.VALIDATION_FAILED,
                    "ARI URL is not allowed");
        }
    }

    private int request(URI uri, int port, InetAddress address, String username, char[] secret)
            throws IOException {
        SSLSocketFactory factory;
        try {
            factory = SSLContext.getDefault().getSocketFactory();
        } catch (Exception e) {
            throw new IOException("TLS context unavailable", e);
        }
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(address, port), connectTimeoutMillis);
            raw.setSoTimeout(readTimeoutMillis);
            try (SSLSocket tls = (SSLSocket) factory.createSocket(raw, uri.getHost(), port, true)) {
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                if (!uri.getHost().matches("[0-9.]+") && uri.getHost().indexOf(':') < 0) {
                    parameters.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(uri.getHost())));
                }
                tls.setSSLParameters(parameters);
                tls.startHandshake();
                String basePath = uri.getRawPath().replaceAll("/+$", "");
                String target = basePath + "/asterisk/info";
                String hostHeader = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();
                if (port != 443) hostHeader += ":" + port;
                String auth = basicAuthorization(username, secret);
                OutputStream out = tls.getOutputStream();
                out.write(("GET " + target + " HTTP/1.1\r\nHost: " + hostHeader
                        + "\r\nAuthorization: " + auth
                        + "\r\nAccept: application/json\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        tls.getInputStream(), StandardCharsets.ISO_8859_1));
                String statusLine = reader.readLine();
                if (statusLine == null || statusLine.length() > 256
                        || !statusLine.matches("HTTP/1\\.[01] [0-9]{3}.*")) {
                    throw new IOException("Invalid ARI HTTP response");
                }
                String[] parts = statusLine.split(" ", 3);
                int status = Integer.parseInt(parts[1]);
                int headerBytes = 0;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    headerBytes += line.length();
                    if (headerBytes > 16384) throw new IOException("ARI response headers too large");
                }
                return status;
            }
        }
    }

    private static String basicAuthorization(String username, char[] secret) throws IOException {
        char[] combined = new char[username.length() + 1 + secret.length];
        username.getChars(0, username.length(), combined, 0);
        combined[username.length()] = ':';
        System.arraycopy(secret, 0, combined, username.length() + 1, secret.length);
        ByteBuffer bytes = null;
        byte[] raw = null;
        try {
            bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(combined));
            raw = new byte[bytes.remaining()];
            bytes.get(raw);
            return "Basic " + Base64.getEncoder().encodeToString(raw);
        } catch (CharacterCodingException e) {
            throw new IOException("Credential encoding failed", e);
        } finally {
            java.util.Arrays.fill(combined, '\0');
            if (raw != null) java.util.Arrays.fill(raw, (byte) 0);
            if (bytes != null && bytes.hasArray()) java.util.Arrays.fill(bytes.array(), (byte) 0);
        }
    }

    private static VoiceProviderException failure(String code, Exception cause) {
        return new VoiceProviderException(VoiceProviderException.Code.ARI_UNREACHABLE,
                code, cause);
    }
}
