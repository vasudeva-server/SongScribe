# Vertical Layout System - Implementation Specification

## Overview

This specification defines a dynamic vertical layout system for SongScribe music sheets. The system replaces the current
static positioning with calculated layouts that respond to content changes.

## Architecture

### LayoutManager

A new `LayoutManager` class owned by `Score` that:

- Maintains centralized tracking of all section bounds
- Coordinates the two-pass rendering (measure → draw)
- Posts `LayoutChangeMessage` to MessageCenter when layout changes
- Respects existing manual adjustments
- **Replaces** the existing `RenderStep` enum and `renderMap` in Score entirely
- **Lifecycle**: Single instance created in Score constructor, invalidated when composition changes

```
Score (UI Component)
├── owns LayoutManager
│   ├── calculates section positions
│   ├── tracks bounds (Map<Section, Rectangle>)
│   └── posts LayoutChangeMessage to MessageCenter
└── owns Composition (Data Model)
    └── provides content, fonts, settings
```

### Event System

Uses the existing MBassador-based `MessageCenter` infrastructure rather than a custom listener system.

**Implementation Language**: `LayoutChangeMessage` will be implemented in **Kotlin** to match the `Message.kt` base class.

```kotlin
// New message class
class LayoutChangeMessage(
    val section: Section,       // Which section changed
    val type: ChangeType,       // CONTENT, FONT, SIZE
    val heightChanged: Boolean  // Whether recalculation needed below
) : Message() {
    enum class ChangeType { CONTENT, FONT, SIZE }
    enum class Section { TITLE, ATTRIBUTION, SCORE, LYRICS, BANGLA_LYRICS, TRANSLATION, FOOTNOTES }
}

// Posting changes (from Java code)
MessageCenter.post(new LayoutChangeMessage(
    LayoutChangeMessage.Section.TITLE,
    LayoutChangeMessage.ChangeType.CONTENT,
    true
));

// Subscribing (in Score or other listeners)
@Handler
public void onLayoutChanged(LayoutChangeMessage message) {
    if (message.isHeightChanged()) {
        invalidateFromSection(message.getSection());
    }
    scheduleRepaint();
}
```

### Section Interface

Each section provides:

- `measure*(Graphics2D)` - Calculate height without drawing
- `draw*(Graphics2D)` - Draw at position from LayoutManager
- Height caching where applicable

## Sections (Top to Bottom)

### 1. Title

- Centered horizontally within page margins
- Fixed top margin (existing constant)
- Max width: 75% of page width (existing wrapping logic preserved)
- **Empty title handling**: Title can never be empty in the editor. When reading a document or when editing clears the
  title, it defaults to "Untitled". This eliminates the need for space reservation logic.
- **Export behavior**: The export omission option is preserved via temporary mutation pattern:
    - Image export: "Export without title" checkbox works unchanged
    - PDF/SVG CLI: `--withoutSongTitle` flag works unchanged
    - Existing code temporarily sets title to empty string for rendering, then restores it
- Changes trigger: title text, title font/size

### 2. Attribution

- Top margin: **2 staff lines** below Title
- Bottom margin: **2 staff lines** above Score
- Right-aligned against right margin
- Center-aligned within its bounding box
- First line: composer font; subsequent lines: 90% size
- Changes trigger: attribution text, font/size
- **Note**: Renaming from "info"/"rightInfo" affects multiple files beyond core data model (see Symbol Renaming section)

### 3. Score

- Positioned 2 staff lines below Attribution (when no annotations)
- **Annotations above first staff** (tempo, dynamics):
    - Annotations include a 3 staff line bottom margin (gap to notes/articulations)
    - This margin is part of the annotation, not the score
    - If no annotations exist, this margin collapses
    - Tall annotations push the score down to maintain the 3 staff line gap
- Extends from left to right margin
- Height: sum of all line heights (dynamic per-line calculation)
- **Uniform inter-staff spacing**: All gaps match the largest required gap across the entire composition
- Changes trigger: add/remove lines, note/annotation changes, font/size

#### Score Line

Each `Line` provides `getRequiredHeight()` which calculates height based on actual content:

**Above the staff:**

- Notes extending above staff (ledger lines, high pitches)
- Articulations above notes (accents, staccatos, fermatas)
- Tempo changes (`tempoChangeYPos`)
- Beat changes (`beatChangeYPos`)
- First/second endings (`firstSecondEndingYPos`)
- Trills (`trillYPos`)
- Dynamics above staff
- Tuplet brackets above
- Annotations (text annotations with custom Y positions)

