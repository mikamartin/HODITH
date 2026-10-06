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

## feat/trends-visual-redesign

**Scope:** the Trends visual redesign: the Insights card row (headline, Pattern/Hint chip, compared figures, evidence count), the full-list row (same row), p-value ordering, and sort-before-cap. PROGRESS.md's Trends item now holds only the open work.

**Walked:** CLEANUP_CHECKLIST.md against the branch's diff, section by section, in two passes. The second pass found the stale instrumented assertions and the log claims corrected below. Verified in this pass: `ktlintCheck`, `test` (1167 unit tests, none failing), `lintDebug` and `assembleDebug` pass; `compileDebugAndroidTestKotlin` compiles. Not run: the instrumented tests (no device this pass), and light and dark mode on a device.

**Found & fixed:**
- Instrumented tests still asserted sentence text the rows no longer render: every row test in `TrendsListScreenTest`, plus the frequency-shift and gap-shift assertions in `CaseDetailInsightsTabTest`. They now assert the headline and the figures. The compact-card test asserted no reliability chip; the chip appears on both surfaces now, so it asserts the chip exists.
- Two inline English phrases in the row code moved into `Voice`: the tag combo's "of" (`trendCountOfTotal`) and the tag-timing bucket (`trendTimingBucket`, shared with the sentence through `trendBucketPhrase`). The case-wide reference moved to `trendCaseWideReference`.
- Stale KDoc in `Trends.kt`, `TrendsEngine.kt` and `InsightsTab.kt`: the Hint/Pattern and "every detector" wording, the Insights card description, and the evidence line, which no longer sits under a sentence. Rewritten to describe the code as it is. One over-long KDoc line reflowed.
- `VoiceTest` gained a `TrendFinding` sample generator covering every kind, direction and outcome, so the reflection invariants check the headlines.
- Two order-dependent unit tests asserted detector order, which the sort replaces. Rewritten to assert the new contract. Added: a strong Pattern finding survives the cap, `pValue` is set on Pattern findings and null on Hints, Hints order by relative change, a zero-baseline Hint ranks first, and a tag combo ranks after measured Hints.
- A `!!` in the new `TrendsEngineTest` assertion was flagged by the compiler as unnecessary. Removed.
- `CaseDetailScreen`'s tab state changed from `remember` to `rememberSaveable` (commit 22a7e41). Opening Trends, Tags or Share disposes this screen, and the tab used to reset to Log on return. It now stays on Insights.

**Deferred:**
- Headline copy length and whether sentences quote tag names as `#Name`: moved to PROGRESS.md's voice phrasing audit item, with the flagged labels and their alternatives. The sentences are shared with the share card, so that is a copy change for the audit.
- Silent fallbacks in the Trends rows: PROGRESS.md. `changePointDate ?: LocalDate.now()` lives in `trendFindingSentence`, the share card's path. The Insights rows read the same date null-safely.
- Recency tie-break and "New" badge: PROGRESS.md.

**Considered and declined:**
- `groupRank` and `hintEffectSize` in their own file: private, single-caller, and documented beside the sort they serve.
- `trendCountOfTotal`, `trendTimingBucket` and `trendReferenceLine` as interface defaults rather than per-voice overrides. They follow the existing `trendReliabilityHintLabel` pattern for structural copy, so their English wording is shared across voices.

**Checks:**
- Duplication: the compact card and the full list share `TrendFindingBody`. The reliability chip uses `StatusChip`, which the Gaps card's burst flag now uses too.
- Decoupling: `domain/` has no `android.*` imports and no `System.currentTimeMillis()`, and it takes no ViewModel or UI types.
- Complexity: `TrendFindingBody` is about 60 lines, the largest new composable, under the split threshold. No new `LaunchedEffect`. The one `remember` changed as recorded above.
- Dead code: no unused imports (ktlint passes). The share card still uses the sentence functions, so no Voice keys are orphaned. The HTML prototype lives outside the repo.
- Hygiene: no secrets or local paths in the diff. `git status` shows only intended files.
- Naming and Voice: every new abstract key is overridden in all three voices. The three interface defaults are structural (see Considered and declined).
- Hardcoded values: no new colours or product constants. The sort ranks 0/1/2 are ordering only.
- Accessibility: the chip is text, so the tier is not conveyed by colour alone. No new tap targets.
- UI copy brevity: flagged labels moved to PROGRESS.md with alternatives.
- Spec: HODITH_SPEC.md §10 and §13 match the code. The share card is still sentence-only.
- Data model, widgets and background work: not touched. `widget/` has no trend references.
- Deprecated APIs: not checked against compiler output in this pass.

**Docs updated:** HODITH_SPEC.md §10 (Trends card contents, info icon placement, ordering); TESTING.md (`TrendFiguresTest` added to the trend sentence row); PROGRESS.md (trends item reduced to its open work; copy review bullet added to the voice phrasing audit item); this entry.

---

## feature/manage-tags

**Scope:** PROGRESS.md's "Tags: bulk rename/merge/delete across all events" item: brainstorm, a global tag management screen under Settings, and rename, merge and delete with a warning before each write.

