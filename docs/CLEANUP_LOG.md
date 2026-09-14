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

## feat/insights-section-info

**Scope:** PROGRESS.md's S16 — added the existing per-section info affordance (`SectionWithInfo`/`InfoDialog`, already used by Frequency and Gaps & streaks) to the Trend and Duration cards on the Case Detail Insights tab. Rhythm and Tags were shortlisted and declined: their content is already self-explanatory (a heatmap grid, a per-tag count against the case total) without a dedicated explanation.

**Changes:**

- `TrendCard` and `DurationCard` (`InsightsTab.kt`) now wrap their content in `SectionWithInfo`, adding a tappable info icon next to each section label — same shape as the existing `GapsCard`/`FrequencyCard` usage, no other logic touched.
- Four new `Voice` keys (`insightsTrendInfoTitle`/`Body`, `insightsDurationInfoTitle`/`Body`) added to all three voices. Duration's copy is a static sentence ("still-running events aren't counted until they stop") rather than S16's suggested dynamic "N of M events had a duration" count — declined on sign-off, to avoid extending `DurationStats`/`DurationDisplay` for an edge-case detail. Trend's copy describes the current 30-vs-30-day comparison and gap/streak shift notes directly, rather than waiting on S2 (an unstarted, separate investigation into whether that math itself should change).

**Checklist walk (against the working-tree `git diff`):**

- *Duplication, decoupling, complexity, hardcoded values, accessibility, deprecated APIs, repo hygiene, naming* — no findings; the change reuses the existing shared component and Voice pattern exactly, no new composables and no strings outside Voice.
- *Dead code & hygiene / tests* — found `CaseDetailInsightsTabTest.gapsCard_infoIcon_opensAndDismissesDefinitions`'s comment ("Frequency and Gaps & streaks are the only two cards with an info icon") had gone stale: it still passed only because that test's fixture (short history, `NONE` duration mode) keeps the new Trend/Duration icons hidden, not because they don't exist. Fixed the comment on sign-off, and added `trendCard_infoIcon_opensAndDismissesDefinitions` / `durationCard_infoIcon_opensAndDismissesDefinitions`, mirroring the existing Gaps coverage — closing the analogous coverage gap the new icons introduced.
- *Spec review* — `HODITH_SPEC.md` §10 describes what Trend and Duration compute, not the info-icon UI affordance (true for Frequency/Gaps already too), so no divergence.

**Deferred:** nothing — Rhythm and Tags were considered and declined rather than deferred (see Scope above), and S16 is otherwise complete, so it's removed from PROGRESS.md rather than struck.

**Docs updated:** `TESTING.md`'s Case Detail Insights coverage row now names the Trend and Duration info icons alongside Gaps'; PROGRESS.md's S16 item removed (fully resolved).

---

## feat/hunch-history-row-redesign

**Scope:** PROGRESS.md's S5. The trigger was a bug — `monthsAgo`/`hunchHistoryRowWhen` reads "0 months ago" for anything resolved inside its first month — but the item called for a full design-and-content pass, prototyped via an Artifact mockup before any Compose changes. The design review itself surfaced a second, unrelated bug: `HunchTabState.EarlyDays`/`Verdict` never carried `history` at all, so a Case's entire resolved-Hunch record disappeared from the screen the moment a new Hunch went active.

**Changes:**

- `HunchTabState`'s `history` moved onto the sealed interface itself (all three subtypes already carried it), so it's read unconditionally in `CaseDetailScreen.kt` rather than only inside the `NoActiveHunch` branch.
- `HunchHistoryRow` rebuilt to lead with a `Made … · Resolved …` stamp (`hunchHistoryRowStamp`, new Voice key ×3) using absolute dates via the existing `formatEventDate` — no live-clock dependency left in the row at all. Rows are separated by `HorizontalDivider`s inside a card now sitting on `MaterialTheme.colorScheme.surfaceVariant` (via a new `containerColor` param on the shared `HunchCard` shell) instead of default surface, so a closed record reads as visibly distinct from the live active-Hunch card above it. No verdict-tier badge in the row — stays only on the live verdict card.
- `hunchHistoryRowOutcome` reworded from a binary held-up/off to three severity tiers (`ABOUT_RIGHT` / `LESS`+`MORE` / `MUCH_LESS`+`MUCH_MORE`) — found during the design review, fixed in this same pass on explicit sign-off rather than deferred. `hunchHistorySummary` and `hunchHistoryRowOutcome` also lost their em dashes (×3 voices each), on request.
- `monthsAgo` (`CaseDetailViewModel.kt`) and `hunchHistoryRowWhen` (×3, `Voice.kt`) deleted outright.

