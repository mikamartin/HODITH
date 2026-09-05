# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Standalone** — isolated items with no cross-dependencies; pick any when resources are thin.
- **Blocked** — gated on something external; not startable now.

The old **Story A** (Start/Stop, duration & ongoing events) is done bar one read-through, **A10**, which has no dependencies and now sits under Standalone.

Each item carries:

- a **trailer** — *Branch · Complexity · Priority · Area*. Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week (same scale as HODITH_SPEC §17). Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet. Area preserves the old grouping (Bug / Duration / Big Picture / Share / Settings / Voice).
- zero or more **tags** — 🎨 *Design decision* (needs a design or product-owner call before implementation) · 🌐 *External action* (work outside this repo) · 🔍 *Investigation* (needs a repro/diagnose pass before the fix is knowable).
- **Acceptance criteria** — the checklist that says "done".
- **Plan / Tests / Concern** — detail, unchanged from prior tracking.

## Story B — copy & Voice

Two items, plus the tail of nearly everything else. Anything that adds or changes a Voice key must land before B2.

### B1 · Square share format should become a fixed preset

*Branch: `feat/square-share-card-preset` · Complexity: M · Priority: Medium · Area: Share*

🎨 **Design decision** — which sections, and in what fixed order, Square always shows. Touches Voice copy, so before B2.

