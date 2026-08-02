# Review — `375da061` "collapse span endpoint index resolution into one resolver"

**Reviewed:** 2026-08-02 **Branch:** `722-index-resolution` (base: `develop`)
**Plan under review:** [span-index-resolution.md](span-index-resolution.md) — step 1 of #722
**Status: RE-DISPOSITIONED 2026-08-02 against `plans/element-index-resolution.md`.**

The review was originally parked pending #725 (the `SpanIndex` snapshot). **#725 is
closed as not planned.** What landed instead was
[element-index-resolution.md](element-index-resolution.md): `Line.getElementIndex`
became O(1) via a lazily-built identity map invalidated at the `attach`/`detach`
chokepoint, plus the `SpanLookup` fast path and receiver-relative endpoint accessors.

Each finding below now carries a **Disposition** line. Two things changed wholesale:

- **Nine findings are closed** by that plan — 1, 5, 9, 13, 14, 15, 16, 17, 18, with 6
  closed only in its implementation half.
- **The linear scan four findings were premised on no longer exists.** Findings 7, 10,
  11 and 12 all argued from `getElementIndex` being `elements.indexOf(...)`. It is a
  hash lookup now, so their cost arguments are void and each needs re-deciding on
  readability grounds alone. Finding 7 in particular must **not** be applied as
  written — restoring that comment would install a false statement.

Findings 2 and 3 were each deferred for a reason that no longer holds; both are now
actionable. See their dispositions.

## Disposition summary

| # | Finding | Disposition |
| --- | --- | --- |
| 1 | `Span.overlaps` dead code | ✅ Closed — deleted |
| 2 | `LineInvariants` hand-rolls containment | ⬜ Open — deferral reason void, now actionable |
| 3 | `FormatMigrator` type-filter loops | ⬜ Open — blocker gone; legacy-path caveat stands |
| 4 | `LineEndingSupport` one-method class | ⬜ Open — question for the user |
| 5 | `getSpans()` ordering claim | ✅ Closed — reworded |
| 6 | Accessors ignore the receiver | ◐ Half — implementation fixed, interface Javadoc not |
| 7 | Deleted comment | ⚠️ **Do not apply as written** — premise false |
| 8 | Does the interface earn its place | ⬜ Open — new rationale, same answer: leave alone |
| 9 | Unfiltered query discards work | ✅ Closed — own loop + parity test |
| 10 | Merge re-resolves positions | ⬜ Open — readability only, cost argument void |
| 11 | `hasOverlap` lost its hoist | ⬜ Open — near-zero |
| 12 | MIDI loop lost its hoist | ⬜ Open — near-zero, nothing coming to absorb it |
| 13 | Guard half untested | ✅ Closed — `EndDetached` group |
| 14 | `exactly` can't tell and from or | ✅ Closed — third tie |
| 15 | Test name overclaims | ✅ Closed — deleted as redundant |
| 16 | Filtering step never filters | ✅ Closed — unaffected-first-ending case |
| 17 | Touching ties untested | ✅ Closed — shared-endpoint case |
| 18 | Merge predicates half-exercised | ✅ Closed — branch coverage groups |
| 19 | Three `Span` methods uncovered | ◐ Partial — `isAbove()` unverified |

Nine closed, two partial, seven open, one to be discarded. Of the seven open, none is
a correctness defect: findings 2, 3 and 4 are maintenance choices, 8 is settled as
leave-alone, and 10, 11 and 12 are readability observations whose performance
rationale no longer holds.

## Baseline at time of review

- `./scripts/test.sh unit` — green: 6830 passed, 1 skipped.
- Coverage figures below come from `./scripts/coverage.sh unit` against this commit.
- **Current tree:** 6859 passed, 1 skipped.

## Verified correct — no action needed

Two things worth recording as *checked*, so a later reviewer does not redo them:

- **The rewrite of `Line.mergeOverlappingSpans` (`Line.java:904`) is equivalent to
  the loop it replaced.** Derived independently three times. Every span
  satisfying the anchor predicate necessarily has an anchor at or before the
  original `anchorIdx`, so taking the minimum over only the matches and falling
  back to `anchorIdx` gives the same answer as the old running minimum seeded
  with `anchorIdx`; symmetrically for the end. The ordering constraint in its
  comment (both bounds resolved before the setters mutate the indices the
  predicates read) holds. The new span is not yet in `spans` during any of the
  three queries, in both the old and new versions.
