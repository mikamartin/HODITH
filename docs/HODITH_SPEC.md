# HODITH — Product Specification

**HODITH: How Often Does It Truly Happen** — a local-only Android app that answers one question: how often does that *actually* happen?

---

## 1. The idea

Sometimes a thought hits you: *"this always happens"* — or *"this never happens anymore."* Usually you don't actually know. HODITH lets you check.

You open a **Case** on the thing you've noticed, log occurrences as life happens, and the app shows you the reality: how often it happens, when it clusters, and — if you stated a **Hunch** up front — whether your gut feeling was right.

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
| **Hunch** | Your stated feeling about frequency: "this happens ~daily", "not nearly enough". |
| **Verdict** | What the data says about your Hunch, once there's enough of it. |

The case → evidence → hunch → verdict framing is deliberate: it gives all three voices a shared metaphor to play with.

## 3. Design principles

1. **The Hunch is the hero.** A Case can carry a Hunch — *"I feel this happens ~daily."* After enough logs the app renders a Verdict: "You felt this happened daily. Reality: 2.1×/week." That confirm-or-bust moment is the payoff of the whole app.
2. **Logging is neutral.** An event not happening is information, not failure. The app never congratulates, never scolds, never asks where you've been.
3. **Logging must survive real life.** Events happen mid-argument or mid-sneeze. One tap from the widget logs "it happened, now." Details are optional and can be added later. Retro-logging is first-class — you often only realize afterwards.
4. **Statistical honesty.** With too few logs there is no verdict — the app says "early days, keep logging" instead of pretending. Small-sample humility keeps the app trustworthy (and is a fun place for theme voice).
5. **Show, don't lecture.** The flagship visual — the Big Picture — puts all your cases on one shared calendar and lets your own eyes spot the patterns when icons land on the same day.
6. **Every Case has a face.** Each Case gets an icon (emoji), shown everywhere it appears — Home, Big Picture, widgets, notifications. Icons are the primary way cases are told apart; color is never the only distinguisher (easier to remember, better for accessibility).
7. **Everything stays on the phone.** No accounts, no analytics, no network permission at all. Export/import is the user's escape hatch.

## 4. Non-goals (v1)

- **HODITH is not a habit tracker.** No streaks, points, scores, rewards, or reminders to "do the thing" — Cases are often about events nobody controls. If a feature idea pushes toward behaviour change rather than observation, it doesn't belong.
- No cloud sync, accounts, or telemetry.
- No computed cross-case correlation ("X causes Y") — the Big Picture shows co-occurrence visually; the math version is Future Work (§17).
- No third-party charting library — v1 visuals are custom Compose.
- No iOS, no tablet-optimised layouts.

## 5. Core concepts & data model

Room (SQLite), local only. Timestamps stored as epoch millis UTC; displayed in device timezone.

### Case

| Field | Notes |
|---|---|
| id | PK |
| name | e.g. "Kiddo was rude" |
| description | nullable String — optional longer freeform text beyond the title |
| icon | emoji, required — the Case's visual identity everywhere |
| createdAt | start of the observation window (see §8) |
| logFlow | `ONE_TAP` \| `DETAIL_SHEET` — what the widget/log button does |
| durationMode | `NONE` \| `MANUAL` \| `START_STOP` |
| intensityEnabled | boolean — show 1–5 intensity on the detail sheet |
| hunchNudgeDismissed | boolean — user said "stop asking" (see §7) |
| pinned | boolean — appears in the list widget |
| checkInDays | nullable Int — days of total silence before a check-in nudge (§11); `null` = use the app-level default from Settings, `0` = off for this Case |
| lastCheckInAt | nullable — when a check-in last fired or was answered "all quiet"; used for re-arming |
| sortOrder | manual ordering on Home and Big Picture |
| archived | boolean — hidden from Home/widgets/Big Picture, data retained |

Case deliberately has no fixed valence field of its own — direction stays scoped to `Hunch.direction`
below, which can change over a Case's life as hunches resolve and new ones are made, and includes a
neutral `JUST_CURIOUS` option. A Case with no Hunch has no framing at all, matching §4's
"observation, not judgment" stance.

Archiving is reversible and non-destructive. **Hard-deleting a Case** is a separate, irreversible
action, reachable only from the Archived Cases screen (§14) on a case that's already archived —
never directly from an active Case. It cascades to the case's events, hunches, and triggers (FK
cascade delete, same as `Event.caseId` below).

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

### Hunch

Optional, at most one active per Case.

