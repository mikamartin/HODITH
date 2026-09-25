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

## fix/future-start-time-clamp-notice

**Scope:** PROGRESS.md's "Log entry silently clamps a future start time to now" — the log sheet's date/time pickers silently clamp a same-day future pick down to `now`, and `computeEndedAt` silently clamps a `START_STOP` end time in either direction (future, or before its own start), with no feedback in either case.

**Changes:**
- `viewmodel/LogDetailViewModel.kt`: two new pure helpers next to `applyPickedDate`/`applyPickedTime` — `isFutureClamped(picked, now)` and `isEndBeforeStart(occurredAt, endedAt)` — mirroring the existing `coerceAtMost`/`coerceIn` clamp sites so the UI can report them without touching `toEventEntity`/`planSaveEvent`, which stay the sole save-time authority.
- `ui/logsheet/LogDetailSheet.kt`: `DateTimePickers` gained an `onClampChanged` callback, called from both dialogs' `onConfirm` using `isFutureClamped`. `LogDetailForm` tracks `startTimeClamped`/`endTimeFutureClamped` flags from that callback and derives `endBeforeStart` live from the current draft every recomposition (it isn't a single-moment event — either field's edit can cause it). `TimeSection`/`EndTimeSection` gained a `notice: String?` param rendered as a `bodySmall`/`onSurfaceVariant` caption under the field, following `CaseEditScreen.kt`'s existing inline-field-message precedent (used there for validation errors; here it's informational, not blocking, so it deliberately skips `colorScheme.error`).
- `ui/voice/Voice.kt`: two new keys, `logSheetFutureTimeClampedNotice`/`logSheetEndBeforeStartClampedNotice`, all three voices.
- `docs/PROGRESS.md`: the resolved item removed entirely.
- `docs/TESTING.md`: the Compose UI row and the existing retro-log picker deferral both updated (see Docs updated).

**Checklist walk (against the working-tree diff):**
- *Duplication* — no inline strings; both new keys go through Voice in all three voices in this same commit. The two clamp-detection functions are the single source of truth for "did this clamp" — the picker `onConfirm` sites and the reactive end-before-start check both call them rather than re-deriving the comparison.
- *Decoupling* — no `android.*` import added to `LogDetailViewModel.kt`; both new functions take `now`/`occurredAt`/`endedAt` as plain `Long`/`Long?` params, no `System.currentTimeMillis()`. UI state (the two clamp flags) stays in `LogDetailForm`'s Compose state, not pushed into a ViewModel that doesn't otherwise exist for this sheet.
- *Complexity & pattern health* — `LogDetailForm` was already large; this added two `remember` flags, one derived `val`, and parameter threading rather than a new sub-composable or state holder. No new `LaunchedEffect`. Considered plumbing a `SnackbarHost` instead (this codebase's established one-shot-message pattern, via `Channel`/`receiveAsFlow`, e.g. `HomeViewModel.quickLogUndo`) but declined: `LogDetailSheet`'s `ModalBottomSheet` and the widget trampoline Activity that hosts it have no `Scaffold` today, and adding one in three places for a non-blocking FYI was disproportionate — confirmed with the user before implementing (see PROGRESS.md item's design-decision tag).
- *Dead code & hygiene* — no unused imports (`ktlintCheck` clean). `git status` clean aside from the pre-existing untracked `merged_branches.txt` (unrelated, flagged in every prior entry, left alone).
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — new Voice keys follow the existing `logSheet*` convention; new functions follow `applyPickedDate`/`applyPickedTime`'s neighboring `is*`-boolean-predicate style.
- *Hardcoded values* — none; no magic numbers introduced.
- *Accessibility* — the caption is plain `Text`, no new tappable target; color comes from the theme (`onSurfaceVariant`), so it renders correctly in dark mode and under Intense/Bright without a separate check.
- *Data model/migrations* — none touched.
- *Background work/widgets/notifications* — none touched.
- *Deprecated APIs* — none introduced.
- *Spec review* — `HODITH_SPEC.md` doesn't describe picker clamp behavior at this level of detail; no update needed.
- *Tests* — see below.

**Tests:**
- `LogDetailViewModelTest.kt`: four new cases for `isFutureClamped`/`isEndBeforeStart` (true/false boundary on each side).
- `LogDetailSheetTest.kt`: new `startStopDraftWithEndBeforeStart_showsClampedNotice`, rendering `LogDetailSheet` with a `START_STOP` draft whose `endedAt` precedes `occurredAt` and asserting the notice text appears — driven entirely by initial draft state, no picker interaction needed.
- Not covered by an instrumented test: the same-day future-time clamp notice itself, since asserting it requires driving M3's `TimePicker`/`DatePicker` internals (hour/minute wheels, calendar grid), which no test in this codebase does for any picker (checked). Flagged in TESTING.md's existing retro-log-picker deferral rather than silently skipped.
- No connected device was available in this environment to run `connectedDebugAndroidTest`; `assembleDebugAndroidTest` confirms the new test compiles. Running it on-device is left to the user before merge.

**Deferred:**
- The future-time-clamp notice's Compose-level verification (see Tests above) — the M3 picker-driving gap is pre-existing and repo-wide, not something to solve as a side effect of this fix.
- Running the new instrumented test on a real device — no emulator/device was attached in this session.

**Docs updated:** TESTING.md (Compose UI row, retro-log deferral note), PROGRESS.md (item resolved and removed).

**Verified:** `ktlintCheck → test → lintDebug → assembleDebug` sequential, all green; `assembleDebugAndroidTest` compiles clean (no device available to actually run `connectedDebugAndroidTest` in this environment).

---

## fix/big-picture-filter-pill-consistency

**Scope:** PROGRESS.md's "Big Picture: filter pill consistency pass (color-coding, empty-selection label, tag/case pill parity)" — Big Picture's Cases/Tags/Year filter chips had no per-type color distinction under Bright, read a bare "0" instead of a "None" wording once a filter cleared to nothing, and `TagFilterChip`'s bare-`Text` layout didn't measure to the same height as `CaseFilterChip`'s `Row` layout in a shared `FlowRow`. Mid-pass, after seeing the first result on-device, the user asked for a follow-up round: drop `TagFilterChip`'s checkmark entirely (added earlier in this same session) from both the filter dialogs and the legend row, thicken its selected-state border, and fix wrapped pill rows sitting flush against each other with no vertical gap.

**Changes:**

- `ui/bigpicture/BigPictureGrid.kt`: `filterCountLabel` gained a `selected == 0` branch returning the new `bigPictureFilterCountNone` key. `BrightChip` took a `tint: Color` parameter (was hardcoded to `primary`) so `BrightCaseFilterChip`/`CaseGroupChip` use `secondary` and `YearFilterChip`/`FilterTriggerChip` keep `primary` — Bright now color-codes Cases distinctly from Year/trigger chips. `TagFilterChip` dropped its `LocalCardDecorationStyle` branch and `BrightTagFilterChip` entirely — one flat, unfilled `Text` pill (no icon, no fill, no checkmark) in every theme, selected state read from a thicker (`2.dp` vs. `1.dp`) border and darker text alone; this both fixed the `FlowRow` height-parity issue (matching padding, no wrapper mismatch) and satisfied the later ask to remove the checkmark. Every `FlowRow` holding these pills (Cases/Tags/Year dialogs, the legend row, one Preview) gained `verticalArrangement = Arrangement.spacedBy(6.dp)` — previously unset, so wrapped rows sat flush against the row above with no gap.
- `ui/voice/Voice.kt`: new `bigPictureFilterCountNone` key, all three voices ("None" / "Not one" / "None!").
- `docs/PROGRESS.md`: the resolved item removed entirely; a new item opened for a test-fixture bug found while verifying this pass (see Follow-up below) — not part of this diff's own scope, tracked separately rather than fixed here.
- `docs/TESTING.md`: the Compose UI — Big Picture row gained clauses for the "None" count label, the case/tag chip equal-height check, and the wrapped-pill-rows-don't-overlap regression; a new "Known environment issues" bullet for the timezone test-fixture bug (see Follow-up below) — a promised note on an earlier, similar sighting never actually landed, so this one was written immediately rather than deferred a second time.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no inline strings; `bigPictureFilterCountNone` goes through `Voice` in all three implementations in this same pass. Removing `BrightTagFilterChip` cut duplication rather than adding it (one fewer chip-styling branch to keep in sync).
- *Decoupling* — N/A; UI-only, no ViewModel/domain code touched.
- *Complexity & pattern health* — `BrightChip`'s new `tint` parameter is a straightforward generalization of a value it already computed internally; its four call sites were updated together, not left half-migrated.
- *Dead code & hygiene* — removed the now-orphaned `BIG_PICTURE_TAG_CHIP_CHECK_TAG_PREFIX` test-tag constant and its `Icons.Filled.Check` import along with the checkmark itself; confirmed no other reference by grep. `ktlintCheck` and a full `compileDebugAndroidTestKotlin` passed clean.
- *Repo hygiene* — `git status` clean aside from the pre-existing untracked `merged_branches.txt` (flagged unrelated in every prior entry, still left alone).
- *Naming* — `bigPictureFilterCountNone` follows the existing `bigPictureFilterCount*` pattern.
- *Hardcoded values* — none beyond this file's existing convention of inline `dp` spacing/padding values.
- *Accessibility* — `TagFilterChip`'s selected/unselected signal dropped from three cues (checkmark + border width + color) to two (border width + color) — a deliberate simplification per the user's direct request, not an oversight; not independently re-verified in dark mode or at larger font scale by this pass (see Deferred).
- *Data model / migrations* — N/A.
- *Deprecated APIs* — none introduced.
- *Spec review* — `HODITH_SPEC.md` doesn't describe filter-chip styling at this level of detail; no update needed.
- *Tests* — see below.

**Tests:**

- `VoiceTest.kt`'s existing reflection-based invariants picked up `bigPictureFilterCountNone` automatically (non-blank and distinct across all three voices), confirmed by running it.
- `BigPictureScreenTest.kt`: the checkmark-specific regression test (`tagFilterChip_showsACheckmarkOnlyWhileSelected`) removed along with the feature it guarded — the underlying selection behavior stays covered by the existing functional tests (`tagFilterChip_deselecting_hidesEventsOfOtherTags`, `..._deselectingAllTags_showsUntaggedOnly`, etc.), which assert on filtering outcomes rather than chip decoration. New: `casesDialog_pillsWrappedAcrossRows_dontOverlapVertically`, a regression guard for the missing-`verticalArrangement` fix (12 short-named Cases forced onto multiple rows in the Cases dialog; asserts each row's max bottom bound stays at or above the next row's top).

**Follow-up (asked directly whether the coverage was actually comprehensive, not just green — caught a real bug and a real gap):**

- `caseChipAndTagChip_haveEqualHeight_inSharedFlowRow` (added earlier in this session, before the checkmark-removal round) failed on-device: it deselects the "Tea" Case to force the Cases dimension into a "Some" state, then tries to deselect the "later" tag — but `later` belonged only to Tea's own event, so once Tea was hidden, `later` was scoped out of the Tags dialog entirely and the click had nothing to hit. Fixed by moving both tags onto the still-visible Case's event, so Tea carries no events of its own and deselecting it only narrows Cases, not Tags.
- Running the full `BigPictureScreenTest` class surfaced 9 unrelated failures, all clock-time text assertions (`"12:00 AM"`, `"Ongoing since …"`). Traced to a genuine test-fixture bug, not flakiness — confirmed reproducible (same 9, same names) across two full runs and an emulator restart in between — and to an *already-known* one: an earlier `CLEANUP_LOG.md` entry (`fix/case-log-sort-persistence-and-sizing`, since rotated out of this file by the 5-entry limit) hit the identical failure mode in `CaseDetailScreenTest`/`CaseDetailInsightsTabTest` and flagged it for a `TESTING.md` note that never actually got written. Opened a real PROGRESS.md item this time and wrote the `TESTING.md` note immediately rather than deferring it again.

**Deferred:**

- Re-verifying the thicker-border/no-checkmark selected-state signal in dark mode and at larger Android font scale — the user visually confirmed the change on-device; dark mode and large-font-scale weren't separately re-checked this pass.
- The *fix* for the timezone test-fixture bug found in Follow-up — out of this diff's scope; opened as its own PROGRESS.md item and documented in `TESTING.md` (both landed this pass) rather than fixed inline.

**Docs updated:** `PROGRESS.md` — item removed entirely; new item opened for the timezone test-fixture bug. `TESTING.md` — Compose UI — Big Picture row gained three clauses; new "Known environment issues" bullet (see Changes above).

**Verified:** `ktlintCheck → lintDebug → test → compileDebugAndroidTestKotlin` sequential, all green.

**Instrumented run:** `connectedDebugAndroidTest` scoped to `BigPictureScreenTest` on `Pixel_8_API36(AVD)`: 58 tests, 9 failures — all 9 pre-existing and unrelated (see Follow-up above); both the fixed and the new pill test confirmed passing, first in isolation and again in the full-class run.

---

## fix/big-picture-filter-persistence

**Scope:** PROGRESS.md's "Big Picture's Case/Tag/Year filters don't persist, and aren't even ViewModel-scoped" — the Case/Tag/Year filter selections lived as plain Compose `remember { mutableStateOf(...) }` state inside `BigPictureGrid`, resetting on every navigation away and back and never surviving an app restart. The user ruled full `SettingsRepository`/DataStore persistence, following the exact `observeLogSortOrder`/`setLogSortOrder` pattern from the Log tab's own equivalent fix.

**Changes:**

- `data/SettingsRepository.kt` / `data/DataStoreSettingsRepository.kt`: `observeBigPictureVisibleCaseIds`/`setBigPictureVisibleCaseIds`, `...VisibleTagNames`, `...SelectedYear` — three new `stringPreferencesKey`s (comma-joined case ids, newline-joined tag names, plain integer year), each `null` meaning "no filter stored" (key absent/removed) rather than a literal snapshot, matching `BigPictureDetail`'s own `null`-vs-`""` idiom.
- `ui/bigpicture/BigPictureFilterState.kt`: `normalizeVisibleSelection`/`resolveVisibleSelection` (generic collapse-to-`null`-on-full-selection / resolve-against-live-values pair) and `bigPictureAllTagNames` (extracted verbatim from `BigPictureGrid`'s old scoping `remember` block, now independently testable).
- `viewmodel/BigPictureViewModel.kt`: `BigPictureUiState` gained `visibleCaseIds`/`visibleTagNames`/`selectedYear` (raw persisted values, resolved against live cases/tags only in `BigPictureGrid`); three more `combine()` stages thread the new repository flows in; `setVisibleCaseIds`/`setVisibleTagNames`/`setSelectedYear` write straight through.
- `ui/bigpicture/BigPictureScreen.kt` / `BigPictureGrid.kt`: the three `remember { mutableStateOf(...) }` filter declarations removed from `BigPictureGrid`; it now takes the raw persisted values plus setters as parameters (nullable defaults, so every `@Preview` keeps compiling), resolves them against live cases/tags itself, and the Case-toggle callback explicitly calls `onSetVisibleTagNames(null)` to reproduce the pre-existing "re-scoping Cases resets Tags" behavior that used to fall out of a `remember(allTagNames)` key reset.
- `data/FakeSettingsRepository.kt`: three new `MutableStateFlow` fields/overrides mirroring `bigPictureDetail`/`logSortOrder`.
- `docs/PROGRESS.md`: the resolved item removed entirely.
- `docs/TESTING.md`: the ViewModels row gained the new persistence/resolution coverage; the Compose UI — Big Picture row gained the stale-id-drop / explicit-empty-vs-null / Case-toggle-resets-Tags coverage.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no new user-visible strings, no Voice keys touched. `normalizeVisibleSelection`/`resolveVisibleSelection` are generic and each used twice (Case and Tag); `bigPictureAllTagNames` is called from both `BigPictureGrid` and its own unit test — multi-caller, the extraction earns its keep.
- *Decoupling* — the resolve/normalize logic deliberately stays in `BigPictureGrid` (a composable), not the ViewModel, because it needs the live `cases`/`allTagNames` already in scope there; `BigPictureViewModel` itself stays a dumb passthrough, same shape as `setDetail`. No `android.*` import added to `BigPictureFilterState.kt`. `BigPictureViewModel` still references no UI types.
- *Complexity & pattern health* — `BigPictureGrid`'s main composable was already well past ~150 lines before this diff; not a regression introduced here, so left alone rather than opportunistically restructured mid-fix. `remember` usage for the newly-derived `resolvedVisibleCaseIds`/`resolvedVisibleTagNames` is correct as plain `remember` (not `rememberSaveable`) since they're derived from ViewModel-sourced state, not primary UI state.
- *Dead code & hygiene* — no unused imports (`ktlintCheck` and a full `compileDebugAndroidTestKotlin` both passed clean). No throwaway spike files.
- *Repo hygiene* — `git status` clean aside from the pre-existing untracked `merged_branches.txt` (flagged unrelated in every prior entry, still left alone). No secrets, no local paths, no new tooling/config files.
- *Naming* — no new Voice keys, no new files; new repository/ViewModel methods follow the existing `observeX`/`setX` shape.
- *Hardcoded values* — none introduced.
- *Accessibility* — no new tap targets.
- *Data model / migrations* — none; DataStore Preferences keys need no Room migration.
- *Deprecated APIs* — none introduced; `compileDebugKotlin` showed only a pre-existing, unrelated `@Inject`-parameter-target warning in `DataStoreSettingsRepository.kt`.
- *Spec review* — HODITH_SPEC.md §9 doesn't describe filter persistence either way; this fix doesn't change what's user-visible enough to warrant a spec update.
- *Tests* — see below.

**Tests:**

- `BigPictureFilterStateTest.kt`: `normalizeVisibleSelection` (collapses a full selection to `null`, leaves a partial or genuinely-empty one unchanged), `resolveVisibleSelection` (returns everything for `null`, intersects and drops stale entries for a real set, returns a real empty set rather than falling back to everything), `bigPictureAllTagNames` (scopes/dedupes/sorts).
- `BigPictureViewModelTest.kt`: filter fields default to `null`; `uiState` reflects a repository-pushed value for each of the three; each setter writes through including the `null` case; a fresh `BigPictureViewModel` instance reads back all three persisted values.
- `BigPictureScreenTest.kt` (instrumented): the test harness's `setContent` now holds local recomposable state feeding the new filter callbacks back into `uiState`, since `BigPictureScreen` itself is stateless and these filters moved out of `BigPictureGrid`'s own `remember` state — every pre-existing filter-toggle test kept its original assertions unchanged. Four new tests: a stale seeded Case id is dropped rather than crashing or inflating the count; an explicit empty Case selection renders "no Cases selected" distinctly from an unset (`null`) one; a stale seeded tag name is dropped the same way; toggling a Case chip resets an active persisted Tag selection back to "All tags" even when the post-toggle Case scope alone wouldn't have forced that (Coffee carrying two tags, so a stale re-intersection would otherwise still read as a partial "1 of 2").

**Deferred:** nothing raised and declined.

**Docs updated:** `PROGRESS.md` — the resolved item removed entirely. `TESTING.md` — ViewModels and Compose UI — Big Picture rows both gained clauses (see Changes above).

**Verified:** `ktlintCheck → lintDebug → test (scoped, then full) → compileDebugAndroidTestKotlin → assembleDebug` sequential, all green.

**Instrumented run (once an emulator became available):** `connectedDebugAndroidTest` scoped to `BigPictureScreenTest` on `Pixel_8_API36(AVD)` caught a real bug in this pass's own first-draft test, not the implementation: `seededVisibleTagNames_dropsAStaleNameInsteadOfInflatingTheCount` asserted on the "All tags" legend chip's text, but `FilterLegendRow` (`BigPictureGrid.kt` 564-566) intentionally renders nothing once both the Case and Tag legends resolve to `AllSelected` — the trigger chips already say "All" — so the assertion could never have passed. Fixed by asserting on the Tags trigger chip's own text instead (`hasText(bigPictureTagsFilterLabel) and hasText(": All")`), the same disambiguation pattern the existing Year-filter tests already use. Two of the four new tests (`seededVisibleCaseIds_dropsAStaleIdInsteadOfCrashingOrInflatingTheCount`, `seededVisibleCaseIds_explicitEmptySet_showsNoCasesSelected_distinctFromNull`) were directly confirmed passing before the emulator's `system_server` crashed mid-suite (`INSTRUMENTATION_ABORTED: System has crashed`, `pm`/`activity` services unreachable afterward even with `adb` still connected) — a pre-existing flakiness this repo's other entries have hit before, not something this diff caused. The remaining 9 failures in that run were all pre-existing timestamp/timezone-formatting mismatches (e.g. `"12:00 AM"` / `"Ongoing since …"` not found) unrelated to filters — confirmed by stashing this branch's changes and re-running the identical suite against unmodified `main`: same 9 failures, same test names, there too. A retry to confirm the fixed test and the remaining two new tests was blocked by the crashed emulator not recovering; the user opted to skip further instrumented verification for this pass rather than wait on an emulator restart, so `togglingACaseChip_clearsAnActivePersistedTagSelection_backToAll` and the fixed `seededVisibleTagNames_...` test are unconfirmed on-device (compiled clean, logic reasoned through carefully, but not actually run).

---

## fix/frequency-over-time-label-truncation

**Scope:** PROGRESS.md's "Frequency over time: week/month labels truncate at larger text scale" — `FrequencyCard`'s WEEK/MONTH tick labels (`InsightsTab.kt`) used `maxLines = 1` with ellipsis inside a fixed 12-equal-column layout, so a label could clip at larger system font scale regardless of how short its format string was — the column width, not the string length, was the binding constraint.

**First attempt (reverted after on-device review):** `maxLines` 1 → 2, letting a label wrap onto a second line instead of clipping — reasoned as sufficient because the per-bar `Column` has no fixed height, so the `Row` would grow to fit. The user tested this on-device and reported both Week and Month labels wrapped but were *harder* to read, not easier — two lines of "7/9" or "Jul" squeezed into a single bar's ~24dp column is a worse reading experience than clean truncation would have been, wrap or not. Caught only by actual on-device review; nothing in the automated test (`hasVisualOverflow == false`) or reasoning about the diff surfaced that a technically-non-clipped label could still read badly.

**Real fix, once the actual constraint was named:** the label was boxed into one bar's column even though only 6 of the 12 columns carry a label at Week/Month density — the other 5 empty columns between labels were wasted space the label could have used instead. `FrequencyCard`'s bars and tick labels now render in two separate `Row`s rather than one label-per-bar-`Column`: the bars stay a 12-equal-`weight(1f)`-column `Row` as before, and a new tick-label `Row` gets `tickCount` equal-weight columns (6 at Week/Month density) instead of 12, so each label spans the same 2-bar-wide slice `frequencyTickIndices` already centers it in — double the horizontal room, with `maxLines` back to `1` (ellipsis kept only as a last-resort safety net, not the mechanism). Confirmed algebraically before implementing (not just by re-trying and hoping): at the narrowest previewed card width (320dp, `FrequencyTickPreviewContent`'s own precedent), a 2-bar slice is ~48dp against a single-line "Jul"/"7/9" in Oswald Bold (the widest of the three themes' tick typefaces) needing roughly 36-42dp at 2x font scale — comfortable headroom, unlike the original 24dp single-bar column that was already tight at 1x.

**Guarding against the original spec S9 regression this shape resembles:** a prior version of this chart put tick labels in their own `Arrangement.SpaceBetween` row and labels drifted off the bars they named as label content varied (spec S9) — the reason the code was restructured to one-column-per-bar in the first place, and the reason a second separate row needed real justification, not just a revert of that fix. The new label row avoids the same failure mode because it uses equal-`weight(1f)` columns, not space-between positioning: column boundaries are a fixed fraction of the row's own width regardless of label content, and both the bars row and the label row resolve from the same `fillMaxWidth()` inside the same parent, so their column boundaries always agree — there's no content-dependent positioning left to drift.

**Changes:**
- `ui/casedetail/InsightsTab.kt`: `FrequencyCard` restructured into a bars-only `Row` (`Box` per bar, no more per-bar `Column`) followed by a separate tick-label `Row` with `tickCount` equal-weight columns; `tickIndices` changed from a `Set<Int>` (membership check per bar) to the `List<Int>` `frequencyTickIndices` already returns, iterated directly to build the label row. `maxLines` back to `1`.
- `viewmodel/EventTimeFormat.kt`: `formatFrequencyTickLabel`'s WEEK branch format string `"M/dd"` → `"M/d"` (drops the leading zero); the sibling doc comment on `FREQUENCY_TICK_COUNT_*` referencing the old format string updated to match.
- `viewmodel/EventTimeFormatTest.kt`: the WEEK assertion updated `"7/09"` → `"7/9"`.
- `ui/casedetail/CaseDetailInsightsTabTest.kt`: `setInsightsTabContent` gained a `fontScale` param, mirroring `SettingsScreenTest`'s established `LocalDensity`-override pattern; two new tests (`frequencyGranularityToggle_week/month_atLargeFontScale_tickLabelDoesNotTruncate`) assert `hasVisualOverflow == false` via Compose's `GetTextLayoutResult` semantics action — the same technique `CenteredEmptyStateTest` already uses — at 2x font scale; the `weekTickLabel` test-mirror helper updated to `"M/d"` to match.
- `docs/PROGRESS.md`: the resolved item removed entirely.
- `docs/TESTING.md`: the Time formatting row's `"M/dd"` reference corrected to `"M/d"`; the Case Detail Insights row gained a clause for the large-font-scale non-truncation coverage; a new Deferrals entry added noting Month-label coverage is only proven in `Locale.US` (the test harness has no locale-override plumbing yet).

**Checklist walk (against the working-tree diff, after the revision):**
- *Duplication* — no new user-visible strings, no Voice keys touched. The new `assertTickLabelHasNoVisualOverflow` test helper is called by both new tests, so the extraction earns its keep rather than being a single-caller abstraction.
- *Decoupling* — no ViewModel/Repository/domain files touched; the fix is entirely UI-layer (`FrequencyCard`'s own layout) plus a pure-Kotlin formatter string tweak already covered by an existing unit test.
- *Complexity & pattern health* — the restructure is a straight split of one `Row` into two, reusing `frequencyTickIndices`' existing output rather than adding new tick-placement logic; `FrequencyCard` stays well under the ~150-line composable-split guideline.
- *Dead code & hygiene* — no unused imports (`ktlintCheck` ran clean). `git status` clean aside from the pre-existing untracked `merged_branches.txt` (unrelated, predates this branch, left alone per every prior entry).
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — no new files; the two large-font-scale test methods follow the existing `frequencyGranularityToggle_<granularity>_<behavior>` naming their siblings in the same class already use.
- *Hardcoded values* — no new magic numbers; the label row reuses `FREQUENCY_TICK_LABEL_GAP` unchanged and derives its column count from the existing `frequencyTickCount`/`frequencyTickIndices` rather than a new constant.
- *Accessibility* — no new tap targets. Not manually verified in the on-device UI by me — that's the user's own pass per standing instruction — but this revision exists specifically *because* that on-device pass caught what neither the diff nor the automated test did on the first attempt.
- *Spec review* — grepped `HODITH_SPEC.md` for any Frequency-chart tick-label layout detail; found none, so no spec update needed. Confirmed the spec S9 precedent this shape resembles (see above) doesn't apply the same failure mode here.
- *Data model/migrations, background work* — not applicable; no entity, schema, or WorkManager surface touched.
- *Tests* — see below.

**Tests:**
- `EventTimeFormatTest`'s `formatFrequencyTickLabel is numeric and short, varying by granularity`: WEEK assertion updated to the new `"7/9"` format, using a fixture date that already has a single-digit day so the dropped leading zero is actually exercised.
- `CaseDetailInsightsTabTest`: two new tests at 2x font scale for WEEK and MONTH granularity, asserting `hasVisualOverflow == false` via `GetTextLayoutResult` — `onNodeWithText` alone can't catch ellipsis truncation since the semantics tree still reports the full untruncated string regardless of visual clipping. Both pass against the restructured layout, run on-device, not just compiled.

**Deferred:**
- The item's "verified across locales with longer short-month names" acceptance criterion. The fix itself (a genuinely wider label slot) is locale-agnostic, but automated proof is English-only since the test harness has no `LocalLocale`-override plumbing — confirmed with the user before implementation rather than built out for this small fix; logged as a `TESTING.md` Deferrals entry instead.

**Docs updated:** `docs/PROGRESS.md` (item resolved + removed). `docs/TESTING.md` (Time formatting row's format string corrected, Case Detail Insights row gained a clause, new Deferrals entry for the locale gap).

**Verified:** `ktlintCheck → test (scoped, then full) → lintDebug → assembleDebug` sequential, all green (run after both the first attempt and the revision). `connectedDebugAndroidTest` scoped to `CaseDetailInsightsTabTest` on `Pixel_8_API36(AVD)`, run after the revision: 37/40 passed, including all four Frequency-related tests. The 3 failures — `rhythmCell_zeroCount_staysInert`, `rhythmCell_tap_opensDialogListingOnlyMatchingEvents` (both seen identically on the first attempt's run too, unrelated `RhythmCard` date-dependent tests), and `heatmapDay_tap_keepsIntensityAndTagsOnTheRow_unlikeTheIntensityAndTagFilters` (a `ActivityScenario` teardown timeout, not an assertion failure — the documented pre-existing flaky-emulator pattern) — are all unrelated to and untouched by this diff.

---

## feat/trends-went-quiet-declutter

**Scope:** PROGRESS.md's "Insights Trends: hide other findings behind a link when a Case has gone quiet" — `TrendsCard`'s compact view always sliced to the first 3 findings, so when `WENT_QUIET` (a live-state read on the Case, always prepended first when it fires) led the list, it sat inline next to up to two unrelated historical-shift findings, blurring two different kinds of claim.

**Changes:**
- `ui/casedetail/InsightsTab.kt`: `TrendsCard` computes a `visibleCount` local (1 when the leading finding is `WENT_QUIET`, else the existing `TRENDS_DEFAULT_VISIBLE_COUNT`) and uses it for both the `.take(...)` slice and the "show more" link's visibility condition, replacing the two unconditional `TRENDS_DEFAULT_VISIBLE_COUNT` references. Doc comment updated to explain the exception. New preview scenario (`TrendsCardWentQuietLeadingPreviewContent` + Plain/Intense/Bright `@Preview`s, both light and dark) added alongside the existing "show more" preview family — dark variants go beyond that sibling family's own light-only coverage, added on the user's explicit call after I'd proposed matching the sibling instead (see Checklist walk below).
- `ui/casedetail/InsightsTabTrendsCardTest.kt`: new `trendsCard_wentQuietLeading_showsOnlyWentQuietPlusShowMoreLink`, run on a connected emulator, not just compiled.
- `docs/HODITH_SPEC.md` §10: the Trends bullet's "shows the first 3 by default" line gained the went-quiet-leading exception — caught during this pass's own spec review, not part of the original request.
- `docs/PROGRESS.md`: the resolved item removed entirely; the share-card item's (`feat/insights-trends-share`) design-decision note lost its "See also ... below" cross-reference to the now-removed item.

**Checklist walk (against the working-tree diff):**
- *Duplication* — no inline strings; the link's copy stays the existing generic `voice.insightsTrendsShowMoreAction` — put to the user as an open question (the link always opens the same full list regardless of what's collapsed inline, so a special-cased copy would describe the destination inaccurately, not clarify it) and confirmed rather than decided unilaterally. The new preview content composable mirrors `TrendsCardShowMorePreviewContent`'s body closely, but that's this file's own established one-composable-per-scenario convention (every existing preview family in this file follows the same shape), not new duplication.
- *Decoupling* — no ViewModel/Repository/domain files touched; the change is a pure local `val` inside an existing composable, no business logic moved into or out of it.
- *Complexity & pattern health* — inline ternary, no new helper function (the ternary reads clearly and has exactly one call site, so extracting it would be the "single-caller helper not earning its keep" anti-pattern this checklist itself warns about). `TrendsCard` stays well under the ~150-line composable-split threshold. Reused `TrendFindingRow`/`InsightsCard`/`TextButton` as-is.
- *Dead code & hygiene* — no unused imports (`ktlintCheck` ran clean, twice). `git status` clean aside from the pre-existing untracked `merged_branches.txt` (unrelated, predates this branch, left alone per prior entries). `ktlintFormat` ran once to fix an auto-correctable line-length wrap in the new test; directly counted `\r\n` vs. total lines on all four touched files afterward rather than trusting `git diff`'s CRLF-normalization warning at face value — all four came back fully CRLF, no corruption.
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — new preview functions (`TrendsCardWentQuietLeading{Plain,Intense,Bright}Preview` and their `...DarkPreview` counterparts) match the sibling `TrendsCardShowMore{Plain,Intense,Bright}Preview` naming, extended with the `InsightsBrightCardsDarkPreview`-style `...DarkPreview` suffix already used elsewhere in this file. No new Voice keys, so no cross-voice naming to check.
- *Hardcoded values* — none introduced; `TRENDS_DEFAULT_VISIBLE_COUNT` (the one relevant existing constant) is unchanged and still used for the non-WENT_QUIET branch.
- *Accessibility* — no new tap targets; the show-more `TextButton` is the same pre-existing one, now gated on `visibleCount` instead of the constant directly. Whether the new preview family needed dark variants (its sibling `TrendsCardShowMore*` family has none) was another open question put to the user rather than decided unilaterally; they asked for dark coverage anyway, so all six previews (Plain/Intense/Bright × light/dark) exist for this scenario, going beyond the sibling family it's modeled on.
- *Spec review* — walked HODITH_SPEC.md §10 (the section describing this exact card) end to end: found and fixed the stale "shows the first 3 by default" line (see Changes above). No other section references Trends card slicing.
- *Data model/migrations, background work* — not applicable; no entity, schema, or WorkManager surface touched.
- *Tests* — see below.

**Tests:**
- `InsightsTabTrendsCardTest.trendsCard_wentQuietLeading_showsOnlyWentQuietPlusShowMoreLink`: `WENT_QUIET` leading a 6-finding list, asserts the `WENT_QUIET` sentence renders, a mixed-in gap-shift sentence with the exact values `syntheticFinding(0)` would produce does *not* render, and the show-more link both appears and invokes its callback. Confirmed `insightsGapShiftSentence`'s parameter names/order against its `Voice.kt` declaration rather than assuming, and `formatDays`'s whole-number rendering (`"3 days"`/`"5 days"`, no decimal) before asserting on it.
- Existing `trendsCard_noShowMoreLink_atExactlyTheDefaultVisibleCount`, `trendsCard_showMoreLink_appearsAndInvokesCallback_whenMoreThanTheDefaultVisibleCount`, and `trendsCard_rendersWentQuietSentence` all re-verified as still valid unchanged (none previously mixed `WENT_QUIET` with other findings, so `visibleCount`'s fallback to the old constant leaves their behavior identical) rather than assumed safe from reading the diff alone — confirmed by running the full class, not just the new test.
- Scoped `connectedDebugAndroidTest` run against `InsightsTabTrendsCardTest` on `Pixel_8_API36(AVD)`: 17/17 passed. Full unit suite, `ktlintCheck`, `lintDebug`, `assembleDebug` all green.

**Deferred:** nothing. Two judgment calls (link copy, preview theme/dark coverage) were surfaced as open questions during this pass rather than decided unilaterally — see Checklist walk above for each outcome.

**Docs updated:** `HODITH_SPEC.md` §10 (Trends bullet), `PROGRESS.md` (item resolved + removed, dangling cross-reference from the share-card item trimmed).

**Verified:** `ktlintCheck → test → lintDebug → assembleDebug` sequential, all green; `connectedDebugAndroidTest` scoped to `InsightsTabTrendsCardTest` on `Pixel_8_API36(AVD)` — 17/17 pass.

