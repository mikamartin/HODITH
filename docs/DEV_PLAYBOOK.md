# HODITH — Developer Playbook

---

## 1. Post-Work Cleanup

Run after any significant feature work or refactor. Copy the checklist into a new entry in [CLEANUP_LOG.md](CLEANUP_LOG.md), tick off what you found and fixed, note anything deferred with a reason.

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

---

## 2. Ship Checklist

Strip completed items — this list only contains open work.

### Before making the repo public
- [ ] Full-history hygiene audit, not just the latest diff (CLAUDE.md "Git hygiene") — secrets, real local paths, personal info can hide in old commits that going public would expose
- [ ] Make the GitHub repository public
- [ ] Add a branch-protection rule on `main` requiring the `build` CI check (blocked on GitHub's free plan while the repo is private — see PROGRESS.md's CI section)

### Before first release
**Debug scaffolding**
- [ ] Remove or fence out (debug-build-only) the Phase 2 seed-data mechanism used to exercise Big Picture before Case CRUD existed

**Play Store**
- [ ] Screenshots for all required form factors (all three themes — they're the differentiator)
- [ ] Short and long store description (lead with what it does — check gut feelings against reality; the habit-tracker distinction can be a clarifying line, not the headline)
- [ ] Content rating questionnaire + data-safety form (truthfully: no data collected, all local, no network permission)
- [ ] App icon that works in all three theme contexts

**Closed Testing**
- [ ] Write CLOSED_TESTING_GUIDE.md (plain-language guide for testing recruits) when the track opens
- [ ] Recruit testers per current Play policy for new personal accounts

### Post-launch
- [ ] CI badge + Play Store link in README
- [ ] Test result artifacts published; badges in README
- [ ] Stretch: auto-upload AAB to internal testing track

---

## 3. How to Cut a Release

Run through this after CI is green on `main` and the Manual Test Plan has passed.
1. Bump `versionCode` (+1, always) and `versionName` in `app/build.gradle.kts`; commit, push, CI green.
2. `git checkout main && git pull origin main`
3. `git tag vX.Y.Z && git push origin vX.Y.Z` — triggers the release workflow.
4. Download the signed AAB from the workflow Artifacts.
5. Upload to Play Console (Internal Testing → Production per release type).

Tags: `vMAJOR.MINOR.PATCH`. Delete a test tag: `git tag -d vX.Y.Z-test && git push origin --delete vX.Y.Z-test`.

---

## 4. Known Limitations

Permanent accepted constraints — nothing here gets checked off.

- Glance widget theming is limited: widgets use a fixed neutral palette that does not follow the Serious/Goth/Quirky in-app theme. Intentional.
- *(populate as they're discovered)*

---

## 5. Tooling Upgrade Reference

**Version matrix reflects the toolchain verified at project setup — re-verify before relying on it, then maintain in place (update rather than append).**

| Tool | Version | Constraint |
|---|---|---|
| AGP | 9.2.1 | Requires Gradle 9.4.1+ (min and default) |
| Gradle | 9.4.1 | Min/default for AGP 9.2.x per official release notes |
| Kotlin | 2.3.20 | Pinned via `buildscript classpath` (gotcha 1). Kotlin 2.4.0 is stable but KSP has not yet published a matching release — stay on 2.3.20 until it does |
| KSP | 2.3.9 | Decoupled from Kotlin versioning since 2.3.0 |
| Hilt | 2.60 | First version supporting and requiring AGP 9 |
| Room | 2.8.4 | Required for Kotlin 2.3.x KSP2. Room 3.0.0 exists (new `androidx.room3` package, KMP/Wasm-focused) but is out of scope — HODITH is Android-only and Room 2.x remains in supported maintenance mode |
| WorkManager | check latest at setup | verify Hilt integration (`androidx.hilt:hilt-work`) version pairing |
| foojay-resolver-convention | 1.0.0 | Pre-1.0 breaks on Gradle 9 |
| Compose BOM | 2026.06.01 | check for newer at setup |
| ktlint plugin | 14.2.0 | default style; `.editorconfig` exceptions: `@Composable` naming, test naming |

### Gotchas
1. **AGP 9 built-in Kotlin:** don't add `id("org.jetbrains.kotlin.android")` — classloader `ClassCastException`. Pin Kotlin via root `buildscript { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20") }`.
2. **`kotlinOptions` removed:** AGP 9 aligns JVM target from `compileOptions`; delete the block.
3. **Hilt 2.51.1–2.58 fail with AGP 9** — use 2.59.x+.
4. **Room < 2.8.x crashes under Kotlin 2.3.x KSP2** — bump all three Room artifacts together.
5. **foojay < 1.0.0 incompatible with Gradle 9.**
6. **Compose API removals:** `animateItemPlacement()` → `animateItem()`; check BOM notes on every bump.
7. **Never run Gradle tasks in parallel** (Windows Kotlin-daemon cache collision → `AccessDeniedException`, needs `./gradlew clean`). Sequential only.

### Next upgrade checklist
- [ ] AGP ↔ Gradle compatibility matrix before changing either
- [ ] Hilt release notes for AGP compatibility
- [ ] KSP releases for Kotlin compatibility
- [ ] Bump Room, Hilt, foojay together with AGP
- [ ] Dedicated branch; expect 3–5 sync/build errors on a major jump
- [ ] `./gradlew assembleDebug` from terminal to confirm
- [ ] Run tests after a clean build
