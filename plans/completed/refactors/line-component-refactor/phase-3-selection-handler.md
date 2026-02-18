# Phase 3: Extract `SelectionHandler`

**Type:** Sub-plan  <br>
**Parent:** plans/refactors/line-component-refactor/line-component-refactor.md → Phase 3  <br>
**Created:** 2026-02-13  <br>
**Pre-planned:** Yes  <br>
**Status:** Done

---

## Context

Phases 1 and 2 extracted `LineRenderer` and `InsertionNoteManager`. `LineComponent` still contains ~130 lines of selection/hit-testing/drag logic that form a cohesive subsystem. Phase 3 extracts this into `SelectionHandler`, following the same pattern as `InsertionNoteManager` (static utility class, package-private, same package).

**Key difference from `InsertionNoteManager`:** Selection state is per-instance (drag fields belong to each `LineComponent`), not static/cross-instance. So `SelectionHandler` methods will take a `LineComponent` parameter to access instance state, but the drag fields (`draggingSelection`, `dragStart`, `dragRectangle`) move to `SelectionHandler` as instance fields, with one `SelectionHandler` instance per `LineComponent`.

Actually, looking more closely at the pattern: `InsertionNoteManager` is a static utility because it manages cross-instance state. For `SelectionHandler`, since drag state is per-instance, it makes more sense to use an **instance per `LineComponent`** (as the master plan specifies). The handler holds the drag state fields and exposes methods that `LineComponent` delegates to.

## New File

`src/main/java/songscribe/ui/component/score/SelectionHandler.java` — package-private class, same package as `LineComponent`.

## What Moves to `SelectionHandler`

### Instance fields (from `LineComponent`)
- `draggingSelection` (boolean)
- `dragStart` (Point)
- `dragRectangle` (Rectangle)

### Methods (from `LineComponent`, become instance methods on `SelectionHandler`)
- `isSelectionActive(MouseEvent e)` — guard check
- `hitTestNote(Point point)` — returns index of hit note or -1
- `buildNoteHitRect(Note note, int noteIndex, Rectangle out)` — populates hit rect
- `calculateLineSelectionFromClick(Point clickPoint)` — click selection logic
- `calculateLineSelectionFromDrag(Rectangle dragRect)` — drag selection logic

## What Stays in `LineComponent`

### Mouse event handlers (thin dispatchers after extraction)
- `mousePressed()` — delegates to `selectionHandler.handlePress(e)`
- `mouseDragged()` — delegates to `selectionHandler.handleDrag(e)`
- `mouseClicked()` — selection branch delegates to `selectionHandler.handleClick(e)`, insertion branch still calls `InsertionNoteManager.handleClick(this)`
- `mouseReleased()` — delegates to `selectionHandler.handleRelease(e)`

### Existing accessors that change
- `isDraggingSelection()` → delegates to `selectionHandler.isDragging()`
- `getDragRectangle()` → delegates to `selectionHandler.getDragRectangle()`

## `SelectionHandler` API Design

```java
class SelectionHandler {
    // --- Instance state ---
    private final LineComponent lc;
    private boolean dragging = false;
    private final Point dragStart = new Point();
    private final Rectangle dragRectangle = new Rectangle();

    // --- Constructor ---
    SelectionHandler(LineComponent lc)

    // --- Public delegation entry points (called from LineComponent mouse handlers) ---
    void handlePress(MouseEvent e)      // mousePressed selection logic
    void handleDrag(MouseEvent e)       // mouseDragged selection logic
    void handleClick(MouseEvent e)      // mouseClicked selection logic
    void handleRelease(MouseEvent e)    // mouseReleased selection logic

    // --- Accessors (used by LineRenderer via LineComponent delegation) ---
    boolean isDragging()
    Rectangle getDragRectangle()

    // --- Private helpers ---
    private boolean isSelectionActive(MouseEvent e)
    private int hitTestNote(Point point)
    private void buildNoteHitRect(Note note, int noteIndex, Rectangle out)
    private void calculateLineSelectionFromClick(Point clickPoint)
    private void calculateLineSelectionFromDrag(Rectangle dragRect)
}
```

## Implementation Steps

1. **Create `SelectionHandler.java`** with package declaration, imports, constructor taking `LineComponent`
2. **Move drag state fields** (`draggingSelection`, `dragStart`, `dragRectangle`) from `LineComponent` to `SelectionHandler`
3. **Move private helper methods** (`isSelectionActive`, `hitTestNote`, `buildNoteHitRect`, `calculateLineSelectionFromClick`, `calculateLineSelectionFromDrag`) — these access `LineComponent` fields via the `lc` reference (e.g., `lc.getScore()`, `lc.getLine()`, `lc.getMiddleLineY()`, `lc.getLineSelectionState()`, `lc.getLineIndex()`)
4. **Create public delegation methods** (`handlePress`, `handleDrag`, `handleClick`, `handleRelease`) that encapsulate the selection logic currently inline in mouse handlers
5. **Update `LineComponent`**:
   - Add `private final SelectionHandler selectionHandler` field, initialized in constructor as `new SelectionHandler(this)`
   - Remove moved fields and methods
   - Simplify mouse handlers to delegate to `selectionHandler`
   - Update `isDraggingSelection()` and `getDragRectangle()` to delegate to `selectionHandler`
6. **Update `LineRenderer`** — `renderDragRectangle` already accesses drag state via `lc.isDraggingSelection()` and `lc.getDragRectangle()`, so no changes needed (the delegation is transparent)

### Required `LineComponent` accessors (already exist)
- `getScore()`, `getLine()`, `getLineIndex()`, `getMiddleLineY()`, `getLineSelectionState()` — all already package-private

### Detail: `handleClick` needs special care
`mouseClicked` has both selection and insertion branches. The `handleClick` method should return a boolean indicating whether it handled the event (selection was active), so `LineComponent.mouseClicked()` knows whether to fall through to `InsertionNoteManager`:

```java
// In SelectionHandler
boolean handleClick(MouseEvent e) {
    if (!isSelectionActive(e)) return false;
    // ... selection logic ...
    return true;
}

// In LineComponent.mouseClicked()
if (!selectionHandler.handleClick(e)) {
    InsertionNoteManager.handleClick(this);
}
```

### Detail: Alt-click mode switching stays in `LineComponent`
The alt-click `Actions.SELECT_MODE_ACTION.perform(this)` call in `mousePressed` and `mouseClicked` stays in `LineComponent` since it's about mode coordination, not selection logic. The handler methods only receive the event after the mode switch.

## Files Modified

| File | Change |
|------|--------|
| `src/main/java/songscribe/ui/component/score/SelectionHandler.java` | **New** — selection/hit-testing/drag logic (~150 lines) |
| `src/main/java/songscribe/ui/component/score/LineComponent.java` | Remove ~130 lines, add `selectionHandler` field, simplify mouse handlers to delegate |
| `src/main/java/songscribe/ui/component/score/LineRenderer.java` | No changes (already accesses via `lc` delegation methods) |

## Verification

1. `./scripts/compile.sh` — must compile cleanly
2. Manual smoke test:
   - Click to select a note in select mode
   - Click empty area near staff lines selects the line
   - Shift+click extends selection from anchor
   - Click and drag draws selection rectangle, selects intersecting notes
   - Releasing drag clears rectangle, selection persists
   - Alt+click switches to select mode and selects
   - Playback mode disables selection
   - Adjustment modes disable selection
   - Insertion note click still works in edit mode (non-selection path)
