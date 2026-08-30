# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story A — Start/Stop, duration & ongoing events** — one dependency chain; work it top to bottom.
- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Standalone** — isolated items with no cross-dependencies; pick any when resources are thin.
- **Blocked** — gated on something external; not startable now.

Each item carries:

- a **trailer** — *Branch · Complexity · Priority · Area*. Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week (same scale as HODITH_SPEC §17). Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet. Area preserves the old grouping (Bug / Duration / Big Picture / Share / Settings / Voice).
- zero or more **tags** — 🎨 *Design decision* (needs a design or product-owner call before implementation) · 🌐 *External action* (work outside this repo) · 🔍 *Investigation* (needs a repro/diagnose pass before the fix is knowable).
- **Acceptance criteria** — the checklist that says "done".
- **Plan / Tests / Concern** — detail, unchanged from prior tracking.

## Story A — Start/Stop, duration & ongoing events

A round of user testing surfaced a cluster of Start/Stop and duration issues. They share `ui/common/OngoingIndicator.kt`, `ui/logsheet/LogDetailSheet.kt`, and `domain/CalendarGrid.kt` — so sequence matters. Work them in the order below.

Shared-file map (why the order is what it is):

- `LogDetailSheet.kt` — A4 (A3 done)
- `CalendarGrid.kt` day iteration — A5, A6
- formatter sites (`BigPictureGrid.kt`, `InsightsTab.kt`) — A6, time-format satellite

### A1 · Ongoing event keeps the "current gap" growing — done (`fix/ongoing-current-gap`, reopened as `fix/duration-gap-from-end`)

First pass handled the *running* case: `computeGapStats` takes an `eventActiveNow` flag (via `ongoingEventIn` in `insightsTabState`) so current gap reads 0 and the active stretch stays out of the longest gap while an event runs. Demo seed data carries ongoing events (Migraine, one; Noisy neighbours, two). That `eventActiveNow` plumbing is what A5 builds on.

Reopened because a *finished* duration event still misreported: once it stopped, `computeGapStats` fell back to `daysBetween(lastEvent.occurredAt, now)` — start-anchored — so a six-day event wrapped up today read as a six-day current gap. Now a gap is measured from when an event *ended*: current gap counts from `max(endedAt ?: occurredAt)`, past gaps run end-to-start (floored at 0 for overlaps), and point events (`endedAt == null`) are unchanged. `SILENT_FOR` triggers and check-ins shared the root cause — both anchored on `getMostRecentEventForCase(...).occurredAt` — and now count from the same `EventDao.getLatestEventEndForCase`, with a still-running event on a `START_STOP` Case pinning the silence clock to zero. Spec §10/§11 reworded to match.

### A2 · Multiple running events on one Case — done (`feat/multiple-ongoing-events`)

`ongoingEventsIn` (list, earliest-first) sits alongside `ongoingEventIn` (now the earliest open event, deterministically). Home rows and the Case-detail log header read "N running" past one event and drop the summary Stop; each running event carries its own Stop button on its log row (`showInlineStop`, reusing `CaseDetailViewModel.stopEvent`). Both widgets swap the red Stop button for an accent "N running" pill that opens Case Detail. `LogDetailSheet`'s End section gained a "Back to ongoing" button (`draft.endedAt = null`); `planSaveEvent` rebases `staleNudgeDismissedAt` on reopen so an old start doesn't fire the stale prompt immediately. `CaseEditViewModel` holds a leave-`START_STOP` mode change behind a confirm when events run and stops them all on save. Several concurrently-stale running events collapse to one consolidated header banner. Spec §6 reworded and §10 gained the duration/intensity-toggle-off clarification.

### A3 · Stop control is a checkmark; the running state is easy to miss — done (`fix/ongoing-affordance`)