- **Every replaced loop kept its original strictness.** All the containment
  queries (`findTieAt`, `findTupletAt`, `findBeamAt`, `isInHairpinRange`,
  `findEndingAt`, `isInsideAnyEnding`) map to the guarded `Span.containing`,
  matching their originals' explicit `>= 0` checks; all the overlap queries
  (`findBeamsOverlapping`, `findTupletsOverlapping`, `findTrillsOverlapping`,
  `hasTrillOverlapping`) map to the unguarded `Span.overlapping`, matching
  theirs. `findExactTie` maps to the unguarded `Span.exactly`, matching. The one
  intended behaviour change (`findEndingAt` / `isInsideAnyEnding` tightening) is
  the one the plan documents.
- **The five deleted methods really were dead.** `Line.isStartOfAnyBeam`,
  `Line.isEndOfAnyBeam`, `Line.findSpansAt`, `LineEndingSupport.isStartOfAnyEnding`
  and `isEndOfAnyEnding` had zero callers anywhere on `develop` outside their own
  definitions and their now-deleted tests. Deleting those tests cost no coverage.

---

## Reuse

### 1. `Span.overlaps(int, int)` is dead code
`src/main/java/songscribe/dom/Span.java:258`

The method answers "does this span's range touch the range from `begin` to
`end`?" This commit rewrote its body to go through the new predicate machinery.
But it had exactly three callers on `develop` — `Line.findTrillsOverlapping`,
`Line.hasTrillOverlapping`, and `MusicEditOperations`'s hairpin-scan helper — and
this same commit rewrote all three to call `Span.overlapping(...)` through
`SpanLookup` instead. Nothing calls it now, in production or tests.

Confirmed three ways: find-usages returns only the method's own body; `git grep`
against `develop` shows the three removed callers; JaCoCo marks the method never
executed.

Nothing breaks. It is a public method with no purpose that will read to a future
maintainer as if it is on a live path. **Delete it and its Javadoc.**

Carried into #725, where it matters extra: `overlaps` resolves positions through
the span's own parent line, bypassing `SpanLookup` — the one place that answers
where an endpoint is.

**Disposition: CLOSED.** `element-index-resolution.md` phase 3 deleted both
`Span.overlaps(int, int)` and `Span.matches(IndexPredicate)` with their Javadoc, and
rewrote every `SpanLookupTest` use to drive the predicates directly.

### 2. One place still hand-rolls the containment test
`src/main/java/songscribe/ui/renderer/LineInvariants.java:463` — the method that
decides whether a note should be highlighted because it is tied to the note
currently playing back

It spells the comparison out by hand: the tie is non-null, its anchor index is at
or before the element, and the element is at or before its end index. That is
exactly what `Span.containing(...)` was created to hold in one place.

Nothing misbehaves today — the tie in that field only ever comes from a lookup
that already rejects unresolvable endpoints. It is a maintenance point: this copy
will not inherit any future correction to the shared one.

**Caveat, and the reason not to act yet.** The obvious fix —
`playingTieSpan.matches(Span.containing(elementIndex))` — would add a production
caller to `Span.matches`, which also resolves through the span's own parent line
and so bypasses the seam #725 depends on. Leave this to #725, or leave it alone.

**Disposition: OPEN, and the deferral reason is void.** `Span.matches` was deleted
(finding 1), so the fix it warned against is no longer expressible. The remaining
route is a `SpanLookup` receiver —
`line.findFirstSpan(Tie.class, Span.containing(elementIndex))` or equivalent — which
resolves against the receiving line rather than the span's own parent. That is now
the *preferred* shape, not a bypass. Small and actionable.

### 3. Five loops in the legacy migration code hand-roll type filtering
`src/main/java/songscribe/io/FormatMigrator.java`, in the methods converting old
pixel measurements to staff-space and migrating line-level offsets (around lines
113, 122, 148, 221, 235)

Each walks every span on a line and checks its type with `instanceof` before
adjusting it — the pattern this commit extracted into `findSpans(Class)`.

