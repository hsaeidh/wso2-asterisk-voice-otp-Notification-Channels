# Upstream WSO2 Contribution Map — First-Class VOICE Notification Channel

**Target:** WSO2 Identity Server 7.3.0  
**Target baseline:** Java 21; see [version-lock.md](version-lock.md).  
**Review date:** 2026-11-07  
**Purpose:** Give WSO2 maintainers and contributors a reviewable, source-oriented map of the upstream changes needed to add a first-class `VOICE` Notification Channel and merge it into the official WSO2 repositories.

> **Scope of this document change:** This file is documentation in the dedicated Voice project only. It does not make or imply any source-code change in WSO2 upstream repositories. The user has authorized documenting the upstream change map; implementation commits in upstream repositories remain separate contribution work.

## 1. Desired upstream outcome

The feature is not complete merely because a Voice OTP authenticator exists. The upstream product change must make Voice a first-class notification delivery channel with all of the following:

1. A stable `VOICE` channel identifier recognized by the server-side channel manager.
2. Voice provider configuration that an authorized administrator can create, view (without secrets), update, delete and test.
3. A documented and supported server-side service contract for resolving the provider in the authenticated tenant/Organization context.
4. A dedicated Voice delivery event/handler that does not route OTP data through Email or SMS templates/adaptors.
5. Password Recovery support using the already-created WSO2 recovery code/challenge.
6. Login MFA support that dispatches the OTP already created by the existing SMSOTP lifecycle.
7. SMS↔Voice fallback without generating a second OTP or creating a second challenge.
8. Console → Notification Channels UI for Voice Provider management.
9. Product-aligned tests, documentation, scopes/permissions, deployment configuration and backward compatibility.

The existing WSO2 Voice OTP authenticator is a candidate for reuse only after source review. It is not proof that the Notification Channel, provider management, Recovery, fallback and Console requirements already exist. See [document 15](15-existing-wso2-voice-otp-compatibility-review.md).

## 2. Upstream repositories and exact source areas

The versions below are pinned to the IS 7.3.0 product source inventory. Paths are relative to each repository root. File names and paths are grounded in the source inspection recorded in [document 12](12-file-level-patch-plan.md). A row marked **conditional** must only be changed if the selected implementation puts that responsibility in that module.

### 2.1 `wso2-extensions/identity-governance` — tag `v1.15.9`

**Why this repository:** server-side notification channel recognition and Password Recovery's channel selection/event construction.

| File / source area | Expected upstream change | Review classification |
|---|---|---|
| `components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/service/notification/NotificationChannels.java` | Add the canonical `VOICE` channel constant. Keep identifiers stable and avoid changing EMAIL/SMS/EXTERNAL values. | Required |
| `components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/service/impl/notification/DefaultNotificationChannelManager.java` | Add VOICE to supported channels and validate the preferred-channel configuration/claim consistently. | Required |
| `components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/IdentityMgtServiceComponent.java` | Review current construction/registration of DefaultNotificationChannelManager. Change only if the chosen supported service design requires it; do not rely on OSGi ranking to replace a component-created default. | Conditional |
| `components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/password/NotificationPasswordRecoveryManager.java` | Accept VOICE in server-supported channel validation; resolve the voice recipient claim; build the Voice event using the same existing recovery code/challenge. Define missing-phone and dispatch-failure behavior. | Required |
| `components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/util/Utils.java` | Add explicit Voice event-name resolution if a dedicated Voice event is selected. Do not silently map Voice to the generic Email notification event. | Required if dedicated event |
| `components/org.wso2.carbon.identity.recovery/src/test/java/**` | Tests for channel recognition, recipient claim, event name/payload, challenge preservation, resend and fallback. | Required |
| `components/org.wso2.carbon.identity.governance/src/test/java/**` | Tests for supported-channel list and preferred-channel validation. | Required |

**Important semantics:** current Recovery logic treats SMS specially for OTP event properties while non-SMS channels follow a different branch. The VOICE change must specify the OTP property and recipient property explicitly. The same recovery data record and code must remain authoritative. Do not generate another code in the Voice sender.

Source links:
- [NotificationChannels.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/service/notification/NotificationChannels.java)
- [DefaultNotificationChannelManager.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/service/impl/notification/DefaultNotificationChannelManager.java)
- [IdentityMgtServiceComponent.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/IdentityMgtServiceComponent.java)
- [NotificationPasswordRecoveryManager.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/password/NotificationPasswordRecoveryManager.java)
- [Utils.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/util/Utils.java)

