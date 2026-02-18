# Layout Manager Implementation Summaries

This document contains summaries appended at the end of each implementation phase.

---

## Phase 1: Foundation - Completed 2026-01-23

### Overview

Established the foundational infrastructure for the dynamic vertical layout system without changing any existing behavior.

### Files Created

1. **`src/main/java/songscribe/ui/layout/LayoutManager.java`**
   - Central layout calculation class (skeleton implementation)
   - `EnumMap<Section, Rectangle>` for tracking section bounds
   - Invalidation methods: `invalidate()`, `invalidateFromSection(Section)`
   - Stub coordinate helpers: `getMiddleLineY()`, `getRowHeight()`, `getNoteYPos()`, `getContentStartY()`, `getTotalHeight()`
   - Section bounds accessors: `getTitleBounds()`, `getAttributionBounds()`, `getScoreBounds()`, `getLyricsBounds()`, `getBanglaLyricsBounds()`, `getTranslationBounds()`, `getFootnotesBounds()`

2. **`src/main/java/songscribe/ui/message/LayoutChangeMessage.kt`**
   - Kotlin message class extending `Message`
   - `Section` enum: TITLE, ATTRIBUTION, SCORE, LYRICS, BANGLA_LYRICS, TRANSLATION, FOOTNOTES
   - `ChangeType` enum: CONTENT, FONT, SIZE
   - `heightChanged` boolean property for cascade invalidation decisions

### Files Modified

3. **`src/main/java/songscribe/ui/component/Score.java`**
   - Added imports for `LayoutManager` and `LayoutChangeMessage`
   - Added `layoutManager` field (initialized to null, then set in constructor)
   - Instantiated `LayoutManager(this)` in constructor after renderer creation
   - Added `@Handler` method `onLayoutChanged(LayoutChangeMessage)` that:
     - Calls `layoutManager.invalidateFromSection()` if height changed
     - Calls `repaint()` (TODO: add debouncing in Phase 7)

### Event Flow Established

```
[Model change]
    → MessageCenter.post(LayoutChangeMessage)
        → Score.onLayoutChanged()
            → LayoutManager.invalidateFromSection()
            → Score.repaint()
```

### Technical Decisions

- **Option C approach**: Stub implementations that compile but don't calculate real values
- **Section enum aligned with plan**: Uses ATTRIBUTION (not INFO) and TRANSLATION (not ENGLISH_TRANSLATION)
- **LayoutManager initialization**: Created in Score constructor after renderer, before saxParser

### Verification

- Compilation verified with `mvn compile`
- All new classes compiled to `target/classes/`
- Existing functionality unchanged

---

## Phase 2: Symbol Renaming - Completed 2026-01-23

### Overview

Renamed all `info`/`rightInfo` references to `attribution` across the codebase while preserving XML element names for backward compatibility and adding runtime migration for profile keys.

### Files Modified

1. **`src/main/java/songscribe/music/Composition.java`**
   - Fields renamed: `info` → `attribution`, `infoFont` → `attributionFont`, `infoFontMetrics` → `attributionFontMetrics`, `infoStartY` → `attributionStartY`
   - Methods renamed: `getInfo()` → `getAttribution()`, `setInfo()` → `setAttribution()`, `getInfoFont()` → `getAttributionFont()`, `setInfoFont()` → `setAttributionFont()`, `getInfoFontMetrics()` → `getAttributionFontMetrics()`, `getInfoStartY()` → `getAttributionStartY()`, `setInfoStartY()` → `setAttributionStartY()`, `calculateInfoStartY()` → `calculateAttributionStartY()`
   - Updated profile key reference from `RIGHT_INFORMATION` to `ATTRIBUTION`

2. **`src/main/java/songscribe/ui/ProfileManager.java`**
   - Enum values renamed: `RIGHT_INFORMATION` → `ATTRIBUTION`, `INFO_FONT` → `ATTRIBUTION_FONT`, `INFO_FONT_SIZE` → `ATTRIBUTION_FONT_SIZE`
   - Added `legacyKey` field to `ProfileKey` enum for backward compatibility
   - Modified `getDefaultProperty()` to check legacy keys when new keys are not found (runtime migration)
   - Updated `saveProfile()` to use new field/key names

