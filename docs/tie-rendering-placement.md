# Tie Placement

Where a tie sits and what shape it takes. The algorithm is ported from LilyPond,
and the useful thing to know is **which parts reproduce it, which parts
deliberately depart from it, and why the structure differs at all**.

## What is modelled

A single tie joining two notes of the same pitch, head to head. Not modelled:
chords with several simultaneous ties, ties broken across a system break, or
collisions with other ties, accidentals or stems.

## Deterministic, where LilyPond searches

LilyPond picks a tie configuration by **penalty-minimising search**: it generates
candidate placements, scores each against aptitude and collision penalties, and
keeps the cheapest.

This program does **not** search. It computes one placement directly,
reconstructing the geometry LilyPond's search converges on for the single-tie
case. That is affordable precisely because of the scope above — with no chord and
no neighbouring ties, the winning configuration is a function of the note's
position and the arc direction, so the search has nothing to explore.

Note spacing and note-head metrics differ from LilyPond's, so absolute
coordinates differ. What is reproduced is the *structure*: which space the tie
sits in, whether it attaches at the head's edge or its centre, and that it clears
the staff lines.

## The governing principle

> Seat the tie in the staff space immediately next to the note in the arc
> direction, placed so that both the endpoints and the apex clear the staff lines
> bounding that space.

Direction is chosen by LilyPond's stem rule, unchanged: with both stems the same
way the arc goes opposite them; with stems conflicting it goes up; with one stem
it goes opposite that one; with none it follows the note's position, defaulting up
on the middle line.

Three things then shift the seat outward from that natural position, each because
the natural one would land on ink:

- a note **on an inner staff line** seats just far enough into the adjacent space
  to clear its own line — the *far* line bounding that space is handled by the arc
  height instead;
- a note **in a space whose next row is a real staff line** is pushed past that
  line;
- a **dotted note on a line** displaces its dot into the very row the tie would
  arc through, so the tie lifts clear of the dot. The dot never moves.

A note on an **outer** staff line is the exception: rather than tucking into the
adjacent space it seats a full one-and-a-half positions out, into the open space
beyond the staff.

## The vertical seat decides the horizontal attachment

This is the part that is easy to miss and explains most of what a tie visibly
does.

LilyPond samples the note's outline **at the endpoint's seat height**, and that
sample is a step function: while the seat is still within the head's box, the
outline is the head's facing edge; once the seat drops below the box, the outline
has receded to the head's centre. The endpoint is then pulled slightly toward the
middle of the tie.

So a half-space change in the *vertical* seat swings the *horizontal* attachment
by about half a note-head width. That is why a fixed horizontal inset cannot
reproduce LilyPond, and why a tie visibly moves from "inset in the gap between the
notes" to "centred under the note heads" as the pitch changes.

## The arc clears the line by changing height, not position

Endpoints are fixed by the seat, so only the height is adjusted, and only against
the staff line nearest the apex:

- **Apex below the line** — keep the natural height, unless the stroked outer edge
  would come too close, in which case flatten just enough to restore the gap. If
  even a flat arc cannot clear underneath, lift it over instead.
- **Apex poking through the line** — heighten the arc up and over, never squash it
  back under, and give it a fixed stroked height independent of the tie's width.

This matches LilyPond's "the arc stays on the side its apex already favours".

The tie is drawn as a filled lens — two curves sharing the endpoints — so the ends
taper naturally, with a stroked outline to blunt the cusps where the curves meet.
Its thickness at the apex is solved backwards from LilyPond's measured ratio
against staff-line thickness, rather than being chosen.

## Where this departs from LilyPond, and why

One deliberate deviation, at the staff boundary.

For a note **on** the outermost staff line the placement is *reproduced*: LilyPond
seats the tie fully outside the staff and attaches it centred under the head, and
so does this program.

For a note in the space **just inside** that line it is *overridden*. LilyPond's
fallback line-clearance test excludes the outermost line from the region it
considers "within the staff", so it leaves the endpoints of such a tie sitting
**on** that line — a genuine edge-case bug rather than a stylistic choice. This
program implements the intent instead: the next row is a real staff line, so the
seat is pushed past it and the endpoints clear.

The in-staff cases are likewise computed from the seat rule rather than from
LilyPond's own vertical-centring routine. The fixed clearance reproduces its
placement closely while *guaranteeing* the endpoints clear the note's own line,
which a fixed seat does not — it drops the endpoints onto the far line.
