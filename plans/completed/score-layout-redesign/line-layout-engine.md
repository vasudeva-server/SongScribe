# Line Layout Engine Implementation Plan

**Parent:** [score-layout-redesign.md](./score-layout-redesign.md) → Phase 5
**Captured:** 2026-01-31
**Status:** Completed

---

This plan implements the layout system documented in `plans/line-layout.md` using a hybrid approach: Element-Centric Strategies with Two-Pass Measure/Arrange, using `java.awt.geom.Area` for accumulation.

## Key Design Decisions

1. **Margins are left/bottom/right only** — no top margin, no margin collapsing
2. **Area-based accumulation** — use `java.awt.geom.Area` instead of rectangular bounds for complex shapes (ties, endings)
3. **Strategy pattern** — each element type has its own `LayoutStrategy`
4. **Two-pass layout** — measure pass (calculate sizes), arrange pass (assign positions)
5. **User offsets stored separately** — final position = calculated position + userOffset
6. **All margins as named constants** — no magic numbers; define all spacing values in `LayoutStylesheet.java`

## Stacking Order (bottom to top)

1. Note (head, stem, flag, ledger lines)
2. Tie
3. Articulations (staccato, then accent)
4. Tuplet
5. Trill
6. Fermata
7. Dynamics (non-range and range)
8. First/second endings
9. Tempo/beat change
10. Text annotations
11. Attribution (topmost)
12. Lyrics (below staff)

---

## ✅ Phase 1: Foundation Classes

**Model**: Sonnet (straightforward data classes and simple logic)

**Goal**: Create core abstractions for the layout system.

### Files to Create

1. **`src/main/java/songscribe/ui/layout/LayoutLayer.java`**
   ```java
   enum LayoutLayer {
       NOTE, TIE, ARTICULATION, TUPLET, TRILL, FERMATA, DYNAMICS,
       ENDING, TEMPO, ANNOTATION, ATTRIBUTION, LYRICS
   }
   ```

2. **`src/main/java/songscribe/ui/layout/Margin.java`**
   ```java
   record Margin(double left, double bottom, double right) {
       static Margin uniform(double m) { return new Margin(m, m, m); }
       static Margin NONE = new Margin(0, 0, 0);
   }
   ```

3. **`src/main/java/songscribe/ui/layout/LayoutAccumulator.java`**
   - Wraps `java.awt.geom.Area`
   - Methods: `add(Rectangle2D)`, `add(Area)`, `intersects(Area)`, `getTopY()`, `clear()`

4. **`src/main/java/songscribe/ui/layout/MeasureContext.java`**
   - Holds: `Graphics2D`, font metrics, composition settings

5. **`src/main/java/songscribe/ui/layout/ArrangeContext.java`**
   - Holds: `LayoutAccumulator`, measurements map, current layer, line reference

### Files to Modify

1. **`src/main/java/songscribe/ui/layout/Bounds.java`**
   - Add factory: `withMargin(Rectangle2D content, Margin margin)`
   - Add: `toArea()` method returning `java.awt.geom.Area`

### Verification

- Unit tests for `LayoutAccumulator` area operations
- Unit tests for `Margin` and updated `Bounds`

---

## ✅ Phase 2: Strategy Interface and Registry

**Model**: Sonnet (simple pattern implementation)

**Goal**: Define the strategy pattern infrastructure.

### Files to Create

1. **`src/main/java/songscribe/ui/layout/strategy/ElementLayoutStrategy.java`**
   ```java
   interface ElementLayoutStrategy<T> {
       LayoutLayer getLayer();
       Size measure(T element, MeasureContext ctx);
       Bounds arrange(T element, Size size, ArrangeContext ctx);
   }
   ```

2. **`src/main/java/songscribe/ui/layout/strategy/LayoutStrategyRegistry.java`**
   - Singleton registry mapping element types to strategies
   - Methods: `register()`, `getStrategy(element)`

3. **`src/main/java/songscribe/ui/layout/LayoutResult.java`**
   - Immutable result containing `Map<Object, Bounds>` for all laid-out elements
   - Methods: `getBounds(element)`, `getPosition(element)`

