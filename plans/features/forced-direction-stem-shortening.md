# Forced-Direction Stem Shortening (align tuplet stems with LilyPond)
**Issue:** #509  
**Base branch:** `develop`  
**Branch:** `509-tuplet-rendering`  
**Created:** 2026-07-07

Correct a factual error the `tuplet-stem-trimming.md` work was built on. That plan assumed LilyPond **holds the bracket and trims interior stems to meet it**. It does not. Verified against `lily/tuplet-bracket.cc`, `lily/bracket.cc`, and `lily/stem.cc` (master), LilyPond's model is:

1. **The tuplet bracket never shortens stems.** It reads each note column's _actual_ vertical extent (`Note_column::cross_staff_extent` = note-heads ∪ the real stem extent) and floats `padding` above the worst one. `stem.cc` contains **no** reference to tuplets or brackets; `tuplet-bracket.cc` only ever _reads_ stem extents.
  
2. **Stems shorten themselves — bracket-independent — only when forced.** A stem in its _unnatural_ (forced) direction is shortened per Ross & Gourlay in `Stem::internal_calc_stem_end_position` (`stem.cc:519-555`). A quarter-note stem's 3.5 ss length is reduced by up to 1.0 ss, progressively with distance from the middle line, to a **2.5 ss floor**. This is the real reason a flipped stem does not float the bracket sky-high, and it is exactly the "2.5 ss minimum" the current SongScribe code hard-codes.
  

So the current code has it backwards in two linked ways: it added a **bracket-driven trim LilyPond does not have**, and it is **missing the forced-direction shortening LilyPond does have** — the trim was standing in for the shortening.

**Decision (user, 2026-07-07): match LilyPond.** Revert the bracket-driven trim; add forced-direction shortening as a bracket-independent stem rule; let the bracket float above the (now correctly shortened) actual tips. The motivating high→middle→low case still shortens the forced middle stem — via the correct mechanism — so the visual result should be close; genuinely natural, full-length stems will float the bracket, as they do in LilyPond.
## What the current code does (verified)
| Concern | Symbol | Verdict |
|---------|--------|---------|
| Slope (contour → veto → clamp 0.5) | `StructuralStacker.computeTupletSlopeDySs` | **Faithful.** Keep. |
| Clearance uses a min-stem obstacle for interior up-stems | `StructuralStacker.computeTupletClearanceLeftYSs` / `effectiveObstacleTopYSs` | **Divergent.** Revert to actual tips. |
| Bracket-driven trim pass | `StructuralStacker.trimSpannedUpStems` / `isTrimmableUpStem` | **Divergent.** Remove. |
| Trim data channel | `LayoutResult.StemLayout.bracketTrimSs` → `NoteRenderer.renderStem` | **Repurpose** for forced shortening. |
| Forced-direction shortening | *(none)* | **Missing.** Add. |
| Unbeamed stem length | `LayoutEngine.calculateUnbeamedStems` | Only ever *lengthens* toward center; add shortening. |
| Static stem-top extent | `ElementColumnBuilder.calculateStemTopSs` | Returns `-STEM_LENGTH_SS` for any up-stem; must reflect shortening so clearance floats above the real tip. |
### Slope simplifications (accept, do not change)
- LilyPond unites the outer note extents with the staff extent (`rv.unite(staff)`) before measuring rise; SongScribe uses raw note tops. For low outer notes LilyPond flattens slightly more. Acceptable in the always-above, chord-free case.
  
- LilyPond's veto also guards chord top-vs-bottom contour reversal; irrelevant chord-free. The single `sign(tipRise) != sign(staffRise)` check is the correct reduction.
  
- LilyPond's `padding` is 1.1 ss; SongScribe's `TUPLET_MARGIN_SS` is 0.625 ss. This is a deliberate house style, not an algorithm error.
  
## The forced-direction shortening rule (port target)
From `stem.cc:519-555`, quarter-note row (SongScribe uses a single 3.5 ss stem length for all durations via `SMuFLConstants.STEM_LENGTH_SS`, so only that row is ported — no `lengths`/`stem-shorten` tables):

