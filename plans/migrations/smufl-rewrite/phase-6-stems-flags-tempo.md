**Type:** Sub-plan  <br>
**Parent:** plans/migrations/smufl-rewrite/smufl-rewrite.md → Phase 6  <br>
**Captured:** 2026-02-16  <br>
**Pre-planned:** No  <br>
**Status:** Completed

---

# Phase 6: Stems, Flags, Tempo — Implementation Plan

## Context

Phase 5 migrated note heads, rests, and accidentals to Bravura/SMuFL, but stems are still drawn as stroked `Line2D` shapes (causing anti-aliasing artifacts), flags still use Fughetta PUA glyphs, and tempo notes use a hybrid of Bravura heads + manual stem lines + Fughetta flags. This phase completes the migration for these three areas.

## Part 1: Stem Rendering — Filled Rectangles with SMuFL Anchors

**Problem**: Stems drawn with `BasicStroke` on `Line2D` produce anti-aliased edges on vertical lines. Per the HiDPI stroke guidelines, axis-aligned lines should use filled rectangles for crisp rendering.

**Files to modify**: `src/main/java/songscribe/ui/renderer/NoteRenderer.java`

### Changes to `renderStem()`

1. **Get anchor positions from SMuFL metadata** instead of hardcoded constants:
   - `stemUpSE` anchor for stem-up notes (right side of notehead)
   - `stemDownNW` anchor for stem-down notes (left side of notehead)
   - The anchors give X position of the stem and Y start point

2. **Replace stroke-based drawing with `g2.fill(Rectangle2D.Double)`**:
   - Stem width = `stemThickness` from engraving defaults (already in `STEM_STROKE_IMPL`)
   - For stem-up: rect from `(anchorX - width/2, stemTopY, width, stemLength)`
   - For stem-down: rect from `(anchorX - width/2, anchorY, width, stemLength)`
   - Standard stem length: 3.5 staff spaces (28px), matching `BravuraFontBoundsProvider.STEM_LENGTH_STAFF_SPACES`

3. **Continue setting `note.properties.stem`** with the center-line coordinates so `BeamGroupRenderer` can still read stem positions for beam attachment. The `Line2D` stores the logical stem line; rendering uses the filled rect.

4. **Remove stem length adjustments for flags** (`FLAG_Y_LENGTH`, `SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE` usage in `renderStem`). SMuFL flags are pre-composed single glyphs that don't need stem extension.

5. **Remove constants that become unused**:
   - `UPPER_CROTCHET_STEM_X`, `UPPER_MINIM_STEM_X` (replaced by anchor data)
   - `UPPER_STEM`, `LOWER_STEM` (replaced by anchor-based calculation)
   - `LOWER_STEM_CROTCHET_Y1_OFFSET` (Fughetta-specific Y tweak)
   - `FLAG_Y_LENGTH`, `SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE`

### New constants/fields to add

- `STEM_LENGTH = StaffSpaces.toPixels(3.5)` — standard stem length in pixels
- `STEM_WIDTH` — extracted from `STEM_STROKE_IMPL.getLineWidth()` for rectangle width
- Cached anchor data for black/half noteheads (from `SMuFLMetadata.getAnchors()`)

## Part 2: Flag Rendering — SMuFL Flag Glyphs

**Problem**: Flags use Fughetta PUA codepoints with manual multi-glyph stacking. SMuFL provides single pre-composed flag glyphs for each duration.

**Files to modify**: `src/main/java/songscribe/ui/renderer/NoteRenderer.java`

### Changes to `renderFlags()`

1. **Map note type + direction to a single SMuFL flag glyph**:
   - Quaver up → `FLAG_8TH_UP`, down → `FLAG_8TH_DOWN`
   - Semiquaver up → `FLAG_16TH_UP`, down → `FLAG_16TH_DOWN`
   - Demi-semiquaver up → `FLAG_32ND_UP`, down → `FLAG_32ND_DOWN`

