# Spec: Update Toolbar to Reflect Selection State

**Issue:** #80
**Branch:** `feat/update-toolbar`

## Overview

When the user selects one or more notes/rests/barlines, the toolbar (and menus) should visually reflect the attributes common to all selected elements. This gives the user immediate feedback about what the selection contains.

## Terminology

- **Reflectable action**: A `UIAction` subclass that implements `UIAction.Reflectable` and can derive its toggle (selected) state from note attributes.
- **Common attribute set**: The intersection of attributes across all selected elements, computed by the centralized handler.

## Architecture

### `UIAction.Reflectable` Interface

A nested interface inside `UIAction`:

```java
public interface Reflectable {
    /**
     * Whether this action's attribute is applicable to the given note.
     * For example, accidental actions return false for rests;
     * barline actions return false for notes.
     */
    boolean appliesTo(Note note);

    /**
     * Whether the given note has the attribute this action represents.
     * Only called when appliesTo() returns true.
     */
    boolean matchesNote(Note note);
}
```

### Action Class Hierarchy Changes

#### Merge `DurationAction` + `NonDurationAction` into `NoteTypeAction`

These two classes differ by only two lines (an extra flag and the message type posted). Merge them into a single `NoteTypeAction` class with a `Kind` enum:

```java
public class NoteTypeAction extends StickyUIAction implements UIAction.Reflectable {

    public enum Kind { DURATION, NON_DURATION }

    private final NoteType type;
    private final Kind kind;
    // ...
}
```

- `Kind.DURATION`: flags exclude `DISABLE_IN_REST_MODE`; `actionPerformed` posts `DurationSelectedMessage`.
- `Kind.NON_DURATION`: flags include `DISABLE_IN_REST_MODE`; `actionPerformed` posts `BarSelectedMessage`.
- `appliesTo`: `DURATION` returns `true` for all elements; `NON_DURATION` returns `true` only for barlines/repeats/breath marks.
- `matchesNote`: `note.getNoteType() == this.type` (shared by both kinds).

Update `DurationActionGroup`, `NonDurationActionGroup`, and `DurationToolbar` to use `NoteTypeAction` as their type parameter. Delete `DurationAction.java` and `NonDurationAction.java`.

#### New `NoteOnlyAction` Intermediate Class

Insert `NoteOnlyAction` between `InsertionNoteAction` and the four action classes whose attributes apply only to notes (not rests or barlines):

```
InsertionNoteAction
├── NoteOnlyAction (implements Reflectable, appliesTo = isNote())
│   ├── AccidentalAction        (implements matchesNote only)
│   ├── ForceArticulationAction (implements matchesNote only)
│   ├── FermataAction           (implements matchesNote only)
│   └── DurationArticulationAction (implements matchesNote only)
└── DotAction (implements Reflectable directly, appliesTo = isNote() || isRest())
```

`NoteOnlyAction` provides the shared `appliesTo` implementation:

```java
public abstract class NoteOnlyAction extends InsertionNoteAction
    implements UIAction.Reflectable {

    @Override
    public boolean appliesTo(Note note) {
        return note.getNoteType().isNote();
    }
}
```

#### New `AccidentalInParensAction` Class

`ACCIDENTAL_IN_PARENS_ACTION` is currently a plain `UIAction` instance in `Actions.java`. Replace it with a dedicated `AccidentalInParensAction` class that implements `Reflectable`:

- `appliesTo`: `note.getNoteType().isNote()` (extends `NoteOnlyAction`)
- `matchesNote`: `note.isAccidentalInParentheses()`

### Reflectable Action Summary

| Action Class | Attribute | `appliesTo` logic |
|---|---|---|
| `NoteTypeAction(DURATION)` | `NoteType` (exact match) | All elements |
| `NoteTypeAction(NON_DURATION)` | `NoteType` (exact match) | Barlines/repeats/breath marks only |
| `DotAction` | `dotCount` | Notes and rests |
| `AccidentalAction` | `accidental` | Notes only (via `NoteOnlyAction`) |
| `ForceArticulationAction` | `forceArticulation` | Notes only (via `NoteOnlyAction`) |
| `FermataAction` | `fermata` | Notes only (via `NoteOnlyAction`) |
| `DurationArticulationAction` | `durationArticulation` | Notes only (via `NoteOnlyAction`) |
| `AccidentalInParensAction` | `accidentalInParentheses` | Notes only (via `NoteOnlyAction`) |

Additional toggle actions (trill, etc.) should also implement `Reflectable` where they have corresponding toolbar/menu actions.

### Discovery

The centralized handler discovers reflectable actions by scanning the static fields of `Actions`. Any field whose value is a `UIAction` that also implements `UIAction.Reflectable` is collected into a list. No annotation or manual list is required.

Discovery is **lazy** -- performed on first use (first non-empty selection), not at construction time. This avoids class-loading order issues since `Actions` static fields may not be initialized when `SelectionCoordinator` is constructed. The discovered list is cached after the first scan.

### Enabled-State Guard via `instanceof`

Instead of adding a new flag to `UIAction.Flag`, `updateEnabledState()` checks `this instanceof Reflectable`. When the action implements `Reflectable` and the selection size is >= 1, `updateEnabledState()` short-circuits and returns early, preserving the action's current enabled state. This prevents the normal flag-based enabled logic from interfering with the reflection handler, which will set the enabled state itself.

No flag needs to be added to reflectable action constructors -- implementing `Reflectable` is sufficient.

### Centralized Handler in `SelectionCoordinator`

`SelectionCoordinator` subscribes to `MusicSelectionChangedMessage` and runs the reflection logic. It fires at **lower priority** than `UIAction.musicSelectionDidChange` so that enabled-state updates complete first.

