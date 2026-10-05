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

## feat/insights-gaps-streaks-split

**Scope:** PROGRESS.md's "Insights tab: split Gaps/Streaks, restyle Gaps & Duration to match Share Story's pattern" item, reworked: no Gaps/Streaks split. The Insights Gaps card and Duration card take the share card's Min/Avg/Max row, and the Tags totals sit side by side as label-over-value columns. The Insights gap labels spell out "gap"; the streak labels keep "streak", since the combined card covers both. The Summary card keeps the Gaps title, and the Duration card has no total.

**Walked:** CLEANUP_CHECKLIST.md checked against the branch's diff in a second pass, after an earlier pass that was written from memory and missed items. Checked and needing nothing: unused imports and dead helpers (ktlint and lint pass; `StatRow`, `formatDays` and the Share streak keys still have callers); Voice keys in all three voices (every new key is a structural default, and both changed info bodies are overridden in each voice); no inline user-visible strings; no em dashes or gamification words in new copy; `@UiTest` tags on the touched Compose classes; the spec and TESTING.md rows against the built behaviour. Not checked on a device: anything that needs the instrumented run.

**Found & fixed:**
- The first pass had no test for a single-event Case's Gaps card (Current gap shown, no Min/Avg/Max row). Added `gapsCard_withOneEvent_showsCurrentGap_andNoMinAvgMaxRow`.
- The Square-fixture figures were unit-tested only as formatting cases. `StatCardRowsTest` now also pins the figures the share card's Gaps panel shows for its fixture. Both surfaces call `gapsStatRows`, so this pins the output rather than proving parity.
- `formatCompactDecimal` and `formatDaysCompact` lived in `ui/casedetail/InsightsTab.kt`, so `viewmodel/StatCardRows.kt` depended on the UI package. Both moved to `viewmodel/CompactFormat.kt`, and their callers and `InsightsFormattingTest` import them from there.
- The Gaps and Duration figures were formatted inline in two places (Insights and the share card). Both now call `gapsStatRows`/`durationMinAvgMax`, so the two surfaces cannot drift.
- `MinAvgMaxRow` and `StatColumn` were private to `ShareCardTemplate.kt`. They moved to `ui/common/StatColumns.kt` so Insights reuses them, and the row takes its labels as a parameter so each surface words its own.
- `StatRow`'s `valueStyle` parameter had one caller (the Tags totals); with that gone it was dead and was removed.
- Voice keys with no remaining caller were removed: the Gaps and Duration labels the first pass replaced, `insightsDurationTotalLabel` once Total left the card, and `shareGapsStreaksTitle` once the Summary panel went back to "Gaps".
- The Gaps and Duration info bodies described only the old figures. Each voice gained "Shortest" on Gaps, and the Duration body lost "total".

**Deferred:**
- The instrumented run (`connectedDebugAndroidTest`) has not been executed: no device is attached. The changed androidTest sources compile. The run needs a device before the PR.
- The Insights Gaps row layout (the two-line "Current gap" label and how the streak labels sit beside it) is for the human to check on a device.

