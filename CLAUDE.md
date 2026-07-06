# CLAUDE.md — Block Brainrot (screen-time tracker/blocker → paid SaaS)

Guidance for AI agents working in this repo. Read this first.
Scoped docs: `app/src/main/java/com/example/stayfree/domain/content/CLAUDE.md`
(detection) · `app/src/main/java/com/example/stayfree/ui/CLAUDE.md` (design system).
Project skills in `.claude/skills/`: **bb-code-review** (every diff review),
**bb-security** (a11y/PIN/manifest/billing changes), **bb-ui** (any screen work),
**bb-verify** (proving any feature works), **bb-play-policy** (before any release
or manifest/permission change), **bb-billing** (payments/premium work).

## 1. Project & location (critical)
- **Repo lives at `C:\Users\djuki\IdeaProjects\Block Brainrot main`** — folder history:
  `StayFree` → `MoreMoney` (2026-07-03) → `Block Brainrot main` (2026-07-06); the old
  paths no longer exist. Never write to `D:\MoreMoney`.
- Work on branch **`main`**. Remote: `https://github.com/djuradjr/Milioni-Kamioni.git`.
  Privacy URL (already wired in `SettingsFragment`):
  `https://djuradjr.github.io/Milioni-Kamioni/privacy.html`.
- Public app name **"Block Brainrot"** (release) / **"Block Brainrot Dev"** (debug),
  set via `resValue` in `app/build.gradle.kts`.
- Package rebrand is **partial by design**: `namespace = com.example.stayfree` stays
  (never rename packages); `applicationId = com.djuki.blockbrainrot` (debug adds
  `.debug`, installs alongside release). ⚠️ applicationId locks at first Play publish.

## 2. Product direction: paid SaaS (decided 2026-07-06)
- Block Brainrot is a **paid product** — NOT ad-supported. Do not add AdMob/rewarded
  ads; the AdMob template comments in `RewardGateActivity` are legacy reference only.
- **2026-07-07: the timed-unlock model is dropped** — detected content gets a plain
  hard-block overlay (no 3-min grace, no daily cap). Not yet implemented: the code
  still runs the reward-gate flow described in §7, but do NOT build anything new on
  the unlock model. Premium via Google Play Billing is still planned — see `bb-billing`.
- When billing lands, the `bb-security` skill's payments section is MANDATORY
  (paywall-bypass + data-leak tests, 3/3 each).

