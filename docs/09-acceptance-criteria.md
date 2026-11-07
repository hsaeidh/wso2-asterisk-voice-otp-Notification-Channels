# Acceptance Criteria

A release is acceptable only when all mandatory criteria below pass on the exact target product build.

## A. First-class Notification Channel

- [ ] Channel identifier is `VOICE` and is recognized by the relevant WSO2 code paths.
- [ ] Unknown-channel fallback logic does not silently replace Voice with Email/SMS.
- [ ] Notification Channels Console displays **Voice Provider** when enabled.
- [ ] Provider management UI uses the authenticated organization context.
- [ ] Backend authorization is enforced independently of UI visibility.

## B. Provider management

- [ ] Create/list/get/update/delete/test operations work.
- [ ] Provider names are organization-scoped and validated.
- [ ] Secret is never returned by GET/list.
- [ ] Empty password on update preserves existing secret.
- [ ] Secret rotation works.
- [ ] Delete behavior with active deliveries is deterministic.
- [ ] Cross-organization access is denied.

## C. Asterisk ARI and playback

- [ ] WSO2 authenticates to ARI over validated TLS.
- [ ] ARI account has only required privileges.
- [ ] Outbound call is originated using the configured endpoint.
- [ ] Answered call enters the intended application/dialplan.
- [ ] OTP digits are played using Asterisk `SayDigits` only.
- [ ] Persian digits 0–9 are audible and intelligible on the real telephony path.
- [ ] Playback completion is distinguished from answer/hangup.
- [ ] OTP is absent from ARI metadata, events and all relevant logs.

## D. Password Recovery

- [ ] Voice is selectable/usable in Password Recovery.
- [ ] OTP is generated and validated by WSO2.
- [ ] Challenge expiry, retry and attempt limits remain enforced.
- [ ] Voice failure can fall back to SMS using the same OTP/challenge.
- [ ] SMS failure can fall back to Voice using the same OTP/challenge, subject to available SMS failure signals.

## E. Login MFA

- [ ] Voice is selectable/usable in Login MFA.
- [ ] Existing WSO2 SMS OTP challenge generation is reused.
- [ ] Voice and SMS fallback do not generate independent OTPs.
- [ ] Authentication context survives retry and channel fallback.
- [ ] Challenge expiry and attempt limits remain enforced.

## F. Reliability and HA

- [ ] Duplicate requests do not create duplicate calls.
- [ ] Concurrent requests converge on one delivery.
- [ ] Out-of-order ARI events do not corrupt state.
- [ ] Node restart does not create duplicate calls or a new OTP.
- [ ] In-flight delivery reconciliation is deterministic.
- [ ] Terminal records expire according to retention policy.

## G. Security

- [ ] OTP is never persisted or logged.
- [ ] ARI secrets are protected and redacted.
- [ ] SSRF and redirect protections pass.
- [ ] Cross-organization access tests pass.
- [ ] Rate limiting and toll-fraud controls pass.
- [ ] Audit events contain no OTP or secret.

## H. Build and packaging

- [ ] Maven build succeeds with Java 21 and product-matched dependencies.
- [ ] OSGi bundles resolve on clean WSO2 IS 7.3.0.
- [ ] Declarative Services components activate.
- [ ] Console build succeeds against the product-matched UI source.
- [ ] Install/upgrade/rollback procedures are rehearsed.
- [ ] Build artifacts, checksums, versions and test reports are recorded.

## Release blockers

Any OTP leakage, cross-organization access, mismatched OTP during fallback, duplicate call caused by retry/restart, unverified Persian playback, or unresolved OSGi dependency is a release blocker.
