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

## chore/voice-phrasing-audit

**Scope:** PROGRESS.md's "Review phrasing across all three Voice implementations" item, run as a joint AI/human review rather than the item's originally planned rubric-first process: the user asked for a simpler loop instead — a table of every key's Plain/Intense/Bright copy per screen, reviewed and edited live, applied immediately, one commit per screen — and explicitly dropped the rubric document, the findings-then-fixes commit split, and new `VoiceTest` mechanical invariants (vocabulary casing, gamification language, length caps) from scope. Every screen in the app was covered, including two gaps the original item's screen list missed (`WatchesTab.kt`, `WatchEditorSheet.kt`).

**Walked:** CLEANUP_CHECKLIST.md against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite) and `assembleDebug` all pass. Not run: `connectedDebugAndroidTest` (no device this pass) — every changed `androidTest` file was grepped for literal-string assertions that a wording change could silently break; none remained except the one fixed below.

**Found & fixed:**
- Every em dash in a `Voice.kt` string literal, across all three voices, replaced with a period, comma or colon depending on the sentence — the project's own documented policy, previously unenforced at this scale.
- Intense's "seal"/"unseal"/"bury"/"exhume" metaphor family retired wherever it had drifted past being readable as its real action (Stop/Resume/Retire/Restore), and "mark" retired entirely — it had accreted four unrelated meanings across the file (icon, tag, selection state, and the Share card's event noun) before this pass.
- Two cases of a word colliding with its own established in-app meaning: Settings' Data section called itself "The archive" (the real Archived Cases feature already owns that word) and the Check-in section/info titles called themselves "the watch kept" (the separate Watch/notifications feature already owns "watch"). Both retitled to name the actual feature plainly.
- `historyFieldsDialogTitle` and `logSheetTagsLabel` converted from abstract per-voice overrides to shared interface defaults once the three voices converged on identical wording by request, keeping `VoiceTest`'s "no key identical across all three voices" invariant green rather than fighting it with three now-redundant overrides.
- The About screen's Licenses section removed entirely — Voice keys, the UI section in `AboutScreen.kt`, and the androidTest assertion that covered it. A product call, not a phrasing fix: Apache 2.0 attribution for the listed dependencies is already satisfied by this public repo's own `LICENSE`/dependency manifest, so an in-app screen was a courtesy, not a requirement.
- Several multi-sentence info-dialog bodies (Insights' Gaps/Trends explainers; Settings' Theme/Check-in/Cloud-backup explainers) shortened and reworded to plain, positively framed sentences at the user's request, rather than one-off dash removal.
- `VoiceTest`'s `Square share copy states the voice's own event noun` test hardcoded the literal Intense "mark"/"marks" strings it exists to verify generically — found and fixed alongside the wording change that would otherwise have broken it for the wrong reason.

**Deferred:**
- `connectedDebugAndroidTest` — no device this pass; see Walked above for the mitigation taken instead.
- CLAUDE.md's voice names ("Serious, Goth, Quirky") are stale — the actual names are Plain/Intense/Bright, confirmed throughout this pass and already correct in HODITH_SPEC.md and PROGRESS.md. Left as is: a CLAUDE.md correction is a separate, smaller fix the user hasn't asked for yet.

**Considered and declined:**
- The item's own acceptance criteria (written rubric, findings-list-then-fix-commit split, new `VoiceTest` mechanical invariants for casing/gamification/length). The user asked for the simpler loop described in Scope instead; this is a deliberate process substitution, not an unmet criterion.

**Checks:**
- Naming and Voice: every string touched keeps three distinct voices; the two keys converted to shared defaults are now genuinely structural, not a workaround.
- Dead code: the Licenses section's Voice keys, UI code and test assertion all removed together, not just the strings.
- Hygiene: no secrets or local paths in the diff; each screen's changes landed as its own commit on `chore/voice-phrasing-audit`.
- Spec: HODITH_SPEC.md's About row description and TESTING.md's About coverage row updated to drop the removed Licenses section.

**Docs updated:** HODITH_SPEC.md (About row, Licenses mention dropped), TESTING.md (About coverage row), PROGRESS.md (this item struck); this entry.

---

## fix/viewmodel-db-teardown-race

**Scope:** CI run 37693819661 failed with `IllegalStateException: connection pool has been closed`, cascading to fail the next test in the same instrumentation process. Checked against the local (non-committed) FLAKY_TESTS.md flake tracker first — new signature, not a known flake — then found the root cause and fixed it.

**Walked:** CLEANUP_CHECKLIST.md's Tests section against the diff; this pass is itself the origin of one of its new checks.

**Found & fixed:**
- `ManageTagsViewModelDatabaseTest` and `SharePreviewOrderFlowTest` each construct their ViewModel directly (not via Hilt/`ViewModelProvider`), against a real Room db from `createInMemoryDatabase()`. Both ViewModels build `uiState` with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(...), ...)` over a Room-backed Flow, but nothing ever cancelled `viewModelScope` before `tearDown()` closed the db — a live Room collector racing `db.close()` on every run of either test, not pure environment flake. Fixed by registering each ViewModel in a `ViewModelStore` and clearing the store before `db.close()`. A grep of `app/src/androidTest` for direct `*ViewModel(...)` construction confirmed these were the only two affected call sites.

