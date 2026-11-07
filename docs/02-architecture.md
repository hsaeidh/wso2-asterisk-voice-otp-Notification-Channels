# Architecture

**Status:** Proposed architecture baseline. Interfaces and component boundaries are design contracts, not implemented APIs.

## 1. System context

```text
                         WSO2 Identity Server 7.3.0
  +---------------------------------------------------------------------+
  | Console UI: Notification Channels -> Voice Provider                |
  |       |                                                             |
  |       v                                                             |
  | Notification Sender API v2: /notification-senders/voice (proposed)  |
  |       |                                                             |
  |       v                                                             |
  | Sender Management / tenant-config persistence / Secret adapter      |
  |                                                                     |
  | Password Recovery adapter --------+                                 |
  |                                    +--> Delivery Coordinator       |
  | Login MFA / SMSOTP adapter --------+          |                     |
  |                                               +--> Voice adapter    |
  |                                               |       |             |
  |                                               |       v             |
  |                                               |    Asterisk ARI     |
  |                                               |       |             |
  |                                               |       v             |
  |                                               |  Stasis/Dialplan    |
  |                                               |   SayDigits         |
  |                                               |                     |
  |                                               +--> WSO2 SMS sender  |
  |                                                                     |
  | Notification event handler / VOICE dispatch integration (required  |
  | where WSO2 event-based notification path is used)                  |
  +---------------------------------------------------------------------+
```

## 2. Trust boundaries

1. **WSO2 identity boundary:** owns user identity, OTP generation, challenge validity, expiry and authentication outcome.
2. **Management boundary:** API v2 is tenant-domain routed. Organization isolation must be implemented only after proving the authenticated organization-to-tenant/configuration mapping in IS 7.3.0.
3. **Configuration boundary:** non-secret provider settings belong in the supported notification sender configuration model or a compatible extension. Credentials belong in a secret facility verified from the exact product runtime.
4. **ARI boundary:** WSO2 authenticates to Asterisk ARI over TLS. ARI credentials must be least privilege.
5. **Telephony boundary:** Asterisk selects the configured outbound trunk and executes the controlled dialplan. WSO2 does not use SIP.
6. **SMS boundary:** SMS requests reuse the WSO2 SMS sender/provider and its real delivery semantics.

## 3. Core modules

| Module | Responsibility |
|---|---|
| `voice-otp-api` | Stable Java service interfaces, DTOs and error model |
| `voice-otp-core` | Provider validation, tenant/organization context and policy |
| `voice-otp-config` | Provider CRUD and secret adapter |
| `voice-otp-ari` | ARI client, call lifecycle and playback result handling |
| `voice-otp-delivery` | Delivery coordinator, state machine, idempotency and fallback |
| `voice-otp-governance` | WSO2 notification-channel and Password Recovery integration |
| `voice-otp-authenticator` | Login MFA integration with the existing OTP challenge |
| `voice-otp-rest` | REST resource/service integration, authorization and serialization |
| `voice-otp-osgi` | Declarative Services components, service registration and lifecycle |
| `voice-otp-tests` | Unit, integration, compatibility, security and operational tests |

These are proposed modules, not existing WSO2 artifact names. Final OSGi boundaries must follow the product dependency graph and avoid split packages or duplicate exported classes.

## 4. WSO2 integration surfaces

The official product baseline is:
- Identity Governance / Recovery `1.15.9`
- Identity Server API `1.5.31`
- Notification Event Handler / sender tenant config `1.12.13`
- SMS OTP Authenticator `3.4.2`
- Console UI `3.3.29`, Apps Core `4.0.15`
- Java 21

The v2 Notification Sender API is an administrative sender-configuration API, not by itself the runtime notification dispatch mechanism. Adding Voice therefore requires both:
1. a provider-management/configuration path compatible with the sender-management service; and
2. a runtime delivery path that consumes the WSO2 notification event/challenge context and invokes the Voice adapter.

Do not assume that adding a new REST endpoint makes the notification channel dispatchable.

## 5. Core service contracts

Provider management is a **dedicated OSGi service**, not an external implementation of the private notification-sender `ChannelConfigurationHandler` registry. The normative interface, DTOs, persistence and secret contracts, scope resolution, validation, REST behavior and acceptance tests are specified in [Voice Provider Management Service Contract](13-voice-provider-management-service-contract.md).

