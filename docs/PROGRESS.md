# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story N — Notifications rework** — Hunch removed, Triggers reframed as Notifications in a bell tab; two ordered branches.
- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Standalone** — isolated items with no cross-dependencies; pick any when resources are thin.
- **Deferred** — startable, but intentionally held back pending a trigger (usually real alpha usage) rather than gated on something external.
- **Blocked** — gated on something external; not startable now.

Each item carries:

- a **trailer** — *Branch · Complexity · Priority · Area*. Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week (same scale as HODITH_SPEC §17). Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet. Area is a loose bucket — Bug / Big Picture / Insights / Notifications / Share / Settings / Voice / Performance / Repo.
- zero or more **tags** — 🎨 *Design decision* (needs a design or product-owner call before implementation) · 🌐 *External action* (work outside this repo) · 🔍 *Investigation* (needs a repro/diagnose pass before the fix is knowable).
- **Acceptance criteria** — the checklist that says "done".
- **Plan / Tests / Concern** — detail, unchanged from prior tracking.

## Story N — Notifications rework

Alpha feedback showed Hunch isn't landing, and it overlaps with `AT_LEAST` Triggers: two similar "N per period" forms, while Triggers sit behind a Case Detail header icon few people find. The call: **remove Hunch entirely and reframe Triggers as Notifications**, shown in a bell-icon tab where the Hunch tab is now. Each Notification card shows all its settings, a live "Now:" line, and — for the often kind — a tier-gated comparison line against its own threshold, so the "how often does it truly happen" answer survives without a separate entity. Per-Case check-ins move from Case edit into the same tab. Only test installs exist, so the rename goes all the way down to code and the DB, and the shared testing backup JSON is adjusted by hand rather than converted in code.

Two branches, in order — N1 leaves the app coherent (Log + Insights tabs, the existing Triggers screen) and N2 builds on what N1 deliberately keeps. Both touch Voice, so both land before B3.

**Docs sweep** (an acceptance criterion in both items, each covering its own half): grep every `*.md` for `Hunch`/`hunch`/`Trigger`/`trigger`/`Verdict`/`check-in` and resolve each hit on purpose, leaving unrelated uses alone (Big Picture's `FilterTriggerChip`). Covers HODITH_SPEC.md, README.md, TESTING.md, MANUAL_TEST_PLAN.md, DEV_PLAYBOOK.md, CLEANUP_CHECKLIST.md, QA_AUDIT_RULES.md, CLAUDE.md (the "Case/Hunch/Verdict vocabulary" line and the "Verdict, trigger, and stats code stays pure" rule), and this file. CLEANUP_LOG.md's past entries stay as history — each branch adds its own new pass entry after actually walking the checklist.

### N1 · Remove Hunch

*Branch: `chore/remove-hunch` · Complexity: L · Priority: Medium · Area: Notifications*

Delete everything Hunch-only; keep and neutralise the pieces N2 reuses. Afterwards Case Detail has two tabs (Log, Insights) and Triggers still live on their own screen.

**Acceptance criteria**

- [ ] **Deleted (Hunch-only):**
  - Hunch tab wiring in `CaseDetailScreen.kt` — `HunchTabContent`, `HunchCard`, `HunchNoneCard`, `HunchNudgeCard`, `HunchHistoryList`/`HunchHistoryPlank` — and the tab itself.
  - `HunchTabState.kt` and the 5-event nudge.
  - `HunchEntity`, `HunchDao`, `HunchDirection`, and their `HodithDatabase`/`DatabaseModule` wiring.
  - `ObservationWindow` and `HunchCreationSheet.kt`'s `WindowStartDatePickerDialog` (a Notification's lookback is always a rolling day count).
  - Hunch paths in `HodithRepository`/`RoomHodithRepository` (including the backfill `RoomHodithRepositoryHunchBackfillTest` covers), `CaseDetailViewModel`, and `FakeHodithRepository`.
  - The Hunch-derived check-in interval — `hunchCheckInDays` and the `hunch` parameter in `domain/CheckIn.kt`; the effective interval becomes toggle + Settings default only.
  - The share card's Hunch vs. Reality beat (`ShareCardState.kt`'s `HunchVsReality`, `ShareCardTemplate.kt`, `SharePreviewScreen.kt`'s toggle, `ShareViewModel`). Reality becomes the only top beat in both formats; B2 replaces it with a summary.
  - Voice keys used only by the deleted pieces, in all three voices.
