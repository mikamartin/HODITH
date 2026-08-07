# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.
- [ ] Settings rework introduces `FilledTonalButton` for its action rows, a style not used anywhere else in the app (existing vocabulary is `Button`/`OutlinedButton`/`TextButton`). Bright now uses a flat label + chevron row instead (`ActionRow`'s `BrightActionRow` branch, resolved alongside the Bright theme redesign's Settings pass); Plain/Intense still use `FilledTonalButton`. Revisit whether Plain/Intense should adopt the flat-row look too, or whether Settings should conform to the existing three-tier button system instead.

## Settings

- [ ] About screen real content (currently wiring-only placeholder copy): version/privacy statement/licenses text, plus a privacy policy hosted on the SecondMonday Studios website and linked from the screen.
- [ ] Rate the App and Contact Us are placeholder rows (show a "coming soon" snackbar) — need real destinations once there's a Play Store listing and a support channel to point to.
- [ ] Developer Mode's hidden unlock (tap About's version row 7 times) reuses `AboutSection`'s default layout as the tap target, which may run a little under the 48dp accessibility minimum. Low priority since it's a deliberately undiscoverable power-user gesture, not a primary control, but worth a look if it ever grows more than one dev tool.

## Insights tab

- [ ] Needs an overall rework — several stat sections' squares (heatmap/rhythm) look visually similar to each other, undermining at-a-glance usefulness.
- [ ] Rhythm grid cells convey their count by shading alone — no content description or visible number, unlike the calendar heatmap's day-of-month numbers and the intensity squares' level numbers. Same color-only gap already deferred for the dot timeline; revisit alongside making either grid tappable. The share card's Rhythm mini-section is a faithful mini-copy of this same composable, so it inherits the identical gap — fix both together.
- [ ] Rhythm grid: large gap between the legend and the grid squares under the Intense theme specifically.

## App icon

- [ ] Dark spot visible on the circle/handle at larger icon sizes — the invisible lens ring fix addressed a related issue, but this artifact remains at bigger resolutions.

## Testing & tooling

- [ ] Two cosmetic build-time warnings, neither worth chasing on its own: a "Kapt support in Moshi Kotlin Code Gen is deprecated" notice from `hiltJavaCompileDebug` even though Moshi is wired in via `ksp()` only (KSP-generated adapters work correctly; likely Moshi's processor warning whenever it's discoverable on any annotation-processing-adjacent classpath, regardless of which mechanism invokes it); and a K2 "`@ApplicationContext` applied to value parameter only" forward-compat notice (KT-73255) on `Notifier.kt`/`WidgetRefresher.kt`/`ComposeShareImageExporter.kt`. Worth a look if either ever escalates from warning to error on a future Kotlin/Hilt bump.

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
