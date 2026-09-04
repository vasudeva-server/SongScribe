# Testing Common Conventions

**Whether a test should exist at all is decided in
[design.md](~/.claude/guides/design.md), under *The testing
floor*.** Read that first; most behavior earns no test. This guide is the
mechanics for the ones that do.

## What runs

Tests live in `src/test/`, next to the code. **The suite is dormant.** A test
run names the classes covering the code just written or changed, and nothing
else:

```bash
./scripts/test.sh BindingsTest ObservableValueTest
```

To find them, run `jet_brains_find_referencing_symbols` on the members that
changed and take the test classes out of the results — the same lookup that
decides which tests need rewriting.

**The whole suite runs only when the user asks for it, and only the user can
start it.** `.claude/hooks/no-full-test-suite.sh` denies a bare
`./scripts/test.sh`, a bare `unit` or `e2e`, more than seven classes in one
command, a loop driving the script, and Gradle's test tasks. There is no flag or
sentinel that lifts it: the user runs the suite by typing `!./scripts/test.sh`,
which the CLI executes directly and no hook sees. Ask for that rather than
looking for a way around the hook. `scripts/test.sh` separately refuses pattern
targets, so a wildcard cannot stand in for naming the classes.

Two reasons, and neither is about the cost of writing tests:

- **Code that is not being changed does not need re-testing.** The chance that
  any particular piece of code is modified is remote. A suite re-verifies all of
  it on every run to catch the fraction that changed, and is paid for in between.
- **A passing suite says nothing about the quality of the architecture or the
  code, and that is the paramount goal.** Green over a bad design reports
  success. A pass count is never reported as evidence that the work is right.

**Compile the test tree on every change** — `./scripts/compile.sh --test`. With
the suite dormant this is the always-on check, and it is what catches a test
still pinning something that has been deleted or renamed. It is not a preamble
to testing: `./scripts/test.sh` builds both trees itself, so compiling before a test run
does the same work twice.

### Before writing a test

**Propose the list first and wait for the user.** Every test — new, added to an
existing class, or rewritten — goes into one table before any test code is
written, giving the promise it checks and which kind of test it is. Proposed
deletions go in the same table. Format and rationale:
*Propose the tests before writing them* in
[design.md](~/.claude/guides/design.md).

