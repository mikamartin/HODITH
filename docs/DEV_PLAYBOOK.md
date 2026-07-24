# HODITH — Developer Playbook

---

## 1. Post-Work Cleanup

Run after any significant feature work or refactor. See [CLEANUP_CHECKLIST.md](CLEANUP_CHECKLIST.md) for the full checklist — walk through it against the real diff, then record the pass in a new [CLEANUP_LOG.md](CLEANUP_LOG.md) entry: what was found and fixed, what was deferred with a reason, and which sections didn't apply.

---

## 2. Ship Checklist

Strip completed items — this list only contains open work.

### Before making the repo public
- [ ] Full-history hygiene audit, not just the latest diff (CLAUDE.md "Git hygiene") — secrets, real local paths, personal info can hide in old commits that going public would expose

### Before first release
**Play Store**
- [ ] App icon that works in all three theme contexts

**Closed Testing**
- [ ] Write CLOSED_TESTING_GUIDE.md (plain-language guide for testing recruits) when the track opens

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

- Glance widget theming is limited: widgets use a fixed neutral palette that does not follow the Plain/Intense/Bright in-app theme. Intentional.
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
| hilt-navigation-compose | 1.2.0 | Deliberately held back from 1.4.0 — that version (and its transitive `androidx.lifecycle` bump to 2.11.0) requires `compileSdk 37`, not yet installed; revisit alongside a deliberate `compileSdk`/`targetSdk` bump, not incidentally |
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
8. **Hilt `@Multibinds`/`@IntoSet` (Set multibindings) under Hilt 2.60 needs `com.google.errorprone:error_prone_annotations` as a `compileOnly` dependency** — Dagger's generated `Set` multibinding code references `@CanIgnoreReturnValue` from that package, which isn't pulled in transitively. Fails at `hiltJavaCompileDebug`/`Release` with `package com.google.errorprone.annotations does not exist` the first time any module adds a multibinding, not before.

### Next upgrade checklist
- [ ] AGP ↔ Gradle compatibility matrix before changing either
- [ ] Hilt release notes for AGP compatibility
- [ ] KSP releases for Kotlin compatibility
- [ ] Bump Room, Hilt, foojay together with AGP
- [ ] Dedicated branch; expect 3–5 sync/build errors on a major jump
- [ ] `./gradlew assembleDebug` from terminal to confirm
- [ ] Run tests after a clean build
