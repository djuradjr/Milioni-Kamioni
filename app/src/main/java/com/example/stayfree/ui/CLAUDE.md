# UI (design system) — read before touching any screen

Stack: XML layouts + ViewBinding + Material3, DayNight theme (Light default,
user-picked in Settings; dark palette in `values-night/colors.xml`, brand
surfaces identical in both modes). NO Compose.
Full design rules: `.claude/skills/bb-ui/SKILL.md`.

- The user wants the app to look **worth paying for**: interactive AND
  professional. Animate state changes (`ui/common/CountUp`, `ui/common/BarChartView`,
  ripples on tappables), but keep layouts clean, aligned, Material3-consistent.
- Palette (`values/colors.xml` — the psychology is intentional):
  - Orange `primary #F4511E` — energy; dashboard is full-bleed gradient
    (AppBlock style, `dash_*` colors) with glass cards (`glass_white` fills).
  - Teal `accent_teal #00897B` — calm/self-control: success, positive stats,
    "time saved".
  - Ink `ink #1B2B4B` — trust/premium: dark premium surfaces, night/sleep.
  - Aubergine `accent_purple #3E2A56` — focus/sleep modes, achievements.
- Block screens (`ui/content/ContentBlockActivity`, `ui/overlay/BlockOverlayActivity`)
  run immersive full-screen, branded, calm copy — no timer pressure.
- All copy through `strings.xml`, never hardcoded.
- Verify visually on the Pixel_9 emulator (screencap; displayed coords ×≈1.21)
  and check all 4 tabs still render before declaring any UI change done.
