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

## How to Write Findings (applies to every phase and every agent)

The reader has not read the test or the code it exercises, and does not remember
how either works. Write every finding, question, and summary so that person
understands it without opening a file. This is a hard requirement, not a style
preference — a finding the reader cannot understand is a failed finding.

**Every finding uses this shape:**

1. **Where** — `SomeTest.java:123`, plus the test's purpose in plain words ("the
   test that checks a rest gets the right vertical position").
2. **What the test does now** — one or two plain sentences: what it sets up and
   what it checks.
3. **What's wrong with it** — stated as *a real bug this test would not catch*.
   Describe that bug concretely: "if the code returned the wrong staff line for
   a rest, this test would still pass, because it only checks that the result
   isn't null."
4. **What to do instead** — the concrete change to the test, described so the
   reader can picture it.

**Rules for the writing itself:**

- Never report a finding purely as a category name. "Tautological assertion" is
  not a finding; "this test asserts that the value it just set equals itself, so
  it passes no matter what the code does" is.
- Full sentences, one idea each. Short sentences beat dense ones.
- Always answer "so what?" — name the bug that slips through, or say plainly
  that nothing slips through and this is about wasted effort or noise.
- Coverage numbers are meaningless on their own. Never report a bare percentage;
  say which behavior is untested and what breaking it would look like.
- For mutation results, do not just name the mutator. Say in plain words what
  the mutation changed ("the tool flipped `<` to `<=`") and what that would mean
  if it were a real bug ("a note exactly on the boundary would be placed one
  line too high, and no test noticed").
- Skip severity labels that carry no information; state the consequence instead.
  Confidence (high / medium / low) is still reported, in plain words.

**Questions follow the same standard.** Before asking anything, give the reader
the background needed to answer it: what the test does, what looked wrong, why
you are unsure, and what each answer would lead you to do. Never ask a question
that presumes the reader has the file in mind.

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
are staged). Collect changed `*Test.java` files.

If there are **no uncommitted changes**, fall back to the branch's own commits —
everything this branch has added since it forked from `develop`:

- Determine the base ref: use `develop` if
  `/opt/homebrew/bin/git rev-parse --verify --quiet develop` succeeds, otherwise
  `origin/develop`.
- List the branch's commits with
  `/opt/homebrew/bin/git log --oneline <base>..HEAD`. The two-dot form lists only
  commits reachable from `HEAD` but not from the base, so commits that came from
  `develop` are excluded automatically — this stays correct even if the branch
  was rebased onto `develop` during development.
- Collect the changed `*Test.java` files with
  `/opt/homebrew/bin/git diff --name-only <base>...HEAD` (three dots — diff
  against the merge base, not against the tip of `develop`), and read their diff
  with `/opt/homebrew/bin/git diff <base>...HEAD`. Exclude deleted files.
- If `HEAD` is `develop` itself, or the commit list is empty, review the test
  files the user mentioned or edited earlier in the conversation.

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

Copy the entire **How to Write Findings** section above verbatim into each
agent's prompt, and tell the agent its findings will be shown to a reader who has
not read the test or the production code. An agent that returns dense,
jargon-filled findings has not done its job.

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
- **Executed-but-unverified lines** — the test runs this code but never checks
  what it produced, so a wrong result would go unnoticed (cross-reference Agent
  1's findings).
- **Criticality** — gaps in `dom`, `io`, `layout`, `smufl` outrank gaps in UI glue.

Treat the coverage percentage as a gap-finder, never as a grade. Report each gap
in plain words per **How to Write Findings**: name the behavior no test
exercises and describe what a user would see if that behavior were broken. Never
present a bare number as a finding.

## Phase 4: Mutation Testing (only if `--mutation`)

Skip this phase entirely unless the flag was set.

1. For each production class/package in scope, run
   `./scripts/mutation-test.sh <fully-qualified-class-or-package-glob>`.
2. Parse `build/reports/pitest/mutations.xml`. For each mutation, the `status`
   attribute matters:
   - **`SURVIVED`** — the tool deliberately broke a line of production code and
     every test still passed. This is a concrete correctness hole. Report it
     naming the class and line, describing **in plain words what the tool
     changed** (not just the mutator's name), what that change would mean if it
     were a real bug, and which assertion should have caught it.
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
3. **Summarize** what was fixed when done, grouped by axis. Write the summary in
   plain language per **How to Write Findings** — for each fix, say what the test
   checked before, what it checks now, and which bug it would now catch.

### Path B: Interactive mode (default)

1. **Present findings** in one organized summary, grouped by axis (Correctness,
   Usefulness, Coverage), each finding with file:line, the bug it misses, and
   confidence. Lead with the findings where a test passes while the code could
   be broken.

   Rewrite every agent finding in your own words before showing it. Do not pass
   an agent's text through untouched — the agents write for other agents; you
   write for a person who has not read the test or the code it exercises. Apply
   **How to Write Findings** to each one. If you cannot explain a finding
   plainly, you do not understand it well enough to report it — either dig in
   until you can, or drop it.

   Then add a blank line and "Ready for questions."
2. **Clarifying questions** via AskUserQuestion for any finding whose intent is
   ambiguous (e.g. a test that never really checks anything, but might be a
   deliberate "does it blow up?" smoke test). Give the background in the question
   text: what the test does, what looked wrong, and what each answer would cause
   you to do. The answer options must be understandable without looking at the
   code.
3. **Questionable findings** — present suspected false positives via
   AskUserQuestion rather than silently dropping them, saying plainly why you
   think each one is not worth acting on.
4. **Approval** — use AskUserQuestion to present the final list of tests to fix or
   add, and get approval before changing anything. Describe each item in one
   plain sentence naming the actual change, not a category label.
5. **Fix** — after approval, edit/add tests. Any change under `src/main/` or
   `src/test/` requires `./scripts/compile.sh` before re-running; then re-run the
   relevant `./scripts/test.sh <target>` (unit only) to confirm green. Briefly
   summarize what changed, in the same plain language.
