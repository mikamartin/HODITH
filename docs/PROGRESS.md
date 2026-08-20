# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

Each item carries a trailer: **Branch** (the branch to open for it), **Complexity** (S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week — same scale as HODITH_SPEC §17), and **Priority** (High = gates the first release or corrects something wrong today · Medium = worth doing before alpha · Low = cosmetic or can wait). Then **Plan** (the main points), **Tests** (what coverage the change needs or breaks), and **Concern** where there is one.

## Recommended order

Sequencing matters more than usual here — several items collide in the same files, and two of them are cheap now and expensive after the first release.

1. **Auto-backup disclosure fix** — the app currently makes a privacy claim that isn't accurate. Small, and shouldn't wait behind the full toggle feature.
2. **The QA audit's own findings** — the audit has run (all sections but the mutation spot checks), so the Testing and Shared UI logic sections below are no longer speculative. Take `refactor/extract-ui-input-logic` early: it's the only audit finding that contains a live user-facing bug, and it touches the log sheet, which several other items also touch.
3. Small fixes: Case Detail tab order, dialog spacing, close-action copy, app icon.
4. Share card sizing, then the cloud-backup toggle.
5. **`test/voice-completeness-by-reflection` immediately before the Voice phrasing review.** The reflection rewrite is what makes the phrasing pass safe — without it, a quarter of the Voice keys have no assertion at all while hundreds of strings are being rewritten.
6. **Voice phrasing review last.** It is a mass edit across a 1945-line file; run it once, after every other copy-touching item on this list has landed, or it conflicts with all of them.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.

  *Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium*

  **Plan** — ~292 keys × 3 voices ≈ 876 strings. Not hard, but big, and it needs a human ear rather than a mechanical pass. Write the rubric first (what "consistent" means per voice: person, tense, sentence length, punctuation and emoji budget, and a locked vocabulary for Case/Hunch/Verdict/Event/Trigger), then audit in slices by screen rather than reading `Voice.kt` top to bottom — the file is grouped by key, so reading it linearly compares the wrong things. Produce a findings list first; fix in a second commit.

  **Tests** — `VoiceTest` today asserts only non-blank and the share-card pronoun rule. A copy audit is the right moment to add the mechanical invariants it can then lock in: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab and button labels, no double spaces or trailing whitespace. Instrumented tests reference `PlainVoice.x` by constant rather than by literal, so copy edits shouldn't break them — confirm that holds everywhere before starting (a grep for hardcoded UI literals in `androidTest`).

  **Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

- [ ] Plain theme's light background/surface colors (`PlainLightBackground` #F4F6F8, `PlainLightSurfaceVariant` #DEE4E7, `PlainLightOnSurfaceVariant` #5B6670 in `Color.kt`) read as slightly blue-grey/murky rather than neutral. Revisit the palette.

  *Branch: `fix/plain-light-palette-neutrality` · Complexity: S to change, M to verify · Priority: Medium*

  **Plan** — decide the target first (true neutral grey vs. warm grey), keeping `primary` #3A6B76 as the only chroma in the scheme. Then note that the three named colors aren't the whole problem: `surfaceVariant` #DEE4E7, `surfaceContainerHigh` #E9ECEE, `outline` #D3DAE0 and `outlineVariant` #E7ECEE are all in the same blue-grey family and are *not* among the named/shared constants, so a fix that only touches the three named ones leaves the rest mismatched. Treat the light scheme as one change.

  **Tests** — there's no automated coverage of colour values and shouldn't be. Contrast is the one mechanically checkable property: add a JVM test asserting WCAG AA contrast for `onSurface`/`onSurfaceVariant` against `surface`/`background`, which is worth having independently of this item. Visual verification is the human's (Compose Previews per theme, plus the widget and a heatmap-bearing screen).

  **Concern** — two blast radii, both easy to miss. (1) The seven `PlainLight*` constants are consumed directly by `WidgetCommon.kt`'s `WidgetPalette`, which renders every Glance widget regardless of the user's in-app theme (DEV_PLAYBOOK §4), so changing them restyles the widgets too. (2) `HeatmapShading.kt`'s `toCellColor` lerps `surfaceVariant → primary`, so `surfaceVariant` is the base of the entire shading ramp — changing it moves every calendar-heatmap cell, rhythm grid cell, intensity cell, *and* their share-card mini-copies, in all three themes' light mode. Neither is a reason not to do it; both are reasons the review pass is wider than "the Plain theme's background".

