# Prerequisites and Version Matrix

**Target:** WSO2 Identity Server 7.3.0 / Java 21  
**Status:** Baseline known; exact product-resolved bundle versions must be verified before implementation.

## 1. Required platform

| Component | Requirement | Status |
|---|---|---|
| WSO2 Identity Server | 7.3.0 | Target confirmed |
| Java | Java 21 | Target confirmed |
| Maven | Version compatible with WSO2 build parent and plugin configuration | Verify from product/source build |
| Asterisk | ARI enabled; outbound trunk configured; dedicated Stasis/dialplan path | Exact deployed version to record |
| Asterisk language assets | Persian (`fa`) digit prompts 0–9 compatible with `SayDigits` | Must be installed and tested |
| TLS | Valid certificates for WSO2-to-ARI and Console-to-API paths | Deployment prerequisite |
| WSO2 secret facility | Secret Manager / Secret Resolve Manager APIs available in target product | Exact bundle/API version to verify |
| WSO2 organization context | Organization-aware management and runtime resolution | Verify in target APIs |
| SMS provider | Existing WSO2 SMS configuration operational | Environment prerequisite |

## 2. Source baselines found during initial inspection

These are investigation baselines, not a complete or final dependency lock:

| Repository/component | Baseline inspected | Intended use | Remaining verification |
|---|---:|---|---|
| `identity-governance` | `1.15.9` | Notification channel and Password Recovery behavior | Confirm exact product dependency and patch surface |
| `identity-api-server` | `1.5.31` | Notification Sender API v2 | Confirm exact product dependency from IS 7.3.0 POM |
| `identity-outbound-auth-sms-otp` | `3.4.2` | Existing Login MFA SMS OTP flow | Confirm exact product bundle and API compatibility |
| `identity-event-handler-notification` | `1.12.13` inspected as a pattern | Config/Secret/Organization integration patterns | Resolve exact product version and exported APIs |
| `identity-apps` | Fork inspected; version not locked | Console UI | Resolve exact UI version from product POM/build metadata |

**Important:** An inspected tag is not proof that the same version is shipped in the target product. Before coding, produce a dependency lock table from the actual WSO2 IS 7.3.0 distribution POMs and installed bundle manifests. Do not use the conflicting UI version references found during preliminary notes as authoritative.

## 3. Required source repositories

| Repository | Planned change |
|---|---|
| `hsaeidh/wso2-asterisk-voice-otp-Notification-Channels` | New Voice modules and integration |
| `hsaeidh/identity-apps` | Voice Provider Console card, forms, API client and routes |
| WSO2 `identity-api-server` fork | Extend Notification Sender API v2 if no supported extension point exists |
| WSO2 `identity-governance` fork | Register `VOICE` and integrate Password Recovery |
| WSO2 `identity-outbound-auth-sms-otp` fork | Preserve the SMS OTP challenge while coordinating Voice fallback |

Forks/patches are conditional on extension-point verification. Avoid forking a component if a supported public OSGi/API extension can satisfy the requirement without unsafe reflection or replacement of internal services.

## 4. Product version verification checklist

Before implementation, capture evidence for each row:

- [ ] Product IS 7.3.0 root POM and dependency management values.
- [ ] Exact governance and recovery artifact versions.
- [ ] Exact Notification Sender API v2 artifact version.
- [ ] Exact SMS OTP authenticator artifact version.
- [ ] Exact notification event-handler/config/secret artifact versions.
- [ ] Exact Console UI artifact/source version.
- [ ] Java compiler release and OSGi execution environment.
- [ ] Exported/imported package versions and service component dependencies.
- [ ] Actual installed JAR names and manifests from a clean IS 7.3.0 installation.

Record the resolved versions in `docs/version-lock.md` before adding implementation dependencies.

## 5. Asterisk prerequisites

- ARI enabled in `ari.conf`, protected with a dedicated service account.
- TLS enabled; certificate chain trusted by the WSO2 JVM.
- Dedicated ARI application/Stasis application.
- Dedicated outbound trunk and dialplan context.
- Outbound caller ID authorized by the carrier.
- Dialplan restricted to the intended OTP call path.
- Persian digit audio prompts installed and verified.
- Asterisk event/log configuration reviewed for sensitive argument leakage.
- Firewall allows only the required WSO2-to-ARI traffic.
- Clock synchronization enabled on WSO2 and Asterisk hosts.

## 6. Environment values to collect (never commit secrets)

- WSO2 base URL and tenant/organization deployment model.
- Asterisk ARI URL, TLS trust chain, ARI application name.
- Outbound endpoint/trunk identifier.
- Dialplan context and extension.
- Permitted caller ID.
- Ring timeout and maximum call duration.
- Language code and audio prompt package/version.
- SMS provider capabilities, especially synchronous errors and delivery receipts.
- HA topology and persistence technology available to the product.

Never commit actual host credentials, ARI passwords, tokens, private keys, customer phone numbers or production configuration exports.
