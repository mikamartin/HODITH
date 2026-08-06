# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.
- [ ] Big picture: Cases/tags filters feel too crowded — needs a design pass on layout, not just content.
- [ ] Settings rework introduces `FilledTonalButton` for its action rows, a style not used anywhere else in the app (existing vocabulary is `Button`/`OutlinedButton`/`TextButton`). Deliberately scoped to Settings for now — revisit whether it should spread app-wide or Settings should conform to the existing three-tier system instead.

## Bright theme redesign (Soft Glow)

Direction is settled — the coral/turquoise "Soft Glow" mockup at [docs/mockups/bright-theme-soft-glow.html](mockups/bright-theme-soft-glow.html) (Home, Big Picture, Case Detail, Edit Case, Settings) is the reference. Palette stays coral/turquoise, `Baloo 2` + `Nunito`, and the existing shape scale in `Type.kt`/`Shape.kt` are unchanged.

Foundations are in: `Color.kt`'s primary-tinted ink, a `LocalCardDecorationStyle` fork point (mirrors `LocalBigPictureCellStyle`/`LocalShareCardSkin`, covered by `CardDecorationStyleTest`), and the shared `GlowCard`/`IconHalo` primitives (`ui/theme/GlowDecoration.kt`, with their own Compose Previews). Nothing consumes the fork point yet, so Plain/Intense/current-Bright are all unchanged. What's left is wiring each screen's `BRIGHT` branch to the new primitives:

- [ ] Home (`HomeScreen.kt`'s `HomeCaseListItem`): branch on `LocalCardDecorationStyle`, wrap rows in `GlowCard`; promote the case name to `Baloo 2` (today only the header uses the display face — the list itself reads like Plain theme recolored); alternate `IconHalo` tint primary/secondary per row.
- [ ] Big Picture (`BigPictureGrid.kt`'s `BrightDayCell`): replace the shipped fanned-sticker-cluster treatment with `IconHalo` as a ring on today's cell. This is a real behavior change to existing code, not just filling in something new — flag it in review.
- [ ] Case Detail Insights (`InsightsTab.kt`): branch `InsightsCard` on `LocalCardDecorationStyle` to use `GlowCard`; frequency chart bars use a primary gradient fill; the two-up stat-tile layout (e.g. longest gap / most common day) doesn't exist as a component yet and needs building, not just restyling.
- [ ] Settings (`SettingsScreen.kt`'s `Plank`): branch on `LocalCardDecorationStyle` to use `GlowCard`. Resolve alongside the existing `FilledTonalButton` item below — both touch `ActionRow` in the same file.
- [ ] Edit Case (`CaseEditScreen.kt`): restyle text fields, the icon-picker grid, and toggle rows to match, using `GlowCard`/`IconHalo` where the mockup calls for them. `SegmentedChoiceRow` is shared by five other screens (Settings, Share preview, Insights, Triggers, Hunch) — branch it on `LocalCardDecorationStyle` too rather than restyling it outright, and confirm the new segmented "on" pill treatment reads fine everywhere it's reused; it has the widest blast radius of anything in this list.
- [ ] Compose Preview per changed composable (Bright, light + dark); check whether `HodithThemeTest`/`BigPictureDecorationTest` need new coverage for the glow decoration; manual on-device pass confirming Plain/Intense are unaffected.

## Settings

- [ ] About screen real content (currently wiring-only placeholder copy): version/privacy statement/licenses text, plus a privacy policy hosted on the SecondMonday Studios website and linked from the screen.
- [ ] Rate the App and Contact Us are placeholder rows (show a "coming soon" snackbar) — need real destinations once there's a Play Store listing and a support channel to point to.
- [ ] Developer Mode's hidden unlock (tap About's version row 7 times) reuses `AboutSection`'s default layout as the tap target, which may run a little under the 48dp accessibility minimum. Low priority since it's a deliberately undiscoverable power-user gesture, not a primary control, but worth a look if it ever grows more than one dev tool.

## Insights tab

- [ ] Needs an overall rework — several stat sections' squares (heatmap/rhythm) look visually similar to each other, undermining at-a-glance usefulness.
- [ ] Rhythm grid cells convey their count by shading alone — no content description or visible number, unlike the calendar heatmap's day-of-month numbers and the intensity squares' level numbers. Same color-only gap already deferred for the dot timeline; revisit alongside making either grid tappable. The share card's Rhythm mini-section is a faithful mini-copy of this same composable, so it inherits the identical gap — fix both together.
- [ ] Calendar heatmap: a visually large gap appears between the current month and prior months right when a new month has just begun.
- [ ] Rhythm grid: large gap between the legend and the grid squares under the Intense theme specifically.

## App icon

- [ ] Dark spot visible on the circle/handle at larger icon sizes — the invisible lens ring fix addressed a related issue, but this artifact remains at bigger resolutions.

## Testing & tooling

- [ ] Two cosmetic build-time warnings, neither worth chasing on its own: a "Kapt support in Moshi Kotlin Code Gen is deprecated" notice from `hiltJavaCompileDebug` even though Moshi is wired in via `ksp()` only (KSP-generated adapters work correctly; likely Moshi's processor warning whenever it's discoverable on any annotation-processing-adjacent classpath, regardless of which mechanism invokes it); and a K2 "`@ApplicationContext` applied to value parameter only" forward-compat notice (KT-73255) on `Notifier.kt`/`WidgetRefresher.kt`/`ComposeShareImageExporter.kt`. Worth a look if either ever escalates from warning to error on a future Kotlin/Hilt bump.

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
