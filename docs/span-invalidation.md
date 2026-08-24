# Span Invalidation

What happens to a tie, tuplet, ending or hairpin when the elements underneath it
change. The arrangement matters more than the individual rules, because the
obvious alternative — a predicate per edit shape per span kind — is a grid that
grows in both directions.

## One funnel

Every edit to a line's elements goes through a small closed set of primitives:
insert one, replace one, delete one or a range. Each of them, **before touching
the elements**, asks every span on the line what should happen to it, collects the
answers, and only then acts.

Adding another primitive later means adding at most one more kind of change — the
two deletion primitives already share one — and never another predicate on every
span class.

Every span is asked first and acted on afterwards, not judged-and-acted one at a
time. Acting inside the loop would both mutate the collection being walked and let
one span's removal change what a later span is judged against.

## Spans judge a projection, not a description

A change describes itself as its **result**, not as its own terms: it carries the
element list exactly as it will read once the change lands, and a mapping from
element to its position in that list.

A span therefore never re-derives "was I touched" from an inserted position, a
deleted set, or an old/new pair. It re-resolves its own endpoints against the list
the edit is about to produce and decides from there. That is what lets one hook
serve insertion, replacement and deletion uniformly — the span does not care what
kind of edit produced the projected list, only whether its endpoints still make
sense inside it.

**The projection is a view, not a copy.** It maps each projected position onto the
unchanged line arithmetically, in both directions, so building one costs nothing
in the length of the line. Deletion carries a *range* rather than a set of removed
elements, because a range is all a deletion can ever be — there is no
non-contiguous selection to delete from — and stating that in the type is what
keeps the mapping to a few comparisons instead of a search.

That frugality is worth having because **only hairpins read the projection at
all.** Every other span kind answers from the edit's own terms, and the projected
list is built only when some hairpin asks for it. Copying the element list and
building an index on every edit would do work that almost every edit throws away:
ties and beams are the common spans, and hairpins are rare.

The view reads the live line, so it is valid only until the elements are mutated
and nothing may retain one past that point. That is exactly the window every
caller uses it in.

## Three answers

A span answers keep, remove, or reshape.

Keep and remove carry no data, and there is exactly one of each — which is why
they are enum constants rather than a pair of empty records, since a record's
constructor cannot be narrowed and any caller could mint another. Reshape's
positions are positions **in the projection**, never in the pre-change line; a
caller indexing them into the old list gets the wrong elements.

Only hairpins ever reshape. And on a deletion a surviving hairpin reshapes
*unconditionally*, even where its span did not move — because the cross-span merge
below needs every surviving hairpin's projected extent to decide what merges with
what, not just the ones whose endpoints happened to shift. Emitting no change for
a reshape that turns out to be identity costs nothing, so this is the one place a
span departs from the "keep means untouched" reading used everywhere else.

## Decisions are made before the change lands

Undo replays a step's changes in reverse. If a companion span change were decided
*after* the element change had landed, the span would be judged against a line
whose references had already been re-pointed — and any record made from that
decision would carry a span pointing at the wrong element. Replaying it backwards
would then restore the span broken.

So spans are always asked against the pre-change line and a projection of what it
will become, and the companion change is recorded **first** and therefore undone
**last**. See [mutations.md](mutations.md).

## Replacement re-points; it does not delete

Replacing an element re-points any span anchored to it at the replacement — that
is what makes a surviving tie or tuplet follow an edited note instead of
vanishing. The projection reflects this: the old element is absent and the new one
sits at its position, so a span whose endpoint was the old element must read as
having that endpoint *become* the new one.

Reading it the other way — as a deletion — would silently remove every tie, tuplet
and ending whose endpoint is being edited, since each would see its endpoint
vanish with nothing to replace it.

## The sweep is skipped while replaying

A recorded step already carries the companion span changes captured when it was
first performed. Re-deriving them during replay would apply them a second time.

## A reshape is a removal plus an addition

Spans have no modify-in-place record; only add and remove exist. A reshape is
therefore expressed as a tracked removal of the old span and a tracked addition of
a copy at the new extent. This keeps the vocabulary small: everything that is not
a keep is either a removal or a removal-plus-addition, never a third kind.

## What stays with the line

Not everything moved onto the spans, and two things deliberately did not.

**Merging adjacent same-type hairpins** is a property of the collection, not of
either hairpin: no span knows its siblings, so deciding whether two should become
one cannot be asked of either. The line groups the reshape answers by kind and
runs the merge over them.

**The generic overlap merge** is not hairpin-specific — beams use it too, with
adjacency absorption turned off, since two beam groups written back to back are
two deliberate groupings while two same-type hairpins are one hairpin. Serving two
kinds already, it has no reason to move onto either.

See [hairpin-editing.md](hairpin-editing.md) for the hairpin-specific rules this
framework hosts but does not define.
