# HODITH — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**Phase 1 complete.** Phase 2 in progress — gesture/rendering prototype built and green (`ktlintCheck` → `test` → `assembleDebug`), not yet wired to real data.

## CI

GitHub Actions (`.github/workflows/ci.yml`, on `chore/ci-setup`) runs `ktlintCheck` → `test` → `assembleDebug` on every PR against `main` and every push to `main`. Verified green on PR #1 (job name `build`).

**Not yet a required check.** Classic branch-protection rules (which would block merging on a failing `build` check) aren't available on GitHub's free plan for a private repo — and this repo is intentionally private for now, going public later once it's release-clean. Tracked in DEV_PLAYBOOK.md's Ship Checklist as a "before going public" step; until then CI runs and reports status but doesn't block a merge.

Deliberately not covered yet — pick up when they start to matter:

- **Android Lint** (`./gradlew lintDebug`) — only ktlint (style) runs today, not Android's own lint checks.
- **Instrumented tests** (`./gradlew connectedDebugAndroidTest`) — needs an emulator/device runner in CI (e.g. `reactivecircus/android-emulator-runner`), not just a JVM; bigger setup, deferred until instrumented tests exist to run (see Phase 2's remaining work).

## Phase order

- [x] **Phase 0** — Repo and project scaffold: git init, Gradle/AGP/Compose/Hilt/Room toolchain, empty package skeleton, trivial `MainActivity` proving the build (`ktlintCheck` → `test` → `assembleDebug` all green).
- [x] **Phase 1** — Room entities, DAOs, `HodithRepository`, JVM `Clock` abstraction + full unit/DAO tests.
- [ ] **Phase 2** — Big Picture view. Hardest UI problem (custom Compose, shared time axis, pinch-zoom, performance with years of data) — done early so its constraints surface before other screens depend on it.
  - Sequencing decision: this phase lands before Case CRUD (Phase 3) and the full Voice layer (Phase 4), so two things are pulled forward in minimal form rather than broken:
    - **Voice, minimal.** The `Voice` interface + `SeriousVoice`/`GothVoice`/`QuirkyVoice` are created now with only the keys Big Picture needs (empty state, early-days placeholder, time-range labels). Phase 4 *extends* the same interface with the full string set and adds theme skins/colors — it does not redesign it. This keeps the "no inline strings, all three voices, same commit" rule intact from the first screen onward instead of breaking it temporarily.
    - **Debug-only seed data.** Since Case/Event creation UI doesn't exist until Phase 3, a debug-build-only mechanism inserts fake cases/events so Big Picture can be run and manually checked (pinch-zoom, scroll, density) on a device while it's being built. Must be removed or fenced out before release — tracked in DEV_PLAYBOOK.md Ship Checklist.
  - **Package name: `timeline`, not `bigpicture`.** "Big Picture" is the screen/route name from the spec; the mechanism it's built from — shared time axis, pan/zoom, dot/bar layout — is more accurately a timeline renderer, and may end up reused by the per-case dot timeline (spec §9 "Per-case: dot timeline"). `domain/timeline/` and `ui/timeline/` hold it; the screen itself (once wired up) can still be named/routed as "Big Picture" in the UI layer.
  - **De-risking slice landed: gesture/rendering prototype, no real data yet.** Per the plan to prove the riskiest part (pan/zoom/tap disambiguation + rendering) before wiring plumbing around it:
    - `domain/timeline/TimelineLayout.kt` — pure Kotlin `TimeWindow`, `ZoomLevel` (week/month/3-month/year presets), `TimelineMark` (`Dot`/`Bar`), and `layoutRow()` which clusters point events into one dot when they'd land closer together than `slotCount` allows. Deliberately takes a domain-level `TimelineEvent`, not Room's `EventEntity` — keeps this package decoupled from the persistence schema, consistent with the verdict/trigger/stats "pure Kotlin" rule even though Room annotations aren't `android.*` per se.
    - `domain/timeline/TimelineWindowMath.kt` — `nextWindow()` (pan/zoom-around-a-focal-point math) and `withDuration()` (recenter at a new duration, used when snapping zoom to the nearest preset). Kept pure/testable by having the UI layer convert pixel offsets to fractions before calling in.
    - `ui/timeline/TimelineGestures.kt` — one custom pointer-input loop (`detectTapOrTimelineGesture`) that disambiguates tap vs. pan/zoom via a single touch-slop state machine, instead of layering a separate tap detector on top of a transform-gesture detector (two independent detectors racing the same pointer stream was the failure mode being designed around).
    - `ui/timeline/TimelineScreen.kt` — `TimelineGrid` composable. Gesture handling lives on **one `Box` spanning all rows**, not per-row Canvas, specifically so a pinch whose two fingers land on different rows is still read as one zoom gesture rather than two independent single-finger pans. Zoom snaps to the nearest `ZoomLevel` preset via an `Animatable` on gesture end. Has a `@Preview` with synthetic rows (point events, clustered events, a duration bar).
    - **Deliberately deferred out of this slice:** `LazyColumn` virtualization (plain `Column` for now — fine at prototype row counts, revisit if/when real case counts make it matter), the time-axis ruler/date labels, and the empty-state/early-days placeholder strings. The last two need the minimal `Voice` layer (still pending) so they're not inline strings even temporarily.
    - **Verified on device (Pixel 6 AVD):** tap and pan, via a throwaway `MainActivity` wire-up (reverted after). Tap hit-tested the correct event; a pan shifted all three rows together and reflowed clustering live, confirming the shared-window design. Pinch/zoom could not be driven this way — `adb` has no multi-touch synthesis — so the focal-point/clamping math is unit-tested but the on-device zoom *feel* is still unverified.
  - **Remaining for Phase 2 close-out:**
    - **Row-header tap is currently broken, not just missing.** Spec requires tapping a row's icon/name to open the Case, but there's no `onCaseTap` callback — worse, because the gesture surface spans the whole row including the leading column, tapping the icon/name today misfires as a dot-tap at the leftmost position. Needs a real fix (exclude the leading column from dot hit-testing, add a distinct callback), not just an addition.
    - On-device pinch/zoom check (see above) — needs a hands-on pass, not just unit tests.
    - Minimal `Voice` layer (blocks the axis labels and empty-state copy below).
    - Time-axis ruler/date labels.
    - Empty-state / early-days placeholder.
    - Debug-only seed data mechanism (decided, not built — the on-device check above used a throwaway wire-up, already reverted).
    - Wire `TimelineGrid` to real `HodithRepository` data instead of synthetic sample rows.
    - Instrumented Compose UI tests per TESTING.md's plan (tap a dot opens the event, tap a row header opens the Case).
    - Phase close-out: DEV_PLAYBOOK §1 cleanup pass, a CLEANUP_LOG entry, this file's status/checkbox.
- [ ] **Phase 3** — Home + Case CRUD + logging flows (one-tap, detail sheet, start/stop, retro-log).
- [ ] **Phase 4** — Voice layer + three themes. Extends the minimal `Voice` interface started in Phase 2 (see note above) — add remaining keys and theme skins, don't re-architect the interface.
- [ ] **Phase 5** — Verdict engine + Hunch flow.
- [ ] **Phase 6** — Per-case visuals + stats.
- [ ] **Phase 7** — Widgets.
- [ ] **Phase 8** — Triggers + check-ins (WorkManager, notifications).
- [ ] **Phase 9** — Share cards, export/import, Settings, About.

Each phase ends with a DEV_PLAYBOOK.md §1 cleanup pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
