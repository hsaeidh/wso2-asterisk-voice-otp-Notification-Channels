# Architecture

**Status:** Proposed architecture baseline. Interfaces and component boundaries are design contracts, not implemented APIs.

## 1. System context

```text
                         WSO2 Identity Server 7.3.0
  +------------------------------------------------------------------+
  | Console UI                                                      |
  |   Notification Channels -> Voice Provider                       |
  |          |                                                       |
  |          v                                                       |
  | Notification Sender REST API v2 (extend existing API if viable) |
  |          |                                                       |
  | Provider Management / Organization Resolver / Secret Adapter    |
  |                                                                  |
  | Password Recovery adapter ----+                                  |
  |                                +--> OTP Delivery Coordinator     |
  | Login MFA adapter ------------+          |                       |
  |                                           +--> Voice Adapter      |
  |                                           |       |               |
  |                                           |       v               |
  |                                           |    Asterisk ARI       |
  |                                           |       |               |
  |                                           |       v               |
  |                                           |   Dialplan/SayDigits  |
  |                                           |                       |
  |                                           +--> WSO2 SMS Adapter   |
  +------------------------------------------------------------------+
```

## 2. Trust boundaries

1. **WSO2 identity boundary:** owns user identity, organization context, OTP generation, challenge validity, expiry and authentication outcome.
2. **Provider configuration boundary:** stores non-secret provider settings in organization scope and credentials in the WSO2 secret facility.
3. **ARI boundary:** WSO2 authenticates to the configured Asterisk ARI endpoint over TLS. ARI credentials must be least privilege.
4. **Telephony boundary:** Asterisk selects the outbound trunk and executes the controlled dialplan. It must not receive the OTP in ARI metadata.
5. **SMS boundary:** SMS requests use the already configured WSO2 SMS provider. The Voice project should not duplicate SMS credentials or gateway configuration.

## 3. Core modules

| Module | Responsibility |
|---|---|
| `voice-otp-api` | Stable Java service interfaces, DTOs and error model |
| `voice-otp-core` | Provider resolution, validation, organization context and policy |
| `voice-otp-config` | Organization-scoped provider CRUD and secret integration |
| `voice-otp-ari` | ARI client, call lifecycle and playback result handling |
| `voice-otp-delivery` | Delivery coordinator, state machine, idempotency and fallback |
| `voice-otp-governance` | WSO2 notification-channel integration and Password Recovery adapter |
| `voice-otp-authenticator` | Login MFA integration and reuse of the existing challenge |
| `voice-otp-rest` | REST resource/service integration, authorization and serialization |
| `voice-otp-osgi` | Declarative Services components, service registration and lifecycle |
| `voice-otp-tests` | Unit, integration, compatibility, security and operational tests |

The module split is provisional. Final OSGi boundaries must follow the actual WSO2 dependency graph and avoid split packages or duplicate exported classes.

## 4. Core service contracts

### Provider management

```java
interface VoiceProviderManagementService {
    VoiceProvider create(String organizationId, VoiceProviderCreateRequest request);
    VoiceProvider get(String organizationId, String name);
    List<VoiceProviderSummary> list(String organizationId);
    VoiceProvider update(String organizationId, String name, VoiceProviderUpdateRequest request);
    void delete(String organizationId, String name);
    ProviderTestResult test(String organizationId, String name);
}
```

### Provider resolution

```java
interface VoiceProviderResolver {
    ResolvedVoiceProvider resolve(String organizationId, String providerName);
}
```

The resolved object may contain credentials only inside the trusted service boundary and must never be serialized to API responses or logs.

### Delivery

```java
interface VoiceDeliveryService {
    DeliveryReceipt submit(VoiceDeliveryRequest request);
    DeliveryStatus getStatus(String organizationId, String deliveryId);
    void cancel(String organizationId, String deliveryId);
}
```

### Challenge adapter

```java
interface OtpChallengeAdapter {
    String getChallengeReference();
    String getDestination();
    String getOrganizationId();
    OtpPurpose getPurpose();
    boolean isValid();
    Instant getExpiryTime();
}
```

The OTP itself must be supplied only through a protected, short-lived in-memory delivery handoff. It must not be added to a generally serializable challenge DTO.

## 5. Password Recovery and Login MFA

Use separate WSO2 adapters for Password Recovery and Login MFA because their lifecycle, context objects, extension points and error handling differ. They may share the coordinator and channel adapters only after the same-challenge contract is proven.

Do not implement a second authenticator that independently generates a new OTP as a fallback. In particular, chaining the existing SMS OTP authenticator and a new Voice authenticator is not sufficient to guarantee OTP identity.

## 6. ARI call flow

1. Resolve organization and provider.
2. Validate challenge and destination.
3. Create an internal delivery ID and idempotency key.
4. Ask ARI to originate the call into a dedicated Stasis application.
5. On answer, route the call to a controlled dialplan extension.
6. Set the Asterisk channel language to `fa` when Persian is selected.
7. Execute `SayDigits` using a protected channel variable populated by the controlled call handoff.
8. Collect playback/termination outcome.
9. Complete the delivery state and clean up channel variables and call resources.

The exact ARI originate/continue/Stasis sequence must be validated against the deployed Asterisk version and ARI API. Do not treat the illustrative dialplan as a production-ready implementation.

## 7. Asterisk dialplan contract (illustrative)

```asterisk
[voice-otp]
exten => s,1,Answer()
 same => n,Set(CHANNEL(language)=fa)
 same => n,SayDigits(${OTP})
 same => n,Hangup()
```

This example documents the intended playback primitive only. The actual mechanism for setting `OTP`, entering the context and reporting completion must be designed so the OTP is not exposed through ARI application arguments, channel names, UserEvent payloads or logs. The implementation must prove whether the selected Asterisk path can meet this requirement; otherwise revise the handoff design before coding.

## 8. Failure model

Expected outcomes include `SUBMITTED`, `RINGING`, `ANSWERED`, `PLAYING`, `COMPLETED`, `BUSY`, `NO_ANSWER`, `REJECTED`, `ARI_ERROR`, `PLAYBACK_FAILED`, `CANCELLED`, `EXPIRED` and `FAILED`.

Only authoritative Asterisk/ARI signals may advance a call to a successful playback state. An answered call is not equivalent to OTP playback completion.

## 9. Design constraints

- All operations must carry an organization identifier derived from authenticated WSO2 context, not trusted from an arbitrary client-supplied field.
- Provider selection must be deterministic and documented.
- Provider deletion must not silently break active deliveries; define reference and in-flight deletion behavior.
- A node restart must not cause an unbounded retry or duplicate call.
- The coordinator must enforce challenge expiry before starting a call and before fallback.
- Asterisk events must be correlated by opaque delivery ID, never OTP.
