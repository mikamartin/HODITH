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

## fix/app-bar-titles-home-case-name-display-body-split

**Scope:** Resolved the PROGRESS.md item (deferred from `fix/cases-intense-typography-roles`): every screen's `TopAppBar` title defaulted to Material3's `titleLarge` (body-face) instead of `Type.kt`'s display-face rule, and `HomeScreen.kt`'s Bright-only case row manually swapped its case name to the display face with no Plain/Intense equivalent. A follow-up audit (asked for explicitly, not part of the original item) widened the pass to find other display/body-split drift app-wide before landing.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (13 changed `.kt` files, 0 new). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. No instrumented test run needed — grepped `app/src/androidTest` for every touched style role (`titleLarge`/`titleMedium`/`titleSmall`/`labelLarge`/`headlineSmall`) and confirmed no existing instrumented test touches any of the 10 changed screens/composables; a font-family/weight-only change has nothing for a content/interaction-based Compose test to catch either way. User reviewed the result on-device/Preview across all three voices, light and dark, before this pass closed out.

**Found & fixed:**
- Six `TopAppBar` titles (`ShareScreen.kt`, `CaseEditScreen.kt`, `LogDetailScreen.kt`, `AboutScreen.kt`, `ManageTagsScreen.kt`, `ArchivedCasesScreen.kt`) — unstyled → `MaterialTheme.typography.headlineSmall`.
- Two bottom-sheet headers that play the identical screen-title role but aren't a literal `TopAppBar`, caught by the follow-up audit: `LogDetailSheet.kt`'s "New event" header and `WatchEditorSheet.kt`'s "Create/Edit Watch" header, both `titleLarge` → `headlineSmall`. `LogDetailSheet`'s own KDoc already documented that its header slot mirrors `LogDetailScreen`'s `TopAppBar` title exactly — leaving it unfixed would have made "New event" and "Edit event" diverge in face right after this same pass aligned them.
- `HomeScreen.kt`: extracted a shared `homeCaseNameStyle()` (`titleMedium` + `FontWeight.Bold`, body-face) used by both `HomeCaseRowBody` (Plain/Intense) and `BrightHomeCaseListItem`, replacing Bright's one-off `fontFamily`-swap override. Removes both the Plain/Intense-vs-Bright asymmetry the PROGRESS.md item flagged and the small style-definition duplication between the two composables.
- `BigPictureGrid.kt`'s month-group divider header (`titleSmall` → `labelLarge`) — found by the follow-up audit, not the original item: it sat directly above `WeekdayHeader`'s `labelSmall` in the same screen, a visible same-screen face mismatch.
- Inline comments added at the three body-face/display-face exceptions that had none before, so each survives future refactors without being re-flagged as a bug: `CaseDetailScreen.kt`'s app bar (case name stays body-face), `InsightsDetailScaffold.kt`'s title (same reason, alongside its existing structural KDoc), and `ShareCardTemplate.kt`'s `SummaryFigure` (display-face is a deliberate branding exception, not an oversight).

**Deferred:** nothing.

**Considered and declined:**
- `ShareCardTemplate.kt`'s `SummaryFigure` hero stat (`displaySmall`) — technically outside `Type.kt`'s app-bar/section-label/button list, but it's a branded number, not prose; declined as a style change, marked with an inline comment instead (see Found & fixed).
- `InfoDialog.kt`/`ConfirmDialog.kt` titles — checked during the audit; both already inherit Material3's `AlertDialog` default `headlineSmall` title style (unlike `TopAppBar`'s `titleLarge` default, the actual bug this pass fixes elsewhere), so every dialog title in the app already matched the display-face rule with no change needed.

