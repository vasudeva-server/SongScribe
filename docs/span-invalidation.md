# Span Invalidation

What happens to a beam, tuplet, trill, ending or hairpin when the elements
underneath it change, and to a tie, which is not a span and answers to a rule of
its own. The arrangement matters more than the individual rules, because the
obvious alternative — a predicate per edit shape per span kind — is a grid that
grows in both directions.

## One funnel

Every edit to a line's elements goes through a small closed set of primitives:
insert one, replace one, delete one or a range, modify one in place. Each of
them, **before touching the elements**, hands the pending change to the line's
span registry, which asks every span what should happen to it, collects the
answers, and only then acts.

Each span is asked through **one hook**. A kind with a rule of its own states it
there as one answer over the four kinds of change; a kind with none takes the
default, under which a deletion that takes an endpoint removes the span and
every other change keeps it. Adding another primitive later means adding at most
one more kind of change — the two deletion primitives already share one — and
never another predicate on every span class.

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
beams are the common span, and hairpins are rare.

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
**last**. See [mutations.md](mutations.md). The tie rule is the one exception, and
[the last section](#ties-are-note-state-judged-when-the-edit-is-over) says why
undo stays correct without it.

## Replacement re-points; it does not delete

Replacing an element re-points any span anchored to it at the replacement — that
is what makes a surviving beam or tuplet follow an edited note instead of
vanishing. The projection reflects this: the old element is absent and the new one
sits at its position, so a span whose endpoint was the old element must read as
having that endpoint *become* the new one.

Reading it the other way — as a deletion — would silently remove every beam,
tuplet and ending whose endpoint is being edited, since each would see its
endpoint vanish with nothing to replace it.

## The sweep is skipped while replaying

A recorded step already carries the companion span changes captured when it was
first performed. Re-deriving them during replay would apply them a second time.

## A reshape is a removal plus an addition

Spans have no modify-in-place record; only add and remove exist, and there is
one of each for every kind. The record carries the span, and the registry makes
it as the span enters or leaves, so no kind declares records of its own and a
new kind needs none. A reshape is therefore expressed as a tracked removal of the
old span and a tracked addition of a copy at the new extent. This keeps the
vocabulary small: everything that is not a keep is either a removal or a
removal-plus-addition, never a third kind.

## What stays with the registry

**Every span lies in one line**, and sits in that line's registry and no other:
both of its endpoints are in that line, or an endpoint an edit has detached is in
no line at all. No span reaches across a line break, so nothing about a span
needs a second line's registry to answer it. The spans of a line are held
separately from the element list, and a span enters or leaves the registry only
through the span itself.

Each kind states its **displacement policy** as one value — what happens to the
same-kind spans already there when a new one arrives: they coexist, the new one
replaces what it overlaps, or it absorbs what it overlaps, optionally reaching
past its endpoints to absorb what sits adjacent. Adding a span reads that value
once, whatever the kind, so the registry never has to know which kinds merge and
which replace. "Same kind" is the same kind of span and, for a hairpin, the same
direction, so a crescendo never displaces a diminuendo. Beams absorb what they
overlap, hairpins also absorb what is adjacent, tuplets and trills replace, and
endings coexist.

Two things nonetheless stay with the registry rather than the spans, because
they are properties of the collection.

**Merging adjacent same-type hairpins** is not a property of either hairpin: no
span knows its siblings, so deciding whether two should become one cannot be
asked of either. The registry groups the reshape answers by kind and runs the
merge over them.

**The generic overlap merge** is not hairpin-specific — a beam's policy uses it
too, with adjacency absorption turned off, since two beam groups written back to
back are two deliberate groupings while two same-type hairpins are one hairpin.
Serving two kinds already, it has no reason to move onto either; each absorbing
policy only says how far past its endpoints the merge may reach.

See [hairpin-editing.md](hairpin-editing.md) for the hairpin-specific rules this
framework hosts but does not define.

## Ties are note state, judged when the edit is over

A tie is not a span. A note stores that it is tied to the next note, and nothing
more: the note the tie ends on is derived each time it is asked for, as the first
element after the anchor that is not a legal separator, continuing at the start
of the next line when the anchor's line runs out first. So a tie crosses a line
break with nothing stored in either line's registry, and no edit has an end to
keep up to date. A tie joins two notes of the plain note kind only; no grace
note, rest or other element can be its anchor or its end. Adding or removing one
is recorded by a record of its own, naming the anchor's line.

What an edit does have to keep is the **tie rule**: a tie stays only while the
element its walk reaches is a note at the same staff position that sounds the
same alteration. Inserting a same-pitch note between the two keeps the tie, which
then ends on the inserted note.

The rule does not run inside the funnel. It runs **once per edit, when the
outermost modification bracket closes**, before the batch is delivered and while
the bracket is still open, so each removal it makes is recorded in the same
batch. By then every change and every accidental restamp in the bracket has
landed, so every note has a sounding alteration to compare. It judges every tied
note in each line the edit touched and in the line before each of them, since a
tie from that line can end in the touched one. A tie the edit itself added is
judged like any other.

That timing makes its removals the one exception to "dependent state is recorded
first": they come **after** the changes that broke the ties. Reverse-order undo is
still correct, because a tie's record names only its anchor — the end is derived
— and undo restores the ties first, against the final state, where every anchor
the rule judged is still in its line. The changes are undone after that.

The rule does not run during replay, which applies the removals the batch
recorded, or while mutation tracking is suspended, which covers a file load: a
loaded tie is trusted.

A line draws the half of a tie that crosses into it from the line before, so an
edit near either edge of a line can change what the line beside it draws. The
model answers which lines an edit reaches that way; see
[mutations.md](mutations.md#scope).
