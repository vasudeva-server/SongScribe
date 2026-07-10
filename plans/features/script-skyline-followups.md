# Script Skylines — Follow-ups to #528

**Branch:** to be cut from `develop` after #528 lands
**Depends on:** #528 (sloped `StaffExtents` buildings, `ShapeProfile`, `Profile`/`Support`/`clearance`)
**Status:** **Complete.** Phases 1 and 2 landed. Phases 3 and 4 closed — each shown to be a no-op or
out of scope, not deferred. Nothing remains.

---

## Scope — read this first

**This plan covers note-attached stacking only:** tiers 1–2, `NoteAttachedStacker` — articulations
(staccato, accent), fermata, trill. Those are LilyPond `Script` grobs and are modelled on LilyPond.

**Structural and system stacking are out of scope and must not be touched.** `StructuralStacker`
(tier 3: tuplets, hairpins, dynamics, endings), `SystemStacker` (tier 4: tempo, annotations) and
`VerticalStackingCalculator`'s own tiering are derived from **abc2svg, not LilyPond**. They work.
Do not migrate them toward LilyPond semantics on the strength of this plan.

This is not merely a preference — LilyPond agrees they are a different world. `TextScript`,
`VoltaBracket` and `ChordName` carry `outside-staff-interface`, and LilyPond places them in
`Axis_group_interface::add_outside_staff_grobs` (axis-group-interface.cc:735), which widens the
**element's** x-extent by `outside-staff-horizontal-padding` (`x_extent.widen()`, line 753) and
resolves collisions by unioning forbidden intervals. That is a different algorithm from
`Side_position_interface::aligned_side`, not a variant of it. Conforming those grobs to LilyPond
would mean porting `add_outside_staff_grobs`, not reusing `clearance`.

Note: **dynamics are placed by `StructuralStacker`** into `structuralExtents`, even though their
padding constants (`DYNAMIC_PADDING_SS`, `DYNAMIC_STAFF_PADDING_SS`) live in `NoteAttachedStacker`.
They already go through `clearance`, so nothing here touches them.

---

## Why this exists

#528 gave the accent its real outline and gave `StaffExtents` sloped buildings. It deliberately
stopped there. What it left behind is small, independently landable, and has a measurable LilyPond
ground truth. None of it is a regression — it all predates #528 — so this is accuracy work, not
repair.

The unifying fact, from `scm/define-grobs.scm`:

| Grob | `vertical-skylines` | Shape used for collisions |
| --- | --- | --- |
| `Script` (staccato, accent, fermata, trill) | `grob::always-vertical-skylines-from-stencil` (line 3009) | the **outline** |
| `NoteHead` | default from `Grob::Grob` (grob.cc:81) — `simple_vertical_skylines_from_extents` | the **box** |

Both verified from source. `NoteHead` has no `vertical-skylines` entry at all; it gets the box
because nothing overrode the default.

So every *script* should collide by its outline and every *notehead* by its box. #528 did this for
the accent's placement; Phase 1 did it for the staccato's reservation.

**A grob's outline matters to a neighbour only where the neighbour's own edge is not flat, or where
the neighbour fails to span the grob's extreme.** That single fact decided three of these four
phases: it is why the staccato's roundness pays (the accent's wedge queries it), and why the accent's
wedge does *not* need reserving (everything above it is flat and centred, so it reads the accent's
apex). What remains is one query-side inconsistency.

Ordered by value per unit of risk. Phases are independent; land them separately.

---

## Phase 1 — the staccato dot reserves its round outline — **DONE**

**The gap.** The dot is reserved as a flat box, so an accent stacking outside it clears a rectangle
the dot only touches at one point.

