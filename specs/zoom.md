# Score Zoom Feature Spec

## Overview

Add discrete zoom support to the score view, allowing users to zoom in and out
to see more detail or a broader view of the composition. Zoom is a screen-only
display feature and does not affect exports, layout reflow, or file state.

---

## Zoom Levels

Fifteen discrete steps from 50% to 400% in 25% increments:

```
50%, 75%, 100%, 125%, 150%, 175%, 200%, 225%, 250%, 275%, 300%, 325%, 350%, 375%, 400%
```

**Default**: 100% always. Zoom is not persisted — every composition opens at 100%.

---

## Implementation Approach

### Prerequisite: Layout2 Migration

The score uses a Swing component tree for layout
(`Score` → `MainPanel` → `StaffPanel` → `LinePanel` → `LineComponent`).
For zoom to work correctly, **all** component sizing and rendering must derive
from a single mutable pixels-per-staff-space value. Currently there are three
independent constants that all equal 8:

| Constant | Location | Used by |
|---|---|---|
| `PIXELS_PER_STAFF_SPACE` | `StaffSpaces.java` | All renderers |
| `STAFF_SPACE` | `LayoutStylesheet.java` | Legacy layout system |
| `STAFF_LINE_SPACING` | `LayoutConstants.java` | Layout2 system |

Changing only `PIXELS_PER_STAFF_SPACE` would scale glyph rendering but leave
component sizes (line heights, staff panel dimensions) unchanged — notes would
overflow their containers at any zoom level other than 100%.

A `Graphics2D` scale transform applied in a wrapper component is also not viable:
AWT dispatches mouse events using real component bounds, so hit-testing for note
placement, drag, and selection would be routed to the wrong elements.

The zoom feature therefore **depends on completing the layout2 migration** so
that a single class is the source of truth for pixels per staff space. Once that
is done, zoom becomes straightforward:

1. Make the single pixels-per-staff-space value mutable
2. On a zoom step, update it to `baseValue * zoomFactor`
3. Invalidate and revalidate the component tree (all components resize)
4. Adjust the scroll position to maintain the zoom anchor (see below)
5. Repaint

Because all components compute their preferred sizes from the same source, they
resize correctly and AWT mouse-event dispatch continues to work without any
coordinate transforms.

### Magic Number Cleanup (can be done now)

The audit found approximately 40 locations in renderer code using the literal
value `8.0` or `8` as a divisor or multiplier instead of calling
`StaffSpaces.fromPixels()` / `StaffSpaces.toPixels()`. Examples:

```java
// Wrong — magic number:
g2.translate(0, -NOTE_FONT_SIZE / 8);
float y = ((i - yPos) * NOTE_FONT_SIZE) / 8;

// Correct:
g2.translate(0, -StaffSpaces.fromPixels(NOTE_FONT_SIZE));
float y = (float) ((i - yPos) * StaffSpaces.fromPixels(NOTE_FONT_SIZE));
```

These must be fixed before zoom is wired up, otherwise glyphs will render at
the wrong positions at non-100% zoom levels. This cleanup is independent of the
layout migration and can be done immediately.

---

## Zoom Anchor

| Trigger | Anchor point |
|---|---|
| Cmd+scroll wheel | Music content under the cursor stays fixed on screen |
| Keyboard shortcut | Center of the visible viewport stays fixed |

**Scroll adjustment formula** (both cases):
```
newScrollX = anchorContentX * newZoomFactor - anchorScreenX
newScrollY = anchorContentY * newZoomFactor - anchorScreenY
```

Where `anchorContentX/Y` is the content-space position of the anchor point
before zoom, and `anchorScreenX/Y` is its screen position (cursor for
scroll-wheel, viewport center for keyboard).

---

## Layout Behavior

Zoom is a **visual scale only**. The number of bars per line, line breaks, and
all other layout decisions remain unchanged at every zoom level. Zooming in
does not reflow the score to fit fewer bars per line.