**Deferred:** nothing.

**Checks:** `ktlintCheck` (after `ktlintFormat`), `lintDebug`, `test` (full unit suite, none failing), `compileDebugAndroidTestKotlin`. Not run: `connectedDebugAndroidTest` — no device/emulator in this environment; needs a scoped run on `ManageTagsViewModelDatabaseTest` and `SharePreviewOrderFlowTest` before merge.

**Docs updated:** CLEANUP_CHECKLIST.md (new Tests-section check: a directly-constructed ViewModel against a real Room db must have its `ViewModelStore` cleared before `db.close()` in `tearDown()`).

---

## refactor/share-insights-history-dedupe

**Scope:** PROGRESS.md's "Review share card, Insights and History for duplicated implementation" item, worked through on its own branch rather than spun into follow-up items: the duplication findings below, the small merges and the dead-code drop they called for, and the Log→History screen-naming cleanup the review surfaced.

**Walked:** CLEANUP_CHECKLIST.md against the diff, section by section. Verified: `ktlintCheck` (after `ktlintFormat`), `lintDebug`, `test` (full unit suite, none failing), and `assembleDebug`. Not run: `connectedDebugAndroidTest` — no device attached this pass; the changed `androidTest` sources compile (`compileDebugAndroidTestKotlin`).

**Found & fixed:**
- Duplication review: formatting, figures and empty states are already shared across the share card, Insights and History (`EventTimeFormat.kt`, `CompactFormat.kt`, `OngoingEvent.kt`, `StatCardRows.kt`, `ui/common/StatColumns.kt`, `ui/common/HeatmapShading.kt`); the share card computes no stats of its own. Four small duplicates found and merged: the share card's private `timeOfDayLabel` (byte-for-byte `rhythmTimeOfDayLabel`), `MiniStatRow` (now reuses `StatRow` with an added `style` param), the per-square intensity `Box` (now the shared `IntensitySquare`), and `formatIntensity`/`HeroRate.rateText`, which lived in UI files that an unrelated surface was importing from (relocated to `viewmodel/CompactFormat.kt` and `viewmodel/ShareCardState.kt`).
- `MiniInsightsCard` and `MiniRhythmSection`'s grid loop are kept separate, not merged: the mini chrome deliberately skips Bright's `GlowCard` (a captured share image can't risk elevation-shading differing by skin), and the grid differs in cell size, tap targets and the info icon from the real `RhythmCard`. Recorded as reviewed-and-kept, not an oversight.
- `trendFindingSentence` (`InsightsTab.kt`) had zero production callers once both surfaces moved to `TrendFindingBody`; removed, along with `TrendFindingSentenceTest.kt`. Its removal orphaned 13 Voice sentence keys (`insightsWentQuietSentence`, `insightsGapShiftSentence`, `insightsTagOutcomeSentence` and ten more) across the interface and all three voices — removed with it, keeping the 13 matching `*EvidenceLabel` keys `TrendFindingBody` still reads.
- The review surfaced a naming split the spec already draws but the code didn't: "History" names the screen, "Log" names the act of recording (spec §6's "Logging flows" vs. its "History tab"). Renamed the screen-facing identifiers to History throughout — `LogRowField`/`LogSortOrder`/`LogEventsPage` → `History*`, `HodithRepository.observeLogEventsForCase` → `observeHistoryEventsForCase`, `CaseDetailViewModel`'s History-tab paging state, `CaseDetailScreen`'s `HistoryTabContent`/`HistoryFilterRow`, `LogShareTab.kt`/`LogShareViewModel.kt` → `HistoryShareTab.kt`/`HistoryShareViewModel.kt`, and the matching Voice keys (`caseDetailHistoryTabLabel`, `historySummaryLine`, `shareHistoryRangeLabel` and others). Left untouched: `LogDetailSheet`/`LogDetailScreen`/`LogDetailViewModel`, `LogDraft`, `LogFlow`, and the "Log an event"/"retro-log" Voice copy — all genuinely about the act of logging, not the screen.
- The DataStore preference key *string literals* (`"log_sort_order"`, `"log_date_from"`, `"log_date_to"`, `"log_visible_fields"`) were kept exactly as they were, even though the Kotlin constant names renamed, so existing users' History sort/range/field-visibility preferences survive the upgrade. Only the `testTag` string values (`history_field_toggle_`, `history_share_field_toggle_`) changed, since those aren't persisted.

**Deferred:**
- `connectedDebugAndroidTest` — no device this pass. The androidTest sources compile; run the suite (or at least `HistoryShareTabTest`, `CaseDetailScreenTest`, `ShareScreenTest`, `ShareCardTemplateTest`, `CaseDetailInsightsTabTest`, `InsightShareTabTest`, `SharePreviewOrderFlowTest`, `RoomHodithRepositoryHistoryEventsTest`) before merge.

