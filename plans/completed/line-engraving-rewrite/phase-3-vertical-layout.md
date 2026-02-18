# Phase 3: Vertical Layout Implementation Plan

**Type:** Implementation Plan
**Parent:** [line-engraving-rewrite.md](line-engraving-rewrite.md) → Phase 3
**Created:** 2026-02-01
**Status:** ✅ Complete
**Completed:** 2026-02-01

---

## Overview

Implement the `VerticalStackingCalculator` to calculate Y positions for all elements above the staff using layer-by-layer stacking with proper bounding areas and margins.

## Phase Dependencies

**Requires:** Phase 2 Complete (✅)
- `NoteColumn` with X positions set
- `HorizontalSpacingCalculator` providing positioned columns
- `LayoutConstants` with all margin values

**Provides for Phase 4+:**
- Y positions for all elements above staff
- Bounding areas for each element
- Line height calculation
- Lyrics baseline position

---

## Design from Master Plan

### Stacking Order (Bottom to Top, Above Staff)

Per master plan lines 130-139:

1. **Note** (head, stem, flag, ledger lines)
2. **Articulations** (staccato, accent) - `ARTICULATION_MARGIN = 1.0 MU`
3. **Trill** - `TRILL_MARGIN = 0.5 MU`
4. **Fermata** - `FERMATA_MARGIN = 0.5 MU`
5. **Dynamics** (point only, ranges deferred) - `DYNAMICS_MARGIN = 0.5 MU`
6. **Tempo/beat change** - `TEMPO_MARGIN = 1.0 MU`
7. **Text annotations** - `ANNOTATION_MARGIN = 0.5 MU`

**Deferred to later phases:**
- First/second endings (range elements)
- Attribution (scope TBD)
- Crescendo/diminuendo (range elements)

### Below Staff

- **Lyrics**: `LYRICS_BASELINE_OFFSET = 2.5 MU` below lowest note bounding area

### Algorithm (from master plan lines 145-157)

```
For each note column:
    1. Initialize bounding area from note column bounds

    2. For each layer (articulations → attribution):
       a. Get elements attached to this note at this layer
       b. For each element:
          - Calculate element's content area
          - Find Y position that clears accumulated bounding area + margin
          - Update accumulated bounding area

    3. Track maximum height reached for line height calculation
```

### Key Principles

- Use bounding areas (not simple rectangles) for collision detection
- Elements can nest inside others (e.g., notes inside ending brackets)
- Collision = actual area intersection, not bounding box overlap
- Each layer builds on the previous layer's bounding area

---

## Current Data Model Analysis

### LineElement Hierarchy (from exploration)

**Base: `LineElement`** (`songscribe.ui.layout.LineElement`)
- Position (x, y) relative to line origin
- User offsets (userXOffset, userYOffset)
- Margins (top, right, bottom, left)
- Content bounds calculation
- Parent/child relationships

**Note: `Note extends LineElement`**
- Has lists of `Articulation` and `Attachment`
- Methods: `getArticulations()`, `getAttachments()`
- Legacy properties: `trill`, `fermata` (boolean flags)
- Legacy properties: `tempoChange`, `beatChange`, `annotation` (old-style)

**Articulation: `Articulation extends LineElement`**
- Type: `ArticulationType` (STACCATO, ACCENT, etc.)
- Parent note reference
- Default size: 8px

**Attachment: `Attachment extends LineElement`** (abstract)
- Alignment: LEFT, CENTER, RIGHT
- Placement: ABOVE, BELOW (will be removed in Phase 7)
- Concrete types:
  - `FermataAttachment` (16px)
  - `TempoAttachment` (60x20px)
  - `DynamicAttachment` (20x14px)
  - `BeatChangeAttachment`
  - `AnnotationAttachment`

### Legacy Properties to Handle

**Important:** Note class has **both** old-style properties AND new LineElement hierarchy:

1. **Old-style flags** (still present):
   - `note.isTrill()` / `note.setTrill(boolean)`
   - `note.isFermata()` / `note.setFermata(boolean)`
   - `note.getTempoChange()` / `note.setTempoChange(Tempo)`
   - `note.getBeatChange()` / `note.setBeatChange(BeatChange)`
   - `note.getAnnotation()` / `note.setAnnotation(Annotation)`

2. **New-style hierarchy**:
   - `note.getArticulations()` → List<Articulation>
   - `note.getAttachments()` → List<Attachment>

**Strategy for Phase 3:** Support BOTH during transition:
- Check legacy flags first (`isTrill()`, `isFermata()`, etc.)
- Also check new attachment lists
- Create virtual elements for legacy properties as needed
- Phase 7 will migrate fully to new hierarchy and remove legacy properties

---

## Implementation Design

### File to Create

**`src/main/java/songscribe/ui/layout2/VerticalStackingCalculator.java`**

### Class Structure

