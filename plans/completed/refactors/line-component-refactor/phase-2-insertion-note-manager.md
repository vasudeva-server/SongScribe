# Phase 2: Extract `InsertionNoteManager`

**Type:** Sub-plan  <br>
**Parent:** plans/refactors/line-component-refactor/line-component-refactor.md → Phase 2  <br>
**Captured:** 2026-02-13  <br>
**Pre-planned:** No  <br>
**Status:** Completed

---

## Context

`LineComponent` (~1413 lines) contains a self-contained insertion note subsystem with its own static cross-instance state. Phase 1 already extracted `LineRenderer`. Phase 2 extracts the insertion note tracking, cursor management, and note mutation logic into `InsertionNoteManager`, reducing `LineComponent` by ~250 lines and giving the insertion note subsystem a clear boundary.

## New File

`src/main/java/songscribe/ui/component/score/InsertionNoteManager.java` — package-private class, same package as `LineComponent`.

## What Moves to `InsertionNoteManager`

### Static state (lines 178-209)
- `currentInsertionLine`, `currentXIndex`, `currentYPos`, `altPressed`, `currentMouseLine`, `currentIsOverNoteHead`
- `MODE_CHANGE_LISTENER` field + static initializer block
- `ModeChangeListener` inner class

### Static methods
- `clearInsertionNote()` (line 682)
- `setAltPressed()` (line 698)
- `onModeChanged()` (line 712)
- `updateCursor()` (line 756)
- `getCurrentInsertionLine()` (line 772)
- `getCurrentXIndex()` (line 779)
- `getCurrentYPos()` (line 784)

### Instance methods (become static, taking `LineComponent` parameter)
- `restoreInsertionNote()` (line 725) — becomes `restoreInsertionNote(LineComponent lc)`
- `shouldHandleInsertionNote()` (line 1337) — becomes `shouldHandleInsertionNote(LineComponent lc)`
- `calculateYPosFromMouse()` (line 1360) — becomes `calculateYPosFromMouse(int mouseY, int middleLineY)`
- `isValidYPos()` (line 1374) — stays static utility
- `hasInsertionNote()` (line 793) — becomes `hasInsertionNote(LineComponent lc)` (compares `lc == currentInsertionLine`)

### Note mutation methods
- `addEditNote()` (line 802)
- `insertEditNote()` (line 882)
- `modifyExistingNote()` (line 932)

### Constants that move
- `STAFF_LINES_ABOVE` (line 160) — needed by `isValidYPos()`
- `STAFF_LINES_BELOW` (line 163) — needed by `isValidYPos()`
- `CROSSHAIR_CURSOR` (line 169) — needed by `updateCursor()`
- `DEFAULT_CURSOR` (line 171) — needed by `updateCursor()` and `mouseExited()`

Note: `DEFAULT_CURSOR` is also used in `mouseExited()` which stays in `LineComponent`. Either expose it from `InsertionNoteManager` as a package-private constant or keep a reference in both. Since `Cursor.getDefaultCursor()` is trivial, keeping it in both is fine.

## What Stays in `LineComponent`

### Mouse event methods (thin dispatchers after extraction)
- `mouseMoved()` — most logic moves to `InsertionNoteManager.trackMouse(LineComponent, MouseEvent)`
- `mouseClicked()` — insertion branch delegates to `InsertionNoteManager.handleClick(LineComponent)`
- `mouseEntered()` — calls `InsertionNoteManager.mouseEnteredLine(this)`
- `mouseExited()` — calls `InsertionNoteManager.mouseExitedLine(this)`
- `mousePressed()`, `mouseDragged()`, `mouseReleased()` — unchanged (selection only)

### All selection handling, layout, rendering delegation, getters/setters

## External Callers to Update

1. **`ScoreInputHandler.keyPressed()`** — `LineComponent.clearInsertionNote()` → `InsertionNoteManager.clearInsertionNote()`
2. **`ScoreInputHandler.keyReleased()`** — `LineComponent.setAltPressed(false)` → `InsertionNoteManager.setAltPressed(false)`
3. **`ScoreInputHandler.keyPressed()`** — `LineComponent.setAltPressed(true)` → `InsertionNoteManager.setAltPressed(true)`
4. **`LineRenderer.renderInsertionNote()`** — `lc.hasInsertionNote()` → `InsertionNoteManager.hasInsertionNote(lc)`
5. **`LineRenderer.renderInsertionNote()`** — `LineComponent.getCurrentXIndex()` → `InsertionNoteManager.getCurrentXIndex()`
6. **`LineRenderer.renderInsertionNote()`** — `LineComponent.getCurrentYPos()` → `InsertionNoteManager.getCurrentYPos()`

