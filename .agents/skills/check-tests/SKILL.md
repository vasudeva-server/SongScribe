---
name: check-tests
description: Audit unit tests for correctness, usefulness, and code coverage
model: opus
effort: high
disable-model-invocation: true
---

## Check Tests: Unit Test Quality Audit

Audit unit tests against the **Test Quality Principles** in
`.agents/guides/testing-common.md` (read that section first; it is the source of
truth for what this skill enforces). Also apply the conventions in
`.agents/guides/testing-unit.md`.

The audit reports on three axes, in priority order:

1. **Correctness** — can each test actually fail when the production code is wrong?
2. **Usefulness** — does each test guard a realistic failure mode, at the right level, without redundancy?
3. **Coverage** — what behavior, branches, and error paths are unverified?

A test that gives **false confidence** (passes while the code is broken, or covers
a line without asserting on its effect) is the highest-severity finding. Rank
findings by that lens, not by volume.

This skill audits **unit tests only**. It never runs the e2e suite. When the
`--mutation` flag is set, PIT runs the covering **unit** tests against mutated
production code; this does not invoke e2e and needs no e2e approval.

## Phase 1: Parse Arguments and Determine Scope

`$ARGUMENTS` may contain optional flags plus a scope token. Parse all flags
first, then resolve the remaining token as the scope.

- **`--mutation`** present → run the mutation-testing pass in Phase 4. Off by
  default because it is the slow path.
- **`--fix`** present → suppress all interactive questions in Phase 5 and fix
  every finding, including minor and low-confidence ones, without approval.

### Scope resolution (the remaining token)

**Mode A — Git diff (default).** If no scope token is given, review the changed
tests from git: run `/opt/homebrew/bin/git diff` (or `git diff HEAD` if changes
are staged). Collect changed `*Test.java` files. If there are no changes, review
the test files the user mentioned or edited earlier in the conversation.

**Mode B — Package or file.**

- A **dotted package** (e.g. `util`, `io`, `smufl`) — the `songscribe.` prefix is
  implicit and must not be included. Tests live at
  `src/test/java/songscribe/<dots-to-slashes>`; production code at
  `src/main/java/songscribe/<dots-to-slashes>`.
- A **file path or class name** (e.g. `StringUtilsTest`, a path ending in
  `.java`) — used as-is.

For each test in scope, identify its **production counterpart** (e.g.
`StringUtilsTest` → `songscribe.util.StringUtils`). Use Serena
(`jet_brains_get_symbols_overview`, `jet_brains_find_symbol`) per
`.agents/rules/serena.md` to map tests to the symbols they exercise. Record:

- the set of **test classes/methods** in scope, and
- the set of **production classes** they target (needed for Phases 3–4).

## Phase 2: Static Review (parallel agents)

Launch two review agents concurrently in a single message via the Agent tool.
When spawning agents, include `model: "sonnet"` in each Agent tool call.
Run both agents (Correctness, Usefulness) with `model: sonnet` — they surface
candidate findings that the orchestrator re-validates before fixing. Begin each
agent's prompt with:

> MANDATORY: Read .agents/rules/serena.md and follow it for all Java exploration.

Pass each agent the scoped test files and their production counterparts, and
instruct it to apply the Test Quality Principles from
`.agents/guides/testing-common.md`. Each finding must name the file and line, state
**what bug the test fails to catch**, and rate confidence (high / medium / low).

### Agent 1: Correctness

For every test in scope, determine whether it can fail when the code is wrong:

1. **Cannot-fail tests** — no assertions; assertions only against mocks; tautologies; `isNotNull()` on a non-nullable value; assertions on a stubbed return value.
2. **Name vs. behavior mismatch** — the test name describes a condition or outcome the body does not actually assert.
3. **Implementation coupling** — over-mocking that verifies *how* the code works rather than its observable behavior; tests that would break on a pure refactor.
4. **Flakiness risk** — dependence on timing, execution order, shared mutable state, the real clock, or the filesystem.
5. **Dead preconditions** — setup that does not actually drive the path the test claims to cover.

