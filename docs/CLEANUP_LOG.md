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

## feat/insights-trends-went-quiet

**Scope:** PROGRESS.md's "Case quiet vs. abandoned" item — resolved its open 🎨 design decision with the user (surfacing mechanism, threshold logic, relationship to check-ins/`SILENT_FOR` triggers) and implemented it in the same branch as a new Trends finding, rather than leaving the design ruling as a separate pass ahead of a later implementation item.

**Changes:**

- `domain/Insights.kt`/`InsightsEngine.kt`: new `QuietSignalResult`, `QUIET_SIGNAL_MIN_SAMPLE_COUNT`/`QUIET_SIGNAL_RECENT_ACTIVITY_WINDOW_DAYS`, `computeQuietSignal`. Reuses the already-computed `GapStats.isCurrentGapLongest` rather than building a new percentile helper — numerically identical to the item's originally-specified 99th-percentile threshold at realistic Case sizes (they only diverge past ~100 historical gaps), so the simpler existing field was reused instead.
- `domain/Trends.kt`/`TrendsEngine.kt`: new `TrendFindingKind.WENT_QUIET`, prepended first in `computeTrendFindings`'s output when it fires (ahead of gap/streak/frequency shift), gated by a new `recentlyActiveElsewhere: Boolean = false` parameter — defaults preserve every existing 3-arg call site.
- `data/EventDao.kt`/`HodithRepository.kt`/`RoomHodithRepository.kt` (+ `FakeHodithRepository`): new `observeMostRecentLoggedAtAcrossActiveCases()`, a lean `MAX(loggedAt)` scalar query across active Cases — the cross-Case "still logging elsewhere" signal the finding's second condition needs. No schema change.
- `viewmodel/InsightsTabState.kt`/`CaseDetailViewModel.kt`/`CaseDetailScreen.kt`/`TrendsListViewModel.kt`: threaded the new signal from the repository through to `computeTrendFindings`, converting it to a recency boolean at the `statsSections` boundary rather than passing raw timestamps into the domain layer.
- `ui/casedetail/InsightsTab.kt`/`ui/voice/Voice.kt`: new `WENT_QUIET` rendering branch and `insightsWentQuietSentence`/`insightsWentQuietEvidenceLabel` Voice keys ×3, framed as an open question ("still happening, or has it wound down?") per spec §4's "ask rather than silently report" rule — never a statement that the user did something wrong.
- `data/demo/DemoDataSeeder.kt`: doc-comment only. The existing "Nosebleed" `quietSpell` seed already deterministically sets a new longest-gap record (it was built for the Gaps card's own note); confirmed by test that it exercises `WENT_QUIET` for free, no seed-data changes needed.

**Found & fixed:**

- The first implementation pass covered domain/ViewModel/DAO logic thoroughly but shipped no Compose UI test for the new `WENT_QUIET` render branch in `InsightsTab.kt`'s `TrendFindingContent` — caught only when the user asked directly whether UI tests had run. Added `trendsCard_rendersWentQuietSentence` (`InsightsTabTrendsCardTest.kt`, the compact card `CaseDetailScreen` actually renders) and `wentQuietFinding_rendersItsOwnPlank` (`TrendsListScreenTest.kt`, the full-list screen), both asserting the real Voice sentence text renders on screen, not just that the domain layer produces the right `TrendFinding`.

**Checklist walk (against the working-tree diff):**

- *Duplication* — checked against every existing/planned Trends detector and the Gaps card before designing: none of `GAP_SHIFT`/`STREAK_SHIFT`/`FREQUENCY_SHIFT` ever reads the live/current gap (only completed history), and `isCurrentGapLongest` was computed but never rendered anywhere before this — not a restatement of an existing signal. New Voice keys added to all three voices in this same pass. `observeMostRecentLoggedAtAcrossActiveCases` doesn't overlap any existing repository query.
- *Decoupling* — `computeQuietSignal`/`computeTrendFindings` take plain data (`GapStats`, a `Boolean`), no `Clock`/`System.currentTimeMillis()` call in `domain/`; the one `now`/`zone`-dependent computation (the recency window check) stays in `InsightsTabState.kt`, outside `domain/`. No `android.*` import added to any touched `domain/` file.
- *Complexity & pattern health* — no new composables; one new `when` branch in the existing `TrendFindingContent` dispatch, matching `GAP_SHIFT`'s shape exactly.
- *Dead code & hygiene* — found and fixed one issue in this pass: the first draft of `HODITH_SPEC.md`'s new `WENT_QUIET` bullet read as historical narration ("Resolves the former 'Case quiet vs. abandoned' design question…") rather than stating current fact, which the checklist's own "current-state docs" item flags explicitly — reworded to a plain present-tense rule instead. `git status` clean aside from a pre-existing untracked `merged_branches.txt` (not part of this work, left alone).
- *Naming* — `QUIET_SIGNAL_*` constants and `WENT_QUIET` follow the existing `GAP_SHIFT_MIN_SAMPLE_COUNT`/`TrendFindingKind` conventions; no new files.
- *Hardcoded values* — both new thresholds are named `domain/` constants, not inline numbers.
- *Spec review* — `HODITH_SPEC.md` §10 updated (new detector line, plus a note that it always leads the Trends list); confirmed no other section still described the old design-only framing.
- *Tests* — happy path plus boundary/negative cases at every layer (`InsightsEngineTest`, `TrendsEngineTest`, `InsightsTabStateTest`, `CaseDetailViewModelTest`, `EventDaoTest`, `DemoDataSeederTest`, `InsightsTabTrendsCardTest`, `TrendsListScreenTest`) rather than one representative case per function, per explicit request.

**Deferred:** nothing.

**Docs updated:** `HODITH_SPEC.md` §10 — new `WENT_QUIET` entry in the Trends detectors list, plus the list-ordering note. `PROGRESS.md` — "Case quiet vs. abandoned" removed from Standalone (fully resolved and shipped as a Trends detector, not left as a separate design-only item); Story C's intro reworded to note this third already-shipped finding kind; T3's design-decision note gained a cross-reference to reconcile its "dead zone" wording against `WENT_QUIET`; T9's design-decision note gained an open question about whether `WENT_QUIET` belongs on a share card at all, plus a consideration (raised by the user) that the card may need a generation timestamp given `WENT_QUIET`'s live-state sentence stops being accurate the moment new data arrives, unlike the Insights tab's always-fresh recompute.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green (one `ktlintFormat` round-trip: fixed three `Voice.kt` expression-body line-length violations and one `CaseDetailViewModel.kt` chained-call wrap). `connectedDebugAndroidTest` run twice as the emulator became available mid-session: first scoped to `EventDaoTest` (36/36, including the three new `observeMostRecentLoggedAtAcrossActiveCases` cases), then again scoped to `InsightsTabTrendsCardTest`/`TrendsListScreenTest`/`CaseDetailScreenTest` after adding the Compose UI coverage above (46/46, all on `Pixel_8_API36(AVD)`).

---

## feat/insights-trends-scaffold

**Scope:** A retroactive checklist walk + test-coverage audit against T1's already-merged diff (`bae8aed`, PROGRESS.md Story C T1 — the Trends section scaffold and gap/streak-shift migration), run on request rather than alongside the feature's own authoring.

**Found & fixed:**

- `TrendsListViewModel` had no unit test, unlike every other `ViewModel` in the app (`TriggersViewModel`, `ShareViewModel`, etc., each with a matching `FakeHodithRepository`/`FakeClock`/Turbine test). Added `TrendsListViewModelTest.kt`: case icon/name mapping, findings mirroring `stats.trends` from `insightsTabState()`, and the null-case (deleted Case) path.
- T1's own acceptance-criteria checklist in `PROGRESS.md` was never struck even though this diff ships it end-to-end — left as open work indefinitely. Removed the whole T1 entry (matching how other fully-resolved items are retired here, e.g. `feat/case-description-on-home`'s entry) and reworded the Story C intro to state T1 shipped rather than describing it as upcoming. One acceptance criterion ("each finding row independently tappable to its own info dialog") shipped differently than planned — a single shared dialog on the compact card instead — but that's already the accurate, intentional description in `HODITH_SPEC.md` §10, so no further doc fix was needed there.
- `InsightsTabTrendsCardTest`'s synthetic `InsightsTabState.Ready` fixture passed `RhythmDisplay(cells = emptyList())`, violating `RhythmDisplay.cells`' documented "always all 28 day-of-week × time-of-day cells" invariant. Never caught by `./gradlew test` (JVM-only) or code review, only by actually running the class on a device: `RhythmCard`'s `display.cells.first { ... }` (`InsightsTab.kt:589`) throws `NoSuchElementException` on an empty grid, so all three of its tests crashed instead of asserting anything. Fixed by building the real full 28-cell grid (`HeatmapLevel.EMPTY`, count 0) in the fixture instead of an empty list — a test-fixture bug, not a production one, since `computeRhythmStats()` never actually produces a short list.

