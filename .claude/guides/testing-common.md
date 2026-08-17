# Testing Common Conventions

**Whether a test should exist at all is decided in
[design.md](/Users/aparajita/.claude/guides/design.md), under *The testing
floor*.** Read that first; most behavior earns no test. This guide is the
mechanics for the ones that do.

## The suite carries nothing

A test verifies a change and is then **moved to the vault**: a separate
repository beside the `develop` working directory, at
`../songscribe-test-vault`, mirroring `src/test/`. `PackageDependencyTest` is
the only test that stays resident — it asserts an invariant over the whole
source tree that any change at all can violate, so there is no one method it
would be fetched back for.

Two reasons, and neither is about the cost of writing tests:

- **Code that is not being changed does not need re-testing.** The chance that
  any particular piece of code is modified is remote. A suite re-verifies all of
  it on every run to catch the fraction that changed, and is paid for in between.
- **A passing suite says nothing about the quality of the architecture or the
  code, and that is the paramount goal.** Green over a bad design reports
  success.

### Before writing a test

**Propose the list first and wait for the user.** Every test — new, added to an
existing class, or rewritten — goes into one table before any test code is
written, giving its justification and what design change would make it
unnecessary. Proposed deletions go in the same table. Format and rationale:
*Propose the tests before writing them* in
[design.md](/Users/aparajita/.claude/guides/design.md).

Wiring carries no tests at all — see *What a dialog may touch* in
[dialogs.md](./dialogs.md), which states that gather, validate and apply carry
none of their own.

**Changing the contract or the implementation of non-UI code requires a test in
that same change.** The suite is not where correctness lives, so the moment of
change is the only moment anything verifies the code against its contract — skip
it and nothing ever does. It applies whether the change is a new method, a
reworded promise, or a rewritten body, and it applies to a promise that looks too
small to break.

### Moving a test to the vault

Once the test passes, move it — with every resource it created or modified — to
the mirrored path in the vault, and take it out of `src/test/`. Commit it there:
the vault is a repository, so a test's history is kept where the test is.

### Fetching a test back

**When only the implementation changed**, the contract still stands and so do
the tests written against it. Find them by searching the vault for calls to the
method, bring them back, and run them. They are expected to pass. A failure
means either the contract is written poorly or the implementation is broken —
never that the test is out of date, because the test was written to the
contract and the contract has not moved.

**When the contract changed**, a new test is written to the new contract and
replaces the vaulted one. Every other vaulted test that calls the method was
also written against the old contract: fetch each one and update it. Then check
every caller of the method in the source — each caller's own contract or
implementation may have to change to match, and each such change carries its own
test obligation. A contract change ripples; an implementation change does not.

Non-UI means everything whose risk is logic, computation, state, data
transformation or model mutation: `dom`, `layout`, `io`, controllers, mutation
records, actions. UI is excluded because a window is verified by opening it —
geometry, focus, tab selection and how a message reads are not things a test
observes. A dialog's populate–gather–ops path is UI in this sense however much
Java it contains.

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