Story stays the one fully customizable, auto-sizing format. `shareCardState()` (`ShareCardState.kt`) applies `selectedSections` the same way to both formats, and `SharePreviewScreen.kt`'s `SectionsPicker` / `availableSections` render identical toggles for both. That's a real problem now that Square keeps a 1:1 floor while Story sizes freely to content (see `fix/dialog-spacing-icon-sharecard-sizing`'s commit 3): selecting every Insights section on Square produces a tall rectangle, undermining the format's purpose — Square exists for chat/feed contexts that expect a predictable square shape.

**Acceptance criteria**

- [ ] A documented fixed section list + order for Square.
- [ ] `SectionsPicker` renders only when `ShareCardFormat.STORY` is selected.
- [ ] `shareCardState()` sources Square's sections from the fixed preset, independent of `selectedSections`.
- [ ] Story keeps full customization and content-sizing.
- [ ] Any Story-only picker copy goes through Voice ×3.
- [ ] Tests: `ShareCardStateTest.kt` (Square driven by preset), `SharePreviewScreenTest.kt` (picker only for Story); `ShareCardTemplateTest.kt` Square floor/no-clip still passes.

**Plan** — needs a product decision first: which sections (and in what fixed order) Square always shows. Once decided: show `SectionsPicker` only when `ShareCardFormat.STORY` is selected in `SharePreviewScreen.kt`, and have `shareCardState()` source Square's sections from the fixed preset, independent of `selectedSections`.

**Tests** — `ShareCardStateTest.kt` needs coverage that Square's output is driven by the preset; `SharePreviewScreenTest.kt` needs coverage that the section picker appears only for Story. `ShareCardTemplateTest.kt`'s Square floor/no-clip coverage should keep passing as-is, since the preset's fixed content is what it already exercises.

**Concern** — this is as much a product decision as an implementation task, and it touches Voice (Story-only picker copy), so land it before B2.

### B2 · Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and the audit needs a human ear. **Must land last** — after every other copy-touching item. The only copy-touching item still open ahead of it is B1 (Story-only picker copy). The `feat/declutter-nudges` branch reworded the Serious `checkInDueNotificationBody` and renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle` (drafts in all three voices) — fold those into the audit.

**Acceptance criteria**

- [ ] A written rubric: per-voice person, tense, sentence length, punctuation/emoji budget, locked Case/Hunch/Verdict/Event/Trigger vocabulary, and an em-dash policy with per-string calls for Goth/Quirky mid-sentence pivots.
- [ ] A findings list produced first; fixes in a separate second commit.
- [ ] Audit done in slices by screen, not by reading `Voice.kt` linearly.
- [ ] New mechanical `VoiceTest` invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab/button labels, no double spaces or trailing whitespace.
- [ ] Confirmed before starting: `androidTest` references `PlainVoice` by constant, not literal, everywhere (grep for hardcoded UI literals).

**Plan** — 294 `Voice` keys total, but only 213 are declared per-voice and need independent authorship (639 strings); the other 81 are shared `get()`/default-body keys (structural chrome — nav labels, field labels, and the like) reviewed once, not per voice. ~720 strings total. Not hard, but big, and it needs a human ear rather than a mechanical pass. Write the rubric first (what "consistent" means per voice: person, tense, sentence length, punctuation and emoji budget, and a locked vocabulary for Case/Hunch/Verdict/Event/Trigger), then audit in slices by screen rather than reading `Voice.kt` top to bottom — the file is grouped by key, so reading it linearly compares the wrong things. Produce a findings list first; fix in a second commit. The rubric should explicitly cover the ~105 em dashes currently in the copy (18 Serious, 36 Goth, 51 Quirky) — most convert cleanly to a period or comma, but Goth and Quirky use the em dash roughly 2–3x more often as a genuine mid-sentence pivot (a beat before a punchline or gothic aside), so each needs a per-string call rather than a mechanical substitution.

**Tests** — `VoiceTest` today walks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. A copy audit is the right moment to add further mechanical invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab and button labels, no double spaces or trailing whitespace. Instrumented tests reference `PlainVoice.x` by constant rather than by literal, so copy edits shouldn't break them — confirm that holds everywhere before starting (a grep for hardcoded UI literals in `androidTest`).

**Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

## Standalone

No cross-dependencies. Pick any when resources are thin. Several soft batching opportunities:

- **On-device QA batch — S4 · S5 · S12, with S3** — S12 the widget red-`+` repro, S4 the Bright/Intense empty-state repro, and S4/S5/S3 are all Bright/Intense visual work. One emulator or device session covers them.
- **Case Detail Log-tab — S4** — touches `LogTabContent` / `CaseDetailScreen.kt` and `CaseDetailScreenTest.kt` (empty-state alignment). S7 (sort row) and S8 (event-edit screen) already landed here, so expect a small rebase.
- **Case Detail Insights tab — S4 · S10** — S10 adds tap targets to `InsightsTab.kt` and cases to `CaseDetailInsightsTabTest.kt`; S4 reworks the same file's empty state. Expect a small rebase between them.
- **Case-editor selection controls — S3 · S11** — S3 retunes `IconChoice` / `IntensityChoice` selection contrast; S11 declutters the adjacent `SegmentedChoiceRow`. Both are affordance polish on the same screens.
- **Fully isolated — S1** (icon vector + Previews), **S6** (external content), and **A10** (the old Story A read-through). Any order, any time.

### A10 · Verdict engine's handling of duration events is unreviewed

*Branch: `feat/verdict-duration-review` · Complexity: S · Priority: Low · Area: Big Picture*

🔍 **Investigation** — read-through first; a fix only if the read finds a real distortion. 🎨 **Design decision** — the ruling belongs in spec §8. No dependency on other outstanding work.

The verdict engine and its observation-window / observed-rate math count each event once at `occurredAt` and never look at `endedAt`. Whether a multi-day event should still count as one occurrence (likely yes), whether a still-running event counts before it stops, and whether the observation window's end should track a running event, have not been decided or tested.

**Acceptance criteria**

- [ ] Verdict engine, observation window, and observed-rate code read through for duration/ongoing handling.
- [ ] A written ruling added to HODITH_SPEC §8 (even if the ruling is "unchanged — starts only, one occurrence each").
- [ ] Code changed only if the read finds a genuine distortion; otherwise a test locking the current behaviour.

### S1 · App-icon handle butts directly against the lens ring with no clearance

*Branch: `fix/icon-handle-clearance` · Complexity: S · Priority: Low · Area: Bug*

In `app/src/main/res/drawable/ic_launcher_foreground.xml` the handle's inner edge (midpoint ~(62,62)) sits on the ring's outer stroke band (~63.7 along the diagonal).

**Acceptance criteria**

- [ ] The handle's two inner points (`58.818,65.182` and `65.182,58.818`) pushed outward along the (1,1) diagonal in `ic_launcher_foreground.xml`; mirrored in `ic_launcher_monochrome.xml`.
- [ ] Visible clearance between handle inner edge and ring outer stroke.
- [ ] Handle tip stays inside the 66dp adaptive-icon safe zone (shorten the handle or nudge the enclosing `group` scale if needed).
- [ ] Verified across densities, the Android 13+ themed/monochrome path, and the splash screen (which reuses the foreground).

**Plan** — push the handle's two inner points (`58.818,65.182` and `65.182,58.818`) outward along the (1,1) diagonal; mirror the change in `ic_launcher_monochrome.xml`. The handle tip is already near the 66dp adaptive-icon safe zone, so this may also mean shortening the handle or nudging the enclosing `group` scale (0.9).

**Tests** — none (Previews only, as with the icon-picker item). Verify across densities, the Android 13+ themed/monochrome path, and the splash screen.

**Concern** — standalone, no dependencies.

### S12 · Red "+" widget glyph has no cause in a source read

*Branch: TBD · Complexity: S · Priority: Low · Area: Bug*

🔍 **Investigation** — needs an on-device repro-and-photograph pass; nothing left to find by reading code.

Split off from the widget Plain-fidelity work (`fix/widget-plain-fidelity`; see CLEANUP_LOG.md). Testers report the "+" glyph rendering red; it is unconditionally `WidgetPalette.accent` (`PlainLightPrimary` `#3A6B76`, a teal) in `widget/ListWidget.kt` / `widget/SingleCaseWidget.kt`, in every state, with no red anywhere in its history — there's no in-widget Stop control at all (a running `START_STOP` Case's "+" just starts a second event; Stop only lives in Case Detail, per spec §6/§15).

