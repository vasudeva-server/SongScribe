# Layout System Redesign: Swing Component + LineElement Hierarchy

**Branch**: `feature/score-layout-redesign`
**Last Updated**: 2026-01-31 (Line Layout Engine Complete, LayoutManager Removed)

---

## Current Status Summary

### Phase Completion Overview

| Phase | Status | Summary |
|-------|--------|---------|
| **Phase 1** | ✅ Complete | Foundation classes (LineElement, Staff, Clef, etc.) |
| **Phase 2** | ✅ Complete | LayoutManager removed, components are source of truth |
| **Phase 3** | ✅ Complete | Core musical elements (Note extends LineElement) |
| **Phase 4** | ✅ Complete | Range elements (Tie, Trill, etc.) and BeamGroup |
| **Phase 5** | 🔄 In Progress | Line layout engine complete; uniform row heights pending |
| **Phase 6** | ✅ Complete | All 18 ElementRenderers implemented |
| **Phase 7** | ⏸️ Deferred | IO/File format versioning - not yet needed |
| **Phase 8.1** | ✅ Complete | iText → JFreePDF migration |
| **Phase 8.2** | ✅ Complete | Slur functionality removed |
| **Phase 8.3** | ✅ Complete | LayoutManager removed (completed as part of Phase 2) |
| **Phase 8.4** | ✅ Complete | Legacy Renderer/FughettaRenderer deleted (~3764 lines) |
| **Phase 9.1-9.5** | ✅ Complete | ScoreRenderer orchestrates all drawing |
| **Phase 9.6** | ✅ Complete | FughettaFontBoundsProvider extracted and integrated |
| **Phase 9.7** | ✅ Complete | Renderer.java and FughettaRenderer.java deleted |
| **Line Layout Engine** | ✅ Complete | Element-centric layout strategies with two-pass measure/arrange |

### Current Architecture

All **drawing** now uses the new ElementRenderer system via ScoreRenderer:
- Screen rendering: `ScoreRenderer.render()`
- SVG export: `ScoreRenderer.render()`
- PDF/PNG export: `ScoreRenderer.render()`
- Edit mode: `NoteRenderer` and `GlissandoRenderer`

All **measurement APIs** now use the new service layer:
- `MeasurementService` - provides 8 measurement methods (delegates to BoundsCalculator)
- `GlissandoRenderer` - provides glissando position methods (static)
- `BeatChangeRenderer` - provides beat change rendering (singleton)
- `FughettaFontBoundsProvider` - provides font-specific glyph bounds (standalone implementation)

### ✅ Migration Complete: Legacy Renderer System Removed

The old Renderer API has been **completely removed**:
- ✅ `Score.getRenderer()` method deleted (commit 1b68261)
- ✅ All 15 call sites migrated to new service layer (commit 1b68261)
- ✅ `MeasurementService` provides all measurement methods
- ✅ `GlissandoRenderer` and `BeatChangeRenderer` provide specialized methods
- ✅ `FughettaFontBoundsProvider` extracted as standalone implementation (commit 7c740d5)
- ✅ `Renderer.java` and `FughettaRenderer.java` deleted (commit 7c740d5)

| Component | Status | Notes |
|-----------|--------|-------|
| Renderer API in Score | ✅ Removed | Commit 1b68261 |
| MeasurementService | ✅ Created | Delegates to BoundsCalculator |
| FughettaFontBoundsProvider | ✅ Extracted | Standalone implementation (271 lines) |
| Renderer.java | ✅ Deleted | 2,651 lines removed (commit 7c740d5) |
| FughettaRenderer.java | ✅ Deleted | 1,113 lines removed (commit 7c740d5) |

### ✅ Phase 2 Complete: LayoutManager Removed

**Completed**: 2026-01-30

LayoutManager has been **completely removed** from the screen rendering path:
- ✅ `NoteSpacing` utility class created for horizontal spacing
- ✅ All static utility callers updated
- ✅ `ScoreRenderer.render()` stubbed (exports disabled pending migration)
- ✅ `RenderContext.getLayoutManager()` deprecated and returns null
- ✅ LayoutManager field removed from Score
- ✅ `updateLayoutFromComponents()` derives coordinates from component hierarchy
- ✅ Component hierarchy is now single source of truth

**Files Modified**: Score.java, CompositionIO.java, HorizontalAdjustment.java, BoundsCalculator.java, LayoutStylesheet.java, RenderContext.java, ScoreRenderer.java, LayoutManager.java

**Files Created**: NoteSpacing.java

### Immediate Next Steps

**Export rendering needs to be re-enabled** using component-based approach. Current status:
- Screen rendering: ✅ Works (uses component hierarchy)
- Exports: ⚠️ Stubbed (shows warning message)

To re-enable exports, implement component-based export rendering that captures the component hierarchy output.

### Long-term Architectural Goal

Replace procedural `ScoreRenderer` with true Swing component hierarchy where:
- Layout is handled by Swing layout managers
- Each section (Title, Lines, Footnotes) is a JComponent with `getPreferredSize()`
- Tempo/attribution positioning issues resolve naturally

---

## Status Dashboard

