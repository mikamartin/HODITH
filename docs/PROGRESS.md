# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.
- [ ] Settings rework introduces `FilledTonalButton` for its action rows, a style not used anywhere else in the app (existing vocabulary is `Button`/`OutlinedButton`/`TextButton`). Bright now uses a flat label + chevron row instead (`ActionRow`'s `BrightActionRow` branch, resolved alongside the Bright theme redesign's Settings pass); Plain/Intense still use `FilledTonalButton`. Revisit whether Plain/Intense should adopt the flat-row look too, or whether Settings should conform to the existing three-tier button system instead.

## Settings

- [ ] Rate the App is still a placeholder row (shows a "coming soon" snackbar) — needs a real destination once there's a Play Store listing.

## App icon

- [ ] Dark spot visible on the circle/handle at larger icon sizes — the invisible lens ring fix addressed a related issue, but this artifact remains at bigger resolutions.

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
