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

## fix/info-note-copy

**Scope:** user-reported info-icon copy issues across all three voices — poor scanability, no term emphasis, bad start-time formatting/wrapping, and a broken sentence in Bright's Gaps & streaks note. Reviewed every info-icon note in the app (12 notes + 2 supporting strings), voice by voice, table by table, with the user approving each change before it landed. Scope grew mid-pass to cover the underlying rendering gaps that caused the reported symptoms (no emphasis mechanism, no dialog scrolling, Rhythm's note never adapting to its own "Start times" relabelling), plus a Canadian-spelling pass ("color" → "colour") the user asked for once the per-voice review was done.

**Walked:** CLEANUP_CHECKLIST.md against the diff, section by section. Verified: `ktlintCheck` (after `ktlintFormat`), `lintDebug`, `test` (full unit suite), `assembleDebug`, and `connectedDebugAndroidTest` scoped to `CaseDetailInsightsTabTest` (43/43 passing on a Pixel 8 API 36 emulator, run twice — once before and once after the final Rhythm-layout fix below). Not confirmed: which theme/light-dark-mode combinations the user's on-device check covered beyond Intense and Bright (see Deferred).

**Found & fixed:**
- All 12 info-icon notes rewritten across Plain/Intense/Bright per the user's table-by-table review: trimmed redundant sentences (Logging/Duration bodies), restructured Check-in bodies into on/off pairs, split the device-backup body into two paragraphs, added `**term**` colour-emphasis markers on defined terms, and rewrote the Trends body to accurately distinguish Hint vs. Pattern findings (Hint: a descriptive-threshold detector with no significance test behind it; Pattern: one that only ever surfaces after passing a permutation significance test) instead of implying one upgrades into the other.
- Real bug found during the review: Bright's Gaps & streaks "Current gap" line was missing the word "ended" ("time since your last event." instead of "...event ended."), silently changing its meaning — present only in Bright, not Plain/Intense. Fixed, with a new `VoiceTest` regression test.
- Fixed an em dash in Bright's Settings → Check-ins body (the project's Voice-copy strings stay em-dash-free).
- Added colour-emphasis as a first-class rendering mechanism: `parseEmphasis` (new `EmphasisText.kt`) parses `**term**` markers into `MaterialTheme.colorScheme.primary`-tinted `AnnotatedString` spans — reusing the app's existing colour-only, no-added-weight precedent (`InsightsTab.kt`'s `highlightTag`) rather than introducing bold. Wired into `SectionWithInfo`/`LabelWithInfo`'s default rendering and `InsightsDetailScaffold`'s Tags/Trends full-list info dialogs.
- Added a scroll wrapper inside `InfoDialog` itself, fixing the Gaps & streaks dialog clipping on longer content (confirmed by the user to run past one screen). Side effect: three other dialogs that already scrolled their own content now nested two vertical scrolls, a Compose crash risk — removed the now-redundant inner scroll from `InsightsDrillDownDialog` and Big Picture's day/week detail dialogs (`BigPictureGrid.kt`).
- Added a Start-times-specific info title/intro for the Rhythm card (`insightsRhythmStartsInfoTitle` per voice, plus structural `insightsRhythmInfoIntro`/`insightsRhythmStartsInfoIntro`), so a multi-day Case's relabelled "Start times" section gets a matching explanation instead of always describing the regular Rhythm wording.
- Rebuilt the Rhythm info dialog's body as its own composable (`RhythmInfoContent`) rather than a flattened Voice string, after three on-device rounds surfaced real layout bugs: the time range was first unconstrained in width with wrapping disabled, clipping "PM" at the dialog's edge; then label and range were split into two independently-stacked `Column`s, letting one range's wrap desync it from its own label; then, with label and range reunited on one `Row` and the range on `Modifier.weight(1f)`, Intense's wider font still wrapped the third and fourth rows' ranges unpredictably mid-string. Final design: the range's start and end clock times always stack on their own lines (no en dash joining them on one line), so every line is a single short clock time that fits regardless of theme font or accessibility font scale — eliminating the wrap case entirely rather than fitting it more tightly.
- Colour spelling: Settings → Theme info body ("colors" → "colours") in all three voices, and a `DemoDataSeeder.kt` demo-mode seed note ("Wrong-color cup incident" → "Wrong-colour cup incident"), once the user's Canadian-spelling preference was confirmed to extend to the app's own user-facing copy, not just chat prose.

**Deferred:**
- Explicit confirmation of the Plain theme and of light/dark mode for the Rhythm dialog fix — the user checked Intense and Bright on-device; Plain and the light/dark axis weren't stated.