Measured (LilyPond 2.26, `\stemDown g''4->-.`, accent's inner edge, Y-up). `boxify` in Appendix A
replaces *every* `Script` stencil, the dot's included, so the four combinations must be probed
separately — the headline 0.1204 conflates the wedge with the dot:

| Accent stencil | Dot stencil | accent inner edge | gain over box/box |
| --- | --- | ---: | ---: |
| box | box | 3.8450 | — |
| box | round | 3.8450 | 0.0000 |
| wedge | box | 3.7757 | 0.0693 |
| wedge | round | 3.7246 | 0.1204 |

A box accent gains *nothing* from a round dot: with a flat inner edge the binding x is wherever the
support is tallest, and that is the dot's apex, which is at box height either way. The roundness is
only reachable once the element above has an outline of its own.

**Corrected ground truth.** The plan originally claimed SongScribe's post-#528 gain was 0.0997 and
that the 0.021 residual "is exactly the dot's roundness". Both are wrong. SongScribe's flat-dot gain
is **0.0973** (`ShapeProfile.innerEdge(accent).offsetSs(0.472)`), and the dot's roundness is worth
far more than 0.021. Derived from `aligned_side` + `Skyline::padded` rather than measured: with the
dot a circle of radius `r` and the accent's inner edge a straight arm of slope `m` through the
binding region, the binding x is where the dilated circle's own slope reaches `m`, and the accent
descends by

```
gain = r · (1 + m − √(1 + m²))
```

independent of the horizon padding and of the dot's position. For Bravura (`r = 0.168`,
`m = 0.2594`) that is **0.0380 ss**. Inverting it against LilyPond's own measured dot contribution
(0.0511 with Feta's `r = 0.200`) gives `m ≈ 0.30` — Feta's slightly steeper sforzato arm. The formula
reproduces both engines.

So SongScribe's total gain lands at **0.1353**, *past* LilyPond's 0.1204 rather than short of it.
That overshoot is correct: Bravura's dot is narrower (0.336 vs Feta's 0.400), so the binding x sits
further along the arm, where the wedge has receded more. Font metrics, as the plan predicted — it
just guessed the sign.

**This is reservation-side, not profile-side.** `Profile` describes the element being *placed*; the
dot's roundness matters because the dot is a *support*. The tool for that already exists:
`StaffExtents.ySetSloped`. Reserve the dot as a short run of chords along its outer outline instead
of one flat building — exactly what #528 did for the tie arc.

### Work

1. Get the dot's outline as a `Shape`. `articStaccatoAbove`/`articStaccatoBelow` are Bravura glyphs,
   not `Path2D`s like `AccentShape`. Precedent for the conversion:
   `Font.createGlyphVector(frc, …).getOutline()` — see `LyricConnectorRenderer:123`,
   `EndingRenderer:134`.

2. **Blocker, resolve first:** the Bravura `Font` is created in `ui/renderer/RenderingUtils.java:89`.
   `layout/` must not depend on `ui/renderer/`. Move the font's ownership down to `songscribe.smufl`
   (or `songscribe.font`) and have `RenderingUtils` read it from there. This is the only structural
   change in the plan; do it as its own commit.

3. Add `ShapeProfile.outerEdge(Shape, boolean above)` — the mirror of `innerEdge`, returning the
   boundary *away* from the staff. The envelope walk is identical; only the `governingEdge`
   comparison and the `offsetSs` datum flip. Consider deriving both from one parameterized walk
   rather than copying it.

4. In `NoteAttachedStacker`, reserve the dot as chords of that outline instead of
   `extents.ySet(…, reserveEdgeYSs)`. Note `stackAtCenter` and `placeAtInnerEdge` both reserve the
   dot; keep them in step.

### Watch out

- **Do not round the notehead.** LilyPond keeps it a box (table above). Rounding it would move every
  script on every note, and would fail #528's `testUntiedAccentRestsOnTheNoteheadRegardlessOfItsSlopingEdge`
  — correctly, because LilyPond's answer there depends on the notehead being a box.
- Reserving a *sloped* dot means `yGet`'s whole-line callers see a slightly shallower dot. **Checked:
  harmless.** All four `VerticalStackingCalculator` calls query the whole line (`0 → lineWidthSs`),
  and the dot's apex chord touches its box top exactly, so line sizing is unchanged.
