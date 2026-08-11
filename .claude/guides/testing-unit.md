# Unit Test Guide

Read `./testing-common.md` first for shared conventions.

## Structure

All unit tests extend `UnitTest`. Location mirrors source: `src/test/java/songscribe/ui/action/FooTest.java`.

```java
class FooTest extends UnitTest {
    @Test
    void testSomethingHappens() {
        assertThat(result).isEqualTo(expected);
    }

    @Nested
    class WhenConditionX {
        @Test
        void testBehaviorY() { ... }
    }
}
```

## Parameterized Tests for Equivalence Classes and Invariants

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

See [Contract-Driven Testing](../guides/contracts.md) for how these cases fall
out of the contract itself.

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

Available: `semibreve`, `crotchet`, `quaver`, `graceQuaver`, `crotchetRest`,
`repeatLeft`, `repeatRight`, `repeatLeftRight`, `singleBarline`, `doubleBarline`,
`finalDoubleBarline`, `createNote(staffPosition, upper)`.

If the type you need is missing, add a method to `StaffElementFactory` rather
than reaching for `newInstance()` at the call site — a per-class `note()` helper
is the duplication this factory exists to prevent.

## MainFrame Singleton Mocking (fallback)

This is a fallback, not a first resort. Reach for it only when the class under
test cannot be constructed without the `MainFrame.getInstance()` chain and
cannot be changed to take its collaborators directly. A dependency that needs
this much mocking to exercise is usually a constructor-injection finding — the
collaborator belongs as a constructor or factory parameter, real API used by
production too, not something reached for through a static singleton at test
time. Prefer that fix over adding another mock setup here.

### Inline (try-with-resources) — for simple tests

```java
try (var mainFrameMock = mockStatic(MainFrame.class)) {
    var mockFrame = mock(MainFrame.class);
    var mockScore = mock(Score.class);
    var mockCoordinator = mock(SelectionCoordinator.class);

    mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
    when(mockFrame.getScore()).thenReturn(mockScore);
    when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
    when(mockScore.getMode()).thenReturn(Mode.EDIT);
    when(mockScore.getSelectionSize()).thenReturn(0);

    // test code
}
```

### Field-based (BeforeEach/AfterEach) — for test classes with many tests needing the same mock

```java
private MockedStatic<MainFrame> mainFrameMock;

@BeforeEach
void setUp() {
    mainFrameMock = mockStatic(MainFrame.class);
    setupMainFrameMock();
}

@AfterEach
void tearDown() {
    mainFrameMock.close();
}
```

### Full MainFrame mock setup (when Actions class initializes)

When the code under test triggers `Actions` initialization (which calls `UIUtils.registerActionKeystroke()`), you also need:

```java
var mockRootPane = mock(JRootPane.class);
when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
when(mockFrame.getRootPane()).thenReturn(mockRootPane);
```

## Multiple Static Mocks

When mocking several static classes, open all in `setUp()` and close all in `tearDown()`:

```java
private MockedStatic<MainFrame> mainFrameMock;
private MockedStatic<InsertionElementManager> insertionManagerMock;

@BeforeEach
void setUp() {
    mainFrameMock = mockStatic(MainFrame.class);
    insertionManagerMock = mockStatic(InsertionElementManager.class);
    // configure...
}

@AfterEach
void tearDown() {
    insertionManagerMock.close();
    mainFrameMock.close();
}
```

## Mockito Patterns

```java
// Stubbing
when(mockScore.getMode()).thenReturn(Mode.EDIT);
when(coordinator.hasActiveSelection()).thenReturn(true);

// Static method stubbing with arguments
spacingCalcMock.when(
    () -> InsertionSpacingCalculator.hasRoomForGraceNotePair(any(), anyInt())
).thenReturn(true);

// Verification
verify(composition).setModified(true);
verify(mockCoordinator, never()).applyActionToSelection(any(), anyBoolean());
```

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

## ReflectionTestHelper

`src/test/java/songscribe/ui/selection/ReflectionTestHelper.java` — creates `SelectionCoordinator` instances for testing without the full UI singleton graph.

```java
// Create coordinator with notes and actions
var coordinator = ReflectionTestHelper.createCoordinator(
    List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER.newInstance()),
    List.of(FERMATA_ACTION)
);

// Selection helpers
ReflectionTestHelper.selectRange(coordinator, 0, 2);
ReflectionTestHelper.selectNote(coordinator, 0);
ReflectionTestHelper.clearSelection(coordinator);
```

To enable `setModified()` verification, attach a mock Composition:

```java
var line = coordinator.getActiveSelection().getLine();
line.setComposition(mock(Composition.class));

// Later:
verify(line.getComposition()).setModified(true);
```

## Creating Test Action Instances

```java
private static final FermataAction FERMATA_ACTION = new FermataAction();
private static final AccidentalAction SHARP_ACTION =
    new AccidentalAction(StaffElement.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
private static final ElementTypeAction QUARTER_ACTION = new ElementTypeAction(
    Kind.DURATION, ElementType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0
);
```

## FlatLaf Properties Access

Tests that call `FlatLafProps.get()` need FlatLaf installed with the production properties loaded. Call `installFlatLafDefaults()` (from `UnitTest`) in a `@BeforeAll` method:

```java
@BeforeAll
static void setUp() throws Exception {
    installFlatLafDefaults();
}

@Test
void testSomethingUsingFlatLafProps() {
    int gap = FlatLafProps.get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_GAP);
    assertThat(gap).isEqualTo(5);
}
```

The helper is idempotent — safe to call from multiple test classes in the same JVM run.

## Mock Helpers for Complex Setup

Extract repeated mock wiring into private helper methods:

```java
private LineComponent lineComponentFor(Line line) {
    var lc = mock(LineComponent.class);
    when(lc.getLine()).thenReturn(line);
    return lc;
}

private MouseEvent mouseEvent(Component source, int id, int x, int y, int button) {
    return new MouseEvent(source, id, 0L, 0, x, y, x, y, 1, false, button);
}
```
