# MoreMoney — Google Play Release Checklist

## Pre-flight (code, already done in repo)

- [x] `applicationId = com.djuki.moremoney`, `versionName = 1.0.0`
- [x] `targetSdk = 35` (Play requirement as of mid-2026)
- [x] `allowBackup = false`, backup/data-extraction rules exclude everything
- [x] No INTERNET permission (strong Data Safety argument)
- [x] Account username/email are LOCAL-ONLY (DataStore, backup-excluded, never
  transmitted) — Data Safety stays "no data collected"; re-answer the form if
  any egress path is ever added
- [x] FGS type `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` description
- [x] Accessibility prominent disclosure dialog before enabling the service
- [x] Release minify + shrinkResources, signing config wired to `keystore.properties`

## Manual steps (one-time)

1. **Generate the upload keystore** (keep passwords in a password manager;
   losing this file means losing the ability to update the app unless you
   enroll in Play App Signing — do enroll, it's the default):

   ```
   keytool -genkeypair -keystore moremoney-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias moremoney
   ```

2. **Create `keystore.properties`** in the repo root (gitignored):

   ```
   storeFile=C:\\path\\to\\moremoney-release.jks
   storePassword=...
   keyAlias=moremoney
   keyPassword=...
   ```

3. **Regenerate legacy launcher webp icons**: Android Studio → right-click
   `res` → New → Image Asset → use `ic_launcher_foreground.xml` /
   `ic_launcher_background.xml` as sources, regenerate all mipmap densities.

4. **Host the privacy policy** (e.g. GitHub Pages from `docs/PRIVACY_POLICY.md`)
   and put the final URL into:
   - `SettingsFragment.PRIVACY_POLICY_URL`
   - Play Console → App content → Privacy policy

## Play Console

- [ ] Create developer account ($25 one-time)
- [ ] **New personal accounts: closed testing with at least 12 testers for 14
      continuous days is mandatory before production access**
- [ ] App content → Privacy policy URL
- [ ] App content → **Data safety form**: declare "no data collected, no data
      shared" (the app has no INTERNET permission); device-or-other-IDs: none
- [ ] App content → Content rating questionnaire (utility, no objectionable content)
- [ ] App content → **Accessibility API declaration**: explain the core
      functionality (app/website blocking chosen by the user), attach a
      screenshot of the in-app prominent-disclosure dialog and a short screen
      recording of the disclosure → settings flow
- [ ] App content → **Foreground service declaration** (`specialUse`): continuous
      on-device screen-time tracking that powers user-configured app blocking;
      a video showing the persistent notification helps
- [ ] App access → provide instructions for reviewers: which permissions to
      grant (usage access, accessibility, overlay) and how (onboarding walks
      through each), note that no account/login exists
- [ ] Store listing assets: 512×512 icon, 1024×500 feature graphic, ≥2 phone
      screenshots (dashboard, blocking screen, block overlay)

## Build the release artifact

```
.\gradlew.bat bundleRelease
# artifact: app\build\outputs\bundle\release\app-release.aab
```

Verify before upload:

- merged manifest contains `com.djuki.moremoney`, `allowBackup="false"`, no `INTERNET`
- install the minified APK (`assembleRelease`) on a device and re-run the core
  flow from `docs/MANUAL_TEST_SCRIPT.md` — R8 issues only appear in minified builds
