# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

Each item carries a trailer: **Branch** (the branch to open for it), **Complexity** (S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week — same scale as HODITH_SPEC §17), and **Priority** (High = gates the first release or corrects something wrong today · Medium = worth doing before alpha · Low = cosmetic or can wait). Then **Plan** (the main points), **Tests** (what coverage the change needs or breaks), and **Concern** where there is one.

## Recommended order

Sequencing matters more than usual here — several items collide in the same files, and two of them are cheap now and expensive after the first release.

1. Share card Square redesign (below) — it touches copy, so land it before Voice phrasing.
2. **Voice phrasing review last.** It is a mass edit across a 1819-line file; run it once, after every other copy-touching item on this list has landed, or it conflicts with all of them.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.

  *Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium*

  **Plan** — 294 `Voice` keys total, but only 213 are declared per-voice and need independent authorship (639 strings); the other 81 are shared `get()`/default-body keys (structural chrome — nav labels, field labels, and the like) reviewed once, not per voice. ~720 strings total. Not hard, but big, and it needs a human ear rather than a mechanical pass. Write the rubric first (what "consistent" means per voice: person, tense, sentence length, punctuation and emoji budget, and a locked vocabulary for Case/Hunch/Verdict/Event/Trigger), then audit in slices by screen rather than reading `Voice.kt` top to bottom — the file is grouped by key, so reading it linearly compares the wrong things. Produce a findings list first; fix in a second commit.

  **Tests** — `VoiceTest` today walks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. A copy audit is the right moment to add further mechanical invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab and button labels, no double spaces or trailing whitespace. Instrumented tests reference `PlainVoice.x` by constant rather than by literal, so copy edits shouldn't break them — confirm that holds everywhere before starting (a grep for hardcoded UI literals in `androidTest`).

  **Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

- [ ] Plain theme's light background/surface colors (`PlainLightBackground` #F4F6F8, `PlainLightSurfaceVariant` #DEE4E7, `PlainLightOnSurfaceVariant` #5B6670 in `Color.kt`) read as slightly blue-grey/murky rather than neutral. Revisit the palette.

  *Branch: `fix/plain-light-palette-neutrality` · Complexity: S to change, M to verify · Priority: Medium*

  **Plan** — decide the target first (true neutral grey vs. warm grey), keeping `primary` #3A6B76 as the only chroma in the scheme. Then note that the three named colors aren't the whole problem: `surfaceVariant` #DEE4E7, `surfaceContainerHigh` #E9ECEE, `outline` #D3DAE0 and `outlineVariant` #E7ECEE are all in the same blue-grey family and are *not* among the named/shared constants, so a fix that only touches the three named ones leaves the rest mismatched. Treat the light scheme as one change.

  **Tests** — there's no automated coverage of colour values and shouldn't be. Contrast is the one mechanically checkable property: add a JVM test asserting WCAG AA contrast for `onSurface`/`onSurfaceVariant` against `surface`/`background`, which is worth having independently of this item. Visual verification is the human's (Compose Previews per theme, plus the widget and a heatmap-bearing screen).

  **Concern** — two blast radii, both easy to miss. (1) The seven `PlainLight*` constants are consumed directly by `WidgetCommon.kt`'s `WidgetPalette`, which renders every Glance widget regardless of the user's in-app theme (DEV_PLAYBOOK §4), so changing them restyles the widgets too. (2) `HeatmapShading.kt`'s `toCellColor` lerps `surfaceVariant → primary`, so `surfaceVariant` is the base of the entire shading ramp — changing it moves every calendar-heatmap cell, rhythm grid cell, intensity cell, *and* their share-card mini-copies, in all three themes' light mode. Neither is a reason not to do it; both are reasons the review pass is wider than "the Plain theme's background".

## Share

- [ ] Square format should become a fixed preset — Story stays the one fully customizable, auto-sizing format. Root cause: `shareCardState()` (`ShareCardState.kt`) applies `selectedSections` the same way to both formats, and `SharePreviewScreen.kt`'s `SectionsPicker`/`availableSections` render identical toggles for both. That's a real problem now that Square keeps a 1:1 floor while Story sizes freely to content (see `fix/dialog-spacing-icon-sharecard-sizing`'s commit 3): selecting every Insights section on Square produces a tall rectangle, undermining the format's purpose — Square exists for chat/feed contexts that expect a predictable square shape.

  *Branch: `feat/square-share-card-preset` · Complexity: M · Priority: Medium*

  **Plan** — needs a product decision first: which sections (and in what fixed order) Square always shows. Once decided: show `SectionsPicker` only when `ShareCardFormat.STORY` is selected in `SharePreviewScreen.kt`, and have `shareCardState()` source Square's sections from the fixed preset, independent of `selectedSections`.

  **Tests** — `ShareCardStateTest.kt` needs coverage that Square's output is driven by the preset; `SharePreviewScreenTest.kt` needs coverage that the section picker appears only for Story. `ShareCardTemplateTest.kt`'s Square floor/no-clip coverage should keep passing as-is, since the preset's fixed content is what it already exercises.

  **Concern** — this is as much a product decision as an implementation task, and it touches Voice (Story-only picker copy), so land it before the Voice phrasing audit.

## Testing

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

## Settings

- [ ] **Audit the hosted privacy policy and Play data-safety form.** Both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

  *Branch: none — external content, not a code change · Complexity: XS · Priority: Medium*

  **Plan** — read both against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and update wherever they still claim otherwise.

- [ ] Rate the App is still a placeholder row (shows a "coming soon" snackbar) — needs a real destination once there's a Play Store listing.

  *Branch: `feat/rate-app-play-link` · Complexity: S · Priority: Blocked — do it in release prep*

  **Plan** — genuinely blocked on the listing existing, so it belongs in the release-prep branch rather than as standalone work. Two implementations: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review means adding a Google Play Services dependency to an app that currently ships none and whose whole positioning is "no network", which makes it a positioning decision rather than a technical one.

  **Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar, so that test changes rather than gets added to: assert the intent is launched (Espresso `Intents`). Note the row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`, which needs no change but shouldn't be mistaken for a second call site.

  **Concern** — In-App Review is quota-limited and no-ops silently once the quota is hit, which makes manual verification unreliable; the deep link is trivially verifiable. Another reason to prefer it.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
