# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review Bright theme's color palette — the current colors aren't landing with the product owner; revisit.
- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.
- [ ] Big picture: Cases/tags filters feel too crowded — needs a design pass on layout, not just content.
- [ ] Settings rework introduces `FilledTonalButton` for its action rows, a style not used anywhere else in the app (existing vocabulary is `Button`/`OutlinedButton`/`TextButton`). Deliberately scoped to Settings for now — revisit whether it should spread app-wide or Settings should conform to the existing three-tier system instead.

## Settings

- [ ] About screen real content (currently wiring-only placeholder copy): version/privacy statement/licenses text, plus a privacy policy hosted on the SecondMonday Studios website and linked from the screen.
- [ ] Rate the App and Contact Us are placeholder rows (show a "coming soon" snackbar) — need real destinations once there's a Play Store listing and a support channel to point to.
- [ ] Developer Mode's hidden unlock (tap About's version row 7 times) reuses `AboutSection`'s default layout as the tap target, which may run a little under the 48dp accessibility minimum. Low priority since it's a deliberately undiscoverable power-user gesture, not a primary control, but worth a look if it ever grows more than one dev tool.

## Insights tab

- [ ] Needs an overall rework — several stat sections' squares (heatmap/rhythm) look visually similar to each other, undermining at-a-glance usefulness.
- [ ] Rhythm grid cells convey their count by shading alone — no content description or visible number, unlike the calendar heatmap's day-of-month numbers and the intensity squares' level numbers. Same color-only gap already deferred for the dot timeline; revisit alongside making either grid tappable. The share card's Rhythm mini-section is a faithful mini-copy of this same composable, so it inherits the identical gap — fix both together.
- [ ] Calendar heatmap: a visually large gap appears between the current month and prior months right when a new month has just begun.
- [ ] Rhythm grid: large gap between the legend and the grid squares under the Intense theme specifically.

## Widgets

- [ ] Single-case widget (spec §15, deferred from Phase 8): small widget bound to one Case — tap logs per its `logFlow`, shows icon + today's count, ongoing state supported (elapsed + Stop, matching the List widget's per-row treatment).
- [ ] Widget background renders black instead of its intended theme color/surface.
- [ ] Widget empty state uses black-and-white font instead of themed colors.
- [ ] Widget empty state: tapping it should open the app; currently doesn't.
- [ ] Widget: tapping a case row or the widget title doesn't open the app.

## App icon

- [ ] Dark spot visible on the circle/handle at larger icon sizes — the invisible lens ring fix addressed a related issue, but this artifact remains at bigger resolutions.

## Testing & tooling

- [ ] Review MANUAL_TEST_PLAN.md for steps that could be converted to automated (instrumented or unit) coverage instead of staying manual-only — the list has grown journey by journey per branch; worth a deliberate pass rather than assuming every entry still needs a human.
- [ ] Two cosmetic build-time warnings, neither worth chasing on its own: a "Kapt support in Moshi Kotlin Code Gen is deprecated" notice from `hiltJavaCompileDebug` even though Moshi is wired in via `ksp()` only (KSP-generated adapters work correctly; likely Moshi's processor warning whenever it's discoverable on any annotation-processing-adjacent classpath, regardless of which mechanism invokes it); and a K2 "`@ApplicationContext` applied to value parameter only" forward-compat notice (KT-73255) on `Notifier.kt`/`WidgetRefresher.kt`/`ComposeShareImageExporter.kt`. Worth a look if either ever escalates from warning to error on a future Kotlin/Hilt bump.

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
