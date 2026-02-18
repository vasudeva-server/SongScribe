# Plan: Remove LayoutManager Completely

**Parent:** [score-layout-redesign.md](./score-layout-redesign.md) → Phase 2
**Captured:** 2026-01-31
**Status:** Completed

---

## Context

This plan completes **Phase 2** from `score-layout-redesign.md` and fully removes the legacy LayoutManager from screen rendering. This work builds on:
- **Phase 1-6** (Complete): Component hierarchy exists, LineLayoutEngine implemented
- **Phase 8.4, 9** (Complete): Legacy Renderer removed, ScoreRenderer orchestrates
- **This work**: Remove LayoutManager dependency, make components fully self-sufficient

## Goal

**Completely remove LayoutManager** from the codebase for screen rendering. Exports will be stubbed out temporarily to avoid maintaining two layout systems.

---

## Implementation Plan

### Step 1: Create NoteSpacing utility class

**New file**: `src/main/java/songscribe/ui/layout/NoteSpacing.java`

Extract horizontal spacing utilities (not related to vertical layout):

```java
package songscribe.ui.layout;

import java.util.EnumMap;
import org.jetbrains.annotations.NotNull;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;

/**
 * Horizontal spacing utilities for note positioning.
 */
public final class NoteSpacing {

    public static final int ACCIDENTAL_WIDTH = 7;

    private static int firstNoteX = 100;

    private static final EnumMap<NoteType, Integer> NOTE_SPACING = new EnumMap<>(NoteType.class);

    static {
        NOTE_SPACING.put(NoteType.SEMIBREVE, 70);
        NOTE_SPACING.put(NoteType.MINIM, 50);
        NOTE_SPACING.put(NoteType.CROTCHET, 35);
        NOTE_SPACING.put(NoteType.QUAVER, 25);
        NOTE_SPACING.put(NoteType.SEMIQUAVER, 25);
        NOTE_SPACING.put(NoteType.DEMI_SEMIQUAVER, 25);
        NOTE_SPACING.put(NoteType.SEMIBREVE_REST, 70);
        NOTE_SPACING.put(NoteType.MINIM_REST, 50);
        NOTE_SPACING.put(NoteType.CROTCHET_REST, 35);
        NOTE_SPACING.put(NoteType.QUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.SEMIQUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.DEMI_SEMIQUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.GRACE_QUAVER, 30);
        NOTE_SPACING.put(NoteType.GRACE_SEMIQUAVER, 50);
        NOTE_SPACING.put(NoteType.GLISSANDO, 0);
        NOTE_SPACING.put(NoteType.REPEAT_LEFT, 25);
        NOTE_SPACING.put(NoteType.REPEAT_RIGHT, 25);
        NOTE_SPACING.put(NoteType.REPEAT_LEFT_RIGHT, 25);
        NOTE_SPACING.put(NoteType.BREATH_MARK, 15);
        NOTE_SPACING.put(NoteType.SINGLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.DOUBLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.FINAL_DOUBLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.PASTE, 0);
    }

    private NoteSpacing() {}

    public static int calculateLastNoteXPos(@NotNull Line line, Note note) {
        if (line.noteCount() == 0) {
            return firstNoteX;
        }
        var lastNote = line.getNote(line.noteCount() - 1);
        return (
            lastNote.getXPos() +
            Math.round(
                (NOTE_SPACING.get(lastNote.getNoteType()) +
                    (note.getAccidental().getWidthFactor() * ACCIDENTAL_WIDTH) +
                    (note.isAccidentalInParentheses() ? 8 : 0)) *
                line.getNoteDistChangeRatio()
            )
        );
    }

    public static int getNoteSpacing(@NotNull NoteType noteType) {
        return NOTE_SPACING.getOrDefault(noteType, 0);
    }

    public static int getFirstNoteX() {
        return firstNoteX;
    }

    public static void setFirstNoteX(int x) {
        firstNoteX = x;
    }
}
```

### Step 2: Add ANNOTATION_MARGIN to LayoutStylesheet

**Modify**: `src/main/java/songscribe/ui/layout/LayoutStylesheet.java`

Add pixel constant near `ANNOTATION_REGION_MARGIN`:
```java
/** Pixel value of annotation margin (for backward compatibility) */
public static final int ANNOTATION_MARGIN = px(ANNOTATION_REGION_MARGIN);
```

### Step 3: Update all static utility callers

Replace `LayoutManager.` with `NoteSpacing.` or `LayoutStylesheet.`:

**CompositionIO.java**:
- Change import: `LayoutManager` → `NoteSpacing`
- Change call: `LayoutManager.calculateLastNoteXPos` → `NoteSpacing.calculateLastNoteXPos`

**HorizontalAdjustment.java**:
- Change import: `LayoutManager` → `NoteSpacing`
- Change call: `LayoutManager.setFirstNoteX` → `NoteSpacing.setFirstNoteX`

**Score.java** (multiple locations):
- Change import: add `NoteSpacing`
- All `LayoutManager.calculateLastNoteXPos` → `NoteSpacing.calculateLastNoteXPos`
- All `LayoutManager.getNoteSpacing` → `NoteSpacing.getNoteSpacing`
- All `LayoutManager.ACCIDENTAL_WIDTH` → `NoteSpacing.ACCIDENTAL_WIDTH`

**BoundsCalculator.java**:
- Change: `LayoutManager.ANNOTATION_MARGIN` → `LayoutStylesheet.ANNOTATION_MARGIN`

### Step 4: Remove LayoutManager from Score

**Modify**: `src/main/java/songscribe/ui/component/Score.java`

