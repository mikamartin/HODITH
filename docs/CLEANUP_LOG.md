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

**Verified:** `ktlintCheck` → `lintDebug` → `test` (scoped to the 4 changed/new test classes, then the full suite) → `assembleDebug`, sequential, all green. No instrumented run needed — no production UI/Compose code touched beyond two `when`-branch additions that only route to already-tested generic rendering, matching the zero-instrumented-coverage precedent `TAG_SHARE_SHIFT`/`TAG_TIMING` already set.

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

## feat/notifications

**Scope:** the Watches rework (Story N, N2): Trigger renamed to Notification and moved into a Case Detail bell tab, Often notifications moved onto span-overlap comparison math, then a second rename to **Watch** (avoiding a collision with `android.app.Notification`) with a redesigned two-zone card and editor. This pass also closes the follow-ups: spec and docs sweep, unit and instrumented coverage for the redesign, a Watch demo seed, and removing Story N from PROGRESS.md.

**Found & fixed:**
- `WatchEditorSheet`'s `windowPresetLabel` returned hardcoded English strings ("7 days", "2mo", "Quarter"). Replaced by the shared `Voice.watchesWindowPresetLabel(days)`, a structural default like `watchesQuietSuffix`.
- Editor KDoc still described the retired `TriggerCreationSheet` and a fixed 14/30-day preset; reworded. Two code comments pointing at PROGRESS.md's N2 rewritten to stand alone.
- The intensity toggle transition and highlight-fill rule lived inline in the composable; extracted to `minIntensityAfterToggle` / `isIntensityLevelHighlighted` and unit-tested, along with the window-preset helpers.
- `DemoDataSeeder` seeded no Watches (an original N2 criterion that had dropped out of PROGRESS.md). Added: a confident often Watch, a disabled quiet one, a days-active one on a duration Case, an intensity-filtered one, a quiet one on the long-silent Case, one Case with none, one Case with check-ins off. Each is run through the real Watch engine against the seeded events, so a condition that is already met is stored fired and loading demo data doesn't fire a burst.
- Docs sweep: HODITH_SPEC §2, §5, §8, §11, §14 and the stale `Trigger`/`SILENT_FOR` mentions; README, TESTING, MANUAL_TEST_PLAN, QA_AUDIT_RULES, CLEANUP_CHECKLIST and CLAUDE.md. Remaining `trigger` hits are deliberate (`FilterTriggerChip`, migration-history comments, "edge-triggered", plain verbs).

**Deferred:**
- Intensity-level circles in the editor are 36dp, under the 48dp touch target; fixing it re-spaces the row and needs a device look. Noted in the picker's KDoc.

**Considered and declined:**
- `WatchEditorSheet` is over 150 lines; left as is.
- Preset day counts in `windowDaysFor` stay literal; they are editor preset data scoped to that one function, not product constants.

**Docs updated:** SPEC §2, §5, §8, §11, §14; TESTING; MANUAL_TEST_PLAN; README; PROGRESS (Story N removed).

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green. Not run by hand: light/dark visual review of the new card and editor.

---

## chore/remove-hunch

**Scope:** PROGRESS.md's Story N, item N1 — "remove Hunch entirely, neutralise the pieces N2's Notifications reuses." Hunch overlapped with `AT_LEAST` Triggers (two similar "N per period" forms) and alpha feedback showed it wasn't landing. Four judgment calls were surfaced to the user before writing code: the verdict headline drops direction-aware framing entirely and reads band-only, since N2's Notifications only ever attach this math to the "Often" kind (no more too-often/not-enough duality); the neutral verdict card's "Resolve" button is dropped rather than kept as an inert hook; every kept `hunch*`-prefixed Voice key is renamed now (`expectation*`/`frequency*`/`expectedPer*`/`metric*`) rather than left for N2; and the count/period picker is bundled into one `FrequencyPicker` composable rather than exposed as separate primitives.