| Phase | Status | Sub-plans |
|-------|--------|-----------|
| 1 | ✅ Complete | — |
| 2 | ✅ Complete | [remove-layout-manager.md](../completed/score-layout-redesign/remove-layout-manager.md) ✓ |
| 3 | ✅ Complete | — |
| 4 | ✅ Complete | — |
| 5 | ✅ Complete | [line-layout-engine.md](../completed/score-layout-redesign/line-layout-engine.md) ✓, **spatial-stacking-fix** (merged) |
| 6 | ✅ Complete | — |
| 7 | ⏸️ Deferred | — |
| 8 | 🔄 In Progress | — |
| 9 | ✅ Complete | — |
| 10 | ⏸️ Future | — |

---

## CRITICAL: Code Quality Principles

**These principles MUST guide every implementation decision:**

### 1. Clean Separation of Concerns
- Each class has ONE clear responsibility
- Model classes hold data, renderer classes draw, layout classes position
- No mixing of data storage with rendering logic
- No mixing of UI event handling with business logic

### 2. Minimize Coupling
- Classes should depend on abstractions, not concrete implementations
- Use interfaces and base classes to define contracts
- Avoid circular dependencies
- Pass only what's needed - don't pass entire objects when a single property suffices

### 3. Always Be DRY (Don't Repeat Yourself)
- **Actively look for duplicate code** and refactor immediately
- Extract common logic to base classes or utility methods
- If you write similar code twice, refactor it into a shared abstraction
- Common patterns (margin calculation, bounds computation, coordinate conversion) belong in base classes
- When creating multiple similar classes (e.g., element renderers), identify shared behavior FIRST and put it in the base class

**Before writing any new code, ask:**
- Is this responsibility in the right place?
- Am I duplicating logic that exists elsewhere?
- Can this be generalized for reuse?

---

## Overview

Replace the custom layout system with a hybrid approach:
- **Top-level sections**: JComponent subclasses using Swing layout managers
- **Within lines**: LineElement hierarchy with DOM-like parent/child relationships and CSS-style margins

## Current State

- Custom `LayoutManager` calculates all positions in a single `measure()` pass
- `LayoutResult` stores immutable positions (NoteLayout, LineLayout, etc.)
- `ElementBounds` has 4 layers: content, padding, margin, visual
- Attachments stored as inline properties on `Note`
- Ranges stored as `IntervalSet` with start/end indices on `Line`

### Classes Being Decomposed

**Score.java → Multiple JComponents**

| Current Score Responsibility | New Location |
|------------------------------|--------------|
| Section layout calculation | Swing layout managers (BoxLayout) |
| Title painting | TitleComponent |
| Staff line painting | LineComponent |
| Lyrics painting | LineLyricsComponent |
| Under-lyrics, translation, bangla | TextPanel subcomponents |
| Footnotes painting | FootnotesComponent |
| Edit mode handling | Distributed to relevant components |
| Mouse/keyboard events | Distributed to relevant components |
| RenderContext implementation | Remains in Score (thin coordinator) |

**Renderer.java (2775 lines) + FughettaRenderer.java → ElementRenderers**

| Current Renderer Method(s) | New ElementRenderer |
|---------------------------|---------------------|
| Fughetta font mappings, common utilities | BaseElementRenderer |
| drawStaffLines() | StaffRenderer |
| drawLineBeginning() (clef part) | ClefRenderer |
| drawKeyChanges() | KeySignatureRenderer |
| paintNote(), drawNote(), drawNoteStem(), drawNoteFlags(), drawNoteDots(), drawAccidental(), drawStave() | NoteRenderer |
| Rest rendering | RestRenderer |
| drawBeams(), drawBeam() | BeamGroupRenderer |
| drawBarLine(), drawRepeat() | BarRenderer |
| drawTie() | TieRenderer |
| drawTrill() | TrillRenderer |
| drawCrescendos(), drawDiminuendos() | DynamicsRenderer |
| drawTuplets() | TupletRenderer |
| First/second endings | EndingRenderer |
| drawTempoChange() | TempoRenderer |
| drawBeatChange() | BeatChangeRenderer |
| drawFermata() | FermataRenderer |
| drawAnnotation() | AnnotationRenderer |
| drawArticulation() | ArticulationRenderer |

## Target Architecture

### JComponent Hierarchy

```
MainPanel (BoxLayout.Y_AXIS)
  ├── TitleComponent extends JComponent
  ├── ScorePanel (BoxLayout.Y_AXIS)
  │     ├── LinePanel
  │     │     ├── LineComponent extends JComponent
  │     │     └── LineLyricsComponent extends JComponent
  │     ├── LinePanel (line 1)
  │     └── ...
  ├── TextPanel (BoxLayout.Y_AXIS)
  │     ├── UnderlyricsComponent
  │     ├── TranslationComponent
  │     └── BanglaComponent
  └── FootnotesComponent (bottom-anchored; different position for export)
```

### LineElement Hierarchy (within LineComponent)

```
Line (root)
  ├── Clef (absolute positioning, own margin)
  ├── KeySignature (absolute positioning, own margin)
  ├── Staff (just 5 lines, margin for attribution spacing)
  │     └── Attribution (right-aligned, triggers staff movement on collision)
  ├── Note (extends LineElement)
  │     ├── Articulations (staccato, accent - drawn outward from head)
  │     └── Attachments (tempo, fermata, dynamics, annotation, beat change)
  ├── NonNote (intermediate class, extends LineElement)
  │     ├── Rest (various durations)
  │     ├── BreathMark
  │     └── Bar (single, double, final, repeat variants)
  ├── BeamGroup (references notes, provides stem targets)
  └── RangeElements (Tie, Trill, Crescendo, Diminuendo, Tuplet, Ending)
```

