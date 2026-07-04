# HODIT — Build Progress

Where the build stands right now, and the intended phase order. Update the status line and phase checkboxes as part of each phase's wrap-up (alongside the [CLEANUP_LOG.md](CLEANUP_LOG.md) entry) — this is what lets a new session pick up without re-deriving history from git log.

## Current status

**Phase 0 complete.** Phase 1 not started.

## Phase order

- [x] **Phase 0** — Repo and project scaffold: git init, Gradle/AGP/Compose/Hilt/Room toolchain, empty package skeleton, trivial `MainActivity` proving the build (`ktlintCheck` → `test` → `assembleDebug` all green).
- [ ] **Phase 1** — Room entities, DAOs, `HoditRepository`, JVM `Clock` abstraction + full unit/DAO tests.
- [ ] **Phase 2** — Big Picture view. Hardest UI problem (custom Compose, shared time axis, pinch-zoom, performance with years of data) — done early so its constraints surface before other screens depend on it.
- [ ] **Phase 3** — Home + Case CRUD + logging flows (one-tap, detail sheet, start/stop, retro-log).
- [ ] **Phase 4** — Voice layer + three themes.
- [ ] **Phase 5** — Verdict engine + Hunch flow.
- [ ] **Phase 6** — Per-case visuals + stats.
- [ ] **Phase 7** — Widgets.
- [ ] **Phase 8** — Triggers + check-ins (WorkManager, notifications).
- [ ] **Phase 9** — Share cards, export/import, Settings, About.

Each phase ends with a DEV_PLAYBOOK.md §1 cleanup pass logged in CLEANUP_LOG.md, a TESTING.md check, and this file updated.