3. **`src/main/java/songscribe/ui/renderer/Renderer.java`**
   - Constant renamed: `RIGHT_INFO_RIGHT_PADDING` → `ATTRIBUTION_RIGHT_PADDING`
   - Method renamed: `drawInfo()` → `drawAttribution()`
   - Updated local variable name: `info` → `attribution`
   - Updated all `RenderStep.INFO` references to `RenderStep.ATTRIBUTION`
   - Updated `getInfoFont()` calls to `getAttributionFont()`

4. **`src/main/java/songscribe/ui/adjustment/VerticalAdjustment.java`**
   - Enum value renamed: `RIGHT_INFO` → `ATTRIBUTION`
   - Methods renamed: `adjustRightInfo()` → `adjustAttribution()`, `getRightInfoAdjustRect()` → `getAttributionAdjustRect()`
   - Updated all method calls and field references

5. **`src/main/java/songscribe/ui/dialog/CompositionSettingsDialog.java`**
   - Fields renamed: `rightInfoArea` → `attributionArea`, `infoFontLabel` → `attributionFontLabel`, `infoFontPreview` → `attributionFontPreview`
   - UI label updated: "Information on right" → "Attribution"
   - Font tab label updated: "Info" → "Attribution"
   - Updated all method calls and field references

6. **`src/main/java/songscribe/ui/component/Score.java`**
   - `RenderStep.INFO` enum value renamed to `RenderStep.ATTRIBUTION`
   - Updated all references in `initRenderMap()`, `viewChanged()`, and `getStartY()`

7. **`src/main/java/songscribe/io/CompositionIO.java`**
   - **XML element names preserved** for backward compatibility: `XML_INFO = "rightinfo"`, `XML_INFO_STARTY = "rightinfostarty"`
   - Updated method calls: `getInfo()` → `getAttribution()`, `setInfo()` → `setAttribution()`, `getInfoStartY()` → `getAttributionStartY()`, `setInfoStartY()` → `setAttributionStartY()`

8. **`src/main/java/songscribe/io/ViewIO.java`**
   - **XML element names preserved**: `XML_GENERAL_FONT = "generalfont"`, `XML_GENERAL_FONT_SIZE = "generalfontsize"`
   - Updated method calls: `getInfoFont()` → `getAttributionFont()`
   - Updated profile key references: `INFO_FONT` → `ATTRIBUTION_FONT`, `INFO_FONT_SIZE` → `ATTRIBUTION_FONT_SIZE`

9. **`src/main/java/songscribe/ui/action/ExportABCAction.java`**
   - Updated method call: `getInfo()` → `getAttribution()`

10. **`src/main/java/songscribe/converter/Converter.java`**
    - Updated method call: `setInfo()` → `setAttribution()`

11. **`src/main/java/songscribe/uiconverter/ConvertAction.java`**
    - Updated method call: `setInfo()` → `setAttribution()`

### Backward Compatibility

- **XML file format**: Element names (`rightinfo`, `rightinfostarty`, `generalfont`, `generalfontsize`) preserved - existing files load correctly
- **Profile files**: Runtime migration reads legacy keys (`RightInformation`, `GeneralFont`, `GeneralFontSize`) when new keys not found - no file rewriting required
- **Profile file not modified**: The `src/main/resources/profiles/Sri Chinmoy` file was intentionally left unchanged since the runtime migration handles old keys

### Technical Decisions

- **Runtime-only profile migration**: Old profile keys are read at runtime without modifying profile files, as requested by user
- **UI label**: Changed to simple "Attribution" as requested by user
- **Consistent naming**: All internal symbols use `attribution` terminology consistently

### Verification

- Compilation verified with `mvn compiler:compile kotlin:compile`
- All renamed symbols compile correctly
- No remaining references to old `info`/`rightInfo` naming in core classes

---

## Phase 3: Measure Methods (Title & Attribution) - Completed 2026-01-23

### Overview

Added measurement methods to Renderer for calculating title and attribution bounds without rendering, and implemented title defaulting to "Untitled" for new compositions and when loading empty titles from XML.

### Files Modified

1. **`src/main/java/songscribe/ui/renderer/Renderer.java`**
   - Added `measureTextBox(FontMetrics, String, float)` - calculates Rectangle bounds for multi-line text
   - Added `measureTitle(Graphics2D)` - measures title with number prefix and text wrapping
   - Added `measureAttribution(Graphics2D)` - measures attribution text block

