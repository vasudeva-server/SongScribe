# Angled Tuplet Brackets (LilyPond-style)

**Issue:** #509  <br>
**Base branch:** `develop`  <br>
**Branch:** `509-tuplet-rendering`

Port LilyPond's tuplet-bracket slope algorithm (`lily/tuplet-bracket.cc`,
`Tuplet_bracket::calc_position_and_height`) to SongScribe's constrained case:
**always above the staff** (`dir = UP`), **no beam-following**, **no chords**
(one note per column). Brackets currently draw flat (one `bracketYSs` feeds both
arms); the goal is a bracket that follows the note contour, clamped to a max
slope, clearing every spanned note tip.

**"No beam-following"** means: the bracket slope is always computed from the note
contour and **never adopted from a beam's slope** — LilyPond's beam-slope branch
(Step 3) is omitted. It does *not* mean brackets never coincide with beams: a
down-stemmed beamed tuplet still draws a bracket (`Tuplet.isNumberOnly` suppresses
the bracket only when the tuplet is beamed at both ends **and** stems are up). All
such cases get the contour-computed slope like any other.

## Coordinate convention

- SongScribe layout Y: staff-space, middle line = 0, **negative = up** (higher
  on the page = smaller Y).
- LilyPond uses up = positive with a `dir` multiplier. Porting: drop `dir`
  (always UP) and flip the sign — LilyPond "raise" (`+ padding`) becomes
  **subtract** in SongScribe.
- `StaffElement.getStaffPosition()` (inherited by `Note`): diatonic half-step
  count, **increases downward** (B4 = 0, A4 = +1, C5 = −1). A higher note has a
  smaller/negative staff position — same sign direction as a higher stem top.

## Touchpoints (verified)

| Stage | File / symbol |
|-------|---------------|
| Compute slope | `layout/stacking/StructuralStacker.java` → `stackSpanElement` (caller `stackTuplets` has the `Line`) |
| Vertical placement | `layout/stacking/StackingUtils.java` → `stackAtAnchor` / `stackAbove` (+ new `stackAboveAtAnchor(double)`; the private `stackAtAnchor(double)` and shared `placeAndReserve` now require a `Neighbor` tag — develop @ `29866388`) |
| Carry data | `layout/LayoutResult.java` → `DecorationLayout` record |
| Preserve under drag | `layout/stacking/VerticalStackingCalculator.java` → `applyDecorationOffsets` |
| Render | `ui/renderer/TupletRenderer.java` → `renderTupletsFromLine` / `renderTuplet` |
| Geometry | `shape/TupletBracketShape.java` → `leftArm` / `rightArm` |
| Constants | `dom/Tuplet.java` (`ARM_EXTENSION_SS`, `BRACKET_ARM_HEIGHT_SS`) |
| Per-note geometry | `layout/ElementColumn.java` → `getStemTopSs`, `getXSs`, `isRest`, `getElement` |
| Span indices | `dom/RangeElement.java` → `getAnchorElementIndex` / `getEndElementIndex`; `dom/Line.java` → `getElement(i)` |
| Drag handler | `ui/adjustment/VerticalAdjustment.java` → `adjustTuplet` (unchanged) |

**Core obstacle:** vertical position is a single scalar at every stage
(`stackAtAnchor` → one `elementTopYSs`; `DecorationLayout` → one `ySs`;
`applyDecorationOffsets` → shifts that one `ySs`; `renderTuplet` → one
`bracketYSs`; `TupletBracketShape.leftArm/rightArm` → one `bracketYSs`). The work
threads a slope (`dySs`, rise over the `widthSs` run from anchor to end element
column) alongside the existing `ySs` through all five stages.

