# Open Decisions, Risks and Design Review Register

This register prevents assumptions from being mistaken for verified implementation facts.

| ID | Topic | Current position | Required evidence / decision |
|---|---|---|---|
| ODR-01 | Exact WSO2 component versions | Preliminary tags inspected only | Resolve from product IS 7.3.0 POM and installed manifests |
| ODR-02 | Governance extension point | Initial source inspection suggests fixed channel handling and direct manager registration | Prove supported extension or define minimal patch |
| ODR-03 | Same-OTP handoff in Password Recovery | Required invariant | Identify exact method/context and safe handoff |
| ODR-04 | Same-OTP handoff in Login MFA | Required invariant | Inspect SMSOTP 3.4.2 against exact shipped source |
| ODR-05 | API v2 extension | Preferred if existing resource is extensible | Confirm OpenAPI/resource/service registration path |
| ODR-06 | Secret API | Use WSO2 secret facility | Confirm exact API/bundle and organization scoping |
| ODR-07 | Delivery persistence | Durable HA-capable store required | Select supported store and transaction/lease model |
| ODR-08 | ARI call-to-dialplan transition | ARI/Stasis plus controlled dialplan intended | Validate exact ARI sequence on deployed Asterisk |
| ODR-09 | OTP handoff to Asterisk | Must avoid ARI metadata and logs | Prove secure channel-variable handoff; inspect Asterisk logs |
| ODR-10 | Persian `SayDigits` assets | Required | Confirm package, filenames and audio quality on target Asterisk |
| ODR-11 | SMS failure semantics | Gateway acceptance may not mean handset delivery | Document actual provider response and DLR capabilities |
| ODR-12 | UI version | Not locked; preliminary notes contain conflicting values | Resolve exact Console UI source version from product build |
| ODR-13 | Provider deletion semantics | Must protect active deliveries | Decide reject/defer/soft-delete behavior |
| ODR-14 | Retry policy | One fallback attempt proposed by default | Confirm business policy and abuse limits |
| ODR-15 | Multi-organization behavior | Organization-scoped design | Test actual root/child organization runtime context |

## High risks

### R1 — OTP cannot safely be recovered after restart
If the OTP exists only in memory and a node restarts before fallback, the coordinator may be unable to continue the same challenge. The solution must be a supported protected handoff/retrieval mechanism or safe failure. Persisting plaintext OTP is not an acceptable shortcut.

### R2 — Asterisk exposes dialplan variables
Asterisk verbose/debug output, dialplan tracing, AMI/ARI events or custom UserEvents may reveal the OTP. Review real runtime output and disable/redact sensitive diagnostics. Never include OTP in ARI application arguments or event payloads.

### R3 — WSO2 internal extension points are not stable
Replacing internal managers or patching product bundles can create upgrade and OSGi risks. Prefer public extension points; if unavailable, keep patches small, version-pinned and separately tested.

### R4 — SMS delivery outcome ambiguity
Most SMS gateway submissions only confirm acceptance. Without DLR, SMS-to-Voice fallback on a handset-level failure cannot be guaranteed. Expose the actual semantics and do not overstate delivery assurance.

### R5 — Voice call cost and toll fraud
A compromised API or weak provider authorization can create billable calls. Restrict destination, rate, duration, caller ID and test-call privileges.

## Decision log

| Date | Decision |
|---|---|
| 2026-11-07 | Target WSO2 Identity Server 7.3.0 and Java 21. |
| 2026-11-07 | Voice is a first-class notification channel with value `VOICE`. |
| 2026-11-07 | Asterisk is accessed directly through ARI; WSO2 does not use SIP. |
| 2026-11-07 | Asterisk playback is exclusively `SayDigits`. |
| 2026-11-07 | WSO2 owns OTP generation, validation and challenge lifecycle. |
| 2026-11-07 | Existing WSO2 SMS provider is reused. |
| 2026-11-07 | Fallback is required in both directions and must reuse the same OTP/challenge. |
| 2026-11-07 | Password Recovery and Login MFA use separate adapters with shared delivery coordination where safe. |
| 2026-11-07 | No implementation changes to dependent repositories until their patch plan is verified and approved. |
