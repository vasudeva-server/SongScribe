**Type:** Sub-plan  <br>
**Parent:** plans/migrations/smufl-rewrite/smufl-rewrite.md → Phase 7  <br>
**Captured:** 2026-02-16  <br>
**Pre-planned:** Yes  <br>
**Status:** In Progress

---

# Phase 7: Custom-Drawn Elements → SMuFL Glyphs

## Status: Pending

## Overview

Replace all remaining custom-drawn elements (Java2D shapes, Fughetta PUA codepoints, custom font glyphs) with SMuFL/Bravura glyph rendering. All infrastructure (`drawBravuraGlyph()`, `BRAVURA_FONT`, SMuFL metadata) is in place from earlier phases.

## Current Drawing Approaches by Renderer

| Renderer | Current Approach | Target |
|---|---|---|
| ArticulationRenderer | Java2D `Line2D` (accent), `Ellipse2D` (staccato) | SMuFL articulation glyphs |
| FermataRenderer | `Arc2D` + `Ellipse2D` composite shape | SMuFL fermata glyph |
| BarRenderer | `Line2D` + `BasicStroke` + `Ellipse2D` (repeat dots) | SMuFL barline/repeat glyphs |
| TrillRenderer | Fughetta `\uf0d9` + `\uf07e` (wavy extension) | SMuFL trill + wiggleTrill |
| GlissandoRenderer | Fughetta `\uf07e` (tiled, rotated) | SMuFL wiggleGlissando |
| TupletRenderer | `TupletNumbers.ttf` custom font | SMuFL tuplet digit glyphs |
| BeatChangeRenderer | Fughetta `\uf06a` (flag) | SMuFL flag glyphs |

## New Glyphs Needed in SMuFLGlyph.java

Only two additions required -- all others already exist in the enum:

- `WIGGLE_TRILL("wiggleTrill", '\uEAA4')` -- trill extension wavy line segment
- `WIGGLE_GLISSANDO("wiggleGlissando", '\uEAAF')` -- glissando wavy line segment

---

## Sub-Phase 7a: Articulations and Fermata

### 7a.1: Accent (ArticulationRenderer)

**Current:** Two `g2.drawLine()` calls forming a ">" shape with `ACCENT_STROKE` and `ACCENT_LINE_HALF_HEIGHT`.

**Target:** `ARTIC_ACCENT_ABOVE` (U+E4A0) only. Per Gould/Ross 5.1, accents are always placed above the staff in vocal-only engraving. No below variant needed.

**Changes:**
1. Remove constants: `ACCENT_STROKE_WIDTH`, `ACCENT_STROKE`, `ACCENT_LINE_HALF_HEIGHT`, `ACCENT_BOUNDS`, `computeAccentBounds()`.
2. Add SMuFL bbox-derived accent dimensions for layout positioning.
3. In `drawAccent()`: replace two `drawLine()` calls with single `drawBravuraGlyph()` call using `ARTIC_ACCENT_ABOVE`.
4. Position glyph so vertical center aligns with computed `accentY`. Use SMuFL bbox to compute height. Center horizontally on notehead using advance width.
5. Update `calculateAccentY()` to use SMuFL bbox height instead of old `ACCENT_BOUNDS.height / 2`.

**Notes:**
- Both `renderFromLayout()` and `renderFallback()` call `drawAccent()`, so change is centralized.
- Removes last usage of `GraphicUtils.getDpiAwareStrokeWidth()` for `ACCENT_STROKE_WIDTH`.

### 7a.2: Staccato (ArticulationRenderer)

**Current:** 4x4 `Ellipse2D.Double` filled at offset from note center. `STACCATO_HALF_HEIGHT = 2`.

**Target:** `ARTIC_STACCATO_ABOVE` (U+E4A2) only. Per Gould/Ross 5.1, staccato is always placed above the staff in vocal-only engraving. No below variant needed.

**Changes:**
1. Remove `STACCATO_ELLIPSE` and `STACCATO_HALF_HEIGHT`.
2. Add SMuFL bbox-derived staccato dimensions.
3. In `renderStaccato()` / `drawStaccatoFromLayout()`: replace `g2.fill(STACCATO_ELLIPSE)` with `drawBravuraGlyph()`.
4. Center horizontally on notehead using glyph advance width.
5. Update `calculateStaccatoY()` to use SMuFL bbox half-height.