| Field | Notes |
|---|---|
| id | PK |
| caseId | FK |
| direction | `TOO_OFTEN` \| `NOT_ENOUGH` \| `JUST_CURIOUS` |
| expectedCount | Int |
| expectedPer | `DAY` \| `WEEK` \| `MONTH` |
| createdAt | verdict compares reality since the hunch was made and overall |
| resolvedAt | nullable — user can close a hunch and keep the verdict in history |

### Trigger

Optional, many per Case.

| Field | Notes |
|---|---|
| id | PK |
| caseId | FK |
| kind | `AT_LEAST` (n+ events within window) \| `SILENT_FOR` (no events for n days) |
| threshold | n |
| windowDays | rolling window for `AT_LEAST` (7, 30, or custom) |
| enabled | boolean |
| lastFiredAt | nullable — dedupe: fire once per window / once per silence streak |

Verdicts are **computed, never stored** — the verdict engine is a set of pure functions over `(hunch, events, now)`. Deliberate: it makes the app's riskiest logic its most unit-testable surface.

## 6. Logging flows

- **One-tap** (widget or Home row): inserts an Event at `now`. In-app shows a snackbar with Undo; from the widget the event is silently created (editable/deletable in-app — Glance can't do transient undo reliably).
- **Detail sheet** (bottom sheet, ≤5 seconds to complete): time (defaults now, editable → retro-log), intensity 1–5 (if enabled), duration or Start (per durationMode), note, tag chips with autocomplete. Big primary button saves; everything optional.
- **Start/stop**: Start creates an Event with `endedAt = null`. Home and case detail show the Case as **ongoing** with elapsed time (ticking, ~60 s refresh) and a Stop action; Stop is always immediate, regardless of `logFlow` — no sheet, since whatever detail was worth capturing was already captured at Start or can be added via edit afterward. One ongoing event per Case; starting again while ongoing isn't offered — the quick-log/Start affordance becomes the Stop action instead. An ongoing event 24 h or older surfaces a gentle prompt on both screens: "Still going, or forgot to stop it?" (edit end time / still going). Dismissing ("still going") re-arms the prompt after another 24 h rather than silencing it forever, so a genuinely-forgotten event doesn't go quiet indefinitely. Widget support for ongoing/elapsed lands with the widgets themselves (§15, Phase 7).
- **Retro-log**: from case detail, "It happened earlier…" → detail sheet with date/time picker. For a `START_STOP` case this is also how a fully-known past start+end range gets logged — the sheet's End section defaults to "Ongoing" but accepts an explicit end date/time, which is what leaving it unset vs. setting it actually means.
- Every event is editable and deletable from the case's event list. Each row shows its timestamp (weekday + date, year only when it differs from the current year, plus time-of-day) and, inline on a second line, an ongoing/duration indicator, intensity/note/tags when present — an ongoing event shows "Ongoing"; a finished duration event (any mode with a real `endedAt`) shows how long it lasted. Currently always shown, no per-user toggle — flagged in PROGRESS.md to reconsider as a Settings switch once Settings (§14) gets real content.

## 7. Hunch flow

- Case creation asks: *"Got a feeling about this one?"* — skippable in one tap.
- **Nudge:** after 5 logged events on a hunch-less Case, the case detail screen shows a dismissible card inviting a Hunch. "Don't ask again" sets `hunchNudgeDismissed`. The nudge lives in-app only; it never notifies.
- Creating a Hunch: direction → expected frequency (count + per day/week/month). Voice-flavoured copy throughout.
- A Hunch can be resolved ("verdict accepted"), archiving it to the Case's hunch history; a new Hunch can then be made. The history of hunches vs verdicts is itself a fun artifact ("you've been wrong about this three times").

## 8. Verdict engine

Pure Kotlin, no Android dependencies. Inputs: hunch, event list, `now`.

- **Observation window** starts at `min(case.createdAt, earliest event.occurredAt)` and ends at `now`.
- **Observed rate** = event count ÷ window length, normalised to the hunch's `expectedPer` unit.
- **Confidence tiers** (both conditions required per tier):
  - **No verdict** — fewer than 5 events *or* window < 14 days → "early days" state
  - **Preliminary** — ≥5 events and ≥14 days
  - **Confident** — ≥15 events and ≥28 days
- **Comparison bands** (observed ÷ expected): `<0.5` much less · `0.5–0.8` less · `0.8–1.25` about right · `1.25–2.0` more · `>2.0` much more. Each cutoff itself belongs to the higher band (e.g. exactly `0.8` is "about right", not "less").
- Rendering is direction-aware: for `TOO_OFTEN`, "much less" is a relief; for `NOT_ENOUGH`, it's a confirmation. `JUST_CURIOUS` gets neutral phrasing. All copy comes from the Voice layer (§12).
- Cases without a Hunch still get visuals and stats (§9–10), just no verdict card.

## 9. Visualizations

The part of the app that makes occurrences *visible at a glance*. All custom Compose, all honouring the "early days" rule — below minimum data they show a friendly placeholder, never an empty chart pretending to mean something.

### The Big Picture — flagship view

A scrollable multi-month calendar grid (day columns × week rows, like a standard month calendar). Every active Case's icon appears in the cell for each day it has an event. Scrolling swipes through time — oldest at the top, most recent at the bottom, opening on the current month; there is no pinch/continuous zoom, since a calendar grid's natural unit is already a day/week/month, not an arbitrary time window. Tapping the month label opens a quick-jump picker to reach a distant month instantly instead.

- Cross-case correlation — the whole point of this screen — comes from multiple case icons landing in the same day cell, not from vertical alignment across per-case rows: when "unbearable day" and "kiddo was rude" land in the same cell, you see it, no statistics required, no causation claimed.
- Only days up to and including today are ever shown; future days render as blank space.
- A day cell belongs to exactly one month — the neighbouring month's leading/trailing days that would normally pad out a boundary week are left blank rather than duplicating that date under both months.
- A day over its icon capacity shows a "+N" overflow badge rather than silently cropping.
- Tapping a day opens that day's logged events (case, note); a separate small chevron on each week opens a week view listing that week's events with full case names — kept as its own tap target from the day cells.
- Case filter chips (icon + name, ordered by Home's manual `sortOrder`) sit above the grid, doubling as a legend and a per-case visibility toggle.
- Intensity and duration are not encoded on the grid — a day cell shows icon-only, cross-case co-occurrence; both remain visible in the day/week detail dialogs' event notes and in a case's own stats (§10).
- Early-days placeholder is two-tier: zero active Cases shows the same empty state as Home; at least one Case but zero events logged anywhere shows a distinct "not enough data yet" placeholder. Below either threshold the grid itself never renders.

*(This replaces an earlier row-per-case/shared-horizontal-time-axis/pinch-zoom design, retired after on-device testing showed it didn't read clearly and since deleted outright — see PROGRESS.md for the build/retirement history.)*

### Per-case: dot timeline (primary)

The same dot-on-a-time-axis rendering, one Case, full width, at the top of the case detail screen. Bursts, droughts, and rhythm are visible before a single number is read. Current gap is annotated ("11 days quiet").

### Per-case: calendar heatmap (secondary)

A year-in-pixels month grid — each day a cell, shaded by event count that day. Cozier, good for "what did this month look like". Sits below the dot timeline on the Insights tab.

## 10. Stats (descriptive)

On the case detail Insights tab, below the visuals:

- **Frequency over time** — counts per day/week/month (granularity auto-picked from data density, user-overridable)
- **Rhythm heatmap** — day-of-week × time-of-day grid, cell shade = count
- **Gaps & clusters** — longest gap, current gap, average gap; "tends to come in bursts" flag when gap variance is high
- **Trend arrow** — last 30 days vs the 30 before (needs ≥ 8 weeks of data, otherwise hidden)
- **Duration stats** (if durationMode ≠ NONE) — average, longest, total time
- **Intensity stats** (if enabled) — average, distribution mini-bars
- **Tag breakdown** — counts per tag

## 11. Triggers, check-ins & notifications

### Triggers (user-configured, about the event)

- Evaluated (a) immediately on every event insert/edit/delete, and (b) by a WorkManager periodic job (~every 6 h) so `SILENT_FOR` triggers can fire without any logging happening.
- `AT_LEAST`: fires when the rolling-window count reaches threshold; re-arms when it drops below.
- `SILENT_FOR`: fires when the gap since the last event reaches n days; re-arms on the next event.
- Notification content is voice-flavoured and factual: icon + count + case name + "tap to see". Information, not advice.

### Check-ins (app-initiated, about the data)

Silence in a Case is ambiguous: did the event stop happening, or did the user stop logging? A check-in resolves that — it's data hygiene, not a nag, and the copy makes the distinction: it asks whether anything went unlogged, never implies the user should "keep it up".

- A check-in fires when a Case has had **zero events for `checkInDays`** — counting from the latest of: last event, last check-in, or case creation. This automatically covers the created-but-never-logged Case ("You opened 🐕 *Dog barking* 14 days ago — nothing logged yet. All quiet, or forgot it exists?").
- **Timing defaults:**
  - Case with a Hunch — derived from the expected rate: expected gap = period ÷ expectedCount, check-in after **2 × expected gap**, clamped to 3–30 days. If you said "3× a week" and a week passes silently, that's exactly when a heads-up is useful.
  - Case without a Hunch — the **app-level default** from Settings (`off / 7 / 14 / 30 days`).
  - Either can be overridden per Case (`checkInDays`), including off.
- Notification actions: **Log** (respects the Case's `logFlow` — one-tap logs directly, detail-sheet opens the sheet) and **All quiet** (re-arms the check-in; no event created).
- Anti-spam: check-ins are evaluated by the same WorkManager job as triggers; multiple due check-ins collapse into a single summary notification ("3 cases are quiet — tap to review"). A Case never fires a check-in more than once per `checkInDays` period.

### Permissions

**POST_NOTIFICATIONS** runtime permission is requested when the user creates their first trigger or first enables check-ins — never on first launch. If denied, both triggers and check-ins still evaluate and appear as in-app banners on Home.

## 12. Themes & voices

Three themes, picked in Settings, applying skin + voice together:

| | Plain | Intense | Bright |
|---|---|---|---|
| Palette | Cool neutrals, one restrained accent | Monochrome (black/white/gray) plus one crimson accent, reserved for interactive elements — genre film-noir, not gothic-archive | Warm brights, a playful accent pair |
| Type feel | Clean, businesslike (Inter) | High-contrast, pulp-poster dramatic — bold condensed display face (Oswald) over a readable serif body (Source Serif 4) | Rounded, friendly (Baloo 2 display / Nunito body) |
| Verdict sample | "Observed: 2.1×/week — below your estimate." | "Your dread was exaggerated. It happens but twice a week." | "Plot twist: only 2×/week. Your brain lied!" |
| Early-days sample | "Insufficient data. Keep logging." | "The evidence is yet insufficient for despair or joy." | "Too soon to tell — feed me more moments!" |
| Empty state sample | "No cases yet." | "Nothing is being watched. Yet." | "It's quiet in here… suspiciously quiet." |
| Home header | "How often does it truly happen?" | "How oft dares it truly haunt?" | "How often does it totally happen?!" |

All three Home header phrasings mean the same thing and each one's six words' first letters still spell **H-O-D-I-T-H**, matching the app's own name.

**Architecture:** every user-visible string is a key on a `Voice` interface with three implementations (`PlainVoice`, `IntenseVoice`, `BrightVoice`), provided via CompositionLocal — no `when(theme)` in composables. A unit test asserts every key is non-blank in all three voices, so a string can never silently ship in only one voice. Full light/dark mode within each theme, each with its own `ColorScheme`, `Typography`, and `Shapes` (`ui/theme/`).

## 13. Sharing a Case

The social payoff of the app: turning a finished (or in-progress) investigation into something you can drop into a group chat or post as a story. *"I checked: it does NOT always rain on my day off."*

- **Share card** — a rendered image, generated locally (offscreen Compose → bitmap → Android share sheet via FileProvider; no network involved, consistent with §16). Two sizes: **story** (1080×1920, 9:16) and **square** (1080×1080) for feeds and messengers.
- **Story template** — the card tells the investigation as a short arc, in the active theme's skin and voice:
  1. *The case* — icon + name ("Case: ☕ Perfect coffee")
  2. *The hunch* — "I felt this almost never happens"
  3. *The evidence* — the dot timeline snippet + headline numbers ("14 times in 60 days")
  4. *The verdict* — the voice-flavoured punchline ("Plot twist: more often than I thought.")
  Cases without a Hunch get a two-beat version: the case + the evidence.
- **Templates are theme-based** — Plain renders like a clean report card, Intense like a pulp detective dossier (high-contrast black-and-white, a stamped-crimson accent), Bright like a playful reveal. The template follows the *currently active* theme; switching themes before sharing restyles the card.
- **Preview before share, always.** The share flow opens a preview screen where the user can:
  - pick story vs square,
  - edit the displayed case name (real names can be personal — "Kiddo was rude" might become "Someone was grumpy"),
  - toggle sections on/off.
  Notes and tags are **never** included on share cards — they're the most personal data in the app and stay out entirely.
- Entry point: Share action on the case detail header. Sharing the Big Picture (multiple cases at once) is deliberately excluded — see §17.
- HODITH branding on the card is a small, unobtrusive footer ("counted with HODITH") — honest attribution, not an ad.

## 14. Screens

Bottom navigation: **Home · Big Picture · Settings**.

| Screen | Contents |
|---|---|
| **Home** | Case list (drag to reorder): icon, name, today/this-week count, quick-log button, ongoing indicator. FAB: new Case. Trigger banners if notifications are denied. Text link to **Archived Cases**, shown only once at least one Case is archived. |
| **Big Picture** | §9 flagship view. |
| **Case detail** | Tabs: **Log** (event list, retro-log, edit/delete), **Insights** (visuals §9 + stats §10), **Hunch** (verdict card or hunch creation, hunch history). Header: icon, name, share action (§13), config access. |
| **New/edit Case** | Name, optional description, collapsible icon picker (expanded by default for a new Case, collapsed with an icon summary when editing), logFlow, durationMode, intensity toggle, pinned toggle, check-in override (default / custom days / off, styled as the same segmented control as logFlow/durationMode); then the skippable Hunch step. Logging, Duration, and Check-in each carry a tappable info icon opening a plain explanatory dialog. The Logging control's "One tap" option is disabled whenever durationMode is Manual and/or intensity tracking is on (one-tap can't capture a typed duration or intensity rating; Start/stop is unaffected) — an existing Case's logFlow silently corrects to Detail sheet the moment its duration/intensity settings make One tap invalid, whether that happens while editing or because a previously-valid stored value became invalid. Header also carries an **Archive** action on an existing Case (confirm dialog; not shown when creating a new Case) — navigates to Home on confirm. |
| **Archived Cases** | List of archived Cases (icon, name, event count). Per row: **Unarchive** (immediate, reversible) and **Delete forever** (confirm dialog naming the event count; permanent, cascades to events/hunches/triggers). Reached via Home's archived-cases link. |
| **Log detail sheet** | §6 — reachable from widget (trampoline activity), Home, case detail. |
| **Share preview** | §13 — card preview, story/square toggle, editable display name, section toggles, share button (system share sheet). |
| **Triggers** | Per Case: list, create, enable/disable. |
| **Settings** | Theme/voice picker with live preview card, default check-in interval (off / 7 / 14 / 30 days), "Load demo data" / "Delete all data" (confirm dialog; permanent), export/import JSON, About. |
| **About** | Version, privacy statement ("everything stays on your phone"), licenses. |

## 15. Widgets (Jetpack Glance)

- **List widget** (resizable): pinned Cases; each row = icon, name, today count, one-tap log (respecting the Case's `logFlow` — `DETAIL_SHEET` opens the sheet via trampoline). Ongoing Cases show elapsed time + Stop.
- **Single-case widget** (small): one Case; tap = log per its flow; shows icon + today count; ongoing state supported.
- Known Glance constraint: widget theming is limited, so widgets use a fixed neutral palette that sits acceptably in all three themes. Documented as a known limitation.

## 16. Data, privacy, distribution

- All data local: Room DB + DataStore prefs. No network permission in the manifest.
- **Export/import**: full JSON (Moshi), schema-versioned (`schemaVersion: 1`), import validates before touching the DB and is all-or-nothing.
- Android auto-backup enabled — documented on the About screen.
- Free, no ads, no IAP at launch.
- Play data-safety form: no data collected.

## 17. Future work (deferred; data model stays ready)

- **Computed cross-case co-occurrence** — the math version of what the Big Picture shows visually. Statistically treacherous (small samples, confounders); needs UX that suggests, never asserts.
- **Charting library evaluation** — if custom Compose visuals hit their limit (zoom, very long ranges), evaluate Vico or similar.
- **Tag-level insights** — verdicts and triggers scoped to a tag ("rude *at dinner*").
- **Big Picture sharing** — a multi-case share card. Excluded from v1: several case names on one image multiplies the privacy footguns; needs careful anonymisation UX first.
- **Animated story export** — the four-beat arc as a short video/GIF for stories. Static cards first.
- **Theme/voice mixing** — pair, e.g., goth skin with serious voice, if users ask.
- **Confirmed-quiet checkpoints** — the check-in "All quiet" answer could be stored, letting verdicts distinguish confirmed silence from unknown silence and raising confidence accordingly. Adds an entity and verdict complexity; revisit after v1 data habits are observed.
- **Weekly digest notification** — opt-in "your week in events" summary.
- **Wear OS tile** — one-tap logging from a watch.

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
| Background | WorkManager (trigger & check-in evaluation) |
| Min SDK | API 31 (Android 12) |

Suggested package: `com.secondmonday.hodith`.
