# HODITH — Test Strategy

Status: **Phase 1 landed** (Room entities/DAOs/repository, `Clock`). This doc defines the strategy and the planned coverage map; the coverage tables get real counts as test files land. Keep it in sync per CLEANUP_CHECKLIST.md → Tests.

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
| **Stats & visual data prep** | Gap calculations (0, 1, n events); cluster detection variance threshold; trend arrow hidden below 8 weeks; granularity auto-pick; duration stats excluding an ongoing (null-end) event; Big Picture calendar-grid day-bucketing (events grouped by local date, month grid boundaries, out-of-month days blank not duplicated); calendar heatmap day-bucketing across month and DST boundaries |
| **Voice layer** | Every `Voice` key non-blank in all three implementations (reflection or exhaustive test); no key returns an identical string across all three (catches copy-paste) |
| **Share card assembly** | Section selection per case state (hunch vs no-hunch arc); notes/tags never included regardless of toggles; display-name override applied everywhere on the card; voice-correct copy for all three themes; story vs square layout parameters |
| **Export/import** | Round-trip equality; schema version rejection; malformed JSON is all-or-nothing (DB untouched on failure); import of an ongoing event |
| **ViewModels** | State reduction, undo window, one-ongoing-per-case invariant |

## Planned instrumented coverage

| Area | What to cover |
|---|---|
| Room DAOs | CRUD per entity, cascade delete case → events/hunches/triggers, tag join queries, "events in window" queries, all-cases-with-events query feeding the Big Picture |
| Compose UI | Create Case incl. skipping the Hunch step; one-tap log + undo; detail sheet save with retro time; start/stop flow; theme switch re-words visible strings; hunch nudge appears at 5th event and dismisses permanently; archive a Case from Case Edit (confirm dialog, hidden when creating new); Archived Cases list with unarchive (immediate) and delete-forever (confirm dialog naming the event count) |
| Compose UI — Big Picture (Phase 3) | Grid renders real cases/events from `HodithRepository` instead of the prototype's synthetic data; empty-state placeholder shows with zero cases; early-days placeholder shows with cases but zero events; day cell tap opens that day's events; week chevron tap opens the week view (and is a distinct tap target from the day cells it overlaps); month title tap opens the month picker; case filter chip toggles a case's icons off the grid; "+N" overflow badge appears once a day exceeds its icon capacity |
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

- ~~Compose UI instrumented test for the log detail sheet~~ — closed by `feature/start-stop`'s `CaseDetailScreenTest`, which drives `CaseDetailScreen`/`LogDetailSheet` directly (Start, Stop-now, editing an ongoing event's end time, the stale-prompt banner's actions) following `HomeScreenTest`'s stateless-screen-plus-fake-callbacks pattern. Retro-log's date/time picker restrictions and tag add/remove/delete-from-sheet specifically still aren't driven by an instrumented test — only by `LogDetailViewModelTest`'s pure-logic coverage and manual on-device verification — pick up if that gap starts to matter.

## Known environment issues

