# Voice Provider Management Service Contract

**Status:** Proposed normative design contract for the first implementation. This document defines the standalone Voice service option selected for further design. It does not claim that the service, endpoints, storage or OSGi components already exist.

**Target:** WSO2 Identity Server 7.3.0, Java 21.

## 1. Design decision and ownership

Implement provider management as a dedicated OSGi service named `VoiceProviderManagementService`, rather than attempting to register an external `ChannelConfigurationHandler` against the private notification-sender tenant-config registry.

The Voice service owns:
- Voice provider validation and lifecycle;
- provider metadata persistence;
- secret reference lifecycle through a WSO2-supported secret facility;
- effective-scope resolution and authorization;
- provider lookup for delivery;
- non-billable ARI connectivity tests.

It does **not** own:
- OTP generation, validation or expiry;
- SMS provider configuration or SMS delivery;
- call delivery state and fallback policy (owned by the delivery subsystem);
- Asterisk trunk administration or arbitrary dialplan editing.

The service must not depend on the private `NotificationSenderTenantConfigDataHolder` or assume that implementing `ChannelConfigurationHandler` makes Voice a supported channel.

## 2. OSGi service boundary

The API below is a Java-level contract sketch. Package names, checked exception hierarchy and DS annotations must be finalized against the exact 7.3.0 bundle manifests before implementation.

```java
public interface VoiceProviderManagementService {
    VoiceProviderSummary create(ProviderScope scope, VoiceProviderCreateRequest request)
            throws VoiceProviderException;

    VoiceProvider get(ProviderScope scope, String providerName)
            throws VoiceProviderException;

    Page<VoiceProviderSummary> list(ProviderScope scope, PageRequest page)
            throws VoiceProviderException;

    VoiceProviderSummary update(ProviderScope scope, String providerName,
                                VoiceProviderUpdateRequest request, String expectedVersion)
            throws VoiceProviderException;

    void delete(ProviderScope scope, String providerName, String expectedVersion)
            throws VoiceProviderException;

    ProviderConnectionTestResult testConnection(ProviderScope scope, String providerName)
            throws VoiceProviderException;
}
```

Supporting contracts:

```java
public interface ProviderScopeResolver {
    ProviderScope resolveAuthenticatedScope(RequestContext context)
            throws ProviderScopeException;
}

public interface VoiceProviderRepository {
    VoiceProviderRecord create(ProviderScope scope, VoiceProviderRecord record);
    Optional<VoiceProviderRecord> find(ProviderScope scope, String providerName);
    Page<VoiceProviderRecord> list(ProviderScope scope, PageRequest page);
    VoiceProviderRecord update(ProviderScope scope, VoiceProviderRecord record,
                               String expectedVersion);
    void delete(ProviderScope scope, String providerName, String expectedVersion);
}

public interface VoiceProviderSecretStore {
    SecretReference put(ProviderScope scope, String providerName, char[] secret);
    Optional<SecretHandle> resolve(ProviderScope scope, SecretReference reference);
    void delete(ProviderScope scope, SecretReference reference);
}

public interface AsteriskAriConnectionTester {
    ProviderConnectionTestResult test(ResolvedAriConnection connection);
}
```

Rules:
- Register the management service as an OSGi service through Declarative Services.
- API bundle obtains it through a typed OSGi service reference/holder; no static singleton containing tenant data or secrets.
- Do not expose implementation packages as OSGi exports unless a real consumer requires them.
- Keep API DTOs in a stable exported API package; storage entities and secret handles remain internal.
- DS deactivation must stop new tests/operations and close HTTP clients/executors cleanly.
- If the service is unavailable, API requests fail closed with a stable service-unavailable error; they must not silently fall back to Email or SMS.

## 3. Scope model and Organization resolution

Never accept a client-provided `organizationId`, tenant domain or effective scope as authority.

The request pipeline must:
1. authenticate and authorize the caller using WSO2's normal API security;
2. obtain the accessing Organization and tenant context from the trusted WSO2 request/thread context;
3. resolve that context using a dedicated `ProviderScopeResolver`;
4. verify the caller has the required operation permission in that resolved scope;
5. pass an immutable `ProviderScope` to the service/repository.

