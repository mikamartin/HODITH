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

## chore/java-time-to-kotlinx-datetime

**Scope:** Second of the two migrations clearing the way for HODITH's planned Compose Multiplatform iOS route (HODITH_SPEC.md §19) — `java.time` has no Kotlin/Native target. Replaced every `java.time` usage in `app/src/main/kotlin` with `kotlinx-datetime` (0.7.1): the 10-file domain-layer math (`CalendarGrid`, `CalendarMath`, `ExportFileTimestamp`, `Insights`, `InsightsEngine`, `Stats`, `StatsEngine`, `Trends`, `TrendsEngine`, `VerdictEngine`), the `viewmodel`/`ui` cascade wherever a migrated domain type crosses that boundary, and `CsvBackupSerializer.kt`'s export formatting. Landed as a single branch staged in two logical commits (domain first, then the cascade), per the pre-landing design decision on scope.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (~60 changed `.kt` files across `main`/`test`/`androidTest`, 2 new files, 5 docs). `ktlintCheck`, `lintDebug`, `test` (full unit suite, 1225 tests), and `assembleDebug` all pass; `compileDebugAndroidTestKotlin` verified separately (the full instrumented source set compiles). No instrumented run against a device this pass — domain/viewmodel risk (DST, zone-offset math, calendar-day bucketing) is pure Kotlin and fully covered by the JVM suite; the only Android-surface pieces touched (Material3 DatePicker/DateRangePicker interop, Compose Previews) have no date-math risk of their own.

**Found & fixed:**
- Domain layer ported file by file, each file's own DST/zone-boundary tests read (not just re-run) before moving to the next, per the Concern this item's PROGRESS.md entry flagged.
- `TemporalAdjusters.previousOrSame`/`nextOrSame` (no `kotlinx-datetime` equivalent) hand-rolled once via `DayOfWeek.isoDayNumber` in `domain/CalendarMath.kt`, shared by `CalendarGrid.kt`, `HomeViewModel.kt`, and a third use site found mid-pass, `data/demo/DemoDataSeeder.kt`'s weekend-showcase generator — not in the original 35-file estimate.
- `YearMonth` (no `kotlinx-datetime` equivalent) hand-rolled as a small public `domain/YearMonth.kt` value type rather than reusing `LocalDate` with an implied day-of-month convention, keeping the type-level "this is a month" guarantee `BigPictureFilterState`/`InsightsTabState`/`CalendarGrid` all rely on.
- `EventTimeFormat.kt`'s full formatter set ported to `kotlinx-datetime`'s format DSL; the two spots that previously followed the platform locale (the frequency-chart month label, the rhythm-grid/share-card weekday labels) are now fixed English, per the pre-landing locale decision — converging with the rest of the app's already `Locale.US`-fixed display. The now-dead `LocalLocale.current.platformLocale` reads and their `locale` parameters were traced and removed from `ShareCardTemplate.kt`/`InsightsTab.kt` in the same commit rather than left for a later pass.
- `CsvBackupSerializer.kt`'s ISO-offset timestamp ported to `DateTimeComponents.Format` with explicit `offsetHours()`/`offsetMinutesOfHour()` fields, preserving the existing "a zero offset renders `+00:00`, never `Z`" behavior exactly (added a test pinning it, since no shortcut format does this by default).
- A real formatting bug caught by the existing `EventTimeFormatTest` suite, not introduced silently: `kotlinx-datetime`'s `amPmHour()` format-DSL builder zero-pads by default, unlike `java.time`'s lowercase `h` pattern — produced "08:02 AM" instead of "8:02 AM" until an explicit `Padding.NONE` was added everywhere a 12-hour formatter uses it.
- Two real coverage gaps closed alongside the migration (per this item's own acceptance criteria): `ExportFileTimestamp.kt` had no test file at all before this pass — added `ExportFileTimestampTest.kt`; `ShareCardTemplateTest.kt`'s narrow-weekday-label assertion built its expected string from `Locale.getDefault()`, which would have silently diverged from production's now-fixed-English output on a non-English test device — rewritten against the same `englishNarrow()` helper production uses.
- `TESTING.md`'s Deferrals list carried a now-resolved entry about the frequency chart's month label only being locale-tested in `Locale.US` — removed outright rather than left describing a condition that stopped being true the moment the label itself stopped following locale.

**Deferred:** nothing new. `StatsEngineTest`'s pre-existing lack of a named-zone DST case (noted during planning) remains a pre-existing gap, not newly introduced or masked by the port — left for a future QA audit pass per this item's own Tests note, not backfilled here.

**Considered and declined:**
- Switching every `kotlinx.datetime.Instant`/`Clock` reference to the newer `kotlin.time.Instant`/`kotlin.time.Clock` the library now re-exports from (0.7.1 deprecates its own prior aliases in favor of the stdlib types) — declined for this pass. The deprecation is a warning, not an error; `kotlinx.datetime.Instant` keeps every file in this already-large diff internally consistent with `TimeZone`/`LocalDate`/`UtcOffset`, none of which have a stdlib equivalent to migrate to instead. Left as a known, intentional residual for a separate future pass once the stdlib types are the clearly-settled convention.

**Checks:**
- Duplication: the shared `previousOrSame`/`nextOrSame` extensions and the `englishFull`/`englishNarrow`/`englishAbbreviated` name helpers are each defined once and reused across every call site that needs them, rather than re-derived per file.
- Decoupling: `CLEANUP_CHECKLIST.md`'s `java.time` Decoupling check (this item's own last acceptance-criteria box) turned from a no-op into a real `git grep` command; verified zero hits across `app/src/main/kotlin`. Verdict/watch/stats code still imports nothing from `android.*`; day-bucketing still resolves each event's own captured `utcOffsetMinutes` (now a `UtcOffset`), not the device's current zone.
- Complexity: no new composables or UI structure — a library swap under existing signatures, plus one new pure-Kotlin value type (`YearMonth`).
- Dead code & hygiene: `ktlintCheck`'s unused-import rule found nothing stray after the pass; the `LocalLocale`/`Locale` cleanup above was this section's main finding; `git status` clean throughout.
- Naming: `domain/YearMonth.kt` follows the existing `domain/` file-per-concept convention; no new `Voice` keys (no UI copy changed).
- Hardcoded values: none introduced — the fixed-English name tables are `kotlinx-datetime`'s own `MonthNames`/`DayOfWeekNames` constants, not ad hoc literals.
- Accessibility, Data model/migrations, Background work/widgets/notifications: untouched — N/A.
- UI Copy Brevity: N/A — no `Voice` copy changed; the two hardcoded-English spots render the same English text length as before for English-locale users.
- Deprecated APIs: addressed above (Considered and declined) for the `kotlin.time` aliases; the one other new warning (`LocalDate.monthNumber` deprecated in favor of `.month.number`) was trivial and fixed directly rather than deferred.
- Spec review: HODITH_SPEC.md §19 updated to state the migration as complete, including the locale decision's outcome.
- Tests: every existing domain/viewmodel/ui DST and zone-boundary test re-verified by reading its assertions against the ported implementation, not just by a green re-run (the Concern this item flagged); two new unit tests added (above) for the two real coverage gaps the migration surfaced.

