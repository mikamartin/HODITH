# HODITH — Build Progress

Main development (Phases 0–11) is complete. That build history lives in [CLEANUP_LOG.md](CLEANUP_LOG.md) (per-branch, newest-first) and git log, not here — this file tracks what's left.

Each item carries a trailer: **Branch** (the branch to open for it), **Complexity** (S ≤ a day · M a few days · L a week-plus · XL a new module or multi-week — same scale as HODITH_SPEC §17), and **Priority** (High = gates the first release or corrects something wrong today · Medium = worth doing before alpha · Low = cosmetic or can wait). Then **Plan** (the main points), **Tests** (what coverage the change needs or breaks), and **Concern** where there is one.

## Recommended order

Sequencing matters more than usual here — several items collide in the same files, and two of them are cheap now and expensive after the first release.

1. **Data & migrations** (both items, one decision session) — they gate the ship checklist and price every HODITH_SPEC §17 item. They are also the only items on this list that get *more* expensive with every day of real user data.
2. **Auto-backup disclosure fix** — the app currently makes a privacy claim that isn't accurate. Small, and shouldn't wait behind the full toggle feature.
3. **First QA audit pass** (`chore/qa-audit`) — the Insights coverage gap below is exactly the kind of finding that audit produces; run it before patching that one gap in isolation, so sibling gaps surface in the same sweep.
4. Small fixes: Case Detail tab order, dialog spacing, close-action copy, app icon.
5. Share card sizing, then the cloud-backup toggle.
6. **Voice phrasing review last.** It is a mass edit across a 1945-line file; run it once, after every other copy-touching item on this list has landed, or it conflicts with all of them.

## Needs design / product-owner input

Items that need a design pass or a product decision before (or instead of) straight implementation.

- [ ] Review phrasing across all three Voice implementations (Plain/Intense/Bright) for quality and consistency.

  *Branch: `chore/voice-phrasing-audit` · Complexity: L · Priority: Medium*

  **Plan** — ~292 keys × 3 voices ≈ 876 strings. Not hard, but big, and it needs a human ear rather than a mechanical pass. Write the rubric first (what "consistent" means per voice: person, tense, sentence length, punctuation and emoji budget, and a locked vocabulary for Case/Hunch/Verdict/Event/Trigger), then audit in slices by screen rather than reading `Voice.kt` top to bottom — the file is grouped by key, so reading it linearly compares the wrong things. Produce a findings list first; fix in a second commit.

  **Tests** — `VoiceTest` today asserts only non-blank and the share-card pronoun rule. A copy audit is the right moment to add the mechanical invariants it can then lock in: vocabulary casing, no gamification vocabulary (streak/score/keep it up/missed — spec §4), length caps on tab and button labels, no double spaces or trailing whitespace. Instrumented tests reference `PlainVoice.x` by constant rather than by literal, so copy edits shouldn't break them — confirm that holds everywhere before starting (a grep for hardcoded UI literals in `androidTest`).

  **Concern** — the audit will change hundreds of lines in one file. Anything else touching `Voice.kt` must land first.

- [ ] Plain theme's light background/surface colors (`PlainLightBackground` #F4F6F8, `PlainLightSurfaceVariant` #DEE4E7, `PlainLightOnSurfaceVariant` #5B6670 in `Color.kt`) read as slightly blue-grey/murky rather than neutral. Revisit the palette.

  *Branch: `fix/plain-light-palette-neutrality` · Complexity: S to change, M to verify · Priority: Medium*

  **Plan** — decide the target first (true neutral grey vs. warm grey), keeping `primary` #3A6B76 as the only chroma in the scheme. Then note that the three named colors aren't the whole problem: `surfaceVariant` #DEE4E7, `surfaceContainerHigh` #E9ECEE, `outline` #D3DAE0 and `outlineVariant` #E7ECEE are all in the same blue-grey family and are *not* among the named/shared constants, so a fix that only touches the three named ones leaves the rest mismatched. Treat the light scheme as one change.

  **Tests** — there's no automated coverage of colour values and shouldn't be. Contrast is the one mechanically checkable property: add a JVM test asserting WCAG AA contrast for `onSurface`/`onSurfaceVariant` against `surface`/`background`, which is worth having independently of this item. Visual verification is the human's (Compose Previews per theme, plus the widget and a heatmap-bearing screen).

  **Concern** — two blast radii, both easy to miss. (1) The seven `PlainLight*` constants are consumed directly by `WidgetCommon.kt`'s `WidgetPalette`, which renders every Glance widget regardless of the user's in-app theme (DEV_PLAYBOOK §4), so changing them restyles the widgets too. (2) `HeatmapShading.kt`'s `toCellColor` lerps `surfaceVariant → primary`, so `surfaceVariant` is the base of the entire shading ramp — changing it moves every calendar-heatmap cell, rhythm grid cell, intensity cell, *and* their share-card mini-copies, in all three themes' light mode. Neither is a reason not to do it; both are reasons the review pass is wider than "the Plain theme's background".

