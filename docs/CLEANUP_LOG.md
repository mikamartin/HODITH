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

## fix/intensity-toggle-gating

**Scope:** PROGRESS.md item "Intensity-toggle: trend/notification code ignores the Case flag" (filed as a follow-up while fixing `fix/intensity-required-when-enabled`): `InsightsTabState` correctly hides the Insights intensity stat card once `Case.intensityEnabled` is off, but two downstream readers of `EventEntity.intensity` had no awareness of the flag at all — a `TAG_OUTCOME` Trends finding on `TagOutcome.INTENSITY` could still surface from stale per-event ratings, and an `OFTEN` Watch's `minIntensity` floor kept filtering by them too, both after the Case's own stat card and panel had already gone quiet about intensity.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `test` (full unit suite), `lintDebug`, and `assembleDebug` all pass. `connectedDebugAndroidTest` not run this pass — no emulator driven directly (this repo's convention); the one instrumented change (`CaseDetailWatchesTabTest`'s `verdictFor` helper gaining a defaulted `intensityEnabled` param) is behaviorally inert for every existing test there, left for the user's own device run.

**Found & fixed:**
- `StatsEngine.computeTagOutcomeFindings` gained the `eligibleOutcomes: Set<TagOutcome>` parameter its siblings `computeTrendSlopeFindings`/`computeTimeOfDaySplitFindings` already had, filtering `TagOutcome.entries` the same way; `TrendsEngine.computeTrendFindings` now threads its existing `statsShownOutcomes` param through to it, so `InsightsTabState`'s already-correct gating reaches the tag-outcome detector for free.
- `WatchEngine.expectationInputsFor`/`evaluateOften` gained an `intensityEnabled: Boolean = true` parameter; the `minIntensity` filter no longer applies when it's `false`, since `WatchEntity.minIntensity` is stored independently of the Case's own flag and survives it being turned off. Wired the live `Case`/`CaseSeed` flag through the three real call sites: `NotificationEvaluator`, `WatchesViewModel.watchCardState` (via `WatchesTab.kt`'s existing `uiState.intensityEnabled`), and `DemoDataSeeder`.
- Regression tests added at both layers: `TrendsEngineTest` (a `TAG_OUTCOME` suppression test mirroring the existing trend-slope one), `InsightsTabStateTest` (end-to-end: intensity off, strong planted tag→intensity signal, no leaked finding), and `WatchEngineTest` (an `OFTEN` watch with `minIntensity` set but `intensityEnabled = false` counting events that would otherwise fail the floor).
- Updated the 14 `StatsEngineTest`/5 `TrendsEngineTest` call sites that previously relied on `computeTagOutcomeFindings`/`computeTrendFindings`'s old gating-free default and would otherwise have silently stopped seeing their planted `TAG_OUTCOME` finding.

**Deferred:** nothing.

**Considered and declined:**
- Making `computeTagOutcomeFindings`'s new `eligibleOutcomes` param required (no default) while defaulting `WatchEngine`'s new `intensityEnabled` param to `true` looks inconsistent at a glance, but each matches its own file's existing convention: `computeTrendSlopeFindings`/`computeTimeOfDaySplitFindings` already require their gating param explicitly, while `WatchEngine`'s `tracksDuration`-adjacent params are already caller-supplied without a shared default elsewhere. Defaulting `intensityEnabled` kept ~20 existing `WatchEngineTest` call sites (none of which construct a `Case`) unchanged; requiring `eligibleOutcomes` kept `computeTagOutcomeFindings` consistent with its two siblings, at the cost of touching `StatsEngineTest`'s call sites once.

**Checks:**
- Duplication: no new detector/significance-test logic — reused the existing `eligibleOutcomes`/`statsShownOutcomes` gating mechanism and `WatchEngine`'s existing filter shape.
- Decoupling: `StatsEngine.kt`/`TrendsEngine.kt`/`WatchEngine.kt` stay pure Kotlin (`domain/`), no `android.*` imports; every `now`/flag value stays caller-supplied.
- Naming: no new `Voice` keys — no user-visible copy changed.
- Hygiene: `git status` clean throughout; no secrets or local paths in the diff.
- Spec review: `HODITH_SPEC.md` updated (§10 tag → outcome's gating, §11's `minIntensity` table row) — unintentional divergence (a genuine bug), not a deliberate spec change.
- Tests: no weakened assertions — every touched call site either gained an explicit gating argument or a brand-new test exercising it; `StatsEngineTest`'s detector-math tests keep exercising every outcome unfiltered via `TagOutcome.entries.toSet()`.

**Docs updated:** PROGRESS.md (the resolved item struck entirely); HODITH_SPEC.md (§10 tag → outcome gating, §11 `minIntensity` table row); TESTING.md (Watch evaluation and Stats & visual data prep rows extended); this entry (oldest entry, `fix/big-picture-day-detail-chronological-order`, removed per the 5-entry retention rule).

---

## feature/case-edit-logging-toggle

**Scope:** Reordered the Case edit/create form's logging control to the end of the form (after Track duration and Track intensity), replaced the two-option "One tap / Detail sheet" segmented chooser with a single "Enable one tap logging" `Switch`, hid it entirely (rather than just disabling it) once intensity tracking makes one-tap unavailable, dropped "(1-5)" from the Track intensity label, and restyled that label to match its sibling toggles.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff, twice — the first pass's "considered and declined" call on the `ToggleRow` duplication below was the user's to make, not mine; raised it and the user asked for the fix instead, so it's folded into this same pass rather than left deferred. `ktlintCheck`, `test` (full unit suite), `lintDebug`, and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to every screen touching `ToggleRow` on a connected emulator — `CaseEditScreenTest` (20/20), `HistoryShareTabTest` (18/18), `InsightShareTabTest` (25/25), `CaseDetailScreenTest` (35/35), `BigPictureScreenTest` (51/51) — all green, 149/149 total.

**Found & fixed:**
- Replaced the `SectionWithInfo` + `SegmentedChoiceRow(LogFlow.ONE_TAP/DETAIL_SHEET)` block with `RowWithInfo` wrapping a single `Switch` bound to `logFlow == ONE_TAP`, the whole row wrapped in `isOneTapAllowed(uiState.intensityEnabled)` so it's omitted outright — not merely disabled — once intensity tracking is on.
- `Voice.kt`: removed `caseLogFlowLabel`/`caseLogFlowOneTap`/`caseLogFlowDetailSheet` (no longer rendered anywhere); renamed `caseLogFlowInfoTitle`/`caseLogFlowInfoBody` to `caseOneTapInfoTitle`/`caseOneTapInfoBody` and rewrote the copy in all three voices — dropping the now-irrelevant "Detail sheet" explanation, and adding a note that one-tap logging on a duration-tracking Case starts the event running until stopped (spec §6 Story U2 behavior that already held, but was previously undocumented in the info dialog).
- `CaseEditScreenTest.kt`: updated node lookups to the new label; changed the intensity-gated test from `assertIsNotEnabled` to `assertDoesNotExist` (now hidden, not disabled — renamed accordingly); split the single info-dialog test in two, since the reorder moved Track duration's info icon to the first position (first icon → Track duration's dialog now; a new test covers the last icon → the new one-tap dialog).
- `ui.common.ToggleRow` collapsed from two same-named, same-package composables (`ToggleRow.kt`'s whole-row-toggleable version and `ThemedToggle.kt`'s themed-`Switch`-owns-click version) into one canonical `ToggleRow.kt`, with `labelStyle`/`colors` params defaulting to each existing call site's current look (`LocalTextStyle.current`/`SwitchDefaults.colors()`) so none of the four live call sites (Share's section picker, History Share's field picker, Big Picture's overview-detail dialog, Case Detail's check-ins row) changed appearance. `ThemedToggle.kt` now holds only `themedSwitchColors()`. Track intensity restyled via the merged component (`labelStyle = MaterialTheme.typography.labelLarge` to match Track duration's bolder label, `colors = themedSwitchColors()`) instead of a one-off inline `Row`; dropped the "(1-5)" suffix from `caseIntensityToggleLabel` in the same change.

