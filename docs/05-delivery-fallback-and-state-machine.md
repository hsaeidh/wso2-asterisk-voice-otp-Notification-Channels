# Delivery, Fallback and State Machine

## 1. Fundamental invariant

One user operation corresponds to one WSO2 OTP challenge. SMS and Voice are alternate delivery attempts for that same challenge. The coordinator must not mint a new OTP during fallback.

## 2. Delivery request

Logical request fields:

- Opaque delivery ID
- Organization ID derived from WSO2 context
- Challenge reference
- Purpose: `PASSWORD_RECOVERY` or `LOGIN_MFA`
- Destination phone number
- Selected primary channel
- Fallback policy
- Challenge expiry
- Correlation ID
- Protected in-memory OTP handoff (never serialized or persisted)

The challenge reference is not a substitute for OTP validation. Every delivery attempt must re-check that the challenge is still valid and not expired.

## 3. State model

```text
CREATED
  -> PRIMARY_SUBMITTED
       -> (SMS) ACCEPTED / DELIVERY_CONFIRMED if provider supports DLR
       -> (VOICE) RINGING -> ANSWERED -> PLAYING -> COMPLETED
       -> FAILED / BUSY / NO_ANSWER / REJECTED / ARI_ERROR / PLAYBACK_FAILED
  -> FALLBACK_PENDING
  -> SECONDARY_SUBMITTED
  -> FINAL_SUCCESS | FINAL_FAILED | EXPIRED | CANCELLED
```

Do not mark Voice as successful at `ANSWERED`; require a trustworthy playback-completed result. Do not mark SMS as handset-delivered merely because the gateway accepted the request.

## 4. Fallback matrix

| Primary | Trigger | Secondary | Important limitation |
|---|---|---|---|
| SMS | Synchronous rejection or authoritative failure result | Voice | Gateway acceptance without DLR cannot prove handset delivery |
| Voice | Busy, no answer, rejected, ARI error, playback failure | SMS | SMS success level depends on provider response semantics |
| Either | Challenge expired/cancelled | None | Stop immediately |
| Either | Invalid destination/provider configuration | Policy-defined failure | Do not loop endlessly |
| Either | Unknown outcome/timeout | Reconcile before retry | Avoid duplicate delivery |

Fallback is bounded to one secondary attempt by default. Any retry count greater than one requires an explicit policy, rate limits and abuse analysis.

## 5. Idempotency and concurrency

Suggested idempotency identity:

`organizationId + challengeReference + purpose`

Use a cryptographically random delivery ID and enforce a unique constraint on the idempotency identity. Concurrent requests must converge on one logical delivery. A repeated request must return the existing delivery state or a deterministic conflict; it must not place another call.

The persistence implementation must support the WSO2 deployment's multi-node topology. In-memory locks alone are insufficient for HA.

## 6. Persistence

Persist only the minimum metadata needed for recovery and operations:

- Delivery ID and idempotency key (opaque or hashed where appropriate)
- Organization ID
- Challenge reference (opaque)
- Purpose
- Channel and provider reference
- State and timestamps
- Attempt count and sanitized error code
- ARI channel/call identifier if safe and necessary
- Expiry and next-action timestamp

Never persist the OTP. Avoid persisting full phone numbers; use encryption or masking according to operational need. Define retention and cleanup for terminal records.

Do not use the provider configuration store as a delivery queue. Select a supported transactional persistence mechanism after verifying what the target IS distribution supports. Document schema migration, HA locking/leases and cleanup.

## 7. Restart recovery

On startup, reconcile non-terminal deliveries:
- Expired challenge -> `EXPIRED`.
- Known terminal ARI outcome -> finalize.
- Active ARI channel -> reconcile with Asterisk before retry.
- Unknown outcome -> do not blindly originate a duplicate call.
- Lost in-memory OTP handoff -> fail safely or use a WSO2-supported protected challenge retrieval mechanism; never generate a replacement OTP implicitly.

A design that cannot safely recover the same OTP after node restart must explicitly document that in-flight attempt behavior and avoid duplicate or mismatched delivery.

## 8. Rate limits and abuse controls

- Per-user, per-destination, per-organization and per-IP throttles where relevant.
- Maximum calls per time window.
- Cooldown after repeated failed attempts.
- Challenge expiry and attempt limits remain authoritative in WSO2.
- Avoid exposing whether a user account exists through channel-specific errors.
- Protect test-call capability against abuse and toll fraud.

## 9. Delivery result semantics

Define a stable status API for internal use. Keep transport-specific status details behind adapters. Normalize provider errors into safe codes; retain sanitized diagnostic context only. Metrics must not use OTP, phone number, username or challenge secret as labels.