- Instrumented tests ran clean on a Pixel 6 AVD, API 34 (Phase 1, 25/25 DAO tests passed) — this doesn't yet exercise the Android 16 (API 36) compatibility gap noted at Phase 0 setup, since no API 36 emulator image was used. Re-verify on an API 36 device/emulator before relying on instrumented tests there. **Updated, `feature/start-stop`:** all 42 instrumented tests (DAO + both Compose UI test classes) ran clean on an API 36 AVD — the gap this note flagged is closed. **Updated, `feature/case-archive`:** all 54 instrumented tests (adds `ArchivedCasesScreenTest`, `CaseEditScreenTest`, and the new `CaseDao` archived-cases query test) ran clean on the same API 36 AVD.
- Never run Gradle tasks in parallel (see CLAUDE.md) — sequential `ktlintCheck` → `test` → `assembleDebug`.
- **Pulling the Room DB from a device/emulator to inspect it (`adb exec-out run-as <pkg> cat .../hodith.db`) can show stale or missing rows even with no bug present.** Room runs SQLite in WAL mode by default, so the most recent writes often sit in the `hodith.db-wal` file (and `hodith.db-shm`) rather than the main `.db` file until a checkpoint happens. Pull all three (`hodith.db`, `hodith.db-wal`, `hodith.db-shm`) into the same local directory before opening with `sqlite3` — this cost significant debugging time chasing a phantom "6th row never inserted" bug in the Big Picture seed-data mechanism that turned out to be a WAL-visibility artifact, not a code bug.
- **Git Bash mangles absolute-looking `adb shell` paths** (e.g. `/sdcard/foo.png` gets rewritten to a Windows path by MSYS's automatic path conversion). Prefix the command with `MSYS_NO_PATHCONV=1` when passing device-side paths to `adb shell`/`adb exec-out`/`adb pull`.
- **Compose UI instrumented tests need `androidx.test.espresso:espresso-core` pinned to 3.7.0+** (`feature/quick-log`, first Compose UI test in the repo) — the version `androidx.compose.ui:ui-test-junit4` pulls in transitively throws `NoSuchMethodException: android.hardware.input.InputManager.getInstance` during Espresso's own startup on some API levels, before any test code runs. Pinned via `libs.versions.toml` (`espresso-core`).
- **Scripted `adb shell input tap <x> <y>` is not reliable for verifying a `Snackbar` action button (or likely other short-lived, animated UI) during manual on-device checks.** While debugging `feature/quick-log`'s Undo snackbar, repeated scripted taps at coordinates matching `uiautomator dump`'s reported bounds exactly consistently failed to register as the action click (the snackbar always resolved via timeout instead), even with sub-second, single-shell-invocation timing between showing it and tapping it. This looked exactly like a real deletion bug — direct SQLite queries confirmed the event was never deleted — until a real finger tap on the same build confirmed the feature works correctly. Root cause not fully diagnosed (possibly touch-injection timing relative to the snackbar's enter animation); the practical takeaway is to treat scripted-tap "failures" on transient/animated UI as suspect and confirm with a real tap before concluding it's a product bug. `uiautomator dump` (pulled via `MSYS_NO_PATHCONV=1 adb shell uiautomator dump /sdcard/window.xml && MSYS_NO_PATHCONV=1 adb pull /sdcard/window.xml`) is still the right way to find a node's exact bounds — the coordinates weren't the problem here.
- **Querying the live on-device DB directly avoids the WAL-visibility pitfall above entirely, and is simpler than pulling files:** `sqlite3` ships on-device (`adb shell sqlite3 --version`). Use `adb shell "run-as <pkg> sqlite3 /data/data/<pkg>/databases/hodith.db \"<query>\""` (device-side path, so no `MSYS_NO_PATHCONV` needed for the query itself) rather than pulling `.db`/`.db-wal`/`.db-shm` to inspect with a local `sqlite3` — the local copy can be an inconsistent snapshot across the three files (seen as `database disk image is malformed` when pulled mid-write), where querying the live file on-device is always consistent.
- **The connected physical device fails Compose UI instrumented tests with `IllegalStateException: No compose hierarchies found in the app`**, while an emulator (Pixel 6 AVD) runs the same tests clean. This surfaces during Espresso's `onIdle`/semantics-tree lookup, not in app code, and is consistent with the test host Activity never resuming on that device (screen locked/off, or an OEM restriction) rather than a code defect. Until diagnosed, run Compose UI instrumented tests on an emulator; to target just the emulator when a physical device is also attached (Gradle's `connectedDebugAndroidTest` runs on every attached device), install and invoke directly: `adb -s <emulator-serial> install -r app/build/outputs/apk/debug/app-debug.apk`, same for `.../androidTest/debug/app-debug-androidTest.apk`, then `adb -s <emulator-serial> shell am instrument -w com.secondmonday.hodith.test/com.secondmonday.hodith.HiltTestRunner` (add `-e class <FqcnTest>` to scope to one class).

## CI coverage

GitHub Actions (`.github/workflows/ci.yml`, job name `build`) runs `ktlintCheck` → `lintDebug` → `test` → `assembleDebug` on every PR against `main` and every push to `main` — layer 1 of the strategy above is enforced automatically, plus Android's own lint checks (`lintDebug`) alongside ktlint's style checks. Layers 2 (instrumented) and 3 (manual) are not in CI: instrumented tests still require a local/manual emulator run, and the manual test plan is human-only by definition. (Open follow-up work for the instrumented-tests gap is tracked in PROGRESS.md's Housekeeping section, not here.)

Not yet a required check on `main`: classic branch-protection rules aren't available on GitHub's free plan for a private repo. Adding one (GitHub Settings, not a repo change) is a "before going public" step, alongside making the repository itself public.