**Checks:**
- Duplication: the Home case-name style duplication between `HomeCaseRowBody` and `BrightHomeCaseListItem` is what this pass removes (see Found & fixed); no new duplication introduced — the 8 `headlineSmall` title swaps each sit in their own screen's existing `TopAppBar`/header call, the established direct-`MaterialTheme.typography` pattern used throughout the app, not a repeated pattern that needs its own abstraction.
- Decoupling: no `ViewModel`/`Repository`/domain code touched; no `android.*` imports added.
- Complexity: `homeCaseNameStyle()` follows the existing lowercase-`@Composable`-helper-returning-a-style/shape convention already in the codebase (`caseEditTextFieldShape()`, `themedSwitchColors()`, `frequencyBarBrush()`); two call sites, not a single-caller extraction.
- Dead code & hygiene: `git status` clean throughout (exactly the 13 intended files); `ktlintCheck` found no unused imports after removing `BrightHomeCaseListItem`'s old `nameStyle` local.
- Naming: no new files, no new `Voice` keys; `homeCaseNameStyle()` matches the naming precedent above.
- Hardcoded values: none introduced.
- Accessibility: no tap-target or contrast changes; confirmed by the user across all three voices in both light and dark (see Walked).
- UI Copy Brevity: N/A — no label text changed, style-role swaps only.
- Data model/migrations, background work/widgets/notifications, deprecated APIs: untouched — N/A.
- Spec review: `HODITH_SPEC.md` §12's theme/font table is unaffected — it names each theme's font pairing, not the display/body role-assignment rule, which lives only in `Type.kt`'s own doc comment and is unchanged by this pass (a conformance fix, not a rule change).
- Tests: no behavior change — font-family/weight-role correction only — so no new test coverage; see Walked above for why no existing instrumented test could or needs to catch this.

**Docs updated:** PROGRESS.md (resolved item struck/removed); this entry (oldest entry, `feature/pdf-export — remove Insights PDF`, removed per the 5-entry retention rule).

---

## fix/single-case-widget-missing-case-edit-icon — remove hidden content, add render-test guard

**Scope:** Continuation of the same open PR: reviewing the previous pass's "✎" edit icon on a real emulator surfaced two rendering bugs the existing instrumented suite doesn't catch — an archived/deleted Case's "Case is gone" message clips down to a single word, and some Case icons clip at the bottom. Traced the root cause to `WidgetRenderTestFixtures.kt`'s `renderedView()`, which only calls `AppWidgetHost.createView()` — no `measure()`/`layout()`, no `AppWidgetHostView` size options, never attached to a window — so every `WidgetActionsFlowTest` assertion walks an unmeasured View tree that proves nothing about the widget's real, non-resizable declared size (`single_case_widget_info.xml`: `minWidth="60dp" minHeight="60dp"`, no `resizeMode`). Rather than patch the symptom, redesigned the Single-case widget to stop rendering content that can't reliably fit: bound-Case state now shows only the icon (its own non-weighted zone, sized to its own content, so a sibling can never starve it of height) and a log button filling the rest of the tile — no subtitle, "Ongoing" pill, elapsed time, or count; missing-Case state shows "Edit" + "✎" filling the same shape, both reopening the configure Activity, with the old message and its separate `MainActivity` tap removed outright.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (9 changed/new `.kt`/gradle files, 4 docs). `ktlintCheck`, `lintDebug`, `test` (full unit suite, including the new `WidgetCaseSubtitleTest`), and `assembleDebug` all pass. `connectedDebugAndroidTest` scoped to `WidgetActionsFlowTest` + `WidgetChromeNavigationTest` + `SingleCaseWidgetConfigureFlowTest` (18/18 passed on a Pixel 8 API 36 emulator).

