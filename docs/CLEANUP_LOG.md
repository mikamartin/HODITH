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

