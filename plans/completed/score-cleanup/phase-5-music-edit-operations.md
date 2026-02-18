# Phase 5: Extract Music Editing Operations

**Type:** Sub-plan  <br>
**Parent:** score-cleanup.md → Phase 5  <br>
**Captured:** 2026-02-02  <br>
**Completed:** 2026-02-03  <br>
**Pre-planned:** No  <br>
**Status:** ✅ Complete  <br>
**Actual Impact:** 276 line reduction in Score.java, 408 lines in new MusicEditOperations class

## Overview

Extract 22 music editing operation methods (~400 lines) from Score.java into a new `MusicEditOperations` stateful service class. This follows the established MidiSequenceBuilder pattern (constructor injection, instance methods) rather than the static utility pattern (BeamCalculator/LyricsProcessor).

## Implementation Results

**Completed:** 2026-02-03
**Commit:** fd9bdad - refactor: extract music editing operations from Score to MusicEditOperations

Successfully extracted all 22 music editing operations into MusicEditOperations.java:
- Score.java reduced by 276 lines (306 removed, 30 added for delegation)
- MusicEditOperations.java created with 408 lines
- All code compiles successfully
- **Testing deferred until selection is fixed**

## Design Decisions

### Single Class Architecture

Use a single `MusicEditOperations` class rather than multiple domain-specific classes because:
- All operations share identical dependencies (Composition, SelectionManager, IMainFrame)
- Operations are independent with minimal cross-dependencies
- Simpler delegation from Score (one instance vs managing multiple)
- Can split later if complexity grows

### Stateful Service Pattern

Constructor injection of dependencies (following MidiSequenceBuilder pattern):
- Operations need access to mutable state (composition, selection)
- Not pure algorithms like BeamCalculator/LyricsProcessor
- Requires UI interaction (mainFrame.showConfirmDialog)
- More testable with dependency injection

### SelectionManager Access

Pass SelectionManager directly (not a snapshot) because:
- Operations need live selection state
- Operations modify SelectionManager state (`setTieContext(null)`)
- SelectionManager lifecycle matches Score lifecycle
- No risk of stale references

## Implementation Details

### New Class Structure

**File:** `src/main/java/songscribe/music/MusicEditOperations.java`

```java
package songscribe.music;

public final class MusicEditOperations {
    private final Composition composition;
    private final SelectionManager selectionManager;
    private final IMainFrame mainFrame;

    public MusicEditOperations(
        @NotNull Composition composition,
        @NotNull SelectionManager selectionManager,
        @NotNull IMainFrame mainFrame
    ) {
        this.composition = composition;
        this.selectionManager = selectionManager;
        this.mainFrame = mainFrame;
    }

    // 22 public operation methods
    // 1 private helper method
}
```

### Methods to Extract (22 total)

**Beaming (2 methods):**
- `canToggleBeaming()` - delegates to SelectionManager
- `toggleBeaming()` - adds/removes beam intervals, recalculates beam lengths

**Tie (3 methods):**
- `canToggleTie()` - validates tie toggle, caches TieContext
- `toggleTie()` - adds/removes tie intervals
- `getTieContext()` - returns cached TieContext from SelectionManager

**Tuplet (2 methods):**
- `canToggleTuplet()` - validates tuplet toggle
- `toggleTuplet(int tupletSize)` - adds/removes/modifies tuplet intervals

**Dynamics (4 methods):**
- `addDynamicsToSelection(boolean crescendo)` - adds crescendo or diminuendo
- `canRemoveDynamicsFromSelection()` - checks if dynamics exist in selection
- `removeDynamicsFromSelection()` - removes all dynamics from selection
- `getDynamicsIntervalsFromSelection()` - **private helper** returns dynamics in selection

**First-Second Ending (3 methods):**
- `canMakeFirstSecondEnding()` - always returns true (has TODO)
- `makeFirstSecondEnding()` - adds ending bracket, shows confirmation dialog if no repeat
- `removeFirstSecondEnding()` - removes ending bracket

**Trill (2 methods):**
- `canToggleTrill()` - checks if selection has real notes
- `toggleTrill()` - toggles trill flag on all notes

**Lyrics Under Rests (2 methods):**
- `canToggleLyricsUnderRests()` - checks if single rest selected
- `toggleLyricsUnderRests()` - toggles forceSyllable, calls LyricsProcessor

**Partial Beam (2 methods):**
- `canFlipPartialBeamOrientation()` - checks if single beamed note selected
- `flipPartialBeamOrientation()` - toggles invertFractionBeamOrientation flag

**Stem Direction (2 methods):**
- `canFlipStemDirection()` - checks if selection has non-rest notes
- `flipStemDirection()` - flips stem direction, recalculates beam angles

**Tempo (1 method):**
- `canChangeTempo()` - checks if selected note is not first note

### Score.java Changes

**Add field:**
```java
private MusicEditOperations operations = null;
```

**Initialize in setComposition():**
```java
private void setComposition(@NotNull Composition composition) {
    this.composition = composition;
    this.selectionManager = new SelectionManager(this::getComposition);
    this.operations = new MusicEditOperations(composition, selectionManager, mainFrame);
    // ... rest of initialization
}
```