- **The chord count was the whole cost, and 0.01 ss was too fine.** `FLATNESS_SS` gives the dot 8
  chords; each is a reservation the rest of the line is then scanned against, and `clearance` is a
  linear scan. Measured on the #528 line: flat dot 9.4 µs, 4 chords 10.8 µs, 8 chords 14.4 µs.
  Dropping to 4 recovers 72% of the added cost for 0.0056 ss of accuracy — 0.04 px. Phase 1 therefore
  introduced `NoteAttachedStacker.STACCATO_OUTLINE_FLATNESS_SS = 0.02` and
  `ShapeProfile.outerEdge(shape, above, flatnessSs)`. A *reserved* edge affords a looser tolerance
  than a *placed* one: chords lie inside a convex arc, so the error is one-sided (it can only
  under-reserve, letting a neighbour sit closer) and bounded by the tolerance. `innerEdge` keeps 0.01.

### Verification — as landed

- LilyPond numbers reproduced on 2.26 with `probe2.ly`, and decomposed with the four-way probe above.
- The gain is asserted as a **derived bracket**, not a recorded number:
  `closedForm < gain < closedForm + flatness`. The lower bound holds because chords under-reserve;
  the upper because the reservation departs from the true circle by at most its flattening tolerance.
- `ArticulationStackingTest.accentEdgeOffsetOverStaccatoSs` re-derived, not re-recorded: it now adds
  `roundDotGainSs` to the wedge offset. Both bindings lie on the same straight arm of the wedge.
- Failure proven both ways: reserving the dot flat fails 5 assertions in `AccentOverStaccatoTest`;
  dropping the `roundDotGainSs` term fails the two `ArticulationStackingTest` cases where the dot
  actually binds (the staff-clamp cases correctly do not move).
- Pipeline gain measured at **0.04358** above and below the staff, matching the isolated
  `StaffExtents` measurement exactly.

**Residual after this phase:** font metrics only, and SongScribe now sits *closer* than LilyPond
(0.1353 vs 0.1204) because Bravura's dot is 0.336 ss where Feta's is 0.400.

---

## Phase 2 — `yGetExpanded` widens the query; LilyPond widens the support — **DONE**

**The gap.** `skyline.cc` `internal_distance` pads `dim` — the *support* skyline — and explicitly
notes that padding the element too would merely double the padding. #528 established this in
`StaffExtents.clearance`. But `stackAtCenter`, `stackAtAnchor` and `stackAboveWithRegions` still go
through `StackingUtils.yGetExpanded`, which widens the **query range** instead:

```java
return extents.yGet(above, xSs - horizonPaddingSs, widthSs + 2 * horizonPaddingSs);
```

For a flat support the two are provably identical — dilating the query by `h` and dilating each
reservation by `h` select the same reservations and the same value. That is why this went unnoticed,
and why #528 could leave it alone. Against a **sloped** support they differ: a widened query reads
the chord's *interior* up to `h` beyond the element's footprint, where a padded support would hold
the chord's endpoint height flat. The difference is bounded by `arcSlope × h`.

**No regression is hiding here.** Before #528 the tie was reserved as flat steps diced at 0.1 ss, and
a widened query over 0.1-ss steps already returned the arc's extreme across the widened window —
the same thing a widened query over chords returns, to within the chord sagitta (~0.002 ss). The
divergence from LilyPond predates #528 and is unchanged by it. It is an inconsistency, not a bug.

### Affected callers — and why only one of them is in scope

`yGetExpanded` has exactly three call sites, all inside `StackingUtils`:

| Call site | Reached from | Element | Tier | Horizon | In scope? |
| --- | --- | --- | --- | ---: | --- |
| `stackAtCenter` (`StackingUtils:451`) | `stackStaccato` | within-staff staccato | 1 | 0.10 | **yes** |
| `stackAtAnchor` (`StackingUtils:433`) | `stackAbove` → `SystemStacker:147`, `StructuralStacker:255`, `VerticalStackingCalculator:221` | annotation, chord symbol, attribution | 4, 3, 5 | 0.75 | no |
| `stackAboveWithRegions` (`StackingUtils:537`) | `SystemStacker:164`, `StructuralStacker:216` | attachment, ending | 4, 3 | 0.75 | no |

**Everything note-attached except the within-staff staccato already uses `clearance`**: fermata and
dynamic via `placeAndReserveClamped`, trill via a direct `clearance` call. So Phase 2 is one
function.

**The original plan's premise for the structural callers was false.** It labelled their 0.75 a
"Horizon", implying LilyPond's `horizon-padding`. It is not. Two source facts:

1. Only four grobs in all of `define-grobs.scm` set `horizon-padding` — `BarNumber` (0.05),
   `CaesuraScript` (0.1), `Script` (0.1), `TrillPitchGroup` (0.1). `TextScript`, `VoltaBracket`,
   `ChordName`, `DynamicText` and `Hairpin` set none, so `aligned_side` reads the property default,
   `0.0`. And `internal_distance` short-circuits on it:
   `if (horizon_padding == 0.0) return internal_distance (other, touch_point);` — `padded()` never
   runs. Query-widening and support-dilation differ *only* through that padding, so at `h = 0` they
   are the same computation. **There is no LilyPond number to probe, and no direction of motion to
   pin.**
2. Those grobs are not placed by `aligned_side` alone anyway; see **Scope** above.

`STRUCTURAL_HORIZONTAL_MARGIN_SS = 0.75` is SongScribe's own (abc2svg-derived) collision margin. The
old estimate `0.36 × 0.75 ≈ 0.27 ss` quantified a divergence from an engine that lacks the parameter.

### How narrow this actually is