**Considered and declined:**
- Bulleted lists for the Gaps & streaks and Rhythm info bodies — the user asked for plain line breaks plus colour emphasis on the labelled terms instead.
- A new fixed-width guess for the Rhythm dialog's label column, in favor of reusing `RHYTHM_LABEL_WIDTH` (the card's own label width, already tuned to fit "Afternoon") paired with `Modifier.weight(1f)` on the range — avoids a second magic number while still guaranteeing per-row alignment. (Superseded in effect, not reverted, by the final stacked-lines design above — the label column width is unaffected.)

**Checks:**
- Duplication: no inline user-visible strings added outside `Voice.kt`; `parseEmphasis` reused across `SectionWithInfo` and `InsightsDetailScaffold` rather than duplicated; `RHYTHM_LABEL_WIDTH`/`RHYTHM_GRID_GAP` reused in the new Rhythm info layout rather than re-declared.
- Decoupling: no `domain/`/`android.*` boundary touched; `rhythmInfoTitle`/`rhythmInfoIntro` extracted as plain functions (no Compose) specifically so the Start-times mode-selection logic is unit-testable without a Compose test.
- Dead code: no unused imports (`ktlintCheck` passes); fixed an unused-import regression in `BigPictureGrid.kt` created by removing its now-redundant inner scroll. A KDoc comment narrating "an earlier version" of `RhythmInfoContent` was caught and reworded to state the current invariant instead, during this walkthrough.
- Hygiene: `git status` clean; no secrets or local paths in the diff.
- Naming: new Voice keys (`insightsRhythmStartsInfoTitle`, `insightsRhythmInfoIntro`, `insightsRhythmStartsInfoIntro`) added to all three voices in this same commit; `EmphasisText.kt` follows `AcronymText.kt`'s existing `ui/common` naming pattern.
- Hardcoded values: no new `Color(0xFF...)` literals; theme colour tokens used throughout.
- Accessibility: no tap targets changed; theme/light-dark coverage not fully confirmed, see Deferred.
- UI copy brevity: the subject of the review itself.
- Tests: `EmphasisTextTest` (new, 4 cases), `RhythmInfoTest` (new, 2 cases), a new `VoiceTest` regression test for the Bright "ended" bug, and `CaseDetailInsightsTabTest`'s Rhythm assertions rewritten twice for the evolving dialog shape (each start/end time now its own text node) plus one new test for the Start-times title/intro switch — all 43 tests in that class run and passing on-emulator.

**Docs updated:** TESTING.md (Voice layer row and the Case Detail Insights instrumented row, both extended); this entry.

---

## fix/duration-selector-wrap

**Scope:** user-reported bug: the duration-mode selector's "Start/stop" option (None/Manual/Start-stop) wrapped to two lines in the Plain theme on the New/Edit Case screen. Three follow-ups from the same user, after the wrap fix landed: "Start/stop" rendered visibly smaller than its siblings instead of matching them; separately, Settings' Appearance section showed its Theme row and Time format row at two different sizes from each other; then, in the Bright theme specifically, every `SegmentedChoiceRow` label (Settings and New/Edit Case both) rendered huge and heavy instead of its normal size.