**Considered and declined:**
- Spinning the merges and the rename out as separate follow-up PROGRESS.md items, as the review item's own acceptance criteria suggested. The user asked for them fixed as part of this same item instead, on one branch.
- Renaming `domain/LogFilter.kt`'s filename. No exported symbol is actually named `LogFilter` (only `ChronologicalOrder`, `filterAndSortEvents` and the entry-cap constant), so there was nothing to disambiguate.
- Folding the logging-action names (`LogDetailSheet`, "retro-log") into History too. The spec's own verb/noun split — Log is the act, History is the screen — is worth keeping, not flattening into one word.

**Checks:**
- Duplication: the four small merges above; `MiniInsightsCard`/`MiniRhythmSection` kept separate with a stated reason.
- Decoupling: the relocated `HeroRate` text helpers (`figureText`/`rateText`/`unitText`) moved to `viewmodel/ShareCardState.kt`, not `domain/HeroRate.kt` — `domain/` still takes no `Voice` import.
- Dead code: `trendFindingSentence` and its 13 orphaned Voice keys removed; no unused imports (ktlint passes).
- Naming and Voice: every renamed Voice key kept its three per-voice strings unchanged — these are identifier renames, not new copy, so no new voice authoring was needed.
- Hygiene: no secrets or local paths in the diff. Persisted DataStore key strings unchanged (see Found & fixed).

**Docs updated:** PROGRESS.md (the duplication-review item struck, resolved rather than spun into follow-ups); this entry.

---

## Trends recency tie-break, fallbacks and share card layout

**Scope:** the PROGRESS.md Trends item (the "New" badge bullet was dropped): a recency tie-break for equal-ranked findings, the silent fallbacks in the Trends rows, and the share card's Trends section moved onto the Insights card's row layout.

**Walked:** CLEANUP_CHECKLIST.md against the diff, section by section. Verified: `ktlintCheck` (after `ktlintFormat`), `lintDebug`, `test` (1188 unit tests, none failing), `assembleDebug`, and `connectedDebugAndroidTest` scoped to `ShareCardTemplateTest`, `InsightsTabTrendsCardTest`, `TrendsListScreenTest` and `CaseDetailInsightsTabTest` (118 tests, all passing on the API 36 emulator). Not run: the full instrumented suite, and light and dark mode on a device.

**Found & fixed:**
- `TrendFinding` now carries a required `latestEvidenceAt`, so no detector can leave it out. Each detector reports the newest event its finding was built from. The Case-history detectors (gap, streak, frequency, recurrence, went quiet, change point) report the Case's latest activity, from `GapStats.lastActivityAt`.
- Tie-break: findings still tied after group, p-value and effect size order newest first. Covered by `TrendsEngineTest`, including a check that the cap keeps the newer of tied findings.
- Silent fallbacks in `InsightsTab.kt` now fail loudly: a missing `changePointDate`, `outcome` or time-of-day bucket throws with the kind named, instead of substituting today's date, intensity or morning. Covered by `TrendFindingSentenceTest`.
- Share card Trends section: each row is the shared `TrendFindingBody` (headline, reliability chip, figures, evidence line) with the same divider as the Insights card. `ShareCardTemplateTest` asserts the headline, the chip count (one per row) and the evidence line.
- The `TAG_TIMING` bucket phrase now names only the bucket that is set, instead of always defaulting time of day to morning. Covered by `TrendFindingSentenceTest`.
- Stale KDoc on `trendFindingSentence` and the evidence-line helper, which still said the share card showed sentences only.

**Deferred:**
- `trendFindingSentence` is now read only by its own tests, since both cards render headlines. Whether it is removed, and whether its sentence wording duplicates the headlines, is for the new PROGRESS.md duplication review item. Removing it now would orphan Voice sentence keys.
- `tagName.orEmpty()` in the same rows is the same kind of null-to-default fallback. It was not in the PROGRESS.md bullet, so it is left as is. Logged here so it is not forgotten.

**Considered and declined:**
- A default value on `latestEvidenceAt`. The field is required so each detector states its own date; the compiler finds any construction site that does not. Test and preview fixtures pass `0L` or a fixed constant.

**Checks:**
- Duplication: the share card and the Insights tab now render trend rows through one composable. `trendFindingSentence` is the remaining duplicate, deferred above.
- Decoupling: `domain/` gains no `android.*` imports. `latestActivityAt()` lives in `data/EventEntity.kt`, next to `loggedZone()`, and the domain already imports from `data`.
- Voice: no new user-visible strings. The share card reuses the Insights headlines, which all three voices already define, so `VoiceTest` covers them.
- Dead code: no unused imports (ktlint passes). `trendFindingSentence` is the one exception, deferred above.
- Hygiene: no secrets or local paths in the diff. Line endings stayed CRLF throughout.

**Docs updated:** HODITH_SPEC.md §10 (tie-break order, and the share card's Trends rows now match the Insights card); PROGRESS.md (the Trends item is struck; a duplication review item is added to Standalone); this entry.

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

