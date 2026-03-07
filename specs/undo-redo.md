# Undo / Redo Specification

## Overview

SongScribe will gain a unified, chronological undo/redo system covering all operations that affect the
visual appearance of the score: individual music edits, composition settings changes, and text block
edits. The system uses a **hybrid** model — a fine-grained command pattern for incremental score edits
and snapshots for dialog-confirmed changes — backed by a **single unified undo stack** that text field
edits share with music edits.

---

## Undo Model

### Hybrid Approach

| Change source | Mechanism | Granularity |
|---|---|---|
| Score edits (notes, intervals, adjustments) | Command pattern | Fine-grained — each command captures exactly the fields it modifies |
| Text field edits (lyrics, footnotes, etc.) | Unified stack | Per character (standard Swing UndoManager forwarded to global stack) |
| Dialog-confirmed changes (composition settings, key signature, tempo, annotation, etc.) | Snapshot (before/after diff) | One step per dialog OK if state actually changed |
| Paste | Composite command | One atomic step regardless of note count |
| Insertion-note session | Composite command | One step per complete insertion-note session (not per keypress) |
| Drag position | Command | One step per completed drag (mouseReleased, not intermediate events) |

### Command Pattern Details

Each score-edit command:

- Captures **exactly the fields it modifies** before mutation (not the entire Line or Composition).
- Carries a human-readable **display name** used in the Edit menu label.
- Implements `javax.swing.undo.UndoableEdit` (extending `AbstractUndoableEdit`) so the standard
  `UndoManager` can manage the stack.

#### Composite Commands

Some operations are logically atomic from the user's perspective:

- **Paste**: wraps N individual note-add commands in a `CompoundEdit`. One Cmd+Z removes all pasted
  notes.
- **Insertion-note session**: captures note state on enter, records final state on exit (click away or
  Escape). The entire session is one `CompoundEdit`.

### Snapshot (Dialog) Details

For dialog-confirmed changes:

- The snapshot is taken **on dialog open** (so the pre-change state is always clean), but the undo
  entry is only **pushed to the stack on OK** and only if the state actually differs from the snapshot.
- Clicking Cancel never adds an undo entry.
- If a dialog gains live-preview support in the future, the snapshot-on-open design already handles
  this correctly.

---

## Stack Configuration

| Property | Value |
|---|---|
| Maximum depth | 100 steps |
| Eviction policy | FIFO — oldest entry discarded when limit is exceeded |
| Redo model | Linear — making a new edit discards the redo stack |
| Scope | Application-level (MainFrame singleton owns the manager) |
| Lifetime | Survives File → Save; cleared by File → New and File → Open |

---

## What Is Undoable

Everything that changes the visual appearance of the score:

### Note-Level Operations
- Add note / delete note
- Change note pitch (via insertion-note session)
- Change note duration / dot count
- Change accidental
- Toggle trill, fermata, stem direction, fraction beam orientation
- Force articulation, duration articulation
- Add / remove articulation
- Set syllable movement / force syllable
- Set glissando
- Set xOffset / yPos (drag; final position only)
- Note annotation (dialog-confirmed)
- Grace note operations

### Interval / Range Operations
- Toggle beaming
- Toggle tie
- Toggle tuplet
- Add / remove dynamics (crescendo, diminuendo)
- First / second ending
- Key signature change (dialog-confirmed)
- Tempo change (dialog-confirmed)
- Beat change (dialog-confirmed)

### Structural Operations
- Insert line
- Delete line
- Cut (removes notes)
- Paste (atomic)

### Composition Settings (dialog-confirmed)
- Title, place, date fields (year, month, day), number
- Attribution
- Default key signature and key type
- Line width
- Row height adjustment
- Top padding / attribution start Y
- Title font, lyrics font, annotation font, attribution font, footnote font, Bangla font

### Text Blocks (per character, unified stack)
- Lyrics
- Under-lyrics
- Bangla lyrics
- Translated lyrics
- Footnotes
- Attribution

---

## What Is NOT Undoable

- Zoom level
- Playback settings (instrument, tempo slider)
- Window size and position
- Application preferences

---

## Unified Text Stack Integration