One treatment for a running event on every surface. An **"Ongoing" pill** (`OngoingPill`, `primaryContainer` chip, Voice `ongoingPillLabel` ×3) marks it, followed by elapsed time (one event) or `ongoingCountIndicator` (several). `StopIconButton` swaps `Icons.Filled.Done` for a hand-drawn `drawRoundRect` square (`StopSquare`, mirroring `InfoIcon` — no `material-icons-extended`), `contentDescription` unchanged. The Case-detail log header always reads as a count (`OngoingCountText`, "1 running" included) with no Stop; every open event's row shows its own live elapsed (`OngoingElapsedText`) and its own Stop — `showInlineStop` gate dropped. Home rows and both widgets show the pill + summary and keep the `+` log button in every state (`HomeViewModel.onQuickLogTap` no longer stops; `WidgetCaseSubtitle` shared by both widgets; `WidgetPalette.accentContainer` added, `StopEventAction`/`widgetStopAction`/`widgetRunningCount`/`ongoingIndicator` removed). `LogDetailSheet` `EndTimeSection` drops the past-tense "Ended" header while `endedAt == null` (no new Voice key). Previews per voice added to `OngoingIndicator.kt`. Grew past the original S scope to unify Home + widgets; spec §6 and §15 reworded (the one-vs-many split, the header/summary Stop, and "the Start affordance becomes the Stop action" all reversed).

### A4 · Manual duration entry is locked to minutes

*Branch: `feat/duration-unit-selector` · Complexity: S · Priority: Medium · Area: Duration*

The Manual-mode duration field (`ui/logsheet/LogDetailSheet.kt`) is a single "Minutes" number field, so a multi-day event means typing thousands of minutes.

**Acceptance criteria**

- [ ] A minutes/hours/days selector sits beside the Manual-mode duration field.
- [ ] Storage stays millis; integer units only (matches `filterDigitInput`).
- [ ] On edit-load, the unit that renders the stored duration cleanly is chosen, else minutes.
- [ ] `LogDetailViewModelTest` covers round-trip per unit and the non-clean-multiple fallback.
- [ ] Voice key in all three voices for any new label.
- [ ] No schema change.

**Plan** — add a minutes/hours/days selector beside the field; storage stays millis. Integer units only (matches `filterDigitInput`); on edit-load pick the unit that renders the stored duration cleanly, else fall back to minutes. Touches `LogDetailSheet.kt`, `viewmodel/LogDetailViewModel.kt` (`LogDraft`, `draftFrom`, `computeEndedAt`), Voice ×3.

**Tests** — `LogDetailViewModelTest` — round-trip per unit, non-clean-multiple fallback.

**Concern** — none; no schema impact. Sequence after A3 to avoid three-way conflicts in `LogDetailSheet.kt`.

### A5 · Duration events are drawn as a single point at their start in per-case views

*Branch: `feat/active-span-insights` · Complexity: M · Priority: Medium · Area: Duration*

🎨 **Design decision** — the "active span" rule is a design artifact, write it before coding; heatmap shading by active-event count is a product decision. Builds on A1's `eventActiveNow` plumbing (done).

The calendar heatmap shades only the start day; streaks credit only start days.

**Acceptance criteria**

- [ ] A written "active span" rule: finished → `occurredAt..endedAt`; ongoing → `occurredAt..now`; a midnight-crossing span covers every calendar day it touches.
- [ ] `activeDates` / `countsByDay` in `insightsTabState` expand to every covered day.
- [ ] Streak runs and calendar-heatmap day marking consume the expanded days.
- [ ] Day iteration stays in `domain/CalendarGrid.kt` (shared with Big Picture).
- [ ] Heatmap colour shades by count of duration events active on a day, not raw start count.
- [ ] Tests: `InsightsEngineTest`, `StatsEngineTest`, `InsightsTabStateTest`; `computeStreakShift` / gap-burst assertions and `ShareCardTemplateTest` floor/no-clip updated if they shift.

