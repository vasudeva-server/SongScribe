# Check Review — LineOverlayPainter extraction

Scope: working-tree diff (Mode A), plus two untracked new files.

**Production files**

- `src/main/java/songscribe/ui/component/ScoreView.java` (modified)
- `src/main/java/songscribe/ui/component/score/LineComponent.java` (modified)
- `src/main/java/songscribe/ui/component/score/LineRenderer.java` (modified)
- `src/main/java/songscribe/ui/component/score/PreviewElementManager.java` (modified)
- `src/main/java/songscribe/ui/edit/PasteModeManager.java` (modified)
- `src/main/java/songscribe/ui/component/score/LineOverlayPainter.java` (new)

**Test files**

- `src/test/java/songscribe/ui/component/score/LineOverlayPainterPasteOverlayTest.java` (new)
- `src/test/java/songscribe/ui/component/score/LineComponentPreviewHeadroomTest.java` (modified)
- `src/test/java/songscribe/ui/edit/PasteModeManagerTest.java` (modified)

Reviewed by five parallel agents (Reuse, Quality, Efficiency, Test Correctness, Test
Usefulness). Every substantive claim below was re-validated against the source before
being reported; verdicts and caveats are the orchestrator's, not the agents'.

---

## Status After the Overlay-Components Re-architecture

`plans/overlay-components.md` replaces the drawing-based overlay mechanism with real Swing
components, deleting `LineOverlayPainter`, `LineComponent.repaintWithOverlayHeadroom`,
`LineSpacing.PREVIEW_REPAINT_MARGIN_SS`, `PreviewElementManager.paintOverlay`, and three
test classes. That changes what is worth acting on here. **Read this section before fixing
anything below.**

### Still worth fixing now

These touch code that survives the re-architecture, and are cheap. Apply them before
committing the current working-tree change, which fixes a real user-visible bug (the paste
marker going unpainted for the whole of paste mode) and should land regardless of when the
re-architecture happens.

- **R1** — dead `!= lc` guard in `LineRenderer.renderInsertionPoint`. That method survives;
  the components call it.
- **Q2** — `renderInsertionPoint`'s Javadoc contradicts reality. Survives, still wrong.
- **Q3** — `PasteModeManager.getActiveInstance`'s "Used by `LineRenderer`" Javadoc. Survives;
  note the correct referent changes again after the re-architecture.
- **TC1** — `testInactivePasteModePaintsNothing` asserts only "does not throw". The test class
  is deleted in the re-architecture, but until then it is a test that cannot fail, and the fix
  is a two-line change.
- **TU1** — `clearTarget()`'s repaint is unverified. Same reasoning.

### Now moot — do not spend effort

The code or test each of these describes is deleted by the re-architecture.

- **Q1** — `PreviewElementManager.paintOverlay` visibility. The method is deleted.
- **E1** — headroom crossing the inter-line gap. `repaintWithOverlayHeadroom` and
  `PREVIEW_REPAINT_MARGIN_SS` are deleted; this is the defect that motivated the whole plan.
- **E2** — duplicate `buildInvariants()`. The overlay render path is replaced.
- **E3** — overlay work not clip-gated. Solved structurally: Swing does not paint a component
  whose bounds miss the clip, so the early-out becomes free rather than hand-written.
- **TU2** — duplication between the two overlay test classes. Both are deleted.
- **TU5** — `LineComponent` delegation untested. The delegation ceases to exist.
- **TU6** — `LineComponentPreviewHeadroomTest` naming drift. The class is deleted.

### Carried into the new plan

Still real, but addressed there rather than here — do not fix twice.

- **TU3** — marker geometry untested → `plans/overlay-components.md` phase 9 task 1, which
  also covers the new ink-bounds composition.
- **TU4** — null-`layoutResult` guard untested → phase 9 task 3, where the guard becomes a
  visibility gate on each component.
- **TC2** — exact call-count coupling in `PasteModeManagerTest`. That file survives, but its
  `repaintWithOverlayHeadroom` verifications must be rewritten when the method is deleted;
  phase 9 task 4 specifies asserting observable component state instead.

---

## Reuse

### R1. Dead target-line guard in `LineRenderer.renderInsertionPoint`

`LineRenderer.java:780` — **confirmed, high confidence**

