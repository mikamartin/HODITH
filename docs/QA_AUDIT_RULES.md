# HODITH — QA Audit Rules

A periodic, whole-suite audit of test quality — distinct from [CLEANUP_CHECKLIST.md](CLEANUP_CHECKLIST.md),
which runs after each unit of feature work and checks that one diff's tests are sound. This
checklist asks whether the *existing* suite, accumulated over many passes, still holds up:
would it actually catch a regression, does it match the spec's intent rather than just the
code's current behavior, and is it organized well enough to keep extending cheaply.

**Branch:** one branch per audit, `chore/qa-audit`, containing the updated
[QA_AUDIT_BACKLOG.md](QA_AUDIT_BACKLOG.md) plus any doc-hygiene fixes the audit surfaces in
TESTING.md/CLEANUP_LOG.md. Findings that require code changes (not just doc fixes) become
separate proposed follow-up branches in the backlog — do not fix everything inline on the audit
branch itself; each is its own logical unit of work.

**Inline-fix mode:** the default above can be overridden by explicit user direction to fix
everything on the audit branch this pass (typically bounded by "unless it's a genuinely large
piece of work" or similar). When that direction is given, resolve code-level findings directly
instead of writing them up as proposed branches, and confirm anything that looks like a large
piece of work with the user before fixing it inline. In this mode, step 8's "Work, Grouped by
Branch" section is unnecessary — say so explicitly in the backlog rather than leaving it empty.

## Checklist

### 1. CI/test-execution integrity
- [ ] List every test class in `app/src/test/` and `app/src/androidTest/`.
- [ ] Confirm every unit test class is actually picked up by `./gradlew test` — one task, no
      selection config to drift, but confirm no class sits outside `app/src/test/` (or in the
      wrong package) where the JVM test task would silently never discover it.
- [ ] Confirm every instrumented test class falls into exactly one of
      `instrumented-tests.yml`'s two shards (`annotation=...testtags.UiTest` /
      `notAnnotation=...testtags.UiTest`). Since that split is a full partition of
      `app/src/androidTest` by one annotation, there's no allow-list to go stale — the real
      failure mode is a class that exists but the instrumentation runner never discovers at all
      (wrong package, missing/misnamed `@RunWith`, a base class that isn't itself a valid test).
- [ ] Flag any test class that exists but never actually runs — this is a distinct failure mode
      from "no test exists" and easy to miss because the build stays green.

### 2. Mutation spot checks
- [ ] Select 6-10 unit test files spanning risk tiers: pure domain logic with no collaborators
      (verdict engine, trigger evaluation, stats), domain/ViewModel logic exercised against
      HODITH's hand-written Fakes (`FakeHodithRepository`, `FakeClock`, `FakeNotifier`,
      `FakeSettingsRepository`, etc. — no mocking library in this project), and Room-instrumented
      DAO tests. Prioritize files backing core mechanics (verdict engine, trigger evaluation,
      check-in scheduling, notification evaluation) and anything touched by recent feature work.
- [ ] For each: introduce one small, targeted mutation in the source under test (flipped
      boolean, off-by-one on a boundary, swapped operator, reordered priority). Run that file's
      tests via `./gradlew test --tests "fully.qualified.ClassName"` (unit) or the equivalent
      `connectedDebugAndroidTest` filter (instrumented). Confirm a clear failure. Revert
      immediately before moving to the next file.
- [ ] Record every result — pass (mutation caught) or fail (mutation missed) — in
      QA_AUDIT_BACKLOG.md's Mutation Check Results table, even if all pass. A miss usually means
      either a duplicated/untested code path (as opposed to the one the test actually exercises)
      or a genuinely weak assertion — trace it to which before concluding.
- [ ] A pass that only happens because a Fake's default/no-op behavior coincidentally matches
      the expected outcome (rather than an explicit assertion catching the mutated behavior) is
      a weak pass, not a clean one — it would silently stop catching the bug the moment the
      Fake's default ever changed. Record it as its own category in the results table and
      strengthen the test with an explicit assertion, the same as if it were a miss.
- [ ] Any new pure function extracted elsewhere in this pass (sections 5/6 below) gets the same
      one-mutation spot check as this section's sampled files before its new test is considered
      validated, not just written.
- [ ] `git status` must be clean before committing — every mutation is transient.

### 3. Spec cross-reference
- [ ] Check `HODITH_SPEC.md`'s documented core mechanics (confidence tiers, comparison bands,
      trigger semantics, check-in scheduling, notification evaluation, export/import semantics,
      or whatever the current spec's headline contracts are) against the corresponding test
      files' actual assertions.
- [ ] Confirm each test encodes the spec's *stated* behavior, not just whatever the code
      currently happens to do — a risk when tests are written test-after rather than test-first.
- [ ] Flag any mismatch, or any core-mechanic test with no clear spec anchor.

### 4. Structural assertion review
- [ ] Sample the largest and newest instrumented/UI test files (by line count and by recency in
      `git log`).