### 7a.3: Fermata (FermataRenderer)

**Current:** Composite `Shape` from two `Arc2D`s (subtracted for crescent) + `Ellipse2D` dot, with coordinates ~-115 to +115, scaled by `g2.scale(0.0625, 0.0625)` then `g2.scale(0.9, 0.8)`.

**Target:** `FERMATA_ABOVE` (U+E4C0). Only above-staff variant needed (no below-staff case in existing code).

**Changes:**
1. Remove static `FERMATA` shape and initializer block (`Arc2D`, `Ellipse2D`, `Area` operations).
2. In `renderElement()`: replace transform+scale+fill with single `drawBravuraGlyph()`.
3. Position using existing `getEffectiveFermataYPos()` for Y. Center horizontally over notehead using advance width.

**Notes:**
- `getFermataYPos()` fallback logic (for insertion note preview) continues working -- it only calculates Y position.

### 7a Verification
- [ ] Compile and run
- [ ] Staccato dots appear centered on noteheads at correct distances
- [ ] Accent ">" renders with correct orientation based on stem direction
- [ ] Staccato + accent combined: correct stacking
- [ ] Fermata appears above staff at correct position
- [ ] Fermata on ledger-line notes adjusts position upward

---

## Sub-Phase 7b: Barlines and Repeats

**Current:** All barlines and repeats drawn with `Line2D`, six different `BasicStroke` objects, and `Ellipse2D.Float` for repeat dots. Positions computed from `NOTE_FONT_SIZE` ratios. Rendering delegates through `switch` on `NoteType`.

**Target glyphs:**
| NoteType | SMuFL Glyph | Codepoint |
|---|---|---|
| `SINGLE_BARLINE` | `BARLINE_SINGLE` | U+E030 |
| `DOUBLE_BARLINE` | `BARLINE_DOUBLE` | U+E031 |
| `FINAL_DOUBLE_BARLINE` | `BARLINE_FINAL` | U+E032 |
| `REPEAT_LEFT` | `REPEAT_LEFT` | U+E040 |
| `REPEAT_RIGHT` | `REPEAT_RIGHT` | U+E041 |
| `REPEAT_LEFT_RIGHT` | `REPEAT_RIGHT_LEFT` | U+E042 |

**Dimensional compatibility:** SMuFL barline glyphs span 4 staff spaces (lines 1-5). Current code uses `NOTE_FONT_SIZE / 2` for half-height. At 1 ss = 8px, 4 ss = 32px = `NOTE_FONT_SIZE`. Exact match.

**Changes:**
1. Remove all stroke constants: `HEAVY_LINE_STROKE`, `THIN_LINE_STROKE`, `REPEAT_HEAVY_STROKE`, `REPEAT_THIN_STROKE`, `LINE_STROKE`.
2. Remove all shape constants: `REPEAT_CIRCLE_1`, `REPEAT_CIRCLE_2`, `VERTICAL_LINE`, `BAR_LINE`.
3. Remove all spacing/position constants: `BAR_LINE_SPACE`, `REPEAT_THIN_CIRCLE_DIFF`, `REPEAT_THICK_THIN_DIFF`, `REPEAT_LEFT_THICK_X`, `REPEAT_RIGHT_THICK_X`, `REPEAT_LEFT_RIGHT_THICK_X`.
4. Replace `drawBarLine()` with single `drawBravuraGlyph()` per type.
5. Replace `drawRepeat()` with single glyph draws.
6. X positioning: align glyph with note's X position (currently `Note.NORMAL_IMAGE_WIDTH` offset). Use advance width from metadata for centering.
7. Y positioning: current code translates to `(noteX, middleLineY)`. SMuFL barline origin is at top staff line. Adjust Y by `-2 * staffSpace` (= -16px).

**Notes:**
- `renderBarLineOrRepeat()` is public static, called from `NoteRenderer`. Interface can remain the same.
- `Note.NORMAL_IMAGE_WIDTH` references preserved for now (Phase 8 removes them).
- `ENGRAVING_DEFAULTS` field can be removed if no stroke constants remain.

### 7b Verification
- [ ] Compile and run
- [ ] Each barline type (single, double, final) renders correctly and aligns with staff lines
- [ ] Each repeat type (left, right, left-right) renders with correct dots and lines
- [ ] Barlines span full staff height (top to bottom line)

