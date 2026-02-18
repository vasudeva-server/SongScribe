# Chrome DevTools-Style Inspector Implementation Plan

## Overview

Redesign the debug menu and bounds visualization to work like Chrome DevTools Inspector:
- Single "Enable Inspector" menu item with Control+Shift+I shortcut
- Hover over components to see their content, padding, and margin visualized in different colors
- Display tooltip with size, padding, and margin information in CSS format

## Lyrics Architecture

SongScribe has two separate lyrics systems:

1. **Staff Lyrics**: Syllables displayed under each staff line, aligned with notes
   - Structure: Each `LineLayout` contains a `LyricsLayout` (accessed via `lineLayout.getLyrics()`)
   - `LyricsLayout` contains a list of `SyllableLayout` objects
   - Each `SyllableLayout` has its own `ElementBounds`
   - These are the syllables that appear directly under notes on the staff

2. **Under Lyrics**: Full lyrics text block displayed below the entire score
   - Structure: `LayoutResult.getLyrics()` returns a `SectionLayout`
   - This is a block of text below the staff lines
   - Comment in code: "under-lyrics (lyrics block below score)"

## Section Type Mapping

The LayoutResult class provides access to these sections via getters:
- `getTitle()` → ElementType.TITLE (label: "Title")
- `getAttribution()` → ElementType.ATTRIBUTION (label: "Attribution")
- `getScore()` → ElementType.SECTION (label: "Score") - the staff section containing lines
- `getLyrics()` → ElementType.UNDER_LYRICS (label: "Under Lyrics") - full lyrics block below score
- `getBanglaLyrics()` → ElementType.BANGLA_LYRICS (label: "Bangla Lyrics")
- `getTranslation()` → ElementType.TRANSLATION (label: "Translation")
- `getFootnotes()` → ElementType.FOOTNOTES (label: "Footnotes")

Additionally, within each LineLayout:
- `lineLayout.getLyrics()` → `LyricsLayout` containing syllables
- Each syllable in `LyricsLayout.getSyllables()` → ElementType.STAFF_LYRICS (label: "Staff Lyrics: {syllable text}")

## Implementation Phases

### ✅ Phase 1: Core Data Structures [COMPLETE]

Create the foundational data structures for inspector state and element tracking.

#### Files to Create/Modify

**DebugState.java** - `/Users/aparajita/Developer/projects/SongScribe/src/main/java/songscribe/ui/menu/DebugState.java`

Changes:
- Remove `showLayoutBoxes`, `showBoundingBoxes`, `showMargins` fields and their getters/setters
- Add `private static boolean inspectorEnabled = false`
- Add `private static HoveredElement hoveredElement = null`
- Add `private static Point mousePosition = null`
- Add getters/setters:
  - `isInspectorEnabled()` / `setInspectorEnabled(boolean)`
  - `getHoveredElement()` / `setHoveredElement(HoveredElement)`
  - `getMousePosition()` / `setMousePosition(Point)`

**New inner class** `HoveredElement` (add to DebugState.java):
```java
public static final class HoveredElement {
    private final ElementBounds bounds;
    private final String label;
    private final ElementType type;

    public HoveredElement(ElementBounds bounds, String label, ElementType type) {
        this.bounds = bounds;
        this.label = label;
        this.type = type;
    }

    public ElementBounds getBounds() { return bounds; }
    public String getLabel() { return label; }
    public ElementType getType() { return type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoveredElement)) return false;
        var that = (HoveredElement) o;
        return type == that.type &&
               label.equals(that.label) &&
               boundsEqual(bounds, that.bounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, label,
            bounds.getMarginBounds().getX(),
            bounds.getMarginBounds().getY());
    }

    private boolean boundsEqual(ElementBounds b1, ElementBounds b2) {
        var m1 = b1.getMarginBounds();
        var m2 = b2.getMarginBounds();
        return m1.getX() == m2.getX() &&
               m1.getY() == m2.getY() &&
               m1.getWidth() == m2.getWidth() &&
               m1.getHeight() == m2.getHeight();
    }
}
```

**New enum** `ElementType` (add to DebugState.java):
```java
public enum ElementType {
    NOTE,
    ATTACHMENT,
    RANGE,
    LINE,
    SECTION,
    STAFF_LYRICS,
    UNDER_LYRICS,
    TRANSLATION,
    BANGLA_LYRICS,
    FOOTNOTES,
    TITLE,
    ATTRIBUTION
}
```

#### Testing Phase 1
- Compile the project: `./scripts/compile.sh`
- Verify no compilation errors

---

### ✅ Phase 2: Menu Simplification [COMPLETE]