**Checklist walk (against the working-tree `git diff`):**

- *Duplication* — first pass had `HunchHistoryCard` hand-roll its own `Card` + padded `Column` to get a different tone, duplicating `HunchCard`'s existing shell; caught and folded back in via the new `containerColor` param (default unchanged, so every other Hunch-tab card call site is untouched). No inline strings — everything through Voice; `hunchHistoryRowStamp` is the only new key, added to all three voices in this commit.
- *Decoupling* — `HunchHistoryRow` calls `formatEventDate` directly, same convention `formatEventTime`/`formatRate`/`formatExpectedFrequency` already use in this file. No `System.currentTimeMillis()`; `now` is no longer threaded into the history composables at all now that both dates are absolute. No `android.*` in domain code (untouched).
- *Complexity & pattern health* — first pass computed `history` via a value-returning `when` with side-effecting composable calls inside each branch; reworked by lifting `history` onto the interface so `HunchTabContent` goes back to a plain dispatch `when`, `state.history` read once afterward.
- *Dead code & hygiene* — grepped every `monthsAgo`/`hunchHistoryRowWhen` reference; also caught a dangling KDoc mention of `[monthsAgo]` in `TriggersViewModel.kt`'s `triggerRows` doc comment (reworded), and my own new doc-comments citing "(spec S5)" as if it were a stable `HODITH_SPEC.md` section rather than a PROGRESS.md tracker code this same diff struck out — removed from all six spots before they could go stale on merge. The Artifact prototype used to pick the layout lived only in the session scratchpad, never the repo (`git status` confirmed clean).
- *Repo hygiene, naming, hardcoded values, accessibility, deprecated APIs* — no findings.
- *Spec review* — `HODITH_SPEC.md` §7's hunch-history paragraph describes resolve → archive → frozen-verdict at an abstract level, not row layout, so it stays accurate; no update needed.
- *Tests* — `HunchTabStateTest` gained two cases pinning `history` on `EarlyDays`/`Verdict` (the exact visibility bug). `CaseDetailScreenTest` gained a regression guard for the visibility bug and a second case locking the new stamp field's exact rendered text — closing a first-pass gap where the row's actual headline change had no UI coverage at all. `VoiceTest` gained a case pinning the three-severity-tier fix (LESS/MORE now read distinctly from ABOUT_RIGHT and from MUCH_LESS/MUCH_MORE) — the pure-Voice-logic equivalent of a regression test, at the unit level rather than Compose, since `hunchHistoryRowOutcome` has no Android dependency. `CaseDetailFormattingTest`'s two `monthsAgo` cases removed with the function. `VoiceTest`'s existing reflection walk covers every other new/removed key automatically.

**Deferred:** nothing — the one finding from this pass (outcome-text granularity) was fixed in the same branch on explicit sign-off rather than deferred.

**Docs updated:** `PROGRESS.md` — S5 struck in full (all acceptance criteria met), its two cross-references in the Standalone intro and B2's copy-touching list removed.

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green. `connectedDebugAndroidTest` scoped to `CaseDetailScreenTest` — 32/32 green on `Pixel_8_API36(AVD)`, including both new cases.

## feat/log-tab-paged-query

**Scope:** PROGRESS.md's F4 — the Log tab's `CaseDetailViewModel.uiState` read a Case's entire event history (`EventDao.observeEventsWithTagsForCase`) and re-sorted it all in memory (`sortEventsForLog`) into one `LazyColumn` on every recomposition. The request was specifically to show the 30 most recent events with a "Show more" button (+50 per tap), and to have that genuinely resolve F4 rather than only capping what's rendered — so the fix is a real capped/sorted SQL query, not a client-side window over the full list.