`stackAtCenter` is `private static` with **one** call site: inside `stackStaccato`, and only on the
*within-staff* branch. Once the note reaches the staff edge, `stackStaccato` goes through
`placeAndReserveClamped`, which is already on `clearance`. (This mirrors LilyPond, where staccato's
`quantize-position . #t` makes `include_staff` false in `aligned_side` — the dot genuinely has no
staff clamp, which is why SongScribe's centre branch has none either.)

Narrower still. Query-widening and support-dilation are provably identical against a **flat** support,
so the divergence needs a *sloped* one. The only sloped buildings ever written into the note-attached
layer are:

- the tie arc's chords (`NoteAttachedStacker:438`, `ySetSloped`), and
- the staccato dot's own outline (`ySetProfile`, added by Phase 1).

The dot is the innermost script, so nothing sits between it and the note but a tie arc. A neighbouring
column's dot would have to fall within the 0.10 ss horizon; adjacent notes are ~2 ss apart.

**Phase 2's entire observable effect is therefore: a within-staff staccato with a tie arc protruding
under it.** Every other case is bit-for-bit unchanged. Treat any other movement as a bug.

Note that `stackStaccatoOnly` is shared by the full pipeline (`NoteAttachedStacker:502`) and the
no-tie insertion preview (`:269`), deliberately, so the two agree. The preview has no tie, hence no
sloped support, so Phase 2 is a no-op there and the preview/full parity test stays green for free.

### Work

Migrate **`stackAtCenter` only** to
`StaffExtents.clearance(above, xSs, Profile.flat(widthSs), padding, horizonPaddingSs)`.
`yGetExpanded` **stays** — its other two call sites are out of scope. Do not delete it.

`clearance` returns a `Support`, so `stackAtCenter`'s `Math.min(idealBottomYSs, collisionBottomYSs)`
becomes an explicit "no support ⇒ ideal anchor alone" branch, removing the last place in the
note-attached tier where `yGet`'s `0.0` silently doubles as an identity element. Read #528's
`Support` javadoc first.

### Watch out

- **Query-widening is the more conservative of the two** (it sees more of the arc). Migrating lets
  the staccato sit *closer* to a tie, bounded by `arcSlope × 0.10 ≈ 0.036 ss`. Derive it; don't
  re-record.
- **Do NOT expect "small movements". Quantization has no small movements.** The dot's centre is
  snapped by `quantizeStaccatoCenterSs` onto *odd* staff positions, so its output moves in steps of
  1.0 ss and nothing finer. A ≈0.036 ss change in the support is therefore either absorbed entirely
  or amplified into a whole staff space. Measured against production code:

  | support Y | dot top Y | quantized centre |
  | ---: | ---: | ---: |
  | −0.7000 | −2.0000 | −1.5000 |
  | −0.7001 | −3.0000 | −2.5000 |

  0.0002 ss of support change → 1.0 ss of dot movement. Phase 2 will thus produce *no* visible change
  in almost every case, and a full-staff-space jump in the few where the raw centre straddles a snap
  boundary. Any golden-image or pixel diff will look like a bug and will not be one. Budget for
  triaging that, and note the raw (pre-quantize) centre is the quantity to assert on.
- Outside the quantize zone (`|centre| > STACCATO_QUANTIZE_ZONE_SS`, 2.5 ss) no snapping occurs and
  the 0.036 ss shows through directly. A within-staff staccato pushed hard enough by a tie can leave
  the zone.
- **No regression is hiding here.** Before #528 the tie was reserved as flat steps diced at 0.1 ss,
  and a widened query over those steps already returned the arc's extreme across the widened window —
  what a widened query over chords returns, to within the chord sagitta (~0.002 ss). The divergence
  predates #528 and is unchanged by it. It is an inconsistency, not a bug.
- The staccato only meets a *sloped* support when a tie arc protrudes under it. Against the notehead
  box the two semantics are provably identical, so most cases will not move at all. Pick the test
  case deliberately: **`e'` is centre attach** (Appendix A, trap 2) — an edge-attached tie never
  touches the scripts.

### Verification — as landed

LilyPond 2.26, `\stemDown c''4-. ~ c''4` (a space note, so the dot's anchor is 1.0 ss and the tie can
reach it), dot centre in staff spaces, Y-up:

| case | dot centre |
| --- | ---: |
| no tie, round dot | 1.5000 |
| tie, round dot | 2.5000 |
| tie, **boxified** dot | 2.5000 |
| `b'` (line note), tie or not | 1.5000 |

Three things follow, all of them load-bearing:

1. **The tie really is a support for the staccato.** It pushes the dot a full staff space, 1.5 → 2.5.
2. **The push is exactly 1.0 ss** — the quantize step — not some fraction. Confirms the discontinuity
   below.
3. **Boxifying the dot changes nothing.** See the closed item below.

A line note (`b'`) is not pushed at all: its on-line anchor of 1.5 ss already clears the arc. Do not
write the test there.

Because flat supports are provably identical under both semantics, and the one real case quantizes to
the same space, **no existing test moved**. The migration is therefore guarded by three new tests in
`StackingUtilsTest` built on a synthetic chord shaped like a tie's endpoint — deliberately steeper
than a real arc (slope 1.05 rather than ~0.36) so the divergence, 0.0526 ss, is unmistakable:

- `testStaccatoOverASlopedSupportSeesTheChordsEndpointNotItsInterior`
- `testWideningTheQueryWouldSeatTheStaccatoFurtherOut`
- `testStaccatoWithNoSupportSitsAtItsIdealAnchor` (the `Support.present()` branch)

The first two were proven to fail against the old `yGetExpanded` call, by exactly the derived
0.0526 ss. They place the dot beyond the quantize zone on purpose, and assert that they have, so the
raw difference survives into the placement instead of being snapped away.

### Closed — the dot's *inner* edge is round, and it does not matter

Phase 1 gave the dot its round **outer** edge (what the accent clears). Its **inner** edge — the
boundary facing the staff, which decides where the dot itself sits — is still `Profile.flat`, and
LilyPond does place the dot by its real skyline (`my_dim` in `aligned_side` comes from the stencil).
This looked worth doing, and it is not.

Against a flat support it cannot matter: the dot's inner offset is zero at its lowest point, so the
binding is a box's. Over a tie arc it would matter — except that `quantize-position` then rounds the
centre onto a space, and the effect is far smaller than the 1.0 ss grid. **LilyPond agrees, measured:
boxifying the dot's stencil over a tie moves it not at all (2.5000 either way).** The engine cannot
see its own dot's roundness here. Neither should we. Do not implement it.

---

## Phase 3 — does a script reserve its sloped *outer* edge? — **CLOSED: no. It buys nothing.**

Keep the two edges straight, because the whole question turns on it:

| | edge used | who reads it | status |
| --- | --- | --- | --- |
| the accent's **query** (`profile`) | its wedge — `innerEdge` | `clearance`, to seat the accent on a tie or notehead | #528. **Correct and working.** |
| the accent's **reservation** (`reserveProfile`) | its box — `Profile.flat` | whatever stacks above the accent | stays a box |

The accent must query with its wedge — that is what seats it correctly over a tie arc, and it is
where the wedge's slope genuinely earns its keep. Phase 3 proposed changing the *reservation* to the
wedge as well, so that elements above could nestle under the arm.

**Measured: they cannot, because they have nothing to nestle into.** The accent's outer edge above
the staff has its apex at its **left** end (offset 0 at x ≈ 0.097, receding 0.4192 ss by the tip).
Every element that stacks above an accent has a flat inner edge and is centred on the note column, so
its footprint covers that apex — and a flat inner edge spanning a support binds at the support's
extreme. Reserving the wedge changes the answer by exactly zero:

| element above the accent | box | wedge | gain |
| --- | ---: | ---: | ---: |
| dynamic, centred, width 1.5, horizon 0.75 | −4.60000 | −4.60000 | **0.00000** |
| hairpin spanning the accent, horizon 0.75 | −4.60000 | −4.60000 | **0.00000** |
| tuplet bracket, wide, horizon 0.75 | −4.60000 | −4.60000 | **0.00000** |

This is the same theorem Phase 1 turned up from the other side: a **box** accent gains nothing from a
**round** dot, for exactly this reason. A flat edge sees only the support's extreme.

**The one case where it is not zero**, recorded so nobody re-derives it as a reason to reopen: a
*narrow* element parked over the accent's right tip gains 0.312 ss — and it gains that at horizon
0.75 just as at horizon 0.00. Dilation in `Skyline::padded` (and in `clearance`) is **per building**,
extending `h` beyond each building's own span; the apex building ends at x ≈ 0.097, so its dilated
reach stops at 0.85, short of the tip at 1.48. A wider horizon does *not* drag the apex rightward.
The invariance above therefore rests on the elements being centred, not on the horizon being wide.
No such narrow, tip-parked element exists in SongScribe.

The slope only matters against a neighbour that shares the accent's horizontal band and is itself
curved — a tie or a slur. Those are *supports* for the accent, not things that stack above it, and
the accent already meets them correctly through its wedge **query**. Nothing above an accent needs to
see anything but a box.

**Consequence for `reserveProfile`:** it stays in the signature (the staccato needs it), and the
accent keeps passing `Profile.flat(widthSs)`. That is not a placeholder awaiting Phase 3; it is the
right answer.

---

## Phase 4 — LilyPond's 45° padding taper — **out of scope, closed**

**Confirmed from source during Phase 1.** `Skyline::padded` (skyline.cc:557) pads *every* building —
flat by `h` on each side, then a slope-1 ramp for a further `h` — and merges the result by max. Two
consequences worth carrying forward:

1. Padding every building means the padded support is the **`h`-dilation** of the support polyline,
   not merely its two outer ends extended. `StaffExtents.clearance` already reproduces this exactly:
   `Building.heightSs` clamps to its endpoint beyond its own ends, and the query takes the extreme
   over all buildings. This is why a chorded dot behaves correctly with no change to `clearance`.
2. `internal_distance` pads `this`, and `aligned_side` calls it as `dim.distance(my_dim, …)` —
   `dim` is the **support**. Phase 2's premise is confirmed verbatim.

`StaffExtents.clearance` implements only the flat extension; the taper is
documented as omitted because nothing places an element in its reach.

**That claim holds for scripts (horizon 0.10), and the phase is now closed.** The original plan asked
to re-check it at the 0.75-ss structural horizon, where the taper's reach would be 1.5 ss. But the
0.75 is SongScribe's own abc2svg margin, not a LilyPond `horizon-padding` (see Phase 2), and the
structural tier is out of scope. There is nothing left to check.

For the record, omitting the taper is *safe*, not merely unnoticed: the ramp only ever lowers the
support beyond the flat extension, so SongScribe's support is greater than or equal to LilyPond's
everywhere. It can only place an element further out, never closer. Phase 1's binding lies inside the
dot's own footprint, well short of the ramp.

---

## Non-goals

- **The notehead stays a box.** Not an oversight — LilyPond's choice, and #528's untied-accent
  guarantee depends on it.
- **The accent keeps reserving a box.** Measured, not assumed: every element that stacks above an
  accent is flat-bottomed and centred on the note column, so it binds at the accent's apex and reads
  box height whether the accent reserves its wedge or its box. Gain is exactly 0.00000. See Phase 3.
- **Structural and system stacking stay on abc2svg.** See Scope. LilyPond places those grobs with
  `add_outside_staff_grobs`, a different algorithm from `aligned_side` — not a variant of it.
- **`tieSeatSs` stays as it is.** SongScribe seats a space note's tie clear of an adjacent staff
  line where LilyPond lets the arc sit on it. Intentional divergence.
- **Chord count for ties stays at 16.** Sagitta ~0.002 ss; measured cost is 2.2 µs/line *less* than
  the 0.1-ss dicing it replaced.

---

## Budget

#528 measured, on a 24-note / 12-tie / 48-articulation line (5 000 warmup, 7 × 3 000, median):

| Configuration | µs/layout |
| --- | ---: |
| pre-#528 (flat accent, 0.1-ss diced steps) | 24.0 |
| post-#528 (wedge profile, 16 chords) | 26.2 |

Phase 1 as landed adds 4 chords per staccato. Measured on the same line (5 000 warmup, 7 × 3 000,
median), isolating the extents work: flat dot 9.4 µs, **4 chords 10.8 µs**, 8 chords 14.4 µs. The
"< 1 µs" the plan originally predicted was optimistic — at `FLATNESS_SS`'s 8 chords it would have
cost 5 µs, because `clearance` is a linear scan and every chord is another reservation the whole line
is scanned against. Hence `STACCATO_OUTLINE_FLATNESS_SS`.

Phase 2 should be neutral or slightly *cheaper* (`clearance`'s early building rejection is tighter
than `yGet`'s). **Benchmark with warmup**: cold, the same configurations read 2–3× apart and appear
to differ when they do not. Even warm, run-to-run spread on the *absolute* numbers is ±1 µs; only
same-process A/B comparisons are trustworthy.

If cost ever does bite: `clearance` and `yGet` are full linear scans with no spatial index, so a
line is `O(elements × reservations)`. Sort each layer by `xStartSs` once and binary-search the
overlap window before compromising on any of the geometry above.

---

## Appendix A — probes

Render with `/opt/homebrew/bin/lilypond -dno-point-and-click -l ERROR <file>.ly`.
`boxify` replaces a script's stencil with a solid box of its identical bounding box; the difference
between the two runs *is* the effect being chased.

```lilypond
\version "2.24.0"
#(define (rep label)
   (lambda (g)
     (let ((ye (ly:grob-extent g (ly:grob-common-refpoint g (ly:grob-object g 'staff-symbol) Y) Y))
           (xe (ly:grob-extent g (ly:grob-common-refpoint g (ly:grob-object g 'staff-symbol) X) X)))
       (format #t "~a top=~,4f bot=~,4f x=[~,4f ~,4f] w=~,4f\n"
               label (cdr ye) (car ye) (car xe) (cdr xe) (- (cdr xe) (car xe))))))

boxify = #(lambda (grob)
  (let* ((orig (ly:script-interface::print grob))
         (xe (ly:stencil-extent orig X)) (ye (ly:stencil-extent orig Y)))
    (ly:make-stencil (list 'round-filled-box (- (car xe)) (cdr xe) (- (car ye)) (cdr ye) 0.001) xe ye)))

%% Phase 1: accent stacking on a staccato, wedge vs box.
\score { \new Staff { \stemDown g''4->-. }
  \layout { \context { \Voice \override Script.after-line-breaking = #(rep "WEDGE") } } }
\score { \new Staff { \stemDown g''4->-. }
  \layout { \context { \Voice \override Script.stencil = #boxify
                              \override Script.after-line-breaking = #(rep "BOX  ") } } }
```

Two traps that cost time on #528, both still live:

1. **Anchor below the staff** (`staffPosition = 4`, or `g''` above). A within-staff script is
   staff-padding-clamped, and the clamp — not the support — decides. A guard test written there
   passes against broken code.

2. **`e'` is centre attach; `d'`, `f'`, `g'` are edge attach.** A tie whose seat stays within the
   head box attaches at the notehead's facing edge plus `NOTE_HEAD_GAP_SS` and never touches the
   scripts. Only a centre-attached tie pushes them. Verified in both engines.

---

## Appendix B — reference metrics

| Quantity | Value (ss) | Source |
| --- | ---: | --- |
| accent wedge | 1.480 × 0.843 | `AccentShape.accent().getBounds2D()` — **not** SMuFL |
| accent arm slope | 0.2594 | exact; one straight path segment, flatness-independent |
| accent offset at tip | 0.4217 | = half its height |
| `articStaccatoAbove` width | 0.336 | `bravura_metadata.json` |
| `noteheadBlack` width | 1.180 | `bravura_metadata.json` |
| `NOTE_HEAD_GAP_SS` | 0.200 | `LayoutEngine:104` |
| `SCRIPT_HORIZON_PADDING_SS` | 0.100 | `StackingUtils` (LilyPond `Script.horizon-padding`) |
| `SCRIPT_STAFF_PADDING_SS` | 0.250 | `NoteAttachedStacker` (LilyPond `Script.staff-padding`) |
| `ACCENT_PADDING_SS` | 0.200 | `NoteAttachedStacker` |
| `STRUCTURAL_HORIZONTAL_MARGIN_SS` | 0.750 | `StackingUtils` — SongScribe's own (abc2svg); **not** a LilyPond horizon-padding |
| LilyPond `horizon-padding`, all other grobs | 0.000 | property default; `internal_distance` then skips `padded()` |
| `TextScript.outside-staff-horizontal-padding` | 0.200 | the real LilyPond analogue, used by a *different* algorithm |
| `ShapeProfile.FLATNESS_SS` | 0.010 | placement only; 5 profile segments; 0.001 gives 18 and costs 12 µs/line |
| `STACCATO_OUTLINE_FLATNESS_SS` | 0.020 | reservation only; 4 chords. 0.01 gives 8 and costs 3.6 µs/line more |
| staccato dot radius (Bravura) | 0.168 | half of the 0.336 bbox width; round to 0.0006 ss |
| accent-over-staccato gain | 0.0380 | `r(1 + m − √(1+m²))`, true circle; 0.0436 as 4 chords |
| LilyPond `Script.padding` (accent, staccato) | 0.200 | `scm/script.scm` — identical for both |