```java
if (pasteModeManager == null || pasteModeManager.getTargetLineComponent() != lc) {
```

Before the diff this ran from every line's own `render()`, so it had to filter down to
the target line. Now the only caller path is
`LineOverlayPainter.paintPasteInsertionPoint` → `LineComponent.renderInsertionPointOverlay`
→ here, and that call site has *already* resolved the target line and null-checked the
manager. The `!= lc` half can never be true.

The cost is not the wasted comparison but the duplicated invariant: "is this the paste
target" now has two sources of truth in two files that must be kept in sync by hand.

Found independently by the Reuse and Quality agents.

**Suggested fix:** drop the `!= lc` half. Keep the `getActiveInstance()` fetch — the
method still needs `getTargetIndex()` — and note in a comment that the line match is now
guaranteed by the caller.

### R2. Repeated zoomed-scale formula

`LineOverlayPainter.java:110` — **informational, low confidence**

`ScaleContext.getPixelsPerStaffSpace() * line.getViewScale().factor()` is also at
`LineComponent.java:522` and `LineInvariants.java:183`, with `* factor()` variants in
`MainPanel`, `TextPanel`, and several more places in `LineComponent`.

Pre-existing codebase-wide duplication; this code was moved verbatim out of
`PreviewElementManager.paintOverlay`. `LineInvariants.getViewPixelsPerStaffSpace()` is
the closest named accessor but is not reachable from `LineOverlayPainter`, which holds
only a `LineComponent`.

**No change proposed** — noting for awareness.

### Checked and clean

- The `g.create()` / `setRenderingHints` / try-finally-dispose shape in `paintOnLine`
  matches the established codebase idiom (`ScoreComponent`, `SplashWindow`, `PasteOverlay`,
  and many renderers). `GraphicsState.save` is a different tool and not a fit here.
- The `isDescendingFrom` staleness guard and the `getAncestorOfClass(ScoreView.class, …)`
  lookup are each used exactly once — no consolidation target exists.
- The diff's own consolidation is good reuse: extracting the stale-line guard and
  transform setup into `paintOnLine` and sharing it between both overlays via
  `BiConsumer<LineComponent, Graphics2D>` avoided a second copy of the transform code.

---

## Quality

### Q1. `PreviewElementManager.paintOverlay` should drop to package-private

`PreviewElementManager.java:238` — **confirmed, high confidence**

Verified the only remaining caller is `LineOverlayPainter`, same package
(`songscribe.ui.component.score`); `ScoreView` now goes through `paintOverlays` instead.
`public` was load-bearing only while `ScoreView` reached across packages.
`PreviewElementManagerPaintOverlayTest` is same-package, so narrowing does not break it.

`LineComponent.renderPreviewOverlay` and `renderInsertionPointOverlay` are already
package-private — this should match them.

### Q2. Stale Javadoc on `renderInsertionPoint`

`LineRenderer.java:768` — **confirmed, high confidence**

> "Topmost — drawn last, still inside the single Ss transform `LineComponent.render`
> establishes."

No longer true. The method now runs in the page-level overlay pass, which builds its own
translate/scale in `LineOverlayPainter.paintOnLine` against `ScoreView` page coordinates.
The diff changed this method's entire calling context without touching its doc.

### Q3. Stale Javadoc on `getActiveInstance`

`PasteModeManager.java:125` — **confirmed, low confidence**

Says "Used by `LineRenderer` to determine whether a given line is the current insertion
target." The target lookup now happens in `LineOverlayPainter`.

### Q4. `repaintWithOverlayHeadroom` widened to `public` — justified, no finding

Verified: `PasteModeManager` lives in `songscribe.ui.edit`, a different package, so
package-private would no longer reach it. The widening is for a real production reason,
not test convenience.

### Q5. No unpainted path left by removing the `render()` call — no finding

Verified `LineComponent.render(Graphics2D)` has exactly one caller (the Swing
`paintComponent` pipeline), which only runs as a child of `ScoreView`, and
`ScoreView.paintChildren` always runs the overlay pass. No print/export path bypasses it.

### Q6. Style conventions — clean

No magic numbers, no `var` violations, braces and control-structure blank lines follow
`.agents/rules/java.md` in the new and changed code. Compiles clean.

