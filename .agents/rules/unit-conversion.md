## Unit Conversion: Pixels and Staff Spaces

### Authoritative Converter

`ScaleContext` is the single source of truth for pixel/staff-space conversion. It holds the mutable `pixelsPerStaffSpace` value and will support per-view zoom in the future.

```java
ScaleContext.getInstance().toPixels(valueSs)        // ss → px (double)
ScaleContext.getInstance().toRoundedPixels(valueSs)  // ss → px (int, rounded)
ScaleContext.getInstance().fromPixels(valuePx)       // px → ss
```

### Deprecated: `StaffSpaces` utility class

`songscribe.smufl.StaffSpaces` uses a hardcoded `PIXELS_PER_STAFF_SPACE` constant that does NOT read from `ScaleContext`. When zoom support is added, callers of `StaffSpaces` will produce incorrect values.

**Do not add new calls to `StaffSpaces.toPixels()` or `StaffSpaces.fromPixels()`.** When modifying code that uses `StaffSpaces`, migrate it to `ScaleContext` (or a bridge method) as part of the change.

### Conversion Direction

The goal is for all layout and rendering code to work in staff spaces. Conversion to pixels should happen as late as possible — ideally only at the rendering boundary when setting pixel coordinates on `Graphics2D`.

- Layout calculations: staff spaces
- Data model (new fields): staff spaces (`double`)
- Data model (legacy fields): pixels (`int`), migrate incrementally
- Renderers using scale transform: staff spaces (the transform handles conversion)
- Renderers not yet converted: pixels via `ScaleContext` (migrate when touched)
