# Implementation Roadmap and Repository Change Plan

**Status:** Planning only. No code changes in dependent WSO2 repositories are authorized by this document.

## Phase 0 — Freeze product compatibility

1. Inspect the exact WSO2 IS 7.3.0 product POMs and distribution.
2. Resolve exact versions of governance, recovery, API server, SMS OTP, notification event handler, secret/config services and Console UI.
3. Inspect exported OSGi packages, DS references and actual installed bundle manifests.
4. Create `docs/version-lock.md`.
5. Build a minimal clean-room IS 7.3.0 environment for compatibility tests.

**Exit:** reproducible dependency/version matrix with source links and installed-JAR evidence.

## Phase 1 — Extension-point feasibility

1. Re-inspect notification channel manager registration and service lifecycle.
2. Determine whether a supported extension point can add `VOICE` without patching governance.
3. Inspect Password Recovery extension points and OTP handoff.
4. Inspect SMS OTP authenticator extension points and challenge lifecycle.
5. Inspect Notification Sender API v2 registration and OpenAPI conventions.
6. Inspect Secret Manager and Organization Manager APIs from exact product versions.
7. Inspect Console feature-configuration and provider management patterns.

**Exit:** file-level patch plan, with each fork justified by a missing extension point. Avoid replacing internal WSO2 services via OSGi ranking unless proven supported and stable.

## Phase 2 — Core Voice backend

1. Create Maven reactor and OSGi module boundaries.
2. Implement API contracts, provider validation and organization resolver.
3. Integrate secret storage and write-only semantics.
4. Implement ARI client and connectivity test.
5. Implement call lifecycle and safe digit playback handoff.
6. Implement delivery coordinator, idempotency and persistence.
7. Implement state reconciliation and cleanup.
8. Add metrics/audit with redaction.

**Exit:** backend unit and integration tests pass; no UI dependency.

## Phase 3 — WSO2 notification channel and Password Recovery

1. Add `VOICE` channel constant and supported-channel handling.
2. Add Password Recovery adapter using the original WSO2 OTP/challenge.
3. Ensure channel selection and fallback do not generate a second OTP.
4. Patch governance/recovery only where extension points are insufficient.

**Exit:** end-to-end Password Recovery Voice delivery and same-challenge fallback.

## Phase 4 — Login MFA

1. Integrate with SMS OTP's existing challenge/OTP generation path.
2. Implement Voice as a delivery adapter for that same OTP.
3. Implement two-way fallback without chaining independent OTP generators.
4. Preserve WSO2 authenticator context, retry and expiry behavior.

**Exit:** Login MFA Voice and fallback tests pass with one challenge.

## Phase 5 — Notification Sender API and Console

1. Extend Notification Sender API v2 or use a verified supported management extension.
2. Add provider CRUD, test operation, scopes and OpenAPI definitions.
3. Add Voice Provider card to Notification Channels.
4. Add provider list/create/edit/delete/test screens.
5. Add translations, validation, loading/error states and accessibility coverage.

**Exit:** authorized administrator can manage an organization-scoped Voice provider in Console.

## Phase 6 — Packaging and deployment

1. Build all bundles with Java 21 and product-aligned Maven dependencies.
2. Verify OSGi resolution and startup on clean IS 7.3.0.
3. Package deployment artifacts and checksums.
4. Document Asterisk configuration, TLS trust and Persian sound assets.
5. Provide backup, upgrade and rollback instructions.

**Exit:** reproducible installation on a clean test environment and successful rollback.

## 7. Repository-level change map

| Repository | Expected files/areas | Reason |
|---|---|---|
| This repository | Maven parent, module POMs, Java components, tests, docs, distribution scripts | New Voice implementation |
| `identity-governance` | Notification channel constants/manager, recovery channel resolution and event mapping | Add first-class Voice to recovery if no extension point |
| `identity-outbound-auth-sms-otp` | SMS OTP delivery integration and fallback handoff | Same OTP across SMS/Voice |
| `identity-api-server` | Notification Sender API v2 OpenAPI, DTOs, service and authorization | Provider management REST API if no extension point |
| `identity-apps` | Notification Channels card, Voice provider feature, API client, routes and translations | Console UI |

Paths and class names must be pinned to the exact source tags during Phase 0/1. This table is a change-area map, not a claim that each file must be modified.

## 8. Build strategy

- Start with the independent Voice backend.
- Build against exact WSO2 artifacts; avoid copying product classes into this repository.
- Use Maven Enforcer/dependency convergence where compatible.
- Run unit tests without a running IS.
- Run integration tests against disposable Asterisk and WSO2 instances.
- Verify OSGi package imports/exports and Declarative Services metadata.
- Test with the exact JDK and product runtime.
- Keep product patches as separate, reviewable commits/forks.

## 9. Change-control rule

No dependent repository should be modified until its exact patch plan is reviewed. A build or test claim must include the command, environment, version and result. Do not report a module as complete merely because source files were created.
