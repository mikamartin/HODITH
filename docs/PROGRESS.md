# HODITH — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**Phase 1 complete.** Phase 2 **paused for re-scoping**, not in progress. The original row/dot shared-time-axis design (built, wired to real data, instrumented-tested) was retired after on-device testing showed it didn't read clearly. A replacement — a multi-month calendar grid of case icons, tap+swipe only, no pinch/zoom — was validated interactively via a throwaway prototype and is now written up in HODITH_SPEC.md §9, but not yet built as production code. See Phase 2's "Open decisions" below before resuming — this is where a new session should start.

## CI

GitHub Actions (`.github/workflows/ci.yml`, on `chore/ci-setup`) runs `ktlintCheck` → `test` → `assembleDebug` on every PR against `main` and every push to `main`. Verified green on PR #1 (job name `build`).

**Not yet a required check.** Classic branch-protection rules (which would block merging on a failing `build` check) aren't available on GitHub's free plan for a private repo — and this repo is intentionally private for now, going public later once it's release-clean. Tracked in DEV_PLAYBOOK.md's Ship Checklist as a "before going public" step; until then CI runs and reports status but doesn't block a merge.

Deliberately not covered yet — pick up when they start to matter:

- **Android Lint** (`./gradlew lintDebug`) — only ktlint (style) runs today, not Android's own lint checks.
- **Instrumented tests** (`./gradlew connectedDebugAndroidTest`) — needs an emulator/device runner in CI (e.g. `reactivecircus/android-emulator-runner`), not just a JVM; bigger setup, deferred until instrumented tests exist to run (see Phase 2's remaining work).

## Phase order

- [x] **Phase 0** — Repo and project scaffold: git init, Gradle/AGP/Compose/Hilt/Room toolchain, empty package skeleton, trivial `MainActivity` proving the build (`ktlintCheck` → `test` → `assembleDebug` all green).
- [x] **Phase 1** — Room entities, DAOs, `HodithRepository`, JVM `Clock` abstraction + full unit/DAO tests.
- [ ] **Phase 2** — Big Picture view. *(Paused for re-scoping — see "Open decisions" below before resuming; don't just continue coding.)*
  - **Original rationale, now superseded.** Big Picture was pulled ahead of Case CRUD (Phase 3) specifically because a shared time axis with pinch-zoom was judged the hardest UI problem, worth de-risking early. With pinch/zoom retired from the design entirely (see below), that rationale no longer holds as stated — phase order is one of the open decisions, not a settled fact anymore.
  - **Landed and still valid regardless of visualization direction:**
    - **Minimal `Voice` layer** (`ui/voice/Voice.kt`) — `Voice` interface + `SeriousVoice`/`GothVoice`/`QuirkyVoice`, scoped to Big Picture's current keys (`bigPictureEmptyState`, `bigPictureEarlyDays`, `timeRangeLabel`), provided via `LocalVoice`. `VoiceTest` asserts every key non-blank across all three voices. Phase 4 extends this interface with the full string set and theme skins — doesn't redesign it.
    - **Debug-only seed-data mechanism** (`app/src/debug/kotlin/.../debug/SeedDataInitializer.kt` + `AppInitializer`/`AppInitializerModule.kt`) — 6 synthetic cases/events, idempotent, structurally excluded from release (never compiled into the release source set, not just runtime-gated). Still the right way to exercise a data-dependent screen before Case CRUD exists; content may want revisiting once real Case examples are decided, but the mechanism itself is sound.
    - **`TimelineViewModel`** (`ui/timeline/TimelineViewModel.kt`) — maps `HodithRepository.observeActiveCasesWithEvents()` (`Flow<List<CaseWithEvents>>`) into UI state, `initialWindow` anchored to `Clock.nowMillis()`. The repository→UI mapping shape here should carry over to whatever screen actually replaces `TimelineGrid`.
    - **A real device bug, found and fixed (uncommitted).** No `android:theme` was set, so the OS's default `ActionBar` rendered over the content, and `targetSdk 36`'s enforced edge-to-edge meant the top of the screen drew underneath it — this is what made the row/dot view look "unusable" and likely contributed to pinch not registering (a two-finger gesture partly landing on native chrome outside Compose's pointer-input system). Fixed via `android:theme="@android:style/Theme.DeviceDefault.NoActionBar"` on the manifest's `<application>`, plus `enableEdgeToEdge()` + `Modifier.safeDrawingPadding()` in `MainActivity`. Worth keeping regardless of what replaces the row/dot rendering.
  - **Retired: row/dot shared-time-axis design.** Built through prototype → real-data wiring → instrumented tests (all committed: `32bb58c`, `ef80cc9`), then on a real device found not to serve its purpose — case names truncated unreadably in the fixed leading column, and dot-size-encodes-cluster-count/intensity read as arbitrary without a legend. Not deleted yet: `domain/timeline/TimelineLayout.kt` (`layoutRow` clustering, `TimeWindow`/`ZoomLevel`), `domain/timeline/TimelineWindowMath.kt` (focal-point pan/zoom math), and `ui/timeline/TimelineGestures.kt` (tap-vs-pan/zoom disambiguation) are candidates for reuse in spec §9's still-planned single-case "Per-case: dot timeline" (Phase 6) — the truncation/correlation problems that killed this for the *multi-case* view don't apply to a single full-width case screen. `ui/timeline/TimelineScreen.kt`'s `TimelineGrid`/`TimelineRowData`/`sampleTimelineRows`, `TimelineAxis.kt`, and their tests are the row/dot-specific UI and are the more likely deletion candidates. See git history (`2357a57`..`ef80cc9`) for the full build narrative (gesture prototype → tap fix → Voice/axis → seed data → real-data wiring → tests) if it's needed later.
  - **New direction: calendar grid (validated as a prototype, not yet built as production code).** Iterated live across many rounds of feedback in a throwaway `ui/timeline/CalendarGridPrototype.kt` (uncommitted, must not ship as-is): month grid (day columns × week rows), case icons per day cell with a "+N" overflow badge, only days up to and including today shown, out-of-month padding days left blank rather than duplicating the neighbouring month's real dates, tap a day for its events + notes, a separate per-week chevron for a week view (kept as a distinct tap target from day cells — a click handler spanning the whole row would never fire for taps landing on a day cell), tap the month title for a quick-jump month picker instead of pinch-zoom. Full design is now written up in HODITH_SPEC.md §9 (this replaces the row/dot description there). A "want more / want less" per-case direction idea was raised and deliberately **not built**: it's close enough to Phase 5's real Hunch/Verdict direction concept (`HunchDirection`) that an ad-hoc Big Picture version risks conflicting with or duplicating that later work — revisit after Phase 5 exists, and only as a *filter*, never aggregated into a single day/week "verdict" (that would drift toward the habit-tracker/scoring mechanics spec §4 explicitly excludes).
  - **Open decisions before Phase 2 resumes** (resolve these first, don't just resume coding):
    1. **Row/dot code fate** — delete `TimelineGestures.kt`/`TimelineWindowMath.kt`/`TimelineLayout.kt`'s clustering outright, or hold them for the Phase 6 per-case dot timeline?
    2. **Phase order** — does Case/Event CRUD (Phase 3) move before Big Picture now that pinch-zoom's de-risking rationale is gone? Building the calendar grid against real Case/Event data instead of synthetic seed data might be the more natural order at that point.
    3. **Naming** — "Big Picture" was named around the row/dot mechanism; reconsider once the grid's shape is settled.
    4. **Build it for real** — the calendar grid exists only as an uncommitted, unshipped Compose Preview spike. Production implementation (real repository data, real navigation, instrumented tests) is still fully ahead.
  - **Remaining for Phase 2 close-out** (once the above is resolved and the grid is actually built):
    - Instrumented Compose UI tests for whatever the grid becomes.
    - Phase close-out: DEV_PLAYBOOK §1 cleanup pass, a CLEANUP_LOG entry, this file's status/checkbox.
- [ ] **Phase 3** — Home + Case CRUD + logging flows (one-tap, detail sheet, start/stop, retro-log).
- [ ] **Phase 4** — Voice layer + three themes. Extends the minimal `Voice` interface started in Phase 2 (see note above) — add remaining keys and theme skins, don't re-architect the interface.
- [ ] **Phase 5** — Verdict engine + Hunch flow.
- [ ] **Phase 6** — Per-case visuals + stats.
- [ ] **Phase 7** — Widgets.
- [ ] **Phase 8** — Triggers + check-ins (WorkManager, notifications).
- [ ] **Phase 9** — Share cards, export/import, Settings, About.

Each phase ends with a DEV_PLAYBOOK.md §1 cleanup pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