**Walked:** every CLEANUP_CHECKLIST.md section against the branch diff. Decoupling: `TagManagement.kt` has no `android.*` imports; the ViewModel imports only lifecycle types. Duplication: the Plank area container moved from `SettingsScreen.kt` into `ui/common/Plank.kt` so both screens share one theme dispatch. Naming and Voice: every new key is in all three voices. Hygiene: no TODO/FIXME or debug output in the diff, no local paths, no narrative dates. Accessibility: every icon button has a Voice content description, and the search icon is decorative. Hardcoded values: the filter threshold is the named constant `TAG_FILTER_THRESHOLD`. Data model: no schema change, so no migration or backup-version bump. Themes: previews cover Plain light, Intense light, and Bright light and dark. Not verified: the screen has not been seen on a device, and the copy-brevity check against neighbouring labels is by reading only. Tests and spec: see below.

**Found & fixed:**
- `FakeHodithRepository` matched tag names case-sensitively, unlike Room's `COLLATE NOCASE` lookup. The fake now matches Room in both the add path and the new collision lookup.
- A rename whose target name was taken after its warning was shown would have violated the unique index. The confirm step re-checks and drops the write instead.
- `VoiceTest` caught two keys identical across all three voices (rename field label, rename confirm title) and one edit description identical across all three. Reworded per voice.

**Deferred:**
- Instrumented tests (`TagDaoTest` additions, `RoomHodithRepositoryTagManagementTest`, `ManageTagsScreenTest`, the Settings row test) compile but have not run: no emulator here. They need a run in CI or on a device before merge.
- Cross-Case wording in the warnings ("across all Cases, including archived ones") is pending the owner's review.

**Docs updated:** PROGRESS.md (item struck), HODITH_SPEC §14 (Settings Data row and a Manage tags row), TESTING.md (coverage rows), MANUAL_TEST_PLAN.md (Tags section).

---

## chore/ui-test-suite-audit

**Scope:** PROGRESS.md's UI test suite audit item, carried through its fix pass: duplicate UI coverage, pure logic tested through the UI, the manual plan's automatable steps, and why local UI runs were slow.

**Walked:** CLEANUP_CHECKLIST.md against the branch's diff. Checked and fixed: ktlint (`ktlintFormat` removed unused imports and fixed import order), unused private helpers in the trimmed test classes (none left), line endings (CRLF, matching the rest of the repo), TESTING.md rows that described removed tests, and MANUAL_TEST_PLAN.md item numbering.

**Found & fixed:**
- The trend-sentence mapping was `@Composable` with no composition in it. It is now a plain function that takes the locale explicitly. `TrendFindingSentenceTest` covers all 13 kinds.
- The Tag reset on a Case change was duplicated inline in two composable lambdas. It is now the pure `caseFilterChange`, with JVM tests.
- The bulk toggle's "all selected" check compared sizes inline. It is now `isAllSelected`, which uses containment. The two only differ for a stale id, and the resolved selection already excludes those.
- Removed UI tests that re-checked logic covered elsewhere: 2 intensity-formatting, 3 week-dialog row text, 1 stale tag seeding, 1 duplicate reset rule, 5 detail-row toggles and 13 trend-sentence text.
- Added instrumented coverage for manual-plan steps: Single-case cancel (result code), two Single-case instances, a log refreshing a second Single-case widget, day taps in the shared range picker, and a per-skin capture check.
- Manual plan: removed the automated steps, narrowed the partly automated ones, and renumbered.
- PROGRESS.md: removed a duplicated line in the insights-gaps entry, added the CI-overhead and espresso-intents items, and struck this audit item.
- Found during the pass: no test asserts the import-failure snackbar text, although TESTING.md's description of the Settings tests implies it does. The manual step stays.

**Deferred:**
- `espresso-intents` (About 1 and 2, Share 1 hand-off): a dependency decision, tracked in PROGRESS.md.
- CI per-shard overhead: an investigation, tracked in PROGRESS.md.
- Import-failure snackbar text: no test yet; the manual step stays.
- List widget rows: their contents can't be read from a test, so those checks stay manual.
- `storyCapture_isNotBlank_andChangesWithTheSkin`: kept, but it passed once and hit a PixelCopy capture timeout on a later, slower emulator run. Watch it on CI.

**Checks:** `ktlintCheck`, `lintDebug`, `test` (1118 unit tests, no failures) and `assembleDebug` pass. Instrumented, scoped to the changed classes, on the local emulator with animations off: `LogShareTabTest` 18/18, `WidgetActionsFlowTest` 6/6, `SingleCaseWidgetConfigureFlowTest` 3/3, and `ShareCardTemplateTest` 61/61 in one run. Later runs on the same emulator, which was degraded, hit an `ActivityScenario` teardown timeout and the capture timeout above.

**Tests:** new `TrendFindingSentenceTest`; `BigPictureFilterStateTest` (+6); `SingleCaseWidgetConfigureFlowTest` (+2); `WidgetActionsFlowTest` (+1); `LogShareTabTest` (+1); `ShareCardTemplateTest` (+1). The removed tests are listed under Found & fixed.

**Docs updated:** TESTING.md (environment note, coverage rows, deferral), DEV_PLAYBOOK §7 gotcha 10 (animations), MANUAL_TEST_PLAN.md, PROGRESS.md, CLAUDE.md and QA_AUDIT_RULES.md (animation pointers).

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