**`DecorationLayout` construction sites (verified against develop @ `29866388` —
the record change ripples here).** The 5-arg form is built in
`StackingUtils.placeAndReserve` (the shared placement core the old `stackBeyond`
and `stackAtAnchor` now delegate to) and `stackAtCenter`; the 6-arg with-regions
form in `StackingUtils.stackAboveWithRegions` and
`VerticalStackingCalculator.applyDecorationOffsets`. Six test files also
construct the 5-arg form (`TupletRendererTest`, `LayoutResultTest`,
`AnnotationRendererTest`, `TempoChangeRendererTest`, `EndingRendererTest`,
`DynamicsRendererTest`). The 5-arg sites route through the retained convenience
ctor unchanged; the two 6-arg sites must be edited to the explicit canonical ctor
(see Phase 1a).

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1a | [Slope data channel & constant](#-phase-1a-slope-data-channel--constant) | ✅ Complete | — |
| 1b | [Stem-extent fix & absolute-top accessor](#-phase-1b-stem-extent-fix--absolute-top-accessor) | ✅ Complete | — |
| 2 | [Slope computation (Steps 1–3)](#-phase-2-slope-computation-steps-13) | ✅ Complete | — |
| 3 | [Sloped vertical clearance (Step 4)](#-phase-3-sloped-vertical-clearance-step-4) | ✅ Complete | — |
| 4 | [Render sloped bracket](#-phase-4-render-sloped-bracket) | ✅ Complete | — |
| 5 | [Tests](#-phase-5-tests) | ✅ Complete | — |

---

## ✅ Phase 1a: Slope data channel & constant

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low — record-field addition with one
convenience ctor, two explicit ctor-site edits, and a new constant; zero runtime
behavior change (`dySs` defaults to 0.0 = flat everywhere), compile gates
correctness. Independent of Phase 1b (different files) — may run in parallel.

Establish the `dySs` slope channel with a default of `0.0` so the whole pipeline
compiles and behaves exactly as today (all brackets still flat). No visual change
in this phase.

### Tasks

1. `layout/LayoutResult.java` — `DecorationLayout` record: insert a
   `double dySs` component **immediately after `ySs`** (canonical order becomes
   `xSs, ySs, dySs, widthSs, heightSs, marginSs, regions`). Keep **only** the
   existing 5-arg convenience ctor, now defaulting `dySs = 0.0`:
   - `(xSs, ySs, widthSs, heightSs, marginSs)` → `dySs = 0.0`, `regions = List.of()`

   **Do not add a 6-arg (with-regions) convenience ctor.** The with-regions sites
   are the drag path and must be forced to state `dySs` explicitly — a defaulting
   6-arg ctor would silently flatten every dragged bracket. The 5-arg sites
   (`placeAndReserve`, `stackAtCenter`, and the six test files) compile unchanged
   via the convenience ctor.
2. `layout/stacking/StackingUtils.java` — `stackAboveWithRegions`: it builds the
   6-arg with-regions form today; change it to the canonical ctor passing
   `dySs = 0.0` (non-tuplet decorations are never sloped).
3. `layout/stacking/VerticalStackingCalculator.java` — `applyDecorationOffsets`:
   change the rebuild to the canonical ctor and pass `layout.dySs()` through
   **unchanged** (the `yOffsetSs` translation shifts `ySs` only). This preserves
   the slope under a uniform user drag — a translated sloped bracket stays sloped.
   (With no defaulting 6-arg ctor this is now a compile error until fixed, which
   is the point.)
4. `dom/Tuplet.java` — add, next to `ARM_EXTENSION_SS` / `BRACKET_ARM_HEIGHT_SS`:
   `public static final double MAX_SLOPE_FACTOR = 0.5;`. (No slope-padding
   constant: the per-note clearance in Phase 3 reuses the tuplet's existing
   `marginSs`, so sloped brackets keep the same gap above notes as flat ones.)
5. `./scripts/compile.sh` → SUCCESS. Verify all four production `DecorationLayout`
   sites (`placeAndReserve`, `stackAtCenter`, `stackAboveWithRegions`,
   `applyDecorationOffsets`) and the six test files compile.

---

## ✅ Phase 1b: Stem-extent fix & absolute-top accessor

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low–medium — a symmetric accessor
inversion fix, its blessing-test correction, and a small accessor. Independent of
Phase 1a (different files) — may run in parallel.

Fix a latent inversion in the stem-extent accessors that Phase 2 depends on, and
add the absolute-top accessor the contour needs. No visual change:
`getStemTopSs`/`getStemBottomSs` have **no production readers** (the only callers
are the `StemGeometry` unit tests, corrected here), so the fix regresses nothing
at runtime.

### Tasks

1. `layout/ElementColumnBuilder.java` — **fix the inverted stem extents** (verified
   bug in **both** methods). `calculateStemTopSs` currently returns `-STEM_LENGTH_SS`
   on the `isDown()` (stem-down) branch and `-HALF_NOTE_HEAD_SS` on the stem-up
   branch — swapped relative to the Javadoc (`Direction.isDown()` is stem-down per
   `NoteGeometry.computeBaseStemGeometry` / `renderStem`). `calculateStemBottomSs`
   has the identical mirrored bug (`isUp()` branch returns `+STEM_LENGTH_SS`).
   Correct both so:
   - **stem-up** → top `-STEM_LENGTH_SS` (stem tip), bottom `+HALF_NOTE_HEAD_SS`;
   - **stem-down** → top `-HALF_NOTE_HEAD_SS` (notehead top), bottom `+STEM_LENGTH_SS`;
   - **stemless/rest** → top `-HALF_NOTE_HEAD_SS`, bottom `+HALF_NOTE_HEAD_SS`.

   Fix the crossed inline comments (`// Stem up:` currently sits above the
   `isDown()` branch), and add a short ASCII diagram of the two stem-direction tip
   extents in the Javadoc. After this, `getStemTopSs()` is the true note-local top
   extent.
2. **Correct the existing blessing tests.** `ElementColumnBuilderTest`
   → `StemGeometry` nested class (~lines 406–448) currently asserts the *inverted*
   values, and its test names/comments are themselves mislabeled (`testStemTopSsStemUp`
   feeds a stem-*down* default note). Update the asserted values **and** the
   names/comments to match the corrected geometry above, in this phase, so the fix
   and its tests land atomically. These are the only current callers of
   `getStemTopSs`/`getStemBottomSs` (no production readers yet), so runtime behavior
   is unaffected; only these tests change.
3. `layout/ElementColumn.java` — add `getAbsoluteTopYSs()`:
   `getElement().getStaffPosition() * Staff.STAFF_POSITION_OFFSET_SS + getStemTopSs()`.
   Single source of truth for the absolute layout-Y top; used by Phases 2 and 3.
4. `./scripts/compile.sh` → SUCCESS.

---

## ✅ Phase 2: Slope computation (Steps 1–3)

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b  <br>
**Recommended model/effort:** Opus 4.8, high — sign-convention reasoning across
two coordinate systems, rest-skipping, and a veto with multiple branches;
extraction for testability.

Populate `dySs` (the LilyPond raw-rise → contour-veto → slope-clamp pipeline) in
the stacker and carry it into the `DecorationLayout`. **Keep the flat `ySs`
placement exactly as today** — the box top is unchanged; only `dySs` becomes
nonzero. The renderer is not yet updated, so there is still no visual change; the
output is verified by unit test in Phase 5.

**Resolved design:**
- **Compute in `stackTuplets`, keep shared methods generic.**
  `stackSpanElement` is shared with crescendo/diminuendo and `stackAbove` with
  ~7 decoration types — neither gets slope params. In `stackTuplets` (which has
  the `Line` and `columnsByElement`), enumerate the span by index
  `[getAnchorElementIndex() … getEndElementIndex()]`, map `line.getElement(i)`
  through `columnsByElement`, and build the ordered spanned column list **once**
  (reused by Phase 3's clearance helper). Compute `dySs` there and set it on the
  tuplet's `DecorationLayout`.
- **Rest-skip for the contour.** Editor-created tuplets are all pitched
  (`LineSelectionState.canToggleTuplet` rejects non-pitched), but MusicXML-imported
  and native-loaded tuplets can have rest endpoints/interior rests
  (`RangeSpanResolver.resolveTuplet` runs on every `<note>`, including `<rest/>`).
  Skip leading/trailing rest columns via `ElementColumn.isRest()` to find the outer
  **non-rest** end columns (mirror LilyPond `get_bounds`). **Defensive early-out:**
  if fewer than two non-rest columns remain (all-rest span, or a degenerate
  single-non-rest tuplet), `dySs = 0.0`. This one-line guard also avoids the
  `runSs = 0` → `NaN` bracket Y a degenerate tuplet would otherwise produce.
  (Such tuplets are additionally rejected on load per **#518**; the early-out
  keeps the renderer robust regardless.)
- **Single run basis: `widthSs`, origin `anchorXSs`.** The renderer only has
  the box (`decorLayout.xSs()` = anchor element X, `widthSs` = anchor→end element
  run). To keep Phase 2/3/4 consistent, express the result as a **slope** and set
  `dySs = slope × widthSs`. The contour direction/magnitude comes from the non-rest
  tips; the run denominator is always `widthSs`.
- **Absolute tops via `getAbsoluteTopYSs()`.** `getStemTopSs()` is note-local;
  the contour is pitch-driven. Use `ElementColumn.getAbsoluteTopYSs()` (added in
  Phase 1b) rather than re-inlining the formula.

### Tasks

1. Implement Steps 1–3 as a **pure, package-visible static helper** — input: the
   ordered non-rest spanned `ElementColumn`s plus `anchorXSs` and `widthSs`;
   output: `dySs`. Unit-testable independently of the stacker. Call it from
   `StructuralStacker.stackTuplets`.
2. Determine the outer non-rest end columns from the pre-built list (leading/trailing
   rests already excluded). Fewer than two non-rest columns (all-rest span or a
   single non-rest column) → return `dySs = 0.0` (defensive early-out).
3. Step 1 — raw contour slope from **absolute** tops (`getAbsoluteTopYSs()`):
   `tipRise = topYSs(rightEnd) − topYSs(leftEnd)`;
   `tipRun = rightEnd.getXSs() − leftEnd.getXSs()`; `slope = tipRise / tipRun`.
4. Step 2 — contour veto (chord-free): `staffRise = rightNote.getStaffPosition() −
   leftNote.getStaffPosition()`. `getStaffPosition()` increases downward and layout
   Y is up-negative, so a higher right note gives negative `staffRise` **and**
   negative `tipRise` — same sign. If `sign(tipRise) != sign(staffRise)` (treat ~0
   on either as "flat"), `slope = 0.0`. (Sign alignment asserted by Phase 5.)
5. Step 3 — slope clamp: if `|slope| > MAX_SLOPE_FACTOR`, set
   `slope = Math.copySign(MAX_SLOPE_FACTOR, slope)`. Then
   `dySs = slope × widthSs` (rise expressed over the renderer's run).
6. Set `dySs` on the tuplet's `DecorationLayout` in `stackTuplets`. `ySs` is still
   the flat placement computed exactly as today (Phase 3 refines it).
7. Add an ASCII sign-convention diagram (up-negative layout Y, `getStaffPosition()`
   increasing downward, veto sign alignment) as a comment on the slope helper.
8. `./scripts/compile.sh` → SUCCESS.

---

## ✅ Phase 3: Sloped vertical clearance (Step 4)

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Opus 4.8, high — the subtle correctness core:
per-tip clearance of the sloped line, reconciled with the flat `StaffExtents`
reservation.

Ensure the **sloped** line clears every intermediate non-rest note tip, not just
the tallest one. A flat box that clears the highest note can still dip below an
intermediate note at the low end once tilted.

**Resolved design:**
- **Clearance gap = the tuplet's existing `marginSs`**, not a new padding
  constant, so a sloped bracket keeps the same visual gap above notes as a flat
  one. `marginSs` is a positioning gap, not part of the reserved rectangle:
  `stackAtAnchor` positions via `boundSs − marginSs − heightSs` but reserves the
  bare element edge; neighbors apply their own margin on query.
- **`StaffExtents` reserves only a flat edge.** `ySet` stamps one constant `ySs`
  across the covered horizontal steps — it cannot describe a slope. Reserve a
  **flat footprint at the lower of the two endpoints** (conservative: never
  under-reserves; the high end over-reserves slightly, which is acceptable per
  "simplest solution first"). Do not attempt per-step stair-stepping. Use the
  tagged `ySet(above, xSs, widthSs, ySs, Neighbor.TUPLET_BRACKET)` overload
  (develop @ `29866388` added the `Neighbor` tag) so the reserved steps report the
  bracket as their neighbor.
- **New `double`-anchor placement variant.** Compute the note-tip `leftYSs`
  in `stackTuplets`, then place via a **new** `StackingUtils.stackAboveAtAnchor(double
  anchorSs, …)` that delegates to the existing private `stackAtAnchor(double)` — it
  combines the anchor with `yGetExpanded(...)` (so non-note layers stay cleared) and
  reserves the footprint through the shared `placeAndReserve`. As of develop @
  `29866388`, that private `stackAtAnchor(double)` (and `placeAndReserve`) require a
  `Neighbor` tag; pass `Neighbor.TUPLET_BRACKET` (added in Phase 3 task 1) so a
  decoration stacked below the tuplet resolves the bracket as its neighbor rather
  than a phantom staff line. **Do not** route through the public `int staffPosition`
  path: it applies an unwanted `STAFF_TOP_Y_SS` clamp and `NOTE_HEAD_RADIUS_SS`
  offset that would corrupt the tip-based ceiling.
- **Origin is `anchorXSs`, run is `widthSs`.** The clearance slope and origin
  must match Phase 2 and the renderer: `slope = dySs / widthSs`, measured from
  `anchorXSs = anchorColumn.getXSs()`.

### Tasks

1. Add `Neighbor.TUPLET_BRACKET` to `layout/Neighbor.java`. Develop @ `29866388`
   introduced the `Neighbor` enum for skyline tagging; the tuplet bracket needs its
   own value so its reservation tags correctly (tasks 3–4). Consumed only here.
2. Extract the clearance as a **pure, package-visible static helper** —
   input: the non-rest columns (the ordered list built in Phase 2), `dySs`,
   `anchorXSs`, `widthSs`, `marginSs`; output: `leftYSs`. Each tip's absolute top
   is `tip.getAbsoluteTopYSs()`. With `slope = dySs / widthSs`, the left endpoint
   that rests on the highest obstacle after tilting is
   `leftYSs = min over tips ( tip.getAbsoluteTopYSs() − marginSs − slope * (tip.getXSs() − anchorXSs) )`
   (`min` because up = smaller Y). Right endpoint = `leftYSs + dySs`.
3. In `stackTuplets`, pass `leftYSs` into `StackingUtils.stackAboveAtAnchor(double)`,
   which tags its reservation `Neighbor.TUPLET_BRACKET`; it is combined (via the
   existing `Math.min` against `yGetExpanded(...)`) with non-note layers — the more
   conservative (higher, smaller-Y) wins. The final left-end Y is that call's result.
4. Reserve the bracket footprint in `StaffExtents` at the **lower** endpoint
   (`Math.max(finalLeftYSs, finalLeftYSs + dySs)`, larger Y = lower) with a single
   flat tagged `ySet(…, Neighbor.TUPLET_BRACKET)` so decorations stacked below the
   tuplet do not collide with its low end. (Conservative: the high end over-reserves
   slightly — acceptable.)
5. Store the resolved left-end Y as the record's `ySs`; keep `dySs`.
6. Add an ASCII diagram to the clearance helper showing the sloped line resting on
   the highest tilted obstacle.
7. `./scripts/compile.sh` → SUCCESS.

---

## ✅ Phase 4: Render sloped bracket

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Opus 4.8, medium — well-specified geometry, but
layout-Y (up-negative) → component-Y conversion of a _delta_ and midpoint number
placement are sign-error-prone.

Draw the bracket with distinct left/right endpoint heights and center the tuplet
number on the bracket midpoint. Rendering consumes `dySs`; with Phases 2–3 in
place the slope is now visible. (Renderer does not depend on Phase 3 to compile,
but visual verification is only meaningful once Phase 3 lands — verify after 3.)

**Resolved design:**
- **Slope run basis.** `dySs` is the rise over `decorLayout.widthSs()`
  (`endXSs − anchorXSs`, anchor→end element run — the same run Phase 2/3 used), so
  `slope = dySs / decorLayout.widthSs()` is exact. The arm endpoints (`leftXSs`,
  `rightXSs`) extend past the columns by `ARM_EXTENSION_SS`; keep `slope` constant
  and **extrapolate** to the arm X positions (one continuous line) from origin
  `anchorXSs`. Do not re-fit `dySs` across the wider arm span.

### Tasks

1. `shape/TupletBracketShape.java` — replace the single `bracketYSs` corner with
   a per-side pair. New signatures:
   - `leftArm(leftXSs, gapLeftXSs, leftYSs, gapLeftYSs, armBottomYSs)` — horizontal
     segment runs `(leftXSs, leftYSs)` → `(gapLeftXSs, gapLeftYSs)`; vertical arm
     bottom is `leftYSs + BRACKET_ARM_HEIGHT_SS` (arm hangs from the sloped corner).
   - `rightArm(gapRightXSs, rightXSs, gapRightYSs, rightYSs, armBottomYSs)` — mirror.

     Update point construction and javadoc; add a short ASCII diagram of the
     4-corner geometry now that each side has its own Y.
2. `ui/renderer/TupletRenderer.java` — thread `dySs` from `renderTupletsFromLine`
   (`decorLayout.dySs()`) into `renderTuplet`. Keep slope constant:
   `slope = dySs / decorLayout.widthSs()` (the run `dySs` was computed over —
   **not** the arm span). Evaluate the line at each arm X, origin `anchorXSs`:
   `leftBracketYSs = boxTopYSs + Tuplet.bracketLineOffsetSs() + slope * (leftXSs − anchorXSs)`,
   and likewise for `rightBracketYSs`, `gapLeftYSs`, `gapRightYSs`.
3. Convert both endpoint Ys through `RenderingUtils.layoutYToComponentYSs` (verified
   affine — a slope delta transforms consistently). Verify the sign: an ascending
   contour must render with the right end **higher**.
4. Number placement: evaluate the **sloped line at `gapCenterXSs`**
   (not the average of the arm corners — the italic correction shifts `gapCenterXSs`
   off the geometric midpoint):
   `numberYSs = boxTopYSs + Tuplet.bracketLineOffsetSs() + slope * (gapCenterXSs − anchorXSs)`;
   `numberBaselineYSs = numberYSs − inkBounds.getCenterY()`. **When `numberOnly`**
   (no arms drawn), use the flat box-top line (`slope` term omitted) so the number
   sits exactly as today. Existing horizontal centering is unchanged.
5. `./scripts/compile.sh` → SUCCESS.
6. `./scripts/run.sh` — visually confirm on a score with ascending, descending,
   and flat tuplets that brackets follow the contour, clear intermediate notes,
   and the number sits on the midpoint. (E2E/visual approval deferred to user.)

---

## ✅ Phase 5: Tests

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b, 2, 3, 4  <br>
**Recommended model/effort:** Sonnet 4.6 or Haiku 4.5, low — table-driven unit
tests against the pure helpers, plus a render-level assertion.

Unit-test the pure slope helper (Phase 2), the pure clearance helper (Phase 3),
the render sign/run reconciliation (Phase 4), and drag preservation. Read
`.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

### Tasks

1. Cover the **slope helper** (Phase 2):
   - Ascending contour → `dySs` nonzero, expected sign (locks the Step 2 sign
     alignment).
   - Descending contour → opposite sign.
   - Flat notes → `dySs == 0`.
   - Contour vs. staff-position sign disagree → veto to `0`.
   - Slope exceeding `MAX_SLOPE_FACTOR` → clamped, i.e. `dySs == ±MAX_SLOPE_FACTOR × widthSs`.
   - Leading/trailing rest columns → skipped (end columns are the outer non-rest
     ones); all-rest span → `dySs == 0`; single non-rest column → `dySs == 0`
     (defensive early-out).
2. The Phase 1b stem-extent tests (`ElementColumnBuilderTest.StemGeometry`) are
   **corrected in Phase 1b**. Confirm here they are green and their names/comments
   match the corrected geometry.
3. Cover the **clearance helper** (Phase 3 — now mandatory, not conditional): an
   intermediate note taller than the endpoints forces the sloped line up so both
   endpoints clear it by `marginSs`.
4. **Render-level test** — extend `TupletRendererTest`: ascending tuplet ⇒ right
   bracket Y higher than left; flat ⇒ equal; a rest-endpoint tuplet ⇒ slope
   anchored/extrapolated correctly from `anchorXSs` over `widthSs`. This is the
   only test that catches the Phase 2→4 run/origin reconciliation and the
   layout→component sign flip.
5. **Drag-preservation test** — run `applyDecorationOffsets` on a sloped tuplet
   layout and assert `ySs` shifts by the offset while `dySs` is unchanged (rigid
   translation).
6. `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh <TestClass>` → green
   (unit target).

---

## Verification (whole plan)

- `./scripts/compile.sh` → SUCCESS after every phase.
- Phase 5 unit tests green.
- App run (`./scripts/run.sh`) on a score containing ascending, descending, and
  flat tuplets:
  - Ascending/descending brackets angle to follow the contour; slope clamped, no
    runaway tilt.
  - The sloped line clears every intermediate note tip (no note pokes above the
    bracket at the low end).
  - The tuplet number is centered on the bracket midpoint.
  - Dragging a tuplet vertically translates the whole sloped bracket rigidly
    (slope preserved).
  - Genuinely flat tuplets (equal-height end notes, or vetoed contour) render
    exactly as before.

---

> **Follow-up:** a post-landing comparison against LilyPond found the bracket
> ~19% too steep and ~0.42 ss too high. Diagnosis, the fix, and the required
> re-measure live in a separate plan:
> [tuplet-lilypond-conformance.md](tuplet-lilypond-conformance.md) (blocked on #514).
