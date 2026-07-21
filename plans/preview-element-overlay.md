# Preview Element Overlay (#591 follow-on)

**Created:** 2026-07-20
**Status:** Pending
**BlockedBy:** [per-line-heights-pairwise-spacing.md](./per-line-heights-pairwise-spacing.md) → Phase 5

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1 | [Overlay Painting](#-phase-1-overlay-painting) | ✅ Complete | — |
| 2 | [Input Reachability](#-phase-2-input-reachability) | ✅ Complete | — |
| 3 | [Manual UI Verification](#-phase-3-manual-ui-verification) | ✅ Complete | — |
| 4 | [Tests](#-phase-4-tests) | ✅ Complete (re-scoped — see [Phase 4](#-phase-4-tests)) | — |

---

## The Problem This Solves

`plans/per-line-heights-pairwise-spacing.md` (issue #591) changed `LineComponent` from a
song-wide uniform height to hugging its own content: `StaffLinesLayout` now sizes each line
from unfloored `contentAboveStaffSs` / `contentBelowStaffSs` on its `LayoutResult`, and the
old `Staff.MIN_ABOVE_STAFF_SS` (3.0) / `Staff.MIN_BELOW_STAFF_SS` (4.0) floors were removed
from those extents because they were what made inter-line spacing excessive.

Those floors were not padding. `Staff.java:47,55` derives them from
`Staff.MIN_STAFF_POSITION_SP` (−10) and `Staff.MAX_STAFF_POSITION_SP` (12) — exactly the
legal staff-position range that `PreviewElementManager.isValidStaffPosition`
(`PreviewElementManager.java:1032`) accepts for the hover preview element. Removing them
gave `LineComponent` two defects on any line whose ink does not already reach those extremes:

1. **Clipping.** The preview element is painted inside `LineComponent`
   (`LineRenderer.render` → `renderPreviewElement`, `LineRenderer.java:167`), so Swing clips
   it at the component edge. Observed as a preview note a few ledger lines above the staff
   being cut off. Dragging a real note is unaffected because a drag mutates the model, layout
   reruns, and the line grows.
2. **Unreachability.** Mouse events only reach `LineComponent` inside its bounds
   (`LineComponent` registers its own listeners at `LineComponent.java:185-186`). On a line
   with no high content the component top sits at the staff top, so staff positions −5..−10
   cannot be hovered at all.

**The chosen fix is an overlay**: the preview element is painted by `StaffPanel` *after* its
children, in `StaffPanel` coordinates, so it is never clipped by a line's bounds; and
`ScoreView` — which already receives every event landing in the gaps between lines — forwards
those that fall in a line's headroom band to that line, so the full legal staff-position range
stays reachable. (See the revision note in Phase 2: forwarding from `StaffPanel` was the
original design and does not work.) Line component bounds and inter-line
spacing are **not** changed — the #591 spacing fix must not regress.

`StaffPanel` is sized by `StaffLinesLayout.preferredLayoutSize` to exactly the lines' total
extent, so it has slack past the first and last lines only because of what the score itself
always puts there: the attribution block above line 0 (stacked into the line's own
`contentAboveStaffSs` via `VerticalStackingCalculator.stackAttribution`), and the underlyrics
band plus its margin below the last line. Both are always present, so `StaffPanel` is a
sufficient overlay host and no page-level painting is needed.

**This whole plan is therefore blocked by Phase 5 of
[per-line-heights-pairwise-spacing.md](./per-line-heights-pairwise-spacing.md)**, which makes
every line reserve its first verse row unconditionally. Until that lands,
`LayoutResult.lyricsBandHeightSs` still returns `0.0` for a line with no verses, so a
lyric-less last line gives `StaffPanel` no slack below its staff and the overlay would still
be clipped there — precisely the defect this plan exists to remove. Do not start Phase 1
before confirming that Phase 5 is complete.

---

## ✅ Phase 1: Overlay Painting

**Status:** Complete
**BlockedBy:** —
**Recommended model/effort:** Opus 4.8, high effort — moves a render step across a component
boundary, which means reconstructing a coordinate transform by hand and re-targeting five
repaint call sites; a mistake shows up as stale ink or a silently invisible preview.

Read `.agents/guides/unit-conversion.md` and `.agents/guides/zoom.md` before starting. Never
introduce a raw numeric literal — every number must be a named constant.

### Tasks

1. **Add the headroom constants to** `src/main/java/songscribe/layout/LineSpacing.java`.
   This class already holds `MIN_INTER_LINE_GAP_SS`, `LYRICS_ROW_MARGIN_SS` and
   `MIN_LINE_HEIGHT_SS`. Add:

   - `public static final double PREVIEW_REPAINT_MARGIN_SS = 1.5;` — Javadoc: slack added to
     the preview element's **dirty rectangle** only, covering glyph ink that extends beyond
     the notehead *centre* (accidental half-height is the tallest contributor). Over-reporting
     a dirty region is free; under-reporting leaves stale ink.

   This is the **only** new constant. The reachable preview range above the staff top is
   exactly `Staff.MIN_ABOVE_STAFF_SS` and below the staff bottom exactly
   `Staff.MIN_BELOW_STAFF_SS` — `Staff.java:47,55` derives those from
   `Staff.MIN_STAFF_POSITION_SP` / `MAX_STAFF_POSITION_SP`, which are precisely the bounds
   `PreviewElementManager.isValidStaffPosition` (`PreviewElementManager.java:1032`) accepts.
   Use those two `Staff` constants directly wherever the headroom band is needed. Do **not**
   pad the band with `PREVIEW_REPAINT_MARGIN_SS`: a cursor in the padding maps to an invalid
   staff position, so padding only steals events from the neighbouring line in order to clear
   the preview.

   Do **not** feed any of this into `LayoutResult`'s content extents or into
   `StaffLinesLayout` — it must not affect inter-line spacing or component bounds.

2. **Extract the invariants build out of** `LineRenderer.render`
   (`src/main/java/songscribe/ui/component/score/LineRenderer.java:118-145`). The
   `LineInvariants.builder(...)...build()` chain there is currently inline. Move it verbatim
   into a new package-private `LineInvariants buildInvariants()` on `LineRenderer` (it reads
   only `lc`, so it needs no parameters, and it must keep the existing
   `layoutResult == null` → `throw RuntimeError.exit(...)` guard at lines 125-127). Have
   `render` call it. No behaviour change in this task.

3. **Move preview painting out of the line's paint pass.** In `LineRenderer.render`, delete
   the `renderPreviewElement(g2, invariants, lineFrame);` call (currently the last statement
   of the render order, `LineRenderer.java:167`). Add a package-private
   `void renderPreviewOverlay(Graphics2D g2)` that does what `render` did for that one step:
   builds invariants via `buildInvariants()`, obtains the line frame via
   `lc.gracePreviewLineFrame()`, calls `NoteGeometry.initializeAccidentalWidths()`, then calls
   the existing `renderPreviewElement(g2, invariants, lineFrame)` unchanged. `g2` arrives
   already scaled to staff spaces (task 4 sets that up), matching what `renderPreviewElement`
   assumed inside `render`. Guard on `lc.getLayoutResult() == null` by returning early rather
   than throwing — the overlay runs on every `StaffPanel` paint, including while a line is in
   the issue-#449 `lineDoesNotFit` state.

4. **Paint the overlay from** `src/main/java/songscribe/ui/component/score/StaffPanel.java`.
   Override `protected void paintChildren(Graphics g)`: call
   `super.paintChildren(g)` first, then paint the preview overlay on top. The overlay body:

   - Get the active preview line with `PreviewElementManager.getCurrentInsertionLine()`
     (`PreviewElementManager.java:210`). Return if it is `null`, or if
     `SwingUtilities.isDescendingFrom(previewLine, this)` is false (a stale reference to a
     line component from a previous `rebuildLayout()`).
   - Copy the graphics (`g.create()`) and dispose it in a `finally`, so the transform and
     clip changes below cannot leak into anything painted after.
   - Apply the rendering hints the line components get, via
     `GraphicUtils.setRenderingHints(g2)` — `ScoreComponent.paintComponent`
     (`ScoreComponent.java:256`) does this for the normal paint pass, and the overlay bypasses
     that path entirely.
   - Translate to the line's origin in this panel's coordinates:
     `var origin = SwingUtilities.convertPoint(previewLine, 0, 0, this); g2.translate(origin.x, origin.y);`
   - Apply the same staff-space transform `LineComponent.render` uses
     (`LineComponent.java:504`): `var scale = ScaleContext.getPixelsPerStaffSpace() * viewScale().factor(); g2.scale(scale, scale);`
     `StaffPanel.viewScale()` already exists and is package-private.
   - Call `previewLine.renderPreviewOverlay(g2)` — add that as a package-private method on
     `LineComponent` that delegates to its `lineRenderer` field (the same pattern
     `LineComponent.render` uses to reach `lineRenderer.render(g2)`).

   Note in a comment *why* this is painted by the parent rather than by the line: the preview
   element may sit up to `Staff.MIN_ABOVE_STAFF_SS` above and `Staff.MIN_BELOW_STAFF_SS` below
   the line's own content-hugging bounds, and Swing clips children to their bounds.

5. **Re-target the preview repaints so overlay ink is actually cleared.** Every repaint in
   `src/main/java/songscribe/ui/component/score/PreviewElementManager.java` currently calls
   `LineComponent.repaint()` — lines 186, 655, 668, 691 and 748. `JComponent.repaint(x,y,w,h)`
   clips the dirty region to the component's own bounds, so those calls can no longer clear
   preview ink drawn in the headroom. Add a package-private
   `void repaintWithPreviewHeadroom()` to `LineComponent` that:

   - Resolves the enclosing `StaffPanel` by walking `getParent()` (the hierarchy is
     `StaffPanel` → `LinePanel` → `LineComponent`; see the diagram at `ScoreView.java:106`).
     If there is no `StaffPanel` ancestor — the detached case exercised by unit tests — fall
     back to plain `repaint()` and return.
   - Builds this component's bounds in `StaffPanel` coordinates with
     `SwingUtilities.convertRectangle(this, new Rectangle(0, 0, getWidth(), getHeight()), staffPanel)`,
     grows it by `Staff.MIN_ABOVE_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS` on top and
     `Staff.MIN_BELOW_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS` on the bottom —
     converted with `getViewScale().toViewPx(...)` and rounded **up**, since these are sizes —
     and calls `staffPanel.repaint(rect)`.

   Replace all five `repaint()` call sites in `PreviewElementManager` with it.

5. Run `./scripts/compile.sh` and confirm **SUCCESS**. Do not run the app — Phase 3 owns
   visual verification, and Phase 2 must land first.

---

## ✅ Phase 2: Input Reachability

**Status:** Complete
**BlockedBy:** —
**Recommended model/effort:** Opus 4.8, high effort — the hit-test geometry and the
hook placement in the existing input handler are both easy to get subtly wrong.

### Revision (2026-07-20): forward from `ScoreView`, not from `StaffPanel`

This phase originally added `MouseListener` / `MouseMotionListener` to `StaffPanel` and
re-dispatched anything it did not consume with
`getParent().dispatchEvent(SwingUtilities.convertMouseEvent(this, e, getParent()))`.

**That recipe does not work.** `StaffPanel`'s parent is `MainPanel`, which registers no mouse
listeners. `Component.dispatchEvent` on a lightweight with no listeners drops the event —
AWT does not bubble to an ancestor. Swing's walk-up-to-find-a-target happens only inside
`LightweightDispatcher`, when it picks a target from the *native* event; it does not apply to
a manual `dispatchEvent`. Every unconsumed event would have died at `MainPanel` instead of
reaching `ScoreView`, breaking exactly the fall-through the re-dispatch was meant to preserve.

The fix is to not intercept at all. `ScoreView` already owns the listeners
(`ScoreView.java:290-291`) and already receives every event that lands in the gaps between
lines, so the headroom hit-test goes there: if the point is in a line's headroom band, forward
to that line; otherwise fall through to the existing handler body, unchanged. Nothing is
intercepted, so nothing needs re-dispatching.

**Painting stays in `StaffPanel`** (Phase 1, already landed). It must not move to `ScoreView`:
`ScoreView`'s children are `mainPanel` plus free-floating overlays added via
`ScoreView.addOverlay` (`ScoreView.java:1467`) — the `LyricEditor` among them — so an overlay
painted after `super.paintChildren` there would draw *on top of* the lyric editor. Painting
from `StaffPanel` keeps the preview correctly beneath ScoreView-level overlays.

### Tasks

1. **Add the headroom hit-test to** `src/main/java/songscribe/ui/component/score/StaffPanel.java`.
   Write a `@Nullable LineComponent lineForHeadroomPoint(int yPx)` that (public — `ScoreView` is in the parent package)
   returns the line whose headroom band contains `yPx` (a Y in `StaffPanel` coordinates), or
   `null`:

   - Iterate `linePanels`, taking each `LinePanel.getLineComponent()`.
   - If `yPx` falls inside a line's own bounds (converted to `StaffPanel` coordinates with
     `SwingUtilities.convertPoint`), return `null` immediately rather than merely skipping
     that line: the event reaches that line directly, so it must not be forwarded to a
     *neighbour* whose headroom band happens to reach the same Y.
   - A line's headroom band is its staff top minus `Staff.MIN_ABOVE_STAFF_SS` down to its
     staff bottom plus `Staff.MIN_BELOW_STAFF_SS` — exactly the range
     `PreviewElementManager.isValidStaffPosition` accepts, with **no** ink margin added, since
     a cursor beyond that range maps to an invalid staff position. Derive staff top
     and bottom from the line's `LayoutResult` (`staffTopYSsInLine()` /
     `staffBottomYSsInLine()`, both in the line's local frame), offset by the line's origin in
     `StaffPanel` coordinates, and converted with `viewScale().toViewPx(...)`. Skip lines
     whose `getLayoutResult()` is `null`.
   - When two adjacent lines' bands both contain `yPx` — possible when
     `LineSpacing.MIN_INTER_LINE_GAP_SS` (2.0) is smaller than the sum of the two headrooms,
     which is the normal case — return the one whose staff midline is **nearer** to `yPx`.

2. **Forward events in the headroom band, from `ScoreView`.** No listeners are added anywhere.

   - Add `boolean forwardHeadroomEvent(MouseEvent e)` to
     `src/main/java/songscribe/ui/component/InputHandlerCallback.java`, implemented by
     `ScoreView`. It converts the point into `StaffPanel` coordinates
     (`mainPanel.getStaffPanel()`, `SwingUtilities.convertPoint`), calls
     `lineForHeadroomPoint`, and returns `false` when there is no target so the caller falls
     through to its existing behaviour.
   - With a target, convert with `SwingUtilities.convertMouseEvent(this, e, target)` and call
     the matching method on `target` directly, switching on `e.getID()` (`LineComponent`
     implements `MouseMotionListener` and `MouseListener` on itself, `LineComponent.java:58`)
     — do **not** call `target.dispatchEvent`, which would re-run Swing's own hit test and
     drop the event as out-of-bounds. Return `true`.
   - In `src/main/java/songscribe/ui/component/ScoreInputHandler.java`, begin `mouseMoved`,
     `mousePressed`, `mouseReleased` and `mouseClicked` with
     `if (callback.forwardHeadroomEvent(e)) { return; }`, ahead of each method's existing
     body so a headroom click is not swallowed by the button/popup guards.
   - Do **not** forward `mouseDragged`: the press-grab belongs to whichever component received
     the press, so a drag begun in the headroom keeps going to `ScoreView` — the same as today,
     since there is no note under the cursor to drag.
   - In `ScoreInputHandler.mouseExited`, also call `LineComponent.clearPreviewElement()`. A
     preview established through a forwarded event never entered the line, so
     `LineComponent.mouseExited` will not fire to clear it when the cursor leaves the score.

3. **Verify the preview reaches the range extremes.** `LineComponent.mouseMoved` funnels into
   `PreviewElementManager.trackMouse` → `calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs())`
   (`PreviewElementManager.java:560`). Because the forwarded event's Y is converted into the
   **target line's** coordinate space, it is legitimately negative when the cursor is above the
   line, and `calculateStaffPositionFromMouse` must produce staff positions down to
   `Staff.MIN_STAFF_POSITION_SP` (−10) from those negative values. Read that method
   (`PreviewElementManager.java:1010`) and confirm it does no clamping to zero or to the
   component's bounds. If it does, remove the clamp — `isValidStaffPosition`
   (`PreviewElementManager.java:1032`) is the intended and sufficient range guard. Report what
   you found either way.

   **Finding: no clamping.** `calculateStaffPositionFromMouse` is
   `Staff.ssToSp(mouseYss - middleLineYSs)`, and `Staff.ssToSp` is
   `(int) Math.round(ss / STAFF_POSITION_OFFSET_SS)`. Negative values pass through the whole
   chain (`ViewScale.toDocPx`, `ScaleContext.pxToSs`, `Math.round`) untouched, so a forwarded
   event above the line yields negative staff positions down to `MIN_STAFF_POSITION_SP`.
   `isValidStaffPosition` is the only range guard. No change was needed.

4. **File the follow-up GitHub issue.** Use `gh issue create` on repo
   `vasudeva-server/SongScribe`. Title: `Draw the paste insertion point in the score overlay`.
   Body: the clipboard work on the `65-clipboard` branch draws a paste insertion point in the
   score; like the hover preview element it can sit outside a `LineComponent`'s content-hugging
   bounds and will be clipped by Swing. It must be painted through the same overlay this work
   introduces — `StaffPanel.paintChildren`'s overlay pass, alongside
   `LineComponent.renderPreviewOverlay` — and its repaints must use
   `LineComponent.repaintWithPreviewHeadroom()` rather than `LineComponent.repaint()`.
   Reference issue #591 as the origin. Report the issue number in your final report.

   **Filed as [#623](https://github.com/vasudeva-server/SongScribe/issues/623).**

5. Run `./scripts/compile.sh` and confirm **SUCCESS**.

---

## ✅ Phase 3: Manual UI Verification

**Status:** Complete
**BlockedBy:** —
**Recommended model/effort:** No model — the user performs this.

Run this in the **same session** as Phase 6 of
[per-line-heights-pairwise-spacing.md](./per-line-heights-pairwise-spacing.md) — both are
user-driven passes over the same score, and that phase's task 4 is the defect this plan fixes.

This gates Phase 4: the expected numbers are a design judgement, not a derivation, and
`LineSpacing.PREVIEW_REPAINT_MARGIN_SS` in particular is a first guess.

### Tasks

1. Ask the user for permission, then run `./scripts/run.sh`. Never run it without asking.

2. Have the user open a song and, on a line with **no** high or low content, hover to place a
   preview element at the extreme staff positions above and below the staff. Confirm: (a) the
   glyph is not clipped at any position, including its accidental; (b) the extreme positions
   are reachable at all — the preview keeps responding as the cursor moves into the gap above
   and below the line. If the glyph is clipped only at the very top or bottom, the fix is to
   raise `LineSpacing.PREVIEW_REPAINT_MARGIN_SS`, not to touch the content extents or
   `StaffLinesLayout`.

3. Have the user confirm no stale ink: move the cursor quickly between lines and out of the
   score entirely, and check that no preview glyph is left behind in the gaps.

4. Have the user confirm the #591 spacing fix did not regress: inter-line spacing must be
   unchanged by this work, since neither line bounds nor `StaffLinesLayout` were touched.

5. Have the user confirm nothing that previously worked in the gaps broke — in particular
   clicking in the space between two lines to deselect, and any drag started on a line and
   continued into the gap.

6. Have the user confirm all of the above at a zoomed-in and a zoomed-out step, and on a
   single-line song.

7. Record the user's verdict and any tuning they ask for in this section. If tuning is needed,
   apply it and re-verify before Phase 4 starts.

### Outcome

**Verified visually by the user (2026-07-21).** The preview element renders unclipped and stays
reachable across the full staff-position range, and inter-line spacing did not regress. No
tuning of `PREVIEW_REPAINT_MARGIN_SS` was requested.

---

## ✅ Phase 4: Tests

**Status:** Complete — re-scoped: task 2's subject was deleted rather than tested
**BlockedBy:** —
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical test writing against
behaviour the user has already signed off on.

**Do not start this phase until Phase 7 of
[per-line-heights-pairwise-spacing.md](./per-line-heights-pairwise-spacing.md) is complete.**
The unit test sources do not currently compile — the #591 work deleted `SongLayoutMetrics` and
changed the `LyricRenderMetrics` constructor arity, and that plan's Phase 7 is what migrates
the test sources. Task 4 below cannot report green before then, and its failures would be
unrelated to this plan.

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first. No raw
numeric literals — every expected value must be built from the named constants.

### ⚠️ Finding (2026-07-21): `lineForHeadroomPoint` is now unreachable

`StaffPanel.lineForHeadroomPoint` **always returns `null`**, so the whole Phase 2 forwarding
path — `ScoreView.forwardHeadroomEvent`, the four `ScoreInputHandler` hooks — is dead code.
Confirmed empirically by probing every Y across a laid-out three-line panel: zero hits.

The cause is the **painted-bounds floor** added to `LayoutResult` / `StaffLinesLayout` after
this plan was written (commit `0cc4fad5`, "line bounds floor"). It makes a line's bounds
strictly contain its own headroom band, so the method's own "inside a line's bounds → return
`null`" guard fires before any band test can match. The containment is unconditional, not a
property of the fixture:

```
band top    = midline − MIN_ABOVE_MIDLINE_SS
bounds top  = midline − max(aboveMidlineSs, MIN_ABOVE_MIDLINE_SS)  ≤  band top
band bottom = midline + MIN_BELOW_MIDLINE_SS
bounds btm  = midline + max(belowMidlineSs, MIN_BELOW_MIDLINE_SS)  ≥  band bottom
```

The floor solves reachability directly — the component is simply big enough for the events to
land in it — which is why the user's Phase 3 verification passed despite the forwarding never
firing.

**This does not invalidate Phase 1.** Overlay *painting* still earns its keep at the extremes:
glyph ink (an accidental in particular) can reach past `MIN_ABOVE_STAFF_SS` beyond the notehead
centre, which is what `PREVIEW_REPAINT_MARGIN_SS` exists for, and that ink is still outside the
bounds.

**Resolved: the forwarding path was deleted** (2026-07-21), and task 2 with it. Removed
`StaffPanel.lineForHeadroomPoint`, `InputHandlerCallback.forwardHeadroomEvent` and its
`ScoreView` implementation, and the four `ScoreInputHandler` hooks. Reachability is now the
bounds floor's job alone.

`ScoreInputHandler.mouseExited`'s `LineComponent.clearPreviewElement()` call was **kept**: it
was introduced for the forwarding case, but it is a genuine safety net for the cursor leaving
without the owning line's exit being delivered, and it has a test. Only its (now false)
justifying comment changed.

#### Honest note on when this died
The forwarding was **not** dead when Phase 2 landed. It was killed by the lyric-baseline fix in
commit `4efee872`, which repointed `staffTopYSsInLine`/`staffBottomYSsInLine` at the painted
midline. Before that they answered from the measured `contentAboveStaffSs`, which on a short
line sits *above* the component's floored top — so the band genuinely fell outside the bounds
and the forwarding genuinely fired. Aligning the staff helpers with the painted frame moved the
band inside the bounds and made the path unreachable in the same stroke.

### Tasks

1. **Update existing preview-render tests.** `renderPreviewElement` is no longer reached from
   `LineRenderer.render`. Find the tests that assert it is (start with
   `src/test/java/songscribe/ui/component/score/LineRendererTest.java`) and repoint them at
   the new `LineRenderer.renderPreviewOverlay(Graphics2D)` entry point.

2. **Add** `StaffPanel.lineForHeadroomPoint` **tests** in
   `src/test/java/songscribe/ui/component/score/` covering: a Y inside a line's own bounds
   returns `null` (the line gets the event directly); a Y in the band above a line returns
   that line; a Y in the band below returns that line; a Y in a gap where both neighbours'
   bands overlap returns the line with the nearer staff midline; a line with a `null`
   `LayoutResult` is skipped; and a Y outside every band returns `null`.

3. **Add a headroom-repaint test** asserting `LineComponent.repaintWithPreviewHeadroom()`
   produces a dirty rectangle in `StaffPanel` coordinates that extends
   `Staff.MIN_ABOVE_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS` above and
   `Staff.MIN_BELOW_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS`
   below the component's bounds, and that a detached `LineComponent` (no `StaffPanel`
   ancestor) falls back to plain `repaint()` without throwing.

4. Run `./scripts/compile.sh`, then `./scripts/test.sh unit`. Both must report SUCCESS /
   green before this phase is done.

### Outcome

Done. `./scripts/compile.sh` SUCCESS, `./scripts/test.sh unit` green at 5469 passed / 1 skipped.

- **Task 1 — no-op.** No test asserted that `LineRenderer.render` reaches `renderPreviewElement`;
  `LineRendererTest`'s preview tests all cover `renderWithPreviewShiftIfNeeded`, an unrelated
  method that still lives on the normal render path. Nothing to repoint.
- **Task 2 — dropped.** Its subject was deleted; see the finding above.
- **Task 3 — done.** `src/test/java/songscribe/ui/component/score/LineComponentPreviewHeadroomTest.java`,
  two tests: the enlarged dirty rectangle, and the detached-component fallback. Verified the
  first can fail by temporarily shrinking the dirty rect back to the component's own bounds.



1. `./scripts/compile.sh` reports SUCCESS.
2. `./scripts/test.sh unit` is green.
3. The preview element renders unclipped and stays reachable across the full
   `Staff.MIN_STAFF_POSITION_SP`..`Staff.MAX_STAFF_POSITION_SP` range on a line with no
   content, confirmed by the user in Phase 3. **Reachability is delivered by the line-bounds
   floor, not by event forwarding** — see the Phase 4 finding.
4. Inter-line spacing is unchanged from before this work — `StaffLinesLayout`,
   `LayoutResult`'s content extents, and `LineComponent.getPreferredSize` are untouched.
5. `rg -n "renderPreviewElement" src/main/` shows it called only from
   `LineRenderer.renderPreviewOverlay`.
