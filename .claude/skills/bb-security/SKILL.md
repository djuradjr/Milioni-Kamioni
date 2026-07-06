---
name: bb-security
description: Security review for Block Brainrot. Use before every release, after touching the accessibility service, PIN handling, DataStore, the manifest, and MANDATORY for any payment/billing/subscription code. Covers data-leak prevention, paywall integrity and the full future-payments checklist.
---

# Block Brainrot — security review

The app's core promise is **"no data leaves the device"**. Any finding that breaks
that promise is CRITICAL. Report findings ranked, each with a concrete exploit or
leak scenario.

## 1. Accessibility service (biggest privacy surface)
The service can read the screen content of every app on the phone. Verify:
- No screen text or node content is persisted, logged or transmitted. Only
  resource-ids, package names and bounds may be inspected. A `Log.d` of node
  content is allowed only as a temporary debug aid and must be gone before commit.
- Tree walks are bounded (depth/time) — a huge or malicious view tree must not
  ANR or wedge the service.

## 2. Network & data egress (offline by design)
- The manifest must NOT gain `INTERNET`; no network libraries may enter the dep
  tree (`./gradlew :app:dependencies` when in doubt). Play Data Safety declares
  "no data collected" — any egress is a Play policy violation, not just a bug.
- `allowBackup=false` stays. If backup is ever enabled, `backup_rules` /
  `data_extraction_rules` must exclude DataStore (PIN hash, unlock state) and
  the Room DB (usage history).

## 3. Component exposure
- Exported surface is exactly: `MainActivity` (launcher), the a11y service
  (protected by `BIND_ACCESSIBILITY_SERVICE`), `BootReceiver` (protected system
  broadcasts). Every new component defaults to `exported="false"`.
- Gates (`RewardGateActivity`, `BlockOverlayActivity`, `ContentInterstitialActivity`)
  must NEVER be exported — an exported gate is a paywall/block bypass.

## 4. Credentials
- PIN uses `util/PinHasher` (salted PBKDF2, 200k iterations, constant-time
  verify). Never weaken parameters, never log PINs, keep the legacy-hash
  migration path. Any new PIN entry UI needs attempt rate-limiting.

## 5. Paywall / entitlement integrity (SaaS)
- Unlock state (`contentUnlockUntil`, daily cap) lives in plain DataStore —
  editable on rooted devices. Acceptable for the free tier; NOT acceptable as
  the only check for paid entitlements.
- Bypass tests to actually run: launch each gate directly via
  `adb shell am start`; kill/disable the a11y service; move the device clock
  forward/back across the unlock window; clear app data mid-unlock.

## 6. FUTURE: payments (run FULLY when any billing option lands — user's explicit rule)
When payment/subscription features are added, this section is mandatory, 3/3 each:
- **Official Google Play Billing only** — never custom card forms, never store
  payment data locally, no PII (emails, order ids, tokens) in logs or crash reports.
- **Leak tests**: full logcat scan during the entire purchase flow (no tokens/
  account data logged); confirm zero network egress from our own process (billing
  traffic belongs to the Play Store app); check nothing payment-related lands in
  DataStore/Room beyond an entitlement flag.
- **Entitlement handling**: acknowledge purchases via the Billing library,
  re-query entitlements on app start (never trust a cached local boolean alone),
  and test the revoked/refunded path — access must actually close.
- **Flows to exercise end-to-end**: purchase → cancel mid-flow → refund →
  restore on reinstall. Each 3/3.
- Update the Play **Data Safety form** in the SAME release that adds billing.
- Verify the whole purchase flow in a **minified release build** on a real device
  (R8 + Billing is a classic breakage point).
