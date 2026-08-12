# Testing Common Conventions

> **Read the first two sections and treat them as the point of everything below.**
> The contract decides *what* is tested and when testing it is finished. The
> conventions that follow — level rubric, frameworks, base classes, naming,
> fixtures — are mechanics in service of that, never the other way around.

## Tests come from the contract

The completeness criterion is the contract, not the implementation and not a
percentage. For every nontrivial API, test each materially distinct class of
behavior the contract promises, plus its boundaries and its invariants. **That set
is also the stopping rule** — the contract's classes are what *done testing this*
means. Whether the method holds 5 branches or 25 is not an input.

So the first question about any test is not *what bug would this catch?* but
**which clause of the contract does this assert?** A test that answers neither is
discarded, not kept on the theory that it might catch something.

If the API has no contract yet, write the contract first. See
[Contracts](./contracts.md) for what a method contract states in Java and how the
test cases fall out of it, and the **Contracts** and **Contract-Driven Testing**
sections of `~/.claude/rules/development.md` for the principles.

### Write from the contract, not from the code

Before writing a test, read exactly three things:

1. the **contract** of the method under test,
2. its **signature**,
3. the **public API of its declaring class** — the other contracts on it, the
   class Javadoc, and any `docs/` document those link to.

Nothing else. Not the method body, not its private fields, not the helpers it
calls. Those are what the contract deliberately does not promise, and a case
derived from them passes by construction: it restates the code back to itself and
reports the agreement as verification. It is also the case that breaks on the next
refactor, having observed nothing that changed.

Those three sources are what a caller has. Writing from them is what makes the
test a caller, which is the only position the promise can be checked from.

**When the contract does not answer the question, the finding is against the
contract.** Not a license to open the body. Amend the contract — proposed and
confirmed with the domain owner where the promise is a musical judgment, never
decided unilaterally — then derive the case from the amended contract.

You will read production code anyway: triaging an existing suite, diagnosing a
failing test, and reading a coverage report all require it. The rule that survives
is the one that keeps the contract the only source of cases:

> A case you learned from the implementation does not become a test until it is in
> the contract. Put it in the contract first, as a visible change; the test then
> derives from a stated promise like every other one.

The same boundary decides **arrangement**, which is where the pressure actually
comes from. If the declaring class's public API cannot arrange the state a case
needs, that is a constructor-or-factory finding, not a reason to reach further in.
Reaching past the public API to set up a test is the same violation as writing the
test from the body.

Four consequences worth stating in test-guide terms:

- **Enumerate a finite domain; sample only when you cannot.** An enum, a small set
  of states, a pair of flags — all of them, via `@ParameterizedTest` over a
  `@MethodSource` or `EnumSource`, which costs what picking two costs. Picking two
  is what leaves the third one broken. This is a check that runs *before* writing
  a test, not a refactor applied after several near-identical ones pile up: the
  moment a second `@Test` would be a copy of a sibling with a different literal or
  a different small piece of arrange/edit code, both become rows in one
  `record`-based case table instead. See [Unit Test Guide](./testing-unit.md#parameterized-tests-for-equivalence-classes-and-invariants).
- **An enumeration you claim must be one the build can check.** Drive the cases
  from the domain — `@EnumSource`, `Type.values()`, a sealed hierarchy's permitted
  subclasses — so a new constant reaches the test on its own. Where each row needs
  a hand-built fixture that cannot be derived that way, assert separately that the
  table's rows are exactly the domain. A hand-listed table goes on passing while
  the sentence claiming it is complete quietly stops being true; nothing connects
  the two. See [Unit Test Guide](./testing-unit.md#asserting-that-a-table-is-exhaustive).
- **A representative of each distinct input class, plus the extremes, is
  sufficient.** Volume past that is cost without safety.
- **Where the contract is an invariant, test the invariant** across many
  representative inputs, rather than pinning one expected output per input. A
  promise that the outputs sum to the requested total is one test, not a table.
- **Not every clause yields a test.** A guard the contract deliberately makes
  unreachable through the public API has no test and is not a gap.

Two rules from the global regime that decide test *structure* here, restated only
as pointers because they are stated in full there:

- **No production surface exists solely for tests** — no method, no accessor, no
  widened field, no relaxed visibility, and no reflection into private state. When
  a test cannot arrange the state it needs, the answer is a constructor or factory
  that takes that state, used by production too.
- **Private helpers are tested through the contract they serve.** A helper is
  promoted to an internal API when it has become a distinct concept with its own
  contract — never because it is long or awkward to reach.

## Is the test trustworthy?

A short list, applied to every test once its contract case is settled.

- **It can fail.** No assertions, assertions only against mocks, a tautology
  (`assertThat(x).isEqualTo(x)`), or `isNotNull()` on a value that cannot be null
  passes regardless of the production code. A misconfigured fixture does the same
  thing more quietly — check that the arrangement actually reaches the case.
- **Its name names the contract case it asserts.** Not the method it calls and not
  the setup it performs: the promise being checked. A test whose name does not
  identify its promise cannot be triaged later against a contract that has changed,
  and it lies in the report. See [Naming Conventions](#naming-conventions).
- **No flakiness.** No dependence on timing, shared mutable state, the real clock,
  or the real filesystem.
- **No order dependence.** See [Test Independence](#test-independence).

Over-mocking fails the first check in a form that looks like passing: a test that
asserts against mocks breaks on refactors and stays green on real regressions.
Mock only what the [Unit Test Guide](./testing-unit.md) sanctions, and read a test
that needs extensive mocking to construct its subject as a constructor-injection
finding rather than a mocking problem.

## The testing-approach Javadoc

**Every test class carries a Javadoc comment stating the contract it tests and how
it tests it** — which equivalence classes it covers, which boundaries, which
invariants. It goes on the **test class**, never on the production method; the
production Javadoc states the promise, the test class states how the promise is
exercised.

It is written *before* the tests, from the contract, and it is what a later reader
triages the class against. A class Javadoc that lists inputs rather than promises
is the coverage habit in prose form:

```java
// Inputs, not promises — says what runs, not what is guaranteed
/**
 * Verifies {@link NoteTypeMapping#ticks(ElementType, int)} over all six
 * note-value types × three dot counts (0, 1, 2), asserting exact integer
 * results and correct tick values, and checks that GRACE_QUAVER throws.
 */

// The contract's clauses, one line each, and where each is exercised
/**
 * Exercises the contract of {@link NoteTypeMapping#ticks(ElementType, int)}.
 *
 * <p><b>Valid domain</b> — enumerated, not sampled: every {@link ElementType}
 * with a tick mapping × every dot count in
 * {@code 0..}{@value NoteTypeMapping#MAX_DOT_COUNT}.
 *
 * <p><b>Invariants</b> — the dot formula ({@code ×3/2} for one dot,
 * {@code ×7/4} for two) holds for every type, and every result in the valid
 * domain is an exact integer, which is the promise
 * {@link NoteTypeMapping#DIVISIONS} exists to keep.
 *
 * <p><b>Boundaries</b> — {@code dotCount} at 0 and at
 * {@link NoteTypeMapping#MAX_DOT_COUNT} succeed; one below and one above
 * both throw.
 *
 * <p><b>Errors</b> — a type with no tick mapping throws
 * {@code IllegalArgumentException}. The {@code ArithmeticException} clause has
 * no test: no input in the valid domain can reach it.
 */
```

Where a contract case is deliberately **not** tested, the class Javadoc is where
that is recorded, with the reason — as the last paragraph above does. An untested
case with a stated reason is a decision; an untested case with no note is a gap.

**A phrase like "enumerated, not sampled" is a claim, and this is the comment
where it gets written.** Do not write it unless something in the class fails when
the domain grows: cases driven from `@EnumSource` / `values()` / a sealed
hierarchy, or an explicit assertion that the table's rows are exactly the domain.
This is not a hypothetical failure mode — `OpNamesTest` carried the sentence for
three domains it did not actually enumerate, and only a coverage run found it,
because a claim of completeness reads as its own evidence.

Where the domain is a private taxonomy, no assertion can reach it, and widening it
so a test can is the [no-test-only-surface](#write-from-the-contract-not-from-the-code)
violation rather than the way out. Either the taxonomy becomes part of the
contract, or the comment states what it actually covers — "one case per category"
— and does not claim enumeration.

## Choosing the level: unit vs. e2e vs. none

Every testable behavior is tested at exactly one level. The contract decides
*whether* a behavior is tested and what the cases are; this rubric decides only
*where* the test lives.

### Default: unit

Prefer a unit test. Unit tests are faster, run without approval, and localize
failures. A behavior is unit-testable if its risk is **logic, computation, state,
data transformation, or model mutation** — even when it requires mocking the
`MainFrame.getInstance()` singleton chain or constructing collaborators through a
test helper (see `testing-unit.md`).

Examples that are **unit**: format migration, serialization round-trips, layout
geometry/stacking math, MIDI generation, action enablement logic, selection state
machines, mutation records, derived model state, `@Nullable` contracts.

### Escalate to e2e ONLY when the risk *is* the integration

Use an e2e test only when the behavior **genuinely requires the real Swing
pipeline** and cannot be meaningfully verified with collaborators mocked:

- real mouse/keyboard event dispatch (click, drag, shift-click, type),
- cross-component integration where the bug lives in the wiring (action →
  model mutation → layout invalidation → repaint → selection reflection),
- behavior only observable after a real layout/repaint cycle,
- application lifecycle (boot, shutdown, file open/save through the UI).

If everything that matters can be asserted with the singleton mocked, it is
**not** an e2e case — putting it in e2e is the wrong level. E2E proves the wiring,
one test per path; the contract's cases are exercised at unit level. See
[E2E Test Guide](./testing-e2e.md).

### Classify as none (no test warranted)

- trivial getters/setters with no logic,
- pure data holders (most `message.mutation` / `message.command` /
  `message.notification` records, unless they carry derivation logic),
- pure display/layout wiring with no branching logic (most dialogs, menus),
- framework behavior that cannot regress in our code,
- pure rendering to a `Graphics2D` with no computed geometry to assert
  (the geometry, if any, is unit-tested upstream).

### Triaging an existing test

Every existing test resolves to one of three outcomes, decided against the
contract:

- **keep** — it asserts a contract case, at the right level, and can fail.
- **rewrite** — the case is real but the test is wrong about it: wrong level,
  a name that does not name the case, assertions against mocks, an arrangement
  that does not reach the case, or several tests pinning one case that a
  parameterized test states once.
- **discard** — it maps to no contract case. That includes a test of a private
  helper's decomposition, a test that pins an implementation detail the contract
  promises nothing about, and a duplicate of a case already asserted.

A case the contract promises and no test asserts is the fourth finding —
**missing** — and it is a finding against the suite, not against the contract.

## Diagnostics: coverage and mutation

Neither runs as a routine phase and neither produces a number reported as a grade,
but they are not peers: coverage closes a package's contract pass, mutation is
opportunistic.

**Coverage** (`./scripts/coverage.sh`) answers *did this code run?* **Run it once,
scoped to the package's own test classes, as the closing step of every contract
pass** — not as an optional extra. It is the only thing that catches a contract
claiming a domain it does not cover, because that claim reads as its own evidence
and re-reading it confirms it. In the `undo` pilot one scoped run produced 22 of
the package's 161 final cases, all of them behind Javadoc asserting the domains
were already enumerated in full.

Ask of each unexecuted region exactly one question:

> Does this correspond to a contract case that is missing, or to implementation
> the contract promises nothing about?

The first answer amends the contract and its tests. The second leaves it alone.
If nothing can reach the region at all, that is a dead-code finding against
production, not a test finding. **You never write a test to turn a region green.**

**Mutation** (`./scripts/mutation-test.sh [target]`) answers *does anything observe
what this code produces?* It mutates production bytecode — flips a `<`, swaps a
`+`, returns `null` — and reruns the covering tests. It is useful for finding a
case you wrote a test for but pinned nothing useful about. Under contract testing a
**high surviving-mutant count is the expected, healthy state**, because contract
tests deliberately leave the implementation free to change; the score therefore
measures precisely what we have decided not to optimize. Read individual survivors
in code the contract makes a promise about; never report the percentage.

## Constants in test code

**Never redeclare or duplicate a production constant's literal in test code.** If a
test needs the value, the question is whether the contract should name the
constant — not whether the field should be widened:

> A constant is part of the contract **if a contract's Javadoc names it**, via
> `{@value #X}` or `{@link Class#X}`. Then its visibility follows the visibility of
> the contract citing it. If no contract names it, it is implementation, and a test
> that needs it is testing implementation.

See [Contracts](./contracts.md#constants-and-the-contract) for the worked cases.

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

See [Unit Test Guide](./testing-unit.md).

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
test should add or use a `.mssw` fixture (see [AGENTS.md](../../AGENTS.md)).
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
`@TestInstance(PER_CLASS)` so a non-static `@BeforeAll` can run once per class).
`ElementInsertionTest.java` is the canonical example; its class header documents
why each block runs where it does.