All Swing text components that back Composition fields (lyrics text areas, footnote area, etc.) are
instrumented to forward their `UndoableEdit` events to the global `UndoManager` rather than an
internal per-component manager. This means:

- Typing four characters in a lyrics field pushes four undo entries onto the global stack.
- Cmd+Z always operates chronologically across both text and music edits.
- Example sequence: insert note A, insert note B, type "do", "re", "mi", "fa" → undoing five times
  produces: undo "fa", undo "mi", undo "re", undo "do", undo note B.

**Implementation note:** Override `createDefaultUndoManager()` or install a custom `Document` listener
on each affected `JTextComponent` that delegates `undoableEditHappened` events to the global manager
and suppresses the component's own undo handling.

---

## Document Modified Flag

The `Composition.isModified()` flag (drives title bar asterisk and "save before quit" prompts)
interacts with the undo stack as follows:

- On **File → Save**, record the current stack depth as the **clean position**.
- After each undo or redo operation, compare the current stack position to the clean position:
  - If they match, call `composition.setModified(false)`.
  - Otherwise, call `composition.setModified(true)`.
- If the 100-step limit evicts entries and the clean position falls off the back of the stack, the
  clean position is **invalidated**. The document remains permanently modified until the user saves
  again.

---

## UX

### Edit Menu

```
Edit
├── Undo [operation name]    Cmd+Z
├── Redo [operation name]    Cmd+Shift+Z
├── ─────────────────────────────────────
├── Cut                      Cmd+X
├── Copy                     Cmd+C
└── Paste                    Cmd+V
```

- When the undo stack is empty, **"Undo"** is grayed out (no label suffix).
- When the redo stack is empty, **"Redo"** is grayed out.
- Label examples: "Undo Add Note", "Undo Change Title", "Undo Paste", "Redo Toggle Trill".

### Keyboard Shortcuts

| Action | Shortcut |
|---|---|
| Undo | Cmd+Z |
| Redo | Cmd+Shift+Z |

### Empty Stack Behavior

When the stack is empty and Cmd+Z / Cmd+Shift+Z is pressed: **silently ignored**. The menu item is
disabled; no beep, no toast.

### Playback Interaction

Undo and Redo are **disabled during MIDI playback**. Both menu items gray out when playback starts and
re-enable when playback stops. Implemented via `playbackStateDidChange()` /
`enableFromPlaybackState()` in `UndoAction` and `RedoAction`.

---

## Action Integration

`UndoAction` and `RedoAction` extend `UIAction` and are registered in the `Actions` class, following
the existing action pattern. They:

- Override `actionPerformed` to call `undoManager.undo()` / `undoManager.redo()`.
- Override `updateEnabledState()` to enable/disable based on `undoManager.canUndo()` /
  `undoManager.canRedo()` and playback state.
- Dynamically update their name (via `setName()`) to reflect the next undoable/redoable operation's
  display name, so the Edit menu label updates on each stack change.

---

## UndoManager Location

The global `UndoManager` lives as a field on `MainFrame`. It is accessible via
`MainFrame.getInstance().getUndoManager()`. This gives every UIAction, dialog, and text component
listener a single access point without introducing new singletons.

---

## Edge Cases

### File → New / File → Open
Clears both the undo and redo stacks and invalidates the clean position. The new document starts
with an empty history.

### Save Marker Eviction
If saving occurred more than 100 edits ago and the saved-state position has been evicted from the
stack, the document is considered permanently modified until the next explicit save.

### Redo Stack Discard
Any new edit (note insertion, text keystroke, dialog OK) discards the entire redo stack. No
confirmation is shown.

### Drag Position
`mousePressed` records the note's starting xOffset/yPos. Intermediate `mouseDragged` events update
the note but do not push to the undo stack. `mouseReleased` pushes one command capturing
before/after positions. If the note did not move, no entry is pushed.

### Insertion-Note Session
Entering insertion-note mode begins a `CompoundEdit`. Every pitch/duration change during the session is
part of that compound. Exiting insertion-note mode (click away, Escape, or switching mode) closes and
commits the compound. If the note was not changed, the compound is discarded.

### Paste With Empty Clipboard
No undo entry is created.

### Dialog OK With No Changes
The pre-dialog snapshot is compared to the post-OK state field by field. If nothing changed, no
undo entry is created.
