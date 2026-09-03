# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

## How this file is organised

Items are grouped by how they connect, not by feature area:

- **Story B — copy & Voice** — a short chain that has to land after everything else that touches copy.
- **Standalone** — isolated items with no cross-dependencies; pick any when resources are thin.
- **Blocked** — gated on something external; not startable now.

The old **Story A** (Start/Stop, duration & ongoing events) is done bar one read-through, **A10**, which has no dependencies and now sits under Standalone.

Each item carries:

- a **trailer** — *Branch · Complexity · Priority · Area*. Complexity: S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week (same scale as HODITH_SPEC §17). Priority: High gates the first release or corrects something wrong today · Medium worth doing before alpha · Low cosmetic or deferrable · Blocked can't start yet. Area preserves the old grouping (Bug / Duration / Big Picture / Share / Settings / Voice).
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

**Plan** — needs a product decision first: which sections (and in what fixed order) Square always shows. Once decided: show `SectionsPicker` only when `ShareCardFormat.STORY` is selected in `SharePreviewScreen.kt`, and have `shareCardState()` source Square's sections from the fixed preset, independent of `selectedSections`.

**Tests** — `ShareCardStateTest.kt` needs coverage that Square's output is driven by the preset; `SharePreviewScreenTest.kt` needs coverage that the section picker appears only for Story. `ShareCardTemplateTest.kt`'s Square floor/no-clip coverage should keep passing as-is, since the preset's fixed content is what it already exercises.

**Concern** — this is as much a product decision as an implementation task, and it touches Voice (Story-only picker copy), so land it before B2.

### B2 · Review phrasing across all three Voice implementations

*Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium · Area: Voice*

🎨 **Design decision** — the rubric is an authored artifact and the audit needs a human ear. **Must land last** — after every other copy-touching item. The only copy-touching items still open ahead of it are S9 (check-in copy reword) and B1 (Story-only picker copy).

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

No cross-dependencies. Pick any when resources are thin. One real ordering constraint and three soft batching opportunities:

- **S9 → B2** *(ordering)* — S9 rewrites `checkInDueNotificationBody`, so it has to land before B2's Voice audit. Noted in both items.
- **On-device QA batch — S2 · S4 · S5, with S3** — S2 needs the widget red-`+` repro, S4 the Bright/Intense empty-state repro, and S4/S5/S3 are all Bright/Intense visual work. One emulator or device session covers them.
- **Case Detail Log-tab — S4** — touches `LogTabContent` / `CaseDetailScreen.kt` and `CaseDetailScreenTest.kt` (empty-state alignment). S7 (sort row) and S8 (event-edit screen) already landed here, so expect a small rebase.
- **Fully isolated — S1** (icon vector + Previews), **S6** (external content), and **A10** (the old Story A read-through). Any order, any time.

### A10 · Verdict engine's handling of duration events is unreviewed

*Branch: `feat/verdict-duration-review` · Complexity: S · Priority: Low · Area: Big Picture*

🔍 **Investigation** — read-through first; a fix only if the read finds a real distortion. 🎨 **Design decision** — the ruling belongs in spec §8. No dependency on other outstanding work.

The verdict engine and its observation-window / observed-rate math count each event once at `occurredAt` and never look at `endedAt`. Whether a multi-day event should still count as one occurrence (likely yes), whether a still-running event counts before it stops, and whether the observation window's end should track a running event, have not been decided or tested.

**Acceptance criteria**

- [ ] Verdict engine, observation window, and observed-rate code read through for duration/ongoing handling.
- [ ] A written ruling added to HODITH_SPEC §8 (even if the ruling is "unchanged — starts only, one occurrence each").
- [ ] Code changed only if the read finds a genuine distortion; otherwise a test locking the current behaviour.

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

### S2 · Widget visuals have drifted from the in-app Plain theme

*Branch: `fix/widget-plain-fidelity` · Complexity: M · Priority: Medium · Area: Bug*

🎨 **Design decision** — what "matches Plain" means for a Glance surface (type ramp, spacing, corner radii, elevation), given Glance can't consume the M3 `ColorScheme` / `Typography` directly. 🔍 **Investigation** — the reported red "+" glyph has no cause in a source read; needs an on-device repro pass within this work.

Both widgets hand-roll every `TextStyle` / `GlanceModifier` with values pinned in `widget/WidgetCommon.kt` — colours are sourced from `Color.kt`'s extracted Plain-light vals, but type sizes, weights, spacing, `WidgetCornerRadius = 16.dp` and `MinTapTarget = 48.dp` are local literals, so the result reads as an approximation of Plain rather than Plain. Spec §15 / DEV_PLAYBOOK §4 already commit the widget to the Plain light palette — this is a fidelity pass, not a theming change. Separately, testers report the "+" glyph rendering red; it is unconditionally `WidgetPalette.accent` (`PlainLightPrimary` `#3A6B76`, a teal) in `widget/ListWidget.kt` / `widget/SingleCaseWidget.kt`, with no red in its history — the only red widget element is the Stop button (`PlainLightError #BA1A1A`) that replaces the "+" for a Case with a running event.

