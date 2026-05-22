# Testing Common Conventions

> **Read this section first and treat it as the point of everything below.** The
> Test Quality Principles are the source of truth for what makes a test worth
> writing. The conventions that follow (frameworks, base classes, naming) are
> mechanics in service of these principles — never the other way around.

## Test Quality Principles

A test's value is its ability to fail when the production code is wrong. Coverage
that executes a line without asserting on its behavior proves nothing. For every
test, the governing question is: *what bug would this catch, and could it actually
catch it?* The `check-tests` skill audits tests against the principles below.

### Correctness — is the test itself trustworthy?

- **It must be able to fail.** A test with no assertions, assertions only against
  mocks, a tautology (`assertThat(x).isEqualTo(x)`), or `isNotNull()` on a value
  that cannot be null passes regardless of the code under test.
- **The name must match what it asserts.** A test named for a condition it does
  not actually verify is worse than no test — it lies in the report. See
  [Naming Conventions](#naming-conventions).
- **Test behavior, not implementation.** Over-mocking couples the test to *how*
  the code works, so it breaks on refactors and stays green on real regressions.
  Mock only what the [Unit Test Guide](./testing-unit.md) sanctions; prefer real
  collaborators.
- **No flakiness.** No dependence on timing, execution order (see
  [Test Independence](#test-independence)), shared mutable state, or the real
  clock/filesystem.
- **Preconditions must exercise the path.** A misconfigured fixture can leave a
  test green while testing nothing.

### Usefulness — is the test worth its maintenance cost?

- **Right level for the risk.** Do not E2E-test what a unit test covers cheaply;
  do not unit-test, with everything mocked, something whose entire risk is the
  integration.
- **Guards a realistic failure mode.** Edge cases, boundaries, error paths, and
  null handling carry the value — and since the project bans `Optional` and leans
  on `@Nullable`, null contracts are where bugs hide. Testing trivial getters or
  framework behavior is noise.
- **Non-redundant.** Several tests asserting the same thing add maintenance cost,
  not safety.
- **Diagnostic on failure.** A good failing test localizes the cause. Use specific
  AssertJ matchers over bare booleans so the failure message says what was expected.

### Coverage — where are the gaps?

- **Coverage is necessary, not sufficient.** Treat the percentage as a gap-finder,
  not a grade. High coverage with weak assertions is *false confidence* — the most
  dangerous state of all. Generate a report with `./scripts/coverage.sh`.
- **Branches over lines.** Uncovered branches, error handlers, and exception paths
  matter more than the headline number.
- **"Executed" is not "verified."** A line can be covered yet have no assertion
  observing its effect.
- **Weight by criticality.** Coverage of the document model and core logic (`dom`,
  `io`, `layout`, `smufl`) matters more than UI glue.

### Proving correctness with mutation testing

Reading and coverage can show that a test *looks* weak; only mutation testing
proves it. PIT (`./scripts/mutation-test.sh [target]`) mutates the production
bytecode — flips a `<`, swaps a `+`, returns `null` — and reruns the covering
tests. A surviving mutant is a change to production code that no test detected:
a concrete hole. Mutation testing is the slow path, so it stays scoped to
pure-logic classes and unit tests only (it excludes `songscribe.e2e` and Swing/UI
by default). `check-tests --mutation` folds surviving mutants into its correctness
findings.

## Frameworks

- **JUnit 5** (Jupiter) — test lifecycle and structure. Global config in
  `src/test/resources/junit-platform.properties` runs test classes and methods
  in name order.
- **AssertJ** for assertions (`assertThat(...).isEqualTo(...)`). Prefer AssertJ
  over JUnit's `assertEquals` / `assertTrue` for its readable failure messages.
  JUnit's `assertAll` is fine for grouping independent assertions that should
  all be reported together.
- **Mockito** for mocking (`mock()`, `mockStatic()`, `when()`, `verify()`)
- **AssertJ Swing** for E2E GUI testing (Robot, FrameFixture)

## Test Independence

Classes and methods run in a fixed name order, but that order is an artifact of
the config, not a contract. Never write a test that depends on another test
having run first. The one sanctioned exception is a class that shares a single
mutable fixture across its tests — see [Fixture Ordering](#fixture-ordering).

## Base Classes

**`UnitTest`** (`src/test/java/songscribe/UnitTest.java`) — extend for all unit
tests. Suppresses modal dialogs and provides shared helpers:

- `loadFixture(name)` — parse `src/test/resources/fixtures/{name}.mssw` into a `Song`
- `roundTrip(song)` — serialize a `Song` and reparse it, for save/load fidelity tests
- `minimalSongMock()` / `detachedLine()` — a `Song` mock (mutation tracking
  suspended) and a `Line` backed by one, for model tests that don't need the UI
- `installFlatLafDefaults()` — see [Unit Test Guide](./testing-unit.md)

See [Unit Test Guide](./testing-unit.md).

**`E2ETest`** (`src/test/java/songscribe/e2e/E2ETest.java`) — extend for E2E
tests. Already annotated `@TestInstance(PER_CLASS)`; subclasses inherit it and
must not re-declare it. See [E2E Test Guide](./testing-e2e.md).

## Naming Conventions

- Test classes: `*Test.java`, mirror the source package structure
- Test methods: `test*` prefix describing condition and expected outcome (e.g.,
  `testApplyToNoteAppliesAccidental`)
- `@Nested` classes: name for the condition they group, without a `test*`
  prefix (e.g., `WhenSelectionEmpty`). Use a `@Nested` class only when there are
  multiple related tests to group — never wrap a single test method.
- Alphabetize top-level test methods and `@Nested` class declarations within a
  test class, and test methods within each `@Nested` class.

## Fixture Ordering

The alphabetization rule is overridden when a test class loads a fixture file
once (in `@BeforeAll`) and its tests mutate that shared fixture cumulatively —
each test builds on the state the previous one left behind. Such a class pins
execution order explicitly with `@TestClassOrder` / `@Order` (plus
`@TestInstance(PER_CLASS)` so a non-static `@BeforeAll` can run once per class)
instead of relying on name order. `ElementInsertionTest.java` is the canonical
example; its class header documents why each block runs where it does.

## Testability Over Encapsulation

Prefer widening visibility to package-private over testing through awkward
public APIs:

- A private method that is a self-contained unit worth testing directly → make
  it package-private and test it directly, rather than driving it through a
  public method that needs heavy setup.
- A private constant a test needs → widen it instead of duplicating the literal.
- A private field a test needs to read → add a package-private getter, or widen
  the existing private getter.

This works because test classes mirror the source package, so package-private
members are visible to their tests.
