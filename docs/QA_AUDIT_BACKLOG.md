# HODITH — QA Audit Backlog

Tracks findings from the most recent QA audit (see [QA_AUDIT_RULES.md](QA_AUDIT_RULES.md) for
the procedure). When a listed follow-up branch lands, don't delete its entry — condense it:
shrink its Issues Found entry to one sentence stating what it was and that it's fixed (keep the
number, so it stays findable), and replace its Work Item's Steps with a short, dry summary of
what was actually done, marked `(done)`. Once every item from a pass is resolved this way, the
file is emptied back to this shell, ready for the next audit to repopulate.

**No open findings here.** The first audit pass has run, but its findings were written into
[PROGRESS.md](PROGRESS.md) instead of this file — as items in its Testing and Shared UI logic
sections, in that file's own Branch/Complexity/Priority format — so the outstanding-work roadmap
stays in one place. Look there, not here. The pass's doc-hygiene fixes landed directly in
TESTING.md and HODITH_SPEC.md §11; its mutation spot checks (QA_AUDIT_RULES.md §2) are the one
section still outstanding, tracked in PROGRESS.md as `chore/qa-audit-mutation-checks`.
