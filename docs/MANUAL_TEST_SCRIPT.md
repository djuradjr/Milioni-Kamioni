# MoreMoney — Manual Test Script

Run on a fresh install (uninstall first: `adb uninstall com.djuki.moremoney`).
Recommended devices: API 34/35 emulator + at least one API 31-33 device.

## 1. Fresh install & onboarding

- [ ] Launch → splash (white, orange glyph) → onboarding opens
- [ ] Usage Access step: Grant opens system settings; back → Next enabled
- [ ] Report → paywall. No other permission is asked before the paywall.
- [ ] After a purchase (or turning a block on with premium) → **Accessibility
      step with the animated guide; the prominent-disclosure dialog appears
      BEFORE system settings open**; "Not now" keeps you on the step; "Accept and
      continue" opens Accessibility settings
- [ ] No Overlay, Notifications or Battery step anywhere in onboarding
- [ ] Settings → Notifications switch shows OFF until the permission is granted;
      turning it on asks for it

## 2. Tracking

- [ ] Use 2-3 apps for a couple of minutes → dashboard shows screen time within ~1 min
- [ ] Lock + unlock device several times → Unlocks counter increases
- [ ] Dashboard list does NOT contain a "__device__" entry
- [ ] Reboot the device → notification returns, tracking continues

## 3. Blocking — each type

- [ ] **BLOCK_NOW**: add rule for an app → open the app → orange gradient
      overlay appears; press home, reopen the app quickly → overlay reappears
      every time (no debounce escape)
- [ ] **SCHEDULED**: rule with a window covering "now" blocks; window in the
      past does not
- [ ] **DAILY_LIMIT**: set a 1-minute limit on an app, use it past the limit →
      blocked; force-stop MoreMoney, reopen target app → still blocked
      (limit comes from the database, not in-memory state)
- [ ] **SESSION**: set 1-minute session limit → blocked after 1 continuous minute
- [ ] **Sleep mode**: set a window covering "now" → any normal app is blocked,
      but the **launcher, SystemUI, dialer and Settings still work**
- [ ] **Focus mode**: start 25 min focus → other apps blocked, home screen still
      usable; **kill the MoreMoney process** (adb shell am force-stop) → reopen
      a blocked app → focus block still enforced (persisted state)
- [ ] Website block (Chrome): add a domain → navigate to it → overlay appears

## 4. PIN flows

- [ ] No PIN set: block overlay shows NO Override button, shows the
      "set a PIN in Settings" hint instead
- [ ] Set PIN in Settings (4+ digits, confirm flow)
- [ ] Overlay now shows Override; wrong PIN → "Incorrect PIN"; correct PIN →
      overlay closes
- [ ] Empty PIN input → rejected

## 5. Daily reset boundary

- [ ] Set reset time to a couple of minutes from now → after it passes, today's
      usage counters start from zero (within the next 15-min sync)

## 6. Light-only theme

- [ ] Enable system dark mode → app stays orange/white everywhere
      (dashboard, blocking, settings, overlay, dialogs)

## 7. Minified release pass

```
.\gradlew.bat assembleRelease
adb install app\build\outputs\apk\release\app-release.apk
```

- [ ] Repeat sections 1-4 on the minified build (R8/proguard issues only
      surface here)

## 8. Artifact sanity

- [ ] `aapt dump badging app-release.apk` (or Studio APK Analyzer):
      package `com.djuki.moremoney`, no INTERNET permission,
      `allowBackup="false"` in the merged manifest
