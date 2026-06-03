### 6D. `ui/adjustment`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| Adjustment | `mousePressed`: ignores event when `enabled=false` | unit | none | missing | Add unit test: mock scoreView, setEnabled(false), fire mousePressed, assert startedDrag remains false and scoreView.setDragDisabled never called | ✅ |
| Adjustment | `mousePressed`: sets startedDrag=true, captures startPoint, calls startedDrag(), disables ScoreView drag when startedDrag flag survives startedDrag() | unit | none | missing | Add unit test via concrete subclass or spy; assert startedDrag=true and setDragDisabled(true) called | ✅ |
| Adjustment | `mouseReleased`: ignores event when `enabled=false` | unit | none | missing | Add unit test: verify finishedDrag not called and drag not re-enabled | ✅ |
| Adjustment | `mouseReleased`: clears startedDrag, calls finishedDrag(), re-enables ScoreView drag | unit | none | missing | Add unit test | ✅ |
| Adjustment | `mouseDragged`: ignores event when `enabled=false` | unit | none | missing | Add unit test | ✅ |
| Adjustment | `mouseDragged`: clamps X to [topLeftDragBounds.x, bottomRightDragBounds.x-1] | unit | none | missing | Critical arithmetic: test exact boundary values — at bound, one-past-bound, below bound; assert endPoint.x is clamped precisely | ✅ |
| Adjustment | `mouseDragged`: clamps Y to [topLeftDragBounds.y, bottomRightDragBounds.y-1] | unit | none | missing | Same — exact value assertions, not just sign | ✅ |
| Adjustment | `mouseDragged`: skips drag() when startedDrag=false | unit | none | missing | Add unit test | ✅ |
| HorizontalAdjustment | `startedDrag()`: sets startedDrag=false when no AdjustRect contains startPoint | unit | none | missing | Unit test with populated adjustRects, click outside all — assert startedDrag=false | ✅ |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: left bound = prev note x + rect.width; right bound = next note x - rect.width | unit | none | missing | Exact arithmetic; mock line with known note positions; assert topLeftDragBounds.x and bottomRightDragBounds.x to exact pixel values | ✅ |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: left bound = 20 + rect.width when xIndex=0 (no predecessor) | unit | none | missing | Edge case: first note | ✅ |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: right bound = lineWidth when xIndex = last note | unit | none | missing | Edge case: last note | ✅ |
| HorizontalAdjustment | `startedDrag()` TO_END_OF_LINE: bounds computation | unit | none | missing | Exact arithmetic | ✅ |
| HorizontalAdjustment | `startedDrag()` STRETCH_NOTE_SPACING: stretchHelper populated with note x positions; reallocated when too small | unit | none | missing | Assert stretchHelper values equal note xOffsets | ✅ |
| HorizontalAdjustment | `startedDrag()` GLISSANDO_START: right bound = next AdjustRect's rect.x | unit | none | missing | Exact index lookup | ✅ |
| HorizontalAdjustment | `startedDrag()` GLISSANDO_END: left bound = prev AdjustRect's rect.x + rect.width | unit | none | missing | | ✅ |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x NOT adjusted when `!isInteractable` (terminal node) | unit | HorizontalAdjustmentTest.SnapToEndSkipped.testFinalDoubleBarlineTerminalSkipsSnap | inadequate | Existing test only asserts `isInteractable=false` and `snapToEnd=true` on the model; it never exercises `drag()` — endPoint.x is never set and the snap branch is never reached. The test verifies preconditions of the guard, not that drag() actually skips the snap. | ✅ |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x NOT adjusted when `!isInteractable` (REPEAT_RIGHT terminal) | unit | HorizontalAdjustmentTest.SnapToEndSkipped.testRepeatRightTerminalSkipsSnap | inadequate | Same issue as above — precondition-only assertion, drag() never called | ✅ |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x IS adjusted to `lineWidth - contentWidthPx` when interactable, snapToEnd, and within END_SNAP_LIMIT | unit | none | missing | The actual snap arithmetic is completely untested | ✅ |
| HorizontalAdjustment | `drag()` SINGLE_NOTE: `note.setXOffsetPx(endPoint.x)` | unit | none | missing | Verify model mutation; exact value | ⬜ |
| HorizontalAdjustment | `drag()` TO_END_OF_LINE: all notes from xIndex forward shifted by (endPoint.x - diffX) | unit | none | missing | Multi-note delta arithmetic; assert each element's x exactly | ⬜ |
| HorizontalAdjustment | `drag()` STRETCH_NOTE_SPACING: each element x = firstX + (stretchHelper[i]-firstX)*ratio; `changeElementSpacingRatio` called | unit | none | missing | Non-trivial float ratio arithmetic | ⬜ |
| HorizontalAdjustment | `drag()` START_OF_LINE: all lines' notes shifted by diff from new x | unit | none | missing | Multi-line cascading offset math | ⬜ |
| HorizontalAdjustment | `drag()` GLISSANDO_START: `glissando.x1Translate += endPoint.x - diffX` | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `drag()` GLISSANDO_END: `glissando.x2Translate += endPoint.x - diffX` | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `drag()` CRESCENDO_START/DIMINUENDO_START: `setX1ShiftSs` incremented by delta | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `drag()` CRESCENDO_END/DIMINUENDO_END: `setX2ShiftSs` incremented by delta | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `drag()`: `song.setModified(true)` called on every drag event | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `finishedDrag()`: draggingRect set to null | unit | none | missing | Trivial but verifiable | ⬜ |
| HorizontalAdjustment | `setEnabled(true)`: adjustRects populated with correct types and counts | unit | none | missing | Verify per type: SINGLE_NOTE count = effectiveElementCount, STRETCH = 1 per line, etc. | ⬜ |
| HorizontalAdjustment | `setEnabled(false)`: adjustRects cleared | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `setEnabled(true)`: GLISSANDO_END rect NOT added for SLIDE_OUT glissando | unit | none | missing | Important conditional: CONNECTED vs SLIDE_OUT | ⬜ |
| HorizontalAdjustment | `findHairpinByAnchor`: returns crescendo/diminuendo matching anchorElementIndex | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `findHairpinByEnd`: returns crescendo/diminuendo matching endElementIndex | unit | none | missing | | ⬜ |
| HorizontalAdjustment | `drag()` diffX calculation: `diffX = draggingRect.rect.x + (rect.width/2)` — midpoint arithmetic | unit | none | missing | Exact mid-point computation drives all delta calculations | ⬜ |
| VerticalAdjustment | `startedDrag()`: sets startedDrag=false when startPoint=null | unit | none | missing | Null guard path | ⬜ |
| VerticalAdjustment | `startedDrag()`: sets startedDrag=false when no AdjustRect contains startPoint | unit | none | missing | | ⬜ |
| VerticalAdjustment | `startedDrag()` ROW_HEIGHT: topLeft.y = noteYPosPx(6, 0); bottomRight.y = Integer.MAX_VALUE | unit | none | missing | Exact bound values | ⬜ |
| VerticalAdjustment | `startedDrag()` TEMPO_CHANGE / FIRST_SECOND_ENDING / TRILL / BEAT_CHANGE: upLeft.y = noteYPosPx(6, line-1); downRight.y = noteYPosPx(-4, line) | unit | none | missing | Exact bound arithmetic | ⬜ |
| VerticalAdjustment | `startedDrag()` ANNOTATION / TUPLET / CRESCENDO_Y / DIMINUENDO_Y: upLeft.y = noteYPosPx(6, line-1); downRight.y = noteYPosPx(-6, line+1) | unit | none | missing | Exact bound arithmetic | ⬜ |
| VerticalAdjustment | `drag()` diffY calculation: `(endPoint.y - dragRect.rect.y) + midPoint.y` | unit | none | missing | Core delta arithmetic driving ALL adjust* calls; should be tested with exact values | ⬜ |
| VerticalAdjustment | `drag()` diffX calculation: `(endPoint.x - dragRect.rect.x) + midPoint.x` — only used internally, rect.y updated | unit | none | missing | | ⬜ |
| VerticalAdjustment | `adjustAttribution(diffY)`: posts LayoutDidChangeNotification with attributionStartYSs + diffY | unit | none | missing | Exact new value | ⬜ |
| VerticalAdjustment | `adjustTopSpace(diffY)`: posts LayoutDidChangeNotification with topPaddingSs + diffY | unit | none | missing | Exact new value | ⬜ |
| VerticalAdjustment | `adjustRowHeight(diffY)`: posts LayoutDidChangeNotification with rowHeightAdjustmentSs + diffY | unit | none | missing | Exact new value | ⬜ |
| VerticalAdjustment | `adjustTempoChange(line, diffY)`: all TempoChangeAttachment.userYOffsetSs incremented by diffY | unit | none | missing | Accumulation for multi-note lines | ⬜ |
| VerticalAdjustment | `adjustBeatChange(line, diffY)`: all BeatChangeAttachment.userYOffsetSs incremented by diffY | unit | none | missing | | ⬜ |
| VerticalAdjustment | `adjustFirstSecondEnding(line, diffY)`: all Ending.yPositionSs incremented by diffY | unit | none | missing | | ⬜ |
| VerticalAdjustment | `adjustAnnotation(line, diffY)`: both userYOffsetSs AND legacy yPosPx incremented by diffY | unit | none | missing | Dual-update is a subtle correctness requirement | ⬜ |
| VerticalAdjustment | `adjustAnnotation(line, diffY)`: no-op when dragRect has no AnnotationAttachment | unit | none | missing | Null-guard path | ⬜ |
| VerticalAdjustment | `adjustTrill(line, diffY)`: all Trill.yPositionSs incremented by diffY | unit | none | missing | | ⬜ |
| VerticalAdjustment | `adjustDynamics(line, diffY)`: Hairpin.yShiftSs incremented by diffY | unit | none | missing | | ⬜ |
| VerticalAdjustment | `adjustDynamics(line, diffY)`: no-op when dragRect=null or hairpin not found | unit | none | missing | | ⬜ |
| VerticalAdjustment | `adjustTuplet(line, diffY)`: Tuplet.verticalPositionSs incremented by diffY | unit | none | missing | | ⬜ |
| VerticalAdjustment | `adjustTuplet(line, diffY)`: no-op when tuplet not found | unit | none | missing | | ⬜ |
| VerticalAdjustment | `finishedDrag()`: dragRect set to null | unit | none | missing | | ⬜ |
| VerticalAdjustment | `setEnabled(true)`: ATTRIBUTION rect added only when attribution non-empty | unit | none | missing | Conditional enrollment | ⬜ |
| VerticalAdjustment | `setEnabled(true)`: TOP_SPACE rect added only when lineCount > 0 | unit | none | missing | | ⬜ |
| VerticalAdjustment | `setEnabled(true)`: ROW_HEIGHT rect added only when lineCount > 1 | unit | none | missing | | ⬜ |
| VerticalAdjustment | `setEnabled(true)`: per-line rects (TEMPO_CHANGE, ANNOTATION, ENDING, TRILL, BEAT_CHANGE, CRESCENDO_Y, DIMINUENDO_Y, TUPLET) populated for matching elements | unit | none | missing | | ⬜ |
| VerticalAdjustment | `setEnabled(false)`: adjustRects cleared | unit | none | missing | | ⬜ |
| VerticalAdjustment | `getAttributionAdjustRect`: x = sheetWidthPx - HANDLE_SIZE_PX; y = attributionStartYSs | unit | none | missing | Exact pixel placement | ⬜ |
| VerticalAdjustment | `getHeightAdjustRect`: x = 0; y = noteYPosPx(0, line) - HANDLE_SIZE_PX/2 | unit | none | missing | Exact pixel placement (HANDLE_SIZE_PX/2 rounding matters) | ⬜ |
| VerticalAdjustment | `getRangeElementAdjustRect`: x = startNote.x - xOffsetPx; y = bounds.topSs - HANDLE_SIZE_PX | unit | none | missing | Exact geometry | ⬜ |
| VerticalAdjustment | `getRangeElementAdjustRect`: returns false and skips when rangeElement/startNote/endNote is null | unit | none | missing | Null-guard correctness | ⬜ |
| VerticalAdjustment | dynamics handle x = (startX + endX + DYNAMICS_HANDLE_CENTER_BIAS_PX) / 2 | unit | none | missing | Integer midpoint arithmetic with bias constant | ⬜ |
| VerticalAdjustment | tuplet handle x = startNote.x (upper stem) vs startNote.x + TUPLET_LOWER_HANDLE_X_OFFSET_PX (lower stem) | unit | none | missing | Branch on isUpper() | ⬜ |
| VerticalAdjustment | `findHairpinByAnchor`: returns crescendo/diminuendo matching anchorElementIndex | unit | none | missing | | ⬜ |
| VerticalAdjustment | `getLayoutResultForLine`: throws IllegalStateException when mainPanel null or layoutResult null | unit | none | missing | Error-path contracts | ⬜ |
| VerticalAdjustment | `repaint()`: renders all AdjustRect handles with correct colors | none | none | none | Pure Graphics2D rendering, no branching logic worth testing | — |
| HorizontalAdjustment | `repaint()`: renders all AdjustRect handles | none | none | none | Pure rendering | — |
| Adjustment | constructor: registers self as MouseListener and MouseMotionListener on scoreView | none | none | none | Pure listener registration — no branching or computation; framework wiring, not our risk | — |