### 2.2 `wso2-extensions/identity-event-handler-notification` — tag `v1.12.13`

**Why this repository:** a supported provider configuration service and the runtime event dispatch path.

#### Provider management / persistence

| File / source area | Expected upstream change | Review classification |
|---|---|---|
| `components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/NotificationSenderManagementService.java` | Only if maintainers choose to extend the shared sender service: add Voice CRUD contract and DTOs. Preferred lower-coupling alternative is a dedicated exported VoiceProviderManagementService. | Architectural choice |
| Same module: `NotificationSenderManagementServiceImpl.java` | Only for shared-service option: implement Voice persistence, validation, revision/conflict behavior and Organization scoping. | Conditional |
| Same module: `NotificationSenderManagementConstants.java` | Add Voice resource identifiers and error codes only if this module owns the Voice resource implementation. | Conditional |
| Same module: `internal/NotificationSenderTenantConfigServiceDS.java` | Register the selected Voice management service and its supported references. | Required if service hosted here |
| Same module: `internal/NotificationSenderTenantConfigDataHolder.java` | Internal/private holder must not become an external bundle's integration contract. Modify only within this bundle if the shared-service design truly needs it. | Conditional |
| Same module: `utils/NotificationSenderSecretProcessor.java` | Reuse the product-supported Secret Manager/SecretResolveManager pattern if secrets are stored here; enforce write-only reads and preserve-on-omitted-update. | Conditional |
| Same module: `utils/NotificationSenderUtils.java` | Add Voice resource mapping only if the shared sender persistence model is selected. | Conditional |
| New Voice DTOs and tests | Validate input, secret rotation, secret redaction, Organization isolation, deletion guard and serialization. | Required for implementation |

#### Runtime event delivery

| File / source area | Expected upstream change | Review classification |
|---|---|---|
| `components/event-handler-notification/org.wso2.carbon.identity.event.handler.notification/src/main/java/org/wso2/carbon/identity/event/handler/notification/internal/NotificationHandlerServiceComponent.java` | Register a dedicated Voice event handler service if this bundle owns runtime dispatch. | Required for this hosting option |
| New `VoiceOtpNotificationHandler.java` in the event-handler module | Consume only the dedicated Voice OTP event; validate properties; resolve effective provider server-side; dispatch to Voice delivery service. | Required for this hosting option |
| `internal/NotificationHandlerDataHolder.java` | Add only the OSGi service reference needed by the handler. Never store OTP or provider secrets in the holder. | Conditional |
| Runtime tests | Test event subscription, duplicate suppression, tenant/Organization isolation, redaction and dispatch failures. | Required |

**Extension-point warning:** `ChannelConfigurationHandler` is not a safe external plug-in registration point in 1.12.13: the registry holder is internal/private and the DS component registers the default handler itself. A standalone Voice bundle must not import the private holder. Either implement Voice management inside this repository or expose a separate public OSGi service with explicit package exports/imports.

**Event warning:** `DefaultNotificationHandler` resolves Email and SMS and can fall back to a configured default for unknown channels. Voice must have a dedicated event subscription/handler; do not overload Email template handling or the SMS publisher.

**Product event configuration:** the IS 7.3.0 integration-test artifact contains `module.name.2=emailSend` and `emailSend.subscription.1=TRIGGER_NOTIFICATION`. Add a distinct Voice module name and subscription in the correct product/runtime configuration mechanism. The integration-test file is evidence of the event contract, not automatically the production deployment file.

Source links:
- [NotificationSenderManagementService.java — v1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/NotificationSenderManagementService.java)
- [NotificationSenderTenantConfigServiceDS.java — v1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/internal/NotificationSenderTenantConfigServiceDS.java)
- [NotificationSenderSecretProcessor.java — v1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/utils/NotificationSenderSecretProcessor.java)
- [NotificationHandlerServiceComponent.java — v1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/event-handler-notification/org.wso2.carbon.identity.event.handler.notification/src/main/java/org/wso2/carbon/identity/event/handler/notification/internal/NotificationHandlerServiceComponent.java)
- [DefaultNotificationHandler.java — v1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/event-handler-notification/org.wso2.carbon.identity.event.handler.notification/src/main/java/org/wso2/carbon/identity/event/handler/notification/DefaultNotificationHandler.java)

### 2.3 `wso2/identity-api-server` — tag `v1.5.31`

**Why this repository:** expose provider management to administrators through the supported Notification Sender REST API v2.

