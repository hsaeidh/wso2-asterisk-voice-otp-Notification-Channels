# WSO2 Asterisk Voice OTP Notification Channel

Add a first-class **Voice Notification Channel** to WSO2 Identity Server 7.3.0 using Asterisk ARI for outbound calls and Asterisk `SayDigits` for OTP playback.

> **Project status:** Architecture and requirements documentation has been committed. Backend, WSO2 integration, Console UI and deployment code have not yet been implemented.

## Product decisions

- Target: WSO2 Identity Server 7.3.0, Java 21.
- Channel identifier: `VOICE`.
- Asterisk connectivity: direct ARI over TLS; WSO2 does not connect to Asterisk using SIP.
- OTP authority: WSO2 generates, validates and expires OTP challenges.
- Voice playback: Asterisk `SayDigits` only.
- SMS: reuse the existing WSO2 SMS provider.
- Fallback: SMS ↔ Voice, preserving the same OTP and same WSO2 challenge.
- User journeys: Password Recovery and Login MFA, using separate WSO2 adapters.
- Provider configuration: organization-scoped; secrets are write-only and stored through the supported WSO2 secret facility.

## Documentation

| Document | Contents |
|---|---|
| [Scope and requirements](docs/01-scope-and-requirements.md) | Goals, functional/non-functional requirements and acceptance gates |
| [Architecture](docs/02-architecture.md) | Components, trust boundaries, interfaces and ARI call flow |
| [Prerequisites and version matrix](docs/03-prerequisites-and-version-matrix.md) | Platform prerequisites, source baselines and version verification |
| [Provider configuration and REST API](docs/04-provider-configuration-and-rest-api.md) | Provider model, secret rules, API and Console contract |
| [Delivery and fallback](docs/05-delivery-fallback-and-state-machine.md) | Same-challenge invariant, state machine, idempotency and HA |
| [Security and privacy](docs/06-security-and-privacy.md) | OTP protection, ARI security, SSRF, organization isolation and toll fraud |
| [Implementation roadmap](docs/07-implementation-roadmap.md) | Phases and planned changes to dependent repositories |
| [Testing and operations](docs/08-testing-and-operations.md) | Test strategy, deployment, rollback and monitoring |
| [Acceptance criteria](docs/09-acceptance-criteria.md) | Release gates |
| [Open decisions and risks](docs/10-open-decisions-and-risks.md) | Unverified assumptions, risks and decision log |
| [Version lock](docs/version-lock.md) | Product-resolved WSO2 7.3.0 component versions |
| [Source validation review](docs/11-source-validation-review.md) | Source-backed review findings and corrections |
| [File-level WSO2 patch plan](docs/12-file-level-patch-plan.md) | Exact source paths, OSGi constraints, event integration and implementation order |

## Critical design invariants

1. Fallback must never generate a second OTP or a second independent challenge.
2. An answered call is not a successful delivery; playback completion must be established.
3. OTP must not be persisted or written to logs, ARI metadata, channel names or events.
4. Provider secrets must never be returned by read APIs.
5. Organization authorization is enforced by the backend, not merely by Console visibility.
6. HA/restart recovery must not create duplicate calls or silently replace the challenge.
7. Persian digit prompts must be verified on the actual Asterisk version and carrier path.

## Version status

Product-resolved versions are recorded in [version-lock.md](docs/version-lock.md) and [the prerequisites matrix](docs/03-prerequisites-and-version-matrix.md), based on the official WSO2 IS 7.3.0 POM and distribution inventory. Installed bundle manifests must still be checked in the actual deployment before build/release.

## Implementation status

The repository currently contains design documentation only. No Maven modules, Java implementation, WSO2 product patches, Console UI changes or built artifacts are claimed.

## License

Apache License 2.0.
