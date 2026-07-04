# CLAUDE.md

Project-specific instructions for AI assistants working in this repository.

## What this is

HODIT ("How Often Does That Happen") — a local-only Android app (Kotlin, Jetpack Compose, Room, Hilt, Glance, WorkManager) for checking gut feelings about how often events happen against logged reality. Read [docs/HODIT_SPEC.md](docs/HODIT_SPEC.md) §1–4 (idea, vocabulary, principles, non-goals) before writing any code or copy — the Case/Hunch/Verdict vocabulary and the observation-not-behaviour-change stance shape everything.

This repo is also a portfolio piece, built with heavy AI assistance. This file is checked in as both working instructions and a transparent record of how AI was used. See the README's "AI-assisted development workflow" section.

## Collaboration rules

- Confirm before making non-trivial changes; explain the rationale for technical and UX decisions before implementing.
- Always confirm the branch name and proposed commit message with the user before committing — never commit without explicit approval of both.
- Never merge a branch or PR without the user's explicit go-ahead. Commit, present a summary of what changed and why, then stop and wait.
- Branch naming: `type/topic` (e.g. `feature/verdict-engine`, `fix/widget-elapsed-time`, `chore/ci-setup`). Topic describes the work — no timestamps.
- Before the repo has a GitHub remote: one local feature branch per logical unit of work, merged locally only once the user says go.
- Once published: open a PR per unit of work; the user reviews and merges on GitHub.

## HODIT-specific rules

- **Every user-visible string goes through the `Voice` layer** and must be added to all three voices (Serious, Goth, Quirky) in the same commit. No inline UI strings, ever.
- **No gamification language or mechanics** — no streaks, scores, "keep it up!", "you missed a day". HODIT observes; it does not push behaviour change (spec §4). If a feature idea drifts that way, stop and raise it.
- **Verdict, trigger, and stats code stays pure Kotlin** (`domain/` package): no `android.*` imports, all time via injected `Clock`.
- Product constants (confidence tiers, comparison bands, nudge threshold) live as named constants in the domain layer — never inline magic numbers.

## Git hygiene (public repo)

This repo is public on GitHub. Treat every commit as visible to the world from day one:

- **Never commit secrets** — no API keys, tokens, passwords, or signing credentials of any kind. Keystores (`*.jks`, `*.keystore`) and `keystore.properties` must be gitignored and stay local / in CI secrets only.
- **Never commit local setup** — `local.properties`, `.idea/` (beyond the standard shared subset, prefer none), `*.iml`, emulator configs, OS junk (`Thumbs.db`, `.DS_Store`), build output.
- A complete Android `.gitignore` is the first commit of the repo, before any code.
- **No personal data in the repo** — no real local filesystem paths (`C:\Users\...`) in code, docs, scripts, or committed logs; no personal info in screenshots, seed data, or test fixtures.
- Review `git status` and the staged diff before every commit; if a secret is ever committed, treat it as leaked — rotate it and rewrite history before pushing.
- CI signing uses GitHub Actions secrets, never files in the repo.

## Branch and PR workflow (post-publish SOP)

1. `git checkout -b type/topic` from `main`
2. Make changes — confirm rationale first for anything non-trivial
3. Test locally, **sequentially**: `./gradlew ktlintCheck` → `./gradlew test` → `./gradlew assembleDebug`
4. Human reviews diff and does exploratory/manual testing
5. Commit — one subject-line commit per logical change (`type: description`)
6. `git push -u origin branch-name`
7. `gh pr create` with title and summary; CI runs
8. Human reviews on GitHub and merges — never merge without explicit go-ahead
9. `git checkout main && git pull origin main && git branch -d branch-name`

## Source-of-truth docs (`docs/`)

- [HODIT_SPEC.md](docs/HODIT_SPEC.md) — what the app does, data model, screens, future work
- [TESTING.md](docs/TESTING.md) — test strategy, coverage, deferrals
- [MANUAL_TEST_PLAN.md](docs/MANUAL_TEST_PLAN.md) — deliberately manual-only journeys *(create from the seed list in TESTING.md when the first widget/notification flow lands)*
- [DEV_PLAYBOOK.md](docs/DEV_PLAYBOOK.md) — cleanup checklist, ship checklist, tooling reference
- [CLEANUP_LOG.md](docs/CLEANUP_LOG.md) — log of every cleanup pass, in order
- CLOSED_TESTING_GUIDE.md — *(create when the Play closed testing track opens)*

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/): `type: short description`, subject ≤ 72 chars, imperative mood, subject line only — context belongs in the branch name, PR description, or CLEANUP_LOG.md.

Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`.

## Working agreements

- After any significant feature work, walk through DEV_PLAYBOOK.md §1 and log a new pass in CLEANUP_LOG.md.
- Keep HODIT_SPEC.md in sync with what was actually built — intentional divergence updates the spec; unintentional divergence is a bug to fix, not a spec update.
- Strike resolved ship-checklist items out entirely — the checklist only contains open work.

## Commands

- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (device/emulator required)
- Lint check / autofix: `./gradlew ktlintCheck` / `./gradlew ktlintFormat`
- Debug build: `./gradlew assembleDebug`

**Never run Gradle tasks in parallel** — concurrent Kotlin daemons collide on incremental build cache files (Windows `AccessDeniedException`), requiring `./gradlew clean` to recover. Always sequential.