**Docs updated:** HODITH_SPEC.md (§19); PROGRESS.md (resolved item removed entirely); TESTING.md (Deferrals entry removed, two new unit tests noted in the Time formatting / Export-import rows); CLEANUP_CHECKLIST.md (`java.time` Decoupling check turned from a no-op into a real enforcement); DEV_PLAYBOOK.md (§7 version matrix gains the `kotlinx-datetime` row); this entry (oldest entry, `fix/cases-intense-typography-roles`, removed per the 5-entry retention rule).

---

## chore/moshi-to-kotlinx-serialization

**Scope:** First of two migrations clearing the way for HODITH's planned Compose Multiplatform iOS route (new HODITH_SPEC.md §19) — Moshi has no Kotlin/Native target. Swapped Moshi for kotlinx.serialization across the backup export/import path only; the sibling `java.time` → `kotlinx-datetime` migration is deliberately deferred as its own PROGRESS.md item given its size.

**Walked:** CLEANUP_CHECKLIST.md section by section against the diff (13 changed `.kt`/`.kts`/`.toml` files, 6 docs). `ktlintCheck`, `lintDebug`, `test` (full unit suite), and `assembleDebug` all pass. No instrumented test run needed — the one androidTest file touched (`BackupImportIntegrationTest`) only swaps its `Json` construction to match; left for the user's own device run before merge per this repo's standing convention, though the equivalent JVM-side coverage (`BackupSerializerTest`) already proves the real logic change.

