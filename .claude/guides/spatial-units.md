# Spatial Units

How to write spatial values here. For what the program promises about scale and
zoom, see [zoom.md](../../docs/zoom.md).

## Required suffixes

Every spatial value — field, parameter, local, method, constant — carries a unit
suffix. A spatial value with no suffix is a bug; flag it in review.

| Suffix | Unit |
|--------|------|
| `Ss` | staff spaces — the layout unit, where the distance between two adjacent staff lines is 1.0 |
| `DocPx` | document pixels — pixels at the fixed document scale, i.e. at 100% zoom |
| `ViewPx` | view pixels — on-screen pixels at the view's current zoom |
| `Sp` | staff positions — discrete integer pitch-grid steps, in half-staff-space units |

A bare `Px` names a unit but not a regime, and the two pixel regimes differ by
whatever the view happens to be zoomed to. Name the regime — in the suffix, or
in the type where the value is a `DocPx` or a `ViewPx`, which say it themselves.

Counts, ratios and indices are not spatial and take no suffix.

```java
double xOffsetSs;           // staff spaces
int    staffPositionSp;     // staff positions
int    lineWidthDocPx;      // document pixels
int    mouseXViewPx;        // view pixels
ViewPx lineWidth;           // the type names the regime
int    elementCount;        // a count — no suffix
```

## Author in staff spaces

Hold and compute spatial values in staff spaces. Convert to pixels at exactly
two kinds of boundary:

- producing a size or position for a Swing component or the print system;
- reading pixel input back from the toolkit — mouse points and font metrics.

Ask `TextMeasurement` rather than measuring text yourself. Its render context is
package-private so that every measurement is taken with the instrument the paint
pass draws with; a run measured under a context of your own ends somewhere else.

**A text measurement answers in the unit its font was sized in**, which the query
cannot know and does not name. Pass a font sized in staff spaces — as the music,
tuplet and volta fonts are — and the answer is in staff spaces already;
converting it would scale it twice. Pass one sized in document pixels, as the
lyrics and attribution fonts are, and converting the answer is the crossing
itself, not a bypass of one. Read the font's size before you decide, and suffix
the result for what came back.

Measure off a live `Graphics2D` only to draw with the answer immediately, in that
same paint pass. Such a measurement is in view pixels at one particular zoom, so
it may not be stored, and nothing derived from it may reach layout.

**Renderers never convert.** The paint transform is applied before any renderer
runs, so renderer code works entirely in staff spaces. A conversion to pixels
inside a renderer is a bug — it double-scales. Flag it in review. Layout code
outside the render path does convert, because it is producing component sizing.

## Rounding at the pixel boundary

Which way a value rounds depends on what it is, and the two are never conflated:

- **Sizes** — widths and heights — round **up**, through `sizePx()`, so content
  is never clipped.
- **Positions** — coordinates and margins — round to **nearest**, through
  `positionPx()`, so placement stays centered.

There is deliberately no single ambiguous rounding operation, so every call site
must say which rule it means.

`Ss` is outside `PixelDistance` — the sealed interface `DocPx` and `ViewPx`
implement — by construction, not by omission. Staff spaces have no integer form
to round to, so a staff-space value has nothing to round until it names the
pixel regime it is entering, and the rule applies there.

## Staff spaces hold the truth

A layout element states its content dimensions in staff spaces and in no other
unit. There is no pixel accessor beside them to fall out of step, and the
abstract staff-space pair is what forces every element to answer in the unit the
document model is defined in.

```java
// LineElement
public abstract double getContentWidthSs();

// Clef
@Override
public double getContentWidthSs() { return CONTENT_WIDTH_SS; }
```

A caller that needs pixels converts at its own boundary, where it is also the one
choosing the rounding rule:

```java
idealSpace = (float) DocumentScale.ssToPx(endNote.getContentWidthSs()).value();
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
| `int` | `DocPx` | a whole-pixel offset the document persists as such |

The document model never holds view pixels: zoom is per-view state, and a stored
value has no view to be relative to.
