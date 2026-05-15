# Testing Common Conventions

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
