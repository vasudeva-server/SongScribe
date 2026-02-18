**Type:** Sub-plan  <br>
**Parent:** plans/migrations/smufl-rewrite/smufl-rewrite.md → Phase 3  <br>
**Captured:** 2026-02-15  <br>
**Pre-planned:** No  <br>
**Status:** In Progress

---

# Phase 3: Engraving Defaults

## Context

Phase 3 replaces all hardcoded stroke/thickness constants in renderers with values from Bravura's `engravingDefaults` metadata. This ensures visual proportions follow the SMuFL standard rather than ad-hoc pixel values tuned for Fughetta.

## Access Pattern

`SMuFLMetadata.getInstance().getEngravingDefaults()` returns the `EngravingDefaults` record. Values are in staff spaces; convert to pixels with `StaffSpaces.toPixels()`. Strokes will be computed once and stored as `static final` fields.

## Impact Analysis

Most changes are sub-pixel. The notable differences:

| Constant | Current (px) | Bravura (px) | Delta |
|----------|-------------|-------------|-------|
| staffLineThickness | 1.0 | 1.04 | +0.04 |
| stemThickness | 1.0 | 0.96 | -0.04 |
| beamThickness | 4.04 | 4.0 | -0.04 |
| beamSpacing | 6.0 | 2.0 | -4.0 * |
| thinBarlineThickness | 1.5 | 1.28 | -0.22 |
| thickBarlineThickness | 4.0 | 4.0 | 0 |
| barlineSeparation | 4.167 | 3.2 | -0.97 |
| legerLineThickness | 1.0 | 1.28 | +0.28 |
| hairpinThickness | 1.0 | 1.28 | +0.28 |
| tieEndpointThickness | 1.0 | 0.8 | -0.2 |
| tieMidpointThickness | 1.0 | 1.76 | +0.76 |
| tupletBracketThickness | 1.0 | 1.28 | +0.28 |
| repeatEndingLineThickness | 1.0 | 1.28 | +0.28 |

*`beamSpacing` is a notable outlier — SMuFL defines it as the gap between beams (0.25 ss = 2px), while the current `INNER_BEAM_OFFSET` (6px) likely includes beam thickness. Will need to verify the semantics and adjust accordingly.

## Changes

### 1. BaseElementRenderer.java

Replace the three stroke constants with metadata-driven values:

```java
private static final EngravingDefaults ENGRAVING_DEFAULTS =
    SMuFLMetadata.getInstance().getEngravingDefaults();

static final BasicStroke STAFF_LINE_STROKE = new BasicStroke(
    (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.staffLineThickness()));
static final BasicStroke STEM_STROKE = new BasicStroke(
    (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.stemThickness()));
static final BasicStroke LEDGER_LINE_STROKE = new BasicStroke(
    (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.legerLineThickness()));
```

### 2. BarRenderer.java

Replace `HEAVY_LINE_STROKE`, `THIN_LINE_STROKE`, `REPEAT_HEAVY_STROKE`, `REPEAT_THIN_STROKE`, `LINE_STROKE`, and `BAR_LINE_SPACE`:

- `thickBarlineThickness` for heavy/repeat-heavy strokes
- `thinBarlineThickness` for thin/repeat-thin/single strokes
- `barlineSeparation` for the gap between double barline lines

### 3. BeamGroupRenderer.java

- `BEAM_STROKE` width from `beamThickness`
- `STEM_STROKE` width from `stemThickness`
- `INNER_BEAM_OFFSET`: Investigate whether the current 6px value means "center-to-center distance" (beamThickness + beamSpacing) or just gap. Adjust to `beamThickness + beamSpacing` if center-to-center.

### 4. DynamicsRenderer.java

- `LINE_STROKE` width from `hairpinThickness`

### 5. TieRenderer.java

- `LINE_STROKE` is 1px uniform. SMuFL provides separate endpoint (0.1 ss = 0.8px) and midpoint (0.22 ss = 1.76px) thicknesses. For now, use a single stroke from the larger `tieMidpointThickness` to maintain current drawing approach (variable-width ties would require a different rendering technique).

### 6. TupletRenderer.java

- `LINE_STROKE` width from `tupletBracketThickness`

### 7. EndingRenderer.java

- `STEM_STROKE` width from `repeatEndingLineThickness`

### 8. ArticulationRenderer.java

- No direct SMuFL engraving default for accent stroke thickness. Keep as-is (DPI-aware 1.0f). Can revisit in Phase 7 when accents become SMuFL glyphs.

### 9. NoteRenderer.java

- `STEM_STROKE_IMPL` from `stemThickness` (or reference `BaseElementRenderer.STEM_STROKE`)
- `LINE_STROKE` from `legerLineThickness` (or reference `BaseElementRenderer.LEDGER_LINE_STROKE`)

## File Order

No dependencies between files. Can be done in any order. Suggested: BaseElementRenderer first (establishes the pattern), then remaining files alphabetically.

## Verification

1. `./scripts/compile.sh` succeeds
2. Run app, visually verify:
   - Staff lines, stems, ledger lines render cleanly
   - Single, double, and final barlines look correct
   - Repeat barlines (left, right, left-right) look correct
   - Beamed note groups (8th, 16th, 32nd) — beam thickness and spacing
   - Hairpin crescendo/diminuendo
   - Ties between notes
   - Tuplet brackets
   - First/second ending brackets
3. All existing tests pass
