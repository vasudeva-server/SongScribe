# Plan: NoteSpacing to Layout2 APIs Migration

**Type:** Master Plan  <br>
**Created:** 2026-02-04  <br>
**Status:** ✅ Complete  <br>
**Category:** migrations

---

## Overview

Remove the legacy `NoteSpacing` class and replace all usages with layout2 APIs.

## Background Context

**Legacy NoteSpacing** (`songscribe.ui.layout.NoteSpacing`):
- Proportional spacing based on note type (crotchet=35px, quaver=25px, etc.)
- Mutable static state (`firstNoteX` field)
- Single accidental width constant (7px)

**Layout2** (`songscribe.ui.layout2.*`):
- Non-proportional, lyric-driven spacing (Gould/Ross principles)
- Dynamic first note position based on key signature
- Type-specific accidental widths (6-9px)
- Works with NoteColumn objects via HorizontalSpacingCalculator

## Status Dashboard

| Phase | Description                                                              | Files                           | Status     |
|-------|--------------------------------------------------------------------------|---------------------------------|------------|
| 1     | [Create InsertionSpacingCalculator](#-phase-1-create-bridge-utility)     | InsertionSpacingCalculator.java | ✅ Complete |
| 2     | [Migrate edit operations](#-phase-2-migrate-edit-operations)             | LineComponent, EditModeManager  | ✅ Complete |
| 3     | [Migrate file loading](#-phase-3-migrate-file-loading)                   | CompositionIO                   | ✅ Complete |
| 4     | [Migrate adjustment operations](#-phase-4-migrate-adjustment-operations) | HorizontalAdjustment            | ✅ Complete |
| 5     | [Migrate width calculations](#-phase-5-migrate-width-calculations)       | Score                           | ✅ Complete |
| 6     | [Remove NoteSpacing](#-phase-6-remove-notespacing)                       | NoteSpacing.java                | ✅ Complete |

---

## Files to Migrate

| File                      | Lines                      | Usage                              |
|---------------------------|----------------------------|------------------------------------|
| LineComponent.java        | 1053, 1143, 1147-1149      | addEditNote(), insertEditNote()    |
| EditModeManager.java      | 339, 354-356, 470, 473-477 | Paste operations, modifyEditNote() |
| CompositionIO.java        | 426                        | File loading                       |
| HorizontalAdjustment.java | 251                        | setFirstNoteX() global state       |
| Score.java                | 850                        | drawWidthIfWiderLine()             |

---

## ✅ Phase 1: Create Bridge Utility

**Status:** ✅ Complete  <br>
**Risk:** Low

Create `InsertionSpacingCalculator` in `songscribe.ui.layout2`:

```java
public class InsertionSpacingCalculator {
    // Calculate position for appending note to end of line
    public static double calculateAppendPosition(Line line, Note noteToAppend);

    // Calculate shift amount when inserting at index
    public static double calculateInsertionShift(Line line, Note insertedNote, int insertIndex);
}
```

Implementation: Create lightweight NoteColumns internally to leverage `HorizontalSpacingCalculator.calculateNextColumnX()`.

---

## ✅ Phase 2: Migrate Edit Operations

**Status:** ✅ Complete  <br>
**Risk:** Medium

### 2.1 LineComponent.addEditNote() (line 1053)

```java
// Before:
editNote.setXPos(NoteSpacing.calculateLastNoteXPos(line, editNote));

// After:
    editNote.

setXPos((int) Math.

round(
    InsertionSpacingCalculator.calculateAppendPosition(line, editNote)));
```

### 2.2 LineComponent.insertEditNote() (lines 1141-1155)

Replace NoteSpacing.getNoteSpacing() and ACCIDENTAL_WIDTH usage with InsertionSpacingCalculator.

### 2.3 EditModeManager Paste Operations (lines 339-363)

Same pattern - use InsertionSpacingCalculator for positioning and shifts.

### 2.4 EditModeManager.modifyEditNote() (lines 466-484)

This method is orphaned (not called anywhere). Remove it.

---

## ✅ Phase 3: Migrate File Loading

**Status:** ✅ Complete  <br>
**Risk:** Medium-High

### CompositionIO.endElement10() (line 426)

Use layout2 spacing for file loading:

```java
// Before:
note.setXPos(NoteSpacing.calculateLastNoteXPos(line, note));

// After:
    note.

setXPos((int) Math.

round(
    InsertionSpacingCalculator.calculateAppendPosition(line, note)));
```

Note: This may slightly reposition notes in existing compositions, which is acceptable.

---

## ✅ Phase 4: Migrate Adjustment Operations

**Status:** ✅ Complete  <br>
**Risk:** Medium

### HorizontalAdjustment.doAdjustment() (line 251)

The `setFirstNoteX()` modifies global state. Options:

1. Store custom first note X on Composition object
2. Trigger full re-layout instead of manual repositioning

Recommendation: Option 2 - HorizontalAdjustment should trigger re-layout via LayoutEngine rather than manually shifting notes.

---

## ✅ Phase 5: Migrate Width Calculations

**Status:** ✅ Complete  <br>
**Risk:** Low

### Score.drawWidthIfWiderLine() (line 850)

```java
// Before:
idealSpace =(NoteSpacing.

getNoteSpacing(endNote.getNoteType())*
    line.

getNoteDistChangeRatio())+20;

// After:
idealSpace =LayoutConstants.

px(LayoutConstants.DEFAULT_COLUMN_GAP) +20;
```

---

## ✅ Phase 6: Remove NoteSpacing

**Status:** ✅ Complete  <br>
**Risk:** Very Low

After all migrations complete:
1. Remove import statements from all files
2. Delete `NoteSpacing.java`

---

## Verification

1. **Compile**: `./scripts/compile.sh`
2. **Manual testing**:
    - Insert notes at end of line
    - Insert notes in middle of line
    - Paste operations
    - Load existing composition files
    - Horizontal adjustment operations
3. **Visual check**: Compare note spacing before/after migration

---

## Critical Files

- `src/main/java/songscribe/ui/layout/NoteSpacing.java` - To be removed
- `src/main/java/songscribe/ui/layout2/HorizontalSpacingCalculator.java` - Main spacing API
- `src/main/java/songscribe/ui/layout2/NoteColumnBuilder.java` - For creating NoteColumns
- `src/main/java/songscribe/ui/layout2/LayoutConstants.java` - Spacing constants
- `src/main/java/songscribe/ui/component/score/LineComponent.java` - Edit operations
- `src/main/java/songscribe/ui/edit/EditModeManager.java` - Paste operations
