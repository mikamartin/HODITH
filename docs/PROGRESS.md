# HODITH — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**Phase 1 complete. Re-scoping resolved — Phase 2 is now Home + Case CRUD, not Big Picture.** The original row/dot shared-time-axis Big Picture design (built, wired to real data, instrumented-tested) was retired after on-device testing showed it didn't read clearly. Its replacement — a multi-month calendar grid of case icons, tap+swipe only, no pinch/zoom — was validated interactively via a throwaway prototype (`ui/timeline/CalendarGridPrototype.kt`, still uncommitted) and is written up in HODITH_SPEC.md §9.

That pivot removed the original reason Big Picture was built before Case CRUD (de-risking pinch-zoom early — gone now that there's no pinch-zoom at all), so the phase order changed: **Case/Event CRUD is now Phase 2, Big Picture is now Phase 3**, and Big Picture will be built against real Case/Event data instead of synthetic seed data. All row/dot production code and tests (`domain/timeline/TimelineLayout.kt`, `TimelineWindowMath.kt`, `TimelineAxis.kt`, `ui/timeline/TimelineGestures.kt`, `TimelineScreen.kt`, `TimelineViewModel.kt`, and their tests) have been deleted rather than held for reuse — they were shaped around a multi-case shared-row layout, so reuse for the future single-case dot timeline (Phase 6) was speculative, and four phases is too long to carry untested-in-context code as a "maybe." `Voice.kt` lost `timeRangeLabel(ZoomLevel)` as part of that deletion (no zoom levels left to label); `bigPictureEmptyState`/`bigPictureEarlyDays` stay. `MainActivity` now shows a placeholder pending Phase 2's Home screen. The "Big Picture" name stays — it describes the cross-case-correlation concept, not the retired mechanism.

Next up: **Phase 2 — Home + Case CRUD.**

## CI

GitHub Actions (`.github/workflows/ci.yml`, on `chore/ci-setup`) runs `ktlintCheck` → `test` → `assembleDebug` on every PR against `main` and every push to `main`. Verified green on PR #1 (job name `build`).

**Not yet a required check.** Classic branch-protection rules (which would block merging on a failing `build` check) aren't available on GitHub's free plan for a private repo — and this repo is intentionally private for now, going public later once it's release-clean. Tracked in DEV_PLAYBOOK.md's Ship Checklist as a "before going public" step; until then CI runs and reports status but doesn't block a merge.

Deliberately not covered yet — pick up when they start to matter:

- **Android Lint** (`./gradlew lintDebug`) — only ktlint (style) runs today, not Android's own lint checks.
- **Instrumented tests** (`./gradlew connectedDebugAndroidTest`) — needs an emulator/device runner in CI (e.g. `reactivecircus/android-emulator-runner`), not just a JVM; bigger setup, deferred until instrumented tests exist to run (see Phase 2's remaining work).

## Phase order

- [x] **Phase 0** — Repo and project scaffold: git init, Gradle/AGP/Compose/Hilt/Room toolchain, empty package skeleton, trivial `MainActivity` proving the build (`ktlintCheck` → `test` → `assembleDebug` all green).
- [x] **Phase 1** — Room entities, DAOs, `HodithRepository`, JVM `Clock` abstraction + full unit/DAO tests.
- [ ] **Phase 2** — Home + Case CRUD + logging flows (one-tap, detail sheet, start/stop, retro-log). *(Moved ahead of Big Picture — see "Current status" above for why.)*
  - **Landed already, carries forward:**
    - **Minimal `Voice` layer** (`ui/voice/Voice.kt`) — `Voice` interface + `SeriousVoice`/`GothVoice`/`QuirkyVoice`, currently just `bigPictureEmptyState`/`bigPictureEarlyDays`, provided via `LocalVoice`. `VoiceTest` asserts every key non-blank across all three voices. This phase and Phase 4 both add keys as they go — Phase 4 doesn't redesign the interface.
    - **Debug-only seed-data mechanism** (`app/src/debug/kotlin/.../debug/SeedDataInitializer.kt` + `AppInitializer`/`AppInitializerModule.kt`) — 6 synthetic cases/events, idempotent, structurally excluded from release. Useful for exercising Big Picture in Phase 3 before real Case creation is polished; content may want revisiting once real Case examples are decided.
    - **A real device bug, found and fixed.** No `android:theme` was set, so the OS's default `ActionBar` rendered over the content, and `targetSdk 36`'s enforced edge-to-edge meant the top of the screen drew underneath it. Fixed via `android:theme="@android:style/Theme.DeviceDefault.NoActionBar"` on the manifest's `<application>`, plus `enableEdgeToEdge()` + `Modifier.safeDrawingPadding()` in `MainActivity`.
  - **Not yet started:** Home screen (case list, quick-log, ongoing indicator), New/Edit Case screen (§14), the three logging flows (§6), retro-log. `MainActivity` currently just shows a placeholder pending this.
- [ ] **Phase 3** — Big Picture: multi-month calendar grid of case icons (§9), built against real Case/Event data from Phase 2 rather than seed data.
  - **New direction: calendar grid (validated as a prototype, not yet built as production code).** Iterated live across many rounds of feedback in a throwaway `ui/timeline/CalendarGridPrototype.kt` (uncommitted, must not ship as-is): month grid (day columns × week rows), case icons per day cell with a "+N" overflow badge, only days up to and including today shown, out-of-month padding days left blank rather than duplicating the neighbouring month's real dates, tap a day for its events + notes, a separate per-week chevron for a week view (kept as a distinct tap target from day cells — a click handler spanning the whole row would never fire for taps landing on a day cell), tap the month title for a quick-jump month picker instead of pinch-zoom. Full design is written up in HODITH_SPEC.md §9. A "want more / want less" per-case direction idea was raised and deliberately **not built**: it's close enough to Phase 5's real Hunch/Verdict direction concept (`HunchDirection`) that an ad-hoc Big Picture version risks conflicting with or duplicating that later work — revisit after Phase 5 exists, and only as a *filter*, never aggregated into a single day/week "verdict" (that would drift toward the habit-tracker/scoring mechanics spec §4 explicitly excludes).
  - **Retired: row/dot shared-time-axis design.** Built through prototype → real-data wiring → instrumented tests (committed: `32bb58c`, `ef80cc9`), then on a real device found not to serve its purpose — case names truncated unreadably in the fixed leading column, and dot-size-encodes-cluster-count/intensity read as arbitrary without a legend. All production code and tests were deleted, not held for reuse: `domain/timeline/TimelineLayout.kt`, `TimelineWindowMath.kt`, `TimelineAxis.kt`, `ui/timeline/TimelineGestures.kt`, `TimelineScreen.kt`, `TimelineViewModel.kt`, and their unit/instrumented tests. It was shaped around a multi-case shared-row layout, so its reuse potential for Phase 6's single-case dot timeline was speculative — see git history (`2357a57`..`ef80cc9`) if that build narrative is needed later.
  - **Still ahead:** the calendar grid exists only as an uncommitted, unshipped Compose Preview spike. Production implementation (real repository data, real navigation, instrumented Compose UI tests), plus how/whether intensity and duration events surface on the grid (open question, spec §9), plus phase close-out: DEV_PLAYBOOK §1 cleanup pass, a CLEANUP_LOG entry, this file's status/checkbox.
- [ ] **Phase 4** — Voice layer + three themes. Extends the `Voice` interface started in Phase 2 (see note above) — add remaining keys and theme skins, don't re-architect the interface.
- [ ] **Phase 5** — Verdict engine + Hunch flow.
- [ ] **Phase 6** — Per-case visuals + stats.
- [ ] **Phase 7** — Widgets.
- [ ] **Phase 8** — Triggers + check-ins (WorkManager, notifications).
- [ ] **Phase 9** — Share cards, export/import, Settings, About.

Each phase ends with a DEV_PLAYBOOK.md §1 cleanup pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
