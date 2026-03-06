# Implementation Plan: Toolbar Selection Reflection

**Spec:** `specs/issue-80-toolbar-reflection.md`
**Issue:** #80

This plan provides step-by-step implementation details for every change described in the spec. Each section includes exact code, affected files, and the order of operations.

IMPORTANT: Follow the code rules in .claude/rules/code-styles/java+kotlin.md.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Add `LOW_PRIORITY` to `Message.kt`](#-phase-1-add-low_priority-to-messagekt) | ✅ Complete | — |
| 2 | [Merge `DurationAction` + `NonDurationAction` into `NoteTypeAction`](#-phase-2-merge-durationaction--nondurationaction-into-notetypeaction) | ✅ Complete | — |
| 3 | [Add `UIAction.Reflectable` Interface](#-phase-3-add-uiactionreflectable-interface) | ✅ Complete | — |
| 4 | [Add `NoteOnlyAction` Intermediate Class](#-phase-4-add-noteonlyaction-intermediate-class) | ✅ Complete | — |
| 5 | [Implement `Reflectable` on Action Classes](#-phase-5-implement-reflectable-on-action-classes) | ✅ Complete | — |
| 6 | [Create `AccidentalInParensAction`](#-phase-6-create-accidentalinparensaction) | ✅ Complete | — |
| 7 | [Add `instanceof Reflectable` Guard in `updateEnabledState()`](#-phase-7-add-instanceof-reflectable-guard-in-updateenabledstate) | ✅ Complete | — |
| 8 | [Add `getSelectedNotes()` to `SelectionCoordinator`](#-phase-8-add-getselectednotes-to-selectioncoordinator) | ✅ Complete | — |
| 9 | [Add Reflection Handler to `SelectionCoordinator`](#-phase-9-add-reflection-handler-to-selectioncoordinator) | ✅ Complete | — |

## Implementation Order

The changes should be implemented in this order to keep the build green at each step:

1. Add `LOW_PRIORITY` constant to `Message.kt`
2. Merge `DurationAction` + `NonDurationAction` into `NoteTypeAction` (refactor, no new behavior)
3. Add `UIAction.Reflectable` interface
4. Add `NoteOnlyAction` intermediate class
5. Implement `Reflectable` on all action classes
6. Create `AccidentalInParensAction`
7. Add `instanceof Reflectable` guard in `updateEnabledState()`
8. Add `getSelectedNotes()` to `SelectionCoordinator`
9. Add reflection handler to `SelectionCoordinator`

---

## ✅ Phase 1: Add `LOW_PRIORITY` to `Message.kt`

**File:** `src/main/java/songscribe/ui/message/Message.kt`

The existing priority constants are:
```kotlin
const val HIGH_PRIORITY = 27
const val MEDIUM_PRIORITY = 13
```

mbassy: higher number = fires first. `UIAction.musicSelectionDidChange` uses `MEDIUM_PRIORITY` (13). The reflection handler must fire after all `UIAction` handlers, so it needs a lower value.

**Add:**
```kotlin
const val LOW_PRIORITY = 0
```

---

## ✅ Phase 2: Merge `DurationAction` + `NonDurationAction` into `NoteTypeAction`

### 2a. Create `NoteTypeAction.java`

**New file:** `src/main/java/songscribe/ui/action/NoteTypeAction.java`

```java
public class NoteTypeAction extends StickyUIAction {

    public enum Kind { DURATION, NON_DURATION }

    private final NoteType type;
    private final Kind kind;

    public NoteTypeAction(
        Kind kind,
        NoteType type,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(name, icon, size, actionCommand, tooltip, virtualKey, modifiers);
        this.kind = kind;
        this.type = type;

        if (kind == Kind.NON_DURATION) {
            setFlags(
                Flag.DISABLE_IN_REST_MODE,
                Flag.DISABLE_WHEN_PLAYING,
                Flag.DISABLE_IN_ADJUSTMENT_MODE,
                Flag.DISABLE_WHEN_EDITING_TEXT
            );
        } else {
            setFlags(
                Flag.DISABLE_WHEN_PLAYING,
                Flag.DISABLE_IN_ADJUSTMENT_MODE,
                Flag.DISABLE_WHEN_EDITING_TEXT
            );
        }
    }

    public NoteType getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (doActionPerformed(e)) {
            if (kind == Kind.DURATION) {
                MessageCenter.post(new DurationSelectedMessage(type));
            } else {
                MessageCenter.post(new BarSelectedMessage(type));
            }
        }
    }
}
```

### 2b. Update references

| File | Change |
|---|---|
| `DurationActionGroup.java` | Change `ActionGroup<DurationAction>` to `ActionGroup<NoteTypeAction>`, constructor param type |
| `NonDurationActionGroup.java` | Change `ActionGroup<NonDurationAction>` to `ActionGroup<NoteTypeAction>` |
| `DurationToolbar.java` | Change `List<DurationAction>` to `List<NoteTypeAction>` |
| `Actions.java` | Change all `DurationAction` field types to `NoteTypeAction`, add `Kind.DURATION` as first constructor arg. Change all `NonDurationAction` field types to `NoteTypeAction`, add `Kind.NON_DURATION` as first constructor arg. |

### 2c. Delete old files

- Delete `src/main/java/songscribe/ui/action/DurationAction.java`
- Delete `src/main/java/songscribe/ui/action/NonDurationAction.java`

---

## ✅ Phase 3: Add `UIAction.Reflectable` Interface

**File:** `src/main/java/songscribe/ui/action/UIAction.java`

Add as a nested interface inside `UIAction`, after the `Flag` enum:

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

---

## ✅ Phase 4: Add `NoteOnlyAction` Intermediate Class

**New file:** `src/main/java/songscribe/ui/action/NoteOnlyAction.java`

```java
/**
 * Abstract base for actions whose attributes apply only to notes
 * (not rests or barlines). Provides a shared appliesTo implementation.
 */
public abstract class NoteOnlyAction extends InsertionNoteAction
    implements UIAction.Reflectable {

    // Forward all InsertionNoteAction constructors used by subclasses

    public NoteOnlyAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        boolean isToggle
    ) {
        super(name, icon, size, actionCommand, tooltip, isToggle);
    }

    public NoteOnlyAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        boolean isToggle,
        int virtualKey,
        int modifiers
    ) {
        super(name, icon, size, actionCommand, tooltip, isToggle, virtualKey, modifiers);
    }

    @Override
    public boolean appliesTo(Note note) {
        return note.getNoteType().isNote();
    }
}
```

The constructors forward the signatures used by the four subclasses:
- `AccidentalAction` uses the 8-arg constructor (with virtualKey/modifiers)
- `ForceArticulationAction` uses the 6-arg constructor (without virtualKey/modifiers)
- `FermataAction` uses the 6-arg constructor
- `DurationArticulationAction` uses the 6-arg constructor

---

## ✅ Phase 5: Implement `Reflectable` on Action Classes

### 5a. `NoteTypeAction` — add `implements UIAction.Reflectable`

```java
public class NoteTypeAction extends StickyUIAction implements UIAction.Reflectable {
    // ...existing code...

    @Override
    public boolean appliesTo(Note note) {
        if (kind == Kind.DURATION) {
            return true;
        }

        var noteType = note.getNoteType();
        return noteType.isBarLine() || noteType.isRepeat() || noteType == NoteType.BREATH_MARK;
    }

    @Override
    public boolean matchesNote(Note note) {
        return note.getNoteType() == type;
    }
}
```

### 5b. `AccidentalAction` — extend `NoteOnlyAction` instead of `InsertionNoteAction`

Change: `extends InsertionNoteAction` to `extends NoteOnlyAction`

Add `matchesNote`:
```java
@Override
public boolean matchesNote(Note note) {
    return note.getAccidental() == accidental;
}
```

`appliesTo` is inherited from `NoteOnlyAction`.

### 5c. `ForceArticulationAction` — extend `NoteOnlyAction`

Change: `extends InsertionNoteAction` to `extends NoteOnlyAction`

The `ForceArticulation` enum currently has only `ACCENT`, and the class doesn't store which articulation it represents. Since `Note.forceArticulation` is `@Nullable` (defaults to `null`, set to `ForceArticulation.ACCENT` when present), `matchesNote` checks for non-null:

```java
@Override
public boolean matchesNote(Note note) {
    return note.getForceArticulation() != null;
}
```

Note: If `ForceArticulation` gains more values in the future, store the value in the constructor and compare with `==`. For now, there is only one instance (`ACCENT_ACTION`) and one enum value.

### 5d. `FermataAction` — extend `NoteOnlyAction`

Change: `extends InsertionNoteAction` to `extends NoteOnlyAction`

```java
@Override
public boolean matchesNote(Note note) {
    return note.isFermata();
}
```

### 5e. `DurationArticulationAction` — extend `NoteOnlyAction`

Change: `extends InsertionNoteAction` to `extends NoteOnlyAction`

```java
@Override
public boolean matchesNote(Note note) {
    return note.getDurationArticulation() == articulation;
}
```

The `articulation` field (`DurationArticulation` enum) is already stored. `Note.durationArticulation` defaults to `null`; `DurationArticulation.STACCATO` when present. Comparison with `==` is correct for enums.

### 5f. `DotAction` — implement `Reflectable` directly

`DotAction` extends `InsertionNoteAction` (not `NoteOnlyAction`) because its `appliesTo` includes rests.

Change: add `implements UIAction.Reflectable`

```java
public class DotAction extends InsertionNoteAction implements UIAction.Reflectable {
    // ...existing code...

    @Override
    public boolean appliesTo(Note note) {
        var noteType = note.getNoteType();
        return noteType.isNote() || noteType.isRest();
    }

    @Override
    public boolean matchesNote(Note note) {
        return switch (dotLevel) {
            case NONE -> note.getDotCount() == 0;
            case SINGLE -> note.getDotCount() == 1;
            case DOUBLE -> note.getDotCount() == 2;
        };
    }
}
```

`DotLevel` enum: `NONE`, `SINGLE`, `DOUBLE`.
`Note.getDotCount()` returns `int` (0, 1, or 2).

Note: There is no `DotAction` instance for `DotLevel.NONE` in `Actions.java` (only `DOT_ACTION` = SINGLE, `DOUBLE_DOT_ACTION` = DOUBLE). So the `NONE` case in `matchesNote` will not be reached during reflection, but is included for completeness.

---

## ✅ Phase 6: Create `AccidentalInParensAction`

**New file:** `src/main/java/songscribe/ui/action/AccidentalInParensAction.java`

```java
public class AccidentalInParensAction extends NoteOnlyAction {

    public AccidentalInParensAction() {
        super(
            "In Parentheses",
            null,
            0,
            "accidental-in-parens",
            "Add accidental in parentheses",
            true
        );
        setFlags(
            Flag.DISABLE_IN_ADJUSTMENT_MODE
        );
    }

    @Override
    public boolean matchesNote(Note note) {
        return note.isAccidentalInParentheses();
    }
}
```

**Update `Actions.java`:**

Change:
```java
public static final UIAction ACCIDENTAL_IN_PARENS_ACTION = new UIAction(
    "In Parentheses", null, 0, "accidental-in-parens",
    "Add accidental in parentheses", true
);

static {
    ACCIDENTAL_IN_PARENS_ACTION.setFlags(UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE);
}
```

To:
```java
public static final AccidentalInParensAction ACCIDENTAL_IN_PARENS_ACTION =
    new AccidentalInParensAction();
```

Remove the `static` initializer block that set flags (flags are now in the constructor).

---

## ✅ Phase 7: Add `instanceof Reflectable` Guard in `updateEnabledState()`

**File:** `src/main/java/songscribe/ui/action/UIAction.java`

Modify `updateEnabledState()`. At the top, before the existing logic, add:

```java
protected boolean updateEnabledState() {
    // During active selection, reflectable actions are controlled
    // by the reflection handler, not by flag-based logic.
    if (this instanceof Reflectable) {
        var score = MainFrame.getInstance().getScore();
        if (score.getSelectionCoordinator().getSelectionSize() >= 1) {
            return isEnabled();
        }
    }

    // ...existing flag-based logic unchanged...
}
```

When the guard triggers, it returns early preserving the current enabled state. The reflection handler (which fires at lower priority, after this method) will set both selected and enabled state.

---

## ✅ Phase 8: Add `getSelectedNotes()` to `SelectionCoordinator`

**File:** `src/main/java/songscribe/ui/selection/SelectionCoordinator.java`

```java
/**
 * Returns the notes in the active selection, or an empty list if nothing is selected.
 */
public List<Note> getSelectedNotes() {
    var selection = getSelection();

    if (selection == null) {
        return List.of();
    }

    var line = selection.line();
    var notes = new ArrayList<Note>(selection.end() - selection.begin() + 1);

    for (var i = selection.begin(); i <= selection.end(); i++) {
        notes.add(line.getNote(i));
    }

    return notes;
}
```

---

## ✅ Phase 9: Add Reflection Handler to `SelectionCoordinator`

**File:** `src/main/java/songscribe/ui/selection/SelectionCoordinator.java`

### 9a. New fields

```java
private List<UIAction.Reflectable> reflectableActions = null;  // lazy-init
private final Map<UIAction, Boolean> savedToggleStates = new HashMap<>();
private boolean hasSavedState = false;
```

### 9b. Discovery method (lazy)

```java
private List<UIAction.Reflectable> getReflectableActions() {
    if (reflectableActions == null) {
        reflectableActions = new ArrayList<>();

        for (var field : Actions.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                var value = field.get(null);

                if (value instanceof UIAction[] array) {
                    for (var action : array) {
                        if (action instanceof UIAction.Reflectable reflectable) {
                            reflectableActions.add(reflectable);
                        }
                    }
                } else if (value instanceof UIAction.Reflectable reflectable) {
                    reflectableActions.add(reflectable);
                }
            } catch (IllegalAccessException e) {
                // Non-public fields are skipped
            }
        }
    }

    return reflectableActions;
}
```

This scans all static fields of `Actions`. Fields whose values implement `UIAction.Reflectable` are collected. Array fields (like `REPEAT_ACTIONS`, `BARLINE_ACTIONS`) are not `UIAction` instances themselves -- their elements are. These need special handling:

`REPEAT_ACTIONS` and `BARLINE_ACTIONS` are `NoteTypeAction[]`. After the merge, their elements are `NoteTypeAction implements Reflectable`. The array itself is `NoteTypeAction[]`, not `Reflectable[]`. So we check for arrays of UIAction too (handled in the code above).

### 9c. Message subscription

In the `SelectionCoordinator` constructor, add:
```java
MessageCenter.subscribe(this);
```

`SelectionCoordinator` is held by `Score` (strong reference), so it won't be GC'd by mbassy's weak references.

### 9d. Handler method

```java
@Handler(priority = Message.LOW_PRIORITY)
public void reflectSelection(MusicSelectionChangedMessage message) {
    var actions = getReflectableActions();
    var selectionSize = getSelectionSize();

    // Selection cleared -- restore saved state
    if (selectionSize == 0) {
        if (hasSavedState) {
            for (var entry : savedToggleStates.entrySet()) {
                entry.getKey().setSelected(entry.getValue());
            }

            savedToggleStates.clear();
            hasSavedState = false;
        }

        return;
    }

    // Selection just became active -- save current toggle states
    if (!hasSavedState) {
        for (var reflectable : actions) {
            var action = (UIAction) reflectable;
            savedToggleStates.put(action, action.isSelected());
        }

        hasSavedState = true;
    }

    // Reflect selection attributes
    var selectedNotes = getSelectedNotes();

    for (var reflectable : actions) {
        var action = (UIAction) reflectable;
        var applicable = false;
        var matched = true;

        for (var note : selectedNotes) {
            if (!reflectable.appliesTo(note)) {
                continue;
            }

            applicable = true;

            if (!reflectable.matchesNote(note)) {
                matched = false;
                break;
            }
        }

        action.setSelected(applicable && matched);
        action.setEnabled(true);
    }
}
```

### 9e. Required imports

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.engio.mbassy.listener.Handler;

import songscribe.music.Note;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.message.Message;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MusicSelectionChangedMessage;
```

---

## Complete `matchesNote` Reference

| Action Class | Field | Note getter | Comparison |
|---|---|---|---|
| `NoteTypeAction` | `NoteType type` | `note.getNoteType()` | `== type` |
| `AccidentalAction` | `Note.Accidental accidental` | `note.getAccidental()` | `== accidental` |
| `DotAction` | `DotLevel dotLevel` | `note.getDotCount()` | SINGLE: `== 1`, DOUBLE: `== 2`, NONE: `== 0` |
| `ForceArticulationAction` | (none stored) | `note.getForceArticulation()` | `!= null` |
| `FermataAction` | (none stored) | `note.isFermata()` | `== true` |
| `DurationArticulationAction` | `DurationArticulation articulation` | `note.getDurationArticulation()` | `== articulation` |
| `AccidentalInParensAction` | (none stored) | `note.isAccidentalInParentheses()` | `== true` |

---

## Complete `appliesTo` Reference

| Action Class | Logic | Expression |
|---|---|---|
| `NoteTypeAction(DURATION)` | All elements | `return true;` |
| `NoteTypeAction(NON_DURATION)` | Barlines, repeats, breath mark | `var t = note.getNoteType(); return t.isBarLine() \|\| t.isRepeat() \|\| t == NoteType.BREATH_MARK;` |
| `NoteOnlyAction` (shared) | Notes only | `return note.getNoteType().isNote();` |
| `DotAction` | Notes and rests | `var t = note.getNoteType(); return t.isNote() \|\| t.isRest();` |

---

## Files Summary

| File | Status | Change |
|---|---|---|
| `Message.kt` | Modify | Add `LOW_PRIORITY = 0` |
| `NoteTypeAction.java` | **New** | Merged from DurationAction + NonDurationAction, implements Reflectable |
| `DurationAction.java` | **Delete** | Replaced by NoteTypeAction |
| `NonDurationAction.java` | **Delete** | Replaced by NoteTypeAction |
| `DurationActionGroup.java` | Modify | Type param `DurationAction` to `NoteTypeAction` |
| `NonDurationActionGroup.java` | Modify | Type param `NonDurationAction` to `NoteTypeAction` |
| `DurationToolbar.java` | Modify | List type `DurationAction` to `NoteTypeAction` |
| `Actions.java` | Modify | Field types for NoteTypeAction, replace ACCIDENTAL_IN_PARENS_ACTION, remove static init block |
| `UIAction.java` | Modify | Add Reflectable interface, add instanceof guard in updateEnabledState |
| `NoteOnlyAction.java` | **New** | Abstract class with shared appliesTo for note-only actions |
| `AccidentalAction.java` | Modify | Extend NoteOnlyAction, add matchesNote |
| `ForceArticulationAction.java` | Modify | Extend NoteOnlyAction, add matchesNote |
| `FermataAction.java` | Modify | Extend NoteOnlyAction, add matchesNote |
| `DurationArticulationAction.java` | Modify | Extend NoteOnlyAction, add matchesNote |
| `AccidentalInParensAction.java` | **New** | Extends NoteOnlyAction, matchesNote for isAccidentalInParentheses |
| `DotAction.java` | Modify | Implement Reflectable directly, add appliesTo + matchesNote |
| `SelectionCoordinator.java` | Modify | Add reflection handler, getSelectedNotes, lazy discovery, saved state |

**Total: 17 files (3 new, 2 deleted, 12 modified)**

---

## Test Plan

### A. Reflection Handler — State Transitions (4 tests)

| ID | Setup | Action | Assert |
|---|---|---|---|
| H1 | No selection, hasSavedState=false | Fire message with selectionSize=0 | No-op, no state changes |
| H2 | Selection active, hasSavedState=true, saved states stored | Fire message with selectionSize=0 | Each reflectable action's selected state restored to saved value, hasSavedState=false |
| H3 | No selection, hasSavedState=false | Fire message with selectionSize>=1 | Current toggle states saved, hasSavedState=true, then reflection runs |
| H4 | Selection active, hasSavedState=true | Fire message with different selectionSize>=1 | No re-save, reflection runs with new selection |

### B. Reflection Handler — Core Logic (6 tests)

| ID | Selection content | Action under test | Assert selected |
|---|---|---|---|
| R1 | 2 notes, both applicable, both match | Any reflectable | true |
| R2 | 2 notes, both applicable, first doesn't match | Any reflectable | false |
| R3 | 2 notes, both applicable, second doesn't match | Any reflectable | false |
| R4 | Note + rest, action applies to notes only | NoteOnlyAction subclass | true (rest skipped, note matches) |
| R5 | Note + rest, action applies to notes only, note doesn't match | NoteOnlyAction subclass | false |
| R6 | 2 rests, action applies to notes only | NoteOnlyAction subclass | false (none applicable) |

### C. updateEnabledState Short-Circuit (3 tests)

| ID | Action type | selectionSize | Assert |
|---|---|---|---|
| U1 | Reflectable | >= 1 | Returns early, enabled state preserved |
| U2 | Reflectable | 0 | Normal flag logic runs |
| U3 | Non-reflectable | >= 1 | Normal flag logic runs |

### D. appliesTo per Action Type (12 tests)

| ID | Action | Note type | Assert |
|---|---|---|---|
| A1 | NoteTypeAction(DURATION) | note | true |
| A2 | NoteTypeAction(DURATION) | rest | true |
| A3 | NoteTypeAction(DURATION) | barline | true |
| A4 | NoteTypeAction(NON_DURATION) | note | false |
| A5 | NoteTypeAction(NON_DURATION) | rest | false |
| A6 | NoteTypeAction(NON_DURATION) | barline | true |
| A7 | NoteOnlyAction | note | true |
| A8 | NoteOnlyAction | rest | false |
| A9 | NoteOnlyAction | barline | false |
| A10 | DotAction | note | true |
| A11 | DotAction | rest | true |
| A12 | DotAction | barline | false |

### E. matchesNote per Action Class (16 tests)

| ID | Action | Note state | Assert |
|---|---|---|---|
| M1 | NoteTypeAction(CROTCHET) | noteType=CROTCHET | true |
| M2 | NoteTypeAction(CROTCHET) | noteType=MINIM | false |
| M3 | AccidentalAction(SHARP) | accidental=SHARP | true |
| M4 | AccidentalAction(SHARP) | accidental=FLAT | false |
| M5 | DotAction(SINGLE) | dotCount=1 | true |
| M6 | DotAction(SINGLE) | dotCount=0 | false |
| M7 | DotAction(DOUBLE) | dotCount=2 | true |
| M8 | DotAction(DOUBLE) | dotCount=1 | false |
| M9 | ForceArticulationAction | forceArticulation=ACCENT | true |
| M10 | ForceArticulationAction | forceArticulation=null | false |
| M11 | FermataAction | fermata=true | true |
| M12 | FermataAction | fermata=false | false |
| M13 | DurationArticulationAction(STACCATO) | durationArticulation=STACCATO | true |
| M14 | DurationArticulationAction(STACCATO) | durationArticulation=null | false |
| M15 | AccidentalInParensAction | isAccidentalInParentheses=true | true |
| M16 | AccidentalInParensAction | isAccidentalInParentheses=false | false |

### F. Force-Enable (2 tests)

| ID | State | Assert |
|---|---|---|
| E1 | Selection active after reflection | All reflectable actions have setEnabled(true) |
| E2 | Selection cleared after restore | Enabled state determined by normal updateEnabledState logic |

### G. Integration Scenarios (6 tests)

| ID | Selection | Duration btn | Accidental btn | Dot btn | Barline btn |
|---|---|---|---|---|---|
| I1 | 2 crotchets, both sharp, dotCount=0 | CROTCHET=selected | SHARP=selected | DOT=deselected | all deselected |
| I2 | Crotchet + minim, both sharp | CROTCHET=deselected, MINIM=deselected | SHARP=selected | per dots | all deselected |
| I3 | Crotchet + crotchet_rest | all duration=deselected (exact match) | SHARP=selected (rest skipped) | per dots | all deselected |
| I4 | 2 crotchet_rests | CROTCHET_REST not a duration btn | all accidental=deselected (none applicable) | per dots | all deselected |
| I5 | 2 single_barlines | all duration=deselected (none applicable) | all=deselected | all=deselected | SINGLE_BARLINE=selected |
| I6 | Single crotchet, sharp, dotCount=1, fermata | CROTCHET=selected | SHARP=selected | DOT=selected | all deselected, FERMATA=selected |

### H. NoteTypeAction Merge Regression (2 tests)

| ID | Kind | Assert |
|---|---|---|
| T1 | DURATION | actionPerformed posts DurationSelectedMessage; flags exclude DISABLE_IN_REST_MODE |
| T2 | NON_DURATION | actionPerformed posts BarSelectedMessage; flags include DISABLE_IN_REST_MODE |

**Total: 51 test cases** (4 + 6 + 3 + 12 + 16 + 2 + 6 + 2)