---

## Sub-Phase 7c: Trill, Glissando, Breath Mark

### 7c.1: Trill Symbol (TrillRenderer)

**Current:** Draws Fughetta `\uf0d9` using `ctx.getMusicFont()`.

**Target:** `ORNAMENT_TRILL` (U+E566).

**Changes:**
1. Remove `TRILL_GLYPH = "\uf0d9"`.
2. In `renderTrill()`: replace `g2.setFont(ctx.getMusicFont())` + `g2.drawString(TRILL_GLYPH, x, y)` with `drawBravuraGlyph(g2, SMuFLGlyph.ORNAMENT_TRILL, x, y)`.
3. Adjust x/y if Bravura glyph has different origin than Fughetta.

### 7c.2: Trill Wavy Extension (TrillRenderer)

**Current:** `drawWavyLine()` tiles Fughetta `\uf07e` with segment width `GLISSANDO_LENGTH = NOTE_FONT_SIZE / 2.6666667` (~12px).

**Target:** `WIGGLE_TRILL` (U+EAA4) -- dedicated trill wavy line segment. SMuFL `repeatOffset = 0.948 ss = 7.584px`.

**Changes:**
1. Add `WIGGLE_TRILL` to `SMuFLGlyph.java`.
2. Remove `GLISSANDO_GLYPH` and `GLISSANDO_LENGTH`.
3. Compute segment width from Bravura metadata: `StaffSpaces.toPixels(0.948)`.
4. In `drawWavyLine()`: draw `WIGGLE_TRILL` with `BRAVURA_FONT` instead of Fughetta.

### 7c.3: Glissando (GlissandoRenderer)

**Current:** Tiles Fughetta `\uf07e`, rotated and scaled between two notes. `GLISSANDO_LENGTH = NOTE_FONT_SIZE / 2.6666667 = 12px`.

**Target:** `WIGGLE_GLISSANDO` (U+EAAF) -- repeatable segment. SMuFL `repeatOffset = 0.96 ss = 7.68px`.

**Note on glyph choice:** The master plan specifies `glissandoUp` (U+E585), but that is a pre-composed fixed-length glyph. The tiling approach requires a repeatable segment, making `wiggleGlissando` (U+EAAF) the correct choice.

**Changes:**
1. Add `WIGGLE_GLISSANDO` to `SMuFLGlyph.java`.
2. Remove `GLISSANDO_GLYPH = "\uf07e"`.
3. Update `GLISSANDO_LENGTH` to `StaffSpaces.toPixels(0.96)`.
4. In `renderGlissandoLine()`: use `BaseElementRenderer.BRAVURA_FONT` instead of `ctx.getMusicFont()`, draw `WIGGLE_GLISSANDO`.
5. Adjust `GLISSANDO_Y_OFFSET` (currently `2.25`) if Bravura glyph has different vertical alignment.

**Note:** `GlissandoRenderer` does not extend `BaseElementRenderer`, so it must reference `BaseElementRenderer.BRAVURA_FONT` directly.

### 7c.4: Breath Mark

**Current:** `BREATH_MARK` is a `NonNote` type. `NOTE_HEAD.get(NoteType.BREATH_MARK)` returns null → nothing renders. Breath marks are currently **invisible**.

**Target:** `BREATH_MARK_COMMA` (U+E4CE).

**Changes:**
1. In `NoteRenderer.renderElement()`: add a dedicated branch for `BREATH_MARK` before the standard note rendering path. Render using `drawBravuraGlyph()` with `BREATH_MARK_COMMA`.
2. Position at the note's X position, Y from `NoteType.BREATH_MARK.defaultYPos` (-7, above staff).
3. No stems, dots, or ledger lines for breath marks.

### 7c.5: BeatChangeRenderer Flag Cleanup

**Current:** `paintSimpleTempoNote()` still uses Fughetta `\uf06a` for flag glyph (line ~222). Was noted as "Phase 6 scope" but not migrated.

**Changes:**
1. Replace Fughetta flag `\uf06a` with `SMuFLGlyph.FLAG_8TH_UP` (and `FLAG_16TH_UP`, `FLAG_32ND_UP` for other note values).
2. Replace `g2.setFont(MUSIC_FONT)` with Bravura font usage.
3. Adjust flag positioning for Bravura glyph origin.

