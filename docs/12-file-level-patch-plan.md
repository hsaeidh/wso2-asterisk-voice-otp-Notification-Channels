# File-Level WSO2 Patch Plan — Voice Notification Channel

**Review date:** 2026-11-07  
**Target:** WSO2 Identity Server 7.3.0 / Java 21  
**Status:** Source inspection and patch planning only. No dependent-repository code changes, builds, or runtime tests have been performed.

## 1. Architecture decisions from source inspection

### 1.1 ChannelConfigurationHandler is not a public plug-in point

In identity-event-handler-notification 1.12.13:

- ChannelConfigurationHandler is exported, but its contract is specifically Email/SMS sender configuration operations.
- NotificationSenderTenantConfigDataHolder is in the internal package. The bundle POM marks that package private.
- NotificationSenderTenantConfigServiceDS.activate() registers only DefaultChannelConfigurationHandler.
- The handler map is an internal implementation detail. An independent OSGi bundle cannot safely register a handler through the private holder package.

Therefore, implementing ChannelConfigurationHandler in a separate Voice bundle is **not** a supported drop-in solution.

Preferred direction:
1. Keep Voice provider management in a dedicated VoiceProviderManagementService OSGi service and its own resource/secret namespace; or
2. Patch the notification-sender tenant-config bundle to add first-class Voice DTO/service methods.

Option 1 reduces coupling to internal WSO2 implementation details. Option 2 provides a more uniform sender API but increases product patching. The API v2 layer can call a dedicated OSGi service if its registration and package/import/export contract are explicitly added.

### 1.2 Provider management and runtime dispatch are separate

The existing API v2 delegates to NotificationSenderManagementService; that service persists sender configuration and, for Email/SMS, deploys event publisher configuration. This is not the same thing as delivering an OTP.

Voice runtime should use a dedicated event contract and delivery service. Do not route an OTP through an Email template/SMTP publisher or reuse the SMS publisher as a transport shim.

### 1.3 Password Recovery has channel-sensitive branches

In Governance/Recovery 1.15.9:
- NotificationPasswordRecoveryManager.getServerSupportedNotificationChannel() explicitly accepts EMAIL, SMS and EXTERNAL.
- NotificationPasswordRecoveryManager.triggerNotification() places SMS OTP in OTP_TOKEN_STRING; all non-SMS channels are given CONFIRMATION_CODE.
- Utils.resolveEventName() returns the SMS-specific event name only for SMS; all other channels use TRIGGER_NOTIFICATION.

A Voice implementation must deliberately define:
- whether Voice uses a dedicated event name (recommended);
- the exact OTP event property and recipient property;
- the channel-specific claim URI and verification semantics;
- how a failed Voice attempt is handed to SMS without changing the Recovery challenge.

Simply adding VOICE to the enum will not complete this path.

### 1.4 Login MFA owns OTP before dispatch

SMSOTP 3.4.2 generates the OTP, stores it under SMSOTPConstants.OTP_TOKEN in AuthenticationContext, then calls either the configured direct SMS REST URL or the WSO2 notification event path.

The Voice adapter must join this dispatch lifecycle and reuse the existing context OTP. A second independent authenticator that generates a second OTP is not acceptable for fallback.

## 2. Exact source files and intended changes

### A. Governance / Password Recovery — identity-governance v1.15.9

| Source file | Required change |
|---|---|
| components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/service/notification/NotificationChannels.java | Add VOICE identifier and explicit destination/verified-claim mapping after the business claim contract is finalized. |
| components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/service/impl/notification/DefaultNotificationChannelManager.java | Add VOICE to supported channels and preferred-channel claim validation. |
| components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/IdentityMgtServiceComponent.java | Review service registration. The component directly constructs and registers the default manager; service-ranking override is not proven safe. |
| components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/password/NotificationPasswordRecoveryManager.java | Accept VOICE in server-supported channel validation; define Voice recipient and event-property mapping; preserve the same recovery code. |
| components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/util/Utils.java | Extend resolveEventName() if Voice uses a dedicated event (recommended). |
| Recovery tests under components/org.wso2.carbon.identity.recovery/src/test/java/ | Add channel resolution, event-name, OTP-property and fallback tests. |
| Governance tests under components/org.wso2.carbon.identity.governance/src/test/java/ | Add VOICE support and preferred-channel claim validation tests. |

