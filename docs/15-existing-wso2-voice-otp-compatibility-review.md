# Existing WSO2 Voice OTP Extension: Compatibility and Reuse Review

**Review date:** 2026-11-07  
**Target product:** WSO2 Identity Server 7.3.0  
**Status:** Source-discovery finding; no dependent repository has been modified.

## 1. Finding

WSO2 maintains an extension repository named
[`wso2-extensions/identity-outbound-auth-voice-otp`](https://github.com/wso2-extensions/identity-outbound-auth-voice-otp).
Its Jenkins project identifies the artifact as an outbound Voice OTP authenticator. The latest surfaced stable build is build #17, dated 2026-07-15, for release 1.0.3; the build page reports no test failures.

This is evidence that WSO2 already has a Voice OTP authentication extension. It is **not** evidence that WSO2 ships a first-class `VOICE` notification channel in Identity Server 7.3.0, that it exposes a Voice Provider management page under Notification Channels, or that it implements the required SMS↔Voice same-challenge fallback.

References:
- [WSO2 Voice OTP extension repository](https://github.com/wso2-extensions/identity-outbound-auth-voice-otp)
- [WSO2 Jenkins stable build #17](https://wso2.org/jenkins/job/identity-extensions/job/identity-outbound-auth-voice-otp/lastStableBuild/)

## 2. Product boundary

These are separate extension points and must not be conflated:

| Capability | Existing Voice OTP authenticator | This project |
|---|---|---|
| Voice-based login authentication | Existing extension is relevant | Integrate only if it can preserve the agreed OTP lifecycle |
| Password recovery voice delivery | Must be verified from its source and product integration | Required |
| First-class Notification Channel value `VOICE` | Not established by the build/repository evidence | Required |
| Voice Provider CRUD in Console → Notification Channels | Not established | Required |
| Provider secret lifecycle and Organization isolation | Must be verified | Required |
| Asterisk ARI provider with `SayDigits` | Must be verified from source | Required |
| SMS↔Voice fallback using the same OTP/challenge | Not established | Required |

## 3. Mandatory source review before integration

Before reusing or extending the existing authenticator, inspect its exact source tag compatible with IS 7.3.0 and establish:

1. Which OTP generator and challenge/token store it uses.
2. Whether it owns OTP generation or can consume a WSO2-generated OTP without generating another one.
3. Whether it invokes ARI, SIP, an external gateway, or another call provider.
4. Whether playback completion is observable and distinguished from call answer.
5. How it resolves the user's phone claim, tenant and Organization context.
6. How resend, expiry, rate limiting, account locking, retries and failure states work.
7. Whether its configuration is an IdP/authenticator configuration or a Notification Sender resource.
8. Whether its API and UI are exposed through the IS 7.3.0 Console.

Do not infer these behaviors from the repository name or Jenkins result. The build result validates that extension's build/tests, not this project's acceptance criteria.

## 4. Integration decision

The existing WSO2 extension must be treated as a candidate reusable authentication adapter, not as a substitute for Notification Channel registration and provider management.

The architecture remains:

- WSO2 owns OTP generation, validation, expiry and challenge state.
- The Voice delivery adapter receives the already-generated OTP transiently and never generates a second code.
- The provider-management service owns ARI configuration and credentials.
- Asterisk only originates the call and reads digits through `SayDigits`.
- SMS fallback reuses the same challenge and OTP.
- Password Recovery and Login MFA remain separate lifecycle adapters over the shared delivery coordinator.

If source review proves that the existing authenticator cannot satisfy the same-challenge invariant, do not wrap it in a way that creates a second OTP. Implement the smallest compatible adapter at the supported WSO2 extension point instead.

## 5. Scope and repository boundary

This review changes documentation in the dedicated Voice Notification Channel repository only. It does not modify the WSO2 upstream extension or any WSO2 product repository. Such changes require separate explicit authorization.
