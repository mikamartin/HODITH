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

## feature/manual-duration-ongoing-stop

**Scope:** User request: show a `MANUAL`-duration event as "ongoing" — expected-end display plus an immediate Stop — once its computed duration projects past `now`, matching the existing `START_STOP` ongoing/Stop UX. Landed across the History tab, Insights drill-down rows, the Edit Event screen, Home, both Glance widgets, and Big Picture's day/week detail (display-only there, per the user's explicit scope cut: no Stop, since that screen targets a Case across many events, not a single one). Extended the existing "still-running events are excluded until they stop" stats rule to cover this new case too.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to every touched/new instrumented class across several passes as the diff grew (`CaseDetailScreenTest`, `HomeScreenTest`, `LogDetailScreenTest`, `BigPictureScreenTest`, `EventDaoTest`, `HomeCountQueriesTest`, `WidgetActionsFlowTest`, `CaseEditScreenTest`, `HistoryShareTabTest`, `InsightShareTabTest`, `CaseDetailWatchesTabTest`, `RoomHodithRepositoryHistoryEventsTest`) — all green, including two real bugs the first run of each caught (see Found & fixed).

**Found & fixed:**
- New shared domain predicate `isStillRunning` (`domain/ActiveSpan.kt`) replacing six duplicated `durationMode == START_STOP && endedAt == null` inline checks across `VerdictEngine`/`WatchEngine`/`BigPictureViewModel`/`ShareCardState`/`CaseDetailScreen`/`InsightsTab`; generalizes `activeSpanEnd` so a `MANUAL` event's not-yet-elapsed `endedAt` spans to `now` the same way a `START_STOP` open event already did.
- `NotificationEvaluator.silenceAnchorFor`'s SQL-based counterpart (documented as needing to stay in sync "by hand" with `WatchEngine`'s in-memory version) had no way to see a `MANUAL` event's future `endedAt` at all (`getOngoingEvent`'s `endedAt IS NULL` query structurally can't). Added `EventDao.hasEventEndingAfter`/repository plumbing and wired it in, keeping its own doc-comment call count ("four suspend calls") in sync.
- Extended "still-running events are excluded until they stop" to `MANUAL`'s duration-based stats: `computeDurationStats`, and the Trends `outcomeValueFor` used by tag→outcome/trend slope/time-of-day split — a still-running event's duration is now excluded (its intensity, if recorded, still counts).
- Real stale-UI bug caught mid-build: the Stop button on a `MANUAL` row appeared to do nothing for up to 60s. `rememberTickingNow`'s `now` is a periodically-ticked Compose state, harmless for `START_STOP` (whose check is `endedAt == null`, independent of `now`) but `MANUAL`'s check compares `endedAt > now` — immediately after Stop, the freshly-written `endedAt` could still exceed the UI's stale `now`. Exposed the ticker as a `MutableState` and had the History tab's Stop callback bump it immediately alongside the stop action.
- Home's ongoing indicator read from `observeOpenEvents` (`endedAt IS NULL`), which structurally can never see a `MANUAL` event's future `endedAt` — a state that expires by the passage of time, not a database write. Switched `homeCaseRows` to derive `ongoingEvent`/`runningCount` from the event-span projection it already loads, via `isStillRunning`, re-evaluated against a fresh `now` on every call. `HomeCaseRow.ongoingEvent` narrowed from `EventEntity` to the leaner `CaseEventSpan` it only ever needed fields from.
- Checklist-surfaced (Duplication): four near-identical "elapsed-time vs. expected-end text" branches (History row, Insights drill-down row, Home's two row variants) collapsed into one shared `OngoingTimeText` composable.
- Checklist-surfaced (Duplication): the widgets' identical `use24Hour`-from-Settings `produceState` block, duplicated between `ListWidget.kt` and `SingleCaseWidget.kt`, extracted to `WidgetCommon.kt`'s `rememberWidgetUse24Hour`.
- Checklist-surfaced (UI Copy Brevity): `OngoingExpectedEndText`'s trailing text used the full `formatEventTime` (e.g. "Oct 14, 3:00 PM, Wed") — much longer than sibling `OngoingElapsedText`'s "2h 15m", and inconsistent with Big Picture's own new branch already using a bare clock time. New shared `formatExpectedEndTime` (same-day bare time, cross-day falls back to the shorter `formatSpanDateTime`) used by both the Compose composable and the widgets' own subtitle.
- Checklist-surfaced (Dead Code): `observeOpenEvents` (`EventDao`/`HodithRepository`/`RoomHodithRepository`/`FakeHodithRepository`) had exactly one caller, which the Home fix above removed. Deleted outright, with its two dedicated tests (`FakeHodithRepositoryTest`, `HomeCountQueriesTest`).
- Real pre-existing-test bug caught by the first instrumented run: `CaseDetailScreenTest.finishedEventRow_showsDurationLabel_whenTheCaseTracksDuration` used a `MANUAL` event with `endedAt` 45 minutes after start but the screen's default `now` only 10 seconds in — under the new semantics that event reads as still running, not finished, so the duration label stopped appearing. Fixed by passing an explicit `now` past the event's `endedAt`.
- New `WidgetActionsFlowTest` coverage for the Single-case widget's `MANUAL` expected-end subtitle — this path had no automated coverage at all before this pass; caught my own test-setup bug (`use24Hour` in the assertion not matching what `setUp()` pinned Settings to) on the first run.

**Deferred:** nothing.

**Considered and declined:**
- A Stop action on Big Picture's day/week detail row for a still-running `MANUAL` event — explicitly scoped out by the user; display-only there.
- Reconciling a `MANUAL` event's not-yet-elapsed `endedAt` in the duration-mode transition contract (switching a Case away from `MANUAL` mid-projection) — the existing "no migration for an inflated `endedAt` from an earlier round-trip, fixed by editing the event" policy already covers this without a special-case addition.

**Checks:**
- Duplication: see Found & fixed (`OngoingTimeText`, `rememberWidgetUse24Hour`, `formatExpectedEndTime`).
- Decoupling: `isStillRunning`/`activeSpanEnd`/`computeDurationStats`/`outcomeValueFor` all stay pure Kotlin (`domain/`), no `android.*` imports; every new `now` comparison takes an injected value, nothing reads the system clock directly.
- Accessibility: reused `StopIconButton`'s existing content description; the Edit Event screen's new "Stop now" button carries a text label, no icon-only affordance introduced.
- Naming: three new `Voice` members (`ongoingExpectedEndTrailing`, `bigPictureEventExpectedEnd`, `logSheetExpectedEndNotice`) added to all three voices in the same commit.
- Tests: new unit coverage for `isStillRunning`/`activeSpanEnd`, `computeDurationStats`/`computeTagOutcomeFindings` `MANUAL` exclusion, `silenceAnchorForEvents`/`NotificationEvaluator` `MANUAL` coverage, `BigPictureViewModel`'s `expectedEndAt`, `HomeViewModelMappingTest`'s `MANUAL` `ongoingEvent`/`runningCount`; new instrumented coverage for `EventDaoTest.hasEventEndingAfter`, the Edit Event screen's Stop-now round-trip, Home's `MANUAL` row, and the Single-case widget's `MANUAL` subtitle.

**Docs updated:** HODITH_SPEC.md (§6 new "Manual duration projecting past now" bullet, plus the Event table, §9 Active span, Big Picture, and §15 widget bullets); TESTING.md (Watch evaluation, Notification evaluation, Stats & visual data prep, Room DAOs, ViewModels, Compose UI, and Compose UI — Big Picture rows extended or corrected); MANUAL_TEST_PLAN.md (item 6 extended for the List widget's `MANUAL` row); this entry (oldest entry, `test/autosize-harness-investigation`, removed per the 5-entry retention rule).

---

## fix/big-picture-day-detail-chronological-order

**Scope:** User-reported: the Big Picture day/week detail dialogs listed a day's events grouped by Case (every event of Case A before any of Case B) instead of chronologically, with no distinct treatment for a multi-day span's "no real time that day" rows. Clarified with the user that HODITH has no literal "all-day event" concept — only a multi-day span's middle day (or any day a still-running event is merely carried through) genuinely has no boundary time; a span's own start/end day has a real clock time and should sort and read by it ("Started …"/"Ended …"), not the old blanket full-range label.

**Walked:** CLEANUP_CHECKLIST.md against the diff, section by section. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to `BigPictureScreenTest` on a Pixel 8 API 36 emulator — 51/51 green, including the three new cases.

**Found & fixed:**
- `BigPictureGrid.kt`'s `DayEvent` replaced `isSpanStart`/`isSpanCarried` with `isSpanStart`/`isSpanEnd`/`isAllDay`, so a span's end day (a real boundary) is distinguished from a true middle day (none).
- New pure `sortDayEvents` (`BigPictureFilterState.kt`): `isAllDay` rows sort first (nothing to sort by); every other row sorts by whichever boundary falls that day — start time, or end time for `isSpanEnd`.
- `coveredDates` and the `eventsByDay`-building loop, previously private/inline inside the `BigPictureGrid` composable, extracted to pure `coveredDates`/`buildEventsByDay` in `BigPictureFilterState.kt` — both now unit-testable on the JVM without Compose, matching this file's own established rationale for `isTagVisible`/`resolveVisibleSelection`.
- `EventDetailRow` labelling: a span's start day now reads "Started …", its finished end day "Ended …" (each a bare clock time), reserving the old "lasted …" full-range label for an actual no-boundary (`isAllDay`) day; the `isOngoing` branch is unchanged.
- New `Voice.bigPictureEventSpanStarted`/`bigPictureEventSpanEnded`, given default (identical) bodies directly on the `Voice` interface rather than per-voice overrides — confirmed with the user that this label is deliberately the same wording in all three voices, same pattern as `bigPictureFilterCount`'s existing structural default. (A per-voice-override attempt first failed `VoiceTest`'s "no abstract key is identical across all three voices" check, which is exactly why structural/identical keys use interface defaults instead.)

**Deferred:** nothing.

**Considered and declined:**
- A dedicated per-theme (Intense/Bright) visual pass for the new rows — declined since "Started …"/"Ended …" are plain-text labels reusing the existing row's typography/layout with no new colors or sizing; the user's own manual check (default theme) already confirmed the layout.

**Checks:**
- Duplication: no inline strings — the two new labels went through `Voice` (as interface defaults, not per-voice literals) in the same commit.
- Decoupling: the extraction moves more logic *out* of the composable into pure, JVM-testable functions, not the other direction; day-bucketing still resolves via the event's own captured `utcOffsetMinutes` (`loggedZone()`), not the device's current zone.
- Complexity: `buildEventsByDay`/`coveredDates`/`sortDayEvents` each still have exactly one production caller, but earn the split the same way this file's existing single-caller helpers do (JVM testability without Compose) — not a premature extraction.
- Hygiene: `git status` clean throughout; no secrets or local paths in the diff.
- Naming: new `Voice` keys follow the existing `bigPictureEventSpan*` convention.
- UI copy brevity: "Started …"/"Ended …" are shorter than the siblings they partly replace ("Lasted … – …", "Ongoing since …") — no wrap risk.
- Spec review: `HODITH_SPEC.md` §9 updated (chronological/all-day-pinned ordering, three-way Started/Ended/lasted label) — this was unintentional divergence (a genuine behavior bug/gap), not a deliberate spec change.
- Tests: regression coverage added at both layers — `BigPictureFilterStateTest.kt` (JVM: same-day-duration-not-flagged-as-span, 2-day vs. 3+/4-day span flagging, a still-running event's carried days staying `isAllDay` forever and never falsely `isSpanEnd`, a still-running event started "today" staying a plain timed row, year-boundary date generation, offset-based date resolution, a full mixed-day integration case, sort ties/stability/empty-list) and `BigPictureScreenTest.kt` (instrumented: span start day, span end day, all-day-pinned-above-a-timed-row ordering).

**Docs updated:** HODITH_SPEC.md (§9, day/week detail ordering and label rewritten); TESTING.md (ViewModels row extended for the three new pure functions; Compose UI — Big Picture row corrected for the new labels/ordering); this entry (oldest entry, `test/autosize-harness-investigation`, removed per the 5-entry retention rule).

---

## fix/widget-edit-affordance

**Scope:** Two user-reported bugs on the List widget's configure flow, found and fixed in sequence in the same session. First: the empty-widget message told users to long-press and tap Edit to pick Cases, but tapping it only opened the app (not the picker it promised), and long-press on the user's launcher only resizes — there's no reachable "Edit" affordance that way. Fixed by adding a persistent header edit icon (present in both the empty and populated states) and wiring it, plus the empty-state message, to relaunch the real `android:configure` Activity. That change made the Activity reachable from an already-placed widget for the first time, which exposed two further latent bugs that only ever held for first-ever setup: re-editing opened the app instead of returning to the widget, and re-editing always started from a blank selection instead of the widget's real one.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to the touched classes (`WidgetChromeNavigationTest`, `ListWidgetConfigureFlowTest`, `WidgetActionsFlowTest`) on a Pixel 8 API 36 emulator, across three passes as the diff grew — all green each time (8/8, 2/2, 6/6 most recently).

**Found & fixed:**
- `ListWidget.kt`'s header restructured into a `Row` with a persistent "✎" edit icon (`MinTapTarget`-sized, accessibility-labeled) alongside the title, present in both the empty and populated states; both it and the empty-state message now relaunch `ListWidgetConfigureActivity` via a real `ACTION_APPWIDGET_CONFIGURE` intent instead of opening `MainActivity`.
- `Voice.kt`: `widgetConfigureBody` and `widgetNoCasesSelectedMessage` reworded in all three voices to drop the false long-press/Edit claim; new `widgetEditButtonDescription` added to all three.
- Reported bug: re-editing opened the app instead of returning to the widget. `ListWidgetConfigureActivity` had no declared task affinity, so launching it from a widget tap (a cross-process click Android runs with `FLAG_ACTIVITY_NEW_TASK`) merged it onto an existing `MainActivity` task sharing the default affinity, and finishing it revealed that task instead of the home screen. Fixed with `android:taskAffinity=""` on `ListWidgetConfigureActivity` — and, preemptively, on `SingleCaseWidgetConfigureActivity`, since it shares the identical manifest pattern and would hit the same bug the moment its own edit icon lands (tracked in PROGRESS.md).
- Reported bug: re-editing always started from a blank selection. `ListWidgetConfigureViewModel`'s `init` hardcoded `selectedCaseIds = emptySet()` — harmless on first-ever configure, wrong once an edit icon can reopen the picker on an already-configured widget. Replaced with a `load(initialSelectedCaseIds)` the Activity calls after reading the widget's real `CaseIdsKey` state via Glance's `getAppWidgetState`, guarded against a second call (e.g. a rotation) wiping an in-progress selection back to the original.
- Checklist-surfaced (Accessibility section, not user-reported): the header restructure had moved the title's clickable padding from the `Text` onto the `Row`, shrinking its tap target from the already-documented ~37dp trade-off down to just its bare text line height. Moved the padding back onto the `Text` itself, alongside the icon's own independent 48dp target, before this ever shipped.
- Checklist-surfaced (Hardcoded Values section): the new edit icon reused `WidgetPlusGlyphSize`, a token named for one specific glyph (the quick-log "+") now backing three unrelated glyph buttons. Renamed to `WidgetGlyphButtonSize` across both widgets and every referencing test/doc.

**Deferred:** nothing.

**Considered and declined:**
- A client-side Intent-flag fix (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK`) for the task-affinity bug instead of the manifest attribute — the manifest fix is the conventional, documented approach for `android:configure` Activities and holds regardless of future launch paths, rather than depending on assumptions about Glance's own `PendingIntent` flag construction.

**Checks:**
- Duplication: no new composables; the edit icon reuses the existing "glyph-as-`Text`-in-a-sized-`Box`" pattern the quick-log "+" button already established. All new/changed strings went through `Voice` in all three voices in the same commit.
- Decoupling: `ListWidgetConfigureViewModel.load` stays Context/Glance-free (a plain `Set<Long>` parameter) — the Activity owns the Context-bound Glance read, matching the class's own documented read/write split.
- Accessibility: the new edit icon is `.size(MinTapTarget)` with a non-empty content description in all three voices; the title's tap target confirmed not regressed (see Found & fixed).
- Hygiene: `git status` clean throughout; no secrets or local paths in the diff.
- Naming: `widgetEditButtonDescription` matches the existing `widget*` Voice key convention; the `WidgetGlyphButtonSize` rename keeps the token's name accurate to what it now controls.
- Spec review: `HODITH_SPEC.md` §15 gained one clause noting the List widget's header edit icon, and that the Single-case widget doesn't have one yet.
- Tests: regression tests added for both reported bugs — `WidgetChromeNavigationTest`'s renamed `listWidget_emptyStateTap_opensConfigureActivity` plus two new edit-icon tests (tap behavior, content description); `ListWidgetConfigureFlowTest`'s new re-edit test seeding an existing selection and asserting it renders pre-checked; `ListWidgetConfigureViewModelTest` extended with `load`-seeding and double-call-guard unit tests. The task-affinity fix itself has no practical automated test (it's about which OS task an Activity lands in) — it relies on manifest correctness plus the user's own on-device confirmation.

**Docs updated:** HODITH_SPEC.md (§15, edit icon clause); TESTING.md (Widgets — chrome & trampoline row corrected and extended); MANUAL_TEST_PLAN.md (items 2 and 8 — stale long-press instruction and a stale test-name reference both corrected); DEV_PLAYBOOK.md (widget token table, `WidgetPlusGlyphSize` → `WidgetGlyphButtonSize`); PROGRESS.md (new Single-case widget edit-affordance follow-up item added); this entry (oldest entry, `chore/qa-audit`, removed per the 5-entry retention rule).

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
