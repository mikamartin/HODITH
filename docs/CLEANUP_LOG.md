# HODITH — Cleanup Log

A record of the 5 most recent cleanup passes, newest first (ordering, not dating, marks recency — see CLAUDE.md's no-dates rule). After any significant feature work, actually walk through every applicable item in [CLEANUP_CHECKLIST.md](CLEANUP_CHECKLIST.md) against the real diff first — an entry here records what that walk-through found, it isn't a template to fill in from memory of what changed. Then add a new entry above the previous one: what was found and fixed, what was deferred with a reason, and which sections didn't apply and why.

**Retention:** only the 5 newest entries are kept here. When adding one pushes the count past 5, delete the oldest entry in the same commit — its content still lives in git history and is reachable via `git log -- docs/CLEANUP_LOG.md`.

## Entry format

```
## <branch or feature name>

**Scope:** what work triggered this pass
**Found & fixed:** bullet list (or "nothing found" — that's a valid result)
**Deferred:** bullet list with reasons (or "nothing deferred")
**Docs updated:** SPEC / TESTING / PLAYBOOK sections touched, if any
```

---

## fix/duplicate-case-warnings

**Scope:** Two related user-reported bugs: reloading demo data duplicated the seed set instead of replacing it, and Case Edit's duplicate-name check only looked at active Cases, letting a new/edited Case silently take an archived Case's name with no warning at all. Manual testing of the first implementation surfaced two further bugs: the duplicate-name check was skipped entirely whenever the icon wasn't yet selected (hiding a real duplicate warning behind the icon error), and a failed save's error could land off-screen with no way for the user to tell why saving did nothing.

**Walked:** CLEANUP_CHECKLIST.md against the diff, section by section. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to `SettingsScreenTest` on a Pixel 8 API 36 emulator — run twice, once catching a real on-device bug (see Found & fixed), once clean (35/35).

**Found & fixed:**
- Demo data duplication: `SettingsViewModel.loadDemoData()` now calls `hodithRepository.deleteAllData()` before `demoDataSeeder.seed()`, gated behind a new destructive `ConfirmDialog` (reusing the component "Delete all data" already uses) that warns all existing data will be wiped. `DemoDataSeeder.seed()` itself is unchanged — the fix is entirely at the trigger path.
- Archived-name collision: `validateCaseEdit` now takes every other Case (active + archived) instead of just active ones, splitting the match into a blocking `nameDuplicate` (another active Case) and a separate, non-blocking `nameDuplicatesArchived` warning (only an archived Case). New `HodithRepository.getAllCases()` (delegating to `CaseDao`'s pre-existing, previously-unexposed `getAll()`) backs the lookup. New `Voice.caseNameDuplicateArchivedError` (all three voices) explains the three ways to resolve it — rename, restore the archived Case, or delete it — as supporting text under the name field, the same mechanism the existing duplicate-name error uses; no new dialog or navigation.
- Save-time duplicate check wrongly skipped: `save()`'s short-circuit guard skipped the duplicate-name query whenever the icon was unset, even if the name itself was a genuine duplicate — a Case with both a duplicate name and no icon showed only the icon error, silently dropping the name warning. The query now skips only when the name itself is blank.
- Silent failed save: `CaseEditForm` didn't scroll back to a validation error that fired on a field above the Save button, if the button only came into view by scrolling down a longer form — the save appeared to do nothing. Added a `LaunchedEffect` that scrolls the form back to the top whenever any of the four error flags turns true.
- Real on-device bug caught by the new instrumented test: Plain's new `settingsLoadDemoDataConfirmAction` copy ("Load demo data") was identical to the button label that opens the dialog, so both text nodes coexisted on screen — ambiguous for both the Compose test and a real user. Changed to "Load it".

**Deferred:** nothing.

**Considered and declined:**
- A name-matching "replace only the demo Cases" approach for the reseed fix (delete Cases whose name matches one of the 9 fixed `CASE_SEEDS` names) — simpler to wipe everything behind an explicit warning dialog instead, per the user's own suggestion once the name-matching design was shown; avoids a "replace" implying partial preservation when it's actually a full reset.
- A richer `CaseEditValidation` error-kind enum instead of a second boolean (`nameDuplicatesArchived`) — two mutually-exclusive booleans match the existing `showNameError`/`showDuplicateNameError` sibling shape; an enum would be overkill for two states.
- Adding `CaseEditScreenTest` coverage for the new archived-duplicate warning / scroll-to-top behavior — that file's own doc comment already declares full field-by-field form validation UI coverage (required-name, duplicate-name, icon-required errors) a separate, pre-existing, out-of-scope gap; singling out just the new branch would be inconsistent with every sibling branch staying untested at this layer.
- A new TESTING.md row itemizing `DemoDataSeederTest`/`loadDemoData` test coverage generally — that area was never itemized in TESTING.md even before this change (developer-mode-only diagnostic tooling); only the one factually-stale clause (Case Edit's duplicate-name description) was corrected, not backfilled wholesale.

**Checks:**
- Duplication: no new composables; reused the existing `ConfirmDialog` component for the new Load-demo-data confirmation rather than a bespoke `AlertDialog`. `HodithRepository.getAllCases()` exposes a DAO query (`CaseDao.getAll()`) that already existed for backup/export, not a new overlapping one.
- Decoupling: `validateCaseEdit` stays a pure, Compose-free function; the scroll-to-top effect is UI-only and lives in the Composable, not the ViewModel.
- Dead code: no unused imports; `kotlinx.coroutines.flow.first` stays used elsewhere in `CaseEditViewModel.kt` (`stampOpenEndedEvents`) after its one `save()` call site was replaced.
- Hygiene: `git status` clean; no secrets or local paths in the diff.
- Naming: new Voice keys (`caseNameDuplicateArchivedError`; `settingsLoadDemoDataConfirmTitle`/`Body`/`Action`) added to all three voices in this commit, matching each voice's existing tone for their string family.
- UI copy brevity: the new confirm dialog's title/body/action lengths matched against the sibling `settingsDeleteAllDataConfirm*`/`settingsImportConfirm*` strings. The archived-duplicate warning is deliberately longer than its one-line `caseNameDuplicateError` sibling since it explains three resolution paths, not one fact — it's field supporting text that wraps rather than a fixed-width control, so the length difference isn't a brevity violation.
- Spec review: `HODITH_SPEC.md`'s Settings row updated to note Load demo data's new confirm-dialog/full-replace behavior. Case-name uniqueness rules were never documented in the spec at this level of detail before (same as required-name/max-length), so no divergence there.
- Tests: regression tests added for both reported bugs (`SettingsViewModelTest` — reload no longer duplicates; `CaseEditValidationTest`/`CaseEditViewModelTest` — an archived-only duplicate is a separate non-blocking warning, and a duplicate name with no icon selected still surfaces) and for the two bugs the user found via manual testing on-device. Fixed the one existing `SettingsScreenTest` the behavior change broke (`loadDemoData` no longer taps straight through) and added its cancel counterpart.

**Docs updated:** HODITH_SPEC.md (Settings row); TESTING.md (ViewModels row's Case Edit clause corrected; Compose UI row's Developer Mode clause extended); this entry (oldest entry, `fix/info-note-copy`, removed per the 5-entry retention rule).

---

## fix/widget-row-sizing

**Scope:** user-reported List widget resize bug — a continuation of an earlier, uncommitted investigation (branch deleted, nothing landed). Two root causes: `CaseRow` emitted an unconditional trailing `Spacer` after every row including the last, leaving a dead strip at the bottom; and the `LazyColumn` had no height constraint, so it wrap-sized to content instead of filling the widget, leaving empty space when resized taller than the row count. Three prior attempts to additionally force "exactly N whole rows, no scroll" from a guessed row-height constant had all failed in opposite directions and were explicitly not re-attempted this pass — researched against Glance's own docs instead, which confirmed a partial row clipped at the bottom of a scrolling list is standard `ListView` behavior, not a defect to engineer around with unmeasurable row-height math. Scope grew to a spacing-tightening pass once the mechanical fix was confirmed on-device and the user asked for a denser row/plank layout.

**Walked:** CLEANUP_CHECKLIST.md against the diff, section by section. `ktlintCheck`, `lintDebug`, `test` (full unit suite, including the updated `WidgetTokenFidelityTest`), and `assembleDebug` all pass. No instrumented coverage is possible for either fix — see Tests below.

**Found & fixed:**
- Root cause 1: `CaseRow`'s trailing `Spacer(WidgetPlankSpacing)` fired unconditionally. Added an `isLast` flag threaded from the `LazyColumn`'s item index through the private `items` extension to `CaseRow`, skipping the Spacer on the last row.
- Root cause 2: the `LazyColumn` wrap-sized instead of filling the outer `Column`. Added `.defaultWeight()` to its modifier so it claims the remaining height and scrolls instead of leaving dead space.
- Spacing tightened for row density, confirmed on-device by the user: `WidgetOuterPadding` 12→10dp, `WidgetRowPadding` 10→6dp, `WidgetHeaderPadding` 15→10dp, `WidgetPlankSpacing` 8→6dp.
- `WidgetHeaderPadding`'s reduction puts the List widget header/empty-state tap target below the 48dp `MinTapTarget` minimum (~37dp) — calculated that 15dp was already near the floor for a true 48dp target at this font size, and that no Box/Spacer layering trick can shrink both the top-of-widget→title and title→rows gaps at once without the same floor (the slack only relocates between them). User explicitly decided the row-cutoff fix mattered more than this one control's tap target. See Considered and declined.
- `WidgetPlankSpacing`'s reduction breaks its fidelity to Home's `PlainPlankVerticalMargin * 2` — removed the now-invalid `WidgetTokenFidelityTest` assertion and its `PlainPlankVerticalMargin` import, and updated the stale doc comments in `WidgetCommon.kt`, `HomeScreen.kt`, and `DEV_PLAYBOOK.md` §4 that documented the old guaranteed relationship (all three explicitly claimed a sync that no longer holds — caught by walking the actual files, not assumed from memory of the earlier edit).
- Documented the underlying Glance fact as a third numbered gotcha in `DEV_PLAYBOOK.md` §4 (alongside the existing Text-in-Box and no-margin notes): `LazyColumn` has no `verticalArrangement`/`spacedBy` equivalent, so a manual inter-item `Spacer` must know which item is last and skip itself there, or it reintroduces this exact bug.
- Added a List widget resize item to `MANUAL_TEST_PLAN.md` (item 12) — this surface can't be driven by an instrumented test (the harness can't populate `LazyColumn` row items at all), so it's a standing manual check, not a one-off verification of this fix.

**Deferred:** nothing.

**Considered and declined:**
- `SizeMode.Responsive` with discrete, on-device-calibrated `DpSize` buckets (each hand-confirmed to show exactly N rows, no clipping or dead space) — the properly-supported Glance pattern for this problem, researched this pass. Declined in favor of the simpler scrolling fix once the user approved its on-device look; would need a round of real calibration measurements if revisited.
- `WidgetHeaderPadding` kept at 15dp to preserve the 48dp tap target — declined by explicit user decision; row-cutoff fix weighed higher than this control's tap target size.
- A Box + invisible-`Spacer` trick to emulate a min-height modifier for the header (Glance has no `heightIn`) — doesn't work regardless: the reserved height still pushes following siblings down by the same total amount, so the slack only moves between the top-of-widget→title and title→rows gaps rather than shrinking both.

**Checks:**
- Duplication: no new composables; the existing `items` extension and `CaseRow` were extended, not duplicated.
- Decoupling: widget code stays Glance/Android-only, no `domain/` or Repository changes.
- Dead code: no unused imports (removed the now-unused `PlainPlankVerticalMargin` import from the test file after deleting the assertion that used it).
- Hygiene: `git status` clean; no secrets or local paths in the diff.
- Hardcoded values: all four changed numbers are already named tokens in `WidgetCommon.kt`, not inline magic numbers.
- Accessibility: `WidgetHeaderPadding`'s tap-target reduction below 48dp is a deliberate, user-approved exception — see Considered and declined. All other tap targets (`MinTapTarget`-sized Boxes) are untouched and unaffected by any of the padding changes.
- Background work, widgets: Plain-light-only theming and per-Case `logFlow` handling untouched by this diff.
- Spec review: `HODITH_SPEC.md` §15 doesn't claim any specific resize behavior, so no divergence to fix there; `DEV_PLAYBOOK.md` §4's token table and layout-facts list updated (see Found & fixed).
- Tests: no regression test added for either root-cause fix. `docs/DEV_PLAYBOOK.md` already documents that the on-device widget test harness can't populate `LazyColumn` row items at all (`RemoteViewsService`/`ListView` binding gap), ruling out an instrumented assertion on the trailing-spacer or fill behavior; `CaseRow` is a private Composable with no feasible unit-test path without a Glance/Robolectric test harness this repo doesn't have. Declined rather than silently skipped — flagging here since "bug fixed → regression test added" is otherwise an unconditional checklist item.

**Docs updated:** `DEV_PLAYBOOK.md` §4 (token table values, the plank-spacing/Home-fidelity description, a new Glance layout-facts bullet); `MANUAL_TEST_PLAN.md` (new widget-resize item); this entry (oldest entry, `fix/duration-selector-wrap`, removed per the 5-entry retention rule).

---

## fix/intensity-required-when-enabled

**Scope:** Reported bug — a Case could have intensity enabled, but an event could still be logged with no rating. Investigation found the gap (`LogDetailForm`'s `canSave` never checked intensity, and a selected choice could be tapped back to none), then expanded to cover what the fix needed to get right: whether any other optional event field had the same case-flag-says-"tracked"-but-still-skippable shape (none did), and what toggling `intensityEnabled` does to a Case's existing events (nothing, either direction — which then needed its own user-facing notice).

**Walked:** CLEANUP_CHECKLIST.md against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite, including 3 new `CaseEditViewModelTest` cases), and `connectedDebugAndroidTest` scoped to the two changed/new instrumented classes (`LogDetailIntensityTest`, `CaseEditScreenTest` — 25/25 passing on a Pixel 8 API 36 emulator), plus `assembleDebug`. The full `connectedDebugAndroidTest` suite was attempted once; it hit the pre-existing documented flaky teardown-race crash on two unrelated, untouched classes (`CaseDetailScreenTest`, `ShareCardTemplateTest`) and an emulator system-server crash — not re-run after that, per TESTING.md's own scoped-first convention for this known issue.

**Found & fixed:**
- Root cause: `LogDetailForm`'s `canSave` validated only start/end time-picker rejections, never intensity, and `IntensitySection`'s `onSelect` let an already-selected choice toggle back to `null`.
- Intensity made required only for *new* events, never for editing an existing one — avoids two hazards surfaced during review: fabricating a rating for an event logged before the Case tracked intensity, and the toggle itself never touching existing events either direction (which needed its own notice rather than silent behavior).
- Added a transient Snackbar (`CaseEditViewModel.intensityToggleNotice`, the same one-shot `Channel`/`Flow` idiom as `HomeViewModel.quickLogUndo`) fired once per toggle flip on a Case with existing events — redesigned mid-implementation after an initial persistent `ActionBanner` version was caught sitting on the screen as a standing fixture instead of behaving like a transient notification; the `ActionBanner` widening made for that first version was reverted once the Snackbar replaced it.
- Added `withDismissAction = true` to the Snackbar after on-device testing showed it doesn't close on an outside tap or swipe (Material3's default); every other snackbar in the app uses the short default with no dismiss control, but this one needed `SnackbarDuration.Long` and an explicit close button since it carries a full two-sentence message.

**Deferred:**
- `outcomeValueFor` (trend/notification code) reads `EventEntity.intensity` directly regardless of `Case.intensityEnabled`, found while reviewing what the toggle does to existing events. Logged as its own `PROGRESS.md` item rather than fixed here — a pre-existing, unrelated inconsistency outside this bug's scope.

**Considered and declined:**
- Keeping intensity optional even when enabled (the status quo the spec originally documented) — declined by explicit product decision in favor of requiring it for new events.
- A persistent inline notice next to the toggle — declined in favor of a transient Snackbar, the idiom this app already uses for one-shot notifications.

**Checks:**
- Duplication: `enableIntensityNotice`/`disableIntensityNotice` added to all three Voice implementations in the same change; no inline strings.
- Decoupling: the default-intensity seeding lives in the UI-layer composable (`LogDetailForm`), the only place that already knows both `isEditing` and `intensityEnabled`; `domain/` untouched.
- Hygiene: no unused imports or leftover debug code; `git status` clean.
- Naming: `IntensityToggleNotice`/`intensityToggleNotice`/`loggedEventCount` follow the existing `QuickLogUndo`/`quickLogUndo`/`runningEventCount` pattern.
- UI copy brevity: doesn't apply — the new strings are Snackbar body copy, not control labels.
- Tests: regression coverage added at both layers — `CaseEditViewModelTest` (3 new cases for the notice-gating logic) and new instrumented coverage (`LogDetailIntensityTest`, `CaseEditScreenTest` additions), both run on-device.

**Docs updated:** HODITH_SPEC.md (§6, the Watch table's `minIntensity` row), TESTING.md (Compose UI and ViewModels rows extended), PROGRESS.md (new follow-up item for the `outcomeValueFor` finding); this entry (oldest entry, `chore/voice-phrasing-audit`, removed per the 5-entry retention rule).

---

## test/autosize-harness-investigation

**Scope:** PROGRESS.md's "Instrumented tests can't observe `TextAutoSize`'s resolved font size" investigation item, opened during `fix/duration-selector-wrap` after two instrumented-test attempts (a bounding-box height assertion, a `GetTextLayoutResult`-semantics probe) both showed zero observable shrink across every tested width, even at an unreasonable 20dp-per-segment — not matching the real on-device behavior the user had confirmed.

**Walked:** CLEANUP_CHECKLIST.md against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite, nothing to re-run — only `androidTest` sources changed), and `connectedDebugAndroidTest` scoped to `SegmentedChoiceRowTest` (6/6 passing on a Pixel 8 API 36 emulator, including the new test).

**Found & fixed:**
- Root cause: both prior techniques resolved `onNodeWithText` (or the matching semantics action) to the *merged* `SegmentedButton` semantics node — not the inner `Text` leaf that `autoSize` actually resizes — and that merged node's measured height is pinned to `SegmentedButtonDefaults`' fixed minimum touch-target height (40dp), confirmed directly: a real `SegmentedChoiceRow` at 300dp width and the same row squeezed to 60dp both measured `onNodeWithText("Start/stop")` at exactly 40.0dp. `autoSize` itself was never broken in this harness — a bare `BasicText`/`Text` with `autoSize = TextAutoSize.StepBased(...)` shrinks correctly under the same `v2` `createComposeRule()` rule this repo already uses everywhere, and `SegmentedRowFontSizeCoordinator.resolved`, read directly, settled to a genuinely shrunk size (12sp against a 14sp baseline) through a real `SegmentedChoiceRow` at 60dp.
- Added `coordinatedFontSize_shrinksBelowBaselineAtNarrowWidth` to `SegmentedChoiceRowTest.kt`: constructs its own `SegmentedRowFontSizeCoordinator`, provides it via `LocalSegmentedRowFontSizeCoordinator` (the same already-public API `SettingsScreen.kt` uses for its shared Appearance-section coordinator), and reads `coordinator.resolved` directly — the same value every segment's `onTextLayout` already reports in production — sidestepping the semantics tree entirely instead of inferring the font size from a bounding box or a semantics action.
- Added a class doc comment to `SegmentedChoiceRowTest.kt` naming the merged-semantics/fixed-minHeight pitfall, so a future reader doesn't re-attempt either of the two techniques already ruled out.
- Closed the PROGRESS.md item (both acceptance criteria met: root cause identified, working technique landed) and updated `fix/duration-selector-wrap`'s stale "worth a closer look later" Deferred bullet to point at this entry instead of dangling.

**Deferred:** nothing — both acceptance criteria were met in this pass.

**Considered and declined:**
- A `Modifier.layout` constraints probe spliced into a locally-modified copy of `SegmentedChoiceRow` (the plan's fallback step for if the coordinator read had failed) — not needed once `coordinator.resolved` passed cleanly.
- Comparing the classic (`androidx.compose.ui.test.junit4.createComposeRule`) and `v2` rules head-to-head — not needed once the bare-`autoSize` case passed under `v2` on the first attempt, ruling out the rule itself as a cause.

**Checks:**
- Duplication: the new test reuses `SegmentedRowFontSizeCoordinator`/`LocalSegmentedRowFontSizeCoordinator` as already-public API, no new test-only hook added to production code.
- Decoupling: no `domain/`/`android.*` boundary touched; no production code changed at all, this pass was test- and doc-only.
- Dead code: the throwaway diagnostic file used to stage the investigation (bare-`autoSize`, bounding-box comparison, and real-row height probes) was deleted once its purpose was served, not left behind — same convention as `fix/duration-selector-wrap`'s own diagnostic test.
- Hygiene: `git status` clean; no secrets or local paths in the diff.
- Naming: new test method name follows this class's existing `subject_condition` convention (e.g. `longLabelOption_doesNotWrapInPlainTheme`).

**Docs updated:** TESTING.md (shared-components row extended), PROGRESS.md (item resolved and removed — see CLAUDE.md's outstanding-only convention for that file), CLEANUP_LOG.md's `fix/duration-selector-wrap` entry (Deferred bullet repointed); this entry.

---

## chore/qa-audit

**Scope:** The periodic whole-suite QA audit, prompted by reviewing the ruleset itself against this log's history: section 7 had no equivalent staleness check for `MANUAL_TEST_PLAN.md`, and `fix/viewmodel-db-teardown-race`'s fix only guarded new tests, not existing ones. Extended the ruleset first (new section 8 "Manual test plan hygiene", a new ViewModelStore/db.close() sweep bullet in section 1, renumbering), fixed a stale `CLAUDE.md` line found along the way ("Serious, Goth, Quirky" → "Plain, Intense, Bright"), then ran the full, now-9-section checklist for real.

**Walked:** Ran sections 1-9 against the current suite rather than reconstructing them from memory. `ktlintCheck`, `compileDebugAndroidTestKotlin`, and a scoped `connectedDebugAndroidTest` run (the 7 classes touched by the duplication fix, 69 tests) all pass on-device.

**Found & fixed:**
- Section 6: three byte-identical duplicated test-helper clusters — `millisAtDay`/`nameFieldText`/`ZONE` across `ShareScreenTest.kt`/`InsightShareTabTest.kt`/`HistoryShareTabTest.kt` (now `ui/share/ShareTestFixtures.kt`); `unusedScheduler` across both `RoomHodithRepositoryBackupTest.kt`/`RoomHodithRepositoryHistoryEventsTest.kt` (now parameterized by reason, in `data/TestFixtures.kt`); `activeGroupSummary` across `NotificationActionReceiverTest.kt`/`NotifierContentTest.kt` (now a `NotificationManager` extension in `notification/NotificationTestFixtures.kt`).
- Section 1's new sweep: both known ViewModelStore/db.close() call sites still correctly ordered — no regression since `fix/viewmodel-db-teardown-race`.
- Section 2: 8 targeted mutations across pure domain (`VerdictEngine`, `WatchEngine`, `InsightsEngine`, `StatsEngine`, `CheckIn`), Fake-backed orchestration (`NotificationEvaluator`), and — device attached this pass — the Room-instrumented DAO tier (`RoomHodithRepository.observeHistoryEventsForCase`'s `hasMore` boundary, `.mergeTag`'s transaction ordering). All 8 caught cleanly; no misses, no weak passes.
- Section 3: every Trends-detector constant (tag→outcome, change point, trend slope, time-of-day split, tag timing, weekday/weekend) checked against `HODITH_SPEC.md` §10. All match exactly.
- Sections 4/5: sampled largest/newest instrumented files and every inline `onValueChange`/`onClick` lambda. No chaining-before-asserting, no under-asserting, no un-extracted pure logic (the two likely candidates are already extracted and tested).
- Section 7: no aggregate test counts exist in `TESTING.md` to drift; every `CLEANUP_LOG.md` cross-reference still resolves; two spot-checked bug-fix regression tests still assert their original failure.
- Section 8 (new): all 27 class-level and 12 method-level test cross-references in `MANUAL_TEST_PLAN.md` still resolve; the Espresso-Intents-absence justification behind its About & Contact items still holds.

**Deferred:** nothing.

**Considered and declined:** nothing — every finding was small enough to fix inline, per this pass's agreed fix mode.

**Checks:**
- Duplication: this pass's own subject; see Found & fixed.
- Hygiene: `git status` clean; no secrets or local paths in the diff; every mutation reverted before moving to the next.
- Tests: the three dedup fixes verified by running every touched class twice — once mid-crash on a degraded emulator (unrelated to this diff, see below), once clean on a freshly restarted one (69/69 passing).
- An unrelated flake surfaced during verification: a 7-class run hit `INSTRUMENTATION_ABORTED: System has crashed` after running 1h44m instead of ~15-20 minutes — the same degraded-emulator signature (`Can't find service: activity`/`package`) `TESTING.md`'s Known environment issues section already documents. Restarting Android Studio resolved it. Not logged as a new line since the existing entry already covers this signature.

**Docs updated:** QA_AUDIT_RULES.md (new section 8, new section 1 bullet, renumbering); CLAUDE.md (stale voice names fixed); QA_AUDIT_BACKLOG.md (condensed back to its shell); this entry.
