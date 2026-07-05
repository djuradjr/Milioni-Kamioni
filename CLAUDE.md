# CLAUDE.md — MoreMoney (screen-time tracker/blocker)

Guidance for AI agents working in this repo. Read this first.

## 1. Project & location (critical)
- **Repo lives at `C:\Users\djuki\IdeaProjects\MoreMoney`** — NOT `D:\MoreMoney` (that
  path may be the session cwd but is wrong/empty; never write there). The repo folder
  was renamed `StayFree` → `MoreMoney` on 2026-07-03; the old `IdeaProjects\StayFree`
  path no longer exists (only the folder name changed — the package/namespace/class
  names below are unaffected and intentionally keep `stayfree`).
- Git remote: `https://github.com/djuradjr/Milioni-Kamioni.git` (branch `main`).
  (GitHub username renamed `MentalyIll` → `djuradjr` on 2026-07-05; old owner URL
  no longer redirects. Pages/privacy URL is `https://djuradjr.github.io/Milioni-Kamioni/privacy.html`.)
- Public app name is **"Block Brainrot"** (rebranded from MoreMoney on 2026-07-06;
  repo folder + internal project name stay "MoreMoney"). Release `app_name = "Block Brainrot"`,
  debug `app_name = "Block Brainrot Dev"` (set via `resValue` in `app/build.gradle.kts`).
