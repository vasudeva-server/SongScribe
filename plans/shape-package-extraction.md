# `songscribe.shape` Package — Custom Shape Extraction

Establish `songscribe.shape` as the home for **font-independent, programmatically-built notation
shapes** (as opposed to SMuFL font glyphs drawn via `drawGlyphVector`/`drawString`, and as opposed
to generic geometry helpers in `util/GraphicUtils`). `AccentGlyph` is the first tenant. This plan
extracts the shape geometry for **tie, hairpin, and ending** out of their renderers into peer
providers in the same package.

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 0 | [Create package + move `AccentGlyph`](#-phase-0-package--accentglyph) | ✅ Done (this branch) |
| 1 | [`TieShape` extraction](#-phase-1-tieshape) | ⬜ Not started |
| 2 | [`EndingBracketShape` extraction](#-phase-2-endingbracketshape) | ⬜ Not started |
| 3 | [`HairpinShape` extraction](#-phase-3-hairpinshape) | ⬜ Not started |

> Phases 1–3 are independent of one another and of the accent feature. Each is a self-contained,
> behavior-preserving refactor and should land as its own commit (ideally its own branch off
> `develop`), **not** on the accent branch.

## Why a `shape` package (settled)

- **Name.** `shape` describes *what the code produces* (a `java.awt.Shape`) without implying SMuFL
  conformance. `AccentGlyph` is a LilyPond metafont transcription, not a spec glyph, so `smufl`
  would have been misleading; `glyph` was the runner-up but `shape` is more honest about the
  contents (some tenants are brackets/hairpins, not "glyphs").
- **Layering.** `shape` is a **leaf package** — it depends only on `java.desktop` (`Path2D`,
  `Point2D`, `Arc2D`, `Line2D`, …) and primitives. It must **not** import from `ui`, `ui.renderer`,
  `layout`, or `dom`. This is what lets both `dom.Articulation` (for bounds) and
  `ui.renderer.*` (for drawing) depend on it without a back-edge, exactly mirroring how both
  already depend on `smufl`.

## Two categories of tenant

The package holds two structurally different kinds of provider. Keep the distinction explicit:

1. **Pure cached outlines** — no inputs, unit-space geometry, cached in a `static final Shape`.
   `AccentGlyph.accent()` is the archetype. These are true font-independent glyphs.
2. **Parameterized shape builders** — take plain geometry (doubles / `Point2D`) and return a
   freshly-built `Shape` (or array). Tie, hairpin, and ending fall here: their geometry today comes
   from layout results, so the **renderer resolves layout → plain coordinates, then calls the shape
   builder**. The builder itself stays free of layout/renderer types.

## Design contract for the parameterized builders (settled)

- **Inputs are primitives only.** The renderer unpacks `LayoutResult.*` records and
  `LineInvariants`/coordinate transforms *before* the call. The builder signature takes `double`s
  and `Point2D`s — never `TieLayout`, `DecorationLayout`, `BracketRange`, or `LineInvariants`. This
  keeps `shape` a leaf.
- **Behavior-preserving.** Each extraction produces byte-identical geometry to today. No visual
  change. (The hairpin *rework* to follow LilyPond is a separate, later change — this plan only
  moves the seam so that rework has one home.)
- **Return type follows current stroking.** Where a renderer fills an outline, the builder returns
  a `Shape`. Where a renderer strokes a polyline via `GraphicUtils.drawPath` (ending) or draws
  discrete segments (hairpin), the builder returns the geometry the existing stroking code expects
  (`Point2D[]` / `Line2D.Double[]`) so the stroking path is untouched.

## Phases

### ✅ Phase 0: package + `AccentGlyph`

- Created `src/main/java/songscribe/shape/package-info.java` (`@NullMarked`, mirroring `dom`).
- Moved `AccentGlyph` `dom → shape` via IDE move; imports in `Articulation`, `ArticulationRenderer`,
  `ArticulationTest`, `ArticulationStackingTest` updated automatically. Compiles clean.

### ⬜ Phase 1: `TieShape`

**Source:** `ui/renderer/TieRenderer.renderTie` builds a `GeneralPath` (two cubic Béziers forming a
lens) from control points already computed in `LayoutResult.TieLayout`, then `g2.fill`s it.

- Add `TieShape.build(...)` in `shape`, taking the eight-plus control-point coordinates
  (start, cp1, cp2, end, innerCp1, innerCp2) as `double`s (or `Point2D`s) and returning the filled
  lens `Shape`. No `TieLayout` import.
- `renderTie` reads `TieLayout` as today, passes its fields to `TieShape.build`, and fills the
  result. Everything else in `renderTie` (color, transform, null-layout early return) stays put.
- **Verify:** `./scripts/compile.sh`, then existing tie renderer/parity unit tests.

### ⬜ Phase 2: `EndingBracketShape`

**Source:** `ui/renderer/EndingRenderer.drawEnding` builds the bracket as an `ArrayList<Point2D>`
(left leg up, across the top, optional right closing stroke), then strokes it via
`GraphicUtils.drawPath`. The method also draws the volta label glyph.

- Add `EndingBracketShape.points(x1, x2, yTopSs, yBottomSs, hasClosingStroke)` in `shape` returning
  `Point2D[]` (bracket corners only). This is the extractable geometry.
- `drawEnding` calls it, then keeps its existing `GraphicUtils.drawPath` stroking and **all** label
  glyph drawing (the `ENDING_FONT` / `drawGlyphVector` block stays in the renderer — it is font-glyph
  drawing, not a shape).
- **Verify:** compile + ending renderer unit tests.

### ⬜ Phase 3: `HairpinShape`

**Source:** `ui/renderer/DynamicsRenderer.computeHairpinLines` is already a `static` provider
returning `Line2D.Double[]` (two segments; crescendo opens right, diminuendo opens left). It derives
`x1/x2/top/bottom/middle` from `DecorationLayout` + a `LineInvariants` Y transform.

- Move the pure geometry into `HairpinShape` in `shape`, taking resolved `x1, x2, topYSs,
  bottomYSs, middleYSs` (or `x1, x2, topYSs, heightSs`) and `isCrescendo`, returning
  `Line2D.Double[]`. The `RenderingUtils.layoutYToComponentYSs` call and `DecorationLayout` unpacking
  stay in `DynamicsRenderer`.
- **This is the payoff the user called out:** even though hairpin engraving will likely be rewritten
  to follow LilyPond (from two straight lines to a tapered filled outline, like the accent), giving
  it a single home in `shape` now means that rework changes one class with a stable call site
  instead of surgery inside `DynamicsRenderer`.
- **Verify:** compile + dynamics/hairpin renderer unit tests.

## Out of scope / not moving

- **SMuFL font-glyph drawing** (noteheads, flags, clefs, key sigs, fermata, trill, staccato,
  metronome, dynamics text, slide fall, annotations, lyrics) — these draw font characters, not
  built geometry. Stay in their renderers.
- **`util/GraphicUtils`** (`drawPath`, `drawRoundedLine`, `inkHeight`) — symbol-agnostic stroking
  infrastructure the builders rely on; stays generic.
- **`BeamGroupRenderer.drawBeam`** (a filled parallelogram) and **`NoteRenderer.renderStem`**
  (rounded-rect stem) — reconsider later; low value since the geometry is a trivial quad/rect built
  inline. Not part of this plan.
- **Layout `Area` math** (`ElementBoundsSs`, `LayoutAccumulator`) — collision/bounds, not glyphs.

## Testing note

Unit tests only (`./scripts/test.sh`, `./scripts/compile.sh` first). Each phase is
behavior-preserving, so existing renderer/parity tests are the guard. E2e requires user approval —
not needed for these refactors.