**Plan** — define an "active span" rule once, written down: finished event → `occurredAt..endedAt`; ongoing → `occurredAt..now`; a span crossing midnight covers every calendar day it touches. Expand `activeDates` / `countsByDay` in `insightsTabState` to every covered day, feeding streak runs and calendar-heatmap day marking; keep the day iteration in `domain/CalendarGrid.kt` (shared with Big Picture). Heatmap colour shades by the count of duration events active on a day (product decision), not raw start count.

**Tests** — `InsightsEngineTest`, `StatsEngineTest`, `InsightsTabStateTest`; ripples into `computeStreakShift` / gap-burst assertions and the share card (`ShareCardTemplateTest` floor/no-clip may shift).

**Concern** — the active-span rule is a real design artifact — write it before coding. Builds on A1's `eventActiveNow` plumbing (done).

### A6 · Duration/ongoing not encoded on the Big Picture grid or the frequency chart

*Branch: `feat/big-picture-duration-spans` · Complexity: L · Priority: Medium · Area: Big Picture*

🎨 **Design decision** — needs a design + spec pass; open sub-decision on whether frequency buckets become "active time". Depends on A5.

Every visualization currently treats an event as a zero-width point at `occurredAt`: a multi-day or still-running event shows its icon on the start day only, and a long event counts once in the frequency chart. The product decision is to span-fill the grid with a *distinct* visual (a faded/connected icon, not the normal "it happened" icon) and trail a running event to today, so duration reads without masquerading as repeat occurrences.

**Acceptance criteria**

- [ ] `CalendarEvent` (`viewmodel/BigPictureViewModel.kt`) gains `endedAt`.
- [ ] `BigPictureGrid` renders spanned days in a distinct faded/connected style — not the normal "it happened" icon.
- [ ] A running event trails to today.
- [ ] `DayDetailDialog` / `WeekDetailDialog` show "ongoing since …" / "lasted N days" instead of a misleading clock time on a spanned day.
- [ ] `computeFrequencyStats` (`domain/StatsEngine.kt`) distributes an event across every bucket it covers.
- [ ] `computeTrendStats` and the verdict engine stay on `occurredAt` starts; the difference is noted in the Rhythm/Insights caveat.
- [ ] HODITH_SPEC §9 line 174, the `CalendarEvent` doc comment, and `BigPictureGrid`'s KDoc all updated together.
- [ ] Tests: `BigPictureViewModelTest`, `BigPictureGrid` UI tests, `StatsEngineTest`.

**Plan** — `CalendarEvent` (`viewmodel/BigPictureViewModel.kt`) gains `endedAt`; `BigPictureGrid` grouping expands an event across its span and renders spanned days in the faded/connected style. `DayDetailDialog` / `WeekDetailDialog` show "ongoing since …" / "lasted N days" instead of a clock time that misleads on a spanned day. Frequency-over-time (`domain/StatsEngine.kt` `computeFrequencyStats`) distributes an event across the buckets it covers. Depends on the active-span rule from A5.

**Tests** — `BigPictureViewModelTest`, `BigPictureGrid` UI tests, `StatsEngineTest`.

**Concern** — reverses a decision documented in three places: HODITH_SPEC §9 line 174, the `CalendarEvent` doc comment, and `BigPictureGrid`'s KDoc ("intensity and duration are not encoded — a day cell shows icon-only, cross-case co-occurrence"). All three change together. **Open sub-decision:** does distributing an event across frequency buckets redefine that chart as "active time" rather than "starts", and do `computeTrendStats` and the verdict engine follow? Recommendation: no — keep trend and verdict on `occurredAt` starts, and note the difference in the Rhythm/Insights caveat.

### A7 · Rhythm grid plots duration events by start time with no caveat

*Branch: `feat/rhythm-start-caption` · Complexity: S · Priority: Low · Area: Big Picture*

🎨 **Design decision** — optional; whether to ship it standalone or fold the caption into A6's design pass.

An event that began late Monday and ran into Tuesday morning shows as one "late Monday night" mark, so the grid can read as "only happens at night".

**Acceptance criteria**

