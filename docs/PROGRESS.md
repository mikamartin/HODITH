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

Story stays the one fully customizable, auto-sizing format. `shareCardState()` applies `selectedSections` identically to both formats, and `SharePreviewScreen.kt`'s `SectionsPicker`/`availableSections` render the same toggles for both — but Square keeps a 1:1 floor while Story sizes freely to content, so selecting every Insights section on Square produces a tall rectangle instead of the predictable square shape it's for.

**Acceptance criteria**

- [ ] A documented fixed section list + order for Square.
- [ ] `SectionsPicker` renders only when `ShareCardFormat.STORY` is selected.
- [ ] `shareCardState()` sources Square's sections from the fixed preset, independent of `selectedSections`.
- [ ] Story keeps full customization and content-sizing.
- [ ] Any Story-only picker copy goes through Voice ×3.
- [ ] Tests: `ShareCardStateTest.kt` (Square driven by preset), `SharePreviewScreenTest.kt` (picker only for Story); `ShareCardTemplateTest.kt` Square floor/no-clip still passes.
- [ ] `docs/mockups/share-cards-prototype.html` deleted and its `ShareCardDecoration.kt` KDoc pointer dropped — it was the last mockup left in that directory, kept only as this item's Story/Square section-layout reference (`chore/prune-design-mockups` removed the other five).

**Plan** — decide Square's fixed section list first. Then gate `SectionsPicker` to `ShareCardFormat.STORY` only, and source Square's sections from the preset in `shareCardState()`.

**Tests** — `ShareCardStateTest.kt` and `SharePreviewScreenTest.kt` cover the preset-driven output and Story-only picker; `ShareCardTemplateTest.kt`'s floor/no-clip tests keep passing unchanged.

### B2 · Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and needs a human ear. **Must land last**, after every other copy-touching item (currently just B1).

Fold these already-drafted key changes into the audit:

- `feat/declutter-nudges` — reworded Serious `checkInDueNotificationBody`; renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle`.
- `feat/insights-from-first-event` — added `insightsNothingLoggedMessage`, `insightsSingleEventNote` (replacing `insightsNotEnoughDataMessage`).
- `feat/big-picture-overview-detail` — retired `bigPictureEventNoteEmptyState`; added `bigPictureDetailDialogTitle`, `bigPictureDetailEditDescription`, four shared field labels.
- `feat/resolved-hunch-list-redesign` — retired `hunchHistoryRowText`; added `hunchHistoryShowMoreAction`, `hunchHistoryRetentionNote`.

**Acceptance criteria**

- [ ] A written rubric: per-voice person, tense, sentence length, punctuation/emoji budget, locked Case/Hunch/Verdict/Event/Trigger vocabulary, and an em-dash policy with per-string calls for Goth/Quirky mid-sentence pivots.
- [ ] A findings list produced first; fixes in a separate second commit.
- [ ] Audit done in slices by screen, not by reading `Voice.kt` linearly.
- [ ] New mechanical `VoiceTest` invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab/button labels, no double spaces or trailing whitespace.
- [ ] Confirmed before starting: `androidTest` references `PlainVoice` by constant, not literal, everywhere (grep for hardcoded UI literals).

**Plan** — 720 strings total: 213 keys declared per-voice (639 strings) need independent authorship; 81 shared `get()`/default-body keys are reviewed once. Write the rubric first (person, tense, sentence length, punctuation/emoji budget, locked Case/Hunch/Verdict/Event/Trigger vocabulary), then audit in slices by screen — not top to bottom, since `Voice.kt` is grouped by key. Produce a findings list first; fix in a second commit. ~105 em dashes exist today (18 Serious, 36 Goth, 51 Quirky); most convert to a period or comma, but Goth/Quirky use them ~2–3x more often as a genuine mid-sentence pivot, so each needs a per-string call rather than a mechanical substitution.

**Tests** — `VoiceTest` already checks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. Add mechanical invariants during the audit: vocabulary casing, no gamification vocabulary (spec §4), length caps on tab/button labels, no double spaces or trailing whitespace. Confirm `androidTest` references `PlainVoice` by constant everywhere, not literal, before starting.

**Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

## Standalone

No cross-dependencies — pick by appetite. Grouped by area below; items are identified by title or branch, not a number.

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

### Hunch/Trigger relationship: feasibility & rework

*Branch: `chore/hunch-trigger-feasibility` · Complexity: L · Priority: Low · Area: Hunch*

🎨 **Design decision** — whether Hunch and Trigger stay two entities, merge, or become something new is a product-positioning call, not just an implementation detail. 🔍 **Investigation** — nothing below is buildable until the feasibility question is answered.

`AT_LEAST` triggers ("N+ times in a rolling window") and Hunches ("~N times per period", verdict computed over the whole observation window) currently overlap: a user with an active Hunch may re-enter nearly the same numbers to also get notified. They're not actually the same thing (rolling-window burst detection vs. whole-history average), so a naive prefill would misrepresent what the alert means. Current usage doesn't demonstrate that keeping them as two separate, similarly-shaped entities is the right call — the first step here is feasibility: could Hunch and Trigger be one entity, or does the overlap resolve some other way? That answer may require reworking HODITH_SPEC.md's Hunch/Trigger sections and how the app positions the two concepts, not just picking one of the options below.

Options considered for the narrower overlap question, still relevant regardless of how the feasibility question resolves:

1. A genuinely new hunch-verdict-based alert kind, evaluated via the verdict engine rather than `TriggerEngine`.
2. Prefill `AT_LEAST`'s fields from the active Hunch as a labelled approximation.
3. Leave both engines as-is and just surface trigger creation contextually from the Hunch tab instead of a separate entry point.

Folded-in ideas — each parked until the feasibility question above is settled, since building any of them now risks doubling down on a shape that gets reworked:

- **Time-to-confidence projection** — "At the current rate, CONFIDENT in about 9 days," projected off `confidenceTierFor(observationCount: Int, windowDays: Long)` (`VerdictEngine.kt:132-140`)'s existing `PRELIMINARY_MIN_EVENTS`/`CONFIDENT_MIN_EVENTS` and `*_MIN_DAYS` constants: given the Case's current event rate, solve for the day both thresholds clear.
- **Belief drift across superseded Hunches** — when a Case has more than one Hunch over time on the same question (e.g. coffee: 3/day, then 2/day), say so: "Your expectation dropped, and the data agrees." No new query needed — `HunchDao.observeHunchHistory(caseId)` (`HunchDao.kt:27-28`) already returns every Hunch for a Case ordered `createdAt DESC`, and each resolved one already carries a frozen verdict snapshot (`HunchEntity`'s `resolved*` columns, `Verdict.kt`'s `withResolvedVerdictSnapshot`). The resolved-Hunch list (`feat/resolved-hunch-list-redesign` — `CaseDetailScreen.kt`/`HunchTabState.kt`, 15-item retention cap via `HunchDao.deleteResolvedHunchesBeyondLimit`) is the natural surface for a belief-drift sentence between consecutive entries.
- **Perception-gap framing for `JUST_CURIOUS`** — frame the result as how it felt vs. what the data shows, rather than a verdict against an expectation.
- **Trigger threshold suggestions** — suggest a `SILENT_FOR` threshold from the Case's 90th-percentile historical gap. `InsightsEngine.computeGapStats` (`InsightsEngine.kt:63-99`) builds the gap list; this would add a percentile helper over it (none exists today). When editing a trigger, show "would have fired N times in the last year" by replaying `evaluateAtLeast`/`evaluateSilentFor` (`TriggerEngine.kt:36-55`, both pure functions of `now`) over the past year's events — a historical loop, no new evaluation logic.

**Acceptance criteria**

- [ ] A written feasibility call: keep Hunch and Trigger as two entities, merge them into one, or introduce a new entity — with rationale grounded in actual usage (alpha data) rather than the abstract overlap alone.
- [ ] `HODITH_SPEC.md`'s Hunch (§7), Trigger (§11 Triggers subsection), and Vocabulary (§2) sections flagged for rework once the feasibility call is made, scoped to whichever entity shape wins.
- [ ] If the two-entity shape survives: a decision among the three overlap options above, implemented.
- [ ] Folded-in ideas (time-to-confidence projection, belief drift, perception-gap framing, trigger threshold suggestions) revisited only after the feasibility call, each re-scoped to whatever the winning shape turns out to be.
- [ ] Voice ×3 for any new copy that results.

**Plan** — parked until alpha testing shows how people actually use Hunches and Triggers, per the original spec note. When picked up: feasibility investigation first (no production code), then the overlap-option decision, then the folded-in ideas re-scoped to match.

**Tests** — none until the feasibility call is made; each folded-in idea keeps its own test shape (noted above) once re-scoped and picked up.

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

Exploratory pass over the Intense and Bright themes (`Color.kt`, `GlowDecoration.kt`, `CardDecorationStyle.kt`, `BigPictureDecoration.kt`, `ShareCardDecoration.kt`) for minor redesigns. Restyle-only — visual refinement of what exists, not new features a mockup might suggest.

**Acceptance criteria**

- [ ] A written pass over both themes across the main screens (Home, Case Detail/Insights, Big Picture, Share, Settings) noting legibility/contrast/consistency issues.
- [ ] A shortlist of proposed tweaks, restyle-only, each with a keep/drop call.
- [ ] Approved tweaks spun out as their own follow-up items.

**Plan** — audit pass first, no code; produce a findings list. Implementation only for approved items, spun out separately.

**Tests** — none for the audit itself.

### UI test suite never renders real theme colors, and covers only Plain's copy

*Branch: `chore/ui-test-voice-theme-coverage` · Complexity: M–L · Priority: Medium · Area: Repo*

🔍 **Investigation** — the fix shape (full distribution vs. a smoke pass) depends on a survey not yet done.

An emulator run surfaced a color scheme that matched none of the three voices, exposing two separate gaps, not one:

- **Theme (color scheme)**: no `androidTest` file applies `HodithTheme` (or even a bare `MaterialTheme`) at all. Every screen test renders with Compose's generic Material3 default colors — not Plain's real palette either, let alone Intense's or Bright's.
- **Voice (copy)**: almost every `*ScreenTest.kt` hardcodes `CompositionLocalProvider(LocalVoice provides PlainVoice)`. Intense/Bright copy is exercised only at the unit level (`VoiceTest`'s reflection checks that all three exist and differ), never inside a rendered UI test.

`LocalCardDecorationStyle` (Plain/Intense/Bright's chip-chrome equivalent) also defaults to `PLAIN` almost everywhere. `BigPictureScreenTest.kt` is the one exception — its own comment already flags that without its two `decorationStyle = CardDecorationStyle.BRIGHT` tests, `FilterTriggerChip`/`CaseFilterChip`/`CaseGroupChip`'s entire BRIGHT branch (`BrightChip`) would go untested. Every other screen using those same shared chip components (e.g. `CaseDetailScreen`'s Sort/Range chips) gets no Bright coverage at all (Intense shares Plain's code path in that `when`, so it's covered incidentally there). `CenteredEmptyStateTest.kt` goes one step further and reuses `IntenseVoice`'s string content for a layout edge case, but that's a text-length probe, not theme or decoration-style coverage.

Not every gap here is equally risky — a composable that only reads `MaterialTheme.colorScheme`/`typography` (like `DateRangeFilterDialog`) renders one structural tree regardless of voice or theme, so missing color coverage there is cosmetic, not a correctness risk. The real risk is composables that structurally branch on `LocalCardDecorationStyle`/`LocalBigPictureCellStyle` (`BrightChip` and anything else `when`-ing on decoration style) — those branches can silently break (wrong click target, broken semantics, a crash) with nothing to catch it.

**Acceptance criteria**

- [ ] A survey of every composable that branches structurally on `CardDecorationStyle`/`BigPictureCellStyle` (not just per-voice string content, which `VoiceTest` already covers at the unit level) — this is the list that actually needs non-Plain rendering coverage.
- [ ] Ideally: existing tests across test classes redistributed so some run under Plain, some Intense, some Bright (voice, decoration style, and real `HodithTheme` colors together), rather than concentrating all non-Plain coverage in one file.
- [ ] If full redistribution is impractical for a given screen: at minimum, one happy-path smoke test each for Intense and Bright (open the screen, exercise its primary action, real `HodithTheme` colors applied) — following `BigPictureScreenTest`'s `cellStyle`/`decorationStyle` parameter pattern as the model — while the rest of that screen's tests stay on Plain copy as today.
- [ ] Any test found asserting on a hardcoded copy literal instead of `PlainVoice.xxx` (or whichever voice it's under) fixed while in the area — echoes B2's own `androidTest`-hygiene criterion.

**Plan** — survey first (grep for `LocalCardDecorationStyle`/`LocalBigPictureCellStyle` usage under `ui/` to enumerate every structurally-branching composable and which test classes exercise it today). Then decide per screen: redistribute voice/style/theme across existing tests where cheap, or add a dedicated Intense/Bright smoke pass (wrapped in real `HodithTheme`) where full parameterization isn't worth the churn.

**Tests** — this item's entire scope is test changes; no production code.

**Concern** — varying `LocalVoice`/`LocalCardDecorationStyle`/real theme colors in existing tests will surface any test currently passing only because it happens to match Plain's specific copy or Compose's default colors — expect some collateral fixes, not just new coverage.

## Deferred

### D1 · Big Picture's grid query, windowed or not

*Branch: `refactor/big-picture-windowed-query` (if taken) · Complexity: S–M · Priority: Low · Area: Performance*

🔍 **Investigation, deferred** — `BigPictureViewModel` now reads two flat projections (`EventDao.observeActiveCaseEventDetails()`, `TagDao.observeActiveCaseEventTagNames()`) instead of the original `@Transaction @Relation` cascade — removing the chunked `IN (...)` sub-fetches and full-row hydration that were the measured cost. A JVM probe confirmed Kotlin-side mapping is cheap at S6 scale. Undecided: whether the two queries' SQL-scan cost holds up at that scale under a write burst.

**Deferred rather than pursued next** — closing this needs a synthetic S6-scale DB probe with no real usage behind it: speculative complexity. Real alpha usage is a better trigger than a cautionary probe.

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

Raises the same question **D1** is deferred pending — app capacity for years of records — but broader than D1's Big Picture-specific scope. `EventDao.observeEventsWithTagsForCase` (unbounded) backs every Insights/Hunch stats computation (rhythm, frequency-over-time, trend, duration averages) with no row-count limit; only the Log tab got paged querying (`feat/log-tab-paged-query`). May itself be D1's "real alpha usage" trigger — resolve together with D1 rather than as a separate track.

**Acceptance criteria**

- [ ] A synthetic or real multi-year dataset used to measure current behavior of the unbounded per-case stats query (load time, memory) at a defined scale (e.g. matching D1's S6 reference point).
- [ ] A stated current capacity (rows/years before a defined threshold degrades).
- [ ] A ruling on whether this satisfies D1's alpha-usage gate, supersedes it, or should stay a separate track.
- [ ] If a guardrail is warranted: a shortlist of options (windowed stats queries, a soft in-app warning at N events, etc.) with a keep/drop call each, spun out as their own item(s).

**Plan** — probe first, no production code in this item; read alongside D1 before deciding investigation scope, to avoid running two parallel capacity investigations.

**Tests** — none until a follow-up item lands.

### D4 · Detector: cycles and seasonality (autocorrelation + month-of-year)

*Branch: none yet — deferred, scope narrowed · Complexity: L · Priority: Low · Area: Insights*

🔍 **Investigation, deferred** · 🎨 **Design decision**

Originally scoped three sub-features: autocorrelation for weekly/~28-day cycles, a month-of-year comparison (needs 1+ years of data), and a weekday-vs-weekend fallback. The fallback shipped on its own (`HODITH_SPEC.md` §10). The other two are deferred: autocorrelation is a genuinely new technique (not a reuse of the existing bucket-share/label-shuffle/timeline-shuffle machinery) and needs the most data of any detector here to fire reliably.

**Deferred rather than pursued next** — building this before knowing whether Cases run long enough to show real weekly/monthly structure is speculative complexity. Real alpha usage (Cases with a year-plus of history) is a better trigger.

**Acceptance criteria**

- [ ] Revisit once alpha usage shows Cases commonly reach 1+ years of history (or a deliberate decision to build it sooner).
- [ ] Autocorrelation method + lag set chosen and documented (weekly ~7-day, ~28-day, and any others).
- [ ] Weekly/~28-day cycle detection gated by a minimum span; month-of-year comparison gated on ≥1 year of data.
- [ ] Voice ×3 for the new sentence template(s).
- [ ] Tests: a planted weekly cycle, a planted no-cycle null.
- [ ] `HODITH_SPEC.md` §10 gains one line per kept signal, or a rationale note here for any dropped.

**Plan** — none yet — the weekday-vs-weekend fallback already covers the cheapest, most useful signal of the original three.

**Tests** — none until picked back up.

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

**Plan** — blocked on the listing existing; belongs in the release-prep branch. Two options: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review adds a Google Play Services dependency to an app that ships none and positions itself as "no network".

**Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar — change it to assert the intent launches (Espresso `Intents`). The row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`; no change needed, don't mistake it for a second call site.

**Concern** — In-App Review is quota-limited and no-ops silently once hit, making manual verification unreliable. The deep link is trivially verifiable.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
