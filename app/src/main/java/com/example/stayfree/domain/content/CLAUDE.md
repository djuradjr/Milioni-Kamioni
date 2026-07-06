# Content detection (domain/content) — read before touching detection

- `ContentSignatures.kt` is the SINGLE source of truth for detection signatures.
  When a target app updates and detection breaks, edit ONLY this file — never
  hardcode ids in the service.
- `ContentBlockTarget` knobs:
  - `matchWholeApp = true` → any foreground screen of the app triggers, no tree
    walk (TikTok: entirely short-form + obfuscated, version-unstable ids).
  - `pressBackBeforeBlock = false` for whole-app targets with no safe screen
    behind the feed (TikTok: Back just exits the app).
- Id namespaces never collide (verified on live dumps): IG Reels `clips_*`,
  IG Stories `reel_viewer_*` (Instagram's internal name for Stories is "reel"),
  YT Shorts `reel_watch_*`/`shorts`.
- Adding a target: capture real ids first — `uiautomator dump` fails on autoplay
  video, so use `screencap` or a temporary `Log.d` tree-walk in
  `handleContentBlock` (remove before commit). Then add the entry here and expose
  the toggle in `ui/inapp/InAppBlockFragment`.
- Verification bar (non-negotiable): **3/3 in a row** on the Pixel_9 emulator
  (enter surface → gate fires → clear from recents → repeat), confirmed via
  `logcat -s MoreMoneyA11y` (`Content surface: <Name>`). After EVERY reinstall
  re-enable the a11y service (root CLAUDE.md gotcha #1) or nothing fires.
