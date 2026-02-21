# Phase 6: Update BeamGroupRenderer to Use LayoutResult

## Context

Phase 6 updates `BeamGroupRenderer.drawBeam()` to read geometry from `LayoutResult`
instead of the removed `Note.Properties` fields (`stem`, `beamThickening`, `lengthening`).
It also converts all pixel-based constants to staff-space constants.

## Coordinate System (verified)

- `LineComponent.paintComponent` applies `g2.scale(pxPerSs, pxPerSs)` — a uniform scale,
  no Y-axis flip.
- **All renderer coordinates are in staff spaces (ss).** Drawing through `g2` in ss units
  is correct; the transform handles conversion to device pixels.
- `middleLineYSs` is in ss (returned by `calculateMiddleLineYSs()`, e.g. ~5–7 ss).
- Staff Y convention: **Y-down** (positive staffPosition = below middle line = larger screen Y).
  - `staffPosition * 0.5` = Y offset from middle line in ss.
  - `noteYPosToCoordinateSs(staffPos, middleLineYSs)` = `middleLineYSs + staffPos * 0.5`.
- The existing `BeamGroupRenderer.drawBeam()` incorrectly calls
  `LayoutStylesheet.toPixelsDouble(staffPos * NOTE_Y_OFFSET)`, converting ss to pixels and
  then adding to an ss value. This is a pre-existing bug Phase 6 fixes.

## StemLayout Coordinate Semantics

From `LayoutResult.StemLayout`: `topYSs`, `bottomYSs`, `lengtheningSs`, `stubRight`.

From `LayoutEngine.calculateUnbeamedStems` (Y-down, `noteYSs = staffPos * 0.5`):

```
stemsUp = !note.isUpper()   ← stemsUp=false when isUpper=true (stem goes up visually)

topYSs    = stemsUp ? noteYSs + MIN_STEM_SS : noteYSs
bottomYSs = stemsUp ? noteYSs               : noteYSs - MIN_STEM_SS
```

Invariant: `topYSs >= bottomYSs` always (topYSs is the larger screen-Y coordinate).

| isUpper | topYSs | bottomYSs |
|---------|--------|-----------|
| true (stem UP) | notehead Y | stem tip Y (above, smaller) |
| false (stem DOWN) | stem tip Y (below, larger) | notehead Y |

**Beam tip Y offset** (relative to `middleLineYSs`):
```java
double stemTipYSsOffset = isUpper ? stemLayout.bottomYSs() : stemLayout.topYSs();
// Screen Y of beam outer edge (level 0): middleLineYSs + stemTipYSsOffset
```

## LayoutEngine Bugs Found (not fixed in Phase 6)

`calculateBeams` and `calculateUnbeamedStems` both have sign bugs in the stem direction logic:
- `calculateBeams`: `stemsUp = (min + max) < 0` → sets upper=true for notes above middle.
  Standard notation: notes above middle → stems down. Should be `> 0`.
- `calculateUnbeamedStems`: `setUpper(staffPosition <= 0)` → stems up for notes on/above middle.
  Should match `defaultUpperNote`: `setUpper(staffPosition > 0)`.

Also, `calculateBeams` has `// TODO: Phase 4 — BeamLayout → builder` — the computed
`stemLayouts` HashMap is never put into the `LayoutResult.Builder`. **This must be fixed
as part of Phase 6** (the builder call is a prerequisite for the renderer to find any data).

These stem-direction bugs should be filed separately and fixed in Phase 8 verification.
For Phase 6, fix only the builder TODO; note the direction bugs as separate work.

## Refactoring: Stem Anchor Constants

`NoteRenderer` has private static `STEM_UP_SE_BLACK`, `STEM_DOWN_NW_BLACK`,
`STEM_UP_SE_HALF`, `STEM_DOWN_NW_HALF` initialized from `SMuFLMetadata`.
`BeamGroupRenderer` needs identical data. **Do not replicate — refactor:**

Move the four anchor constants and their static initializer block from `NoteRenderer`
into `BaseElementRenderer` as `protected static final`. `NoteRenderer` then just
inherits them. `BeamGroupRenderer` (which also extends `BaseElementRenderer`) gets
access for free.

## Files to Change

