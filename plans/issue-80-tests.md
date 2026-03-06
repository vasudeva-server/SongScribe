# Test Plan: Toolbar Selection Reflection

**Parent plan:** `plans/issue-80-implementation-plan.md`
**Issue:** #80

This plan covers the implementation of all tests for the toolbar selection reflection feature: 51 unit tests (sections A-H from the parent plan) plus 6 e2e tests.

**IMPORTANT:** Be careful to follow the code rules in @.claude/rules/code-styles/java+kotlin.md.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | [Test helper: `ReflectionTestHelper`](#phase-1-reflectiontesthelper) | ✅ Complete |
| 2 | [Unit: `NoteTypeActionTest`](#phase-2-notetypeactiontest) | ✅ Complete |
| 3 | [Unit: `NoteOnlyActionAppliesToTest`](#phase-3-noteonlyactionappliestotest) | ✅ Complete |
| 4 | [Unit: `DotActionTest`](#phase-4-dotactiontest) | ✅ Complete |
| 5 | [Unit: `AccidentalActionTest`](#phase-5-accidentalactiontest) | ✅ Complete |
| 6 | [Unit: `ForceArticulationActionTest`](#phase-6-forcearticulationactiontest) | ✅ Complete |
| 7 | [Unit: `FermataActionTest`](#phase-7-fermataactiontest) | ✅ Complete |
| 8 | [Unit: `DurationArticulationActionTest`](#phase-8-durationarticulationactiontest) | ✅ Complete |
| 9 | [Unit: `AccidentalInParensActionTest`](#phase-9-accidentalinparensactiontest) | ✅ Complete |
| 10 | [Unit: `UIActionReflectableGuardTest`](#phase-10-uiactionreflectableguardtest) | ✅ Complete |
| 11 | [Unit: `ReflectionHandlerTest`](#phase-11-reflectionhandlertest) | ✅ Complete |
| 12 | [Unit: `ReflectionIntegrationTest`](#phase-12-reflectionintegrationtest) | ✅ Complete |
| 13 | [E2E: `ToolbarReflectionTest`](#phase-13-toolbarreflectiontest) | ✅ Complete |

---

## Conventions

- **Framework:** JUnit 5, AssertJ assertions, Mockito 5.21.0
- **Test location:** `src/test/java/songscribe/` mirroring source structure
- **Naming:** `test` + what-and-expected (e.g., `testAppliesToReturnsTrueForNote`)
- **Notes are created via** `NoteType.CROTCHET.newInstance()` etc., then mutated with setters (`setAccidental`, `setDotCount`, `setFermata`, etc.)

---

## Phase 1: ReflectionTestHelper

**File:** `src/test/java/songscribe/ui/selection/ReflectionTestHelper.java`

A utility class used by phases 11-12 to set up a `SelectionCoordinator` with a `Line` containing specific notes and a custom set of reflectable actions.

### Why needed

`SelectionCoordinator.getReflectableActions()` scans `Actions.class` static fields via reflection, which pulls in the full UI singleton graph. Tests must bypass this by injecting a custom list into the private `reflectableActions` field.

### API

```java
class ReflectionTestHelper {

    // Creates a SelectionCoordinator with a Line containing the given notes,
    // registered and activated at line index 0, with the given actions
    // injected as the reflectable actions list.
    static SelectionCoordinator createCoordinator(
        List<Note> notes,
        List<UIAction.Reflectable> actions
    );

    // Selects notes [fromIndex..toIndex] inclusive on the coordinator's line.
    static void selectRange(SelectionCoordinator coordinator, int fromIndex, int toIndex);

    // Selects a single note.
    static void selectNote(SelectionCoordinator coordinator, int noteIndex);

    // Clears the selection.
    static void clearSelection(SelectionCoordinator coordinator);
}
```

### Implementation details

**`createCoordinator`:**
1. Create a `Line` (no `Composition` — `modifiedComposition()` is a no-op without one)
2. Add each note via `line.addNote(note)`
3. Create `new SelectionCoordinator(() -> null)`
4. Create `new LineSelectionState(line)`
5. `coordinator.registerLineState(0, state)` and `coordinator.activateLine(0)`
6. Inject `actions` into `coordinator` via field reflection:
   ```java
   var field = SelectionCoordinator.class.getDeclaredField("reflectableActions");
   field.setAccessible(true);
   field.set(coordinator, new ArrayList<>(actions));
   ```
7. Return the coordinator

**`selectRange`:**
1. Get the `LineSelectionState` via `coordinator.getActiveSelection()`
2. `state.setSelectionFromClick(fromIndex)`
3. If `toIndex != fromIndex`: `state.extendSelectionTo(toIndex)`

**`selectNote`:** Delegates to `selectRange(coordinator, noteIndex, noteIndex)`

**`clearSelection`:** `coordinator.getActiveSelection().clearSelection()`

---

## Phase 2: NoteTypeActionTest

**File:** `src/test/java/songscribe/ui/action/NoteTypeActionTest.java`

Covers: A1-A6 (appliesTo), M1-M2 (matchesNote), T1-T2 (merge regression)

### Test fixture

Construct `NoteTypeAction` instances directly. The constructor is public:
```java
new NoteTypeAction(Kind.DURATION, NoteType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0)
new NoteTypeAction(Kind.NON_DURATION, NoteType.SINGLE_BARLINE, "Barline", null, 0, "barline", "Single barline", 0, 0)
```

Create notes via `NoteType.*.newInstance()`.

### Tests

| ID | Method | Setup | Assert |
|---|---|---|---|
| A1 | `testDurationAppliesToNote` | DURATION action, CROTCHET note | `appliesTo` returns true |
| A2 | `testDurationAppliesToRest` | DURATION action, CROTCHET_REST note | true |
| A3 | `testDurationAppliesToBarline` | DURATION action, SINGLE_BARLINE note | true |
| A4 | `testNonDurationDoesNotApplyToNote` | NON_DURATION action, CROTCHET note | false |
| A5 | `testNonDurationDoesNotApplyToRest` | NON_DURATION action, CROTCHET_REST note | false |
| A6 | `testNonDurationAppliesToBarline` | NON_DURATION action, SINGLE_BARLINE note | true |
| M1 | `testMatchesNoteWhenTypeMatches` | CROTCHET action, CROTCHET note | `matchesNote` returns true |
| M2 | `testDoesNotMatchNoteWhenTypeDiffers` | CROTCHET action, MINIM note | false |
| T1 | `testDurationKindHasCorrectFlags` | DURATION action | Does NOT have `DISABLE_IN_REST_MODE`; has `DISABLE_WHEN_PLAYING`, `DISABLE_IN_ADJUSTMENT_MODE`, `DISABLE_WHEN_EDITING_TEXT` |
| T2 | `testNonDurationKindHasCorrectFlags` | NON_DURATION action | Has `DISABLE_IN_REST_MODE`, `DISABLE_WHEN_PLAYING`, `DISABLE_IN_ADJUSTMENT_MODE`, `DISABLE_WHEN_EDITING_TEXT` |

**Note on T1/T2:** The parent plan specifies verifying `actionPerformed` posts the correct message type. However, `actionPerformed` calls `doActionPerformed(e)` which chains through `StickyUIAction` -> `UIAction` and depends on UI state (ActionGroup). Testing the message posting would require extensive mocking for minimal value. The critical regression check is that flags are correct per Kind, since that controls enabled/disabled behavior. If message posting needs testing, it belongs in the e2e suite.

---

## Phase 3: NoteOnlyActionAppliesToTest

**File:** `src/test/java/songscribe/ui/action/NoteOnlyActionAppliesToTest.java`

Covers: A7-A9

### Test fixture

`NoteOnlyAction` is abstract. Use a concrete subclass — `FermataAction` (simplest constructor, no fields):
```java
var action = new FermataAction();
```

### Tests

| ID | Method | Note type | Assert |
|---|---|---|---|
| A7 | `testAppliesToNote` | CROTCHET | true |
| A8 | `testDoesNotApplyToRest` | CROTCHET_REST | false |
| A9 | `testDoesNotApplyToBarline` | SINGLE_BARLINE | false |

---

## Phase 4: DotActionTest

**File:** `src/test/java/songscribe/ui/action/DotActionTest.java`

Covers: A10-A12 (appliesTo), M5-M8 (matchesNote)

### Test fixture

```java
new DotAction(DotLevel.SINGLE, "Dot", null, 0, "dot", "Add dot", 0, 0)
new DotAction(DotLevel.DOUBLE, "Double Dot", null, 0, "double-dot", "Add double dot", 0, 0)
```

Notes created via `NoteType.*.newInstance()`, mutated with `note.setDotCount(n)`.

### Tests

| ID | Method | DotLevel | Note | Assert |
|---|---|---|---|---|
| A10 | `testAppliesToNote` | SINGLE | CROTCHET | true |
| A11 | `testAppliesToRest` | SINGLE | CROTCHET_REST | true |
| A12 | `testDoesNotApplyToBarline` | SINGLE | SINGLE_BARLINE | false |
| M5 | `testSingleDotMatchesDotCount1` | SINGLE | dotCount=1 | true |
| M6 | `testSingleDotDoesNotMatchDotCount0` | SINGLE | dotCount=0 | false |
| M7 | `testDoubleDotMatchesDotCount2` | DOUBLE | dotCount=2 | true |
| M8 | `testDoubleDotDoesNotMatchDotCount1` | DOUBLE | dotCount=1 | false |

---

## Phase 5: AccidentalActionTest

**File:** `src/test/java/songscribe/ui/action/AccidentalActionTest.java`

Covers: M3-M4

### Test fixture

```java
new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp")
```

Notes mutated with `note.setAccidental(Accidental.SHARP)` etc.

### Tests

| ID | Method | Accidental | Note accidental | Assert |
|---|---|---|---|---|
| M3 | `testMatchesWhenAccidentalMatches` | SHARP | SHARP | true |
| M4 | `testDoesNotMatchWhenAccidentalDiffers` | SHARP | FLAT | false |

---

## Phase 6: ForceArticulationActionTest

**File:** `src/test/java/songscribe/ui/action/ForceArticulationActionTest.java`

Covers: M9-M10

### Test fixture

```java
new ForceArticulationAction("Accent", null, 0, "accent", "Add accent")
```

Notes mutated with `note.setForceArticulation(ForceArticulation.ACCENT)`.

### Tests

| ID | Method | Note state | Assert |
|---|---|---|---|
| M9 | `testMatchesWhenArticulationPresent` | forceArticulation=ACCENT | true |
| M10 | `testDoesNotMatchWhenArticulationNull` | forceArticulation=null (default) | false |

---

## Phase 7: FermataActionTest

**File:** `src/test/java/songscribe/ui/action/FermataActionTest.java`

Covers: M11-M12

### Tests

| ID | Method | Note state | Assert |
|---|---|---|---|
| M11 | `testMatchesWhenFermataTrue` | fermata=true | true |
| M12 | `testDoesNotMatchWhenFermataFalse` | fermata=false (default) | false |

---

## Phase 8: DurationArticulationActionTest

**File:** `src/test/java/songscribe/ui/action/DurationArticulationActionTest.java`

Covers: M13-M14

### Test fixture

```java
new DurationArticulationAction(DurationArticulation.STACCATO, "Staccato", null, 0, "staccato", "Add staccato")
```

### Tests

| ID | Method | Note state | Assert |
|---|---|---|---|
| M13 | `testMatchesWhenArticulationMatches` | durationArticulation=STACCATO | true |
| M14 | `testDoesNotMatchWhenArticulationNull` | durationArticulation=null (default) | false |

---

## Phase 9: AccidentalInParensActionTest

**File:** `src/test/java/songscribe/ui/action/AccidentalInParensActionTest.java`

Covers: M15-M16

### Tests

| ID | Method | Note state | Assert |
|---|---|---|---|
| M15 | `testMatchesWhenInParentheses` | accidentalInParentheses=true | true |
| M16 | `testDoesNotMatchWhenNotInParentheses` | accidentalInParentheses=false (default) | false |

---

## Phase 10: UIActionReflectableGuardTest

**File:** `src/test/java/songscribe/ui/action/UIActionReflectableGuardTest.java`

Covers: U1-U3

### Challenge

`updateEnabledState()` calls `MainFrame.getInstance().getScore().getSelectionCoordinator().getSelectionSize()`. This requires `mockStatic(MainFrame.class)` (supported by Mockito 5.21.0).

### Test fixture

```java
try (var mainFrameMock = mockStatic(MainFrame.class)) {
    var mockFrame = mock(MainFrame.class);
    var mockScore = mock(Score.class);
    var mockCoordinator = mock(SelectionCoordinator.class);

    mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
    when(mockFrame.getScore()).thenReturn(mockScore);
    when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
    when(mockCoordinator.getSelectionSize()).thenReturn(selectionSize);

    // ... test action ...
}
```

For U1/U2, use a concrete `Reflectable` action (e.g., `FermataAction`).
For U3, use a non-reflectable `UIAction` (e.g., `Actions.TOGGLE_BEAM_ACTION` or a directly constructed `UIAction`).

### Tests

| ID | Method | Action type | selectionSize | Assert |
|---|---|---|---|---|
| U1 | `testReflectableWithSelectionPreservesEnabledState` | FermataAction | 2 | Returns early, enabled state unchanged |
| U2 | `testReflectableWithNoSelectionRunsNormalLogic` | FermataAction | 0 | Normal flag logic runs (enabled state changes) |
| U3 | `testNonReflectableWithSelectionRunsNormalLogic` | Non-reflectable UIAction | 2 | Normal flag logic runs |

**Verification approach for U1:** Set the action enabled, call `updateEnabledState()`, assert it returns `true` (the current state). The key is that it does NOT run the full flag logic which would disable the action (since we haven't set up the full flag context).

**Verification approach for U2/U3:** The full flag logic runs. Since the mock Score has no composition state, the result depends on the action's flags. The point is that it does NOT short-circuit — verify by checking that the returned value reflects flag evaluation (not the preserved state).

---

## Phase 11: ReflectionHandlerTest

**File:** `src/test/java/songscribe/ui/selection/ReflectionHandlerTest.java`

Covers: H1-H4 (state transitions), R1-R6 (core logic), E1-E2 (force-enable)

Uses `ReflectionTestHelper` from phase 1.

### Test fixture pattern

```java
// Create notes
var note1 = NoteType.CROTCHET.newInstance();
note1.setAccidental(Note.Accidental.SHARP);

// Create actions
var accidentalAction = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");

// Create coordinator with notes and actions
var coordinator = ReflectionTestHelper.createCoordinator(
    List.of(note1, note2),
    List.of(accidentalAction)
);

// Select and reflect
ReflectionTestHelper.selectRange(coordinator, 0, 1);
coordinator.reflectSelection(null);  // message param is unused
```

### Section A: State Transitions (H1-H4)

| ID | Method | Setup | Action | Assert |
|---|---|---|---|---|
| H1 | `testNoSelectionNoSavedStateIsNoOp` | No selection, hasSavedState=false | `reflectSelection(null)` | No state changes, actions unchanged |
| H2 | `testClearSelectionRestoresSavedState` | Select notes, reflect (saves state), then clear selection | `reflectSelection(null)` | Each action's selected state restored to pre-selection value |
| H3 | `testNewSelectionSavesState` | No prior selection | Select notes, `reflectSelection(null)` | Actions have reflected states (not original states) |
| H4 | `testChangedSelectionDoesNotResave` | Select range [0,0], reflect. Change to [0,1] | `reflectSelection(null)` | Saved states are from the first selection, not re-saved |

**H2 detail:** Before selecting, set specific toggle states on actions (e.g., `action.setSelected(true)`). After selection+reflect, the actions will have reflected states. Clear selection and reflect again — assert the original toggle states are restored.

**H4 detail:** Create 2 notes where only the first matches. Select note 0, reflect — action becomes selected. Now the saved state is `true` (the pre-reflection state). Select notes 0+1 (second doesn't match), reflect — action becomes `false`. Clear and reflect — should restore to the *original* saved state (before the first selection), not the state after the first reflection.

### Section B: Core Logic (R1-R6)

All use `AccidentalAction(SHARP)` as the test action.

| ID | Method | Notes | Assert selected |
|---|---|---|---|
| R1 | `testAllApplicableAllMatchSelected` | 2 CROTCHET notes, both SHARP | true |
| R2 | `testAllApplicableFirstMismatchDeselected` | 2 CROTCHET notes, first FLAT, second SHARP | false |
| R3 | `testAllApplicableSecondMismatchDeselected` | 2 CROTCHET notes, first SHARP, second FLAT | false |
| R4 | `testNoteAndRestNoteMatchesSelected` | CROTCHET(SHARP) + CROTCHET_REST | true (rest skipped) |
| R5 | `testNoteAndRestNoteMismatchDeselected` | CROTCHET(FLAT) + CROTCHET_REST | false |
| R6 | `testAllInapplicableDeselected` | 2 CROTCHET_REST notes | false (none applicable) |

### Section F: Force-Enable (E1-E2)

| ID | Method | Setup | Assert |
|---|---|---|---|
| E1 | `testReflectionEnablesAllActions` | Select notes, reflect | All reflectable actions have `isEnabled() == true` |
| E2 | `testClearSelectionDoesNotSetEnabled` | Select, reflect, then clear, reflect | After restore, handler does not call `setEnabled()`. Verify by disabling actions before clear+reflect, then check they remain disabled after restore. |

---

## Phase 12: ReflectionIntegrationTest

**File:** `src/test/java/songscribe/ui/selection/ReflectionIntegrationTest.java`

Covers: I1-I6

Tests the full reflection logic across multiple action types simultaneously. Each test creates a set of notes and a comprehensive set of actions, then verifies the selected state of every action after reflection.

### Shared action set

Each test creates these actions and injects them all:

```java
var crotchetAction = new NoteTypeAction(DURATION, CROTCHET, ...);
var minimAction = new NoteTypeAction(DURATION, MINIM, ...);
var barlineAction = new NoteTypeAction(NON_DURATION, SINGLE_BARLINE, ...);
var sharpAction = new AccidentalAction(SHARP, ...);
var flatAction = new AccidentalAction(FLAT, ...);
var dotAction = new DotAction(SINGLE, ...);
var doubleDotAction = new DotAction(DOUBLE, ...);
var fermataAction = new FermataAction();
var staccatoAction = new DurationArticulationAction(STACCATO, ...);
```

### Tests

| ID | Method | Notes | Key assertions |
|---|---|---|---|
| I1 | `testTwoCrotchetsSharpNoDot` | 2 CROTCHET, both SHARP, dotCount=0 | crotchet=selected, minim=deselected, sharp=selected, flat=deselected, dot=deselected, doubleDot=deselected, barline=deselected |
| I2 | `testCrotchetAndMinimBothSharp` | CROTCHET(SHARP) + MINIM(SHARP) | crotchet=deselected, minim=deselected, sharp=selected |
| I3 | `testCrotchetAndCrotchetRest` | CROTCHET(SHARP) + CROTCHET_REST | crotchet=deselected (matches note but not rest for DURATION kind), sharp=selected (rest skipped by NoteOnlyAction) |
| I4 | `testTwoCrotchetRests` | 2 CROTCHET_REST | All accidental/articulation actions deselected (NoteOnlyAction.appliesTo=false for rests), duration actions reflect per matchesNote |
| I5 | `testTwoSingleBarlines` | 2 SINGLE_BARLINE | duration=deselected (DURATION appliesTo=true but matchesNote=false), barline=selected, accidental/dot=deselected |
| I6 | `testSingleCrotchetSharpDottedFermata` | 1 CROTCHET, SHARP, dotCount=1, fermata=true | crotchet=selected, sharp=selected, dot=selected, fermata=selected |

**I3 clarification:** `NoteTypeAction(DURATION)` has `appliesTo` returning `true` for all note types. A CROTCHET_REST has `NoteType.CROTCHET_REST`, so `matchesNote` returns false for a CROTCHET action. The CROTCHET action applies to both (applicable=true), but the rest doesn't match (matched=false). Result: deselected.

**I5 clarification:** `NoteTypeAction(DURATION)` applies to barlines (appliesTo=true), but `matchesNote` checks `note.getNoteType() == CROTCHET` which is false for SINGLE_BARLINE. So duration actions are deselected. `NoteTypeAction(NON_DURATION, SINGLE_BARLINE)` applies to barlines and matches. `DotAction.appliesTo` returns false for barlines, so dot actions: applicable=false, result: deselected.

---

## Phase 13: ToolbarReflectionTest

**File:** `src/test/java/songscribe/e2e/ToolbarReflectionTest.java`

**Extends:** `BaseSwingTest`

Also update `src/test/java/songscribe/e2e/e2e-tests.md` with the new test descriptions.

These e2e tests verify that the full wiring works end-to-end: message bus priority ordering, the `updateEnabledState()` guard with the real `MainFrame` singleton, the lazy `Actions` scan in `getReflectableActions()`, and the actual toolbar button selected states.

### Helper methods

```java
// Builds a composition with the given notes on line 0
private void buildComposition(Note... notes);

// Asserts a toolbar action's selected state via the actual Swing button.
// Finds the button by action command name (using findButtonByName from
// BaseSwingTest), checks button.getModel().isSelected() on the EDT
// via GuiActionRunner.execute().
private void assertButtonSelected(UIAction action, boolean expected);

// Inserts a note at the next position on line 0, returns the note index
private int insertNote(NoteType type, int staffPosition);

// Inserts a note and sets its accidental
private int insertNoteWithAccidental(NoteType type, int staffPosition, Note.Accidental accidental);
```

### Composition building strategy

Rather than building compositions programmatically and checking action state (which tests the model, not the toolbar), the e2e tests should:
1. Enter edit mode
2. Select a duration, click to insert notes
3. Optionally select accidentals/articulations and click to modify
4. Enter select mode
5. Click/shift-click to select notes
6. Assert toolbar button states

This tests the real user workflow end-to-end.

### Tests

| ID | Method | Actions | Assert |
|---|---|---|---|
| E2E-1 | `testSingleNoteSelection` | Insert 1 crotchet. Select mode, click note. | QUARTER=selected, HALF/EIGHTH=deselected, accidentals=deselected, dot=deselected |
| E2E-2 | `testSingleRestSelection` | Insert 1 crotchet rest (enable rest mode, insert, disable rest mode). Select mode, click rest. | QUARTER=selected (DURATION appliesTo=true, CROTCHET_REST != CROTCHET so actually deselected). All accidental/articulation buttons deselected (NoteOnlyAction.appliesTo=false for rests). |
| E2E-3 | `testMultipleIdenticalNotes` | Insert 2 crotchets at different positions. Select mode, click first, shift-click second. | QUARTER=selected, accidentals=NONE/deselected, dot=deselected |
| E2E-4 | `testMultipleDifferentDurations` | Insert 1 crotchet + 1 minim. Select both. | QUARTER=deselected, HALF=deselected (types differ) |
| E2E-5 | `testMultipleDifferentAccidentals` | Insert 2 crotchets, add SHARP to first, FLAT to second. Select both. | SHARP=deselected, FLAT=deselected (accidentals differ), QUARTER=selected |
| E2E-6 | `testNoteAndRestSelection` | Insert 1 crotchet note + 1 crotchet rest. Select both. | QUARTER=deselected (CROTCHET != CROTCHET_REST), accidentals=deselected |
| E2E-7 | `testDifferentDurationsAndRest` | Insert 1 crotchet + 1 minim + 1 crotchet rest. Select all. | QUARTER=deselected, HALF=deselected, all accidentals=deselected |
| E2E-8 | `testSelectionClearedRestoresState` | Set QUARTER selected (edit mode default). Select a note (QUARTER becomes selected via reflection). Deselect (Cmd+D). | QUARTER returns to its pre-selection state |

### E2E-2 note on rest behavior

When a rest is selected:
- `NoteTypeAction(DURATION, CROTCHET)`: appliesTo=true (DURATION applies to everything), matchesNote checks `getNoteType() == CROTCHET` but the note's type is `CROTCHET_REST`, so matched=false. Result: deselected.
- `NoteTypeAction(DURATION, CROTCHET_REST)` does not exist in Actions (rests are not toolbar buttons).
- `NoteOnlyAction` subclasses: appliesTo=false for rests, so applicable=false, result: deselected.
- `DotAction`: appliesTo=true for rests. matchesNote checks dotCount. Rests default to dotCount=0, so DOT=deselected, DOUBLE_DOT=deselected.

### e2e-tests.md update

Append the following section to `src/test/java/songscribe/e2e/e2e-tests.md`:

```markdown
## ToolbarReflectionTest

### testSingleNoteSelection
**Goal:** Verify that selecting a single note reflects its properties onto toolbar buttons.
**Steps:**
1. Insert 1 quarter note at staff position 0.
2. Switch to select mode, click the note.
3. Verify QUARTER button is selected, HALF/EIGHTH are deselected.
4. Verify all accidental buttons are deselected (note has no accidental).
5. Verify DOT button is deselected.

### testSingleRestSelection
**Goal:** Verify that selecting a single rest deselects all toolbar buttons.
**Steps:**
1. Select quarter note duration, enable rest mode, insert a rest, disable rest mode.
2. Switch to select mode, click the rest.
3. Verify all duration buttons are deselected (CROTCHET_REST != CROTCHET).
4. Verify all accidental/articulation buttons are deselected (not applicable to rests).

### testMultipleIdenticalNotes
**Goal:** Verify that selecting multiple identical notes reflects their shared properties.
**Steps:**
1. Insert 2 quarter notes at different staff positions.
2. Switch to select mode, click first note, shift-click second note.
3. Verify QUARTER button is selected (both are quarter notes).
4. Verify accidental buttons are deselected (both have no accidental).

### testMultipleDifferentDurations
**Goal:** Verify that selecting notes with different durations deselects all duration buttons.
**Steps:**
1. Insert 1 quarter note + 1 half note.
2. Switch to select mode, select both notes.
3. Verify QUARTER is deselected, HALF is deselected (types differ).

### testMultipleDifferentAccidentals
**Goal:** Verify that selecting notes with different accidentals deselects accidental buttons.
**Steps:**
1. Insert 2 quarter notes, add SHARP to first, FLAT to second.
2. Switch to select mode, select both notes.
3. Verify SHARP is deselected, FLAT is deselected (accidentals differ).
4. Verify QUARTER is selected (both are quarter notes).

### testNoteAndRestSelection
**Goal:** Verify toolbar state when selecting a note and a rest together.
**Steps:**
1. Insert 1 quarter note + 1 quarter rest.
2. Switch to select mode, select both.
3. Verify QUARTER is deselected (CROTCHET != CROTCHET_REST).
4. Verify all accidental buttons are deselected.

### testDifferentDurationsAndRest
**Goal:** Verify toolbar state when selecting notes of different durations plus a rest.
**Steps:**
1. Insert 1 quarter note + 1 half note + 1 quarter rest.
2. Switch to select mode, select all 3.
3. Verify QUARTER is deselected, HALF is deselected, all accidentals deselected.

### testSelectionClearedRestoresState
**Goal:** Verify that clearing a selection restores toolbar buttons to their pre-selection state.
**Steps:**
1. Note the current QUARTER button state (selected in edit mode).
2. Switch to select mode, click a note (reflection updates toolbar).
3. Press Cmd+D to deselect all.
4. Verify QUARTER button returns to its pre-selection state.
```

---

## Implementation Order

1. `ReflectionTestHelper` -- needed by phases 11-13
2. Phases 2-9 in any order (pure unit tests, no dependencies between them)
3. Phase 10 (`UIActionReflectableGuardTest`) -- requires Mockito mockStatic
4. Phase 11 (`ReflectionHandlerTest`) -- depends on phase 1
5. Phase 12 (`ReflectionIntegrationTest`) -- depends on phase 1
6. Phase 13 (`ToolbarReflectionTest`) -- depends on full application boot, run last

Phases 2-9 can be implemented in parallel since they are independent.

---

## Files Summary

| File | Type | Tests |
|---|---|---|
| `src/test/java/songscribe/ui/selection/ReflectionTestHelper.java` | Helper | -- |
| `src/test/java/songscribe/ui/action/NoteTypeActionTest.java` | Unit | 10 |
| `src/test/java/songscribe/ui/action/NoteOnlyActionAppliesToTest.java` | Unit | 3 |
| `src/test/java/songscribe/ui/action/DotActionTest.java` | Unit | 7 |
| `src/test/java/songscribe/ui/action/AccidentalActionTest.java` | Unit | 2 |
| `src/test/java/songscribe/ui/action/ForceArticulationActionTest.java` | Unit | 2 |
| `src/test/java/songscribe/ui/action/FermataActionTest.java` | Unit | 2 |
| `src/test/java/songscribe/ui/action/DurationArticulationActionTest.java` | Unit | 2 |
| `src/test/java/songscribe/ui/action/AccidentalInParensActionTest.java` | Unit | 2 |
| `src/test/java/songscribe/ui/action/UIActionReflectableGuardTest.java` | Unit | 3 |
| `src/test/java/songscribe/ui/selection/ReflectionHandlerTest.java` | Unit | 10 |
| `src/test/java/songscribe/ui/selection/ReflectionIntegrationTest.java` | Unit | 6 |
| `src/test/java/songscribe/e2e/ToolbarReflectionTest.java` | E2E | 8 |
| `src/test/java/songscribe/e2e/e2e-tests.md` | Docs | -- |

**Total: 14 files (1 helper, 12 test classes, 1 docs), 57 tests** (51 unit + 6 e2e from spec + 2 additional e2e)

---

## Dependencies

No new Maven dependencies needed. Existing:
- JUnit 5.11.4
- AssertJ 3.27.3
- AssertJ Swing 3.17.1
- Mockito 5.21.0 (supports `mockStatic` natively)

---

## Potential Issues

1. **Mockito `mockStatic` for `MainFrame.getInstance()`**: Mockito 5.x supports this with the inline mock maker. If it fails, add `mockito-inline` artifact as a test dependency.

2. **`MessageCenter.subscribe()` in `SelectionCoordinator` constructor**: Called during test setup. mbassy is lightweight and the subscription is a weak reference. The coordinator is held by the test, so it won't be GC'd.

3. **`Line.addNote()` calls `modifiedComposition()`**: This is a no-op when the Line has no Composition (which is the case in unit tests).

4. **Thread safety**: All unit tests are single-threaded. `MessageCenter.post().now()` is synchronous.

5. **E2E `TOTAL_E2E_TESTS` counter in `BaseSwingTest`**: Currently set to 37. Must be updated when adding the 8 new e2e tests (new total: 45).
