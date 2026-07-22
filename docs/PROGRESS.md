# HODITH — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**You can create and edit Cases, and log Events against them** — one-tap quick-log, start/stop for ongoing activity, retro-log for past events — plus archive, delete, or view Archived Cases.

## Housekeeping

Cross-cutting tooling/repo-hygiene work, unrelated to any specific phase — pick up whenever convenient, not gated on phase progress.

Open items:

- [ ] **Instrumented tests in CI** (`./gradlew connectedDebugAndroidTest`) — needs an emulator/device runner (e.g. `reactivecircus/android-emulator-runner`), not just a JVM. Planned as its own branch, `chore/ci-instrumented-tests`. Requirements for that branch: tag every instrumented test class (e.g. AndroidX `@SmallTest`/`@MediumTest`/`@LargeTest`, or a custom annotation) and have the CI workflow select tests by tag rather than hardcoding class names/paths, so newly added instrumented tests are picked up automatically without further `ci.yml` edits. Also decide which runner action/API level to target and whether to cache the AVD snapshot.

## Phase order

- [x] **Phase 0** — Repo/toolchain scaffold; trivial `MainActivity` proving the build.
- [x] **Phase 1** — Room entities/DAOs, `HodithRepository`, JVM `Clock` abstraction, full test coverage.
- [x] **Phase 2** — Home + Case CRUD + logging flows (one-tap, detail sheet, start/stop, retro-log).
  - **Foundational, landed before the slicing below began:**
    - Minimal `Voice` layer (`ui/voice/Voice.kt`) — interface + Serious/Goth/Quirky, grown incrementally with each branch since.
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
- [ ] **Phase 3** — Big Picture: multi-month calendar grid of case icons (§9), built against real Case/Event data from Phase 2 rather than seed data.
  - **Design (full detail in HODITH_SPEC.md §9):** month grid (day columns × week rows), case icons per day cell with a "+N" overflow badge, only days up to and including today shown, out-of-month padding days left blank, tap a day for its events + notes, a separate per-week chevron for a week view, tap the month title for a quick-jump month picker instead of pinch-zoom.
  - **To do:** production implementation (real repository data, real navigation, instrumented Compose UI tests) — currently only an uncommitted Compose Preview prototype (`ui/timeline/CalendarGridPrototype.kt`). Open question: how/whether intensity and duration events surface on the grid (spec §9). Phase close-out: CLEANUP_CHECKLIST.md pass, a CLEANUP_LOG entry, this file's checkbox.
- [ ] **Phase 4** — Voice layer + three themes. Extends the `Voice` interface started in Phase 2 (see note above) — add remaining keys and theme skins, don't re-architect the interface.
- [ ] **Phase 5** — Verdict engine + Hunch flow.
- [ ] **Phase 6** — Per-case visuals + stats.
- [ ] **Phase 7** — Widgets.
- [ ] **Phase 8** — Triggers + check-ins (WorkManager, notifications).
- [ ] **Phase 9** — Share cards, export/import, Settings, About.

Each phase ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
