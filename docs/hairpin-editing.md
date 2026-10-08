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
note-count checks run, because all four judge the span the model is actually
about to build. A menu label promising a narrower hairpin than the commit would
produce would be a lie. Each pass either strictly widens the span or ends the
loop, so it takes at most one pass per same-type hairpin on the line.

The convergence and every check belong to the hairpin's own rule. The menu, the
paste and the load all ask that rule and act on its answer; the menu keeps no
rule of its own, so the three routes cannot disagree about the same range.

## Ineligible versus blocked

Two different refusals, and the rule's refusal says which:

- **Ineligible** — the range itself is at fault: this stretch of music cannot
  carry a hairpin, whatever else is on the line.
- **Blocked** — another hairpin is the obstacle.

The menu maps the refusal directly to its two states. A repeat or a non-single
barline inside the selection makes it ineligible. Absorption never brings one in:
a hairpin absorbs only a hairpin that overlaps or touches it, and no hairpin holds
such a bar.

An endpoint inherited from an absorbed hairpin already meets the endpoint rules,
so a refused endpoint is always one the selection supplied.

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

## Never starting or ending on a rest

A hairpin never **starts** on a rest and never **ends** on one: a wedge closing on
silence or beginning from it has no note to slope to or from. The anchor is a
pitched note, or a grace note whose host is one; the end is a pitched note.

This is a rule about where a hairpin *stops* and *starts*, not about what it may
*cross*. Rests inside a hairpin, however many, are unaffected.

Two pitched notes are the minimum a hairpin needs to slope across, and a rest,
like a grace note beside its host, never counts toward them.

## Why an edit removes one hairpin and reshapes another

The rules a hairpin keeps are the invariants on `Hairpin`, and how each element
change answers them is on `Hairpin.outcomeFor`. What the contracts do not say is
why the answer differs by the kind of edit.

An **insertion or a replacement** deletes no element for the hairpin to pull back
to, so there is no reliable "move the end there instead" to reach for: a hairpin
it leaves breaking a rule is removed and reported, and undo restores it. Left
unchecked, re-pointing would quietly produce shapes the menu would never allow —
a hairpin ending on a grace note, or anchored on silence.

A **deletion** always leaves a shorter run of surviving elements to pull back
onto, so it reshapes the hairpin to its nearest valid endpoints instead.

**Two legal endpoints do not imply a legal hairpin.** A grace note shares its
host's column, so a hairpin anchored on a grace note and ending on its host has
two elements but a single column — both endpoints legal, nothing to slope across.
That shape is removed rather than kept.

## Corpus figures

Back-to-back hairpins are ordinary engraving practice rather than a theoretical
edge case: they appear in twenty-five files of the ABC corpus.