Two caveats. This is the legacy `.mssw` read path, which `AGENTS.md` marks as
migration-only. And converting them today would make them *slower*, because of
finding 9. Sequence after that fix, or skip.

**Disposition: OPEN, and the blocker is gone.** Finding 9 is fixed —
`findSpans(Class)` now filters by type and resolves no positions — so converting
these loops no longer costs anything. The legacy-path caveat stands on its own: this
is `.mssw` migration code, so "skip" remains defensible. Decide on maintenance
grounds, not performance.

### 4. `LineEndingSupport` is now a class holding one static method
`src/main/java/songscribe/layout/LineEndingSupport.java`

It went from about a dozen helpers to one: the method reporting what effect
replacing an element would have on any ending in the line. Its class comment was
correctly updated, and it still has two production callers, so it is not dead.

Raised as a question, not a defect. The remaining method is not a plain index
lookup — it walks every ending and returns the first non-trivial effect — so it
does not obviously belong on `SpanLookup`. Leaving it as-is is defensible.

**Disposition: OPEN, unchanged.** Recorded again in `element-index-resolution.md`'s
out-of-scope section. Still a question for the user, not a defect.

---

## Quality

### 5. The interface promises an ordering the code does not provide
`src/main/java/songscribe/dom/SpanLookup.java:508`

`getSpans()` is documented as returning the spans "in line order." The old
comment on the equivalent `Line` method made no ordering claim.

The spans list is only ever appended to (`Line.java:281`) and is never sorted by
position. The real order is creation order — including whatever order an
undo/redo replay produces — not left-to-right across the line. A reader trusting
the comment could assume ties come back in staff order and be quietly wrong
whenever a tie was added out of that order, which ordinary editing does.

**Either sort by position, or reword to "in the order they were added."**

**Disposition: CLOSED.** Phase 3 took the reword — `SpanLookup.java:39` now reads
"The spans to query, in the order they were added."

### 6. The two index accessors ignore the object they are called on
`src/main/java/songscribe/dom/Line.java:1525` and `:1530`

The interface declares two methods whose job is to say where a span's anchor and
end sit *in this line*, and their documentation reads that way. `Line`'s
implementation forwards to the span, which resolves by asking its own anchor
element which line *it* belongs to. The receiver is never consulted.

Nothing can go wrong today — `Line` is the only implementor and the iterating
methods only hand a line its own spans. But nothing checks that the span belongs
to the receiver, so asking one line about another line's span silently returns
the other line's answer rather than failing.

#725 adds a second implementor whose accessors genuinely do depend on the
receiver, and which returns `-1` for a foreign span. **State the contract in the
interface Javadoc so both implementations are held to the same rule.**

**Disposition: HALF CLOSED.** Phase 7 fixed the implementation: `Line.anchorIndexOf`
/ `endIndexOf` now resolve through `getElementIndex` against the receiving line and
return -1 for an endpoint belonging to another line, pinned by
`SpanLookupTest.CrossLineEndpoints`.

