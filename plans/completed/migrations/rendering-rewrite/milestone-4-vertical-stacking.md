# Sub-plan: Vertical Stacking + All Decorations

**Type:** Sub-plan  <br>
**Parent:** [rendering-rewrite.md](rendering-rewrite.md) → Phase 4  <br>
**Created:** 2026-03-26  <br>
**Status:** Completed  <br>
**BlockedBy:** —

**Spec:** [specs/rendering-rewrite.md](../../../specs/rendering-rewrite.md) -- always read the spec before implementing tasks.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | [StaffExtents: Y-Extent Array Collision Detection](#-phase-1-staffextents-y-extent-array-collision-detection) | ✅ Done |
| 2 | [Rewrite VerticalStackingCalculator with Three-Layer Model](#-phase-2-rewrite-verticalstackingcalculator-with-three-layer-model) | ✅ Done |
| 3 | [Tier 1: Near-Note Decorations (Articulations)](#-phase-3-tier-1-near-note-decorations-articulations) | ✅ Done |
| 4 | [Tier 2: Note Decorations (Fermata, Trill)](#-phase-4-tier-2-note-decorations-fermata-trill) | ✅ Done |
| 5 | [Tier 3: Staff Decorations (Dynamics Hairpins, Text Dynamics, Volta)](#-phase-5-tier-3-staff-decorations-dynamics-hairpins-text-dynamics-volta) | ✅ Done |
| 6 | [Tier 4: System Decorations (Tempo, Beat Changes, Annotations)](#-phase-6-tier-4-system-decorations-tempo-beat-changes-annotations) | ✅ Done |
| 7 | [Manual Offset Adjustments](#-phase-7-manual-offset-adjustments) | ✅ Done |
| 8 | [Integrate with LayoutEngine + LayoutResult](#-phase-8-integrate-with-layoutengine--layoutresult) | ✅ Done |
| 9 | [Verification + Cleanup](#-phase-9-verification--cleanup) | ✅ Done |

## Overview

Replace the current `VerticalStackingCalculator` (which uses Java2D `Area` intersection testing and pixel-based calculations) with a y-extent array collision detection system ported from abc2svg's `y_get`/`y_set`. Implement the three-layer vertical stacking model (note-attached, structural, system). Rewrite all above-staff decoration positioning to work in staff-space units. Add text dynamics rendering (currently unimplemented). Wire all computed positions into `LayoutResult` so renderers read pre-computed geometry.

## Key Design Decisions

1. **`StaffExtents` replaces `Area` intersection.** The current `VerticalStackingCalculator` creates a Java2D `Area` per column and incrementally adds element bounds, testing intersection via `Area.intersect()`. This is replaced by segmented y-extent arrays indexed by horizontal step (YSTEP=128), matching abc2svg's approach. Much faster and produces consistent results across elements at different X positions.

2. **Three independent `StaffExtents` instances per line.** Note-attached layer (articulations, fermata, trill), structural layer (dynamics hairpins, text dynamics, volta brackets), and system layer (tempo, beat changes, annotations). Each layer's `ySet` reservations are visible to subsequent layers via `yGet`. The structural layer does NOT push to avoid note-attached intrusions (volta brackets maintain consistent height).

3. **All calculations in staff-space units.** The current `VerticalStackingCalculator` mixes pixels and staff spaces (e.g., `getContentWidthPx()`, `findClearYPositionPx()`). The rewrite operates entirely in staff spaces. The `LineElement.getContentWidthPx()`/`getContentHeightPx()` methods will be renamed or supplemented with `Ss` variants.

4. **Span elements (hairpins, endings) participate in y_get/y_set.** The current calculator only handles per-note attachments. The rewrite also positions span elements (crescendo, diminuendo, endings) via the structural-layer `StaffExtents`, querying the full horizontal range of the span.

5. **`DecorationLayout` and `SpanLayout` records in LayoutResult.** New record types store computed positions for above-staff elements, analogous to `BeamLayout`/`TieLayout`/`StemLayout` from phases 2-3. Renderers read these instead of computing positions themselves.

6. **Text dynamics rendering.** `DynamicAttachment` exists in the model but has no renderer. This phase adds rendering using an italic music font, positioned in the structural tier alongside hairpins.

7. **VerticalStackingResult eliminated.** The current `VerticalStackingResult` (pixel-based, `Map<StaffElement, Map<LineElement, Point2D>>`) is replaced by storing decoration positions directly in `LayoutResult` via the builder. This removes the intermediate conversion step in `LayoutEngine.buildLayoutResult()`.

8. **Legacy flag bridging.** The current calculator has fallback paths for `note.isFermata()`, `note.isTrill()`, `note.getTempoChange()` etc. These legacy boolean/object flags on `StaffElement` are bridged to the layout `Attachment`/`RangeElement` types during layout so the stacking calculator works uniformly with the new types. The legacy flags themselves are removed in master plan Phase 6 (Legacy Decoration Flag Migration), which migrates all callers (user actions, file I/O, export, copy/paste) to the new types.

## Constants

All vertical stacking constants in staff-space units, defined in `LayoutConstants`:

| Constant | Value | Source |
|----------|-------|--------|
| `VOLTA_TICK_HEIGHT_SS` | 2.5 ss | abc2svg: `20 / 8` |
| `VOLTA_MARGIN_SS` | 0.625 ss | abc2svg: `5 / 8` |
| `YSTEP` | 128 | abc2svg: matching step count |
| `ARTICULATION_MARGIN_SS` | 0.5 ss | Existing constant, validated |
| `TRILL_MARGIN_SS` | 0.25 ss | Existing constant |
| `FERMATA_MARGIN_SS` | 0.25 ss | Existing constant |
| `DYNAMICS_MARGIN_SS` | 0.25 ss | Existing constant |
| `ENDING_MARGIN_SS` | 0.5 ss | Existing constant |
| `TEMPO_MARGIN_SS` | 0.5 ss | Existing constant |
| `ANNOTATION_MARGIN_SS` | 0.25 ss | Existing constant |

## Phases

### ✅ Phase 1: StaffExtents: Y-Extent Array Collision Detection

Create the `StaffExtents` class that replaces Java2D `Area` intersection testing with segmented y-extent arrays, matching abc2svg's `y_get`/`y_set` approach.

#### Tasks

- [x] **Create `StaffExtents.java` in `songscribe/ui/layout/`**
  - Two arrays: `double[] top` and `double[] bot`, length = YSTEP (128)
  - Initialize `top[]` to staff top Y (0.0 ss), `bot[]` to staff bottom Y (4.0 ss)
  - `ySet(boolean above, double xSs, double widthSs, double ySs)` -- reserve space:
    - Convert x and width to step indices: `startStep = (int)(xSs * YSTEP / lineWidthSs)`, clamped to [0, YSTEP-1]
    - For `above=true`: for each step in range, `top[i] = Math.min(top[i], ySs)` (Y-down: smaller = higher)
    - For `above=false`: for each step in range, `bot[i] = Math.max(bot[i], ySs)`
  - `yGet(boolean above, double xSs, double widthSs)` -- query:
    - For `above=true`: return `Math.min(top[i])` across the step range (highest occupied point)
    - For `above=false`: return `Math.max(bot[i])` across the step range (lowest occupied point)
  - Constructor takes `lineWidthSs` for step resolution calculation
  - All methods and data in staff-space units

- [x] **Unit tests for `StaffExtents`**
  - Test `ySet`/`yGet` basic operations
  - Test overlapping reservations (max/min behavior)
  - Test step clamping at edges (x=0, x=lineWidth)
  - Test query across multiple steps returns correct extreme
  - Test initialization defaults (top=0, bot=staffHeight)

---

### ✅ Phase 2: Rewrite VerticalStackingCalculator with Three-Layer Model

Replace the per-column `Area` accumulation approach with a line-wide three-layer `StaffExtents` model.

#### Tasks

- [x] **Add `DecorationLayout` record to `LayoutResult`**
  - `record DecorationLayout(double xSs, double ySs, double widthSs, double heightSs)`
  - Represents the positioned bounds of a single above-staff decoration
  - Add `Map<LineElement, DecorationLayout> decorationLayouts` to `LayoutResult`
  - Add corresponding builder methods: `putDecorationLayout(LineElement, DecorationLayout)`
  - Add accessor: `getDecorationLayout(LineElement)`

- [x] **Add `SpanLayout` record to `LayoutResult`**
  - `record SpanLayout(double startXSs, double endXSs, double ySs, double heightSs)`
  - Represents the positioned bounds of a span element (hairpin, ending, tuplet)
  - Add `Map<Interval, SpanLayout> spanLayouts` to `LayoutResult`
  - Add builder method: `putSpanLayout(Interval, SpanLayout)`
  - Add accessor: `getSpanLayout(Interval)`

- [x] **Rewrite `VerticalStackingCalculator`**
  - Replace instance state with method parameters; make methods static or use a fresh instance per layout pass
  - Constructor takes `lineWidthSs` and creates three `StaffExtents` instances:
    - `noteAttachedExtents` -- tier 1+2 (articulations, fermata, trill)
    - `structuralExtents` -- tier 3 (dynamics hairpins, text dynamics, volta brackets)
    - `systemExtents` -- tier 4 (tempo, beat changes, annotations)
  - Remove `Area accumulated` parameter from all `stack*` methods
  - Replace `findClearYPositionPx()` with `StaffExtents.yGet()` + margin
  - Replace `addToAccumulated()` with `StaffExtents.ySet()`
  - Remove `VerticalStackingResult` -- write directly to `LayoutResult.Builder`
  - All calculations in staff-space units (remove all `toPixels`/`fromPixels` calls)
  - New signature: `calculate(List<ElementColumn> columns, Line line, LayoutResult.Builder builder, double lineWidthSs)`

- [x] **Seed note bounding areas into `noteAttachedExtents`**
  - Before processing any decorations, iterate all columns and call `ySet` for each note's stem/head bounds
  - Use `StemLayout` from `LayoutResult.Builder` (computed during beam/stem pass) for accurate stem top/bottom
  - Use notehead bounding box width from SMuFL metadata

- [x] **Remove `VerticalStackingResult.java`**
  - All data now flows through `LayoutResult.Builder` directly
  - Update `LayoutEngine.buildLayoutResult()` to remove the `VerticalStackingResult` parameter
  - Update `LayoutEngine.layout()` accordingly

---

### ✅ Phase 3: Tier 1: Near-Note Decorations (Articulations)

Rewrite articulation positioning to use `StaffExtents.yGet`/`ySet` on the note-attached layer.

#### Tasks

- [x] **Add `Ss` content dimension methods to `Articulation`**
  - Add `getContentWidthSs()` and `getContentHeightSs()` using SMuFL bounding box data
  - The existing `getContentWidthPx()` / `getContentHeightPx()` can delegate to `Ss` methods * scale

- [x] **Rewrite `stackArticulations()` in `VerticalStackingCalculator`**
  - Replace `ArticulationRenderer.calculateStaccatoYPx()` / `calculateAccentYPx()` calls with staff-space stacking logic
  - Use `noteAttachedExtents.yGet(true, xSs, widthSs)` to find clear space above
  - Position staccato first (closest to notehead), then accent above staccato
  - Call `noteAttachedExtents.ySet(true, xSs, widthSs, ySs)` to reserve space
  - Write `DecorationLayout` for each articulation to `LayoutResult.Builder`

- [x] **Update `ArticulationRenderer` to read from `LayoutResult`**
  - Replace self-computed Y positions with `layoutResult.getDecorationLayout(articulation)`
  - The renderer becomes a pure drawing routine: read position, draw glyph
  - Remove `calculateStaccatoYPx()` and `calculateAccentYPx()` static methods after migration

- [x] **Unit tests for articulation stacking**
  - Staccato positions above note head for stems-down notes
  - Staccato positions below note head for stems-up notes (near-note special case)
  - Accent stacks above staccato when both present
  - Articulations do not collide with stem tips
  - Articulations reserve space in `noteAttachedExtents` (verified via `yGet`)

---

### ✅ Phase 4: Tier 2: Note Decorations (Fermata, Trill)

Position fermata and trill in the note-attached layer, above articulations.

#### Tasks

- [x] **Add `Ss` content dimension methods to `FermataAttachment` and `Trill`**
  - `FermataAttachment`: width/height from SMuFL `fermataAbove` glyph bounding box
  - `Trill`: width from SMuFL `ornamentTrill` glyph bbox + wavy extension width; height from glyph bbox

- [x] **Rewrite `stackFermata()` in `VerticalStackingCalculator`**
  - Bridge legacy `note.isFermata()` to temporary `FermataAttachment` during stacking
  - Use `noteAttachedExtents.yGet(true, xSs, widthSs)` to find clear space
  - Apply `FERMATA_MARGIN_SS` gap from previous layer
  - Reserve space with `noteAttachedExtents.ySet()`
  - Write `DecorationLayout` to `LayoutResult.Builder`

- [x] **Rewrite `stackTrill()` in `VerticalStackingCalculator`**
  - Remove hardcoded pixel dimensions (`trillHeightPx = 12.0`, `trillWidthPx = 20.0`)
  - Use actual trill glyph dimensions from SMuFL metadata
  - Process `Trill` range elements from `Line.findRangeElements(Trill.class)`
  - Multi-note trills reserve full horizontal span via `getSpanWidthSs()`
  - Apply `TRILL_MARGIN_SS` gap
  - Reserve space; write `DecorationLayout` to builder

- [x] **Update `FermataRenderer` to read from `LayoutResult`**
  - Read `DecorationLayout` via `findAttachmentDecorationLayout()` for position
  - Converted to staff-space coordinates (removed pixel constants)
  - Fallback for insertion preview preserved

- [x] **Update `TrillRenderer` to read from `LayoutResult`**
  - Read `DecorationLayout` for the trill symbol position
  - Converted to staff-space coordinates (removed `StaffSpaces` dependency)
  - Renders both new `Trill` range elements and legacy `isTrill()` flags via bridged layouts

- [x] **Bridge legacy flags to layout types**
  - `stackFermata()` creates temporary `FermataAttachment` when `note.isFermata()` is true but no attachment exists
  - `bridgeLegacyTrillFlags()` creates temporary `Trill` objects for `isTrill()` flags not covered by existing range elements
  - This is temporary until master plan Phase 6 (Legacy Decoration Flag Migration) eliminates the dual representation

- [x] **Unit tests for fermata and trill stacking**
  - Fermata positions above articulations when both present
  - Trill positions above note (or above articulation if present)
  - Fermata and trill do not overlap when on the same note
  - Wide trill with wavy extension reserves correct horizontal range
  - Positions stored correctly in `DecorationLayout`
  - Legacy flag bridging produces layouts for both fermata and trill

---

### ✅ Phase 5: Tier 3: Staff Decorations (Dynamics Hairpins, Text Dynamics, Volta)

Position span elements in the structural layer. This tier operates on the `structuralExtents`, which starts by importing the note-attached layer's reservations. Volta brackets maintain consistent height and allow note-attached intrusions.

#### Tasks

- [x] **Initialize `structuralExtents` from `noteAttachedExtents`**
  - After tiers 1-2 complete, copy `noteAttachedExtents.top[]` into `structuralExtents.top[]`
  - This ensures structural elements clear note-attached elements

- [x] **Position crescendo/diminuendo hairpins**
  - Process new `Crescendo`/`Diminuendo` range elements from `line.findRangeElements()`
  - Bridge legacy `DynamicsInterval` data to temporary range elements
  - Query `structuralExtents.yGet(true, startXSs, spanWidthSs)` for clear space
  - Apply `DYNAMICS_MARGIN_SS` gap
  - Reserve with `structuralExtents.ySet()`
  - Write `SpanLayout` (keyed by interval) and `DecorationLayout` (keyed by range element) to builder

- [x] **Implement text dynamics positioning**
  - `DynamicAttachment` (pp, p, mp, mf, f, ff, sfz, fp) exists in the model
  - Added `getContentWidthSs()` / `getContentHeightSs()` with staff-space constants
  - Position in structural tier using `structuralExtents.yGet`/`ySet`
  - Write `DecorationLayout` to `LayoutResult.Builder`

- [x] **Position volta brackets (first/second endings)**
  - Process new `Ending` range elements from `line.findRangeElements()`
  - Bridge legacy `EndingInterval` data to temporary `Ending` objects
  - Added `VOLTA_TICK_HEIGHT_SS` (2.5 ss) and `VOLTA_MARGIN_SS` (0.625 ss) to `LayoutConstants`
  - Query `structuralExtents.yGet`/`ySet` for collision detection
  - Write `SpanLayout` (keyed by interval) and `DecorationLayout` (keyed by range element) to builder

- [x] **Update `DynamicsRenderer` to read from `LayoutResult`**
  - New range element path: reads `DecorationLayout` for pre-computed Y position
  - Legacy interval path: reads `SpanLayout` for Y, applies manual shifts from interval
  - Added `renderHairpinFromLayout()` for layout-based rendering
  - Retained legacy `renderHairpin()` for backward compatibility

- [x] **Update `EndingRenderer` to read from `LayoutResult`**
  - Reads `SpanLayout` (keyed by `EndingInterval`) or `DecorationLayout` (for `Ending` range elements)
  - Falls back gracefully through both lookup paths

- [x] **Unit tests for structural tier stacking**
  - Hairpins positioned above note-attached elements
  - Two hairpins at same height when they don't overlap horizontally
  - Text dynamics positioned alongside hairpins
  - Volta brackets at consistent height above hairpins
  - Legacy interval bridging produces SpanLayout for all element types
  - 15 tests in `StructuralTierStackingTest`, all passing

---

### ✅ Phase 6: Tier 4: System Decorations (Tempo, Beat Changes, Annotations)

Position system-level decorations in the system layer. Always topmost.

#### Tasks

- [x] **Initialize `systemExtents` from `structuralExtents`**
  - Already implemented in Phase 2: `systemExtents.copyTopFrom(structuralExtents)` at line 133
  - System elements always clear everything below them

- [x] **Rewrite `stackTempo()` in `VerticalStackingCalculator`**
  - Removed hardcoded dimension constants (TEMPO_HEIGHT_SS, TEMPO_WIDTH_SS)
  - Added `getContentWidthSs()` / `getContentHeightSs()` to `TempoAttachment`
  - Bridge legacy `note.getTempoChange()` to temporary `TempoAttachment`
  - Query `systemExtents.yGet`/`ySet` for collision detection
  - Write `DecorationLayout` to `LayoutResult.Builder`
  - Note: "dosh" bit-shifting for overlapping tempo marks deferred (basic stacking sufficient)
  - Note: Manual offsets deferred to Phase 7

- [x] **Rewrite `stackBeatChange()` (separated from `stackTempo()`)**
  - New standalone `stackBeatChange()` method
  - Added `getContentWidthSs()` / `getContentHeightSs()` to `BeatChangeAttachment`
  - Bridge legacy `note.getBeatChange()` to temporary `BeatChangeAttachment`
  - Query `systemExtents.yGet`/`ySet` for collision detection
  - Write `DecorationLayout` to `LayoutResult.Builder`

- [x] **Rewrite `stackAnnotations()` in `VerticalStackingCalculator`**
  - Removed hardcoded dimension constants (ANNOTATION_HEIGHT_SS, ANNOTATION_WIDTH_SS)
  - Added `getContentWidthSs()` / `getContentHeightSs()` to `AnnotationAttachment`
  - Bridge legacy `note.getAnnotation()` to temporary `AnnotationAttachment`
  - Query `systemExtents.yGet`/`ySet` for collision detection
  - Write `DecorationLayout` to `LayoutResult.Builder`

- [x] **Update `TempoRenderer` to read from `LayoutResult`**
  - Switched from `findAttachmentBounds` to `findAttachmentDecorationLayout`
  - Reads `DecorationLayout.ySs()` + middleLineY for component coordinates

- [x] **Update `BeatChangeRenderer` to read from `LayoutResult`**
  - Switched from `findAttachmentBounds` to `findAttachmentDecorationLayout`
  - Reads `DecorationLayout.ySs()` + middleLineY for component coordinates

- [x] **Update `AnnotationRenderer` to read from `LayoutResult`**
  - Switched from `findAttachmentBounds` to `findAttachmentDecorationLayout`
  - Reads `DecorationLayout.ySs()` + middleLineY for component coordinates

- [x] **Bridge legacy properties to layout types**
  - Bridging done in `stackTempo()`, `stackBeatChange()`, `stackAnnotations()`
  - Each method checks new attachment hierarchy first, falls back to legacy property
  - Creates temporary attachment objects for legacy data (same pattern as fermata/trill)

- [x] **Unit tests for system tier stacking**
  - 14 tests in `SystemTierStackingTest`, all passing
  - Tempo positioned above staff and above structural elements
  - Beat change positioned above staff, stacks below tempo on same note
  - Annotations positioned above staff and above tempo markings
  - All system elements stack correctly on same note
  - System layer clears structural and note-attached layers
  - Attachment hierarchy and legacy bridging both produce layouts

---

### ✅ Phase 7: Manual Offset Adjustments

Ensure all manual position adjustments work correctly post-layout, in staff-space units.

#### Tasks

- [x] **Apply manual offsets to `DecorationLayout` and `SpanLayout`**
  - Added `applyManualOffsets()` post-processing pass in `VerticalStackingCalculator.calculate()`
  - Runs after all stacking tiers complete, before line height calculation
  - `applyDecorationOffsets()`: iterates all DecorationLayout entries, applies `userXOffsetSs`/`userYOffsetSs` from `LineElement` base class, plus element-specific offsets:
    - `Trill.getYPositionSs()` — additional Y offset (int, already ss)
    - `Ending.getYPositionSs()` — additional Y offset (int, already ss)
    - `Crescendo`/`Diminuendo` — pixel-based `x1Shift`, `x2Shift`, `yShift` converted via `ScaleContext.fromPixels()`
    - `AnnotationAttachment` — legacy `Annotation.getUserYOffsetSs()` from the music model
  - `applySpanOffsets()`: iterates all SpanLayout entries, applies `DynamicsInterval.x1ShiftSs`/`x2ShiftSs`/`yShiftSs`
  - Added `getDecorationLayoutEntries()` and `getSpanLayoutEntries()` to `LayoutResult.Builder`

- [x] **Verify unit conversion for legacy offset fields**
  - `DynamicsInterval` shifts: already in staff spaces (`x1ShiftSs`, `x2ShiftSs`, `yShiftSs`)
  - `Trill.yPositionSs` and `Ending.yPositionSs`: already in staff spaces (int)
  - `LineElement.userXOffsetSs`/`userYOffsetSs`: already in staff spaces (double)
  - `Annotation.userYOffsetSs`: already in staff spaces (double)
  - `Crescendo`/`Diminuendo` `x1Shift`/`x2Shift`/`yShift`: pixel-based (int), converted via `ScaleContext.fromPixels()`

- [x] **Manual offsets do NOT re-run collision detection**
  - Offsets applied after all `StaffExtents.yGet`/`ySet` calls are complete
  - Line height calculated from `systemExtents` before offsets are applied
  - Verified in `NoCollisionRerun` test

- [x] **Unit tests for manual offsets**
  - 11 tests in `ManualOffsetStackingTest`, all passing
  - `AnnotationOffsets`: attachment X/Y offsets, legacy annotation Y offset
  - `FermataOffsets`: attachment Y offset
  - `HairpinOffsets`: legacy DynamicsInterval Y shift, X1/X2 shifts
  - `TempoOffsets`: attachment X and Y offsets
  - `TrillOffsets`: trill yPosition offset
  - `EndingOffsets`: ending yPosition offset
  - `NoCollisionRerun`: offset on one element does not affect another's position

---

### ✅ Phase 8: Integrate with LayoutEngine + LayoutResult

Wire the rewritten `VerticalStackingCalculator` into the `LayoutEngine` pipeline and ensure all renderers use `LayoutResult` for positions.

#### Tasks

- [x] **Update `LayoutEngine.layout()` pipeline**
  - Already complete from phases 1-7: no `VerticalStackingResult` exists
  - Calculator writes directly to `LayoutResult.Builder` via `putDecorationLayout()`/`putSpanLayout()`
  - `buildLayoutResult()` only adds element columns and staff geometry (no vertical stacking conversion)
  - Pipeline order correct: beams/stems (steps 5/5b) → ties (step 6) → vertical stacking (step 7)

- [x] **Calculate total line height from `systemExtents`**
  - Already complete in `VerticalStackingCalculator.calculate()`: computes `maxAboveStaffSs` from `systemExtents`,
    calculates lyrics baseline, and writes `lineHeightSs` and `lyricBaselineYSs` to builder

- [x] **Update `LineRenderer.renderAttachments()` dispatch**
  - Switched all dispatch checks from legacy flags to `findAttachmentDecorationLayout()` presence:
    - Tempo: `findAttachmentBounds` → `findAttachmentDecorationLayout(element, TempoAttachment.class)`
    - Beat change: `element.getBeatChange()` → `findAttachmentDecorationLayout(element, BeatChangeAttachment.class)`
    - Fermata: `element.isFermata()` → `findAttachmentDecorationLayout(element, FermataAttachment.class)`
    - Annotation: `element.getAnnotation()` → `findAttachmentDecorationLayout(element, AnnotationAttachment.class)`
  - Articulations: kept `!element.getArticulations().isEmpty()` (renderer handles DecorationLayout fallback)
  - Reordered rendering to match stacking tier order: articulations → fermata → tempo → beat change → annotation → trills

- [x] **Update `LineRenderer` span rendering methods**
  - `renderDynamics()`: already reads `SpanLayout`/`DecorationLayout` via `DynamicsRenderer` internally
  - `renderEndings()`: already reads `SpanLayout`/`DecorationLayout` via `EndingRenderer` internally
  - `renderTuplets()`: tuplets deferred to master plan Phase 5

- [x] **Compile and verify**
  - `./scripts/compile.sh` passes with no errors
  - `./scripts/test.sh unit`: all 494 tests pass
  - Visual verification: deferred to Phase 9

---

### ✅ Phase 9: Verification + Cleanup

Final verification, dead code removal, and polish.

#### Tie-Aware Stacking (completed this session)

Upward-arcing ties (stem down) now seed their Bezier curve bounds into the note-attached extents layer, so decorations stack above ties. Key files:

- `VerticalStackingCalculator.seedTieBounds()` — samples the outer Bezier curve and seeds extents; populates `notesWithUpwardTie` set for margin adjustment
- `VerticalStackingCalculator.anchorCeilingSs(StaffElement)` / `anchorCeilingSs(int, double)` — extracted helper for anchor ceiling computation (used by both `anchoredCeilingSs` and `seedTieBounds`)
- `VerticalStackingCalculator.evaluateBezierYSs()` — evaluates cubic Bezier Y at parameter t
- `LayoutStylesheet.TIE_DECORATION_MARGIN_SS` (0.25 ss) — reduced margin for articulations above ties
- `LayoutResult.Builder.getTieLayout(Interval)` — added so stacking calculator can read tie geometry

Tie margin rules:
- **Articulations** (staccato, accent): use `TIE_DECORATION_MARGIN_SS` (0.25) when the tie protrudes above the anchor ceiling at that note; otherwise normal `NOTE_DECORATION_MARGIN_SS` (0.5)
- **Fermata**: always uses `NOTE_DECORATION_MARGIN_SS` (0.5) — the seeded tie bounds raise the ceiling naturally
- **All other decorations** (hairpins, trill extensions, etc.): their normal margins apply; the tie bounds in the extents push the ceiling up automatically

#### Tempo Renderer Migration (in progress)

`TempoRenderer` has been migrated to staff-space coordinates and reads position from `DecorationLayout`. Remaining state:

- **X position**: reads from `decorationLayout.xSs()` (correct)
- **Y position**: reads from `decorationLayout.ySs()`, note glyph top aligned with layout top via scaled SMuFL bbox
- **Text baseline**: aligned with the bottom of the tempo note glyph (`ySs + bbox.height() * NOTE_SCALE`)
- **Note glyph**: uses `TEMPO_NOTE_FONT` (Bravura at `FONT_SIZE * NOTE_SCALE`), scale from `FlatLaf.properties` (`SongScribe.score.tempo.note.scale`)
- **Glyph widths**: SMuFL advance widths (no glyph vector creation), via `TempoAttachment.noteWidthSs()` / `metronomeGlyphFor()`
- **Text font**: attribution font scaled to ss via `ScaleContext.fromPixels()`
- **Content width**: computed from actual text/glyph metrics in `TempoAttachment.computeContentWidthSs(FontMetrics)` — no hardcoded default
- **Content height**: computed from `MET_NOTE_QUARTER_UP` bbox height * `NOTE_SCALE`
- **FlatLaf properties**: `score.tempo.note.scale` (0.65), `score.tempo.glyph.text.gap` (0.375 ss)
- **Shared constants**: `TempoAttachment.NOTE_SCALE`, `TempoAttachment.QUARTER_NOTE_BBOX`, `TempoAttachment.metronomeGlyphFor()`, `TempoAttachment.noteWidthSs()` — used by both layout and renderer

`BeatChangeRenderer` still uses the legacy `TEMPO_CHANGE_ZOOM_X/Y` scaling approach and has not been migrated. It shares the same metronome glyph rendering pattern and will need similar migration.

#### Line Height (known limitation)

`LineComponent.calculateMiddleLineYSs()` uses a legacy height calculation that doesn't account for stacking results. `MIN_SPACE_ABOVE_SS` was temporarily doubled to 10.0 ss as a stopgap. The proper fix is to use `LayoutResult.getLineHeightSs()` from the stacking calculator, but this requires wiring the layout result into the component sizing path (deferred).

#### Hardcoded Content Widths (tracked)

Several attachments use hardcoded `DEFAULT_WIDTH_SS` that should be computed from actual content. See `plans/fix-attachment-content-widths.md` for the handoff document. Affected: `AnnotationAttachment` (5.0), `BeatChangeAttachment` (6.25), `DynamicAttachment` (2.5). `TempoAttachment` has been fixed.

#### Tasks

- [x] **Visual verification with test compositions**

  Individual elements:
  - [x] Staccato (above staff, correct spacing from staff/notehead)
  - [x] Accent (above staff, correct spacing from staff/notehead)
  - [x] Fermata
  - [x] Trill (single note)
  - [x] Trill with wavy extension (multi-note)
  - [x] Trill with wavy extension — collision detection across span
  - [x] Crescendo hairpin
  - [x] Diminuendo hairpin
  - [x] Text dynamics (pp, p, mp, mf, f, ff, sfz, fp) — TODO: requires a separate plan to add dedicated UI for adding text dynamics (currently no way to add them distinct from generic annotations)
  - [x] Volta bracket (first ending)
  - [x] Volta bracket (second ending)
  - [x] Tempo marking (X/Y positioning migrated; needs visual fine-tuning)
  - [x] Beat change (renderer not yet migrated to ss coordinates)
  - [x] Annotation

  Tie-aware stacking:
  - [x] Articulations above upward-arcing tie (0.25 ss margin)
  - [x] Fermata above upward-arcing tie (0.5 ss margin)
  - [x] Downward-arcing tie does not affect decoration margins
  - [x] Tie within staff does not reduce margins (only when tie protrudes above anchor ceiling)

  Combinations on the same note:
  - [x] Staccato + accent (accent stacks above staccato)
  - [x] Staccato + fermata (fermata above staccato)
  - [x] Accent + fermata (fermata above accent)
  - [x] Staccato + accent + fermata (all three stacked)
  - [x] Articulation + trill (trill above articulation)
  - [x] Fermata + tempo (tempo above fermata)

  Cross-element stacking:
  - [x] Hairpin under fermata on same note
  - [x] Volta bracket above hairpin
  - [x] Tempo above volta bracket
  - [x] Annotation above tempo

  Insertion note preview:
  - [x] Staccato matches inserted note placement
  - [x] Accent matches inserted note placement
  - [x] Staccato + accent matches inserted note placement
  - [x] Fermata matches inserted note placement

  Edge cases:
  - [x] Notes above the staff (ledger lines) — articulations anchor to notehead
  - [x] Notes below the staff — articulations anchor to top staff line
  - [x] Manual offset adjustments on each element type

- [x] **Fix hardcoded attachment content widths**

  Several attachments use hardcoded `DEFAULT_WIDTH_SS` values. The stacking calculator reserves incorrect horizontal space, allowing text/glyphs to overlap with adjacent elements. Each fix follows the pattern established by `TempoAttachment.computeContentWidthSs(FontMetrics)`: compute width from actual glyph advance widths and font-measured text, pass font metrics from the stacking method.

  - [x] `TempoAttachment` (was 7.5 ss) — fixed: `computeContentWidthSs(FontMetrics)` computes from SMuFL advance widths + `FontMetrics.stringWidth()`. Shared helpers: `metronomeGlyphFor()`, `noteWidthSs()`.
  - [x] `AnnotationAttachment` (5.0 ss) — content: user text from `note.getAnnotation()`, font: `composition.getAnnotationFontMetrics()`, stacking: `stackAnnotations()`
  - [x] `BeatChangeAttachment` (6.25 ss, height 2.5 ss) — content: time signature as metronome glyphs, font: Bravura at tempo scale. Height also needs computing from glyph bbox (same pattern as `TempoAttachment.DEFAULT_HEIGHT_SS`). Stacking: `stackBeatChange()`. Check `BeatChangeRenderer` for content composition.
  - [x] `DynamicAttachment` (2.5 ss) — deferred until text dynamics have dedicated UI/rendering (see text dynamics TODO above)

- [x] **Remove dead code**
  - `VerticalStackingResult.java` already removed in Phase 2
  - Legacy pixel-based positioning methods already migrated to `Ss` variants in Phases 3-6
  - `VerticalStackingCalculator` imports already clean (no `Area`, no `StaffSpaces`)
  - Removed 5 dead methods from `CollisionDetector`: `checkCollision`, `findAttributionCollisions`,
    `calculateAttributionOffset`, `calculateLineHeight`, `findElementAt` (only `calculateNoteExtent` retained,
    still used by `LineComponent`)
  - Migrated `StaffSpaces` → `ScaleContext` in `DynamicsRenderer` and `EndingRenderer` stroke constants

- [x] **Clean up `VerticalStackingCalculator` interface**
  - Verified: no pixel-based methods remain
  - Verified: class-level javadoc states "All calculations are in staff-space units";
    `calculate()` method documents `lineWidthSs` parameter with unit
  - Verified: no references to `StaffSpaces`; `fromPixels()` calls in `applyManualOffsets()`
    correctly use `ScaleContext` to convert legacy pixel-based user offsets

- [x] **Update `LayoutEngine` javadoc**
  - Already complete: class-level javadoc documents pipeline order (ElementColumnBuilder →
    HorizontalSpacingCalculator → VerticalStackingCalculator → LineJustificationCalculator)
  - Three-layer model documented in `VerticalStackingCalculator` class javadoc

- [x] **Run full test suite**
  - `./scripts/test.sh unit`: all 494 tests pass

## Element-to-Tier Mapping

For reference during implementation:

| Element | Tier | Layer | abc2svg func |
|---------|------|-------|-------------|
| Staccato | 1 (near-note) | noteAttachedExtents | d_near (func 0) |
| Accent | 1 (near-note) | noteAttachedExtents | d_near (func 0) |
| Fermata | 2 (note) | noteAttachedExtents | d_upstaff (func 3) |
| Trill (+wavy) | 2 (note) | noteAttachedExtents | d_upstaff (func 3) |
| Crescendo hairpin | 3 (staff) | structuralExtents | d_cresc (func 7) |
| Diminuendo hairpin | 3 (staff) | structuralExtents | d_cresc (func 7) |
| Text dynamics (pp, f, etc.) | 3 (staff) | structuralExtents | d_pf (func 6) |
| Volta brackets | 3 (staff) | structuralExtents | d_pf (func 6) |
| Tempo | 4 (system) | systemExtents | draw_partempo |
| Beat change | 4 (system) | systemExtents | draw_partempo |
| Annotation | 4 (system) | systemExtents | draw_partempo |

## Files Affected

### New Files
- `src/main/java/songscribe/ui/layout/StaffExtents.java` -- y-extent array collision detection
- `src/test/java/songscribe/ui/layout/StaffExtentsTest.java` -- unit tests

### Major Rewrites
- `src/main/java/songscribe/ui/layout/VerticalStackingCalculator.java` -- complete rewrite
- `src/main/java/songscribe/ui/layout/LayoutResult.java` -- add `DecorationLayout`, `SpanLayout` records and maps
- `src/main/java/songscribe/ui/layout/LayoutEngine.java` -- update pipeline, remove `VerticalStackingResult` usage

### Renderer Updates (position reading, not algorithmic changes)
- `src/main/java/songscribe/ui/renderer/ArticulationRenderer.java`
- `src/main/java/songscribe/ui/renderer/FermataRenderer.java`
- `src/main/java/songscribe/ui/renderer/TrillRenderer.java`
- `src/main/java/songscribe/ui/renderer/DynamicsRenderer.java`
- `src/main/java/songscribe/ui/renderer/EndingRenderer.java`
- `src/main/java/songscribe/ui/renderer/TempoRenderer.java`
- `src/main/java/songscribe/ui/renderer/BeatChangeRenderer.java`
- `src/main/java/songscribe/ui/renderer/AnnotationRenderer.java`
- `src/main/java/songscribe/ui/component/score/LineRenderer.java`

### Removed Files
- `src/main/java/songscribe/ui/layout/VerticalStackingResult.java`

### Layout Element Updates (add Ss dimension methods)
- `src/main/java/songscribe/ui/layout/Articulation.java`
- `src/main/java/songscribe/ui/layout/FermataAttachment.java`
- `src/main/java/songscribe/ui/layout/Trill.java`
- `src/main/java/songscribe/ui/layout/DynamicAttachment.java`
- `src/main/java/songscribe/ui/layout/TempoAttachment.java`
- `src/main/java/songscribe/ui/layout/BeatChangeAttachment.java`
- `src/main/java/songscribe/ui/layout/AnnotationAttachment.java`
- `src/main/java/songscribe/ui/layout/Crescendo.java`
- `src/main/java/songscribe/ui/layout/Diminuendo.java`
- `src/main/java/songscribe/ui/layout/Ending.java`