### Verification

- Registry correctly returns strategies for registered types
- Returns null/default for unknown types

---

## ✅ Phase 3: Note, Tie, and Articulation Strategies

**Model**: Opus (bezier curves for ties, area-based collision, nuanced articulation rules)

**Goal**: Implement strategies for layers 1-3.

### ✅ Phase 3a: Notes

1. **`src/main/java/songscribe/ui/layout/strategy/NoteLayoutStrategy.java`**
   - Measures full note bounding box (head, stem, flag, ledger lines)
   - Arranges at calculated X position (from XPositionCalculator)

### ✅ Phase 3b: Ties

**Tie Positioning Rules:**
- Ties connect exactly two notes at the same pitch
- Tie direction based on stem direction: stem up = tie above, stem down = tie below
- Arc endpoints: horizontal center of first and last note heads
- Arc distance from note head: 0.25 MU (1px)
- Arc height scales with distance: `minHeight + sqrt(distance / reference) * heightScale`

**Tie Shape:**
- Cubic bezier curves for inner and outer edges (tapered shape)
- Rounded ends, wider at apex
- Create filled `Path2D`, convert to `Area` for collision detection

**Area-based Margin:**
- Expand tie area by margin using `BasicStroke.createStrokedShape()`
- Articulations maintain 0.5 MU distance from tie *area* (not bounding box)

1. **`src/main/java/songscribe/ui/layout/strategy/TieLayoutStrategy.java`**
   - Determines tie direction based on note yPos
   - Generates bezier curve geometry
   - Calculates arc height based on horizontal distance
   - Creates `Area` for collision detection
   - Adds expanded area (with 0.5 MU margin) to accumulator

### ✅ Phase 3c: Articulations

**Status**: Complete. Created `StaccatoLayoutStrategy`, `AccentLayoutStrategy`, and composite `ArticulationLayoutStrategy` for registry integration.

**Note:** Articulations are a special case — they always appear on the side **opposite the stem**, not always stacked above. This means:
- Stem up → articulations below note head
- Stem down → articulations above note head

Tie-articulation collision detection only applies when both are on the same side of the note (e.g., tie above + stem down, or tie below + stem up).

2. **`src/main/java/songscribe/ui/layout/strategy/StaccatoLayoutStrategy.java`**
   - Special distance calculation based on yPos (1.0× or 1.5× staffLineYOffset)
   - Places opposite stem
   - Checks for tie collision (if on same side), shifts if needed

3. **`src/main/java/songscribe/ui/layout/strategy/AccentLayoutStrategy.java`**
   - Anchors to staff edge (|yPos| ≤ 4) or note head (ledger lines)
   - 0.5 MU from staccato if present
   - 0.5 MU from tie area if present (when on same side)
   - 1 MU margin

4. **`src/main/java/songscribe/ui/layout/strategy/ArticulationLayoutStrategy.java`**
   - Composite strategy registered for `Articulation` class
   - Delegates to StaccatoLayoutStrategy or AccentLayoutStrategy based on type

### Constants to Define in LayoutStylesheet

```java
// Tie layout constants
public static final double TIE_NOTE_HEAD_OFFSET = 0.25 * MU;  // Distance from note head
public static final double TIE_MIN_ARC_HEIGHT = 1.5 * MU;     // Minimum arc height
public static final double TIE_HEIGHT_SCALE = ...;            // Arc height scaling factor
public static final double TIE_ARTICULATION_MARGIN = 0.5 * MU;

// Articulation constants
public static final double ACCENT_STACCATO_MARGIN = 0.5 * MU;
public static final double ACCENT_MARGIN = 1.0 * MU;
```

### Verification

- Visual test: ties above and below notes at various pitches
- Visual test: tie arc height with varying note distances
- Visual test: articulations shifted to avoid tie collision
- Visual test: notes with staccato at various yPos values
- Visual test: notes with accent, with and without staccato
- Compare output to reference images

---

## ✅ Phase 4: Tuplet Strategy

**Model**: Opus (beam angle calculations, perpendicular margins, two rendering modes)