All elements scale uniformly: notes, beams, ties, text, lyrics, title,
footnotes, and all attachments.

---

## Input Methods

| Action | Binding |
|---|---|
| Zoom in | `Cmd+=` and `Cmd+Shift+=` |
| Zoom out | `Cmd+-` |
| Reset to 100% | `Cmd+0` |
| Zoom in/out | `Cmd+scroll wheel` (each scroll tick = one step) |

All shortcuts are registered globally in the main window (active regardless of
which component has focus).

Zoom past the min (50%) or max (400%) limit clamps silently.

---

## Status Bar

A new full-width status bar is added to the bottom edge of the main window,
below the scroll pane. It is designed to accommodate additional controls in the
future beyond zoom.

### Zoom Controls (rightmost section of the status bar)

```
[ 🔍- ]  [ 🔍+ ]  [ 207% ▼ ]
```

- **Zoom-out button**: magnifying glass with minus icon. Disabled at 50%.
- **Zoom-in button**: magnifying glass with plus icon. Disabled at 400%.
- **Percentage label + dropdown arrow**: displays the current zoom level (e.g.
  `207%`). Clicking the dropdown arrow opens a popup menu listing all 15 zoom
  levels. Selecting a level sets zoom to that value. The current level is shown
  with a check mark in the menu.

The style should match the reference image (Xcode-style compact status bar with
icon buttons and a label+arrow combo).

---

## Export Behavior

Zoom is a screen-only feature. PDF, SVG, and image exports always render at
100% (8px/staff-space) regardless of the current zoom level. The export
resolution/scale controls are independent.

---

## Playback

Auto-scroll during playback should account for the zoom factor so that the
currently playing note remains visible. This is **best-effort** — it is not a
hard requirement for the initial release.

---

## Phased Implementation

### Phase 1 — Magic Number Cleanup (can start now)

Replace all `/ 8`, `/ 8.0`, `* 8`, `* 8.0` in renderer and layout code with
`StaffSpaces.fromPixels()` / `StaffSpaces.toPixels()` calls. This is a code
style fix independent of zoom.

### Phase 2 — Complete Layout2 Migration

Consolidate `LayoutStylesheet.STAFF_SPACE`, `LayoutConstants.STAFF_LINE_SPACING`,
and `StaffSpaces.PIXELS_PER_STAFF_SPACE` into a single source of truth for
pixels per staff space. All component sizing and rendering must flow through
this single value. Also parameterize hardcoded layout element sizes in
`VerticalStackingCalculator` (fermata size, lyric height, beat-change width,
annotation width).

### Phase 3 — Zoom Core

1. **Make pixels-per-staff-space mutable**: Provide a setter (or equivalent).
   EDT-only mutation is sufficient for thread safety.
2. **ZoomController**: Holds the current zoom index, exposes `zoomIn()`,
   `zoomOut()`, `resetZoom()`, `setZoomLevel(int percent)`, and broadcasts a
   `ZoomChangedMessage` via the message bus.
3. **ZoomChangedMessage handler** in `Score`: Updates pixels-per-staff-space,
   invalidates the component tree, adjusts scroll position (per anchor rules),
   and repaints.

### Phase 4 — Zoom UI

1. **StatusBar component**: A new `StatusBar` JPanel at the bottom of the main
   window. Contains `ZoomStatusBarPanel` (zoom-out button, zoom-in button,
   percentage+dropdown).
2. **Keyboard actions**: Register `ZoomInAction`, `ZoomOutAction`,
   `ResetZoomAction` in the `Actions` registry. Bind globally via the main
   window's input map.
3. **Scroll wheel handler**: A `MouseWheelListener` on the score scroll pane
   that intercepts `Cmd+scroll` events, delegates to `ZoomController`, and
   computes the cursor-anchored scroll adjustment.

---

## Open Questions / Deferred

- **Playback cursor scroll**: Auto-scroll during playback at non-100% zoom is
  best-effort and not a hard requirement for the initial release.
