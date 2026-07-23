# HODITH — Cleanup Log

A record of every cleanup pass, newest first (ordering, not dating, marks recency — see CLAUDE.md's no-dates rule). After any significant feature work, actually walk through every applicable item in [CLEANUP_CHECKLIST.md](CLEANUP_CHECKLIST.md) against the real diff first — an entry here records what that walk-through found, it isn't a template to fill in from memory of what changed. Then add a new entry above the previous one: what was found and fixed, what was deferred with a reason, and which sections didn't apply and why.

## Entry format

```
## <branch or feature name>

**Scope:** what work triggered this pass
**Found & fixed:** bullet list (or "nothing found" — that's a valid result)
**Deferred:** bullet list with reasons (or "nothing deferred")
**Docs updated:** SPEC / TESTING / PLAYBOOK sections touched, if any
```

---

## feature/settings-foundation (Phase 4)

**Scope:** Settings foundation — a DataStore-backed `AppTheme` (Serious/Goth/Quirky) selection that
now actually drives the pre-existing `Voice` layer app-wide (previously `LocalVoice` was never
overridden anywhere; every screen silently ran on the hardcoded `SeriousVoice` default), plus
promoting demo-data seeding from a debug-only, silent, first-launch-only mechanism
(`debug/SeedDataInitializer.kt` + the `AppInitializer` plumbing) into a real, user-triggered
`DemoDataSeeder` wired to Settings' "Load demo data" / "Delete all data" (confirm dialog) actions,
available in release builds too. New `SettingsRepository` (Preferences DataStore) + `SettingsViewModel`
+ real `SettingsScreen` (segmented theme picker, text-only preview card, demo-data section) replacing
the `ComingSoonPlaceholder`. `HodithApp.kt` is a new small root composable (`CompositionLocalProvider(
LocalVoice provides voiceFor(theme))` wrapping the nav host) that `MainActivity` now renders instead of
inlining `MaterialTheme`/`Surface` itself. Three scope questions were resolved with the user before
building: the default check-in interval from spec §14 is deferred to Phase 9 (the only phase that
consumes it, so building the picker now would be inert UI); the debug auto-seed is retired outright
rather than kept alongside the new manual action; "Load demo data" always adds another full set of 6
(no dedupe/cap), "Delete all data" gets a standard (not type-to-confirm) dialog.
**Found & fixed:**
- **Real correctness bug, found by manual on-device verification, not the automated suite:** after
  wiring `deleteAllData()` to `caseDao.deleteAll()` (relying on the existing cascade FKs to clear
  events/hunches/triggers), a live device check (`sqlite3` against the running app's DB, per
  TESTING.md's documented method) showed the `tags` table still had 21 rows after "Delete all data."
  Root cause: `tags` isn't scoped to a case — it's a shared, case-independent vocabulary (`TagDao`'s
  `observeAllTags()`), so nothing cascades into it when cases are deleted, only the `event_tags`
  join-table rows do (via `eventId`'s cascade). Left uncaught, this would have silently reappeared as
  autocomplete suggestions (leftover demo-data tag names like "oat-milk") for a user who believed
  they'd wiped everything. Added `TagDao.deleteAll()` and called it alongside `caseDao.deleteAll()` in
  `RoomHodithRepository.deleteAllData()`, plus an instrumented regression test
  (`caseDaoDeleteAll_doesNotOnItsOwnRemoveTags`) documenting *why* the explicit second call is needed,
  not just that it's there.
- **Duplication:** `SettingsScreen`'s theme picker started as its own inline
  `SingleChoiceSegmentedButtonRow` block, near-identical to the private `SegmentedChoiceRow<T>` helper
  already living in `CaseEditScreen.kt` (used there for logFlow/durationMode/check-in) — differing only
  in the `enabled` lambda Settings didn't need. Promoted it to a shared
  `ui/common/SegmentedChoiceRow.kt` and switched both call sites onto it, removing the second copy
  before it was ever committed.
**Considered, not changed:**
- `DataStoreSettingsRepository` (the real DataStore-backed impl) has no direct unit test of its own —
  matches the existing precedent that `RoomHodithRepository` also isn't directly unit-tested; ViewModel
  tests exercise the interface via `FakeSettingsRepository` instead (same shape as
  `FakeHodithRepository`), and the real wiring was verified manually on-device (theme selection
  surviving a force-stop + relaunch, isolated from an unrelated debug-APK reinstall data-reset
  artifact encountered mid-verification that turned out to be a signing quirk, not a persistence bug).
- Considered testing `DemoDataSeeder`'s randomized occurrence generation (`spacedOccurrences`,
  `burstyOccurrences`, etc.) more exhaustively now that it's promoted out of `debug/` and into
  production code (`chore/viewmodel-test-infra`'s entry explicitly deferred testing this exact code
  while it lived in `debug/`, citing no wired test-source-set and imminent removal). Added
  `DemoDataSeederTest` covering the invariants that matter for correctness (six distinct cases, every
  event within the seed span, repeated calls are additive) rather than testing the specific
  random-density shapes, which are cosmetic (Big Picture grid variety) not correctness-bearing.
**Deferred:**
- Default check-in interval (spec §14's off/7/14/30 picker) — not built this phase; Phase 9 is the
  only phase that reads it, so a picker now would persist a value nothing consumes. Recorded directly
  in PROGRESS.md's Phase 4 entry, not just here.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as every
  previous entry.
**Docs updated:** PROGRESS.md (Phase 4 checked off with full description, current-status line
updated). HODITH_SPEC.md §14 (Settings row — added "Load demo data" / "Delete all data," previously
undocumented). DEV_PLAYBOOK.md (Ship Checklist's debug-seed-removal item struck — fully retired, not
just fenced). TESTING.md (Room DAOs row — delete-all-cases and the tags-don't-cascade case). This file.

---

## feature/big-picture (Phase 3)

**Scope:** Production wiring for the Big Picture calendar grid — the flagship view spec'd in §9,
already prototyped and validated on-device as `ui/timeline/CalendarGridPrototype.kt` (committed in
`3faa460`, despite PROGRESS.md's stale "uncommitted" note). Promoted that design to production: a new
`BigPictureViewModel` (mirrors `HomeViewModel`'s `combine`/`stateIn` pattern) sourcing
`HodithRepository.observeActiveCasesWithEvents()` — no new repository/DAO query needed, since that same
flow already carries every active case's `createdAt`, so `earliestMonth` is derived reactively as
`min(createdAt)` rather than a separate query. Moved the grid Composable to
`ui/bigpicture/BigPictureGrid.kt`, wired a `BigPictureRoute`/`BigPictureScreen` split into the nav host
(same pattern as `HomeRoute`/`HomeScreen`), and routed every dialog string (month picker, day/week
detail, empty states) through 10 new `Voice` keys added to all three voices. Resolved two product
decisions with the user before building: intensity/duration stay icon-only, never encoded on the grid
(closing spec §9's "still open" question), and the early-days placeholder is two-tier — zero active
Cases shows the same empty state as Home, at least one Case but zero events shows a distinct "not
enough data yet" placeholder.
**Found & fixed:**
- **Duplication:** the three new dialogs (month picker, day detail, week detail) were each a
  near-identical single-dismiss-button `AlertDialog`, differing only in title/content — exactly the
  shape the pre-existing `ui/common/InfoDialog.kt` already covers (previously used once, by Case
  Edit's info icons). Generalized `InfoDialog`'s `body: String` parameter into a
  `content: @Composable () -> Unit` slot plus an optional `dismissLabel` override (Case Edit's "Got
  it" doesn't read right for a list dialog; these use a new "Close"-style `bigPictureDialogCloseAction`
  instead) and switched all four call sites — Case Edit's existing one plus the three new ones — onto
  it, removing three copies of the same `AlertDialog` boilerplate.
- **Accessibility — tap target size + missing action label:** the per-week chevron that opens the
  week-detail view was a 24dp `Text("›")`, under the 48dp minimum, with no accessible label beyond the
  glyph itself (TalkBack would read "greater-than sign", not what it does). Wrapped it in a 48dp `Box`
  with `Modifier.clickable(onClickLabel = ...)` and a new `bigPictureWeekViewDescription` Voice key;
  `WeekdayHeader`'s leading spacer now shares the same `WEEK_CHEVRON_TOUCH_TARGET` constant so the
  header stays column-aligned with the grid below it. The month-title tap target had the same
  under-48dp-height issue (no explicit height, sized to just its `titleSmall` line) — same fix class
  `feature/case-edit-polish` already applied to the icon-picker's collapse header; added
  `Modifier.heightIn(min = 48.dp)` plus vertical centering.
- **Test gap named in TESTING.md's own pre-existing plan, not yet closed:** the "Stats & visual data
  prep" row already called for "Big Picture calendar-grid day-bucketing (month grid boundaries,
  out-of-month days blank not duplicated)" unit coverage, but `weeksInGrid` — the exact function that
  does this — was `private` and untested. Made it `internal` and added `BigPictureGridTest.kt` (5
  cases: 7-day chunking, mid-week month start padding back to Monday, mid-week month end padding
  forward to Sunday, no padding needed when a month already starts/ends on boundaries, every day of the
  target month appears exactly once with no duplicates).
- **Instrumented tests initially failed with `IllegalStateException: No compose hierarchies found`** —
  a physical device had become attached alongside the emulator mid-branch, the exact scenario
  TESTING.md's Known Environment Issues already documents. Not a regression; worked around by targeting
  the emulator directly (`adb -s emulator-5554 ...`) per that doc's existing instructions, which
  themselves needed no changes.
**Considered, not changed:**
- Weekday header letters (`M T W T F S S`) and the day cell's "+N" overflow badge stay inline, not
  routed through `Voice` — calendar/data-visualization chrome (a locale-fixed abbreviation, a count
  badge), not personality-flavored narrative copy, matching how formatted dates and numeric counts
  elsewhere in the app (event-list timestamps, Home's today/week counts) also sit outside the Voice
  layer.
- `CaseFilterChip`'s ~28–32dp height (icon + `labelSmall` text + 6dp padding) is under the 48dp
  tappable-target guideline, but matches Material 3's own `FilterChip` sizing convention (a
  deliberately compact selection control, not a primary action) — this exact chip shape was already
  reviewed and approved during the prototype's own on-device validation before this branch started;
  not re-litigated here.
- `visibleCaseIds`/`selectedDay`/`selectedWeek`/`showMonthPicker` use plain `remember`, not
  `rememberSaveable` — won't survive rotation/process death. Same reasoning `feature/log-detail-sheet`'s
  entry already gave for `LogDetailSheet`'s draft state: transient UI state, not a big form investment,
  accepted for now.
**Deferred:**
- No instrumented test covers the case-filter-chip toggle or the "+N" overflow badge — the chip toggle
  is hard to assert cleanly since the same case icon renders in both the chip label and day cells, and
  the overflow badge needs 4+ same-day events to trigger, meaningfully more setup than the other six
  interaction tests needed. Verified visually during this branch's manual on-device pass instead of by
  an automated assertion. Revisit if either becomes a source of real bugs.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as every
  previous entry.
**Docs updated:** HODITH_SPEC.md §9 (intensity/duration decision resolved from "still open" to
icon-only, and where that data actually lives instead; two-tier early-days placeholder documented).
TESTING.md (day-bucketing row moved from planned-unit to landed, now covered by `BigPictureGridTest`;
Big Picture instrumented-coverage row trimmed to what's actually tested and its "(Phase 3)" tag
dropped). PROGRESS.md (Phase 3 checked off, current-status line updated, stale "uncommitted prototype"
note corrected). This file.

---

## chore/ci-instrumented-tests

**Scope:** Closed PROGRESS.md's last Housekeeping item — instrumented tests (`connectedDebugAndroidTest`)
had never run in CI, only `./gradlew test` (JVM). Added `.github/workflows/instrumented-tests.yml`:
`reactivecircus/android-emulator-runner` on a cached API 36 emulator, sharded via a new class-level
`@UiTest` annotation (`app/src/androidTest/kotlin/.../testtags/UiTest.kt`) into `repository`
(DAO/`CaseWithEventsTest`, not annotated) and `ui` (the four Compose screen test classes, annotated)
matrix jobs — new test classes land in the right shard by tag, no further workflow edits needed. Also
added a method-level `@Smoke` annotation (`testtags/Smoke.kt`) tagging one representative happy-path
test per existing class (10 total), for a quick local sanity run without the full suite. Both this
workflow and `ci.yml`'s existing unit-test job now report total/passed/failed/skipped counts via
`mikepenz/action-junit-report` (Checks-tab annotations + job summary) — a user-requested addition not
present in the reference implementation (EarnIt's own `instrumented-tests.yml`) this branch's shard/tag
approach was otherwise modeled on.
**Found & fixed:** nothing — this is new CI/test-tagging infrastructure with no existing behavior to
regress; every changed test file only gained an import + annotation, no logic touched.
**Checklist pass:** almost every section is N/A — no composables, ViewModels, Repository/Dao logic,
Voice strings, or domain code touched, only CI config, two new test-only annotations, and doc updates.
Two sections did apply and were actually checked:
- **Repo Hygiene:** `git status` reviewed before staging — only the intended files changed/added,
  nothing secret-shaped in the new workflow YAML (no tokens; uses the default `GITHUB_TOKEN` via
  `permissions:`), no stray untracked files.
- **Tests → "new instrumented tests actually ran on a device before committing":** the annotations
  aren't new tests, but the filtering mechanism they enable is the entire point of this branch, so it
  was verified for real rather than assumed from reading the YAML: ran
  `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=...UiTest` (29
  tests, matching a hand count of the 6 DAO/`CaseWithEventsTest` classes),
  `...annotation=...UiTest` (32 tests, matching the 4 Compose screen classes — 29+32=61 with no overlap
  or gap), and `...annotation=...Smoke` (exactly the 10 confirmed tests) — all green on a local
  `Pixel_6(AVD)` API 36 emulator before any of this was committed.
**Deferred:**
- No CI hook (e.g. `workflow_dispatch`) to run just `@Smoke` or one shard on demand from the Actions UI
  — deliberately scoped out during planning; tags exist for local `./gradlew` filtering only for now,
  revisit if that turns out to matter.
- No per-feature annotations (e.g. `@HomeFeature`) beyond `@UiTest`/`@Smoke` — the suite is already one
  test class per package per screen/DAO entity, so `-Pandroid.testInstrumentationRunnerArguments.package=...`
  already isolates any single feature today with zero extra code; adding a redundant tag was considered
  and rejected during planning.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as previous
  entries.
**Docs updated:** PROGRESS.md (Housekeeping item struck — last open item in that section). TESTING.md
(CI coverage section now describes both workflows and the reporting action; new "Instrumented test
tags" subsection documenting `@UiTest`/`@Smoke` and the package-filter alternative). This file.

---

## chore/viewmodel-test-infra

**Scope:** Closed PROGRESS.md's Housekeeping item of the same name — `HomeViewModel`,
`CaseDetailViewModel`, `CaseEditViewModel`, and `ArchivedCasesViewModel` were covered only by
manual/instrumented testing, not isolated JVM unit tests, because no fake repository seam existed
(a decision recorded back in `feature/quick-log`'s entry). Extracted `HodithRepository` from a
concrete `@Singleton class` into an interface, renamed the Room-backed implementation to
`RoomHodithRepository`, and added a `RepositoryModule` `@Binds` (mirroring `ClockModule`'s existing
pattern) — every ViewModel's constructor was already typed against `HodithRepository`, so this was
a DI-binding change only, no ViewModel code touched. Added a hand-rolled `FakeHodithRepository`
(in-memory, `MutableStateFlow`-backed, same no-mocking-library style as the existing `FakeClock`)
and Turbine as a new `testImplementation` dependency for `StateFlow`/`Flow` assertions. Added real
ViewModel-instantiation test suites for all four (24 tests total), and renamed the three existing
`*ViewModelTest.kt` files that only covered co-located pure functions (`CaseDetailViewModelTest` →
`CaseDetailFormattingTest`, etc. — content unchanged, class renamed to match) to free up the
canonical name for the new suites. A pre-work sweep (with the user) confirmed no other current-phase
production code has a comparable test gap; see Deferred.
**Found & fixed:**
- **Test gap in the test infra itself, caught applying this file's own Tests section to the new
  code, not just the ViewModels it was built for:** `FakeClock` — the one existing precedent for a
  hand-rolled fake with real behavior — has its own direct `FakeClockTest`, but the first draft of
  `FakeHodithRepository` (materially more logic: reactive joins for `CaseWithEvents`/
  `EventWithTags`, case-insensitive archived-name sort, tag find-or-create idempotency, cascading
  delete) had none. A bug there could silently mask a real ViewModel bug or produce a false test
  failure in all 24 dependent tests. Added `FakeHodithRepositoryTest` (11 cases) covering the sort/
  join/cascade/idempotency behavior directly.
- **Duplication:** an identical `awaitLoadedItem()` Turbine helper (skips a `stateIn` StateFlow's
  `isLoading = true` default before the first real `combine` emission) was copy-pasted across three
  of the four new test files, differing only in which `UiState` type it was written against.
  Extracted a single generic `TurbineTestSupport.kt` (`ReceiveTurbine<T>.awaitLoadedItem(isLoading:
  (T) -> Boolean)`) in the `viewmodel` test package; all three call sites now share it.
- ktlint reformatted every new/touched test file (multi-arg calls, trailing-lambda placement) —
  caught by actually running `ktlintCheck` before this entry was written, not assumed clean.
**Deferred:**
- **`data/Converters.kt`'s Room `TypeConverter`s remain untested** — each is a one-line
  `enum.name`/`enum.valueOf()` wrapper; a typo would be caught immediately by any DAO instrumented
  test round-tripping through Room. Considered during the pre-work sweep and judged low value for
  the effort, not overlooked.
- **`debug/SeedDataInitializer.kt`'s pure occurrence-generation functions (`spacedOccurrences`,
  `burstyOccurrences`, `endedAtFor`, etc.) remain untested** — also surfaced in the sweep. It lives
  in `app/src/debug` with no `testDebug` source set wired up to reach it (would need a
  `build.gradle.kts` change), and DEV_PLAYBOOK.md's Ship Checklist already slates the whole file for
  removal before first release. Not worth standing up test infra for code on its way out.
- `ui/timeline/CalendarGridPrototype.kt`'s pure helpers (`weeksInGrid`, `monthLabel`) are untested,
  but that file is Phase 3 prototype-stage work for a phase that hasn't started — out of scope.
- `LogDetailViewModel.kt` turned out not to be an actual `@HiltViewModel` (just pure data
  classes/functions shared by `CaseDetailViewModel`, already fully covered by the pre-existing
  `LogDetailViewModelTest`) — a naming artifact, not a gap; no ViewModel instance exists there to
  test. Noted since PROGRESS.md's item didn't name it but the user asked to include it if in scope.
**Docs updated:** PROGRESS.md (Housekeeping item struck). HODITH_SPEC.md and TESTING.md
reviewed — no changes needed (test-infra work; no user-facing behavior or coverage-scope
description changed). This file.

---

## chore/android-lint

**Scope:** Two housekeeping items from PROGRESS.md, bundled onto one branch since the second is
small: (1) Android Lint (`./gradlew lintDebug`) had never been run — only ktlint's style checks
were enforced. Ran it, triaged every finding, fixed what was real and mechanical, and wired it
into CI (plus into CLAUDE.md's documented local workflow, so `lintDebug` runs locally before
`test`, not just discovered in CI). (2) Extracted DEV_PLAYBOOK.md §1's cleanup checklist into its
own document, `CLEANUP_CHECKLIST.md` — it had grown long enough to deserve one rather than living
inline in the playbook; §1 is now a short pointer to it.
**Checklist pass:** a tooling/config/docs-only branch, so most sections don't apply (no
composables, ViewModels, Repository/Dao code, Voice strings, or domain logic touched). Two items
did apply and were actually checked, not assumed:
- **Spec Review:** the `DataExtractionRules` fix (below) touches backup behavior, which brushes
  against HODITH's "local-only" positioning. Checked HODITH_SPEC.md directly — line 284 already
  states "Android auto-backup enabled — documented on the About screen," and the new
  `dataExtractionRules` XML preserves that same default (full cloud-backup + device-transfer)
  rather than changing it, so no spec divergence.
- **Repo Hygiene:** `git status` reviewed before staging each commit — only the intended files
  changed, nothing secret-shaped, no stray untracked files.
**Found & fixed:**
- **`ModifierParameter` ×2:** `CalendarGridPrototype` and `LogDetailSheet` both declared `modifier:
  Modifier = Modifier` after other optional parameters instead of first. Reordered in both (all call
  sites already use named arguments, so no call-site changes needed).
- **`DataExtractionRules` ×1:** `android:allowBackup="true"` alone is deprecated guidance since
  Android 12 (app's `minSdk` is already 31). Added `res/xml/data_extraction_rules.xml` (full
  cloud-backup + device-transfer, matching the prior implicit default) and referenced it via
  `android:dataExtractionRules` in the manifest.
- **`UseTomlInstead` ×10:** the Compose BOM and its modules were declared inline in
  `app/build.gradle.kts` instead of through `libs.versions.toml`, the one exception to how every
  other dependency in the file is declared. Added `compose-bom` and each Compose module as version
  catalog entries (module version left to the BOM, as usual) and switched both `implementation`/
  `debugImplementation`/`androidTestImplementation` blocks to reference them.
**Deferred:**
- **`MissingApplicationIcon` ×1** — left as a live, unsuppressed warning rather than patched with a
  placeholder. Real app icon design is already an open Ship Checklist item ("App icon that works in
  all three theme contexts") gated on actual design work across all three themes, not something to
  paper over from a lint pass.
- **`AndroidGradlePluginVersion` / `GradleDependency` ×6 / `NewerVersionAvailable` ×5 / `OldTargetApi`
  ×1 (13 warnings)** — all "a newer version exists" nags that directly conflict with versions
  deliberately pinned and documented in DEV_PLAYBOOK.md §5 (e.g. Kotlin held at 2.3.20 until KSP
  publishes a matching release; `hilt-navigation-compose` held back from 1.4.0 because it needs
  `compileSdk 37`, not yet installed). Bumping any of these is its own project gated by that
  section's own upgrade checklist, not a lint fix — disabled these specific checks in `app/
  build.gradle.kts`'s new `lint {}` block with a comment pointing back to §5, so CI stays clean
  without silently re-litigating decisions already made and documented.
**Docs updated:** PROGRESS.md (both Housekeeping items struck; DEV_PLAYBOOK/CLEANUP_CHECKLIST
cross-references updated), TESTING.md (CI coverage paragraph — `lintDebug` now part of the
`ci.yml` chain; checklist cross-reference updated), CLAUDE.md (Commands section + Branch/PR
workflow step 3 — `lintDebug` added to the local check sequence), DEV_PLAYBOOK.md (§1 replaced
with a pointer to the new CLEANUP_CHECKLIST.md), this file.

---

## feature/case-edit-polish (Phase 2, slice 3, PR 6 of 6)

**Scope:** A batch of `CaseEditScreen` UX fixes flagged since `feature/case-archive`: the icon picker
becomes collapsible (collapsed by default when editing, expanded for a new Case, with a
label + icon + chevron summary row); Logging, Duration, and Check-in each get a tappable info icon
opening a plain explanatory dialog; Check-in restyles from radio rows to the same segmented-button
pattern Logging/Duration already use; and a validation fix disabling the Logging control's "One tap"
option whenever `durationMode == MANUAL` and/or `intensityEnabled == true` (`START_STOP` is
unaffected — a one-tap Start/Stop is legitimate). Two open design questions from PROGRESS.md were
resolved with the user before building: an existing Case's `logFlow` **auto-switches** to
`DETAIL_SHEET` the moment it becomes invalid (never auto-restores), and the info affordance is a
**tap-to-open dialog** rather than a long-press tooltip (no precedent for either existed in the
codebase; a touch-only app makes long-press a weaker discoverability bet than a visible icon).
**Found & fixed:**
- **Duplication:** the first pass wired Logging/Duration/Check-in's info icons with three
  near-identical `var showXInfo by remember { ... }` + conditional `InfoDialog` blocks in
  `CaseEditForm`, differing only in which `Voice` strings they used — the exact "three copies
  differing only in which Voice strings and callbacks they wire up" pattern `feature/case-archive`'s
  own entry already flagged once for `ConfirmDialog`. Extracted a shared `SectionWithInfo(label,
  infoTitle, infoBody, infoDescription, content)` composable that owns the dialog-visibility state
  once; all three sections now call it, wrapping their own `SegmentedChoiceRow` (plus, for Check-in,
  the conditional custom-days field) as trailing content. Also shrank `CaseEditForm` itself in the
  process (roughly 114 lines → ~102).
- **Accessibility, caught before commit, not by a device check:** the new info `IconButton` was
  built with an explicit `Modifier.size(24.dp)` override to keep the visible glyph small, which also
  shrinks the button's actual touch target below this file's own "≥48dp×48dp" tappable-target rule.
  Fixed by removing the size override from the `IconButton` (keeping M3's default ~48dp touch area)
  and sizing only the inner `Icon` down to 18dp instead. Same reasoning applied to the icon-picker's
  clickable collapse/expand header row, which had no explicit height and could size down to just its
  `Text`'s line height — added `Modifier.heightIn(min = 48.dp)`.
- **Real correctness bug, found only by manual on-device testing, not by the automated test suite:**
  loading an *existing* Case whose stored `logFlow = ONE_TAP` was already invalid under the new rule
  (the debug seed data's `Migraine` case: `START_STOP` duration + intensity on, set before this
  validation existed) rendered the Logging control showing "One tap" as **both checked and
  disabled** — a contradictory state, and one that would have let a user re-save the same invalid
  combination unchanged since nothing re-validated it. The auto-switch logic only ran from the two
  `onDurationModeChange`/`onIntensityToggle` mutators, not from the entity→UI-state mapping used when
  the screen first loads an existing Case. Fixed by running the same `coerceLogFlow` pure function
  inside `CaseEntity.toUiState()` (made `internal` from `private` so it's directly unit-testable),
  closing the gap for any Case whose invalid state predates this branch rather than arising from an
  in-session edit. Two new `CaseEditViewModelTest` cases cover it directly (constructing a
  `CaseEntity` with the seed data's exact stale combination).
**Deferred:** nothing — full field-by-field `CaseEditScreenTest` coverage remains the same
pre-existing gap noted since `feature/case-archive`; this branch added targeted coverage only for the
interactions it introduced (icon-picker collapse/expand, logFlow enable/disable, info dialog
open/dismiss), matching the file's existing scoping rather than expanding it wholesale.
**Docs updated:** HODITH_SPEC.md §14 (New/edit Case row — collapsible icon picker, info icons,
segmented Check-in, the One-tap validation/auto-switch rule); this file; PROGRESS.md (checked off
this branch, Phase 2 slice 3 now fully landed).

---

## feature/case-archive (Phase 2, slice 3, PR 5 of 6)

**Scope:** Archive and hard-delete for Case, plus a new Archived Cases view to manage both — corrected
mid-flight from PROGRESS.md's original archive-only framing (§ this file's own precedent: scope
corrections happen before code, not after). Adds an Archive action + confirm dialog to Case Edit's
header (existing cases only, navigates to Home on confirm); a new Archived Cases screen (reached via a
text link on Home, shown only once at least one Case is archived) listing archived Cases with
immediate Unarchive and a confirm-gated Delete forever (names the event count, cascades to
events/hunches/triggers); `CaseDao.observeArchivedCasesWithEvents` backing both.
**Found & fixed:**
- **Icon availability:** `material-icons-core` (the project's curated ~50-icon set, already known to
  lack a `Stop` icon per `feature/start-stop`'s entry below) also has no Archive/Unarchive/Inventory
  glyph — confirmed by inspecting the actual jar rather than assuming. Substituted `Icons.Filled.ExitToApp`
  (archive, via its `AutoMirrored` variant — the plain one is deprecated in this Compose BOM), `Icons.Filled.Refresh`
  (unarchive), `Icons.Filled.Delete` (hard-delete, already the app's established destructive glyph
  from the event-delete flow). `contentDescription` carries the real meaning in each case, same
  reasoning as the prior `Stop`→`Done` substitution.
- **Duplication:** this branch's two new confirm dialogs (archive case, delete case forever) would have
  been near-verbatim copies of the pre-existing `DeleteEventConfirmDialog` in `LogDetailSheet.kt` — same
  `AlertDialog` shape (title/body/confirm `TextButton`/cancel `TextButton`), differing only in which
  `Voice` strings and callbacks they wire up. Extracted a shared `ConfirmDialog` (`ui/common/ConfirmDialog.kt`)
  and switched all three call sites (including the pre-existing one) to use it, removing three copies of
  the same boilerplate down to one.
- Adding a required `onOpenArchivedCases` parameter to `HomeScreen`/`HomeRoute` (for the new Home link)
  broke the existing `HomeScreenTest.kt` call site at compile time — fixed by threading the parameter
  through with a `{}` default in the test helper, same pattern the file already uses for its other
  callbacks.
- **Composable over the ~150-line guideline:** the archive icon/dialog additions pushed `CaseEditScreen`'s
  main composable to 156 lines (the guideline `feature/start-stop`'s cleanup pass already applied once to
  `LogDetailSheet`). Extracted the form body (every field from name through the save button) into a
  private `CaseEditForm` composable, matching this file's own existing pattern of pulling out
  `IconChoice`/`ToggleRow`/`CheckInOptionRow`/`SegmentedChoiceRow`. `CaseEditScreen` itself is now ~74
  lines (state, top bar, archive dialog, and a single call into the form).
- A deprecation warning surfaced during `./gradlew test`: `Icons.Filled.ExitToApp` is deprecated in this
  Compose BOM in favor of `Icons.AutoMirrored.Filled.ExitToApp`. Switched to the `AutoMirrored` variant,
  matching how `ArrowBack` is already used in the same file.
- ktlint caught an unused `Arrangement` import in `ArchivedCasesScreen.kt` and a wrapped function body
  in `Voice.kt` that fit on one line — both were only caught because `ktlintCheck` was actually run
  before this entry was written, not assumed clean.
**Considered, not changed:**
- `ArchivedCasesViewModel.unarchive` and `CaseEditViewModel.save` both recompute a fresh `sortOrder` via
  the same one-line `repository.observeActiveCases().first().size`. Two occurrences of a one-liner,
  same reasoning `feature/start-stop`'s entry already gave for not sharing `HomeViewModel`/
  `CaseDetailViewModel`'s near-identical `stopEvent`/`dismissStalePrompt` bodies — extracting a helper
  for two one-line call sites is net-negative indirection.
- `CaseDao.observeArchivedCasesWithEvents()` looks like it overlaps with `observeActiveCasesWithEvents()`
  (both are "cases with events, filtered by `archived`"), but the two also differ in `ORDER BY` (`sortOrder`
  vs `name COLLATE NOCASE`) — Room can't parameterize which column to sort by without raw SQL, so a single
  parameterized query would trade a clear intent-revealing name for a con­ditional that's harder to read.
  Left as two queries.
- `ArchivedCaseListItem` and `HomeCaseListItem` share the same icon-then-column-then-trailing-controls Row
  shape, but Home's row also carries the ongoing indicator, ticking elapsed time, and stale-prompt banner
  that Archived Cases has no equivalent of — forcing a shared component would need enough conditional
  slots that it wouldn't read as simpler than two separate, short composables.
**Deferred:**
- No ViewModel-level unit test for `CaseEditViewModel.archive()` or
  `ArchivedCasesViewModel.unarchive`/`deleteForever` — same pre-existing gap already flagged for
  `HomeViewModel`/`CaseDetailViewModel` (PROGRESS.md): no `FakeHodithRepository` seam exists yet, that's
  its own planned dedicated branch. This branch's contribution is covered instead at the pure-function
  layer (`archivedCaseRows`, JVM-only) plus Compose UI tests asserting the callbacks fire correctly.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as previous
  entries.
**Docs updated:** HODITH_SPEC.md §5 (Case table — hard-delete's cascade/irreversibility note) and §14
(Home, New/edit Case, and a new Archived Cases table row). PROGRESS.md (`feature/case-archive` bullet
corrected from archive-only to the actual three-part scope, before any code was written). TESTING.md
(instrumented test count 42→54; new Compose UI coverage bullet for archive/unarchive/delete-forever).

---

## feature/start-stop (Phase 2, slice 3, PR 4 of 6)

**Scope:** Start/Stop + ongoing indicator + 24h prompt (spec §6) — the `START_STOP` duration mode was selectable on Case Edit since `feature/case-crud` but nothing started, stopped, or displayed an ongoing event anywhere. Adds: Start (reuses `quickLogOneTap` for `ONE_TAP` cases; for `DETAIL_SHEET` cases, a new **End** section in `LogDetailSheet` where leaving it "Ongoing" and saving *is* the start action), an always-immediate Stop action (no sheet, regardless of `logFlow`), a live-ticking ongoing indicator on both Home and Case Detail, and the 24h stale-prompt banner (persisted dismissal, re-arms after another 24h) — also on both screens per user decision during planning. New shared pure logic (`viewmodel/OngoingEvent.kt`: `ongoingEventIn`, `isStaleOngoing`, `formatElapsedDuration`) and shared UI (`ui/common/OngoingIndicator.kt`, `ui/common/Ticker.kt`). Schema bump to v3 (`EventEntity.staleNudgeDismissedAt`). Mid-branch, a user-reported gap was also fixed: finished duration events (any mode, not just `START_STOP`) never showed their duration anywhere in the event list, despite it being tracked — added a new `eventDurationLabel` Voice key and duration display in `eventDetailSummary`.
**Found & fixed:**
- **Duplication + complexity, together:** `LogDetailSheet`'s main composable grew to 186 lines (over the ~150-line guideline `feature/log-detail-sheet` had already brought it under once) by adding the end-time date/time pickers as a near-verbatim copy of the existing start-time pickers — same two dialogs, same `applyPickedDate`/`applyPickedTime`/clamp logic, differing only in which `LogDraft` field they read/write. Extracted a shared `DateTimePickers(value, ..., onValueChange)` composable used by both, and pulled the inline tags `Column { Text(...); TagEditor(...) }` block out into a `TagsSection` matching the file's existing `TimeSection`/`IntensitySection` extraction pattern. Brought the main composable down to 156 lines — still a hair over the guideline, but every remaining piece is already its own named section; splitting further would be indirection, not clarity.
- **Dead code:** `HodithRepository.getOngoingEvent` had zero production callers — Home/Case Detail derive ongoing status from the event list they already observe (`ongoingEventIn`), same pattern as `homeCaseRows`'s existing today/week counts, so the repository wrapper was never needed. Removed, matching `feature/log-detail-sheet`'s precedent for `observeEventsForCase`: the underlying `EventDao.getOngoingEvent` primitive stays, since `EventDaoTest` still exercises it directly and it may be useful later (e.g. a Phase 7 widget trampoline wanting a single lookup without the full list).
- **Icon availability:** `Icons.Filled.Stop` doesn't exist in this project's `material-icons-core` dependency (a curated ~50-icon legacy set, not the full Material icon catalog — confirmed by inspecting the actual jar rather than assuming). Used `Icons.Filled.Done` instead (a checkmark reads as "mark this complete," closer to Stop's intent than the only other close alternative, `Close`, which already means "remove" elsewhere in the app, e.g. tag removal). Adding `material-icons-extended` for one icon was considered and rejected — meaningful APK size cost for a single glyph, not part of the plan, and accessibility already carries the real meaning via `contentDescription`.
- **Compose UI test gotcha:** `ExtendedFloatingActionButton`'s text and icon merge into one semantics node, so `onNodeWithText` couldn't find the FAB's label without `useUnmergedTree = true` — cost two failing instrumented tests until diagnosed; documented here so it isn't re-discovered from scratch.
- Removed two incorrect/no-op assertions from an early draft of `CaseDetailScreenTest` (inverted `assertDoesNotExist`/`assertExists` polarity, and a lookup with no assertion at all) before they were ever committed.
**Considered, not changed:**
- `HomeViewModel.stopEvent`/`dismissStalePrompt` and `CaseDetailViewModel.stopEvent`/`dismissStalePrompt` are near-identical one-line `repository.updateEvent(event.copy(...))` bodies, duplicated across the two ViewModels rather than shared. No base ViewModel class exists in the codebase to hang a shared implementation off, and each is a single line — extracting a helper for four one-line functions would be net-negative indirection (CLAUDE.md: "three similar lines is better than a premature abstraction"). Left as-is.
**Deferred:**
- **No ViewModel-level test exercises `stopEvent`, `dismissStalePrompt`, or `onQuickLogTap`'s stop-vs-start routing decision** — same gap `feature/quick-log` and `feature/log-detail-sheet` already flagged for `quickLogOneTap`/`saveEvent`/etc., closed only by the planned dedicated `HodithRepository` interface + `FakeHodithRepository` branch (PROGRESS.md, Phase 2). This branch's contribution to that invariant is fully covered at the pure-function layer instead (`OngoingEventTest`'s `ongoingEventIn`/`isStaleOngoing` cases), plus the Compose UI tests asserting the *callback* fires correctly.
- Retro-log's date/time picker restrictions and tag add/remove/delete-from-sheet still aren't driven by an instrumented test (`CaseDetailScreenTest` covers the start/stop-specific scenarios this branch needed, not the full sheet) — `TESTING.md`'s deferral note updated to reflect the narrower remaining gap rather than closed outright.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as previous entries.
**Docs updated:** HODITH_SPEC.md §6 (Start/stop, retro-log, and event-list bullets — persisted 24h re-arm behavior, both-screens placement, duration-in-list). PROGRESS.md (slice-3 sub-branch 4 marked done with full description, "Current status" pointer moved to `feature/case-archive`). TESTING.md (Deferrals section narrowed instead of closed outright; Known Environment Issues' API 36 gap marked verified — 42/42 instrumented tests green on an API 36 AVD).

---

## feature/quick-log (Phase 2, slice 3, PR 3 of 6)

**Scope:** One-tap + Home wiring (spec §6, §14) — a per-row quick-log tap target on Home, distinct from the row's own tap: `ONE_TAP` cases insert an Event at `now` immediately with an Undo snackbar; `DETAIL_SHEET` cases open the shared `LogDetailSheet` right from Home. New `Voice` keys for the button description, undo message, and undo action. Also lands the repo's first Compose UI instrumented test (`HomeScreenTest`), and a small drive-by: new Cases now default to `logFlow = DETAIL_SHEET` instead of `ONE_TAP` (`CaseEditUiState`), flagged in PROGRESS.md as upstream context for `feature/case-edit-polish`'s validation-fix bullet.
**Found & fixed:**
- **`LaunchedEffect` key:** the snackbar-collecting effect was keyed on `quickLogUndo` (the Flow instance itself) rather than `Unit`. It happened to work because the Flow reference is stable across recompositions (a `val` on the Hilt-scoped ViewModel), but keying on it signals "restart when this changes" when the actual intent is "collect once for this composable's lifetime." Changed to `LaunchedEffect(Unit)`.
- **Deprecation warning:** `HomeScreenTest` used the deprecated `androidx.compose.ui.test.junit4.createComposeRule()`, flagged at compile time with a note that v2 uses `StandardTestDispatcher` instead of `UnconfinedTestDispatcher`. Switched to `androidx.compose.ui.test.junit4.v2.createComposeRule` and reran all 4 tests on the emulator to confirm the dispatcher change didn't affect the `runOnIdle`/`waitUntil` synchronization already in use — no warning, tests still green.
- **Environment issue found and fixed, not just documented:** Compose UI instrumented tests initially crashed on *every* connected device/emulator with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`, thrown inside Espresso's own startup before any test code runs. Root-caused to the `espresso-core` version `androidx.compose.ui:ui-test-junit4` pulls in transitively; pinned explicitly to 3.7.0 via `libs.versions.toml`, which resolved it.
- **Manual on-device verification surfaced that scripted `adb shell input tap` is not a reliable way to test the Undo snackbar action** — repeated attempts to tap "Undo" via raw coordinates (even ones matching `uiautomator dump`'s reported bounds exactly) consistently failed to register as the snackbar's action click, always resolving via timeout instead. This looked like a real product bug until manual (real finger) testing on-device confirmed one-tap logging and tap-plus-then-Undo both work correctly and the record disappears from the list as expected. No code fix needed; recorded here so a future session doesn't re-spend time suspecting the feature instead of the test tooling.
**Deferred:**
- **No direct unit test exercises `HomeViewModel`'s new repository-calling methods** (`quickLogOneTap`, `openLogSheet`, `saveLogSheetEvent`, `undoQuickLog`) — coverage is `HomeViewModelMappingTest` (pure mapping), `VoiceTest`, and `HomeScreenTest` (which drives the stateless UI contract with stubbed callbacks, not the real ViewModel). This matches the existing precedent set by `CaseDetailViewModel.saveEvent`, which is also untested at the ViewModel level — no fake-`HodithRepository` test pattern exists in the repo yet. Closed the loop for this branch via manual on-device verification (screenshots + direct SQLite queries against the live app DB) instead of introducing that pattern unilaterally. **Decision made, not just deferred:** a dedicated follow-up branch will extract a `HodithRepository` interface + hand-written fake and add JVM unit tests for both `HomeViewModel` and `CaseDetailViewModel` at once — chosen over an instrumented-test alternative specifically because JVM tests already run in CI today. Recorded in PROGRESS.md's Phase 2 section.
- CI wiring for instrumented tests (`connectedDebugAndroidTest`) is explicitly **not** part of this branch — planned as its own `chore/ci-instrumented-tests` follow-up per PROGRESS.md's CI section, since it's tooling/CI work, not the quick-log feature.
- `gradle/gradle-daemon-jvm.properties` remains untracked in the working tree, unrelated to this branch — same as previous entries, its own small branch is planned.
**Docs updated:** PROGRESS.md (slice 3 sub-branch 3 description, CI section's instrumented-tests follow-up plan, `case-edit-polish`'s new default-`logFlow` bullet). TESTING.md (Deferrals section — `HomeScreenTest` stands up the first Compose UI instrumented test instead of `feature/log-detail-sheet`'s screen; new Known Environment Issues entries for the `espresso-core` pin and the scripted-adb-tap limitation, including the workaround for targeting a single device when more than one is attached).

---

## feature/log-detail-sheet (Phase 2, slice 3, PR 2 of 6)

**Scope:** Shared `LogDetailSheet` bottom sheet (time via date/time pickers, intensity, MANUAL duration, note, tag chips with autocomplete), wired into Case Detail's retro-log FAB and tap-to-edit. New `EventWithTags` (Room `@Relation`/`Junction`) so the event list shows each event's own tags without a separate lookup; debug seed data expanded with realistic notes/tags per case; event list rows now show weekday + date (year omitted when current) + time.
**Found & fixed:**
- **Complexity — oversized composable:** `LogDetailSheet`'s body was ~208 lines, over the ~150-line guideline. Split into `SheetHeader`, `TimeSection`, `IntensitySection`, and three dialog composables (`LogDetailDatePickerDialog`, `LogDetailTimePickerDialog`, `DeleteEventConfirmDialog`); the main function is now state + a flat list of section calls.
- **Efficiency — redundant clock reads:** `CaseDetailScreen` called `nowMillis()` three separate times per composition (FAB, each row, each row's edit request). Computed once at the top and reused.
- **Dead code:** `HodithRepository.observeEventsForCase` had zero production callers left once `CaseDetailViewModel` switched to `observeEventsWithTagsForCase` (a strict superset). Removed the repository wrapper; kept the underlying `EventDao.observeEventsForCase` since `EventDaoTest`/`CaseDaoTest` still exercise it directly as a DAO-level primitive.
- **Accessibility — tap target size:** `IntensityChoice` circles were 40dp, under the 48dp minimum (same class of issue as `feature/case-crud`'s icon-picker cells below). Bumped to 48dp via a named `INTENSITY_CHOICE_SIZE` constant, matching `CaseEditScreen`'s `ICON_CHOICE_SIZE` precedent.
- **Compose layout bug (user-reported):** the tag input field chained both `.fillMaxWidth()` and `.weight(1f)` — an anti-pattern that gives the field ambiguous width constraints across measurement passes. This was the root cause of two reported symptoms: typed text not appearing until the field lost focus, and the field's height changing on blur. Fixed by dropping the redundant `fillMaxWidth()`.
- **Keyboard-covers-field bug (user-reported):** the sheet's content `Column` had no `imePadding()` and wasn't scrollable, so fields near the bottom (tags, note) sat behind the keyboard once it opened. Added `.verticalScroll(rememberScrollState())` and `.imePadding()`.
- **Data-integrity bug (user-reported):** saving an edited event under a duration mode with no editable duration control yet (`NONE`/`START_STOP`) silently nulled the event's existing `endedAt`, permanently destroying a real duration logged while the case was still `START_STOP`. Fixed by threading `existingEndedAt` through `LogDraft`; only `MANUAL` mode's control can actually change it now.
- **Missing validation (user-reported):** nothing stopped saving an event with a future `occurredAt`. Fixed with `DatePicker`'s `selectableDates`, a same-day time clamp after the time picker, and an authoritative `coerceAtMost(now)` at save time. `endedAt` from a MANUAL duration is deliberately *not* clamped — a stated/expected duration at logging time ("started now, runs about 2 hours") is legitimate even if it projects past `now`.
- **UX (user-requested):** event delete moved from a per-row icon+dialog in the Case Detail event list to the log sheet's header (top-right, edit-mode only) — keeps the list scannable; delete now lives where you're already looking at the one event.
- **Ordering fix (user-requested):** weekday moved to after the date/time in the event-row timestamp, with a positional regression test (`indexOf` check) so this can't silently flip back.
**Deferred:**
- No Compose UI instrumented test drives the log sheet end-to-end yet (save with retro time, tag add/remove, delete-from-sheet). Covered so far by unit tests for all the pure logic and instrumented DAO tests for the new relation query, but no Compose UI test exists anywhere in the repo yet — deferred until `feature/quick-log` or `feature/start-stop` stands up that pattern for the first time. Logged in TESTING.md's Deferrals section.
- `LogDetailSheet`'s in-progress state (`draft`, `tagInput`, picker visibility) uses plain `remember`, not `rememberSaveable` — won't survive a rotation or process death mid-edit. `LogDraft` isn't trivially Parcelable and this is a quick bottom-sheet action, not a big form investment (contrast with `CaseEditViewModel`'s `SavedStateHandle`-backed state). Accepted for now; revisit if it causes real friction.
- Tags-and-weekday-always-on (no per-user Settings toggle) — deliberately not built now to avoid standing up DataStore/Settings-screen infrastructure early for a display preference that may not need to be optional. Flagged in PROGRESS.md and HODITH_SPEC.md §6 for reevaluation once Settings (Phase 9) gets real content.
- Two more Case Edit screen gaps surfaced during manual testing, out of scope for this branch (different screen, already-merged `feature/case-crud`): Case removal (no archive/delete UI at all) and a batch of form UX fixes (collapsible icon picker, info icons, Check-in restyle, One-Tap `logFlow` option needs disabling when duration is MANUAL and/or intensity is tracked). Both recorded as new planned branches in PROGRESS.md (`feature/case-archive`, `feature/case-edit-polish`) rather than bolted on here.
**Docs updated:** HODITH_SPEC.md (§6 — event list now describes what each row shows, flagged for the Settings-toggle reevaluation). PROGRESS.md (slice-3 sub-branch breakdown updated to reflect the actual 4→6 branch split, two new branches added, tags/weekday note). TESTING.md (Deferrals section — Compose UI gap for the log sheet).

---

## feature/case-crud (Phase 2, slice 2 of 3)

**Scope:** New/Edit Case screen (§14 core fields: name, optional description, icon picker, logFlow, durationMode, intensity toggle, pinned toggle, check-in override), `CaseEntity.description` field + schema bump to v2, Home's FAB (create) and row-tap (edit) wired to it. Skippable Hunch step deliberately out of scope — see Deferred.
**Found & fixed:**
- **Duplication:** the logFlow and durationMode pickers were two near-identical inline `SingleChoiceSegmentedButtonRow` blocks differing only in the enum type and options. Extracted a shared generic `SegmentedChoiceRow<T>` composable.
- **Accessibility — icon-only button:** the screen's back `IconButton` had `contentDescription = null`. That null pattern is correct for the bottom-nav items (precedent in `feature/home-screen`'s entry below) because their visible label already names them — but this back button has no adjacent label, so `null` would leave it unannounced to TalkBack. Added a new `backButtonDescription` Voice key and wired it in.
- **Accessibility — selection conveyed by color alone:** the icon-picker grid and the check-in radio rows only showed selection via a background-color change, invisible to TalkBack. Switched both to `Modifier.selectable(selected, onClick, role = Role.RadioButton)` on the row/cell plus `Modifier.selectableGroup()` on their containers, so selected state is announced. `RadioButton`'s own `onClick` set to `null` (the recommended pattern) to avoid a duplicate, differently-scoped click target on top of the row's.
- **Tap target size:** the icon-picker cells were 44dp, under the 48dp minimum. Bumped to 48dp.
- **Test gap:** `checkInDaysFor` (DEFAULT/CUSTOM/OFF → nullable day count mapping) had no coverage. Made `internal` and added five cases including blank and zero custom-input fallback.
- **Deprecation:** `fallbackToDestructiveMigration()` is deprecated in this Room version; switched to the `dropAllTables = true` overload.
- **Instrumented tests verified:** `CaseDaoTest`'s new `description` round-trip case, plus the full `connectedDebugAndroidTest` and `test` suites, run and passed on a physical device — closes the gap flagged mid-review (DEV_PLAYBOOK §1's "ran on a device" check is separate from "compiles").
**Deferred:**
- **Hunch step:** §14 describes the New/Edit Case flow ending in a skippable Hunch step. Moved to its own follow-up branch — Hunch creation is new surface area (direction/expectedCount/expectedPer) beyond Case CRUD's core fields, and splitting it keeps this PR reviewable. Recorded in PROGRESS.md.
- **Row-tap → Edit Case is interim wiring**, not the final design: Case Detail doesn't exist until `feature/logging-flows`. Documented in PROGRESS.md with the intended end state (row-tap → Case Detail, Edit moves to its header, Home gets its own quick-log button per row).
- **Check-in "Custom" with a blank/zero day count** silently falls back to the same result as "Use default" rather than blocking save with a validation error. Deliberate simplification, not a tracked gap — revisit only if it causes real confusion.
**Docs updated:** PROGRESS.md (slice 2 scope corrected to reflect the Hunch-step deferral, new "Interim UX wiring" note). HODITH_SPEC.md not touched — the target screen design is unchanged, only the build sequencing differs.

---

## feature/home-screen (Phase 2, slice 1 of 3)

**Scope:** First Phase 2 slice — Navigation Compose scaffold (bottom nav: Home · Big Picture · Settings), read-only Home screen wired to real `HodithRepository` data via a new `HomeViewModel`, placeholder Big Picture/Settings destinations, new `Voice` keys, `description` field added to the Case spec (§5, §14) after a product discussion ruled out a separate Case-level valence field.
**Found & fixed:**
- **Duplication:** `BigPictureScreen.kt` and `SettingsScreen.kt` were identical placeholder bodies (Box + centered Text reading `comingSoonPlaceholder`). Extracted a shared `ComingSoonPlaceholder` composable (`ui/common/ComingSoonPlaceholder.kt`); both screens now delegate to it.
- **Accessibility:** `NavigationBarItem`'s `Icon` had `contentDescription` set to the same text as its visible `label`. Since the label is already shown (not an icon-only button), that would make TalkBack announce the name twice. Set `contentDescription = null` with a comment explaining why, per Material accessibility guidance for icon+label nav items.
- **Test gap (caught by a question, not the checklist pass):** `HomeViewModel`'s today/this-week boundary math (`startOfToday`/`startOfWeek` cutoffs) had no unit coverage. Extracted it to a pure top-level `homeCaseRows(casesWithEvents, nowMillis)` function and added `HomeViewModelMappingTest` covering the inclusive/exclusive boundary at both cutoffs, an empty-events case, and identity-field mapping.
- **Dependency pin:** `hilt-navigation-compose` 1.4.0 (and its transitive `androidx.lifecycle` bump) requires `compileSdk 37`, not installed and out of scope for this branch. Pinned to 1.2.0 instead; recorded in PROGRESS.md as a decision to revisit alongside a deliberate SDK bump, not silently.
**Deferred:**
- `HomeViewModel` computes today/this-week counts by filtering each Case's full in-memory event list (from `observeActiveCasesWithEvents()`) rather than using `EventDao.eventsInWindow`'s SQL-side windowing — a literal read of DEV_PLAYBOOK §1's "Dao query duplicated with a Kotlin-side filter" check. Deferred: `eventsInWindow` is a one-shot suspend query, not reactive, and per-case event volumes are small (personal habit-tracking data), so pushing this into SQL isn't worth the added complexity yet. Revisit if event counts per Case grow large enough for this to matter.
- No instrumented Compose UI test added for Home's rendering. TESTING.md's planned Compose UI coverage for Home is bundled with Create Case / one-tap-log / detail-sheet interactions (slices 2–3); writing one now against a read-only screen with no `onClick` handlers risks being rewritten wholesale once those land. Revisit once slice 2 or 3 adds interaction.
**Docs updated:** HODITH_SPEC.md (§5 `description` field, §14 New/Edit Case field list). PROGRESS.md (slice 1 marked done, data-model decision on Case valence recorded, `hilt-navigation-compose` pin documented).

---

## feature/big-picture (Phase 2/3 rescope)

**Scope:** Re-scoping pass after the calendar-grid pivot: swapped Case CRUD and Big Picture phase order (pinch-zoom's de-risking rationale for building Big Picture first no longer applies), deleted the retired row/dot Big Picture screen and its domain math rather than holding it for reuse, and reconciled every doc that still referenced the old design or the old phase numbers.
**Found & fixed:**
- **Dead code:** all row/dot production code and tests deleted — `domain/timeline/{TimelineLayout,TimelineWindowMath,TimelineAxis}.kt`, `ui/timeline/{TimelineGestures,TimelineScreen,TimelineViewModel}.kt`, and their unit/instrumented tests (~1,067 lines). Previously flagged as "candidates for reuse" for Phase 6's per-case dot timeline; resolved as delete-not-hold since it was shaped around a multi-case shared-row layout, so reuse for a single-case screen four phases out was speculative.
- **Decoupling fallout from that deletion:** `Voice.kt`'s `timeRangeLabel(ZoomLevel)` removed (no zoom levels left to label), `VoiceTest.kt` updated to match; `SeedDataInitializer.kt`'s `MIN_INTENSITY`/`MAX_INTENSITY` re-homed as local private constants (borrowed from the deleted `TimelineLayout.kt`, not conceptually tied to it); `MainActivity.kt` no longer wires the deleted `TimelineGrid`/`TimelineViewModel`.
- **Stale docs:** DEV_PLAYBOOK.md's Ship Checklist referenced "the Phase 2 seed-data mechanism" — that's Phase 3's now; reworded without a phase number so it can't rot the same way again. This file's own Phase 1 entry referenced "Phase 3's job" for ViewModel concerns that are now Phase 2's. TESTING.md's Big Picture coverage rows still described dot/bar bucketing, pinch-zoom responsiveness, and "tap a dot" navigation from the retired design — rewritten for the calendar grid's actual day/week/month/filter-chip interactions. HODITH_SPEC.md §9's closing note pointed at "domain-layer code that may still be reusable" — that code no longer exists; note corrected.
**Deferred:**
- `MainActivity.kt`'s temporary placeholder Compose text ("HODITH") is an inline string, not routed through the `Voice` layer — normally a hard violation of the #1 hygiene rule, but this is throwaway scaffolding pending Phase 2's Home screen, not shipping product copy. Not worth plumbing a `Voice` key for text that gets deleted within the next phase.
- `gradle/gradle-daemon-jvm.properties` sits untracked in the working tree, unrelated to this branch's work — flagged for awareness, not resolved here.
**Docs updated:** PROGRESS.md (phase order swap, current status, resolved open decisions), TESTING.md (Big Picture coverage rows), DEV_PLAYBOOK.md (Ship Checklist wording), HODITH_SPEC.md (§9 closing note), this file (Phase 1 entry's stale phase reference).

---

## feature/room-data-layer (Phase 1)

**Scope:** Room entities/DAOs/`HodithRepository`, JVM `Clock` abstraction, and their tests — first product code in the repo.
**Found & fixed:**
- Migrated `build.gradle.kts` files from inline dependency versions to `gradle/libs.versions.toml`, since this phase roughly doubled the dependency count (Room, coroutines, AndroidX test, Hilt testing).
- All six instrumented DAO test classes repeated the same `Room.inMemoryDatabaseBuilder(...).build()` boilerplate in `@Before` — extracted to a shared `createInMemoryDatabase()` helper in `TestFixtures.kt`.
- Enums stored as `String` (not ordinal `Int`) via `Converters.kt` — safer against future reordering/insertion of enum values.
- Tag cross-ref inserts use `OnConflictStrategy.IGNORE` so `HodithRepository.addTagToEvent` is idempotent.
**Deferred:**
- No domain logic beyond `Clock` exists yet (verdict/trigger/stats engines are Phase 5+), so most of DEV_PLAYBOOK §1's Decoupling/Complexity checks don't yet apply — revisit at that phase.
- `HodithRepository` mirrors the DAOs closely with no additional orchestration beyond tag find-or-create; ViewModel-level concerns (undo window, one-ongoing-per-case invariant) are explicitly Case CRUD's job per TESTING.md.
**Docs updated:** TESTING.md — status line updated (Phase 1 landed), known-environment-issues note replaced with the actual verification result (25/25 instrumented DAO tests passed on a Pixel 6 AVD, API 34; the Android 16/API 36 compatibility gap flagged at Phase 0 remains unverified since no API 36 image was used). PROGRESS.md — Phase 1 checked off.

---

## chore/project-scaffold (Phase 0)

**Scope:** Repo init and Gradle/Android project scaffold — no product code yet, just the empty app skeleton and tooling.
**Found & fixed:** nothing to fix; no product logic exists yet for most checklist categories to apply to (duplication, decoupling, complexity, accessibility are all N/A pre-feature-code). Confirmed `git status` clean, `.gitignore` correctly excludes `local.properties`/`build/`/`.gradle/`, no secret-shaped files staged.
**Deferred:**
- Dependency versions are inline literals in `build.gradle.kts` rather than a Gradle version catalog (`libs.versions.toml`). Matches DEV_PLAYBOOK §5's matrix directly for now; revisit once the module/dependency count grows enough that duplication becomes a real problem.
- Instrumented-test environment (the documented Android 16 compatibility gap in TESTING.md) is still unverified — Phase 0 only exercised the JVM `test` task (no source, ran clean) and `ktlintCheck`; no emulator was used. Verification happens when Phase 1 lands the first DAO tests.
**Docs updated:** DEV_PLAYBOOK.md §5 tooling matrix updated in place with verified versions (AGP 9.2.1, Hilt 2.60, Compose BOM 2026.06.01, Kotlin held at 2.3.20 pending KSP support, Room decision recorded). HODITH_SPEC.md and TESTING.md reviewed — no divergence, no changes needed (no product or test code landed this phase).
