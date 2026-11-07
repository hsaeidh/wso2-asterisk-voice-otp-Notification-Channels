# WSO2 IS 7.3.0 Version Lock

**Source of truth:** official `wso2/product-is` tag `v7.3.0`, root `pom.xml` and distribution `LICENSE.txt`.  
**Reviewed:** 2026-11-07.

| Component | Maven/product version | Evidence |
|---|---:|---|
| Product | 7.3.0 | `identity-server-parent` root POM |
| Java source/target | 21 | root POM maven-compiler-plugin |
| Identity Governance / Recovery | 1.15.9 | `identity.governance.version`; `org.wso2.carbon.identity.recovery_1.15.9.jar` |
| Identity Server API | 1.5.31 | `identity.server.api.version`; Notification Sender v2 bundle inventory |
| Notification Event Handler / sender tenant config | 1.12.13 | `identity.event.handler.notification.version`; `org.wso2.carbon.identity.notification.sender.tenant.config_1.12.13.jar` |
| SMS OTP Authenticator | 3.4.2 | `org.wso2.carbon.extension.identity.authenticator.smsotp.connector-3.4.2.jar` |
| Console UI | 3.3.29 | `identity.apps.console.version` |
| Identity Apps Core | 4.0.15 | `identity.apps.core.version`; `org.wso2.identity.apps.common_4.0.15.jar` |

## Build rules

1. Compile and test with JDK 21.
2. Use the exact WSO2 dependency versions above; do not silently upgrade a single bundle to a newer tag.
3. Confirm installed JAR names, bundle symbolic names, exported package versions and OSGi imports against the actual IS 7.3.0 distribution before finalizing Maven dependencies.
4. Treat SMSOTP 3.4.2 as an installed product extension in this target, not as a generic assumption about every WSO2 IS deployment.
5. Match Console source to 3.3.29; Apps Core 4.0.15 is a different artifact/version and must not be substituted for the Console UI version.

## Sources

- [Product POM v7.3.0](https://github.com/wso2/product-is/blob/v7.3.0/pom.xml)
- [Distribution license and bundle inventory](https://github.com/wso2/product-is/blob/v7.3.0/LICENSE.txt)
- [Identity Governance v1.15.9](https://github.com/wso2-extensions/identity-governance/tree/v1.15.9)
- [Identity API Server v1.5.31](https://github.com/wso2/identity-api-server/tree/v1.5.31)
- [Notification Event Handler v1.12.13](https://github.com/wso2-extensions/identity-event-handler-notification/tree/v1.12.13)
- [SMS OTP Authenticator v3.4.2](https://github.com/wso2-extensions/identity-outbound-auth-sms-otp/tree/v3.4.2)