### 7c Verification
- [ ] Compile and run
- [ ] Trill "tr" symbol renders correctly
- [ ] Extended trill wavy line renders smoothly
- [ ] Glissando connects notes at correct angle
- [ ] Breath mark comma appears above staff
- [ ] Beat change flags render correctly for 8th, 16th, 32nd note values

---

## Sub-Phase 7d: Tuplet Numbers and Brackets

### 7d.1: Tuplet Numbers

**Current:** Uses `BaseElementRenderer.TUPLET_FONT` (from `TupletNumbers.ttf` at size 13f) via `g2.drawString(Integer.toString(grade), cx - 3, tc.getRate(cx - 3) - 5)`.

**Target:** SMuFL tuplet digits `TUPLET_0` through `TUPLET_9` (U+E880-U+E889).

**Changes:**
1. Replace `g2.setFont(BaseElementRenderer.TUPLET_FONT)` + `g2.drawString()` with drawing appropriate SMuFL tuplet glyph via `drawBravuraGlyph()`.
2. Map grade to glyph: direct array/switch mapping from digit to `TUPLET_0`..`TUPLET_9`.
3. For grades >= 10 (extremely rare): draw two glyphs side by side.
4. Use `BRAVURA_FONT` instead of `TUPLET_FONT`.
5. Adjust position offsets (`cx - 3`, `-5`) using SMuFL advance width to center in bracket gap.
6. The bracket gap width (14px: `cx - 7` to `cx + 7`) may need adjustment for SMuFL glyph width.

**ENDING_FONT dependency:** `ENDING_FONT = TUPLET_FONT` in `BaseElementRenderer`. Endings display regular text numbers ("1.", "2."), not music-notation tuplet glyphs. `TupletNumbers.ttf` cannot be deleted until Phase 8 resolves this. Out of scope here.

### 7d.2: Tuplet Bracket Rewrite

**Current:** Two `QuadCurve2D.Float` segments form a curved bracket with a gap for the number. `TupletCalc` computes the linear slope between bracket endpoints, but the bracket itself bows downward via quadratic control points (`tc.getRate(...) - 10` for curvature, `tc.getRate(...) - 8` at gap edges). This curved shape is non-standard.

**Target:** Straight brackets per standard engraving practice (Gould, Ross). SMuFL does not define tuplet bracket glyphs -- the spec explicitly states "scoring applications should use primitives to draw tuplet brackets."

**Design:** A straight bracket consists of three line segments:
- Left vertical end cap (short downward tick at left endpoint)
- Right vertical end cap (short downward tick at right endpoint)
- Horizontal line from left to right, following the slope computed by `TupletCalc`, with a gap in the center for the tuplet number

`LINE_STROKE` (already using SMuFL `tupletBracketThickness`) remains unchanged.

**Changes:**
1. Replace the two `QuadCurve2D.Float` draws with three `Line2D.Float` draws:
   - Left end cap: vertical line of ~0.5 staff space at `(lx, ly)` pointing away from notes.
   - Left bracket arm: `(lx, ly)` to `(cx - halfGap, tc.getRate(cx - halfGap))`.
   - Right bracket arm: `(cx + halfGap, tc.getRate(cx + halfGap))` to `(rx, ry)`.
   - Right end cap: vertical line of ~0.5 staff space at `(rx, ry)` pointing away from notes.
2. `halfGap` is derived from the SMuFL tuplet glyph advance width (+ small padding), replacing the hardcoded `7px`.
3. `TupletCalc` is preserved -- it correctly computes the slope for straight brackets too.
4. End cap direction depends on stem direction: caps point downward for upper brackets, upward for lower brackets.

**Notes:**
- The bracket arms naturally follow the slope because `TupletCalc.getRate()` interpolates linearly between `(lx, ly)` and `(rx, ry)`.
- Combining 7d.1 and 7d.2 avoids touching `renderTuplet()` twice and ensures the bracket gap matches the new glyph width.

### 7d.3: Fix Beam Rendering — Remove Stroke Outline

In `BeamGroupRenderer.drawBeam()`, the beam parallelogram is rendered with both `g2.draw(beam)` and `g2.fill(beam)`. The `draw()` call uses `STEM_STROKE` which adds a half-stroke-width border around the filled shape, causing:
- Anti-aliased edges where beam meets stems (visible fuzziness)
- Beam ends not aligning exactly with stem lines
- Small extra rectangles visible at stem tops on first/last notes of beamed groups

