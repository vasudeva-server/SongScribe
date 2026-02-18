**Type:** Sub-plan  <br>
**Parent:** plans/migrations/smufl-rewrite/smufl-rewrite.md → Phase 7a  <br>
**Captured:** 2026-02-15  <br>
**Pre-planned:** No  <br>
**Status:** Pending  <br>
**Note:** Originally Phase 3a, moved to Phase 7a after Phase 7 glyph conversions

---

# Phase 7a: Screen Rendering Stroke Quality

## Context

After Phases 5-7 convert staff lines, stems, barlines, note heads, and other elements to SMuFL glyphs, on-screen rendering may still exhibit anti-aliasing artifacts. SMuFL glyph outlines and remaining drawn elements (beams, ties, hairpins, tuplet brackets) use fractional coordinates and stroke widths from `engravingDefaults` (e.g., `stemThickness` = 0.12 staff spaces = 0.96px, `staffLineThickness` = 0.13 ss = 1.04px). On screen at typical display densities, fractional rendering produces anti-aliasing artifacts — grey fringing where strokes and glyph edges don't land cleanly on device pixel boundaries.

### Current Rendering Hint State

- **`ScoreComponent.initGraphics()`** — Sets `KEY_ANTIALIASING=ON`, `KEY_TEXT_ANTIALIASING=ON`, `KEY_FRACTIONALMETRICS=ON`. No `KEY_STROKE_CONTROL` hint at all.
- **`GraphicUtils.setRenderingHints()`** — Legacy method, only called from `HiDPIScaledGraphics` constructor (used by `HiDPIScaledImage` for offscreen image creation). Sets `VALUE_STROKE_PURE` on Retina, `VALUE_STROKE_NORMALIZE` on non-Retina. This code path does **not** affect the main screen rendering pipeline.
- Net result: Screen rendering uses the JVM default for `KEY_STROKE_CONTROL`, which is `VALUE_STROKE_DEFAULT` (implementation-defined, typically equivalent to `VALUE_STROKE_NORMALIZE` on most platforms).

### How Java2D Stroke Control Works

| Value | Behavior |
|-------|----------|
| `VALUE_STROKE_NORMALIZE` | Snaps stroke edges to device pixel grid. A 1.04px stroke renders as a crisp 1px line. Diagonal/curved strokes still get antialiasing. |
| `VALUE_STROKE_PURE` | Renders strokes at exact geometric coordinates. Produces sub-pixel accuracy but grey fringing on axis-aligned lines. Best for print/high-density output. |
| `VALUE_STROKE_DEFAULT` | Platform-dependent; typically equivalent to `NORMALIZE` on screen, `PURE` on printers. |

### Why `VALUE_STROKE_NORMALIZE` Should Work

SMuFL engraving defaults produce near-integer pixel widths at our current scale (1 ss = 8px):
- `staffLineThickness` = 0.13 ss = 1.04px → normalizes to 1px
- `stemThickness` = 0.12 ss = 0.96px → normalizes to 1px
- `thinBarlineThickness` = 0.16 ss = 1.28px → normalizes to 1px
- `thickBarlineThickness` = 0.5 ss = 4.0px → already integer
- `beamThickness` = 0.5 ss = 4.0px → already integer
- `legerLineThickness` = 0.16 ss = 1.28px → normalizes to 1px

The values are close enough to integers that normalization will produce clean results without visibly distorting proportions.

## Strategy

**Step 1 (primary):** Add `KEY_STROKE_CONTROL = VALUE_STROKE_NORMALIZE` to `ScoreComponent.initGraphics()`. This is the simplest change and should resolve artifacts for the main screen rendering path.

**Step 2 (if needed):** If normalization causes visible distortion or artifacts on specific elements (e.g., beams at 4px being shifted oddly, or curves losing smoothness), implement a `StrokeFactory` that provides context-aware stroke control — normalizing axis-aligned strokes while preserving pure rendering for curves/diagonals.

**Step 3 (deferred to Phase 9):** The legacy `GraphicUtils.setRenderingHints()` / `HiDPIScaledGraphics` / `HiDPIScaledImage` / `RetinaImage` infrastructure is separate from the main screen rendering path and will be removed entirely in Phase 9. No changes needed here.

## Changes

### Step 1: ScoreComponent.initGraphics()

Add `KEY_STROKE_CONTROL` hint after existing hints:

```java
protected Graphics2D initGraphics(Graphics g) {
    var g2 = (Graphics2D) g;

    g2.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON
    );
    g2.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON
    );
    g2.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS,
        RenderingHints.VALUE_FRACTIONALMETRICS_ON
    );
    g2.setRenderingHint(
        RenderingHints.KEY_STROKE_CONTROL,
        RenderingHints.VALUE_STROKE_NORMALIZE
    );

    return g2;
}
```

**Files modified:** `src/main/java/songscribe/ui/component/score/ScoreComponent.java`

### Step 2 (conditional): StrokeFactory

Only implement if Step 1 produces unacceptable results on certain elements. The factory would:

- Provide `BasicStroke` instances that are pixel-aligned for screen rendering
- Accept the raw SMuFL staff-space value and the current rendering context (screen vs. export)
- For screen: round stroke widths to nearest device pixel
- For export: pass through raw sub-pixel values unchanged
- Support fractional scale factors (125%, 150%, 175% on Windows/Linux)