Main module:
`components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/`

| File / source area | Expected upstream change | Review classification |
|---|---|---|
| `src/main/resources/notification-sender.yaml` | Add Voice list/create/get/update/delete endpoints and a separately authorized connection-test endpoint. Define request/response schemas and error responses. | Required |
| `src/gen/java/**/v2/NotificationSendersApi.java` | Regenerate JAX-RS API routes/annotations from the OpenAPI contract, or use the repository's accepted generated-source workflow. | Required |
| `src/gen/java/**/v2/NotificationSendersApiService.java` | Generated service contract must include Voice operations. | Required |
| `src/gen/java/**/v2/model/VoiceSender*.java` | Generated Voice request/response DTOs. Never include secret values in GET responses. | Required |
| `src/main/java/**/v2/impl/NotificationSendersApiServiceImpl.java` | Implement endpoint delegation, response codes, Location/ETag or revision behavior, tenant checks and safe error mapping. | Required |
| `src/main/java/**/v2/core/NotificationSenderManagementService.java` | Map REST models to the selected Voice OSGi service without changing Email/SMS/Push behavior. | Required |
| `src/main/java/**/v2/factories/NotificationSenderManagementServiceFactory.java` or new Voice service factory | Resolve the Voice service through supported OSGi wiring. Do not assume Email/SMS service owns Voice. | Required |
| Module POM, OpenAPI generation config and tests | Ensure generated source, dependencies, OSGi imports and API tests are reproducible. | Required |

Expected resource shape (final naming must follow maintainer API conventions):
- `/notification-senders/voice`
- `/notification-senders/voice/{sender-name}`
- `/notification-senders/voice/{sender-name}/test`

Existing internal sender scopes include `internal_config_mgt_view`, `internal_config_mgt_add`, `internal_config_mgt_update` and `internal_config_mgt_delete`. The connection-test operation needs an explicit least-privilege permission decision; do not silently grant it through an overly broad scope. API paths are tenant-domain based, so Organization authorization must come from authenticated server context, not caller-supplied Organization IDs.

