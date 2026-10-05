# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by status:

- **Standalone** — no cross-dependencies; pick any when resources are thin.
- **Deferred** — startable, but intentionally held back pending a trigger (usually real alpha usage) rather than gated on something external.
- **Blocked** — gated on something external; not startable now.

Each item carries a **trailer** (*Branch · Complexity · Priority · Area*; Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week, same scale as HODITH_SPEC §17; Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet; Area is a loose bucket), zero or more **tags** (🎨 *Design decision* needs a human call before implementation · 🌐 *External action* work outside this repo · 🔍 *Investigation* needs a repro/diagnose pass before the fix is knowable), **Acceptance criteria**, and **Plan / Tests / Concern** detail.

## Standalone

No cross-dependencies — pick by appetite. Grouped by area below; items are identified by title or branch, not a number.

### Tags: bulk rename/merge/delete across all events

*Branch: none yet — investigation first · Complexity: L · Priority: Medium · Area: Settings*

🎨 **Design decision** · 🔍 **Investigation**

No tag-management UI, rename, or merge operation exists anywhere today. Tags are global (`TagEntity`, unique index on `name`) attached via a composite-PK join table (`EventTagCrossRef`), not per-Case — "rename everywhere" is global by construction. Testers hit this directly: near-duplicate tag spellings (two options for basically the same thing) with no way to consolidate or fix wording after the fact. Related but distinct from the retired "new-case tag suggestions have no history" item: that one was about *preventing* near-duplicate spellings via better suggestions; this is about *fixing* them after the fact.

**Acceptance criteria**

- [ ] An edge-case brainstorm completed and written down before any implementation: case-insensitive collisions (the DB unique index is case-sensitive, lookups are case-insensitive), merging two tags' `event_tags` composite-PK rows when an event already has both, orphaned-tag cleanup (none exists today), undo/confirmation needs.
- [ ] A tag-management screen designed (none exists today) with warnings and confirmations appropriate to the data-integrity stakes.
- [ ] Rename-a-tag-everywhere and merge-two-tags-into-one both implemented, with the brainstormed edge cases covered.
- [ ] Thorough test coverage given the data-integrity risk: collision handling, cross-ref de-duplication on merge, cascade behaviour.

**Plan** — brainstorm first, no code; then design the management screen and confirmation flow; implement rename/merge against `TagDao`/`EventTagCrossRef` last.

**Tests** — none until the brainstorm and design are done.

### Case Detail: split the 250-line CaseDetailScreen composable

*Branch: none yet · Complexity: M · Priority: Low · Area: Refactor*

`CaseDetailScreen` is about 250 lines and owns the header, the tab row, the FAB, the three tab bodies and the new-event sheet state. It was already this long before the Share change; that change only removed the chooser from it.

**Acceptance criteria**

- [ ] Each tab body (Log, Insights, Watches) lives in its own composable, with the tab-selection state kept in the screen.
- [ ] The FAB and the new-event sheet live beside the screen, not inside the tab bodies.
- [ ] No change in behaviour: `CaseDetailScreenTest` and `CaseDetailInsightsTabTest` pass unchanged.

**Plan** — extract one tab at a time, running those two classes after each.

**Tests** — none new; the existing Case Detail classes are the regression net.

### Insights tab: split Gaps/Streaks, restyle Gaps & Duration to match Share Story's pattern

*Branch: `feat/insights-gaps-streaks-split` · Complexity: M · Priority: Medium · Area: Insights*

`GapsCard` combines gaps and streaks under one `SectionWithInfo`; `DurationCard` uses the same vertical `StatRow` stack. The Share Story card already uses a different, more visual pattern for the same stats — separate mini-cards with a three-column min/avg/max row (`MinAvgMaxRow`).

**Acceptance criteria**

- [ ] Gaps and Streaks split into two cards, each with its own `SectionWithInfo` info icon and copy.
- [ ] Gaps and Duration's stat layout restyled to the min/avg/max row idiom `GapsPanel`/`DurationPanel` already use in Share.
- [ ] Visual consistency confirmed between the Insights tab and the Share preview for these stats.

**Plan** — split `GapsCard` into `GapsCard`/`StreaksCard`; port the `MinAvgMaxRow` idiom from `ShareCardTemplate.kt` into the Insights tab's Gaps and Duration cards.

**Tests** — `CaseDetailInsightsTabTest` updated for the split cards and new row layout.

### Trends: visual redesign, order by recency/significance

*Branch: `feat/trends-visual-redesign` · Complexity: M · Priority: Medium · Area: Insights*

🎨 **Design decision**

Trend findings render as plain sentence + caption text, not cards, and their order is fixed by detector-execution order, not recency or significance.

**Acceptance criteria**

- [ ] `TrendFindingRow`/`Plank` restyled to a more scannable, visual per-finding treatment.
- [ ] Findings reordered by significance — a decision made on whether the existing binary `TrendReliability` tier (Pattern before Hint) is sufficient, or whether raw p-values need to be persisted on `TrendFinding` for finer-grained ordering.
- [ ] "New" label — open sub-question, not blocking the rest of this item: needs a finding-identity/persistence design decision (`TrendFinding` has no id or timestamp today) before it's buildable. Ship the visual redesign and significance ordering first, add the badge once that's decided.

**Plan** — restyle rendering first; add significance-based sort to `TrendsEngine`'s output; revisit "New" once identity/persistence is designed.

**Tests** — `InsightsTabTrendsCardTest`/`TrendsListScreenTest` updated for the new rendering and sort order.

### Delete Data: clearer option copy

*Branch: `fix/delete-data-copy` · Complexity: XS · Priority: Medium · Area: Settings / Voice*

