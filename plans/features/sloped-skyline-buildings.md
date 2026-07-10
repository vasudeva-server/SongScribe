# Sloped Skyline Buildings — What Landed

**Branch:** `528-staffextents` (from `develop`) **Issue:** [#528](https://github.com/vasudeva-server/SongScribe/issues/528)
**Status:** Complete. Unit suite 5002 pass / 1 skipped (was 4991 / 1). Not committed.

---

## The bug

`StaffExtents` modelled every reservation as a flat rectangle and every element being placed as a
flat box. Both are false for curved and wedge-shaped glyphs. The visible symptom: **an accent on a
tied note was pushed 0.619 ss from the staff where LilyPond pushes it 0.286.** It collided with the
tie arc along the corners of a rectangle the `>` glyph only touches at one point.

LilyPond builds a `Script`'s skyline from the stencil outline
(`define-grobs.scm` → `grob::always-vertical-skylines-from-stencil`) and a notehead's from its
extents. Replacing LilyPond's accent stencil with a solid box of its identical bounding box
reproduced SongScribe's wrong answer exactly — which pinned the bounding box, not the tie and not
the padding, as the cause.

---

## What changed

**`StaffExtents`** — a reservation is now a `Building`: the linear segment `y(x)` from
`(xStart, yStart)` to `(xEnd, yEnd)`. A flat building is the old rectangle edge; a sloped one is a
chord of a curve. `ySet` still produces a flat building; `ySetSloped` produces a chord.

**Element profiles** — `Profile` describes an element's inner edge (the boundary nearest the staff)
as offsets from its own inner bounding edge. `Profile.flat(w)` is every element that isn't a wedge.
`ShapeProfile.innerEdge(Shape, above)` derives a real one from an outline: it flattens the path, then
emits one segment per run of the envelope governed by a single outline edge.

**`clearance`** — replaces the `yGet ∓ padding` arithmetic at the placement sites. It is LilyPond's
`Side_position_interface::aligned_side` distance query, evaluated exactly. LilyPond merges its
supports into one envelope first; we do not have to, because the max distributes over the supports:

```
max_x ( max_i support_i(x) − profile(x) )  ==  max_i ( max_x ( support_i(x) − profile(x) ) )
```

Each building is intersected with each profile segment; both are linear there, so the extreme lies at
a breakpoint. No sorting, no merging, no sampling.

**`Support(present, ySs)`** — replaces the `EMPTY_EXTENT_SS = 0.0` sentinel at the two places that
compared against it. `yGet` still returns `0.0` for an empty footprint, now documented as the
identity for the `min`/`max` its callers fold it into, not a sentinel.

**Tie arc reserves 16 chords** whose endpoints lie on the curve. `TIE_BOUND_MAX_SEGMENT_WIDTH_SS` and
its 0.1-ss dicing are deleted. A chord lies inside a convex arc, so this under-reserves by the
sagitta — ~0.002 ss at 16 chords, against the ~0.2 ss tip error the old midpoint-sampled flat steps
introduced.

**The accent gets its wedge profile**, derived once from `AccentShape.accent()`. Its outer edge is
still reserved flat, so the blast radius stays inside the accent's own placement.

---

## Results

| Quantity | Before | After | LilyPond |
| --- | ---: | ---: | ---: |
| accent push, tied (ss) | 0.6190 | **0.2816** | 0.2857 |
| accent top, untied (ss) | 2.700 | **2.700** | unmoved |
| wedge / box push ratio | 0.98 | **0.4549** | 0.4533 |
| layout, 24 notes / 12 ties / 48 articulations | 24.0 µs | **26.2 µs** | — |

The wedge costs 4.4 µs; the chords give back 2.2 µs by cutting each tie from ~33 reservations to 16.
Roughly the wash the plan predicted.

---

## Five things the original plan got wrong

**1. Horizon padding widens the support, not the profile.** The plan said to extend the profile's
x-range with flat caps. `skyline.cc` `internal_distance` pads `dim` — the *support* skyline — and
notes that padding the element too would merely double it. For a flat profile the two are identical,
which is why nobody noticed. For a sloped one the plan's version would have **broken its own success
criterion 2**, lifting the untied accent ~0.015 ss.

Confirmed by feeding LilyPond a right-triangle accent stencil with a maximally sloped inner edge: its
untied accent doesn't move either (−2.7455 vs the box's −2.7450), because the padded notehead reaches
the wedge's zero-offset cap.

**2. The accent is not a SMuFL glyph.** `Articulation` derives `ACCENT_WIDTH_SS`/`HEIGHT_SS` from
`AccentShape.accent().getBounds2D()` — **1.480 × 0.843**, not Bravura's `articAccentAbove`
1.356 × 0.976. The renderer fills that box exactly; there is no glyph/box mismatch.