**Found & fixed:**
- `SingleCaseWidget.kt`: `SingleCaseContent`'s icon moved out of the weighted `Column` it shared with the now-removed subtitle into its own non-weighted `Box` sized to its own content (the actual fix for the icon-clipping bug), and its "+" button swapped `.height(MinTapTarget)` for `.defaultWeight()` to fill the rest of the tile. `SingleCaseMissingContent` mirrors the same shape with the new "Edit" label and the resized "✎" icon (kept borderless, matching its existing styling — no background was added just because the box grew); the old message and its `MainActivity` tap are gone entirely. Dropped the now-unused `now`/`use24Hour` params from `SingleCaseContent` and the dead `use24Hour` local/`rememberWidgetUse24Hour` call from `provideGlance` (the `now` local itself stays — `homeCaseRowsFlow` still needs it). Caught on the user's own on-device look (not by any test): the "Edit" label's `Text` had no `fontWeight` set, reading thinner than every other widget text label (`WidgetCaseNameSize`'s `FontWeight.Medium`, the header's Bold) — only the glyph-only "✎"/"+" texts are weight-less by design. Added `FontWeight.Medium` to match the Case name convention, the closer analog since "Edit" is a short readable word, not a glyph.
- `Voice.kt`: removed `widgetCaseNotFoundMessage` (×3 voices) entirely; added `singleCaseWidgetEditLabel` for the new visible label, distinct from the existing `singleCaseWidgetEditButtonDescription` content description. Initially added as ×3 per-voice overrides, then simplified on review: widget chrome only ever renders `PlainVoice` regardless of the user's in-app theme (DEV_PLAYBOOK.md §4), so `singleCaseWidgetEditLabel` and three other pre-existing widget-chrome keys that had the same invisible-but-still-written-three-ways shape (`widgetNoCasesSelectedMessage`, `widgetEditButtonDescription`, `singleCaseWidgetEditButtonDescription`) all collapsed to a single `get() = "literal"` default in the `Voice` interface, with every per-voice override deleted — the same "structural, identical across voices" pattern this file already uses for `sharePdfDocumentTitle`/`widgetTodayCount`, just not yet applied to these four.
- `WidgetCommon.kt`: `MinTapTarget`/`WidgetGlyphButtonSize` doc comments corrected — they no longer claim every glyph button (including Single-case's) sits in a fixed `MinTapTarget` box; values and the List widget's own usage are unchanged.
- `docs/CLEANUP_CHECKLIST.md`: new Tests-section line asking whether a widget instrumented test's fixture actually constrains measurement to the widget's real declared size, generalized beyond this widget.
- Tests: `WidgetActionsFlowTest` lost two tests with nothing left to assert (subtitle, today-count) and had `runningCase_showsOngoingPill_andTheCaseAreaOpensCaseDetail` rewritten to `singleCaseWidget_iconTap_opensCaseDetail` (drops the pill/subtitle/`WidgetPillTextSize` assertions, keeps the icon-tap-opens-`MainActivity` check), which also let the `SettingsRepository`/`TimeFormat` pinning that only existed for the deleted subtitle test come out of `setUp`/`tearDown`. `WidgetChromeNavigationTest` lost the missing-Case message tests and gained `singleCaseWidget_missingCase_editLabelTap_opensConfigureActivity` alongside the existing edit-icon ones. `SingleCaseWidgetConfigureFlowTest` repointed its missing-Case poll/assertions from the deleted message string to the new label.
- **Caught mid-pass, not by the checklist walk itself:** removing `WidgetActionsFlowTest`'s subtitle tests also removed the *only* instrumented coverage of the List widget's own "Ongoing" pill/elapsed/today-count text — that text comes from `WidgetCaseSubtitle`, a function shared by both widgets, and the List widget's `LazyColumn` rows can never be reached by `AppWidgetHost.createView()` at all, so the Single-case widget was the only vehicle any test ever had for exercising that shared code. First pass left this as a manual-only regression in MANUAL_TEST_PLAN.md; caught on review before landing. Fixed properly instead of accepted: added `androidx.glance:glance-testing`/`glance-appwidget-testing` (`libs.versions.toml`/`app/build.gradle.kts`, version `1.1.1` to match the existing `glance` version) and a new JVM test, `WidgetCaseSubtitleTest`, composing `WidgetCaseSubtitle` directly via Glance's own unit-testing library — no `AppWidgetHost`, no device, and zero changes to `ListWidget.kt` or either widget's production code. Needed `testOptions.unitTests.isReturnDefaultValues = true` (`app/build.gradle.kts`) — the library touches `android.os.Bundle` internally even though the composable under test never reads a real `Context`, which the stock `android.jar` stub otherwise throws on.
- **On-device confirmation (the actual point of this investigation):** the user placed the redesigned widget on a real launcher and confirmed both states — neither the Case icon nor the missing-Case "Edit"/"✎" content clips any more, and both the icon/label zone and the body-filling button feel like proper, comfortably tappable targets. This is the one thing in this whole pass instrumented tests structurally can't prove (Task A's finding) — it had to be eyes-on, and now it has been.

**Deferred:** nothing — see the on-device confirmation below (Found & fixed), the last thing instrumented tests structurally can't prove (Task A's finding).

**Considered and declined:**
- Constraining `WidgetRenderTestFixtures.kt`'s `renderedView()` to measure/layout against the XML's declared 60dp minWidth/minHeight — declined: a real launcher sizes a non-resizable widget by its own grid-cell geometry, not a naive dp→px conversion of the manifest minimums, so a hardcoded constraint would look like real-size verification without actually being one. The new checklist line is the mitigation instead.
- Keeping an instrumented render-size assertion for `WidgetPillTextSize` — declined: it was only ever reachable through the Single-case widget's now-removed pill (the List widget's own pill lives inside an un-instrumentable `LazyColumn`); `WidgetTokenFidelityTest` still locks the token's value.
- Giving the resized "✎" box a surface-colored background to match the "+" button now that both fill the whole body — declined: the task called for a sizing change, not a styling one, and the borderless look is unchanged from what shipped in the previous pass on this same branch.

