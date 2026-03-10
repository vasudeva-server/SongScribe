# E2E Test Guide

Read `.claude/testing-common.md` first for shared conventions.

## Core Principle

E2E tests simulate exactly what the user does. Never call `UIAction.actionPerformed()`, `ActionGroup` selection mutating methods, or mutate the model directly to trigger the behavior under test. Always click toolbar buttons or select menu items.

**Exception:** Direct model setup via `GuiActionRunner.execute()` is acceptable for establishing preconditions when the test is not verifying the setup path.

## Structure

All E2E tests extend `E2ETest` and live in `src/test/java/songscribe/e2e/`.

```java

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FooTest extends E2ETest {
    @Test
    void testSomething() {
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        assertThat(composition().getLine(0).elementCount()).isEqualTo(1);
    }
}
```

The base class handles per-class MainFrame boot, per-test composition reset, edit mode entry, and rest mode deselection.

## Toolbar and Menu Helpers

| Method                       | Purpose                                             |
|------------------------------|-----------------------------------------------------|
| `clickToolbarButton(action)` | Click any toolbar button by its action              |
| `selectDuration(action)`     | Select a note duration (quarter, eighth, etc.)      |
| `triggerAction(action)`      | Click an action button (beam, tie, flip stem, etc.) |
| `enableRestMode()`           | Toggle rest mode on                                 |
| `deselectRestMode()`         | Toggle rest mode off (if active)                    |
| `enterEditMode()`            | Switch to edit mode (if not already)                |
| `enterSelectMode()`          | Switch to select mode (if not already)              |
| `clickMenuItem(itemName)`    | Click a menu item by its text label                 |
| `findButtonByName(name)`     | Find a toolbar button component by name             |

## Coordinate Helpers

| Method                                       | Returns | Purpose                                             |
|----------------------------------------------|---------|-----------------------------------------------------|
| `insertionPoint(lineIndex, staffPositionSp)` | `Point` | Screen coords for inserting a new note              |
| `noteScreenPosition(lineIndex, noteIndex)`   | `Point` | Screen coords of an existing note's notehead center |

Both must be called within `GuiActionRunner.execute()` for EDT safety. `insertionPoint` places X past the rightmost note (or at a fixed offset if empty).

## Click Helpers

| Method                                                  | Purpose                                                                                                               |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `clickAt(point)`                                        | Left-click at screen coordinates                                                                                      |
| `shiftClickAt(point)`                                   | Shift-click (for multi-selection); uses synthetic MouseEvents because robot doesn't reliably carry keyboard modifiers |
| `dragNote(lineIndex, noteIndex, targetStaffPositionSp)` | Drag a note to a new pitch                                                                                            |

## Pausing

`pause()` sleeps for a short delay (10ms normally, 250ms in debug mode). All click and toolbar helpers call `pause()` automatically, so you rarely need to call it yourself. Use it explicitly only when you need to wait for an async operation that isn't covered by a helper.

## Layout Synchronization

**Always call `performLayout(lineIndex)` after any model mutation before reading layout data or coordinates.** It invalidates the layout cache and forces an immediate repaint.

```java
clickAt(insertionPoint(0, 0));

performLayout(0);  // Required before querying model or coordinates

assertThat(composition().

getLine(0).

elementCount()).

isEqualTo(1);
```

## Model Query Helpers

| Method                           | Purpose                                      |
|----------------------------------|----------------------------------------------|
| `score()`                        | Returns `MainFrame.getInstance().getScore()` |
| `composition()`                  | Returns `score().getComposition()`           |
| `isBeamed(lineIndex, noteIndex)` | Check if a note is in a beam interval        |
| `isTied(lineIndex, noteIndex)`   | Check if a note is in a tie interval         |

## Building Preconditions

When setting up initial state (not the feature under test), build directly via the model:

```java
private void buildTwoQuarterNotes() {
    GuiActionRunner.execute(() -> {
        var composition = new Composition(MainFrame.getInstance());
        var line = new Line();
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(0);
        line.addElement(note);
        composition.addLine(0, line);
        score().setComposition(composition);
    });
    performLayout(0);
}
```

## Assertion Patterns

```java
// Model state
assertThat(line.elementCount()).

isEqualTo(2);

assertThat(element.getType()).

isEqualTo(ElementType.CROTCHET);

assertThat(isBeamed(0, 0)).

isTrue();

// Selection state
assertThat(score().

getSelectionSize()).

isEqualTo(2);

var lss = score().getLineComponent(0).getLineSelectionState();

assertThat(lss.canToggleTie()).

isTrue();

// Toolbar reflection (verify UI tracks model)
var selectable = (UIAction.Selectable) action;
var isSelected = GuiActionRunner.execute(() -> selectable.isSelected());

assertThat(isSelected).

isEqualTo(expected);

// Button state
var button = findButtonByName(action.getActionCommand());
var isSelected = GuiActionRunner.execute(() -> button.getModel().isSelected());

assertThat(isSelected).

isEqualTo(expected);

// Save/load round-trip
var reloaded = roundTrip(composition());

assertThat(reloaded.getLine(0).

elementCount()).

isEqualTo(original.getLine(0).

elementCount());
```

## Common Patterns

### Insert notes then test a feature

```java
selectDuration(Actions.EIGHTH_NOTE_ACTION);

clickAt(insertionPoint(0, 0));

performLayout(0);

clickAt(insertionPoint(0, 2));

performLayout(0);

enterSelectMode();

clickAt(noteScreenPosition(0, 0));

shiftClickAt(noteScreenPosition(0, 1));

triggerAction(Actions.TOGGLE_BEAM_ACTION);

performLayout(0);

assertThat(isBeamed(0, 0)).

isTrue();
```

### Custom coordinate helpers for non-note features

```java
private Point glissandoMidpoint(int lineIndex) {
    var p0 = noteScreenPosition(lineIndex, 0);
    var p1 = noteScreenPosition(lineIndex, 1);
    return new Point((p0.x + p1.x) / 2, (p0.y + p1.y) / 2);
}
```
