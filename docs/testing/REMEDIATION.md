# Test Remediation — Session Driver

> **Start here for the remediation phase.** The audit (see [`matrix.md`](./matrix.md)
> and [`handoff.md`](./handoff.md)) is COMPLETE. This file is the "where we are /
> what to do next" overlay for *implementing* the audit's findings: writing,
> strengthening, relocating, and deleting tests. Disposable scaffolding — deleted
> with the rest when remediation finishes.

## What this phase is

The audit enumerated **2,614 behavior rows** across 70 section files (in
`matrix-<pkg>/`). Each actionable row carries a `done` column marker (⬜ todo /
✅ done). **~1,619 rows are actionable**; the rest are `adequate`/`keep`/`none`.
We implement every actionable row's `action`, package by package, in risk order.

## Decisions (settled — do not re-litigate)

1. **Order:** audit risk-order (`dom → io → layout → util → action → selection →
   component → message → renderer → dialog → menu → lifecycle → e2e`). Finish each
   section file before the next.
2. **Tracking:** the section files' `done` column is the single source of truth.
   `remediation-ledger.md` is a *generated* view (`python3 gen_ledger.py`) — never
   hand-edit its counts.
3. **E2E deferred:** the 38 `e2e`-level rows are written last, in one batch, run
   with a single user-approval session. Unit work needs no approval. (Note:
   `wrong-level` rows that *relocate e2e logic into unit tests* are unit work and
   are NOT deferred.)
4. **PIT at package checkpoints:** after a package's pure-logic units are done,
   run scoped `./scripts/mutation-test.sh <target>` to confirm flagged mutants die.
   Record results in the ledger. Not per-class.
5. **Keep/remove lists built as we go:** `dispositions/{keep,modify,add,relocate,
   remove}.txt` are populated *during* remediation, not from the archived scripts
   (`archive/tests-to-*.txt` are superseded — ignore them).
6. **Final sweep is a quality judgment, not mechanical:** any `@Test` not in
   keep/modify/add/relocate-target is a delete *candidate*; each is then judged via
   the `/check-tests` skill (which encodes the `testing-common.md` Quality
   Principles, `--mutation` where relevant). A test survives only with a solid
   reason to exist; the delete list is surfaced for user review before removal.

## Chunking, models, orchestration (settled)

**Chunk = one test file (one production class's actionable rows), NOT a section
file.** The 1,619 rows span 326 classes; sizes are lopsided (206 classes have 1–3
rows, 21 have 16+, max `SelectionCoordinator` 42). Size each chunk by estimated
context cost, not row count, against a **130K-token worker budget**: hard caps of
**7 rows for ordinary work, 4 rows for heavy pure-logic** (geometry/MIDI/
serialization math) per worker invocation. At each package's start,
pre-compute its per-class actionable tally and write the planned chunk boundaries
into the ledger — chunk deliberately, never guess mid-stream.

- **Tiny (1–3 rows):** batch several from the *same source directory* into one
  worker so the static reading (guides, base classes, fixtures) amortizes.
- **Medium/large (4–15):** one worker per class.
- **Huge (16+):** dedicated worker, sliced, **continued via `SendMessage` to the
  same worker** (keeps the production class it already read in context — never
  re-spawn fresh for a continuation).

**Orchestration: fresh scoped workers + continuation.** Main session orchestrates
at test-file granularity (this is what makes ⬜/✅ markers, commits, and resume
work). Do NOT fork (a fork drags the main session's full history into every
worker). Lean fresh workers + same-directory grouping + `SendMessage` give the
"don't re-read" benefit without that cost.

**Model per worker:** default **Sonnet 4.6**; use **Opus** for rows where a subtly
wrong assertion does real damage and PIT will judge it — pure-logic math and the
`inadequate` "weak-but-green" rewrites (assertion precision is the whole point).
No Haiku for test authoring (it is reasoning, not mechanics). Main coordinator: Opus.

## Per-work-unit procedure

Spawn a fresh subagent (its prompt MUST begin with
`MANDATORY: Read .agents/rules/serena.md`) to do the heavy reading/writing so the
main session keeps only the summary. The subagent:

1. Reads its assigned ⬜ rows + the production class(es) via serena, and the
   relevant testing guides (`testing-common.md`, `testing-unit.md`).
2. Implements each row's `action`: write (`missing`), strengthen in place
   (`inadequate`), relocate to correct level (`wrong-level`), delete (`redundant`).
   E2E-level `missing` rows are SKIPPED (deferred batch) — leave ⬜.
3. Runs `./scripts/compile.sh`, then `./scripts/test.sh <ClassName>` (unit only).
   Fixes failures before returning. Never runs e2e.
4. Returns, per row: the `Class.method` disposition(s) (keep/modify/add/relocate/
   remove) and which rows are complete.

Main session, after the unit returns:
- Flips each completed row's marker ⬜ → ✅ in the section file.
- Appends the dispositions to `dispositions/*.txt`.
- `python3 gen_ledger.py` to refresh progress.
- Snapshots: `git stash push --include-untracked -m "remediation <section>" &&
  git stash apply` (per the development safety rule), then commit via the
  `/commit-commands:commit` skill.

## Phases

- **Phase A — unit remediation** (the bulk): packages 1→12 in order, e2e rows left ⬜.
- **Phase B — PIT checkpoints:** interleaved at each package boundary (decision 4).
- **Phase C — e2e batch:** all deferred ⬜ e2e rows, one approval session.
- **Phase D — final sweep:** `/check-tests` over delete candidates; user-reviewed removal.
- **Phase E — close-out:** promote the rubric (`matrix.md` lines 29–91) into
  `.agents/guides/testing-common.md`; archive/delete this scaffolding.

## Scripts

- `add_markers.py` — adds the `done` column to section files (idempotent; already run).
- `gen_ledger.py` — regenerates `remediation-ledger.md` from the markers.
