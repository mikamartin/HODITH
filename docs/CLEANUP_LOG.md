# HODITH — Cleanup Log

A record of every cleanup pass, newest first (ordering, not dating, marks recency — see CLAUDE.md's no-dates rule). After any significant feature work, copy the checklist from [DEV_PLAYBOOK.md](DEV_PLAYBOOK.md) §1 into a new entry above the previous one, tick what was found and fixed, and note anything deferred with a reason.

## Entry format

```
## <branch or feature name>

**Scope:** what work triggered this pass
**Found & fixed:** bullet list (or "nothing found" — that's a valid result)
**Deferred:** bullet list with reasons (or "nothing deferred")
**Docs updated:** SPEC / TESTING / PLAYBOOK sections touched, if any
```

---

## feature/log-detail-sheet (Phase 2, slice 3, PR 2 of 6)

**Scope:** Shared `LogDetailSheet` bottom sheet (time via date/time pickers, intensity, MANUAL duration, note, tag chips with autocomplete), wired into Case Detail's retro-log FAB and tap-to-edit. New `EventWithTags` (Room `@Relation`/`Junction`) so the event list shows each event's own tags without a separate lookup; debug seed data expanded with realistic notes/tags per case; event list rows now show weekday + date (year omitted when current) + time.
**Found & fixed:**
- **Complexity — oversized composable:** `LogDetailSheet`'s body was ~208 lines, over the ~150-line guideline. Split into `SheetHeader`, `TimeSection`, `IntensitySection`, and three dialog composables (`LogDetailDatePickerDialog`, `LogDetailTimePickerDialog`, `DeleteEventConfirmDialog`); the main function is now state + a flat list of section calls.
- **Efficiency — redundant clock reads:** `CaseDetailScreen` called `nowMillis()` three separate times per composition (FAB, each row, each row's edit request). Computed once at the top and reused.
- **Dead code:** `HodithRepository.observeEventsForCase` had zero production callers left once `CaseDetailViewModel` switched to `observeEventsWithTagsForCase` (a strict superset). Removed the repository wrapper; kept the underlying `EventDao.observeEventsForCase` since `EventDaoTest`/`CaseDaoTest` still exercise it directly as a DAO-level primitive.
- **Accessibility — tap target size:** `IntensityChoice` circles were 40dp, under the 48dp minimum (same class of issue as `feature/case-crud`'s icon-picker cells below). Bumped to 48dp via a named `INTENSITY_CHOICE_SIZE` constant, matching `CaseEditScreen`'s `ICON_CHOICE_SIZE` precedent.
- **Compose layout bug (user-reported):** the tag input field chained both `.fillMaxWidth()` and `.weight(1f)` — an anti-pattern that gives the field ambiguous width constraints across measurement passes. This was the root cause of two reported symptoms: typed text not appearing until the field lost focus, and the field's height changing on blur. Fixed by dropping the redundant `fillMaxWidth()`.
- **Keyboard-covers-field bug (user-reported):** the sheet's content `Column` had no `imePadding()` and wasn't scrollable, so fields near the bottom (tags, note) sat behind the keyboard once it opened. Added `.verticalScroll(rememberScrollState())` and `.imePadding()`.
- **Data-integrity bug (user-reported):** saving an edited event under a duration mode with no editable duration control yet (`NONE`/`START_STOP`) silently nulled the event's existing `endedAt`, permanently destroying a real duration logged while the case was still `START_STOP`. Fixed by threading `existingEndedAt` through `LogDraft`; only `MANUAL` mode's control can actually change it now.
- **Missing validation (user-reported):** nothing stopped saving an event with a future `occurredAt`. Fixed with `DatePicker`'s `selectableDates`, a same-day time clamp after the time picker, and an authoritative `coerceAtMost(now)` at save time. `endedAt` from a MANUAL duration is deliberately *not* clamped — a stated/expected duration at logging time ("started now, runs about 2 hours") is legitimate even if it projects past `now`.
- **UX (user-requested):** event delete moved from a per-row icon+dialog in the Case Detail event list to the log sheet's header (top-right, edit-mode only) — keeps the list scannable; delete now lives where you're already looking at the one event.
- **Ordering fix (user-requested):** weekday moved to after the date/time in the event-row timestamp, with a positional regression test (`indexOf` check) so this can't silently flip back.
**Deferred:**
- No Compose UI instrumented test drives the log sheet end-to-end yet (save with retro time, tag add/remove, delete-from-sheet). Covered so far by unit tests for all the pure logic and instrumented DAO tests for the new relation query, but no Compose UI test exists anywhere in the repo yet — deferred until `feature/quick-log` or `feature/start-stop` stands up that pattern for the first time. Logged in TESTING.md's Deferrals section.
- `LogDetailSheet`'s in-progress state (`draft`, `tagInput`, picker visibility) uses plain `remember`, not `rememberSaveable` — won't survive a rotation or process death mid-edit. `LogDraft` isn't trivially Parcelable and this is a quick bottom-sheet action, not a big form investment (contrast with `CaseEditViewModel`'s `SavedStateHandle`-backed state). Accepted for now; revisit if it causes real friction.
- Tags-and-weekday-always-on (no per-user Settings toggle) — deliberately not built now to avoid standing up DataStore/Settings-screen infrastructure early for a display preference that may not need to be optional. Flagged in PROGRESS.md and HODITH_SPEC.md §6 for reevaluation once Settings (Phase 9) gets real content.
- Two more Case Edit screen gaps surfaced during manual testing, out of scope for this branch (different screen, already-merged `feature/case-crud`): Case removal (no archive/delete UI at all) and a batch of form UX fixes (collapsible icon picker, info icons, Check-in restyle, One-Tap `logFlow` option needs disabling when duration is MANUAL and/or intensity is tracked). Both recorded as new planned branches in PROGRESS.md (`feature/case-archive`, `feature/case-edit-polish`) rather than bolted on here.
**Docs updated:** HODITH_SPEC.md (§6 — event list now describes what each row shows, flagged for the Settings-toggle reevaluation). PROGRESS.md (slice-3 sub-branch breakdown updated to reflect the actual 4→6 branch split, two new branches added, tags/weekday note). TESTING.md (Deferrals section — Compose UI gap for the log sheet).

---

## feature/case-crud (Phase 2, slice 2 of 3)

**Scope:** New/Edit Case screen (§14 core fields: name, optional description, icon picker, logFlow, durationMode, intensity toggle, pinned toggle, check-in override), `CaseEntity.description` field + schema bump to v2, Home's FAB (create) and row-tap (edit) wired to it. Skippable Hunch step deliberately out of scope — see Deferred.
**Found & fixed:**
- **Duplication:** the logFlow and durationMode pickers were two near-identical inline `SingleChoiceSegmentedButtonRow` blocks differing only in the enum type and options. Extracted a shared generic `SegmentedChoiceRow<T>` composable.
- **Accessibility — icon-only button:** the screen's back `IconButton` had `contentDescription = null`. That null pattern is correct for the bottom-nav items (precedent in `feature/home-screen`'s entry below) because their visible label already names them — but this back button has no adjacent label, so `null` would leave it unannounced to TalkBack. Added a new `backButtonDescription` Voice key and wired it in.
- **Accessibility — selection conveyed by color alone:** the icon-picker grid and the check-in radio rows only showed selection via a background-color change, invisible to TalkBack. Switched both to `Modifier.selectable(selected, onClick, role = Role.RadioButton)` on the row/cell plus `Modifier.selectableGroup()` on their containers, so selected state is announced. `RadioButton`'s own `onClick` set to `null` (the recommended pattern) to avoid a duplicate, differently-scoped click target on top of the row's.
- **Tap target size:** the icon-picker cells were 44dp, under the 48dp minimum. Bumped to 48dp.
- **Test gap:** `checkInDaysFor` (DEFAULT/CUSTOM/OFF → nullable day count mapping) had no coverage. Made `internal` and added five cases including blank and zero custom-input fallback.
- **Deprecation:** `fallbackToDestructiveMigration()` is deprecated in this Room version; switched to the `dropAllTables = true` overload.
- **Instrumented tests verified:** `CaseDaoTest`'s new `description` round-trip case, plus the full `connectedDebugAndroidTest` and `test` suites, run and passed on a physical device — closes the gap flagged mid-review (DEV_PLAYBOOK §1's "ran on a device" check is separate from "compiles").
**Deferred:**
- **Hunch step:** §14 describes the New/Edit Case flow ending in a skippable Hunch step. Moved to its own follow-up branch — Hunch creation is new surface area (direction/expectedCount/expectedPer) beyond Case CRUD's core fields, and splitting it keeps this PR reviewable. Recorded in PROGRESS.md.
- **Row-tap → Edit Case is interim wiring**, not the final design: Case Detail doesn't exist until `feature/logging-flows`. Documented in PROGRESS.md with the intended end state (row-tap → Case Detail, Edit moves to its header, Home gets its own quick-log button per row).
- **Check-in "Custom" with a blank/zero day count** silently falls back to the same result as "Use default" rather than blocking save with a validation error. Deliberate simplification, not a tracked gap — revisit only if it causes real confusion.
**Docs updated:** PROGRESS.md (slice 2 scope corrected to reflect the Hunch-step deferral, new "Interim UX wiring" note). HODITH_SPEC.md not touched — the target screen design is unchanged, only the build sequencing differs.

