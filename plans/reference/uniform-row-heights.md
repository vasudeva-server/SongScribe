# Uniform Row Height Layout System

## Overview

Implement a uniform row height system where midline-to-midline spacing is consistent across all staff rows. The spacing is determined by the maximum required gap between any adjacent pair of rows, calculated as content-to-content distance plus minimum margin.

## Key Concepts

- **Midline**: Center of the 5-line staff (B line)
- **Uniform spacing**: Distance from midline to midline, applied to all rows
- **Content-to-content**: Spacing is based on the bottom content of row N to top content of row N+1
- **First row special**: First row's position is determined by the component layout (for tempo, attribution attachments, etc.), but its bottom content contributes to the spacing calculation

## Algorithm

### Spacing Calculation

For each adjacent pair of rows (N, N+1), the required spacing is:

```
requiredSpacing[N] = row[N].contentBelowMidline + minGap + row[N+1].contentAboveMidline
```

Where:
- `contentBelowMidline` = distance from midline to lowest content (half staff + notes below + lyrics)
- `contentAboveMidline` = distance from midline to highest content (half staff + notes above + tempo/annotations)
- `minGap` = minimum margin between content (2 staff lines = 16px)

The uniform spacing is:

```
uniformSpacing = max(requiredSpacing[0], requiredSpacing[1], ..., requiredSpacing[N-1])
```

### Row Positioning

1. **First row**: Position determined by component layout (handles attribution, tempo margins automatically)
2. **Subsequent rows**: Each row's midline Y = previous row's midline Y + uniformSpacing

## Margins (from composition layout PDF)

| Element | Margin | Pixels |
|---------|--------|--------|
| Annotation (above staff) | 3 staff lines | 24 |
| Lyrics (below staff) | 1 staff line | 8 |
| Inter-row minimum (content to content) | 2 staff lines | 16 |

## Implementation Plan

### Phase 1: Create LinesContainer Component

**New file:** `src/main/java/songscribe/ui/layout/LinesContainer.java`

A container component that sits between Score and individual Line components, owning the uniform spacing logic.

```java
public class LinesContainer extends LineElement {
    private final List<Line> lines = new ArrayList<>();
    private double uniformSpacing = 0;

    // Line management
    public void addLine(Line line);
    public void removeLine(Line line);
    public Line getLine(int index);
    public int lineCount();
    public List<Line> getLines();  // unmodifiable view

    // Spacing
    public double getUniformSpacing();
    public double getMidlineY(int lineIndex);
}
```

**Key responsibilities:**
- Maintain references to Line components (coordinator pattern, like BeamGroup)
- Calculate uniform spacing based on line extents
- Provide midline Y position for each line

### Phase 2: Create LinesContainerLayoutStrategy

**New file:** `src/main/java/songscribe/ui/layout/strategy/LinesContainerLayoutStrategy.java`

Implements `ElementLayoutStrategy<LinesContainer>` with two-pass layout:

**Measure pass:**
1. For each line, query its extent requirements:
   - `contentAboveMidline` - distance from midline to highest element
   - `contentBelowMidline` - distance from midline to lowest element
2. Calculate required spacing for each adjacent pair
3. Return total size (width of widest line, height of all lines with uniform spacing)

**Arrange pass:**
1. First line: position based on its content above (handled by parent layout)
2. Subsequent lines: midlineY = previousMidlineY + uniformSpacing
3. Store uniform spacing in container for later queries

### Phase 3: Add Extent Methods to Line

**File:** `src/main/java/songscribe/music/Line.java`

Add methods to report content extent relative to the midline:

```java
/**
 * Returns distance from midline to highest content (above staff).
 * Includes: notes above staff, tempo, beat changes, endings, trills, annotations.
 */
public int getContentAboveMidline();

/**
 * Returns distance from midline to lowest content (below staff).
 * Includes: notes below staff, lyrics, annotations below.
 */
public int getContentBelowMidline();
```

These methods should:
- Query the LineLayoutEngine results for actual rendered bounds
- Include appropriate margins (annotation margin, lyrics margin)
- Cache results and invalidate when line content changes

### Phase 4: Add Margin Constants to LayoutStylesheet

**File:** `src/main/java/songscribe/ui/layout/LayoutStylesheet.java`

Add constants for uniform spacing calculation:

```java
// Minimum gap between adjacent row content (2 staff lines)
public static final double INTER_ROW_MIN_GAP = 2 * STAFF_LINE_Y_OFFSET;  // 16px

// Annotation margin above staff (3 staff lines)
public static final double ANNOTATION_MARGIN = 3 * STAFF_LINE_Y_OFFSET;  // 24px

// Lyrics margin below staff (1 staff line)
public static final double LYRICS_MARGIN = 1 * STAFF_LINE_Y_OFFSET;  // 8px
```

### Phase 5: Integrate with Score Rendering

**File:** `src/main/java/songscribe/ui/renderer/ScoreRenderer.java`

Update to:
1. Create LinesContainer during score setup
2. Populate with Line references from Composition
3. Use LinesContainerLayoutStrategy for layout
4. Query container for midline positions when rendering lines

### Phase 6: Update Hit Testing

**File:** Update mouse/click handling to use LinesContainer

When determining which line a click is on:
- Query LinesContainer for line positions
- Use component bounds rather than uniform row height division

## Files to Create

| File | Purpose |
|------|---------|
| `src/main/java/songscribe/ui/layout/LinesContainer.java` | Container component for lines |
| `src/main/java/songscribe/ui/layout/strategy/LinesContainerLayoutStrategy.java` | Layout strategy for container |

## Files to Modify

| File | Changes |
|------|---------|
| `src/main/java/songscribe/music/Line.java` | Add extent methods |
| `src/main/java/songscribe/ui/layout/LayoutStylesheet.java` | Add margin constants |
| `src/main/java/songscribe/ui/layout/strategy/LayoutStrategyRegistry.java` | Register new strategy |
| `src/main/java/songscribe/ui/renderer/ScoreRenderer.java` | Integrate LinesContainer |

## Verification

1. **Base spacing test:**
   - Create composition with 3 lines, no extreme notes
   - Verify uniform midline-to-midline spacing

2. **Content above first row test:**
   - Add high note on first row
   - Verify first row moves down (maintains tempo margin)
   - Verify rows 2-3 spacing unchanged

3. **Content below affects next row test:**
   - Add low note with ledger lines on row 1
   - Verify lyrics pushed down
   - Verify row 2 moves down to maintain gap
   - Verify row 3 moves down uniformly

4. **Content above non-first row test:**
   - Add high note on row 2
   - Verify row 2 moves down (maintains gap from row 1 content)
   - Verify row 3 moves down uniformly

5. **Shrink recalculation test:**
   - Remove content that was causing extra spacing
   - Verify uniform spacing recalculates to smaller value

6. **Visual inspection:**
   - Compile with `./scripts/compile.sh`
   - Run with `./scripts/run.sh`
   - Open a composition with multiple staff lines
   - Verify even spacing across all rows

## Component Hierarchy

```
Score (render context)
└── LinesContainer (owns uniform spacing logic)
    ├── Line 0 (first row - special positioning)
    │   └── LineElement children (notes, attachments, etc.)
    ├── Line 1
    │   └── LineElement children
    └── Line N
        └── LineElement children
```

## Notes

- LinesContainer follows the coordinator pattern (like BeamGroup) - it references Lines but doesn't remove them from Composition
- First row positioning is handled by the parent component layout, not calculated manually
- Attribution is now an attachment to the first line, not a separate section
- The uniform spacing is recalculated whenever any line's content changes