`DeleteDataOptionsDialog` uses `settingsDeleteDataOptionAll`/`settingsDeleteDataOptionLogsOnly` (default copy "All data"/"Logs only", plus two theme overrides). "Logs only" currently wraps to 2 lines.

**Acceptance criteria**

- [ ] `settingsDeleteDataOptionAll` reworded to literally list what's deleted, across all three Voices.
- [ ] The 2-line wrap resolved by the new, shorter-or-equal copy.
- [ ] Related confirm-step strings (`settingsDeleteAllDataConfirmTitle`, `settingsDeleteDataLogsConfirmTitle`) updated to match, across all three Voices.

**Plan** — reword the two option strings and the matching confirm-step strings in `Voice.kt` (default + 2 theme overrides each); verify the dialog no longer wraps.

**Tests** — `SettingsScreenTest`/`DeleteDataDialogsTest` assertions updated for the new copy; `VoiceTest` picks up renamed/reworded keys automatically.

### Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and needs a human ear. **Must land last**, after every other copy-touching item. Anything that adds or changes a Voice key must land before this item — nearly everything else in this file does.

Fold these already-drafted key changes into the audit:

- `feat/declutter-nudges` — reworded Serious `checkInDueNotificationBody`; renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle`.
- `feat/insights-from-first-event` — added `insightsNothingLoggedMessage`, `insightsSingleEventNote` (replacing `insightsNotEnoughDataMessage`).
- `feat/square-share-card-preset` — added the Square share keys (`shareSquare*`, `shareStat*Label`, `shareRate*`), the structural `shareInsightScreenTitle`, and renamed `shareCardFooter`'s parameter to `timestamp` (the footer now carries date and time).
- `feat/share-card-summary-beat` — retired `shareRealityEventsLabel` and `shareRealityDaysObservedLabel`; renamed `shareSquareGapsTitle` and `shareSquareDurationTitle` to `shareGapsTitle` and `shareDurationTitle`; added the structural `shareTopTagsTitle`.
- `feat/big-picture-overview-detail` — retired `bigPictureEventNoteEmptyState`; added `bigPictureDetailDialogTitle`, `bigPictureDetailEditDescription`, four shared field labels.

**Acceptance criteria**

- [ ] A written rubric: per-voice person, tense, sentence length, punctuation/emoji budget, locked Case/Event/Watch/Check-in vocabulary, and an em-dash policy with per-string calls for Goth/Quirky mid-sentence pivots.
- [ ] A findings list produced first; fixes in a separate second commit.
- [ ] Audit done in slices by screen, not by reading `Voice.kt` linearly.
- [ ] New mechanical `VoiceTest` invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab/button labels, no double spaces or trailing whitespace.
- [ ] Confirmed before starting: `androidTest` references `PlainVoice` by constant, not literal, everywhere (grep for hardcoded UI literals).

**Plan** — write the rubric first (person, tense, sentence length, punctuation/emoji budget, locked Case/Event/Watch/Check-in vocabulary), then audit in slices by screen — not top to bottom, since `Voice.kt` is grouped by key. Produce a findings list first; fix in a second commit. Re-tally the per-voice/shared key split and the em-dash count before starting — `chore/remove-hunch` retired dozens of keys and touched dashes in the ones it rewrote, so the prior counts no longer hold.

**Tests** — `VoiceTest` already checks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. Add mechanical invariants during the audit: vocabulary casing, no gamification vocabulary (spec §4), length caps on tab/button labels, no double spaces or trailing whitespace. Confirm `androidTest` references `PlainVoice` by constant everywhere, not literal, before starting.

**Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

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


**Tests** — none existing cover this row's own layout/sizing directly; Preview-verify the four call sites after the change.

### UI test suite audit: duration and duplicate coverage

*Branch: `chore/ui-test-suite-audit` · Complexity: S (investigation) · Priority: Medium · Area: Repo*

🔍 **Investigation** — a review pass, not a known fix, same shape as the Intense/Bright theme audit above.

`androidTest` has 48 files, ~11,000 lines, ~500 `@Test`s; only 10 files have genuine Hilt/instrumentation dependencies (widget/notification/backup). Starting leads already found: `CaseDetailInsightsTabTest` re-verifies number-formatting/rounding (intensity decimal truncation, exact gap/streak counts) through full Compose rendering, duplicating logic already unit-tested at the JVM level in `InsightsFormattingTest`; `ShareCardTemplateTest` and `InsightsTabTrendsCardTest` both independently exercise Trends-row rendering.

**Acceptance criteria**

- [ ] A written pass over the suite noting duration hot spots, duplicated coverage, and tests asserting pure-logic results through the UI instead of unit-testing the logic directly.
- [ ] A shortlist of tests to convert to unit tests, de-duplicate, or delete, each with a keep/drop call.
- [ ] `docs/MANUAL_TEST_PLAN.md` re-checked against the automated suite: any step that an existing or easily added automated test already covers is either removed from the manual plan or noted as redundant, and any check that can be automated is moved into `androidTest`/`test` with a rationale.
- [ ] Approved changes spun out as their own follow-up item(s).

**Plan** — audit pass first, no code changes; produce a findings list.

**Tests** — none for the audit itself.

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

### D3 · Investigate app capacity at multi-year logging scale

*Branch: none yet — investigation first · Complexity: S (investigation) · Priority: Medium · Area: Performance*

🔍 **Investigation**

Raises the same question **D1** is deferred pending — app capacity for years of records — but broader than D1's Big Picture-specific scope. `EventDao.observeEventsWithTagsForCase` (unbounded) backs every Insights stats computation (and every Watch's Now line) (rhythm, frequency-over-time, trend, duration averages) with no row-count limit; only the Log tab got paged querying (`feat/log-tab-paged-query`). May itself be D1's "real alpha usage" trigger — resolve together with D1 rather than as a separate track.

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
