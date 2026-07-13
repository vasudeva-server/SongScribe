# Tuplet Bracket LilyPond Conformance (padding & angle)

**Parent feature:** [tuplet-angled-brackets.md](tuplet-angled-brackets.md) (#509)  <br>
**Base branch:** `develop`  <br>
**Status:** Diagnosed, **not applied** — blocked on #514.

After the angled-bracket feature landed, a side-by-side against LilyPond on the
`tuplet.ly` / `tuplet.mssw` counterpart showed the bracket is **~19% too steep**
and sits **~0.42 ss too high**. Both causes were isolated empirically against
LilyPond ground truth. This plan captures the reference data and the fix so the
work can resume cleanly.

## Blocked by #514

The `514-lilypond-ties` worktree rewrites the stacking layer
(`StructuralStacker`, `StackingUtils`) and **deletes `layout/Neighbor.java`**.
The tuplet slope/clearance code lives in those same files, and the articulation
clearance depends on `Neighbor.ACCENT` / `Neighbor.STACCATO`. Applying the fix
before 514 lands would only produce conflicts and get re-ported. **Do this on
top of 514, not before it.**

## Test case

`~/Documents/Centre/Music/SongScribe songs/tuplet.ly` (and its `.mssw` twin): a
`\tupletUp \tuplet 2/3` — number **2** — over three notes: **B** (high, above the
staff), **D** (mid), **E** (low, in the staff, carrying `\accent \staccato`).
Descending contour, so the bracket tilts down left→right.

Render LilyPond with `/opt/homebrew/bin/lilypond`.

## LilyPond reference geometry (ground truth)

Extracted by overriding `TupletBracket.stencil` with a Scheme hook that forces
`(ly:tuplet-bracket::print grob)` then reads the grob properties. LilyPond Y is
**up-positive**, staff-center = 0, staff-space units.

| Property | Value |
|----------|-------|
| `X-positions` (x0→x1, arm-edge bounds) | `(0.0 . 8.870)` → run **8.870 ss** |
| `positions` **with** `\accent \staccato` | `(6.336 . 4.591)` |
| `positions` **without** articulations | `(5.145 . 3.400)` |
| Rise (right − left), **both** variants | **−1.745 ss** |
| `padding` (tip → line) | 1.1 |
| `staff-padding` | 0.25 |
| `max-slope-factor` | 0.5 |
| `edge-height` (arm length) | 0.7 |
| `thickness` | 1.6 |

Reproduce with a scratch `.ly`:

```lilypond
#(define (dump-bracket grob)
   (let* ((stil (ly:tuplet-bracket::print grob))
          (xpos (ly:grob-property grob 'X-positions))
          (pos (ly:grob-property grob 'positions)))
     (format #t "\n[LY] X-positions=~a positions=~a\n" xpos pos)
     stil))
\override TupletBracket.stencil = #dump-bracket
```

`control-points` is empty at `after-line-breaking`; the geometry is in
`positions` / `X-positions`, computed lazily — force it via the `stencil` hook
above.

## SongScribe runtime (from the `[TUPLET-DBG]` dump)

Layout Y is **up-negative**, so a SongScribe line-Y of `−h` corresponds to
LilyPond `+h`.

- `anchorXSs=85.97 endXSs=93.33 widthSs=7.36`
- tips: B `(x=85.97, top=−4.09, sp=−7)`, D `(x=89.65, top=−1.59, sp=−2)`,
  E `(x=93.33, top=−2.09, sp=−3)`
- `dySs=1.7237 slope=0.2342`
- clearance `rawCeiling=−5.660 → finalLeftYSs=−7.338`
- **bracket line: left=−6.760, right=−5.037** (rise 1.724)
- constants: `PADDING=1.1 ARM_MARGIN=0.4 bracketLineOffset=0.578`

## Comparison (sign-flipped to compare)

| | SongScribe | LilyPond | Δ |
|--|--|--|--|
| Left line height | 6.76 | 6.34 | +0.42 ss high |
| Right line height | 5.04 | 4.59 | +0.45 ss high |
| Rise (contour) | 2.00 | 1.745 | +15% |
| Slope run | 8.54 (edge run) | 8.87 (x0→x1) | −4% |
| **\|slope\|** | **0.234** | **0.197** | **+19% steep** |
| Line → tip padding | 1.10 | 1.10 | ✅ |

## Root cause 1 — angle too steep (the fix)

`StructuralStacker.computeTupletSlopeDySs` takes the rise from **raw note tips**.
LilyPond first **floors each outer endpoint to the staff extent widened by
`staff-padding`** (`rv.unite(staff)` / `lv.unite(staff)` in `tuplet-bracket.cc`,
`calc_position_and_height`): an endpoint low in the staff contributes the staff
edge, not its own tip. Here the low endpoint **E** (tip +2.09) is floored to the
staff top +2.25, cutting the rise 2.0 → 1.745 (~15% of the error). The remaining
~4% is the run basis: SongScribe's `edgeRunSs` (8.54, head-edge outer bounds) is
narrower than LilyPond's `x0→x1` (8.87) — a note-head-width glyph-metric
difference, **not a bug**; leave it.

**Fix** — add a `staff-padding` constant (0.25) and floor both endpoints before
the rise. Up-negative ⇒ "higher of tip / staff edge" is `Math.min`:

```java
public static final double TUPLET_STAFF_PADDING_SS = 0.25; // LilyPond TupletBracket.staff-padding

// in computeTupletSlopeDySs, replacing the raw tipRise:
var staffTopCeilingYSs = -Staff.STAFF_HALF_SS - TUPLET_STAFF_PADDING_SS;
var leftTopYSs  = Math.min(leftEnd.getAbsoluteTopYSs(),  staffTopCeilingYSs);
var rightTopYSs = Math.min(rightEnd.getAbsoluteTopYSs(), staffTopCeilingYSs);
var tipRise = rightTopYSs - leftTopYSs;
```

Estimated post-fix slope ≈ 0.215 (vs LilyPond 0.197) — within the head-width run
difference. **This estimate is unverified at runtime** — 514 preempted the
re-run (see the re-measure task below).

## Root cause 2 — padding (already conformant; re-measure after the slope fix)

Scripts **are** part of LilyPond's offset sweep: removing `\accent \staccato`
raised the bracket by **1.19 ss** (positions `(6.336,4.591)` → `(5.145,3.400)`),
so LilyPond clears accents/staccato — SongScribe's `articulationCeilingSs`
folding (via `Neighbor.ACCENT` / `STACCATO`) is **correct**. Critically the
**rise is −1.745 in both variants**: scripts shift the vertical **offset only,
never the slope**. So the line→tip padding (1.1) is already right; the residual
~0.42 ss height is a knock-on of the steeper slope. Fix the slope first, then
re-measure — do **not** touch the accent folding.

## Tasks

1. **Re-establish the debug dump.** Re-add the temporary geometry dump to
   `StructuralStacker.stackTuplets` (tag `[TUPLET-DBG]`, via `LOG.info` so it
   lands in `~/Library/Logs/SongScribe/songscribe.log`): per-tip contour,
   `dySs`/slope, arm Xs, raw-ceiling vs final Y, the padding constants, and the
   resolved bracket-line Y at each end. Remove again once verified.
2. **Apply the slope fix** (Root cause 1) in the post-514 `computeTupletSlopeDySs`.
   Confirm the conformant rewrite still floors endpoints to the staff — if 514
   already does, this may be a no-op.
3. **Re-measure (mandatory).** Open `tuplet.mssw`, read the `[TUPLET-DBG]` dump,
   and confirm against the LilyPond table:
   - slope magnitude ≈ 0.197 (within the ~4% head-width run difference — do not
     chase the run);
   - bracket-line heights ≈ 6.34 (left) / 4.59 (right) after sign flip.
   The pre-514 estimate (0.215) was never run; **do not skip this step.**
4. **Verify script folding survived 514.** Confirm the conformant rewrite still
   folds accent/staccato into the offset sweep (the old path used
   `Neighbor.ACCENT` / `STACCATO`, and `Neighbor` is deleted by 514). Re-run the
   with/without-articulation comparison if in doubt: the bracket must move
   ~1.19 ss when the scripts are present, and the slope must be unchanged.
5. **Add a unit test** locking the staff-floor: a tuplet whose low endpoint sits
   in the staff yields a slope computed from the staff edge, not the raw tip.
6. `./scripts/compile.sh` → SUCCESS; unit tests green.

## Verification

- `[TUPLET-DBG]` numbers for `tuplet.mssw` match the LilyPond reference table
  (slope ≈ 0.197, heights ≈ 6.34 / 4.59) within the documented head-width run
  tolerance.
- Removing the articulations in both programs shifts the offset by the same
  ~1.19 ss and leaves the slope unchanged.
