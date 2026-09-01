# Design Pass 3 — Step 8b: Visual Gate

Derived from the call sites this pass re-pointed, by following each moved member's
fan-in out to the surface that draws it. Nothing in the suite asserts any of it.

Run with `./scripts/run.sh`. Record Pass/Fail in the table at the end.

## Deliberate behaviour changes — check these first

**1. Drag a note above the top ledger line, then below the bottom one.**
`StaffPosition.fromSs` clamps into the grid where `Staff.ssToSp` did not. The note
stops at the outermost ledger line and stays there while the pointer keeps going.
Regression: it jumps, wraps to the opposite extreme, or follows the pointer past the
grid.

**2. Move the insertion preview off the top and bottom of the pitch range.**
Same clamp, other caller. The preview note still disappears outside the range —
`PreviewElementManager` asks `containsSs` before converting. Regression: a preview
note pinned at the extreme position instead of vanishing.

## Moved glyph measurements

**3. Staff header — clef, key signature, first note.**
The G clef's advance width and the key-signature padding both moved. Regression: the
clef touching the first accidental, or a visibly wider gap than the rest of the score
would lead you to expect.

**4. A key signature of four or more sharps, and one of four or more flats.**
Accidental spacing now comes from a live ink-bbox query rather than a stored constant.
Regression: accidentals bunched or spread unevenly, or the last one crowding the first
note.

**5. A mid-line key change from sharps to flats.**
Regression: cancelling naturals overlapping each other, or the gap between the run of
naturals and the new key's own accidentals gone or doubled.

**6. A line whose following line changes key (cautionary signature).**
Regression: the cautionary's own barline sitting on top of the accidentals, or the run
overflowing the right edge of the staff.

## Ledger lines — the code changed packages

**7. A note three ledger lines above the staff, and one four below.**
The extremes of the grid. Regression: a missing outermost line, an extra line, or
lines half a staff space out of position on a note sitting in a space rather than on a
line.

**8. A high note carrying an accidental.**
Ledger length is a fraction of the notehead width, which is now a live bbox query.
Regression: the line too short to reach under the notehead, or running under the
accidental.

## Everything else the moved constants reach

**9. A whole rest and a half rest.**
Their vertical offsets changed representation. Regression: the whole rest hanging from
the wrong line, or the half rest not sitting on the middle line.

**10. A single barline, a final double barline, and a left-right repeat.**
`BarStroke.SEPARATION_SS` moved packages. Regression: repeat dots overlapping the
thick line, or the thin and thick pair too close together.

**11. First and second endings over a repeat.**
Regression: the volta number clipped at the bracket's right edge, or the right arm
landing on the wrong side of the barline it closes on.

**12. A three-beam group (demisemiquavers), and a French-beamed group.**
Regression: a stem tip stopping short of or reaching past the beam it should touch, or
inner beams unevenly spaced.

**13. Click a beam group to select it, including on its lower half.**
`HitRegionBuilder` now calls `beamStackHeightSs` instead of re-deriving the depth. The
arithmetic is identical, so this confirms rather than suspects. Regression: part of
the stack not selecting when clicked.

**14. A hairpin, a tuplet bracket, and a glissando.**
All three thicknesses moved to `EngravingConstants`. Regression: any of them drawn
noticeably thicker or thinner than the staff lines around it.

## Results

| #  | Result | What was observed, if it failed |
|----|--------|---------------------------------|
| 1  | ok     |                                 |
| 2  | ok     |                                 |
| 3  | ok     |                                 |
| 4  | ok     |                                 |
| 5  | ok     |                                 |
| 6  | ok     |                                 |
| 7  | ok     |                                 |
| 8  | ok     |                                 |
| 9  | ok     |                                 |
| 10 | ok     |                                 |
| 11 | ok     |                                 |
| 12 | ok     |                                 |
| 13 | ok     |                                 |
| 14 | ok     |                                 |