An enum, a small set of states, a pair of flags: cover all of them via
`@ParameterizedTest` over a `@MethodSource` or `@EnumSource`, which costs what
picking two costs. The check runs *before* writing the test, not as a refactor
after near-identical ones pile up: the moment a second `@Test` would be a copy of
a sibling with a different literal, both become rows in one `record`-based case
table. See [Unit Test Guide](./testing-unit.md#parameterized-tests-for-equivalence-classes-and-invariants).

**An enumeration you claim must be one the build can check.** Drive cases from
the domain itself — `@EnumSource`, `Type.values()`, a sealed hierarchy's
permitted subclasses — so a new constant reaches the test on its own. Where a row
needs a hand-built fixture, assert separately that the table's rows are exactly
the domain. A hand-listed table goes on passing while the claim that it is
complete quietly stops being true — only a coverage run catches the gap. See
[Unit Test Guide](./testing-unit.md#asserting-that-a-table-is-exhaustive).

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
- **No flakiness.** No dependence on timing, shared mutable state, the real
  clock, or the real filesystem.
- **No order dependence.** See [Test Independence](#test-independence).

Over-mocking fails the first check in a form that looks like passing: a test
asserting against mocks breaks on refactors and stays green on real regressions.
A test needing extensive mocking to construct its subject is a
constructor-injection finding, not a mocking problem.

## Choosing the level: unit vs. e2e vs. none

A behavior that earns a test is tested at exactly one level.

**Default: unit.** Faster, runs without approval, localizes failures. A behavior
is unit-testable if its risk is logic, computation, state, data transformation,
or model mutation — even when it requires mocking the `MainFrame.getInstance()`
chain or constructing collaborators through a test helper. Format migration,
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

An e2e test is vaulted like any other once it passes. Nothing stays resident on
the grounds that it is expensive to reconstruct.

**None** covers trivial accessors, pure data holders, display and layout wiring
with no branching, framework behavior that cannot regress in our code, and pure
rendering to a `Graphics2D` with no computed geometry to assert.

## Triaging a test fetched from the vault

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

**Never redeclare or duplicate a production constant's literal in test code.** If
a test needs the value, the question is whether the contract should name the
constant, never whether the field should be widened. See
[Contracts](./contracts.md#constants-and-the-contract).

## Frameworks

- **JUnit 5** (Jupiter) — test lifecycle and structure. Global config in
  `src/test/resources/junit-platform.properties` runs test classes and methods
  in name order. `@ParameterizedTest` with `@MethodSource` / `@EnumSource` is the
  normal shape for an enumerated domain or an invariant over many inputs.
- **AssertJ** for assertions (`assertThat(...).isEqualTo(...)`). Prefer AssertJ
  over JUnit's `assertEquals` / `assertTrue` for its readable failure messages — a
  failing test should localize the cause without a debugger. JUnit's `assertAll` is
  fine for grouping independent assertions that should all be reported together.
- **Mockito** for mocking (`mock()`, `mockStatic()`, `when()`, `verify()`)
- **AssertJ Swing** for E2E GUI testing (Robot, FrameFixture)

## MBassador Subscribers

MBassador holds subscribers via weak references. Any test that creates a
non-persistent subscriber (e.g. a local object registered with the bus) MUST
unsubscribe it at the end of the test — in an `@AfterEach`/finally block, not
just at the end of a happy path — to prevent zombie subscribers from lingering
and affecting later tests.

## Test Independence

Classes and methods run in a fixed name order, but that order is an artifact of
the config, not a contract. Never write a test that depends on another test
having run first. The one sanctioned exception is a class that shares a single
mutable fixture across its tests — see [Fixture Ordering](#fixture-ordering).

## Base Classes

**`UnitTest`** (`src/test/java/songscribe/UnitTest.java`) — extend for all unit
tests. Suppresses modal dialogs and provides shared helpers:

- `loadFixture(name)` — load `src/test/resources/fixtures/{name}` into a `Song`, preferring
  a `.musicxml` fixture over a `.mssw` fixture of the same name (see [Fixtures](#fixtures))
- `roundTrip(song)` — serialize a `Song` and reparse it, for save/load fidelity tests
- `minimalSongMock()` / `detachedLine()` — a `Song` mock (mutation tracking
  suspended) and a `Line` backed by one, for model tests that don't need the UI
- `installFlatLafDefaults()` — see [Unit Test Guide](./testing-unit.md)

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

## Fixture Ordering

Test methods run in name order by default, but that order is an artifact of
config, not a contract (see [Test Independence](#test-independence)). A test
class that loads a fixture file once (in `@BeforeAll`) and whose tests mutate
that shared fixture cumulatively — each test building on the state the previous
one left behind — must not depend on that default order. Such a class pins
execution order explicitly with `@TestClassOrder` / `@Order` (plus
`@TestInstance(PER_CLASS)` so a non-static `@BeforeAll` can run once per class),
with its class header documenting why each block runs where it does.
