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

## feature/pdf-export — remove Insights PDF

**Scope:** Dropped PDF export for the Insights tab entirely, keeping it as a History-only feature. The Insights PDF's content builder reduced most panels (Gaps, Streaks, Duration, Intensity, Tags) to plain "Label: value" text lines and dropped Rhythm and Trends outright (no text form exists for a heatmap grid or a narrative finding); its one real graphic, a separately-captured Frequency chart, is `null` whenever a Case tracks multi-day/duration events or has too few events — leaving the PDF text-only in exactly the case the user hit. Closing that gap would mean either capturing the whole share card as one image (forces Square format on the page, loses real/searchable text) or extending the existing per-chart capture to Rhythm/Intensity too (requires generalizing `PdfLayout.kt`'s pagination math from one fixed chart block to a variable number of variable-position image blocks, each with its own eligibility gating) — real feature work for unclear payoff on a document most users will rarely compare against the image card. History's PDF (a real bordered table) was unaffected and stays as-is.

**Walked:** CLEANUP_CHECKLIST.md section by section against `git diff` (16 changed files, 4 deleted, 0 new). `ktlintCheck`, `lintDebug`, `test` (full unit suite, `PdfLayoutTest` gone with the constants it tested), `compileDebugAndroidTestKotlin`, and `assembleDebug` all pass. `connectedDebugAndroidTest` deferred — no emulator/device was available in this session; the touched instrumented suites (`ShareScreenTest`, `InsightShareTabTest`, `SharePreviewOrderFlowTest`, `HistoryShareTabTest`, `CasePdfExporterTest`) need a run on the user's own device before merge, per this repo's standing instrumented-test convention.

**Found & fixed:**
- Initial exploration missed one call site: `HistoryShareTabTest.kt` also called `ShareScreen(...)` with the now-removed `chartGraphicsLayer`/`onInsightSharePdfClick` params. Caught by a post-edit grep sweep for every deleted symbol across `app/src`, not by the original file-by-file plan — a reminder that a map built from one exploration pass needs re-verifying against the real diff, not trusted as exhaustive.
- `ShareScreen.kt` picked up two unused imports (`pdfAppLinkText`, `pdfDateRangeBounds`) once `buildInsightsPdfContent()` was deleted — caught by `ktlintCheck`, not by inspection.
- `ShareScreenTest.kt`'s `summaryTab_hasNoPdfToggle` test referenced the now-deleted `INSIGHT_SHARE_PDF_TOGGLE_TAG` constant; renamed to `summaryAndInsightsTabs_haveNoPdfToggle` and rewritten to assert History's toggle tag is absent from both tabs instead, since there's no Insights-specific tag left to check.
- Confirmed `CasePdfExporterTest`'s five remaining History tests (few rows, zero rows, many rows/pagination, long-note wrapping, missing optional columns) already cover `drawHistoryPage`/table pagination fully on their own — no coverage gap opened by removing the two Insights tests.
- Confirmed `FakePdfExporter` stays meaningfully exercised after losing its `exportInsightsPdf` override: `HistoryShareViewModelTest` still constructs `HistoryShareViewModel` with it.

**Deferred:**
- `connectedDebugAndroidTest` — see Walked above; needs the user's device.

**Considered and declined:** nothing new; this pass only removes code the prior two `feature/pdf-export` passes added.