2. **`src/main/java/songscribe/music/Composition.java`**
   - Default title changed from `"A New Song"` to `"Untitled"` (line 68)

3. **`src/main/java/songscribe/IO/CompositionIO.java`**
   - `endElement10()`: Empty title defaults to "Untitled" when loading
   - `endElement12()`: Empty title defaults to "Untitled" when loading (for v1.1 and v1.2 files)

### New Methods

```java
// Renderer.java
protected Rectangle measureTextBox(FontMetrics metrics, String str, float leadingAdjustment)
public Rectangle measureTitle(Graphics2D g2)
public Rectangle measureAttribution(Graphics2D g2)
```

### Key Implementation Details

- **measureTextBox()**: Parallel to drawTextBox(), calculates max line width and total height
- **measureTitle()**: Handles number prefix (e.g., "1. Title"), wraps using `StringUtils.wrapText()`, calculates `titleMaxAscent` for combining marks
- **measureAttribution()**: Uses `GraphicUtils.getTextBlockWidth()` for width, delegates to measureTextBox() for height
- **Export pattern preserved**: `setTitle("")` still accepts empty strings to allow export title suppression via temporary mutation
- **UI validation deferred**: CompositionSettingsDialog validation for empty titles to be added in future work

### Verification

- Compilation verified with `mvn compiler:compile kotlin:compile`
- All new methods compile correctly
- Existing rendering behavior unchanged

---

## Phase 4: Score Section - Completed 2026-01-23

### Overview

Added height calculation infrastructure for score lines with caching, measurement methods for the score section, and uniform inter-staff spacing calculation based on maximum required height across all lines.

### Files Modified

1. **`src/main/java/songscribe/music/Line.java`**
   - Added import for `Score` (for `STAFF_LINE_Y_OFFSET` and `NOTE_Y_OFFSET` constants)
   - Added `cachedRequiredHeight` field (int, -1 means not calculated)
   - Added `invalidateHeightCache()` method for explicit cache invalidation
   - Modified `modifiedComposition()` to invalidate the height cache
   - Added `getRequiredHeight()` method that calculates vertical extent based on:
     - Staff lines (5 lines spanning -32 to +32 pixels from middle)
     - Note positions including ledger lines (converted via `NOTE_Y_OFFSET`)
     - Tempo change markers (at `tempoChangeYPos` if `getFirstTempoChange() >= 0`)
     - Beat change markers (at `beatChangeYPos` if `getFirstBeatChange() >= 0`)
     - First/second endings (at `firstSecondEndingYPos` if `!firstSecondEndings.isEmpty()`)
     - Trills (at `trillYPos` if `getFirstTrill() >= 0`)
     - Note annotations at their Y positions (checks `note.getAnnotation().getYPos()`)
     - Inline lyrics (at `lyricsYPos` if any note has `acceleration.syllable`)

2. **`src/main/java/songscribe/ui/renderer/Renderer.java`**
   - Added `measureScore(Graphics2D)` - returns Rectangle with total score section bounds
   - Added `calculateUniformRowHeight()` - finds max required height across all lines, applies `rowHeightAdjustment`, ensures minimum of default row height
   - Added `getDefaultRowHeight()` - returns base 104 pixel height (13 * STAFF_LINE_Y_OFFSET)

3. **`src/main/java/songscribe/ui/layout/LayoutManager.java`**
   - Added `cachedUniformRowHeight` field (int, -1 means not calculated)
   - Updated `invalidate()` to also clear `cachedUniformRowHeight`
   - Updated `invalidateFromSection()` to clear row height cache when SCORE section or above is affected
   - Updated `getRowHeight()` to return cached value or delegate to `score.getRenderer().calculateUniformRowHeight()`

### New Methods

```java
// Line.java
public void invalidateHeightCache()
public int getRequiredHeight()

// Renderer.java
public Rectangle measureScore(Graphics2D g2)
public int calculateUniformRowHeight()
protected int getDefaultRowHeight()

// LayoutManager.java
public int getRowHeight()  // Updated implementation
```

### Key Implementation Details