Simplify the debug menu to a single "Enable Inspector" item with keyboard shortcut.

#### Files to Modify

**DebugMenu.java** - `/Users/aparajita/Developer/projects/SongScribe/src/main/java/songscribe/ui/menu/DebugMenu.java`

Changes:
- Replace 3 checkbox menu items with single `JCheckBoxMenuItem("Enable Inspector")`
- Set keyboard shortcut:
  ```java
  var inspectorItem = new JCheckBoxMenuItem("Enable Inspector");
  inspectorItem.setAccelerator(
      KeyStroke.getKeyStroke(
          KeyEvent.VK_I,
          InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
      )
  );
  ```
- ActionListener:
  ```java
  inspectorItem.addActionListener(e -> {
      DebugState.setInspectorEnabled(inspectorItem.isSelected());
      MainFrame.getInstance().getScore().repaint();
  });
  ```

#### Testing Phase 2
- Set DEBUG environment variable: `export DEBUG=1`
- Launch application: `./scripts/run-dev.sh`
- Verify Debug menu shows only "Enable Inspector" item
- Verify menu item shows "Ctrl+Shift+I" shortcut
- Click menu item, verify checkmark toggles
- Press Control+Shift+I, verify it toggles the menu item

---

### Phase 3: Mouse Interaction

Add mouse tracking to detect when elements are hovered.

#### Files to Modify

**Score.java** - `/Users/aparajita/Developer/projects/SongScribe/src/main/java/songscribe/ui/component/Score.java`

**Modify `mouseMoved()` method** (line 2772):
Add at the beginning of the method, before the edit mode check:
```java
// Inspector hover tracking - only repaint if hovered element changes
if (DebugState.isInspectorEnabled()) {
    var oldHoveredElement = DebugState.getHoveredElement();
    DebugState.setMousePosition(new Point(e.getX(), e.getY()));
    updateInspectorHover(e.getX(), e.getY());
    var newHoveredElement = DebugState.getHoveredElement();

    // Only repaint if the hovered element actually changed
    if (!Objects.equals(oldHoveredElement, newHoveredElement)) {
        repaint();
    }
}
```

**Add new method** `updateInspectorHover(int x, int y)`:
```java
private void updateInspectorHover(int x, int y) {
    if (!layoutManager.isValid()) {
        DebugState.setHoveredElement(null);
        return;
    }

    var layoutResult = layoutManager.getLayoutResult();

    // Check in order of priority (most specific first)

    // 1. Check notes
    for (var line : layoutResult.getLines()) {
        for (var note : line.getNotes()) {
            if (note.getBounds().containsForHitTest(x, y)) {
                DebugState.setHoveredElement(new DebugState.HoveredElement(
                    note.getBounds(),
                    "Note",
                    DebugState.ElementType.NOTE
                ));
                return;
            }
        }
    }

    // 2. Check staff lyrics syllables
    for (var line : layoutResult.getLines()) {
        var lyrics = line.getLyrics();
        for (var syllable : lyrics.getSyllables()) {
            if (syllable.containsPoint(x, y)) {
                DebugState.setHoveredElement(new DebugState.HoveredElement(
                    syllable.getBounds(),
                    "Staff Lyrics: " + syllable.getText(),
                    DebugState.ElementType.STAFF_LYRICS
                ));
                return;
            }
        }
    }

    // 3. Check attachments
    for (var line : layoutResult.getLines()) {
        for (var attachment : line.getAttachments()) {
            if (attachment.getBounds().containsForHitTest(x, y)) {
                DebugState.setHoveredElement(new DebugState.HoveredElement(
                    attachment.getBounds(),
                    "Attachment: " + attachment.getType(),
                    DebugState.ElementType.ATTACHMENT
                ));
                return;
            }
        }
    }

    // 4. Check ranges
    for (var line : layoutResult.getLines()) {
        for (var range : line.getRanges()) {
            if (range.getBounds().containsForHitTest(x, y)) {
                DebugState.setHoveredElement(new DebugState.HoveredElement(
                    range.getBounds(),
                    "Range",
                    DebugState.ElementType.RANGE
                ));
                return;
            }
        }
    }

    // 5. Check line bounds
    for (var line : layoutResult.getLines()) {
        if (line.getLineBounds().containsForHitTest(x, y)) {
            DebugState.setHoveredElement(new DebugState.HoveredElement(
                line.getLineBounds(),
                "Line",
                DebugState.ElementType.LINE
            ));
            return;
        }
    }

    // 6. Check sections
    var sections = List.of(
        new Object[] { layoutResult.getTitle(), "Title", DebugState.ElementType.TITLE },
        new Object[] { layoutResult.getAttribution(), "Attribution", DebugState.ElementType.ATTRIBUTION },
        new Object[] { layoutResult.getLyrics(), "Under Lyrics", DebugState.ElementType.UNDER_LYRICS },
        new Object[] { layoutResult.getBanglaLyrics(), "Bangla Lyrics", DebugState.ElementType.BANGLA_LYRICS },
        new Object[] { layoutResult.getTranslation(), "Translation", DebugState.ElementType.TRANSLATION },
        new Object[] { layoutResult.getFootnotes(), "Footnotes", DebugState.ElementType.FOOTNOTES },
        new Object[] { layoutResult.getScore(), "Score", DebugState.ElementType.SECTION }
    );

    for (var section : sections) {
        var sectionLayout = (SectionLayout) section[0];
        var bounds = sectionLayout.getBounds();
        if (bounds.containsForHitTest(x, y) && sectionLayout.hasContent()) {
            DebugState.setHoveredElement(new DebugState.HoveredElement(
                bounds,
                (String) section[1],
                (DebugState.ElementType) section[2]
            ));
            return;
        }
    }

    // No element found
    DebugState.setHoveredElement(null);
}
```