**Checks:**
- Duplication: none introduced; pure removal.
- Decoupling: untouched — `PdfLayout.kt` stays `android.*`-free, `CasePdfExporter` still takes no `viewmodel`/`ui` imports.
- Complexity: `CasePdfExporter.kt` shrank from ~300 to ~230 lines by dropping `exportInsightsPdf`/`drawInsightsPage`/`drawChart`; no new functions added.
- Dead code & hygiene: every import touched by a deletion was swept for staleness (`ktlintCheck` catching the two misses above); `git status` clean throughout, no secrets or local paths.
- Naming: no new files; `FakePdfExporter`/`CasePdfExporter`/`PdfExporter` keep their names with one fewer method each.
- Hardcoded values, accessibility, data model/migrations, background work/widgets/notifications, deprecated APIs: untouched — N/A.
- Spec review: `HODITH_SPEC.md` §13's "PDF record" bullet rewritten History-only; the Frequency sentence that referenced the Insights PDF rewritten to state it never appears on any Insights share output; §14's Insights Share-preview row dropped its "Share as PDF" toggle mention.
- Tests: covered above (coverage review, the renamed `ShareScreenTest` case); `TESTING.md` and `MANUAL_TEST_PLAN.md` brought up to date (see Docs updated).

**Docs updated:** HODITH_SPEC.md (§13 PDF record bullet + Frequency sentence, §14 Insights Share-preview row); TESTING.md (dropped the `PdfLayoutTest` JVM row, reworded the ShareScreenTest/Insights-tab instrumented rows and the `CasePdfExporterTest` row); MANUAL_TEST_PLAN.md (PDF records section narrowed to History only); PROGRESS.md (the `ACTION_SEND` coverage line narrowed to the History PDF export); `Voice.kt`'s `shareAsPdfActionLabel` doc comment reworded to describe a History-only toggle (the string itself is unchanged); this entry (oldest entry, `feature/case-edit-logging-toggle`, removed per the 5-entry retention rule).

---

## feature/pdf-export — toggle/table redesign pass

**Scope:** A follow-on redesign pass on top of the already-committed `feature/pdf-export` base, driven by hands-on user feedback: the two-button image/PDF split replaced with a single "Share as PDF" toggle per tab; the History table rebuilt as a real bordered, multi-column grid (Start/End/Duration/Intensity/Tags/Notes) with genuine text wrapping instead of a joined detail string; the document header redesigned (masthead title, a visually separated and larger date-range section, a record count, explicit "generated on" wording); Start/End cells reformatted to two lines (date, then weekday + time honoring the 12h/24h setting) with the year always shown; and the PDF filename brought onto the same timestamp convention as the JSON/CSV exports.

**Walked:** CLEANUP_CHECKLIST.md section by section against `git diff HEAD` (14 changed files, 3 new). `ktlintCheck`, `lintDebug`, `test` (full unit suite, including two new test files), and `assembleDebug` all pass. Instrumented: the `ui.share` package (117/117) and `CasePdfExporterTest` (7/7, confirmed on two separate devices) were run earlier in this pass; the final round of edits (removing notes truncation, removing the "History" kicker, narrowing the Start/End columns, recalibrating the header gap) were verified visually by the user directly on their own device rather than re-run by me, per their explicit request to hold off on further automated instrumented batches while they tested.

**Found & fixed:**
- **Zero direct unit coverage** for `historyPdfContent`'s column-gating, row-building and ongoing-event logic — it was only ever exercised indirectly through hand-built content objects in `CasePdfExporterTest`, never through the actual builder function. Added `HistoryPdfContentBuilderTest` (column gating for each optional column; ongoing vs. zero-length-point vs. finished-duration event handling; tag/note formatting; record count reflecting the filtered match count). Writing it caught a real bug in the test itself on the first pass, not the implementation: a `null` `endedAt` reads as *ongoing* on a tracking Case, not "no duration" — the existing `finishedPoint` fixture exists precisely to disambiguate that, and the first draft used a plain `testEvent` instead.
- `OffscreenCaptureTest` was missing the `@Smoke` tag every other `@UiTest` class in `ui/share` carries on its one representative happy-path test — added.
- The header block's fixed height (`PDF_HEADER_BLOCK_HEIGHT_PT`) had been sized for "kicker line and record-count line both present" — a combination that never actually occurs (Insights has a kicker and no count, History has a count and no kicker) — leaving a visible ~40pt gap before the table on every real document. Recalculated against the actual drawn content (~97-99pt either way) and dropped 140→115pt.
- The `PDF_TABLE_CELL_MAX_LINES`/ellipsize truncation on table cells directly contradicted the user's ask ("longer notes are truncated which should not happen"); removed in favor of unbounded `StaticLayout` wrapping, with row heights already real-measured per row rather than assumed, so this didn't require touching the pagination math.

