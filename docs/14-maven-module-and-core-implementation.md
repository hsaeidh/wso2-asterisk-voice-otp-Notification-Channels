# Maven Modules and Provider Core Implementation

**Status:** Initial implementation scaffold committed on branch `feature/voice-provider-management-core`. This is not yet a WSO2-installable feature.

## 1. Module layout

- `voice-provider-api`: stable Java contracts, scope and DTOs, repository/secret/tester abstractions.
- `voice-provider-core`: validation and provider-management orchestration.
- Parent build: Java 21, Maven multi-module reactor, JUnit 5.

The API module intentionally has no WSO2 dependency. The Core module also avoids WSO2-specific classes so its behavior can be unit-tested without loading the Carbon runtime.

## 2. Implemented behavior

- Scope value object requires both tenant domain and Organization ID.
- Provider names are normalized to lowercase and validated against a safe identifier pattern.
- ARI URLs require HTTPS and an exact host allowlist.
- ARI user name, Stasis application, endpoint, dialplan context/extension, caller ID, language and timeout bounds are validated.
- Create consumes the request password once, clears the request object's internal buffer, stores the credential through a secret-store interface and persists only its opaque reference.
- Read/list DTOs expose `secretConfigured`, never the credential or reference.
- Update consumes a supplied password once and clears the request buffer; omitting it preserves the current reference. Rotation writes a new secret and retires the previous reference after metadata update.
- Revision checks reject stale updates/deletes.
- Delete is blocked while an active delivery references the provider.
- Connection test delegates to an injected tester and clears the resolved character buffer after completion.

## 3. Explicit non-implementations

The following are deliberately interfaces or future integration work:
- WSO2 Secret Manager adapter and Organization-context resolver;
- production persistence adapter and transaction/outbox behavior;
- OSGi Declarative Services component metadata and runtime wiring;
- actual HTTP ARI connection tester (must be read-only and TLS-verified);
- REST API v2 resource, authorization scopes and Console UI;
- runtime rate limiting and audit implementation;
- DNS pinning/rebinding defense at connection time;
- inherited provider lookup (disabled in v1).

Do not package this scaffold as an installable WSO2 feature yet.

## 4. Build and test

From repository root, with JDK 21 and Maven 3.9+:

```bash
mvn -B clean verify
```

Expected initial tests cover create/secret redaction, HTTPS/host validation, Organization isolation, secret preservation/rotation, optimistic concurrency, active-delivery delete protection and secret-buffer clearing.

The actual build result must be recorded only after running Maven in a JDK 21 environment.