Do not broaden this patch to Self Registration, Mobile Number Verification or unrelated recovery flows unless their product requirements explicitly include Voice.

### B. Notification sender configuration and runtime — identity-event-handler-notification v1.12.13

#### Provider management candidate files

| Source file | Required change |
|---|---|
| components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/NotificationSenderManagementService.java | If using the shared service approach, add Voice CRUD methods and a Voice DTO contract. Otherwise leave unchanged and expose a separate Voice OSGi service. |
| Same module: NotificationSenderManagementServiceImpl.java | If shared service is selected, implement Voice CRUD, resource type/name rules, validation, Organization context and deletion behavior. |
| Same module: NotificationSenderManagementConstants.java | Add Voice resource/publisher constants and Voice-specific error codes only if this module owns persistence. |
| Same module: internal/NotificationSenderTenantConfigServiceDS.java | Register the Voice management service and required WSO2 service references if the dedicated-service design is selected. |
| Same module: internal/NotificationSenderTenantConfigDataHolder.java | Avoid exposing or depending on this private holder from an external bundle. Modify only for the shared-service option inside this module. |
| Same module: utils/NotificationSenderSecretProcessor.java | Reuse the proven SecretManager/SecretResolveManager pattern if this module owns Voice secrets; ensure write-only reads and preserve-on-omitted-update semantics. |
| Same module: utils/NotificationSenderUtils.java | Add resource/DTO mapping only if Voice persistence is placed in this module. |
| New VoiceSenderDTO and tests | Define validated provider data and test CRUD, secret lifecycle, inheritance, delete protection and serialization. |

#### Runtime event delivery candidate files

| Source file | Required change |
|---|---|
| components/event-handler-notification/org.wso2.carbon.identity.event.handler.notification/src/main/java/org/wso2/carbon/identity/event/handler/notification/internal/NotificationHandlerServiceComponent.java | Register a dedicated Voice event handler as an AbstractEventHandler OSGi service if runtime delivery is implemented in this module. |
| New VoiceOtpNotificationHandler.java | Consume only the dedicated Voice OTP event; validate required properties; resolve effective Organization/provider server-side; submit to Voice delivery service. Do not pass OTP through the Email DataBridge stream. |
| Same module: internal/NotificationHandlerDataHolder.java | Add dependencies only if Voice handler is implemented here; do not store secrets or OTP in the holder. |
| Product event configuration default-identity-event.properties | Register a dedicated module name and event subscription for the Voice event. The official product test artifact currently maps emailSend to TRIGGER_NOTIFICATION; add a separate Voice mapping rather than overloading the Email handler. |
| Runtime tests | Verify event registration, tenant/organization isolation, duplicate suppression, asynchronous failure behavior and redaction. |

The event handler module's DefaultNotificationHandler resolves only EMAIL and SMS; unsupported channels are changed to the configured default. Its Email and SMS event/template handling must not be assumed to support Voice.

### C. Notification Sender REST API — identity-api-server v1.5.31