**The staff itself:**

- Fixed 5 lines

**Below the staff:**

- Notes extending below staff (ledger lines, low pitches)
- Articulations below notes
- Dynamics below staff
- Tuplet brackets below
- Slurs extending below
- Inline lyrics (syllabified, positioned 1 staff line below lowest point)

**Caching:**

- `Line` caches its calculated height
- Cache invalidated when any content changes (notes added/removed, annotations changed, etc.)
- Cache key includes: note count, annotation presence, lyrics presence, manual adjustments

Lyrics positioned: 1 staff line below lowest point of staff (existing constant + manual offset preserved)

### 4. Lyrics Block

- Top margin: 5 staff lines below Score
- Text: left-aligned within block
- Block: centered horizontally on page (when no translation)
- If translation exists: left-aligned within union of lyrics + translation bounding boxes
- Changes trigger: lyrics text, lyrics font/size

### 5. Bangla Lyrics

- Positioned AFTER Lyrics Block in render order
- Top margin: 2 staff lines below Lyrics Block
- Text: left-aligned within block
- Block: left-aligned within union of lyrics + Bangla + translation bounding boxes
- Uses `Composition.banglaFont` and `banglaFontMetrics` for measurement
- Changes trigger: Bangla lyrics text, Bangla font/size
- **Implementation note**: `drawBanglaLyrics()` currently exists as a stub (Renderer.java:1482-1492) but is never called. **Bangla lyrics rendering is currently a no-op placeholder** - the layout reserves space for it, but actual rendering is not implemented. Full implementation and wiring into `drawScore()` method will be required in the future.

### 6. Translation Block (Optional)

- Top margin: 2 staff lines below Bangla Lyrics (or Lyrics Block if no Bangla lyrics)
- Header line: semibold italic, text determined by `Composition.unofficialTranslation` flag
    - If `unofficialTranslation` is true: "Unofficial translation:"
    - If `unofficialTranslation` is false or absent (default): "Sri Chinmoy's translation:"
    - Flag persisted in XML as `<unofficialTranslation>` boolean element
- Text: left-aligned within block
- Block: left-aligned within union of lyrics + translation bounding boxes
- Changes trigger: translation text, lyrics font/size, unofficialTranslation flag

### 7. Footnotes Block (Optional)

- **Positioned: bounding box just above bottom margin** (bottom-anchored, not sequential)
- This requires measuring total content height first, then calculating footnote position relative to page bottom
- Max width: 2/3 of page width
- Centered horizontally
- Font: 90% of lyrics font, italic
- Overflow: clipped (no reflow or pagination yet)
- Changes trigger: footnotes text, font/size
- **Implementation note**: `drawFootnotes()` exists as a stub (Renderer.java:1494-1504) and IS called from
  `drawScore()`, but requires full implementation with bottom-anchored positioning logic.

## Manual Adjustments

All manual adjustments are **additive** — the layout calculates natural positions, then adjustments shift them.

### Composition-Level Adjustments

| Field                 | Location      | Applies To       | Behavior                                                    |
|-----------------------|---------------|------------------|-------------------------------------------------------------|
| `topPadding`          | `Composition` | Title top margin | Additive to fixed margin                                    |
| `attributionStartY`   | `Composition` | Attribution Y    | Additive to calculated position (renamed from `infoStartY`) |
| `rowHeightAdjustment` | `Composition` | Inter-staff gap  | Additive to uniform spacing                                 |

### Per-Line Adjustments

| Field                   | Location | Applies To           | Behavior                         |
|-------------------------|----------|----------------------|----------------------------------|
| `lyricsYPos`            | `Line`   | Inline lyrics        | Additive per-line, default 50    |
| `tempoChangeYPos`       | `Line`   | Tempo mark position  | Additive per-line, default 0/-24 |
| `beatChangeYPos`        | `Line`   | Beat change position | Additive per-line, default -24   |
| `firstSecondEndingYPos` | `Line`   | Ending bracket       | Additive per-line, default -25   |
| `trillYPos`             | `Line`   | Trill position       | Additive per-line, default -27   |
| `noteDistChangeRatio`   | `Line`   | Horizontal spacing   | Multiplier for note distances    |

### Per-Note/Element Adjustments

