package org.irisadc.voiceotp.provider.core;

import org.irisadc.voiceotp.provider.api.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DefaultVoiceProviderManagementService implements VoiceProviderManagementService {
    private final ProviderRepository repository;
    private final ProviderSecretStore secretStore;
    private final ProviderSecretResolver secretResolver;
    private final AriConnectionTester connectionTester;
    private final ProviderUsageChecker usageChecker;
    private final ProviderValidator validator;

    public DefaultVoiceProviderManagementService(ProviderRepository repository,
            ProviderSecretStore secretStore, ProviderSecretResolver secretResolver,
            AriConnectionTester connectionTester, ProviderUsageChecker usageChecker,
            ProviderValidator validator) {
        this.repository = repository;
        this.secretStore = secretStore;
        this.secretResolver = secretResolver;
        this.connectionTester = connectionTester;
        this.usageChecker = usageChecker;
        this.validator = validator;
    }

    @Override
    public ProviderSummary create(ProviderScope scope, ProviderCreateRequest request)
            throws VoiceProviderException {
        validate(() -> validator.validate(request));
        String normalized = validator.normalizeName(request.name());
        char[] password = request.consumeAriPassword();
        String secretRef = null;
        try {
            secretRef = secretStore.store(scope, normalized, password);
            if (secretRef == null || secretRef.isBlank()) {
                throw error(VoiceProviderException.Code.SECRET_STORE_UNAVAILABLE,
                        "Secret store did not return a valid reference");
            }
            Instant now = Instant.now();
            ProviderRecord record = new ProviderRecord(normalized, request.ariUrl().trim(),
                    request.username().trim(), secretRef, request.application(), request.outboundEndpoint(),
                    request.dialplanContext(), request.dialplanExtension(), request.callerId(),
                    request.ringTimeoutSeconds(), request.maxCallDurationSeconds(), request.language(),
                    request.enabled(), 1L, now, now);
            return toSummary(repository.create(scope, record));
        } catch (VoiceProviderException e) {
            cleanupSecret(scope, secretRef);
            throw e;
        } catch (RuntimeException e) {
            cleanupSecret(scope, secretRef);
            throw e;
        } finally {
            clear(password);
        }
    }

    @Override
    public ProviderSummary get(ProviderScope scope, String name) throws VoiceProviderException {
        return toSummary(find(scope, name));
    }

    @Override
    public Page<ProviderSummary> list(ProviderScope scope, PageRequest page) {
        List<ProviderRecord> all = repository.list(scope).stream()
                .sorted(Comparator.comparing(ProviderRecord::name)).toList();
        int from = Math.min(page.offset(), all.size());
        int to = Math.min(from + page.limit(), all.size());
        return new Page<>(all.subList(from, to).stream().map(this::toSummary).toList(),
                page.offset(), page.limit(), all.size());
    }

    @Override
    public ProviderSummary update(ProviderScope scope, String name, ProviderUpdateRequest request,
                                  long expectedRevision) throws VoiceProviderException {
        validate(() -> validator.validate(request));
        String normalized = validator.normalizeName(name);
        ProviderRecord current = find(scope, normalized);
        if (current.revision() != expectedRevision) {
            throw error(VoiceProviderException.Code.VERSION_CONFLICT, "Provider version conflict");
        }
        char[] password = request.consumeAriPassword();
        String newSecretRef = current.secretReference();
        boolean rotated = password != null;
        try {
            if (rotated) {
                newSecretRef = secretStore.store(scope, normalized, password);
                if (newSecretRef == null || newSecretRef.isBlank()) {
                    throw error(VoiceProviderException.Code.SECRET_STORE_UNAVAILABLE,
                            "Secret store did not return a valid reference");
                }
            }
            ProviderRecord updated = new ProviderRecord(current.name(), request.ariUrl().trim(),
                    request.username().trim(), newSecretRef, request.application(), request.outboundEndpoint(),
                    request.dialplanContext(), request.dialplanExtension(), request.callerId(),
                    request.ringTimeoutSeconds(), request.maxCallDurationSeconds(), request.language(),
                    request.enabled(), current.revision() + 1, current.createdAt(), Instant.now());
            ProviderRecord saved = repository.update(scope, updated, expectedRevision);
            if (rotated && current.secretReference() != null) {
                cleanupSecret(scope, current.secretReference());
            }
            return toSummary(saved);
        } catch (VoiceProviderException e) {
            if (rotated && !java.util.Objects.equals(newSecretRef, current.secretReference())) {
                cleanupSecret(scope, newSecretRef);
            }
            throw e;
        } catch (RuntimeException e) {
            if (rotated && !java.util.Objects.equals(newSecretRef, current.secretReference())) {
                cleanupSecret(scope, newSecretRef);
            }
            throw e;
        } finally {
            clear(password);
        }
    }

    @Override
    public void delete(ProviderScope scope, String name, long expectedRevision) throws VoiceProviderException {
        String normalized = validator.normalizeName(name);
        ProviderRecord current = find(scope, normalized);
        if (current.revision() != expectedRevision) {
            throw error(VoiceProviderException.Code.VERSION_CONFLICT, "Provider version conflict");
        }
        if (usageChecker.hasActiveDeliveries(scope, normalized)) {
            throw error(VoiceProviderException.Code.IN_USE, "Provider is referenced by active deliveries");
        }
        repository.delete(scope, normalized, expectedRevision);
        cleanupSecret(scope, current.secretReference());
    }

    @Override
    public ProviderConnectionTestResult testConnection(ProviderScope scope, String name)
            throws VoiceProviderException {
        ProviderRecord provider = find(scope, name);
        if (!provider.enabled()) {
            throw error(VoiceProviderException.Code.VALIDATION_FAILED, "Provider is disabled");
        }
        Optional<char[]> resolved = secretResolver.resolve(scope, provider.secretReference());
        if (resolved.isEmpty()) {
            throw error(VoiceProviderException.Code.SECRET_STORE_UNAVAILABLE, "Provider credential is unavailable");
        }
        char[] secret = resolved.get();
        try {
            return connectionTester.test(provider, secret);
        } finally {
            clear(secret);
        }
    }

    private ProviderRecord find(ProviderScope scope, String name) throws VoiceProviderException {
        return repository.find(scope, validator.normalizeName(name))
                .orElseThrow(() -> error(VoiceProviderException.Code.NOT_FOUND, "Voice provider not found"));
    }

    private ProviderSummary toSummary(ProviderRecord r) {
        return new ProviderSummary(r.name(), "ASTERISK_ARI", r.ariUrl(), r.username(),
                r.application(), r.outboundEndpoint(), r.dialplanContext(), r.dialplanExtension(),
                r.callerId(), r.ringTimeoutSeconds(), r.maxCallDurationSeconds(), r.language(),
                r.enabled(), r.secretReference() != null, ProviderSummary.ScopeSource.LOCAL,
                r.revision(), r.createdAt(), r.updatedAt());
    }

    private void validate(Runnable validation) throws VoiceProviderException {
        try {
            validation.run();
        } catch (IllegalArgumentException e) {
            throw new VoiceProviderException(VoiceProviderException.Code.VALIDATION_FAILED, e.getMessage());
        }
    }

    private void cleanupSecret(ProviderScope scope, String reference) {
        if (reference == null) return;
        try {
            secretStore.delete(scope, reference);
        } catch (VoiceProviderException ignored) {
            // Production adapters must emit a redacted cleanup event for reconciliation.
        }
    }

    private static void clear(char[] secret) {
        if (secret != null) java.util.Arrays.fill(secret, '\0');
    }

    private static VoiceProviderException error(VoiceProviderException.Code code, String message) {
        return new VoiceProviderException(code, message);
    }
}