**Walked:** CLEANUP_CHECKLIST.md against the diff, section by section. Verified: `ktlintCheck`, `lintDebug`, `test` (full unit suite), `assembleDebug`, and `connectedDebugAndroidTest` scoped to `SegmentedChoiceRowTest` (5/5 passing on a Pixel 8 API 36 emulator) and `SettingsScreenTest` (34/34 passing, confirming the Appearance section's click/selection behavior survived being wrapped in a `CompositionLocalProvider`).

**Found & fixed:**
- Root cause: `SegmentedChoiceRow`'s Plain/Intense `SegmentedButton` branch gave its label `Text` no wrap/shrink handling; Plain's Inter font is wider per character than Intense's condensed Oswald, so the same 3-way None/Manual/Start-stop split only overflowed in Plain. Fixed by adding `maxLines = 1` and `TextAutoSize.StepBased()`, which only shrinks the font when a label would otherwise overflow — every other current usage (shorter labels) renders unchanged. Applied the same to Bright's own track (`BrightSegmentedChoiceRow`) for consistency, even though it wasn't wrapping at the width tested. Confirmed red (56.4dp tall vs. 40dp for "None") against the pre-fix code before confirming green, on-device.
- Added a Plain light/dark Compose preview pair, reusing the existing preview-content composable (renamed from Bright-specific `SegmentedChoiceRowBrightPreviewContent` to `SegmentedChoiceRowPreviewContent` since it was already decoration-agnostic) rather than duplicating it.
- Added `longLabelOption_doesNotWrapInPlainTheme`/`...InBrightTheme` to `SegmentedChoiceRowTest`, comparing the long label's rendered height against a short sibling's in the same row/width/theme instead of hardcoding font metrics.
- Follow-up 1: each segment's independent autoSize meant a short label stayed at its own larger natural size while "Start/stop" shrank, reading as inconsistent even once nothing wrapped. Added `SegmentedRowFontSizeCoordinator` to `SegmentedChoiceRow.kt` — each segment reports its own autoSize result via `onTextLayout`, and once every segment has reported, all of them switch from independent autoSize to the shared smallest size. Applied to both the Plain/Intense and Bright branches.
- Follow-up 2: by default each row gets its own private coordinator instance, so a 3-option row (Theme) and a 2-option row (Time format) stacked in the same Plank can still land on two different sizes from each other, since each divides the same full width by a different segment count. Generalized the coordinator to support several rows sharing one instance -- a row joins via `registerRow`/`unregisterRow` (so a row that disappears, e.g. a dismissed dialog, can't block the rest from ever settling) -- exposed via a new `LocalSegmentedRowFontSizeCoordinator` composition local. `SettingsScreen.kt`'s Appearance section now provides one shared instance around its Theme and Time format rows; every other caller is unaffected (each still gets its own private instance, same as before).
- Follow-up 3: `TextAutoSize.StepBased()`'s default search range is 12sp–112sp, with no upper bound tied to the control's own intended size. Likely cause: if `BrightSegmentedChoiceRow`'s `Box`/`Row` layout ever hands the autoSize search an effectively unbounded width during measurement, every candidate size up to 112sp reads as "fits", so the search climbs toward that ceiling instead of shrinking -- producing the huge, heavy-looking text reported in both Settings and New/Edit Case's Bright theme. Capped `maxFontSize` at the row's own `textStyle.fontSize` in both the Plain/Intense and Bright branches, so autoSize can only ever shrink from the intended size, never grow past it, regardless of whether that unbounded-width theory is the full story. Not yet confirmed fixed on-device.

**Deferred:**
- Automated verification of both font-size-coordination fixes (within-row and cross-row). Instrumented-test attempts (bounding-box height, and a `GetTextLayoutResult`-semantics font-size probe) both showed rendered text height staying completely unchanged across every tested width, 300dp down to an unreasonable 20dp-per-segment — not even "None" shrank at the extreme end, which real `autoSize` behavior can't produce. That means `autoSize` is not taking visible effect in this instrumented-test harness, for reasons not pinned down in this pass, even though the user directly observed it shrinking "Start/stop" in the real running app. Given the harness and the real app disagree, both coordinators' correctness rests on source-level reasoning (Compose Foundation's `MultiParagraphLayoutCache` confirms `onTextLayout` receives the real post-autoSize style) rather than an automated check; the user will confirm visually on-device instead. Worth a closer look later if this project ever needs to assert on `autoSize` behavior from an instrumented test again.
- Extending the shared Appearance-section coordinator to other screens with multiple stacked rows (e.g. Case Edit's logFlow + durationMode rows). Not requested for this pass; flagged here so it's not forgotten if the same "rows look mismatched" complaint resurfaces elsewhere.

**Considered and declined:**
- Shortening the "Start/stop" label itself (flagged by UI Copy Brevity: "None" = 4 chars, "Manual" = 6, "Start/stop" = 10). The layout fix already resolves the wrap; the user chose to keep the label, since changing it would mean editing all three voices plus every doc that names durationMode's options for a label that's accurate and already consistent.
- A fully automated regression test for either font-size-coordination fix, in favor of the user's own on-device check — see Deferred.

**Checks:**
- Duplication: the preview-content composable is now shared across Bright and Plain previews instead of being duplicated per theme. `SegmentedRowFontSizeCoordinator` is one class shared by both branches and, when a screen opts in, by every row under its `CompositionLocalProvider`, rather than duplicated per branch or per row.
- Decoupling, data model, background work: not touched.
- Dead code: no unused imports; a draft cross-reference naming a specific test-method name was caught and reworded so it can't go stale if the test is renamed. The diagnostic test written to investigate the autoSize harness question was removed once its purpose was served, not left behind.
- Hygiene: `git status` clean; no secrets or local paths in the diff.
- Naming: new previews/tests/the coordinator class and composition local follow existing naming patterns.
- Accessibility: autoSize only affects the label's font size inside its existing container — tap targets unchanged.
- UI copy brevity: flagged and resolved, see Considered and declined.
- Tests: wrap regression test added and confirmed red/pre-fix then green/post-fix on-device. `SettingsScreenTest`'s full 34-test suite re-run to confirm the Appearance section's behavior survived the `CompositionLocalProvider` wrapping. Neither font-size-coordination property itself is covered by an automated test — see Deferred.

**Docs updated:** TESTING.md (shared-components row extended to cover the wrap regression); this entry.

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