**Checklist walk (against the merged diff `main...feat/insights-trends-scaffold`):**

- *Duplication* — no inline strings; new Voice keys (`insightsSectionLabelTrends`, `insightsTrendsShowMoreAction`, `insightsTrendsInfoTitle`/`Body`, `trendReliabilityHintLabel`/`PatternLabel`, `insights*ShiftEvidenceLabel`) all added to Serious/Goth/Quirky in the same commit (compiler-enforced via the `Voice` interface).
- *Decoupling* — `TrendsEngine.kt`/`Trends.kt` stay pure Kotlin, no `android.*` imports; `TrendsListViewModel` takes the injected `Clock`, not `System.currentTimeMillis()`.
- *Complexity & pattern health* — the old monolithic `TrendCard` was split into small, single-purpose composables (`TrendsCard`, `TrendReliabilityTag`, `TrendFindingContent`, `TrendFindingRow`, `TrendFindingPlank`), each reused across the compact card and the new full-list screen rather than duplicated.
- *Dead code & hygiene* — old `TrendCard`/its info-icon test correctly removed rather than left dead; no stray untracked files.
- *Repo hygiene* — `git status` clean; no secrets, no local paths.
- *Naming* — new files (`Trends.kt`, `TrendsEngine.kt`, `TrendsListViewModel.kt`, `TrendsListScreen.kt`) match existing package/suffix conventions.
- *Hardcoded values* — `TRENDS_MAX_FINDINGS`/`TRENDS_DEFAULT_VISIBLE_COUNT` are named constants, not inline numbers.
- *Accessibility* — icon-only actions keep non-empty `contentDescription`s; verified via the instrumented run below.
- *Deprecated APIs* — none; `lintDebug` clean.
- *Spec review* — `HODITH_SPEC.md` §10 updated with the new Trends section and its "Trends detectors" subsection, correctly documenting the shared-dialog divergence noted above.

