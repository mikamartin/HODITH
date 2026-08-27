# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

Each item carries a trailer: **Branch** (the branch to open for it), **Complexity** (S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week — same scale as HODITH_SPEC §17), and **Priority** (High = gates the first release or corrects something wrong today · Medium = worth doing before alpha · Low = cosmetic or can wait). Then **Plan** (the main points), **Tests** (what coverage the change needs or breaks), and **Concern** where there is one.

## Recommended order

Sequencing matters more than usual here — several items collide in the same files, and two of them are cheap now and expensive after the first release.

1. Icon handle/ring clearance and the widget "+" investigation (both in Bugs) — isolated, no dependencies.
2. Ongoing event zeroes the current gap (Duration & ongoing events) — isolated domain fix.
3. Multiple ongoing events: widget + summary indicators — shares `OngoingIndicator.kt` / `LogDetailSheet.kt` with the next two, so land it first.
4. Ongoing-affordance redesign, then the duration unit selector — same two files, in that order.
5. Active-span per-case rendering, then Big Picture / frequency span-fill (still needs a design + spec pass), then the Rhythm caption if kept.
6. 12h/24h time format — its formatter consolidation collides with the Big Picture work, so give it a clear window.
7. Share card Square redesign (below) — it touches copy, so land it before Voice phrasing.
8. **Voice phrasing review last.** It is a mass edit across a 1819-line file; run it once, after every other copy-touching item on this list has landed, or it conflicts with all of them.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.

  *Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium*

  **Plan** — 294 `Voice` keys total, but only 213 are declared per-voice and need independent authorship (639 strings); the other 81 are shared `get()`/default-body keys (structural chrome — nav labels, field labels, and the like) reviewed once, not per voice. ~720 strings total. Not hard, but big, and it needs a human ear rather than a mechanical pass. Write the rubric first (what "consistent" means per voice: person, tense, sentence length, punctuation and emoji budget, and a locked vocabulary for Case/Hunch/Verdict/Event/Trigger), then audit in slices by screen rather than reading `Voice.kt` top to bottom — the file is grouped by key, so reading it linearly compares the wrong things. Produce a findings list first; fix in a second commit. The rubric should explicitly cover the ~105 em dashes currently in the copy (18 Serious, 36 Goth, 51 Quirky) — most convert cleanly to a period or comma, but Goth and Quirky use the em dash roughly 2–3x more often as a genuine mid-sentence pivot (a beat before a punchline or gothic aside), so each needs a per-string call rather than a mechanical substitution.

  **Tests** — `VoiceTest` today walks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. A copy audit is the right moment to add further mechanical invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab and button labels, no double spaces or trailing whitespace. Instrumented tests reference `PlainVoice.x` by constant rather than by literal, so copy edits shouldn't break them — confirm that holds everywhere before starting (a grep for hardcoded UI literals in `androidTest`).

  **Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

