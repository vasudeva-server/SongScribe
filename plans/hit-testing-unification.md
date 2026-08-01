# Hit Testing Unification
**Created:** 2026-07-31

Replace the six ad-hoc hit-geometry mechanisms with one priority-ordered hit registry produced at layout time, make selection a `HitTarget` instead of three parallel concepts, and make articulations, attachments, accidentals, ties, and beams selectable.
## Coordinate space (applies to every phase)
Every hit shape, in every phase, is stored in **layout space**: X in line-local staff spaces (the space of `LayoutResult.getElementXSs`), Y in staff spaces relative to the **staff midline** (the space of `LayoutResult.DecorationLayout.ySs`, `TieLayout.startYSs`, `BeamLayout.startYSs`).

Do not store component space. It is not buildable at layout time: `middleLineYSs` is a `LineComponent` field (`src/main/java/songscribe/ui/component/score/LineComponent.java:130`, `calculateMiddleLineYSs()` at `:606`) with no layout-side equivalent at construction.

```
  view px ──ViewScale.toDocumentPoint──▶ doc px ──ScaleContext.pxToSs──▶ Ss
                                                                         │
                                                        (click path only)│ − middleLineYSs
                                                                         ▼
   ┌───────────────────────────────────────────────────────────┐   LAYOUT SPACE
   │  y = −4 ····························· above staff         │   X: line-local Ss
   │  y = −2  ─────────────────────────── staff line           │   Y: 0 at midline,
   │  y =  0  ═══════════════════════════ MIDLINE  (y = 0)     │      positive downward
   │  y = +2  ─────────────────────────── staff line           │
   │  y = +6 ····························· lyric row           │
   └───────────────────────────────────────────────────────────┘
              ▲                                    ▲
              │ DecorationLayout.ySs               │ lyric boxes arrive in
              │ TieLayout.startYSs                 │ LINE-TOP origin and must
              │ BeamLayout.startYSs                │ subtract paintAboveMidlineSs
              │ SlideLayout (built with            │ at registration
              │   middleLineYSs = 0)               │
              └── already midline-relative ────────┘
```

Exactly two conversions exist, and there are no others:

- A click point converts once, at query time: `layoutYSs = ScaleContext.pxToSs(docPointPx.getY()) - middleLineYSs`.
  
- Geometry already in line-top origin — the lyric boxes, via `LayoutResult.lyricAreaBaseYSs()` (`LayoutResult.java:726`) — converts to midline origin at registration by subtracting `LayoutResult.paintAboveMidlineSs()` (`LayoutResult.java:625`), which is documented as the distance from the top of the line's component to the staff midline and therefore equals `middleLineYSs`.
  

`ScaleContext` is a **fixed document scale** — `ssToPx` / `pxToSs` never vary with on-screen zoom (see `.agents/guides/unit-conversion.md`). Px-derived constants such as `MIN_HIT_SIZE_PX` and `HIT_THICKNESS_PX` therefore convert to a constant number of staff spaces and are safe to bake into layout-time geometry.
## Targets are addressed by identity, never by index
`HitTarget` names the thing it selects by object reference, not by position in the line. `Element` carries a `StaffElement`, not an `int`.