**Deferred:** nothing — both findings were fixed in this pass.

**Docs updated:** `PROGRESS.md` — Story C T1 struck in full (shipped); Story C intro reworded accordingly.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green. `connectedDebugAndroidTest` scoped to the branch's touched instrumented classes (`CaseDetailInsightsTabTest`, `CaseDetailScreenTest`, `InsightsTabTrendsCardTest`, `TrendsListScreenTest`, `ShareCardTemplateTest`) — first run caught the `InsightsTabTrendsCardTest` fixture bug above (3/87 failed); 87/87 green on `Pixel_8_API36(AVD)` after the fix.

---

## feature/big-picture-year-filter

**Scope:** PROGRESS.md's "Big Picture: year filter" — UX settled via a throwaway HTML prototype (`docs/mockups/big-picture-year-filter-prototype.html`). Adds a third **Year** trigger chip narrowing the grid's month range, bundled with the grid's month order reversing to current-first/top (the reversal's own rationale — matching the Year dialog's current-year-first listing — only makes sense alongside the filter, so the two shipped together rather than split across two PRs, per discussion with the user).

**Changes:**

- `BigPictureFilterState.kt`: four new pure functions — `monthsNewestFirst`, `bigPictureYearFilterVisible`, `bigPictureYearOptions`, `filterMonthsByYear` — alongside the existing `isTagVisible`/`isPastOrToday`/legend helpers.
- `BigPictureGrid.kt`: `months` stays ascending (unchanged); a new `filteredMonths`/`displayMonths` layer narrows by the selected year and reverses only at the render boundary, extending `MonthPickerDialog`'s existing display-only-reversal pattern rather than flipping the base list's polarity everywhere. `LaunchedEffect` now scrolls to the top on `displayMonths` identity instead of the bottom on `months.size`. `FilterTriggerChip` gained `isFiltered` (a highlight border/ring on all three chips when narrowed off default, reusing `BrightChip`'s existing selected-state ring for Bright). New `YearFilterChip` composable (single-select, dismisses its dialog on tap, `primaryContainer` to read as its own chip family). Chip content format changed from "Cases N of M" to "Cases: N" (`Voice.bigPictureFilterCount` dropped its `total` parameter). `MonthPickerDialog` now scopes to the active year filter (per discussion with the user) rather than the full range, with its scroll-index math corrected to read against `displayMonths`, not the list it was handed.
- `Voice.kt`: new `bigPictureYearFilterLabel` ("Year") as a shared-default, not per-voice — matches `bigPictureCasesFilterLabel`/`bigPictureTagsFilterLabel`'s un-re-voiced axis-name pattern rather than `bigPictureMonthPickerTitle`'s per-voice verb-phrase pattern, since "Year" carries no personality of its own. No separate dialog-title key — reuses the label, same as Cases/Tags.
- Test hook: `BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG`, a `testTag` on the week-chevron `Box` containing `today`, added because neither `.onFirst()` nor `.onLast()` is a sound way to find "today's week" once month order is current-first; all six existing positional lookups in `BigPictureScreenTest.kt` switched to it.

**Checklist walk (against the working-tree `git diff`):**

- *Duplication* — no inline strings; the Year label/dialog title route through `Voice` like Cases/Tags. `YearFilterChip` is new, deliberate chip family (distinct color), not an accidental copy-paste of `TagFilterChip`.
- *Decoupling* — all new filtering logic (`monthsNewestFirst`, `bigPictureYearFilterVisible`, `bigPictureYearOptions`, `filterMonthsByYear`) is pure Kotlin in `BigPictureFilterState.kt`, not inline in the composable; no `System.currentTimeMillis()`, no `android.*` import. Year filter state stays local `remember` state in `BigPictureGrid`, matching `visibleCaseIds`/`visibleTagNames` — doesn't need to survive navigation, so no ViewModel change.
- *Complexity & pattern health* — `LaunchedEffect(displayMonths)` keys on the list itself (structural equality), not just `.size`, so switching between two same-length years still re-triggers the scroll-to-top; the old `.size`-only key wouldn't have. `monthsNewestFirst` is a one-line wrapper around `.asReversed()` kept as a named, separately-unit-tested function rather than inlined, since it's the exact seam the scroll/index math depends on.
- *Dead code & hygiene* — `docs/mockups/big-picture-year-filter-prototype.html` deleted now that the real implementation and its tests have landed (`git grep`-checked first: every reference lived inside the PROGRESS.md item just struck, nothing else pointed at it). The separate, still-open "Big Picture: filter pill consistency pass" PROGRESS.md item referenced this work as "in-progress" and cited pre-diff line numbers for the chip composables it plans to touch — updated both (Year chip now exists and shares Bright's undifferentiated-color gap; line numbers refreshed) so it isn't a landmine for whoever picks it up next.
- *Repo hygiene* — `git status` clean throughout; no secrets, no local paths; the deleted mockup is the only removed file.
- *Naming* — `YearFilterChip`, `filterMonthsByYear`, etc. follow existing PascalCase/camelCase conventions; `bigPictureYearFilterLabel` joins the existing `bigPicture*FilterLabel` run.
- *Hardcoded values* — no new magic numbers of the product-constant kind (confidence tiers etc.); the `1.5.dp`/`3.dp` border-width tweaks are UI dimension constants, consistent with this file's existing inline `1.dp`/`2.dp` styling values.
- *Accessibility* — no new icon-only controls. Trigger chip tap targets are unchanged in size from the existing Cases/Tags chips (a pre-existing sub-48dp pattern this diff doesn't introduce or worsen).
- *Spec review* — `HODITH_SPEC.md` §9 updated: the scroll-order sentence, the chip label format sentence, and a new bullet for the Year chip/filter/border behavior.
- *Tests* — a first pass only covered the happy paths; asked to check coverage explicitly and found real gaps, since fixed. Unit: `BigPictureFilterStateTest` now also covers `filterMonthsByYear` narrowing to a multi-month year and to a year absent from the data (empty result), all four new functions against an empty months list, `bigPictureYearOptions` when every month falls in one year, and `bigPictureYearFilterVisible`'s same-month boundary — not just the happy-path narrow/reverse/visible cases. Instrumented (`BigPictureScreenTest`): beyond chip absent/present, narrowing, and "All years" reset, added the acceptance-criteria regression the prototype itself hit (the current-first month's trailing future week is dropped outright, not blanked — asserted via chevron count), switching directly between two specific years without routing back through "All", the Year filter composing correctly with an active Case filter (day-detail respects both narrowings at once), and Year's Bright-theme dispatch (parity with the existing Cases/Tags Bright coverage).
  - **Third look, on-device run:** asked to actually run the instrumented suite, not just compile it. First on-device run found two real bugs — both in the tests, not production: (1) `yearChip_present_defaultsToAllYears_whenDataSpansMultipleYears` asserted a bare `": All"` text, which matched *both* the Cases and Year trigger chips (both fully-selected at that point) — Compose's `onNodeWithText` doesn't disambiguate; fixed by anchoring on `hasText("Year") and hasText(": All")` together. (2) Two tests asserted `"December 2025 ›"` existed after the grid's month range included it, but `LazyColumn` only composes what's in the current viewport — with 8 unfiltered months and the list freshly scrolled to the top (current-first order), the earliest month sits off-screen at the bottom and simply isn't in the semantics tree yet; fixed with `performScrollToIndex` to the earliest month's index first, matching this file's existing `hasScrollToIndexAction()` precedent (`TriggersScreenTest`/`CaseDetailScreenTest`/`HomeScreenTest`). Root-caused rather than papered over: confirmed via the failure's own node dump that both trigger chips genuinely rendered `": All"` simultaneously (expected, not a bug), and confirmed the "missing" month was a virtualization artifact, not an actual absence, before writing either fix. Separately, the *test run itself* hung for over 30 minutes on the emulator mid-session — traced to the emulator's own adb daemon going `offline` (`protocol fault` on every subsequent shell command), unrelated to this branch's code; recovered by force-killing the wedged `qemu-system`/`emulator` processes and relaunching the AVD fresh (`-no-snapshot-load`), which came back healthy in 15s. All 52/52 `BigPictureScreenTest` tests green on the second, clean on-device run.
  - **Second look, full re-walk:** asked directly whether the checklist had actually been walked — it had, but only once, before the coverage pass above; the other sections hadn't been re-checked against the diff those new tests introduced. Re-walked every section against the real `git diff main` (not from memory): found and fixed a KDoc cross-reference in `BigPictureFilterState.kt` using an unnecessary fully-qualified name within its own package, and one new unit test bundling assertions for three unrelated functions (`filterMonthsByYear`/`bigPictureYearOptions`/`monthsNewestFirst`) into a single test case, split into three to match this file's one-function-per-test convention (now 27/27). Everything else re-confirmed clean on re-check: no stray `git status` files, no new a11y issue beyond the pre-existing sub-48dp trigger-chip tap target this diff extends but didn't introduce, `bigPictureYearFilterLabel`'s shared-default (not per-voice) treatment double-checked against CLAUDE.md's "added to all three voices" rule — satisfied by inheritance, same as the two adjacent keys it mirrors, not a violation. Confirmed via `MANUAL_TEST_PLAN.md` that in-app filters (Cases/Tags) have no entry there either, so Year needs none.

**Deferred:** nothing formally deferred (no checklist item was raised and declined). The instrumented test run noted above is outstanding, not deferred — it needs to happen before this is considered fully verified.

**Docs updated:** `HODITH_SPEC.md` §9 — scroll order, chip label format, new Year-chip bullet. `TESTING.md` — Compose UI — Big Picture row gained the Year filter coverage clause. `PROGRESS.md` — item struck; the separate filter-pill-consistency item's stale cross-reference and line numbers corrected.

**Verified:** `ktlintCheck → lintDebug → testDebugUnitTest (scoped, then full) → compileDebugAndroidTestKotlin → assembleDebug` sequential, all green. `connectedDebugAndroidTest` scoped to `BigPictureScreenTest` on `Pixel_8_API36(AVD)` — 52/52 green on a clean run, after fixing the two test bugs the first on-device run surfaced (see Tests, third look) and recovering the emulator from an unrelated adb hang mid-session.

---

## feat/case-description-on-home

**Scope:** PROGRESS.md's "Case description isn't shown anywhere in the app" — `CaseEntity.description` was writable on Case Edit but rendered nowhere, not even on Case Detail (the original bug report's assumption). Closes that gap and adds it to Home. The Case Detail treatment specifically went through many rounds of visual feedback once seen live in the app, each a small, deliberate correction rather than rework: `bodyMedium` → `bodySmall`; a "Description" label dropped (redundant with the card's own position); a solid `surfaceVariant` fill (too visually loud, competing with the tab row below it) → an `OutlinedCard` on `colorScheme.background` with an `outlineVariant` border; that border's hue still shifted per theme (blue on Plain, warm on Bright) and its `background` fill still coincided with the Log tab's own tinted screen color, so a comparison prototype was built (see Tooling note) to settle it rather than continuing to guess — landed on wrapping the whole strip between the header and the tab row in the theme's own `colorScheme.surface` (both bars already render on `surface` via stock M3 defaults, so this closes what had been a tinted gap between two already-white bars) with the card itself borderless-filled down to a fixed cross-theme neutral outline, and padding tightened throughout once the first attempt at "tighter" wasn't a noticeable enough change.

**Tooling note:** the border/background comparison was prototyped as an interactive Artifact (Design-canvas type) before touching real code — 7 fill/border options × Plain/Intense/Bright × light/dark, built from the exact hex values in `Color.kt`, including the real Log tab's own note-row and summary-line styles alongside each option so the description card could be checked against every existing muted-text treatment on that screen, not just one. Not committed to the repo (unlike `docs/mockups/*.html` prototypes elsewhere in this log) since the Artifact tool itself hosted it end to end.

**Changes:**

- `CaseEditViewModel.kt`: `CASE_DESCRIPTION_MAX_LENGTH` 280 → 120 → 90 across rounds. Referenced symbolically everywhere else (`BackupValidationResult.kt`, both `CaseEditViewModelTest`/`BackupValidationResultTest`), so no other code change needed at any step.
- `HomeViewModel.kt`: `HomeCaseRow` gained `description: String? = null`; `homeCaseRows`' mapping passes `case.description` through.
- `HomeScreen.kt`: `HomeCaseRowBody` and `BrightHomeCaseListItem` each gained an identical conditional description `Text` (`bodySmall`, `onSurfaceVariant`, `maxLines = 2`, `TextOverflow.Ellipsis`) between the name and the existing counts/ongoing `when` block. One `previewRows` entry given a description for Preview visibility.
- `CaseDetailScreen.kt`: the description now renders inside a full-width `Surface(color = colorScheme.surface)` sitting between the `TopAppBar` and `SecondaryTabRow`, containing an `OutlinedCard` (`containerColor = Color.Transparent`, `border = BorderStroke(1.dp, CaseDescriptionBorderColor)`) wrapping the description `Text` (`bodySmall`, `onSurfaceVariant`). New top-level `private val CaseDescriptionBorderColor = Color(0x66828282)` — deliberately not theme-derived (see Scope). Shown once regardless of selected tab, guarded on `case?.description != null`.
- `DemoDataSeeder.kt`: `CaseSeed` gained `description: String? = null`, threaded into the seeded `CaseEntity`; set on two of the seven seeds (Coffee, Migraine) and left `null` on the rest, following this file's existing convention of populating optional fields "on only some, not all, not none" so both rendering paths stay exercised after a fresh seed.

**Checklist walk (against the working-tree `git diff`):**

- *Duplication* — the description `Text` block is near-identical between `HomeCaseRowBody` and `BrightHomeCaseListItem`; considered extracting a shared composable, but the existing counts/ongoing `when` block in those same two composables is already duplicated the same way without extraction, so this follows established (if imperfect) precedent rather than introducing a new pattern. No inline strings — the description's own text is user-authored case data, same status as notes/tags, correctly not `Voice`-routed; no UI-chrome label remained to route through `Voice` once the label itself was dropped.
- *Decoupling* — no business logic in the touched composables beyond a null check; no `System.currentTimeMillis()`, no `android.*` import; `CaseDetailScreen` reads `description` off the `CaseEntity` it already had in scope, no new `CaseDetailUiState`/ViewModel plumbing.
- *Complexity & pattern health* — no new component reimplementing M3; the final `Surface`-band treatment is new to this screen (no existing precedent to reuse), but composed entirely from stock M3 pieces (`Surface`, `OutlinedCard`) rather than a bespoke one.
- *Dead code & hygiene* — `TextOverflow`, `BorderStroke`, `OutlinedCard`, `Surface` imports all added and used; no unused imports, no commented-out code. No prototype to clean up in-repo — the comparison Artifact lived entirely in the Artifact tool, not as a tracked file.
- *Repo hygiene* — `git status` clean throughout; no secrets, no local paths; only the intended files touched.
- *Naming* — no new files/composables; `CaseDescriptionBorderColor` sits alongside this file's other top-level constants (`LOG_TAB`, `HUNCH_HISTORY_SHOWN_INITIAL`) by location, but as a non-const `Color` its PascalCase matches `Color.kt`'s own convention for named color values (`PlainLightPrimary`, `BrightLightHeadingInk`) rather than those `SCREAMING_SNAKE_CASE` `Int` constants.
- *Hardcoded values* — `maxLines = 2` is a UI layout constant, not a product constant (confidence tiers/nudge thresholds/etc.), consistent with inline `maxLines`/`overflow` literals elsewhere in the codebase (`InsightsTab.kt`, `ShareCardTemplate.kt`). `CaseDescriptionBorderColor = Color(0x66828282)` is a deliberate fixed literal, not a case of "should use a theme value instead" — the whole point of the final round was that the theme-derived `outlineVariant` border read as inconsistent across the three voices; named as a top-level `private val` with a comment stating why, rather than left as a bare literal inline.
- *Accessibility* — no new icon-only controls or tappable targets; the description block is plain, non-interactive text.
- *Spec review* — `HODITH_SPEC.md` updated: Home row's contents line, Case Detail row's header description, and the New/edit Case cap (280 → 90).
- *Tests* — `HomeViewModelMappingTest` (the file `HomeViewModel.kt`'s own KDoc points to for `homeCaseRows`' field-mapping coverage, not the StateFlow-level `HomeViewModelTest`): `maps case identity fields through` now also asserts `description`, plus a new `description is null when the case has none` case, both against the pure `homeCaseRows` function. `DemoDataSeederTest` — which exists specifically to pin each seed's distinguishing characteristic (Coffee's surge, Migraine's ongoing event, etc.) — gained `seed gives Coffee and Migraine a description, and leaves the rest without one`, closing a real gap where the new `CaseSeed.description` field had no coverage at all. `HomeScreenTest`: description renders when set, renders nothing (that string doesn't appear) when unset. `CaseDetailScreenTest`: description text renders when the Case has one, doesn't when it doesn't (rewritten from an earlier label-based assertion once the label was dropped). `CaseEditViewModelTest`'s existing `CASE_DESCRIPTION_MAX_LENGTH` boundary test re-verified green against each cap value across rounds (references the constant symbolically, no edit ever needed). None of the later `CaseDetailScreen.kt` visual-treatment changes (fill/border/padding swaps) needed test changes — `CaseDetailScreenTest` asserts on the description text itself, never the container.

**Deferred:** nothing raised and declined.

**Docs updated:** `HODITH_SPEC.md` — Home row, Case Detail row, New/edit Case cap. `TESTING.md` — ViewModels row (`HomeCaseRow.description` clause) and Compose UI row (Home/Case Detail description rendering clause).

**Verified:** `ktlintCheck → lintDebug → test → compileDebugAndroidTestKotlin → assembleDebug` sequential, all green after every round, confirmed again on the final implementation. Manually checked live in the app by the user (Plain, per the specific concerns raised) — approved. `connectedDebugAndroidTest` run scoped to `HomeScreenTest`/`CaseDetailScreenTest` on `Pixel_8_API36(AVD)` — 50/50 green, including all four new description cases (`caseRow_showsDescription_whenSet`, `caseRow_showsNoDescriptionText_whenUnset`, `description_showsText_whenCaseHasOne`, `description_showsNothing_whenCaseHasNone`).

---

## fix/settings-switch-row-label-wrap

**Scope:** PROGRESS.md's "Settings switch rows crowd the label against the switch at large system font sizes" — the shared `RowWithInfo` composable laid out its label and trailing control in a `Row(Arrangement.SpaceBetween)` with no weight on either side, so a long label at large system font scale grew into the trailing `Switch` instead of wrapping.

**Changes:**

- `SectionWithInfo.kt`: `LabelWithInfo` gained a `modifier: Modifier = Modifier` parameter, applied to its own `Row`; `RowWithInfo` passes `Modifier.weight(1f, fill = false)` at that call site so the label's `Row` is capped to the space `SpaceBetween` would otherwise let it consume, letting `Text`'s existing default wrapping kick in. `SectionWithInfo`'s own call to `LabelWithInfo` (no trailing content to balance against) is left on the default `Modifier`.
- `SettingsScreenTest.kt`: `setContent` gained a `fontScale` parameter (default `1f`), applied via `CompositionLocalProvider(LocalDensity provides Density(...))`; new test `cloudBackupRow_atLargeFontScale_labelDoesNotOverlapSwitch` asserts the label and switch bounds don't overlap at `LARGE_FONT_SCALE` (2x, Android's largest standard accessibility step), reusing the existing `overlapsRect` test helper (`ui/common/RectOverlap.kt`) already used by `TriggersScreenTest`/`HomeScreenTest`/`CaseDetailScreenTest` for the same class of check.

