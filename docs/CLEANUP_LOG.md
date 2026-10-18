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

## fix/single-case-widget-missing-case-edit-icon

**Scope:** PROGRESS.md item: the Single-case widget had no persistent in-widget way back into its Case picker. Decided against a persistent edit icon on a *populated* Single-case widget (remove-and-recreate already covers that, and the widget is already a dense 1x1 square) — the one real gap is when the bound Case gets deleted: the widget fell back to an unconstrained "Case is gone" message with no way to rebind short of deleting the widget outright. Fixed by showing a "✎" edit icon in that missing-Case state only, in the exact slot the "+" log button normally occupies, reopening `SingleCaseWidgetConfigureActivity` to rebind. Also removed, per explicit request: the one-line description under the title in both widgets' configure dialogs (List and Single-case) — the title alone is self-explanatory.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (7 changed `.kt` files, 3 docs). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` run scoped to `WidgetChromeNavigationTest` + `SingleCaseWidgetConfigureFlowTest` (14 tests, all green) — the emulator initially had no HODITH package installed at all (a Gradle up-to-date/device-state mismatch, not a code issue), resolved with `installDebug installDebugAndroidTest --rerun-tasks` plus `adb shell appwidget grantbind`.

**Found & fixed:**
- `SingleCaseWidget.kt`: `provideGlance` now computes `appWidgetId` (mirroring `ListWidget.kt`); the missing-Case branch is a new private `SingleCaseMissingContent` composable structured like `SingleCaseContent` (weighted message area + a bottom `MinTapTarget` slot) instead of one unconstrained full-size `Text` — this also fixes the reported symptom of the "Case is gone" message rendering oversized, as if sized for the bigger widget. The bottom slot is a "✎" glyph (neutral `onSurface` color, no filled box — matching the List widget's header edit icon's look, not the "+" log button's accent/bold/filled-box look it replaces) that reopens `SingleCaseWidgetConfigureActivity` via the same `ACTION_APPWIDGET_CONFIGURE`/`EXTRA_APPWIDGET_ID` intent shape `ListWidget.kt`'s header icon uses.
- `Voice.kt`: added `singleCaseWidgetEditButtonDescription` (×3 voices) for the new icon's `contentDescription`; removed `widgetConfigureBody`/`singleCaseWidgetConfigureBody` (×3 voices each) and their `item { Text(...) }` lines in `ListWidgetConfigureActivity.kt`/`SingleCaseWidgetConfigureActivity.kt`'s `LazyColumn`s.
- `WidgetCommon.kt`'s `WidgetGlyphButtonSize` doc comment and `SingleCaseWidgetConfigureActivity.kt`'s class doc comment updated to describe the new third caller / re-bind entry point.
- Tests: `WidgetChromeNavigationTest` gained a tap-opens-Activity test and a content-description test for the new icon (plus a class KDoc update); `SingleCaseWidgetConfigureFlowTest` gained a real end-to-end test — insert and bind a Case, delete it, confirm the missing-Case state renders, reopen the same configure Activity, and confirm rebinding to a different real Case clears the missing-Case message. Reviewed the rest of the widget test suite (`WidgetActionsFlowTest`, `WidgetLogTrampolineActivityTest`, `WidgetRefreshWorkerTest`, `WidgetTokenFidelityTest`, both ViewModel unit tests, `WidgetConfigureTestFixtures.kt`) — none touch the missing-Case path or the removed body text, so none needed changes.

**Deferred:** nothing.

**Considered and declined:**
- A persistent edit icon on a *populated* Single-case widget — explicitly declined by the user; remove-and-recreate is an acceptable path there, and the 1x1 square is already dense (icon, subtitle, log button).

**Checks:**
- Duplication: the new icon reuses `MinTapTarget`/`WidgetGlyphButtonSize`/`WidgetPalette` tokens and the exact `ACTION_APPWIDGET_CONFIGURE` intent shape already established by `ListWidget.kt`'s header icon — no new pattern introduced.
- Decoupling: no `ViewModel`/`Repository`/domain code touched; `SingleCaseWidgetConfigureViewModel` needed no change at all (it already seeds `selectedCaseId = null` unconditionally, which is exactly right for a re-bind with nothing to preselect).
- Complexity: one new composable (`SingleCaseMissingContent`, ~40 lines), mirroring the existing `SingleCaseContent` shape; no new abstraction layer.
- Dead code & hygiene: removed the now-unused `glanceContext` local in `SingleCaseWidget.kt` left over from the old inline `Text`'s `clickable`; `git status` clean throughout; ktlint found nothing stray.
- Naming: new `Voice` key added to all three voices in the same commit; no new files.
- Hardcoded values: none introduced — all sizes/colors pull from existing `WidgetCommon.kt` tokens.
- Accessibility: new edit icon has a non-empty, voice-backed `contentDescription`; its tap target is the existing `MinTapTarget`-sized slot (≥48dp).
- UI Copy Brevity: the three new `singleCaseWidgetEditButtonDescription` strings checked against their `widgetEditButtonDescription` sibling for length/tone parity per voice.
- Background Work, Widgets & Notifications: missing-Case state still renders via `WidgetPalette`'s fixed Plain-light palette, matching the widgets' theming constraint.
- Spec review: `HODITH_SPEC.md` §15 updated — removed the stale "Single-case widget doesn't have this yet" note on the List widget's edit-icon bullet, and added a sentence to the Single-case widget's own bullet describing the missing-Case rebind path and the no-persistent-edit-affordance-while-bound decision.
- Tests: bug fixed (no rebind path) → regression test added (the new `SingleCaseWidgetConfigureFlowTest` case); all new instrumented tests actually run on a device before this entry was written.

**Docs updated:** PROGRESS.md (resolved item struck entirely); HODITH_SPEC.md (§15, two bullets); TESTING.md (Widgets — chrome & trampoline row); this entry (oldest entry, `fix/bright-intensity-label`, removed per the 5-entry retention rule).

---

## fix/cases-intense-typography-roles

**Scope:** User-reported: Intense voice's distinctive display face (Oswald, bold) seemed to be missing from most of the Cases screens, showing up correctly only in filters, some labels, and buttons. Traced to `Type.kt`'s typography-swap mechanism itself being correct (no wiring bug) — several call sites on Cases screens were just choosing a body-face role (`titleSmall`) for text that functions as a header/label, instead of a display-face role (`labelLarge`).

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (3 changed `.kt` files, 1 doc). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. No instrumented test run needed — see Tests below.

**Found & fixed:**
- `InsightsTab.kt`: all 8 Case Detail Insights-tab section headers (Heatmap, Frequency, Rhythm, Gaps & streaks, Trends, Duration, Intensity, Tags) were rendering on `titleSmall`. The 5 `SectionWithInfo(...)` call sites explicitly overrode `labelStyle` down to `titleSmall`; deleted the override so each falls back to `SectionWithInfo`'s own correct default (`labelLarge`). The 3 plain `Text(voice.X, style = titleSmall)` call sites changed to `labelLarge` directly.
- `WatchesTab.kt`: the Watch card's own title and the headline observed-rate figure both used `titleSmall` + a manually bolted-on `fontWeight = FontWeight.Bold`; changed to `labelLarge`, keeping the explicit `FontWeight.Bold` override so Plain's cards stay visually bold the same way `CardEyebrow` (same file) already deliberately forces bold regardless of theme, rather than only getting Plain's default `SemiBold` display weight.
- `HodithTabRow.kt`: `HodithTab`'s `ProvideTextStyle(titleSmall)` hardcoded the body face for every tab label — fixed to `labelLarge`. Shared by Case Detail's History/Insights tabs and the Share screen's tabs, so both get the correct role in the same fix; no new inconsistency introduced.
- Confirmed before changing: Material3's default `Typography()` gives `titleSmall` and `labelLarge` the identical base size/line-height/weight (14sp/20sp/Medium) — so every change above swaps only font family and the theme's display weight, not layout size. The two dynamic strings touched (`WatchesTab.kt`'s card title and rate label) already wrap without truncation (`Modifier.weight(1f)`, no `maxLines`/`overflow`), so the bolder Intense/Bright display weight can't clip them; the section/tab labels touched are fixed English words identical across all three voices (`Voice.kt`'s own doc comments confirm this), so there was no longer-Intense/Bright-phrasing wrap risk to check either.

**Deferred:**
- Two broader, non-Cases-specific gaps found during the same audit — spun out as one new PROGRESS.md item rather than fixed here, since both need a design call first: every screen's `TopAppBar` title (About, Archived Cases, Case Edit, Log Detail, Manage Tags, Share) renders unstyled/body-face though `Type.kt`'s comment says the app bar should take the display face; and `HomeScreen.kt`'s Bright-only case-list item manually overrides a case name to the display face with no Intense/Plain equivalent.

**Considered and declined:**
- `CaseDetailScreen.kt`'s `TopAppBar` title (the case name itself) — initially flagged, then declined: `Type.kt`'s own comment says case names stay body-face (`titleMedium`), matching `ArchivedCasesScreen.kt`'s and `HomeScreen.kt`'s Plain/Intense case-name rows. Correct as-is; folded into the deferred PROGRESS.md item's reasoning instead of touched here.
- `ArchivedCasesScreen.kt`'s case-name row (`titleMedium`) — declined for the same reason; matches the established case-name-is-body-face pattern exactly.
- `WatchesTab.kt`'s empty-state title (`titleMedium`) — declined; matches the app-wide empty-state convention (`CenteredEmptyState` is unstyled/body-face everywhere else too), no sibling pattern argues for display-face here.
- `CaseEditScreen.kt`'s icon-grid selected-icon labels (`titleMedium`) — declined; these render emoji glyphs, so font-family has no visible effect either way.
- `DateRangeFilterDialog.kt`'s date-range headline (`titleLarge` → `titleMedium`) — declined; its own comment explains this is a deliberate width-only downsize for a dialog-width row, and the text it styles (a selected date range) is content, not a label, so body-face was already the right role.

**Checks:**
- Duplication: no new composables, strings, or logic; pure style-role correction reusing existing `Voice` keys and the existing `SectionWithInfo`/`labelLarge` default.
- Decoupling: no ViewModel/Repository/domain code touched; no `android.*` imports added.
- Complexity: no new composables or structural changes, only `style =`/`ProvideTextStyle(...)` argument swaps.
- Dead code & hygiene: `git status` clean throughout; `ktlintCheck` found nothing stray after the `labelStyle` line deletions.
- Naming: no new files, no new `Voice` keys.
- Hardcoded values: none introduced.
- Accessibility: no tap-target or contrast changes; light/dark unaffected (role swap only touches font family/weight, not color).
- UI Copy Brevity: checked above (Found & fixed, last bullet) — no label-fit issue from the weight/face change.
- Data model/migrations, background work/widgets/notifications, deprecated APIs: untouched — N/A.
- Spec review: `HODITH_SPEC.md` doesn't describe typography roles at this level of detail; no divergence to reconcile.
- Tests: no behavior change — a style-role correction, not new logic — so no new test coverage; existing Compose UI tests for Case Detail/Insights/Watches/Share tabs assert content and interaction, not font family, and stay unaffected.

**Docs updated:** PROGRESS.md (new Standalone item for the two deferred findings); this entry (oldest entry, `fix/big-picture-selector-chip-contrast`, removed per the 5-entry retention rule).

---

## fix/widget-case-name-overlap

**Scope:** User-reported bug: in both widget "add/edit" configure dialogs (List widget's multi-select `Switch` row, Single-case widget's single-select `RadioButton` row), a long Case name had no width constraint on its label `Text`, so it was measured at its full intrinsic width and could grow past the row's own bounds, overlapping the toggle/radio control instead of stopping short of it.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (3 changed files, 2 new test files). `ktlintCheck`, `lintDebug`, and `test` (full unit suite) all pass. `connectedDebugAndroidTest` not run this pass — no emulator/device was available in this session; reasoned through (not run) compatibility with the existing `ListWidgetConfigureFlowTest`/`SingleCaseWidgetConfigureFlowTest` real-activity flow tests below, left for the user's own device run before merge, per this repo's standing instrumented-test convention.

**Found & fixed:**
- `ui/common/ToggleRow.kt`'s label `Text` (shared by every `Switch`-based label row in the app — Big Picture, Case Edit, Case Detail, Share tabs, and now the List widget's case picker) gained `Modifier.weight(1f)`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, matching the established idiom elsewhere in the codebase (`WatchesTab.kt`, `InsightsTab.kt`, `HomeScreen.kt`).
- `ListWidgetConfigureActivity.kt`'s private `CasePickerRow` was hand-rolling the exact `Row`/`Arrangement.SpaceBetween`/`Switch` shape `ToggleRow` already provides — duplication the original bug exposed. Replaced with a direct `ToggleRow(...)` call, removing five now-unused imports (`Arrangement`, `Row`, `fillMaxWidth`, `Switch`, `Alignment`, `Modifier`) along with it.
- `SingleCaseWidgetConfigureActivity.kt`'s `CasePickerRow` (label then `RadioButton`, no description) can't reuse `ToggleRow` (`Switch`-specific) or the existing `RadioOptionRow` (radio-first, mandatory description, a different shape entirely — see Considered and declined) — given the same `weight`/`maxLines`/`overflow` treatment in place instead.
- Both `rowControlNode`/`clickRowControl` test helpers (`WidgetConfigureTestFixtures.kt`) that the existing flow tests use were re-read against this change: `ListWidgetConfigureActivity`'s row now resolves to `ToggleRow`'s single merged (`Modifier.toggleable`) node, so `isToggleable()` + the text-bounds-overlap match still resolves to the same node as before (the weight change only narrows the inner `Text`, not the outer row's own bounds). `SingleCaseWidgetConfigureActivity`'s row has no row-level merge, so `isSelectable()` still matches the `RadioButton` directly; its position is unchanged by the fix, which only ever pulls the label in — if anything, this makes the vertical-bounds heuristic more reliable, not less, since the label can no longer wrap/grow unpredictably.

**Deferred:**
- `connectedDebugAndroidTest` — see Walked above; needs the user's device, same as the most recent PDF-export passes.

**Considered and declined:**
- Reusing `RadioOptionRow` for the single-case widget's row instead of fixing `CasePickerRow` in place: `RadioOptionRow` puts the `RadioButton` first in a fixed-width slot with a mandatory description line below the label — a different shape from this row's icon+name-then-radio, no-description layout. Forcing the fit would mean a bigger UI change than the bug called for.

**Checks:**
- Duplication: the `ListWidgetConfigureActivity` fix above removes a duplicated `Row` shape by routing through `ToggleRow`; the single-case row's near-identical-but-not-quite shape was evaluated for the same treatment and declined (see above).
- Decoupling: no `ViewModel`/`Repository`/`domain` code touched — layout-only, inside existing Composables.
- Complexity: no new composables; `ToggleRow`/both `CasePickerRow`s stay well under the line-count threshold.
- Hygiene: `git status` clean throughout; no secrets or local paths; `ktlintCheck` caught no stray unused imports after the manual cleanup above.
- Naming: two new `internal const val ..._TAG` constants (`TOGGLE_ROW_SWITCH_TAG`, `CASE_PICKER_RADIO_TAG`) follow the existing `HISTORY_SHARE_PDF_TOGGLE_TAG`/`SECTION_TOGGLE_TAG_PREFIX` convention, added purely for direct test access to each row's control; `SingleCaseWidgetConfigureActivity.CasePickerRow` changed from `private` to `internal` for the same reason, mirroring `SegmentedChoiceRow`'s existing public visibility for testability.
- Accessibility: no tap-target size changes; the fix only prevents the label from encroaching on the existing `Switch`/`RadioButton` footprint.
- UI Copy Brevity: N/A — no fixed UI label text changed; the truncated string is user-entered Case data, not app copy.
- Hardcoded values, data model/migrations, deprecated APIs, spec review: untouched — N/A. `HODITH_SPEC.md` doesn't describe either dialog's row layout at this level of detail, so no divergence to reconcile.
- Background work, widgets & notifications: the actual Glance widget tiles (`ListWidget.kt`, `SingleCaseWidget.kt`) were checked for the same bug and don't have it — `ListWidget.kt`'s case-name `Text` already has `maxLines = 1` inside a `.defaultWeight()` `Column`, and `SingleCaseWidget.kt` never displays the case name on-tile at all — so neither needed a change; Plain-light-only theming and `logFlow` handling are untouched either way.
- Tests: two new instrumented Compose tests (`ToggleRowTest`, `CasePickerRowTest`), both `@UiTest`-tagged, bounds-based (not click-based) regressions modeled on `SegmentedChoiceRowTest`'s pattern — deliberately sidestepping the known `ToggleRow` merge-reliability caveat already documented in TESTING.md's "Known environment issues" rather than relying on click dispatch. Existing flow tests reviewed for compatibility, not re-run (see Walked/Deferred above). No `@Smoke` tag added — no shared-component row test in this codebase carries one.

**Docs updated:** TESTING.md (shared-components row gains `ToggleRowTest`; the widget chrome/trampoline row gains `CasePickerRowTest`); this entry (oldest entry, `fix/intensity-toggle-gating`, removed per the 5-entry retention rule).

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