**6D notes (quality concerns):**

The two existing tests in `HorizontalAdjustmentTest` (`testFinalDoubleBarlineTerminalSkipsSnap` and `testRepeatRightTerminalSkipsSnap`) are **inadequate** in a particularly misleading way: they are correctly named ("SnapToEndSkipped") but they never call `drag()` and never set `endPoint`. They verify only that the model properties which *would* allow the snap to fire happen to be false/true — i.e., they test preconditions on `Song.isInteractable` and `ElementType.snapToEnd`, not the behavior of `drag()` itself. The snap branch in `drag()` is entirely uncovered: a test asserting `endPoint.x` was NOT modified (or WAS modified for an interactable note near the end) would actually exercise the code. The class has zero additional tests for any of the ~10 drag operation types, the bounds computation, the stretchHelper ratio arithmetic, or the `setModified` call.

`VerticalAdjustment` has **zero tests** of any kind despite containing non-trivial logic: the `diffY` calculation `(endPoint.y - dragRect.rect.y) + midPoint.y` drives all model mutations; the `adjustAnnotation` dual-update (both `userYOffsetSs` and legacy `yPosPx`); the conditional HANDLE_SIZE_PX/2 midpoint in `getHeightAdjustRect`; the dynamics center-bias arithmetic; and the `isUpper()` branch in the tuplet handle placement. Any silent regression in these calculations would go undetected.

`Adjustment` (the abstract base) has zero tests for its mouse-event dispatch, the enabled guard, and most critically the X/Y clamping arithmetic in `mouseDragged` — the only shared geometry computation in the hierarchy.

The `HorizontalAdjustment` GLISSANDO_END omission for SLIDE_OUT glissandos (`setEnabled` conditional) is a correctness rule that is completely untested.

The unit-conversion guide identifies `diffY` and `diffX` as values arriving in pixels (`endPoint` is an AWT pixel coordinate) applied as-is to `Ss`-suffixed fields (e.g. `getUserYOffsetSs() + diffY`). This is a potential mixed-unit bug that no test currently guards.

