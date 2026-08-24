# Spatial Units

How to write spatial values here. For what the program promises about scale and
zoom, see [zoom.md](../../docs/zoom.md).

## Required suffixes

Every spatial value — field, parameter, local, method, constant — carries a unit
suffix. A spatial value with no suffix is a bug; flag it in review.

| Suffix | Unit |
|--------|------|
| `Ss` | staff spaces — the layout unit, where the distance between two adjacent staff lines is 1.0 |
| `Px` | pixels |
| `Sp` | staff positions — discrete integer pitch-grid steps, in half-staff-space units |

Counts, ratios and indices are not spatial and take no suffix.

```java
double xOffsetSs;        // staff spaces
int    staffPositionSp;  // staff positions
int    lineWidthPx;      // pixels
int    elementCount;     // a count — no suffix
```

## Author in staff spaces

Hold and compute spatial values in staff spaces. Convert to pixels at exactly
two kinds of boundary:

- producing a size or position for a Swing component or the print system;
- reading pixel input back from the toolkit — mouse points and font metrics.

Toolkit font metrics are always in pixels. Bring them into staff spaces through
the measurement helpers rather than converting by hand, so text measurement
crosses the boundary in one place.

**Renderers never convert.** The paint transform is applied before any renderer
runs, so renderer code works entirely in staff spaces. A conversion to pixels
inside a renderer is a bug — it double-scales. Flag it in review. Layout code
outside the render path does convert, because it is producing component sizing.

## Rounding at the pixel boundary

Which way a value rounds depends on what it is, and the two are never conflated:

- **Sizes** — widths and heights — round **up**, so content is never clipped.
- **Positions** — coordinates and margins — round to **nearest**, so placement
  stays centered.

There is deliberately no single ambiguous rounding operation, so every call site
must say which rule it means.

## Staff spaces hold the truth

Layout elements expose their dimensions in both units. The staff-space accessor
holds the value; the pixel accessor is a thin conversion of it.

```java
public double getContentWidthSs() { return CONTENT_WIDTH_SS; }

public double getContentWidthPx() { return ssToPx(getContentWidthSs()); }
```

Never store a pixel field duplicating a staff-space field — derive it on demand,
or the two drift and nothing reports it.

## Typed units stop at the paint path

The typed unit records exist to guard the points where one regime **converts**
into another — layout, measurement, mouse and page boundaries — where the wrong
unit is a real and easy mistake. Do not use them inside the per-frame paint path,
which stays on plain arithmetic; where a measurement helper returns a typed
value, unwrap it at once rather than threading it further into paint-time math.

This is a deliberate performance boundary: typed wrappers are cheap but not free,
and the paint path runs every frame. Introducing one there is a correctness no-op
with an allocation cost — flag it in review.

## Field units in the document model

| Type | Unit | When |
|------|------|------|
| `double` | `Ss` | the default for every spatial field |
| `int` | `Sp` | discrete pitch-grid steps |
| `int` | `Px` | paper and resolution dimensions coming from the print system |
