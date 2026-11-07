# Provider Configuration and REST API

**Status:** API contract corrected against Notification Sender v2 OpenAPI at `identity-api-server:v1.5.31`. Voice endpoints are proposed additions, not existing WSO2 endpoints.

## 1. Existing WSO2 API contract

The official Notification Sender v2 OpenAPI declares this server base:

`https://localhost:9443/t/{tenant-domain}/api/server/v2`

Existing resource paths are:

- `/notification-senders/email`
- `/notification-senders/email/{sender-name}`
- `/notification-senders/sms`
- `/notification-senders/sms/{sender-name}`
- `/notification-senders/push`
- `/notification-senders/push/{sender-name}`

There is no Voice resource and no generic provider `/test` operation in the current v2 contract.

## 2. Proposed Voice API extension

If the existing Notification Sender v2 model is retained, proposed paths should follow its established naming convention:

| Method | Proposed path (relative to API v2 base) | Purpose |
|---|---|---|
| GET | `/notification-senders/voice` | List Voice senders |
| POST | `/notification-senders/voice` | Create Voice sender |
| GET | `/notification-senders/voice/{sender-name}` | Read Voice sender |
| PUT | `/notification-senders/voice/{sender-name}` | Update Voice sender |
| DELETE | `/notification-senders/voice/{sender-name}` | Delete Voice sender |
| POST | `/notification-senders/voice/{sender-name}/test` | Proposed extension: test ARI connectivity |

The last operation is new and must be designed explicitly in OpenAPI, generated API interfaces, implementation, permission/scope definitions, tests and Console client. It must not be described as an existing WSO2 endpoint.

Before adopting these paths, inspect and follow the exact v2 response envelopes, error model, naming, URL encoding, permission and scope conventions. The current API uses the configuration-management permission family and scopes such as `internal_config_mgt_view` / `internal_config_mgt_add`; use the precise existing scope for each HTTP operation rather than inventing a new authorization scheme.

## 3. API implementation impact

The existing v2 API service delegates to `org.wso2.carbon.identity.notification.sender.tenant.config.NotificationSenderManagementService`. Therefore adding only an OpenAPI path or only a new REST resource is insufficient.

The implementation review must cover:
- v2 OpenAPI definition and generated `NotificationSendersApi` / service interface;
- `NotificationSendersApiServiceImpl` and management-service facade;
- service factory/holder and OSGi service references;
- sender configuration model and persistence service from the product's notification sender tenant-config bundle;
- API integration tests and Console API client;
- product API servlet registration.

The product's `api-resources-full` web.xml already registers `org.wso2.carbon.identity.api.server.notification.sender.v2.NotificationSendersApi`. If the new Voice operations remain in that same API class, a new servlet registration should not be necessary; verify generated class/package identity during build before changing product-is.

## 4. Provider model

Illustrative create/update payload:

```json
{
  "name": "AsteriskVoiceProvider",
  "provider": "ASTERISK_ARI",
  "ariUrl": "https://asterisk.example.net:8089/ari",
  "username": "wso2-ari",
  "password": "<write-only-secret>",
  "application": "voice-otp",
  "outboundEndpoint": "PJSIP/irisa-trunk",
  "dialplanContext": "voice-otp",
  "dialplanExtension": "s",
  "callerId": "+982100000000",
  "ringTimeout": 25,
  "maxCallDuration": 45,
  "language": "fa",
  "enabled": true
}
```

This is a logical model, not a final OpenAPI schema. Validate field names, constraints and provider model against the final ARI implementation.

## 5. Secret handling

- Password is write-only.
- GET/list responses expose `secretConfigured: true|false`, never the secret.
- An update that omits the password preserves the existing secret.
- An explicit secret-clear operation must be separate and strongly authorized.
- Use the secret mechanism actually exported by the target IS 7.3.0 runtime; do not assume class names/API signatures from a different release.
- Bind secret references to the provider's effective tenant/organization scope.
- Provider deletion removes its secret only after safe handling of in-flight deliveries.
- Do not store credentials in ordinary configuration properties, logs, exception text or exported diagnostics.

## 6. Tenant and organization isolation

The published API route is tenant-domain based. Organization-scoped provider isolation is a product requirement, but the mapping from Organization Management context to the notification sender configuration tenant must be proven in IS 7.3.0.

Do not trust an arbitrary client-supplied tenant or organization identifier. Derive context from the authenticated WSO2 request, authorize it, and test root organization, child organization and tenant-domain behavior. Until this is proven, describe the design as **tenant-context aware with organization mapping to be verified**, not as an already-supported organization-scoped API.

## 7. API behavior and security

- Production ARI URLs must use HTTPS with certificate validation.
- Validate host, port, path and URL scheme.
- Prevent SSRF and DNS rebinding; use deployment allowlists for internal Asterisk addresses rather than globally blocking private IP ranges.
- Disable or tightly constrain redirects.
- Apply bounded connect/read timeouts and response-size limits.
- Generic connectivity test must not place a billable PSTN call.
- If a test call is required, make it a distinct privileged operation with destination allowlisting, rate limiting and audit.
- Use stable machine-readable errors and API pagination conventions.
- Audit provider changes without recording submitted secret values.

## 8. UI contract

Console **Notification Channels** page should show a **Voice Provider** card when the feature is enabled. The card opens a provider list and create/edit workflow.

UI fields:
- Provider name
- ARI URL
- ARI username
- ARI password (write-only; empty on edit)
- ARI application
- Outbound endpoint
- Dialplan context and extension
- Caller ID
- Ring timeout
- Maximum call duration
- Language
- Enabled state

UI behavior:
- Show whether a secret is configured, not its value.
- Editing without entering a new password preserves the existing secret.
- Display validation and connection-test results.
- Warn before deleting a provider referenced by an active delivery policy.
- Follow the product-matched `admin.sms-providers.v1` patterns where applicable.
- UI visibility is not authorization; backend permission checks remain mandatory.

## 9. Feature flag

A feature configuration (working name `voiceProviders`) may control card visibility. The final key and source must follow the product-matched Console feature-configuration mechanism.
