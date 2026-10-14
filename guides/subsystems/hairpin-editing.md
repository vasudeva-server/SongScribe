# Hairpin Editing

Read `Hairpin` first.

## Membership

- `Hairpin`
- `Spanning`
- `PendingHairpin`
- `RangeQueries`
- `HairpinAction`
- `AddHairpinCommand`

## Rules

- Decide whether a range can carry a hairpin only through `Hairpin.spanning`, because the menu,
  the paste and the load must give the same answer for the same range.
- Resolve the crescendo and the diminuendo menu items separately, because the two can disagree
  about the same selection.
- Recompute `RangeQueries.resolveHairpin` on every ask, because a hairpin added or undone
  without moving the selection makes a cached answer wrong.