The Javadoc half is **still open**. `SpanLookup.java:42-46` says only "The anchor
element's position in the line, or -1 when unresolvable" — it does not state that the
position is relative to *the receiver*, nor that a foreign endpoint yields -1. #725
is closed so there is no second implementor coming, but cross-line ties (#493) make
these two methods return directional sentinels, so the contract needs stating before
that work starts.

### 7. A true and newly-relevant comment was deleted
`src/main/java/songscribe/ui/MusicEditOperations.java:676`, in the loop that
extends a hairpin over a selection

The loop reads each hairpin's anchor and end index into locals before using them
twice. The line explaining why — that each accessor scans the line looking for
the element, so read them once — was removed. The plan called it obsolete.

It is not obsolete. The accessor still performs a linear scan; it bottoms out in
`elements.indexOf(element)` at `Line.java:689`. The two locals it justified were
deliberately kept, so the code now does something deliberate with no stated
reason, and the next reader may "simplify" it. **Restore it.**

**Disposition: DO NOT APPLY AS WRITTEN.** The premise is now false. `getElementIndex`
is a hash lookup, not `elements.indexOf(...)`, so "each accessor scans the line
looking for the element" would be a false statement if restored — the exact rot this
review exists to catch.

The underlying observation survives in weaker form: two locals still sit there with
no stated reason. But hoisting a hash lookup out of a loop needs no justification, so
the honest resolution is probably to leave the code alone and write nothing, or to
inline the locals. Re-decide on readability; the performance argument is void.

### 8. Whether the interface earns its place — no action
One reviewer argued that since `Line` is the only implementor and the three
abstract accessors do not really vary, the interface is a fiction and the queries
should be plain methods on `Line`. Sound in isolation, but answered by #725,
which adds `SpanIndex implements SpanLookup` and is the whole reason the
accessors are extension points. **Leave alone.**

**Disposition: OPEN — the answer changed, the conclusion did not.** #725 is closed,
so no second implementor is coming and the original justification is gone. The
interface still earns its place for a different reason: it is where the endpoint-
resolution contract is stated once for every query, which is what let phase 7 change
every span query by editing two methods. Cross-line ties (#493) rely on exactly that.
**Still leave alone.**

---

## Efficiency

The plan states performance is not the goal, which is not a reason to reject the
commit. These are regressions the refactor *introduced*, which is a different
thing from declining to optimise.

The measurement that underlies all of them: `Span.getAnchorElementIndex()` /
`getEndElementIndex()` (`Span.java:243`, `:251`) resolve via `indexInLine` →
`Line.getElementIndex` → `elements.indexOf(element)` (`Line.java:689`) — a linear
scan of the line's element list. There is no cached index on `Span` or
`StaffElement`.

> **This whole section's premise is void.** `Line.getElementIndex` is now a hash
> lookup against a lazily-built identity map, and the measured cost of the entire
> resolution path across a real editing session is ~80,100 operations (78,358
> lookups, 68 map rebuilds, lines averaging ~25 elements). Findings 10, 11 and 12
> below are regressions measured in linear scans that no longer happen. Read them as
> readability observations; none is worth acting on for speed.

### 9. The "every span of this type" query does work it immediately discards
`src/main/java/songscribe/dom/SpanLookup.java:553`

The no-predicate overload is implemented by calling the predicate-taking version
with a predicate that always returns `true`. Java evaluates arguments eagerly, so
both endpoint positions are resolved for every span of the requested type and
then thrown away. Before this commit the unfiltered query was a plain type check
that touched no positions at all.

A line with three endings now costs six linear scans on every call to "find the
endings," where it previously cost none — and this runs on the repaint path
(`EndingRenderer.java:83`, plus the tie, beam and tuplet renderers), so on every
scroll, resize and cursor move.

Honest magnitude: on a line of fifty elements this is a few hundred extra
comparisons per line per repaint. Very unlikely to be visible; it is waste rather
than a slowdown users would notice.

**Fix:** give the unfiltered version its own loop that filters by type and
resolves nothing — about eight lines. Cost: the file's comment claiming only two
methods iterate becomes three.

**Mostly subsumed by #725** on the render and layout paths, which get O(1) array
reads. It stays a live cost on the `Line` path the editing code keeps using.

**Disposition: CLOSED.** Phase 3 gave `findSpans(Class)` its own loop that filters by
type and resolves no endpoint positions, preserving `getSpans()` order for
`findFirstSpan`. The interface Javadoc was updated from two iterating methods to
three. Phase 4 added a parity case asserting `findSpans(type)` equals
`findSpans(type, alwaysTrue)` element-for-element, in order, including a
half-detached span — the case where an endpoint-resolving path could diverge from one
that resolves nothing.

### 10. Merging a new span resolves the same positions three to four times over
`src/main/java/songscribe/dom/Line.java:918-940`

Runs whenever the user adds a beam, crescendo or diminuendo. Its job is to expand
the new span so it swallows same-type spans it touches, then delete the swallowed
ones.

The old version made one pass, reading each same-type span's two positions once
into locals and reusing them for every comparison. The new version makes three
separate queries, each independently re-resolving both positions of every
same-type span — and the first two additionally re-resolve, in their
`.mapToInt(...)`, a position just computed and discarded inside the query. Two
position lookups per same-type span became roughly six, each a linear scan.

The result is identical and on a line with a handful of beams this is invisible.
It is, though, a threefold increase in exactly the work #722 set out to reduce,
sitting in the one piece of real logic the commit rewrote.

**Fix:** resolve each span's positions once into a small local structure and reuse
them — which pulls the method back toward a loop and away from the predicate
style. Genuine trade-off; needs a decision.

**Not covered by #725**, whose snapshot is for read-only passes and explicitly
excludes the editing paths.

**Disposition: OPEN, but the cost argument is void.** The two direct
`elements.indexOf(...)` calls at the top of the method were switched to
`getElementIndex`, so every resolution here is a hash get. "Roughly six linear scans
per same-type span" is now roughly six hash gets, on a line with a handful of beams.

The structural observation stands — three queries each re-resolve positions, and two
re-resolve in their `.mapToInt(...)` a position just discarded inside the query. That
is a readability and duplication argument now, and the fix still trades the predicate
style back for a loop. **Genuine trade-off; needs a decision — but not a performance
one.**

### 11. `hasOverlap` lost its hoisted lookup
`src/main/java/songscribe/ui/MusicEditOperations.java:866` — checks whether a
selection collides with an existing ending

It used to compute the line's endings once before the loop and check each index
against that short list. It now calls a line-wide lookup inside the loop.

Smaller than it sounds. The expensive part — resolving positions — was already
repeated per iteration in the old code, so that cost is unchanged. What got worse
is that each iteration now walks the line's *entire* span list to find the
endings instead of a pre-filtered list. Those are cheap type checks over small
ranges. Reported because the hoisting was deliberate and its removal was not
reasoned about, not because a difference is expected.

**Not covered by #725** — editing path.

**Disposition: OPEN, near-zero.** The lost hoist now costs a walk of the line's span
list (5-10 spans, cheap `instanceof` checks) per iteration instead of a pre-filtered
list. Reported originally because the hoisting was deliberate and its removal was not
reasoned about — that remains the only reason to act.

### 12. The MIDI note loop lost the same hoist
`src/main/java/songscribe/midi/MidiSequenceBuilder.java:205`

Same shape and same near-zero magnitude as finding 11.

**Covered by #725**, which lists `MidiSequenceBuilder:209` in its call-site
inventory.

**Disposition: OPEN.** #725 is closed, so nothing is coming to absorb this. Same
near-zero magnitude as finding 11 and the same sole rationale.

---

## Test quality

Coverage of the new interface itself is complete — every default method on
`SpanLookup` is executed. The findings below are all tests that pass whether or
not the code is right.

### 13. Half of the unresolvable-endpoint guard is never tested
`src/test/java/songscribe/dom/SpanLookupTest.java`, the `GuardBehavior` group

The guard says a span covers an element only if *both* endpoints resolve to real
positions. The tests detach the *anchor* element and check a half-detached ending
stops being reported. They never detach the *end* while leaving the anchor valid.

If someone decided the end check was redundant and deleted it, every test would
still pass — while a span whose end had been detached would start being reported
as covering elements past a position that no longer means anything. JaCoCo
confirms: one of the predicate's eight branches is never taken.

**Fix:** mirror the existing case, detaching the end element instead.

**Disposition: CLOSED.** Phase 4 refactored `GuardBehavior` to share a
`buildHalfDetachedSpans` helper between the existing `AnchorDetached` tests and a new
`EndDetached` mirror group covering `Span.containing`'s `endIndex` guard.

### 14. The exact-range predicate cannot tell "and" from "or"
`src/test/java/songscribe/dom/SpanLookupTest.java`, the `ExactlyPredicate` group

`Span.exactly` matches a span whose anchor *and* end are both exactly the queried
positions. It exists specifically to pick the right tie out of a chain of ties
sharing a note — the reason `findExactTie` exists rather than just `findTieAt`.

The tests use two ties differing in both endpoints, so in the negative case both
halves of the conjunction are false at once. If the `&&` became `||`, every test
would still pass — and a tie sharing a start with the queried range but ending
elsewhere would be wrongly accepted, which is the exact confusion the method
prevents. JaCoCo: one of four branches never taken.

**Fix:** add a third tie matching one endpoint but not the other, and assert it
does not match.

**Disposition: CLOSED.** Phase 4 added a third-tie case to `ExactlyPredicate` matching
one endpoint but not the other, so `&&` is no longer indistinguishable from `||`.

### 15. A test's name claims something the test does not check
`src/test/java/songscribe/dom/SpanLookupTest.java:316`,
`testTwoOverlappingTrillsReturnTrueWithoutMaterializingAList`

It adds two overlapping trills and asserts the "is there a trill here?" query
returns true. The name promises it also proves the query stops at the first match
without building a list; the test's own comment concedes this is not checked. If
someone reimplemented the query as "build the full list, then ask if it is
empty," the test would still pass despite the method's Javadoc promising
otherwise. The boolean half is already covered more thoroughly in `LineTrillTest`.

**Fix:** rename to describe what it actually checks, or drop it as redundant.

**Disposition: CLOSED.** Phase 4 took the second option — the test was deleted as
redundant, the boolean half being covered more thoroughly in `LineTrillTest`.

### 16. A filtering step is never required to filter anything
`src/test/java/songscribe/layout/LineEndingSupportTest.java:265`,
`testTwoEndingsAffectedReturnsFirstNonNoneEffect`

The production method walks every ending, works out what replacing an element
would do to each, discards the "no effect" answers, and returns the first real
one. The test builds two endings where the replacement invalidates *both*, then
checks it gets the first one's answer.

Because neither answer is "no effect", the discarding step is never exercised.
Delete that step and the test still passes — but in production, a replacement
leaving the first ending alone and invalidating the second would then return "no
effect", and the second ending would silently survive a change that should have
invalidated it.

**Fix:** add a case where the first ending is genuinely unaffected.

**Disposition: CLOSED.** Phase 6 added
`testFirstEndingUnaffectedReturnsSecondEndingEffect` — two disjoint endings with only
the second affected, exercising the None-discard step.

### 17. Two ties that touch are never tested against "is it the same tie?"
`src/test/java/songscribe/dom/LineTieTest.java`, the `SameTieAt` group — a file
this commit does not touch

The query returns true only when one and the same tie object covers both given
notes. The existing tests use two ties with a gap between them, never two sharing
a note — the configuration that matters, because ties, unlike beams, deliberately
never merge at a shared endpoint.

If the method were loosened to "is there a tie spanning between these two notes",
it would wrongly say yes for two chained ties, and no current test would catch it.

**Fix:** add a case with ties covering notes 0–1 and 1–2 and assert the query says
no for notes 0 and 2.

**Disposition: CLOSED.** Phase 6 added
`testAdjacentTiesSharingAnEndpointReturnsFalse` — ties [0,1] and [1,2] sharing note 1,
asserting `sameTieAt(0, 2)` is false.

### 18. The two new merge predicates are only half-exercised
`src/main/java/songscribe/dom/Line.java:918` and `:923`

Each of the two absorption tests has one of its four branches never taken. The
absorption boundaries themselves are well protected by existing beam and hairpin
merge tests, so no real bug is likely hiding — but the rewritten expressions are
not fully pinned. One or two added cases would lock the rewrite down.

**Disposition: CLOSED.** Phase 5 added a `MergePredicateBranchCoverage` group to both
`LineBeamTest` and `LineHairpinMergeTest`, four tests each — one per half-failure case
of the two conjunctions, with `reach` 0 for beams and `SPAN_ADJACENCY_REACH` for
hairpins.

### 19. Three methods on `Span` are never executed by any unit test
`isAbove()`, `getContentWidthSs()`, `getContentWidthPx()`

Predate this commit and are unrelated to it. Recorded because the file is in the
review.

**Disposition: PARTIALLY CLOSED.** Phase 6 added a `GetContentWidthPx` group to
`LineTieTest` exercising `getContentWidthPx()` as a `ssToPx(getContentWidthSs())`
wrapper with a non-default pixels-per-staff-space. `getContentWidthSs()` is exercised
at `StructuralTierStackingTest:735`. **`isAbove()` was not verified and may still be
uncovered** — re-check with `./scripts/coverage.sh unit` before acting.

---

## Reported and not recommended

- **The paste-reconciliation loop** (`src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java:180`)
  technically matches the "walks spans and compares positions" shape this refactor
  targeted. But it makes one pass over five span types with different side effects
  per type, and its condition is none of the three shared predicates. Converting
  it would mean adding a fourth predicate and running five passes instead of one.
- **Dissolving `SpanLookup` into plain methods on `Line`** — see finding 8.