- **Height calculation uses actual Y-positions**: Respects manual adjustments made by users to tempo, beat change, ending, trill, and lyrics positions
- **Uniform spacing**: Maximum required height across all lines ensures consistent inter-staff spacing for cleaner visual alignment and simpler hit-testing
- **Caching strategy**: Line height is cached in `cachedRequiredHeight`, invalidated when `modifiedComposition()` is called (any content change)
- **Row height caching**: LayoutManager caches uniform row height, invalidated when layout or score section is invalidated
- **Minimum height preserved**: `calculateUniformRowHeight()` ensures result is at least the default 104 pixels to prevent overly compressed layouts

### Technical Decisions

- **Uses actual Y-position fields**: Per user request, height calculation includes tempoChangeYPos, beatChangeYPos, etc. rather than theoretical minimums
- **Syllable detection**: Checks `note.acceleration.syllable` (not a getter method) to determine if lyrics are present
- **Base staff extent**: Staff lines span from -4 to +4 in note units = -32 to +32 pixels from middle line
- **Note margin**: Each note adds ±STAFF_LINE_Y_OFFSET margin above/below its pixel position

### Verification

- Compilation verified with `mvn compiler:compile kotlin:compile`
- Application launched successfully for user inspection
- All new methods integrate with existing caching and invalidation patterns

---

## Phase 5: Lyrics, Bangla & Translation - Completed 2026-01-23

### Overview

Refactored lyrics and translation rendering to support all three text blocks (lyrics, Bangla, translation) with proper union-based centering, hierarchical margins, and translation header styling.

### Files Modified

1. **`src/main/java/songscribe/music/Composition.java`**
   - Added `unofficialTranslation` boolean field (defaults to false)
   - Added `isUnofficialTranslation()` getter and `setUnofficialTranslation(boolean)` setter

2. **`src/main/java/songscribe/ui/renderer/Renderer.java`**
   - Added constants:
     - `BANGLA_LYRICS_TOP_MARGIN` = 16px (2 staff lines)
     - `TRANSLATION_TOP_MARGIN` = 16px (2 staff lines)
     - `TRANSLATION_HEADER_OFFICIAL` = "Sri Chinmoy's translation:"
     - `TRANSLATION_HEADER_UNOFFICIAL` = "Unofficial translation:"
   - Refactored `drawUnderLyrics()` to coordinate all three blocks with hasDrawnContent flag
   - Added `calculateLyricsUnionWidth()` to compute max width across all text blocks
   - Added `drawTranslationBlock()` with header line (bold, no italics) and 1/4 font size margin below
   - Added `measureLyrics()` - measures underLyrics block
   - Added `measureBanglaLyrics()` - measures Bangla lyrics block
   - Added `measureTranslation()` - measures translation block including header

### Key Implementation Details

**Union-Based Centering:**
- All three text blocks measured for maximum width
- Union box centered horizontally on page
- All blocks left-aligned within union box

**Hierarchical Vertical Spacing:**
- Lyrics block: Starts at `score.getUnderLyricsYPos()`
- Bangla block: 16px margin from lyrics (if lyrics present)
- Translation block: 16px margin from Bangla/lyrics (always applied)
- Y coordinates properly converted from "bottom + margin" to baseline for correct text drawing

**Translation Header:**
- Rendered in bold font (not italic)
- 1/4 font size margin below header before translation text begins
- Header text determined by `unofficialTranslation` flag

**Insertion Note Positioning:**
- Fixed regression where `lyricsMaxY` was set even when no lyrics present
- Now only updates `lyricsMaxY` when content actually drawn
- Preserves `lyricsMaxY == 0` check for insertion note hit-testing
- Known issue: Insertion note Y position offset by ~2.5 staff lines (deferred to Phase 7)

### Verification

- Compilation verified with `mvn compiler:compile kotlin:compile`
- Visual rendering tested with underLyrics, Bangla, and translation blocks
- Horizontal centering verified with variable-width text blocks
- Vertical spacing verified: proper margins between all sections
- Translation header style verified: bold, no italics, with margin below

### Remaining Work

- Phase 7 (Integration): Migrate insertion note positioning from renderMap to LayoutManager
- Phase 8 (Testing): Comprehensive testing with edge cases

---

## Phase 7: Integration - In Progress (4 of 6 Checkpoints) - Started 2026-01-23

### Overview

Migrating from renderMap/RenderStep mechanism to LayoutManager-based layout. Completed four checkpoints:
1. LayoutManager.measure() implementation with coordinate helpers
2. Score method migration to use LayoutManager
3. Removal of renderMap and RenderStep from codebase
4. Wire up LayoutChangeMessage posting and fix initialization bugs