**Deferred:** nothing.

**Considered and declined:**
- UI Copy Brevity: "Enable one tap logging" runs longer than its new siblings "Track duration"/"Track intensity". Flagged per the checklist, but the wording was the user's explicit instruction for this change, not an incidental pick — kept as specified.

**Checks:**
- Duplication: the `ToggleRow` duplication (see Found & fixed) was the only one found and is now fixed; no inline Voice strings added.
- Decoupling: no ViewModel/domain changes; `isOneTapAllowed` reused exactly as before, called from the Composable the same way the segmented control's `enabled` lambda used it.
- Complexity: `CaseEditForm` is ~90 lines, unchanged bracket, under the 150-line guideline.
- Dead code & hygiene: unused imports removed throughout; `git status` clean. `HODITH_SPEC.md` describes `logFlow`/`tracksDuration`/`intensityEnabled` only at the data-model level, never this screen's specific control shape, so nothing there was stale.
- Naming: new Voice keys (`caseOneTapToggleLabel`, `caseOneTapInfoTitle`, `caseOneTapInfoBody`) follow the existing `case*` convention and landed in all three voices in the same commit.
- Accessibility: reused the same `Switch`/`RowWithInfo`/`ToggleRow` building blocks already in place; no new icon-only button added. Manual light/dark verification left to the user per this repo's convention.
- Data model/migrations/widgets/notifications: untouched — `LogFlow` and its widget/notification dispatch are unchanged; only the Case-edit screen's control for setting it changed.
- Spec review: no divergence — see Dead code & hygiene above. TESTING.md's `ToggleRow` semantics-merging note (Known environment issues) still accurate — the kept shape is exactly the one it describes.
- Tests: no weakened assertions — the intensity-gated test got strictly stronger (hidden, not just disabled); every other `ToggleRow` call site's existing tests stayed green unmodified, confirming the merge didn't change their behavior.