**Acceptance criteria**

- [ ] Red "+" root cause identified on-device: rebuild, remove/re-add the widget, photograph; capture device / launcher / system theme; note List vs single-case; rule out the red Stop pill being misread; check for a stale older build.
- [ ] If a real cause turns up outside this repo's code, fix or document it; if it doesn't reproduce, close this out as unreproducible with the repro notes kept for next time.

**Plan** — on-device only; no code plan until a repro pins down where the red is coming from.

**Tests** — none until a cause is found.

**Concern** — if the underlying "+" ask is a "you haven't logged in a while" nudge, that hits spec §4 (no gamification) / §7 (HODITH doesn't nudge logging) — needs a spec ruling first; steer is not to add it.

### S4 · Empty-state note is shifted to the left edge on Bright and Intense

*Branch: `fix/empty-state-left-alignment` · Complexity: S to fix, M to diagnose · Priority: Medium · Area: Bug*

🔍 **Investigation** — confirmed visually, but a source read found no cause; needs a repro-and-diagnose pass first.

Big Picture, the case detail Log tab, and the Insights tab empty states (`BigPictureScreen.kt`, `CaseDetailScreen.kt`'s `LogTabContent`, `InsightsTab.kt`) all use the identical `Modifier.align(Alignment.Center)` / `contentAlignment = Alignment.Center` pattern, with no theme-conditional branching anywhere in that code.

**Acceptance criteria**

- [ ] Screenshots on Bright and Intense for all three locations; confirmed whether it repros in all three or just Big Picture.
- [ ] Cause found outside the three already-read composables (parent `Scaffold`/`Surface`/`Card`, `LocalLayoutDirection`, `CardDecorationStyle` / `GlowDecoration.kt`).
- [ ] Empty-state text centred on all themes.
- [ ] A Compose UI test asserting the text node's bounds are centred (or at least not flush left) for Bright/Intense.

**Plan** — needs a repro-and-diagnose pass before a fix: capture screenshots on device/emulator for Bright and Intense across all three locations, and check what's outside the three composables already read — parent `Scaffold`/`Surface`/`Card` wrapping, `LocalLayoutDirection`, or a theme-specific decoration (`CardDecorationStyle`/`GlowDecoration.kt`) that might apply an offset the static read wouldn't show. Confirm whether it reproduces in all three locations or just Big Picture before assuming it's the shared pattern.

**Tests** — no existing test asserts empty-state horizontal position; once the cause is found, a Compose UI test asserting the text node's bounds are centered (or at minimum not flush against the left edge) for Bright/Intense would catch a regression.

### S5 · Bright theme's light-mode `onSurfaceVariant` fails WCAG AA contrast

*Branch: `fix/bright-light-onsurfacevariant-contrast` · Complexity: S · Priority: Medium · Area: Bug*

`Color.kt`'s `brightLight` `onSurfaceVariant` (#8A7A68) fails WCAG AA contrast (4.5:1) against both `surface` (~4.15:1) and `background` (~3.91:1). Found while writing `HodithThemeTest`'s new WCAG contrast test (scoped to Plain only for that reason — see its doc comment); not fixed in the branch that found it, since it's a pre-existing gap unrelated to the Plain-theme work.

**Acceptance criteria**

- [ ] `onSurfaceVariant` darkened to clear 4.5:1 against both `surface` and `background`, keeping Bright's warm cast.
- [ ] `secondary` / `onSecondaryContainer` checked against the same bar and fixed if needed.
- [ ] `HodithThemeTest`'s contrast test widened from Plain-only back to all 6 theme×mode combinations.

**Plan** — darken `onSurfaceVariant` (and check `secondary`/`onSecondaryContainer`, which look similarly light) until it clears 4.5:1 against both `surface` and `background`, keeping Bright's warm cast. Then widen `HodithThemeTest`'s new contrast test from Plain-only back to all 6 theme×mode combinations, closing the gap this item is tracking.

**Tests** — `HodithThemeTest`'s contrast test already exists and is ready to widen once this lands; no new test scaffolding needed.

### S6 · Audit the hosted privacy policy and Play data-safety form

*Branch: none — external content, not a code change · Complexity: XS · Priority: Medium · Area: Settings*

🌐 **External action** — both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

**Acceptance criteria**

- [ ] Hosted policy read against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and updated wherever it still claims otherwise.
- [ ] Play data-safety answers reconciled with the same copy (once a listing exists).

**Plan** — read both against the new About copy and update wherever they still claim otherwise.

### S10 · Insights tab elements aren't tappable for drill-down

*Branch: `feat/insights-drilldown` · Complexity: M · Priority: Medium · Area: Insights*

🎨 **Design decision** — intensity and tag drill-down aren't in spec §10, and the per-case calendar heatmap's day-tap isn't in spec §9 either (only the Big Picture's is). Needs a ruling on what each drill-down row shows and a §9/§10 sentence that these elements are tappable.

On the Case Detail Insights tab (`ui/casedetail/InsightsTab.kt`), `HeatmapCell`, `IntensityCard`'s five shaded squares, and `TagsCard`'s per-tag `StatRow`s are pure display — no `Modifier.clickable`, no `onClick`. Tapping a specific intensity, a tag, or a heatmap day should open the logged events behind it. The pattern already exists in `ui/bigpicture/BigPictureGrid.kt`: `DayDetailDialog` / `WeekDetailDialog` render an `InfoDialog` listing events via `EventDetailRow`, each row `clickable` to open a Case. Here the tab is already scoped to one Case, so rows would instead call the Insights tab's existing `onEditEvent(caseId, eventId)` nav callback. All three filters run in memory over `CaseDetailUiState.events` (already loaded and in scope where `CaseDetailScreen` calls `InsightsTabContent`): `event.intensity == level`, `tags.any { it.name == tagName }`, and — for a heatmap day — the tapped `LocalDate` inside the event's active span via the existing `datesCovered` helper (`viewmodel/InsightsTabState.kt`). No new DAO query, no new nav route.

**Acceptance criteria**

- [ ] A ruling in HODITH_SPEC §9 (per-case heatmap day-tap) and §10 (intensity + tag drill-down) that these elements are tappable, plus what each result row shows (timestamp, note, tags, ongoing/duration line — mirroring `EventDetailRow`).
- [ ] `IntensityCard` square, `TagsCard` tag row, and `HeatmapCell` each become a tap target (`.clickable` / `Role.Button`) with a `contentDescription`; empty / zero-count cells stay inert.
- [ ] One shared drill-down surface — an `InfoDialog` listing the matching events, reusing or extracting a row composable equivalent to `EventDetailRow`; each row opens that event's editor via `onEditEvent`.
- [ ] Filtering stays in memory over `CaseDetailUiState.events`; no new nav route, no new `EventDao` query.
- [ ] Dialog title + empty-state strings go through Voice ×3.
- [ ] Renders under all three card decoration styles (Plain / Intense / Bright), like the Big Picture dialogs.
- [ ] Tests: `CaseDetailInsightsTabTest` — tapping an intensity square / tag row / heatmap day opens the dialog with the right events, a row tap fires `onEditEvent` with the correct ids, a zero-count element opens nothing; a Preview of the drill-down dialog per theme.

**Plan** — thread `events` + `onEditEvent` from `CaseDetailScreen`'s `INSIGHTS_TAB` branch into `InsightsTabContent` and down to `IntensityCard` / `TagsCard` / `CalendarHeatmapCard`. Add tap handlers that set a `selectedFilter` state, plus one `InsightsDrillDownDialog` modelled on `DayDetailDialog`. `EventDetailRow` is `private` to `BigPictureGrid.kt` — lift it to `ui/common/` or write a small local equivalent; decide during the cleanup pass.

**Tests** — `CaseDetailInsightsTabTest` for the three tap paths and the `onEditEvent` callback. No domain-layer test needed — `datesCovered` is already covered and no new pure logic is added.

**Concern** — mostly a product / spec call (what belongs in each row, whether the heatmap day-tap should also offer a week view like the Big Picture) plus Voice ×3; the wiring is small because the data is already in memory.

### S11 · Case-editor Duration segmented row is cramped at the right edge

*Branch: `fix/segmented-row-label-crowding` · Complexity: S · Priority: Low · Area: Bug*

🎨 **Design decision** — the fix lands in the shared `SegmentedChoiceRow`, and the tightest options trade against spec §3 principle 6 (the selected-state checkmark is a non-colour cue).

`CaseEditScreen.kt`'s Duration section (`SectionWithInfo` + `SegmentedChoiceRow`, options None / Manual / Start/stop) renders through `ui/common/SegmentedChoiceRow.kt`. The Plain/Intense branch is `SingleChoiceSegmentedButtonRow` with one `SegmentedButton` per option: equal-width segments, no `maxLines` / `softWrap` / auto-size, and M3's leading selected-checkmark slot takes ~24–28dp. The longest label, "Start/stop", sits in the rightmost segment, so on narrower screens or larger font scales it reads tight or clips. The Bright branch (`BrightSegmentedChoiceRow`) uses `horizontal = 0.dp` inner padding, so its text butts the capsule edge too. The control is shared (Case Edit logFlow + durationMode, Settings theme picker, Insights frequency granularity, Log-tab sort), so a fix here is consistency-positive. `caseDurationModeNone` / `Manual` / `StartStop` are interface `get()` defaults, identical across voices.

**Acceptance criteria**

- [ ] The three Duration labels render comfortably (no clip, sensible wrap) at a ~320dp width and the largest supported font scale, in Plain, Intense, and Bright.
- [ ] Fix applied in `SegmentedChoiceRow.kt` so every caller benefits; the Bright branch gains a small minimum horizontal inset.
- [ ] The affordance decision recorded — e.g. (a) keep the checkmark, drop label typography to `labelMedium` and tighten `SegmentedButton` content padding; (b) allow labels to wrap to two lines; (c) shorten a Voice label. If the checkmark is dropped to reclaim width, a replacement non-colour cue is added (spec §3 principle 6).
- [ ] `.selectable` / `Role.RadioButton` semantics unchanged.
- [ ] A Plain and an Intense Preview of the three-option row at a narrow width + large font scale (only a Bright Preview exists today).

**Plan** — reproduce in a Preview first (narrow width, bumped `fontScale`), pick the affordance, apply it once in `SegmentedChoiceRow.kt`, then eyeball the other call sites (the Settings theme picker is also three options) for regressions.

**Tests** — add a `SegmentedChoiceRow` Compose test (none exists) asserting all option labels are displayed for the three-option case in a constrained-width container; `CaseEditScreenTest` and `SettingsScreenTest` stay green.

**Concern** — cosmetic; nothing is functionally broken. Soft-batches with S3 (selection-state contrast in `IconChoice` / `IntensityChoice`), the other selection-control polish item on the same screens.

## Blocked

### BL1 · Rate the App is still a placeholder row

*Branch: `feat/rate-app-play-link` · Complexity: S · Priority: Blocked — do it in release prep · Area: Settings*

🌐 **External action** — genuinely gated on a Play Store listing existing. 🎨 **Design decision** — In-App Review would add Google Play Services to a zero-network app; that's a positioning call. Steer: deep link.

The row shows a "coming soon" snackbar — needs a real destination once there's a Play Store listing.

**Acceptance criteria**

- [ ] Implemented in the release-prep branch, not as standalone work.
- [ ] `market://details?id=…` intent with an `https://play.google.com/…` fallback (recommended over the In-App Review API).
- [ ] `SettingsScreenTest` changes from asserting the coming-soon snackbar to asserting the intent launches (Espresso `Intents`).
- [ ] The Bright plank Preview's no-op `onClick` left as-is (not a second call site).

**Plan** — genuinely blocked on the listing existing, so it belongs in the release-prep branch rather than as standalone work. Two implementations: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review means adding a Google Play Services dependency to an app that currently ships none and whose whole positioning is "no network", which makes it a positioning decision rather than a technical one.

**Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar, so that test changes rather than gets added to: assert the intent is launched (Espresso `Intents`). Note the row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`, which needs no change but shouldn't be mistaken for a second call site.

**Concern** — In-App Review is quota-limited and no-ops silently once the quota is hit, which makes manual verification unreliable; the deep link is trivially verifiable. Another reason to prefer it.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
