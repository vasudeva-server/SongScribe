# Rendering and Layout System Rewrite

## Overview

Rewrite SongScribe's rendering and layout pipeline, porting algorithms from
abc2svg (LGPL v3, by Jean-Francois Moine) to replace the current mix of legacy
code and Gould/Ross rules. The rewrite normalizes the coordinate system to
staff-space units, replaces the collision detection model, and restructures the
renderer architecture. This enables zoom support and future SVG/image export.

**Source:** https://github.com/moinejf/abc2svg (core/ directory)
**License:** LGPL v3, compatible with SongScribe's GPL v3. Direct algorithmic
port with attribution in source comments. Variable and method names adapted to
SongScribe code style conventions.

---

## Goals

1. **Staff-space coordinate system** throughout all layout and rendering code,
   with pixel conversion only at the render boundary
2. **abc2svg-based algorithms** for beams, ties, vertical stacking, lyrics, and
   all above-staff elements
3. **y-extent array collision detection** replacing Java2D Area intersection
4. **Three-layer vertical stacking** (note-attached, structural, system) with
   all elements above the staff (vocal music context)
5. **Self-rendering elements** replacing the RendererRegistry/singleton pattern
6. **Immutable LayoutResult** as the single source of computed rendering geometry
7. **Zoom integration points** defined for the separate zoom spec
8. **Lyric collision avoidance** with syllable widths influencing note spacing

---

## What Is Preserved

- Bravura/SMuFL glyph rendering (noteheads, rests, clefs, key signatures,
  accidentals, flags, staff lines, barlines, breath marks, grace notes)
- Non-proportional note spacing philosophy (but with increased default gap and
  lyric-driven width expansion)
- The SMuFL metadata infrastructure (SMuFLMetadata, SMuFLGlyph, BBox,
  GlyphAnchors, EngravingDefaults, StaffSpaces)
- The music model classes (Composition, Line, Note, NoteType)
- The typed IntervalSet system (Interval subclasses from the develop branch)

## What Is Rewritten

- Beam algorithm and rendering
- Tie algorithm and rendering
- Vertical stacking / collision detection
- Lyrics rendering with collision avoidance
- All above-staff element positioning: articulations, fermata, trill, tuplets,
  dynamics hairpins, text dynamics, endings, tempo, beat changes, annotations
- Glissando rendering (note-to-note connector pass)
- Horizontal spacing (lyric width influence)
- Line justification
- The coordinate system (staff-space units throughout)
- The renderer architecture (eliminate RendererRegistry)
- The LayoutResult structure (expanded to hold all computed geometry)

---

## Coordinate System

### Current State

Three independent pixel constants all equal to 8:
- `PIXELS_PER_STAFF_SPACE` in StaffSpaces.java
- `STAFF_SPACE` in LayoutStylesheet.java
- `STAFF_LINE_SPACING` in LayoutConstants.java

Layout and rendering code mixes pixel values throughout (NOTE_Y_OFFSET=4,
beam thicknesses in pixels, tie arc heights in pixels, etc.).

### Target State

All layout and algorithm code operates in **staff-space units** (1 staff space =
distance between adjacent staff lines). Conversion to pixels happens at the
render boundary via a single scale factor.

**One staff space** is the base unit. Half staff-spaces (0.5 ss) are used for
pitch positions, beam snapping, etc.

**Scale factor:** A single mutable `pixelsPerStaffSpace` value (default 8.0)
controls the conversion. This is the integration point for zoom — changing this
value scales everything.

**Render boundary:** The `Graphics2D` transform is set once at the top of
`LineComponent.paintComponent()` or equivalent, applying the staff-space-to-pixel
scale. All drawing code downstream uses staff-space coordinates.

### abc2svg Constants (in staff-space units)

These constants are ported directly from abc2svg and expressed in staff spaces:

| Constant | Value | abc2svg source |
|---|---|---|
| BEAM_DEPTH | 0.4 ss | `BEAM_DEPTH = 3.2` (abc2svg units / 8) |
| BEAM_SHIFT | 0.625 ss | `BEAM_SHIFT = 5` |
| BEAM_STUB | 1.0 ss | `BEAM_STUB = 8` |
| BEAM_SLOPE_MAX | 0.4 | `BEAM_SLOPE = 0.4` (dimensionless) |
| TIE_ALFA | 0.3 | Control point factor (dimensionless) |
| TIE_BETA | 0.45 | Tangent factor (dimensionless) |
| TIE_HEIGHT_SHORT | 0.08 | Height scale for spans > 3 notes |
| TIE_HEIGHT_LONG | 0.03 | Height scale for spans <= 3 notes |
| TIE_BASE_HEIGHT | 1.5 ss | `12 / 8` |
| TIE_MAX_HEIGHT | 5.0 ss | `40 / 8` |
| VOLTA_TICK_HEIGHT | 2.5 ss | `20 / 8` |
| VOLTA_MARGIN | 0.625 ss | `5 / 8` |

### Zoom Integration Points

The zoom spec (docs/specs/zoom.md) identifies the need for a single source of
truth for pixels-per-staff-space. This rewrite provides it:

1. `pixelsPerStaffSpace` is the single mutable scale factor
2. All layout code produces coordinates in staff-space units
3. LayoutResult stores geometry in staff-space units
4. The render boundary applies `pixelsPerStaffSpace` as a Graphics2D scale
5. Mouse coordinate inverse mapping: `staffSpaceX = pixelX / pixelsPerStaffSpace`

Zoom becomes: change `pixelsPerStaffSpace`, invalidate layout, repaint.

---

## Layout Pipeline

The pipeline is reorganized to match abc2svg's processing order, where spacing
and decoration placement are interleaved.

### New Pipeline Order

```
1. Build note columns from Line
2. Calculate lyric syllable widths (ly_width)
3. Calculate horizontal spacing (duration-independent, non-proportional)
   - Apply lyric width influence via ly_width overflow walk (see below)
   - Apply accidental clearance
   - Apply beam group internal spacing with per-note lyric influence
4. Line justification (compress if overflow, stretch if underfull; see below)
5. Calculate beam geometry (slope, stem lengths, beam Y)
6. Calculate stem directions (automatic from pitch, with manual overrides)
7. Near-decoration placement (articulations: staccato, accent)
   - y_set reservations in note-attached layer
8. Note-decoration placement (fermata, trill)
   - y_set reservations in note-attached layer
9. Staff-decoration placement (dynamics hairpins, text dynamics, volta brackets)
   - y_set reservations in structural layer
   - Note-attached elements may intrude into this layer
10. System-decoration placement (tempo, beat changes, annotations)
    - y_set reservations in system layer
11. Lyrics placement below staff
12. Calculate total line height
13. Build immutable LayoutResult
```

### LayoutResult Expansion

LayoutResult becomes the single source of all computed rendering geometry.
Model objects (Note, Interval) remain pure data — no mutable rendering state.

```java
public final class LayoutResult {
    // Existing
    Map<Note, NoteColumn> noteColumns;
    double lineHeight, staffTopY, staffBottomY, lyricBaselineY;

    // New: beam geometry
    Map<Interval, BeamLayout> beamLayouts;

    // New: tie geometry
    Map<Interval, TieLayout> tieLayouts;

    // New: above-staff element positions
    Map<Note, List<DecorationLayout>> noteDecorations;

    // New: span element positions
    Map<Interval, SpanLayout> spanLayouts;  // tuplets, dynamics, endings

    // New: lyrics positions
    Map<Note, LyricLayout> lyricLayouts;

    // New: tempo/annotation positions
    Map<Note, List<SystemLayout>> systemDecorations;
}

record BeamLayout(
    double slope,
    double startY,
    boolean stemsUp,
    Map<Note, StemLayout> stems
) {}

record TieLayout(
    double startX, double startY,
    double endX, double endY,
    double cp1X, double cp1Y,  // cubic Bezier control point 1
    double cp2X, double cp2Y,  // cubic Bezier control point 2
    double innerCp1X, double innerCp1Y,
    double innerCp2X, double innerCp2Y
) {}

record StemLayout(
    double topY, double bottomY,
    double lengthening
) {}
```

All coordinates in staff-space units.

---

## Horizontal Spacing Algorithm

### NoteColumn Preservation

The existing `NoteColumn` class is the SongScribe equivalent of abc2svg's implicit
"sequence group" (symbols sharing a `seqst` flag in the time-sorted chain). For
single-voice music, the mapping is 1:1. `NoteColumn` is preserved as-is — its
fields (`leftExtent`, `rightExtent`, `x`, `syllableWidth`) already map directly
to abc2svg's `s.wl`, `s.wr`, `s.x`, and the lyric width computed in `ly_width()`.