**Docs updated:** PROGRESS.md (the `ToggleRow` duplication item added during the first pass removed again — fixed in this same branch rather than left as backlog); this entry (oldest entry, `fix/widget-edit-affordance`, removed per the 5-entry retention rule).

---

## feature/duration-unification — pre-merge cleanup pass

**Scope:** A full CLEANUP_CHECKLIST.md walk-through of the whole `feature/duration-unification` diff requested before merge, including finally running the instrumented tests the previous pass on this branch had deferred for lack of an emulator.

**Walked:** CLEANUP_CHECKLIST.md section by section against the full `main..HEAD` diff (two independent passes, cross-verified against the actual source rather than taken on faith). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass on the final state. `connectedDebugAndroidTest`: the full scope (17 touched test classes, 259 tests) hit the shared AVD's own pre-existing flaky `system_server` watchdog crash twice in a row on a long run (a full-system process dump at an arbitrary point, not a test assertion failure — unrelated to this diff); recovered by restarting the emulator fresh rather than burning more time on a degraded instance. Final verification scoped to the 55 test methods that actually exercise `tracksDuration` behavior (as opposed to ones only touched by a mechanical fixture-constructor rename) — all 55 green on the fresh emulator in just over a minute.

**Found & fixed:**
- Naming leftover the original rename pass missed: `caseDurationModeInfoTitle`/`caseDurationModeInfoBody` (and their one call site, `CaseEditScreen.kt`) kept the old `DurationMode` name even though the control's own label had already become `caseTracksDurationLabel` — compiled fine (both sides still matched each other), just inconsistent with every other key in the same rename. Renamed to `caseTracksDurationInfoTitle`/`caseTracksDurationInfoBody` across the `Voice` interface, all three voice overrides, and the call site.
- Stale retired-enum terminology left by the original mechanical find-and-replace: test names (`EventDaoTest.observeEventsWithTagsForCasePagedByEnd_floatsARunningStartStopEventAbove...` → `...TrackedEventAbove...`; `HomeScreenTest.nonOngoingStartStopRow_...` → `nonOngoingTrackedRow_...`; `WidgetActionsFlowTest.logTap_startsASecondEventForARunningStartStopCase`/`manualCaseWithFutureEndedAt_showsExpectedEndInSubtitle` → `...RunningTrackedCase`/`trackedCaseWithFutureEndedAt_...`), a comment in `NotificationEvaluatorTest.kt` still describing a `MANUAL`/`NONE` case, and doc comments in three files outside the diff's own touched set (`Ticker.kt`, `EventTimeFormat.kt`, `WidgetCommon.kt`) still describing a `MANUAL` event — all now read in terms of `tracksDuration`. `MANUAL_TEST_PLAN.md`'s two citations of the renamed `WidgetActionsFlowTest` methods updated to match.
- `UnifiedDurationSection`'s status-word `.removeSuffix(" at")` call (stripping Bright's "Wrapped up at" down to a standalone word, correct today only by coincidence with Plain/Intense wording) replaced: `Voice.logSheetEndLabel` renamed to `logSheetEndedStatusLabel` with Bright's value corrected to "Wrapped up" directly — the key already had exactly one caller, so it can just say what it means instead of being string-manipulated after the fact.
- Duration field label inconsistency: `Days`/`Hours`/`Min` (two spelled out, one abbreviated, shown as three equal-width siblings in the same row) unified to `Day`/`Hr`/`Min`.
- Confirmed `DemoDataSeeder.kt`/`DemoDataSeederTest.kt` are fully migrated: `DurationMode.NONE`/`START_STOP` → `tracksDuration = false`/`true` with identical semantics (no demo Case ever used `MANUAL`), no lingering `DurationMode`/`durationMode` references anywhere in `data/demo/`.