Logical scope:

```java
public record ProviderScope(
    String tenantDomain,
    String organizationId,
    String primaryOrganizationId,
    ScopeKind kind
) {}
```

This is a logical value object, not a claim that these exact identifiers are available together in every IS 7.3.0 API context. The resolver must reject incomplete or ambiguous context. It must not infer an Organization from an email domain, request header or body field.

### v1 inheritance policy

- Provider writes (create/update/delete) always target the authenticated Organization's local scope.
- Provider reads/listing use the same local scope.
- Inheritance is **disabled by default** until the exact Organization-to-tenant mapping and authorization behavior is verified on IS 7.3.0.
- If inheritance is enabled in a later compatible release, v1 policy is all-or-nothing fallback: use the child scope when it contains at least one Voice provider; otherwise read from the primary Organization. Do not merge parent and child lists implicitly.
- A child Organization cannot update or delete an inherited parent provider through its child scope.
- API responses should identify whether a returned provider is local or inherited, without revealing internal tenant identifiers to an unauthorized caller.

This explicit policy avoids pretending that WSO2's existing sender inheritance is a per-provider merge. Any future per-provider override model requires a versioned design change and dedicated tests.

## 4. Provider data model

### 4.1 Public summary/read model

```json
{
  "name": "AsteriskVoiceProvider",
  "provider": "ASTERISK_ARI",
  "ariUrl": "https://asterisk.example.net:8089/ari",
  "username": "wso2-ari",
  "application": "voice-otp",
  "outboundEndpoint": "PJSIP/irisa-trunk",
  "dialplanContext": "voice-otp",
  "dialplanExtension": "s",
  "callerId": "+982100000000",
  "ringTimeoutSeconds": 25,
  "maxCallDurationSeconds": 45,
  "language": "fa",
  "enabled": true,
  "secretConfigured": true,
  "scopeSource": "LOCAL",
  "version": "opaque-version"
}
```

The response never contains a password, secret value, secret-store path or raw secret reference.

### 4.2 Create request

The create request accepts the public fields above except server-managed fields, plus `ariPassword`. It must reject unknown provider types and unknown security-sensitive fields rather than silently accepting them.

### 4.3 Update request

- Uses PUT with full non-secret configuration semantics, or PATCH if the final API deliberately adopts partial-update semantics. Do not mix the two.
- For the initial API proposal, use PUT for the complete non-secret configuration.
- Omitted `ariPassword` means preserve the existing secret.
- Empty string is not a secret-clear instruction; reject it or treat it as omitted consistently.
- Secret clearing is a separate operation, separately authorized and audited. It must not be inferred from null/empty JSON.
- Require an expected version/ETag to prevent lost updates.

### 4.4 Internal persistence record

Persist only:
- provider name and type;
- normalized ARI URL and non-secret connection/dialplan settings;
- enabled flag and timestamps;
- opaque secret reference;
- schema version and optimistic-lock version;
- owning scope key.

Do not persist:
- ARI password;
- OTP or challenge value;
- transient test credentials;
- ARI authorization header;
- call-specific destination or OTP in provider records.

Provider names are unique within one local scope, case-insensitive after a documented normalization rule. Do not allow path separators, control characters, leading/trailing whitespace or names that collide after normalization.

## 5. Validation contract

| Field | Validation |
|---|---|
| `name` | Required; 1–64 chars; stable normalized uniqueness within scope |
| `provider` | Exactly `ASTERISK_ARI` in v1 |
| `ariUrl` | HTTPS only in production; valid URI; no user-info or fragment; bounded path; certificate/hostname validation required |
| `username` | Required; bounded length; no control characters |
| `ariPassword` | Required on create; write-only; bounded size; never included in errors/logs |
| `application` | Required Stasis application identifier; bounded safe character set |
| `outboundEndpoint` | Required; allowlisted endpoint/trunk syntax; no arbitrary dialplan expression |
| `dialplanContext` | Required; must match deployment allowlist |
| `dialplanExtension` | Required; bounded safe dialplan extension; reject separators/injection syntax |
| `callerId` | Optional or required by deployment policy; normalize as E.164 where applicable |
| `ringTimeoutSeconds` | 5–90 seconds (initial default 25); deployment may narrow |
| `maxCallDurationSeconds` | 5–180 seconds (initial default 45); must be bounded |
| `language` | Allowlisted language identifier; `fa` is the Persian default |
| `enabled` | Boolean; default true only after all required configuration is valid |

