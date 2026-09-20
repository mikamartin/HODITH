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

---

## feat/insights-trends-went-quiet

**Scope:** PROGRESS.md's "Case quiet vs. abandoned" item — resolved its open 🎨 design decision with the user (surfacing mechanism, threshold logic, relationship to check-ins/`SILENT_FOR` triggers) and implemented it in the same branch as a new Trends finding, rather than leaving the design ruling as a separate pass ahead of a later implementation item.

**Changes:**

- `domain/Insights.kt`/`InsightsEngine.kt`: new `QuietSignalResult`, `QUIET_SIGNAL_MIN_SAMPLE_COUNT`/`QUIET_SIGNAL_RECENT_ACTIVITY_WINDOW_DAYS`, `computeQuietSignal`. Reuses the already-computed `GapStats.isCurrentGapLongest` rather than building a new percentile helper — numerically identical to the item's originally-specified 99th-percentile threshold at realistic Case sizes (they only diverge past ~100 historical gaps), so the simpler existing field was reused instead.
- `domain/Trends.kt`/`TrendsEngine.kt`: new `TrendFindingKind.WENT_QUIET`, prepended first in `computeTrendFindings`'s output when it fires (ahead of gap/streak/frequency shift), gated by a new `recentlyActiveElsewhere: Boolean = false` parameter — defaults preserve every existing 3-arg call site.
- `data/EventDao.kt`/`HodithRepository.kt`/`RoomHodithRepository.kt` (+ `FakeHodithRepository`): new `observeMostRecentLoggedAtAcrossActiveCases()`, a lean `MAX(loggedAt)` scalar query across active Cases — the cross-Case "still logging elsewhere" signal the finding's second condition needs. No schema change.
- `viewmodel/InsightsTabState.kt`/`CaseDetailViewModel.kt`/`CaseDetailScreen.kt`/`TrendsListViewModel.kt`: threaded the new signal from the repository through to `computeTrendFindings`, converting it to a recency boolean at the `statsSections` boundary rather than passing raw timestamps into the domain layer.
- `ui/casedetail/InsightsTab.kt`/`ui/voice/Voice.kt`: new `WENT_QUIET` rendering branch and `insightsWentQuietSentence`/`insightsWentQuietEvidenceLabel` Voice keys ×3, framed as an open question ("still happening, or has it wound down?") per spec §4's "ask rather than silently report" rule — never a statement that the user did something wrong.
- `data/demo/DemoDataSeeder.kt`: doc-comment only. The existing "Nosebleed" `quietSpell` seed already deterministically sets a new longest-gap record (it was built for the Gaps card's own note); confirmed by test that it exercises `WENT_QUIET` for free, no seed-data changes needed.

**Found & fixed:**

- The first implementation pass covered domain/ViewModel/DAO logic thoroughly but shipped no Compose UI test for the new `WENT_QUIET` render branch in `InsightsTab.kt`'s `TrendFindingContent` — caught only when the user asked directly whether UI tests had run. Added `trendsCard_rendersWentQuietSentence` (`InsightsTabTrendsCardTest.kt`, the compact card `CaseDetailScreen` actually renders) and `wentQuietFinding_rendersItsOwnPlank` (`TrendsListScreenTest.kt`, the full-list screen), both asserting the real Voice sentence text renders on screen, not just that the domain layer produces the right `TrendFinding`.

**Checklist walk (against the working-tree diff):**

- *Duplication* — checked against every existing/planned Trends detector and the Gaps card before designing: none of `GAP_SHIFT`/`STREAK_SHIFT`/`FREQUENCY_SHIFT` ever reads the live/current gap (only completed history), and `isCurrentGapLongest` was computed but never rendered anywhere before this — not a restatement of an existing signal. New Voice keys added to all three voices in this same pass. `observeMostRecentLoggedAtAcrossActiveCases` doesn't overlap any existing repository query.
- *Decoupling* — `computeQuietSignal`/`computeTrendFindings` take plain data (`GapStats`, a `Boolean`), no `Clock`/`System.currentTimeMillis()` call in `domain/`; the one `now`/`zone`-dependent computation (the recency window check) stays in `InsightsTabState.kt`, outside `domain/`. No `android.*` import added to any touched `domain/` file.
- *Complexity & pattern health* — no new composables; one new `when` branch in the existing `TrendFindingContent` dispatch, matching `GAP_SHIFT`'s shape exactly.
- *Dead code & hygiene* — found and fixed one issue in this pass: the first draft of `HODITH_SPEC.md`'s new `WENT_QUIET` bullet read as historical narration ("Resolves the former 'Case quiet vs. abandoned' design question…") rather than stating current fact, which the checklist's own "current-state docs" item flags explicitly — reworded to a plain present-tense rule instead. `git status` clean aside from a pre-existing untracked `merged_branches.txt` (not part of this work, left alone).
- *Naming* — `QUIET_SIGNAL_*` constants and `WENT_QUIET` follow the existing `GAP_SHIFT_MIN_SAMPLE_COUNT`/`TrendFindingKind` conventions; no new files.
- *Hardcoded values* — both new thresholds are named `domain/` constants, not inline numbers.
- *Spec review* — `HODITH_SPEC.md` §10 updated (new detector line, plus a note that it always leads the Trends list); confirmed no other section still described the old design-only framing.
- *Tests* — happy path plus boundary/negative cases at every layer (`InsightsEngineTest`, `TrendsEngineTest`, `InsightsTabStateTest`, `CaseDetailViewModelTest`, `EventDaoTest`, `DemoDataSeederTest`, `InsightsTabTrendsCardTest`, `TrendsListScreenTest`) rather than one representative case per function, per explicit request.

**Deferred:** nothing.

**Docs updated:** `HODITH_SPEC.md` §10 — new `WENT_QUIET` entry in the Trends detectors list, plus the list-ordering note. `PROGRESS.md` — "Case quiet vs. abandoned" removed from Standalone (fully resolved and shipped as a Trends detector, not left as a separate design-only item); Story C's intro reworded to note this third already-shipped finding kind; T3's design-decision note gained a cross-reference to reconcile its "dead zone" wording against `WENT_QUIET`; T9's design-decision note gained an open question about whether `WENT_QUIET` belongs on a share card at all, plus a consideration (raised by the user) that the card may need a generation timestamp given `WENT_QUIET`'s live-state sentence stops being accurate the moment new data arrives, unlike the Insights tab's always-fresh recompute.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green (one `ktlintFormat` round-trip: fixed three `Voice.kt` expression-body line-length violations and one `CaseDetailViewModel.kt` chained-call wrap). `connectedDebugAndroidTest` run twice as the emulator became available mid-session: first scoped to `EventDaoTest` (36/36, including the three new `observeMostRecentLoggedAtAcrossActiveCases` cases), then again scoped to `InsightsTabTrendsCardTest`/`TrendsListScreenTest`/`CaseDetailScreenTest` after adding the Compose UI coverage above (46/46, all on `Pixel_8_API36(AVD)`).

---

## feat/insights-trends-scaffold

**Scope:** A retroactive checklist walk + test-coverage audit against T1's already-merged diff (`bae8aed`, PROGRESS.md Story C T1 — the Trends section scaffold and gap/streak-shift migration), run on request rather than alongside the feature's own authoring.

**Found & fixed:**

- `TrendsListViewModel` had no unit test, unlike every other `ViewModel` in the app (`TriggersViewModel`, `ShareViewModel`, etc., each with a matching `FakeHodithRepository`/`FakeClock`/Turbine test). Added `TrendsListViewModelTest.kt`: case icon/name mapping, findings mirroring `stats.trends` from `insightsTabState()`, and the null-case (deleted Case) path.
- T1's own acceptance-criteria checklist in `PROGRESS.md` was never struck even though this diff ships it end-to-end — left as open work indefinitely. Removed the whole T1 entry (matching how other fully-resolved items are retired here, e.g. `feat/case-description-on-home`'s entry) and reworded the Story C intro to state T1 shipped rather than describing it as upcoming. One acceptance criterion ("each finding row independently tappable to its own info dialog") shipped differently than planned — a single shared dialog on the compact card instead — but that's already the accurate, intentional description in `HODITH_SPEC.md` §10, so no further doc fix was needed there.
- `InsightsTabTrendsCardTest`'s synthetic `InsightsTabState.Ready` fixture passed `RhythmDisplay(cells = emptyList())`, violating `RhythmDisplay.cells`' documented "always all 28 day-of-week × time-of-day cells" invariant. Never caught by `./gradlew test` (JVM-only) or code review, only by actually running the class on a device: `RhythmCard`'s `display.cells.first { ... }` (`InsightsTab.kt:589`) throws `NoSuchElementException` on an empty grid, so all three of its tests crashed instead of asserting anything. Fixed by building the real full 28-cell grid (`HeatmapLevel.EMPTY`, count 0) in the fixture instead of an empty list — a test-fixture bug, not a production one, since `computeRhythmStats()` never actually produces a short list.

