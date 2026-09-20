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

## fix/log-detail-save-button-visibility

**Scope:** PROGRESS.md bug — the Save button in the log entry form (`LogDetailForm`, shared by the new-event `LogDetailSheet` and the edit-event `LogDetailScreen`) got pushed below the fold and unreachable while the tag field had focus and the keyboard was open.

**Found & fixed:**

- Root cause: `LogDetailForm`'s Save `Button` was the last item in the same single `Column` that had both `.verticalScroll(...)` and `.imePadding()` applied — when the IME opened, it shrank the viewport but nothing scrolled Save into view. Fix: split into an outer `Column` (padding + `imePadding()`) with two children — an inner `Modifier.weight(1f, fill = ...).verticalScroll(...)` `Column` holding the fields, and the Save button pinned after it, outside the scroll region.
- First pass hardcoded `fill = false` (keeps a short form, e.g. `DurationMode.NONE`, sizing the `ModalBottomSheet` compactly). The user caught a real problem in manual testing: with the keyboard open on a short form, `fill = false` let the field area shrink to just its content height, leaving Save floating right after it with dead space down to the keyboard instead of docked at the visible bottom edge. Fixed by making `fill` track `WindowInsets.isImeVisible` — `false` when the keyboard is closed (unchanged compact behavior), `true` while it's open (field area fills the IME-shrunk space, docking Save at its bottom).
- Added an unnecessary `import androidx.compose.foundation.layout.weight` that broke compilation (`weight` is a `ColumnScope` member, not a top-level import — Kotlin resolved the import to an unrelated internal `RowColumnParentData` property instead). Removed; confirmed via `grep` that no other file in the codebase imports it either.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no new user-visible strings (no Voice changes needed); no composable/ViewModel/Repository/Dao duplication.
- *Decoupling* — no `domain/` files touched; the change is contained to `LogDetailForm`'s layout.
- *Complexity & pattern health* — `LogDetailForm` grew from ~123 to ~132 lines (nesting indentation), still under the ~150-line guideline; the one added nesting level has an explanatory comment on why `fill = false` matters.
- *Dead code & hygiene* — the bad `weight` import (see above) was caught by `compileDebugKotlin` failing, not silently left in.
- *Repo hygiene* — `git status` clean aside from the pre-existing untracked `merged_branches.txt` (unrelated to this work, flagged in prior passes too, left alone); no secret-shaped content; new test file is real source, belongs in the repo.
- *Naming* — `LogDetailSheetTest.kt` follows the existing `*Test.kt` pattern and sits alongside `LogDetailScreenTest.kt`.
- *Hardcoded values / accessibility / deprecated APIs* — not applicable; no new colors, tap targets, or deprecation warnings.
- *Spec review* — `HODITH_SPEC.md` has no mention of this layout at this level of detail; nothing to update.
- *Tests* — regression test added to both wrappers: `LogDetailScreenTest.tagFieldFocused_saveButtonStaysReachable` (existing file) and a new `LogDetailSheetTest` (there was previously no instrumented test file for `LogDetailSheet` itself), tagged `@UiTest`/`@Smoke`. `TESTING.md`'s Compose UI row updated to describe the new coverage.