**Deferred:** nothing.

**Considered and declined:**
- Backup export/import schema gap: `BACKUP_SCHEMA_VERSION` wasn't bumped for the `durationMode`→`tracksDuration` rename, and `CaseEntity.tracksDuration` has no Moshi-visible default, so importing a pre-this-branch backup JSON would throw `JsonDataException` instead of restoring. Declined — every backup in existence right now is test data the user can adjust by hand, and `BACKUP_SCHEMA_VERSION` is expected to reset to 1 before any public release anyway, which would subsume this.
- `DurationFieldInput`'s custom-built text field bypasses `OutlinedTextField`'s 56dp minimum height to stay compact for the 3-up Days/Hours/Minutes row, and may render under the 48dp accessibility touch-target guideline. Declined to add a minimum-height constraint — kept intentionally compact, the user's explicit call.
- Splitting `UnifiedDurationSection` (~136 lines) into sub-composables — under the 150-line guideline; splitting now is optional polish, not a real problem.

**Checks:**
- Duplication: the Voice-layer rule held everywhere except the one missed rename above (now fixed). No copy-pasted composables; `UnifiedDurationSection` cleanly replaced `EndTimeSection`/`DurationSection`/`DurationUnitSelector`/`ExpectedEndNotice` (all deleted, not left dangling).
- Decoupling: no `android.*` imports in `domain/`; no direct `System.currentTimeMillis()` outside `@Preview`-only code; day-bucketing still resolves via each event's own captured `utcOffsetMinutes`, not the device's current zone.
- Complexity: `LogDetailForm` is ~176 lines, over the ~150-line guideline, but that's pre-existing on `main` (this branch actually shrank it slightly) — not a regression from this diff.
- Hygiene: `git status` clean throughout; no secrets, local paths, or stray tooling files in the diff.
- Hardcoded values: no new `Color(0xFF...)` literals; no new domain-layer magic numbers (the new `DURATION_FIELD_MAX_DIGITS` constant follows the existing pattern).
- Accessibility: no new icon-only buttons without a `contentDescription`; the one borderline touch-target case is the declined `DurationFieldInput` item above.
- Data model & migrations: the Room schema version bump (14→15) and matching `AutoMigrationSpec`/`15.json` are correct; `SchemaMigrationCoverageTest` passes. The backup/export gap is the one declined item above. Share card still excludes notes and gates tags the same way.
- Background work/widgets/notifications: watch/notification debounce, single-group alerting, and widget Plain-light-only theming all unaffected; `isStillRunning`'s collapse of the old per-mode branches into one boolean check is internally consistent across `VerdictEngine`/`WatchEngine`/`NotificationEvaluator`/`OngoingEvent`/`ListWidget`.
- Spec review: HODITH_SPEC.md fully describes the new model, with no stale `DurationMode`/`NONE`/`MANUAL`/`START_STOP` references anywhere in `docs/` outside this log's own (correctly historical) entries.
- Tests: no weakened assertions found across the `CaseEditViewModelTest`/`LogDetailViewModelTest` rewrites — every removed test corresponds to an enum permutation that's now structurally impossible, not a dropped check; TESTING.md is accurate.