---

## Efficiency

### E1. Headroom repaint spills across the inter-line gap

`LineComponent.java:940-947`, driven from `PasteModeManager.java:328,367,370`
— **math confirmed; severity overstated by the agent, medium**

Headroom is `MIN_ABOVE_STAFF_SS (3.0) + PREVIEW_REPAINT_MARGIN_SS (1.5)` = **4.5 Ss**
above and `MIN_BELOW_STAFF_SS (4.0) + 1.5` = **5.5 Ss** below, against a default
inter-line gap of `DEFAULT_INTER_LINE_GAP_SS = 4.0 Ss` (floor 2.0). Both exceed the gap,
so the enlarged dirty rect reaches into the neighbouring line's bounds. Because
`ScoreView.isOptimizedDrawingEnabled()` returns `false`, that neighbour gets a full
`render()` — the entire line pipeline — even though nothing on it changed. During
paste-mode mouse-move this is up to 3 line renders per target change instead of 1.

**Caveat (orchestrator):** the hover preview already paid exactly this cost through the
same method before the diff, and the marker genuinely *needs* headroom now that it paints
outside line bounds — a plain `repaint()` would leave stale ink, which is the bug this
change fixes. So this is not a regression introduced here.

The only open question is whether `PREVIEW_REPAINT_MARGIN_SS` belongs in the paste path
at all. Its own doc says it covers "glyph ink that extends beyond the notehead centre"
for the preview element's accidentals; the paste marker is a plain vertical line
(`GraphicUtils.drawRoundedLine`) with no glyph overflow. Dropping it saves 1.5 Ss per
side but requires parameterizing a method currently shared by both overlays.

### E2. `buildInvariants()` runs a second time for the target line

`LineRenderer.java:228` — **confirmed, low confidence / low magnitude**

`render()` builds `invariants` once and used to hand it to `renderInsertionPoint`
directly. The overlay pass runs at a different point in the paint cycle with no live
`invariants` to borrow, so it must rebuild. This exactly mirrors what `renderPreviewOverlay`
has always done. `buildInvariants()` is mostly getter forwarding, and both calls are
synchronous on the EDT so no state can drift between them.

**Recommendation: leave it.** The duplication is the price of decoupling the overlay pass
from the line's own paint pass.

### E3. Overlay work is not clip-gated

`LineOverlayPainter.java:88` — **questionable, medium confidence**

`paintOnLine` performs `g.create()`, `convertPoint`, then `buildInvariants()` and
`calculateInsertionXSs()` before Java2D discards the draw as out-of-clip. A
bounds-versus-`g.getClip()` early-out would skip that work.

**Caveat (orchestrator):** this is speculative optimization on a path whose per-call cost
is modest — `calculateInsertionXSs` is called with `betweenElementsOnly=true`, which skips
the element-head scan loop — and it adds code to a method that is currently very clean.
The pattern also predates this diff on the preview side.

### E4/E5. Checked and clean — no findings

- `NoteGeometry.initializeAccidentalWidths()` is a cached idempotent static initializer,
  and the new `renderInsertionPointOverlay` correctly does not call it at all (the marker
  needs no glyph metrics).
- `isOptimizedDrawingEnabled()` returning `false` is pre-existing and is required by the
  page-level overlay design. Its Javadoc changed in this diff; its body did not.
- `LineOverlayPainter` is stateless — private constructor, static methods only, no fields.
  No new listeners, caches, or unbounded collections. No leak surface added.
- Per-paint allocations (`g.create()`, `convertPoint`, the temporary `ElementColumn`) are
  small and short-lived; not worth flagging in isolation.

---

## Test Quality — Correctness

### TC1. `testInactivePasteModePaintsNothing` asserts only "does not throw"

`LineOverlayPainterPasteOverlayTest.java:186` — **confirmed, high confidence.
Found independently by both test agents.**

```java
assertThatCode(() -> LineOverlayPainter.paintOverlays(graphics, host))
    .as("an idle repaint must not fail for want of a paste target")
    .doesNotThrowAnyException();
```

The name promises "paints nothing"; the body only proves "did not crash". Unlike the
sibling `testNoTargetLinePaintsNoMarker` ten lines above, it hosts a bare
`new LineComponent()` rather than a `TransformCapturingLine`, so there is no signal
either way.

