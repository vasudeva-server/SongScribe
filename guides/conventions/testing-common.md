# Testing Common Conventions

Read `~/.claude/guides/design.md`, under *The testing floor*, first: it decides whether a
test should exist at all.

## What runs

- Find the test classes that cover a change with `jet_brains_find_referencing_symbols` on the
  members that changed.

## Before writing a test

- Propose every test in one table before writing any test code, and wait for the user. The format
  is *Propose the tests before writing them* in `~/.claude/guides/design.md`.
- Include proposed deletions in the same table.
- Write a test in the same change that alters the contract or the implementation of non-UI code,
  because nothing else ever verifies that code against its contract.
- Non-UI means any code whose risk is logic, computation, state, data transformation or model
  mutation: `dom`, `layout`, `io`, controllers, mutation records and actions.
- Never propose a test for UI behavior, and never propose a way to make UI testable: say the
  verification is manual and stop, because a test that needs injection, a mocked singleton, a
  suppressed dialog or a test-only accessor verifies the scaffolding.
- Write no test for wiring; a dialog's controller carries the tests (see
  [dialogs.md](./dialogs.md#rules)).

## When the code under a test changes

- When only the implementation changed, run the existing tests and treat a failure as a poorly
  written contract or a broken implementation, never as an out-of-date test.
- When the contract changed, rewrite every test that calls the method against the new contract
  instead of patching it until it compiles.
- After a contract change, check every caller of the method in the source: each caller's contract
  or implementation may have to change, and each such change carries its own test.

## Deriving the case

- Derive a case from only the method's full contract, its signature, and the public API of its
  declaring class, never from the method body, its private fields or the helpers it calls, because
  a case derived from the code restates the code and breaks on the next refactor.
- When the contract does not answer the question, amend the contract first as a visible change,
  confirmed with the domain owner where the promise is a musical judgment, and derive the case from
  the amended contract.
- Arrange state only through the public API of the declaring class; when it cannot arrange the
  state, that is a constructor-or-factory finding, and reflection into private state is no escape.
- Enumerate every value of a finite domain (an enum, a small set of states, a pair of flags), never
  two representative values.
- Derive the rows from the domain, or assert the table's coverage, so that a constant added later
  reaches the test (see
  [Unit Test Guide](./testing-unit.md#parameterized-tests-for-equivalence-classes-and-invariants)).
- Where the domain is private, do not widen it for the test; that is test-only surface.

## Is the test trustworthy?

- Make the test able to fail: no assertion-free test, no assertion only against mocks, no
  tautology, no `isNotNull()` on a value that cannot be null, and no fixture that fails to reach the
  case.
- Name the test for the contract case it asserts (see [Naming](#naming)).
- Depend on no timing, no real clock and no real filesystem.
- Pin the order of a test that relies on a predecessor (see [Test environments](#test-environments)).
- Treat a test that needs extensive mocking to construct its subject as a constructor-injection
  finding, not a mocking problem.

## Choosing the level

- Verify a behavior in exactly one place.
- Write a unit test when the risk is logic, computation, state, data transformation or model
  mutation, even when it needs an injected mock `MainFrame` or collaborators built by a test helper.
- Escalate to e2e only when the risk is the integration and the behavior cannot be verified with
  collaborators mocked: real event dispatch, wiring across components, behavior observable only
  after a real layout and repaint, or application lifecycle (see
  [E2E Test Guide](./testing-e2e.md)).
- Write a manual check for what only a person can observe: geometry, focus, tab selection, how a
  message reads, and a dialog's populate-gather-ops path (see
  [Manual Verification Guide](./testing-manual.md)).
- Write no test for trivial accessors, pure data holders, display wiring with no branching,
  framework behavior, or pure rendering to a `Graphics2D` with no computed geometry.
- Run an e2e test by name, never as a suite, and never without the user's approval.

## Triaging a test a change surfaced

- Keep a test that asserts a contract case at the right level and can fail.
- Rewrite a test whose case is real but whose level, name, assertions or arrangement is wrong, and
  merge several tests that pin one case into one parameterized test.
- Discard a test that maps to no contract case: a private helper's decomposition, an implementation
  detail the contract does not promise, a guard no caller can reach, or a duplicate of a case.

## Diagnostics

- Run `./scripts/coverage.sh` once as a gap-finder, never as a grade.
- Run `./scripts/mutation-test.sh [target]` to find a test that pins nothing, and read individual
  survivors only in code the contract makes a promise about, because contract tests deliberately
  leave the implementation free to change.
- Never report a coverage or mutation percentage.

## Constants in test code

- Never redeclare a production constant's literal in a test (see
  [Contracts](./contracts.md#constants-and-the-contract)).

## Frameworks

- Use JUnit 5 with `@ParameterizedTest` (`@MethodSource` or `@EnumSource`) for an enumerated domain
  or an invariant over many inputs.
- Never enable parallel execution.
- Use AssertJ rather than JUnit's `assertEquals` or `assertTrue`, because its failure messages
  localize the cause without a debugger; `assertAll` is fine for independent assertions.
- Use Mockito for mocking and AssertJ Swing for e2e GUI tests.

## Test environments

- Build a class's environment once in `@BeforeAll` and keep it stable through its tests; reset
  per-test state in the class's own `@BeforeEach`.
- Put a test that must run alone in a class of its own, because a class runs in isolation and a
  test within a class does not.
- Pin the order of a class whose tests rely on a predecessor with
  `@TestMethodOrder(OrderAnnotation.class)` and `@Order` on each method, because the default order is
  alphabetical and relying on it silently is wrong.
- Walk a scenario in one test method when it must run alone; use separate ordered methods when
  per-step reporting matters.
- Split tests that each need a different environment into `@Nested` classes with
  `@TestInstance(PER_CLASS)`, one per environment; a nested class shares the outer class's bus and
  frame.
- Dispose a listener that a test or nested class registers, in `@AfterEach`, `@AfterAll` or
  `finally`, never only on the happy path, because the bus holds subscribers weakly and the listener
  stays registered until collected.
- Clear a shared mock's invocations before a test that verifies a call count.
- Put a process singleton into the state a class needs through the operation the application uses
  for it, never through a method that exists for tests; do not add `deinitialize` or `resetForTest`
  to a singleton.
- Post `DocumentDidLoadNotification` for a song a class builds, so the undo history returns to its
  baseline.

## Base classes

- Extend `UnitTest` for every unit test, and do not re-declare `@TestInstance(PER_CLASS)`.
- Use `MockEnvHelper.setupMockEnv()` for a class that takes the frame as a constructor or factory
  parameter (see [Unit Test Guide](./testing-unit.md#the-mainframe-is-injected-never-mocked-as-a-singleton)).
- Extend `E2ETest` for every e2e test, and do not re-declare `@TestInstance(PER_CLASS)`.

## Naming

- Name test classes `*Test.java` and mirror the source package.
- Prefix a test method with `test` and name the contract case: the condition and the promised
  outcome, not the method called.
- Name a `@Nested` class for the condition it groups, without a `test` prefix, and use one only to
  group several related tests.

## Fixtures

- Write every new fixture in `.musicxml` format; add or use an `.mssw` fixture only when the subject
  is the legacy reader or the format migration (see [CLAUDE.md](../../CLAUDE.md)).
- Use `UnitTest.fixtureFile` only when the legacy reader itself is the subject.
- Keep a table of inputs and expected outputs inline in the test class, in Java, because a resource
  file turns the columns into string parsing and needs an untested parser.
- Keep a fixture file for a document only: a song the reader under test must read.