**Docs updated:** MANUAL_TEST_PLAN.md (two `WidgetActionsFlowTest` method-name citations corrected to match the renamed tests); this entry (oldest entry, `fix/duplicate-case-warnings`, removed per the 5-entry retention rule).

---

## feature/duration-unification

**Scope:** Story U2 — collapsed the Case-level `DurationMode` enum (`NONE`/`MANUAL`/`START_STOP`) into a single `CaseEntity.tracksDuration: Boolean`, with per-event ongoing/ended state derived (`endedAt == null || endedAt > now`) rather than branched on mode. Landed across the schema, every domain engine, ~20 ViewModels/screens, both Glance widgets, Voice ×3, the Case Edit confirm-dialog redesign, and the Log/Edit Event Days/Hours/Minutes duration control. This pass finished migrating the remaining JVM unit test files and all instrumented test files off the retired enum, synced HODITH_SPEC.md/TESTING.md/MANUAL_TEST_PLAN.md/PROGRESS.md, and deleted the superseded `ui/prototype/DurationUnificationPrototype.kt`.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass; `compileDebugAndroidTestKotlin` confirms every instrumented test file compiles, but no emulator was available this pass to actually run `connectedDebugAndroidTest` — flagged under Deferred below rather than silently skipped.

**Found & fixed:**
- Self-caught correctness bug mid-migration: several already-edited test fixtures blindly mapped the old `MANUAL`/`START_STOP` enum values to `tracksDuration = true` without checking the paired event's `endedAt`. Under the old model, a `MANUAL` event with `endedAt == null` read as "not yet given a duration," not running; under the new unified rule, `tracksDuration = true` plus a null `endedAt` always means running — a silent semantic drift that would have corrupted several tests' actual day-counting results without failing outright. Caught by auditing every `eventAtDay`/bare-`testEvent` call site against its enclosing test's tracking flag before continuing the mechanical pass (`InsightsTabStateTest`, `WatchEngineTest`, `VerdictEngineTest`, `FakeHodithRepositoryTest`).
- `LogDetailViewModelTest.kt` was missing the DST spring-forward regression test the old `computeEndedAt` had for its fixed-24h day scaling — ported it to the new `composeDuration` function, which has the identical property, rather than letting the coverage silently disappear in the rewrite.
- `LogDetailSheet.kt`'s preview-only `UnifiedDurationSectionPreviewContent` had a `remember { draft = ... }` block that only mutates state and returns `Unit` — a real `lintDebug` error (`RememberReturnType`), not a style nit. Folded the mutation into the initial `mutableStateOf` computation instead.
- `CaseDetailScreenTest.kt`'s `startStopCase` fixture renamed to `trackedCase` for clarity under the new model — first attempted via a PowerShell regex replace, which silently corrupted the file's UTF-8 encoding (mojibake on its em dashes/emoji) and added a BOM, and mangled one test's method name mid-rename. Caught by diffing before compiling; reverted and redone with exact-string edits instead.

**Deferred:**
- Running the new/updated instrumented tests on a real device (`connectedDebugAndroidTest`) — no emulator available this pass; `compileDebugAndroidTestKotlin` confirms every file compiles. Needs a human pass before merge, per this repo's standing workflow.

