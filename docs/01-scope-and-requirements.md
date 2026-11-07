# Scope and Requirements

**Project:** WSO2 Asterisk Voice OTP Notification Channel  
**Target platform:** WSO2 Identity Server 7.3.0  
**Status:** Architecture baseline; implementation has not started  
**Last reviewed:** 2026-11-07

## 1. Objective

Provide a first-class `VOICE` notification channel in WSO2 Identity Server (IS), backed by Asterisk ARI, manageable from the WSO2 Console, and usable by Password Recovery and Login MFA. Voice must be exposed as a real notification channel, not merely as an unrelated authenticator or a custom outbound HTTP call.

The design must preserve WSO2 as the authority for OTP generation, challenge ownership, validation, expiry, retry policy and authentication decisions. Asterisk is a call-and-playback system only.

## 2. In-scope capabilities

- A `VOICE` channel identifier integrated into the relevant WSO2 notification-channel flows.
- Organization-scoped Voice Provider management: create, read, list, update, delete and test connectivity.
- Console UI entry under **Notification Channels**, labelled **Voice Provider**, with provider configuration screens.
- Asterisk ARI integration for outbound calls.
- OTP playback using **Asterisk `SayDigits` only**.
- Password Recovery OTP delivery through Voice.
- Login MFA OTP delivery through Voice.
- Reuse of the existing WSO2 SMS provider and SMS delivery path.
- Two-way fallback between SMS and Voice, preserving the same OTP and the same WSO2 challenge.
- Delivery lifecycle tracking, bounded retries, idempotency and HA-safe processing.
- Unit, integration, compatibility, OSGi, security and operational tests.
- Deployment and rollback documentation.

## 3. Explicit non-goals

- WSO2 must not delegate OTP generation or validation to Asterisk.
- No SIP stack or SIP credentials are to be implemented in the WSO2 connector. WSO2 talks to Asterisk over ARI; Asterisk handles its configured telephony trunk.
- No alternative TTS engine, external TTS service or text-to-speech fallback. Playback is exclusively `SayDigits`.
- No independent Voice OTP challenge that competes with the WSO2 challenge.
- No OTP value in application logs, delivery-state persistence, ARI application arguments, channel names, user events or metrics labels.
- No automatic modification of unrelated WSO2 repositories until the required changes are separately approved and implemented.

## 4. Functional requirements

| ID | Requirement | Acceptance evidence |
|---|---|---|
| FR-01 | Voice is represented by the stable channel value `VOICE`. | Governance and notification-channel tests |
| FR-02 | Console displays a Voice Provider entry under Notification Channels when the feature is enabled. | Console UI test and browser verification |
| FR-03 | Authorized administrators can create, list, inspect, update, delete and test providers. | REST and UI integration tests |
| FR-04 | Provider configuration and secrets are organization-scoped. | Cross-organization isolation tests |
| FR-05 | Password Recovery can select and execute Voice delivery. | End-to-end recovery test |
| FR-06 | Login MFA can select and execute Voice delivery. | End-to-end authentication test |
| FR-07 | SMS and Voice fallback reuse the original OTP and challenge. | Deterministic fallback tests |
| FR-08 | Asterisk receives a call request through ARI and plays digits with `SayDigits`. | ARI integration test and call trace |
| FR-09 | Voice delivery reports meaningful terminal and intermediate outcomes. | State-machine tests |
| FR-10 | Repeated delivery requests do not create duplicate calls or conflicting challenges. | Idempotency/concurrency tests |
| FR-11 | Provider secrets are never returned by read APIs. | API serialization/security tests |
| FR-12 | Asterisk Persian digit prompts are verified on the target deployment. | Persian playback acceptance test |

## 5. Non-functional requirements

- **Compatibility:** WSO2 IS 7.3.0, Java 21 and the exact component versions resolved from the product distribution.
- **Security:** TLS for ARI and WSO2 APIs; least-privilege ARI account; secret-store integration; redacted logs.
- **Isolation:** tenant/organization-aware authorization and provider resolution.
- **Availability:** safe operation with multiple IS nodes and process restarts.
- **Reliability:** bounded timeouts, explicit state transitions, idempotency and recovery of in-flight work.
- **Observability:** correlation IDs and outcome metrics without OTP or other secrets.
- **Maintainability:** modular OSGi bundles, documented extension points and reproducible Maven builds.

## 6. Delivery semantics and fallback

The two channels are delivery mechanisms for one logical WSO2 challenge. A fallback must not create a second OTP or silently replace the active challenge.

The coordinator must receive or resolve the original challenge context and OTP through a controlled in-memory handoff. If WSO2 does not expose a safe extension point for obtaining the same OTP in both flows, the relevant WSO2 component must be patched rather than generating a second OTP in this project.

A successful SMS gateway submission is not proof that a handset received the message. SMS-to-Voice fallback can be triggered only by outcomes the configured SMS provider exposes (for example synchronous rejection or a delivery receipt, if supported). The limitation must be visible in configuration and documentation.

## 7. Acceptance gates

Implementation is not complete until all acceptance criteria in [09-acceptance-criteria.md](09-acceptance-criteria.md) pass on the target WSO2 IS 7.3.0 distribution. A successful Maven build alone is insufficient.
