# Phase 2: Rounded Rect Rendering — Implementation Plan

**Type:** Sub-plan  <br>
**Parent:** plans/glissando-rendering-redesign/glissando-rendering-redesign.md → Phase 2  <br>
**Captured:** 2026-03-01  <br>
**Status:** In Progress  <br>
**BlockedBy:** —

---

## Context

Phase 2 of the glissando rendering redesign replaces the glyph-tiling renderer with a filled rounded rectangle (pill shape). Phase 0 (DRY consolidation) and Phase 1 (Area construction + binary search) are complete. The `GlissandoRenderer` already has `buildNoteArea()`, `findAreaExitPoint()`, and cached notehead shapes. This phase wires them into the actual rendering pipeline.

## File: `src/main/java/songscribe/ui/renderer/GlissandoRenderer.java`

All changes are in this single file.

### Step 1: Update constants

**Remove** these old glyph-tiling constants:
- `MIN_SEGMENTS` (line 64)
- `SLIDE_OUT_SEGMENTS` (line 67)
- `DOTTED_SLIDE_OUT_GAP_SS` (line 80)
- `SLIDE_OUT_TANGENT_LENGTH_SS` (line 83)
- `GLYPH_Y_CENTER_SS` (line 90)

**Change** `MIN_GAP_SS` from `0.5` to `0.3`.

**Add** new constants:
```java
private static final double MIN_RECT_LENGTH_SS = 1.34;
private static final double RECT_THICKNESS_SS = ENGRAVING.legerLineThickness();
private static final double CORNER_RADIUS_SS = RECT_THICKNESS_SS / 2.0;
```

**Remove** helper methods that are no longer needed:
- `computeSlideOutGapSs()` — the area-based gap replaces per-dot logic
- `computeSlideOutTangentLength()` — no longer using glyph advance widths
- `computeGlyphYCenter()` — no glyph tiling

### Step 2: Rewrite `renderGlissando`

The new flow:

1. Compute **notehead center** as anchor: `noteX + getNoteheadRightEdgeSs(note) / 2.0` for X, `noteStaffPositionToCoordinateSs()` for Y.
2. For **CONNECTED**: same center formula for the target note.
3. For **SLIDE_OUT**: direction is fixed 45 degrees down-right. No target anchor needed.
4. Determine if the note is beamed via `line.getBeamings().findInterval(noteIndex) != null`.
5. Build or retrieve the cached Area: `note.getGlissandoArea()`, or build via `buildNoteArea()` and cache with `note.setGlissandoArea()`.
6. Delegate to a new `renderPill()` method (see Step 4).

### Step 3: Rewrite `renderPreviewGlissando`

Same flow as `renderGlissando` but with a sentinel glissando (no user translates). The beamed check uses the same `line.getBeamings().findInterval(sourceIndex)` approach.

### Step 4: Replace `renderGlissandoLine` with `renderPill`

New private method signature:
```java
private void renderPill(
    Graphics2D g2,
    double cx1, double cy1, Area area1,       // source center + area
    double cx2, double cy2, Area area2,        // target center + area (null for SLIDE_OUT)
    double x1Translate, double x2Translate     // user offsets
)
```

Algorithm:
1. Compute tangent direction: `dx = cx2 - cx1`, `dy = cy2 - cy1`. For SLIDE_OUT, `dx = dy = 1.0` (45 degrees).
2. `findAreaExitPoint()` from source center along tangent → `exit1`.
3. Source gap edge = `exit1` + `MIN_GAP_SS` along tangent direction.
4. For CONNECTED: `findAreaExitPoint()` from target center along **negative** tangent → `exit2`. Target gap edge = `exit2` + `MIN_GAP_SS` along negative tangent.
5. For SLIDE_OUT: right endpoint = source gap edge + `MIN_RECT_LENGTH_SS` along tangent.
6. Apply user translates with clamping (cannot enter the area):
   - `effectiveX1Translate = Math.max(x1Translate, -MIN_GAP_SS)` (gap can shrink to 0 but pill can't enter area)
   - Similarly for x2Translate.
7. Compute pill length. If < `MIN_RECT_LENGTH_SS`, skip rendering.
8. Save transform, translate to pill start point, rotate by tangent angle.
9. Fill `new RoundRectangle2D.Double(0, -RECT_THICKNESS_SS / 2, pillLength, RECT_THICKNESS_SS, CORNER_RADIUS_SS * 2, CORNER_RADIUS_SS * 2)`.
10. Restore transform via `GraphicsState.save()`.

For SLIDE_OUT without a target area, use a `maxDist` of ~5.0 ss for the exit point search (generous upper bound covering notehead + stem + flags).

### Step 5: Update private `getGlissandoX1Ss` / `getGlissandoX2Ss`

These private methods change to return **notehead center X** instead of column edge:
- `getGlissandoX1Ss`: returns `noteX + getNoteheadRightEdgeSs(note) / 2.0`
  (no longer depends on glissando type)
- `getGlissandoX2Ss` for CONNECTED: returns `resolveNoteXSs(nextNote) + getNoteheadRightEdgeSs(nextNote) / 2.0`
- `getGlissandoX2Ss` for SLIDE_OUT: no longer needed (direction is fixed, no "x2" anchor)

The **public static** overloads (used by `HorizontalAdjustment`) will reflect the center-based positions. Phase 3 will add proper endpoint methods for handle positioning.

### Step 6: Remove `getNoteRightExtentSs` / `getNoteLeftExtentSs`

These column-extent helpers are no longer needed for rendering — the Area-based approach replaces them. If the public static methods still need them temporarily, keep them; otherwise remove.

Check: `getNoteRightExtentSs` and `getNoteLeftExtentSs` are only used in the private `getGlissandoX1Ss`/`getGlissandoX2Ss` methods. After Step 5 rewrites those, these helpers can be removed.

## Verification

1. `./scripts/compile.sh` — must compile cleanly.
2. `./scripts/run.sh` — visually verify:
   - CONNECTED glissandos render as pills between notes
   - SLIDE_OUT glissandos render as pills at 45 degrees
   - Pills hug noteheads closely, clear dots/accidentals/stems
   - Preview rendering matches committed style
   - Drag handles may be mispositioned (acceptable — Phase 3 fixes this)