This is a real hole, not a style nit. The no-arg `LineComponent` constructor leaves
`layoutResult` null, so `LineRenderer.renderInsertionPointOverlay` would hit its
null-layout guard and silently no-op rather than throw. Concretely: if the
`pasteModeManager == null` guard at `LineOverlayPainter.java:70` were ever weakened so it
fell through and painted onto the host's own child line, **this test would still pass** —
the no-throw assertion and the accidental no-op conspire to hide the regression.

**Suggested fix:** use `TransformCapturingLine` and assert `capturedTransform` is null,
matching the pattern already used by the sibling test.

### TC2. Exact call-count verification couples to implementation

`PasteModeManagerTest.java:315-317` — **medium confidence, fragility note**

```java
verify(lineComponent, times(REPAINT_COUNT_TRACKED_THEN_ABANDONED))
    .repaintWithOverlayHeadroom();
```

The counts are correct today (traced through `updateTarget`), and the test also asserts
real state via `getTargetLineComponent()`/`getTargetIndex()`, so it is not assertion-free.
But verifying *how many times* a collaborator method fires would break on a
correctness-preserving refactor — e.g. batching the two repaints over a computed dirty-line
set. Mock verification of Swing side effects is a sanctioned pattern in the project guide;
the exact-count coupling is the part worth noting.

**Not a must-fix.**

### TC3/TC4. Checked and clean — no findings

- `PreviewElementManager.setCurrentPreviewLine` is **not** a new test backdoor. Verified
  untouched by this diff and already used by `PreviewElementManagerPaintOverlayTest`,
  `PreviewElementManagerTestBase`, and `LineRendererTest`. It is an established seam.
- Static-state hygiene is correct in both directions: the new class's `@AfterEach`
  resets `currentPreviewLine`, and `PasteModeManagerTest.tearDown()` resets the
  `PasteModeManager` static instance. No cross-class leakage.
- The bulk of the changes to both existing test files are mechanical renames tracking the
  production rename; no behavior change introduced.

---

## Test Quality — Usefulness

### TU1. `clearTarget()`'s headroom repaint is unverified

`PasteModeManager.java:322-330`, tests at `PasteModeManagerTest.java:278,289`
— **high confidence**

`testMouseLeftOfHeaderClearsTarget` and `testMouseRightOfStaffClearsTarget` exercise this
path but assert only `getTargetLineComponent()` / `getTargetIndex()` — never
`verify(lineComponent).repaintWithOverlayHeadroom()`.

This is one of the exact three call sites the diff changed from plain `repaint()`. If it
regressed, the abandoned marker would leave stale ink outside the line's clipped bounds —
precisely the bug class this change exists to fix — and nothing would catch it. Only the
cross-line case in `testTargetTracksAcrossLineComponentChange` verifies repaint invocation;
the single-line clear path does not.

### TU2. Substantial duplication between the two overlay test classes

`LineOverlayPainterPasteOverlayTest` vs. `PreviewElementManagerPaintOverlayTest`
— **medium confidence**

Both re-derive and assert the identical `paintOnLine` transform (`translateX`, `translateY`,
`scaleX`, `scaleY`), with byte-for-byte duplicated constants — `HOST_WIDTH_PX`,
`HOST_HEIGHT_PX`, `LINE_X_PX`, `LINE_Y_PX`, `LINE_WIDTH_PX`, `LINE_HEIGHT_PX`,
`ZOOM_PERCENT`, `ZOOM_FACTOR`, `TRANSFORM_TOLERANCE` — and near-identical
`scratchGraphics()` / `hostContaining()` helpers.

Now that `paintOnLine` is shared production code, the transform math only needs proving
once. Each caller-specific test really only needs to confirm it passes the *right line*
and the *right callback*. As written, a future `paintOnLine` signature change means
updating two files identically.

The two tests do differ in which collaborator they wire up, so this is not pure
duplication — but the bulk of each one's assertions are.

### TU3. Marker geometry is entirely untested

`LineRenderer.java:777-810` — **high confidence, but pre-existing**

