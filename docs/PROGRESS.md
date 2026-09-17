# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Story C — Insights: within-case Trends** — a sequenced set of within-case trend detectors sharing one extensible scaffold; later items add or drop one detector each and must not change the scaffold's shape.
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

### B2 · Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and the audit needs a human ear. **Must land last** — after every other copy-touching item. Copy-touching items still open ahead of it: B1 (Story-only picker copy). The `feat/declutter-nudges` branch reworded the Serious `checkInDueNotificationBody` and renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle` (drafts in all three voices) — fold those into the audit. The `feat/insights-from-first-event` branch added `insightsNothingLoggedMessage` and `insightsSingleEventNote` (drafts in all three voices, replacing the old `insightsNotEnoughDataMessage`) — fold those in too. The `feat/big-picture-overview-detail` branch retired `bigPictureEventNoteEmptyState` (×3) and added `bigPictureDetailDialogTitle` + `bigPictureDetailEditDescription` (×3) plus four shared `get()` field labels — fold those in. The `feat/resolved-hunch-list-redesign` branch retired the shared `hunchHistoryRowText` default and added `hunchHistoryShowMoreAction` + `hunchHistoryRetentionNote` (drafts in all three voices, no em dashes) — fold those in too.

**Acceptance criteria**

- [ ] A written rubric: per-voice person, tense, sentence length, punctuation/emoji budget, locked Case/Hunch/Verdict/Event/Trigger vocabulary, and an em-dash policy with per-string calls for Goth/Quirky mid-sentence pivots.
- [ ] A findings list produced first; fixes in a separate second commit.
- [ ] Audit done in slices by screen, not by reading `Voice.kt` linearly.
- [ ] New mechanical `VoiceTest` invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab/button labels, no double spaces or trailing whitespace.
- [ ] Confirmed before starting: `androidTest` references `PlainVoice` by constant, not literal, everywhere (grep for hardcoded UI literals).

**Plan** — 294 `Voice` keys total, but only 213 are declared per-voice and need independent authorship (639 strings); the other 81 are shared `get()`/default-body keys (structural chrome — nav labels, field labels, and the like) reviewed once, not per voice. ~720 strings total. Not hard, but big, and it needs a human ear rather than a mechanical pass. Write the rubric first (what "consistent" means per voice: person, tense, sentence length, punctuation and emoji budget, and a locked vocabulary for Case/Hunch/Verdict/Event/Trigger), then audit in slices by screen rather than reading `Voice.kt` top to bottom — the file is grouped by key, so reading it linearly compares the wrong things. Produce a findings list first; fix in a second commit. The rubric should explicitly cover the ~105 em dashes currently in the copy (18 Serious, 36 Goth, 51 Quirky) — most convert cleanly to a period or comma, but Goth and Quirky use the em dash roughly 2–3x more often as a genuine mid-sentence pivot (a beat before a punchline or gothic aside), so each needs a per-string call rather than a mechanical substitution.

**Tests** — `VoiceTest` today walks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. A copy audit is the right moment to add further mechanical invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab and button labels, no double spaces or trailing whitespace. Instrumented tests reference `PlainVoice.x` by constant rather than by literal, so copy edits shouldn't break them — confirm that holds everywhere before starting (a grep for hardcoded UI literals in `androidTest`).

**Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

## Story C — Insights: within-case Trends

