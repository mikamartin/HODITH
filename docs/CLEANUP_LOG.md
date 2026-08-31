# HODITH — Cleanup Log

A record of every cleanup pass, newest first (ordering, not dating, marks recency — see CLAUDE.md's no-dates rule). After any significant feature work, actually walk through every applicable item in [CLEANUP_CHECKLIST.md](CLEANUP_CHECKLIST.md) against the real diff first — an entry here records what that walk-through found, it isn't a template to fill in from memory of what changed. Then add a new entry above the previous one: what was found and fixed, what was deferred with a reason, and which sections didn't apply and why.

## Entry format

```
## <branch or feature name>

**Scope:** what work triggered this pass
**Found & fixed:** bullet list (or "nothing found" — that's a valid result)
**Deferred:** bullet list with reasons (or "nothing deferred")
**Docs updated:** SPEC / TESTING / PLAYBOOK sections touched, if any
```

---

## feat/duration-display-follows-mode

**Scope:** PROGRESS.md Story A item A7. Three surfaces decided whether to show a duration by reading a raw non-null `event.endedAt`, ignoring the Case's current `durationMode` — so a Case switched to `NONE` kept showing "Lasted X" on every event row, multi-day icon spans on the Big Picture grid, and spread heatmap shading / inflated streaks / a hidden frequency card on Insights; a zero-length event (`endedAt == occurredAt`) read "Lasted 0m". Now every duration-display surface follows `durationMode`: spec §9's active-span rule is gated on the Case still tracking duration, and each surface collapses a non-tracking Case's stored `endedAt` to a point at its own mapping boundary so the existing span/date/label logic needs no new branches. Stored `endedAt` is never mutated — switching the mode back on restores every span. No schema change.

**Found & fixed:**
- No shared "does this Case track duration" predicate existed — `!= DurationMode.NONE` was inlined at `InsightsTabState`'s Event-duration-card gate and `SharePreviewScreen.availableSections`, and the three broken surfaces checked nothing. Added one `internal val DurationMode.tracksDuration` beside the enum in `data/DurationMode.kt` (on the enum, not `CaseEntity` — the Case-detail composable caller has only a `DurationMode` in scope) and routed all four sites through it.
- Chose to collapse `endedAt` to a point at each surface's entry boundary rather than thread a `tracksDuration` flag into `CalendarEvent` → `coveredDates` → the grid renderers (the shape PROGRESS.md's AC tentatively sketched). `bigPictureUiState` nulls `CalendarEvent.endedAt` for a non-tracking Case (one expression; `isOngoing` already `START_STOP`-gated, so unaffected); `insightsTabState` collapses the event list once where it's derived, so `spanEnd` / `hasMultiDayEvent` / `computeGapStats` all follow with no change; `eventDetailSummary` gains a caller-asserted `tracksDuration: Boolean = true` mirroring its existing `isOngoing` flag. The "resolve a span end" step is thus not duplicated further — each site is a different data shape (null a field / map a list / gate a bool) and all bottom out in the unchanged `datesCovered` / `spansMultipleDays` primitives; the rule is named in a comment at each.
- Zero-length guard (`endedAt > occurredAt`) added only to `eventDetailSummary` — `coveredDates` already collapses `endDate == startDate` and `computeDurationStats` already filters `> 0`, so that surface is the only one that showed "Lasted 0m".
- `DurationMode` import dropped from `SharePreviewScreen.kt` (now unused after the `tracksDuration` swap); still imported where `DurationMode.START_STOP` is referenced (`InsightsTabState`, `BigPictureViewModel`).

**Deferred:**
- `InsightsTabState` collapses the list with `events.map { it.copy(endedAt = null) }` for a `NONE` Case — a small allocation per Insights read, `NONE` only. Left as is: Insights already recomputes from scratch and already does `eventsWithTags.map { it.event }`, and the alternative (a mode-aware `spanEnd` plus a mode-aware `hasMultiDayEvent` plus a mode-aware `computeGapStats` call) is three branches instead of one line.
- `eventDetailSummary`'s `tracksDuration` defaults to `true` so ~13 unrelated `CaseDetailFormattingTest` call sites stay untouched; the sole production caller passes it explicitly. Same trade-off the existing `isOngoing = false` default already makes.
- A8 / A9 still open (they sequence after this). A9's Home span predicate must call `DurationMode.tracksDuration`, not re-inline the check — flagged in its PROGRESS.md acceptance criteria.

**Sections walked, nothing to do:** *Duplication* — no new Voice keys (the `eventDurationLabel` string is reused); the new predicate removes two inline `!= NONE` checks rather than adding one. *Decoupling* — `DurationMode.tracksDuration` is pure Kotlin in `data/` referencing only its own enum; no `System.currentTimeMillis()` (Insights `now` is a parameter, Big Picture uses the injected `Clock`); no UI type crosses into a viewmodel. *Complexity* — no composable grew past ~150 lines (the one call site became a multi-line call); the `.let { list -> if (...) list else list.map { ... } }` is one level deep; no `LaunchedEffect` / `remember` / coroutine scope touched. *Dead code* — unused `DurationMode` import removed from `SharePreviewScreen`; no commented-out blocks, no resolved TODOs, no self-updating tallies added to living docs. *Repo hygiene* — `git status` shows only the expected files (5 main, 5 unit-test, 2 instrumented-test, 3 docs), nothing secret-shaped, no local paths. *Naming* — `tracksDuration` is lowerCamelCase like `quickLogEvent`; no new files or composables. *Hardcoded values* — `endedAt > occurredAt` is a zero-length test, not a magic number; `DurationMode.NONE` is a named enum member. *Accessibility* — a `NONE` Case now paints fewer shaded heatmap cells (correct); no new colour-only information, no icon buttons, no tap-target change. *Deprecated APIs* — none new.

**Docs updated:** `HODITH_SPEC.md` §6 (event-row "lasted" line gated on the Case still tracking duration; zero-length event is a point), §9 (active-span rule scoped to `durationMode ≠ NONE` with the point-event bullet widened; Big Picture paragraph — a `NONE` Case shows no spans), §10 (Gaps & streaks current-gap anchor; the duration/intensity-card gating sentence generalised to the event-row line and Big Picture spans). `DurationMode` and `CalendarEvent` KDocs, plus the `insightsTabState` / `eventDetailSummary` / `bigPictureUiState` inline comments. `PROGRESS.md` (A7 → done one-liner in the renamed A1–A7 block; full section removed; chain intro and A9's predicate-reuse criterion updated). `TESTING.md` (Stats & visual data prep, ViewModels, and Case Detail Insights UI rows). No `DEV_PLAYBOOK.md` change.

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug` → `assembleDebugAndroidTest`, sequential, all green. Unit: `InsightsTabStateTest` (27, +3 — a `NONE` Case with a stored multi-day `endedAt` renders as a point in the heatmap/streak, keeps the frequency card, and reads the current gap from `occurredAt`; six existing span-expecting cases switched from the default `NONE` `testCase` to `MANUAL` so they still exercise spans), `BigPictureViewModelTest` (7, +2 — a `NONE` Case drops a stored `endedAt` to a point, a `MANUAL` Case keeps it), `CaseDetailFormattingTest` (25, +3 — the line is hidden for a non-tracking Case and for a zero-length event, shown for a real span on a tracking Case). Instrumented, run on `Pixel_8_API36` (`connectedDebugAndroidTest` scoped to the two classes — 39 tests, 0 failed): `CaseDetailInsightsTabTest` (+1 — the frequency card stays and rhythm keeps its plain title for a `NONE` Case with a stored multi-day `endedAt`, mirroring the existing `START_STOP` hide test), `CaseDetailScreenTest` (+2 — the event row shows the duration label for a `MANUAL` Case and hides it for a `NONE` Case with the same stored `endedAt`, covering the `CaseDetailScreen` → `eventDetailSummary` wiring). `BigPictureScreenTest` unchanged — it builds `CalendarEvent` directly, below the ViewModel boundary where the gate lives.

---

## feat/big-picture-duration-spans

**Scope:** PROGRESS.md Story A item A6. Every visualization still treated an event as a zero-width point at `occurredAt`, so a multi-day or still-running event showed its icon on the start day only. Now the Big Picture grid encodes duration: `CalendarEvent` (`viewmodel/BigPictureViewModel.kt`) gains `endedAt`/`isOngoing`, and an event whose active span (spec §9) crosses a calendar-day boundary shows its icon on every day it covered, with a `primary` ring on the start day and a trail to today for a still-running one. A same-day duration event is unchanged. The day/week detail dialogs read "ongoing since …" / "lasted …" in place of a clock time that would mislead on a carried day. Product-owner scope calls departed from the original A6 note in two ways: (1) `computeFrequencyStats` is **not** changed to distribute an event across buckets — that recreates the "duration looks like repeat occurrences" problem and contradicts what A5 wrote into spec §9; instead the frequency-over-time card is **hidden** for a Case with any multi-day event (`domain/CalendarGrid.kt` `spansMultipleDays` helper, `StatsSections.frequency` now nullable). (2) The old A7 (rhythm plots starts with no caveat) is **absorbed**: the Rhythm card retitles to "Start times" under the same condition, so there's nothing left to caption. `computeFrequencyStats`/`computeTrendStats`/the verdict engine are untouched and stay `occurredAt`-anchored.

**Found & fixed:**
- The "resolve an event's span end" step now exists twice — `coveredDates` in `BigPictureGrid.kt` (works in `LocalDate` off the precomputed `CalendarEvent.isOngoing` + the grid's `today`) and `spanEnd` in `insightsTabState` (works in millis off `case.durationMode` + `now`). Considered a shared helper, but the two operate on different data shapes (one has `isOngoing` already reduced, the other has the `CaseEntity`) and both bottom out in the shared primitives `datesCovered` / `spansMultipleDays`. Left parallel, each with the active-span rule named in a comment.
- `SharePreviewScreen.availableSections` gained a `frequencyAvailable` parameter rather than re-deriving "has a multi-day event" — the screen already computes `insightsTabState`, so the FREQUENCY toggle is dropped from the picker exactly when the tab itself drops the card (`stats.frequency == null`). `ShareCardData.frequency` was already nullable, so `shareCardState` needed no change.
- `MiniRhythmSection` (`ShareCardTemplate.kt`) picks up the same `plottedByStart` title swap as the real card, so a shared card and the tab agree.

**Deferred:**
- The span-start ring is drawn per cell style (`Modifier.border` on `CircleShape` for Plain/Bright, on the square icon box for Intense) rather than through one shared helper — the three cells already diverge on icon shape and base-border logic (Plain has none, Intense replaces `outlineVariant`, Bright's depends on `isToday`), and a helper taking shape + base-border would not be shorter. Matches the file's existing "the three variants are the only place that branch on `BigPictureCellStyle`" stance.
- In the week detail dialog a multi-day event's row repeats ("lasted Feb 3 – Feb 6") once per covered day in that week. Accepted — each line is on the correct day and the repetition reads as "still the same event", which is the point.
- The prototype spike (`ui/bigpicture/SpanCellSpike.kt`, five throwaway previews used to pick the treatment) was deleted once variant A + the multi-day-only scope were chosen; `BigPictureGrid.kt`'s own previews gained a 4-day finished event and a running one instead.

**Sections walked, nothing to do:** *Duplication* — the three new Voice keys (`bigPictureEventOngoingSince`, `bigPictureEventSpanRange` abstract; `insightsSectionLabelRhythmStarts` a shared `get()` default) are authored in all three voices in this commit; `SPAN_START_RING_WIDTH` / `SPAN_DATE_FORMATTER` are named, not inlined. *Decoupling* — `spansMultipleDays` is pure (`java.time` only, injected `ZoneId`); `coveredDates` takes `today` as a parameter, no `System.currentTimeMillis()`; the grid's day-bucketing stays in the composable exactly where `events.groupBy { … }` already lived. *Complexity* — no composable crossed ~150 lines; `EventDetailRow` gained one `when` block; `remember(events, zoneId, today)` correctly adds `today` as a key since a running event's span ends there. *Hardcoded values* — `1.5.dp` ring is a UI stroke (stays in the UI file, named); no `Color(0xFF…)`, the ring uses `colorScheme.primary`. *Accessibility* — the ring is a shape cue, not colour-only, and its meaning ("lasted …"/"ongoing since …") is spoken in the day/week dialog, matching how cross-case co-occurrence is also grid-visual-only and explained on tap; icon tap targets and day-number text unchanged. *Naming* — `DayEvent`/`DayCellIcon` are PascalCase local data classes; `spansMultipleDays` sits beside `datesCovered` in `domain/`. *Deprecated APIs* — none new. No schema change, no `MANUAL_TEST_PLAN.md` change (nothing crosses a process boundary; TESTING.md's timezone/DST manual seed item already names Big Picture).

**Docs updated:** `HODITH_SPEC.md` §9 (active-span paragraph — grid now spans, frequency hidden / rhythm retitled for multi-day Cases; the "intensity and duration are not encoded" bullet rewritten), §10 (Frequency and Rhythm bullets). `CalendarEvent` and `BigPictureGrid` KDocs. `PROGRESS.md` (A6 → done bullet in the A1–A6 block; A7 removed as absorbed; shared-file map trimmed; time-format satellite and B2 notes rebased off A6). `TESTING.md` (Stats & visual data prep, ViewModels, Big Picture UI, Case Detail Insights UI rows). No `DEV_PLAYBOOK.md` change.

**Verified:** `ktlintCheck` → `lintDebug` → `testDebugUnitTest` → `assembleDebug`, sequential, all green. Unit: `CalendarGridTest` (+4 `spansMultipleDays` — within a day, midnight-crossing, reversed-floored, zone-resolved), `BigPictureViewModelTest` (+1 — `endedAt` carried through and only a `START_STOP` open event flagged `isOngoing`), `InsightsTabStateTest` (the old "frequency stays start-anchored while the heatmap spans" test rewritten to assert the card is now `null` and `rhythm.plottedByStart`; +2 — frequency present and start-anchored when every event fits in a day, and a running event that began before today makes the Case multi-day). `connectedDebugAndroidTest` on `Pixel_8_API36` (`installDebug` + `appwidget grantbind --user 0` first, per DEV_PLAYBOOK §5): **223/223, 0 failed** — including the new `BigPictureScreenTest` (+2 — a carried day shows the span range not a clock time; a carried day of a running event shows "ongoing since") and `CaseDetailInsightsTabTest` (+1 — frequency card gone and rhythm retitled once an event spans days); `ShareCardTemplateTest`'s `RhythmDisplay` fixture updated.

---

## feat/active-span-insights

**Scope:** PROGRESS.md Story A item A5. The per-case calendar heatmap and the gaps & streaks card treated every event as a zero-width point at `occurredAt`, so a multi-day or still-running event shaded one heatmap cell and credited one active day. Now they follow a written "active span" rule (HODITH_SPEC §9): an event covers every calendar day from its start to its end inclusive — `occurredAt..endedAt` when finished, `occurredAt..now` while a `START_STOP` event runs, a single day for a point event, every day touched for a span crossing midnight. New `domain/CalendarGrid.kt` `datesCovered(startMillis, endMillis, zone)` helper does the day iteration (A6 will reuse it); `insightsTabState`'s `countsByDay` is built by flat-mapping every event across its covered days, and both the heatmap shading and `computeStreakStats`/`computeStreakShift` consume that expanded map with no signature change. Frequency-over-time, Rhythm, Trend, the verdict engine and "observed for N days" stay anchored to `occurredAt` starts. Folded in from the same review: a tappable info icon on the gaps & streaks card with a one-line definition per metric (the streak can now outrun the event count, which needed explaining), and the Insights "Duration" card label renamed to "Event duration".

**Found & fixed:**
- `GapsCard` set its section label as a bare `Text` while `FrequencyCard` already used the shared `SectionWithInfo` (label + info icon + dialog). Moved Gaps onto that same component rather than hand-rolling an icon and `showInfo` state. `SectionWithInfo`'s content slot is an unspaced `Column`, so the five `StatRow`s were wrapped in a `Column(verticalArrangement = Arrangement.spacedBy(8.dp))` to keep the row rhythm `InsightsCard` gave them.
- The active-span end-date expression (`endedAt ?: if (START_STOP) now else occurredAt`) restates `ongoingEventsIn`'s "only a `START_STOP` case can be ongoing" rule. Considered calling `ongoingEventsIn` instead, but it returns a list to test membership against and the unit fixtures all share `id = 0`; inlined with a comment pointing at the shared rule.
- `datesCovered` is single-caller today (`insightsTabState`) but lives in `domain/CalendarGrid.kt` per A5's own acceptance criterion — A6 (`feat/big-picture-duration-spans`) is the imminent second caller; its KDoc says so.

**Deferred:**
- Heatmap "flood": a lone long running event shades its whole span near the top tier, since shading is relative to the case's busiest day. Confirmed acceptable with the product owner (it is truthful) — a distinct faded/carried-day treatment is A6's scope, not retrofitted here.
- The verdict engine's handling of duration / still-running events is untouched and has never been reviewed. Captured as new PROGRESS.md item **A9** (`feat/verdict-duration-review`) rather than widened into this pass.
- No instrumented test asserts the heatmap paints the extra cells on screen; the shading/streak math is covered in `InsightsTabStateTest` on the JVM, and the instrumented `CaseDetailInsightsTabTest` gained only the info-icon open/dismiss test, matching that class's existing presence-only heatmap coverage.

**Sections walked, nothing to do:** *Duplication* — the six new `insightsGapsInfo{Title,Body}` strings are authored per-voice in this commit; the icon's `contentDescription` (`caseSectionInfoDescription`) and `infoDialogDismissAction` are reused; `insightsSectionLabelDuration` is a shared `get()` default, so "Event duration" is a one-line change, not ×3. *Decoupling* — `datesCovered` is pure (`java.time` only, injected `ZoneId`, no `android.*`); `insightsTabState` gains no `System.currentTimeMillis()` (`now` is a parameter); no UI types cross into the viewmodel. *Complexity* — `GapsCard` gains one nesting level, matching `FrequencyCard`; `datesCovered` is a 3-line generator; no new `LaunchedEffect`/`remember`/coroutine scope. *Hardcoded values* — `Arrangement.spacedBy(8.dp)` mirrors `InsightsCard`'s own spacing; the active-span rule reads off `DurationMode`/`endedAt`, both named, no domain magic numbers. *Accessibility* — the info icon carries a `contentDescription`, its `IconButton` keeps the 48 dp target, heatmap cells still render the day-of-month text. *Naming* — `insightsGapsInfo*` mirrors `insightsFrequencyInfo*`; `datesCovered` is a `domain/` function. *Deprecated APIs* — none new (pre-existing Moshi/kapt warning only). No schema change, no new colours or resources, no `MANUAL_TEST_PLAN.md` change (everything in-process).

**Docs updated:** `HODITH_SPEC.md` §9 (new "Active span" subsection; the per-case heatmap's shading sentence), §10 (streak definition; "Duration stats" bullet → "Event duration"). `PROGRESS.md` (A5 folded into the "done" A1–A5 block; shared-file map's `CalendarGrid.kt` line; new item A9). `TESTING.md` (Stats & visual data prep row — `datesCovered` and the span-driven heatmap/streak cases; Case Detail Insights row — the gaps & streaks info icon). No `DEV_PLAYBOOK.md` change.

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, sequential, all green (498 unit tests). Unit: `CalendarGridTest` (+6 `datesCovered` cases — same-day, inclusive multi-day, midnight-crossing → 2 days, reversed end floored to the start day, end resolved in the supplied zone, and a span across a DST spring-forward not dropping/doubling a day), `InsightsTabStateTest` (+8 — a finished multi-day event shades every covered day; a running event shades through today and extends the streak to now; a 4-day span alone gives `longestStreakDays == 4`; two overlapping spans merge into one 13-day run with the shared days shading darker — the reported case; a midnight-crossing event gives a 2-day streak and marks both cells; a `NONE`-mode null-ended event stays a single point; frequency-over-time still counts each event once at its start while the heatmap spans), `VoiceTest` green with the two new per-voice keys. Full `connectedDebugAndroidTest` on `Pixel_8_API36`: first run 211/219, the 8 failures all the widget-package `bindAppWidgetIdIfAllowed` setup gap (DEV_PLAYBOOK §5 — `appwidget grantbind` never run this AVD session); after `installDebug` + `grantbind --user 0`, 219/219, then **220/220** with the added `CaseDetailInsightsTabTest.gapsCard_infoIcon_opensAndDismissesDefinitions`.

---

## feat/duration-unit-selector

**Scope:** PROGRESS.md Story A item A4. The Manual-mode duration field in `ui/logsheet/LogDetailSheet.kt` was a minutes-only number box, so a multi-day event meant typing thousands of minutes. Now the field carries a compact minutes/hours/days unit selector inside its trailing slot (prototype artifact, variant D — the compact in-field control the product owner picked over a full-width segmented row). `LogDraft.durationMinutes` became `durationAmount: String` + `durationUnit: DurationUnit` (new enum in `viewmodel/LogDetailViewModel.kt`, `millis` per unit sourced from `domain/TimeConstants.kt`); `computeEndedAt` scales the parsed integer by `durationUnit.millis` instead of a hardcoded `MILLIS_PER_MINUTE`; `draftFrom` decodes a stored `endedAt` back onto the largest unit that divides it evenly via a new `durationUnitFor` helper, else minutes. Storage stays millis, no schema change.

**Found & fixed:**
- The Manual `OutlinedTextField` was inline in the main `LogDetailSheet` composable; pulled to a `DurationSection` sibling of `TimeSection`/`IntensitySection`/`EndTimeSection` so the `if (durationMode == MANUAL)` block stays a one-liner like the others.
- The now-wrong `"Minutes"` placeholder (`voice.logSheetDurationHint`, an interface `get()` default with no per-voice overrides and no other callers) was removed outright rather than left showing the wrong unit — the floating "Duration" label plus the unit toggle carry the meaning.
- `DURATION_MINUTES_MAX_DIGITS` renamed to `DURATION_AMOUNT_MAX_DIGITS` (the cap is unit-agnostic now); the `LogDetailViewModelTest` comment that named the old constant updated with it.
- Unit-selector tap targets: each visual segment is ~28 dp tall, so `minimumInteractiveComponentSize()` was added (same modifier order M3's own `IconButton` uses) to keep the drawn control compact while the touch target stays a full 48 dp — closes CLEANUP_CHECKLIST's "tappable targets ≥ 48 dp" against the compact design.
- Small-caps unit labels are rendered `durationUnitLabel(...).uppercase()` at `labelSmall` (matching Settings' `AreaHeader`), not via `fontFeatureSettings = "smcp"` — only Inter and Source Serif of the five theme fonts ship the `smcp` feature, so the OpenType route would silently fall back to lowercase on Bright/Intense.

**Deferred:**
- On-device check of the trailing-slot control: whether ~140 dp of segmented control inside the field leaves enough room for the number on the narrowest phone, and whether the selected segment's `secondaryContainer` fill (now its own content width, centred in a 48 dp min slot rather than edge-to-edge) reads cleanly. Flagged from the start as a Compose-Preview-can't-judge-it risk; variant A (full-width row below the field) is the documented fallback if the density doesn't hold up.
- No instrumented test toggles the selector — the plan scoped UI coverage to per-voice `DurationSection` previews; the scaling/round-trip/fallback logic is all covered in `LogDetailViewModelTest` (JVM). Pick up an instrumented test if the sheet's duration path starts to matter.

**Sections walked, nothing to do:** *Duplication* — the four `DurationSection` previews are near-identical theme swaps, matching the established pattern in `OngoingIndicator.kt` / `SegmentedChoiceRow.kt` / `CaseEditScreen.kt`; `durationUnitLabel` / `durationUnitFor` / `DurationSection` are single-caller but each earns its keep (keeps a `when` out of a composable / is independently tested / keeps the main composable flat). *Decoupling* — `DurationUnit` is a plain enum in `viewmodel/` beside `LogDraft` (no `android.*`, no UI types), consistent with the pure functions already in that file; no `System.currentTimeMillis()`; `domain/` untouched. *Reuse* — `DurationUnitSelector` builds a small selectable Row (the `IntensityChoice` idiom) rather than reusing `SegmentedChoiceRow`, which is a full-width `SingleChoiceSegmentedButtonRow` with M3 segment chrome that doesn't fit a text-field trailing slot; noted on the code. *Hardcoded values* — `DURATION_UNITS` list and the digit cap are named; `Color.Transparent` is the no-background sentinel, every other colour via `MaterialTheme.colorScheme`; `.dp` literals match the file's house style. *Naming* — new `Voice` keys `logSheetDurationUnit{Minutes,Hours,Days}` are shared interface `get()` defaults (structural chrome, exactly as `logSheetDurationHint` was), so they're "in all three voices" by inheritance; `VoiceTest`'s identical-across-voices check excludes non-abstract keys by design. *Deprecated APIs* — `.uppercase()` is the non-deprecated form; build shows only the pre-existing Moshi/kapt warning. No schema change, no new colours/resources.

**Docs updated:** `HODITH_SPEC.md` §6 (detail-sheet bullet — duration is "a whole number with a minutes/hours/days unit"); `PROGRESS.md` (A4 collapsed to a "done" note, shared-file map's `LogDetailSheet.kt` line updated). `TESTING.md` unchanged — it carries no per-feature test list and its "covered only by `LogDetailViewModelTest`'s pure-logic tests" note (line 72) already generalises. No `MANUAL_TEST_PLAN.md` change — the sheet is in-process, no system-boundary crossing.

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, all pass, sequentially. Unit: `LogDetailViewModelTest` (helper + every `computeEndedAt`/`draftFrom` call threaded through the new `durationUnit`; added per-unit round-trip, `150 min → minutes` fallback, hours/days scaling, and `"99999" days` digit-cap ceiling), `VoiceTest` green with the three new shared keys. Full `connectedDebugAndroidTest` on `Pixel_8_API36`: 211/219 on the first pass, the 8 failures all the widget-package `bindAppWidgetIdIfAllowed` setup issue (DEV_PLAYBOOK §5 — the per-AVD `appwidget grantbind` had never been run this emulator session); after `installDebug` + `grantbind --user 0`, the `com.secondmonday.hodith.widget` package re-ran 12/12, so 219/219 effective. The touched instrumented sites (`CaseDetailScreenTest` / `CaseDetailInsightsTabTest` / `HomeScreenTest`) got only the mechanical `durationUnit = DurationUnit.MINUTES` addition and passed on the first pass.

---

## fix/ongoing-affordance

**Scope:** PROGRESS.md Story A item A3. Started as "swap the checkmark Stop glyph + add a colour cue to the running marker"; a design pass with the product owner (prototype artifact) widened it to *one* running-event treatment across every surface. `StopIconButton` now draws a rounded square with `Canvas.drawRoundRect` (`StopSquare`, file-scope fraction constants, `tint` defaulting to `LocalContentColor`) instead of `Icons.Filled.Done`, `contentDescription` untouched. A new `OngoingPill` (`primaryContainer` chip, Voice `ongoingPillLabel`) marks a running event; `OngoingElapsedText`/`OngoingCountText` became `OngoingPill` + a trailing elapsed/count via a private `OngoingSummary`. Case Detail's log header always shows the count form (`OngoingCountText`, "1 running" included) with no Stop; every open event's row shows its own `OngoingElapsedText` and its own `StopIconButton` (the `showInlineStop` gate is gone). Home rows and both widgets show the pill + summary and keep the `+` log button in every state — `HomeViewModel.onQuickLogTap` no longer stops a running event, so on a `START_STOP` Case the `+` starts a second one. `LogDetailSheet`'s `EndTimeSection` drops the past-tense "Ended" label while `endedAt == null`.

**Found & fixed:**
- The Home trailing `+` button was built inline in both `HomeCaseRowBody` and `BrightHomeCaseListItem` (pre-existing duplication, made a clean standalone block by this change). Extracted to one `HomeCaseLogButton(row, voice, onClick)`.
- Both widgets' line-2 (elapsed-or-today logic) was duplicated between `ListWidget.CaseRow` and `SingleCaseWidget.SingleCaseContent`. Extracted to `WidgetCaseSubtitle(row, now)` (in `ListWidget.kt`, `internal`, doc'd as shared — mirrors how the widgets already share `formatElapsedDuration`/`homeCaseRows` loosely rather than through a dedicated file).
- First cut joined the pill and its trailing text with an inline `"· "`. That's a user-visible separator string outside the Voice layer (the old per-voice `ongoingIndicator` carried its own "·"/"—"), so it was dropped — the chip + a 6dp gap carry the separation now, no string.
- Dead code from the removals: Voice `ongoingIndicator` (replaced by the pill), `widgetStopAction` and `widgetRunningCount` (all three voices); `StopEventAction` + `EventIdParam` in `WidgetCommon.kt`; `WidgetPalette.stopBackground`/`onStopBackground`; `HomeViewModel.stopEvent`; the `showInlineStop` parameter threaded through `EventRow`/`EventRowContent`. `WidgetRefreshWorker`'s KDoc reference to `StopEventAction` trimmed.
- `Color.kt` inlined `primaryContainer`/`onPrimaryContainer` hex in `plainLight`; pulled to `PlainLightPrimaryContainer`/`PlainLightOnPrimaryContainer` constants so `WidgetPalette.accentContainer` sources them the same way as the other `PlainLight*` roles. The "seven roles" count in the comment above them (already stale) was reworded to drop the number.
- `OngoingPill` started public; only `OngoingSummary` and the file's own previews call it, so it's `private` with no `modifier` param now. Its KDoc records why it's a `Surface` + `Text` and not an M3 `AssistChip`/`SuggestionChip` — those carry button semantics, ripple and a 32dp min height, wrong for a static status label.
- `StopSquare` reimplements a filled square rather than using a Material icon — same justification as `InfoIcon` (the project ships no `material-icons-extended`, and the default set has no "stop" glyph), documented on it.

**Deferred:**
- `eventDetailSummary` keeps its `isOngoing` parameter even though it no longer prepends a label — it still needs the gate to suppress a stray `endedAt` on a reopened event from rendering a "lasted N" line. Documented on the function.
- On-device sizing of the pill + count line on the narrowest widget / a long Case name is left for the human's exploratory pass — the layout is single-line by design and can't be judged from Previews.

**Sections walked, nothing to do:** *Decoupling* — no composable gained business logic (`formatElapsedDuration` is the same pure util the widgets/Home/Insights already share, `now` still passed in); `HomeViewModel`/`CaseDetailViewModel` changes only remove code and touch no UI types; the two new `Color.kt` constants keep the existing widget-palette sourcing pattern, not a new layer crossing. *Complexity & Pattern Health* — every touched composable got shorter (the widgets' 3-branch trailing `if/else` collapsed to one button; the two Home row bodies lost their inline button); no new `LaunchedEffect`/`remember`/coroutine scope; new helpers each have 2 real callers. *Decoupling / time* — no `System.currentTimeMillis()` (previews use literal offsets, not runtime). No `android.*` in `domain/` (not touched). No schema change. Magic numbers: `STOP_SQUARE_*_FRACTION` named; widget `sp`/`dp` stay inline per that package's house style. Accessibility: `StopSquare` keeps its `contentDescription`, the pill is a readable `Text`, the `+` stays a 48dp `IconButton`. Deprecated APIs: full `--rerun-tasks` compile shows only the pre-existing Moshi/kapt-codegen warning, nothing new.

**Docs updated:** `HODITH_SPEC.md` §6 (the "Ongoing" pill treatment; summary surfaces show a count and never an inline Stop; Stop lives on each open event's log row; the `+` stays put so a second event can be started from it — reverses the one-vs-many split, the summary Stop, and "the Start affordance becomes the Stop action") and §15 (widgets show the pill + summary and keep their log button); `PROGRESS.md` (A3 collapsed to a "done" note; shared-file map's A3 entries dropped). `TESTING.md`: see below.

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, all pass. Unit: `HomeViewModelTest` (the ongoing-`START_STOP` `onQuickLogTap` test rewritten from "stops it" to "starts a second concurrent event"), `CaseDetailFormattingTest` (three `isOngoing` tests rewritten — the summary no longer carries a running label), `VoiceTest` green with the new `ongoingPillLabel`. Full `connectedDebugAndroidTest` on the `Pixel_8_API36` emulator: 218/219 on the first run — the one failure, `WidgetActionsFlowTest.runningCase_showsOngoingPill_andTheCaseAreaOpensCaseDetail`, looked for the Case *name* as a tap target, which the Single-case widget doesn't render (icon + subtitle only); retargeted to the "Ongoing" pill inside the same tappable column and re-verified 3/3 for that class. A confirmatory full re-run then hit an unrelated emulator crash (`INSTRUMENTATION_ABORTED: System has crashed` at ~test 153, `am` shell services dead); re-run clean on a cold-booted emulator: **219/219**. `CaseDetailScreenTest` (header-Stop test retargeted to the row; a pill-count test added), `HomeScreenTest` (Stop-instead-of-log and no-trailing-button tests rewritten for the always-present `+`), `WidgetActionsFlowTest` (Stop-tap and running-pill tests replaced with "log button starts a second event" and "pill renders + Case area opens Case Detail") all pass.

---

## fix/duration-gap-from-end

**Scope:** PROGRESS.md Story A item A1, reopened. The first pass (`fix/ongoing-current-gap`) zeroed the current gap only while an event was *running*; a *finished* duration event still misreported, because `computeGapStats` (`domain/InsightsEngine.kt`) fell back to `daysBetween(lastEvent.occurredAt, now)` once it stopped — start-anchored — so a six-day event wrapped up today read as a six-day current gap. Now a gap is measured from when an event ended: current gap counts from the furthest end any event reached, past gaps are each measured from that running furthest-reach to the next event's start (`coerceAtLeast(0L)`), point events (`endedAt == null`) unchanged. The running-reach (rather than the previous event's end) is what makes overlapping same-Case durations — common for things like family sick days — not split one silence into two. `SILENT_FOR` triggers and check-ins shared the bug — both anchored on `getMostRecentEventForCase(...)?.occurredAt` in `NotificationEvaluator` — and now read a shared `silenceAnchorFor(...)` helper backed by a new `EventDao.getLatestEventEndForCase` (`SELECT MAX(IFNULL(endedAt, occurredAt))`), with a still-running event on a `START_STOP` Case pinning the anchor to `now` (zero silence).

**Found & fixed:**
- The two `NotificationEvaluator` call sites had the same `repo.getMostRecentEventForCase(case.id)?.occurredAt` line; the SILENT_FOR/check-in anchor logic (latest end, plus the running-event pin) is now one `silenceAnchorFor(repo, case, now)` helper both call, with the rule documented once on it.
- `getLatestEventEndForCase` went in as a SQL `MAX(IFNULL(endedAt, occurredAt))` aggregate rather than pulling `observeEventsForCase(...).first()` and reducing in Kotlin — the periodic job runs it per Case, and CLEANUP_CHECKLIST's "push the filter into SQL" item points the same way.
- `getOngoingEvent` already existed on `EventDao` but not on `HodithRepository`; lifted it to the interface (+ `RoomHodithRepository` delegate, `FakeHodithRepository` filter) so `silenceAnchorFor` can check the running state without a second bespoke query.
- First cut of `computeGapStats` used `zipWithNext` measuring each gap from the *previous-by-start* event's end. That's wrong for overlapping durations: a short event nested inside a longer one made the gap after it read from the short event's end, inventing silence the longer event was still filling (a day-25 event after A ran 0–20 with B nested 5–6 reported a 19-day gap, not 5). Replaced with a running furthest-reach pass; the KDoc and an inline comment explain why, and a regression test (`measures a past gap from the furthest end reached, not the previous event's`) pins it.
- The new duration-event test helper landed under two names in sibling files — `durationEvent` in `InsightsEngineTest`, `durationEventOverDays` in `InsightsTabStateTest`. Renamed both to `durationEvent` (the codebase already duplicates `eventAtDay`/`millisAtDay`/`testCase` per unit-test file by house style, so the duplication is fine — only the name needed to match).

**Deferred:**
- `getMostRecentEventForCase` is now unused by production code (only `androidTest` assertions and its own DAO/Fake tests still call it). Kept: it's a small, tested, sensibly-named primitive that four instrumented test classes use to fetch a just-logged event, and removing it is disproportionate churn for no runtime benefit. Revisit if it's still unused when the next feature touches `EventDao`.
- The Home row today/this-week counts have the same start-anchored assumption (an event finished today still shows "Today 0") — logged as its own PROGRESS.md item **A8** (`feat/home-counts-duration-span`), gated on A5's active-span rule so Home and the calendar heatmap agree rather than fixed ad hoc here.

**Sections that didn't apply:** no composables, Voice keys, colours, or resources touched (numbers-only change, no new user-visible strings). No `System.currentTimeMillis()` — `computeGapStats` takes `now: Long`, `silenceAnchorFor` takes `now` from the injected `Clock`. No `android.*` in `domain/` — `InsightsEngine.kt` adds no imports. No schema change — the new query reads existing columns. No magic numbers — the zero-silence pin is the plain meaning, not a product constant. Accessibility: the current-gap value is rendered text (`StatRow`), no UI change. Deprecated APIs: build shows only the pre-existing Moshi/kapt warning. §17 Future work has no matching item.

**Docs updated:** `HODITH_SPEC.md` §10 (Gaps & streaks — current gap is silence since the last event *ended*; SILENT_FOR/check-ins count from the same point) and §11 (`SILENT_FOR` and the check-in bullet reworded for the end-anchor and the running-event case); `TESTING.md` (Trigger evaluation, Check-in scheduling, Notification evaluation, Stats & visual data prep, Room DAOs rows); `PROGRESS.md` (A1 collapsed back to a "done" note recording the reopen; A8, S7 and S8 added; A5's "Builds on A1" pointers left as "(done)").

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, all pass. Unit: `InsightsEngineTest` +5, `InsightsTabStateTest` +1, `NotificationEvaluatorTest` +3, `FakeHodithRepositoryTest` +2. Full `connectedDebugAndroidTest` on the `Pixel_8_API36` emulator: **219/219**, 0 failures (`EventDaoTest` 13→16 with the new `getLatestEventEndForCase` cases; `CaseDetailInsightsTabTest` 18/18 confirms the gaps card is unaffected; the widget and notification suites confirm the `NotificationEvaluator` anchor change leaves those flows intact).

---

## feat/multiple-ongoing-events

**Scope:** PROGRESS.md Story A item A2 — more than one event can run on a `START_STOP` Case at once (the data model always allowed it), but past the first the app lied: Home rows, the Case-detail log header, and both widgets showed only the first, the widget Stop button only stopped the first, a stopped event couldn't be reopened, and switching a Case out of `START_STOP` stranded whatever was running. `ongoingEventsIn` (list, earliest-first) now sits beside `ongoingEventIn` (reduced to `ongoingEventsIn(...).firstOrNull()`, so it's deterministic where `List.find` over Room's relation order wasn't). Summary surfaces read "N running" past one event and drop the summary Stop; each running row gets its own Stop (`showInlineStop`, gated to ≥2 so the exactly-one case is byte-for-byte unchanged); both widgets swap the red Stop for an accent "N running" pill that opens Case Detail. `LogDetailSheet`'s End section gained a "Back to ongoing" `TextButton`; `planSaveEvent` rebases `staleNudgeDismissedAt` on a genuine reopen so an old start doesn't fire the stale prompt on the next render. `CaseEditViewModel` holds a leave-`START_STOP` mode change behind a `ConfirmDialog` when events run and stops them all on save. Several concurrently-stale running events collapse to one consolidated header banner.

**Found & fixed:**
- First cut gave every ongoing `EventRow` its own Stop button unconditionally — that put a second identical-`contentDescription` Stop control next to the header's at exactly one running event (redundant, and it broke `CaseDetailScreenTest`'s single-node lookup). Added `showInlineStop`, true only at ≥2, so the header keeps the sole Stop at one and the rows carry it past that.
- First cut had the widget row's subtitle *and* its trailing pill both say "N running". Pulled the subtitle back to the neutral today-count for the multi-running case (both widgets) so the row doesn't repeat itself.
- `onDurationModeChange` grew a guard + two new callbacks; extracted `applyDurationMode(...)` so the guarded path, the confirm path, and the unguarded path share one state update instead of three copies.
- Reused `repository.observeEventsWithTagsForCase` for the running-event count in `CaseEditViewModel` rather than adding `observeEventsForCase` to the repository interface / `RoomHodithRepository` / `FakeHodithRepository` — the edit screen opens rarely, the extra tag join is cheap, and the surface stays smaller.
- `Color.kt`'s `plainLight.onPrimary` was an inline `Color(0xFFFFFFFF)`; promoted to a named `PlainLightOnPrimary` (matching the file's stated "source these from here, don't duplicate hex" convention) so `WidgetPalette.onAccent` can reference it for the pill's text.

