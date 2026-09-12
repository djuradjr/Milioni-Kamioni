# UI (design system) — read before touching any screen

Stack: XML layouts + ViewBinding + Material3, DayNight theme. NO Compose.
**Dark is the default** (`AppPreferences.appearanceMode` defaults to `dark`);
light is a translation of the dark design, not an inversion.
Full design rules: `.claude/skills/bb-ui/SKILL.md`.

## Skor design system (current)
Tokens are the `skor_*` colors — light in `values/colors.xml`, dark in
`values-night/colors.xml`. Type scale is `values/type.xml` (`Skor.*` styles),
font is **Archivo** (`res/font/archivo.xml`, subset to latin).

- `skor_ground` / `skor_card` / `skor_card_raised` / `skor_line` — surfaces
- `skor_focus` teal — **the only action colour**: buttons, active tab, success
- `skor_distraction` orange — **distraction and blocking ONLY**, never chrome.
  Brand orange survives on the launcher icon and the Play listing, not in the UI.
- `skor_night` lilac — Sleep mode · `skor_warn` amber — limit approaching
- `skor_idle` — neutral time, empty tracks

Components: `ui/common/ScoreRingView` (score ring, animated),
`ui/common/HourlyBarsView` (24 two-colour bars, drag selects an hour),
`ui/common/CountUp` (animated numbers).

- Interactive AND professional: animate state changes, ripple every tappable,
  but keep layouts clean and aligned.
- Empty/first-day states are designed: the score shows a dash, never 0 — a zero
  reads as "you failed" on a day with no data yet.
- Block screens (`ui/content/ContentBlockActivity`, `ui/overlay/BlockOverlayActivity`)
  run immersive full-screen, calm copy — no timer pressure, no guilt.
- All copy through `strings.xml` (EN + `values-sr`), never hardcoded.
- Verify on the Pixel_9 emulator (screencap; displayed coords ×≈1.21) in BOTH
  themes, and check all three tabs still render before declaring anything done.

## Legacy (being removed)
`dash_*` and `glass_*` colors belong to the retired orange-gradient dashboard.
They still exist because screens not yet migrated reference them. Do not use
them in new work; delete each one once its last screen is migrated.