2. **Position flag at stem end**:
   - Stem-up: flag X = stem X, flag Y = stem top (the tip of the stem)
   - Stem-down: flag X = stem X, flag Y = stem bottom
   - SMuFL flags have their origin at the stem connection point, so positioning at stem end should be correct

3. **Single `drawString()` call** per note (no stacking of multiple flag glyphs)

4. **Remove all flag position constants**: `UPPER_FLAG_X`, `UPPER_FLAG_Y`, `UPPER_FLAG_2_Y`, `UPPER_FLAG_3_Y`, `LOWER_FLAG_Y`, `LOWER_FLAG_2_Y`, `LOWER_FLAG_3_Y`

5. **Remove Fughetta flag references from `BaseElementRenderer.java`**: `MAIN_UPPER_FLAG`, `SECOND_UPPER_FLAG`, `MAIN_LOWER_FLAG`, `SECOND_LOWER_FLAG` (check for other usages first)

## Part 3: Tempo Rendering — SMuFL Metronome Glyphs

**Problem**: `TempoRenderer.paintSimpleTempoNote()` manually assembles notehead + stem + flag + dot using mixed Fughetta/Bravura. SMuFL provides pre-composed metronome note glyphs that include notehead + stem + flag in a single codepoint.

**Files to modify**: `src/main/java/songscribe/ui/renderer/TempoRenderer.java`

### Changes to `paintSimpleTempoNote()`

1. **Map tempo note type to SMuFL metronome glyph** (all stem-up since tempo notes always display stem-up):
   - Semibreve → `MET_NOTE_WHOLE`
   - Minim → `MET_NOTE_HALF_UP`
   - Crotchet → `MET_NOTE_QUARTER_UP`
   - Quaver → `MET_NOTE_8TH_UP`
   - Semiquaver → `MET_NOTE_16TH_UP`
   - Demi-semiquaver → `MET_NOTE_32ND_UP`

2. **Single `drawString()` call** for the note glyph (replaces separate head + stem + flag drawing)

3. **Use `MET_AUGMENTATION_DOT`** for dotted tempo notes (replaces `fillOval`)

4. **Remove unused constants** from `TempoRenderer`: `UPPER_CROTCHET_STEM_X`, `UPPER_MINIM_STEM_X`, `TEMPO_STEM_SHORTENING`, `UPPER_STEM`, `UPPER_FLAG_X`, `UPPER_FLAG_Y`

5. **Update `getTempoNoteBounds()`** to compute bounds from the metronome glyph's visual bounds (using `BRAVURA_FONT.createGlyphVector()`) instead of manual stem/flag bound assembly

6. **Update `drawTempoChangeNote()`** — likely needs positioning adjustments since metronome glyphs have different metrics than the manual assembly. The scaling via `TEMPO_CHANGE_ZOOM_X/Y` should still work.

7. **Remove `isTempoNote` parameter and branches** from `NoteRenderer.renderStem()` and `NoteRenderer.renderFlags()` since tempo notes will no longer use those methods.

## Part 4: Cleanup

1. Remove `MUSIC_FONT` usage from `TempoRenderer` (was only used for Fughetta flag)
2. Check `NoteRenderer.renderNoteHead()` — the `noteHeadXPos` adjustment for lower stems may need revisiting with anchor-based positioning
3. Verify `renderNoteHeadSimple()` still works (used by backward-compat `render(g2, note, middleLineY)`)

## Verification

1. `./scripts/compile.sh` — must succeed
2. `./scripts/run-debug.sh` — run app, visually verify:
   - Stems: crisp vertical lines (no grey fringing), correct positions for all note types
   - Stems up and down for crotchet, minim, quaver, semiquaver, demi-semiquaver
   - Flags: correct glyph for 8th/16th/32nd notes, up and down
   - Beamed notes: stems still connect to beams correctly
   - Tempo markings: correct metronome note appearance with dots
   - Dotted notes: dots still positioned correctly