| Field                          | Location               | Applies To              | Behavior                        |
|--------------------------------|------------------------|-------------------------|---------------------------------|
| Annotation Y position          | `Note.annotation`      | Text annotations        | Additive to default position    |
| `TupletIntervalData.yPosition` | `TupletIntervalData`   | Tuplet bracket vertical | Additive to calculated position |
| `DynamicsIntervalData.yShift`  | `DynamicsIntervalData` | Dynamics vertical shift | Additive to default position    |
| `SlurData` control points      | `SlurData`             | Slur curve shape        | Control points for bezier curve |

## Data Model Mapping

| Section       | Data Source                    | Notes                                                                                      |
|---------------|--------------------------------|--------------------------------------------------------------------------------------------|
| Title         | `Composition.title`            | Multi-line, wrapped; defaults to "Untitled" if empty                                       |
| Attribution   | `Composition.attribution`      | Renamed from `rightInfo`                                                                   |
| Score         | `Composition.lines[]`          | Includes inline syllabified lyrics                                                         |
| Lyrics Block  | `Composition.underLyrics`      | Separate block below score                                                                 |
| Bangla Lyrics | `Composition.banglaLyrics`     | Rendered AFTER Lyrics Block; uses `banglaFont`/`banglaFontMetrics`                         |
| Translation   | `Composition.translatedLyrics` | Below Bangla lyrics; header determined by `Composition.unofficialTranslation` boolean flag |
| Footnotes     | `Composition.footnotes`        | Bottom-anchored positioning                                                                |

## Layout Calculation

### Two-Pass Rendering

**Pass 1: Measure** (calculates bounds without drawing)

```java
layoutManager.measureTitle(g2d);
layoutManager.

measureAttribution(g2d);
layoutManager.

measureScore(g2d);  // Includes calculating uniform inter-staff spacing
layoutManager.

measureLyrics(g2d);
layoutManager.

measureBanglaLyrics(g2d);
layoutManager.

measureTranslation(g2d);
layoutManager.

measureFootnotes(g2d);  // Bottom-anchored calculation
layoutManager.

finalizeLayout();
```

**LayoutManager API Requirements:**

The LayoutManager must provide coordinate helpers for hit-testing and rendering:

```java
// Coordinate helpers (for Score.java methods that currently use renderMap)
public int getMiddleLineY();            // Y of first staff's middle line

public int getRowHeight();              // Uniform spacing between staves

public int getNoteYPos(int yPos, int lineIndex);  // Note position calculation

public int getContentStartY();          // Topmost content (replaces getStartY)

public int getTotalHeight();            // Total sheet height

// Section bounds accessors
public Rectangle getTitleBounds();

public Rectangle getAttributionBounds();

public Rectangle getScoreBounds();

public Rectangle getLyricsBounds();

public Rectangle getBanglaLyricsBounds();

public Rectangle getTranslationBounds();

public Rectangle getFootnotesBounds();
```

**Pass 2: Draw** (renders at calculated positions)

```java
renderer.drawTitle(g2d, layoutManager.getTitleBounds());
    renderer.

drawAttribution(g2d, layoutManager.getAttributionBounds());
    renderer.

drawScore(g2d, layoutManager.getScoreBounds());
    renderer.

drawLyrics(g2d, layoutManager.getLyricsBounds());
    renderer.

drawBanglaLyrics(g2d, layoutManager.getBanglaLyricsBounds());
    renderer.

drawTranslation(g2d, layoutManager.getTranslationBounds());
    renderer.

drawFootnotes(g2d, layoutManager.getFootnotesBounds());
```

### Caching and Invalidation Strategy

**Section-Level Caching:**

- LayoutManager caches bounds for each section (Rectangle per Section enum)
- Cache is keyed by section; invalidation cascades to sections below when height changes

**Invalidation Rules:**

| Change Type                  | Sections Invalidated                                              |
|------------------------------|-------------------------------------------------------------------|
| Title text/font              | Title, Attribution, Score, Lyrics, Bangla, Translation, Footnotes |
| Attribution text/font        | Attribution, Score, Lyrics, Bangla, Translation, Footnotes        |
| Score content (notes, lines) | Score, Lyrics, Bangla, Translation, Footnotes                     |
| Lyrics text/font             | Lyrics, Bangla, Translation, Footnotes                            |
| Bangla text/font             | Bangla, Translation, Footnotes                                    |
| Translation text/font        | Translation, Footnotes                                            |
| Footnotes text/font          | Footnotes only                                                    |

**Line-Level Caching:**

- Each `Line` caches its `requiredHeight`
- Invalidated when: notes added/removed, annotations changed, lyrics changed, manual adjustments changed
- Uniform spacing recalculated when any line's height changes

