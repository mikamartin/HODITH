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

## fix/segmented-choice-row-modifier-default

**Scope:** PROGRESS.md's "SegmentedChoiceRow's `modifier` parameter defaults to more than `Modifier`" item, the `ModifierParameter` lint warning deferred from the fix/log-share-controls pass.

**Found & fixed:**
- `modifier` now defaults to plain `Modifier`. The row's own `fillMaxWidth()` and 8dp top gap moved into the function body, gated on `stretchToFill` and applied before the caller's modifier, so a caller's modifier (test tag, semantics) adds to the layout instead of replacing it.
- KDoc's reference to the Log tab's inline Sort control removed. That control is now a filter chip that opens a dialog and no longer uses this composable; the PROGRESS item's Log-tab re-check was stale for the same reason.
- Walked the call sites: none passed `modifier` or `stretchToFill = false`, so no call site renders differently.
- Added `SegmentedChoiceRowTest` (instrumented): a caller's modifier keeps the full-width layout and 8dp top gap, and `stretchToFill = false` drops both. Mutation-checked: restoring the old default made both caller-modifier tests fail.

**Considered and declined:**
- Removing the `stretchToFill = false` path. It has no callers today, but it still drives the inline layout and the Bright segment padding default. Dropping it is a separate API change, so it stays.

**Deferred:** nothing deferred.

**Docs updated:** PROGRESS.md item struck; TESTING.md shared-components row names the new test. No SPEC changes.

**Verified:** `ktlintCheck`, `lintDebug` ("No issues found."), `test`, `assembleDebug`, all green, run sequentially. On the emulator: `SegmentedChoiceRowTest` and `SettingsScreenTest` pass, 36/36. A broader run of the seven UI classes that host these call sites (Case Edit, Case Detail, Insights, Watches, both share previews) aborted at 29 of 201 when the emulator crashed, so those were not covered by this pass. One `SettingsScreenTest` case failed on a renderer-finalizer timeout during that run and passed on rerun.

---

## fix/log-share-controls

**Scope:** PROGRESS.md's "Log Share: drop the format toggle and add Name on card" item. Log Share had a Story/Square toggle that only added a 1:1 height floor under a list that already sizes to its rows, and no way to rename the card, even though its rows are the most personal data the app shares.

**Found & fixed:**
- Checklist walked against the full diff. The format choice is gone from `LogShareSelection`, `LogShareViewModel` and the screen's callbacks. `format` moved off the shared `ShareCardData` interface onto `ShareCardData.Insights` only, since a Log card has no shape to choose. The Square min-height check in `ShareCardTemplate` now keys off Insights, and its modifier was hoisted into a named local so the existing modifier chain keeps its shape.
- Name on card: `LogShareSelection.displayNameOverride`, set by `LogShareViewModel.setDisplayNameOverride`, which mirrors Insight Share's `setDisplayNameOverride` (same 60-char cap, blank resets to the Case's name). The override reaches `logShareCardState`'s `displayName` and never touches the Case.
- Controls now read name field, date range, fields, sort, then the card, with the sort control in the old toggle's slot. The sort control had its own "Include in card" heading, duplicating the fields section's, so it was removed and the selector is called directly.
- `LOG_SHARE_CARD_ENTRY_CAP` and `logShareCardState` KDoc no longer describe Square's floor as a reason for the cap.
- No new `Voice` keys. The name field reuses `shareNameFieldLabel`, and the sort control reuses its existing labels, so there was no three-voice copy to add.
- No `android.*` imports or clock reads in the domain code touched (`LogFilter.kt` is a KDoc-only change).
- Dead-code walk: the `ShareCardFormat` import left `LogSharePreviewScreen.kt` with it; ktlint's unused-import rule passes.

**Deferred:**
- Nothing deferred.

**Considered and declined:**
- Pinning `ShareCardData.Log.format` to `STORY` instead of removing it. Declined: a constant that every Log card carries is a field that can be misread, and removing it moves the Square check to where it applies.

**Verified:** `ktlintCheck`, `lintDebug`, `test` (1057/1057 JVM tests, zero failures, forced re-run), `assembleDebug`, and `connectedDebugAndroidTest` scoped to `LogSharePreviewScreenTest` and `ShareCardTemplateTest` (70/70 on the Pixel 8 API 36 emulator). The full instrumented suite was not run, at the user's request.

