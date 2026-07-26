# Tie Rendering and Placement Algorithm (largely ported from LilyPond)

This document captures the tie-placement algorithm **as implemented in SongScribe**, including
the deliberate deviations from LilyPond. It is the source of truth for
`LayoutEngine.calculateTies` and its helpers (`tieSeatSs`, `tieEndpointXSs`,
`tieLineAvoidedHeightSs`, `tieSeatRowHasDot`) plus `BezierBow`.

It is *not* a description of LilyPond's algorithm. Where the two differ, the difference is called
out. For LilyPond's own algorithm see `lily/tie-formatting-problem.cc` and the notes that preceded
this work.

---

## 1. Scope and provenance

The algorithm handles a **single tie joining two same-pitch notes** (note-head to note-head, one
staff line/space). It does **not** model:

- chords with multiple simultaneous ties (LilyPond's `Tie_column`),
- ties broken across system breaks,
- collisions with other ties, accidentals, or stems of a chord outline.

LilyPond selects a tie configuration by **penalty-minimising search** — it generates candidate
`(position, direction, Δy)` configurations, scores each against aptitude and collision penalties,
and keeps the cheapest (`generate_optimal_configuration`, 1-opt local search). SongScribe does
**not** search. It computes one placement **deterministically**, reconstructing the *geometry* of
the configuration LilyPond's search converges on for the single-tie case, and **overriding it**
where LilyPond's search has an edge-case bug at the staff boundary (§9).

Because SongScribe's note spacing and note-head metrics differ from LilyPond's, the absolute
coordinates differ; what is reproduced is the *structure* — which space the tie sits in, whether it
attaches at the note-head edge or center, and that it clears the staff lines.

---

## 2. Coordinate conventions

All quantities are in **staff spaces (ss)** unless noted.

- **Y increases downward** (Y-down): smaller Y is higher on the page.
- **Staff position (sp)** is in half staff-spaces, `spToSs(sp) = sp × 0.5`. sp **increases
  downward** too: `0` is the middle line, positive sp is lower (e.g. E4 = +4, D4 = +5), negative sp
  is higher (e.g. D5 = −2, F5 = −4).
- **Staff lines** are the even sp positions within the staff: sp ∈ {−4, −2, 0, +2, +4}, i.e. even
  and `|spToSs(sp)| ≤ STAFF_HALF_SS` (`STAFF_HALF_SS = 2.0`). Even sp outside that range is a ledger
  row, **not** a real staff line.
- **`arcSign`** is the arc direction: `+1` = the arc bulges downward (tie below the notes), `−1` =
  upward (tie above). The tie's endpoints are seated on the `arcSign` side of the note.
- A note's nominal **head box** spans `noteCenterY ± STAFF_POSITION_OFFSET_SS`
  (`STAFF_POSITION_OFFSET_SS = 0.5`) vertically — a half staff-space above and below the note
  center — and the note-head glyph width horizontally.

---

## 3. High-level structure

For each tie, `calculateTies` computes, in order:

1. **`arcSign`** — the arc direction (§4).
2. **Vertical seat** `seatSs` = distance from the note center to the shared endpoint Y, in the
   `arcSign` direction (§5). Both endpoints share one Y because the notes are the same pitch:
   `endpointYSs = spToSs(notePositionSp) + arcSign × seatSs`.
3. **Horizontal attachment** — X of each endpoint, derived *from the seat* via the edge/center step
   (§6). X depends on Y, so it is computed after Y.
4. **Arc shape** — the `slur_shape` Bézier width/height, with the height adjusted so the arc body
   clears the nearest staff line (§7).
5. **Lens outline** — the filled two-curve tie shape and its control points (§8).

---

## 4. Arc direction (`Tie.arcSign`)

Direction is chosen by LilyPond's both-stem fall-through tree (`Tie::get_default_dir`), unchanged:

- both stems up → arc **down** (`+1`); both stems down → arc **up** (`−1`);
- stems conflict → arc **up**;
- one stem only → **opposite** that stem;
- no stems → `sign` of the note's staff position (on the middle line → the neutral default, up);
- missing note → neutral default (up).

`arcSign() > 0` ⇔ arc below ⇔ `isAbove() == false`.

---

## 5. Vertical seat (`tieSeatSs`) — the core

The governing principle:

> **Seat the tie in the staff space immediately adjacent to the note in the arc direction, placed
> so both the endpoints and the apex clear the staff lines bounding that space.**

`tieSeatSs(notePositionSp, arcSign, dotRowCoincides)` returns the seat magnitude:

```
if dotRowCoincides:                         # augmentation-dot coincidence (§5.3)
    return STAFF_POSITION_OFFSET_SS + TIE_DOT_ROW_NUDGE_SS      # 0.5 + 0.25 = 0.75

if notePositionSp is a line position (even):                    # note ON a line (§5.1)
    if notePositionSp is an OUTER staff line (top or bottom, sp ±4):
        return TIE_OUTER_STAFF_LINE_SEAT_SS                      # 0.75 (centerd outside the staff)
    return STAFF_LINE_TIE_CLEARANCE_GAP_SS                       # 0.225 (inner line)

# note in a SPACE (§5.2)
edgeRowSp = notePositionSp + arcSign
if edgeRowSp is a real staff line:                              # edge-seat would land on a line
    return STAFF_POSITION_OFFSET_SS + STAFF_LINE_TIE_CLEARANCE_GAP_SS   # 0.5 + 0.225 = 0.725
return STAFF_POSITION_OFFSET_SS                                 # 0.5 (head-box edge)
```

### 5.1 Note on a line

Two cases, split by whether the note sits on an **outer** staff line (the top or bottom line, sp
±4) or an **inner** one:

- **Inner line.** The endpoints seat `STAFF_LINE_TIE_CLEARANCE_GAP_SS` (0.225) into the adjacent
  space, clearing the note's **own** line. The **far** line bounding that space is cleared
  separately by the arc-height adjustment (§7). Seat = 0.225 < 0.5, so the endpoints remain within
  the head box → **edge attach** (§6).
- **Outer line.** The endpoints seat `TIE_OUTER_STAFF_LINE_SEAT_SS` (0.75) — a full 1.5
  staff-positions out, into the open space beyond the staff — reproducing LilyPond's measured
  placement for the top/bottom line (F5, E4). Seat = 0.75 > 0.5, so the endpoints drop below the
  head box → **center attach** (§6), the tie sitting centerd outside the note-head rather than
  tucked into the adjacent space (§9).

### 5.2 Note in a space

The natural seat is the **head-box edge**, `STAFF_POSITION_OFFSET_SS` (0.5) out — one half-space,
which for a space note lands exactly on the next row (`edgeRowSp = notePositionSp + arcSign`, always
an even position).

- If `edgeRowSp` is a **real staff line**, the endpoints would land on it, so the tie is pushed a
  further 0.225 out (seat = 0.725). Seat > 0.5 drops the endpoints below the head box →
  **center attach** (§6).
- Otherwise (the edge row is off the staff, e.g. below the bottom line) the tie keeps the edge seat
  (0.5) → **edge attach**.

### 5.3 Augmentation-dot coincidence (`tieSeatRowHasDot`)

A dotted note **on a line** has its dot displaced one half-space toward the top
(`NoteGeometry.DOT_ON_LINE_Y_SHIFT_SS`). When the tie arcs into that same row (`dotRowSp ==
notePositionSp + arcSign`), the tie seats at the head-box edge and lifts a further
`TIE_DOT_ROW_NUDGE_SS` (0.25) so it attaches above the dot (LilyPond adds `dir·0.25` and disables
y-tuning). Seat = 0.75 → center attach. The dot itself never moves.

---

## 6. Horizontal attachment (`tieEndpointXSs`) — the edge/center step

LilyPond's `get_attachment` samples the chord's **skyline outline at the endpoint's seat height**,
then `note-head-gap` pulls the endpoint toward the span center (`attachment_x_.widen(-gap)`). That
sample is a **step function of the vertical seat**:

- while the seat is **within** the head box (seat ≤ 0.5), the outline is the note-head's **facing
  (span-side) edge**;
- once the seat has **dropped below** the head box (seat > 0.5), the outline has receded to the
  note-head **center** (LilyPond's boundary box, `linear_combination(-dir/2)`).

`centerAttach = seatSs > STAFF_POSITION_OFFSET_SS`. Then, for a note whose left edge (column X) is
`noteLeftXSs`, width `W = NOTE_HEAD_WIDTH_SS`, and endpoint direction `dir` (`+1` = left endpoint,
tie extends rightward; `−1` = right endpoint):

```
centerX     = noteLeftXSs + W/2
facingEdgeX = centerX + dir·W/2               # right edge for the left note, left edge for the right note
skylineX    = centerAttach ? centerX : facingEdgeX
endpointX   = skylineX + dir·NOTE_HEAD_GAP_SS  # gap toward the span center
```

Consequences:

- **Edge attach** left endpoint = `noteLeftX + W + gap` — 0.2 ss into the gap, clear of the note.
- **Center attach** left endpoint = `noteLeftX + W/2 + gap` — under the note-head, near its center.

A half-space change in the vertical seat therefore swings the horizontal attachment by roughly half
a note-head width. This is why a fixed horizontal inset cannot reproduce LilyPond, and it is the
reason a tie visibly moves from "inset in the gap" to "centerd on the note-heads" as the pitch
changes.

---

## 7. Arc shape and staff-line-avoided height

### 7.1 `slur_shape` (`BezierBow`)

The bow is LilyPond's single-curve `slur_shape`: a cubic Bézier with control points
`P1 = (indent, height)`, `P2 = (width − indent, height)`.

- **width** = `endXSs − startXSs`.
- **height** = `BezierBow.height(width, TIE_RATIO, TIE_HEIGHT_LIMIT_SS)`
  = `f01(width·ratio / hLimit)·hLimit`, where `f01(x) = (2/π)·atan(πx/2)` maps `[0, ∞) → [0, 1)`.
  Height grows with width and saturates below `TIE_HEIGHT_LIMIT_SS`.
- **indent** = `BezierBow.indent(width, TIE_HEIGHT_LIMIT_SS, TIE_SLUR_MAX_FRACTION)`
  = `2·hLimit − q²·maxFraction/(width + q)`, with `q = 2·hLimit/maxFraction`.

A symmetric cubic reaches only `TIE_APEX_CONTROL_REACH` (0.75) of the control-point height at its
apex (t = 0.5).

### 7.2 Staff-line-avoided height (`tieLineAvoidedHeightSs`)

The endpoints are fixed by the seat; only the **height** is adjusted so the arc body clears the
nearest staff line to its apex:

1. Find the staff line nearest the natural apex centerline. If none is a real (in-staff) line
   (`|nearestLineY| > STAFF_HALF_SS`), keep the natural height.
2. If the apex sits **below** the line (arc fits under it): keep the natural height unless the arc's
   outer edge (including the render stroke) would come within `TIE_OUTER_EDGE_LINE_CLEARANCE_SS`
   (0.125) of the line, in which case **flatten** just enough to restore that gap. If even a flat
   arc cannot clear below, fall through and lift it over.
3. If the apex **pokes through** the line: **heighten** the arc up and over it — never squash it
   back under — so the stroked arc stands a fixed `TIE_HEIGHTENED_INK_HEIGHT_SS` (0.88) tall,
   independent of tie width.

This matches LilyPond's "arc stays on the side its apex already favours."

---

## 8. Lens outline and control points

The rendered tie is a filled **lens**: an outer cubic Bézier (start → end) and a reversed inner
cubic (end → start) sharing the two endpoints, so the ends taper naturally
(`BezierBow.lens`). From the shoulder (apex line) `shoulderYSs = endpointYSs + arcSign·heightSs`:

```
cp1XSs   = startXSs + indentSs
cp2XSs   = endXSs   − indentSs
outerCpY = shoulderYSs + arcSign·TIE_MID_THICKNESS_SS
innerCpY = shoulderYSs − arcSign·TIE_MID_THICKNESS_SS
```

`TIE_MID_THICKNESS_SS` is solved so the rendered apex thickness — the filled lens (each side
reaching `TIE_APEX_CONTROL_REACH` of the offset) plus the `TIE_OUTLINE_THICKNESS_SS` round-pen
stroke — equals LilyPond's measured `TIE_APEX_THICKNESS_RATIO` (5/3) × staff-line thickness.
`TieRenderer` fills the lens and strokes its outline with a round pen of `TIE_OUTLINE_THICKNESS_SS`
to blunt the cusps where the two curves meet.

---

## 9. Staff-boundary handling vs LilyPond

At the outermost staff line SongScribe **reproduces** LilyPond for the on-line note and **overrides**
it for the adjacent space note.

**Outer line (E4, F5 — reproduced).** LilyPond seats a tie whose note sits on the top or bottom line
a full 1.5 staff-positions out, so the endpoints land in the open space beyond the staff and attach
centerd outside the note-head (measured: ±1.5 sp = 0.75 ss). SongScribe matches this with the
outer-line seat (§5.1): `TIE_OUTER_STAFF_LINE_SEAT_SS` = 0.75, seat > 0.5 → center attach. (Earlier
revisions treated LilyPond's centering here as a bug and forced edge attach; the measured ground
truth showed it is correct, and the code was brought into line.)

**Space just inside the outer line (F4 — overridden).** LilyPond's fall-back line-clearance test
uses `staff_span.widen(-1)`, which **excludes** the outermost line from the "within staff" region,
so it leaves the endpoints of a tie in the space just above the bottom line **on** that line — a
genuine edge-case bug. SongScribe implements the **intent** (clear the line): the edge row is the
bottom line (a real staff line), so the space-note push (§5.2) lifts the seat to 0.725, dropping the
endpoints 0.225 ss clear of the line at center attach.

| note | position | SongScribe seat | attach | vs LilyPond |
|----|----|----|----|----|
| E4 | bottom line (sp +4) | 0.75 (outer-line seat) | center | **reproduces** — endpoints centerd outside the staff |
| F4 | space above bottom line (sp +3) | 0.725 (pushed, edge row is the bottom line) | center | **deviates** — LilyPond leaves it on the line |

The in-staff cases (notes on inner lines or in inner spaces) are also computed from the seat rule
above rather than LilyPond's `center_tie_vertically`; the fixed 0.225 clearance reproduces
LilyPond's placement closely while guaranteeing the endpoints clear the note's own line, which the
prior fixed-seat model did not (it seated in-staff ties a full space too deep, dropping the
endpoints onto the far line).

---

## 10. Constants

All in staff spaces unless noted. Defined in `LayoutEngine` unless another class is named.

| Constant | Value | Meaning |
|----|----|----|
| `Staff.STAFF_POSITION_OFFSET_SS` | 0.5 | half a staff space; head-box half-height and the edge seat |
| `Staff.STAFF_HALF_SS` | 2.0 | half the staff height; outermost staff line offset |
| `NOTE_HEAD_GAP_SS` | 0.2 | gap pulling each endpoint toward the span center (LilyPond note-head-gap) |
| `STAFF_LINE_TIE_CLEARANCE_GAP_SS` | 0.225 | inner-line seat, and the outward push for a space note whose edge row is a line |
| `TIE_OUTER_STAFF_LINE_SEAT_SS` | 0.75 | seat for a note on an outer (top/bottom) staff line — endpoints land outside the staff, centerd (LilyPond's measured 1.5-sp placement) |
| `TIE_DOT_ROW_NUDGE_SS` | 0.25 | extra lift when the seat row coincides with an augmentation dot |
| `SMuFLConstants.NOTE_HEAD_WIDTH_SS` | 1.18 (Bravura `noteheadBlack` bBox width) | note-head glyph width |
| `TIE_HEIGHT_LIMIT_SS` | 1.1 | asymptotic max arc height (`h_inf`) |
| `TIE_RATIO` | 0.333 | height-vs-width growth ratio (`r_0`) |
| `TIE_SLUR_MAX_FRACTION` | 1/3.1 | control-point indent factor (`max_fraction`) |
| `TIE_APEX_CONTROL_REACH` | 0.75 | fraction of control height a symmetric cubic reaches at t = 0.5 |
| `TIE_MID_THICKNESS_SS` | 7/90 ≈ 0.07778 | lens midpoint half-thickness = `(5/3·0.1 − 0.05)/(2·0.75)`, solved from the apex-thickness target |
| `TIE_OUTLINE_THICKNESS_SS` | 0.05 | round-pen outline width around the filled lens |
| `TIE_OUTER_EDGE_LINE_CLEARANCE_SS` | 0.125 | flatten trigger: outer edge must stay this far below a line |
| `TIE_HEIGHTENED_INK_HEIGHT_SS` | 0.88 | fixed stroked-arc height when heightened over a line |
| `TIE_LINE_THICKNESS_SS` | 0.1 | staff-line thickness (LilyPond default) |
| `TIE_APEX_THICKNESS_RATIO` | 5/3 | apex thickness as a multiple of staff-line thickness |

---

## 11. Worked sweep: D4–B4

The `ties.ly` diagnostic: each note tied to itself, walking a step at a time from below the staff up
through it. SongScribe coordinates (sp downward). Note center Y = `sp × 0.5`; endpoint Y =
`noteCenterY + arcSign × seat`.

| note | sp | line/space | arc | seat | attach | endpoint Y | sits in / clears |
|----|----|----|----|----|----|----|----|
| D4 | +5 | space (below staff) | down (+1) | 0.5 (edge) | edge | +3.0 | open space below the staff |
| E4 | +4 | bottom line | down (+1) | 0.75 (outer line) | center | +2.75 | 0.75 below the bottom line, in the open space |
| F4 | +3 | space | down (+1) | 0.725 (pushed) | center | +2.225 | 0.225 below the bottom line |
| G4 | +2 | inner line | down (+1) | 0.225 (on-line) | edge | +1.225 | sp +3 space, clears the bottom line |
| A4 | +1 | space | down (+1) | 0.725 (pushed) | center | +1.225 | 0.225 below the sp +2 line |
| B4 | 0 | middle line | up (−1) | 0.225 (on-line) | edge | −0.225 | sp −1 space, clears the sp −2 line |

The arc height (§7) then flattens or heightens each arc as needed so its apex also clears the far
bounding line.

---

## 12. Not modelled

- Chords / multiple simultaneous ties (no `Tie_column` equivalent, no tie-tie collision handling).
- Ties across system breaks.
- Collisions with accidentals, other stems, or neighbouring ties.
- LilyPond's penalty search itself — SongScribe reconstructs the winning single-tie geometry
  deterministically and overrides the boundary bug (§9).
