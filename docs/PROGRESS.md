# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by status alone:

- **Story U — Duration unification** — collapsing Case-level `DurationMode` into a per-Case on/off switch plus a per-event ongoing/end-time/duration control (U1 prototype, U2 schema/engine/consumers, remaining).
- **Standalone** — no cross-dependencies; pick any when resources are thin.
- **Deferred** — startable, but intentionally held back pending a trigger (usually real alpha usage) rather than gated on something external.
- **Blocked** — gated on something external; not startable now.

Each item carries a **trailer** (*Branch · Complexity · Priority · Area*; Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week, same scale as HODITH_SPEC §17; Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet; Area is a loose bucket), zero or more **tags** (🎨 *Design decision* needs a human call before implementation · 🌐 *External action* work outside this repo · 🔍 *Investigation* needs a repro/diagnose pass before the fix is knowable), **Acceptance criteria**, and **Plan / Tests / Concern** detail.

## Story U — Duration unification

Collapsing the Case-level `DurationMode` (`NONE`/`MANUAL`/`START_STOP`) into a single per-Case `tracksDuration: Boolean` plus a per-*event* ongoing/end-time/duration state removes the parallel branching duplicated across `domain/ActiveSpan.kt`, `BigPictureViewModel`/`BigPictureGrid.kt`, `OngoingIndicator.kt`, `CaseDetailScreen.kt` (History), `InsightsTab.kt`, `HomeViewModel.kt`, `ShareCardState.kt`, `EventTimeFormat.kt`, and the `WatchEngine`/`StatsEngine`/`VerdictEngine`/`NotificationEvaluator`/`Ticker` consumers of `isStillRunning`/`activeSpanEnd`. Pre-release, so no careful migration is needed — `demo_backup.json` and test fixtures get hand-edited to the new shape rather than migrated.

### U1 · Prototype the merged Edit Event / Case Edit UX

*Branch: `feature/duration-unification` · Complexity: S · Priority: Medium · Area: Case/Event*

Throwaway Compose Preview mockups (`ui/prototype/DurationUnificationPrototype.kt`) — no production code touched. Validated the control shape through several review rounds; the settled design is captured in **U2 → Notes from U1** below, not repeated here. Pending: user sign-off on moving to U2 (or another prototype pass). The prototype file is deleted once U2's real implementation supersedes it.

### U2 · Implement the unified duration model

*Branch: `feature/duration-unification` (continued) · Complexity: L–XL · Priority: Medium · Area: Case/Event*

🎨 **Design decision** — needs U1 signed off first.

**Acceptance criteria**

