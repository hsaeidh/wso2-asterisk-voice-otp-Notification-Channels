# Source Validation Review — WSO2 IS 7.3.0

**Review date:** 2026-11-07  
**Target:** WSO2 Identity Server 7.3.0  
**Review type:** Documentation and architecture validation against official WSO2 source and documentation.  
**Implementation status:** Not implemented; no build or runtime test is claimed.

## 1. Executive result

The original design direction is viable as a project goal, but the initial documentation was not yet sufficiently precise for implementation. This review corrected the product version matrix, REST API path model, management-vs-runtime distinction, and dependent repository map.

The following are verified:
- Product Java compiler source/target is 21.
- Governance/Recovery is 1.15.9.
- Identity Server API modules are 1.5.31.
- Notification Event Handler and sender tenant-config are 1.12.13.
- SMS OTP authenticator 3.4.2 is present in the product distribution.
- Console UI is 3.3.29 and Apps Core is 4.0.15.
- Notification Sender v2 is rooted at `/t/{tenant-domain}/api/server/v2` and currently defines Email, SMS and Push sender resources only.
- The API v2 implementation delegates to `NotificationSenderManagementService` from the notification sender tenant-config layer.
- Governance 1.15.9's channel enum and default channel manager list only EMAIL and SMS; its service component directly instantiates and registers `DefaultNotificationChannelManager`.
- Password Recovery channel handling is channel-sensitive, including OTP event-property mapping.
- SMSOTP 3.4.2 generates the OTP, stores it in `AuthenticationContext`, and dispatches the SMS event using that value.

The following are **not yet proven**:
- A supported public extension point for adding VOICE to governance/recovery.
- Safe same-OTP handoff across WSO2 recovery/MFA, event dispatch and Voice delivery.
- Exact Secret Manager API contract and organization/tenant scoping.
- Organization-to-tenant mapping for Notification Sender API v2.
- Safe OTP handoff into Asterisk without exposure in ARI request logs, dialplan variables or Asterisk diagnostics.
- Persian audio prompt package/version and real carrier playback quality.
- HA-safe persistence/reconciliation design.

## 2. Corrections applied to the design documents

### 2.1 Product versions

The version matrix now uses the product-resolved values from the IS 7.3.0 root POM and distribution inventory. See [version-lock.md](version-lock.md).

### 2.2 Notification Sender v2 routes

The official OpenAPI defines the base URL:

`https://localhost:9443/t/{tenant-domain}/api/server/v2`

Existing paths:
- `/notification-senders/email`
- `/notification-senders/email/{sender-name}`
- `/notification-senders/sms`
- `/notification-senders/sms/{sender-name}`
- `/notification-senders/push`
- `/notification-senders/push/{sender-name}`

The proposed Voice resource is therefore `/notification-senders/voice` and `/notification-senders/voice/{sender-name}`, relative to that API base. Voice and the `/{sender-name}/test` operation are new proposed extensions, not existing endpoints.

### 2.3 API management is not runtime notification dispatch

The API service delegates to the notification sender tenant-config management service. Creating a CRUD API alone does not route notification events to Asterisk. The design now includes the notification event handler/runtime dispatch integration as a separate required workstream.

### 2.4 Organization scope

The v2 API URL is tenant-domain based. It is not evidence by itself of organization-scoped provider configuration. The docs now require proving the organization/tenant/configuration mapping and avoid treating a client-supplied `organizationId` as trusted.

### 2.5 WSO2 source behavior

Governance 1.15.9:
- `NotificationChannels` currently enumerates EMAIL, SMS and EXTERNAL.
- `DefaultNotificationChannelManager` adds EMAIL and SMS to its supported list.
- `IdentityMgtServiceComponent` constructs and registers the default manager directly.
- Password Recovery resolves supported channels and maps SMS OTP to `OTP_TOKEN_STRING`, while other channel values use `CONFIRMATION_CODE`.

SMSOTP 3.4.2:
- Generates OTP inside the authenticator.
- Stores the value under `SMSOTPConstants.OTP_TOKEN` in the authentication context.
- Sends notification events with the OTP and SMS channel metadata.

These facts make it unsafe to implement Voice as a second independent authenticator and then rely on authenticator chaining for fallback.

## 3. Official source references

