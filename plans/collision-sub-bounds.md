# Collision Sub-Bounds for Vertical Stacking

## Context

The vertical stacking calculator treats each element as a single bounding rectangle for collision detection. Composite elements like ending brackets (vertical ticks + horizontal bar + label) and tempo markings (note glyph + text) waste vertical space because their full bounding box occupies the entire span. By decomposing these into sub-regions, elements in higher stacking layers can nestle into open space within lower-layer elements — e.g., a tempo marking fitting inside an ending bracket.

Additionally, `LayoutStylesheet` contains many unused constants and renderer-specific constants that belong in their renderers.

## Phase 1: CollisionRegion and COLLISION_PADDING_SS

### 1a. Create `CollisionRegion` record

**New file**: `src/main/java/songscribe/ui/layout/CollisionRegion.java`

```java
public record CollisionRegion(double xOffsetSs, double widthSs, double heightSs)
```

- `xOffsetSs`: horizontal offset from the element's anchor X
- `widthSs`: width of this sub-region
- `heightSs`: height of this sub-region (extends downward from the element's top Y)

### 1b. Add `COLLISION_PADDING_SS` to `CollisionDetector`

**File**: `src/main/java/songscribe/ui/layout/CollisionDetector.java`

```java
public static final double COLLISION_PADDING_SS = 0.25;  // 2px
```

This replaces all the individual unused `*_PADDING_SS` constants. It expands every collision region (both sub-regions and full-bounds) by 0.25 ss on all sides during both query and set phases.

## Phase 2: Sub-Region Providers

### 2a. Ending sub-regions

**File**: `src/main/java/songscribe/ui/layout/Ending.java`

Add `computeCollisionRegions(double spanWidthSs)` returning `List<CollisionRegion>`:

| Sub-region | xOffsetSs | widthSs | heightSs |
|---|---|---|---|
| Horizontal bar | 0 | spanWidthSs | voltaBracketSs |
| Left tick | 0 | voltaBracketSs | VOLTA_TICK_HEIGHT_SS |
| Right tick | spanWidthSs - voltaBracketSs | voltaBracketSs | VOLTA_TICK_HEIGHT_SS |
| Label ("1.") | LABEL_X_INSET_SS | labelWidthSs | labelHeightSs |

- Volta bracket thickness: `LineThickness.getInstance().voltaBracketSs()`
- Label constants (`LABEL_X_INSET_SS`, `LABEL_Y_OFFSET_SS`, `ENDING_FONT`) must be moved from `EndingRenderer` into `Ending`, since they describe the element's geometry
- Label dimensions: computed from `ENDING_FONT` glyph metrics (extract a static helper method that both `Ending.computeCollisionRegions` and `EndingRenderer.drawEnding` can use)
- Right tick may be absent (no closing stroke) — the caller or the method itself should handle this (parameter or the `Ending.Type` can determine it)

### 2b. TempoAttachment sub-regions

**File**: `src/main/java/songscribe/ui/layout/TempoAttachment.java`

Add `computeCollisionRegions(FontMetrics attrFontMetrics)` returning `List<CollisionRegion>`:

| Sub-region | xOffsetSs | widthSs | heightSs |
|---|---|---|---|
| Note glyph | 0 | noteWidthSs | DEFAULT_HEIGHT_SS |
| Text ("= 96") | noteWidthSs + GLYPH_TEXT_GAP_SS | textWidthSs | textHeightSs |

- Refactor `computeContentWidthSs` to extract individual component widths (note portion, text portion) into a private helper to avoid duplicating measurement logic
- Text height: `ScaleContext.fromPixels(attrFontMetrics.getAscent())` — may differ from note glyph height

## Phase 3: Stacking Algorithm Changes

**File**: `src/main/java/songscribe/ui/layout/VerticalStackingCalculator.java`

### 3a. Modify `stackAbove` to apply COLLISION_PADDING_SS

The existing `stackAbove` already handles the `horizontalMarginSs` expansion for queries vs. reservations. Add COLLISION_PADDING_SS expansion to both the query dimensions and the set dimensions for all elements:

- Query: expand x by `-COLLISION_PADDING_SS`, width by `+2*COLLISION_PADDING_SS`, height by `+2*COLLISION_PADDING_SS` (in addition to `horizontalMarginSs`)
- Set: expand x by `-COLLISION_PADDING_SS`, width by `+2*COLLISION_PADDING_SS`, ySs by `-COLLISION_PADDING_SS`

### 3b. Add `stackAboveWithRegions`

New private method that accepts `List<CollisionRegion>` and replaces the single-rectangle logic:

**Query phase** — for each region:
1. Compute absolute X: `regionXSs = xSs + region.xOffsetSs()`
2. Expand by COLLISION_PADDING_SS: `paddedXSs = regionXSs - COLLISION_PADDING_SS`, `paddedWidthSs = region.widthSs() + 2 * COLLISION_PADDING_SS`
3. Apply horizontal margin (for margin collapse): `queryXSs = paddedXSs - horizontalMarginSs`, `queryWidthSs = paddedWidthSs + 2 * horizontalMarginSs`
4. `regionTopSs = extents.yGet(true, queryXSs, queryWidthSs)`
5. `regionCeilingSs = Math.min(regionTopSs, anchorCeilingSs(...))`
6. `regionYSs = regionCeilingSs - marginSs - region.heightSs() - COLLISION_PADDING_SS`
7. Track minimum (most negative/highest) `regionYSs`

**Final position**: `elementYSs = min(regionYSs) across all regions`

**Set phase** — for each region:
1. `extents.ySet(true, regionXSs - COLLISION_PADDING_SS, region.widthSs() + 2 * COLLISION_PADDING_SS, elementYSs - COLLISION_PADDING_SS)`
2. Only the sub-region columns are reserved — gaps between regions remain open

**Record**: `builder.putDecorationLayout(element, new DecorationLayout(xSs, elementYSs, widthSs, heightSs))`

### 3c. Update `stackEndings`

In `stackSpanElement` (or a new overload for endings), after computing `spanWidthSs`:
- Call `ending.computeCollisionRegions(spanWidthSs)` 
- Delegate to `stackAboveWithRegions` instead of `stackAbove`

### 3d. Update `stackTempo`

After computing width/height:
- Call `tempo.computeCollisionRegions(attrFontMetrics)`
- Delegate to `stackAboveWithRegions` instead of `stackAbove`

## Phase 4: LayoutStylesheet Cleanup

**File**: `src/main/java/songscribe/ui/layout/LayoutStylesheet.java`

### 4a. Remove all unused constants

All `*_PADDING_SS` constants (16 total), plus:
- ANNOTATION_REGION_MARGIN_SS, VOLTA_MARGIN_SS, LYRICS_BASELINE_MARGIN_SS
- TIE_MARGIN_SS, TIE_NOTE_HEAD_OFFSET_SS, TIE_MIN_ARC_HEIGHT_SS, TIE_REFERENCE_DISTANCE_SS, TIE_HEIGHT_SCALE_SS, TIE_ARTICULATION_MARGIN_SS
- BEAM_INTER_MARGIN_SS, ARTICULATION_INTER_MARGIN_SS
- SEMIBREVE_MARGIN_RIGHT_SS, MINIM_MARGIN_RIGHT_SS, CROTCHET_MARGIN_RIGHT_SS, QUAVER_MARGIN_RIGHT_SS, SEMIQUAVER_MARGIN_RIGHT_SS, BARLINE_MARGIN_RIGHT_SS, BREATH_MARK_MARGIN_RIGHT_SS
- SYLLABLE_PADDING_H_SS, SYLLABLE_MARGIN_LEFT_SS, SYLLABLE_MARGIN_RIGHT_SS
- ACCIDENTAL_PADDING_SS, ACCIDENTAL_INTER_MARGIN_SS
- BARLINE_GAP_BEFORE_SS, BARLINE_GAP_AFTER_SS, BREATH_MARK_GAP_SS
- GRACE_NOTE_MIN_WIDTH_SS, BEAM_GROUP_EXTERNAL_GAP_SS
- DYNAMICS_MARGIN_SS, CRESC_DIM_MARGIN_SS
- LYRICS_BLOCK_MARGIN_TOP_SS, BANGLA_MARGIN_TOP_SS, TRANSLATION_MARGIN_TOP_SS
- FIRST_NOTE_X_SS, MIN_COMPRESSION_RATIO
- TUPLET_BEAM_MARGIN_SS, TUPLET_BRACKET_MARGIN_SS, TUPLET_MIN_STAFF_MARGIN_SS, TUPLET_NUMBER_VERTICAL_PADDING_SS

Also remove empty section headers/comments that become orphaned.

### 4b. Move renderer-specific constants

| Constant | From | To |
|---|---|---|
| TUPLET_NUMBER_GAP_SS | LayoutStylesheet | TupletRenderer |
| TUPLET_BRACKET_OVERHANG_SS | LayoutStylesheet | TupletRenderer |
| TUPLET_ARM_EXTENSION_SS | LayoutStylesheet | TupletRenderer |
| TUPLET_GAP_ITALIC_CORRECTION_SS | LayoutStylesheet | TupletRenderer |
| BEAM_STUB_SS | LayoutStylesheet | BeamGroupRenderer |

Update references in each renderer to use the local constant.

## Phase 5: Verification

1. `./scripts/compile.sh` — verify clean compilation after all changes
2. `./scripts/test.sh unit` — run unit tests
3. Visual verification: `./scripts/crun.sh` — open a score with ending brackets and tempo markings on the same line; confirm tempo nestles inside bracket when space permits

## Files to Modify

| File | Changes |
|---|---|
| `ui/layout/CollisionRegion.java` | **New** — record |
| `ui/layout/CollisionDetector.java` | Add COLLISION_PADDING_SS |
| `ui/layout/Ending.java` | Add computeCollisionRegions, move label constants from EndingRenderer |
| `ui/layout/TempoAttachment.java` | Add computeCollisionRegions, refactor width computation |
| `ui/layout/VerticalStackingCalculator.java` | Add stackAboveWithRegions, update stackEndings/stackTempo, apply COLLISION_PADDING_SS to stackAbove |
| `ui/layout/LayoutStylesheet.java` | Remove ~50 unused constants, remove 5 renderer-specific constants |
| `ui/renderer/EndingRenderer.java` | Move label constants to Ending, reference from there |
| `ui/renderer/TupletRenderer.java` | Receive 4 constants from LayoutStylesheet |
| `ui/renderer/BeamGroupRenderer.java` | Receive BEAM_STUB_SS from LayoutStylesheet |
