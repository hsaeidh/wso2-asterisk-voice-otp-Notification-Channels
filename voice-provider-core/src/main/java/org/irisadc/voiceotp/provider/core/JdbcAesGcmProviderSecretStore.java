package org.irisadc.voiceotp.provider.core;

import org.irisadc.voiceotp.provider.api.ProviderScope;
import org.irisadc.voiceotp.provider.api.ProviderSecretResolver;
import org.irisadc.voiceotp.provider.api.ProviderSecretStore;
import org.irisadc.voiceotp.provider.api.VoiceProviderException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * AES-GCM encrypted JDBC secret store. The AES key must be supplied by the host's
 * secure-secret facility; this class deliberately does not load keys from files,
 * environment variables, or provider configuration.
 */
public final class JdbcAesGcmProviderSecretStore implements ProviderSecretStore, ProviderSecretResolver {
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final DataSource dataSource;
    private final SecretKey masterKey;
    private final SecureRandom random;

    public JdbcAesGcmProviderSecretStore(DataSource dataSource, SecretKey masterKey) {
        this(dataSource, masterKey, new SecureRandom());
    }

    JdbcAesGcmProviderSecretStore(DataSource dataSource, SecretKey masterKey, SecureRandom random) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource");
        this.masterKey = java.util.Objects.requireNonNull(masterKey, "masterKey");
        this.random = java.util.Objects.requireNonNull(random, "random");
        if (!"AES".equalsIgnoreCase(masterKey.getAlgorithm())) {
            throw new IllegalArgumentException("AES key is required");
        }
    }

    @Override
    public String store(ProviderScope scope, String providerName, char[] secret) throws VoiceProviderException {
        if (secret == null || secret.length == 0) {
            throw new VoiceProviderException(VoiceProviderException.Code.VALIDATION_FAILED,
                    "Provider credential is required");
        }
        String reference = "vpsec-" + UUID.randomUUID();
        byte[] plaintext = encode(secret);
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = null;
        try {
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(scope, reference));
            ciphertext = cipher.doFinal(plaintext);
            String sql = "INSERT INTO voice_provider_secret "
                    + "(tenant_domain, organization_id, secret_reference, iv_b64, ciphertext_b64) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, scope.tenantDomain());
                p.setString(2, scope.organizationId());
                p.setString(3, reference);
                p.setString(4, Base64.getEncoder().encodeToString(iv));
                p.setString(5, Base64.getEncoder().encodeToString(ciphertext));
                p.executeUpdate();
            }
            return reference;
        } catch (GeneralSecurityException | SQLException e) {
            throw new VoiceProviderException(VoiceProviderException.Code.SECRET_STORE_UNAVAILABLE,
                    "Unable to store provider credential", e);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
        }
    }

    @Override
    public Optional<char[]> resolve(ProviderScope scope, String secretReference)
            throws VoiceProviderException {
        String sql = "SELECT iv_b64, ciphertext_b64 FROM voice_provider_secret "
                + "WHERE tenant_domain=? AND organization_id=? AND secret_reference=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, scope.tenantDomain());
            p.setString(2, scope.organizationId());
            p.setString(3, secretReference);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                byte[] iv = Base64.getDecoder().decode(rs.getString(1));
                byte[] ciphertext = Base64.getDecoder().decode(rs.getString(2));
                byte[] plaintext = null;
                try {
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
                    cipher.updateAAD(aad(scope, secretReference));
                    plaintext = cipher.doFinal(ciphertext);
                    return Optional.of(decode(plaintext));
                } catch (GeneralSecurityException | CharacterCodingException e) {
                    throw new VoiceProviderException(VoiceProviderException.Code.SECRET_STORE_UNAVAILABLE,
                            "Unable to resolve provider credential", e);
                } finally {
                    Arrays.fill(iv, (byte) 0);
                    Arrays.fill(ciphertext, (byte) 0);
                    if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            throw new VoiceProviderException(VoiceProviderException.Code.SECRET_STORE_UNAVAILABLE,
                    "Unable to read provider credential", e);
        }
    }

    @Override
    public void delete(ProviderScope scope, String secretReference) throws VoiceProviderException {
        String sql = "DELETE FROM voice_provider_secret "
                + "WHERE tenant_domain=? AND organization_id=? AND secret_reference=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, scope.tenantDomain());
            p.setString(2, scope.organizationId());
            p.setString(3, secretReference);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new VoiceProviderException(VoiceProviderException.Code.SECRET_STORE_UNAVAILABLE,
                    "Unable to delete provider credential", e);
        }
    }

    private static byte[] aad(ProviderScope scope, String reference) {
        return (scope.tenantDomain() + "\u0000" + scope.organizationId() + "\u0000" + reference)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] encode(char[] chars) throws VoiceProviderException {
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(chars));
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            if (buffer.hasArray()) Arrays.fill(buffer.array(), (byte) 0);
            return result;
        } catch (CharacterCodingException e) {
            throw new VoiceProviderException(VoiceProviderException.Code.VALIDATION_FAILED,
                    "Provider credential contains invalid characters", e);
        }
    }

    private static char[] decode(byte[] bytes) throws CharacterCodingException {
        CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
        char[] result = new char[chars.remaining()];
        chars.get(result);
        if (chars.hasArray()) Arrays.fill(chars.array(), '\u0000');
        return result;
    }
}