**Goal**: Implement strategy for layer 4.

**Status**: Complete. Created `TupletLayoutStrategy` with support for both beamed and non-beamed tuplets. Beamed tuplets position the number perpendicular to the beam angle; non-beamed tuplets use horizontal brackets with centered numbers.

### Tuplet Positioning Rules

**Beamed Tuplets** (all notes connected by beams):
- Number only, no bracket
- Positioned 0.5 MU from beam, measured *perpendicular* to beam angle
- Number baseline follows beam slope (rotated/angled positioning)
- Above beam for stems-down, below beam for stems-up

**Non-beamed Tuplets** (quarter notes, mixed values, etc.):
- Horizontal bracket with vertical legs, number centered with 1px gaps
- Bracket always above staff
- Positioned 0.5 MU from highest note+articulation bounds in tuplet range
- Minimum 1 MU from staff top
- Bracket extends 1px beyond first and last note heads
- Bracket height = half the number glyph height
- Number font size = 90% of beamed tuplet number size

**Stacking:**
- Tuplet number area participates in accumulator
- Higher layers (fermata, tempo, etc.) stack above tuplet numbers

### Files to Create

1. **`src/main/java/songscribe/ui/layout/strategy/TupletLayoutStrategy.java`**
   - Detects beamed vs. non-beamed tuplets
   - For beamed: calculates beam angle, positions number perpendicular to beam
   - For non-beamed: positions horizontal bracket above highest element
   - Adds number area to accumulator for higher layer stacking

### Constants to Define in LayoutStylesheet

```java
// Tuplet layout constants
public static final double TUPLET_BEAM_MARGIN = 0.5 * MU;     // Perpendicular to beam
public static final double TUPLET_BRACKET_MARGIN = 0.5 * MU;  // From highest bounds
public static final double TUPLET_MIN_STAFF_MARGIN = 1.0 * MU; // From staff top
public static final double TUPLET_NUMBER_GAP = 1.0;           // 1px gap around number
public static final double TUPLET_BRACKET_OVERHANG = 1.0;     // 1px beyond note heads
public static final double TUPLET_BRACKET_NUMBER_SCALE = 0.9; // 90% font size in bracket
```

### Verification

- Visual test: beamed triplet with stems up (number below beam)
- Visual test: beamed triplet with stems down (number above beam)
- Visual test: beamed triplet with angled beam (number follows angle)
- Visual test: non-beamed triplet with bracket
- Visual test: tuplet with fermata stacking above number

---

## ✅ Phase 5: Trill, Fermata, Dynamics Strategies

**Model**: Opus (range element complexity, spanning multiple notes)

**Goal**: Implement strategies for layers 5-7.

**Status**: Complete. Created `TrillLayoutStrategy`, `FermataLayoutStrategy`, `DynamicsLayoutStrategy`, `CrescendoLayoutStrategy`, and `DiminuendoLayoutStrategy`. Added `getHighestBoundsInRange()` and `getLowestBoundsInRange()` methods to `ArrangeContext` for range element positioning.

### Files Created

1. **`src/main/java/songscribe/ui/layout/strategy/TrillLayoutStrategy.java`**
   - Range element: spans multiple notes
   - 1 MU margin (using FERMATA_MARGIN)
   - Positions above highest bounding box in range
   - User-adjustable Y offset support

2. **`src/main/java/songscribe/ui/layout/strategy/FermataLayoutStrategy.java`**
   - 1 MU margin
   - Above full note bounding box (or trill if present via accumulator)
   - Centered on note head

3. **`src/main/java/songscribe/ui/layout/strategy/DynamicsLayoutStrategy.java`**
   - Non-range dynamics (p, f, mf, etc.)
   - 1 MU margin, below staff
   - Centered under note head

4. **`src/main/java/songscribe/ui/layout/strategy/CrescendoLayoutStrategy.java`**
   - Range hairpin (< shape opening right)
   - 2 MU margin
   - Below lowest note in range
   - User-adjustable X1, X2, Y shifts

