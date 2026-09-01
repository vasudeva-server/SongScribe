# Layout Geometry

Spatial reference for the parts of layout where the arrangement is easier to see
than to read. See [line-layout.md](line-layout.md) for the placement rules these
geometries serve.

## Ledger-line extent

Note-relative, horizontal right-positive, vertical down-positive.

```
                      note origin (x = 0)
                           |
        headLeft           |          headRight
           |               |              |
   --------+---------------+--------------+--------   ← one ledger line, drawn at
   |       |          width = headRight - headLeft      the note-relative height
   |       |                              |       |    it belongs to
ledgerLeft |                              |  ledgerRight
   <-------->                             <-------->
    overhang                               overhang

   accidental clamp midpoint = midway between the accidental's right edge
     and headLeft. Where an accidental spans the ledger's height, the ledger's
     left end is pulled in to that midpoint — unless the midpoint falls left of
     ledgerLeft, where the base extent stands.
```

## Column extent

Note-local, with the origin at the note-head glyph.

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

The left edge is driven by the accidental if there is one, else the stem when it
points down, else the note head. The right edge is driven by the stem when it
points up, else the note head or the augmentation dots.

The extent used for attaching a glissando is a **different** measurement: it drops
the stem entirely, taking only the accidental, note head and dots. Ledger lines
are excluded from both — they are reference lines, not ink a glissando must avoid.

## Stem-tip height

```
Stem up:            Stem down:
  |  <- top (tip)     ==o==  <- top (notehead top)
  |                     |
  |                     |
 ==o==                  |  <- bottom (tip)
```

For a stem-up element the top is the stem tip. For a stem-down or stemless one it
is the top of the element's own glyph box — a rest reaches far higher than a note
head, and a barline spans half the staff — rather than a fixed note-head
half-height.

## Optical stem-overlap correction

Two adjacent columns whose stems point opposite ways read as closer together than
they are, because the ink between them is a stem and a stem rather than two note
heads. Where their vertical spans genuinely overlap, the gap is widened; where two
same-direction stems sit far apart in pitch, it is adjusted the other way.

```
    higher  --- stem tip (UP stem)
          |
          |       prev (UP)        curr (DOWN)
    top --|       +---+             +---+   <- smaller / higher
          |  o====|   |       o=====|   |
        --+---- staff middle line ---------
          |       |   |notehead    |   |
    bot --|       +---+             +---+   <- larger / lower
          |                          |
    lower --                        +-- stem tip (DOWN stem)

    vertical overlap = min(bottoms) − max(tops), positive only where
                       the two spans actually intersect
```

The correction ramps with how much the spans overlap, up to a cap, rather than
switching on at a threshold — so the spacing does not jump as a pitch changes by
one step. A grace note is an ordinary stem-up row on its own shorter stem. A
barline against a stem-down note is corrected too, measured against the staff span
rather than against a note.

The correction is skipped for a knee — two stems already pointing away from each
other — where the ink is not in fact adjacent.

## The spring recipe

Each spring governs the distance between one adjacent pair of columns.

```
  resting length  ┌ grace note before ──▶ a fixed rest that never scales
                  ├ key change after ───▶ a fixed padding that never scales
                  ├ same beam group ────▶ a reduced share of the line rest
                  └ otherwise ──────────▶ the full line rest

  floor  = max( note-collision floor      the two columns' ink, plus a minimum gap
              , syllable-collision floor  half of each syllable, plus the minimum
                                          gap between them (a bare hyphen where
                                          the pair is hyphenated)
              , glissando reservation     room for the slide to be legible
              , grace compression floor   how far a grace note may be pulled in
              , hairpin reservation       room for a wedge whose tips are pulled
                                          back by a dynamic on a bound )

  give   = max(0, resting length − floor)   ← rest ≤ floor ⇒ the gap starts frozen
```

A barline-to-key-change gap is frozen by construction: its resting length and its
floor are the same padding, and it is exempt from the lyric lift, so the
accidentals stand that exact distance behind their barline on every line.

## The spring solver

Each gap starts at its natural length — the larger of its resting length and its
floor, because a wide-glyph gap can have a rest below its floor, in which case the
floor wins and the gap simply never gives. That is the only way a gap can be
immovable; there is no separate pinning flag.

```
  natural total = sum of the natural lengths

         natural ≤ available                    natural > available
                  |                                      |
                  v                                      v
         +------------------+          floor total > available   floor total ≤ available
         |     SOLVED       |                    |                        |
         | every gap at its |                    v                        v
         | natural length   |            +-------------+      WEIGHTED WATER-FILL:
         | (ragged right)   |            | INFEASIBLE  |      raise a common level until
         +------------------+            | the floors  |      the clamped lengths sum to
                                         | do not fit  |      the available width
                                         +-------------+                 |
                                                                         v
                                    each gap = its weighted share of that level,
                                    clamped below by its floor and above by its
                                    natural length
```

The level is found by levelling the still-free gaps, clamping the single
most-violated one, and re-levelling the rest — so the loop is bounded by the
number of springs. Exceeding that bound is a solver fault, not a layout condition,
and fails loudly rather than mis-spacing.

## Tuplet bracket arms

A sloped bracket gives each corner its own height, so the horizontal segments tilt
with the note contour. The verticals still hang straight down from their sloped
corners by a fixed arm length.

```
  left corner                          right corner
       o___________                       ___________o
       |           \___         gap   ___/           |
       |               o               o             |
       |                                             |
       o                                             o
```
