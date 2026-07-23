# 579 — Full Port of LilyPond's Beam Positioning Algorithm

Replace SongScribe's abc2svg-style beam slope/placement (endpoint slope + hyperbolic damping + iterative flattening + 0.5-ss snap) with LilyPond's complete pipeline: per-stem ideal/minimum lengths → least-squares fit → concaveness/slope damping → feasible-region shift → quant candidate generation (straddle/sit/inter/hang) → demerit scoring → best candidate. Blast radius is contained: all downstream consumers read `LayoutResult.BeamLayout`/`StemLayout`, whose contract is preserved. Beams only interact with staff lines; the stacking machinery (`StructuralStacker.obstacleTopSs`) adapts to wherever the beam lands via `StemLayout.topYSs`.

## Conventions (referenced by every phase)

All phases MUST follow these. They are the single source of truth for units and signs.

- **Internal scoring space (LilyPond convention)**: staff-space (ss) units, **Y-up positive**, y=0 at the middle staff line. A beam "position" is a pair `(leftY, rightY)` — the Y of the **center of the beam farthest from the noteheads** (the one the stems reach), measured at the first and last stem's X. X is in ss, measured from the first stem (`x = columnXSs - firstColumnXSs`); `xSpan = lastX` (0 for a degenerate single-column group).
- **SongScribe layout space**: ss units, **Y-down positive**, y=0 at the middle line. `Staff.spToSs(staffPosition)` converts a note's integer staff position (positive = below middle) to Y-down ss. Therefore a note's Y-up head position is `-Staff.spToSs(element.getStaffPosition())`, and its integer Y-up half-space position is `-element.getStaffPosition()`.
- **Direction**: SongScribe beam groups share one stem direction (no knees/cross-staff). Represent as `dirSign`: `+1` stems-up, `-1` stems-down (from `groupStemDirection(...).isUp()`).
- **Output conversion** (scoring space → `BeamLayout`): SongScribe's `startYSs` is the beam's **outer edge** in Y-down ss (top edge for stems-up, bottom edge for stems-down), and the per-element stem tip sits on the line through it (renderer insets drawn stems by half thickness, so drawn stems end at the beam center — visually identical to LilyPond). With `bt = LineThickness.BEAM_THICKNESS_SS`:
  - `startYSs = -leftY - dirSign * bt / 2.0`
  - `slope (Y-down per X) = -(rightY - leftY) / xSpan` (0 if `xSpan == 0`)
