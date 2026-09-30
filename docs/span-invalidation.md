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

A removal carries the **reason** the span's rule gives — an endpoint deleted, a
note that can no longer be beamed, a tuplet whose interior changed — and the
removal is reported with that reason. Every removal the user did not directly
request is reported: those the funnel decides, the spans a displacement
replaces, and those the rules at the close of the edit make. A span a merge
absorbs is not removed in any sense the user sees — the merged span covers it —
so it goes unreported. A
removal the user asked for — deleting a selected span, deleting every element a
span covers, toggling a beam or a tie off — is not.

That second case is decided by the registry, not by any span's rule. A deletion
whose range holds a span's anchor, its end and everything between takes the span
with it, and the span is never asked: whether the user deleted the whole span is
a fact about the deletion, the same for every kind. No edit asks the user to consent to a removal before it happens;
the edit goes ahead, the report tells the user what else changed, and undo
reverses both. See [change-report.md](change-report.md).

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

That frugality is worth having because **only hairpins and endings read the
projection at all.** Every other span kind answers from the edit's own terms, and
the projected list is built only when a hairpin or an ending asks for it. Copying
the element list and building an index on every edit would do work that almost
every edit throws away: beams are the common span, and hairpins and endings are
both rare.

The view reads the live line, so it is valid only until the elements are mutated
and nothing may retain one past that point. That is exactly the window every
caller uses it in.

## Three answers

A span answers keep, remove, or reshape.

Keep carries no data, and there is exactly one of it — which is why it is an
enum constant rather than an empty record, since a record's constructor cannot be
narrowed and any caller could mint another. Remove carries the reason the report
shows, so it is a value, one for each reason. Reshape's
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
**last**. See [mutations.md](mutations.md). The rules that run when the edit is
over — the tie rule and the ending's section rule below among them — are the
exceptions, and [the section on ties](#ties-are-note-state-judged-when-the-edit-is-over)
says why undo stays correct without it.

## Replacement re-points; it does not delete

Replacing an element re-points any span anchored to it at the replacement — that
is what makes a surviving beam or tuplet follow an edited note instead of
vanishing. The projection reflects this: the old element is absent and the new one
sits at its position, so a span whose endpoint was the old element must read as
having that endpoint *become* the new one.

Reading it the other way — as a deletion — would remove every beam, tuplet and
ending whose endpoint is being edited, since each would see its endpoint vanish
with nothing to replace it.

An edit that leaves a span ill formed never makes a second change to keep it: a
replaced bar that breaks an ending removes the ending, with its reason, rather
than rewriting another of its bars to suit.

The same holds for a hairpin's structural boundary. An insertion or a replacement
that puts a repeat, or a bar other than a single barline, inside a hairpin removes
the hairpin and reports it, alongside the endpoint checks. A deletion cannot bring
a bar into a range, so it never triggers this removal.

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
both of its endpoints are in that line. No span reaches across a line break, so
nothing about a span needs a second line's registry to answer it. An ending's
validity is the one thing that reads further: the ending sits in one line's
registry, but whether its split closes an open repeat section depends on bars
any number of lines back — see [below](#an-endings-section-is-judged-when-the-edit-is-over).
The spans of a line are held separately from the element list, and a span enters
or leaves the registry only through the span itself.

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

**Two endings on a line do not overlap**, and that is the only rule between
them: there is no separation rule, and two endings may sit back to back, each
still answering to the section rule on its own. The overlap is refused where an ending is created, from a selection or from a file;
no edit can make two existing endings overlap, so the funnel judges no pair of
endings.

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
batch, and reports it. By then every change and every accidental restamp in the
bracket has landed, so every note has a sounding alteration to compare. It judges
every tied note in each line the edit touched and in the line before each of
them, since a tie from that line can end in the touched one. A tie the edit
itself added is judged like any other.

That timing makes its removals an exception to "dependent state is recorded
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

## An ending's section is judged when the edit is over

An ending's split must close an open repeat section: walking back from the split,
a repeated section must be open, however many lines back it opened. That is the
**section rule**, and it is the one part of an ending's validity that no
projection holds. The funnel judges one line's projected elements, and the
section rule reads the song across lines — a bar added, removed or retyped on an
earlier line, or a whole line inserted or deleted, which goes through no funnel
at all, can close or open the section of an ending lines later.

So the funnel judges only what an ending's own line decides — its anchor, its
split, its end, its brackets — and the section rule runs where the tie rule does,
**once per edit, when the outermost modification bracket closes**, under the same
conditions. It judges every ending in the song rather than the lines the edit
touched, because no smaller set of lines holds every ending an edit can break,
and it removes and reports, with its reason, each ending whose split no longer
closes an open section.

Undo stays correct for the tie rule's reason. The rule judges the final state, so
both endpoints of every ending it removes are present in that state. Reverse-order
undo restores those endings first, against that same final state, and only then
undoes the changes that broke them.
