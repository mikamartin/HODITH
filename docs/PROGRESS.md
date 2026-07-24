# HODITH — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**You can create and edit Cases, and log Events against them** — one-tap quick-log, start/stop for ongoing activity, retro-log for past events — plus archive, delete, or view Archived Cases. **Big Picture** shows every active Case's icons on a scrollable multi-month calendar grid, with day/week detail views and a per-case filter. **Settings** has a Theme picker (Plain/Intense/Bright) that drives the `Voice` layer app-wide with a live preview, plus "Load demo data" / "Delete all data" actions.

## Housekeeping

Cross-cutting tooling/repo-hygiene work, unrelated to any specific phase — pick up whenever convenient, not gated on phase progress.

- [ ] Case Detail FAB — drop the "It happened earlier…" text label, icon-only, keep bottom-right position (it already behaves as a dual-purpose log-now/log-earlier control).

## Phase order

- [x] **Phase 0** — Repo/toolchain scaffold; trivial `MainActivity` proving the build.
- [x] **Phase 1** — Room entities/DAOs, `HodithRepository`, JVM `Clock` abstraction, full test coverage.
- [x] **Phase 2** — Home + Case CRUD + logging flows (one-tap, detail sheet, start/stop, retro-log).
  - **Foundational, landed before the slicing below began:**
    - Minimal `Voice` layer (`ui/voice/Voice.kt`) — interface + Plain/Intense/Bright (named Serious/Goth/Quirky at the time; renamed in Phase 5), grown incrementally with each branch since.
    - Debug-only seed-data mechanism (6 synthetic cases/events, release-excluded) — removal tracked in DEV_PLAYBOOK's Ship Checklist.
    - Fixed a real device bug: missing `android:theme` let the OS's default `ActionBar` draw over edge-to-edge content.
  - **Branches, in dependency order** (each independently reviewable per the one-branch-per-logical-unit convention):
    1. [x] `feature/home-screen` — nav scaffold (Home · Big Picture · Settings) + read-only Home screen wired to real data.
    2. [x] `feature/case-crud` — New/Edit Case screen (§14); wires Home's FAB and row-tap. Hunch step deferred to its own later branch.
    3. `feature/logging-flows` — shared log sheet, one-tap, start/stop, retro-log, Case Detail. Split into 6 sequential PRs:
       1. [x] `feature/case-detail` — minimal Case Detail screen (event list, config access).
       2. [x] `feature/log-detail-sheet` — shared `LogDetailSheet` (time, intensity, MANUAL duration, note, tags).
       3. [x] `feature/quick-log` — one-tap quick-log + Undo snackbar, wired to Home.
       4. [x] `feature/start-stop` — Start/Stop, ongoing indicator, 24h stale-event prompt.
       5. [x] `feature/case-archive` — archive, hard delete, and a new Archived Cases view.
       6. [x] `feature/case-edit-polish` — collapsible icon picker, info icons, segmented Check-in, logFlow validation/auto-switch fix.
- [x] **Phase 3** — Big Picture: multi-month calendar grid of case icons (§9), built against real Case/Event data from Phase 2.
  - **Design (full detail in HODITH_SPEC.md §9):** month grid (day columns × week rows), case icons per day cell with a "+N" overflow badge, only days up to and including today shown, out-of-month padding days left blank, tap a day for its events + notes, a separate per-week chevron for a week view, tap the month title for a quick-jump month picker instead of pinch-zoom. Intensity/duration are not encoded on the grid (icon-only); early-days placeholder is two-tier (no cases vs. cases with no events yet).
  - `feature/big-picture` — promoted the validated `CalendarGridPrototype.kt` spike to production: `BigPictureViewModel` + real navigation + unit and instrumented Compose UI test coverage.
- [x] **Phase 4** — Settings foundation: DataStore-backed theme/voice picker (unblocks exercising Phase 5's themes) + promoting demo-data seeding from a debug-only mechanism to a real "load demo data" / "delete all data" pair of actions in Settings.
  - `feature/settings-foundation` — `SettingsRepository` (DataStore) now drives `LocalVoice` app-wide via a Theme picker with a text preview card; the debug-only auto-seed was retired in favor of `DemoDataSeeder`, triggered from Settings' "Load demo data" / "Delete all data" actions. Default check-in interval deferred to Phase 9, its only consumer.
- [ ] **Phase 5** — Voice layer + three themes, renamed Plain/Intense/Bright (previously Serious/Goth/Quirky). The Voice half was already complete going into this phase (every screen already read `LocalVoice`, all three voices already existed); the phase's real work was the *skin* half of spec §12 — palette, type, and shape per theme — validated via an HTML mockup artifact before any Kotlin, iterated live with the user (Intense went through a full rework mid-review, from a gothic-archive look to genre film-noir: monochrome plus one crimson accent, bold condensed Oswald display type).
  - **Branches, in dependency order:**
    1. [x] `feature/theme-skins` — new `ui/theme/` package (`Color.kt` six `ColorScheme`s, `Type.kt` three `Typography`s built on 12 bundled OFL font files, `Shape.kt` three `Shapes`, `HodithTheme.kt`) wired into `HodithApp.kt` and Settings' live preview card, replacing the bare `MaterialTheme { }` that previously made theme-switching a copy-only change. Also folded in the Housekeeping list's Home-header item: a new `homeHeaderTitle` Voice key, three phrasings that all spell H-O-D-I-T-H, with the initials bolded in the theme's accent color.
    2. [ ] `feature/big-picture-theme-polish` — Big Picture's bespoke day-cell/badge treatment per theme (e.g. Intense's neutral tab-stripe, Bright's soft-shadow cells), on top of the skin from (1).
- [ ] **Phase 6** — Verdict engine + Hunch flow.
- [ ] **Phase 7** — Per-case visuals + stats.
- [ ] **Phase 8** — Widgets.
- [ ] **Phase 9** — Triggers + check-ins (WorkManager, notifications).
- [ ] **Phase 10** — Share cards, export/import, Settings polish (check-in default, About).
- [ ] **Phase 11** — Big Picture polish (follow-ups to the finished Phase 3 screen):
  - [ ] Tapping an event in the day/week detail view opens that Case's detail screen.
  - [ ] Day/week detail dialogs show event timestamp and tags.
  - [ ] Tag filter chips alongside the existing case filter chips (ties into HODITH_SPEC.md §17 "Tag-level insights").

Each phase ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
