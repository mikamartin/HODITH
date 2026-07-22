# HODITH — Post-Work Cleanup Checklist

Run after any significant feature work or refactor (see [DEV_PLAYBOOK.md](DEV_PLAYBOOK.md) §1). Actually walk through every applicable item below against the real diff — don't skip straight to writing up [CLEANUP_LOG.md](CLEANUP_LOG.md) from memory of what changed. Then record the pass there: what was found and fixed, what was deferred with a reason, and which sections didn't apply.

### Duplication
- [ ] Are any composables copy-pasted with minor variation? Extract a shared component or parameter.
- [ ] Are any styling patterns (colors, gradients, padding sequences) repeated inline instead of using the design system / theme?
- [ ] **Are any user-visible strings inline in composables instead of going through the `Voice` layer?** (HODITH's #1 hygiene rule — an inline string ships in one voice only.)
- [ ] Does any ViewModel logic appear in more than one place?
- [ ] Does any new `Repository` function overlap with an existing one that could be parameterised instead?
- [ ] Does any new `Dao` query duplicate an existing query with a Kotlin-side filter that could be pushed into SQL?

### Decoupling
- [ ] Do composables contain business logic that belongs in the ViewModel or Repository?
- [ ] **Is any time-dependent logic calling `System.currentTimeMillis()` directly instead of the injected `Clock`?** (Breaks verdict/trigger/stats testability.)
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

### Deprecated APIs
- [ ] Any new deprecation warnings? Resolve or document with a reason.

### Spec Review
- [ ] Does [HODITH_SPEC.md](HODITH_SPEC.md) still describe what was built? Walk through touched sections.
- [ ] Intentional divergence → update the spec. Unintentional divergence → log a bug, don't paper over it.
- [ ] New patterns/components/flows undocumented? Add them.
- [ ] Any Future Work items implemented? Update §14.

### Tests
- [ ] New Repository/ViewModel/domain logic without unit coverage?
- [ ] Changed methods making existing tests pass for the wrong reason? Review test files, not just CI green.
- [ ] Bug fixed → regression test added?
- [ ] Features removed/renamed → tests updated so they don't pass against dead code?
- [ ] New instrumented tests actually ran on a device before committing?
- [ ] Is [TESTING.md](TESTING.md) accurate? Counts, new rows, Deferrals in/out.
- [ ] New flow crossing a system-process boundary? Add to MANUAL_TEST_PLAN.md with rationale, cadence, steps.
