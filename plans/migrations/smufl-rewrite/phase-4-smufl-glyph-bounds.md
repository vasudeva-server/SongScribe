**Type:** Sub-plan  <br>
**Parent:** plans/migrations/smufl-rewrite/smufl-rewrite.md → Phase 4  <br>
**Captured:** 2026-02-15  <br>
**Pre-planned:** Yes  <br>
**Status:** Completed

---

# Phase 4: SMuFL-Driven Glyph Bounds

## Context

After Phase 2 collapsed the Note subclass hierarchy, all per-type bounding rectangles live in `NoteType` as hardcoded `java.awt.Rectangle` values measured from the Fughetta font. These pixel rectangles drive hit-testing (`SelectionHandler.buildNoteHitRect`), layout extent calculations (`NoteColumnBuilder`), and vertical stacking (`VerticalStackingCalculator`). The `FughettaFontBoundsProvider` class provides additional font-metric-based bounds for the `FontBoundsProvider` interface, but is currently instantiated as dead code in `Score.java`.

Phase 4 replaces all these Fughetta-derived bounds with values computed from Bravura's SMuFL metadata: glyph bounding boxes (`BBox`), stem anchor points (`GlyphAnchors`), and advance widths.

## SMuFL Metadata Available

From `bravura_metadata.json`, loaded via `SMuFLMetadata.getInstance()`:

### Bounding Boxes (staff spaces, Y-down after `BBox.fromSMuFL` flip)

| Glyph | left | top | right | bottom | Width (ss) | Width (px at 8px/ss) |
|---|---|---|---|---|---|---|
| `noteheadBlack` | 0 | -0.5 | 1.18 | 0.5 | 1.18 | 9.44 |
| `noteheadHalf` | 0 | -0.5 | 1.18 | 0.5 | 1.18 | 9.44 |
| `noteheadWhole` | 0 | -0.5 | 1.688 | 0.5 | 1.688 | 13.5 |
| `restQuarter` | varies | | | | | |
| `rest8th` | varies | | | | | |
| `rest16th` | varies | | | | | |
| `rest32nd` | varies | | | | | |
| `restWhole` | varies | | | | | |
| `restHalf` | varies | | | | | |

### Stem Anchors (staff spaces, Y-down)

| Glyph | stemUpSE (x, y) | stemDownNW (x, y) |
|---|---|---|
| `noteheadBlack` | (1.18, -0.168) | (0, 0.168) |
| `noteheadHalf` | (1.18, -0.168) | (0, 0.168) |
| `noteheadWhole` | N/A (no stem) | N/A |

### Advance Widths (staff spaces)

| Glyph | Advance (ss) | Advance (px) |
|---|---|---|
| `noteheadBlack` | 1.18 | 9.44 |
| `noteheadHalf` | 1.18 | 9.44 |
| `noteheadWhole` | 1.688 | 13.5 |

## Current Hardcoded Values vs. SMuFL Equivalents

| Location | Current Value | SMuFL Equivalent | Source |
|---|---|---|---|
| `Note.NORMAL_IMAGE_WIDTH` | 18 px | 9.44 px (1.18 ss) | `noteheadBlack` advance width |
| `Note.HOT_SPOT` | (5, 27) | derived from BBox + stem anchors | glyph origin is at (0, 0) in SMuFL |
| `NoteColumnBuilder.NOTE_HEAD_WIDTH` | 18 px | 9.44 px (1.18 ss) | `noteheadBlack` bbox width |
| `NoteColumnBuilder.HALF_NOTE_HEAD` | 9 px | 4.72 px (0.59 ss) | half of bbox width |
| `NoteColumnBuilder.STEM_LENGTH` | 28 px | 28 px (3.5 ss) | SMuFL standard: 3.5 staff spaces |
| `VerticalStackingCalculator` noteHeadWidth | 8 px | 9.44 px (1.18 ss) | bbox width |
| `VerticalStackingCalculator` noteHeadHeight | 6 px | 8 px (1.0 ss) | bbox height |
| `NoteType.CROTCHET` up rect width | 11 px | 9.44 px | notehead bbox + stem at stemUpSE.x |
| `NoteType.SEMIBREVE` rect width | 15 px | 13.5 px | `noteheadWhole` bbox width |

