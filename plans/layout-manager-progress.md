# Layout Manager Implementation Progress

This document tracks the implementation progress of the layout manager plan defined in `layout-manager.md`.

## Phase Status

| Phase | Name | Status | Notes |
|-------|------|--------|-------|
| 1 | Foundation | Complete | LayoutManager class, LayoutChangeMessage, Score integration |
| 2 | Symbol Renaming | Complete | All info/rightInfo renamed to attribution with backward compatibility |
| 3 | Measure Methods (Title & Attribution) | Complete | measureTextBox(), measureTitle(), measureAttribution() |
| 4 | Score Section | Complete | Line.getRequiredHeight(), measureScore(), uniform spacing |
| 5 | Lyrics, Bangla & Translation | Complete | drawBanglaLyrics(), split drawUnderLyrics(), translation header |
| 6 | Footnotes & XML Versioning | Complete | drawFootnotes(), measureFootnotes(), XML version 1.3 |
| 7 | Integration | Not Started | Two-pass rendering, migrate from renderMap |
| 8 | Testing & Polish | Not Started | Unit tests, visual regression, manual QA |

## Current Phase Details

### Phase 1: Foundation - COMPLETE

**Files Created:**
- `src/main/java/songscribe/ui/layout/LayoutManager.java`
- `src/main/java/songscribe/ui/message/LayoutChangeMessage.kt`

**Files Modified:**
- `src/main/java/songscribe/ui/component/Score.java`

**Checklist:**
- [x] Create LayoutManager class with basic structure
- [x] Create LayoutChangeMessage class (Kotlin) with Section and ChangeType enums
- [x] Add LayoutManager field to Score
- [x] Instantiate LayoutManager in Score constructor
- [x] Subscribe Score to LayoutChangeMessage via @Handler
- [x] Wire up basic event flow (invalidation on message receipt)
- [x] Verify compilation

---

### Phase 2: Symbol Renaming - COMPLETE

**Files Modified:**
- [x] `Composition.java` - All info* fields/methods renamed to attribution*
- [x] `Renderer.java` - drawInfo() → drawAttribution(), constant renamed
- [x] `VerticalAdjustment.java` - RIGHT_INFO → ATTRIBUTION enum and methods
- [x] `CompositionSettingsDialog.java` - UI labels updated to "Attribution"
- [x] `ProfileManager.java` - Enum values renamed with legacy key runtime migration
- [x] `ViewIO.java` - Method calls updated, XML elements preserved
- [x] `ExportABCAction.java` - getInfo() → getAttribution()
- [x] `ConvertAction.java` - setInfo() → setAttribution()
- [x] `Converter.java` - setInfo() → setAttribution()
- [x] `Score.java` - RenderStep.INFO → ATTRIBUTION

**Key Implementation Details:**
- XML element names (`rightinfo`, `rightinfostarty`) preserved for backward compatibility
- Profile keys updated with legacy key support for runtime migration
- No profile file rewrites required (runtime migration only)
- All 11 affected Java files successfully compiled and verified

---

### Phase 3: Measure Methods (Title & Attribution) - COMPLETE

**Files Modified:**
- [x] `Renderer.java` - Added measureTextBox(), measureTitle(), measureAttribution()
- [x] `Composition.java` - Default title changed to "Untitled"
- [x] `CompositionIO.java` - Empty titles default to "Untitled" when loading

**Checklist:**
- [x] Add measureTextBox() helper (parallel to drawTextBox(), returns Rectangle)
- [x] Add measureTitle() method using StringUtils.wrapText() and titleMaxAscent calculation
- [x] Add measureAttribution() method using GraphicUtils.getTextBlockWidth()
- [x] Change default title from "A New Song" to "Untitled"
- [x] Default empty titles to "Untitled" when loading from XML
- [x] Verify compilation

**Key Decisions:**
- Export omission via temporary mutation pattern preserved (setTitle("") still works)
- UI validation for empty title deferred to future work (CompositionSettingsDialog)

---

### Phase 4: Score Section - COMPLETE

**Files Modified:**
- [x] `Line.java` - Added getRequiredHeight() with caching, invalidateHeightCache()
- [x] `Renderer.java` - Added measureScore(), calculateUniformRowHeight(), getDefaultRowHeight()
- [x] `LayoutManager.java` - Added cachedUniformRowHeight, updated getRowHeight() and invalidation

