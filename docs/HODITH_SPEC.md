# HODITH — Product Specification

**HODITH: How Often Does It Truly Happen** — a local-only Android app that answers one question: how often does that *actually* happen?

---

## 1. The idea

Sometimes a thought hits you: *"this always happens"* — or *"this never happens anymore."* Usually you don't actually know. HODITH lets you check.

You open a **Case** on the thing you've noticed, log occurrences as life happens, and the app shows you the reality: how often it happens, when it clusters, and how it compares to your other Cases on the same calendar.

### Example use cases

| Area | Examples |
|---|---|
| People around you | Teenager snaps at you — or helps unprompted. Partner does the dishes without being asked. Coworker interrupts you in meetings. |
| Your own days | A day that feels unbearable. A morning you wake up genuinely rested. Skipping lunch because work ran over. |
| Health | Migraines. Sleepless nights. Catching a cold. An allergy flare-up. |
| The world | The sun is out. The train is late. The neighbour's dog barks past 10pm. |
| Nice things | A stranger is unexpectedly kind. The coffee comes out perfect. An old friend calls out of the blue. |
| "We never…" | Eating out together. Game night actually happening. |

Some of these you influence, many you don't. HODITH doesn't care — it just counts, honestly. The audience is deliberately broad for now: anyone who's ever said "this always happens" and wondered.

## 2. Vocabulary

| Term | Meaning |
|---|---|
| **Case** | The thing being observed ("Kiddo was rude", "Migraine", "Perfect coffee"). |
| **Event** | One logged occurrence. The voices may dress this up ("evidence" in Intense). |
| **Watch** | A per-Case condition HODITH keeps an eye on: it fires a notification when an event happens often enough, or goes quiet for long enough (§11). "Watch" is the code and spec term only; the app calls it a **Rule** (Plain), **Alarm** (Intense) or **Alert** (Bright). |

The case → evidence framing is deliberate: it gives all three voices a shared metaphor to play with.

## 3. Design principles

1. **Logging is neutral.** An event not happening is information, not failure. The app never congratulates, never scolds, never asks where you've been.
2. **Logging must survive real life.** Events happen mid-argument or mid-sneeze. One tap from the widget logs "it happened, now." Details are optional and can be added later. Retro-logging is first-class — you often only realize afterwards.
3. **Statistical honesty.** With too little data the app says "not enough yet" rather than pretending a pattern exists — small-sample humility keeps the app trustworthy, and each Voice can flavor this state differently (§12).
4. **Show, don't lecture.** The flagship visual — the Big Picture — puts all your cases on one shared calendar and lets your own eyes spot the patterns when icons land on the same day.
5. **Every Case has a face.** Each Case gets an icon (emoji), shown everywhere it appears — Home, Big Picture, widgets, notifications. Icons are the primary way cases are told apart; color is never the only distinguisher (easier to remember, better for accessibility).
6. **Nothing leaves through the app itself.** No accounts, no analytics, no network permission at all — export/import is the user's escape hatch. Android's own device backup can still carry the app's data off the phone if the user has that turned on; a Settings toggle opts out (§16).

## 4. Non-goals (v1)

- **HODITH is not a habit tracker.** No streaks, points, scores, rewards, or reminders to "do the thing" — Cases are often about events nobody controls. If a feature idea pushes toward behaviour change rather than observation, it doesn't belong.
- No cloud sync, accounts, or telemetry.
- No computed cross-case correlation ("X causes Y") — the Big Picture shows co-occurrence visually, not computed correlation.
- No third-party charting library — v1 visuals are custom Compose.
- No iOS, no tablet-optimised layouts.

## 5. Core concepts & data model

Room (SQLite), local only. Timestamps stored as epoch millis UTC; each Event also captures the device's UTC offset at log time, so day/hour bucketing reflects where the event actually happened rather than wherever the device is when stats are later computed. Anything with no captured offset of its own (a Case's `createdAt`, "now") resolves in the device's current timezone.

### Case

| Field | Notes |
|---|---|
| id | PK |
| name | e.g. "Kiddo was rude"; unique case-insensitively among active Cases — an archived Case's name may be reused |
| description | nullable String — optional longer freeform text beyond the title |
| icon | emoji, required — the Case's visual identity everywhere |
| createdAt | when the Case was opened; the earliest point any of its stats or visuals can look back to |
| logFlow | `ONE_TAP` \| `DETAIL_SHEET` — what the widget/log button does |
| durationMode | `NONE` \| `MANUAL` \| `START_STOP` |
| intensityEnabled | boolean — show 1–5 intensity on the detail sheet |
| checkInsEnabled | boolean — whether this Case participates in check-ins (§11); the interval itself is always the app-level default from Settings. A Case wanting a custom silence threshold instead gets a `QUIET` Watch (§11), which already covers exactly that. The toggle lives at the top of the Case Detail bell tab (§14), not on the edit screen. |
| lastCheckInAt | nullable — when a check-in last fired or was answered "all quiet"; used for re-arming |
| sortOrder | manual ordering on Home and Big Picture |
| archived | boolean — hidden from Home/widgets/Big Picture, data retained |

Archiving is reversible and non-destructive. **Hard-deleting a Case** is a separate, irreversible
action, reachable only from the Archived Cases screen (§14) on a case that's already archived —
never directly from an active Case. It cascades to the case's events and watches (FK cascade
delete, same as `Event.caseId` below).

### Event

| Field | Notes |
|---|---|
| id | PK |
| caseId | FK, cascade delete |
| occurredAt | when it happened (editable — retro-logging) |
| endedAt | nullable — set for duration events; null + durationMode=START_STOP ⇒ **ongoing** |
| intensity | nullable Int 1–5 |
| note | nullable String |
| tags | tag strings via join table (`EventTag` / `Tag`) for reuse & autocomplete per case |
| loggedAt | when it was recorded (audit; distinguishes retro-logs) |
| utcOffsetMinutes | the device's UTC offset captured at `occurredAt` (not save time), so a retro-logged entry gets its own historical offset |

### Watch

Optional, many per Case. Table `watches`.

| Field | Notes |
|---|---|
| id | PK |
| caseId | FK |
| kind | `OFTEN` (the observed rate over the lookback reaches the expected rate) \| `QUIET` (no events for n days) |
| threshold | `OFTEN`: the expected count per `expectedPer`; `QUIET`: n days |
| windowDays | `OFTEN` only: the rolling lookback in days (always rolling; the editor offers presets that scale with `expectedPer`, or a custom number) |
| expectedPer | `OFTEN` only: the period `threshold` is counted per (day / week / month / quarter) |
| metric | occurrence count (default) \| days active (`OFTEN` on a duration Case only), same meaning as §8 |
| minIntensity | nullable 1–5, `OFTEN` only and only on a Case with intensity tracking: events rated below it (or unrated) don't count |
| enabled | boolean |
| armed | boolean, defaults true — edge-trigger state: fires (and flips to false) when the condition first becomes true, flips back to true once the condition stops being true. Prevents refiring on every evaluation while the condition remains met. |
| lastFiredAt | nullable — when it last fired, for notification copy |

## 6. Logging flows

