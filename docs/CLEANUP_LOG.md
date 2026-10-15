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

## fix/big-picture-selector-chip-contrast

**Scope:** User-reported Big Picture Case/Tag filter popup inconsistencies across voices: Case and Tag chips used different selected/unselected conventions, Intense's selected Case chip read weaker (greyer) than unselected, and Bright's Tag chip selected/unselected contrast was nearly invisible. Two follow-on issues surfaced during manual verification: Intense's bulk-action/dismiss button row wrapped, and the read-only legend row could grow unboundedly with a large partial selection, crowding out the calendar grid.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff. `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to `BigPictureScreenTest` on a connected emulator — 51/51 green, including `caseChipAndTagChip_haveEqualHeight_inSharedFlowRow`, which guards Case/Tag chip height parity and is touched by `TagFilterChip`'s new bold selected text.

**Found & fixed:**
- `CaseFilterChip`'s combined `PLAIN, INTENSE` branch split in two: Intense's selected Case fill moved from `secondaryContainer`/`onSecondaryContainer` (a grey lighter than the unselected `surface` in light mode) to `secondary`/`onSecondary`, so selected reads heavier than unselected instead of weaker; Plain unchanged. `CaseGroupChip` ("All Cases" aggregate legend label) got the identical fix, since it shares the same bug and sits beside the now-fixed per-case chips in the legend row.
- `TagFilterChip`'s selected border moved from `outline` to `onSurface`, and selected text gained `FontWeight.Bold` — on Bright, `outline`/`outlineVariant` are nearly indistinguishable, so the old border-width-only signal was almost invisible. Tags stay tint-free (no fill/halo), preserving the documented one-accent (Intense) / two-colour (Bright) budget the original design called for.
- Intense's bulk-action labels shortened (`bigPictureSelectAllAction`/`bigPictureClearAllAction`: "Select every one"/"Clear every selection" → "Select all"/"Clear all") and `bigPictureFilterCountAll` ("Every one" → "All") — the originals overflowed the info dialog's confirm-button row alongside the dismiss button, and the Year trigger chip once every dimension showed Intense's longer wording.
- `FilterLegendRow`'s itemized Case/Tag list had no size cap, so a large partial selection could grow past the available screen space. First tried a character-budget "+N" remainder chip (`bigPictureLegendOverflow`); dropped after the user's manual testing showed it read as a disabled button, gave inconsistent line counts per theme (chip chrome width varies by theme), and placed the "+N" ambiguously once one group (e.g. Tags) had already collapsed to a single label. Replaced with a binary `bigPictureLegendFits` check: list every selected item, or (over a generous ~3-line character budget) show nothing at all — the same "trigger chips above still state the count" fallback the existing all-selected case already uses.

**Deferred:** nothing.

**Considered and declined:**
- A per-item "+N" truncation of the legend row via Compose's built-in `FlowRowOverflow.expandIndicator` line-capping mechanism — declined twice: it's `@Deprecated("FlowLayout overflow is no longer maintained")` in this project's Compose version, and independent of that, its indicator is wired to expand the row on tap rather than stay static, which wasn't wanted here.

**Checks:**
- Duplication: `CaseFilterChipRow` extracted once Intense needed different token roles than Plain, so the two branches share one row layout instead of duplicating it; no new inline Voice strings (the dropped "+N" indicator would have needed none either, being numeral-only like `bigPictureFilterCount`'s existing default).
- Decoupling: `bigPictureLegendFits` stays pure Kotlin in `BigPictureFilterState.kt` alongside the `bigPictureCaseLegend`/`bigPictureTagLegend` helpers it complements; no `android.*` imports.
- Hygiene: `git status` clean throughout; no secrets or local paths in the diff.
- Naming: `LEGEND_VISIBLE_CHAR_BUDGET`/`LEGEND_CHIP_CHROME_CHARS` named and documented rather than inlined; no new `Voice` keys — only three existing Intense-only values were edited, so the "all three voices in one commit" rule doesn't apply.
- Hardcoded values: no new `Color(0xFF...)` literals — every chip fix reuses existing `ColorScheme` roles.
- Accessibility: chip padding/touch targets unchanged; Plain/Intense/Bright contrast changes verified manually by the user across light and dark, per this repo's convention.
- Deprecated APIs: see Considered and declined above — deliberately avoided.
- Spec review: HODITH_SPEC.md §9 updated for the legend row's new show-everything-or-nothing rule on a long partial selection.
- Tests: new unit coverage for `bigPictureLegendFits` (empty input, exact budget boundary, a shared Case+Tag budget, a realistic long selection against the real default budget); no existing test weakened.

**Docs updated:** HODITH_SPEC.md (§9 Big Picture legend row); TESTING.md (`bigPictureLegendFits` added to the Big Picture filter helpers list); this entry (oldest entry, `feature/manual-duration-ongoing-stop`, removed per the 5-entry retention rule).

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