- [ ] **Kept for N2** (unused between the two branches, deliberately — don't delete them as dead code):
  - `HunchCreationSheet.kt`'s count, period and metric pickers and helpers (`periodOptionsFor`, `coerceExpectedPer`, `expectedPerLabel`, `LabelledSection`), moved to a neutral shared file (e.g. `ui/common/FrequencyPickers.kt`).
  - `HunchEarlyCard`/`HunchVerdictCard` layouts, turned into neutral tier-badge + headline composables.
  - `VerdictEngine.kt`/`Verdict.kt` window, rate, tier and band math, refactored from a `HunchEntity` input to a pure `Expectation(count, per, metric, windowStart)` value. Constants stay named in `domain/`.
  - `ExpectedPer`, `VerdictMetric`, and the Voice keys for period, metric, tier and comparison-band copy.
- [ ] Room: `hunches` dropped via a new schema version (an `AutoMigration` with a `@DeleteTable` spec, like the existing `DropHunchNudgeDismissedColumn`), exported schema JSON committed.
- [ ] Backup: `BACKUP_SCHEMA_VERSION` 1 → 2 with no `hunches` key; a v1 file is rejected through the existing invalid-file path (`BackupValidationResult`). CSV export unaffected beyond any Hunch column.
- [ ] HODITH_SPEC.md, Hunch half: §1–3 rewritten (idea, vocabulary, the "Hunch is the hero" principle); §5 Hunch model removed; §7 removed (it also still describes a "Got a feeling?" case-creation step that doesn't exist); §8 reframed as the comparison math N2 reuses; §11 check-in timing, §13 share beat, §14 Case detail tabs and §17 updated.
- [ ] Docs sweep (above), Hunch half.

**Plan** — neutralise first (move the kept pickers/cards, refactor `VerdictEngine` to `Expectation`, all green), then delete the Hunch-only pieces, then the Room/backup version bumps, then docs.

**Tests**

- Unit — `VerdictEngineTest` ported to `Expectation` with every existing case kept: window filtering including span overlap, both metrics, rate normalisation per DAY/WEEK/MONTH, every tier boundary, every band cutoff (including cutoff-belongs-to-higher-band).
- Unit — `CheckInTest`: toggle off, Settings default, no Hunch path left.
- Unit — `BackupSerializerTest`/`BackupValidationResultTest`: v2 round-trip, v1 rejected, no `hunches` key; `CsvBackupSerializerTest` updated.
- Unit — `ShareCardStateTest`/`ShareViewModelTest`: no Hunch beat, Reality always present in both formats.
- Unit — `HunchCreationSheetLogicTest` becomes a test of the relocated picker helpers; `HunchTabStateTest` deleted; `CaseDetailViewModelTest`, `SettingsViewModelTest`, `FakeHodithRepositoryTest` lose their Hunch cases.
- Migration — `MigrationTestHelper` (already used by `DatabaseFreshInstallTest`): `hunches` dropped, every other table and its rows intact.
- Instrumented — `CaseDetailScreenTest` shows two tabs; `SharePreviewScreenTest` has no Hunch toggle; `ShareCardTemplateTest` passes; `HunchDaoTest` and `RoomHodithRepositoryHunchBackfillTest` deleted; `CaseDetailInsightsTabTest`, `RoomHodithRepositoryBackupTest`, `BackupImportIntegrationTest`, `CaseDaoTest`, `TestFixtures` lose their Hunch parts.
- `VoiceTest` passes with no orphaned keys.

### N2 · Notifications tab

*Branch: `feat/notifications` · Complexity: L · Priority: Medium · Area: Notifications*

Needs N1. Rename Trigger → Notification everywhere and move it into a bell tab, reusing N1's kept pieces — no new visual design; the cards and editor are today's `TriggerListItem`/`TriggerCreationSheet` extended with the Hunch sheet's pickers and the neutral tier cards.

**Target UX**

- Case Detail tabs: **Log · Insights · 🔔** — icon only, with a Voice content description. The header Triggers action, the Triggers route and `TriggersScreen.kt` go away.
- Bell tab, top to bottom:
  1. **Check-ins row** — the same control Case edit has today: `RowWithInfo` (`ui/common/SectionWithInfo.kt`) with the existing check-in label, info dialog and switch bound to `CaseEntity.checkInsEnabled`. Interval is always the Settings default. Removed from `CaseEditScreen.kt`.
  2. **Notification cards** — title ("3+ times per week" / "Quiet for 14 days"); settings line ("Looking back 30 days · counting times · intensity 3+", measure only on duration Cases, intensity only when set); **Now line** ("Now: 2 per week" / "Now: quiet for 3 days"); for the often kind, a **comparison line** below/about/above the threshold using the existing bands and tier badge, shown only once the lookback clears the existing confidence tiers; last-fired line when set; enable switch (a disabled Notification still shows Now and the comparison); tap to **edit**, with delete (confirm dialog) inside the editor.
  3. Add FAB and empty state, following `TriggersEmptyState`.
- **Editor** (create and edit, prefilled when editing) — today's `TriggerCreationSheet` layout:
  - Kind: `SegmentedChoiceRow` — Happens often / Goes quiet.
  - Often: count (`NumberStepper`); per day/week/month (N1's period picker); looking back 7 / 30 / 90 / custom days, always rolling; measure times / days active (N1's metric picker, duration Cases only); **intensity at least** off / 1–5 (`SegmentedChoiceRow`, only when the Case has `intensityEnabled` — e.g. "3+ times per week with intensity 3+"; events without an intensity don't count while it's set).
  - Quiet: days (`NumberStepper`), as today.

**Acceptance criteria**

- [ ] Rename throughout: `NotificationEntity`, `NotificationDao`, table `notifications`, `NotificationKind { OFTEN, QUIET }`, engine, decision type, ViewModel, tests, Voice keys, spec. `android.app.Notification` imported under an alias where a file needs both; `NotificationEvaluator`/`Notifier` keep their names.
- [ ] New columns `expectedPer`, `metric` (default occurrence count), `minIntensity` (nullable). No hand-written migration: an `AutoMigration` with a `@DeleteTable` spec drops `triggers` and creates `notifications`, so existing test-install triggers are discarded (satisfies `SchemaMigrationCoverageTest`). Test installs are restored from the shared testing backup JSON, adjusted by hand to the new shape on request.
- [ ] Often condition: observed rate over the lookback, normalised to `per`, ≥ count — through N1's `Expectation` math (span-overlap filtering, both metrics) after the intensity filter. Firing is not tier-gated; only the comparison line is. Edge-trigger state machine unchanged. Quiet condition unchanged. `NotificationEvaluator`'s fetch window covers the lookback.
- [ ] Bell tab, cards, editor and check-in row per Target UX; `CaseDetailScreen.kt` gets new tab state + ViewModel wiring.
- [ ] Backup `BACKUP_SCHEMA_VERSION` 2 → 3 carrying the new fields and the `notifications` key; a v2 file is rejected through the existing invalid-file path.
- [ ] Voice ×3: Now, comparison, settings-line and intensity copy; trigger keys renamed. No em dashes, no gamification.
- [ ] **Demo seed** (`data/demo/DemoDataSeeder.kt` seeds none today): an often Notification per week over 30 days with its comparison line showing, a days-active one on a duration Case, an intensity-filtered one on the migraine seed, a quiet one, one disabled, one Case with none (empty state), one Case with check-ins off. `armed`/`lastFiredAt` consistent with the seeded events so loading demo data doesn't fire a burst.
- [ ] HODITH_SPEC.md, Notifications half: §2 vocabulary, §5 model, §11, §14 (Case detail tabs, New/edit Case without check-ins, Triggers row removed), §15 if affected.
- [ ] Docs sweep (above), Notifications half; this story struck from this file in the same commit.

**Plan** — rename + migration first (behaviour-neutral, all green), then the often-condition engine change, then the UI swap, then seed and docs.

**Tests**

- Unit — engine: often condition at/below/above threshold for each `per`; lookback 7/30/90/custom; times vs days active; intensity filter (null excluded, boundary included); span-overlap events; fire / stay quiet / re-arm sequences; quiet condition unchanged (last event end, running event, never-logged Case); `enabled = false` never fires. `TriggerEngineTest` renamed and extended.
- Unit — card state: Now line for each kind, per and metric; comparison hidden below the tier threshold and shown at each band; settings-line composition.
- Unit — ViewModel: create, edit, toggle, delete; check-in toggle writes `checkInsEnabled`. `TriggersViewModelTest` renamed and extended.
- Unit — `NotificationEvaluatorTest`/`NotifierContentTest`: fetch window covers the lookback; copy per kind.
- Unit — backup v3 round-trip with the new fields; a v2 file rejected.
- Unit — `DemoDataSeederTest`: each seeded Notification's kind and fields; intensity only on intensity Cases, days active only on duration Cases; the check-ins-off Case; a repeat load adds another full set; nothing left armed with its condition already met.
- Migration — `MigrationTestHelper`: `triggers` dropped, `notifications` created empty, every other table and its rows intact.
- Instrumented — new bell-tab screen test: check-in row present and toggling; empty state → create; card fields per kind; Now line; comparison gating; edit prefilled → save; delete confirm; measure picker only on duration Cases, intensity picker only with intensity on; disabled card still shows Now; tab icon content description. `TriggersScreenTest` deleted with its coverage carried over; `TriggerDaoTest` renamed; `CaseEditScreenTest` without the check-in toggle; `CaseDetailScreenTest` with the bell tab and no header Triggers action. All through `HodithComposeContent`, across themes and voices.
- `VoiceTest` covers every new key ×3.

## Story B — copy & Voice

Three items, plus the tail of nearly everything else. Anything that adds or changes a Voice key must land before B3.

### B1 · Square share format should become a fixed preset

*Branch: `feat/square-share-card-preset` · Complexity: M · Priority: Medium · Area: Share*

🎨 **Design decision** — which sections, and in what fixed order, Square always shows. Touches Voice copy, so before B3.

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

### B2 · Share card summary beat

*Branch: `feat/share-card-summary-beat` · Complexity: M · Priority: Medium · Area: Share*

🎨 **Design decision** — what the summary says and how it reads in each theme's template. Needs N1 (which removes the Hunch vs. Reality beat); touches Voice copy, so before B3.

N1 leaves the plain Reality beat (event count + days observed) as the card's only top beat. The Hunch beat was the card's punchline — "I checked: it does NOT always rain on my day off" — and a Case can carry several Notifications, so no single Notification's comparison can stand in for it. Replace Reality with a short summary beat that works for both Story and Square.

**Acceptance criteria**

- [ ] A documented summary shape (e.g. overall rate + observed span, with an optional voice-flavoured line), built from data the Insights tab already computes — no new domain math.
- [ ] Rendered in both Story and Square, in all three theme templates, without breaking Square's 1:1 floor or B1's fixed preset.
- [ ] Voice ×3 for any new copy, impersonal (no "I"/"you" — the viewer isn't the user).
- [ ] HODITH_SPEC.md §13 updated.

**Plan** — decide the summary shape with a cheap mockup first, then swap it in for Reality in `shareCardState()` and `ShareCardTemplate.kt`.

**Tests** — `ShareCardStateTest.kt` (summary content for both formats, edge cases: one event, no events), `ShareCardTemplateTest.kt` (fits and no-clip in both formats), `SharePreviewScreenTest.kt` if the preview gains or loses a control.

### B3 · Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and needs a human ear. **Must land last**, after every other copy-touching item (currently B1, B2, and Story N).

Fold these already-drafted key changes into the audit:

- `feat/declutter-nudges` — reworded Serious `checkInDueNotificationBody`; renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle`.
- `feat/insights-from-first-event` — added `insightsNothingLoggedMessage`, `insightsSingleEventNote` (replacing `insightsNotEnoughDataMessage`).
- `feat/big-picture-overview-detail` — retired `bigPictureEventNoteEmptyState`; added `bigPictureDetailDialogTitle`, `bigPictureDetailEditDescription`, four shared field labels.
- `feat/resolved-hunch-list-redesign` — retired `hunchHistoryRowText`; added `hunchHistoryShowMoreAction`, `hunchHistoryRetentionNote` (both retired again by N1).

**Acceptance criteria**

- [ ] A written rubric: per-voice person, tense, sentence length, punctuation/emoji budget, locked Case/Event/Notification/Check-in vocabulary, and an em-dash policy with per-string calls for Goth/Quirky mid-sentence pivots.
- [ ] A findings list produced first; fixes in a separate second commit.
- [ ] Audit done in slices by screen, not by reading `Voice.kt` linearly.
- [ ] New mechanical `VoiceTest` invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab/button labels, no double spaces or trailing whitespace.
- [ ] Confirmed before starting: `androidTest` references `PlainVoice` by constant, not literal, everywhere (grep for hardcoded UI literals).

**Plan** — 720 strings total: 213 keys declared per-voice (639 strings) need independent authorship; 81 shared `get()`/default-body keys are reviewed once. Write the rubric first (person, tense, sentence length, punctuation/emoji budget, locked Case/Event/Notification/Check-in vocabulary), then audit in slices by screen — not top to bottom, since `Voice.kt` is grouped by key. Produce a findings list first; fix in a second commit. ~105 em dashes exist today (18 Serious, 36 Goth, 51 Quirky); most convert to a period or comma, but Goth/Quirky use them ~2–3x more often as a genuine mid-sentence pivot, so each needs a per-string call rather than a mechanical substitution.

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

Raises the same question **D1** is deferred pending — app capacity for years of records — but broader than D1's Big Picture-specific scope. `EventDao.observeEventsWithTagsForCase` (unbounded) backs every Insights stats computation (and, after N2, every Notification's Now line) (rhythm, frequency-over-time, trend, duration averages) with no row-count limit; only the Log tab got paged querying (`feat/log-tab-paged-query`). May itself be D1's "real alpha usage" trigger — resolve together with D1 rather than as a separate track.

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

### D5 · Notification threshold suggestions

*Branch: none yet — deferred, needs N2 · Complexity: M · Priority: Low · Area: Notifications*

🎨 **Design decision**

Carried over from the retired Hunch/Trigger feasibility item, re-scoped to Notifications. When creating or editing a quiet Notification, suggest a threshold from the Case's 90th-percentile historical gap — `InsightsEngine.computeGapStats` builds the gap list; this adds a percentile helper over it (none exists today). For either kind, show "would have fired N times in the last year" by replaying the (pure, `now`-parameterised) Notification conditions over the past year's events — a historical loop, no new evaluation logic.

**Deferred rather than pursued next** — the editor's shape settles in N2 first, and real use of the bell tab should show whether people struggle to pick thresholds at all.

**Acceptance criteria**

- [ ] Revisit once N2 has shipped and alpha testers have used it.
- [ ] A percentile helper over the gap list, with its own unit tests.
- [ ] The replay loop reuses the Notification conditions as-is and is unit-tested against a planted event history.
- [ ] Voice ×3 for the suggestion copy, observational only (no "you should").

**Plan** — none yet.

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
