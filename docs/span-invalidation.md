# Span Invalidation

This document explains why span invalidation — the machinery that decides
whether a tie, tuplet, ending or hairpin survives an edit to the line's
elements — is built around a single hook and a projected element list, rather
than a predicate per edit shape per span type.

## The single funnel

Every edit to a line's elements goes through exactly four primitives:

- `Line.addElement` — insert one element
- `Line.setElement` — replace one element with another
- `Line.removeElement` / `Line.removeRange` — delete one or more elements

Each of the four, before it touches `Line.elements`, calls one private
helper:

```
Line.addElement / setElement / removeElement / removeRange
    → Line.applySpanOutcomes(ElementChange)
        → Span.outcomeFor(ElementChange, Line)   // once per span on the line
```

`applySpanOutcomes` asks every span on the line to judge itself against the
pending change, collects the answers, and only then acts on them — `Remove`
routes to the typed removal that emits the right mutation, `Reshape` feeds the
hairpin merge (see below), and `KEEP` does nothing. Adding another primitive in
the future means adding at most one more `ElementChange` case — `removeElement`
and `removeRange` already share `Deletion` — and never another predicate on
every span class.

## The projection

`ElementChange` describes the edit as its *result*, not as its description.
Each of the four primitives builds an `ElementChange.Insertion`,
`Replacement` or `Deletion` from the line before the line is mutated, and that
change carries `projectedElements()` — the list exactly as it will read once
the change lands — plus an index lookup (`projectedIndexOf`) from element to
its position in that projected list.

A span's `outcomeFor` reads only the projection. It never re-derives "was I
touched" from a description of the edit (an inserted index, a deleted-element
set, an old/new pair); it re-resolves its own endpoints against the list the
edit is about to produce and decides from there. This is what lets one hook
serve insertion, replacement and deletion uniformly: the span doesn't care
what kind of edit produced the projected list, only whether its endpoints
still make sense inside it.

### The projection is a view, not a copy

`projectedElements()` does not copy the line. It returns a `ProjectedElements`
— an `AbstractList` that maps each projected position onto the pre-change line
— and `projectedIndexOf` maps the other way, over the identity map
`Line.getElementIndex` already caches. Both directions are arithmetic:

- `Insertion` — the inserted element sits at `index`, and everything from
  `index` onward is one place later than it was.
- `Replacement` — `newElement` sits at `index`; nothing moves, and the replaced
  element reports -1.
- `Deletion` — a position inside `[from, to]` reports -1, one after it moves
  down by the range's length, and one before it does not move.

Building any change is therefore O(1) in the length of the line, and so is
either direction of the mapping.

`Deletion` carries the range `[from, to]` rather than the elements being
removed, because a range is all a deletion can ever be: the editor has no
non-contiguous selection to delete from, and both primitives that delete —
`Line.removeElement` and `Line.removeRange` — are handed a position or a range.
`Line.hasEndingInvalidatedByDeletion` takes the same two positions for the same
reason. Stating the invariant in the type is what keeps the mapping to three
comparisons rather than a search over a derived set of positions, and
`Deletion.deletedElements()` becomes a sublist view of the line instead of a
list anyone has to build.

This is worth the arithmetic because **`Hairpin` is the only span type that
reads the projection at all.** `Tie`, `Tuplet`, `Ending` and `Beam` all answer
from the edit's description through the default `Span.outcomeFor` bridge, and
`Line.applySpanOutcomes` asks for the projected list only when some hairpin
answered `Reshape`. Copying the element list and building an index map on every
element edit therefore did work that almost every edit threw away — ties and
beams are the common spans, and hairpins are rare.

**The view reads the live line, so it is valid only until the line's elements
are mutated,** and nothing may retain one past that point. That is exactly the
window every caller uses it in: `applySpanOutcomes` runs before the primary
element mutation, and the span mutations it emits in between touch `Line.spans`,
never `Line.elements`.

## The three outcomes

```java
public sealed interface SpanOutcome {
    enum Simple implements SpanOutcome { KEEP, REMOVE }

    record Reshape(int begin, int end) implements SpanOutcome { }
}
```

The two outcomes that carry no data are an enum, not a pair of stateless
records: there is exactly one of each, and only an enum can say so — a record's
canonical constructor cannot be narrowed, so any caller could mint another
`Keep`. Since Java 21 a qualified enum constant is a legal case label for a
sealed selector type, so `switch (outcome)` still reads flat over all three and
is still checked for exhaustiveness.