| Source file | Required change |
|---|---|
| components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/src/main/resources/notification-sender.yaml | Add Voice list/create/get/update/delete operations and a separately authorized test operation. |
| .../src/gen/java/.../v2/NotificationSendersApi.java | Regenerate or consistently update JAX-RS routes and annotations from the OpenAPI contract. |
| .../src/gen/java/.../v2/NotificationSendersApiService.java | Add generated service signatures. |
| .../src/gen/java/.../v2/model/VoiceSender*.java | Add generated request/response models. |
| .../src/main/java/.../v2/impl/NotificationSendersApiServiceImpl.java | Implement HTTP status, tenant restrictions, Location header and Voice operation delegation. |
| .../src/main/java/.../v2/core/NotificationSenderManagementService.java | Map API models to the selected Voice OSGi service; preserve existing Email/SMS/Push behavior. |
| .../src/main/java/.../v2/factories/NotificationSenderManagementServiceFactory.java or a new Voice service factory | Resolve the Voice OSGi service without assuming the existing Email/SMS service can manage Voice. |
| Module POM and integration tests | Verify generated-source inclusion, OSGi imports, OpenAPI validation, permissions/scopes and endpoint behavior. |

The API v2 base is /t/{tenant-domain}/api/server/v2. Existing sender scopes use:
- internal_config_mgt_view
- internal_config_mgt_add
- internal_config_mgt_update
- internal_config_mgt_delete

The test operation needs an explicit least-privilege scope decision; do not silently reuse a broad add/update scope. The API is tenant-domain based; Organization authorization must be resolved from authenticated server context.

### D. Login MFA — identity-outbound-auth-sms-otp v3.4.2

Primary source:
component/authenticator/src/main/java/org/wso2/carbon/identity/authenticator/smsotp/SMSOTPAuthenticator.java

Required change areas:
- the method that generates OTP and writes SMSOTPConstants.OTP_TOKEN into AuthenticationContext;
- the subsequent branch that selects direct REST SMS or WSO2 notification dispatch;
- resend/expiry/validation lifecycle;
- authenticator configuration and tests.

Implement a coordinator around the existing OTP, not a second OTP generator. Direct SMS URL behavior must be preserved. Voice-first and SMS-first paths must retain the same context OTP and expiry.

### E. Console UI — product-matched source ref still unresolved

Candidate source paths were confirmed in the currently accessible wso2/identity-apps default branch, but that branch is newer than the product-pinned Console 3.3.29. Both refs v3.3.29 and 3.3.29 returned NOT_FOUND. Therefore, these are candidate paths, not yet version-certified patch targets.