**Checklist:**
- [x] Add cachedRequiredHeight field to Line
- [x] Add getRequiredHeight() method calculating vertical extent from all elements
- [x] Add invalidateHeightCache() method
- [x] Invalidate cache in modifiedComposition()
- [x] Add measureScore() to Renderer returning total score bounds
- [x] Add calculateUniformRowHeight() finding max across all lines
- [x] Add getDefaultRowHeight() returning base 104 pixel height
- [x] Add cachedUniformRowHeight to LayoutManager
- [x] Update LayoutManager.getRowHeight() to use renderer calculation
- [x] Update LayoutManager invalidation to clear row height cache
- [x] Verify compilation

---

### Phase 5: Lyrics, Bangla & Translation - COMPLETE

**Files Modified:**
- [x] `Renderer.java` - Refactored drawUnderLyrics(), added drawTranslationBlock(), added measure methods
- [x] `Composition.java` - Added unofficialTranslation boolean flag with getter/setter

**Checklist:**
- [x] Add unofficialTranslation boolean field to Composition
- [x] Refactor drawUnderLyrics() to coordinate lyrics, Bangla, and translation rendering
- [x] Implement calculateLyricsUnionWidth() for union-based centering
- [x] Implement drawTranslationBlock() with header line and margin below header
- [x] Add measureLyrics() method
- [x] Add measureBanglaLyrics() method
- [x] Add measureTranslation() method with header
- [x] Verify compilation
- [x] Fix lyricsMaxY tracking to preserve insertion note positioning
- [x] Add 16px (2 staff line) margins between sections
- [x] Add 1/4 font size margin between translation header and text

**Key Implementation Details:**
- Union centering: All three text blocks (lyrics, Bangla, translation) calculated for max width, then centered as a group
- Vertical spacing: 16px margin between lyrics→Bangla, Bangla→translation, maintained via hasDrawnContent flag
- Translation header: Bold font (not italic), with 1/4 font size margin below before translation text
- Proper baseline positioning: Y coordinates converted from bottom-of-previous + margin to baseline for correct text placement
- lyricsMaxY tracking: Only updated when content is actually drawn, preserving insertion note hit-testing

**Known Issues:**
- Insertion note Y position is ~2.5 staff lines offset (will be addressed in Phase 7 integration)

---

### Phase 6: Footnotes & XML Versioning - COMPLETE

**Files Modified:**
- [x] `Renderer.java` - Completed drawFootnotes(), added measureFootnotes(), added FOOTNOTES_MIN_TOP_MARGIN constant
- [x] `CompositionIO.java` - XML version 1.3, added startElement13()/endElement13(), unofficialTranslation element

**Checklist:**
- [x] Add FOOTNOTES_MIN_TOP_MARGIN constant (5 staff lines = 40px)
- [x] Complete drawFootnotes() with bottom-anchored positioning
- [x] Implement max width constraint (2/3 of line width)
- [x] Center footnotes horizontally
- [x] Check for minimum top margin (5 staff lines) before drawing
- [x] Add measureFootnotes() method
- [x] Bump IO_MINOR_VERSION to 3
- [x] Add XML_UNOFFICIAL_TRANSLATION constant
- [x] Add startElement13() method
- [x] Add endElement13() method with unofficialTranslation parsing
- [x] Update writeComposition() to output unofficialTranslation element
- [x] Update version dispatch in startElement() and endElement()
- [x] Verify compilation and visual rendering

**Key Implementation Details:**
- Bottom-anchored positioning: `footnotesY = score.getPreferredSize().height - bounds.height`
- Max width: 2/3 of composition line width, centered horizontally
- Minimum top margin: 5 staff lines (40px) required above footnotes; if insufficient space, footnotes not drawn
- XML 1.3: Adds `<unofficialTranslation>` element (boolean, only written when true)
- Backward compatibility: Version 1.2 files load normally with unofficialTranslation defaulting to false

---

### Phase 7: Integration - IN PROGRESS (4 of 6 Checkpoints Complete)

**Checkpoint 1: Implement LayoutManager.measure() and coordinate helpers - COMPLETE**

**Files Modified:**
- [x] `LayoutManager.java` - Implemented measure() with staff-line-based margins, coordinate helpers