---

## feature/home-screen (Phase 2, slice 1 of 3)

**Scope:** First Phase 2 slice — Navigation Compose scaffold (bottom nav: Home · Big Picture · Settings), read-only Home screen wired to real `HodithRepository` data via a new `HomeViewModel`, placeholder Big Picture/Settings destinations, new `Voice` keys, `description` field added to the Case spec (§5, §14) after a product discussion ruled out a separate Case-level valence field.
**Found & fixed:**
- **Duplication:** `BigPictureScreen.kt` and `SettingsScreen.kt` were identical placeholder bodies (Box + centered Text reading `comingSoonPlaceholder`). Extracted a shared `ComingSoonPlaceholder` composable (`ui/common/ComingSoonPlaceholder.kt`); both screens now delegate to it.
- **Accessibility:** `NavigationBarItem`'s `Icon` had `contentDescription` set to the same text as its visible `label`. Since the label is already shown (not an icon-only button), that would make TalkBack announce the name twice. Set `contentDescription = null` with a comment explaining why, per Material accessibility guidance for icon+label nav items.
- **Test gap (caught by a question, not the checklist pass):** `HomeViewModel`'s today/this-week boundary math (`startOfToday`/`startOfWeek` cutoffs) had no unit coverage. Extracted it to a pure top-level `homeCaseRows(casesWithEvents, nowMillis)` function and added `HomeViewModelMappingTest` covering the inclusive/exclusive boundary at both cutoffs, an empty-events case, and identity-field mapping.
- **Dependency pin:** `hilt-navigation-compose` 1.4.0 (and its transitive `androidx.lifecycle` bump) requires `compileSdk 37`, not installed and out of scope for this branch. Pinned to 1.2.0 instead; recorded in PROGRESS.md as a decision to revisit alongside a deliberate SDK bump, not silently.
**Deferred:**
- `HomeViewModel` computes today/this-week counts by filtering each Case's full in-memory event list (from `observeActiveCasesWithEvents()`) rather than using `EventDao.eventsInWindow`'s SQL-side windowing — a literal read of DEV_PLAYBOOK §1's "Dao query duplicated with a Kotlin-side filter" check. Deferred: `eventsInWindow` is a one-shot suspend query, not reactive, and per-case event volumes are small (personal habit-tracking data), so pushing this into SQL isn't worth the added complexity yet. Revisit if event counts per Case grow large enough for this to matter.
- No instrumented Compose UI test added for Home's rendering. TESTING.md's planned Compose UI coverage for Home is bundled with Create Case / one-tap-log / detail-sheet interactions (slices 2–3); writing one now against a read-only screen with no `onClick` handlers risks being rewritten wholesale once those land. Revisit once slice 2 or 3 adds interaction.
**Docs updated:** HODITH_SPEC.md (§5 `description` field, §14 New/Edit Case field list). PROGRESS.md (slice 1 marked done, data-model decision on Case valence recorded, `hilt-navigation-compose` pin documented).