| Candidate source file | Intended change |
|---|---|
| features/admin.email-and-sms.v1/pages/email-and-sms.tsx | Add Voice Provider card to Notification Channels. |
| features/admin.voice-providers.v1/** (new feature) | Implement list/create/edit/delete/test screens, validation and API client. |
| apps/console/src/configs/routes.tsx | Add Voice Provider route. |
| features/admin.core.v1/constants/app-constants.ts | Add Voice Provider route key/path. |
| features/admin.core.v1/utils/route-utils.ts | Include Voice route in selected navigation state. |
| features/admin.extensions.v1/configs/endpoints.ts | Add Voice API v2 endpoint. |
| apps/console/java/org.wso2.identity.apps.console.server.feature/resources/deployment.config.json.j2 | Add Voice feature configuration block, scopes and enabled flag. |
| features/index.ts, features/rollup.config.cjs, apps/console/package.json | Register/build the new feature package. |
| modules/i18n/src/translations/en-US/... and supported locale files | Add Voice translations. |

The exact Console/Apps Core source commit matching the WSO2 IS 7.3.0 distribution must be found before editing or building UI code. Do not use current master as a substitute.

## 3. Product event configuration finding

The WSO2 IS 7.3.0 product integration test artifact
modules/integration/tests-integration/tests-backend/src/test/resources/artifacts/IS/default-identity-event.properties
contains:
- module.name.2=emailSend
- emailSend.subscription.1=TRIGGER_NOTIFICATION

A dedicated Voice event should have its own event-handler module name and subscription. This avoids coupling Voice to the Email handler's template and DataBridge schema. The actual production configuration location and override mechanism must be verified against the installed IS 7.3.0 distribution before packaging.

## 4. Implementation order and dependencies

1. Freeze exact source refs and artifact manifests for all six WSO2 components.
2. Implement Voice core contracts and tests in this repository without product patches.
3. Choose one provider-management ownership model: dedicated Voice OSGi service (preferred) or shared tenant-config patch.
4. Implement and test provider CRUD, Secret handling, Organization scope and authorization.
5. Implement dedicated Voice event handler and event subscription.
6. Patch Governance/Recovery channel validation and same-challenge event mapping.
7. Patch SMSOTP dispatch to call the shared coordinator with its existing context OTP.
8. Extend API v2 and generate/update its JAX-RS models/routes.
9. Patch Console only after exact product-matched source ref is confirmed.
10. Build and test in layers: module unit tests → OSGi resolution → API integration → Recovery integration → MFA integration → Asterisk call/playback → HA/failover.

## 5. Mandatory release gates

- Same OTP and same WSO2 challenge across both delivery orders.
- No OTP in logs, API URLs, ARI appArgs, channel names, UserEvents, event-bus payloads or durable plaintext state.
- Voice event is not accidentally consumed by Email/SMS handler.
- Provider CRUD and test operations enforce tenant/Organization authorization server-side.
- Secret GET is write-only; omitted secret on update preserves existing secret.
- No duplicate calls after retry/restart.
- Asterisk call completion is based on confirmed playback completion, not merely answer status.
- Existing Email and SMS flows remain unchanged.

## 6. Source references

- [Governance NotificationChannels.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/service/notification/NotificationChannels.java)
- [Governance DefaultNotificationChannelManager.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/service/impl/notification/DefaultNotificationChannelManager.java)
- [Recovery NotificationPasswordRecoveryManager.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/password/NotificationPasswordRecoveryManager.java)
- [Recovery Utils.java — v1.15.9](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/util/Utils.java)
- [SMSOTPAuthenticator.java — v3.4.2](https://github.com/wso2-extensions/identity-outbound-auth-sms-otp/blob/v3.4.2/component/authenticator/src/main/java/org/wso2/carbon/identity/authenticator/smsotp/SMSOTPAuthenticator.java)
- [ChannelConfigurationHandler.java — 1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/handlers/ChannelConfigurationHandler.java)
- [NotificationSenderTenantConfigDataHolder.java — 1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/internal/NotificationSenderTenantConfigDataHolder.java)
- [NotificationSenderTenantConfigServiceDS.java — 1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/internal/NotificationSenderTenantConfigServiceDS.java)
- [NotificationSenderManagementServiceImpl.java — 1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/notification-sender-config/org.wso2.carbon.identity.notification.sender.tenant.config/src/main/java/org/wso2/carbon/identity/notification/sender/tenant/config/NotificationSenderManagementServiceImpl.java)
- [DefaultNotificationHandler.java — 1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/event-handler-notification/org.wso2.carbon.identity.event.handler.notification/src/main/java/org/wso2/carbon/identity/event/handler/notification/DefaultNotificationHandler.java)
- [NotificationHandlerServiceComponent.java — 1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/blob/v1.12.13/components/event-handler-notification/org.wso2.carbon.identity.event.handler.notification/src/main/java/org/wso2/carbon/identity/event/handler/notification/internal/NotificationHandlerServiceComponent.java)
- [Notification Sender v2 OpenAPI — v1.5.31](https://github.com/wso2/identity-api-server/blob/v1.5.31/components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/src/main/resources/notification-sender.yaml)
- [Notification Sender v2 API service interface — v1.5.31](https://github.com/wso2/identity-api-server/blob/v1.5.31/components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/src/gen/java/org/wso2/carbon/identity/api/server/notification/sender/v2/NotificationSendersApiService.java)
- [IS 7.3.0 default identity event test configuration](https://github.com/wso2/product-is/blob/v7.3.0/modules/integration/tests-integration/tests-backend/src/test/resources/artifacts/IS/default-identity-event.properties)

## 7. Change-control status

This plan is documentation, not authorization to modify dependent WSO2 repositories. No source code in those repositories has been changed. Implementation requires explicit approval for the relevant repositories after this plan is reviewed.
