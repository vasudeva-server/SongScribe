# Drag Selection

Which piece of a rubber-band drag decides what, and why the gesture's vertical
position never affects what gets selected.

## Two pieces, and one of them owns both answers

A drag has two horizontal positions: the anchor, fixed where the mouse went down,
and the lead, which follows the mouse. The **band** is the interval between them,
and it is nothing else — neither end widens out to the notation under it, so the
band's edge tracks the mouse continuously, including while the mouse is inside a
column.

The **reach** is what the drag is allowed to select, and how far across the staff
its edges may travel. Those two questions have one answer between them, derived
once per drag event from the line and its layout, because stating them separately
is what lets a painted band lie over notation that then refuses to highlight.

```
      col 2            col 3                col 4
   ┌─────────┐      ┌─────────┐          ┌─────────┐
───┤  ♯ ♪ ·  ├──────┤    ♩    ├──────────┤   ♩ ··  ├───▶
   L2       R2      L3       R3          L4       R4
        gap              gap

        ▲ anchor                   ▲ lead
        └────────── band ──────────┘

col 2   band starts inside it, past L2  →  selected
col 3   band covers it                  →  selected
col 4   band stops short of L4          →  not selected
```

So the band decides nothing on its own. A band edge resting mid-column is not an
in-between state: the column it is standing in is already wholly selected, because
membership is touching the ink rather than enclosing it.

The song's auto-maintained terminal is left out of every drag by being absent from
the reach. It is not a member because it was never collected, and the edges stop
where the collection runs out — one fact, not a membership rule and a travel limit
that have to be kept agreeing with each other.

## Only the horizontal position matters, once the gesture has started

The mouse's vertical position is never consulted while a drag is under way. A note
on a high ledger line, a rest and a barline are swept identically by a band at the
same horizontal position, however far above or below the staff the mouse has
moved — including off the staff entirely, up in the tempo band or down in the
lyrics. The gesture ends only on release.

The press that starts it is the exception, and it is a different question. A band
spans the staff and sweeps by horizontal position alone, so a gesture beginning
off the staff is one the user has no way to aim; a press only arms a band if it
lands within the staff, both staff lines included. The line component is far
taller than its staff, and the clickable regions inside it are only as tall as
what they draw, so without that test a press in the empty space above the staff
would arm a band from anywhere at all — including horizontal positions the drag is
not allowed to reach.

The band's own vertical extent comes from staff geometry, straddling the top and
bottom staff lines, never from where the mouse happens to be.

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