---

## feature/big-picture (Phase 2/3 rescope)

**Scope:** Re-scoping pass after the calendar-grid pivot: swapped Case CRUD and Big Picture phase order (pinch-zoom's de-risking rationale for building Big Picture first no longer applies), deleted the retired row/dot Big Picture screen and its domain math rather than holding it for reuse, and reconciled every doc that still referenced the old design or the old phase numbers.
**Found & fixed:**
- **Dead code:** all row/dot production code and tests deleted — `domain/timeline/{TimelineLayout,TimelineWindowMath,TimelineAxis}.kt`, `ui/timeline/{TimelineGestures,TimelineScreen,TimelineViewModel}.kt`, and their unit/instrumented tests (~1,067 lines). Previously flagged as "candidates for reuse" for Phase 6's per-case dot timeline; resolved as delete-not-hold since it was shaped around a multi-case shared-row layout, so reuse for a single-case screen four phases out was speculative.
- **Decoupling fallout from that deletion:** `Voice.kt`'s `timeRangeLabel(ZoomLevel)` removed (no zoom levels left to label), `VoiceTest.kt` updated to match; `SeedDataInitializer.kt`'s `MIN_INTENSITY`/`MAX_INTENSITY` re-homed as local private constants (borrowed from the deleted `TimelineLayout.kt`, not conceptually tied to it); `MainActivity.kt` no longer wires the deleted `TimelineGrid`/`TimelineViewModel`.
- **Stale docs:** DEV_PLAYBOOK.md's Ship Checklist referenced "the Phase 2 seed-data mechanism" — that's Phase 3's now; reworded without a phase number so it can't rot the same way again. This file's own Phase 1 entry referenced "Phase 3's job" for ViewModel concerns that are now Phase 2's. TESTING.md's Big Picture coverage rows still described dot/bar bucketing, pinch-zoom responsiveness, and "tap a dot" navigation from the retired design — rewritten for the calendar grid's actual day/week/month/filter-chip interactions. HODITH_SPEC.md §9's closing note pointed at "domain-layer code that may still be reusable" — that code no longer exists; note corrected.
**Deferred:**
- `MainActivity.kt`'s temporary placeholder Compose text ("HODITH") is an inline string, not routed through the `Voice` layer — normally a hard violation of the #1 hygiene rule, but this is throwaway scaffolding pending Phase 2's Home screen, not shipping product copy. Not worth plumbing a `Voice` key for text that gets deleted within the next phase.
- `gradle/gradle-daemon-jvm.properties` sits untracked in the working tree, unrelated to this branch's work — flagged for awareness, not resolved here.
**Docs updated:** PROGRESS.md (phase order swap, current status, resolved open decisions), TESTING.md (Big Picture coverage rows), DEV_PLAYBOOK.md (Ship Checklist wording), HODITH_SPEC.md (§9 closing note), this file (Phase 1 entry's stale phase reference).

---

## feature/room-data-layer (Phase 1)

**Scope:** Room entities/DAOs/`HodithRepository`, JVM `Clock` abstraction, and their tests — first product code in the repo.
**Found & fixed:**
- Migrated `build.gradle.kts` files from inline dependency versions to `gradle/libs.versions.toml`, since this phase roughly doubled the dependency count (Room, coroutines, AndroidX test, Hilt testing).
- All six instrumented DAO test classes repeated the same `Room.inMemoryDatabaseBuilder(...).build()` boilerplate in `@Before` — extracted to a shared `createInMemoryDatabase()` helper in `TestFixtures.kt`.
- Enums stored as `String` (not ordinal `Int`) via `Converters.kt` — safer against future reordering/insertion of enum values.
- Tag cross-ref inserts use `OnConflictStrategy.IGNORE` so `HodithRepository.addTagToEvent` is idempotent.
**Deferred:**
- No domain logic beyond `Clock` exists yet (verdict/trigger/stats engines are Phase 5+), so most of DEV_PLAYBOOK §1's Decoupling/Complexity checks don't yet apply — revisit at that phase.
- `HodithRepository` mirrors the DAOs closely with no additional orchestration beyond tag find-or-create; ViewModel-level concerns (undo window, one-ongoing-per-case invariant) are explicitly Case CRUD's job per TESTING.md.
**Docs updated:** TESTING.md — status line updated (Phase 1 landed), known-environment-issues note replaced with the actual verification result (25/25 instrumented DAO tests passed on a Pixel 6 AVD, API 34; the Android 16/API 36 compatibility gap flagged at Phase 0 remains unverified since no API 36 image was used). PROGRESS.md — Phase 1 checked off.

---

## chore/project-scaffold (Phase 0)

**Scope:** Repo init and Gradle/Android project scaffold — no product code yet, just the empty app skeleton and tooling.
**Found & fixed:** nothing to fix; no product logic exists yet for most checklist categories to apply to (duplication, decoupling, complexity, accessibility are all N/A pre-feature-code). Confirmed `git status` clean, `.gitignore` correctly excludes `local.properties`/`build/`/`.gradle/`, no secret-shaped files staged.
**Deferred:**
- Dependency versions are inline literals in `build.gradle.kts` rather than a Gradle version catalog (`libs.versions.toml`). Matches DEV_PLAYBOOK §5's matrix directly for now; revisit once the module/dependency count grows enough that duplication becomes a real problem.
- Instrumented-test environment (the documented Android 16 compatibility gap in TESTING.md) is still unverified — Phase 0 only exercised the JVM `test` task (no source, ran clean) and `ktlintCheck`; no emulator was used. Verification happens when Phase 1 lands the first DAO tests.
**Docs updated:** DEV_PLAYBOOK.md §5 tooling matrix updated in place with verified versions (AGP 9.2.1, Hilt 2.60, Compose BOM 2026.06.01, Kotlin held at 2.3.20 pending KSP support, Room decision recorded). HODITH_SPEC.md and TESTING.md reviewed — no divergence, no changes needed (no product or test code landed this phase).