1. **Remove field**:
   ```java
   // DELETE: private LayoutManager layoutManager = null;
   ```

2. **Remove from constructor**:
   ```java
   // DELETE: layoutManager = new LayoutManager(this);
   ```

3. **Remove import**:
   ```java
   // DELETE: import songscribe.ui.layout.LayoutManager;
   ```

4. **Remove getLayoutManager() method** (or make it return null/throw):
   ```java
   // DELETE or stub:
   public LayoutManager getLayoutManager() {
       throw new UnsupportedOperationException("LayoutManager removed - use component hierarchy");
   }
   ```

5. **Modify paintComponent()**:
   ```java
   public void paintComponent(Graphics g) {
       mainPanel.setVisible(useComponentRendering);
       super.paintComponent(g);

       var g2 = (Graphics2D) g;
       g2.setColor(Color.white);
       g2.fillRect(0, 0, marginPanel.getWidth(), marginPanel.getHeight());

       // Derive coordinates from positioned components
       updateLayoutFromComponents();

       // Legacy ScoreRenderer path removed - exports stubbed
       if (!useComponentRendering) {
           // TODO: Migrate exports to component-based rendering
           System.err.println("WARNING: Legacy rendering disabled");
       }

       drawEditElements(g2);
       drawSelectionRect(g2);
       drawDebugOverlays(g2);
   }

   // DELETE: updateLayoutFromManager() method
   ```

6. **Add updateLayoutFromComponents()**:
   ```java
   private void updateLayoutFromComponents() {
       if (mainPanel == null) return;

       var staffPanel = mainPanel.getStaffPanel();
       if (staffPanel == null) return;

       var linePanels = staffPanel.getLinePanels();
       if (linePanels.isEmpty()) return;

       // middleLineY = first line's absolute middle Y
       middleLineY = getActualLineMiddleY(0);

       // rowHeight = distance between consecutive line midpoints
       if (linePanels.size() >= 2) {
           rowHeight = getActualLineMiddleY(1) - getActualLineMiddleY(0);
       } else {
           var linePanel = linePanels.get(0);
           rowHeight = linePanel.getLineComponent().getHeight()
                     + LayoutStylesheet.px(LayoutStylesheet.LINE_MARGIN_BOTTOM);
       }
   }
   ```

7. **Remove debug logging** (System.out.printf calls around line 774-778)

### Step 5: Stub RenderContext.getLayoutManager()

**Modify**: `src/main/java/songscribe/ui/renderer/RenderContext.java`

Change interface method to return null or remove:
```java
/**
 * @deprecated LayoutManager removed. Returns null.
 */
@Deprecated
@Nullable
LayoutManager getLayoutManager();
```

### Step 6: Stub ScoreRenderer for exports

**Modify**: `src/main/java/songscribe/ui/renderer/ScoreRenderer.java`

Stub the render method:
```java
public void render(Graphics2D g2, boolean editMode, double scale) {
    // TODO: Migrate exports to component-based rendering
    System.err.println("WARNING: ScoreRenderer.render() stubbed - exports disabled");

    // Draw a placeholder message
    g2.setColor(Color.RED);
    g2.drawString("Export rendering disabled - migration in progress", 50, 50);
}
```

### Step 7: Update Score's RenderContext implementation

**Modify**: `src/main/java/songscribe/ui/component/Score.java`

Update the `getLayoutManager()` implementation in the anonymous `RenderContext`:
```java
@Override
public LayoutManager getLayoutManager() {
    return null;  // LayoutManager removed
}
```

---

## Files to Create

| File | Description |
|------|-------------|
| `src/main/java/songscribe/ui/layout/NoteSpacing.java` | Horizontal spacing utilities |

## Files to Modify

| File | Changes |
|------|---------|
| `Score.java` | Remove layoutManager field, remove import, modify paintComponent(), add updateLayoutFromComponents(), update static calls |
| `CompositionIO.java` | Change import and calls to NoteSpacing |
| `HorizontalAdjustment.java` | Change import and calls to NoteSpacing |
| `BoundsCalculator.java` | Change ANNOTATION_MARGIN to LayoutStylesheet |
| `LayoutStylesheet.java` | Add ANNOTATION_MARGIN constant |
| `RenderContext.java` | Deprecate/stub getLayoutManager() |
| `ScoreRenderer.java` | Stub render() method |

## Files NOT Modified (Keep for Reference)

| File | Reason |
|------|--------|
| `LayoutManager.java` | Keep for now as reference, can delete later |

---

## What This Achieves

1. **LayoutManager completely removed from screen rendering path**
2. **Single source of truth** - component hierarchy only
3. **Horizontal spacing cleanly separated** in NoteSpacing
4. **No confusion** - can't accidentally use old system
5. **Exports stubbed** - clear indication they need migration

---

## Verification

1. **Compile**: `./scripts/compile.sh`
2. **Test** (after user runs application):
   - Open existing song - should render correctly
   - Edit mode - add/insert notes, positions should be correct
   - Drag adjustment handles - should work
   - Multi-line songs - all lines correct
3. **Verify exports are stubbed** - try export, should show warning

---

## Order of Implementation

1. Create `NoteSpacing.java` (new file, no dependencies)
2. Add `ANNOTATION_MARGIN` to `LayoutStylesheet.java`
3. Update callers: `CompositionIO`, `HorizontalAdjustment`, `BoundsCalculator`
4. Stub `ScoreRenderer.render()`
5. Stub `RenderContext.getLayoutManager()`
6. Remove LayoutManager from `Score.java` and add `updateLayoutFromComponents()`
7. Compile and test