## Data & migrations

Both need settling before the first release to real users — cheap decisions now, expensive ones once people hold data worth keeping. **Decide them together in one session**: while destructive migration is in place, export/import is the only route data has across a schema change, and import currently rejects any version mismatch, so both doors close at the same moment.

- [ ] Room migration policy is unresolved: `DatabaseModule` builds the database with `fallbackToDestructiveMigration(dropAllTables = true)` at schema v6, so every schema change wipes the user's data. Decide between real `Migration` objects with migration tests (`room.schemaLocation` is configured and the per-version `app/schemas/*.json` files are committed, so Room's `MigrationTestHelper` has everything it needs) and a consciously accepted, documented wipe for the pre-1.0 window. This decision also sets the price of every schema-touching item in HODITH_SPEC §17.

  *Branch: `feat/room-migrations` (real migrations) or `docs/migration-policy` (accepted wipe) · Complexity: decision S, implementation M · Priority: High — first on the list*

  **Plan** — the key simplification: v1–v5 never shipped, so no user holds that data and no retroactive 1→6 migration chain is needed. The whole requirement is *freeze the schema at the version that ships, then write real migrations forward from there*. That makes this decision cheap right now and expensive the day after release. Concretely: keep the destructive fallback for the pre-release window (documented, not implicit), and at the release-prep commit remove `fallbackToDestructiveMigration` and add the first `Migration` slot.

  **Tests** — `room-testing` is already an `androidTestImplementation` dependency and `app/schemas/1.json`–`6.json` are committed, so `MigrationTestHelper` needs no setup work. Land a guard test with whichever decision wins: assert the DB opens at the declared version on a fresh install, and add a check that fails when a new `schemas/*.json` appears without a matching `Migration` — otherwise a future schema bump lands silently green.

  **Concern** — `fallbackToDestructiveMigration(dropAllTables = true)` also wipes on a *downgrade*, not just an upgrade (a user sideloading an older APK, or a tester rolling back a track). If the accepted-wipe option wins, that belongs in the written statement, because it's the case people hit by accident.

