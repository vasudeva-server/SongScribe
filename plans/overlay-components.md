# Overlay Components

**Created:** 2026-07-21  <br>
**Status:** Pending  <br>
**BlockedBy:** —

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Overlay Hosting](#-phase-1-overlay-hosting) | ⏳ Pending | — |
| 2 | [Note Ink Bounds](#-phase-2-note-ink-bounds) | ⏳ Pending | — |
| 3 | [Insertion Marker Component](#-phase-3-insertion-marker-component) | ⏳ Pending | — |
| 4 | [Preview Element Component](#-phase-4-preview-element-component) | ⏳ Pending | — |
| 5 | [Fall and Glissando Components](#-phase-5-fall-and-glissando-components) | ⏳ Pending | — |
| 6 | [Remove Old Mechanism](#-phase-6-remove-old-mechanism) | ⏳ Pending | — |
| 7 | [Manual UI Verification](#-phase-7-manual-ui-verification) | ⏸️ Blocked by 6 | — |
| 8 | [Split Position From Configuration](#-phase-8-split-position-from-configuration) | ⏸️ Blocked by 7 | — |
| 9 | [Tests](#-phase-9-tests) | ⏸️ Blocked by 8 | — |

---

## ⏳ Phase 1: Overlay Hosting

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high — reversible-boundary work on a container's layout contract; a wrong call here silently breaks page layout or scrolling.

Establishes the hosting mechanism and the shared base class that phases 3, 4, and 5 subclass. No overlay behavior changes in this phase; the existing `LineOverlayPainter` drawing path stays fully intact and is removed in phase 6.

### Tasks

1. **Replace `ScoreView`'s layout manager.** `src/main/java/songscribe/ui/component/ScoreView.java:260` currently calls `setLayout(new BorderLayout())`, and `initMainPanel()` at line 370 calls `add(mainPanel, BorderLayout.CENTER)`. `ScoreView` has exactly one child today. `BorderLayout` cannot host the overlay children: an unconstrained `add()` also resolves to `CENTER` and would evict `mainPanel` from `BorderLayout`'s bookkeeping, leaving it unpositioned. Change `ScoreView` to `setLayout(null)` and override `doLayout()` to set `mainPanel`'s bounds to `(0, 0, getWidth(), getHeight())`, reproducing exactly what `BorderLayout.CENTER` did. Overlay children must not be touched by `doLayout()` — their bounds are owned by their controllers.

   Keep this change minimal and self-contained. `specs/184-pagination.md` §5 rewrites `ScoreView`'s layout entirely — `ScorePanel` will host one `PageComponent` per page — so this particular task is expected to be discarded when issue #184 lands. Everything else in this phase is written to survive that; see **Pagination Compatibility** at the end of this plan.

2. **Confirm the size computation is unaffected.** `BorderLayout` also supplied `preferredLayoutSize`/`minimumLayoutSize`. This has been checked and is safe: `layoutPage` calls `setPreferredSize(preferredSizePx)` explicitly at `ScoreView.java:1099`, so `isPreferredSizeSet()` is true and `JComponent.getPreferredSize()` returns that value without consulting a layout manager. The scroll pane's view is `scorePanel` (`ScoreView.java:340-341`), not `ScoreView`, so `ScoreView` is a fixed-preferred-size child within it. Verify this still holds rather than re-deriving it, and check `getMinimumSize()` is not load-bearing for `ScorePanel`'s layout — with a null layout it no longer falls through to `BorderLayout.minimumLayoutSize`.

3. **Create `LineOverlayComponent`** at `src/main/java/songscribe/ui/component/score/LineOverlayComponent.java` — an abstract `JComponent` base for all four overlays. It must:
   - call `setOpaque(false)` in its constructor;
   - register **no** mouse or mouse-motion listeners, so AWT never selects it as an event target and clicks pass through to the `LineComponent` beneath. Mirror `src/main/java/songscribe/ui/component/PasteOverlay.java:35-37`, which documents this same requirement.
   - hold a `@Nullable LineComponent` target reference (the overlay needs it for `middleLineYSs` and `calculateInsertionXSs`, which come from the line's layout);
   - hold its **host container** as a `JComponent` collaborator rather than referring to `ScoreView` by type. The host is whatever ancestor the overlay is a child of and computes its coordinates against. It is `ScoreView` today and becomes `PageComponent` under `specs/184-pagination.md` §5; typing this field as `ScoreView` would force every subclass to be edited when that lands. Support re-homing: allow the host to change at runtime, removing the overlay from the old host and adding it to the new one, because pagination reparents `LinePanel`s across pages on repagination.
   - expose an abstract method returning the overlay's ink bounds in staff spaces relative to the target line's origin, returning null when there is nothing to draw;
   - expose a concrete `updateBounds()` that converts those Ss bounds to host view pixels and calls `setBounds()`, hiding the component (`setVisible(false)`) when the bounds method returns null.

4. **Implement the Ss-to-view-pixel bounds conversion in `LineOverlayComponent`.** The scale is `ScaleContext.getPixelsPerStaffSpace() * targetLine.getViewScale().factor()` — the same expression `LineOverlayPainter.paintOnLine` uses at `src/main/java/songscribe/ui/component/score/LineOverlayPainter.java:110`. Translate the line-relative Ss rect into **host** coordinates using `SwingUtilities.convertPoint(targetLine, 0, 0, host)` — note this works for any ancestor, which is what keeps the conversion valid when the host becomes `PageComponent`. Then `floor` the minimums and `ceil` the maximums to integer pixel boundaries and inflate by a named constant of **1 device pixel per side**. Rationale to put in the constant's Javadoc: Java2D antialiasing only tints pixels the shape actually intersects, so pixel-aligned expansion already contains AA ink; the extra pixel covers `STROKE_NORMALIZE` grid-snapping and glyph hinting. Stroke width is **geometry, not AA** — each subclass must fold half its stroke width into its own Ss bounds rather than relying on this pad.

5. **Implement the line-space to component-space translate.** `NoteRenderer`, `ArticulationRenderer`, `FermataRenderer`, and `SlideRenderer` all consume `LineInvariants` and `ElementFrame` in the **line's** coordinate space, because until now the `Graphics2D` was always pre-translated to the line's origin. A component's graphics origin is its own top-left instead. Give `LineOverlayComponent` a final `paintComponent(Graphics)` that: applies `GraphicUtils.setRenderingHints`, scales by the same factor as task 4, translates by the negative of the component's Ss offset within the line, then calls an abstract render hook. Subclasses implement only the render hook and may then delegate to the existing renderers unchanged.

6. **Add overlay registration to the host with correct z-order.** Add a method to `ScoreView` that registers a `LineOverlayComponent` as a child. Swing paints children from the highest index to the lowest, so **index 0 paints last and therefore on top** — register overlays with `add(overlay, 0)`. Leave `isOptimizedDrawingEnabled()` returning `false` at `ScoreView.java:661` and update its Javadoc: it is no longer a workaround propping up hand-computed dirty rectangles but a truthful declaration that the container has overlapping children, which is what lets Swing compute dirty regions itself. Note in the Javadoc that `JLayeredPane` returns `false` for the same reason, and that any future container hosting overlays — `PageComponent` under `specs/184-pagination.md` §5 — must return `false` for the same reason.

7. **Add the relayout reposition hook — not a zoom hook.** Overlay bounds are fixed in staff spaces but not in pixels, so they go stale whenever a `LineComponent` moves or resizes. Zoom is only one cause; editing notes changes line heights, window resizing changes line width, and the configurable inter-line gap changes spacing. Hooking `ScoreView.applyZoomPercent` would catch one cause out of several. Hook the seam they all pass through instead: **the layout manager that positions line components**, which today is `StaffLinesLayout.layoutContainer` (`src/main/java/songscribe/ui/component/score/StaffLinesLayout.java:184-205`), setting every line's bounds at line 202. After its positioning loop completes, invoke `updateBounds()` on every registered overlay. Do this synchronously rather than via a `ComponentListener` on the target line: `Component.setBounds` posts `COMPONENT_MOVED`/`COMPONENT_RESIZED` asynchronously through the event queue, which would leave the overlay a frame behind. Call only `setBounds()` on the overlays from here — never `revalidate()`, which would re-enter layout.

   Express this as "notify the overlays after line positioning completes" rather than binding it to `StaffLinesLayout` by name. `specs/184-pagination.md` §5 absorbs `StaffPanel`'s single-stack role into per-page containers, so the class hosting this hook will change even though the seam — the moment line bounds are finalized — will not.

   Note for context: there is no push notification to subscribe to instead. `.agents/guides/zoom.md` states `ViewScale` is read on demand, and `ZoomDidChangeNotification` exists only for loosely-coupled observers such as `ZoomAction` and `ZoomStatusBarPanel`; the score tree is driven synchronously by `applyZoomPercent` (`ScoreView.java:1170`), whose `scrollPane.validate()` at line 1214 is what runs `layoutContainer`.

8. **Add the stale-target guard.** `rebuildLayout()` discards `LineComponent` instances and creates new ones, leaving any overlay holding a reference to a component no longer in the hierarchy — its origin within the host would be meaningless. `LineOverlayPainter.paintOnLine` guards this today with `SwingUtilities.isDescendingFrom(line, host)` at `src/main/java/songscribe/ui/component/score/LineOverlayPainter.java:96`. Carry the same guard into `LineOverlayComponent.updateBounds()`: if the target is null or is not descending from the current host, hide the overlay and return rather than computing bounds against a detached component. Under pagination this guard also catches a line that has been reparented onto a different page than the overlay's host, which is the signal to re-home the overlay rather than merely hide it.

9. Run `./scripts/compile.sh` and confirm SUCCESS. Launching the app is not required in this phase; no behavior has changed yet.

---

## ⏳ Phase 2: Note Ink Bounds

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high — composing geometry from five renderers with no existing reference implementation; under-reporting clips visible artwork.

Produces the bounds facility that phase 4 consumes. Independent of phase 1 and may run in parallel with it.

### Tasks

1. **Confirm no existing facility before building one.** The only rectangle-producing method for an element is `ElementHitTest.buildElementHitRect` (`src/main/java/songscribe/ui/component/score/ElementHitTest.java:80-127`), built from fixed per-`ElementType` constants — `getElementWidthSs()` (`src/main/java/songscribe/dom/ElementType.java:250`), `getFullElementHeightSs()` (line 296), `getNoteheadTopOffsetSs()` (line 305). These are nominal click-target dimensions that ignore actual stem direction, ledger-line count, and accidental glyphs. **Do not reuse them for ink bounds** — they under-report whenever accidentals or ledger lines are present.

2. **Create the bounds method.** Add a method returning a `Rectangle2D` of ink bounds in staff spaces, relative to the line origin, for a rendered element given its `LineInvariants` and `ElementFrame`. Place it alongside the geometry it composes — `src/main/java/songscribe/layout/NoteGeometry.java` — unless reading that file shows a better home. It must take the same override-X path the preview uses: `LineRenderer.renderPreviewElement` builds `lineFrame.withElement(lineFrame.currentElementIndex(), x)` at `src/main/java/songscribe/ui/component/score/LineRenderer.java:753` and sub-renderers detect preview rendering via `hasOverrideElementX()`.

3. **Compose the contributors, mirroring `NoteRenderer.render`** (`src/main/java/songscribe/ui/renderer/NoteRenderer.java:147-187`), which draws notehead → stem → flags → dots → ledger lines → accidental in that order. Union:
   - notehead rect;
   - stem: `NoteGeometry.effectiveDirection(note)` (line 230) feeding `NoteGeometry.computeBaseStemGeometry` (line 274) — include `geom.lengthSs()`, `stemLayout.lengtheningSs()`, and `forcedShorteningSs` (lines 282-337), since beam-slope lengthening varies the vertical extent per note;
   - flags and dots;
   - ledger lines: `NoteGeometry.noteNeedsLedgerLines` / `getLedgerLineGeometry` (lines 408-421), including the **horizontal** extent from `extentAtSs(yOffsetSs).leftSs()/rightSs()`;
   - accidental: extends leftward from `NoteGeometry.getAccidentalStartXSs(note)` (`NoteGeometry.java:457`) by the width of the glyph components returned by `getAccidentalComponents` — this is not a fixed width.

4. **Include articulations and fermata.** These are *not* drawn by `NoteRenderer.render`. `LineRenderer.renderPreviewElement` renders them separately afterward at `LineRenderer.java:756-761`: `ArticulationRenderer` when `previewElement.getArticulations()` is non-empty, and `FermataRenderer` when `previewElement.findAttachment(FermataAttachment.class)` is non-null. Union their extents under the same conditions, so the bounds match exactly what the preview path draws.

5. **Fold in stroke half-widths.** Any stroked contributor (stem, ledger lines, flags) extends half its stroke width perpendicular to its path. That is geometry and belongs in these bounds. The 1-device-pixel pad added by `LineOverlayComponent.updateBounds()` in phase 1 covers antialiasing and stroke normalization only, and must not be relied on to absorb stroke width.

6. Run `./scripts/compile.sh` and confirm SUCCESS.

---

## ⏳ Phase 3: Insertion Marker Component

**Status:** Pending  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 5, medium — mechanical subclass plus straightforward state wiring; bounds are exactly computable from constants.

The simplest of the four overlays, and the one that proves the phase 1 base class. Its bounds need no estimation.

### Tasks

1. **Create `InsertionMarkerOverlay`** at `src/main/java/songscribe/ui/component/score/InsertionMarkerOverlay.java`, extending `LineOverlayComponent` from `src/main/java/songscribe/ui/component/score/LineOverlayComponent.java`. Exactly one instance exists for the lifetime of the `ScoreView`; it is never recreated.

2. **Implement its Ss bounds exactly.** Read the existing drawing code at `src/main/java/songscribe/ui/component/score/LineRenderer.java:777-810` (`renderInsertionPoint`) and reproduce its extent: vertically `middleLineYSs + Staff.spToSs(Staff.MIN_STAFF_POSITION_SP)` to `middleLineYSs + Staff.spToSs(Staff.MAX_STAFF_POSITION_SP)`; horizontally the marker's x — `layoutResult.calculateInsertionXSs(targetIndex, 0, previewElement, line, true)` plus `SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2` — widened by half of `INSERTION_POINT_THICKNESS_SS` on each side. Because `MIN_STAFF_POSITION_SP` and `MAX_STAFF_POSITION_SP` are compile-time constants, the component's **height is identical on every line** and changes only with zoom.

3. **Implement the render hook** by delegating to the existing marker drawing. Keep `GraphicUtils.drawRoundedLine` and `ScoreView.getPreviewElementColor()` as they are used today at `LineRenderer.java:806-809`. Coordinates arrive in line space because `LineOverlayComponent.paintComponent` applies the line-space-to-component-space translate.

4. **Wire `PasteModeManager` state transitions** in `src/main/java/songscribe/ui/edit/PasteModeManager.java`. Replace each `repaintWithOverlayHeadroom()` call with a bounds/visibility update on the marker component:
   - `enter()` (lines 170-197) — show the marker if a target is already resolved by `syncTargetToMouse()`;
   - `updateTarget(LineComponent, MouseEvent)` (lines 336-372) — retarget and `updateBounds()`; delete the separate `previous.repaintWithOverlayHeadroom()` at line 367, since Swing now dirties the marker's old and new bounds automatically when it moves;
   - `clearTarget()` (lines 322-330) — hide the marker;
   - `exit()` (lines 239-259) — hide the marker.
   Do not change `PasteOverlay` (`src/main/java/songscribe/ui/component/PasteOverlay.java`) or its `JLayeredPane` hosting — that is the paste-mode banner, a separate concern in viewport space, and it stays as is.

5. **Remove the now-dead target check.** `LineRenderer.renderInsertionPoint` re-fetches `PasteModeManager.getActiveInstance()` and re-tests `getTargetLineComponent() != lc` at `LineRenderer.java:779-782`. The component knows its own target, so this second source of truth must go. This closes finding R1 in `plans/overlay-review-findings.md`.

6. Run `./scripts/compile.sh` and confirm SUCCESS.

---

## ⏳ Phase 4: Preview Element Component

**Status:** Pending  <br>
**BlockedBy:** 1, 2  <br>
**Recommended model/effort:** Opus 4.8, high — the widest state surface of the four, consuming phase 2's bounds and rewiring six state fields.

Correctness first: this phase recomputes bounds on **every** tracked state change. The position-only vs configuration optimization is deliberately deferred to phase 8, after the behavior has been visually verified.

### Tasks

1. **Create `PreviewElementOverlay`** at `src/main/java/songscribe/ui/component/score/PreviewElementOverlay.java`, extending `LineOverlayComponent`. One instance for the `ScoreView`'s lifetime.

2. **Implement its Ss bounds** using the ink-bounds method delivered by phase 2 (in `src/main/java/songscribe/layout/NoteGeometry.java` unless that phase documented a different home). Return null — meaning "hide" — under exactly the conditions `LineRenderer.renderPreviewElement` currently returns early, at `src/main/java/songscribe/ui/component/score/LineRenderer.java:653-714`: no preview element for this line (`PreviewElementManager.hasPreviewElement(lc)`), `Mode.SELECT`, a null preview element, `!lc.isPreviewElementVisible()`, or a null `layoutResult`. Preserve all of them; each is load-bearing.

3. **Implement the render hook** by delegating to the existing preview drawing path unchanged: `NoteRenderer.getInstance().render(...)`, then `ArticulationRenderer` when articulations are present, then `FermataRenderer` when a `FermataAttachment` is present, all under `ScoreView.getPreviewElementColor()` — as at `LineRenderer.java:750-765`. Preserve the override-X mechanism: the frame is built with `lineFrame.withElement(lineFrame.currentElementIndex(), x)` so sub-renderers apply device-pixel snapping to the raw double and do not look the preview element up in the layout, where it does not exist.

4. **Preserve the x-position branch.** `LineRenderer.renderPreviewElement` computes x two ways at lines 723-742: in grace mode it uses `lc.getGraceModeLockedXSs()` directly, because that already accounts for grace-note spacing and `calculateInsertionXSs` would apply normal inter-element spacing instead; otherwise it uses `calculateInsertionXSs(currentXIndex, mouseX, previewElement, line, false)` with `PreviewElementManager.getCurrentMouseXSs()` rather than `getMousePosition()`, which can return null during repaints. Keep both branches and the reason for each.

5. **Wire `PreviewElementManager` — correctness first.** In `src/main/java/songscribe/ui/component/score/PreviewElementManager.java`, replace every `repaintWithOverlayHeadroom()` call with a retarget-plus-`updateBounds()` on the overlay: line 197 (`clearPreviewElement`, hide), line 683 (`trackMouse`, line changed), line 696 (`trackMouse`, slide placeholder branch), line 719 (`trackMouse`, general case), line 776 (`handleClick`, slide committed). Also handle the mode-driven paths at lines 138-163 (`modeDidChange`, `playbackStateDidChange`, `pasteModeDidChange`), which call `clearPreviewElement()` or `restorePreviewElement(currentMouseLine)`. **Recompute bounds unconditionally on each of these** — do not attempt to distinguish position-only from configuration changes in this phase. `setBounds()` is cheap and Swing dirties both the old and new rectangles.

6. **Leave the slide placeholder path alone.** When `PreviewElementManager.isSlidePlaceholder(previewElement)` is true (line 451-453) there is no note-head preview at all — the slide branch at `LineRenderer.java:673-711` draws instead. `PreviewElementOverlay` must return null bounds for slide placeholders; phase 5 delivers the components that cover that case.

7. Run `./scripts/compile.sh` and confirm SUCCESS.

---

## ⏳ Phase 5: Fall and Glissando Components

**Status:** Pending  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 5, medium — two focused subclasses with exactly computable bounds; the split between them is already decided.

These are two separate components, not one "slide preview". A fall is a fixed glyph whose size never varies; a glissando spans two resolved endpoints. Merging them would reintroduce unpredictable sizing into a component that is otherwise fixed.

### Tasks

1. **Create `FallPreviewOverlay`** at `src/main/java/songscribe/ui/component/score/FallPreviewOverlay.java`, extending `LineOverlayComponent`. `SlideRenderer.renderPreviewFall` (`src/main/java/songscribe/ui/renderer/SlideRenderer.java:300-315`) resolves only a source note context and calls `drawFallGlyph` — it never resolves a target element. Its size is therefore **constant** and changes only with zoom; only its anchor moves. Bounds are the fall glyph's outline bounds at the source note's position, plus half the glyph's stroke width where stroked.

2. **Create `GlissandoPreviewOverlay`** at `src/main/java/songscribe/ui/component/score/GlissandoPreviewOverlay.java`, extending `LineOverlayComponent`. `SlideRenderer.renderPreviewGlissando` (`SlideRenderer.java:269-286`) resolves both a source context and a target context via `resolveNoteContext`/`resolveTargetContext` and draws between them. Bounds are the union of the two resolved endpoints, expanded by half the drawn line's stroke width. Note that the endpoints are anchored to **note positions**, so the shape snaps when the integer `xIndex` changes rather than tracking the mouse continuously — it is discrete, not continuous, but its size does vary.

3. **Implement both render hooks** by delegating to `SlideRenderer.getInstance().renderPreviewFall(...)` and `renderPreviewGlissando(...)` unchanged, under `ScoreView.getPreviewElementColor()`, matching `LineRenderer.java:700-707`.

4. **Wire the visibility gates** in `src/main/java/songscribe/ui/component/score/PreviewElementManager.java`. Both components must return null bounds (hide) unless all of these hold, mirroring `LineRenderer.java:673-711`: the preview element is a slide placeholder (`isSlidePlaceholder`), `shouldShowSlidePreview()` is true, `getSlideZone()` is non-null, the line is non-null, and `sourceAlreadyHasSlide(line, sourceIndex, zone)` is false. The source index is `PreviewElementManager.getCurrentXIndex() - 1`. Select between the two components on the `SlideZone` value: `GLISSANDO` shows the glissando overlay, `FALL` shows the fall overlay. The two are mutually exclusive — never both visible.

5. Run `./scripts/compile.sh` and confirm SUCCESS.

---

## ⏳ Phase 6: Remove Old Mechanism

**Status:** Pending  <br>
**BlockedBy:** 3, 4, 5  <br>
**Recommended model/effort:** Sonnet 5, low — mechanical deletion once all four components are live; the compiler finds the stragglers.

Nothing here may run until phases 3, 4, and 5 have each replaced their overlay, or the score will paint no overlays at all.

### Tasks

1. **Delete `LineOverlayPainter`** — `src/main/java/songscribe/ui/component/score/LineOverlayPainter.java` in full, and its test `src/test/java/songscribe/ui/component/score/LineOverlayPainterPasteOverlayTest.java`. Use `jet_brains_safe_delete` so remaining usages are reported rather than silently broken.

2. **Remove the `paintChildren` override** from `src/main/java/songscribe/ui/component/ScoreView.java:647-650`, including its Javadoc. Overlays are now real children painted by `super.paintChildren`. Leave `isOptimizedDrawingEnabled()` at line 661 returning `false` — phase 1 rewrote its Javadoc to explain that it is now a truthful declaration of overlapping children rather than a workaround.

3. **Delete `LineComponent.repaintWithOverlayHeadroom()`** — `src/main/java/songscribe/ui/component/score/LineComponent.java:927-954` — and its test `src/test/java/songscribe/ui/component/score/LineComponentPreviewHeadroomTest.java`. All callers were rewired in phases 3, 4, and 5; confirm with `jet_brains_find_referencing_symbols` before deleting.

4. **Delete `LineSpacing.PREVIEW_REPAINT_MARGIN_SS`** — `src/main/java/songscribe/layout/LineSpacing.java:89`. This is the arbitrary 1.5-staff-space slop the whole plan exists to remove. Verify no remaining references, including in tests. Do **not** touch `MIN_ABOVE_STAFF_SS`, `MIN_BELOW_STAFF_SS`, `MIN_ABOVE_MIDLINE_SS`, `MIN_BELOW_MIDLINE_SS`, or `MIN_LINE_HEIGHT_SS` — those are real layout extents that lines still need for ledger-line room, unrelated to overlay painting.

5. **Remove the overlay plumbing from the render path.** Delete `LineComponent.renderPreviewOverlay` and `LineComponent.renderInsertionPointOverlay` (`LineComponent.java:904-917`), and `LineRenderer.renderPreviewOverlay` and `LineRenderer.renderInsertionPointOverlay` (`src/main/java/songscribe/ui/component/score/LineRenderer.java:195-229`). Also delete `PreviewElementManager.paintOverlay` (`src/main/java/songscribe/ui/component/score/PreviewElementManager.java:238`) and its test `src/test/java/songscribe/ui/component/score/PreviewElementManagerPaintOverlayTest.java`. The drawing code these called — `renderPreviewElement` and `renderInsertionPoint` — is now invoked by the components and must be kept, though it may need its access widened to package-private visibility for the component classes.

6. **Fix the stale Javadoc uncovered during review.** `LineRenderer.renderInsertionPoint`'s doc at `LineRenderer.java:768` still claims the marker is "drawn last, still inside the single Ss transform `LineComponent.render` establishes" — untrue since it moved to the overlay pass, and still untrue now. Rewrite it to describe component-hosted painting. Same for `PasteModeManager.getActiveInstance`'s doc at `src/main/java/songscribe/ui/edit/PasteModeManager.java:125`, which says "Used by `LineRenderer`". This closes finding Q2 in `plans/overlay-review-findings.md`.

7. Run `./scripts/compile.sh` and confirm SUCCESS. Do not run tests yet — several test files were deleted in this phase and replacements are written in phase 9.

---

## ⏳ Phase 7: Manual UI Verification

**Status:** Blocked  <br>
**BlockedBy:** 6  <br>
**Recommended model/effort:** None — this phase is performed by the user, not by a model.

No tests may be written until the user confirms the behavior is correct. Do not proceed to phase 8 or 9 without that confirmation.

### Tasks

1. **Ask the user to run the app and verify.** Do not launch it without explicit permission. The user checks each overlay end to end:
   - hover preview follows the mouse, at every zoom level, with no clipped glyphs and no stale ink trails;
   - preview with accidentals, with ledger lines well above and below the staff, and with articulations and a fermata — these are the cases the old fixed headroom under-reported and the cases phase 2's composed bounds must cover;
   - preview on the **first** and **last** line of a page, at the highest and lowest legal staff positions, where clipping would previously have occurred;
   - paste-mode insertion marker: appears on entering paste mode, tracks across insertion points and across lines, disappears on exit and when the pointer leaves a line;
   - glissando and fall previews with the slide tool, including the case where the source note already carries that slide type (nothing should draw);
   - zoom in and out while each overlay is visible, confirming it resizes and stays correctly positioned;
   - **relayout while an overlay is visible** — these are the cases the phase 1 task 7 hook exists for, and each moves lines by a different route: add and delete notes on a line so its height changes, resize the window so line width changes and content reflows, and change the inter-line gap preference. The overlay must follow its line rather than staying at stale coordinates;
   - scroll the score while each overlay is visible; overlays are children of `ScoreView` in page coordinates, so they must scroll with the content rather than floating in the viewport.

2. **Confirm the page still lays out and scrolls correctly** — this is the risk introduced by phase 1's replacement of `ScoreView`'s `BorderLayout` with a null layout and an explicit `doLayout()`. Check the page is correctly sized at several zoom levels and that scrolling reaches the whole score.

3. **Record any defects and fix them before proceeding.** Re-verify after each fix. Phases 8 and 9 stay blocked until the user confirms all of the above.

---

## ⏳ Phase 8: Split Position From Configuration

**Status:** Blocked  <br>
**BlockedBy:** 7  <br>
**Recommended model/effort:** Opus 4.8, medium — reasoning about which of six interacting state fields affect size versus position.

Phase 4 deliberately recomputes bounds on every change. This phase adds the optimization: reposition when only the position moved, resize only when the drawn configuration actually changed.

### Tasks

1. **Split the `trackMouse` guard.** `src/main/java/songscribe/ui/component/score/PreviewElementManager.java:674-679` currently tests six fields together — `lc`, `xIndex`, `staffPosition`, `xPosSsMatchesElement`, `yPosSpMatchesElement`, `currentSlideZone` — and funnels every change into one generic repaint. Separate them into a position group and a configuration group.

2. **Classify `staffPosition` as configuration, not position.** This is the subtle one and the reason this phase is Opus. A purely vertical move changes `staffPosition`, which flips stem direction via `previewElement.setDirection(StaffElement.defaultDirection(previewElement))` at `src/main/java/songscribe/ui/component/score/LineRenderer.java:735`, and crosses the threshold where ledger lines appear and widen the ink. Both change the component's **size**, not just its location. Treat `staffPosition` as a resize trigger. Both effects are step functions, so recomputes stay infrequent despite the mouse moving continuously.

3. **Route position-only changes to `setLocation()`** and configuration changes to a full bounds recompute plus `repaint()`. `xIndex` alone moves the preview horizontally without changing its shape and is the main beneficiary.

4. **Apply the same split to the insertion marker.** In `src/main/java/songscribe/ui/edit/PasteModeManager.java`, `updateTarget` (lines 336-372) changes only the marker's location — its height is constant across all lines, being derived from the compile-time constants `Staff.MIN_STAFF_POSITION_SP` and `Staff.MAX_STAFF_POSITION_SP`. It therefore needs `setLocation()` only, never a resize. The sole resize trigger for this component is zoom.

5. **Ask the user to re-verify the same checklist as phase 7**, with attention to the vertical-movement cases: dragging the preview up and down through the stem-direction flip and across the ledger-line thresholds, confirming no clipping and no stale ink appear at those transitions.

6. Run `./scripts/compile.sh` and confirm SUCCESS.

---

## ⏳ Phase 9: Tests

**Status:** Blocked  <br>
**BlockedBy:** 8  <br>
**Recommended model/effort:** Sonnet 5, medium — test authoring against a design already verified by hand.

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` before writing anything. Phase 6 deleted `LineOverlayPainterPasteOverlayTest`, `LineComponentPreviewHeadroomTest`, and `PreviewElementManagerPaintOverlayTest`; this phase replaces their coverage against the new design.

### Tasks

1. **Test the ink-bounds composition from phase 2** — the highest-value target, since under-reporting clips visible artwork. Cover a plain notehead, a note with an accidental, a note needing ledger lines above and below, a stem-up versus stem-down note, and a note carrying articulations and a fermata. Assert the returned bounds actually contain the contributing geometry. This is real computed geometry, and review finding TU3 in `plans/overlay-review-findings.md` records that the equivalent marker geometry was historically untested.

2. **Test `LineOverlayComponent.updateBounds()` conversion** — that Ss bounds convert to the expected view-pixel rectangle at a zoom far enough from 100% that ignoring zoom cannot pass as rounding, that minimums floor and maximums ceil, and that the 1-device-pixel antialiasing pad is applied on all four sides. Assert against concrete numeric values, not just non-null.

3. **Test the visibility gates** for each of the four components: each returns null bounds and hides under its documented conditions. For `PreviewElementOverlay` that is the full early-return set from phase 4 task 2. For the slide components it is the gate set from phase 5 task 4, including the `sourceAlreadyHasSlide` case. A test asserting only "did not throw" does not count — assert the component is actually hidden. Review finding TC1 records that exact defect in the deleted test.

4. **Test the `PasteModeManager` and `PreviewElementManager` transitions** drive show, hide, and reposition correctly — including `clearTarget()`, whose repaint was previously unverified (review finding TU1). Prefer asserting observable component state (`isVisible()`, `getBounds()`) over mock call counts; review finding TC2 flagged exact-call-count verification as fragile.

5. **Test the phase 8 split**: a position-only change repositions without resizing, and a `staffPosition` change that flips stem direction or adds ledger lines does resize.

6. Run `./scripts/compile.sh`, then `./scripts/test.sh unit`, and confirm green. Do not run e2e tests without the user's approval.

---

## Pagination Compatibility

`specs/184-pagination.md` (issue #184) introduces on-screen pagination and rewrites the containers these overlays live in. This plan is intended to land **before** #184, so that pagination inherits four self-contained components with exact bounds rather than a headroom mechanism that would have to be carried across page boundaries. §5 of that spec already calls page-aware coordinate mapping "the highest-risk area of the change."

Constraints recorded here so whoever implements #184 does not have to rediscover them:

1. **The host changes from `ScoreView` to `PageComponent`.** Today `ScoreView` is the single white page surface. Under §5, `ScorePanel` hosts one `PageComponent` per page. The hosting principle is unchanged — put the overlay at the level whose bounds are never the binding constraint — and a page still contains any ink a line can produce, including into the margins. `PageComponent` is arguably the more correct host, since an overlay must never spill onto the inter-page gap or an adjacent page, which the pre-pagination design has no way to prevent. Phase 1 task 3 types the host field as `JComponent` specifically so this swap does not touch the subclasses.

2. **Phase 1 task 1 is expected to be discarded.** It changes `ScoreView` from `BorderLayout` to a null layout with an explicit `doLayout()`. §5 replaces that layout wholesale. The task is deliberately minimal and self-contained for this reason. Whatever container ends up hosting overlays must return `false` from `isOptimizedDrawingEnabled()` — see phase 1 task 6.

3. **Repagination reparents lines across pages.** §5 distributes `LinePanel`s across `PageComponent`s and reparents them when pagination changes. An overlay whose target line moves to another page must **re-home** to that page's container, not merely hide. Phase 1 task 3 requires the host to be reassignable at runtime, and phase 1 task 8's `isDescendingFrom` guard is what detects the condition.

4. **The relayout hook moves with `StaffPanel`'s absorbed role.** Phase 1 task 7 hooks the moment line bounds are finalized, which is `StaffLinesLayout.layoutContainer` today. §5 absorbs `StaffPanel`'s single-stack role into per-page containers, so the hosting class changes but the seam does not. The hook must also fire on repagination; §10 introduces `PaginationDidChangeNotification` for exactly this kind of observer.

5. **§7's `paintPage` must not paint the overlays.** That method paints one page for print with edit-time decorations — selection highlights, edit overlays, insertion cursors — suppressed via a render-mode flag. These overlays are ephemeral UI and must be suppressed there. Because they are separate child components rather than drawing welded into `paintChildren`, suppression is simply skipping the overlay children while painting the content ones. This is strictly easier than it would have been under the mechanism this plan removes.

## Review Findings Closed

`plans/overlay-review-findings.md` findings resolved by this plan: **R1** (phase 3), **Q2** (phase 6), **E1**, **E2**, **E3** (superseded — the hand-computed dirty rectangle, the duplicated `buildInvariants()`, and the unclipped overlay pass all disappear with `LineOverlayPainter`), **TC1**, **TU1**, **TU3** (phase 9).

Findings **not** addressed here and still open independently: **Q1** (`PreviewElementManager.paintOverlay` visibility — moot once phase 6 deletes the method), **Q3** (`getActiveInstance` Javadoc — folded into phase 6 task 6), **TU2** (test duplication — the duplicated classes are deleted in phase 6), **TU6** (`LineComponentPreviewHeadroomTest` naming — the class is deleted in phase 6), **TC2** and **TU4/TU5** (addressed by phase 9's approach but not tracked as discrete fixes).