```java
public class VerticalStackingCalculator {

    // Constructor
    public VerticalStackingCalculator() {}

    // Main entry point
    public VerticalStackingResult calculateVerticalPositions(
        List<NoteColumn> columns,
        Line line,
        Graphics2D g2  // For text measurement
    )

    // Layer processing methods
    private void stackArticulations(NoteColumn column, BoundingArea accumulated)
    private void stackTrill(NoteColumn column, BoundingArea accumulated)
    private void stackFermata(NoteColumn column, BoundingArea accumulated)
    private void stackDynamics(NoteColumn column, BoundingArea accumulated)
    private void stackTempo(NoteColumn column, BoundingArea accumulated)
    private void stackAnnotations(NoteColumn column, BoundingArea accumulated)

    // Note: Endings and attribution deferred to later phases

    // Helper methods
    private BoundingArea getNoteBoundingArea(NoteColumn column)
    private double findClearYPosition(LineElement element, BoundingArea accumulated, double margin)
    private void positionElement(LineElement element, double x, double y)
}
```

### Data Structures

#### VerticalStackingResult
```java
public class VerticalStackingResult {
    private final Map<Note, Map<LineElement, Point2D>> elementPositions;
    private final double maxHeightAboveStaff;
    private final double lyricsBaselineY;
    private final double lineHeight;

    // Constructor and getters
}
```

#### BoundingArea
Use existing `java.awt.geom.Area` for non-rectangular collision detection:
```java
private class BoundingArea {
    private Area area;  // java.awt.geom.Area

    void addRectangle(Rectangle2D rect)
    boolean intersects(Rectangle2D rect)
    double getTopY()
    double getBottomY()
}
```

### Algorithm Implementation

#### Step 1: Initialize for Each Column
```java
for (NoteColumn column : columns) {
    Note note = column.getNote();

    // 1. Get note bounds from column (stem, head, flags)
    BoundingArea accumulated = getNoteBoundingArea(column);
    double maxYAboveStaff = note bounding top;

    // 2. Process each layer in order...
}
```

#### Step 2: Layer-by-Layer Stacking

For each layer, retrieve elements from both legacy and new systems:

**Articulations:**
```java
List<Articulation> articulations = note.getArticulations();
for (Articulation art : articulations) {
    double y = findClearYPosition(art, accumulated, ARTICULATION_MARGIN);
    positionElement(art, column.getX(), y);
    accumulated.addRectangle(art.getContentBounds());
    maxYAboveStaff = Math.min(maxYAboveStaff, y);
}
```

**Trill (legacy flag):**
```java
if (note.isTrill()) {
    // Create virtual trill element or position legacy trill
    double y = findClearYPosition(trillElement, accumulated, TRILL_MARGIN);
    // Position trill
    accumulated.add(...);
    maxYAboveStaff = Math.min(maxYAboveStaff, y);
}
```

**Fermata (legacy flag + new attachments):**
```java
// Check legacy flag
if (note.isFermata()) {
    // Handle legacy fermata
}

// Check new attachment hierarchy
FermataAttachment fermata = note.findAttachment(FermataAttachment.class);
if (fermata != null) {
    double y = findClearYPosition(fermata, accumulated, FERMATA_MARGIN);
    positionElement(fermata, column.getX(), y);
    accumulated.addRectangle(fermata.getContentBounds());
    maxYAboveStaff = Math.min(maxYAboveStaff, y);
}
```

**Similar pattern for:** Dynamics, Endings, Tempo, Annotations, Attribution

#### Step 3: Calculate Y Positions

```java
private double findClearYPosition(
    LineElement element,
    BoundingArea accumulated,
    double margin
) {
    // Start from top of accumulated bounding area
    double candidateY = accumulated.getTopY() - margin;

    // Move up by element height
    candidateY -= element.getContentHeight();

    // Check for intersection with accumulated area
    Rectangle2D elementBounds = new Rectangle2D.Double(
        element.getX(),
        candidateY,
        element.getContentWidth(),
        element.getContentHeight()
    );

    // If intersects, move further up
    while (accumulated.intersects(elementBounds)) {
        candidateY -= 1.0;  // Move up 1px at a time
        elementBounds.setRect(
            element.getX(),
            candidateY,
            element.getContentWidth(),
            element.getContentHeight()
        );
    }

    return candidateY;
}
```

#### Step 4: Lyrics Positioning

```java
// After all columns processed, find lowest note bounding area
double lowestNoteY = findLowestNoteBoundingY(columns);

// Lyrics baseline is LYRICS_BASELINE_OFFSET below that
double lyricsBaselineY = lowestNoteY + LayoutConstants.px(LYRICS_BASELINE_OFFSET);
```

#### Step 5: Line Height Calculation

```java
double lineHeight =
    LayoutConstants.STAFF_HEIGHT +  // 32px staff
    maxHeightAboveStaff +            // Distance from staff to highest element
    (hasLyrics ? lyricsHeight : 0) + // Lyric line height if present
    interLineMargin;                  // Space between lines
```

---

## Edge Cases to Handle

1. **Empty columns** - No elements to stack, just note bounds
2. **Columns without attachments** - Only articulations, or nothing
3. **Multiple articulations on same note** - Stack vertically with small gap
4. **Legacy vs new hierarchy** - Support both during transition
5. **User offsets** - Apply userYOffset from LineElement after calculated position
6. **Notes without syllables** - Don't affect lyrics baseline calculation
7. **Rests** - No articulations or attachments (usually)