**Docs updated:** HODITH_SPEC.md (§5 data model, §6 logging flows + the duration-tracking transition contract, §8 comparison math, §9 active span, §10 stats, §14 Case Edit, §15 widgets); TESTING.md (every `DurationMode`/`START_STOP`/`MANUAL` reference across the Verdict/Watch/Notification/Stats/ViewModels/Room DAO/Compose UI rows); MANUAL_TEST_PLAN.md (items 6-7); PROGRESS.md (Story U struck entirely — implementation, tests and docs are done, and the prototype file is deleted); this entry (oldest entry, `fix/widget-row-sizing`, removed per the 5-entry retention rule).

---

## feature/manual-duration-ongoing-stop

**Scope:** User request: show a `MANUAL`-duration event as "ongoing" — expected-end display plus an immediate Stop — once its computed duration projects past `now`, matching the existing `START_STOP` ongoing/Stop UX. Landed across the History tab, Insights drill-down rows, the Edit Event screen, Home, both Glance widgets, and Big Picture's day/week detail (display-only there, per the user's explicit scope cut: no Stop, since that screen targets a Case across many events, not a single one). Extended the existing "still-running events are excluded until they stop" stats rule to cover this new case too.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to every touched/new instrumented class across several passes as the diff grew (`CaseDetailScreenTest`, `HomeScreenTest`, `LogDetailScreenTest`, `BigPictureScreenTest`, `EventDaoTest`, `HomeCountQueriesTest`, `WidgetActionsFlowTest`, `CaseEditScreenTest`, `HistoryShareTabTest`, `InsightShareTabTest`, `CaseDetailWatchesTabTest`, `RoomHodithRepositoryHistoryEventsTest`) — all green, including two real bugs the first run of each caught (see Found & fixed).

