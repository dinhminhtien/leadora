---
name: mobile-ui
description: Design-system rules and a redesign workflow for the Leadora Flutter app (mobile/) — theme tokens, component polish, spacing/typography scale, empty/loading/error states, and how to verify visually on the emulator. Use when asked to improve/redesign the mobile UI ("làm lại UI", "cho đẹp hơn", "polish", "redesign"), build a new screen/widget, or touch anything under mobile/lib/core/theme or shared/widgets.
---

# Leadora mobile UI — design system & redesign workflow

The app is Flutter + Material 3, one shared theme for light/dark. Keep the UI
**one coherent system**: change tokens in the theme, not ad-hoc values in
screens. A screen that hardcodes colors/radii/paddings is a bug to fix.

## Where the system lives

| File | Owns |
|---|---|
| `core/theme/app_colors.dart` | brand seed (`0xFF2563EB` indigo) + semantic colors (success/warning/danger/info/neutral) |
| `core/theme/app_theme.dart` | the `ThemeData` — ColorScheme.fromSeed, component themes (AppBar, Card, Input, Buttons, Chip, SnackBar) |
| `shared/widgets/` | reusable widgets: `SectionCard`, `StatusChip`, `EmptyState`, `AsyncValueView`, `AppAvatar` |
| `shared/formatters.dart` | date/money/relative/enum formatting — never format inline |

## Non-negotiable rules

1. **Tokens over literals.** Pull color from `Theme.of(context).colorScheme`
   (or `AppColors` for domain status), radius/spacing from a small scale
   (4/8/12/16/20/24), text style from `theme.textTheme.*`. No raw `Color(0x…)`
   or magic paddings inside screens — if you need a new value, add it to the
   theme or a shared widget.
2. **Semantic color for state, scheme color for chrome.** Lead/booking/payment
   status → `AppColors` via `StatusChip`; surfaces/text/buttons → the generated
   scheme so dark mode and contrast stay correct.
3. **Every async surface has 3 states.** Loading, error (+retry), empty — use
   `AsyncValueView` + `EmptyState`. `skeletonizer` (already a dep) for skeletons
   beats a bare spinner on list/detail. Never leave a dead blank screen.
4. **Respect text scale & tap targets.** No fixed-height box that clips at large
   font scale (this bit the KPI cards — overflowed 5.5px). Min tap target 48dp;
   give long text `maxLines` + `TextOverflow.ellipsis`.
5. **Light AND dark must both look right.** Test both; don't hardcode a color
   that only works on one background.

## What "nicer" means here (polish checklist)

- **Hierarchy**: one clear primary action per screen (FilledButton); everything
  else lower-emphasis (tonal/outlined/text). Size/weight should encode importance.
- **Spacing rhythm**: consistent gaps from the 4/8 scale; group related rows,
  separate sections with whitespace not just dividers.
- **Depth**: this theme is intentionally flat (elevation 0 + hairline borders).
  Add depth with tonal surface steps (`surfaceContainerLow/High/Highest`) and
  the primary/secondary containers, not drop shadows.
- **Typography**: a real scale (display→headline→title→body→label). Consider a
  brand font via `google_fonts` (bundle the .ttf as an asset for offline/CSP
  safety rather than runtime-fetching). One display face + system body is plenty.
- **Motion**: cheap wins — `AnimatedSwitcher` on state changes, hero on
  list→detail avatar, implicit animations on chips/filters. Keep it <250ms.
- **Iconography & avatars**: consistent icon weight (rounded set), `AppAvatar`
  color derived from the name hash so it's stable and varied.
- **Empty states with a next action** (the lead list already does this — a CTA,
  not just "nothing here").

## Redesign workflow

1. Change the **theme/tokens first** (`app_theme.dart`, `app_colors.dart`) so a
   restyle propagates everywhere for free; then polish shared widgets; only then
   touch individual screens.
2. Keep behavior identical — this is a visual pass. Don't move state/logic.
3. `flutter analyze` clean, `flutter test` green (widget tests may assert
   structure).
4. **Verify visually on the emulator** (see the `run-stack` skill): launch,
   screenshot the changed screens in BOTH light and dark, compare before/after.
   A UI change isn't done until it's been looked at running.
5. Watch the run log for `RenderFlex overflowed` / layout exceptions — a clean
   screenshot can still hide an overflow banner in release-off debug paint.

## Gotchas specific to this app

- The emulator reaches the backend at `10.0.2.2:8085`; you need real data to
  judge lists — create a couple of leads first.
- `StatefulShellRoute` preserves each tab's state, so a restyle only shows after
  that branch rebuilds (hot restart, not hot reload, for theme changes).
- Config is compile-time (`--dart-define-from-file`); a theme change is Dart, so
  hot restart is enough — no rebuild needed.
