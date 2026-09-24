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

---

## fix/case-log-sort-persistence-and-sizing

**Scope:** PROGRESS.md's "Case Log sort order resets on navigating away; sort row is oversized" — two issues reported against the same Log tab row: `CaseDetailViewModel.logSortOrder` was in-memory-only state that reset to `BY_START` on every nav away/back (ViewModel recreation), and the sort row's text/padding read oversized next to the log rows beneath it.

**Changes:**
- `data/SettingsRepository.kt`/`DataStoreSettingsRepository.kt`: `observeLogSortOrder`/`setLogSortOrder`, following `observeTheme`'s plain-enum DataStore pattern (not `BigPictureDetail`'s custom serialize/parse, since `LogSortOrder` is a two-value enum).
- `data/FakeSettingsRepository.kt`: matching fake field + impl.
- `viewmodel/CaseDetailViewModel.kt`: `settingsRepository` injected; `logSortOrder` now sourced directly from `settingsRepository.observeLogSortOrder()` instead of a local `MutableStateFlow` — no cached value left to go stale on recreation. `setLogSortOrder` persists via `viewModelScope.launch`.
- `ui/common/SegmentedChoiceRow.kt`: new optional `textStyle`/`segmentHorizontalPadding`/`segmentVerticalPadding` params on both `SegmentedChoiceRow` and `BrightSegmentedChoiceRow`, defaulted to the prior hardcoded values so every other caller (Case Edit, Settings, Insights, etc.) is unaffected.
- `ui/casedetail/CaseDetailScreen.kt`: Log tab's sort label and `SegmentedChoiceRow` call pass `bodyLarge` (down from `labelLarge`) and tighter padding (`12dp`/`4dp`, down from `16dp`/`7dp`).
- `docs/HODITH_SPEC.md` §6: the sort toggle's line updated from "not persisted, not a schema field" to describe the new `SettingsRepository`/DataStore-backed, cross-Case device preference.
- `docs/PROGRESS.md`: the resolved item removed; a new, separate investigation item added for Big Picture's Case/Tag/Year filters, which turned out to have the same non-persistence gap (worse — not even ViewModel-scoped) but weren't part of this bug report and are left as an open ruling (persist or intentionally session-only?) rather than folded into this fix.
- `docs/TESTING.md`: the `ViewModels` and `Compose UI` rows each gained a clause for the new persistence and sizing coverage.

**Checklist walk (against the working-tree diff):**
- *Duplication* — no new user-visible strings (persistence + styling only); existing `Voice.logSortLabel`/`logSortByStartLabel`/`logSortByEndLabel` untouched. New `SettingsRepository` methods mirror `observeTheme`'s shape rather than inventing a new pattern.
- *Decoupling* — `CaseDetailViewModel` still imports nothing from `androidx.compose.*`; the new `TextStyle`/`Dp` params live in the UI layer (`SegmentedChoiceRow.kt`/`CaseDetailScreen.kt`), not the ViewModel. No `domain/` files touched.
- *Complexity & pattern health* — reused `SegmentedChoiceRow` via new defaulted params rather than forking a Log-tab-specific component. Caught and fixed one real gap here: the new `segmentHorizontalPadding`/`segmentVerticalPadding` params only affect the Bright-theme render branch (`BrightSegmentedChoiceRow`) — PLAIN/INTENSE's M3 `SegmentedButton` manages its own chrome and silently ignores them. Left as-is (M3's default control was never the "oversized" offender — only Bright's custom `16dp`/`7dp` pill padding was), but added a doc comment on the params so a future reader isn't misled into thinking they apply everywhere; `textStyle` does apply to both branches.
- *Dead code & hygiene* — no unused imports (ktlintCheck ran clean). `git status` clean aside from the pre-existing untracked `merged_branches.txt` (unrelated, left alone, as noted in prior entries).
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — no new files; `observeLogSortOrder`/`setLogSortOrder` match the existing `observe*`/`set*` pattern.
- *Hardcoded values* — the new `12.dp`/`4.dp` sort-row padding is inline UI layout tuning (same as the original `16.dp`/`7.dp` it replaces), not a product/domain constant, so no named constant warranted.
- *Accessibility* — considered, declined to change: the Bright pill's touch target was already below the 48dp guideline before this change (`labelLarge` + `7dp` vertical padding ≈ 34dp) across all eight `SegmentedChoiceRow`/`BrightSegmentedChoiceRow` call sites, not just this one. This change's padding reduction (`4dp` vertical) narrows it further for the Log tab specifically (≈32dp) but doesn't newly introduce the gap — it's a pre-existing, repo-wide pattern in the shared component. A systemic fix (e.g. `minimumInteractiveComponentSize()`) touches every caller and is out of scope for this bug fix; not tracked as a new PROGRESS.md item since it's cosmetic-adjacent and no user has reported it, but flagged here for visibility.
- *Deprecated APIs* — the `@Inject`-annotation-target Kotlin compiler warning seen during `compileDebugKotlin` is pre-existing (same line, unrelated to this diff) and affects every `@Inject constructor` in the codebase, not something newly introduced here.
- *Spec review* — HODITH_SPEC.md §6 updated (see Changes above); confirmed no other section describes sort-row behavior.
- *Data model/migrations* — no Room schema touched (DataStore Preferences key, like `theme`/`bigPictureDetail`, needs no migration); confirmed export/import JSON doesn't include Settings preferences (same precedent as `theme`/`bigPictureDetail`, both excluded as device prefs, not investigation data — see MANUAL_TEST_PLAN item 7 in TESTING.md).
- *Tests* — see below.

