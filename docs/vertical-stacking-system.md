# Vertical Stacking Layout System

## Coordinate System

All layout calculations use staff-space units with **Y-down orientation** (smaller Y = higher on page). The origin is **middleLineY = 0** (the B4 line). Key reference points:

- `STAFF_POSITION_OFFSET_SS` = 0.5 — half a staff space per staff position
- `TOP_STAFF_LINE_POSITION` = -4 — staff position of the top line (F5)
- `STAFF_TOP_Y_SS` = -2.0 — Y coordinate of the top staff line (`-4 * 0.5`)
- `STAFF_HEIGHT_SS` = 4.0 — total height of the 5-line staff

A note's center Y is `staffPosition * STAFF_POSITION_OFFSET_SS`. Negative = above middle line, positive = below.

## Architecture

`VerticalStackingCalculator` (in `songscribe.ui.layout`) positions all above-staff decorations using a three-layer `StaffExtents` collision detection model.

### StaffExtents

`StaffExtents` divides the horizontal span of a staff line into `YSTEP` (128) equal steps. Two arrays track the highest occupied Y (`top[]`, initialized to 0.0) and lowest occupied Y (`bot[]`, initialized to `STAFF_HEIGHT_SS`) at each step.

- `ySet(above, xSs, widthSs, ySs)` — reserves space. Converts x/width to step range via `xToStep()` (clamped to `[0, YSTEP-1]`). For above: `top[i] = Math.min(top[i], ySs)`. For below: `bot[i] = Math.max(bot[i], ySs)`.
- `yGet(above, xSs, widthSs)` — queries the extreme Y across a step range. For above: returns the minimum (highest point). For below: returns the maximum (lowest point).
- `copyTopFrom(source)` — copies `top[]` from another instance. Used when initializing a higher tier from a lower tier.
- `xToStep(xSs)` — `(int)(xSs * YSTEP / lineWidthSs)`, clamped.

### Three Layers

1. **Note-attached** (`noteAttachedExtents`): articulations, fermata, trill
2. **Structural** (`structuralExtents`): hairpins (crescendo/diminuendo), text dynamics, volta brackets
3. **System** (`systemExtents`): tempo, beat changes, annotations

Each layer imports the previous layer's top extents via `copyTopFrom()`, so higher layers automatically clear lower-layer elements.

### Stacking Flow (in `calculate()`)

1. `seedNoteBounds()` — reserves notehead+stem space in the note-attached layer
2. Per-column: `stackArticulations()` → `stackFermata()`
3. Per-line: `stackTrills()` (including legacy bridging)
4. Copy note-attached → structural
5. Per-line: `stackHairpins()` (including legacy bridging)
6. Per-column: `stackTextDynamics()`
7. Per-line: `stackEndings()` (including legacy bridging)
8. Copy structural → system
9. Per-column: `stackTempo()` → `stackBeatChange()` → `stackAnnotations()`
10. `applyManualOffsets()` — post-layout, no collision re-run
11. Calculate line height and lyrics baseline

## Key Methods

### `stackAbove()` — the single stacking method

All decorations use this (including hairpins via `stackSpanElement`). It applies **anchored ceiling** logic via `anchoredCeilingSs()`, then updates extents and writes a `DecorationLayout`.

```
ceilingSs = anchoredCeilingSs(extents, xSs, widthSs, staffPosition, noteHeadYSs)
ySs = ceilingSs - marginSs - heightSs
extents.ySet(true, xSs, widthSs, ySs)
builder.putDecorationLayout(element, DecorationLayout(xSs, ySs, widthSs, heightSs))
return ySs
```

Parameters: `extents, element, xSs, widthSs, heightSs, marginSs, staffPosition, noteHeadYSs, builder`. Returns the computed top Y.

There is no unanchored variant — all elements anchor to the staff.

### `anchoredCeilingSs()` — ceiling computation

```java
currentTopSs = extents.yGet(true, xSs, widthSs)
if (staffPosition > TOP_STAFF_LINE_POSITION)  // note is within or below staff
    anchorSs = STAFF_TOP_Y_SS                  // anchor to top staff line (-2.0)
else                                           // note is at or above staff
    anchorSs = noteHeadYSs - NOTE_HEAD_RADIUS_SS  // anchor to notehead top
return Math.min(currentTopSs, anchorSs)        // take the higher (more negative) of the two
```

