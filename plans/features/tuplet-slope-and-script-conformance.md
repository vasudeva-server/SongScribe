# Tuplet Bracket LilyPond Conformance — Slope & Script Clearance
**Parent feature:** [tuplet-angled-brackets.md](./tuplet-angled-brackets.md) (#509)  
**Base branch:** `develop`  
**Branch:** `509-tuplet-rendering`  
**Supersedes:** [tuplet-lilypond-conformance.md](./tuplet-lilypond-conformance.md) — that plan's diagnosis predates the stacking rework; its numbers and one-line fix are stale. This plan is a **fresh, source-verified** re-diagnosis measured against LilyPond 2.26.0 on 2026-07-12.

A side-by-side against LilyPond on the `tuplet.ly` / `tuplet-test.musicxml` counterpart shows two independent discrepancies, both isolated to `StructuralStacker` and both confirmed against `lily/tuplet-bracket.cc` with exact numeric agreement:

1. **The bracket slope is ~19% too steep** (0.234 vs LilyPond 0.197).
  
2. **The trailing arm sits ~0.33 ss too far from the accent below it** (gap 1.43 vs LilyPond 1.10).
  
## Test case
`~/Documents/Centre/Music/SongScribe songs/tuplet.ly` (LilyPond) and its `tuplet-test.musicxml` twin (SongScribe): a `\tupletUp \tuplet 2/3` — number **2** — over three notes **B** (high, above staff), **D** (mid), **E** (low, in staff, carrying `\accent \staccato`). Descending contour, so the bracket tilts down left→right. Render LilyPond with `/opt/homebrew/bin/lilypond`.
## Ground truth (all re-measured this session, sign-flipped to LilyPond up-positive)
LilyPond geometry extracted via a `TupletBracket.stencil` hook dumping `positions`, `X-positions`, and the note-column / script grob extents (scratch `.ly` in the session scratchpad). SongScribe geometry from a `[TUPLET-DBG]` `LOG.info` dump in `StructuralStacker.stackTuplets`.

| Quantity | SongScribe | LilyPond | Δ |
|---|---|---|---|
| Slope \|rise/run\| | **0.234** | **0.197** | **+19% steeper** |
| Left arm height | 6.76 | 6.34 | +0.43 too high |
| Right arm height (at arm edge) | 4.67 | 4.59 | +0.08 |
| **Gap: trailing arm → accent, at the accent's own X** | **1.43** | **1.10** | **+0.33 too far** |

LilyPond constants (grob properties): `padding=1.1`, `staff-padding=0.25`, `max-slope-factor=0.5`, `edge-height=0.7`, `thickness=1.6`. LilyPond internal geometry (system coords, up-positive): `X-positions=(0.0 . 8.870)` where `x0=17.121` (B column left edge) → `x1=25.991` (E column right edge); note-column tops B `4.045`, D `1.545`, E `2.045`; accent (outer script) top `3.620` at center `x≈25.34` (`x_rel = 8.22`, i.e. **0.65 ss left of x1**); staccato top `2.7`.
## Root cause 1 — trailing arm too far from the accent
**LilyPond** (`tuplet-bracket.cc`, `calc_position_and_height`, lines 680–717) folds each **script** into the vertical-clearance point set at the script's **own center X** (`script_x.center()`, line 702), and folds only the flat **staff line** at the outer bounds `x0`/`x1` (lines 633–634). The offset is then `max over points( y − dy·x/(x1−x0) ) + padding` (lines 706–717). Verified: the accent point `(x_rel=8.22, top=3.620)` reproduces `positions.left = 6.336` exactly, and the line evaluated at the accent's X clears it by exactly `padding` (1.10).

**SongScribe** (`StructuralStacker.computeTupletClearanceLeftYSs`) folds the **end column's combined obstacle** — `columnObstacleTopSs = min(tip, articulationCeiling)`, i.e. the accent — projected from the **right-arm edge X** (`rightArmXSs`, 1.38 ss past the note). Verified: `rawCeiling = accent(−3.570) − slope·(rightArmXSs − anchorXSs) = −5.617`, matching the live dump. Because the bracket descends left→right, projecting the accent from an X that far to the right over-lifts the whole trailing region by `slope · 1.38 ≈ 0.33` — the entire discrepancy.

The interior loop **already** folds every spanned column's full obstacle (tip + articulation) at its own center X, so E's accent is already cleared correctly there (candidate ceiling −5.292). The arm-edge fold then over-rides it with the more-binding −5.617. **The arm-edge fold must not carry the articulation** — only the notehead tip (arm-reach over the note is a real, SongScribe-specific concern because our arms extend `ARM_EXTENSION_SS=0.2` past the note; LilyPond's arms sit at the note edge).
## Root cause 2 — slope too steep
**LilyPond** (lines 520–560) floors each outer endpoint to the **staff extent widened by** `staff-padding` **0.25** (`rv.unite(staff)` / `lv.unite(staff)`), then takes the rise as the difference of the floored tops. An endpoint whose own top is inside the staff contributes the staff edge, not its tip. Here the low endpoint **E** (top 2.045) floors up to **2.25**; B (top 4.045) stays. Rise = `2.25 − 4.045 ≈ −1.745`.

**SongScribe** (`computeTupletSlopeDySs`) takes the rise from **raw note tips** with no staff floor: `tipRise = rightEnd.getAbsoluteTopYSs() − leftEnd.getAbsoluteTopYSs()`. E contributes its raw tip (−2.09 layout) instead of the staff floor (−2.25), inflating the rise from 1.84 to 2.00.

Decomposition (reproduces LilyPond within the spacing-engine tolerance):

| Step | slope |
| --- | --- |
| SongScribe raw (rise 2.00 / edgeRun 8.54) | 0.234 |
| **+ floor low endpoint E to staff (−2.09 → −2.25): rise 1.84** | **0.216** |
| LilyPond (rise 1.745 / run 8.87) | 0.197 |

The staff-floor is the fix and removes the bulk of the error (0.234 → 0.216). The residual 0.216 vs 0.197 (~9%) is the **different horizontal spacing engine** (SongScribe `edgeRunSs` 8.54 vs LilyPond `x1−x0` 8.87) plus a minor notehead-height metric — **not bugs**. `edgeRunSs` is already SongScribe's correct analog of LilyPond's `x1−x0` (outer note-column edge span); **do not** change the run basis.
## Interaction
The +0.43 left-arm error = **0.08** (LilyPond places the accent 0.08 ss lower — a script-placement detail, not a bracket bug) + **0.35** (the steeper slope's extra rise). The two fixes are independent: the slope fix removes the 0.35, the script-projection fix removes the 0.33 trailing-arm gap.
## Coordinate convention
Layout Y is **up-negative** (middle line = 0, higher on page = smaller Y); the bracket is **always above** (`dir = UP`). LilyPond is up-positive, so a SongScribe layout Y of `−h` compares to LilyPond `+h`. "Higher of tip / staff edge" is therefore `Math.min` in layout Y.
## Constants (verified)
- `StackingUtils.STAFF_TOP_Y_SS = −2.0` (top staff line centerline). LilyPond floors to the line **centerline** widened by staff-padding (`staff.widen(0.25)` on the staff-**symbol** extent ±2), so the slope floor is `STAFF_TOP_Y_SS − TUPLET_STAFF_PADDING_SS = −2.25` — the **centerline**, not `STAFF_TOP_INK_Y_SS`.
  
- `Tuplet.MAX_SLOPE_FACTOR = 0.5`, `Tuplet.bracketLineOffsetSs() = 0.578`.
  
- `StructuralStacker.TUPLET_ARM_MARGIN_SS = 0.4`, `TUPLET_BRACKET_PADDING_SS = 1.1`.
  
## Touchpoints
| Concern | File / symbol |
| --- | --- |
| Slope staff-floor (RC2) | `layout/stacking/StructuralStacker.java` → `computeTupletSlopeDySs` |
| New constant | `StructuralStacker` → `TUPLET_STAFF_PADDING_SS = 0.25` |
| Script projection (RC1) | `StructuralStacker` → `computeTupletClearanceLeftYSs`, `columnObstacleTopSs` |
| Instrumentation (remove) | `StructuralStacker` → `LOG`, the `[TUPLET-DBG]` block, slf4j imports |
| Tests | `test/.../layout/stacking/StructuralStackerTest.java` |
## Status Dashboard
| Phase | Description | Status |
| --- | --- | --- |
| 1   | Slope staff-floor fix (RC2) | ☑ Done |
| 2   | Script-at-center clearance fix (RC1) | ☑ Done |
| 3   | Re-measure vs LilyPond; remove instrumentation | ☑ Done |
| 4   | Unit tests | ☑ Done |

* * *
## Phase 1 — Slope staff-floor fix (Root cause 2)
**BlockedBy:** —  
**Recommended model/effort:** Opus 4.8, medium — one sign-aware `Math.min` per endpoint plus a constant; the risk is the up-negative floor direction.

Floor each outer endpoint to the staff-top ceiling before computing the rise, mirroring LilyPond's `rv.unite(staff)` / `lv.unite(staff)`. Keep `edgeRunSs` as the run (already the correct analog).
### Tasks
1. Add to `StructuralStacker`, next to `TUPLET_BRACKET_PADDING_SS`:
  
  ```java
  /** LilyPond TupletBracket.staff-padding: the outer endpoints floor to the staff-line centerline
      widened by this before the slope rise is taken (tuplet-bracket.cc, staff.widen). */
  public static final double TUPLET_STAFF_PADDING_SS = 0.25;
  ```
  
2. In `computeTupletSlopeDySs`, replace the raw `tipRise` with a staff-floored rise. Up-negative ⇒ "higher of tip / staff edge" is `Math.min`:
  
  ```java
  var staffTopCeilingYSs = StackingUtils.STAFF_TOP_Y_SS - TUPLET_STAFF_PADDING_SS; // −2.25
  var leftTopYSs  = Math.min(leftEnd.getAbsoluteTopYSs(),  staffTopCeilingYSs);
  var rightTopYSs = Math.min(rightEnd.getAbsoluteTopYSs(), staffTopCeilingYSs);
  var tipRise = rightTopYSs - leftTopYSs;
  ```
  
  The veto (Step 2) still uses raw `getStaffPosition()` sign — a floored endpoint keeps its pitch sign, so the veto is unaffected. `edgeRunSs`, the clamp (Step 3), and `dySs = slope × widthSs` are unchanged.
  
3. Update the method Javadoc: the rise is taken from **staff-floored** outer tops, not raw tips, and why (LilyPond `unite(staff)`); note the residual vs LilyPond is the spacing engine, not a bug.
  
4. `./scripts/compile.sh` → SUCCESS.
  

**Expected after this phase (unverified until Phase 3):** slope 0.234 → ≈0.216; left arm drops ≈0.35.

* * *
## Phase 2 — Script-at-center clearance fix (Root cause 1)
**BlockedBy:** — (independent of Phase 1; different method)  
**Recommended model/effort:** Opus 4.8, high — the correctness core: separating the notehead-tip arm-reach fold from the script fold without regressing the interior clearance.

Stop projecting the articulation from the arm-edge X. Split the obstacle so the **arm-edge fold uses the notehead tip only**, while the **interior loop keeps the full tip+articulation obstacle at each column's own center X** (which already clears scripts correctly, mirroring LilyPond's `script_x.center()`).
### Tasks
1. Add a private helper alongside `columnObstacleTopSs`:
  
  ```java
  /** The notehead/stem tip a column presents to the extended arm, staff-top-clamped, WITHOUT any
      articulation. The arm-reach fold uses this so a script is never projected from the arm edge —
      LilyPond folds scripts only at their own center X (tuplet-bracket.cc, avoid-scripts). */
  private static double columnTipTopSs(ElementColumn column) {
      return StackingUtils.staffTopClampSs(column.getAbsoluteTopYSs());
  }
  ```
  
2. In `computeTupletClearanceLeftYSs`, leave the interior loop unchanged (it folds the full `columnObstacleTopSs` — tip **and** articulation — at each `column.getXSs()`, clearing scripts at their own X). Change **only the two arm-edge folds** to use `columnTipTopSs` instead of `columnObstacleTopSs`:
  
  ```java
  var anchorTipTopYSs = columnTipTopSs(anchorColumn);
  var endTipTopYSs    = columnTipTopSs(endColumn);
  leftYSs = Math.min(leftYSs, anchorTipTopYSs - slope * (leftArmXSs  - anchorXSs));
  leftYSs = Math.min(leftYSs, endTipTopYSs   - slope * (rightArmXSs - anchorXSs));
  ```
  
3. Update the method Javadoc's "Arm reach" paragraph: the arm-edge fold clears the extended arm over the **notehead tip** only; scripts are cleared at their own center by the interior loop, matching LilyPond.
  
4. `./scripts/compile.sh` → SUCCESS.
  

**Expected after this phase (unverified until Phase 3):** the accent binds via the interior loop at E's center; trailing-arm gap 1.43 → ≈1.10.
### Flagged uncertainty (resolve during implementation)
The interior loop projects the accent from E's **column center** (`E.getXSs()`), whereas LilyPond uses the **script's center**. Accent and note share a center here (script `x≈25.34` = E column center 25.34), so column center is a faithful proxy. If a future case has an off-center script this proxy drifts; out of scope now — note it, don't build for it.

* * *
## Phase 3 — Re-measure & remove instrumentation
**BlockedBy:** 1, 2  
**Recommended model/effort:** Sonnet 4.6, low — mechanical run/compare/cleanup.

1. `./scripts/compile.sh` → SUCCESS, then run the app on the test file (dump lands in `~/Library/Logs/SongScribe/songscribe.log`): `./scripts/run.sh "~/Documents/Centre/Music/SongScribe songs/tuplet-test.musicxml" --truncate-log`
  
2. Read the `[TUPLET-DBG]` line and confirm against the ground-truth table:
  
  - `drawnSlope` ≈ **0.216** (within the ~9% spacing-engine tolerance of LilyPond 0.197 — **do not chase the run**);
    
  - trailing-arm gap at the accent's X ≈ **1.10** (compute `bracketLine(at E.getXSs()) − accentCeiling`; the dump prints the accent ceiling in the contour as `art=…`).
    
3. Remove the temporary instrumentation from `StructuralStacker`: the `[TUPLET-DBG]` block in `stackTuplets`, the `LOG` field, and the two slf4j imports. `./scripts/compile.sh` → SUCCESS.
  

* * *
## Phase 4 — Unit tests
**BlockedBy:** 1, 2, 3  
**Recommended model/effort:** Sonnet 4.6, low. Read `.agents/guides/testing-common.md` and `testing-unit.md` first.

Cover the two pure helpers in `StructuralStackerTest`:

1. **Slope staff-floor** — a tuplet whose low endpoint sits **in** the staff yields a slope computed from the staff-floor `−2.25`, not the raw tip: assert `dySs` matches the floored rise, and that an endpoint already **above** the staff is unaffected (its tip < −2.25 wins the `Math.min`).
  
2. **Script at center** — a spanned end note carrying an accent produces a `leftYSs` bound by the accent projected from the note's **center X**, not the arm-edge X: assert the resulting bracket line clears the accent by the margin at the note's X, and that a bare notehead (no articulation) is still folded at the arm edge (regression guard for arm reach).
  
3. `./scripts/compile.sh` → SUCCESS, then `./scripts/test.sh StructuralStackerTest` → green (unit).
  

* * *
## Verification (whole plan)
- `./scripts/compile.sh` → SUCCESS after every phase.
  
- `[TUPLET-DBG]` for `tuplet-test.musicxml` (before removal): slope ≈ 0.216, trailing-arm→accent gap ≈ 1.10 — both within tolerance of the LilyPond ground truth.
  
- Phase 4 unit tests green.
  
- Instrumentation removed; no `[TUPLET-DBG]` / `LOG` left in `StructuralStacker`.
  
- Visual: on `./scripts/run.sh`, the descending tuplet's bracket is visibly shallower and its trailing arm sits close above the accent, matching `tuplet.pdf`. (Visual approval deferred to user.)