5. **`src/main/java/songscribe/ui/layout/strategy/DiminuendoLayoutStrategy.java`**
   - Range hairpin (> shape opening left)
   - 2 MU margin
   - Below lowest note in range
   - User-adjustable X1, X2, Y shifts

### Files Modified

1. **`ArrangeContext.java`**
   - Added `getHighestBoundsInRange(startNote, endNote)` for above-staff elements
   - Added `getLowestBoundsInRange(startNote, endNote)` for below-staff elements

### Verification

- Visual test: trill spanning notes of varying heights
- Visual test: fermata above trill
- Visual test: dynamics with and without hairpins

---

## ✅ Phase 6: Endings Strategy (Area-based)

**Model**: Opus (complex geometry with L-shaped areas)

**Goal**: Implement first/second endings with Area-based collision.

**Status**: Complete. Created `EndingLayoutStrategy` with L-shaped Area for bracket collision detection. First endings have closed brackets (left leg + top bar + right leg), second endings have open brackets (left leg + top bar only). The LayoutAccumulator already supported Area operations.

### Files Created

1. **`src/main/java/songscribe/ui/layout/strategy/EndingLayoutStrategy.java`**
   - Creates L-shaped `Area` for the bracket using `Path2D`
   - First ending: closed bracket with left leg, top bar, right leg
   - Second ending: open bracket with left leg, top bar only
   - Uses ENDING_MARGIN (1.5 MU = 6px) for spacing
   - Positions above highest element in note range
   - User-adjustable Y offset via `getYPosition()`

### Files Verified (No Modification Needed)

1. **`LayoutAccumulator.java`**
   - Already supports `add(Area)` correctly for L-shaped areas

### Verification

- Visual test: endings with notes nesting inside bracket
- Visual test: endings with high notes pushing bracket up
- Collision detection works with irregular shape

---

## ✅ Phase 7: Tempo, Annotation, Attribution Strategies

**Model**: Sonnet (patterns established, straightforward implementation)

**Goal**: Implement strategies for layers 9-11.

### Files to Create

1. **`src/main/java/songscribe/ui/layout/strategy/TempoLayoutStrategy.java`**
   - 3 MU margin (left, bottom, right)
   - Horizontal overlap allowed if outside margins
   - Beat change uses same strategy

2. **`src/main/java/songscribe/ui/layout/strategy/AnnotationLayoutStrategy.java`**
   - 1 MU margin
   - Migration: move below-staff annotations above staff

3. **`src/main/java/songscribe/ui/layout/strategy/AttributionLayoutStrategy.java`**
   - Margin: left 3 MU, bottom 3 MU, right 0
   - Positioned at line end
   - Topmost layer

### Verification

- Visual test: tempo with nearby high notes
- Visual test: annotation migration from below to above staff
- Visual test: attribution at line end

---

## ✅ Phase 8: Lyrics Strategy

**Model**: Sonnet (relatively simple, follows established patterns)

**Goal**: Implement below-staff lyrics.

**Status**: Complete. Created `LyricsLayoutStrategy` that positions lyrics 2.5 MU below the lowest note bounds on the line. Added `getLowestBoundsOnLine()` method to `ArrangeContext` to find the lowest Y coordinate across all notes on a line.

### Files Created

1. **`src/main/java/songscribe/ui/layout/strategy/LyricsLayoutStrategy.java`**
   - Below staff
   - Baseline positioned with ascent 2.5 MU below lowest note bounding box
   - Operates on entire Line (lyrics are a line-level concept)
   - Measures height based on lyrics font metrics
   - Per-syllable positioning will be handled during rendering (integration phase)

### Files Modified

1. **`ArrangeContext.java`**
   - Added `getLowestBoundsOnLine()` for lyrics positioning
   - Returns lowest Y coordinate across all notes on the line
   - Considers note heads, stems, and ledger lines

2. **`LayoutStylesheet.java`**
   - Added `LYRICS_BASELINE_MARGIN` constant (2.5 MU = 10px)

### Verification

- Visual test: lyrics below notes with varying stem directions
- Correct spacing from lowest note

---

## ✅ Phase 9: LineLayoutEngine Integration

