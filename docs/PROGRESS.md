# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Standalone** — isolated items with no cross-dependencies; pick any when resources are thin.
- **Deferred** — startable, but intentionally held back pending a trigger (usually real alpha usage) rather than gated on something external.
- **Blocked** — gated on something external; not startable now.

Each item carries:

- a **trailer** — *Branch · Complexity · Priority · Area*. Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week (same scale as HODITH_SPEC §17). Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet. Area is a loose bucket — Bug / Big Picture / Insights / Hunch / Share / Settings / Voice / Performance / Repo.
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
- [ ] `docs/mockups/share-cards-prototype.html` deleted and its `ShareCardDecoration.kt` KDoc pointer dropped — it was the last mockup left in that directory, kept only as this item's Story/Square section-layout reference (`chore/prune-design-mockups` removed the other five).

**Plan** — needs a product decision first: which sections (and in what fixed order) Square always shows. Once decided: show `SectionsPicker` only when `ShareCardFormat.STORY` is selected in `SharePreviewScreen.kt`, and have `shareCardState()` source Square's sections from the fixed preset, independent of `selectedSections`.

**Tests** — `ShareCardStateTest.kt` needs coverage that Square's output is driven by the preset; `SharePreviewScreenTest.kt` needs coverage that the section picker appears only for Story. `ShareCardTemplateTest.kt`'s Square floor/no-clip coverage should keep passing as-is, since the preset's fixed content is what it already exercises.

**Concern** — this is as much a product decision as an implementation task, and it touches Voice (Story-only picker copy), so land it before B2.

### B2 · Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and the audit needs a human ear. **Must land last** — after every other copy-touching item. Copy-touching items still open ahead of it: B1 (Story-only picker copy). The `feat/declutter-nudges` branch reworded the Serious `checkInDueNotificationBody` and renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle` (drafts in all three voices) — fold those into the audit. The `feat/insights-from-first-event` branch added `insightsNothingLoggedMessage` and `insightsSingleEventNote` (drafts in all three voices, replacing the old `insightsNotEnoughDataMessage`) — fold those in too. The `feat/big-picture-overview-detail` branch retired `bigPictureEventNoteEmptyState` (×3) and added `bigPictureDetailDialogTitle` + `bigPictureDetailEditDescription` (×3) plus four shared `get()` field labels — fold those in.

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

No cross-dependencies — **S1** (icon vector + Previews), **S2** (Trend-card calculation review), **S7** (external content). Pick by appetite.

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

### S2 · Review the Trend section's calculation and investigate additions

*Branch: `chore/trend-calculation-review` · Complexity: S to M · Priority: Low · Area: Insights*

🔍 **Investigation** — a review pass, not a known fix. 🎨 **Design decision** — any new trend readout is a product call.

Scope is the **Trend card specifically** (`InsightsTab.kt` trend section — the ↑/↓/→ arrow, `insightsTrendSentence`, and the optional gap-shift / streak-shift sentences), not the whole Insights screen.

What it computes today:

- `domain/StatsEngine.kt` `computeTrendStats` — last-30-days vs prior-30-days event count → UP / DOWN / FLAT; returns `null` below `TREND_MIN_SPAN_DAYS = 56` (`TREND_WINDOW_DAYS = 30`). `statsSections` also holds the card back below `INSIGHTS_MIN_EVENTS = 2` events, so a lone old event can't render a FLAT arrow.
- `domain/InsightsEngine.kt` `computeGapShift` / `computeStreakShift` / `shiftDirectionFor` — first half vs second half of past gaps / streak run lengths; "noticeable" gate `SHIFT_MIN_FRACTION = 0.3` and `SHIFT_MIN_ABSOLUTE_DAYS = 1.0`; needs ≥6 samples.

**Acceptance criteria**

- [ ] A written overview of the current trend + shift maths: each input, its min-data guard, and the Voice strings it drives.
- [ ] A ruling on the open questions: is a fixed 30/30-day window right, or should it scale with the observation span? does the hard 56-day cutoff leave newer cases blank too long? are UP/DOWN/FLAT the right states, or is "not enough signal yet" worth showing?
- [ ] A shortlist of candidate additions (rate change as a percentage, "trending toward / away from your Hunch", whole-history direction, …) with a keep/drop call each.
- [ ] Anything approved spun out as its own item.

**Plan** — read the `StatsEngine.kt` / `InsightsEngine.kt` trend paths, write the overview, then a short spike if a candidate needs feasibility-checking. No production code in this item.

