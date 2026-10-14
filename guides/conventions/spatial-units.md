# Spatial Units

Terms are in [Spatial Units concepts](../concepts/spatial-units.md). Zoom behavior is in
[Zoom](../subsystems/zoom.md).

## Rules

- Give every spatial value (field, parameter, local, method, constant) a unit suffix: `Ss`,
  `DocPx`, `ViewPx` or `Sp`. A spatial value with no suffix is a bug.
- Never use a bare `Px` suffix, because it names a unit but not the regime, and the two pixel
  regimes differ by whatever the view is zoomed to. A value typed `DocPx` or `ViewPx` needs no
  suffix.
- Give counts, ratios and indices no suffix.
- Hold and compute spatial values in staff spaces. Convert to pixels only when producing a size
  or position for a Swing component or the print system, and when reading pixel input back from
  the toolkit (mouse points, font metrics).
- Measure text through `TextMeasurement`, never with an instrument of your own, because a run
  measured under another render context ends somewhere else than where the paint pass draws it.
- Read the size of the font before converting a text measurement, and suffix the result for the
  unit that came back, because converting an answer that is already in staff spaces scales it
  twice.
- Measure off a live `Graphics2D` only to draw with the answer in that same paint pass. Never
  store such a measurement or let anything derived from it reach layout, because it is in view
  pixels at one zoom.
- Never convert to pixels inside a renderer, because the paint transform is already applied and
  the conversion double-scales. Layout code outside the render path does convert.
- Round a size up with `sizePx()` and a position to nearest with `positionPx()`.
- State a layout element's content dimensions in staff spaces only, with no pixel accessor beside
  them:

  ```java
  public abstract double getContentWidthSs();
  ```

- Convert at the caller's boundary, where the caller chooses the rounding rule.
- Never store a pixel field that duplicates a staff-space field, because the two drift and
  nothing reports it.
- Never use the typed unit records (`Ss`, `DocPx`, `ViewPx`) inside the per-frame paint path.
  Unwrap a typed value from a measurement helper at once, because the wrapper costs an
  allocation on every frame and buys no correctness there.
- Type a spatial field of the document model `double` with the `Ss` suffix unless it is one of
  the following, and never hold view pixels in it, because zoom is per-view state:
  - a discrete pitch-grid step is a `StaffPosition`. The `Sp` suffix is for a raw count of
    half-space steps that is not itself a position, such as a shift delta;
  - a whole-pixel offset the document persists as such is an `int` with the `DocPx` suffix.