```
A stem is FORCED when its direction opposes defaultDirection. Auto stems equal
defaultDirection, so they are never forced (this holds regardless of when direction
is resolved):
    forced up-stem   = direction.isUp()   && staffPosition <= 0   (and not a grace note)
    forced down-stem = direction.isDown() && staffPosition >  0

For a forced stem, shorten by (expressed directly in staffPosition — integer diatonic
steps from the middle line, so no half-space↔ss conversion is needed):
    shortenSs   = min( MAX_FORCED_SHORTEN_SS, FORCED_SHORTEN_PER_STEP_SS * (1 + |staffPosition|) )
    finalStemSs = STEM_LENGTH_SS - shortenSs                        (>= the 2.5 ss floor)
where, derived exactly from stem.cc's half-space constants (each halved to ss):
    FORCED_SHORTEN_PER_STEP_SS = 1/6   (LilyPond shortening_step 0.3333 half-spaces / 2)
    MAX_FORCED_SHORTEN_SS      = 1.0   (LilyPond 2·stem-shorten[0] = 2.0 half-spaces / 2)
    floor = STEM_LENGTH_SS(3.5) - MAX(1.0) = 2.5, reached at |staffPosition| >= 5
            (one diatonic step above the top staff line)
```

At the middle line the stem shortens ~0.17 ss; a note at `|staffPosition| >= 5` (one diatonic step above the top line) hits the 2.5 ss floor. This composes cleanly with the existing "lengthen toward center" logic in `calculateUnbeamedStems`: lengthening only fires for a note _far on its natural side_ (lengthening = `max(0, distanceTowardCenter − MIN_STEM_SS)`), which is disjoint from the forced region — a stem is lengthened, natural, or shortened, never two at once.

Two resolved subtleties baked into the formula above:

- **Constants are exact, not calibrated.** Keying the rule on integer `staffPosition` (with `STAFF_POSITION_OFFSET_SS = 0.5`, verified) means LilyPond's `shortening_step` of 0.3333 half-spaces halves to exactly `1/6` ss per diatonic step. No rendered-staff tuning is required.
  
- **Forced ≡ the strict complement of** `defaultDirection`**.** `defaultDirection` (verified) is UP iff `staffPosition > 0` (or grace), else DOWN. Defining forced as its complement (up: `sp <= 0`; down: `sp > 0`; grace excluded) makes an auto stem — which always equals `defaultDirection` — provably never forced, so the shortening is 0 for every auto/grace stem regardless of when direction resolves. The sole divergence from LilyPond, a down-stem exactly on the middle line, is treated as natural here (negligible).
  
## Coordinate convention (unchanged from prior tuplet work)
- Layout Y: staff-space, middle line = 0, **negative = up**. Bracket always above the staff (`dir = UP`).
  
- `StaffElement.getStaffPosition()` increases **downward** (higher pitch = smaller / negative). `defaultDirection`: `staffPosition > 0` (below middle) → stem up; else down. So a **forced up-stem** is an up-stem with `staffPosition <= 0`.
  
- Up-stem tip is above the note head (smaller Y); a shorter stem → larger (lower) tip Y.
  
## Key constants (verified)
- `SMuFLConstants.STEM_LENGTH_SS = 3.5` — the single natural stem length.
  
- `LayoutEngine.MIN_STEM_SS = STEM_LENGTH_SS` (3.5) — misnamed; it is the natural length, not a floor. Do not repurpose it.
  
- `Tuplet.MIN_TRIMMED_STEM_SS = 2.5` — currently the trim floor; becomes the **forced-shorten floor** and should move out of `Tuplet` (a stem constant, not a tuplet one).
  
- `Staff.STAFF_POSITION_OFFSET_SS = 0.5` — one diatonic step; the linchpin of the exact `1/6` step derivation.
  
- `StructuralStacker.TUPLET_MARGIN_SS = 0.625` — bracket gap above the clearance ceiling.
  