## 3. Build & run
- Export JDK before every Gradle call: `export JAVA_HOME="$HOME/.jdks/openjdk-22.0.2"`
- Build: `./gradlew :app:assembleDebug` · release APK: `assembleRelease` · Play bundle: `bundleRelease` (→ `app/build/outputs/bundle/release/app-release.aab`).
- adb: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`
- Install with a **Windows-style path** (`C:\...\app-debug.apk`); `MSYS_NO_PATHCONV=1`
  breaks local paths — only set it for adb *remote* (device) paths.

## 4. ⚠️ Gotcha #1 — reinstall DISABLES the accessibility service
Every `adb install -r` turns the a11y service off (Android security). After EVERY reinstall re-run:
```
adb shell settings put secure enabled_accessibility_services com.djuki.blockbrainrot.debug/com.example.stayfree.service.StayFreeAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.djuki.blockbrainrot.debug SYSTEM_ALERT_WINDOW allow
```
Then poll `dumpsys accessibility | grep 'Block Brainrot Screen Monitor'` until bound (may take a few s; re-set `accessibility_enabled 1` while waiting). Without this, NOTHING blocks — the #1 cause of "it doesn't work".

## 5. Emulator testing
- AVD `Pixel_9`; boot: `emulator -avd Pixel_9 -gpu host -no-snapshot-load` (run in background), then wait `getprop sys.boot_completed == 1`.
- `uiautomator dump` **fails on autoplay video** ("could not get idle state") — IG feed/Reels, TikTok For You, YouTube Shorts. Workarounds: `screencap` (always works) or a **temporary in-service debug logger** (walk the tree in `handleContentBlock`, `Log.d` every visible node's `viewIdResourceName`+bounds) — the only reliable way to see obfuscated ids the service actually sees. Remove the logger before committing.
- Screenshots come back scaled; multiply displayed coords by the given factor (≈1.21) to get device px.
- Verify a block via logcat: `logcat -s MoreMoneyA11y` → look for `Content surface: <Name>`.

## 6. Content-blocking architecture (the heart of the app)
Flow: **`ContentSignatures.kt` → `StayFreeAccessibilityService.handleContentBlock` → `RewardGateActivity`**
- `domain/content/ContentSignatures.kt` — the SINGLE source of detection signatures.
  When detection breaks after an app update, **edit ONLY this file** (details in the
  module CLAUDE.md next to it).
- `service/StayFreeAccessibilityService.kt` — `handleContentBlock`: detect surface
  (visible + ≥60% screen height, OR whole-app) → past 4s on-open grace → if unlocked
  skip → (optional Back) → launch gate. **Arming logic was removed** (it stopped
  Reels/Shorts from ever firing — those apps restore straight into the feed on open).
- `ui/content/RewardGateActivity.kt` — the gate Activity (branded, immersive, **no
  timer**). `ui/content/ContentInterstitialActivity.kt` — old black hard-block
  screen, **dead code** (all targets are reward-unlock).

**4 targets verified working 3/3:** Instagram Reels (`clips_*`), Instagram Stories
(`reel_viewer_*`), YouTube Shorts (`reel_watch_*`/`shorts`) — id-match; TikTok
(`com.zhiliaoapp.musically`) — whole-app.

## 7. Unlock model (current code — slated for removal, see §2)
- **Global unlock**: one gate pass → 3 min free for ALL reward targets (single
  `contentUnlockUntil` in DataStore). User chose global over per-platform.
- Daily cap 5 (`RewardGateActivity.DAILY_CAP`), grace window `CONTENT_OPEN_GRACE_MS = 4s`.
- Unlock state + enabled-target set live in `data/local/preferences/AppPreferences.kt`
  (DataStore). Per-target toggles in `ui/inapp/InAppBlockFragment` + `fragment_in_app_block.xml`.

## 8. Offline by design
No `INTERNET` permission (Data Safety = "no data collected", a big review advantage).
This stays true under SaaS: Google Play Billing talks through the Play Store app, so
keep the app itself networkless unless a future explicit decision changes that.

## 9. Working rules (from the user — non-negotiable)
- **NEVER `git push` unless the user explicitly says "push" in that moment.**
  Local commits are fine.
- **Token economy**: code quality unchanged, but no wasted tokens — no comments that
  narrate code (only non-obvious constraints), no dead code, docs proportional to
  the codebase.
- **Verify before "done"**: never declare a feature working without proof — build
  green + behavior exercised. Blocking features: **3/3 in a row** on the emulator.
- Feature commit messages in **Serbian**, ending with the `Co-Authored-By: Claude` trailer.
- Don't touch `D:\MoreMoney`.

## 10. Play / release state
- `keystore.properties` (repo root, gitignored) drives release signing; absent →
  release stays unsigned. See `docs/PLAY_RELEASE_CHECKLIST.md` + `docs/PRIVACY_POLICY.md`
  + `docs/MANUAL_TEST_SCRIPT.md`.
- `versionCode=1`, `versionName=1.0.0`; release has R8 minify + shrink — test minified
  builds on a real device (R8 bugs only show there).
- Privacy policy URL is DONE (GitHub Pages, wired in Settings). Remaining before
  submit: relabel the stub "Watch ad" button (policy risk) — resolves itself when the
  premium gate replaces it.

## 11. Known dead ends (don't re-investigate)
- **X / Twitter** (`com.twitter.android`): crashes on the x86_64 emulator — `UnsatisfiedLinkError: libyoga.so not found`. X's x86_64 split genuinely omits `libyoga.so` (confirmed by unzipping the split); Play serves the same broken build. It's X's bug — works on a real arm64 phone, not on this emulator.
- **Snapchat** (`com.snapchat.android`): opens fine but blocks emulator **login** (anti-bot). Test on a real device. Also the worst content-detection candidate (exposes ~nothing to a11y; not all short-form, so whole-app is wrong).
- **Legacy in-app back-kick** (`ui/inapp/InAppBlockViewModel` defaults): signatures like Twitter `explore` were too broad and kicked the user out of the WHOLE app (Twitter removed for this reason). Snapchat `spotlight` / Facebook `reels` remain and carry the same risk — they are opt-in (`isActive=false`) but a landmine.