- [ ] One caption line under `RhythmCard` (`ui/casedetail/InsightsTab.kt`), shown only when the Case tracks duration.
- [ ] Copy: "Plotted by when each occurrence started." — one Voice key in all three voices.
- [ ] No behaviour change.
- [ ] Preview coverage only.

**Plan** — one caption line under `RhythmCard` (`ui/casedetail/InsightsTab.kt`), shown only when the Case tracks duration: "Plotted by when each occurrence started." One Voice key ×3. No behaviour change — Rhythm needs a single point per event, so start-only is correct here; it just needs saying.

**Tests** — Preview only.

**Concern** — optional; the value only really lands once the span-fill work makes Rhythm the odd chart out. Could fold into that design pass instead of tracking separately.

### A8 · Home's today / this-week counts treat duration events as points at their start

*Branch: `feat/home-counts-duration-span` · Complexity: S · Priority: Medium · Area: Duration*

🎨 **Design decision** — whether a duration event counts toward Home's tallies on its start day, its end day, or every day it was active; the rule must match A5's active-span rule so Home and the calendar heatmap agree.

`homeCaseRows` (`viewmodel/HomeViewModel.kt`) counts `events.count { it.occurredAt >= startOfToday }` / `>= startOfWeek` — pure start day. An event started six days ago and wrapped up today shows "Today 0 / This week 0" on its Home row, the same day it was finished and logged.

**Acceptance criteria**

- [ ] A decided day-attribution rule for duration events on Home, written down alongside A5's active-span rule.
- [ ] `homeCaseRows` today / this-week counts apply it; a running event's span runs to now.
- [ ] `HomeViewModelMappingTest` covers a span crossing the today boundary and the week boundary.
- [ ] No schema change.

**Plan** — pick the rule (recommendation: an event counts on any day it was active, matching A5), then change the two `count {}` predicates in `homeCaseRows` to test span overlap rather than `occurredAt` alone.

**Tests** — `HomeViewModelMappingTest`.

**Concern** — not in Story A's shared-file chain (`HomeViewModel.kt` only), but the rule must match A5's or Home and the heatmap disagree. Sequence after A5's rule is written.

### Satellite · 12h/24h time format

*Branch: `feat/time-format-setting` · Complexity: M · Priority: Medium · Area: Settings*

Not part of Story A, but its formatter consolidation touches `BigPictureGrid.kt` / `InsightsTab.kt` — the same sites as A6. Land it in a clear window **before A6**, or well after. Also adds a Voice key (see Story B).

All times render 12-hour US regardless of the device setting. Formatting is hardcoded `h:mm a` `Locale.US` in `viewmodel/CaseDetailViewModel.kt`'s top-level formatters, with duplicate `ofPattern` copies in `ui/bigpicture/BigPictureGrid.kt`, `ui/casedetail/InsightsTab.kt`, and `ui/share/ShareCardTemplate.kt`.

**Acceptance criteria**

- [ ] DataStore key + Settings row, defaulting to `DateFormat.is24HourFormat`.
- [ ] The four `ofPattern` formatter sites consolidated into one shared util.
- [ ] Compose reads it via a CompositionLocal (mirroring `LocalVoice`); VM-side `formatEventTime` via injected `SettingsRepository`.
- [ ] All times respect the setting; default follows the device.
- [ ] Voice key in all three voices for the row label.
- [ ] Tests: `CaseDetailFormattingTest` parametrized by format; `HomeViewModelMappingTest` and `LogDetailViewModelTest` time-string assertions checked.

**Plan** — DataStore key + Settings row (`data/DataStoreSettingsRepository.kt`, `ui/settings/SettingsScreen.kt`, `viewmodel/SettingsViewModel.kt`), defaulting to `DateFormat.is24HourFormat`. Consolidate the four formatter sites into one shared util, threaded via a CompositionLocal (mirror `LocalVoice`) for Compose and injected `SettingsRepository` for VM-side `formatEventTime`. Voice ×3 for the row label.