This ensures decorations never appear *within* the staff for notes that are within or below the staff. For hairpins with `HAIRPIN_MARGIN_SS` (1.0) + `HAIRPIN_OPENING_HEIGHT_SS` (1.0), this naturally produces 2.0 ss (16px) above the staff top when no notes protrude above the staff.

### `seedNoteBounds()`

For each column, reserves the vertical extent of the note (notehead + stem) in the note-attached layer:

- If `StemLayout` exists (from the beam/stem pass): uses `stemLayout.topYSs()` / `stemLayout.bottomYSs()`
- Otherwise: computes from `ElementType.getTopYOffsetSs(upper)` / `getElementHeightSs(upper)`
- Both paths also consider `noteheadTopSs` / `noteheadBotSs` from `ElementType.getNoteheadTopOffsetSs()` / `getNoteheadHeightSs()`
- Reserves with width `NOTE_HEAD_WIDTH_SS` (from SMuFL `noteheadBlack` bbox)

**Important**: note bounds are seeded only at each note's X position (notehead width). Steps between notes retain the default `top[]=0.0`. The anchored ceiling logic handles this — for notes within the staff, the ceiling is clamped to `STAFF_TOP_Y_SS` regardless of what `top[]` contains.

### `stackSpanElement()` — for range elements requiring both endpoints

Used by hairpins and endings. Resolves anchor/end columns from `columnsByElement` map, computes span width via `element.getSpanWidthSs(anchorXSs, endXSs)`, gets staff position from anchor note, then delegates to `stackAbove()`.

### `stackSingleTrill()` — trills with lenient end handling

Unlike `stackSpanElement()`, trills allow a missing or same-as-anchor end note (single-note trill), defaulting endX to the anchor X.

### `isRangeCovered()` — unified coverage check

`static boolean isRangeCovered(startNote, endNote, List<? extends RangeElement>)`. Checks if any existing range element has the same anchor and end. Used to avoid double-stacking when both new range elements and legacy flags exist for the same span.

## Margin Constants

All in `LayoutStylesheet`:

- `NOTE_DECORATION_MARGIN_SS` (0.5) — articulations, fermata, trill, text dynamics
- `HAIRPIN_MARGIN_SS` (1.0) — crescendo/diminuendo hairpins
- `ENDING_MARGIN_SS` (0.75) — volta brackets
- `TEMPO_MARGIN_SS` (1.0) — tempo markings
- `BEAT_CHANGE_MARGIN_SS` (1.0) — beat/time signature changes
- `ANNOTATION_ABOVE_MARGIN_SS` (0.5) — text annotations

Hairpins use a larger margin than other note decorations because the anchored ceiling + margin + height naturally determines the minimum distance above the staff (margin 1.0 + height 1.0 = 2.0 ss above the staff top for notes within the staff).

## Manual Offsets (`applyManualOffsets()`)

Applied after all collision detection. Two passes:

### `applyDecorationOffsets()`

Iterates all `DecorationLayout` entries. For each, collects offsets:

- **Base offsets** (all elements): `element.getUserXOffsetSs()`, `element.getUserYOffsetSs()`
- **Trill**: adds `trill.getYPositionSs()` to Y
- **Ending**: adds `ending.getYPositionSs()` to Y
- **Crescendo/Diminuendo**: converts pixel-based `x1Shift`, `x2Shift`, `yShift` via `ScaleContext.fromPixels()`. X offset = x1Shift, width adjustment = x2Shift - x1Shift.
- **AnnotationAttachment**: adds `annotation.getUserYOffsetSs()` to Y

If any offset is non-zero, writes a new `DecorationLayout` with adjusted values.

### `applySpanOffsets()`

Iterates all `SpanLayout` entries. For `DynamicsInterval`: applies `x1ShiftSs`, `x2ShiftSs`, `yShiftSs` (already in staff spaces).

## Legacy Bridging

The stacking calculator bridges old `StaffElement` flags to new layout types during layout. Each stacking method checks the new attachment hierarchy first, only bridging if no attachment exists:

- `note.isFermata()` → temporary `FermataAttachment`
- `note.isTrill()` → temporary `Trill` (consecutive sequences grouped by `bridgeLegacyTrillFlags`)
- `note.getTempoChange()` → temporary `TempoAttachment`
- `note.getBeatChange()` → temporary `BeatChangeAttachment`
- `note.getAnnotation()` → temporary `AnnotationAttachment`
- `DynamicsInterval` (from `line.getCrescendos()`/`getDiminuendos()`) → temporary `Crescendo`/`Diminuendo`
- `EndingInterval` (from `line.getFirstSecondEndings()`) → temporary `Ending`

All bridged objects are stored as keys in `LayoutResult.decorationLayouts`, making them accessible to renderers via `getDecorationLayoutsByType()`.

For legacy hairpins and endings, a `SpanLayout` keyed by the interval is also written (used by `EndingRenderer` which still iterates legacy intervals).

## Rendering Pipeline

### LayoutResult records

- `DecorationLayout(xSs, ySs, widthSs, heightSs)` — positioned bounds of a decoration, keyed by `LineElement`
- `SpanLayout(startXSs, endXSs, ySs, heightSs)` — positioned bounds of a span, keyed by `Interval`

### `getDecorationLayoutsByType(Class<T>)`

Returns all `DecorationLayout` entries whose key is an instance of the given type. This is how renderers iterate all elements of a type in a single pass (both new and bridged).

### Layout → Renderer coordinate conversion (on `BaseElementRenderer`)

- `layoutYToComponentYSs(layoutYSs, ctx)` — adds `ctx.getMiddleLineYSs()` to convert from layout space (middleLineY=0) to component space.
- `centeredGlyphX(g2, layoutXSs, note, glyphWidthSs)` — computes `layoutXSs + noteCenterXSs - glyphWidthSs/2`, snapped to device pixels. Centers a glyph over the notehead.
- `glyphOriginYFromLayoutTop(layoutTopYSs, glyph)` — converts a layout top Y to the glyph origin Y for drawing. Uses `layoutTopYSs - bbox.top()` (not `+ height()`) to correctly handle glyphs whose origin differs from their top edge.

### Renderer dispatch (in `LineRenderer`)

**Per-element** (`renderAttachments()` loop over `line.elementCount()`):
- Articulations: guarded by `!element.getArticulations().isEmpty()`
- Fermata: guarded by `findAttachmentDecorationLayout(element, FermataAttachment.class) != null`
- Tempo: guarded by `findAttachmentDecorationLayout(element, TempoAttachment.class) != null`
- BeatChange: guarded by `findAttachmentDecorationLayout(element, BeatChangeAttachment.class) != null`
- Annotation: guarded by `findAttachmentDecorationLayout(element, AnnotationAttachment.class) != null`

**Per-line** (separate calls after the element loop):
- Trills: `TrillRenderer.renderTrillsFromLine(g2, ctx)` — iterates `getDecorationLayoutsByType(Trill.class)`
- Hairpins: `DynamicsRenderer.renderHairpinsFromLine(g2, ctx)` — iterates `getDecorationLayoutsByType(Crescendo.class)` and `Diminuendo.class`
- Endings: `EndingRenderer.renderEndings(g2, line, lineIndex, ctx)` — still iterates `line.getFirstSecondEndings()` (legacy intervals), looks up Y from `SpanLayout` or `DecorationLayout`

### Trill rendering details

- **Single-note**: glyph centered over notehead via `centeredGlyphX()`
- **Multi-note**: glyph left-aligned with notehead, wavy extension drawn from glyph right edge to `layoutResult.getElementXSs(endNote) + NOTE_HEAD_WIDTH_SS`

### Hairpin rendering details

`DynamicsRenderer.renderSingleHairpin()` draws two lines (`Line2D.Double`) from `layout.xSs()` to `layout.xSs() + layout.widthSs()`. The stroke width is in staff-space units (`hairpinThickness` from SMuFL engraving defaults, NOT converted to pixels — the Graphics2D scale transform handles the conversion). All manual offsets are pre-applied in the `DecorationLayout`.

