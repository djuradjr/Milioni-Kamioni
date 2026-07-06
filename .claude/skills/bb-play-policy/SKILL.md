---
name: bb-play-policy
description: Google Play compliance for Block Brainrot. Use before any release/submission, when touching the manifest or permissions, when writing store-listing or declaration text, and whenever Data Safety consistency is in question. An a11y-based blocker lives or dies on this.
---

# Block Brainrot — Play policy

The app uses an AccessibilityService + PACKAGE_USAGE_STATS — two of the most
scrutinized capabilities on Play. Rejection is a bigger business risk than any
bug. Check every item before submit.

## 1. Listing & audience
- Category: **Tools** (or Health & Fitness — Digital Wellbeing adjacent).
  **NEVER Parenting/Families** — that triggers the far stricter Families review
  pipeline. This is a self-blocking tool for the device owner, not child
  monitoring; confirm in Play Console → Target audience that children are NOT
  targeted.

## 2. Accessibility API declaration form (biggest rejection risk)
- Declared use-case must match actual behavior EXACTLY. Declare as app
  blocking / content filtering for **user-selected** apps, stating explicitly:
  only `viewIdResourceName`, package names and bounds are read — never screen
  text or content; the user configures every target; nothing is hidden or
  silent monitoring.
- Keep the exact submitted wording as a checked-in artifact in `docs/` so it is
  diffable release over release — vague or drifted wording is a common
  rejection reason.

## 3. Prominent disclosure
- An in-app disclosure screen must appear BEFORE requesting the a11y permission
  and its copy must match the console declaration (neither underselling nor
  overselling). The acceptance flag exists:
  `AppPreferences.ACCESSIBILITY_DISCLOSURE_ACCEPTED` — verify the shown copy
  whenever the declaration text changes.

## 4. Data Safety consistency
- Form says **"no data collected"** — true while the manifest has no `INTERNET`
  permission. Any egress (analytics, crash reporting, anything) requires
  updating the form in the SAME release. Play Billing itself needs no Data
  Safety change (Google processes that data).

## 5. Device and Network Abuse boundary
- Allowed: reading node info + the single documented Back press before showing
  our own gate/overlay.
- Danger zone: injecting any other input/gestures into target apps,
  auto-clicking, interfering with other apps beyond the documented block.

## 6. Self-defense boundary (what keeps us un-banned)
- Allowed: detecting that protection is off (a11y disabled, service dead) and
  nudging the user (notification/banner); graceful permission re-request;
  re-querying entitlements on launch.
- Ban-risk — never do: fighting force-stop, auto-re-enabling the a11y service,
  blocking access to Settings, hiding from recents to resist disabling.
  Force-stop killing the service is an accepted, documented limitation.

## 7. Pre-submit checklist
Signed `.aab` (`bundleRelease` + `keystore.properties`), R8-minified build
tested on a real device, `versionCode` bumped, privacy policy URL live
(`https://djuradjr.github.io/Milioni-Kamioni/privacy.html`), PACKAGE_USAGE_STATS
declaration filled, a11y declaration text matches the in-app disclosure, Data
Safety still truthful, store screenshots current.
