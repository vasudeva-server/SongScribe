# Issue #680 — Hit-Test and Selection-Color Refactor

## Goal

Collapse two axes that currently grow linearly with every new selectable
`LineElement`:

1. `LineSelectionHandler.hitTest()` — an imperative cascade with one bespoke
   `if` block and `hitTest*` signature per selectable element — becomes a
   declarative list of `HitTester`s.
2. `LineComponent.SelectionProvider` — one `is*Selected` method per decoration
   kind — collapses to a single `isSelected(LineElement, int)`, and the
   duplicated `determine*Color` shape (only `EndingRenderer` has it today, byte
   for byte) is extracted to a shared `RenderingUtils` helper.

The long-term goal is every `LineElement` becoming selectable; hairpins
(#230) are the next one, not the only one — this refactor must land before
#230 (that spec's §8/§9 assume it's done), as its own issue, not folded into
the hairpin PR.

Must not regress: cascade order and outcomes stay identical for note heads,
slides, grace glissandi, endings, and staff lines; ending selection
highlighting stays pixel-identical. This is a behavior-preserving refactor —
existing tests are the correctness gate at every phase, not a deferred
manual-verification step.

Build with `./scripts/compile.sh` exactly (never `./gradlew`). Tests via
`./scripts/test.sh unit`. Use `jet_brains_*` tools for all Java exploration
and refactoring (`.agents/rules/serena.md`) — in particular `jet_brains_move`
for the `HitResult` package move and `jet_brains_rename` for
`isEndingSelected` → `isSelected`.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [songscribe.ui.hit package](#-phase-1-songscribeuihit-package) | ✅ Complete | — |
| 2 | [ElementHitTest adapter](#-phase-2-elementhittest-adapter) | ✅ Complete | — |
| 3 | [Rewrite the hit-test cascade](#-phase-3-rewrite-the-hit-test-cascade) | ✅ Complete | — |
| 4 | [Collapse SelectionProvider.isEndingSelected](#-phase-4-collapse-selectionproviderisendingselected) | ✅ Complete | — |
| 5 | [decorationSelectionColor helper](#-phase-5-decorationselectioncolor-helper) | ✅ Complete | — |

Dependency shape: Phase 4 has no blockers and can run alongside Phase 1.
Phase 3 is blocked by 2 **and** 4 (needs `ElementHitTest.hit` from 2; needs
`isSelected` to exist from 4, since it touches the same two lines in
`LineSelectionHandlerTest` that Phase 4's rename reaches). Phase 5 is blocked
by 3 **and** 4 (it touches `EndingRenderer.java`/`EndingRendererTest.java`,
which Phase 3 also touches, and calls `isSelected` from Phase 4).

```
Phase 1 ──► Phase 2 ──┐
                       ├──► Phase 3 ──► Phase 5
Phase 4 ───────────────┘
   (Phase 4 also feeds Phase 3 and Phase 5 directly)
```

## Package location

`HitTester`, `HitTestContext`, and `HitResult` live in a new
`songscribe.ui.hit` package, not hoisted into `songscribe.ui` (verified
grab-bag: `Mode`, `ViewScale`, `Appearance`, `MusicEditOperations` already live
there — the three hit types are cohesive and shouldn't join it).

---

## ✅ Phase 1: songscribe.ui.hit package

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 5, low effort — mechanical package move plus two new small types; existing tests gate correctness and shouldn't change behavior.

### Tasks
1. `jet_brains_move` `HitResult` from `songscribe.ui.component.score` to a new `songscribe.ui.hit` package. Make the `HitResult` interface and all six record variants (`ElementHead`, `Slide`, `Ending`, `GraceGlissando`, `StaffLine`, `Nothing`) `public` — package-private is invisible from `songscribe.ui.renderer`/`songscribe.ui.component.score`, and the renderers construct these records directly.
2. Rewrite `HitResult`'s Javadoc: delete the cascade-order sentence ("The cascade tests note heads first, then slides, then staff-line proximity" — already stale, it omits endings). Keep only a plain description ("Result of a hit test against selectable elements in a `LineComponent`."). Cascade order now lives declaratively in `LineSelectionHandler.hitTesters` (Phase 3) — don't restate it here, that's exactly the kind of prose that goes stale the next time a tester is added.
3. Add a `HitTestContext` record to `songscribe.ui.hit`:
   ```java
   public record HitTestContext(
       Point pointPx,                      // document pixels
       double xSs,
       double ySs,
       Line line,
       @Nullable LayoutResult layoutResult,
       double middleLineYSs
   ) {}
   ```
4. Add a `HitTester` functional interface to `songscribe.ui.hit`:
   ```java
   @FunctionalInterface
   public interface HitTester {
       @Nullable HitResult hitTest(HitTestContext context);
   }
   ```
5. Update the `HitResult` import in `LineSelectionHandler.java` to the new package.
6. `./scripts/compile.sh`; `./scripts/test.sh unit` — must stay green. No behavior change in this phase.

---

## ✅ Phase 2: ElementHitTest adapter

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 5, low effort — small additive method, no existing test touched.

`ElementHitTest.hitTestElement(LineComponent, Point)` has 3 other production
callers besides the hit-test cascade (`NoteDragHandler.handlePress`,
`LineComponent.editLyricOnDoubleClickedElement`,
`LineSelectionHandler.calculateLineSelectionFromDrag`) that only want a plain
element index — changing its signature would force all of them to build a
`HitTestContext` just to extract an `int`. Leave it untouched; add a new
wrapper for the cascade only.

### Tasks
1. Add `static @Nullable HitResult hit(LineComponent lc, HitTestContext context)` to `ElementHitTest`. Calls `hitTestElement(lc, context.pointPx())` and translates the result: `-1` → `null`, index → `HitResult.ElementHead(index)`.
2. Add test cases to `ElementHitTestTest` for the new wrapper: a hit returns `ElementHead(index)`, a miss returns `null`.
3. `./scripts/compile.sh`; `./scripts/test.sh unit`.

---

## ✅ Phase 3: Rewrite the hit-test cascade

**Status:** Complete  <br>
**BlockedBy:** 2, 4  <br>
**Recommended model/effort:** Opus 5, high effort — this is one compile-coupled atomic unit, not several independent changes. `SlideRenderer.hitTestSlide`/`EndingRenderer.hitTestEnding` each have exactly one production caller (`LineSelectionHandler`'s private adapters), so their signature change and the cascade rewrite that replaces those callers must land together — splitting them would leave a broken intermediate compile state that any concurrently-scheduled phase (even unrelated ones) would also fail against, since `./scripts/compile.sh` compiles the whole project. Do not split this phase.

Unlike Phases 1, 2, 4, and 5, `SlideRenderer.hitTestSlide` and
`EndingRenderer.hitTestEnding` **change signature in place** rather than
getting an additive wrapper (unlike `ElementHitTest` in Phase 2) — each has
exactly one production caller, so there's no other call site to preserve
compatibility with.

### Tasks
1. Change `SlideRenderer.hitTestSlide(double clickXSs, double clickYSs, Line line)` → `hitTest(HitTestContext context)`, returning `@Nullable HitResult`. Fold in the grace-glissando decision that today lives inline in `LineSelectionHandler.hitTest` (`:113-117`): a slide hit on a grace note's glissando returns `HitResult.GraceGlissando()`; any other slide hit returns `HitResult.Slide(index)`; no hit returns `null`.
2. Change `EndingRenderer.hitTestEnding(double, double, Line, @Nullable LayoutResult, double)` → `hitTest(HitTestContext context)`, returning `@Nullable HitResult.Ending`.
3. Add `LineSelectionHandler.buildContext(Point point)`: resolve `lc.getLine()`/`lc.getLineSelectionState()`, convert `point` to `xSs`/`ySs` via `ScaleContext.pxToSs`, gather `lc.getLayoutResult()`/`lc.getMiddleLineYSs()`. Return `null` if line/selection state are absent. Document the invariant this collapse relies on with a comment: `LineComponent.setLine` always sets `line` and `lineSelectionState` together (verified — grepped for any independent nulling of either field; there isn't one), so a single null-check on either is equivalent to checking both. If that invariant is ever broken, note-head and staff-line hit-testing (which today work independent of `lineSelectionState`) would silently stop working — flag that risk in the comment, not just the mechanism.
4. Extract `hitTestStaffLine(HitTestContext context)` from the inlined staff-line-proximity check in `hitTest(Point)`, using `context.middleLineYSs()` in place of `lc.getMiddleLineYSs()`.
5. Add the `hitTesters` field (`List<HitTester>`, built once in the constructor):
   ```java
   hitTesters = List.of(
       context -> ElementHitTest.hit(lc, context),
       SlideRenderer.getInstance()::hitTest,
       EndingRenderer.getInstance()::hitTest,
       this::hitTestStaffLine
   );
   ```
   Rewrite `hitTest(Point point)` to build the context via `buildContext`, return `Nothing()` if `null`, otherwise loop over `hitTesters` and return the first non-null result, or `Nothing()` if none hit. Remove `hitTestSlideAtPoint`, `hitTestEndingAtPoint`, and the inlined grace-glissando check (now in `SlideRenderer.hitTest`).
6. Rewrite `SlideRendererTest`'s 7 `hitTestGlissando_*` cases and `FallRendererTest`'s 3 cases: build a `HitTestContext` instead of passing raw `(xSs, yYs, line)`, call `hitTest`, assert on the returned `HitResult` variant (`Slide`/`GraceGlissando`/`null`) instead of a raw index.
7. Rewrite `EndingRendererTest`'s 12 `hitTestEnding_*` cases the same way — build a `HitTestContext`, call `hitTest`, assert on `HitResult.Ending`/`null`.
8. Rewrite `LineSelectionHandlerTest`'s cascade coverage: the `HitTest` nested test class (mocks of `ElementHitTest.hitTestElement`, `SlideRenderer.getInstance().hitTestSlide`, `EndingRenderer.getInstance().hitTestEnding`) needs to mock `ElementHitTest.hit`, `SlideRenderer.getInstance().hitTest`, `EndingRenderer.getInstance().hitTest` instead, returning `HitResult` values directly rather than raw index/`Ending`. Also touches `calculateLineSelectionFromDrag`'s anchor-lookup test and `testPressOnEndingSelectsItAndNotifiesScoreView`/`testPressOnEndingSuppressesRubberBandDrag`'s setup mocking (their `lineSelState.isSelected(ending)` assertions are already correct from Phase 4's rename — don't touch those lines, only the mocking setup above them).
9. Add two new regression tests with no coverage today, both silent-if-wrong: (a) `hitTest` on a `LineComponent` with no line/selection state set returns `Nothing()` — pins the Task-3 invariant; (b) a point that would hit two testers (e.g. an element head positioned under a slide) resolves according to `hitTesters` list order, not arbitrarily — pins cascade priority, which nothing currently tests directly (each tester's individual test suite only proves it works in isolation).
10. `./scripts/compile.sh`; `./scripts/test.sh unit`.

---

## ✅ Phase 4: Collapse SelectionProvider.isEndingSelected

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 5, medium effort — mechanical rename chain across 4 production files, safe because `Ending` is a subtype of `LineElement`.

`Ending`, `Hairpin`, `Tie`, and `Tuplet` are all `RangeElement extends
LineElement` (verified). Because every existing caller of
`isEndingSelected` already passes an `Ending`-typed argument, and `Ending` is
assignable to `LineElement`, widening the parameter type doesn't break any
call site — this lets the rename happen atomically via the tool, followed by
a non-breaking type widen, rather than a full manual signature rewrite.

### Tasks
1. `jet_brains_rename` `isEndingSelected` → `isSelected` at all four layers: `LineComponent.SelectionProvider`, `ScoreView`, `SelectionCoordinator`, `LineSelectionState`. This updates every reference atomically, including test call sites (`SelectionCoordinatorQueryGuardsTest`, `LineSelectionStateTest`, `LineSelectionHandlerTest`'s two direct assertions) and Mockito stubs (`EndingRendererTest`'s `when(selectionProvider.isEndingSelected(...))` calls — those specific test methods get deleted wholesale in Phase 5, so the rename touching them first is harmless, not wasted-and-reverted).
2. Widen the parameter type from `Ending ending` to `LineElement element` at all four layers. Non-breaking: every existing caller already passes an `Ending`.
3. Update `LineSelectionState.isSelected`'s body/Javadoc for the widened type — comparison logic is unchanged (`element == selectedEnding`), just structured so a later `|| element == selectedHairpin` (when hairpins land) is a one-line addition, not a new method.
4. `./scripts/compile.sh`; `./scripts/test.sh unit`.

---

## ✅ Phase 5: decorationSelectionColor helper

**Status:** Complete  <br>
**BlockedBy:** 3, 4  <br>
**Recommended model/effort:** Sonnet 5, low effort — small extraction plus a call-site swap matching an existing codebase convention.

`TrillRenderer.renderTrillsFromLine` and `MetronomeRenderer.buildRenderSetup`
both call `RenderingUtils.getDecorationColor` directly at their render call
sites, with no per-renderer wrapper method, and it's tested once, centrally,
in `RenderingUtilsTest` — not duplicated per renderer. Match that convention:
delete `EndingRenderer.determineEndingColor` rather than keeping it as a
wrapper. A one-line wrapper per selectable element, multiplied across
endings/hairpins/dynamics/attachments/tempos/beat-changes/annotations/
lyrics/attribution, recreates exactly the proliferation this refactor exists
to kill — both in production code and in duplicated per-renderer test suites.

### Tasks
1. Add `static Color decorationSelectionColor(LineElement element, LineInvariants invariants)` to `RenderingUtils` — the 5-line body currently in `determineEndingColor`, using the new `provider.isSelected(element, invariants.getLineIndex())`.
2. `jet_brains_safe_delete` `EndingRenderer.determineEndingColor`; update its call site to call `RenderingUtils.decorationSelectionColor` directly, matching `TrillRenderer`/`MetronomeRenderer`.
3. Move the 4 color-decision test cases (`testDetermineEndingColor_*`, `EndingRendererTest.java:375-419`) into `RenderingUtilsTest`, adapted to call `RenderingUtils.decorationSelectionColor` directly instead of `EndingRenderer.determineEndingColor`.
4. `./scripts/compile.sh`; `./scripts/test.sh unit`.