**Implementation:**
- Added margin constants: ATTRIBUTION_TOP_MARGIN (16px), SCORE_TOP_MARGIN (16px), LYRICS_TOP_MARGIN (40px), BANGLA_LYRICS_TOP_MARGIN (16px), TRANSLATION_TOP_MARGIN (16px), FOOTNOTES_MIN_TOP_MARGIN (40px)
- `measure(Graphics2D)` - Orchestrates measurement pass, calculates Y positions for all 7 sections
- `getMiddleLineY()` - Returns Y coordinate of middle line of first staff
- `getNoteYPos(int yPos, int lineIndex)` - Calculates note Y position using middle line and row height
- `getContentStartY()` - Returns topmost content Y (handles empty title/attribution)
- `getTotalHeight()` - Returns total sheet height by checking sections bottom-to-top
- `getUnderLyricsYPos()` - Returns Y position of lyrics section
- Made `STAFF_LINES_ABOVE` and `STAFF_LINES_BELOW` public in Score.java for LayoutManager access

**Checkpoint 2: Migrate Score methods from renderMap to LayoutManager - COMPLETE**

**Files Modified:**
- [x] `Score.java` - Updated to call layoutManager.measure(), delegate coordinate methods

**Implementation:**
- `paintComponent()` - Calls `layoutManager.measure(g2)` before drawing if invalid
- Added `updateLayoutFromManager()` - Syncs middleLineY and rowHeight from LayoutManager
- `viewChanged()` - Invalidates layout and calls updateLayoutFromManager()
- `getStartY()` - Delegates to `layoutManager.getContentStartY()`
- `getSheetHeight()` - Delegates to `layoutManager.getTotalHeight()`, no throwaway render
- `getUnderLyricsYPos()` - Delegates to `layoutManager.getUnderLyricsYPos()`
- Added `getLayoutManager()` public getter

**Checkpoint 3: Remove renderMap and RenderStep - COMPLETE**

**Files Modified:**
- [x] `Score.java` - Removed renderMap field, RenderStep enum, RenderInfo class, initRenderMap(), getRenderMap()
- [x] `Renderer.java` - Removed RenderStep parameter from drawScore(), updated draw methods to use LayoutManager

**Implementation - Score.java:**
- Removed `private EnumMap<RenderStep, RenderInfo> renderMap` field
- Removed `RenderStep` enum (7 step values)
- Removed `RenderInfo` static class
- Removed `initRenderMap()` method (was called in constructor)
- Removed `getRenderMap()` method
- Removed call to `initRenderMap()` from `init()`
- Kept `EnumMap` import (used by NOTE_PADDING)

**Implementation - Renderer.java:**
- Simplified `drawScore()` - removed startStep parameter, always renders all sections
- Updated `drawTitle()` - Gets bounds from LayoutManager, uses titleBounds.y for positioning
- Updated `drawAttribution()` - Gets bounds from LayoutManager, uses attributionBounds.y
- Updated `drawFootnotes()` - Gets bounds from LayoutManager, skips drawing if height=0
- Height now: `height = score.getLayoutManager().getTotalHeight()`
- Added null check in `calculateUniformRowHeight()` - Sets composition from score if null (for initialization)

**Checkpoint 4: Wire up LayoutChangeMessage posting - COMPLETE**

**Files Modified:**
- [x] `Composition.java` - Added LayoutChangeMessage posting to setters
- [x] `Line.java` - Added LayoutChangeMessage posting to modifiedComposition()
- [x] `VerticalAdjustment.java` - Added LayoutChangeMessage posting to adjustment methods
- [x] `Score.java` - Fixed viewChanged() to measure before updating layout values
- [x] `Renderer.java` - Fixed all measure*() methods to get composition from score (null safety)
- [x] `LayoutManager.java` - Fixed title Y position and removed attributionStartY double-counting

**Implementation - Composition.java:**
- `setTitle()` → Posts TITLE / CONTENT message (with early-return check)
- `setUnderLyrics()` → Posts LYRICS / CONTENT message (with early-return check)
- `setBanglaLyrics()` → Posts BANGLA_LYRICS / CONTENT message (with early-return check)
- `setTranslatedLyrics()` → Posts TRANSLATION / CONTENT message (with early-return check)
- `setFootnotes()` → Posts FOOTNOTES / CONTENT message (with early-return check)
- `setAttribution()` → Posts ATTRIBUTION / CONTENT message (with early-return check)
- `setTitleFont()` → Posts TITLE / FONT message
- `setLyricsFont()` → Posts LYRICS / FONT message
- `setAttributionFont()` → Posts ATTRIBUTION / FONT message
- `setBanglaFont()` → Posts BANGLA_LYRICS / FONT message
- `setFootnoteFont()` → Posts FOOTNOTES / FONT message