- [WSO2 IS 7.3.0 product POM](https://github.com/wso2/product-is/blob/v7.3.0/pom.xml)
- [WSO2 IS 7.3.0 distribution inventory](https://github.com/wso2/product-is/blob/v7.3.0/LICENSE.txt)
- [WSO2 IS 7.3.0 release notes](https://is.docs.wso2.com/en/7.3.0/references/about-this-release/)
- [Notification Sender v2 OpenAPI, tag v1.5.31](https://github.com/wso2/identity-api-server/blob/v1.5.31/components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/src/main/resources/notification-sender.yaml)
- [Notification Sender v2 service implementation](https://github.com/wso2/identity-api-server/blob/v1.5.31/components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/src/main/java/org/wso2/carbon/identity/api/server/notification/sender/v2/impl/NotificationSendersApiServiceImpl.java)
- [Governance NotificationChannels.java](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/service/notification/NotificationChannels.java)
- [Governance DefaultNotificationChannelManager.java](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/service/impl/notification/DefaultNotificationChannelManager.java)
- [Governance IdentityMgtServiceComponent.java](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/IdentityMgtServiceComponent.java)
- [Password Recovery NotificationPasswordRecoveryManager.java](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.recovery/src/main/java/org/wso2/carbon/identity/recovery/password/NotificationPasswordRecoveryManager.java)
- [SMSOTPAuthenticator.java](https://github.com/wso2-extensions/identity-outbound-auth-sms-otp/blob/v3.4.2/component/authenticator/src/main/java/org/wso2/carbon/identity/authenticator/smsotp/SMSOTPAuthenticator.java)
- [WSO2 official release documentation](https://is.docs.wso2.com/en/versions/)

## 4. Remaining implementation blockers

1. **OTP transport security:** determine and test how the OTP reaches the Asterisk dialplan for `SayDigits` without leaking through ARI payloads, HTTP access logs, Asterisk verbose logs, dialplan tracing or events. This is a hard blocker.
2. **Same challenge:** prove the same OTP is available to both primary and fallback adapters without persisting plaintext OTP or generating a second challenge.
3. **Runtime event dispatch:** determine the precise event handler/config extension required in notification event handler 1.12.13.
4. **Organization scope:** prove the mapping between authenticated organization context and tenant-scoped sender configuration.
5. **Secret storage:** verify exact 7.3.0 Secret Manager API and bundle wiring.
6. **HA state:** select transactional persistence/lease/reconciliation strategy compatible with the product's deployment topology.

## 5. Review conclusion

The updated documents are now aligned with the verified IS 7.3.0 version baseline and the observed Notification Sender v2 API shape. The project is **design-ready for a focused implementation feasibility phase**, but not yet ready for production coding of the WSO2 integration until the blockers above are resolved. No code build, OSGi deployment, API runtime test or Asterisk call test is claimed by this documentation review.


## 5. Follow-up file-level review — 2026-11-07

A further file-by-file inspection clarified several implementation details and corrected an earlier interpretation.

### 5.1 ChannelConfigurationHandler is not an external plug-in contract

In notification sender tenant-config 1.12.13:
- ChannelConfigurationHandler is exported, but its abstract CRUD methods are specifically Email/SMS sender operations.
- NotificationSenderTenantConfigDataHolder is under the internal package.
- The module POM marks the internal package private.
- NotificationSenderTenantConfigServiceDS registers only DefaultChannelConfigurationHandler during activation.
- The handler registry is a map held by the private data holder.

Conclusion: a standalone Voice OSGi bundle cannot safely use this as a public registration extension point. Either patch this module internally or implement a separate VoiceProviderManagementService with its own persistence/resource contract and register that service through OSGi.

### 5.2 Recovery event routing needs more than channel validation

The exact Recovery 1.15.9 source shows:
- NotificationPasswordRecoveryManager.triggerNotification() writes OTP_TOKEN_STRING only for SMS; other channels use CONFIRMATION_CODE.
- NotificationPasswordRecoveryManager.getServerSupportedNotificationChannel() explicitly accepts EMAIL, SMS and EXTERNAL.
- Utils.resolveEventName() selects the SMS-specific event name only for SMS; other channels use TRIGGER_NOTIFICATION.

A Voice patch must deliberately define the Voice event name and payload property. A dedicated Voice event handler is preferred over routing Voice through the Email handler.

### 5.3 Product event-handler registration

The IS 7.3.0 product integration test artifact default-identity-event.properties contains:
- module.name.2=emailSend
- emailSend.subscription.1=TRIGGER_NOTIFICATION

The Voice handler should receive its own module name and event subscription. The exact production configuration file/override mechanism must be verified in the installed IS distribution before packaging.

### 5.4 Console source ref mismatch

The product-resolved Console version is recorded as 3.3.29, but identity-apps refs v3.3.29 and 3.3.29 both return NOT_FOUND. The currently accessible default branch is newer and must not be used as a version-certified implementation base. The file-level plan therefore marks Console file paths as candidates only until the exact product-matched source commit is located.

### 5.5 New file-level plan

See [docs/12-file-level-patch-plan.md](12-file-level-patch-plan.md) for the complete file map, implementation order, OSGi constraints, API v2 model/service changes and release gates.
