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

## feat/insights-trends-tag-outcome

**Scope:** PROGRESS.md's Story C T4 — a Trends detector comparing a tag's events against a Case's other events on intensity and duration, backed by a real permutation-significance test rather than a descriptive threshold; the sixth detector in the Trends roster, and the first able to report `Pattern`. Also builds the reusable label-shuffle permutation-significance helper T5's future timeline-shuffle variant will build on.

**Feasibility ruling:** already recorded before this pass (Monte Carlo spike, off-repo) — 1000 iterations, relative % difference in means as the statistic, 15 tagged/30 untagged sample floors per outcome, a deterministic seed from (caseId, tagName, outcome, eventCount). This pass confirmed the remaining, previously-unspecified parameters directly with the user before implementing rather than inferring them: a 5% significance alpha, a 20% relative-difference descriptive floor gating the permutation test from even running, and a 3-finding per-detector cap (matching `TAG_SHARE_SHIFT_MAX_FINDINGS`'s precedent value).

**Changes:**

- `domain/PermutationSignificance.kt` (new): `permutationPValue`, `labelShufflePValue`, `relativeDifferenceInMeans`, `permutationSeedFor` — generic label-shuffle now, reusable for T5's timeline-shuffle variant later.
- `domain/StatsEngine.kt`: `computeTagOutcomeFindings` (the detector) + `tagOutcomeResultFor`/`outcomeValueFor` private helpers + six new named constants.
- `domain/Insights.kt`: new `TagOutcome` enum, `TagOutcomeResult` model.
- `domain/Trends.kt`: new `TrendFindingKind.TAG_OUTCOME`; `TrendFinding` gained `outcome: TagOutcome? = null`.
- `domain/TrendsEngine.kt`: `computeTrendFindings` appends `computeTagOutcomeFindings`'s results last, always `PATTERN` (a non-significant candidate never reaches this function at all).
- `ui/voice/Voice.kt`: `insightsTagOutcomeSentence`/`insightsTagOutcomeEvidenceLabel`, implemented in all three voices in this same commit — the first Trends sentence needing a nested `when(outcome) { when(direction) {...} }`, since every prior detector only varies by direction.
- `ui/casedetail/InsightsTab.kt`: new `TAG_OUTCOME` branch in `TrendFindingContent`'s dispatch; `formatIntensity` factored out of what had been an inline `String.format` call.
- `ui/share/ShareCardTemplate.kt`: found and fixed the same inline intensity-formatting duplicate while extracting `formatIntensity` — not part of the original plan, a checklist-walk finding (see *Duplication* below).
- `ui/casedetail/CaseDetailScreen.kt` / `ui/share/SharePreviewScreen.kt`: performance fix — both now memoize `insightsTabState` on the calendar day rather than raw `now`, since every Trends/Stats computation that reads `now` only cares about calendar-day granularity (`CalendarMath.kt`'s `daysBetween`). `SharePreviewScreen` had no memoization at all before this (recomputed on every recomposition — every keystroke, every section toggle); `CaseDetailScreen`'s existing `remember` was keyed on a 60-second ticking clock, wastefully re-running the whole Trends computation — now including a 1000-iteration permutation test — every minute the tab stayed open regardless of data changes.
- `data/demo/DemoDataSeeder.kt`: Migraine's `"aura"` tag now gets a deterministic, boosted (60%) duration assignment on ~40% of its events — bypassing the normal random `tagsFor` draw for this one tag so the tagged/untagged split stays clean — giving the demo data a real, findable effect matching `HODITH_SPEC.md`'s own "aura migraines last 40% longer" example.

**Checklist walk (against the working-tree diff):**

- *Duplication* — no inline UI strings; both new sentence/evidence-label calls go through `Voice`. Real find while extracting `formatIntensity`: `ShareCardTemplate.kt`'s `MiniIntensitySection` had the exact same inline `String.format(Locale.US, "%.1f", ...)` `InsightsTab.kt` used to have — switched to the new shared helper instead of leaving two copies, and removed the now-unused `java.util.Locale` import there too.
- *Decoupling* — confirmed by direct grep, not assumption: no `android.*` import in any touched `domain/` file. No `System.currentTimeMillis()` anywhere — the day-bucketing performance fix reads the already-passed `now: Long` parameter, not a new time source.
- *Complexity & pattern health* — the nested `when(outcome) { when(direction) {...} }` in `insightsTagOutcomeSentence` is a real structural difference from every prior Trends sentence (all single-dimension), not gratuitous nesting — this is the first detector with two independent axes to word. `permutationPValue`/`labelShufflePValue`/`relativeDifferenceInMeans`/`permutationSeedFor` are each single-caller today; kept as separate `internal` functions rather than inlined specifically so T5's timeline-shuffle variant has something to call — the item's own stated purpose, not a premature abstraction.
- *Dead code & hygiene* — real finding, a recurring class of bug this repo has hit before (`fix/event-timezone-offset`'s cleanup pass, now rolled off this log's 5-entry retention window but still in git history): `ktlintFormat` (run to fix line-length/argument-wrapping violations) silently rewrote `StatsEngine.kt`, `DemoDataSeeder.kt`, and `SharePreviewScreen.kt` to LF-only line endings against the repo's CRLF convention, and both newly-created files (`PermutationSignificance.kt`, `PermutationSignificanceTest.kt`) were LF from creation. Confirmed by counting `\r\n` vs. bare `\n` per file (a targeted Python pass this session, not PowerShell) rather than trusting `git diff`'s silent normalization; restored CRLF on all five, verified the diff's line/content counts were identical before and after. `git status` otherwise clean aside from the pre-existing untracked `merged_branches.txt` (flagged unrelated in three prior entries already, still left alone).
- *Repo hygiene* — no secrets, no local paths, no new tooling/config files.
- *Naming* — `insightsTagOutcomeSentence`/`insightsTagOutcomeEvidenceLabel` follow the established `insightsXSentence`/`insightsXEvidenceLabel` pattern. `PermutationSignificance.kt` breaks the domain layer's usual `*Engine.kt` naming (`StatsEngine`, `InsightsEngine`, `TrendsEngine`) deliberately: like `CalendarMath.kt`, it's a generic, Case-independent utility, not a per-entity engine, so that existing non-`Engine` precedent applies rather than the `*Engine.kt` one.
- *Hardcoded values* — all six new constants are named `internal const val`s, their alpha/floor/cap values confirmed with the user before implementing since the original feasibility ruling didn't specify them. The demo seeder's `durationBoostFactor = 1.6` and 40% showcase chance are inline per-Case config, matching how every other `CaseSeed` field (e.g. `ongoingEventCount`) is already inline rather than a shared named constant.
- *Accessibility* — no new tap targets; the new finding reuses the existing Trends row/plank tap surface.
- *Deprecated APIs* — none introduced; build output showed only the pre-existing Moshi Kapt deprecation warning.
- *Spec review* — `HODITH_SPEC.md` §10's tag→outcome paragraph (previously a placeholder noting the detector was "in progress") rewritten with the actual shipped rules — gating sample sizes, descriptive floor, significance level, suppression behavior, per-detector cap — matching the level of detail every other detector's bullet already has, folded into the existing paragraph in place rather than added as a new bullet, per the item's own note that this paragraph exists specifically to be updated once T4 ships.
- *Tests* — see below; `TESTING.md`'s Stats & visual data prep row gained a permutation-helper clause and a tag→outcome clause, matching the existing per-detector clause style.

**Tests:**

- `PermutationSignificanceTest.kt` (new): seed determinism, distinct seeds for distinct inputs, `relativeDifferenceInMeans`'s zero-baseline guard, `permutationPValue`/`labelShufflePValue` boundary behavior (always-extreme and never-extreme cases), the exact fraction for a deterministic mixed-extremity sequence (pins the count/iterations division itself, not just its endpoints), `permutedStatistic` called exactly `iterations` times, and `labelShufflePValue` preserving each group's size across every shuffle (a structural guarantee, tested with a size-reporting statistic so it fails hard rather than "usually").
- `StatsEngineTest.kt`: a strong low-variance effect (significant, both intensity and duration), direction reported both ways, below the tagged-count floor, below the untagged-count floor, below the descriptive floor, a genuine null with real shared variance that clears the descriptive floor by chance but the permutation test correctly declines to confirm, the per-detector cap/ordering across both outcomes, an event with no recorded intensity excluded from an intensity comparison, a still-running event excluded from a duration comparison, a reversed (bad-round-trip) `endedAt` excluded from a duration comparison, and one tag producing both an intensity and a duration finding at once. The cap/ordering test needed a real fix mid-pass: an initial version put four tags' tagged groups directly against each other, but each tag's "untagged" comparison group is every *other* tag's events too — real contamination that broke the intended effect-size ordering. Fixed with a large, dominant baseline pool so cross-tag contamination stays negligible. The duration/exclusion tests were a second-look addition once initial coverage turned out intensity-only — the DURATION half of `outcomeValueFor` had no direct unit coverage at all until this pass.
- `TrendsEngineTest.kt`: a `PATTERN` finding with tag name/outcome attached (intensity); a second wiring test confirming a `DURATION` outcome flows through too, not just intensity; ordering after recurrence shape. The ordering fixture initially put all tagged events early and all untagged events late in time, which also (unintentionally) tripped `TAG_SHARE_SHIFT` — a tag confined to one half of a Case's history is exactly what that detector looks for. Fixed by interleaving tagged/untagged events evenly across the same span.
- `DemoDataSeederTest.kt`: Migraine clears both sample-size floors for `"aura"`, and the real seeded data produces the actual `TAG_OUTCOME`/`PATTERN` finding, not just a sample-count assertion.
- `InsightsTabTrendsCardTest.kt`/`TrendsListScreenTest.kt` (instrumented, new methods): a rendered sentence for each outcome type, not just one — the two use different formatters (`formatIntensity` vs. `formatMinutesDuration`) behind the same `when` branch, so a swapped formatter or a swapped `Voice` argument would silently show a plausible-looking but wrong number rather than fail to compile; only a rendered-text assertion catches that class of bug. Second-look addition, prompted by the user asking directly whether coverage was comprehensive enough against silent malfunction — the original pass leaned on `VoiceTest`'s reflection walk (which does already exercise all 4 outcome×direction combinations via its enum-Cartesian-product argument generation, confirmed by reading `VoiceTest.kt` directly rather than assuming) but that only proves each `Voice` sentence function is well-formed in isolation, not that `InsightsTab.kt` calls it with the right formatter/arguments for the branch it's in.

**Deferred:** nothing raised and declined — one open question surfaced and was resolved rather than deferred: whether `computeTrendFindings` needed a new `caseId` parameter for seeding. Resolved by reading it off `eventsWithTags.first().event.caseId` instead — every events list passed in already belongs to one Case, the same assumption every other per-Case stats function makes — so no call-site signature changes were needed anywhere.

**Docs updated:** `HODITH_SPEC.md` §10 — tag→outcome paragraph rewritten with the full shipped rules. `TESTING.md` — Stats & visual data prep row gained permutation-helper and tag→outcome clauses. `PROGRESS.md` — T4's whole section removed entirely once every box was checked, matching T1–T3's precedent (its own line 78 said as much: "won't survive PROGRESS.md's cleanup once T4 ships") rather than left in place with checked boxes, caught on a second look after initially only ticking them; Story C's intro paragraph updated (T4 counted as shipped, "Six items remain" → "Five", T4–T8 → T5–T8 range references).

**Verified:** `ktlintCheck → lintDebug → test (scoped, then full) → assembleDebug` sequential, all green — run three times across this pass (before the CRLF fix and `ShareCardTemplate.kt` dedup; after those; after the coverage second-look), fully green each time. `connectedDebugAndroidTest` scoped to `InsightsTabTrendsCardTest` + `TrendsListScreenTest` on `Pixel_8_API36(AVD)` once an emulator became available: 14/14 (7 each — existing coverage plus the 3 new tag-outcome methods), 0 failed, 0 skipped — actually run, not just compiled.

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