Changes are to the **algorithms that consume** NoteColumns, not to the data
structure itself.

### Lyric Overflow Walk (ported from abc2svg `ly_width()`)

The current implementation uses a simple pairwise formula:
`prevHalfSyllableWidth + gap + currHalfSyllableWidth`. This over-allocates space
when a long syllable is followed by notes without lyrics, because those
intermediate notes could visually share the syllable's space.

The abc2svg approach walks forward through subsequent notes to determine the
actual right-side extension needed:

```
// For each note with a syllable:
shift = (textWidth + 2 * spaceWidth) * 0.4
if (shift > 2.5 ss) shift = 2.5 ss

// Left extent: max(existing leftExtent, shift)
leftExtent = max(leftExtent, shift)

// Right overflow: start with remaining width after the centering shift
overflow = (textWidth + 2 * spaceWidth) - shift

// Walk forward through subsequent notes:
for each subsequent note k:
    if k has its own syllable:
        break  // stop — next syllable claims its own space
    if k has a hyphen or extender:
        overflow -= shift
    else:
        overflow -= defaultNoteWidth  // ~1.125 ss (9 / 8)
    if overflow <= 0:
        break  // syllable fits within existing spacing

// Only extend rightExtent if overflow remains:
rightExtent = max(rightExtent, overflow)
```

This walk is performed during NoteColumn construction (step 2 of the pipeline).
The resulting `leftExtent` and `rightExtent` values feed directly into the
horizontal spacing calculator, which uses them for gap computation as before.

### Beam Group Lyric Distribution

Within beam groups, abc2svg applies `ly_width()` per-note (with a -10% spacing
modifier for beamed notes). The current SongScribe approach calculates tight
internal spacing first (ignoring lyrics), then distributes any lyric-driven
expansion evenly across all internal gaps.

The new approach applies the lyric overflow walk per-note within beam groups,
so each note's `rightExtent` already reflects its syllable requirements. The
beam group spacing algorithm then:

1. Computes tight internal spacing using `rightExtent + gap + leftExtent`
   for each adjacent pair (this now includes lyric-driven extents)
2. Applies a -10% modifier to the gap between beamed notes (matching abc2svg's
   `!s.beam_st` check) to keep beam groups visually cohesive
3. No separate lyric distribution pass is needed — the per-note extents handle it

### Line Justification (compress and stretch)

The current `LineJustificationCalculator` only compresses. The new implementation
supports both directions, matching abc2svg's `set_sym_glue()`:

**Compression (line overflows staff width):**

```
compressionRatio = targetWidth / naturalWidth
```

Applied uniformly to all inter-column gaps. Compression is bounded by a minimum
gap (`COMPRESSED_MIN_COLUMN_GAP`) and minimum syllable gap
(`COMPRESSED_MIN_SYLLABLE_GAP`). If compression would violate these minimums,
the line reports an overflow error (too many notes to fit).

**Stretch (line underfills staff width):**

```
stretchRatio = targetWidth / naturalWidth
```