**Checks:**
- Duplication: no new composables or patterns; reused existing `WidgetSingleCaseOuterPadding`/`WidgetSingleCaseLogButtonSpacing`/`WidgetIconGlyphSize`/`WidgetGlyphButtonSize`/`WidgetLogButtonCornerRadius` tokens throughout.
- Decoupling: no `ViewModel`/`Repository`/domain code touched; no `android.*` imports added anywhere that shouldn't have them.
- Complexity: `SingleCaseContent`/`SingleCaseMissingContent` stayed the same two-composable shape at roughly the same line count; no new abstraction layer.
- Dead code & hygiene: removed the now-unused `TextAlign` import, the dropped `now`/`use24Hour` params, and — in `WidgetActionsFlowTest.kt` — the `SettingsRepository`/`TimeFormat` pinning and `MILLIS_PER_*`/`formatExpectedEndTime`/`assertTrue` imports that existed only to support the deleted subtitle test; `git status` clean throughout, `ktlintCheck` caught nothing stray.
- Naming: new `Voice` key `singleCaseWidgetEditLabel` follows the existing `sharePdfDocumentTitle`/`widgetTodayCount` default-only naming/shape rather than being added "to all three voices" as distinct text, since it never varies by voice; renamed/new test methods follow the existing `camelCase_describingBehavior` convention in each file.
- Hardcoded values: none introduced; every size/color pulls from existing `WidgetCommon.kt` tokens.
- Repo hygiene: `git status` clean except the one new tracked test file; diffed the full changeset for secret-shaped strings/local paths — none found; `app/build.gradle.kts`/`gradle/libs.versions.toml` changes are shared tooling config, not local setup, so they belong in the repo.
- Accessibility: the icon/label zone and the "+"/"✎" button both stay clickable, and the "✎" box keeps its existing content description; confirmed on the user's own device (see Found & fixed) that both zones are comfortably tappable now that the top zone is sized to its own content rather than a fixed box — not just asserted from reading the code.
- UI Copy Brevity: N/A for per-voice tone/length comparison — `singleCaseWidgetEditLabel` has one fixed string, not three to compare; checked only that "Edit" itself fits the body-filling zone alongside the "✎" icon.
- Data model, migrations: untouched — N/A.
- Deprecated APIs: no new deprecation warnings from the build output after adding `glance-testing`/`glance-appwidget-testing`; the two warnings the build already prints (Moshi's Kapt codegen, `srcDirs`'s vararg overload) are pre-existing and untouched by this diff.
- Background Work, Widgets & Notifications: Single-case widget still renders via `WidgetPalette`'s fixed Plain-light palette and still respects each Case's `logFlow`; the List widget's own code, constants, and tests are untouched by this pass.
- Spec review: `HODITH_SPEC.md` §15's Single-case bullet rewritten to match the new icon-zone/body-button shape and the "Edit"/"✎" missing-Case state; the List widget's own bullet is untouched.
- Tests: the test/reality gap this pass investigates → the CLEANUP_CHECKLIST.md guard above, since the real fix is removing the untestable content, not a regression test for a harness that can't be trusted; every test that would otherwise pass against now-dead code (`widgetCaseNotFoundMessage`, pill/subtitle assertions) was updated or removed; all touched instrumented tests actually ran on a device (18/18) before this entry was written; the coverage loss this pass's own test deletions caused (see Found & fixed) was caught and fixed with `WidgetCaseSubtitleTest` rather than written off as newly manual, and that new JVM test passed (4/4) before this entry was written too.

**Docs updated:** HODITH_SPEC.md (§15 Single-case bullet); TESTING.md (Widgets — chrome & trampoline row, seed list item 3); MANUAL_TEST_PLAN.md (items 6, 7, 9, 11); CLEANUP_CHECKLIST.md (new Tests-section line); this entry (oldest entry, `feature/pdf-export — toggle/table redesign pass`, removed per the 5-entry retention rule).

---

## fix/single-case-widget-missing-case-edit-icon