**Tests** — none; `StatsEngineTest` / `InsightsEngineTest` gain coverage only when an approved change lands as its own item.

### S7 · Audit the hosted privacy policy and Play data-safety form

*Branch: none — external content, not a code change · Complexity: XS · Priority: Medium · Area: Settings*

🌐 **External action** — both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

**Acceptance criteria**

- [ ] Hosted policy read against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and updated wherever it still claims otherwise.
- [ ] Play data-safety answers reconciled with the same copy (once a listing exists).

**Plan** — read both against the new About copy and update wherever they still claim otherwise.

### S8 · Rhythm card: legend spacing, cell size, and tap interaction

*Branch: `feat/rhythm-card-interaction` · Complexity: S–M · Priority: Medium · Area: Insights*

🎨 **Design decision** — what tapping a Rhythm cell should open.

User testing on `RhythmCard` (`InsightsTab.kt:470-526`) flagged three things on the same screen: the gap between the time-of-day/day-of-week legend and the grid feels cramped, the cells themselves feel small, and — unlike `TagsCard`'s tag rows, which are tappable via `tappableWithDescription` and open matching logged events — Rhythm cells aren't tappable at all. Today a single `Modifier.width(RHYTHM_LABEL_WIDTH.dp)` spacer (88dp) does double duty as both the label column and the only separation from the grid, and `RHYTHM_CELL_SIZE = 20` sets both the cell size and the day header width.

**Acceptance criteria**

- [ ] A dedicated gap added between the label column and the grid, distinct from the label column's own width.
- [ ] `RHYTHM_CELL_SIZE` increased (value TBD by review) and checked against the widest supported screen width so the grid still fits without scrolling.
- [ ] Rhythm cells made tappable via the same `tappableWithDescription` pattern `TagsCard` uses.
- [ ] A design decision on what tapping a cell opens — most likely the matching logged events for that day-of-week/time-of-day combination, mirroring tag-tap behavior.
- [ ] Voice strings added for the new tap's accessibility description.

**Plan** — split `RHYTHM_LABEL_WIDTH` from a new spacing constant in `InsightsTab.kt`, bump `RHYTHM_CELL_SIZE`, and wire a `tappableWithDescription` click modifier onto each cell `Box`, routing to a new `onRhythmCellTap(dayOfWeek, timeOfDay)` callback plumbed the same way `onTagTap` is today.

**Tests** — a Compose UI test asserting Rhythm cells are clickable and invoke the callback with the tapped day/time-of-day; no `StatsEngine`/`InsightsEngine` changes needed.

**Concern** — needs the tap-target design decision resolved before implementation; prefer actually wiring the interaction through to something useful over adding a no-op ripple for consistency's sake alone.

### S9 · Frequency-over-time chart: axis labels illegible

*Branch: `fix/frequency-chart-axis-labels` · Complexity: S–M · Priority: Medium · Area: Insights*

🎨 **Design decision** — labeling strategy for a custom (non-charting-library) bar layout.

`FrequencyCard` (`InsightsTab.kt:378-452`) is a hand-built Compose bar layout, not a charting library. It only ever renders two x-axis labels — the first and last bar's period start (lines 438-449) — so every bar in between is unlabeled, which testers read as "labels missing." The two labels shown already use a shortened format (`formatFrequencyPeriodLabel`, `EventTimeFormat.kt:114-124`) but still crowd the chart edges.

**Acceptance criteria**

- [ ] A labeling strategy decided: more ticks (e.g. every Nth bar), rotated labels, or a shorter format still — whichever reads clearly at minimum supported screen width.
- [ ] Chosen approach implemented in `FrequencyCard`.
- [ ] Confirmed no label overlap at minimum supported screen width, across the shortest and longest period-count cases the card renders.
- [ ] `EventTimeFormatTest` updated if the label format itself changes.

**Plan** — prototype two or three label layouts cheaply (Compose Preview) before committing to one, since this reshapes a chart rather than fixing a single value.

**Tests** — `EventTimeFormatTest` for any format changes; Compose Preview/manual check for the chosen layout at narrow widths.

**Concern** — needs a cheap prototype/spike before committing to an approach, per the usual bar for anything that starts feeling like a redesign.

### S10 · Resolved hunches list: planks, pagination, and a clear-all setting

*Branch: `feat/resolved-hunch-list-redesign` · Complexity: M–L · Priority: Medium · Area: Hunch*

🎨 **Design decision** — plank visual style, and the show-more/clear-all copy.

