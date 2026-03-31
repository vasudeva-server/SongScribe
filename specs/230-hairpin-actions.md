# Hairpin Action Rework

Rework crescendo/diminuendo (hairpin) menu actions and introduce direct hairpin
selection for deletion. Replaces the existing three-item submenu with a
context-sensitive two-item submenu and adds click-to-select hairpin interaction.

**Issue:** vasudeva-server/SongScribe#230

---

## Goals

1. **Context-sensitive submenu** that adapts item labels and enabled state based
   on the current note selection and existing hairpins
2. **Direct hairpin selection** via click on the rendered hairpin lines, enabling
   deletion with the Delete key
3. **Mutual exclusivity** between hairpin selection and note selection, following
   the established glissando selection pattern
4. **Clean removal** of the old AddDynamicsAction, RemoveDynamicsAction, and
   their command types

---

## Current State

### Menu Structure

The Notation menu has a "Crescendo & Diminuendo" submenu (`createDynamicsMenu()`)
with three static items:
- Add Crescendo (AddDynamicsAction, REQUIRES_MULTIPLE_SELECTION)
- Add Diminuendo (AddDynamicsAction, REQUIRES_MULTIPLE_SELECTION)
- Remove (RemoveDynamicsAction, REQUIRES_SELECTION)

All three are always visible; enabled state is coarse-grained.

### Data Model

Hairpins are stored as `DynamicsInterval` objects in two `IntervalSet` fields on
`Line`:
- `Line.getCrescendos()` — `IntervalSet<DynamicsInterval>`
- `Line.getDiminuendos()` — `IntervalSet<DynamicsInterval>`

`DynamicsInterval` extends `Interval` (int `start`, int `end`) and adds
`x1ShiftSs`, `x2ShiftSs`, `yShiftSs` adjustment fields.

`IntervalSet.addInterval()` automatically merges overlapping intervals (but not
adjacent ones). When merging, a new interval is created with default (zero)
shift values.

### Relevant Files

| File | Role |
|------|------|
| `ui/action/AddDynamicsAction.java` | Current add action (to be removed) |
| `ui/action/RemoveDynamicsAction.java` | Current remove action (to be removed) |
| `ui/menu/NotationMenu.java` | Submenu construction |
| `music/MusicEditOperations.java` | `addDynamicsToSelection()`, `removeDynamicsFromSelection()` |
| `message/command/AddDynamicsCommand.java` | Current add command (to be removed) |
| `message/command/RemoveDynamicsCommand.java` | Current remove command (to be removed) |
| `ui/component/ScoreMessageCoordinator.java` | Command handlers |
| `ui/selection/LineSelectionState.java` | Selection state |
| `ui/selection/SelectionCoordinator.java` | Selection coordination, action state management |
| `ui/component/score/SelectionHandler.java` | Click-to-select routing |
| `ui/action/PasteboardAction.java` | DELETE action enabled-state logic |
| `music/Line.java` | Hairpin interval storage |
| `music/DynamicsInterval.java` | Hairpin data |
| `music/Interval.java` | Base interval (needs setters) |
| `music/IntervalSet.java` | Interval merging |

---

## Design

### 1. Submenu Structure

The "Crescendo & Diminuendo" submenu always contains exactly two `JMenuItem`s:

| Item | Default text | Default state |
|------|-------------|---------------|
| Crescendo item | "Add Crescendo" | Disabled |
| Diminuendo item | "Add Diminuendo" | Disabled |

Both items are **reused and mutated** (text and enabled state change in place)
rather than recreated on each selection change. When only one item is relevant
(extend case), the other is hidden via `setVisible(false)`.

The submenu parent item text ("Crescendo & Diminuendo") is static and never
changes.

### 2. HairpinActionController

A new `HairpinActionController` class manages the two submenu items. It:

- Holds a static `final` self-reference for GC protection (message bus uses
  weak references)
- Subscribes to `MusicSelectionDidChangeNotification`
- Computes the hairpin action state from the current selection and line data
- Updates the text, enabled state, and visibility of both menu items
- Does NOT handle mode-based disabling (delegates to UIAction flags on the
  underlying actions)

The two menu items are backed by UIActions that have mode-disabling flags
(DISABLE_WHEN_PLAYING, DISABLE_WHEN_EDITING_TEXT, DISABLE_IN_ADJUSTMENT_MODE,
DISABLE_IN_GRACE_MODE) but NO selection-related flags. The controller manages
all selection-dependent state.