**Acceptance criteria**

- [ ] A short written spec of the widget's Plain type ramp + spacing + shape tokens, derived from `ui/theme/Type.kt` / `Shape.kt` and the Plain Home surfaces, added to DEV_PLAYBOOK §4; HODITH_SPEC §15 gains one sentence that the widget targets Plain's type ramp and spacing, not only its palette.
- [ ] `WidgetCommon.kt` grows the missing tokens so `ListWidget.kt` / `SingleCaseWidget.kt` stop carrying inline literals.
- [ ] Row layout, header, and empty state visually reconciled against Home's Plain rendering.
- [ ] Red "+" root cause identified on-device: rebuild, remove/re-add the widget, photograph; capture device / launcher / system theme; note List vs single-case; rule out the red Stop pill being misread; check for a stale older build. The "+" resolves to `accent` regardless of last-event age.
- [ ] Tests: existing `ListWidgetTest` / `SingleCaseWidgetTest` still pass; assertions added for any newly-centralised token and, once the cause is known, a Glance check that the "+" resolves to `accent`.

**Plan** — pull the Plain type/spacing/shape values into named `WidgetCommon.kt` tokens mirroring `Type.kt`, replace the inline literals in both widgets, eyeball against Home; run the red-"+" repro in the same on-device session.

**Tests** — `ListWidgetTest`, `SingleCaseWidgetTest`.

