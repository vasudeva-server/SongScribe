# Hairpin Editing Rules

This document is the long-term home for the editor's hairpin rules: how
`MusicEditOperations.resolveHairpinAction(Hairpin.Kind)` decides whether a
selection can carry a new hairpin, extend an existing one, or is blocked or
ineligible. `docs/line-layout.md` covers hairpin *layout and rendering*
(endpoint geometry, stacking, spacing); this document covers the *editor*
decision that precedes layout.

## Decision tree

`resolveHairpinAction` is resolved once per menu item, with the hairpin kind
(`Hairpin.Kind.CRESCENDO` or `DIMINUENDO`) as an explicit input — the two menu
items can and do disagree about the same selection, because a crescendo
extension and a diminuendo addition are different gestures over different
spans.

```
resolveHairpinAction(kind)                    selection = [begin, end]
──────────────────────────────────────────────────────────────────────
  range == null? ────────────────────────yes──> INELIGIBLE
        │ no
        ▼
  ┌─ converge the union ────────────────────────────────────────────┐
  │   sameType = findSpans(kind.spanType(),                         │
  │       overlapping(spanBegin−REACH, spanEnd+REACH))              │
  │   isExtend = !sameType.isEmpty()                                │
  │   a sameType hairpin covers the ORIGINAL [begin,end]? → BLOCKED │
  │   widen [spanBegin, spanEnd] over every sameType hairpin        │
  │ repeat while the span grew                                      │
  └─────────────────────────────────────────────────────────────────┘
        ▼
  anchorComesFromSelection && !canAnchorHairpin(begin, spanEnd)? ─yes─> INELIGIBLE
  endComesFromSelection   && !canEndHairpin(end)?               ─yes─> INELIGIBLE
        │ no          └── an inherited endpoint stays put, unchecked
        ▼
  spansStructuralBoundary(spanBegin, spanEnd)? ──yes──> isExtend ? BLOCKED
        │ no                                                     : INELIGIBLE
        ▼
  hasSpan(kind.opposite(),
          overlappingBeyondEndpoint(spanBegin, spanEnd))? ──yes──> BLOCKED
        │ no        └── one shared endpoint is allowed; ≥2 shared is not
        ▼
  !isExtend && !Hairpin.hasEnoughColumns(…)? ────────────────yes─> INELIGIBLE
        │ no
        ▼
  isExtend ? EXTEND : CAN_ADD   over [spanBegin, spanEnd]
```

## `INELIGIBLE` vs `BLOCKED`

- **`INELIGIBLE`** — this span cannot carry a hairpin, whatever else is on the
  line. The menu item reads "Add …", disabled.
- **`BLOCKED`** — another hairpin is the obstacle. The menu item reads
  "Add …" (or "Extend …" for the boundary case reached mid-extension),
  disabled.

Every return in `resolveHairpinAction` follows that distinction, including the
structural-boundary check's ternary: when the union only reaches the boundary
because a same-type hairpin widened it, that hairpin is the obstacle
(`BLOCKED`); when the raw selection itself crosses the boundary with nothing
in play, the span itself is at fault (`INELIGIBLE`).

## Back-to-back hairpins

Two opposite-type hairpins may share exactly the one element where one ends
and the next begins — a crescendo ending on note 4 and a diminuendo starting
on note 4 is legal, ordinary engraving practice. A gap between them is
equally fine. More than one shared element is a collision and is `BLOCKED`.

`Span.overlappingBeyondEndpoint` is what permits this: it treats the single
shared boundary element as *not* an overlap, so `Line.hasSpan(kind.opposite(),
Span.overlappingBeyondEndpoint(...))` only fires when the opposite-type
hairpin's overlap goes beyond that one shared element. This also fixes a
pre-existing accident where a gap of exactly one element was blocked while a
gap of two or more already worked — both reused the same-type adjacency scan
before this predicate existed.

At layout time, the shared element's endpoints pull back from the notehead
center by `Hairpin.BACK_TO_BACK_PADDING_SS` on each side, so the two wedges'
tips do not touch — see `docs/line-layout.md`, Example 8.

## The rest rule and its asymmetry

A hairpin may **end** on a rest (`Hairpin.canEndAt`) but may never **anchor**
on one (`Hairpin.canAnchorAt`, unchanged). `Line.canEndHairpin` and
`Line.canAnchorHairpin` are thin instance conveniences that delegate to these
— the rule itself lives on `Hairpin`. This follows LilyPond
(`hairpin.cc:268-271`): a wedge closing on a rest reads naturally — the
diminuendo trails off into silence — but a wedge cannot meaningfully begin
from silence.

