package org.irisadc.voiceotp.provider.core;

import org.h2.jdbcx.JdbcDataSource;
import org.irisadc.voiceotp.provider.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JdbcProviderRepositoryTest {
    private JdbcProviderRepository repository;
    private final ProviderScope root = new ProviderScope("carbon.super", "org-root");
    private final ProviderScope child = new ProviderScope("carbon.super", "org-child");

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:voice_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE voice_provider (tenant_domain VARCHAR(255) NOT NULL, "
                    + "organization_id VARCHAR(255) NOT NULL, provider_name VARCHAR(64) NOT NULL, "
                    + "ari_url VARCHAR(2048) NOT NULL, ari_username VARCHAR(128) NOT NULL, "
                    + "secret_reference VARCHAR(512), application_name VARCHAR(128) NOT NULL, "
                    + "outbound_endpoint VARCHAR(128) NOT NULL, dialplan_context VARCHAR(128) NOT NULL, "
                    + "dialplan_extension VARCHAR(80) NOT NULL, caller_id VARCHAR(32), "
                    + "ring_timeout_seconds INTEGER NOT NULL, max_call_duration_seconds INTEGER NOT NULL, "
                    + "language VARCHAR(16) NOT NULL, enabled BOOLEAN NOT NULL, revision BIGINT NOT NULL, "
                    + "created_at_epoch_ms BIGINT NOT NULL, updated_at_epoch_ms BIGINT NOT NULL, "
                    + "PRIMARY KEY (tenant_domain, organization_id, provider_name))");
        }
        repository = new JdbcProviderRepository(ds);
    }

    @Test
    void persistsAndIsolatesByTenantAndOrganization() throws Exception {
        ProviderRecord record = record("provider-a", 1);
        repository.create(root, record);

        assertEquals(record, repository.find(root, "provider-a").orElseThrow());
        assertTrue(repository.find(child, "provider-a").isEmpty());
        assertEquals(1, repository.list(root).size());
        assertTrue(repository.list(child).isEmpty());
    }

    @Test
    void updateAndDeleteUseOptimisticRevision() throws Exception {
        ProviderRecord original = record("provider-a", 1);
        repository.create(root, original);
        ProviderRecord changed = new ProviderRecord(original.name(), original.ariUrl(),
                "new-user", original.secretReference(), original.application(),
                original.outboundEndpoint(), original.dialplanContext(), original.dialplanExtension(),
                original.callerId(), original.ringTimeoutSeconds(), original.maxCallDurationSeconds(),
                original.language(), original.enabled(), 2, original.createdAt(), Instant.now());

        repository.update(root, changed, 1);
        assertThrows(VoiceProviderException.class, () -> repository.update(root, original, 1));
        assertThrows(VoiceProviderException.class, () -> repository.delete(root, "provider-a", 1));
        repository.delete(root, "provider-a", 2);
        assertTrue(repository.find(root, "provider-a").isEmpty());
    }

    @Test
    void duplicateProviderNameIsRejected() throws Exception {
        repository.create(root, record("provider-a", 1));
        VoiceProviderException error = assertThrows(VoiceProviderException.class,
                () -> repository.create(root, record("provider-a", 1)));
        assertEquals(VoiceProviderException.Code.ALREADY_EXISTS, error.getCode());
    }

    private ProviderRecord record(String name, long revision) {
        Instant now = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        return new ProviderRecord(name, "https://ari.example.test:8089/ari", "ari-user",
                "opaque-secret-ref", "voiceotp", "PJSIP/100", "voiceotp", "s",
                "+10000000000", 20, 60, "fa", true, revision, now, now);
    }
}
