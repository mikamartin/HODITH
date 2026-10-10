# HODITH — QA Audit Backlog

Tracks findings from the most recent QA audit (see [QA_AUDIT_RULES.md](QA_AUDIT_RULES.md) for
the procedure). When a listed follow-up branch lands, don't delete its entry — condense it:
shrink its Issues Found entry to one sentence stating what it was and that it's fixed (keep the
number, so it stays findable), and replace its Work Item's Steps with a short, dry summary of
what was actually done, marked `(done)`. Once every item from a pass is resolved this way, the
file is emptied back to this shell, ready for the next audit to repopulate.

**No open findings here.** The third audit pass has run, in the ruleset's "inline-fix mode" — its
one finding (three duplicated test-helper clusters, all quick-fix size) was resolved directly on
that pass's own audit branch, so nothing was left outstanding to track here or in PROGRESS.md.
This pass also ran the ruleset's two brand-new checks for the first time (section 1's
teardown-race sweep, section 8's manual-test-plan hygiene) — both came back clean. The full
detail — what was found, every mutation check's result, the spec cross-reference, and an
unrelated emulator crash hit during verification — lives in
[CLEANUP_LOG.md](CLEANUP_LOG.md)'s `chore/qa-audit` entry, not here.