**Delegation pattern for all 22 methods:**
```java
// Simple delegation for most operations
public boolean canToggleBeaming() {
    return operations.canToggleBeaming();
}

@Handler
public void onToggleBeaming(ToggleBeamMessage message) {
    operations.toggleBeaming();
    repaint();
}

// Special case: toggleTuplet also needs selectionChanged()
@Handler
public void onToggleTuplet(@NotNull ToggleTupletMessage message) {
    operations.toggleTuplet(message.getTupletSize());
    selectionChanged();  // Update UI state
    repaint();
}
```

### UI Coordination Strategy

**Operations handle:**
- `composition.setModified(true)` - domain state change
- Dialog interactions via `mainFrame.showConfirmDialog()` / `showInfoMessage()`

**Score handles:**
- `repaint()` - UI update after every operation
- `selectionChanged()` - only for `toggleTuplet()` operation

This keeps clean separation: operations modify model, Score updates view.

## Step-by-Step Implementation

### Step 1: Create MusicEditOperations Class
- Create file with package, imports, class structure
- Add constructor with 3 parameters
- Add private fields
- Compile to verify structure

### Step 2: Extract Methods by Group

**Group A - Beaming/Tie/Tuplet (7 methods):**
- Copy methods from Score.java to MusicEditOperations
- Update field access (use `this.composition`, `this.selectionManager`, etc.)
- Keep all logic identical

**Group B - Dynamics (4 methods including helper):**
- Copy all 4 methods (including private helper)
- Update field access

**Group C - Endings/Trill/Lyrics (7 methods):**
- Copy methods
- Update field access
- Verify mainFrame dialog calls work

**Group D - Beam/Stem Flips (4 methods):**
- Copy methods
- Update field access
- Verify BeamCalculator calls work

**Group E - Tempo (1 method):**
- Copy method
- Update field access (needs `getSingleSelectedNote()` - keep in Score)

### Step 3: Update Score.java

**Add operations field and initialization:**
- Add field declaration
- Initialize in `setComposition()`

**Update all 22 methods to delegate:**
- Replace method bodies with `return operations.method()`
- Update message handlers to call operations then repaint
- Add `selectionChanged()` call only in `onToggleTuplet()`

**Remove extracted code:**
- Delete original method implementations
- Keep helper methods used by Score (e.g., `getSingleSelectedNote()`)

### Step 4: Compile and Test

**Compilation:**
```bash
./scripts/compile.sh
```

**Manual testing:**
- Test each toggle operation from UI menus
- Verify dialogs appear correctly (First-Second Ending, Partial Beam, Stem Direction)
- Verify composition modified state updates
- Verify UI repaints correctly

## Critical Files

**Create:**
- `src/main/java/songscribe/music/MusicEditOperations.java` (~450 lines)

**Modify:**
- `src/main/java/songscribe/ui/component/Score.java`
  - Add operations field and initialization (~10 lines)
  - Replace 22 method bodies with delegation (~50 lines)
  - Remove extracted implementations (~400 lines)
  - Net: ~350 line reduction

**Reference files (for patterns):**
- `src/main/java/songscribe/midi/MidiSequenceBuilder.java` - stateful service pattern
- `src/main/java/songscribe/music/BeamCalculator.java` - utility class pattern
- `src/main/java/songscribe/ui/selection/SelectionManager.java` - selection state access

## Dependencies

**MusicEditOperations requires:**
- Composition (constructor) - read/write composition state
- SelectionManager (constructor) - query/update selection state
- IMainFrame (constructor) - show user dialogs
- BeamCalculator (static) - recalculate beam lengths
- LyricsProcessor (static) - reprocess lyrics after changes
- TupletIntervalData (static) - set tuplet grade

**External dependencies (static imports):**
- `javax.swing.JOptionPane` - for dialog constants
- `java.util.stream.IntStream` - for repeat checking in makeFirstSecondEnding()

## Verification

After implementation:

1. **Compile:** `./scripts/compile.sh` should succeed
2. **Run:** `./scripts/run-debug.sh` should start without errors
3. **Test each operation via UI:**
   - Toggle Beaming (Ctrl+B)
   - Toggle Tie (Ctrl+T)
   - Toggle Tuplet (menu)
   - Add/Remove Dynamics (menu)
   - Make/Remove First-Second Ending (menu)
   - Toggle Trill (menu)
   - Toggle Lyrics Under Rests (menu)
   - Flip Partial Beam Orientation (menu)
   - Flip Stem Direction (menu)
   - Change Tempo (menu)
4. **Verify dialogs:**
   - First-Second Ending without repeat shows confirmation
   - Invalid Partial Beam selection shows error
   - Invalid Stem Direction selection shows error
5. **Verify state updates:**
   - Composition modified flag set after operations
   - UI repaints after operations
   - Selection state updated after tuplet toggle

## Expected Results

**Before:**
- Score.java: ~3,346 lines
- Mixed concerns: UI coordination + music editing operations

**After:**
- Score.java: ~2,996 lines (350 line reduction)
- MusicEditOperations.java: ~450 lines
- Clear separation: Score coordinates UI, MusicEditOperations handles domain logic

## Risk Assessment

**Low Risk:**
- Pure extraction, no logic changes
- Established pattern (MidiSequenceBuilder Phase 1)
- Dependencies clearly defined
- All methods have clear boundaries

**Mitigation:**
- Keep all logic identical during extraction
- Thorough manual testing of all operations
- Verify dialog interactions work correctly
