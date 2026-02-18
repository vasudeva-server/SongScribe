# Plan: Articulations Always Above the Staff

## Context

Currently, staccato and accent placements are stem-direction-dependent: they go on the opposite side from the stem (above for stem-down, below for stem-up). The user wants both articulation types to always appear above the staff, regardless of stem direction. When both are present, they should cluster together (staccato closest to staff, accent above it).

This simplifies the placement logic significantly — no more stem-direction branching, no inverted-stem special cases, no concern about dots landing on staff lines (since the dot is always outside the staff).

## Changes

### 1. Unified standalone method: `calculateArticulationY` (`ArticulationRenderer.java`)

Replace the separate `calculateStaccatoY` and `calculateAccentY` with a single method for standalone placement:

```java
public static int calculateArticulationY(@NotNull Note note, int middleLineY, int articulationHalfHeight)
```

The `articulationHalfHeight` parameter is the distance from the articulation's center to its edge (e.g. `STACCATO_HALF_HEIGHT` for a dot, `ACCENT_BOUNDS.height / 2` for an accent chevron).

Logic (always above):
- **Notes within or below the staff** (`yPos > -4`): anchor above the top staff line at `staffTopY - (articulationHalfHeight + margin)`
- **Notes at or above the top staff line** (`yPos <= -4`): anchor above the note head at `noteHeadY - (noteHeadRadius + margin + articulationHalfHeight)`

### 2. Clustering method: `calculateAccentAboveStaccatoY` (`ArticulationRenderer.java`)

When both staccato and accent are present, accent stacks above staccato:

```java
public static int calculateAccentAboveStaccatoY(int staccatoY, int accentHalfHeight)
```

Returns `staccatoY - (STACCATO_HALF_HEIGHT + 1 + accentHalfHeight)`.

### 3. Remove old methods

Delete `calculateStaccatoY` and `calculateAccentY`. Update all callers to use the new unified method.

### 4. `VerticalStackingCalculator.stackArticulations` (`VerticalStackingCalculator.java`)

- Update calls to use `calculateArticulationY` / `calculateAccentAboveStaccatoY`
- Always add articulations to the `accumulated` bounding area (remove the `if (!isUpper)` guard), since they are now always above the staff

### 5. `ArticulationType.getDrawingOrder` (`ArticulationType.java`)

Simplify — the `stemUp` parameter is no longer meaningful. Return enum order (STACCATO first, ACCENT second = closest to staff first) unconditionally. Update the Javadoc.

## Files Modified

1. `src/main/java/songscribe/ui/renderer/ArticulationRenderer.java` — new unified method, remove old methods, update `renderFallback`
2. `src/main/java/songscribe/ui/layout2/VerticalStackingCalculator.java` — update `stackArticulations`
3. `src/main/java/songscribe/music/ArticulationType.java` — simplify `getDrawingOrder`

## Verification

1. Compile: `./scripts/compile.sh`
2. Run: user runs `./scripts/run-debug.sh`
3. Visual checks:
   - Notes with stems up (below middle line) should now show staccato/accent above the staff
   - Notes with stems down should continue showing staccato/accent above (no change for these)
   - Notes on ledger lines above the staff should show staccato/accent above the note head
   - Staccato + accent should cluster together above the staff (staccato closer to staff, accent above)
   - Other stacking elements (tempo, annotations) should not overlap with articulations