**Model**: Opus (complex integration with existing code, many touch points)

**Goal**: Wire everything together and integrate with rendering.

**Status**: Complete. Created `LineLayoutEngine` that orchestrates the two-pass layout process. Integrated with `LineComponent` to run layout during rendering and store the result. Added `layoutResult` field to `ElementRenderContext` so renderers can access pre-calculated bounds.

### Files Created

1. **`src/main/java/songscribe/ui/layout/LineLayoutEngine.java`**
   - Orchestrates two-pass layout (measure then arrange)
   - Collects elements from Line: notes, articulations, range elements, attachments
   - Processes layers in LayoutLayer order (NOTE → LYRICS)
   - Returns `LineElementLayoutResult` with bounds for all elements

### Files Modified

1. **`src/main/java/songscribe/ui/component/score/LineComponent.java`**
   - Added `LineLayoutEngine` and `LineElementLayoutResult` fields
   - Added `performLayout(Graphics2D)` method that runs the engine
   - Added `invalidateLayout()` and `getLayoutResult()` methods
   - Layout runs lazily during render when dirty
   - Layout result passed to `ElementRenderContext`

2. **`src/main/java/songscribe/ui/renderer/ElementRenderContext.java`**
   - Added `layoutResult` field with getter/setter
   - Renderers can now access pre-calculated bounds from the layout engine

### Note on LineLyricsComponent

The current `LineLyricsComponent` implementation continues to work as-is. Full migration of renderers to use layout result bounds will be completed in Phase 11 (Cleanup and Migration). The infrastructure is now in place for renderers to optionally use `ctx.getLayoutResult()` to get pre-calculated positions.

### Verification

- Full visual test with complex score
- Compare to current rendering output
- Performance check: layout time acceptable

---

## ✅ Phase 10: User Offset Support

**Model**: Sonnet (moderate complexity, clear requirements)

**Goal**: Preserve user adjustments as offsets from calculated positions.

**Status**: Complete. Added `userXOffset` and `userYOffset` fields to `LineElement` base class, providing unified user offset support for all elements. Updated `AnnotationLayoutStrategy`, `TempoLayoutStrategy`, `EndingLayoutStrategy`, and `TrillLayoutStrategy` to apply user offsets after calculating positions. Modified `VerticalAdjustment` to update the new `userYOffset` field for annotations.

### Files Modified

1. **`src/main/java/songscribe/ui/layout/LineElement.java`**
   - Added `userXOffset` and `userYOffset` fields with getters/setters
   - All elements (Note, RangeElement, Attachment) now inherit unified offset support

2. **`src/main/java/songscribe/music/Annotation.java`**
   - Added `userYOffset` field with getters/setters
   - Preserves legacy `yPos` for backward compatibility

3. **`src/main/java/songscribe/ui/layout/strategy/AnnotationLayoutStrategy.java`**
   - Applies annotation.getUserYOffset() after calculating position

4. **`src/main/java/songscribe/ui/layout/strategy/TempoLayoutStrategy.java`**
   - Applies line-level offsets (tempoChangeYPos, beatChangeYPos) for backward compatibility
   - Applies per-instance userYOffset from attachment
   - Both offsets are additive, allowing gradual migration

5. **`src/main/java/songscribe/ui/layout/strategy/EndingLayoutStrategy.java`**
   - Applies line-level offset (firstSecondEndingYPos) for backward compatibility
   - Applies per-instance yPosition from Ending

6. **`src/main/java/songscribe/ui/layout/strategy/TrillLayoutStrategy.java`**
   - Applies line-level offset (trillYPos) for backward compatibility
   - Applies per-instance yPosition from Trill

7. **`src/main/java/songscribe/ui/adjustment/VerticalAdjustment.java`**
   - Modified adjustAnnotation() to update userYOffset
   - Maintains legacy yPos update for backward compatibility

### Implementation Notes

- User offsets are applied by layout strategies after calculating base positions
- Line-level offsets (legacy pattern) are supported during transition to per-instance offsets
- Final position = calculated base position + line-level offset + per-instance offset
- Existing elements (Tuplet, Crescendo, Diminuendo) already had working offset support