This is not a style preference. An index-addressed target goes silently wrong on mutation: select element 5, delete element 2, and index 5 now names a different, live element — nothing dangles, the selection is simply pointing at the wrong note. Identity addressing removes the failure mode instead of detecting it, and it matches how the DOM already refers to things across mutations (`Line.java:2013` resolves span anchors with `elements.indexOf(span.getAnchorElement())`, tuplets the same way at `:2089`, and `LineComponent.java:767` converts a lyric's element to an index at point of use).

Indices are derived where an index is actually needed, via `Line.getElementIndex(StaffElement)` (`Line.java:1623`), and never stored in a target.

The index range `selectionBegin..selectionEnd` is a separate concept that survives unchanged beside the target — see Phase 7. Its own index-shift hole is tracked as issue #710 and is out of scope here.
## What is still stored separately after Phase 9
Phase 9 unified the selection *query* — `isSelected(HitTarget, int)` is the only one renderers ask — but not the selection *stores*. Four remain:

| store | shape | in the hit vocabulary? | owner / lifetime |
| --- | --- | --- | --- |
| `selected` (`LineSelectionState:94`) | names one thing | yes — 9 variants | per-line state, rebuilt by `LineComponent.setLine:190` |
| `selectionBegin/End/Anchor` (`:87-89`) | spans many, names none | **no** — the registry never emits a range | same |
| `lineSelected` (`:90`) | names one thing | **yes** — `HitTarget.StaffLine` | same |
| `lyricSelection` (`SelectionCoordinator:83`) | names one thing | **yes** — `HitTarget.Lyric` | score-level, outlives a line rebuild |

`lineSelected` differs from `selected` on nothing, and `lyricSelection` differs only on ownership; Phases 12 and 13 fold both in. The index range differs on the one axis that matters — it names no single thing, and no click ever produces it — so it stays where it is. Giving `HitTarget` a `Range` variant was considered and rejected: every other variant comes out of `HitRegistry.hitTest`, a range never can, and it would force an `owner()` answer that does not exist.

> **Superseded in part by Phase 15.** The rejection above is sound *about `HitTarget`* and remains in force — no `Range` variant is ever added to it. But it does not follow that the range must stay in a second store, and this section reads as though it did. Phase 15 unifies the two stores behind a `Selection` supertype that wraps `HitTarget`, leaving `HitTarget` itself untouched.

Unifying the stores does **not** resolve the deliberate disagreement between `isElementSelected` (which takes in a trailing breath mark) and what the tie/beam/tuplet toggles operate on — see `LineSelectionState:68-74`. That stays as it is.
## Overlap resolution
```
  PRIORITY (higher wins)          a click at ✱ resolves like this:
  ────────────────────────
  LYRIC        100                 ┌──────────── Ending box (50) ────────────┐
  ARTICULATION  90                 │        ▲ articulation (90)              │
  ATTACHMENT    90                 │      ╭─┴─────────────╮ Tie bbox (40)    │
  ACCIDENTAL    85                 │      │   ✱           │                  │
  ELEMENT       80                 │  ♯ ● │  note head    │  ●               │
  SLIDE         70                 │ (85)(80)             │ (80)             │
  HAIRPIN       60                 │      ╰───────────────╯                  │
  ENDING        50                 └─────────────────────────────────────────┘
  TIE           40
  BEAM          30                 ✱ is inside Ending(50) and Tie(40) only
  STAFF_LINE    10                 → highest priority containing it wins → TIE

  Rule: of all regions whose shape contains the point, the highest priority wins.
        Ties in priority are broken by SMALLEST bounding-box area.

  The area tiebreak is why a note head inside an ending's box needs no hand-ordering:
  ELEMENT(80) already outranks ENDING(50). The tiebreak matters within a band —
  two articulations at 90 whose boxes overlap resolve to the smaller one.
```

Two ordering constraints are deliberate and must not be reordered:

1. `LYRIC > ELEMENT > SLIDE > HAIRPIN > ENDING > STAFF_LINE` reproduces, verbatim, the current `List<HitTester>` cascade order in `LineSelectionHandler`'s constructor.
  
2. `ARTICULATION` and `ATTACHMENT` outrank `TIE`, because a tie's hit shape is its bounding box and deliberately over-covers the notes it spans (see Phase 6).
  
## Phase dependency graph
```
  1 DOM Prerequisites
  │
  ├──▶ 2 Hit Vocabulary + Registry ──┐
  │                                  │
  3 Layout-Time Slide Geometry ──┬───┴──▶ 5 Register Six Existing Kinds
  │                              │                    │
  └──▶ 4 Consume Slides,         │        ┌───────────┴───────────┐
       Delete Cache,             │        ▼                       ▼
       Layout Provider           │   6 Register New Kinds    7 Selection on
                │                │        │                    HitTarget
                │                │        │                       │
                └────────────────┴────────┴───────────┬───────────┘
                                                      ▼
                                        8 Renderer Repoint A (existing colors)
                                                      │
                                                      ▼
                                        9 Renderer Repoint B (new colors) + drop bridge
                                                      │
                                                      ▼
                                        10 Manual UI Verification  ── user gate
                                                      │
                                                      ▼
                                        11 Integration + Regression Tests
                                                      │
                                                      ▼
                                        12 Fold Line Selection into Target
                                                      │
                                                      ▼
                                        13 Hoist Target Selection to Coordinator
                                                      │
                                        ┌─────────────┴─────────────┐
                                        ▼                           ▼
                                14 Wire In Deletion    15 One Selection, Several Shapes
```

Phases 2 and 3 can run concurrently once Phase 1 lands. Phases 6 and 7 can run concurrently — 6 owns `HitRegionBuilder.java`, 7 owns the UI files.
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [DOM Prerequisites](#-phase-1-dom-prerequisites) | ✅ Complete | — |
| 2   | [Hit Vocabulary and Registry](#-phase-2-hit-vocabulary-and-registry) | ✅ Complete | — |
| 3   | [Layout-Time Slide Geometry](#-phase-3-layout-time-slide-geometry) | ✅ Complete | — |
| 4   | [Consume Slide Geometry, Delete Render Cache](#-phase-4-consume-slide-geometry-delete-render-cache) | ✅ Complete | — |
| 5   | [Register the Six Existing Kinds](#-phase-5-register-the-six-existing-kinds) | ✅ Complete | — |
| 6   | [Register the New Kinds](#-phase-6-register-the-new-kinds) | ✅ Complete | — |
| 7   | [Selection on HitTarget, Handler Collapse](#-phase-7-selection-on-hittarget-handler-collapse) | ✅ Complete | — |
| 8   | [Renderer Repoint A: Existing Color Decisions](#-phase-8-renderer-repoint-a-existing-color-decisions) | ✅ Complete | — |
| 9   | [Renderer Repoint B: New Color Decisions](#-phase-9-renderer-repoint-b-new-color-decisions) | ✅ Complete | — |
| 10  | [Manual UI Verification](#-phase-10-manual-ui-verification) | ✅ Complete | — |
| 11  | [Integration and Regression Tests](#-phase-11-integration-and-regression-tests) | ✅ Complete | —   |
| 12  | [Fold the Line Selection into the Target](#-phase-12-fold-the-line-selection-into-the-target) | ✅ Complete | —   |
| 13  | [Hoist the Target Selection to the Coordinator](#-phase-13-hoist-the-target-selection-to-the-coordinator) | ✅ Complete | —   |
| 14  | [Wire In Deletion](#phase-14-wire-in-deletion) | ⏳ Pending | —   |
| 15  | [One Selection, Several Shapes](#-phase-15-one-selection-several-shapes) | ✅ Complete | —   |

* * *
## ✅ Phase 1: DOM Prerequisites
**Status:** Complete **BlockedBy:** — **Files:** src/main/java/songscribe/layout/Ending.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/ui/renderer/EndingRenderer.java, src/main/java/songscribe/dom/LineElement.java, src/test/java/songscribe/dom/LineElementTest.java, src/test/java/songscribe/layout/EndingTest.java, src/test/java/songscribe/ui/renderer/EndingRendererTest.java **Recommended model/effort:** Opus 4.8, high effort — the `Ending` move drags a layout/dom layering violation into the open, and splitting the class is a judgment-heavy refactor rather than the mechanical move originally scoped.

Three prerequisites that later phases assume. All are wrong to discover mid-refactor.
### Tasks
1. Move `Ending` from `songscribe.layout` to `songscribe.dom`. Use `jet_brains_move` so every reference updates atomically — never move a Java file by hand. `Ending extends RangeElement` (`src/main/java/songscribe/layout/Ending.java:49`), a `dom` type, so `layout` was never its right home.
  
  The reason this must happen first: Phase 2's `HitTarget` has an `Ending` variant. If `Ending` stays in `songscribe.layout`, then `songscribe.hit` depends on `songscribe.layout` while `songscribe.layout.HitRegionBuilder` depends on `songscribe.hit` — a package cycle, in a package introduced specifically as the neutral vocabulary both layers share. After the move, `songscribe.hit` depends only on `songscribe.dom`.
  
2. Split the layout geometry off `Ending` into a new `src/main/java/songscribe/layout/EndingBracketGeometry.java`.
  
  The move in task 1 does not stand on its own: `Ending` imports `songscribe.layout.ElementColumn` and `songscribe.layout.NoteGeometry`, so once it lands in `songscribe.dom` the architecture test `PackageDependencyTest.domMustNotImportLayout` (`src/test/java/songscribe/PackageDependencyTest.java:63`) fails. That test is a real layering invariant and must not be weakened or whitelisted. The class was always half DOM data and half layout geometry; the move is what forces the split.
  
  **Moves to `songscribe.layout.EndingBracketGeometry`** (a final class, private constructor, static members only):
  
  - `ENDING_FONT` (`Ending.java:56`) and `LABEL_FONT_SCALE` (`:53`) — the font is built from `NoteGeometry.MUSIC_FONT_SIZE_SS`, which is the whole reason `NoteGeometry` is imported.
    
  - `LABEL_1_BOUNDS_SS` (`:66`) and `LABEL_2_BOUNDS_SS` (`:70`), which are initialized from `ENDING_FONT`, and the private label-bounds accessor at `:359` that selects between them.
    
  - `computeBracketRanges(Line, Function<? super StaffElement, ElementColumn>)` (`:214`) — the only user of `ElementColumn` and of `NoteGeometry.ACCIDENTAL_PADDING_SS` (`:268`). Becomes `static List<Ending.BracketRange> computeBracketRanges(Ending ending, Line line, Function<? super StaffElement, ElementColumn> columnFn)`.
    
  - `computeCollisionRegions(BracketRange, double)` (`:373`), which reads the label bounds. Becomes static, taking the `Ending` as its first parameter.
    
  
  **Stays on `songscribe.dom.Ending`:** the `RangeElement` data, the `BracketRange` record, the `bracketRanges` field and `getBracketRanges()` (`:196`), and the pure-`double` constants `VOLTA_TICK_HEIGHT_SS` (`:51`), `LABEL_X_INSET_SS` (`:60`), and `LABEL_Y_OFFSET_SS` (`:63`) — none of which touch `songscribe.layout`. After the split, `Ending`'s only remaining imports outside `dom` are `songscribe.engraving` and `songscribe.util`, both of which the test already permits.
  
  **Preserve today's behavior exactly.** `computeBracketRanges` currently both returns the ranges *and* stores them on the `Ending` for `EndingRenderer.getBracketRanges()` (`EndingRenderer.java:98`) to read back. Keep that: have the static version write through to the `Ending` (add a setter if one is needed) and return the same list. Do not change when ranges are computed or invalidated.
  
  **Update the callers:** `StructuralStacker.java:579` (`computeBracketRanges`) and `:605` (`computeCollisionRegions`); `EndingRenderer.java:163` and `:165` (`Ending.ENDING_FONT`). Find them with `jet_brains_find_referencing_symbols`, not `rg`. Then repoint the tests that call the moved members — `src/test/java/songscribe/layout/EndingTest.java` (`computeBracketRanges` at `:374`, `:427`, `:458`, `:482`, `:520`, `:554`, `:595`; `computeCollisionRegions` at `:267`, `:283`) and `src/test/java/songscribe/ui/renderer/EndingRendererTest.java:90` — without weakening any assertion.
  
3. Make `LineElement.removeChild` clear `parentLine` as well as `parentElement` (`src/main/java/songscribe/dom/LineElement.java:381-385`). It is currently asymmetric with `addChild` (`:370-374`):
  
  ```java
  public void addChild(LineElement child) {
      child.parentElement = this;
      child.parentLine = parentLine;      // sets both
      children.add(child);
  }
  
  public void removeChild(LineElement child) {
      if (children.remove(child)) {
          child.parentElement = null;      // clears only one — parentLine dangles
      }
  }
  ```
  
  Every other removal path in `Line` already nulls `parentLine` (`:1861` ties, `:2072` beams, `:2114` tuplets, `:2191` hairpins, `:2643` elements); `removeChild` is the lone exception. Phase 7's single liveness rule walks the parent chain and checks `getParentLine()`, so a dangling `parentLine` makes a removed articulation or fermata report itself as still on the line.
  
  Production callers are only `StaffElement`'s articulation and attachment paths (`StaffElement.java:257`, `:266`, `:310`, `:346`, `:355`, `:365`). Confirm the full list with `jet_brains_find_referencing_symbols` before editing.
  
4. Update `src/test/java/songscribe/dom/LineElementTest.java` (`testRemoveChildClearsParentElement`, `:175-187`, asserts on post-`removeChild` state) and add an explicit assertion that `removeChild` clears **both** `parentElement` and `parentLine`, so the contract is pinned rather than incidental.
  
5. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green. `PackageDependencyTest` must pass unmodified — if it still reports a `songscribe/dom` → `songscribe.layout` violation, task 2 is not finished.
  

* * *
## ✅ Phase 2: Hit Vocabulary and Registry
**Status:** Complete **BlockedBy:** 1 **Files:** src/main/java/songscribe/hit/HitTarget.java, src/main/java/songscribe/hit/HitRegion.java, src/main/java/songscribe/hit/HitPriority.java, src/main/java/songscribe/hit/HitRegistry.java, src/test/java/songscribe/hit/HitRegistryTest.java **Recommended model/effort:** Opus 4.8, high effort — the `HitTarget` variant list is a one-way door; every later phase is written against it and retrofitting a variant touches every registration site, every `isSelected` call site, and every renderer color decision.

Creates a new package that depends only on `songscribe.dom`. Touches no existing file, so it compiles standalone and can run concurrently with Phase 3.
### Tasks
1. Create `src/main/java/songscribe/hit/HitTarget.java` — a `public sealed interface HitTarget` with exactly these record variants and no others. This list is final; later phases register and color these but never add to them.
  

```java
public sealed interface HitTarget {

    /** The element this target addresses; null only for StaffLine. */
    @Nullable LineElement owner();

    record Element(StaffElement element)          implements HitTarget { }  // note head
    record Lyric(StaffElement element, int verse) implements HitTarget { }
    record Slide(StaffElement owner)              implements HitTarget { }  // glissando or fall
    record GraceGlissando(StaffElement owner)     implements HitTarget { }
    record Hairpin(songscribe.dom.Hairpin hairpin)         implements HitTarget { }
    record Ending(songscribe.dom.Ending ending)            implements HitTarget { }
    record StaffLine()                            implements HitTarget { }
    record Articulation(songscribe.dom.Articulation articulation) implements HitTarget { }
    record Attachment(songscribe.dom.Attachment attachment)       implements HitTarget { }
    record Accidental(StaffElement owner)         implements HitTarget { }  // sub-element target
    record Tie(songscribe.dom.Tie tie)            implements HitTarget { }
    record Beam(songscribe.dom.Beam beam)         implements HitTarget { }
}
```

Each record implements `owner()` in one line, returning the `LineElement` it carries; `StaffLine` returns `null`. Phase 7's liveness rule is written against `owner()` alone and needs no per-variant switch, so this accessor is what keeps revalidation from growing a twelfth arm.

`HitTarget.Accidental` is the sub-element variant, and it is what makes accidentals selectable at all — an element index or an element reference alone cannot name "the accidental of this note" as distinct from the note.

Note the deliberate omission: the existing `songscribe.ui.hit.HitResult` has a `Nothing()` variant — the registry returns `null` instead, so `Nothing` does not carry over. `GraceGlissando` _does_ carry over: read `LineSelectionHandler.hitTestSlide` (in `src/main/java/songscribe/ui/component/score/LineSelectionHandler.java`) to see which condition produces `HitResult.GraceGlissando()` today — it triggers a warning dialog rather than a selection, and that behavior must be preserved in Phase 7.

2. Create `src/main/java/songscribe/hit/HitPriority.java` — a final class of `public static final int` constants, private constructor, no instances. Higher value wins. Use exactly the values in the **Overlap resolution** section above, and Javadoc the two ordering constraints stated there.
  
3. Create `src/main/java/songscribe/hit/HitRegion.java`:
  

```java
public record HitRegion(
    java.awt.Shape shapeSs,
    HitTarget target,
    int priority,
    boolean hoverTestable) { }
```

`Shape` rather than `Rectangle2D` so a glissando strip can be a rotated four-point `Path2D`. In practice `Path2D` is used **only** for glissando strips; every other region in Phases 5 and 6 is a `Rectangle2D.Double`. Coordinates are layout space per the **Coordinate space** section.

`hoverTestable` marks regions that participate in the mouse-move query. It is explicit data at the registration site rather than something inferred from priority, so a later priority change cannot silently alter which regions the hover path scans. Only lyrics set it in this work (Phase 5 task 3).

4. Create `src/main/java/songscribe/hit/HitRegistry.java` — immutable, built once per line at layout time. Put an ASCII diagram of the resolution rule in the class Javadoc; the overlap picture in this plan's **Overlap resolution** section is the one to use, since the rule is not obvious from the code alone.
  

- A static nested `Builder` with `add(Shape shapeSs, HitTarget target, int priority, boolean hoverTestable)` and `build()`. Insertion order carries no meaning.
  
- `public static final HitRegistry EMPTY` for lines with no regions.
  
- `public @Nullable HitTarget hitTest(double xSs, double layoutYSs)` — of all regions whose `shapeSs.contains(xSs, layoutYSs)` is true, return the target of the one with the highest `priority`; break ties by smallest `shapeSs.getBounds2D()` area (width × height); return `null` if none contain the point.
  
- `public @Nullable HitTarget hitTestHover(double xSs, double layoutYSs)` — identical resolution, restricted to regions with `hoverTestable() == true`. This exists because the mouse-move path fires on every pixel of pointer motion; `LineComponent.mouseMoved` (`:679`, lyric query at `:703`) deliberately asks only about lyrics today rather than running the whole cascade, and that must not regress into a full-registry scan. Javadoc that reason.
  
- `public java.util.List<HitTarget> intersecting(java.awt.geom.Rectangle2D rectSs)` — every target whose `shapeSs.intersects(rectSs)`, in **unspecified order**. Document it as unordered: the only caller is the drag-rectangle path in `LineSelectionHandler.calculateLineSelectionFromDrag` (`LineSelectionHandler.java:625`), which filters and iterates without caring about order, and that path runs on every drag event — guaranteeing an order nobody uses would pay for a sort per event.
  
- `public java.util.List<HitRegion> regions()` returning an unmodifiable view, for tests.
  
  Annotate nullable returns with `@Nullable`. Do not use `Optional` and do not use `Objects.requireNonNull` — see `.agents/rules/development.md`.
  

5. Create `src/test/java/songscribe/hit/HitRegistryTest.java`. Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first. This is pure logic with no layout and no UI, so it is testable the moment the class exists — do not defer it. Cover:
  

- highest priority containing the point wins;
  
- equal priorities broken by smallest bounding-box area, including the case the tiebreak exists for: a small region fully inside a large one at the same priority;
  
- a point inside no region returns `null`;
  
- `EMPTY` returns `null` for everything;
  
- `hitTestHover` ignores regions with `hoverTestable == false`, and applies the same priority and area rules among those that remain;
  
- `intersecting` returns every overlapping target and omits every non-overlapping one (assert on set membership, not order — the contract is unordered).
  

6. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  

* * *
## ✅ Phase 3: Layout-Time Slide Geometry
**Status:** Complete **BlockedBy:** — **Files:** src/main/java/songscribe/layout/SlideGeometry.java, src/main/java/songscribe/layout/NoteGeometry.java, src/main/java/songscribe/ui/renderer/RenderingUtils.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/layout/LayoutEngine.java, src/test/java/songscribe/layout/SlideGeometryTest.java **Recommended model/effort:** Opus 4.8, high effort — relocating geometry across a layer boundary while keeping the drawn result pixel-identical; the trim/reject rules are subtle and nothing pins them today.

Moves glissando and fall geometry from render time to layout time. Produces the data; Phase 4 switches the consumers over. After this phase the render-time cache still exists and is still authoritative — nothing observable changes yet.

`StaffElement.Fall` is in scope alongside `Glissando`, but its work is trivial by comparison. Both share the same defect — geometry computed during `SlideRenderer.render` and cached on the DOM model — so both move. But a fall is a single glyph that cannot overlap anything else, so its hit shape is the glyph's axis-aligned bounding box: exact, no over-coverage, no strip, no rotation, no shape design.

Do not touch `SlideRenderer.java`, `StaffElement.java`, or anything under `src/main/java/songscribe/io/musicxml/` in this phase; Phase 4 owns those files.
### Tasks
1. Move `RenderingUtils.noteStaffPositionToCoordinateSs` from `songscribe.ui.renderer` into `songscribe.layout` — `NoteGeometry` is its natural home — and leave `RenderingUtils` delegating to it so existing render call sites are untouched. Use `jet_brains_move`.
  
  Do **not** inline its arithmetic into `SlideGeometry`. Layout must not depend on `ui`, but the fix for that is to put the arithmetic on the correct side of the boundary once, not to fork a second copy of staff-position math that no compiler links to the first. Phase 6 task 3 needs the same quantity again for accidental Y.
  
2. Create `src/main/java/songscribe/layout/SlideGeometry.java` holding the endpoint math currently in `src/main/java/songscribe/ui/renderer/SlideRenderer.java`. Copy (do not yet delete) these symbols and their logic verbatim, preserving every constant, trim rule, and rejection condition:
  

- `SlideRenderer.NoteContext` (record, `SlideRenderer.java:411`) — components `note, cySs, columnRightXSs, glissLeftXSs, glissRightXSs`, plus `shiftedX`.
  
- `SlideRenderer.Endpoints` (record, `SlideRenderer.java:438`) — components `startXSs, startYSs, endXSs, endYSs, angle, length`.
  
- `SlideRenderer.computeEndpoints(NoteContext src, @Nullable NoteContext tgt)` (`SlideRenderer.java:517`) — trims `NoteGeometry.GLISSANDO_DRAWN_GAP_SS` off each end along the line direction, returns `null` when `attachLength <= 2*gap` or when the drawn length is below `SlideRenderer.MIN_RECT_LENGTH_SS` (`SlideRenderer.java:69`). Move `MIN_RECT_LENGTH_SS` into `SlideGeometry`; Phase 4 will delete the original.
  
- `SlideRenderer.noteContextAt(StaffElement note, double elementXSs, boolean beamed, double middleLineYSs)` (`SlideRenderer.java:464`). **Call it with** `middleLineYSs = 0` — that yields exactly the midline-relative layout space the registry uses.
  

3. In `SlideGeometry`, add the fall geometry that today only exists inside `SlideRenderer.drawFallGlyph` (`SlideRenderer.java:192`, called from `renderFall` at `:183`), which computes a `Rectangle2D` and writes it to `StaffElement.Fall.cachedHitBounds`. `drawFallGlyph` already computes that box as a self-contained tail that never touches the `Graphics2D`. Copy it verbatim into `static Rectangle2D.Double computeFallBoundsSs(NoteContext src)`:
  
  ```java
  var glyphXSs = src.columnRightXSs() + NoteGeometry.FALL_GAP_SS;
  var glyphYSs = src.cySs();
  var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.BRASS_FALL_LIP_SHORT);
  return new Rectangle2D.Double(
      glyphXSs + bbox.left(), glyphYSs + bbox.top(), bbox.width(), bbox.height());
  ```
  
  Build the `NoteContext` with `middleLineYSs = 0` as in task 2, so `cySs` is midline-relative. The glyph bbox is in staff spaces with the renderer's Y-down convention relative to the `drawString` baseline anchor, so translating it by the draw point gives the drawn rect directly — this is not an approximation and needs no minimum-size expansion. The return is non-null: unlike a glissando, a fall has no reject case.
  
4. Add a per-line result type and store it on `LayoutResult` (`src/main/java/songscribe/layout/LayoutResult.java`):
  

- A nested record `LayoutResult.SlideLayout` carrying, for one slide-owning note: the `Endpoints` for a glissando and the bounding box for a fall, both `@Nullable`. Exactly one is ever populated — `StaffElement.Slide` is a sealed interface permitting `Glissando` and `Fall` (`StaffElement.java:947`), and they are mutually exclusive. The two are nullable because only one applies at a time, not because either computation can fail: `computeEndpoints` can reject a too-short glissando, but `computeFallBoundsSs` always succeeds.
  
- A `private final Map<StaffElement, SlideLayout> slideLayouts;` field beside the existing `tieLayouts` (`LayoutResult.java:80`) and `decorationLayouts` (`:81`), an accessor `public @Nullable SlideLayout getSlideLayout(StaffElement note)` modeled on `getTieLayout` (`:243`), and a `Builder.putSlideLayout(...)` modeled on `Builder.putTieLayout` (`:1299`). Wire the map through the `LayoutResult` constructor and `Builder.build()` the same way the existing maps are wired.
  

5. Populate it in `src/main/java/songscribe/layout/LayoutEngine.java`. Add a private `calculateSlides(Line line, List<ElementColumn> columns, LayoutResult.Builder builder)` step and call it from the main `layout(...)` pipeline (`LayoutEngine.java:238-295`), immediately after `calculateTies` (called at `:280`). For each element index `i` in the line whose element has a `Glissando` or `Fall`:
  

- Build the source `NoteContext` the way `SlideRenderer.resolveNoteContext` (`SlideRenderer.java:448`) does: element X from `builder`/columns (the equivalent of `layoutResult.getElementXSs(note)`, which is `LayoutResult.java:177`), `beamed = line.findBeamAt(i) != null`, `middleLineYSs = 0`.
  
- For a glissando, build the target context from element `i + 1` the way `SlideRenderer.resolveTargetContext` (`SlideRenderer.java:484`) does — including its `null` return when `i + 1 >= line.elementCount()` — then call `SlideGeometry.computeEndpoints`.
  
- For a fall, call `SlideGeometry.computeFallBoundsSs`.
  
- Store the result via `builder.putSlideLayout`. Store nothing when the geometry computation returns `null`. Note `calculateSlides` runs against the builder, not a finished `LayoutResult`; look at how `calculateTies` (`LayoutEngine.java:819`) reaches column X positions during layout and use the same source.
  

6. Create `src/test/java/songscribe/layout/SlideGeometryTest.java`. These rules are pure arithmetic with no layout pipeline and nothing pins them today, which is exactly why they must be pinned before Phase 4 deletes the original copy. Cover:
  

- a straight horizontal glissando: endpoints trimmed by `GLISSANDO_DRAWN_GAP_SS` at each end;
  
- a diagonal glissando: `angle` and `length` match the trimmed segment;
  
- the two reject cases return `null` — `attachLength <= 2*gap`, and drawn length below `MIN_RECT_LENGTH_SS`;
  
- a glissando with no following element (`tgt == null`);
  
- `computeFallBoundsSs` returns the glyph bbox translated by the draw point, and never `null`.
  

7. Run `./scripts/compile.sh` and confirm SUCCESS. Then run `./scripts/test.sh unit` and confirm green — apart from the new test this phase is purely additive, so any other failure is a real regression in the layout pipeline.
  

* * *
## ✅ Phase 4: Consume Slide Geometry, Delete Render Cache
**Status:** Complete **BlockedBy:** 3 **Files:** src/main/java/songscribe/ui/renderer/SlideRenderer.java, src/main/java/songscribe/dom/StaffElement.java, src/main/java/songscribe/ui/component/MainFrame.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/io/SongFileWriter.java, src/main/java/songscribe/io/musicxml/MusicXmlWriter.java, src/main/java/songscribe/io/musicxml/NoteWriteContext.java, src/main/java/songscribe/io/musicxml/MusicXmlNoteWriter.java, src/main/java/songscribe/io/musicxml/MusicXmlNotationsWriter.java **Recommended model/effort:** Opus 4.8, high effort — deletes public mutable state read by an unrelated subsystem and threads a new dependency through the MusicXML writer; getting the writer's data source wrong silently changes saved files.

Phase 3 has added `LayoutResult.SlideLayout`, `LayoutResult.getSlideLayout(StaffElement)`, and `src/main/java/songscribe/layout/SlideGeometry.java` containing `NoteContext`, `Endpoints`, `computeEndpoints`, `computeFallBoundsSs`, and `MIN_RECT_LENGTH_SS`. This phase switches every consumer to that data and deletes the render-time cache.

Do not touch `LayoutResult.java` or `LayoutEngine.java` in this phase.
### Tasks
1. In `src/main/java/songscribe/ui/renderer/SlideRenderer.java`, delete the copied originals now living in `SlideGeometry`: `computeEndpoints` (`:517`), the `NoteContext` (`:411`) and `Endpoints` (`:438`) records, and `MIN_RECT_LENGTH_SS` (`:69`).
  
  Also delete `resolveNoteContext` (`:448`), `noteContextAt` (`:464`), and `resolveTargetContext` (`:484`) from `SlideRenderer` outright. Once task 2 rewrites `render` to read `layoutResult.getSlideLayout(note)`, the renderer no longer builds note contexts at all — except the hover-preview caller that passes an explicit X for an element not on the line. Point that caller directly at `SlideGeometry.noteContextAt(...)`; find it with `jet_brains_find_referencing_symbols` and keep it working. Do not leave delegating wrappers behind.
  
2. Rewrite `SlideRenderer.render` (`:576`) to read `layoutResult.getSlideLayout(note)` instead of calling `computeEndpoints`, and **delete the seven cache writes** at `SlideRenderer.java:590-596` (`cachedStartX`, `cachedStartY`, `cachedAngle`, `cachedCos`, `cachedSin`, `cachedLength`, `hasCachedGeometry`). Layout coordinates are midline-relative; the renderer draws in component space, so add `middleLineYSs` to every Y read out of `SlideLayout` — `RenderingUtils.layoutYToComponentYSs` (`RenderingUtils.java:187`/`:201`) is exactly this conversion. The drawn result must be pixel-identical to before.
  
3. Rewrite `SlideRenderer.renderFall`/`drawFallGlyph` (`:183`/`:192`) the same way: read the fall rect from `LayoutResult.SlideLayout` and delete the write to `Fall.cachedHitBounds`. Then rewrite `SlideRenderer.hitTestSlide` (`:370`) to read `layoutResult.getSlideLayout(...)` for both branches instead of the cache — it currently rotates the click point into the glissando's local frame using `cachedStartX/Y`, `cachedCos/Sin` and tests `0 <= localX <= cachedLength && |localY| <= halfHitSs` (using `HIT_THICKNESS_PX`, `:72`). Keep `hitTestSlide` working; Phase 8 deletes it once nothing calls it.
  
4. Delete the cache fields from `src/main/java/songscribe/dom/StaffElement.java`: all seven `transient` fields on the nested `Glissando` class (`:962-968`) and `cachedHitBounds` on `Fall` (`:980`). They are `public` with no accessors. The comment on `Slide.copy()` (`:951-956`) explains why the caches are not copied — update or remove it as the deletion makes it stale.
  
5. Give the MusicXML writer a real geometry source by **injecting a layout provider from the caller**, not by constructing a `LayoutEngine` inside the writer.
  
  A `LayoutEngine` cannot legitimately be built in `songscribe.io`: it is an instance class (`LayoutEngine.java:169`) requiring `LyricRenderMetrics`, which is owned by `ScoreView` (field at `ScoreView.java:229`, accessor `getLyricRenderMetrics()` at `:1412`) in the UI layer. Worse, the convenience overload `layout(line)` delegates to `layout(line, false, false, null)` (`:190`), while the paint path uses the four-argument form with real values (`LineComponent.performLayout`, `:451`): `isLastLine`, `hasLeadingLyricContinuation`, and the first line's `attribution`. `isLastLine` changes horizontal placement (`:265`), so a writer-built layout would emit coordinates that disagree with the painted score on the first and last lines — silently, in saved files. `layout()` also calls `resolveStemDirections(line)` (`:255`), which would mutate the document during a save.
  
  Instead:
  

- Define a small functional interface — a line-to-`LayoutResult` provider — in `songscribe.layout`. Thread it through `SongFileWriter.write` (`src/main/java/songscribe/io/SongFileWriter.java:51`, and the `File` overload at `:67`) and `MusicXmlWriter.writeSong` (`MusicXmlWriter.java:52`, `:66`) alongside the existing `DocumentFontsHolder fonts` parameter. The interactive save path already hands a UI object across this seam: `MainFrame.java:1045` calls `SongFileWriter.write(song, scoreView, currentFile)`.
  
- Implement the provider in `ScoreView` by returning each line's live `LayoutResult` from its `LineComponent` via `readyLayout()`. This is what makes saved coordinates identical to painted ones by construction rather than by re-derivation, and it is nearly free — most lines are already laid out (`layoutDirty == false`), so no relayout happens. Saving is user-initiated; there is no autosave timer, so a full pass on the few dirty lines is acceptable.
  
- Provide a headless fallback for callers with no `ScoreView` (tests, and any non-interactive writer path). It must construct the engine with the same real arguments the paint path uses — `isLastLine` computed from the line index, `attribution` on the first line — not the one-argument convenience overload. Verify the current caller list with `jet_brains_find_referencing_symbols` on `MusicXmlWriter.writeSong` before choosing the fallback's shape.
  
- Add a `@Nullable LayoutResult layoutResult` component to the `NoteWriteContext` record (`NoteWriteContext.java:33-39`) and populate it at the construction site (`MusicXmlWriter.java:302`), obtaining it from the provider in `writeLineDrivenMeasures` (`:124`, which iterates `song.getLines()` at `:139`). Update `MusicXmlNoteWriter.writeNote` (`:40`) and its call to `MusicXmlNotationsWriter.writeNotations` (`:142`) to carry it through.
  

6. Repoint `MusicXmlNotationsWriter.writeSlide` (`MusicXmlNotationsWriter.java:232`, called from `:104` and `:110`). Replace the reads of `glissando.cachedStartX/cachedStartY/cachedLength/cachedCos/cachedSin` (`:256-260`) and the `hasCachedGeometry` guard (`:237`) with a lookup of `LayoutResult.SlideLayout` for the glissando's owner note. Preserve the existing semantics exactly: the `SLIDE_STOP` element's `default-x`/`default-y` is the **end** of the line (`start + length*cos`, `start + length*sin`) and `SLIDE_START`'s is the line's start. `writeSlide` currently receives only the `Glissando` object; it will need the owner `StaffElement` (and the pending-stop glissando's owner, tracked as `pendingGlissando` at `MusicXmlWriter.java:143` and `:308`) to do the lookup — change the signature accordingly.
  
  **Keep the no-coordinates fallback branch** (`:237-243`, emits `<slide type=... line-type="solid"/>` with no `default-x`/`default-y`). It is tempting to make that branch unreachable, since its whole purpose today is to cover "nothing has painted yet" — but it must stay, with a narrower meaning: `computeEndpoints` legitimately returns `null` for a glissando too short to draw (`attachLength <= 2*gap`, or drawn length below `MIN_RECT_LENGTH_SS`), so after this phase the fallback fires for that real case and only that case. Update its comment to say so.
  
  Note that neither `MusicXmlReader` nor `MusicXmlNoteReader` ever reads `default-x`/`default-y` back — `MusicXmlNoteReader.handleStartNotations` (`:267`) reads only `ATTR_TYPE` (`:276`) — so nothing round-trips; these coordinates exist for external consumers only.
  
7. Repair the tests this phase breaks, so they exercise the new path rather than merely compiling. Existing tests write the deleted fields directly and will fail to compile: `src/test/java/songscribe/ui/renderer/SlideRendererTest.java` (helper `setCachedGeometry`, `:154`, called from `:175`, `:195`, `:207`, `:220`, `:232`, `:257`, `:276`), `src/test/java/songscribe/ui/renderer/FallRendererTest.java` (`:87`, `:100`, `:135`, `:179`, `:198`, `:211`), and `src/test/java/songscribe/io/musicxml/MusicXmlWriterOutputTest.java` (`testGlissandoSlideEndpointsInOutput` at `:433`, cache writes `:442-447`; `testDiagonalGlissandoSlideEndpointsInOutput` at `:617`, cache writes `:625-630`). Rewrite them to drive the layout pipeline rather than to poke cache fields; do not weaken their assertions about the emitted coordinates.
  
8. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  

* * *
## ✅ Phase 5: Register the Six Existing Kinds
**Status:** Complete **BlockedBy:** 2, 3 **Files:** src/main/java/songscribe/layout/HitRegionBuilder.java, src/main/java/songscribe/layout/ElementHitGeometry.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/layout/LayoutEngine.java, src/test/java/songscribe/layout/HitRegionBuilderTest.java **Recommended model/effort:** Opus 4.8, high effort — six geometry sources in three different coordinate conventions have to be reproduced exactly; a silent off-by-one in the Y origin makes everything unclickable in a way only the manual pass would catch.

Populates the registry with exactly the six kinds that are hit-testable today, so behavior is unchanged when Phase 7 switches the handler over. Adds nothing new to `HitTarget` — Phase 2 already fixed the full variant list.

**Registration happens at layout, never at render.** Every region registered here and in Phase 6 is built from data the layout pipeline already produced. Registering during a render pass is what the glissando cache does today, and it means a hit test before the first paint returns nothing, the registry has to be invalidated on every repaint including repaints caused by selection itself, and unrelated consumers end up reading render scratch state.

Coordinates follow the **Coordinate space** section: X in line-local staff spaces, Y relative to the staff midline. Geometry taken from `LayoutResult.DecorationLayout` (`LayoutResult.java:1535`) is already in that space and needs no conversion.
### Tasks
1. Create `src/main/java/songscribe/layout/HitRegionBuilder.java`: a final class with a private constructor and one entry point, `static HitRegistry build(Line line, LayoutResult.Builder builder, ...)` — choose the parameter list from what the registration sites below actually need. `LyricRenderMetrics` is available: it is a `private final` field on `LayoutEngine` (`LayoutEngine.java:155`), so pass it in.
  
  Add a `private final HitRegistry hitRegistry` field to `LayoutResult`, a `public HitRegistry getHitRegistry()` accessor, and wire it through the `LayoutResult` constructor and `Builder.build()`. Call `HitRegionBuilder.build` from `LayoutEngine.buildLayoutResult` (`LayoutEngine.java:432`, invoked from `layout(...)` at `:294`) so the registry is the last thing computed, after every other layout map is populated. Default to `HitRegistry.EMPTY` rather than null.
  
  Give the class a Javadoc ASCII diagram of the registration pipeline — which layout map feeds which target kind — since the mapping is the whole content of the class and is not evident from reading eleven similar-looking `add` calls.
  
  Add one private helper used by every decoration registration in this phase and the next, so the margin rule is written once:
  
  ```java
  private static Rectangle2D.Double decorationHitRectSs(LayoutResult.DecorationLayout layout) {
      return new Rectangle2D.Double(
          layout.xSs(), layout.ySs(), layout.widthSs(),
          layout.heightSs() + layout.marginSs());   // margin is part of the hit area
  }
  ```
  
  This reproduces the rect `RenderingUtils.hitTestDecoration` builds (`RenderingUtils.java:226-258`, the `+ marginSs` at `:250`). Four kinds in this phase and the next use it; do not spell it out repeatedly.
  
2. Create `src/main/java/songscribe/layout/ElementHitGeometry.java` (or add the method to an existing layout class if one fits better) holding **one** function:
  
  ```java
  public static void elementHitRectSs(
      double elementXSs, StaffElement element, Rectangle2D.Double out, boolean expandToMinimum)
  ```
  
  Port it verbatim from `ElementHitTest.buildElementHitRect` (`src/main/java/songscribe/ui/component/score/ElementHitTest.java:115-147`), including `MIN_HIT_SIZE_PX = 8` (`:42`) and the symmetric expansion. The port needs no UI state: the original reads only `layoutResult.getElementXSs(element)`, `lc.getMiddleLineYSs()` (which is `0` in midline-relative layout space), `ElementType` natural width/height, and `getNoteheadTopOffsetSs()`.
  
  One function, two consumers: this phase registers the **expanded** rect for clicking, and Phase 7's drag path calls the same function with `expandToMinimum = false`. Do not store the unexpanded rect in a second `HitRegion` list or a parallel `LayoutResult` accessor — that duplicates geometry that a single call can produce.
  
3. Register note heads at `HitPriority.ELEMENT` as `HitTarget.Element(element)`, using `elementHitRectSs(..., expandToMinimum = true)` — that is what the click path uses today via `ElementHitTest.hitTestElement` (`:51`). Also reproduce its skip rule: elements where `line.getSong().isInteractable(element, line)` is false are not registered (`ElementHitTest.java:66`). `hoverTestable = false`.
  
4. Register lyrics at `HitPriority.LYRIC` as `HitTarget.Lyric(element, verseIndex)`, with `hoverTestable = true` — lyrics are the only kind that opts in. Reproduce `LayoutResult.hitTestLyric` (`:700-719`): for each element, for each `LyricBoxLayout` in `getLyricBoxes(element)` (`:695`), a rect of `(box.xSs(), rowTopYSs, box.widthSs(), rowHeightSs)` where `rowTopYSs = lyricAreaBaseYSs()` and `rowHeightSs = lyricRenderMetrics.lyricBoxHeightSs()`. Convert `rowTopYSs` to midline origin by subtracting `paintAboveMidlineSs()`.
  
  Javadoc why lyrics set `hoverTestable`: `LineComponent.mouseMoved` (`:679`, lyric query at `:703`) suppresses the preview element over lyric text, and the existing comment there (`:692-693`) records that this fires on every pixel of pointer motion and therefore must not run the full cascade.
  
5. Register hairpins and endings from `LayoutResult.decorationLayouts` (`:81`) at `HitPriority.HAIRPIN` and `HitPriority.ENDING`, as `HitTarget.Hairpin(hairpin)` and `HitTarget.Ending(ending)`, using `decorationHitRectSs` from task 1. Use `LayoutResult.getDecorationLayoutsByType(Class)` (`:280`) to enumerate each type. Both are `songscribe.dom` types after Phase 1's move.
  
  Register both as plain rects. A closed `Path2D` tracing the hairpin's actual wedge would be a tighter shape and is worth doing eventually, but it is a refinement rather than a behavior port, and this phase must reproduce today's behavior exactly.
  
6. Register slides at `HitPriority.SLIDE` as `HitTarget.Slide(note)`, reading `LayoutResult.getSlideLayout(note)` (added by Phase 3). For a glissando, build a closed four-point `Path2D.Double` strip: the segment from `(startXSs, startYSs)` to `(endXSs, endYSs)` offset perpendicular by ±`halfHitSs` on each side, where `halfHitSs` reproduces `SlideRenderer.HIT_THICKNESS_PX` (`SlideRenderer.java:72`) converted to staff spaces via `ScaleContext.pxToSs`. This must select the same points that `SlideRenderer.hitTestSlide` (`:370`) selects today, which rotates the click into the strip's local frame and tests `0 <= localX <= length` and `|localY| <= halfHitSs`.
  
  For a fall, register the bounding box straight from `SlideLayout` with no shape work — a fall is one glyph that cannot overlap anything else, so the box is exact.
  
  This is the **only** place a `Path2D` is used. Everything else registers a `Rectangle2D.Double`.
  
  Also register `HitTarget.GraceGlissando(note)` at the same priority for whichever case `LineSelectionHandler.hitTestSlide` currently reports as `HitResult.GraceGlissando()` — read that method to find the condition.
  
7. Register the staff line at `HitPriority.STAFF_LINE` as `HitTarget.StaffLine()`. Reproduce `LineSelectionHandler.hitTestStaffLine` (`:240-247`) exactly, both ANDed conditions: within `STAFF_HIT_RADIUS_SS = 2.0` (`:59`) of the middle line — i.e. `|layoutYSs| <= 2.0`, since layout space is midline-relative — **and** `HorizontalSpacingCalculator.isWithinHeaderXSs(xSs, line)`. The line is deliberately selectable only from the header at the left, never by clicking a staff line under the music. Do not "fix" this; the port must be verbatim.
  
8. Delete the dead bounds layer from `src/main/java/songscribe/layout/LayoutResult.java`: the `Map<LineElement, ElementBoundsSs> elementBounds` field (`:71`), the `Builder.elementBounds` field and `Builder.putElementBounds` (`:1192`), and the methods `getElementBounds(LineElement)` (`:350`), `getElementBounds()` (`:375`), `getBounds(Object)` (`:424`), `findAttachmentBounds(...)` (`:442`), and `findRangeElementBounds(...)` (`:508`). Every one of these is called only from `src/test/java/songscribe/layout/LayoutResultTest.java` — no production caller exists. Delete the corresponding tests in `LayoutResultTest.java` too.
  
  Do **not** delete `src/main/java/songscribe/layout/ElementBoundsSs.java` itself or `src/test/java/songscribe/layout/ElementBoundsSsTest.java`: the class is still live in production via `songscribe.layout.SectionLayout` (record component `bounds`, `SectionLayout.java:35`) for title/attribution text.
  
  Also check `getElementPosition(LineElement)` (`:360`) and `contains(Object)` (`:537`) — if they read `elementBounds`, they go too unless they have production callers; verify with `jet_brains_find_referencing_symbols` before deleting either.
  
9. Create `src/test/java/songscribe/layout/HitRegionBuilderTest.java` covering this phase's six kinds: lay out a line and assert that a note head, a lyric box, a hairpin, an ending, a glissando strip, and the staff line each register a target. Assert on target identity and on a point known to fall inside each shape — not on exact coordinates, which are layout details. Add one assertion that lyric regions have `hoverTestable == true` and every other kind has it `false`.
  
10. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  

* * *
## ✅ Phase 6: Register the New Kinds
**Status:** Complete **BlockedBy:** 5 **Files:** src/main/java/songscribe/layout/HitRegionBuilder.java, src/test/java/songscribe/layout/HitRegionBuilderTest.java **Recommended model/effort:** Sonnet 4.6, medium effort — additions to one file, each routing geometry that already exists into an existing builder; the hard decisions (vocabulary, priorities, coordinate space, resolution rule) were made in Phases 2 and 5.

Touch only `HitRegionBuilder.java` and its test. This phase runs concurrently with Phase 7, which owns the UI files.

`LayoutResult.DecorationLayout` (`LayoutResult.java:1535`), `LayoutResult.TieLayout` (`:1491`), and `LayoutResult.BeamLayout` (`:1463`) are already in layout space. Use the `decorationHitRectSs` helper from Phase 5 task 1 for every decoration rect. Every region in this phase is a `Rectangle2D.Double`; none needs a `Path2D`.
### Tasks
1. Register articulations at `HitPriority.ARTICULATION` as `HitTarget.Articulation(articulation)`. `songscribe.dom.Articulation` (`Articulation.java:41`) extends `LineElement` and is added to its owner note by `StaffElement.addArticulation` (`StaffElement.java:252`), which calls both `articulations.add(...)` and `addChild(...)`. Each one already has a `LayoutResult.DecorationLayout` — enumerate with `LayoutResult.getDecorationLayoutsByType(Articulation.class)` (`LayoutResult.java:280`) and register `decorationHitRectSs(layout)`.
  
2. Register attached decorations at `HitPriority.ATTACHMENT` as `HitTarget.Attachment(attachment)`, using `decorationHitRectSs`. Register **each concrete subtype explicitly**, not the `Attachment` base type:
  
  ```
  Attachment (abstract)
  ├── FermataAttachment
  ├── DynamicAttachment
  ├── AnnotationAttachment
  └── MetronomeAttachment (abstract — do not register)
      ├── TempoChangeAttachment
      └── BeatChangeAttachment
  ```
  
  Five concrete subtypes register. `MetronomeAttachment` is abstract (`MetronomeAttachment.java:38`) and is never attached directly. Confirm the list with `jet_brains_type_hierarchy` on `Attachment` before writing the registrations — registering a subtype that has no `DecorationLayout` yields no regions, and missing one makes that kind silently unselectable.
  
  Each of these five requires a matching selection color in Phase 8 or 9. A registered kind with no color decision is selectable but shows no feedback, and clicking it silently clears the user's previous selection — check both lists agree before finishing.
  
3. Register accidentals at `HitPriority.ACCIDENTAL` as `HitTarget.Accidental(ownerNote)` — the sub-element variant. An accidental is not a `LineElement`: it is the `accidental` enum field on `StaffElement` (`StaffElement.java:84`) plus `isAccidentalInParentheses` (`:85`).
  
  Geometry comes from `NoteGeometry.getAccidentalBoundsSs(StaffElement note)` (`src/main/java/songscribe/layout/NoteGeometry.java:477`), which returns `@Nullable AccidentalBounds` — `record AccidentalBounds(double leftSs, double widthSs, double topSs, double botSs)` (`src/main/java/songscribe/dom/AccidentalBounds.java:39`). Read that record's Javadoc: `leftSs`/`widthSs` are relative to the **notehead glyph origin** (X) and `topSs`/`botSs` are relative to the **note center**, Y-down. So:
  
  ```
  x      = elementXSs + leftSs
  width  = widthSs
  y      = noteCenterYSs + topSs
  height = botSs - topSs
  ```
  
  where `noteCenterYSs` is the note's midline-relative center — obtain it from `NoteGeometry.noteStaffPositionToCoordinateSs` (moved there by Phase 3 task 1) with `middleLineYSs = 0`. This is the same quantity `SlideGeometry`'s note context calls `cySs`.
  
  `getAccidentalBoundsSs` returns `null` for notes with no accidental and for grace notes (by design); skip those. The bounds come from a static per-`Accidental`-ordinal table initialized by `NoteGeometry.initializeAccidentalWidths()` (called from `LayoutEngine.layout(...)` at `:246`), so it is already initialized when `HitRegionBuilder` runs. The only existing production consumer is `VerticalStackingCalculator.seedAccidentalsIntoStructural` (`src/main/java/songscribe/layout/stacking/VerticalStackingCalculator.java:277-299`) — read it for a worked example and match its convention.
  
4. Register ties at `HitPriority.TIE` as `HitTarget.Tie(tie)`. Use the **bounding box** of the curve. Compute it directly as `min`/`max` over the eight control-point coordinates in `LayoutResult.TieLayout` (`LayoutResult.java:1491`) — `startXSs/startYSs`, `cp1XSs/cp1YSs`, `cp2XSs/cp2YSs`, `endXSs/endYSs`. Do not allocate a `CubicCurve2D.Double` just to call `getBounds2D()`: that method returns the control-point hull, which is exactly what the direct computation gives, and the registry is rebuilt on every layout pass.
  
  Enumerate the `Map<Tie, TieLayout> tieLayouts` field (`LayoutResult.java:80`) via `getTieLayout(Tie)` (`:243`) or add an entries accessor.
  
  The box deliberately over-covers — it contains the notes the tie spans and the empty region under the arc — which is wanted: a tie is a thin curve and hard to click precisely, and clicking the empty space under the arc should select it. The over-coverage is resolved by priority: articulations and attachments outrank ties, and the area tiebreak hands note heads to `HitTarget.Element`. There is no `Slur` type in this codebase; `songscribe.dom.Tie` (`Tie.java:29`, extends `RangeElement`) is the only curve span.
  
5. Register beams at `HitPriority.BEAM` as `HitTarget.Beam(beam)` — **one region per beam group**, the bounding box of every beam in that group, not per-beam shapes. Read `Map<Beam, BeamLayout> beamLayouts` (`LayoutResult.java:72`), keyed by `songscribe.dom.Beam` (`Beam.java:29`, extends `RangeElement`); `record BeamLayout(double slope, double startYSs, boolean stemsUp, double thickeningSs, Map<StaffElement, StemLayout> stems)` (`:1463`). Derive the group's X extent from the first and last entries of `stems` (their element X positions) and its Y extent from `startYSs` plus `slope × width` plus `thickeningSs`. Read `src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java` (`drawBeams`/`drawBeam`) to see exactly how it turns a `BeamLayout` into drawn beam quads, and take the union of those quads' bounds.
  
6. Extend `src/test/java/songscribe/layout/HitRegionBuilderTest.java` with this phase's kinds: an articulation, each of the five concrete attachment subtypes, an accidental, a tie, and a beam group. Same style as Phase 5 — assert on target identity and on a point known to fall inside each shape.
  
  Add the two priority constraints as explicit regression tests, because they are the ones a later change is most likely to break silently: (a) the six original kinds resolve in the order `LYRIC > ELEMENT > SLIDE > HAIRPIN > ENDING > STAFF_LINE`, and (b) an articulation overlapping a tie's bounding box resolves to the articulation.
  
7. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  

* * *
## ✅ Phase 7: Selection on HitTarget, Handler Collapse
**Status:** Complete **BlockedBy:** 5 **Files:** src/main/java/songscribe/ui/selection/LineSelectionState.java, src/main/java/songscribe/ui/selection/SelectedDecoration.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/component/score/LineSelectionHandler.java, src/main/java/songscribe/ui/component/score/ElementHitTest.java, src/main/java/songscribe/ui/hit/, src/test/java/songscribe/e2e/E2ETest.java **Recommended model/effort:** Opus 4.8, high effort — replaces the selection model while the index-range model must keep working unchanged beside it, and collapses a six-branch dispatch whose branches have unequal side effects.

Phase 5 has made every line's `LayoutResult` carry a `songscribe.hit.HitRegistry`, reachable as `layoutResult.getHitRegistry()`, with `hitTest`, `hitTestHover`, and `intersecting`. Registry coordinates are layout space.

**This phase is deliberately additive on the provider side.** `SelectionProvider` gains `isSelected(HitTarget, int lineIndex)` but keeps its five existing methods, reimplemented on top of the new state so every current renderer call site keeps compiling and behaving identically. Phases 8 and 9 repoint the renderers and delete the five. Do not touch any file under `src/main/java/songscribe/ui/renderer/` here.

**Risk to respect:** the `selectionBegin`/`selectionEnd`/`selectionAnchor` index range is what the tie, beam, and tuplet toggles are built from. It must survive **alongside** the target, not be absorbed into it — an index range cannot name an accidental or a single articulation, and `HitTarget` cannot express a multi-element range. Both live in `LineSelectionState` at once. Note also that `LineSelectionState.isElementSelected` (`:301-306`) deliberately disagrees with the raw range (it includes a trailing breath mark via `line.effectiveDeleteEnd`); that disagreement is documented as intentional in the class Javadoc (`:44-50`) and must be preserved exactly.

Add an ASCII diagram to `LineSelectionState`'s class Javadoc showing the two selection concepts living side by side — the index range and the single `HitTarget` — since the whole class is about keeping them from being confused.
### Tasks
1. In `src/main/java/songscribe/ui/selection/LineSelectionState.java`, replace the `@Nullable SelectedDecoration selectedDecoration` field with `private @Nullable HitTarget selected;`. Keep every index-range field (`selectionBegin`, `selectionEnd`, `selectionAnchor`) and `lineSelected` untouched.
  
  Reimplement on top of `selected`: `getSelectedDecoration()` → a `getSelectedTarget()` returning `@Nullable HitTarget`; `selectDecoration(SelectedDecoration)` → `select(HitTarget)`; `hasDecorationSelection()`, `isSlideSelected(...)`, and `isDecorationSelected(LineElement)` keep their names and semantics but pattern-match on `HitTarget`.
  
  Then delete `src/main/java/songscribe/ui/selection/SelectedDecoration.java`. Map the variants: `SlideSelection` → `HitTarget.Slide`, `EndingSelection` → `HitTarget.Ending`, `HairpinSelection` → `HitTarget.Hairpin`.
  
  Note `lineSelected` is a separate boolean today even though `HitTarget.StaffLine()` now exists — leave `lineSelected` as-is in this phase; folding it in is a behavior change with its own blast radius.
  
2. Rewrite `revalidateDecorationSelection()` (`:166-181`) as a **single liveness rule**, not a per-variant switch. Today it has three hand-written arms; with twelve variants a switch would need twelve. Use `HitTarget.owner()` from Phase 2:
  
  ```java
  var owner = selected == null ? null : selected.owner();
  return clearIfStale(owner != null && !isOnLine(owner));
  ```
  
  `isOnLine(LineElement)` walks `getParentElement()` to the root and compares the root's `getParentLine()` to this state's `line`. This works for every variant at once: articulations and attachments are children of a note, so the walk reaches the note, whose `parentLine` is nulled when it is removed (`Line.java:2643`); ties, beams and hairpins are direct children whose `parentLine` is nulled on removal (`:1861`, `:2072`, `:2191`); `StaffLine` has a null owner and is never stale.
  
  This depends on Phase 1 task 2 having made `LineElement.removeChild` clear `parentLine`. Without it, a removed articulation or fermata reports itself as live. Confirm Phase 1 landed before relying on this.
  
  Delete the three old per-variant rules. Keep `clearIfStale` (`:189`) as-is.
  
3. Update the production consumers of the old API: `src/main/java/songscribe/ui/selection/SelectionCoordinator.java` — `getSelectedDecoration` (`:356`), `triggerReflection` (`:1295`, which reads `getSelectedDecoration` at `:1322`), `hasDecorationSelection` (`:365`), `songDidChangeReflectSelection` (`:1254`, `hasDecorationSelection` check at `:1258`), `isSlideSelected` (`:322`), `isDecorationSelected` (`:334`) — and `src/main/java/songscribe/ui/component/ScoreViewController.java` `handleDelete` (`:596`). Find every caller with `jet_brains_find_referencing_symbols`, not with `rg`.
  
4. Add `boolean isSelected(HitTarget target, int lineIndex)` to `LineComponent.SelectionProvider` (`src/main/java/songscribe/ui/component/score/LineComponent.java:74-112`) and implement it in `ScoreView` (`src/main/java/songscribe/ui/component/ScoreView.java:596-619`, the only implementation) as a one-line delegate to `selectionCoordinator`, matching the shape of the five existing delegates. Keep all five existing methods working — their renderer call sites are `LineInvariants.java:318` and `:277`, `SlideRenderer.java:246` and `:251`, `BeamGroupRenderer.java:133`, `LineRenderer.java:198`, and `RenderingUtils.java:152-153`; none of those files may be edited in this phase.
  
5. Collapse `src/main/java/songscribe/ui/component/score/LineSelectionHandler.java`. Delete the `private final List<HitTester> hitTesters` field (`:68`) and its constructor population (`:76-86`, the `List.of` at `:78-85`), and delete the six tester methods `hitTestLyric`, `hitTestSlide`, `hitTestHairpin`, `hitTestEnding`, `hitTestStaffLine` (`:240-247`), and the `ElementHitTest.hit` adapter call.
  
  Rewrite `hitTest(Point point)` (`:112-127`) as: keep the existing `buildContext`/`readyLayout` guard (`:144-162` — it returns null when `lc.getLine()` or `lc.getLineSelectionState()` is null and calls `lc.readyLayout()` so layout is current), convert the doc-space point to layout space with `xSs = ScaleContext.pxToSs(point.getX())` and `layoutYSs = ScaleContext.pxToSs(point.getY()) - lc.getMiddleLineYSs()`, then return `layoutResult.getHitRegistry().hitTest(xSs, layoutYSs)`.
  
  Keep `hitTestViewPoint(Point)` (`:257-259`) converting view px → doc px via `lc.getViewScale().toDocumentPoint` first.
  
  Reimplement `hitTestLyricViewPoint` (`:271-279`) as a `hitTestHover` query, pattern-matched to `HitTarget.Lyric` so its two callers keep their typed result — `LineComponent.java:703` tests `!= null`, and `:755` reads `.element()` and `.verse()`. Do **not** implement it as a full `hitTest` filtered afterwards: it runs on every pixel of pointer motion (`LineComponent.java:692-693`), which is why `hitTestHover` exists.
  
6. Rewrite `LineSelectionHandler.handlePress` (`:295-364`) as a switch over `HitTarget` instead of `HitResult`, preserving each branch's current effect exactly:
  

   | Target | Effect |
   | --- | --- |
   | `Lyric(element, verse)` | `selectLyric(element, verse)` |
   | `Element(element)` | `selectAndPlayElement(index)` — `selectElementAtIndex` plus `playNoteIfPitched`; derive the index with `line.getElementIndex(element)` |
   | `Slide` / `Hairpin` / `Ending` | `lineSelectionState.select(target)`, replacing today's `selectDecoration(new SelectedDecoration...)` at `:547` |
   | `GraceGlissando` | the existing `OptionDialogs.showWarningMessage` warning, with **no** selection |
   | `StaffLine` | `prepareSelection()` then `lineSelectionState.setLineSelected(true)` |
   | `Articulation` / `Attachment` / `Accidental` / `Tie` / `Beam` | `lineSelectionState.select(target)` then repaint |
   | `null` (no hit) | `lc.repaint()` only, replacing today's `HitResult.Nothing()` branch |

Also update `handleEditModePress(HitResult)` (`:391-413`), which handles the `Lyric`, `Ending`, and `Hairpin` subset for EDIT-mode presses that are not over a preview element.

7. Repoint the drag-rectangle path. `calculateLineSelectionFromDrag(Rectangle dragRect)` (`:625-680`) currently converts the view-px drag rect to staff spaces and calls the private `buildElementHitRect(StaffElement, Rectangle2D.Double)` wrapper (`:621-623`) for each interactable element, adding intersecting ones via `lineSelectionState.extendSelection(elementIndex)`.
  
  Switch it to `ElementHitGeometry.elementHitRectSs(..., expandToMinimum = false)` from Phase 5 task 2 — the same function the registry uses, called with the other flag. Preserve the anchor logic that follows (`ElementHitTest.hitTestElement(lc, dragStart)` at `:666`, with the nearest-endpoint distance fallback) — reimplement the anchor lookup as a registry `hitTest` filtered to `HitTarget.Element`.
  
8. Delete `src/main/java/songscribe/ui/component/score/ElementHitTest.java` and the whole `src/main/java/songscribe/ui/hit/` package (`HitResult.java`, `HitTestContext.java`, `HitTester.java`). Use `jet_brains_safe_delete` so remaining usages are reported rather than silently broken.
  
  Repair every test that referenced them, so they exercise the new API rather than merely compiling:
  

- `src/test/java/songscribe/ui/component/score/ElementHitTestTest.java` — the class under test is gone; fold whatever behavior it still covers into `LineSelectionHandlerTest` or `HitRegionBuilderTest`, then delete the file.
  
- `src/test/java/songscribe/ui/component/score/LineSelectionHandlerTest.java` — it `mockStatic(ElementHitTest.class)` (`:97`, `:123`, and every `ElementHitTest.hit` / `buildElementHitRect` / `hitTestElement` stub). Rewrite against a real `HitRegistry`.
  
- `src/test/java/songscribe/ui/component/score/LineComponentTest.java` (`mockStatic(ElementHitTest.class)` at `:1117-1122` and `:1187-1192`, plus the Javadoc reference to `ElementHitTestTest` at `:1070`).
  
- `src/test/java/songscribe/ui/component/score/NoteDragHandlerTest.java` — it does **not** touch `ElementHitTest`, but it imports `songscribe.ui.hit.HitResult` (`:68`) and constructs `HitResult.ElementHead`, `HitResult.Nothing`, and `HitResult.Lyric` (`:600-601`, `:632`, `:644`, `:703`) to drive `handlePress`. Repoint those to `HitTarget` / `null`.
  
- `src/test/java/songscribe/e2e/E2ETest.java:431` calls `ElementHitTest.buildElementHitRect(lc, note, hitRect)`. Point it at `ElementHitGeometry.elementHitRectSs`. This file is in the e2e source set, which `./scripts/test.sh unit` does **not** compile — it is invisible to this phase's normal verification, which is why it is called out explicitly.
  

9. Add liveness tests to `src/test/java/songscribe/ui/selection/LineSelectionStateTest.java`, one per target family, since task 2 replaced three hand-written rules with one derived rule and a wrong answer here is silent:
  

- a direct child of the line (select a hairpin, remove it, assert the selection clears);
  
- a child of an element (select an articulation or fermata, remove the owner note, assert the selection clears) — this is the case that depends on Phase 1's `removeChild` fix;
  
- an element target (select a note, remove it, assert the selection clears);
  
- `StaffLine` never goes stale;
  
- a live target is **not** cleared by an unrelated mutation on the same line.
  

10. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green. Then compile the e2e source set as well and confirm it builds — deleting a public class is not verified until every source set that could reference it has been compiled.
  

* * *
## ✅ Phase 8: Renderer Repoint A: Existing Color Decisions
**Status:** Complete **BlockedBy:** 4, 6, 7 **Files:** src/main/java/songscribe/ui/renderer/LineInvariants.java, src/main/java/songscribe/ui/renderer/RenderingUtils.java, src/main/java/songscribe/ui/renderer/HairpinRenderer.java, src/main/java/songscribe/ui/renderer/EndingRenderer.java, src/main/java/songscribe/ui/renderer/TieRenderer.java, src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java, src/main/java/songscribe/ui/renderer/SlideRenderer.java, src/main/java/songscribe/ui/renderer/ArticulationRenderer.java, src/main/java/songscribe/ui/renderer/FermataRenderer.java, src/test/java/songscribe/ui/renderer/LineInvariantsTest.java **Recommended model/effort:** Opus 4.8, high effort — the index-keyed path has to survive beside the target-keyed one; a wrong ambient-color save/restore silently recolors unrelated ink.

Phase 7 has replaced `LineSelectionState`'s `SelectedDecoration` field with `@Nullable HitTarget selected`, added `boolean isSelected(HitTarget target, int lineIndex)` to `LineComponent.SelectionProvider`, and left the five legacy provider methods in place as a bridge.

This phase repoints the renderers that **already make a color decision**. Phase 9 handles the ones that need a decision introduced, and deletes the bridge.

`getElementColor(int elementIndex)` **stays.** It is keyed by element index and serves the index-range selection path (`selectionBegin..selectionEnd`), which `colorFor(HitTarget)` cannot express. `colorFor(HitTarget)` is an addition beside it, not a rename of it.
### Tasks
1. Add `public Color colorFor(HitTarget target, int elementIndex)` to `src/main/java/songscribe/ui/renderer/LineInvariants.java`, beside the existing `getElementColor(int)` (`:252`). Keep the existing private `colorFor(int elementIndex, BooleanSupplier playingCheck, BooleanSupplier extraSelectionCheck)` (`:304-325`) and its precedence cascade — playing → extra selection check → `selectionProvider.isElementSelected` → `isElementHovered` (→ `REPLACED_ELEMENT_COLOR`, `:62`) → `Color.BLACK` — and make the new method follow the same cascade, substituting `selectionProvider.isSelected(target, lineIndex)` for the `isElementSelected` step.
  
  `colorFor` **must never call** `Line.getElementIndex`**.** The index is a parameter. It is needed only to feed three pre-existing index-keyed predicates — `isElementPlaying(int)` (`:367`), `isElementInPlayingTie(int)`, `isElementHovered(int)` (`:345`) — and only for the two variants that use them: `HitTarget.Element` and `HitTarget.Accidental`. For every other variant the playing and hovered checks do not apply and the parameter is ignored. Every renderer call site that draws those two variants already has the index from its own loop, so deriving it inside `colorFor` would add a linear scan per drawn element for nothing.
  
2. Repoint `RenderingUtils.decorationSelectionColor(LineElement element, LineInvariants invariants)` (`src/main/java/songscribe/ui/renderer/RenderingUtils.java:149-158`) to take a `HitTarget` and delegate to `invariants.colorFor(target, ...)`, keeping its `ELEMENT_COLOR` (`:111`) default. Do the same for `RenderingUtils.applyDecorationColor(Graphics2D, StaffElement, LineInvariants, ElementFrame)` (`:164-171`) and `getDecorationColor`. Update every caller.
  
3. Repoint hairpins and endings. `HairpinRenderer.renderSingleHairpin` (`HairpinRenderer.java:108`) passes `RenderingUtils.decorationSelectionColor(hairpin, invariants)` — change to `invariants.colorFor(new HitTarget.Hairpin(hairpin), ...)`. Do the same for the ending in `EndingRenderer` (it calls `RenderingUtils.layoutYToComponentYSs` at `EndingRenderer.java:93`; find its `setColor` site) with `new HitTarget.Ending(ending)`.
  
4. Repoint ties and beams. `TieRenderer` sets color at `TieRenderer.java:98` from `determineTieColor(tie, invariants)` — make that consult `invariants.colorFor(new HitTarget.Tie(tie), ...)` while keeping whatever range-selection behavior it has today (it may derive from `getElementColor(int)`; keep that as a fallback so a range selection still highlights its ties). `BeamGroupRenderer.drawBeam` (`:270`, params `boolean selected` at `:279` and `Color selectionColor` at `:281`) sets `selected ? selectionColor : RenderingUtils.ELEMENT_COLOR` at `:347`, and `getBeamHighlightColor` (`:120`) consults `isElementSelected` (`:133`) — add `invariants.colorFor(new HitTarget.Beam(beam), ...)` so a directly-selected beam highlights, without losing the existing range-driven highlight.
  
5. Repoint slides. `SlideRenderer.determineSlideColor(int noteIndex, boolean isGlissando, LineInvariants invariants)` (`SlideRenderer.java:227`) currently delegates to `invariants.getElementColor(noteIndex)` and then, only when that returns `Color.BLACK`, checks `selectionProvider.isSlideSelected(...)` or (glissando only) `selectionProvider.isElementSelected(noteIndex + 1, ...)`. Replace the two provider checks with `invariants.colorFor(new HitTarget.Slide(note), noteIndex)`, preserving the "only when the element color is black" precedence.
  
  Then delete `SlideRenderer.hitTestSlide` (`:370`) — Phase 7 removed its only caller; confirm with `jet_brains_find_referencing_symbols` before deleting.
  
6. Repoint `ArticulationRenderer` and `FermataRenderer`, both of which already have a color decision. `ArticulationRenderer.render(...)` calls `RenderingUtils.applyDecorationColor(g2, element, invariants, frame)` inside a `GraphicsState.save(g2, COLOR)` block, keyed on the **owner note**, not the articulation. Change it to color each articulation from `invariants.colorFor(new HitTarget.Articulation(articulation), ...)`.
  
  Because `render` draws all of a note's articulations in one pass and they can now be selected individually, the color must be set **per articulation**, immediately before `drawArticulationGlyph`/`drawAccentGlyph` — those two helpers set no color of their own. Apply the same treatment to `FermataRenderer` with `new HitTarget.Attachment(fermata)`.
  
7. Create `src/test/java/songscribe/ui/renderer/LineInvariantsTest.java` (or extend it if it exists) with a **table-driven test over every** `HitTarget` **variant**: with a stubbed `SelectionProvider` reporting the target selected, `colorFor` returns the selection color; reporting it unselected, it returns `Color.BLACK`. Assert the precedence cascade explicitly for `HitTarget.Element`: playing beats selected, selected beats hovered.
  
  This is the central rule; the per-renderer assertions in task 8 cover the call sites.
  
8. For each renderer repointed in tasks 3–6, assert that it calls `setColor` with the selection color when its target is selected. A missed `setColor` is the characteristic failure of this phase — it leaves a kind selectable with no visible feedback, which is silent and which only the manual pass in Phase 10 would otherwise catch. The renderer tests under `src/test/java/songscribe/ui/renderer/` already mock `SelectionProvider`; extend them rather than starting fresh.
  
9. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  

* * *
## ✅ Phase 9: Renderer Repoint B: New Color Decisions
**Status:** Complete **BlockedBy:** 8 **Files:** src/main/java/songscribe/ui/renderer/NoteRenderer.java, src/main/java/songscribe/ui/renderer/DynamicMarkingRenderer.java, src/main/java/songscribe/ui/renderer/AnnotationRenderer.java, src/main/java/songscribe/ui/renderer/TempoChangeRenderer.java, src/main/java/songscribe/ui/renderer/BeatChangeRenderer.java, src/main/java/songscribe/ui/component/score/LineRenderer.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/component/ScoreView.java **Recommended model/effort:** Opus 4.8, high effort — introduces color decisions where none exist, each needing ambient-color save/restore so the rest of the drawing keeps its own color; then removes the compatibility bridge, which is the point of no return for the five legacy provider methods.

Five renderers need a color decision **introduced** rather than repointed. Read each one before editing; their starting points differ.
### Tasks
1. `src/main/java/songscribe/ui/renderer/NoteRenderer.java`, `renderAccidental` — sets no color at all and inherits the ambient color set by `LineRenderer.renderElements` (`:259`; `setColor` at `LineRenderer.java:269-270`, immediately before `noteRenderer.render(...)` at `:275`). It already opens `GraphicsState.save(g2, COLOR, FONT)`, so add `g2.setColor(invariants.colorFor(new HitTarget.Accidental(note), elementIndex))` inside that block — the save/restore makes it self-contained and the rest of the note keeps drawing in the caller's color.
  
  `NoteRenderer.render`'s comment at `NoteRenderer.java:172-173` ("Don't set color here — respect the color set by the caller") stays true and must not be violated: the change is scoped to the accidental, inside its own save block.
  
  `renderAccidental` is declared at `NoteRenderer.java:442` with its `GraphicsState.save(g2, COLOR, FONT)` block at `:454`. It currently takes no `LineInvariants`; thread one in from `NoteRenderer.render` (call at `:184`). It is also reached from `PreviewElementOverlay.recordPreviewElement` (`:175`) — a preview note is not on the line and is never selected, so make sure that path still gets the caller's ambient color.
  
2. `DynamicMarkingRenderer` (`:106-107`) and `AnnotationRenderer` (`:74-76`) both already call `RenderingUtils.applyDecorationColor(g2, element, invariants, frame)` inside a `GraphicsState.save` block, keyed on the owner note. Repoint each to `invariants.colorFor(new HitTarget.Attachment(attachment), ...)` for the specific attachment being drawn, exactly as Phase 8 task 6 did for articulations.
  
3. `TempoChangeRenderer` (`:98-100`) sets `g2.setColor(setup.color())` inside `GraphicsState.save(g2, COLOR, FONT)`. It has its own color source; fold the selection check into it rather than overriding it afterwards, so the existing behavior for unselected tempo marks is unchanged.
  
4. `BeatChangeRenderer` sets **no color at all**. Introduce one the same way as `renderAccidental` in task 1: a `GraphicsState.save(g2, COLOR)` block with `invariants.colorFor(new HitTarget.Attachment(beatChange), ...)`.
  
  Tasks 2–4 exist because Phase 6 task 2 registers all five concrete `Attachment` subtypes. Every registered kind needs a color decision; check the two lists agree before finishing this phase.
  
5. Delete the bridge: remove `isElementSelected(int, int)`, `isLineSelected(int)`, `isSlideSelected(int, int)`, `isDecorationSelected(LineElement, int)`, and `isLyricSelected(StaffElement, int, int)` from `LineComponent.SelectionProvider` (`src/main/java/songscribe/ui/component/score/LineComponent.java:74-112`) and their implementations in `ScoreView` (`src/main/java/songscribe/ui/component/ScoreView.java:596-619`), keeping only `isSelected(HitTarget, int)`.
  
  Two call sites are not covered by Phase 8 and must be repointed here: `LineInvariants.getLyricColor` (`:272`, calls `isLyricSelected` at `:277`) → `new HitTarget.Lyric(element, verseIndex)`, and `LineRenderer.drawStaffLines` (`LineRenderer.java:195`, calls `isLineSelected` at `:198`) → `new HitTarget.StaffLine()`.
  
  Use `jet_brains_find_referencing_symbols` on each of the five to confirm nothing in `src/main/` is left.
  
6. Extend the color tests from Phase 8 tasks 7–8 to cover the five renderers touched here, with the same assertion: selected target → selection color, unselected → the renderer's normal color. For `renderAccidental` add the specific assertion that selecting an accidental recolors **only** the accidental and leaves the rest of the note in the caller's ambient color — that is the property the `GraphicsState.save` block exists to guarantee.
  
7. Update the `SelectionProvider` mocks under `src/test/java/songscribe/ui/renderer/`, which mock the interface extensively and must move to the collapsed form. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  

* * *
## ✅ Phase 10: Manual UI Verification
**Status:** Complete **BlockedBy:** 9 **Files:** — **Recommended model/effort:** Opus 4.8, low effort — no code changes; drive the app, then wait for the user's verdict.

No further tests are written until the user has confirmed the behavior in the running app. Do not proceed to Phase 11 without that confirmation.
### Tasks
1. Run `./scripts/compile.sh` and confirm SUCCESS. Then ask the user for permission to launch the app — `./scripts/run.sh` must never be executed without it.
  
2. Once granted, launch with `./scripts/run.sh` and ask the user to confirm each of the following, reporting exactly what they say rather than summarizing:
  

- **No regressions in the five existing kinds.** Clicking a note head, a lyric syllable, a glissando/fall, a hairpin, and an ending each selects the same thing it did before, and each draws in the selection color. 👍
  
- **Staff line unchanged.** The line is still selectable only by clicking the header at the left, and still not by clicking a staff line under the music. 👍

- **EDIT mode selects lyrics and nothing else.** In EDIT mode a click selects a lyric syllable in place; a click on any other kind — hairpin, ending, note, or any of the newly selectable kinds — inserts or does nothing, never selects. Endings and hairpins used to be selectable there and deliberately no longer are. Everything else is reached by alt+click (which selects in place) or by switching to SELECT mode, including by clicking the staff header.
  
- **Lyric hover unchanged.** Moving the pointer over lyric text still suppresses the preview element, with no lag introduced on pointer motion. 👍
  
- **Overlap resolution.** Clicking a note head that sits inside an ending's box selects the note, not the ending. Clicking the empty space under a tie's arc selects the tie. Clicking an articulation that sits over a tie's bounding box selects the articulation. 👍
  
- **Every newly selectable kind, one at a time.** An articulation (accent, staccato), a fermata, a dynamic marking, an annotation, a tempo change, a beat change, an accidental, a tie, a beam group, a trill, and a tuplet are each individually selectable **and each draws in the selection color when selected**. Do not skip any: a kind registered in Phase 6 without a color in Phase 8 or 9 is selectable but invisible, and clicking it silently clears the previous selection. 👍
  
- **Accidental selection is scoped.** Selecting an accidental recolors only the accidental, leaving the rest of the note black. 👍
  
- **Selection clears on removal.** Select an articulation or fermata, then delete its note (and separately, undo an insertion). The selection clears rather than persisting invisibly. 👍
  
- **Range selection intact.** Drag-selecting a range of notes still works, and the tie / beam / tuplet toggles still operate on the same range they did before. 👍
  
- **Glissando coordinates survive a cold save.** Open a score containing a glissando and save it to `.musicxml` without scrolling the glissando's line into view, then inspect the saved file: the `<slide>` elements must carry `default-x` and `default-y`. Before this work they were silently dropped whenever the line had not been painted. 👍
  
- **Saved coordinates match the painted score.** Save a score whose glissando is on the **last** line, and confirm the emitted `default-x`/`default-y` match what is drawn. This is the case a writer-built layout would get wrong. 👍

- **Trills** Verify the correct hit region. 👍

- **Tuplets** Verify the correct hit region. 👍

- **Breath marks** Currently can only be selected via drag select. Need to be able to directly click to select. 👍

  Both are now registered: `HitTarget.Trill` at `HitPriority.TRILL` and `HitTarget.Tuplet` at `HitPriority.TUPLET`, with the regions above — a trill's X runs from the `tr` glyph's left edge to the wavy line's end, and a tuplet's is the bracket edges when bracketed and the number's ink alone when not. Verify by clicking: a single-note trill, a trill spanning several notes (including out along the wavy line), a bracketed tuplet, and a beamed number-only tuplet, whose bracket-less run of empty space above the beam must **not** select it.

3. Report the results verbatim. If the user reports a defect, fix it and re-verify before moving on.
  

* * *
## ✅ Phase 11: Integration and Regression Tests
**Status:** Complete **BlockedBy:** 10 **Files:** src/test/java/songscribe/io/musicxml/MusicXmlWriterOutputTest.java, src/test/java/songscribe/layout/HitRegionBuilderTest.java, src/test/java/songscribe/ui/component/score/LineSelectionHandlerTest.java **Recommended model/effort:** Sonnet 4.6, medium effort — the behavior is verified by hand and the unit tests are already in place; this is the end-to-end layer on a settled API.

Each earlier phase wrote the tests for what it built. This phase adds only the cross-cutting cases that span phases. Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first. Do not write e2e tests — they require the user's approval to run.
### Tasks
1. ~~Add a regression test for the save-before-paint glissando defect...~~ Already covered: Phase 4 task 7's rewrite of `testGlissandoSlideEndpointsInOutput` builds a glissando song, writes it via `MusicXmlWriter` with no render pass, and asserts `default-x`/`default-y`, with a javadoc note pinning exactly this defect. No new test added.
  
2. Added `testTooShortGlissandoEmitsNoCoordinatesFallback` (`MusicXmlWriterOutputTest`). The real spacing engine reserves at least `NoteGeometry.MIN_GLISSANDO_RESERVATION_SS` between any two glissando-connected notes (refs #443), so a too-short glissando cannot be reproduced by laying a song out normally — that reservation exists precisely to prevent it. Since `layoutResult` is consulted only for slide geometry in the whole `io.musicxml` package, the test instead injects a bare `mock(LayoutResult.class)` via the explicit-`LineLayoutProvider` `writeSong` overload: an unstubbed mock returns `null` from `getSlideLayout`, which is exactly the state `LayoutEngine.calculateSlides` leaves when `computeEndpoints` rejects a glissando as too short (see the `SlideLayout` javadoc).
  
3. ~~Add a test for the headless layout-provider fallback... for a glissando on the **last** line...~~ Dropped: false premise. `isLastLine` only feeds `positionTerminalFlushRight` (`LayoutEngine.placeColumnsHorizontally`), which repositions the terminal barline column; a glissando sits on earlier columns, so its endpoints are identical regardless of `isLastLine`. A test built as described would not actually exercise a broken `isLastLine`.
  
4. Added an end-to-end click test in `LineSelectionHandlerTest` (`EndToEndClickResolution`, `EndToEndGraceGlissandoClick`), driving a real `LayoutEngine` layout and real `HitRegistry` rather than the hand-built one-region registry every other test in that file uses. `HitTarget` now has **fourteen** variants, not the twelve this task was written against (`Trill` and `Tuplet` were added after this task's text) — all fourteen are covered, including `GraceGlissando` and `StaffLine`.
  
5. Ran `./scripts/compile.sh` — SUCCESS. Ran `./scripts/test.sh unit` — 6706 passed, 1 skipped (pre-existing). E2E tests share the same `test` source set (filtered by path, not a separate Gradle source set), so the unit run's compile step already compiled them.

* * *
## ✅ Phase 12: Fold the Line Selection into the Target
**Status:** Complete **BlockedBy:** — **Files:** src/main/java/songscribe/ui/selection/LineSelectionState.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/component/score/LineSelectionHandler.java, src/test/java/songscribe/ui/selection/LineSelectionStateTest.java, src/test/java/songscribe/ui/selection/SelectionCoordinatorQueryGuardsTest.java, src/test/java/songscribe/ui/selection/GetSelectedLineAndElementsTest.java, src/test/java/songscribe/ui/selection/CanDeleteLineAndChangeTempoTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerTest.java, src/test/java/songscribe/ui/component/ScoreViewTest.java **Recommended model/effort:** Sonnet 4.6, medium effort — a mechanical fold with exactly one semantic hazard, named in task 3; most of the work is test churn.

`lineSelected` (`LineSelectionState:90`) and `selected` (`:94`) hold the same shape of thing — one selected item — and `HitTarget.StaffLine` already exists and is already emitted by the registry from the header region. The two fields already clear each other by hand (`:127`, `:177`); folding them makes that mutual exclusion structural, because one field can hold only one value.

Production cost is small: `setLineSelected` has exactly **one** production caller, `LineSelectionHandler.selectWholeLine` (`:481`), with `true`. Six production reads follow. The bulk of the work is roughly twenty test call sites across six classes.
### Tasks
1. Delete the `lineSelected` field (`LineSelectionState:90`) and `setLineSelected` (`:123`). Change `selectWholeLine` (`LineSelectionHandler.java:481`) to `lineSelectionState.select(new HitTarget.StaffLine())`. `select` (`:172`) already clears the range and the anchor, which is what `setLineSelected(true)` did by hand at `:127`.
  
2. Repoint the six production reads:
  

- `LineSelectionState.isLineSelected()` (`:119`) → `selected instanceof HitTarget.StaffLine`. Keep the method; it is the readable name and has five callers.
  
- `LineSelectionState.isSelected` (`:157`) — delete the `StaffLine` arm; plain equality against `selected` now answers it.
  
- `LineSelectionState.getSelection()` (`:396`) — keep the full-line `ElementSelection` synthesis verbatim, just branch on `isLineSelected()`.
  
- `LineSelectionState.selectAll()` (`:352`) — `selected = null` now clears the line selection too, so the separate assignment goes. Keep the reasoning in its javadoc (`:338-341`) and repoint the wording.
  
- `SelectionCoordinator.getSelectedLine()` (`:469`) and `triggerReflection()` (`:1319`) — both already call `state.isLineSelected()`; no change if that method is kept.
  

3. **The one place the fold is not free:** `hasDecorationSelection()` (`:165`) is `selected != null`, which is `false` for a line selection today and would silently become `true`. Redefine it as `selected != null && !(selected instanceof HitTarget.StaffLine)` and say so in its javadoc — this predicate is the reason the fold needs a phase rather than an edit. Its three callers each change behavior otherwise: `DeleteAction:60`, `SelectionCoordinator:1211`, and the undo/redo revalidation guard at `SelectionCoordinator:1275`.
  
  Audit `getSelectedTarget()` (`:138`) the same way, since it now returns a `StaffLine` where it returned `null`. `SelectionCoordinator:1339` pattern-matches `HitTarget.Slide` and is safe; check `ScoreViewController:622` before assuming the same.
  
4. Confirm — do not change — that revalidation still ignores a line selection. `revalidateDecorationSelection` (`:222`) reads `selected.owner()`, and `StaffLine.owner()` returns `null`, so `clearIfStale` is never reached for it. That is what the comment at `:223` already asserts; it becomes reachable for the first time in this phase, so add a test that a line selection survives a mutation on its own line.
  
5. Harden `isSelected` (`:154`) against a thirteenth variant: drop the `default ->` arm so the switch is exhaustive over `HitTarget`. Java permits only one pattern per `case` label, so this costs one arm per variant — eleven of them returning `target.equals(selected)`. Take that cost: the arm that silently answered `false` for a store it did not know about is the exact failure this plan hit once already, when the Phase 9 bridge deletion assumed `isSelected` answered for all three stores. If the twelve-arm switch is rejected on readability, the fallback is not "keep the default" alone — it is keep the default **and** pin every variant in the table-driven test from Phase 11 task 4.
  
6. Update the test call sites: `LineSelectionStateTest` (12 sites, including the only `setLineSelected(false)` at `:165`), `ScoreViewControllerTest` (`:686`, `:711`, `:1891`), `SelectionCoordinatorQueryGuardsTest` (`:153`, `:173`), `GetSelectedLineAndElementsTest` (`:62`), `CanDeleteLineAndChangeTempoTest` (`:133`, `:154`), `ScoreViewTest` (`:1184`). Each becomes `select(new HitTarget.StaffLine())`. The `setLineSelected(false)` case at `LineSelectionStateTest:165` has no production equivalent — decide whether it becomes `clearSelection()` or is deleted, and say which in the test's name.
  
  Add a test that selecting a target clears a line selection and vice versa **without** either code path saying so — the point of the fold is that the invariant is now structural.
  
7. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  
8. Ask the user to confirm in the running app: clicking a line header still selects the line and draws it in the selection color, Insert Line and Delete Line still enable off it, and clicking a note or a decoration still drops the line selection.
  

* * *
## ✅ Phase 13: Hoist the Target Selection to the Coordinator
**Status:** Complete **BlockedBy:** 12 **Files:** src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/selection/LineSelectionState.java, src/main/java/songscribe/ui/component/score/LineSelectionHandler.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/message/notification/MusicSelectionDidChangeNotification.java, src/main/java/songscribe/ui/action/DeleteAction.java, src/test/java/songscribe/ui/selection/SelectionCoordinatorQueryGuardsTest.java **Recommended model/effort:** Opus 4.8, high effort — moves ownership of a selection concept across two classes while the index range stays put, deletes a callback that currently enforces a cross-store invariant, and changes the shape of a message-bus notification.

`activeLineIndex` already makes the single-target selection a score-level singleton: exactly one target is selected in the whole score. The current design stores that score-wide invariant in N per-line objects and then spends `activateLine` (`:229`) and a per-state callback (`registerLineState:154`) keeping them consistent.

`lyricSelection` (`:83`) is the evidence. It is the same shape as every other target and is already a `HitTarget.Lyric` variant, but it lives on the coordinator because `LineComponent.setLine` (`:190`) constructs a **new** `LineSelectionState` on every line rebuild, so nothing held there outlives one. Hoisting `selected` up removes the store, removes the callback, and removes an ordering trap in one move: `selectLyric` (`:260-264`) works only because it calls `clearSelection()` *before* assigning `lyricSelection` — reverse those two statements today and the selection erases itself.

After this phase the per-line state holds the index range and nothing else, and `isSelected(HitTarget, int)` is a two-field comparison in one place with no delegation and no per-variant special case.
### Tasks
1. **Settle the premise before writing code.** `SelectionCoordinator:360-362` claims a lyric can be selected on a line with no registered `LineSelectionState`, but registration looks unconditional (`LineComponent:198`, `:305`), which would make the claim defensive rather than real. Determine which it is, and separately whether a lyric selection must survive a `LineComponent.setLine` rebuild (`:190`) — the `LyricEditor` path is where that would show. The answer does not change the design, but it decides what the migration must preserve, and getting it wrong loses a selection in a way only the user will see. Report the finding before proceeding.
  
2. Move to `SelectionCoordinator`: the `selected` field, `select(HitTarget)`, `getSelectedTarget()`, `hasDecorationSelection()`, `isSlideSelected(int)`, `isDecorationSelected(LineElement)`, `revalidateDecorationSelection()` and its `isOnLine` helper (`LineSelectionState:138-253`). Key them by the line they belong to — `activeLineIndex` is that line, and the hoisted `isOnLine` needs the `Line`, so resolve it through `lineStates` rather than capturing it.
  
  `LineSelectionState` keeps the range (`selectionBegin/End/Anchor`), the tie cache (`canTie`, `existingTie`), `getLine()`, and the range-shaped queries. Its class javadoc (`:44-74`) describes two concepts living side by side; rewrite it to describe one, and move the two-concept explanation to the coordinator where both now meet.
  
3. Rewrite `SelectionCoordinator.isSelected(HitTarget, int)` (`:364`) as: `lineIndex` must equal the selected line, `HitTarget.Element` delegates to that line's `isElementSelected(line.getElementIndex(element))`, every other variant is equality against the hoisted field. The `Lyric` pre-check at `:369-372` and the delegation-ordering comment at `:357-363` both go away. Keep the switch exhaustive, per Phase 12 task 5.
  
4. Fold `lyricSelection` in. `selectLyric(element, verse)` becomes `select(new HitTarget.Lyric(element, verse))`. Delete the `LyricSelection` record (`:71`), the field (`:83`), `clearLyricSelection` (`:266`), `getLyricSelection` (`:270`), `hasLyricSelection` (`:274`), and the `setSelectionChangeCallback(this::clearLyricSelection)` wiring at `:154` together with `LineSelectionState.setSelectionChangeCallback` (`:314`) and the `selectionChangeCallback` field (`:85`) if nothing else uses them.
  
  Repoint the three consumers to pattern-match `HitTarget.Lyric`: `ScoreViewController.handleDelete` (`:598-619`), `DeleteAction:58`, and `MusicSelectionDidChangeNotification` (`:34-52`). The notification is public message-bus API — read `.agents/guides/messages.md` before changing its shape, and prefer exposing the `HitTarget` over re-exporting a replacement record.
  
5. Delete the two coordinator queries that the Phase 9 bridge removal already orphaned: `isSlideSelected(int, int)` (`:323`) and `isDecorationSelected(LineElement, int)` (`:335`) have no `src/main` callers left — only `SelectionCoordinatorQueryGuardsTest`. Confirm with `jet_brains_find_referencing_symbols` on each, then delete both and the tests that exist only to cover them. Do not preserve a query solely because a test names it.
  
6. Tests: the cross-line guard tests in `SelectionCoordinatorQueryGuardsTest` move from "does the state answer for the right line" to "does the coordinator's single field answer for the right line" — the guard is the same, the mechanism is not. Add the two cases the hoist is meant to make true: a target selection survives a `LineComponent.setLine` rebuild of an unrelated line, and selecting a lyric on line B clears a target selected on line A with no callback in the picture.
  
7. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  
8. Ask the user to confirm in the running app, since this phase moves state that Phase 10 verified by hand: selecting a lyric syllable still highlights it and Delete still deletes that syllable rather than the note; selecting a decoration on one line and then clicking another line clears the first; undo/redo of a mutation that removes a selected decoration still clears the selection; and a line selection still behaves as it did after Phase 12.


## Phase 14: Wire In Deletion

Currently all of the elements can be selected, but for most pressing delete does not remove them. This will be deferred to a separate plan.

* * *
## ✅ Phase 15: One Selection, Several Shapes
**Status:** Complete **BlockedBy:** 13 **Files:** src/main/java/songscribe/ui/selection/Selection.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/selection/LineSelectionState.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/component/score/LineSelectionHandler.java, src/main/java/songscribe/ui/component/score/NoteDragHandler.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/component/ScoreInputHandler.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/action/TrillAction.java **Recommended model/effort:** Opus 4.8, high effort — 585 lines and 29 public methods move across a class boundary while the tie/beam/tuplet toggles ride on top of them, and ~24 call sites lose the object they currently reach through.

Phase 13 hoisted the single target to the coordinator and left the index range on the per-line state. That was one step short of the plan's own logic. There is exactly **one selection in the score**, and it has several shapes; storing two of those shapes in two objects is what forces every mechanism this phase deletes.

What Phase 13 had to build because the two stores are separate:

- `LineSelectionState.setRangeChangeCallback`, wired in `SelectionCoordinator.registerLineState` (`:190`) to `clearTargetSelection` — the "a range displaces a target" direction, crossing an object boundary by indirection.
  
- `LineSelectionState.clearRange()`, a second non-announcing clear, existing only so `select()` can drop the range without the callback erasing the target it is setting.
  
- A hole nothing closes: `getActiveSelection()` (`:234`) is public with ~24 production call sites outside the coordinator, so any caller can mutate a range directly and leave a stale target behind. The callback catches this by construction, which is the only reason it was kept rather than replaced by wrapper methods.
  

All three vanish when the range and the target live in one field. The exclusion stops being a rule anyone enforces and becomes what "one selection" means — the same move that made folding in `StaffLine` (Phase 12) and `lyricSelection` (Phase 13) correct.
### Two decisions already settled — do not relitigate
1. **`Selection` wraps `HitTarget`; `HitTarget` gains no `Range` variant.** The rejection recorded in *What is still stored separately after Phase 9* is correct **about `HitTarget`** and only about it: that type's contract is "a thing a click resolves to", `owner()` is meaningful for all fourteen variants, and a fifteenth that `HitRegistry.hitTest` can never produce would add an unreachable arm to every switch on the registry and renderer path. It is **not** an argument against unifying the stores, which is what this phase does.
  
2. **Immutable records, not a sealed class with a mutable `Range`.** A mutable range was considered. The deciding reason is that with immutable variants, *"the selection changed"* and *"the field was assigned"* are the same event, giving exactly one choke point — which is the entire payoff of the unification. A mutable `Range` sitting in the field lets code change what is selected without assigning anything, leaving future observers (repaint, notification, cache invalidation) nowhere single to hang: the callback problem in a new costume. Secondary: a mutable class cannot be a record, so `case Selection.Range(var line, var begin, var end)` is unavailable against a codebase whose whole selection vocabulary is deconstruction-based (`case HitTarget.Slide(var owner)`, `instanceof HitTarget.Lyric(var element, var verse)`).
  
  **Performance is not a reason to prefer mutability, and any write-up claiming otherwise is wrong.** A rubber-band drag runs `calculateLineSelectionFromDrag`, which loops the line and calls `extendSelection(i)` per intersecting element — a few dozen tiny, immediately-dead objects per drag event. Immutability also *improves* that path: instead of `clearSelection()` followed by N incremental `extendSelection` calls, compute begin/end in the loop and assign one `Range` at the end.
  

```java
public sealed interface Selection {
    record Target(HitTarget target)                        implements Selection { }
    record Range(Line line, int begin, int end, int anchor) implements Selection { }
}
```
### Tasks
1. Create `src/main/java/songscribe/ui/selection/Selection.java` as above. Give `Range` the trivial accessors that are pure functions of its own components plus its `Line` — `isEmpty()`, `size()`, `contains(int elementIndex)` (the `effectiveDeleteEnd` widening that `isElementSelected` applies today, breath mark included), `singleElement()`, `toElementSelection()`. These are the members of `LineSelectionState` that carry no music theory.
  
2. Replace `SelectionCoordinator.selected : @Nullable HitTarget` with `@Nullable Selection selected`. Rewrite the readers accordingly: `getSelectedTarget()` unwraps `Selection.Target`, `isLineSelected()`, `isSelected(HitTarget, int)`, `hasDecorationSelection()`, `revalidateDecorationSelection()`, `getSelection()`, `getSelectedLine()`, `triggerReflection()` (`:1402`).
  
  **Delete `setRangeChangeCallback`, the `rangeChangeCallback` field, `clearRange()`, and the wiring in `registerLineState`.** Their reason for existing is gone. If any of them still looks necessary, the range has not actually moved.
  
3. Rehome the range-derived music queries. `selectionBegin/End/Anchor` are read in **50 places** across `LineSelectionState`'s 585 lines, and the bulk of that is not bookkeeping: `canToggleBeaming`, `canToggleTie`, `canToggleTuplet`, `canToggleTrill`, `canModifyStemDirection`, `getNonGraceSelectionBegin/End`, `validGradesFor`, `shouldConnectBeamSelection`, `shouldConnectTieSelection`, `isTieSeparator`. These are pure functions of `(line, begin, end)` — move them to a new stateless `RangeQueries` taking a `Selection.Range`, preserving every rule verbatim. The grace-note transparency (refs #592) and the tie-separator rule (refs #527) are the two most easily broken; `LineSelectionStateTest` already pins both and must keep passing unchanged in substance.
  
4. **Delete the tie cache — dead in production, alive only in tests.** `canTie` and `existingTie` are written by `canToggleTie()` and reset by `resetTieState()` (`MusicEditOperations:271`). `getCanTie()` and `getExistingTie()` have **no production callers** on this branch or on `develop` (verified with `jet_brains_find_referencing_symbols`, not `rg`): `MusicEditOperations.toggleTie()` recomputes `line.findExactTie(beginIndex, endIndex)` for itself rather than asking.
  
  They are, however, read from **24 sites across two test classes**, so this is not a free deletion:
  
  - `LineSelectionStateTest` — 17 `getCanTie()` reads and 6 `getExistingTie()` reads. Every one of them sits directly beneath an assertion on `canToggleTie()`'s own return value (`assertThat(result).isFalse(); assertThat(state.getCanTie()).isEqualTo(false);`), so they assert nothing the line above does not. They go with the fields. `testResetTieStateClearsCanTieAndExistingTie` exists only to cover `resetTieState` and goes with it.
    
  - `ToggleConflictTest.toggleTie()` (`:136-148`) is the one real user, and it is a **test-local reimplementation of the production toggle that has drifted from it**: it calls `canToggleTie()` to populate the cache, reads `getExistingTie()`, then branches on it — while production recomputes `findExactTie` and never touches the cache. The test is therefore exercising a path the app does not have. Rewrite it to mirror `MusicEditOperations.toggleTie()`; that is a fix regardless of whether this phase happens.
    
  
  Then remove `canTie`, `existingTie`, `getCanTie`, `getExistingTie` and `resetTieState`, drop the `resetTieState()` call at `MusicEditOperations:271`, and let `canToggleTie()` become the pure query it already behaves as.
  
5. Dissolve `LineSelectionState`. After tasks 3 and 4 it holds only `line`, which `Selection.Range` already carries. What must survive is the *registry* — `lineStates` is how the coordinator maps a line index to a `Line`, and `registerLineState` / `unregisterLineState` / `getLineState` are the `LineComponent`-facing API (`LineComponent:198`, `:305`; accessor at `:223`). Reduce it to `Map<Integer, Line>` with `registerLine(int, Line)`. Note this removes the reason `LineComponent.setLine` constructs a new state per rebuild, which is what made a per-line store unable to outlive a rebuild in the first place.
  
6. Repoint the callers. The range mutators lose their receiver, so each site assigns a new `Selection.Range` through the coordinator instead:
  

| site | today |
| --- | --- |
| `SelectionCoordinator.selectSingleElement:257` | `state.setSelectionFromClick(...)` |
| `ScoreView.extendSelectionTo:739` | `state.extendSelectionTo(...)` |
| `ScoreInputHandler.applyPitchShift:416`, `:418` | `state.clearSelection()` / `state.setSelectionRange(...)` |
| `ScoreViewController.handleSelectAllElements:1232` | `state.selectAll()` |
| `LineSelectionHandler.calculateLineSelectionFromDrag:568`, `:597`, `:606`, `:614` | `clearSelection()` / `extendSelection(...)` / `setSelectionAnchor(...)` ×2 |

The drag path is the one worth rewriting rather than translating: build begin/end/anchor in the loop and assign a single `Range` at the end, replacing the clear-then-extend-N-times sequence. The readers — `getActiveSelection()`'s ~24 external call sites, twelve of them in `MusicEditOperations`, plus `ScoreViewController`, `ScoreInputHandler`, `ScoreView`, `EditLyricAction`, `TrillAction`, `NoteDragHandler:145` and `LineSelectionHandler` (`:120`, `:223`, `:363`, `:454`, `:559`) — mostly want the range or a `RangeQueries` answer, not the object.

7. Leave `ElementSelection` alone. It is already `record ElementSelection(Line, int begin, int end)`, and the three coordinator caches keyed by it (`:684`, `:709`, `:1346`, `:1475`) work because `getSelection()` builds a fresh immutable snapshot per call. They are insulated from this change and must stay that way — a cache keyed on a `Selection` directly would be the mutable-key hazard this phase's immutability decision exists to avoid.
  
8. Tests: 19 test files reference `LineSelectionState`. `LineSelectionStateTest` splits along the same seam the production code does — range-shaped assertions follow `RangeQueries`, and the callback tests (`testClearRangeResetsAllThreeFieldsWithoutFiringCallback`, `testRangeChangeCallbackFiresOnEveryRangeMutatingCall`) are **deleted, not ported**: they test a mechanism this phase removes. Add the case the unification is meant to make structural — a range and a target cannot both be set, with no code path saying so.
  
9. Run `./scripts/compile.sh` and confirm SUCCESS, then `./scripts/test.sh unit` and confirm green.
  
10. Ask the user to confirm in the running app: rubber-band drag selects the elements it covers and sets the anchor at the near end; shift+click and arrow-key extension still grow the selection from the anchor; the tie, beam and tuplet toggles enable on exactly the selections they did before; Select All still swaps a line selection for its elements; and clicking a decoration still drops an element range and vice versa.
  

* * *
### Implementation notes
1. `Selection.Range` rejects an empty or reversed span in its compact constructor. "Nothing selected" is `selected == null` and nothing else, which is what lets every `RangeQueries` method and every `Range` accessor skip an emptiness guard. `resetElementSelection()` and the bare `setSelectionAnchor()` — the only two things that could produce an anchor with no range — had no production callers and are gone with the class.

2. `getActiveSelection()` split into **two** accessors rather than one, because "which line is active" turned out to be independent of the selection's shape. Three readers inside `SelectionCoordinator` need the active `Line` precisely when a *target* is selected: `getSelection()` synthesizing a full-line span for `StaffLine`, `revalidateDecorationSelection()`, and `songDidChangeReflectSelection()`. A range-only accessor answers null for all three.
   - `getActiveLine()` — `lines.get(activeLineIndex)`, any shape.
   - `getRange()` — the selected `Selection.Range`, or null.

   `Selection` itself deliberately has **no** `line()` member: the line registry is the one source of truth for it, and `HitTarget.StaffLine` has no owner to ask.

3. `activateLine` now clears unconditionally, where it previously cleared the target always and the range only when switching lines. With one field that distinction would mean the *shape* of the selection decided whether it survived. No production caller depended on it — both (`prepareSelection`, `calculateLineSelectionFromDrag`) clear or assign immediately.

4. `clearActiveSelection()` was added beside `clearSelection()`: drops what is selected but leaves the line active. It is what a rubber band that caught nothing and both revalidation paths leave behind — behavior the old `state.clearSelection()` had by virtue of living on the line.

5. The drag path now computes begin/end in the loop and assigns one `Range` at the end, replacing clear-then-extend-N-times. `dragAnchor` was extracted for the "which end did the drag start from" rule.

6. Task 4's tie cache is gone. `ToggleConflictTest.toggleTie()` was rewritten to mirror `MusicEditOperations.toggleTie` — it looks the tie up with `findExactTie` instead of reading a cache production never touched.

7. `LineSelectionStateTest` (1311 lines) split along the production seam:
   - `RangeQueriesTest` — the pure queries plus the `Selection.Range` accessors.
   - `SelectionCoordinatorRangeTest` — selecting, extending, clearing, revalidating, `selectAll`, and the "nothing selected" answers a range can no longer express.

   Deleted rather than ported: `testClearRangeResetsAllThreeFieldsWithoutFiringCallback`, `testRangeChangeCallbackFiresOnEveryRangeMutatingCall`, `testResetElementSelection…`, `testResetTieStateClearsCanTieAndExistingTie`, and 23 `getCanTie()`/`getExistingTie()` assertions that each sat under an assertion on `canToggleTie()`'s own return value. Added `testARangeAndATargetCannotBothBeSelected`, which is the invariant the phase makes structural.

8. `SelectionCoordinatorRegistryTest`'s row-1 test changed subject rather than being deleted: it asserted the range-change callback cleared the target; it now asserts that selecting a range displaces a target with no callback in the picture.

9. Ran `./scripts/compile.sh` — SUCCESS. Ran `./scripts/test.sh unit` — 6712 passed, 1 skipped (pre-existing), up from 6706 at Phase 11.

**Not done, reported instead:** `unregisterLine`/`clearLines` have no `src/main` callers (they had none as `unregisterLineState`/`clearLineStates` either) — kept as the lifecycle counterpart of `registerLine` rather than deleted, since without them a shrinking score has no way to drop stale index→line entries. Pre-existing; out of scope here.