**Debouncing:**

- 300ms delay during rapid changes (typing, dragging)
- Debounce timer resets on each change
- Final recalculation occurs 300ms after last change
- Immediate recalculation for discrete events (font change, line add/remove)

### Score.java renderMap Migration

The following Score.java methods currently depend on renderMap and must be migrated to use LayoutManager:

1. **viewChanged()** (lines 637-653)
    - Current: `var topPadding = renderMap.get(RenderStep.INFO).maxY;`
    - After: `middleLineY = layoutManager.getMiddleLineY();`
    - After: `rowHeight = layoutManager.getRowHeight();`

2. **getNoteYPos(int yPos, int line)** (lines 738-742)
    - Current: Uses `middleLineY` and `rowHeight` from viewChanged()
    - After: Delegate to `layoutManager.getNoteYPos(yPos, line)`
    - **CRITICAL**: All hit-testing depends on this being accurate

3. **getUnderLyricsYPos()** (lines 744-752)
    - Current: Calculates from `middleLineY + (lineCount * rowHeight)`
    - After: `return layoutManager.getLyricsBounds().y;`

4. **getStartY()** (lines 2215-2229)
    - Current: Complex logic for empty title handling
    - After: `return layoutManager.getContentStartY();`
    - **Simplified**: Empty title logic can be REMOVED (title never empty in editor)

5. **getSheetHeight()** (lines 2239-2254)
    - Current: Triggers throwaway render if height unknown
    - After: `return layoutManager.getTotalHeight();`
    - **Performance win**: No throwaway rendering needed

6. **Selection hit-testing** (calculateSelection, updateSelection, mouseMoved)
    - Depends on getNoteYPos() accuracy
    - No direct changes, but must test thoroughly after LayoutManager integration

### Layout Change Notification Emission

LayoutChangeMessage must be posted from model classes when layout-affecting changes occur:

**Composition.java** - After text/font changes:

```java
public void setTitle(String text) {
    // ... existing code ...
    title = processText(strippedTitle);
    infoStartY = calculateInfoStartY();

    // NEW:
    MessageCenter.post(new LayoutChangeMessage(
        Section.TITLE, ChangeType.CONTENT, true));
}

// Similar for: setAttribution(), setUnderLyrics(), setBanglaLyrics(),
// setTranslatedLyrics(), setFootnotes(), setTitleFont(), setAttributionFont(), setLyricsFont()
```

**Line.java** - After note/annotation changes:

```java
public void addNote(Note note) {
    notes.add(note);
    invalidateHeightCache();

    // NEW:
    MessageCenter.post(new LayoutChangeMessage(
        Section.SCORE, ChangeType.CONTENT, true));
}

// Similar for: removeNote(), setTempo(), etc.
```

**VerticalAdjustment.java** - After adjustment changes:

```java
public void adjustAttribution(int delta) {
    composition.setAttributionStartY(
        composition.getAttributionStartY() + delta);

    // NEW:
    MessageCenter.post(new LayoutChangeMessage(
        Section.ATTRIBUTION, ChangeType.SIZE, true));
}
```

**Score.java** - Subscribe to messages:

```java

@Handler
public void onLayoutChanged(LayoutChangeMessage message) {
    if (message.isHeightChanged()) {
        layoutManager.invalidateFromSection(message.getSection());
    }
    scheduleRepaintWithDebounce(300); // 300ms debouncing
}
```

## Export and Scaling

### Scale-Independent Measurements

LayoutManager always measures at 1.0 scale (screen resolution). Export applies scale transformation:

```java
// In PDFConverter/ExportPDFAction
Graphics2D g2 = createGraphics();
g2.

scale(exportScale, exportScale);  // Scale transformation applied to Graphics2D
renderer.

drawScore(g2, layoutManager);  // LayoutManager bounds are scale-independent
```

**Benefits:**

- Clean separation of concerns
- Measurements cached once, valid for any export scale
- No precision issues from scale-dependent calculations
- Same LayoutManager instance serves screen and export

**Export Flow:**

1. LayoutManager measures at 1.0 scale
2. Export calculates scale factor based on paper size
3. Graphics2D transform applies scale
4. Renderer draws using LayoutManager bounds (scaled by transform)

## Margins Summary