### Completed Work

#### Checkpoint 1: LayoutManager Measurement & Coordinate Helpers

**New Implementation:**
- Added staff-line-based margin constants (16px for 2 lines, 40px for 5 lines)
- `measure(Graphics2D)` orchestrates full measurement pass, calculates Y positions for all 7 sections
- Coordinate helpers: `getMiddleLineY()`, `getNoteYPos()`, `getContentStartY()`, `getTotalHeight()`, `getUnderLyricsYPos()`

**Key Features:**
- Conditional margins: Bangla/translation margins only added if prior content exists
- Bottom-anchored footnotes: Positioned from page bottom with minimum margin check
- Fallback calculations: Uses composition data if section bounds not measured

**Files Modified:**
- LayoutManager.java - Complete implementation
- Score.java - Made STAFF_LINES_ABOVE/STAFF_LINES_BELOW public

#### Checkpoint 2: Score Method Migration

**Refactored Methods:**
- `paintComponent()` - Calls layoutManager.measure(g2) before rendering
- `viewChanged()` - Invalidates layout instead of calculating positions
- `getStartY()` - Delegates to layoutManager.getContentStartY()
- `getSheetHeight()` - Delegates to layoutManager.getTotalHeight() (no throwaway render)
- `getUnderLyricsYPos()` - Delegates to layoutManager.getUnderLyricsYPos()

**New Helper:**
- `updateLayoutFromManager()` - Syncs middleLineY and rowHeight from LayoutManager

**Files Modified:**
- Score.java - All coordinate-related methods updated

#### Checkpoint 3: renderMap Removal

**Removed from Score.java:**
- `renderMap` field (EnumMap<RenderStep, RenderInfo>)
- `RenderStep` enum (7 steps: TITLE, ATTRIBUTION, COMPOSITION, LYRICS, BANGLA_LYRICS, ENGLISH_TRANSLATION, FOOTNOTES)
- `RenderInfo` static class (maxY, topPadding)
- `initRenderMap()` method
- `getRenderMap()` method

**Updated in Renderer.java:**
- Simplified `drawScore()` - removed startStep parameter, always renders all sections
- `drawTitle()` - Gets Y position from LayoutManager bounds
- `drawAttribution()` - Gets Y position from LayoutManager bounds
- `drawFootnotes()` - Gets Y position from LayoutManager bounds, skips if height=0

**Files Modified:**
- Score.java - Removed 4 pieces of code
- Renderer.java - Updated 3 draw methods, simplified drawScore()

#### Checkpoint 4: LayoutChangeMessage Posting & Initialization Fixes

**Files Modified:**
- Composition.java - Added LayoutChangeMessage posting to 11 setters with early-return checks
- Line.java - Added LayoutChangeMessage posting to modifiedComposition()
- VerticalAdjustment.java - Added LayoutChangeMessage posting to 3 adjustment methods
- Score.java - Fixed viewChanged() to measure before updating layout values
- Renderer.java - Fixed all 6 measure*() methods to get composition from score with null safety
- LayoutManager.java - Fixed title Y position (0 not topPadding), removed attributionStartY double-counting

**Implementation - Composition.java:**
- Text setters: `setTitle()`, `setUnderLyrics()`, `setBanglaLyrics()`, `setTranslatedLyrics()`, `setFootnotes()`, `setAttribution()`
  - All post CONTENT messages with early-return checks to avoid posting when value unchanged
- Font setters: `setTitleFont()`, `setLyricsFont()`, `setAttributionFont()`, `setBanglaFont()`, `setFootnoteFont()`
  - All post FONT messages

**Implementation - Line.java:**
- `modifiedComposition()` posts SCORE / CONTENT message (DRY approach - all note/line changes call this)

**Implementation - VerticalAdjustment.java:**
- `adjustAttribution()` posts ATTRIBUTION / SIZE message
- `adjustTopSpace()` posts TITLE / SIZE message
- `adjustRowHeight()` posts SCORE / SIZE message

**Bugs Fixed:**

1. **NullPointerException on initialization**
   - **Issue:** `viewChanged()` called before composition set, tried to call measure()
   - **Solution:** Added null check in `viewChanged()` - only measure if composition != null
   - **File:** Score.java

