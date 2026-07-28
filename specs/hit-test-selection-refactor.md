# Handoff: Hit-Test and Selection-Color Refactor

**Status:** Design agreed, no code written. Next step is a detailed phased
implementation plan.

**Purpose of this document:** give a fresh session everything it needs to write
that plan without re-deriving the design or re-surveying the codebase.

**Working directory:** `/Users/aparajita/Developer/projects/SongScribe-worktrees/230-hairpin-actions`
(branch `230-hairpin-actions`, based on `develop`).

---

## Why

The long-term plan is to make **every `LineElement` selectable**. Two subsystems
grow linearly with each newly selectable element, and both are already awkward at
four:

1. `LineSelectionHandler.hitTest()` is an imperative cascade of bespoke calls,
   each with a different signature and return convention. Every new selectable
   element adds another `if` block and another `hitTest*` API shape.
2. `LineComponent.SelectionProvider` has one method per decoration kind
   (`isSlideSelected`, `isEndingSelected`, …), so every new selectable element
   adds an interface method, an implementation in `ScoreView`, one in
   `SelectionCoordinator`, one in `LineSelectionState`, and a bespoke
   `determine*Color` method in its renderer.

This refactor collapses both axes **before** the next selectable element
(hairpins, issue #230) is added, so that element costs one list entry and one
call instead of a new cascade branch and a five-file plumbing chain.

**Sequencing:** this must land before `specs/230-hairpin-actions.md` is
implemented. That spec's §8 and §9 are written assuming this is done. Decide
whether it lands as its own issue that #230 rebases onto, or as the first phases
of #230 — the user has not settled this. Prefer a separate issue: otherwise the
hairpin PR is majority unrelated diff.

---

## Part 1 — Hit testing

### Current state

| Tester | Signature | Returns |
|---|---|---|
| `ElementHitTest.hitTestElement` (`src/main/java/songscribe/ui/component/score/ElementHitTest.java:47`) | `(LineComponent lc, Point point)` — point in document **pixels**, converts internally | `int`, `-1` sentinel |
| `SlideRenderer.hitTestSlide` (`src/main/java/songscribe/ui/renderer/SlideRenderer.java:367`) | `(double clickXSs, double clickYSs, Line line)` | `int`, `-1` sentinel |
| `EndingRenderer.hitTestEnding` (`src/main/java/songscribe/ui/renderer/EndingRenderer.java:119`) | `(double clickXSs, double clickYSs, Line line, @Nullable LayoutResult, double middleLineYSs)` | `@Nullable Ending` |
| staff-line proximity | inlined at `LineSelectionHandler.java:126` | — |

The cascade is `LineSelectionHandler.hitTest(Point)` at
`src/main/java/songscribe/ui/component/score/LineSelectionHandler.java:92-131`,
with two private adapters at `:416` (`hitTestSlideAtPoint`) and `:428`
(`hitTestEndingAtPoint`).

`HitResult` is a package-private sealed interface at
`src/main/java/songscribe/ui/component/score/HitResult.java:29`, with record
variants `ElementHead(int)`, `Slide(int)`, `Ending(songscribe.layout.Ending)`,
`GraceGlissando()`, `StaffLine()`, `Nothing()`.

### Target design

A new package `songscribe.ui.hit` holding three public types:

```java
@FunctionalInterface
public interface HitTester {
    /** Returns the hit result, or null to fall through to the next tester. */
    @Nullable HitResult hitTest(HitTestContext context);
}

public record HitTestContext(
    Point pointPx,                      // document pixels
    double xSs,                         // pointPx converted, staff spaces
    double ySs,
    Line line,
    @Nullable LayoutResult layoutResult,
    double middleLineYSs
) {}

public sealed interface HitResult { /* variants as today, now public */ }
```

`LineSelectionHandler.hitTest()` becomes:

```java
HitResult hitTest(Point point) {
    var context = buildContext(point);

    if (context == null) {          // no line / no selection state
        return new HitResult.Nothing();
    }

    for (var tester : hitTesters) {
        var result = tester.hitTest(context);

        if (result != null) {
            return result;
        }
    }

    return new HitResult.Nothing();
}
```

`hitTesters` is an instance field built once in the constructor. Order **is** the
cascade order:

```java
hitTesters = List.of(
    context -> ElementHitTest.hitTest(lc, context),   // note heads
    SlideRenderer.getInstance()::hitTest,             // slides
    EndingRenderer.getInstance()::hitTest,            // endings
    this::hitTestStaffLine                            // staff-line proximity
);
```

`ElementHitTest` is the one tester that also needs the `LineComponent` (it builds
element hit rectangles from component geometry), so its entry is a lambda that
closes over `lc` rather than a bare method reference. Do **not** put
`LineComponent` in `HitTestContext` — it would couple every renderer to a Swing
component to serve one caller.

### Why this shape

- **Return `HitResult`, not a raw index or element.** Each tester decides its own
  result variant, which is what lets bespoke post-processing move out of the
  cascade (see the grace-glissando note below).
- **`null` means "no hit, try the next."** Uniform fall-through with no
  sentinels, and no `Optional` (project rule: no `Optional`).
- **Method references against a functional interface**, not reflection or a
  service loader. The compiler still checks every entry, and registration stays a
  single readable list.

### Three cleanups this enables — do not skip them

1. **Grace-glissando moves out of the cascade.** Today `LineSelectionHandler`
   knows that a slide hit landing on a grace note means `GraceGlissando` rather
   than `Slide` (`:113-117`). That check belongs in the slide tester.
2. **The null-state path collapses.** Today `hitTestSlideAtPoint` returns `-1`
   when `LineSelectionState` is null (`:416-422`), and *separately* the cascade
   returns `Nothing` if the state is null after a slide hit (`:105-109`). With
   the context resolved up front, absence of a line short-circuits once and the
   cascade never runs.
3. **Ordering becomes declarative** rather than an emergent property of statement
   order.

### Constraint you cannot design around

`HitResult` must become **`public`**. Java packages are flat for access control:
package-private in `songscribe.ui` is *not* visible to `songscribe.ui.renderer`
or `songscribe.ui.component.score`, because subpackages are unrelated packages,
not nested scopes. Since the renderers construct `HitResult` variants, the type
and its records must be public wherever they live. The sealed hierarchy is
unaffected — renderers construct the records, they do not implement the
interface.

The user considered hoisting to `songscribe.ui` and settled on a dedicated
`songscribe.ui.hit` package, since `songscribe.ui` is currently a grab-bag
(`Mode`, `ViewScale`, `Appearance`, `MusicEditOperations`, …) and the three hit
types are cohesive. Confirm before moving if the plan wants to revisit.

---

## Part 2 — Selection color

### Current state — and what is *not* wrong with it

Five color functions exist, and they are **not** all the same shape. Do not try
to unify all five behind one interface; the user's objection was specifically to
the proliferation of near-identical `determine*Color` methods, not to bespoke
logic that is genuinely bespoke.

| Function | Keyed by | Genuinely different because |
|---|---|---|
| `SlideRenderer.determineSlideColor` (`:227`) | note index | a glissando inherits the **target** note's selection (`:249-252`) |
| `TieRenderer.determineTieColor` (`:123`) | `Tie` | reads both endpoints and only colors when they agree |
| `BeamGroupRenderer.getBeamHighlightColor` (`:120`) | beam group | has a third state, `REPLACED_ELEMENT_COLOR` |
| `RenderingUtils.getDecorationColor` (`:120`) | index | index-keyed decorations |
| `EndingRenderer.determineEndingColor` (`:159`) | `Ending` | **nothing** — this is the duplicated shape |

`determineEndingColor` is exactly:

```java
if (!invariants.isEditMode()) return ELEMENT_COLOR;
var provider = invariants.getSelectionProvider();
if (provider != null && provider.isEndingSelected(ending, invariants.getLineIndex())) {
    return invariants.getSelectionColor();
}
return ELEMENT_COLOR;
```

A hairpin version would be byte-for-byte identical, and so would every future
selectable `RangeElement`. That is the duplication to kill.

### Target design

**1. Collapse the provider methods.** In
`LineComponent.SelectionProvider` (`src/main/java/songscribe/ui/component/score/LineComponent.java:74`),
replace `isEndingSelected(Ending, int)` with:

```java
boolean isSelected(LineElement element, int lineIndex);
```

`Ending`, `Hairpin`, `Tie`, and `Tuplet` are all `RangeElement extends
LineElement`, so one method covers endings today and every future selectable
range element with **zero** further interface changes.

Implement down the existing chain, mirroring how `isEndingSelected` is wired
today:

- `ScoreView.isEndingSelected` (`:634`) → `ScoreView.isSelected`
- `SelectionCoordinator.isEndingSelected` (`:324`) → `SelectionCoordinator.isSelected`
- `LineSelectionState.isEndingSelected` (`:168`) → `LineSelectionState.isSelected`,
  which checks `selectedEnding` (and later `selectedHairpin`)

**2. Leave slides alone.** `StaffElement.Slide`
(`src/main/java/songscribe/dom/StaffElement.java:854`) is **not** a
`LineElement` — it is a nested sealed class owned by a `StaffElement`. Keying it
by its owning `StaffElement` (which *is* a `LineElement`) would collide with
note-head selection. `isSlideSelected(int, int)` stays as it is.

**3. One shared helper.** Add to `RenderingUtils`:

```java
static Color decorationSelectionColor(LineElement element, LineInvariants invariants)
```

containing the five-line body above. `determineEndingColor` becomes a call to it
(or is deleted outright, with `EndingRenderer` calling the helper directly —
decide in the plan; deleting it is cleaner but `EndingRendererTest` tests it by
name at `:379-415`).

`determineSlideColor`, `determineTieColor`, `getBeamHighlightColor`, and
`getDecorationColor` keep their own logic. If their *selection* sub-check can
delegate to the helper without contortion, do so; if not, leave them.

---

## Test impact

**Lower than it looks.** Every test obtains its provider via
`mock(LineComponent.SelectionProvider.class)` — Mockito generates the
implementation, so adding or removing interface methods does not require editing
those files. Verified across:

```
src/test/java/songscribe/ui/renderer/{BeamGroupRendererTest,SlideRendererTest,
  TieRendererTest,LineInvariantsTest,EndingRendererTest,LyricTextRendererTest,
  RenderContextTestHelper}.java
src/test/java/songscribe/ui/component/ComponentHierarchyNavigatorTest.java
src/test/java/songscribe/ui/component/score/LineRendererTest.java
```

Only files that **stub** the renamed method need edits. For `isEndingSelected`
that is `EndingRendererTest` (`:379, 391, 406`) alone. Re-verify with:

```
rg -n "isEndingSelected|isSlideSelected" src/test/java
```

Hit-test tests: find the current coverage with
`rg -n "hitTest" src/test/java` before planning, and expect
`LineSelectionHandler`-level tests to need rewriting against the tester list
rather than the cascade.

---

## Scope boundaries

**In scope**

- `songscribe.ui.hit` package: `HitTester`, `HitTestContext`, `HitResult` (moved
  and made public)
- `ElementHitTest`, `SlideRenderer`, `EndingRenderer` adapted to `HitTester`
- `LineSelectionHandler.hitTest()` rewritten as a tester-list loop; the two
  private adapters and the two null-state special cases removed
- Grace-glissando decision moved into the slide tester
- `SelectionProvider.isEndingSelected` → `isSelected(LineElement, int)`, with the
  `ScoreView` / `SelectionCoordinator` / `LineSelectionState` chain
- `RenderingUtils.decorationSelectionColor` + `EndingRenderer` using it

**Out of scope**

- Hairpins — everything hairpin-related belongs to
  `specs/230-hairpin-actions.md` and lands after this
- Making any *additional* element selectable
- `determineSlideColor` / `determineTieColor` / `getBeamHighlightColor` behavior
  changes
- `isSlideSelected`, `isElementSelected`, `isLineSelected`, `isLyricSelected`

**Must not regress**

- Cascade order and outcomes must be identical for note heads, slides, grace
  glissandi, endings, and staff lines. This refactor is behavior-preserving; any
  observable change is a bug.
- Selection highlighting for endings must be pixel-identical.

---

## Planning notes

- Project rules: read `AGENTS.md`, `.agents/rules/java.md`,
  `.agents/rules/development.md`, and `.agents/rules/serena.md`. Use
  `jet_brains_*` tools for all Java exploration and refactoring — in particular
  `jet_brains_move` for the `HitResult` package move and `jet_brains_rename` for
  `isEndingSelected` → `isSelected`, so references update atomically. Never move
  Java files by hand.
- Build with `./scripts/compile.sh` exactly — never `./gradlew`. Tests via
  `./scripts/test.sh unit`. e2e requires user approval.
- This is a behavior-preserving refactor with full existing test coverage as the
  gate, so the usual "defer tests until after manual UI verification" rule does
  not apply — existing tests must stay green at every phase, and that is the
  proof of correctness. New tests are only needed for genuinely new surface
  (`HitTestContext` construction, fall-through semantics).
- Suggested phase split: (1) introduce `songscribe.ui.hit` and move `HitResult`;
  (2) adapt the three testers, one phase each or one combined mechanical phase;
  (3) rewrite the cascade and remove the special cases — this is the only
  design-sensitive phase; (4) the `SelectionProvider` collapse; (5) the color
  helper. Phases 1–3 and 4–5 are independent of each other.
