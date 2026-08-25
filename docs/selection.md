# Drag Selection

Why a rubber-band selection on a staff snaps to whole columns rather than
tracking the mouse pixel for pixel, and why its vertical position never affects
what gets selected.

## The band is the smallest interval covering two contributions

A drag has two horizontal positions: the anchor, fixed where the mouse went down,
and the lead, which follows the mouse. **Each contributes either a single point or
a whole column's width**, depending on whether it currently falls inside a column.

```
      col 2            col 3                col 4
   ┌─────────┐      ┌─────────┐          ┌─────────┐
───┤  ♯ ♪ ·  ├──────┤    ♩    ├──────────┤   ♩ ··  ├───▶
   L2       R2      L3       R3          L4       R4
        gap              gap

anchor inside col 3  →  contributes [L3, R3]

  lead in a gap       → point       band = [L3, lead]
  lead inside col 4   → [L4, R4]    band = [L3, R4]
  lead left of col 2  → point       band = [lead, R3]
  lead inside col 2   → [L2, R2]    band = [L2, R3]

selected = every sweepable column whose extent overlaps the band
```

A column's extent is its full ink, with a leading accidental enclosed on the left
and augmentation dots or a fall enclosed on the right.

That single rule produces all three behaviours below; none of them is coded
separately.

**Snap on entry, hold inside.** Moving through a gap, the lead contributes a
point, so the band's edge tracks the mouse continuously. The moment the mouse
enters a column the contribution becomes that column's *whole* width, so the band
snaps out to its far edge and then holds there, neither growing nor shrinking as
the mouse moves around within the column. On leaving, the contribution reverts to
a point and tracking resumes from wherever the mouse already is — so the band
never snaps backward over ground it just covered. This is the feel of
double-click-drag text selection: the selection jumps by whole words once a word
has been entered.

**Reversal is correct for free.** The anchor's column contributes its full width
for the whole drag, whichever way the mouse later goes, so the contributing edge
simply flips from one side of that column to the other as the drag crosses it. The
anchor column is never lost along the way.

## Only the horizontal position matters

The mouse's vertical position is never consulted. A note on a high ledger line, a
rest and a barline are swept identically by a band at the same horizontal
position, however far above or below the staff the mouse is — including while the
mouse is off the staff entirely, up in the tempo band or down in the lyrics. The
gesture ends only on release.

The band's own vertical extent comes from staff geometry, straddling the top and
bottom staff lines, never from where the mouse happens to be.

## What the leading edge can reach

The lead is clamped, before the band is computed, to the stretch of staff a
selection may cover: on the left, just clear of the staff header, since a press
there selects the staff lines rather than sweeping; on the right, the
auto-maintained terminal's left edge, or the end of the staff on a line without
one.

The limits are the *staff*, not the music on it — the band reaches bare staff on
either side of the notes. Dragging on into the margin therefore adds nothing once
the edge has clamped, and **no amount of dragging can make the band touch the
terminal**, which is enforced by the clamp rather than by testing each element
during the sweep.

The anchor is *not* clamped: a press in a leading gap keeps its raw position, so
the empty-band phase — a band that paints but selects nothing until it first
overlaps a column — behaves the same whichever side of the content the press
started on.

**A press only arms a band where there is something to select.** On an empty line,
or one holding nothing but the terminal, the press leaves the band unarmed and the
drag is a complete no-op. A rubber band swept across bare staff would tell the
user the gesture is doing something it cannot do.

## Band geometry is held in staff spaces

The anchor and lead are stored as staff-space values, and the pixel rectangle used
for painting is re-derived from them on every paint rather than computed once and
cached.

That is what lets a zoom change mid-drag repaint correctly: staff-space
coordinates describe a position in the music, not a position on screen, so they
stay valid across a zoom. A stored pixel rectangle would keep painting the drag's
old screen extent over content that has since moved and rescaled underneath it.

## One overlap tie-break

Column extents are ordered and normally do not overlap, but a grace note's extent
can legitimately overlap its host's. In that sliver the lower index wins — the
grace note, which always precedes its host.

This only affects which column the *lead* snaps to inside the sliver; it does not
change what ends up selected, since the band still overlaps the host's extent
either way. Only the painted band's exact edge differs, by a fraction of a glyph.

## Double-click edits a key

Three double-click targets edit a key, and each is contracted by **what it
resolves to** rather than by where it sits — a hit test answering only "is this
point inside the rectangle" would leave every caller to re-derive which line or
element the rectangle stood for.

- **A line's header** — the same region a single press selects the staff lines for
  — edits **that line's own** key. Every header is a target, whether or not the
  line holds a key of its own, because a header restating an inherited key is
  drawn identically to one that changes it.
- **A cautionary at the end of a line** edits the key of the **following** line,
  not the line it is drawn on. It renders a change that lives on the next line, so
  double-clicking edits that change where the user sees it — the one target whose
  subject differs from the line it sits on. The rectangle tested is the one
  rendering actually draws, including the overflow-relative placement an
  overflowing line gives it, so the target follows the glyphs rather than the
  margin.
- **A mid-line key signature's accidentals** edit that change directly, as an
  ordinary column hit-tested like any other.

What happens once one resolves — opening a dialog, routing a commit, reporting a
refusal — belongs to the controller, not to hit-testing. See
[key-signatures.md](key-signatures.md).

## An edit target need not be a selection target

Addressing something with a click and selecting it are separate questions, and a
target answers them independently. The three key targets above answer only the
first, and so does the attribution block above the first staff: double-clicking
it opens the song's settings, while pressing it selects nothing and leaves the
rubber band armed exactly as the empty space around it does.

The two kinds are reached differently and that difference is not incidental. The
key targets are resolved by asking the layout directly, so they never enter the
registry of clickable areas at all. The attribution block is in that registry,
because it has to resolve against the notation it could overlap — which is a
question only the registry can answer, and one the layout cannot.

Being in the registry is therefore not what makes something selectable. The
vocabulary of clickable things separates the two: only a kind declared selectable
can become the selection, so the selection and delete paths are typed to accept
nothing else and never have to rule a kind out. Adding a new clickable thing that
is edited rather than selected costs no decision on either path.