**Note**: Clef and KeySignature are absolutely positioned - they render at the line start but don't affect Staff's content/margin bounds.

### LineElement Base Class

```java
abstract class LineElement {
    Line parentLine;
    LineElement parentElement;  // null for direct Line children
    Point2D position;           // relative to parent line origin
    Dimension2D size;
    Rectangle2D margin;         // CSS-style, collapses with adjacent margins
    List<LineElement> children;
}
```

### Simplified Box Model

- **contentBounds** - what's actually drawn
- **marginBounds** - for layout spacing and collision (CSS-style collapsing)
- **Hit testing** - contentBounds + fixed expansion (2-4px)
- No padding layer

### Key Design Decisions

1. **Lines are self-contained** - no cross-line elements (slurs removed entirely)
2. **Attribution** attaches to Staff, triggers staff movement if overlapped
3. **BeamGroup** (Option B) - references notes but doesn't contain them; provides stem length adjustments
4. **Trills** become RangeElements (even single-note trills)
5. **X positioning** - existing algorithm based on note duration
6. **Articulation ordering** - by ArticulationType enum, drawn outward from note head
7. **NonNote** (renamed from NotNote) - kept as intermediate class for MIDI/IO
8. **Clef and KeySignature** - separate LineElements with absolute positioning and own margins
9. **File format** - reader supports old and new formats; writer uses new format; version field added

### Rendering Architecture (Strategy Pattern)

Each element type has an associated renderer class. Elements are passive data; rendering is delegated.

```java
interface ElementRenderer<T extends LineElement> {
    void render(T element, Graphics2D g2, RenderContext ctx);
}

// Concrete renderers
class NoteRenderer implements ElementRenderer<Note> { ... }
class StaffRenderer implements ElementRenderer<Staff> { ... }
class BeamGroupRenderer implements ElementRenderer<BeamGroup> { ... }
// etc.
```

**Structure:**
- `BaseElementRenderer` - shared utilities, Fughetta font mappings, common drawing operations
- Per-element renderers extend base and implement element-specific drawing
- `RendererRegistry` - maps element types to renderer instances
- JComponents call `registry.getRenderer(element).render(element, g2, ctx)` in paintComponent()

**Benefits:**
- Modular - one focused class per element type (vs. 2775-line god class)
- Elements stay clean (no graphics code mixed with model)
- Easy to swap renderer implementations
- Centralized font management in base class

**Rendering Targets (Graphics2D abstraction):**

ElementRenderers work exclusively with Graphics2D and are target-agnostic. The target format is determined by what backs the Graphics2D:

| Target | Graphics2D Implementation |
|--------|---------------------------|
| Screen | Native Swing Graphics2D |
| Image (PNG/JPG) | `BufferedImage.createGraphics()` |
| PDF | JFreePDF's `PDFGraphics2D` |
| SVG | JFreeSVG's `SVGGraphics2D` |

This keeps renderers simple - they don't know or care about the output format.

**Debug Rendering (integrated in base class):**
```java
abstract class BaseElementRenderer<T> implements ElementRenderer<T> {
    public final void render(T element, Graphics2D g2, RenderContext ctx) {
        renderElement(element, g2, ctx);  // Subclass implements
        if (ctx.isDebugEnabled()) {
            renderDebug(element, g2, ctx); // Common debug drawing
        }
    }

    protected abstract void renderElement(T element, Graphics2D g2, RenderContext ctx);

    protected void renderDebug(T element, Graphics2D g2, RenderContext ctx) {
        // Default: draw content bounds, margin bounds
        // Subclasses can override to add element-specific debug info
    }
}
```
Debug rendering is in the same class hierarchy because it operates on the same data and changes together with normal rendering.

### Export Architecture (Visitor Pattern)

For exporting to MIDI, ABC, MusicXML, and other formats, the visitor pattern provides clean separation:

```java
interface LineElementVisitor<R> {
    R visitNote(Note note);
    R visitRest(Rest rest);
    R visitBar(Bar bar);
    R visitBeamGroup(BeamGroup beamGroup);
    R visitTie(Tie tie);
    R visitTrill(Trill trill);
    // ... all element types
}

abstract class LineElement {
    abstract <R> R accept(LineElementVisitor<R> visitor);
}

// Export implementations
class MIDIExportVisitor implements LineElementVisitor<Void> { ... }
class ABCExportVisitor implements LineElementVisitor<String> { ... }
class MusicXMLExportVisitor implements LineElementVisitor<Element> { ... }
```

**Why Visitor for exports (vs. Strategy for rendering):**

| Concern | Pattern | Rationale |
|---------|---------|-----------|
| Rendering | Strategy + Registry | Easy to add element types without updating all renderers |
| Export | Visitor | Easy to add export formats; compiler ensures all element types are handled |

---

## Phase 1: Foundation Classes ✅ COMPLETE

**Goal**: Establish core base classes and simplified box model.

### Tasks

1.1. Create `LineElement` abstract base class
   - Properties: parentLine, parentElement, position, size, margin, children
   - Methods: getContentBounds(), getMarginBounds(), containsPoint() (with fixed expansion)

1.2. Simplify `ElementBounds` (or create new `Bounds` class)
   - Remove padding layer
   - Content bounds + margin bounds only
   - Hit testing with configurable expansion (2-4px)