**Tests:**
- `CaseDetailViewModelTest.kt`: new `` `setLogSortOrder persists across a fresh ViewModel instance` `` — constructs a second `CaseDetailViewModel` sharing the same `FakeSettingsRepository`, proving the actual regression (a fresh VM, not just the fake, reads back the persisted value).
- `CaseDetailScreenTest.kt`: new `logSortLabel_textHeight_matchesEventRowPrimaryLineHeight` — bounds-height comparison between the sort label and an event row's primary time text, both now `bodyLarge`. First run on a connected emulator failed (`169.0` vs `63.0`): `EventRow`'s outer `Row` is clickable, which merges its children's semantics by default, so the un-scoped query matched the whole row (both text lines + padding) instead of the time `Text` alone. Fixed with `useUnmergedTree = true` on both lookups, matching this file's own existing precedent (`logTab_fullScreenList_lastRowsStopButton_doesNotOverlapRetroLogFab`); passes on-device after the fix, confirming the sizing change actually holds at runtime, not just at compile time.
- Full unit suite and `ktlintCheck`/`lintDebug`/`assembleDebug` all green. `connectedDebugAndroidTest` run scoped to `ui.casedetail` on a connected emulator (`Pixel_8_API36(AVD)`) surfaced three unrelated pre-existing failures — `CaseDetailScreenTest.eventRow_rendersInTwentyFourHourTime_whenLocalTimeFormatIsTwentyFourHour`/`eventRow_click_invokesOnEditEventForThatEvent` and `CaseDetailInsightsTabTest.rhythmCell_tap_opensDialogListingOnlyMatchingEvents` all fail to find text built from an unpinned `ZoneId.systemDefault()`, consistent with this emulator's configured timezone disagreeing with whatever zone those tests were authored/last verified against. Confirmed unrelated to this diff: none of those three tests or their files were touched here, and a scoped re-run of an untouched pre-existing sort-toggle test in the same file (`logSortToggle_shown_whenTheCaseTracksDurationAndHasEvents`) passed clean in isolation. Not fixed here — different files/root cause, out of this bug fix's scope — but worth a `TESTING.md` "Known environment issues" entry and a zone-pinning pass over those three call sites; flagged for a follow-up, not silently dropped.

