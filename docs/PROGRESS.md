# HODITH — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**You can create and edit Cases, and log Events against them** — one-tap quick-log, start/stop for ongoing activity, retro-log for past events — plus archive, delete, or view Archived Cases. **Big Picture** shows every active Case's icons on a scrollable multi-month calendar grid, with day/week detail views and a per-case filter; day cells carry a bespoke treatment per theme (Intense's tab-stripe, Bright's shadowed sticker cells; Plain is the generic baseline). **Case Detail** now has a Log/Insights/Hunch tab structure: Log is the event list plus a summary line (total events, observation span), Insights has all of spec §9–10 — dot timeline, calendar heatmap, and all seven stat sections (frequency, rhythm, gaps & clusters, trend, conditional duration/intensity, tag breakdown) — and Hunch carries the full spec §7 flow — a nudge card after 5 events on a hunch-less Case, Hunch creation, early-days/verdict cards driven by the pure verdict engine, resolve, and hunch history. **Settings** has a Theme picker (Plain/Intense/Bright) that drives the `Voice` layer and full palette/type/shape skin app-wide with a live preview, a default check-in interval picker (off/7/14/30 days), plus "Load demo data" / "Delete all data" actions. A Case's check-in control in New/Edit Case is now a single on/off toggle (§11's per-Case custom interval was dropped in favor of a `SILENT_FOR` Trigger). **Triggers** now has a real screen, reached from Case Detail's header: per-Case list/create/enable-disable/delete — nothing fires yet, that's the next two Phase 9 branches.

## Housekeeping

Cross-cutting tooling/repo-hygiene work, unrelated to any specific phase — pick up whenever convenient, not gated on phase progress.

- [ ] Insights tab's rhythm grid cells convey their count by shading alone — no content description or visible number, unlike the calendar heatmap's day-of-month numbers and the intensity squares' level numbers. Same color-only gap already deferred for the dot timeline (`feature/case-insights-visuals`); revisit alongside making either grid tappable.

## Phase order

- [x] **Phase 0** — Repo/toolchain scaffold; trivial `MainActivity` proving the build.
- [x] **Phase 1** — Room entities/DAOs, `HodithRepository`, JVM `Clock` abstraction, full test coverage.
- [x] **Phase 2** — Home + Case CRUD + logging flows (one-tap, detail sheet, start/stop, retro-log).
  - **Foundational:**
    - Minimal `Voice` layer (`ui/voice/Voice.kt`) — Plain/Intense/Bright, grown incrementally since.
    - Debug-only seed-data mechanism (6 synthetic cases/events, release-excluded).
    - Fixed a device bug: missing `android:theme` let the OS's `ActionBar` draw over edge-to-edge content.
  - **Branches, in dependency order:**
    1. [x] `feature/home-screen` — nav scaffold (Home · Big Picture · Settings) + read-only Home screen wired to real data.
    2. [x] `feature/case-crud` — New/Edit Case screen; wires Home's FAB and row-tap.
    3. `feature/logging-flows` — shared log sheet, one-tap, start/stop, retro-log, Case Detail. Split into 6 sequential PRs:
       1. [x] `feature/case-detail` — minimal Case Detail screen (event list, config access).
       2. [x] `feature/log-detail-sheet` — shared `LogDetailSheet` (time, intensity, MANUAL duration, note, tags).
       3. [x] `feature/quick-log` — one-tap quick-log + Undo snackbar, wired to Home.
       4. [x] `feature/start-stop` — Start/Stop, ongoing indicator, 24h stale-event prompt.
       5. [x] `feature/case-archive` — archive, hard delete, and a new Archived Cases view.
       6. [x] `feature/case-edit-polish` — collapsible icon picker, info icons, segmented Check-in, logFlow validation/auto-switch fix.
- [x] **Phase 3** — Big Picture: multi-month calendar grid of case icons.
  - `feature/big-picture` — promoted the validated `CalendarGridPrototype.kt` spike to production: `BigPictureViewModel` + real navigation + unit and instrumented Compose UI test coverage.
- [x] **Phase 4** — Settings foundation.
  - `feature/settings-foundation` — `SettingsRepository` (DataStore) drives `LocalVoice` app-wide via a Theme picker with a live preview card; debug-only auto-seed replaced by `DemoDataSeeder`, triggered from Settings' "Load demo data" / "Delete all data" actions.
- [x] **Phase 5** — Voice layer + three themes (Plain/Intense/Bright).
  - **Branches, in dependency order:**
    1. [x] `feature/theme-skins` — new `ui/theme/` package (`Color.kt` six `ColorScheme`s, `Type.kt` three `Typography`s on 12 bundled OFL font files, `Shape.kt` three `Shapes`, `HodithTheme.kt`) wired into `HodithApp.kt` and Settings' live preview card. New `homeHeaderTitle` Voice key.
    2. [x] `feature/big-picture-theme-polish` — Big Picture's bespoke day-cell/badge treatment per theme.
- [x] **Phase 6** — Verdict engine + Hunch flow.
  - **Branches, in dependency order:**
    1. [x] `feature/verdict-engine` — pure verdict engine (`domain/Verdict.kt`, `domain/VerdictEngine.kt`): observation window, confidence tiers, comparison bands.
    2. [x] `feature/hunch-flow` — nudge card, Hunch creation, verdict/early-days cards, resolve + history, the Log/Insights/Hunch tab structure, new Voice keys across all three voices.