---

## Integration with Existing Code

### Input from Phase 2

```java
List<NoteColumn> columns = ... // From NoteColumnBuilder
HorizontalSpacingCalculator hCalc = new HorizontalSpacingCalculator();
hCalc.calculatePositions(columns, line);
// Now columns have X positions set
```

### Output for Phase 4+

```java
VerticalStackingCalculator vCalc = new VerticalStackingCalculator();
VerticalStackingResult result = vCalc.calculateVerticalPositions(columns, line, g2);

// Result provides:
// - Y positions for all elements
// - Line height for layout engine
// - Lyrics baseline for lyric rendering
```

---

## Testing Strategy

### Unit Tests to Create

**`src/test/java/songscribe/ui/layout2/VerticalStackingCalculatorTest.java`**

Test cases:
1. **Empty column** - No articulations or attachments
2. **Single articulation** - Position above note
3. **Multiple articulations** - Stack with ARTICULATION_MARGIN
4. **Fermata only** - Position with FERMATA_MARGIN
5. **Tempo only** - Position with TEMPO_MARGIN
6. **Complex stacking** - Note + articulation + fermata + tempo
7. **Legacy trill flag** - Position correctly
8. **Legacy fermata flag** - Position correctly
9. **Legacy tempo** - Position correctly
10. **Mixed legacy + new** - Both systems working
11. **Line height calculation** - Correct total height
12. **Lyrics baseline** - Correct position below lowest note

### Test Helpers

```java
// Helper to create test column with positioned note
private NoteColumn createColumn(Note note, double x)

// Helper to add articulation to note
private void addArticulation(Note note, ArticulationType type)

// Helper to add attachment to note
private void addAttachment(Note note, Attachment attachment)

// Helper to verify element Y position
private void assertElementY(LineElement element, double expectedY, double tolerance)
```

---

## Implementation Steps

1. Create `VerticalStackingResult` data class
2. Create `VerticalStackingCalculator` class skeleton
3. Implement `getNoteBoundingArea(NoteColumn)`
4. Implement `findClearYPosition()` with Area-based collision detection
5. Implement `stackArticulations()` layer
6. Implement `stackTrill()` layer (legacy flag)
7. Implement `stackFermata()` layer (legacy + new)
8. Implement `stackDynamics()` layer (point dynamics only)
9. Implement `stackTempo()` layer (legacy + new)
10. Implement `stackAnnotations()` layer (legacy + new)
11. Implement lyrics baseline calculation
12. Implement line height calculation
13. Create comprehensive unit tests
14. Manual testing with real Line instances

**Note:** Endings, attribution, and beams deferred to later phases per scope decisions.

---

## Critical Files

**To create:**
- `src/main/java/songscribe/ui/layout2/VerticalStackingCalculator.java`
- `src/main/java/songscribe/ui/layout2/VerticalStackingResult.java`
- `src/test/java/songscribe/ui/layout2/VerticalStackingCalculatorTest.java`

**To reference (read-only):**
- `src/main/java/songscribe/ui/layout2/NoteColumn.java`
- `src/main/java/songscribe/ui/layout2/LayoutConstants.java`
- `src/main/java/songscribe/ui/layout/LineElement.java`
- `src/main/java/songscribe/ui/layout/Articulation.java`
- `src/main/java/songscribe/ui/layout/Attachment.java`
- `src/main/java/songscribe/ui/layout/*Attachment.java` (concrete types)
- `src/main/java/songscribe/music/Note.java`
- `src/main/java/songscribe/music/Line.java`

---

## Scope Decisions

**Phase 3 Scope (Point Attachments Only):**
- ✅ Articulations (staccato, accent)
- ✅ Trill (legacy flag)
- ✅ Fermata (legacy flag + new attachments)
- ✅ Dynamics (point dynamics only)
- ✅ Tempo/BeatChange (legacy properties + new attachments)
- ✅ Annotations (legacy property + new attachments)

**Deferred to Later Phases:**
- ❌ Range elements (endings, crescendo/diminuendo) - User will update Gould-Ross reference and master plan first
- ❌ Beams - Complex due to potential stem extension requirements
- ❌ Attribution - Clarification needed on per-note vs per-line

**Rationale:** Keep Phase 3 focused on vertical stacking of individual point elements. This provides a solid foundation before tackling range elements and beam rendering complications.

---

## Success Criteria

**Functionality:**
- `VerticalStackingCalculator` successfully sets Y positions on all elements
- Elements stack in correct order (articulations → trill → fermata → dynamics → endings → tempo → annotations → attribution)
- Proper margins maintained between layers (from LayoutConstants)
- No collisions between elements
- Line height calculated correctly
- Lyrics baseline positioned correctly below lowest note

**Code Quality:**
- Code follows project style guidelines
- Well-documented with JavaDoc
- All unit tests pass
- Test coverage includes edge cases (empty columns, mixed legacy/new, complex stacking)

**Integration:**
- Works correctly with NoteColumn instances from Phase 2
- Provides VerticalStackingResult for future phases
- Ready for Phase 4 (Line Justification) and Phase 5 (Rendering)