**Concern** — if the underlying "+" ask is a "you haven't logged in a while" nudge, that hits spec §4 (no gamification) / §7 (HODITH doesn't nudge logging) — needs a spec ruling first; steer is not to add it. Glance's constraints make the fidelity work convergence-by-hand, not a shared-token import.

### S3 · Selected-state indicator is low-contrast in the icon picker and the intensity selector

*Branch: `fix/case-icon-selection-contrast` · Complexity: S · Priority: Medium · Area: Bug*

🎨 **Design decision** — needs an affordance decision (checkmark overlay / stroked ring / scale-elevation change), not just a colour retune. Spec §3 principle 6 ("colour is never the only distinguisher") is the governing rule; no spec edit.

Selection is shown only by a background-color swap, with no border, ring, or checkmark. Three places share the pattern: Plain's `IconChoice` (`ui/case/CaseEditScreen.kt`) uses `primaryContainer` (#C7E8ED light) vs. `surfaceVariant` (#CFE8F8 light) — near-identical lightness; the event-log `IntensityChoice` (`ui/logsheet/LogDetailSheet.kt`) uses the exact same `primaryContainer`-vs-`surfaceVariant` fill-only swap; and Bright's `BrightIconChoice` uses `IconHalo`'s selected fill, a 16% tint wash of `primary` over `surface` (near-white on near-white).

**Acceptance criteria**

- [ ] Selection shown by an element that doesn't depend on background contrast alone.
- [ ] Applied in `IconChoice` (Plain/Intense, `CaseEditScreen.kt`), `IntensityChoice` (`LogDetailSheet.kt`), and `BrightIconChoice` — adapted to each visual language (flat fill vs. glow halo) and each control's size.
- [ ] A Preview per theme showing selected vs. unselected side by side, for both the icon picker and the intensity row.
- [ ] `.selectable(selected = …)` / `Role.RadioButton` structure unchanged in all three.

**Plan** — needs a design decision on the affordance, not just a color retune, since a lightness-only swap will keep being fragile across themes (including future ones): add a distinguishing element that doesn't depend on background contrast alone — a checkmark overlay, a stroked ring, or a scale/elevation change. Once decided, apply it in `IconChoice` / `IntensityChoice` (Plain/Intense flat fill) and `BrightIconChoice` (glow halo) — the same primitive won't drop into both visual languages identically.

**Tests** — no existing test covers selection visuals (Compose Previews only); add or update a Preview per theme for the icon picker and the intensity row showing selected vs. unselected side by side. The selection state itself is already exposed structurally via `.selectable(selected = ...)` / `Role.RadioButton`, so there's nothing new to unit-test beyond the visual.

### S4 · Empty-state note is shifted to the left edge on Bright and Intense

*Branch: `fix/empty-state-left-alignment` · Complexity: S to fix, M to diagnose · Priority: Medium · Area: Bug*

🔍 **Investigation** — confirmed visually, but a source read found no cause; needs a repro-and-diagnose pass first.

Big Picture, the case detail Log tab, and the Insights tab empty states (`BigPictureScreen.kt`, `CaseDetailScreen.kt`'s `LogTabContent`, `InsightsTab.kt`) all use the identical `Modifier.align(Alignment.Center)` / `contentAlignment = Alignment.Center` pattern, with no theme-conditional branching anywhere in that code.

**Acceptance criteria**

- [ ] Screenshots on Bright and Intense for all three locations; confirmed whether it repros in all three or just Big Picture.
- [ ] Cause found outside the three already-read composables (parent `Scaffold`/`Surface`/`Card`, `LocalLayoutDirection`, `CardDecorationStyle` / `GlowDecoration.kt`).
- [ ] Empty-state text centred on all themes.
- [ ] A Compose UI test asserting the text node's bounds are centred (or at least not flush left) for Bright/Intense.

**Plan** — needs a repro-and-diagnose pass before a fix: capture screenshots on device/emulator for Bright and Intense across all three locations, and check what's outside the three composables already read — parent `Scaffold`/`Surface`/`Card` wrapping, `LocalLayoutDirection`, or a theme-specific decoration (`CardDecorationStyle`/`GlowDecoration.kt`) that might apply an offset the static read wouldn't show. Confirm whether it reproduces in all three locations or just Big Picture before assuming it's the shared pattern.

**Tests** — no existing test asserts empty-state horizontal position; once the cause is found, a Compose UI test asserting the text node's bounds are centered (or at minimum not flush against the left edge) for Bright/Intense would catch a regression.

### S5 · Bright theme's light-mode `onSurfaceVariant` fails WCAG AA contrast

*Branch: `fix/bright-light-onsurfacevariant-contrast` · Complexity: S · Priority: Medium · Area: Bug*

`Color.kt`'s `brightLight` `onSurfaceVariant` (#8A7A68) fails WCAG AA contrast (4.5:1) against both `surface` (~4.15:1) and `background` (~3.91:1). Found while writing `HodithThemeTest`'s new WCAG contrast test (scoped to Plain only for that reason — see its doc comment); not fixed in the branch that found it, since it's a pre-existing gap unrelated to the Plain-theme work.

**Acceptance criteria**

- [ ] `onSurfaceVariant` darkened to clear 4.5:1 against both `surface` and `background`, keeping Bright's warm cast.
- [ ] `secondary` / `onSecondaryContainer` checked against the same bar and fixed if needed.
- [ ] `HodithThemeTest`'s contrast test widened from Plain-only back to all 6 theme×mode combinations.

**Plan** — darken `onSurfaceVariant` (and check `secondary`/`onSecondaryContainer`, which look similarly light) until it clears 4.5:1 against both `surface` and `background`, keeping Bright's warm cast. Then widen `HodithThemeTest`'s new contrast test from Plain-only back to all 6 theme×mode combinations, closing the gap this item is tracking.

**Tests** — `HodithThemeTest`'s contrast test already exists and is ready to widen once this lands; no new test scaffolding needed.

### S6 · Audit the hosted privacy policy and Play data-safety form

*Branch: none — external content, not a code change · Complexity: XS · Priority: Medium · Area: Settings*

🌐 **External action** — both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

**Acceptance criteria**

- [ ] Hosted policy read against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and updated wherever it still claims otherwise.
- [ ] Play data-safety answers reconciled with the same copy (once a listing exists).

**Plan** — read both against the new About copy and update wherever they still claim otherwise.

### S9 · Check-in notification copy is a bare reproach in the Serious voice

*Branch: `fix/check-in-notification-copy` · Complexity: S · Priority: Medium · Area: Voice*

Spec §11 requires check-in copy to "ask whether anything went unlogged, never imply the user should keep it up". The Serious `checkInDueNotificationBody` (`ui/voice/Voice.kt`) is just `"Nothing logged in $silentDays days."` — a flat statement with no question, which reads as a scold. Goth (`"$silentDays days of silence. Has it stopped, or have you?"`) and Quirky (`"Nothing logged in $silentDays days — all quiet, or did you forget?"`) both already pose the question.

**Acceptance criteria**

- [ ] `checkInDueNotificationBody` in all three voices asks the "did something go unlogged?" question per §11; Serious brought in line with Goth/Quirky.
- [ ] `checkInDueNotificationTitle` / `checkInsSummaryNotificationTitle` reviewed for the same tone in all three voices.
- [ ] Copy only — no new keys, no behaviour change, no spec edit (§11 already requires this framing). Re-fire cadence is explicitly out of scope.
- [ ] `VoiceTest`'s existing non-blank / no-gamification checks still pass.

**Plan** — reword the three `checkInDueNotificationBody` overrides (and check the two title keys) in `Voice.kt`. Pure string edits.

**Tests** — `VoiceTest` (existing).

**Concern** — changes existing Voice strings, so land before B2's audit.

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
