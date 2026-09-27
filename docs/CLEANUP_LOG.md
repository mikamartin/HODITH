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

## feat/insights-trends-share

**Scope:** PROGRESS.md's "Replace the share card's old trend arrow with real Trends findings" — the Insights tab had already folded its standalone Trend arrow into `stats.trends` (`TrendFindingKind.FREQUENCY_SHIFT`), but the Share card still sourced a single up/down/flat arrow from a separate, vestigial `StatsSections.trend`/`TrendDisplay` path. Mid-review, the user also asked for a generation-date footer, since a still-open `WENT_QUIET` finding (kept eligible on the card per a design decision resolved with the user before implementation) is only true at the moment the card is made.

**Changes:**
- `ui/casedetail/InsightsTab.kt`: extracted `trendsVisibleCount`/`trendsVisibleFindings` (was an inline ternary in `TrendsCard`, now `internal`) and `trendFindingSentence`/`trendFindingEvidenceLabel` (split out of `TrendFindingContent`'s single combined `when`, `trendFindingSentence` made `internal`) so the Share card reuses both instead of duplicating the cap rule or the 12-way sentence dispatch. `TRENDS_DEFAULT_VISIBLE_COUNT` made `internal`.
- `viewmodel/ShareCardState.kt`: `ShareInsightsSection.TREND` renamed `TRENDS`; `ShareCardData.trend: TrendDisplay?` replaced by `trends: List<TrendFinding>` (pre-capped via `trendsVisibleFindings`); new `generatedAtMillis: Long` field, threaded as a new `shareCardState()` parameter.
- `viewmodel/InsightsTabState.kt`: `StatsSections.trend`/the `TrendDisplay` class deleted along with their computation in `statsSections()` — `trendStatsResult` itself stays, still feeding `computeTrendFindings`'s `FREQUENCY_SHIFT` finding.
- `ui/share/SharePreviewScreen.kt`: `availableSections`/`SectionsPicker` gained a `trendsAvailable` parameter (`stats.trends.isNotEmpty()`, mirroring the existing `frequencyAvailable`), replacing the old unconditional `TREND` entry; the `shareCardState()` call passes the screen's existing `now` straight through as `generatedAtMillis`.
- `ui/share/ShareCardTemplate.kt`: `MiniTrendSection` (single arrow glyph + `insightsTrendSentence`) replaced by `MiniTrendsSection` (iterates `data.trends`, one `trendFindingSentence` line each — no reliability tag, no evidence line); `ShareCardFooter` now takes `generatedAtMillis`, formatting it via the existing `formatEventDate` helper.
- `ui/voice/Voice.kt`: `insightsSectionLabelTrend` (singular) deleted, now unused; `shareCardFooter` changed from a `val` to `fun shareCardFooter(date: String)` appending the formatted date — stays a single shared implementation across all three voices (structural, like `insightsSectionLabelGaps`), not newly-authored per-voice copy.
- `domain/TrendsEngine.kt`: doc comment's dangling references to `StatsSections.trend` and this now-deleted PROGRESS.md item trimmed.
- Tests: see below.
- `docs/HODITH_SPEC.md`, `docs/TESTING.md`, `docs/PROGRESS.md`: see Docs updated.

**Checklist walk (against the working-tree diff):**
- *Duplication* — the `InsightsTab.kt` extraction exists specifically so the cap rule and the 12-way sentence `when` aren't duplicated; `viewmodel/ShareCardState.kt` importing `ui.casedetail.trendsVisibleFindings` matches `BackupValidationResult.kt`'s existing precedent (viewmodel already imports plain, non-Composable declarations from `ui.*` files), not a new layering violation.
- *Decoupling* — `generatedAtMillis` flows from `SharePreviewScreen.kt`'s existing, already-Clock-derived `now` parameter; production code never calls `System.currentTimeMillis()` directly. The one direct call added is in `ShareCardTemplate.kt`'s own `@Preview` fixture, matching that file's existing `LocalDate.now()` precedent for preview-only data.
- *Complexity & pattern health* — net simpler: one shared cap/sentence implementation instead of a second copy waiting to drift; no composable crossed the ~150-line split threshold.
- *Dead code & hygiene* — `TrendDisplay`, `MiniTrendSection`, `StatsSections.trend`, `ShareCardState.trend`, and `insightsSectionLabelTrend` all deleted outright, not left dangling. This walk itself caught and fixed three things mid-edit: a doc comment orphaned by the `InsightsTab.kt` extraction; a new `ShareCardTemplate.kt` doc comment pointing at "PROGRESS.md's acceptance criteria" for an item this same diff deletes (reworded to cite spec §13 instead); and `ktlintFormat`'s own auto-fix pass silently rewriting three files (`InsightsTab.kt`, `ShareCardState.kt`, `ShareCardStateTest.kt`) as pure LF instead of this repo's CRLF — caught by directly counting `\r\n` vs. total lines per touched file rather than trusting `git diff`'s CRLF-normalization warning at face value, then converted back to CRLF with no content change. `git status` clean, no untracked files. `ktlintCheck` and a full `compileDebugAndroidTestKotlin` both passed clean.
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — `MiniTrendsSection`/`trendFindingSentence`/`trendsVisibleFindings` follow existing conventions; no new per-voice Voice keys needed.
- *Hardcoded values* — none; reused the existing `TRENDS_DEFAULT_VISIBLE_COUNT` constant rather than introducing a new magic number for the share-card cap.
- *Accessibility* — no new tap targets (Trends findings render as plain text, matching the "no tap-revealed detail" design decision); not independently verified on-device this pass — no emulator available, left for the user's own manual pass per their standing instruction.
- *Data model, migrations & privacy* — Share card still excludes notes/tags (§13); confirmed Trends sentences are descriptive stats like every other section already shown, not raw logged text, so no new exception was needed.
- *Background work, widgets & notifications, deprecated APIs* — not applicable, nothing in these areas touched.
- *Spec review* — walked HODITH_SPEC.md §10 and §13 end to end: found and fixed the Frequency-shift bullet's stale "former standalone Trend arrow" aside (§10) alongside the intended Trend→Trends/footer updates (§13). §17 has no Future Work entry referencing this to update.

**Tests:**
- `ShareCardStateTest.kt`: updated the 3 existing assertions referencing `.trend`/`TREND`; added coverage for selection gating (findings exist but aren't selected), the 3-finding cap and the `WENT_QUIET`-leads-1 exception (via a directly-constructed synthetic `StatsSections`/`InsightsTabState.Ready`, mirroring `InsightsTabTrendsCardTest.kt`'s own synthetic-fixture convention rather than fighting real detector thresholds), and `generatedAtMillis` passthrough. 17 tests, all green.
- `InsightsTabStateTest.kt`: 5 tests referencing the retired `stats.trend` field converted to check the `FREQUENCY_SHIFT` finding in `stats.trends` instead, preserving each test's original intent (gating at exactly 2 events, span minimum, multi-day-event parity).
- `InsightsTabTrendsCardTest.kt`: dropped its now-invalid `trend = null` fixture line.
- `ShareCardTemplateTest.kt`: `realityData`/`richData`/`hunchVsRealityData` fixtures moved to `trends`/`generatedAtMillis`; added sentence-only rendering (no reliability tag, no evidence line), multi-finding rendering, and footer-date-wiring tests.
- `SharePreviewScreenTest.kt`: added Trends-toggle-presence tests (hidden with no findings, shown with at least one), mirroring the existing Duration/Intensity presence tests' shape. First version asserted on the "Trends" label text directly and failed on-device — 2 nodes matched, since with findings present that text also renders in the screen's own live card preview above the picker, not just the toggle row (the exact ambiguity `sectionChecklist_togglingARow_invokesCallbackWithTheSection` already works around elsewhere in this file). Fixed by asserting on the toggle's tag (`SECTION_TOGGLE_TAG_PREFIX + TRENDS.name`) instead, matching that existing convention.
- Full JVM suite: 906/906 pass. `connectedDebugAndroidTest` scoped to the three changed classes (`ShareCardTemplateTest`, `SharePreviewScreenTest`, `InsightsTabTrendsCardTest`) on `Pixel_8_API36(AVD)`: 34/34 pass, after the fix above (first run: 33/34, the text-ambiguity failure caught it).

**Deferred:** nothing — both open design questions PROGRESS.md flagged for this item (whether to keep `WENT_QUIET` on the card, and the per-card finding cap) were resolved with the user via a direct question before implementation started, not left as unresolved judgment calls in this pass.

**Docs updated:** `HODITH_SPEC.md` §10 (Frequency-shift bullet trimmed of historical framing), §13 (Trend→Trends description, footer bullet gains the generation-date mention); `TESTING.md` (Stats & visual data prep row's stale "trend arrow"/"trend card" phrasing corrected in two places, Share card assembly row extended for the cap/`WENT_QUIET`/timestamp coverage); `PROGRESS.md` (item removed entirely). A quick pass over PROGRESS.md's other Standalone items' branch names against git history found none already shipped — nothing else to flag there.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green, against the full accumulated diff. `connectedDebugAndroidTest` scoped to `ShareCardTemplateTest`/`SharePreviewScreenTest`/`InsightsTabTrendsCardTest` on `Pixel_8_API36(AVD)`: 34/34 pass (see Tests above for the one failure this caught and its fix).

---

## feat/csv-export

**Scope:** PROGRESS.md's "CSV export of case/event data" — the one Standalone item tagged with none of the doc's 🎨/🔍/🌐 markers, so it needed no upstream design call before implementation. Spec §17 already scoped the shape (a new writer alongside `BackupFileWriter`, a Settings row, Voice ×3, export-only). Two judgment calls were surfaced to the user before writing any code: timestamps render as ISO-8601 with the event's own captured offset (not raw epoch millis or the device's current zone), and archived Cases (and their events) are excluded from the CSV entirely, unlike the JSON backup which includes everything. A later pass, still on this branch before merge, revised the UI after the user tried the two-button layout: a separate "Export as CSV" row read as a second, easy-to-miss export path, and neither button explained what its format was for or gave the resulting file a way to tell it apart from an earlier export. This pass replaces the two buttons with one "Export data" row that opens a format-choice dialog (JSON/CSV, each with a one-line explainer — CSV's names Excel/Google Sheets/LibreOffice), and adds a `yyyyMMdd-HHmm` timestamp to both formats' default filenames. Supersedes this entry's own prior version, rewritten in place since the branch hasn't merged yet.

**Changes:**
- New `data/backup/CsvBackupSerializer.kt`: a plain `toCsv(BackupData): String`, mirroring `BackupSerializer`'s shape. One row per event across active (non-archived) Cases, cases in `BackupData.cases` order then events ascending by `occurredAt`; blank (not `0`) `ended_at`/`duration_minutes` for an instant event; tags joined sorted and comma-separated; RFC 4180 quoting for a field containing a comma/quote/newline.
- `viewmodel/SettingsViewModel.kt`: `BackupEvent` gains `CsvExportSuccess`/`CsvExportFailure`; `performCsvExport`/`exportCsv` added alongside the existing JSON pair.
- New `ui/settings/ExportFormatDialog.kt`: an `AlertDialog` (same idiom as this package's `DeleteDataOptionsDialog`) offering a JSON/CSV radio choice, each with a `Voice`-worded explainer, defaulting to JSON since it's the one format HODITH can restore from.
- `ui/settings/SettingsScreen.kt`: the Data plank's separate "Export data"/"Export as CSV" rows collapsed into one "Export data" row that opens `ExportFormatDialog`; confirming routes to the existing `onExportClick`/`onExportCsvClick` callbacks by chosen format. Both export launchers now build their filename at tap time via a new `backupFileName`/`csvFileName` pair, each appending a `yyyyMMdd-HHmm` timestamp (no seconds — precise enough to tell same-day exports apart without a needlessly long name) computed off the existing `nowMillis`/`Clock` plumbing, not `System.currentTimeMillis()`.
- `ui/voice/Voice.kt`: `settingsCsvExportButton` removed; added `settingsExportFormatDialogTitle`/`...JsonOption`/`...JsonDescription`/`...CsvOption`/`...CsvDescription`/`...ConfirmAction`/`...CancelAction` to the interface and all three voices. `settingsCsvExportSuccessMessage`/`...FailureMessage` (the post-export snackbar text) are unchanged — only the button that used to lead to them is gone.
- `docs/PROGRESS.md`: item resolved and removed. `docs/HODITH_SPEC.md`: §16 gained a CSV sentence; §17's now-obsolete "CSV export" future-work entry removed; the Settings row in the screen table (§14) reworded for the format-choice dialog and timestamped filenames. `docs/TESTING.md`: the Export/import unit-coverage row and the Compose UI Data-actions bullet both extended/reworded.

**Checklist walk (against the full accumulated diff):**
- *Duplication* — no inline strings; every Voice key (old and new) landed in the same commit as its usage, all three voices. `exportData`/`exportCsv` share the `writeExport(uri, produceContent, successEvent, failureEvent)` helper (Uri/stream handling), leaving `performExport`/`performCsvExport` as the two independently unit-tested pure functions. `ExportFormatDialog`'s two options share one private `ExportFormatOption(label, description, selected, onSelect)` row rather than two near-identical `Row`/`RadioButton` blocks.
- *Decoupling* — `CsvBackupSerializer` has no `android.*` import; every row timestamp comes from the event's own captured `utcOffsetMinutes`, never the device's current zone. The new filename timestamp lives in the UI layer (`SettingsScreen.kt`), reads time through `viewModel.nowMillis()` (backed by the injected `Clock`), not a direct `System.currentTimeMillis()` call.
- *Complexity & pattern health* — `ExportFormatDialog` is a plain `AlertDialog` composition under 90 lines, matching `DeleteDataOptionsDialog`'s existing shape rather than inventing a new one; `ExportFormatOption` has two call sites and factors out real duplication, so it earns its keep. `rowFor`/`isoOffsetDateTime`/`csvField` in `CsvBackupSerializer` unchanged from the original pass.
- *Dead code & hygiene* — the old `BACKUP_FILE_NAME`/`CSV_FILE_NAME` constants and the two-button layout are fully removed, not left dead alongside the new dialog; `git grep` confirms no remaining reference to `settingsCsvExportButton` outside this log's own history. `ktlintCheck` clean. `git status` clean; only the intended files touched.
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — `ExportFormatDialog`/`ExportFormat` sit in `ui/settings` next to `DeleteDataDialogs.kt`'s `DeleteDataOptionsDialog`/`DeleteDataMode`, same enum-plus-dialog shape. New Voice keys follow the `settingsExportFormat*` prefix, grouped with their `settingsExport*`/`settingsImport*` neighbors.
- *Hardcoded values* — none; `MILLIS_PER_MINUTE` (from the original pass) is a unit-conversion constant, not a product threshold. The `yyyyMMdd-HHmm` pattern is a filename format, not a domain constant.
- *Accessibility* — `ExportFormatOption`'s whole row (not just the `RadioButton`) is `selectable` with `Role.RadioButton`, matching `CasePickerRow`'s existing precedent for a tap target larger than the control itself. Not independently verified in an emulator this pass (no connected device available) — manual/instrumented verification is left to the user per their standing instruction.
- *Data model, migrations & privacy* — none touched; UI-only change, no entity/column/schema impact.
- *Background work, widgets & notifications, deprecated APIs* — not applicable, untouched.
- *Spec review* — re-walked §14/§16/§17; §14's Settings row updated to describe the dialog and timestamped filenames, §16 unchanged (still accurately describes the underlying JSON/CSV capability, not UI structure).
- *Tests* — see below.

**Tests:**
- `CsvBackupSerializerTest` (11 cases, from the original pass, unchanged): empty backup → header only; instant event leaves `ended_at`/`duration_minutes` blank rather than `0`; a durationed event with intensity renders duration and its own non-zero captured offset; null intensity blank; no tags blank; multiple tags joined sorted with the resulting comma forcing quoting; a note containing a comma, a double quote, and a newline each quoted per RFC 4180; row ordering (case order, then ascending `occurredAt`); an archived case and its events excluded entirely.
- `SettingsViewModelTest`: `performCsvExport` wiring test (from the original pass, unchanged) against `FakeHodithRepository`. No ViewModel-level test changes needed this pass — the dialog and filename timestamp are both UI-layer, and neither `performExport`/`performCsvExport` changed.
- `SettingsScreenTest`: the old `exportButton_tapInvokesCallback`/`exportCsvButton_tapInvokesCallback` replaced with `exportButton_opensFormatDialog_jsonOptionConfirmInvokesOnExportClick` (`@Smoke`), `exportFormatDialog_csvOptionConfirmInvokesOnExportCsvClick` (`@Smoke`), and a new `exportFormatDialog_cancelInvokesNeitherCallback`.

**Deferred:**
- `exportCsv(uri: Uri)`'s Uri/stream path stays untested at the JVM unit level (from the original pass) — matching this repo's existing precedent for `exportData`/`importData`; `performCsvExport` carries the real coverage instead.
- This pass's `SettingsScreenTest` changes compiled and ran against `ktlintCheck`/`lintDebug`/`testDebugUnitTest`/`assembleDebug`, but not on a connected device — no emulator was available this session. Running `connectedDebugAndroidTest` scoped to `SettingsScreenTest` (and a light manual pass of the new dialog in at least one theme) is left to the user before merge.

**Docs updated:** `PROGRESS.md` (item resolved + removed, from the original pass), `HODITH_SPEC.md` (§16 sentence from the original pass; §14 Settings row reworded this pass), `TESTING.md` (Export/import unit row and Compose UI Data-actions bullet, both touched across the two passes).

**Verified:** `ktlintCheck → lintDebug → testDebugUnitTest → assembleDebug` sequential, all green against the full accumulated diff. `connectedDebugAndroidTest` for `SettingsScreenTest` last ran clean during the original pass (30/30, see git history for this entry's prior version); not re-run this pass — see Deferred.

---

## fix/future-start-time-clamp-notice

**Scope:** PROGRESS.md's "Log entry silently clamps a future start time to now" (resolved and removed from PROGRESS.md by this branch's first commit). A later review of that same fix, still on this branch before merge, found the result inconsistent: a future pick clamped silently to `now`, but a start time set later than an already-set end time wasn't caught at all until save, and an out-of-order end time was flagged but not corrected on screen — the notice said "matched to start" while the visible value stayed wrong until save quietly fixed it. This pass replaces all of that with one rule across all three constraints (future, start-after-end, end-before-start): an invalid pick is discarded outright, the field keeps its prior value, and a Voice-worded caption says why. Supersedes this entry's own prior version, rewritten in place since the branch hasn't merged yet.

**Changes:**
- `viewmodel/LogDetailViewModel.kt`: `isFutureClamped`/`isEndBeforeStart` replaced by `validateStartEdit`/`validateEndEdit`, each returning a `TimeEditRejection?` (`FUTURE`/`AFTER_END`/`BEFORE_START`, or `null` to accept). These are now the single source of truth for whether a start/end pick applies, called from the picker `onConfirm` sites before any state mutation — nothing is clamped anymore, so what's on screen always matches what a save would persist. Save-time clamping in `toEventEntity`/`computeEndedAt` is untouched — a correctness backstop, not the UI's concern.
- `ui/logsheet/LogDetailSheet.kt`: `DateTimePickers` takes `validate`/`onResult` instead of `onClampChanged`; a rejected pick is dropped rather than applied. `LogDetailForm` tracks `startNotice`/`endNotice` (`TimeEditRejection?`) instead of two booleans; a successful edit on either field clears both, since fixing one side can resolve the other's stale rejection. Dropped the "live" `endBeforeStart` derivation that read straight off the current draft every recomposition — unreachable now that both fields validate on entry, so it was pure defensive display for a state the UI can no longer produce. `TimeSection`/`EndTimeSection`'s notice moved from `bodySmall`/`onSurfaceVariant` to `labelMedium`/`colorScheme.error`, per user feedback that the original didn't read clearly as an error across all three themes — `colorScheme.error` is already tuned per theme (Intense uses amber, not red, precisely so it isn't confused with its crimson accent — see `Color.kt`'s own comment), and `labelMedium` resolves to each theme's bold display font rather than its plain body font.
- `ui/voice/Voice.kt`: `logSheetFutureTimeClampedNotice`/`logSheetEndBeforeStartClampedNotice` replaced by three keys — `logSheetFutureTimeNotice` (shared by both fields), `logSheetStartAfterEndNotice` (Start field), `logSheetEndBeforeStartNotice` (End field) — all three voices. Copy iterated live with the user: dropped the original "set to.../matched to..." phrasing since nothing is clamped now, then reworded Plain's future-time line again once "staying put" tested as confusing (didn't say what was wrong, or what "it" referred to).
- `docs/TESTING.md`: removed the Compose UI row for the now-deleted end-before-start static-state test; rewrote the Deferrals note — all three time-edit notices now share the same Compose-untestable-picker-interaction gap (previously end-before-start was the one exception, since it used to be derived from draft state alone).

**Checklist walk (against the working-tree diff):**
- *Duplication* — no inline strings; all three keys go through Voice in all three voices. `validateStartEdit`/`validateEndEdit` are the single source of truth for accept/reject, called from both the date and time `onConfirm` sites rather than re-deriving the comparison; a new `noticeText` helper centralizes the one rejection→Voice-key mapping so `TimeSection`/`EndTimeSection` don't each restate it.
- *Decoupling* — no `android.*` import in `LogDetailViewModel.kt`; both validators take plain `Long`/`Long?`/`now`, no `System.currentTimeMillis()`. Notice state stays in `LogDetailForm`'s Compose state.
- *Complexity & pattern health* — net simpler than before: one derived `val` and one `when` block removed, in exchange for two `TimeEditRejection?` `remember`s (was two `Boolean` `remember`s) and a 5-line mapping function. No new `LaunchedEffect`, no new sub-composable needed.
- *Dead code & hygiene* — no unused imports (`ktlintCheck` clean); confirmed no leftover `coerceAtMost`/`coerceIn` calls in the Compose file. `git status` clean aside from the pre-existing untracked `merged_branches.txt` (unrelated, flagged in every prior entry).
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — new Voice keys keep the `logSheet*Notice` convention; `TimeEditRejection`/`validateStartEdit`/`validateEndEdit` read as a pair with the existing `applyPickedDate`/`applyPickedTime` neighbors.
- *Hardcoded values* — none; `colorScheme.error` is a theme token, not a literal color.
- *Accessibility* — not independently verified in an emulator this pass (manual verification is left to the user per their standing instruction); the color/style reasoning above is for their review when they do.
- *Data model/migrations, background work/widgets/notifications, deprecated APIs* — none touched.
- *Spec review* — `HODITH_SPEC.md` still doesn't describe picker-validation behavior at this granularity; no update needed (unchanged from the prior pass).
- *Tests* — see below.

**Tests:**
- `LogDetailViewModelTest.kt`: `isFutureClamped`/`isEndBeforeStart`'s 4 cases replaced by 6 for `validateStartEdit`/`validateEndEdit`, covering every accept/reject boundary for both fields (a future candidate rejected ahead of an ordering check; equal-boundary values accepted).
- `LogDetailSheetTest.kt`: removed `startStopDraftWithEndBeforeStart_showsClampedNotice` — it asserted the now-deleted live-derived notice from a static invalid initial draft, a state the UI can no longer produce.
- No new instrumented coverage added for the reject/revert behavior itself: as before, no test in this codebase drives M3's `TimePicker`/`DatePicker` internals, and that gap now applies uniformly to all three notices rather than two of three (see `docs/TESTING.md`'s Deferrals).

**Deferred:**
- Compose-level verification of all three time-edit notices (see Tests) — the M3 picker-driving gap is pre-existing and repo-wide, not something to solve as a side effect of this fix.
- Manual/emulator verification of the notice's color and readability across all three themes' light/dark schemes — left to the user's own pass per their standing instruction.

**Docs updated:** TESTING.md (Compose UI row removed, Deferrals note rewritten).

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green, against the full accumulated diff.

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