```java
public final class StrokeFactory {
    public enum RenderContext { SCREEN, EXPORT }

    public static BasicStroke create(double staffSpaces, RenderContext context) {
        var pixels = StaffSpaces.toPixels(staffSpaces);
        if (context == RenderContext.SCREEN) {
            pixels = Math.max(1.0, Math.round(pixels));
        }
        return new BasicStroke((float) pixels);
    }
}
```

**Files created (if needed):** `src/main/java/songscribe/ui/renderer/StrokeFactory.java`
**Files modified (if needed):** All renderers that create `BasicStroke` from engraving defaults

### Step 3: Staff Lines — fillRect

Even with `VALUE_STROKE_NORMALIZE`, `drawLine` for staff lines can still exhibit anti-aliasing because the stroke geometry involves sub-pixel edge positions. `fillRect` operates entirely on integer pixel boundaries and is guaranteed crisp.

Replace the `drawLine` + stroke approach in `StaffRenderer` with `fillRect`:

```java
@Override
protected void renderElement(
    @NotNull Staff element,
    @NotNull Graphics2D g2,
    @NotNull ElementRenderContext ctx
) {
    int lineThickness = Math.max(1,
        (int) Math.round(StaffSpaces.toPixels(ENGRAVING_DEFAULTS.staffLineThickness())));
    int halfThickness = lineThickness / 2;

    try (var ignored = GraphicsState.save(g2, COLOR)) {
        g2.setColor(STAFF_LINE_COLOR);

        int middleLineY = ctx.getMiddleLineY();
        int staffWidth = (int) element.getWidth();

        for (int i = 0; i < LayoutStylesheet.STAFF_LINE_COUNT; i++) {
            int y = staffLineToY(i, middleLineY);
            g2.fillRect(0, y - halfThickness, staffWidth, lineThickness);
        }
    }
}
```

Note: `STAFF_LINE_STROKE` in `BaseElementRenderer` becomes unused after this change. It will be removed in Phase 8 along with other dead stroke constants.

**Files modified:** `src/main/java/songscribe/ui/renderer/StaffRenderer.java`

### What NOT to Change

- **`GraphicUtils.setRenderingHints()`** — Legacy code only used by `HiDPIScaledGraphics`. Removed in Phase 9.
- **`GraphicUtils.getDpiAwareStrokeWidth()`** — Only used by `ArticulationRenderer`. Will be removed in Phase 7a when accents become SMuFL glyphs.
- **Other stroke constants in renderers** — `STEM_STROKE`, `LEDGER_LINE_STROKE`, and others stay as-is. Stroke normalization handles them.
- **Export pipeline** — PDF/image export is currently a stub. When implemented, it will use `VALUE_STROKE_PURE` for sub-pixel accuracy on high-DPI output. That's a future concern.

## Verification

### 1. Compilation

```bash
./scripts/compile.sh
```

### 2. Visual Inspection

Run the app and verify axis-aligned strokes at the following display conditions:

**On Retina (2x) display:**
- [ ] Staff lines are crisp, no grey fringing (fillRect, no stroke)
- [ ] Stems are crisp, consistent width
- [ ] Ledger lines are crisp, consistent width
- [ ] Single barlines are crisp
- [ ] Double barlines: both lines crisp, gap consistent
- [ ] Final barlines: thin line crisp, thick line solid
- [ ] Repeat barlines: all lines crisp

**On non-Retina (1x) display (if available):**
- [ ] Same checks as above

**Curved/diagonal elements (should be unaffected):**
- [ ] Ties render smoothly (no jagged curves)
- [ ] Hairpin crescendo/diminuendo lines are smooth
- [ ] Beams at angles render smoothly
- [ ] Tuplet brackets render correctly

### 3. Automated Tests

```bash
mvn test
```

No test changes expected — rendering hints don't affect layout calculations.

### 4. Comparison with Pre-Change Rendering

The key visual improvement should be:
- **Before:** Axis-aligned strokes with fractional widths show grey fringing (especially visible on 1x displays)
- **After:** Axis-aligned strokes snap to pixel grid, appear solid and crisp

### 5. Edge Cases

- [ ] Zoom in/out in the application (if supported) — strokes should remain crisp at all zoom levels
- [ ] Very thin strokes (0.96px stems) don't disappear at any zoom level
- [ ] Very thick strokes (4.0px beams, thick barlines) aren't visibly shifted

## Risk Assessment

**Low risk.** This is a single rendering hint addition. `VALUE_STROKE_NORMALIZE` is the standard choice for screen rendering and is well-tested in Java2D. The change is purely visual and affects no layout calculations, file I/O, or test expectations. If it causes unexpected issues, it's a one-line revert.

## Dependencies

- **Depends on:** Phases 5-7 (glyph conversions complete — staff lines, stems, barlines, note heads, rests, accidentals, flags, articulations all using SMuFL glyphs)
- **Blocks:** Phase 8 (Fughetta cleanup should wait until rendering quality is finalized)
- **No dependency on:** Phase 9 (HiDPI infrastructure removal is independent)

## File Summary

| File | Action | Description |
|------|--------|-------------|
| `ScoreComponent.java` | Modify | Add `KEY_STROKE_CONTROL = VALUE_STROKE_NORMALIZE` to `initGraphics()` |
| `StrokeFactory.java` | Create (if needed) | Pixel-aligned stroke factory for screen vs. export contexts |
| `StaffRenderer.java` | Modify | Replace `drawLine` + `STAFF_LINE_STROKE` with pixel-aligned `fillRect` |