**Docs updated:** `HODITH_SPEC.md` §13 (Log Share paragraph and preview-screen row); `TESTING.md` (Log Share preview row); `PROGRESS.md` (item struck, and the Share-tabs item's sequencing note removed).

---

## feature/tag-combo-trends

**Scope:** PROGRESS.md's "common tag combos" idea, spiked last session (`domain/TagComboSpike.kt`/`TagComboSpikeTest.kt`, never committed) as two candidate findings — a descriptive closed-itemset miner and a permutation-significance test on top of it. This pass promotes only the first (common tag combos) to a real `TrendFindingKind.TAG_COMBO` detector; the second (combos beyond chance) stays deferred — the spike's own fixture showed a near-1 lift pair still clearing a raw `p < 0.05` cut, meaning it needs a descriptive floor before the permutation test runs, the same shape every other `Pattern`-tier detector already uses, not just a multiple-comparisons correction (logged as PROGRESS.md's D6).

**Found & fixed:**
- Checklist walked against the full diff. The spike's two files were deleted outright (their logic promoted into the same files their sibling detectors already live in — `Insights.kt`, `StatsEngine.kt`), satisfying the "throwaway prototype cleared out" item.
- No `android.*` imports or clock reads in the new `domain/` code (pure count-based itemset mining, no time dependency at all).
- `TAG_COMBO_MIN_SUPPORT_COUNT`/`TAG_COMBO_MAX_FINDINGS` are named domain constants, not inline magic numbers; the demo seeder's `TAG_COMBO_SHOWCASE_CHANCE_PERCENT` follows the same existing-showcase-constant pattern.
- New `Voice` keys (`insightsTagComboSentence`/`insightsTagComboEvidenceLabel`) landed in the interface and all three voices in this same pass; `VoiceTest`'s reflection coverage picked them up with no new test code needed.
- `TrendFinding.tagNames` (a new field, since every existing per-tag kind only carries a singular `tagName`) is documented in the same KDoc style as `weekday`/`timeOfDay`/`changePointDate`.
- Adding a 9th demo `CaseSeed` ("Skipped lunch") broke two existing `DemoDataSeederTest` assertions by count (`eight cases` → nine, `16` → `18` on double-seed) — both caught by the scoped test run and fixed, not just patched to pass.
- No new entity/column/migration needed — `TrendFinding` is computed live, never persisted, same as every other Trends finding.

**Deferred:**
- Finding 2 (combos beyond chance / significance test) — see Scope above and PROGRESS.md's new D6.

**Considered and declined:**
- Grafting the demo showcase onto an existing Case (Coffee) instead of adding a new one — Coffee's own test comment explicitly keeps it a "clean two-finding showcase," and every other detector got its own dedicated Case, so "Skipped lunch" followed that precedent instead.
- A tag-vocabulary size cap before mining — no existing detector limits input vocabulary (only output-finding counts), and PROGRESS.md's own D1/D3/D4 defer performance questions until real alpha usage exists rather than guessing upfront.

**Docs updated:** `HODITH_SPEC.md` §10 (new "Common tag combos" bullet); `TESTING.md` (Stats & visual data prep row); `PROGRESS.md` (new D6 deferred item for finding 2).

**Follow-up pass — coverage audit, instrumented gap, lint audit:**
- A dedicated coverage audit mutation-tested the new domain/seeder tests directly: broke the closed-itemset filter, the min-support floor boundary, the `TrendsEngine` wiring, and the demo seeder's `forcedCombo` routing one at a time, confirmed each break was caught by a specific test, then reverted. All four held.
- The original "no instrumented run needed ... matching the zero-instrumented-coverage precedent `TAG_SHARE_SHIFT`/`TAG_TIMING` already set" call above turned out wrong on inspection — `TAG_TIMING` already had rendering coverage in `InsightsTabTrendsCardTest`; only `TAG_SHARE_SHIFT` was actually uncovered, and `TAG_COMBO` was extending that same gap rather than matching settled precedent. Fixed by adding `trendsCard_rendersTagShareShiftSentence` and `trendsCard_rendersTagComboSentence` (separate `test:` commit) — each mutation-verified on a real device (wrong join separator, swapped percent args; both failed, then reverted) before landing.
- Same pass surfaced 4 pre-existing `lintDebug` warnings, unrelated to this feature: two `Uri.parse(x)` → `x.toUri()` swaps (`AboutScreen.kt`, `SettingsScreen.kt` — mechanically equivalent, no test surface since the real call sits in each `Route` composable's `context.startActivity` wiring, outside what `AboutScreenTest`/`SettingsScreenTest` exercise against the presentational `Screen`), and an obsolete `mipmap-anydpi-v26` → `mipmap-anydpi` rename (minSdk 31 already exceeds the API 26 the `-v26` qualifier was guarding). Fixed in a separate `chore:` commit. The fourth (`SegmentedChoiceRow`'s `modifier` parameter defaulting to more than plain `Modifier`) is a real behavior-affecting fix across four call sites, not a mechanical one — deferred to its own PROGRESS.md Standalone item rather than risked here.

**Verified:** `ktlintCheck` → `lintDebug` → `test` (scoped, then full suite) → `assembleDebug`, sequential, all green, rerun clean after the lint-fix commit too (a stale `packaged_res` cache briefly broke `lintDebug` on the mipmap rename — resolved by `./gradlew clean`, not a real issue). Instrumented: full-suite `connectedDebugAndroidTest` hit its usual mid-run emulator crash partway through (`INSTRUMENTATION_ABORTED`, consistent with prior entries' precedent); rather than keep re-running the whole 499-test suite, switched to batches scoped to what this branch actually touches. All green: `data`+`backup` (93/93), `notification` (13/13), `about`+`archivedcases` (14/14), `CaseEditScreenTest` (17/17), and the `casedetail` package including `InsightsTabTrendsCardTest`'s two new tests (100/100) — 237/237 total. `BigPictureScreenTest`, `CaseDetailScreenTest`, and the rest of `ui`/`widget` weren't rerun locally: none are touched by this diff, and CI covers full-suite regression anyway.

---

## feat/share-card-summary-beat

**Scope:** PROGRESS.md's B2, plus a round of Story card changes asked for alongside it. Story opens with the same summary hero as Square instead of the Reality beat, and its sections follow one order on the picker and the card (Gaps, Length, Start times, Intensity, Trends, Top tags) using the Square panel formatting. Overlap calls made: Frequency is dropped from Story (the hero's rate and pill carry it); went-quiet is left out of Story's Trends (the hero and the Gaps label say it), and the quiet label shows on Story only while Gaps is picked; Story with nothing picked is the hero alone. A new Top tags section lists the three busiest tags with counts, so the spec's "tags never on the card" rule became "only as Story's opt-in Top tags". The picker offers a row only when the Case has data for it. Layout tweaks from review: wider gap and larger cells in the Start times grid, fixed-size intensity squares, more inner padding on section cards, and the card on its own tinted stage below a divider.

**Found & fixed:**
- Checklist walked against the full diff. The Gaps, Duration and Intensity mini sections, `MiniFrequencySection` with its constants, `RealityBeat`, `ShareTopBeat.Reality`, `ShareCardData.Insights.frequency`, `shareFrequencyTitle` and the two Reality Voice keys were dead after the swap and are deleted; `MiniStatRow` stays because Top tags uses it.
- The Square panels lost their "Square" prefix (`GapsPanel`, `DurationPanel`, `IntensityPanel`) and their title keys became `shareGapsTitle` and `shareDurationTitle`, since both formats and the picker use them. The other `shareSquare*` hero keys keep their names.
- The picker's availability rules moved out of the screen into `availableShareSections` (pure, in `ShareCardState.kt`), and the Story trend filter into `storyTrendFindings`, both unit-tested. The top-tag cap is the named domain constant `SHARE_CARD_TOP_TAG_COUNT`.
- `LogSharePreviewScreen`'s KDoc pointed at the deleted `availableSections`; repointed. The spec, README and checklist statements about tags on the card were updated deliberately, with the checklist item reworded to the new rule.
- No `android.*` imports or clock reads in `domain/`, no inline strings (`shareTopTagsTitle` is a structural Voice key like the other share labels), no untracked prototype files, no new deprecation warnings.

**Deferred:** nothing deferred.

**Considered and declined:**
- Renaming the remaining `shareSquare*` hero keys (observed line, trend pill, quiet label, event noun, intensity average) to format-neutral names. They are voiced strings queued for B3's phrasing audit, and a rename now would churn every test that reads them for no behaviour change.
- A mockup or Compose Preview step: the Square implementation was the reference, and the human checked the result on a device between rounds.

**Docs updated:** SPEC §13 and the Share preview row; TESTING (share card assembly, Share preview); README and CLEANUP_CHECKLIST (tags rule); PROGRESS (B2 struck, B3's key list extended).

**Verified:** `ktlintCheck`, `lintDebug`, `test` and `assembleDebug` run sequentially, all green; `compileDebugAndroidTestKotlin` clean. Not run: the instrumented share tests (`ShareCardTemplateTest`, `SharePreviewScreenTest`) need a device.

---

## feat/square-share-card-preset

**Scope:** PROGRESS.md's B1. Square share cards become a fixed preset built from the Case's own settings and how much data exists, with no section picker (Story keeps its picker). The design was settled in a three-voice prototype first (hero with a last-30-day rate and trend pill, Gaps, then Duration/Intensity or Rhythm by Case settings, footer with date and time). Judgment calls made along the way: the shared `formatIntensity` now drops a trailing `.0` (so the Insights tab changes too, rather than a share-only formatter); the hero is built as its own `ShareTopBeat.Summary` so B2 can place it on Story without rework; the quiet label reads the existing went-quiet Trends finding instead of adding a second quiet detector.

**Found & fixed:**
- New domain pieces: `GapStats.shortestGapDays`, `DurationStats.shortestMinutes`, and `HeroRate.kt` (rate unit selection by exact integer comparison, 5-event/14-day eligibility reusing the verdict's preliminary constants, the 30-vs-30 basis). Pure Kotlin, no `android.*` imports (checked by grep), counts passed in rather than a clock read.
- The `/day` `/week` `/month` unit and the `<1` marker are structural Voice keys; the event noun, trend pill and quiet wording are voiced. All new keys landed in the interface and all three voices.
- The intensity distribution squares lived inline in `MiniIntensitySection`; extracted to `IntensityDistributionRow` and shared with the Square panel. `PanelHeaderRow` removed the title-plus-note row repeated in two panels.
- Existing `ShareCardStateTest` cases that exercised the section picker through `SQUARE` were moved to `STORY`, which is where selection now applies; two instrumented Trends tests now expect `2` and `4` where `formatIntensity` used to print `2.0` and `4.0`. Several `ShareCardTemplateTest` cases that built Square from Story's sections now use a Square fixture, and Trends tests moved to Story.
- Insight Share's screen title now reads "Share Insights" through a new structural `shareInsightScreenTitle` (the top bar had borrowed the share button's label, `shareOpenDescription`); the Square tab is listed first and is the default format.
- A second walk of the checklist against the full branch diff, after the coverage commit: the Square summary-beat constants had been inserted between the two `MINI_FREQUENCY_*` constants in `ShareCardTemplate.kt`; moved the pair back together. Nothing else found: no `android.*` imports or `currentTimeMillis` in `domain/`, no inline strings, no remaining mockup references, no new deprecation warnings, and the log holds exactly 5 entries.
- `docs/mockups/share-cards-prototype.html` and its `ShareCardDecoration.kt` KDoc pointer removed (the item's last live reference). The prototype artifact itself lives outside the repo.

**Deferred:** nothing deferred.

**Considered and declined:**
- The trend pill's direction is a drawn triangle plus the prior rate, with no spoken up/down/same description. The card is shared as an image, and in-app the figure beside it states the prior value; spoken direction would need three more Voice keys per voice for no sighted-user gain.
- The triangle geometry fractions in `TrendTriangle` stay inline; they are drawing constants for one shape, not product constants.
- `previewData` still takes a format although every caller now passes Story; left as is since it mirrors `previewSquareData`'s role for the other format.

**Docs updated:** SPEC §13 and the §14 Share preview row; TESTING (stats, time formatting, Voice, share assembly, share preview rows); PROGRESS (B1 struck, B2/B3 reworded for what shipped).

**Verified:** `ktlintCheck`, `lintDebug`, `test` (1009 unit tests), `assembleDebug` run sequentially, all green; `compileDebugAndroidTestKotlin` clean. Not run: the instrumented share tests (`ShareCardTemplateTest`, `SharePreviewScreenTest`, `InsightsTabTrendsCardTest`) need a device, and the light/dark visual pass of the three Square cards is the human's.

---