1.3. Create `Staff` class extending `LineElement`
   - Bounds: just the 5 staff lines (clef/key sig are absolute-positioned siblings)
   - Has margin for attribution spacing

1.4. Create `Clef` class extending `LineElement`
   - Absolute positioning at line start
   - Own margin for spacing

1.5. Create `KeySignature` class extending `LineElement`
   - Absolute positioning after clef
   - Own margin for spacing to first note

1.6. Create `ArticulationType` enum
   - Values: STACCATO, ACCENT (ordered for drawing priority)

1.7. Create `Attachment` abstract base class
   - Properties: parentNote, alignment (left/center/right), placement (above/below)

1.8. Create `RangeElement` abstract base class
   - Properties: anchorNote (first note in range)
   - Subclass stubs: Tie, Trill, Crescendo, Diminuendo, Tuplet, Ending

### Files to Create
- `src/main/java/songscribe/ui/layout/LineElement.java`
- `src/main/java/songscribe/ui/layout/Staff.java`
- `src/main/java/songscribe/ui/layout/Clef.java`
- `src/main/java/songscribe/ui/layout/KeySignature.java`
- `src/main/java/songscribe/music/ArticulationType.java`
- `src/main/java/songscribe/ui/layout/Attachment.java`
- `src/main/java/songscribe/ui/layout/RangeElement.java`

### Files to Modify
- `src/main/java/songscribe/ui/layout/ElementBounds.java` (simplify)

---

## Phase 2: JComponent Hierarchy ✅ COMPLETE

**Goal**: Create Swing component structure with layout managers and remove LayoutManager.

**Status**: ✅ Complete (2026-01-30)
- Component hierarchy exists and is source of truth
- LayoutManager completely removed from rendering path
- Screen rendering uses component hierarchy via MainPanel
- Exports temporarily stubbed (pending migration to component-based approach)

### Sub-plans

- [remove-layout-manager.md](../completed/score-layout-redesign/remove-layout-manager.md) ✓ — Complete removal of LayoutManager from screen rendering

### LayoutManager Removal Summary

The LayoutManager has been **completely removed** from the screen rendering path:
- ✅ `NoteSpacing` utility class created for horizontal spacing
- ✅ All static utility callers updated (CompositionIO, HorizontalAdjustment, Score, BoundsCalculator)
- ✅ `RenderContext.getLayoutManager()` deprecated and returns null
- ✅ LayoutManager field removed from Score
- ✅ `updateLayoutFromComponents()` derives coordinates from component hierarchy

**Files created**: `NoteSpacing.java`
**Files modified**: Score.java, CompositionIO.java, HorizontalAdjustment.java, BoundsCalculator.java, LayoutStylesheet.java, RenderContext.java, ScoreRenderer.java

### Tasks

2.1. Create `TitleComponent extends JComponent`
   - paintComponent() draws title text
   - getPreferredSize() based on text bounds

2.2. Create `LineComponent extends JComponent`
   - Contains LineElement tree (built in Phase 3+)
   - paintComponent() renders all elements
   - getPreferredSize() based on content extent (notes above/below staff, attachments)

2.3. Create `LineLyricsComponent extends JComponent`
   - paintComponent() draws syllables
   - getPreferredSize() based on lyrics height

2.4. Create `LinePanel extends JPanel`
   - Contains LineComponent + LineLyricsComponent
   - BoxLayout.Y_AXIS with margin between

2.5. Create `ScorePanel extends JPanel`
   - Contains LinePanel instances
   - BoxLayout.Y_AXIS

2.6. Create `TextPanel extends JPanel`
   - Contains Underlyrics, Translation, Bangla components
   - BoxLayout.Y_AXIS

2.7. Create `FootnotesComponent extends JComponent`
   - paintComponent() draws footnotes
   - Positioned differently for display vs export

2.8. Create `MainPanel` or refactor `Score`
   - Assembles all components
   - Swing layout for vertical stacking

### Files to Create
- `src/main/java/songscribe/ui/component/TitleComponent.java`
- `src/main/java/songscribe/ui/component/LineComponent.java`
- `src/main/java/songscribe/ui/component/LineLyricsComponent.java`
- `src/main/java/songscribe/ui/component/LinePanel.java`
- `src/main/java/songscribe/ui/component/ScorePanel.java`
- `src/main/java/songscribe/ui/component/TextPanel.java`
- `src/main/java/songscribe/ui/component/FootnotesComponent.java`

### Files to Modify
- `src/main/java/songscribe/ui/component/Score.java` (integrate new hierarchy)

---

## Phase 3: Core Musical Elements ✅ COMPLETE

**Goal**: Migrate Note/Rest/Bar to LineElement hierarchy.

### Tasks

3.1. Make existing `Note` class extend `LineElement`
   - Add required LineElement properties
   - Maintain existing properties for compatibility

3.2. Extract articulations to proper `Articulation` class
   - Parent note reference
   - Type from ArticulationType enum
   - Drawing logic (outward from note head based on stem direction)

3.3. Create concrete Attachment subclasses
   - `TempoAttachment` (from Note.tempoChange)
   - `FermataAttachment` (from Note.fermata)
   - `AnnotationAttachment` (from Note.annotation)
   - `DynamicAttachment` (f, p, mf, etc.)
   - `BeatChangeAttachment` (from Note.beatChange)

