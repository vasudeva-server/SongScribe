# LineComponent Refactoring Plan

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Extract `LineRenderer`](#-phase-1-extract-linerenderer) | ✅ Complete | — |
| 2 | [Extract `InsertionNoteManager`](#-phase-2-extract-insertionnotemanager) | ✅ Complete | [phase-2-insertion-note-manager.md](./phase-2-insertion-note-manager.md) |
| 3 | [Extract `SelectionHandler`](#-phase-3-extract-selectionhandler) | ✅ Complete | [phase-3-selection-handler.md](./phase-3-selection-handler.md) |
| 4 | [Simplify Mouse Event Methods](#-phase-4-simplify-mouse-event-methods) | ✅ Complete | — |

## Context

`LineComponent` (src/main/java/songscribe/ui/LineComponent.java) has grown to ~1,900 lines with 67 methods, 34 fields, and multiple distinct responsibilities. This refactoring extracts cohesive subsystems into dedicated classes to improve maintainability and readability.

The refactoring is structured in phases ordered by isolation (most self-contained first). Each phase produces a compilable, working state.

---

## ✅ Phase 1: Extract `LineRenderer`

**Why first:** Largest concern (~21 methods), and rendering methods are already naturally grouped as callees of `render()`. They read state but don't mutate it, making extraction low-risk.

**New class:** `src/main/java/songscribe/ui/LineRenderer.java`

**Extract these methods:**
- `render()` (becomes the public entry point)
- `drawStaffLines()`
- `renderLineBeginning()`
- `renderDebug()`
- `renderGlissandos()`
- `renderKeyChanges()`
- `renderNotes()` / `renderElement()` / `renderNotesDirectly()`
- `getNoteColor()`
- `renderBeams()`
- `renderTies()`
- `renderTuplets()`
- `renderDynamics()`
- `renderEndings()`
- `renderAttachments()`
- `renderInsertionNote()`
- `renderDragRectangle()`

**Approach:**
- `LineRenderer` takes `LineComponent` (or a read-only interface) in its constructor
- Single public method: `render(Graphics2D g)`
- All rendering constants (`STAFF_LINE_COLOR`, `PLACEHOLDER_COLOR`, `EDIT_NOTE_COLOR`, `SELECTION_RECT_STROKE`, etc.) move to `LineRenderer`
- `LineComponent.render()` becomes a one-liner delegating to `lineRenderer.render(g)`

**State accessed (read-only):** `line`, `lineIndex`, `layoutResult`, `middleLineY`, `rootElement`, `score`, `editMode`, `playingNoteIndex`, `draggingSelection`, `dragRectangle`, plus static insertion note state.

---

## ✅ Phase 2: Extract `InsertionNoteManager`

Extract the insertion note subsystem (static cross-instance state, cursor management, note mutation logic) into a dedicated `InsertionNoteManager` class. This reduces `LineComponent` by ~250 lines and establishes a clear boundary for the insertion note feature.

See [phase-2-insertion-note-manager.md](./phase-2-insertion-note-manager.md) for detailed implementation plan.

---

## ✅ Phase 3: Extract `SelectionHandler`

**Why third:** Cleanly separable hit-testing and selection logic.

See [phase-3-selection-handler.md](./phase-3-selection-handler.md) for detailed implementation plan.

---

## ✅ Phase 4: Simplify Mouse Event Methods

After Phases 1-3, the mouse methods in `LineComponent` become thin dispatchers:

- `mouseMoved()` -> delegates to `InsertionNoteManager.trackMouse(...)`
- `mouseClicked()` -> dispatches to `InsertionNoteManager` or `SelectionHandler`
- `mousePressed/Dragged/Released()` -> delegates to `SelectionHandler`
- `mouseEntered/Exited()` -> simple cursor/state updates

No new class needed -- just cleanup of the now-simplified methods.

---

## Expected Result

| Class | Responsibility | Est. Lines |
|-------|---------------|------------|
| `LineComponent` | Coordination, layout, getters/setters, mouse dispatch | ~400-500 |
| `LineRenderer` | All rendering/drawing | ~700-800 |
| `InsertionNoteManager` | Insertion note tracking, state, actions | ~350-400 |
| `SelectionHandler` | Selection logic, hit testing, drag state | ~150-200 |

---

## Verification

After each phase:
1. `./scripts/compile.sh` - must compile cleanly
2. `./scripts/run.sh` - manual smoke test:
   - Notes render correctly on staff lines
   - Insertion note preview follows mouse in edit mode
   - Click to insert notes works
   - Click and drag selection works
   - Shift+click extends selection
   - Playback highlights correct notes
   - Mode switching (edit/select) works
   - Alt+click behavior works