---

## Execution Steps

### Step 1: Add NoteType-to-SMuFLGlyph Mapping

**File**: `src/main/java/songscribe/music/NoteType.java`

Add a method that returns the `SMuFLGlyph` for a given `NoteType`'s notehead. This avoids duplicating the mapping in multiple places.

```java
private static final Map<NoteType, SMuFLGlyph> SMUFL_NOTEHEADS = Map.ofEntries(
    Map.entry(SEMIBREVE, SMuFLGlyph.NOTEHEAD_WHOLE),
    Map.entry(MINIM, SMuFLGlyph.NOTEHEAD_HALF),
    Map.entry(CROTCHET, SMuFLGlyph.NOTEHEAD_BLACK),
    Map.entry(QUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
    Map.entry(SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
    Map.entry(DEMI_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
    Map.entry(GRACE_QUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
    Map.entry(GRACE_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK),
    Map.entry(SEMIBREVE_REST, SMuFLGlyph.REST_WHOLE),
    Map.entry(MINIM_REST, SMuFLGlyph.REST_HALF),
    Map.entry(CROTCHET_REST, SMuFLGlyph.REST_QUARTER),
    Map.entry(QUAVER_REST, SMuFLGlyph.REST_8TH),
    Map.entry(SEMIQUAVER_REST, SMuFLGlyph.REST_16TH),
    Map.entry(DEMI_SEMIQUAVER_REST, SMuFLGlyph.REST_32ND)
);

@Nullable
public SMuFLGlyph getSMuFLNoteheadGlyph() {
    return SMUFL_NOTEHEADS.get(this);
}
```

**Verification**: Compile succeeds. No behavior changes yet.

### Step 2: Create `BravuraFontBoundsProvider`

**File**: `src/main/java/songscribe/ui/layout/BravuraFontBoundsProvider.java` (new)

Implements `FontBoundsProvider` using SMuFL metadata instead of Fughetta font metrics. Uses `NoteType.getSMuFLNoteheadGlyph()` from Step 1.

Key methods:

- **`getNoteHeadStemBounds()`**: Look up glyph via `NoteType`, get `BBox` from metadata, convert to pixels. For stemmed notes, extend bounds using `GlyphAnchors.stemUpSE`/`stemDownNW` plus stem length.
- **`getCrotchetWidth()`**: Precomputed from `noteheadBlack` bbox width in pixels.
- **`getHalfNoteWidthForTie()`**: For semibreve/minim, use `noteheadWhole`/`noteheadHalf` bbox width / 2. For others, use `crotchetWidth / 2`.
- **`getTempoNoteBounds()`** (static): Replicate logic from `FughettaFontBoundsProvider` using SMuFL bboxes and anchors.

**Verification**: Compiles and all `FontBoundsProvider` methods return reasonable values.

### Step 3: Replace NoteType Rectangle Computation with SMuFL Metadata

**File**: `src/main/java/songscribe/music/NoteType.java`

Change `realUpNoteRect` and `realDownNoteRect` fields to be computed from SMuFL metadata. The rectangles must continue to use the same `HOT_SPOT`-relative coordinate convention as the current code.

**Approach**: Compute rectangles in a static initializer using `SMuFLMetadata`:

For **notes with stems** (crotchet, quaver, semiquaver, demisemiquaver, minim):
- Notehead BBox gives the head's pixel extent
- `stemUpSE` anchor defines stem start for stem-up; `stemDownNW` for stem-down
- Stem length is 3.5 staff spaces (28px)

