# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Standalone** — isolated items with no cross-dependencies; pick any when resources are thin.
- **Performance** — what's left of the S6 high-volume review, one shared root cause.
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

🎨 **Design decision** — the rubric is an authored artifact and the audit needs a human ear. **Must land last** — after every other copy-touching item. Copy-touching items still open ahead of it: B1 (Story-only picker copy) and S5 (resolved-hunch row wording). The `feat/declutter-nudges` branch reworded the Serious `checkInDueNotificationBody` and renamed `checkInsSummaryNotificationTitle` → `notificationsGroupSummaryTitle` (drafts in all three voices) — fold those into the audit. The `feat/insights-from-first-event` branch added `insightsNothingLoggedMessage` and `insightsSingleEventNote` (drafts in all three voices, replacing the old `insightsNotEnoughDataMessage`) — fold those in too. The `feat/big-picture-overview-detail` branch retired `bigPictureEventNoteEmptyState` (×3) and added `bigPictureDetailDialogTitle` + `bigPictureDetailEditDescription` (×3) plus four shared `get()` field labels — fold those in.

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

No cross-dependencies — **S1** (icon vector + Previews), **S2** (Trend-card calculation review), **S5** (hunch-history row redesign), **S7** (external content). Pick by appetite. The **Performance** section below is a separate cluster with its own shared root cause.

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

### S5 · Resolved-hunch history rows need a proper design and content pass

*Branch: `feat/hunch-history-row-redesign` · Complexity: M · Priority: Medium · Area: Hunch*

🎨 **Design decision** — the whole row: which fields, their hierarchy, and the wording per voice. Touches Voice, so before B2.

A full formatting review of the resolved-hunch record — design *and* content. The "0 months ago" bug is the trigger, not the scope.

Today (`ui/casedetail/CaseDetailScreen.kt` — `HunchHistoryCard:521-533`, `HunchHistoryRow:535-559`): a header plus an "N of M held up" summary, then per row — line 1 is `hunchHistoryRowText(direction, frequencyLabel)` ("Too often, ~7×/week") left / `hunchHistoryRowWhen(monthsAgo(resolvedAt))` right; line 2 is `hunchHistoryRowOutcome(band, observedRateLabel)`. No made-date, no absolute resolved-date, no verdict-tier text, and no structure beyond two text lines. `monthsAgo` (`viewmodel/CaseDetailViewModel.kt:180-188`) counts whole calendar months, so a hunch resolved inside its first month reads "0 months ago" / "0 months past".

**Acceptance criteria**

- [ ] A decided row design recorded here — an ordered field list (made date, resolved date or a "held for N weeks" span, direction + expected rate, observed rate, outcome band, and whether the verdict tier belongs in the row) plus layout and per-voice wording.
- [ ] `HunchHistoryRow` rebuilt to it; the time display reworked to absolute dates and/or a held-for span.
- [ ] `hunchHistoryRowWhen` replaced or removed (×3 voices); `monthsAgo` removed if nothing else uses it (grep); any new Voice keys added ×3.
- [ ] The `HunchHistoryCard` summary line re-checked against the new row shape.
- [ ] `HunchTabStateTest`, `CaseDetailScreenTest`, `VoiceTest` updated.

**Plan** — decide the row (a sketch or field list in this item), then implement. `HunchEntity` already carries `createdAt` and `resolvedAt`, so no schema change.

**Tests** — `CaseDetailScreenTest` swaps its "N months ago" assertions for the new fields; `VoiceTest` covers the new keys.

**Concern** — standalone; the redesign is a small surface but a visible one, and the content call (does the verdict tier show?) is a product decision.

### S7 · Audit the hosted privacy policy and Play data-safety form

*Branch: none — external content, not a code change · Complexity: XS · Priority: Medium · Area: Settings*

🌐 **External action** — both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

**Acceptance criteria**

- [ ] Hosted policy read against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and updated wherever it still claims otherwise.
- [ ] Play data-safety answers reconciled with the same copy (once a listing exists).

**Plan** — read both against the new About copy and update wherever they still claim otherwise.

## Performance

The open tail of the S6 high-volume / rapid-logging review. One root cause runs through both: **Room's invalidation is table-level**, so every `events` write re-runs every query that touches `events`, over the whole dataset — fine per query until the dataset is large or the query is heavy. S6's measurements and the reasoning behind each item live in the local (non-committed) performance baseline notes; the stat-engine aggregation it flagged turned out not to be a bottleneck.

### F2 · Big Picture loads every event and every tag on every write

*Branch: `refactor/big-picture-windowed-query` · Complexity: M · Priority: Medium · Area: Performance*

`BigPictureViewModel` subscribes to `observeActiveCasesWithEventsAndTags()` — the full cross-Case event set *plus a tag junction per event*, the heaviest query in the app — and it refetches on every `events` / `event_tags` write. At S6-scale volumes this is a visible stall on Big Picture open and on logging while it's on screen. The grid opens on the current month and scrolls, and it never renders tags on the grid itself — only the day / week tap-through dialog needs them.

**Acceptance criteria**

- [ ] The grid query bounded to a visible month range (open month ± a scroll buffer), extended as the user scrolls, rather than all history eagerly.
- [ ] Tags dropped from the grid query; an event's tags loaded on demand when a day / week detail dialog opens.
- [ ] The filter chips' tag universe (`allTagNames`) sourced from a lightweight distinct-tags query, not by flattening every event's tags.
- [ ] Re-run the S6 baseline probe: Big Picture cold open and per-write refetch both within a frame's budget per visible month.

**Plan** — windowed month-range DAO query for the grid; separate on-demand tag fetch for the detail dialogs; distinct-tags query for the filter chips.

**Tests** — `bigPictureUiState` over a windowed event list; a DAO test for the month-range query; the detail-dialog tag fetch; Big Picture Compose tests stay green.

**Concern** — scroll-triggered range extension must not stutter or flash empty cells on a fast scroll to a distant month, and the month-picker quick-jump (§9) must still land populated.

### F4 · Log tab has no query cap and sorts the whole history in memory

*Branch: `feat/log-tab-paged-query` · Complexity: M · Priority: Low · Area: Performance*

🔍 **Investigation** — measure in alpha before committing to Paging.

`observeEventsWithTagsForCase` returns the full Case history (with its tag junction), then `sortEventsForLog` sorts it all in memory into one `LazyColumn`. Comfortable at ordinary volumes; a multi-year single Case is the edge.

**Acceptance criteria**

- [ ] A call, informed by alpha feedback, on whether the Log tab needs a capped / paged query or stays as-is.
- [ ] If taken: Paging 3 (or a capped query with "load older") for the Log tab; the Started / Ended sort (§6) pushed into SQL or kept as a small in-memory sort over the loaded page.

**Plan** — defer until F2 lands and alpha shows whether the Log tab feels slow; then Paging or a capped query.

**Tests** — `CaseDetailScreenTest` Log-tab coverage; a DAO test for the paged / capped query if taken.

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
