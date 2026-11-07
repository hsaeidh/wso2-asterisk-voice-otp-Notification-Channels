package org.irisadc.voiceotp.provider.core;

import org.irisadc.voiceotp.provider.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class DefaultVoiceProviderManagementServiceTest {
    private final ProviderScope scope = new ProviderScope("carbon.super", "org-root");
    private final ProviderScope otherScope = new ProviderScope("carbon.super", "org-child");
    private FakeRepository repository;
    private FakeSecrets secrets;
    private FakeUsage usage;
    private RecordingTester tester;
    private VoiceProviderManagementService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        secrets = new FakeSecrets();
        usage = new FakeUsage();
        tester = new RecordingTester();
        ProviderValidator validator = new ProviderValidator(
                new AllowlistAriUrlPolicy(Set.of("asterisk.example.test")));
        service = new DefaultVoiceProviderManagementService(
                repository, secrets, secrets, tester, usage, validator);
    }

    @Test
    void createStoresSecretButNeverReturnsItOrRetainsRequestBuffer() throws Exception {
        char[] input = "top-secret".toCharArray();
        ProviderCreateRequest request = createRequest("MainProvider", input);
        ProviderSummary result = service.create(scope, request);

        assertEquals("mainprovider", result.name());
        assertTrue(result.secretConfigured());
        assertFalse(result.toString().contains("top-secret"));
        assertEquals(-1, request.passwordLength());
        assertEquals(1, secrets.values.size());
        assertTrue(Arrays.equals("top-secret".toCharArray(), secrets.values.values().iterator().next()));
    }

    @Test
    void createRejectsNonHttpsAndDoesNotPersistSecret() {
        ProviderCreateRequest request = new ProviderCreateRequest("Bad", "http://asterisk.example.test:8088/ari",
                "ari-user", "secret".toCharArray(), "voice-otp", "PJSIP/irisa", "voice-otp",
                "s", "+982100000000", 25, 45, "fa", true);

        VoiceProviderException error = assertThrows(VoiceProviderException.class,
                () -> service.create(scope, request));

        assertEquals(VoiceProviderException.Code.VALIDATION_FAILED, error.getCode());
        assertEquals(-1, request.passwordLength());
        assertTrue(repository.rows.isEmpty());
        assertTrue(secrets.values.isEmpty());
    }

    @Test
    void scopesAreIsolated() throws Exception {
        service.create(scope, createRequest("MainProvider", "secret".toCharArray()));

        assertThrows(VoiceProviderException.class, () -> service.get(otherScope, "mainprovider"));
        assertEquals(1, service.list(scope, PageRequest.firstPage()).totalCount());
        assertEquals(0, service.list(otherScope, PageRequest.firstPage()).totalCount());
    }

    @Test
    void updateWithoutPasswordPreservesSecretAndIncrementsRevision() throws Exception {
        ProviderSummary created = service.create(scope, createRequest("MainProvider", "secret".toCharArray()));
        String originalRef = repository.find(scope, "mainprovider").orElseThrow().secretReference();

        ProviderUpdateRequest update = updateRequest(null, "ari-new-user");
        ProviderSummary updated = service.update(scope, "mainprovider", update, created.revision());

        assertEquals(created.revision() + 1, updated.revision());
        assertTrue(updated.secretConfigured());
        assertEquals(originalRef, repository.find(scope, "mainprovider").orElseThrow().secretReference());
        assertEquals(1, secrets.values.size());
    }

    @Test
    void updateRotatesSecretAndRejectsStaleRevision() throws Exception {
        ProviderSummary created = service.create(scope, createRequest("MainProvider", "old-secret".toCharArray()));
        String oldRef = repository.find(scope, "mainprovider").orElseThrow().secretReference();

        ProviderUpdateRequest update = updateRequest("new-secret", "ari-user");
        ProviderSummary updated = service.update(scope, "mainprovider", update, created.revision());

        String newRef = repository.find(scope, "mainprovider").orElseThrow().secretReference();
        assertEquals(-1, update.passwordLength());
        assertNotEquals(oldRef, newRef);
        assertFalse(secrets.values.containsKey(oldRef));
        assertTrue(secrets.values.containsKey(newRef));
        assertThrows(VoiceProviderException.class, () -> service.update(scope, "mainprovider",
                updateRequest(null, "ari-user"), created.revision()));
    }

    @Test
    void deleteIsBlockedWhileDeliveryIsActive() throws Exception {
        ProviderSummary created = service.create(scope, createRequest("MainProvider", "secret".toCharArray()));
        usage.inUse = true;

        VoiceProviderException error = assertThrows(VoiceProviderException.class,
                () -> service.delete(scope, "mainprovider", created.revision()));

        assertEquals(VoiceProviderException.Code.IN_USE, error.getCode());
        assertTrue(repository.find(scope, "mainprovider").isPresent());
    }

    @Test
    void connectionTestDelegatesAndClearsResolvedSecret() throws Exception {
        service.create(scope, createRequest("MainProvider", "secret".toCharArray()));

        ProviderConnectionTestResult result = service.testConnection(scope, "mainprovider");

        assertTrue(result.successful());
        assertEquals("secret", tester.received);
        assertTrue(Arrays.equals(new char[] {'\0','\0','\0','\0','\0','\0'}, tester.bufferAfterCall));
        assertEquals(1, tester.callCount);
    }

    private ProviderCreateRequest createRequest(String name, char[] password) {
        return new ProviderCreateRequest(name, "https://asterisk.example.test:8089/ari",
                "ari-user", password, "voice-otp", "PJSIP/irisa", "voice-otp",
                "s", "+982100000000", 25, 45, "fa", true);
    }

    private ProviderUpdateRequest updateRequest(String password, String username) {
        return new ProviderUpdateRequest("https://asterisk.example.test:8089/ari",
                username, password == null ? null : password.toCharArray(),
                "voice-otp", "PJSIP/irisa", "voice-otp", "s",
                "+982100000000", 25, 45, "fa", true);
    }

    private static final class FakeRepository implements ProviderRepository {
        private final Map<String, ProviderRecord> rows = new ConcurrentHashMap<>();
        private String key(ProviderScope scope, String name) { return scope.storageKey() + "/" + name; }

        @Override
        public ProviderRecord create(ProviderScope scope, ProviderRecord record) throws VoiceProviderException {
            if (rows.putIfAbsent(key(scope, record.name()), record) != null) {
                throw new VoiceProviderException(VoiceProviderException.Code.ALREADY_EXISTS, "exists");
            }
            return record;
        }

        @Override
        public Optional<ProviderRecord> find(ProviderScope scope, String name) {
            return Optional.ofNullable(rows.get(key(scope, name)));
        }

        @Override
        public List<ProviderRecord> list(ProviderScope scope) {
            String prefix = scope.storageKey() + "/";
            return rows.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue).toList();
        }

        @Override
        public ProviderRecord update(ProviderScope scope, ProviderRecord record, long expectedRevision)
                throws VoiceProviderException {
            String key = key(scope, record.name());
            ProviderRecord current = rows.get(key);
            if (current == null) throw new VoiceProviderException(VoiceProviderException.Code.NOT_FOUND, "missing");
            if (current.revision() != expectedRevision) {
                throw new VoiceProviderException(VoiceProviderException.Code.VERSION_CONFLICT, "conflict");
            }
            rows.put(key, record);
            return record;
        }

        @Override
        public void delete(ProviderScope scope, String name, long expectedRevision) throws VoiceProviderException {
            String key = key(scope, name);
            ProviderRecord current = rows.get(key);
            if (current == null) throw new VoiceProviderException(VoiceProviderException.Code.NOT_FOUND, "missing");
            if (current.revision() != expectedRevision) {
                throw new VoiceProviderException(VoiceProviderException.Code.VERSION_CONFLICT, "conflict");
            }
            rows.remove(key);
        }
    }

    private static final class FakeSecrets implements ProviderSecretStore, ProviderSecretResolver {
        private final Map<String, char[]> values = new ConcurrentHashMap<>();
        private int nextId;

        @Override
        public synchronized String store(ProviderScope scope, String providerName, char[] secret) {
            String ref = "secret-ref-" + (++nextId);
            values.put(ref, secret.clone());
            return ref;
        }

        @Override
        public Optional<char[]> resolve(ProviderScope scope, String secretReference) {
            char[] value = values.get(secretReference);
            return value == null ? Optional.empty() : Optional.of(value.clone());
        }

        @Override
        public void delete(ProviderScope scope, String secretReference) {
            char[] removed = values.remove(secretReference);
            if (removed != null) Arrays.fill(removed, '\0');
        }
    }

    private static final class FakeUsage implements ProviderUsageChecker {
        private boolean inUse;
        @Override public boolean hasActiveDeliveries(ProviderScope scope, String providerName) { return inUse; }
    }

    private static final class RecordingTester implements AriConnectionTester {
        private String received;
        private char[] bufferAfterCall;
        private int callCount;

        @Override
        public ProviderConnectionTestResult test(ProviderRecord provider, char[] secret) {
            received = new String(secret);
            callCount++;
            bufferAfterCall = secret;
            return new ProviderConnectionTestResult(true, "ARI_AUTHENTICATED", 12, Instant.now());
        }
    }
}
