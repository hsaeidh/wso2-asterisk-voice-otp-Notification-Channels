# Open Decisions, Risks and Design Review Register

This register distinguishes verified product facts from unresolved implementation decisions.

| ID | Topic | Current status | Required evidence / decision |
|---|---|---|---|
| ODR-01 | WSO2 product versions | **Verified** in product POM and distribution inventory | Re-check installed manifests at build/release |
| ODR-02 | Governance extension point | **Not extensible by enum addition alone** in inspected 1.15.9 source; manager is instantiated/registered directly | Determine minimal supported patch; do not rely on OSGi service ranking without proof |
| ODR-03 | Password Recovery VOICE semantics | **Requires patch or proven extension**: built-in channel validation and OTP event-property mapping are channel-sensitive | Define VOICE property key and runtime event handler contract |
| ODR-04 | Same-OTP handoff in Password Recovery | Required invariant; no safe cross-channel contract proven yet | Identify exact method/context and delivery handoff |
| ODR-05 | Same-OTP handoff in Login MFA | SMSOTP 3.4.2 generates OTP and stores it in AuthenticationContext before sending | Integrate at this dispatch point; preserve resend/validation semantics |
| ODR-06 | Notification Sender v2 routes | Existing routes verified; no VOICE route or test operation exists | Extend OpenAPI and generated/service implementation consistently |
| ODR-07 | Sender config/runtime event dispatch | API delegates to tenant-config management service; REST-only implementation is incomplete | Inspect and extend event-handler notification 1.12.13 where required |
| ODR-08 | Secret API | Product bundle version locked; exact public API contract not yet verified | Inspect target Secret Manager exports and supported usage |
| ODR-09 | Tenant vs Organization scope | API v2 route is `/t/{tenant-domain}/api/server/v2` | Prove Organization-to-tenant/config mapping; do not assume organizationId is accepted |
| ODR-10 | Delivery persistence | Durable HA-capable store required | Select supported store and transaction/lease model |
| ODR-11 | ARI call-to-dialplan transition | ARI/Stasis plus controlled dialplan intended | Validate exact ARI sequence on deployed Asterisk |
| ODR-12 | OTP handoff to Asterisk | **Security blocker** until demonstrated safe | Prove OTP does not leak through ARI requests, Asterisk variables, logs, events or tracing |
| ODR-13 | Persian `SayDigits` assets | Required | Confirm package, filenames and audio quality on target Asterisk |
| ODR-14 | SMS failure semantics | Gateway acceptance may not mean handset delivery | Document actual provider response and DLR capabilities |
| ODR-15 | UI version | **Verified:** Console 3.3.29; Apps Core 4.0.15 | Match source/build artifacts exactly |
| ODR-16 | Provider deletion semantics | Must protect active deliveries | Decide reject/defer/soft-delete behavior |
| ODR-17 | Retry policy | One fallback attempt proposed by default | Confirm business policy and abuse limits |
| ODR-18 | Multi-organization behavior | Requirement established, API scope not yet proven | Test root/child organization and tenant context |

## High risks

### R1 — OTP cannot safely be recovered after restart
If the OTP exists only in memory and a node restarts before fallback, the coordinator may be unable to continue the same challenge. The solution must use a supported protected handoff/retrieval mechanism or fail safely. Persisting plaintext OTP is not an acceptable shortcut.

### R2 — Asterisk exposes dialplan variables
Asterisk verbose/debug output, dialplan tracing, ARI events or custom UserEvents may reveal the OTP. Review actual runtime output and disable/redact sensitive diagnostics. Never put OTP in ARI application arguments or event payloads. This remains a release blocker until tested.

### R3 — WSO2 internal extension points are not stable
Replacing internal managers or patching product bundles can create upgrade and OSGi risks. Prefer public extension points; if unavailable, keep patches small, version-pinned and separately tested.

### R4 — SMS delivery outcome ambiguity
Gateway acceptance is not proof of handset delivery. Without DLR, SMS-to-Voice fallback on handset-level failure cannot be guaranteed. Expose actual semantics and do not overstate delivery assurance.

### R5 — API management is not runtime delivery
Adding a Voice provider CRUD endpoint does not cause WSO2 notification events to be delivered over Voice. The sender config service and event dispatch path must also recognize and route VOICE.

### R6 — Voice call cost and toll fraud
A compromised API or weak provider authorization can create billable calls. Restrict destination, rate, duration, caller ID and test-call privileges.

## Decision log

| Date | Decision |
|---|---|
| 2026-11-07 | Target WSO2 Identity Server 7.3.0 and Java 21. |
| 2026-11-07 | Product component versions locked in `version-lock.md`. |
| 2026-11-07 | Voice is a first-class notification channel with value `VOICE`. |
| 2026-11-07 | Asterisk is accessed directly through ARI; WSO2 does not use SIP. |
| 2026-11-07 | Asterisk playback is exclusively `SayDigits`. |
| 2026-11-07 | WSO2 owns OTP generation, validation and challenge lifecycle. |
| 2026-11-07 | Existing WSO2 SMS provider is reused. |
| 2026-11-07 | Fallback is required in both directions and must reuse the same OTP/challenge. |
| 2026-11-07 | Password Recovery and Login MFA use separate adapters with shared delivery coordination only where safe. |
| 2026-11-07 | No dependent-repository implementation changes until its patch plan is verified and approved. |