Source:
- [Notification Sender v2 OpenAPI — v1.5.31](https://github.com/wso2/identity-api-server/blob/v1.5.31/components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/src/main/resources/notification-sender.yaml)
- [Notification Sender v2 API service implementation — v1.5.31](https://github.com/wso2/identity-api-server/tree/v1.5.31/components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2)

### 2.4 `wso2-extensions/identity-outbound-auth-sms-otp` — tag `v3.4.2`

**Why this repository:** Login MFA must reuse the OTP already generated by the SMSOTP authenticator.

Primary file:
`component/authenticator/src/main/java/org/wso2/carbon/identity/authenticator/smsotp/SMSOTPAuthenticator.java`

Expected change areas:
- Existing OTP generation and write to `SMSOTPConstants.OTP_TOKEN` in `AuthenticationContext`.
- Existing branch that chooses direct SMS REST delivery or WSO2 notification event dispatch.
- Resend, expiry, validation and failure handling.
- Authenticator configuration and tests.

The implementation should introduce or call a delivery coordinator with the existing OTP/context. It must not invoke a second independent OTP generator for Voice fallback. Existing SMS configuration and behavior must remain backward compatible. Tests must cover SMS-first→Voice fallback and Voice-first→SMS fallback using one context OTP and one expiry.

Source:
- [SMSOTPAuthenticator.java — v3.4.2](https://github.com/wso2-extensions/identity-outbound-auth-sms-otp/blob/v3.4.2/component/authenticator/src/main/java/org/wso2/carbon/identity/authenticator/smsotp/SMSOTPAuthenticator.java)

### 2.5 `wso2/identity-apps` — Console UI (product-matched source ref unresolved)

**Why this repository:** administrators must see and manage Voice Provider under Console → Notification Channels.

The IS 7.3.0 distribution pins Console UI `3.3.29` and Identity Apps Core `4.0.15`. The previously inspected identity-apps refs `v3.3.29` and `3.3.29` were not found. Therefore, paths below are confirmed candidates from the accessible source tree, **not yet certified against the exact Console source commit shipped in IS 7.3.0**. Do not submit a product PR based on a newer default branch without locating the matching source baseline.

| Candidate file / area | Expected upstream change | Review classification |
|---|---|---|
| `features/admin.email-and-sms.v1/pages/email-and-sms.tsx` | Add Voice Provider card/entry to Notification Channels. | Required after ref match |
| New `features/admin.voice-providers.v1/**` | Provider list, create, edit, delete, connection test, form validation and API client. | Required |
| `apps/console/src/configs/routes.tsx` | Register Voice Provider route. | Required |
| `features/admin.core.v1/constants/app-constants.ts` | Add route key/path constants. | Required |
| `features/admin.core.v1/utils/route-utils.ts` | Include Voice route in navigation/selection behavior. | Conditional on route pattern |
| `features/admin.extensions.v1/configs/endpoints.ts` | Add Notification Sender v2 Voice endpoint definitions. | Required |
| `apps/console/java/org.wso2.identity.apps.console.server.feature/resources/deployment.config.json.j2` | Add feature enablement/configuration and permission declarations as required by this Console architecture. | Required after ref match |
| `features/index.ts`, `features/rollup.config.cjs`, `apps/console/package.json` | Register/build the new feature package. | Required |
| `modules/i18n/src/translations/**` | Add English and supported locale labels, errors and help text. | Required |
| Feature unit/integration tests | CRUD, validation, authorization visibility, API error handling and secret write-only behavior. | Required |

Source discovery:
- [identity-apps repository](https://github.com/wso2/identity-apps)
- [Candidate Email and SMS page](https://github.com/wso2/identity-apps/tree/master/features/admin.email-and-sms.v1)
- [IS 7.3.0 product version lock](version-lock.md)

### 2.6 `wso2/product-is` — product assembly and integration verification

This repository should not be treated as the primary place to implement the feature logic. It is the product composition/integration layer.

Review and update only if the upstream component PRs require product-level assembly:
- Product POM dependency/version management for newly published WSO2 artifacts.
- Distribution feature/bundle inclusion and provisioning metadata.
- Product-level event-handler configuration and deployment defaults.
- Integration test artifacts and test suites for clean IS 7.3.0 startup and end-to-end flows.
- Distribution inventory/release documentation where required.

Known integration-test event config:
`modules/integration/tests-integration/tests-backend/src/test/resources/artifacts/IS/default-identity-event.properties`

It currently contains the Email event module/subscription entries. Add Voice registration only if the product's actual event configuration mechanism requires it. Do not assume this test fixture is the production configuration file.

Source:
- [product-is v7.3.0](https://github.com/wso2/product-is/tree/v7.3.0)
- [default-identity-event.properties](https://github.com/wso2/product-is/blob/v7.3.0/modules/integration/tests-integration/tests-backend/src/test/resources/artifacts/IS/default-identity-event.properties)

## 3. Recommended upstream PR split

Do not submit one oversized PR spanning all repositories. WSO2 maintainers should be able to review and merge small, independently testable changes in dependency order.

| Order | Upstream PR | Contents | Depends on |
|---:|---|---|---|
| 0 | Architecture/API proposal (issue or design PR) | Channel semantics, event schema, provider service contract, permissions, secret handling, Organization scope and fallback invariant. | None |
| 1 | `identity-event-handler-notification` | Dedicated Voice provider service/persistence (or accepted shared sender model), Secret Manager integration, OSGi registration and Voice event handler. | PR 0 |
| 2 | `identity-api-server` | Notification Sender v2 OpenAPI, generated models/routes, service implementation, authorization and tests. | PR 1 service contract |
| 3 | `identity-governance` | VOICE channel recognition and Password Recovery event/recipient/OTP mapping with same-challenge tests. | PR 0 and Voice event contract |
| 4 | `identity-outbound-auth-sms-otp` | Login MFA integration with existing OTP context and bidirectional fallback tests. | PR 1 event/service contract |
| 5 | `identity-apps` | Console Notification Channels entry and Voice Provider UI against the merged API. | PR 2; exact Console ref |
| 6 | `product-is` | Dependency alignment, distribution provisioning and end-to-end integration tests. | Merged component PRs |

A maintainer may choose a different split or repository ownership. Keep each PR scoped to one repository and link dependent PRs/issues in its description. Avoid cross-repository commits that require maintainers to approve code outside their ownership.

## 4. Contract that should be agreed before code review

The following must be written down in the architecture/API proposal before implementation PRs are opened:

### 4.1 Channel and event contract
- Canonical channel value: `VOICE`.
- Dedicated event name and version.
- Exact property names/types for tenant, user, user store, recipient phone claim, OTP and correlation/idempotency key.
- Event delivery semantics: synchronous acceptance vs asynchronous completion.
- No Email/SMS handler fallback for Voice events.

### 4.2 OTP/challenge contract
- WSO2 is the only OTP generator and validator.
- Recovery uses the existing recovery code and record.
- MFA uses the existing SMSOTP `AuthenticationContext` OTP and expiry.
- Delivery adapters receive OTP only transiently; no second OTP, challenge or independent expiry.
- Retries and fallback use a stable idempotency key and cannot create duplicate calls.
- Define how Voice playback completion is confirmed; answered call alone is not successful OTP delivery.

### 4.3 Provider API contract
- Tenant and Organization scope resolved from authenticated server context.
- Provider CRUD, revision/concurrency semantics and active-delivery deletion guard.
- Secrets are write-only; GET never returns secret or secret reference.
- Omitted secret during update preserves current secret.
- Connection test is read-only, non-billable where possible, rate-limited and separately authorized.
- ARI URL validation includes HTTPS, host/port allowlist and connection-time DNS/IP pinning.
- Inheritance semantics must be explicit; do not accidentally merge or inherit providers.

### 4.4 Security and operations
- Never persist/log OTP plaintext or put it in URLs, channel names, ARI appArgs, UserEvents or general event-bus logs.
- Define secure OTP handoff to Asterisk `SayDigits`, including HTTP and Asterisk log redaction.
- Define TLS trust, certificate rotation, ARI least privilege and outbound network policy.
- Define rate limits, toll-fraud controls, per-user/per-provider call caps and audit events.
- Define HA lease/locking, crash recovery, retry and cleanup behavior.

## 5. Maintainer-friendly PR acceptance checklist

Each upstream PR should include:

- [ ] Exact target tag/commit and supported Java version.
- [ ] Problem statement and why existing extension points are insufficient.
- [ ] Minimal diff limited to the owning repository.
- [ ] Public API/OSGi contracts documented; no dependency on private/internal packages across bundles.
- [ ] Backward compatibility statement for Email, SMS, Push and existing authenticator behavior.
- [ ] Unit tests for positive, negative, boundary and concurrency cases.
- [ ] Tenant and Organization isolation tests.
- [ ] Secret redaction and no-OTP-in-logs tests.
- [ ] Failure, retry, idempotency, restart and fallback tests.
- [ ] Build/test command and actual CI result.
- [ ] API/OpenAPI/generated-source consistency where relevant.
- [ ] Docs/configuration/permissions updated.
- [ ] No unrelated formatting or refactoring changes.
- [ ] Cross-repository dependency PRs linked, with merge order explained.

## 6. Important unresolved items before upstream submission

These are not to be represented as solved:

1. Exact source commit for Console 3.3.29 / Apps Core 4.0.15 must be located.
2. Exact supported Secret Manager APIs and OSGi service references must be verified against IS 7.3.0 source/runtime.
3. Production persistence design and transactional behavior must be chosen; current Voice repository abstractions alone do not implement it.
4. Secure OTP transfer to Asterisk and redaction across ARI/Asterisk logs must be proven.
5. The existing WSO2 Voice OTP authenticator must be source-reviewed for reuse, not presumed compatible.
6. The final owner of provider persistence and event dispatch (existing notification bundle vs dedicated Voice bundle) must be agreed with WSO2 maintainers.
7. The exact production event-handler registration/configuration mechanism must be verified in the installed product distribution.

## 7. Scope boundary and change control

This contribution map authorizes no code changes in WSO2 repositories. Its purpose is to make future upstream PRs small, source-grounded and easy for the owning WSO2 maintainers to review. The dedicated Voice repository may hold the reference implementation, tests and documentation; upstream product changes must be proposed as separate repository-specific PRs and merged by their maintainers.

## 8. Official product references

- [WSO2 IS 7.3.0 release](https://github.com/wso2/product-is/releases/tag/v7.3.0)
- [WSO2 IS 7.3.0 documentation](https://is.docs.wso2.com/en/7.3.0/)
- [Notification Sender v2 API documentation for IS 7.3.0](https://is.docs.wso2.com/en/7.3.0/apis/notification-senders-v2/)
- [Identity Governance source](https://github.com/wso2-extensions/identity-governance/tree/v1.15.9)
- [Notification Event Handler source](https://github.com/wso2-extensions/identity-event-handler-notification/tree/v1.12.13)
- [Identity API Server source](https://github.com/wso2/identity-api-server/tree/v1.5.31)
- [SMS OTP Authenticator source](https://github.com/wso2-extensions/identity-outbound-auth-sms-otp/tree/v3.4.2)
