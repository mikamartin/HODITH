# HODITH — QA Audit Backlog

Tracks findings from the most recent QA audit (see [QA_AUDIT_RULES.md](QA_AUDIT_RULES.md) for
the procedure). When a listed follow-up branch lands, don't delete its entry — condense it:
shrink its Issues Found entry to one sentence stating what it was and that it's fixed (keep the
number, so it stays findable), and replace its Work Item's Steps with a short, dry summary of
what was actually done, marked `(done)`. Once every item from a pass is resolved this way, the
file is emptied back to this shell, ready for the next audit to repopulate.

**No open findings here.** The second audit pass has run, in the ruleset's "inline-fix mode" —
every finding it turned up (5, all quick-fix size: a stale doc pointer, two dangling
`CLEANUP_LOG.md` comment references, two real mutation-coverage gaps in `StatsEngine.kt`, and a
duplicated test fixture) was resolved directly on that pass's own audit branch, so nothing was
left outstanding to track here or in PROGRESS.md. The full detail — what was found, how each fix
was verified, and what's still deferred (the instrumented/DAO tier of the mutation spot check,
no device attached that pass) — lives in [CLEANUP_LOG.md](CLEANUP_LOG.md)'s `chore/qa-audit`
entry, not here.
