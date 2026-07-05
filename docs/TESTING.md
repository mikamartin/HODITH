# HODITH — Test Strategy

Status: **Phase 1 landed** (Room entities/DAOs/repository, `Clock`). This doc defines the strategy and the planned coverage map; the coverage tables get real counts as test files land. Keep it in sync per DEV_PLAYBOOK §1 → Tests.

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
| **Stats & visual data prep** | Gap calculations (0, 1, n events); cluster detection variance threshold; trend arrow hidden below 8 weeks; granularity auto-pick; duration stats excluding an ongoing (null-end) event; Big Picture row/dot bucketing per zoom level (week/month/3mo/year); calendar heatmap day-bucketing across month and DST boundaries |
| **Voice layer** | Every `Voice` key non-blank in all three implementations (reflection or exhaustive test); no key returns an identical string across all three (catches copy-paste) |
| **Share card assembly** | Section selection per case state (hunch vs no-hunch arc); notes/tags never included regardless of toggles; display-name override applied everywhere on the card; voice-correct copy for all three themes; story vs square layout parameters |
| **Export/import** | Round-trip equality; schema version rejection; malformed JSON is all-or-nothing (DB untouched on failure); import of an ongoing event |
| **ViewModels** | State reduction, undo window, one-ongoing-per-case invariant |

## Planned instrumented coverage

| Area | What to cover |
|---|---|
| Room DAOs | CRUD per entity, cascade delete case → events/hunches/triggers, tag join queries, "events in window" queries, all-cases-with-events query feeding the Big Picture |
| Compose UI | Create Case incl. skipping the Hunch step; one-tap log + undo; detail sheet save with retro time; start/stop flow; theme switch re-words visible strings; hunch nudge appears at 5th event and dismisses permanently; Big Picture — tap a dot opens the event, tap a row header opens the Case |
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
9. Big Picture with 15+ cases and a year of data — scrolling and pinch-zoom stay responsive on a mid-range device
10. Check-in notification: "Log" action logs correctly per the Case's logFlow; "All quiet" dismisses and re-arms; several due check-ins arrive as one summary notification
11. Share flow: preview renders in all three themes, edited display name shows on the card, share sheet opens and the image lands correctly in a messenger app (both story and square)

## Deferrals

*(empty — populate as gaps are knowingly accepted, with reasons)*

## Known environment issues

- Instrumented tests ran clean on a Pixel 6 AVD, API 34 (Phase 1, 25/25 DAO tests passed) — this doesn't yet exercise the Android 16 (API 36) compatibility gap noted at Phase 0 setup, since no API 36 emulator image was used. Re-verify on an API 36 device/emulator before relying on instrumented tests there.
- Never run Gradle tasks in parallel (see CLAUDE.md) — sequential `ktlintCheck` → `test` → `assembleDebug`.
- **Pulling the Room DB from a device/emulator to inspect it (`adb exec-out run-as <pkg> cat .../hodith.db`) can show stale or missing rows even with no bug present.** Room runs SQLite in WAL mode by default, so the most recent writes often sit in the `hodith.db-wal` file (and `hodith.db-shm`) rather than the main `.db` file until a checkpoint happens. Pull all three (`hodith.db`, `hodith.db-wal`, `hodith.db-shm`) into the same local directory before opening with `sqlite3` — this cost significant debugging time chasing a phantom "6th row never inserted" bug in the Phase 2 seed-data mechanism that turned out to be a WAL-visibility artifact, not a code bug.
- **Git Bash mangles absolute-looking `adb shell` paths** (e.g. `/sdcard/foo.png` gets rewritten to a Windows path by MSYS's automatic path conversion). Prefix the command with `MSYS_NO_PATHCONV=1` when passing device-side paths to `adb shell`/`adb exec-out`/`adb pull`.

## CI coverage

GitHub Actions runs `ktlintCheck` and unit tests (`./gradlew test`) on every PR (see PROGRESS.md's CI section) — layer 1 of the strategy above is enforced automatically. Layers 2 (instrumented) and 3 (manual) are not in CI: instrumented tests still require a local/manual emulator run, and the manual test plan is human-only by definition. Android Lint is also not yet wired into CI, separately from ktlint's style checks.