**At most one rest**, though. `Hairpin.canEndAt` accepts a rest only when the
nearest duration element before it is a pitched note, so a hairpin ends on
the rest that closes a run of notes and not on a second one after it — a
wedge running on across further rests has nothing left to slope over.
Selecting `note, rest, rest` is therefore `INELIGIBLE` rather than resolving
to a shorter span; the resolution never narrows a selection, it only reports
that the end the selection supplies cannot be an end. Non-durations are
skipped when looking back, so a grace note between two rests does not make
the second one count as the first of its run.

This is a rule about where a hairpin *stops*, not about what it may *cross*:
interior rests, however many, are unaffected.

`Hairpin.resolveEndIndex` reads the same predicate, so deleting a hairpin's
end element pulls the end in to the first surviving rest rather than the
last — the model can never hold a shape the menu would refuse to create.

## A hairpin is revalidated after every element change

A hairpin is re-checked against `Hairpin.outcomeFor` after **every** change to
the line's elements — insertion, replacement or deletion — not only
replacement. See `docs/span-invalidation.md` for the general framework this
sits on top of (the projected-element-list hook every span type answers, and
why decisions are made before the change lands); this section covers only the
rules `Hairpin.outcomeFor` applies.

An insertion or a replacement that leaves an endpoint invalid **removes** the
hairpin, silently, the way ties and tuplets go; undo restores it. Left
unchecked, `Line.setElement`'s re-pointing would quietly produce shapes the
menu would never allow — replace the rest a hairpin ends on with a grace note
and the hairpin ends on the grace note; replace an anchor note with a rest and
the hairpin is anchored on silence — and an insertion can do the same, for
example pushing a hairpin's end rest into second place in its run. The
hairpin is removed rather than shortened for these two shapes because neither
one deletes an element for the hairpin to pull back to: there is no reliable
"move the end there instead" to reach for.

A **deletion**, by contrast, **reshapes** the hairpin to its nearest valid
endpoints rather than removing it, because a deletion always leaves a shorter
run of surviving elements for the hairpin to pull back onto.

In every case the rules consulted are exactly `Hairpin.canAnchorAt`,
`Hairpin.canEndAt` and `Hairpin.hasEnoughColumns` — the same three the menu
itself reads when deciding whether a selection is eligible for a new hairpin
(the first two through `Line.canAnchorHairpin` / `Line.canEndHairpin`). The
model can therefore never hold a shape the menu would refuse to create:
whatever a fresh hairpin could not anchor on, end on, or slope across, a
surviving hairpin cannot be left with either.

The column rule is the one that two legal endpoints do not imply. A grace note
shares its host's column, so a hairpin anchored on a grace note and ending on
its host has two elements but a single column — both endpoints legal, nothing
to slope across. Deleting everything after the host of such a hairpin, or
replacing a two-note hairpin's anchor with a grace note, therefore **removes**
the hairpin rather than leaving that shape behind. An insertion can only ever
widen the span, so it never costs a hairpin its columns.

A trailing rest counts toward the two-column minimum
(`Hairpin.MIN_COLUMNS`): one pitched note followed by a rest is enough
columns for a new hairpin to slope across, even though the rest itself
carries no dynamic. `Hairpin.hasEnoughColumns` counts every pitched note in
`[begin, end]` plus one more if the element at `end` is a rest; interior
rests and grace notes never count.

## Why the union converges rather than scanning once

`Line.addHairpin` absorbs any same-type hairpin within `Line.
SPAN_ADJACENCY_REACH` of the span it is handed, and absorbing one hairpin can
bring a further one into reach. `resolveHairpinAction` widens
`[spanBegin, spanEnd]` over every same-type hairpin found, then rescans at the
wider span, repeating until a pass finds nothing new to absorb. This has to
converge before the endpoint, boundary, opposite-type and column-count checks
run, because all four judge the *resolved* span the model is about to build,
not the raw selection — a menu label promising a narrower hairpin than
`Line.addHairpin` will actually produce would be a lie. Each pass either
strictly widens the union or ends the loop, so convergence takes at most one
pass per same-type hairpin on the line.

## The resolution is deliberately uncached

`resolveHairpinAction` is recomputed on every call, once per menu item. Two
independent resolutions cost a handful of `O(spans)` scans per selection
change, which is free; a resolution cached on the selection would go stale
the moment a hairpin is added or undone without the selection moving,
leaving both menu items lying about what they will do.

## Corpus figures

Both configurations documented here are ordinary engraving practice, not
theoretical edge cases: back-to-back hairpins appear in 25 files of the ABC
corpus, and hairpins bounded by a trailing rest appear in two. See issue
#743.