Additional security validation:
- ARI URL host/port must match a deployment-controlled allowlist. Do not rely on a one-time DNS lookup; protect against DNS rebinding and revalidate resolved destinations at connection time.
- Do not block private address ranges globally; private datacenter Asterisk endpoints are a valid deployment case when explicitly allowlisted.
- Redirects are disabled by default. If unavoidable, revalidate every redirect target against the same allowlist.
- Enforce bounded connect/read/overall timeouts, response size and connection pool limits.
- Do not accept arbitrary HTTP headers, TLS trust-all flags, custom trust stores from the API, dialplan snippets, shell commands or arbitrary application arguments.
- TLS certificate validation and hostname verification are mandatory. Development exceptions must be isolated from production configuration and visibly marked.

## 6. Secret Manager integration

The implementation must use a secret API that is actually exported and supported by the installed WSO2 IS 7.3.0 runtime. The precise package, service name and method signatures remain a build-time verification item; do not guess them from another WSO2 release.

Required adapter behavior:
- `put(scope, providerName, secret)` stores the credential and returns an opaque reference.
- Secret lookup is allowed only to the ARI client path after provider authorization and scope resolution.
- Secret material is short-lived in memory, never placed in DTOs, cache entries, MDC, audit events or exception messages.
- Clear mutable buffers after use where possible; acknowledge that JSON parsing may transiently create immutable Strings and minimize their lifetime.
- GET/list return only `secretConfigured`.
- Updating non-secret fields does not rotate or erase the secret.
- Secret rotation is atomic from the caller's perspective: write new secret, commit new reference, then retire old reference.
- Failed metadata persistence must clean up an orphaned newly written secret.
- Provider deletion must not delete a secret still referenced by an in-flight delivery; use reference checks or deferred cleanup.
- Secret-store unavailability fails closed. Never fall back to plaintext database/config storage.

## 7. REST contract

Proposed relative paths under the existing Notification Sender API v2 base:

| Method | Path | Required permission family | Result |
|---|---|---|---|
| GET | `/notification-senders/voice` | view | Paginated provider summaries |
| POST | `/notification-senders/voice` | add | Created provider summary |
| GET | `/notification-senders/voice/{sender-name}` | view | Provider read model |
| PUT | `/notification-senders/voice/{sender-name}` | update | Updated provider summary |
| DELETE | `/notification-senders/voice/{sender-name}` | delete | No-content response |
| POST | `/notification-senders/voice/{sender-name}/test` | dedicated test permission to be defined | Non-billable connection result |

Base URL follows the product API convention:
`https://{host}:9443/t/{tenant-domain}/api/server/v2`

These are proposed routes, not live endpoints. The final OpenAPI must copy the exact response envelope, error model, pagination conventions, permission annotations and naming rules from the v1.5.31 API source.

### Connection test semantics

The test operation performs authentication and a harmless ARI read-only request (for example, Asterisk server information) without originating a call. It:
- never places a PSTN/mobile call;
- never sends OTP;
- never returns response headers or credential material;
- returns a bounded status, latency and sanitized diagnostic code;
- uses the same URL allowlist, TLS verification and timeouts as runtime;
- is rate-limited and audited;
- does not expose raw upstream error bodies.

If a real test call is ever required, it must be a separate feature with a separately named endpoint, explicit destination allowlist, additional permission, rate limit and audit trail. It is not part of this v1 contract.

### Error model

Use stable machine-readable codes, mapped to the product's established API error envelope:

- `VOICE_PROVIDER_NOT_FOUND`
- `VOICE_PROVIDER_ALREADY_EXISTS`
- `VOICE_PROVIDER_VALIDATION_FAILED`
- `VOICE_PROVIDER_VERSION_CONFLICT`
- `VOICE_PROVIDER_SCOPE_FORBIDDEN`
- `VOICE_PROVIDER_SECRET_STORE_UNAVAILABLE`
- `VOICE_PROVIDER_ARI_UNREACHABLE`
- `VOICE_PROVIDER_TEST_RATE_LIMITED`
- `VOICE_PROVIDER_IN_USE`
- `VOICE_PROVIDER_SERVICE_UNAVAILABLE`

Do not expose upstream ARI response bodies, stack traces, secret references or internal tenant/Organization IDs in client-facing messages.

## 8. Delete and in-flight delivery behavior

- A provider referenced by an active delivery policy cannot be hard-deleted immediately.
- Default delete behavior returns `VOICE_PROVIDER_IN_USE` (conflict) while active deliveries reference it.
- An explicit disable operation or `enabled=false` prevents new deliveries but permits already accepted deliveries to reach a terminal state under their original bounded policy.
- Once no active delivery references remain, deletion removes metadata and schedules secret cleanup.
- Never silently redirect an in-flight delivery to a different provider after a provider is edited or deleted. Capture a safe immutable provider configuration version for the delivery, excluding plaintext secret.

## 9. OSGi lifecycle and dependency direction

Expected service registration:

```text
voice-otp-config bundle
  provides VoiceProviderManagementService
  provides ProviderScopeResolver
  requires VoiceProviderRepository
  requires VoiceProviderSecretStore

voice-otp-rest bundle
  requires VoiceProviderManagementService
  derives ProviderScope from authenticated WSO2 request context

voice-otp-ari bundle
  requires authorized ResolvedAriConnection / secret handle
  does not expose secret material outside the ARI transport boundary
```

Avoid circular dependencies. The config service must not depend on REST classes; REST maps HTTP models to service DTOs. The ARI test adapter must not call the delivery coordinator or initiate calls.

## 10. Acceptance tests

### Unit
- required/optional field validation and boundary values;
- provider name normalization and uniqueness;
- URL allowlist, TLS and redirect validation;
- secret omitted/present/clear semantics;
- optimistic-lock conflict;
- stable error mapping and diagnostic redaction.

### Persistence and secret lifecycle
- create/read/update/delete in isolated scopes;
- secret never appears in read/list serialization;
- non-secret update preserves secret;
- rotation handles partial failure without losing the active secret;
- delete defers cleanup while referenced;
- secret-store outage fails closed.

### Authorization and Organization
- root Organization cannot read a child provider;
- child cannot read another child's provider;
- client-supplied scope/tenant/Organization values are ignored or rejected;
- local writes always remain local;
- inheritance disabled by default;
- when explicitly enabled, all-or-nothing fallback is tested and parent records cannot be mutated from child scope.

### ARI connection test
- successful TLS-authenticated read-only ARI request;
- bad credentials, certificate, DNS, timeout and refused connection;
- redirect to non-allowlisted host rejected;
- private Asterisk IP allowed only when explicitly allowlisted;
- no originate/call action invoked;
- no credential leakage in logs, errors or metrics.

### OSGi and API integration
- DS service registers and becomes available;
- API bundle resolves the service without private notification-config holder access;
- missing service fails closed;
- API permissions are enforced per operation;
- CRUD and test responses match the OpenAPI schema;
- existing Email/SMS/Push sender APIs remain unchanged.

## 11. Decisions still requiring runtime proof

This contract deliberately does not invent product-specific APIs. The following must be verified against the actual IS 7.3.0 distribution and manifests before coding:
1. Exact WSO2 Secret Manager/SecretResolveManager service and exported package available to the new bundle.
2. Exact API request context accessors for Organization ID, tenant domain and primary Organization.
3. Whether the existing API v2 permission model can express a distinct non-billable connection-test permission without modifying product authorization configuration.
4. Supported persistence abstraction and transaction semantics for a standalone provider resource.
5. OSGi import ranges and DS activation order with the exact product bundle set.

No implementation or runtime compatibility is claimed until these checks and tests pass.