**Changes:**

- **Two new `EventDao` queries** (`observeEventsWithTagsForCasePagedByStart`/`...PagedByEnd`), each `LIMIT`-capped and ordered in SQL rather than in Kotlin. `PagedByEnd` is the one genuinely new bit of SQL in this DAO — a conditional `CASE WHEN ... END DESC` term floating a running `START_STOP` event first. Two single-purpose queries rather than one dynamic-`ORDER BY` query keyed on sort order, matching this DAO's existing small/single-purpose style.
- **New `data/LogSortOrder.kt`** (moved out of `viewmodel/LogSort.kt`) and **new `data/LogEventsPage.kt`** (`events` + `hasMore`). `LogSortOrder` had to move because `RoomHodithRepository` now branches on it to pick a DAO method, and `data` can't depend on `viewmodel`.
- **`HodithRepository.observeLogEventsForCase(caseId, order, limit, durationMode)`** — additive; `observeEventsWithTagsForCase` is untouched and still serves `ShareViewModel`/`CaseEditViewModel`/`LogDetailScreenViewModel`, plus `CaseDetailViewModel`'s own ongoing-event detection, Insights/Hunch stats, and the Log tab's summary line (`eventCount`/`observedDays`, which needs the *earliest* event — a capped query could never supply that). `RoomHodithRepository` fetches `limit + 1` and trims to compute `hasMore`, no separate `COUNT(*)` subscription. `FakeHodithRepository` mirrors the same sort/trim logic in Kotlin.
- **`CaseDetailViewModel`** gained `logSortOrder`/`logLimit` `MutableStateFlow`s and a `logPage` flow (`combine().distinctUntilChanged().flatMapLatest { repository.observeLogEventsForCase(...) }` — this codebase's first use of `flatMapLatest`, needing `@OptIn(ExperimentalCoroutinesApi::class)`), layered onto the existing 5-way `combine` via two more `.combine()` calls rather than restructuring it. `CaseDetailUiState` gained `logEvents`/`logHasMore`/`logSortOrder`; `setLogSortOrder` also resets the window back to 30 (a re-sorted list reads as a fresh top-30, not whatever window size was earned under the old order); `loadMoreLogEvents` grows it by 50, one-directional.
- **`CaseDetailScreen.kt`**: `logSortOrder` moved out of local `remember` (it now has to be a query bind parameter, not just display order) into the ViewModel; `LogTabContent` renders `uiState.logEvents` directly instead of computing `sortEventsForLog(uiState.events, ...)`; a "Show more" `TextButton` appended as a trailing `LazyColumn` item when `logHasMore`, styled/placed like `InsightsTab.kt`'s `CalendarHeatmapCard` toggle (borrowing the idiom, not its all-in-memory expand/collapse model, since this load is cumulative).
- **New persona-styled `Voice.logShowMoreAction`** (all three voices, same commit).
- **Deleted** `viewmodel/LogSort.kt` and `LogSortTest.kt` — `sortEventsForLog`'s logic is superseded by the SQL queries; its 8 ordering-contract cases were re-derived as `EventDaoTest`/`RoomHodithRepositoryLogEventsTest` cases against the real queries (not a 1:1 port — see *Tests* below for the second pass that actually checked this rather than asserting it).

**Checklist walk (against the working-tree `git diff`):**

- *Duplication* — no composables/styling repeated; `logShowMoreAction` is the only new Voice string, added to all three voices in this commit. `observeLogEventsForCase` doesn't overlap `observeEventsWithTagsForCase` — it's the Kotlin-side sort (`sortEventsForLog`) pushed into SQL, which is the checklist's explicit "duplicate query with a Kotlin-side filter" direction, not a violation of it.
- *Decoupling* — `LogTabContent` no longer computes a sort order client-side; it just renders what the ViewModel already sorted. `LogSortOrder`/`LogEventsPage` are plain `data/` types, no `android.*`. `RoomHodithRepository`/`EventDao` reference nothing above `data/`, which is exactly why `LogSortOrder` moved out of `viewmodel/` rather than staying there. No `System.currentTimeMillis()` introduced; no domain-layer code touched.
- *Complexity & pattern health* — `LogTabContent` stayed well under 150 lines. The new `logPage`/`uiState` flow chain is denser than the rest of this ViewModel (`flatMapLatest` is new to the codebase) but each step is commented and it's a standard "restart downstream on a key change" operator, not a bespoke one. No new `remember`/`LaunchedEffect` — `logSortOrder` moving out of `remember` into `MutableStateFlow` state was necessary once it became a query parameter, not incidental.
- *Dead code & hygiene* — `sortEventsForLog`/`LogSortOrder` (viewmodel) and `LogSortTest.kt` deleted outright, not left dangling; confirmed no remaining references (`grep`). No unused imports (ktlint/lint clean). New `.kt` files were LF; converted to CRLF to match the repo convention (`feat/log-sort-by-end`/`refactor/big-picture-lean-projections` hit the same thing). HODITH_SPEC.md's new bullet states current behaviour only, no historical narration.
- *Repo hygiene* — two new files, no secrets, no local paths; `git status` matches the intended diff exactly.
- *Naming* — `LogEventsPage.kt`/`LogSortOrder.kt` sit in `data/` next to `CaseEventSpan.kt`/`EventWithTags.kt`, same projection-file convention. `observeLogEventsForCase`/`observeEventsWithTagsForCasePagedByStart`/`...PagedByEnd` follow the existing `observe*` convention.
- *Hardcoded values* — `LOG_INITIAL_LIMIT = 30`/`LOG_LOAD_MORE_INCREMENT = 50` are paging mechanics, not a product truth like a confidence tier or nudge threshold, so they stay private `const val`s on `CaseDetailViewModel` (same treatment as `InsightsTab.kt`'s `HEATMAP_DEFAULT_MONTH_COUNT`), not the domain layer.
- *Accessibility* — the Show More button is a labelled `TextButton` (no icon-only target), same component `CalendarHeatmapCard`'s existing toggle already uses.
- *Deprecated APIs* — `flatMapLatest` needed `@OptIn(ExperimentalCoroutinesApi::class)`, applied narrowly to the `logPage` property; `lintDebug` clean after.
- *Spec review* — HODITH_SPEC.md §6 gained a bullet for the 30-initial/+50-per-tap capped-query behaviour, next to the existing Started/Ended sort bullet it now depends on.
- *Tests* — `EventDaoTest` (11 new cases: cap + id-tiebreak for `PagedByStart`; for `PagedByEnd` — running-floats, non-`START_STOP` ignores running-state, `MANUAL` end-less fallback, a cap check, several-running-events-newest-first, finished-events-ordered-by-endedAt-not-start, occurredAt tiebreak when endedAt ties, and a pure id tiebreak when both tie), new `RoomHodithRepositoryLogEventsTest` (4 cases, against a real Room DB rather than the DAO's raw boolean parameter or `FakeHodithRepository`'s separate reimplementation: cap+`hasMore`, `hasMore` false at the exact boundary, `BY_END` dispatch, and the `durationMode == START_STOP` mapping actually gating the floating behaviour), `FakeHodithRepositoryTest` (cap/`hasMore`/boundary/BY_END-gating), `CaseDetailViewModelTest` (window caps at 30, grows by 50, sort-order reset, `hasMore`), `CaseDetailScreenTest` (Show More hidden/shown/tap, a `logEvents`-vs-full-`events` regression guard, the existing "tap Ended" test reworked to assert the callback rather than re-prove reordering now that the screen is purely a renderer of what the ViewModel already sorted) — plus a two-line fix to `CaseDetailInsightsTabTest`'s direct `CaseDetailScreen(...)` call site, the one other place in the repo besides `CaseDetailScreenTest` that constructs the screen directly, for the two new required callback params. `connectedDebugAndroidTest` run scoped to the touched classes on `Pixel_8_API36(AVD)`: `EventDaoTest` 27/27, `RoomHodithRepositoryLogEventsTest` 4/4, `CaseDetailScreenTest` + `CaseDetailInsightsTabTest` 61/61, all clean. `ktlintCheck → lintDebug → test → assembleDebug` all green.
  - **Correction, second pass:** the first pass claimed `LogSortTest.kt`'s 8 deleted cases "moved to `EventDaoTest`" without checking case-by-case — asked to verify, 3 had no real equivalent (several-running-events ordering, finished-events-ordered-by-end-not-start, and an id tiebreak for `PagedByEnd`), and `RoomHodithRepository.observeLogEventsForCase`'s own dispatch/mapping/trim logic had no coverage against a real Room DB at all (only the DAO's raw output and `FakeHodithRepository`'s separate code). Both gaps are what the cases above close.

**Deferred:** nothing — F4's own acceptance criteria (capped query, sort in SQL) are both satisfied by this branch, so the item is struck from PROGRESS.md rather than reworded.

**Docs updated:** `PROGRESS.md` — F4 struck; its `## Performance` section (F4 was its only item) removed along with the "how this file is organised" bullet that named it, and D1's now-dangling cross-reference to that section reworded. `HODITH_SPEC.md` §6 — new bullet for the capped-query/Show More behaviour. `TESTING.md` — ViewModels row (`sortEventsForLog` clause replaced with the paging description), Room DAOs row (new paged queries), Compose UI row (Show More coverage, "tap Ended" now described as callback-only).

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green. `connectedDebugAndroidTest` scoped to `EventDaoTest`, `RoomHodithRepositoryLogEventsTest`, `CaseDetailScreenTest`, and `CaseDetailInsightsTabTest` — all green on `Pixel_8_API36(AVD)`.

## refactor/big-picture-lean-projections

**Scope:** PROGRESS.md's F2 — `BigPictureViewModel` subscribed to `observeActiveCasesWithEventsAndTags()`, a `@Transaction @Relation` cascade (cases → events per case → tags per event via the `event_tags` junction, chunked `IN (...)` fetches, full hydration), the heaviest query in the app, refetched on every `events` / `event_tags` / `tags` / `cases` write. The acceptance criteria called for month-range windowing immediately; this pass did the lower-risk fix first (query shape, not volume, was the measured cost) and deferred windowing to real alpha usage instead of a synthetic probe — item renumbered D1, moved to a new Deferred section.

**Changes:**

- **Two new lean flat projections, mirroring `observeActiveCaseEventSpans` (670b605).** `EventDao.observeActiveCaseEventDetails()` — `events JOIN cases WHERE archived = 0`, carrying `id`/`caseId`/`occurredAt`/`endedAt`/`intensity`/`note` (new `CaseEventDetail`). `TagDao.observeActiveCaseEventTagNames()` — `event_tags JOIN tags JOIN events JOIN cases WHERE archived = 0`, one flat `(eventId, tagName)` row per attachment (new `EventTagName`), replacing the junction's chunked `IN (...)` + nested `TagEntity` hydration.
- **`bigPictureUiState` takes the three lean inputs (`cases: List<CaseEntity>`, `eventDetails`, `tagNames`) instead of `List<CaseWithEventsAndTags>`**, grouping by `caseId`/`eventId` itself rather than receiving pre-nested data. Case-level gating (`durationMode.tracksDuration`, `intensityEnabled`) stays applied from the case list already in scope — deliberately not duplicated onto `CaseEventDetail` the way `CaseEventSpan` carries its own `durationMode`, since Big Picture's mapper (unlike Home's) already holds the full case list for other reasons. Output shape (`CalendarEvent.tags` included) is unchanged, so the Compose layer (grid tag filter, `allTagNames` visible-case scoping, detail-dialog pills) needed no changes.
- **`BigPictureViewModel.uiState` gained `.flowOn(Dispatchers.Default).conflate()`**, matching `HomeViewModel` (previously absent here — a rapid-logging burst touched the main thread).
- **Dead code removed:** `CaseDao.observeActiveCasesWithEventsAndTags()`, `CaseWithEventsAndTags.kt` (single call site), `CaseWithEventsAndTagsTest.kt`.

**Checklist walk (against the working-tree `git diff`):**

- *Duplication* — no composables, styling, or Voice strings touched. New DAO queries don't overlap existing ones — they replace the one query with a Kotlin-side nest, pushing that nesting into two flat SQL projections instead.
- *Decoupling* — `CaseEventDetail`/`EventTagName` are plain `data/` types, no `android.*`, no UI types; a first-draft KDoc cross-link from `CaseEventDetail` into `viewmodel.CalendarCase` was reworded to stay in prose rather than a cross-layer `[Link]`. `BigPictureViewModel` still takes no UI types; `Clock.nowMillis()` still the only time source.
- *Complexity & pattern health* — `combine` grew from 2 to 4 flows, same shape as `HomeViewModel`'s existing 3-flow `combine` with `.flowOn(Dispatchers.Default).conflate()`; no composable or `remember` touched.
- *Dead code & hygiene* — the throwaway JVM mapper probe (median ~10ms over 40 cases / 30k events / 75k tag attachments — confirms the Kotlin-side grouping is cheap regardless of Room) was written, run, and deleted; `git status` clean of it. No unused imports (ktlint clean after `ktlintFormat` auto-wrapped one long line in the new DAO test).
- *Repo hygiene* — three new `.kt` files, no secrets, no local paths; `git status` matches the intended diff.
- *Naming* — `CaseEventDetail`/`EventTagName`/`observeActiveCaseEventDetails`/`observeActiveCaseEventTagNames` follow the `observe*` / projection-type conventions next to `CaseEventSpan`/`observeActiveCaseEventSpans`.
- *Hardcoded values / accessibility / deprecated APIs* — n/a: no UI, no constants, lint clean.
- *Spec review* — HODITH_SPEC.md §17's cross-case co-occurrence Future Work item cited `observeActiveCasesWithEventsAndTags` as existing plumbing; updated to name the two new projections instead.
- *Tests* — `BigPictureViewModelTest` rewritten for the flat-projection inputs (all ~20 cases kept, same assertions); new `BigPictureQueriesTest` (androidTest, 4 cases, one `@Smoke`) for the two new queries; `FakeHodithRepository` gained matching `combine`-based implementations. `BigPictureScreenTest` / `BigPictureFilterStateTest` needed no changes (they operate on `CalendarEvent`/pure filter logic, untouched by this refactor). `connectedDebugAndroidTest` ran on a rebuilt `Pixel_8_API36` emulator: 235/237 clean first pass, all 48 Big Picture–related tests (including the new `BigPictureQueriesTest`) among them; the 2 failures (`SettingsScreenTest.backupEvent_showsMatchingSnackbarMessage`, `.developerMode_hiddenByDefault`) are in a file with zero diff on this branch, matched a signature already logged multiple times elsewhere in this file as emulator-load `ActivityScenario` teardown flakiness, and passed clean on a scoped rerun — confirmed flake, not a regression.

**Deferred:** whether Stage 2 (windowing) is needed at all — needs a synthetic S6-scale probe with no real usage behind it, so it's held for real alpha usage to decide instead (PROGRESS.md's D1).