**Deferred:** nothing.

**Considered and declined:**
- The two-line `pdfDateRangeBounds(...)` + `voice.shareHistoryRangeNote(...)` pattern appears identically in `ShareScreen.kt`'s inline Insights-PDF builder and in `HistoryPdfContentBuilder.kt`. Declined to extract a shared helper for it — two call sites, two lines each, sitting in functions whose surrounding context genuinely differs (one always spans the Case's full history, the other honors a user-picked range); a new function for this would add more indirection than it saves.

**Checks:**
- Duplication: the one instance above, considered and declined. No copy-pasted composables — the Insights/History PDF toggles are each one line wrapping the existing `ToggleRow`, not worth a shared wrapper for two call sites. Every new Voice string reused an existing one where the meaning already matched (`sharePdfDateRangeLabel`, `sharePdfRecordCount` reusing `shareSquareEventNoun`, `sharePdfGeneratedLine`); Start/End column headers are the only genuinely new strings this pass needed.
- Decoupling: no `android.*` imports in `domain/PdfLayout.kt` or the new `domain/ExportFileTimestamp.kt`; `CasePdfExporter.kt`'s one `System.currentTimeMillis()` call matches the existing `ComposeShareImageExporter` precedent (filename uniqueness, not business logic); the exporter still imports nothing from `viewmodel`/`ui`.
- Complexity: `CasePdfExporter.kt` is ~300 lines but stays a collection of small, single-purpose drawing/layout functions; its largest function (`drawDocumentHeader`) is about 20 lines.
- Hygiene: `git status` clean throughout; no secrets, local paths, or leftover TODO/FIXME; no stray prototype artifacts (the earlier feature work's two capture spikes were already cleared in the prior commit).
- Naming: new files (`domain/ExportFileTimestamp.kt`, `viewmodel/PdfAppLink.kt`, `viewmodel/HistoryPdfContentBuilderTest.kt`) follow convention and sit in the right package. `sharePdfDocumentTitle` converted from three per-voice overrides to one shared default (the user wanted the literal app tagline, not voice-flavored copy); `shareAsImageActionLabel` removed cleanly (no longer needed once the toggle replaced the two-button split) with no dangling references anywhere.
- Hardcoded values: PDF drawing constants (text sizes, gaps, colors) stay as named private consts local to the exporter since nothing else needs them; the layout-math constants pagination depends on stay in `domain/PdfLayout.kt`, narrowed/recalibrated rather than left as guesses.
- Accessibility: no new icon-only buttons; `ToggleRow` is an existing, already-accessible shared component reused as-is.
- Data model, migrations, widgets, notifications: untouched — N/A.
- Deprecated APIs: `StaticLayout.Builder.obtain(...)` is the current, non-deprecated API; no new compiler warnings introduced this pass.
- Spec review: `HODITH_SPEC.md` §13 updated with a new "PDF record" bullet (the toggle, the document's structure) and a one-line fix to the pre-existing "Frequency never appears on a share card" sentence, which the Insights PDF now contradicts for that path specifically; §14's two Share-preview table rows cross-reference it.
- Tests: covered above — new JVM coverage for the content builder, the missing `@Smoke` tag, and `TESTING.md`/`MANUAL_TEST_PLAN.md` both brought up to date (see Docs updated).

**Docs updated:** HODITH_SPEC.md (§13 new bullet + one sentence fix, §14 two table rows); TESTING.md (new JVM row for `PdfLayoutTest`/`HistoryPdfContentBuilderTest`, three reworded Share instrumented rows, one new `CasePdfExporterTest` row); MANUAL_TEST_PLAN.md (new "PDF records" section); this entry (oldest entry, `feature/duration-unification — pre-merge cleanup pass`, removed per the 5-entry retention rule).

---

## fix/bright-intensity-label

**Scope:** User-reported Bright voice copy issue: `eventIntensityLabel` read "Feels like a 3!" with no word "intensity" anywhere, so the bare number in the dot-separated event summary row didn't clearly map to an intensity rating (unlike Plain's "Intensity 3" and Intense's "Intensity: 3"). Changed to "Intensity: 3!" — keeps Bright's exclamation-mark style, matches the other two voices' explicit wording.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (a single-line string change). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. No instrumented run — no new/changed test behavior; every existing test calls `BrightVoice.eventIntensityLabel(n)` dynamically rather than asserting the literal string, so none needed touching.

**Found & fixed:**
- `Voice.kt`'s `BrightVoice.eventIntensityLabel` changed from `"Feels like a $intensity!"` to `"Intensity: $intensity!"`.

**Deferred:** nothing.

**Checks:**
- Duplication: this isn't a new inline string — it's the existing `Voice` layer's `eventIntensityLabel`, already called from `CaseDetailViewModel`'s shared `eventDetailSummary` helper.
- Naming: not a new `Voice` key, so the "all three voices in the same commit" rule doesn't apply; Plain/Intense were reviewed and already say "Intensity" explicitly, so neither needed a change.
- UI Copy Brevity: "Intensity: 3!" is the same length class as Plain/Intense's own intensity labels and no longer than its neighbouring summary fragments (duration, tags).
- Hygiene: `git status` clean throughout; no secrets or local paths in the diff.
- Spec review: HODITH_SPEC.md doesn't quote this string — nothing to update.
- Tests: confirmed no test hardcoded the old "Feels like" string; full unit suite green against the new copy.

**Docs updated:** this entry (oldest entry, `feature/duration-unification`, removed per the 5-entry retention rule).

---

## fix/big-picture-selector-chip-contrast

**Scope:** User-reported Big Picture Case/Tag filter popup inconsistencies across voices: Case and Tag chips used different selected/unselected conventions, Intense's selected Case chip read weaker (greyer) than unselected, and Bright's Tag chip selected/unselected contrast was nearly invisible. Two follow-on issues surfaced during manual verification: Intense's bulk-action/dismiss button row wrapped, and the read-only legend row could grow unboundedly with a large partial selection, crowding out the calendar grid.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to `BigPictureScreenTest` on a connected emulator — 51/51 green, including `caseChipAndTagChip_haveEqualHeight_inSharedFlowRow`, which guards Case/Tag chip height parity and is touched by `TagFilterChip`'s new bold selected text.

**Found & fixed:**
- `CaseFilterChip`'s combined `PLAIN, INTENSE` branch split in two: Intense's selected Case fill moved from `secondaryContainer`/`onSecondaryContainer` (a grey lighter than the unselected `surface` in light mode) to `secondary`/`onSecondary`, so selected reads heavier than unselected instead of weaker; Plain unchanged. `CaseGroupChip` ("All Cases" aggregate legend label) got the identical fix, since it shares the same bug and sits beside the now-fixed per-case chips in the legend row.
- `TagFilterChip`'s selected border moved from `outline` to `onSurface`, and selected text gained `FontWeight.Bold` — on Bright, `outline`/`outlineVariant` are nearly indistinguishable, so the old border-width-only signal was almost invisible. Tags stay tint-free (no fill/halo), preserving the documented one-accent (Intense) / two-colour (Bright) budget the original design called for.
- Intense's bulk-action labels shortened (`bigPictureSelectAllAction`/`bigPictureClearAllAction`: "Select every one"/"Clear every selection" → "Select all"/"Clear all") and `bigPictureFilterCountAll` ("Every one" → "All") — the originals overflowed the info dialog's confirm-button row alongside the dismiss button, and the Year trigger chip once every dimension showed Intense's longer wording.
- `FilterLegendRow`'s itemized Case/Tag list had no size cap, so a large partial selection could grow past the available screen space. First tried a character-budget "+N" remainder chip (`bigPictureLegendOverflow`); dropped after the user's manual testing showed it read as a disabled button, gave inconsistent line counts per theme (chip chrome width varies by theme), and placed the "+N" ambiguously once one group (e.g. Tags) had already collapsed to a single label. Replaced with a binary `bigPictureLegendFits` check: list every selected item, or (over a generous ~3-line character budget) show nothing at all — the same "trigger chips above still state the count" fallback the existing all-selected case already uses.

**Deferred:** nothing.

**Considered and declined:**
- A per-item "+N" truncation of the legend row via Compose's built-in `FlowRowOverflow.expandIndicator` line-capping mechanism — declined twice: it's `@Deprecated("FlowLayout overflow is no longer maintained")` in this project's Compose version, and independent of that, its indicator is wired to expand the row on tap rather than stay static, which wasn't wanted here.

**Checks:**
- Duplication: `CaseFilterChipRow` extracted once Intense needed different token roles than Plain, so the two branches share one row layout instead of duplicating it; no new inline Voice strings (the dropped "+N" indicator would have needed none either, being numeral-only like `bigPictureFilterCount`'s existing default).
- Decoupling: `bigPictureLegendFits` stays pure Kotlin in `BigPictureFilterState.kt` alongside the `bigPictureCaseLegend`/`bigPictureTagLegend` helpers it complements; no `android.*` imports.
- Hygiene: `git status` clean throughout; no secrets or local paths in the diff.
- Naming: `LEGEND_VISIBLE_CHAR_BUDGET`/`LEGEND_CHIP_CHROME_CHARS` named and documented rather than inlined; no new `Voice` keys — only three existing Intense-only values were edited, so the "all three voices in one commit" rule doesn't apply.
- Hardcoded values: no new `Color(0xFF...)` literals — every chip fix reuses existing `ColorScheme` roles.
- Accessibility: chip padding/touch targets unchanged; Plain/Intense/Bright contrast changes verified manually by the user across light and dark, per this repo's convention.
- Deprecated APIs: see Considered and declined above — deliberately avoided.
- Spec review: HODITH_SPEC.md §9 updated for the legend row's new show-everything-or-nothing rule on a long partial selection.
- Tests: new unit coverage for `bigPictureLegendFits` (empty input, exact budget boundary, a shared Case+Tag budget, a realistic long selection against the real default budget); no existing test weakened.

**Docs updated:** HODITH_SPEC.md (§9 Big Picture legend row); TESTING.md (`bigPictureLegendFits` added to the Big Picture filter helpers list); this entry (oldest entry, `feature/manual-duration-ongoing-stop`, removed per the 5-entry retention rule).

---

## fix/intensity-toggle-gating

**Scope:** PROGRESS.md item "Intensity-toggle: trend/notification code ignores the Case flag" (filed as a follow-up while fixing `fix/intensity-required-when-enabled`): `InsightsTabState` correctly hides the Insights intensity stat card once `Case.intensityEnabled` is off, but two downstream readers of `EventEntity.intensity` had no awareness of the flag at all — a `TAG_OUTCOME` Trends finding on `TagOutcome.INTENSITY` could still surface from stale per-event ratings, and an `OFTEN` Watch's `minIntensity` floor kept filtering by them too, both after the Case's own stat card and panel had already gone quiet about intensity.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `test` (full unit suite), `lintDebug`, and `assembleDebug` all pass. `connectedDebugAndroidTest` not run this pass — no emulator driven directly (this repo's convention); the one instrumented change (`CaseDetailWatchesTabTest`'s `verdictFor` helper gaining a defaulted `intensityEnabled` param) is behaviorally inert for every existing test there, left for the user's own device run.

**Found & fixed:**
- `StatsEngine.computeTagOutcomeFindings` gained the `eligibleOutcomes: Set<TagOutcome>` parameter its siblings `computeTrendSlopeFindings`/`computeTimeOfDaySplitFindings` already had, filtering `TagOutcome.entries` the same way; `TrendsEngine.computeTrendFindings` now threads its existing `statsShownOutcomes` param through to it, so `InsightsTabState`'s already-correct gating reaches the tag-outcome detector for free.
- `WatchEngine.expectationInputsFor`/`evaluateOften` gained an `intensityEnabled: Boolean = true` parameter; the `minIntensity` filter no longer applies when it's `false`, since `WatchEntity.minIntensity` is stored independently of the Case's own flag and survives it being turned off. Wired the live `Case`/`CaseSeed` flag through the three real call sites: `NotificationEvaluator`, `WatchesViewModel.watchCardState` (via `WatchesTab.kt`'s existing `uiState.intensityEnabled`), and `DemoDataSeeder`.
- Regression tests added at both layers: `TrendsEngineTest` (a `TAG_OUTCOME` suppression test mirroring the existing trend-slope one), `InsightsTabStateTest` (end-to-end: intensity off, strong planted tag→intensity signal, no leaked finding), and `WatchEngineTest` (an `OFTEN` watch with `minIntensity` set but `intensityEnabled = false` counting events that would otherwise fail the floor).
- Updated the 14 `StatsEngineTest`/5 `TrendsEngineTest` call sites that previously relied on `computeTagOutcomeFindings`/`computeTrendFindings`'s old gating-free default and would otherwise have silently stopped seeing their planted `TAG_OUTCOME` finding.

**Deferred:** nothing.

**Considered and declined:**
- Making `computeTagOutcomeFindings`'s new `eligibleOutcomes` param required (no default) while defaulting `WatchEngine`'s new `intensityEnabled` param to `true` looks inconsistent at a glance, but each matches its own file's existing convention: `computeTrendSlopeFindings`/`computeTimeOfDaySplitFindings` already require their gating param explicitly, while `WatchEngine`'s `tracksDuration`-adjacent params are already caller-supplied without a shared default elsewhere. Defaulting `intensityEnabled` kept ~20 existing `WatchEngineTest` call sites (none of which construct a `Case`) unchanged; requiring `eligibleOutcomes` kept `computeTagOutcomeFindings` consistent with its two siblings, at the cost of touching `StatsEngineTest`'s call sites once.

**Checks:**
- Duplication: no new detector/significance-test logic — reused the existing `eligibleOutcomes`/`statsShownOutcomes` gating mechanism and `WatchEngine`'s existing filter shape.
- Decoupling: `StatsEngine.kt`/`TrendsEngine.kt`/`WatchEngine.kt` stay pure Kotlin (`domain/`), no `android.*` imports; every `now`/flag value stays caller-supplied.
- Naming: no new `Voice` keys — no user-visible copy changed.
- Hygiene: `git status` clean throughout; no secrets or local paths in the diff.
- Spec review: `HODITH_SPEC.md` updated (§10 tag → outcome's gating, §11's `minIntensity` table row) — unintentional divergence (a genuine bug), not a deliberate spec change.
- Tests: no weakened assertions — every touched call site either gained an explicit gating argument or a brand-new test exercising it; `StatsEngineTest`'s detector-math tests keep exercising every outcome unfiltered via `TagOutcome.entries.toSet()`.

**Docs updated:** PROGRESS.md (the resolved item struck entirely); HODITH_SPEC.md (§10 tag → outcome gating, §11 `minIntensity` table row); TESTING.md (Watch evaluation and Stats & visual data prep rows extended); this entry (oldest entry, `fix/big-picture-day-detail-chronological-order`, removed per the 5-entry retention rule).

