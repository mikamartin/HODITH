# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

Each item carries a trailer: **Branch** (the branch to open for it), **Complexity** (S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week — same scale as HODITH_SPEC §17), and **Priority** (High = gates the first release or corrects something wrong today · Medium = worth doing before alpha · Low = cosmetic or can wait). Then **Plan** (the main points), **Tests** (what coverage the change needs or breaks), and **Concern** where there is one.

## Recommended order

Sequencing matters more than usual here — several items collide in the same files, and two of them are cheap now and expensive after the first release.

1. Share card Square redesign (below) — it touches copy, so land it before Voice phrasing.
2. **Voice phrasing review last.** It is a mass edit across a 1819-line file; run it once, after every other copy-touching item on this list has landed, or it conflicts with all of them.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.

  *Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium*

  **Plan** — 294 `Voice` keys total, but only 213 are declared per-voice and need independent authorship (639 strings); the other 81 are shared `get()`/default-body keys (structural chrome — nav labels, field labels, and the like) reviewed once, not per voice. ~720 strings total. Not hard, but big, and it needs a human ear rather than a mechanical pass. Write the rubric first (what "consistent" means per voice: person, tense, sentence length, punctuation and emoji budget, and a locked vocabulary for Case/Hunch/Verdict/Event/Trigger), then audit in slices by screen rather than reading `Voice.kt` top to bottom — the file is grouped by key, so reading it linearly compares the wrong things. Produce a findings list first; fix in a second commit. The rubric should explicitly cover the ~105 em dashes currently in the copy (18 Serious, 36 Goth, 51 Quirky) — most convert cleanly to a period or comma, but Goth and Quirky use the em dash roughly 2–3x more often as a genuine mid-sentence pivot (a beat before a punchline or gothic aside), so each needs a per-string call rather than a mechanical substitution.

  **Tests** — `VoiceTest` today walks every key by reflection (non-blank in all three voices, no per-voice key identical across all three) plus the share-card pronoun rule. A copy audit is the right moment to add further mechanical invariants: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab and button labels, no double spaces or trailing whitespace. Instrumented tests reference `PlainVoice.x` by constant rather than by literal, so copy edits shouldn't break them — confirm that holds everywhere before starting (a grep for hardcoded UI literals in `androidTest`).

  **Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

- [ ] Plain theme's light background/surface colors (`PlainLightBackground` #F4F6F8, `PlainLightSurfaceVariant` #DEE4E7, `PlainLightOnSurfaceVariant` #5B6670 in `Color.kt`) read as slightly blue-grey/murky rather than neutral. Revisit the palette.

  *Branch: `fix/plain-light-palette-neutrality` · Complexity: S to change, M to verify · Priority: Medium*

  **Plan** — decide the target first (true neutral grey vs. warm grey), keeping `primary` #3A6B76 as the only chroma in the scheme. Then note that the three named colors aren't the whole problem: `surfaceVariant` #DEE4E7, `surfaceContainerHigh` #E9ECEE, `outline` #D3DAE0 and `outlineVariant` #E7ECEE are all in the same blue-grey family and are *not* among the named/shared constants, so a fix that only touches the three named ones leaves the rest mismatched. Treat the light scheme as one change.

  **Tests** — there's no automated coverage of colour values and shouldn't be. Contrast is the one mechanically checkable property: add a JVM test asserting WCAG AA contrast for `onSurface`/`onSurfaceVariant` against `surface`/`background`, which is worth having independently of this item. Visual verification is the human's (Compose Previews per theme, plus the widget and a heatmap-bearing screen).

  **Concern** — two blast radii, both easy to miss. (1) The seven `PlainLight*` constants are consumed directly by `WidgetCommon.kt`'s `WidgetPalette`, which renders every Glance widget regardless of the user's in-app theme (DEV_PLAYBOOK §4), so changing them restyles the widgets too. (2) `HeatmapShading.kt`'s `toCellColor` lerps `surfaceVariant → primary`, so `surfaceVariant` is the base of the entire shading ramp — changing it moves every calendar-heatmap cell, rhythm grid cell, intensity cell, *and* their share-card mini-copies, in all three themes' light mode. Neither is a reason not to do it; both are reasons the review pass is wider than "the Plain theme's background".