2. **NullPointerException in all measure methods**
   - **Issue:** Renderer.composition field cached at construction time, null during initialization
   - **Solution:** All measure*() methods now get composition from `score.getComposition()` with null checks
   - **Files:** Renderer.java (measureTitle, measureAttribution, measureScore, measureLyrics, measureBanglaLyrics, measureTranslation, measureFootnotes)

3. **Text blocks positioned incorrectly on first document load**
   - **Issue:** `viewChanged()` called `updateLayoutFromManager()` before `measure()` was executed
   - **Solution:** `viewChanged()` now creates temporary Graphics2D context and calls `measure()` before `updateLayoutFromManager()`
   - **File:** Score.java

4. **Attribution top margin too large**
   - **Issue:** `attributionStartY` was double-counted - LayoutManager added it as offset on top of calculated position
   - **Solution:** Removed addition of `attributionStartY` in LayoutManager.measure() (line 161)
   - **File:** LayoutManager.java

5. **Title top margin too large**
   - **Issue:** Title Y position was set to `topPadding` instead of 0 (top of page)
   - **Solution:** Changed `titleBounds.y = 0` in LayoutManager.measure()
   - **File:** LayoutManager.java

### Remaining Checkpoints (Phase 7)

- [ ] **Checkpoint 5:** Add debounced repaint (300ms)
  - Implement debounce timer in Score.onLayoutChanged()
  - Cancel previous timer on new changes

- [ ] **Checkpoint 6:** Update export paths
  - Update ExportPDFAction for LayoutManager integration
  - Update Score.exportToSVG() for LayoutManager bounds
  - Verify "export without title" option works

---

## Phase 6: Footnotes & XML Versioning - Completed 2026-01-23

### Overview

Implemented bottom-anchored footnotes rendering and XML version 1.3 support with the `unofficialTranslation` element.

### Files Modified

1. **`src/main/java/songscribe/ui/renderer/Renderer.java`**
   - Added `FOOTNOTES_MIN_TOP_MARGIN` constant (5 staff lines = 40px)
   - Completed `drawFootnotes()` with bottom-anchored positioning:
     - Uses `score.getPreferredSize().height` for page height
     - Max width constrained to 2/3 of line width
     - Centered horizontally within max width
     - Checks for minimum 5 staff lines margin above; skips drawing if insufficient space
     - Updates renderMap FOOTNOTES.maxY
   - Added `measureFootnotes()` method returning Rectangle bounds

2. **`src/main/java/songscribe/io/CompositionIO.java`**
   - Bumped `IO_MINOR_VERSION` from 2 to 3
   - Added `XML_UNOFFICIAL_TRANSLATION = "unofficialTranslation"` constant
   - Updated `writeComposition()` to output `<unofficialTranslation>true</unofficialTranslation>` when flag is true
   - Added `startElement13()` method (delegates to startElement12)
   - Added `endElement13()` method handling `XML_UNOFFICIAL_TRANSLATION` parsing
   - Updated version dispatch in `startElement()` and `endElement()`

### New Methods

```java
// Renderer.java
public Rectangle measureFootnotes(@NotNull Graphics2D g2)
private void drawFootnotes(Graphics2D g2)  // completed implementation

// CompositionIO.java
public void startElement13(String uri, String localName, String qName, Attributes attributes)
public void endElement13(String qName)
```

### Key Implementation Details

**Bottom-Anchored Positioning:**
- Footnotes Y position: `score.getPreferredSize().height - bounds.height`
- This aligns the footnotes bottom edge with the Score component's bottom edge
- The marginPanel border (40px) provides the visual margin outside the Score component

**Space Checking:**
- Minimum 5 staff lines (40px) required between content above and footnotes
- If insufficient space, footnotes are not drawn (placeholder for future pagination)

**XML Version 1.3:**
- Only `<unofficialTranslation>` element added (written only when true)
- Backward compatible: Version 1.2 files load with unofficialTranslation defaulting to false
- Empty titles continue to default to "Untitled"

### Verification

- Compilation verified with `mvn compiler:compile kotlin:compile`
- Visual rendering tested: footnotes appear at page bottom with correct margins
- Bottom margin matches top margin (both controlled by marginPanel border)
- XML round-trip tested: version 1.3 files save and load correctly

---
