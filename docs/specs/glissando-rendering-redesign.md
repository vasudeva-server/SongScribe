# Glissando Rendering Redesign

## Summary

Replace the current glyph-tiling glissando renderer with a filled rounded rectangle (pill shape) that uses `java.awt.geom.Area`-based gap calculation for precise clearance from note elements.

## Visual Design

### Shape

- A **filled rounded rectangle** (pill shape) replaces the current sequence of wiggle trill glyphs.
- **Thickness**: `legerLineThickness` from SMuFL engraving defaults (0.16 ss in Bravura).
- **Corner radius**: `thickness / 2` (fully rounded ends — semicircular caps).
- **Color**: Inherited from the current `Graphics2D` color. Do not set explicitly. The pipeline sets color to `INSERTION_NOTE_COLOR` for preview/insertion notes or black for normal notes.

### Rendering Technique

- Use `Graphics2D.rotate()` to align the rect with the tangent angle, matching the existing rotation pattern used by the current glyph-based renderer.
- Save/restore the graphics transform using try/finally per project conventions.
- Fill a horizontal `RoundRectangle2D` in the rotated coordinate system.

## Tangent Geometry

### Anchor Points

The **tangent line** is defined by two anchor points:

- **x1 (left anchor)**: The **center** of the source note's notehead.
- **x2 (right anchor)**: For `CONNECTED`, the **center** of the target note's notehead. For `SLIDE_OUT`, a fixed offset at 45 degrees down-right from x1.

The tangent angle is `atan2(y2 - y1, x2 - x1)`, where Y values are derived from each note's `staffPosition` via the existing `noteStaffPositionToCoordinateSs()` method on `BaseElementRenderer`.

### CONNECTED Glissando

The glissando rect spans from the left note's area boundary (+ gap) to the right note's area boundary (+ gap) along the tangent. The rect fills the available space between the two gap edges.

### SLIDE_OUT Glissando