### 3. Submenu State Algorithm

On each `MusicSelectionDidChangeNotification`, the controller evaluates:

**Step 1 -- Basic eligibility:**
- Selection must have >= 2 elements
- First and last elements of the selection must be notes or grace notes (rests
  may appear in between, but a hairpin cannot begin or end in silence)
- Selection must not cross a non-single barline or repeat (via
  `Line.rangeCanSpanSelection(begin, end)`)
- If any check fails: both items show "Add [type]", both disabled

**Step 2 -- Hairpin intersection analysis:**

Examine which hairpins (crescendo and diminuendo) intersect the selection range.

| Condition | Crescendo item | Diminuendo item |
|-----------|---------------|-----------------|
| No hairpin intersects selection | "Add Crescendo" (enabled) | "Add Diminuendo" (enabled) |
| Selection overlaps crescendo AND extends beyond it, no other hairpin conflict | "Extend Crescendo" (enabled) | hidden |
| Selection overlaps diminuendo AND extends beyond it, no other hairpin conflict | hidden | "Extend Diminuendo" (enabled) |
| Selection overlaps both a crescendo and diminuendo | "Add Crescendo" (disabled) | "Add Diminuendo" (disabled) |
| Selection entirely inside a hairpin (no extension possible) | "Add Crescendo" (disabled) | "Add Diminuendo" (disabled) |

**Extend eligibility** means:
- The selection includes notes inside the hairpin AND notes outside its range
- The extended notes (outside the hairpin) do not intersect any other hairpin
- Extension can be in one direction or both directions simultaneously

**Implementation note:** Extend uses the same `IntervalSet.addInterval()` as
Add. Since the new interval overlaps the existing one, IntervalSet's merge
behavior produces the extended hairpin automatically. Shift values are reset to
zero on merge, which is the desired behavior (rebuild from scratch).

### 4. Line.rangeCanSpanSelection(begin, end)

A new method on `Line` that returns `false` if any element between `begin` and
`end` (inclusive) is a non-single barline or a repeat sign. This is a general
utility -- no range element (hairpins, slurs, ties, glissandos) should span
these structural boundaries.

### 5. AddHairpinCommand

A single command class replacing both `AddDynamicsCommand` and
`RemoveDynamicsCommand`:

```java
public class AddHairpinCommand extends Message {
    private final boolean crescendo;

    public AddHairpinCommand(boolean crescendo) { this.crescendo = crescendo; }
    public boolean isCrescendo() { return crescendo; }
}
```

The handler in `ScoreMessageCoordinator` calls
`MusicEditOperations.addDynamicsToSelection(crescendo)` (existing method). The
IntervalSet handles merging when this is an extend operation.

### 6. Hairpin Selection

#### Selection State

Add a hairpin selection field to `LineSelectionState`:

```
private DynamicsInterval selectedHairpin;
private boolean selectedHairpinIsCrescendo;
```

Selecting a hairpin clears element/line/glissando selection (mutual exclusivity).
Selecting an element, line, or glissando clears hairpin selection.

Methods:
- `selectHairpin(DynamicsInterval hairpin, boolean isCrescendo)`
- `hasHairpinSelection()`
- `getSelectedHairpin()`
- `isSelectedHairpinCrescendo()`

#### MusicSelectionDidChangeNotification

Add `hasHairpinSelection` field (boolean), mirroring the existing
`hasGlissandoSelection` field. Populated from
`SelectionCoordinator.hasHairpinSelection()`.

#### Hit Testing

Hairpin click detection in `SelectionHandler`:

- The hit area is each line of the hairpin wedge, widened to 8 staff spaces
  (scales with zoom)
- For a crescendo: two lines diverging from the left point
- For a diminuendo: two lines converging to the right point
- The hit area is the union of the two widened line segments
- Hit testing order relative to notes does not matter -- the hairpin lines are
  rendered far enough below notes that the 8ss widening cannot overlap note
  hit areas

A new `HitResult.Hairpin` variant carries the `DynamicsInterval` reference and
whether it is a crescendo.

#### Click Handling

- Click within a hairpin's hit area: select the hairpin, clear note selection,
  post `MusicSelectionDidChangeNotification`
- Click on a note: clear hairpin selection, select note (existing behavior)
- Click on empty space: clear hairpin selection (and note selection -- existing
  behavior)

#### Action Disabling When Hairpin Is Selected

Follows the established glissando selection pattern:

1. Most staff-element-modifying actions have `REQUIRES_SELECTION` or
   `REQUIRES_MULTIPLE_SELECTION` flags, which check for element selection.
   When only a hairpin is selected, element selection is empty, so these
   actions self-disable.
2. `SelectionCoordinator` saves action states before hairpin selection and
   restores them when hairpin selection clears (same pattern as
   `reflectGlissandoSelection()`).
3. No new flags are needed -- the existing flag system handles this naturally.

#### Deletion

`PasteboardAction.DELETE` is updated to check `hasHairpinSelection()` (same
pattern as the existing `hasGlissandoSelection()` check). When Delete is pressed
with a hairpin selected:

1. Remove the `DynamicsInterval` from the appropriate `IntervalSet` on the `Line`
2. Clear the hairpin selection
3. Post `MusicSelectionDidChangeNotification`

After deletion, selection is cleared (no note selection restored).

### 7. Selection Highlight Rendering

When a hairpin is selected, the renderer draws the hairpin lines in the standard
selection color instead of the normal color. No other visual change (no handles,
no thickening).

### 8. Coexistence Constraint

A crescendo and diminuendo cannot coexist on the same note range. This is
enforced by the submenu state algorithm: when any hairpin (of either type)
overlaps the selection and the selection doesn't extend beyond it, both Add
items are disabled. When the selection extends beyond a hairpin, only the
matching type shows Extend, and the other type is hidden.

---

## What Is Removed

- `AddDynamicsAction.java` -- replaced by actions managed by
  HairpinActionController
- `RemoveDynamicsAction.java` -- replaced by direct selection + Delete
- `AddDynamicsCommand.java` -- replaced by `AddHairpinCommand`
- `RemoveDynamicsCommand.java` -- deletion handled directly, no command needed
- Command handlers in `ScoreMessageCoordinator` for the old commands
- `MusicEditOperations.removeDynamicsFromSelection()` -- no longer used
- Related string keys in `strings.properties` (after verifying no other
  references)

---

## New / Modified Files

| File | Change |
|------|--------|
| `ui/action/HairpinActionController.java` | **New** -- submenu state management |
| `message/command/AddHairpinCommand.java` | **New** -- unified add/extend command |
| `music/Line.java` | Add `rangeCanSpanSelection(begin, end)` |
| `music/Interval.java` | Add `setStart()` / `setEnd()` setters |
| `ui/selection/LineSelectionState.java` | Add hairpin selection fields and methods |
| `ui/selection/SelectionCoordinator.java` | Add `hasHairpinSelection()`, hairpin reflection |
| `ui/component/score/SelectionHandler.java` | Hairpin hit testing and click handling |
| `ui/action/PasteboardAction.java` | Enable DELETE for hairpin selection |
| `ui/menu/NotationMenu.java` | Replace `createDynamicsMenu()` with controller-managed submenu |
| `ui/component/ScoreMessageCoordinator.java` | Replace old handlers with `AddHairpinCommand` handler |
| `message/notification/MusicSelectionDidChangeNotification.java` | Add `hasHairpinSelection` |
| `music/MusicEditOperations.java` | Remove `removeDynamicsFromSelection()` |
| Renderer (hairpin) | Draw in selection color when selected |
| `strings.properties` | Update/add/remove string keys |

---

## Testing

### Unit Tests

- **HairpinActionController state computation:** mock selection ranges and line
  hairpin data, verify correct menu item text, enabled state, and visibility
  for each scenario in the state algorithm table
- **Line.rangeCanSpanSelection():** test with single barlines (allowed), double
  barlines, final barlines, and repeat signs (all disallowed)
- **Hairpin hit testing:** verify hit detection geometry for crescendo and
  diminuendo shapes at various positions
- **IntervalSet merge behavior for extend:** verify that overlapping add produces
  correctly merged interval with reset shift values

### E2E Tests

- Select eligible notes, verify submenu shows enabled "Add Crescendo" /
  "Add Diminuendo"
- Add a crescendo, verify it appears on the score
- Select notes overlapping the crescendo and extending beyond, verify submenu
  shows "Extend Crescendo"
- Extend the crescendo, verify the hairpin range expanded
- Click on a rendered hairpin, verify it becomes selected (highlight color)
  and note-modifying actions are disabled
- Press Delete with hairpin selected, verify hairpin is removed and selection
  cleared
- Select notes entirely inside a hairpin, verify both submenu items are disabled
- Select notes crossing a double barline, verify both items are disabled