**Checklist walk (against the merged diff `main...feat/insights-trends-scaffold`):**

- *Duplication* — no inline strings; new Voice keys (`insightsSectionLabelTrends`, `insightsTrendsShowMoreAction`, `insightsTrendsInfoTitle`/`Body`, `trendReliabilityHintLabel`/`PatternLabel`, `insights*ShiftEvidenceLabel`) all added to Serious/Goth/Quirky in the same commit (compiler-enforced via the `Voice` interface).
- *Decoupling* — `TrendsEngine.kt`/`Trends.kt` stay pure Kotlin, no `android.*` imports; `TrendsListViewModel` takes the injected `Clock`, not `System.currentTimeMillis()`.
- *Complexity & pattern health* — the old monolithic `TrendCard` was split into small, single-purpose composables (`TrendsCard`, `TrendReliabilityTag`, `TrendFindingContent`, `TrendFindingRow`, `TrendFindingPlank`), each reused across the compact card and the new full-list screen rather than duplicated.
- *Dead code & hygiene* — old `TrendCard`/its info-icon test correctly removed rather than left dead; no stray untracked files.
- *Repo hygiene* — `git status` clean; no secrets, no local paths.
- *Naming* — new files (`Trends.kt`, `TrendsEngine.kt`, `TrendsListViewModel.kt`, `TrendsListScreen.kt`) match existing package/suffix conventions.
- *Hardcoded values* — `TRENDS_MAX_FINDINGS`/`TRENDS_DEFAULT_VISIBLE_COUNT` are named constants, not inline numbers.
- *Accessibility* — icon-only actions keep non-empty `contentDescription`s; verified via the instrumented run below.
- *Deprecated APIs* — none; `lintDebug` clean.
- *Spec review* — `HODITH_SPEC.md` §10 updated with the new Trends section and its "Trends detectors" subsection, correctly documenting the shared-dialog divergence noted above.

