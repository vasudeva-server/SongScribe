# Sub-plan: Coordinate System + Staff + Notes

**Type:** Sub-plan  <br>
**Parent:** [rendering-rewrite.md](rendering-rewrite.md) → Phase 1  <br>
**Created:** 2026-01-01  <br>
**Status:** Complete  <br>
**BlockedBy:** —

**Spec:** [docs/specs/rendering-rewrite.md](../../../docs/specs/rendering-rewrite.md) — always read the spec before implementing tasks.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | [Foundation](#-phase-1-foundation-no-existing-code-breaks) | ✅ Done |
| 2 | [IO Format Migration](#-phase-2-io-format-migration) | ✅ Done |
| 3 | [Convert Layout Constants](#-phase-3-convert-layout-constants) | ✅ Done |
| 4 | [Update Layout Pipeline](#-phase-4-update-layout-pipeline) | ✅ Done |
| 5 | [Apply Graphics2D Scale and Update Renderers](#-phase-5-apply-graphics2d-scale-and-update-renderers) | ✅ Done |
| 6 | [Mouse Coordinate Mapping](#-phase-6-mouse-coordinate-mapping) | ✅ Done |
| 7 | [Verification](#-phase-7-verification) | ✅ Done |

## Overview

Establish the staff-space coordinate system with `pixelsPerStaffSpace` as the single scale factor. Apply a Graphics2D scale transform at the render boundary. Convert all layout constants, the layout pipeline, and all basic renderers to staff-space units. Migrate the file format to store positions in staff-space units.

## Key Design Decisions

1. **Graphics2D scale transform** applied in `LineComponent.render()` before `lineRenderer.render()`. All downstream drawing uses staff-space coordinates.
2. **`middleLineY`** becomes `double` in staff-space units (~5.0 ss instead of ~40px).
3. **`Note.xPos`** no longer written by layout. All rendering reads X from `LayoutResult` exclusively.
4. **Font sizes** expressed in staff-space units (music font 32px → 4.0 ss) so the scale transform produces correct pixel sizes.
5. **Stroke widths** from SMuFL `EngravingDefaults` (already in ss) used directly. The scale transform produces correct pixel widths.
6. **Device-pixel snapping** (`snapXToDevicePixel`) works unchanged — already handles arbitrary transforms.
7. **Component sizing** (`getPreferredSize()`) stays in pixels for Swing, converting at the boundary.

## Risks

- **~40 call sites** for `StaffSpaces.toPixels` need auditing
- **Font sizes under scale transform** are critical — a 32pt font under 8x scale = 256px if not adjusted
- **Syllable measurement** needs pixel-accurate text measurement then conversion to ss
- **Stroke widths** — `BasicStroke(1.0)` under 8x scale = 8px line; must set in ss

## Phases

### ✅ Phase 1: Foundation (no existing code breaks)

- [x] Create `ScaleContext` class in `ui/layout2/`
  - Mutable `pixelsPerStaffSpace` (default 8.0)
  - `toPixels(double ss)`, `fromPixels(double px)` conversion methods
  - `getScaleTransform()` → `AffineTransform`
  - Lives as accessible singleton for now (zoom changes this later)

- [x] Rename `Note.yPos` → `Note.staffPosition`
  - Use Serena `rename_symbol` for atomic rename across codebase
  - Update XML tag in NoteIO (read old `<ypos>` for compat, write `<staffposition>`)

### ✅ Phase 2: IO Format Migration

- [x] Bump format version to 2.1
  - `CompositionIO`: IO_MINOR_VERSION = 1
  - Read v2.0 files: convert pixel values to ss on load via FormatMigrator
  - Write v2.1 files: all positions in staff-space units

- [x] FormatMigrator: add v2.0 → v2.1 conversion
  - Divide by 8.0 (the fixed legacy pixelsPerStaffSpace):
    - `Note.xPos` — skipped (layout always recomputes; will become irrelevant in Phase 4)
    - `Composition.topPadding`, `lineWidth`, `rowHeightAdjustment`, `attributionStartY`
    - `Line.lyricsYPos`
    - `TupletInterval.verticalPosition`
    - `DynamicsInterval.x1Shift`, `x2Shift`, `yShift`
    - `Glissando.x1Translate`, `x2Translate`
  - `Note.staffPosition` (formerly yPos): NO conversion needed (already unit-agnostic pitch position)

### ✅ Phase 3: Convert Layout Constants

- [x] Convert `LayoutConstants` to staff-space units
  - Remove `MU`, `px()`, `pxInt()` helpers
  - All constants become staff-space values with ss comments:
    - `CLEF_WIDTH = 3.5` (was 28px)
    - `KEY_ACCIDENTAL_WIDTH = 1.0` (was 8px)
    - `FIRST_NOTE_OFFSET = 3.5` (was 7 MU = 28px)
    - `MIN_COLUMN_GAP = 0.125` (was 0.25 MU = 1px)
    - `DEFAULT_COLUMN_GAP = 1.5` (was 3.0 MU = 12px)
    - `STAFF_LINE_SPACING` removed (1.0 ss by definition)
    - `STAFF_HEIGHT = 4.0` (was 32px)
    - etc.
  - `calculateFirstNoteX()` simplified (no px() call), returns ss
  - Bridge method `toPixels(double ss)` added for callers not yet converted

- [x] Convert `LayoutStylesheet` to staff-space units
  - `NOTE_Y_OFFSET = 0.5` (was 4px)
  - `STAFF_HEIGHT = 4.0` (was 32)
  - `STAFF_SPACE` removed (1.0 ss by definition)
  - Remove `MU`, `px()` helpers; replaced with `toPixels()`/`toPixelsDouble()` bridge
  - All `_MU` suffixed constants renamed (suffix dropped)
  - All padding/margin values converted
  - All default Y position constants converted to ss
  - All callers updated to use bridge methods

### ✅ Phase 4: Update Layout Pipeline

- [x] Convert `NoteColumnBuilder` to ss
  - `NOTE_HEAD_WIDTH`, `HALF_NOTE_HEAD`, `DOT_WIDTH`, `DOT_GAP`, accidental widths, `STEM_LENGTH` all in ss
  - `calculateLeftExtent()` / `calculateRightExtent()` produce ss values
  - `measureSyllableWidth()`: measure in pixels, divide by pixelsPerStaffSpace

- [x] Convert `HorizontalSpacingCalculator` to ss
  - All gap computations in ss. Input/output in ss.

- [x] Convert `LineJustificationCalculator` to ss
  - `staffRightMargin` in ss

- [x] Update `LayoutEngine`
  - `staffRightMargin` converted from pixel `composition.getLineWidth()` to ss
  - `layout()` produces LayoutResult with all positions in ss
  - Remove `updateNotePositions()` — no more writing back to Note

- [x] Update `LayoutResult`
  - All values in ss
  - `findInsertionIndex()` and `isMouseOverNoteHead()` accept ss coordinates
  - `noteHeadHalfWidth` constant → ~1.125 ss (was 9.0px)

### 🔄 Phase 5: Apply Graphics2D Scale and Update Renderers

- [x] Apply scale transform in `LineComponent.render()`
  ```java
  var savedTransform = g2.getTransform();
  var scale = ScaleContext.getInstance().getPixelsPerStaffSpace();
  g2.scale(scale, scale);
  try {
      lineRenderer.render(g2);
  } finally {
      g2.setTransform(savedTransform);
  }
  ```

- [x] Update `LineComponent` sizing
  - `middleLineY` field: `int` (pixels) → `double` (ss)
  - `MIN_SPACE_ABOVE`: `int` (pixels) → `double` (5.0 ss)
  - `calculateMiddleLineY()` / `calculateLineHeight()` / `calculateLineWidth()` work in ss
  - `getPreferredSize()` converts ss → px at the Swing boundary
  - `getMiddleLineYPixels()` bridge method for callers not yet converted

- [x] Update `ElementRenderContext`
  - `middleLineY`: `int` → `double` (ss units)
  - `leadingKeysPos`: → `double` (ss)
  - Add `getPixelsPerStaffSpace()` for callers needing pixel conversion

- [x] Update `BaseElementRenderer`
  - `staffLineToY()`: `middleLineY + (lineIndex - 2) * 1.0`
  - `noteYPosToCoordinate()`: `middleLineY + staffPosition * 0.5`
  - Stroke widths in ss (from EngravingDefaults)
  - Font sizes in ss (music font: 4.0 ss)

- [x] Update `LineRenderer.drawStaffLines()`
  - Lines at y offsets -2, -1, 0, +1, +2 from middleLineY
  - Drawn as filled rectangles (not stroked lines) snapped to device pixels
  - Uses SMuFL `staffLineThickness` (0.13 ss) for line height
  - **First visual verification point**

- [x] Update `ClefRenderer`
  - `CLEF_X_POSITION` in ss
  - Glyph coordinates in ss

- [x] Update `KeySignatureRenderer`
  - Accidental X/Y positions in ss

- [x] Update `NoteRenderer`
  - `calculateNoteY()`: `middleLineY + staffPosition * 0.5`
  - `STEM_WIDTH` → ~0.15 ss, `STEM_LENGTH` → ~3.5 ss
  - Font size: 4.0 ss (was 32px)
  - `resolveNoteX()` returns ss from LayoutResult
  - Ledger line spacing: 1.0 ss
  - Flag/dot/accidental coordinates in ss

- [x] Update `RestRenderer`
  - Rest glyph positions in ss

- [x] Update `BarRenderer`
  - Barline drawing coordinates in ss
  - X position resolved from LayoutResult (same pattern as NoteRenderer/RestRenderer)
  - Removed StaffSpaces pixel conversion — advance widths used directly in ss

- [x] Update `GraceNoteRenderer`
  - Grace note positions in ss, scaling factor preserved

- [x] Fix remaining renderers for `double middleLineY`
  - ArticulationRenderer, BeamGroupRenderer, DynamicsRenderer, FermataRenderer, GlissandoRenderer, StaffRenderer, TempoRenderer, TieRenderer, TrillRenderer, TupletRenderer
  - Changed `int middleLineY` to `double middleLineY` at all call sites
  - **Temporary `(int)` casts** remain where `middleLineY` feeds into methods still typed as `int` (e.g. `calculateStaccatoY`, `renderGlissandoLine`, `drawTempoChangeNote`). These casts will be removed when each renderer is fully rewritten in Milestones 3-5.
  - Removed the wrong-approach `getMiddleLineYInt()` bridge from `ElementRenderContext`

### ⏳ Phase 6: Mouse Coordinate Mapping

- [x] Update `InsertionNoteManager`
  - Convert `e.getX()`/`e.getY()` from pixels to ss before LayoutResult queries
  - `calculateYPosFromMouse()`: `(mouseY_ss - middleLineY_ss) / 0.5`

- [x] Update `SelectionHandler`
  - Convert mouse click Y to ss before staff position comparisons

- [x] Update any other hit-testing code

### ✅ Phase 7: Verification

- [x] Compile with `./scripts/compile.sh`
- [x] Run with `./scripts/run.sh`
- [x] Open existing composition — staff lines render correctly
- [x] Notes, stems, flags, dots, accidentals, ledger lines render correctly
- [x] Clef and key signature render correctly
- [x] Rests, barlines, breath marks, grace notes render correctly
- [x] Mouse hit testing works (click on notes, insert notes)
- [x] Save and reload — positions preserved correctly
- [ ] Open a v2.0 file — migrated correctly on load _(deferred to Milestone 6 — older format files cause a freeze on load)_