**Deferred:**
- `HomeCaseRowBody` and `BrightHomeCaseListItem` remain near-duplicate bodies — this change widened the shared shape (the `when` on `runningCount`) in both. The two are deliberately kept separate per their own comments (Bright's `IconHalo`/`GlowCard` structure diverges), and merging them is its own refactor, not A2's job.
- Per-row Stop buttons all carry `stopActionDescription(caseName)`, so a screen reader hears the same label on each of 2+ concurrent rows. The row's own timestamp text disambiguates visually; a per-event a11y label would mean a new Voice key for a rare edge. Left for the B2 Voice pass if it wants it.

**Sections that didn't apply:** no `System.currentTimeMillis()` (VM uses injected `Clock`, `planSaveEvent` takes `now`); no `android.*` in `domain/` (`ongoingEventsIn` lives in `viewmodel/` beside `ongoingEventIn`, unchanged location); no new Dao query or schema change (`computeDurationStats` already ignores `endedAt == null`); no magic numbers (the "≥ 2" threshold is the plain meaning of "more than one", not a product constant). New Voice keys added to all three voices in this commit. Accessibility: the "N running" text is rendered text, the pill and per-row Stop are ≥ 48 dp, icon buttons keep non-empty `contentDescription`.

**Docs updated:** `HODITH_SPEC.md` §6 (Start/stop reworded — affordance vs. hard limit, the count surfaces, per-row Stop, widget pill, reopen, mode-change guard, consolidated stale prompt) and §10 (gaps "any event", duration excludes running events, duration/intensity cards gated on the flags not the data); `TESTING.md` (Stats, ViewModels, Compose UI, Widgets rows); `MANUAL_TEST_PLAN.md` (item 6 extended with the List-widget "N running" pill parity check); `PROGRESS.md` (A2 collapsed to a one-paragraph "done" note, shared-file map's A2 entries removed).

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, all pass. Unit: 467 total, 0 failures (`OngoingEventTest` 11→15, `HomeViewModelMappingTest` +2, `LogDetailViewModelTest` +2, `CaseEditViewModelTest` +5, `StatsEngineTest` +1). **Instrumented tests were written but not run — no device/emulator available in this session.** `WidgetActionsFlowTest`, `CaseDetailScreenTest`, `HomeScreenTest`, `CaseEditScreenTest` all compile (`compileDebugAndroidTestKotlin` green); they need a `connectedDebugAndroidTest` pass on a device with `adb shell appwidget grantbind` before merge.

---

## fix/ongoing-current-gap

**Scope:** PROGRESS.md Story A item A1 — the Insights "Gaps & streaks" card's current gap kept climbing while a `START_STOP` Case had an event running, and that active stretch was folded into the longest gap. `computeGapStats` (`domain/InsightsEngine.kt`) gained an `eventActiveNow: Boolean = false` parameter (current gap pinned to 0, kept out of `longestGapDays` / `isCurrentGapLongest`); `insightsTabState` derives it via the existing `ongoingEventIn(case, events)` helper, so both Compose call sites stay unchanged. Plus a demo-data change so the fix (and the ongoing indicator / stale prompt / multi-running path) has something to show on "Load demo data": `DemoDataSeeder` now seeds ongoing events — Migraine one, a new "Noisy neighbours" Case two (one ~2h old, one ~26h old to trip the 24h stale prompt).

**Found & fixed:**
- The ongoing-event insertion in `DemoDataSeeder.seed()` duplicated the main occurrence loop's `repository.insertEvent(EventEntity(...))` + `tagsFor(...).forEach { addTagToEvent }` block — extracted `private suspend fun insertSeedEvent(caseId, occurredAt, endedAt, caseSeed, random)`, both call sites now use it (the only behavioural difference was `endedAt`: `endedAtFor(...)` vs. `null`).
- `computeGapStats`'s `currentGapDays` line pulled the time-since-last-event calc into a named `timeSinceLastEvent` local so the `if (eventActiveNow) 0L else …` branch reads cleanly.
- `DemoDataSeederTest` — the first instinct for a new invariant test ("every open-ended event belongs to a START_STOP case") was wrong: `endedAtFor` returns `null` for every NONE/MANUAL event, so open-ended events are everywhere in the raw data — that's exactly why `ongoingEventIn` gates on `durationMode`. Replaced with meaningful assertions instead: Migraine has exactly one open event, Noisy neighbours exactly two, Workout (START_STOP, no ongoing seed) has none, and at least one open event on a START_STOP case is ≥ 24h old.

**Deferred:**
- HODITH_SPEC §6's "One ongoing event per Case" line is now technically contradicted by the demo data (Noisy neighbours runs two at once). Left as-is: the §6 clarification ("summary indicators show a count; the phrase describes the Start affordance, not a hard limit") is an explicit acceptance criterion and 🎨 design-decision of **A2**, not A1. The data model (§5, `endedAt` nullable) already permits it, and the Start-affordance behaviour §6 describes is still accurate. Flagged for whoever picks up A2.
- No new `@Smoke` — added to the existing `@UiTest`-annotated `CaseDetailInsightsTabTest`, not a new class, and the ongoing-gap case isn't the class's representative happy path.

**Sections that didn't apply:** no new composables, Voice keys, colours, resources, Repository/Dao surface, or `android.*` imports in `domain/` (checked the full diff — `InsightsEngine.kt`/`InsightsTabState.kt` add no import lines; no `Voice*.kt` touched). No `System.currentTimeMillis()` — `computeGapStats` still takes `now: Long`, the seeder uses the injected `Clock`. Accessibility: the current-gap value is rendered text (`StatRow`), not colour. Deprecated APIs: build shows only the pre-existing Moshi/kapt warning. HODITH_SPEC §17 (Future work) has no item matching this change — nothing to strike there. No system-process boundary, so nothing for MANUAL_TEST_PLAN.md.

**Docs updated:** `HODITH_SPEC.md` §10 (Gaps & streaks — current gap "reads 0 while an event is running"); `TESTING.md` (Stats & visual data prep unit row; Compose UI — Case Detail Insights row); `PROGRESS.md` (A1 collapsed to a one-paragraph "done" note kept in place — per the user, so Story A's dependency chain stays legible — rather than removed; A5's two "Depends on A1" pointers updated to "Builds on A1 (done)").

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, all pass. Unit: `InsightsEngineTest` 25→30, `InsightsTabStateTest` 10→13, `DemoDataSeederTest` 5→9. Full `connectedDebugAndroidTest` on the `Pixel_8_API36` emulator: **208/208** (`CaseDetailInsightsTabTest` 17→18 with the new `gapsCard_currentGapReadsZero_whileAnEventIsRunning`; `CaseDetailScreenTest` and the widget suites confirm the `ongoingEventIn`/`computeGapStats`/seeder changes leave the Log-tab ongoing flow and widgets untouched). First full run showed 7 widget failures — all `bindAppWidgetIdIfAllowed failed`, a stale `BIND_APPWIDGET` grant on the reused AVD (DEV_PLAYBOOK §"Real, end-to-end AppWidgetHost tests"), identical on clean `main`; fixed with `installDebug` + `adb shell appwidget grantbind`, green on the re-run.

---

## fix/plain-light-palette-neutrality

**Scope:** PROGRESS.md's "Plain theme's light background/surface colors" item — the light neutrals read as blue-grey/murky. What started as a hex retune became a full visual prototype (`docs/mockups/plain-theme-light-neutrals.html`), iterated live with the user across ~15 rounds, then implemented: `Color.kt`'s `plainLight` scheme retuned (background/surface/outline family, plus two newly authored roles — `surfaceContainerLow` for Insights/Hunch cards, `tertiaryContainer`/`onTertiaryContainer` for Settings' buttons specifically), a new white-plank-on-tinted-background pattern for Plain's Home rows and Log's event rows (Intense unchanged), Settings' `Plank` losing its border for Plain only (Intense keeps it), and a custom outlined info icon replacing the filled Material one everywhere. A second round, driven by the user running a real build and screenshotting it against the approved mockup, found and fixed several places where Plain's now-far-more-visible `background`/`outline` values exposed real bugs the old near-invisible palette had been masking.

**Found & fixed:**
- `SettingsScreen.kt`'s `Plank`/`ActionRow` and `SegmentedChoiceRow.kt`'s Plain/Intense branches initially duplicated whole blocks (differing only in card type or one `colors` param) — collapsed each to a single call site with a computed value (`PlankContent` helper for `Plank`; a `colors`/`when` expression for `ActionRow` and `SegmentedChoiceRow`).
- `SectionWithInfo.kt`'s new `InfoIcon` had five inline magic-number fractions for its geometry — extracted to named `private const val`s, matching `InsightsTab.kt`'s existing convention for this kind of drawing constant.
- Writing the new WCAG contrast test (`HodithThemeTest`) surfaced a real pre-existing bug unrelated to this branch: Bright light's `onSurfaceVariant` (#8A7A68) fails 4.5:1 AA against both `surface` (~4.15:1) and `background` (~3.91:1). Not fixed here — logged as a new PROGRESS.md Bugs item (`fix/bright-light-onsurfacevariant-contrast`) with a note to widen the new test back to all 6 combos once it lands. The new test is scoped to Plain only for this reason.
- `Icons.Outlined.Info` isn't available (project deliberately depends only on `material-icons-core`, same constraint `feat/clear-archive` hit for a different icon — see below); built a small `Canvas`-based `InfoIcon` instead of a hand-rolled `ImageVector` path, since arbitrary bezier/arc path data couldn't be visually verified in this session before landing.
- **Second round, from real-build screenshots:** Insights/Hunch cards rendered M3's stock baseline purple, not white. Root cause: `Card()`'s default `containerColor` isn't `surfaceContainerLow` (which was authored) but a different, still-unset tier (`surfaceContainer`) — `lightColorScheme(...)`/`darkColorScheme(...)` fill any unset parameter with a hardcoded M3 baseline color, not something derived from the scheme's own `primary`. Same mechanism was independently responsible for the bottom nav bar not matching the mockup (`NavigationBar`'s default `containerColor` → `NavigationBarDefaults.containerColor` → the same unset `surfaceContainer`). Fixed properly instead of patching one instance: authored the *entire* surfaceContainer family (Lowest/Low/plain/High/Highest) in **all six** schemes, not just Plain's — a screenshot of Bright's Hunch tab confirmed Intense and Bright had the identical latent leak (`HunchCard` is a single unbranched `Card()` shared by all three themes; `InsightsCard` branches Bright to `GlowCard` but not Intense). Every tier is set equal to that scheme's own `surface`, except `surfaceContainerHigh`, which keeps each theme's existing deliberate value.
- Case Edit showed an unwanted blue background — its `Scaffold`, like every other `Scaffold` in the app, had never passed an explicit `containerColor` and so defaulted to `background`, invisible when `background` was near-white and glaring now that it's visibly tinted. Fixed for Plain only (`containerColor = surface`, matching `LogDetailSheet`'s `ModalBottomSheet`, whose M3 default container role is `surface` not `background` — the mechanism the user wanted Case Edit to match); Intense/Bright keep defaulting to `background` as before.
- Big Picture's week-row border (`outlineVariant`) went from invisible to prominent for the same reason — removed for Plain per direct request; Intense/Bright keep it, since it was already a deliberate, subtle part of their existing look and this branch never touched it before.
- All three of the above (nav bar, Case Edit, week-row border) initially landed as shared/unbranched fixes affecting all three themes, since none of those call sites had ever been theme-branched — reworked into explicit Plain-only branches after the user asked for the themes to stay fully independent, so Intense/Bright now provably keep their pre-existing behavior rather than incidentally inheriting Plain's fixes.

**Deferred:**
- No new Compose Preview for Case Detail's Log tab — unlike Home/Insights/Settings, `CaseDetailScreen.kt` has zero existing preview infrastructure to extend, and building fake `CaseEntity`/`EventEntity`/`CaseDetailUiState` data from scratch was disproportionate to this item's scope. Home, Insights, and Settings each got a new Plain preview by extending an existing Bright preview's pattern.
- No new Intense previews for the touched screens (Home/Log/Settings) confirming they're visually unchanged — Intense's code paths weren't touched at all (still call the same pre-existing functions or an explicit branch preserving old behavior), so the regression risk is low; deferred rather than padding an already-large diff.
- Bright light's `onSurfaceVariant` contrast gap (see above) — separate, unrelated theme, logged as its own PROGRESS.md item.

**Docs updated:** `PROGRESS.md` (struck the resolved item and the surfaceContainer-leak item once fixed; added the Bright-contrast bug item, still open); `Color.kt`'s own top-of-file doc comment (documents the surfaceContainer-authoring rule and why, across all six schemes).

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, all pass, rerun after each round of fixes. No instrumented run — nothing here crosses a system-process boundary or adds new ViewModel/domain logic; visual sign-off against the approved mockup was the user's, via real-build screenshots that drove the second round of fixes above.

---

## feat/clear-archive

**Scope:** PROGRESS.md's "Case archive" item — `ArchivedCasesScreen.kt` only offered per-row Unarchive/Delete forever, no way to clear the whole archive at once. Added a scoped bulk-delete: `CaseDao.deleteAllArchived()` (`DELETE FROM cases WHERE archived = 1`, cascades via existing FK `ON DELETE CASCADE`, distinct from the existing unscoped `deleteAll()`), threaded through `HodithRepository`/`RoomHodithRepository`/`FakeHodithRepository` as `deleteAllArchivedCases()` and `ArchivedCasesViewModel.clearArchive()`, exposed in `ArchivedCasesScreen.kt` as a top-bar icon (hidden when the archive is empty) opening the existing `ConfirmDialog` naming the archived-case count — same pattern as the existing per-row delete-forever dialog.

**Found & fixed:**
- New `Voice` keys (`clearArchiveButtonDescription`, `clearArchiveConfirmTitle`, `clearArchiveConfirmBody(caseCount)`, `clearArchiveConfirmAction`, `clearArchiveConfirmCancelAction`) added to all three voices in the same commit, each its own dedicated key rather than reused across Settings' analogous "delete all data" dialog — matches how those two dialogs already have separate keys despite overlapping text per voice.
- No material-icons-extended dependency exists in this project (only core `Icons.Filled.*`/`Icons.AutoMirrored.Filled.*` are used anywhere), so the top-bar action reuses `Icons.Filled.Delete` rather than pulling in a new icon-set dependency for a `DeleteSweep`-style icon.
- `ktlintFormat` reflowed a multi-line chained call in `FakeHodithRepository.deleteAllArchivedCases()` that `ktlintCheck` flagged (`Expected newline before '.'`) — no other files needed reformatting.
- `ArchivedCasesScreenTest.kt`'s new `assertDoesNotExist()` call had an unnecessary explicit `import androidx.compose.ui.test.assertDoesNotExist` that broke `compileDebugAndroidTestKotlin` (`Unresolved reference`) — it's a member function on `SemanticsNodeInteraction`, not a top-level extension, so no import is needed; every other call site in the repo already omits it. `ktlintCheck`/`lintDebug`/`test`/`assembleDebug` don't compile the `androidTest` source set, so this only surfaced once `connectedDebugAndroidTest` actually ran.

**Deferred:**
- Nothing deferred — the item's own plan scoped it to a single button/dialog, no multi-select UI.

**Docs updated:** `HODITH_SPEC.md` §14 (Archived Cases screen row now lists the top-bar clear action); `TESTING.md` (Room DAOs and Compose UI — Archived Cases coverage rows); `PROGRESS.md` (struck the resolved "Case archive" item).

**Verified:** `ktlintCheck` → `lintDebug` → `test` → `assembleDebug` → `connectedDebugAndroidTest`, all pass on a real emulator (API36_Repro AVD) — 207/207 instrumented tests, including the new `CaseDaoTest.deleteAllArchived_removesOnlyArchivedCasesAndCascades` and the three new `ArchivedCasesScreenTest.clearArchive*` cases. One unrelated test (`CaseDetailInsightsTabTest.belowInsightsMinEvents_showsNotEnoughDataPlaceholder_notAnEmptyChart`) failed on the first full run with an `ActivityScenario` teardown timeout (`Activity never becomes requested state [DESTROYED]`) and passed cleanly when the class was rerun in isolation — a pre-existing emulator flake, not a regression from this branch.

---

## fix/backup-import-validation

**Scope:** PROGRESS.md's "Data integrity" item — backup restore had no semantic validation: `SettingsViewModel.performImport` only checked JSON shape and schema version, and `RoomHodithRepository.importBackupData` inserted every deserialized entity as-is, so a malformed backup could throw an uncaught `SQLiteConstraintException`.

**Found & fixed:**
- New `viewmodel/BackupValidationResult.kt`: pure `validateBackup(BackupData)`, checking every field rule and cross-entity reference against the same constants the in-app editors already enforce — blank/over-length case name, description, tag name, event note; blank case icon; duplicate tag names; hunch `expectedCount` and trigger `threshold` ranges; the `AT_LEAST`/`SILENT_FOR` `windowDays` invariant; dangling `caseId`/`eventId`/`tagId` references; duplicate non-zero ids within any entity list (a Room PK-conflict crash class parallel to the FK/uniqueness ones, not in the original item text but the same mechanism). Whole-file, reject-only: any violation rejects the entire backup, nothing is clamped.
- `SettingsViewModel.performImport` calls `validateBackup` before the repository is touched, returning a new `ImportFailureReason.SEMANTIC_INVALID` on failure; the repository call itself is wrapped in `catch (e: SQLException)` as a backstop, narrow enough that `CancellationException` still propagates.
- New Voice key `settingsImportFailureSemanticMessage`, added to all three voices in the same commit.
- `THRESHOLD_RANGE` (`TriggersScreen.kt`) and `EXPECTED_COUNT_RANGE` (`HunchCreationSheet.kt`) made `internal` so the validator can reuse them directly instead of redeclaring the numbers.
- First draft placed the validator in `data/backup/`, importing constants from `ui.*` and `viewmodel.*` files — the checklist's Decoupling section caught this (`data` had never imported from `ui`/`viewmodel` anywhere else in the codebase). Moved the whole file to the `viewmodel` package instead, matching `CaseEditValidation`'s existing placement; `data/` is clean of `ui`/`viewmodel` imports again.
- 21 unit tests in `BackupValidationResultTest.kt`, one per rule. `SettingsViewModelTest` gained a case: a well-formed-JSON-but-dangling-reference backup is rejected without the fake repository's data changing. `RoomHodithRepositoryBackupTest`'s existing rollback test got a comment noting it now demonstrates a backstop, since validation normally catches this one layer up.
- Verified sequentially via `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`, then `connectedDebugAndroidTest` on a real emulator — all 203 instrumented tests pass, including all four `RoomHodithRepositoryBackupTest` cases and `BackupImportIntegrationTest`.

**Deferred:**
- The three constants the validator reuses that live in `ui.casedetail`/`ui.logsheet`/`ui.triggers` files (`EXPECTED_COUNT_RANGE`, `TAG_NAME_MAX_LENGTH`, `THRESHOLD_RANGE`) are still imported into the `viewmodel` package. A cleaner end state would centralize all six length/range constants in one layer everything can depend on downward (`domain/`, per CLAUDE.md's product-constants rule) — left alone here since it means moving constants used by five existing files, a larger refactor than this item's scope.
- No new `BackupImportIntegrationTest` case: that test exercises `RoomHodithRepository.importBackupData` directly against a real DB, one layer below where `validateBackup` runs, so a malformed-backup case there wouldn't exercise the new code path.

**Docs updated:** `HODITH_SPEC.md` §16 (import behavior now matches what's built); `TESTING.md` (Export/import coverage row); `MANUAL_TEST_PLAN.md` (Data & backup section's test-class list); `PROGRESS.md` (struck the resolved "Data integrity" item).

---

## fix/input-length-guardrails

**Scope:** PROGRESS.md's "Input validation" item — event note and tag name had no length cap, and the trigger custom-window (days) field silently accepted `0`. A deeper follow-up audit run before implementation (prompted by a request to check for gaps beyond length caps) also found a 7th text-input site the original pass missed (Share screen's display-name override, uncapped) and confirmed the trigger threshold/hunch expected-count fields have no edit path that bypasses their stepper's bound — only backup/restore can, logged separately rather than implemented here.

**Found & fixed:**
- `LogDetailViewModel.kt`: added `EVENT_NOTE_MAX_LENGTH = 280`, applied at the existing trim-to-null seam in `LogDraft.toEventEntity`.
- `TagInput.kt`: added `TAG_NAME_MAX_LENGTH = 30`, applied inside `tagToAdd`'s existing trim/dedupe logic.
- `ShareViewModel.kt`: `setDisplayNameOverride` now caps at the existing `CASE_NAME_MAX_LENGTH` (reused rather than duplicated — this field overrides that same name).
- `TriggersScreen.kt`/`TriggersViewModel.kt`: `canSave` now rejects a zero-day custom window (matching the screen's existing disabled-button feedback convention — no new inline error text or Voice strings, since none exist anywhere else in this screen); `TriggersViewModel.createTrigger` also no-ops for an `AT_LEAST` trigger with a null-or-non-positive `windowDays`, since the UI-only fix doesn't protect the ViewModel if it's ever called another way — this is the actual data guardrail and the layer that's unit-testable.
- Each cap is a constant declared next to its own call site, matching the existing `CASE_NAME_MAX_LENGTH`/`CASE_DESCRIPTION_MAX_LENGTH`/`DURATION_MINUTES_MAX_DIGITS`/`CUSTOM_WINDOW_MAX_DIGITS` precedent — not centralized, and not in `domain/` (that rule targets verdict/trigger business thresholds, not form-field caps; the codebase's own practice for this category is consistently non-domain).
- Four regression tests added: `LogDetailViewModelTest` (note truncation), `TagInputTest` (tag truncation), `ShareViewModelTest` (display-name truncation), `TriggersViewModelTest` (zero-day window rejected, no insert). Existing tests in the same files pass unmodified — their fixture strings are all well under the new caps.
- Verified sequentially via `ktlintCheck` → `lintDebug` → `test` → `assembleDebug`.

**Deferred:**
- Backup/restore import validation, found during the same audit: `SettingsViewModel.performImport` only checks JSON shape/schema version; `RoomHodithRepository.importBackupData` then inserts every deserialized entity as-is with no length/range/referential-integrity checks, and the insert isn't wrapped in a try/catch anywhere (no `CoroutineExceptionHandler` exists in the app), so a malformed backup — e.g. a dangling `caseId` reference — can throw an uncaught `SQLiteConstraintException`. Materially bigger and riskier than this branch's fixes; logged as its own new, High-priority PROGRESS.md item (`fix/backup-import-validation`) rather than folded in here, per direction.
- Case-name uniqueness scoped to active cases only: confirmed as existing, deliberate behavior (per the code's own doc comment), not a bug — documented in the spec instead of changed.
- No `androidTest` coverage added for any of these guardrails — the original PROGRESS.md item's own text already called this out as a separate, suite-wide gap (no `performTextInput` call exists anywhere in the instrumented suite today), not specific to these fields.

**Docs updated:** `docs/HODITH_SPEC.md` §5 (Case table's `name` row now notes the active-cases-only uniqueness scope). `docs/PROGRESS.md` — struck the resolved "Input validation" item; added the new backup/restore validation item under a new "Data integrity" section.

---

## test/insights-tab-rendered-values

**Scope:** PROGRESS.md's "Insights instrumented coverage gap" item — the pure-Kotlin engine tests already pinned exact computed values for every Insights metric, but the instrumented `CaseDetailInsightsTabTest` only checked card presence/gating, never the actual rendered numbers. Test-only change, no production code touched.

**Found & fixed:**
- Added four tests to `CaseDetailInsightsTabTest.kt` asserting hardcoded literal rendered text for the Duration, Intensity, Frequency, and Gaps & Streaks cards, seeded with known data and hand-derived expected strings (not calling the same formatter the UI calls, which would make the assertion a tautology). Corrected the class KDoc's closing sentence, which had justified the original gap in a way that itself missed the wiring-bug risk.
- First attempt at the Frequency test asserted bare digit counts ("3", "1") and failed on-device: the calendar heatmap renders day-of-month numbers 1–31 as plain text on the same tab, so any small bare-digit assertion collides with a heatmap cell. Tried scoping via `hasAnyAncestor` first — didn't work, since the section label and the bar counts are siblings under the card's `Column`, not ancestor/descendant. Fixed by seeding bucket counts >31 (33/34 events), which can't collide with any day-of-month value; simpler than tree-scoping and needed no production `testTag` (the app has none today, deliberately — text-based assertions are the established convention throughout `androidTest`).
- Verified all 17 tests (13 existing + 4 new) pass via `connectedDebugAndroidTest` on a real emulator, not just compile-checked.

**Deferred:** nothing.

**Docs updated:** PROGRESS.md (item struck as resolved).

---

## test/instrumented-suite-hygiene

**Scope:** PROGRESS.md's instrumented-suite-hygiene item: dedupe copy-pasted `app/src/androidTest` helpers, and fix tests whose only assertion is on the *final* state after two sequential UI actions — so a silently no-op'd setup click can't be told apart from one that worked. The doc named two confirmed BigPicture tests and asked to sweep `BigPictureScreenTest`/`SettingsScreenTest`/`SharePreviewScreenTest` for the same shape; the sweep (and a broader pass across the rest of `androidTest`) found more real instances than the two named, and this pass fixed all of them rather than just the doc-minimum two.

**Found & fixed:**
- **Duplicated helpers, extracted:**
  - `WidgetRenderTestFixtures.kt` (new, sibling to `WidgetConfigureTestFixtures.kt`): `renderedView()` (was byte-for-byte duplicated in `ListWidgetConfigureFlowTest`/`WidgetActionsFlowTest`/`WidgetChromeNavigationTest`, inlined a fourth way in `SingleCaseWidgetConfigureFlowTest`), `collectText()` (two incompatible signatures existed — a mutate-into-list variant and a list-returning variant; standardized on the list-returning one), `findClickableAncestorOfText()` (identical in `WidgetActionsFlowTest`/`WidgetChromeNavigationTest` — one copy carried a comment explicitly defending the duplication, since removed), and `bindAndRenderSingleCaseWidget()` (identical in the same two files; changed to return the allocated `appWidgetId` instead of mutating a field, since the shared version can't reach into each test's private state).
  - `NotificationTestFixtures.kt` (new — no shared fixture file existed yet for this package): `grantPostNotificationsPermission()`, deduped out of `NotificationActionReceiverTest`/`NotifierContentTest` as a `Context` extension.
- **Fragile-assertion tests, fixed (12):** added an assertion of the setup action's own effect before the next action runs, following the idiom already used elsewhere in these files (e.g. `BigPictureScreenTest`'s `filterLegend_showsNoCasesSelectedNote_afterDeselectingOnlyCase`, and each screen's own "confirm" dialog test asserting the dialog title before proceeding) — `BigPictureScreenTest` (`deselectingACase_resetsStaleTagSelection_insteadOfEmptyingTheGrid`, `bulkToggle_selectAll_reselectsEveryTag`, `tagFilterChip_deselecting_hidesEventsOfOtherTags`, `tagFilterChip_deselectingAllTags_showsUntaggedOnly`, `tagsDialog_onlyOffersTagsFromSelectedCases`), `SettingsScreenTest` (`deleteAllData_cancelDoesNotInvokeCallback`, `importButton_cancelDoesNotInvokeCallback`), `TriggersScreenTest` (`delete_cancelDoesNotInvokeCallback`, `create_switchToSilentFor_savesWithNullWindow`), `CaseEditScreenTest` (`archiveIcon_cancelDoesNotInvokeCallback`), `ArchivedCasesScreenTest` (`delete_cancelDoesNotInvokeCallback`), `CaseDetailScreenTest` (`stopNowInSheet_thenSave_savesWithAnEndedAt`).
- Verified via `connectedDebugAndroidTest` on a real emulator: all 199 instrumented tests pass (individually confirmed for every touched class, then the full suite).

**Deferred:**
- `BigPictureScreenTest`'s two event-row-tap tests (`dayDetailDialog_eventRowTap_...`, `weekDetailDialog_eventRowTap_...`) — same two-action/one-assertion shape, but their second click targets text that only exists once the first dialog is open, so a missed setup click throws immediately rather than passing silently. Not an instance of the risk this pass targets.
- `SharePreviewScreenTest` — swept per the doc's instruction, no matches; every test there performs at most one action before asserting.

**Docs updated:** PROGRESS.md — struck the instrumented-suite-hygiene item from the Testing section.

---

## fix/dialog-spacing-icon-sharecard-sizing

**Scope:** Three PROGRESS.md items bundled into one branch per request, as three commits: Big Picture filter dialog spacing, the launcher icon's handle/ring dark-spot bug, and the share card's sparse-content sizing bug (plus real fallout that grew past the original scope).

**Found & fixed:**
- **Commit 1 (dialog spacing):** `InfoDialog.kt` gained an optional `leadingAction` slot on its button row; `BigPictureGrid.kt`'s Cases/Tags dialogs moved `BulkSelectionToggle` into that slot, next to Dismiss. Verified live via `connectedDebugAndroidTest` (all 24 `BigPictureScreenTest` tests pass) rather than relying on the text-based lookup pattern alone.
- **Commit 2 (icon):** the diagnosed root cause — the handle's round line-cap extends backward past its own path coordinate, into the ring's stroke band — took real visual iteration to fix correctly. Three geometry-only attempts (coordinate nudge, full opacity, ring-gap cutout) each surfaced a new visible defect (residual overlap, a translucency mismatch, uncovered notches) only visible by actually rendering the result. This machine has no SVG rasterizer installed (`magick`/`inkscape`/`rsvg-convert`/`cairosvg` all absent); headless Chrome (`--headless --screenshot` against a local HTML wrapper) renders local SVG/HTML with zero install, and using it finally let the geometry get iterated to a working result (flat-capped handle meeting the ring's edge exactly, a shared diagonal gradient replacing the old alpha-blended overlap). Also regenerated the two stale PNG exports (`hodith_icon_512.png`, `feature_graphic_1024x500.png`) the same way, and propagated the final geometry to all four consuming files (two in-repo vector drawables, two external source SVGs living outside the repo).
- **Commit 3 (share card sizing):** root cause was `weight(1f)` resolving its share of space before the parent Column's `heightIn(min=...)`-only-bounded height settled, which could allocate less than rich content needed — the card's own `clip()` then cropped the difference. The investigation surfaced three more real, connected findings the original PROGRESS.md item didn't anticipate: (1) the "Reality" beat's kicker label only makes sense in contrast to a Hunch, so it now renders without one when there's no comparison; (2) the Share Preview screen's reported bottom-cutoff traced to the same clip mechanism, confirmed fixed by the same change; (3) Square's full section-picker customizability conflicts with keeping a 1:1 floor once every section is selected — confirmed genuinely user-reachable (`selectedSections` applies the same way to both formats) — deferred as a new PROGRESS.md item (`feat/square-share-card-preset`) rather than folded into this branch, per direction. Fixed the Story/Square asymmetry (Story sizes freely to content, Square keeps its floor) via `Arrangement.SpaceBetween` replacing the `weight`+`Box` pattern. Rewrote `ShareCardTemplateTest.kt`'s single height-comparison test — whose premise inverted under the new design — into four tests covering the floor, no-clip growth, the format asymmetry, and a footer-gap-consistency regression signal; verified all against a real emulator, twice (once before and once after a follow-up cleanup pass).
- **Cleanup pass itself found:** `TESTING.md` and `HODITH_SPEC.md` §17 both still described the old (replaced) test behavior and the now-resolved sizing blocker; one test carried a redundant second `@Smoke` tag (trimmed to one class-representative test per the checklist's own guidance); a handful of negatively-framed comments/test names in this branch's own new code, corrected per feedback mid-session.

**Deferred:**
- Square share-card preset redesign — new PROGRESS.md item, `feat/square-share-card-preset`, needs a product decision (which sections, what order) before implementation.
- Re-exporting the two icon PNGs again if the geometry changes further — regenerated once this pass via the headless-Chrome technique above; that's a technique to reuse, not a standing tool.

**Docs updated:** PROGRESS.md (recommended order, Share section replaced, Big Picture item struck as done), HODITH_SPEC.md (§13 sizing/footer copy, §17 cross-references), TESTING.md (Share preview row), DEV_PLAYBOOK.md (post-launch Play Store link item).

---

## chore/qa-audit-mutation-checks

**Scope:** PROGRESS.md's last outstanding QA-audit item — [QA_AUDIT_RULES.md](QA_AUDIT_RULES.md) §2's mutation spot checks, the one audit section that couldn't run read-only. Fixed the item's own known target (a duplicated rolling-window formula) on this branch per its own instruction, then ran one mutation at a time against the eight pre-selected sample files, reverting each before the next. Per this pass's own direction, findings beyond the known target were fixed inline on this branch rather than deferred to a new one, and results are recorded here rather than in QA_AUDIT_BACKLOG.md — that file already defers this item's findings to PROGRESS.md/CLEANUP_LOG.md and stays untouched.

**Found & fixed:**
- **Known target, fixed:** `TriggerEngine.evaluateAtLeast` and `NotificationEvaluator.evaluateTriggers` independently recomputed the same rolling-window-start formula (`now - windowDays * MILLIS_PER_DAY`) — one to *count* events, the other to *fetch* them. Extracted a shared `atLeastWindowStart(now, windowDays)` in `TriggerEngine.kt`; both call sites now use it. Confirmed the fix actually closes the gap the audit flagged: mutating the shared helper broke both `TriggerEngineTest` and `NotificationEvaluatorTest` together (previously a mutation to either standalone copy could in principle be masked by the other's independent correctness).
- **Mutation check results** (one mutation per file, `./gradlew test --tests` / `connectedDebugAndroidTest` per file, reverted before the next):

  | File | Mutation | Result |
  |---|---|---|
  | `VerdictEngineTest` | `confidenceTierFor`'s Preliminary-tier `>=` → `>` | Caught |
  | `TriggerEngineTest` | shared `atLeastWindowStart`'s `now - ...` → `now + ...` | Caught |
  | `CheckInTest` | `evaluateCheckIn`'s `silentDays >= effectiveDays` → `>` | Caught |
  | `StatsEngineTest` | `pickFrequencyGranularity`'s `<=` → `<` | Caught |
  | `InsightsEngineTest` | `computeGapStats`'s `isCurrentGapLongest = currentGapDays >= longestPastGap` → `>` | **Missed** — fixed |
  | `NotificationEvaluatorTest` | shared `atLeastWindowStart`'s `now - ...` → `now + ...` (verifies both call sites now move together) | Caught |
  | `LogDetailViewModelTest` | `computeEndedAt`'s MANUAL-mode `it > 0` → `it >= 0` | Caught |
  | `EventDaoTest` (instrumented) | `eventsInWindow`'s `@Query` upper bound `<` → `<=` | **Missed** — fixed |

- **Miss 1 — `InsightsEngineTest`:** the two existing `isCurrentGapLongest` tests only cover "current gap clearly longer" (11 vs. 2) and "current gap clearly shorter" (3 vs. 20); neither exercises the exact tie (`currentGapDays == longestPastGap`), which is the only input the `>=`/`>` mutation actually changes. Added `computeGapStats flags the current gap as longest when it exactly ties the biggest past gap` (events with two equal 5-day gaps, current gap also 5 days) — confirmed it fails against the mutation and passes against the real `>=` implementation. Separately noted, not acted on: `isCurrentGapLongest` isn't read by any ViewModel/UI code today (`GapStats` is otherwise fully consumed by `InsightsTabState.kt`) — flagging in case it's meant to back a future "your longest gap yet" callout rather than being genuinely dead.
- **Miss 2 — `EventDaoTest`:** `eventsInWindow_excludesEventsOutsideRange` places events well inside (150) and well outside (50, 250) a `[100, 200)` window, never exactly at either edge, so it can't distinguish `<` from `<=` at `windowEnd`. Added `eventsInWindow_includesAnEventAtExactlyWindowStart` and `eventsInWindow_excludesAnEventAtExactlyWindowEnd` (each with a single event pinned to one boundary) — confirmed the window-end test fails against the mutation and both pass against the real query. Ran the full class via `connectedDebugAndroidTest` on a connected emulator (13/13 pass) after reverting, not just the transient per-mutation runs.

**Deferred:** nothing — both misses were fixed inline per this pass's own instruction rather than written up as follow-up branches.

**Docs updated:** PROGRESS.md — removed the completed mutation-spot-checks item from the Testing section (last remaining piece of the QA audit sequence). No TESTING.md change: no new test classes, no counts tracked there for these files, and the two new tests sit under coverage the table already describes qualitatively (Trigger/Stats boundary values, Room DAO window queries).

---

## test/voice-completeness-by-reflection

**Scope:** PROGRESS.md's Voice-completeness item — `VoiceTest`'s hand-written non-blank list missed 68 of 291 keys (QA audit finding) and had no cross-voice uniqueness check at all. Replaced the manual list with reflection over the `Voice` interface (properties and parameterised `fun` keys both, enum params covered exhaustively via `enumConstants`, non-enum params via a small sample registry), and added the new uniqueness check PROGRESS.md's own Tests note asked for. Required as the gate immediately before the (still-open) Voice phrasing review.

**Found & fixed:**
- Added `kotlin-reflect` as a test dependency (`gradle/libs.versions.toml`, `app/build.gradle.kts`) — not previously on the classpath.
- Rewrote `VoiceTest.kt`'s completeness test via reflection; added `no per-voice key returns an identical string across all three voices`, which collects and reports every violation at once rather than failing at the first (found this was more useful than fail-fast partway through triaging the findings below).
- The new uniqueness check surfaced 45 real violations on first run, exactly as PROGRESS.md's Tests note anticipated:
  - 41 properties + 2 functions (`bigPictureFilterCount`, `widgetTodayCount`) were declared as per-voice keys but every implementation was word-for-word identical structural chrome (nav/tab labels, field labels, theme option names, check-in/trigger-builder chips). Converted to interface `get()`/default-body implementations and removed the redundant per-voice overrides — matches the existing precedent (`insightsSectionLabelHeatmap` and similar). No visible copy change.
  - 2 genuine content gaps, fixed with the user's direction: `caseNameHint` (was "e.g. Kiddo was rude" verbatim in all three voices — Intense now "e.g. The migraine returns", Bright now "e.g. Perfect coffee!", both reusing HODITH_SPEC.md's own existing Case-name example vocabulary) and `hunchDirectionPillLabel` (Bright's three branches were a wholesale copy of Plain's; now "So much" / "Not much" / "Just wondering").
- Ran the full instrumented suite (`connectedDebugAndroidTest`, 194 tests) to confirm the content changes didn't break anything relying on literal Voice text — initial run showed 7 failures, all in the `widget` package on `bindAppWidgetIdIfAllowed`. Traced this to my own process error, not a real gap: ran `adb shell appwidget grantbind` before the app was actually installed on the emulator, which DEV_PLAYBOOK.md §"AppWidgetHost instrumented tests" already documents as a silent no-op. Reinstalled (`installDebug installDebugAndroidTest`), re-granted, reran — 194/194 pass.
- `ktlintFormat` (needed to clear "needless blank line" violations left by the property removals) rewrote `Voice.kt` with LF line endings, breaking from the CRLF the rest of `src/main` uses — restored CRLF byte-for-byte after formatting, confirmed via `git diff --stat` that only the intended content lines changed.
- PROGRESS.md: removed the completed item and its "Recommended order" entry; corrected the still-open Voice-phrasing-review item's now-stale numbers (1945→1819 line count; ~292×3≈876 strings → 294 keys total, only 213 declared per-voice, ~720 strings) since this branch changed the facts that item's estimate was built on.

**Deferred:** nothing deferred — both real findings (structural collapse, content gaps) were fixed on this branch rather than punted to the phrasing review, since PROGRESS.md's own ordering note says this branch exists specifically so the phrasing review starts from a fully-asserted baseline.

**Docs updated:** TESTING.md (Voice row now describes both checks). PROGRESS.md (as above). HODITH_SPEC.md's Case-name example vocabulary ("Kiddo was rude" / "Migraine" / "Perfect coffee") already covers the new `caseNameHint` choices — no update needed there.

---

## chore/voice-close-action-copy

**Scope:** PROGRESS.md's close-action copy item — shorten `bigPictureDialogCloseAction` to
"Close" in all three voices. Investigating first (per the item's own note) showed that once all
three voices say the same thing the key is voice-invariant and exists only to override
`InfoDialog`'s shared `dismissLabel` default (`infoDialogDismissAction`). Confirmed with the user:
delete the key entirely rather than shorten it, so Big Picture's five dialogs read "Got it" /
"Understood" / "Got it!" like every other `InfoDialog` in the app, instead of carrying a duplicate
key by accident. Also folded in a separate, already-decided piece of doc cleanup: the Case Detail
tab-order item was abandoned, so it's removed from PROGRESS.md rather than left open.

**Found & fixed:**
- Removed `bigPictureDialogCloseAction` from the `Voice` interface and all three implementations
  (`Voice.kt`).
- Removed the `dismissLabel = voice.bigPictureDialogCloseAction` override at all five
  `InfoDialog` call sites in `BigPictureGrid.kt` (Cases filter, Tags filter, month picker,
  day-detail, week-detail); they now fall through to the shared default, matching
  `SectionWithInfo.kt`'s existing usage.
- Swapped all 13 `PlainVoice.bigPictureDialogCloseAction` references in `BigPictureScreenTest.kt`
  to `PlainVoice.infoDialogDismissAction`. No `VoiceTest.kt` change was needed — the key was never
  in its hand-maintained completeness list (it was one of the QA audit's 69 uncovered keys).
- PROGRESS.md: removed the abandoned Case Detail tab-order item and its `## Case Detail` section;
  removed the now-completed close-action-copy item; updated the "Recommended order" list; fixed a
  stale "alongside Close" reference in the still-open dialog-spacing item to say "alongside the
  dismiss button"; corrected the Voice-completeness item's key counts (292→291, 69→68 uncovered)
  and dropped its now-stale mention of `bigPictureDialogCloseAction`.

**Deferred:** nothing deferred.

**Docs updated:** PROGRESS.md (as above). `HODITH_SPEC.md` and `TESTING.md` don't reference this
key or the tab-order item, so nothing to correct there.

---

## refactor/extract-ui-input-logic

**Scope:** PROGRESS.md's Shared UI logic item — the QA audit's §5 finding that four pure
transformations lived inline in composables, untested, and reimplemented with different rules in
different places. Followed the `BigPictureFilterState.kt`/`AcronymText.kt` precedent: one plain
Kotlin function per transformation, filed next to its screen, with a direct unit test. Two open
product questions were resolved with the user first: the digit cap (5 digits) and whether tag
matching becomes case-insensitive (yes, to match Case Edit's existing dupe check).

**Found & fixed:**
- The digit filter carried a live bug: `LogDetailSheet`'s duration field filtered digits with no
  cap, so `LogDetailViewModel.computeEndedAt`'s `toIntOrNull()` could overflow to `null` on long
  input, silently saving the event with no duration and no error shown. Extracted
  `ui/common/DigitInput.kt`'s `filterDigitInput(value, maxDigits)`; `LogDetailSheet` now caps at 5
  digits, `TriggersScreen`'s custom-window field keeps its existing 3-digit cap via the same
  function. `MILLIS_PER_MINUTE` is a `Long` constant, so the multiplication that follows can't
  overflow at 5 digits — confirmed rather than assumed.
- Tag-name normalization was duplicated three ways with inconsistent casing rules (case-sensitive
  in `LogDetailSheet`'s `onAddTag` and `TagDao.getByName`'s exact-match query; already
  case-insensitive in Case Edit's duplicate check). Made matching case-insensitive throughout:
  `TagDao.getByName` now uses `COLLATE NOCASE`, `LogDetailViewModel.tagDiff` compares on
  lowercased names, and the sheet's add logic moved into a new pure `ui/logsheet/TagInput.kt`
  (`tagToAdd`). "Coffee" typed against an existing "coffee" now reuses the existing tag instead of
  creating a near-duplicate. The tags table's unique index stays case-sensitive at the schema
  level — deliberately left as-is, since the now-case-insensitive `getByName` lookup is what
  actually prevents the duplicate insert, and touching the index would mean a migration for no
  behavioral gain.
- Tag suggestion filtering (`TagEditor`'s inline `remainingSuggestions`) was pure logic reachable
  only through instrumented tests. Extracted to `ui/logsheet/TagInput.kt`'s
  `filterTagSuggestions`, made case-insensitive to match the rest of this pass, with a direct JVM
  test.
- Future-day/week trimming was three separate inline expressions in `BigPictureGrid.kt` (week
  filter, day-cell gate, week-dialog's `validDays`) duplicating a rule `InsightsTabState` already
  had as a tested plain function. Extracted `isPastOrToday` into `BigPictureFilterState.kt`
  (already the file split out of `BigPictureGrid` for exactly this reason) and pointed all three
  call sites at it.

**Deferred:**
- Nothing deferred — all four extractions from the PROGRESS.md item landed on this branch.

**Docs updated:** none required. `HODITH_SPEC.md` doesn't document tag-matching casing at its
level of detail, so there's nothing there to correct. `TESTING.md` doesn't track per-file coverage
counts, so no tally to update. PROGRESS.md's Shared UI logic item is struck below.

---

## feat/cloud-backup-toggle

**Scope:** PROGRESS.md's *Correct the auto-backup disclosure* and *Add a Settings toggle for
Android's OS-level cloud backup* items, combined at the user's request into one branch (two
commits) rather than the disclosure-only branch originally planned — the toggle resolves the
disclosure fix's biggest weakness: without it, the corrected copy could only say backup *might* be
happening, not let the user stop it. Rewrote `aboutPrivacyBody` in all three voices plus README and
HODITH_SPEC §16/§4 to stop claiming an absolute "everything stays on the phone," since
`allowBackup="true"` and an unrestricted `data_extraction_rules.xml` mean Android's own device
backup can carry the Room DB to the user's Google account. Added a Settings toggle (default on,
opt-out) enforced by a new `HodithBackupAgent.onFullBackup` override, following a locally reviewed
reference implementation (EarnIt, a sibling project) directly.

**Found & fixed:**
- Checklist's Tests section caught a real gap: the disclosure-copy fix had no regression guard.
  Added `AboutScreenTest.privacyBody_doesNotClaimEverythingStaysOnThePhone`, asserting on a literal
  substring rather than the `Voice` constant itself, so a future revert of the copy actually fails
  the test instead of trivially passing against whatever the string currently says (mirrors EarnIt's
  own regression test for the same bug).
- Checklist's Duplication/Complexity questions caught `CaseEditScreen.kt`'s private `ToggleRow`/
  `caseEditSwitchColors` as exactly the kind of copy-paste-with-variation the `BigPictureFilterState`/
  `AcronymText` precedent exists to avoid, now that Settings needed the identical themed-switch shape.
  Promoted both to `ui/common/ThemedToggle.kt` (renamed `caseEditSwitchColors` → `themedSwitchColors`,
  no longer case-specific); `CaseEditScreen.kt` now imports the shared versions instead, and three
  now-unused imports (`SwitchColors`, `SwitchDefaults`, `Color`) came out with the private functions.
- Checklist's Tests section, and `VoiceTest`'s own docstring ("every voice has a non-blank string for
  every key"), didn't hold: `aboutPrivacyLabel`, `aboutPrivacyBody`, `aboutPrivacyPolicyLinkLabel`,
  `aboutIdeaLabel`, and `aboutIdeaBody` were entirely absent from the hand-written non-blank list —
  the QA audit's "69 uncovered keys" finding, confirmed directly against this file rather than taken
  on faith. Added all five (plus the three new cloud-backup keys) rather than only the minimum the
  disclosure fix touched, since the gap was fully visible in the block already being edited.
- TESTING.md's Compose UI — Settings row hadn't been touched to mention the new toggle; updated in
  place rather than left silently stale next to the new coverage.
- HODITH_SPEC §16's own claim ("documented on the About screen") was the specific unintentional
  divergence PROGRESS.md flagged — now true rather than aspirational, since the About screen actually
  says it.

**Deferred:**
- The hosted privacy policy (linked from `AboutScreen.kt`) and the Play data-safety form both live
  outside this repo and can't be edited from here. Replaced the two closed PROGRESS.md items with a
  new, smaller one (*Audit the hosted privacy policy and Play data-safety form*) rather than letting
  the follow-up go undocumented.
- `data_extraction_rules.xml` stays unrestricted rather than adding EarnIt-style explicit `<include>`
  scoping — optional hardening the toggle doesn't depend on, not required for this branch.
- Instrumented tests (`AboutScreenTest`, `SettingsScreenTest`, `SettingsViewModelTest`'s JVM
  counterparts already ran) were not run on a device — no emulator available in this session.
  `compileDebugAndroidTestKotlin` confirms they compile; running them on a device before merge is
  still outstanding, consistent with CLAUDE.md's "no manual UI verification" boundary for this
  assistant.

**Held up under scrutiny:** confirmed HODITH's "Delete all data" (`RoomHodithRepository.deleteAllData()`)
only touches Room, never `DataStoreSettingsRepository` — so EarnIt's "wipe silently re-enables backup"
edge case (their `resetForWipeEverything()` preserving the choice across a settings-wide clear)
doesn't apply here; no equivalent guard needed. Repo hygiene clean: `git status` shows only the
intended files (two new: `backup/HodithBackupAgent.kt`, `ui/common/ThemedToggle.kt`), no secrets, no
local paths, no stray untracked files.

**Didn't apply:** Hardcoded Values (no new colors/magic numbers — the new DataStore key string
follows the existing `booleanPreferencesKey` naming pattern), Accessibility (no new icon-only
buttons; the new `Switch` matches the existing check-in toggle's touch target), Deprecated APIs (no
new warnings; `BackupAgent.onFullBackup` is the current, non-deprecated full-backup override).

**Docs updated:** README.md (data-stays-yours bullet, "no cloud" softened), HODITH_SPEC.md (§4
principle 7, §14 Settings/About rows, §16), DEV_PLAYBOOK.md (new §6 Testing Cloud Backup, Tooling
Reference renumbered to §7), MANUAL_TEST_PLAN.md (Data & backup section, three new items),
TESTING.md (Compose UI — Settings row), PROGRESS.md (both Settings items resolved and removed,
replaced with the smaller external-audit follow-up, *Recommended order* renumbered).

---

## feat/data-migration-and-backup-tolerance

**Scope:** PROGRESS.md's *Data & migrations* item — the decision session it called for, settled and
implemented rather than left as a decision doc. Removed `fallbackToDestructiveMigration` now that
the schema is frozen at v6 (no user holds v1-5 data, so no retroactive migration chain is needed),
with a guard test that fails a future schema bump lacking a matching `Migration`. Made backup import
version-tolerant: rejects only newer files, and folds older ones through a (currently empty)
upgrade-chain mechanism instead of an exact-version match. Four commits.

**Found & fixed:**
- Checklist's Hardcoded Values/Duplication questions caught the frozen schema version (`6`)
  declared as a private `const` in two separate test files (`SchemaMigrationCoverageTest`,
  `DatabaseFreshInstallTest`) instead of one source of truth. Room's `@Database` annotation can't be
  read back via reflection (`AnnotationRetention.BINARY`), so introduced a top-level
  `HODITH_DATABASE_VERSION` const in `HodithDatabase.kt` that both the annotation and both tests
  reference instead.
- Getting `MigrationTestHelper` running at all needed two build-config fixes, neither anticipated by
  the plan: `app/schemas/*.json` wasn't wired into the `androidTest` assets (no instrumented
  migration test had ever needed it before), and Room 2.8.4's migration-bundle parser needs
  `kotlinx-serialization-core` 1.8.1+, but the pinned Compose BOM's constraint set strictly holds it
  to 1.7.3 — threw `AbstractMethodError` at test runtime, not compile time. Forced 1.8.1 scoped to
  only the `androidTest` configurations (no production code uses `kotlinx.serialization`); logged as
  a Gotcha in DEV_PLAYBOOK §6 so the next Compose BOM/Room bump doesn't rediscover it blind.
- Checklist's Tests section, prompted by a direct question about e2e coverage: unit tests proved the
  new version-detection logic against hand-written JSON strings, and a pre-existing instrumented
  test proved real-database restore against raw `BackupData` objects, but nothing proved both
  together. Added `BackupImportIntegrationTest` — real exported JSON, through
  `BackupSerializer`'s version-tolerant parsing, into a real database.
- `DatabaseFreshInstallTest`'s one test is the class's representative happy path; tagged `@Smoke` to
  match the `CaseDaoTest`/`RoomHodithRepositoryBackupTest` precedent.
- HODITH_SPEC §17's shared-cost note assumed the pre-release destructive-migration fallback this
  branch removed — an intentional divergence, so the spec moved (CLAUDE.md rule) to state the guard
  test that replaces it.
- DEV_PLAYBOOK's ship checklist had an item pointing at PROGRESS.md's *Data & migrations* section,
  now resolved; struck per CLAUDE.md ("the checklist only contains open work") rather than left
  dangling at a section that no longer exists.
- PROGRESS.md's *Data & migrations* section removed entirely (outstanding-work-only rule) and
  *Recommended order* renumbered.

**Deferred:** nothing — small enough scope that no code-level finding needed pushing off. (The
upgrade-chain mechanism itself stays deliberately empty: no backup schema version older than the
current one has ever shipped, so there is nothing to write an upgrade step *for* yet — a stated
design constraint in the code comments, not a deferral.)

**Held up under scrutiny:** `DemoDataSeeder` confirmed unaffected (inserts via `HodithRepository`,
never touches `schemaVersion` or raw backup JSON) — checked before assuming no seed-data update was
needed, not just asserted. Repo hygiene clean: no secrets, no local paths, no stray untracked files.

**Didn't apply:** Duplication (Voice/composables/styling — no UI touched), Decoupling, Complexity &
Pattern Health, Accessibility — no Compose code in this diff.

**Docs updated:** HODITH_SPEC §17 (shared-cost note), TESTING.md (Export/import row, new Room
migrations row, new Backup import full-chain row), DEV_PLAYBOOK §2 (ship-checklist item struck) and
§6 (new Gotcha), PROGRESS.md (*Data & migrations* section removed, *Recommended order* renumbered).

---

## docs/future-work-triage (QA audit pass)

**Scope:** First run of [QA_AUDIT_RULES.md](QA_AUDIT_RULES.md)'s whole-suite test-quality audit,
sections 1 and 3–7 (section 2's mutation spot checks need a working tree and stayed outstanding).
Findings were written into PROGRESS.md in its own item format rather than into QA_AUDIT_BACKLOG.md,
on the product owner's call, so the outstanding-work roadmap lives in one file. Grouped into four
branches rather than one per finding. Docs only — nothing under `app/` changed.

**Found & fixed:**
- TESTING.md claimed a Voice test that has never existed — "no key returns an identical string
  across all three (catches copy-paste)". `VoiceTest` has two tests: the non-blank list and the
  `sharePunchline` pronoun rule. Claim removed from the doc; building the check is now part of
  `test/voice-completeness-by-reflection`, which is where the doc and the suite reconverge.
- TESTING.md described the non-`@UiTest` CI shard as "DAO tests". It is a full partition on one
  annotation, so it also carries every notification and widget instrumented class — nine of the
  seventeen classes in it. Restated.
- Two real behaviours had no spec anchor, both correct and both tested, so per CLAUDE.md the spec
  moved rather than the code: §11 now says `AT_LEAST` requires a `windowDays`, and that `SILENT_FOR`
  counts from case creation for a Case with no events yet.
- QA_AUDIT_BACKLOG.md still said no audit had run and offered itself as the place to populate.
  Rewritten to point at PROGRESS.md, so a future session doesn't look in the wrong file.
- Checklist "no self-updating tallies" caught a test count used as a sampling rationale in the new
  mutation-check item; replaced with the durable fact (largest ViewModel suite) that motivated it.
- Checklist "living docs shouldn't narrate what changed" caught the new Voice item, which explained
  what TESTING.md used to claim. Rewritten to state only what is true now.
- Checklist Spec Review pointed at "Update §14" for Future Work; §14 is Screens and Future Work is
  §17. Corrected in CLEANUP_CHECKLIST.md itself.

**Deferred:**
- Section 2 (mutation spot checks) — needs source edits plus a sequential `./gradlew test` per
  sampled file, which doesn't belong on a docs branch. Tracked as `chore/qa-audit-mutation-checks`
  with the sample pre-selected and the duplicated `AT_LEAST` window formula named as its first
  target, so it can be picked up without re-deriving.
- Every code-level finding — the 69 uncovered Voice keys, the copy-pasted widget/notification test
  helpers, the two BigPicture tests that pass on a missed setup click, and the four inline UI
  transformations (including the uncapped duration field, which is a live input bug) — left as
  proposed branches per QA_AUDIT_RULES §8 rather than fixed here.

**Held up under scrutiny** (recorded because a clean result is a result): no orphan test classes —
every unit class sits under `app/src/test/`, package matches directory throughout, no base classes,
and the instrumented shard split is a true partition. `VerdictEngine`'s tier thresholds and all four
comparison-band cutoffs match spec §8 including the "each cutoff belongs to the higher band" rule,
with each boundary pinned by a named test; `CheckIn`'s clamp and both Trigger kinds' arm/re-arm match
§11. The DAO tier shares `TestFixtures.kt` properly — the helper duplication is confined to the
widget and notification classes.

**Didn't apply:** Duplication, Decoupling, Complexity & Pattern Health, Naming Consistency,
Hardcoded Values, Accessibility, Deprecated APIs — no code in the diff. Repo hygiene checked and
clean: no secrets, no local paths, no new untracked files.

**Docs updated:** PROGRESS.md (new *Shared UI logic* section; Testing section rewritten — the audit
item narrowed to its outstanding section, two new items, the Voice and Insights items strengthened
with measured findings; *Recommended order* resequenced), TESTING.md (Voice coverage row, CI shard
description), HODITH_SPEC §11 (both Trigger bullets), QA_AUDIT_BACKLOG.md (shell now points at
PROGRESS.md), CLEANUP_CHECKLIST.md (Future Work section reference).

---

## docs/future-work-triage

**Scope:** Audit of HODITH_SPEC §17's twelve deferred items against the code — which are already
built, which rest on a premise that no longer holds, and what each would cost to pick up. Each
surviving item gained a Status/Effort/Touches/Lean trailer, the costs common to nearly all of them
moved into a preamble, two items were dropped outright on the product owner's call, and the two
findings that are pre-release work rather than backlog moved to PROGRESS.md. Docs only — nothing
under `app/` changed.

**Found & fixed:**
- Three items' descriptions no longer matched the code. "Tag-level insights" claimed work that had
  shipped (§10's tag breakdown, §9's tag filter); retitled to "Tag-scoped verdicts & triggers", the
  part that's actually open. The charting-library item's trip-wire named zoom, which §9 retired by
  design; narrowed to the one surviving trigger. "Confirmed-quiet checkpoints" read as though the
  data model were ready — `CaseEntity.lastCheckInAt` exists but keeps a single overwritten re-arm
  anchor that `VerdictEngine` never reads; the trailer now says so.
- The §17 heading claimed "data model stays ready", which is false for the three items needing a new
  entity or column. Dropped from the heading.
- Two concerns had no home: the unresolved Room migration policy (`fallbackToDestructiveMigration`
  at schema v6, whose own code comment defers to a ship checklist that didn't list it) and backup
  import's strict `schemaVersion` equality, which orphans existing exports at the first bump. Both
  are pre-release decisions, so they went to PROGRESS.md's new *Data & migrations* section with a
  gate line in DEV_PLAYBOOK §2 pointing at it rather than a second copy.
- Checklist "living docs shouldn't narrate what changed" caught the first draft of the
  charting-library entry, which explained what its old trigger used to say; rewritten to state only
  the current trigger. Same pass trimmed two trailers that restated their own item's prose.
- Checklist "no self-updating tallies" caught `app/schemas/1.json`–`6.json` in the new PROGRESS.md
  entry — a list that grows on every schema bump. Replaced with the durable `app/schemas/*.json`.

**Deferred:**
- The Hunch/Trigger overlap stays parked until alpha testing, as its entry already says; only a
  trailer was added, its body is untouched.
- Theme/voice mixing and the Wear OS tile were removed from §17 entirely — the product owner's
  decision, taken after reading the triage, not the triage acting on its own leans.
- Nothing else in §17 was implemented or acted on, retitling aside; the remaining leans are input to
  a decision, not the decision.

**Didn't apply:** Duplication, Decoupling, Complexity & Pattern Health, Naming Consistency — no code
in the diff. Repo hygiene checked and clean: no secrets, no local paths, no new untracked files.

**Docs updated:** HODITH_SPEC §17 (rewritten), PROGRESS.md (new *Data & migrations* section; Share
entry cross-referenced), DEV_PLAYBOOK §2 (before-first-release gate line).

---

## feature/about-contact-content

**Scope:** PROGRESS.md's "About screen real content" and "Contact Us" placeholder items — unblocked
by a hosted privacy policy URL and a contact email address the user supplied. Added an app
idea/description section (sourced from HODITH_SPEC.md §1, not the marketing landing page) as the
About screen's first row; a real Licenses body naming the Apache License 2.0 (all runtime deps share
that one license, so no per-library generated screen was needed); a "read the full policy" link that
opens the real hosted privacy policy in the browser; and wired Settings' Contact Us row to a real
`mailto:` intent instead of a "coming soon" snackbar. Rate the App stays a placeholder — no Play
Store listing yet.

**Found & fixed:**
- No existing pattern for opening an external app from within the app (`ACTION_VIEW`/`ACTION_SENDTO`)
  — the closest precedent was `NotificationsDeniedBanner`'s `LocalContext` + `Intent` +
  `context.startActivity` shape for opening system Settings. Reused that shape rather than inventing
  a new abstraction for two call sites.
- New `Voice` keys (`aboutIdeaLabel`/`aboutIdeaBody`, `aboutPrivacyPolicyLinkLabel`) added to all
  three voice implementations in this same commit, and the placeholder `aboutLicensesBody` replaced
  with real copy in all three, per the Voice layer rule.
- New/updated instrumented tests ran clean on an emulator (API 36): 5/5 `AboutScreenTest`, 19/19
  `SettingsScreenTest`.

**Deferred:**
- Whether the actual `Intent` resolves to a browser/email app (rather than just the callback firing)
  isn't unit-testable here — no Espresso-Intents dependency in the repo, and adding one for two link
  taps felt like more tooling than the feature warranted. Added to MANUAL_TEST_PLAN.md's new "About &
  Contact" section instead.

**Docs updated:** PROGRESS.md (struck both resolved items, left Rate the App as the sole open Settings
item), HODITH_SPEC.md §14 (About/Settings rows now describe the idea section, privacy policy link,
and wired Contact Us), TESTING.md (Compose UI and Compose UI — About rows reflect the new content and
callbacks), MANUAL_TEST_PLAN.md (new "About & Contact" section for the two external-intent flows).

---

## chore/build-warning-cleanup

**Scope:** PROGRESS.md's deferred item on two cosmetic build-time warnings — investigated both
against upstream issue trackers instead of re-guessing, fixed the one that was fixable, and
verified both outcomes with real (non-cached, `--rerun-tasks`) Gradle output rather than assuming.

**Found & fixed:**
- *K2 `@ApplicationContext` "applied to value parameter only" forward-compat notice (KT-73255)* —
  all four `@Inject constructor(@ApplicationContext private val context: Context)` sites
  (`Notifier.kt`, `WidgetRefresher.kt`, `ComposeShareImageExporter.kt`,
  `ContentResolverBackupFileWriter.kt`) declare the parameter as `val`, so it's simultaneously a
  value parameter and a property-backing declaration — the exact ambiguity K2 warns about. The two
  `@Provides` module functions (`DatabaseModule.kt`, `DataStoreModule.kt`) use a plain
  `context: Context` parameter with no `val` and never warned, confirming the diagnosis. Fixed by
  adding the compiler's own recommended explicit use-site target — `@param:ApplicationContext` —
  on all four, which keeps the parameter-only semantics Hilt actually needs and removes the
  ambiguity. Confirmed gone from `compileDebugKotlin --rerun-tasks` output after the change.

**Deferred:**
- *Moshi kapt-codegen deprecation notice from `hiltJavaCompileDebug`* — root-caused this time
  instead of leaving it speculative: confirmed via repo-wide search that no `kapt` plugin or
  `kapt(...)` dependency exists anywhere in the project, and Moshi's codegen is wired exclusively
  through `ksp(libs.moshi.kotlin.codegen)`. The warning is a known, still-open upstream Dagger/Hilt
  bug ([google/dagger#4116](https://github.com/google/dagger/issues/4116), filed 2023): Hilt's
  `hiltJavaCompile` task aggregates all KSP/kapt annotation-processor jars — including Moshi's —
  onto the javac processor path it builds for its own codegen step, so Moshi's processor gets
  invoked via javac too and unconditionally prints its kapt-deprecation notice regardless of the
  fact only KSP is used ([square/moshi#1779](https://github.com/square/moshi/issues/1779)
  confirms Moshi's processor does this independent of invocation mechanism). Reconfirmed present
  via `hiltJavaCompileDebug --rerun-tasks` after the K2 fix — nothing in this repo's build config
  causes or can suppress it; needs an upstream Dagger/Hilt fix.

**Docs updated:** PROGRESS.md (struck the now-resolved/documented checklist item — see this entry
instead).

---

## feature/insights-tab-rework

**Scope:** PROGRESS.md's Insights tab rework — dropped the dot timeline entirely; reordered the
tab to Frequency → Rhythm → Gaps & streaks → Trend → Duration/Intensity → Tags → Calendar heatmap
(moved to the very end); gave Rhythm its own finer 20-tier color scale (`RHYTHM_TIER_COUNT`,
threaded through `heatmapLevelFor`/`toCellColor`/`toTextColor` without touching the calendar
heatmap's or Intensity's shared 10-tier scale); renamed "Gaps & clusters" to "Gaps & streaks" and
added longest/average streak (`computeStreakStats`, a run of consecutive active days); added an
optional Trend note when the average gap or streak length has shifted noticeably across history
(`computeGapShift`/`computeStreakShift`), gated on Trend's existing 8-week span.

**Found & fixed:**
- *Real UI bug from manual on-device review* — Rhythm's time-of-day label column used a fixed
  68dp width (copied from the share card's `MINI_RHYTHM_LABEL_WIDTH`, believed proven-safe since
  it uses the same `labelSmall` style). On-device it wrapped to two lines for Plain (Inter) and
  Bright (Baloo2) — only Intense's condensed Oswald happened to fit. Bumped both the main and
  mini-share-card constants to 88dp and added `maxLines = 1` + `TextOverflow.Ellipsis` as a safety
  net so a future font swap can't silently wrap again instead of failing visibly.
- *Stale doc/comment references to the removed dot timeline* — `TESTING.md`'s "Compose UI — Case
  Detail Insights" row still said "timeline/heatmap/frequency/..."; `DemoDataSeeder.kt`'s
  `RECENT_SURGE_DAYS`/`QUIET_SPELL_DAYS` comments and one `DemoDataSeederTest.kt` test name
  justified themselves by the now-deleted dot timeline's window-shrink/gap-note behavior instead
  of the still-live Trend/Gaps-&-streaks features those same seed knobs also exercise.
- *Demo data didn't showcase the new streak stat* — none of the six seed Cases were guaranteed to
  produce a multi-day streak (a run of consecutive active days) worth looking at. Coffee's existing
  `recentSurge` flag (one event on each of the most recent `RECENT_SURGE_DAYS` = 12 days) already
  produces one incidentally; documented that dual purpose in the comment and added a regression
  test (`` `seed gives Coffee's recent surge a genuine multi-day streak` ``) locking in ≥12 days
  instead of leaving it an unverified accident.
- *Instrumented coverage gap* — the new Gaps & streaks stat rows and Trend shift note had unit
  coverage (domain math) and viewmodel-wiring coverage, but nothing exercised them through the
  actual composed UI the way every sibling stat card already is in
  `CaseDetailInsightsTabTest.kt`. Added `gapsCard_showsLongestAndAverageStreakLabelsAlongsideGapLabels`
  and `trendCard_showsGapShiftNote_whenAverageGapWidensNoticeably`.
- Unused `java.time.Instant` import left behind in `InsightsEngine.kt` after
  `computeTimelineWindow`/`groupEventsByDay` were deleted (caught by `ktlintCheck`).

**Deferred:**
- Rhythm/calendar-heatmap cells still convey their count by shading alone (no content description
  or visible number) — pre-existing PROGRESS.md item, not touched by this pass since it needs its
  own tap-target design, not a drive-by fix.
- Share card's `MiniGapsSection`/`MiniTrendSection` don't surface the new streak/shift fields —
  `GapsDisplay`/`TrendDisplay` gaining fields doesn't break them, and surfacing the new content
  there wasn't part of this request; flagging here rather than silently expanding scope.

**Docs updated:** HODITH_SPEC.md §9 (dropped the dot timeline subsection, calendar heatmap now
described as rendering last) and §10 (reordered stat list description, Gaps & streaks content,
Rhythm's 20-tier note, Trend's shift note) and its share-card section list; TESTING.md's "Stats &
visual data prep" and "Compose UI — Case Detail Insights" rows; PROGRESS.md (struck the
now-resolved Rhythm legend-gap bullet, trimmed the stale "dot timeline" cross-reference from the
remaining color-only-shading bullet).

---

## fix/calendar-heatmap-current-month-gap

**Scope:** PROGRESS.md's Insights tab bug — the calendar heatmap's current-month grid always
rendered every week-row of the full month (`weeksInGrid`), then blanked out any day after "now."
Right after a new month began, that left 3-5 fully-blank trailing rows stacked above the previous
month's card, reading as one large gap. Fixed in `heatmapMonths()` (`InsightsTabState.kt`) by
dropping trailing week-rows that are entirely null once built — safe because a completed month's
last row always contains real data (nothing to drop) and the current month always keeps at least
one row (today is never null).

**Found & fixed:**
- The gap bug itself, plus a regression test asserting the current month's last week-row isn't
  entirely blank when `now` is early in the month.

**Deferred:** nothing deferred — single-function, single-file logic change. Walked the full
checklist against the diff: no duplication, no `android.*` creeping into the touched viewmodel
code, no new composables/strings/colors/magic numbers, `git status` clean, no stray files, and
HODITH_SPEC.md's heatmap description (§9) is high-level enough to still hold — it doesn't
describe week-row layout, so nothing there needed touching.

**Docs updated:** PROGRESS.md (struck the now-fixed bullet); TESTING.md's "Stats & visual data
prep" coverage row (noted the new trailing-row-trim coverage alongside existing day-bucketing).

---

## feature/big-picture-filter-redesign

**Scope:** PROGRESS.md's Big Picture filter redesign — `BigPictureGrid.kt`'s always-expanded
`FilterChipsRow` (two `FlowRow`s of chips permanently above the grid) replaced with two small
trigger chips ("Cases N of M ▸" / "Tags N of M ▸") that each open the existing `CaseFilterChip`/
`TagFilterChip` picker in an `InfoDialog`, plus one combined read-only legend row below summarizing
the current selection with the six collapse states from the validated mockup (both full → nothing;
zero Cases → static note; otherwise per-dimension "All Cases"/"All tags"/"Untagged only" collapse or
itemized chips). Includes the confirmed behavior change: zero tags selected now shows untagged
events only, not nothing. The tag-matching and legend-collapsing logic was extracted into a new
pure, unit-tested file (`BigPictureFilterState.kt`), following the same rationale as
`domain.weeksInGrid`/`viewmodel.bigPictureUiState`. Follow-up during review: each picker dialog also
gained a single bulk `BulkSelectionToggle` button ("Select all"/"Clear all", labelled by the action
it's about to take) so clearing a long preselected list doesn't require deselecting every chip
individually — there was no select-all/clear-all precedent anywhere in the app before this.

**Found & fixed:**
- *Duplication* — `RoundedCornerShape(16.dp)` was repeated as an inline literal at four separate
  chip call sites (`CaseFilterChip`, `TagFilterChip`, `BrightChip`'s local `shape` val, and the two
  new composables `FilterTriggerChip`/`CaseGroupChip`) once this pass added the last two. Extracted
  a single `private val CHIP_SHAPE` and pointed all five sites at it.
- *KDoc-adjacency ktlint violation* — `BigPictureFilterState.kt`'s first draft had a floating
  file-purpose KDoc block immediately followed by `isTagVisible`'s own KDoc, which ktlint rejects
  (a KDoc may not be preceded by a KDoc). Merged the two into one KDoc on `isTagVisible` itself.
- *Legend edge case* — `bigPictureTagLegend` initially checked `selectedTagNames.isEmpty()` before
  the full-selection check, so when zero tags exist anywhere in the app (nothing to filter) it would
  return `UntaggedOnly` instead of the intended vacuous `AllSelected`. Reordered the `when` and added
  a regression test (`` `bigPictureTagLegend is AllSelected, never UntaggedOnly, when no tags exist
  at all` ``) locking in the fix — this only matters if a future caller stops guarding the tag
  trigger/dialog on `allTagNames.isNotEmpty()`, since today's render path never hits it.
- *Test coverage gap* — `VoiceTest.kt`'s "every voice has a non-blank string for every key" test is a
  manually-enumerated assertion list, not reflective; the first draft added the 8 new Big Picture
  filter keys to `Voice.kt`/all three voice objects without adding matching assertions here, so the
  test passed without ever checking them. Added the 8 missing assertions (existing gaps in this same
  test predating this pass — several older Big Picture keys are also unchecked — were left alone as
  out of this pass's scope).
- *Test coverage gap* — no instrumented test in the app provides `LocalCardDecorationStyle`
  (confirmed via repo-wide search), so the entire BRIGHT branch of `CaseFilterChip`/`TagFilterChip`/
  `BrightChip` was already untested by anything but Compose Previews before this pass, and the two
  new composables (`FilterTriggerChip`, `CaseGroupChip`) would have inherited the same gap. Added a
  `decorationStyle` parameter to `BigPictureScreenTest`'s `setContent` helper and two new tests
  (`filterTriggerAndCaseChip_toggleWorksUnderBrightTheme`,
  `filterLegend_showsAllCasesGroupChipAndUntaggedOnlyChip_underBrightTheme`) exercising the BRIGHT
  dispatch branch of all four chip composables end-to-end, run and passing on-device.
- *`BulkSelectionToggle` follow-up walkthrough* — a second full checklist pass specifically against
  the bulk select-all/clear-all addition (Duplication, Decoupling, Complexity, Dead Code, Naming,
  Hardcoded Values, Accessibility, Tests) found nothing to fix: `TextButton` reuse instead of a new
  button style, no pure logic worth extracting (`cases.map{it.id}.toSet()`/`emptySet()` stay inline,
  same as the pre-existing per-chip toggle logic), both new instrumented tests run and passing
  on-device. Logged as its own explicit "nothing found" result rather than folded silently into the
  Scope paragraph, since the first pass at this entry appended that paragraph without actually
  re-running the checklist against the diff.
- *Cross-dimension filter bug* — user-reported: `allTagNames` was computed from every Case's events
  globally, not just currently-selected ones, so the Tags dialog could offer a tag belonging solely
  to a hidden Case. Selecting that tag while the owning Case was deselected made `isEventVisible`'s
  `caseId in visibleCaseIds` and `isTagVisible(...)` clauses individually look satisfied but AND to
  zero events, with no chip or note indicating why the grid had gone empty. Fixed by re-keying
  `allTagNames`'s `remember` on `visibleCaseIds` too and filtering events to `visibleCaseIds` before
  collecting tags — a one-line change reusing the existing `remember(allTagNames)` re-keying that
  already resets `visibleTagNames`, so narrowing Cases now always resets tag selection to "everything
  in the new scope" rather than leaving a stale, unreachable tag selection behind. Three new
  instrumented tests: the Tags dialog only offers tags from selected Cases, and the exact reported
  scenario (narrow tags first, then deselect the Case that owned the only selected tag) now shows the
  remaining Case's events instead of an empty grid.

**Deferred:**
- *Chip touch target* — `FilterTriggerChip` reuses `CaseFilterChip`/`TagFilterChip`'s existing
  10dp/6dp padding formula, so it inherits the same sub-48dp effective tap height already logged as
  deferred in this file's `feature/bright-theme-soft-glow (Big Picture filter chips)` entry. Not a
  new regression, not re-logged as a separate item.
- *Manual on-device exploratory check* — build/lint/unit-test/instrumented-test verification is done
  (including on an emulator), but actually launching the app and eyeballing the new trigger-chip/
  dialog/legend UI is the user's side of this workflow, not run here.

**Docs updated:** HODITH_SPEC.md §9 (filter-chip bullet rewritten for the trigger/dialog/legend
structure, the corrected zero-tags semantics, the bulk toggle, and the Tags dialog's Case-scoping);
TESTING.md (Big Picture instrumented-coverage row
updated to match); PROGRESS.md (the "Big Picture filter redesign" section removed now that it's
shipped, per this file's outstanding-work-only convention).

---

## feature/bright-theme-soft-glow (Big Picture filter chips)

**Scope:** PROGRESS.md's Bright theme redesign checklist, Big Picture's filter chips —
`BigPictureGrid.kt`'s `CaseFilterChip`/`TagFilterChip` now branch on `LocalCardDecorationStyle`:
Bright gets a tint-wash pill with a hairline border, plus an outer 3dp ring at 10%-alpha tint when
selected (Soft Glow mockup's `.chip`/`.chip.on`), approximating the mockup's zero-blur
`box-shadow: 0 0 0 3px` spread (no direct Compose equivalent) as a padded outer `Modifier.border`.
Both chips share the new `BrightChip` container rather than each reimplementing the pill+ring
chrome, since only their inner content (icon+name vs. tag text) differs. Plain/Intense untouched.

**Found & fixed:**
- *Preview fidelity gap* — `BigPictureGridBrightLightPreview`/`...DarkPreview` only provided
  `LocalBigPictureCellStyle`, not `LocalCardDecorationStyle` (nothing in this file dispatched on
  the latter before this pass). Left as-is, the new chip branch would have silently rendered
  Plain-style chips inside the "Bright" grid preview. Added `LocalCardDecorationStyle provides
  CardDecorationStyle.BRIGHT` alongside the existing provider in both.
- *Stale-comment risk* — first pass cited a specific mockup CSS line number
  (`bright-theme-soft-glow.html ~line 112`) in `BrightChip`'s doc comment. Every other Bright-pass
  comment in this codebase cites the CSS selector and file, never a line number, since the mockup
  can be edited independently and a cited line drifts silently. Dropped the line reference.

**Deferred:**
- *Chip touch target* — `CaseFilterChip`/`TagFilterChip`'s effective tap height (6dp+6dp padding
  around ~14sp text, ~26dp) is under the 48dp accessibility minimum, but that's pre-existing across
  all three themes, unchanged by this pass — not introduced or worsened here.

**Docs updated:** none — cosmetic per-theme decoration, no spec-level divergence; TESTING.md's Big
Picture row already covers filter-chip *behaviour* generically, not per-theme styling.

---

## feature/bright-theme-soft-glow (bottom navigation)

**Scope:** PROGRESS.md's Bright theme redesign checklist, bottom navigation item —
`HodithNavHost.kt`'s `NavigationBar`/`NavigationBarItem` now branches on
`LocalCardDecorationStyle`: the active tab's icon sits inside `IconHalo`'s glow (Soft Glow
mockup's `.navitem.on .ico`), with Material3's default pill indicator suppressed
(`indicatorColor = Color.Transparent`) so the two don't stack. Shared/app-wide component, wired
in its own pass per the checklist's own note rather than folded into a screen. Plain/Intense
untouched.

**Found & fixed:**
- *Naming* — first pass named the new preview composables `BrightNavIconPreviewContent`/
  `BrightNavIconLightPreview`/`BrightNavIconDarkPreview`, "Bright" leading. Every other Bright-pass
  preview follows `<Subject>Bright<Descriptor>PreviewContent`/`...LightPreview`/`...DarkPreview`
  (`HomeBrightRowsPreviewContent`, `SettingsBrightPlankPreviewContent`,
  `SegmentedChoiceRowBrightPreviewContent`, `InsightsBrightCardsPreviewContent`) — "Bright" placed
  after the subject, not before. Renamed to `NavBrightIconPreviewContent`/
  `NavBrightIconLightPreview`/`NavBrightIconDarkPreview` to match. (The branch composable itself,
  `BrightNavIcon`, keeps "Bright" leading — the separate, consistent convention already used by
  `BrightIconChoice`/`BrightDayCell`/`BrightActionRow`/`BrightHomeCaseListItem`.)
- *Hardcoded value* — `IconHalo(size = 28.dp)` deviates from `IconHalo`'s own 34dp default with no
  comment explaining why (Edit Case's icon-picker grid pass, by contrast, declared a named
  `BRIGHT_ICON_CHOICE_VISUAL_SIZE` constant specifically to document that its value matches the
  default on purpose). Added an inline comment explaining the smaller size matches the mockup's
  compact nav-icon circle; skipped a named constant since, unlike Edit Case's two-call-site match,
  nothing else needs this exact value.

**Deferred:**
- *Duplication* — none found; `BrightNavIcon` follows the same `when`/`if`-on-
  `LocalCardDecorationStyle` idiom as `HomeCaseListItem`/`ActionRow`/`InsightsCard`.
- *Complexity & Pattern Health* — `BrightNavIcon` does replace Material3's own selected-item
  indicator visual; deliberate, matching the mockup, same category of call already made for
  `GlowCard` over `Card` and `BrightSegmentedChoiceRow` over `SegmentedButton`.
- *Accessibility* — unchanged: `contentDescription = null` on nav icons was already correct before
  this pass (the visible label supplies the accessible name), and the touch target is still the
  full `NavigationBarItem`, not the icon glyph — the 28dp `IconHalo` sits well inside it.
- *Tests* — no new pure logic (purely visual); no `HodithNavHost` unit/instrumented test exists to
  update, confirmed by search. Not run on a device (the user's side of the workflow).
- *Spec Review* — HODITH_SPEC.md's nav mention ("Bottom navigation: Home · Big Picture · Settings")
  documents the three destinations, not per-item chrome; no update needed, same conclusion as every
  prior Bright pass.
- *TESTING.md* — not touched, matching every prior Bright pass's own wiring commit.
- Sections not applicable: Decoupling (no ViewModel/domain/data-layer code touched); Repo Hygiene
  (only `HodithNavHost.kt` changed, confirmed via `git status`); Hardcoded Values beyond the one
  comment above (no new `Color(0xFF...)`; `Color.Transparent` is a standard API value, not a
  literal); Deprecated APIs (`compileDebugKotlin --warning-mode all` shows nothing new for this
  file); Naming Consistency beyond the one fix above (no new `Voice` keys needed — labels already
  come from `destination.label(voice)`).

**Docs updated:** PROGRESS.md — bottom navigation item struck from the Bright theme redesign
checklist.

---

## feature/bright-theme-soft-glow (Edit Case)

**Scope:** PROGRESS.md's Bright theme redesign checklist, Edit Case slice — `CaseEditScreen.kt`'s
name/description fields, icon-picker grid, and both toggle rows now branch on
`LocalCardDecorationStyle`, and `SegmentedChoiceRow.kt` (shared by five other screens: Settings,
Share preview, Insights, Triggers, Hunch) picks up a Bright-only continuous "pill track" look.
Text fields get Bright's `shapes.small` (16dp) radius; the icon grid's selected choice gets
`IconHalo`'s tint-wash + glow, the rest a plain thin-bordered circle, both at `IconHalo`'s own
34dp default inside the existing 48dp touch target; both `Switch`es get Bright-tinted colors
matching the mockup's `.mswitch`. Live the moment it ships, same as every prior Bright pass.

**Found & fixed:**
- *Scope/approach, caught before writing code* — the mockup's `.segrow`/`.seg.on` is a single
  tinted track with the selected option popped forward as a floating capsule, a different visual
  metaphor than M3's bordered `SegmentedButton` chrome (which only rounds a group's outer ends,
  not each segment individually). Raised it rather than guessing; user's answer was to match the
  mockup as closely as possible, which pointed at a custom Bright-only composable
  (`BrightSegmentedChoiceRow`) rather than fighting `SegmentedButton`'s built-in border/divider/
  sizing assumptions — the same call already made for `GlowCard` over a restyled M3 `Card`. Since
  `SegmentedChoiceRow` is a single shared function, branching it wires all five call sites the
  moment this commits; added Compose Previews exercising both a two-option (logFlow, including its
  disabled-when-unavailable state) and three-option (durationMode/theme-picker) shape as the cheap
  validation CLAUDE.md's collaboration rule calls for, ahead of committing to it broadly.
- *Accessibility semantics* — `BrightSegmentedChoiceRow` manually replicates the
  `selectableGroup`/`Role.RadioButton` semantics `SegmentedButton` normally provides for free, same
  idiom already used by this file's own icon-picker grid (`selectableGroup` + per-icon
  `selectable(..., role = Role.RadioButton)`).
- *Lint* — `AutoboxingStateCreation`: the new `SegmentedChoiceRow` preview's local `Int` state used
  `mutableStateOf` instead of `mutableIntStateOf`; fixed both.
- *Compile* — an explicit `import androidx.compose.foundation.layout.weight` resolved to an
  internal `RowColumnParentData` property of the same name instead of `RowScope`'s member
  `weight()` extension (which needs no import at all, confirmed against every other file in the
  app that calls `.weight(...)`); removed the import.
- *Tests* — added light + dark Compose Previews for `SegmentedChoiceRow`'s Bright branch (in both
  shapes above) and for the full `CaseEditScreen` Bright form, matching every prior pass's
  precedent of a screen-level preview pair.

**Deferred:**
- *Duplication* — none found; `IconChoice`/`caseEditSwitchColors`/`caseEditTextFieldShape` all
  branch on `LocalCardDecorationStyle` with the same `when` shape as `Plank`/`ActionRow`/
  `InsightsCard`. The Bright icon-choice/segmented-row composables are single-caller extractions,
  same accepted convention as `BrightActionRow`.
- *Complexity & Pattern Health* — `BrightSegmentedChoiceRow` and `BrightIconChoice` do reimplement
  something M3 already provides (`SegmentedButton`, and `IconChoice`'s own existing selectable
  circle); deliberate, per the approach decision above, not an oversight.
- *Accessibility* — segment touch height in `BrightSegmentedChoiceRow` lands at roughly 42dp (track
  padding + 7dp segment padding + `labelLarge`'s line height), under the 48dp guideline but not a
  new regression: M3's own `SegmentedButton` it replaces is already sub-48dp too, matching the
  same already-accepted precedent noted for `BrightActionRow` in the Settings pass. Icon-choice
  emoji `Text` still carries no `contentDescription` in either branch — pre-existing on the
  Plain/Intense side already, not something this pass introduced or was scoped to fix.
- *Tests* — no new pure logic (branch is purely visual), so no unit coverage needed;
  `CardDecorationStyleTest`'s mapping is unaffected. `CaseEditScreenTest` only ever provides
  `PlainVoice` with no `LocalCardDecorationStyle` override, so it exercises the untouched `PLAIN`
  default — confirmed by rereading the test file — and wasn't re-run on a device (the user's side
  of the workflow).
- *Spec Review* — HODITH_SPEC.md §14's Edit Case row and §12's theme table describe flow/content
  and high-level palette/type feel, not per-component chrome; same "no update needed" conclusion as
  every prior Bright pass.
- *TESTING.md* — not touched, matching every prior Bright pass's own wiring commit.
- Sections not applicable: Decoupling (no ViewModel/domain/data-layer code touched); Naming
  Consistency (`Bright`-prefixed composables follow the established convention; no new `Voice` keys
  needed, every label reuses an existing key); Hardcoded Values (no new `Color(0xFF...)` literals;
  the new `onSurface.copy(alpha = ...)` track/border/switch tints and `Dp` layout literals are UI
  values like the file's existing ones, not domain-layer product constants); Deprecated APIs
  (checked the actual `lintDebug` HTML report, not just its exit code — zero findings against
  either changed file); Repo Hygiene (`git status` shows only the two expected modified files).

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean.

**Docs updated:** PROGRESS.md (Bright theme redesign section — struck the Edit Case bullet).

---

## feature/bright-theme-soft-glow (Settings)

**Scope:** PROGRESS.md's Bright theme redesign checklist, Settings slice — `SettingsScreen.kt`'s
shared `Plank` shell now branches on `LocalCardDecorationStyle`: Bright wraps every settings
section ("Spread the word", "Look & feel", "Nudge me", "Your stuff") in `GlowCard`, Plain/Intense
keep today's `OutlinedCard`. `ActionRow` (About HODITH, Rate the app, Contact us, Export, Import,
Delete all data, Load demo data) also branches: Bright renders a flat label + chevron row
(`BrightActionRow`, matching the mockup's `.arow`) instead of `FilledTonalButton`, Plain/Intense
unchanged. Live the moment it ships, same as Home/Big Picture/Insights before it.

**Found & fixed:**
- *Scope, caught before writing code* — PROGRESS.md's Settings bullet explicitly tied `ActionRow`'s
  `FilledTonalButton` question to this pass, and the mockup's `.arow` look (flat chevron row) is
  visually nothing like today's button pill — a real ambiguity, not a call to make solo. Raised it;
  user's answer: adopt the flat chevron-row look for `ActionRow`, but Bright-only (branched on
  `LocalCardDecorationStyle` like `Plank`), not app-wide. Plain/Intense keep `FilledTonalButton`;
  PROGRESS.md's standing `FilledTonalButton` item stays open, reworded to reflect that Bright is
  now resolved while Plain/Intense's styling is still an open product-owner question.
- *Tests* — added a light + dark Compose Preview (`SettingsBrightPlankLightPreview`/
  `...DarkPreview`) exercising `Plank` and `ActionRow` together, including the destructive
  (`Delete all data`) tint — `SettingsScreen.kt` had no previews at all before this pass, same gap
  `InsightsTab.kt` had before its own Bright pass.

**Deferred:**
- *Duplication* — none found; `Plank`'s branch is a straight `when` mirroring `InsightsCard`'s/
  `HomeCaseListItem`'s existing dispatch pattern. `BrightActionRow` follows the same
  single-caller-branch convention as `BrightHomeCaseListItem`.
- *Complexity & Pattern Health* — considered whether `BrightActionRow` should use M3's `ListItem`
  (headline + trailing icon) instead of a plain `Row`; kept `Row` for consistency with the app's
  existing row convention (`PlainHomeCaseListItem`, `BrightHomeCaseListItem` are both plain `Row`s
  too, not `ListItem`), not because `ListItem` couldn't do the job.
- *Accessibility* — the chevron `Icon` has `contentDescription = null`: it's decorative, not an
  icon-only tap target — the row itself is the click target and already carries the visible label
  Text, same pattern as `HomeCaseListItem`'s existing clickable `Row`. Touch-target height: Bright's
  `BrightActionRow` lands at roughly 42dp (11dp padding + `labelLarge`'s ~20dp line height), close to
  but still under the 48dp guideline — not a new regression, though, since the `FilledTonalButton`
  it replaces is already only 40dp tall (M3's `ButtonDefaults` minimum) in Plain/Intense; this
  matches the already-flagged, already-accepted sub-48dp precedent noted in PROGRESS.md's Settings
  section (Developer Mode's tap target) rather than introducing a new one.
- *Tests* — no new pure logic (branch is purely visual), so no unit coverage needed;
  `CardDecorationStyleTest`'s theme→style mapping is unaffected. `SettingsScreenTest` only ever
  provides `PlainVoice` with no `LocalCardDecorationStyle` override, so it exercises the untouched
  `PLAIN` default — confirmed by rereading the test file, not assumed — and wasn't re-run on a
  device (the user's side of the workflow).
- *Spec Review* — HODITH_SPEC.md's Settings mentions (data model, About screen, bottom nav) don't
  describe per-theme card/button chrome at this granularity, same conclusion as every prior Bright
  pass — no update needed.
- *TESTING.md* — not touched, matching Home/Big Picture/Insights, none of which updated it for their
  own Bright wiring either; confirmed via `git show --stat` on those three commits rather than
  assumed.
- Sections not applicable: Decoupling (no ViewModel/domain/data-layer code touched); Naming
  Consistency (`BrightActionRow` follows the established `Bright`-prefix convention; no new `Voice`
  keys needed — every label reuses an existing key); Hardcoded Values (no new literal colors, all
  via `MaterialTheme.colorScheme`; the `11.dp` row padding is a plain UI layout value like the
  file's other inline `Dp` literals, not a product constant per CLAUDE.md's domain-layer rule);
  Deprecated APIs (checked the actual `lintDebug` HTML report, not just its exit code — only the
  pre-existing, unrelated `ObsoleteSdkInt` notice, nothing new); Repo Hygiene (`git status`/`git
  diff --stat` show only the one expected modified file).

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean.

**Docs updated:** PROGRESS.md (Bright theme redesign section — struck the Settings bullet; reworded
the `FilledTonalButton` "needs design" item to reflect Bright's resolution, left it open for
Plain/Intense).

---

## feature/bright-theme-soft-glow (Insights)

**Scope:** PROGRESS.md's Bright theme redesign checklist, Case Detail Insights slice —
`InsightsTab.kt`'s shared `InsightsCard` shell now branches on `LocalCardDecorationStyle`: Bright
wraps every stat card (Dot timeline, Heatmap, Frequency, Rhythm, Gaps, Trend, Duration, Intensity,
Tags) in `GlowCard`, Plain/Intense keep today's plain `Card`. `FrequencyCard`'s bars swap their flat
`colorScheme.primary` fill for a primary→surface-tinted vertical gradient on Bright, unchanged flat
fill elsewhere. Live the moment it ships, same as the Home and Big Picture passes before it.

**Found & fixed:**
- *Scope, caught before writing code* — the mockup's Insights screen also shows a two-up stat-tile
  row ("Longest gap" / "Most common day") sitting outside any card, and PROGRESS.md's original
  bullet described it as a new component to build. "Most common day" isn't computed anywhere in the
  domain layer today, and the tile row would have pulled `GapsCard`'s existing `longestGapDays` row
  out into new UI — both are feature additions, not a restyle of something already shipping. Raised
  it; user's answer was explicit: this pass changes design only, and anything the mockup shows that
  isn't already a feature in the app gets ignored rather than built. Result: no tile row, no new
  component, `GapsCard` and every other stat card's content untouched — only the shared shell and
  the bar-chart fill actually changed.
- *Tests* — added a light + dark Compose Preview (`InsightsBrightCardsLightPreview`/
  `...DarkPreview`) exercising `FrequencyCard` and `GapsCard` together, covering both the new
  `GlowCard` branch and the gradient bars — `InsightsTab.kt` had no previews at all before this
  pass, unlike Home/Big Picture's composables.

**Deferred:**
- *Duplication* — none found; `InsightsCard`'s branch is a straight `when` mirroring
  `HomeCaseListItem`'s existing dispatch pattern, and `frequencyBarBrush` is a small standalone
  helper rather than something with an existing home.
- *Tests* — no new pure logic (the gradient is a `Brush`, not domain code), so no unit coverage
  needed; `CardDecorationStyleTest`'s theme→style mapping is unaffected by a purely visual change
  inside the existing `BRIGHT` branch. The instrumented `CaseDetailInsightsTabTest` doesn't reference
  `AppTheme`/`LocalCardDecorationStyle`, so it exercises the untouched Plain path — wasn't re-run on
  a device (not something this pass can do; manual/on-device confirmation is the user's side of the
  workflow).
- *Spec Review* — HODITH_SPEC.md's §9-10 describe the Insights tab's data/layout, not per-theme card
  chrome, and no theme's card decoration is spec'd at that granularity today (same conclusion as the
  Home and Big Picture passes) — no update needed.
- *TESTING.md* — not touched, matching the Home and Big Picture commits, neither of which updated it
  for their own (larger) Bright wiring changes either; confirmed via `git show --stat` rather than
  assumed.
- Sections not applicable: Decoupling (no ViewModel/domain/data-layer code touched); Complexity &
  Pattern Health (`FrequencyCard` is 71 lines, well under the ~150-line split threshold;
  `frequencyBarBrush`'s single-caller extraction earns its keep — computes the `Brush` once outside
  the bar-drawing loop rather than per-bar); Naming Consistency (`frequencyBarBrush` is lowerCamelCase,
  correct for a `@Composable` that returns a value rather than emitting UI); Repo Hygiene (`git
  status` shows only the three expected modified files, nothing stray or secret-shaped); Hardcoded
  Values (no new literal colors — `frequencyBarBrush`'s `0.4f` tint fraction is a named constant,
  `FREQUENCY_BAR_GRADIENT_END_TINT_FRACTION`, following the file's existing constant style, not a
  product constant); Deprecated APIs (checked the actual `lintDebug` HTML report, not just its exit
  code — zero issues flagged anywhere in `InsightsTab.kt`); Accessibility (no new icon-only tap
  targets or tap-target size changes — cards remain non-interactive containers, and the frequency
  bars' gradient is purely decorative alongside their existing numeric count labels, not a new
  color-only signal).

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean.

**Docs updated:** PROGRESS.md (Bright theme redesign section — struck the Insights bullet; no new
gaps spotted this pass, unlike Big Picture's).

---

## feature/bright-theme-soft-glow (Big Picture)

**Scope:** PROGRESS.md's Bright theme redesign checklist, Big Picture slice — `BigPictureGrid.kt`'s
`BrightDayCell` replaces today's border+elevation-only marker with a blurred primary-tint ring
(Soft Glow mockup's `.cal-cell.today` box-shadow ring). Flagged in PROGRESS.md as a real behavior
change to already-shipped Bright code, not new work — and, like the Home pass, it's live the
moment it ships, not just inert infrastructure.

**Found & fixed:**
- *Approach* — confirmed with the user before implementing, since `IconHalo`'s own doc comment
  anticipated being reused here but its API (fixed `Dp` size, hardcoded `CircleShape`) doesn't fit
  a dynamic-width rounded-square grid cell, and the mockup's ring is drawn directly on the cell's
  own rounded-square shape via `box-shadow`, never a circular badge. Built the ring locally in
  `BrightDayCell` using the same blur+tint recipe instead of forcing `IconHalo`'s shape, and left
  `IconHalo` and its two shipped Home call sites untouched.
- *Own mistake caught by the build* — first pass imported `matchParentSize` as a top-level import;
  it's a `BoxScope` member function, not an importable symbol. `lintDebug`'s `compileDebugKotlin`
  step caught the unresolved reference; removed the bad import.
- *Tests* — replaced the single `BigPictureGridBrightPreview` with paired light/dark previews
  (`BigPictureGridBrightLightPreview`/`...DarkPreview`), matching the Home pass's precedent and
  PROGRESS.md's "Compose Preview per changed composable" checklist item.

**Deferred:**
- *Duplication* — the ring's blur+tint layer duplicates ~2 lines of `IconHalo`'s glow recipe
  (`tint.copy(alpha = 0.45f)` behind a `blur()`) rather than sharing code, for the API-mismatch
  reason above; documented in `BrightDayCell`'s doc comment. Worth revisiting only if a third call
  site needs the same square-ring treatment.
- *Tests* — no new pure logic, so no new unit coverage; `BigPictureDecorationTest`'s theme→style
  mapping test is unaffected by a purely visual change inside the existing `BRIGHT` branch. Manual
  on-device confirmation that Plain/Intense render unchanged and the Bright ring reads correctly is
  the user's side of the workflow, not run here.
- *Spec Review* — HODITH_SPEC.md doesn't describe per-theme cell decoration at this granularity
  (same conclusion as the Home pass) — no update needed.
- Sections not applicable: Decoupling (no ViewModel/domain/data-layer code touched); Hardcoded
  Values (the `0.45f` alpha matches `IconHalo`'s own constant rather than introducing a new one; the
  `10.dp` blur radius is a one-off visual tuning value inline like the file's existing
  `2.dp`/`3.dp`/`6.dp`/`15.dp` constants — not a product constant); Deprecated APIs (none);
  Accessibility (today's cell already signals "today" via bold day-number text plus a border/ring,
  never color alone; no new icon-only tap targets, no tap-target size change).

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean.

**Docs updated:** PROGRESS.md (Bright theme redesign section — struck the Big Picture bullet, noted
the ring is live alongside Home's row change; added two new bullets for gaps spotted during this
pass but out of its scope: the bottom `NavigationBar`'s active-tab glow, which is a shared app-wide
component and not this pass's to fix, and Big Picture's filter chips, which are still fully
unbranched across all three themes).

---

## feature/bright-theme-soft-glow (Home rows)

**Scope:** PROGRESS.md's Bright theme redesign checklist, Home slice — `HomeScreen.kt`'s
`HomeCaseListItem` now branches on `LocalCardDecorationStyle`: Plain/Intense render the exact
original row (extracted unchanged into `PlainHomeCaseListItem`), Bright wraps rows in `GlowCard`,
alternates `IconHalo` tint primary/secondary per row by list index, and promotes the case name to
the theme's display font. Unlike the foundations pass, this is genuinely live — anyone with Bright
selected today sees the new row look, not just inert infrastructure.

**Found & fixed:**
- *`GlowCard` had no click support* — Home rows are a whole-row tap target, and appending a caller-
  supplied `Modifier.clickable` to `GlowCard`'s `modifier` param would land before its internal
  `.clip(shape)`, so the ripple would render as a rectangle instead of respecting the rounded card.
  Added an `onClick` param to `GlowCard` that applies `clickable` after `clip`, matching how M3
  `Card`'s own `onClick` overload avoids the same problem.
- *Tests* — added a light + dark Compose Preview (`HomeBrightRowsLightPreview`/`...DarkPreview`)
  for the wired rows, per PROGRESS.md's checklist item asking for one per changed composable.

**Deferred:**
- *Duplication* — `PlainHomeCaseListItem`/`BrightHomeCaseListItem` are two full, independent
  composables with substantial structural overlap (icon, name/meta column, action button,
  `StaleOngoingBanner`) rather than one function sharing an extracted inner block. Follows this
  codebase's own precedent exactly: `BigPictureGrid.kt`'s `PlainDayCell`/`IntenseDayCell`/
  `BrightDayCell` are three fully independent composables with the same kind of overlap, dispatched
  from `DayCell` the same way `HomeCaseListItem` now dispatches to these two.
- *Tests* — no new instrumented coverage added for Bright's row click-through specifically, and the
  existing `HomeScreenTest` wasn't re-run on a device/emulator (not something this pass can do —
  manual/on-device verification is the human's side of this workflow). Existing test should be
  unaffected: it doesn't reference `AppTheme`, so it exercises `LocalCardDecorationStyle`'s default
  (`PLAIN`), which routes to `PlainHomeCaseListItem` — byte-for-byte the original implementation,
  same `caseId` keys via `itemsIndexed`. Worth an eyes-on pass (Previews or a real device) before
  this goes further, since it's the first change in this branch that actually changes what ships.
- *Spec Review* — HODITH_SPEC.md's §12 stays at the palette/type-feel/sample-copy level for all
  three themes; it doesn't describe any theme's row/card decoration today (Big Picture's per-theme
  cell treatments aren't spec'd either), so no update needed — consistent with existing granularity.
- Sections not applicable: Decoupling (no ViewModel/domain/data-layer code touched); Hardcoded
  Values (no new literal colors, only theme-derived `tint`; `index % 2` mirrors `BrightDayCell`'s
  own unextracted alternation pattern); Deprecated APIs (none); most of Accessibility (existing
  `IconButton`/`StopIconButton` content descriptions untouched; the new whole-row tap target is
  larger than the old one, not smaller).

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean.

**Docs updated:** PROGRESS.md (Bright theme redesign section — struck the Home bullet, noted the
row change is live, not just inert infrastructure).

---

## feature/bright-theme-soft-glow

**Scope:** PROGRESS.md's Bright theme redesign (Soft Glow) checklist, foundations slice: `Color.kt`'s
primary-tinted ink for `brightLight`/`brightDark`, a new `LocalCardDecorationStyle` fork point
(mirrors the existing `LocalBigPictureCellStyle`/`LocalShareCardSkin` split), and the shared
`GlowCard`/`IconHalo` primitives those screens will consume. No screen wired to the new fork point
yet — Plain/Intense/current-Bright are all unaffected.

**Found & fixed:**
- *Naming Consistency* — the file holding `GlowCard` and `IconHalo` was named `GlowCardDecoration.kt`,
  which overclaims (`IconHalo` isn't card decoration). Renamed to `GlowDecoration.kt`.
- *Tests* — `cardDecorationStyle`'s `when` mapping had the same copy-paste risk (two themes
  accidentally mapping to the same style) that `BigPictureDecorationTest` already guards for
  `bigPictureCellStyle`, with no equivalent coverage. Added `CardDecorationStyleTest`, mirroring it.
- *Docs* — PROGRESS.md's Bright theme redesign section struck the three now-complete foundation
  bullets (ink formula, card-decoration primitive, icon-halo primitive) and rewrote the five
  remaining bullets to name the concrete `GlowCard`/`IconHalo`/`LocalCardDecorationStyle` symbols
  now that they exist, instead of speculative "needs to be built" language.

**Deferred:**
- Considered building `GlowCard` on top of Material3 `Card`/`Surface` instead of a raw `Column` with
  manual `shadow`/`background`/`border`. Kept the manual version: `Card`'s `containerColor` only
  accepts a solid `Color`, not a `Brush`, and its elevation API doesn't expose a per-tint
  ambient/spot shadow color the way `Modifier.shadow` does — wrapping `Card` with a transparent
  container and layering the gradient on top would add a wrapper with no functional benefit over the
  direct approach.
- `GlowCard`/`IconHalo` have no real callers yet, only their own Compose Previews. Intentional — this
  is the foundations step of a staged plan; consumers land screen-by-screen in the checklist's
  remaining bullets.
- Sections not applicable: Decoupling (no ViewModel/domain/data-layer code touched); most of
  Accessibility (no clickable targets introduced — both primitives are decorative containers, tap
  targets are each future consumer's responsibility); Deprecated APIs (none); Spec Review (nothing
  user-visible has shipped yet — HODITH_SPEC.md's theme section doesn't need updating until a screen
  actually renders the new look); most of Tests (no domain/ViewModel logic, no bug fix, no
  instrumented tests, no flow crossing a system-process boundary — this is unshipped UI plumbing).

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean.

**Docs updated:** PROGRESS.md (Bright theme redesign section — struck 3 completed bullets, rewrote
the remaining 6 to point at the new primitives by name).

---

## test/automate-manual-plan

**Scope:** PROGRESS.md's testing item asked for a deliberate pass over MANUAL_TEST_PLAN.md to find
steps that could move from manual to automated coverage instead of assuming every entry still needs
a human. Added five new instrumented test classes: `WidgetChromeNavigationTest` (List widget
title/empty-state taps, Single-case widget's "Case is gone" tap — all outside the List widget's
`LazyColumn`/`ListView`, so clickable via a real `AppWidgetHost`), `WidgetLogTrampolineActivityTest`
(the DETAIL_SHEET trampoline sheet, launched directly), `NotifierContentTest` (real `Notifier`
posts a correctly `Voice`-worded notification), `NotificationActionReceiverTest` (Log/All quiet
broadcast actions), and `ContentResolverBackupFileWriterTest` (the real `ContentResolver` boundary
under export/import). No production code changed — test-only, plus the three test docs.

**Found & fixed:**
- *Bugs, both in new test setup, not production code* — `NotificationActionReceiver.onReceive`
  calls `goAsync()`, which throws `NullPointerException` unless the system's real broadcast dispatch
  set up the receiver's pending-result state first; the first pass called `onReceive` directly on a
  bare instance. Fixed by dispatching through a real `Context.sendBroadcast`. Separately,
  instrumented tests run inside `HiltTestApplication` (`HiltTestRunner`), not the real
  `HodithApplication`, so the `hodith_alerts` notification channel that `HodithApplication.onCreate()`
  normally creates never exists under test — `NotificationManagerCompat.notify()` silently drops a
  notification posted to a channel that doesn't exist. Both `NotifierContentTest` and
  `NotificationActionReceiverTest` now call `ensureNotificationChannel()` in `setUp`.
- *Docs* — MANUAL_TEST_PLAN.md's Widgets/Notifications/Data & backup sections rewritten: items now
  fully covered by the new tests removed outright (Widgets 14 → 11 items, Notifications 8 → 5);
  items only partly covered narrowed to just the still-manual slice (visual checks, notification tap
  targets — `PendingIntent` doesn't expose its wrapped `Intent` through any public API, so that part
  stays human-only). Also documented three pre-existing test classes
  (`ListWidgetConfigureFlowTest`/`SingleCaseWidgetConfigureFlowTest`/`WidgetActionsFlowTest`) whose
  coverage the manual plan hadn't caught up to yet. TESTING.md's "Planned instrumented coverage"
  table gained rows for all of the above; its manual-only seed list left as historical rather than
  rewritten item-by-item, with a note pointing at MANUAL_TEST_PLAN.md as the authoritative version.

**Deferred:**
- *Duplication* — the small Android `View`-traversal click-helpers (`findClickableAncestorOfText`)
  and the POST_NOTIFICATIONS shell-grant/`waitFor` polling helpers are each duplicated across two new
  test files rather than extracted to a shared utility. Follows this test suite's existing
  convention (e.g. `SingleCaseWidgetConfigureFlowTest` already keeps its own `collectText` rather
  than sharing `WidgetActionsFlowTest`'s) — one-off per-file helpers over a shared abstraction for
  small, single-purpose Android test plumbing.
- Several manual items stay open because nothing added coverage for them: the List widget's row
  content (blocked by the `ListView`-under-`AppWidgetHost` limitation), a second independent List
  widget configure instance and its Edit re-entry path, Single-case widget's icon/count-area tap to
  open Case details, and the `DETAIL_SHEET` case's widget-button tap specifically (its trampoline
  sheet is covered; the button click that launches it isn't). None were in scope for this pass —
  listed in MANUAL_TEST_PLAN.md with what is and isn't covered for each.
- Not tagged `@UiTest`: none of the five new classes are Compose *screen* tests, and no existing
  class with incidental Compose interaction (`ListWidgetConfigureFlowTest`,
  `SingleCaseWidgetConfigureFlowTest`) is tagged either — followed that precedent rather than
  introducing a new one.
- Sections not applicable: Decoupling, Complexity & Pattern Health, Naming Consistency, Hardcoded
  Values, Accessibility, Deprecated APIs, Spec Review — no production `main` source changed.

`ktlintCheck`, `lintDebug`, and `test` all run clean. `connectedDebugAndroidTest` — the full suite,
not just the new classes — passes clean (176 tests) on an emulator.

**Docs updated:** MANUAL_TEST_PLAN.md (Widgets/Notifications/Data & backup sections narrowed/
trimmed per above) and TESTING.md ("Planned instrumented coverage" table gained three rows; manual
seed list annotated as historical). PROGRESS.md's now-completed testing item struck.

---

## feature/list-widget-per-instance-cases

**Scope:** The redesign deferred at the end of `feature/single-case-widget (bug-fix follow-up)`
below: List widget Case selection moved from the Case-level `pinned` flag to per-widget-instance
selection, mirroring the Single-case widget's own `PreferencesGlanceStateDefinition` pattern. Each
List widget placement now stores its own `Set<Long>` of Case ids (`CaseIdsKey`, a
`stringSetPreferencesKey`) instead of every instance sharing one Case-level flag. `CaseEntity.pinned`
removed entirely (Room bumped to v6 — no real `Migration` exists pre-release, destructive fallback
covers it). `ListWidgetConfigureViewModel`/`Activity` rewritten from skip-once-anything-pinned to
always-show-multi-select, matching `SingleCaseWidgetConfigureViewModel`/`Activity`'s shape.

**Found & fixed:**
- *Docs* — `widgetConfigureBody`'s copy ("You can change this later from each Case's settings")
  predated this branch and was already stale — that toggle was removed from Case Edit in the prior
  pass, so the sentence pointed at a control that no longer exists. Reworded across all three
  voices to point at long-pressing the widget and tapping Edit (the actual reconfigure path), which
  is also now literally true instead of aspirational.
- *Naming* — renamed `widgetNoPinnedCasesMessage` to `widgetNoCasesSelectedMessage` and reworded it
  in all three voices, since "pin one from its Case screen" no longer describes how selection
  works.
- *Tests* — the first pass at rewriting `ListWidgetConfigureFlowTest` added assertions that each
  selected Case's name renders inside the List widget's `ListView` after a real configure flow.
  That's unverifiable and failed on-device: Glance backs the List widget's `LazyColumn` with a
  `RemoteViewsService`/`ListView` adapter that only populates once the host view is attached to a
  real window, which `AppWidgetHost.createView()` never triggers (same limitation already
  documented in `DEV_PLAYBOOK.md` §5 and the `feature/single-case-widget (bug-fix follow-up)` entry
  below) — the original test never asserted on row content for exactly this reason. Reverted to the
  original's achievable assertion level: ListView present, no-Cases-selected empty state absent.

**Deferred:**
- Reconfiguring an already-placed List widget (long-press → Edit) opens the picker with nothing
  pre-selected, rather than showing its current picks checked. This matches the Single-case
  widget's existing behavior (its picker doesn't pre-select the currently-bound Case either) —
  deliberately not scope-creeped into "pre-populate from existing per-instance state," which
  neither widget does today and PROGRESS.md's item didn't ask for.

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean, including the Room v6 schema
export and the rewritten `ListWidgetConfigureViewModelTest`. `connectedDebugAndroidTest` (165
tests, including the rewritten `ListWidgetConfigureFlowTest` and `CaseDaoTest`'s round-trip swapped
from `pinned` to `archived`) passes clean on an emulator, after two unrelated environment hiccups
mid-session: the emulator's `system_server` degraded after ~10h uptime (needed a fresh
emulator instance) and, separately, `SettingsScreenTest` — a class untouched by this branch —
threw an unrelated lifecycle-teardown timeout on a different test each of two full-suite runs
(`checkInInfoIcon_opensDialog`, then `rateAppButton_showsComingSoonSnackbar`), neither reproducing
on retry. Worth a look if `SettingsScreenTest` keeps flaking on future branches, but not chased
further here since it's unrelated to this change.

**Docs updated:** HODITH_SPEC.md's §5 Case field table (`pinned` row removed) and §15 (List widget
bullet now describes per-instance selection; Single-case widget bullet's now-pointless contrast
against the `pinned` flag dropped). MANUAL_TEST_PLAN.md's Widgets section gained a per-instance
multi-widget configure step and had its empty-state/"pin the same Case" wording updated to match.
PROGRESS.md's Widgets item struck as done.

---

## feature/single-case-widget (bug-fix follow-up)

**Scope:** Fixed both widgets getting stuck on their empty/not-found state right after a correct,
verified configure flow (two stacked causes: missing `android:initialLayout`, then non-reactive
`provideGlance()` state reads — full root-cause writeup in `DEV_PLAYBOOK.md`'s new §5), plus a
handful of smaller fixes found along the way and two new instrumented test files. Physical-device
verification (the blocker at the end of the previous session on this branch) is done: both widgets
populate immediately after configuring, and the Single-case widget correctly reflects a logged
event's duration.

**Found & fixed:**
- *Duplication* — `WidgetPalette` (`WidgetCommon.kt`) hardcoded seven hex values that are literal
  copies of `plainLight`'s fields in `ui/theme/Color.kt`, restated correctly this pass to actually
  match the Plain theme (previously they were just an unrelated dark palette, so the duplication
  hadn't been *live* duplication before). Extracted the seven overlapping values into named
  `internal` constants next to `plainLight` (`PlainLightPrimary`, `PlainLightBackground`, etc.) and
  pointed both `plainLight` and `WidgetPalette` at them, instead of leaving two independently
  hardcoded copies that could silently drift.
- *Duplication* — `ListWidgetConfigureFlowTest.toggleCase` and
  `SingleCaseWidgetConfigureFlowTest.selectCase` were near-identical (same vertical bounds-overlap
  matching against a `ComposeTestRule`, differing only in `isToggleable()` vs `isSelectable()`).
  Extracted into a shared `ComposeTestRule.clickRowControl(caseName, control)` in a new
  `WidgetConfigureTestFixtures.kt`, removing the duplicate implementation and its now-orphaned
  `SemanticsMatcher`/`hasText` imports from both files.
- *Deprecated APIs* — both new configure-flow test files used `createEmptyComposeRule()` (v1,
  deprecated in favor of the `androidx.compose.ui.test.junit4.v2` variant) — new to the codebase
  this pass, not a pre-existing tolerated warning. Migrated both to the v2 import; re-ran the two
  tests 5x on-device afterward specifically to check whether the v2 dispatcher change
  (`UnconfinedTestDispatcher` → `StandardTestDispatcher`) destabilized the hard-won click-matching
  in these tests — it didn't, all 5 runs passed.
- *Tests* — `QuickLogAction`/`StopEventAction` (the widgets' one-tap-log and Stop buttons) had zero
  coverage of any kind, not even unit tests: both need a real `Context` with a live Hilt component
  and a real `WorkManager`, neither available on the JVM, and this repo doesn't use Robolectric or
  a mocking library (precedent: `WidgetLogTrampolineActivity`/`NotificationActionReceiver` in
  `docs/CLEANUP_LOG.md`'s `feature/export-import` entry). Added `WidgetActionsFlowTest`, a real
  `AppWidgetHost`-driven click-through test, discovering along the way that the List widget's
  `LazyColumn` rows can't be exercised this way at all (see Deferred) — the test drives the
  Single-case widget instead, which runs the exact same two callbacks.

**Deferred:**
- The List widget's `LazyColumn` rows are untestable via a bare `AppWidgetHost`: Glance backs them
  with a `RemoteViewsService`/`ListView` adapter that only populates once the host view is attached
  to a real window and laid out, which `AppWidgetHost.createView()` never triggers (confirmed by
  dumping the rendered view tree — the `ListView` had zero children after 10s of polling). Not
  fixed this pass — would need either a real window-attached host or a different test approach
  entirely; documented as a known limitation in `DEV_PLAYBOOK.md` §5 rather than worked around.
- The List widget's Case selection is still the Case-level `pinned` flag rather than
  per-widget-instance selection (each widget picking its own Cases, mirroring the Single-case
  widget) — real redesign (Room migration, ViewModel/Activity rewrite), scoped as its own future
  branch per the previous session's note, not bundled with this bug-fix pass.

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean. `connectedDebugAndroidTest`
(165 tests, including the three widget instrumented test files) passes clean on an emulator — one
run hit an unrelated mid-suite emulator system crash (`INSTRUMENTATION_ABORTED: System has
crashed`), a full clean rerun immediately after passed all 165 with zero failures.

**Docs updated:** DEV_PLAYBOOK.md gained a new §5 "Testing App Widgets" (renumbering the old §5 to
§6) covering the reactive-`provideGlance()` pattern, `android:initialLayout`, targeted
configure-time updates, the `AppWidgetHost` instrumented-test pattern, and its `LazyColumn`
limitation; HODITH_SPEC.md's `pinned` row and §14's New/edit Case row updated to drop the removed
"Pin to widget" toggle; PROGRESS.md's Widgets section rewritten with this pass's outcome and the
remaining per-instance-redesign punch list item.

---

## feature/single-case-widget

**Scope:** Built the Single-case widget (spec §15, deferred from Phase 8) and fixed four
List-widget bugs logged in PROGRESS.md's Widgets section (black background, black-and-white
empty-state font, empty state/title/case row not opening the app), bundled into one branch/PR since
the new widget reuses the List widget's action classes and palette and should launch without
inheriting the same bugs.

Extracted `WidgetPalette`, `MinTapTarget`, `WidgetCornerRadius`, `CaseIdParam`/`EventIdParam`, and
`QuickLogAction`/`StopEventAction` out of `ListWidget.kt` into a new `WidgetCommon.kt` so
`SingleCaseWidget.kt` could reuse them unchanged rather than duplicating; both actions now call a
shared `refreshAllWidgets(context)` (refreshes both `ListWidget` and `SingleCaseWidget`) instead of
only `ListWidget().updateAll()`. Renamed `WidgetRefresher.refreshListWidget()` →
`refreshWidgets()` for the same reason — it's called from `CaseEditViewModel`,
`ArchivedCasesViewModel`, `WidgetLogSheetViewModel`, and the 15-minute `WidgetRefreshWorker`, none
of which should only refresh one widget type once two exist.

The black background/font bugs trace to a real, documented Glance gap: the root `Column`/`Box` set
`.background(...)` but never called `androidx.glance.appwidget.appWidgetBackground()`, the modifier
that marks a view as the App Widget's actual `@android:id/background` for the system's corner-mask
compositing. Added it (plus `cornerRadius()`) to both widgets' roots. Couldn't verify the visual fix
on-device — that's a manual step, added to MANUAL_TEST_PLAN.md's new Widgets section.

The other two bugs (title/empty-state/case-row tap not opening the app) were just missing
`clickable`s — wired to `MainActivity` via `actionStartActivity`, reusing the `EXTRA_CASE_ID` extra
`WidgetLogTrampolineActivity.kt` already defines and `MainActivity.kt` already reads for a Case
Detail deep link (confirmed with the user: a case row/single-case-widget-body tap deep-links to that
Case; the title/empty state open Home).

Single-case widget's Case binding is per-widget-instance Glance `Preferences` state
(`CaseIdKey`/`PreferencesGlanceStateDefinition`), not a Case-level flag like List widget's `pinned`
— each instance gets its own `SingleCaseWidgetConfigureActivity` picker every time it's added (no
"already configured" skip), backed by a new `SingleCaseWidgetConfigureViewModel`
(unit-tested, mirroring `ListWidgetConfigureViewModelTest`) that only tracks the in-progress
selection — the Activity itself owns writing the confirmed Case id into Glance state, since that's
Context-bound infrastructure the ViewModel would otherwise need to fake in tests.

Per user direction, this is an intentional divergence from spec §15's original "tap = log" phrasing
for the Single-case widget: a dedicated `+`/Stop button (matching the List widget's per-row
treatment) logs or stops, and tapping the rest of the widget opens that Case's detail screen —
spec updated to match.

`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean.

**Found & fixed:**
- `WidgetCornerRadius` was first added as a `private val` duplicated identically in both
  `ListWidget.kt` and `SingleCaseWidget.kt` — moved into `WidgetCommon.kt` alongside the other
  already-shared widget constants once the duplication was noticed on the checklist pass.
- Undersized tap targets: the new `ListWidget.kt` title and empty-state clicks were plain `Text`
  composables with a `clickable` modifier and no explicit height — at 13sp with no padding beyond
  the title's original 8dp, both sized to well under the 48dp accessibility minimum the same
  checklist item caught for the `+`/Stop buttons in the original List widget pass (see
  `feature/list-widget` below). Wrapped both in a `Box` sized to `MinTapTarget` (48dp), same fix
  shape as that precedent. `SingleCaseWidget.kt`'s equivalent "Case not found" message didn't need
  the same fix — its `clickable` already spans the widget's full `fillMaxSize()`, well over 48dp
  given the widget's own 60dp minimum size.

`connectedDebugAndroidTest` (160 tests) also runs clean on an emulator: 159/160 passed first try,
the lone failure (`ShareCardTemplateTest.squareNeverShowsTheHunchVsRealityBeatEvenWhenDataProvidesIt`,
untouched by this branch) passed on its own on rerun — the same `ActivityScenario` teardown-timeout
signature (`Activity never becomes requested state [DESTROYED]`) already documented as emulator
flakiness in the `feature/settings-rework` entry below, not a regression. Both `WidgetRefreshWorkerTest`
cases passed. The Widgets section in MANUAL_TEST_PLAN.md still covers what only a human eye can
check (the appWidgetBackground visual fix, launcher-specific corner rendering).

**Deferred:**
- `ListWidgetConfigureActivity` and `SingleCaseWidgetConfigureActivity` share a similar
  `AlertDialog`-based picker shape but weren't merged into one component — the underlying state
  differs enough (multi-select `Set<Long>` writing straight to Case `pinned` vs. single-select
  `Long?` handed back to the caller to write into Glance state) that a shared abstraction would be
  forced for two call sites. Revisit only if a third widget configure flow shows up.

**Docs updated:** HODITH_SPEC.md §15 now describes the Single-case widget's actual `+`-button +
tap-to-open-details interaction instead of the original terse "tap = log"; PROGRESS.md's Widgets
section (all five items resolved) removed entirely; MANUAL_TEST_PLAN.md gained a new Widgets
section — the seed list in TESTING.md had anticipated these widget journeys but they'd never
actually been transcribed in.

---

## feature/settings-rework

**Scope:** Full Settings screen rework, prototyped first as throwaway Compose Previews (compared
card-grouped vs. flat-list layouts, and outlined/filled-tonal/card-row action styles) before being
folded into `SettingsScreen.kt` once a direction was picked. Reorganized the previously flat list
into area cards ("planks" — white `OutlinedCard` with a thin `outlineVariant` border and a
small-caps header, holding up better than the stock tonal-fill `Card` which read as muddy):
**Support** (About, plus placeholder Rate the App/Contact Us rows) pinned above **Appearance**
(Theme picker, live preview card dropped in favor of a tappable info icon reusing the existing
`SectionWithInfo` pattern), **Check-ins** (unchanged, its own label+info icon carries the plank so
there's no duplicate header), **Data** (export/import/delete-all), and a new hidden **Developer
Mode** area holding "Load demo data" (moved out of Data), gated behind a persisted
`developerModeUnlocked` DataStore flag unlocked by tapping the About screen's version row 7 times
— same convention as Android's own Developer Options, with a countdown snackbar on the last few
taps and an "unlocked" snackbar on the last one. New `AboutViewModel` owns the tap-counting/unlock
logic (unit tested); `SettingsRepository`/`DataStoreSettingsRepository`/`FakeSettingsRepository`
gained the matching persistence methods, mirroring the existing
`hasRequestedNotificationPermission`/`setNotificationPermissionRequested` one-way-flag pattern.
Action rows use `FilledTonalButton` — a style not used anywhere else in the app (existing
vocabulary is `Button`/`OutlinedButton`/`TextButton`), deliberately scoped to Settings only per
product-owner call; flagged in PROGRESS.md to revisit. All new/changed Voice strings (Support,
Appearance, Data, Developer Mode headers; Rate the App/Contact Us; Theme's info dialog; the
version-tap countdown/unlocked messages) added to all three voices in this same change.
`ktlintCheck`, `lintDebug`, `test`, and `assembleDebug` all run clean; `connectedDebugAndroidTest`
(160 tests) also runs clean on an emulator, after the fix noted below.

**Found & fixed:**
- Three Voice keys became dead once the live preview card and the Data area's two separate
  sub-headers were removed: `settingsPreviewLabel`, `settingsDemoDataSectionLabel`,
  `settingsBackupSectionLabel`. Deleted from the interface and all three voice objects rather than
  left as unused strings.
- Reusing `voice.caseSectionInfoDescription` for Theme's new info icon means two info icons on the
  same screen now share one content description (Theme's and Check-in's) — confirmed this already
  matches an existing precedent (`CaseEditScreen.kt` reuses the same description across three info
  icons), so it's consistent with the established convention rather than a new problem; the
  instrumented tests target them via `onAllNodesWithContentDescription(...).onFirst()/.onLast()`,
  same technique `CaseEditScreenTest` already uses.
- `SettingsScreenTest.loadDemoData_tapInvokesCallback` failed deterministically the first time it
  ran on an emulator: the Developer Mode plank is the last item in the screen's scrolling Column,
  below the fold on the emulator's screen size, and real-device `performClick()` dispatches an
  actual synthetic touch at the node's on-screen coordinates rather than invoking its semantics
  action directly — so `assertExists()` passed but the click landed nowhere. Fixed by adding
  `.performScrollTo()` before `.performClick()`, the same pattern already used in
  `CaseDetailInsightsTabTest`/`SharePreviewScreenTest` for the same reason. A second, unrelated
  failure (`importButton_opensConfirmDialog_confirmInvokesCallback` timing out waiting for its
  `ActivityScenario` to reach `DESTROYED` during teardown) didn't reproduce on a second full run —
  emulator flakiness, not a code issue.

**Deferred:**
- Whether `FilledTonalButton` should spread app-wide or Settings should conform back to
  `Button`/`OutlinedButton`/`TextButton` — product-owner call to make later, logged in PROGRESS.md.
- Rate the App / Contact Us are coming-soon placeholders until there's a real Play Store listing
  and support channel to point them at — logged in PROGRESS.md.
- The version-tap row's touch target may run a little under the 48dp accessibility guideline —
  low priority since it's a deliberately hidden, non-primary gesture; logged in PROGRESS.md.

**Docs updated:** HODITH_SPEC.md §14's Settings/About rows now describe the area-grouped layout
and the hidden Developer Mode unlock instead of the old flat list and live preview card; TESTING.md's
planned-coverage rows for Compose UI/About updated to match.

---

## feature/app-icon

**Scope:** HODITH's first real app icon: a magnifying-glass mark, iterated through several
color/translucency mockups with the product owner (paled teal field, ink-teal glass ring with a
glass-highlight glint), landing on the "1b" colorway. Built as an Android adaptive icon
(`drawable/ic_launcher_background.xml` + `ic_launcher_foreground.xml`, both hand-authored vector
drawables reproducing the mockup's gradients exactly) plus a themed/monochrome layer
(`ic_launcher_monochrome.xml`) for Android 13+, wired via `mipmap-anydpi-v26/ic_launcher(.xml/
_round.xml)` and `android:icon`/`android:roundIcon` on `<application>`. Added a matching splash
screen theme (`Theme.Hodith.Splash`, MainActivity only) using the platform SplashScreen attributes
directly rather than the AndroidX `core-splashscreen` library, since minSdk 31 already meets the
platform API's own minimum — no library dependency or `installSplashScreen()` call needed. Also
exported portfolio/store assets (icon SVG + 512px PNG, feature graphic SVG + 1024×500 PNG) to the
sibling `icons/HODITH` folder, matching the file-set convention already established in
`icons/EarnIt`. Also set the List Widget's `android:previewImage` to `@mipmap/ic_launcher`
explicitly, rather than relying on the undocumented per-launcher fallback behavior when
`previewImage`/`previewLayout` is omitted — product call was that an icon in the widget picker is
enough, no separate screenshot-style widget mockup needed. `ktlintCheck`, `lintDebug`, `test`, and
`assembleDebug` all run clean (no `IconMissingDensityFolder` or similar lint findings; minSdk 31
means no legacy raster mipmaps are needed at all, adaptive icon alone covers every supported API
level; `ktlintCheck`/`test` are no-ops here since the diff touches no `.kt` files, but were run
rather than assumed).

**Found & fixed:**
- No legacy `mipmap-*dpi` PNG fallbacks added — deliberate, not an oversight: minSdk 31 exceeds
  adaptive icons' own API 26 requirement, so no supported device ever falls back to them.
- First two build attempts failed: `android:Theme.SplashScreen` and `android:postSplashScreenTheme`
  are AndroidX-library-only names, not public platform resources — the platform-only splash
  attributes go directly on a normal theme instead. Documented in a comment so it isn't
  re-attempted later.
- The first `hodith_icon_512.png` export (headless-browser screenshot of the standalone SVG) was
  off-center: the SVG's declared intrinsic size (672×672) didn't match the screenshot window size
  (512×512), so Chromium cropped instead of scaling. Fixed by rendering through a sized HTML shell
  instead of pointing the browser at the bare `.svg` file; the source SVGs themselves were correct
  throughout.
- `ic_launcher_foreground.xml`'s glyph color (`#1F3A40`) happens to match `Color.kt`'s
  `plainDark.primaryContainer` exactly, an inline hex duplicating a named token. Left as-is:
  vector drawables can't reference Kotlin `Color()` constants, the project has no `colors.xml`
  bridging layer, and Android Studio's own generated adaptive-icon templates hardcode hex the same
  way — routing one icon's color through a new shared resource layer for this alone would be the
  kind of premature abstraction CLAUDE.md warns against.

**Deferred:**
- Per-voice icon variants (Intense/Bright) — current read is one static launcher icon regardless
  of in-app voice, matching normal Android convention; flagged to the product owner, not yet an
  explicit decision.

**Docs updated:** PROGRESS.md (struck the resolved app-icon and widget-picker-preview-image items).
DEV_PLAYBOOK.md (struck the Ship Checklist's app-icon line).

---

## fix/case-edit-delete-and-validation

**Scope:** Closed out PROGRESS.md's "Case Edit" section: swapped Edit Case's unclear `ExitToApp`
(door) archive icon for `Delete` (trash can, matching Archived Cases' hard-delete icon) after the
product owner said they'd forgotten what it did; updated the archive confirm-dialog copy in all
three Voices to note that permanent delete is available from Archived Cases; added a 60-char cap
on Case name and 280-char cap on description (silent truncation as you type); added a
case-insensitive duplicate-name check against other active Cases (archived Cases and the Case's own
unchanged name are exempt), blocking Save with a new inline error distinct from the existing
required-name error. `save()` was restructured so validation and the sort-order lookup share one
`observeActiveCases().first()` call inside the existing `viewModelScope.launch`, instead of the old
synchronous pre-launch validation plus a second, separate active-cases query later for sort order.
Walked the full working diff against CLEANUP_CHECKLIST.md; `ktlintCheck`, `lintDebug`, `test` (all
green), and `assembleDebug` run sequentially, clean; `connectedDebugAndroidTest` run in full against
a live emulator (API36_Default) — 152/152, 0 failed.

**Found & fixed:**
- No duplicated composables/strings/ViewModel logic, no stray `android.*` imports, no new hardcoded
  colors, no unused imports (ktlint clean), no new deprecation warnings.
- `HODITH_SPEC.md`'s New/edit Case row was accurate on hard-delete already living only in Archived
  Cases, but silent on the new required/unique/length-capped name and description rules — added
  those. `TESTING.md`'s generic "ViewModels" unit-coverage row didn't call out the new validation;
  added a clause naming it specifically, matching how other rows in that table itemize by feature
  rather than staying generic.
- **`save()` queried active Cases before validating even a blank name or missing icon** —
  previously the blank-name case failed synchronously with no repository call at all. Guarded the
  `observeActiveCases().first()` call behind a cheap `name.isBlank() || icon == null` check so an
  obviously-invalid save short-circuits without touching the repository, same as before this branch.
- **Pre-existing spec drift, caught while reviewing the touched New/edit Case row**:
  `HODITH_SPEC.md` said New/edit Case ends with "the skippable Hunch step," and `TESTING.md`'s planned
  Compose UI coverage listed "Create Case incl. skipping the Hunch step." Neither has ever been true —
  traced the phrase back to the very first spec commit (`243f5cb`), before any code existed; `git log
  -S"HunchStep"` finds no such composable ever built. Hunch creation was actually implemented as the
  nudge card on Case Detail's Hunch tab instead (§7, already documented correctly), so this was the
  original plan superseded by a different design that never got reflected back into the screen-table
  row. Intentional divergence per CLAUDE.md's spec rule → removed the stale clause from both docs
  rather than leaving it open.

**Deferred:**
- `CASE_NAME_MAX_LENGTH`/`CASE_DESCRIPTION_MAX_LENGTH` stay as `internal const` in
  `CaseEditViewModel.kt`, not `domain/`. Raised explicitly with the product owner: CLAUDE.md's
  "product constants live in the domain layer" rule is written for verdict/trigger/stats thresholds
  (confidence tiers, comparison bands, nudge count) — `domain/` is explicitly scoped to "verdict,
  trigger, and stats code." These are UI text-field caps with a single caller, following the same
  file's existing `NO_CASE_ID` precedent; moving them to `domain/` would imply they're product-tunable
  business rules in the way a comparison band is, which they aren't. Confirmed as the intended
  reading of that rule, not left as an unreviewed judgment call.

**Docs updated:** HODITH_SPEC.md (New/edit Case row: name/description validation rules, archive
dialog copy note, stale Hunch-step clause removed), TESTING.md (ViewModels unit-coverage row, stale
Hunch-step clause removed from planned Compose UI coverage), PROGRESS.md (Case Edit section resolved
and removed).

---

## feature/big-picture-polish (Phase 11)

**Scope:** Big Picture follow-ups (PROGRESS.md Phase 11): tapping an event in the day/week detail
dialogs opens that Case's detail screen; those dialogs now show each event's timestamp and tags;
tag filter chips sit alongside the existing case filter chips. Data plumbing needed a Big-Picture-only
Room query (`CaseDao.observeActiveCasesWithEventsAndTags`, nested `@Relation` to `EventWithTags`) so
`CalendarEvent` could carry `id`/`tags` without adding a join to Home/Archived's shared
`CaseWithEvents` query. Walked the full working diff against CLEANUP_CHECKLIST.md; `ktlintCheck`,
`lintDebug`, `test` (all green), and `assembleDebug` run sequentially, clean; `connectedDebugAndroidTest`
scoped to the touched classes also ran clean on an emulator — 14/14 in
`com.secondmonday.hodith.ui.bigpicture` (7 pre-existing plus 7 new, including two added after an
initial pass only covered `DayDetailDialog` and left `WeekDetailDialog`'s shared `EventDetailRow`
usage unproven) and 2/2 for the new `CaseWithEventsAndTagsTest`, which is also what confirmed the
nested `@Relation` actually resolves at runtime, not just compiles.

**Found & fixed:**
- **`BigPictureGrid`'s top-level composable was creeping past ~140 lines** after the tag-filter-row
  addition — extracted the case-chip and tag-chip `FlowRow`s into a private `FilterChipsRow`
  composable, grouping the two related filter UIs together and shrinking the parent function back down.
- **Missing JVM-level coverage for the new tag mapping** — the Room-relation nesting was covered by a
  new instrumented test, but nothing verified `bigPictureUiState`'s pure mapping from
  `CaseWithEventsAndTags` to `CalendarEvent.id`/`.tags`. Added a `BigPictureViewModelTest` case seeding
  `FakeHodithRepository`'s tags/eventTags state directly.
- **`HODITH_SPEC.md` §9 undersold the day/week detail dialogs and case filter chips** — still described
  event rows as "(case, note)" with no mention of time/tags/tap-through, and the filter chips paragraph
  didn't mention the new tag row. Updated both.
- **`TESTING.md`'s Big Picture rows didn't list the new coverage** — added the tag-nesting DAO query
  and the new day/week detail dialog behaviors (timestamp/tags display, tap-to-navigate, tag filter
  hiding untagged events) to the Room DAO and Compose UI — Big Picture rows.

**Deferred:**
- Considered merging `TagFilterChip` and the new read-only `TagPill` (event-row tag badge) into one
  shared composable — their pill/clip/background/border modifier chains rhyme closely. Kept them
  separate: they serve different interaction roles (toggleable filter vs. inline read-only badge) and
  deliberately differ in size, so a merged `onToggle: (() -> Unit)?` parameter would trade a real
  distinction for a smaller diff. Revisit if a third variant shows up.
- `TagFilterChip`'s touch target matches the existing (pre-branch) `CaseFilterChip` sizing, which is
  already below the 48dp guideline — not a new regression introduced by this branch, but Big Picture's
  chips as a whole would benefit from a dedicated accessibility pass if that's ever prioritized.

**Docs updated:** HODITH_SPEC.md §9 (Big Picture detail-dialog and filter-chip description),
TESTING.md (Room DAOs and Compose UI — Big Picture rows), PROGRESS.md (Phase 11 checked off, the
standalone Housekeeping section folded into Phase 12, five new Phase 12 items added from product-owner
notes: Edit Case missing a delete action, New/Edit Case input validation, two Insights-tab layout gaps,
and an Insights-tab usefulness rework).

---

## feature/share-cards (Phase 10, branch 3 of 3)

**Scope:** Share cards end to end (spec §13, PROGRESS.md Phase 10) — `Voice.kt` copy, pure
`shareCardState` assembly, the render pipeline (`FileProvider` + `ComposeShareImageExporter`), a
`ShareViewModel`, the actual `ShareCardTemplate` composable (per-theme chrome via a new
`ShareCardSkin`), `SharePreviewScreen`, and nav/Case-Detail-header wiring. Walked the full working
diff against CLEANUP_CHECKLIST.md; all four DEV_PLAYBOOK.md §3 checks (`ktlintCheck`, `lintDebug`,
`test` — 349/349 — `assembleDebug`) run sequentially, clean both before and after the fixes below,
plus `connectedDebugAndroidTest` scoped to the branch's new/touched instrumented classes (32/32,
API36_Repro emulator) — the very first full-suite attempt overwhelmed that emulator into a pile of
ANRs and a killed test process after ~30 minutes; a scoped rerun against just the touched classes
completed cleanly in ~2 minutes, so the crash was the emulator running out of steam on a long
continuous run, not a product bug.

**Found & fixed:**
- **Inline string bypassing the Voice layer:** Intense's corner stamp hardcoded `"CASE FILE"`
  directly in `ShareCardTemplate.kt` instead of going through `Voice` — added
  `shareIntenseStampLabel` (structural default, like `shareRealityKicker`) and wired it through.
- **UI-toolkit type leaking into the data and viewmodel layers:** `ShareImageExporter`/
  `ComposeShareImageExporter` (`data/share/`) and `ShareViewModel.share()` all took Compose's
  `ImageBitmap` directly. Switched every one to plain `android.graphics.Bitmap` — matching how
  `SettingsViewModel` already takes `android.net.Uri`, not a Compose type — and moved the
  `ImageBitmap.asAndroidBitmap()` conversion to the one Composable call site (`SharePreviewRoute`)
  that actually captured the bitmap.
- **Single-caller composable not earning its keep:** `LaunchedShareRequests` wrapped one
  `LaunchedEffect` for exactly one call site; inlined it into `SharePreviewRoute` and switched its
  key from `viewModel` to `Unit`, matching `SettingsScreen`'s existing one-shot-collection pattern.
- **Story and Square rendered with identical dimensions** — a real gap the product owner caught
  visually (asked "what's the difference?") after the screen first landed: only the top beat choice
  differed, with no shape difference at all. Fixed with per-format `heightIn(min = ...)` (Story ~9:16,
  Square 1:1, both still content-driven beyond their minimum) plus `weight(1f)` on the inner content
  Box so short content still pins the footer to the card's bottom instead of leaving it stranded
  mid-card. The same investigation surfaced a second bug: the `@Preview` functions always built
  `ShareCardData` with the Hunch-vs-Reality beat regardless of format, contradicting
  `shareCardState`'s real "Square never gets it" rule — fixed `previewData(format)` to branch the
  same way the real assembly does, and added a dedicated `ShareCardTemplateTest` regression test.
- **`HODITH_SPEC.md` §13 had drifted from what was actually built** — still described the dropped
  fixed 4-beat "evidence" arc and literal 1080×1920/1080×1080 canvases. Rewritten to describe the
  Hunch vs. Reality/Reality beat choice and the Insights section picker that actually shipped, plus
  the minimum-shape (not fixed-canvas) sizing; §17's "four-beat arc" cross-reference updated too.
- **`VoiceTest` predated this branch** — none of the ~20 new share-card `Voice` keys, including the
  45-branch `sharePunchline`, had coverage. Added exhaustive non-blank assertions for all of them,
  plus a dedicated regression test asserting `sharePunchline` never uses a first/second-person
  pronoun across all three voices — turning a conversation-level design rule (the card is read by
  whoever it's shared with, not the user) into something that can't silently regress on a future edit.
- **`TESTING.md`'s planned-coverage rows still described the old spec's arc** — updated "Share card
  assembly"'s unit-coverage row and added a "Compose UI — Share preview" instrumented-coverage row
  (Story/Square shape difference, checklist/toggle gating).
- **No `MANUAL_TEST_PLAN.md` section existed for the share flow**, even though `TESTING.md`'s
  manual-only seed list already named it — added one covering the parts only a real device can prove
  (actual share-sheet handoff, all three themes through the real pipeline, edited name/section
  choices landing correctly on the exported image).
- `PROGRESS.md`'s existing KT-73255 housekeeping note (Kotlin K2 `@ApplicationContext` forward-compat
  warning) didn't mention `ComposeShareImageExporter.kt`, which now also carries it — updated the list.
- **Real accessibility gap caught by the instrumented run, not just review:** `ToggleRow` (the
  section-checklist rows and the Hunch-vs-Reality switch) made only the tiny `Switch` itself
  clickable — a screen reader would announce it with no indication of what it toggles, since the
  label `Text` and the `Switch` were never merged into one semantics node. Made the whole row
  toggleable (`Modifier.toggleable(role = Role.Switch)`, `Switch`'s own `onCheckedChange` set to
  `null` per Material's guidance for a label+control row) — bigger tap target and a screen reader
  now gets one merged "Rhythm, Switch, on" node. `SharePreviewScreenTest`'s section-toggle test
  needed a `testTag` (an `onNodeWithText` match is ambiguous once the same label can also appear in
  the live card preview above) and a `performScrollTo()` before `performClick()` — the screen is a
  scrolling `Column` and this project's `junit4.v2` testing API needs the target actually reachable,
  not just present in the semantics tree.

**Deferred:**
- The Rhythm mini-section's cells convey their count by shading alone, same as the real
  `RhythmCard`'s already-tracked gap (PROGRESS.md's Housekeeping section). Since the share card's
  Rhythm section is deliberately a faithful mini-copy of the real one, it inherits this rather than
  introducing a new gap — not fixed here; worth revisiting alongside that existing item, not on its own.

**Docs updated:** HODITH_SPEC.md §13 (and a stale §17 cross-reference), PROGRESS.md (Phase 10 branch 3
entry + the KT-73255 housekeeping note), TESTING.md (planned unit + instrumented coverage tables),
MANUAL_TEST_PLAN.md (new Share cards section).

---

## feature/about-screen (Phase 10, branch 2 of 3)

**Scope:** About screen wiring only (spec §14, §16; PROGRESS.md Phase 10) — placeholder copy for
version/privacy/licenses, real content deferred to Phase 12. New `ui/about/AboutScreen.kt`
(`AboutRoute`/`AboutScreen`, stateless, no ViewModel — there's no logic to own), following
`TriggersScreen`'s `Scaffold`/`TopAppBar`/back-arrow pattern. `app/build.gradle.kts` gets
`buildFeatures.buildConfig = true` (previously only `compose` was enabled) so the version section
can read `BuildConfig.VERSION_NAME`. Six new Voice keys (`aboutScreenTitle`,
`aboutVersionLabel`/`aboutPrivacyLabel`/`aboutPrivacyBody`/`aboutLicensesLabel`/
`aboutLicensesBody`) added to the interface and all three voice objects in the same commit; the
back button reuses the existing shared `backButtonDescription` key rather than adding a new one.
Settings gets a new About row (`OutlinedButton`, bottom of the screen) wired through
`SettingsRoute(onOpenAbout)` to a new `about` nav route in `HodithNavHost.kt`, added to the
bottom-bar-hiding detail-screen check alongside Triggers/Case Detail/Archived Cases. Walked the
full working diff against CLEANUP_CHECKLIST.md; all four DEV_PLAYBOOK.md §3 checks (`ktlintCheck`,
`lintDebug`, `test`, `assembleDebug`) run sequentially, clean.

**Found & fixed:**
- An early draft of `AboutSection` took an unused `voice: Voice` parameter (leftover from copying
  the section-header pattern elsewhere) — dropped the parameter and the now-unused `Voice` import
  before it ever landed in a commit.

**Deferred:**
- `connectedDebugAndroidTest` (the new `AboutScreenTest`, plus the updated `SettingsScreenTest`)
  hasn't been run on-device yet — no emulator available in this pass. Per DEV_PLAYBOOK.md §3 this
  is a human step before merge, same as every other branch.
- Real About content (final version/privacy/licenses copy, hosted privacy policy link) — already
  tracked as PROGRESS.md Phase 12's "About screen content polish" item; this branch is deliberately
  wiring-only, matching the Phase 10 scope note.

**Docs updated:** TESTING.md's "Planned instrumented coverage" table gains an About row.
HODITH_SPEC.md §14 already described this screen (version/privacy/licenses) before this branch —
no divergence, so no spec edit needed.

---

## feature/export-import (Phase 10, branch 1 of 3)

**Scope:** Settings export/import JSON (spec §16, PROGRESS.md Phase 10). Room entities annotated
directly with Moshi `@JsonClass` (no parallel DTO layer — export shape is the DB shape); new
`BackupData`/`BackupSerializer` in `data/backup/`; `HodithRepository.exportBackupData`/
`importBackupData`, the Room side running the restore in one `withTransaction` (delete-all then
FK-ordered reinsert: cases/tags → events → event_tags → hunches/triggers) — full replace, not a
merge, so original ids never need remapping. Settings gets a new Backup section (SAF document
picker, no new permissions) with its own destructive confirm dialog before import, and distinct
Voice-driven snackbar messages for export success/failure and three import failure reasons
(invalid file, unsupported schema version, unreadable file), all three voices. `SettingsViewModel`
exposes the real logic as plain-data `performExport()`/`performImport(json)` — a
`BackupFileWriter` seam replaces a direct `Context` dependency specifically so the ViewModel stays
constructible in this project's plain JVM unit tests (no Robolectric, no mocking library here);
the thin `exportData(uri)`/`importData(uri)` wrappers that touch `ContentResolver` are deliberately
untested framework glue, same category as `WidgetLogTrampolineActivity`/
`NotificationActionReceiver`. Walked the full working diff against CLEANUP_CHECKLIST.md, plus all
four DEV_PLAYBOOK.md §3 checks (`ktlintCheck`, `lintDebug`, `test`, `assembleDebug`) run
sequentially, clean both before and after the fixes below — plus a full `connectedDebugAndroidTest`
run against a running emulator (API36_Default AVD): 132/132 instrumented tests green on the final
run.

**Found & fixed:**
- **Real coverage gap for the branch's central risk:** the "all-or-nothing" guarantee (spec §16)
  had a Fake-level test (`FakeHodithRepositoryTest`/`SettingsViewModelTest`) but nothing proving
  rollback actually holds against a real Room transaction — the one place this branch's own logic
  (not just a DAO query) matters. Added
  `RoomHodithRepositoryBackupTest.importBackupData_rollsBackEverythingWhenAnInsertFails`: forces a
  foreign-key violation partway through a restore and asserts the pre-existing data survived
  untouched rather than landing in a half-imported state.
- **Missing `@Smoke` tag:** every existing Dao/repository instrumented test class tags exactly one
  test as its representative happy path (CI's shard-split signal); the new
  `RoomHodithRepositoryBackupTest` didn't. Tagged `exportThenImport_roundTripsEveryTableIntoAFreshDatabase`.
- **TESTING.md's Room DAOs coverage row** didn't mention the new backup round-trip/rollback
  coverage. Added.
- **TESTING.md's manual-seed item 7** carried an open question ("decide and document whether theme
  choice is included") from before this branch existed. Resolved and documented: export/import
  scope is Room data only — Settings prefs (theme, check-in default) are a device preference, not
  investigation data, and are deliberately excluded.
- **Real bug surfaced by the on-device `connectedDebugAndroidTest` run:**
  `RoomHodithRepositoryBackupTest`'s round-trip test seeded its event via
  `repository.insertEvent(...)` — the public wrapper, which fires notification evaluation as a
  fire-and-forget side effect on `applicationScope`. On the emulator, with the test's
  `Dispatchers.Unconfined` scope, that side effect ran eagerly and invoked the test's
  intentionally-throwing `NotificationEvaluator` stand-in, failing the test. This never surfaced in
  the earlier `compileDebugAndroidTestKotlin`-only check since that only compiles, doesn't execute. Fixed by
  seeding via the raw `eventDao.insert(...)` instead, matching how `insertCase`/`insertHunch`/
  `insertTrigger`/`addTagToEvent` (none of which touch notification evaluation) were already seeded
  in the same test — keeps the class's own doc comment ("notification evaluation is never invoked
  here") actually true.

**Deferred:**
- **Kapt-codegen deprecation warning from `hiltJavaCompileDebug`** ("Kapt support in Moshi Kotlin
  Code Gen is deprecated") — Moshi's codegen artifact is wired in via `ksp()` only, and
  `kspDebugKotlin` generates the adapters correctly, so this appears to be Moshi's processor
  printing its warning whenever it's discoverable on any annotation-processing-adjacent classpath,
  regardless of which mechanism actually invokes it. Cosmetic; not chased further within this
  branch's scope.
- **`@ApplicationContext`/K2 "applied to value parameter only" forward-compat notice** on the new
  `ContentResolverBackupFileWriter.kt` — pre-existing warning already present on `Notifier.kt`/
  `WidgetRefresher.kt` before this branch; this file just follows the same established
  `@ApplicationContext` constructor-param pattern. Not new tech debt introduced here.

**Docs updated:** TESTING.md (Room DAOs coverage row, manual-seed item 7's resolved open question),
MANUAL_TEST_PLAN.md (new "Data & backup" section — 5 steps covering the system file-picker/
content-provider boundary the automated tests can't drive), PROGRESS.md (branch checkbox, once
committed).

---

## feature/notification-actions (Phase 9, branch 6 of 6 — phase complete)

**Scope:** Closes out Phase 9. New `notification/NotificationActionReceiver.kt`: a single
`BroadcastReceiver` handling both check-in notification actions — **Log** looks up the Case's
`logFlow` itself and branches (direct `insertEvent` for `ONE_TAP`, launches the existing
`WidgetLogTrampolineActivity` for `DETAIL_SHEET`) rather than deciding at notification-construction
time which kind of `PendingIntent` the action needs; **All quiet** re-arms by updating
`lastCheckInAt`. Both explicit-cancel the notification (`NotificationManagerCompat.cancel`) rather
than relying on `setAutoCancel`, which doesn't reliably dismiss on an action-button tap. Trigger and
check-in notifications' content tap now deep-links to that Case's detail screen — new
`deepLinkCaseId` plumbing threaded `MainActivity` → `HodithApp` → `HodithNavHost`, landing on
`case_detail/{id}` on top of Home rather than changing the start destination. Re-arming a check-in
moved from automatic-at-post-time (a stated stopgap from the previous branch) to explicit-only —
`NotificationEvaluator` no longer touches `lastCheckInAt`; only the All-quiet action or a new event
does — a confirmed design decision, so an ignored check-in now recurs on each ~6h periodic pass
rather than firing at most once per interval. `evaluateAll` collapses 2+ due check-ins into one
`notifyCheckInsSummary` notification (opens Home) instead of firing one per Case; exactly one due
check-in still fires its own per-Case notification with actions. Three new Voice keys
(`notificationLogAction`/`notificationAllQuietAction`/`checkInsSummaryNotificationTitle`), all three
voices. Walked the full working diff against CLEANUP_CHECKLIST.md, plus all four DEV_PLAYBOOK.md §3
checks (`ktlintCheck`, `lintDebug`, `test`, `assembleDebug`) run sequentially — clean both before and
after the fixes below. No instrumented run this pass — no Compose UI or Room changes; the new
receiver is framework glue in the same untested-by-precedent category as `WidgetLogTrampolineActivity`/
`ListWidgetReceiver`.

**Found & fixed:**
- **Real duplication, introduced by this branch's own new call site:** `NotificationActionReceiver`'s
  `ONE_TAP` branch built the exact same `EventEntity(caseId, occurredAt = now, endedAt = null,
  intensity = null, note = null, loggedAt = now)` shape already duplicated in `HomeViewModel.
  quickLogOneTap` and `ListWidget.QuickLogAction` — a third copy of the identical one-tap event
  construction, this time added with full knowledge of the other two (found them while researching
  this branch's own plan). Per this repo's own precedent (`checkin-settings` entry below: citing
  "duplication already existed" isn't a reason to add to it in a portfolio repo), extracted a
  `quickLogEvent(caseId, now)` factory in `data/EventEntity.kt` and switched all three call sites —
  `HomeViewModel`, `ListWidget`, and the new receiver — onto it.
- **Spec drift, the exact kind this pass exists to catch:** HODITH_SPEC.md §11 stated "A Case never
  fires a check-in more than once per its effective interval" — true under the old auto-rearm
  behavior, false the moment this branch moved re-arming to explicit-only (an intentional,
  user-confirmed design decision, not a bug). Reworded to describe the actual re-fire-until-acted-on
  behavior. TESTING.md's "Check-in scheduling" and "Notification evaluation" rows carried the same
  stale "at most one fire per interval" framing and a since-false claim that the evaluator persists
  `lastCheckInAt`; both corrected.
- **Manual-only journeys promoted from aspirational to real:** MANUAL_TEST_PLAN.md's "Notifications &
  permissions" section only covered generic tap-opens-the-app behavior (with an explicit note that
  deep-link/actions/summary weren't buildable yet). Now that they are, expanded to 8 concrete items:
  deep-link tap-through for both notification kinds, Log on both `logFlow` variants, All quiet,
  ignored-check-in re-fire, and summary collapsing.

**Considered, not changed:**
- `NotificationActionReceiver.onReceive` creates a fresh `CoroutineScope(Dispatchers.IO)` per call
  (via `goAsync()` + `pendingResult.finish()`) rather than reusing the injectable app-scoped
  `CoroutineScope` (`di/CoroutineScopeModule.kt`) that `RoomHodithRepository`/`HodithApplication` use
  elsewhere for fire-and-forget work. Not an inconsistency to fix: that shared scope has no way to
  signal completion back to `goAsync`'s `PendingResult` without extra plumbing, whereas a
  receiver-local scope is the standard, Google-documented shape for `BroadcastReceiver` + coroutines
  specifically because a receiver has no lifecycle-scoped `CoroutineScope` of its own — the two
  existing uses of the shared scope are a different problem (ongoing app-lifetime work), not
  precedent this should match.
- The pre-existing Kotlin compiler warning on `SystemNotifier`'s `@Inject constructor` ("this
  annotation is currently applied to the value parameter only... KT-73255") is a forward-compat
  notice tied to the compiler version, not this diff — every `@Inject constructor` in the codebase
  triggers it identically. Not a new warning to resolve here.

**Deferred:** nothing beyond the "Considered, not changed" items above.

**Docs updated:** HODITH_SPEC.md §11 (re-arm-timing sentence). TESTING.md ("Check-in scheduling" and
"Notification evaluation" rows). MANUAL_TEST_PLAN.md (Notifications & permissions section expanded
from 3 to 8 items). PROGRESS.md (Phase 9 branch 6 checked off, Phase 9 marked complete, current-status
paragraph rewritten for the finished notification behavior). This file.

---

## feature/notification-infra (Phase 9, branch 5 of 6)

**Scope:** Triggers and check-ins now evaluate for real. `domain/TriggerEngine.kt`/`CheckIn.kt`'s
evaluation functions widened from `internal` to public for a new `notification/` package:
`NotificationEvaluator` (orchestrates both engines against real repository data, persists
`armed`/`lastFiredAt`/`lastCheckInAt`), `Notifier`/`SystemNotifier` (minimal notification — title,
body, tap opens the app), `NotificationChannels`, `NotificationEvalWorker` (~6h periodic, same
plain-constructor + Hilt `@EntryPoint` pattern as `WidgetRefreshWorker`, for the same
`Configuration.Provider`/`HiltTestApplication` conflict reason). `RoomHodithRepository`'s event
mutations now launch immediate per-Case evaluation fire-and-forget on a new app-scoped
`CoroutineScope`. `POST_NOTIFICATIONS` is requested once (first Trigger created or first check-in
enabled) via a new `NotificationPermissionRequestSignal` singleton, collected at the app root; Home
gained a `NotificationsDeniedBanner`. New Voice `Notifications` section, all three voices. Walked
the full diff against CLEANUP_CHECKLIST.md and DEV_PLAYBOOK.md §3's four checks, plus
`connectedDebugAndroidTest` on-device — clean (see note on emulator flakiness below).

**Found & fixed:**
- **Real bug, caught by the new tests, not by inspection:** `NotificationEvaluator`'s `AT_LEAST`
  branch called `HodithRepository.eventsInWindow(caseId, windowStart, now)` — but `eventsInWindow`'s
  range is half-open (`[start, end)`), so an event occurring at exactly `now` was silently excluded
  from its own trigger's count. That's precisely the common case for the immediate-eval hook (the
  event that was just logged). Fixed by passing `now + 1` as the end bound; a regression test
  (`evaluateCase fires an AT_LEAST trigger once its window count reaches threshold`) pinned it.
- **Real duplication:** `NotificationsDeniedBanner` copied `StaleOngoingBanner`'s exact
  `Card`/`Column(padding 12dp, spacedBy 8dp)`/`Text` shape, second occurrence of the same pattern —
  same call as `feature/triggers-screen`'s `NumberStepper` extraction. Extracted
  `ui/common/ActionBanner.kt`; both banners now delegate to it, unchanged rendered output (existing
  `HomeScreenTest`/`CaseDetailScreenTest` coverage of `StaleOngoingBanner` still passes, since they
  assert on text/labels, not structure).
- **Magic numbers:** `Notifier`'s per-Trigger/per-Case notification-ID scheme (`% 100_000`,
  `1_000_000 +`) was inline; named `NOTIFICATION_ID_MODULUS`/`CHECK_IN_NOTIFICATION_ID_BASE`.
- **No app icon exists yet** (open Ship Checklist item) — `Notifier` needs a `setSmallIcon()`
  regardless, so added a minimal `res/drawable/ic_notification.xml` (standard Material bell glyph)
  scoped to notifications only; not a substitute for the real app icon.

**Design decision surfaced mid-branch, corrected with the user:** the plan going in cited
`WidgetRefresher` as precedent for putting the immediate-eval hook inside `RoomHodithRepository`
itself — checking the actual code showed `WidgetRefresher` is called from ViewModels, not the
repository, so the precedent claim was wrong. Went back to the user with the corrected comparison
(repository choke point vs. matching the ~10-call-site ViewModel pattern); repository was
re-confirmed on the merits (harder to forget in a future logging flow), not the false precedent.

**Considered, not changed:**
- `NotificationEvaluator.evaluateCase`/`evaluateAll` each re-fetch `getMostRecentEventForCase` for a
  Case with both a `SILENT_FOR` Trigger and check-ins enabled — one redundant local SQLite read per
  evaluation. Not worth restructuring `evaluateAll`'s two separate iteration passes (by-Trigger, then
  by-Case) to share it; this runs at most once per event log plus every ~6h, on a personal app's
  modest per-case event counts.

**Deferred:**
- Notification content stays intentionally minimal this branch (title + body, tap opens the app
  generically) — richer voice-flavoured content, tap-to-the-right-Case, Log/All-quiet actions, and
  anti-spam summary collapsing are `feature/notification-actions`, the next branch.
- `TESTING.md`'s existing manual-only seed list items #5/#10 (full trigger/check-in notification
  journeys) stay as-is rather than being migrated — they describe the branch-6 end state (deep-link,
  actions, summary), not what's testable yet.
- One instrumented run hit `INSTRUMENTATION_ABORTED: System has crashed` on an unrelated test
  (`ArchivedCasesScreenTest`, untouched by this branch) partway through, after a prior clean 118/118
  run on the same code; the emulator's `package` service stayed dead afterward (`installPackages`
  failures, unreadable test-result XML) until a restart. Emulator-level, not a regression — same
  class of flakiness the `feature/triggers-screen` entry above already documents. Confirmed clean
  (118/118) after the restart.

**Docs updated:** HODITH_SPEC.md reviewed (§11 Triggers/check-ins/notifications, §14 Home row) —
already accurately describes the end state this branch partially builds toward; no changes needed.
TESTING.md (WorkManager row corrected to describe what's actually instrumented-tested vs. unit-tested
per the doc's own JVM-first rule; new "Notification evaluation" row). New `docs/MANUAL_TEST_PLAN.md`
— didn't exist yet despite CLAUDE.md's "create from the seed list... when the first widget/notification
flow lands" (the widget flow landing in Phase 8 didn't trigger it); scoped to only this branch's new
system-process-boundary flows (trigger/check-in notification firing, the permission dialog, the
denied-banner fallback) rather than backfilling the full TESTING.md seed list, which spans unrelated
already-shipped features outside this diff. PROGRESS.md (status line, Phase 9 branch 5 checked off).

---

## feature/triggers-screen (Phase 9, branch 4 of 6)

**Scope:** HTML mockup (`docs/mockups/triggers-prototype.html`) validated first, then the real
Triggers screen: `TriggersScreen`/`TriggersRoute`/`TriggerCreationSheet` (`ui/triggers/`),
`TriggersViewModel`/`TriggerRow` (`viewmodel/`), a new bell icon + `onOpenTriggers` wiring in Case
Detail's header, the `triggers/{caseId}` nav route, and ~24 new `Voice` keys × three voices. Walked
the full working diff against CLEANUP_CHECKLIST.md and all four DEV_PLAYBOOK.md §3 checks, plus
`connectedDebugAndroidTest` on-device (111 tests) — all passed clean after the fixes below. One
instrumented run hit a single unrelated flaky failure (`HomeScreenTest.staleOngoingBanner_...`, an
`ActivityScenario` teardown timeout on an evidently loaded emulator — 28 min run vs. the usual
~5–10); re-ran that class alone (clean, 1m38s) and the full suite again (clean, 111/111) to confirm
it wasn't a regression — `HomeScreenTest` touches nothing this branch changed.

**Found & fixed:**
- **Real duplication:** `TriggerCreationSheet`'s threshold +/- stepper was a near-verbatim copy of
  the inline stepper already in `HunchCreationSheet.kt`. Extracted `ui/common/NumberStepper.kt`;
  both sheets now share it.
- **Two pre-existing tests broke:** `CaseDetailScreenTest`/`CaseDetailInsightsTabTest` call
  `CaseDetailScreen` directly and needed the new `onOpenTriggers` param — added no-op lambdas. Only
  surfaced once `connectedDebugAndroidTest` actually compiled the `androidTest` source set; the
  `test`/`lintDebug`/`assembleDebug` trio doesn't.
- **API assumed from memory, not verified:** the first `TriggersScreenTest` draft used
  `onNode(isToggleable())`, assuming the classic Compose-testing matcher API. This project's Compose
  UI version (1.11.4) only ships the newer, narrower "v2" finder set — no generic `onNode(matcher)`.
  Fixed properly rather than working around it: gave the enable/disable `Switch` an explicit
  `contentDescription` (new `triggerToggleDescription` Voice key, also a real accessibility win),
  then targeted it the same way as every other icon-action in the file.
- **Test coverage gap:** neither the new bell icon nor the pre-existing Edit icon in
  `CaseDetailScreen`'s header had a test verifying they actually invoke their callbacks. Added
  `onEditCase`/`onOpenTriggers` params to `CaseDetailScreenTest`'s content-setter and one new test
  covering both.
- **`@Smoke` over-tagged:** `TriggersScreenTest` had two `@Smoke` tests; `TESTING.md` documents
  `@Smoke` as one representative happy path per class. Trimmed to one (the create flow).
- **Spec left behind:** the Triggers row in HODITH_SPEC.md §14 said "list, create, enable/disable" —
  delete was added intentionally (confirmed with the user) but the spec wasn't updated. Fixed.

**Deferred:**
- The Hunch/`AT_LEAST` Trigger conceptual overlap the user found during exploratory testing —
  logged as a new HODITH_SPEC.md §17 item, left alone pending alpha-testing feedback per the user's
  explicit call. Not a cleanup-pass item; a product decision.

**Docs updated:** HODITH_SPEC.md (§14 Triggers row now mentions delete; new §17 item on the
Hunch/Trigger overlap), TESTING.md (new "Compose UI — Triggers" instrumented-coverage row),
PROGRESS.md (Phase 9 branch 4 checked off).

---

## feature/trigger-data (Phase 9, branch 3 of 6)

**Scope:** Repository-layer follow-up on `TriggerEntity`/`TriggerDao`, which had already been
scaffolded ahead of schedule — entity, DAO, `TriggerKind` enum, and Room's FK cascade-delete
(`onDelete = CASCADE` on `caseId`) all existed from early Case-CRUD work, and `HodithRepository`/
`RoomHodithRepository` already had thin pass-through CRUD (`observeTriggersForCase`,
`getEnabledTriggers`, insert/update/delete). Walked the diff against CLEANUP_CHECKLIST.md and all
four DEV_PLAYBOOK.md §3 checks (`ktlintCheck`, `lintDebug`, `test`, `assembleDebug`) plus the two
touched instrumented test classes (`TriggerDaoTest`, `CaseDaoTest`) re-run on-device via
`connectedDebugAndroidTest` — all passed clean both before and after. Added
`TriggerDao.getById`/`getTriggersForCase` (one-shot suspend variants, mirroring `CaseDao`/
`EventDao`'s existing `getById` pattern) and mirrored both through `HodithRepository`/
`RoomHodithRepository`/`FakeHodithRepository`.

**Found & fixed:**
- **Real latent bug in the test double:** `FakeHodithRepository.deleteCase` only cascaded
  `events`, not `hunches`/`triggers`, unlike Room's real `ON DELETE CASCADE` on all three. Any
  future ViewModel unit test exercising delete-Case against a Case with an active Hunch or Trigger
  would have silently diverged from real app behavior. Fixed to cascade all three; extended
  `FakeHodithRepositoryTest`'s existing cascade test to assert hunches/triggers alongside events.
- **Stale premise going into this branch:** initial research (an Explore sub-agent) reported no
  cascade-delete test existed for Triggers — wrong; `CaseDaoTest.deletingCase_cascadesToEventsHunchesAndTriggers`
  already covers it, added in `feature/trigger-checkin-engine`'s own pass alongside the Trigger
  schema-v5 bump. Verified directly (read the file) before writing what would have been a
  duplicate test, so no redundant coverage landed.

**Considered, not changed:**
- `TriggerDao.observeTriggersForCase` and the new `getTriggersForCase` run the identical SQL query
  text (`SELECT * FROM triggers WHERE caseId = :caseId`) — not consolidated, because Room requires
  separate DAO methods for `Flow`-returning vs. one-shot `suspend` queries; `CaseDao.observeById`/
  `getById` already establish the same paired-method idiom for the identical reason.
- `getTrigger`/`getTriggersForCase` have no callers yet — added ahead of
  `feature/notification-infra`, which will consume them to resolve a fired trigger's Case and
  events. Matches this repo's own precedent of scaffolding data-layer methods a documented future
  branch needs, same as how this branch's own starting point (`TriggerEntity`/`TriggerDao`) was
  scaffolded ahead of schedule during Case-CRUD.
- An `armed`-aware query (e.g. `getFirableTriggers()` filtering `enabled AND armed`) was discussed
  and deliberately deferred to `feature/notification-infra`, the branch that actually consumes it —
  shaping the query around real usage there rather than guessing now.

**Deferred:** nothing beyond the "Considered, not changed" items above.

**Docs updated:** PROGRESS.md (Phase 9 branch 3 checked off with what was actually built). This
file. (HODITH_SPEC.md and TESTING.md needed no changes — no user-facing or spec-level behavior
changed, and TESTING.md's "Room DAOs" row already named CRUD-per-entity and cascade-delete as
planned coverage before this branch touched anything.)

---

## feature/trigger-checkin-engine (Phase 9, branch 2 of 6)

**Scope:** Post-work cleanup pass for the Trigger/check-in evaluation engines, walked against the full
working diff (nothing committed yet) per CLEANUP_CHECKLIST.md, plus all four DEV_PLAYBOOK.md §3 checks
(`ktlintCheck`, `lintDebug`, `test`, `assembleDebug`) run sequentially, plus the existing `TriggerDaoTest`
instrumented suite re-run on-device (the schema bump touches it directly) — all passed clean both before
and after the fixes below. New `domain/TriggerEngine.kt` (`evaluateTrigger`'s shared armed/fired state
machine, `evaluateAtLeast`, `evaluateSilentFor`) and `domain/TriggerDecision.kt`; `TriggerEntity` gained
`armed: Boolean` (schema v4 → v5, no real migration needed pre-release); `evaluateCheckIn`/
`CheckInDecision` added to the existing `domain/CheckIn.kt`. 26 new unit tests across
`TriggerEngineTest`/`CheckInTest`, mirroring `VerdictEngineTest`'s boundary-value style.

**Found & fixed:**
- **Fresh duplication, same shape as a past-flagged one:** `TriggerEngine.kt` and `CheckIn.kt` each
  wrote their own private `daysBetween` (calendar-date diff via `ZoneId`/`ChronoUnit`, to stay correct
  across DST) — two new copies of a calculation that already existed, inline or as a local function, in
  `VerdictEngine.kt`, `InsightsEngine.kt`, and `StatsEngine.kt`. The previous entry below (`checkin-settings`)
  already established that "a past commit accepted this duplication shape" isn't a reason to repeat it in
  a portfolio repo — the fix there was extracting to `domain/CalendarMath.kt`, which already holds
  `DAYS_PER_WEEK`/`DAYS_PER_MONTH`. Applied the same fix here: `daysBetween` now lives in
  `CalendarMath.kt`, and both new files import it instead of redeclaring it. Left `VerdictEngine.kt`/
  `InsightsEngine.kt`/`StatsEngine.kt`'s own inline/local copies untouched — they predate this branch and
  aren't part of its diff; consolidating those too is a separate, deliberate refactor, not something to
  fold into an unrelated branch's cleanup pass.
- **Test-plan gap:** TESTING.md's "Trigger evaluation" planned-coverage row explicitly names "deleted
  events un-fire correctly on next evaluation" as a scenario to cover. The original test set covered
  re-arming via time-based window aging but not the deletion framing specifically. Added
  `evaluateAtLeast re-arms when a previously-counted event is deleted, without waiting for the window to
  age` to make that exact documented scenario explicit, even though it exercises the same code path as
  the aging test (the pure function can't distinguish "removed by deletion" from "removed by aging" —
  both are just a shorter `events` list on the next call).
- **Spec drift:** HODITH_SPEC.md's Trigger field table (§5) didn't mention the new `armed` field. Added a
  row describing it as the edge-trigger state `lastFiredAt` alone can't carry, and tightened
  `lastFiredAt`'s own row (it no longer needs to explain dedupe — `armed` does that job now).

**Deferred:**
- **`VerdictEngine.kt`/`InsightsEngine.kt`/`StatsEngine.kt`'s own `daysBetween` duplication** — pre-existing,
  not introduced by this branch, out of scope for this pass. Worth a dedicated opportunistic pass
  (Housekeeping-style) consolidating all five call sites onto `CalendarMath.daysBetween` in one go, once
  Phase 9 lands.
- **Not visually verified on-device** — N/A this branch; no UI was touched. `TriggerDaoTest` was run
  on-device specifically to validate the schema change, not as a substitute for UI verification (still
  the human's step, per this repo's own working agreement).

**Docs updated:** HODITH_SPEC.md §5 (Trigger table's `armed` row, `lastFiredAt` reworded). PROGRESS.md
(Phase 9 branch 2 checked off with what was actually built). This file.

---

## feature/checkin-settings (Phase 9, branch 1 of 6)

**Scope:** Post-work cleanup pass for the check-in Settings branch, walked against the full working
diff (nothing committed yet) per CLEANUP_CHECKLIST.md, plus all four DEV_PLAYBOOK.md §3 checks
(`ktlintCheck`, `lintDebug`, `test`, `assembleDebug`) run sequentially — all passed clean both before
and after the fixes below. New Settings "Check-ins" section (off/7/14/30-day default, DataStore-backed,
same `SegmentedChoiceRow`/`SectionWithInfo` pattern as Theme); Case Edit's check-in control simplified
from a DEFAULT/CUSTOM/OFF segmented control + custom-days field to a single on/off `Switch`
(`CaseEntity.checkInDays: Int?` → `checkInsEnabled: Boolean`, schema bumped to v4, no real migration
needed pre-release); new pure-Kotlin `domain/CheckIn.kt` resolving a Case's effective check-in interval
(hunch-derived 2×expected-gap, clamped 3–30 days, takes priority over the Settings default) — added and
unit-tested but not yet wired into any UI, since the engine that consumes it lands in the next branch.

**Found & fixed:**
- **Spec drift, two places:** HODITH_SPEC.md §11 still said "at most one fire per `checkInDays` period"
  — the exact field name this branch just deleted. Reworded to "its effective interval." TESTING.md's
  "Check-in scheduling" planned-coverage row still described the old design's "per-case override incl.
  off" (a custom per-case *interval*), which is no longer true — a Case can only toggle check-ins
  on/off now; a custom interval is a `SILENT_FOR` Trigger's job. Reworded the row to state the new
  design and note the priority order (Hunch-derived beats the Settings default).
- **Test gap:** `onCheckInToggle` — the new toggle's entire reason for existing — had no dedicated test;
  the removed `CheckInOption`/`checkInDaysFor` tests it replaced had thorough coverage of the old
  three-way logic, and nothing filled the equivalent gap for the new boolean. Added
  `onCheckInToggle updates state and is persisted on save` to `CaseEditViewModelTest`.
- **Missing planned-coverage line:** TESTING.md's Compose UI row named Settings' existing
  theme/demo-data coverage but not the new check-in interval picker, even though
  `SettingsScreenTest` gained two real tests for it this branch. Added it to the row.
- **Duplicated constants, introduced fresh by this branch, not inherited debt:** this branch's own
  `domain/CheckIn.kt` redeclared `DAYS_PER_WEEK`/`DAYS_PER_MONTH` as private constants that already
  existed, correctly, as private constants in `domain/VerdictEngine.kt` — written in the same
  session, in the same package, one file over from the original. Initially left as "considered, not
  changed," citing `feature/case-stats`' old `MILLIS_PER_MINUTE`/`MILLIS_PER_DAY` entry as precedent
  for accepting this shape of duplication. Wrong on two counts, both flagged by the user: a past
  commit accepting a duplication shape doesn't make repeating it correct, especially in a portfolio
  repo where thoroughness is the point — and the cited precedent turned out to be stale anyway.
  `MILLIS_PER_MINUTE`/`MILLIS_PER_DAY` are no longer duplicated at all; a `domain/TimeConstants.kt`
  was added at some point after that old entry, and every file that used to redeclare them now
  imports from it. Citing a CLEANUP_LOG entry as live justification without checking current code is
  the exact "verify before recommending from memory" trap. Extracted `DAYS_PER_WEEK`/`DAYS_PER_MONTH`
  to a new `domain/CalendarMath.kt` (`internal`, same package, no import needed) and removed both
  private copies.
- `domain/CheckIn.kt` isn't named `CheckInEngine.kt` despite sitting next to `VerdictEngine.kt`/
  `StatsEngine.kt` — deliberate: PROGRESS.md's own plan calls this "a small pure-Kotlin helper," and
  reserves the `CheckInEngine` name for `feature/trigger-checkin-engine`'s actual due-check engine,
  which will consume this helper. Naming it `CheckInEngine.kt` now would collide in spirit with that
  planned file.
- No new `CaseEditScreenTest` coverage was added for the check-in `Switch` itself — matches a
  pre-existing, already-documented scope limit in that file's own class doc ("full field-by-field form
  coverage otherwise remains a separate, pre-existing gap out of scope here"), not a gap this branch
  introduced.

**Deferred:**
- **Not visually verified on-device or emulator** — the new Settings "Check-ins" row packs four
  segmented-button labels ("Off" / "7 days" / "14 days" / "30 days") into one row, one more option than
  the Theme picker's three; whether that wraps or truncates on a narrow phone width is a real open
  question this pass couldn't close (static analysis and Compose UI tests don't catch text-fit issues,
  and manual on-device verification is explicitly the human's step in this workflow, not mine). Please
  eyeball Settings and Case Edit before merging.

**Docs updated:** HODITH_SPEC.md §11 (stale `checkInDays` field-name reference). TESTING.md (Check-in
scheduling row's per-case description; Compose UI row's Settings line). PROGRESS.md (Phase 9 branch 1
checked off; current-status paragraph's Settings sentence extended; branch 1's own description gained
the resolved defaults and the trigger-data branch's already-scaffolded-data-layer note, done in an
earlier pass this session before implementation started). This file.

---

## feature/list-widget (Phase 8)

**Scope:** Post-work cleanup pass for the List widget branch (7 commits), walked against the full
`main...feature/list-widget` diff per CLEANUP_CHECKLIST.md, plus all four `DEV_PLAYBOOK.md` §3
checks (`ktlintCheck`, `lintDebug`, `test`, `assembleDebug`) run sequentially — all passed clean
both before and after the fixes below.

**Found & fixed:**
- **Inline strings bypassing the Voice layer:** `ListWidget.kt`'s "No pinned Cases yet...", "Stop",
  "Today: N", and "Ongoing · X" were hardcoded literals instead of `Voice` keys — even though the
  same file's `homeHeaderTitle` usage one line away already established the correct pattern
  (reference `PlainVoice.xyz` directly, since the widget's chrome is fixed to Plain regardless of
  in-app theme). Added `widgetNoPinnedCasesMessage`, `widgetStopAction`, and `widgetTodayCount(count)`
  to `Voice` (interface + all three implementations); reused the existing `ongoingIndicator(elapsed)`
  key rather than duplicating it.
- **Duplicated, less-correct elapsed-time formatting:** `ListWidget.kt`'s private `elapsedLabel()`
  reimplemented duration formatting using `System.currentTimeMillis()` directly — bypassing the
  `Clock` already injected into `provideGlance` — and only handled minutes/hours, not days, so an
  ongoing Case left running past 24h would render "30h 6m" instead of "1d 6h". Replaced with the
  shared `formatElapsedDuration()` from `viewmodel/OngoingEvent.kt`, fed by the `Clock`-derived `now`
  threaded down through `CaseRow`.
- **Undersized tap targets:** the "+" quick-log and "Stop" targets in `ListWidget.kt` were ~32-34dp
  tall, under the 48dp accessibility minimum — notable on a home-screen widget where a mis-tap has no
  undo (spec §6: widget one-tap events are created silently). Wrapped both in a `Box` sized/height to
  48dp.
- **Icon-only target with no accessible name:** the "+" quick-log target had no `contentDescription`,
  so TalkBack would announce just "+". Added `GlanceModifier.semantics { contentDescription = ... }`,
  reusing the existing `quickLogButtonDescription(caseName)` Voice key already used by Home's
  equivalent row.

**Deferred:**
- `MANUAL_TEST_PLAN.md` still doesn't exist, though CLAUDE.md names this branch's List widget as the
  explicit trigger to create it ("create from the seed list in TESTING.md when the first
  widget/notification flow lands"). Not created this pass — open question on scope (only the 2
  currently-testable widget journeys vs. the full seed list, most of which references unbuilt Phase
  9/10 features). Flagged here so it isn't lost; resolve before Phase 9 starts.
- TESTING.md's coverage tables weren't updated for the new `ListWidgetConfigureViewModelTest`,
  `WidgetLogSheetViewModelTest`, and `WidgetRefreshWorkerTest`. Not treated as a gap: the doc's
  "Planned unit/instrumented coverage" tables are forward-looking targets, not a done-status log
  (its own header: "Current build status lives in PROGRESS.md, not here"), and the existing generic
  "ViewModels" and "WorkManager" rows already cover what these add in kind.

**Docs updated:** PROGRESS.md (Phase 8 marked done and compressed to match the format of other
completed phases; Phase 12 gained the deferred Single-case widget item, spec §15).

---

## test/case-detail-insights-tab

**Scope:** Closed a PROGRESS.md Housekeeping gap: no instrumented Compose UI coverage for Case
Detail's Insights tab. Added `CaseDetailInsightsTabTest.kt` (11 tests) driving `CaseDetailScreen`'s
Insights tab directly, covering the not-enough-data placeholder, all seven stat cards'
presence/absence gating (trend's 8-week boundary, duration/intensity gated on the Case's config
rather than on data presence, tag breakdown), and the two interactive toggles (frequency
granularity, heatmap "show more months"). Extended the previously DAO-test-only `testEvent()`
fixture in `data/TestFixtures.kt` with an optional `id` param, since UI tests build in-memory lists
directly rather than inserting through Room (which normally assigns ids on insert).

**Found & fixed:**
- **Real crash, not caught until run on-device:** every fixture event defaulted to `id = 0` (via
  `testEvent()`, which never exposed an `id` param since DAO tests always insert through Room and
  let it assign one). The Log tab's `LazyColumn` keys items by event id, so any test with more than
  one event crashed with `IllegalArgumentException: Key "0" was already used` on the very first
  composition, before the test even reached the Insights tab. Fixed by adding an `id` param to
  `testEvent()` and assigning each fixture a distinct one.
- **Interaction failures from unscrolled targets:** the frequency granularity toggle and the
  heatmap's "show more months" button both sit far enough down the Insights tab's scrollable column
  that `performClick()` alone missed them — no exception, the click just landed on nothing, so the
  state never changed and the follow-up assertion failed instead. Fixed with `.performScrollTo()`
  before each click.
- **Naming consistency:** the new file/class was initially named `CaseDetailInsightsTest`, but it
  drives the same `CaseDetailScreen` composable `CaseDetailScreenTest` already exercises for the
  Log/Hunch tabs — the name read as if a separate "CaseDetailInsights" screen existed. Renamed to
  `CaseDetailInsightsTabTest`.
- **Stale doc-comment risk:** the class KDoc originally quoted PROGRESS.md's housekeeping bullet
  verbatim ("closes the Housekeeping gap PROGRESS.md named: ..."), which would have gone stale the
  moment that bullet was rewritten/removed in this same session. Reworded to describe the test's
  own scope instead of pointing at a list entry that will keep changing.
- **Readability:** `heatmapShowMore_revealsMonthsBeyondTheDefaultThreeMonthWindow` built its
  expected month label from three separate `today.minusMonths(4)` calls, awkwardly line-wrapped by
  ktlint. Extracted a single `earliestMonth` val and a `monthYearLabel()` helper mirroring
  `InsightsTab.kt`'s private formatting.
- **TESTING.md gap:** the instrumented-coverage table had no row for Case Detail's Insights tab —
  the exact gap this branch closes. Added one.

**Deferred:**
- The other five Compose UI instrumented test files (`CaseDetailScreenTest`, `BigPictureScreenTest`,
  `HomeScreenTest`, `SettingsScreenTest`, `ArchivedCasesScreenTest`, `CaseEditScreenTest`) still
  hand-roll their own `CaseEntity`/`EventEntity` construction instead of the shared
  `testCase()`/`testEvent()` builders this branch adopted — each screen's local fixture varies
  enough (different `logFlow`/`durationMode`/`intensityEnabled` combos) that converting them now
  wouldn't remove much duplication, and touching five passing, unrelated test files wasn't this
  branch's job. Logged as its own PROGRESS.md Housekeeping item.
- Boundary values (2-event minimum, 56-day trend span, 3-month heatmap default) are restated as
  local test constants rather than imported from `domain/InsightsEngine.kt`/`StatsEngine.kt`,
  because those constants are `internal` and this module's Gradle config gives `androidTest` no
  friend-path visibility into `main`'s `internal` declarations (confirmed no existing instrumented
  test does this either). Matches the precedent `CaseDetailScreenTest` already set with its own
  hardcoded 24h stale-event threshold — adding friend-paths for this alone would be a build-config
  change disproportionate to the value.

**Docs updated:** TESTING.md (new instrumented-coverage row); PROGRESS.md (Housekeeping: removed
the now-resolved Insights-coverage item, added the deferred fixture-duplication item).

---

## feature/case-stats (Phase 7, branch 2 of 2)

**Scope:** Spec §10's seven stat sections, filling in the rest of Case Detail's Insights tab on top
of branch 1's dot timeline/heatmap: frequency over time (auto-picked granularity + a user-overridable
Day/Week/Month toggle), rhythm heatmap, gaps & clusters (extends `GapStats` with average gap and a
"tends to come in bursts" flag), trend arrow (30-day rolling comparison, hidden below 8 weeks of
history), conditional duration/intensity stats, and tag breakdown. New `domain/Stats.kt` +
`domain/StatsEngine.kt` (pure, mirroring `InsightsEngine.kt`'s constants-plus-functions pattern);
`viewmodel/InsightsTabState.kt` extended with `StatsSections` and its per-card display models;
`ui/casedetail/InsightsTab.kt` grew seven new card composables. Also added a Log tab summary line
(total events, observation span) reusing `observationSpanDays`, and extracted Case Edit's private
`SectionWithInfo` into a shared `ui/common/SectionWithInfo.kt` component to reuse its info-icon
pattern for the frequency chart's explanation dialog. Several rounds of user-driven fixes and
refinement on top of the initial build: the frequency chart's per-bucket count labels moved from a
separate row above the bars (visual clutter, especially with zero-count buckets) to sitting directly
above each bar's own top edge; the intensity distribution was rewritten from bars to shaded squares
after a real bug surfaced (see below); and the heatmap/rhythm/intensity shading scale grew from 4 to
10 tiers for finer color distinction once a user noticed adjacent-but-different counts rendering
identically.
**Found & fixed:**
- **Real rendering bug, found by the user on-device, not caught by any test:** the intensity
  distribution's bar `Box` never got a `.fillMaxWidth()` and had no content of its own — an empty
  `Box` with no width modifier measures to zero width, so every bar was invisible regardless of its
  data; only the "1 2 3 4 5" axis labels beneath them ever rendered. Compounding it, the bars also
  used `Modifier.weight(1f).fillMaxHeight(fraction)`, which doesn't work the way it looks:
  `weight()` gives the child a *tight* height constraint (min = max), and `fillMaxHeight(fraction)`
  can't shrink below that floor, so even with width fixed all five bars would have rendered at the
  same full height instead of scaling to the data. Root cause explained to the user as pure
  Compose-layout logic (not JVM-unit-testable — no instrumented Compose test covers Case Detail's
  Insights tab yet, a pre-existing gap this branch doesn't close either). Fixed by rewriting the
  section as a row of five shaded squares reusing the calendar heatmap's `HeatmapLevel` scale
  instead of bars — sidesteps the whole bar-height class of bug rather than just patching this one
  instance.
- **Duplication:** `InsightsTab.kt`'s new `DurationCard` reimplemented the exact same
  minutes-to-"Xh Ym"/"Xd Yh" bucketing already in `viewmodel/OngoingEvent.kt`'s
  `formatElapsedDuration` (used by the ongoing-event indicator). Extracted the shared bucketing into
  a new `formatMinutesDuration(totalMinutes: Long)`, called by both.
- **Inconsistent/semantically wrong color:** the Gaps & clusters card's "tends to come in bursts"
  badge used `MaterialTheme.colorScheme.error` — every other badge in this screen (Hunch tab's
  "Early days"/tier badges) uses `colorScheme.primary`, and per spec §4's observation-not-judgement
  stance, a bursty pattern isn't an error condition to warn about. Switched to `primary` to match.
- **Magic number duplicating a named domain constant:** `IntensityCard` hardcoded `(1..5)` for the
  intensity scale instead of the `INTENSITY_MIN`/`INTENSITY_MAX` constants `StatsEngine.kt` already
  names for exactly this range. Switched to the constants.
- **Spec drift:** HODITH_SPEC.md §10's tag breakdown line just said "counts per tag," not mentioning
  the total-event-count denominator added after a user request partway through the branch. Updated
  the sentence to match what was actually built.
**Considered, not changed:**
- `InsightsTab.kt` is now 599 lines (up from ~250 after branch 1) — comparable to
  `CaseDetailScreen.kt`'s own 537 lines, and organized into many small (<60-line) focused
  composables rather than a few large ones, so no forced split into e.g. a separate `StatsTab.kt`.
  The dot-timeline/heatmap vs. seven-stat-cards boundary would be a clean split point if this file
  keeps growing, but wasn't done unilaterally as a pure size judgment call with no functional
  motivation.
- Rhythm grid cells convey their count by shading alone, no content description or visible number
  (unlike the calendar heatmap's day-of-month numbers and the intensity squares' level numbers) —
  the same color-only gap `feature/case-insights-visuals`'s entry already flagged and deferred for
  the dot timeline, for the same reason: the cells aren't tappable, so there's no natural place to
  hang a description without inventing new interaction (and doing it properly would mean new `Voice`
  surface for something nobody asked for). Revisit alongside making the grid tappable.
- `MILLIS_PER_MINUTE`/`MILLIS_PER_DAY` are now redeclared as file-private constants in four places
  (`OngoingEvent.kt`, `LogDetailViewModel.kt`, `InsightsTabState.kt`, and this branch's
  `StatsEngine.kt`) rather than one shared constant — matches a pattern that already existed across
  three files before this branch touched any of them; not a new inconsistency this branch
  introduced, and not worth a cross-cutting refactor of pre-existing files to fix as a side effect of
  an unrelated feature branch.
- No instrumented Compose UI test added for the Insights tab's new cards — this branch's own bug
  (above) was found manually, on-device, by the user; TESTING.md's instrumented-coverage plan doesn't
  name Case Detail's Insights tab yet, so this isn't a regression in existing coverage, but it is a
  real gap for a screen with this much new pure-rendering logic. Flagged rather than silently
  accepted; no emulator was available this session to add and run one.
**Deferred:** nothing beyond the "Considered, not changed" items above.
**Docs updated:** PROGRESS.md (Phase 7 fully checked off, branch 2 description, current-status line).
HODITH_SPEC.md §10 (tag breakdown's total-count denominator). This file.

---

## feature/case-insights-visuals (Phase 7, branch 1 of 2)

**Scope:** Spec §9's visuals half of Case Detail's Insights tab, replacing the Phase-6 placeholder —
a full-width dot timeline (primary, current-gap annotation) and a per-case calendar heatmap
(secondary, multi-month). New: `domain/Insights.kt` + `domain/InsightsEngine.kt` (pure
`computeTimelineWindow`, `computeGapStats`, `groupEventsByDay`, `heatmapLevelFor`, mirroring
`VerdictEngine.kt`'s constants-plus-pure-functions pattern); `domain/CalendarGrid.kt` (`weeksInGrid`
promoted out of `ui/bigpicture/BigPictureGrid.kt` so both features share one Monday-start grid
generator); `viewmodel/InsightsTabState.kt` (`insightsTabState`, mirroring `HunchTabState.kt`'s
pure-mapping style); `ui/casedetail/InsightsTab.kt` (new file, `CaseDetailScreen.kt` was already
500+ lines). Two rounds of user-driven UX refinement on top of the initial build: (1) the calendar
heatmap now shows 3 months by default with a themed "show more/fewer" toggle, and cells carry their
day-of-month number for readability and as a real accessibility improvement (color is no longer the
only way to read a cell); (2) the dot timeline collapses same-day events into one dot shaded by the
same 4-level scale as the heatmap, instead of drawing overlapping dots, so "darker" means the same
magnitude in both visuals. `DemoDataSeeder.kt` also gained a deliberate recent logging surge (Coffee)
and a deliberate quiet spell (Lost my keys) after checking the existing seed data against the new
logic found neither the timeline's window-shrink nor its "longest stretch since it started" gap note
was ever exercised by any of the six seeded Cases.
**Found & fixed:**
- **Duplication:** `DotTimelineCard` and `CalendarHeatmapCard` each opened with the identical
  `Card(fillMaxWidth) { Column(padding(16.dp), spacedBy) }` shell — the same shape
  `feature/hunch-flow`'s own cleanup pass already named and fixed once for the Hunch tab's cards,
  now reappearing in a new file since `CaseDetailScreen.kt`'s private `HunchCard` isn't visible
  outside that file. Extracted a matching private `InsightsCard(content)` wrapper in
  `InsightsTab.kt`; both cards now build on it.
- **Stale-comment risk:** `DemoDataSeeder.kt`'s surge-sizing comment restated `TIMELINE_MAX_DOTS`'s
  current value ("cap (24 — see domain/InsightsEngine.kt)") inline — exactly the self-updating-tally
  pattern this checklist's Dead Code & Hygiene section warns about. If the constant ever changes,
  the comment would silently go stale. Removed the restated number, kept the pointer to the
  authoritative constant.
- **Test gap:** the two new `DemoDataSeeder` behaviours (`recentSurge`, `quietSpell`) existed only
  to guarantee two specific downstream states, verified only via a throwaway probe test deleted
  after use — no permanent regression coverage. Added two cases to the existing
  `DemoDataSeederTest.kt`: Coffee's recent window clears `TIMELINE_MAX_DOTS`, and Lost my keys'
  current gap exceeds SPARSE's own `maxGapDays`, so a future change to either seed can't silently
  stop exercising the states they exist for.
**Considered, not changed:**
- The dot timeline's axis captions ("5 weeks ago" / "Today") and the heatmap's weekday letters,
  month labels, and day numbers stay outside the `Voice` layer — matching `feature/big-picture`'s
  own precedent for exactly this call ("calendar/data-visualization chrome... not
  personality-flavored narrative copy"), and avoiding a real layout risk: these are tight,
  fixed-width captions in the mockup-validated layout, and Intense/Bright's usual phrasing is
  often longer than Plain's (e.g. "the coldest the trail has ever run" vs. "the longest stretch
  since it started"), which the gap note has room for but a two-word axis label doesn't.
- `CalendarHeatmapCard`'s `expanded` toggle uses plain `remember`, not `rememberSaveable` — won't
  survive rotation/process death. Same reasoning `feature/big-picture`'s entry already gave for its
  own transient UI state (`selectedDay`/`showMonthPicker`/etc.): not a big form investment, accepted
  for now.
- `TESTING.md`'s "Stats & visual data prep" planned-coverage row already named "calendar heatmap
  day-bucketing" and "gap calculations" — both now covered (`InsightsEngineTest`,
  `InsightsTabStateTest`) — but per this file's standing-reference convention (rows describe ground
  a layer is meant to cover and aren't pruned once satisfied — the Verdict engine row reads the same
  today as before Phase 6 shipped it), no edit made. Same call `feature/verdict-engine`'s entry made
  for a same-shaped partial-row match.
**Deferred:**
- The dot timeline's per-dot shading is still color-only, with no textual readout of that day's
  count — the heatmap gained day numbers this pass, but a dot is a fixed 8dp circle with no room for
  a number, and neither dots nor heatmap cells are tappable in this branch (a scope call made with
  the user before building), so there's no tap target to hang a content description on either.
  Matches how Big Picture's own day-cell icons and "+N" badge also aren't individually
  screen-reader-described beyond the cell's own tap semantics. Revisit alongside making either
  visual tappable, which is the more natural place to add a real per-day/per-dot description.
**Docs updated:** PROGRESS.md (Phase 7 restructured into the two-branch format, branch 1 checked
off; current-status line). HODITH_SPEC.md §9 (calendar heatmap paragraph — the 3-month default +
expand behaviour, previously undocumented since it postdates the original spec text). This file.

---

## feature/hunch-flow (Phase 6, branch 2 of 2)

**Scope:** Spec §7's Hunch flow on top of the merged verdict engine — nudge card, Hunch creation
sheet, early-days/verdict cards, resolve + history, and the Case Detail Log/Insights/Hunch tab
restructuring, plus every new `Voice` key across all three voices. New: `domain/VerdictEngine.kt`'s
`HUNCH_NUDGE_EVENT_THRESHOLD` constant; `viewmodel/HunchTabState.kt` (pure `hunchTabState`/
`hunchProgressFraction`, mirroring `homeCaseRows`/`ongoingEventIn`'s pattern); `formatRate`/
`formatExpectedFrequency`/`monthsAgo` formatting helpers in `CaseDetailViewModel.kt`; ~35 new
`Voice` members; `CaseDetailViewModel` wiring (`activeHunch`/`hunchHistory`, `addHunch`/
`resolveHunch`/`dismissHunchNudge`); `ui/casedetail/CaseDetailScreen.kt`'s `SecondaryTabRow`
restructuring and five Hunch-card composables; `ui/casedetail/HunchCreationSheet.kt` (new file).
**Found & fixed:**
- The Hunch tab's five cards (`HunchNoneCard`/`HunchNudgeCard`/`HunchEarlyCard`/`HunchVerdictCard`/
  `HunchHistoryCard`) each repeated the same `Card(fillMaxWidth) { Column(padding(16.dp), spacedBy) }`
  shell. Extracted a private `HunchCard(spacing, content)` wrapper and rewrote all five against it.
- The Hunch-creation sheet's count stepper (`IconButton` wrapping a bare `Text("−"/"+")`) had no
  `contentDescription` — a real accessibility gap (icon-only-equivalent tappable targets with
  nothing for a screen reader to read). Added `hunchCreatingDecreaseCountDescription`/
  `hunchCreatingIncreaseCountDescription` to `Voice` (themed per-voice, matching the tone of
  `caseIconSectionExpandDescription`'s existing precedent) and wired them via `Modifier.semantics`.
- `TabRow` is deprecated in the M3 version this project pins; used `SecondaryTabRow` instead (a
  content-area tab bar under an app bar is exactly what "secondary" tabs are for) — zero deprecation
  warnings in the resulting build.
- `TESTING.md`'s existing "Planned instrumented coverage" row already named "hunch nudge appears at
  5th event and dismisses permanently" as target coverage. Added five `CaseDetailScreenTest` cases
  (none/nudge card gating, dismiss-nudge callback, creation-sheet save, resolve-hunch callback) —
  run on-device (`API36_Repro` AVD), 11/11 passing including the six pre-existing cases (confirming
  the tab restructuring didn't regress the Log-tab flows they cover).
- Manually exercised the full flow (nudge → create → verdict → resolve → history) on-device across
  all three themes (Plain/Intense/Bright) via `adb`/screenshots — every card, badge, and copy string
  rendered as designed; no Compose crashes or blank states.
**Deferred:**
- Seeding a demo Hunch in `DemoDataSeeder` so "Load demo data" showcases the tab out of the box —
  nice-to-have, not in the branch's stated scope; flagged rather than added silently.
- The +/- stepper's glyphs (`"−"`/`"+"`) are literal `Text` content rather than `Voice` members —
  treated as symbols (like the digit display next to them), not language, matching how numeric
  formatting elsewhere in the app (`formatElapsedDuration`, `eventIntensityLabel`'s number) isn't
  themed either. Flagging the reasoning here in case a future reviewer expects every string in the
  file to resolve through `Voice`.
**Docs updated:** None needed beyond this entry and PROGRESS.md's phase checkbox — `HODITH_SPEC.md`
already described this flow (§7) and the Case Detail tab structure (§14) before any code landed;
`TESTING.md`'s coverage map already named the scenario this branch fills in, so its wording didn't
need to change, only the coverage itself.

## feature/verdict-engine (Phase 6, branch 1 of 2)

**Scope:** Spec §8's verdict engine — `domain/Verdict.kt` (types) and `domain/VerdictEngine.kt`
(`computeVerdict` + its three internal pure helpers: `confidenceTierFor`, `observedRateFor`,
`comparisonBandFor`). Pure Kotlin, no Android/Room-behavioural dependencies, no UI — the Hunch-flow
UI that consumes this is branch 2. 30 unit tests targeting every named boundary (event/day
confidence-tier cutoffs, all four comparison-band cutoffs and the value just below each, per-unit
rate normalization, a retro-log predating the Case, a same-instant zero-day window, and a
DST spring-forward crossing verified by temporarily overriding the JVM default timezone).
**Found & fixed:**
- `VerdictEngineTest`'s `hunch()` fixture exposed a `createdAt` parameter no test ever varied
  (the engine keys the observation window off the *Case's* `createdAt`, passed as its own
  argument — the Hunch's `createdAt` field isn't read at all). Removed the parameter, hardcoded
  `0L` inline instead of carrying unused flexibility.
- HODITH_SPEC.md §8's comparison-band notation (`0.5–0.8` etc.) was ambiguous about which band a
  boundary value itself falls into. The implementation resolves it as "boundary belongs to the
  higher band" and tests lock that in; added a clarifying sentence to §8 so the spec states the
  same rule instead of leaving it to be inferred from code.
- ktlint's `chain-method-continuation` rule caught un-wrapped method chains in the test file's
  date-math helper; fixed via `ktlintFormat`.
- One test (`computeVerdict at a DAY expectedPer...`) initially asserted a comparison band over a
  10-day window, which is one of the exact "no verdict yet" cases the engine is supposed to guard
  — the test's assumption was wrong, not the code. Widened the window to clear the Preliminary bar
  so the assertion actually exercises band computation.
**Deferred:**
- TESTING.md's existing "Verdict engine" row also lists direction-aware interpretation
  (`TOO_OFTEN`/`NOT_ENOUGH`/`JUST_CURIOUS`) as coverage for this area. That rendering logic
  deliberately lives in the Voice layer, not the engine (spec §8: "All copy comes from the Voice
  layer") — its tests land with branch 2 (`feature/hunch-flow`), not here. No edit needed now; the
  row will be fully satisfied once both branches are in.
**Docs updated:** HODITH_SPEC.md §8 (comparison-band boundary clarification). TESTING.md and
DEV_PLAYBOOK.md needed no changes — this branch didn't add a new test *category*, just filled in
one the coverage map already planned for.

## feature/big-picture-theme-polish (Phase 5, branch 2 of 2)

**Scope:** Big Picture's bespoke day-cell/badge treatment per theme, on top of `feature/theme-skins`'
skin — Intense's dossier tab-stripe, Bright's shadowed sticker-fan cells. Plain is unchanged. Two
concept variants per theme were mocked up as an HTML artifact (real hex/shape/font values pulled
from `ui/theme/`) and validated with the user before any Kotlin; both themes picked "variant A"
(top tab / sticker fan). Implemented as a small `BigPictureCellStyle` enum + `CompositionLocalProvider`
in `ui/theme/BigPictureDecoration.kt`, mapped from `AppTheme` centrally like
`hodithColorScheme`/`hodithTypography`/`hodithShapes`, so `BigPictureGrid.kt`'s `DayCell` dispatches
on the style rather than branching on the theme directly.
**Found & fixed:**
- The first draft of `IntenseDayCell`/`BrightDayCell` hardcoded `RoundedCornerShape(2.dp)` /
  `RoundedCornerShape(16.dp)` instead of reading `MaterialTheme.shapes.small` — both values already
  exist in `Shape.kt` (`intenseShapes.small` / `brightShapes.small`), so the literals were a
  needless duplicate of the theme's own numbers. Swapped to read from the theme.
- The new dispatcher (`DayCell` → `PlainDayCell`/`IntenseDayCell`/`BrightDayCell`) had zero
  instrumented coverage for the two new branches — `BigPictureScreenTest` only ever exercised the
  default (Plain) `BigPictureCellStyle`. Added `setContent`'s `cellStyle` parameter plus two tests
  (`grid_rendersAndOpensDayDetail_under{Intense,Bright}CellStyle`) confirming the grid still renders
  case/month text and the day-tap → detail-dialog flow still works under each style, matching the
  existing test suite's behavioural (not pixel-level) assertion style.
- **Missing unit coverage, caught when asked directly whether it existed (same failure mode as
  `feature/theme-skins`'s own cleanup entry):** `bigPictureCellStyle(theme)` is a `when(theme)`
  picker function in `ui/theme/` — the exact category `HodithThemeTest.kt` already guards for
  `hodithColorScheme`/`hodithTypography`/`hodithShapes` — but had no test at all. Added
  `BigPictureDecorationTest.kt` next to it, same style: every theme maps to a distinct style, and
  each maps to its own-named one.
**Considered, not changed:**
- The three `*DayCell` composables share a similar `icons.take(MAX_ICONS_PER_CELL).forEach { }`
  shape but render genuinely different structures (tab header vs. shadowed card vs. plain circles);
  collapsing them into one parameterized composable would mostly re-embed the same `when` dispatch
  one level down for no real duplication savings, so kept as three named, independently-readable
  functions instead.
- No color-assertion tests were added for the tab/card treatments themselves (crimson-on-today,
  shadow elevation) — `BigPictureScreenTest`'s existing style only asserts on text/interaction, never
  color, and pixel-level snapshot testing isn't set up in this project; visual correctness was
  instead verified by installing the debug build on the connected emulator and screenshotting Big
  Picture in all three themes with demo data loaded.
**Docs updated:** PROGRESS.md (Phase 5 and its branch 2 checked off, Current status line); TESTING.md
(Big Picture instrumented-coverage row now names the per-theme cell-style dispatch check).

---

## feature/theme-skins (Phase 5, branch 1 of 2)

**Scope:** The theme-skin half of spec §12 — Serious/Goth/Quirky renamed to Plain/Intense/Bright
(mechanical rename, ~125 occurrences / 15 files) and given a real `ColorScheme`/`Typography`/
`Shapes` per theme × light/dark (new `ui/theme/` package, 12 bundled OFL font files), replacing
the bare `MaterialTheme { }` that previously made theme-switching a copy-only change. Direction
validated via an HTML mockup artifact before any Kotlin was written, iterated live with the user
across several rounds (Intense went through a full mid-review rework, gothic-archive → genre
film-noir). Also folded in the Housekeeping list's Home-header item (new `homeHeaderTitle` Voice
key, three phrasings that all spell H-O-D-I-T-H) since it was a natural fit for the same phase.
**Found & fixed:**
- **Test staleness risk:** `AcronymTextTest`'s "real voice phrasings" test originally hardcoded
  copies of the three `homeHeaderTitle` strings as string literals instead of importing
  `PlainVoice`/`IntenseVoice`/`BrightVoice` directly — if the copy changed later without touching
  this test, it would keep passing against a stale duplicate rather than the real production
  strings. Changed to reference the actual `Voice` objects.
- **Missing test coverage, caught when asked directly whether tests existed:** the acronym-mark
  logic (`acronymHighlighted`) was a private, untestable function inside `HomeScreen.kt` with zero
  coverage — extracted to `ui/common/AcronymText.kt` with a dedicated `AcronymTextTest.kt` (4
  tests, including one asserting all three real phrasings mark exactly H-O-D-I-T-H). Also added a
  `HomeScreenTest` assertion for the new header, and a first-ever `SettingsScreenTest.kt` (Settings
  previously had zero instrumented UI coverage, only its ViewModel) covering theme selection, the
  live preview card, demo-data actions, and the delete-all confirm dialog.
- **Self-updating tally in PROGRESS.md:** an earlier draft of this phase's working-roadmap note
  cited "the full 77-test instrumented suite" — exactly the kind of count that goes stale the next
  time a test is added. Reworded to state what was verified without embedding a number that needs
  babysitting.
- **CRLF corruption, caught before anything was staged:** an early global `sed -i` rename pass
  (via git-bash) silently flipped ~82 unrelated `.kt` files from CRLF to LF with zero content
  change, because `core.autocrlf=true` on this repo. `git status` showed ~103 modified files where
  only 17 had a real diff (confirmed via `git diff --name-only`). Restored the 82 noise files with
  `git checkout --` before staging anything; recorded a memory note (outside this repo) so future
  sessions check `git status` vs `git diff --name-only` after any broad `sed` pass.
**Considered, not changed:**
- Full ColorScheme roles beyond what the app actually renders (`tertiary` family, `inverse*`,
  `scrim`, `surfaceTint`, the extra `surfaceContainer` tiers) were left at Material3's baseline
  defaults rather than hand-picked per theme — nothing in the app reads them today, and inventing
  values for roles with no current visual effect isn't worth the six-way maintenance burden it'd
  add to `Color.kt`. Documented in a code comment; revisit if a component starts using one.
- A third-party seed-color → full-tonal-palette generator (e.g. `materialkolor`) would have made
  the 6 `ColorScheme`s more internally consistent than hand-picked hex values, but adding a new
  dependency wasn't something to decide unilaterally mid-implementation — the mockup-validated
  hand-picked values were already signed off, so used those directly instead.
**Deferred:**
- Two mockup flourishes don't have a clean Compose equivalent without threading theme-awareness
  into composables for a purely cosmetic detail: Intense's CSS `text-transform: uppercase` on
  display type, and Bright's alternating accent-pair colors on the header's six initials (all
  themes use a single accent color for the marks instead). Noted in PROGRESS.md; revisit only if
  asked.
- Big Picture's bespoke day-cell/badge treatment per theme — `feature/big-picture-theme-polish`,
  the second of the two branches agreed for this phase, on top of this one.
**Docs updated:** HODITH_SPEC.md §12 (theme table, names, Home header row) and §13 (Intense's
share-card description, previously describing the abandoned gothic-archive look); README's theme
mentions; DEV_PLAYBOOK's widget-theming limitation note; TESTING.md (new Settings row in planned
instrumented coverage); PROGRESS.md (Phase 5 restructured into the two-branch format, Housekeeping
item struck).

---

## feature/settings-foundation (Phase 4)

**Scope:** Settings foundation — a DataStore-backed `AppTheme` (Serious/Goth/Quirky) selection that
now actually drives the pre-existing `Voice` layer app-wide (previously `LocalVoice` was never
overridden anywhere; every screen silently ran on the hardcoded `SeriousVoice` default), plus
promoting demo-data seeding from a debug-only, silent, first-launch-only mechanism
(`debug/SeedDataInitializer.kt` + the `AppInitializer` plumbing) into a real, user-triggered
`DemoDataSeeder` wired to Settings' "Load demo data" / "Delete all data" (confirm dialog) actions,
available in release builds too. New `SettingsRepository` (Preferences DataStore) + `SettingsViewModel`
+ real `SettingsScreen` (segmented theme picker, text-only preview card, demo-data section) replacing
the `ComingSoonPlaceholder`. `HodithApp.kt` is a new small root composable (`CompositionLocalProvider(
LocalVoice provides voiceFor(theme))` wrapping the nav host) that `MainActivity` now renders instead of
inlining `MaterialTheme`/`Surface` itself. Three scope questions were resolved with the user before
building: the default check-in interval from spec §14 is deferred to Phase 9 (the only phase that
consumes it, so building the picker now would be inert UI); the debug auto-seed is retired outright
rather than kept alongside the new manual action; "Load demo data" always adds another full set of 6
(no dedupe/cap), "Delete all data" gets a standard (not type-to-confirm) dialog.
**Found & fixed:**
- **Real correctness bug, found by manual on-device verification, not the automated suite:** after
  wiring `deleteAllData()` to `caseDao.deleteAll()` (relying on the existing cascade FKs to clear
  events/hunches/triggers), a live device check (`sqlite3` against the running app's DB, per
  TESTING.md's documented method) showed the `tags` table still had 21 rows after "Delete all data."
  Root cause: `tags` isn't scoped to a case — it's a shared, case-independent vocabulary (`TagDao`'s
  `observeAllTags()`), so nothing cascades into it when cases are deleted, only the `event_tags`
  join-table rows do (via `eventId`'s cascade). Left uncaught, this would have silently reappeared as
  autocomplete suggestions (leftover demo-data tag names like "oat-milk") for a user who believed
  they'd wiped everything. Added `TagDao.deleteAll()` and called it alongside `caseDao.deleteAll()` in
  `RoomHodithRepository.deleteAllData()`, plus an instrumented regression test
  (`caseDaoDeleteAll_doesNotOnItsOwnRemoveTags`) documenting *why* the explicit second call is needed,
  not just that it's there.
- **Duplication:** `SettingsScreen`'s theme picker started as its own inline
  `SingleChoiceSegmentedButtonRow` block, near-identical to the private `SegmentedChoiceRow<T>` helper
  already living in `CaseEditScreen.kt` (used there for logFlow/durationMode/check-in) — differing only
  in the `enabled` lambda Settings didn't need. Promoted it to a shared
  `ui/common/SegmentedChoiceRow.kt` and switched both call sites onto it, removing the second copy
  before it was ever committed.
**Considered, not changed:**
- `DataStoreSettingsRepository` (the real DataStore-backed impl) has no direct unit test of its own —
  matches the existing precedent that `RoomHodithRepository` also isn't directly unit-tested; ViewModel
  tests exercise the interface via `FakeSettingsRepository` instead (same shape as
  `FakeHodithRepository`), and the real wiring was verified manually on-device (theme selection
  surviving a force-stop + relaunch, isolated from an unrelated debug-APK reinstall data-reset
  artifact encountered mid-verification that turned out to be a signing quirk, not a persistence bug).
- Considered testing `DemoDataSeeder`'s randomized occurrence generation (`spacedOccurrences`,
  `burstyOccurrences`, etc.) more exhaustively now that it's promoted out of `debug/` and into
  production code (`chore/viewmodel-test-infra`'s entry explicitly deferred testing this exact code
  while it lived in `debug/`, citing no wired test-source-set and imminent removal). Added
  `DemoDataSeederTest` covering the invariants that matter for correctness (six distinct cases, every
  event within the seed span, repeated calls are additive) rather than testing the specific
  random-density shapes, which are cosmetic (Big Picture grid variety) not correctness-bearing.
**Deferred:**
- Default check-in interval (spec §14's off/7/14/30 picker) — not built this phase; Phase 9 is the
  only phase that reads it, so a picker now would persist a value nothing consumes. Recorded directly
  in PROGRESS.md's Phase 4 entry, not just here.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as every
  previous entry.
**Docs updated:** PROGRESS.md (Phase 4 checked off with full description, current-status line
updated). HODITH_SPEC.md §14 (Settings row — added "Load demo data" / "Delete all data," previously
undocumented). DEV_PLAYBOOK.md (Ship Checklist's debug-seed-removal item struck — fully retired, not
just fenced). TESTING.md (Room DAOs row — delete-all-cases and the tags-don't-cascade case). This file.

---

## feature/big-picture (Phase 3)

**Scope:** Production wiring for the Big Picture calendar grid — the flagship view spec'd in §9,
already prototyped and validated on-device as `ui/timeline/CalendarGridPrototype.kt` (committed in
`3faa460`, despite PROGRESS.md's stale "uncommitted" note). Promoted that design to production: a new
`BigPictureViewModel` (mirrors `HomeViewModel`'s `combine`/`stateIn` pattern) sourcing
`HodithRepository.observeActiveCasesWithEvents()` — no new repository/DAO query needed, since that same
flow already carries every active case's `createdAt`, so `earliestMonth` is derived reactively as
`min(createdAt)` rather than a separate query. Moved the grid Composable to
`ui/bigpicture/BigPictureGrid.kt`, wired a `BigPictureRoute`/`BigPictureScreen` split into the nav host
(same pattern as `HomeRoute`/`HomeScreen`), and routed every dialog string (month picker, day/week
detail, empty states) through 10 new `Voice` keys added to all three voices. Resolved two product
decisions with the user before building: intensity/duration stay icon-only, never encoded on the grid
(closing spec §9's "still open" question), and the early-days placeholder is two-tier — zero active
Cases shows the same empty state as Home, at least one Case but zero events shows a distinct "not
enough data yet" placeholder.
**Found & fixed:**
- **Duplication:** the three new dialogs (month picker, day detail, week detail) were each a
  near-identical single-dismiss-button `AlertDialog`, differing only in title/content — exactly the
  shape the pre-existing `ui/common/InfoDialog.kt` already covers (previously used once, by Case
  Edit's info icons). Generalized `InfoDialog`'s `body: String` parameter into a
  `content: @Composable () -> Unit` slot plus an optional `dismissLabel` override (Case Edit's "Got
  it" doesn't read right for a list dialog; these use a new "Close"-style `bigPictureDialogCloseAction`
  instead) and switched all four call sites — Case Edit's existing one plus the three new ones — onto
  it, removing three copies of the same `AlertDialog` boilerplate.
- **Accessibility — tap target size + missing action label:** the per-week chevron that opens the
  week-detail view was a 24dp `Text("›")`, under the 48dp minimum, with no accessible label beyond the
  glyph itself (TalkBack would read "greater-than sign", not what it does). Wrapped it in a 48dp `Box`
  with `Modifier.clickable(onClickLabel = ...)` and a new `bigPictureWeekViewDescription` Voice key;
  `WeekdayHeader`'s leading spacer now shares the same `WEEK_CHEVRON_TOUCH_TARGET` constant so the
  header stays column-aligned with the grid below it. The month-title tap target had the same
  under-48dp-height issue (no explicit height, sized to just its `titleSmall` line) — same fix class
  `feature/case-edit-polish` already applied to the icon-picker's collapse header; added
  `Modifier.heightIn(min = 48.dp)` plus vertical centering.
- **Test gap named in TESTING.md's own pre-existing plan, not yet closed:** the "Stats & visual data
  prep" row already called for "Big Picture calendar-grid day-bucketing (month grid boundaries,
  out-of-month days blank not duplicated)" unit coverage, but `weeksInGrid` — the exact function that
  does this — was `private` and untested. Made it `internal` and added `BigPictureGridTest.kt` (5
  cases: 7-day chunking, mid-week month start padding back to Monday, mid-week month end padding
  forward to Sunday, no padding needed when a month already starts/ends on boundaries, every day of the
  target month appears exactly once with no duplicates).
- **Instrumented tests initially failed with `IllegalStateException: No compose hierarchies found`** —
  a physical device had become attached alongside the emulator mid-branch, the exact scenario
  TESTING.md's Known Environment Issues already documents. Not a regression; worked around by targeting
  the emulator directly (`adb -s emulator-5554 ...`) per that doc's existing instructions, which
  themselves needed no changes.
**Considered, not changed:**
- Weekday header letters (`M T W T F S S`) and the day cell's "+N" overflow badge stay inline, not
  routed through `Voice` — calendar/data-visualization chrome (a locale-fixed abbreviation, a count
  badge), not personality-flavored narrative copy, matching how formatted dates and numeric counts
  elsewhere in the app (event-list timestamps, Home's today/week counts) also sit outside the Voice
  layer.
- `CaseFilterChip`'s ~28–32dp height (icon + `labelSmall` text + 6dp padding) is under the 48dp
  tappable-target guideline, but matches Material 3's own `FilterChip` sizing convention (a
  deliberately compact selection control, not a primary action) — this exact chip shape was already
  reviewed and approved during the prototype's own on-device validation before this branch started;
  not re-litigated here.
- `visibleCaseIds`/`selectedDay`/`selectedWeek`/`showMonthPicker` use plain `remember`, not
  `rememberSaveable` — won't survive rotation/process death. Same reasoning `feature/log-detail-sheet`'s
  entry already gave for `LogDetailSheet`'s draft state: transient UI state, not a big form investment,
  accepted for now.
**Deferred:**
- No instrumented test covers the case-filter-chip toggle or the "+N" overflow badge — the chip toggle
  is hard to assert cleanly since the same case icon renders in both the chip label and day cells, and
  the overflow badge needs 4+ same-day events to trigger, meaningfully more setup than the other six
  interaction tests needed. Verified visually during this branch's manual on-device pass instead of by
  an automated assertion. Revisit if either becomes a source of real bugs.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as every
  previous entry.
**Docs updated:** HODITH_SPEC.md §9 (intensity/duration decision resolved from "still open" to
icon-only, and where that data actually lives instead; two-tier early-days placeholder documented).
TESTING.md (day-bucketing row moved from planned-unit to landed, now covered by `BigPictureGridTest`;
Big Picture instrumented-coverage row trimmed to what's actually tested and its "(Phase 3)" tag
dropped). PROGRESS.md (Phase 3 checked off, current-status line updated, stale "uncommitted prototype"
note corrected). This file.

---

## chore/ci-instrumented-tests

**Scope:** Closed PROGRESS.md's last Housekeeping item — instrumented tests (`connectedDebugAndroidTest`)
had never run in CI, only `./gradlew test` (JVM). Added `.github/workflows/instrumented-tests.yml`:
`reactivecircus/android-emulator-runner` on a cached API 36 emulator, sharded via a new class-level
`@UiTest` annotation (`app/src/androidTest/kotlin/.../testtags/UiTest.kt`) into `repository`
(DAO/`CaseWithEventsTest`, not annotated) and `ui` (the four Compose screen test classes, annotated)
matrix jobs — new test classes land in the right shard by tag, no further workflow edits needed. Also
added a method-level `@Smoke` annotation (`testtags/Smoke.kt`) tagging one representative happy-path
test per existing class (10 total), for a quick local sanity run without the full suite. Both this
workflow and `ci.yml`'s existing unit-test job now report total/passed/failed/skipped counts via
`mikepenz/action-junit-report` (Checks-tab annotations + job summary) — a user-requested addition not
present in the reference implementation (EarnIt's own `instrumented-tests.yml`) this branch's shard/tag
approach was otherwise modeled on.
**Found & fixed:** nothing — this is new CI/test-tagging infrastructure with no existing behavior to
regress; every changed test file only gained an import + annotation, no logic touched.
**Checklist pass:** almost every section is N/A — no composables, ViewModels, Repository/Dao logic,
Voice strings, or domain code touched, only CI config, two new test-only annotations, and doc updates.
Two sections did apply and were actually checked:
- **Repo Hygiene:** `git status` reviewed before staging — only the intended files changed/added,
  nothing secret-shaped in the new workflow YAML (no tokens; uses the default `GITHUB_TOKEN` via
  `permissions:`), no stray untracked files.
- **Tests → "new instrumented tests actually ran on a device before committing":** the annotations
  aren't new tests, but the filtering mechanism they enable is the entire point of this branch, so it
  was verified for real rather than assumed from reading the YAML: ran
  `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=...UiTest` (29
  tests, matching a hand count of the 6 DAO/`CaseWithEventsTest` classes),
  `...annotation=...UiTest` (32 tests, matching the 4 Compose screen classes — 29+32=61 with no overlap
  or gap), and `...annotation=...Smoke` (exactly the 10 confirmed tests) — all green on a local
  `Pixel_6(AVD)` API 36 emulator before any of this was committed.
**Deferred:**
- No CI hook (e.g. `workflow_dispatch`) to run just `@Smoke` or one shard on demand from the Actions UI
  — deliberately scoped out during planning; tags exist for local `./gradlew` filtering only for now,
  revisit if that turns out to matter.
- No per-feature annotations (e.g. `@HomeFeature`) beyond `@UiTest`/`@Smoke` — the suite is already one
  test class per package per screen/DAO entity, so `-Pandroid.testInstrumentationRunnerArguments.package=...`
  already isolates any single feature today with zero extra code; adding a redundant tag was considered
  and rejected during planning.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as previous
  entries.
**Docs updated:** PROGRESS.md (Housekeeping item struck — last open item in that section). TESTING.md
(CI coverage section now describes both workflows and the reporting action; new "Instrumented test
tags" subsection documenting `@UiTest`/`@Smoke` and the package-filter alternative). This file.

---

## chore/viewmodel-test-infra

**Scope:** Closed PROGRESS.md's Housekeeping item of the same name — `HomeViewModel`,
`CaseDetailViewModel`, `CaseEditViewModel`, and `ArchivedCasesViewModel` were covered only by
manual/instrumented testing, not isolated JVM unit tests, because no fake repository seam existed
(a decision recorded back in `feature/quick-log`'s entry). Extracted `HodithRepository` from a
concrete `@Singleton class` into an interface, renamed the Room-backed implementation to
`RoomHodithRepository`, and added a `RepositoryModule` `@Binds` (mirroring `ClockModule`'s existing
pattern) — every ViewModel's constructor was already typed against `HodithRepository`, so this was
a DI-binding change only, no ViewModel code touched. Added a hand-rolled `FakeHodithRepository`
(in-memory, `MutableStateFlow`-backed, same no-mocking-library style as the existing `FakeClock`)
and Turbine as a new `testImplementation` dependency for `StateFlow`/`Flow` assertions. Added real
ViewModel-instantiation test suites for all four (24 tests total), and renamed the three existing
`*ViewModelTest.kt` files that only covered co-located pure functions (`CaseDetailViewModelTest` →
`CaseDetailFormattingTest`, etc. — content unchanged, class renamed to match) to free up the
canonical name for the new suites. A pre-work sweep (with the user) confirmed no other current-phase
production code has a comparable test gap; see Deferred.
**Found & fixed:**
- **Test gap in the test infra itself, caught applying this file's own Tests section to the new
  code, not just the ViewModels it was built for:** `FakeClock` — the one existing precedent for a
  hand-rolled fake with real behavior — has its own direct `FakeClockTest`, but the first draft of
  `FakeHodithRepository` (materially more logic: reactive joins for `CaseWithEvents`/
  `EventWithTags`, case-insensitive archived-name sort, tag find-or-create idempotency, cascading
  delete) had none. A bug there could silently mask a real ViewModel bug or produce a false test
  failure in all 24 dependent tests. Added `FakeHodithRepositoryTest` (11 cases) covering the sort/
  join/cascade/idempotency behavior directly.
- **Duplication:** an identical `awaitLoadedItem()` Turbine helper (skips a `stateIn` StateFlow's
  `isLoading = true` default before the first real `combine` emission) was copy-pasted across three
  of the four new test files, differing only in which `UiState` type it was written against.
  Extracted a single generic `TurbineTestSupport.kt` (`ReceiveTurbine<T>.awaitLoadedItem(isLoading:
  (T) -> Boolean)`) in the `viewmodel` test package; all three call sites now share it.
- ktlint reformatted every new/touched test file (multi-arg calls, trailing-lambda placement) —
  caught by actually running `ktlintCheck` before this entry was written, not assumed clean.
**Deferred:**
- **`data/Converters.kt`'s Room `TypeConverter`s remain untested** — each is a one-line
  `enum.name`/`enum.valueOf()` wrapper; a typo would be caught immediately by any DAO instrumented
  test round-tripping through Room. Considered during the pre-work sweep and judged low value for
  the effort, not overlooked.
- **`debug/SeedDataInitializer.kt`'s pure occurrence-generation functions (`spacedOccurrences`,
  `burstyOccurrences`, `endedAtFor`, etc.) remain untested** — also surfaced in the sweep. It lives
  in `app/src/debug` with no `testDebug` source set wired up to reach it (would need a
  `build.gradle.kts` change), and DEV_PLAYBOOK.md's Ship Checklist already slates the whole file for
  removal before first release. Not worth standing up test infra for code on its way out.
- `ui/timeline/CalendarGridPrototype.kt`'s pure helpers (`weeksInGrid`, `monthLabel`) are untested,
  but that file is Phase 3 prototype-stage work for a phase that hasn't started — out of scope.
- `LogDetailViewModel.kt` turned out not to be an actual `@HiltViewModel` (just pure data
  classes/functions shared by `CaseDetailViewModel`, already fully covered by the pre-existing
  `LogDetailViewModelTest`) — a naming artifact, not a gap; no ViewModel instance exists there to
  test. Noted since PROGRESS.md's item didn't name it but the user asked to include it if in scope.
**Docs updated:** PROGRESS.md (Housekeeping item struck). HODITH_SPEC.md and TESTING.md
reviewed — no changes needed (test-infra work; no user-facing behavior or coverage-scope
description changed). This file.

---

## chore/android-lint

**Scope:** Two housekeeping items from PROGRESS.md, bundled onto one branch since the second is
small: (1) Android Lint (`./gradlew lintDebug`) had never been run — only ktlint's style checks
were enforced. Ran it, triaged every finding, fixed what was real and mechanical, and wired it
into CI (plus into CLAUDE.md's documented local workflow, so `lintDebug` runs locally before
`test`, not just discovered in CI). (2) Extracted DEV_PLAYBOOK.md §1's cleanup checklist into its
own document, `CLEANUP_CHECKLIST.md` — it had grown long enough to deserve one rather than living
inline in the playbook; §1 is now a short pointer to it.
**Checklist pass:** a tooling/config/docs-only branch, so most sections don't apply (no
composables, ViewModels, Repository/Dao code, Voice strings, or domain logic touched). Two items
did apply and were actually checked, not assumed:
- **Spec Review:** the `DataExtractionRules` fix (below) touches backup behavior, which brushes
  against HODITH's "local-only" positioning. Checked HODITH_SPEC.md directly — line 284 already
  states "Android auto-backup enabled — documented on the About screen," and the new
  `dataExtractionRules` XML preserves that same default (full cloud-backup + device-transfer)
  rather than changing it, so no spec divergence.
- **Repo Hygiene:** `git status` reviewed before staging each commit — only the intended files
  changed, nothing secret-shaped, no stray untracked files.
**Found & fixed:**
- **`ModifierParameter` ×2:** `CalendarGridPrototype` and `LogDetailSheet` both declared `modifier:
  Modifier = Modifier` after other optional parameters instead of first. Reordered in both (all call
  sites already use named arguments, so no call-site changes needed).
- **`DataExtractionRules` ×1:** `android:allowBackup="true"` alone is deprecated guidance since
  Android 12 (app's `minSdk` is already 31). Added `res/xml/data_extraction_rules.xml` (full
  cloud-backup + device-transfer, matching the prior implicit default) and referenced it via
  `android:dataExtractionRules` in the manifest.
- **`UseTomlInstead` ×10:** the Compose BOM and its modules were declared inline in
  `app/build.gradle.kts` instead of through `libs.versions.toml`, the one exception to how every
  other dependency in the file is declared. Added `compose-bom` and each Compose module as version
  catalog entries (module version left to the BOM, as usual) and switched both `implementation`/
  `debugImplementation`/`androidTestImplementation` blocks to reference them.
**Deferred:**
- **`MissingApplicationIcon` ×1** — left as a live, unsuppressed warning rather than patched with a
  placeholder. Real app icon design is already an open Ship Checklist item ("App icon that works in
  all three theme contexts") gated on actual design work across all three themes, not something to
  paper over from a lint pass.
- **`AndroidGradlePluginVersion` / `GradleDependency` ×6 / `NewerVersionAvailable` ×5 / `OldTargetApi`
  ×1 (13 warnings)** — all "a newer version exists" nags that directly conflict with versions
  deliberately pinned and documented in DEV_PLAYBOOK.md §5 (e.g. Kotlin held at 2.3.20 until KSP
  publishes a matching release; `hilt-navigation-compose` held back from 1.4.0 because it needs
  `compileSdk 37`, not yet installed). Bumping any of these is its own project gated by that
  section's own upgrade checklist, not a lint fix — disabled these specific checks in `app/
  build.gradle.kts`'s new `lint {}` block with a comment pointing back to §5, so CI stays clean
  without silently re-litigating decisions already made and documented.
**Docs updated:** PROGRESS.md (both Housekeeping items struck; DEV_PLAYBOOK/CLEANUP_CHECKLIST
cross-references updated), TESTING.md (CI coverage paragraph — `lintDebug` now part of the
`ci.yml` chain; checklist cross-reference updated), CLAUDE.md (Commands section + Branch/PR
workflow step 3 — `lintDebug` added to the local check sequence), DEV_PLAYBOOK.md (§1 replaced
with a pointer to the new CLEANUP_CHECKLIST.md), this file.

---

## feature/case-edit-polish (Phase 2, slice 3, PR 6 of 6)

**Scope:** A batch of `CaseEditScreen` UX fixes flagged since `feature/case-archive`: the icon picker
becomes collapsible (collapsed by default when editing, expanded for a new Case, with a
label + icon + chevron summary row); Logging, Duration, and Check-in each get a tappable info icon
opening a plain explanatory dialog; Check-in restyles from radio rows to the same segmented-button
pattern Logging/Duration already use; and a validation fix disabling the Logging control's "One tap"
option whenever `durationMode == MANUAL` and/or `intensityEnabled == true` (`START_STOP` is
unaffected — a one-tap Start/Stop is legitimate). Two open design questions from PROGRESS.md were
resolved with the user before building: an existing Case's `logFlow` **auto-switches** to
`DETAIL_SHEET` the moment it becomes invalid (never auto-restores), and the info affordance is a
**tap-to-open dialog** rather than a long-press tooltip (no precedent for either existed in the
codebase; a touch-only app makes long-press a weaker discoverability bet than a visible icon).
**Found & fixed:**
- **Duplication:** the first pass wired Logging/Duration/Check-in's info icons with three
  near-identical `var showXInfo by remember { ... }` + conditional `InfoDialog` blocks in
  `CaseEditForm`, differing only in which `Voice` strings they used — the exact "three copies
  differing only in which Voice strings and callbacks they wire up" pattern `feature/case-archive`'s
  own entry already flagged once for `ConfirmDialog`. Extracted a shared `SectionWithInfo(label,
  infoTitle, infoBody, infoDescription, content)` composable that owns the dialog-visibility state
  once; all three sections now call it, wrapping their own `SegmentedChoiceRow` (plus, for Check-in,
  the conditional custom-days field) as trailing content. Also shrank `CaseEditForm` itself in the
  process (roughly 114 lines → ~102).
- **Accessibility, caught before commit, not by a device check:** the new info `IconButton` was
  built with an explicit `Modifier.size(24.dp)` override to keep the visible glyph small, which also
  shrinks the button's actual touch target below this file's own "≥48dp×48dp" tappable-target rule.
  Fixed by removing the size override from the `IconButton` (keeping M3's default ~48dp touch area)
  and sizing only the inner `Icon` down to 18dp instead. Same reasoning applied to the icon-picker's
  clickable collapse/expand header row, which had no explicit height and could size down to just its
  `Text`'s line height — added `Modifier.heightIn(min = 48.dp)`.
- **Real correctness bug, found only by manual on-device testing, not by the automated test suite:**
  loading an *existing* Case whose stored `logFlow = ONE_TAP` was already invalid under the new rule
  (the debug seed data's `Migraine` case: `START_STOP` duration + intensity on, set before this
  validation existed) rendered the Logging control showing "One tap" as **both checked and
  disabled** — a contradictory state, and one that would have let a user re-save the same invalid
  combination unchanged since nothing re-validated it. The auto-switch logic only ran from the two
  `onDurationModeChange`/`onIntensityToggle` mutators, not from the entity→UI-state mapping used when
  the screen first loads an existing Case. Fixed by running the same `coerceLogFlow` pure function
  inside `CaseEntity.toUiState()` (made `internal` from `private` so it's directly unit-testable),
  closing the gap for any Case whose invalid state predates this branch rather than arising from an
  in-session edit. Two new `CaseEditViewModelTest` cases cover it directly (constructing a
  `CaseEntity` with the seed data's exact stale combination).
**Deferred:** nothing — full field-by-field `CaseEditScreenTest` coverage remains the same
pre-existing gap noted since `feature/case-archive`; this branch added targeted coverage only for the
interactions it introduced (icon-picker collapse/expand, logFlow enable/disable, info dialog
open/dismiss), matching the file's existing scoping rather than expanding it wholesale.
**Docs updated:** HODITH_SPEC.md §14 (New/edit Case row — collapsible icon picker, info icons,
segmented Check-in, the One-tap validation/auto-switch rule); this file; PROGRESS.md (checked off
this branch, Phase 2 slice 3 now fully landed).

---

## feature/case-archive (Phase 2, slice 3, PR 5 of 6)

**Scope:** Archive and hard-delete for Case, plus a new Archived Cases view to manage both — corrected
mid-flight from PROGRESS.md's original archive-only framing (§ this file's own precedent: scope
corrections happen before code, not after). Adds an Archive action + confirm dialog to Case Edit's
header (existing cases only, navigates to Home on confirm); a new Archived Cases screen (reached via a
text link on Home, shown only once at least one Case is archived) listing archived Cases with
immediate Unarchive and a confirm-gated Delete forever (names the event count, cascades to
events/hunches/triggers); `CaseDao.observeArchivedCasesWithEvents` backing both.
**Found & fixed:**
- **Icon availability:** `material-icons-core` (the project's curated ~50-icon set, already known to
  lack a `Stop` icon per `feature/start-stop`'s entry below) also has no Archive/Unarchive/Inventory
  glyph — confirmed by inspecting the actual jar rather than assuming. Substituted `Icons.Filled.ExitToApp`
  (archive, via its `AutoMirrored` variant — the plain one is deprecated in this Compose BOM), `Icons.Filled.Refresh`
  (unarchive), `Icons.Filled.Delete` (hard-delete, already the app's established destructive glyph
  from the event-delete flow). `contentDescription` carries the real meaning in each case, same
  reasoning as the prior `Stop`→`Done` substitution.
- **Duplication:** this branch's two new confirm dialogs (archive case, delete case forever) would have
  been near-verbatim copies of the pre-existing `DeleteEventConfirmDialog` in `LogDetailSheet.kt` — same
  `AlertDialog` shape (title/body/confirm `TextButton`/cancel `TextButton`), differing only in which
  `Voice` strings and callbacks they wire up. Extracted a shared `ConfirmDialog` (`ui/common/ConfirmDialog.kt`)
  and switched all three call sites (including the pre-existing one) to use it, removing three copies of
  the same boilerplate down to one.
- Adding a required `onOpenArchivedCases` parameter to `HomeScreen`/`HomeRoute` (for the new Home link)
  broke the existing `HomeScreenTest.kt` call site at compile time — fixed by threading the parameter
  through with a `{}` default in the test helper, same pattern the file already uses for its other
  callbacks.
- **Composable over the ~150-line guideline:** the archive icon/dialog additions pushed `CaseEditScreen`'s
  main composable to 156 lines (the guideline `feature/start-stop`'s cleanup pass already applied once to
  `LogDetailSheet`). Extracted the form body (every field from name through the save button) into a
  private `CaseEditForm` composable, matching this file's own existing pattern of pulling out
  `IconChoice`/`ToggleRow`/`CheckInOptionRow`/`SegmentedChoiceRow`. `CaseEditScreen` itself is now ~74
  lines (state, top bar, archive dialog, and a single call into the form).
- A deprecation warning surfaced during `./gradlew test`: `Icons.Filled.ExitToApp` is deprecated in this
  Compose BOM in favor of `Icons.AutoMirrored.Filled.ExitToApp`. Switched to the `AutoMirrored` variant,
  matching how `ArrowBack` is already used in the same file.
- ktlint caught an unused `Arrangement` import in `ArchivedCasesScreen.kt` and a wrapped function body
  in `Voice.kt` that fit on one line — both were only caught because `ktlintCheck` was actually run
  before this entry was written, not assumed clean.
**Considered, not changed:**
- `ArchivedCasesViewModel.unarchive` and `CaseEditViewModel.save` both recompute a fresh `sortOrder` via
  the same one-line `repository.observeActiveCases().first().size`. Two occurrences of a one-liner,
  same reasoning `feature/start-stop`'s entry already gave for not sharing `HomeViewModel`/
  `CaseDetailViewModel`'s near-identical `stopEvent`/`dismissStalePrompt` bodies — extracting a helper
  for two one-line call sites is net-negative indirection.
- `CaseDao.observeArchivedCasesWithEvents()` looks like it overlaps with `observeActiveCasesWithEvents()`
  (both are "cases with events, filtered by `archived`"), but the two also differ in `ORDER BY` (`sortOrder`
  vs `name COLLATE NOCASE`) — Room can't parameterize which column to sort by without raw SQL, so a single
  parameterized query would trade a clear intent-revealing name for a con­ditional that's harder to read.
  Left as two queries.
- `ArchivedCaseListItem` and `HomeCaseListItem` share the same icon-then-column-then-trailing-controls Row
  shape, but Home's row also carries the ongoing indicator, ticking elapsed time, and stale-prompt banner
  that Archived Cases has no equivalent of — forcing a shared component would need enough conditional
  slots that it wouldn't read as simpler than two separate, short composables.
**Deferred:**
- No ViewModel-level unit test for `CaseEditViewModel.archive()` or
  `ArchivedCasesViewModel.unarchive`/`deleteForever` — same pre-existing gap already flagged for
  `HomeViewModel`/`CaseDetailViewModel` (PROGRESS.md): no `FakeHodithRepository` seam exists yet, that's
  its own planned dedicated branch. This branch's contribution is covered instead at the pure-function
  layer (`archivedCaseRows`, JVM-only) plus Compose UI tests asserting the callbacks fire correctly.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as previous
  entries.
**Docs updated:** HODITH_SPEC.md §5 (Case table — hard-delete's cascade/irreversibility note) and §14
(Home, New/edit Case, and a new Archived Cases table row). PROGRESS.md (`feature/case-archive` bullet
corrected from archive-only to the actual three-part scope, before any code was written). TESTING.md
(instrumented test count 42→54; new Compose UI coverage bullet for archive/unarchive/delete-forever).

---

## feature/start-stop (Phase 2, slice 3, PR 4 of 6)

**Scope:** Start/Stop + ongoing indicator + 24h prompt (spec §6) — the `START_STOP` duration mode was selectable on Case Edit since `feature/case-crud` but nothing started, stopped, or displayed an ongoing event anywhere. Adds: Start (reuses `quickLogOneTap` for `ONE_TAP` cases; for `DETAIL_SHEET` cases, a new **End** section in `LogDetailSheet` where leaving it "Ongoing" and saving *is* the start action), an always-immediate Stop action (no sheet, regardless of `logFlow`), a live-ticking ongoing indicator on both Home and Case Detail, and the 24h stale-prompt banner (persisted dismissal, re-arms after another 24h) — also on both screens per user decision during planning. New shared pure logic (`viewmodel/OngoingEvent.kt`: `ongoingEventIn`, `isStaleOngoing`, `formatElapsedDuration`) and shared UI (`ui/common/OngoingIndicator.kt`, `ui/common/Ticker.kt`). Schema bump to v3 (`EventEntity.staleNudgeDismissedAt`). Mid-branch, a user-reported gap was also fixed: finished duration events (any mode, not just `START_STOP`) never showed their duration anywhere in the event list, despite it being tracked — added a new `eventDurationLabel` Voice key and duration display in `eventDetailSummary`.
**Found & fixed:**
- **Duplication + complexity, together:** `LogDetailSheet`'s main composable grew to 186 lines (over the ~150-line guideline `feature/log-detail-sheet` had already brought it under once) by adding the end-time date/time pickers as a near-verbatim copy of the existing start-time pickers — same two dialogs, same `applyPickedDate`/`applyPickedTime`/clamp logic, differing only in which `LogDraft` field they read/write. Extracted a shared `DateTimePickers(value, ..., onValueChange)` composable used by both, and pulled the inline tags `Column { Text(...); TagEditor(...) }` block out into a `TagsSection` matching the file's existing `TimeSection`/`IntensitySection` extraction pattern. Brought the main composable down to 156 lines — still a hair over the guideline, but every remaining piece is already its own named section; splitting further would be indirection, not clarity.
- **Dead code:** `HodithRepository.getOngoingEvent` had zero production callers — Home/Case Detail derive ongoing status from the event list they already observe (`ongoingEventIn`), same pattern as `homeCaseRows`'s existing today/week counts, so the repository wrapper was never needed. Removed, matching `feature/log-detail-sheet`'s precedent for `observeEventsForCase`: the underlying `EventDao.getOngoingEvent` primitive stays, since `EventDaoTest` still exercises it directly and it may be useful later (e.g. a Phase 7 widget trampoline wanting a single lookup without the full list).
- **Icon availability:** `Icons.Filled.Stop` doesn't exist in this project's `material-icons-core` dependency (a curated ~50-icon legacy set, not the full Material icon catalog — confirmed by inspecting the actual jar rather than assuming). Used `Icons.Filled.Done` instead (a checkmark reads as "mark this complete," closer to Stop's intent than the only other close alternative, `Close`, which already means "remove" elsewhere in the app, e.g. tag removal). Adding `material-icons-extended` for one icon was considered and rejected — meaningful APK size cost for a single glyph, not part of the plan, and accessibility already carries the real meaning via `contentDescription`.
- **Compose UI test gotcha:** `ExtendedFloatingActionButton`'s text and icon merge into one semantics node, so `onNodeWithText` couldn't find the FAB's label without `useUnmergedTree = true` — cost two failing instrumented tests until diagnosed; documented here so it isn't re-discovered from scratch.
- Removed two incorrect/no-op assertions from an early draft of `CaseDetailScreenTest` (inverted `assertDoesNotExist`/`assertExists` polarity, and a lookup with no assertion at all) before they were ever committed.
**Considered, not changed:**
- `HomeViewModel.stopEvent`/`dismissStalePrompt` and `CaseDetailViewModel.stopEvent`/`dismissStalePrompt` are near-identical one-line `repository.updateEvent(event.copy(...))` bodies, duplicated across the two ViewModels rather than shared. No base ViewModel class exists in the codebase to hang a shared implementation off, and each is a single line — extracting a helper for four one-line functions would be net-negative indirection (CLAUDE.md: "three similar lines is better than a premature abstraction"). Left as-is.
**Deferred:**
- **No ViewModel-level test exercises `stopEvent`, `dismissStalePrompt`, or `onQuickLogTap`'s stop-vs-start routing decision** — same gap `feature/quick-log` and `feature/log-detail-sheet` already flagged for `quickLogOneTap`/`saveEvent`/etc., closed only by the planned dedicated `HodithRepository` interface + `FakeHodithRepository` branch (PROGRESS.md, Phase 2). This branch's contribution to that invariant is fully covered at the pure-function layer instead (`OngoingEventTest`'s `ongoingEventIn`/`isStaleOngoing` cases), plus the Compose UI tests asserting the *callback* fires correctly.
- Retro-log's date/time picker restrictions and tag add/remove/delete-from-sheet still aren't driven by an instrumented test (`CaseDetailScreenTest` covers the start/stop-specific scenarios this branch needed, not the full sheet) — `TESTING.md`'s deferral note updated to reflect the narrower remaining gap rather than closed outright.
- `gradle/gradle-daemon-jvm.properties` remains untracked, unrelated to this branch — same as previous entries.
**Docs updated:** HODITH_SPEC.md §6 (Start/stop, retro-log, and event-list bullets — persisted 24h re-arm behavior, both-screens placement, duration-in-list). PROGRESS.md (slice-3 sub-branch 4 marked done with full description, "Current status" pointer moved to `feature/case-archive`). TESTING.md (Deferrals section narrowed instead of closed outright; Known Environment Issues' API 36 gap marked verified — 42/42 instrumented tests green on an API 36 AVD).

---

## feature/quick-log (Phase 2, slice 3, PR 3 of 6)

**Scope:** One-tap + Home wiring (spec §6, §14) — a per-row quick-log tap target on Home, distinct from the row's own tap: `ONE_TAP` cases insert an Event at `now` immediately with an Undo snackbar; `DETAIL_SHEET` cases open the shared `LogDetailSheet` right from Home. New `Voice` keys for the button description, undo message, and undo action. Also lands the repo's first Compose UI instrumented test (`HomeScreenTest`), and a small drive-by: new Cases now default to `logFlow = DETAIL_SHEET` instead of `ONE_TAP` (`CaseEditUiState`), flagged in PROGRESS.md as upstream context for `feature/case-edit-polish`'s validation-fix bullet.
**Found & fixed:**
- **`LaunchedEffect` key:** the snackbar-collecting effect was keyed on `quickLogUndo` (the Flow instance itself) rather than `Unit`. It happened to work because the Flow reference is stable across recompositions (a `val` on the Hilt-scoped ViewModel), but keying on it signals "restart when this changes" when the actual intent is "collect once for this composable's lifetime." Changed to `LaunchedEffect(Unit)`.
- **Deprecation warning:** `HomeScreenTest` used the deprecated `androidx.compose.ui.test.junit4.createComposeRule()`, flagged at compile time with a note that v2 uses `StandardTestDispatcher` instead of `UnconfinedTestDispatcher`. Switched to `androidx.compose.ui.test.junit4.v2.createComposeRule` and reran all 4 tests on the emulator to confirm the dispatcher change didn't affect the `runOnIdle`/`waitUntil` synchronization already in use — no warning, tests still green.
- **Environment issue found and fixed, not just documented:** Compose UI instrumented tests initially crashed on *every* connected device/emulator with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`, thrown inside Espresso's own startup before any test code runs. Root-caused to the `espresso-core` version `androidx.compose.ui:ui-test-junit4` pulls in transitively; pinned explicitly to 3.7.0 via `libs.versions.toml`, which resolved it.
- **Manual on-device verification surfaced that scripted `adb shell input tap` is not a reliable way to test the Undo snackbar action** — repeated attempts to tap "Undo" via raw coordinates (even ones matching `uiautomator dump`'s reported bounds exactly) consistently failed to register as the snackbar's action click, always resolving via timeout instead. This looked like a real product bug until manual (real finger) testing on-device confirmed one-tap logging and tap-plus-then-Undo both work correctly and the record disappears from the list as expected. No code fix needed; recorded here so a future session doesn't re-spend time suspecting the feature instead of the test tooling.
**Deferred:**
- **No direct unit test exercises `HomeViewModel`'s new repository-calling methods** (`quickLogOneTap`, `openLogSheet`, `saveLogSheetEvent`, `undoQuickLog`) — coverage is `HomeViewModelMappingTest` (pure mapping), `VoiceTest`, and `HomeScreenTest` (which drives the stateless UI contract with stubbed callbacks, not the real ViewModel). This matches the existing precedent set by `CaseDetailViewModel.saveEvent`, which is also untested at the ViewModel level — no fake-`HodithRepository` test pattern exists in the repo yet. Closed the loop for this branch via manual on-device verification (screenshots + direct SQLite queries against the live app DB) instead of introducing that pattern unilaterally. **Decision made, not just deferred:** a dedicated follow-up branch will extract a `HodithRepository` interface + hand-written fake and add JVM unit tests for both `HomeViewModel` and `CaseDetailViewModel` at once — chosen over an instrumented-test alternative specifically because JVM tests already run in CI today. Recorded in PROGRESS.md's Phase 2 section.
- CI wiring for instrumented tests (`connectedDebugAndroidTest`) is explicitly **not** part of this branch — planned as its own `chore/ci-instrumented-tests` follow-up per PROGRESS.md's CI section, since it's tooling/CI work, not the quick-log feature.
- `gradle/gradle-daemon-jvm.properties` remains untracked in the working tree, unrelated to this branch — same as previous entries, its own small branch is planned.
**Docs updated:** PROGRESS.md (slice 3 sub-branch 3 description, CI section's instrumented-tests follow-up plan, `case-edit-polish`'s new default-`logFlow` bullet). TESTING.md (Deferrals section — `HomeScreenTest` stands up the first Compose UI instrumented test instead of `feature/log-detail-sheet`'s screen; new Known Environment Issues entries for the `espresso-core` pin and the scripted-adb-tap limitation, including the workaround for targeting a single device when more than one is attached).

---

## feature/log-detail-sheet (Phase 2, slice 3, PR 2 of 6)

**Scope:** Shared `LogDetailSheet` bottom sheet (time via date/time pickers, intensity, MANUAL duration, note, tag chips with autocomplete), wired into Case Detail's retro-log FAB and tap-to-edit. New `EventWithTags` (Room `@Relation`/`Junction`) so the event list shows each event's own tags without a separate lookup; debug seed data expanded with realistic notes/tags per case; event list rows now show weekday + date (year omitted when current) + time.
**Found & fixed:**
- **Complexity — oversized composable:** `LogDetailSheet`'s body was ~208 lines, over the ~150-line guideline. Split into `SheetHeader`, `TimeSection`, `IntensitySection`, and three dialog composables (`LogDetailDatePickerDialog`, `LogDetailTimePickerDialog`, `DeleteEventConfirmDialog`); the main function is now state + a flat list of section calls.
- **Efficiency — redundant clock reads:** `CaseDetailScreen` called `nowMillis()` three separate times per composition (FAB, each row, each row's edit request). Computed once at the top and reused.
- **Dead code:** `HodithRepository.observeEventsForCase` had zero production callers left once `CaseDetailViewModel` switched to `observeEventsWithTagsForCase` (a strict superset). Removed the repository wrapper; kept the underlying `EventDao.observeEventsForCase` since `EventDaoTest`/`CaseDaoTest` still exercise it directly as a DAO-level primitive.
- **Accessibility — tap target size:** `IntensityChoice` circles were 40dp, under the 48dp minimum (same class of issue as `feature/case-crud`'s icon-picker cells below). Bumped to 48dp via a named `INTENSITY_CHOICE_SIZE` constant, matching `CaseEditScreen`'s `ICON_CHOICE_SIZE` precedent.
- **Compose layout bug (user-reported):** the tag input field chained both `.fillMaxWidth()` and `.weight(1f)` — an anti-pattern that gives the field ambiguous width constraints across measurement passes. This was the root cause of two reported symptoms: typed text not appearing until the field lost focus, and the field's height changing on blur. Fixed by dropping the redundant `fillMaxWidth()`.
- **Keyboard-covers-field bug (user-reported):** the sheet's content `Column` had no `imePadding()` and wasn't scrollable, so fields near the bottom (tags, note) sat behind the keyboard once it opened. Added `.verticalScroll(rememberScrollState())` and `.imePadding()`.
- **Data-integrity bug (user-reported):** saving an edited event under a duration mode with no editable duration control yet (`NONE`/`START_STOP`) silently nulled the event's existing `endedAt`, permanently destroying a real duration logged while the case was still `START_STOP`. Fixed by threading `existingEndedAt` through `LogDraft`; only `MANUAL` mode's control can actually change it now.
- **Missing validation (user-reported):** nothing stopped saving an event with a future `occurredAt`. Fixed with `DatePicker`'s `selectableDates`, a same-day time clamp after the time picker, and an authoritative `coerceAtMost(now)` at save time. `endedAt` from a MANUAL duration is deliberately *not* clamped — a stated/expected duration at logging time ("started now, runs about 2 hours") is legitimate even if it projects past `now`.
- **UX (user-requested):** event delete moved from a per-row icon+dialog in the Case Detail event list to the log sheet's header (top-right, edit-mode only) — keeps the list scannable; delete now lives where you're already looking at the one event.
- **Ordering fix (user-requested):** weekday moved to after the date/time in the event-row timestamp, with a positional regression test (`indexOf` check) so this can't silently flip back.
**Deferred:**
- No Compose UI instrumented test drives the log sheet end-to-end yet (save with retro time, tag add/remove, delete-from-sheet). Covered so far by unit tests for all the pure logic and instrumented DAO tests for the new relation query, but no Compose UI test exists anywhere in the repo yet — deferred until `feature/quick-log` or `feature/start-stop` stands up that pattern for the first time. Logged in TESTING.md's Deferrals section.
- `LogDetailSheet`'s in-progress state (`draft`, `tagInput`, picker visibility) uses plain `remember`, not `rememberSaveable` — won't survive a rotation or process death mid-edit. `LogDraft` isn't trivially Parcelable and this is a quick bottom-sheet action, not a big form investment (contrast with `CaseEditViewModel`'s `SavedStateHandle`-backed state). Accepted for now; revisit if it causes real friction.
- Tags-and-weekday-always-on (no per-user Settings toggle) — deliberately not built now to avoid standing up DataStore/Settings-screen infrastructure early for a display preference that may not need to be optional. Flagged in PROGRESS.md and HODITH_SPEC.md §6 for reevaluation once Settings (Phase 9) gets real content.
- Two more Case Edit screen gaps surfaced during manual testing, out of scope for this branch (different screen, already-merged `feature/case-crud`): Case removal (no archive/delete UI at all) and a batch of form UX fixes (collapsible icon picker, info icons, Check-in restyle, One-Tap `logFlow` option needs disabling when duration is MANUAL and/or intensity is tracked). Both recorded as new planned branches in PROGRESS.md (`feature/case-archive`, `feature/case-edit-polish`) rather than bolted on here.
**Docs updated:** HODITH_SPEC.md (§6 — event list now describes what each row shows, flagged for the Settings-toggle reevaluation). PROGRESS.md (slice-3 sub-branch breakdown updated to reflect the actual 4→6 branch split, two new branches added, tags/weekday note). TESTING.md (Deferrals section — Compose UI gap for the log sheet).

---

## feature/case-crud (Phase 2, slice 2 of 3)

**Scope:** New/Edit Case screen (§14 core fields: name, optional description, icon picker, logFlow, durationMode, intensity toggle, pinned toggle, check-in override), `CaseEntity.description` field + schema bump to v2, Home's FAB (create) and row-tap (edit) wired to it. Skippable Hunch step deliberately out of scope — see Deferred.
**Found & fixed:**
- **Duplication:** the logFlow and durationMode pickers were two near-identical inline `SingleChoiceSegmentedButtonRow` blocks differing only in the enum type and options. Extracted a shared generic `SegmentedChoiceRow<T>` composable.
- **Accessibility — icon-only button:** the screen's back `IconButton` had `contentDescription = null`. That null pattern is correct for the bottom-nav items (precedent in `feature/home-screen`'s entry below) because their visible label already names them — but this back button has no adjacent label, so `null` would leave it unannounced to TalkBack. Added a new `backButtonDescription` Voice key and wired it in.
- **Accessibility — selection conveyed by color alone:** the icon-picker grid and the check-in radio rows only showed selection via a background-color change, invisible to TalkBack. Switched both to `Modifier.selectable(selected, onClick, role = Role.RadioButton)` on the row/cell plus `Modifier.selectableGroup()` on their containers, so selected state is announced. `RadioButton`'s own `onClick` set to `null` (the recommended pattern) to avoid a duplicate, differently-scoped click target on top of the row's.
- **Tap target size:** the icon-picker cells were 44dp, under the 48dp minimum. Bumped to 48dp.
- **Test gap:** `checkInDaysFor` (DEFAULT/CUSTOM/OFF → nullable day count mapping) had no coverage. Made `internal` and added five cases including blank and zero custom-input fallback.
- **Deprecation:** `fallbackToDestructiveMigration()` is deprecated in this Room version; switched to the `dropAllTables = true` overload.
- **Instrumented tests verified:** `CaseDaoTest`'s new `description` round-trip case, plus the full `connectedDebugAndroidTest` and `test` suites, run and passed on a physical device — closes the gap flagged mid-review (DEV_PLAYBOOK §1's "ran on a device" check is separate from "compiles").
**Deferred:**
- **Hunch step:** §14 describes the New/Edit Case flow ending in a skippable Hunch step. Moved to its own follow-up branch — Hunch creation is new surface area (direction/expectedCount/expectedPer) beyond Case CRUD's core fields, and splitting it keeps this PR reviewable. Recorded in PROGRESS.md.
- **Row-tap → Edit Case is interim wiring**, not the final design: Case Detail doesn't exist until `feature/logging-flows`. Documented in PROGRESS.md with the intended end state (row-tap → Case Detail, Edit moves to its header, Home gets its own quick-log button per row).
- **Check-in "Custom" with a blank/zero day count** silently falls back to the same result as "Use default" rather than blocking save with a validation error. Deliberate simplification, not a tracked gap — revisit only if it causes real confusion.
**Docs updated:** PROGRESS.md (slice 2 scope corrected to reflect the Hunch-step deferral, new "Interim UX wiring" note). HODITH_SPEC.md not touched — the target screen design is unchanged, only the build sequencing differs.

---

## feature/home-screen (Phase 2, slice 1 of 3)

**Scope:** First Phase 2 slice — Navigation Compose scaffold (bottom nav: Home · Big Picture · Settings), read-only Home screen wired to real `HodithRepository` data via a new `HomeViewModel`, placeholder Big Picture/Settings destinations, new `Voice` keys, `description` field added to the Case spec (§5, §14) after a product discussion ruled out a separate Case-level valence field.
**Found & fixed:**
- **Duplication:** `BigPictureScreen.kt` and `SettingsScreen.kt` were identical placeholder bodies (Box + centered Text reading `comingSoonPlaceholder`). Extracted a shared `ComingSoonPlaceholder` composable (`ui/common/ComingSoonPlaceholder.kt`); both screens now delegate to it.
- **Accessibility:** `NavigationBarItem`'s `Icon` had `contentDescription` set to the same text as its visible `label`. Since the label is already shown (not an icon-only button), that would make TalkBack announce the name twice. Set `contentDescription = null` with a comment explaining why, per Material accessibility guidance for icon+label nav items.
- **Test gap (caught by a question, not the checklist pass):** `HomeViewModel`'s today/this-week boundary math (`startOfToday`/`startOfWeek` cutoffs) had no unit coverage. Extracted it to a pure top-level `homeCaseRows(casesWithEvents, nowMillis)` function and added `HomeViewModelMappingTest` covering the inclusive/exclusive boundary at both cutoffs, an empty-events case, and identity-field mapping.
- **Dependency pin:** `hilt-navigation-compose` 1.4.0 (and its transitive `androidx.lifecycle` bump) requires `compileSdk 37`, not installed and out of scope for this branch. Pinned to 1.2.0 instead; recorded in PROGRESS.md as a decision to revisit alongside a deliberate SDK bump, not silently.
**Deferred:**
- `HomeViewModel` computes today/this-week counts by filtering each Case's full in-memory event list (from `observeActiveCasesWithEvents()`) rather than using `EventDao.eventsInWindow`'s SQL-side windowing — a literal read of DEV_PLAYBOOK §1's "Dao query duplicated with a Kotlin-side filter" check. Deferred: `eventsInWindow` is a one-shot suspend query, not reactive, and per-case event volumes are small (personal habit-tracking data), so pushing this into SQL isn't worth the added complexity yet. Revisit if event counts per Case grow large enough for this to matter.
- No instrumented Compose UI test added for Home's rendering. TESTING.md's planned Compose UI coverage for Home is bundled with Create Case / one-tap-log / detail-sheet interactions (slices 2–3); writing one now against a read-only screen with no `onClick` handlers risks being rewritten wholesale once those land. Revisit once slice 2 or 3 adds interaction.
**Docs updated:** HODITH_SPEC.md (§5 `description` field, §14 New/Edit Case field list). PROGRESS.md (slice 1 marked done, data-model decision on Case valence recorded, `hilt-navigation-compose` pin documented).

---

## feature/big-picture (Phase 2/3 rescope)

**Scope:** Re-scoping pass after the calendar-grid pivot: swapped Case CRUD and Big Picture phase order (pinch-zoom's de-risking rationale for building Big Picture first no longer applies), deleted the retired row/dot Big Picture screen and its domain math rather than holding it for reuse, and reconciled every doc that still referenced the old design or the old phase numbers.
**Found & fixed:**
- **Dead code:** all row/dot production code and tests deleted — `domain/timeline/{TimelineLayout,TimelineWindowMath,TimelineAxis}.kt`, `ui/timeline/{TimelineGestures,TimelineScreen,TimelineViewModel}.kt`, and their unit/instrumented tests (~1,067 lines). Previously flagged as "candidates for reuse" for Phase 6's per-case dot timeline; resolved as delete-not-hold since it was shaped around a multi-case shared-row layout, so reuse for a single-case screen four phases out was speculative.
- **Decoupling fallout from that deletion:** `Voice.kt`'s `timeRangeLabel(ZoomLevel)` removed (no zoom levels left to label), `VoiceTest.kt` updated to match; `SeedDataInitializer.kt`'s `MIN_INTENSITY`/`MAX_INTENSITY` re-homed as local private constants (borrowed from the deleted `TimelineLayout.kt`, not conceptually tied to it); `MainActivity.kt` no longer wires the deleted `TimelineGrid`/`TimelineViewModel`.
- **Stale docs:** DEV_PLAYBOOK.md's Ship Checklist referenced "the Phase 2 seed-data mechanism" — that's Phase 3's now; reworded without a phase number so it can't rot the same way again. This file's own Phase 1 entry referenced "Phase 3's job" for ViewModel concerns that are now Phase 2's. TESTING.md's Big Picture coverage rows still described dot/bar bucketing, pinch-zoom responsiveness, and "tap a dot" navigation from the retired design — rewritten for the calendar grid's actual day/week/month/filter-chip interactions. HODITH_SPEC.md §9's closing note pointed at "domain-layer code that may still be reusable" — that code no longer exists; note corrected.
**Deferred:**
- `MainActivity.kt`'s temporary placeholder Compose text ("HODITH") is an inline string, not routed through the `Voice` layer — normally a hard violation of the #1 hygiene rule, but this is throwaway scaffolding pending Phase 2's Home screen, not shipping product copy. Not worth plumbing a `Voice` key for text that gets deleted within the next phase.
- `gradle/gradle-daemon-jvm.properties` sits untracked in the working tree, unrelated to this branch's work — flagged for awareness, not resolved here.
**Docs updated:** PROGRESS.md (phase order swap, current status, resolved open decisions), TESTING.md (Big Picture coverage rows), DEV_PLAYBOOK.md (Ship Checklist wording), HODITH_SPEC.md (§9 closing note), this file (Phase 1 entry's stale phase reference).

---

## feature/room-data-layer (Phase 1)

**Scope:** Room entities/DAOs/`HodithRepository`, JVM `Clock` abstraction, and their tests — first product code in the repo.
**Found & fixed:**
- Migrated `build.gradle.kts` files from inline dependency versions to `gradle/libs.versions.toml`, since this phase roughly doubled the dependency count (Room, coroutines, AndroidX test, Hilt testing).
- All six instrumented DAO test classes repeated the same `Room.inMemoryDatabaseBuilder(...).build()` boilerplate in `@Before` — extracted to a shared `createInMemoryDatabase()` helper in `TestFixtures.kt`.
- Enums stored as `String` (not ordinal `Int`) via `Converters.kt` — safer against future reordering/insertion of enum values.
- Tag cross-ref inserts use `OnConflictStrategy.IGNORE` so `HodithRepository.addTagToEvent` is idempotent.
**Deferred:**
- No domain logic beyond `Clock` exists yet (verdict/trigger/stats engines are Phase 5+), so most of DEV_PLAYBOOK §1's Decoupling/Complexity checks don't yet apply — revisit at that phase.
- `HodithRepository` mirrors the DAOs closely with no additional orchestration beyond tag find-or-create; ViewModel-level concerns (undo window, one-ongoing-per-case invariant) are explicitly Case CRUD's job per TESTING.md.
**Docs updated:** TESTING.md — status line updated (Phase 1 landed), known-environment-issues note replaced with the actual verification result (25/25 instrumented DAO tests passed on a Pixel 6 AVD, API 34; the Android 16/API 36 compatibility gap flagged at Phase 0 remains unverified since no API 36 image was used). PROGRESS.md — Phase 1 checked off.

---

## chore/project-scaffold (Phase 0)

**Scope:** Repo init and Gradle/Android project scaffold — no product code yet, just the empty app skeleton and tooling.
**Found & fixed:** nothing to fix; no product logic exists yet for most checklist categories to apply to (duplication, decoupling, complexity, accessibility are all N/A pre-feature-code). Confirmed `git status` clean, `.gitignore` correctly excludes `local.properties`/`build/`/`.gradle/`, no secret-shaped files staged.
**Deferred:**
- Dependency versions are inline literals in `build.gradle.kts` rather than a Gradle version catalog (`libs.versions.toml`). Matches DEV_PLAYBOOK §5's matrix directly for now; revisit once the module/dependency count grows enough that duplication becomes a real problem.
- Instrumented-test environment (the documented Android 16 compatibility gap in TESTING.md) is still unverified — Phase 0 only exercised the JVM `test` task (no source, ran clean) and `ktlintCheck`; no emulator was used. Verification happens when Phase 1 lands the first DAO tests.
**Docs updated:** DEV_PLAYBOOK.md §5 tooling matrix updated in place with verified versions (AGP 9.2.1, Hilt 2.60, Compose BOM 2026.06.01, Kotlin held at 2.3.20 pending KSP support, Room decision recorded). HODITH_SPEC.md and TESTING.md reviewed — no divergence, no changes needed (no product or test code landed this phase).