At the architecture level, keep these boundaries:
- `VoiceProviderManagementService`: scoped CRUD and non-billable connection test;
- `ProviderScopeResolver`: derives scope only from authenticated WSO2 context;
- `VoiceProviderRepository`: persists non-secret provider metadata with optimistic concurrency;
- `VoiceProviderSecretStore`: stores and resolves opaque secret references;
- `VoiceProviderResolver`: resolves an authorized provider for delivery;
- `AsteriskAriConnectionTester`: performs a read-only ARI check and never originates a call.

The exact WSO2 Secret Manager service, Organization context accessors and persistence API remain runtime-verification items. Do not invent class names or assume a product API from another release.

Credentials may exist only inside the trusted service boundary and must never be serialized to API responses or logs.

### Delivery

```java
interface VoiceDeliveryService {
    DeliveryReceipt submit(VoiceDeliveryRequest request);
    DeliveryStatus getStatus(String effectiveConfigScope, String deliveryId);
    void cancel(String effectiveConfigScope, String deliveryId);
}
```

### Challenge adapter

```java
interface OtpChallengeAdapter {
    String getChallengeReference();
    String getDestination();
    String getEffectiveConfigScope();
    OtpPurpose getPurpose();
    boolean isValid();
    Instant getExpiryTime();
}
```

The OTP itself must be supplied only through a protected delivery handoff. It must not be added to a generally serializable challenge DTO.

## 6. Password Recovery and Login MFA

Use separate WSO2 adapters because the recovery and authentication flows have different context objects, extension points and failure semantics. They may share the delivery coordinator and channel adapters only after the same-challenge contract is proven.

Password Recovery source currently resolves only the built-in EMAIL and SMS channel values in its channel validation path. The event payload also uses different property keys for SMS OTP and other confirmation codes. VOICE integration must explicitly define its OTP property/event contract and ensure the downstream event handler receives the intended value. Do not assume that adding the enum alone is sufficient.

Login MFA's SMSOTP authenticator generates an OTP and stores it in the authentication context before dispatch. Voice fallback must be integrated into that existing operation; chaining two independent authenticators is not sufficient.

## 7. ARI call flow

1. Resolve the effective provider scope and provider.
2. Validate challenge and destination.
3. Create an internal delivery ID and idempotency key.
4. Originate the call into a dedicated Stasis application.
5. On answer, route the call to a controlled dialplan extension.
6. Set the Asterisk channel language to `fa` when Persian is selected.
7. Execute `SayDigits` through the controlled playback path.
8. Collect playback/termination outcome.
9. Complete delivery state and clean up channel/call resources.

The exact ARI originate/continue/Stasis sequence must be validated against the deployed Asterisk version. The OTP-to-dialplan handoff remains a security blocker until its exposure through ARI, Asterisk logs, channel variables and dialplan tracing has been tested.

## 8. Asterisk dialplan contract (illustrative)

```asterisk
[voice-otp]
exten => s,1,Answer()
 same => n,Set(CHANNEL(language)=fa)
 same => n,SayDigits(${OTP})
 same => n,Hangup()
```

This example documents the intended playback primitive only. It is not production-ready. The mechanism that populates `OTP` and reports playback completion must be proven safe. Do not put the OTP in ARI appArgs, channel names, UserEvent payloads or diagnostic logs.

## 9. Failure model

Expected outcomes include `SUBMITTED`, `RINGING`, `ANSWERED`, `PLAYING`, `COMPLETED`, `BUSY`, `NO_ANSWER`, `REJECTED`, `ARI_ERROR`, `PLAYBACK_FAILED`, `CANCELLED`, `EXPIRED` and `FAILED`.

An answered call is not a successful delivery; require trustworthy playback completion. SMS gateway acceptance is not handset delivery.

## 10. Design constraints

- Tenant/organization scope must come from authenticated WSO2 context.
- Provider selection must be deterministic and documented.
- Provider deletion must not silently break active deliveries.
- A node restart must not cause unbounded retries or duplicate calls.
- Challenge expiry must be checked before call start and fallback.
- Asterisk events must be correlated by opaque delivery ID, never OTP.