### Agent 2: Usefulness

For the same tests:

1. **Wrong level** — unit-testing pure integration risk with everything mocked, or duplicating cheap unit coverage in a heavier test.
2. **Low-value targets** — tests of trivial getters/setters or framework behavior that cannot regress.
3. **Missing high-value cases** — absent edge cases, boundaries, error paths, and `@Nullable` contracts (recall the project bans `Optional`).
4. **Redundancy** — multiple tests asserting the same thing.
5. **Poor diagnostics** — bare boolean assertions where a specific AssertJ matcher would localize the failure.

## Phase 3: Coverage

Generate a fresh JaCoCo report scoped to the tests under review:

- Run `./scripts/coverage.sh unit` for a package-level audit, or
  `./scripts/coverage.sh <TestClass...>` for specific classes.
- Read the per-class numbers from `build/reports/jacoco/test/jacocoTestReport.xml`
  for the **production classes** identified in Phase 1.

Report, per production class in scope:

- **Uncovered branches and error paths** — weighted above uncovered lines.
- **Executed-but-unverified lines** — covered by JaCoCo but with no assertion
  observing the effect (cross-reference Agent 1's findings).
- **Criticality** — gaps in `dom`, `io`, `layout`, `smufl` outrank gaps in UI glue.

Treat the coverage percentage as a gap-finder, never as a grade.

## Phase 4: Mutation Testing (only if `--mutation`)

Skip this phase entirely unless the flag was set.

1. For each production class/package in scope, run
   `./scripts/mutation-test.sh <fully-qualified-class-or-package-glob>`.
2. Parse `build/reports/pitest/mutations.xml`. For each mutation, the `status`
   attribute matters:
   - **`SURVIVED`** — the mutated code passed all covering tests. This is a
     concrete correctness hole: a high-severity finding naming the class, line,
     mutator, and the assertion that should have caught it.
   - **`NO_COVERAGE`** — no test exercised the mutated line; fold into the Phase 3
     coverage gaps.
   - `KILLED` / `TIMED_OUT` — the suite caught it; no finding.
3. If the run errors before producing a report, report the failure verbatim and
   continue with Phases 2–3 results rather than silently dropping the axis.

## Phase 5: Report, Approve, Fix

Choose the path based on whether `--fix` was in `$ARGUMENTS`.

### Path A: `--fix` mode

1. **Fix all findings immediately** — every finding from Phases 2–4 (Correctness,
   Usefulness, Coverage, and Mutation if it ran), including minor and
   low-confidence ones. Do not ask any questions or seek approval.
2. Any change under `src/main/` or `src/test/` requires `./scripts/compile.sh`
   before re-running; then re-run the relevant `./scripts/test.sh <target>`
   (unit only) to confirm green.
3. **Summarize** what was fixed when done, grouped by axis.

### Path B: Interactive mode (default)

1. **Present findings** in one organized summary, grouped by axis (Correctness,
   Usefulness, Coverage), each finding with file:line, the bug it misses, and
   confidence. Lead with false-confidence findings. Then add a blank line and
   "Ready for questions."
2. **Clarifying questions** via AskUserQuestion for any finding whose intent is
   ambiguous (e.g. a test that looks tautological but may be a deliberate
   smoke test).
3. **Questionable findings** — present suspected false positives via
   AskUserQuestion rather than silently dropping them.
4. **Approval** — use AskUserQuestion to present the final list of tests to fix or
   add, and get approval before changing anything.
5. **Fix** — after approval, edit/add tests. Any change under `src/main/` or
   `src/test/` requires `./scripts/compile.sh` before re-running; then re-run the
   relevant `./scripts/test.sh <target>` (unit only) to confirm green. Briefly
   summarize what changed.