**Changes:**
- New `domain/Expectation.kt`: a pure `Expectation(count, per, metric, windowStart)` value. `VerdictEngine.kt`/`Verdict.kt` refactored onto it — `windowStartFor` deleted (window-start resolution is now the caller's job), `computeVerdict` drops `caseCreatedAt`, the `HunchEntity.withResolvedVerdictSnapshot`/`resolvedVerdictSnapshotOrNull` extensions deleted (no resolved/frozen concept survives). `domain/CheckIn.kt`'s Hunch-derived interval removed; `effectiveCheckInDays` collapses to toggle + Settings default.
- New `ui/common/ExpectationCards.kt` (`ExpectationCard`/`ExpectationEarlyCard`/`ExpectationVerdictCard`/`expectationProgressFraction`, moved from `CaseDetailScreen.kt`'s `HunchCard`/`HunchEarlyCard`/`HunchVerdictCard`/`HunchTabState.kt`'s `hunchProgressFraction`) and `ui/common/FrequencyPickers.kt` (`periodOptionsFor`/`coerceExpectedPer`/`expectedPerLabel`/`LabelledSection`, moved from `HunchCreationSheet.kt`, plus a new `FrequencyPicker` composable bundling the count+period stepper/row pairing that sheet inlined). Neither file is wired to a screen yet — both are dormant, kept for N2.
- Deleted: `HunchEntity`/`HunchDao`/`HunchDirection`/`ObservationWindow`, `HunchCreationSheet.kt`, `viewmodel/HunchTabState.kt`, `CaseDetailScreen.kt`'s Hunch tab and its cards, the Hunch paths in `HodithRepository`/`RoomHodithRepository`/`CaseDetailViewModel`/`FakeHodithRepository`, the share card's `HunchVsReality` beat (`ShareCardState.kt`/`ShareCardTemplate.kt`/`SharePreviewScreen.kt`/`ShareViewModel.kt` — `Reality` is now the only top beat, both formats), and every Hunch-only Voice key.
- `data/HodithDatabase.kt`: schema v11 → v12, `@DeleteTable(tableName = "hunches")` (`DropHunchesTable`), `AUTO_MIGRATION_COUNT` 5 → 6; `12.json` exported. `data/backup/BackupData.kt`: `BACKUP_SCHEMA_VERSION` 1 → 2, `hunches` field removed. `viewmodel/SettingsViewModel.kt`'s `performImport`: the version guard changed from `declaredVersion > BACKUP_SCHEMA_VERSION` to `!=` — a real bug fix, not cosmetic, since an older file would otherwise silently parse (Moshi ignores its now-unknown `hunches` key) instead of being rejected.
- `ui/voice/Voice.kt`: every Hunch-tab/creation-sheet/share-beat key deleted; kept keys renamed (`hunchChipLabel`→`expectationChipLabel` etc., dropping the `hunch` prefix throughout); `verdictHeadline`/`verdictHeadlineDaysActive` rewritten band-only (5 branches per voice, not 15) since `direction` no longer exists on `Expectation`.
- `Converters.kt`: `HunchDirection`/`ObservationWindow`/`ConfidenceTier`/`ComparisonBand` Room `TypeConverter`s removed (the first two because their types are gone; the latter two because `HunchEntity` was the only entity storing them, and nothing else does yet).
- Docs: `HODITH_SPEC.md` §1–3 rewritten (idea, vocabulary, principles — down to 6 from 7), §5's Hunch model and Case's direction-scoping paragraph removed, §7 removed outright, §8 reframed as dormant comparison math kept for Notifications, §11/§13/§14 updated. `README.md`'s idea/features/architecture sections and Mermaid diagram updated. `TESTING.md`'s Verdict engine/Check-in/Share-card/Export-import/Room-DAO/Compose-UI/Share-preview rows and the manual-journey list trimmed of dead Hunch coverage. `MANUAL_TEST_PLAN.md`, `CLAUDE.md`, `CLEANUP_CHECKLIST.md` each had one stale reference fixed. `PROGRESS.md`: N1 struck (removed entirely, not just checked off), the Story N intro rewritten to describe what shipped, B2/B3 items updated for the now-satisfied dependency and the now-stale key-count/em-dash tally.

**Checklist walk (against the full diff):**
- *Duplication* — no inline strings; every Voice key change (deletions, renames, the band-only copy rewrite) landed across the interface and all three voices in the same commit. `ExpectationCard`/`FrequencyPicker` are moves, not copies, of the code they replace.
- *Decoupling* — `VerdictEngine.kt`/`Verdict.kt`/`CheckIn.kt` confirmed zero `android.*` imports after the refactor (re-checked by grep, not assumed); all still take `now`/`windowStart` as plain `Long` params rather than reading the clock directly.
- *Complexity & pattern health* — `FrequencyPicker` has no callers yet (dormant, kept for N2 per the plan) rather than an accidental unused abstraction — considered and accepted, not flagged. `ExpectationCard`/`ExpectationEarlyCard`/`ExpectationVerdictCard` stayed under 40 lines each, matching the Hunch cards' own size before the move.
- *Dead code & hygiene* — `ktlintCheck` caught seven unused imports (`ColumnScope`, `rememberScrollState`, `verticalScroll`, `Button`, `LinearProgressIndicator`, `Dp`, `formatEventDate`) left behind in `CaseDetailScreen.kt` by the Hunch-tab deletion; all removed. Swept every doc comment referencing deleted Hunch types across the main source tree (`Color.kt`, `DeleteDataDialogs.kt`, `TriggersScreen.kt`, `InsightsTab.kt`, `LogDetailViewModel.kt`, `StatsEngine.kt`, `HodithRepository.kt`, `EventDao.kt`, `CaseDao.kt`, `ExpectedPer.kt`, `VerdictMetric.kt`, `CheckInDefaultInterval.kt`, `NumberStepper.kt`) rather than leaving stale cross-references. `git status` clean — only the intended files touched; all five new untracked files (`Expectation.kt`, `ExpectationCards.kt`, `FrequencyPickers.kt`, `FrequencyPickersTest.kt`, `12.json`) are the deliberate additions.
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — `Expectation.kt` sits in `domain/` next to `VerdictEngine.kt`/`Verdict.kt`; `ExpectationCards.kt`/`FrequencyPickers.kt` sit in `ui/common/` alongside the package's other shared composables; renamed Voice keys drop the `hunch` prefix consistently (`expectation*` for card copy, `frequency*`/`expectedPer*`/`metric*` for picker copy).
- *Hardcoded values* — none introduced; the confidence-tier/comparison-band constants moved with `VerdictEngine.kt` unchanged, still named constants in `domain/`.
- *Accessibility* — not applicable this pass — `ExpectationCards.kt`/`FrequencyPickers.kt` render nothing yet (no screen calls them), so there's nothing to verify in either theme until N2 wires them up.
- *Data model, migrations & privacy* — the three changes travelled together as the checklist requires: Room migration (`@DeleteTable`, v11→v12), `BACKUP_SCHEMA_VERSION` bump (1→2), and import validation (`BackupValidationResult`'s Hunch block removed) all in the same pass. `SchemaMigrationCoverageTest` passes with `AUTO_MIGRATION_COUNT` bumped to 6. FK cascade (Case → Event/Trigger) reconfirmed correct via `CaseDaoTest` now that `hunches` is out of the graph entirely.
- *Background work, widgets & notifications* — `NotificationEvaluator`'s only touch point was its check-in call site dropping the now-removed `hunch` argument; debounce/grouping/permission-timing logic itself is untouched.
- *Deprecated APIs* — none introduced by this diff (the one pre-existing Kotlin compiler warning, in `DataStoreSettingsRepository.kt`, belongs to a file this branch never touches).
- *Spec review* — walked the full spec end to end, not just the sections PROGRESS.md named — found and fixed §5's Case-direction paragraph and the Trigger-section verdict-storage paragraph, neither of which PROGRESS.md's acceptance criteria called out by number.
- *Tests* — see below.

**Tests:**
- `VerdictEngineTest` (42 cases) ported to `Expectation`: every case that varied `observationWindow`/`windowStartDate`/`caseCreatedAt` now computes the equivalent `windowStart` directly in the test body (absorbing what the deleted `windowStartFor` used to do) rather than being dropped; window filtering, both metrics, every tier boundary and band cutoff all still covered.
- `CheckInTest` (8 cases): Hunch-derived-interval cases deleted outright (no domain equivalent left to test); toggle/settings-default cases ported with the `hunch` argument simply removed.
- `FrequencyPickersTest` (5 cases, renamed/moved from `HunchCreationSheetLogicTest`): unchanged content — the helpers were already Hunch-free.
- `VoiceTest`: the two hand-written regression tests tied to deleted functions (`hunchHistoryRowOutcome`'s near-miss/fully-off guard, `sharePunchline`'s pronoun guard) replaced with a `verdictHeadline`-distinguishes-every-band test, since the reflection-based non-blank/no-duplicate-string tests already adapt automatically to the interface's new shape.
- `ShareCardStateTest`/`ShareViewModelTest`: every `HunchVsReality`/`showHunchVsReality` case deleted; a new test asserts `Reality` is the top beat for every `ShareCardFormat`.
- `SettingsViewModelTest`: added a regression test for the `performImport` version-guard fix — a v1 payload (carrying the now-retired `hunches` key) must be rejected as `UNSUPPORTED_VERSION`, not silently accepted.
- `BackupSerializerTest`/`BackupValidationResultTest`/`CsvBackupSerializerTest`/`CaseDetailViewModelTest`/`FakeHodithRepositoryTest`/`CaseDaoTest`/`RoomHodithRepositoryBackupTest`/`BackupImportIntegrationTest`/`RoomHodithRepositoryLogEventsTest`/`RoomHodithRepositoryNotificationEvalTest`: Hunch fixtures/cases/DAO-constructor references removed throughout.
- Deleted outright (no neutral successor): `HunchTabStateTest`, `HunchDaoTest`, `RoomHodithRepositoryHunchBackfillTest`.
- New: `DatabaseFreshInstallTest.migrationFrom11To12_dropsHunchesTable_andPreservesOtherTables` — seeds a `hunches` row plus a case/event row at v11, migrates to v12, asserts the table is gone from `sqlite_master` and the other rows survive.
- `CaseDetailScreenTest`: every Hunch-tab test deleted (the file's back half, ~300 lines); `SharePreviewScreenTest`/`ShareCardTemplateTest`: every Hunch-beat/toggle test deleted.

**Deferred:**
- The kept `ExpectationCard`/`ExpectationVerdictCard`/`ExpectationEarlyCard`/`FrequencyPicker` composables have no rendering test of their own yet — nothing calls them until N2 wires up the Notifications tab, so there's nothing to render.

**Second pass (checklist re-walk plus the deferred instrumented run):**
- `connectedDebugAndroidTest` run against every changed instrumented test class (`CaseDaoTest`, `DatabaseFreshInstallTest`, `RoomHodithRepositoryBackupTest`, `RoomHodithRepositoryLogEventsTest`, `RoomHodithRepositoryNotificationEvalTest`, `BackupImportIntegrationTest`, `CaseDetailInsightsTabTest`, `CaseDetailScreenTest`, `ShareCardTemplateTest`, `SharePreviewScreenTest`) on a connected emulator: 127/128 passed; `ShareCardTemplateTest.storySizesToContentAndIsShorterThanSquaresFloorForSparseContent` failed on an `ActivityScenario` teardown timeout (`ReferenceQueueDaemon`/`HardwareRenderer` crash after the test body completed, not an assertion failure) — reran in isolation and it passed cleanly, confirming emulator flakiness rather than a regression.
- Independent re-walk of the full checklist against the diff (fresh read, not from this entry) turned up two further findings, both fixed: `HODITH_SPEC.md` §8 (lines 133, 146) narrated Hunch's retirement inside prose that should read as present-state only — trimmed to state just what the comparison engine does now; `PROGRESS.md`'s N2 section cited "N1" three times with no N1 heading to point to (the Story N intro describes that work in prose, never as a numbered item) — replaced with direct references to `chore/remove-hunch` and `FrequencyPickers.kt`.

**Docs updated:** `HODITH_SPEC.md` (§1–3, §5, §7, §8, §11, §13, §14), `README.md` (idea, features, architecture, diagram), `TESTING.md` (Verdict engine, Check-in, Share card, Export/import, Room DAOs, Room migrations, Compose UI, Share preview, manual-journey list, date-picker deferral), `MANUAL_TEST_PLAN.md` (two check-in/import references), `CLAUDE.md` (vocabulary line, product-constants example), `CLEANUP_CHECKLIST.md` (cascade-relationship line), `PROGRESS.md` (N1 struck, Story N intro rewritten, B2/B3 updated, N1 self-references in N2 replaced with direct pointers).

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug` sequential, all green. `test` covers the full JVM suite: 907/907 passing across 58 classes, zero failures. `connectedDebugAndroidTest`: 128/128 passing (one flaky teardown failure, confirmed non-reproducing on rerun).

---