**Docs updated:** HODITH_SPEC §10 (Gaps & streaks, Event duration) and §13 (Square panel titles, structural labels); TESTING.md (share-card assembly, Insights tab rows); PROGRESS.md (item struck, Voice audit list gains this branch's key changes).

---

## fix/share-history-range-line

**Scope:** PROGRESS.md's "Share History card: range on the title line, and a real 'All' range" item. The Log card's kicker is removed, so the resolved range is the card's title line. An unset range resolves to the Case's creation date through today on the card and in the Log tab's note line. The Range selectors (Log tab and Log Share) keep reading "All time" when unset. The filter itself stays unbounded.

**Walked:** CLEANUP_CHECKLIST.md item by item against the diff, in two passes. The first pass was written from memory of the changes and missed the docs; the second, against the real diff, found the stale TESTING.md and spec rows listed below. Items not listed were checked and needed nothing.

**Found & fixed:**
- The range bounds were formatted in three places (the card, the Log tab note, the Log Share button). They now share `logRangeBounds` in `EventTimeFormat.kt`, so the "unset means creation date / today" rule lives in one function.
- `Voice.shareLogRangeNote` took nullable bounds and handled the unbounded cases with "From"/"To"/"All time" text. No caller passes null any more, so those branches were dead. The function takes two strings, and the `shareLogDateFromLabel`/`shareLogDateToLabel` keys are gone.
- The kicker keys `shareLogCardKicker` were removed from all three voices in the same change: "The history", "The record" and "Every entry!".
- Unused imports left by the change (`DOT_SEPARATOR`, `formatDateRangeBound`) were removed.
- The Log tab's range note now shows whether or not a range is set, so an unset range reads as the Case's span.
- The Log Share range selector reads "All time" when unset, matching the Log tab's chip. Its earlier span text was reverted.
- The Log Share selector's value and the "is the range unset" check moved out of `DateRangeSection` into `logShareSelectorValue` and `isUnsetLogRange` (`ShareCardState.kt`). The Intense capitals moved into `kickerText` (`ShareCardTemplate.kt`). Both are now unit-testable. New unit tests: `LogRangeBoundsTest` (nine cases) and `KickerTextTest`. The Log tab note line and the card title are still checked only through their helpers, not their Compose wiring.
- TESTING.md's Share card assembly, Compose UI (Log tab filter row) and Log Share card rows still described the old "All time" label, separate From/To chips and the kicker. They now describe the current behaviour.
- HODITH_SPEC §13's card paragraph said the range was a subtitle. It is the title line.

**Deferred:**
- The instrumented suite was not run on a device, since none was available. The changed androidTest classes compile. Run `connectedDebugAndroidTest` before merging.

**Considered and declined:**
- Putting the resolved span into the Range chip itself. A formatted date pair doesn't fit the chip's width, which is the reason the Log tab already puts bounds on a note line. The chip keeps "All time" as its unfiltered value.
- Storing the Case's creation date as the filter's start. Events can be dated before the Case was created (the log-detail date picker only caps at today), so that would hide them from the "All" view.

**Docs updated:** PROGRESS.md (item removed, since it is resolved). HODITH_SPEC §6: the filter-chip row now describes the combined Range control, the unset-range span, and the unbounded filter. The paragraph had described separate From and To chips, which the combined control replaced earlier.

---

## feat/tags-short-expanded-view

**Scope:** PROGRESS.md's "Insights tab: tag list short/expanded view" item. The Insights tag card collapses past 5 distinct tags to a summary and the busiest three, with a "see all" link to a new full tag list screen that mirrors the Trends list.

**Walked:** every item in CLEANUP_CHECKLIST.md against the diff. Items not listed below were checked and needed nothing.

**Found & fixed:**
- The Trends and Tags cards each built their own right-aligned show-more button. It's now `InsightsShowMoreButton`, used by both.
- The Trends and Tags full-list screens each built their own scaffold (top bar, case subtitle, back arrow, info dialog, scrolling column). It's now `InsightsDetailScaffold`. `TrendsListScreen` moved onto it with no change in behaviour.
- The Trends and Tags view models each combined the same three repository flows. That combine is now `caseInsightsFlow`, used by both.
- `StatRow`, `InsightsDrillDownDialog` and `InsightsCard` were private to `InsightsTab.kt`. They're internal now, so the tag list reuses them rather than copying them. The tag-filter drill-down's event filter is `eventsWithTag`, shared by the card and the list.
- The new helper file held one class. It's named `CaseInsights.kt` to match the rule that a file holding one class is named after it.
- New and edited files had LF line endings. All changed files are CRLF again, matching HEAD.
- Import order and line wrapping in touched files, via ktlint.
- The Tags summary (total events and total tags) sits in one shared composable, `TagsSummary`, used on the card and the full list.

**Deferred:**
- Nothing deferred from this pass.

**Considered and declined:**
- `distinctTagCount` on `StatsSections` defaults to `tags.size`. That keeps the existing test fixtures compiling. The production path always passes the real value from `computeTagBreakdown`.
- `tagsVisibleEntries` has one production caller. It's the compact-card rule the unit tests pin, so the extraction earns its keep, as with `shareDisplayName`.
- The Tags full-list screen has no `@Preview` composables. Per the instruction to skip separate previews, the working state was checked on the card and screen instead.

**Docs updated:** HODITH_SPEC §10 (tag breakdown: summary, collapsed card, full tag list, Share card difference). TESTING.md Compose UI, Case Detail Insights row. PROGRESS.md item struck.

## feat/share-tabs

**Scope:** PROGRESS.md's "Share: replace the chooser dialog with Summary / Insights / History tabs" item. The Case Detail Share icon now opens one Share screen with three tabs instead of a chooser dialog and a second route.

**Walked:** every item in CLEANUP_CHECKLIST.md against the diff. Items not listed below were checked and needed nothing.

**Found & fixed:**
- Case Detail's hand-rolled tab row and the Share screen's tab row were the same code. The row is now `HodithTabRow`/`HodithTab` in `ui/common`, used by both.
- The share screens each held their own Name-on-card state, and the ViewModels held the name and the card format. The name now lives once on the host, above the tabs. The ViewModels hold neither.
- The two share routes each launched the share sheet. `ShareRoute` now merges both ViewModels' share requests and launches once.
- `SharePreviewScreen.kt` and `LogSharePreviewScreen.kt` held only tab content by now. Renamed to `InsightShareTab.kt` and `LogShareTab.kt`, with their tests renamed to match.
- The middle-dot separator was written inline in 15 places. It's now the `DOT_SEPARATOR` constant in `ui/voice`.
- Thirteen retired chooser and title keys removed from all three voices. `shareSectionsPickerLabel` is one structural key ("Include"), not three overrides. Every share Voice key is referenced outside `Voice.kt`.
- Some changed files had LF line endings, left behind by `ktlintFormat`. All changed files are CRLF again, matching the repo.
- The History title is one line, kicker and range together. Its test asserted the old two-line layout, so it was updated.
- The name rule (blank or untouched falls back to the Case's name) is a pure function, `shareDisplayName`, with its own unit test.
- Manual share-card steps 4–7 checked things automation already covers. Removed. Steps 1–2 keep what only a device can check: the capture and the share-sheet handoff.
- `ShareCardTemplateTest` gained a test that a card draws the name it's given, the one check the removed manual steps had been the only coverage for.
- `ktlintFormat` import order and line wrapping in touched test files.

**Deferred:**
- The History card's range on the title line, and a real range for "All" (creation date to today). Both change what the card shows, so they need a decision first. In PROGRESS.md.
- `CaseDetailScreen` is 251 lines and was already that long before this change. Splitting it is a refactor with its own risk, so it's in PROGRESS.md.

**Considered and declined:**
- `share(bitmap)` is duplicated in `ShareViewModel` and `LogShareViewModel`, about six lines each. The two differ only in the file-name prefix. Left as is.
- `CaseDetailViewModel` imports `DOT_SEPARATOR` from `ui/voice`. The ViewModel already imports from `ui` in other files, and the separator is one formatting string, so it stays.
- `shareDisplayName` has one caller. It's the rule the tests cover, so the extraction earns its keep.
- `LOG_SHARE_FIELD_TOGGLE_TAG_PREFIX` keeps its name. It names the History field toggles and matches the other `*_TAG_PREFIX` constants.
- `InsightShareTab` computes `insightsTabState` in the composable, remembered by day. That pattern predates this change. Moving it into the ViewModel is out of scope here.
- `docs/mockups/log-share-prototype.html` is still referenced by the spec and by `LogShareTab`'s comment, so it stays as design history.

**Not verified here:**
- Dark mode for the new tabs and the preview stage. Needs a manual check.
- The 48dp touch target on the Share button. Material's `Button` should enforce it; not measured.
- `DataStoreSettingsRepository.kt` has a compiler warning about an annotation target. It predates this change and this change doesn't touch the file.

**Checks:** ktlint passes. `lintDebug` passes. 1070 unit tests pass. `assembleDebug` passes. Instrumented, scoped: `ShareScreenTest` 6/6, `InsightShareTabTest` 25/25, `LogShareTabTest` 17/17, `SharePreviewOrderFlowTest` 1/1, `CaseDetailScreenTest` 35/35, `CaseDetailInsightsTabTest` 43/43, `ShareCardTemplateTest` 60/60. The full `connectedDebugAndroidTest` suite has not been run for this change.

**Tests:** `ShareScreenTest` (new) covers the title, the three tabs, the Preview heading on every tab, the Include heading, name carry-over across tabs, and which callback each Share button fires. `InsightShareTabTest` and `LogShareTabTest` are re-pointed at the new host, and the name test moved from `InsightShareTabTest` into `ShareScreenTest`. The chooser tests in `CaseDetailScreenTest` became one test that the Share icon opens the screen. Format-toggle and display-name ViewModel cases were removed with the API they tested. `ShareDisplayNameTest` (new) covers the name rule.

**Docs updated:** HODITH_SPEC §13 (the tabbed screen and entry point), TESTING.md (the Share rows, plus a new Share screen row), MANUAL_TEST_PLAN.md (the Share cards steps), PROGRESS.md (the Share item struck, two items added).

## feat/share-insight-section-order

**Scope:** PROGRESS.md's "Share Insight: reorderable sections" item. The Story picker's rows are dragged into order, the card follows, and the order is saved device-wide.

**Found & fixed:**
- The first instrumented run of the picker's drag was on the grip icon only, so a tap on the row's title did nothing. The gesture now sits on the whole row, and a short tap still toggles the section.
- The first drag test drove a stateful harness, so it didn't exercise the ViewModel or DataStore. It's removed. `SharePreviewOrderFlowTest` covers the same gesture through a real Room database, the real `ShareViewModel` and a DataStore file, and it's tagged `@Smoke`.
- The card-position helper was copy-pasted into both instrumented share test files. It's now one `ComposeContentTestRule.cardTitleTop` in `SharePreviewScreenTest.kt`.
- `ShareInsightsSection` moved from `viewmodel/` to `data/`, next to `LogRowField`, so `SettingsRepository` can persist it without `data` depending on `viewmodel`.
- SPEC §13 said "drag-to-reorder handles"; it now says rows, with the long-press.
- TESTING.md's Share card, Share preview, ViewModels rows and manual step 12 now describe the saved order and the new coverage.

**Considered and declined:**
- Drag state uses `remember`, not `rememberSaveable`. A rotation mid-drag ends the gesture, and that's acceptable for a transient interaction.
- The grip's geometry (dot positions, 24 dp box) is inline in `DragGrip`. These are layout values, not product constants, so they don't belong in the domain layer.
- The `ShareCardTemplateTest` order test planned earlier is not written. `ShareCardStateTest` checks the order of `storyOrder`, and the flow test checks the rendered card, so a third test would repeat them.
- The section order is not in the JSON export. It's a device preference like `LOG_VISIBLE_FIELDS`, which the export also leaves out (TESTING manual step 7).
- The grip is not a separate tap target. The whole row is, and it's at least the 48 dp minimum set by the row's switch.

**Deferred:** nothing.

**Docs updated:** SPEC §13 (picker, drag wording); TESTING.md (Share card assembly, Compose UI — Share preview, ViewModels, manual step 12); CLEANUP_LOG (this entry; the oldest entry, `feature/tag-combo-trends`, removed to keep five).