**Modify `mouseExited()` method** (line 2925):
Add at the beginning:
```java
if (DebugState.isInspectorEnabled()) {
    DebugState.setHoveredElement(null);
    DebugState.setMousePosition(null);
    repaint();
}
```

#### Testing Phase 3
- Enable inspector (Control+Shift+I)
- Move mouse over score area
- Open console and add debug logging to `updateInspectorHover()` to verify:
  - Notes are detected
  - Syllables are detected
  - Sections are detected
- Move mouse off score, verify hovered element is cleared

---

### Phase 4: Box Model Visualization

Implement Chrome DevTools-style box model rendering.

#### Files to Modify

**DebugRenderer.java** - `/Users/aparajita/Developer/projects/SongScribe/src/main/java/songscribe/ui/renderer/DebugRenderer.java`

**Add Chrome DevTools color constants** (replace existing color scheme):
```java
// Chrome DevTools-style inspector colors
private static final Color CHROME_CONTENT_FILL = new Color(111, 168, 220, 40);     // Blue fill
private static final Color CHROME_CONTENT_STROKE = new Color(41, 98, 155, 180);    // Dark blue stroke
private static final Color CHROME_PADDING_FILL = new Color(147, 196, 125, 40);     // Green fill
private static final Color CHROME_PADDING_STROKE = new Color(77, 156, 45, 180);    // Dark green stroke
private static final Color CHROME_MARGIN_FILL = new Color(246, 178, 107, 40);      // Orange fill
private static final Color CHROME_MARGIN_STROKE = new Color(237, 125, 49, 180);    // Dark orange stroke
```

**Modify `drawDebugVisualization()` method**:
```java
public void drawDebugVisualization(
    @NotNull Graphics2D g2,
    @NotNull LayoutResult layoutResult,
    @NotNull RenderContext context
) {
    if (!DebugState.isInspectorEnabled()) {
        return;
    }

    var hoveredElement = DebugState.getHoveredElement();
    if (hoveredElement == null) {
        return;
    }

    // Save graphics state
    var originalStroke = g2.getStroke();
    var originalColor = g2.getColor();

    drawElementBoxModel(g2, hoveredElement.getBounds());

    // Restore graphics state
    g2.setStroke(originalStroke);
    g2.setColor(originalColor);
}
```

**Add new method** `drawElementBoxModel(Graphics2D g2, ElementBounds bounds)`:
```java
private void drawElementBoxModel(@NotNull Graphics2D g2, @NotNull ElementBounds bounds) {
    // Draw margin bounds (outermost)
    var marginBounds = bounds.getMarginBounds();
    g2.setColor(CHROME_MARGIN_FILL);
    g2.fill(marginBounds);
    g2.setColor(CHROME_MARGIN_STROKE);
    g2.setStroke(new BasicStroke(1.0f));
    g2.draw(marginBounds);

    // Draw padding bounds
    var paddingBounds = bounds.getPaddingBounds();
    g2.setColor(CHROME_PADDING_FILL);
    g2.fill(paddingBounds);
    g2.setColor(CHROME_PADDING_STROKE);
    g2.setStroke(new BasicStroke(1.0f));
    g2.draw(paddingBounds);

    // Draw content bounds (innermost)
    var contentBounds = bounds.getContentBounds();
    g2.setColor(CHROME_CONTENT_FILL);
    g2.fill(contentBounds);
    g2.setColor(CHROME_CONTENT_STROKE);
    g2.setStroke(new BasicStroke(1.5f));
    g2.draw(contentBounds);
}
```

