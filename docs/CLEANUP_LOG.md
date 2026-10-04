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

## feat/share-tabs

**Scope:** PROGRESS.md's "Share: replace the chooser dialog with Summary / Insights / History tabs" item. The Case Detail Share icon now opens one Share screen with three tabs instead of a chooser dialog and a second route.

**Walked:** every item in CLEANUP_CHECKLIST.md against the diff. Items not listed below were checked and needed nothing.

**Found & fixed:**
- Case Detail's hand-rolled tab row and the Share screen's tab row were the same code. The row is now `HodithTabRow`/`HodithTab` in `ui/common`, used by both.
- The share screens each held their own Name-on-card state, and the ViewModels held the name and the card format. The name now lives once on the host, above the tabs. The ViewModels hold neither.
- The two share routes each launched the share sheet. `ShareRoute` now merges both ViewModels' share requests and launches once.
- `SharePreviewScreen.kt` and `LogSharePreviewScreen.kt` held only tab content by now. Renamed to `InsightShareTab.kt` and `LogShareTab.kt`, with their tests renamed to match.
- The middle-dot separator was written inline in 15 places. It's now the `DOT_SEPARATOR` constant in `ui/voice`.
- Thirteen retired chooser and title keys removed from all three voices. `shareSectionsPickerLabel` is one structural key ("Include"), not three overrides. Every share Voice key is referenced outside `Voice.kt`.
- Some changed files had LF line endings, left behind by `ktlintFormat`. All changed files are CRLF again, matching the repo.
- The History title is one line, kicker and range together. Its test asserted the old two-line layout, so it was updated.
- The name rule (blank or untouched falls back to the Case's name) is a pure function, `shareDisplayName`, with its own unit test.
- Manual share-card steps 4–7 checked things automation already covers. Removed. Steps 1–2 keep what only a device can check: the capture and the share-sheet handoff.
- `ShareCardTemplateTest` gained a test that a card draws the name it's given, the one check the removed manual steps had been the only coverage for.
- `ktlintFormat` import order and line wrapping in touched test files.

**Deferred:**
- The History card's range on the title line, and a real range for "All" (creation date to today). Both change what the card shows, so they need a decision first. In PROGRESS.md.
- `CaseDetailScreen` is 251 lines and was already that long before this change. Splitting it is a refactor with its own risk, so it's in PROGRESS.md.

**Considered and declined:**
- `share(bitmap)` is duplicated in `ShareViewModel` and `LogShareViewModel`, about six lines each. The two differ only in the file-name prefix. Left as is.
- `CaseDetailViewModel` imports `DOT_SEPARATOR` from `ui/voice`. The ViewModel already imports from `ui` in other files, and the separator is one formatting string, so it stays.
- `shareDisplayName` has one caller. It's the rule the tests cover, so the extraction earns its keep.
- `LOG_SHARE_FIELD_TOGGLE_TAG_PREFIX` keeps its name. It names the History field toggles and matches the other `*_TAG_PREFIX` constants.
- `InsightShareTab` computes `insightsTabState` in the composable, remembered by day. That pattern predates this change. Moving it into the ViewModel is out of scope here.
- `docs/mockups/log-share-prototype.html` is still referenced by the spec and by `LogShareTab`'s comment, so it stays as design history.

**Not verified here:**
- Dark mode for the new tabs and the preview stage. Needs a manual check.
- The 48dp touch target on the Share button. Material's `Button` should enforce it; not measured.
- `DataStoreSettingsRepository.kt` has a compiler warning about an annotation target. It predates this change and this change doesn't touch the file.

**Checks:** ktlint passes. `lintDebug` passes. 1070 unit tests pass. `assembleDebug` passes. Instrumented, scoped: `ShareScreenTest` 6/6, `InsightShareTabTest` 25/25, `LogShareTabTest` 17/17, `SharePreviewOrderFlowTest` 1/1, `CaseDetailScreenTest` 35/35, `CaseDetailInsightsTabTest` 43/43, `ShareCardTemplateTest` 60/60. The full `connectedDebugAndroidTest` suite has not been run for this change.

**Tests:** `ShareScreenTest` (new) covers the title, the three tabs, the Preview heading on every tab, the Include heading, name carry-over across tabs, and which callback each Share button fires. `InsightShareTabTest` and `LogShareTabTest` are re-pointed at the new host, and the name test moved from `InsightShareTabTest` into `ShareScreenTest`. The chooser tests in `CaseDetailScreenTest` became one test that the Share icon opens the screen. Format-toggle and display-name ViewModel cases were removed with the API they tested. `ShareDisplayNameTest` (new) covers the name rule.

**Docs updated:** HODITH_SPEC §13 (the tabbed screen and entry point), TESTING.md (the Share rows, plus a new Share screen row), MANUAL_TEST_PLAN.md (the Share cards steps), PROGRESS.md (the Share item struck, two items added).

## feat/share-insight-section-order

**Scope:** PROGRESS.md's "Share Insight: reorderable sections" item. The Story picker's rows are dragged into order, the card follows, and the order is saved device-wide.

**Found & fixed:**
- The first instrumented run of the picker's drag was on the grip icon only, so a tap on the row's title did nothing. The gesture now sits on the whole row, and a short tap still toggles the section.
- The first drag test drove a stateful harness, so it didn't exercise the ViewModel or DataStore. It's removed. `SharePreviewOrderFlowTest` covers the same gesture through a real Room database, the real `ShareViewModel` and a DataStore file, and it's tagged `@Smoke`.
- The card-position helper was copy-pasted into both instrumented share test files. It's now one `ComposeContentTestRule.cardTitleTop` in `SharePreviewScreenTest.kt`.
- `ShareInsightsSection` moved from `viewmodel/` to `data/`, next to `LogRowField`, so `SettingsRepository` can persist it without `data` depending on `viewmodel`.
- SPEC §13 said "drag-to-reorder handles"; it now says rows, with the long-press.
- TESTING.md's Share card, Share preview, ViewModels rows and manual step 12 now describe the saved order and the new coverage.

**Considered and declined:**
- Drag state uses `remember`, not `rememberSaveable`. A rotation mid-drag ends the gesture, and that's acceptable for a transient interaction.
- The grip's geometry (dot positions, 24 dp box) is inline in `DragGrip`. These are layout values, not product constants, so they don't belong in the domain layer.
- The `ShareCardTemplateTest` order test planned earlier is not written. `ShareCardStateTest` checks the order of `storyOrder`, and the flow test checks the rendered card, so a third test would repeat them.
- The section order is not in the JSON export. It's a device preference like `LOG_VISIBLE_FIELDS`, which the export also leaves out (TESTING manual step 7).
- The grip is not a separate tap target. The whole row is, and it's at least the 48 dp minimum set by the row's switch.

**Deferred:** nothing.

**Docs updated:** SPEC §13 (picker, drag wording); TESTING.md (Share card assembly, Compose UI — Share preview, ViewModels, manual step 12); CLEANUP_LOG (this entry; the oldest entry, `feature/tag-combo-trends`, removed to keep five).

---

## fix/share-insight-streaks-name-affordance

**Scope:** PROGRESS.md's "Share Insight: render streaks, clarify Name-on-card is editable" item, covering the Insight share card's streak display and the Name-on-card field on both share screens.

**Found & fixed:**
- Square's Gaps card had no streak display, though `GapsDisplay` already carried the streak fields. It now has a second row (Longest streak / Average streak) inside the same card.
- Gaps and Streaks are both left off the card until the Case has two events. On Square the Gaps card is omitted; on Story the Gaps and Streaks rows aren't offered in the picker. This replaces the earlier "2+ events needed." placeholder, so `shareSquareGapsNeedMoreEvents` is removed. The went-quiet label rides on the Gaps card, so it also waits for the second event.
- Story has its own Streaks row in the section picker, toggled independently of Gaps and placed after Gaps. The figures travel on a new `ShareCardData.Insights.streaks` field (`StreakDisplay`), so the picker can switch them without touching Gaps. The card builder decides availability (`GapsDisplay.streakDisplay()`, and the `takeIf` on `shortestGapDays`), and `availableShareSections` mirrors it.
- The Name-on-card field was an `OutlinedTextField` whose placeholder Material3 hides while the field is empty and unfocused, so the label sat inside an apparently blank box. It now shows the name the card will carry (the override, or the Case's own name), with its label on the top border.
- The Insight and History share screens each had their own copy of that field. Both now use one composable, `ShareNameField`. It holds its text locally, so clearing the field leaves it empty instead of refilling it with the Case name mid-edit (the ViewModel stores blank as `null`). Typed text is capped at `CASE_NAME_MAX_LENGTH`, the same cap the ViewModel applies.
- The name label is structural: `shareNameFieldLabel` defaults to "Name on card" on the `Voice` interface, and the three per-voice overrides are gone. The new `shareStreaksTitle` is structural too. `VoiceTest` pins both.
- The label-over-value column was written twice, in `MinAvgMaxRow` and the streak row. It is now `StatColumn`.
- Test problems found on the first instrumented run: the two name-field assertions read `Text` semantics, which the field doesn't expose, so they failed. The clearing test's `hasText` check would have passed trivially. Both now read `EditableText` through `nameFieldText()`.
- Two unit fixtures for the went-quiet label used a one-event Case. They now use `withTwoEvents()`.

**Considered and declined:**
- An edit icon on the name field. The field's label and default value already signal that it's editable.
- A "2+ events needed." placeholder for streaks, in place of hiding them. Hiding matches the Gaps rule, which is what was asked for.
- Folding streaks into the Gaps picker row. The Story picker needed a separate selector for streaks.
- A separate Compose test for the Insight screen's name field. The field is the same composable as the History screen's, which `LogSharePreviewScreenTest` covers directly.
- Checklist items with nothing to act on in this diff: no new colours, icons or deprecations; no data model, widget or notification changes; no domain or `Clock` changes; the share card still excludes notes, and tags stay on Story's opt-in section.

**Deferred:** nothing.

**Docs updated:** PROGRESS.md item struck. HODITH_SPEC §13: the Story picker's section list and availability rule, the Square Gaps and streak row, the Story Streaks card, and the name-field description. TESTING.md: the Share preview and History Share preview rows, and the share card assembly row. MANUAL_TEST_PLAN.md checked: its typed-name export step still holds.

**Verified:** `ktlintCheck`, `lintDebug`, `test` (unit suite), `compileDebugAndroidTestKotlin` and `assembleDebug`, run sequentially. Instrumented: `connectedDebugAndroidTest` on the Pixel 8 emulator, run per class: `ui.share.ShareCardTemplateTest` (59), `ui.share.SharePreviewScreenTest` (25) and `ui.share.LogSharePreviewScreenTest` (19), all passing. Not run: the rest of the instrumented suite.

---

## feat/history-rename-average

**Scope:** PROGRESS.md's "History: rename the Log tab, add a top-line average" item, plus every user-facing "Log" noun for the record (share chooser, Log Share screen, Delete Data copy, Log detail).

**Found & fixed:**
- Case Detail's History summary line now leads with the Insights hero rate (`2.1/week`, `<1/month`) when the Case has enough data, via a new `caseHeroRate` in `domain/HeroRate.kt`. It reuses `computeHeroRate` and skips the trend comparison below `INSIGHTS_MIN_EVENTS`, as Insights does. The figure and unit formatting moved to internal `figureText`/`unitText` in `ShareCardTemplate.kt`, shared by the share card and the summary line.
- `Voice.logSummaryLine` takes the preformatted rate (or `null`) as its first argument. Plain, Intense and Bright each prepend it with the existing line unchanged.
- Renamed to "History" across all voices: the tab label, the share chooser option, "History Share", "Share history", "The history", "History detail", and the Delete Data "History only" / "Delete history" copy. Verbs such as "Log an event" stay.
- Two stale comments updated: `ShareChooserDialog`'s tag-collision note and the `Voice` KDoc for the tab's Edit icon.
- The ktlint failure on the new function signature in `ShareCardTemplate.kt` was fixed before the final run.
- The `figureText + unitText` pairing had been written out inline in the History summary call. It's now `HeroRate.rateText(voice)` in `ShareCardTemplate.kt`, used by the History summary; the share card keeps calling the two pieces separately because it styles them apart.

**Considered and declined:**
- Reusing `caseHeroRate` inside Insights too. Insights already computes the trend stats for its Trends card, so the History path computes its own rate instead of threading the shared value through.
- A trend pill on the History summary line. It would lengthen a line that already carries the rate, the count and the span.
- Checklist items with nothing to act on in this diff: no new colours, icons or Voice keys (accessibility and theme items); no data model, widget or notification changes; no new `System.currentTimeMillis` or `android.*` use in domain code; no new deprecations; no self-updating counts added to TESTING.md.

**Deferred:**
- A Compose test for the summary line. The rate logic is covered by `HeroRateTest` and the wording by `VoiceTest`. The call site in `CaseDetailScreen` is not covered by any test, which is accepted for now. Revisit if the summary grows logic of its own.

**Docs updated:** PROGRESS.md item struck, and the Delete Data "History only" bullet removed. SPEC §6 and §13 names, the summary-line description in §6, and the chooser option in §13. TESTING.md's Log Share preview row renamed.

**Verified:** `ktlintCheck`, `lintDebug` ("No issues found."), `test` (1060 unit tests, 0 failures), `assembleDebug`, all green, run sequentially. A later rename-only helper extraction was re-checked with `ktlintCheck`, `test` (1060 unit tests) and `assembleDebug`; `lintDebug` was not re-run after it. Not run: instrumented tests and any on-device check.

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