**Deferred:** nothing — both findings were fixed in this pass.

**Docs updated:** `PROGRESS.md` — Story C T1 struck in full (shipped); Story C intro reworded accordingly.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green. `connectedDebugAndroidTest` scoped to the branch's touched instrumented classes (`CaseDetailInsightsTabTest`, `CaseDetailScreenTest`, `InsightsTabTrendsCardTest`, `TrendsListScreenTest`, `ShareCardTemplateTest`) — first run caught the `InsightsTabTrendsCardTest` fixture bug above (3/87 failed); 87/87 green on `Pixel_8_API36(AVD)` after the fix.

---

## feature/big-picture-year-filter

**Scope:** PROGRESS.md's "Big Picture: year filter" — UX settled via a throwaway HTML prototype (`docs/mockups/big-picture-year-filter-prototype.html`). Adds a third **Year** trigger chip narrowing the grid's month range, bundled with the grid's month order reversing to current-first/top (the reversal's own rationale — matching the Year dialog's current-year-first listing — only makes sense alongside the filter, so the two shipped together rather than split across two PRs, per discussion with the user).

**Changes:**

- `BigPictureFilterState.kt`: four new pure functions — `monthsNewestFirst`, `bigPictureYearFilterVisible`, `bigPictureYearOptions`, `filterMonthsByYear` — alongside the existing `isTagVisible`/`isPastOrToday`/legend helpers.
- `BigPictureGrid.kt`: `months` stays ascending (unchanged); a new `filteredMonths`/`displayMonths` layer narrows by the selected year and reverses only at the render boundary, extending `MonthPickerDialog`'s existing display-only-reversal pattern rather than flipping the base list's polarity everywhere. `LaunchedEffect` now scrolls to the top on `displayMonths` identity instead of the bottom on `months.size`. `FilterTriggerChip` gained `isFiltered` (a highlight border/ring on all three chips when narrowed off default, reusing `BrightChip`'s existing selected-state ring for Bright). New `YearFilterChip` composable (single-select, dismisses its dialog on tap, `primaryContainer` to read as its own chip family). Chip content format changed from "Cases N of M" to "Cases: N" (`Voice.bigPictureFilterCount` dropped its `total` parameter). `MonthPickerDialog` now scopes to the active year filter (per discussion with the user) rather than the full range, with its scroll-index math corrected to read against `displayMonths`, not the list it was handed.
- `Voice.kt`: new `bigPictureYearFilterLabel` ("Year") as a shared-default, not per-voice — matches `bigPictureCasesFilterLabel`/`bigPictureTagsFilterLabel`'s un-re-voiced axis-name pattern rather than `bigPictureMonthPickerTitle`'s per-voice verb-phrase pattern, since "Year" carries no personality of its own. No separate dialog-title key — reuses the label, same as Cases/Tags.
- Test hook: `BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG`, a `testTag` on the week-chevron `Box` containing `today`, added because neither `.onFirst()` nor `.onLast()` is a sound way to find "today's week" once month order is current-first; all six existing positional lookups in `BigPictureScreenTest.kt` switched to it.

**Checklist walk (against the working-tree `git diff`):**

- *Duplication* — no inline strings; the Year label/dialog title route through `Voice` like Cases/Tags. `YearFilterChip` is new, deliberate chip family (distinct color), not an accidental copy-paste of `TagFilterChip`.
- *Decoupling* — all new filtering logic (`monthsNewestFirst`, `bigPictureYearFilterVisible`, `bigPictureYearOptions`, `filterMonthsByYear`) is pure Kotlin in `BigPictureFilterState.kt`, not inline in the composable; no `System.currentTimeMillis()`, no `android.*` import. Year filter state stays local `remember` state in `BigPictureGrid`, matching `visibleCaseIds`/`visibleTagNames` — doesn't need to survive navigation, so no ViewModel change.
- *Complexity & pattern health* — `LaunchedEffect(displayMonths)` keys on the list itself (structural equality), not just `.size`, so switching between two same-length years still re-triggers the scroll-to-top; the old `.size`-only key wouldn't have. `monthsNewestFirst` is a one-line wrapper around `.asReversed()` kept as a named, separately-unit-tested function rather than inlined, since it's the exact seam the scroll/index math depends on.
- *Dead code & hygiene* — `docs/mockups/big-picture-year-filter-prototype.html` deleted now that the real implementation and its tests have landed (`git grep`-checked first: every reference lived inside the PROGRESS.md item just struck, nothing else pointed at it). The separate, still-open "Big Picture: filter pill consistency pass" PROGRESS.md item referenced this work as "in-progress" and cited pre-diff line numbers for the chip composables it plans to touch — updated both (Year chip now exists and shares Bright's undifferentiated-color gap; line numbers refreshed) so it isn't a landmine for whoever picks it up next.
- *Repo hygiene* — `git status` clean throughout; no secrets, no local paths; the deleted mockup is the only removed file.
- *Naming* — `YearFilterChip`, `filterMonthsByYear`, etc. follow existing PascalCase/camelCase conventions; `bigPictureYearFilterLabel` joins the existing `bigPicture*FilterLabel` run.
- *Hardcoded values* — no new magic numbers of the product-constant kind (confidence tiers etc.); the `1.5.dp`/`3.dp` border-width tweaks are UI dimension constants, consistent with this file's existing inline `1.dp`/`2.dp` styling values.
- *Accessibility* — no new icon-only controls. Trigger chip tap targets are unchanged in size from the existing Cases/Tags chips (a pre-existing sub-48dp pattern this diff doesn't introduce or worsen).
- *Spec review* — `HODITH_SPEC.md` §9 updated: the scroll-order sentence, the chip label format sentence, and a new bullet for the Year chip/filter/border behavior.
- *Tests* — a first pass only covered the happy paths; asked to check coverage explicitly and found real gaps, since fixed. Unit: `BigPictureFilterStateTest` now also covers `filterMonthsByYear` narrowing to a multi-month year and to a year absent from the data (empty result), all four new functions against an empty months list, `bigPictureYearOptions` when every month falls in one year, and `bigPictureYearFilterVisible`'s same-month boundary — not just the happy-path narrow/reverse/visible cases. Instrumented (`BigPictureScreenTest`): beyond chip absent/present, narrowing, and "All years" reset, added the acceptance-criteria regression the prototype itself hit (the current-first month's trailing future week is dropped outright, not blanked — asserted via chevron count), switching directly between two specific years without routing back through "All", the Year filter composing correctly with an active Case filter (day-detail respects both narrowings at once), and Year's Bright-theme dispatch (parity with the existing Cases/Tags Bright coverage).
  - **Third look, on-device run:** asked to actually run the instrumented suite, not just compile it. First on-device run found two real bugs — both in the tests, not production: (1) `yearChip_present_defaultsToAllYears_whenDataSpansMultipleYears` asserted a bare `": All"` text, which matched *both* the Cases and Year trigger chips (both fully-selected at that point) — Compose's `onNodeWithText` doesn't disambiguate; fixed by anchoring on `hasText("Year") and hasText(": All")` together. (2) Two tests asserted `"December 2025 ›"` existed after the grid's month range included it, but `LazyColumn` only composes what's in the current viewport — with 8 unfiltered months and the list freshly scrolled to the top (current-first order), the earliest month sits off-screen at the bottom and simply isn't in the semantics tree yet; fixed with `performScrollToIndex` to the earliest month's index first, matching this file's existing `hasScrollToIndexAction()` precedent (`TriggersScreenTest`/`CaseDetailScreenTest`/`HomeScreenTest`). Root-caused rather than papered over: confirmed via the failure's own node dump that both trigger chips genuinely rendered `": All"` simultaneously (expected, not a bug), and confirmed the "missing" month was a virtualization artifact, not an actual absence, before writing either fix. Separately, the *test run itself* hung for over 30 minutes on the emulator mid-session — traced to the emulator's own adb daemon going `offline` (`protocol fault` on every subsequent shell command), unrelated to this branch's code; recovered by force-killing the wedged `qemu-system`/`emulator` processes and relaunching the AVD fresh (`-no-snapshot-load`), which came back healthy in 15s. All 52/52 `BigPictureScreenTest` tests green on the second, clean on-device run.
  - **Second look, full re-walk:** asked directly whether the checklist had actually been walked — it had, but only once, before the coverage pass above; the other sections hadn't been re-checked against the diff those new tests introduced. Re-walked every section against the real `git diff main` (not from memory): found and fixed a KDoc cross-reference in `BigPictureFilterState.kt` using an unnecessary fully-qualified name within its own package, and one new unit test bundling assertions for three unrelated functions (`filterMonthsByYear`/`bigPictureYearOptions`/`monthsNewestFirst`) into a single test case, split into three to match this file's one-function-per-test convention (now 27/27). Everything else re-confirmed clean on re-check: no stray `git status` files, no new a11y issue beyond the pre-existing sub-48dp trigger-chip tap target this diff extends but didn't introduce, `bigPictureYearFilterLabel`'s shared-default (not per-voice) treatment double-checked against CLAUDE.md's "added to all three voices" rule — satisfied by inheritance, same as the two adjacent keys it mirrors, not a violation. Confirmed via `MANUAL_TEST_PLAN.md` that in-app filters (Cases/Tags) have no entry there either, so Year needs none.

**Deferred:** nothing formally deferred (no checklist item was raised and declined). The instrumented test run noted above is outstanding, not deferred — it needs to happen before this is considered fully verified.

**Docs updated:** `HODITH_SPEC.md` §9 — scroll order, chip label format, new Year-chip bullet. `TESTING.md` — Compose UI — Big Picture row gained the Year filter coverage clause. `PROGRESS.md` — item struck; the separate filter-pill-consistency item's stale cross-reference and line numbers corrected.

**Verified:** `ktlintCheck → lintDebug → testDebugUnitTest (scoped, then full) → compileDebugAndroidTestKotlin → assembleDebug` sequential, all green. `connectedDebugAndroidTest` scoped to `BigPictureScreenTest` on `Pixel_8_API36(AVD)` — 52/52 green on a clean run, after fixing the two test bugs the first on-device run surfaced (see Tests, third look) and recovering the emulator from an unrelated adb hang mid-session.
