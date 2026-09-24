# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Standalone** — isolated items with no cross-dependencies; pick any when resources are thin.
- **Deferred** — startable, but intentionally held back pending a trigger (usually real alpha usage) rather than gated on something external.
- **Blocked** — gated on something external; not startable now.

Each item carries:

- a **trailer** — *Branch · Complexity · Priority · Area*. Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week (same scale as HODITH_SPEC §17). Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet. Area is a loose bucket — Bug / Big Picture / Insights / Hunch / Share / Settings / Voice / Performance / Repo.
- zero or more **tags** — 🎨 *Design decision* (needs a design or product-owner call before implementation) · 🌐 *External action* (work outside this repo) · 🔍 *Investigation* (needs a repro/diagnose pass before the fix is knowable).
- **Acceptance criteria** — the checklist that says "done".
- **Plan / Tests / Concern** — detail, unchanged from prior tracking.

## Story B — copy & Voice

Two items, plus the tail of nearly everything else. Anything that adds or changes a Voice key must land before B2.

### B1 · Square share format should become a fixed preset

*Branch: `feat/square-share-card-preset` · Complexity: M · Priority: Medium · Area: Share*

🎨 **Design decision** — which sections, and in what fixed order, Square always shows. Touches Voice copy, so before B2.

Story stays the one fully customizable, auto-sizing format. `shareCardState()` applies `selectedSections` identically to both formats, and `SharePreviewScreen.kt`'s `SectionsPicker`/`availableSections` render the same toggles for both — but Square keeps a 1:1 floor while Story sizes freely to content, so selecting every Insights section on Square produces a tall rectangle instead of the predictable square shape it's for.

**Acceptance criteria**

- [ ] A documented fixed section list + order for Square.
- [ ] `SectionsPicker` renders only when `ShareCardFormat.STORY` is selected.
- [ ] `shareCardState()` sources Square's sections from the fixed preset, independent of `selectedSections`.
- [ ] Story keeps full customization and content-sizing.
- [ ] Any Story-only picker copy goes through Voice ×3.
- [ ] Tests: `ShareCardStateTest.kt` (Square driven by preset), `SharePreviewScreenTest.kt` (picker only for Story); `ShareCardTemplateTest.kt` Square floor/no-clip still passes.
- [ ] `docs/mockups/share-cards-prototype.html` deleted and its `ShareCardDecoration.kt` KDoc pointer dropped — it was the last mockup left in that directory, kept only as this item's Story/Square section-layout reference (`chore/prune-design-mockups` removed the other five).

**Plan** — decide Square's fixed section list first. Then gate `SectionsPicker` to `ShareCardFormat.STORY` only, and source Square's sections from the preset in `shareCardState()`.

**Tests** — `ShareCardStateTest.kt` and `SharePreviewScreenTest.kt` cover the preset-driven output and Story-only picker; `ShareCardTemplateTest.kt`'s floor/no-clip tests keep passing unchanged.

### B2 · Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and needs a human ear. **Must land last**, after every other copy-touching item (currently just B1).

Fold these already-drafted key changes into the audit:

- `feat/declutter-nudges` — reworded Serious `checkInDueNotificationBody`; renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle`.
- `feat/insights-from-first-event` — added `insightsNothingLoggedMessage`, `insightsSingleEventNote` (replacing `insightsNotEnoughDataMessage`).
- `feat/big-picture-overview-detail` — retired `bigPictureEventNoteEmptyState`; added `bigPictureDetailDialogTitle`, `bigPictureDetailEditDescription`, four shared field labels.
- `feat/resolved-hunch-list-redesign` — retired `hunchHistoryRowText`; added `hunchHistoryShowMoreAction`, `hunchHistoryRetentionNote`.

**Acceptance criteria**

- [ ] A written rubric: per-voice person, tense, sentence length, punctuation/emoji budget, locked Case/Hunch/Verdict/Event/Trigger vocabulary, and an em-dash policy with per-string calls for Goth/Quirky mid-sentence pivots.
- [ ] A findings list produced first; fixes in a separate second commit.
- [ ] Audit done in slices by screen, not by reading `Voice.kt` linearly.
- [ ] New mechanical `VoiceTest` invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab/button labels, no double spaces or trailing whitespace.
- [ ] Confirmed before starting: `androidTest` references `PlainVoice` by constant, not literal, everywhere (grep for hardcoded UI literals).

**Plan** — 720 strings total: 213 keys declared per-voice (639 strings) need independent authorship; 81 shared `get()`/default-body keys are reviewed once. Write the rubric first (person, tense, sentence length, punctuation/emoji budget, locked Case/Hunch/Verdict/Event/Trigger vocabulary), then audit in slices by screen — not top to bottom, since `Voice.kt` is grouped by key. Produce a findings list first; fix in a second commit. ~105 em dashes exist today (18 Serious, 36 Goth, 51 Quirky); most convert to a period or comma, but Goth/Quirky use them ~2–3x more often as a genuine mid-sentence pivot, so each needs a per-string call rather than a mechanical substitution.

**Tests** — `VoiceTest` already checks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. Add mechanical invariants during the audit: vocabulary casing, no gamification vocabulary (spec §4), length caps on tab/button labels, no double spaces or trailing whitespace. Confirm `androidTest` references `PlainVoice` by constant everywhere, not literal, before starting.

**Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

## Standalone

No cross-dependencies — pick by appetite. Grouped by area below; items are identified by title or branch, not a number.

### Replace the share card's old trend arrow with real Trends findings

*Branch: `feat/insights-trends-share` · Complexity: S–M · Priority: Low · Area: Share*

🎨 **Design decision** — whether Trends belongs on Square once B1 settles its fixed section list, and how many findings a card has room for. Also whether `WENT_QUIET` belongs on a share card at all — it's a live-state read on the Case right now, not a historical shift like the other detectors, and may read oddly out of context. If kept, consider a generation timestamp on the card, since a `WENT_QUIET` sentence is only true at the moment the card was made.

The Insights tab already folded its Trend arrow card into the Trends section — `TrendFindingKind.FREQUENCY_SHIFT` is one more finding in `stats.trends`. The Share card still uses the old path: `ShareCardState.trend`/`TrendDisplay`/`ShareCardTemplate.kt`'s `MiniTrendSection`, sourced from `StatsSections.trend`. This item swaps Share over to real Trends findings and retires the old path.

Trends should slot into the existing Insight Share flow (`ShareViewModel.kt` → `SharePreviewScreen.kt` → `ShareCardTemplate.kt`) as one more toggle gated on `stats.trends.isNotEmpty()`, same as `TagsCard`. Render each selected finding as sentence text only — no reliability tag, no evidence line — since a share card has no room for tap-revealed detail.

**Acceptance criteria**

- [ ] `availableSections` (`SharePreviewScreen.kt`) gains a Trends entry (replacing the old Trend entry, not adding alongside it), offered only when `stats.trends.isNotEmpty()`.
- [ ] `ShareCardTemplate.kt` renders the selected Trends findings as sentence-only text (no tag, no evidence line), respecting whatever per-card finding cap this item settles on.
- [ ] `MiniTrendSection`, `ShareCardState.trend`, `TrendDisplay`, and `StatsSections.trend` all removed — no code path still reads the old single-arrow shape once this ships.
- [ ] Voice ×3 for the new section-toggle label, if `insightsSectionLabelTrends` doesn't already read correctly in that context; `insightsSectionLabelTrend` (singular) and its now-orphaned Voice keys removed once `MiniTrendSection` no longer needs them.
- [ ] Confirmed against spec §13's "no notes/tags on share cards" rule: Trends sentences are descriptive stats like every other section already shown, not raw logged text, so no new exception needed.
- [ ] Tests: `ShareCardStateTest.kt`/`SharePreviewScreenTest.kt` coverage that the Trends toggle appears only when findings exist; `ShareCardTemplateTest.kt` coverage for its rendering; every existing test referencing the old Trend toggle/`MiniTrendSection` updated or removed.

**Plan** — mirror Tags' gating pattern. Swap the toggle and rendering to `stats.trends`, verify Share round-trips, then delete `MiniTrendSection`/`ShareCardState.trend`/`TrendDisplay`/`StatsSections.trend` and their dead Voice keys in the same change — not a follow-up, so the old and new paths never coexist.

**Tests** — see acceptance criteria; no new statistics, so no domain-level tests needed here.

### Share button: add a Log Share option alongside the existing Insight Share

*Branch: `feat/share-log-export` · Complexity: L · Priority: Medium · Area: Share*

🎨 **Design decision** — sort options, date-range UI, and column-selection UX need a ruling before implementation.

The existing share action (`CaseDetailScreen.kt:175-177` → `ShareViewModel.kt` → `SharePreviewScreen.kt`) becomes one of two options: "Insight Share" (unchanged) and a new "Log Share" that exports the Case's raw log data as a file — configurable sort order, date range, and toggleable columns (tags, notes, duration, intensity), each shown only when applicable to the Case. Column applicability keys off `CaseEntity.durationMode`/`intensityEnabled` (`CaseEntity.kt:16-17`), reusing `availableSections`'s existing gating pattern.

Distinct from **CSV export**: that's a bulk, all-cases export with no sort/date-range/column UI; this is single-Case and share-sheet-triggered. HODITH_SPEC §13's "no notes/tags on share cards" rule is specific to the image card and doesn't apply here — needs a note distinguishing the two once this ships.

**Acceptance criteria**

- [ ] A ruling on Log Share's output format (CSV/text attachment via Android share sheet is the likely default, consistent with the existing CSV export item's format).
- [ ] A ruling on sort options offered (e.g. date ascending/descending) and date-range picker UX.
- [ ] Column toggles for tags/notes/duration/intensity, each shown only when applicable to the Case (reusing `availableSections`-style gating against `CaseEntity.durationMode`/`intensityEnabled`).
- [ ] Share entry point presents both "Insight Share" and "Log Share" as distinct options (e.g. a chooser before `SharePreviewScreen`, or a new sibling screen).
- [ ] Voice ×3 for all new labels, toggles, and picker copy.
- [ ] HODITH_SPEC §13 updated to scope the "no notes/tags" rule to the image share card specifically, once Log Share exists.

**Plan** — settle the format/sort/date-range/column UX first (mock as a static prototype). Then: a new export path parallel to `ShareViewModel`/`SharePreviewScreen` producing the tabular file, reusing `availableSections`'s gating pattern for column applicability, plus a chooser between Insight Share and Log Share.

**Tests** — a unit test for the export-row-shaping logic (column gating by Case config, sort, date-range filtering); Compose coverage for the two-option share entry point and the Log Share configuration screen.

### App-icon handle butts directly against the lens ring with no clearance

*Branch: `fix/icon-handle-clearance` · Complexity: S · Priority: Low · Area: Bug*

In `app/src/main/res/drawable/ic_launcher_foreground.xml` the handle's inner edge (midpoint ~(62,62)) sits on the ring's outer stroke band (~63.7 along the diagonal).

**Acceptance criteria**

- [ ] The handle's two inner points (`58.818,65.182` and `65.182,58.818`) pushed outward along the (1,1) diagonal in `ic_launcher_foreground.xml`; mirrored in `ic_launcher_monochrome.xml`.
- [ ] Visible clearance between handle inner edge and ring outer stroke.
- [ ] Handle tip stays inside the 66dp adaptive-icon safe zone (shorten the handle or nudge the enclosing `group` scale if needed).
- [ ] Verified across densities, the Android 13+ themed/monochrome path, and the splash screen (which reuses the foreground).

**Plan** — push the handle's two inner points (`58.818,65.182` and `65.182,58.818`) outward along the (1,1) diagonal; mirror the change in `ic_launcher_monochrome.xml`. The handle tip is already near the 66dp adaptive-icon safe zone, so this may also mean shortening the handle or nudging the enclosing `group` scale (0.9).

**Tests** — none (Previews only, as with the icon-picker item). Verify across densities, the Android 13+ themed/monochrome path, and the splash screen.

### Log entry silently clamps a future start time to now

*Branch: `fix/future-start-time-clamp-notice` · Complexity: S · Priority: Medium · Area: Bug*

🎨 **Design decision** — clamp-and-notify vs. blocking the save outright.

Picking a future time on today's date isn't blocked. `LogDetailSheet.kt`'s date/time pickers clamp the value to `now` (`onConfirm`, lines 321 and 334), and `LogDetailViewModel.kt`'s `toEventEntity` clamps again as the authoritative floor (line 148, `coerceAtMost(now)`). Neither shows a message, so a saved event's start time can silently differ from what was entered.

**Acceptance criteria**

- [ ] A note is shown when a picked time gets clamped (snackbar or inline caption).
- [ ] New Voice key (e.g. `logSheetFutureTimeClampedNotice`) ×3.
- [ ] Same treatment applied to the `START_STOP` end-time clamp in `computeEndedAt`, if in scope.
- [ ] Ruling made: clamp-and-notify (current behavior, now explained) vs. block the save until the time is valid.

**Plan** — detect the clamp by comparing the picked value to `now` before save, either in the picker `onConfirm` handlers (`LogDetailSheet.kt`) or by having `toEventEntity`/`planSaveEvent` report whether it clamped, so the ViewModel can push a message into UI state.

**Tests** — a unit test asserting the clamp is reported; a Compose test for the note appearing.

### Big Picture: cross-case trend detection (design)

*Branch: `chore/big-picture-cross-case-trends-design` · Complexity: XL · Priority: Low · Area: Big Picture*

🎨 **Design decision** — a new engine and its statistical framework are a product call, not just an implementation detail. 🔍 **Investigation** — nothing here is spec'd enough to build yet.

Expands HODITH_SPEC §17's "Computed cross-case co-occurrence" entry — data plumbing (`observeActiveCases`, `observeActiveCaseEventDetails`, `observeActiveCaseEventTagNames`) is in place; the real cost is statistical-honesty UX. Every Insights card looks at one Case in isolation; this computes connections across them.

Candidate cross-Case detectors:

- **Lagged precedence** — for each B event, check whether an A event started within a lag window before it (3h/12h/24h/48h/72h), compare hit rate to baseline, run in reverse too; asymmetric lift suggests A leads (e.g. "late-night noise followed by a migraine the next day").
- **Suppression** — same computation, lift below 1 ("migraines are less common in the 48 hours after a workout").
- **Absence as a precursor** — test whether B is more likely when A's *current* gap exceeds A's own typical (75th-percentile) gap, not after A itself.
- **Dose-response** — bucket A-count in the prior window (0/1/2+) and look for a steady rise in B's probability, intensity, or duration; the strongest causal hint available from observational data, deserving a higher confidence tier.
- **Cross-case intensity/duration spillover** — does A's intensity/duration predict the severity of the next B?
- **Shared shifts** — run change-point detection per-Case; if two Cases shift within ~2 weeks of each other, surface it ("workouts dropped and arguments rose around the same time in March").

Architectural framework (applies to all six, and is the reusable piece other detector work should build on):

- **`Finding` interface** — every detector returns effect size, support count, a significance score, sentence-template parameters, and evidence event IDs (for the drill-down the app already has elsewhere).
- **Pipeline**: eligibility gating (same shape as existing card-visibility gates) → significance via circular shift (shift A's timeline by random offsets, ~200 runs, in whole-week steps to preserve weekday structure) → multiple-comparisons control (Benjamini-Hochberg across all pairs×lags from one run, plus a minimum lift ≥1.5/≤0.67 and support ≥5 hits) → stability check (effect holds in both history halves) → tiering (Hint → passed significance; Pattern → also stable; Strong connection → also directional with dose-response) → persist/dedupe (store `firstSeenAt` and last effect size, re-surface only on tier change, let users dismiss or mark "makes sense" and use that to rank future findings).
- **Wording rules** — "often follows," "tends to come before," "less common after"; never "causes." The honest route to causation here is directional + dose-response + stable → offer a Hunch → confirm with future data (see the Hunch extensions item).
- **Run cadence** — cheap within-Case work on event insert/edit; expensive cross-Case shift tests in a daily background job.

Two prerequisites carried in from the raw idea list:

- **Tags are global** — "home" is used by Coffee and Workout both, so any tag-aware detector must key on `(caseId, tagName)`, not tag name alone.
- **No timezone stored** — resolved by `fix/event-timezone-offset` (events now carry their own captured UTC offset); any same-day/lag/time-of-day detector here can build on it.
- **Logging lag / batch-logging exclusion** — `loggedAt - occurredAt` marks heavily backfilled events as fuzzy-timed; down-weight them in lag/time-of-day detectors, and exclude event pairs from different Cases logged within ~2 minutes of each other (batch logging creates fake co-occurrence).

**Acceptance criteria**

- [ ] A written architecture doc covering the `Finding` interface, the full pipeline, tiering, wording rules, and run cadence above.
- [ ] A keep/drop call on each of the six detectors, with the pair-count-at-alpha-scale (8 Cases → 56 ordered pairs × 5 lags = 280 tests) sanity-checked against the multiple-comparisons control.
- [ ] A ruling on where findings surface (a Big Picture section vs. a cross-Case Insights-adjacent screen).
- [ ] A testing strategy: known patterns planted in `DemoDataSeeder.kt` (e.g. noise → migraine within 24h at 3× lift; a refractory gap after migraines) with a shuffled-null-data check that no detector invents a finding that isn't there.
- [ ] `HODITH_SPEC.md` §17's "Computed cross-case co-occurrence" entry flagged for an update once any part of this is approved (not done in this item).
- [ ] Anything approved spun out as its own implementation item. No production code in this item.

**Plan** — write the architecture doc first, then rule detector-by-detector. A throwaway JVM spike for the circular-shift significance test specifically — the piece most likely to have a subtle bug (whole-week shifts, not arbitrary offsets).

**Tests** — none; detector-level tests land with each spun-out implementation item, following the planted-pattern strategy above.

### Big Picture: filter pill consistency pass (color-coding, empty-selection label, tag/case pill parity)

*Branch: `fix/big-picture-filter-pill-consistency` · Complexity: S–M · Priority: Medium · Area: Big Picture*

🎨 **Design decision** — the actual color choices per filter type need a call.

Issues reported against `ui/bigpicture/BigPictureGrid.kt`'s filter chips/pills:

- **Not color-coded by filter type.** In Plain/Intense, `CaseFilterChip` (lines 865-892) uses `secondaryContainer`, `TagFilterChip` (896-921) uses `tertiaryContainer`, and `YearFilterChip` (930-962, landed with the "Big Picture: year filter" item) uses `primaryContainer` — three distinct colors. The actual gap is **Bright**: `BrightCaseFilterChip` (1009-1022) and `BrightTagFilterChip` (1025-1036) call the shared `BrightChip` (974-1006) with the same `tint = MaterialTheme.colorScheme.primary`, and `YearFilterChip`'s own Bright branch does too — so Bright shows no color distinction across any of the three.
- **"0 of 5" should read "None" when nothing is selected.** The "Cases: N of M" format this note originally described has since shipped as "Cases: N" ("All" once fully selected, via `filterCountLabel`, lines 509-513). `filterCountLabel` still branches only on `selected == total` (→ `bigPictureFilterCountAll`); there's no `selected == 0` branch, so it falls through to the bare `"$selected"` (`Voice.kt`, `bigPictureFilterCount(selected: Int)`, not overridden per-voice) and reads "Cases: 0" instead of a "None" wording. Needs a `bigPictureFilterCountNone`-style key, following the same per-voice-override pattern `bigPictureFilterCountAll` already uses.
- **Tag pills don't match case pills' size/alignment.** `CaseFilterChip` renders a `Row` (icon + name `Text`s, `Arrangement.spacedBy(4.dp)`, `CenterVertically`) with padding on the `Row`; `TagFilterChip` renders a single bare `Text` with the same padding values but no `Row`/explicit vertical-centering container — same `CHIP_SHAPE`/padding constants, different measurement shape, which is the likely source of the visible height/alignment mismatch in the filter `FlowRow`s (lines 419, 438, 452) and `FilterLegendRow` (538-574).
- **Gets worse at larger font scale.** The `TagFilterChip`/`CaseFilterChip` layout mismatch above diverges further as text grows. `FilterLegendRow` (538-576) also has no divider or extra spacing between the Case-chip group and the Tag-chip group — only a uniform 6dp `spacedBy` — so at larger font/display scale the two groups read as one.

**Acceptance criteria**

- [ ] A ruling on the four chip colors (Cases/Tags/Year trigger chips, plus each dialog's own pills), applied consistently across Plain, Intense, and Bright.
- [ ] `BrightCaseFilterChip`/`BrightTagFilterChip`/`YearFilterChip`'s Bright branch use distinct tints instead of all defaulting to `colorScheme.primary`.
- [ ] `filterCountLabel` gains a `selected == 0` branch returning a new `bigPictureFilterCountNone` Voice key (Voice ×3) instead of falling through to a bare "0".
- [ ] `TagFilterChip` (and Bright's tag chip) restructured to match `CaseFilterChip`'s `Row`-based layout so both measure to the same height/alignment in a `FlowRow`.
- [ ] Verified side-by-side in the Cases/Tags/Year filter dialogs and in `FilterLegendRow` where Case and Tag chips can appear together, and at larger Android font-scale/display-size settings, not just default.

**Plan** — settle the color ruling first (affects Plain/Intense chips, Bright chips, and `YearFilterChip`'s own PLAIN/INTENSE branch, which already uses `primaryContainer` and may need to move once the ruling lands). Then: add the `bigPictureFilterCountNone` Voice key and wire it into `filterCountLabel`; restructure `TagFilterChip`/Bright tag chip onto `CaseFilterChip`'s `Row` layout for size/alignment parity.

**Tests** — `VoiceTest` coverage for the new key across all three voices; a Compose test asserting tag and case chips render at equal height in a shared `FlowRow`; existing Big Picture filter tests updated if any assert the old bare "0" label text.

### Notes mining for tag/Case suggestions

*Branch: `feat/notes-mining-suggestions` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision** — must read as an offer, never a nudge (spec §4's no-gamification stance applies directly to anything that reacts to how much a user logs or writes). One concrete UI option raised in testing: a dismissible note on Case Detail, as an alternative to a point-of-logging prompt.

Normalize event notes, count repeated phrases, and offer a tag when one repeats 3+ times ("Burnt beans again" → suggested tag). Flag notes that mention another Case's name or a recurring cause word ("wine," "screen time") and offer "want to track this as its own Case?" Turns free text into testable data for the cross-case detectors (feeds the Big Picture item) without being a detector itself.

**Acceptance criteria**

- [ ] Phrase-repetition detection (≥3 occurrences) surfaces a tag suggestion at the point of logging, not a background nag.
- [ ] Cross-Case-name / cause-word mentions surface a "track this as its own Case?" offer, dismissible with no repeat nagging on decline.
- [ ] Confirmed against spec §4: no streak-like framing, no "you keep mentioning X" scolding tone — purely an offer.
- [ ] Voice ×3 for the suggestion/offer copy.

**Plan** — a simple normalize-and-count pass over `EventEntity.note` at logging time (no ML), feeding results into the existing suggestion filtering (`TagInput.kt:26` `filterTagSuggestions`) for the tag case; a new lightweight prompt for the Case-suggestion case.

**Tests** — unit tests for the phrase-repetition threshold and cause-word matching; Compose coverage for the suggestion/offer UI appearing and being dismissible.

### Hunch extensions: confidence projection, belief drift, perception gap

*Branch: `feat/hunch-extensions` · Complexity: M · Priority: Low · Area: Hunch*

🎨 **Design decision** — copy tone for each extension needs settling (avoid anything reading as pressure toward a particular verdict).

Three independent extensions to the Hunch feature:

- **Time-to-confidence projection** — "At the current rate, CONFIDENT in about 9 days," projected off `confidenceTierFor(observationCount: Int, windowDays: Long)` (`VerdictEngine.kt:132-140`)'s existing `PRELIMINARY_MIN_EVENTS`/`CONFIDENT_MIN_EVENTS` and `*_MIN_DAYS` constants: given the Case's current event rate, solve for the day both thresholds clear.
- **Belief drift across superseded Hunches** — when a Case has more than one Hunch over time on the same question (e.g. coffee: 3/day, then 2/day), say so: "Your expectation dropped, and the data agrees." No new query needed — `HunchDao.observeHunchHistory(caseId)` (`HunchDao.kt:27-28`) already returns every Hunch for a Case ordered `createdAt DESC`, and each resolved one already carries a frozen verdict snapshot (`HunchEntity`'s `resolved*` columns, `Verdict.kt`'s `withResolvedVerdictSnapshot`). The just-shipped resolved-Hunch list (`feat/resolved-hunch-list-redesign` — `CaseDetailScreen.kt`/`HunchTabState.kt`, 15-item retention cap via `HunchDao.deleteResolvedHunchesBeyondLimit`) is the natural surface for a belief-drift sentence between consecutive entries.
- **Perception-gap framing for `JUST_CURIOUS`** — frame the result as how it felt vs. what the data shows, rather than a verdict against an expectation.

A fourth extension — "when a cross-Case finding appears, offer to turn it into a Hunch" — is **blocked on** the Big Picture cross-case trend detection item shipping first, since it depends on that item's findings existing at all.

**Acceptance criteria**

- [ ] Time-to-confidence projection implemented as a `VerdictEngine` extension over `confidenceTierFor`'s existing thresholds, shown only where a Hunch is already `NO_VERDICT`→`PRELIMINARY` or `PRELIMINARY`→`CONFIDENT` trending.
- [ ] Belief-drift sentence shown when `observeHunchHistory` returns more than one Hunch on a comparable question, comparing consecutive resolved snapshots' `resolvedExpectedRate`/`resolvedObservedRate`.
- [ ] Perception-gap framing applied specifically to `HunchDirection.JUST_CURIOUS`.
- [ ] Voice ×3 for all new copy.
- [ ] Fourth extension noted as blocked, not attempted, until the Big Picture item lands.

**Plan** — each extension is a `VerdictEngine`/Hunch-UI addition, shipped independently. Belief drift extends the existing resolved-Hunch history UI rather than building new plumbing.

**Tests** — `VerdictEngineTest` coverage for the projection math and belief-drift comparison over a fixed `observeHunchHistory` fixture; Compose coverage for the perception-gap framing on `JUST_CURIOUS` Hunches.

### Trigger: suggested threshold from historical percentile + backtest preview

*Branch: `feat/trigger-threshold-suggestions` · Complexity: S–M · Priority: Low · Area: Hunch*

Suggest a `SILENT_FOR` threshold from the Case's 90th-percentile historical gap. `InsightsEngine.computeGapStats` (`InsightsEngine.kt:63-99`) builds the gap list; this item adds a percentile helper over it (none exists today). When editing a trigger, show "would have fired N times in the last year" by replaying `evaluateAtLeast`/`evaluateSilentFor` (`TriggerEngine.kt:36-55`, both pure functions of `now`) over the past year's events — a historical loop, no new evaluation logic. Unrelated to HODITH_SPEC §17's parked "Hunch/Trigger relationship" item (the `AT_LEAST`/Hunch overlap question) — this is purely threshold-tuning UX.

**Acceptance criteria**

- [ ] A percentile helper over `computeGapStats`'s gap list; `SILENT_FOR` trigger creation defaults its threshold suggestion to the Case's 90th-percentile result.
- [ ] Trigger edit screen shows a historical-replay count ("would have fired N times in the last year") for the currently-entered threshold, for both `AT_LEAST` and `SILENT_FOR`, by replaying `evaluateAtLeast`/`evaluateSilentFor` over the past year's events.
- [ ] Voice ×3 for the suggestion and replay-count copy.

**Plan** — add the percentile helper first (small, testable in isolation); then a `domain/` function that walks a Case's event history day-by-day (or event-by-event) calling the existing `evaluateAtLeast`/`evaluateSilentFor` with a historical `now`, counting rising-edge fires.

**Tests** — unit tests for the percentile helper against a known gap list; a backtest-count test against a fixture event sequence with known fire points for both trigger kinds; Compose coverage for both appearing on the trigger edit screen.

### Audit the hosted privacy policy and Play data-safety form

*Branch: none — external content, not a code change · Complexity: XS · Priority: Medium · Area: Settings*

🌐 **External action** — both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

**Acceptance criteria**

- [ ] Hosted policy read against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and updated wherever it still claims otherwise.
- [ ] Play data-safety answers reconciled with the same copy (once a listing exists).

**Plan** — read both against the new About copy and update wherever they still claim otherwise.

### Intense/Bright theme: exploratory testing pass

*Branch: `chore/intense-bright-theme-audit` · Complexity: S–M · Priority: Low · Area: Settings*

🔍 **Investigation** — a review pass, not a known fix.

Exploratory pass over the Intense and Bright themes (`Color.kt`, `GlowDecoration.kt`, `CardDecorationStyle.kt`, `BigPictureDecoration.kt`, `ShareCardDecoration.kt`) for minor redesigns. Restyle-only — visual refinement of what exists, not new features a mockup might suggest.

**Acceptance criteria**

- [ ] A written pass over both themes across the main screens (Home, Case Detail/Insights, Big Picture, Share, Settings) noting legibility/contrast/consistency issues.
- [ ] A shortlist of proposed tweaks, restyle-only, each with a keep/drop call.
- [ ] Approved tweaks spun out as their own follow-up items.

**Plan** — audit pass first, no code; produce a findings list. Implementation only for approved items, spun out separately.

**Tests** — none for the audit itself.

### CSV export of case/event data

*Branch: `feat/csv-export` · Complexity: S · Priority: Medium · Area: Settings*

Scoped in HODITH_SPEC §17: CSV export alongside the existing JSON export. JSON stays canonical for import since a flattened tabular format doesn't round-trip cleanly, so CSV is export-only. No spec change needed, just implementation.

**Acceptance criteria**

- [ ] A new CSV writer alongside the existing `BackupFileWriter` (`data/backup/`).
- [ ] A Settings row for CSV export, alongside the existing JSON export/import row.
- [ ] Voice ×3 for the new row and any share/save-location prompts.
- [ ] Confirmed export-only — no CSV import path.

**Plan** — implement per §17 as already scoped: new writer, Settings row, Voice strings.

**Tests** — a unit test for the CSV writer's output shape; `SettingsScreenTest` coverage for the new row/action.

**Concern** — none; per the spec's own note, this is the most self-contained item here.

## Deferred

### D1 · Big Picture's grid query, windowed or not

*Branch: `refactor/big-picture-windowed-query` (if taken) · Complexity: S–M · Priority: Low · Area: Performance*

🔍 **Investigation, deferred** — `BigPictureViewModel` now reads two flat projections (`EventDao.observeActiveCaseEventDetails()`, `TagDao.observeActiveCaseEventTagNames()`) instead of the original `@Transaction @Relation` cascade — removing the chunked `IN (...)` sub-fetches and full-row hydration that were the measured cost. A JVM probe confirmed Kotlin-side mapping is cheap at S6 scale. Undecided: whether the two queries' SQL-scan cost holds up at that scale under a write burst.

**Deferred rather than pursued next** — closing this needs a synthetic S6-scale DB probe with no real usage behind it: speculative complexity. Real alpha usage is a better trigger than a cautionary probe.

**Acceptance criteria**

- [ ] Alpha usage (or a deliberate decision to probe synthetically instead) confirms whether the two flat projections' SQL scans — particularly the tag-attachment join — hold up under a logging burst at real-world scale. This is the decision gate for everything below.
- [ ] If not: a `SELECT DISTINCT` per-case tag-vocabulary query sourcing `allTagNames` directly, rather than flattening every event's tags client-side.
- [ ] If still needed after that: `observeActiveCaseEventDetails` bounded to a loaded month range (half-open bounds, mirroring `eventsInWindow`), extended in chunks as the grid nears the top of its loaded range, well before the user hits the edge. The tag projection stays live-and-windowed alongside it rather than moving to on-demand fetch, unless that's also still too hot.
- [ ] Month-picker quick-jump (§9) extends the loaded range to cover the picked month before scrolling, rather than landing in an unpopulated region.

**Plan** — revisit once alpha usage says whether Big Picture feels slow at scale; only then run the probe, and only build the criteria its result actually calls for, cheapest lever first.

**Tests** — if windowing is taken: `bigPictureUiState` over a windowed event list; a DAO test for the month-range query; Big Picture Compose tests stay green.

**Concern** — scroll-triggered range extension (if taken) must not stutter or flash empty cells on a fast scroll to a distant month.

### D2 · New-case tag suggestions have no history to draw from

*Branch: none — deferred, no fix prescribed · Complexity: S · Priority: Low · Area: Bug*

🔍 **Investigation, deferred**

`TagInput.kt`'s suggestion filtering and case-insensitive dedup (`filterTagSuggestions`) are already correct; every call site (`LogDetailScreenViewModel`, `HomeViewModel`, `WidgetLogSheetViewModel`) sources its candidate list from `repository.observeTagsForCase(caseId)`. A brand-new Case's per-case tag list is empty on its very first tag entry, so nothing suggests, even when the same tag name already exists on other Cases — which is how testers ended up with near-duplicate spellings. Cross-case suggestions were considered and explicitly ruled out, so no fix is prescribed here.

**Acceptance criteria**

- [ ] Revisit with a concrete proposal once one exists — this item exists to hold the observation, not to specify a solution.

**Plan** — none yet; deferred pending a future proposal that doesn't widen tag suggestions across Cases.

**Tests** — none until a proposal is approved.

### D3 · Investigate app capacity at multi-year logging scale

*Branch: none yet — investigation first · Complexity: S (investigation) · Priority: Medium · Area: Performance*

🔍 **Investigation**

Raises the same question **D1** is deferred pending — app capacity for years of records — but broader than D1's Big Picture-specific scope. `EventDao.observeEventsWithTagsForCase` (unbounded) backs every Insights/Hunch stats computation (rhythm, frequency-over-time, trend, duration averages) with no row-count limit; only the Log tab got paged querying (`feat/log-tab-paged-query`). May itself be D1's "real alpha usage" trigger — resolve together with D1 rather than as a separate track.

**Acceptance criteria**

- [ ] A synthetic or real multi-year dataset used to measure current behavior of the unbounded per-case stats query (load time, memory) at a defined scale (e.g. matching D1's S6 reference point).
- [ ] A stated current capacity (rows/years before a defined threshold degrades).
- [ ] A ruling on whether this satisfies D1's alpha-usage gate, supersedes it, or should stay a separate track.
- [ ] If a guardrail is warranted: a shortlist of options (windowed stats queries, a soft in-app warning at N events, etc.) with a keep/drop call each, spun out as their own item(s).

**Plan** — probe first, no production code in this item; read alongside D1 before deciding investigation scope, to avoid running two parallel capacity investigations.

**Tests** — none until a follow-up item lands.

### D4 · No repository-level test coverage for the notification-eval scheduling side effect

*Branch: none yet — needs a reusable test double designed first · Complexity: S–M · Priority: Low · Area: Repo*

`RoomHodithRepository.deleteEventsOlderThan` fetches affected Case ids *before* deleting, then deletes, then calls `evaluateNotificationsForCase` per Case — the ordering isn't pinned by any test. Wider gap: `evaluateNotificationsForCase` → `NotificationEvalScheduler.schedule()` is untested at the repository level for every call site (`insertEvent`, `updateEvent`, `deleteEvent`, `deleteEventById`, not just `deleteEventsOlderThan`). `RoomHodithRepositoryBackupTest.kt` already documents the workaround: it inserts via `db.eventDao().insert(...)` directly to avoid triggering the wrapper's notification side effect against its intentionally-throwing `NotificationEvaluator` stand-in.

The scheduler/evaluator chain itself is testable — `NotificationEvalSchedulerTest` (JVM) proves the full path with `FakeHodithRepository`/`FakeSettingsRepository`/`FakeClock`/`FakeNotifier`. Missing: an androidTest equivalent against a real `RoomHodithRepository`/`HodithDatabase`, without triggering `unusedScheduler()`'s deliberate error or routing around the wrapper methods. `FakeNotifier` also isn't reachable from `androidTest` — it's in `src/test`, a separate source set.

Not blocking — every affected path self-heals within ~6 hours via `NotificationEvalWorker`'s periodic `evaluateAll` sweep. Coverage gap, not a correctness risk.

**Acceptance criteria**

- [ ] A reusable androidTest double/helper for the notification-eval side effect — real `NotificationEvalScheduler` + `NotificationEvaluator` wired to the `RoomHodithRepository` under test, with a `FakeNotifier`-equivalent double it can actually read from (moved to a shared source set, or reimplemented for `androidTest`).
- [ ] `RoomHodithRepository.deleteEventsOlderThan`'s affected-Case-id-before-delete ordering pinned by a test using it — the concrete bug that prompted this item.
- [ ] The same coverage extended to `insertEvent`/`updateEvent`/`deleteEvent`/`deleteEventById`'s `evaluateNotificationsForCase` call, currently untested at the repository level.
- [ ] `RoomHodithRepositoryBackupTest.kt`'s raw-DAO insert workaround revisited once the double exists — it could go back to calling `repository.insertEvent(...)` directly instead of bypassing the wrapper, if that reads more naturally with the new double in place.

**Plan** — mirror `NotificationEvalSchedulerTest`'s shape but swap in the real `RoomHodithRepository`/in-memory `HodithDatabase`, matching `RoomHodithRepositoryLogEventsTest`'s setup. Settle `FakeNotifier`'s reachability first (shared source set vs. `androidTest`-local reimplementation).

**Tests** — this item's entire scope is new tests; see acceptance criteria above.

**Concern** — none blocking. Trigger CRUD doesn't call `evaluateNotificationsForCase` today, unlike Event CRUD — noticed in passing, not evaluated here as correct or a bug.

### D5 · Detector: cycles and seasonality (autocorrelation + month-of-year)

*Branch: none yet — deferred, scope narrowed · Complexity: L · Priority: Low · Area: Insights*

🔍 **Investigation, deferred** · 🎨 **Design decision**

Originally scoped three sub-features: autocorrelation for weekly/~28-day cycles, a month-of-year comparison (needs 1+ years of data), and a weekday-vs-weekend fallback. The fallback shipped on its own (`HODITH_SPEC.md` §10). The other two are deferred: autocorrelation is a genuinely new technique (not a reuse of the existing bucket-share/label-shuffle/timeline-shuffle machinery) and needs the most data of any detector here to fire reliably.

**Deferred rather than pursued next** — building this before knowing whether Cases run long enough to show real weekly/monthly structure is speculative complexity. Real alpha usage (Cases with a year-plus of history) is a better trigger.

**Acceptance criteria**

- [ ] Revisit once alpha usage shows Cases commonly reach 1+ years of history (or a deliberate decision to build it sooner).
- [ ] Autocorrelation method + lag set chosen and documented (weekly ~7-day, ~28-day, and any others).
- [ ] Weekly/~28-day cycle detection gated by a minimum span; month-of-year comparison gated on ≥1 year of data.
- [ ] Voice ×3 for the new sentence template(s).
- [ ] Tests: a planted weekly cycle, a planted no-cycle null.
- [ ] `HODITH_SPEC.md` §10 gains one line per kept signal, or a rationale note here for any dropped.

**Plan** — none yet — the weekday-vs-weekend fallback already covers the cheapest, most useful signal of the original three.

**Tests** — none until picked back up.

## Blocked

### BL1 · Rate the App is still a placeholder row

*Branch: `feat/rate-app-play-link` · Complexity: S · Priority: Blocked — do it in release prep · Area: Settings*

🌐 **External action** — genuinely gated on a Play Store listing existing. 🎨 **Design decision** — In-App Review would add Google Play Services to a zero-network app; that's a positioning call. Steer: deep link.

The row shows a "coming soon" snackbar — needs a real destination once there's a Play Store listing.

**Acceptance criteria**

- [ ] Implemented in the release-prep branch, not as standalone work.
- [ ] `market://details?id=…` intent with an `https://play.google.com/…` fallback (recommended over the In-App Review API).
- [ ] `SettingsScreenTest` changes from asserting the coming-soon snackbar to asserting the intent launches (Espresso `Intents`).
- [ ] The Bright plank Preview's no-op `onClick` left as-is (not a second call site).

**Plan** — blocked on the listing existing; belongs in the release-prep branch. Two options: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review adds a Google Play Services dependency to an app that ships none and positions itself as "no network".

**Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar — change it to assert the intent launches (Espresso `Intents`). The row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`; no change needed, don't mistake it for a second call site.

**Concern** — In-App Review is quota-limited and no-ops silently once hit, making manual verification unreliable. The deep link is trivially verifiable.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
