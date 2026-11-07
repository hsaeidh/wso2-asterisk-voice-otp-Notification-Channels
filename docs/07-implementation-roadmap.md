# Implementation Roadmap and Repository Change Plan

**Status:** Planning only. No code changes in dependent WSO2 repositories are authorized by this document.

## Phase 0 — Product compatibility lock (completed by source review)

The official WSO2 IS 7.3.0 root POM and distribution inventory confirm:
- Governance/Recovery `1.15.9`
- Identity Server API modules `1.5.31`
- Notification Event Handler and sender tenant config `1.12.13`
- SMS OTP Authenticator `3.4.2`
- Console UI `3.3.29`
- Identity Apps Core `4.0.15`
- Java 21

Keep these in [version-lock.md](version-lock.md). Re-check installed JAR manifests in the actual target distribution before build/release.

## Phase 1 — Extension-point and event-flow feasibility

1. Inspect notification channel enum and supported-channel manager registration in governance 1.15.9.
2. Inspect Password Recovery channel validation and event-property mapping.
3. Inspect notification event handler 1.12.13, including sender tenant-config service and runtime event dispatch.
4. Inspect Notification Sender API v2 OpenAPI, generated API interfaces and management service.
5. Inspect SMSOTP 3.4.2 generation, event dispatch, resend and validation lifecycle.
6. Inspect exact Secret Manager and Organization/Tenant APIs in the target runtime.
7. Inspect Console 3.3.29 source and feature configuration.
8. Prove a secure OTP handoff to Asterisk and restart/fallback behavior.

**Exit:** file-level patch plan with every modified repository justified. Avoid replacing internal WSO2 services via OSGi ranking unless a supported contract explicitly permits it.

## Phase 2 — Core Voice backend

1. Create Maven reactor and OSGi module boundaries.
2. Implement API contracts, provider validation and effective-scope resolver.
3. Integrate secret storage and write-only semantics.
4. Implement ARI client and non-billable connectivity test.
5. Implement call lifecycle and safe digit playback handoff.
6. Implement delivery coordinator, idempotency and durable persistence.
7. Implement state reconciliation and cleanup.
8. Add metrics/audit with redaction.

**Exit:** backend unit and integration tests pass independently of Console.

## Phase 3 — WSO2 configuration API and runtime notification dispatch

1. Extend Notification Sender v2 with `/notification-senders/voice` and `/{sender-name}` resources.
2. Add a separately specified privileged ARI connectivity-test operation.
3. Extend the underlying notification sender tenant-config management/persistence service if required; the REST API alone is not a complete provider implementation.
4. Extend the notification event handler/runtime dispatch path so VOICE events reach the Voice delivery service.
5. Verify API scopes, permissions, tenant context and Organization mapping.
6. Add integration tests for provider CRUD and event dispatch.

**Exit:** API management and runtime delivery both work; a provider cannot be managed in one scope and accidentally used by another.

## Phase 4 — Password Recovery

1. Add VOICE channel recognition and supported-channel behavior.
2. Patch recovery channel validation and OTP event-property mapping where no supported extension point exists.
3. Ensure the original WSO2 recovery challenge and OTP are passed to Voice.
4. Verify SMS/Voice fallback reuses the same challenge.
5. Preserve existing Email/SMS behavior.

**Exit:** end-to-end Password Recovery Voice delivery and same-challenge fallback.

## Phase 5 — Login MFA

1. Integrate with SMSOTP's existing OTP generation and authentication-context lifecycle.
2. Dispatch Voice using the same OTP value and challenge context.
3. Implement two-way fallback without chaining independent OTP generators.
4. Preserve resend, retry, expiry, account-lock and authentication-context behavior.

**Exit:** Login MFA Voice and fallback tests pass with one OTP challenge.

## Phase 6 — Console UI

1. Add Voice Provider card to Notification Channels in Console 3.3.29 source.
2. Add provider list/create/edit/delete/test screens.
3. Add API client, route, translations, feature configuration and validation.
4. Verify tenant/organization context and authorization.
5. Add UI tests.

**Exit:** authorized administrator can manage a provider in the correct scope.

## Phase 7 — Packaging and deployment

1. Build all bundles with Java 21 and product-aligned Maven dependencies.
2. Verify OSGi resolution and startup on clean IS 7.3.0.
3. Package deployment artifacts and checksums.
4. Document Asterisk configuration, TLS trust and Persian sound assets.
5. Provide backup, upgrade and rollback instructions.

**Exit:** reproducible installation on a clean test environment and successful rollback.

## Repository-level change map

| Repository | Expected change areas | Reason |
|---|---|---|
| This Voice repository | Maven modules, Java components, tests, docs and distribution scripts | New Voice implementation |
| `identity-governance` 1.15.9 | Channel enum/manager, recovery channel validation and event mapping | First-class Voice and Password Recovery |
| `identity-api-server` 1.5.31 | Notification Sender v2 OpenAPI, generated API/service, implementation and tests | Provider management |
| `identity-event-handler-notification` 1.12.13 | Sender tenant-config model/persistence and notification event dispatch | Store provider and deliver VOICE event |
| `identity-outbound-auth-sms-otp` 3.4.2 | OTP dispatch/fallback integration | Same OTP in Login MFA |
| `identity-apps` Console 3.3.29 / Core 4.0.15 | Notification Channels card, provider feature, API client, routes and translations | Console UI |

Exact file paths and patch necessity must be frozen against each exact tag before implementation. The table is a change-area map, not a claim that every listed file must be modified.

## Build strategy

- Start with the independent Voice backend.
- Build against exact WSO2 artifacts; do not copy WSO2 product classes into this repository.
- Run unit tests without a running IS.
- Run integration tests against disposable Asterisk and WSO2 instances.
- Verify OSGi package imports/exports and Declarative Services metadata.
- Test with the exact JDK and product runtime.
- Keep product patches as separate, reviewable commits/forks.

## Change-control rule

No dependent repository should be modified until its exact patch plan is reviewed. A build/test claim must include command, environment, version and result. Do not report a module as complete merely because source files were created.