For the **up rect** (stem extends upward from SE corner):
- Left = 0, Top = 0 (top of stem)
- Width = max(notehead width, stemUpSE.x)
- Height = stem length + notehead height below origin

For the **down rect** (stem extends downward from NW corner):
- Left = 0, Top = notehead top
- Width = notehead width
- Height = stem length + notehead height above origin

For **semibreve** (no stem): Both rects = notehead BBox in pixels.

For **rests**: Both rects = rest glyph BBox in pixels.

For **grace notes**: Use pre-composed `graceNoteAcciaccaturaStemUp` and `graceNoteAcciaccaturaStemDown` BBoxes directly (U+E560, U+E561). These glyphs already include the notehead, stem, flag, and slash at correct grace-note size. Note: `GRACE_SEMIQUAVER` will be removed in Step 10, but for now both grace note types use the same acciaccatura glyph BBox.

For **barlines, breath marks, etc.**: Keep current hardcoded values (Phase 7 handles these).

**Important constraints**:
- `java.awt.Rectangle` uses integers; use `Math.round()` consistently
- Keep `HOT_SPOT` coordinate convention intact; actual `HOT_SPOT` removal is Phase 8
- Translate SMuFL-derived bounds into `HOT_SPOT`-relative pixel coordinates so existing consumers (SelectionHandler, EndingRenderer, `Score.drawWidthIfWiderLine`, etc.) continue working

**Verification**: Compile succeeds. Visual spot-check that notes are positioned and selectable correctly. Minor pixel shifts (1-2px) are acceptable.

### Step 4: Replace NoteColumnBuilder Constants

**File**: `src/main/java/songscribe/ui/layout2/NoteColumnBuilder.java`

Replace hardcoded constants with metadata-driven values:

```java
private static final SMuFLMetadata METADATA = SMuFLMetadata.getInstance();

private static final double NOTE_HEAD_WIDTH =
    StaffSpaces.toPixels(METADATA.getBBox(SMuFLGlyph.NOTEHEAD_BLACK).width());

private static final double HALF_NOTE_HEAD = NOTE_HEAD_WIDTH / 2.0;

private static final double STEM_LENGTH = StaffSpaces.toPixels(3.5);
```

Also replace `computeAccidentalWidths()`: Use `SMuFLMetadata.getAdvanceWidth()` for each accidental glyph instead of measuring Fughetta glyphs with `Font.getStringBounds()`. Compound accidentals (double natural, natural+flat, natural+sharp) sum the individual widths.

Keep `DOT_WIDTH` and `DOT_GAP` as-is for now (refined in Phase 5).

**Verification**: Compile succeeds. Visual check that notes are spaced correctly, accidentals don't overlap noteheads.

### Step 5: Replace VerticalStackingCalculator Hardcoded Values

**File**: `src/main/java/songscribe/ui/layout2/VerticalStackingCalculator.java`

Replace local variables in `getNoteBoundingArea()`:

```java
private static final double NOTE_HEAD_WIDTH =
    StaffSpaces.toPixels(
        SMuFLMetadata.getInstance().getBBox(SMuFLGlyph.NOTEHEAD_BLACK).width());

private static final double NOTE_HEAD_HEIGHT =
    StaffSpaces.toPixels(
        SMuFLMetadata.getInstance().getBBox(SMuFLGlyph.NOTEHEAD_BLACK).height());
```

**Verification**: Compile succeeds. Vertical stacking of articulations, dynamics, and annotations clears note head and stem.

### Step 6: Verify SelectionHandler.buildNoteHitRect (No Code Change Expected)

**File**: `src/main/java/songscribe/ui/component/score/SelectionHandler.java`

`buildNoteHitRect()` uses `NoteType.getRealUpNoteRect()` and `getRealDownNoteRect()`. After Step 3, these rectangles already contain metadata-derived values, so no code changes should be needed.

If metadata-derived rectangles are too small for comfortable hit-testing (Bravura notehead width 9.44px vs. old 11px), add small padding (+2px each side) as a follow-up.

