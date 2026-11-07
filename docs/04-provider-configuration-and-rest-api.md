# Provider Configuration and REST API

**Status:** Proposed API contract. Exact path, schema conventions, scopes and generated-client patterns must be aligned with the shipped Notification Sender API v2 implementation before coding.

## 1. Provider model

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

This is a logical model, not a final OpenAPI schema. Validate field names and supported values against the final ARI implementation.

## 2. Secret handling

- Password is write-only.
- GET/list responses expose `secretConfigured: true|false`, never the secret.
- An update that omits the password preserves the existing secret.
- An explicit secret-clear operation must be separate and strongly authorized.
- Secret storage must use the WSO2 secret facility supported by the exact target product version.
- Secret reference must be organization-scoped and bound to the provider identity.
- Provider deletion removes its secret only after safe handling of in-flight deliveries.
- Do not store credentials in ordinary configuration properties, database rows, logs, exception text or exported diagnostics.

## 3. Organization isolation

Organization identity must be derived from the authenticated WSO2 request context and checked against the caller's administrative permissions. Do not accept a caller-supplied organization ID as authoritative.

A provider name is unique within its organization, not globally, unless the target WSO2 API conventions require a different key model. Cross-organization list/get/update/delete/test requests must fail without leaking whether another organization's provider exists.

## 4. Proposed REST resources

Prefer extending the existing Notification Sender API v2 resource if source inspection confirms that it is the correct supported integration point.

Proposed logical operations:

| Method | Resource | Purpose |
|---|---|---|
| GET | `/voice` | List Voice providers in caller organization |
| POST | `/voice` | Create provider |
| GET | `/voice/{name}` | Read provider metadata |
| PUT | `/voice/{name}` | Update provider |
| DELETE | `/voice/{name}` | Delete provider |
| POST | `/voice/{name}/test` | Test ARI connectivity and minimal permissions |

The exact base path, API version, OpenAPI structure, response envelopes, error codes and scopes are **not frozen** until matched to the current API v2 source.

## 5. API behavior

- Validate URL scheme, hostname, port and path. Production must require HTTPS.
- Prevent SSRF: restrict allowed destinations by deployment policy; reject loopback/link-local/metadata endpoints unless explicitly required in a controlled test environment.
- Validate provider names, dialplan values, caller ID and timeouts.
- Enforce bounded timeouts for connectivity tests.
- Never originate a real PSTN call as a side effect of a generic connectivity test. Test authentication and ARI capabilities without placing a billable call; provide a separately named controlled test-call operation only if explicitly required.
- Use stable machine-readable error codes.
- Apply pagination according to existing API v2 conventions.
- Make create/update/delete operations auditable without recording secret values.
- Ensure API scopes/permissions align with existing provider-management APIs.

## 6. UI contract

Console **Notification Channels** page should show a **Voice Provider** card when the Voice feature is enabled. The card opens a provider list and create/edit workflow.

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
- Warn before deleting a provider referenced by an active configuration.
- Use organization context already established by Console; do not allow arbitrary organization switching without WSO2 authorization.
- Follow existing `admin.sms-providers.v1` patterns where applicable, but do not copy SMS-specific assumptions blindly.

## 7. Feature flag

A feature configuration (working name `voiceProviders`) may control card visibility. The final key and its source must follow the product's established feature-configuration mechanism. Hiding the card is not authorization; backend permissions remain mandatory.
