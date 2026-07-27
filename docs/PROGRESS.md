# HODITH — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**You can create and edit Cases, and log Events against them** — one-tap quick-log, start/stop for ongoing activity, retro-log for past events — plus archive, delete, or view Archived Cases. **Big Picture** shows every active Case's icons on a scrollable multi-month calendar grid, with day/week detail views and a per-case filter; day cells carry a bespoke treatment per theme (Intense's tab-stripe, Bright's shadowed sticker cells; Plain is the generic baseline). **Case Detail** now has a Log/Insights/Hunch tab structure: Log is the event list plus a summary line (total events, observation span), Insights has all of spec §9–10 — dot timeline, calendar heatmap, and all seven stat sections (frequency, rhythm, gaps & clusters, trend, conditional duration/intensity, tag breakdown) — and Hunch carries the full spec §7 flow — a nudge card after 5 events on a hunch-less Case, Hunch creation, early-days/verdict cards driven by the pure verdict engine, resolve, and hunch history. **Settings** has a Theme picker (Plain/Intense/Bright) that drives the `Voice` layer and full palette/type/shape skin app-wide with a live preview, plus "Load demo data" / "Delete all data" actions.

## Housekeeping

Cross-cutting tooling/repo-hygiene work, unrelated to any specific phase — pick up whenever convenient, not gated on phase progress.

- [ ] Case Detail FAB — drop the "It happened earlier…" text label, icon-only, keep bottom-right position (it already behaves as a dual-purpose log-now/log-earlier control).
- [ ] Insights tab's rhythm grid cells convey their count by shading alone — no content description or visible number, unlike the calendar heatmap's day-of-month numbers and the intensity squares' level numbers. Same color-only gap already deferred for the dot timeline (`feature/case-insights-visuals`); revisit alongside making either grid tappable.
- [ ] `MILLIS_PER_MINUTE`/`MILLIS_PER_DAY` are redeclared as file-private constants in four places (`OngoingEvent.kt`, `LogDetailViewModel.kt`, `InsightsTabState.kt`, `StatsEngine.kt`) instead of one shared constant — pre-existing pattern across three of those files, not introduced by `feature/case-stats`. Consider a shared `domain`-layer constant if a fifth copy ever shows up.
- [ ] Compose UI instrumented test files each hand-roll their own `CaseEntity`/`EventEntity` fixtures instead of the shared `testCase()`/`testEvent()` builders in `data/TestFixtures.kt` (added for DAO tests, unused by any `ui` test so far). Left alone when `CaseDetailInsightsTest` adopted them, since each screen's local fixture varies enough (different `logFlow`/`durationMode`/`intensityEnabled` combos) that a shared builder wouldn't remove much duplication as-is — revisit if a `ui`-test-specific set of builders would actually pay for itself once more screens use `testCase()`/`testEvent()`.

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
- [x] **Phase 5** — Voice layer + three themes, renamed Plain/Intense/Bright (previously Serious/Goth/Quirky). The Voice half was already complete going into this phase (every screen already read `LocalVoice`, all three voices already existed); the phase's real work was the *skin* half of spec §12 — palette, type, and shape per theme — validated via an HTML mockup artifact before any Kotlin, iterated live with the user (Intense went through a full rework mid-review, from a gothic-archive look to genre film-noir: monochrome plus one crimson accent, bold condensed Oswald display type).
  - **Branches, in dependency order:**
    1. [x] `feature/theme-skins` — new `ui/theme/` package (`Color.kt` six `ColorScheme`s, `Type.kt` three `Typography`s built on 12 bundled OFL font files, `Shape.kt` three `Shapes`, `HodithTheme.kt`) wired into `HodithApp.kt` and Settings' live preview card, replacing the bare `MaterialTheme { }` that previously made theme-switching a copy-only change. Also folded in the Housekeeping list's Home-header item: a new `homeHeaderTitle` Voice key, three phrasings that all spell H-O-D-I-T-H, with the initials bolded in the theme's accent color.
    2. [x] `feature/big-picture-theme-polish` — Big Picture's bespoke day-cell/badge treatment per theme (Intense's neutral tab-stripe, Bright's soft-shadow sticker-fan cells), on top of the skin from (1). Plain is unchanged.
- [x] **Phase 6** — Verdict engine + Hunch flow. Case Detail's Log/Insights/Hunch tab structure and Hunch-flow copy (nudge, creation, verdict tiers, history) validated across all three themes in [docs/mockups/case-detail-prototype.html](mockups/case-detail-prototype.html) before any Kotlin.
  - **Branches, in dependency order:**
    1. [x] `feature/verdict-engine` — spec §8's pure verdict engine (`domain/Verdict.kt`, `domain/VerdictEngine.kt`): observation window, confidence tiers, comparison bands. No UI; direction-aware rendering is branch 2's job.
    2. [x] `feature/hunch-flow` — nudge card, Hunch creation, verdict/early-days cards, resolve + history, the Log/Insights/Hunch tab structure itself, and every new Voice key across all three voices.
- [x] **Phase 7** — Per-case visuals + stats (spec §9–10), filling in Case Detail's Insights tab. Layout validated in the same prototype above — toggle "Phase 7 preview" on its Insights tab.
  - **Branches, in dependency order:**
    1. [x] `feature/case-insights-visuals` — spec §9's visuals: dot timeline (primary, full-width, current-gap annotation) and calendar heatmap (secondary, multi-month, per-case relative shading). `domain/InsightsEngine.kt` for the pure window/gap/shading logic; `weeksInGrid` promoted to `domain/CalendarGrid.kt` to share with Big Picture.
    2. [x] `feature/case-stats` — spec §10's seven stat sections: frequency over time (auto-picked granularity, user-overridable Day/Week/Month toggle), rhythm heatmap, gaps & clusters (extends `GapStats` with average gap + "tends to come in bursts" flag), trend arrow (30-day rolling comparison, hidden below 8 weeks of history), conditional duration/intensity stats, and tag breakdown (shown against the Case's total event count). New `domain/Stats.kt` + `domain/StatsEngine.kt`. The heatmap/rhythm/intensity shading scale grew from 4 to 10 tiers along the way (`HeatmapLevel.L1`..`L10`) for finer-grained color distinction. Also added a summary line ("N events logged · observed for N days") above the Log tab's event list, reusing the same `observationSpanDays` the stats tab is built on.
- [ ] **Phase 8** — Widgets.
- [ ] **Phase 9** — Triggers + check-ins (WorkManager, notifications).
- [ ] **Phase 10** — Share cards, export/import, Settings polish (check-in default, About).
- [ ] **Phase 11** — Big Picture polish (follow-ups to the finished Phase 3 screen):
  - [ ] Tapping an event in the day/week detail view opens that Case's detail screen.
  - [ ] Day/week detail dialogs show event timestamp and tags.
  - [ ] Tag filter chips alongside the existing case filter chips (ties into HODITH_SPEC.md §17 "Tag-level insights").

Each phase ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
