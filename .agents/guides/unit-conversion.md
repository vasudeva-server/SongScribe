### Required suffixes on spatial values (field, param, local, method, constant)

Every spatial value carries a unit suffix. A spatial value with no suffix is a bug — flag it in review.

| Suffix | Unit                                                                          |
|--------|-------------------------------------------------------------------------------|
| `Ss`   | staff spaces (layout unit; distance between two adjacent staff lines is `1.0`) |
| `Px`   | pixels (1 ss = `ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE` px at 1:1 zoom)   |
| `Sp`   | staff positions (discrete integer pitch-grid steps, in half-staff-space units) |

Counts, ratios, and indices take no suffix.

```java
double xOffsetSs;          // staff spaces
int    staffPositionSp;    // staff positions
int    lineWidthPx;        // pixels
int    elementCount;       // count — no suffix
```

### Converters

#### `ScaleContext` — `Ss` ↔ `Px`

`ScaleContext.getInstance()` is the single source of truth; it holds the mutable `pixelsPerStaffSpace`.

| Method                  | Direction        | Returns  |
|-------------------------|------------------|----------|
| `ssToPx(ss)`            | `Ss → Px`        | `double` |
| `ssToRoundedPx(ss)`     | `Ss → Px`        | `int`    |
| `pxToSs(px)`            | `Px → Ss`        | `double` |
| `scaleFont(font)`       | px-sized font → ss-sized font | `Font` |
| `textWidthSs(font, s)`  | AWT text advance → `Ss`       | `double` |
| `textHeightSs(font)`    | AWT ascent+descent → `Ss`     | `double` |
| `fontAscentSs(font)`    | AWT ascent → `Ss`             | `double` |
| `fontMaxAscentSs(font)` | AWT max ascent → `Ss`         | `double` |
| `fontDescentSs(font)`   | AWT descent → `Ss`            | `double` |
| `getScaleTransform()`   | `AffineTransform` scaling `Ss → Px` | `AffineTransform` |

AWT font metrics are always in pixels. Convert them with the `font*Ss` / `text*Ss` helpers — do **not** hand-roll `pxToSs(lm.getAscent())`. (Some existing call sites still do; prefer the helper in new code.)

#### `StaffExtents` — `Sp` ↔ `Ss`

`ScaleContext` does not handle staff positions. Use the static methods on `StaffExtents`:

- `spToSs(staffPositionSp) → double` — `Sp → Ss`
- `ssToSp(ss) → int` — `Ss → Sp` (rounds to nearest)

```java
var deltaYSs = ScaleContext.getInstance().pxToSs(deltaYPx);
var deltaSp  = StaffExtents.ssToSp(deltaYSs);   // NoteDragHandler.handleDrag
```

### Direction: work in staff spaces

Hold and compute spatial values in `Ss`. Convert to `Px` only when (a) producing a size/position for a Swing component or print system, or (b) reading pixel input back from AWT (mouse points, font metrics).

**Renderers never convert.** `LineComponent.paintComponent` applies `g2.scale(pixelsPerStaffSpace, ...)` before calling any renderer, so renderer code works entirely in `Ss`. A call to `ssToPx` inside a renderer is a bug — flag it in review. (Renderers may still call `pxToSs` to bring AWT font/text metrics into `Ss`.)

**Layout code outside the render path** uses `ssToPx` to produce pixel sizing/positioning.

#### Rounding when crossing to `Px`

- **Sizes** (widths, heights) — round up so content is never clipped: `(int) Math.ceil(scale.ssToPx(widthSs))`.
- **Positions** (coordinates) — round to nearest: `(int) Math.round(scale.ssToPx(xSs))`, or use `ssToRoundedPx`.

```java
// LineComponent.getPreferredSize — sizes, ceil
return new Dimension(
    (int) Math.ceil(scale.ssToPx(result.getLineWidthSs())),
    (int) Math.ceil(scale.ssToPx(metrics.totalLineHeightSs())));

// LineComponent.getMiddleLineYPx — position, round
return (int) Math.round(ScaleContext.getInstance().ssToPx(getMiddleLineYSs()));
```

### Canonical pattern: paired `Ss` / `Px` accessors

Layout elements expose their dimensions in both units. The `Ss` accessor holds the truth; the `Px` accessor is a thin conversion. This pattern is pervasive (`Clef`, `KeySignature`, `Articulation`, `RangeElement`, …) — follow it for any new layout element.

```java
public double getContentWidthSs() {
    return CONTENT_WIDTH_SS;                      // or a computed Ss value
}

public double getContentWidthPx() {
    return ScaleContext.getInstance().ssToPx(getContentWidthSs());
}
```

Never store a `Px` field that duplicates an `Ss` field — derive it on demand.

### Spatial data-model field units

| Type     | Unit | When                                                                                                       |
|----------|------|------------------------------------------------------------------------------------------------------------|
| `double` | `Ss` | Default for all spatial fields                                                                             |
| `int`    | `Sp` | Discrete integer pitch-grid steps (`staffPosition`)                                                        |
| `int`    | `Px` | Paper/DPI dimensions from the OS print system (`PageLayoutData`)                                            |
| `int`    | `Px` | Legacy serialization-only fields in `Line` (`*YPosPx`); explicitly marked as legacy — do not add new ones   |