**Deferred:**
- The sort row's new padding values (`12dp`/`4dp`) are a reasoned starting point, confirmed to produce equal-height text on-device (see Tests above) but not visually reviewed for overall balance — same "human does a final visual pass" convention. Worth a look in both light/dark and across Plain/Bright/Intense before merging.
- Big Picture's filter-persistence gap, found in passing — spun out to its own PROGRESS.md investigation item rather than fixed here (see Changes above); not this bug report's scope.
- The three pre-existing unpinned-zone instrumented test failures found while verifying this branch's own instrumented test (see Tests above) — real gap, wrong branch to fix it on.

**Docs updated:** HODITH_SPEC.md §6, TESTING.md (`ViewModels`/`Compose UI` rows), PROGRESS.md (item resolved + removed, new Big Picture item added).

**Verified:** `ktlintCheck → test → lintDebug → assembleDebug` sequential, all green; `connectedDebugAndroidTest` scoped to `ui.casedetail` and to the two new/touched tests individually on `Pixel_8_API36(AVD)` — both pass in isolation (the package-wide run's three failures are the pre-existing, unrelated ones noted above).

---

## feat/insights-trends-weekday-weekend

**Scope:** PROGRESS.md's Story C T8 ("Detector: cycles and seasonality") — scoped down after discussion to just its cheapest sub-feature, a case-wide `WEEKDAY_WEEKEND_SPLIT` Trends detector testing whether a Case's events cluster on weekends vs. weekdays against the fixed 2/7 calendar baseline. The eleventh detector in the Trends roster. T8's other two sub-features (autocorrelation cycle detection, month-of-year comparison) split off into a new Deferred item (D5) rather than shipping partial code for them. Also closes out Story C as a section: with T8 resolved and T9 (the only other item left) moved to Standalone, the whole Story C heading is retired from PROGRESS.md.

**Feasibility ruling (resolved with the user before implementation):** three design questions settled by direct confirmation rather than inferred: (1) build only the weekday-vs-weekend fallback now, defer the rest — not attempt full autocorrelation on spec; (2) the statistic is a direct Monte Carlo binomial null via `permutationPValue` called directly with a custom closure (each shuffle draws every event's weekend/weekday membership independently as a Bernoulli(2/7) trial) — neither `labelShufflePValue` (not two equal-size cross-sectional groups) nor tag timing's look-elsewhere correction (no bucket search — weekend is fixed in advance) fit; (3) two-directional (weekend-heavy vs. weekday-heavy), unlike tag timing's always-over-concentration convention, since a Case's overall rhythm has no default lean the way a single tag's clustering does.

**Changes:**

- `domain/PermutationSignificance.kt`: `weekdayWeekendSeedFor` — same minimal shape as `timelineShuffleSeedFor` (just caseId + sampleCount, no tag/outcome/dimension to key on), trailing `+3` discriminator.
- `domain/StatsEngine.kt`: `computeWeekdayWeekendFindings`/`clearsWeekdayWeekendFloor` (the detector) + five new named constants, placed alongside `computeTagTimingFindings`.
- `domain/Insights.kt`: new `WeekdayWeekendResult` model.
- `domain/Trends.kt`: new `TrendFindingKind.WEEKDAY_WEEKEND_SPLIT` — reuses existing `TrendFinding` fields (`priorValue`/`recentValue` for baseline/observed share) rather than adding new ones, unlike tag timing's own `weekday`/`timeOfDay` addition.
- `domain/TrendsEngine.kt`: `computeTrendFindings` appends `WEEKDAY_WEEKEND_SPLIT` last, always `PATTERN`.
- `ui/voice/Voice.kt`: `insightsWeekdayWeekendSentence`/`insightsWeekdayWeekendEvidenceLabel`, implemented in all three voices in this same commit.
- `ui/casedetail/InsightsTab.kt`: new `WEEKDAY_WEEKEND_SPLIT` branch in `TrendFindingContent`'s dispatch.
- `data/demo/DemoDataSeeder.kt`: new `weekendDateFor` showcase mechanism (mirrors `eveningHourFor`'s "bypass the normal random draw, pin into the target bucket" shape, shifting the date to the nearest Saturday/Sunday instead of the hour) applied to the existing "Argument" Case — picked because it carried no other Story C showcase, confirmed by checking every `CaseSeed` field and cross-referencing `DemoDataSeederTest.kt` before choosing it; "Noisy neighbours" was ruled out specifically because it's already `RECURRENCE_SHAPE`'s dedicated, single-finding showcase, and shifting its dates risked breaking that pinning.
- `docs/HODITH_SPEC.md` §10: one new paragraph ("Weekday vs weekend"), following the established per-detector prose shape.
- `docs/PROGRESS.md`: T8's section removed entirely (fallback shipped, resolving the item); a new Deferred item (D5) added for the un-shipped autocorrelation/month-of-year scope; T9 moved into Standalone under its title (no longer numbered, since Story C's sequencing no longer applies), with its own cross-reference to "T1" reworded to describe the change directly rather than by story-item number; the whole Story C heading and its historical intro paragraph removed now that no items remain under it; the "How this file is organised" bullet list's Story C entry dropped to match.
- `docs/TESTING.md`: Stats & visual data prep row gained a weekday-vs-weekend detection clause.
- Two doc-comment-only fixes in already-shipped code: `TrendsEngine.kt`'s and `InsightsTabState.kt`'s "(PROGRESS.md T9 retires this)" references reworded to name the item by title, since T9 is no longer a numbered PROGRESS.md item after this pass's reorg.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no inline strings; both new sentence/evidence-label calls go through `Voice`. `weekendDateFor` mirrors `eveningHourFor`'s exact shape rather than being written independently, since both solve the identical "bypass the normal random draw, pin into a target bucket, clamp into span" problem.
- *Decoupling* — confirmed by grep this pass (not assumed by analogy to a prior pass's check): no `android.*` import and no `System.currentTimeMillis()` added anywhere under `domain/` in this diff. `computeWeekdayWeekendFindings` takes no `now`/`Clock` — pure function of `EventWithTags`, matching every prior detector.
- *Complexity & pattern health* — `clearsWeekdayWeekendFloor` and `weekendDateFor` are each single-caller, kept as separate functions anyway for the same testability/doc-clarity precedent `clearsShareFloor`/`eveningHourFor` already set, not a premature extraction. `TrendFindingContent`'s `when` grew by one small branch (~9 lines), no length concern.
- *Dead code & hygiene* — no unused imports (`ktlintCheck` and the full build passed clean, run several times across the pass). A throwaway probe test (printlns only, no assertions) was added mid-pass to empirically check the permutation-significance gate's actual boundary behavior before writing a real test around it — deleted immediately after its one use, confirmed gone by grep before committing. `git status` clean aside from the pre-existing untracked `merged_branches.txt` (flagged unrelated in every prior entry, still left alone, not touched).
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — `insightsWeekdayWeekendSentence`/`insightsWeekdayWeekendEvidenceLabel` follow the established pattern, added to all three voices in this commit. `computeWeekdayWeekendFindings`/`WeekdayWeekendResult` match the domain layer's existing detector-naming shape.
- *Hardcoded values* — all five new constants are named `internal const val`s with a doc comment explaining each against tag timing's own floors, including a specific note on why the dual absolute/relative floor is structurally asymmetric at this fixed baseline (see Tests below) rather than left unexplained.
- *Accessibility* — no new tap targets; the new finding reuses the existing Trends row/plank tap surface.
- *Deprecated APIs* — none introduced.
- *Spec review* — `HODITH_SPEC.md` §10 gained the paragraph described above, explicitly cross-referencing PROGRESS.md's new deferred item for the un-shipped remainder.
- *Tests* — see below.

**Tests:**

- `StatsEngineTest.kt`: `computeWeekdayWeekendFindings` — a planted weekend-heavy Case and a planted weekday-heavy Case (both confirmed significant, direction/shares/sample count correct), an exact-baseline null (delta exactly 0), a fails-both-floors null, below the minimum sample count even with a stark effect, and a boundary case isolating the one reachable half of the dual floor (clears the relative floor but not the absolute one — the mirror direction is structurally unreachable given the fixed 2/7 baseline, documented in `StatsEngine.kt`'s own doc comment rather than silently absent).
- `PermutationSignificanceTest.kt`: `weekdayWeekendSeedFor` — determinism, differs by caseId, differs by sampleCount, no collision with `trendSlopeSeedFor`/`timeOfDaySplitSeedFor`/`tagTimingSeedFor` for a shape that could otherwise collide.
- `TrendsEngineTest.kt`: a `PATTERN` finding with tag/outcome/weekday/timeOfDay/changePointDate all confirmed null, and ordering after tag timing (using a combined fixture deliberately checked to still clear the detector's own floor once tag timing's own showcase events are mixed in, not assumed to).
- `VoiceTest.kt`: existing reflection-based invariants picked up the two new keys automatically — confirmed by running it, not assumed.
- `InsightsTabTrendsCardTest.kt` (instrumented, new methods): a rendered sentence for each direction.
- `DemoDataSeederTest.kt`: Argument clears the minimum sample size, and the real seeded data produces the actual `WEEKDAY_WEEKEND_SPLIT`/`PATTERN` finding, not just a sample-count assertion.

**Follow-up (asked directly whether the coverage was actually comprehensive, not just green):** caught two real gaps in the first draft:

- The first draft's only "null" tests failed the descriptive floor outright (either both sub-floors at once, or the sample-count floor) — nothing isolated the dual floor's two conditions independently, and nothing exercised the permutation-significance gate distinctly from the descriptive floor. Probed the actual (deterministic, seeded) output across a range of sample counts and deltas before writing anything: found that at this detector's chosen constants, clearing the descriptive floor at the minimum sample count already clears the 5% significance bar too, with no counterexample found from n=40 to n=1000 — unlike tag timing, whose look-elsewhere correction across several buckets leaves real room between the two. That's a genuine, now-documented property of this design, not a coverage gap; no "clears floor but insignificant" test exists because none is constructible. Documented directly in `StatsEngine.kt`'s doc comment so a future reader doesn't go looking for the missing test.
- The dual floor itself was under-tested for the opposite reason: initially assumed (wrongly) that both "clears absolute not relative" and "clears relative not absolute" needed separate tests, mirroring tag timing's own two boundary tests. Working the algebra for this detector's *fixed* 2/7 baseline (unlike tag timing's per-bucket one) showed the absolute floor (0.15) always exceeds the relative floor's threshold at this baseline (0.5 × 2/7 ≈ 0.1429), so "clears absolute, fails relative" is structurally impossible here — only the reverse direction is reachable. Verified this with the same probe rather than trusting the algebra alone (n=1000, 432 weekend events landed exactly in the gap and returned null as expected). Added the one reachable boundary test and a doc-comment note explaining why its mirror doesn't exist, rather than leaving that asymmetry silently unaccounted for.

**Deferred:** nothing raised and declined.

**Docs updated:** `HODITH_SPEC.md` §10 — one new paragraph. `TESTING.md` — Stats & visual data prep row gained a weekday-vs-weekend clause. `PROGRESS.md` — T8 struck entirely; a new Deferred item (D5) added for the un-shipped scope; T9 moved to Standalone under its title; the now-empty Story C heading, its intro paragraph, and its "How this file is organised" bullet all removed.

**Verified:** `ktlintCheck → lintDebug → test (scoped, then full) → compileDebugAndroidTestKotlin → assembleDebug` sequential, all green — run several times across the pass (after the initial implementation; after the ktlint line-length fixes; after the coverage follow-up's new tests). `connectedDebugAndroidTest` scoped to `InsightsTabTrendsCardTest` on `Pixel_8_API36(AVD)` once an emulator became available: 16/16, 0 failed, 0 skipped — including both new `trendsCard_rendersWeekdayWeekendSentence_weekendHeavy`/`_weekdayHeavy` methods, actually run, not just compiled.