Iterates on the row redesign that just shipped in `a6d70e7` (`feat/hunch-history-row-redesign`), which fixed the created→resolved stamp, reworded outcome tiers, and a history-visibility bug — but kept every resolved Hunch as a divider-separated row inside one shared `HunchCard` (`HunchHistoryCard`/`HunchHistoryRow`, `CaseDetailScreen.kt:560-598`), rendered unconditionally via `forEachIndexed` with no pagination. New user-testing ask: drop the per-item summary line, give each resolved Hunch its own full-width "plank" card — matching the pattern `HomeScreen.kt`'s `PlainPlankHomeCaseListItem` already establishes elsewhere in the app, rather than the shared-card-with-dividers layout — show only the 5 most recent by default with a "Show more" revealing the next 10, and add a settings action to clear all resolved hunches for a Case.

**Acceptance criteria**

- [ ] Each resolved Hunch rendered as its own plank/card, no shared-card dividers.
- [ ] Per-item summary line removed — decide exactly what stays (the created→resolved stamp and outcome, presumably).
- [ ] Default view shows the 5 most recent resolved hunches, newest first.
- [ ] "Show more" reveals the next 10; decide behavior after that (further paging vs. show-all).
- [ ] A settings/action to permanently clear all resolved hunches for a Case, with a confirm dialog mirroring the existing "delete all data" confirm pattern.
- [ ] Clear-all copy reviewed against spec §4 non-goals — must not read like a gamification "reset"/"fresh start," even though the action itself isn't gamification.
- [ ] Voice ×3 for all new/changed strings.

**Plan** — rework `HunchHistoryCard`/`Row` into individually-carded planks with client-side windowing (first 5, then +10 on tap); add a repository method plus a confirm dialog for bulk-clearing a Case's resolved hunches.

**Tests** — `CaseDetailScreen` Compose tests for the plank layout, the show-more reveal count, and the clear-all confirm/cancel flow; a repository/DAO test for the bulk-clear query.

**Concern** — this reopens a screen area that just shipped a redesign; re-read `a6d70e7`'s rationale before changing layout again so nothing already fixed (the visibility bug, the outcome-tier wording) regresses.

### S12 · Intense/Bright theme: exploratory testing pass

*Branch: `chore/intense-bright-theme-audit` · Complexity: S–M · Priority: Low · Area: Settings*

🔍 **Investigation** — a review pass, not a known fix.

User testing asked for an exploratory pass over the Intense and Bright visual themes (`Color.kt`, `GlowDecoration.kt`, `CardDecorationStyle.kt`, `BigPictureDecoration.kt`, `ShareCardDecoration.kt`) with an eye to minor redesigns. Scope stays restyle-only, per the standing rule from the prior Bright redesign pass — visual refinement of what already exists, not new features a mockup might otherwise suggest.

**Acceptance criteria**

- [ ] A written pass over both themes across the main screens (Home, Case Detail/Insights, Big Picture, Share, Settings) noting legibility/contrast/consistency issues.
- [ ] A shortlist of proposed tweaks, restyle-only, each with a keep/drop call.
- [ ] Approved tweaks spun out as their own follow-up items.

**Plan** — audit pass first, no code; produce a findings list. Implementation only for approved items, spun out separately.

**Tests** — none for the audit itself.

**Concern** — keep scope restyle-only; don't let exploratory testing regrow into a feature request.

### S13 · CSV export of case/event data

*Branch: `feat/csv-export` · Complexity: S · Priority: Medium · Area: Settings*

Already scoped in HODITH_SPEC §17 Future Work: CSV export alongside the existing JSON export, JSON staying canonical for import since a flattened tabular format doesn't round-trip cleanly, making CSV export-only. This item promotes that spec entry into active work — no spec change needed, just implementation.

**Acceptance criteria**

- [ ] A new CSV writer alongside the existing `BackupFileWriter` (`data/backup/`).
- [ ] A Settings row for CSV export, alongside the existing JSON export/import row.
- [ ] Voice ×3 for the new row and any share/save-location prompts.
- [ ] Confirmed export-only — no CSV import path.

**Plan** — implement per §17 as already scoped: new writer, Settings row, Voice strings.

**Tests** — a unit test for the CSV writer's output shape; `SettingsScreenTest` coverage for the new row/action.

**Concern** — none; per the spec's own note, this is the most self-contained item here.

### S14 · Settings: delete logs older than a chosen date

*Branch: `feat/bulk-delete-logs-by-date` · Complexity: M · Priority: Medium · Area: Settings*

🎨 **Design decision** — cascade behavior, plus a spec update.

