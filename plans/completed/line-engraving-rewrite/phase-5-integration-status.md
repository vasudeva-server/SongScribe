# Phase 5 Integration Status

## Completed

### Task 1: LayoutEngine Orchestrator ✅
Created `/src/main/java/songscribe/ui/layout2/LayoutEngine.java`

The LayoutEngine orchestrates all layout calculators in sequence:
1. NoteColumnBuilder - Creates note columns
2. HorizontalSpacingCalculator - Positions columns horizontally
3. VerticalStackingCalculator - Positions elements vertically
4. LineJustificationCalculator - Compresses spacing if needed

Returns a `LayoutResult` with all positioned elements.

### Task 2: LayoutResult Output Structure ✅
Created `/src/main/java/songscribe/ui/layout2/LayoutResult.java`

The LayoutResult provides:
- Note column positions (`getNoteColumn`, `getNoteX`)
- Element bounds (`getElementBounds`, `getElementPosition`)
- Staff geometry (`getStaffTopY`, `getStaffBottomY`, `getLyricBaselineY`)
- Compatibility methods matching `LineElementLayoutResult` interface:
  - `getBounds(Object element)`
  - `findAttachmentBounds(Note, Class<Attachment>)`
  - `findRangeElementBounds(Note, Note, Class<RangeElement>)`
  - `contains(Object)`

### Tests ✅
All layout2 calculator tests pass (41 tests, 0 failures):
- HorizontalSpacingCalculatorTest
- VerticalStackingCalculatorTest
- LineJustificationCalculatorTest

## Remaining Work

### Task 3: Update Rendering Code (IN PROGRESS)

The rendering code needs to be updated to consume the new `layout2.LayoutResult` instead of positions stored on Note objects.

#### Current State
**Old System:**
1. Layout engine calculates positions → stores in Note objects (setXPos/setYPos)
2. Renderers read positions from Note objects (getXPos/getYPos)
3. `ElementRenderContext` holds `LineElementLayoutResult`
4. Some renderers use layout result (TempoRenderer, AnnotationRenderer, FermataRenderer, BeatChangeRenderer)
5. Most renderers use direct position reads (NoteRenderer, LyricsRenderer, ArticulationRenderer, etc.)

**New System Target:**
1. Layout engine calculates positions → stores in LayoutResult
2. Renderers read positions from LayoutResult
3. `ElementRenderContext` holds `layout2.LayoutResult`
4. All renderers use layout result exclusively

#### Integration Steps Required

1. **Update ElementRenderContext** (`src/main/java/songscribe/ui/renderer/ElementRenderContext.java`)
   - Change `layoutResult` field type from `LineElementLayoutResult` to `layout2.LayoutResult`
   - OR: Make it accept both types during transition
   - Update getter/setter methods

2. **Update LineComponent** (`src/main/java/songscribe/ui/component/score/LineComponent.java`)
   - Replace `LineLayoutEngine` with `layout2.LayoutEngine`
   - Update `performLayout()` method to use new engine:
     ```java
     private void performLayout(Graphics2D g2) {
         if (composition == null || line == null) {
             return;
         }

         var lyricsFont = /* get lyrics font */;
         var staffRightMargin = composition.getLineWidth();
         var engine = new layout2.LayoutEngine(g2, lyricsFont, staffRightMargin);

         layoutResult = engine.layout(line);

         if (layoutResult == null) {
             // Handle layout failure (line too long)
             var error = engine.getLastError();
             // Display error to user
             return;
         }

         layoutDirty = false;
     }
     ```
   - Handle layout failures (when line cannot fit with minimum spacing)

3. **Update Renderers to Use LayoutResult**

   **High Priority (direct position reads):**
   - `NoteRenderer` - Replace `note.getXPos()` with `layoutResult.getNoteX(note)` or `layoutResult.getNoteColumn(note).getX()`
   - `LyricsRenderer` - Use lyric baseline from `layoutResult.getLyricBaselineY()`
   - `ArticulationRenderer` - Get positions from LayoutResult
   - `GlissandoRenderer` - Get note positions from LayoutResult

   **Already Using LayoutResult (verify compatibility):**
   - `TempoRenderer` ✓
   - `AnnotationRenderer` ✓
   - `FermataRenderer` ✓
   - `BeatChangeRenderer` ✓
   - `EndingRenderer` ✓
   - `TrillRenderer` ✓
   - `TupletRenderer` ✓
   - `DynamicsRenderer` ✓

4. **Use New Staff Geometry**
   - Update code that references staff positions to use:
     - `layoutResult.getStaffTopY()`
     - `layoutResult.getStaffBottomY()`
     - `layoutResult.getLyricBaselineY()`
   - Currently these are calculated relative to `middleLineY` - may need adjustment

5. **Remove Position Storage on Note Objects (Later - Phase 7)**
   - After all renderers use LayoutResult, remove `xPos` and `yPos` fields from Note
   - Remove `setXPos()`, `setYPos()`, `getXPos()`, `getYPos()` methods
   - This is a breaking change and should be done last

## Risk Assessment

### Low Risk
- ✅ LayoutEngine and LayoutResult are created and tested
- ✅ Compatibility methods allow gradual migration
- ✅ Calculator tests all pass

### Medium Risk
- ⚠️ Switching layout engines is a major change to rendering pipeline
- ⚠️ Different coordinate systems may need reconciliation
- ⚠️ Edge cases in rendering may behave differently

### Mitigation Strategies

1. **Phased Integration**
   - Add feature flag to switch between old and new layout engines
   - Test new system extensively before removing old system
   - Compare rendering output visually (old vs new)

2. **Testing Strategy**
   - Create integration tests for LayoutEngine
   - Visual regression testing for common scores
   - Test edge cases: long lines, many lyrics, complex notation

3. **Rollback Plan**
   - Keep old layout system intact during transition
   - Document which renderers have been updated
   - Allow quick switch back to old system if issues found

## Next Steps

1. Create integration test for complete layout pipeline
2. Add feature flag to LineComponent for layout engine selection
3. Update NoteRenderer as proof-of-concept
4. Visual comparison testing
5. Incrementally update remaining renderers
6. Remove old layout system (Phase 7)

## Files Modified This Session

- ✅ Created: `src/main/java/songscribe/ui/layout2/LayoutEngine.java`
- ✅ Created: `src/main/java/songscribe/ui/layout2/LayoutResult.java`
- ✅ Updated: `plans/line-engraving-rewrite/line-engraving-rewrite.md` (status dashboard)

## Files To Modify Next

- `src/main/java/songscribe/ui/renderer/ElementRenderContext.java`
- `src/main/java/songscribe/ui/component/score/LineComponent.java`
- `src/main/java/songscribe/ui/renderer/NoteRenderer.java` (proof-of-concept)
- `src/main/java/songscribe/ui/renderer/LyricsRenderer.java`
- `src/main/java/songscribe/ui/renderer/ArticulationRenderer.java`
- `src/main/java/songscribe/ui/renderer/GlissandoRenderer.java`