### Insertion note preview

For the insertion note preview (no `LayoutResult` available):
- `ArticulationRenderer` and `FermataRenderer` call `VerticalStackingCalculator.computePreviewDecorationLayouts(note, xSs)` which creates a minimal `StaffExtents` and runs the same stacking logic (articulations then fermata).
- The precise X position comes from `ctx.getOverrideElementXSs()` (set by `LineRenderer.renderInsertionElement()`), not from `element.getXPosSs()` which is a rounded integer. The override remains set during all decoration rendering, cleared afterward.
- These renderers use `preserveColor=true` when calling `drawBravuraGlyph()` so the caller controls the color.
- `LineRenderer.renderInsertionElement()` sets the color to `Score.getInsertionElementColor()` before calling any renderers.

### `RangeElement` abstract methods

`getContentHeightSs()` and `getSpanWidthSs(anchorXSs, endXSs)` are abstract on `RangeElement`, implemented by all 6 subclasses (Crescendo, Diminuendo, Trill, Ending, Tie, Tuplet). This enables `stackSpanElement()` to work uniformly with any range element type.

## Template Code

### Single-note decoration renderer (e.g. FermataRenderer)

Per-element renderer dispatched from `LineRenderer.renderAttachments()`. Looks up its `DecorationLayout` via the attachment type, converts coordinates, centers glyph, draws.

```java
@Override
protected void renderElement(StaffElement element, Graphics2D g2, ElementRenderContext ctx) {
    // Guard: check legacy flag (will be removed in Phase 6)
    if (!element.isFermata()) {
        return;
    }

    var layoutResult = ctx.getLayoutResult();

    if (layoutResult == null) {
        // Insertion note preview: compute layouts on the fly.
        // Use override X for precise positioning, falling back to xPosSs.
        double xSs = ctx.hasOverrideElementX()
            ? ctx.getOverrideElementXSs() : element.getXPosSs();
        layoutResult = VerticalStackingCalculator.computePreviewDecorationLayouts(element, xSs);
    }

    var decorationLayout = layoutResult.findAttachmentDecorationLayout(
        element, FermataAttachment.class);

    if (decorationLayout == null) {
        return;
    }

    // Convert layout Y (middleLineY=0) to component Y
    double topYSs = layoutYToComponentYSs(decorationLayout.ySs(), ctx);

    // Center glyph over notehead, snap to device pixels
    double x = centeredGlyphX(g2, decorationLayout.xSs(), element, FERMATA_WIDTH_SS);

    // Convert layout top to glyph origin Y (accounts for bbox.top offset)
    double y = glyphOriginYFromLayoutTop(topYSs, SMuFLGlyph.FERMATA_ABOVE);

    // preserveColor=true — caller controls color (important for insertion preview)
    drawBravuraGlyph(g2, SMuFLGlyph.FERMATA_ABOVE, x, y, true);
}
```

### Range decoration renderer (e.g. TrillRenderer)

Line-level renderer called from `LineRenderer.renderAttachments()`. Iterates all elements of its type via `getDecorationLayoutsByType()`, handles single-note (centered) vs. multi-note (left-aligned + extension) variants.

```java
// Called from LineRenderer: TrillRenderer.getInstance().renderTrillsFromLine(g2, ctx)
public void renderTrillsFromLine(Graphics2D g2, ElementRenderContext ctx) {
    var layoutResult = ctx.getLayoutResult();

    if (layoutResult == null) {
        return;
    }

    // Single unified loop — includes both new range elements and bridged legacy flags
    for (var entry : layoutResult.getDecorationLayoutsByType(Trill.class)) {
        var trill = entry.getKey();
        var layout = entry.getValue();
        var anchor = trill.getAnchorElement();

        if (anchor == null) {
            continue;
        }

        double layoutXSs = layout.xSs();
        double trillTopYSs = layoutYToComponentYSs(layout.ySs(), ctx);
        var endNote = trill.getEndElement();

        if (endNote != null && endNote != anchor) {
            // Multi-note: left-align glyph with notehead
            double trillXSs = GraphicUtils.snapXToDevicePixel(g2, layoutXSs);
            // End X from layout (precise), not from endNote.getXPosSs() (rounded int)
            double endXSs = layoutResult.getElementXSs(endNote) + NOTE_HEAD_WIDTH_SS;
            renderTrill(g2, trillXSs, endXSs, trillTopYSs);
        } else {
            // Single-note: center glyph over notehead
            double trillXSs = centeredGlyphX(g2, layoutXSs, anchor, TRILL_ADVANCE_WIDTH_SS);
            renderTrill(g2, trillXSs, Double.NaN, trillTopYSs);
        }
    }
}
```