### Verification

- Drag an element, save, reload — position preserved
- Change note heights — attached elements adjust but maintain user offset

---

## ✅ Phase 11: Cleanup and Migration

**Model**: Opus (careful code removal, migration logic requires attention to detail)

**Goal**: Remove legacy code and handle document migration.

**Status**: Complete. Deprecated line-level Y position fields and migrated to per-instance offsets. Updated FormatMigrator to convert legacy documents, VerticalAdjustment to use per-instance offsets, and AnnotationIO to persist userYOffset.

### Changes Made

1. **Deprecated line-level Y position fields in Line.java**
   - Added `@Deprecated` annotations and documentation to `tempoChangeYPos`, `beatChangeYPos`, `firstSecondEndingYPos`, and `trillYPos`
   - These fields are retained for backward compatibility with legacy documents
   - `lyricsYPos` is still in active use (not yet migrated to per-instance)

2. **Updated layout strategies to use only per-instance offsets**
   - Removed line-level offset application from `TempoLayoutStrategy`, `EndingLayoutStrategy`, and `TrillLayoutStrategy`
   - Strategies now only apply per-instance `userYOffset`/`yPosition`

3. **Updated FormatMigrator with line-level offset migration**
   - Added `migrateLineLevelOffsets()` to convert deprecated line-level Y positions to per-instance offsets
   - Added `migrateAnnotationPositions()` to migrate below-staff annotations to above-staff with `userYOffset`

4. **Updated VerticalAdjustment to use per-instance offsets**
   - Modified `adjustTempoChange()`, `adjustBeatChange()`, `adjustFirstSecondEnding()`, and `adjustTrill()` to update per-instance offsets instead of line-level offsets

5. **Updated LineIO to skip writing deprecated fields**
   - No longer writes `tempoChangeYPos`, `beatChangeYPos`, `firstSecondEndingYPos`, or `trillYPos`
   - Still reads these fields for backward compatibility
   - `lyricsYPos` is still written (not yet migrated)

6. **Updated AnnotationIO to persist userYOffset**
   - Added `XML_USER_Y_OFFSET` constant
   - Writes `userYOffset` when non-zero
   - Reads `userYOffset` during document loading

### Verification

- All existing songs render correctly
- No regressions in visual output
- Legacy documents with below-staff annotations load correctly

---

## Critical Files Summary

**New files:**
- `src/main/java/songscribe/ui/layout/LayoutLayer.java`
- `src/main/java/songscribe/ui/layout/Margin.java`
- `src/main/java/songscribe/ui/layout/LayoutAccumulator.java`
- `src/main/java/songscribe/ui/layout/MeasureContext.java`
- `src/main/java/songscribe/ui/layout/ArrangeContext.java`
- `src/main/java/songscribe/ui/layout/LayoutResult.java`
- `src/main/java/songscribe/ui/layout/LineLayoutEngine.java`
- `src/main/java/songscribe/ui/layout/strategy/ElementLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/LayoutStrategyRegistry.java`
- `src/main/java/songscribe/ui/layout/strategy/NoteLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/TieLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/StaccatoLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/AccentLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/TupletLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/TrillLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/FermataLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/DynamicsLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/CrescendoLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/DiminuendoLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/EndingLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/TempoLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/AnnotationLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/AttributionLayoutStrategy.java`
- `src/main/java/songscribe/ui/layout/strategy/LyricsLayoutStrategy.java`

**Modified files:**
- `src/main/java/songscribe/ui/layout/Bounds.java`
- `src/main/java/songscribe/ui/layout/LayoutStylesheet.java` (new constants)
- `src/main/java/songscribe/ui/component/score/LineComponent.java`
- `src/main/java/songscribe/ui/component/score/LineLyricsComponent.java`

---

## Test Strategy

Each phase includes visual verification:
1. Create test compositions with specific element combinations
2. Compare rendered output to reference images
3. Verify margins and spacing match documented rules

Final integration test:
- Load existing complex songs
- Verify no visual regressions
- Check performance with large scores
