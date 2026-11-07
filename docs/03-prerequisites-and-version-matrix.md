# Prerequisites and Version Matrix

**Target:** WSO2 Identity Server 7.3.0 / Java 21  
**Status:** Product versions verified against the official `wso2/product-is:v7.3.0` POM and distribution license inventory on 2026-11-07.

## 1. Product-locked versions

| Component | Version | Evidence in product source | Notes |
|---|---:|---|---|
| WSO2 Identity Server | `7.3.0` | Root POM | Target release |
| Java compiler/runtime baseline | `21` | Product POM compiler source/target | Build and runtime must use Java 21 |
| Identity Governance / Recovery | `1.15.9` | `identity.governance.version`; recovery JAR inventory | Exact source tag `v1.15.9` |
| Identity Server API modules | `1.5.31` | `identity.server.api.version`; Notification Sender v2 JAR inventory | Exact source tag `v1.5.31` |
| Notification Event Handler / Sender tenant config | `1.12.13` | `identity.event.handler.notification.version`; tenant-config JAR inventory | Runtime notification dispatch and sender config must be reviewed here |
| SMS OTP Authenticator | `3.4.2` | Product distribution JAR inventory | External extension source tag `v3.4.2`; present in product distribution |
| Identity Apps Console | `3.3.29` | `identity.apps.console.version` | UI source must match this release |
| Identity Apps Core | `4.0.15` | `identity.apps.core.version`; distribution JAR inventory | Not the Console UI version |

The table above is the product-aligned dependency baseline. It supersedes the earlier "verify exact product version" status for these components. It does **not** prove that every internal package/API is a supported extension point; those still require source-level compatibility review.

## 2. Primary source evidence

- Product root POM: [wso2/product-is v7.3.0/pom.xml](https://github.com/wso2/product-is/blob/v7.3.0/pom.xml)
- Product distribution inventory: [wso2/product-is v7.3.0/LICENSE.txt](https://github.com/wso2/product-is/blob/v7.3.0/LICENSE.txt)
- Governance channel enum: [identity-governance v1.15.9 NotificationChannels.java](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/service/notification/NotificationChannels.java)
- Notification Sender v2 OpenAPI: [identity-api-server v1.5.31 notification-sender.yaml](https://github.com/wso2/identity-api-server/blob/v1.5.31/components/org.wso2.carbon.identity.api.server.notification.sender/org.wso2.carbon.identity.api.server.notification.sender.v2/src/main/resources/notification-sender.yaml)
- SMS OTP authenticator: [identity-outbound-auth-sms-otp v3.4.2 SMSOTPAuthenticator.java](https://github.com/wso2-extensions/identity-outbound-auth-sms-otp/blob/v3.4.2/component/authenticator/src/main/java/org/wso2/carbon/identity/authenticator/smsotp/SMSOTPAuthenticator.java)
- Governance service activation: [IdentityMgtServiceComponent.java](https://github.com/wso2-extensions/identity-governance/blob/v1.15.9/components/org.wso2.carbon.identity.governance/src/main/java/org/wso2/carbon/identity/governance/internal/IdentityMgtServiceComponent.java)

## 3. Required platform

| Component | Requirement | Status |
|---|---|---|
| WSO2 Identity Server | 7.3.0 | Product target and version locked |
| Java | 21 | Product target and version locked |
| Maven | Use product-compatible Maven and plugin versions | Verify against product build environment |
| Asterisk | ARI enabled; outbound trunk configured; dedicated Stasis/dialplan path | Deployment-specific version must be recorded |
| Asterisk language assets | Persian (`fa`) digit prompts 0–9 compatible with `SayDigits` | Must be installed and tested |
| TLS | Valid certificates for WSO2-to-ARI and Console-to-API paths | Deployment prerequisite |
| WSO2 secret facility | Use the secret APIs available in the actual 7.3.0 runtime | Exact bundle/API contracts still need source verification |
| Organization context | Must map authenticated Console/API context to the intended organization configuration | Tenant/organization semantics remain an open verification item |
| SMS provider | Existing WSO2 SMS sender/provider operational | Environment prerequisite |

## 4. Product repositories and planned changes

| Repository | Product/source baseline | Planned responsibility |
|---|---:|---|
| This Voice project | New | Voice provider runtime, ARI, delivery coordinator and adapters |
| `identity-governance` | `1.15.9` | Add VOICE channel support and Password Recovery channel semantics if no public extension point exists |
| `identity-api-server` | `1.5.31` | Extend Notification Sender API v2 contract and service |
| `identity-event-handler-notification` | `1.12.13` | Extend sender configuration and/or notification event dispatch for VOICE, subject to exact source inspection |
| `identity-outbound-auth-sms-otp` | `3.4.2` | Integrate Voice delivery into the existing OTP generation/challenge flow for same-OTP fallback |
| `identity-apps` | Console `3.3.29`, Core `4.0.15` | Add Voice Provider UI using product-matched source |

## 5. Important distinction: API tenant path vs organization scope

The Notification Sender v2 OpenAPI server URL is tenant-domain based:

`https://localhost:9443/t/{tenant-domain}/api/server/v2`

This is not, by itself, proof that the API is organization-scoped in the WSO2 Organizations model. The implementation must establish how an authenticated organization context maps to the tenant/configuration context in IS 7.3.0. Do not invent an `organizationId` storage model or assume root/child organization isolation until verified with the actual Organization Management APIs and runtime context.

## 6. Asterisk prerequisites

- ARI enabled and protected with a dedicated service account.
- TLS enabled; certificate chain trusted by the WSO2 JVM.
- Dedicated ARI application/Stasis application.
- Dedicated outbound trunk and dialplan context.
- Outbound caller ID authorized by the carrier.
- Dialplan restricted to the OTP call path.
- Persian digit audio prompts installed and verified.
- Asterisk event/log configuration reviewed for sensitive argument leakage.
- Firewall allows only required WSO2-to-ARI traffic.
- Clock synchronization enabled on WSO2 and Asterisk hosts.

## 7. Environment values to collect (never commit secrets)

- WSO2 base URL and organization/tenant deployment model.
- Asterisk ARI URL, TLS trust chain, ARI application name.
- Outbound endpoint/trunk identifier.
- Dialplan context and extension.
- Permitted caller ID.
- Ring timeout and maximum call duration.
- Language code and audio prompt package/version.
- SMS provider capabilities, especially synchronous errors and delivery receipts.
- HA topology and persistence technology available to the product.

Never commit actual host credentials, ARI passwords, tokens, private keys, customer phone numbers or production configuration exports.