- [ ] Check each test asserts after every state-changing action rather than chaining several
      (log an event, log another, toggle a setting) before checking any of them landed — a
      chained failure only points at "something in this block," not which action broke.
- [ ] Check for under-asserting tests: does the test edit/change N things but only verify fewer
      of them survived? A test whose name claims full coverage (e.g. `updatesFieldsAndPersists`)
      should verify every field it touched, not a subset.

### 5. UI-logic-that-could-be-a-unit-test review
- [ ] Grep composables for inline validation/transformation logic living directly in
      `onValueChange`/`onClick` lambdas (character caps, digit filters, toggle-reset patterns,
      any small pure transformation) that's exercised only through full instrumented UI tests.
- [ ] For each candidate, confirm it's genuinely pure (no Compose/Context/Android dependency) —
      if so, it's a candidate for extraction into a plain function with a direct unit test,
      following the project's own `BigPictureFilterState`/`AcronymText` precedent.
- [ ] Note duplication too: the same inline transformation reimplemented independently in more
      than one composable (not shared) is the same class of drift those extractions were meant
      to fix — call it out even if extraction isn't proposed yet.

### 6. Test duplication / helper-library review
- [ ] Check whether common multi-step flows (creating a fixture Case/Event, waiting for a
      specific screen, standard dialog-cancel assertions, driving a real configure Activity) are
      copy-pasted inline across many instrumented test files instead of shared via a helper
      file, mirroring what `WidgetConfigureTestFixtures.kt` already does for the
      widget-configure tier.
- [ ] A duplicated helper redefined per-file (rather than imported) is the concrete signal to
      look for.

### 7. TESTING.md hygiene
- [ ] Recompute aggregate test counts (grep every `@Test` per file) and compare against every
      aggregate figure TESTING.md states (coverage tables, section headers) — these tend to
      drift independently since only some are updated per pass.
- [ ] Check Edge Case / narrative entries for bug-discovery history (how a bug was found, what
      the fix was) rather than current behavior — against CLAUDE.md's/CLEANUP_CHECKLIST.md's
      documentation rule that living docs read as a snapshot of the present, not a changelog.
      History belongs in CLEANUP_LOG.md, referenced by branch/feature name, not narrated inline.
- [ ] Check every CLEANUP_LOG.md cross-reference elsewhere in the repo (docs and source-code
      comments alike, e.g. `// see CLEANUP_LOG.md's <branch>`) still names a heading that
      actually exists there. HODITH's log isn't pruned to a retained window like some projects'
      — a dangling reference here means a heading was renamed or the entry was edited, not that
      it aged out. Grep the whole repo, not just `docs/`.
- [ ] Spot-check a handful of per-area table descriptions in TESTING.md against the actual test
      code or the behavior it documents, not just the aggregate counts and history-narration
      checks above — a description can be flatly wrong (e.g. claiming a test verifies the
      opposite of what it actually verifies) without any count drifting to hint at it.
- [ ] Check Known environment issues / Deferrals entries are still accurate — a gotcha or
      deferred item can get fixed by an unrelated dependency bump and never get struck.

### 8. Record findings
- [ ] Write or refresh `docs/QA_AUDIT_BACKLOG.md`: a short "What's Working" summary (issues get
      their own section — this one is for what held up under scrutiny, e.g. mutation checks
      caught, spec alignment confirmed, structural review clean), numbered Issues Found,
      Mutation Check Results table, Spec Cross-Reference Notes, and a "Work, Grouped by Branch"
      section — one subsection per proposed follow-up branch, each with a Deliverable, Steps,
      and Tests line, so it can be picked up directly without re-deriving context.
- [ ] Before assuming an old backlog item is still open, check `git log`/merged PRs for its
      follow-up branch.
- [ ] When a backlog item is resolved: don't delete it. Condense its Issues Found entry to one
      sentence stating what it was and that it's fixed (keep the heading/number so it stays
      findable). Condense its Work Item section to a short, dry summary of what was actually
      done in place of the Steps list, and mark the section heading `(done)`.
- [ ] Apply the doc-hygiene fixes from step 7 directly on the audit branch; leave every
      code-level finding (formula fixes, extraction refactors, CI matrix fixes) as a proposed,
      not-yet-started branch in the backlog.

## Verification

Same build/lint/test/commit discipline as any other change — see `CLAUDE.md`.

- If a connected device/emulator is available, actually run any `androidTest` files touched
  during the audit (structural-review fixes, section 5/6 extractions) via
  `connectedDebugAndroidTest` rather than relying on read-only review alone.
- If verification surfaces a failure unrelated to anything the audit changed (e.g. a
  pre-existing flaky or environment-dependent test), don't try to fix it as part of the audit —
  but don't let it live only in conversation either. Note it somewhere durable: a short line in
  DEV_PLAYBOOK.md's Known Limitations if it looks environment-specific, or a brief aside in
  QA_AUDIT_BACKLOG.md if it's unclear yet whether it's a real bug.
