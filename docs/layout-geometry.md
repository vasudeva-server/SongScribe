# Layout Geometry Diagrams

Spatial reference diagrams for `songscribe.layout` and `songscribe.shape`. The classes
themselves carry prose summaries and point here.

See also [Line Layout Rules](line-layout.md) for the placement rules these geometries
serve, and [Unit Conversion](unit-conversion.md) for the pixel/staff-space
conventions.

---

## Ledger-line extent (`NoteGeometry.LedgerLineGeometry`)

Note-relative coordinates, X right-positive, Y down-positive.

```
                      note origin (x = 0)
                           |
        headLeft           |          headRight
           |               |              |
   --------+---------------+--------------+--------   ← a ledger line (one of several),
   |       |          width = headRight - headLeft    drawn at note-relative y = yOffsetSs
   |       |                              |       |   (Y increases downward)
ledgerLeft |                              |  ledgerRight
   <-------->                             <-------->
   lf · width                             lf · width

   accidental clamp midpoint = (accRight + headLeft) / 2
     lands between the accidental's right edge and headLeft, i.e. right of ledgerLeft;
     extentAtSs() pulls ledgerLeft in to this midpoint where an accidental spans the ledger's y.
```

---

## Column extent (`NoteColumnGeometry`)

Note-local space, X = 0 at the notehead glyph origin.

```
  Stem up:

    accidental  notehead    stem-right
    |           |<-- notehead -->|stem|
    |←  left   →|               |←right

  Stem down:

    accidental  stem  notehead
    |           |stem |<-- notehead -->|
    |←  left   →|    ←left            →right
```

Full-extent left is driven by the accidental if present, else the stem's left edge when the
stem points down, else the notehead's left edge. Full-extent right is driven by the stem's
right edge when the stem points up, else the notehead's right edge or the augmentation dots.
The stem-free glissando-attach extent drops the stem contribution entirely, so its left and
right come only from the accidental, notehead, and augmentation dots. Ledger lines are
excluded — they are reference lines, not ink the glissando must avoid.

---

## Stem-tip Y (`ElementColumnBuilder`)

```
Stem up:            Stem down:
  |  <- top (tip)     ==o==  <- top (notehead top)
  |                     |
  |                     |
 ==o==                  |  <- bottom (tip)
```

For stem-up elements the top is the stem tip above the head. For stem-down or stemless
elements the top is the top edge of the element's own glyph bounding box — a rest reaches
far higher than a notehead, and a barline spans half the staff height — rather than a fixed
notehead half-height.

---

## Optical stem-overlap correction (`OpticalSpacing`)

Screen-down Ss axis; negative is higher on the staff.

```
    -Ss --- stem tip (UP stem)
          |
          |       prev (UP)        curr (DOWN)
    top --|       +---+             +---+   <- getAbsoluteTopYSs  (smaller / higher)
          |  o====|   |       o=====|   |
      0 --+---- staff middle line ---------
          |       |   |notehead    |   |
    bot --|       +---+             +---+   <- getAbsoluteBottomYSs (larger / lower)
          |                          |
    +Ss --                          +-- stem tip (DOWN stem)

    verticalOverlapSs = min(bottoms) - max(tops)   (> 0 only where the spans intersect)

    A grace note is a stem-UP row like any other, on its shorter grace stem.
```

| prev | curr | fires when | correction (Ss) |
| ---- | ---- | ---------- | --------------- |
| stem UP | stem DOWN | overlap > 0, not a knee | `+ramp * OPPOSITE_STEM_MAX_CORRECTION_SS` |
| stem DOWN | stem UP | overlap > 0, not a knee | `-ramp * OPPOSITE_STEM_MAX_CORRECTION_SS` |
| stem X | stem X | \|deltaPos\| > `SAME_DIRECTION_THRESHOLD_SS` | `±SAME_DIRECTION_MAX_CORRECTION_SS` (widen if curr is higher) |
| barline | stem DOWN | overlap(staff span, curr) > 0 | `+ramp * DOWNSTEM_BARLINE_MAX_CORRECTION_SS` |
| — | — | otherwise | 0 |

