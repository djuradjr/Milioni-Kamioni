---
name: bb-ui
description: UI design system and the user's visual preferences for Block Brainrot. Use whenever creating or changing screens, layouts, colors, animations, or any user-facing surface in this repo.
---

# Block Brainrot — UI

Goal: every screen must look **worth paying for**. The user explicitly likes
**interactive and professional** interfaces — alive, but never gimmicky.

## Stack (fixed)
XML layouts + ViewBinding + Material3 components, light-only theme. NO Compose,
no new UI frameworks.

## Palette & color psychology (`values/colors.xml` — intentional, don't fight it)
- **Orange `#F4511E`** (primary) — energy/urgency. The dashboard is full-bleed
  orange gradient (AppBlock style): `dash_bg_top → dash_bg_bottom`, glass cards
  (`dash_card` fill + `dash_card_stroke`), white chart bars, `dash_chart_peak`
  amber highlight.
- **Teal `#00897B`** (`accent_teal`) — calm/self-control: success states,
  positive stats, "time saved".
- **Ink `#1B2B4B`** (`ink`) — trust/focus: premium dark surfaces, night/sleep.
- **Aubergine `#3E2A56`** (`accent_purple`) — focus/sleep modes, achievements.
- Elements on gradients use the `glass_white*` colors — never solid gray.

## Interactive (make it feel alive)
- Animate numbers (`ui/common/CountUp`) and charts (`ui/common/BarChartView`);
  animate state transitions; ripple on every tappable surface.
- Exception: block-screen entries use 0-duration transitions — they must appear
  instantly (`overridePendingTransition(0, 0)`).
- Empty, loading and error states are designed, never blank screens.

## Professional (polish rules)
- Material3 spacing and typography scale consistent across all 4 tabs; nothing
  misaligned, no cramped layouts, generous whitespace.
- Block screens (`ContentBlockActivity`, `BlockOverlayActivity`): immersive
  full-screen, branded, calm copy — never dark-pattern pressure (no fake timers,
  no guilt copy).
- All copy through `strings.xml`; support both Serbian and English tone: short,
  direct, friendly.

## Verification (before "done")
Build, install, re-enable the a11y service (root CLAUDE.md gotcha #1), then
screencap on the Pixel_9 emulator (displayed coords ×≈1.21) and check the changed
screen AND all 4 tabs still render correctly.
