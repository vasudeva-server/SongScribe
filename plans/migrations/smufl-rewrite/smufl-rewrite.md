# Bravura/SMuFL Migration Plan

## Status Dashboard

| Phase | Description                                                                                                | Status      | Sub-plan |
|-------|------------------------------------------------------------------------------------------------------------|-------------|----------|
| 0     | [SMuFL Metadata Infrastructure](#-phase-0-smufl-metadata-infrastructure)                                   | ✅ Complete | —        |
| 1     | [Bravura Font Registration + Proof of Concept](#-phase-1-bravura-font-registration--proof-of-concept)      | ✅ Complete | —        |
| 2     | [Collapse Note Subclass Hierarchy](#-phase-2-collapse-note-subclass-hierarchy)                             | ✅ Complete | [phase-2-collapse-hierarchy.md](phase-2-collapse-hierarchy.md) |
| 3     | [Engraving Defaults](#️-phase-3-engraving-defaults-parallel-with-phase-2)                                  | ✅ Complete | [phase-3-engraving-defaults.md](phase-3-engraving-defaults.md) |
| 4     | [SMuFL-Driven Glyph Bounds](#-phase-4-smufl-driven-glyph-bounds)                                           | ✅ Complete | [phase-4-smufl-glyph-bounds.md](phase-4-smufl-glyph-bounds.md) |
| 5     | [Note Head, Rest, and Accidental Glyph Rendering](#-phase-5-note-head-rest-and-accidental-glyph-rendering) | ✅ Complete | [phase-5-note-rest-accidental-glyphs.md](phase-5-note-rest-accidental-glyphs.md) |
| 6     | [Stems, Flags, Tempo](#-phase-6-stems-flags-tempo)                                                        | ✅ Complete | [phase-6-stems-flags-tempo.md](phase-6-stems-flags-tempo.md) |
| 7     | [Custom-Drawn Elements → SMuFL Glyphs](#-phase-7-custom-drawn-elements--smufl-glyphs)                      | 📋 Sub-plan | [phase-7-custom-to-smufl.md](phase-7-custom-to-smufl.md) |
| 7a    | [Screen Rendering Stroke Quality](#-phase-7a-screen-rendering-stroke-quality)                              | 📋 Sub-plan | [phase-7a-stroke-quality.md](phase-7a-stroke-quality.md) |
| 8     | [Remove Fughetta and Clean Up](#-phase-8-remove-fughetta-and-clean-up)                                     | ⏳ Pending | —        |
| 9     | [Remove Legacy HiDPI Infrastructure](#-phase-9-remove-legacy-hidpi-infrastructure)                         | ⏳ Pending | —        |
| 10    | [Staff-Space Coordinate Unit Migration](#-phase-10-staff-space-coordinate-unit-migration)                  | ⏳ Pending | —        |

## Context

SongScribe's note bounding boxes and glyph dimensions are scattered across 4+ independent systems that disagree with each other, all using hardcoded pixel constants measured from the Fughetta font. There are 20+ Note subclasses that differ only in these constants. Renderers mix font glyphs with custom drawing for elements that have standard SMuFL glyphs. All thickness/spacing values are hardcoded.

This plan replaces the entire glyph and metrics infrastructure with SMuFL/Bravura. Staff spaces become the universal coordinate unit. Engraving defaults come from metadata. The Note subclass hierarchy is collapsed. Custom-drawn elements that have SMuFL equivalents become glyph renders.

## Architectural Decisions

- **Font**: Bravura (SMuFL standard). Files already in project.
- **Coordinate unit**: Staff spaces throughout. 1 staff space = `STAFF_SPACE` = 8px at current scale.
- **Y convention**: Y-down (screen convention) internally. SMuFL metadata Y values flipped on load.
- **Engraving defaults**: All from `bravura_metadata.json`, never hardcoded.
- **Note hierarchy**: Collapsed. `Note` becomes concrete, `NonNote` retained for barlines/breath marks, `GraceSemiQuaver` retained (has extra state).
- **Hairpins**: Keep as draw operations (variable-length spans don't suit fixed-width glyphs).
- **Volta brackets**: Keep as draw operations (spanning elements with text).
- **Beams, ties, staff lines, ledger lines**: Keep as draw operations.

---

## ✅ Phase 0: SMuFL Metadata Infrastructure

**Goal**: Data foundation. Load `bravura_metadata.json`, provide typed access to glyphs, bounds, anchors, and engraving defaults.

**Create**:
- `src/main/java/songscribe/smufl/SMuFLGlyph.java` - Enum of ~50 glyphs with SMuFL name + Unicode codepoint
- `src/main/java/songscribe/smufl/SMuFLMetadata.java` - Parses metadata JSON, lazy singleton. Provides:
    - `getBBox(String glyphName)` -> Y-flipped bounding box in staff spaces
    - `getAnchors(String glyphName)` -> stem attachment points, Y-flipped
    - `getAdvanceWidth(String glyphName)` -> staff spaces
    - `getEngravingDefaults()` -> typed record
- `src/main/java/songscribe/smufl/BBox.java` - Record: left, top, right, bottom (Y-down, staff spaces)
- `src/main/java/songscribe/smufl/GlyphAnchors.java` - Record with stem/cutout anchors
- `src/main/java/songscribe/smufl/EngravingDefaults.java` - Record with all defaults from metadata
- `src/main/java/songscribe/smufl/StaffSpaces.java` - `toPixels(double)` / `fromPixels(double)` conversion

**Modify**: `pom.xml` - Add Jackson/Gson if needed for JSON parsing

**Verify**: Compile. Unit tests for metadata loading, Y-flip, pixel conversion.

---

## ✅ Phase 1: Bravura Font Registration + Proof of Concept

**Goal**: Register Bravura font, create `drawBravuraGlyph()`, render treble clef with Bravura to prove the pipeline.

**Modify**:
- `BaseElementRenderer.java` - Add `BRAVURA_FONT` (from `Bravura.otf`, size 32f), add `drawBravuraGlyph()` method. Keep Fughetta fields for now.
- `ClefRenderer.java` - Switch treble clef from `drawFughettaGlyph(TREBLE_CLEF)` to `drawBravuraGlyph(SMuFLGlyph.G_CLEF)`. Adjust positioning for Bravura origin.

**Verify**: Compile + run. Treble clef renders correctly. Everything else unchanged.

---

## ✅ Phase 2: Collapse Note Subclass Hierarchy

**Summary**: Collapsed 20+ Note subclasses into a single concrete `Note` class with `NoteType` as the differentiator. All note-specific data (rectangles, durations, default Y positions) moved to `NoteType` enum. `NonNote` and `GraceSemiQuaver` retained for special behavior.

**Results**:
- Deleted 23 subclass files
- `Note` made concrete with `noteType` field
- `NoteType` enum now holds per-type data (rectangles, duration, default Y position)
- All external references updated to use `NoteType.XXX.newInstance()`
- Compilation succeeds
- Test infrastructure updated

**Details**: See [phase-2-collapse-hierarchy.md](phase-2-collapse-hierarchy.md)

---

## ✅ Phase 3: Engraving Defaults (parallel with Phase 2)

**Goal**: Replace all hardcoded stroke/thickness constants with values from `EngravingDefaults`.

**Modify**:
- `BaseElementRenderer.java` - Staff line, stem, ledger line strokes from metadata
- `BarRenderer.java` - Thin/thick barline, repeat strokes from metadata
- `BeamGroupRenderer.java` - Beam thickness, beam spacing from metadata
- `DynamicsRenderer.java` - Hairpin thickness from metadata
- `TieRenderer.java` - Tie endpoint/midpoint thickness from metadata
- `TupletRenderer.java` - Bracket thickness from metadata
- `EndingRenderer.java` - Ending line thickness from metadata
- `ArticulationRenderer.java` - Accent stroke from metadata

**Details**: See [phase-3-engraving-defaults.md](phase-3-engraving-defaults.md)

**Verify**: Compile + run. Visual differences should be sub-pixel.

---

## ✅ Phase 4: SMuFL-Driven Glyph Bounds

**Summary**: Replaced all Fughetta-derived bounding rectangles and layout constants with values computed from Bravura's SMuFL metadata (glyph bounding boxes, stem anchors, advance widths). Created `BravuraFontBoundsProvider`, rewired `NoteType` rectangle computation, updated `NoteColumnBuilder` and `VerticalStackingCalculator` constants, deleted `FughettaFontBoundsProvider`, and removed the `GRACE_SEMIQUAVER` note type.

**Details**: See [phase-4-smufl-glyph-bounds.md](phase-4-smufl-glyph-bounds.md)

---

## ✅ Phase 5: Note Head, Rest, and Accidental Glyph Rendering

**Summary**: Switched all core notation glyphs from Fughetta PUA codepoints to Bravura/SMuFL codepoints. Covers note heads, rests, accidentals (including parenthesized), augmentation dots, and key signature accidentals across NoteRenderer, RestRenderer, KeySignatureRenderer, TempoRenderer, and BeatChangeRenderer. Cleaned up unused Fughetta glyph constants. Visual verification complete (note: tempo marks deferred -- tempo changes not currently rendering).

**Details**: See [phase-5-note-rest-accidental-glyphs.md](phase-5-note-rest-accidental-glyphs.md)

---

## ✅ Phase 6: Stems, Flags, Tempo

**Summary**: Replaced Fughetta stem/flag rendering with SMuFL anchor-driven positioning and pre-composed glyphs. Stems now use filled rectangles with `stemUpSE`/`stemDownNW` anchor data for pixel-perfect placement. Flags use single SMuFL glyphs (`flag8thUp`, `flag16thUp`, `flag32ndUp`, etc.) instead of stacked Fughetta PUA codepoints. Tempo notes migrated to pre-composed SMuFL metronome glyphs (`metNoteQuarterUp`, etc.). Added `GraphicUtils.snapXToDevicePixel()` and pixel-aligned note origins to eliminate rounding disagreements between glyph and stem rendering. Note: tempo rendering is not currently visible due to a pre-existing layout system gap (TempoAttachments not wired through `VerticalStackingCalculator`).

**Details**: See [phase-6-stems-flags-tempo.md](phase-6-stems-flags-tempo.md)

---

## 📋 Phase 7: Custom-Drawn Elements → SMuFL Glyphs

**Summary**: Replace all remaining custom-drawn elements (Java2D shapes, Fughetta PUA codepoints, custom font glyphs) with SMuFL/Bravura glyph rendering across ArticulationRenderer, FermataRenderer, BarRenderer, TrillRenderer, GlissandoRenderer, TupletRenderer, and BeatChangeRenderer.

**Details**: See [phase-7-custom-to-smufl.md](phase-7-custom-to-smufl.md)

---

## 📋 Phase 7a: Screen Rendering Stroke Quality

**Goal**: Eliminate anti-aliasing artifacts on axis-aligned strokes for screen rendering. After converting staff lines, stems, barlines, and other elements to SMuFL glyphs in Phases 5-7, glyph rendering may still produce sub-pixel anti-aliasing. This phase ensures crisp on-screen rendering while preserving high-quality output for print/export.

**Strategy**:
1. **Experiment with `VALUE_STROKE_NORMALIZE`**: The `KEY_STROKE_CONTROL = VALUE_STROKE_NORMALIZE` hint added to `ScoreComponent.initGraphics()` may be sufficient after glyph conversion.
2. **If insufficient**: Implement glyph-specific rendering hints or a `StrokeFactory` utility for remaining drawn elements (beams, ties, hairpins, tuplet brackets).
3. **Architecture**: Support zoom transforms and fractional scale factors (Windows/Linux 125%, 150%, 175%).

**Modify**:
- `ScoreComponent.java` - `KEY_STROKE_CONTROL` hint already added, verify effectiveness after glyph conversion
- `StaffRenderer.java` - Replace `drawLine` + stroke with pixel-aligned `fillRect` (stroke AA is not suppressible for horizontal lines)
- (If needed) Create `StrokeFactory.java` - Provides screen-optimized vs. export-quality strokes for remaining drawn elements
- (If needed) Update renderers for beams, ties, hairpins, tuplet brackets to use pixel-aligned rendering

**Verify**: Visual inspection at 1x, 2x (Retina), and fractional scales. All glyphs and drawn elements should render crisply without grey fringing.

**Details**: See [phase-7a-stroke-quality.md](phase-7a-stroke-quality.md)

---

## ⏳ Phase 8: Remove Fughetta and Clean Up

**Goal**: Delete all Fughetta references and dead code.

**Modify**:
- `BaseElementRenderer.java` - Remove all Fughetta glyph constants, `MUSIC_FONT` (Fughetta), `drawFughettaGlyph()`. Rename `BRAVURA_FONT` -> `MUSIC_FONT`.
- `Note.java` - Remove `HOT_SPOT`, `NORMAL_IMAGE_WIDTH`, deprecated rect methods
- All renderers - Remove any lingering Fughetta-era positioning constants
- Fix all ripple references to `Note.HOT_SPOT`, `Note.NORMAL_IMAGE_WIDTH` (in `HorizontalAdjustment`, `LyricsRenderer`, `TupletRenderer`, `EndingRenderer`, etc.)

**Delete**:
- `src/main/resources/fonts/Fughetta.ttf`
- `src/main/resources/fonts/TupletNumbers.ttf`

**Verify**: `grep -r "Fughetta\|fughetta\|HOT_SPOT\|NORMAL_IMAGE_WIDTH\|uf0[0-9a-f]" src/` returns nothing.

---

## ⏳ Phase 9: Remove Legacy HiDPI Infrastructure

**Goal**: Remove pre-Java-9 HiDPI workaround classes. Modern Java (9+) handles HiDPI natively through `Graphics2D` transforms and `GraphicsConfiguration.createCompatibleImage()`.

**Context**: `HiDPIScaledGraphics`, `HiDPIScaledImage`, and `RetinaImage` manually implement 2x scaling and wrapped graphics delegation. This was necessary before Java had native HiDPI support. These classes are now redundant and interfere with proper stroke rendering by forcing `STROKE_PURE` on Retina displays.

**Delete**:
- `src/main/java/songscribe/ui/graphics/HiDPIScaledGraphics.java`
- `src/main/java/songscribe/ui/graphics/HiDPIScaledImage.java`
- `src/main/java/songscribe/ui/graphics/RetinaImage.java`

**Modify**:
- `GraphicUtils.java`:
    - Remove `HiDPIScaledImage` import and usage
    - Remove `RetinaImage.createFrom()` call (replace with direct image usage or `GraphicsConfiguration.createCompatibleImage()`)
    - Remove `instanceof HiDPIScaledImage` check and special half-size drawing logic
    - Simplify `setRenderingHints()` — remove `isRetina` branching for `STROKE_CONTROL` (use consistent strategy from Phase 3a)
    - Remove `isRetina` field and detection if no longer needed elsewhere

**Verify**:
- Compile succeeds
- Run on both 1x and 2x (Retina) displays
- Visual rendering identical to before removal
- Off-screen image rendering (export, caching) works correctly

---

## ⏳ Phase 10: Staff-Space Coordinate Unit Migration

**Goal**: All internal layout constants expressed in staff spaces. Pixel conversion only at render boundary.

**Modify**:
- `LayoutStylesheet.java` - Redefine MU-based constants in staff spaces. Change `px()` to convert from staff spaces.
- `Score.java` - Remove duplicate `NOTE_Y_OFFSET`, reference `LayoutStylesheet`
- `NoteColumnBuilder.java` - All constants in staff spaces
- `VerticalStackingCalculator.java` - Internal calculations in staff spaces
- All renderers - Convert staff-space values to pixels only at `Graphics2D` call sites

**Verify**: Full regression. Every visual element identical before and after (pure unit change).

---

## Phase Dependencies

```
Phase 0 (Metadata)
  |
  +---> Phase 1 (Font + Proof)
  |       |
  |       +---> Phase 2 (Collapse Hierarchy) --+
  |       |                                     |
  |       +---> Phase 3 (Engraving Defaults) ---+---> Phase 3a (Stroke Quality)
  |                                             |
  |                                             v
  +-----------------------------------> Phase 4 (SMuFL Bounds)
                                                |
                                                v
                                        Phase 5 (Note/Rest/Accidental Glyphs)
                                                |
                                                v
                                        Phase 6 (Stems/Flags/Tempo)
                                                |
                                                v
                                        Phase 7a-d (Custom -> Glyphs)
                                                |
                                                v
                                        Phase 8 (Remove Fughetta)
                                                |
                                                v
                                        Phase 9 (Remove Legacy HiDPI)
                                                |
                                                v
                                        Phase 10 (Staff-Space Units)
```

Phases 2 and 3 can proceed in parallel. Phase 4 requires both. Phase 3a should follow Phase 3.

## Verification

After each phase: `./scripts/compile.sh` succeeds, app runs via `./scripts/run-debug.sh`, visual spot-check.

After Phase 8: grep confirms no Fughetta references remain.

After Phase 9: full visual regression - every element type renders identically.
