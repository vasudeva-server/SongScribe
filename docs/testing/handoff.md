# Test Audit — Session Handoff

> **Start here in a new session.** This is the entry point for the
> production-first test audit. The full method, rubric, and session plan live in
> [`matrix.md`](./matrix.md); read it before doing anything. This file is the
> "where we are / what to do next" overlay. Both files are disposable scaffolding
> and will be deleted when the audit is done.

## What this effort is

Audit the test suite **from the production code, not from the existing tests** —
existing tests may be systematically testing the wrong things, so starting from
them would propagate that error. For each production class we enumerate testable
behaviors, classify each as `unit` / `e2e` / `none` per the rubric, then check
whether an adequate test (unit *or* e2e) already exists. The output is a complete
testing matrix that later drives a rewrite.

## Decisions already made (do not re-litigate)

- **Matrix is disposable scaffolding**, not a maintained living doc. The durable
  outputs are (1) rewritten tests and (2) the unit/e2e/none **rubric**, which
  gets promoted into `.agents/guides/testing-common.md` at the very end.
- **Plan first, fix later.** No test code changes until the *entire* matrix
  exists and a remediation order is approved. Audit sessions are read-only.
- **Quality Principles outrank the level rubric.** Apply Correctness →
  Usefulness → Coverage first; unit/e2e/none second. (See rubric in `matrix.md`.)
- **E2E assessed by reading source only.** Never run the e2e suite during the
  audit (it needs approval and isn't necessary for planning).
- **PIT confirms verdicts during remediation, not during the audit.** Audit
  `inadequate` verdicts are reading-based hypotheses (predicted surviving
  mutants). PIT (`./scripts/mutation-test.sh`, pure-logic + unit only) is run
  when a class's tests are rewritten — to prove the weakness and prove the
  rewrite kills the mutant. Audit sessions remain read-only.
- **Both levels checked per behavior.** When auditing a package, look for
  existing coverage in unit *and* e2e tests — not just the mirrored unit file.

## Per-session procedure

1. Open `matrix.md`, find the next package with status `not yet audited`.
2. Spawn a **subagent** (Java work → its prompt must begin with
   `MANDATORY: Read .agents/rules/serena.md`) to read that package's production
   classes and any tests touching them, and return a findings table with rows:
   **class · behavior · required level · existing test · verdict · action**.
   The subagent does the heavy reading so the main session keeps only the table.
3. Sanity-check the subagent's table against the rubric and Quality Principles;
   resolve any ambiguous classifications.
4. Append the table to the **Findings** section of `matrix.md` under a heading
   for the package, and flip that package's row in the progress table to `done`.
5. Stop and report. One package per session unless a package is small.

## Status

- **Session 0 (charter): DONE** — committed `ce2faf4b`. Rubric written, matrix
  scaffolded, `testing-common.md` reordered to lead with Quality Principles.
- **Session 1 (`dom`, 38 classes): DONE** — six parallel sub-audits; full
  findings appended to `matrix.md` §1, progress row flipped to `done`. Key
  cross-cutting gaps: untested pure conversion/geometry math (`ScaleContext`,
  `getSpanWidthSs`/`get*Px` clamps), weak-but-green tests (relative-only
  `getPitch`, `>=`/`>0` where exact values matter, self-referential oracles,
  tautologies), and untested branch/error paths (`Song` `@Handler` methods,
  `Line` merge logic, `BeatChange.fromLegacyName` happy paths).
- **Next: Session 2 — `io` (16 production classes).**

## Session order (risk-ordered)

See the progress table in `matrix.md` for the authoritative list. Summary:
`dom` → `io` → `layout` → (`midi`/`converter`/`util`/`smufl`/`prefs`/`font`/
`export`/`uiconverter`) → `ui/action` → (`ui/selection`/`edit`/`adjustment`/
`clipboard`) → `ui/component` → `message` → `ui/renderer` → `ui/dialog` →
(`ui/menu`/`playback`/`platform`/top-level `ui`) → (`lifecycle`/`error`/
top-level) → e2e reconciliation.

## Baseline counts

Unit: 1267 `@Test` methods (~132 files). E2E: 79 methods (7 files). Production:
~507 classes. These are gap-finders, not grades.