Current spec §14 only supports deleting all data outright; there's no partial or date-scoped delete anywhere in spec or code. This is a genuine spec addition, not a bug fix — approving it means adding a new Data-card action to HODITH_SPEC §14.

**Acceptance criteria**

- [ ] Design decision: does this delete raw Events only, or does it also need to handle Hunches/Verdicts whose window now has missing data? What happens to stats/rhythm/frequency computations for a Case whose oldest data was just trimmed?
- [ ] A Settings row with a date picker and a destructive confirm dialog, mirroring the existing "delete all data" pattern.
- [ ] HODITH_SPEC §14 updated with the new Data-card action.
- [ ] Voice ×3 for the new row/dialog.

**Plan** — resolve the design decision above first; likely a bounded DAO delete query plus a confirm dialog reusing existing patterns.

**Tests** — a DAO test for the date-bounded delete; `SettingsScreenTest` for the picker/confirm flow.

**Concern** — touching historical data that Hunches may reference needs a clear answer on cascade/rollup effects before coding; don't assume delete-and-move-on is safe.

### S15 · Big Picture: year-level filter UX exploration

*Branch: none yet — design exploration first · Complexity: S–M (investigation) · Priority: Low · Area: Big Picture*

🎨 **Design decision** — UX approach, before any implementation.

No year-level filter exists in Big Picture today (§9 only has a scrolling multi-month grid with a month quick-jump). User testing asked for design options for a "big picture year filter" — a UX design question before it's an implementation one.

**Acceptance criteria**

- [ ] 2–3 candidate UX approaches sketched cheaply (mockup or Compose Preview) — e.g. a year selector alongside the existing month quick-jump, a year-summary zoom level, etc.
- [ ] A recommendation with tradeoffs, reviewed with the user before any production code.
- [ ] Approved direction spun out as its own implementation item.

**Plan** — cheap prototype/spike only in this item, per the standing rule for anything that starts feeling complicated.

**Tests** — none until an approach is approved and implemented.

**Concern** — don't build the real windowed-query/UI work in this item; keep it to the design spike only.

### S16 · Insights: add per-section info affordance explaining what's included

*Branch: `feat/insights-section-info` · Complexity: S–M · Priority: Medium · Area: Insights*

🎨 **Design decision** — the affordance pattern, and which sections need it.

Grew out of triaging a user report that the Duration section's average only reflects events with a recorded duration. That's confirmed correct — `StatsEngine.computeDurationStats()` (lines 156-167) already excludes no-duration events from both sum and count, per spec §10, and `StatsEngineTest` already covers this exact shape. The gap isn't the calculation, it's that the UI doesn't disclose its own scope. Rather than a one-off Duration caption, treat it as a per-section info affordance across Insights (Duration, Trend, Rhythm, Frequency, Tags) — a small info icon/tooltip stating what each section counts or excludes (e.g. "based on the N events with a recorded duration"). **S2**'s Trend-calculation review (already scoped to produce "a written overview of the current trend + shift maths … and the Voice strings it drives") is a natural source for that section's copy — sequence this alongside or after S2.

**Acceptance criteria**

- [ ] The affordance decided: info icon + tooltip/dialog vs. inline caption, consistent across every Insights section that needs one.
- [ ] Duration section gets a note when the average excludes any no-duration events (e.g. "N of M events had a duration").
- [ ] Shortlist which other sections (Trend, Rhythm, Frequency, Tags) need the same treatment and which don't.
- [ ] Voice ×3 for every new string.
- [ ] Cross-checked against S2's output before writing Trend's note, to avoid two competing descriptions of the same math.

**Plan** — design the affordance first (small spike/Preview), then wire per-section strings once the pattern is picked.

**Tests** — a Compose test that the Duration info affordance appears and shows the right counts for a Case with mixed duration/no-duration events (mirrors the existing `StatsEngineTest` fixture).

**Concern** — could sprawl if every section gets bespoke copy; keep the affordance mechanically simple (one component, per-section text) so it doesn't become its own mini-feature per section.

## Deferred

### D1 · Big Picture's grid query, windowed or not

*Branch: `refactor/big-picture-windowed-query` (if taken) · Complexity: S–M · Priority: Low · Area: Performance*

🔍 **Investigation, deferred** — `BigPictureViewModel` now reads two lean flat projections (`EventDao.observeActiveCaseEventDetails()`, `TagDao.observeActiveCaseEventTagNames()`) instead of the `@Transaction @Relation` cascade this item originally flagged (see CLEANUP_LOG). That already removes the chunked `IN (...)` sub-fetches and full-row hydration that were the measured cost, and a throwaway JVM probe confirmed the Kotlin-side mapping is cheap at S6 scale. Undecided: whether the two flat queries' raw SQL-scan cost also holds up at that scale under a write burst.