## `InsertionNoteManager` API Design

```java
class InsertionNoteManager {
    // --- Static state (all private) ---
    // currentInsertionLine, currentXIndex, currentYPos, altPressed,
    // currentMouseLine, currentIsOverNoteHead, MODE_CHANGE_LISTENER

    // --- Public static API ---
    static void clearInsertionNote()
    static void setAltPressed(boolean pressed)
    static void onModeChanged()                    // called by ModeChangeListener
    static LineComponent getCurrentInsertionLine()  // unused externally but kept for symmetry
    static int getCurrentXIndex()                   // used by LineRenderer
    static int getCurrentYPos()                     // used by LineRenderer
    static boolean hasInsertionNote(LineComponent lc)  // used by LineRenderer

    // --- Delegation entry points (called from LineComponent mouse handlers) ---
    static void trackMouse(LineComponent lc, MouseEvent e)      // replaces mouseMoved logic
    static void handleClick(LineComponent lc)                    // replaces mouseClicked insertion branch
    static void mouseEnteredLine(LineComponent lc)               // replaces mouseEntered insertion logic
    static void mouseExitedLine(LineComponent lc)                // replaces mouseExited insertion logic

    // --- Internal helpers (private static) ---
    static boolean shouldHandleInsertionNote(LineComponent lc)
    static void restoreInsertionNote(LineComponent lc)
    static void updateCursor()
    static int calculateYPosFromMouse(int mouseY, int middleLineY)
    static boolean isValidYPos(int yPos)
    static void addEditNote(LineComponent lc, Line line)
    static void insertEditNote(LineComponent lc, int xIndex, Line line)
    static void modifyExistingNote(LineComponent lc, int noteIndex, Line line)
}
```

## Implementation Steps

1. **Create `InsertionNoteManager.java`** with file header, package declaration, imports
2. **Move static state and inner class** from `LineComponent` to `InsertionNoteManager`
3. **Move static methods** (`clearInsertionNote`, `setAltPressed`, `onModeChanged`, `updateCursor`, getters)
4. **Move instance methods** as static methods taking `LineComponent` parameter (`restoreInsertionNote`, `shouldHandleInsertionNote`, `calculateYPosFromMouse`, `isValidYPos`, `hasInsertionNote`)
5. **Move note mutation methods** (`addEditNote`, `insertEditNote`, `modifyExistingNote`) as static methods
6. **Create delegation methods** (`trackMouse`, `handleClick`, `mouseEnteredLine`, `mouseExitedLine`) that encapsulate the insertion-note logic currently inline in mouse handlers
7. **Update `LineComponent`** — remove moved code, simplify mouse handlers to delegate to `InsertionNoteManager`
8. **Update `ScoreInputHandler`** — change `LineComponent.clearInsertionNote()` / `setAltPressed()` to `InsertionNoteManager.*`
9. **Update `LineRenderer`** — change `lc.hasInsertionNote()` to `InsertionNoteManager.hasInsertionNote(lc)`, update static getter calls
10. **Move `STAFF_LINES_ABOVE`/`STAFF_LINES_BELOW` constants** — these are only used by `isValidYPos()` which moves. Keep them in `InsertionNoteManager`. `LineComponent` doesn't reference them elsewhere.

## Files Modified

| File | Change |
|------|--------|
| `src/main/java/songscribe/ui/component/score/InsertionNoteManager.java` | **New** — all insertion note state and logic |
| `src/main/java/songscribe/ui/component/score/LineComponent.java` | Remove ~250 lines of insertion note code, simplify mouse handlers |
| `src/main/java/songscribe/ui/component/score/LineRenderer.java` | Update 3 static calls + `hasInsertionNote` call |
| `src/main/java/songscribe/ui/component/ScoreInputHandler.java` | Update 3 static calls (2 in `keyPressed`, 1 in `keyReleased`) |

## Verification

1. `./scripts/compile.sh` — must compile cleanly
2. Manual smoke test:
   - Mouse over staff in edit mode shows insertion note preview
   - Insertion note follows mouse, snaps to note positions
   - Click to insert/append notes works
   - Click on existing note head modifies pitch
   - Alt+click switches to select mode
   - Alt held clears insertion note, release restores it
   - Mode switch (edit/select) updates cursor correctly
   - Mouse enter/exit shows/hides edit note
