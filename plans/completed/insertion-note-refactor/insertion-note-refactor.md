# Plan: Refactor Insertion Note to Use Layout System

**Type:** Master Plan
**Created:** 2026-02-02
**Status:** ✅ Complete
**Completed:** 2026-02-02

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | Add Position Calculation Methods to LayoutResult | ✅ Complete | |
| 2 | Add Insertion Note Handling to LineComponent | ✅ Complete | |
| 3 | Simplify EditModeManager | ✅ Complete | |
| 4 | Remove Legacy Code from Score.java | ✅ Complete | |
| 5 | Clean Up NotePosition | ✅ Complete | |

**Last Updated:** 2026-02-02

---

## Goal

Move insertion note (edit note) positioning and rendering from Score.java to LineComponent and EditModeManager, using the same layout infrastructure as actual notes. Maximize decoupling from Score.java in preparation for future Score refactoring.

## Current State

The insertion note is positioned and rendered entirely in Score.java using legacy calculations:
- `drawEditElements()` (lines 718-775) - renders the insertion note
- `calculateEditNoteXPos()` (lines 1073-1098) - calculates X position with custom logic
- `calculateEditNoteMovement()` (lines 1111-1119) - calculates movement offset
- `getActualLineMiddleY()` (lines 3092-3120) - aggregates Y through component hierarchy
- `NotePosition` stores `xIndex`, `movement`, `y`, `lineIndex`

This is separate from the layout2 system which handles actual note positioning.

## Target Architecture

```
Mouse Events → LineComponent (direct, as JComponent)
                    ↓
              LayoutResult.findInsertionIndex(mouseX)
                    ↓
              LineComponent static state (tracks which line has insertion note)
                    ↓
              LineComponent.renderInsertionNote()
```

- **LineComponent**: Handles everything - mouse events, position calculation, cross-line tracking (via static fields), rendering
- **EditModeManager**: Only holds the edit Note object (type, duration, etc.) - set by toolbar
- **Score.java**: No involvement in insertion note handling

## Implementation

### Phase 1: Add Position Calculation Methods to LayoutResult

**Recommended Model:** Sonnet - Well-defined algorithmic methods with clear specifications.

**File:** `src/main/java/songscribe/ui/layout2/LayoutResult.java`

Add methods for insertion note positioning:

```java
// Find which insertion slot a mouse X coordinate falls into
public int findInsertionIndex(double mouseX, @NotNull Line line) {
    // Uses NoteColumn positions to determine slot
}

// Calculate the X position for rendering at a given insertion index
public double calculateInsertionX(int insertionIndex, @NotNull Line line) {
    // Index 0: position before first note
    // Index == noteCount: position after last note
    // Otherwise: midpoint between adjacent notes
}
```

### Phase 2: Add Insertion Note Handling to LineComponent

**Recommended Model:** Opus - Most complex phase with static state management, cross-line tracking, mouse events, and rendering.

**File:** `src/main/java/songscribe/ui/component/score/LineComponent.java`

**Testing:** Write comprehensive tests if appropriate. Focus on testing static state management, cross-line tracking behavior, and mouse event handling logic where testable without full UI setup.

Add static fields for cross-line tracking:

```java
// Static state for tracking insertion note across all lines
private static LineComponent currentInsertionLine = null;
private static int currentXIndex = -1;
private static int currentY = -1;
```

Add instance fields:

```java
private EditModeManager editModeManager;  // To get the edit Note object
```

Add mouse handling:

```java
// MouseMotionListener on each LineComponent
public void mouseMoved(MouseEvent e) {
    if (!inEditMode()) return;

    int xIndex = layoutResult.findInsertionIndex(e.getX(), line);
    int y = calculateYFromMouse(e.getY());

    // Check if position actually changed
    if (this == currentInsertionLine && xIndex == currentXIndex && y == currentY) {
        return;  // No change, no repaint
    }

    // Repaint old line if different
    if (currentInsertionLine != null && currentInsertionLine != this) {
        currentInsertionLine.repaint();
    }

    // Update static state
    currentInsertionLine = this;
    currentXIndex = xIndex;
    currentY = y;

    // Repaint this line
    repaint();
}
```

Add rendering:

```java
private void renderInsertionNote(Graphics2D g2, ElementRenderContext ctx) {
    // Only render if this line is the current insertion line
    if (currentInsertionLine != this) return;

    Note editNote = editModeManager.getEditNote();
    if (editNote == null) return;

    double x = layoutResult.calculateInsertionX(currentXIndex, line);
    // Render with EDIT_NOTE_COLOR using NoteRenderer
}
```

### Phase 3: Simplify EditModeManager

**Recommended Model:** Sonnet - Straightforward code removal and simplification with dependency analysis.

**File:** `src/main/java/songscribe/ui/edit/EditModeManager.java`

**Testing:** Write comprehensive tests if appropriate. Test the simplified EditModeManager's core functionality (setting/getting edit note, visibility flags).

EditModeManager becomes simpler - it only holds:
- The edit Note object (type, duration, accidentals - set by toolbar)
- Basic visibility flag (if needed for toolbar state)

Remove:
- `NotePosition` objects (position now tracked in LineComponent)
- `applyNewEditNotePoint()` method

### Phase 4: Remove Legacy Code from Score.java

**Recommended Model:** Sonnet - Systematic code removal with clear specifications and dependency tracking.

**File:** `src/main/java/songscribe/ui/component/Score.java`

**Testing:** Write comprehensive tests if appropriate. Since this is primarily code removal, focus on integration testing to ensure the refactored system works correctly end-to-end.

1. Wire EditModeManager to LineComponents during construction
2. Remove from `drawEditElements()`:
   - All insertion note rendering code
3. Remove obsolete methods:
   - `calculateEditNoteXPos()`
   - `calculateEditNoteMovement()`
   - `getActualLineMiddleY()`
   - `setNewEditNotePoint()` (mouse→index logic moves to LineComponent)
4. Remove insertion-note-related mouse handling from Score's listeners

### Phase 5: Clean Up NotePosition

**Recommended Model:** Haiku - Simple cleanup task involving search for usages and deletion or field removal.

**File:** `src/main/java/songscribe/ui/edit/NotePosition.java`

**Testing:** Write comprehensive tests if appropriate. If NotePosition is still used elsewhere after cleanup, add tests for remaining functionality.

Options:
- **Delete entirely** if no longer needed (position now tracked in LineComponent)
- **Remove `movement` field** if class is still used elsewhere

## Critical Files

| File | Changes |
|------|---------|
| `LayoutResult.java` | Add `findInsertionIndex()`, `calculateInsertionX()` |
| `LineComponent.java` | Add static tracking, mouse handling, rendering |
| `EditModeManager.java` | Simplify - remove NotePosition objects |
| `Score.java` | Remove all insertion note mouse handling/positioning/rendering |
| `NotePosition.java` | Delete or simplify |

## Verification

1. Compile: `./scripts/compile.sh`
2. Run: `./scripts/run-debug.sh`
3. Test insertion note behavior:
   - Hover mouse over staff in edit mode - insertion note should appear
   - Move between notes - insertion note should position correctly
   - Click to insert - note should appear at correct position
   - Verify insertion at: beginning of line, between notes, end of line
