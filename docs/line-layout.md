# Line Layout

How everything above and below a staff finds its place. All distances are in
staff spaces; see [Spatial Units](../.claude/guides/spatial-units.md) for the
convention code follows.

## There is no bounding box

Nothing computes a note's full extent as an object that later elements collide
against. Instead each pass writes what it occupies into a shared **skyline** — a
running record, indexed across the line, of how far out anything reaches at each
horizontal position — and reads that same skyline to find its own place.

The consequence is that every layer automatically clears everything reserved by
earlier layers *within its own horizontal footprint*, and nothing clears anything
at other positions. Two marks far apart on the line never push each other out,
which is not a special case anyone wrote; it falls out of the skyline being
indexed by position.

In practice this behaves like a bounding box once articulations are placed, but
no such box exists, and the difference shows at the edges: ledger lines add no
vertical extent of their own, and an articulation is accounted for only because
it wrote its own reservation before later elements asked.

## Tiers, nearest the staff first

Each tier stacks against a skyline that already reflects every earlier one.

**Above the staff:**

1. note heads and stems, then tie arcs
2. articulations, on the opposite side from the stem
3. tuplet brackets
4. fermatas
5. trills — after fermatas, so a trill always clears one
6. hairpins and text dynamics, as one tier
7. first and second endings
8. tempo, then beat change
9. text annotations
10. attribution, topmost, first line only

**Below the staff:** lyrics, and nothing else. Annotations are unconditionally
forced above, so a document that places one below is corrected on read.

Hairpins and dynamics are **one tier rather than two**, and that is load-bearing:
they are placed group by group so a hairpin and the dynamic beside it share a
reference line, which is impossible if a second sweep stacks outside whatever the
first reserved.

Two collision styles are in use. Most marks reserve a plain rectangle. Those
whose silhouette differs sharply from their box — the staccato dot, the accent
wedge, the trill's flourish — are reserved and cleared by their actual outline
instead, so an accent can tuck under the curve of what sits above it.

Endings are the one exception to whole-element collision: a bracket reserves
several separate regions rather than one, because only its top line needs
clearance. Content nested *inside* the bracket does not collide with it.

## Where the first note sits

The measurement is taken from a different edge depending on what precedes it.
With a key signature, the note stands a fixed gap past the signature's right
edge. Without one, the span is measured from the clef's *left* edge and has a
floor the clef's own width does not reach — so the clef's width drops out of the
answer entirely.

Every line draws a key signature, whether it established that key or inherited
it, so "no key of its own" is not "no key signature". A line running in a key
with no accidentals is the no-signature case.

## Mid-line and cautionary key signatures

A key change written into a line is an ordinary element in the line's element
list, placed by the same chain of springs as every other column — but its width
is not a per-type constant. It is the width of the accidentals actually drawn,
which depends on the key in effect immediately before it: a change that cancels
the previous signature is wider than one that does not, and the spacing reflects
that.

The gap between the barline and the key signature behind it is **frozen**: its
resting length and its collision floor are the same value, so the solver can
never compress it, and it takes no share of the lyric lift. The accidentals stand
the same distance behind their barline on a crowded line and an empty one alike.
Ahead of the signature, the following note clears the last accidental by an
ordinary gap.

A cautionary key signature at the end of a line — drawn when the next line begins
in a different key — is given the *whole* of the trailing space rather than a
floor under it. Taking the larger of the reservation and the ordinary line rest
would push a narrow cautionary off its padding and leave it further from its
barline than the padding allows.

A key change is not local to the line it is made on. It claims horizontal space
in four places, all of which are measured before the edit is accepted: the
cautionary at the end of the line before it, the header of every line the change
re-keys, the cautionary at the end of each of *those* lines, and — for a mid-line
change — the signature's own column plus any barline inserted with it. "Every
line the change re-keys" is the inheritance chain; see
[key-changes.md](key-changes.md).

Those lines are measured by the identical solver the committed layout runs, over
columns built the same way, so a pre-check cannot disagree with the result. The
keys a solve reads travel together as one value, so the check states the keys the
edit *would* produce rather than reading some from the edit and the rest from the
unedited document.

## The pitch grid

Vertical position on the staff is an integer count of diatonic steps from the
middle line, increasing downward, each step half a staff space. Even values are
lines, odd values are spaces — which is why an articulation's clearance differs
between the two.

## Articulations

An articulation goes on the **opposite side of the note head from the stem**.