## Touchpoints (verified)
| Stage | File / symbol |
| --- | --- |
| Shorten helper (new, pure) | co-locate with existing stem geometry (`NoteGeometry.computeBaseStemGeometry` neighborhood) — single source used by builder **and** engine |
| Constants home | move `MIN_TRIMMED_STEM_SS`(→ forced-shorten floor) + new `MAX_FORCED_SHORTEN_SS` / `FORCED_SHORTEN_PER_STEP_SS` beside `MIN_STEM_SS` / `SMuFLConstants` |
| Static extent | `layout/ElementColumnBuilder.java` → `calculateStemTopSs`, `calculateStemBottomSs` (apply shortening so `getAbsoluteTopYSs()` is the real tip) |
| Stem length | `layout/LayoutEngine.java` → `calculateUnbeamedStems` (apply shortening, populate field) |
| Data channel | `layout/LayoutResult.java` → `StemLayout` rename `bracketTrimSs` → `forcedShorteningSs` |
| Render | `ui/renderer/NoteRenderer.java` → `renderStem` (`stemLength = base + lengtheningSs − forcedShorteningSs`; mechanical rename) |
| Clearance revert | `layout/stacking/StructuralStacker.java` → `computeTupletClearanceLeftYSs` (use `getAbsoluteTopYSs()` for all columns) |
| Trim removal | `layout/stacking/StructuralStacker.java` → delete `trimSpannedUpStems`, `effectiveObstacleTopYSs`, `isTrimmableUpStem`; drop trim call in `stackTuplets` |
| Per-note geometry | `layout/ElementColumn.java` → `getStemTopSs`, `getAbsoluteTopYSs` (unchanged; now reflect shortening via the builder) |