- [ ] Case icon picker's selected-state indicator is low-contrast, especially in Plain and Bright. Selection is shown only by a background-color swap, with no border, ring, or checkmark: Plain's `IconChoice` (`ui/case/CaseEditScreen.kt`) uses `primaryContainer` (#C7E8ED light) vs. `surfaceVariant` (#DEE4E7 light) — close in lightness — and Bright's `BrightIconChoice` uses `IconHalo`'s selected fill, a 16% tint wash of `primary` over `surface` (near-white on near-white).

  *Branch: `fix/case-icon-selection-contrast` · Complexity: S · Priority: Medium*

  **Plan** — needs a design decision on the affordance, not just a color retune, since a lightness-only swap will keep being fragile across themes (including future ones): add a distinguishing element that doesn't depend on background contrast alone — a checkmark overlay, a stroked ring, or a scale/elevation change. Once decided, apply it in both `IconChoice` (Plain/Intense branch, `CaseEditScreen.kt`) and `BrightIconChoice` — the two use different visual languages (flat fill vs. glow halo), so the same primitive won't drop into both identically.

  **Tests** — no existing test covers icon-selection visuals (Compose Previews only); add or update a Preview per theme showing selected vs. unselected side by side for manual verification. The selection state itself is already exposed structurally via `.selectable(selected = ...)` / `Role.RadioButton`, so there's nothing new to unit-test beyond the visual.

- [ ] Encode duration/ongoing events on the Big Picture grid and the frequency-over-time chart. Every visualization currently treats an event as a zero-width point at `occurredAt`: a multi-day or still-running event shows its icon on the start day only, and a long event counts once in the frequency chart. The product decision is to span-fill the grid with a *distinct* visual (a faded/connected icon, not the normal "it happened" icon) and trail a running event to today, so duration reads without masquerading as repeat occurrences.

  *Branch: `feat/big-picture-duration-spans` · Complexity: L · Priority: Medium*

  **Plan** — `CalendarEvent` (`viewmodel/BigPictureViewModel.kt`) gains `endedAt`; `BigPictureGrid` grouping expands an event across its span and renders spanned days in the faded/connected style. `DayDetailDialog` / `WeekDetailDialog` show "ongoing since …" / "lasted N days" instead of a clock time that misleads on a spanned day. Frequency-over-time (`domain/StatsEngine.kt` `computeFrequencyStats`) distributes an event across the buckets it covers. Depends on the active-span rule from `feat/active-span-insights` (Duration & ongoing events).

  **Tests** — `BigPictureViewModelTest`, `BigPictureGrid` UI tests, `StatsEngineTest`.

  **Concern** — reverses a decision documented in three places: HODITH_SPEC §9 line 174, the `CalendarEvent` doc comment, and `BigPictureGrid`'s KDoc ("intensity and duration are not encoded — a day cell shows icon-only, cross-case co-occurrence"). All three change together. **Open sub-decision:** does distributing an event across frequency buckets redefine that chart as "active time" rather than "starts", and do `computeTrendStats` and the verdict engine follow? Recommendation: no — keep trend and verdict on `occurredAt` starts, and note the difference in the Rhythm/Insights caveat.

## Duration & ongoing events

A round of user testing surfaced a cluster of Start/Stop and duration issues. Several of these share `ui/common/OngoingIndicator.kt` and `ui/logsheet/LogDetailSheet.kt` — see *Recommended order*.

- [ ] Ongoing event keeps the "current gap" growing. On the Insights tab, current gap (days since the last event) climbs even while an event is actively running — the app reads a Case as silent during something that is happening right now.

  *Branch: `fix/ongoing-current-gap` · Complexity: S · Priority: High*

  **Plan** — `computeGapStats` (`domain/InsightsEngine.kt`) takes an "event active now" flag; `currentGapDays = 0` while one is active, and that stretch is excluded from `longestGapDays`. Thread the flag through `insightsTabState` (`viewmodel/InsightsTabState.kt`).

  **Tests** — `InsightsEngineTest` — add active-event cases for current and longest gap.

  **Concern** — none. Isolated, visibly wrong today, no dependency on the rest of this section — do it first.

- [ ] Multiple running events on one Case: the widget and the summary indicators mishandle them. In-app you can already run several events at once and stop each from its row in the Case log, but the widget's single Stop button only ever acts on the first, and Home / the Case-detail log header silently show just the first. Related gaps: no way to move a stopped event back to running (wanted when something stops and restarts so fast it's the same occurrence), and switching a Case out of Start/Stop mode while events run strands them with no way to finish them.

  *Branch: `feat/multiple-ongoing-events` · Complexity: M · Priority: High*

  **Plan** — add `ongoingEventsIn` (list) alongside `ongoingEventIn` (`viewmodel/OngoingEvent.kt`); Home row and Case-detail header (`ui/casedetail/CaseDetailScreen.kt:309`) show "N running" past one, keeping the single-event elapsed display at one. Per-event Stop button on each `EventRow` in the Case log (reuse `CaseDetailViewModel.stopEvent`, which already takes a specific event). Widget: at >1 running, swap the Stop button for an "N running" pill that deep-links to Case Detail (`MainActivity` + `EXTRA_CASE_ID`, already wired); unchanged at exactly one. Reopen: a "back to ongoing" toggle in `LogDetailSheet` `EndTimeSection` setting `draft.endedAt = null` (`computeEndedAt` already persists null). Mode-change guard: `CaseEditViewModel` confirms "stop the running events first" when leaving `START_STOP`.

  **Tests** — `OngoingEventTest` (`ongoingEventsIn`), `CaseDetailScreenTest`, `HomeViewModel` mapping tests, `LogDetailViewModelTest` (reopen round-trip), both widget tests. `computeDurationStats` already ignores `endedAt == null` — add a multi-ongoing case, no code change.

  **Concern** — needs a small HODITH_SPEC §6 clarification: the summary indicators show a count; "one ongoing event per Case" describes the Start affordance, not a hard limit. Decide whether reopening a long-since-ended event rebases `staleNudgeDismissedAt` (else the stale banner fires on the next render). Separately, the "what happens when a Case toggles duration/intensity off" question is documentation only — `statsSections` already retains the underlying data and just hides the Insights card, restoring it on re-enable; confirm and state that in §10.