where `ramp = min(overlapSs / STEM_OVERLAP_SATURATION_SS, 1.0)`.

---

## Spring recipe (`HorizontalSpacingCalculator.buildSpring`)

Each `Spring` governs the delta-X between one adjacent column pair, `prev` to `curr`.

```
  base rest  ┌ grace note prev ──▶ prevRight + GRACE_HOST_REST_SS     (fixed, never scales)
             ├ same beam group ──▶ rightExtentExclAug + factor × lineRest  (0.6× both ≤16th,
             │                                                              else 1.0×)
             └ otherwise ────────▶ rightExtentExclAug + lineRest                      (1.0×)

  strut = max( note-collision floor    prevRight + MIN_COLUMN_GAP_SS + |currLeft|
             , syllable-collision floor prevSyl/2 + prev.minCollisionGapToNextSyllable
                                                  + currSyl/2   (either bears a syllable;
                                                    floor = 1 space, or bare hyphen if hyphenated)
             , glissando reservation    prevRight − currLeft
                                                  + MIN_GLISSANDO_RESERVATION_SS
                                                                (prev has a glissando)
             , grace compression floor  rest − GRACE_HOST_COMPRESSION_ALLOWANCE_SS
                                                                (prev is a grace note)
             , hairpin reservation      MINIMUM_LENGTH_SS − curr.noteheadWidth
                                                                (only when prev has a hairpin ending at curr) )

  compliance = max(0, rest − strut)     ← rest ≤ strut ⇒ the gap starts frozen

  prevRight = rightExtentFacingSs(prev, curr) — prev's full right extent, except that a grace
              note's flag is not charged when it hangs clear of curr's left-facing band
```

---

## Spring solver (`SpringSpacer.solve`)

Each gap starts at its natural length `max(rest, strut)` — `max` rather than `rest` because a
wide-glyph gap can have `rest < strut`, in which case the strut wins and the gap starts on its
floor. Such a gap simply never gives; the water-fill leaves it there. That is the only way a
gap can be immovable — there is no separate pinning flag.

```
  natural = SUM max(rest_i, strut_i)

         natural <= availableSpanSs                 natural > availableSpanSs
                  |                                           |
                  v                                           v
         +------------------+          floorSum = SUM strut_i
         |     SOLVED       |                            |
         | gap = natural    |            floorSum > available   floorSum <= available
         | (ragged right,   |                    |                     |
         |  O(n), no loop)  |                    v                     v
         +------------------+             +-------------+       WEIGHTED WATER-FILL to unit U:
                                          | INFEASIBLE  |       SUM clamp(w_i·U, strut_i,
                                          | (struts do  |           natural_i) = availableSpanSs
                                          |  not fit)   |
                                          +-------------+
                                                                       |
                                                                       v
                                          length_i = clamp(w_i·U, strut_i, natural_i)
                                            - w_i·U < strut_i  : freeze on the strut (floor)
                                            - w_i·U > natural_i: cap at natural (no stretch)
                                            - otherwise        : the weighted level w_i·U
```

`U` is found by levelling the still-free gaps, clamping the single most-violated one, and
re-levelling the rest — so the loop is bounded by `springs.size()` passes. Exceeding that
bound is a solver bug, not a layout condition, and throws rather than mis-spacing.

---

## Tuplet bracket arms (`TupletBracketShape`)

A sloped bracket gives each corner its own Y, so the horizontal segments tilt with the note
contour. The verticals still hang straight down from their sloped corners by a fixed arm
height. The four corner Ys are supplied by the caller; the class only orders the points.

```
  left corner                          right corner
  (leftXSs, leftYSs)                    (rightXSs, rightYSs)
       o___________                       ___________o
       |           \___         gap   ___/           |
       |               o (gapLeftYSs)  o (gapRightYSs)|
       |                                              |
       o (leftXSs, armBottomYSs)   (rightXSs, armBottomYSs) o
```