- **One-tap** (widget or Home row): inserts an Event at `now`. In-app shows a snackbar with Undo; from the widget the event is silently created (editable/deletable in-app — Glance can't do transient undo reliably).
- **Detail sheet** (bottom sheet, ≤5 seconds to complete — logging a *new* event: quick-log, retro-log, widget): time (defaults now, editable → retro-log), intensity 1–5 (if enabled), duration (a whole number with a minutes/hours/days unit) or Start (per durationMode), note, tag chips with autocomplete. Big primary button saves; everything optional. **Editing an existing event uses the same fields on a full screen** (`TopAppBar` with a back arrow and a delete action), matching the Case edit screen so the two edit flows read the same way — reached only from Case Detail's History tab.
- **Start/stop**: Start creates an Event with `endedAt = null`. A running event is marked the same way on every surface — an **"Ongoing" pill** (`primaryContainer` chip) followed by the elapsed time (ticking, ~60 s refresh) for a lone running event or a count for several. Home rows and the widgets show only that indicator and open Case Detail on tap; the log/`+` button stays put in every state, so on a `START_STOP` Case it starts a second concurrent event (retro-logging and a fast stop/restart both legitimately leave more than one event open at once — "one ongoing event per Case" was never a hard limit). **Stop lives only where a single event can be targeted:** each open event's own log row in Case Detail, alongside that event's own elapsed time. The Case-detail log header only ever shows the ongoing indicator (the pill + elapsed for one running event, a running count for several), never a Stop. Stop is always immediate — no sheet, regardless of `logFlow`, since whatever detail was worth capturing was already captured at Start or can be added via edit afterward. A stopped event can be put back to running from its edit screen (End section → "Back to ongoing"), for when something stopped and restarted so fast it's really the same occurrence. Changing a Case's `durationMode` while it has open-ended events prompts for confirmation in either direction — see the transition contract below. A long-running open event carries the same signal as any other: the "Ongoing" pill plus its ticking elapsed time, which reads "2d 4h" once it's past a day, on both Home and Case Detail. HODITH surfaces the state and leaves closing it out to the user (§4). Widget support for ongoing/elapsed lands with the widgets themselves (§15, Phase 7).
- **Retro-log**: from case detail, the History tab's icon-only FAB (bottom-right; also the log-now entry point) → detail sheet with date/time picker. For a `START_STOP` case this is also how a fully-known past start+end range gets logged — the sheet's End section defaults to "Ongoing" but accepts an explicit end date/time, which is what leaving it unset vs. setting it actually means.
- Every event is editable and deletable from the case's event list — tapping a row opens the full-screen editor above (delete lives in its `TopAppBar`). Each row shows its timestamp (weekday + date, year only when it differs from the current year, plus time-of-day — 12- or 24-hour per the Settings toggle, §14) and, inline on a second line, an ongoing/duration indicator, intensity/note/tags when present — an open event shows the "Ongoing" pill + its own live elapsed time and carries its own Stop; a finished duration event on a Case that still tracks duration (`durationMode ≠ NONE`) shows how long it lasted — a zero-length event (`endedAt == occurredAt`) is a point and shows no duration line. Which of Notes/Tags/Duration/Intensity a row shows is a user-editable, persisted preference (an Edit icon opens a four-switch dialog, same idiom as Big Picture's own Overview detail toggle, §9), Duration/Intensity offered only when the Case tracks them; a note-less or tag-less event still shows whichever other fields are on and toggled visible.
- Above the row list, a small filter-chip row (same `FilterTriggerChip` idiom as Big Picture's own filters, §9) holds **Sort** and **Range**, with the field-visibility Edit icon pinned to its right. The **Sort** chip (shown only when the Case tracks duration) opens the existing **Started / Ended** toggle: "Ended" lists still-running events first, then finished events by most recent end. **Range** opens one date-range picker covering both bounds; once a range is set, the dialog's title row offers a one-tap "All time" button that clears it and closes the dialog. Below the chips, a set range shows its two bounds on a line of their own; an unset range shows the Case's creation date through today, so "All" reads as the Case's actual span. The filter itself stays unbounded when nothing is set, so events dated before the Case was created still appear. The range narrows the row list to events whose start falls within the picked range, inclusive; a range with no matches shows an empty-range message rather than the Case's plain "nothing logged yet" state. Sort, the date range and the field-visibility choice are all device-level view preferences (`SettingsRepository`/DataStore, like the theme) applied across every Case, not per-Case schema fields.
- The History tab's row list loads 30 events at a time (capped, sorted, range-narrowed query), with a "Show more" button revealing 50 more per tap; narrowing Sort or Range resets the loaded window back to 30. Ongoing-event detection, the History summary line (its headline rate, event count and observed span), and the Insights tab's stats always read the Case's full history regardless of how much of the History tab's list is loaded or how the Range filter narrows it.

### Duration-mode transition contract

Only `START_STOP` reads an `endedAt == null` event as *ongoing*; under `NONE` and `MANUAL` an end-less event is just a point (`NONE`) or a duration-not-recorded entry (`MANUAL`). Changing a Case's `durationMode` therefore has to reconcile its existing events. Stored `endedAt` history is never destroyed — the only write is collapsing an end-less event to `endedAt = occurredAt`, and that is idempotent (a point stays a point on any later round-trip).

| From → To | Effect on existing events | Confirm dialog |
|---|---|---|
| `NONE` → `MANUAL` | none | none |
| `MANUAL` → `NONE` | none (stored `endedAt` kept; simply no longer shown — §9) | none |
| `NONE` → `START_STOP` | every `endedAt == null` event gets `endedAt = occurredAt` (kept as an instant one-time event, not a live span) | shown when ≥ 1 end-less event exists; cancel leaves the mode unchanged |
| `MANUAL` → `START_STOP` | same as above (a `MANUAL` event logged without a duration is also `endedAt == null`) | same as above |
| `START_STOP` → `NONE` or `MANUAL` | every running event gets `endedAt = now` (stopped at the moment of the switch) | shown when ≥ 1 event is still running; cancel leaves the mode unchanged |

The switch-*in* conversion uses `endedAt = occurredAt` (the event's own start), not `now`, so a point logged days ago does not become a multi-day span. The switch-*out* conversion uses `now` because a genuinely-running event is being ended. There is no migration for an event left with an inflated `endedAt` by an earlier round-trip — an over-long span is indistinguishable from a real one — so those are fixed by editing the event.

## 8. Comparison math (internal — feeds Watches)

A pure-Kotlin comparison engine behind a Watch's `OFTEN` condition and the bell tab's Now zone and comparison line (§11, §14). Inputs: an `Expectation(count, per, metric, windowStart)` value, an event list, the Case's `durationMode`, `now`.

- **Observation window** is `[expectation.windowStart, now]` — resolving `windowStart` is the caller's job, not this engine's; a Watch's lookback is always a rolling day count decided at the call site.
- **Window filtering** — an event feeds the count only if its active span (§9) reaches into the window and it started by `now`. A duration event that began before the window but is still active inside it counts (span-overlap, not `occurredAt` alone). Days-active only counts the event's in-window days.
- **Metric** (`expectation.metric`):
  - **Occurrence count** (default, and the only option for a `NONE` Case) — number of in-window events.
  - **Days active** — number of distinct calendar days any in-window event's active span touched. Two same-day events read as one active day ("a day either had it happen or it didn't"). Honest for a Case with long, overlapping duration events, where a raw event tally undersells how much of the time the event was happening. Only offered when the Case tracks duration.
- **Observed rate** = the metric's count ÷ window length, normalised to `expectation.per`.
- **Confidence tiers** (both conditions required per tier) — the same math for every window and both metrics; days-active feeds its distinct-active-day count in where the event count would go, with no new constants and no dual-condition guard. Accepted tradeoff: a single long duration event can clear the bar alone.
  - **No verdict** — count < 5 *or* window < 14 days → "early days" state
  - **Preliminary** — count ≥5 and window ≥14 days
  - **Confident** — count ≥15 and window ≥28 days
- **Comparison bands** (observed ÷ expected): `<0.5` much less · `0.5–0.8` less · `0.8–1.25` about right · `1.25–2.0` more · `>2.0` much more. Each cutoff itself belongs to the higher band (e.g. exactly `0.8` is "about right", not "less").
- Copy comes from the Voice layer (§12) and is band-only, not direction-aware.

## 9. Visualizations

The part of the app that makes occurrences *visible at a glance*. All custom Compose, all honouring the "early days" rule — below minimum data they show a friendly placeholder, never an empty chart pretending to mean something.

### Active span

The active span applies only while the Case tracks duration (`durationMode ≠ NONE`). A Case set to `NONE` renders **every** event as a point at `occurredAt` on every day-counting surface — the heatmap, the streak count, the Big Picture grid, and the gaps & streaks "silence since it ended" anchor (§10) — whatever `endedAt` is stored. That stored value is never mutated, so switching the mode back on restores every span. For a Case that does track duration, an event covers every calendar day from its start to its end, inclusive, resolved in the event's own captured offset (§5) — so a traveler's logged days land on the day they actually happened, not wherever the device is when stats are computed. A still-running event's open end is the one exception: it resolves in the device's current timezone, since "now" has no captured offset of its own:

- **finished** event — `occurredAt … endedAt`
- **still-running** event (`START_STOP`, no `endedAt`) — `occurredAt … now`
- **point** event (no `endedAt`, not `START_STOP`; or any event on a `NONE` Case) — its single day

A span that crosses midnight covers each calendar day it touches — an event from 23:30 Monday to 00:30 Tuesday marks both Monday and Tuesday. This rule governs the per-case calendar heatmap, the streak count (§10), the Big Picture grid (a multi-day event's icon appears on every day it covers, ringed on the start day), and Home's today / this-week counts (§14) — an event counts toward a window if its span reaches into it, counted once, not once per active day, so a run stopped and logged today still shows on its Home row that day. Frequency over time, Rhythm, Trend, the verdict engine (§8), and "observed for N days" keep counting each event once at its `occurredAt` start. Because a per-bucket count can't honestly answer "how often" once a single event spans several days, Frequency over time is hidden outright, and Rhythm is retitled "Start times", for any Case with at least one multi-day event — the calendar heatmap covers that ground instead.

### The Big Picture — flagship view

A scrollable multi-month calendar grid (day columns × week rows, like a standard month calendar). Every active Case's icon appears in the cell for each day it has an event. Scrolling swipes through time — the current month at the top, oldest at the bottom, opening scrolled to the top; there is no pinch/continuous zoom, since a calendar grid's natural unit is already a day/week/month, not an arbitrary time window. Tapping the month label opens a quick-jump picker to reach a distant month instantly instead, scoped to the active Year filter below when one is set.

- Cross-case correlation — the whole point of this screen — comes from multiple case icons landing in the same day cell, not from vertical alignment across per-case rows: when "unbearable day" and "kiddo was rude" land in the same cell, you see it, no statistics required, no causation claimed.
- Only days up to and including today are ever shown; future days render as blank space.
- A day cell belongs to exactly one month — the neighbouring month's leading/trailing days that would normally pad out a boundary week are left blank rather than duplicating that date under both months.
- A day over its icon capacity shows a "+N" overflow badge rather than silently cropping.
- Tapping a day opens that day's logged events; tapping an event opens that Case's detail screen. A separate small chevron on each week opens a week view listing that week's events the same way — kept as its own tap target from the day cells. Each detail row always shows the Case and the time (or the "ongoing since …" / "lasted …" span label); note, tags, a same-day duration line, and intensity are each shown or hidden per the user's **Overview detail** preference — an edit icon at the right of the filter row opens a four-switch dialog (Notes / Tags / Duration / Intensity), and the choice persists. Default: Notes, Tags and Duration on, Intensity off. A note-less event shows no note line at all. This preference only affects these tap-through dialogs; intensity and sub-day duration are still never drawn on the grid itself.
- Small trigger chips above the grid ("Cases: N ▸" / "Tags: N ▸", "All" in place of N once fully selected) each open a picker dialog with the full set of Case (icon + name, ordered by Home's manual `sortOrder`) or tag chips; toggling a chip inside applies immediately and filters the grid and detail dialogs identically. The Tags chip is shown only once a currently-selected Case's event carries a tag. The Tags dialog's tag list is scoped to the currently-selected Cases only, not every Case in the app — a tag belonging solely to a hidden Case would otherwise let both dialogs look non-empty while their combination silently shows nothing. Changing the Case selection re-scopes the tag universe and resets tag selection to everything within it, so narrowing Cases always moves toward showing more, never toward that empty trap. Each dialog also carries a single bulk-toggle button ("Select all"/"Clear all") that flips its whole dimension in one tap — labelled by the action it's about to take, not the current state — so clearing a long preselected list doesn't require deselecting each chip individually. Below the triggers, one combined read-only legend row summarizes the current Case/Tag selection: a fully-selected dimension collapses to a single "All Cases"/"All tags" chip; both dimensions fully selected (the default) shows no legend at all; zero Cases selected collapses the whole row to a static "no Cases selected" note, since tag state is moot when nothing can render regardless. Deselecting some but not all tags still hides untagged events, since they have nothing left to match against — but deselecting every tag shows untagged events only ("Untagged only"), a real, non-empty filter result rather than an empty one.
- A third trigger chip, **Year** ("Year: 2025 ▸" / "Year: All ▸"), is shown only once a Case's data spans more than one year. Its dialog is a flat, single-select list of years present in the data, current year first, oldest last, plus an "All years" option — picking a value applies immediately and closes the dialog, unlike the Cases/Tags dialogs' multi-select-stays-open behaviour. Narrowing to a year hides every other year's months from the grid; there is an explicit reset back to "All years". The Year chip has no entry in the combined legend row below the triggers — its own label already states the whole selection. Any trigger chip narrowed off its default (not all Cases, not all Tags, or a specific year) carries a highlight border as a quick "something is filtered" signal.
- Intensity is not encoded on the grid. Duration is, but only for an event whose active span crosses a calendar-day boundary: its icon then appears on every day it covered, the start day's icon carrying a `primary` ring, and a still-running event trails to today. A same-day duration event is indistinguishable from a moment event here. A `NONE` Case shows no spans at all — every icon is a single-day mark regardless of any stored `endedAt` (§9 active span). Tapping a day still opens its events; on a spanned day the row reads "ongoing since …" / "lasted …" on its own line, carrying the start date and time once the event began on another day (a bare clock time would read as belonging to the row's day). On the grid itself, intensity and sub-day duration never appear — they belong to a case's own stats (§10), and are reachable from the day/week dialog only via the Overview detail toggle above.
- Zero active Cases shows the same empty state as Home, and the grid itself never renders. At least one Case but zero events logged anywhere is different: the grid still renders (every day cell empty), with a small "not enough data yet" note above it that clears itself the moment a single event exists anywhere — the flagship view is the one exception to §9's general "no chart pretending to mean something" placeholder rule, since an empty calendar grid doesn't pretend to show a pattern the way an empty chart would.

### Per-case: calendar heatmap

A year-in-pixels month grid — each day a cell, shaded by how many events were active that day (the active-span rule above: a multi-day event shades every day it covers, a running event through today). Cozier, good for "what did this month look like". Rendered last on the Insights tab, after the §10 stat cards below. Shows the three most recent months by default, most-recent-first, with an option to reveal the Case's full history.

Tapping a day with at least one active event opens that day's logged events for this Case — no separate week view the way Big Picture has one, since the per-case heatmap is already scoped to a single Case and a week rollup would mostly repeat what the month grid already shows at a glance. A day with zero events is inert (no tap target at all). Each result row reads timestamp (or ongoing/duration line for a spanned event), note, and tags — no case icon/name, since the tab is already scoped to one Case.

The heatmap renders from the **first** event, alongside a one-line event-count note and the Rhythm and Gaps & streaks cards (§10) — the same carve-out §9's Big Picture grid takes, since a calendar with one marked day doesn't pretend to show a pattern the way an empty chart would. With zero events the Insights tab shows a flat invitation to log one, never a countdown toward a threshold.

## 10. Stats (descriptive)

On the case detail Insights tab, in this order. Trends is shown only when at least one finding exists; Rhythm and Gaps & streaks appear from the first event; Frequency over time is held back until there are at least two events, where a single bar would read as a pattern that isn't there yet:

- **Trends** — the first card, shown only when at least one finding exists across every detector in the roster below. There is no separate Trend arrow card any more — the former standalone 30-vs-30-day comparison is one of these findings (frequency shift, below), not a distinct feature. Each row shows its headline, its Hint/Pattern chip top-right, the figures it compares (e.g. "6 days vs 3 days", or "62% vs 2 in 7 by chance"), and its evidence count; each row's figures carry every number it needs, so no separate sentence restates them (the full list shows the same rows, up to the domain cap rather than the compact card's limit). The compact card shows the first 3 by default, with a link to the full (capped) list as its own screen rather than expanding in place. A "went quiet" finding (below), when present, always leads the list — it's the one finding about the Case's current live state rather than settled history, so when it leads, the compact card shows only it plus the link, instead of pairing it with up to two unrelated shift findings. Findings are ordered by strength: a went-quiet finding first, then Pattern findings by ascending p-value, then Hint findings by size of change. The cap on the full list applies after ordering, so the strongest findings are kept. The compact card and the full list each have an info icon that explains the Hint and Pattern tiers once for the whole list, not per row.
- **Frequency over time** — counts per day/week/month (granularity auto-picked from data density, user-overridable). Hidden entirely for a Case with any multi-day event (§9): a per-bucket count would double-count a long event, and the calendar heatmap already shows the shape honestly.
- **Rhythm heatmap** — day-of-week × time-of-day grid, cell shade = count, shaded on a finer 20-tier scale than the calendar heatmap or intensity stats for more visible contrast between nearby counts. Always plots each event's start; retitled "Start times" for a Case with any multi-day event (§9), so a span that began late one night doesn't read as "only happens at night".
- **Gaps & streaks** — shortest, average and longest gap (one row labelled Min gap / Avg gap / Max gap, with the same figures as the share card's Gaps panel), current gap (silence since the last event *ended* — its start for a point event, and for every event on a Case that no longer tracks duration; reads 0 while *any* event is running on the Case); longest streak, average streak (a streak is a run of consecutive calendar days each covered by at least one event's active span, §9); "tends to come in bursts" flag when gap variance is high. `QUIET` watches and check-ins count silence from the same point.
- **Event duration** (if durationMode ≠ NONE) — shortest, average and longest time as one Min / Avg / Max row (the same as the share card's Duration panel); still-running events are excluded until they stop
- **Intensity stats** (if enabled) — average, distribution mini-bars
- **Tag breakdown** — counts per tag, shown against the Case's total event count so an individual tag's count reads in proportion rather than in isolation. Both the card and the full tag list open with a summary (total events and total tags) set apart from the tag rows by a divider. A Case with up to 5 distinct tags shows every tag. Past 5, the card collapses to the busiest 3 tags, with a "see all" link to the full tag list as its own screen (the same navigate-not-expand pattern as Trends). Every tag row on either screen opens the same drill-down of its events. The Share card's Tags section keeps the busiest 3 at every count, since a static card has no room for a link.

The calendar heatmap (§9) follows the tag breakdown as the tab's final section.

### Trends detectors

The Trends section's current roster of detectors — every one shares the "often follows"/"tends to," never "causes" wording rule, and a `Hint`/`Pattern` reliability tier distinct from §8's `ConfidenceTier` (that measures sample-size adequacy for an average; this measures whether the effect itself has been tested for significance).

- **Went quiet** — the current, still-open silence since the last event is a record for this Case (at least as long as any gap it's ever had), while the user is demonstrably still logging elsewhere (something logged, any Case, within the last week). Unlike every other detector below, this one isn't a shift between two halves of completed history — it's the Case's live state — and its sentence is framed as an open question ("still happening, or has it wound down?", spec §4's "ask rather than silently report"), never a statement that the user did something wrong. A Trends finding, not a notification — it never touches check-ins or `QUIET` watches (§11), which stay the only two ways to be alerted about a Case's silence. Always `Hint`.
- **Gap shift** — whether the average gap between events has shifted noticeably between the earlier and more recent half of the Case's history (by gap count, not a fixed day window). Always `Hint`: a descriptive dual-threshold check, no significance test behind it.
- **Streak shift** — as gap shift, over streak-run lengths rather than event-to-event gaps.
- **Frequency shift** — last 30 days vs the 30 before (needs ≥ 8 weeks of data, otherwise absent). Absent (not shown as "flat") when the two counts are equal — the same "silent when nothing moved" rule as gap/streak shift, rather than reporting a non-finding. Always `Hint`: no significance test, just a direct count comparison.
- **Tag share shift** — whether a tag's share of the Case's own events has shifted noticeably between the earlier and more recent half of its history (by event count, not a fixed day window), gated on both a minimum total event count and a minimum number of times the tag itself has been seen — a tag used once or twice can't support a share claim regardless of how big the swing looks. The one detector that can surface more than one finding per Case (up to 3, strongest shift first). Always `Hint`: a descriptive dual-threshold check on percentage points, no significance test behind it.
- **Common tag combos** — which sets of two or more tags recur together on the same event, found via closed frequent-itemset mining with no cap on how many tags a combo can hold. A subset that always carries the same extra tag is folded into the larger set — if every event with tags A and B also carries C, only {A, B, C} is shown, not {A, B} as well. Gated on a minimum co-occurrence count. Up to 3 combos shown per Case, most frequent first. Always `Hint`: a descriptive support-count check, no significance test behind it.
- **Recurrence shape** — whether this Case's past gaps form an early-spike pattern (it usually recurs within a self-relative day boundary, scaled off the Case's own average gap rather than a fixed day count) or a dead-zone pattern (it almost never does, and the gaps have real spread rather than just a steady rhythm — a Case whose gaps are simply consistent doesn't qualify). A heavier sibling to the Gaps & streaks card's "tends to come in bursts" flag: that's one variance number for the whole distribution, this states the short end of it as a share plus a real day figure. Distinct from "went quiet": that's a claim about the Case's current, still-open silence; this is a distribution-shape claim true regardless of current state, so both can appear on the same Case at once. Always `Hint`: a descriptive threshold check, no significance test behind it.

**Tag → outcome** compares a tag's events against the Case's other events, independently on intensity and on duration — the first detector able to report `Pattern` as well as `Hint`, via a real permutation-significance test rather than a descriptive threshold. Gated on at least 15 events with the tag and 30 without it, per outcome (a tag can qualify for one outcome but not the other); a pair that clears those minimums must also show at least a 20% relative difference in means before the (comparatively expensive) permutation test runs at all — a 1000-iteration label-shuffle test, checked at a 5% significance level. A pair that clears the descriptive floor but not significance produces no finding at all, never a `Hint` — unlike every threshold-only detector above it, where clearing the threshold *is* the finding, a `Hint` here would misrepresent a test that ran and failed. Like tag share shift, this is the other detector that can surface more than one finding per Case (up to 3, across both outcomes combined, strongest effect first). A third candidate outcome, time-to-next-event, isn't offered: its statistical power to detect a realistic effect stays below 70% even at generous per-Case sample sizes (25 tagged / 80 untagged events), since gap-based outcomes are inherently noisier than a bounded intensity score or a duration — real inter-event gaps run more over-dispersed than that, so the metric isn't reliable enough at typical Case scale.

**Change point** finds where in a Case's own gap history a real shift happened, catching the slow drift a fixed 30-vs-30-day frequency shift structurally can't see — additive to it, not a replacement. Gated on at least 12 past gaps, roughly twice gap shift's own floor, since searching for the best split (rather than trusting the midpoint the way gap shift does) is inherently more overfitting-prone at small sample sizes. A CUSUM walk over the Case's past gaps (the same event-to-event gap sequence gap shift already uses) picks that best-supported split point, restricted to candidates with at least 3 gaps of real support on either side so a split can't sit right at an edge with nothing to compare against. That split only becomes a finding once its two segments clear a 40% relative-difference floor and a 1000-iteration timeline-shuffle permutation test at a 5% significance level — a sibling of tag → outcome's label-shuffle test, reshuffling the gap sequence's own order rather than which group each value belongs to, and re-walking the same CUSUM search on every shuffle so the null distribution already accounts for the observed statistic being a maximum over many candidate splits, not one fixed test. CUSUM was chosen over a Bayesian change-point model because it needs no new likelihood/posterior machinery in this pure-Kotlin domain layer, is deterministic, and its split location doubles as both the significance statistic and the calendar date the sentence needs ("since around mid-March" — stated as a third-of-month estimate, not a claim of the exact day); the false-positive rate lands near the nominal 5% (3.7–7% across realistic sample sizes), and CUSUM needs no second method to compare against. Like tag → outcome, a candidate that misses significance produces no finding at all, so every kept finding here is already `Pattern`.

**Trend slope** finds a real slope over time in intensity or duration's own per-event values — gated per outcome, only for a Case whose stat card for that outcome is already shown, and only once at least 12 qualifying events exist, matching change point's own floor since a full time-ordered series is being searched rather than a fixed two-average comparison. A 20% relative-difference floor between the time-ordered first and second half is checked before the (comparatively expensive) permutation test runs at all. Rather than a third bespoke significance test, this reuses the shared permutation engine directly (the same one tag → outcome's label-shuffle test and change point's timeline-shuffle test both sit on top of) with its own OLS-slope statistic — 1000 shuffles of the outcome's values against fixed time positions, checked at a 5% significance level. Like tag → outcome and change point, a candidate that misses significance produces no finding at all, so every kept finding here is already `Pattern`.

**Time-of-day split** (alongside trend slope) compares the same two outcomes — intensity and duration — between day and evening events, reusing the Rhythm heatmap's own day/time-of-day boundaries collapsed from four buckets to two (day: morning + afternoon; evening: evening + night). Gated the same way as trend slope (per outcome, only for a Case whose stat card is already shown), with at least 15 qualifying events in both the day group and the evening group. Reuses tag → outcome's label-shuffle permutation test exactly as-is, with the day and evening groups standing in for the untagged and tagged groups — the same 20% descriptive floor, 1000 iterations, and 5% significance level. Also always `Pattern` for the same reason.

**Tag timing** compares one tag's own events against the Case's overall rhythm — both the Rhythm heatmap's four `TimeOfDay` buckets (morning/afternoon/evening/night) and its seven `DayOfWeek` buckets — to test whether the tag concentrates in one specific bucket of either dimension beyond the Case's own base rate there. A tag can qualify on one dimension, both, or neither. Gated per dimension on a tagged-event floor tuned to its bucket count (15 for the four time-of-day buckets, matching tag → outcome's own tagged floor; 28 for the seven weekday buckets, keeping roughly the same per-bucket density even with more ways for a peak to appear by chance) and a Case-wide floor for a stable baseline (45 events overall for time-of-day, 80 for weekday, the same per-bucket density scaled up). The tag's single most-concentrated bucket must then clear both a 15-percentage-point absolute floor and a 50%-relative floor over that bucket's own Case-wide share — tag share shift's own dual-threshold shape, since a flat percentage-point bar alone would be easier to clear on the seven-bucket weekday split (~14% baseline per bucket) than the four-bucket time-of-day split (~25% baseline per bucket) — before the (comparatively expensive) permutation test runs at all. That test is a 1000-iteration permutation on a max-positive-bucket-deviation statistic, reusing the shared permutation engine directly with its own statistic (trend slope's "no third bespoke test" precedent) rather than either of the roster's typed wrappers, checked at a 5% significance level and re-taking the same maximum-across-buckets on every shuffle so the null distribution already accounts for the observed statistic being a maximum over several candidate buckets, not one fixed test — the same look-elsewhere correction change point's own test uses. A candidate that misses significance produces no finding at all, never a `Hint`. Only over-concentration is tested, never under-representation, so every finding reads "clusters," never "avoids" — direction is always fixed, the same one-directional convention went quiet uses. Like tag → outcome, this can surface more than one finding per Case, across both tags and dimensions combined (up to 3, strongest concentration first). Always `Pattern`.

**Weekday vs weekend** (a scoped-down slice of a broader cycles/seasonality idea — full autocorrelation and month-of-year comparisons stay deferred, see PROGRESS.md) compares the Case-wide share of events landing on a weekend day (Saturday or Sunday) against the fixed calendar baseline of 2/7 (≈28.6%), rather than a baseline derived from the Case's own observation window — HODITH Cases are open-ended, ongoing logs, so a fixed baseline carries negligible risk and keeps the same simple-floor shape every other detector uses. Gated on at least 40 events Case-wide (the same ~11-events-per-bucket density the tag-timing floors target, scaled to the 2/7 weekend slice). The observed weekend share must clear a 15-percentage-point absolute floor and a 50%-relative floor over 2/7 — tag timing's own dual-threshold shape — before the permutation test runs: a 1000-iteration test on the signed weekend-share-minus-baseline statistic, reusing the shared permutation engine directly, where each shuffle draws every event's weekend/weekday membership independently as a Bernoulli(2/7) trial, checked at a 5% significance level. Unlike tag timing, this is two-directional — a Case can lean toward weekends or toward weekdays, each with its own reading — since a Case's overall rhythm has no default lean. A candidate that misses significance produces no finding at all, never a `Hint`. Always `Pattern`, and case-wide — at most one finding per Case, not per-tag.

Tapping an intensity square, a tag row, or a rhythm cell opens the matching logged events for this Case, same shared result surface as the calendar heatmap's day-tap (§9) — a rhythm cell's match is every event whose start falls in that day-of-week/time-of-day bucket. A zero-count intensity square or rhythm cell is inert (no tap target); every tag row is tappable, since a tag only appears in the breakdown once it has counted at least one event.

The duration and intensity cards are gated purely on the Case's current `durationMode`/`intensityEnabled` flags — turning either off hides its card but keeps every event's recorded `endedAt`/`intensity` untouched, so turning it back on restores the card with all its history intact. The same `durationMode` gate governs every other duration surface: the Case-detail event row's "lasted …" line (§6) and the Big Picture spans (§9) all treat a `NONE` Case's events as points, reading no stored `endedAt`.

## 11. Watches, check-ins & notifications

### Watches (user-configured, about the event)

- Evaluated (a) immediately on every event insert/edit/delete — a sub-second per-Case debounce collapses a rapid logging burst to one evaluation — and (b) by a WorkManager periodic job (~every 6 h) so `QUIET` watches can fire without any logging happening.
- Both kinds are edge-triggered: a Watch fires when its condition first becomes true and re-arms once the condition stops being true.
- `OFTEN`: fires when the observed rate over the lookback, normalised to `expectedPer`, reaches `threshold` ("3+ times per week"). The rate comes from §8's comparison math (span-overlap window filtering, both metrics) after the optional `minIntensity` filter. Firing is not confidence-tier-gated; only the card's comparison line is. Requires a `windowDays`, so the editor always supplies one.
- `QUIET`: fires when the gap since the last event *ended* reaches n days (a duration event's silence starts when it stops; a still-running event counts as no silence at all; a Case that no longer tracks duration counts from `occurredAt`, ignoring any stored `endedAt`, per §9/§10); re-arms on the next event. A Case with no events yet counts from its creation instead, so a never-logged Case still fires.
- Notification content is voice-flavoured and factual: icon + count + case name + "tap to see". Information, not advice.
- The bell tab (§14) shows each Watch as a card in two zones: what it watches for (eyebrow, title, enable switch), then a tinted "Now" zone with the current rate or silence, plus, for `OFTEN` once the lookback clears §8's confidence tiers, a tier badge and comparison line, and a fired line when it has fired. A disabled Watch still shows its Now zone.

### Check-ins (app-initiated, about the data)

Silence in a Case is ambiguous: did the event stop happening, or did the user stop logging? A check-in resolves that — it's data hygiene, not a nag, and the copy makes the distinction: it asks whether anything went unlogged, never implies the user should "keep it up".

- A check-in fires when a Case has had **zero events for its effective interval** — counting from the latest of: last event's end (its start for a point event or any event on a Case that no longer tracks duration; now if one is still running), last check-in, or case creation. This automatically covers the created-but-never-logged Case ("You opened 🐕 *Dog barking* 14 days ago — nothing logged yet. All quiet, or forgot it exists?").
- **Timing:** the **app-level default** from Settings (`off / 7 / 14 / 30 days`) — every Case shares it. A Case can opt out entirely (`checkInsEnabled = false`) but has no custom interval of its own — a Case wanting a specific silence threshold gets a `QUIET` Watch instead, rather than a second, overlapping way to configure the same idea.
- Notification actions: **Log** (respects the Case's `logFlow` — one-tap logs directly, detail-sheet opens the sheet) and **All quiet** (re-arms the check-in; no event created).
- Anti-spam: check-ins are evaluated by the same WorkManager job as watches. Every HODITH notification — check-ins and fired watches alike — joins one Android notification group, so the shade bundles them into a single stack under a group summary ("3 cases need a look — tap to review"). Only the summary alerts for a batch (`GROUP_ALERT_SUMMARY`) and it alerts once, so an unanswered check-in re-posted on each ~6h pass updates its "N days quiet" text silently rather than re-alerting. Each due Case keeps its own **Log** / **All quiet** actions in the expanded stack — there's no action-less flattened summary. Re-arming only happens explicitly — via **All quiet**, or a new event moving the anchor forward — never automatically at fire time; and a check-in whose Case has stopped being due has its notification withdrawn on the next pass, so the stack doesn't keep a stale entry.

### Permissions

**POST_NOTIFICATIONS** runtime permission is requested when the user creates their first Watch or first enables check-ins — never on first launch. If denied, both watches and check-ins still evaluate and appear as in-app banners on Home.

## 12. Themes & voices

Three themes, picked in Settings, applying skin + voice together:

| | Plain | Intense | Bright |
|---|---|---|---|
| Palette | Cool neutrals, one restrained accent | Monochrome (black/white/gray) plus one crimson accent, reserved for interactive elements — a noir palette, not a gothic one | Warm brights, a playful accent pair |
| Type feel | Clean, businesslike (Inter) | High-contrast, pulp-poster dramatic — bold condensed display face (Oswald) over a readable serif body (Source Serif 4) | Rounded, friendly (Baloo 2 display / Nunito body) |
| Verdict sample | "Observed: 2.1×/week — below your estimate." | "Your dread was exaggerated. It happens but twice a week." | "Plot twist: only 2×/week. Your brain lied!" |
| Early-days sample | "Insufficient data. Keep logging." | "The evidence is yet insufficient for despair or joy." | "Too soon to tell — feed me more moments!" |
| Empty state sample | "No cases yet." | "Nothing is being watched. Yet." | "It's quiet in here… suspiciously quiet." |
| Home header | "How often does it truly happen?" | "How oft dares it truly haunt?" | "How often does it totally happen?!" |

All three Home header phrasings mean the same thing and each one's six words' first letters still spell **H-O-D-I-T-H**, matching the app's own name.

**Architecture:** every user-visible string is a key on a `Voice` interface with three implementations (`PlainVoice`, `IntenseVoice`, `BrightVoice`), provided via CompositionLocal — no `when(theme)` in composables. A unit test asserts every key is non-blank in all three voices, so a string can never silently ship in only one voice. Full light/dark mode within each theme, each with its own `ColorScheme`, `Typography`, and `Shapes` (`ui/theme/`).

## 13. Sharing a Case

Turning a finished (or in-progress) investigation into something you can drop into a group chat or post as a story. *"I checked: it does NOT always rain on my day off."*

- **Share card** — a rendered image, generated locally (Compose capture → bitmap → Android share sheet via FileProvider; no network involved, consistent with §16). Two formats, sized asymmetrically on purpose: **story** sizes purely to its selected content, since Instagram/Snapchat Stories letterbox a shorter-than-9:16 image back to shape automatically; **square** keeps a 1:1 floor (matching 1080×1080), since it shares into contexts — chat threads, feed posts — that render whatever aspect ratio they're given, and is a fixed preset the user never configures (below). Both still grow taller than their floor if their content needs more room.
- **Card content**, in order, in the active theme's skin and voice:
  1. *The case* — icon + name.
  2. *The top beat* — the summary hero, on both formats (below).
  3. *Story's sections* — a checklist-driven picker. Its default order is **Gaps**, **Streaks**, **Duration**, **Start times**, **Intensity**, **Trends**, **Top tags**. The user drags the picker's rows into any order and the card follows it. The order is device-wide, so every Case shares it, and it persists across sessions. Sections a Case has no data for keep their place in the order without a row. Gaps, Duration and Intensity are the same panels Square uses (below), and Start times is the Rhythm grid, always under that title. Streaks is its own row, independent of Gaps: picking it adds the Streaks card and picking Gaps does not add it. A row is offered only when the Case has something to show for it: Gaps and Streaks need two events, Duration and Intensity need the Case to track the measure and have logged it, Trends needs at least one finding other than went-quiet, Top tags needs at least one tagged event, and with no events yet there is no picker at all. Trends renders each finding as sentence text only — no reliability tag, no evidence line, since the card has no room for tap-revealed detail. Top tags lists the Case's three busiest tags, busiest first, each with its event count. Frequency never appears on a share card: the hero's rate and trend pill carry it. The went-quiet finding is left out of Trends because the hero and the Gaps panel's quiet label already say it, and that label shows on Story only while Gaps is picked. With no section picked, the hero alone is the card.
- **Summary hero** — shared by Story and Square. A top line, `94d observed · 31 events` (the event noun is the voice's own: events / marks / logs; before a rate exists the line is just `12d observed` and the event count becomes the headline). Then the headline rate and, when it moved, a trend pill on the same basis, so a unit appears once. With 56+ days of history the headline is the **last-30-days rate** and the pill states the rate it moved from, in the same unit (`from 1.4 a month ago`, or `same as a month ago` when flat, each led by a drawn up/down/right triangle); under 56 days the headline is the **overall rate** with no pill. A rate needs 5+ events over 14+ days (the verdict's preliminary tier); below that the hero is the event count alone. The unit is the largest of `/day`, `/week`, `/month` in which the rate is still 1 or more, else `<1/month`; figures drop a trailing `.0`. The pill drops to its own line when the card is narrow or the text large, never squeezing the figure.
- **Square preset** — content is chosen by the Case's own settings and how much data exists, never by a picker, so the shape stays predictable. Top to bottom, after the hero:
  1. *Gaps* — Min / Avg / Max, once the Case has two events, with a second row for the streaks (Longest streak / Average streak) inside the same card, the same panel Story's Gaps uses (Story keeps its streaks in their own card). Before the second event the whole panel is left off the card, quiet label included. While the went-quiet signal (§10) is live, a dashed `Quiet for 14d` label sits at the panel's top right.
  2. *Duration and Intensity, by Case settings* — a Duration panel (Min / Avg / Max duration) when the Case tracks duration, an Intensity panel (`average 3.4 of 5` plus the 1–5 distribution squares) when intensity is on, both when both; the **Start times** grid appears only when the Case tracks neither. A tracked measure with no data yet simply has no panel, rather than being swapped for Rhythm.
  Frequency, Trends and Top tags never appear on Square. On Story the streaks sit in their own *Streaks* card, directly after Gaps when both are picked, and appear only when the Streaks row is picked and the Case has two events. `Min`, `Avg`, `Max`, `Gaps`, `Streaks`, `Duration`, `Top tags` and the rate units are structural (identical in every voice); the event noun, the trend pill's phrasing (`a month ago` / `a month prior` / `last month`) and the quiet label (`Quiet for` / `Silent for`) are voiced.
- **Templates are theme-based** — Plain renders like a clean report card, Intense like a bordered dossier with a rotated corner stamp and uppercase titles, Bright with a banner header and sticker. The template follows the *currently active* theme; switching themes before sharing restyles the card.
- **Preview before share, always.** The share flow opens a preview screen where the user can:
  - pick square vs. story (the preview opens on square),
  - edit the displayed case name (real names can be personal — "Kiddo was rude" might become "Someone was grumpy"). The field shows the name the card will carry, labelled *Name on card* on the top border (structural, the same in every voice), so it reads as editable content rather than a hint,
  - toggle Story's sections on/off and drag them into order (Square has no picker).
  Notes are **never** included on this card, and tags appear only as the Story card's opt-in Top tags section (names and counts, never tied to an entry) — the redacted-summary premise is the whole point.
- **History Share** — a second card type, same rendered-image pipeline, showing the Case's actual logged entries (timestamp, and duration/intensity/note/tags per row, each field only offered when the Case tracks it) instead of Insights stats. Deliberately the opposite of the Insight card's redacted-summary rule above: raw notes/tags are the whole point of choosing this option, so nothing is held back. A Log filter (sort direction, date range, per-field toggles) curates what fits — the card caps at 30 entries, truncating to the most recent matches with an on-card note when the range holds more, so a long range can't grow the card past a readable length. The card has one content-sized shape. Its name is the Share screen's shared name field, the same as Insight Share's, without changing the Case. The card's title line under the case name is its own resolved date range.
- Entry point: the Case Detail header's Share icon opens one Share screen (titled "Share") with three tabs: **Summary** (the square card), **Insights** (the story card, with its section picker) and **History** (the log card). A single "Name on card" field above the tabs is shared by all three, so a name typed on one tab carries to the others. Each tab's card sits under a "Preview" heading on its own tinted stage, and each tab's share button reads "Share". Sharing the Big Picture (multiple cases at once) is deliberately excluded — several case names on one image multiplies the privacy footguns anonymisation would need to solve first.
- HODITH branding on both card types is a small, unobtrusive footer ("counted with HODITH app") alongside the card's own generation date and time — honest attribution, not an ad. The "app" is there for discoverability: someone seeing a shared card should be able to search the phrase and find it. The timestamp marks every section as a snapshot from that moment, since a Trends finding like a current silence streak is only true as of when the card was made.

## 14. Screens

Bottom navigation: **Home · Big Picture · Settings**.

| Screen | Contents |
|---|---|
| **Home** | Case list (drag to reorder): icon, name, description (when set, truncated to two lines), today/this-week count (a duration event counts while its active span is still open into the window, matching the calendar heatmap — §9), quick-log button, ongoing indicator. FAB: new Case. Watch banners if notifications are denied. Text link to **Archived Cases**, shown only once at least one Case is archived. |
| **Big Picture** | §9 flagship view. |
| **Case detail** | Tabs: **Log** (event list, retro-log, edit/delete), **Insights** (visuals §9 + stats §10), and an icon-only **bell** tab (content description "Rules" / "Alarms" / "Alerts!" per voice) holding the Case's Watches (§11): a check-ins row on top (info icon, switch bound to `checkInsEnabled`), then the Watch cards, an add FAB and an empty state. Tapping a card opens the editor sheet (create and edit, with delete behind a confirm dialog). The editor: kind (happens too often / goes quiet); for `OFTEN`, count, per day/week/month/quarter, the measure (occurrence count / days active, duration Cases only), the lookback (two presets that scale with the chosen period, or custom days), and an "intensity at least" toggle that reveals a 1–5 picker only when switched on (intensity Cases only; tapping a level highlights it and every level above it); for `QUIET`, a day count. Header: icon, name, share action (§13, opens the Insight/Log chooser), config access. Description shown below the header, above the tabs, when the Case has one set. |
| **New/edit Case** | Name (required, capped at 60 characters, must be unique among active Cases case-insensitively), optional description (capped at 90 characters), collapsible icon picker (expanded by default for a new Case, collapsed with an icon summary when editing), logFlow, durationMode, intensity toggle. Logging and Duration each carry a tappable info icon opening a plain explanatory dialog. The Logging control's "One tap" option is disabled whenever durationMode is Manual and/or intensity tracking is on (one-tap can't capture a typed duration or intensity rating; Start/stop is unaffected) — an existing Case's logFlow silently corrects to Detail sheet the moment its duration/intensity settings make One tap invalid, whether that happens while editing or because a previously-valid stored value became invalid. Changing durationMode while the Case has open-ended events raises a confirm dialog in either direction — leaving Start/stop stops running events now, entering it collapses open-ended events to instant events (§6 transition contract); cancelling either dialog leaves the mode unchanged. Header also carries an **Archive** action on an existing Case (confirm dialog noting the Case stays intact and pointing to Archived Cases for permanent delete; not shown when creating a new Case) — navigates to Home on confirm. |
| **Archived Cases** | List of archived Cases (icon, name, event count). Per row: **Unarchive** (immediate, reversible) and **Delete forever** (confirm dialog naming the event count; permanent, cascades to events/watches). Top bar: **Clear archive** (shown only when the list is non-empty; confirm dialog naming the archived-Case count; permanent, cascades the same as per-row delete). Reached via Home's archived-cases link. |
| **Log detail sheet** | §6 — logging a *new* event; reachable from widget (trampoline activity), Home, case detail's History-tab FAB. |
| **Edit event** | §6 — full-screen editor for an existing event (`TopAppBar` back arrow + delete action, mirroring New/edit Case). Reached from Case Detail's History tab by tapping a row. |
| **Share preview** | §13 — Share screen (titled "Share"): Summary / Insights / History tabs, a shared "Name on card" field above them, and per tab a "Preview" heading over the card on its own tinted stage. Insights (Story) adds the section toggles and drag-to-reorder rows (long-press a row to move it; only for sections the Case has data for), placed above the stage. Share button below. |
| **History Share preview** | §13 — the History tab of the Share screen: date-range/field/sort controls above a capped card preview (deliberately the reverse order of Insight's layout, so a long log doesn't crowd the controls off-screen), share button. |
| **Settings** | Grouped by area, each in its own card: **Support** (About, Rate the app — still a placeholder pending a store listing, Contact us — opens an email compose intent to the developer address); **Appearance** (theme/voice picker with a tappable info icon explaining themes, no live preview; a 12-hour / 24-hour time-format toggle, seeded from the device clock setting until the user picks); **Check-ins** (default interval: off / 7 / 14 / 30 days); **Data** (cloud-backup opt-out toggle with a tappable info icon, default on — a **Manage tags** row opening the screen in the table below; export opens a JSON/CSV format-choice dialog, each option with a one-line explainer, filenames timestamped to tell exports apart; import JSON; delete data: a choice between all data or logs before a chosen date, defaulting to today — confirm dialog, permanent); a hidden **Developer Mode** area, unlocked by a tap-pattern gesture on About's version row, currently holding "Load demo data". |
| **About** | A short "what HODITH is" blurb, Version (a tap-pattern gesture on it unlocks Settings' hidden Developer Mode area), privacy statement — HODITH itself sends nothing anywhere, and explains how Android's own device backup can still carry its data unless opted out via the Data section's toggle — with a link to the full hosted privacy policy, licenses (open-source dependencies and their license). |
| **Manage tags** | Reached from Settings' Data section. Every tag across all Cases, archived ones included, sorted by name, each with its event count. A filter field appears above the list only once there are more than 10 tags; it matches part of a name, ignoring case. Each row has **Edit** (rename, in a small text dialog) and **Delete**. Every operation shows a warning naming what changes and how many events it touches, and nothing is written until the warning is confirmed; there is no undo. Renaming to a name another tag already holds, ignoring case, is a **merge**: the renamed tag's events move to the existing tag, an event that carries both keeps one attachment, the existing tag keeps its spelling, and the renamed tag is removed. Changing only the case of a tag's own name is a plain rename. Deleting removes the tag from every event and leaves the events in place. |

## 15. Widgets (Jetpack Glance)

- **List widget** (resizable): shows the Cases picked via its own configure picker (per-widget-instance — each placement can pick a different set); each row = icon, name, today count, one-tap log (respecting the Case's `logFlow` — `DETAIL_SHEET` opens the sheet via trampoline). A Case with a running event shows the "Ongoing" pill + elapsed (one) or a count (several) in place of the today count; the log button stays put (it starts a second event on a `START_STOP` Case), and tapping the row opens Case Detail to Stop (§6).
- **Single-case widget** (small): bound to one Case via its own configure step. Shows icon + today count; a dedicated log button respects the Case's `logFlow` (matching the List widget's per-row treatment), tapping elsewhere on the Case opens its detail screen. A running Case shows the "Ongoing" pill + elapsed/count, same as the List widget.
- Known Glance constraint: widget theming is limited, so widgets always render the Plain theme's light palette, regardless of the user's chosen in-app theme or the system's light/dark mode. Documented as a known limitation.
- Widget rendering targets Plain's type ramp (icon/name/subtitle sizes and weights) and spacing/shape tokens too, not only its palette — DEV_PLAYBOOK §4 has the exact mapping and Glance's platform limits on it.

## 16. Data, privacy, distribution

- All data local: Room DB + DataStore prefs. No network permission in the manifest.
- **Export/import**: full JSON (Moshi), schema-versioned (`schemaVersion: 1`). Import checks the file's shape and content — field rules and referential integrity across the backup — before writing anything, then restores atomically inside one transaction. A CSV export sits alongside it for tabular use: one row per event across all active (non-archived) Cases, export-only since a flattened tabular shape can't round-trip back into the relational schema.
- Android auto-backup enabled by default (`allowBackup`, unrestricted `data_extraction_rules.xml`), with a Settings toggle (default on) to opt out — documented on the About screen. One toggle governs both cloud backup and device-transfer, since both go through the same `onFullBackup` path on API 31+; enforcement lives in `HodithBackupAgent`, since the manifest flags themselves are static and can't be flipped at runtime. Opting out only stops future backups — it doesn't purge a backup already made.
- Free, no ads, no IAP at launch.
- Play data-safety form: no data collected.

## 17. Future work (deferred)

Ideas explicitly considered and set aside rather than built now. Each carries a trailer — **Status** (whether any of it is already built), **Effort** (S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week), **Touches** (where the change lands), **Lean** (a suggestion to inform the decision, not the decision itself).

The list, in no particular priority order:

- **Confirmed-quiet checkpoints** — the check-in "All quiet" answer could be stored, letting verdicts distinguish confirmed silence from unknown silence and raising confidence accordingly. Adds an entity and verdict complexity; revisit after v1 data habits are observed.

  *Status: open — the "All quiet" action exists, but `CaseEntity.lastCheckInAt` keeps only a single overwritten re-arm anchor, read by check-in scheduling and never by `VerdictEngine`, so no history is being accumulated today · Effort: M · Touches: a new entity, `VerdictEngine` and its confidence tiers, Voice ×3 · Lean: hold until v1 data habits are observed, as written.*
- **Weekly digest notification** — opt-in "your week in events" summary. Needs a product stance before implementation: a weekly recap of the user's own logging is the exact shape a streak takes, and §4 rules out anything reading as encouragement or scolding. It stays observational only if it reports what happened, never how diligently the user logged it.

  *Status: open · Effort: M · Touches: the WorkManager evaluation schedule, `Notifier`, a Settings toggle, Voice ×3 · Lean: hold — settle the copy stance first; it may not survive it.*

## 18. Tech stack

The tooling version matrix and gotchas live in DEV_PLAYBOOK §5.

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM — ViewModel + StateFlow; `HodithRepository` single source of truth |
| Widgets | Jetpack Glance |
| Storage | Room (SQLite), local only |
| DI | Hilt |
| Navigation | Navigation Compose |
| Settings | DataStore Preferences |
| Serialisation | Moshi (export/import) |
| Background | WorkManager (watch & check-in evaluation) |
| Min SDK | API 31 (Android 12) |

Suggested package: `com.secondmonday.hodith`.