## Case Detail

- [ ] Reorder Case Detail tabs to Insights → Logs → Hunch (currently Logs → Insights → Hunch — `LOG_TAB`/`INSIGHTS_TAB`/`HUNCH_TAB` constants and the `Tab` declaration order in `CaseDetailScreen.kt`).

  *Branch: `feat/case-detail-tab-order` · Complexity: S · Priority: Medium*

  **Plan** — mechanically trivial: the three constants are `private` to `CaseDetailScreen.kt` with no usages anywhere else in the codebase, so renumbering them plus reordering the `Tab` declarations and the `when` branches is the whole change. The decision hiding inside it is the default landing tab: `selectedTab` initialises to `LOG_TAB`, and the retro-log FAB renders *only* on the Log tab. If reordering also changes what a Case opens on, the primary "add an event" action moves one tap further away, and a brand-new Case would land on the Insights tab's "not enough data" placeholder as its first impression. Recommend reordering the tabs but keeping Log as the initial selection — and if that's wrong, make it an explicit decision rather than a side effect of the constant renumbering.

  **Tests** — `CaseDetailScreenTest` and `CaseDetailInsightsTabTest` both select tabs by label text (`onNodeWithText(PlainVoice.caseDetailInsightsTabLabel).performClick()`), so they're order-agnostic and should pass unchanged. That's a good sign, but it also means nothing currently pins the default tab: **add a test asserting which tab is selected on open, and that the FAB is present there** — otherwise this change can silently alter the landing tab and the suite stays green. Small, and it's the one behaviour this item actually risks.

## Shared UI logic

