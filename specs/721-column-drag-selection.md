# Column-Based Drag Selection

Replace the free-form rubber-band rectangle with a band that is pinned to the staff vertically and jumps by element column horizontally, so an element's vertical position can never affect whether a drag selects it.

**Issue:** vasudeva-server/SongScribe#721

* * *
## Goals

1. **Vertical position stops mattering.** A note four ledger lines above the staff, a rest, a barline and a stem are all swept identically. Selection is decided by horizontal overlap alone.

2. **The band is pinned to the staff.** Its height is always `Staff.STAFF_HEIGHT_SS` (4.0 ss), with the stroke centered on the top and bottom staff lines, regardless of where the mouse is vertically.

3. **The band jumps by column, like text selection.** The leading edge tracks the mouse continuously through the gaps between columns, then snaps to enclose a whole column the moment the mouse enters it, and holds there until the mouse leaves.

4. **The band survives a zoom.** Changing zoom mid-drag re-renders the band at the new scale over the same music, instead of leaving a stale pixel rectangle behind.

* * *
## Current State

### The gesture

`LineComponent.mousePressed` (`LineComponent.java:897`) resolves a hit target once and offers the press to `NoteDragHandler` first (`LineComponent.java:974-980`):

```java
if (noteDragHandler.handlePress(e, pressHit)) {
    return;
}
if (selectionHandler.isSelectionActive(e)) {
    selectionHandler.handlePress(e, pressHit);
}
```

`NoteDragHandler.handlePress` takes the press only when the mode is exactly `Mode.SELECT`, MIDI is not playing, Shift is not down, the hit resolves to an element index on this line, and that element `getType().isNote()`. It then runs a pitch drag.

`LineSelectionHandler.handlePress` (line 201) records `dragStart` in view pixels, zeroes `dragRectangle`, and short-circuits for Shift on an element (lines 220-222):

```java
if (e.isShiftDown() && hitTarget instanceof HitTarget.Element) {
    return;
}
```

That `return` leaves `pressHandled` at its initial `false` (line 203), which serves two unrelated purposes at once: it lets `handleClick`'s Shift branch run the range extension (#748), **and** it lets `handleDrag` paint a band. The second is unwanted — Shift-dragging from a notehead sweeps when the user asked to extend.

Every other case falls into the switch (lines 226-257), and every non-null hit target's handler returns `true` for a live, registered line, setting `pressHandled = true` (line 259). `handleDrag` then bails immediately (lines 304-306), so a band is otherwise possible only after a genuine miss (`null`).

`handleDrag` (lines 303-335) clamps the point to the component's bounds, builds `dragRectangle` with a `MIN_DRAG_EXTENT_PX = 1` (line 62) floor on width and height (needed because `Rectangle2D.intersects` rejects an empty rectangle outright, so a perfectly straight drag would otherwise sweep nothing), recomputes the selection, and repaints. `handleRelease` (lines 370-377) zeroes the rectangle and fires `scoreView.selectionChanged()`.

`dragRectangle` is a `Rectangle` in **view pixels**, written once per drag event and read verbatim by the renderer. Nothing re-derives it when `ViewScale.factor()` changes, so a zoom mid-drag repaints the old pixel rectangle over content that has moved and rescaled.

### The sweep

`calculateLineSelectionFromDrag` (lines 560-614) converts the view-pixel rectangle to line-local staff spaces, then for each element builds

```java
ElementHitGeometry.elementHitRectSs(layoutResult.getElementXSs(element), element, helper, false)
```