### Hairpin renderer (DynamicsRenderer)

Line-level renderer. Iterates crescendo and diminuendo layouts, draws two lines forming the hairpin wedge. Stroke width is in staff spaces (not pixels).

```java
public void renderHairpinsFromLine(Graphics2D g2, ElementRenderContext ctx) {
    var layoutResult = ctx.getLayoutResult();
    if (layoutResult == null) return;

    for (var entry : layoutResult.getDecorationLayoutsByType(Crescendo.class)) {
        renderSingleHairpin(entry.getValue(), true, g2, ctx);
    }
    for (var entry : layoutResult.getDecorationLayoutsByType(Diminuendo.class)) {
        renderSingleHairpin(entry.getValue(), false, g2, ctx);
    }
}

private void renderSingleHairpin(DecorationLayout layout, boolean isCrescendo,
                                  Graphics2D g2, ElementRenderContext ctx) {
    double x1 = layout.xSs();
    double x2 = x1 + layout.widthSs();
    double topYSs = layoutYToComponentYSs(layout.ySs(), ctx);
    double bottomYSs = topYSs + layout.heightSs();
    double middleYSs = topYSs + layout.heightSs() / 2.0;

    // Stroke in staff spaces — scale transform converts to pixels
    g2.setStroke(LINE_STROKE);
    g2.setColor(ELEMENT_COLOR);

    if (isCrescendo) {
        g2.draw(new Line2D.Double(x1, middleYSs, x2, topYSs));
        g2.draw(new Line2D.Double(x1, middleYSs, x2, bottomYSs));
    } else {
        g2.draw(new Line2D.Double(x1, topYSs, x2, middleYSs));
        g2.draw(new Line2D.Double(x1, bottomYSs, x2, middleYSs));
    }
}
```

## Common Pitfalls

1. **All layout constants are in `LayoutStylesheet`** — the old `LayoutConstants` was deleted after merging. Using the wrong constant (with a similar name but different value) caused bugs.
2. **All decorations use anchored ceiling** — there is no unanchored stacking path. Forgetting to pass `staffPosition` and `noteHeadYSs` will cause a compile error. Hairpins use `stackSpanElement` which resolves the anchor note's staff position internally.
3. **Hairpin stroke in staff spaces** — `LINE_STROKE` width is `hairpinThickness` from SMuFL engraving defaults (already in ss). Do NOT convert to pixels — the Graphics2D scale transform handles it. Using `toPixels()` would make lines ~8x too thick.
4. **Hairpin coordinates from DecorationLayout** — `xSs` and `xSs + widthSs` define the hairpin span. Do NOT read X positions from `startNote.getXPosSs()` or `endNote.getXPosSs()` — those are rounded integers that cause position drift.
5. **Insertion note X precision** — use `ctx.getOverrideElementXSs()` for the precise double X, not `element.getXPosSs()` which rounds to int and causes position drift.
6. **Color in decoration renderers** — renderers that can appear on the insertion note preview must use `preserveColor=true` so the caller controls the color.
7. **Manual offsets are pre-applied** — renderers that iterate `DecorationLayout` entries should NOT apply additional shifts from legacy interval fields. The `applyDecorationOffsets()` pass already incorporated them.
8. **`getDecorationLayoutsByType()` returns both new and bridged** — no need for separate iteration of legacy flags and new range elements.
9. **Note bounds are sparse** — `seedNoteBounds` only reserves space at each note's X position. Steps between notes retain the default `top[]=0.0`. The anchored ceiling in `anchoredCeilingSs` handles this by clamping to at least `STAFF_TOP_Y_SS` for notes within the staff.