**Verification**: Click-test all note types. Drag-selection works.

### Step 7: Wire Up BravuraFontBoundsProvider in Score.java

**File**: `src/main/java/songscribe/ui/component/Score.java`

Replace:
```java
FontBoundsProvider fontBoundsProvider = new FughettaFontBoundsProvider(this);
```
With:
```java
FontBoundsProvider fontBoundsProvider = new BravuraFontBoundsProvider(this);
```

Note: This variable is currently dead code (created but unused). This change ensures correct wiring for when other code starts using it.

**Verification**: App starts without exceptions.

### Step 8: Verify Passive Consumers (No Code Changes Expected)

The following callsites use `note.getRealUpNoteRect().width` and will automatically receive new values after Step 3. Verify correct results:

- **`TieRenderer.getHalfNoteWidthForTie()`** - Bravura whole note width 13.5px (was 15px), slight inward shift. Acceptable.
- **`ArticulationRenderer.getHalfNoteWidthForTie()`** - Same pattern, same acceptable shift.
- **`EndingRenderer.renderEndings()`** - Width changes are minor.
- **`Score.drawWidthIfWiderLine()`** - Minor adjustment.
- **`Note.getContentWidth()`** and **`Note.getContentHeight()`** - Feed into layout calculations.

**Verification**: Visual check of ties, articulation positioning, endings, and line width.

### Step 9: Delete FughettaFontBoundsProvider

**File**: Delete `src/main/java/songscribe/ui/layout/FughettaFontBoundsProvider.java`

Also remove the import from `Score.java`.

**Verification**: Compile succeeds. `grep -r "FughettaFontBoundsProvider" src/` returns nothing.

### Step 10: Remove GRACE_SEMIQUAVER

**Rationale**: There is no standard notation case for 16th-note grace notes. Grace notes are always rendered as 8th notes (single flag). The `GRACE_SEMIQUAVER` type represents a special two-note beamed construct used only in editing UI, not a standard grace note variant.

**Files to modify**:

1. **`NoteType.java`**:
   - Remove `GRACE_SEMIQUAVER` enum constant
   - Remove `GRACESEMIQUAVER` alias
   - Remove `GRACE_SEMIQUAVER_EDIT_STEP1` enum constant
   - Update `isGraceNote()` to only check for `GRACE_QUAVER`
   - Update `drawStaveLongitude()` to remove `GRACE_SEMIQUAVER` check
   - Update `createDefaultInstance()` to remove `GRACE_SEMIQUAVER` special case
   - Remove `GRACE_SEMIQUAVER` from `computeGraceNoteRects()` call

2. **`GraceSemiQuaver.java`**: Delete entire file

3. **`NoteIO.java`**:
   - Add conversion logic in read path: map any loaded `GRACE_SEMIQUAVER` to `GRACE_QUAVER`
   - Update `writeNote()` to remove `GRACE_SEMIQUAVER` handling

4. **`GraceNoteRenderer.java`**:
   - Remove `renderGraceSemiQuaver()` method
   - Update `render()` to only handle `GRACE_QUAVER`
   - Remove `drawGraceSemiQuaverBeam()` method

5. **`Line.java`**: Update `addNoteMessages()` to remove `GRACE_SEMIQUAVER` case

6. **`GlissandoRenderer.java`**: Update `getGlissandoX1Pos()` to remove `GRACE_SEMIQUAVER` case

7. **`HorizontalAdjustment.java`**: Update `setEnabled()` to remove `GRACE_SEMIQUAVER` check

8. **`ExportABCAction.java`**: Update `translateNote()` to remove `GRACE_SEMIQUAVER` handling

9. **Edit mode files**: Search for and remove any `GRACE_SEMIQUAVER_EDIT_STEP1` references in:
   - `EditModeManager.java`
   - `Score.java`
   - `Actions.java`