**3. The wedge is neither a single straight arm nor monotone.** Its rounded cap makes the offset
*dip* from 0.0713 at x=0 to zero at x≈0.097 before climbing the arm at slope 0.2594. The plan said to
"assert the derived profile is monotone" — that would assert something false. And the dip is exactly
what makes the untied case work: padding the notehead to accent-local `[0.05, 1.43]` brackets the
zero, so the minimum offset under it is exactly 0.

**4. A tie's `x(t)` is not linear in `t`.** Control points are inset by `BezierBow.indent`, not by
`width/3`, so the old loop's pairing of uniform x-steps with uniform `t` was skewed — over-reserving
the arc's left half and under-reserving its right. Chords now evaluate the true `x(t)`.

**5. `VerticalStackingCalculator` and `SystemStacker` need no `Support` migration.** Their whole-line
`yGet` folds `0.0` into a `min`/`max` where it is the identity. Only the two `== EMPTY_EXTENT_SS`
comparisons were the actual problem.

---

## Performance needed real work

The first correct implementation ran **195 µs** against a 21.8 µs baseline: `clearance` walked every
profile segment against every building on the line. Fixes, in order of what they bought:

- **Reject distant buildings** against the profile's whole span before walking its segments
  (195 → 34.6 µs).
- **`ShapeProfile.FLATNESS_SS = 0.01`, not 0.001** (34.6 → 26.2 µs). 17 of the 18 segments at 0.001
  covered 10% of the glyph — slivers from flattening the curved cap and tip, while the arm that meets
  every real support is one straight path segment and stays exact at any tolerance. 5 segments;
  placements agree to 0.003 ss.
- Precomputing `slopeSs` on `Building`/`Segment` bought only 1.5 µs. Iteration count was the cost,
  not division.

Benchmark with warmup. Cold, the same configurations read 2–3× apart and appear to differ when they
do not.

---

## Verification

Every moved expectation was re-derived and then falsified:

- Reverting the accent to `Profile.flat` fails 4 tests, including both re-derived
  `ArticulationStackingTest` helpers, which now compute the wedge's offset at the support's padded
  near edge rather than hardcoding a number.
- Reverting chords to midpoint steps fails the new chord guard,
  `testArcIsReservedAsChordsThatLieInsideTheCurveNotStepsThatOvershootIt`.
- Phase 1 passed the entire suite unchanged (4991 / 1), proving the flat fast path is bit-identical.

New: `ShapeProfileTest` (7), `AccentWedgeClearanceTest` (3), the chord guard (1).

The accent-over-staccato case was checked against LilyPond too: it gains 0.1204 there, we gain
0.0997. The 0.021 ss residual is the staccato dot's round outline — deferred, and tracked in
[script-skyline-followups.md](script-skyline-followups.md) along with the `yGetExpanded` query-vs-support
padding inconsistency and the accent's flat outer reservation.

---

## Commit message

Land as two commits so a bisect separates the refactor from the behaviour change. The prerequisite
work already on the branch (tie span, script horizon padding, articulation centring) belongs with
the second.

```
refactor: model staff extents as sloped buildings

- Replace Reservation with Building: the linear segment y(x) over
  [xStart, xEnd]; ySet still produces a flat one, ySetSloped a chord
- Add Profile, an element's inner edge, and Support, a clearance result
- Add StaffExtents.clearance: LilyPond's aligned_side distance query,
  evaluated exactly at breakpoints, with no merged support envelope
- Replace the EMPTY_EXTENT_SS sentinel with Support.present; yGet's 0.0
  is the identity for its callers' min/max, not a sentinel
- Every caller passes a flat profile, so the suite passes unchanged

refs #528
```

```
fix: clear a tie arc by the accent's wedge, not its bounding box

An accent on a centre-attached tie was pushed 0.619 ss from the staff
where LilyPond pushes it 0.286. It collided along the corners of a
rectangle the '>' glyph only touches at one point. Replacing LilyPond's
accent stencil with a solid box of its identical bounding box
reproduces the old answer exactly.

- Reserve the tie arc as 16 chords with endpoints on the curve, and
  delete TIE_BOUND_MAX_SEGMENT_WIDTH_SS and its 0.1-ss dicing; evaluate
  the true x(t), which BezierBow.indent makes non-linear in t
- Derive the accent's inner edge from AccentShape via ShapeProfile and
  place it with StaffExtents.clearance
- Widen the support by horizon-padding, never the profile, as
  skyline.cc internal_distance does; this is what keeps an untied
  accent seated exactly where its bounding box put it
- Reserve the accent's outer edge flat, confining the change to its own
  placement

Tied push 0.619 -> 0.2816 ss (LilyPond 0.2857); wedge/box ratio 0.4549
vs LilyPond's 0.4533. Untied accent unmoved at 2.700 ss.

refs #528
```

