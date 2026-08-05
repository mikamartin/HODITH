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

- Glance widget theming is limited: widgets always render the Plain theme's light palette and don't follow the Plain/Intense/Bright in-app theme or system light/dark mode. Intentional.
- *(populate as they're discovered)*

---

## 5. Testing App Widgets

- **`provideGlance()` must read its data reactively, inside `provideContent { }`** — via `currentState<Preferences>()` for Glance-owned per-instance state, and a live collected `Flow` (e.g. `produceState { repository.observeX().collect { value = ... } }`) for Room-backed data — never as one-shot suspend reads captured by the composable from *outside* `provideContent { }`. This was the real root cause of a bug where both widgets got permanently stuck showing their empty/not-found state even after a correct, verified configure flow: Android binds a widget (`bindAppWidgetIdIfAllowed`) *before* its `android:configure` Activity even runs, so Glance can start a session and run `provideGlance()` once with no data configured yet. If that first pass captures its values as plain `val`s instead of Compose `State`, the composable has nothing to recompose against — later `update()` calls (even when they demonstrably write the right data and the system genuinely receives an update push) just reapply the same frozen output. Reading state reactively inside `provideContent { }` fixes this regardless of when the session started, since the composable then recomposes itself whenever the underlying value actually changes.
- **`android:initialLayout` is required in every `res/xml/*_widget_info.xml`.** Its absence caused a separate, earlier-discovered bug: `AppWidgetHostView` threw `Resources$NotFoundException` (resource ID 0) trying to inflate a placeholder before a widget's first real update landed. Point it at a minimal placeholder layout (see `res/layout/widget_loading.xml`) — Glance doesn't generate one automatically.
- **Widget configure-time updates should target the specific widget being configured**, not `updateAll()`: a widget being configured for the first time isn't guaranteed to be in Glance's "known instances" list yet, so `updateAll()` can silently miss it. Resolve the real `glanceId` via `GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)` and call `MyWidget().update(context, glanceId)` instead.
- **Real, end-to-end `AppWidgetHost` instrumented tests are possible without a physical device or manual placement** — see `ListWidgetConfigureFlowTest`/`SingleCaseWidgetConfigureFlowTest` for the pattern. Requires `adb shell appwidget grantbind --package com.secondmonday.hodith --user 0` on the target device/emulator first — it's a per-device grant, not a per-run one, so it only needs redoing when the device/AVD itself is reset (a locally reused emulator needs this once; CI's disposable emulator needs it every run, wired into `instrumented-tests.yml`'s test step).
  - **Drive the real configure Activity, not the ViewModel/Glance functions directly.** An early version of these tests called `ListWidgetConfigureViewModel`/`Glance` functions directly instead of launching the real Activity, and it passed even though the real on-device flow was still broken by the bug above — it only proved the data/render pipeline works in isolation, not that the Activity actually reaches it. Launch the real Activity via `ActivityScenario.launch<T>(intent)` (with `EXTRA_APPWIDGET_ID` set, after `AppWidgetHost.allocateAppWidgetId()` + `bindAppWidgetIdIfAllowed()`), drive its real Compose picker dialog with a `createEmptyComposeRule()` (not `createAndroidComposeRule<T>()` — that manages its own Activity launch and doesn't support a custom Intent), then inspect the real `AppWidgetHostView`'s inflated RemoteViews via `AppWidgetHost.createView()`.
  - **`LazyColumn`-backed content won't populate its row items in this minimal host**: Glance backs it with a `RemoteViewsService`/`ListView` adapter, which needs a fuller service-binding lifecycle than this host implements. Assert on structural presence (e.g. is a `ListView` there, is the empty-state message gone) rather than list-item text for those widgets.
  - **Matching a specific row's toggle/radio button by text sibling doesn't work**: `LazyColumn` flattens row items into semantics siblings regardless of visual `Row` grouping, so every toggle in the list can appear as a "sibling" of every row's text. Match by vertical bounds overlap with the target text node instead (find the text node's `boundsInRoot`, then the toggleable/selectable node whose bounds overlap it — see `ComposeTestRule.clickRowControl` in `WidgetConfigureTestFixtures.kt`). This also matters because these tests run against the real shared app database, which may contain other, unrelated Cases in the same list — clicking every toggle isn't safe.

---

## 6. Tooling Upgrade Reference

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