**Scope guard.** This plan covers **unbeamed** stems, matching both the reverted trim's scope and LilyPond's split (beamed forced stems use the beam's own `calc_stem_shorten`, a separate path SongScribe handles in `calculateBeams`). Beamed stems are out of scope.

**Open scope decision (surface before Phase 1):** **up-stem only** (minimal for the `\tupletUp` bracket that #509 is about) **vs. symmetric up+down** (the correct general rule; the formula is direction-agnostic). Recommendation: implement **symmetric**, since a half-rule is a latent inconsistency, but keep it a conscious choice.
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Forced-direction shortening](#-phase-1-forced-direction-shortening) | ✅ Complete (pending visual check) | —   |
| 2   | [Revert the bracket-driven trim](#-phase-2-revert-the-bracket-driven-trim) | ✅ Complete (pending visual check) | —   |
| 2a  | [Match LilyPond slope run](#-phase-2a-match-lilypond-slope-run) | ✅ Complete (pending visual check) | —   |
| 3   | [Tests](#-phase-3-tests) | ✅ Complete | —   |

* * *
## ✅ Phase 1: Forced-direction shortening
**Status:** Complete (code + `compile.sh` SUCCESS; task 8 visual check pending)  
**BlockedBy:** —  
**Recommended model/effort:** Opus 4.8, high — a pure formula port (constants resolved exactly), wired through two producers (static extent + stem layout) so the clearance and the renderer agree on the same shortened tip.

Add the bracket-independent forced-direction shortening. After this phase, forced stems render shorter and their column extents report the shortened tip — with the tuplet clearance still on the old (trim) path, so no tuplet visual change yet.
### Tasks
1. Add the pure helper `forcedShorteningSs(staffPosition, direction, isGraceNote)` returning the shortening in ss (0 for natural, auto, and grace stems), implementing the rule above (forced up = `isUp && sp <= 0 && !grace`; forced down = `isDown && sp > 0`). Home it beside the existing stem geometry so both producers call one source of truth.
  
2. Move `MIN_TRIMMED_STEM_SS` out of `dom/Tuplet.java` to the stem-constant home, renamed to reflect "forced-stem floor"; add `MAX_FORCED_SHORTEN_SS` (1.0) / `FORCED_SHORTEN_PER_STEP_SS` (1.0/6.0) (no magic numbers).
  
3. `LayoutResult.java` — rename `StemLayout.bracketTrimSs` → `forcedShorteningSs` (same type/position); update its Javadoc (the ≥0 amount a **forced** stem is shortened, not a tuplet trim). Update all construction sites.
  
4. `LayoutEngine.calculateUnbeamedStems` — compute `forcedShorteningSs` via the helper; subtract it from the stem length used for `topYSs`/`bottomYSs`; pass it in the record. Preserve the existing lengthening branch unchanged (disjoint region).
  
5. `ElementColumnBuilder.calculateStemTopSs` / `calculateStemBottomSs` — subtract the same helper's result so `getStemTopSs()` / `getAbsoluteTopYSs()` report the real (shortened) tip. Update the Javadoc/ASCII diagram.
  
6. `NoteRenderer.renderStem` — read the renamed `forcedShorteningSs` (`stemLength = base + lengtheningSs − forcedShorteningSs`, floored defensively at the forced-stem floor). Mechanical rename; behavior identical to today for natural stems.
  
7. `./scripts/compile.sh` → SUCCESS.
  
8. `./scripts/run.sh` — a forced up-stem note (e.g. `\stemUp` on a note above the middle line), no tuplet, renders with a visibly shortened stem matching LilyPond. **Also check a forced eighth/16th:** its flag must not crowd the note head after shortening — the only duration-dependent visual risk; if it does, add LilyPond's `stem-shorten` split (0.5 ss eighth, 0.25 ss 16th+).
  

* * *
## ✅ Phase 2: Revert the bracket-driven trim
**Status:** Complete (code + `compile.sh` SUCCESS; the padding correction below landed; visual check pending)  
**BlockedBy:** 1  
**Recommended model/effort:** Opus 4.8, medium — mostly deletion, but the clearance must reduce to the pre-trim (angled-brackets Phase 3) form and now correctly float above the Phase 1 shortened tips.

Remove the trim so the bracket floats above actual tips, exactly as LilyPond does. With Phase 1 in place, forced stems are already shortened, so the bracket sits low on them without any trimming.

**Landed during visual checking (LilyPond geometry fixes):**
- **Padding reference point.** LilyPond measures its `padding` from the note tip to the bracket **line**, not to the arm bottom (`tuplet-bracket.cc:706–717`, `*offset += padding * dir`; the `edge-height` arms hang into that gap and never enter positioning). `TUPLET_MARGIN_SS (0.625)` was removed; `TUPLET_BRACKET_PADDING_SS = 1.1` (LilyPond default, tip → line) plus a derived `TUPLET_ARM_MARGIN_SS = padding − BRACKET_ARM_HEIGHT_SS` (fed to `stackAboveAtAnchor`, which measures to the arm bottom) now land the line exactly 1.1 ss above the worst tip. This is a uniform vertical shift and does **not** change the slope.
### Tasks
1. `StructuralStacker.computeTupletClearanceLeftYSs` — replace `effectiveObstacleTopYSs(column, isEndpoint)` with `column.getAbsoluteTopYSs()` for **every** column (drop the endpoint/interior split and the min-stem obstacle). Keep the `STAFF_TOP_Y_SS` clamp, the slope projection, and the `min` over columns. This is the angled-brackets Phase 3 clearance, now reading Phase 1's shortened tips.
  
2. Delete `trimSpannedUpStems`, `effectiveObstacleTopYSs`, and `isTrimmableUpStem`.
  
3. `StructuralStacker.stackTuplets` — remove the `trimSpannedUpStems` call and any now-dead locals it required.
  
4. Confirm no remaining reader references `forcedShorteningSs` as a _trim_ (it is now a stem property, not tuplet output) and that `NoteAttachedStacker` (a `StemLayout` consumer) sees the shortened `topYSs` correctly for articulation placement.
  
5. `./scripts/compile.sh` → SUCCESS.
  
6. `./scripts/run.sh` — descending tuplet with a forced interior up-stem: the bracket holds its clamped slope and floats a `TUPLET_MARGIN_SS` gap above the shortened tip (no trim, no runaway float). A genuinely natural, full-length interior up-stem floats the bracket above it (LilyPond behavior). Flat/ascending unchanged.
  

* * *
## ✅ Phase 2a: Match LilyPond slope run
**Status:** Complete (code + `compile.sh` SUCCESS; task 6 visual check pending)  
**BlockedBy:** 2  
**Recommended model/effort:** Opus 4.8, medium — a small formula change to one pure helper, but the run must be built from the correct per-endpoint head/stem edge (verified against LilyPond), and the reduced `dySs` feeds both the clearance and the renderer, so getting the run basis right matters.

The bracket currently follows the raw first/last note-tip contour and reads a few degrees too steep. LilyPond sets the bracket's **total** rise equal to the outer note-tip difference `dy` and spreads it over the head/stem-edge run `x1 − x0`, which is wider than the note-center span — so the drawn slope `dy/(x1 − x0)` is proportionally shallower. It is a smooth, contour-proportional reduction, **not** a damping curve and **not** a hard clamp (the only clamp is the existing `MAX_SLOPE_FACTOR`, `tuplet-bracket.cc:626–627`).

**LilyPond evidence (verified in `~/Developer/projects/lilypond/lily`):**
- `calc_position_and_height`: `*dy = graphical_dy` (outer-tip rise, line 547); the line is drawn with slope `dy / (x1 − x0)` via `factor = 1/(x1 − x0)` (lines 707–714); `calc_positions` returns `positions = (offset, offset + dy)` at `x0`/`x1` (line 512, 768) — i.e. the bracket rises by exactly `dy` across its whole width.
- `x0`/`x1` come from `get_x_bound_item` (lines 71–85, 483–486): if an endpoint is a note whose **stem points toward the bracket** (up-stem, since SongScribe's bracket is always UP) the bound is the **stem** → its near X edge; otherwise the bound is the **NoteColumn** → the note-head's outer X edge. `x0` is the LEFT bound's LEFT edge, `x1` the RIGHT bound's RIGHT edge.
- The bound extent is **head/stem only** — ledger lines, dots, accidentals, and flags are all excluded: `note-column.cc` adds only the stem (line 124) and heads (line 155) to the axis group; accidentals (`accidentals()`, 210) and dots (`dot_column()`, 233) are merely looked up, ledger lines are a separate `LedgerLineSpanner`, and the flag is a separate `Flag` grob not in the stem's own stencil extent (`stem.cc:1038–1041`; `flag.cc:58`). This is **option 2** — mirror `x0…x1` exactly, with **no** `ARM_EXTENSION_SS` (that ±0.2 is a visual overhang, not a bound edge; using it would over-flatten — option 1, rejected).

### Tasks
1. `StructuralStacker.computeTupletSlopeDySs` — replace the note-center run (`tipRun`) with the LilyPond head/stem-edge run. For each of the two outer non-rest columns, compute its bound edge:
   - endpoint is a note-with-stem **and** its resolved direction is UP → the **stem** edge (up-stem sits on the right of the head; offset from `getXSs()` by head width, adjusted by `LineThickness.STEM_SS`);
   - otherwise → the **note-head** outer edge (`getXSs()` ± `SMuFLConstants.NOTE_HEAD_WIDTH_SS` as appropriate).
   The left endpoint contributes `x0` (its LEFT edge), the right endpoint `x1` (its RIGHT edge); `edgeRunSs = x1 − x0`.
2. Set the reduced rise so the drawn slope matches LilyPond: the render slope is `dySs / widthSs`, and it must equal `tipRise / edgeRunSs`, so `dySs = tipRise × widthSs / edgeRunSs`. Keep Step 2's contour veto and Step 3's `MAX_SLOPE_FACTOR` clamp, applied to the reduced slope.
3. Verify what `ElementColumn.getXSs()` denotes (note-head left origin vs center) before offsetting — `TupletRenderer`'s `leftXSs`/`rightXSs` computation is the reference for the head-width/stem offsets, **minus** its `ARM_EXTENSION_SS` term. Do not introduce raw numeric literals; reuse `SMuFLConstants.NOTE_HEAD_WIDTH_SS` / `LineThickness.STEM_SS`.
4. No other call site changes: the clearance (`computeTupletClearanceLeftYSs`) and the renderer both read `dySs / widthSs`, so the smaller `dySs` shallows both consistently.
5. `./scripts/compile.sh` → SUCCESS.
6. `./scripts/run.sh` — the descending forced-interior-up-stem tuplet's angle sits a few degrees shallower than the raw note-tip line, matching a LilyPond rendering of the same notes; flat and single-direction cases unchanged.

**Phase 3 note:** add a slope-run test asserting the reduced `dySs` (rise spread over the head/stem-edge run, not the note-center span), including the mixed-endpoint case (one outer stem toward the bracket → stem edge, the other away → head edge).

* * *
## ✅ Phase 3: Tests
**Status:** Complete (`compile.sh --test` SUCCESS; `test.sh unit` 4932 passed)  
**BlockedBy:** 1, 2, 2a  
**Recommended model/effort:** Sonnet 4.6, low–medium — table-driven unit tests on the pure helper and the reverted clearance, plus render-level assertions. Read `.agents/guides/testing-common.md` and `testing-unit.md` first.
### Tasks
1. **Forced-shorten helper** (new): forced up-stem (note above middle) shortens progressively with distance; caps at the 2.5 ss floor at `|staffPosition| >= 5`; a natural up-stem (note below middle) and any auto-direction or grace stem shorten by 0; a down-stem exactly on the middle line is treated as natural (the resolved boundary); symmetric down-stem case if symmetric scope was chosen.
  
2. **Stem extents** (`ElementColumnBuilderTest.StemGeometry`): a forced up-stem's `getStemTopSs()` / `getAbsoluteTopYSs()` now report the shortened tip; natural stems unchanged from the Phase-1b-corrected values.
  
3. **Renderer** (`NoteRendererTest`): a forced stem draws shorter by exactly `forcedShorteningSs`; natural stems draw as before; the renamed field is read.
  
4. **Clearance revert** (`StructuralStackerTest`): the bracket **line** floats `TUPLET_BRACKET_PADDING_SS` (1.1) above the actual (shortened) interior tip — i.e. the box bottom / arm bottom is `TUPLET_ARM_MARGIN_SS` above it; **remove/replace** the min-stem-obstacle and bracket-yields trim tests, and any test asserting the old `TUPLET_MARGIN_SS` (0.625) arm-bottom gap, from the stem-trimming work.
  
4a. **Slope run** (`StructuralStackerTest`, Phase 2a): `computeTupletSlopeDySs` returns the reduced `dySs` — the outer-tip rise spread over the head/stem-edge run (`x1 − x0`), not the note-center span — so the drawn slope is proportionally shallower; cover the mixed-endpoint case (one outer stem toward the bracket → stem edge, the other away → head edge); veto and `MAX_SLOPE_FACTOR` clamp still apply.
  
5. **Tuplet render** (`TupletRendererTest`): a forced interior up-stem no longer floats the bracket beyond the shortened tip; slope/flat cases unchanged; no stem is trimmed by the tuplet (the removed trim leaves `forcedShorteningSs` driven only by direction).
  
6. **Drag preservation** unchanged: `applyDecorationOffsets` shifts `ySs`, `dySs` preserved.
  
7. `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh <TestClasses>` → green (unit target).
  

* * *
## Verification (whole plan)
- `./scripts/compile.sh` → SUCCESS after every phase.
  
- Phase 3 unit tests green.
  
- `./scripts/run.sh` on a score with forced and natural stems, tupleted and not:
  
  - A forced up-stem (no tuplet) is shortened per Ross & Gourlay — verified against a LilyPond rendering of the same note.
    
  - A `\tupletUp` bracket floats `TUPLET_MARGIN_SS` above the actual tips: low on a forced (shortened) interior stem, higher over a natural full-length one — no bracket-driven trimming anywhere.
    
  - Slope still follows the contour, clamped to `MAX_SLOPE_FACTOR`.
    
  - Dragging a tuplet translates the bracket rigidly (`dySs` preserved); stem lengths, now a pure function of direction, are unaffected by the drag.