### 1. `BaseElementRenderer.java`
- Move anchor constants from `NoteRenderer` here as `protected static final`:
  ```java
  protected static final GlyphAnchors.Anchor STEM_UP_SE_BLACK;
  protected static final GlyphAnchors.Anchor STEM_DOWN_NW_BLACK;
  protected static final GlyphAnchors.Anchor STEM_UP_SE_HALF;
  protected static final GlyphAnchors.Anchor STEM_DOWN_NW_HALF;

  static {
      var metadata = SMuFLMetadata.getInstance();
      var blackAnchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
      var halfAnchors  = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_HALF);
      STEM_UP_SE_BLACK  = blackAnchors.stemUpSE();
      STEM_DOWN_NW_BLACK = blackAnchors.stemDownNW();
      STEM_UP_SE_HALF   = halfAnchors.stemUpSE();
      STEM_DOWN_NW_HALF = halfAnchors.stemDownNW();
  }
  ```
- Add imports: `GlyphAnchors`, `SMuFLGlyph`, `SMuFLMetadata`.
- Add a small `protected` helper used by both renderers:
  ```java
  /**
   * Returns the X offset (in ss, relative to note reference point) from the note
   * center to the stem's center for the given stem direction and note type.
   */
  protected static double stemCenterXOffsetSs(NoteType noteType, boolean upper) {
      boolean isMinim = noteType == NoteType.MINIM;
      double anchorX = upper
          ? (isMinim ? STEM_UP_SE_HALF.x()   : STEM_UP_SE_BLACK.x())
          : (isMinim ? STEM_DOWN_NW_HALF.x() : STEM_DOWN_NW_BLACK.x());
      // upper: stem right edge is at anchorX, center = anchorX - STEM_WIDTH_SS/2
      // lower: NW anchor is the left edge after notehead shift; center = anchorX
      return upper ? anchorX - NoteRenderer.STEM_WIDTH_SS / 2.0 : anchorX;
  }
  ```
  Wait — `NoteRenderer.STEM_WIDTH_SS` would create a circular dependency since
  `NoteRenderer` extends `BaseElementRenderer`. Move `STEM_WIDTH_SS` to
  `BaseElementRenderer` instead (or define it in both; it's a SMuFL constant).
  Cleaner: define `STEM_WIDTH_SS` in `BaseElementRenderer` as `protected static final`
  and have `NoteRenderer` reference it from there (or keep its own alias).

### 2. `NoteRenderer.java`
- Delete the four `STEM_UP_SE_*` / `STEM_DOWN_NW_*` field declarations and their
  static initializer block (now inherited from `BaseElementRenderer`).
- Keep `STEM_WIDTH_SS` here (or move to base; either works — moving is cleaner).
  If moved to base, update references.
- Remove now-unused imports (`GlyphAnchors`, `SMuFLGlyph`, `SMuFLMetadata`) if they
  were only used for anchor init. (Keep if used elsewhere.)

### 3. `LayoutEngine.java` — fix Phase 4 TODO
Replace the `// TODO: Phase 4 — BeamLayout → builder` comment with:
```java
var beamLayout = new LayoutResult.BeamLayout(slope, startYSs, stemsUp, thickeningSs, stemLayouts);
builder.putBeamLayout(interval, beamLayout);
```
All variables (`slope`, `startYSs`, `stemsUp`, `thickeningSs`, `stemLayouts`) are in scope
at that point.

### 4. `BeamGroupRenderer.java`

#### 4a. Imports
Remove:
- `songscribe.smufl.EngravingDefaults`
- `songscribe.smufl.StaffSpaces`
- `songscribe.ui.layout.LayoutStylesheet`

Add:
- `org.jetbrains.annotations.Nullable`
- `songscribe.music.Note`
- `songscribe.ui.layout2.LayoutResult`

`GlyphAnchors`, `SMuFLGlyph`, `SMuFLMetadata` are no longer needed here (moved to base).

#### 4b. Constants
Remove:
```java
private static final EngravingDefaults ENGRAVING_DEFAULTS = ...;
private static final double BEAM_THICKNESS_PX = StaffSpaces.toPixels(...);
private static final double INNER_BEAM_LENGTH_PX = 11d;
private static final double INNER_BEAM_OFFSET_PX = StaffSpaces.toPixels(...);
```

Add:
```java
// Beam geometry constants (staff-space units; scale transform handles pixel conversion)
private static final double BEAM_DEPTH_SS  = 0.4;    // beam thickness
private static final double BEAM_SHIFT_SS  = 0.625;  // gap between stacked beam levels
private static final double BEAM_STUB_SS   = 1.0;    // partial beam stub length
private static final double CLIP_SLOP_SS   = 0.25;   // extra clipping margin (~2 px)
```

#### 4c. `renderElement` — fix log statements
Remove the log lines that access `note.properties.lengthening`, `note.properties.stem`
(they'll be compile errors). Replace with log lines that read from `layoutResult`
or omit the removed fields.

#### 4d. `drawBeams` — look up BeamLayout, pass to `doDrawBeams`
```java
private void drawBeams(...) {
    var outerNotes = new Point(beginIndex, endIndex);
    var layoutResult = ctx.getLayoutResult();
    var interval = (layoutResult != null) ? line.getBeamings().findInterval(beginIndex) : null;
    var beamLayout = (layoutResult != null && interval != null)
        ? layoutResult.getBeamLayout(interval) : null;
    doDrawBeams(g2, level, line, ctx, outerNotes,
        beginIndex, endIndex, beginIndex, endIndex, false, 0, selected, beamLayout);
}
```

#### 4e. `doDrawBeams` — add `beamLayout` param, use `StemLayout.stubRight()` for stubs
Add `@Nullable LayoutResult.BeamLayout beamLayout` as last parameter.

For the half-beam (single-note) stub direction, replace the existing heuristic:
```java
// old:
leftOriented = (prevBeginIndex == prevEndIndex)
    ? isPrevLeftOriented
    : (beginIndex != prevBeginIndex);

// new:
var layoutResult = ctx.getLayoutResult();
var stubStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(beginNote) : null;
leftOriented = (stubStemLayout != null)
    ? !stubStemLayout.stubRight()
    : (prevBeginIndex == prevEndIndex) ? isPrevLeftOriented : (beginIndex != prevBeginIndex);
```

Pass `beamLayout` to `drawBeam` calls and to recursive `doDrawBeams` calls.

#### 4f. `drawBeam` — full rewrite

Signature gains `@Nullable LayoutResult.BeamLayout beamLayout` as last parameter.

```java
private void drawBeam(
    @NotNull Graphics2D g2,
    @NotNull Line line,
    @NotNull ElementRenderContext ctx,
    int beginIndex,
    int endIndex,
    boolean isUpper,
    @NotNull BeamType type,
    int recursionLevel,
    boolean selected,
    @Nullable LayoutResult.BeamLayout beamLayout
) {
    var beginNote = line.getNote(beginIndex);
    var endNote   = line.getNote(endIndex);
    var layoutResult = ctx.getLayoutResult();
    double middleLineYSs = ctx.getMiddleLineYSs();
    double halfStemWidthSs = STEM_WIDTH_SS / 2.0;

    // --- Thickening (from BeamLayout, zero if unavailable) ---
    double thickeningSs = (beamLayout != null) ? beamLayout.thickeningSs() : 0.0;
    double effectiveBeamDepthSs  = BEAM_DEPTH_SS + thickeningSs;
    double beamDepthSs           = isUpper ? effectiveBeamDepthSs : -effectiveBeamDepthSs;
    double effectiveBeamShiftSs  = BEAM_SHIFT_SS + thickeningSs;
    double innerBeamOffsetSs     = effectiveBeamShiftSs * recursionLevel * (isUpper ? 1 : -1);

    // --- First note stem geometry ---
    var firstStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(beginNote) : null;
    double firstNoteXSs = (layoutResult != null)
        ? layoutResult.getNoteXSs(beginNote) : beginNote.getXPos();
    double firstStemCenterXSs = firstNoteXSs
        + stemCenterXOffsetSs(beginNote.getNoteType(), isUpper);
    double firstX       = GraphicUtils.snapXToDevicePixel(g2, firstStemCenterXSs - halfStemWidthSs);
    double firstTipYSs  = stemTipYSsOffset(firstStemLayout, isUpper, beginNote);
    double firstOuterY  = GraphicUtils.snapYToDevicePixel(
        g2, middleLineYSs + firstTipYSs + innerBeamOffsetSs);
    double firstInnerY  = GraphicUtils.snapYToDevicePixel(g2, firstOuterY + beamDepthSs);

    // --- Last note stem geometry ---
    var lastStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(endNote) : null;
    double lastNoteXSs = (layoutResult != null)
        ? layoutResult.getNoteXSs(endNote) : endNote.getXPos();
    double lastStemCenterXSs = lastNoteXSs
        + stemCenterXOffsetSs(endNote.getNoteType(), isUpper);
    double lastX       = GraphicUtils.snapXToDevicePixel(g2, lastStemCenterXSs + halfStemWidthSs);
    double lastTipYSs  = stemTipYSsOffset(lastStemLayout, isUpper, endNote);
    double lastOuterY  = GraphicUtils.snapYToDevicePixel(
        g2, middleLineYSs + lastTipYSs + innerBeamOffsetSs);
    double lastInnerY  = GraphicUtils.snapYToDevicePixel(g2, lastOuterY + beamDepthSs);

    // --- Build and draw parallelogram (unchanged logic) ---
    var beam = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
    beam.moveTo(firstX, firstOuterY);
    beam.lineTo(lastX,  lastOuterY);
    beam.lineTo(lastX,  lastInnerY);
    beam.lineTo(firstX, firstInnerY);
    beam.closePath();

    Shape oldClip = null;

    if (type != BeamType.FULL) {
        var clip = beam.getBounds2D();
        double x1 = (type == BeamType.ATTACH_LEFT)
            ? firstX - CLIP_SLOP_SS
            : lastX  - BEAM_STUB_SS;
        clip.setRect(
            x1,
            clip.getMinY() - CLIP_SLOP_SS,
            BEAM_STUB_SS + CLIP_SLOP_SS,
            clip.getHeight() + CLIP_SLOP_SS * 2);
        oldClip = g2.getClip();
        g2.setClip(clip);
    }

    try (var ignored = GraphicsState.save(g2, COLOR)) {
        g2.setColor(selected ? Score.SELECTION_STROKE_COLOR : NOTE_COLOR);
        g2.fill(beam);
    }

    if (oldClip != null) {
        g2.setClip(oldClip);
    }
}
```

#### 4g. New private helpers

```java
/**
 * Returns the Y offset from {@code middleLineYSs} to the beam-connection end of the stem
 * (the stem tip), in staff-space units.
 *
 * @param layout   StemLayout from LayoutResult, or null if unavailable
 * @param isUpper  true = stem goes up (beam above notes)
 * @param note     fallback note for staff-position estimate when layout is null
 */
private static double stemTipYSsOffset(
    @Nullable LayoutResult.StemLayout layout,
    boolean isUpper,
    @NotNull Note note
) {
    if (layout != null) {
        // topYSs = larger Y (lower screen); bottomYSs = smaller Y (higher screen)
        // Stem-up tip = bottomYSs; stem-down tip = topYSs
        return isUpper ? layout.bottomYSs() : layout.topYSs();
    }

    // Fallback: approximate from staff position + standard stem length
    double noteYSs = note.getStaffPosition() * 0.5;
    return isUpper ? noteYSs - 3.5 : noteYSs + 3.5;  // 3.5 = MIN_STEM_SS
}
```

`stemCenterXOffsetSs` lives in `BaseElementRenderer` (see §1 above).

## Implementation Order

1. ✅ Move `STEM_WIDTH_SS` to `BaseElementRenderer` (if not already accessible).
2. ✅ Move stem anchor constants + static init block from `NoteRenderer` to
   `BaseElementRenderer`; add `stemCenterXOffsetSs` helper there.
3. ✅ Fix `NoteRenderer` to use inherited constants (remove deleted fields, update imports).
4. ✅ Fix the `// TODO: Phase 4 — BeamLayout → builder` in `LayoutEngine.calculateBeams`.
5. ✅ Rewrite `BeamGroupRenderer` (constants, imports, all method changes above).
6. ✅ Compile: `./scripts/compile.sh`.

## Notes

- `renderBeams` (the public entry point from `LineRenderer`) also calls `drawBeams`,
  so the `beamLayout` lookup happens inside `drawBeams` — no signature change needed
  for `renderBeams`.
- Log statements that referenced removed `Note.Properties` fields should be rewritten
  to read from `layoutResult` or simply omit the removed values.
- After Phase 6 compiles, `Note.Properties.beamThickening` and `Note.Properties.stem`
  are no longer read by `BeamGroupRenderer`. They will be removed once `NoteRenderer`
  is also updated in Phase 7.