- Package rebrand is **partial by design**: `namespace = com.example.stayfree` stays
  (don't rename packages), but `applicationId = com.djuki.blockbrainrot`
  (debug variant: `com.djuki.blockbrainrot.debug`, installs alongside release).
  ⚠️ applicationId locked at first Play publish — do not change after upload.

## 2. Build & run
- Export JDK before every Gradle call: `export JAVA_HOME="$HOME/.jdks/openjdk-22.0.2"`
- Build: `./gradlew :app:assembleDebug` · release APK: `assembleRelease` · Play bundle: `bundleRelease` (→ `app/build/outputs/bundle/release/app-release.aab`).
- adb: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`
- Install with a **Windows-style path** (`C:\...\app-debug.apk`); `MSYS_NO_PATHCONV=1`
  breaks local paths — only set it for adb *remote* (device) paths.

## 3. ⚠️ Gotcha #1 — reinstall DISABLES the accessibility service
Every `adb install -r` turns the a11y service off (Android security). After EVERY reinstall re-run:
```
adb shell settings put secure enabled_accessibility_services com.djuki.blockbrainrot.debug/com.example.stayfree.service.StayFreeAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.djuki.blockbrainrot.debug SYSTEM_ALERT_WINDOW allow
```
Then poll `dumpsys accessibility | grep 'Block Brainrot Screen Monitor'` until bound (may take a few s; re-set `accessibility_enabled 1` while waiting). Without this, NOTHING blocks — the #1 cause of "it doesn't work".

## 4. Emulator testing
- AVD `Pixel_9`; boot: `emulator -avd Pixel_9 -gpu host -no-snapshot-load` (run in background), then wait `getprop sys.boot_completed == 1`.
- `uiautomator dump` **fails on autoplay video** ("could not get idle state") — IG feed/Reels, TikTok For You, YouTube Shorts. Workarounds: `screencap` (always works) or a **temporary in-service debug logger** (walk the tree in `handleContentBlock`, `Log.d` every visible node's `viewIdResourceName`+bounds) — this is the only reliable way to see obfuscated ids the service actually sees.
- Screenshots come back scaled; multiply displayed coords by the given factor (≈1.21) to get device px.
- Verify a block via logcat: `logcat -s MoreMoneyA11y` → look for `Content surface: <Name>`.

## 5. Content-blocking architecture (the heart of the app)
Flow: **`ContentSignatures.kt` → `StayFreeAccessibilityService.handleContentBlock` → `RewardGateActivity`**
- `domain/content/ContentSignatures.kt` — the SINGLE source of detection signatures. When detection breaks after an app update, **edit ONLY this file.**
- `domain/content/ContentBlockTarget.kt` — model. Key fields:
  - `blockMode` = `REWARD_UNLOCK` (all live targets) or `HARD_BLOCK` (legacy, unused).
  - `matchWholeApp` — block any foreground screen of the app, no tree walk (for TikTok: entirely short-form + obfuscated ids).
  - `pressBackBeforeBlock` — press Back to leave the surface first; **false** for whole-app apps with no safe screen behind the feed (TikTok — Back would exit the app).
- `service/StayFreeAccessibilityService.kt` — `handleContentBlock`: detect surface (visible + ≥60% screen height, OR whole-app) → past 4s on-open grace → if unlocked skip → (optional Back) → launch gate. **Arming logic was removed** (it stopped Reels/Shorts from ever firing since those apps restore straight into the feed on open).
- `ui/content/RewardGateActivity.kt` — the overlay Activity (branded, "Watch ad — unlock 3 min" + "Exit", **no timer**). `showAd()` is a STUB that grants immediately; real AdMob template is in comments.
- `ui/content/ContentInterstitialActivity.kt` — old black hard-block screen, **now dead code** (all targets are reward-unlock).

**4 targets verified working 3/3:** Instagram Reels (`clips_*`), Instagram Stories (`reel_viewer_*`), YouTube Shorts (`reel_watch_*`/`shorts`) — all id-match; TikTok (`com.zhiliaoapp.musically`) — whole-app.

## 6. Reward-unlock model
- **Global unlock**: one ad → 3 min free for ALL reward targets (single `contentUnlockUntil` in DataStore). User chose global over per-platform.
- Daily cap 5 (`RewardGateActivity.DAILY_CAP`), grace window `CONTENT_OPEN_GRACE_MS = 4s`.
- Unlock state + enabled-target set live in `data/local/preferences/AppPreferences.kt` (DataStore). Toggles per target in `ui/inapp/InAppBlockFragment` + `fragment_in_app_block.xml`.

## 7. Offline by design
No `INTERNET` permission (Data Safety = "no data collected", a big review advantage). Real AdMob (INTERNET + play-services-ads + UMP/GDPR consent + Data Safety change) is intentionally deferred to pre-release — the ad is a stub until then.

## 8. Working rules (from the user)
- **NEVER `git push` unless the user explicitly says "push" in that moment.** Committing locally is fine. (See memory `never-push-without-asking`.)
- **Test 3/3 in a row** (enter → gate fires → exit/clear → repeat) before declaring a feature works. This is the user's bar.
- Feature commit messages: **Serbian**, end with the `Co-Authored-By: Claude` trailer.
- Don't touch `D:\MoreMoney`.

## 9. Play / release state
- `keystore.properties` (repo root, gitignored) drives release signing; absent → release stays unsigned. See `docs/PLAY_RELEASE_CHECKLIST.md` + `docs/PRIVACY_POLICY.md` + `docs/MANUAL_TEST_SCRIPT.md`.
- `versionCode=1`, `versionName=1.0.0`; release build has R8 minify + shrink (test minified on a real device — R8 bugs only show there).
- **TODO before submit:** `PRIVACY_POLICY_URL` in `SettingsFragment.kt` is still `example.com`; the stub "Watch ad" label is a policy risk (relabel until real AdMob).

## 10. Known dead ends (don't re-investigate)
- **X / Twitter** (`com.twitter.android`): crashes on the x86_64 emulator — `UnsatisfiedLinkError: libyoga.so not found`. X's x86_64 split genuinely omits `libyoga.so` (confirmed by unzipping the split); Play serves the same broken build. It's X's bug — works on a real arm64 phone, not on this emulator.
- **Snapchat** (`com.snapchat.android`): opens fine but blocks emulator **login** (anti-bot). Test on a real device. Also the worst content-detection candidate (exposes ~nothing to a11y; not all short-form, so whole-app is wrong).
- **Legacy in-app back-kick** (`ui/inapp/InAppBlockViewModel` defaults): signatures like Twitter `explore` were too broad and kicked the user out of the WHOLE app (Twitter removed for this reason). Snapchat `spotlight` / Facebook `reels` remain and carry the same risk — they are opt-in (`isActive=false`) but a landmine.
