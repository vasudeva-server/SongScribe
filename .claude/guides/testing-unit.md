# Unit Test Guide

Read `./testing-common.md` first for shared conventions.

## Structure

All unit tests extend `UnitTest`. Location mirrors source: `src/test/java/songscribe/ui/action/FooTest.java`.
The class builds its environment once and its tests share it (see
[Test Environments](./testing-common.md#test-environments)).

```java
class FooTest extends UnitTest {
    private Foo foo;

    @BeforeAll
    void buildEnvironment() {
        foo = new Foo();
    }

    @Test
    void testSomethingHappens() {
        assertThat(foo.result()).isEqualTo(expected);
    }

    @Nested
    @TestInstance(PER_CLASS)
    class WhenConditionX {
        @BeforeAll
        void enterConditionX() {
            foo.enterX();
        }

        @Test
        void testBehaviorY() { ... }
    }
}
```

## Parameterized Tests for Equivalence Classes and Invariants

**Check before writing any `@Test` method: will this sit beside a sibling that
exercises the same method in the same way — same assertion shape, only the
input, the expected value, or a small piece of arrange/edit code differing?**
If yes, it is a row, not a method, from the first such case — not a refactor
applied once three or four near-identical methods have accumulated, and not
disqualified because the varying piece is a lambda rather than a literal (a
`record` field can hold a `Function`/`Consumer`/`BiFunction` as easily as a
value; see the third example below). This check runs at the moment you would
otherwise copy-paste an existing test and change a literal — not only when
adding a genuinely new case, but before reaching for copy-paste at all.

`@ParameterizedTest` with `@MethodSource` (or `@EnumSource` for a plain enum) is
the normal shape for a contract's enumerated domain or an invariant that must
hold across many inputs — not a special case reached for only when a table gets
long. Enumerating a finite domain this way costs what picking two representative
values costs; picking two is what leaves the third one broken.

Enumerated domain, one case per contract clause:

```java
@ParameterizedTest
@MethodSource("typesWithTickMapping")
void testTicksReturnsExactIntegerForEveryValidDotCount(ElementType type) {
    for (var dotCount = 0; dotCount <= NoteTypeMapping.MAX_DOT_COUNT; dotCount++) {
        assertThat(NoteTypeMapping.ticks(type, dotCount)).isPositive();
    }
}

static Stream<ElementType> typesWithTickMapping() {
    return Stream.of(ElementType.values())
        .filter(NoteTypeMapping::hasDuration);
}
```

Invariant over many representative inputs, asserting the property rather than
pinning one expected output per input:

```java
@ParameterizedTest
@MethodSource("typeAndTupletRatioCombinations")
void testTicksScaleExactlyForEveryTupletRatioAndDotCount(ElementType type, int ratio) {
    for (var dotCount = 0; dotCount <= NoteTypeMapping.MAX_DOT_COUNT; dotCount++) {
        var scaled = NoteTypeMapping.ticks(type, dotCount) * 2 / ratio;

        assertThat(scaled * ratio).isEqualTo(NoteTypeMapping.ticks(type, dotCount) * 2);
    }
}
```

Several cases that map varying input to a specific expected value — not an
invariant, a table — take a `record` case table rather than one hand-written
`@Test` per row. This is the shape a family of near-identical tests is in as soon
as a second one is a copy of the first with different literals; don't wait for a
third case or a "long" table before reaching for it:

```java
private record DeleteLabelCase(String description, List<ElementType> types, String expectedKey) {}

@ParameterizedTest(name = "{0}")
@MethodSource("deleteLabelCases")
void testDeleteLabel(DeleteLabelCase testCase) {
    assertThat(OpNames.deleteLabel(testCase.types()))
        .isEqualTo(Strings.get(testCase.expectedKey()));
}

static Stream<DeleteLabelCase> deleteLabelCases() {
    return Stream.of(
        new DeleteLabelCase("single note is singular",
            List.of(ElementType.CROTCHET), Strings.ACTION_EDIT_OP_DELETE_NOTE),
        new DeleteLabelCase("multiple notes is plural",
            List.of(ElementType.CROTCHET, ElementType.QUAVER), Strings.ACTION_EDIT_OP_DELETE_NOTES),
        new DeleteLabelCase("mixed categories is generic",
            List.of(ElementType.CROTCHET, ElementType.SINGLE_BARLINE), Strings.ACTION_EDIT_OP_DELETE_ELEMENTS)
        // ...one row per case, not one method per case
    );
}
```

The record's fields are exactly what varies between cases — input, expected output,
and a description that doubles as the parameterized test's display name (via
`name = "{0}"`) — so a reviewer scans rows to see what is and is not covered, rather
than reading N method bodies to confirm they're identical except for a literal.

See [Contracts](./contracts.md) for how these cases fall out of the contract
itself, and [design.md](~/.claude/guides/design.md) for whether
the behavior earns a test at all.

### Asserting that a table is exhaustive

A hand-listed table covers the domain on the day it is written and silently stops
covering it the day a constant is added. Wherever the class Javadoc claims a domain
is enumerated, something has to fail when the domain grows.

Prefer deriving the rows, which needs no extra assertion:

```java
static Stream<ArticulationType> cases() {
    return Stream.of(ArticulationType.values());       // grows on its own
}
```

When each row needs a fixture that cannot be derived from the constant — a
subclass instance, a built element, an expected `Strings` key — keep the table and
assert its coverage separately:

```java
@Test
void testCasesCoverEveryArticulationType() {
    assertThat(cases().map(ArticulationLabelCase::type))
        .containsExactlyInAnyOrder(ArticulationType.values());
}
```

For a sealed hierarchy the domain is its permitted subclasses, and the assertion is
the same shape against `Hairpin.class.getPermittedSubclasses()`. Where the
hierarchy nests — `Attachment` permits `MetronomeAttachment`, which itself permits
two more — the domain is the *leaves*, reached by walking `getPermittedSubclasses()`
until it comes back empty.

If the domain is private (`OpNames.Category` is a private enum), no assertion can
reach it. Do not widen it — that is test-only surface. Reword the Javadoc to claim
what the table actually covers, or make the taxonomy part of the contract.

## Creating Staff Elements

Never call `ElementType.X.newInstance()` in a test, and never write a local
`note()` / `crotchet()` helper. Use `songscribe.dom.StaffElementFactory` — it is
public and already covers the common element types:

```java
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;

var note = crotchet();
var grace = graceQuaver();
var note = StaffElementFactory.createNote(staffPosition, upper);   // pitched
```

If the type you need is missing, add a method to `StaffElementFactory` rather
than reaching for `newInstance()` at the call site — a per-class `note()` helper
is the duplication this factory exists to prevent.

## The MainFrame is injected, never mocked as a singleton

A class that needs a `MainFrame` takes it as a constructor or factory parameter
— real API used by production too. Its test builds one with
`MockEnvHelper.setupMockEnv()` and passes `env.frame()` in; the frame is
construction ceremony, and the assertions are against the model or the return
value, never against the mock.

**Do not `mockStatic(MainFrame.class)`.** Code under test that reaches
`MainFrame.getInstance()` is a constructor-injection finding — the collaborator
belongs as a parameter, not something reached for through a static singleton at
test time. Fix that, rather than adding a stub that lets the reach stand. There
is no shared fixture for the stub, deliberately.

The trap: **code that triggers `Actions` initialization needs a
mocked root pane too**, carrying a real `InputMap` and `ActionMap`, because
initialization registers action keystrokes through it. Without them the failure
is a `NullPointerException` inside static initialization, which does not name the
missing mock.

## AssertJ null checks and NullAway

`assertThat(x).isNotNull()` **does** narrow `x` for NullAway in test code, so a
dereference after it compiles. `build.gradle.kts` sets
`NullAway:HandleTestAssertionLibraries` to `true`, which teaches NullAway to treat
AssertJ's `isNotNull()` as a null check:

```java
var restored = element.getMainLyric();

assertThat(restored).isNotNull();
assertThat(restored.text()).isEqualTo("la");   // restored is non-null to NullAway
```

Prefer that form. It reports "expected non-null but was null" on failure, where a
manual guard reports whatever message the test author happened to write.

Older tests use a manual guard instead:

```java
if (restored == null) {
    throw new AssertionError("probe did not restore the original lyric");
}
```

That still compiles and is not worth churning through existing tests to replace, but
new tests should use `assertThat(...).isNotNull()`.

## Reading FlatLaf properties from a test

A test that reads a FlatLaf property needs FlatLaf installed with the production
properties loaded first, which `UnitTest.installFlatLafDefaults()` does. Call it
from `@BeforeAll`; it is idempotent, so several test classes in one JVM run may
each call it.

Without it the read fails as a missing key rather than as missing setup.
