# Testing, Deployment and Operations

## 1. Test layers

### Unit tests
- Provider validation and normalization.
- Organization-aware provider resolution.
- Secret omission/preservation behavior.
- State-machine transition legality.
- Fallback decision matrix.
- Idempotency key and duplicate request handling.
- Challenge expiry and cancellation.
- Error normalization and log redaction.

### Integration tests
- ARI authentication and connectivity test.
- ARI originate, answer, Stasis/dialplan handoff and hangup.
- Busy, no-answer, reject, timeout and network failure.
- Playback completion and playback failure.
- SMS adapter behavior using the existing WSO2 SMS provider.
- Same OTP/challenge through both channel orders.
- Provider CRUD and secret rotation.
- Organization-scoped access.

### WSO2 compatibility tests
- Clean IS 7.3.0 startup.
- OSGi bundle resolution and DS activation.
- Password Recovery channel selection.
- Login MFA channel selection.
- API v2 registration, scopes and authorization.
- Console feature discovery and provider CRUD.
- Multi-organization and parent/child organization behavior.

### Security tests
- OTP absent from logs, database, metrics and ARI metadata.
- Password absent from API responses and diagnostics.
- SSRF and redirect restrictions.
- TLS certificate validation.
- Cross-organization access denial.
- Rate limit and toll-fraud protection.
- Malformed ARI events and replayed callbacks.
- Unauthorized provider test/delete/update.

### Resilience tests
- WSO2 node restart during each delivery state.
- Asterisk restart and ARI reconnect.
- Duplicate and out-of-order ARI events.
- Network partition and slow response.
- Two WSO2 nodes processing the same challenge concurrently.
- Persistence outage and recovery.
- Provider deletion/update during an active delivery.

## 2. Persian playback acceptance

- Verify the Asterisk `fa` language directory contains the required digit prompt files.
- Verify each digit 0–9 independently.
- Verify multi-digit OTP order and pauses.
- Test pronunciation using the exact deployed Asterisk version and sound package.
- Verify playback completion is observable.
- Inspect Asterisk verbose/debug logs and ensure the OTP is not printed.
- Test real carrier path, DTMF/codec/audio path and call termination.

A call that is answered but does not play all digits is a failure.

## 3. Deployment prerequisites

- Supported WSO2 IS 7.3.0 installation and Java 21 runtime.
- Product-matched bundle versions and dependency lock.
- ARI endpoint reachable over TLS from all relevant IS nodes.
- Dedicated ARI user and application.
- Outbound trunk, dialplan and caller ID configured.
- Persian audio prompts installed.
- Existing WSO2 SMS provider configured if fallback is enabled.
- Secret store and delivery persistence available.
- Firewall and DNS entries configured.
- Monitoring and log redaction enabled.

## 4. Installation sequence (to be finalized after packaging)

1. Back up WSO2 configuration, deployment artifacts and relevant persistence.
2. Verify product version and Java runtime.
3. Install/verify Asterisk ARI and dialplan prerequisites.
4. Deploy the Voice bundles and any explicitly approved WSO2 patch bundles.
5. Restart or perform the product-supported deployment lifecycle.
6. Verify OSGi bundle and DS component status.
7. Configure the provider through Console/API.
8. Run a non-billable ARI connectivity test.
9. Run a controlled test call to an authorized test destination.
10. Execute Password Recovery and Login MFA acceptance tests.
11. Enable the channel for a pilot organization before broad rollout.

Exact JAR placement, restart requirements and bundle replacement steps must be documented only after verifying the product's deployment conventions. Do not overwrite WSO2 JARs by guesswork.

## 5. Rollback

- Disable Voice channel selection and stop accepting new Voice deliveries.
- Allow or safely terminate in-flight calls.
- Remove Voice bundles using the documented deployment mechanism.
- Revert only the explicitly changed WSO2 patch bundles to their backed-up versions.
- Preserve or clean delivery metadata according to the retention policy.
- Verify Email/SMS Password Recovery and Login MFA still work.
- Verify no OSGi resolution errors remain.

## 6. Operational monitoring

Metrics:
- Delivery attempts by channel and normalized outcome.
- ARI connection and request latency.
- Ring duration and call duration.
- Playback completion/failure counts.
- Fallback counts and reasons.
- Active/in-flight deliveries.
- Expired and reconciled delivery counts.

Never use OTP, phone number, username, raw challenge reference or credentials as metric labels.

Alerts:
- ARI unavailable.
- Sudden increase in no-answer/busy/playback failures.
- Persian sound prompt missing.
- Fallback surge.
- Stuck non-terminal deliveries.
- Delivery persistence unavailable.
- Secret resolution failure.

## 7. Operational runbooks to add before production

- ARI connectivity failure.
- Carrier/trunk outage.
- Persian sound assets missing.
- Stuck delivery reconciliation.
- Credential rotation.
- Provider secret recovery.
- WSO2 node restart during active call.
- Rollback and disablement.