**Docs updated:** `PROGRESS.md` — F2 rewritten and moved to a new `## Deferred` section as D1, with the deferral rationale; the file's own "how this is organised" list gained a Deferred bucket; the Log tab item's plan note reworded to drop its now-stale dependency on this one. Also added S16 (chase the recurring `ActivityScenario` teardown flake — see above) to Standalone. `HODITH_SPEC.md` §17 — cross-case co-occurrence item's plumbing citation updated. `TESTING.md` — Room DAOs row (lean Big Picture projections) and ViewModels row (tag sort-order parenthetical).

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green. `connectedDebugAndroidTest` green (2 confirmed-flaky failures unrelated to this branch, detailed above).

## fix/rapid-log-debounce

**Scope:** the S6 review's F6 — a rapid quick-log burst fanned out one un-debounced `evaluateNotificationsForCase` coroutine per tap (each several DAO reads + a possible `triggers` write, all on the one SQLite connection), and `HomeViewModel._quickLogUndo` (`Channel.BUFFERED`) back-pressured its producers on `send` past 64 unconsumed items.

**Changes:**

- **New `NotificationEvalScheduler` (`notification/`).** `@Singleton`, owns the app `CoroutineScope` + `Provider<NotificationEvaluator>`; `schedule(caseId)` keeps at most one pending `Job` per Case behind a lock, replaced on each new request, firing `evaluateCase` after `EVAL_DEBOUNCE_MILLIS = 300`. `RoomHodithRepository` drops its `Provider<NotificationEvaluator>` + `CoroutineScope` constructor params for this one dependency; `evaluateNotificationsForCase` now delegates to it. The `~6h` `NotificationEvalWorker` path (`evaluateAll`) is untouched.
- **`_quickLogUndo` → `Channel(capacity = 1, onBufferOverflow = DROP_OLDEST)`.** Only the latest one-tap event is undoable, so `send` never suspends and no producer parks. Consumer (`HomeScreen`) already only needs a `Flow`.