| Margin                     | Value         | Pixels (at STAFF_LINE_Y_OFFSET=8) | Notes                                           |
|----------------------------|---------------|-----------------------------------|-------------------------------------------------|
| Title top margin           | Fixed         | Existing constant                 | Plus `topPadding` adjustment                    |
| Attribution margin (above) | 2 staff lines | 16px                              | From Title bottom                               |
| Attribution margin (below) | 2 staff lines | 16px                              | To Score/Annotations                            |
| Annotation bottom margin   | 3 staff lines | 24px                              | Part of annotation; collapses if no annotations |
| Inline lyrics margin       | 1 staff line  | 8px                               | Staff to lyrics                                 |
| Lyrics block margin        | 5 staff lines | 40px                              | Score to lyrics block                           |
| Bangla lyrics margin       | 2 staff lines | 16px                              | Lyrics block to Bangla                          |
| Translation block margin   | 2 staff lines | 16px                              | Bangla (or Lyrics) to translation               |

**Note:** These are intentional changes from current pixel values (INFO=13px, COMPOSITION=7px, LYRICS=27px) to
consistent staff-line-based units. Existing documents may render with slightly different spacing.

## Behavior Rules

| Scenario                     | Behavior                                                        |
|------------------------------|-----------------------------------------------------------------|
| Content overflow             | Allow overflow; pagination handled later                        |
| Inter-staff spacing          | Uniform across entire composition (max gap used everywhere)     |
| Annotations                  | Include 3 staff line bottom margin; collapses if no annotations |
| Lyrics/translation alignment | Use union width always                                          |
| Empty title                  | Defaults to "Untitled"; never zero height                       |
| Empty optional sections      | Zero height; maintain section order                             |
| Manual adjustments           | Preserved and respected by layout                               |
| Animations                   | Instant (no smooth transitions)                                 |
| Units                        | Pixels internally; convert from staff lines at boundaries       |
| Rendering targets            | LayoutManager drives both on-screen rendering and PDF export    |
| Scale handling               | Scale-independent measurements; transform applied to Graphics2D |

## Compatibility Notes

### XML File Format

- **Version bump**: File format version changes from 1.2 to 1.3
- **XML element names preserved**: `rightinfo`, `rightinfostarty` remain unchanged for backward compatibility
- **New elements**: `<unofficialTranslation>` added (boolean, defaults to false if missing)
- **Reading old files**: Files without `unofficialTranslation` default to false
- **Reading old files**: Files without title default to "Untitled"

### renderMap Replacement

The existing `RenderStep` enum and `renderMap` in Score are **completely replaced** by LayoutManager:

| Old (Score.java)                         | New (LayoutManager)                      |
|------------------------------------------|------------------------------------------|
| `RenderStep` enum                        | `LayoutChangeMessage.Section` enum       |
| `RenderInfo` class                       | `Rectangle` bounds per section           |
| `renderMap: Map<RenderStep, RenderInfo>` | `sectionBounds: Map<Section, Rectangle>` |
| `getRenderMap()`                         | `getBounds(Section)`                     |
| Padding in `RenderInfo.topPadding`       | Calculated margins in LayoutManager      |

### Symbol Renaming (Complete List)

**Composition.java:**

| Old                    | New                           |
|------------------------|-------------------------------|
| `info` (field)         | `attribution`                 |
| `getInfo()`            | `getAttribution()`            |
| `setInfo()`            | `setAttribution()`            |
| `infoStartY`           | `attributionStartY`           |
| `getInfoStartY()`      | `getAttributionStartY()`      |
| `setInfoStartY()`      | `setAttributionStartY()`      |
| `infoFont`             | `attributionFont`             |
| `getInfoFont()`        | `getAttributionFont()`        |
| `setInfoFont()`        | `setAttributionFont()`        |
| `infoFontMetrics`      | `attributionFontMetrics`      |
| `getInfoFontMetrics()` | `getAttributionFontMetrics()` |

**Renderer.java:**

| Old          | New                 |
|--------------|---------------------|
| `drawInfo()` | `drawAttribution()` |

**VerticalAdjustment.java:**

| Old                        | New                          |
|----------------------------|------------------------------|
| `AdjustType.RIGHT_INFO`    | `AdjustType.ATTRIBUTION`     |
| `adjustRightInfo()`        | `adjustAttribution()`        |
| `getRightInfoAdjustRect()` | `getAttributionAdjustRect()` |

**CompositionSettingsDialog.java:**

| Old          | New           |
|--------------|---------------|
| "Right info" | "Attribution" |

**ProfileManager.java:**

- Update profile key names and implement automatic migration from old keys to new keys
- Old keys (e.g., `RIGHT_INFORMATION`) should be automatically converted to new keys (e.g., `ATTRIBUTION`) when loading profiles