Wiring carries no tests at all — see *The rule* in
[dialogs.md](./dialogs.md#the-rule), which states that a dialog's own steps —
populate, gather, call the ops — carry none of their own, and that what carries
tests is the controller.

**Changing the contract or the implementation of non-UI code requires a test in
that same change.** The suite is not where correctness lives, so the moment of
change is the only moment anything verifies the code against its contract — skip
it and nothing ever does. It applies whether the change is a new method, a
reworded promise, or a rewritten body, and it applies to a promise that looks too
small to break.

Non-UI means everything whose risk is logic, computation, state, data
transformation or model mutation: `dom`, `layout`, `io`, controllers, mutation
records, actions. UI is excluded because a window is verified by opening it —
geometry, focus, tab selection and how a message reads are not things a test
observes. A dialog's populate–gather–ops path is UI in this sense however much
Java it contains.

**Never propose a test for UI behavior, and never propose a way to make UI
testable.** Dialogs, focus traversal, input verifiers and renderers carry no
tests. When a UI promise has no test, say the verification is manual and stop. Do
not offer injection, a mocked singleton, a suppressed dialog or a test-only
accessor as the route to a test — a test that needs one of those verifies the
scaffolding, not the promise. Plans in this repo assign that verification to a
manual phase on purpose.

The line is whether the assertion needs a window, focus or a dialog. Model logic
reachable without one — a combo's list contents, a mapping function — is still
fair game.

### When the code under a test changes

Find the affected tests with `jet_brains_find_referencing_symbols` on the members
that changed. What happens to them turns on which thing moved:

**When only the implementation changed**, the contract still stands and so do the
tests written against it. Run them; they are expected to pass. A failure means
either the contract is written poorly or the implementation is broken — never
that the test is out of date, because the test was written to the contract and
the contract has not moved.

**When the contract changed**, every test that calls the method was written
against the old contract. Rewrite each one to the new contract rather than
patching it until it compiles — a test that was edited into passing has stopped
asserting anything anyone chose. Then check every caller of the method in the
source: each caller's own contract or implementation may have to change to match,
and each such change carries its own test obligation. A contract change ripples;
an implementation change does not.

### Deriving the case

**The contract is the durable artifact.** It is what a future change is checked
against, so a contract too vague to derive a test from is the finding — not a
reason to keep the test around as the real specification. Amend the contract
first, as *When the contract does not answer the question* below requires, then
derive the case from the amended one.

Then read exactly three things: the **contract** of the method under test, its
**signature**, and the **public API of its declaring class** — other contracts on
it, the class Javadoc, and any `docs/` document they link to.

Not the method body, not its private fields, not the helpers it calls. A case
derived from those passes by construction: it restates the code back to itself
and reports the agreement as verification. It is also the test that breaks on the
next refactor having observed nothing that changed.

**When the contract does not answer the question, the finding is against the
contract**, not a license to open the body. Amend the contract — proposed and
confirmed with the domain owner where the promise is a musical judgment — then
derive the case from the amended contract.

You will read production code anyway when triaging or diagnosing. The rule that
survives:

> A case you learned from the implementation does not become a test until it is
> in the contract. Put it in the contract first, as a visible change.

The same boundary decides **arrangement**. If the declaring class's public API
cannot arrange the state a case needs, that is a constructor-or-factory finding.
Reaching past the public API to set up a test is the same violation as writing
the test from the body, and reflection into private state is not an escape from
it.

### Enumerate a finite domain

An enum, a small set of states, a pair of flags: cover **all** of them, never two
representative values, because enumerating a finite domain costs what picking two
costs and picking two is what leaves the third one broken. **An enumeration you
claim must also be one the build can check**, so that a constant added later
reaches the test on its own rather than leaving a table that goes on passing
while the claim of completeness quietly stops being true.

The shapes for both — deriving rows from the domain, and asserting a hand-built
table's coverage when a row needs a fixture that cannot be derived — are in the
[Unit Test Guide](./testing-unit.md#parameterized-tests-for-equivalence-classes-and-invariants).
The check runs *before* writing the test, not as a refactor after near-identical
ones pile up.

Where the domain is a private taxonomy, no assertion can reach it, and widening
it so a test can is the no-test-only-surface violation rather than the way out.

## Is the test trustworthy?

- **It can fail.** No assertions, assertions only against mocks, a tautology
  (`assertThat(x).isEqualTo(x)`), or `isNotNull()` on a value that cannot be null
  passes regardless of the production code. A misconfigured fixture does the same
  thing more quietly — check that the arrangement actually reaches the case.
- **Its name names the contract case it asserts.** Not the method it calls and
  not the setup it performs: the promise being checked. See
  [Naming Conventions](#naming-conventions).
- **No flakiness.** No dependence on timing, the real clock, or the real
  filesystem. State shared within a class is the class's environment, not
  flakiness; see [Test Environments](#test-environments).
- **No unpinned order dependence.** A test that relies on a predecessor runs in
  a class that pins its order. See [Test Environments](#test-environments).

Over-mocking fails the first check in a form that looks like passing: a test
asserting against mocks breaks on refactors and stays green on real regressions.
A test needing extensive mocking to construct its subject is a
constructor-injection finding, not a mocking problem.

## Choosing the level: unit vs. e2e vs. manual vs. none

A behavior is verified in exactly one place.

**Default: unit.** Faster, runs without approval, localizes failures. A behavior
is unit-testable if its risk is logic, computation, state, data transformation,
or model mutation — even when it requires an injected mock `MainFrame` or
collaborators built through a test helper. Format migration,
serialization round-trips, layout geometry, MIDI generation, action enablement,
selection state machines, mutation records, derived model state.

**Escalate to e2e ONLY when the risk *is* the integration** and the behavior
cannot be meaningfully verified with collaborators mocked:

- real mouse/keyboard event dispatch (click, drag, shift-click, type),
- cross-component wiring where the bug lives in the wiring (action → model
  mutation → layout invalidation → repaint → selection reflection),
- behavior only observable after a real layout/repaint cycle,
- application lifecycle (boot, shutdown, file open/save through the UI).

If everything that matters can be asserted with the singleton mocked, it is not
an e2e case. E2E proves the wiring, one test per path. See
[E2E Test Guide](./testing-e2e.md).

An e2e test lives beside the unit tests like any other and is run by name for
the same reason — never as a suite, and never without the user's approval.

**Manual** covers what only a person can observe — geometry, focus, tab selection,
how a message reads, and a dialog's populate–gather–ops path. It becomes a numbered
check in a checklist under `src/test/manual/`, never a test, and a behavior a check
covers is not also tested. See [Manual Verification Guide](./testing-manual.md).

**None** covers trivial accessors, pure data holders, display and layout wiring
with no branching, framework behavior that cannot regress in our code, and pure
rendering to a `Graphics2D` with no computed geometry to assert.

## Triaging a test a change surfaced

- **keep** — it asserts a contract case, at the right level, and can fail.
- **rewrite** — the case is real but the test is wrong about it: wrong level, a
  name that does not name the case, assertions against mocks, an arrangement that
  does not reach the case, or several tests pinning one case a parameterized test
  states once.
- **discard** — it maps to no contract case. That includes a test of a private
  helper's decomposition, a test pinning an implementation detail the contract
  promises nothing about, a test of a guard no caller can reach, and a duplicate
  of a case already asserted.

## Diagnostics

**Coverage** (`./scripts/coverage.sh`) answers *did this code run?* It is a
gap-finder run once, never a grade — see design.md for the one question to ask of
each unexecuted region.

**Mutation** (`./scripts/mutation-test.sh [target]`) answers *does anything
observe what this code produces?* It mutates production bytecode and reruns the
covering tests, which finds a test that pinned nothing useful. **A high
surviving-mutant count is the expected, healthy state**, because contract tests
deliberately leave the implementation free to change. Read individual survivors
in code the contract makes a promise about; never report the percentage.

## Constants in test code

A test never redeclares a production constant's literal — see
[Contracts](./contracts.md#constants-and-the-contract) for the rule and what to
do instead.

## Frameworks

- **JUnit 5** (Jupiter) — test lifecycle and structure. Global config in
  `src/test/resources/junit-platform.properties` runs test classes and methods
  in name order; that order is a config artifact, and a class that relies on
  order pins it (see [Test Environments](#test-environments)). Parallel execution
  is never enabled. `@ParameterizedTest` with `@MethodSource` / `@EnumSource` is
  the normal shape for an enumerated domain or an invariant over many inputs.
- **AssertJ** for assertions (`assertThat(...).isEqualTo(...)`). Prefer AssertJ
  over JUnit's `assertEquals` / `assertTrue` for its readable failure messages — a
  failing test should localize the cause without a debugger. JUnit's `assertAll` is
  fine for grouping independent assertions that should all be reported together.
- **Mockito** for mocking (`mock()`, `mockStatic()`, `when()`, `verify()`)
- **AssertJ Swing** for E2E GUI testing (Robot, FrameFixture)

## Test Environments

A test class defines one environment: the message bus, the frame, the action
constants, the fixtures its tests need. It builds that environment once, in
`@BeforeAll`, and keeps it stable through its tests. The harness never rebuilds
application state per test. A class that resets one piece of state before each
test does so in its own `@BeforeEach`, as part of the environment it defines.

- **A class is guaranteed to run in isolation. A test within a class is not.** A
  test may rely on the state a predecessor left behind. A test that must run
  alone belongs in a class of its own. To diagnose a failure, run the class.
- **A test that relies on a predecessor is order-dependent, and the class pins
  the order** with `@TestMethodOrder(OrderAnnotation.class)` and `@Order` on each
  method. JUnit has no declaration-order orderer, and the `MethodName` default in
  `junit-platform.properties` is alphabetical; a class that relies on that
  alphabetical order silently is wrong. A class that shares an environment
  without relying on order needs no annotation.
- **A sequence has a second shape:** one test method that walks the scenario,
  asserting at each step. It gives up per-step reporting and gains the ability
  to run alone. Choose by which of those the scenario needs.
- **Where many tests in one class each need a different environment, split them
  into `@Nested` classes, one per environment.** A nested class builds its
  additional state in its own `@BeforeAll`, which needs
  `@TestInstance(PER_CLASS)` on the nested class, and shares the outer class's
  bus and frame.
- **The bus belongs to the top-level class.** `UnitTest` installs a fresh
  recording bus in `@BeforeAll` and retires it in `@AfterAll`, so a listener one
  class leaks cannot hear another class's posts. Within a class, MBassador holds
  subscribers weakly, so a listener a test or a nested class registers on its
  own stays registered until collected. Whoever registered it disposes it, in an
  `@AfterEach`, `@AfterAll` or `finally` block, never only on the happy path.
- **A shared mock accumulates invocations across the class.** A test that
  verifies a call count clears the mock's invocations first.
- **A process singleton is put into the state a class needs by the operation the
  application uses for it, never by a method that exists for tests.** Undo
  history returns to its baseline when a document loads, so a class posts
  `DocumentDidLoadNotification` for the song it built, as `ScoreView.setSong`
  does. Production has no `deinitialize` or `resetForTest` on a singleton, and
  none is added.

## Base Classes

**`UnitTest`** (`src/test/java/songscribe/UnitTest.java`) — extend for all unit
tests. Annotated `@TestInstance(PER_CLASS)`, so a subclass's `@BeforeAll` may be
an instance method and must not re-declare it. Installs the class's message bus,
suppresses modal dialogs and provides shared helpers:

- `loadFixture(name)` — load `src/test/resources/fixtures/{name}` into a `Song`, preferring
  a `.musicxml` fixture over a `.mssw` fixture of the same name (see [Fixtures](#fixtures))
- `minimalSongMock()` / `detachedLine()` — a `Song` mock (mutation tracking
  suspended) and a `Line` backed by one, for model tests that don't need the UI
- `installFlatLafDefaults()` — see [Unit Test Guide](./testing-unit.md)

**`MockEnvHelper`** (`src/test/java/songscribe/ui/action/MockEnvHelper.java`) —
`setupMockEnv()` builds a mock `MainFrame` with a score view, selection
coordinator and controller behind it, for a class that takes the frame as a
constructor or factory parameter. It does not stub `MainFrame.getInstance()`.
See [Unit Test Guide](./testing-unit.md#the-mainframe-is-injected-never-mocked-as-a-singleton).

**`E2ETest`** (`src/test/java/songscribe/e2e/E2ETest.java`) — extend for E2E
tests. Already annotated `@TestInstance(PER_CLASS)`; subclasses inherit it and
must not re-declare it. See [E2E Test Guide](./testing-e2e.md).

## Naming Conventions

- Test classes: `*Test.java`, mirror the source package structure
- Test methods: `test*` prefix naming the **contract case** — the condition and the
  promised outcome, not the method called (e.g.
  `testApplyToNoteAppliesAccidental`, `testTicksThrowsForTypeWithNoTickMapping`)
- `@Nested` classes: name for the condition they group, without a `test*`
  prefix (e.g., `WhenSelectionEmpty`). Use a `@Nested` class only when there are
  multiple related tests to group — never wrap a single test method.

## Fixtures

Fixture files live in `src/test/resources/fixtures/`. Always write a new fixture in
`.musicxml` format — the current storage format. The only exception is a test whose
subject is the legacy `.mssw` reader or the format migration itself; only that kind of
test should add or use a `.mssw` fixture (see [CLAUDE.md](../../CLAUDE.md)).
`UnitTest.loadFixture` / `E2ETest.loadFixture` prefer a `.musicxml` fixture over a
`.mssw` fixture of the same name, so the remaining legacy-reader fixtures keep loading
unchanged. Those are the only `.mssw` fixtures left: `damaged`, `newer-version`,
`lyrics-date-invalid`, and `full-line`. Reach for `UnitTest.fixtureFile` (`.mssw`-only)
just when the legacy reader itself is the subject.

A fixture loaded once in `@BeforeAll` is part of the class's environment, and a
class whose tests mutate it cumulatively pins its order as
[Test Environments](#test-environments) states.
