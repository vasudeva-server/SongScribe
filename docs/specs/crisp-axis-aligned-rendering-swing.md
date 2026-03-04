# Crisp Axis-Aligned Rendering in Swing (Minimal Antialiasing Strategy)

If your goal is *crisp, axis-aligned* notation in Swing with minimal
antialiasing, the correct approach is:

-   Turn off shape antialiasing globally
-   Render horizontal/vertical strokes on the pixel grid
-   Quantize stroke widths to integer device pixels
-   Use "pure" stroke control so Java2D does not normalize strokes
    unpredictably
-   Enable antialiasing only for diagonals and curves when necessary

------------------------------------------------------------------------

## 1) Disable Shape Antialiasing

``` java
g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);

g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON); // optional

g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
```

Notes:

-   With AA off, fractional coordinates will look incorrect unless
    aligned to the device grid.
-   `VALUE_STROKE_PURE` prevents Java2D from performing stroke
    normalization that varies by platform.

------------------------------------------------------------------------

## 2) Compute Device Pixels Per User Unit

To align geometry properly, determine how many device pixels correspond
to one user-space unit:

``` java
AffineTransform tx = g2.getTransform();

double sx = Math.hypot(tx.getScaleX(), tx.getShearX());
double sy = Math.hypot(tx.getScaleY(), tx.getShearY());

double devPerUserX = sx;
double devPerUserY = sy;
```

This allows you to convert between user coordinates and device pixel
coordinates.

------------------------------------------------------------------------

## 3) Pixel Snapping Helpers

### Snap to Pixel Center (for 1px strokes)

``` java
static double snapToDevicePixelCenter(double userCoord, double devPerUser) {
    double dev = userCoord * devPerUser;
    double snapped = Math.floor(dev) + 0.5;
    return snapped / devPerUser;
}
```

### Snap to Pixel Edge (for filled rectangles)

``` java
static double snapToDevicePixelEdge(double userCoord, double devPerUser) {
    double dev = userCoord * devPerUser;
    double snapped = Math.rint(dev);
    return snapped / devPerUser;
}
```

Use:

-   Pixel centers for stroked single-pixel lines
-   Pixel edges for filled rectangles

------------------------------------------------------------------------

## 4) Prefer Filled Rectangles Over Stroked Lines

With AA disabled, `drawLine` combined with `BasicStroke` can behave
inconsistently.

The robust method for staff lines, ledger lines, stems, and barlines is:

-   Compute thickness in **device pixels (integer)**
-   Draw a **filled rectangle aligned to device pixel edges**

Example: horizontal staff line

``` java
int thicknessDevPx = 1;

double y0 = ...;
double x1 = ...;
double x2 = ...;

double yDev = y0 * devPerUserY;
double x1Dev = x1 * devPerUserX;
double x2Dev = x2 * devPerUserX;

double topDev = Math.rint(yDev - thicknessDevPx / 2.0);
double botDev = topDev + thicknessDevPx;

double leftDev  = Math.rint(Math.min(x1Dev, x2Dev));
double rightDev = Math.rint(Math.max(x1Dev, x2Dev));

double top  = topDev / devPerUserY;
double left = leftDev / devPerUserX;
double w    = (rightDev - leftDev) / devPerUserX;
double h    = (botDev - topDev) / devPerUserY;

g2.fill(new Rectangle2D.Double(left, top, w, h));
```

This eliminates nearly all fuzziness for axis-aligned strokes.

------------------------------------------------------------------------

## 5) Device-Pixel-Based Thickness Policy

For consistent ledger/staff differentiation:

-   Staff thickness = N device pixels
-   Ledger thickness = N + 1 device pixels (for screen rendering)
-   Transition to typographic ratios (e.g., 1.23×) only when device
    thickness is sufficiently large

This approach ensures visible differentiation without relying on
fractional pixel rounding.

------------------------------------------------------------------------

## 6) Mixed Rendering Modes (Common in Notation)

### AA Off (Crisp Mode)

-   Staff lines
-   Ledger lines
-   Stems
-   Barlines
-   Staff brackets (axis-aligned)

### AA On or Filled Vector Mode

-   Noteheads
-   Slurs and ties
-   Hairpins
-   Curved articulations
-   Grace slashes
-   Diagonal flags

You may switch rendering hints locally or render curves at higher scale
and composite.

------------------------------------------------------------------------

## 7) Important Limitation

With antialiasing disabled, rotated or skewed drawing will look
incorrect.

For rotated items:

-   Temporarily enable AA, or
-   Render into a higher-resolution buffer and composite

------------------------------------------------------------------------

## Minimal Implementation Strategy

1.  Disable shape AA and enable `STROKE_PURE`
2.  Render axis-aligned primitives as filled rectangles
3.  Snap all geometry to device pixel edges or centers
4.  Use integer device-pixel thickness rules for screen rendering

This produces crisp, predictable results across 1× and 2× displays
without relying on antialiasing.
