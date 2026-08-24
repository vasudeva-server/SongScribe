# Hairpin Editing

Whether a selection can carry a new hairpin, extend an existing one, or neither.
[line-layout.md](line-layout.md) covers where a hairpin's tips end up once it
exists; this is the decision that precedes layout, and
[span-invalidation.md](span-invalidation.md) is the framework that re-asks it
after every element change.

## The resolution runs per menu item

Crescendo and diminuendo are resolved separately, with the kind as an input,
because the two menu items can and do disagree about the same selection — a
crescendo extension and a diminuendo addition are different gestures over
different stretches of music.

The resolution is recomputed on every ask and deliberately not cached. Two
resolutions cost a few scans per selection change, which is free; a resolution
cached against the selection would go stale the moment a hairpin is added or
undone without the selection moving, leaving both menu items lying about what they
would do.

## The span converges before anything is judged

Adding a hairpin absorbs any same-type hairpin within reach of it, and absorbing
one can bring a further one into reach. So the span widens over every same-type
hairpin found, then rescans at the wider span, repeating until a pass finds
nothing new.

That has to converge **before** the endpoint, boundary, opposite-type and
column-count checks run, because all four judge the span the model is actually
about to build. A menu label promising a narrower hairpin than the commit would
produce would be a lie. Each pass either strictly widens the span or ends the
loop, so it takes at most one pass per same-type hairpin on the line.

## Ineligible versus blocked

Two different refusals, and every return distinguishes them:

- **Ineligible** — this stretch of music cannot carry a hairpin, whatever else is
  on the line.
- **Blocked** — another hairpin is the obstacle.

The structural-boundary check shows why the distinction is worth keeping: when the
span only reaches the boundary because a same-type hairpin widened it, *that
hairpin* is the obstacle; when the raw selection crosses the boundary on its own,
the selection is at fault.

An endpoint inherited from an absorbed hairpin rather than supplied by the
selection is left unchecked — it is already where it was, and re-judging it would
refuse an extension because of a position the user is not proposing.

## Back-to-back hairpins

Two opposite-type hairpins may share **exactly one** element, where one ends and
the next begins. A crescendo ending on a note and a diminuendo starting on it is
ordinary engraving practice, and a gap between them is equally fine. More than one
shared element is a collision.

The predicate that permits this treats a single shared boundary element as *not*
an overlap, so the opposite-type check only fires when the overlap goes beyond
that one element.

## A dynamic may sit on a bound, never inside

A text dynamic may sit on **either bound** of a hairpin — its first or its last
element — or outside it entirely. It may never sit **strictly inside**. So a
dynamic before a crescendo, after a diminuendo, or between two back-to-back
hairpins are all legal, and only the wedge's interior is off limits.

Two things enforce it, one live and one on commit: the dynamic commands are
unavailable for a selection strictly inside a hairpin, and adding or extending a
hairpin over an existing dynamic strips only those in the strict interior, leaving
one that has become a bound alone.

**The rule is bound-wide rather than shared-element-only, and that is what makes
it need no cleanup machinery.** Deleting one of two back-to-back hairpins that
shared a dynamic-bearing element leaves the survivor with that element as its own
bound — which is itself a legal shape. Nothing is ever left stranded that the menu
would refuse to create, so there is no sweep to hook into deletion.

## Ending on a rest, but never starting from one

A hairpin may **end** on a rest and may never **anchor** on one: a wedge closing
on a rest reads naturally, trailing off into silence, but a wedge cannot
meaningfully begin from silence.

**At most one rest, though.** A rest is accepted as an end only when the nearest
duration element before it is a pitched note, so a hairpin ends on the rest that
closes a run of notes and not on a second one after it — a wedge running on across
further rests has nothing left to slope over. Selecting a note and two rests is
therefore refused outright rather than resolving to a shorter span: the resolution
never narrows a selection, it only reports that the end the selection supplies
cannot be an end. Non-durations are skipped when looking back, so a grace note
between two rests does not make the second one count as the first of its run.

This is a rule about where a hairpin *stops*, not about what it may *cross*.
Interior rests, however many, are unaffected.

## The model can never hold a shape the menu would refuse

A hairpin is re-checked after **every** change to the line's elements — insertion,
replacement and deletion alike, not only replacement — and the rules consulted are
exactly the three the menu itself reads. Whatever a fresh hairpin could not anchor
on, end on, or slope across, a surviving hairpin cannot be left with either.

The response differs by what the edit made possible:

- An **insertion or a replacement** that leaves an endpoint invalid **removes**
  the hairpin, silently, the way ties and tuplets go; undo restores it. Neither
  deletes an element for the hairpin to pull back to, so there is no reliable
  "move the end there instead" to reach for. Left unchecked, re-pointing would
  quietly produce shapes the menu would never allow — a hairpin ending on a grace
  note, or anchored on silence.
- A **deletion** **reshapes** the hairpin to its nearest valid endpoints instead,
  because a deletion always leaves a shorter run of surviving elements to pull back
  onto.

**Two legal endpoints do not imply a legal hairpin.** A grace note shares its
host's column, so a hairpin anchored on a grace note and ending on its host has
two elements but a single column — both endpoints legal, nothing to slope across.
That shape is removed rather than kept. An insertion can only widen a span, so it
never costs a hairpin its columns.

A trailing rest counts toward the two-column minimum: one pitched note followed by
a rest is enough to slope across, even though the rest carries no dynamic of its
own. Interior rests and grace notes never count.

## Corpus figures

Both configurations documented here are ordinary engraving practice rather than
theoretical edge cases: back-to-back hairpins appear in twenty-five files of the
ABC corpus, and hairpins bounded by a trailing rest in two.