**Implementation - Line.java:**
- `modifiedComposition()` → Posts SCORE / CONTENT message (DRY approach - all note/line changes call this)

**Implementation - VerticalAdjustment.java:**
- `adjustAttribution()` → Posts ATTRIBUTION / SIZE message
- `adjustTopSpace()` → Posts TITLE / SIZE message
- `adjustRowHeight()` → Posts SCORE / SIZE message

**Bugs Fixed During Implementation:**
1. **NullPointerException on initialization** - `viewChanged()` called before composition set
   - Solution: Added null check in `viewChanged()` - only measure if composition != null
2. **NullPointerException in measure methods** - Renderer.composition field cached at construction, null during init
   - Solution: All measure*() methods now call `score.getComposition()` with null checks
3. **Text blocks positioned incorrectly on first load** - `viewChanged()` called `updateLayoutFromManager()` before `measure()`
   - Solution: `viewChanged()` now creates temporary Graphics2D to call `measure()` before updating layout values
4. **Attribution top margin too large** - `attributionStartY` was double-counted (calculated position + manual adjustment)
   - Solution: Removed addition of `attributionStartY` in LayoutManager.measure() (line 161)
5. **Title top margin too large** - Title was starting at `topPadding` instead of 0
   - Solution: Changed title Y position to 0 (top of page) in LayoutManager.measure()

**Remaining Checkpoints (TODO):**
- [ ] Checkpoint 5: Add debouncing for repaint (300ms)
- [ ] Checkpoint 6: Update export paths (ExportPDFAction, exportToSVG)

---

## Known Bugs to Fix (Found During Phase 7)

### Bug #1: NullPointerException in calculateUniformRowHeight() - FIXED
**Status:** Fixed
**Issue:** `composition` field in Renderer is null during initialization (set only in drawScore())
**Stacktrace:**
```
SEVERE: Cannot invoke "songscribe.music.Composition.lineCount()" because "this.composition" is null
java.lang.NullPointerException: Cannot invoke "songscribe.music.Composition.lineCount()" because "this.composition" is null
    at songscribe.ui.renderer.Renderer.calculateUniformRowHeight(Renderer.java:603)
    at songscribe.ui.layout.LayoutManager.getRowHeight(LayoutManager.java:324)
    at songscribe.ui.component.Score.updateLayoutFromManager(Score.java:631)
    at songscribe.ui.component.Score.viewChanged(Score.java:620)
    at songscribe.ui.component.Score.initView(Score.java:552)
```
**Root Cause:** `viewChanged()` calls `updateLayoutFromManager()` which calls `getRowHeight()` which calls `calculateUniformRowHeight()`, but Renderer.composition is only set during drawScore()
**Solution:** Added null check in calculateUniformRowHeight() - if composition is null, get it from score.getComposition()
**Files Modified:** Renderer.java lines 602-609

### Bug #2: Layout Positioning Issues - FIXED
**Status:** Fixed
**Issues Found:**
1. Text blocks positioned incorrectly on first document load
2. Attribution top margin too large
3. Title top margin too large
**Root Causes:**
1. `viewChanged()` called `updateLayoutFromManager()` before `measure()` was called
2. `attributionStartY` was being double-counted in LayoutManager
3. Title Y position incorrectly used `topPadding` instead of starting at 0
**Solutions:**
1. Modified `viewChanged()` to create temporary Graphics2D and call `measure()` before `updateLayoutFromManager()`
2. Removed addition of `attributionStartY` in LayoutManager.measure() (already calculated correctly)
3. Changed title bounds Y to 0 in LayoutManager.measure()
**Files Modified:** Score.java, LayoutManager.java

---

---

### Phase 8: Testing & Polish - NOT STARTED

**Tasks:**
- [ ] Create synthetic test compositions for edge cases
- [ ] Unit tests for LayoutManager calculations
- [ ] Unit tests for Line.getRequiredHeight()
- [ ] Test hit-testing and selection
- [ ] Test export with/without title
- [ ] Test version 1.2 to 1.3 migration
- [ ] Visual regression tests
- [ ] Manual QA with real songs
- [ ] Add debug visualization (when DEBUG env var set)