`Reshape`'s `begin` and `end` are positions in `change.projectedElements()`,
never in the pre-change line — a caller that indexes them into the old list
gets the wrong elements.

`Tie`, `Tuplet` and `Ending` only ever return `KEEP` or `REMOVE`; a
tie, tuplet or ending endpoint is either still valid or it isn't. `Hairpin`
is the only type that returns `Reshape`. On a deletion, it returns `Reshape`
for **every** surviving hairpin, not `KEEP` for the ones whose span didn't
move — `Line`'s cross-span merge pass (below) needs every surviving hairpin's
projected span to decide what merges with what, not just the ones whose
endpoints happened to shift. `Line.applySpanRun` already emits no mutation
for a run of one whose endpoints both survive unchanged, so returning
`Reshape` unconditionally costs nothing: it is the one place a hairpin
deliberately departs from the "`KEEP` means untouched" reading everywhere
else in the sweep.

## Why decisions are made before the change lands

Undo replays a step's mutations in reverse. If a companion span mutation
(a removal or a reshape) were decided *after* the primary element mutation
had already landed, the span would be judged against a line `Line.setElement`
has already re-pointed — and any mutation recorded from that decision would
carry a span pointing at the wrong element. Replaying it backward on undo
would then restore the span in a broken state. `outcomeFor` is therefore
always called against the pre-change line and a projection of what it will
become, and the four primitives call `applySpanOutcomes` *before* applying
their own mutation, so the companion span mutation is recorded first and
undone last.

## Replacement re-points, it does not delete

`Line.setElement` re-points any span whose anchor or end was the replaced
element at the replacement — that is what makes a surviving tie or tuplet
follow an edited note instead of vanishing. `ElementChange.Replacement`
reflects this: `oldElement` is absent from `projectedElements`, and
`newElement` sits at the replaced index. A span whose endpoint is
`oldElement` must be read as having that endpoint *become* `newElement`, not
as having lost an endpoint. Reading it the other way — as a deletion of
`oldElement` — would silently remove every tie, tuplet and ending whose
endpoint is being edited, since each would see its endpoint vanish from the
projection with nothing to replace it.

## The sweep is skipped while replaying

Each of the four call sites guards `applySpanOutcomes` behind
`!song.isReplaying()`. Replaying a recorded step already carries the
companion span mutations (removals, reshapes) that were captured when the
step was first performed; re-deriving them during replay would apply them a
second time.

## A reshape has no mutation of its own

Spans have no "modify in place" mutation — only add and remove exist in the
undo model. A `Reshape` is therefore expressed as a tracked removal of the
old span plus a tracked addition of a copy at the new `[begin, end]`, exactly
as `Line.applySpanRun` does for the hairpin merge today. This keeps the
mutation vocabulary small: everything that isn't a `Keep` is either a removal
or a removal-plus-addition, never a third kind of record.

## What stays on `Line`, and why

Not everything moved onto the spans. Three things are and remain `Line`
concerns:

- **The cross-span hairpin merge** (`Line.mergeAdjacentSpans` /
  `Line.applySpanRun`). No span knows its siblings — deciding whether two
  adjacent same-type hairpins should combine into one is inherently a
  property of the collection, not of either hairpin alone. `Line` groups the
  `Reshape` outcomes for each hairpin type and runs the merge pass over them.
- **The generic overlap merge** (`Line.mergeOverlappingSpans`). It is not
  hairpin-specific: `Line.addBeaming` calls it for `Beam` with
  `absorbAdjacent=false`, and `Line.addHairpin` calls it for hairpins with
  `absorbAdjacent=true`. Since it already serves two span types with a shared
  general-purpose algorithm, it has no reason to move onto either one.
- **`Line.SPAN_ADJACENCY_REACH`**, the adjacency-reach constant the generic
  merge reads. It stays public on `Line` because `MusicEditOperations` and
  `Line.mergeAdjacentSpans` both read it from there directly.

## See also

`docs/hairpin-editing.md` documents the hairpin-specific rules —
`Hairpin.canAnchorAt`, `Hairpin.canEndAt` and how `Hairpin.outcomeFor` applies
them — that this framework hosts but does not define.