Use `Closes #528` instead of `refs #528` on the second commit if the issue should close.

---

## Appendix — LilyPond probes

Render with `/opt/homebrew/bin/lilypond -dno-point-and-click -l ERROR <file>.ly`. The box-stencil
probe is the one that proves the diagnosis; the triangle probe is the one that proves the padding
side.

```lilypond
\version "2.24.0"
#(define (rep label)
   (lambda (g)
     (let ((ye (ly:grob-extent g (ly:grob-common-refpoint g (ly:grob-object g 'staff-symbol) Y) Y))
           (xe (ly:grob-extent g (ly:grob-common-refpoint g (ly:grob-object g 'staff-symbol) X) X)))
       (format #t "~a top=~,4f bot=~,4f x=[~,4f ~,4f]\n"
               label (cdr ye) (car ye) (car xe) (cdr xe)))))
#(define (nhrep g)
   (let ((ye (ly:grob-extent g (ly:grob-common-refpoint g (ly:grob-object g 'staff-symbol) Y) Y))
         (xe (ly:grob-extent g (ly:grob-common-refpoint g (ly:grob-object g 'staff-symbol) X) X)))
     (format #t "  NOTEHEAD y=[~,4f ~,4f] x=[~,4f ~,4f]\n" (car ye) (cdr ye) (car xe) (cdr xe))))

%% A solid box of the script's identical bounding box.
boxify = #(lambda (grob)
  (let* ((orig (ly:script-interface::print grob))
         (xe (ly:stencil-extent orig X)) (ye (ly:stencil-extent orig Y)))
    (ly:make-stencil (list 'round-filled-box (- (car xe)) (cdr xe) (- (car ye)) (cdr ye) 0.001) xe ye)))

%% Same bbox, flat bottom, top edge sloping from top-left to bottom-right: a maximally
%% sloped inner edge. If a slope could move the untied accent, this stencil would move it.
trify = #(lambda (grob)
  (let* ((orig (ly:script-interface::print grob))
         (xe (ly:stencil-extent orig X)) (ye (ly:stencil-extent orig Y))
         (x0 (car xe)) (x1 (cdr xe)) (y0 (car ye)) (y1 (cdr ye)))
    (ly:make-stencil `(polygon (,x0 ,y0 ,x1 ,y0 ,x0 ,y1) 0.001 #t) xe ye)))

\score { \new Staff { e'4-> e'4-. }
  \layout { \context { \Voice \override Script.after-line-breaking = #(rep "WEDGE-untied")
                              \override NoteHead.after-line-breaking = #nhrep } } }
\score { \new Staff { e'4-> ~ e'4-. }
  \layout { \context { \Voice \override Script.after-line-breaking = #(rep "WEDGE-tied  ") } } }
\score { \new Staff { e'4-> e'4-. }
  \layout { \context { \Voice \override Script.stencil = #boxify
                              \override Script.after-line-breaking = #(rep "BOX-untied  ") } } }
\score { \new Staff { e'4-> ~ e'4-. }
  \layout { \context { \Voice \override Script.stencil = #boxify
                              \override Script.after-line-breaking = #(rep "BOX-tied    ") } } }
\score { \new Staff { e'4-> e'4-. }
  \layout { \context { \Voice \override Script.stencil = #trify
                              \override Script.after-line-breaking = #(rep "TRI-untied  ") } } }
```

Output that settled the design:

```
  NOTEHEAD y=[-2.5450 -1.4550] x=[17.1208 18.4250]
WEDGE-untied top=-2.7450  x=[17.0229 18.5229]
WEDGE-tied   top=-3.0307
BOX-untied   top=-2.7450
BOX-tied     top=-3.3752
TRI-untied   top=-2.7455        <-- a sloped inner edge does not move it
```

The notehead padded by 0.1 spans `[17.0208, 18.5250]`, covering the accent's full `[17.0229, 18.5229]`
including its zero-offset cap. Hence `min d = 0`, and box and wedge agree untied.

**Two traps.** Anchor a guard test *below* the staff (`staffPosition = 4`): a within-staff script is
staff-padding-clamped and the clamp, not the tie, decides — such a test passes against broken code.
And `e'` is centre attach while `d'`, `f'`, `g'` are edge attach; only a centre-attached tie, whose
endpoint recedes to the notehead centre, lands on the scripts at all.

**`tieSeatSs` stays as it is.** SongScribe deliberately seats a space note's tie clear of an adjacent
staff line where LilyPond lets the arc sit on it. Intentional divergence, not a bug.