**Checklist walk (against the working-tree `git diff`):**

- *Duplication, Decoupling* — no findings; no Voice strings, ViewModel, or domain code touched. This is a shared-component fix (`RowWithInfo` has exactly two call sites app-wide — the reported cloud-backup toggle in `SettingsScreen.kt` and the check-in toggle in `CaseEditScreen.kt` — both fixed by the one change).
- *Complexity & pattern health* — no new state, no new composables; `LabelWithInfo`'s existing `remember { mutableStateOf(false) }` untouched.
- *Dead code & hygiene* — a first attempt imported `androidx.compose.foundation.layout.weight` explicitly, which shadowed `RowScope.weight`'s member-function resolution with an internal `RowColumnParentData` property of the same name and failed to compile (`compileDebugKotlin`, not caught by `ktlintCheck`); removed the import once traced — `RowScope.weight()` needs no import at all, being a member of the implicit `RowScope` receiver. Caught two more findings on the walkthrough itself, both fixed with sign-off: the new test's `fontScale = 2f` was a bare magic literal with no prior pattern in the codebase to point to (extracted `LARGE_FONT_SCALE` with an explanatory comment, mirroring the file's existing `TEST_NOW_MILLIS` precedent); and TESTING.md's Compose UI coverage row didn't mention the new font-scale check (added a clause to the existing Data-actions coverage description).
- *Repo hygiene* — no secrets, no stray files; `git status` clean throughout.
- *Naming, hardcoded values, accessibility, deprecated APIs* — no findings.
- *Spec review* — not spec-level behavior (`RowWithInfo`'s exact wrap point isn't documented in HODITH_SPEC.md); nothing to update.
- *Tests* — regression test added for the exact reported bug; ran on-device both before and after the `LARGE_FONT_SCALE` extraction.

**Deferred:** nothing.

**Docs updated:** `TESTING.md` — Compose UI coverage row's Data-actions clause now names the font-scale label/switch overlap check. `PROGRESS.md` — item struck.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green (`test`'s one failure, `HomeViewModelTest`'s `onQuickLogTap on an ongoing START_STOP case starts a second concurrent event`, reproduced identically on a clean `main` with this branch's changes stashed — confirmed pre-existing and unrelated, not a regression from this diff). `connectedDebugAndroidTest` scoped to `SettingsScreenTest` — 28/28 green on `Pixel_8_API36(AVD)`, run twice (before and after the hygiene fixes above), including the new test both times.

**Post-push follow-up:** CI's `ui` instrumented shard failed the new `cloudBackupRow_atLargeFontScale_labelDoesNotOverlapSwitch` test with `assertIsDisplayed()` reporting the switch not displayed — not one of `FLAKY_TESTS.md`'s known nondeterministic patterns, but a genuine test bug: CI runs on the `pixel_6` emulator profile (`instrumented-tests.yml`), a smaller screen than the local `Pixel_8_API36` this branch was verified against, so at `LARGE_FONT_SCALE` the cloud-backup row falls below the fold before the assertion runs. Fixed by calling `performScrollTo()` on the switch node first, matching this same file's existing below-the-fold pattern (`loadDemoData_tapInvokesCallback`'s comment on the Developer Mode plank). Re-verified locally (28/28 on `Pixel_8_API36(AVD)`); CI re-run pending.
