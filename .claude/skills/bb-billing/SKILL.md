---
name: bb-billing
description: Google Play Billing implementation runbook for Block Brainrot. Use when implementing payments, subscriptions, premium entitlements, or a paywall. Pairs with bb-security section 6 (mandatory) and bb-play-policy.
---

# Block Brainrot — Play Billing runbook

Context: paid SaaS, app is offline by design (no `INTERNET` permission — the
Billing library talks through the Play Store app, so this holds). The old
timed-unlock model (3-min grace, daily cap) was dropped on 2026-07-07 in favor
of a hard-block overlay; billing gates premium features — do NOT build
entitlements on top of `contentUnlockUntil`/`DAILY_CAP` semantics.

## 1. BillingClient lifecycle
- Single client instance; connect lazily, retry with backoff on
  `onBillingServiceDisconnected`; end connection when done.
- Query `ProductDetails` before showing any price — never hardcode prices.
- Handle `PENDING` purchases (cash/slow payment methods): grant nothing until
  `PURCHASED`; surface a "pending" state in UI.

## 2. ⚠️ Acknowledgment — the classic money-loser
Every purchase must be **acknowledged within 3 days or Google auto-refunds
it**. Acknowledge immediately after granting the entitlement (in
`onPurchasesUpdated` and in the app-start re-query, since the callback may
never fire if the app died mid-purchase).

## 3. Entitlement model
- Cache the entitlement in DataStore for offline use, but **re-query
  `queryPurchasesAsync` on every app start** — the cached flag alone is never
  the source of truth (rooted devices can edit DataStore; bb-security §5).
- Handle revoked/refunded: when the re-query no longer returns the purchase,
  close premium access — actually verify features lock again.
- Never gate entitlements on wall-clock windows — the device clock is
  user-editable (known weakness of the old unlock model).

## 4. UX (per bb-ui)
Paywall branded, immersive, professional — clear price from `ProductDetails`,
what's included, restore-purchases action. No dark patterns: no fake urgency,
no pre-selected upsells, cancel path obvious.

## 5. Testing
- License-tester accounts + internal testing track (real Play flow, no charge).
- Flows to exercise end-to-end, **3/3 each**: purchase → cancel mid-flow →
  refund (via Play Console) → restore on reinstall → pending→purchased.
- Test the entire flow in a **minified release build on a real device** — R8 +
  Billing is a classic breakage point (keep rules for the Billing library).
- Then run **bb-security §6 in full** (leak tests: logcat scan during purchase,
  zero egress from our process, no payment data persisted). MANDATORY before
  declaring billing done.

## 6. Release coupling
Billing lands together with: relabeling the stub gate button (Play policy
risk), bb-play-policy full pass, and a Data Safety review (no form change
expected for Billing itself, but verify).