Applied uniformly to all inter-column gaps, capped at 1.8x the natural spacing
(matching abc2svg's `BETA` upper bound). This prevents excessively sparse lines
when a line has very few notes. Lines that would require more than 1.8x stretch
are left at their natural spacing (not justified).

Stretch justification is applied only to the last line of a composition or lines
explicitly marked for justification. Interior lines that underfill are always
stretched (they result from line breaks the user placed, so the intent is to
fill the staff width).

---

## Collision Detection: Y-Extent Arrays

### Overview

Replace Java2D `Area` intersection testing with segmented y-extent arrays,
matching abc2svg's `y_get`/`y_set` approach.

### Data Structure

Each staff maintains two arrays indexed by horizontal step:

```java
class StaffExtents {
    double[] top;   // highest occupied Y at each horizontal step
    double[] bot;   // lowest occupied Y at each horizontal step
    int stepCount;  // = YSTEP (128, matching abc2svg)

    void ySet(boolean above, double x, double width, double y);
    double yGet(boolean above, double x, double width);
}
```

- `ySet(true, x, w, y)` — reserve space above: for each step in [x, x+w],
  set `top[i] = max(top[i], y)`
- `yGet(true, x, w)` — query: return max of `top[i]` for steps in [x, x+w]
- Step resolution: line width / YSTEP (~4.7px at typical widths)

### Three-Layer Model

Three independent `StaffExtents` instances per staff:

| Layer | Elements | Collision behavior |
|---|---|---|
| Note-attached | Articulations, fermata, trill, breath mark | Stacked closest to noteheads. Can intrude into structural layer. |
| Structural | Volta brackets, dynamics hairpins, text dynamics | Drawn at consistent height. Note-attached elements allowed to overlap. |
| System | Tempo, beat changes, annotations | Always topmost. Pushes total line height if needed. |

Processing order matches abc2svg: note-attached first, then structural, then
system. Each tier's `ySet` reservations are visible to subsequent tiers via
`yGet`, but the structural tier does NOT push to avoid note-attached intrusions
(volta brackets maintain consistent height per standard engraving practice).

### Hardcoded Above-Staff

All elements stack above the staff. No below-staff collision domain needed.
SongScribe is exclusively vocal music with lyrics below, so the abc2svg
`up_p()` above/below decision logic is not ported. Direction is always "above."

---

## Beam Algorithm

Ported from abc2svg `calculate_beam()` and `draw_beams()` in core/draw.js,
plus `set_beams()` in core/music.js.

### Slope Calculation

1. Compute raw slope from first/last note positions:
   `a = (lastY - firstY) / (lastX - firstX)`
2. Apply hyperbolic dampening:
   `a = BEAM_SLOPE_MAX * a / (BEAM_SLOPE_MAX + abs(a))`
   This saturates at 0.4 regardless of pitch contour.
3. Compute y-intercept `b` to maintain minimum stem lengths from `min_tb[]`
4. Iteratively adjust if any stem is too short

### Flat Beam Snapping

When the beam is flat (slope near zero), snap to staff-line grid:
`y = round((y + 1.5) / 0.75) * 0.75 - 1.5` (in staff spaces)

This ensures beams sit clearly ON a line or IN a space.

### Beam Thickening

Preserved from current SongScribe: `1/cos(angle)` correction clamped to
3.3-8.8% to compensate for lost perpendicular thickness on angled beams.
(abc2svg doesn't need this because SVG rendering is resolution-independent,
but SongScribe's raster rendering benefits from it.)

### Stem Direction

Automatic from pitch, matching abc2svg `set_beams()`:

- **Beamed notes:** Average of highest + lowest pitch across the group vs
  staff midpoint. Below midpoint = stems up, above = stems down.
- **Unbeamed notes:** Average pitch of the note/chord vs midpoint.
- **Manual override:** `note.upper` can be set explicitly by the user. An
  explicit override persists until the note's pitch changes or the user
  clears it. A flag distinguishes "user set" from "auto-computed."

### Partial Beam Stub Direction

Automatic from rhythmic context, ported from abc2svg `draw_beams()`:

1. First note in group: stub extends right
2. Last note in group: stub extends left
3. At a beam break: stub extends right
4. Next note has a beam break: stub extends left
5. Otherwise: compare previous and next note durations — stub points toward
   the note with more flags (shorter duration)

The current `invertFractionBeamOrientation` manual toggle is removed.

### Multi-Level Beams

Recursive beam drawing for 16th, 32nd notes — same as current
`BeamGroupRenderer.doDrawBeams()` but using abc2svg's iteration pattern
for finding sub-groups at each beam level.

---

## Tie Algorithm

Ported from abc2svg `slur_out()` in core/draw.js.

### Cubic Bezier Curves

Replace current quadratic Bezier with cubic, providing independent endpoint
tangents.

**Control point formula (abc2svg):**

```
mx = 0.5 * (x1 + x2)          // midpoint X
my = 0.5 * (y1 + y2)          // midpoint Y
alfa = 0.3                     // adjusted for wide ties: += 0.002 * (dx - 40), max 0.7
beta = 0.45                    // tangent factor

// Control point 1 (near start)
cp1x = mx + alfa * (x1 - mx)
cp1y = my + alfa * (y1 - my) + height
cp1x = x1 + beta * (cp1x - x1)
cp1y = y1 + beta * (cp1y - y1)

// Control point 2 (near end) — symmetric
cp2x = mx + alfa * (x2 - mx)
cp2y = my + alfa * (y2 - my) + height
cp2x = x2 + beta * (cp2x - x2)
cp2y = y2 + beta * (cp2y - y2)
```

### Dynamic Height

Arc height scales with span distance:

```
height = (0.08 * dx + 1.5) * direction    // dx in staff spaces
```

Clamped to 5.0 staff spaces maximum. The `slurheight` scaling factor from
abc2svg's format options is not ported (no user-facing control needed).

### Interior Note Collision Avoidance

For ties spanning multiple notes, check interior notes for clearance:

```
h = 0.65 * (max interior deflection from tie curve)
```

If interior notes would collide with the tie arc, push the midpoint up by
`0.45 * h`, recalculating control points.

### Tie Direction

Tied to stem direction: stem up = tie curves below, stem down = tie curves
above. When start and end notes have mixed stem directions, tie direction
follows the start note.

### Rendered Shape

The tie is drawn as a filled lens shape: outer cubic Bezier curve with a
parallel inner curve offset by `tieMidpointThickness` (from SMuFL
EngravingDefaults), forming a closed path.

---

## Vertical Stacking Algorithm

Ported from abc2svg's tier-based decoration processing in core/deco.js and
core/draw.js.

### Processing Order (abc2svg tier mapping)

**Tier 1 — Near-note decorations (d_near, func 0):**
- Staccato
- Accent

Positioned closest to the notehead/stem, using y_get to find clear space above
the note column, then y_set to reserve.

**Tier 2 — Note decorations (d_upstaff, func 3):**
- Fermata
- Trill (+ wavy line extension)
- Breath mark (right-shifted, large right width)

Positioned above tier 1 elements. y_get queries include tier 1 reservations.

**Tier 3 — Staff decorations (d_pf/d_cresc, func 6-7):**
- Crescendo / diminuendo hairpins
- Text dynamics (pp, p, mp, mf, f, ff, sfz, fp)
- Volta brackets (first/second endings)

Volta brackets: initial Y = staff top + VOLTA_MARGIN, adjusted upward only to
clear other staff-decoration tier elements. Note-attached elements from tiers 1
and 2 are allowed to intrude into the volta bracket space (standard engraving
practice per Gould).

**Tier 4 — System decorations (draw_partempo):**
- Tempo markings (note glyph + BPM or equivalence)
- Beat change equivalences (note = note)
- Annotations (free text)

Always topmost. Uses abc2svg's "dosh" bit-shifting to stagger horizontally
overlapping tempo markings vertically.

### Note-to-Note Connectors (separate pass, no vertical stacking)

- Ties
- Glissando

Rendered after notes, before above-staff decorations. Not part of the
y_get/y_set collision system.

---

## Lyrics System

Ported from abc2svg core/lyrics.js.

### Syllable Width Influence on Note Spacing

Ported from `ly_width()`. The full algorithm is described in the
**Horizontal Spacing Algorithm** section (Lyric Overflow Walk). In summary:
each syllable's required width can extend the note column's `leftExtent` and
`rightExtent`, which the horizontal spacing calculator then uses for gap
computation. The forward-walk approach avoids over-spacing when a long syllable
is followed by notes without their own lyrics.

### Collision Avoidance

Adjacent syllables cannot overlap. The lyric overflow walk ensures each note
has enough right-side space to accommodate its syllable without colliding with
the next note's syllable.

### Rendering

Single lyric line (no multi-verse support). Rendered below the staff using:

- Syllable text centered under each note (shifted by `ly.shift`)
- Hyphens between syllables of the same word
- Extender lines for held syllables

### Vertical Position

Lyrics baseline = lowest stem bottom + configurable offset. Lyrics do not
participate in the above-staff y_get/y_set system. They have a fixed position
below the staff, influencing only the total line height.

---

## Text Dynamics Rendering (New)

`DynamicAttachment` (pp, p, mp, mf, f, ff, sfz, fp) exists in the model but
currently has no renderer. This spec adds rendering.

Text dynamics are positioned in the **staff-decoration tier** (tier 3),
alongside hairpins and volta brackets. They participate in the same y_get/y_set
collision domain.

Rendered using an italic music font at a configurable size. Position: below
the note column but above the staff (above-only context), offset horizontally
to center under the notehead.

---

## Renderer Architecture

### Current Architecture (removed)

- `RendererRegistry` — singleton registry mapping element types to renderers
- `BaseElementRenderer<T>` — abstract base with template method pattern
- 18 singleton renderer classes accessed via `getInstance()`

### New Architecture

**Note-attached elements render themselves:**

Each attachment/decoration class has a `render(Graphics2D, RenderContext)` method.
The rendering logic is co-located with the element data. No registry lookup.

Examples:
- `Articulation.render(g2, ctx)` — draws staccato dot or accent mark
- `FermataAttachment.render(g2, ctx)` — draws fermata glyph
- `TrillAttachment.render(g2, ctx)` — draws trill symbol + wavy extension
- `TempoAttachment.render(g2, ctx)` — draws tempo note glyph + text
- `BeatChangeAttachment.render(g2, ctx)` — draws equivalence marking
- `AnnotationAttachment.render(g2, ctx)` — draws annotation text

**Span elements rendered by LineRenderer methods:**

Interval-based elements that span multiple notes remain as methods on
LineRenderer, since their data (intervals) lives in the data layer and should
not have Graphics2D dependencies.

- `LineRenderer.renderBeams(g2, layoutResult)`
- `LineRenderer.renderTies(g2, layoutResult)`
- `LineRenderer.renderTuplets(g2, layoutResult)`
- `LineRenderer.renderDynamicsHairpins(g2, layoutResult)`
- `LineRenderer.renderTextDynamics(g2, layoutResult)`
- `LineRenderer.renderEndings(g2, layoutResult)`
- `LineRenderer.renderGlissandos(g2, layoutResult)`
- `LineRenderer.renderLyrics(g2, layoutResult)`

**In-staff elements preserved:**

NoteRenderer, RestRenderer, BarRenderer, GraceNoteRenderer, ClefRenderer,
KeySignatureRenderer, StaffRenderer continue to exist but are called directly
by LineRenderer, not via registry lookup.

### Draw Order

Matches abc2svg tier processing:

```
1. Staff lines
2. Clef + key signature
3. Notes (heads, stems, flags, dots, accidentals, ledger lines)
4. Rests, barlines, breath marks, grace notes
5. Glissandos (note-to-note connectors)
6. Ties (note-to-note connectors)
7. Beams
8. Near-note decorations (articulations)
9. Note decorations (fermata, trill)
10. Staff decorations (dynamics hairpins, text dynamics, volta brackets)
11. System decorations (tempo, beat changes, annotations)
12. Lyrics
13. Edit-mode overlays (insertion note, drag rectangle)
```

---

## Manual Position Adjustments

SongScribe allows users to manually adjust element positions after layout.
abc2svg does not provide this facility. All manual offsets are preserved.

### Behavior

Manual offsets are applied **post-layout, without re-running collision
detection**. The user takes responsibility for any resulting overlaps. This is
the simplest approach and matches the current behavior.

### Adjusted Elements

| Element | X Adjustment | Y Adjustment | Field |
|---|---|---|---|
| Note | xOffset (int) | - | Note.xOffset |
| Glissando | x1Translate, x2Translate | - | Note.Glissando fields |
| Crescendo | x1Shift, x2Shift | yShift | DynamicsInterval fields |
| Diminuendo | x1Shift, x2Shift | yShift | DynamicsInterval fields |
| Ending | - | yPosition | EndingInterval field |
| Trill | - | yPosition | Trill/RangeElement field |
| Tempo | userXOffset | userYOffset | TempoAttachment fields |
| Beat change | userXOffset | userYOffset | BeatChangeAttachment fields |
| Annotation | userXOffset | userYOffset | AnnotationAttachment fields |
| Tuplet | - | verticalPosition | TupletInterval field |

All offset fields are stored in staff-space units after the coordinate system
migration. Legacy pixel-based offsets in existing files are converted on load
via FormatMigrator.

---

## Data Model Changes

### New Typed Intervals

Following the pattern established on the develop branch (commit 30662307):

**BeamInterval** — new subclass of Interval:
- No additional fields needed on the interval itself
- All computed beam geometry stored in `LayoutResult.BeamLayout`

**TieInterval** — new subclass of Interval (if ties migrate from plain Interval):
- No additional fields needed
- All computed tie geometry stored in `LayoutResult.TieLayout`

### Stem Direction

`Note.upper` remains as the stored field but gains a companion flag:

```java
boolean upper;              // stem direction (true = up)
boolean stemDirectionAuto;  // true = computed from pitch, false = user override
```

When `stemDirectionAuto` is true, the beam/layout algorithm overwrites `upper`
on each layout pass. When false, the stored value is preserved.

### Removed Fields

- `Note.invertFractionBeamOrientation` — partial beam direction is now automatic
- `Note.Properties.lengthening` — moved to LayoutResult.StemLayout
- `Note.Properties.beamThickening` — moved to LayoutResult.BeamLayout
- `Note.Properties.stem` (Line2D.Double) — moved to LayoutResult.StemLayout

### FormatMigrator Updates

- Convert legacy pixel-based manual offsets to staff-space units
- Handle removal of `invertFractionBeamOrientation` (ignore on read)
- Convert legacy `Note.Properties` data if persisted (it is not currently
  persisted, so this may not be needed)

---

## Elements NOT Ported from abc2svg

These abc2svg decorations have no SongScribe equivalent and are not ported:

- Tenuto, marcato, snap, thumb, wedge/spiccato
- Mordent (upper/lower), turn, inverted turn
- Upbow, downbow
- Slide (abc2svg's slide; SongScribe has glissando instead)
- Arpeggio, roll
- Fingering (0-5)
- Segno, coda, D.C., D.S., Fine
- Guitar chords / chord symbols
- Multi-verse lyrics
- Multiple voices per staff
- Multiple staves per system
- C clef, F clef
- Feathered beams
- Cross-staff beams

---

## Implementation Milestones

The rewrite replaces the entire rendering pipeline at once. These milestones
define checkpoints where the application should produce viewable (if incomplete)
output.

### Milestone 1: Coordinate System + Staff + Notes

- Establish staff-space coordinate system with pixelsPerStaffSpace
- Render boundary transform on Graphics2D
- Staff lines render correctly at any scale
- Note heads, stems, flags, dots, accidentals, ledger lines render correctly
- Rests, barlines, breath marks, grace notes render correctly
- Clef and key signature render correctly
- Mouse coordinate inverse mapping works (hit testing)

**Verification:** Open an existing composition. Notes and staff should render
correctly. Nothing above the staff renders yet. Lyrics don't render yet.

### Milestone 2: Beams + Stems

- Beam algorithm ported (hyperbolic slope, flat beam snapping, thickening)
- Automatic stem direction from pitch
- Manual stem direction override
- Automatic partial beam stub direction
- Multi-level beams (16th, 32nd)
- BeamLayout stored in LayoutResult

**Verification:** Open compositions with beamed passages. Beams should render
with correct slopes. Compare visually against abc2svg output for the same
melodic patterns.

### Milestone 3: Ties + Glissandos

- Cubic Bezier tie curves with dynamic height
- Interior note collision avoidance
- Tie direction from stem direction
- Filled lens shape rendering
- TieLayout stored in LayoutResult
- Glissando rendering between notes

**Verification:** Open compositions with ties. Ties should scale height with
span distance. Short ties should look similar to before; long ties should look
noticeably better.

### Milestone 4: Vertical Stacking + All Decorations

- y-extent array collision detection (YSTEP=128)
- Three-layer model (note-attached, structural, system)
- Tier 1: Articulations (staccato, accent)
- Tier 2: Fermata, trill
- Tier 3: Dynamics hairpins, text dynamics (new), volta brackets
- Tier 4: Tempo, beat changes, annotations
- All manual offset adjustments working post-layout
- Self-rendering note-attached elements

**Verification:** Open compositions with stacked decorations. Elements should
not overlap (except allowed intrusions into volta space). Text dynamics should
render for the first time.

### Milestone 5: Lyrics + Line Height + Tuplets

- Lyric collision avoidance (syllable widths influence spacing)
- Lyric rendering below staff (syllables, hyphens, extenders)
- Total line height calculation including all layers
- Tuplet bracket rendering
- Line justification (compress/stretch)

**Verification:** Full compositions should render completely. Compare overall
layout quality against abc2svg output. Lyrics should not overlap.

### Milestone 6: Cleanup + Polish

- Remove RendererRegistry and BaseElementRenderer hierarchy
- Remove Note.Properties mutable state
- Remove all pixel-based constants from layout code
- Update FormatMigrator for coordinate system changes
- Verify all manual adjustment fields work in staff-space units
- Performance profiling (ensure layout speed is acceptable)

**Verification:** Full regression testing with all existing composition files.
No visual regressions compared to milestone 5. Clean codebase with no legacy
rendering code paths.