- [ ] Pure transformations living inline in composables, untested and independently reimplemented — the QA audit's §5 sweep. Four sites, all the same class of drift the `BigPictureFilterState`/`AcronymText` extractions were meant to fix:
  - **Digit filtering, twice, with different rules.** `LogDetailSheet`'s duration field does `it.filter(Char::isDigit)` uncapped; `TriggersScreen`'s custom-window field does `it.filter(Char::isDigit).take(3)`. The uncapped one is a live input bug, not just a test gap: type enough digits and `computeEndedAt`'s `toIntOrNull()` overflows to null, so the event saves with **no duration and no feedback**.
  - **Tag-name normalization, three times.** `LogDetailSheet`'s `onAddTag` trims and dedupes **case-sensitively**; `LogDetailViewModel.tagDiff` trims and drops blanks; `RoomHodithRepository.addTagToEvent` trims again. The `tags` table has a unique index on `name` and Case Edit's duplicate-name check is case-insensitive, so the sheet is the odd one out — it will happily show "Coffee" and "coffee" as two chips.
  - **Tag suggestion filtering** in `TagsSection` — pure, and reachable only through the instrumented sheet tests.
  - **Future-day/week trimming** inline in `BigPictureGrid` (three separate expressions: the week filter, the day-cell gate, the week-dialog's `validDays`), while the same rule in `InsightsTabState`'s `heatmapMonths` is a plain function with unit tests.

  *Branch: `refactor/extract-ui-input-logic` · Complexity: M · Priority: Medium*

  **Plan** — follow the `BigPictureFilterState.kt`/`AcronymText.kt` precedent: one plain-Kotlin function per transformation, filed next to its screen, with a direct unit test. Four extractions, in that order — the digit filter (shared, with an explicit cap) is the one carrying the real bug, so it's worth landing even if the rest slips.

  **Tests** — new JVM tests per extracted function, and each gets the same one-mutation spot check the audit's §2 sample gets before it counts as validated rather than merely written. The existing instrumented tests should pass unchanged, since none of this changes rendered output — except the duration cap, which is a real behaviour change and needs its own test.

  **Concern** — two of these are behaviour changes wearing a refactor's clothes. Decide the duration cap's value first (it's a product question: what's the longest duration worth typing?), and confirm case-insensitive tag matching is actually wanted — it matches the unique index and Case Edit, but it means typing "Coffee" against an existing "coffee" silently resolves to the existing tag rather than creating what the user typed.

## Big Picture

- [ ] Cases/Tags filter dialog (`BigPictureGrid.kt`'s `InfoDialog` + `BulkSelectionToggle`) has a visually large gap between the dialog title and the Select all/Clear all row — it's Material3 `AlertDialog`'s default title→content spacing, not a custom Spacer HODITH added. `InfoDialog` is shared across every Big Picture info dialog (month/day/week detail too, not just Cases/Tags), so a fix needs to check it doesn't regress those.

  *Branch: `fix/big-picture-filter-dialog-spacing` · Complexity: S · Priority: Low*

  **Plan** — the gap is that default title→content spacing *plus* `BulkSelectionToggle`'s `TextButton`, which carries Material3's 48dp minimum touch height and its own content padding, so the whitespace stacks rather than being one culprit. That points the cheapest fix at the call site rather than the shared composable: change what the Cases/Tags dialogs put at the *top* of their content — move Select all/Clear all into the dialog's button row alongside Close, or into the title slot as a trailing action — instead of restyling `InfoDialog`'s padding.

  **Tests** — spacing isn't assertable and shouldn't be; this needs human visual verification. But `BigPictureScreenTest` drives dialog dismissal by text through thirteen call sites, so if the fix relocates the bulk toggle into the button row, those interactions need re-checking. Add a test that Select all/Clear all is still reachable and still functions from wherever it ends up.

  **Concern** — `InfoDialog` has five call sites in `BigPictureGrid.kt` plus `SectionWithInfo.kt` (the Case Edit info icons), so a change to the shared composable regresses screens this item never mentions. Prefer the call-site fix for that reason alone.

- [ ] Shorten the shared dialog-dismiss Voice string (`bigPictureDialogCloseAction`) to "Close" in all three voices — currently Plain already says "Close", Intense says "Seal it shut", Bright says "Got it, close this". Update all three in the same commit per the Voice layer rule.

  *Branch: `chore/voice-close-action-copy` · Complexity: XS · Priority: Low*

  **Plan** — three one-line edits. But finish the thought first: if all three voices say "Close", the key no longer varies by voice, and it exists solely to *override* `InfoDialog`'s `dismissLabel` default, which is `infoDialogDismissAction` ("Got it" / "Understood" / "Got it!"). At that point the honest change is to **delete `bigPictureDialogCloseAction` entirely and drop the five `dismissLabel =` overrides**, so Big Picture's dialogs use the shared default like every other `InfoDialog` — unless the intent is specifically that Big Picture's dialogs read flatter than the rest of the app, in which case keeping a voice-invariant key is a deliberate choice worth a comment. Either outcome is fine; keeping a duplicate key by accident isn't.

  **Tests** — `BigPictureScreenTest` references `PlainVoice.bigPictureDialogCloseAction` thirteen times. Shortening the string needs no test change (the constant is referenced, not the literal); deleting the key means swapping those to `PlainVoice.infoDialogDismissAction`. `VoiceTest`'s non-blank list also needs the line removed if the key goes.

## Share

- [ ] Share card doesn't shrink for sparse content: `ShareCardTemplate.kt`'s `heightIn(min = STORY_MIN_HEIGHT/SQUARE_MIN_HEIGHT)` enforces each format's minimum aspect-ratio height regardless of how many optional sections are selected; the `weight(1f)` wrapper around the header+sections column (meant to keep the footer pinned to the bottom instead of stranding it mid-card) just relocates the resulting slack to blank space above the footer rather than removing it. With most sections toggled off, that reads as a big empty gap. Needs a layout approach that lets the card shrink toward actual content height instead of the format's fixed minimum. Not only cosmetic: both deferred share items in HODITH_SPEC §17 (Big Picture sharing, animated story export) sit behind this, since each would inherit the same fixed-minimum behaviour.

  *Branch: `fix/share-card-sparse-content-height` · Complexity: M · Priority: Medium*

  **Plan** — prototype before writing production layout code (CLAUDE.md's "validate cheaply first"), because "shrink toward content height" may not be the right answer for Story. 9:16 *is* the story canvas; an image shorter than that gets letterboxed by the destination app anyway, so shrinking Story improves the in-app preview and changes nothing about the shared result. Three candidates, cheapest first, all testable in a Compose Preview: (a) drop the minimum entirely for both formats; (b) keep Story's ratio but distribute the slack — centre the content block vertically rather than pooling it above the footer; (c) keep the ratio and scale content up to fill when few sections are selected. Recommend evaluating (b) and (c) against (a) rather than assuming (a).

  **Tests** — the harness already exists: `ShareCardTemplateTest.storyIsTallerThanSquareForIdenticalContent` measures rendered heights in an instrumented test. Extend that pattern — a minimal-sections card measurably shorter than a maximal-sections card (if shrinking wins), the footer sitting at the bottom edge in both cases, and Square still respecting its floor if the floor is kept.

  **Concern** — whichever way it goes, the outcome is inherited by §17's Big Picture sharing and animated story export, so record the decision in the spec rather than only in the layout code. And confirm the chosen approach against the actual destination apps before committing to it — a fix that only looks better in HODITH's preview is a worse outcome than leaving it.

## Testing

- [ ] **Finish the QA audit's mutation spot checks.** [QA_AUDIT_RULES.md](QA_AUDIT_RULES.md) §1 and §3–§7 have run — their findings are the items in this section and in Shared UI logic, and their doc-hygiene fixes landed in TESTING.md and HODITH_SPEC.md §11. §2 (mutation spot checks) is the one section that can't run read-only: it needs source edits plus a sequential `./gradlew test` per sampled file, so it is still outstanding. Findings go here rather than into [QA_AUDIT_BACKLOG.md](QA_AUDIT_BACKLOG.md), which now just points back at this file — the outstanding-work roadmap stays in one place.

  *Branch: `chore/qa-audit-mutation-checks` · Complexity: M · Priority: Medium*

  **Plan** — sample is pre-selected so it can be picked up without re-deriving: `VerdictEngineTest`, `TriggerEngineTest`, `CheckInTest` (core mechanics), `StatsEngineTest`, `InsightsEngineTest` (heaviest pure-math surfaces), `NotificationEvaluatorTest` (the only orchestration tested against Fakes), `LogDetailViewModelTest` (the largest ViewModel suite), and one Room-instrumented DAO class. One mutation at a time — flipped boolean, off-by-one on a boundary, swapped operator — run that file's tests, confirm a clear failure, revert before the next.

  **Known target** — `evaluateAtLeast`'s rolling-window formula (`now - windowDays * MILLIS_PER_DAY`) exists twice: `TriggerEngine.evaluateAtLeast` computes it to *count* the events and `NotificationEvaluator.evaluateTriggers` recomputes it to *fetch* them. Change one and the fetch and the count disagree silently; the pre-filter also partly masks a mutation to the counter, which is the duplicated-code-path case §2 warns about. It's a few lines to collapse into one shared helper, and it *is* the finding, so fix it on this branch rather than spinning up another.

  **Concern** — `git status` must be clean before committing; every mutation is transient. And never run Gradle tasks in parallel (CLAUDE.md) — the per-file test runs are strictly sequential, which is most of this item's time cost.

- [ ] Instrumented suite hygiene: two audit findings in `app/src/androidTest`, both test-only, no production risk. (1) **Copy-pasted helpers** — `WidgetConfigureTestFixtures.kt` is the shared-fixture precedent for the widget-configure tier, but `renderedView()` is redefined in three widget test classes, and `collectText()`, `findClickableAncestorOfText()` and `bindAndRenderSingleCaseWidget()` in two each, plus `grantPostNotificationsPermission()` in both notification classes. (2) **Two tests that pass even when their setup click misses** — `BigPictureScreenTest.deselectingACase_resetsStaleTagSelection_insteadOfEmptyingTheGrid` narrows tags to "solo" and *then* deselects Tea with no assertion in between, so if the first click no-ops the end state still shows "work note" and the test is green; `bulkToggle_selectAll_reselectsEveryTag` has the identical shape.

  *Branch: `test/instrumented-suite-hygiene` · Complexity: M · Priority: Medium*

  **Plan** — extend `WidgetConfigureTestFixtures.kt` (or add a sibling render-support file) with the shared widget helpers and import them from `ListWidgetConfigureFlowTest`, `SingleCaseWidgetConfigureFlowTest`, `WidgetActionsFlowTest` and `WidgetChromeNavigationTest`; move `grantPostNotificationsPermission()` into a shared notification-test helper. Separately, add the missing intermediate assertion to the two BigPicture tests, then sweep the rest of `BigPictureScreenTest`/`SettingsScreenTest`/`SharePreviewScreenTest` for the same shape — the structural review flagged those three as where multi-step sequences cluster.

  **Tests** — this *is* test work; verification is the same suite still passing, so it needs a real `connectedDebugAndroidTest` run rather than read-only review.

  **Concern** — the added assertions have to be able to fail. Assert the *effect* of the setup click (the chip's state flipping, a filtered row disappearing), not merely that the node it targeted still exists — the latter passes just as happily on a missed tap.

- [ ] Insights instrumented coverage gap: pure-Kotlin `StatsEngineTest`/`InsightsEngineTest`/`InsightsTabStateTest` (54 tests) already assert exact computed values for every Insights metric, but the instrumented `CaseDetailInsightsTabTest` (13 tests) is almost entirely card visibility/gating (`assertExists`/`assertDoesNotExist`) — only 2 tests read actual rendered text (the trend sentence, the gap-shift note). No instrumented test reads the Duration/Intensity/Frequency cards' displayed numbers off the real UI against known seeded data, so a wiring/formatting bug between correct state and the rendered `Text` wouldn't be caught by anything today.

  *Branch: `test/insights-tab-rendered-values` · Complexity: M · Priority: Medium-High*

  **Plan** — target the cards that format numbers, since that's where the untested gap between correct state and rendered text lives: Duration (`formatMinutesDuration`), Intensity, Frequency (`formatRate`), and the Gaps/Streaks values. Seed known data, assert the exact displayed string. Roughly six to eight new tests on top of the existing thirteen.

  **Tests** — the trap to avoid is the one that makes this cheap and worthless: a test that builds its expected string by calling the same formatter the UI calls is a tautology that passes through any formatter change. At least the value-formatting assertions must hardcode the expected literal ("1h 30m", "2.5 / week") so a formatting regression actually fails. Use the `PlainVoice.x(...)` constant only where the sentence *around* the number is what's being pinned.

  **Concern** — `CaseDetailInsightsTabTest`'s class comment currently justifies the gap ("the underlying math is already covered exhaustively... on the JVM"). That reasoning is what produced the hole — correct math plus a wiring bug still ships wrong numbers — so the comment has to be corrected in the same commit, or the gap grows back.

  **Audit note** — the QA audit's structural review confirmed this one and found no sibling of the same shape elsewhere: the other screen suites do read rendered values, so this is an isolated gap rather than a pattern to sweep.

- [ ] `VoiceTest`'s completeness check is hand-maintained and has already rotted. `every voice has a non-blank string for every key` is a hand-written list of `assertTrue(voice.someKey.isNotBlank())` lines, and the QA audit measured what it actually reaches: **223 of the `Voice` interface's 292 keys — 69 are uncovered.** Not a future risk; whole surfaces are missing today. All 25 Triggers keys, all 7 About keys, all 10 Settings import/export keys, every widget-configure key, plus `bigPictureDialogCloseAction` and both notification action labels. Replace the manual list with reflection over the `Voice` interface's properties (handling the parameterised `fun` keys separately), so every key is covered by construction.

  *Branch: `test/voice-completeness-by-reflection` · Complexity: S · Priority: Medium-High — gates the Voice phrasing review*

  **Rationale for adding it** — surfaced while sizing the Voice phrasing review above, then quantified by the QA audit. The Voice layer's one hard rule (CLAUDE.md: every string in all three voices, same commit) is enforced only by the compiler for *existence* and by this list for *content*. The compiler half is solid; the manual half degrades fastest exactly during a large copy pass — so this lands *before* the phrasing audit, not after.

  **Tests** — beyond the reflection rewrite, add a cross-voice-uniqueness check: no key returning an identical string in all three voices, which catches the copy-paste that a compiler-satisfying "add the key to all three" pass invites. Nothing asserts that today. Expect the reflection rewrite to fail on first run against the 69 keys above — that's the point, not a defect. Add the corresponding row to TESTING.md's Voice line once both land.

## Settings

- [ ] **Correct the auto-backup disclosure.** Independent of the toggle below, the app currently says something that isn't accurate. About's privacy copy reads "Everything stays on your phone. HODITH has no network access and sends nothing anywhere" (Plain), "Nothing. No network, no signal sent outward — every case stays sealed here" (Intense), "Everything stays right here on your phone — no internet, no sneaky data stuff!" (Bright), and README says "no cloud". With `android:allowBackup="true"` and an unrestricted `data_extraction_rules.xml` (`<cloud-backup />` + `<device-transfer />` with no exclusions), the Room database *does* reach the user's Google account whenever device backup is enabled. Separately, HODITH_SPEC §16 states "Android auto-backup enabled — documented on the About screen" and the About screen documents no such thing — an unintentional spec/implementation divergence, which CLAUDE.md classifies as a bug to fix rather than a spec update.

  *Branch: `fix/about-backup-disclosure` · Complexity: S · Priority: High — ahead of the toggle*

  **Rationale for splitting it out** — the toggle below is an M-sized feature with open product questions. The inaccurate claim shouldn't sit in the app while those get answered, and a copy correction is independent of whichever way the toggle decision goes.

  **Plan** — rewrite `aboutPrivacyBody` in all three voices to say plainly that HODITH itself sends nothing (still true: no INTERNET permission) *and* that Android's own device backup may include HODITH's data if the user has it on, with how to check. Also audit the two claims that live outside this repo: the hosted privacy policy at the URL `AboutScreen.kt` links to, and the Play data-safety answers, both of which probably repeat the same claim.

  **Tests** — `AboutScreenTest` asserts section content; extend it for the new disclosure. `VoiceTest`'s non-blank list already covers the keys.

- [ ] Add a Settings toggle for Android's OS-level cloud backup: HODITH has no INTERNET permission and doesn't sync anything itself, but `allowBackup="true"` plus an unrestricted `data_extraction_rules.xml` mean the OS's own device backup (if the user has "Back up to Google One" on) currently includes HODITH's local database like any other app's. EarnIt already implements this pattern (a custom `BackupAgent` honoring a persisted toggle, since Android has no runtime API for an app to flip its own default Auto Backup on/off) — review that implementation as a reference. Open sub-decisions: default toggle state, and whether device-transfer (new-phone setup) is gated by the same toggle or always allowed independently of cloud backup. Update About/README once resolved to state plainly whether HODITH data can reach the user's Google account and how to prevent it.

  *Branch: `feat/cloud-backup-toggle` · Complexity: M · Priority: Medium (High if the disclosure fix above is skipped)*

  **Plan** — the toggle value slots into the existing `DataStoreSettingsRepository` alongside theme and default check-in interval, with a `BackupAgent` reading it; the Settings row goes in the Data plank next to Export/Import/Delete. The two open sub-decisions are the actual work: **default state** (defaulting off contradicts §16's "auto-backup enabled" and silently loses new-phone restore; defaulting on contradicts the app's current framing) and **device-transfer** (`<device-transfer />` is the new-phone path and is arguably a different question from `<cloud-backup />` — data moving phone-to-phone doesn't linger in a Google account the way a cloud backup does, so gating them together may be stricter than users want).

  **Tests** — the toggle's persistence and the ViewModel wiring are testable exactly like the existing theme/check-in preferences (`DataStoreSettingsRepository`, `SettingsViewModelTest`, `SettingsScreenTest`). `BackupAgent` behaviour is not practically unit-testable; verification is `adb shell bmgr` (enable, `backupnow`, wipe, restore) — document that procedure in DEV_PLAYBOOK the way the widget `grantbind` procedure is documented, and add the journey to MANUAL_TEST_PLAN.

  **Concern** — EarnIt is a different repository, so "review that implementation as a reference" is a real time cost, not a free lookup; budget for it. And whichever default wins, five things move together: HODITH_SPEC §16, the About copy, README, the hosted privacy policy, and the Play data-safety form. Missing one of the two off-repo ones is the likely failure.

- [ ] Rate the App is still a placeholder row (shows a "coming soon" snackbar) — needs a real destination once there's a Play Store listing.

  *Branch: `feat/rate-app-play-link` · Complexity: S · Priority: Blocked — do it in release prep*

  **Plan** — genuinely blocked on the listing existing, so it belongs in the release-prep branch rather than as standalone work. Two implementations: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review means adding a Google Play Services dependency to an app that currently ships none and whose whole positioning is "no network", which makes it a positioning decision rather than a technical one.

  **Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar, so that test changes rather than gets added to: assert the intent is launched (Espresso `Intents`). Note the row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`, which needs no change but shouldn't be mistaken for a second call site.

  **Concern** — In-App Review is quota-limited and no-ops silently once the quota is hit, which makes manual verification unreliable; the deep link is trivially verifiable. Another reason to prefer it.

## App icon

- [ ] Dark spot visible on the circle/handle at larger icon sizes — the invisible lens ring fix addressed a related issue, but this artifact remains at bigger resolutions.

  *Branch: `fix/launcher-icon-handle-overlap` · Complexity: S · Priority: Low*

  **Plan** — the vector gives a concrete diagnosis. In `ic_launcher_foreground.xml` the handle (`M62,62 L74,74`, `strokeWidth 9`, `strokeAlpha 0.75`) is drawn before the ring, and the ring stroke (`strokeWidth 6.5`, `strokeAlpha 0.7`) spans radius ~15.75–22.25 around centre (48,48) — i.e. roughly 59.1 to 63.7 along the 45° diagonal. The handle's first ~1.7 units start *inside* that band, so two translucent dark strokes composite into a darker patch exactly where the handle meets the ring. It only reads as a spot at large sizes because at launcher resolution it's sub-pixel. Three fixes, cheapest first: (a) start the handle outside the ring's outer edge (`M64.5,64.5`); (b) draw the handle *after* the ring at full opacity so it occludes rather than blends; (c) make both strokes fully opaque in a slightly lighter tone so overlap can't darken.

  **Tests** — nothing automatable. Verification is rendering the vector at several sizes (Compose Preview or the asset preview) and a human looking at it.

  **Concern** — the same glyph exists in three places and they have to stay consistent. `ic_launcher_monochrome.xml` repeats the identical geometry at full-opacity white (so it has no dark spot today, but inherits any geometry change — its comment already commits to matching), and `ic_launcher_foreground.xml` is reused directly as the splash icon via `Theme.Hodith.Splash`, which is a third size to check.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