- [ ] Stop control is a checkmark; the running state is easy to miss. `StopIconButton` (`ui/common/OngoingIndicator.kt`) uses `Icons.Filled.Done` — a ✓ that reads as "confirm/complete" rather than "stop" — and the running marker is plain grey text with no colour cue. The event-edit sheet's End section also shows the past-tense header "Ended" directly above the value "Ongoing".

  *Branch: `fix/ongoing-affordance` · Complexity: S · Priority: High (glyph) / Medium (rest)*

  **Plan** — replace the checkmark with a stop-shaped glyph (draw a ~12dp rounded `Box`; don't pull in `material-icons-extended` for one icon). Add a coloured dot/pill to the elapsed-time marker. Present-tense header in `LogDetailSheet` `EndTimeSection` when `endedAt == null` — new Voice key ×3.

  **Tests** — existing UI tests find the Stop control by `contentDescription`, so the glyph swap is safe; keep any new dot out of the a11y tree or update `CaseDetailScreenTest` / `HomeScreenTest`. Add a Preview per voice.

  **Concern** — shares `OngoingIndicator.kt` with the multi-ongoing item and `LogDetailSheet.kt` with the unit selector; sequence after multi-ongoing.

- [ ] Manual duration entry is locked to minutes. The Manual-mode duration field (`ui/logsheet/LogDetailSheet.kt`) is a single "Minutes" number field, so a multi-day event means typing thousands of minutes.

  *Branch: `feat/duration-unit-selector` · Complexity: S · Priority: Medium*

  **Plan** — add a minutes/hours/days selector beside the field; storage stays millis. Integer units only (matches `filterDigitInput`); on edit-load pick the unit that renders the stored duration cleanly, else fall back to minutes. Touches `LogDetailSheet.kt`, `viewmodel/LogDetailViewModel.kt` (`LogDraft`, `draftFrom`, `computeEndedAt`), Voice ×3.

  **Tests** — `LogDetailViewModelTest` — round-trip per unit, non-clean-multiple fallback.

  **Concern** — none; no schema impact. Sequence after the affordance item to avoid three-way conflicts in `LogDetailSheet.kt`.

- [ ] Duration events are drawn as a single point at their start in the per-case views. The calendar heatmap shades only the start day; streaks credit only start days.

  *Branch: `feat/active-span-insights` · Complexity: M · Priority: Medium*

  **Plan** — define an "active span" rule once, written down: finished event → `occurredAt..endedAt`; ongoing → `occurredAt..now`; a span crossing midnight covers every calendar day it touches. Expand `activeDates` / `countsByDay` in `insightsTabState` to every covered day, feeding streak runs and calendar-heatmap day marking; keep the day iteration in `domain/CalendarGrid.kt` (shared with Big Picture). Heatmap colour shades by the count of duration events active on a day (product decision), not raw start count.

  **Tests** — `InsightsEngineTest`, `StatsEngineTest`, `InsightsTabStateTest`; ripples into `computeStreakShift` / gap-burst assertions and the share card (`ShareCardTemplateTest` floor/no-clip may shift).

  **Concern** — the active-span rule is a real design artifact — write it before coding. Depends on `fix/ongoing-current-gap`'s ongoing-flag plumbing.

- [ ] Rhythm grid plots duration events by start time with no caveat. An event that began late Monday and ran into Tuesday morning shows as one "late Monday night" mark, so the grid can read as "only happens at night".

  *Branch: `feat/rhythm-start-caption` · Complexity: S · Priority: Low*

  **Plan** — one caption line under `RhythmCard` (`ui/casedetail/InsightsTab.kt`), shown only when the Case tracks duration: "Plotted by when each occurrence started." One Voice key ×3. No behaviour change — Rhythm needs a single point per event, so start-only is correct here; it just needs saying.

  **Tests** — Preview only.

  **Concern** — optional; the value only really lands once the span-fill work makes Rhythm the odd chart out. Could fold into that design pass instead of tracking separately.

## Bugs

- [ ] Empty-state note is shifted to the left edge of the screen on Bright and Intense — confirmed visually. A source read found no cause: Big Picture, the case detail Log tab, and the Insights tab empty states (`BigPictureScreen.kt`, `CaseDetailScreen.kt`'s `LogTabContent`, `InsightsTab.kt`) all use the identical `Modifier.align(Alignment.Center)` / `contentAlignment = Alignment.Center` pattern, with no theme-conditional branching anywhere in that code.

  *Branch: `fix/empty-state-left-alignment` · Complexity: S to fix, M to diagnose · Priority: Medium*

  **Plan** — needs a repro-and-diagnose pass before a fix: capture screenshots on device/emulator for Bright and Intense across all three locations, and check what's outside the three composables already read — parent `Scaffold`/`Surface`/`Card` wrapping, `LocalLayoutDirection`, or a theme-specific decoration (`CardDecorationStyle`/`GlowDecoration.kt`) that might apply an offset the static read wouldn't show. Confirm whether it reproduces in all three locations or just Big Picture before assuming it's the shared pattern.

  **Tests** — no existing test asserts empty-state horizontal position; once the cause is found, a Compose UI test asserting the text node's bounds are centered (or at minimum not flush against the left edge) for Bright/Intense would catch a regression.

- [ ] Bright theme's light-mode `onSurfaceVariant` (`Color.kt`'s `brightLight`, #8A7A68) fails WCAG AA contrast (4.5:1) against both `surface` (~4.15:1) and `background` (~3.91:1). Found while writing `HodithThemeTest`'s new WCAG contrast test (scoped to Plain only for that reason — see its doc comment); not fixed here since it's a pre-existing gap unrelated to the Plain-theme branch that found it.

  *Branch: `fix/bright-light-onsurfacevariant-contrast` · Complexity: S · Priority: Medium*

  **Plan** — darken `onSurfaceVariant` (and check `secondary`/`onSecondaryContainer`, which look similarly light) until it clears 4.5:1 against both `surface` and `background`, keeping Bright's warm cast. Then widen `HodithThemeTest`'s new contrast test from Plain-only back to all 6 theme×mode combinations, closing the gap this item is tracking.

  **Tests** — `HodithThemeTest`'s contrast test already exists and is ready to widen once this lands; no new test scaffolding needed.

- [ ] Widget "+" renders red — confirmed visually on-device. A source read found no cause: the "+" glyph colour is unconditionally `WidgetPalette.accent` (= `PlainLightPrimary` `#3A6B76`, a teal) in `widget/ListWidget.kt` and `widget/SingleCaseWidget.kt`, with no time-since-last-log logic, no threshold, and no red in the widget palette's git history for the "+". The only red widget element is the Stop button (`PlainLightError #BA1A1A`), which replaces the "+" entirely and only shows for a Case with a running event.

  *Branch: `fix/widget-plus-colour` · Complexity: S to fix, M to diagnose · Priority: Medium*

  **Plan** — rebuild, remove and re-add the widget (Glance caches host views aggressively) and photograph the current "+". If still red: capture device / launcher / system theme (some launchers tint widget content), note List vs single-case widget, and rule out the red Stop pill being misread. Check whether an older build with different palette logic is still installed.

  **Tests** — nothing asserts widget glyph colour today; once the cause is known, a Robolectric/Glance check that the "+" resolves to `accent` regardless of last-event age would lock it.

  **Concern** — if the underlying ask is a "you haven't logged in a while" nudge rather than a rendering bug, that conflicts with spec §4 (no gamification) and §7 (HODITH doesn't nudge logging) — needs a spec ruling first, and the steer is not to add it.

- [ ] App-icon handle butts directly against the lens ring with no clearance. In `app/src/main/res/drawable/ic_launcher_foreground.xml` the handle's inner edge (midpoint ~(62,62)) sits on the ring's outer stroke band (~63.7 along the diagonal).

  *Branch: `fix/icon-handle-clearance` · Complexity: S · Priority: Low*

  **Plan** — push the handle's two inner points (`58.818,65.182` and `65.182,58.818`) outward along the (1,1) diagonal; mirror the change in `ic_launcher_monochrome.xml`. The handle tip is already near the 66dp adaptive-icon safe zone, so this may also mean shortening the handle or nudging the enclosing `group` scale (0.9).

  **Tests** — none (Previews only, as with the icon-picker item). Verify across densities, the Android 13+ themed/monochrome path, and the splash screen (which reuses the foreground).

  **Concern** — standalone, no dependencies.

## Share

- [ ] Square format should become a fixed preset — Story stays the one fully customizable, auto-sizing format. Root cause: `shareCardState()` (`ShareCardState.kt`) applies `selectedSections` the same way to both formats, and `SharePreviewScreen.kt`'s `SectionsPicker`/`availableSections` render identical toggles for both. That's a real problem now that Square keeps a 1:1 floor while Story sizes freely to content (see `fix/dialog-spacing-icon-sharecard-sizing`'s commit 3): selecting every Insights section on Square produces a tall rectangle, undermining the format's purpose — Square exists for chat/feed contexts that expect a predictable square shape.

  *Branch: `feat/square-share-card-preset` · Complexity: M · Priority: Medium*

  **Plan** — needs a product decision first: which sections (and in what fixed order) Square always shows. Once decided: show `SectionsPicker` only when `ShareCardFormat.STORY` is selected in `SharePreviewScreen.kt`, and have `shareCardState()` source Square's sections from the fixed preset, independent of `selectedSections`.

  **Tests** — `ShareCardStateTest.kt` needs coverage that Square's output is driven by the preset; `SharePreviewScreenTest.kt` needs coverage that the section picker appears only for Story. `ShareCardTemplateTest.kt`'s Square floor/no-clip coverage should keep passing as-is, since the preset's fixed content is what it already exercises.

  **Concern** — this is as much a product decision as an implementation task, and it touches Voice (Story-only picker copy), so land it before the Voice phrasing audit.

## Settings

- [ ] **Audit the hosted privacy policy and Play data-safety form.** Both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

  *Branch: none — external content, not a code change · Complexity: XS · Priority: Medium*

  **Plan** — read both against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and update wherever they still claim otherwise.

- [ ] Rate the App is still a placeholder row (shows a "coming soon" snackbar) — needs a real destination once there's a Play Store listing.

  *Branch: `feat/rate-app-play-link` · Complexity: S · Priority: Blocked — do it in release prep*

  **Plan** — genuinely blocked on the listing existing, so it belongs in the release-prep branch rather than as standalone work. Two implementations: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review means adding a Google Play Services dependency to an app that currently ships none and whose whole positioning is "no network", which makes it a positioning decision rather than a technical one.

  **Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar, so that test changes rather than gets added to: assert the intent is launched (Espresso `Intents`). Note the row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`, which needs no change but shouldn't be mistaken for a second call site.

  **Concern** — In-App Review is quota-limited and no-ops silently once the quota is hit, which makes manual verification unreliable; the deep link is trivially verifiable. Another reason to prefer it.

- [ ] No 12h/24h time-format control — all times render 12-hour US regardless of the device setting. Formatting is hardcoded `h:mm a` `Locale.US` in `viewmodel/CaseDetailViewModel.kt`'s top-level formatters, with duplicate `ofPattern` copies in `ui/bigpicture/BigPictureGrid.kt`, `ui/casedetail/InsightsTab.kt`, and `ui/share/ShareCardTemplate.kt`.

  *Branch: `feat/time-format-setting` · Complexity: M · Priority: Medium*

  **Plan** — DataStore key + Settings row (`data/DataStoreSettingsRepository.kt`, `ui/settings/SettingsScreen.kt`, `viewmodel/SettingsViewModel.kt`), defaulting to `DateFormat.is24HourFormat`. Consolidate the four formatter sites into one shared util, threaded via a CompositionLocal (mirror `LocalVoice`) for Compose and injected `SettingsRepository` for VM-side `formatEventTime`. Voice ×3 for the row label.

  **Tests** — parametrize `CaseDetailFormattingTest` by format (it currently asserts literal `h:mm a` output); check `HomeViewModelMappingTest` and `LogDetailViewModelTest` for time-string assertions.

  **Concern** — the formatter consolidation collides with `feat/big-picture-duration-spans` (`BigPictureGrid` / `InsightsTab`); give it a clear window. The consolidation is worthwhile cleanup regardless of the toggle.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
