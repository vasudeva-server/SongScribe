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

`ScaleContext` is the single source of truth for the document scale; it holds the mutable `pixelsPerStaffSpace`. Every member is static and the one instance is private, so a conversion is `ScaleContext.ssToPx(ss)` — there is no instance to obtain or to hold in a field.

| Method                  | Direction        | Returns  |
|-------------------------|------------------|----------|
| `ssToPx(ss)`            | `Ss → Px`        | `double` |
| `ssToRoundedPx(ss)`     | `Ss → Px`        | `int`    |
| `pxToSs(px)`            | `Px → Ss`        | `double` |
| `inchesToSs(inches)`    | inches `→ Ss`    | `double` |
| `ssToInches(ss)`        | `Ss →` inches    | `double` |
| `scaleFont(font)`       | px-sized font → ss-sized font | `Font` |
| `textWidthSs(font, s)`  | AWT text advance → `Ss`       | `Ss` |
| `textHeightSs(font)`    | AWT ascent+descent → `Ss`     | `Ss` |
| `fontAscentSs(font)`    | AWT ascent → `Ss`             | `Ss` |
| `fontMaxAscentSs(font)` | AWT max ascent → `Ss`         | `Ss` |
| `fontDescentSs(font)`   | AWT descent → `Ss`            | `Ss` |
| `getScaleTransform()`   | `AffineTransform` scaling `Ss → Px` | `AffineTransform` |

AWT font metrics are always in pixels. Convert them with the `font*Ss` / `text*Ss` helpers — do **not** hand-roll `pxToSs(lm.getAscent())`. (Some existing call sites still do; prefer the helper in new code.) These helpers are a **typed converter**: unwrap the returned `Ss` with `.value()` at the call site rather than threading the typed value further into plain-`double` layout math.

##### Zoom is per-view, not part of `ScaleContext`

`ScaleContext` is a **fixed document scale** (`pixelsPerStaffSpace` is always
`DEFAULT_PIXELS_PER_STAFF_SPACE`) — `ssToPx` / `pxToSs` never vary with on-screen
zoom. Zoom is applied separately, per view, only at view boundaries (the paint
transform, component sizes, mouse input, page sizing). See
[zoom.md](zoom.md) for the full model: the `Ss` / `DocPx` / `ViewPx` unit types,
`ScaleContext` vs. `ViewScale`, and where each conversion belongs.

#### `StaffExtents` — `Sp` ↔ `Ss`

`ScaleContext` does not handle staff positions. Use the static methods on `StaffExtents`:

- `spToSs(staffPositionSp) → double` — `Sp → Ss`
- `ssToSp(ss) → int` — `Ss → Sp` (rounds to nearest)

```java
var deltaYSs = ScaleContext.pxToSs(deltaYPx);
var deltaSp  = StaffExtents.ssToSp(deltaYSs);   // NoteDragHandler.handleDrag
```

### Direction: work in staff spaces

Hold and compute spatial values in `Ss`. Convert to `Px` only when (a) producing a size/position for a Swing component or print system, or (b) reading pixel input back from AWT (mouse points, font metrics).

**Renderers never convert.** `LineComponent.paintComponent` applies `g2.scale(pixelsPerStaffSpace, ...)` before calling any renderer, so renderer code works entirely in `Ss`. A call to `ssToPx` inside a renderer is a bug — flag it in review. (Renderers may still call `pxToSs` to bring AWT font/text metrics into `Ss`.)

**Layout code outside the render path** uses `ssToPx` to produce pixel sizing/positioning.

#### Rounding when crossing to `Px`

- **Sizes** (widths, heights) — round up so content is never clipped: `(int) Math.ceil(ScaleContext.ssToPx(widthSs))`.
- **Positions** (coordinates) — round to nearest: `(int) Math.round(ScaleContext.ssToPx(xSs))`, or use `ssToRoundedPx`.

```java
// LineComponent.getPreferredSize — sizes, ceil (via ViewPx conversion)
return new Dimension(
    toViewPx(new Ss(song.getLineWidthSs())).ceilPx(),
    toViewPx(new Ss(metrics.totalLineHeightSs())).ceilPx());

// LineComponent.getMiddleLineYPx — position, round
return (int) Math.round(ScaleContext.ssToPx(getMiddleLineYSs()) * getViewScale().factor());
```

### Canonical pattern: paired `Ss` / `Px` accessors

Layout elements expose their dimensions in both units. The `Ss` accessor holds the truth; the `Px` accessor is a thin conversion. This pattern is pervasive (`Clef`, `KeySignature`, `Articulation`, `Span`, …) — follow it for any new layout element.

```java
public double getContentWidthSs() {
    return CONTENT_WIDTH_SS;                      // or a computed Ss value
}

public double getContentWidthPx() {
    return ScaleContext.ssToPx(getContentWidthSs());
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
