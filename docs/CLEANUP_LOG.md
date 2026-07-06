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
