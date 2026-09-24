# HODITH — Post-Work Cleanup Checklist

Run after any significant feature work or refactor (see [DEV_PLAYBOOK.md](DEV_PLAYBOOK.md) §1). Walk every applicable item below against the real diff, then log the pass in [CLEANUP_LOG.md](CLEANUP_LOG.md): what was found and fixed, what was deferred and why, and which sections didn't apply.

**Deferring a finding requires an explicit call.** Fix now, defer, or decline — made and recorded, not assumed. An item parked without that call is unfinished, not deferred. Anything deferred that's worth revisiting later also goes in [PROGRESS.md](PROGRESS.md). A finding considered and rejected is written as "considered and declined" with the reason, never as "deferred".

### Duplication
- [ ] Are any composables copy-pasted with minor variation? Extract a shared component or parameter.
- [ ] Are any styling patterns (colors, gradients, padding sequences) repeated inline instead of using the design system / theme?
- [ ] **Are any user-visible strings inline in composables instead of going through the `Voice` layer?** (HODITH's #1 hygiene rule — an inline string ships in one voice only.)
- [ ] Does any ViewModel logic appear in more than one place?
- [ ] Does any new `Repository` function overlap with an existing one that could be parameterised instead?
- [ ] Does any new `Dao` query duplicate an existing query with a Kotlin-side filter that could be pushed into SQL?
- [ ] Does a new Trends detector implement its own significance test instead of reusing the shared permutation engine (§10)?

### Decoupling
- [ ] Do composables contain business logic that belongs in the ViewModel or Repository?
- [ ] **Is any time-dependent logic calling `System.currentTimeMillis()` directly instead of the injected `Clock`?** (Breaks verdict/trigger/stats testability.)
- [ ] Does new day-bucketing or elapsed-time logic use the event's captured `utcOffsetMinutes` rather than the device's current offset (§5/§9)?
- [ ] **Does verdict/trigger/stats code import anything from `android.*`?** These modules stay pure Kotlin.
- [ ] Does the ViewModel directly reference UI types (Color, Dp, Composable functions)?
- [ ] Does the data layer reference ViewModel or UI concerns?
- [ ] Are new screens receiving the full ViewModel when they only need a subset? Pass specific lambdas or state instead.

### Complexity & Pattern Health
- [ ] Composables over ~150 lines that could be split into focused sub-composables?
- [ ] Deeply nested lambdas or modifier chains that are hard to follow?
- [ ] Are `LaunchedEffect` keys correct — re-trigger exactly when needed and no more?
- [ ] Is `remember` vs `rememberSaveable` correct for each piece of state?
- [ ] Coroutine scopes (`rememberCoroutineScope`, `viewModelScope`) in the right layer?
- [ ] Do new buttons/dialogs/rows reuse established components rather than reimplementing?
- [ ] Does any new composable reimplement something M3 already provides?
- [ ] Single-caller helpers: is the extraction earning its keep?

### Dead Code & Hygiene
- [ ] Unused imports, variables, parameters, functions? (Check IDE warnings.)
- [ ] Commented-out code blocks to delete?
- [ ] Declared-but-unreferenced resources?
- [ ] Resolved TODO/FIXME comments?
- [ ] Test/debug helpers (seed data, logging) still present that are marked for pre-release removal?
- [ ] **Throwaway prototype served its purpose and been cleared out?** A spike test, concept-mockup HTML, or scratch Compose Preview built to de-risk this work — once the real implementation and its tests have landed, delete it (or, for a mockup genuinely worth keeping as design history, commit it to `docs/mockups/` deliberately rather than leaving it untracked). A spike whose own KDoc says "expected to be deleted" isn't done until it's gone. Check `git status` for untracked prototype files as well as tracked ones. This also applies to already-committed `docs/mockups/` files when *this* diff is what ships or stabilises the feature one referenced — re-decide whether it's still a live reference (comments point at it, or open PROGRESS.md work needs it) or now a stale snapshot. Before deleting one, `git grep` every mention form, not just the file path: `"<name> mockup"` phrasing and the `.css-class` / line-number anchors comments accrete over time won't show up in a path search, and each must be rewritten or dropped first.
- [ ] **Docs or comments touched by this diff carrying stale info, content duplicated elsewhere, or a self-updating tally (test/instance counts, "Updated, feature/X" logs, branch-name callouts) that will need babysitting on every future change?** Prefer a durable fact or a pointer to the authoritative source (e.g. PROGRESS.md for build status) over content that needs maintaining forever.
- [ ] **Do the current-state docs touched by this diff (HODITH_SPEC.md, TESTING.md, DEV_PLAYBOOK.md, CLAUDE.md, README) narrate what used to be true, what was removed, or how something changed, instead of just stating what's true now?** That history belongs in CLEANUP_LOG.md (explicitly historical, newest-first) or the commit/PR itself — trim it from living docs so they read as a snapshot of the present, not a changelog. (Doesn't apply to CLEANUP_LOG.md itself or PROGRESS.md's Phase order section, which are intentionally historical.)

### Repo Hygiene (public repo — see CLAUDE.md "Git hygiene")
- [ ] Is `git status` clean — no stray untracked files that should be gitignored, nothing accidentally staged?
- [ ] Does the staged diff contain anything secret-shaped (keys, tokens, passwords, keystore files, `keystore.properties`)?
- [ ] Did `local.properties`, `.idea/` files, `*.iml`, build output, or OS junk sneak past `.gitignore`? If yes, fix `.gitignore`, don't just unstage.
- [ ] Any real local paths (`C:\Users\...`) or personal info in code, docs, scripts, seed data, or committed screenshots?
- [ ] New tooling/config files: do they belong in the repo (shared) or in `.gitignore` (local setup)?

### Naming Consistency
- [ ] New files follow `*Screen.kt`, `*ViewModel.kt`, `*Repository.kt` patterns and sit in the right package (`data/`, `di/`, `domain/` (verdict/trigger/stats engines), `ui/`, `viewmodel/`, `widget/`)?
- [ ] New composables PascalCase, descriptive, no abbreviations?
- [ ] New `Voice` keys named consistently and added to **all three** voices in the same commit?

### Hardcoded Values
- [ ] New colors hardcoded as `Color(0xFF...)` where a theme value should be used?
- [ ] Magic numbers (verdict thresholds, confidence tiers, nudge count) inline where a named constant in the domain layer would be clearer? These are product constants — they live in one place.

### Accessibility
- [ ] Icon-only buttons have non-empty `contentDescription`?
- [ ] All tappable targets ≥ 48 dp × 48 dp?
- [ ] Heatmap/chart cells convey information by more than color alone (value on tap / content descriptions)?
- [ ] New UI verified in both light and dark mode for the themes it appears in, not just the default?

### Data Model, Migrations & Privacy
- [ ] New entity or column added? Room migration, `BACKUP_SCHEMA_VERSION` bump, and import validation all updated together (§17's "three changes, not one")?
- [ ] Schema version bumped without a matching Room `Migration`? (`SchemaMigrationCoverageTest` should fail rather than falling back to a destructive migration.)
- [ ] Export/import (JSON) shape and referential-integrity validation still mirror the current schema?
- [ ] FK cascade-delete relationships (Case → Event/Hunch/Trigger) still correct after schema changes?
- [ ] Share card still excludes notes and tags (§13) — no new field reaches it without deliberately updating that exclusion?

### Background Work, Widgets & Notifications
- [ ] Trigger/check-in evaluation still debounced and idempotent on repeated runs (a logging burst, the ~6h WorkManager pass)?
- [ ] Notifications still join the single HODITH group with summary-only alerting, not one alert per Case?
- [ ] `POST_NOTIFICATIONS` still requested contextually (first trigger created / first check-in enabled), never on launch?
- [ ] Widget code (Glance) respects the Plain-light-only theming constraint and each Case's `logFlow` (one-tap vs. detail-sheet trampoline)?

### Deprecated APIs
- [ ] Any new deprecation warnings? Resolve or document with a reason.

### Spec Review
- [ ] Does [HODITH_SPEC.md](HODITH_SPEC.md) still describe what was built? Walk through touched sections.
- [ ] Intentional divergence → update the spec. Unintentional divergence → log a bug, don't paper over it.
- [ ] New patterns/components/flows undocumented? Add them.
- [ ] Any Future Work items implemented? Update §17.

### Tests
- [ ] New Repository/ViewModel/domain logic without unit coverage?
- [ ] Changed methods making existing tests pass for the wrong reason? Review test files, not just CI green.
- [ ] Bug fixed → regression test added?
- [ ] Features removed/renamed → tests updated so they don't pass against dead code?
- [ ] New instrumented tests actually ran on a device before committing?
- [ ] New instrumented test class — tagged `@UiTest` if it's a Compose screen test (drives CI's shard split)? Does one of its tests deserve `@Smoke` as the class's representative happy path?
- [ ] Is [TESTING.md](TESTING.md) accurate? Counts, new rows, Deferrals in/out.
- [ ] New flow crossing a system-process boundary? Add to MANUAL_TEST_PLAN.md with rationale, cadence, steps.