- [ ] Case icon picker's selected-state indicator is low-contrast, especially in Plain and Bright. Selection is shown only by a background-color swap, with no border, ring, or checkmark: Plain's `IconChoice` (`ui/case/CaseEditScreen.kt`) uses `primaryContainer` (#C7E8ED light) vs. `surfaceVariant` (#DEE4E7 light) — close in lightness — and Bright's `BrightIconChoice` uses `IconHalo`'s selected fill, a 16% tint wash of `primary` over `surface` (near-white on near-white).

  *Branch: `fix/case-icon-selection-contrast` · Complexity: S · Priority: Medium*

  **Plan** — needs a design decision on the affordance, not just a color retune, since a lightness-only swap will keep being fragile across themes (including future ones): add a distinguishing element that doesn't depend on background contrast alone — a checkmark overlay, a stroked ring, or a scale/elevation change. Once decided, apply it in both `IconChoice` (Plain/Intense branch, `CaseEditScreen.kt`) and `BrightIconChoice` — the two use different visual languages (flat fill vs. glow halo), so the same primitive won't drop into both identically.

  **Tests** — no existing test covers icon-selection visuals (Compose Previews only); add or update a Preview per theme showing selected vs. unselected side by side for manual verification. The selection state itself is already exposed structurally via `.selectable(selected = ...)` / `Role.RadioButton`, so there's nothing new to unit-test beyond the visual.

## Data integrity

- [ ] Backup/restore import does no semantic validation, and a malformed backup can crash the app. `SettingsViewModel.performImport` only checks the JSON is Moshi-decodable and schema-version-compatible; `RoomHodithRepository.importBackupData` then wipes the DB and inserts every deserialized entity as-is — no length caps, no positivity checks on numeric fields, no referential-integrity check across the file, and the insert step itself isn't wrapped in a try/catch anywhere (no `CoroutineExceptionHandler` exists in the app), so a backup with e.g. a dangling `caseId` reference throws an uncaught `SQLiteConstraintException` inside `viewModelScope.launch`. `docs/HODITH_SPEC.md` §16's "import validates before touching the DB" claim overstates what actually happens today — it's JSON-shape validation only.

  *Branch: `fix/backup-import-validation` · Complexity: M · Priority: High*

  **Plan** — needs two layers: (1) semantic validation before `importBackupData` runs — reject/clamp field lengths and ranges matching the rules already enforced in-app (case name/description length, non-blank names, positive trigger `windowDays`/`threshold`, etc.), and check referential integrity across the whole backup (every `caseId`/`tagId`/`eventId` reference in events/tags/hunches/triggers resolves to a row present in the same file) before the DB is touched at all, so a bad file is rejected atomically rather than partially trusted; (2) wrap the `importBackupData` call so a constraint violation that slips through becomes a clean import-failure message rather than a crash. This is the one genuinely external-input boundary in an otherwise local-only app (spec §16) and deserves the same care as a network API boundary would elsewhere. Also correct HODITH_SPEC.md §16's "import validates" wording once real behavior is decided.

  **Tests** — extend `BackupImportIntegrationTest.kt` (currently only round-trips app-exported, valid-by-construction data) with hand-built malformed fixtures: a blank case name, a negative/zero trigger `windowDays`, an out-of-range threshold, and a dangling `caseId` reference — asserting each is rejected without touching the existing DB, and that none crash.

  **Concern** — this touches the app's one real trust boundary and changes failure-mode behavior (today: possible crash; after: a rejected import), so it's worth a product decision on the exact user-facing message/behavior for a rejected import, not just silently failing.

## Bugs

- [ ] Empty-state note is shifted to the left edge of the screen on Bright and Intense — confirmed visually. A source read found no cause: Big Picture, the case detail Log tab, and the Insights tab empty states (`BigPictureScreen.kt`, `CaseDetailScreen.kt`'s `LogTabContent`, `InsightsTab.kt`) all use the identical `Modifier.align(Alignment.Center)` / `contentAlignment = Alignment.Center` pattern, with no theme-conditional branching anywhere in that code.

  *Branch: `fix/empty-state-left-alignment` · Complexity: S to fix, M to diagnose · Priority: Medium*

  **Plan** — needs a repro-and-diagnose pass before a fix: capture screenshots on device/emulator for Bright and Intense across all three locations, and check what's outside the three composables already read — parent `Scaffold`/`Surface`/`Card` wrapping, `LocalLayoutDirection`, or a theme-specific decoration (`CardDecorationStyle`/`GlowDecoration.kt`) that might apply an offset the static read wouldn't show. Confirm whether it reproduces in all three locations or just Big Picture before assuming it's the shared pattern.

  **Tests** — no existing test asserts empty-state horizontal position; once the cause is found, a Compose UI test asserting the text node's bounds are centered (or at minimum not flush against the left edge) for Bright/Intense would catch a regression.

## Case archive

- [ ] No way to clear the archive at once — `ArchivedCasesScreen.kt` only offers per-row Unarchive/Delete forever (`ArchivedCaseListItem`), no bulk action. The one existing bulk-delete primitive, `CaseDao.deleteAll()`, wipes every case app-wide (it backs Settings' "delete all data") and isn't scoped to archived-only.

  *Branch: `feat/clear-archive` · Complexity: S · Priority: Low*

  **Plan** — no multi-select UI needed: a single "Clear archive" button, reusing the existing per-row delete-forever confirm-dialog pattern (`ConfirmDialog` naming the count), that deletes every currently-archived case. Add a scoped DAO query to `CaseDao.kt` (e.g. `DELETE FROM cases WHERE archived = 1`), distinct from the existing unscoped `deleteAll()`, threaded through `HodithRepository` and `ArchivedCasesViewModel` as a new bulk method alongside the existing per-case `deleteForever(caseId)`.

  **Tests** — a DAO-level test that the new query only removes archived cases and leaves active ones untouched; an `ArchivedCasesViewModel` test for the new bulk action; confirm cascade deletes (events/tags/hunches/triggers via `ON DELETE CASCADE`) behave the same as the existing per-case delete path.

## Share

- [ ] Square format should become a fixed preset — Story stays the one fully customizable, auto-sizing format. Root cause: `shareCardState()` (`ShareCardState.kt`) applies `selectedSections` the same way to both formats, and `SharePreviewScreen.kt`'s `SectionsPicker`/`availableSections` render identical toggles for both. That's a real problem now that Square keeps a 1:1 floor while Story sizes freely to content (see `fix/dialog-spacing-icon-sharecard-sizing`'s commit 3): selecting every Insights section on Square produces a tall rectangle, undermining the format's purpose — Square exists for chat/feed contexts that expect a predictable square shape.

  *Branch: `feat/square-share-card-preset` · Complexity: M · Priority: Medium*

  **Plan** — needs a product decision first: which sections (and in what fixed order) Square always shows. Once decided: show `SectionsPicker` only when `ShareCardFormat.STORY` is selected in `SharePreviewScreen.kt`, and have `shareCardState()` source Square's sections from the fixed preset, independent of `selectedSections`.

  **Tests** — `ShareCardStateTest.kt` needs coverage that Square's output is driven by the preset; `SharePreviewScreenTest.kt` needs coverage that the section picker appears only for Story. `ShareCardTemplateTest.kt`'s Square floor/no-clip coverage should keep passing as-is, since the preset's fixed content is what it already exercises.

  **Concern** — this is as much a product decision as an implementation task, and it touches Voice (Story-only picker copy), so land it before the Voice phrasing audit.

## Settings

- [ ] **Audit the hosted privacy policy and Play data-safety form.** Both live outside this repo and likely still repeat the "nothing leaves the phone" claim that `feat/cloud-backup-toggle` just corrected in-app (About screen, README, HODITH_SPEC §16). The hosted policy is linked from `AboutScreen.kt`'s privacy section; the Play data-safety answers live in Play Console once a listing exists. Neither can be edited from this repo.

  *Branch: none — external content, not a code change · Complexity: XS · Priority: Medium*

  **Plan** — read both against the new About copy (HODITH itself sends nothing; Android's own device backup may include HODITH's data unless the user opts out via Settings) and update wherever they still claim otherwise.

- [ ] Rate the App is still a placeholder row (shows a "coming soon" snackbar) — needs a real destination once there's a Play Store listing.

  *Branch: `feat/rate-app-play-link` · Complexity: S · Priority: Blocked — do it in release prep*

  **Plan** — genuinely blocked on the listing existing, so it belongs in the release-prep branch rather than as standalone work. Two implementations: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review means adding a Google Play Services dependency to an app that currently ships none and whose whole positioning is "no network", which makes it a positioning decision rather than a technical one.

  **Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar, so that test changes rather than gets added to: assert the intent is launched (Espresso `Intents`). Note the row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`, which needs no change but shouldn't be mistaken for a second call site.

  **Concern** — In-App Review is quota-limited and no-ops silently once the quota is hit, which makes manual verification unreliable; the deep link is trivially verifiable. Another reason to prefer it.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