**Tests** — parametrize `CaseDetailFormattingTest` by format (it currently asserts literal `h:mm a` output); check `HomeViewModelMappingTest` and `LogDetailViewModelTest` for time-string assertions.

**Concern** — the formatter consolidation collides with A6 (`BigPictureGrid` / `InsightsTab`); give it a clear window. The consolidation is worthwhile cleanup regardless of the toggle.

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

🎨 **Design decision** — the rubric is an authored artifact and the audit needs a human ear. **Must land last** — after A3, A4, A7, the time-format satellite, and B1 (every other copy-touching item).

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

No cross-dependencies. Pick any when resources are thin. Two soft batching opportunities, not dependencies: S3's affordance-language call overlaps A3 conceptually; S4 + S5 are both Bright-theme visual bugs and could share one on-device QA session.

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

### S2 · Widget "+" renders red

*Branch: `fix/widget-plus-colour` · Complexity: S to fix, M to diagnose · Priority: Medium · Area: Bug*

🔍 **Investigation** — a source read found no cause; needs an on-device repro pass. 🎨 **Design decision** — if the real ask is a "you haven't logged in a while" nudge, that hits spec §4/§7 and needs a ruling (steer: don't add it).

The "+" glyph colour is unconditionally `WidgetPalette.accent` (= `PlainLightPrimary` `#3A6B76`, a teal) in `widget/ListWidget.kt` and `widget/SingleCaseWidget.kt`, with no time-since-last-log logic, no threshold, and no red in the widget palette's git history for the "+". The only red widget element is the Stop button (`PlainLightError #BA1A1A`), which replaces the "+" entirely and only shows for a Case with a running event.

**Acceptance criteria**

- [ ] Root cause identified on-device: rebuild, remove/re-add the widget, photograph; capture device / launcher / system theme; note List vs single-case; rule out the red Stop pill being misread; check for a stale older build still installed.
- [ ] The "+" resolves to `WidgetPalette.accent` regardless of last-event age.
- [ ] A Robolectric/Glance check locking that, once the cause is known.

**Plan** — rebuild, remove and re-add the widget (Glance caches host views aggressively) and photograph the current "+". If still red: capture device / launcher / system theme (some launchers tint widget content), note List vs single-case widget, and rule out the red Stop pill being misread. Check whether an older build with different palette logic is still installed.

**Tests** — nothing asserts widget glyph colour today; once the cause is known, a Robolectric/Glance check that the "+" resolves to `accent` regardless of last-event age would lock it.

**Concern** — if the underlying ask is a "you haven't logged in a while" nudge rather than a rendering bug, that conflicts with spec §4 (no gamification) and §7 (HODITH doesn't nudge logging) — needs a spec ruling first, and the steer is not to add it.

### S3 · Case icon picker's selected-state indicator is low-contrast

*Branch: `fix/case-icon-selection-contrast` · Complexity: S · Priority: Medium · Area: Bug*

🎨 **Design decision** — needs an affordance decision (checkmark overlay / stroked ring / scale-elevation change), not just a colour retune.

Selection is shown only by a background-color swap, with no border, ring, or checkmark: Plain's `IconChoice` (`ui/case/CaseEditScreen.kt`) uses `primaryContainer` (#C7E8ED light) vs. `surfaceVariant` (#DEE4E7 light) — close in lightness — and Bright's `BrightIconChoice` uses `IconHalo`'s selected fill, a 16% tint wash of `primary` over `surface` (near-white on near-white).

**Acceptance criteria**

- [ ] Selection shown by an element that doesn't depend on background contrast alone.
- [ ] Applied in both `IconChoice` (Plain/Intense, `CaseEditScreen.kt`) and `BrightIconChoice` — adapted to each visual language (flat fill vs. glow halo).
- [ ] A Preview per theme showing selected vs. unselected side by side.
- [ ] `.selectable(selected = …)` / `Role.RadioButton` structure unchanged.

**Plan** — needs a design decision on the affordance, not just a color retune, since a lightness-only swap will keep being fragile across themes (including future ones): add a distinguishing element that doesn't depend on background contrast alone — a checkmark overlay, a stroked ring, or a scale/elevation change. Once decided, apply it in both `IconChoice` (Plain/Intense branch, `CaseEditScreen.kt`) and `BrightIconChoice` — the two use different visual languages (flat fill vs. glow halo), so the same primitive won't drop into both identically.

**Tests** — no existing test covers icon-selection visuals (Compose Previews only); add or update a Preview per theme showing selected vs. unselected side by side for manual verification. The selection state itself is already exposed structurally via `.selectable(selected = ...)` / `Role.RadioButton`, so there's nothing new to unit-test beyond the visual.

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

### S7 · Log tab can't sort by when an event ended

*Branch: `feat/log-sort-by-end` · Complexity: S · Priority: Low · Area: Duration*

The case detail Log tab is hardcoded `ORDER BY occurredAt DESC` (`data/EventDao.kt`) — sorted by start. For a Case that tracks duration there's no way to view events by when they *ended*: ongoing events first, then most-recently-ended.

**Acceptance criteria**

- [ ] A start / end sort toggle on the Log tab, shown only when the Case tracks duration.
- [ ] "By end" orders ongoing events first (no `endedAt`), then by `endedAt` descending.
- [ ] Sort choice is UI state only — no persistence, no schema change.
- [ ] Any new control label goes through Voice ×3.
- [ ] Tests: the ordering logic covered where the Log list is assembled (`CaseDetailViewModel` mapping or a pure sort helper).

**Plan** — add an end-ordered `EventDao` query (or sort in the VM) and a toggle in `CaseDetailScreen.kt`'s Log tab gated on `durationMode != NONE`. Ongoing events sort to the top.

**Tests** — a pure sort-comparator test; `CaseDetailViewModel` mapping if the sort lands there.

**Concern** — standalone; overlaps A3/A4 only as more duration polish, no file conflict.

### S8 · Editing an event is a bottom sheet with no close affordance; editing a Case is a full screen with a back arrow

*Branch: `fix/log-sheet-dismiss-affordance` · Complexity: S · Priority: Low · Area: Bug*

🎨 **Design decision** — promote event-edit to a full screen to match `CaseEditScreen`, or keep the sheet and give it an explicit close/Cancel control.

`LogDetailSheet` (`ui/logsheet/LogDetailSheet.kt`) is a `ModalBottomSheet` used for both quick-logging a new event and editing an existing one; it dismisses only by swipe-down, scrim tap, or system back. `SheetHeader` shows the title and (when editing) a delete icon — no back arrow, no Cancel button. `CaseEditScreen.kt` is a full destination with a `TopAppBar` back arrow, so the two edit flows don't match. The sheet is reachable from Home, Case Detail's Log tab, and the widget trampoline (`WidgetLogTrampolineActivity`).

**Acceptance criteria**

- [ ] Either: event-edit becomes a screen with a `TopAppBar` matching `CaseEditScreen` (new-event quick-log may stay a sheet); or the sheet gains a visible close/Cancel affordance in `SheetHeader`.
- [ ] Whichever way, the three entry points (Home, Log tab, widget trampoline) still reach it and still save/dismiss correctly.
- [ ] Any new control label goes through Voice ×3.
- [ ] Tests: the affected `CaseDetailScreenTest` / `HomeScreenTest` / `WidgetLogTrampolineActivityTest` flows updated for the new affordance.

**Plan** — decide the direction first (a sheet is fine for a 5-second new-event log; an edit with time/end-time/intensity/duration/note/tags/delete is closer to `CaseEditScreen`'s weight). Sheet-with-Cancel is the smaller change; screen-for-edit is the more consistent one.

**Tests** — `CaseDetailScreenTest`, `HomeScreenTest`, `WidgetLogTrampolineActivityTest`.

**Concern** — standalone. Shares `LogDetailSheet.kt` with A3/A4 — sequence after them, or fold the affordance into whichever lands last.

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