and tests `dragRectSs.intersects(helper)` (line 599). The `false` requests the unexpanded rect. That box's height comes from `ElementType.getFullElementHeightSs()`, which **excludes the stem** — this is the root cause of the issue's complaint. Elements swept are tracked as first/last index and handed to `coordinator.selectRange(begin, end)` (line 613), which anchors at `begin` (issue #748). The song's auto-maintained terminal is skipped per element via `song.isAutoMaintainedTerminal` (line 590, issue #713).

### Painting

`LineRenderer.renderDragRectangle` (lines 641-685) is called from `LineComponent` at line 548, **after** the staff-space transform is restored, so the band is a pixel-space overlay. `SELECTION_RECT_STROKE_WIDTH_PX = 2.0f` and `SELECTION_RECT_ARC_PX = 2.0` are 100%-zoom values multiplied by `ViewScale.factor()` (issue #628). The stroke is centered on the path, so the drawn path is inset by half the stroke width on all four sides to keep the border from being clipped at the component's bounds (issue #643). The shape is a `RoundRectangle2D.Double` in `ScoreView.getSelectionColor()`.

The renderer returns early on `dragRectangle.isEmpty()` (line 648) and otherwise floors the drawn extent at `Math.max(0, right - left)` (line 677). It has no floor of its own — it relies on `MIN_DRAG_EXTENT_PX` never letting a zero through.

### Columns

`ElementColumnBuilder.buildColumns` creates exactly one `ElementColumn` per element index, with no type-based skip — barlines, the terminal, and grace notes all get their own column. `ElementColumn.getGraceNotes()` is a stub returning `Collections.emptyList()`, so the "grace notes borrow space from their host" model in the class doc is not implemented; a grace note is an ordinary column with its own `xSs`.

`LayoutResult.getElementColumn` is a `Map<StaffElement, ElementColumn>` lookup — O(1).

`LayoutEngine.placeColumnsHorizontally` assigns `xSs` cumulatively, and the spring strut floor `calculateMinimumColumnSpacingSs` (`HorizontalSpacingCalculator.java:222-231`) guarantees

```
xSs[N+1] + leftExtentSs[N+1]  >=  xSs[N] + rightExtentFacingSs[N] + MIN_COLUMN_GAP_SS
```

with `MIN_COLUMN_GAP_SS = 1.0`.

**That floor is on *facing* ink, not on full spans.** `rightExtentFacingSs` charges a grace note only its Y-band-restricted right extent against the following column — the #560 flag discount — instead of `getRightExtentSs()`. So a grace note's full span can legitimately overlap its host's span, and "at most one column contains any given x" is **not** an invariant. Every other column is charged its full right extent, so overlaps occur only at a grace-host boundary.

Extents:
- `getLeftEdgeXSs()` = `xSs + leftExtentSs` — `leftExtentSs` is 0 with no accidental, negative when one is present.
- `getRightEdgeXSs()` = `xSs + rightExtentSs` — grows with augmentation dots or a fall.
- `getRightExtentExcludingAugmentationSs()` omits dots and fall; used for comfortable spacing, not for hit testing.

`LayoutHitTester.findElementAtXSs(mouseXSs, line)` already answers "which column contains this x", X-only with Y explicitly ignored, over the span `[xSs, getRightEdgeXSs()]` — the head span, which omits a leading accidental. Its one production caller is `PreviewElementManager.trackMouse`. `LayoutResult.findElementAtXSs` delegates to it.

### Hit targets

`HitTarget` is a sealed interface with 14 variants; `HitRegistry.resolve` scans all regions and picks the highest `HitPriority`, breaking ties by smaller bounding-box area. Priority order: `LYRIC` 100, `ARTICULATION`/`ATTACHMENT`/`TRILL` 90, `ACCIDENTAL` 85, `ELEMENT` 80, `SLIDE` 70, `HAIRPIN` 60, `ENDING` 50, `TUPLET` 45, `TIE` 40, `BEAM` 30, `STAFF_LINE` 10. A miss returns `null`.

`HitTarget.Element`'s region is `elementHitRectSs(..., expandToMinimum=true)` — the notehead/rest/barline glyph expanded to an 8px minimum. **There is no hit region for a stem** and none for an augmentation dot. A click on the middle of a stem resolves to `null` (unless it lands in a beamed group's thin beam band). `HitTarget.StaffLine`'s region is the header column only — `0..calculateHeaderRightEdgeSs`, ±`STAFF_HIT_RADIUS_SS` (2.0 ss) of the midline — which is what makes a header click select the line.

### Selection model

`Selection.Range(Line line, int begin, int end, int anchor)` is necessarily **contiguous** on one line, with the anchor inside the range. `SelectionCoordinator.setRange` clamps `end` to `line.effectiveElementCount() - 1` whenever `end > begin`, so a multi-element range can never include the auto-maintained terminal. `Line.effectiveElementCount()` returns 0 for an empty line and for a line holding only the terminal. A header click produces `Selection.Target(HitTarget.StaffLine)` instead.

`selectRange` allocates one `Selection.Range` record and does nothing else — no notification, no repaint, no cache invalidation. `SelectionCoordinator.ensureContentComputed` compares with structural `equals`, so re-assigning an identical range is a cache hit.

### Test fixtures

`ElementColumnTestHelper.columnAt(...)` (`ElementColumnTestHelper.java:57`) hardcodes `rightExtentSs = 0.0`, so every column it builds is a zero-width point.

`LineSelectionHandlerTest.CalculateLineSelectionFromDrag` stubs `mockLayout.getElementXSs(element)` only (`positionElement`, lines 520-522).

`LineRendererTest` already spies a real `Graphics2D` over an off-screen image against a mocked `LineComponent` (`spyGraphics()`, line 110; the `DrawStaffLines` nested class, line 121).

* * *
## Behavior Specification

### 1. Definitions

**Sweepable columns** are every element on the line except the song's auto-maintained terminal — i.e. indices `0 .. line.effectiveElementCount() - 1`. Grace notes are ordinary sweepable columns with no special treatment.

**A column's span** is `[getLeftEdgeXSs(), getRightEdgeXSs()]` — full ink, accidental enclosed on the left, augmentation dots and fall enclosed on the right.

**`columnAt(xSs)`** returns the **lowest-index** sweepable column whose span contains `xSs`, or none. Spans are ordered by index and overlap only at a grace-host boundary (the #560 flag discount, see Current State). In that sliver the grace note is the lower index and wins. The resulting *selection* is identical either way — snapping the lead to the grace's right edge still leaves the host overlapping the band, so the host is selected too; only the painted band differs by a fraction of a glyph.

All band geometry is computed in line-local staff spaces and converted to view pixels only at paint time.

### 2. What starts a band

Unchanged from today except that Shift no longer arms one. In `Mode.SELECT`, or in `Mode.EDIT` with Alt held (`isSelectionActive`, line 396):

| Press lands on | Result |
| --- | --- |
| Notehead, rest glyph, or barline glyph (`HitTarget.Element`), no Shift | The element is eligible for dragging — pitch drag today via `NoteDragHandler`; horizontal dragging for rests and barlines in the future. No band. |
| `HitTarget.Element` with Shift held | Extends or shrinks the selection on the click that follows (#748). **No band** — this is the change. |
| Any other non-null hit target — accidental, articulation, attachment, lyric, tie, beam, tuplet, slide, hairpin, ending, trill, staff line | Selects itself exactly as today, with or without Shift. No band. |
| A genuine miss (`null`) — a stem, the space around a glyph within the column, anywhere the registry has no region | **Starts a band.** |

The header keeps working as it does now: a header click resolves to `HitTarget.StaffLine`, which selects the whole line and suppresses the band. No header special-casing is added to the drag path.

**Splitting `pressHandled`.** Today one flag answers two questions. Introduce `bandArmed` so each answers one:

```java
// handlePress
if (e.isShiftDown() && hitTarget instanceof HitTarget.Element) {
    bandArmed = false;   // extends on the click that follows; never sweeps
    return;
}
...
bandArmed = !pressHandled;

// handleDrag
if (!bandArmed) {
    return;
}
```

`pressHandled` keeps its existing meaning for `handleClick`, so `handleClick`, `pressTarget`, `LineComponent.elementIndexOf` and `ScoreView.extendSelectionTo` are all untouched.

### 3. Band extents

Captured on press: `anchorXSs` only — a `double` in line-local staff spaces, **replacing** the `dragStart` `Point`. No resolved column is cached: `columnAt(anchorXSs)` is recomputed on every drag event, so a mid-drag re-layout cannot leave a stale `ElementColumn` behind, and there is no invalidation path to maintain.

On each drag event, with `leadXSs` = the mouse x clamped as described in §6, and `leadColumn = columnAt(leadXSs)`:

- **Anchor contribution** — the anchor column's full span if the press was inside a column, otherwise the single point `anchorXSs`.
- **Leading contribution** — the lead column's full span if the mouse is inside a column, otherwise the single point `leadXSs`.
- **Band** = the smallest interval containing both contributions.

```
      col 2            col 3                 col 4
   ┌─────────┐      ┌─────────┐          ┌─────────┐
───┤  ♯ ♪ ·  ├──────┤    ♩    ├──────────┤   ♩ ··  ├───  x →
   L2       R2      L3       R3          L4       R4
        gap              gap

anchor at A, inside col 3   →   anchor contribution = [L3, R3]

  ┌────────────────────────────────────────────────────────┐
  │ lead P in a gap        → point  {P}      band = [L3, P] │
  │ lead P inside col 4    → span [L4, R4]   band = [L3,R4] │
  │ lead P left of col 2   → point  {P}      band = [P, R3] │
  │ lead P inside col 2    → span [L2, R2]   band = [L2,R3] │
  └────────────────────────────────────────────────────────┘

  selected = every sweepable column whose [L, R] overlaps the band
```

That single rule produces every behavior required:

- **Press in a gap, drag through it** — both contributions are points, so the band is drawn continuously from the press x to the mouse, exactly as today.
- **Enter a column** — the leading contribution becomes that column's whole span, so the band snaps out to the column's far extent and stays there while the mouse remains inside.
- **Leave a column** — the leading contribution reverts to the mouse point, which is already past the column's edge, so the band resumes tracking continuously without ever shrinking back over the column it just took.
- **Press inside a column** — the anchor contribution is that column's whole span, so the band covers the entire column from the first drag movement. There is no mid-notehead edge.
- **Reversal** — the anchor column is *always* fully enclosed, so its contributing edge flips automatically when the drag crosses back over it. Press in column 3, drag right to column 5, then back left to column 1, and the band correctly encloses columns 1–3.

**No sweepable columns.** When `line.effectiveElementCount() == 0` — an empty line, or one holding only the terminal — there is nothing a band could select, so the press does not arm one and the drag that follows is a complete no-op: no band is painted, and the selection is untouched. This is the state every line of a new song is in. The band computation keeps the same condition as a guard, since a line's content can change under a live drag and this is the only path that would then reach for a column that no longer exists.

**Leading-edge clamp.** The leading contribution is clamped to the stretch of staff a selection may cover, which is the staff itself rather than the music on it:

- **Left** — `calculateHeaderRightEdgeSs(line)` plus one document pixel. A press in the header selects the staff lines, so the band stops just clear of it.
- **Right** — the auto-maintained terminal's column left edge, or `Song.getLineWidthSs()` when the line carries no terminal. No gap is subtracted here: the terminal is right-aligned with the end of the staff, so stopping at its left edge already leaves no reachable staff beyond it.

The band therefore reaches the bare staff on either side of the notes, but the terminal barline is not a column it can touch, and dragging further into the right margin past the clamp changes nothing. The header's gap is measured in document pixels so the reachable range covers the same music at every zoom.

The anchor contribution is **not** clamped, so a press in a leading gap or the header keeps its raw x and the empty-band phase behaves as specified in §5. `columnAt` additionally rejects any index at or past `effectiveElementCount()`, so an unclamped anchor landing in the terminal's own span still contributes only a point.

### 4. What is selected

An element is selected iff it is sweepable and its column span overlaps the band's x interval. Vertical position is not consulted anywhere.

The sweep runs `0 .. line.effectiveElementCount() - 1`. That bound **is** the terminal-exclusion rule of #713, so the per-element `song.isAutoMaintainedTerminal` check goes away rather than being carried alongside it.

Because spans are ordered by index, the overlapping set is contiguous, so the result is `coordinator.selectRange(begin, end)` — anchored at `begin`, preserving #748. When no column overlaps, the selection is cleared (`selectRange(-1, ...)`).

`selectRange` is called on every drag event even when the range is unchanged, which column snapping now makes the common case. Leave it that way: the call allocates one record, fires nothing, and `ensureContentComputed` compares structurally, so an identical reassignment is a cache hit. A dirty-check would add a second source of truth for the current range in exchange for one small allocation per mouse event.

`ElementHitGeometry` is **not** used by the drag path any more. It stays exactly as it is for the click path.

### 5. The empty-band phase

A press in a gap clears the selection immediately, as a non-Shift press does today. While the band is still entirely within dead space it is painted but selects nothing; the selection appears the instant the first column is intersected. A press-and-release in a gap with no movement leaves nothing selected.

### 6. Vertical geometry and the mouse's Y

The mouse's Y is **ignored entirely**. The band survives the mouse leaving the line vertically — dragging up into the tempo band, down through the lyrics, or off the component keeps the band alive and tracking x. The gesture ends only on mouse release.

The band's vertical extent is fixed:

```
topSs    = middleLineYSs - Staff.STAFF_HALF_SS
bottomSs = middleLineYSs + Staff.STAFF_HALF_SS
```

with the stroke **centered** on those edges so it straddles the top and bottom staff lines. `LineComponent.getMiddleLineYSs()` (line 247) is the source for the midline. Note that `LineRenderer.drawStaffLines` currently writes the ±2 bound as a literal loop range rather than referencing `Staff.STAFF_HALF_SS`; the band must use the constant.

**Horizontal x clamp.** Keep clamping x to `[0, lc.getWidth() - 1]`. A `LineComponent` is exactly `song.getLineWidthSs()` wide, so on a line whose content overflows the staff width (`LayoutResult.overflowsStaffWidth()`) the clipped-off columns are unreachable by a band — matching what the user can actually see.

**Y clamping is removed.** The `MIN_DRAG_EXTENT_PX` floor is removed with it: it existed solely because `Rectangle2D.intersects` rejects an empty rectangle, and the new hit basis is a 1-D interval overlap that a degenerate interval handles correctly. The floor moves to paint time (§7), where it belongs — it is a rendering concern, not a hit-testing one.

### 7. Painting

The band remains a pixel-space overlay rendered after the transform is restored, keeping the `RoundRectangle2D`, the zoom-scaled stroke width and arc (#628), and `ScoreView.getSelectionColor()`.

**Nothing pixel-valued is stored, and nothing pixel-valued is published.** The handler keeps `anchorXSs` and `leadXSs` and exposes the band as `getSelectionBand()` — a `SelectionBand(leftSs, rightSs)` record, or `null` when no drag is live. The single conversion to pixels happens at paint time, in the renderer. That is what makes goal 4 work: `applyZoomPercentAndReanchor` re-anchors the viewport so the content point under the cursor stays put, so a zoom leaves the musical x unchanged and the band simply re-renders at the new scale.

The band has no vertical extent of its own, since it is always the staff's. `LineComponent.getStaffTopYSs()` / `getStaffBottomYSs()` are the single definition of that, so the renderer reads them rather than re-deriving `getMiddleLineYSs() ∓ Staff.STAFF_HALF_SS` for itself.

**The vertical half-stroke inset from #643 is removed** — the band's Y is now driven by staff geometry well inside the component, so the border cannot be clipped, and insetting would pull the stroke off the staff lines it is meant to straddle. **The horizontal inset is kept**, since the band's left and right edges can still approach the component's bounds.

**The `isEmpty()` early return is dropped** — a `null` band is now the one signal for "no drag in progress", and with `MIN_DRAG_EXTENT_PX` gone a legitimate zero-width band (the lead x returning exactly to the anchor x) would otherwise blink out. The drawn width is floored at a named 1px constant instead.

### 8. Playback

Notes do **not** sound as the band sweeps them. Press-to-play on a click is unchanged.

### 9. Selection notification

Unchanged. The selection continues to update live on every drag event, and `scoreView.selectionChanged()` continues to fire only from `handleRelease`.

* * *
## Implementation

### Phase 1 — Column span lookup in `layout/`

1. Add `ColumnSpan` enum to `songscribe.layout` with constants `HEAD` and `FULL_INK`, documented as: `HEAD` is `[getXSs(), getRightEdgeXSs()]` (the glyph body, no leading accidental); `FULL_INK` is `[getLeftEdgeXSs(), getRightEdgeXSs()]` (accidental enclosed).
2. Change `LayoutHitTester.findElementAtXSs(double, Line)` to `findElementAtXSs(double, Line, ColumnSpan)`, selecting the left bound from the enum. Keep the loop's first-match-wins order and document it as the grace-host overlap tie-break from §1. Update the Javadoc's "hit zone" sentence to name both spans.
3. Update `LayoutResult.findElementAtXSs`'s delegating signature to match.
4. Update the one production caller, `PreviewElementManager.trackMouse` (line 684), to pass `ColumnSpan.HEAD`, and the mock stubs in `PreviewElementManagerOverlayTest`, `PreviewElementManagerTrackMouseTest` and `LayoutResultTest`.
5. Add an `ElementColumnTestHelper.columnAt(element, xSs, leftExtentSs, rightExtentSs)` overload so tests can build a column that has width. Without it every fixture column is a zero-width point and the snap-to-far-extent behavior is untestable.
6. New `LayoutHitTesterTest` in `songscribe.layout` (it has package access to the cheap 9-arg `ElementColumn` constructor and to `setXSs`): `HEAD` vs `FULL_INK` on a column with an accidental; x in a gap → `-1`; x past the last column → `-1`; a grace note whose span overlaps its host, x inside the overlap → the grace's index.

Compile, then run the unit suite.

### Phase 2 — Band geometry in `LineSelectionHandler`

1. Replace the `dragStart` `Point` field with `anchorXSs` and `leadXSs` (`double`, line-local staff spaces). Delete the `dragRectangle` field and `MIN_DRAG_EXTENT_PX`.
2. Add the `bandArmed` field and wire it per §2 — `false` on the Shift+`HitTarget.Element` short-circuit, `!pressHandled` after the switch, and `handleDrag` bailing on `!bandArmed` instead of on `pressHandled`.
3. Add a private `columnAt(double xSs, LayoutResult, Line)` returning the `ElementColumn` or `null`: `findElementAtXSs(xSs, line, ColumnSpan.FULL_INK)`, then reject an index `< 0` or `>= line.effectiveElementCount()`, then `getElementColumn`.
4. Add a private band computation implementing §3 — the no-sweepable-columns early return first, then the leading-edge clamp, then the two contributions, then their hull.
5. Rewrite `calculateLineSelectionFromDrag` to take the band's x interval instead of a `Rectangle`: scan `0 .. effectiveElementCount() - 1`, test span overlap, drop both the `ElementHitGeometry` call and the per-element `isAutoMaintainedTerminal` check. Keep the existing null-`LayoutResult` guard — a drag started before the first layout completes must still return quietly. Keep the anchor-contract Javadoc.
6. Rewrite `handleDrag` to clamp x only (no Y clamp), convert to staff spaces via the existing `toSs`, compute the band, and apply the selection. Change `getDragRectangle()` to derive the pixel rectangle from `anchorXSs`/`leadXSs`, the current `ViewScale` and `getMiddleLineYSs()` per §7, and have `handleRelease` clear the two Ss endpoints.

Compile.

### Phase 3 — Painting

1. In `LineRenderer.renderDragRectangle`, take the vertical extent from `getMiddleLineYSs() ∓ Staff.STAFF_HALF_SS` converted to view pixels, with the stroke centered on those edges.
2. Remove the vertical half-stroke inset; keep the horizontal one.
3. Drop the `isEmpty()` early return and floor the drawn width at a new named 1px constant.
4. Add a `RenderDragRectangle` nested class to `LineRendererTest`, following the `DrawStaffLines` pattern: capture the `RoundRectangle2D` passed to the spied `g2.draw` and assert (a) y and height derive from the midline ± `Staff.STAFF_HALF_SS` and are **not** inset by half the stroke, (b) left and right **are** inset, (c) a zero-width band still strokes at 1px, (d) stroke width and arc scale with `ViewScale.factor()`.

Compile, then run the unit suite.

### Phase 4 — Rework the existing handler tests

1. Rework `LineSelectionHandlerTest.CalculateLineSelectionFromDrag`'s fixture: `positionElement` must stub `mockLayout.getElementColumn(element)` with a column built by `ElementColumnTestHelper` at a given x with a real right extent, not just `getElementXSs`.
2. Delete the tests that encode the old rectangle model and cannot survive: `testPerfectlyVerticalDragStillSelectsTheElementUnderIt` and `testPerfectlyHorizontalDragStillSelectsTheElementsItSweeps` (both exist only because of the `MIN_DRAG_EXTENT_PX` floor), and `testTheElementUnderTheDragStartIsNotTheAnchor` (press-point semantics §3 replaces).
3. Restate the three regression guards for the new model: the right-to-left anchor test, the terminal-exclusion test (#713 — now assert that a band dragged past the terminal clamps short of its column and produces `end != terminalIndex`), and the non-default-zoom conversion test.

Compile, then run the unit suite.

### Phase 5 — New handler and geometry tests

Band behavior (against the pure geometry where possible, otherwise the handler):

1. Press in a gap, drag within the gap → nothing selected. Press in a gap, drag into the next column → that column selected, band snapped to its far extent.
2. Mouse inside a column moving within it → band and selection unchanged. Mouse exits a column into a gap → band resumes tracking, column stays selected.
3. Press inside a column → whole column covered on the first drag movement. Reversal past the anchor column → anchor column still fully enclosed, range flips correctly.
4. Y irrelevance: identical x with wildly different y — above the staff, below the lyrics, outside the component — produce identical selections; and a note on a high ledger line, a rest and a barline are all swept by the same band.
5. Leading edge clamped one document pixel short of the terminal's column, at the end of the staff when there is no terminal, and one document pixel clear of the staff header on the left; grace notes behave as ordinary sweepable columns.

Branch coverage:

6. Shift+press on an element, then drag → no band painted, selection unchanged, `dragDidStart` never called. Plain press on an element, then drag → no band. Empty line and terminal-only line, press and drag → no band armed, nothing painted, selection untouched, no throw. Zoom changes mid-drag → `getDragRectangle()` returns a rectangle scaled by the new `ViewScale.factor()` from unchanged Ss endpoints.

Compile, then run the unit suite.

### Phase 6 — e2e and documentation

1. In `SelectionTest.RangeSelection`, replace `DRAG_START_INSET_PX` and `DRAG_OVERSHOOT_PX` with endpoints derived from the fixture's actual column edges — the midpoint of the gap before note 0 and the midpoint of the gap after note 2. Under the snap-to-span model a 20px overshoot past note 2's center can enter the next column's span and select a fourth element, which would break `testDragSelect`'s count of three; deriving the endpoints removes the dependency on glyph widths entirely.
2. `testDragSelect` and the Shift-click-after-drag test stay as regression guards. `SelectionTest.Drag` exercises pitch dragging and is unaffected.
3. Add `docs/selection.md` covering the column-band model: the ASCII diagram from §3, the snap-on-entry and hold-in-gap rules, the anchor column always being fully enclosed, the X-only hit basis, the grace-host overlap tie-break, terminal unreachability, and why band geometry is stored in staff spaces rather than pixels. Scope it to what this change invents — the `HitPriority` cascade, the click path and the #748 anchor contract are pre-existing and are not documented here.

Run the unit suite, then the e2e suite.

* * *
## Out of Scope

- **Autoscroll during a band drag.** Filed as #754, blocked by this work.
- **Alt-click in EDIT mode does not start a pitch drag.** `NoteDragHandler.handlePress` gates on `getMode() != Mode.SELECT` with no Alt branch, while `LineSelectionHandler.isSelectionActive` treats Alt as select-equivalent. That inconsistency is real but pre-existing.
- **`SelectionCoordinator.setRange`'s asymmetric terminal clamp.** It clamps `end` only when `end > begin`, so a single-element `selectRange(t, t)` on the terminal is not clamped. Pre-existing and untouched here.
- **Horizontal dragging of rests and barlines.** Direct clicks on them are reserved for it, but the behavior itself is future work.
- **Multi-line band selection.** `Selection.Range` holds a single `Line`; extending a band across lines would need a new selection model.
- **Escape to cancel a drag.** No Escape handling exists in `score/` and none is added.
- **Narrowing the drag repaint.** `handleDrag` repaints the whole `LineComponent`; this change already reduces the painted area, and a remembered-bounds optimization would add exactly the kind of state `SelectionDragTracker` exists to clean up after.