3.4. Rename `NotNote` to `NonNote` and migrate subclasses
   - Rest types: extend NonNote (which extends LineElement)
   - BreathMark: extend NonNote
   - Bar types: extend NonNote

### Files to Modify
- `src/main/java/songscribe/music/Note.java`
- `src/main/java/songscribe/music/NotNote.java`
- All Note/Rest subclasses

### Files to Create
- `src/main/java/songscribe/ui/layout/Articulation.java`
- `src/main/java/songscribe/ui/layout/TempoAttachment.java`
- `src/main/java/songscribe/ui/layout/FermataAttachment.java`
- `src/main/java/songscribe/ui/layout/AnnotationAttachment.java`
- `src/main/java/songscribe/ui/layout/DynamicAttachment.java`
- `src/main/java/songscribe/ui/layout/BeatChangeAttachment.java`

---

## Phase 4: Range Elements and BeamGroup ✅ COMPLETE

**Goal**: Implement RangeElement subclasses and BeamGroup coordinator.

### Tasks

4.1. Implement `Tie extends RangeElement`

4.2. Implement `Trill extends RangeElement`
   - Migrated from attachment (even single-note trills)

4.3. Implement `Crescendo extends RangeElement`

4.4. Implement `Diminuendo extends RangeElement`

4.5. Implement `Tuplet extends RangeElement`
   - Properties: grade (3 for triplet, etc.)

4.6. Implement `Ending extends RangeElement` (first/second endings)