**Fix**: Remove the `g2.draw(beam)` call, keeping only `g2.fill(beam)`. The filled Path2D parallelogram is sufficient. This should also eliminate the extra rectangles at stem tops.

**File**: `src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java`, `drawBeam()` method.

### 7d.4: Verify Extra Rectangles at Stem Tops Resolved

Small extra rectangles are visible at the top of stems on the first and last notes of beamed groups (on the left side of the stem). Likely caused by the `g2.draw(beam)` stroke outline extending beyond the stem line at the beam edges.

Should be resolved by 7d.3. If artifacts persist, investigate whether `NoteRenderer.renderStem()` has overlap/overdraw at the stem endpoint.

**Files**: `BeamGroupRenderer.java`, possibly `NoteRenderer.java`.

### 7d.5: Fix Tuplet Bracket/Number Positioning Above Beams

The tuplet number ("3") is positioned too low — it sits just above the stem tip instead of being offset above the top beam. The bracket lines also overlap with the beams.

**Root cause**: `TupletRenderer.renderTuplet()` calculates bracket Y from the note's Y position + stem lengthening with only a fixed `ly -= 5` pixel offset. This doesn't account for beam thickness or beam count (8th notes have 1 beam, 16ths have 2, etc.).

**Fix options**:
1. Read actual beam top position from note properties (if available after beam rendering)
2. Calculate beam clearance from beam count x (beamThickness + beamSpacing) and add to bracket offset
3. Store the outermost beam Y in note properties during beam rendering for tuplet renderer to reference

The bracket should clear the top beam by at least 0.5-1 staff space. The number should be centered in the bracket gap above the beams.

**File**: `src/main/java/songscribe/ui/renderer/TupletRenderer.java`, `renderTuplet()` method.

### 7d Verification
- [ ] Compile and run
- [ ] Triplet "3" renders centered in bracket gap
- [ ] Other tuplet grades (5, 6, 7) render correctly
- [ ] Bracket is straight with vertical end caps
- [ ] Bracket gap accommodates SMuFL glyph width
- [ ] Upper and lower brackets have correct end cap direction
- [ ] Beams align crisply with stems, no anti-aliased edges or extra rectangles
- [ ] Tuplet brackets clear the top beam with proper spacing
- [ ] First/second endings still render correctly (unaffected)

---

## Recommended Implementation Sequence

**7a → 7c → 7d → 7b** (simplest/lowest risk → most complex)

- **7a** (Articulations + Fermata): Isolated, self-contained. Good warm-up.
- **7c** (Trill, Glissando, Breath Mark, BeatChange flags): Requires two new enum entries. Straightforward Fughetta → Bravura swaps.
- **7d** (Tuplet Numbers + Brackets): Font swap plus bracket shape rewrite from curved to straight.
- **7b** (Barlines + Repeats): Most drastic change -- removes many constants, changes positioning model. Benefits from confidence gained in earlier sub-phases.

Sub-phases have no inter-dependencies and can technically be done in any order.

---

## Risks and Complications

1. **Positioning offsets:** Every renderer has hand-tuned pixel offsets calibrated to Fughetta metrics. Bravura glyphs have different bounding boxes/origins. Each sub-phase requires visual tuning. Use SMuFL metadata (bounding boxes, advance widths) to compute offsets rather than introducing new magic numbers.

2. **Barline staff height alignment:** SMuFL barlines span 4 staff spaces. At 1 ss = 8px, 4 ss = 32px = `NOTE_FONT_SIZE`. Exact match -- no issue expected.

3. **Glissando/trill segment density change:** Fughetta segment width ~12px, SMuFL segments ~7.6px. More segments per unit length, changing visual density. Cosmetic change matching SMuFL standard, but verify visually.

4. **Breath mark visibility:** Currently invisible. Adding the glyph makes them visible for the first time. A correction, not a regression, but may surface unintended breath marks in existing compositions.

5. **ENDING_FONT dependency:** `TupletNumbers.ttf` cannot be deleted in this phase. Phase 8 must resolve the `ENDING_FONT` dependency first.

6. **GlissandoRenderer isolation:** Does not extend `BaseElementRenderer`. Must reference `BRAVURA_FONT` directly rather than calling `drawBravuraGlyph()`.
