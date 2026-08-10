# Test Review Agents, Coverage, and Mutation

Runs only when the **test scope** is non-empty. Audit tests against the **Test
Quality Principles** in `.agents/guides/testing-common.md` — that section is the
source of truth for what these axes enforce — and apply the conventions in
`.agents/guides/testing-unit.md`.

The test axes report in priority order:

1. **Correctness** — can each test actually fail when the production code is
   wrong?
2. **Usefulness** — does each test guard a realistic failure mode, at the right
   level, without redundancy?
3. **Coverage** — what behavior, branches, and error paths are unverified?

A test that gives **false confidence** — passes while the code is broken, or
covers a line without asserting on its effect — is the highest-severity test
finding. Rank by that lens, not by volume.

Cutting across all three: when a test's problem is really the production
design's problem, **the design is the finding**. See
`.agents/skills/check/reference/design-flaws.md`.

These axes audit **unit tests only**; they never run the e2e suite. When
`--mutation` is set, PIT runs the covering **unit** tests against mutated
production code — this does not invoke e2e and needs no e2e approval.

## Agents

Three agents, spawned against the test scope and their production counterparts.
Spawn Correctness and Usefulness with `model: "sonnet"`. Spawn **Testability and
Design with `model: "opus"`**: root-cause design analysis is the one axis where
a smaller model reliably returns a plausible-sounding workaround — one more
mock, one more accessor — instead of the actual cause.

Open every prompt with `.agents/skills/check/reference/agent-preamble.md`. Pass
each agent the scoped test files and their production counterparts, and instruct
it to apply the Test Quality Principles from `.agents/guides/testing-common.md`.
Each finding must name the file and line, state **what bug the test fails to
catch**, and rate confidence (high / medium / low).

### Agent 1: Correctness

For every test in scope, determine whether it can fail when the code is wrong:

1. **Cannot-fail tests** — no assertions; assertions only against mocks;
   tautologies; `isNotNull()` on a non-nullable value; assertions on a stubbed
   return value.
2. **Name vs. behavior mismatch** — the test name describes a condition or
   outcome the body does not actually assert.
3. **Implementation coupling** — over-mocking that verifies *how* the code works
   rather than its observable behavior; tests that would break on a pure
   refactor.
4. **Flakiness risk** — dependence on timing, execution order, shared mutable
   state, the real clock, or the filesystem.
5. **Dead preconditions** — setup that does not actually drive the path the test
   claims to cover.

### Agent 2: Usefulness

For the same tests:

1. **Wrong level** — unit-testing pure integration risk with everything mocked,
   or duplicating cheap unit coverage in a heavier test.
2. **Low-value targets** — tests of trivial getters and setters, or of framework
   behavior that cannot regress.
3. **Missing high-value cases** — absent edge cases, boundaries, error paths,
   and `@Nullable` contracts (recall the project bans `Optional`).
4. **Redundancy** — multiple tests asserting the same thing.
5. **Poor diagnostics** — bare boolean assertions where a specific AssertJ
   matcher would localize the failure.

### Agent 3: Testability and Design

Spawn with `model: "opus"`. Its mandate is different in kind from the other two:
they judge the tests, it judges what the tests reveal about the production code.
Give it the tests in scope and their production counterparts, and this question
— *where do these tests strain, and what about the production design is making
them strain?*

1. **Hidden dependencies** — the class under test obtains its collaborators
   itself (singletons, statics, global lookups, `new` in a constructor) instead
   of receiving them, so a test cannot substitute one without fighting the
   language.
2. **Unreachable behavior** — an error path or branch no caller can provoke from
   outside, so it can only be covered by reaching into internals.
3. **Doing too much** — a method whose test needs several unrelated fixtures,
   which means the method has several unrelated responsibilities.
4. **Test-only surface** — production API that exists solely because a test
   needed it, which is the design admitting the seam is in the wrong place.
5. **Interaction-only verification** — tests forced to assert on calls because
   the code returns nothing observable; usually the result should be a value
   rather than a side effect.
6. **Fixture gravity** — a shared setup every test in the file must inherit
   whether or not it needs it, so tests are coupled to each other through state
   the production design forced into existence.
7. **Tests that ratify a flaw** — the suite has absorbed a design mistake and
   now protects it: constants named for the fact that a value is ignored,
   assertions over whole composite values whose parts are not all real, an
   emptiness check standing in for a state the type cannot express. These tests
   make the mistake look intentional and make correcting it look expensive.
   Report the design flaw and count these tests as *removals*, not as breakage.

For every value these tests assert on, establish **who reads it in production**
before judging the assertion — per *Before proposing a fix, find out who reads
the value* in `design-flaws.md`. A value asserted by tests and read by nothing
is a production finding wearing test clothes.

Report the flaw, the tests it explains, the corrected design, and what the
change touches, per *Reporting a design finding*, and say which tests become
simple or
unnecessary once it is fixed. Consult the design notes first, and return no
findings when the production code is straightforward to test, per *Check the
design notes before reporting*.

## Coverage

Generate a fresh JaCoCo report scoped to the tests under review:

- Run `./scripts/coverage.sh unit` for a package-level audit, or
  `./scripts/coverage.sh <TestClass...>` for specific classes.
- Read the per-class numbers from
  `build/reports/jacoco/test/jacocoTestReport.xml` for the **production
  classes** identified during scope resolution.

Report, per production class in scope:

- **Uncovered branches and error paths** — weighted above uncovered lines.
- **Executed-but-unverified lines** — the test runs this code but never checks
  what it produced, so a wrong result would go unnoticed (cross-reference the
  Correctness agent's findings).
- **Criticality** — gaps in `dom`, `io`, `layout`, `smufl` outrank gaps in UI
  glue.

Treat the coverage percentage as a gap-finder, never as a grade. Report each gap
in plain words per `findings.md`: name the behavior no test exercises and
describe what a user would see if that behavior were broken. Never present a
bare number as a finding.

## Mutation Testing (only if `--mutation`)

Skip entirely unless the flag was set.

1. For each production class or package in scope, run
   `./scripts/mutation-test.sh <fully-qualified-class-or-package-glob>`.
2. Parse `build/reports/pitest/mutations.xml`. For each mutation, the `status`
   attribute matters:
   - **`SURVIVED`** — the tool deliberately broke a line of production code and
     every test still passed. This is a concrete correctness hole. Report it
     naming the class and line, describing **in plain words what the tool
     changed** (not just the mutator's name), what that change would mean if it
     were a real bug, and which assertion should have caught it.
   - **`NO_COVERAGE`** — no test exercised the mutated line; fold into the
     coverage gaps above.
   - `KILLED` / `TIMED_OUT` — the suite caught it; no finding.
3. If the run errors before producing a report, report the failure verbatim and
   continue with the other axes rather than silently dropping this one.
