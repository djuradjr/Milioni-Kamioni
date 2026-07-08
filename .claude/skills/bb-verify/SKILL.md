---
name: bb-verify
description: Step-by-step emulator verification runbook for Block Brainrot. Use whenever a feature must be proven working — after any build/install, before declaring anything done, for blocking features (3/3 bar) and for usage-stats accuracy checks.
---

# Block Brainrot — verification runbook

Follow the steps IN ORDER. Skipping step 4 is the #1 cause of false "it doesn't
work" conclusions. Never declare a feature done if any step was skipped — say
which step was skipped instead.

## 1. Boot the emulator
`emulator -avd Pixel_9 -gpu host -no-snapshot-load` (run in background), then
poll `adb shell getprop sys.boot_completed` until it prints `1`.

## 2. Build
`export JAVA_HOME="$HOME/.jdks/openjdk-22.0.2"` then
`./gradlew :app:assembleDebug`.

## 3. Install
`adb install -r` with a **Windows-style local path** (`C:\...\app-debug.apk`).
`MSYS_NO_PATHCONV=1` is only for device-side paths — it breaks local ones.

## 4. ⚠️ Re-enable the accessibility service (EVERY reinstall disables it)
```
adb shell settings put secure enabled_accessibility_services com.djuki.blockbrainrot.debug/com.example.stayfree.service.StayFreeAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.djuki.blockbrainrot.debug SYSTEM_ALERT_WINDOW allow
```
Poll `adb shell dumpsys accessibility | grep 'Block Brainrot Screen Monitor'`
until bound (a few seconds; re-set `accessibility_enabled 1` while waiting).
⚠️ Zombie binding: `Crashed services` non-empty, or "bound" under the app label
("Block Brainrot Dev") instead of the a11y label = events are NOT delivered.
Reset: `settings put secure enabled_accessibility_services none` → force-stop
the app → re-run step 4.

## 5. The 3/3 protocol (blocking features)
Enter the target surface → block fires → clear the target app from recents →
repeat. **3 consecutive passes**; any failure resets the count to 0. Confirm
each fire via `adb logcat -s MoreMoneyA11y` (`Content surface: <Name>`).

## 6. Usage-stats features
Ground truth is `adb shell dumpsys usagestats`, NOT the Digital Wellbeing UI
(its dashboard lags by minutes and will fail a fresh comparison for reasons
that aren't your bug). Protocol: controlled usage of 2–3 apps for a known
duration → compare app's numbers against dumpsys (±5%) → 3/3. Avoid
split-screen during comparison runs (overlapping foreground inflates totals
by design, same as Digital Wellbeing).

## 7. Seeing what the service sees
`uiautomator dump` fails on autoplay video ("could not get idle state") — IG
feed/Reels, TikTok, Shorts. Use `screencap` (always works; multiply displayed
coords by ≈1.21 for device px) or a temporary `Log.d` tree-walk in
`handleContentBlock` (viewIdResourceName + bounds). Remove the logger before
committing.

## 8. Report honestly
State exactly what was exercised and what wasn't. "Build green" alone is NOT
verification.