**Scope:** PROGRESS.md item: the Single-case widget had no persistent in-widget way back into its Case picker. Decided against a persistent edit icon on a *populated* Single-case widget (remove-and-recreate already covers that, and the widget is already a dense 1x1 square) — the one real gap is when the bound Case gets deleted: the widget fell back to an unconstrained "Case is gone" message with no way to rebind short of deleting the widget outright. Fixed by showing a "✎" edit icon in that missing-Case state only, in the exact slot the "+" log button normally occupies, reopening `SingleCaseWidgetConfigureActivity` to rebind. Also removed, per explicit request: the one-line description under the title in both widgets' configure dialogs (List and Single-case) — the title alone is self-explanatory.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (7 changed `.kt` files, 3 docs). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. `connectedDebugAndroidTest` run scoped to `WidgetChromeNavigationTest` + `SingleCaseWidgetConfigureFlowTest` (14 tests, all green) — the emulator initially had no HODITH package installed at all (a Gradle up-to-date/device-state mismatch, not a code issue), resolved with `installDebug installDebugAndroidTest --rerun-tasks` plus `adb shell appwidget grantbind`.

**Found & fixed:**
- `SingleCaseWidget.kt`: `provideGlance` now computes `appWidgetId` (mirroring `ListWidget.kt`); the missing-Case branch is a new private `SingleCaseMissingContent` composable structured like `SingleCaseContent` (weighted message area + a bottom `MinTapTarget` slot) instead of one unconstrained full-size `Text` — this also fixes the reported symptom of the "Case is gone" message rendering oversized, as if sized for the bigger widget. The bottom slot is a "✎" glyph (neutral `onSurface` color, no filled box — matching the List widget's header edit icon's look, not the "+" log button's accent/bold/filled-box look it replaces) that reopens `SingleCaseWidgetConfigureActivity` via the same `ACTION_APPWIDGET_CONFIGURE`/`EXTRA_APPWIDGET_ID` intent shape `ListWidget.kt`'s header icon uses.
- `Voice.kt`: added `singleCaseWidgetEditButtonDescription` (×3 voices) for the new icon's `contentDescription`; removed `widgetConfigureBody`/`singleCaseWidgetConfigureBody` (×3 voices each) and their `item { Text(...) }` lines in `ListWidgetConfigureActivity.kt`/`SingleCaseWidgetConfigureActivity.kt`'s `LazyColumn`s.
- `WidgetCommon.kt`'s `WidgetGlyphButtonSize` doc comment and `SingleCaseWidgetConfigureActivity.kt`'s class doc comment updated to describe the new third caller / re-bind entry point.
- Tests: `WidgetChromeNavigationTest` gained a tap-opens-Activity test and a content-description test for the new icon (plus a class KDoc update); `SingleCaseWidgetConfigureFlowTest` gained a real end-to-end test — insert and bind a Case, delete it, confirm the missing-Case state renders, reopen the same configure Activity, and confirm rebinding to a different real Case clears the missing-Case message. Reviewed the rest of the widget test suite (`WidgetActionsFlowTest`, `WidgetLogTrampolineActivityTest`, `WidgetRefreshWorkerTest`, `WidgetTokenFidelityTest`, both ViewModel unit tests, `WidgetConfigureTestFixtures.kt`) — none touch the missing-Case path or the removed body text, so none needed changes.

**Deferred:** nothing.

**Considered and declined:**
- A persistent edit icon on a *populated* Single-case widget — explicitly declined by the user; remove-and-recreate is an acceptable path there, and the 1x1 square is already dense (icon, subtitle, log button).

**Checks:**
- Duplication: the new icon reuses `MinTapTarget`/`WidgetGlyphButtonSize`/`WidgetPalette` tokens and the exact `ACTION_APPWIDGET_CONFIGURE` intent shape already established by `ListWidget.kt`'s header icon — no new pattern introduced.
- Decoupling: no `ViewModel`/`Repository`/domain code touched; `SingleCaseWidgetConfigureViewModel` needed no change at all (it already seeds `selectedCaseId = null` unconditionally, which is exactly right for a re-bind with nothing to preselect).
- Complexity: one new composable (`SingleCaseMissingContent`, ~40 lines), mirroring the existing `SingleCaseContent` shape; no new abstraction layer.
- Dead code & hygiene: removed the now-unused `glanceContext` local in `SingleCaseWidget.kt` left over from the old inline `Text`'s `clickable`; `git status` clean throughout; ktlint found nothing stray.
- Naming: new `Voice` key added to all three voices in the same commit; no new files.
- Hardcoded values: none introduced — all sizes/colors pull from existing `WidgetCommon.kt` tokens.
- Accessibility: new edit icon has a non-empty, voice-backed `contentDescription`; its tap target is the existing `MinTapTarget`-sized slot (≥48dp).
- UI Copy Brevity: the three new `singleCaseWidgetEditButtonDescription` strings checked against their `widgetEditButtonDescription` sibling for length/tone parity per voice.
- Background Work, Widgets & Notifications: missing-Case state still renders via `WidgetPalette`'s fixed Plain-light palette, matching the widgets' theming constraint.
- Spec review: `HODITH_SPEC.md` §15 updated — removed the stale "Single-case widget doesn't have this yet" note on the List widget's edit-icon bullet, and added a sentence to the Single-case widget's own bullet describing the missing-Case rebind path and the no-persistent-edit-affordance-while-bound decision.
- Tests: bug fixed (no rebind path) → regression test added (the new `SingleCaseWidgetConfigureFlowTest` case); all new instrumented tests actually run on a device before this entry was written.

**Docs updated:** PROGRESS.md (resolved item struck entirely); HODITH_SPEC.md (§15, two bullets); TESTING.md (Widgets — chrome & trampoline row); this entry (oldest entry, `fix/bright-intensity-label`, removed per the 5-entry retention rule).

---

## fix/cases-intense-typography-roles

**Scope:** User-reported: Intense voice's distinctive display face (Oswald, bold) seemed to be missing from most of the Cases screens, showing up correctly only in filters, some labels, and buttons. Traced to `Type.kt`'s typography-swap mechanism itself being correct (no wiring bug) — several call sites on Cases screens were just choosing a body-face role (`titleSmall`) for text that functions as a header/label, instead of a display-face role (`labelLarge`).

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (3 changed `.kt` files, 1 doc). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. No instrumented test run needed — see Tests below.

**Found & fixed:**
- `InsightsTab.kt`: all 8 Case Detail Insights-tab section headers (Heatmap, Frequency, Rhythm, Gaps & streaks, Trends, Duration, Intensity, Tags) were rendering on `titleSmall`. The 5 `SectionWithInfo(...)` call sites explicitly overrode `labelStyle` down to `titleSmall`; deleted the override so each falls back to `SectionWithInfo`'s own correct default (`labelLarge`). The 3 plain `Text(voice.X, style = titleSmall)` call sites changed to `labelLarge` directly.
- `WatchesTab.kt`: the Watch card's own title and the headline observed-rate figure both used `titleSmall` + a manually bolted-on `fontWeight = FontWeight.Bold`; changed to `labelLarge`, keeping the explicit `FontWeight.Bold` override so Plain's cards stay visually bold the same way `CardEyebrow` (same file) already deliberately forces bold regardless of theme, rather than only getting Plain's default `SemiBold` display weight.
- `HodithTabRow.kt`: `HodithTab`'s `ProvideTextStyle(titleSmall)` hardcoded the body face for every tab label — fixed to `labelLarge`. Shared by Case Detail's History/Insights tabs and the Share screen's tabs, so both get the correct role in the same fix; no new inconsistency introduced.
- Confirmed before changing: Material3's default `Typography()` gives `titleSmall` and `labelLarge` the identical base size/line-height/weight (14sp/20sp/Medium) — so every change above swaps only font family and the theme's display weight, not layout size. The two dynamic strings touched (`WatchesTab.kt`'s card title and rate label) already wrap without truncation (`Modifier.weight(1f)`, no `maxLines`/`overflow`), so the bolder Intense/Bright display weight can't clip them; the section/tab labels touched are fixed English words identical across all three voices (`Voice.kt`'s own doc comments confirm this), so there was no longer-Intense/Bright-phrasing wrap risk to check either.

**Deferred:**
- Two broader, non-Cases-specific gaps found during the same audit — spun out as one new PROGRESS.md item rather than fixed here, since both need a design call first: every screen's `TopAppBar` title (About, Archived Cases, Case Edit, Log Detail, Manage Tags, Share) renders unstyled/body-face though `Type.kt`'s comment says the app bar should take the display face; and `HomeScreen.kt`'s Bright-only case-list item manually overrides a case name to the display face with no Intense/Plain equivalent.

**Considered and declined:**
- `CaseDetailScreen.kt`'s `TopAppBar` title (the case name itself) — initially flagged, then declined: `Type.kt`'s own comment says case names stay body-face (`titleMedium`), matching `ArchivedCasesScreen.kt`'s and `HomeScreen.kt`'s Plain/Intense case-name rows. Correct as-is; folded into the deferred PROGRESS.md item's reasoning instead of touched here.
- `ArchivedCasesScreen.kt`'s case-name row (`titleMedium`) — declined for the same reason; matches the established case-name-is-body-face pattern exactly.
- `WatchesTab.kt`'s empty-state title (`titleMedium`) — declined; matches the app-wide empty-state convention (`CenteredEmptyState` is unstyled/body-face everywhere else too), no sibling pattern argues for display-face here.
- `CaseEditScreen.kt`'s icon-grid selected-icon labels (`titleMedium`) — declined; these render emoji glyphs, so font-family has no visible effect either way.
- `DateRangeFilterDialog.kt`'s date-range headline (`titleLarge` → `titleMedium`) — declined; its own comment explains this is a deliberate width-only downsize for a dialog-width row, and the text it styles (a selected date range) is content, not a label, so body-face was already the right role.

**Checks:**
- Duplication: no new composables, strings, or logic; pure style-role correction reusing existing `Voice` keys and the existing `SectionWithInfo`/`labelLarge` default.
- Decoupling: no ViewModel/Repository/domain code touched; no `android.*` imports added.
- Complexity: no new composables or structural changes, only `style =`/`ProvideTextStyle(...)` argument swaps.
- Dead code & hygiene: `git status` clean throughout; `ktlintCheck` found nothing stray after the `labelStyle` line deletions.
- Naming: no new files, no new `Voice` keys.
- Hardcoded values: none introduced.
- Accessibility: no tap-target or contrast changes; light/dark unaffected (role swap only touches font family/weight, not color).
- UI Copy Brevity: checked above (Found & fixed, last bullet) — no label-fit issue from the weight/face change.
- Data model/migrations, background work/widgets/notifications, deprecated APIs: untouched — N/A.
- Spec review: `HODITH_SPEC.md` doesn't describe typography roles at this level of detail; no divergence to reconcile.
- Tests: no behavior change — a style-role correction, not new logic — so no new test coverage; existing Compose UI tests for Case Detail/Insights/Watches/Share tabs assert content and interaction, not font family, and stay unaffected.

**Docs updated:** PROGRESS.md (new Standalone item for the two deferred findings); this entry (oldest entry, `fix/big-picture-selector-chip-contrast`, removed per the 5-entry retention rule).

---

## fix/widget-case-name-overlap

**Scope:** User-reported bug: in both widget "add/edit" configure dialogs (List widget's multi-select `Switch` row, Single-case widget's single-select `RadioButton` row), a long Case name had no width constraint on its label `Text`, so it was measured at its full intrinsic width and could grow past the row's own bounds, overlapping the toggle/radio control instead of stopping short of it.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (3 changed files, 2 new test files). `ktlintCheck`, `lintDebug`, and `test` (full unit suite) all pass. `connectedDebugAndroidTest` not run this pass — no emulator/device was available in this session; reasoned through (not run) compatibility with the existing `ListWidgetConfigureFlowTest`/`SingleCaseWidgetConfigureFlowTest` real-activity flow tests below, left for the user's own device run before merge, per this repo's standing instrumented-test convention.

**Found & fixed:**
- `ui/common/ToggleRow.kt`'s label `Text` (shared by every `Switch`-based label row in the app — Big Picture, Case Edit, Case Detail, Share tabs, and now the List widget's case picker) gained `Modifier.weight(1f)`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, matching the established idiom elsewhere in the codebase (`WatchesTab.kt`, `InsightsTab.kt`, `HomeScreen.kt`).
- `ListWidgetConfigureActivity.kt`'s private `CasePickerRow` was hand-rolling the exact `Row`/`Arrangement.SpaceBetween`/`Switch` shape `ToggleRow` already provides — duplication the original bug exposed. Replaced with a direct `ToggleRow(...)` call, removing five now-unused imports (`Arrangement`, `Row`, `fillMaxWidth`, `Switch`, `Alignment`, `Modifier`) along with it.
- `SingleCaseWidgetConfigureActivity.kt`'s `CasePickerRow` (label then `RadioButton`, no description) can't reuse `ToggleRow` (`Switch`-specific) or the existing `RadioOptionRow` (radio-first, mandatory description, a different shape entirely — see Considered and declined) — given the same `weight`/`maxLines`/`overflow` treatment in place instead.
- Both `rowControlNode`/`clickRowControl` test helpers (`WidgetConfigureTestFixtures.kt`) that the existing flow tests use were re-read against this change: `ListWidgetConfigureActivity`'s row now resolves to `ToggleRow`'s single merged (`Modifier.toggleable`) node, so `isToggleable()` + the text-bounds-overlap match still resolves to the same node as before (the weight change only narrows the inner `Text`, not the outer row's own bounds). `SingleCaseWidgetConfigureActivity`'s row has no row-level merge, so `isSelectable()` still matches the `RadioButton` directly; its position is unchanged by the fix, which only ever pulls the label in — if anything, this makes the vertical-bounds heuristic more reliable, not less, since the label can no longer wrap/grow unpredictably.

**Deferred:**
- `connectedDebugAndroidTest` — see Walked above; needs the user's device, same as the most recent PDF-export passes.

**Considered and declined:**
- Reusing `RadioOptionRow` for the single-case widget's row instead of fixing `CasePickerRow` in place: `RadioOptionRow` puts the `RadioButton` first in a fixed-width slot with a mandatory description line below the label — a different shape from this row's icon+name-then-radio, no-description layout. Forcing the fit would mean a bigger UI change than the bug called for.

**Checks:**
- Duplication: the `ListWidgetConfigureActivity` fix above removes a duplicated `Row` shape by routing through `ToggleRow`; the single-case row's near-identical-but-not-quite shape was evaluated for the same treatment and declined (see above).
- Decoupling: no `ViewModel`/`Repository`/`domain` code touched — layout-only, inside existing Composables.
- Complexity: no new composables; `ToggleRow`/both `CasePickerRow`s stay well under the line-count threshold.
- Hygiene: `git status` clean throughout; no secrets or local paths; `ktlintCheck` caught no stray unused imports after the manual cleanup above.
- Naming: two new `internal const val ..._TAG` constants (`TOGGLE_ROW_SWITCH_TAG`, `CASE_PICKER_RADIO_TAG`) follow the existing `HISTORY_SHARE_PDF_TOGGLE_TAG`/`SECTION_TOGGLE_TAG_PREFIX` convention, added purely for direct test access to each row's control; `SingleCaseWidgetConfigureActivity.CasePickerRow` changed from `private` to `internal` for the same reason, mirroring `SegmentedChoiceRow`'s existing public visibility for testability.
- Accessibility: no tap-target size changes; the fix only prevents the label from encroaching on the existing `Switch`/`RadioButton` footprint.
- UI Copy Brevity: N/A — no fixed UI label text changed; the truncated string is user-entered Case data, not app copy.
- Hardcoded values, data model/migrations, deprecated APIs, spec review: untouched — N/A. `HODITH_SPEC.md` doesn't describe either dialog's row layout at this level of detail, so no divergence to reconcile.
- Background work, widgets & notifications: the actual Glance widget tiles (`ListWidget.kt`, `SingleCaseWidget.kt`) were checked for the same bug and don't have it — `ListWidget.kt`'s case-name `Text` already has `maxLines = 1` inside a `.defaultWeight()` `Column`, and `SingleCaseWidget.kt` never displays the case name on-tile at all — so neither needed a change; Plain-light-only theming and `logFlow` handling are untouched either way.
- Tests: two new instrumented Compose tests (`ToggleRowTest`, `CasePickerRowTest`), both `@UiTest`-tagged, bounds-based (not click-based) regressions modeled on `SegmentedChoiceRowTest`'s pattern — deliberately sidestepping the known `ToggleRow` merge-reliability caveat already documented in TESTING.md's "Known environment issues" rather than relying on click dispatch. Existing flow tests reviewed for compatibility, not re-run (see Walked/Deferred above). No `@Smoke` tag added — no shared-component row test in this codebase carries one.

**Docs updated:** TESTING.md (shared-components row gains `ToggleRowTest`; the widget chrome/trampoline row gains `CasePickerRowTest`); this entry (oldest entry, `fix/intensity-toggle-gating`, removed per the 5-entry retention rule).