**Checklist walk (against the working-tree `git diff`):**

- *Duplication* — the two androidTest constructors that build `RoomHodithRepository` directly needed the same throwaway scheduler; `RoomHodithRepositoryBackupTest` builds it twice so it got a private `unusedScheduler()` helper, `BackupImportIntegrationTest` builds it once inline in its existing `repositoryFor`. No repeated logic in `main`.
- *Decoupling* — `NotificationEvalScheduler` is coroutines-only, no `android.*`, no `Clock` (it schedules, it doesn't reason about time); the debounce window is infrastructure timing, not a product constant, so it stays a `const` on the class rather than moving to `domain/`. Repository still owns no scope. Trigger/check-in logic in `domain/` is untouched.
- *Complexity & pattern health* — `schedule` is ~10 lines; the `pending[caseId] === job` identity check in the `finally` keeps a cancelled job's cleanup from evicting its replacement's map entry. `MutableSharedFlow` + `debounce` was the AC's parenthetical suggestion but only debounces globally — a per-`caseId` `Job` map is the smaller correct thing (a burst across two Cases still evaluates both). `viewModelScope` / channel usage in `HomeViewModel` unchanged in shape.
- *Dead code & hygiene* — `CoroutineScopeModule`'s KDoc rerouted from `RoomHodithRepository` to `NotificationEvalScheduler` (still its only non-`HodithApplication` user); `RoomHodithRepository` lost 4 now-unused imports (ktlint clean). No throwaway prototype — the change was small enough to build directly. `git status` clean of stray files.
- *Repo hygiene* — two new `.kt` files, LF like every committed source; no secrets, no local paths.
- *Naming* — `NotificationEvalScheduler` sits in `notification/` beside `NotificationEvaluator` / `NotificationEvalWorker`; `schedule` / `EVAL_DEBOUNCE_MILLIS` match the surrounding style. No `Voice` keys touched.
- *Hardcoded values / accessibility / deprecated APIs* — n/a: one named `const`, no UI, no deprecated calls; lint clean.
- *Spec review* — §11 said triggers/check-ins "evaluate immediately on every event insert/edit/delete"; still true, with a clause added that a sub-second per-Case debounce collapses a burst. Intentional, so the spec was updated.
- *Tests* — new `NotificationEvalSchedulerTest` (3, JVM, virtual time): burst on one Case → one evaluation; interleaved distinct Cases → one each; a lone request waits out the window. `HomeViewModelTest` +1: a 70-tap burst with no collector never blocks and leaves only the latest undo actionable. Existing `NotificationEvaluatorTest` / `HomeViewModelTest` green unchanged. The two androidTest backup classes only changed a constructor argument (evaluator still a throwing stand-in, never invoked) — **instrumented re-run pending an emulator**; the JVM suite covers the wiring. No new system-boundary flow → no MANUAL_TEST_PLAN change.

**Deferred:** nothing.

**Docs updated:** `PROGRESS.md` — F6 struck (Performance section now F2 + F4, intro "all three" → "both"). `HODITH_SPEC.md` §11 — debounce clause added. `TESTING.md` — Notification evaluation row (the scheduler's debounce contract) and ViewModels row (undo-burst).

**Verified:** `ktlintCheck → lintDebug → test → assembleDebug` sequential, all green (650 JVM tests, was 646). Instrumented re-run pending the emulator.