- **Geometry constants** (match the renderer — which now follows LilyPond): beam thickness `bt = LineThickness.BEAM_THICKNESS_SS` (= 0.48, LilyPond's `beam-thickness`, deliberately **not** Bravura's 0.5); beam translation (center-to-center distance of stacked beams) `beamTranslation = LineThickness.BEAM_TRANSLATION_SS` (= 0.81, LilyPond's `get_beam_translation` fewer-than-four-beams branch `(2·ss + slt − bt)/2`; this is what `BeamGroupRenderer.drawBeam` uses as `innerBeamOffsetSs` per level); staff line thickness `slt = LineThickness.STAFF_LINE_SS` (= 0.1); staff radius `2.0` ss (5-line staff).
  - The renderer also rounds beam corners LilyPond-style via `LineThickness.BEAM_BLOT_DIAMETER_SS` (= 0.08, LilyPond's `blot-diameter` of 0.4 pt at a 5 pt staff space): `BeamGroupRenderer.drawBeam` insets the parallelogram by half that and fills **plus** strokes it with a round pen of that width, so the drawn extent still measures `bt`.
  - Note `LayoutEngine.BEAM_DEPTH_SS` (= 0.4) is **not** a beam thickness despite its comment; it is only the base for the thickening scale factor and is intentionally left alone.
- **Per-stem beam count**: `BeamMath.beamCount(element)` (1 for 8th, 2 for 16th, 3 for 32nd; 1 for anything else). Group max beam count = max over members. All LilyPond `robust_list_ref(n, list)` lookups clamp the index to the last element — port as `array[Math.min(n, array.length - 1)]`.
- **Simplifications (deliberate, do not "fix")**: no knees (`is_knee_ = false`), no cross-staff (`is_xstaff_ = false`), no collisions/covered-grobs (beams only score against staff lines), no tremolo, no broken/`align_broken_intos` beams (a SongScribe beam never spans lines), `length-fraction = 1.0` (grace members are treated uniformly by staff position, matching current `calculateBeams` behavior), single noteheads (no chords: LilyPond's `head_positions` interval collapses to one value; `chord_start_y` = the head's Y-up ss position), all stems "normal" (`is_normal_` always true), `stem_ypositions_ = 0` (single staff, common refpoint).
- **LilyPond reference source** (read before porting each piece): `~/Developer/projects/lilypond/lily/beam-quanting.cc`, `~/Developer/projects/lilypond/lily/stem.cc`, `~/Developer/projects/lilypond/lily/beam.cc`, `~/Developer/projects/lilypond/lily/least-squares.cc`. Exact line ranges are given per task.

### Data flow and the Y-sign boundary (the plan's #1 failure mode)

A transposed sign silently picks a wrong-looking beam instead of failing, so keep this map in front of you. **All Y-up ↔ Y-down conversion happens only at the LayoutEngine ↔ BeamScoring boundary; inside `BeamScoring` everything is Y-up.**

```
 LayoutEngine (SongScribe space, Y-DOWN +, y=0 = middle line)
 ─────────────────────────────────────────────────────────────
   element.getStaffPosition()          (+ = below middle line)
        │  build StemInput  (negate once per stem)
        │    headYUpSs  = -Staff.spToSs(staffPosition)
        │    headHalfPos = -staffPosition
        ▼
 ══════════════ BeamScoring (LilyPond space, Y-UP +, y=0 = middle line) ══════════════
   StemInput[]  ──▶ calc_stem_info  (ideal/shortest, Y-up ss)
                ──▶ least-squares + set_minimum_dy → musicalDy
                ──▶ concaveness → slope_damping  (may force flat)
                ──▶ shift_region_to_valid  → unquantedLeftY / unquantedRightY
                ──▶ generate 256 quant candidates → score 6 demerits → min
                ──▶ BeamPosition(leftYUpSs, rightYUpSs)   ← outer-beam CENTER, Y-up
 ═════════════════════════════════════════════════════════════════════════════════════
        │  convert once (per Conventions "Output conversion")
        │    startYSs = -leftYUpSs - dirSign * bt/2      (Y-down outer edge)
        │    slope    = -(rightYUpSs - leftYUpSs) / xSpan
        ▼
   BeamLayout / StemLayout  (Y-DOWN, consumed unchanged downstream)
```

Reproduce a compact version of this as an ASCII header comment atop `BeamScoring.java` (sign convention + the two conversion formulas), so the boundary is documented at the code that owns it.

## Status Dashboard

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|--------------------|
| 1 | [BeamScoring Scaffold + Stem Info](#-phase-1-beamscoring-scaffold--stem-info) | ✅ Complete | Opus 4.8, high |
| 2 | [Ideal Positions: Least-Squares + Damping + Region Shift](#-phase-2-ideal-positions-least-squares--damping--region-shift) | ✅ Complete | Opus 4.8, high |
| 3 | [Quant Generation + Demerit Scoring + Solve](#-phase-3-quant-generation--demerit-scoring--solve) | ✅ Complete | Opus 4.8, high |
| 4 | [Wire Into LayoutEngine](#-phase-4-wire-into-layoutengine) | ✅ Complete | Fable 5 (or Opus 4.8), high |
| 5 | [Manual Verification](#-phase-5-manual-verification) | ⏳ Pending | N/A — manual |
| 6 | [Test Coverage](#-phase-6-test-coverage) | ⏳ Pending | Sonnet 4.6, medium |

## ✅ Phase 1: BeamScoring Scaffold + Stem Info

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high effort — port of `Stem::calc_stem_info` whose clamps operate in direction-multiplied coordinates; sign errors here poison every later phase.

### Context

Create a new pure-math class that will hold the whole scoring pipeline, plus the per-stem ideal/minimum stem-end computation that feeds it. Follow the **Conventions** section at the top of this plan file for units, signs, and simplifications. No existing file is modified in this phase; the new class is dead code until Phase 4 wires it in.

### Tasks

1. Create `src/main/java/songscribe/layout/BeamScoring.java` (same GPL header as `BeamMath.java`, package `songscribe.layout`). **Class shape: instance-based.** A package-private constructor takes the group-level inputs and stores them plus the pipeline's intermediate state (`stemInfos`, `unquantedLeftY`, `unquantedRightY`, `musicalDy`, `xSpan`, `dirSign`, `forcedFraction`, group max beam count) as private fields; the pipeline methods read/write those fields rather than threading long parameter lists. A single `public static` entry point (added in Phase 3) constructs an instance, runs the pipeline, and returns the result. This mirrors LilyPond's own `Beam_scoring_problem` object and keeps the sign-sensitive math readable. Prepend the ASCII sign-convention header comment described in Conventions. Define the input types:
   - `record StemInput(double xSs, double headYUpSs, int headHalfPos, int beamCount)` — `xSs` per Conventions (relative to first stem), `headYUpSs = -Staff.spToSs(staffPosition)`, `headHalfPos = -staffPosition` (integer Y-up half-spaces), `beamCount` from `BeamMath.beamCount`.
   - Group-level inputs (constructor parameters or a record): `List<StemInput> stems` (in element order), `int dirSign` (+1 up / -1 down), `double forcedFraction` (computed by the caller in Phase 4; range 0–1).
2. Add named constants to `BeamScoring` (values from `~/Developer/projects/lilypond/scm/define-grobs.scm:3442-3458` and `:494`, and `beam-quanting.cc:70-120` `Beam_quant_parameters::fill` defaults). Indexed arrays are by `beamCount - 1`, clamp-to-last per Conventions:
   - `BEAMED_LENGTHS_SS = {3.26, 3.5, 3.6}`
   - `BEAMED_MINIMUM_FREE_LENGTHS_SS = {1.83, 1.5, 1.25}`
   - `BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS = {2.0, 1.25}` (only two entries in LilyPond — keep two and clamp)
   - `BEAMED_STEM_SHORTEN_SS = {1.0, 0.5, 0.25}`
   - Scoring parameters (used in Phase 3, defined now): `REGION_SIZE = 2`, `BEAM_EPS = 1e-3`, `SECONDARY_BEAM_DEMERIT = 10.0`, `STEM_LENGTH_DEMERIT_FACTOR = 5.0`, `HORIZONTAL_INTER_QUANT_PENALTY = 500.0`, `STEM_LENGTH_LIMIT_PENALTY = 5000.0`, `DAMPING_DIRECTION_PENALTY = 800.0`, `HINT_DIRECTION_PENALTY = 20.0`, `MUSICAL_DIRECTION_FACTOR = 400.0`, `IDEAL_SLOPE_FACTOR = 10.0`, `ROUND_TO_ZERO_SLOPE = 0.02`
   - Damping (used in Phase 2): `SLOPE_DAMPING_COEFFICIENT = 0.6`, `DAMPING = 1.0`, `CONCAVE_FORCE_FLAT = 10000.0`
   - Geometry, derived per Conventions: `STAFF_RADIUS_SS = 2.0`, and instance/static accessors for `bt = LineThickness.BEAM_THICKNESS_SS`, `beamTranslation = LineThickness.BEAM_TRANSLATION_SS`, `slt = LineThickness.STAFF_LINE_SS`.
   - **Quant offsets (single source of truth, DRY).** The straddle/sit/inter/hang offsets are used by `set_minimum_dy` (Phase 2), `generate_quants` (Phase 3), and the re-targeted/added tests (Phases 4, 6) — do **not** recompute them anywhere. Define them once here as **package-visible** `static final` fields computed at class-init (they depend on the runtime-loaded `bt`/`slt`, so they are not compile-time constants): `STRADDLE_SS = 0.0`, `SIT_SS = (bt - slt) / 2`, `INTER_SS = 0.5`, `HANG_SS = 1.0 - (bt - slt) / 2`. Every later reference to `sit`/`inter`/`hang`/`straddle` in this plan means these fields; tests import them rather than re-deriving.
3. Port `Stem::calc_stem_info` (`~/Developer/projects/lilypond/lily/stem.cc:1135-1266` — read it) as a method producing, per stem, `record StemInfo(double idealYUpSs, double shortestYUpSs)` (both in scoring space, real Y-up ss — LilyPond's `ideal_y_`/`shortest_y_` after the final `*= my_dir` / `minimum_y * my_dir` steps). Mapping with `staff_space = 1`, `length_fraction = 1`, no tremolo, no `no-stem-extend`, no knee, and `d = dirSign`, `bc = stemInput.beamCount()`:
   - `heightOfBeams = bt + (bc - 1) * beamTranslation`
   - `idealLength = max(BEAMED_LENGTHS_SS[bc-1] - bt/2, BEAMED_MINIMUM_FREE_LENGTHS_SS[bc-1] + heightOfBeams - bt/2)`
   - `noteStart = headYUpSs * d` (LilyPond `head_positions(me)[my_dir] * 0.5 * my_dir * staff_space` — the head interval collapses to the single head)
   - `idealY = noteStart + idealLength` (direction-multiplied space)
   - Clamps (direction-multiplied space, from stem.cc:1235-1243): `idealY = max(idealY, 0.0)` then `idealY = max(idealY, -1.0 - bt + heightOfBeams)`
   - Shorten (port of `Beam::calc_stem_shorten`, `beam.cc:1059-1091`, using the **group max** beam count `maxBc`): `idealY -= BEAMED_STEM_SHORTEN_SS[maxBc-1] * forcedFraction`
   - `minimumLength = BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS[min(bc-1, 1)] + heightOfBeams - bt/2`
   - Return `idealYUpSs = idealY * d`, `shortestYUpSs = (noteStart + minimumLength) * d`
4. Run `./scripts/compile.sh` exactly — must report SUCCESS. (An "unused" warning for the new class is expected and acceptable until Phase 4.)
5. **Smoke test (closes the dead-code feedback gap).** `BeamScoring` is otherwise dead code until Phase 4, so a sign flip in `calc_stem_info` would only surface two phases later. To catch it at the source, create `src/test/java/songscribe/layout/BeamScoringTest.java` now with **one** hand-computed assertion validating stem-info signs: for a single stems-up stem (`dirSign = +1`) with a head below the middle line, assert `idealYUpSs > headYUpSs` (the beam-side stem end is above the head, i.e. more positive Y-up) and `shortestYUpSs > headYUpSs` and `idealYUpSs >= shortestYUpSs`; mirror one stems-down case (`dirSign = -1`, ends below the head). Derive the expected values from the ported formula, not by running the code. This file is extended in Phase 2 and folded into the full suite in Phase 6. Run `./scripts/test.sh BeamScoringTest` — must pass. (Needs the public entry point from Phase 3; until then, make the ported per-stem method package-visible so the test can call it directly, or defer only this assertion's wiring — but write the test class and the sign assertions now.)

## ✅ Phase 2: Ideal Positions: Least-Squares + Damping + Region Shift

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Opus 4.8, high effort — three interacting numeric routines (fit, damping, feasibility clamp) ported across a Y-sign flip.

### Context

Extend `src/main/java/songscribe/layout/BeamScoring.java` (created by Phase 1, which defined `StemInput`, `StemInfo`, and all constants) with the "unquanted" ideal-position pipeline. Everything operates in scoring space per the **Conventions** section at the top of this plan file. The pipeline state to produce: `double unquantedLeftY`, `double unquantedRightY`, `double musicalDy` (the pre-damping least-squares dy, used by a Phase 3 scorer). Read each referenced LilyPond function before porting it.

### Tasks

1. Port `minimise_least_squares` (`~/Developer/projects/lilypond/lily/least-squares.cc:24-62`) as a private helper: standard least-squares slope/intercept over `(xSs, idealYUpSs)` points; degenerate denominator (or <2 points) → slope 0, offset = mean Y (0 if empty).
2. Port `set_minimum_dy` (`beam-quanting.cc:462-482`): if `dy != 0`, `dy = signum(dy) * max(|dy|, min(SIT_SS, min(INTER_SS, HANG_SS)))` using the shared quant-offset fields defined in Phase 1 task 2 (do not recompute `(bt - slt)/2` inline).
3. Port `least_squares_positions` (`beam-quanting.cc:537-604`) simplified per Conventions (all stems normal; first/last normal index = first/last stem):
   - `ideal[LEFT/RIGHT] = stemInfo.idealYUpSs` of first/last stem (`stem_ypositions_` = 0).
   - If `ideal[RIGHT] - ideal[LEFT] == 0`: the artificial-slope case — if `ideal[LEFT] == 0 && stems.size() == 2 && (last.headYUpSs - first.headYUpSs) != 0`, set `unquanted[towardHigherHead] = bt/2`, other side `-bt/2` (LilyPond `beam-quanting.cc:571-577`, `chord_start_y` = head Y-up ss); else `unquanted = ideal`. `musicalDy = unquantedRightY - unquantedLeftY`.
   - Else: least-squares fit over all stems' `(xSs, idealYUpSs)`; `dy = slope * xSpan`; apply `set_minimum_dy`; `unquanted = {y, y + dy}`; `musicalDy = dy`.
4. Port concaveness (`beam-quanting.cc:606-744`: `is_concave_single_notes`, `calc_positions_concaveness`, `calc_concaveness`): returns 0.0 when `stems.size() <= 2`; input positions are `stems[i].headHalfPos()` (integer Y-up half-spaces; single noteheads so close == far positions, and LilyPond averages two identical `calc_positions_concaveness` calls — compute once). Use LilyPond's `beam_dir * positions[i]` formulation directly with `beam_dir = dirSign` (positions are already Y-up, so no extra sign adjustment): covering interval from first/last, above&&below → concave; opposite-trend inner note at/past the closest edge (`beam-quanting.cc:645-655`, loop starts at `i = 2`) → concave; all inner strictly closer than both edges → concave; concave → return `CONCAVE_FORCE_FLAT`, else the normalized magnitude from `calc_positions_concaveness` (`beam-quanting.cc:667-689`).
5. Port `slope_damping` (`beam-quanting.cc:746-777`): skip if `stems.size() <= 1`; if `concaveness >= CONCAVE_FORCE_FLAT`, set `unquantedLeftY = unquantedRightY` and `musicalDy = 0` exactly as LilyPond does (note LilyPond then still applies the damping formula with `damping = 0` — mirror the exact control flow: `damping` local starts at `DAMPING = 1.0`, is zeroed in the concave branch, and the `damping != 0` guard skips the formula). Otherwise: `slope = 0.6 * tanh(dy / xSpan) / (DAMPING + concaveness)` (no `xSpan == 0` guard needed — the degenerate case short-circuits in `solve`, Phase 3 task 5, so this code only runs with `xSpan != 0`), `dampedDy = slope * xSpan`, `set_minimum_dy`, then `unquantedLeftY += (dy - dampedDy)/2; unquantedRightY -= (dy - dampedDy)/2`.
6. Port `shift_region_to_valid` (`beam-quanting.cc:779-892`) **without** the collision minefield (no collisions per Conventions — the minefield with zero forbidden intervals returns the input placement, so the surviving logic is only the stem-length feasibility clamp): `slope = (unquantedRightY - unquantedLeftY)/xSpan` (no `xSpan == 0` guard — degenerate groups short-circuit in `solve` before reaching here); feasible interval for the left Y = intersection over stems of the half-line bounded on the `-dirSign` side by `leftYBound_i = stemInfo_i.shortestYUpSs - slope * x_i` (for stems-up: `feasibleMin = max(leftYBound_i)`, leftY must be ≥ it; mirrored for stems-down). If `unquantedLeftY` violates the bound, move it to `bound + dirSign * 2.0`, keeping `dy` (`unquantedRightY = unquantedLeftY + dy`). **Not** the bound itself: with zero forbidden intervals both `feasible_beam_placements` are pushed to infinity whenever the input is outside `feasible_left_point`, so LilyPond takes the `point_in_interval(feasible_left_point, 2.0)` branch (`beam-quanting.cc:873`), and `point_in_interval` (`:442`) returns `v[DOWN] + dist` / `v[UP] - dist` on a half-line. Sitting on the edge instead would leave half the quant candidate window below the minimum stem length.
7. Run `./scripts/compile.sh` exactly — must report SUCCESS.
8. **Smoke test (extend Phase 1's).** Add one assertion to `BeamScoringTest` validating the unquanted flat case: for a symmetric concave triplet (3 stems, middle head far on the beam side) the pipeline through `slope_damping` must force flat — assert `unquantedLeftY == unquantedRightY` (within `BEAM_EPS`) and `musicalDy == 0`. Also add a monotonic-ascending 3-stem case asserting `unquantedRightY - unquantedLeftY` is nonzero with the sign matching the musical direction. Expose the post-Phase-2 pipeline state package-visibly for the test if the public entry point is not yet in place. Run `./scripts/test.sh BeamScoringTest` — must pass.

## ✅ Phase 3: Quant Generation + Demerit Scoring + Solve

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Opus 4.8, high effort — six scorers with fiddly per-edge geometry (`score_forbidden_quants` especially); a transposed sign silently picks wrong-looking beams instead of failing.

### Context

Extend `src/main/java/songscribe/layout/BeamScoring.java` (Phases 1–2 defined `StemInput`/`StemInfo`, all scoring constants, and produce `unquantedLeftY`/`unquantedRightY`/`musicalDy` in scoring space) with candidate generation and demerit scoring, and expose the single public entry point. Follow the **Conventions** section at the top of this plan file. Read each referenced LilyPond function before porting. Skip LilyPond's lazy priority-queue evaluation (`solve`, `beam-quanting.cc:1020-1081`) — candidate count is ≤ 256, so score every candidate fully and take the minimum (ties: first wins, matching a stable scan).

### Tasks

1. Define a candidate type `BeamConfiguration` (mutable class or record-with-accumulator): `double leftY, rightY, demerits`. Port `Beam_configuration::new_config` (`beam-quanting.cc:152-167`): `y[d] = (int) unquanted[d] + offset[d]` — the `(int)` cast is truncation toward zero, Java's `(int)` matches exactly; initial `demerits = (|offsetL| + |offsetR|) / 1000.0`.
2. Port the quant range (`beam-quanting.cc:343-360`, inside `init_instance_variables`): per edge `d` (first/last stem), the beam center must stay at least `0.5 + (edgeBeamCount_d - 1) * beamTranslation + bt/2` away from that stem's notehead on the stem side: for stems-up, `minLeftY_d = headYUpSs_d + (that widen amount)` is a lower bound on candidate `y[d]`; mirrored (upper bound) for stems-down. `edgeBeamCount_d` = `beamCount` of the first/last stem.
3. Port `generate_quants` (`beam-quanting.cc:894-956`) with the Conventions simplifications (no knee/collision `region_size` bumps; `grid_shift` requires `max_beam_count > 4` which cannot happen with ≤ 3 beams — omit): base quants `{STRADDLE_SS, SIT_SS, INTER_SS, HANG_SS}` (the shared quant-offset fields from Phase 1 task 2); offsets `i + q` for `i` from `-REGION_SIZE` to `REGION_SIZE - 1` inclusive (preserve LilyPond's asymmetric `i < region_size` loop) — 16 offsets per edge, 256 left×right combinations; drop candidates violating task 2's quant range.
4. Port the six applicable scorers as private methods, each adding to `config.demerits` (drop `score_collisions`; drop all `is_knee_`/`is_xstaff_` branches). Shared helper `shrinkExtraWeight(x, fac) = |x| * (x < 0 ? fac : 1)` (`beam-quanting.cc:122-127`):
   - `score_slope_ideal` (`:1214-1234`): `shrinkExtraWeight(|dampedDy| - |dy|, 1.5) * IDEAL_SLOPE_FACTOR` where `dampedDy = unquantedRightY - unquantedLeftY`, `dy = rightY - leftY`.
   - `score_slope_direction` (`:1175-1202`): sign mismatch between `dy` and `dampedDy` → `DAMPING_DIRECTION_PENALTY`, except when `dy == 0`: `HINT_DIRECTION_PENALTY` if `|dampedDy / xSpan| <= ROUND_TO_ZERO_SLOPE`, else full penalty. (No `xSpan == 0` guard — degenerate groups short-circuit in `solve`, so every scorer here runs with `xSpan != 0`.)
   - `score_slope_musical` (`:1204-1212`): `MUSICAL_DIRECTION_FACTOR * max(0, |dy| - |musicalDy|)`.
   - `score_stem_lengths` (`:1115-1173`, minus knee branches): per stem, `beamY` = linear interpolation of `(leftY, rightY)` at `x_i` (midpoint when `xSpan == 0`); `currentY = beamY`. **The `base_lengths_[i]` term is 0 — do not port it.** In LilyPond `base_lengths_[i]` is the French-beaming stem-end correction and is nonzero only when `french_count > 0`; SongScribe has no French-beaming mode, so `french_count = 0` for every group and the term is *structurally* 0 (not merely "probably" 0). This is the sole reason it can be dropped; when French beaming is added (**issue #652**), that work reintroduces this term. Demerit `+= STEM_LENGTH_LIMIT_PENALTY * max(0, dirSign * (shortestYUpSs_i - currentY))` and `+= STEM_LENGTH_DEMERIT_FACTOR * shrinkExtraWeight(dirSign * (currentY - idealYUpSs_i), 1.5)`; divide the total by `stems.size()` (single direction, so LilyPond's per-direction split collapses).
   - `score_forbidden_quants` (`:1263-1369`): full port. `extra_demerit = SECONDARY_BEAM_DEMERIT / max(edgeBeamCountL, edgeBeamCountR)`; per edge and per beam index `j = 1..edgeBeamCount_d`: gap interval between `y[d] - dirSign*((j-1)*beamTranslation + bt/2 - slt/2.2)` and `y[d] - dirSign*(j*beamTranslation - bt/2 + slt/2.2)` (fudge factor 2.2); for each staff line `k` in `{-2,-1,0,1,2}` (i.e. `-STAFF_RADIUS_SS..STAFF_RADIUS_SS` step 1, with `+BEAM_EPS` slack on the upper bound) inside the gap: `demerit += extra_demerit * (0.39 + (1-0.39) * min(|gapTop-k|,|gapBottom-k|) / gapLength * 2)`. Then the second block (`:1329-1366`): for edges with ≥2 beams within the staff, penalize `sit` quants for up-stems with `dy <= eps` / `hang` quants for down-stems with `dy >= eps`; for ≥3 beams, penalize `straddle` similarly — use `myModf(y) = y - floor(y)` per LilyPond.
   - `score_horizontal_inter_quants` (`:1245-1256`): if `dy == 0` and `|leftY| < STAFF_RADIUS_SS`, and `leftY - 0.5` is within 0.01 of an integer (LilyPond's `round_halfway_up` on `yshift` — round half-up), add `HORIZONTAL_INTER_QUANT_PENALTY`.
5. Add the public entry point, e.g. `public static BeamPosition solve(List<StemInput> stems, int dirSign, double forcedFraction)` returning `record BeamPosition(double leftYUpSs, double rightYUpSs)`:
   - **Degenerate short-circuit (single source of the zero-span guard).** Compute `xSpan` once. If `stems.size() < 2 || xSpan == 0`, return a trivial flat placement immediately (both edges at the single/first stem's `idealYUpSs`, or 0 if no stems) and skip the entire scoring pipeline. This is the *only* place `xSpan == 0` is handled: because scoring never runs in the degenerate case, the per-scorer routines (`slope_damping`, `shift_region_to_valid`, `score_slope_direction`, `score_stem_lengths`, `score_horizontal_inter_quants`) may assume `xSpan != 0` and need no inline zero guards — removing the scattered guards and the whole `NaN`-demerit-wins failure class.
   - Otherwise: run Phase 1 stem infos → Phase 2 pipeline → generate candidates.
   - **Empty-candidate fallback (observable, not silent).** If the candidate list is empty (all violated the quant range), return the unquanted position (LilyPond's fallback, `:1026-1031`), and emit a debug-level log line recording that the fallback fired (group size, `xSpan`, unquanted `(leftY, rightY)`) so the rare path is visible in the log rather than silently producing possibly-invalid geometry. Phase 6 adds a test that forces this path.
   - Else score all candidates fully and return the minimum-demerit candidate's `(leftY, rightY)`.
6. Run `./scripts/compile.sh` exactly — must report SUCCESS.

## ✅ Phase 4: Wire Into LayoutEngine

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Recommended model/effort:** Fable 5 (or Opus 4.8), high effort — deletes the old algorithm across a live method, crosses the Y-sign boundary in both directions, and re-targets three existing tests; the riskiest phase.

### Context

Replace the beam-geometry computation inside `LayoutEngine.calculateBeams` (`src/main/java/songscribe/layout/LayoutEngine.java:511-693`) with a call to `BeamScoring.solve` (created by Phases 1–3: input `List<BeamScoring.StemInput>` with `(xSs, headYUpSs, headHalfPos, beamCount)` per stem, plus `dirSign` and `forcedFraction`; output `BeamPosition(leftYUpSs, rightYUpSs)` — the Y-up center of the outer beam at the first/last stem's X). Follow the **Conventions** section at the top of this plan file, especially the output conversion. The `BeamLayout`/`StemLayout` contract, the beam-thickening block, and `computeBeamElementGeometry` are preserved; `BeamGroupRenderer`, `NoteRenderer`, `StructuralStacker`, and the MusicXML writer need no changes.

### Tasks

1. In `calculateBeams`, keep the per-beam loop shell (`beamStart`/`beamEnd`/`stemDirection` recovery) and the `elementToColumn` map. Replace everything from the `// Compute beam slope` comment through the flat-beam-snapping block (lines ~534-646: endpoint slope + hyperbolic damping, anchor search, `MIN_STEM_SS` placement, 20-iteration slope reduction, vertical deficit shift, and the `Math.abs(slope) < 0.05` 0.5-ss snap — the snap is superseded by quanting) with:
   - Build `List<BeamScoring.StemInput>` over `[beamStart, beamEnd]`, skipping elements with no column (mirroring `computeBeamElementGeometry`'s null guard): `xSs = column.getXSs() - firstColumnXSs`, `headYUpSs = -Staff.spToSs(element.getStaffPosition())`, `headHalfPos = -element.getStaffPosition()`, `beamCount = BeamMath.beamCount(element)`. If fewer than 1 stem resolves, keep the existing degenerate behavior (slope 0, `startYSs` 0).
   - `forcedFraction`: count of members with `element.getStaffPosition() != 0 && element.getDirection() != StaffElement.defaultDirection(element)` divided by member count (LilyPond `Beam::forced_stem_count`, `beam.cc:1276-1294`: middle-line heads — `|chord_start_y| <= 0.1` — are never "forced"; `StaffElement.defaultDirection` is at `src/main/java/songscribe/dom/StaffElement.java:543-547`).
   - Call `BeamScoring.solve(stems, dirSign, forcedFraction)` with `dirSign = stemDirection.isUp() ? 1 : -1`; convert per Conventions: `startYSs = -result.leftYUpSs() - dirSign * LineThickness.BEAM_THICKNESS_SS / 2.0`; `slope = xSpan != 0 ? -(result.rightYUpSs() - result.leftYUpSs()) / xSpan : 0.0`.
2. Delete now-dead pieces: the `BEAM_SLOPE_MAX` constant (line 85) and, if no longer referenced, `MIN_STEM_SS`'s beam-path uses — note `MIN_STEM_SS` **stays** (still used by `calculateUnbeamedStems` and by task 3's clamped fields). Keep `BEAM_DEPTH_SS`, the thickening block (lines ~648-653), and `computeBeamElementGeometry` unchanged.
3. In the `StemLayout` build loop (lines ~659-688), populate the length fields against the standard stem length so `NoteRenderer.renderStem` (`src/main/java/songscribe/ui/renderer/NoteRenderer.java:304-308`, formula `geom.lengthSs() + lengtheningSs - forcedShorteningSs`) reproduces the quanted tip: `lengtheningSs = Math.max(0.0, stemLenSs - MIN_STEM_SS)` and `forcedShorteningSs = Math.max(0.0, MIN_STEM_SS - stemLenSs)` (replacing the unclamped `stemLenSs - MIN_STEM_SS` and the hardcoded `0.0`). Update the `forcedShorteningSs` Javadoc on `LayoutResult.StemLayout` (`src/main/java/songscribe/layout/LayoutResult.java:1415-1417`): for beamed notes it is now the quanted-geometry shortening below the standard stem length; the Ross & Gourlay forced-direction wording applies only to the unbeamed path.
4. **Scope the `FORCED_STEM_FLOOR_SS` clamp to the unbeamed path (correctness — the quanted length must be authoritative for beamed notes).** `NoteRenderer.renderStem` (line ~307) currently computes `stemLength = Math.max(NoteGeometry.FORCED_STEM_FLOOR_SS, geom.lengthSs() + lengtheningSs - forcedShorteningSs)`, and `FORCED_STEM_FLOOR_SS = STEM_LENGTH_SS - MAX_FORCED_SHORTEN_SS = 2.5`. LilyPond's quanting legitimately produces beamed stems shorter than 2.5 ss (the extreme-minimum free length bottoms out around ~2.25 ss, shorter for stacked/steep groups), so the `Math.max` would silently lengthen a short quanted stem back to 2.5 while the beam stays at the quanted position — the drawn stem overshoots the beam edge. Fix: apply the 2.5-ss floor **only when the note is not beamed**; for a beamed note use `geom.lengthSs() + lengtheningSs - forcedShorteningSs` verbatim (the `beamed` flag is already in scope in `renderStem`). Update the now-stale comment at `NoteRenderer.java:305-307` ("The forced-shortening formula already caps at the floor; guard here defensively" — true only for the unbeamed path now) and audit `StructuralStacker.java:201`'s `forcedShorteningSs` reference for the same wording drift.
5. Re-target the existing geometry-coupled tests in `src/test/java/songscribe/layout/LayoutEngineTest.java` that encode old-algorithm behavior (minimal semantic updates forced by the algorithm change — new coverage belongs to Phase 6, do not add tests here). These are the *only* beam tests that legitimately change; T12a/T12b/T13 assert direction only, T18 stub direction and T30 beam count are unaffected, so a failure in any of those is a real port bug (see task 7):
   - **T14** (`testBeamSlopeWithLargePitchDifferenceIsDampened`, lines ~494-512): references deleted `LayoutEngine.BEAM_SLOPE_MAX`. Assert instead that `|slope|` is well below the raw pitch slope — bound it by `BeamScoring.SLOPE_DAMPING_COEFFICIENT` plus a named quant-slack constant (one quant step over the group's X span; derive in the test, no magic number).
   - **T15** (`testBeamedGroupAllStemsAtLeastMinimumStemLength`, lines ~514-538): the 3.5-ss floor no longer holds — quanting allows shorter stems. Assert every stem length ≥ the extreme minimum: `BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS[0] + bt/2` for 1-beam groups (derive from `BeamScoring` constants; make them package-visible if needed), minus `TOLERANCE`.
   - **T16** (`testFlatBeamSnappingSnapsStartYSsToHalfSpaceGrid`, lines ~540-559): the 0.5-grid snap is gone. Recover the beam center from the layout (`centerYUp = -(startYSs + dirSign * bt/2)` per Conventions — the test's group is stems-up, `dirSign = 1`) and assert its fractional part `myModf(centerYUp)` is within `TOLERANCE` of one of `{STRADDLE_SS, SIT_SS, INTER_SS, HANG_SS}` (the shared quant-offset fields from Phase 1 task 2 — import them, do not recompute `(bt - slt)/2` in the test).
   - **T17** (`testSlopedBeamHasThickeningInBoundedRange`, lines ~561-584): **verify, re-target only if it flattens.** T17 asserts `thickeningSs > 0` (strict) for a 2-note group straddling the middle line, which holds only if quanting keeps that group sloped. Run it: if it still passes, leave it untouched. If the new algorithm flattens the group (slope 0 → `thickeningSs == 0`), that is a *legitimate* algorithm change, not a port bug — re-target it to assert `thickeningSs` is in `[0, BEAM_DEPTH_SS * 0.088]` and note the flatten. This is the one exception to task 7's "failure = port bug" rule; do not chase it into the production code.
6. Run `./scripts/compile.sh` exactly — must report SUCCESS.
7. Run `./scripts/test.sh unit`. All unit tests must pass. Beyond the tests in task 5, do not edit tests to make them pass — a failure elsewhere means a port or conversion bug; fix the production code. (E2e tests may also encode old beam geometry; they require user approval to run — do NOT run them; note any expected e2e impact in the completion report for Phase 5's user session.)

## ⏳ Phase 5: Manual Verification

**Status:** Pending  <br>
**BlockedBy:** 4  <br>
**Recommended model/effort:** N/A — manual verification by the user; do not delegate to a subagent.

### Tasks

1. Run `./scripts/run.sh` (user permission granted for this phase) and enter the issue-#579 triplet: three eighth notes under one beam, middle note far above its neighbors (`g8 d'8 e,8` from `/Users/aparajita/Documents/Centre/Music/SongScribe songs/minimal.ly`). Compare against LilyPond's rendering of that file: the beam should be flat (concaveness-forced), sit at a valid quant, and the outer stems should be shortened comparably to LilyPond's.
2. Check ordinary sloped beams (ascending/descending 2–4 note groups at various pitches, 8th/16th/32nd): each beam edge should land cleanly relative to staff lines (straddle/sit/inter/hang), never leaving a thin sliver of white between beam and staff line, with no wild slopes.
3. Check groups with forced-direction stems (e.g. groups straddling the middle line, manual direction overrides) and stacked-beam groups (16th/32nd): stems visibly shortened but legible, secondary beams not colliding with staff lines.
4. Open several existing songs with beamed content and confirm no regressions (beams into noteheads, tuplet brackets/articulations still clearing beams — the stacker adapts via `StemLayout.topYSs`). Confirm resolution of issue #579 before Phase 6 proceeds.

## ⏳ Phase 6: Test Coverage

**Status:** Pending  <br>
**BlockedBy:** 5  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — mostly mechanical test authoring, but `BeamScoring` expectation values must be derived from the ported formulas, not guessed.

### Context

`BeamScoring` (`src/main/java/songscribe/layout/BeamScoring.java`) is a pure class — test it directly with synthetic `StemInput` lists (no `Line`/layout fixtures needed). `BeamScoringTest` already exists from the Phase 1/2 smoke assertions; this phase **extends** it into the full suite (keep the smoke assertions). Integration tests go in `src/test/java/songscribe/layout/LayoutEngineTest.java` following its conventions (T-numbered comments — continue from the highest existing number; named `SP_*` constants; `detachedLine()`/`engine()`/`require()` helpers; no raw numeric literals per project rules; import `BeamScoring`'s package-visible quant-offset fields rather than recomputing them). Before writing tests read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md`.

### Tasks

1. Extend `BeamScoringTest` with direct pipeline tests: (a) concave contour (3 stems, middle head closer to beam) → resulting `leftYUpSs == rightYUpSs` exactly or within a quant of flat; (b) monotonic ascending contour → nonzero dy whose sign matches the musical direction; (c) both edge Ys of any solve land on valid quant positions (fractional part in `{STRADDLE_SS, SIT_SS, INTER_SS, HANG_SS}` within `BEAM_EPS`); (d) `forcedFraction = 1.0` yields stem-end positions strictly closer to the noteheads than `forcedFraction = 0.0` for the same input (verifies `calc_stem_shorten` integration).
2. Add a `BeamScoringTest` case for the stem-length floor: for a steep contour, no stem's quanted length (distance from `headYUpSs` to the beam line at its X, direction-adjusted) falls below `BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS`-derived minimum minus a small tolerance (soft floor via `STEM_LENGTH_LIMIT_PENALTY` + region shift — assert with the tolerance, not exact).
3. **Branch coverage for concaveness and the artificial-slope path** (each is a distinct sign-error site). Add a `BeamScoringTest` case per concave trigger, asserting each independently forces flat: (a) covering interval above && below (middle head straddles beyond both edges); (b) opposite-trend inner note at/past the closest edge (`beam-quanting.cc:645-655`); (c) all inner notes strictly closer to the beam than both edges. Add (d) the 2-note equal-ideal artificial-slope branch (Phase 2 task 3: `ideal[LEFT] == ideal[RIGHT] == 0`, `stems.size() == 2`, heads differ) → asserts the toward-higher-head edge sits at `+bt/2` and the other at `-bt/2`.
4. **Degenerate short-circuit** (`solve` short-circuit, Phase 3 task 5). Add a `BeamScoringTest` case: a single-stem group and a two-stem group with identical X (`xSpan == 0`) each return a finite flat placement (`leftYUpSs == rightYUpSs`, no `NaN`/`Infinity`), confirming scoring is skipped rather than dividing by zero.
5. **Empty-candidate fallback** (`solve`, Phase 3 task 5). Add a `BeamScoringTest` case that forces every candidate out of the quant range (e.g. a pathologically wide interval where no offset satisfies both edges' quant ranges) → `solve` returns the unquanted position, the result is finite, and the debug log records the fallback. If constructing a genuinely empty candidate set proves infeasible with realistic inputs, assert that explicitly (document why the path is unreachable) rather than leaving it untested.
6. In `LayoutEngineTest`, add the issue-#579 integration test: three quavers with the middle note's staff position far on the beam side (concave hump, mirroring `g8 d'8 e,8`) → `beamLayout.slope()` within `TOLERANCE` of 0 and each outer stem's `forcedShorteningSs() >= 0` with stem length below `SMuFLConstants.STEM_LENGTH_SS` (shortened) — assert the middle stem is the longest.
7. **Guard the beamed-stem floor scoping** (Phase 4 task 4). In `LayoutEngineTest`, add a case where a steep or stacked group produces a beamed stem whose quanted length is below `NoteGeometry.FORCED_STEM_FLOOR_SS` (2.5 ss) — i.e. `StemLayout.forcedShorteningSs()` exceeds `NoteGeometry.MAX_FORCED_SHORTEN_SS` — and assert the effective/rendered stem length equals the quanted length (is **not** floored back up to 2.5). This pins the correctness fix so a future re-hardening of the floor can't silently reintroduce stem overshoot.
8. In `LayoutEngineTest`, add: (a) a forced-direction group (member with `staffPosition != 0` whose resolved direction opposes `StaffElement.defaultDirection`) → that layout completes and the group's stems are shorter than an otherwise-identical unforced group's; (b) a regression guard that an all-natural-direction group yields `forcedFraction`-independent geometry (equal to itself across two identical layouts — deterministic output).
9. Run `./scripts/compile.sh` exactly, then `./scripts/test.sh unit`. Everything must pass (SUCCESS/green). If e2e updates were flagged in Phase 4's report, list them for the user (e2e runs need user approval — do not run without it).