#### Testing Phase 4
- Enable inspector
- Hover over elements (notes, syllables, sections)
- Verify box model visualization appears:
  - Content: Blue fill + dark blue stroke (thickest stroke at 1.5px)
  - Padding: Green fill + dark green stroke
  - Margin: Orange fill + dark orange stroke
- Verify colors match Chrome DevTools
- Verify visualization updates as mouse moves

---

### Phase 5: Tooltip Display

Add tooltip with size, padding, and margin information.

#### Files to Modify

**DebugRenderer.java** - Continue modifications

**Modify `drawDebugVisualization()` method** (add after drawElementBoxModel call):
```java
var mousePos = DebugState.getMousePosition();
if (mousePos != null) {
    drawInspectorTooltip(g2, hoveredElement, context);
}
```

**Add new method** `drawInspectorTooltip(...)`:
```java
private void drawInspectorTooltip(
    @NotNull Graphics2D g2,
    @NotNull DebugState.HoveredElement element,
    @NotNull RenderContext context
) {
    var bounds = element.getBounds();
    var contentBounds = bounds.getContentBounds();
    var paddingBounds = bounds.getPaddingBounds();
    var marginBounds = bounds.getMarginBounds();

    // Calculate dimensions
    var width = (int) Math.round(contentBounds.getWidth());
    var height = (int) Math.round(contentBounds.getHeight());

    // Calculate padding spacing
    var paddingSpacing = calculateSpacing(paddingBounds, contentBounds);
    var paddingCss = formatCssSpacing(
        paddingSpacing[0], paddingSpacing[1],
        paddingSpacing[2], paddingSpacing[3]
    );

    // Calculate margin spacing
    var marginSpacing = calculateSpacing(marginBounds, paddingBounds);
    var marginCss = formatCssSpacing(
        marginSpacing[0], marginSpacing[1],
        marginSpacing[2], marginSpacing[3]
    );

    // Create HTML tooltip
    var html = "<html>" +
        "<div style=\"font-size: 11px; padding: 4px;\">" +
        "<strong>" + element.getLabel() + "</strong><br>" +
        "Size: " + width + " × " + height + "<br>" +
        "Padding: " + paddingCss + "<br>" +
        "Margin: " + marginCss +
        "</div>" +
        "</html>";

    // Create and size label
    var label = new JLabel(html);
    var tooltipSize = label.getPreferredSize();
    label.setSize(tooltipSize);

    // Calculate position (bottom-centered, 4px gap)
    var elementCenterX = marginBounds.getCenterX();
    var elementBottom = marginBounds.getMaxY();

    var x = (int) (elementCenterX - (tooltipSize.width / 2.0));
    var y = (int) (elementBottom + 4);

    // Adjust horizontal position if clipped
    var componentWidth = ((JComponent) context).getWidth();
    if (x < 0) {
        x = 0;
    } else if (x + tooltipSize.width > componentWidth) {
        x = componentWidth - tooltipSize.width;
    }

    // Adjust vertical position if clipped
    var componentHeight = ((JComponent) context).getHeight();
    if (y + tooltipSize.height > componentHeight) {
        // Place above element
        y = (int) (marginBounds.getMinY() - tooltipSize.height - 4);
    }

    // Render tooltip
    var originalTransform = g2.getTransform();
    g2.translate(x, y);
    label.paint(g2);
    g2.setTransform(originalTransform);
}
```

**Add helper method** `calculateSpacing(...)`:
```java
private double[] calculateSpacing(
    @NotNull Rectangle2D outer,
    @NotNull Rectangle2D inner
) {
    return new double[] {
        inner.getY() - outer.getY(),                    // top
        outer.getMaxX() - inner.getMaxX(),              // right
        outer.getMaxY() - inner.getMaxY(),              // bottom
        inner.getX() - outer.getX()                     // left
    };
}
```

**Add helper method** `formatCssSpacing(...)`:
```java
private String formatCssSpacing(
    double top,
    double right,
    double bottom,
    double left
) {
    int t = (int) Math.round(top);
    int r = (int) Math.round(right);
    int b = (int) Math.round(bottom);
    int l = (int) Math.round(left);

    // All zero
    if (t == 0 && r == 0 && b == 0 && l == 0) {
        return "0";
    }

    // All same
    if (t == r && r == b && b == l) {
        return t + "px";
    }

    // Top/bottom same, left/right same
    if (t == b && l == r) {
        return t + "px " + r + "px";
    }

    // Top different, left/right same, bottom different
    if (l == r) {
        return t + "px " + r + "px " + b + "px";
    }

    // All different
    return t + "px " + r + "px " + b + "px " + l + "px";
}
```

