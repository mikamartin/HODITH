# HODITH — Test Strategy

This doc defines the test strategy and coverage map. Current build status lives in PROGRESS.md, not here. Keep this in sync per CLEANUP_CHECKLIST.md → Tests.

---

## Strategy

Three layers, cheapest first:

1. **Unit tests (JVM, `./gradlew test`)** — the bulk. HODITH is deliberately architected so its riskiest logic is pure Kotlin: the verdict engine, trigger evaluation, stats calculations, and the Voice layer have zero Android dependencies and are tested exhaustively here.
2. **Instrumented tests (`./gradlew connectedDebugAndroidTest`)** — Room DAO tests against a real in-memory SQLite, Compose UI tests for the critical flows, WorkManager integration via `WorkManagerTestInitHelper`.
3. **Manual test plan (MANUAL_TEST_PLAN.md)** — only for flows that cross system-process boundaries instrumented tests can't drive: widget → trampoline activity → detail sheet, notification tap-through, system file picker (export/import), POST_NOTIFICATIONS permission dialog, device reboot with an ongoing event.

Rule of thumb: **if logic can be tested on the JVM, it must not require an emulator.** Time math goes through an injected `Clock`, never `System.currentTimeMillis()` — non-negotiable; it's what makes verdicts, gaps, and triggers testable.

## Planned unit coverage (highest value first)

| Area | What to cover |
|---|---|
| **Verdict engine** | Confidence tiers (boundary values: exactly 5 events, exactly 14 days); comparison bands at each boundary (0.5, 0.8, 1.25, 2.0); direction-aware interpretation (TOO_OFTEN vs NOT_ENOUGH vs JUST_CURIOUS); observation window with retro-logs earlier than case creation; empty event list; events all at the same instant; DST/timezone edges around day boundaries |
| **Trigger evaluation** | AT_LEAST fires at threshold, not below; rolling window drops old events; re-arm behaviour; SILENT_FOR fires at exactly n days and re-arms on the next event; deleted events un-fire correctly on next evaluation; disabled triggers never fire |
| **Check-in scheduling** | Hunch-derived interval (2 × expected gap, clamped 3–30 days) at each expectedPer unit; app-default fallback when no Hunch; per-case override incl. off; never-logged case counts from createdAt; "All quiet" re-arms without creating an event; at most one fire per interval; multiple due check-ins collapse into one summary |
| **Stats & visual data prep** | Gap calculations (0, 1, n events); cluster detection variance threshold; trend arrow hidden below 8 weeks; granularity auto-pick; duration stats excluding an ongoing (null-end) event; calendar heatmap day-bucketing across month and DST boundaries |
| **Voice layer** | Every `Voice` key non-blank in all three implementations (reflection or exhaustive test); no key returns an identical string across all three (catches copy-paste) |
| **Share card assembly** | Section selection per case state (hunch vs no-hunch arc); notes/tags never included regardless of toggles; display-name override applied everywhere on the card; voice-correct copy for all three themes; story vs square layout parameters |
| **Export/import** | Round-trip equality; schema version rejection; malformed JSON is all-or-nothing (DB untouched on failure); import of an ongoing event |
| **ViewModels** | State reduction, undo window, one-ongoing-per-case invariant |

## Planned instrumented coverage

| Area | What to cover |
|---|---|
| Room DAOs | CRUD per entity, cascade delete case → events/hunches/triggers, tag join queries, "events in window" queries, all-cases-with-events query feeding the Big Picture |
| Compose UI | Create Case incl. skipping the Hunch step; one-tap log + undo; detail sheet save with retro time; start/stop flow; theme switch re-words visible strings; hunch nudge appears at 5th event and dismisses permanently; archive a Case from Case Edit (confirm dialog, hidden when creating new); Archived Cases list with unarchive (immediate) and delete-forever (confirm dialog naming the event count) |
| Compose UI — Big Picture | Grid renders real cases/events from `HodithRepository`; empty-state placeholder shows with zero cases; early-days placeholder shows with cases but zero events; day cell tap opens that day's events; month title tap opens the month picker |
| WorkManager | Periodic trigger evaluation job enqueued once, executes, fires SILENT_FOR |

## Manual-only journeys (seed list for MANUAL_TEST_PLAN.md)

Cadence: before every release; full pass before Play submissions.

1. List widget one-tap log on a `ONE_TAP` case — event appears in-app
2. List widget tap on a `DETAIL_SHEET` case — sheet opens via trampoline, saves
3. Single-case widget: start, see ongoing/elapsed, stop
4. Reboot device with an ongoing event — state survives, elapsed correct
5. Trigger notification fires; tap opens the right Case
6. POST_NOTIFICATIONS: deny → in-app banner fallback works
7. Export → wipe app data → import → everything restored (decide and document whether theme choice — stored in prefs — is included)
8. Timezone change / DST night with events on both sides — day-bucketing in stats, calendar heatmap, and Big Picture stays sane
9. Big Picture with 15+ cases and a year of data — scrolling stays responsive on a mid-range device
10. Check-in notification: "Log" action logs correctly per the Case's logFlow; "All quiet" dismisses and re-arms; several due check-ins arrive as one summary notification
11. Share flow: preview renders in all three themes, edited display name shows on the card, share sheet opens and the image lands correctly in a messenger app (both story and square)