A staccato dot inside the staff sits further from a note on a line than from one
in a space, so the dot does not visually merge with the line it would otherwise
touch. Beyond the staff the distance rule changes: the dot is anchored to the
staff edge instead, and its reserved extent is its outline rather than its box.

An accent is not a position-dependent special case, even though it behaves like
one. It is placed by the same generic skyline collision as everything else, taking
whichever is further out: what is already reserved beneath it in that column,
padded; or the staff line's ink edge, padded by a floor. That produces staff-edge
anchoring for notes within or near the staff and note-head anchoring for notes far
out on ledger lines, without either being written down as a rule. An accent above
a staccato clears the dot for the same reason — the dot is simply the nearest
reserved neighbour.

## Dynamics and hairpins

A hairpin and a text dynamic beside it share one reference line, and align on it
differently: the hairpin's full height is centred, while the dynamic's glyph sits
low enough that its x-height centre lands on the line. Centring the glyph's box
instead would leave differently-shaped dynamics at visibly different heights.

A dynamic may sit on either **bound** of a hairpin or immediately outside it, and
joins that hairpin's group either way. Only the strict interior is forbidden, and
that is an editing rule rather than a layout one — an imported dynamic inside a
wedge falls through to its own independent group instead of joining. See
[hairpin-editing.md](hairpin-editing.md) for the editor decision that produces
these shapes.

**Where a hairpin's tips stop** is a first-match cascade, from the notehead
outward: a dynamic on the bound element pulls the tip back to that glyph's edge; a
back-to-back hairpin meeting at the shared element stops both tips short of the
notehead centre; a dynamic just outside the span pulls the tip similarly; a
hairpin ending on a rest stops at the rest's left edge; otherwise the tip takes
the note head's own edge.

Minimum length is applied by moving the **right** tip only, and never where a
dynamic placed that tip — widening there would drive the wedge back under the
glyph the padding just cleared.

Two measurement details matter more than they look. A dynamic's *pull-back*
measures from the glyph's advance width, not its ink, because the italic dynamic
glyphs paint well outside their declared box and measuring from the ink puts the
wedge about twice as far away as it should be. The glyph *itself* is still
centred over its note head by ink, because that is what looks centred. Only the
wedge's stopping point uses the advance box.

A dynamic on a bound pulls that tip inward by roughly two staff spaces, so a
hairpin spanning two adjacent notes needs a wider gap between them than the bare
minimum length implies. Spacing reserves that extra distance, which is why a
dynamic on a compressed line pushes its two notes apart instead of collapsing to
an invisible wedge. The pull-back is stated once, as an offset from the column
origin, so spacing can ask for it before any column has a resolved position, and
a test pins that the two readings agree.

Three of the tip rules are deliberately **not** reserved for: a dynamic on the
element *outside* the span pulls by an amount that depends on a neighbouring
pair's spring, which the per-pair model cannot see, and the back-to-back and rest
rules only ever shorten the wedge.

Manual offsets still exist and are applied after layout with no collision re-run.
They are an override on top of automatic coordination, not the only mechanism.

## Metronome markings are typeset once

A metronome marking — a beat change, a tempo change, or the song's own tempo mark
— is typeset **once, by layout**, into a positioned list of glyphs and text.
Nothing downstream measures anything: each item carries its offset on both axes
and its own font, and the renderer walks the list and draws.

That is the design rather than an incidental property, because three consumers
depend on the same numbers — the marking's horizontal extent, its clickable
target, and its collision reservation. If layout measures one thing and the
renderer draws another, the hit box and the reservation are both wrong by the
difference and nothing in the code can notice. Measuring in one place makes the
divergence unrepresentable rather than merely absent.

A marking reserves **one region per run of ink**. Gaps between items count toward
its width but belong to no region, so nothing is reserved over them and other
marks can nestle into the silhouette. An augmentation dot extends the region of
the note it belongs to rather than taking one of its own, because a note and its
dot are one continuous run.

Note regions and text regions are not the same shape: a note region starts at the
marking's top edge, while a text region sits lower and reaches further down to
cover the font's descenders. Those come from the resolved font rather than from
the characters actually present, so a marking's silhouette does not change shape
when its numbers change.

## Lyrics

The lyric baseline is computed **independently of the above-staff skyline** — it
sits a fixed margin below the bottom of below-staff content, plus the measured
ascent of the lyric font. The height of a lyric row is a separate quantity from
the gap above it; conflating the two is a recurring mistake.