- [ ] `DurationMode` replaced by `CaseEntity.tracksDuration: Boolean`; `EventEntity` needs no new columns beyond today's `occurredAt`/`endedAt` — ongoing is *derived* (`endedAt == null || endedAt > now`), not a stored flag; duration stays derived too.
- [ ] `domain/ActiveSpan.kt`'s `isStillRunning`/`activeSpanEnd` rewritten for the two-state (tracks-duration or not) model, staying pure Kotlin with injected `Clock`/`now`, no `android.*` imports.
- [ ] Every duplicated `isOngoing`/`expectedEndAt` branch found during exploration updated to the unified model: `BigPictureViewModel`/`BigPictureGrid.kt`, `OngoingIndicator.kt`, `CaseDetailScreen.kt`, `InsightsTab.kt`, `HomeViewModel.kt`, `ShareCardState.kt`, `EventTimeFormat.kt`, both Glance widgets, `WatchEngine`/`StatsEngine`/`VerdictEngine`/`NotificationEvaluator`/`Ticker`.
- [ ] **Confirm-dialog / warning UX redesigned** for toggling `tracksDuration` on an existing Case with events (replacing today's leave-/enter-`START_STOP` dialogs in `CaseEditScreen.kt`/`CaseEditViewModel.kt`) — this was explicitly deferred out of U1's prototype and must not be dropped. Covers at minimum: turning tracking off while events are ongoing (they get closed), and turning tracking on for a Case with pre-existing events that have no end time.
- [ ] `LogDetailViewModel.kt`'s `computeEndedAt`/`durationFieldsFor` and the `LogDetailForm`/`EndTimeSection`/`DurationSection` UI collapsed into the single validated control from U1 — **replaced by three plain Days/Hours/Minutes fields**, not one amount+unit field (see Notes below); `DurationUnit`/`durationUnitFor`/`durationFieldsFor` stay `Long`-only and need no decimal support, since the final prototype design dropped the single-field+unit-selector approach specifically to avoid fractional values.
- [ ] Schema bump (no `AutoMigration`/hand migration needed pre-release); `demo_backup.json` and test fixtures hand-edited to the new shape.
- [ ] Voice ×3 for any new/changed strings; no gamification language.
- [ ] `HODITH_SPEC.md` synced with the new model (data model and Case/Event vocabulary sections).
- [ ] Docs sweep for `DurationMode`/`MANUAL`/`START_STOP` mentions across `docs/`.

**Notes from U1** (validated in `ui/prototype/DurationUnificationPrototype.kt` — read before starting, delete once superseded):

- **Ongoing/Ended is derived, not stored** — `isOngoing = endedAt == null || endedAt > now`, mirroring today's MANUAL-mode `isStillRunning` exactly, just generalized to every tracked event rather than gated by Case-level mode. No separate ongoing flag/column.
- **Duration is three plain fields — Days / Hours / Minutes — not one amount+unit field.** A simple floor-division decompose/compose pair (no unit-selection heuristics, no decimals: "1.5 days" is just "1" day + "12" hours) converts between a millis duration and the three fields. This was a deliberate pivot during U1 review away from an earlier single-field+unit-selector design specifically to avoid fractional values reaching `DurationUnit`/Insights/Big Picture/StatsEngine at all.
- **The Duration fields and the end-time date/time buttons are always visible, but start empty/unset — never auto-filled with a live-elapsed guess.** They only get a value from an actual user action: typing into Days/Hours/Minutes, picking an end date/time, or tapping Stop (which commits the real elapsed duration as a fact). The end-time buttons prompt "Choose date"/"Choose time" until then; both stay two-way synced once something is set. Tapping "Start again" clears everything back to empty.
- **A separate plain-text note under the status row reads "Ongoing for Xh Ym"** (reusing the real `formatElapsedDuration`) whenever the event is ongoing — independent of whatever is or isn't in the Duration fields, so "how long has this actually been running" stays answerable even when nothing's been typed.
- **The end-time label has three states, not two**: "End time can be set manually" (nothing stated yet), "Set to end" (stated, still ahead of now — still Ongoing), "Ended at" (stated, already passed — Ended). The Duration fields get their own paired label: "Planned duration" while Ongoing (what's typed is a projection, not a fact yet), plain "Duration" (the real Voice key) once Ended.
- **Status row**: "Ongoing"/"Ended" status word styled the same as the start-time label (reads as a label, not a heading) + a filled (not outlined) Stop/Start-again button — visually distinct from the outlined date/time selectors. Bright's `logSheetEndLabel` ("Wrapped up at") needs its trailing "at" dropped to "Wrapped up": it's a standalone status word now, not leading straight into an inline time value the old `EndTimeSection` layout assumed.
- **New Voice copy needed** (prototype placeholders, not committed keys yet): "Started" (event start-time label — consider whether this simply replaces `logSheetTimeLabel` ["When"] rather than adding a second key), "Start again" (the reopen action — distinct from the screen's own Create/Save button), "Set to end" / "End time can be set manually" / "Ended at" (end-time section labels), "Planned duration" (Duration fields' label while Ongoing), "Ongoing for …" (the elapsed note), "Days" / "Hours" / "Min" (the three field labels), "Choose date" / "Choose time" (unset button prompts), "Create" (new-event save-button wording, see below). `logSheetStopNowAction` ("Stop now") and `logSheetDurationLabel` ("Duration") are reused as-is.
- **Screen-level Save button wording is fixed, not state-dependent**: "Create" for a brand-new event, "Save" for editing an existing one — never flips with ongoing/ended (that was specifically today's START_STOP-only behaviour, and it collided with the duration control's own Stop/Start-again wording once tested).
- **Superseded, don't carry over**: the old `ExpectedEndNotice`/`OngoingExpectedEndText` pattern (a notice with the time embedded) is replaced by the "Set to end" label + date/time buttons for Log/Edit Event. `OngoingExpectedEndText`/`OngoingTimeText` are also used by History/Home/Insights rows (`ui/common/OngoingIndicator.kt`) — those call sites need their own equivalent decision, not an automatic copy of the Log/Edit Event control's choice.
- **Case Edit's duration switch** is one row: a "Track duration" label (placeholder, replaces `voice.caseDurationModeLabel` ["Duration"] now that this is an on/off switch, not a 3-way picker) + info icon + `Switch`, via `RowWithInfo` (`ui/common/SectionWithInfo.kt`) and `themedSwitchColors()` — the same shape as the check-in toggle (`WatchesTab.kt`) and Settings' cloud-backup toggle, not a new pattern.
- **The end-date picker needs no "max = now" ceiling**, unlike today's start/end pickers (`MaxDateBoundDatePickerDialog` with `maxDateUtcMillis = now`) — picking a future date is how a projected/expected duration gets set. Picking a future date while editing an already-ended event is expected to just reopen it as Ongoing with a projected end, not an error case.
- **Decided during U1 review**: the status row's "Ongoing"/"Ended" word is sufficient to distinguish a stated-future projection from a real past end — no extra visual treatment needed; Bright's "Wrapped up at" → "Wrapped up" trim ships as written, not reworded further; start-time editing (`TimeSection`'s pickers) is unchanged by this story — the prototype left it display-only on purpose, not as a gap to fill. (An earlier pass of this review had chosen full decimal support for a single amount+unit field — superseded by the Days/Hours/Minutes pivot above, which needs no decimals at all.)

**Plan** — rename/schema first (behaviour-neutral where possible), then `ActiveSpan.kt`, then the confirm-dialog redesign, then each consumer, then the Log/Edit Event UI swap, then docs.

**Tests** — `ActiveSpan` unit tests for the new two-state logic; `CaseEditViewModel`/`LogDetailViewModel` tests for the new switch and confirm-dialog flows; instrumented coverage for Case Edit, Log/Edit Event, Big Picture, History, Insights, Home, and both widgets reflecting the merged model.

The end-date/duration editing logic specifically needs every **state transition** covered, not just the three states checked in isolation. States: **open-ended** Ongoing (`endedAt == null`), **planned** Ongoing (`endedAt` stated and still ahead of `now`), **Ended** (`endedAt` stated and in the past). Transitions to cover:

- open-ended → planned: typing a Days/Hours/Minutes value, or picking an end date/time, that lands after `now`.
- open-ended → Ended: Stop, or typing/picking a value that lands at or before `now`.
- planned → Ended: Stop, or editing the duration/end time to a value that's now in the past.
- planned → planned: editing to a different future value — stays Ongoing with the new projection.
- planned → open-ended: clearing the Days/Hours/Minutes fields back to blank.
- Ended → open-ended: Start again.
- Ended → planned: editing the end time/duration into the future — reopens it as Ongoing, not an error case.
- Ended → Ended: editing to a different past value — stays Ended with the new actual duration.

At every state, duration and end-time must stay two-way synced — test both edit directions (typing a duration recomputes the end time; picking an end date/time recomputes the duration fields), not just one.

## Standalone

No cross-dependencies — pick by appetite. Grouped by area below; items are identified by title or branch, not a number.

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

### Intensity-toggle: trend/notification code ignores the Case flag

*Branch: none yet · Complexity: S · Priority: Medium · Area: Insights*

🔍 **Investigation**

Found while fixing the "intensity can be logged without a rating" bug (`fix/intensity-required-when-enabled`). Insights' intensity stat panel correctly hides itself once `Case.intensityEnabled` is off (`InsightsTabState` gates on the live flag), but `outcomeValueFor` (trend/notification code) still reads `EventEntity.intensity` directly with no `intensityEnabled` check — so ratings logged while a Case tracked intensity can keep quietly influencing trend/notification output even after the Case stops tracking it and its own Insights panel is hidden.

**Acceptance criteria**

- [ ] `outcomeValueFor` (or its caller) checks `Case.intensityEnabled` the same way `InsightsTabState` does, so a Case with intensity off is read consistently everywhere, not just on the Insights panel.
- [ ] A test with intensity turned off after rated events exist, confirming trend/notification no longer factor in those old ratings.

**Plan** — find every `outcomeValueFor` call site, gate it on the live flag.

**Tests** — a `WatchEngine`/trend test with intensity toggled off after rated events exist.

### Single-case widget has no persistent in-widget way to reopen its Case picker

*Branch: none yet · Complexity: S · Priority: Low · Area: Widgets*

🎨 **Design decision** — found while fixing `fix/widget-edit-affordance` (List widget's empty-state
message wrongly told users to long-press for an "Edit" option that most launchers don't surface;
fixed by adding a persistent edit icon to the List widget's header, which already has room for
one). `SingleCaseWidget.kt` has no header or chrome at all — just an icon, subtitle, and a
full-width log button in an already-dense square — so the same fix doesn't drop in without first
deciding where an edit affordance would go without crowding the existing tap targets.

**Acceptance criteria**

- [ ] A layout spike (Compose Preview or similar) for a small edit affordance on the Single-case
  widget that doesn't shrink or overlap the icon/subtitle or the log button's tap target.
- [ ] The chosen spot wired to reopen `SingleCaseWidgetConfigureActivity` the same way the List
  widget's icon reopens `ListWidgetConfigureActivity`.
- [ ] Voice ×3 for any new accessibility label needed.

**Plan** — prototype the layout first (cheap spike, not production code) before committing to a
placement, per this repo's validate-before-building rule.

**Tests** — an instrumented test mirroring `WidgetChromeNavigationTest`'s edit-icon coverage once a
placement is chosen.

## Deferred

### CI: per-shard emulator overhead outside test execution
*Deferred: moved from Standalone. Revisit when CI wall-clock time matters again.*

*Branch: none yet — investigation first · Complexity: S (investigation) · Priority: Medium · Area: Repo*

🔍 **Investigation**

Each instrumented shard spends about five minutes outside test execution. On the `ui` shard the `Run instrumented tests` step takes about 18 minutes against about 12 minutes of JUnit time; on the `repository` shard it takes about 4.6 minutes against about 0.4 minutes. The gap is emulator boot, `installDebug`, and the androidTest APK build. The two shards run in parallel, so the `ui` shard sets wall-clock time, and its overhead is the lever.

**Acceptance criteria**

- [ ] The gap split into emulator boot, installs and Gradle build, from the `Run instrumented tests` step's log timestamps across several recent runs.
- [ ] Options compared on wall-clock saved, each with a keep/drop call: caching a booted emulator snapshot (checking what `reactivecircus/android-emulator-runner` supports); caching the androidTest build; folding the `repository` shard into the `ui` run only if it removes a boot without lengthening wall-clock time.
- [ ] The chosen option implemented, with wall-clock before and after measured over the same number of runs.

**Plan** — measure from the workflow logs first; no workflow edit until the split is known. Test execution is not the lever here.

**Tests** — none; verified by the CI job summary timings.

**Concern** — a cached emulator snapshot can hide flakiness that a cold boot exposes, so any caching change needs a few cold-boot runs checked before it's kept.

### Espresso-intents: verify external intent handoffs in tests
*Deferred: moved from Standalone. A fair amount of work for a small gain, and it adds a test-only dependency.*

*Branch: `chore/espresso-intents` · Complexity: S · Priority: Low · Area: Repo*

🎨 **Design decision** — adds a test-only dependency, so it needs a human call. Downsides: `espresso-intents` must stay aligned with the pinned `espresso-core` 3.7.0, `Intents` is process-wide state that leaks between tests unless released, and intercepted intents prove the Intent the app built, not that a browser or mail app opened.

`MANUAL_TEST_PLAN.md` About 1 (privacy policy link opens the browser) and About 2 (Contact Us builds a mail intent to the developer address) only need the built Intent verified. The external app handoff stays manual.

**Acceptance criteria**

- [ ] `espresso-intents` added to `androidTest` at the same version as `espresso-core`, pinned in `libs.versions.toml` with the same explanation the `espresso-core` pin carries, so the `NoSuchMethodException` on some API levels does not return.
- [ ] Both CI shards green on the API 36 emulator before merge.
- [ ] Tests use `IntentsRule` (or init and release per test), so recorded intents don't leak between tests.
- [ ] The About privacy-policy link and Contact Us assert the built intent's action and URI or address.
- [ ] Manual plan About 1 and 2 reworded to cover only the external app handoff.
- [ ] If the share-sheet step is included: the test matches the wrapped chooser intent and the FileProvider URI.

**Plan** — add the dependency and pin, land one smoke test, then the About tests. The share-sheet test comes last, since chooser matching is the most fragile.

**Tests** — `AboutScreenTest` (privacy link, Contact Us); `SettingsScreenTest` (Contact Us row); the share-sheet step only if included.

**Concern** — intercepted intents never launch a real app, so resolution to the right app is not proven. The blocked "Rate the app" item would reuse this dependency.

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
