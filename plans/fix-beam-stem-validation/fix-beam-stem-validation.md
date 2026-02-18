# Fix Beam Stem Length Validation

## Context

Beamed notes with large pitch spans produce stems that are too short. The `BeamCalculator` computes lengthening values without validating against the Gould/Ross minimum of 3.5 staff spaces. For example, in a 4-note group spanning yPos -3 to 1 with a flat beam, the last note gets `lengthening=-16`, yielding a stem of only 12px (1.5 ss). This causes beams to collide with noteheads.

Per Gould/Ross spec sections 4.1 and 4.2, after computing lengthenings we must validate all stems against MIN_STEM (3.5 ss). If any stem is too short: first try reducing slope (15% iteratively), then shift beam vertically if slope reaches 0.

## File to modify

`src/main/java/songscribe/music/BeamCalculator.java`

No changes to BeamGroupRenderer or NoteRenderer — the renderer code correctly uses `stem.y2` which reflects the lengthening. Once the calculator produces correct lengthenings, rendering will be correct.

## Status Dashboard

| Step | Description | Status |
|------|-------------|--------|
| 1 | [Add constants](#-1-add-constants-section-42) | ✅ Complete |
| 2 | [Extract lengthening computation](#-2-extract-lengthening-computation-into-a-helper) | ✅ Complete |
| 3 | [Add validation method](#-3-add-validation-method) | ✅ Complete |
| 4 | [Add validation pass](#-4-add-validation-pass-at-end-of-calculatelengthenings) | ✅ Complete |

## Implementation

### ✅ 1. Add constants (section 4.2)

```java
private static final double MIN_STEM_SS = 3.5;
private static final double MIN_STEM_PX = MIN_STEM_SS * LayoutStylesheet.STAFF_SPACE;
private static final double SLOPE_REDUCTION_FACTOR = 0.85;
private static final int MAX_SLOPE_ITERATIONS = 20;
```

Note: `STEM_LENGTH` in NoteRenderer is `StaffSpaces.toPixels(3.5)` = MIN_STEM_PX = 28px. Since both are 3.5 ss, any negative lengthening violates MIN_STEM.

### ✅ 2. Extract lengthening computation into a helper

Refactor the anchor + loop at lines 115-131 into a method so we can call it repeatedly during slope reduction:

```java
private static void computeLengthenings(
    Line line, int startIndex, int endIndex,
    double angle, int direction
)
```

This sets `lengthening` on every note in the range (anchor gets 0, others computed from beam line).

### ✅ 3. Add validation method

```java
private static int findMinLengthening(
    Line line, int startIndex, int endIndex
)
```

Returns the minimum `lengthening` across all non-grace notes.

### ✅ 4. Add validation pass at end of `calculateLengthenings()`

After the initial `computeLengthenings()` call:

1. **Slope reduction loop (section 4.2 Inner Stem Validation):**
   - Find `minLengthening` across all non-grace notes
   - While `minLengthening < 0` and `|angle| > epsilon` and iterations < MAX:
     - `angle *= SLOPE_REDUCTION_FACTOR`
     - If `|angle| < epsilon`: `angle = 0`
     - Recompute all lengthenings with new angle
     - Recheck `minLengthening`

2. **Vertical beam shift (section 4.2 "shift entire beam vertically"):**
   - If `minLengthening` still < 0 after slope reaches 0:
     - `deficit = -minLengthening` (always positive)
     - Add `deficit` to every non-grace note's `lengthening`

3. Add LOG.fine diagnostics for slope reductions and beam shifts.

## Verification

1. `./scripts/compile.sh` — must compile cleanly
2. Run the app, open the test file with the beamed 32nd notes
3. Check BeamRenderer and BeamCalculator log output to confirm shift is applied
4. Visually verify stems are no longer too short and beams have proper clearance
5. Check other beam groups (8th notes, 16th notes) are not negatively affected
