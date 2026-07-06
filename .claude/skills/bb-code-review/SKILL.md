---
name: bb-code-review
description: Code review infrastructure for Block Brainrot. Use for every review of this repo's diffs or PRs, before merging any feature, and whenever asked to check code quality. Covers correctness, future crash-risk analysis, token economy and the mandatory verification bar.
---

# Block Brainrot — code review

Run this checklist over the diff. Report findings ranked by severity, each with a
concrete failure scenario. A feature passes review only if the build is green AND
the 3/3 verification bar was met.

## 1. Future crash-risk analysis (mandatory — user's explicit rule)
For every change ask: **"how does this crash in production in 6 months?"**
- `AccessibilityNodeInfo`: nodes can be null, recycled or stale mid-walk; target
  apps change their view trees with every update — never index children blindly,
  always null-guard and bound tree walks.
- API levels: minSdk 23 / targetSdk 35 — every API ≥24 call needs a
  `Build.VERSION` guard or an androidx equivalent.
- Process death: services and activities restart cold — durable state belongs in
  DataStore/Room, never in instance fields that "should" survive.
- R8/minify (release only): reflection, Hilt entry points, enums via `values()`
  — these crash ONLY in minified builds; any new library or reflective call means
  testing `assembleRelease` on a device.
- Coroutines: cancelled scope mid-write can leave state half-updated — one atomic
  DataStore `edit {}` per logical change; watch `lifecycleScope` vs service scope.
- ViewBinding in fragments: access after `onDestroyView` crashes; the
  `_binding = null` pattern is mandatory.
- OEM reality: aggressive battery killers stop the a11y/tracking services —
  never assume the service was alive to see an event.
- Time logic: day keys, reset times, unlock windows — check midnight rollover,
  DST and timezone changes.

## 2. Correctness
- Detection changes live ONLY in `domain/content/ContentSignatures.kt`; check new
  id signatures don't collide with existing target namespaces.
- DataStore is written by both the service and the UI — look for read-modify-write
  races outside `edit {}`.
- Blocking flow order matters: grace window → unlock check → Back → gate launch
  (see `StayFreeAccessibilityService.handleContentBlock`).

## 3. Token & code economy (user's explicit rule)
- No comments that narrate code — only non-obvious constraints earn a comment.
- No dead code added; flag dead code the diff touches
  (`ContentInterstitialActivity` is known-dead).
- No new dependencies without clear need — every lib is APK size + R8 risk.

## 4. Repo invariants
- No `INTERNET` permission, no network libs (offline by design; SaaS billing goes
  through Play Billing, not our own network code).
- `namespace com.example.stayfree` and `applicationId com.djuki.blockbrainrot`
  never change.
- Serbian commit message + `Co-Authored-By: Claude` trailer; NEVER push without
  explicit permission.

## 5. Verdict
Approve only with: `./gradlew :app:assembleDebug` green, behavior exercised
**3/3 in a row**, and the a11y service re-enabled after any reinstall
(root CLAUDE.md gotcha #1). If any of these was skipped, say so — never imply
verification that didn't happen.