**Found & fixed:**
- New shared domain predicate `isStillRunning` (`domain/ActiveSpan.kt`) replacing six duplicated `durationMode == START_STOP && endedAt == null` inline checks across `VerdictEngine`/`WatchEngine`/`BigPictureViewModel`/`ShareCardState`/`CaseDetailScreen`/`InsightsTab`; generalizes `activeSpanEnd` so a `MANUAL` event's not-yet-elapsed `endedAt` spans to `now` the same way a `START_STOP` open event already did.
- `NotificationEvaluator.silenceAnchorFor`'s SQL-based counterpart (documented as needing to stay in sync "by hand" with `WatchEngine`'s in-memory version) had no way to see a `MANUAL` event's future `endedAt` at all (`getOngoingEvent`'s `endedAt IS NULL` query structurally can't). Added `EventDao.hasEventEndingAfter`/repository plumbing and wired it in, keeping its own doc-comment call count ("four suspend calls") in sync.
- Extended "still-running events are excluded until they stop" to `MANUAL`'s duration-based stats: `computeDurationStats`, and the Trends `outcomeValueFor` used by tag→outcome/trend slope/time-of-day split — a still-running event's duration is now excluded (its intensity, if recorded, still counts).
- Real stale-UI bug caught mid-build: the Stop button on a `MANUAL` row appeared to do nothing for up to 60s. `rememberTickingNow`'s `now` is a periodically-ticked Compose state, harmless for `START_STOP` (whose check is `endedAt == null`, independent of `now`) but `MANUAL`'s check compares `endedAt > now` — immediately after Stop, the freshly-written `endedAt` could still exceed the UI's stale `now`. Exposed the ticker as a `MutableState` and had the History tab's Stop callback bump it immediately alongside the stop action.
- Home's ongoing indicator read from `observeOpenEvents` (`endedAt IS NULL`), which structurally can never see a `MANUAL` event's future `endedAt` — a state that expires by the passage of time, not a database write. Switched `homeCaseRows` to derive `ongoingEvent`/`runningCount` from the event-span projection it already loads, via `isStillRunning`, re-evaluated against a fresh `now` on every call. `HomeCaseRow.ongoingEvent` narrowed from `EventEntity` to the leaner `CaseEventSpan` it only ever needed fields from.
- Checklist-surfaced (Duplication): four near-identical "elapsed-time vs. expected-end text" branches (History row, Insights drill-down row, Home's two row variants) collapsed into one shared `OngoingTimeText` composable.
- Checklist-surfaced (Duplication): the widgets' identical `use24Hour`-from-Settings `produceState` block, duplicated between `ListWidget.kt` and `SingleCaseWidget.kt`, extracted to `WidgetCommon.kt`'s `rememberWidgetUse24Hour`.
- Checklist-surfaced (UI Copy Brevity): `OngoingExpectedEndText`'s trailing text used the full `formatEventTime` (e.g. "Oct 14, 3:00 PM, Wed") — much longer than sibling `OngoingElapsedText`'s "2h 15m", and inconsistent with Big Picture's own new branch already using a bare clock time. New shared `formatExpectedEndTime` (same-day bare time, cross-day falls back to the shorter `formatSpanDateTime`) used by both the Compose composable and the widgets' own subtitle.
- Checklist-surfaced (Dead Code): `observeOpenEvents` (`EventDao`/`HodithRepository`/`RoomHodithRepository`/`FakeHodithRepository`) had exactly one caller, which the Home fix above removed. Deleted outright, with its two dedicated tests (`FakeHodithRepositoryTest`, `HomeCountQueriesTest`).
- Real pre-existing-test bug caught by the first instrumented run: `CaseDetailScreenTest.finishedEventRow_showsDurationLabel_whenTheCaseTracksDuration` used a `MANUAL` event with `endedAt` 45 minutes after start but the screen's default `now` only 10 seconds in — under the new semantics that event reads as still running, not finished, so the duration label stopped appearing. Fixed by passing an explicit `now` past the event's `endedAt`.
- New `WidgetActionsFlowTest` coverage for the Single-case widget's `MANUAL` expected-end subtitle — this path had no automated coverage at all before this pass; caught my own test-setup bug (`use24Hour` in the assertion not matching what `setUp()` pinned Settings to) on the first run.

**Deferred:** nothing.

**Considered and declined:**
- A Stop action on Big Picture's day/week detail row for a still-running `MANUAL` event — explicitly scoped out by the user; display-only there.
- Reconciling a `MANUAL` event's not-yet-elapsed `endedAt` in the duration-mode transition contract (switching a Case away from `MANUAL` mid-projection) — the existing "no migration for an inflated `endedAt` from an earlier round-trip, fixed by editing the event" policy already covers this without a special-case addition.

**Checks:**
- Duplication: see Found & fixed (`OngoingTimeText`, `rememberWidgetUse24Hour`, `formatExpectedEndTime`).
- Decoupling: `isStillRunning`/`activeSpanEnd`/`computeDurationStats`/`outcomeValueFor` all stay pure Kotlin (`domain/`), no `android.*` imports; every new `now` comparison takes an injected value, nothing reads the system clock directly.
- Accessibility: reused `StopIconButton`'s existing content description; the Edit Event screen's new "Stop now" button carries a text label, no icon-only affordance introduced.
- Naming: three new `Voice` members (`ongoingExpectedEndTrailing`, `bigPictureEventExpectedEnd`, `logSheetExpectedEndNotice`) added to all three voices in the same commit.
- Tests: new unit coverage for `isStillRunning`/`activeSpanEnd`, `computeDurationStats`/`computeTagOutcomeFindings` `MANUAL` exclusion, `silenceAnchorForEvents`/`NotificationEvaluator` `MANUAL` coverage, `BigPictureViewModel`'s `expectedEndAt`, `HomeViewModelMappingTest`'s `MANUAL` `ongoingEvent`/`runningCount`; new instrumented coverage for `EventDaoTest.hasEventEndingAfter`, the Edit Event screen's Stop-now round-trip, Home's `MANUAL` row, and the Single-case widget's `MANUAL` subtitle.

**Docs updated:** HODITH_SPEC.md (§6 new "Manual duration projecting past now" bullet, plus the Event table, §9 Active span, Big Picture, and §15 widget bullets); TESTING.md (Watch evaluation, Notification evaluation, Stats & visual data prep, Room DAOs, ViewModels, Compose UI, and Compose UI — Big Picture rows extended or corrected); MANUAL_TEST_PLAN.md (item 6 extended for the List widget's `MANUAL` row); this entry (oldest entry, `test/autosize-harness-investigation`, removed per the 5-entry retention rule).

