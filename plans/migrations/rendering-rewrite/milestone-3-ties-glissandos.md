# Sub-plan: Ties + Glissandos

**Type:** Sub-plan  <br>
**Parent:** [rendering-rewrite.md](rendering-rewrite.md) → Phase 3  <br>
**Created:** 2026-02-21  <br>
**Status:** Pending  <br>
**BlockedBy:** —

**Spec:** [docs/specs/rendering-rewrite.md](../../../docs/specs/rendering-rewrite.md) — always read the spec before implementing tasks.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | [TieInterval + Line Wiring](#-phase-1-tieinterval--line-wiring) | ✅ Done |
| 2 | [TieLayout in LayoutResult](#-phase-2-tielayout-in-layoutresult) | ✅ Done |
| 3 | [Tie Calculation in LayoutEngine](#-phase-3-tie-calculation-in-layoutengine) | ✅ Done |
| 4 | [Rewrite TieRenderer](#-phase-4-rewrite-tierenderer) | ✅ Done |
| 5 | [Migrate GlissandoRenderer to Staff Spaces](#-phase-5-migrate-glissandorenderer-to-staff-spaces) | ✅ Done |
| 6 | [Cleanup](#-phase-6-cleanup) | ✅ Done |
| 7 | [Verification](#-phase-7-verification) | ⏳ Pending |

## Overview

Replace the current quadratic Bezier tie renderer with a cubic Bezier algorithm ported from abc2svg `slur_out()`. Add dynamic height scaling, interior note collision avoidance, and filled lens shape rendering. Move tie geometry calculation into `LayoutEngine` and store results in `LayoutResult.TieLayout`. Introduce `TieInterval` as a typed interval subclass (matching the `BeamInterval` pattern). Migrate `GlissandoRenderer` from pixel-based constants to staff-space units.

Note: SongScribe supports ties only, not slurs. The abc2svg `slur_out()` algorithm is ported for tie rendering only.

## Key Design Decisions

1. **`TieInterval` typed subclass.** `Line.ties` changes from `IntervalSet<Interval>` to `IntervalSet<TieInterval>`. No additional fields — all computed geometry lives in `LayoutResult.TieLayout`. Follows the `BeamInterval` pattern exactly.

2. **Tie geometry in LayoutResult.** `TieLayout` record stores the outer and inner cubic Bezier control points. `TieRenderer` becomes a pure renderer that reads pre-computed geometry from `LayoutResult`.

3. **Cubic Bezier replacing quadratic.** The current quadratic Bezier (two control points, `quadTo`) is replaced by cubic Bezier (four control points, `curveTo`). This provides independent endpoint tangents, producing smoother curves — especially for long ties.

4. **Dynamic height scaling.** Arc height scales with span distance: `height = (0.08 * dx + 1.5) * direction`, clamped to 5.0 ss max. The current fixed arc offsets (+6/-6 pixels) are eliminated.

5. **Interior note collision avoidance.** For ties spanning 3+ notes (rare but possible), interior notes are checked against the tie curve. If collision detected, the tie height is pushed upward.

6. **Filled lens shape.** Outer cubic Bezier + inner cubic Bezier offset by `tieMidpointThickness` (SMuFL EngravingDefaults), forming a closed filled path. Same conceptual approach as the current renderer but with cubic curves.

7. **Glissando migration.** `GlissandoRenderer` is migrated from pixel constants to staff-space constants. No algorithmic change — the glyph-repetition approach is preserved. The mixed unit calculations (pixel offsets added to ss positions) are cleaned up.

8. **`Tie` (RangeElement subclass) preserved for now.** The `Tie` class in `songscribe.ui.layout` is used by `RendererRegistry` and `FormatMigrator`. It will be removed in Phase 6 (Cleanup + Polish) when `RendererRegistry` is eliminated. For now, `TieRenderer` stops using it and reads from `LayoutResult` instead.

## Constants

All tie constants in staff-space units, defined in `LayoutEngine`:

| Constant | Value | Notes |
|----------|-------|-------|
| `TIE_ALFA` | 0.3 | Control point lateral factor (dimensionless). Adjusted for wide ties: `+= 0.002 * (dx - 5.0)`, max 0.7 |
| `TIE_BETA` | 0.45 | Tangent factor (dimensionless) |
| `TIE_HEIGHT_SCALE` | 0.08 | Height scale per distance unit (dimensionless) |
| `TIE_BASE_HEIGHT_SS` | 1.5 ss | Base height (`12 / 8` from abc2svg) |
| `TIE_MAX_HEIGHT_SS` | 5.0 ss | Maximum height (`40 / 8` from abc2svg) |
| `TIE_COLLISION_FACTOR` | 0.65 | Interior deflection scaling (dimensionless) |
| `TIE_COLLISION_PUSH` | 0.45 | Midpoint push-up ratio on collision (dimensionless) |

The `alfa` adjustment threshold (`dx - 5.0` ss) replaces abc2svg's `dx - 40` pixel threshold, converting 40px to 5.0 ss at the default 8px/ss scale.

## Phases

### ✅ Phase 1: TieInterval + Line Wiring

- [x] **Create `TieInterval.java` in `songscribe/data/`**
  - Subclass of `Interval`, no additional fields
  - Constructor: `TieInterval(int start, int end)` — passes `null` data to super
  - Override `copyRange(int, int)` → returns `new TieInterval(newStart, newEnd)`
  - Follow the exact pattern of `BeamInterval`

- [x] **`Line.java`: Change `ties` type**
  - `IntervalSet<Interval> ties` → `IntervalSet<TieInterval>`
  - Update `getTies()` return type accordingly

- [x] **Fix all `getTies()` construction sites**
  - `LineIO`: Added `stringToTieIntervalSet()` that constructs `TieInterval` objects; `XML_TIES` case updated to call it
  - `MusicEditOperations`: Updated `addInterval(int, int)` call to `addInterval(new TieInterval(...))`
  - `LineRenderer.renderTies()`: Removed the `(Interval)` cast (now returns `TieInterval` directly)

### ✅ Phase 2: TieLayout in LayoutResult

- [x] **Add `TieLayout` record to `LayoutResult`**
  ```java
  /**
   * Immutable tie geometry, computed during layout.
   * All values in staff-space units. The outer and inner curves
   * form a filled lens shape when rendered as a closed path.
   *
   * @param startXSs     Tie start X position
   * @param startYSs     Tie start Y position
   * @param endXSs       Tie end X position
   * @param endYSs       Tie end Y position
   * @param cp1XSs       Outer curve control point 1 X
   * @param cp1YSs       Outer curve control point 1 Y
   * @param cp2XSs       Outer curve control point 2 X
   * @param cp2YSs       Outer curve control point 2 Y
   * @param innerCp1XSs  Inner curve control point 1 X
   * @param innerCp1YSs  Inner curve control point 1 Y
   * @param innerCp2XSs  Inner curve control point 2 X
   * @param innerCp2YSs  Inner curve control point 2 Y
   */
  public record TieLayout(
      double startXSs, double startYSs,
      double endXSs, double endYSs,
      double cp1XSs, double cp1YSs,
      double cp2XSs, double cp2YSs,
      double innerCp1XSs, double innerCp1YSs,
      double innerCp2XSs, double innerCp2YSs
  ) {}
  ```

- [x] **Add `tieLayouts` field to `LayoutResult`**
  - `Map<Interval, TieLayout> tieLayouts`
  - Add `getTieLayout(Interval)` accessor returning `Optional<TieLayout>`
  - Update `Builder` with `putTieLayout(Interval, TieLayout)`

### ✅ Phase 3: Tie Calculation in LayoutEngine

This phase implements the abc2svg-ported tie algorithm as a private pipeline step in `LayoutEngine`.

- [x] **Add tie calculation step to `LayoutEngine.layout()` pipeline**
  - After beam and stem calculation (steps 5-6), add: `calculateTies(line, columns, builder)`
  - This runs before `buildLayoutResult()` so tie geometry is available for rendering

- [x] **Implement `calculateTies()` method**
  - For each `TieInterval` in `line.getTies()`:
    1. Get start/end note from line
    2. Get note X positions from columns
    3. Calculate tie endpoints (start X, end X, start Y, end Y)
    4. Calculate tie height and control points
    5. Calculate inner curve control points
    6. Store `TieLayout` in builder

- [x] **Implement tie endpoint calculation**
  - Start X: note X + half notehead width + small offset (in ss)
  - End X: note X + half notehead width - small offset (in ss)
  - Start/End Y: notehead Y ± vertical offset based on tie direction
  - Tie direction: `startNote.isUpper()` → tie below (direction = +1 in screen coords where Y increases downward); `!startNote.isUpper()` → tie above (direction = -1)
  - Mixed stem directions: follow start note's direction

- [x] **Implement dynamic height calculation (abc2svg port)**
  ```
  dx = endX - startX                           // span distance in ss
  height = (TIE_HEIGHT_SCALE * dx + TIE_BASE_HEIGHT_SS) * direction
  height = clamp(abs(height), 0, TIE_MAX_HEIGHT_SS) * signum(height)
  ```

- [x] **Implement cubic Bezier control point calculation (abc2svg port)**
  ```
  mx = 0.5 * (startX + endX)                   // midpoint X
  my = 0.5 * (startY + endY)                   // midpoint Y

  // Adjust alfa for wide ties
  alfa = TIE_ALFA
  if (dx > 5.0) alfa = min(alfa + 0.002 * (dx - 5.0), 0.7)

  // Control point 1 (near start)
  cp1x = mx + alfa * (startX - mx)
  cp1y = my + alfa * (startY - my) + height
  cp1x = startX + TIE_BETA * (cp1x - startX)
  cp1y = startY + TIE_BETA * (cp1y - startY)

  // Control point 2 (near end) — symmetric
  cp2x = mx + alfa * (endX - mx)
  cp2y = my + alfa * (endY - my) + height
  cp2x = endX + TIE_BETA * (cp2x - endX)
  cp2y = endY + TIE_BETA * (cp2y - endY)
  ```

- [x] **Implement interior note collision avoidance**
  - Only applies when tie spans 3+ notes (start index + 2 <= end index)
  - For each interior note: compute its Y position, evaluate the tie curve at that note's X, check clearance
  - If any interior note collides: `h = TIE_COLLISION_FACTOR * maxDeflection`, push height by `TIE_COLLISION_PUSH * h`, recalculate control points

- [x] **Implement inner curve control points**
  - The inner curve is offset from the outer curve by `tieMidpointThickness` (from SMuFL EngravingDefaults)
  - Offset direction depends on tie direction (above/below)
  - Inner control points mirror outer control points with the thickness offset applied to Y values

### ✅ Phase 4: Rewrite TieRenderer

- [x] **Rewrite `TieRenderer.renderTie()` to read from `LayoutResult`**
  - Method signature changes: accept `Interval` + `ElementRenderContext`
  - Read `TieLayout` from `ctx.getLayoutResult().getTieLayout(interval)`
  - Draw outer cubic Bezier: `curveTo(cp1x, cp1y, cp2x, cp2y, endX, endY)`
  - Draw inner cubic Bezier (reversed): `curveTo(innerCp2x, innerCp2y, innerCp1x, innerCp1y, startX, startY)`
  - Close path and fill (filled lens shape)
  - Removed the quadratic Bezier code entirely

- [x] **Remove pixel-based constants from TieRenderer**
  - Deleted `LINE_STROKE` (rendering is fill-only, not stroked)
  - Deleted `NOTE_Y_OFFSET_PX`
  - Removed `StaffSpaces`, `LayoutStylesheet`, `Note`, `NoteType` imports
  - Removed `getHalfNoteWidthForTiePx()` (endpoint calculation lives in `LayoutEngine`)

- [x] **Update `LineRenderer.renderTies()` to pass intervals**
  - Now passes interval directly: `renderTie(g2, interval, ctx)`
  - Guard: `getLayoutResult()` null check + empty Optional check (defensive)

- [x] **Stop extending `BaseElementRenderer<Tie>`**
  - `TieRenderer` is now a standalone class with singleton pattern
  - Removed `registerDefaultRenderers()` entry for `Tie.class` from `RendererRegistry`
  - `Tie` import removed from `RendererRegistry`

### ✅ Phase 5: Migrate GlissandoRenderer to Staff Spaces

- [x] **Convert pixel constants to staff-space units**
  - `GLISSANDO_START_OFFSET_PX` (15px) → `GLISSANDO_START_OFFSET_SS` (1.875 ss)
  - `GLISSANDO_END_GAP_PX` (3px) → `GLISSANDO_END_GAP_SS` (0.375 ss)
  - `SEMIBREVE_OFFSET_PX` (3px) → `SEMIBREVE_OFFSET_SS` (0.375 ss)
  - `GRACE_NOTE_OFFSET_PX` (-3px) → `GRACE_NOTE_OFFSET_SS` (-0.375 ss)
  - `DOT_OFFSET_PX` (6px) → `DOT_OFFSET_SS` (0.75 ss)
  - `END_OF_LINE_OFFSET_PX` (45px) → `END_OF_LINE_OFFSET_SS` (5.625 ss)
  - Remove `StaffSpaces.toPixels()` from `GLISSANDO_LENGTH_PX` → use `0.96` directly as `GLISSANDO_LENGTH_SS`
  - Remove all `StaffSpaces` import

- [x] **Fix mixed-unit calculations**
  - `getGlissandoX1PosPx()` → `getGlissandoX1Ss()`: return type changes to `double`, all arithmetic in ss
  - `getGlissandoX2PosPx()` → `getGlissandoX2Ss()`: same
  - `renderGlissandoLine()`: parameters change from `int` (pixels) to `double` (ss)
  - `noteStaffPositionToCoordinateSs()`: fix the pixel-based `NOTE_FONT_SIZE / 8` to use proper ss conversion (staff position × 0.5 ss, since staff positions are in half-staff-space increments)

- [x] **Update public static methods used by HorizontalAdjustment**
  - `getGlissandoX1PosPx()` → `getGlissandoX1Ss()`: returns `double` in ss
  - `getGlissandoX2PosPx()` → `getGlissandoX2Ss()`: returns `double` in ss
  - Updated callers in `HorizontalAdjustment` to convert ss to pixels via `ScaleContext.getInstance().toPixels()`

### ✅ Phase 6: Cleanup

- [x] **Remove `Tie` (RangeElement subclass) from TieRenderer's type parameter**
  - TieRenderer no longer extends `BaseElementRenderer<Tie>`, so the import of `Tie` can be removed
  - The `Tie` class itself is NOT deleted yet — it's still referenced by `RendererRegistry` and `FormatMigrator`, which will be cleaned up in Phase 6 (Cleanup + Polish) of the master plan

- [x] **Verify no remaining pixel-unit references in tie or glissando code**
  - Grep for `StaffSpaces.toPixels` in TieRenderer and GlissandoRenderer — should be zero
  - Grep for `_PX` constant names in both renderers — should be zero
  - Grep for `LayoutStylesheet.toPixels` in both renderers — should be zero

### ⏳ Phase 7: Verification

- [ ] Compile with `./scripts/compile.sh`
- [ ] Run with `./scripts/run.sh`
- [ ] Open a composition with short ties (2 adjacent notes) — ties render correctly, similar appearance to before
- [ ] Open a composition with long ties (spanning many notes) — ties should be noticeably taller/better shaped than before
- [ ] Ties follow stem direction: stem up → tie below, stem down → tie above
- [ ] Ties render as filled lens shapes (not just stroked lines)
- [ ] Glissandos render correctly (wavy lines between notes, correct angle and length)
- [ ] Glissando manual adjustments (x1Translate, x2Translate) still work
- [ ] Save and reload — ties and glissandos persist correctly
- [ ] No regressions: beams, stems, notes, clef, key signature, rests, barlines, grace notes all render correctly