The `xSs` computation (`calculateInsertionXSs` plus `NOTE_HEAD_WIDTH_SS / 2`) and the
vertical span (`middleLineYSs` ± `MIN_STAFF_POSITION_SP`/`MAX_STAFF_POSITION_SP`) are real
computed geometry, not pass-through rendering. `TransformCapturingLine` overrides
`renderInsertionPointOverlay`, so nothing exercises any of it. A mutant swapping
`MIN_STAFF_POSITION_SP` and `MAX_STAFF_POSITION_SP`, or dropping the half-notehead offset,
would survive the whole suite.

The method body did not change in this diff, so this predates it.

### TU4. Null-`layoutResult` guard untested

`LineRenderer.java:224` — **medium confidence**

Nothing drives `renderInsertionPointOverlay` with `getLayoutResult() == null` to confirm
the early return during the issue-#449 `lineDoesNotFit` state. Mirrors an identical
pre-existing gap for `renderPreviewOverlay`'s twin guard at `LineRenderer.java:199`.

### TU5. Delegation from `LineComponent` to `LineRenderer` is never asserted

`LineComponent.java:915-917` — **medium confidence**

`TransformCapturingLine` overrides exactly this method, so the delegation is bypassed
rather than tested. No e2e test touches paste mode either. Combined with TU3 and TU4, the
entire rendering path below `paintOnLine`'s transform setup has zero coverage. The same
gap exists on the preview side, so it is a pattern rather than a new regression.

### TU6. Stale test class name and Javadoc

`LineComponentPreviewHeadroomTest` — **low confidence, three agents flagged it**

The production method was renamed `repaintWithPreviewHeadroom` → `repaintWithOverlayHeadroom`
and now serves both overlays. The class Javadoc's first line was updated; the class *name*,
the rest of its Javadoc framing (lines 47-55), and the method name
`testDirtyRectGrowsByThePreviewHeadroomOnBothSides` all still frame it purely around the
preview element. The mechanism under test is now caller-agnostic.

Suggested rename: `LineComponentOverlayHeadroomTest`.

### TU7. Checked and clean

- `testInsertionMarkerIsPaintedForThePasteTargetWithNoPreviewLine` is a genuine, valuable
  regression test for the bug named in the class Javadoc. It explicitly nulls the preview
  line and drives the marker purely through a mocked `PasteModeManager`, so a regression
  that re-couples the marker to `currentPreviewLine` would fail it.
- The stale-line guard in `paintOnLine` is already covered by
  `PreviewElementManagerPaintOverlayTest.testStaleLineOutsideTheHostIsNotPainted`. Since
  `paintOnLine` is shared generic code, one test is sufficient; a paste-specific duplicate
  would be redundant rather than missing.

---

## Suggested priority

Superseded by the **Status After the Overlay-Components Re-architecture** section above.
Retained for the reasoning behind each severity call; act on the status section, not this
table.

| # | Finding | Severity | Effort | Post-re-architecture |
|---|---------|----------|--------|----------------------|
| TC1 | `testInactivePasteModePaintsNothing` cannot fail | High — false confidence | Small | Fix now |
| TU1 | `clearTarget()` headroom repaint unverified | High | Small | Fix now |
| Q2 | `renderInsertionPoint` Javadoc contradicts reality | High | Trivial | Fix now |
| R1 | Dead `!= lc` guard, duplicated invariant | Medium-high | Small | Fix now |
| Q1 | `paintOverlay` should be package-private | Medium | Trivial | Moot — method deleted |
| Q3 | `getActiveInstance` Javadoc stale | Low | Trivial | Fix now |
| TU6 | `LineComponentPreviewHeadroomTest` naming drift | Low | Small | Moot — class deleted |
| TU2 | Duplication across the two overlay test classes | Medium | Medium | Moot — both deleted |
| E1 | `PREVIEW_REPAINT_MARGIN_SS` in the paste path | Medium | Medium | Moot — constant deleted |
| TU3/TU4 | Marker geometry and guards uncovered | Medium — pre-existing | Medium | Plan phase 9 |
| TU5 | Delegation uncovered | Medium — pre-existing | Medium | Moot — delegation removed |
| TC2 | Exact call-count coupling | Low — fragility note | n/a | Plan phase 9 |
| E2/E3 | `buildInvariants()` rebuild; no clip gate | Low | n/a | Moot — path replaced |

Nothing has been changed on disk. No fixes applied.