**ViewIO.java:**

- Update view serialization (maintain backward compatibility)

**ExportABCAction.java:**

- Update ABC export references to use new terminology

**ConvertAction.java:**

- Update converter references

**Converter.java:**

- Update base converter class

**Note:** XML element names (`rightinfo`, `rightinfostarty`) remain unchanged for file compatibility.

## Debug Visualization

When the `DEBUG` environment variable is set, a menu option will appear (e.g., in View menu: "Show Layout Bounds") that allows toggling section bounding box visualization. The menu option is only visible when `DEBUG` is set, making this a developer-only feature.

## Files to Modify

| File                                | Path                                                                | Changes                                                                                                                                                                                                                                               |
|-------------------------------------|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Score.java`                        | `src/main/java/songscribe/ui/component/Score.java`                  | Add LayoutManager field; integrate with rendering; remove `renderMap` and `RenderStep`; subscribe to LayoutChangeMessage; migrate methods from renderMap (viewChanged, getNoteYPos, getUnderLyricsYPos, getStartY, getSheetHeight)                    |
| `Renderer.java`                     | `src/main/java/songscribe/ui/renderer/Renderer.java`                | Add `measureTextBox()` helper (parallel to `drawTextBox()`); add measure*() methods; rename `drawInfo()` → `drawAttribution()`; complete `drawBanglaLyrics()` implementation; complete `drawFootnotes()` implementation; wire Bangla lyrics into `drawScore()`; modify draw*() to use positions from LayoutManager |
| `Line.java`                         | `src/main/java/songscribe/music/Line.java`                          | Add `getRequiredHeight()` method with caching; add cache invalidation on content changes; post LayoutChangeMessage on note/annotation changes                                                                                                         |
| `Composition.java`                  | `src/main/java/songscribe/music/Composition.java`                   | Rename `info` → `attribution`, `infoStartY` → `attributionStartY`, `infoFont` → `attributionFont`; add `unofficialTranslation` boolean flag; add empty title → "Untitled" defaulting; post LayoutChangeMessage on content/font changes                |
| `CompositionIO.java`                | `src/main/java/songscribe/io/CompositionIO.java`                    | Add version 1.3 with explicit read13() method; update version dispatch in read(); add `<unofficialTranslation>` XML reading/writing; handle title defaulting; maintain backward compatibility                                                         |
| `VerticalAdjustment.java`           | `src/main/java/songscribe/ui/adjustment/VerticalAdjustment.java`    | Rename `RIGHT_INFO` → `ATTRIBUTION` and related methods; post LayoutChangeMessage on adjustments                                                                                                                                                      |
| `CompositionSettingsDialog.java`    | `src/main/java/songscribe/ui/dialog/CompositionSettingsDialog.java` | Update UI labels from "Right info" to "Attribution"                                                                                                                                                                                                   |
| `ProfileManager.java`               | (path TBD)                                                          | Update profile key names; implement automatic migration from old keys to new keys when loading profiles                                                                                                                                               |
| `ViewIO.java`                       | (path TBD)                                                          | Update view serialization; maintain backward compatibility                                                                                                                                                                                            |
| `ExportABCAction.java`              | `src/main/java/songscribe/ui/action/ExportABCAction.java`           | Update ABC export references to use new terminology                                                                                                                                                                                                   |
| `ConvertAction.java`                | (path TBD)                                                          | Update converter references                                                                                                                                                                                                                           |
| `Converter.java`                    | `src/main/java/songscribe/converter/Converter.java`                 | Update base converter class                                                                                                                                                                                                                           |
| `PDFConverter.java`                 | `src/main/java/songscribe/converter/PDFConverter.java`              | Route rendering through LayoutManager (same as UI)                                                                                                                                                                                                    |
| `ExportPDFAction.java`              | `src/main/java/songscribe/ui/action/ExportPDFAction.java`           | Ensure scale-independent LayoutManager integration; verify "export without title" option                                                                                                                                                              |
| **New: `LayoutManager.java`**       | `src/main/java/songscribe/ui/layout/LayoutManager.java`             | Central layout calculation and tracking (replaces RenderStep/RenderMap); provides coordinate helpers for hit-testing                                                                                                                                  |
| **New: `LayoutChangeMessage.kt`**   | `src/main/java/songscribe/ui/message/LayoutChangeMessage.kt`        | Message class (Kotlin) for MessageCenter integration                                                                                                                                                                                                  |

## Implementation Phases

### Implementation Strategy

Each phase should leave the application in a **working, testable state**. This allows for:
- Incremental testing after each phase
- Multiple commits rather than one large change
- Easier debugging if issues arise
- Ability to pause implementation between phases if needed

Phases 1-2 establish the foundation but won't change visible behavior. Phase 3 onwards will incrementally replace renderMap-based layout with LayoutManager-based layout. The application should remain functional throughout.

### Phase 1: Foundation

- Create `LayoutManager` class (Java) with basic structure
- Create `LayoutChangeMessage` class (Kotlin) for MessageCenter integration with Section and ChangeType enums
- Add `LayoutManager` field to `Score` (single instance, created in constructor)
- Subscribe Score to LayoutChangeMessage
- Wire up basic event flow (no actual layout calculation yet)

### Phase 2: Symbol Renaming (EXPANDED SCOPE)

- Rename all `info`/`rightInfo` references to `attribution` in core classes:
    - Composition.java (fields, getters, setters)
    - Renderer.java (drawInfo → drawAttribution)
    - VerticalAdjustment.java (AdjustType.RIGHT_INFO → ATTRIBUTION)
- Update UI labels and references:
    - CompositionSettingsDialog.java ("Right info" → "Attribution")
    - ProfileManager.java (profile key names, implement automatic migration from old keys)
    - ViewIO.java (view serialization, maintain backward compatibility)
- Update export and converter references:
    - ExportABCAction.java (ABC export references)
    - ConvertAction.java (converter references)
    - Converter.java (base converter class)
- Preserve XML element names (`rightinfo`, `rightinfostarty`) for file compatibility
- Test that existing files still load correctly

### Phase 3: Measure Methods (Title & Attribution)

- Add `Renderer.measureTextBox()` helper (parallel to existing `drawTextBox()` in Renderer, returns Rectangle without rendering)
- Add `measureTitle()` to Renderer using measureTextBox()
- Add `measureAttribution()` to Renderer using measureTextBox()
- Implement title defaulting to "Untitled" in Composition.setTitle():
    - Editor behavior: Title defaults to "Untitled" and cannot be cleared
    - Composition.java: Default from "A New Song" → "Untitled" (line 68)
    - CompositionIO: Files with no title load as "Untitled"
- Preserve export omission via temporary mutation pattern:
    - Image export: "Export without title" checkbox works unchanged
    - PDF/SVG CLI: `--withoutSongTitle` flag works unchanged
    - Existing temporary setTitle("") pattern unchanged
- Test: Verify measurements produce correct bounds

### Phase 4: Score Section

- Add `Line.getRequiredHeight()` with caching
    - Include all per-line elements in height calculation (tempo, beat, endings, trills, annotations, inline lyrics)
    - Cache invalidation when: notes added/removed, annotations changed, lyrics changed, manual adjustments changed
- Add `measureScore()` to Renderer
- Implement uniform inter-staff spacing calculation (max across composition):
    - Calculate max required height across all Line.getRequiredHeight() values
    - Apply this maximum uniformly to all lines
    - Benefits: simpler hit-testing, cleaner visual alignment
- Implement first-staff annotation shift-down logic (3 staff line bottom margin, collapses if no annotations)

### Phase 5: Lyrics, Bangla & Translation (EXPANDED WIRING)

- Complete `drawBanglaLyrics()` implementation:
    - Full rendering logic (currently stub at Renderer.java:1482-1492)
    - Calculate width and position (left-aligned in union box)
    - Use composition.getBanglaFont() and banglaFontMetrics
- Wire Bangla lyrics into `drawScore()` method:
    - Add call to drawBanglaLyrics() after LYRICS step
    - Test with actual Bangla content to verify font rendering
- Split `drawUnderLyrics()` to separate lyrics/translation rendering
- Add `measureLyrics()` to Renderer
- Add `measureBanglaLyrics()` to Renderer
- Add `measureTranslation()` to Renderer
- Implement union bounding box calculation for lyrics/Bangla/translation alignment
- Add `Composition.unofficialTranslation` boolean flag (XML-only, no UI editing)
- Implement translation header text logic:
    - If flag is true: "Unofficial translation:"
    - If flag is false/absent: "Sri Chinmoy's translation:"

### Phase 6: Footnotes & XML Versioning (EXPANDED)

- Complete `drawFootnotes()` implementation:
    - Currently stub (Renderer.java:1494-1504), IS called from drawScore()
    - Implement bottom-anchored positioning (measure total content height first)
    - Calculate position: pageHeight - bottomMargin - footnotesHeight
    - Center horizontally with max 2/3 page width
- Add `measureFootnotes()` to Renderer with bottom-anchored calculation
- Implement XML version 1.3 in CompositionIO.java:
    - Update version constant: CURRENT_VERSION = "1.3"
    - Add read13() method (inherits read12(), adds unofficialTranslation parsing and title defaulting)
    - Update version dispatch in read() to handle "1.3"
    - Update write() to output version="1.3" and unofficialTranslation element
- Backward compatibility:
    - Version 1.2 files load normally via read12(), then read13() adds defaults
    - Missing `<unofficialTranslation>` defaults to false
    - Empty title defaults to "Untitled"
- Test backward compatibility with 1.2 files

### Phase 7: Integration (EXPANDED)

- Convert `drawScore()` to two-pass (measure → draw)
- Migrate Score.java methods from renderMap to LayoutManager:
    - viewChanged(): Use getMiddleLineY() and getRowHeight()
    - getNoteYPos(): Delegate to layoutManager.getNoteYPos()
    - getUnderLyricsYPos(): Use layoutManager.getLyricsBounds().y
    - getStartY(): Use layoutManager.getContentStartY() (remove empty title logic)
    - getSheetHeight(): Use layoutManager.getTotalHeight() (no throwaway rendering)
- Remove old `renderMap` and `RenderStep` from Score
- Wire up LayoutChangeMessage posting:
    - Composition.java: Post on setTitle(), setAttribution(), setUnderLyrics(), setBanglaLyrics(),
      setTranslatedLyrics(), setFootnotes(), setTitleFont(), setAttributionFont(), setLyricsFont()
    - Line.java: Post on addNote(), removeNote(), setTempo(), etc.
    - VerticalAdjustment.java: Post on adjustAttribution()
    - Score.java: Subscribe with @Handler and implement invalidation + debounced repaint
- Implement section-level caching with cascade invalidation
- Add 300ms debouncing for repaint (reset timer on each change)
- Update export paths:
    - ExportPDFAction: Ensure scale-independent LayoutManager integration
    - Score.exportToSVG(): Use LayoutManager bounds
    - Verify "export without title" option still works

### Phase 8: Testing & Polish (EXPANDED)

- Create synthetic test compositions for edge cases (see Test Cases section)
- Unit tests for LayoutManager calculations
- Unit tests for Line.getRequiredHeight()
- Test hit-testing and selection with new coordinate system:
    - calculateSelection, updateSelection, mouseMoved depend on getNoteYPos() accuracy
    - Verify selection works correctly after LayoutManager integration
- Test export with/without title (verify temporary mutation pattern works)
- Test version 1.2 → 1.3 migration (verify backward compatibility)
- Visual regression tests comparing old vs new layout
- Manual QA with real songs
- Add debug visualization (when DEBUG env var set)

## Future Work

- Multi-page handling and pagination
- Configurable translation header text
- Animation options for layout transitions
- Per-page uniform spacing (instead of per-composition)

## Test Cases to Create

1. **Tall first staff**: High notes with annotations extending above first staff
2. **Variable line heights**: Mix of simple and complex lines to test uniform spacing
3. **Complex line elements**: Line with tempo change, beat change, first/second endings, trills, dynamics, tuplets
4. **Long lyrics**: Lyrics block significantly wider than translation
5. **All sections present**: Title, attribution, score, lyrics, Bangla, translation, footnotes
6. **Empty optional sections**: No translation, no footnotes, no Bangla
7. **Font size changes**: Verify recalculation when lyrics font changes
8. **Single line composition**: Edge case with just one staff line
9. **Empty title on load**: Verify defaults to "Untitled"
10. **Footnotes positioning**: Verify bottom-anchored behavior
11. **Export at various scales**: Verify scale-independent measurements work correctly
12. **Version 1.2 file compatibility**: Verify old files load with correct defaults (unofficialTranslation=false, empty
    title→"Untitled")
13. **Export without title**: Verify checkbox/flag for omitting title works correctly (temporary mutation pattern)
14. **Hit-testing accuracy**: Verify note selection, mouse hover, and clicking work correctly with new coordinate system
15. **Bangla lyrics rendering**: Test with actual Bangla content to verify font rendering and layout
16. **Profile migration**: Verify user profiles with old "rightInfo" keys load correctly after renaming
17. **View serialization compatibility**: Verify saved views load correctly after ViewIO changes
