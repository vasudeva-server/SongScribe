# Fix Ending Stacking: Separate Bracket Collision Regions

## Context

The vertical stacking algorithm treats each `Ending` as a single collision region spanning
the entire repeat section. But the `EndingRenderer` draws TWO visual brackets from one
Ending — a first ending before the repeat barline and a second ending after it. The stacking
needs to match the renderer: compute separate collision regions for each visual bracket,
anchored at the barline/repeat positions (not note positions).

Without this, the ending collision regions are wrong: wrong x position, wrong width, and
only one set of regions when there should be two.

## Step 1: Add repeat split index to Ending model

**File:** `src/main/java/songscribe/ui/layout/Ending.java`

Add a field to store the element index of the REPEAT_RIGHT that separates first and second
endings:

```java
private int repeatSplitIndex = -1;  // -1 = no repeat found (single bracket)
```

Add getter/setter. Add a method `findRepeatSplitIndex(Line line)` that scans elements
between anchor and end for `ElementType.REPEAT_RIGHT` (same logic the renderer uses at
EndingRenderer.java line 118-121):

```java
public int findRepeatSplitIndex(Line line) {
    int start = getAnchorElementIndex();
    int end = getEndElementIndex();
    return IntStream.rangeClosed(start, end)
        .filter(i -> line.getElement(i).getType() == ElementType.REPEAT_RIGHT)
        .findFirst()
        .orElse(-1);
}
```

## Step 2: Add bracket range computation to Ending

**File:** `src/main/java/songscribe/ui/layout/Ending.java`

Add a record and method to compute the x-ranges for each visual bracket. This logic is
currently buried in `EndingRenderer.renderEndings()` (lines 108-228). Extract the
x-position computation to a shared method on Ending so both the stacking and renderer use
the same positions.

```java
public record BracketRange(
    double x1Ss, double x2Ss,
    int number,              // 1 or 2
    boolean hasClosingStroke
) {
    public double widthSs() { return x2Ss - x1Ss; }
}
```

Method: `computeBracketRanges(Line line, Map<StaffElement, ElementColumn> columnsByElement, LineThickness lt)`

This method replicates the renderer's x-position logic using column X positions
(available during stacking) instead of `layoutResult.getElementXSs()` (available during
rendering). Both return the same values.

The computation mirrors EndingRenderer lines 127-228:
- **First bracket x1**: start at anchor element X; adjust left to barline if previous
  element is SINGLE_BARLINE; align with barline center or go halfway to previous note
- **First bracket x2**: if repeat exists, align with REPEAT_RIGHT thin barline center
  via `BarRenderer.repeatRightThinBarlineCenterXSs(lt)`
- **Second bracket x1**: `repeatX` = repeat element X +
  `BarRenderer.repeatRightAfterThickXSs(lt)` - voltaBracketThickness/2
- **Second bracket x2**: end element X, adjusted for barline type; hasClosingStroke
  determined by end element type (same switch as renderer lines 194-225)

## Step 3: Update `computeCollisionRegions` signature

**File:** `src/main/java/songscribe/ui/layout/Ending.java`

Change the method to accept a `BracketRange` instead of `spanWidthSs` and
`hasClosingStroke`:

```java
public List<CollisionRegion> computeCollisionRegions(BracketRange bracket) {
    double spanWidthSs = bracket.widthSs();
    boolean hasClosingStroke = bracket.hasClosingStroke();
    // ... same region creation, but xOffsets are relative to bracket.x1Ss
    // (the caller will set the element anchor X to the first bracket's x1)
}
```

Actually, the collision regions need xOffsets relative to the ELEMENT's anchor X (the first
bracket's x1). For the second bracket, we need to add an x offset. Add an `xBaseSs`
parameter:

```java
public List<CollisionRegion> computeCollisionRegions(BracketRange bracket, double xBaseSs)
```

Where `xBaseSs` = `bracket.x1Ss - elementAnchorXSs`. For the first bracket this is 0.
For the second bracket this is `bracket.x1Ss - firstBracket.x1Ss`.

## Step 4: Rewrite `stackEndings` in VerticalStackingCalculator

**File:** `src/main/java/songscribe/ui/layout/VerticalStackingCalculator.java`

The rewritten `stackEndings` method:

1. For each Ending, call `ending.computeBracketRanges(line, columnsByElement, lt)` to get
   the list of BracketRanges (1 or 2 brackets)
2. Use the first bracket's x1 as the element anchor X
3. Compute collision regions for EACH bracket, combining them into one list:
   - First bracket: `ending.computeCollisionRegions(firstBracket, 0)`
   - Second bracket: `ending.computeCollisionRegions(secondBracket, secondBracket.x1Ss - firstBracket.x1Ss)`
4. Compute overall widthSs = last bracket's x2 - first bracket's x1
5. Call `stackAboveWithRegions` once with all combined regions

The stacking needs access to `LineThickness`. It can get this from
`LineThickness.getInstance()`.

## Step 5: Refactor EndingRenderer to use stored bracket ranges

**File:** `src/main/java/songscribe/ui/renderer/EndingRenderer.java`

The stacking computes `BracketRange`s once and stores them on the `Ending` object. The
renderer reads the stored ranges instead of recomputing x-positions inline. This eliminates
the duplicated x-position logic and guarantees stacking and rendering use identical positions.

Refactor `renderEndings` to iterate over `ending.getBracketRanges()` and draw each bracket
using the stored x1/x2/hasClosingStroke/number values. Remove the inline x-position
computation (lines 108-228) and the `repeatRightPos` / `repeatX` logic.

## Files Modified

| File | Change |
|---|---|
| `Ending.java` | Add repeatSplitIndex, BracketRange record, computeBracketRanges(), update computeCollisionRegions() |
| `VerticalStackingCalculator.java` | Rewrite stackEndings() to compute per-bracket regions |
| `EndingRenderer.java` | Refactor to use shared bracket range computation |
| `BarRenderer.java` | No change (helper methods already exist) |

## Verification

1. `./scripts/compile.sh`
2. `./scripts/test.sh unit`
3. `DEBUG=1 ./scripts/crun.sh` — verify:
   - Each visual bracket has 4 collision rects (bar, left tick, right tick, label)
   - Collision rects align with the rendered bracket positions
   - First and second endings each have their own set of rects
   - Elements (dynamics, articulations) nestle correctly within/below endings