**Algorithm:**

1. Lazy-init: if the reflectable action list has not been built, scan `Actions` fields and cache the list.
2. Get the current selection size via `getSelectionSize()`.
3. If selection is empty (size == 0):
   - If `hasSavedState` is true, **restore** saved pre-selection toggle states and set `hasSavedState = false`.
   - Return.
4. If `hasSavedState` is false (transition from 0 to 1+), **save** the current toggle state of all reflectable actions into the saved state map and set `hasSavedState = true`.
5. Iterate through selected elements (via `getSelectedNotes()`).
6. For each reflectable action:
   - Start with `applicable = false`, `matched = true` (optimistic).
   - For each selected element:
     - If `appliesTo(note)` is false, skip this element.
     - Set `applicable = true`.
     - If `matchesNote(note)` is false, set `matched = false` and break.
   - Call `action.setSelected(applicable && matched)`.
7. Enable all reflectable actions (`action.setEnabled(true)`).

### Pre-Selection State Save/Restore

The handler maintains a `Map<UIAction, Boolean>` for saved toggle states and a `boolean hasSavedState` flag to reliably detect transitions.

- **Save**: When `hasSavedState` is false and selection becomes non-empty, capture each reflectable action's current `isSelected()` state.
- **Restore**: When `hasSavedState` is true and selection becomes empty, restore each reflectable action's saved selected state.

This ensures the toolbar reverts to its pre-selection state when the user clears the selection.

### Iteration Logic

Add a `getSelectedNotes()` method to `SelectionCoordinator` that returns `List<Note>` from the current `NoteSelection` (line + begin/end indices). This encapsulates the iteration and keeps callers from needing to access `Line` directly.

### Group Handling

Actions within exclusive groups (duration, accidental, barline/repeat, dot) are iterated uniformly -- each action independently evaluates `matchesNote` and gets `setSelected` accordingly. No special group-aware logic is needed in the handler.

### Rest Handling

Rests participate in the iteration like any other element. The `appliesTo` method on each action determines relevance:
- `NoteTypeAction(DURATION)`: apply to all elements (exact `NoteType` match)
- `DotAction`: apply to notes and rests
- `NoteOnlyAction` subclasses (accidentals, articulations, fermata, etc.): apply to notes only (return `false` for rests)
- `NoteTypeAction(NON_DURATION)`: apply to barlines/repeats only

When a rest is encountered and `appliesTo` returns `false`, the rest is skipped -- it neither adds to nor removes from the common set for that action.

## Edge Cases

| Scenario | Behavior |
|---|---|
| Empty selection | Restore saved pre-selection toggle states |
| Single note selected | Reflect that note's attributes (same algorithm, just one element) |
| Mixed notes and rests | Rests are skipped for note-only attributes (accidentals, articulations) |
| All selected elements are rests | Note-only attributes have no applicable elements, so `matched = false` (deselected) |
| Selection contains only barlines | Note/rest attributes have no applicable elements, so `matched = false`; barline type is reflected |
| Mixed durations | No duration button is toggled (none matches all) |
| All same duration (e.g., all CROTCHET) | That duration button is toggled on |
| Note + rest of same base duration (CROTCHET + CROTCHET_REST) | No duration toggled (exact NoteType match required) |

## Future Considerations

- **Undo/redo**: When undo/redo is implemented, the reflection handler should re-run on composition-change events if a selection is active, so that undoing an attribute change updates the toolbar.
- **Apply on click**: A future issue should make clicking a reflectable action during multi-selection apply/remove that attribute on all selected notes, rather than only affecting the insertion note.

## Files to Modify

| File | Change |
|---|---|
| `UIAction.java` | Add `Reflectable` nested interface; update `updateEnabledState` to short-circuit via `instanceof Reflectable` |
| `SelectionCoordinator.java` | Add reflection handler, `getSelectedNotes()`, saved state map, `hasSavedState` flag, lazy discovery |
| `NoteTypeAction.java` | **New file.** Merged from `DurationAction` + `NonDurationAction`; implements `Reflectable` with `Kind` enum |
| `DurationAction.java` | **Delete.** Replaced by `NoteTypeAction` |
| `NonDurationAction.java` | **Delete.** Replaced by `NoteTypeAction` |
| `DurationActionGroup.java` | Update type parameter from `DurationAction` to `NoteTypeAction` |
| `NonDurationActionGroup.java` | Update type parameter from `NonDurationAction` to `NoteTypeAction` |
| `DurationToolbar.java` | Update `ACTIONS` list type from `DurationAction` to `NoteTypeAction` |
| `NoteOnlyAction.java` | **New file.** Abstract class between `InsertionNoteAction` and note-only actions; implements `Reflectable` with shared `appliesTo` |
| `AccidentalAction.java` | Extend `NoteOnlyAction` instead of `InsertionNoteAction`; implement `matchesNote` |
| `ForceArticulationAction.java` | Extend `NoteOnlyAction` instead of `InsertionNoteAction`; implement `matchesNote` |
| `FermataAction.java` | Extend `NoteOnlyAction` instead of `InsertionNoteAction`; implement `matchesNote` |
| `DurationArticulationAction.java` | Extend `NoteOnlyAction` instead of `InsertionNoteAction`; implement `matchesNote` |
| `AccidentalInParensAction.java` | **New file.** Extends `NoteOnlyAction`; implements `matchesNote` for `isAccidentalInParentheses()` |
| `Actions.java` | Update field types for merged `NoteTypeAction`; replace `ACCIDENTAL_IN_PARENS_ACTION` with `AccidentalInParensAction` instance |
| `DotAction.java` | Implement `Reflectable` directly (different `appliesTo` than `NoteOnlyAction`) |
