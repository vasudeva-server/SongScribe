# Tuplet Bracket Stem Trimming

**Issue:** #509  <br>
**Base branch:** `develop`  <br>
**Branch:** `509-tuplet-rendering`  <br>
**Created:** 2026-07-06

Invert the tuplet bracket ↔ stem relationship. Today the bracket is pushed **up**
to clear every interior note's **natural** stem tip
(`computeTupletClearanceLeftYSs` mins over `getAbsoluteTopYSs()`), so an interior
up-stem note floats the whole bracket above it. Correct engraving (and LilyPond)
holds the bracket at its clamped max slope and **shortens** interior up-stems that
would poke through it — never lengthening. When a stem would have to shorten below
a minimum length, the **bracket yields** (rises) for that note.

New model, in dependency order:
1. Bracket clearance uses each note's **minimum-stem tip** (`noteheadTop −
   MIN_TRIMMED_STEM_SS` for an up-stem toward the bracket; `getAbsoluteTopYSs()`
   otherwise), so the bracket only rises when a stem truly can't reach.
2. Once the bracket line is fixed, a **trim pass** shortens each spanned unbeamed
   up-stem note whose natural tip is above the bracket line down to meet it.
3. The trim rides through `StemLayout` → `NoteRenderer`; slope Steps 1–3 are
   untouched.

## Coordinate convention (unchanged from angled-brackets work)

- Layout Y: staff-space, middle line = 0, **negative = up** (higher on page =
  smaller Y). Bracket is **always above** the staff (`dir = UP`).
- Up-stem tip is **above** the notehead: `tipY = staffPos·OFFSET − stemLength`
  (smaller Y). A shorter stem → **larger** (lower) tip Y.
- `ElementColumn.getStemTopSs()` (post Phase 1b of the prior plan): up-stem →
  `−STEM_LENGTH_SS` (natural tip); down-stem / stemless → `−HALF_NOTE_HEAD_SS`
  (notehead top). `getAbsoluteTopYSs() = staffPos·STAFF_POSITION_OFFSET_SS +
  getStemTopSs()`.

## Key constants (verified)

- `SMuFLConstants.STEM_LENGTH_SS = 3.5` — natural stem length (renderer base via
  `NoteGeometry.computeBaseStemGeometry`).
- `LayoutEngine.MIN_STEM_SS = STEM_LENGTH_SS` (`3.5`) — **misnamed**: it is the
  natural length, not a floor. A real floor must be added (`MIN_TRIMMED_STEM_SS =
  2.5`); do **not** repurpose `MIN_STEM_SS`.
- `StructuralStacker.TUPLET_MARGIN_SS = 0.625` — the gap `stackAboveAtAnchor`
  applies above the raw clearance ceiling.

## Touchpoints (verified)

| Stage | File / symbol |
|-------|---------------|
| New constant | `dom/Tuplet.java` → add `MIN_TRIMMED_STEM_SS = 2.5` |
| Trim channel | `layout/LayoutResult.java` → `StemLayout` record (+ builder `getStemLayout` / `putStemLayout`, both exist) |
| Render trim | `ui/renderer/NoteRenderer.java` → `renderStem` (`stemLength = geom.lengthSs() + lengtheningSs`, returns tip `Point2D`) |
| Existing StemLayout sites | `layout/LayoutEngine.java` → beamed loop (~579) and `calculateUnbeamedStems` (~644) |
| Clearance rework | `layout/stacking/StructuralStacker.java` → `computeTupletClearanceLeftYSs` |
| Trim pass | `layout/stacking/StructuralStacker.java` → `stackTuplets` (has `builder`, `nonRestColumns`, `finalLeftYSs`, `dySs`, `anchorXSs`, `widthSs`) |
| Per-note geometry | `layout/ElementColumn.java` → `getStemTopSs`, `getAbsoluteTopYSs`, `getXSs`, `getElement`, `isBeamed`, `isRest` |
| Direction | `StaffElement.getDirection().isUp()` (bracket above ⇒ up-stem = toward bracket) |

**Scope guards.** Trimming applies to **unbeamed up-stem** spanned notes only.
Beamed notes keep beam-driven stems; the bracket clears them via
`getAbsoluteTopYSs()` (unchanged path). Down-stem / stemless notes are never
trimmed (their obstacle stays the notehead top). Slope computation
(`computeTupletSlopeDySs`, Steps 1–3) is unchanged.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Trim data channel](#-phase-1-trim-data-channel) | ✅ Complete | — |
| 2 | [Clearance inversion & trim pass](#-phase-2-clearance-inversion--trim-pass) | ✅ Complete | — |
| 3 | [Tests](#-phase-3-tests) | ✅ Complete | — |

---

## ✅ Phase 1: Trim data channel

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low — one constant, one record field
with defaulted call sites, and a subtraction in the renderer; zero behavior change
(`bracketTrimSs` defaults `0.0`), compile gates correctness.

Establish the `bracketTrimSs` channel so the pipeline compiles and renders exactly
as today (no trim anywhere). No visual change in this phase.

### Tasks

1. `dom/Tuplet.java` — add, next to `BRACKET_ARM_HEIGHT_SS`:
   `public static final double MIN_TRIMMED_STEM_SS = 2.5;` with a Javadoc noting
   it is the floor a stem may be shortened to when meeting a tuplet bracket, below
   which the bracket yields.
2. `layout/LayoutResult.java` — add a `double bracketTrimSs` component to the
   `StemLayout` record **immediately after `lengtheningSs`** (canonical order:
   `topYSs, bottomYSs, lengtheningSs, bracketTrimSs, stubRight`). Document it: the
   amount (≥ 0, ss) to shorten this stem so its tip meets the tuplet bracket; `0`
   for every non-trimmed stem. Do **not** relax `lengtheningSs` (stays ≥ 0).
3. `layout/LayoutEngine.java` — update both existing `StemLayout` construction
   sites (beamed loop ~579, `calculateUnbeamedStems` ~644) to pass
   `bracketTrimSs = 0.0`.
4. `ui/renderer/NoteRenderer.java` — in `renderStem`, read `bracketTrimSs` from the
   `stemLayout` (null-safe, default `0.0`) and apply it: `stemLength =
   geom.lengthSs() + lengtheningSs − bracketTrimSs`, floored so `stemLength ≥
   Tuplet.MIN_TRIMMED_STEM_SS` (defensive). Both the drawn stem (`drawTop`/
   `drawBottom`) and the returned tip `Point2D` use the trimmed `stemLength`. The
   `beamInsetSs` tuck is unaffected (trim is 0 for beamed notes).
5. Grep for any other `new LayoutResult.StemLayout(` sites (production + test) and
   add the `0.0` argument. Then `./scripts/compile.sh` → SUCCESS.

---

## ✅ Phase 2: Clearance inversion & trim pass

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Opus 4.8, high — the conceptual core: an effective
min-stem obstacle in up-negative Y, a trim pass that must respect the same margin
chain the clearance uses so the floor is never breached, and a `StemLayout`
read-modify-write on the builder.

Lower the bracket onto the min-stem obstacle, then trim interior up-stems down to
the fixed bracket line. After this phase the slope is visibly clamped and interior
stems meet the bracket instead of floating it up.

### Tasks

1. `layout/stacking/StructuralStacker.java` — rework
   `computeTupletClearanceLeftYSs` to use an **effective obstacle top** per column
   instead of `getAbsoluteTopYSs()`:
   - unbeamed **up-stem** (toward the bracket): `column.getElement()
     .getStaffPosition() * Staff.STAFF_POSITION_OFFSET_SS − Tuplet.MIN_TRIMMED_STEM_SS`
     (the min-stem tip — highest the bracket must clear, since the stem can trim
     down to it);
   - otherwise (down-stem, stemless, or **beamed**): `getAbsoluteTopYSs()`
     (unchanged).

     Keep the existing `Math.min(…, StackingUtils.STAFF_TOP_Y_SS)` staff-top clamp
     and the `min`-over-columns projection back to the left endpoint. Update the
     Javadoc/ASCII diagram to show the bracket resting on the min-stem tip with the
     natural stem trimmed down to the line.
2. In `stackTuplets`, immediately after `finalLeftYSs` is resolved and **before**
   the `builder.putDecorationLayout` call, add a **trim pass** over
   `nonRestColumns`. For each **unbeamed up-stem** column:
   - bracket-line Y at the column: `bracketLineYSs = finalLeftYSs +
     Tuplet.bracketLineOffsetSs() + slope * (column.getXSs() − anchorXSs)` where
     `slope = dySs / widthSs` (guard `widthSs == 0`);
   - natural tip Y: `naturalTipYSs = staffPos·OFFSET − STEM_LENGTH_SS`;
   - if `bracketLineYSs > naturalTipYSs` (bracket sits **below** the natural tip ⇒
     stem pokes through), trim so the tip meets the line:
     `trimSs = bracketLineYSs − naturalTipYSs`, then clamp so the resulting length
     `STEM_LENGTH_SS − trimSs ≥ Tuplet.MIN_TRIMMED_STEM_SS`; else `trimSs = 0`.
3. Write the trim back via the builder: `var stem = builder.getStemLayout(element)`;
   if non-null and `trimSs > 0`, `builder.putStemLayout(element, new StemLayout(…
   trimmedTopYSs, bottomYSs, lengtheningSs, trimSs, stubRight))`, recomputing
   `topYSs` to the trimmed tip (`naturalTipYSs + trimSs`) so the record stays
   self-consistent for downstream stem-layout consumers (e.g.
   `NoteAttachedStacker`). `bottomYSs`, `lengtheningSs`, `stubRight` pass through.
4. `./scripts/compile.sh` → SUCCESS, then `./scripts/run.sh` — visually confirm on
   a descending tuplet (high → middle → low, middle up-stem) that the bracket holds
   the clamped slope and the middle stem trims to the line rather than floating the
   bracket up. Flat and ascending tuplets unchanged. (Visual/e2e approval deferred
   to user.)

### Flagged uncertainties (resolve during implementation)

1. **Bracket-line reference Y.** Task 2 uses `finalLeftYSs +
   bracketLineOffsetSs()` as the line the stem meets — mirror `TupletRenderer`'s
   layout-space bracket line exactly (see the debug block in `logTupletPlacement`:
   `bracketLineLeftYSs = finalLeftYSs + lineOffsetSs`). If the renderer's true line
   differs, trimmed tips will sit slightly off; verify the two use the identical
   expression. Recommended: extract the bracket-line-at-X formula so clearance,
   trim, and render share one method.
2. **Floor breach guard.** The min-stem obstacle in task 1 should make the task-2
   clamp a no-op for the binding note (`STEM_LENGTH_SS − trimSs == MIN_TRIMMED_STEM_SS`
   exactly), because clearance placed the bracket `TUPLET_MARGIN_SS` above the
   min-stem tip. Confirm the margin chain lines up; if the clamp ever fires
   non-trivially, the obstacle and the line reference are inconsistent — fix the
   reference, don't just clamp.

---

## ✅ Phase 3: Tests

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Recommended model/effort:** Sonnet 4.6 or Haiku 4.5, low — table-driven unit
tests against the reworked pure clearance helper plus a render-level trim
assertion. Read `.agents/guides/testing-common.md` and `testing-unit.md` first.

### Tasks

1. `computeTupletClearanceLeftYSs` (reworked): an interior unbeamed up-stem note
   makes the bracket sit on its **min-stem tip** (ceiling higher/lower than the old
   natural-tip result by exactly `STEM_LENGTH_SS − MIN_TRIMMED_STEM_SS` at that
   column); a down-stem / beamed interior note still uses `getAbsoluteTopYSs()`.
2. Bracket-yields case: a note whose min-stem tip is the binding obstacle forces
   the ceiling up (the bracket rises) rather than trimming past the floor.
3. Trim pass (render-level, extend `TupletRendererTest` or a stacker test): an
   interior up-stem note that pokes through gets `bracketTrimSs > 0` with the
   trimmed tip on the bracket line; the binding note trims to exactly
   `MIN_TRIMMED_STEM_SS`; down-stem, beamed, and endpoint notes get
   `bracketTrimSs == 0`; a flat tuplet trims nothing.
4. Drag preservation: `applyDecorationOffsets` on a sloped, trimmed tuplet still
   shifts `ySs` by the offset with `dySs` unchanged (trim is recomputed from the
   bracket, not stored on the decoration).
5. `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh <TestClasses>` → green
   (unit target).

---

## Verification (whole plan)

- `./scripts/compile.sh` → SUCCESS after every phase.
- Phase 3 unit tests green.
- `./scripts/run.sh` on a score with descending, ascending, and flat tuplets:
  - Descending tuplet with a tall interior up-stem: bracket holds the clamped slope;
    the interior stem trims to the line (matches LilyPond image 4), no bracket float.
  - A note that can't trim below `2.5 ss`: the bracket yields (rises) for it.
  - Endpoints, down-stems, beamed notes, and flat tuplets render as before.
  - Dragging a tuplet vertically translates the bracket rigidly; trims follow.