#### Testing Phase 5
- Enable inspector
- Hover over various elements
- Verify tooltip appears with:
  - Element label (bold)
  - Size: width × height
  - Padding: CSS format (coalesced)
  - Margin: CSS format (coalesced)
- Verify tooltip positioning:
  - Bottom-centered by default
  - Positioned above if bottom doesn't fit
  - Adjusted left/right if centering would clip
- Verify tooltip inherits theme font

---

### Phase 6: Cleanup

Remove obsolete code and update interfaces.

#### Files to Modify

**RenderContext.java** - `/Users/aparajita/Developer/projects/SongScribe/src/main/java/songscribe/ui/renderer/RenderContext.java`

Remove methods:
- `isShowLayoutBoxes()`
- `isShowBoundingBoxes()`
- `isShowMargins()`

**Score.java** - Remove implementations (lines 832-843):
Remove the three method implementations that provided these values.

#### Testing Phase 6
- Compile project: `./scripts/compile.sh`
- Verify no compilation errors
- Run application: `./scripts/run-dev.sh`
- Verify inspector works correctly
- Verify no console errors

---

## Complete End-to-End Testing

After all phases are implemented:

1. **Enable Inspector**:
   - Set DEBUG environment variable: `export DEBUG=1`
   - Launch application: `./scripts/run-dev.sh`
   - Open Debug menu, verify it shows only "Enable Inspector" with Control+Shift+I shortcut
   - Click menu item or press Control+Shift+I
   - Verify checkmark appears

2. **Test Hover Visualization**:
   - With inspector enabled, move mouse over score area
   - Verify box model visualization appears for elements under cursor:
     - Content bounds: Blue fill + dark blue stroke (thickest at 1.5px)
     - Padding bounds: Green fill + dark green stroke
     - Margin bounds: Orange fill + dark orange stroke
   - Verify visualization follows mouse in real-time
   - Move mouse off score, verify visualization disappears

3. **Test Tooltip Display**:
   - Hover over various elements (notes, staff lyrics syllables, attachments, lines, sections)
   - Verify tooltip appears bottom-centered on element with 4px gap, showing:
     - Element label (e.g., "Note", "Attachment: Tempo", "Title", "Staff Lyrics: la", "Under Lyrics")
     - Size: width × height
     - Padding: CSS format
     - Margin: CSS format
   - Verify tooltip positioning:
     - Default: centered horizontally, 4px below element
     - If doesn't fit at bottom, positioned 4px above element
     - If horizontal centering would clip left/right edges, adjusted to stay within bounds
   - Verify tooltip inherits theme font (no custom font-family)

4. **Test Keyboard Shortcut**:
   - Press Control+Shift+I to toggle inspector
   - Verify menu checkmark updates
   - Verify visualization disappears when disabled

5. **Test Edge Cases**:
   - Empty score (no notes)
   - Mouse over empty space (no tooltip should appear)
   - Overlapping elements (verify most specific element is selected)
   - Fast mouse movement (should not flicker or lag)

6. **Test All Element Types**:
   - Notes
   - Staff lyrics syllables
   - Attachments (tempo, annotations, trills)
   - Range elements (crescendos, diminuendos)
   - Lines (staff lines)
   - Sections (title, attribution, under lyrics, Bangla lyrics, translation, footnotes, score)

## Success Criteria

- Debug menu shows single "Enable Inspector" item with keyboard shortcut
- Control+Shift+I toggles inspector on/off
- Hovering over elements shows Chrome DevTools-style box model visualization
- Tooltip appears with correct size, padding, and margin information
- Tooltip is positioned bottom-centered on element with 4px gap (or top-centered if bottom doesn't fit)
- Tooltip inherits theme font family (no custom font-family in HTML)
- CSS format is properly coalesced (e.g., "10px" not "10px 10px 10px 10px")
- Efficient rendering: repaint only occurs when hovered element changes, not on every mouse move
- All section types are properly detected: Title, Attribution, Under Lyrics, Translation, Bangla Lyrics, Footnotes, Score
- Staff lyrics syllables are properly detected and display their text in the tooltip
- No performance issues or flickering during mouse movement
- Visualization disappears when mouse exits score area or inspector is disabled

## Implementation Order

Implement phases in order 1-6. Each phase builds on the previous one and can be tested independently. This ensures that if any issues arise, they can be isolated to a specific phase.
