# Complete Layout2 Migration Plan

> **Note:** This plan is for reference only. It was abandoned in favor of a different approach.

## Status Dashboard

| Phase | Description                                                                      | Status         | Sub-plan |
|-------|----------------------------------------------------------------------------------|----------------|----------|
| 1A    | [Articulation](#a-articulation-stacker-already-positions-renderer-ignores)       | ✅ Complete | —        |
| 1B    | [Tempo](#b-tempo-stacker-doesnt-store-renderer-already-reads)                    | ✅ Complete | —        |
| 1C    | [BeatChange](#c-beatchange-stacker-doesnt-store-renderer-has-coordinate-bug)     | ✅ Complete | —        |
| 1D    | [Annotation](#d-annotation-stacker-doesnt-store-renderer-crashes-coordinate-bug) | ✅ Complete | —        |
| 2B    | [Trill](#b-trill)                                                                | ✅ Complete | —        |
| 2C    | [Dynamics](#c-dynamics-crescendodiminuendo)                                      | ✅ Complete | —        |
| 2D    | [Ending](#d-ending)                                                              | ✅ Complete | —        |
| 2E    | [Tuplet](#e-tuplet)                                                              | ✅ Complete | —        |
| 3     | [Legacy Code Removal](#phase-3-legacy-code-removal)                              | ✅ Complete | —        |

## Context

The layout2 system (`VerticalStackingCalculator` + `LayoutResult`) was designed to replace self-positioning in renderers with a clean separation: layout engine computes positions, renderers just draw. Currently only Fermata is fully migrated. All other elements have gaps ranging from "stacker computes but renderer ignores" to "renderer expects layout but stacker doesn't provide" to "no stacker support at all." This mixed state makes rendering bugs impossible to diagnose -- we cannot tell if a bug is in old code or new code.

**Goal:** Complete the migration for ALL elements, then remove ALL legacy positioning code, so there is exactly one code path: layout2 computes positions, renderers read them from `LayoutResult`.

## Migration Status (Current)

| Element      | Stacker                               | Renderer                                                      | Status   |
|--------------|---------------------------------------|---------------------------------------------------------------|----------|
| Fermata      | positionElement via FermataAttachment | Reads LayoutResult, fallback for insertion note               | **Done** |
| Articulation | positionElement (output ignored)      | Self-computes                                                 | Done     |
| Tempo        | Reserves space only (legacy check)    | Expects LayoutResult, throws ISE                              | Phase 1  |
| BeatChange   | Reserves space only (legacy check)    | Expects LayoutResult, throws ISE, coordinate bug              | Phase 1  |
| Annotation   | Reserves space only (legacy check)    | Expects LayoutResult, throws ISE, coordinate bug, **crashes** | Phase 1  |
| Trill        | Reserves space only (legacy flag)     | Legacy `renderTrillsFromLine`                                 | Phase 2  |
| Dynamics     | No-op (TODO)                          | Legacy IntervalSet path                                       | Phase 2  |
| Tuplet       | No stacker layer                      | Legacy IntervalSet path                                       | Phase 2  |
| Ending       | No stacker layer                      | Y from layout (partially), X legacy                           | Phase 2  |

## Architecture Reference

### Key patterns to follow

**Stacker pattern** (from `stackFermata`):
```java
var attachment = note.findAttachment(XxxAttachment.class);
if(attachment !=null){
var y = findClearYPosition(attachment, column.getX(), accumulated, margin);

positionElement(attachment, column.getX(),y,noteElementPositions);

addToAccumulated(attachment, column.getX(),y,accumulated);
    }
```

**Renderer pattern** (from `FermataRenderer`):
```java
var layoutResult = ctx.getLayoutResult();
if(layoutResult !=null){
var bounds = layoutResult.findAttachmentBounds(note, XxxAttachment.class);
    if(bounds !=null){
int y = layoutYToComponentY(bounds, ctx);  // middleLineY + bounds.getTop()
// draw at y
    }
        }else{
        // fallback for insertion note preview
        }
```

**Coordinate conversion:** `layoutYToComponentY(bounds, ctx)` in `BaseElementRenderer` converts layout-space Y (relative to middleLineY=0) to component-space Y. All renderers reading layout positions must use this.

### Key files

- **Stacker:** `src/main/java/songscribe/ui/layout2/VerticalStackingCalculator.java`
- **Layout result:** `src/main/java/songscribe/ui/layout2/LayoutResult.java`
    - `getElementBounds(LineElement)` -- direct lookup (for Articulation)
    - `findAttachmentBounds(Note, Class<Attachment>)` -- search by parent note + type
    - `findRangeElementBounds(Note anchor, Note end, Class<RangeElement>)` -- search by anchor/end notes + type
- **Layout constants:** `src/main/java/songscribe/ui/layout2/LayoutConstants.java`
- **Build result:** `src/main/java/songscribe/ui/layout2/LayoutEngine.java` (`buildLayoutResult`)
- **Base renderer:** `src/main/java/songscribe/ui/renderer/BaseElementRenderer.java` (`layoutYToComponentY`)
- **LineRenderer:** `src/main/java/songscribe/ui/component/score/LineRenderer.java` (`renderAttachments`)

---

## Phase 1: Point Attachments

Each step: fix stacker → fix renderer → verify. Compile after each element.

### ✅ 1A. Articulation (stacker already positions, renderer ignores)

**LayoutConstants.java:**
- Add `public static final int ARTICULATION_OUTSIDE_STAFF_MARGIN_PX = 3;`

**VerticalStackingCalculator.stackArticulations:**
- In the loop, replace uniform margin with conditional:
  ```java
  double margin = LayoutConstants.px(LayoutConstants.ARTICULATION_MARGIN);
  if (Math.abs(note.getYPos()) > 4) {
      margin = LayoutConstants.ARTICULATION_OUTSIDE_STAFF_MARGIN_PX;
  }
  ```

**ArticulationRenderer.renderElement:**
- Rewrite to dispatch: if `ctx.getLayoutResult() != null` → `renderFromLayout`, else → `renderFallback`
- `renderFromLayout`: iterate `note.getArticulations()`, call `layoutResult.getElementBounds(articulation)`, draw at bounds position
    - Staccato: translate Y = `bounds.getTop() + 2` (center 4px dot in 8px content box)
    - Accent: center Y = `bounds.getTop() + 4` (center of 8px box)
    - Note: Articulation uses `getElementBounds` (direct lookup), NOT `findAttachmentBounds` (Articulation extends LineElement, not Attachment)
- `renderFallback`: preserve current logic verbatim for insertion note preview
- Delete dead `renderArticulation` method (line 137-143, zero callers)

### 1B. Tempo (stacker doesn't store, renderer already reads)

**VerticalStackingCalculator.stackTempo:**
- Add TempoAttachment handling before the legacy check:
  ```java
  var tempoAttachment = note.findAttachment(TempoAttachment.class);
  if (tempoAttachment != null) {
      var y = findClearYPosition(tempoAttachment, column.getX(), accumulated,
              LayoutConstants.px(LayoutConstants.TEMPO_MARGIN));
      positionElement(tempoAttachment, column.getX(), y, noteElementPositions);
      addToAccumulated(tempoAttachment, column.getX(), y, accumulated);
      // Skip legacy check since attachment supersedes it
  } else if (note.getTempoChange() != null) {
      // Legacy fallback (keep temporarily, remove in Phase 3)
  }
  ```
- Same pattern for BeatChangeAttachment within the same method.

**TempoRenderer:** Already correct -- uses `layoutYToComponentY`. No changes needed.

**LineRenderer.renderAttachments:** Tempo gate (line 534-540) already uses layout bounds check. No change needed.

### 1C. BeatChange (stacker doesn't store, renderer has coordinate bug)

**VerticalStackingCalculator.stackTempo:**
- Add BeatChangeAttachment handling (same pattern as Tempo above).

**BeatChangeRenderer.getEffectiveBeatChangeYPos:**
- Fix: replace `return (int) bounds.getTop()` with `return layoutYToComponentY(bounds, ctx)`

**LineRenderer.renderAttachments:** Update BeatChange gate (line 543-545) to use layout bounds check:
```java
var beatChangeBounds = layoutResult != null
    ? layoutResult.findAttachmentBounds(note, BeatChangeAttachment.class)
    : null;
if(beatChangeBounds !=null){
    beatChangeRenderer.

render(note, g2, ctx);
}
```

### 1D. Annotation (stacker doesn't store, renderer crashes, coordinate bug)

**VerticalStackingCalculator.stackAnnotations:**
- Add AnnotationAttachment handling:
  ```java
  var annotationAttachment = note.findAttachment(AnnotationAttachment.class);
  if (annotationAttachment != null) {
      var y = findClearYPosition(annotationAttachment, column.getX(), accumulated,
              LayoutConstants.px(LayoutConstants.ANNOTATION_MARGIN));
      positionElement(annotationAttachment, column.getX(), y, noteElementPositions);
      addToAccumulated(annotationAttachment, column.getX(), y, accumulated);
  } else if (note.getAnnotation() != null) {
      // Legacy fallback
  }
  ```

**AnnotationRenderer.getAnnotationYPos:**
- Fix: replace `return (float) bounds.getTop()` with `return (float) layoutYToComponentY(bounds, ctx)`

**LineRenderer.renderAttachments:** Update Annotation gate (line 553-554) to use layout bounds check:
```java
var annotationBounds = layoutResult != null
    ? layoutResult.findAttachmentBounds(note, AnnotationAttachment.class)
    : null;
if(annotationBounds !=null){
    annotationRenderer.

render(note, g2, ctx);
}
```

---

## Phase 2: Range Elements

Range elements span multiple notes. The stacker architecture change: for each column, check if that column's note falls within a range element, and reserve space. Call `positionElement` only on the anchor note's column.

### 2A. Stacker architecture for range elements

**VerticalStackingCalculator.calculateVerticalPositions:**
- Accept `Line line` parameter (already does)
- Before the per-column loop, collect range elements from `line.findRangeElements(Trill.class)`, etc.
- In each per-column stacking method, check range elements that span this column

Alternatively, add new stacking methods (`stackTrillRangeElements`, `stackDynamicsRangeElements`, etc.) that are called per-column but reference the pre-collected range elements.

### 2B. Trill

**VerticalStackingCalculator:**
- Replace `stackTrill` to use `line.findRangeElements(Trill.class)`
- For each column, check if its note falls within any trill's range
- Reserve space on every covered column (add to accumulated)
- Call `positionElement` on anchor note column only
- Use Trill's `getContentWidth()`/`getContentHeight()` for bounds

**TrillRenderer:**
- Wire `renderElement` to read from `layoutResult.findRangeElementBounds(anchorNote, endNote, Trill.class)`
- Or create new method called from LineRenderer that iterates `line.findRangeElements(Trill.class)` and reads layout bounds for each
- Draw "tr" glyph + wavy extension using layout-provided Y position
- X comes from note positions (anchor to end note)

**LineRenderer.renderAttachments:**
- Replace `TrillRenderer.getInstance().renderTrillsFromLine(g2, line, ctx)` with new layout-based call

### 2C. Dynamics (Crescendo/Diminuendo)

**Note:** Dynamics are BELOW the staff, not above. The stacker currently only stacks above. This needs either:
- A separate below-staff stacking pass, or
- Store below-staff positions in LayoutResult without going through the above-staff accumulated area

**VerticalStackingCalculator:**
- Implement `stackDynamics` using `line.findRangeElements(Crescendo.class)` and `line.findRangeElements(Diminuendo.class)`
- Position below the staff (positive Y values, below lowest note bounds)
- Reserve space, call positionElement on anchor note column

**DynamicsRenderer:**
- Wire layout-based `renderCrescendo`/`renderDiminuendo` methods (scaffolding exists)
- Read bounds from `layoutResult.findRangeElementBounds(anchor, end, Crescendo.class)`
- X: anchor note xPos to end note xPos (plus user offsets x1Shift, x2Shift from RangeElement)
- Y: from layout result

**LineRenderer:**
- Replace `renderDynamics` calls to use layout-based rendering

### 2D. Ending

**VerticalStackingCalculator:**
- Add `stackEndings` method using `line.findRangeElements(Ending.class)`
- Endings are above the staff, above all other elements (highest layer)
- Position above accumulated area, call positionElement on anchor note column

**EndingRenderer:**
- `getEffectiveEndingYPos` already reads from layout (Y is partially migrated)
- Complete X migration: compute X positions in the layout engine or stacker, store in Bounds
- The complex X logic (bar line alignment, repeat positioning) should move to the layout phase

**LineRenderer:**
- Replace `renderEndings` to iterate `line.findRangeElements(Ending.class)` and use layout bounds

### 2E. Tuplet

**VerticalStackingCalculator:**
- Add `stackTuplets` method using `line.findRangeElements(Tuplet.class)`
- Most complex: needs stem-aware Y calculation, slope between anchor and end note
- Position above accumulated area on covered columns

**TupletRenderer:**
- Rewrite `renderTuplet` to read positions from LayoutResult
- The slope/quadratic-curve rendering logic stays (it's rendering, not positioning)
- Positioning (which Y, which slope angle) comes from layout

**LineRenderer:**
- Replace `renderTuplets` to iterate `line.findRangeElements(Tuplet.class)` and use layout bounds

---

## Phase 3: Legacy Code Removal

After all elements are migrated and visually verified, remove:

### Renderers (remove legacy methods)

| Renderer             | Methods to remove                                                                                         |
|----------------------|-----------------------------------------------------------------------------------------------------------|
| ArticulationRenderer | `calculateStaccatoY`, `calculateAccentY`, `renderStaccato`, `drawAccent` (once insertion note is handled) |
| TrillRenderer        | `renderTrillsFromLine`, legacy `renderTrill` overload                                                     |
| DynamicsRenderer     | `renderCrescendosFromLine`, `renderDiminuendosFromLine`, `renderDynamicsFromInterval`                     |
| TupletRenderer       | `renderTupletsFromLine`, `TupletCalc` inner class                                                         |
| EndingRenderer       | Legacy X computation code, `renderEndings` IntervalSet path                                               |

### VerticalStackingCalculator (remove legacy fallbacks)

- Remove all `note.getTempoChange()` / `note.getBeatChange()` / `note.getAnnotation()` / `note.isTrill()` / `note.isFermata()` legacy checks from stacking methods
- Only Attachment/RangeElement paths remain

### Data model (remove legacy storage)

| File                        | Remove                                                                                       |
|-----------------------------|----------------------------------------------------------------------------------------------|
| `Line.java`                 | `trillYPos`, `firstSecondEndingYPos` fields + getters/setters                                |
| `Line.java`                 | `IntervalSet` fields: `crescendo`, `diminuendo`, `tuplets`, `firstSecondEndings` + accessors |
| `Note.java`                 | `trill` flag + `isTrill()`/`setTrill()` (replaced by Trill RangeElement)                     |
| `DynamicsIntervalData.java` | Entire class (replaced by Crescendo/Diminuendo fields)                                       |
| `TupletIntervalData.java`   | Entire class (replaced by Tuplet fields)                                                     |
| `IntervalSet.java`          | Evaluate if still used elsewhere; if not, remove                                             |

### LineRenderer (remove legacy dispatch)

- Remove separate `renderDynamics`, `renderTuplets`, `renderEndings` methods
- All rendering goes through the unified `renderAttachments` path with layout bounds checks

### FormatMigrator

- Once legacy Note/Line properties are removed, FormatMigrator becomes the sole migration point from old file format → Attachment/RangeElement model
- Remove migration code for properties that no longer exist
- Eventually, FormatMigrator handles only file format versioning

---

## Implementation Order

Execute phases sequentially with compile + visual test after each element:

1. **1A** Articulation (staccato margin fix + wire to layout)
2. **1B** Tempo (complete stacker)
3. **1C** BeatChange (complete stacker + fix coordinate bug)
4. **1D** Annotation (complete stacker + fix coordinate bug + fix crash)
5. Compile, visual test all point attachments
6. **2B** Trill (simplest range element)
7. **2C** Dynamics (below-staff positioning)
8. **2D** Ending (complex X positioning)
9. **2E** Tuplet (most complex, deferred last)
10. Compile, visual test all range elements
11. **Phase 3** Legacy code removal (only after all rendering is verified correct)

## Verification

After each element migration:
1. `./scripts/compile.sh`
2. User runs app, visually verifies element rendering
3. `mvn test -pl . -Dtest=VerticalStackingCalculatorTest`

After Phase 3 (legacy removal):
1. Full `mvn clean package`
2. Open existing files with all element types
3. Verify no regressions
4. Search for any remaining references to removed code
