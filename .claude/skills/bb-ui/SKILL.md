---
name: bb-ui
description: UI design system and the user's visual preferences for Block Brainrot. Use whenever creating or changing screens, layouts, colors, animations, or any user-facing surface in this repo.
---

# Block Brainrot — UI

Goal: every screen must look **worth paying for**. The user likes **interactive
and professional** interfaces — alive, but never gimmicky.

## Stack (fixed)
XML layouts + ViewBinding + Material3, DayNight theme. NO Compose, no new UI
frameworks. **Dark is the default** (`AppPreferences.appearanceMode` → `dark`);
the user can switch in Settings → Appearance.

## Skor design system (chosen 2026-09-12)
Replaces the retired orange-gradient "Ink/Puls" dashboard. Full pitch and all
15 screen mockups: https://claude.ai/code/artifact/ba2e1c37-ee17-4af5-aadf-101452e7f066

**Tokens** — `skor_*` in `values/colors.xml` (light) and `values-night/colors.xml`
(dark). Dark is the designed original; light is a *translation*: teal, amber and
orange are darkened there because the dark hues fall under 4.5:1 on white.

| token | means |
|---|---|
| `skor_ground` `skor_card` `skor_card_raised` `skor_line` `skor_nav` | surfaces |
| `skor_focus` (teal) | **the only action colour** — buttons, active tab, success |
| `skor_distraction` (orange) | **distraction and blocking ONLY** |
| `skor_night` (lilac) | Sleep mode, quiet hours |
| `skor_warn` (amber) | a limit is approaching |
| `skor_idle` | neutral time, empty tracks |

**The colour rule that matters:** orange is no longer brand chrome. It appears
only where something is a distraction or is being blocked. No ordinary button,
no active tab, no heading is orange. Brand orange lives on the launcher icon and
the Play listing, not in the UI.

**Type** — Archivo (`res/font/archivo.xml`, instanced from the variable font and
subset to latin, 208 KB for 4 weights). Scale is `values/type.xml`: `Skor.Display`
(the score) · `Skor.Metric` · `Skor.MetricSmall` · `Skor.Title` · `Skor.CardTitle`
· `Skor.Body` · `Skor.Caption` · `Skor.Label` · `Skor.Micro` · `Skor.Chip`.
Anything with changing digits sets `tnum`.

**Geometry** — card radius 14dp, button 12dp, chip/pill 999dp. Spacing scale
4 / 8 / 12 / 16 only. Cards are flat: separation comes from fill, not elevation.

**Components** — `ui/common/ScoreRingView` (animated score ring; draws only the
ring so the number can use CountUp), `ui/common/HourlyBarsView` (24 two-colour
bars, drag selects an hour), `ui/common/CountUp` (animated numbers).

## The score
`domain/score/FocusScore` — 100 minus 0.30/min on DISTRACTION apps, 0.50/min once
the daily goal is blown, 0.30 per unlock above the user's own average (cap 15),
plus 0.50 per content block fired (cap 15). Local and deterministic.
`domain/score/AppCategory` seeds DISTRACTION/NEUTRAL/PRODUCTIVE; unknown apps are
NEUTRAL so nobody is punished for a guess. Band colours: ≥80 teal, ≥55 amber,
below that orange.

## Interactive (make it feel alive)
- Animate numbers and charts; animate state transitions; ripple every tappable.
- Exception: block-screen entries use 0-duration transitions — they must appear
  instantly (`overridePendingTransition(0, 0)`).
- Empty, loading and first-day states are designed. **The score shows a dash,
  never 0** — a zero on a day with no data reads as "you failed".

## Professional (polish rules)
- One spacing scale, one type scale, across all three tabs.
- Block screens (`ContentBlockActivity`, `BlockOverlayActivity`): immersive
  full-screen, calm copy — never dark-pattern pressure (no fake timers, no guilt).
- All copy through `strings.xml` **and** `values-sr/strings.xml`.

## Verification (before "done")
Build, install, re-enable the a11y service (root CLAUDE.md gotcha #1), then
screencap on the Pixel_9 emulator (`MSYS_NO_PATHCONV=1` for device paths;
displayed coords ×≈1.21). Check the changed screen **in both themes** and
confirm all three tabs still render.

## Legacy being removed
`dash_*` and `glass_*` belong to the old orange dashboard and survive only where
a screen has not been migrated yet. Never use them in new work; delete each one
once its last reference goes.