- [x] **Phase 7** — Per-case visuals + stats, filling in Case Detail's Insights tab.
  - **Branches, in dependency order:**
    1. [x] `feature/case-insights-visuals` — dot timeline (current-gap annotation) and calendar heatmap (multi-month, per-case relative shading). `domain/InsightsEngine.kt`; `weeksInGrid` promoted to `domain/CalendarGrid.kt` to share with Big Picture.
    2. [x] `feature/case-stats` — seven stat sections: frequency over time, rhythm heatmap, gaps & clusters, trend arrow, conditional duration/intensity, tag breakdown. New `domain/Stats.kt` + `domain/StatsEngine.kt`; heatmap/rhythm/intensity shading scale grew to 10 tiers (`HeatmapLevel.L1`..`L10`). Added a summary line above the Log tab's event list.
- [x] **Phase 8** — Widgets.
  - `feature/list-widget` — shipped the List widget: `ListWidget` reads real pinned/active Cases with one-tap log and Start/Stop; `WidgetLogTrampolineActivity` hosts `LogDetailSheet` for `DETAIL_SHEET` Cases; `ListWidgetConfigureActivity` prompts a Case picker; `WidgetRefresher` + 15-minute `WidgetRefreshWorker` keep it in sync. Widget chrome fixed to `PlainVoice`. Verified on-device.
- [ ] **Phase 9** — Triggers + check-ins (WorkManager, notifications). Design simplified from the original spec during Phase 9 planning: per-Case check-in *custom days* is dropped, since a `SILENT_FOR` Trigger already covers "notify me after N days of silence on this specific Case" — Case Edit's check-in control shrinks from a DEFAULT/CUSTOM/OFF segmented picker down to a single on/off toggle, and the only days-interval configuration left is Settings' app-level default. The hunch-derived default (2×expected-gap, spec §11) still applies automatically per-Case when a Hunch exists — that's engine logic, not a manual setting, so it's unaffected. Triggers are new end-to-end: no entity, no engine, no screen exist yet, so they're the bulk of this phase's work.
  - **Branches, in dependency order:**
    1. [x] `feature/checkin-settings` — Settings' default check-in interval picker (off/7/14/30 days, DataStore-backed, defaults to 7 days). Case Edit's check-in control simplified to a single on/off toggle (`checkInsEnabled: Boolean`, defaults true; schema v4). Added `effectiveCheckInDays` helper (hunch-derived → Settings default → off), unit-tested, not yet wired into UI.
    2. [x] `feature/trigger-checkin-engine` — `domain/TriggerEngine.kt`: `evaluateTrigger` (shared armed/fired state machine), `evaluateAtLeast` (rolling-window count), `evaluateSilentFor` (silence gap). `TriggerEntity` gained `armed: Boolean` (schema v5). `evaluateCheckIn`/`CheckInDecision` added to `domain/CheckIn.kt`. 26 new unit tests; `TriggerDaoTest` re-run on-device.
    3. [x] `feature/trigger-data` — repository CRUD was already scaffolded from early Case-CRUD work (entity, DAO, `TriggerKind` enum, Room FK cascade-delete on `caseId`); this branch rounded it out: `TriggerDao`/`HodithRepository` gained `getById`/`getTrigger` and a one-shot `getTriggersForCase` alongside the existing Flow/enabled-only queries, and `FakeHodithRepository.deleteCase` now cascades hunches and triggers to match Room's real FK cascade (previously only cascaded events).
    4. [x] `feature/triggers-screen` — HTML mockup first (`docs/mockups/triggers-prototype.html`), then the real Triggers screen: `TriggersScreen`/`TriggersViewModel`, per-Case list/create/enable-disable/delete, reached via a new bell icon in Case Detail's header. Hunch/`AT_LEAST` conceptual overlap surfaced during testing, deliberately left unresolved pending alpha feedback — see HODITH_SPEC.md §17.
    5. [ ] `feature/notification-infra` — WorkManager periodic job (~6h) evaluating both engines against real data; immediate trigger evaluation on event insert/edit/delete; notification channel(s); POST_NOTIFICATIONS permission request flow (first trigger created or first check-in enabled); Home banners when notifications are denied.
    6. [ ] `feature/notification-actions` — voice-flavoured notification content; tap-to-open for triggers; Log / All quiet actions for check-ins (reusing the widget's trampoline pattern); anti-spam summary notification collapsing multiple due check-ins.
- [ ] **Phase 10** — Share cards, export/import, Settings polish (About).
- [ ] **Phase 11** — Big Picture polish (follow-ups to the finished Phase 3 screen):
  - [ ] Tapping an event in the day/week detail view opens that Case's detail screen.
  - [ ] Day/week detail dialogs show event timestamp and tags.
  - [ ] Tag filter chips alongside the existing case filter chips (ties into HODITH_SPEC.md §17 "Tag-level insights").
- [ ] **Phase 12** — Leftover work deferred from earlier phases, picked up opportunistically once the phases above land:
  - [ ] Widget-picker preview image (deferred from Phase 8; tied to the existing app-icon item in DEV_PLAYBOOK.md's Ship Checklist).
  - [ ] Single-case widget (spec §15, deferred from Phase 8): small widget bound to one Case — tap logs per its `logFlow`, shows icon + today's count, ongoing state supported (elapsed + Stop, matching the List widget's per-row treatment).

Each phase ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
