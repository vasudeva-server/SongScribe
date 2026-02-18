# Uniform Row Height Layout System

## Overview

Implement a uniform row height system where midline-to-midline spacing is consistent across all staff rows. The row with the maximum required height among rows 2+ determines the spacing for ALL rows. First row is excluded from this calculation.

## Key Concepts

- **Midline**: Center of the 5-line staff (B line)
- **Uniform spacing**: Distance from midline to midline, applied globally
- **First row excluded**: First row's height only affects its position, not uniform spacing

## Margins (from composition layout PDF)

| Element | Margin | Pixels |
|---------|--------|--------|
| Annotation (above staff) | 3 staff lines | 24 |
| Lyrics (below staff) | 1 staff line | 8 |
| Attribution to first row | 2 staff lines | 16 |
| Inter-row minimum | 2 staff lines | 16 |

## Implementation Plan

### Phase 1: Add Extent Tracking to Line.java

**File:** `src/main/java/songscribe/music/Line.java`

Add two new methods to track content extent above/below the staff separately:

1. **Add cached fields:**
   ```java
   private int cachedDistanceAboveTopLine = -1;
   private int cachedDistanceBelowBottomLine = -1;
   ```

2. **Add `getDistanceAboveTopLine()`:**
   - Returns distance (pixels) from top staff line to highest element
   - Checks: tempo changes, beat changes, endings, trills, notes above staff, annotations above
   - Returns 0 if nothing extends above

3. **Add `getDistanceBelowBottomLine()`:**
   - Returns distance (pixels) from bottom staff line to lowest element
   - Checks: notes below staff, annotations below, lyrics
   - Returns 0 if nothing extends below

4. **Update cache invalidation:**
   - Modify `invalidateHeightCache()` to clear both new caches
   - Modify `modifiedComposition()` to clear both new caches

5. **Keep `getRequiredHeight()` for compatibility:**
   - Can be reimplemented as: `getDistanceAboveTopLine() + staffHeight + getDistanceBelowBottomLine()`

### Phase 2: Add Margin Constants to LayoutManager.java

**File:** `src/main/java/songscribe/ui/layout/LayoutManager.java`

Add constants for within-score margins:

```java
// Margin between notes/articulations and notations above (3 staff lines)
private static final int ANNOTATION_MARGIN = 3 * Score.STAFF_LINE_Y_OFFSET;

// Margin between staff bottom and line lyrics (1 staff line)
private static final int LYRICS_MARGIN = 1 * Score.STAFF_LINE_Y_OFFSET;

// Minimum margin between adjacent rows (2 staff lines)
private static final int INTER_ROW_MARGIN = 2 * Score.STAFF_LINE_Y_OFFSET;
```

### Phase 3: Modify Renderer.calculateUniformRowHeight()

**File:** `src/main/java/songscribe/ui/renderer/Renderer.java`

Update to:
1. **Exclude first row** from uniform spacing calculation
2. **Calculate separate extents:**
   - `maxAbove` = max of `getDistanceAboveTopLine()` for rows 1+ (index 1+)
   - `maxBelow` = max of `getDistanceBelowBottomLine()` for rows 0+ (all rows, since row 0's below affects row 1)
3. **Apply formula:**
   ```
   uniformRowHeight = staffHeight + maxAbove + maxBelow + INTER_ROW_MARGIN
   ```
4. **Apply adjustment and minimum:**
   ```
   return Math.max(baseHeight + composition.getRowHeightAdjustment(), getDefaultRowHeight())
   ```

### Phase 4: Update First Row Positioning

**File:** `src/main/java/songscribe/ui/layout/LayoutManager.java`

The first row's position must maintain attribution margin:

1. **Add method `getFirstRowOffset()`:**
   - If attribution exists: ensure 2 staff lines (16px) between attribution bottom and first row's highest element
   - Calculate: `attributionBottom + ATTRIBUTION_MARGIN + firstRow.getDistanceAboveTopLine()`

2. **Update `getMiddleLineY()`:**
   - Account for first row's content extent when positioning

### Phase 5: Margin Enforcement in Extent Calculations

In `Line.getDistanceAboveTopLine()`:
- When annotations exist above staff, ensure annotation margin (24px) is included in the distance

In `Line.getDistanceBelowBottomLine()`:
- When lyrics exist, ensure lyrics margin (8px) is included in the distance

## Files to Modify

| File | Changes |
|------|---------|
| `src/main/java/songscribe/music/Line.java` | Add extent methods, update cache invalidation |
| `src/main/java/songscribe/ui/renderer/Renderer.java` | Modify `calculateUniformRowHeight()` |
| `src/main/java/songscribe/ui/layout/LayoutManager.java` | Add margin constants, first row positioning |

## Verification

1. **Attribution margin test:**
   - Add tempo marking far above staff on first row
   - Verify 16px margin maintained between attribution and highest element
   - Verify rows 2+ spacing unchanged

2. **Uniform spacing test:**
   - Add content above staff on row 3
   - Verify all rows get same midline-to-midline spacing (including rows 1-2)

3. **Shrink recalculation test:**
   - Remove content that was causing extra height
   - Verify uniform spacing recalculates to new (smaller) value

4. **Lyrics margin test:**
   - Add notes below staff
   - Verify lyrics pushed down maintaining 8px margin

5. **Visual inspection:**
   - Compile with `./scripts/compile.sh`
   - Run with `./scripts/run.sh`
   - Open a composition with multiple staff lines
   - Verify even spacing across all rows