Eight items: T1 builds the extensible scaffold, T2–T8 each add or drop exactly one candidate detector. Closes out the prior "Insights: within-case Trends section (design)" item — its reasoning (the fixed 30-vs-30 window stays as the simple immediate-shift signal but structurally can't see slow drift; the change-point detector is an addition, not a replacement; UP/DOWN/FLAT stays sufficient for the existing single-arrow card, with nuance moved to the new section instead of overloading the arrow) carries forward into T1 below. No single item rules on more than one detector's statistics at once — each of T2–T8 opens with its own scoped design decision and may close as "dropped" rather than shipping code. Every item from T1 on appends its own entry to a maintained "Trends detectors" list in `HODITH_SPEC.md` §10, so the spec always shows the current full roster in one place rather than scattering it across item-specific prose.

### T1 · Build the Trends scaffold; migrate gap/streak-shift into it

*Branch: `feat/insights-trends-scaffold` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision** — the `TrendFinding` field shape, the exact cap/reveal numbers, and the `Hint`/`Pattern` reliability vocabulary are decided here, scoped only to the scaffold itself — not to any candidate detector's own feasibility, which is each of T2–T8's own job.

New Insights section, shown only when at least one trend is actually detected — mirrors `TagsCard`'s existing `.isNotEmpty()` gate in `InsightsTab.kt`'s `StatsSectionCards` (`if (stats.tags.isNotEmpty()) TagsCard(...)`), rather than folding more into the single Trend card. Reliability is disclosed per finding, on tap, reusing the app's one existing "(i) icon → `InfoDialog`" affordance (`ui/common/SectionWithInfo.kt`, already used by `TrendCard` and every other Insights card except Tags/Intensity) — not a single section-level icon. When more findings clear their bar than the section shows by default, a "show more" reveal exposes the rest up to a stated ceiling, the same shape as the calendar heatmap's show-more/fewer months and Hunch history's retention note — never a silent cap that hides that more was found.

The existing Trend arrow card's `gapShiftDirection`/`streakShiftDirection` sub-lines move out of `TrendDisplay`/`TrendCard` and become this section's first two findings, with zero new statistics — `computeGapShift`/`computeStreakShift` themselves don't change, only what consumes their output. This validates the scaffold end-to-end (rendering, per-finding info-tap, the guardrail with a real but small finding count) before any new detector math is trusted to plug into it, and matches the prior design item's own ruling that nuance moves to the new section rather than overloading the arrow.

**Acceptance criteria**

- [ ] `TrendFinding` domain type + guardrail constants added (new `TrendsEngine.kt` or added to `InsightsEngine.kt`, following the file's existing doc-comment-cites-spec convention).
- [ ] Non-nullable `StatsSections.trends: List<TrendFinding>`; section renders only when non-empty.
- [ ] Cap + "show more" mechanism implemented with a stated default-visible count and total ceiling.
- [ ] `Hint`/`Pattern` reliability tiers defined and documented, kept distinct from `ConfidenceTier` (`NO_VERDICT`/`PRELIMINARY`/`CONFIDENT`) — Hunch's tiers measure sample-size adequacy for an average, a Trends finding's tier measures effect significance.
- [ ] Each finding row independently tappable to its own info dialog (methodology + evidence size), phrased like `voice.verdictMeta`'s "Based on $eventCount events over $windowDays days."
- [ ] `gapShiftDirection`/`streakShiftDirection` removed from `TrendDisplay`/`TrendCard`; the arrow card shows only direction/recentCount/priorCount.
- [ ] New Voice key `insightsSectionLabelTrends` (plural) added and audited against the existing singular `insightsSectionLabelTrend` (arrow card) so the two read unambiguously in all three voices.
- [ ] Wording rule stated as a written constraint for every future detector sentence: "often follows," "tends to," never "causes" (spec §4) — not yet a mechanical `VoiceTest` check, since no real detector sentence exists yet.
- [ ] `HODITH_SPEC.md` §10 gains a new "Trends detectors" subsection, seeded with its first two entries (gap shift, streak shift).

**Plan** — thread `TrendFinding` from `InsightsEngine`/`StatsEngine` through `insightsTabState()`'s `statsSections()` the same way `tags` already is; add the new composable next to `TagsCard` in `InsightsTab.kt`.

**Tests** — `InsightsEngineTest`/`InsightsTabStateTest` coverage that the two migrated findings appear/disappear exactly as `gapShiftDirection`/`streakShiftDirection` did before the move; a guardrail test with more eligible findings than the default cap.

### T2 · Detector: tag drift (share rising/falling)

*Branch: `feat/insights-trends-tag-drift` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision (this detector only)** — feasibility, minimum sample size, and keep/drop, checked against `shiftDirectionFor`'s existing dual-threshold pattern (`InsightsEngine.kt`) applied to a tag's share of events instead of a gap/streak average. Is a tag's share of a Case's events rising or falling over time (decaf 10%→40%)? No permutation test, no timezone dependency — the simplest detector to rule on first.

**Acceptance criteria**

- [ ] Feasibility ruling stated before any code; closes as "dropped" with rationale if the tag-share signal turns out too noisy at realistic sample sizes.
- [ ] If kept: a tag-share-shift function gated by its own `MIN_SAMPLE_COUNT`-style constant, returning zero or more `TrendFinding`s (one per tag clearing the bar), tiered `Hint`/`Pattern` per T1's vocabulary.
- [ ] Sentence template follows "tends to," never "causes."
- [ ] Voice ×3 for the new sentence template.
- [ ] Tests: a rising tag, a falling one, a stable one (no finding), and below-minimum-sample (no finding).
- [ ] `HODITH_SPEC.md` §10's "Trends detectors" list gains one line — or, if dropped, a short rationale left in this item instead.

### T3 · Detector: recurrence shape (hazard by time-since-last-event)

*Branch: `feat/insights-trends-recurrence-hazard` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision (this detector only)** — bins `computeGapStats`'s existing `pastGaps` (`InsightsEngine.kt`) into days-since-last-event buckets and rules on early-spike ("often follows within N days") vs. dead-zone ("almost never recurs within N days") thresholds as its own constants. A heavier, more informative sibling to the existing bursts CV flag (`isBursty`), but still descriptive arithmetic — no permutation test needed for a first pass.

**Acceptance criteria**

- [ ] Feasibility ruling stated before any code; closes as "dropped" with rationale if the hazard shape doesn't separate cleanly from noise at realistic sample sizes.
- [ ] If kept: hazard-bucket computation over `pastGaps`, gated by a minimum sample count, with early-spike/dead-zone thresholds documented as `internal const val`s in the same doc-comment style as `GAP_BURST_MIN_COEFFICIENT_OF_VARIATION`.
- [ ] At most one `TrendFinding` per Case from this detector (can't be both a spike and a dead zone).
- [ ] Voice ×3 for the new sentence template.
- [ ] Tests: a clear spike, a clear dead zone, and a flat hazard (no finding).
- [ ] `HODITH_SPEC.md` §10's "Trends detectors" list gains one line — or, if dropped, a short rationale left in this item instead.

### T4 · Detector: tag → outcome, plus the shuffle-significance helper

*Branch: `feat/insights-trends-tag-outcome` · Complexity: L · Priority: Low · Area: Insights*

🎨 **Design decision (this detector only)** · 🔍 **Investigation** — the significance approach needs a spike before it's knowable. Compares intensity, duration, and time-to-next-event for events with a tag vs. without it (e.g. "aura migraines last 40% longer"). Introduces the first real permutation test for a single Case: shuffle which events carry the tag (label permutation, not a timeline shuffle), recompute the difference many times, and see how extreme the observed difference is. The natural first home for the generic "permutation by shuffling the Case's own data" helper the prior design item's own reasoning called for — simpler than a timeline shuffle, so it lands before T5 rather than after. Whether T5 goes on to reuse this same helper is T5's call, not decided here.

**Acceptance criteria**

- [ ] Feasibility ruling stated before any code, including the permutation iteration count and seed handling (a deterministic seed keyed off the Case/detector/event-count, not real randomness, so a finding doesn't flicker in/out across app opens with unchanged data).
- [ ] If kept: a reusable permutation-significance helper in `domain/`, generic enough for label-shuffle here and a timeline-shuffle variant later; tag→outcome computation for intensity, duration, and time-to-next-event, each independently gated by minimum in-tag/out-of-tag sample sizes.
- [ ] Findings carry a `Hint`/`Pattern` tier from T1's vocabulary, not a raw p-value, in the UI.
- [ ] Voice ×3 for the new sentence template(s).
- [ ] Tests: a planted strong tag effect, a planted null (shuffled data), and below-minimum-sample.
- [ ] `HODITH_SPEC.md` §10's "Trends detectors" list gains one line per kept outcome (intensity/duration/time-to-next) — or, for any dropped, a short rationale left in this item instead.

### T5 · Detector: real change-points

*Branch: `feat/insights-trends-change-points` · Complexity: L · Priority: Low · Area: Insights*

🎨 **Design decision (this detector only)** · 🔍 **Investigation** — CUSUM vs. a simple Bayesian change-point method is still open; spike both cheaply before committing. Catches slow drift the fixed 30-vs-30 `computeTrendStats` window structurally can't see ("since around mid-March, roughly twice as often"), additive to the arrow card, not a replacement, per the prior design item's ruling. Circular-shift + Benjamini-Hochberg (the framework proposed for the separate Big Picture cross-case item) is built for pairs of Cases; this item needs its own answer for a single Case's own timeline — most likely permutation by shuffling the Case's own event times, possibly reusing T4's helper with a timeline-shuffle variant, but that reuse is this item's own call to make.

**Acceptance criteria**

- [ ] Method chosen (CUSUM or simple Bayesian) and documented with a rationale; closes as "dropped" with rationale if neither clears a reasonable false-positive rate at realistic sample sizes.
- [ ] If kept: minimum-data floor for a change-point specifically decided — may be lower than `TREND_MIN_SPAN_DAYS = 56`.
- [ ] Significance approach stated explicitly (reusing T4's helper with a timeline-shuffle variant, or a separate method, with rationale either way).
- [ ] Sentence template gives an approximate date ("since around mid-March").
- [ ] Voice ×3 for the new sentence template.
- [ ] Tests: a planted single change-point in synthetic data, a planted no-change-point null.
- [ ] `HODITH_SPEC.md` §10's "Trends detectors" list gains one line — or, if dropped, a short rationale left in this item instead.

### T6 · Detector: intensity/duration trend

*Branch: `feat/insights-trends-intensity-duration-trend` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision (this detector only)** — a slope over time for `computeIntensityStats`/`computeDurationStats`'s existing flat averages, plus a split by start time ("evening events run longer and more severe"). Rules on whether to reuse a significance helper already built by T4/T5 rather than shipping a third bespoke test. The start-time split reuses `timeOfDayFor` as-is, inheriting Rhythm's existing device-current-timezone accuracy — not a new dependency, so not blocked on the timezone item.

**Acceptance criteria**

- [ ] Feasibility ruling stated before any code, including which existing significance helper (if any) this reuses.
- [ ] If kept: slope computation + significance for intensity and duration, only for Cases where each stat's card is already shown; start-time split via `timeOfDayFor`.
- [ ] Voice ×3 for the new sentence template(s).
- [ ] Tests: a planted upward/downward slope, a planted evening-vs-day split, both nulls.
- [ ] `HODITH_SPEC.md` §10's "Trends detectors" list gains one line per kept signal — or, for any dropped, a short rationale left in this item instead.

### T7 · Detector: tag timing (weekday/time-of-day clustering)

*Branch: `feat/insights-trends-tag-timing` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision (this detector only)** — blocked on `fix/event-timezone-offset` landing first, since any same-day/time-of-day detector needs it. Tests whether a tag clusters in a weekday/time-of-day bucket beyond the Case's own base rate, comparing `computeRhythmStats`-shaped per-tag counts against the Case's overall rhythm.

**Acceptance criteria**

- [ ] Explicit gate: this item does not start implementation until `fix/event-timezone-offset` has landed.
- [ ] Feasibility ruling stated before any code, once unblocked.
- [ ] If kept: per-tag rhythm comparison against the Case's own base rate, significance via whichever helper T4/T5 established.
- [ ] Voice ×3 for the new sentence template.
- [ ] Tests: a planted weekday clustering for one tag, a planted no-clustering null.
- [ ] `HODITH_SPEC.md` §10's "Trends detectors" list gains one line — or, if dropped, a short rationale left in this item instead.

### T8 · Detector: cycles and seasonality

*Branch: `feat/insights-trends-cycles-seasonality` · Complexity: L · Priority: Low · Area: Insights*

🎨 **Design decision (this detector only)** · 🔍 **Investigation** — autocorrelation on daily counts for weekly/monthly/~28-day cycles; month-of-year comparison once a Case has 1+ years of data; an explicit weekday-vs-weekend sentence as a simpler fallback when full seasonality doesn't clear its bar. Also blocked on `fix/event-timezone-offset`, for the same day-bucketing reason as T7. Sequenced last: needs the most data of any detector here and is the heaviest single computation.

**Acceptance criteria**

- [ ] Explicit gate: this item does not start implementation until `fix/event-timezone-offset` has landed.
- [ ] Autocorrelation method + lag set chosen and documented, once unblocked.
- [ ] If kept: weekly/~28-day cycle detection gated by a minimum span; month-of-year comparison only offered once ≥1 year of data exists; weekday-vs-weekend sentence as a fallback finding.
- [ ] Voice ×3 for the new sentence template(s).
- [ ] Tests: a planted weekly cycle, a planted no-cycle null.
- [ ] `HODITH_SPEC.md` §10's "Trends detectors" list gains one line per kept signal — or, for any dropped, a short rationale left in this item instead.

## Standalone

No cross-dependencies — pick by appetite. Identified by title, not a number: numbering churned confusingly as items were added and removed, so items here are found by name or by their branch.

### App-icon handle butts directly against the lens ring with no clearance

*Branch: `fix/icon-handle-clearance` · Complexity: S · Priority: Low · Area: Bug*

In `app/src/main/res/drawable/ic_launcher_foreground.xml` the handle's inner edge (midpoint ~(62,62)) sits on the ring's outer stroke band (~63.7 along the diagonal).

**Acceptance criteria**

- [ ] The handle's two inner points (`58.818,65.182` and `65.182,58.818`) pushed outward along the (1,1) diagonal in `ic_launcher_foreground.xml`; mirrored in `ic_launcher_monochrome.xml`.
- [ ] Visible clearance between handle inner edge and ring outer stroke.
- [ ] Handle tip stays inside the 66dp adaptive-icon safe zone (shorten the handle or nudge the enclosing `group` scale if needed).
- [ ] Verified across densities, the Android 13+ themed/monochrome path, and the splash screen (which reuses the foreground).

**Plan** — push the handle's two inner points (`58.818,65.182` and `65.182,58.818`) outward along the (1,1) diagonal; mirror the change in `ic_launcher_monochrome.xml`. The handle tip is already near the 66dp adaptive-icon safe zone, so this may also mean shortening the handle or nudging the enclosing `group` scale (0.9).

**Tests** — none (Previews only, as with the icon-picker item). Verify across densities, the Android 13+ themed/monochrome path, and the splash screen.

### Case quiet vs. abandoned

*Branch: `chore/case-quiet-vs-abandoned-design` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision** — where this surfaces (an Insights card vs. the existing check-in flow) isn't settled.

Deep-work-style Cases go quiet and the app currently has no way to tell "this Case went quiet" from "the user stopped tracking it." The rule: when the current gap since the last event exceeds the Case's own 99th-percentile historical gap, *and* the user is still actively logging other Cases, ask rather than silently report a trend.

`domain/CheckIn.kt`'s `evaluateCheckIn` is the exact structural precedent, not just a related feature: it already computes `anchor = maxOf(case.createdAt, case.lastCheckInAt ?: case.createdAt, mostRecentEventAt ?: case.createdAt)`, `silentDays = daysBetween(anchor, now)`, and fires `CheckInDecision(due = silentDays >= effectiveDays, ...)` where `effectiveDays` today comes from `effectiveCheckInDays` (the active Hunch's implied gap, or a Settings default). This item is the same shape with a different, data-derived threshold: `effectiveDays` becomes the Case's own 99th-percentile historical gap (sort the gap list `computeGapStats` in `InsightsEngine.kt` already builds and index into it), and a second condition — the user has logged *some* event, any Case, within a recent window — gets ANDed onto `due` before it's treated as "ask," not just reported. Whether that becomes a third branch inside `evaluateCheckIn`'s own check-in cadence, or a wholly new sibling function evaluated alongside it, is exactly the surfacing-mechanism question below (routed through the existing check-in notification path vs. a new Insights card reading `StatsSections`).

**Acceptance criteria**

- [ ] A ruling on surfacing mechanism: new Insights card (reading a new nullable field on `StatsSections`) vs. a new branch in the check-in evaluation path (`CheckIn.kt`/`NotificationEvaluator.kt`) alongside the existing Hunch/Settings-driven `effectiveCheckInDays`.
- [ ] A percentile helper over the gap list `computeGapStats` (`InsightsEngine.kt:63`) already collects, since no percentile function exists there today.
- [ ] The two-signal rule implemented as stated: current gap > Case's own 99th-percentile historical gap, AND the user has logged *some* event (any Case) within a recent window.
- [ ] Voice ×3 for whatever prompt/copy results.
- [ ] Confirmed this doesn't read as gamification or scolding (spec §4) — framed as a question, not a nudge to resume logging.

**Plan** — settle the surfacing-mechanism question first (cheap to prototype as a static mock of both), then add the percentile helper and the two-signal check as a `domain/` function mirroring `evaluateCheckIn`'s shape.

**Tests** — a domain-level unit test for the two-signal rule (gap-exceeds-99th-percentile AND still-active-elsewhere → ask; either condition false → no prompt), following `CheckInTest`'s existing pattern for `evaluateCheckIn`; Compose/instrumented coverage once the surfacing mechanism is chosen.

### Big Picture: cross-case trend detection (design)

*Branch: `chore/big-picture-cross-case-trends-design` · Complexity: XL · Priority: Low · Area: Big Picture*

🎨 **Design decision** — a new engine and its statistical framework are a product call, not just an implementation detail. 🔍 **Investigation** — nothing here is spec'd enough to build yet.

Expands HODITH_SPEC §17's existing "Computed cross-case co-occurrence" entry, which already notes the data plumbing is in place (`observeActiveCases`, `observeActiveCaseEventDetails`, `observeActiveCaseEventTagNames`) and the real cost is statistical-honesty UX. Every existing Insights card looks at one Case in isolation; Big Picture puts all Cases on one calendar but computes nothing across them — that's where connections and possible causes live.

Candidate cross-Case detectors:

- **Lagged precedence** — for each B event, check whether an A event started within a lag window before it (3h/12h/24h/48h/72h), compare hit rate to baseline, run in reverse too; asymmetric lift suggests A leads (e.g. "late-night noise followed by a migraine the next day").
- **Suppression** — same computation, lift below 1 ("migraines are less common in the 48 hours after a workout").
- **Absence as a precursor** — test whether B is more likely when A's *current* gap exceeds A's own typical (75th-percentile) gap, not after A itself.
- **Dose-response** — bucket A-count in the prior window (0/1/2+) and look for a steady rise in B's probability, intensity, or duration; the strongest causal hint available from observational data, deserving a higher confidence tier.
- **Cross-case intensity/duration spillover** — does A's intensity/duration predict the severity of the next B?
- **Shared shifts** — run change-point detection per-Case; if two Cases shift within ~2 weeks of each other, surface it ("workouts dropped and arguments rose around the same time in March").

Architectural framework (applies to all six, and is the reusable piece other detector work should build on):

- **`Finding` interface** — every detector returns effect size, support count, a significance score, sentence-template parameters, and evidence event IDs (for the drill-down the app already has elsewhere).
- **Pipeline**: eligibility gating (same shape as existing card-visibility gates) → significance via circular shift (shift A's timeline by random offsets, ~200 runs, in whole-week steps to preserve weekday structure) → multiple-comparisons control (Benjamini-Hochberg across all pairs×lags from one run, plus a minimum lift ≥1.5/≤0.67 and support ≥5 hits) → stability check (effect holds in both history halves) → tiering (Hint → passed significance; Pattern → also stable; Strong connection → also directional with dose-response) → persist/dedupe (store `firstSeenAt` and last effect size, re-surface only on tier change, let users dismiss or mark "makes sense" and use that to rank future findings).
- **Wording rules** — "often follows," "tends to come before," "less common after"; never "causes." The honest route to causation here is directional + dose-response + stable → offer a Hunch → confirm with future data (see the Hunch extensions item).
- **Run cadence** — cheap within-Case work on event insert/edit; expensive cross-Case shift tests in a daily background job.

Two prerequisites carried in from the raw idea list:

- **Tags are global** — "home" is used by Coffee and Workout both, so any tag-aware detector must key on `(caseId, tagName)`, not tag name alone.
- **No timezone stored** — see the standalone timezone-offset item below; any same-day/lag/time-of-day detector here is blocked on it.
- **Logging lag / batch-logging exclusion** — `loggedAt - occurredAt` marks heavily backfilled events as fuzzy-timed; down-weight them in lag/time-of-day detectors, and exclude event pairs from different Cases logged within ~2 minutes of each other (batch logging creates fake co-occurrence).

**Acceptance criteria**

- [ ] A written architecture doc covering the `Finding` interface, the full pipeline, tiering, wording rules, and run cadence above.
- [ ] A keep/drop call on each of the six detectors, with the pair-count-at-alpha-scale (8 Cases → 56 ordered pairs × 5 lags = 280 tests) sanity-checked against the multiple-comparisons control.
- [ ] A ruling on where findings surface (a Big Picture section vs. a cross-Case Insights-adjacent screen).
- [ ] A testing strategy: known patterns planted in `DemoDataSeeder.kt` (e.g. noise → migraine within 24h at 3× lift; a refractory gap after migraines) with a shuffled-null-data check that no detector invents a finding that isn't there.
- [ ] `HODITH_SPEC.md` §17's "Computed cross-case co-occurrence" entry flagged for an update once any part of this is approved (not done in this item).
- [ ] Anything approved spun out as its own implementation item. No production code in this item.

**Plan** — write the architecture doc first (it's reusable regardless of which detectors are approved), then rule detector-by-detector; a throwaway JVM spike for the circular-shift significance test specifically, since it's the piece most likely to have a subtle bug (whole-week shifts, not arbitrary offsets).

**Tests** — none; detector-level tests land with each spun-out implementation item, following the planted-pattern strategy above.

### No timezone stored — same-day / time-of-day logic breaks for travelers

*Branch: `fix/event-timezone-offset` · Complexity: M · Priority: Medium · Area: Bug*

🎨 **Design decision** — backfill behavior for existing rows needs a ruling before implementation.

`EventEntity.occurredAt`/`endedAt`/`loggedAt` (`EventEntity.kt:25-29`) store epoch millis with no captured UTC offset. The bug is systemic, not one function: every domain calculation that buckets a timestamp into a calendar day or hour takes a `zone: ZoneId = ZoneId.systemDefault()` parameter, resolved at *compute* time — `StatsEngine.kt:39,82,114` (frequency bucketing, the Rhythm heatmap's `timeOfDayFor` caller, tag/date grouping), `InsightsEngine.kt:66` (`computeGapStats`), `CalendarMath.daysBetween` (`CalendarMath.kt:22`), and `CalendarGrid.kt:36,52` (the shared week/month-grid helper `weeksInGrid` and friends build on, which is also what Big Picture's day-cell placement goes through). `VerdictEngine.kt:57` hardcodes the same call (`val zone = ZoneId.systemDefault()`) rather than taking it as a parameter. `ZoneId.systemDefault()` is the *device's current* timezone at the moment Insights/Verdict/Big Picture are computed, not the timezone the event actually happened in — so a user who travels gets every past event's day/hour reinterpreted in their new location the next time anything reads it. Real correctness bug today, independent of any new analytics work, and a hard prerequisite for the Big Picture cross-case item's same-day/lag detectors.

**Acceptance criteria**

- [ ] A new `EventEntity` column (e.g. `utcOffsetMinutes`) capturing the device's UTC offset at insert time.
- [ ] A ruling on backfill: existing rows have no captured offset — assume the device's current offset (simplest, wrong for past travel) vs. leave pre-migration rows on the old read-time-timezone behavior. State the tradeoff, pick one.
- [ ] Room migration + `BACKUP_SCHEMA_VERSION` bump + import validation (three changes, not one, per HODITH_SPEC §17's own note on schema changes).
- [ ] Every `zone: ZoneId = ZoneId.systemDefault()` call site listed above (`StatsEngine.kt`, `InsightsEngine.kt`, `CalendarMath.kt`, `CalendarGrid.kt`) threaded to read the stored per-event offset instead of the default parameter; `VerdictEngine.kt:57`'s hardcoded call converted to take the same parameter.
- [ ] Tests reproducing a cross-midnight or cross-timezone scenario via a non-default-offset event/`FakeClock` combination.

**Plan** — add the column and migration first (smallest independent piece), then replace each `zone: ZoneId = ZoneId.systemDefault()` default parameter's *source* with the relevant event's stored offset at the call sites listed above, rather than changing each function's signature (they already correctly take `zone` as a parameter — the caller is what's wrong).

**Tests** — `EventEntity`/Room migration round-trip; `StatsEngineTest`/`InsightsEngineTest`/`CalendarMathTest`/`CalendarGridTest`/`VerdictEngineTest` cases with an event logged at a non-UTC offset crossing a day boundary that only shows up wrong under device-current-timezone logic.

### Notes mining for tag/Case suggestions

*Branch: `feat/notes-mining-suggestions` · Complexity: M · Priority: Low · Area: Insights*

🎨 **Design decision** — must read as an offer, never a nudge (spec §4's no-gamification stance applies directly to anything that reacts to how much a user logs or writes).

Normalize event notes, count repeated phrases, and offer a tag when one repeats 3+ times ("Burnt beans again" → suggested tag). Flag notes that mention another Case's name or a recurring cause word ("wine," "screen time") and offer "want to track this as its own Case?" Turns free text into testable data for the cross-case detectors (feeds the Big Picture item) without being a detector itself.

**Acceptance criteria**

- [ ] Phrase-repetition detection (≥3 occurrences) surfaces a tag suggestion at the point of logging, not a background nag.
- [ ] Cross-Case-name / cause-word mentions surface a "track this as its own Case?" offer, dismissible with no repeat nagging on decline.
- [ ] Confirmed against spec §4: no streak-like framing, no "you keep mentioning X" scolding tone — purely an offer.
- [ ] Voice ×3 for the suggestion/offer copy.

**Plan** — a simple normalize-and-count pass over `EventEntity.note` at logging time (no ML), feeding results into the existing suggestion filtering (`TagInput.kt:26` `filterTagSuggestions`) for the tag case; a new lightweight prompt for the Case-suggestion case.

**Tests** — unit tests for the phrase-repetition threshold and cause-word matching; Compose coverage for the suggestion/offer UI appearing and being dismissible.

### Hunch extensions: confidence projection, belief drift, perception gap

*Branch: `feat/hunch-extensions` · Complexity: M · Priority: Low · Area: Hunch*

🎨 **Design decision** — copy tone for each extension needs settling (avoid anything reading as pressure toward a particular verdict).

Three independent extensions to the Hunch feature:

- **Time-to-confidence projection** — "At the current rate, CONFIDENT in about 9 days," projected off `confidenceTierFor(observationCount: Int, windowDays: Long)` (`VerdictEngine.kt:132-140`)'s existing `PRELIMINARY_MIN_EVENTS`/`CONFIDENT_MIN_EVENTS` and `*_MIN_DAYS` constants: given the Case's current event rate, solve for the day both thresholds clear.
- **Belief drift across superseded Hunches** — when a Case has more than one Hunch over time on the same question (e.g. coffee: 3/day, then 2/day), say so: "Your expectation dropped, and the data agrees." No new query needed — `HunchDao.observeHunchHistory(caseId)` (`HunchDao.kt:27-28`) already returns every Hunch for a Case ordered `createdAt DESC`, and each resolved one already carries a frozen verdict snapshot (`HunchEntity`'s `resolved*` columns, `Verdict.kt`'s `withResolvedVerdictSnapshot`). The just-shipped resolved-Hunch list (`feat/resolved-hunch-list-redesign` — `CaseDetailScreen.kt`/`HunchTabState.kt`, 15-item retention cap via `HunchDao.deleteResolvedHunchesBeyondLimit`) is the natural surface for a belief-drift sentence between consecutive entries.
- **Perception-gap framing for `JUST_CURIOUS`** — frame the result as how it felt vs. what the data shows, rather than a verdict against an expectation.

A fourth extension — "when a cross-Case finding appears, offer to turn it into a Hunch" — is **blocked on** the Big Picture cross-case trend detection item shipping first, since it depends on that item's findings existing at all.

**Acceptance criteria**

- [ ] Time-to-confidence projection implemented as a `VerdictEngine` extension over `confidenceTierFor`'s existing thresholds, shown only where a Hunch is already `NO_VERDICT`→`PRELIMINARY` or `PRELIMINARY`→`CONFIDENT` trending.
- [ ] Belief-drift sentence shown when `observeHunchHistory` returns more than one Hunch on a comparable question, comparing consecutive resolved snapshots' `resolvedExpectedRate`/`resolvedObservedRate`.
- [ ] Perception-gap framing applied specifically to `HunchDirection.JUST_CURIOUS`.
- [ ] Voice ×3 for all new copy.
- [ ] Fourth extension noted as blocked, not attempted, until the Big Picture item lands.

**Plan** — each of the three is a `VerdictEngine`/Hunch-UI addition; implement and ship independently rather than as one bundle, since they don't depend on each other. Belief drift specifically extends the resolved-Hunch history UI that already exists rather than building new plumbing.

**Tests** — `VerdictEngineTest` coverage for the projection math and belief-drift comparison over a fixed `observeHunchHistory` fixture; Compose coverage for the perception-gap framing on `JUST_CURIOUS` Hunches.

### Trigger: suggested threshold from historical percentile + backtest preview

*Branch: `feat/trigger-threshold-suggestions` · Complexity: S–M · Priority: Low · Area: Hunch*

Suggest a `SILENT_FOR` threshold from the Case's own 90th-percentile historical gap. `InsightsEngine.computeGapStats` (`InsightsEngine.kt:63-99`) already builds the past-gap list the Rhythm/Gaps card uses, but there's no percentile helper over it today — this item adds one (sort the gap list, index into the 90th percentile), it isn't reusing existing math wholesale. When a user edits a trigger, show "this would have fired N times in the last year" by replaying the threshold against history: `TriggerEngine.evaluateAtLeast(trigger, events, now)` and `evaluateSilentFor(trigger, mostRecentEventAt, caseCreatedAt, now)` (`TriggerEngine.kt:36-55`) are both pure functions of `now`, so a backtest is a matter of calling them once per day (or per event) over the past year and counting `TriggerDecision`s where the condition newly became true — no new evaluation logic, just a historical loop over the existing ones. Unrelated to the already-parked "Hunch/Trigger relationship" item in HODITH_SPEC §17 (that's about the `AT_LEAST`/Hunch overlap question, deliberately left for alpha testing) — this is purely a threshold-tuning UX affordance and doesn't touch that decision.

**Acceptance criteria**

- [ ] A percentile helper over `computeGapStats`'s gap list; `SILENT_FOR` trigger creation defaults its threshold suggestion to the Case's 90th-percentile result.
- [ ] Trigger edit screen shows a historical-replay count ("would have fired N times in the last year") for the currently-entered threshold, for both `AT_LEAST` and `SILENT_FOR`, by replaying `evaluateAtLeast`/`evaluateSilentFor` over the past year's events.
- [ ] Voice ×3 for the suggestion and replay-count copy.

**Plan** — add the percentile helper first (small, testable in isolation); then a `domain/` function that walks a Case's event history day-by-day (or event-by-event) calling the existing `evaluateAtLeast`/`evaluateSilentFor` with a historical `now`, counting rising-edge fires.

**Tests** — unit tests for the percentile helper against a known gap list; a backtest-count test against a fixture event sequence with known fire points for both trigger kinds; Compose coverage for both appearing on the trigger edit screen.

### Audit the hosted privacy policy and Play data-safety form

*Branch: none — external content, not a code change · Complexity: XS · Priority: Medium · Area: Settings*

🌐 **External action** — both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

**Acceptance criteria**

- [ ] Hosted policy read against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and updated wherever it still claims otherwise.
- [ ] Play data-safety answers reconciled with the same copy (once a listing exists).

**Plan** — read both against the new About copy and update wherever they still claim otherwise.

### Intense/Bright theme: exploratory testing pass

*Branch: `chore/intense-bright-theme-audit` · Complexity: S–M · Priority: Low · Area: Settings*

🔍 **Investigation** — a review pass, not a known fix.

User testing asked for an exploratory pass over the Intense and Bright visual themes (`Color.kt`, `GlowDecoration.kt`, `CardDecorationStyle.kt`, `BigPictureDecoration.kt`, `ShareCardDecoration.kt`) with an eye to minor redesigns. Scope stays restyle-only, per the standing rule from the prior Bright redesign pass — visual refinement of what already exists, not new features a mockup might otherwise suggest.

**Acceptance criteria**

- [ ] A written pass over both themes across the main screens (Home, Case Detail/Insights, Big Picture, Share, Settings) noting legibility/contrast/consistency issues.
- [ ] A shortlist of proposed tweaks, restyle-only, each with a keep/drop call.
- [ ] Approved tweaks spun out as their own follow-up items.

**Plan** — audit pass first, no code; produce a findings list. Implementation only for approved items, spun out separately.

**Tests** — none for the audit itself.

### CSV export of case/event data

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

### Log entry form: Save button disappears while the tag field is focused

*Branch: `fix/log-detail-save-button-visibility` · Complexity: S–M · Priority: Medium · Area: Bug*

Reported bug: tapping into the tag input while logging or editing an event hides the Save button, so backing out of adding a tag requires tapping elsewhere first just to make Save reappear. Root cause: `LogDetailForm` (`app/src/main/kotlin/com/secondmonday/hodith/ui/logsheet/LogDetailSheet.kt`, lines 142-238) puts the Save `Button` (lines 235-237) as the *last* item inside the same scrollable `Column` as the form fields, and applies `.verticalScroll(...).imePadding()` (lines 163-169) to that whole column. When the tag `OutlinedTextField` (line 656) gains focus and the keyboard opens, `imePadding()` shrinks the viewport but nothing scrolls Save into view — it's pushed below the fold until the keyboard closes. Both `LogDetailScreen.kt` (edit) and `LogDetailSheet.kt` (new) share `LogDetailForm`, so the fix belongs there, not in either wrapper.

**Acceptance criteria**

- [ ] Save button stays visible (or is reachable without deliberately dismissing the keyboard) while the tag field has focus, in both the new-event sheet and the edit-event screen.
- [ ] No regression to the existing scroll behavior for the rest of the form's fields.

**Plan** — most direct fix is pinning the Save button outside the scrollable region (e.g. a fixed bottom bar) so it's unaffected by `imePadding()`/scroll position; alternative is auto-scrolling the focused field's container so Save stays in the IME-adjusted viewport. Since `LogDetailForm` is shared, fix once and verify both call sites.

**Tests** — Compose UI test asserting the Save button remains visible (or scrolled into view) when the tag field is focused, for both `LogDetailScreen` and `LogDetailSheet`.

### Share button: add a Log Share option alongside the existing Insight Share

*Branch: `feat/share-log-export` · Complexity: L · Priority: Medium · Area: Share*

🎨 **Design decision** — sort options, date-range UI, and column-selection UX need a ruling before implementation.

Requested: the existing share action (`CaseDetailScreen.kt:175-177` → `ShareViewModel.kt` → `SharePreviewScreen.kt`, which renders a `ShareCardTemplate` image via `ShareImageExporter`) should become one of two options — keep it as "Insight Share," and add a new "Log Share" that exports the Case's raw log data as a shareable file rather than an image: configurable sort order, a date range, and toggleable columns (tags, notes, duration, intensity), showing only the columns applicable to that Case. Column applicability should key off `CaseEntity.durationMode`/`intensityEnabled` (`CaseEntity.kt:16-17`), the same way `SharePreviewScreen.kt`'s `availableSections` already gates Insights sections by Case config — that gating logic is directly reusable as a pattern here.

Distinct from two existing/adjacent items: the Settings-level **CSV export** item (`feat/csv-export`) is a bulk, all-cases export with no sort/date-range/column UI; this is a single-Case, share-sheet-triggered, user-configured export. HODITH_SPEC §13's "notes/tags never included on share cards" rule is specific to the *image* share card — it does not apply to Log Share, since raw notes/tags are the explicit point of a data export shared this way. §13 will need a note distinguishing the two once this ships.

**Acceptance criteria**

- [ ] A ruling on Log Share's output format (CSV/text attachment via Android share sheet is the likely default, consistent with the existing CSV export item's format).
- [ ] A ruling on sort options offered (e.g. date ascending/descending) and date-range picker UX.
- [ ] Column toggles for tags/notes/duration/intensity, each shown only when applicable to the Case (reusing `availableSections`-style gating against `CaseEntity.durationMode`/`intensityEnabled`).
- [ ] Share entry point presents both "Insight Share" and "Log Share" as distinct options (e.g. a chooser before `SharePreviewScreen`, or a new sibling screen).
- [ ] Voice ×3 for all new labels, toggles, and picker copy.
- [ ] HODITH_SPEC §13 updated to scope the "no notes/tags" rule to the image share card specifically, once Log Share exists.

**Plan** — needs the format/sort/date-range/column-UX decisions above settled first (cheap to mock as a static prototype per the project's standing rule for non-trivial UI). Once settled: a new export path parallel to `ShareViewModel`/`SharePreviewScreen` (or a mode within them) producing the tabular file, reusing `availableSections`'s Case-config gating pattern for column applicability, and a new entry-point chooser between Insight Share and Log Share.

**Tests** — a unit test for the export-row-shaping logic (column gating by Case config, sort, date-range filtering); Compose coverage for the two-option share entry point and the Log Share configuration screen.

### Big Picture: filter pill consistency pass (color-coding, empty-selection label, tag/case pill parity)

*Branch: `fix/big-picture-filter-pill-consistency` · Complexity: S–M · Priority: Medium · Area: Big Picture*

🎨 **Design decision** — the actual color choices per filter type need a call.

Three related issues reported together against `ui/bigpicture/BigPictureGrid.kt`'s filter chips/pills:

- **Not color-coded by filter type.** In Plain/Intense, `CaseFilterChip` (lines 865-892) uses `secondaryContainer`, `TagFilterChip` (896-921) uses `tertiaryContainer`, and `YearFilterChip` (930-962, landed with the "Big Picture: year filter" item) uses `primaryContainer` — three distinct colors. The actual gap is **Bright**: `BrightCaseFilterChip` (1009-1022) and `BrightTagFilterChip` (1025-1036) call the shared `BrightChip` (974-1006) with the same `tint = MaterialTheme.colorScheme.primary`, and `YearFilterChip`'s own Bright branch does too — so Bright shows no color distinction across any of the three.
- **"0 of 5" should read "None" when nothing is selected.** The "Cases: N of M" format this note originally described has since shipped as "Cases: N" ("All" once fully selected, via `filterCountLabel`, lines 509-513). `filterCountLabel` still branches only on `selected == total` (→ `bigPictureFilterCountAll`); there's no `selected == 0` branch, so it falls through to the bare `"$selected"` (`Voice.kt`, `bigPictureFilterCount(selected: Int)`, not overridden per-voice) and reads "Cases: 0" instead of a "None" wording. Needs a `bigPictureFilterCountNone`-style key, following the same per-voice-override pattern `bigPictureFilterCountAll` already uses.
- **Tag pills don't match case pills' size/alignment.** `CaseFilterChip` renders a `Row` (icon + name `Text`s, `Arrangement.spacedBy(4.dp)`, `CenterVertically`) with padding on the `Row`; `TagFilterChip` renders a single bare `Text` with the same padding values but no `Row`/explicit vertical-centering container — same `CHIP_SHAPE`/padding constants, different measurement shape, which is the likely source of the visible height/alignment mismatch in the filter `FlowRow`s (lines 419, 438, 452) and `FilterLegendRow` (538-574).

**Acceptance criteria**

- [ ] A ruling on the four chip colors (Cases/Tags/Year trigger chips, plus each dialog's own pills), applied consistently across Plain, Intense, and Bright.
- [ ] `BrightCaseFilterChip`/`BrightTagFilterChip`/`YearFilterChip`'s Bright branch use distinct tints instead of all defaulting to `colorScheme.primary`.
- [ ] `filterCountLabel` gains a `selected == 0` branch returning a new `bigPictureFilterCountNone` Voice key (Voice ×3) instead of falling through to a bare "0".
- [ ] `TagFilterChip` (and Bright's tag chip) restructured to match `CaseFilterChip`'s `Row`-based layout so both measure to the same height/alignment in a `FlowRow`.
- [ ] Verified side-by-side in the Cases/Tags/Year filter dialogs and in `FilterLegendRow` where Case and Tag chips can appear together.

**Plan** — settle the color ruling first (affects Plain/Intense chips, Bright chips, and `YearFilterChip`'s own PLAIN/INTENSE branch, which already uses `primaryContainer` and may need to move once the ruling lands). Then: add the `bigPictureFilterCountNone` Voice key and wire it into `filterCountLabel`; restructure `TagFilterChip`/Bright tag chip onto `CaseFilterChip`'s `Row` layout for size/alignment parity.

**Tests** — `VoiceTest` coverage for the new key across all three voices; a Compose test asserting tag and case chips render at equal height in a shared `FlowRow`; existing Big Picture filter tests updated if any assert the old bare "0" label text.

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

### D4 · No repository-level test coverage for the notification-eval scheduling side effect

*Branch: none yet — needs a reusable test double designed first · Complexity: S–M · Priority: Low · Area: Repo*

Surfaced while adding `RoomHodithRepository.deleteEventsOlderThan` (`feat/bulk-delete-logs-by-date`). That method fetches the affected Case ids *before* deleting (`EventDao.getCaseIdsWithEventsOlderThan`), then deletes, then calls `evaluateNotificationsForCase` for each — a real bug (querying after delete instead of before, silently re-evaluating zero Cases) has no test pinning the ordering. Checking for it turned up a wider, pre-existing gap: **`RoomHodithRepository`'s `evaluateNotificationsForCase` → `NotificationEvalScheduler.schedule()` side effect is untested at the repository level for every call site, not just this new one** — `insertEvent`, `updateEvent`, `deleteEvent`, and `deleteEventById` all fire it too, and none are covered. This isn't a guess: `RoomHodithRepositoryBackupTest.kt`'s own doc comment and an inline comment above its one event insert already document the workaround — it inserts via `db.eventDao().insert(...)` directly instead of `repository.insertEvent(...)` specifically "because that wrapper fires notification evaluation as a fire-and-forget side effect, which would invoke this test's intentionally-throwing `NotificationEvaluator` stand-in" (its `unusedScheduler()` helper's `Provider` deliberately errors if ever pulled).

The scheduler/evaluator chain itself *is* testable — `NotificationEvalSchedulerTest` (JVM, `src/test`) already proves the full `NotificationEvalScheduler` → `NotificationEvaluator` → `Notifier` path works, using `FakeHodithRepository`, `FakeSettingsRepository`, `FakeClock`, and `FakeNotifier`, with `backgroundScope`/`advanceTimeBy` driving the debounce deterministically. What's missing is the androidTest-side equivalent: a way to construct that same chain against a *real* `RoomHodithRepository`/`HodithDatabase` (`RoomHodithRepositoryLogEventsTest`'s and `RoomHodithRepositoryBackupTest`'s pattern) without either triggering `unusedScheduler()`'s deliberate error or routing around the repository's own wrapper methods, as `RoomHodithRepositoryBackupTest` currently does. `FakeNotifier` also isn't reachable from `androidTest` today — it's in `src/test`, a separate source set.

Not a known bug and not blocking: every affected path already has a soft failure mode. A stale trigger/check-in evaluation self-heals within roughly six hours via `NotificationEvalWorker`'s periodic `evaluateAll` sweep, which is unaffected by any of this. Priority Low accordingly — this is a coverage gap, not a correctness risk.

**Acceptance criteria**

- [ ] A reusable androidTest double/helper for the notification-eval side effect — real `NotificationEvalScheduler` + `NotificationEvaluator` wired to the `RoomHodithRepository` under test, with a `FakeNotifier`-equivalent double it can actually read from (moved to a shared source set, or reimplemented for `androidTest`).
- [ ] `RoomHodithRepository.deleteEventsOlderThan`'s affected-Case-id-before-delete ordering pinned by a test using it — the concrete bug that prompted this item.
- [ ] The same coverage extended to `insertEvent`/`updateEvent`/`deleteEvent`/`deleteEventById`'s `evaluateNotificationsForCase` call, currently untested at the repository level.
- [ ] `RoomHodithRepositoryBackupTest.kt`'s raw-DAO insert workaround revisited once the double exists — it could go back to calling `repository.insertEvent(...)` directly instead of bypassing the wrapper, if that reads more naturally with the new double in place.

**Plan** — mirror `NotificationEvalSchedulerTest`'s exact successful shape (real `NotificationEvalScheduler`/`NotificationEvaluator`, `backgroundScope`, `advanceTimeBy`) but swap `FakeHodithRepository` for the real `RoomHodithRepository`/in-memory `HodithDatabase` under test, matching `RoomHodithRepositoryLogEventsTest`'s setup. Settle `FakeNotifier`'s reachability first (shared source set vs. an `androidTest`-local reimplementation) since every other piece already has a working precedent to copy.

**Tests** — this item's entire scope is new tests; see acceptance criteria above.

**Concern** — none blocking. Worth a second look if this class of repository-mutation-triggers-a-side-effect pattern grows (e.g. Trigger CRUD notably does *not* call `evaluateNotificationsForCase` today, unlike Event CRUD — noticed in passing while mapping call sites, not evaluated here as correct or a bug; a separate question if it ever comes up).

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