## Deferrals

- Retro-log's date/time picker restrictions and tag add/remove/delete-from-sheet aren't driven by an instrumented test — covered only by `LogDetailViewModelTest`'s pure-logic tests and manual verification. Pick up if it starts to matter.

## Known environment issues

- Instrumented tests run clean on an API 36 AVD (DAO and Compose UI tests both verified there) — no open Android-version compatibility gap.
- **Prefer querying the live on-device DB over pulling `.db` files to inspect locally:** `adb shell "run-as <pkg> sqlite3 /data/data/<pkg>/databases/hodith.db \"<query>\""` is always consistent. A local copy isn't reliable — Room runs SQLite in WAL mode, so recent writes often sit in `hodith.db-wal`/`hodith.db-shm` rather than the main file until a checkpoint, which can make a pulled copy look like it's missing rows or read as corrupt. If you do need to pull, grab all three files (`hodith.db`, `hodith.db-wal`, `hodith.db-shm`) together.
- **Git Bash mangles absolute-looking `adb shell` paths** (e.g. `/sdcard/foo.png` gets rewritten to a Windows path by MSYS's automatic path conversion). Prefix the command with `MSYS_NO_PATHCONV=1` when passing device-side paths to `adb shell`/`adb exec-out`/`adb pull`.
- **Compose UI instrumented tests need `espresso-core` pinned to 3.7.0+** — the version `ui-test-junit4` pulls in transitively throws `NoSuchMethodException: android.hardware.input.InputManager.getInstance` during Espresso's own startup on some API levels. Already pinned in `libs.versions.toml`.
- **Scripted `adb shell input tap <x> <y>` is unreliable for verifying transient/animated UI** (e.g. a `Snackbar` action) during manual on-device checks — taps at coordinates matching `uiautomator dump`'s reported bounds can still fail to register. Confirm with a real finger tap before concluding it's a product bug. `uiautomator dump` (`MSYS_NO_PATHCONV=1 adb shell uiautomator dump /sdcard/window.xml && MSYS_NO_PATHCONV=1 adb pull /sdcard/window.xml`) is still the right way to find a node's exact bounds.
- **A connected physical device can fail Compose UI instrumented tests with `IllegalStateException: No compose hierarchies found in the app`** (emulators don't) — likely the test host Activity never resuming on that device (screen locked/off, OEM restriction), not a code defect. Run Compose UI instrumented tests on an emulator; if a physical device is also attached, target just the emulator: `adb -s <emulator-serial> install -r app/build/outputs/apk/debug/app-debug.apk` (and the matching `androidTest` APK), then `adb -s <emulator-serial> shell am instrument -w com.secondmonday.hodith.test/com.secondmonday.hodith.HiltTestRunner` (`-e class <FqcnTest>` to scope to one class).

## CI coverage

GitHub Actions (`.github/workflows/ci.yml`, job name `build`) runs `ktlintCheck` → `lintDebug` → `test` → `assembleDebug` on every PR against `main` and every push to `main` — layer 1 of the strategy above is enforced automatically, plus Android's own lint checks (`lintDebug`) alongside ktlint's style checks.

Layer 2 (instrumented) also runs in CI: `.github/workflows/instrumented-tests.yml` runs `connectedDebugAndroidTest` on a cached API 36 emulator (`reactivecircus/android-emulator-runner`), split into two matrix shards by the `@UiTest` annotation (`app/src/androidTest/kotlin/com/secondmonday/hodith/testtags/UiTest.kt`) — `repository` (DAO tests, not annotated) and `ui` (Compose screen tests, annotated). New instrumented test classes only need the annotation, not a workflow edit, to land in the right shard.

Both workflows report total/passed/failed/skipped counts (Checks tab annotations + job summary) via `mikepenz/action-junit-report`.

Layer 3 (manual) is not in CI by definition — human-only.

### Instrumented test tags

- `@UiTest` (class-level) — marks a Compose UI instrumented test class; drives the CI shard split above.
- `@Smoke` (method-level) — marks one representative happy-path test per class, for a quick local sanity run without the full suite:
  ```
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.secondmonday.hodith.testtags.Smoke
  ```
- To run just one screen or DAO's tests locally without any tag, filter by package — no annotation needed since the suite is already one class per package per screen/entity:
  ```
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.secondmonday.hodith.ui.home
  ```