- Fixed 45-degree angle, always down-right.
- Left endpoint: area boundary + 0.3 ss gap along the tangent (same as CONNECTED).
- Right endpoint: must clear whatever is to the right (next note's area if present). If the next note needs to be pushed right, the minimum spacing constraint applies (same as CONNECTED).
- If the last note on a line has a SLIDE_OUT with nothing to the right, clip the rect to the staff bounds. **TODO**: In the future, attempt to tighten spacing to the left to make the full glissando fit.

## Note Area Construction

### What "Area" Means

The note area is a `java.awt.geom.Area` — a composite geometric shape — **not** a bounding box. This allows the glissando to tuck closer to the notehead when the tangent doesn't pass through distant elements like dots.

### Area Components

The area is the **union** of these simple geometric shapes, all positioned relative to the note's glyph origin:

| Element | Shape | Source for dimensions |
|---|---|---|
| Notehead | Rotated `Ellipse2D` | See [Notehead Oval Derivation](#notehead-oval-derivation) |
| Augmentation dots | `Ellipse2D` (circles) | Positions from `NoteRenderer` constants; radius from SMuFL bbox of `AUGMENTATION_DOT` |
| Accidentals | `Rectangle2D` | Width from `NoteColumnBuilder.getAccidentalWidthSs()`; gap from `NoteColumnBuilder.ACCIDENTAL_GAP_SS`; height from SMuFL bbox |
| Ledger lines | `Rectangle2D` | Width from `getNoteheadRightEdgeSs() + 2 * legerLineExtension()`; thickness from `legerLineThickness()`; centered on notehead |
| Stem | `Rectangle2D` | Width from `LayoutConstants.STEM_WIDTH_SS`; default length 3.5 ss; anchor from `LayoutConstants.STEM_UP_SE_BLACK` / `STEM_DOWN_NW_BLACK`; direction from note's stem direction |
| Flags | `Rectangle2D` | Bounding box from `SMuFLMetadata.getBBox(flagGlyph)`; positioned at stem tip |

**Important**: Verify that all element positions can be computed relative to the note's glyph origin from the existing data. The following need verification:

- Dot Y offset (0 or -0.5 ss depending on staff position being on a line)
- Dot X offset (see `NoteRenderer.FIRST_DOT_X_SS`, `DOT_SPACING_SS` — currently private)
- Accidental Y position relative to glyph origin
- Ledger line Y positions for each ledger line
- Flag position at stem tip

### Notehead Oval Derivation

Three notehead ovals are pre-computed and cached:

1. **Quarter/filled** — used for quarter, half, eighth, sixteenth, all shorter durations, and all beamed notes.
2. **Whole** — used for whole notes (semibreves).
3. **Grace** — used for grace notes (scaled-down quarter shape).

The oval rotation angle and dimensions are derived from SMuFL anchor data:

```
anchors = SMuFLMetadata.getAnchors(noteheadGlyph)
angle = atan2(stemUpSE.y - stemDownNW.y, stemUpSE.x - stemDownNW.x)
```

The `stemUpSE` and `stemDownNW` anchors define opposite ends of the notehead's major axis. The ellipse semi-major and semi-minor axes are derived from the bounding box dimensions combined with this rotation angle.

For the **grace** oval, use the quarter/filled oval dimensions scaled by `GraceNoteRenderer.GRACE_NOTE_SCALE`.

For **whole** notes, use the anchors from `NOTEHEAD_WHOLE` (if available) or fall back to the bbox dimensions.

### Area Caching

The area is cached on the `Note` itself (not on `NoteColumn`). Since `NoteColumn` is rebuilt on every layout pass, caching there would cause unnecessary recomputation. The area depends only on note-intrinsic properties — type, accidental, dot count, staff position, and stem direction — which change rarely after a note is placed. The cache is invalidated only when one of these properties changes:

- Accidental added/removed/changed
- Dot count changed
- Stem direction changed
- Staff position changed (which affects ledger lines)

Area construction only occurs for notes that have a glissando attached — not for all notes.

## Gap Calculation

### Minimum Gap

**0.3 ss** between the glissando rect endpoint and the note area, measured **along the tangent direction**.

### Binary Search Algorithm

To find the gap edge (where the glissando rect starts/ends):

1. Start at the note center (guaranteed inside the area).
2. Walk outward along the tangent, testing `Area.contains(x, y)`.
3. Use binary search to find the **last exit point** — the farthest point from center that is still inside the area. This handles the (rare) case where the tangent exits and re-enters the area.
4. The gap edge = last exit point + 0.3 ss along the tangent.

**Tolerance**: 0.05 ss (~0.4 px at standard zoom). This is sufficient for screen rendering. **TODO**: Tighten for high-DPI rendering (e.g. print output).

### Gap Geometry

The gap is **point-based**: the glissando endpoint is treated as a single point on the tangent line. Given the rect thickness (0.16 ss), the difference between point-based and area-to-area gap measurement is negligible.

### Endpoint Clamping with User Offsets

User `x1Translate` / `x2Translate` offsets are applied after the area-based gap calculation. The endpoint is clamped so it can never enter the note area itself. The user can reduce the 0.3 ss gap to zero but cannot cause overlap with the area.

## Minimum Length and Layout Integration

### Minimum Rect Length

The visible glissando rect must be at least **1.34 ss** in length. This is measured along the tangent, not horizontally.

### Total Horizontal Reservation

The layout must reserve enough horizontal space for:

```
total = MIN_GAP (0.3) + MIN_RECT_LENGTH (1.34) + MIN_GAP (0.3) = 1.94 ss
```

This reservation is between note area horizontal extents (not NoteColumn extents — see below).

### Layout Integration in HorizontalSpacingCalculator

- Add the glissando spacing constraint directly into `HorizontalSpacingCalculator`.
- Use **worst-case horizontal** spacing: always reserve 1.94 ss regardless of the tangent angle. This is simple and avoids pitch-dependent layout. (Layout already recomputes on note changes, but angle-dependent spacing adds unnecessary complexity.)
- Process in a **single left-to-right pass**. Each glissando constraint is evaluated once. Since notes only move right, a single pass is sufficient — pushing note B right can only help (not hurt) a glissando from B to C.

### Glissando-Aware Horizontal Extents on NoteColumn

Add methods to `NoteColumn` (e.g. `getGlissandoLeftExtentSs()`, `getGlissandoRightExtentSs()`) that include ledger line overhang in the horizontal extent. The standard `leftExtentSs` / `rightExtentSs` do not account for ledger lines, but glissandos must clear them.

These methods are only called for notes involved in glissandos.

## Length Behavior

When notes are already widely spaced (e.g. due to lyrics or natural rhythm spacing), the glissando **fills the available space** between the two gap edges. The 1.34 ss minimum only applies when notes are too close.

## Unison Prevention

Glissandos between notes of the same pitch are musically invalid and are prevented:

### On Add

Do not allow adding a glissando if the next note is the same pitch.

### On Pitch Change

If a pitch drag results in a unison between two notes connected by a glissando:

- **During the drag** (intermediate positions): no warning, no removal.
- **When the drag stops on unison**: remove the glissando and show an alert:

  > "FYI, glissandos can only apply to notes on different pitches, so it was removed."

  The alert includes a **"Don't show this again"** checkbox. The preference is stored via `java.util.prefs.Preferences`.

## Preview Rendering

The existing preview behavior is preserved:

- If there is a note to the right: show a **SLIDE_OUT** preview when the mouse X is less than half the distance between the note columns; otherwise show a **CONNECTED** preview.
- If there is no note to the right: always show a **SLIDE_OUT** preview.

Preview glissandos use the same area-based rendering as committed glissandos. For CONNECTED previews, use the actual next note for area calculation. For SLIDE_OUT previews, only the source note area is needed.

## Grace Notes

Grace notes use the **same parameters** as regular notes (0.3 ss gap, 1.34 ss minimum rect length, 0.16 ss thickness). The note area will be smaller due to the smaller notehead and scaled elements, but the glissando rect itself has the same dimensions and constraints.

## Constants

All new constants are defined in `GlissandoRenderer`:

| Constant | Value | Description |
|---|---|---|
| `MIN_GAP_SS` | 0.3 | Minimum gap between rect endpoint and note area along tangent |
| `MIN_RECT_LENGTH_SS` | 1.34 | Minimum visible rect length along tangent |
| `RECT_THICKNESS_SS` | `engravingDefaults.legerLineThickness()` | Rect height (perpendicular to tangent) |
| `CORNER_RADIUS_SS` | `RECT_THICKNESS_SS / 2.0` | Fully rounded ends |
| `SEARCH_TOLERANCE_SS` | 0.05 | Binary search precision for tangent-area intersection |
| `MIN_HORIZONTAL_RESERVATION_SS` | `MIN_RECT_LENGTH_SS + 2 * MIN_GAP_SS` | Total horizontal spacing reservation (1.94 ss) |

## DRY Requirements

**No duplication of code is acceptable.** The implementation must:

1. **Reuse existing geometry sources** — all element dimensions and positions must be derived from the existing SMuFL metadata API (`SMuFLMetadata.getBBox()`, `.getAnchors()`, `.getAdvanceWidth()`, `.getEngravingDefaults()`) and existing constants (`LayoutConstants`, `NoteColumnBuilder`, `NoteRenderer`).

2. **Extract shared constants** — where constants are currently duplicated (e.g. `STEM_LENGTH_SS = 3.5` exists independently in `NoteColumnBuilder`, `NoteRenderer`, `NoteType`, and as a literal in `BeamGroupRenderer`), extract to a single source of truth before referencing from the glissando code.

3. **Expose currently-private values** — where needed values are private (e.g. `NoteRenderer.FIRST_DOT_X_SS`, `DOT_SPACING_SS`; `NoteColumnBuilder.DOT_WIDTH_SS`, `DOT_GAP_SS`), widen their visibility or extract them to an appropriate shared location rather than re-declaring them.

4. **No null fallbacks for critical objects** — if an `Area`, anchor, bbox, or other geometry object is null, that is a programming error and must fail fast (assertion or exception), not silently degrade.

5. **Single `noteStaffPositionToCoordinateSs` implementation** — this method is currently duplicated in `BaseElementRenderer`, `GlissandoRenderer`, and `NoteRenderer`. Consolidate to a single location.

### Known Duplications to Resolve

| Value | Current Locations | Action |
|---|---|---|
| `STEM_LENGTH_SS = 3.5` | `NoteColumnBuilder`, `NoteRenderer`, `NoteType`, `BeamGroupRenderer` (literal), `BravuraFontBoundsProvider` | Extract to `LayoutConstants` |
| `noteStaffPositionToCoordinateSs` | `BaseElementRenderer`, `GlissandoRenderer`, `NoteRenderer` | Consolidate to `BaseElementRenderer` (or a utility) |
| Accidental width computation | `NoteRenderer`, `NoteColumnBuilder` | Evaluate whether one can delegate to the other |
| Dot position constants | `NoteRenderer` (private), `NoteColumnBuilder` (private) | Extract to shared location as needed |

## Files Affected

### Modified

| File | Changes |
|---|---|
| `GlissandoRenderer.java` | Complete rewrite: rounded rect rendering, area-based gap calculation, binary search, notehead oval cache |
| `HorizontalSpacingCalculator.java` | Add glissando minimum spacing constraint (1.94 ss worst-case) |
| `NoteColumn.java` | Add `getGlissandoLeftExtentSs()`, `getGlissandoRightExtentSs()` (ledger-line-aware) |
| `NoteColumnBuilder.java` | Compute and set glissando-aware extents; expose dot/accidental constants if private |
| `LayoutConstants.java` | Add `STEM_LENGTH_SS` (consolidated from 4 locations) |
| `NoteRenderer.java` | Remove duplicate `STEM_LENGTH_SS`; expose dot position constants; remove duplicate `noteStaffPositionToCoordinateSs` |
| `NoteType.java` | Remove duplicate `STEM_LENGTH_SS` |
| `BeamGroupRenderer.java` | Replace literal `3.5` with `LayoutConstants.STEM_LENGTH_SS` |
| `BravuraFontBoundsProvider.java` | Replace duplicate `STEM_LENGTH_STAFF_SPACES = 3.5` with `LayoutConstants.STEM_LENGTH_SS` |
| `BaseElementRenderer.java` | Own the canonical `noteStaffPositionToCoordinateSs` (make `public static`) |
| `HorizontalAdjustment.java` | Update to use new anchor point API (note center instead of column edge) |

### Behavioral Changes

| File | Changes |
|---|---|
| Pitch drag handler | Add unison detection + glissando removal + alert |
| Glissando add handler | Prevent adding on unison pitch |
