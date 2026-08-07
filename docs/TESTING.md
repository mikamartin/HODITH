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
| **Check-in scheduling** | Hunch-derived interval (2 × expected gap, clamped 3–30 days) at each expectedPer unit takes priority over the Settings app-default; per-case on/off toggle (no custom per-case interval — that's a `SILENT_FOR` Trigger's job); never-logged case counts from createdAt; "All quiet" re-arms without creating an event; re-arming is explicit-only (action or new event), so an ignored due check-in isn't capped at one fire per interval |
| **Notification evaluation** | `NotificationEvaluator` orchestration against fakes: fires a Trigger only when due and persists `armed`/`lastFiredAt` back; fires a check-in only when due, without touching `lastCheckInAt` itself (re-arming is the "All quiet" action's job, outside the evaluator); skips archived Cases and Cases with `checkInsEnabled = false`; unknown Case id is a no-op; `evaluateAll` spans every enabled Trigger and every active Case, not just one; 2+ due check-ins in the same pass collapse into one summary instead of firing individually, exactly 1 due still fires its own notification |
| **Stats & visual data prep** | Gap calculations (0, 1, n events); cluster detection variance threshold; trend arrow hidden below 8 weeks; granularity auto-pick; duration stats excluding an ongoing (null-end) event; calendar heatmap day-bucketing across month and DST boundaries; heatmap's in-progress month drops trailing week-rows that are entirely in the future |
| **Voice layer** | Every `Voice` key non-blank in all three implementations (reflection or exhaustive test); no key returns an identical string across all three (catches copy-paste) |
| **Share card assembly** | Top-beat selection (Hunch vs. Reality only on Story with a resolved Hunch and the toggle on; Reality fallback otherwise, always on Square); section filtering against both user selection and the Case's own config (Duration/Intensity gated on tracking, not just selection); display-name override decoupled from the Case's actual name; every `Voice` key present, and `sharePunchline` specifically pronoun-free ("I"/"you"/"my"/"your") in all three themes, since the card is read by whoever it's shared with, not the user |
| **Export/import** | Round-trip equality; schema version rejection; malformed JSON is all-or-nothing (DB untouched on failure); import of an ongoing event |
| **ViewModels** | State reduction, undo window, one-ongoing-per-case invariant; Case Edit's required-name, max-length (name/description), and case-insensitive duplicate-name-among-active-Cases validation (self-name exempt when editing) |

## Planned instrumented coverage

| Area | What to cover |
|---|---|
| Room DAOs | CRUD per entity, cascade delete case → events/hunches/triggers, delete-all-cases (and that it doesn't on its own remove the case-independent `tags` table), tag join queries, "events in window" queries, all-cases-with-events query feeding the Big Picture and its tag-nesting variant, backup export/import round-trip and its all-or-nothing rollback on a mid-transaction failure |
| Compose UI | Create Case; one-tap log + undo; detail sheet save with retro time; start/stop flow; theme switch re-words visible strings; hunch nudge appears at 5th event and dismisses permanently; archive a Case from Case Edit (confirm dialog, hidden when creating new); Archived Cases list with unarchive (immediate) and delete-forever (confirm dialog naming the event count); Settings theme selection and info icon, check-in default interval selection and info icon, Support row actions (About navigation, Rate the app/Contact us coming-soon snackbar), Data actions (export/import/delete-all confirm dialog), Developer Mode area hidden by default and shown once unlocked |
| Compose UI — Big Picture | Grid renders real cases/events from `HodithRepository`; empty-state placeholder shows with zero cases; early-days placeholder shows with cases but zero events; day cell tap opens that day's events; month title tap opens the month picker; day-tap → detail-dialog flow still works under Intense's and Bright's bespoke cell styles, not just Plain's; day/week detail rows show timestamp and tags and tapping one opens that Case's detail screen and dismisses the dialog; Cases/Tags trigger chips open their picker dialog and its chip toggles filter the grid live; tag filter chip partial deselection still hides non-matching tagged events and untagged events, but deselecting every tag shows untagged events only; legend row shows the "no Cases selected" note, the "Untagged only"/"All Cases"/"All tags" collapse states, and the trigger chip's own count text updating on toggle; each dialog's bulk "Select all"/"Clear all" toggle flips the whole dimension and its label flips with it; the Tags dialog only offers tags from currently-selected Cases, and deselecting a Case whose tag was the sole active tag selection resets that selection to the new scope instead of silently emptying the grid; trigger/legend chips' BRIGHT theme dispatch (the only test in the app that provides `LocalCardDecorationStyle`) |
| Compose UI — Case Detail Insights | Not-enough-data placeholder below the minimum event count; timeline/heatmap/frequency/rhythm/gaps cards render once past it; trend card hidden below 8 weeks of observation, shown above with direction-aware copy; duration/intensity cards gated on the Case's `durationMode`/`intensityEnabled` rather than on data presence alone; tag breakdown only when an event carries a tag; frequency granularity toggle switches the bucket label format; heatmap "show more months" reveals history beyond the default three-month window |
| Compose UI — Triggers | Empty state vs. populated list; row summary/kind label text; fired badge shown only once `lastFiredAt` is set; enable/disable toggle invokes the callback; delete confirm dialog (confirm and cancel paths); create sheet kind switch (AT_LEAST ↔ SILENT_FOR) swaps the visible fields and drops `windowDays` for SILENT_FOR; Case Detail header's Triggers/Edit icons invoke their callbacks with the Case id |
| Compose UI — About | Version/privacy/licenses sections render their Voice-provided copy; back button invokes the callback; Settings' About row navigates there; version row tap invokes the callback; unlock-countdown and unlocked events show their matching snackbar message |
| Compose UI — Share preview | Story renders taller than Square for identical content (the two formats' minimum shapes actually differ, not just their top beat); format toggle and section checklist invoke their callbacks; Hunch vs. Reality show/hide switch only appears on Story with a resolved Hunch; Duration/Intensity checklist rows only appear when the Case tracks them; Case Detail header's Share icon navigates there |
| WorkManager | Periodic notification-eval job enqueued once as unique work; resolves the real `NotificationEvaluator` via Hilt and completes — the firing behaviour itself is unit-tested (JVM) per this doc's own JVM-first rule, not re-proven at this layer |
| Widgets — chrome & trampoline | List widget title/empty-state tap and Single-case widget's "Case is gone" tap all open `MainActivity`, driven via a real `AppWidgetHost` (`WidgetChromeNavigationTest`) — everything *inside* the List widget's `LazyColumn` row still needs the manual pass (see MANUAL_TEST_PLAN.md's Widgets section for why); the `DETAIL_SHEET` trampoline sheet (`WidgetLogTrampolineActivityTest`) saves a real event and finishes, driven directly since the row that launches it is inside that same unreachable `LazyColumn` |
| Notifications | The real `Notifier` posts a correctly `Voice`-worded, correctly-actioned notification (`NotifierContentTest`) and `NotificationActionReceiver`'s Log (`ONE_TAP` direct, `DETAIL_SHEET` via the trampoline) and All quiet actions update the right data and cancel the notification (`NotificationActionReceiverTest`) — both dispatch through the real `NotificationManager`/a real broadcast rather than `NotificationEvaluator`'s data-dependent Trigger/check-in selection, which stays JVM-only per this doc's own JVM-first rule. A notification's tap target isn't checked here: `PendingIntent` doesn't expose its wrapped `Intent` through any public API |
| Backup file I/O | `ContentResolverBackupFileWriter` round-trips bytes through a real `Uri` via the real `ContentResolver` (`ContentResolverBackupFileWriterTest`) — the one piece of export/import nothing else touches; the system "save to"/"open" picker UI itself stays manual |

## Manual-only journeys (seed list for MANUAL_TEST_PLAN.md)

The original coarse-grained starting point MANUAL_TEST_PLAN.md grew from journey by journey — kept
here for history rather than kept in lockstep with it. MANUAL_TEST_PLAN.md is the authoritative,
current breakdown of what's still manual and what each item's automated coverage is; when the two
disagree, MANUAL_TEST_PLAN.md wins.

Cadence: before every release; full pass before Play submissions.

1. List widget one-tap log on a `ONE_TAP` case — event appears in-app
2. List widget tap on a `DETAIL_SHEET` case — sheet opens via trampoline, saves
3. Single-case widget: start, see ongoing/elapsed, stop
4. Reboot device with an ongoing event — state survives, elapsed correct
5. Trigger notification fires; tap opens the right Case
6. POST_NOTIFICATIONS: deny → in-app banner fallback works
7. Export → wipe app data → import → everything restored. Scope is Room data only (cases/events/tags/hunches/triggers) — Settings prefs (theme, default check-in interval) are a device preference, not investigation data, and are deliberately excluded; round-trip equality, malformed-file rejection, and schema-version rejection are covered by `BackupSerializerTest`/`FakeHodithRepositoryTest`/`RoomHodithRepositoryBackupTest`/`SettingsViewModelTest`, so this manual pass is about the real system file picker, not the underlying logic
8. Timezone change / DST night with events on both sides — day-bucketing in stats, calendar heatmap, and Big Picture stays sane
9. Big Picture with 15+ cases and a year of data — scrolling stays responsive on a mid-range device
10. Check-in notification: several due check-ins arrive as one summary notification, and each notification's tap target opens the right place — see MANUAL_TEST_PLAN.md's Notifications section for what's now covered by `NotifierContentTest`/`NotificationActionReceiverTest` instead
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