4.7. Implement `BeamGroup extends LineElement`
   - References beamed notes (doesn't contain them)
   - Calculates beam angle and stem targets
   - Notes query BeamGroup for adjusted stem length

4.8. Migrate from IntervalSet storage
   - Change Line to store RangeElement list instead of 7 IntervalSets
   - Data migration for existing compositions

### Files to Create
- `src/main/java/songscribe/ui/layout/Tie.java`
- `src/main/java/songscribe/ui/layout/Trill.java`
- `src/main/java/songscribe/ui/layout/Crescendo.java`
- `src/main/java/songscribe/ui/layout/Diminuendo.java`
- `src/main/java/songscribe/ui/layout/Tuplet.java`
- `src/main/java/songscribe/ui/layout/Ending.java`
- `src/main/java/songscribe/ui/layout/BeamGroup.java`

### Files to Modify
- `src/main/java/songscribe/music/Line.java` (range storage)

---

## Phase 5: Attribution and Layout Integration ✅ COMPLETE

**Goal**: Implement attribution handling, margin collapsing, and uniform row height spacing.

**Status**: ✅ Complete (2026-01-31)

### Completed Sub-Plans

- ✅ [line-layout-engine.md](../completed/score-layout-redesign/line-layout-engine.md) — Element-centric layout strategies with two-pass measure/arrange
- ✅ **spatial-stacking-fix** — Merged (see below)
- 🔄 [uniform-row-heights.md](./uniform-row-heights.md) — Pending (next priority)

### Line Layout Engine Summary

The Line Layout Engine implements a comprehensive layout system using:
- **Element-Centric Strategies**: Each element type has its own `LayoutStrategy`
- **Two-Pass Layout**: Measure pass (calculate sizes) → Arrange pass (assign positions)
- **Area-based Accumulation**: Uses `java.awt.geom.Area` for complex shapes (ties, endings)
- **Stacking Layers**: 12 layers from Note (bottom) to Lyrics (top)

**Completed phases** (all 11 phases):
1. ✅ Foundation Classes (LayoutLayer, Margin, LayoutAccumulator, contexts)
2. ✅ Strategy Interface and Registry
3. ✅ Note, Tie, and Articulation Strategies
4. ✅ Tuplet Strategy
5. ✅ Trill, Fermata, Dynamics Strategies
6. ✅ Endings Strategy (Area-based)
7. ✅ Tempo, Annotation, Attribution Strategies
8. ✅ Lyrics Strategy
9. ✅ LineLayoutEngine Integration
10. ✅ User Offset Support
11. ✅ Cleanup and Migration

**Key files created**: 25+ new files in `ui/layout/` and `ui/layout/strategy/`

### Merged: Spatial Stacking Fix

**Problem Solved**: The layout system had infrastructure for spatial collision detection but was still using a **global Y waterline** instead of **X-range spatial queries**, causing cross-element interference (e.g., tempo on note 1 incorrectly affecting fermata on note 10).

**Implementation**: 5-phase spatial collision detection system:

1. **Phase 1: X-Range Queries** ✅
   - Added `getTopYInRange()`, `getBottomYInRange()`, `intersectsInRange()` to LayoutAccumulator
   - Enables spatial filtering by X position instead of global queries

2. **Phase 2: ArrangeContext Integration** ✅
   - Updated `getHighestBoundsInRange()` and `getLowestBoundsInRange()` to include accumulated bounds
   - Range queries now properly consider spatial overlap

3. **Phase 3: Remove Global Queries** ✅
   - Removed all `getTopY()` calls from layout strategies
   - All strategies now use spatial range queries exclusively
   - Completely eliminated global Y waterline pattern

4. **Phase 4: Articulation-Tie Collision Detection** ✅
   - Enabled area-based collision detection for articulations (staccato, accent)
   - Articulations now shift away from ties with 0.5 MU margin
   - Works with actual tie curve shapes, not just bounding boxes

5. **Phase 5: Cleanup & Optimization** ✅
   - Removed unused `getTopY()` method entirely (no backward compatibility needed)
   - Added comprehensive debug logging (`-Dsongscribe.debug.collision=true`)
   - Documented memory design and performance characteristics
   - Enhanced javadocs with spatial behavior examples

**Key Achievement**: Elements at different X positions no longer interfere with each other. Tempo on note 1 (x=100) no longer affects fermata positioning on note 10 (x=400).

**Files Modified**:
- `LayoutAccumulator.java` - Added spatial queries, debug logging, memory documentation
- `ArrangeContext.java` - Updated range methods for accumulated bounds
- 6 layout strategies - Removed global query calls, enabled collision detection

**Testing**: ✅ All 26+ tests passing, spatial isolation verified

### Tasks

5.1. Create `Attribution` as Staff attachment
   - Right-aligned positioning
   - Margin collision detection
   - Staff movement when overlapped

5.2. Implement CSS-style margin collapsing
   - Between adjacent LineElements
   - Between JComponents (via Swing layout or custom LayoutManager)

5.3. Integrate X positioning algorithm
   - Existing note-duration-based spacing
   - Apply to LineElement positions

5.4. Implement collision detection
   - Attachments vs attribution
   - Notes extending above/below staff

5.5. Implement LineComponent.getPreferredSize()
   - Based on staff height + max extent above/below
   - Accounts for all attachments and ranges

### Files to Modify
- `src/main/java/songscribe/ui/layout/Staff.java` (attribution)
- `src/main/java/songscribe/ui/layout/LineElement.java` (margin collapsing)
- `src/main/java/songscribe/ui/component/LineComponent.java` (sizing)

---

## Phase 6: Rendering Infrastructure (Strategy Pattern) ✅ COMPLETE

**Goal**: Create modular element renderer system.

**Implemented Renderers** (18 total):
- Core: `NoteRenderer`, `RestRenderer`, `BarRenderer`, `GraceNoteRenderer`
- Staff: `StaffRenderer`, `ClefRenderer`, `KeySignatureRenderer`
- Groups: `BeamGroupRenderer`, `TieRenderer`, `TupletRenderer`
- Dynamics: `DynamicsRenderer`, `TrillRenderer`
- Attachments: `TempoRenderer`, `BeatChangeRenderer`, `FermataRenderer`, `AnnotationRenderer`, `ArticulationRenderer`
- Endings: `EndingRenderer`
- Additional: `LyricsRenderer`, `GlissandoRenderer`, `ScoreRenderer` (orchestrator)

### Tasks

6.1. Create `ElementRenderer<T>` interface
   ```java
   interface ElementRenderer<T extends LineElement> {
       void render(T element, Graphics2D g2, RenderContext ctx);
       Rectangle2D getBounds(T element, RenderContext ctx);
   }
   ```

6.2. Create `BaseElementRenderer` abstract class
   - Shared utilities (font management, common drawing operations)
   - Fughetta font mappings (extracted from FughettaRenderer)
   - Common glyph rendering methods

6.3. Create `RendererRegistry`
   - Maps element types to renderer instances
   - `getRenderer(LineElement element)` returns appropriate renderer
   - Singleton or injectable

6.4. Create concrete element renderers (extract from FughettaRenderer)
   - `NoteRenderer` - note head, stem, flags, dots, accidentals, ledger lines
   - `RestRenderer` - rest glyphs
   - `StaffRenderer` - 5 staff lines
   - `ClefRenderer` - treble clef glyph
   - `KeySignatureRenderer` - sharps/flats
   - `BarRenderer` - bar lines (single, double, final, repeat)
   - `BeamGroupRenderer` - beam bars and stem coordination
   - `ArticulationRenderer` - staccato, accent marks
   - `AttachmentRenderer` subclasses - tempo, fermata, annotation, dynamics
   - `RangeRenderer` subclasses - tie, trill, crescendo, diminuendo, tuplet, ending

6.5. Implement JComponent `paintComponent()` methods
   - Iterate LineElement tree
   - Call `registry.getRenderer(element).render(element, g2, ctx)`

6.6. Coordinate BeamGroup rendering
   - BeamGroup calculates stem targets before notes render
   - Notes query BeamGroup for stem length during render
   - BeamGroupRenderer draws beam bars after notes

### Files to Create
- `src/main/java/songscribe/ui/renderer/ElementRenderer.java`
- `src/main/java/songscribe/ui/renderer/BaseElementRenderer.java`
- `src/main/java/songscribe/ui/renderer/RendererRegistry.java`
- `src/main/java/songscribe/ui/renderer/NoteRenderer.java`
- `src/main/java/songscribe/ui/renderer/RestRenderer.java`
- `src/main/java/songscribe/ui/renderer/StaffRenderer.java`
- `src/main/java/songscribe/ui/renderer/ClefRenderer.java`
- `src/main/java/songscribe/ui/renderer/KeySignatureRenderer.java`
- `src/main/java/songscribe/ui/renderer/BarRenderer.java`
- `src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java`
- `src/main/java/songscribe/ui/renderer/ArticulationRenderer.java`
- `src/main/java/songscribe/ui/renderer/TieRenderer.java`
- `src/main/java/songscribe/ui/renderer/TrillRenderer.java`
- `src/main/java/songscribe/ui/renderer/DynamicsRenderer.java` (crescendo/diminuendo)
- `src/main/java/songscribe/ui/renderer/TupletRenderer.java`
- `src/main/java/songscribe/ui/renderer/EndingRenderer.java`
- `src/main/java/songscribe/ui/renderer/TempoRenderer.java`
- `src/main/java/songscribe/ui/renderer/FermataRenderer.java`
- `src/main/java/songscribe/ui/renderer/AnnotationRenderer.java`

### Files to Modify
- All JComponent classes (implement paintComponent using registry)

---

## Phase 7: IO and File Format ⏸️ DEFERRED

**Goal**: Support both old and new file formats with versioning.

**Status**: Not yet needed. The current file format still works. This phase can be implemented
when there's a need to persist the new LineElement hierarchy to disk.

### Tasks

7.1. Add format version field to Composition
   - Version 1: current/legacy format
   - Version 2: new LineElement-based format

7.2. Create `LegacyCompositionReader`
   - Reads old format files
   - Converts IntervalSet ranges to RangeElement objects
   - Converts inline Note attachments to Attachment objects
   - Produces new data structures

7.3. Create `CompositionReader` (new format)
   - Reads new format files with LineElement hierarchy
   - Handles versioning for future migrations

7.4. Create `CompositionWriter` (new format only)
   - Writes compositions in new format
   - Always includes version field

7.5. Update `CompositionIO` as facade
   - Detects format version on read
   - Delegates to appropriate reader
   - Uses new writer for all saves

### Files to Create
- `src/main/java/songscribe/IO/LegacyCompositionReader.java`
- `src/main/java/songscribe/IO/CompositionReader.java`
- `src/main/java/songscribe/IO/CompositionWriter.java`

### Files to Modify
- `src/main/java/songscribe/music/Composition.java` (add version field)
- `src/main/java/songscribe/IO/CompositionIO.java` (facade pattern)

---

## Phase 8: Cleanup and Library Migration 🔶 IN PROGRESS

**Goal**: Remove deprecated code, migrate libraries, and finalize.

### Tasks

8.1. ✅ Migrate from old iText to JFreePDF
   - Remove old iText dependency
   - Add JFreePDF dependency
   - Add JFreeSVG dependency (if not already present)
   - Update PDF export to use `PDFGraphics2D`
   - Update SVG export to use `SVGGraphics2D` (if applicable)
   - Verify all export formats work correctly

8.2. ✅ Remove slur functionality entirely
   - Delete SlurData class
   - Remove slur IntervalSet from Line
   - Remove slur rendering methods
   - Update UI to remove slur tools

8.3. 🔒 BLOCKED: Remove old layout system classes
   - Old LayoutManager methods — still used for measurement
   - Old LayoutResult structures — still used
   - Old NoteLayout, LineLayout classes — still used
   - **Blocker**: LayoutManager depends on Renderer measurement methods

8.4. ✅ COMPLETE: Remove old Renderer classes
   - ✅ Removed Renderer API from Score (Score.getRenderer() deleted - commit 1b68261)
   - ✅ Created MeasurementService facade for measurement methods (commit 1b68261)
   - ✅ Migrated all 15 call sites to new service layer (commit 1b68261)
   - ✅ Extracted FughettaFontBoundsProvider (271 lines - commit 7c740d5)
   - ✅ Deleted Renderer.java (2,651 lines - commit 7c740d5)
   - ✅ Deleted FughettaRenderer.java (1,113 lines - commit 7c740d5)
   - **Net reduction**: 3,493 lines (~93% reduction)

8.5. Update tests

8.6. Update debug visualization
   - DebugRenderer for new element hierarchy

8.7. Add debug logging infrastructure
   - Logging for layout calculations
   - Logging for rendering passes
   - Helps diagnose issues during transition

### Files to Delete
- `src/main/java/songscribe/data/SlurData.java`
- `src/main/java/songscribe/ui/renderer/Renderer.java` (after migration complete)
- `src/main/java/songscribe/ui/renderer/FughettaRenderer.java` (after migration complete)

### Files to Modify
- `src/main/java/songscribe/music/Line.java` (remove slur IntervalSet)
- Various UI files (remove slur tools)
- Test files

---

## Phase 9: Orchestration Layer Migration ✅ COMPLETE

**Goal**: Migrate Score.java from Renderer.drawScore() to ScoreRenderer.

**Note**: This phase was added to bridge the gap between the new ElementRenderers and the old
monolithic Renderer. The original Phase 9 (Export Visitor Infrastructure) is now Phase 10.

### All Stages Complete

- ✅ **9.1**: Analyzed `drawScore()` structure (see `plans/phase9-1-drawscore-analysis.md`)
- ✅ **9.2**: Created `ScoreRenderer` orchestrator class
- ✅ **9.3**: Migrated screen rendering to ScoreRenderer
- ✅ **9.4**: Migrated export rendering (SVG, PDF, PNG) to ScoreRenderer
- ✅ **9.5**: Migrated edit mode rendering to NoteRenderer/GlissandoRenderer
- ✅ **9.6**: Removed FughettaRenderer dependency
  - ✅ Created `MeasurementService` facade (commit 1b68261)
  - ✅ Migrated all measurement API calls from Renderer to MeasurementService
  - ✅ Updated LayoutManager (11 call sites) to use MeasurementService
  - ✅ Updated VerticalAdjustment (3 call sites) to use MeasurementService
  - ✅ Added glissando position methods to GlissandoRenderer
  - ✅ Updated BeatChangeDialog to use BeatChangeRenderer directly
  - ✅ Extracted FughettaFontBoundsProvider (271 lines - commit 7c740d5)
- ✅ **9.7**: Deleted legacy Renderer files
  - ✅ Deleted Renderer.java (2,651 lines - commit 7c740d5)
  - ✅ Deleted FughettaRenderer.java (1,113 lines - commit 7c740d5)

---

## Phase 10: Export Visitor Infrastructure ⏸️ FUTURE

**Goal**: Implement visitor pattern for MIDI and format exports.

### Tasks

9.1. Add `accept()` method to `LineElement` base class
   - Abstract method: `<R> R accept(LineElementVisitor<R> visitor)`
   - Implement in all LineElement subclasses

9.2. Create `LineElementVisitor<R>` interface
   - Visit methods for all element types
   - Generic return type R for flexibility

9.3. Create `CompositionVisitor` for document-level traversal
   - Iterates over Lines, delegates to LineElementVisitor
   - Handles composition metadata (title, tempo, etc.)

9.4. Refactor MIDI export to use visitor
   - Create `MIDIExportVisitor implements LineElementVisitor<Void>`
   - Migrate existing MIDI generation logic
   - Remove old MIDI export code

9.5. Create ABC export visitor (if needed)
   - `ABCExportVisitor implements LineElementVisitor<String>`

9.6. Create MusicXML export visitor (if needed)
   - `MusicXMLExportVisitor implements LineElementVisitor<Element>`

### Files to Create
- `src/main/java/songscribe/export/LineElementVisitor.java`
- `src/main/java/songscribe/export/CompositionVisitor.java`
- `src/main/java/songscribe/export/MIDIExportVisitor.java`
- `src/main/java/songscribe/export/ABCExportVisitor.java` (optional)
- `src/main/java/songscribe/export/MusicXMLExportVisitor.java` (optional)

### Files to Modify
- `src/main/java/songscribe/ui/layout/LineElement.java` (add accept method)
- All LineElement subclasses (implement accept)
- Existing MIDI export code (refactor to use visitor)

---

## Key Files Reference

### Current Layout System
- `src/main/java/songscribe/ui/layout/LayoutManager.java` (2176 lines)
- `src/main/java/songscribe/ui/layout/BoundsCalculator.java` (568 lines)
- `src/main/java/songscribe/ui/layout/ElementBounds.java` (319 lines)
- `src/main/java/songscribe/ui/layout/LayoutResult.java` (310 lines)
- `src/main/java/songscribe/ui/layout/LineLayout.java` (238 lines)
- `src/main/java/songscribe/ui/layout/NoteLayout.java` (154 lines)

### Current Model
- `src/main/java/songscribe/music/Note.java`
- `src/main/java/songscribe/music/Line.java`
- `src/main/java/songscribe/music/Composition.java`

### Current Rendering
- `src/main/java/songscribe/ui/renderer/Renderer.java` (2775 lines)
- `src/main/java/songscribe/ui/renderer/FughettaRenderer.java`
- `src/main/java/songscribe/ui/component/Score.java`

---

## Verification

**Note**: The application will be non-functional during most of this migration. This is expected and acceptable given the scope of architectural changes.

### During Development
1. Run `./scripts/compile.sh` after each change to catch compilation errors
2. Add debug logging liberally to trace layout and rendering issues
3. Use incremental testing where possible

### Final Verification (after Phase 8)
1. All existing compositions load correctly (via LegacyCompositionReader)
2. All note types render correctly
3. All attachments render correctly (tempo, fermata, annotation, dynamics, beat change)
4. All range elements render correctly (tie, trill, crescendo, diminuendo, tuplet, ending)
5. Beams render correctly with proper stem lengths
6. Attribution collision triggers staff movement as expected
7. Clef and KeySignature positioned correctly with proper margins
8. Swing layout correctly stacks all sections
9. Export produces correct output (footnotes positioned correctly)
10. New compositions save in new format with version field
11. MIDI playback works correctly (NonNote handling)

---

## Quick Reference: Next Steps

### To Complete Phase 8.4 / 9.6 (Delete Old Renderers)

**Status**: API migration complete (commit 1b68261), file deletion blocked

**What's Done**:
- ✅ MeasurementService facade created
- ✅ All 15 call sites migrated to new service layer
- ✅ Score.getRenderer() method removed
- ✅ All functionality verified (screen, PDF, print, dialogs)

**What Remains**:

1. **Extract FontBoundsProvider** - Create standalone implementation:
   - Create new `FughettaFontBoundsProvider` class implementing `FontBoundsProvider`
   - Extract font-specific glyph bounds logic from `FughettaRenderer`
   - Move Fughetta font loading and metrics to new class

2. **Update MeasurementService** - Use new FontBoundsProvider:
   - Change constructor to accept `FughettaFontBoundsProvider` instead of casting from Renderer
   - Update Score.java instantiation

3. **Delete files**:
   - `src/main/java/songscribe/ui/renderer/Renderer.java` (~2775 lines)
   - `src/main/java/songscribe/ui/renderer/FughettaRenderer.java` (~1100 lines)

4. **Verify** - Ensure all rendering and measurement still works

### To Fix Tempo/Title Position Issues

These issues stem from ScoreRenderer being procedural rather than component-based.
Full fix requires implementing Phase 2 properly (Swing component hierarchy with layout managers).
