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

## MainFrame Singleton Mocking

Most UI-dependent tests need to mock the `MainFrame.getInstance()` singleton chain.

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

`assertThat(x).isNotNull()` does **not** narrow `x` for NullAway — the compiler still
treats `x` as `@Nullable`, so any field/method dereference after it fails to compile
with `[NullAway] dereferenced expression x is @Nullable`. When a `@Nullable` value must
be dereferenced after asserting it is present, use a manual guard that NullAway
understands, then dereference:

```java
var restored = element.getMainLyric();

if (restored == null) {
    throw new AssertionError("probe did not restore the original lyric");
}

assertThat(restored.text()).isEqualTo("la");   // restored is now non-null to NullAway
```

Do **not** flag this manual `if (x == null) throw new AssertionError(...)` pattern in
review as "should be `assertThat(x).isNotNull()`" — the manual form is required, not a
style lapse. Plain `assertThat(x).isNotNull()` is still correct when nothing is
dereferenced afterward.

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