- [ ] Backup import has no forward-compatibility path: `SettingsViewModel.performImport` rejects any file whose `schemaVersion` isn't exactly `BACKUP_SCHEMA_VERSION`. That's correct and tested today, but the first bump to 2 makes every file a user has already exported permanently unimportable — and while the destructive migration above is in place, export/import is the only route data has across a schema change, so both doors close at once. Decide between a version-tolerant import that upgrades older payloads and a stated limitation surfaced in the export copy.

  *Branch: `feat/backup-import-version-tolerance` · Complexity: M · Priority: High*

  **Plan** — the minimum viable version-tolerant shape is smaller than it looks: change the check from `!=` to `>` (reject only *newer* files, which genuinely can't be understood) and add one upgrade function per bump, applied in sequence to the parsed model. Since the export shape mirrors the tables one-for-one (§16), the first §17 item that adds a column is what trips this — which is why it's paired with the migration decision rather than deferred behind it.

  **Tests** — `SettingsViewModelTest` already covers exact-match rejection; extend with older-version-accepted, newer-version-rejected, and a round-trip through the upgrade chain. Once bumps start, keep one committed fixture JSON per historical version — a version-tolerant import with no old file to test against isn't tested.

  **Concern** — the version check isn't where this actually breaks. `BackupData` and the entities use Moshi codegen adapters, so an older JSON missing a field that is now non-nullable-without-default throws `JsonDataException` during `fromJson`, *before* `performImport` ever reads `schemaVersion`. Two consequences: (1) a version-tolerant path has to parse into a lenient shape (or give every new column a default) rather than reusing the strict adapter — that's the real work in this item; (2) today, an old file already reports `ImportFailureReason.INVALID` ("this file is broken") rather than `UNSUPPORTED_VERSION` ("this file is too old"), which is a misleading message the user will hit first. Worth fixing regardless of which option wins.

## Case Detail

- [ ] Reorder Case Detail tabs to Insights → Logs → Hunch (currently Logs → Insights → Hunch — `LOG_TAB`/`INSIGHTS_TAB`/`HUNCH_TAB` constants and the `Tab` declaration order in `CaseDetailScreen.kt`).

  *Branch: `feat/case-detail-tab-order` · Complexity: S · Priority: Medium*

  **Plan** — mechanically trivial: the three constants are `private` to `CaseDetailScreen.kt` with no usages anywhere else in the codebase, so renumbering them plus reordering the `Tab` declarations and the `when` branches is the whole change. The decision hiding inside it is the default landing tab: `selectedTab` initialises to `LOG_TAB`, and the retro-log FAB renders *only* on the Log tab. If reordering also changes what a Case opens on, the primary "add an event" action moves one tap further away, and a brand-new Case would land on the Insights tab's "not enough data" placeholder as its first impression. Recommend reordering the tabs but keeping Log as the initial selection — and if that's wrong, make it an explicit decision rather than a side effect of the constant renumbering.

  **Tests** — `CaseDetailScreenTest` and `CaseDetailInsightsTabTest` both select tabs by label text (`onNodeWithText(PlainVoice.caseDetailInsightsTabLabel).performClick()`), so they're order-agnostic and should pass unchanged. That's a good sign, but it also means nothing currently pins the default tab: **add a test asserting which tab is selected on open, and that the FAB is present there** — otherwise this change can silently alter the landing tab and the suite stays green. Small, and it's the one behaviour this item actually risks.

## Big Picture

- [ ] Cases/Tags filter dialog (`BigPictureGrid.kt`'s `InfoDialog` + `BulkSelectionToggle`) has a visually large gap between the dialog title and the Select all/Clear all row — it's Material3 `AlertDialog`'s default title→content spacing, not a custom Spacer HODITH added. `InfoDialog` is shared across every Big Picture info dialog (month/day/week detail too, not just Cases/Tags), so a fix needs to check it doesn't regress those.

  *Branch: `fix/big-picture-filter-dialog-spacing` · Complexity: S · Priority: Low*

  **Plan** — the gap is that default title→content spacing *plus* `BulkSelectionToggle`'s `TextButton`, which carries Material3's 48dp minimum touch height and its own content padding, so the whitespace stacks rather than being one culprit. That points the cheapest fix at the call site rather than the shared composable: change what the Cases/Tags dialogs put at the *top* of their content — move Select all/Clear all into the dialog's button row alongside Close, or into the title slot as a trailing action — instead of restyling `InfoDialog`'s padding.

  **Tests** — spacing isn't assertable and shouldn't be; this needs human visual verification. But `BigPictureScreenTest` drives dialog dismissal by text through thirteen call sites, so if the fix relocates the bulk toggle into the button row, those interactions need re-checking. Add a test that Select all/Clear all is still reachable and still functions from wherever it ends up.

  **Concern** — `InfoDialog` has five call sites in `BigPictureGrid.kt` plus `SectionWithInfo.kt` (the Case Edit info icons), so a change to the shared composable regresses screens this item never mentions. Prefer the call-site fix for that reason alone.

- [ ] Shorten the shared dialog-dismiss Voice string (`bigPictureDialogCloseAction`) to "Close" in all three voices — currently Plain already says "Close", Intense says "Seal it shut", Bright says "Got it, close this". Update all three in the same commit per the Voice layer rule.

  *Branch: `chore/voice-close-action-copy` · Complexity: XS · Priority: Low*

  **Plan** — three one-line edits. But finish the thought first: if all three voices say "Close", the key no longer varies by voice, and it exists solely to *override* `InfoDialog`'s `dismissLabel` default, which is `infoDialogDismissAction` ("Got it" / "Understood" / "Got it!"). At that point the honest change is to **delete `bigPictureDialogCloseAction` entirely and drop the five `dismissLabel =` overrides**, so Big Picture's dialogs use the shared default like every other `InfoDialog` — unless the intent is specifically that Big Picture's dialogs read flatter than the rest of the app, in which case keeping a voice-invariant key is a deliberate choice worth a comment. Either outcome is fine; keeping a duplicate key by accident isn't.

  **Tests** — `BigPictureScreenTest` references `PlainVoice.bigPictureDialogCloseAction` thirteen times. Shortening the string needs no test change (the constant is referenced, not the literal); deleting the key means swapping those to `PlainVoice.infoDialogDismissAction`. `VoiceTest`'s non-blank list also needs the line removed if the key goes.

## Share

- [ ] Share card doesn't shrink for sparse content: `ShareCardTemplate.kt`'s `heightIn(min = STORY_MIN_HEIGHT/SQUARE_MIN_HEIGHT)` enforces each format's minimum aspect-ratio height regardless of how many optional sections are selected; the `weight(1f)` wrapper around the header+sections column (meant to keep the footer pinned to the bottom instead of stranding it mid-card) just relocates the resulting slack to blank space above the footer rather than removing it. With most sections toggled off, that reads as a big empty gap. Needs a layout approach that lets the card shrink toward actual content height instead of the format's fixed minimum. Not only cosmetic: both deferred share items in HODITH_SPEC §17 (Big Picture sharing, animated story export) sit behind this, since each would inherit the same fixed-minimum behaviour.

  *Branch: `fix/share-card-sparse-content-height` · Complexity: M · Priority: Medium*

  **Plan** — prototype before writing production layout code (CLAUDE.md's "validate cheaply first"), because "shrink toward content height" may not be the right answer for Story. 9:16 *is* the story canvas; an image shorter than that gets letterboxed by the destination app anyway, so shrinking Story improves the in-app preview and changes nothing about the shared result. Three candidates, cheapest first, all testable in a Compose Preview: (a) drop the minimum entirely for both formats; (b) keep Story's ratio but distribute the slack — centre the content block vertically rather than pooling it above the footer; (c) keep the ratio and scale content up to fill when few sections are selected. Recommend evaluating (b) and (c) against (a) rather than assuming (a).

  **Tests** — the harness already exists: `ShareCardTemplateTest.storyIsTallerThanSquareForIdenticalContent` measures rendered heights in an instrumented test. Extend that pattern — a minimal-sections card measurably shorter than a maximal-sections card (if shrinking wins), the footer sitting at the bottom edge in both cases, and Square still respecting its floor if the floor is kept.

  **Concern** — whichever way it goes, the outcome is inherited by §17's Big Picture sharing and animated story export, so record the decision in the spec rather than only in the layout code. And confirm the chosen approach against the actual destination apps before committing to it — a fix that only looks better in HODITH's preview is a worse outcome than leaving it.

## Testing

- [ ] **Run the first QA audit pass.** [QA_AUDIT_RULES.md](QA_AUDIT_RULES.md) documents a whole-suite audit procedure and [QA_AUDIT_BACKLOG.md](QA_AUDIT_BACKLOG.md) is still its empty shell — no pass has run. The Insights gap below was found by hand and is precisely the shape of finding that audit is designed to produce systematically, which suggests there are siblings in other tabs that nobody has looked for.

  *Branch: `chore/qa-audit` · Complexity: M · Priority: Medium-High — before the item below*

  **Plan** — run the documented procedure as written (default mode: findings become proposed follow-up branches in the backlog, not inline fixes). Expect the Insights item below to come back as one of its findings, priced alongside whatever else it turns up; that's a better basis for deciding what to fix than patching one known gap in isolation.

  **Concern** — none, beyond the audit being a real time cost. It is the only item here that reduces uncertainty about the *rest* of the list.

- [ ] Insights instrumented coverage gap: pure-Kotlin `StatsEngineTest`/`InsightsEngineTest`/`InsightsTabStateTest` (54 tests) already assert exact computed values for every Insights metric, but the instrumented `CaseDetailInsightsTabTest` (13 tests) is almost entirely card visibility/gating (`assertExists`/`assertDoesNotExist`) — only 2 tests read actual rendered text (the trend sentence, the gap-shift note). No instrumented test reads the Duration/Intensity/Frequency cards' displayed numbers off the real UI against known seeded data, so a wiring/formatting bug between correct state and the rendered `Text` wouldn't be caught by anything today.

  *Branch: `test/insights-tab-rendered-values` · Complexity: M · Priority: Medium-High*

  **Plan** — target the cards that format numbers, since that's where the untested gap between correct state and rendered text lives: Duration (`formatMinutesDuration`), Intensity, Frequency (`formatRate`), and the Gaps/Streaks values. Seed known data, assert the exact displayed string. Roughly six to eight new tests on top of the existing thirteen.

  **Tests** — the trap to avoid is the one that makes this cheap and worthless: a test that builds its expected string by calling the same formatter the UI calls is a tautology that passes through any formatter change. At least the value-formatting assertions must hardcode the expected literal ("1h 30m", "2.5 / week") so a formatting regression actually fails. Use the `PlainVoice.x(...)` constant only where the sentence *around* the number is what's being pinned.

  **Concern** — `CaseDetailInsightsTabTest`'s class comment currently justifies the gap ("the underlying math is already covered exhaustively... on the JVM"). That reasoning is what produced the hole — correct math plus a wiring bug still ships wrong numbers — so the comment has to be corrected in the same commit, or the gap grows back.

- [ ] `VoiceTest`'s completeness check is hand-maintained and rots silently. `every voice has a non-blank string for every key` is roughly 250 individually written `assertTrue(voice.someKey.isNotBlank())` lines. A new Voice key added without a matching line is simply uncovered, and nothing fails — which makes the test look like a guarantee it isn't. Replace the manual list with reflection over the `Voice` interface's properties (handling the parameterised `fun` keys separately), so every key is covered by construction.

  *Branch: `test/voice-completeness-by-reflection` · Complexity: S · Priority: Medium*

  **Rationale for adding it** — surfaced while sizing the Voice phrasing review above. The Voice layer's one hard rule (CLAUDE.md: every string in all three voices, same commit) is currently enforced only by the compiler for *existence* and by a manually maintained list for *content*. The compiler half is solid; the manual half is the one that silently degrades, and it degrades fastest exactly during a large copy pass — so this is worth landing *before* the phrasing audit, not after.

## Settings

- [ ] **Correct the auto-backup disclosure.** Independent of the toggle below, the app currently says something that isn't accurate. About's privacy copy reads "Everything stays on your phone. HODITH has no network access and sends nothing anywhere" (Plain), "Nothing. No network, no signal sent outward — every case stays sealed here" (Intense), "Everything stays right here on your phone — no internet, no sneaky data stuff!" (Bright), and README says "no cloud". With `android:allowBackup="true"` and an unrestricted `data_extraction_rules.xml` (`<cloud-backup />` + `<device-transfer />` with no exclusions), the Room database *does* reach the user's Google account whenever device backup is enabled. Separately, HODITH_SPEC §16 states "Android auto-backup enabled — documented on the About screen" and the About screen documents no such thing — an unintentional spec/implementation divergence, which CLAUDE.md classifies as a bug to fix rather than a spec update.

  *Branch: `fix/about-backup-disclosure` · Complexity: S · Priority: High — ahead of the toggle*

  **Rationale for splitting it out** — the toggle below is an M-sized feature with open product questions. The inaccurate claim shouldn't sit in the app while those get answered, and a copy correction is independent of whichever way the toggle decision goes.

  **Plan** — rewrite `aboutPrivacyBody` in all three voices to say plainly that HODITH itself sends nothing (still true: no INTERNET permission) *and* that Android's own device backup may include HODITH's data if the user has it on, with how to check. Also audit the two claims that live outside this repo: the hosted privacy policy at the URL `AboutScreen.kt` links to, and the Play data-safety answers, both of which probably repeat the same claim.

  **Tests** — `AboutScreenTest` asserts section content; extend it for the new disclosure. `VoiceTest`'s non-blank list already covers the keys.

- [ ] Add a Settings toggle for Android's OS-level cloud backup: HODITH has no INTERNET permission and doesn't sync anything itself, but `allowBackup="true"` plus an unrestricted `data_extraction_rules.xml` mean the OS's own device backup (if the user has "Back up to Google One" on) currently includes HODITH's local database like any other app's. EarnIt already implements this pattern (a custom `BackupAgent` honoring a persisted toggle, since Android has no runtime API for an app to flip its own default Auto Backup on/off) — review that implementation as a reference. Open sub-decisions: default toggle state, and whether device-transfer (new-phone setup) is gated by the same toggle or always allowed independently of cloud backup. Update About/README once resolved to state plainly whether HODITH data can reach the user's Google account and how to prevent it.

  *Branch: `feat/cloud-backup-toggle` · Complexity: M · Priority: Medium (High if the disclosure fix above is skipped)*

  **Plan** — the toggle value slots into the existing `DataStoreSettingsRepository` alongside theme and default check-in interval, with a `BackupAgent` reading it; the Settings row goes in the Data plank next to Export/Import/Delete. The two open sub-decisions are the actual work: **default state** (defaulting off contradicts §16's "auto-backup enabled" and silently loses new-phone restore; defaulting on contradicts the app's current framing) and **device-transfer** (`<device-transfer />` is the new-phone path and is arguably a different question from `<cloud-backup />` — data moving phone-to-phone doesn't linger in a Google account the way a cloud backup does, so gating them together may be stricter than users want).

  **Tests** — the toggle's persistence and the ViewModel wiring are testable exactly like the existing theme/check-in preferences (`DataStoreSettingsRepository`, `SettingsViewModelTest`, `SettingsScreenTest`). `BackupAgent` behaviour is not practically unit-testable; verification is `adb shell bmgr` (enable, `backupnow`, wipe, restore) — document that procedure in DEV_PLAYBOOK the way the widget `grantbind` procedure is documented, and add the journey to MANUAL_TEST_PLAN.

  **Concern** — EarnIt is a different repository, so "review that implementation as a reference" is a real time cost, not a free lookup; budget for it. And whichever default wins, five things move together: HODITH_SPEC §16, the About copy, README, the hosted privacy policy, and the Play data-safety form. Missing one of the two off-repo ones is the likely failure.

- [ ] Rate the App is still a placeholder row (shows a "coming soon" snackbar) — needs a real destination once there's a Play Store listing.

  *Branch: `feat/rate-app-play-link` · Complexity: S · Priority: Blocked — do it in release prep*

  **Plan** — genuinely blocked on the listing existing, so it belongs in the release-prep branch rather than as standalone work. Two implementations: a `market://details?id=…` intent with an `https://play.google.com/…` fallback, or the Play In-App Review API. Recommend the deep link — In-App Review means adding a Google Play Services dependency to an app that currently ships none and whose whole positioning is "no network", which makes it a positioning decision rather than a technical one.

  **Tests** — `SettingsScreenTest` currently asserts the coming-soon snackbar, so that test changes rather than gets added to: assert the intent is launched (Espresso `Intents`). Note the row also appears in `SettingsScreen.kt`'s Bright plank Preview with a no-op `onClick`, which needs no change but shouldn't be mistaken for a second call site.

  **Concern** — In-App Review is quota-limited and no-ops silently once the quota is hit, which makes manual verification unreliable; the deep link is trivially verifiable. Another reason to prefer it.

## App icon

- [ ] Dark spot visible on the circle/handle at larger icon sizes — the invisible lens ring fix addressed a related issue, but this artifact remains at bigger resolutions.

  *Branch: `fix/launcher-icon-handle-overlap` · Complexity: S · Priority: Low*

  **Plan** — the vector gives a concrete diagnosis. In `ic_launcher_foreground.xml` the handle (`M62,62 L74,74`, `strokeWidth 9`, `strokeAlpha 0.75`) is drawn before the ring, and the ring stroke (`strokeWidth 6.5`, `strokeAlpha 0.7`) spans radius ~15.75–22.25 around centre (48,48) — i.e. roughly 59.1 to 63.7 along the 45° diagonal. The handle's first ~1.7 units start *inside* that band, so two translucent dark strokes composite into a darker patch exactly where the handle meets the ring. It only reads as a spot at large sizes because at launcher resolution it's sub-pixel. Three fixes, cheapest first: (a) start the handle outside the ring's outer edge (`M64.5,64.5`); (b) draw the handle *after* the ring at full opacity so it occludes rather than blends; (c) make both strokes fully opaque in a slightly lighter tone so overlap can't darken.

  **Tests** — nothing automatable. Verification is rendering the vector at several sizes (Compose Preview or the asset preview) and a human looking at it.

  **Concern** — the same glyph exists in three places and they have to stay consistent. `ic_launcher_monochrome.xml` repeats the identical geometry at full-opacity white (so it has no dark spot today, but inherits any geometry change — its comment already commits to matching), and `ic_launcher_foreground.xml` is reused directly as the splash icon via `Theme.Hodith.Splash`, which is a third size to check.

---

Each significant change ends with a CLEANUP_CHECKLIST.md pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
