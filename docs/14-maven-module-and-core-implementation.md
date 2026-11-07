# Maven Modules and Provider Core Implementation

**Status:** Provider core plus runtime-neutral JDBC/AES-GCM/ARI adapters are implemented on branch `feature/provider-runtime-adapters`. Latest GitHub Actions Java 21 `mvn clean verify` passed with 13 tests. This is not yet a WSO2-installable feature.

## 1. Module layout

- `voice-provider-api`: stable Java contracts, scope and DTOs, repository/secret/tester abstractions.
- `voice-provider-core`: validation and provider-management orchestration.
- Parent build: Java 21, Maven multi-module reactor, JUnit 5.

The API module intentionally has no WSO2 dependency. The Core module also avoids WSO2-specific classes so its behavior can be unit-tested without loading the Carbon runtime.

## 2. Implemented behavior

- Scope value object requires both tenant domain and Organization ID.
- Provider names are normalized to lowercase and validated against a safe identifier pattern.
- ARI URLs require HTTPS, an exact host/port allowlist and an `/ari` path; production still requires connection-time DNS/IP pinning against rebinding.
- ARI user name, Stasis application, endpoint, dialplan context/extension, caller ID, language and timeout bounds are validated.
- Create stores the ARI credential through a secret-store interface and persists only its opaque reference.
- Read/list DTOs expose `secretConfigured`, never the credential or reference.
- Update without a new password preserves the current reference; rotation writes a new secret and retires the previous reference after metadata update.
- Revision checks reject stale updates/deletes.
- Delete is blocked while an active delivery references the provider.
- Connection test delegates to an injected tester and clears the resolved character buffer after completion.

## 3. Explicit non-implementations

The following are deliberately interfaces or future integration work:
- WSO2 Secret Manager / secure-vault key adapter and trusted Organization-context resolver;
- WSO2-managed DataSource binding and product-supported schema migration lifecycle;
- OSGi Declarative Services component metadata and runtime wiring;
- REST API v2 resource in the product API server, authorization scopes and Console UI;
- production ProviderUsageChecker integration with active-delivery state;
- runtime rate limiting and audit implementation;
- live ARI integration tests against a controlled Asterisk instance;
- inherited provider lookup (disabled in v1).

Do not package this scaffold as an installable WSO2 feature yet.

## 3. Runtime-neutral adapters now implemented

- `JdbcProviderRepository`: persists provider metadata using a host-supplied `DataSource`; tenant and Organization are part of the primary key; update/delete use optimistic revision predicates.
- `JdbcAesGcmProviderSecretStore`: encrypts credentials using AES-GCM with a fresh 96-bit IV and scope-bound AAD. The master `SecretKey` is injected; this module does not read keys from configuration files or environment variables. Secret lookup and deletion are scoped by tenant and Organization.
- `PinnedAriConnectionTester`: performs a read-only `GET /ari/asterisk/info`; resolves DNS once, connects directly to the selected IP, preserves SNI and HTTPS hostname verification, does not follow redirects, bounds connect/read timeouts and returns sanitized diagnostic codes.
- SQL migrations are provided for provider metadata and encrypted secret rows.

The JDBC and cryptographic adapters are runtime-neutral. They are not, by themselves, WSO2 adapters: the WSO2 product integration must provide the managed DataSource and a master key obtained from the supported secure-vault/secret facility. The database migration must be reviewed and applied using the deployment's supported migration process.

## 4. Explicit remaining integration work

- Bind the managed DataSource and secure-vault-backed master key through the WSO2 7.3.0 OSGi lifecycle.
- Resolve tenant and Organization only from trusted Carbon request context; never from request JSON or query parameters.
- Implement the production active-delivery usage checker and secret cleanup reconciliation.
- Add the Voice resources and generated OpenAPI model/service code to the WSO2 API Server repository, with least-privilege scopes.
- Register the provider service and API runtime in OSGi; wire authorization, audit, throttling and error mapping.
- Add the Console feature and translations in the product-matched identity-apps source.
- Implement Voice event delivery, Password Recovery and Login MFA integration with the existing OTP lifecycle, preserving one challenge and one OTP across SMS/Voice fallback.
- Add live Asterisk integration tests and product-level WSO2 IS 7.3.0 install/upgrade tests.

## 5. Build and test

From repository root, with JDK 21 and Maven 3.9+:

```bash
mvn -B clean verify
```

Expected initial tests cover create/secret redaction, HTTPS/host validation, Organization isolation, secret preservation/rotation, optimistic concurrency, active-delivery delete protection and secret-buffer clearing.

The latest provider-runtime-adapters commit passed GitHub Actions with JDK 21: 13 tests, 0 failures, 0 errors. The test suite covers provider orchestration, JDBC repository scope/revision behavior, encrypted secret storage and ciphertext-tamper rejection. It does not exercise a live ARI server or WSO2 runtime.
