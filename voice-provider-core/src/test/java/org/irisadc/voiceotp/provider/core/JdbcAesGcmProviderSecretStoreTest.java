package org.irisadc.voiceotp.provider.core;

import org.h2.jdbcx.JdbcDataSource;
import org.irisadc.voiceotp.provider.api.ProviderScope;
import org.irisadc.voiceotp.provider.api.VoiceProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcAesGcmProviderSecretStoreTest {
    private JdbcAesGcmProviderSecretStore store;
    private JdbcDataSource dataSource;
    private final ProviderScope root = new ProviderScope("carbon.super", "org-root");
    private final ProviderScope child = new ProviderScope("carbon.super", "org-child");

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:secrets_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE voice_provider_secret (tenant_domain VARCHAR(255) NOT NULL, "
                    + "organization_id VARCHAR(255) NOT NULL, secret_reference VARCHAR(64) NOT NULL, "
                    + "iv_b64 VARCHAR(64) NOT NULL, ciphertext_b64 VARCHAR(8192) NOT NULL, "
                    + "PRIMARY KEY (tenant_domain, organization_id, secret_reference))");
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        store = new JdbcAesGcmProviderSecretStore(dataSource, key);
    }

    @Test
    void storesEncryptedValueAndResolvesOnlyWithinSameScope() throws Exception {
        char[] password = "voice-secret-123".toCharArray();
        String reference = store.store(root, "provider-a", password);
        Arrays.fill(password, '\u0000');

        Optional<char[]> resolved = store.resolve(root, reference);
        assertTrue(resolved.isPresent());
        assertArrayEquals("voice-secret-123".toCharArray(), resolved.orElseThrow());
        assertTrue(store.resolve(child, reference).isEmpty());
        Arrays.fill(resolved.orElseThrow(), '\u0000');
        store.delete(root, reference);
        assertTrue(store.resolve(root, reference).isEmpty());
        store.delete(root, reference);
    }

    @Test
    void rejectsTamperedCiphertext() throws Exception {
        String reference = store.store(root, "provider-a", "credential".toCharArray());
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE voice_provider_secret SET ciphertext_b64=? WHERE secret_reference=?")) {
            p.setString(1, "AAAA");
            p.setString(2, reference);
            assertEquals(1, p.executeUpdate());
        }
        assertThrows(VoiceProviderException.class, () -> store.resolve(root, reference));
    }
}
