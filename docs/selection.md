# Column-Band Drag Selection

This document explains the geometry behind a drag selection ("rubber-band"
select) on a line: why the band snaps to whole columns instead of tracking
the mouse pixel-for-pixel, and why the band's vertical position never
affects what gets selected.

**Issue:** vasudeva-server/SongScribe#721

Scope: this covers the band geometry introduced by #721 — how a drag's
horizontal extent turns into a selected range — and, below, the double-click
key-edit targets added later, which reuse some of that same header and
cautionary geometry for a different gesture. It does not cover the
`HitPriority` cascade that resolves a click to a `HitTarget`, the click
(non-drag) selection path, or the anchor contract for Shift+click range
extension (#748); those are pre-existing and unrelated to this document.

## The band diagram

A drag has two x-coordinates in line-local staff spaces: `anchorXSs`, fixed
at the press, and `leadXSs`, which tracks the mouse on every drag event.
Each contributes either a single point or a whole column's span to the
band, depending on whether it currently falls inside a column:

```
      col 2            col 3                 col 4
   ┌─────────┐      ┌─────────┐          ┌─────────┐
───┤  ♯ ♪ ·  ├──────┤    ♩    ├──────────┤   ♩ ··  ├───  x →
   L2       R2      L3       R3          L4       R4
        gap              gap

anchor at A, inside col 3   →   anchor contribution = [L3, R3]

  ┌────────────────────────────────────────────────────────┐
  │ lead P in a gap        → point  {P}      band = [L3, P] │
  │ lead P inside col 4    → span [L4, R4]   band = [L3,R4] │
  │ lead P left of col 2   → point  {P}      band = [P, R3] │
  │ lead P inside col 2    → span [L2, R2]   band = [L2,R3] │
  └────────────────────────────────────────────────────────┘

  selected = every sweepable column whose [L, R] overlaps the band
```

A column's span is `[getLeftEdgeXSs(), getRightEdgeXSs()]` — full ink,
with a leading accidental enclosed on the left and augmentation dots or a
fall enclosed on the right. "Sweepable" columns are every element on the
line except the song's auto-maintained terminal.

The band itself is just the smallest interval containing both
contributions. That single rule is what produces snap-on-entry,
hold-in-gap, and correct reversal, described below.

## Snap-on-entry and hold-in-gap

While the mouse moves through a gap between columns, its contribution is a
single point, so the band's leading edge tracks the mouse continuously —
same as a free-form drag would.

The moment the mouse enters a column, that column's contribution becomes
its *whole span*, not just the point the mouse is currently over. The band
snaps out to the column's far edge immediately, and — because the
contribution stays the column's full span for as long as the mouse remains
inside it — the band holds there without shrinking or growing as the mouse
moves around within the column. Only when the mouse leaves the column does
its contribution revert to a point, and the band resumes tracking
continuously from wherever the mouse already is (past the column's edge),
so it never snaps backward over ground it just covered.

This is the same feel as double-click-drag text selection in a word
processor: the selection jumps by whole words, not by character, once a
word has been entered.

## The anchor column is always fully enclosed

The press point's column (if the press landed inside one) contributes its
full span for the entire drag, regardless of which direction the mouse
later moves. This is what makes reversal correct: press inside column 3,
drag right to column 5, then back left past column 3 to column 1 — the
anchor's contributing edge simply flips from one side of column 3 to the
other as the drag crosses it, and the band correctly encloses columns 1–3
at the end, never losing column 3 along the way.

## X-only hit basis

The mouse's Y coordinate is never consulted. A note on a high ledger line,
a rest, and a barline are swept identically by a band at the same x,
regardless of how far above or below the staff the mouse is — including
while the mouse is off the `LineComponent` entirely, in the tempo band, or
down in the lyrics. The gesture ends only on mouse release.

The band's vertical extent is derived purely from staff geometry — it
always spans `LineComponent.getMiddleLineYSs() ∓ Staff.STAFF_HALF_SS`, with
the stroke centered on those bounds so it straddles the top and bottom
staff lines — never from where the mouse happens to be vertically.

## The grace-host overlap tie-break

Column spans are ordered by index and normally don't overlap, but a grace
note's full span can legitimately overlap its host's span (the host is
charged only the grace note's Y-band-restricted right extent against
itself, not its full width). In that overlap sliver, `columnAt(xSs)`
resolves to the **lower index** — the grace note, which always precedes
its host — because `LayoutHitTester.findElementAtXSs` scans columns in
index order and returns on the first match.

This tie-break only affects which column the band's *lead* snaps to when
the mouse is in that sliver; it doesn't change what ends up selected.
Snapping the lead to the grace note's own right edge still leaves the
band overlapping the host's span, so the host is selected either way —
only the painted band's exact right edge differs, by a fraction of a
glyph.

## What the leading edge can reach

The leading contribution is clamped, before the band is computed, to the
stretch of staff a selection is allowed to cover:

- **Left** — one document pixel past the staff header's right edge. The
  header is the clef and key signature; a single press there selects the
  staff lines rather than sweeping, so the band stops just clear of it. A
  double-click there does something else entirely — see **Double-click
  key-edit targets** below.
- **Right** — the auto-maintained terminal's left ink edge, or the end of
  the staff (`Song.getLineWidthSs()`) on a line that has no terminal. There
  is no matching one-pixel gap here: the terminal is right-aligned with the
  end of the staff, so stopping at its left edge already leaves no
  reachable staff beyond it.

The limits are the *staff*, not the music on it: the band reaches the bare
staff on either side of the notes, and only the terminal, if there is one,
cuts that short. Dragging on into the right margin therefore adds nothing
once the edge has clamped, and no amount of dragging can make the band
touch the terminal. That is the same terminal-exclusion guarantee as #713,
now enforced by the clamp rather than a per-element
`isAutoMaintainedTerminal` check during the sweep.

The header's one-pixel gap is measured in *document* pixels, so the
reachable range covers the same music at every zoom, for the same reason
the band's own endpoints are held in staff spaces (below).

The anchor's contribution is not clamped: a press in a leading gap (or
past the last element) keeps its raw x, so the empty-band phase — a band
that is painted but selects nothing until it first overlaps a column —
behaves the same regardless of which side of the content the press
started on.

## Lines with nothing to sweep

A press only arms a band where there is something to select. On an empty
line, and on a line holding nothing but the auto-maintained terminal, the
press leaves the band unarmed and the drag that follows is a complete
no-op: no band is painted and the selection is left alone. A rubber band
swept across bare staff would tell the user the gesture is doing something
it cannot do.

## Double-click key-edit targets

There is no song-wide key; each line has or inherits one. Three double-click
targets edit a key, all resolved by `LayoutHitTester` and contracted by
**what each resolves to**, not by where it sits — a hit test that only
answered "is this point inside the rect" would leave every caller to
re-derive which line or element the rect stands for:

- **A line's header** — the same clef-and-key-signature region a single
  press there selects the staff lines for (see **Left**, above) — double-
  clicks to edit **that line's own** key. Every header is a target, whether
  or not the line holds a key of its own: a header that merely restates an
  inherited key is drawn identically to one that changes it, so both accept
  the double-click. If the line was inheriting, setting a key on it gives it
  that key and a cautionary is then drawn automatically at the end of the
  previous line; removing an existing key makes the line inherit again and
  the cautionary disappears.
- **The cautionary key change at the end of a line** double-clicks to edit
  the key of the **following** line, not the line the cautionary is drawn
  on. It renders a change that lives on the line after it, so double-
  clicking it edits that change where the user sees it — the one target
  whose subject differs from the line it visually sits on. The rect tested
  is the one rendering draws, including the overflow-relative placement an
  overflowing line gives it (see `LayoutResult#overflowsStaffWidth`), so the
  target follows the glyphs rather than the staff margin.
- **A mid-line key signature's own rendered accidentals** double-click to
  edit that `KeyChangeElement` directly — an ordinary column, hit-tested
  the same way any other element's column is.

These are edit targets, not selection targets: the single-press-selects-
the-staff-lines behavior the header keeps under the band geometry above is
unrelated to what a double-click there does, and neither the band nor these
targets change what the other one hits. What happens once a double-click
resolves to one of the three — opening a dialog, routing a commit, reporting
a fit rejection — belongs to `ScoreViewController`, not to this document or
to the hit-testing layer.

## Why band geometry is stored in staff spaces

The handler stores `anchorXSs` and `leadXSs` as staff-space doubles, not as
a pixel rectangle. The pixel rectangle used for painting is derived from
those two values, the current `ViewScale`, and
`LineComponent.getMiddleLineYSs()` fresh on every paint, rather than being
computed once and cached.

This is what lets a zoom change mid-drag repaint correctly: staff-space
coordinates describe a position in the music, not a position on screen, so
they stay valid across a zoom. A stored pixel rectangle would not — it
would keep painting the drag's old screen extent over content that has
since moved and rescaled underneath it. Re-deriving the pixel rectangle
every paint keeps the band pinned to the same musical x range no matter
what the zoom does mid-drag.
