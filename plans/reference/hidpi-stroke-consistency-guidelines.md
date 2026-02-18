# HiDPI Stroke Width Consistency Guidelines (Java Swing / Java2D)

These guidelines aim to produce *visually consistent stroke thickness*
across standard-density (1×) and HiDPI (2×, Retina) displays for both
axis-aligned UI rules and arbitrary-angle strokes.

------------------------------------------------------------------------

## Concepts

-   **User space**: Logical coordinates you draw in.
-   **Device space**: Physical screen pixels.
-   **Scale factor (s)**: 1 for standard displays, 2 for Retina (or
    obtain from the GraphicsConfiguration / transform).
-   **Device-pixel thickness**: Target visual weight expressed in
    physical pixels.

Java2D strokes are specified in *user space*. On HiDPI, the rasterizer +
antialiasing can make 1-user-unit strokes appear optically thinner than
1-device-pixel strokes on 1× displays. Compensate in device space.

------------------------------------------------------------------------

## 1. Unified Stroke Width Selection (Device-Consistent + Optical Compensation)

Let: - `s` = scale factor (1 or 2) - `wDevTarget` = desired thickness in
**device pixels** (e.g., 1 for hairlines, 2+ for heavier strokes) -
`kThin` = small perceptual compensation for very thin strokes (≈
0.15--0.35)

**Algorithm:**

1.  Base conversion:
    -   `wUser = wDevTarget / s`
2.  Optical compensation for hairlines:
    -   If `wDevTarget ≤ 1.25`:\
        `wUser = (wDevTarget + kThin) / s`
3.  Quantize for stability (half-device-pixel steps):
    -   `wDev = wUser * s`
    -   `wDevQ = round(wDev * 2) / 2`
    -   `wUser = wDevQ / s`

Use `new BasicStroke(wUser, CAP, JOIN)`.

------------------------------------------------------------------------

## 2. Axis-Aligned Lines (Horizontal / Vertical)

Prefer geometry-based rendering to avoid antialiasing artifacts.

### 2.1 Filled-Rectangle Method (Best for UI Rules)

For a 1-device-pixel horizontal rule:

-   `tUser = 1 / s`
-   Snap to device pixel grid:
    -   `ySnap = floor(y * s) / s`
-   Draw:
    -   `fill(new Rectangle2D.Float(x, ySnap, width, tUser))`

For vertical rules:

-   `xSnap = floor(x * s) / s`
-   `fill(new Rectangle2D.Float(xSnap, y, tUser, height))`

This yields true 1-device-pixel rules on both 1× and 2× displays.

### 2.2 Stroke + Center Snapping (If You Must Use Strokes)

If using strokes for axis-aligned lines:

-   Offset line centers to pixel centers:
    -   `offset = 0.5 / s`
-   Horizontal line: draw at `y + offset`
-   Vertical line: draw at `x + offset`

This reduces split-coverage across pixel rows/columns.

------------------------------------------------------------------------

## 3. Arbitrary-Angle Strokes

For angled lines, accept antialiasing and focus on stable visual weight.

-   Use the width algorithm in §1.
-   Keep antialiasing enabled.
-   Optionally quantize in **effective device space** when transforms
    are applied.

### 3.1 Quantization Under Transform

If your `Graphics2D` has a transform:

-   Compute effective scale:
    -   `sx = hypot(m00, m10)`
    -   `sy = hypot(m01, m11)`
    -   `sEff = (sx + sy) / 2`
-   Quantize using `sEff * deviceScale` to avoid width wobble under
    zoom/rotation.

------------------------------------------------------------------------

## 4. Antialiasing Strategy

-   **Hairlines (≤ 1 dev px):**
    -   Axis-aligned: prefer filled rectangles.
    -   Angled: keep AA on (disabling AA produces jagged diagonals).
-   **Thicker strokes (≥ 1.5 dev px):**
    -   AA on; strokes are visually stable.

Rendering hints: - `KEY_ANTIALIASING = VALUE_ANTIALIAS_ON` -
`KEY_STROKE_CONTROL = VALUE_STROKE_PURE` (if you handle snapping
yourself)

------------------------------------------------------------------------

## 5. Practical Defaults

Recommended baseline for UI + diagrams:

-   Hairline target:
    -   `wDevTarget = 1.0`
    -   `kThin = 0.25`
    -   Quantize to 0.5 device px

Results: - 1×: \~1.0 user unit - 2×: \~0.625 user units

Use: - Axis-aligned → filled rectangles, snapped - Angled →
`BasicStroke(0.625f)` (quantized)

------------------------------------------------------------------------

## 6. Checklist

-   [ ] Convert stroke widths from device pixels to user space.
-   [ ] Apply small optical compensation for hairlines on HiDPI.
-   [ ] Quantize widths in device space (0.5 px steps).
-   [ ] Use filled rectangles for axis-aligned UI rules.
-   [ ] Snap geometry to device pixel grid.
-   [ ] Keep AA on for angled strokes.
-   [ ] Consider transform-aware quantization under zoom/rotation.

------------------------------------------------------------------------

## 7. Notes

-   Color contrast affects perceived thickness; darker strokes on light
    backgrounds read heavier.
-   Retina subpixel layouts differ from standard LCD subpixel geometry;
    expect small perceptual differences even with perfect device-pixel
    matching.
-   Avoid mixing "normalize" stroke control with manual snapping.

------------------------------------------------------------------------

## Appendix: Terminology

-   **Hairline**: A 1-device-pixel rule used for separators, grids, and
    borders.
-   **Optical compensation**: Small bias added to counteract
    antialiasing lightening effects.