**Found & fixed:**
- `BackupSerializer.kt`: rewritten on `kotlinx.serialization.json.Json`; `BackupUpgradeStep.upgrade` now takes/returns `JsonObject` instead of a raw `Map<String, Any?>`, which also removes the Double-coercion workaround Moshi's generic map adapter needed for `peekSchemaVersion`.
- 5 Room entities + `BackupData`: `@JsonClass(generateAdapter = true)` → `@Serializable`.
- `SettingsViewModel.kt`: collapsed the `JsonDataException`/`IOException` two-catch import-failure handling to a single `SerializationException` catch, since kotlinx.serialization throws one hierarchy for both malformed syntax and missing-field errors.
- Gradle: Moshi and its KSP codegen removed; `kotlinx-serialization-json` and the Kotlin serialization plugin added; the pre-existing `kotlinx-serialization-core` version force (originally scoped to `androidTest` only, for Room's `MigrationTestHelper`) widened to apply repo-wide now that production code depends on the same library.
- **Caught mid-pass, not by the checklist walk itself:** kotlinx.serialization's `Json` omits a field equal to its default value unless `encodeDefaults = true` is set — unlike Moshi's codegen, which always writes every property. Without that flag, every real export would have silently dropped `schemaVersion` (and `WatchEntity.armed`/`metric`), only caught because an existing test (`fromJson with a declared version below current...`) happened to depend on `schemaVersion` actually being present in the JSON text. Fixed by setting `encodeDefaults = true` on every `Json` instance (`SerializationModule`, and the three test files that construct `BackupSerializer` directly).
- **Caught during this same pass's Tests check:** no test exercised `WatchEntity`'s own defaulted fields (`armed`/`metric`) at all — `testBackup()`'s fixture always used `watches = emptyList()`. Initially added a round-trip-equality test, then recognized it was trivially true regardless of the `encodeDefaults` bug (decoding always reconstructs the same Kotlin default whether or not the key was ever in the JSON text) — rewrote it to assert on the serialized JSON text itself instead, and verified by temporarily reverting `encodeDefaults` to `false` and confirming the new assertion (and only that one) then fails.
- HODITH_SPEC.md §19 (new): a broken relative link (`../docs/CLEANUP_CHECKLIST.md`, written as if the file were one level above `docs/` instead of inside it) and a bare-text `PROGRESS.md` reference, both fixed to match every other doc's same-directory link convention.
- DEV_PLAYBOOK.md gotcha #9: corrected ("no production code uses kotlinx.serialization" was no longer true) and reworded to state current fact rather than "also uses... now," which read as changelog narration.
- HODITH_SPEC.md §18's own intro line ("tooling matrix lives in DEV_PLAYBOOK §5") fixed to §7 — a pre-existing stale cross-reference noticed while editing the same section, unrelated to this migration but a one-line fix in a file already being touched.

**Deferred:** nothing.

**Considered and declined:** nothing — this pass's scope (one library swap) left little room for a could-go-either-way call; the design decisions made along the way (`JsonObject` over `Map` for `BackupUpgradeStep`, a repo-wide force over an `androidTest`-scoped one) are covered under Found & fixed since each had a concrete reason, not a close call.

**Checks:**
- Duplication: N/A — no composables, ViewModel logic, or Dao queries touched.
- Decoupling: no `android.*` import added anywhere; `SettingsViewModel.kt`'s catch-block change doesn't reach for a UI type.
- Complexity: N/A — no composables, `LaunchedEffect`, or coroutine-scope code touched.
- Dead code & hygiene: the broken relative link and narrative wording above are this section's findings; no unused imports after ktlint's own check; `git status` clean throughout, including after a temporary sabotage-and-revert check on the new test (confirmed no stray file was left behind).
- Naming: no new files; the new test method follows the existing backtick-sentence convention already used in the same file.
- Hardcoded values: N/A.
- Accessibility: N/A — no UI touched.
- UI Copy Brevity: N/A — no UI copy touched.
- Data model, migrations & privacy: no entity/column added, no schema version bump (confirmed no new `app/schemas/*.json` file after `assembleDebug`); export/import JSON shape unchanged in content, only in encoding library.
- Background Work, Widgets & Notifications: untouched — N/A.
- Deprecated APIs: no new deprecation warnings in the build output beyond one pre-existing, unrelated `@param:` target warning (`DataStoreSettingsRepository.kt`, untouched by this diff).
- Spec review: HODITH_SPEC.md §16/§18 updated off Moshi; new §19 documents the multiplatform roadmap this migration is the first step of.
- Tests: the `encodeDefaults` gap above is this section's main finding — a regression test added and verified to actually catch the regression (see Found & fixed), not just written and trusted.

**Docs updated:** HODITH_SPEC.md (§16, §18, new §19, §18 intro cross-reference fix); CLEANUP_CHECKLIST.md (two new forward-looking Decoupling checks); DEV_PLAYBOOK.md (gotcha #9 correction, §7 intro pointer to the roadmap); TESTING.md (Export/import row's Moshi-specific wording generalized); PROGRESS.md (new Standalone item for the sibling `java.time` migration); this entry (oldest entry, `fix/widget-case-name-overlap`, removed per the 5-entry retention rule).

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