**Deferred rather than pursued next** — closing that needs a synthetic, S6-scale instrumented DB probe with no real usage behind it. Building month-range windowing on the back of a synthetic measurement, before knowing it's even felt, is speculative complexity worth avoiding; real alpha usage is a better trigger than a cautionary probe.

**Acceptance criteria**

- [ ] Alpha usage (or a deliberate decision to probe synthetically instead) confirms whether the two flat projections' SQL scans — particularly the tag-attachment join — hold up under a logging burst at real-world scale. This is the decision gate for everything below.
- [ ] If not: a `SELECT DISTINCT` per-case tag-vocabulary query sourcing `allTagNames` directly, rather than flattening every event's tags client-side.
- [ ] If still needed after that: `observeActiveCaseEventDetails` bounded to a loaded month range (half-open bounds, mirroring `eventsInWindow`), extended in chunks as the grid nears the top of its loaded range, well before the user hits the edge. The tag projection stays live-and-windowed alongside it rather than moving to on-demand fetch, unless that's also still too hot.
- [ ] Month-picker quick-jump (§9) extends the loaded range to cover the picked month before scrolling, rather than landing in an unpopulated region.

**Plan** — revisit once alpha usage says whether Big Picture feels slow at scale; only then run the probe, and only build the criteria its result actually calls for, cheapest lever first.

**Tests** — if windowing is taken: `bigPictureUiState` over a windowed event list; a DAO test for the month-range query; Big Picture Compose tests stay green.

**Concern** — scroll-triggered range extension (if taken) must not stutter or flash empty cells on a fast scroll to a distant month.

### D2 · New-case tag suggestions have no history to draw from

*Branch: none — deferred, no fix prescribed · Complexity: S · Priority: Low · Area: Bug*

🔍 **Investigation, deferred**

`TagInput.kt`'s suggestion filtering and case-insensitive dedup (`filterTagSuggestions`) are already correct; every call site (`LogDetailScreenViewModel`, `HomeViewModel`, `WidgetLogSheetViewModel`) sources its candidate list from `repository.observeTagsForCase(caseId)`. A brand-new Case's per-case tag list is empty on its very first tag entry, so nothing suggests, even when the same tag name already exists on other Cases — which is how testers ended up with near-duplicate spellings. Cross-case suggestions were considered and explicitly ruled out, so no fix is prescribed here.

**Acceptance criteria**

- [ ] Revisit with a concrete proposal once one exists — this item exists to hold the observation, not to specify a solution.

**Plan** — none yet; deferred pending a future proposal that doesn't widen tag suggestions across Cases.

**Tests** — none until a proposal is approved.

**Concern** — don't let this drift into a cross-case suggestions feature; that was explicitly declined.

### D3 · Investigate app capacity at multi-year logging scale

*Branch: none yet — investigation first · Complexity: S (investigation) · Priority: Medium · Area: Performance*

🔍 **Investigation**

User testing raised the same underlying question **D1** is deferred pending — "what's the current capacity for years of extensive records?" — but broader than D1's Big Picture-specific scope. `EventDao.observeEventsWithTagsForCase` (the unbounded query) backs every Insights/Hunch stats computation (rhythm, frequency-over-time, trend, duration averages) with no row-count limit or windowing at all; only the Log tab's own display got paged querying (`feat/log-tab-paged-query`). This user-testing ask may itself be the "real alpha usage" trigger D1 was waiting on — worth resolving together with D1 rather than as a fully separate track.

**Acceptance criteria**

- [ ] A synthetic or real multi-year dataset used to measure current behavior of the unbounded per-case stats query (load time, memory) at a defined scale (e.g. matching D1's S6 reference point).
- [ ] A stated current capacity (rows/years before a defined threshold degrades).
- [ ] A ruling on whether this satisfies D1's alpha-usage gate, supersedes it, or should stay a separate track.
- [ ] If a guardrail is warranted: a shortlist of options (windowed stats queries, a soft in-app warning at N events, etc.) with a keep/drop call each, spun out as their own item(s).

**Plan** — probe first, no production code in this item; read alongside D1 before deciding investigation scope, to avoid running two parallel capacity investigations.

**Tests** — none until a follow-up item lands.

**Concern** — don't duplicate D1's Big Picture-specific investigation; resolve the overlap explicitly.

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