**Verification**:
1. `./scripts/compile.sh` succeeds
2. `grep -r "GRACE_SEMIQUAVER\|GraceSemiQuaver" src/` returns nothing
3. Load a file containing grace notes, verify they render correctly
4. Insert new grace notes in the editor, verify they work

---

## Step Dependencies

```
Step 1 (NoteType mapping)
  |
  +---> Step 2 (BravuraFontBoundsProvider) ---> Step 7 (wire in Score.java)
  |                                                        |
  +---> Step 3 (NoteType rectangles) ----+                 |
  |                                      |                 v
  +---> Step 4 (NoteColumnBuilder) -+    +---> Step 6 (verify SelectionHandler)
  |                                 |    |
  +---> Step 5 (VerticalStacking) --+----+---> Step 8 (verify passive consumers)
                                                           |
                                                           v
                                                   Step 9 (delete Fughetta provider)
                                                           |
                                                           v
                                                   Step 10 (remove GRACE_SEMIQUAVER)
```

Steps 2, 3, 4, and 5 are independent and can proceed in parallel once Step 1 is complete.
Step 10 can be done anytime after Step 3 (which handles grace note rects).

---

## Risks and Edge Cases

### 1. HOT_SPOT Coordinate System Mismatch

**Risk**: Current rectangles in `NoteType` are defined relative to a Fughetta-era coordinate system where `HOT_SPOT = (5, 27)`. SMuFL glyphs have their origin at the left edge of the notehead on the staff line.

**Mitigation**: In Step 3, translate SMuFL-derived bounds into the `HOT_SPOT` coordinate system. Keep `HOT_SPOT` unchanged until Phase 8. Document the translation in code comments.

### 2. Integer Rectangle Rounding

**Risk**: `java.awt.Rectangle` uses integer coordinates. SMuFL values produce fractional pixel values (e.g., 9.44px). Rounding may cause 1px differences.

**Mitigation**: Use `Math.round()` consistently. Current values are also approximations, so 1px differences are acceptable.

### 3. Grace Note Scaling

**Risk**: Grace notes use scaled-down noteheads. Current `GRACE_QUAVER`/`GRACE_SEMIQUAVER` rectangles are independently hardcoded.

**Mitigation**: Compute grace note bounds by scaling standard notehead BBox by `BaseElementRenderer.GRACE_ACCIDENTAL_RESIZE_FACTOR`. Minor inaccuracies are acceptable since grace notes are small.

### 4. Flag Width in Stem-Up Rects

**Risk**: For quaver/semiquaver/demisemiquaver stem-up, current rectangles include flag width (e.g., `QUAVER` up rect width 18px vs. crotchet's 11px). SMuFL flag glyphs have separate bounding boxes.

**Mitigation**: For stem-up beamable notes, add flag glyph BBox width to the right extent. Use approximate flag width from metadata; exact values refined in Phase 6.

### 5. Rest Glyph Bounds

**Risk**: Rest glyph bounding boxes in Bravura may differ significantly from Fughetta rests.

**Mitigation**: Use SMuFL rest glyph BBoxes directly. Rest hit-testing is less critical; size differences should be fine.

### 6. Barline/NonNote Rectangles

**Risk**: Barlines, breath marks, and repeat signs have rectangles in `NoteType` that are not glyph-derived.

**Mitigation**: Leave unchanged in Phase 4. Addressed in Phase 7.

---

## Verification Checklist

1. `./scripts/compile.sh` succeeds
2. `grep -r "FughettaFontBoundsProvider" src/` returns nothing
3. Visual verification:
   - Notes rendered at correct positions (no visible shifts)
   - Click-selecting individual notes works for all note types
   - Drag-selecting note ranges works
   - Beamed note groups display correctly
   - Ties connect at correct positions
   - Articulations position correctly above/below notes
   - First/second ending brackets position correctly
   - Vertical stacking clears note stems
   - Grace notes selectable and positioned correctly
   - Rest hit-testing works
4. All existing tests pass