**Deferred:** nothing — the one open item (new instrumented coverage hadn't run on a device yet) was resolved by running it once an emulator became available, not deferred.

**Docs updated:** `TESTING.md` — Compose UI row. `PROGRESS.md` — item struck (removed entirely, per this doc's outstanding-only convention).

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green. `connectedDebugAndroidTest` scoped to `LogDetailScreenTest` + `LogDetailSheetTest` run twice — once before the `isImeVisible` refinement (6/6), once after (6/6, on `Pixel_8_API36(AVD)` specifically — a second physical device was connected mid-session with its screen locked, which made every instrumented test fail with a misleading "no compose hierarchy found" error until the run was scoped to the emulator via `ANDROID_SERIAL`, an environment issue rather than a code regression).

---

## fix/flaky-home-bigpicture-uistate-tests

**Scope:** Root-caused and fixed a JVM-unit-test flake that had hit `HomeViewModelTest.onQuickLogTap on an ongoing START_STOP case starts a second concurrent event` four times in CI (PRs #102, #108, a `main` push after #114, #117), always the same assertion, always on diffs that touched none of the files involved.

**Found & fixed:**

- Confirmed root cause: `HomeViewModel.uiState`/`BigPictureViewModel.uiState` both hardcoded `.flowOn(Dispatchers.Default)` on their `combine` chain — a real, load-bearing production fix (commits `670b605`/`fe6b9b9`) for a rapid-logging-burst ANR, but a real OS thread pool that JVM tests never redirect (`Dispatchers.setMain(UnconfinedTestDispatcher())` only touches `Dispatchers.Main`). The `combine` re-map ran on that unsynchronized real thread, racing `runTest`'s virtual scheduler and turbine's `awaitItem()`.
- Fix: injected the `flowOn` dispatcher as a Hilt-qualified `CoroutineDispatcher` (`di/DefaultDispatcher.kt`, `di/DispatcherModule.kt`), following the existing `Clock`/`FakeClock` seam pattern rather than introducing a new one. Production binds to the real `Dispatchers.Default` (ANR fix fully preserved); both JVM test files now pass `UnconfinedTestDispatcher()` directly to the ViewModel constructor (no Hilt in JVM unit tests), removing the real thread hop entirely.
- `BigPictureViewModelTest.kt`'s `uiState keeps the current detail across a repository change` had the identical mutate-then-`awaitItem()` shape and was equally susceptible, though it hadn't flaked in CI yet — fixed by the same constructor change.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no inline strings, no composables; the new `di/` module mirrors `CoroutineScopeModule`'s existing `object` + `@Provides` shape rather than inventing a new DI pattern.
- *Decoupling* — no `domain/` files touched; no `android.*` import added anywhere.
- *Complexity & pattern health* — no composables, no `remember`/`LaunchedEffect` touched. The injected dispatcher is consumed at the same layer `Clock` already is (constructor parameter), provided at the same layer `ClockModule`/`CoroutineScopeModule` already are (`SingletonComponent`) — no new layering introduced. Considered reusing `CoroutineScopeModule`'s existing hardcoded `Dispatchers.Default` and declined: it binds a `CoroutineScope` for an unrelated singleton (`NotificationEvalScheduler`'s fire-and-forget scope), not a swappable `CoroutineDispatcher`, so there was nothing to reuse.
- *Dead code & hygiene* — `import kotlinx.coroutines.Dispatchers` removed from both ViewModels (no longer referenced) in favor of `import kotlinx.coroutines.CoroutineDispatcher`; `ktlintFormat` fixed one line-wrap violation in the new `HomeViewModelTest` assertion.
- *Repo hygiene* — `git status` clean aside from the pre-existing untracked `merged_branches.txt` (unrelated, left alone); no secret-shaped content; no `.gitignore` gaps; the three new files (`di/DefaultDispatcher.kt`, `di/DispatcherModule.kt`, `di/DispatcherModuleTest.kt`) are real source, not local tooling/config, and belong in the repo.
- *Naming* — `DefaultDispatcher.kt`/`DispatcherModule.kt` sit in `di/` alongside `ClockModule.kt`/`CoroutineScopeModule.kt`, same naming shape.
- *Hardcoded values* — not applicable; this change removes the last hardcoded `Dispatchers.Default` reference from both ViewModels in favor of injection and adds no new numeric/color constants.
- *Accessibility* — not applicable; no UI touched.
- *Deprecated APIs* — one new compiler warning surfaced (`@DefaultDispatcher` on a constructor `val` is ambiguous between the parameter and the generated property under a future Kotlin default). Resolved with an explicit `@param:DefaultDispatcher` site target rather than left as a warning; `BigPictureViewModel`'s equivalent parameter isn't a property (no `private val`), so it wasn't ambiguous and needed no change.
- *Spec review* — not applicable; this is internal test infrastructure, not user-visible or spec'd behavior.
- *Tests* — new `DispatcherModuleTest` pins the production binding to the real `Dispatchers.Default` (nothing else exercises `DispatcherModule`, since both JVM test files bypass Hilt). New proof tests in `HomeViewModelTest`/`BigPictureViewModelTest` assert `uiState.value` reflects a mutation with no `awaitItem()` at all — demonstrating the recombination is now synchronous under the injected test dispatcher, not just a rerun of the previously-flaky assertion.

**Deferred:** nothing.

**Docs updated:** none (`HODITH_SPEC.md`/`TESTING.md` don't describe this internal seam).

**Verified:** `ktlintCheck → lintDebug → test (scoped, then full) → assembleDebug` sequential, all green. The previously-flaking test plus both new proof tests reran clean 5/5 with `--rerun` (forcing re-execution rather than Gradle's cache) — the proof tests no longer depend on a real thread at all, so this is stronger evidence than the rerun count alone.

---

## feat/insights-trends-recurrence-hazard

**Scope:** PROGRESS.md's Story C T3 — a Trends detector for whether a Case's past gaps form an early-spike pattern (it usually recurs quickly) or a dead-zone pattern (it almost never does), the fifth detector in the Trends roster T1 scaffolded, and a heavier sibling to the existing bursts CV flag.

**Feasibility ruling (stated before any code, per the item's own gate):** kept. Thresholds are self-relative to the Case's own average gap (a gap counts as "early" once it's at or under half the mean), not a fixed day count, matching how `isBursty` is already self-relative rather than absolute. A design refinement made during this pass, beyond what the item's own acceptance criteria specified: gating dead-zone on a minimum coefficient of variation in addition to the low early-gap share. Without that second gate, a Case with a merely *steady* rhythm (every gap close to the mean, near-zero variance) would trigger dead-zone on every call, since a steady Case trivially has no early gaps either — verified directly with a unit test (`computeRecurrenceShape is null for a steady rhythm even though no gap is ever early`) before considering the shape "separates cleanly from noise." Dead-zone's Voice copy was written to describe the *pattern* ("tends to take a while") rather than echo `WENT_QUIET`'s "still happening?" framing, since a Case can trigger both findings at once and they need to read as complementary, not repetitive.

**Changes:**

- `Insights.kt`: new `RecurrenceShapeResult` model.
- `InsightsEngine.kt`: `computeRecurrenceShape` (the detector) + five new named constants (`RECURRENCE_SHAPE_MIN_SAMPLE_COUNT`, `RECURRENCE_SHAPE_EARLY_FRACTION_OF_MEAN`, `RECURRENCE_SHAPE_SPIKE_MIN_SHARE`, `RECURRENCE_SHAPE_DEAD_ZONE_MAX_SHARE`, `RECURRENCE_SHAPE_DEAD_ZONE_MIN_COEFFICIENT_OF_VARIATION`), reusing the file's existing private `coefficientOfVariation` helper rather than duplicating it.
- `Trends.kt`: new `TrendFindingKind.RECURRENCE_SHAPE` (`priorValue`/`recentValue` repurposed as the self-relative day boundary and the observed early-gap share, following the enum's existing per-kind unit-mapping convention).
- `TrendsEngine.kt`: `computeTrendFindings` appends `computeRecurrenceShape`'s result last, always `HINT` like every other detector.
- `InsightsTab.kt`: new `RECURRENCE_SHAPE` branch in `TrendFindingContent`'s dispatch (the enum addition made this `when` non-exhaustive until filled in — caught at compile time as intended).
- `TrendsListScreen.kt`: preview fixture gained one `RECURRENCE_SHAPE` entry alongside the existing kinds.
- `Voice.kt`: `insightsRecurrenceShapeSentence`/`insightsRecurrenceShapeEvidenceLabel`, implemented in all three voices in this same commit — no em dashes in any of the three new strings.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no inline strings; both new sentence/evidence-label calls go through `Voice`. No ViewModel/Repository/Dao logic touched. `computeRecurrenceShape` calls the file's existing private `coefficientOfVariation` rather than reimplementing variance.
- *Decoupling* — `computeRecurrenceShape` takes no `now`/`Clock` at all (works purely off already-computed `GapStats`), and no `android.*` import was added to `Insights.kt`/`InsightsEngine.kt`/`Trends.kt`/`TrendsEngine.kt`.
- *Complexity & pattern health* — no new composables; the detector is one small function following `computeQuietSignal`/`computeGapShift`'s existing shape.
- *Dead code & hygiene* — no unused imports (ktlint's check covers this and passed); a throwaway JVM probe test (`RecurrenceShapeDemoProbe.kt`) was written to empirically check whether any existing demo Case already exercised the pattern, and deleted once it had answered that question — never committed. Pre-existing untracked `merged_branches.txt` in `git status` predates this branch, not part of this work, left alone.
- *Repo hygiene* — `git status` clean aside from the pre-existing file above; no secrets, no local paths, no new tooling/config files.
- *Naming* — new `Voice` keys follow the established `insightsXSentence`/`insightsXEvidenceLabel` pattern exactly, added to all three voices in this commit.
- *Hardcoded values* — all five new thresholds are named `internal const val`s with a shared doc comment explaining each, no magic numbers inline.
- *Accessibility* — no new tap targets; the new finding reuses the existing Trends row/plank tap surface.
- *Spec review* — `HODITH_SPEC.md` §10's "Trends detectors" list gained the "Recurrence shape" bullet, matching the existing four bullets' format.
- *Tests* — `InsightsEngineTest.kt`: an early-spike happy path with real threshold/share numbers, a spike at exactly the minimum sample count, a spike right at the share boundary, a dead zone with real spread (with real numbers), a dead zone right at its share boundary, a steady rhythm correctly producing no finding despite a zero early-gap share, a flat hazard clearing neither bar, a zero-average-gap Case, and below the minimum sample count. `TrendsEngineTest.kt`: wiring (real numbers, `HINT` reliability), ordering after tag share shift, and combined with a went-quiet finding on the same Case. `DemoDataSeederTest.kt`: confirmed empirically (throwaway probe, see above) that the existing BURSTY-density demo Cases already produce this finding without any seed changes — added a permanent test pinning `Noisy neighbours` as the cleanest single-finding example, rather than inventing a new demo Case the way T1's `trendingShift` needed to. `VoiceTest`'s existing reflection walk covers the two new keys automatically. New instrumented coverage: `InsightsTabTrendsCardTest.trendsCard_rendersRecurrenceShapeSentence`, `TrendsListScreenTest.recurrenceShapeFinding_rendersItsOwnPlank` — both run on a connected emulator, not just compiled.

**Second look, coverage re-check:** asked directly whether Trends coverage was thorough enough — real gaps against the *Tests* bullet above, not just against the item's own acceptance criteria. `RECURRENCE_SHAPE_DEAD_ZONE_MIN_COEFFICIENT_OF_VARIATION`'s own gate had no test anywhere near its 0.3 boundary — the two existing dead-zone tests sat at CV 0.86 and 0.3-ish-by-accident, and the "steady rhythm" test used the degenerate CV-exactly-0 case, so a `>=` → `>` mutation on the gate itself would have passed unnoticed. Fixed with a matched pair: CV 0.2727 (share alone would qualify, gate correctly blocks it) and CV exactly 0.3 (gate boundary, fires). Same gap on the spike side — the boundary was only proven from the "fires" direction (exactly 0.6); added a "just below" case (0.59, via 100 gaps for an exact fraction) proving the bar doesn't fire early. Last, the claim that `isBursty` and this detector are independent (stated in `Trends.kt`'s own doc comment) was never actually tested against real, non-synthetic data — the synthetic `gapStats()` test helper hardcodes `isBursty = false` regardless of input, so nothing could have caught the two being accidentally coupled. Added a test building real events through the production `computeGapStats` (gaps 2,3,4,5,6,7,8,9,10,40 — CV 1.12, so genuinely bursty) and asserting both `isBursty` true and `computeRecurrenceShape` null on the same `GapStats`. Four new tests, `InsightsEngineTest.kt`'s recurrence-shape section now 13 total (was 9); considered adding a `TRENDS_MAX_FINDINGS`-cap test with a `RECURRENCE_SHAPE` finding specifically, but declined — `capTrendFindings` is kind-agnostic (`.take(N)`) and already has direct tests proving the cap and the went-quiet-survives-the-cap ordering, and the real detectors can't structurally exceed 8 findings combined even at every detector's own per-Case maximum, so a kind-specific cap test would exercise no code path the generic tests don't already cover. Re-ran the full sequence after these additions (see **Verified** below) rather than assuming the new tests were compatible with what had already passed.

**Deferred:** nothing raised and declined.

**Docs updated:** `HODITH_SPEC.md` §10 — new "Recurrence shape" bullet. `TESTING.md` — Stats & visual data prep row gained a recurrence-shape clause. `PROGRESS.md` — T3 struck entirely; Story C's intro paragraph and T-range updated (T3–T8 → T4–T8, "Seven items remain" → "Six").

**Verified:** `ktlintCheck → test (scoped, then full) → lintDebug → connectedDebugAndroidTest (scoped to the two touched instrumented classes) → assembleDebug` sequential, all green — re-run in full after the coverage re-check above, not just the first pass: 752/752 unit tests, 11/11 instrumented on `Pixel_8_API36(AVD)`, no unused-import/unused-variable warnings in a forced recompile of main/test/androidTest source sets, no `android.*` import or direct `System.currentTimeMillis()` call in any touched domain file (grepped directly, not inferred).

---

## feat/insights-trends-tag-drift

**Scope:** PROGRESS.md's Story C T2 — a Trends detector for whether a tag's share of a Case's own events is rising or falling over time (e.g. a tag going from 10% to 40% of events), the fourth detector in the Trends roster T1 scaffolded.

**Feasibility ruling (stated before any code, per the item's own gate):** kept. Splits a Case's events into two chronological halves by event count (mirroring `computeGapShift`/`computeStreakShift`, not a fixed day window — sidesteps both a data-volume requirement and any per-event timezone handling), computes each qualifying tag's share in each half, and applies a dual-threshold check in the same shape `shiftDirectionFor` already uses for gap/streak shift, just in share units and with stricter floors (a share of a small event count swings more easily by chance than a day-average does). Two gates keep small samples from producing noise: a minimum total-event count before any comparison runs, and a minimum total tag-occurrence count before a specific tag is considered at all.

**Changes:**

- `Insights.kt`: new `TagShareShiftResult` model.
- `StatsEngine.kt`: `computeTagShareShift` (the detector) + `tagShareShiftDirectionFor` (its private dual-threshold helper) + five new named constants (`TAG_SHARE_SHIFT_MIN_SAMPLE_COUNT`, `TAG_SHARE_SHIFT_MIN_TAG_COUNT`, `TAG_SHARE_SHIFT_MIN_ABSOLUTE_FRACTION`, `TAG_SHARE_SHIFT_MIN_RELATIVE_FRACTION`, `TAG_SHARE_SHIFT_MAX_FINDINGS`).
- `Trends.kt`: new `TrendFindingKind.TAG_SHARE_SHIFT`; `TrendFinding` gained `tagName: String? = null` (only this kind sets it — it's the one kind that can produce more than one finding per Case, so results are ordered by effect size and capped independently of the shared `TRENDS_MAX_FINDINGS`).
- `TrendsEngine.kt`: `computeTrendFindings` gained an `eventsWithTags` parameter (defaulted to `emptyList()` so every existing call site kept working unmodified) and appends `computeTagShareShift`'s results last.
- `InsightsTabState.kt`: threads `eventsWithTags` (already in scope for `computeTagBreakdown`) into the `computeTrendFindings` call.
- `InsightsTab.kt`: new `formatPercent` helper beside `formatDays`; new `TAG_SHARE_SHIFT` branch in `TrendFindingContent`'s dispatch.
- `Voice.kt`: `insightsTagShareShiftSentence`/`insightsTagShareShiftEvidenceLabel`, implemented in all three voices in this same commit — "tends to" framing (Plain literally; Intense/Bright idiomatically non-causal, matching how the existing shift sentences already diverge in wording per voice), no em dashes in any of the three new strings.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no inline strings; both new sentence/evidence-label calls go through `Voice`. No ViewModel/Repository/Dao logic touched.
- *Decoupling* — `computeTagShareShift` takes no `now`/`Clock` at all (the count-based split needs no time reference), and no `android.*` import was added to `StatsEngine.kt`/`Insights.kt`/`Trends.kt`/`TrendsEngine.kt`.
- *Complexity & pattern health* — `tagShareShiftDirectionFor` is single-caller, same as its precedent `shiftDirectionFor`; no new composables.
- *Dead code & hygiene* — no unused imports (ktlint's check covers this and passed); no prototype to clean up — this design was reasoned analytically against existing precedent, not spiked. Pre-existing untracked `merged_branches.txt` in `git status` predates this branch, not part of this work, left alone.
- *Repo hygiene* — `git status` clean aside from the pre-existing file above; no secrets, no local paths, no new tooling/config files.
- *Naming* — new `Voice` keys follow the established `insightsXShiftSentence`/`insightsXShiftEvidenceLabel` pattern exactly, added to all three voices in this commit.
- *Hardcoded values* — all five new thresholds are named `internal const val`s with doc comments, no magic numbers inline.
- *Accessibility* — no new tap targets; the new finding reuses the existing Trends row/plank tap surface.
- *Spec review* — `HODITH_SPEC.md` §10's "Trends detectors" list gained the "Tag share shift" bullet, matching the existing four bullets' format.
- *Tests* — `StatsEngineTest.kt`: rising tag, falling tag, stable tag (no finding), below-minimum-sample, a tag below its own occurrence-count floor despite a sharp swing, and the per-detector cap keeping the three strongest shifts in order when more tags qualify. `TrendsEngineTest.kt`: wiring (tag name attached, reliability HINT), empty when nothing shifts, ordering after gap shift. `VoiceTest`'s existing reflection walk covers the two new keys automatically. No new Compose/instrumented test — `TrendFindingContent`'s `when` was the only exhaustiveness-sensitive call site and is covered by the new branch; the two androidTest files referencing `TrendFindingKind` only construct fixtures positionally, no exhaustive `when` to update.

**Deferred:** nothing raised and declined.

**Docs updated:** `HODITH_SPEC.md` §10 — new "Tag share shift" bullet. `TESTING.md` — Stats & visual data prep row gained a tag-share-shift clause. `PROGRESS.md` — T2 struck entirely; Story C's intro paragraph count and T-range updated (T2–T8 → T3–T8, "Eight items remain" → "Seven").

**Verified:** `ktlintCheck → test (scoped, then full) → lintDebug → assembleDebug` sequential, all green (735 unit tests). No instrumented run — this item's acceptance criteria only calls for domain + Voice ×3 + spec coverage, and no Compose/instrumented test needed updating (see Tests above).

---

## fix/event-timezone-offset

**Scope:** PROGRESS.md's "No timezone stored — same-day / time-of-day logic breaks for travelers" — every domain calculation bucketing a timestamp into a calendar day/hour resolved via the device's *current* zone at compute time, not the zone the event actually happened in, so a traveler got every past event's day/hour silently reinterpreted.

**Changes:**

- `EventEntity.kt`: new `utcOffsetMinutes: Int` column (`@ColumnInfo(defaultValue = "0")` paired with a matching Kotlin default, the same pattern `HunchEntity`'s additive columns use); `loggedZone()`/`zoneOffsetFromMinutes()`/`offsetMinutesAt()` helpers.
- `HodithDatabase.kt`: plain `AutoMigration(10, 11)` — no hand-written `Migration`; no production installs exist yet, so a static `0` default needs no runtime backfill.
- `StatsEngine.kt`/`InsightsEngine.kt`/`VerdictEngine.kt`: per-event bucketing (`computeFrequencyStats`, `computeRhythmStats`, `computeGapStats`'s event-to-event gaps, `distinctActiveDays`) resolves via each event's own `loggedZone()` instead of a shared device-current zone; anything compared against "now" (which has no captured offset) is unchanged. `computeRhythmStats` dropped its `zone` parameter entirely — every date in it is per-event with no "now" reference, so the parameter was dead weight once the fix landed.
- `CalendarGrid.kt`: `datesCovered`/`spansMultipleDays` gained an optional `endZone: ZoneId = zone` parameter (default preserves every existing caller's behavior) so a still-running event's open end can resolve via the live current zone rather than the event's own stale captured offset — `VerdictEngine.distinctActiveDays` and `InsightsTabState.insightsTabState` now special-case an ongoing event exactly like `BigPictureGrid`'s private `coveredDates` already did, closing an inconsistency between the two that a second review pass caught (see below).
- `CheckIn.kt`/`TriggerEngine.kt`: no functional change — both are always anchor-vs-`now`, which is already correct under the rule above; added a comment so a future reader doesn't assume this was missed.
- `BigPictureGrid.kt`: `EventDetailRow`/`DayDetailDialog`/`WeekDetailDialog`/`coveredDates` all resolve per-event now, so the composable's own `zoneId` parameter became entirely unused — removed rather than left dead.
- `LogDetailViewModel.kt`/`DemoDataSeeder.kt`: capture the offset at construction (`toEventEntity` at the chosen `occurredAt`, not save-time `now`, so a retro-logged entry gets its own historical offset).
- `BigPictureViewModel.kt`/`CaseEventDetail.kt`/`EventDao.kt`: threaded the column through Big Picture's flat `CaseEventDetail`/`CalendarEvent` projections, which don't get new `EventEntity` columns automatically (raw `@Query` projections, not `@Embedded`).
- `BackupValidationResult.kt`: range check on `utcOffsetMinutes` (`-720..840`, real-world UTC offset bounds).

**Deviations from the item's originally-written acceptance criteria (discussed and agreed with the user, not a unilateral call):**

- The item posed backfill as a choice between "assume the device's current offset" or "leave pre-migration rows on old behavior." Neither was used: since no production installs exist, a plain `AutoMigration` (static `0` default) was simpler than either, and the one real test dataset's rows were hand-corrected via an export → manual per-event offset fix (`America/Vancouver`, `-420` for every event in that file, verified against real PDT/PST rules) → reimport round-trip, outside the app entirely.
- `BACKUP_SCHEMA_VERSION` was **not** bumped, contrary to the item's "three changes, not one" framing. `EventEntity.utcOffsetMinutes`'s Kotlin default already makes an old export missing the field parse successfully via Moshi's codegen (`moshi-kotlin-codegen` respects constructor defaults for absent JSON keys), so nothing became unreadable — bumping would have contradicted `BackupData.kt`'s own documented policy ("bumped only if a future change makes an older export unreadable"). No new `BackupSerializerTest`/upgrade-step coverage was added as a result — there's no new upgrade path to test.

**Checklist walk (against the working-tree diff):**

- *Duplication* — `EventEntity.loggedZone()`/`CalendarEvent.loggedZone()` both converted minutes to a `ZoneOffset` independently at first; consolidated into one shared `zoneOffsetFromMinutes()`. The inline `durationMode == START_STOP && endedAt == null` "is this event ongoing" check appears in three of this diff's files (`VerdictEngine.kt`, `InsightsTab.kt`) — checked against the rest of the codebase before treating it as a new violation: `CaseDetailScreen.kt` and `InsightsTab.kt` already had this exact inline pattern before this branch, so it's an established convention for a single-event check (the `ongoingEventsIn`/`ongoingEventIn` helpers exist for filtering a list, which `InsightsTabState.kt` correctly uses instead). Not a new duplication to fix.
- *Decoupling* — no `android.*` import added to any `domain/` file; `VerdictEngine.kt`'s ongoing-event check is inlined rather than importing the `viewmodel`-layer `ongoingEventsIn` helper, keeping domain → viewmodel dependency direction intact.
- *Complexity & pattern health* — `zoneOffsetFromMinutes`/`endZoneFor` (local function in `InsightsTabState.kt`) both have 2+ call sites, earning their extraction.
- *Dead code & hygiene* — real finding: `ktlintFormat` (run to fix import-ordering/line-length violations from the rename pass) silently rewrote three files — `CaseDetailScreen.kt`, `InsightsTab.kt`, `InsightsTabState.kt` — with LF-only line endings against the repo's CRLF convention (`file` confirmed; every other touched file still had CRLF). Restored via a targeted PowerShell LF→CRLF pass on just those three files, verified the diff's line counts didn't change and the build stayed green. `git status` otherwise clean aside from the pre-existing untracked `merged_branches.txt` (not part of this work, left alone) and the new `app/schemas/.../11.json` (expected migration artifact).
- *Naming* — `EventEntity.zone()`/`CalendarEvent.zone()` renamed to `loggedZone()` on request: `zone: ZoneId` (device-current) and a `.zone()` extension returning the event's *captured* offset read as the same concept under grep even though they mean opposite things; `loggedZone()` disambiguates.
- *Hardcoded values* — `VALID_UTC_OFFSET_MINUTES_RANGE` is a named constant, not an inline range.
- *Spec review* — `HODITH_SPEC.md` §5 (storage line), the Event field table (new `utcOffsetMinutes` row), and §9 (active-span zone rule) all still described the pre-fix "displayed in device timezone" behavior — updated to state the actual per-event-offset rule and the still-running-event exception. `TESTING.md`'s Stats & visual data prep, Export/import, and Room migrations rows updated with the new coverage.
- *Tests* — a second review pass (prompted by the user asking about simplifications and stability concerns) found a real inconsistency this branch had initially left in place: `BigPictureGrid`'s private `coveredDates` already special-cased a still-running event's open end to resolve via the live current zone, but `VerdictEngine.distinctActiveDays`/`InsightsTabState.insightsTabState` didn't, so a currently-open event could silently misplace "today" by a day if the device's zone had changed since the event started. Fixed (see Changes) and covered with regression tests in `VerdictEngineTest`/`InsightsTabStateTest`/`CalendarGridTest`, each verified to actually fail without the fix (temporarily reverted, confirmed the failure, restored). The first draft of the `InsightsTabStateTest` case had a math error — the chosen offset skew shifted the event's *start* date too, not just "now" as intended — caught by the test failing for the wrong reason, not by rechecking the arithmetic up front.

**Deferred:** nothing — the one open item (new instrumented coverage hadn't run on a device yet) was resolved by running it once an emulator became available, not deferred.

**Docs updated:** `HODITH_SPEC.md` §5, Event table, §9. `TESTING.md` — Stats & visual data prep, Export/import, Room migrations rows. `PROGRESS.md` — item struck; T7/T8's explicit gate checkboxes ticked with a note that their own feasibility/method rulings are still open; the Big Picture cross-case item's "no timezone stored" prerequisite bullet updated from blocked to resolved.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green (727 unit tests). `connectedDebugAndroidTest` scoped to `DatabaseFreshInstallTest` (6/6, including the new v10→v11 migration case) and `BigPictureQueriesTest` (4/4) on `Pixel_8_API36(AVD)`.

