# Cross-Line Ties (issue #493)
**Type:** Master plan **Created:** 2026-08-02 **Branch:** 493 (base: `develop`) **Status:** Pending

* * *
## Goal
Support a tie whose two notes sit in different lines: the anchor in line A, the end in line B. The toggle offers the tie when the anchor is line A's last note and the end is line B's first, but "last note" is not "last element" — the legal-separator rule in phase 4a lets a barline, repeat or breath mark sit after the anchor or before the end without breaking the tie, and a line closed by a barline is the ordinary case, so the toggle looks past those separators at creation time as well. Nothing pins either endpoint's index afterwards either; do not write code that assumes the end is at index 0 or the anchor is last.

The tie is **one** `Tie` **object present in both lines'** `spans` **lists** — half on each. Each line resolves the endpoint it owns to a real position and the far endpoint to a bound meaning "off this line's edge", so each line draws its own half running to the staff edge.

```
  line A:  … ● ● ●─────►        anchorIndexOf = At(7),      endIndexOf = AfterLine
  line B:  ◄─────● ● …          anchorIndexOf = BeforeLine, endIndexOf = At(0)
```

* * *
## Contract every phase is written against

### Parentage: derived from the endpoints, present in both lists
**Decided — do not re-open.** A `Span` is **never attached to a line at all.**

```
span.isIn(L)  ==  anchorElement.getParentLine() == L || endElement.getParentLine() == L
```

- `Line.appendChild` / `removeChild` **stop calling** `attach` / `detach`. They only add to and remove from `spans`.
- A `Span`'s inherited `LineElement.parentLine` therefore stays `null` for its whole life. `Span` gets **no** `getParentLine()` override and **no** `lines()` method — there is no derived "the span's line" to return.
- `Span.isIn(Line)` is **two reference comparisons and nothing else.** It allocates nothing. `SelectionCoordinator.isOnLine` runs after every mutation and its Javadoc records that it deliberately replaced an O(n) scan; that reasoning still holds.
- The span is **physically present in both lines'** `spans` **lists**, so every existing read of a line's span list keeps working unchanged.
- `appendChild` adds the span to `this` line **and** to the other line of the pair when there is one. `removeChild` removes it from both. They remain the only writers of `spans`.

Adding to `this` line unconditionally — rather than only where an endpoint lives — preserves today's behavior exactly when both endpoints are detached, so nothing regresses. The invariant is therefore one-directional:

> `span.isIn(L)` ⟹ `L.spans` contains `span`

`StaffElement` is untouched: it keeps the stored `parentLine` field and the existing two-way invariant. Only `StaffElement` ever reaches `attach`/`detach`, so their `elementIndexMap = null` invalidation no longer needs its `instanceof StaffElement` guard to be *reached* by spans at all.

**Why not a stored set of parent lines.** Three structures would have to agree (list A, list B, the set); undo would have to restore all three; and the set would hold a strong reference to a `Line` that `Song.removeLine` had already dropped from the song, with no invariant able to catch it.

**Why not a single owning line with the far line deriving its half.** Line B's `spans` list would not contain the tie, so every sweep in line B would silently miss it — the deletion sweeps (`Line.java:609, 655`), the insertion sweep (`:394`), the replacement sweep (`:450`) — and `LayoutEngine.calculateTies` iterating `line.findTies()` would draw nothing for line B. The failure mode is silence at a dozen read sites.

### Endpoint bounds: a type, not a sentinel int
**Decided — do not re-open.** A resolved endpoint position is a sealed type, not an `int` with magic values:

```java
sealed interface SpanBound permits SpanBound.At, … {
    record At(int index) implements SpanBound { }
    // the three valueless cases are singletons — they allocate nothing
    SpanBound BEFORE_LINE = …;   // endpoint is in an earlier line; this half enters from the left edge
    SpanBound AFTER_LINE  = …;   // endpoint is in a later line;   this half exits through the right edge
    SpanBound ABSENT      = …;   // no position: unset, in no line, or in a line no longer in the song
}
```

**Why not `-2` / `-3` sentinel ints.** They invert under the comparisons the predicates already use. `overlapping` is `anchorIndex <= end && endIndex >= begin`; with `END_OF_LINE = -3`, `-3 >= 0` is false, so a line-A half would report as overlapping **nothing**. Worse, `mergeOverlappingSpans` reduces with `.mapToInt(this::anchorIndexOf).min()` (`Line.java:997`) and then calls `elements.get(mergedAnchorIdx)` (`:1007`) — a sentinel wins the `min()` and the `get()` throws. A hand-written guard has to catch three separate lines; the type makes `mapToInt` fail to compile, so the compiler finds all three.

**The query side stays `int`.** `containing(int elementIndex)`, `overlapping(int begin, int end)` and `exactly(int anchorIndex, int endIndex)` keep `int` parameters — a *query* is always a real in-line position. Only a *span's resolved endpoint* can be off-line. `Span.IndexPredicate.test` takes two `SpanBound`s. This asymmetry is why "a cross-line half must never match a query for a span genuinely anchored at this line's first or last element" falls out of the type instead of being a rule someone has to remember.

**Reading the bounds in a predicate.** `BEFORE_LINE` reads as "before every index", `AFTER_LINE` as "after every index", `ABSENT` as "no position at all". A cross-line half must report **true** for every element it passes over, not only its attached endpoint.

**Arithmetic is invalid on anything but `At`.** `Line.java:997, 1002, 1007` reduce and index with these values. Every such expression must narrow to `At` first.

### Direction comes from real line order
`Line.anchorIndexOf` / `endIndexOf` resolve a far endpoint's direction by asking the song where the two lines actually sit, via `line.getSong()`. **Not** by inferring "an anchor is the earlier endpoint, so it must be behind us."

**Why not the inference.** `Song.removeLine` leaves the deleted line's elements attached to it (see below), so a far endpoint can point at a `Line` that is no longer in `song.lines` and has no earlier-or-later answer. That case resolves to `ABSENT`: the half stops drawing rather than drawing off an edge toward nothing, and phase 5's sweep removes it moments later.

`Line.getElementIndex` **does not change.** It answers "where is this element in this line", and `elementIndexMap` indexes only this line's own `elements`. A far endpoint is not in that map and must never be added to it.

### `Span.getAnchorElementIndex()` / `getEndElementIndex()` are not touched
These resolve through **each endpoint's own line** (`Span.indexInLine`, `:217`). Roughly **106 call sites** across layout, MIDI, MusicXML, rendering and stacking depend on that. Converting them is emphatically out of scope.

The consequence is phase 6's whole reason for existing: any site that takes **both** values from a **`Tie`** and compares them gets two indices from two different lines. Only ties can be cross-line, so only tie-reading sites are affected.

### The mutation inventory
Every way a cross-line tie can be affected, and which phase owns it. Rows marked *pre-existing* are broken for same-line ties today; fixing them is in scope because a cross-line tie makes the breakage visible and unavoidable.

| Mutation | Today | Phase |
| --- | --- | --- |
| Delete an endpoint (either line) | `Span.isInvalidatedBy`, swept per-line; removes from one list only | 3 |
| Range-delete containing an endpoint | Same | 3 |
| Replace an endpoint (`setElement`) | `Tie.isInvalidatedByReplacement` exists but resolves through the endpoint's own line | 4 |
| Insert note/rest between the endpoints | `Tie.isInvalidatedByInsertion` exists, same defect: for a cross-line tie the comparison is `inserted > 7 && inserted <= 0`, never true | 4 |
| Insert barline/repeat between the endpoints | Allowed — `Tie.isLegalSeparator` already defines this | 4 |
| Delete a whole line | **`Song.removeLine` does no span cleanup at all** | 5 |
| Insert a line between the two lines | Endpoints stop being adjacent; nothing notices | 5 |
| Playing-tie highlight, MIDI, stem direction | **Pre-existing pattern:** raw index pair compared across two lines | 6 |
| Toggle tie at a line boundary | Not offered | 7 |
| Pitch-shift an endpoint | `PitchShifter` expands the tie chain by single-line index range | 8 |
| Render both halves | `resolveSpan` returns null; the tie is skipped and not drawn | 9 |
| Click a tie arc / draw it selected | Selects via `isOnLine`, single-line only | 10 |
| Copy a range splitting the tie | `Fragment.cloneSpans` drops it silently | 11 |
| Paste at the boundary | Reaches insertion through `addPastedSpan` | 11 |
| MusicXML round trip | Reader attaches to the stop's line only | 12 |
| Undo/redo of all of the above | `appendChild`/`removeChild` are the recorded chokepoint | every phase |

Not applicable: there is no transpose, key-change, line-split, line-merge or reflow path in the codebase. Replacing a note with a rest is forbidden by the UI. A pasted span is never transiently cross-line — `ScoreViewController.java:1116-1123` enforces that every clone is inserted before the first `addPastedSpan`, and `Fragment.cloneSpans` produces endpoints in no line.

### Working rules for every phase
**Read narrowly.** In an earlier run two phase agents exhausted their context and died, and three more were on the same trajectory. The cause was whole-file reading — a single 108KB `Read`, a 74KB test-file `Read`, and `cat -n` over all of `Span.java` and `Tie.java`. Do not `cat`, `cat -n`, or `Read` an entire file longer than roughly 400 lines. Instead:

- `jet_brains_find_symbol` with `include_body=true` for one method or member.
- `Read` with `offset` and `limit` around a line number you already know.
- `rg -n` scoped to a directory to locate the line first, then read that range.

`Line.java`, `Span.java`, `Tie.java`, `Song.java`, `LayoutEngine.java`, `MusicEditOperations.java` and the larger test classes are all past that limit. Read them symbol by symbol. You do not need to hold a whole file to change one method.

**Test narrowly, then broadly.** Run `./scripts/test.sh unit <ClassName>` for the classes you touched before running the full `./scripts/test.sh unit`. The full suite is ~6,900 tests and its output is correspondingly large; do not use it as your edit-compile-test loop.

**Report, don't expand.** If your phase needs a file outside its declared `Files:` set, stop and report it as a blocker. Another phase owns that file and may be writing it concurrently.

* * *
## Status Dashboard
Phases marked ✅ below were completed by an earlier run; their work is on disk. Phases 3, 4, 6 and 7 were split after that run showed single phases exhausting an agent's context — each is now an implementation phase followed by a test-writing phase, since the largest reads were of test classes. Phase 9 was split the same way before ever running, being the largest remaining phase.

| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1 | [Span Parentage Without Attachment](#-phase-1-span-parentage-without-attachment) | ✅ Complete | Opus 4.8, high |
| 2 | [Typed Endpoint Bounds and Receiver-Relative Accessors](#-phase-2-typed-endpoint-bounds-and-receiver-relative-accessors) | ✅ Complete | Opus 4.8, high |
| 3a | [Element Deletion — Sweep Correctness](#-phase-3a-element-deletion--sweep-correctness) | ✅ Complete | Opus 4.8, medium |
| 3b | [Element Deletion — Tests](#-phase-3b-element-deletion--tests) | ✅ Complete | Sonnet 4.6, medium |
| 4a | [Insertion and Replacement — Rules](#-phase-4a-insertion-and-replacement--rules) | ✅ Complete | Opus 4.8, medium |
| 4b | [Insertion and Replacement — Tests](#-phase-4b-insertion-and-replacement--tests) | ✅ Complete | Sonnet 4.6, medium |
| 5 | [Line Structure Mutations](#-phase-5-line-structure-mutations) | ✅ Complete | Opus 4.8, medium |
| 6a | [Tie-Reading Sites — Silent Failures](#-phase-6a-tie-reading-sites--silent-failures) | ✅ Complete | Opus 4.8, medium |
| 6b | [Tie-Reading Sites — Rendering and Stems](#-phase-6b-tie-reading-sites--rendering-and-stems) | ✅ Complete | Opus 4.8, medium |
| 6c | [Tie-Reading Sites — Tests](#-phase-6c-tie-reading-sites--tests) | ✅ Complete | Sonnet 4.6, medium |
| 7a | [Creating a Cross-Line Tie — Lookup and Toggle](#-phase-7a-creating-a-cross-line-tie--lookup-and-toggle) | ✅ Complete | Sonnet 4.6, medium |
| 7b | [Creating a Cross-Line Tie — Tests](#-phase-7b-creating-a-cross-line-tie--tests) | ✅ Complete | Sonnet 4.6, medium |
| 8 | [Pitch Shift Across the Break](#-phase-8-pitch-shift-across-the-break) | ✅ Complete | Sonnet 4.6, medium |
| 9a | [Rendering — Resolution and Layout Shape](#-phase-9a-rendering--resolution-and-layout-shape) | ✅ Complete | Opus 4.8, high |
| 9b | [Rendering — Arc Geometry](#-phase-9b-rendering--arc-geometry) | ✅ Complete | Opus 4.8, high |
| 9c | [Rendering — Tests](#-phase-9c-rendering--tests) | ✅ Complete | Sonnet 4.6, medium |
| 10 | [Selecting the Tie](#-phase-10-selecting-the-tie) | ✅ Complete | Sonnet 4.6, low |
| 11 | [Clipboard](#-phase-11-clipboard) | ✅ Complete | Sonnet 4.6, low |
| 12 | [Persistence](#-phase-12-persistence) | ✅ Complete | Sonnet 4.6, medium |
| 14 | [MusicXML Write-Side Tie Markers](#-phase-14-musicxml-write-side-tie-markers) | ✅ Complete | Opus 4.8, medium |
| 13 | [Full Gate](#-phase-13-full-gate) | ✅ Complete | Opus 4.8, medium |

* * *
## ✅ Phase 1: Span Parentage Without Attachment
**Status:** Complete **BlockedBy:** — **Files:** src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/test/java/songscribe/dom/ParentLinePropagationTest.java, src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java, src/test/java/songscribe/dom/SpanParentageTest.java **Recommended model/effort:** Opus 4.8, high — changes a documented invariant that two test files assert directly

### Context this phase needs
`LineElement.java:73-77` documents `parentLine == L ⟺ that list of L contains this`. `parentLine` (`:79`) has a package-private setter (`:167`) written only by `Line.attach` (`:290`) and `Line.detach` (`:301`).

Span parentage is barely consulted. Of the eleven `getParentLine()` call sites in `src/main/java/`, **nine take a `StaffElement` by declared type.** Exactly two can ever receive a `Span`:

- `Line.detach`'s re-parent guard (`:313`) — whose own comment says *"No caller reaches this return today."* Once spans stop reaching `detach`, the guard has no span case to answer.
- `SelectionCoordinator.isOnLine` (`:583-591`) — walks up `getParentElement()` to a root and compares `root.getParentLine() == line`. This is the "click selects both halves" requirement in disguise: it must answer true for a cross-line tie asked about *either* line, which a single field cannot do.

Three facts make never-attaching safe, all verified:

- `attach`/`detach` guard their `elementIndexMap = null` invalidation behind `instanceof StaffElement` (`:292, :304`), so skipping them for spans changes nothing there.
- `attach` also calls `propagateParentLine`, and `Span` has no `addChild`/children machinery at all — a no-op today.
- `Span.copy`'s Javadoc (`:301`) already says *"Does not set `parentLine`"*; that becomes simply true of every span for its whole life.

### Tasks
1. Stop attaching spans. `Line.appendChild` (`:328`) drops its `attach(element)` call and adds the span to `this` line **and** to the other line of the pair when one exists. `removeChild` (`:337`) drops `detach(element)` and removes from both. These stay the only writers of `spans`.

2. Add `Span.isIn(Line)` as exactly two reference comparisons against `anchorElement.getParentLine()` and `endElement.getParentLine()`. Add **no** `lines()` method and **no** `getParentLine()` override — `isOnLine` is a hot path and there is nothing for a collection to buy.

3. Change `SelectionCoordinator.isOnLine` (`:583`) to route a `Span` root through `isIn`. Keep it O(1); its Javadoc explains why, and that reasoning still holds.

4. Widen `removeInvalidatedSpan`'s documented "no-op when the span is no longer in this line" contract (`Line.java:1555-1562`) to "no longer in any line it was in", and make the per-type removals honor it.

5. Update the documentation that describes the old invariant: the `LineElement.java:44-77` diagram and invariant text (`StaffElement` keeps the two-way stored invariant; `Span` gets the one-directional derived one), the invariant comment block at `Line.java:283-287`, and the `elementIndexMap` doc block at `Line.java:114` — span add/remove can no longer invalidate that cache, so its span case must go.

6. Update both test files that assert span parentage directly, replacing rather than deleting what they protect:
   - `ParentLinePropagationTest.WhenSpanAddedOrRemoved` (`:258-309`) asserts `getParentLine()` isSameAs/isNull for a tie, beam, tuplet, hairpin and trill. Each becomes an `isIn` assertion plus a `line.getSpans()` containment check. `testAttachingASpanToAnotherLineBeforeRemovalWins` (`:311-324`) encodes behavior being removed — under derivation, attaching does not *move* a span — so it is replaced by a test of the new rule.
   - `MutationReplayerRoundTripTest.ElementParentage` has two span tests — `testUndoOfSpanAdditionDetachesAndRedoReattaches` (`:955-965`) and `testUndoOfSpanRemovalReattachesAndRedoDetaches` (`:976-986`) — asserting `tie.getParentLine()` / `beam.getParentLine()` across replay. Both must become `isIn`-based on every line the span belongs to, plus the `L.spans` containment check, so replay-time parentage stays as tightly pinned as it is today.

7. Add `SpanParentageTest`: a tie whose endpoints are in two lines is in both `getSpans()` lists and `isIn` is true for both; a span's `getParentLine()` is null throughout its life; a tie with both endpoints detached belongs to no line but is still in the list it was added to; detaching one endpoint drops that line from the derived answer; undo and redo of the add and the remove preserve all of it.

8. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — this touches parentage for every span type).

* * *
## ✅ Phase 2: Typed Endpoint Bounds and Receiver-Relative Accessors
**Status:** Complete **BlockedBy:** 1 **Files:** src/main/java/songscribe/dom/SpanBound.java, src/main/java/songscribe/dom/SpanLookup.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java, src/test/java/songscribe/dom/SpanBoundPredicateTest.java, src/test/java/songscribe/dom/SpanLookupTest.java **Recommended model/effort:** Opus 4.8, high — every span query in the app reads these accessors, and the merge reduction silently misreads an off-line bound

**These land together or not at all.** The bound type, the predicate signatures and the accessor return types are one signature change; splitting them leaves an intermediate that does not compile.

### Context this phase needs
`Line.anchorIndexOf` (`:1602`) and `endIndexOf` (`:1607`) are each a single call to `getElementIndex`, which is `@Nullable`-accepting and returns `-1` for a null element and for any element not in this line, with no direction.

The three predicates, all in `Span.java`:

```java
// :271  containing — guarded, a half-detached span contains nothing
anchorIndex >= 0 && endIndex >= 0 && anchorIndex <= elementIndex && elementIndex <= endIndex

// :284  overlapping — deliberately unguarded; the trill sweeps in Line.addTrill and
//       removeTrillsOverlapping rely on a half-detached span still being found
anchorIndex <= end && endIndex >= begin

// :291  exactly
spanAnchorIndex == anchorIndex && spanEndIndex == endIndex
```

`mergeOverlappingSpans` (`:980`) reads both accessors inside its predicates and then **reduces and indexes** with the results:

```java
.mapToInt(this::anchorIndexOf).min().orElse(anchorIdx)   // :997
.mapToInt(this::endIndexOf).max().orElse(endIdx)         // :1002
span.setAnchorElement(elements.get(mergedAnchorIdx));    // :1007
```

`reach` is `0` for beams and `SPAN_ADJACENCY_REACH` for hairpins. Ties never merge, but these predicates receive every span type's bounds.

`SpanLookup.anchorIndexOf`'s Javadoc (`:41-52`) justifies its contract with *"during a paste or a line split an endpoint may already have been reparented."* Both halves of that are wrong: there is no line-split path in the codebase, and `ScoreViewController.java:1116-1123` enforces an ordering constraint that forecloses the paste case. Replace the justification with the real one — a position in another line is indistinguishable from a real one, so the receiver must decide.

### Tasks
1. Add `SpanBound` as a sealed type with `At(int index)` as a record and `BEFORE_LINE`, `AFTER_LINE`, `ABSENT` as singletons, each with Javadoc stating what it means for a caller. The three valueless cases must not allocate.

2. Change `SpanLookup.anchorIndexOf` / `endIndexOf` to return `SpanBound`, and rewrite their contract Javadoc — including replacing the stale line-split/paste justification described above. Extend it there, not only on the `Line` overrides.

3. Implement both `Line` accessors through **one private resolver** taking the endpoint and its off-line direction; both accessors stay the one-liners they are today. Do not write two parallel bodies — commit `706f1c62` on this branch collapsed exactly this duplication. The resolver returns `ABSENT` for a null endpoint, for an endpoint in no line, and for an endpoint whose line is **not in `song.lines`**; otherwise `At(index)` when the endpoint is in this line, and `BEFORE_LINE`/`AFTER_LINE` from the two lines' actual positions in `song.lines`.

4. Change `Span.IndexPredicate.test` to take two `SpanBound`s and rewrite the three factories, keeping their `int` parameters. `containing`: reject `ABSENT`; read `BEFORE_LINE` as before every index and `AFTER_LINE` as after every index, so a cross-line half reports true for every element it passes over. `overlapping`: apply the same unbounded reading, and preserve today's deliberate non-rejection of the unresolvable case that the trill sweeps depend on. `exactly`: only `At` can equal a query index — never coerce a bound to `0` or `lastIndex`.

5. Narrow to `At` before any arithmetic or indexing in `mergeOverlappingSpans`: exclude candidates with a non-`At` bound from absorption before `+reach`, `-reach`, `min()`, `max()` and `elements.get()` run. Update the two remaining `int` readers — `MusicEditOperations.java:872-876` and `MidiSequenceBuilder.java:192` — which read the pair and feed it straight into a predicate.

6. Tests. `SpanBoundPredicateTest` drives each predicate directly with all sixteen bound combinations per predicate; these are pure functions, so no `Line` fixture is needed. Extend `SpanLookupTest`: for a tie attached to two lines, line A reports `(At(anchorIndex), AFTER_LINE)` and line B `(BEFORE_LINE, At(endIndex))`; each line reports the tie as containing every element from its attached endpoint to its edge; a tie whose far endpoint has been deleted reports `ABSENT`, not a direction; and a merge candidate carrying an off-line bound is excluded from absorption rather than swallowing an unrelated span.

7. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — every span query reads these).

* * *
## ✅ Phase 3a: Element Deletion — Sweep Correctness
**Status:** Complete **BlockedBy:** 2 **Files:** src/main/java/songscribe/dom/Line.java **Recommended model/effort:** Opus 4.8, medium — the failure is a double-recorded mutation, which surfaces only as undo needing two presses

**State on disk.** A killed agent left `Line.java` partially edited and created `src/test/java/songscribe/dom/CrossLineTieDeletionTest.java`. The tree compiles and the test file is phase 3b's to finish, not yours. Verify what is already correct rather than redoing it, and do not edit the test file — 3b owns it.

### Context this phase needs
`removeRange` (`:626`) sweeps **this line's** `spans` at `:650-656`:

```java
var invalidated = spans.stream()
    .filter(r -> !(r instanceof Hairpin))
    .filter(r -> r.isInvalidatedBy(deletedElements)
        || r.isInvalidatedByDeletion(deletedElements, this))
    .toList();
invalidated.forEach(this::removeInvalidatedSpan);
```

`Span.isInvalidatedBy` (`Span.java:119`) tests whether either endpoint is among the deleted elements. `removeElement` runs an equivalent sweep at `:605-609`.

Because phase 1 puts the tie in both lists, **both lines' sweeps now see it.** Line A's removal clears it from both; line B's sweep then calls `removeInvalidatedSpan` on a tie already gone. `removeInvalidatedSpan`'s Javadoc (`:1555-1562`) anticipates unconditional calling, but names one caller — the paste reconciliation — as the exception. After this plan it is the **normal** path for every cross-line tie deletion.

The unanswered question is not whether the second call is a no-op on the list, but whether it is a no-op **before** `applyChange` records a mutation. A mutation recorded with nothing to remove makes undo restore twice for one removal.

`setElement` (`:444`) is the easy case: its re-pointing loop (`:466-474`) walks this line's `spans` swapping `oldElement` for the replacement, and since the tie is one object in two lists, a single pass fixes both halves.

### Tasks
1. Confirm removal of an invalidated cross-line tie reaches **both** lines through `removeChild`, via the same tracked-modification path the single-line case uses, so undo restores both halves.

2. Prove the second line's sweep emits **no** mutation for an already-removed tie. If it does, make the emission point conditional on the span actually being present — not the list mutation alone.

3. Cover every sweep site, not only `removeRange`: `removeElement` (`:605`) and `setElement` (`:444`).

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green). Phase 3b writes the cross-line deletion tests; your gate is that the existing suite still passes.

* * *
## ✅ Phase 3b: Element Deletion — Tests
**Status:** Complete **BlockedBy:** 3a **Files:** src/test/java/songscribe/dom/CrossLineTieDeletionTest.java **Recommended model/effort:** Sonnet 4.6, medium — one new test class against behavior 3a has already made correct

### Context this phase needs
Phase 3a's section above holds the sweep context you need — read it, not `Line.java` in full. A killed agent already created `CrossLineTieDeletionTest.java`; treat it as a draft, verify each case against the task below, and finish it. Do not edit `Line.java` — 3a owns it. If a test proves 3a's behavior wrong, report it as a blocker rather than fixing the production code here.

### Tasks
1. Finish `CrossLineTieDeletionTest`: delete the anchor note in line A, assert the tie is gone from both lines; delete the end note in line B, same; range-delete a run containing one endpoint, same; replace an endpoint via `setElement` and assert the surviving tie points at the replacement in both lines; assert undo restores the tie into both lines and redo removes it from both; and assert **exactly one** restore is recorded for a deletion both lines swept.

2. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieDeletionTest` (green), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 4a: Insertion and Replacement — Rules
**Status:** Complete **BlockedBy:** 2 **Files:** src/main/java/songscribe/dom/Tie.java **Recommended model/effort:** Opus 4.8, medium — converts shipped behavior, so an error changes ties users already have

**State on disk.** A killed agent left `Tie.java` partially edited (roughly 116 lines changed). The tree compiles. Verify what is there against the tasks below rather than starting over or assuming it is finished. `TieInvalidationTest.java` is untouched and belongs to phase 4b — do not edit it.

**Phase 11 depends on you.** `FragmentTest.PasteAcrossLineBoundary.testPastingRightAfterTheAnchorOfACrossLineTieRemovesIt` currently fails against the unfixed `isInvalidatedByInsertion`. It should pass once this phase lands, without any change to the clipboard files.

### Context this phase needs
**`Tie` already has both rules.** Commit `11cd3357` on this branch added `Tie.isInvalidatedByInsertion` (`:97`), `Tie.isInvalidatedByReplacement` (`:129`), `Tie.isLegalSeparator` (`:80`) and a 607-line `TieInvalidationTest`. This phase does **not** add them — it fixes how they resolve.

Both resolve through `getAnchorElementIndex()` / `getEndElementIndex()`, which answer from **each endpoint's own line**:

```java
var anchorIndex = getAnchorElementIndex();   // the ANCHOR's line
var endIndex = getEndElementIndex();         // the END's line
if (anchorIndex < 0 || endIndex < 0) { return false; }
...
return insertedIndex > anchorIndex && insertedIndex <= endIndex;
```

For a cross-line tie that is anchor `7` in line A and end `0` in line B, compared against each other: `insertedIndex > 7 && insertedIndex <= 0` is **never true**, in either line. The tie is silently never invalidated by any insertion. `isInvalidatedByReplacement` is worse — it mixes `getAnchorElementIndex()` (the endpoint's line) with `line.getElementIndex(oldElement)` (the receiving line) in one comparison chain.

This also violates the rule `SpanLookup.anchorIndexOf`'s Javadoc states: every query resolves through the receiver's accessors, *"never through `Span#getAnchorElementIndex()`."*

**Keep the shipped type rule.** `isLegalSeparator(type)` is `type.isNonDuration() && type != FINAL_DOUBLE_BARLINE`, and `RangeQueries.canToggleTie` reads the same definition so the draw check and the invalidation check cannot disagree. That is broader than "a note or a rest breaks it" — a grace note breaks it too. Do not narrow it.

The sweeps pass the receiving line: `Line.java:394` passes `(insertedIndex, insertedType, this)` and `:450` passes `(oldElement, element, this)`.

For a cross-line tie the "between" region is everything after the anchor in line A plus everything before the end in line B. At creation that is only *append to the end of line A* or *insert at index 0 of line B*, but that shape does not survive the first legal separator: the type rule above keeps the tie alive across a barline, repeat, clef or key change, so one may already sit after the anchor or before the end. Do not narrow the region to a single position. The line B boundary is also not a plain `index == 0` test when line B's first element hosts a grace note — `isInsideGraceHostPair`, `precedingGraceNoteIndex` and `nearestNonGraceIndex` already define that boundary.

### Tasks
1. Extract one private resolve-or-bail helper on `Tie` that resolves both endpoints through `line.anchorIndexOf` / `line.endIndexOf` and bails on `ABSENT`. Both overrides call it, so "unresolvable" is defined once and insertion and replacement cannot disagree about a bound case.

2. Convert `isInvalidatedByInsertion` to the receiver's bounds. Reuse the phase 2 predicates rather than writing new index arithmetic: in line A the end bound is `AFTER_LINE`, so anything inserted after the anchor is between the endpoints; in line B the anchor bound is `BEFORE_LINE`, so anything inserted before the end is. Keep `isLegalSeparator` as the type gate.

3. Convert `isInvalidatedByReplacement` the same way, so the replaced element's position and the tie's endpoints are resolved in one line. Preserve the shipped endpoint rule exactly — same staff position, same explicit accidental, deliberately not a `getPitch()` comparison — and its comment explaining why.

4. Use the existing grace-host helpers for the line B boundary rather than testing `index == 0`.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit TieInvalidationTest` (green — the shipped same-line cases must not regress), then `./scripts/test.sh unit` (green). The full suite must also turn `FragmentTest` green.

* * *
## ✅ Phase 4b: Insertion and Replacement — Tests
**Status:** Complete **BlockedBy:** 4a **Files:** src/test/java/songscribe/dom/TieInvalidationTest.java **Recommended model/effort:** Sonnet 4.6, medium — extends a 607-line shipped test class, so read it by nested class, never whole

### Context this phase needs
Phase 4a's section above holds the rule context you need. `TieInvalidationTest` is a 607-line class added by commit `11cd3357` — locate its nested classes with `rg -n "class " src/test/java/songscribe/dom/TieInvalidationTest.java` and read only the one you are extending. Do not read it whole, and do not edit `Tie.java` — 4a owns it.

### Tasks
1. Extend `TieInvalidationTest` — do not create a second class. Add: for a cross-line tie, appending a note to line A or inserting one at the head of line B removes it from **both** lines; a barline or repeat in either position leaves it; a grace note removes it; replacing either endpoint with a note at a different staff position removes it from both; undo restores it into both. Every existing same-line case must still pass unchanged.

2. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit TieInvalidationTest` (green), then `./scripts/test.sh unit` (green — this touches ties users already have).

* * *
## ✅ Phase 5: Line Structure Mutations
**Status:** Complete **BlockedBy:** 1 **Files:** src/main/java/songscribe/dom/Song.java, src/test/java/songscribe/dom/CrossLineTieLineStructureTest.java **Recommended model/effort:** Opus 4.8, medium — `removeLine` has no span handling at all, and replay reaches it from two directions

### Context this phase needs
`Song.removeLine` (`:1193`) removes the line from `lines` and maintains the terminal-barline invariant. It performs **no span cleanup and detaches no elements.** It is reachable from a menu command (`ScoreViewController.java:657`) and from undo/redo replay (`MutationReplayer.java:110, 152`).

That is the sharpest edge in this plan. After deleting line B, the tie's end element still reports B as its parent line, so line A would hold a tie running off the edge to a line no longer in the song.

**Do the cleanup as an explicit span sweep, not by detaching the deleted line's elements.** `removeLine` is `applyChange(new LineDeletion(index, deletedLine), () -> lines.remove(index))` — the deleted `Line` survives inside the mutation record holding its `elements` list, every element still pointing at it, and undo re-inserts that same object intact. Detaching would require undo to re-attach, and `Line` exposes no re-attach path: `attach` is private and only reachable through `addElement`/`appendChild`.

After the sweep the one-directional invariant still holds. **As implemented, the sweep takes the tie out of both lists** — the surviving line's and the deleted line's — because `removeChild` removes a cross-line span from both halves together, and `CrossLineTieLineStructureTest.assertTieRemovedEverywhere` pins that. An earlier draft of this paragraph said the deleted line kept the tie in its own `spans`; that was never what shipped.

**Replay must not double-apply.** `MutationReplayer` reaches `removeLine` for both `LineInsertion` undo and `LineDeletion` redo, so the sweep is gated on `!isReplaying()`: during replay the recorded span mutations are replayed independently.

That gate is deliberately *narrower* than the one on the terminal maintenance beside it (`!isMutationTrackingSuspended() && !isReplaying()`, `:1151`, `:1203`). The two are not the same kind of work. Maintaining the closing barline is auto-maintenance that bulk loading and test setup legitimately opt out of while they build a line into a deliberately unusual intermediate state. A tie between two lines that are no longer adjacent is not an intermediate state anyone builds on purpose — it is a shape nothing can draw — so the repair applies whether or not mutations are being recorded. An earlier draft of this paragraph said the sweep followed the terminal gate exactly; that was never what shipped, and matching it would let a suspended-tracking line insert leave a tie spanning a gap.

`Song.addLine(int index, Line)` (`:1130`) inserts a line. Inserting between the two tied lines leaves the endpoints non-adjacent, which no cross-line tie can represent.

Both methods carry a `<pre>` flow diagram enumerating every `applyChange` in order — `addLine` at `:1110-1128`, `removeLine` at `:1170-1191`. Both currently omit the step this phase adds, and both read as exhaustive lists.

### Tasks
1. Make `removeLine` sweep every span anchored in the deleted line out of the lines that survive it, through the tracked path so undo restores them. Leave the deleted line's own elements attached to it.

2. Gate the sweep on `!isReplaying()`, matching how `maintainTerminalOnLastLineChange` is already gated at `:1203`, so replay does not double-apply.

3. Make `addLine(int, Line)` invalidate any cross-line tie whose two endpoint lines are no longer adjacent after the insertion, under the same replay gate.

4. Update both `<pre>` diagrams — `addLine` (`:1110-1128`) and `removeLine` (`:1170-1191`) — to include the new step in the right position. A diagram that enumerates every `applyChange` and omits one actively misleads.

5. Add `CrossLineTieLineStructureTest`: deleting line A removes the tie from line B and vice versa; undo restores the line and the tie into both; inserting a line between the two tied lines removes the tie; undo restores it; **undo-redo-undo returns to the identical state**, proving the sweep does not double-apply under replay; and a same-line tie in an unrelated line is untouched by any of it.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 6a: Tie-Reading Sites — Silent Failures
**Status:** Complete **BlockedBy:** 2 **Files:** src/main/java/songscribe/ui/renderer/LineInvariants.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/dom/Span.java **Recommended model/effort:** Opus 4.8, medium — the two silent user-visible failures, plus the one method that lives on `Span` rather than `Tie`

**State on disk.** A killed agent already edited `LineInvariants.java` and `LineTrackBuilder.java`. The tree compiles. Verify those edits against the tasks below rather than redoing them from scratch.

**You own three of the six audited sites** — the two the original phase said to convert first because their failures are silent and user-visible, plus `Span.getElementCount`. Phase 6b owns the other three (`MusicEditOperations.modifyStemDirection`, `TieRenderer.determineTieColor`, `LineRenderer.renderTies`); do not edit those files. Phase 6c writes the tests for both.

### Context this phase needs
`Span.getAnchorElementIndex()` / `getEndElementIndex()` resolve through **each endpoint's own line** and are not being changed — roughly 106 call sites depend on that. Only **ties** can be cross-line, so only sites reading the pair off a `Tie` are affected. For a cross-line tie they return `7` (line A) and `0` (line B) and callers compare them.

The six sites, found with `jet_brains_find_referencing_symbols`:

| Site | What it does | Cross-line result |
| --- | --- | --- |
| `LineInvariants.isElementInPlayingTie:465` | `Span.containing(i).test(anchorIdx, endIdx)` | `7 <= i && i <= 0` — highlight **silently never fires** |
| `LineTrackBuilder.addNoteMessages:312` | `tieSpan.getAnchorElementIndex() == elementIndex` | matches index 7 in **whichever** line is being built |
| `MusicEditOperations.modifyStemDirection:1139-1140` | `tieStart` / `tieEnd` as a range | range is inverted |
| `TieRenderer.determineTieColor:126` | `invariants.getElementColor(anchorIdx)` | reads line B's element 7 |
| `LineRenderer.renderTies:418` | anchor index for the preview shift | shift applied against the wrong element |
| `Span.getElementCount:98-99` | `endIndex - startIndex` style arithmetic | negative count |

### Tasks
1. For each of **your three** sites, decide and record in a comment whether it converts to receiver-relative resolution (`line.anchorIndexOf` / `endIndexOf`) or keeps endpoint-own-line resolution behind a guard that recognizes a cross-line tie. State the reason at the site.

2. Convert `LineInvariants.isElementInPlayingTie` and `LineTrackBuilder.addNoteMessages` — these are the two whose failures are user-visible and silent.

3. Handle `Span.getElementCount` explicitly: it is on `Span`, not `Tie`, so decide whether a cross-line tie has a meaningful count or whether the method must refuse. Check its callers with `jet_brains_find_referencing_symbols` before choosing.

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green). Phase 6c writes the new tests; your gate is that the existing suite still passes.

* * *
## ✅ Phase 6b: Tie-Reading Sites — Rendering and Stems
**Status:** Complete **BlockedBy:** 6a **Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/renderer/TieRenderer.java, src/main/java/songscribe/ui/component/score/LineRenderer.java **Recommended model/effort:** Opus 4.8, medium — three sites, each needing its own recorded decision

### Context this phase needs
Phase 6a's section above holds the shared context and the full six-site table — read it there rather than re-deriving it. You own the remaining three rows: `MusicEditOperations.modifyStemDirection:1139-1140`, `TieRenderer.determineTieColor:126` and `LineRenderer.renderTies:418`. Do not edit 6a's three files. Phase 6c writes the tests.

### Tasks
1. For each of your three sites, decide and record in a comment whether it converts to receiver-relative resolution (`line.anchorIndexOf` / `endIndexOf`) or keeps endpoint-own-line resolution behind a guard that recognizes a cross-line tie. State the reason at the site. Follow whatever convention 6a established for the same decision.

2. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 6c: Tie-Reading Sites — Tests
**Status:** Complete **BlockedBy:** 6b **Files:** src/test/java/songscribe/ui/renderer/CrossLineTieReadingTest.java **Recommended model/effort:** Sonnet 4.6, medium — one new test class covering the sites 6a and 6b converted

### Context this phase needs
Phases 6a and 6b above hold the six-site table and the decisions actually taken. Read the comments those phases left at each converted site — they record why each site resolves the way it does — rather than re-reading the files whole. Do not edit any production file; 6a and 6b own them. If a test proves a conversion wrong, report it as a blocker.

### Tasks
1. Add `CrossLineTieReadingTest` covering the converted sites: the playing-tie highlight fires for elements under **both** halves; MIDI suppresses the note-on for the tie's continuation note and not for an unrelated element at the same index in the other line; stem direction applies across both halves.

2. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieReadingTest` (green), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 7a: Creating a Cross-Line Tie — Lookup and Toggle
**Status:** Complete **BlockedBy:** 6b **Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/selection/RangeQueries.java **Recommended model/effort:** Sonnet 4.6, medium — the enable predicate has four preconditions and a mirror case

**State on disk.** A killed agent left small edits in `RangeQueries.java` and `MusicEditOperations.java`. The tree compiles. Verify them against the tasks below rather than assuming either direction.

`BlockedBy: 6b` is a file-conflict ordering, not a logical dependency: 6b also writes `MusicEditOperations.java`, and two phases must never write one file at the same time.

### Context this phase needs
From issue #493: selecting the **last** element of a line enables the toggle-tie action when that element is a pitched note and the **first** element of the next line is a pitched note at the same pitch. Selecting the **first** element of a line is the mirror case, pairing with the previous line's last element.

**Correction.** "Last element" was too narrow, and was found so by driving the app. A line closed by a barline — the ordinary way to write one — could never offer the tie, because its last element was the barline rather than the note. `boundaryTieAt` now walks inward from each line's edge past every element `Tie.isLegalSeparator` admits and attaches to the first element that is not one, on both sides of the break; the pitched-note and same-pitch checks then apply to what the walk found. The final double barline stops the walk, since nothing may sound across the end of the piece. This is the same type rule phase 4a already uses to decide that an inserted barline does not break an existing tie — a tie the user could not have created is not a state the toggle should be able to refuse into existence.

`Selection.Range` (`Selection.java:70`) is `(Line line, int begin, int end, int anchor)` — bound to one line. This feature needs no cross-line selection: the selection stays a single element and the partner note is found in the adjacent line.

`toggleTie` (`MusicEditOperations.java:252-269`) takes `range.begin()`/`range.end()` as the two tie notes, calls `line.findExactTie(beginIndex, endIndex)` and either adds or removes. `RangeQueries.canToggleTie` (`:122`) gates the action, and `:267` decides whether it shows as connect or disconnect.

Phase 1 makes attachment automatic: `line.addTie(tie)` on either line puts it in both lists.

### Tasks
1. Add the boundary-pair lookup: given a single-element selection, return the partner element in the adjacent line when all of these hold — the selected element is the first or last in its line, an adjacent line exists in that direction, both elements are pitched notes, and their pitches are equal. Return null otherwise.

2. Enable the toggle-tie action for a single-element selection exactly when that lookup returns a partner, in both directions.

3. Extend `toggleTie` to handle the boundary case: create `new Tie(anchor, end)` with the anchor being whichever element comes first in document order, and add it through `line.addTie`. Removing an existing cross-line tie goes through `removeTie`, which phase 1 makes remove from both lists.

4. Use `findExactTie` against the owning line to decide add-versus-remove. Phase 2 makes a cross-line half distinguishable from a same-line tie at the same index — only `At` can equal a query index — so this must not match a same-line tie ending on the boundary note.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green). Phase 7b writes the toggle tests; your gate is that the existing suite still passes.

* * *
## ✅ Phase 7b: Creating a Cross-Line Tie — Tests
**Status:** Complete **BlockedBy:** 7a **Files:** src/test/java/songscribe/ui/selection/CrossLineTieToggleTest.java **Recommended model/effort:** Sonnet 4.6, medium — one new test class over an enable predicate with four preconditions

### Context this phase needs
Phase 7a's section above holds the toggle and enable-predicate context. Read the boundary-pair lookup 7a added via `jet_brains_find_symbol` rather than reading `MusicEditOperations.java` or `RangeQueries.java` whole — both are large. Do not edit either; 7a owns them.

### Tasks
1. Add `CrossLineTieToggleTest`: enabled for a matching boundary pair in both directions; disabled when pitches differ, when either element is unpitched, when the selected element is not at a boundary, and at the first line's start and last line's end; the toggle creates a tie in both lines and toggling again removes it from both.

2. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieToggleTest` (green), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 8: Pitch Shift Across the Break
**Status:** Complete **BlockedBy:** 1 **Files:** src/main/java/songscribe/ui/component/score/PitchShifter.java, src/test/java/songscribe/ui/component/score/CrossLinePitchShiftTest.java **Recommended model/effort:** Sonnet 4.6, medium — the existing tie-chain expansion is index-based and silently produces the wrong set

### Context this phase needs
`PitchShifter` already moves tied notes together. `PitchShifter.buildPitchShiftGroup` (`:311-313`) expands each selected note's tie chain to its full transitive closure — chained ties must move as one unit even though each link is its own two-note `Tie` — and does it with:

```java
var tie = line.findTieAt(i);

if (tie != null) {
    for (var j = tie.getAnchorElementIndex(); j <= tie.getEndElementIndex(); j++) {
```

On a cross-line tie those two indices resolve through **different lines** (anchor 7 in line A, end 0 in line B), so the loop either runs zero times or walks an unrelated index range in whichever line is being iterated. Left alone, shifting one endpoint silently produces a tie between two different pitches.

`PitchShifter.shiftPitch` (`:92`) is called per line with a `(begin, end)` range (`ScoreInputHandler.java:385-387`), so the partner note in the adjacent line is outside everything this class currently reasons about.

### Tasks
1. Replace the index-range walk with an endpoint-based traversal: from a note, reach its tie partner through the `Tie`'s anchor and end **elements**, not through index arithmetic. This is the natural shape for the chain closure anyway and removes the single-line assumption.

2. Extend the shift itself to apply to a partner note in an adjacent line, keeping the whole operation inside one tracked modification so undo restores both notes together.

3. Preserve the existing same-line chained-tie behavior exactly — the transitive closure over separately-created links is the reason this code exists.

4. Add `CrossLinePitchShiftTest`: raising the anchor of a cross-line tie raises the end in the other line by the same amount; lowering does the same; a chain that crosses the break moves as one unit; undo restores every moved note; and a same-line chain still moves as one unit.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 9a: Rendering — Resolution and Layout Shape
**Status:** Complete **BlockedBy:** 6b **Files:** src/main/java/songscribe/layout/ElementColumn.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/ui/renderer/TieRenderer.java **Recommended model/effort:** Opus 4.8, high — the data shape every later rendering task is written against

This phase carries the data-shape half of the rendering work: teaching span resolution to distinguish "off this line's edge" from "unresolvable", and giving `TieLayout` somewhere to record which side is open. Phase 9b computes the actual arc geometry against that shape; phase 9c tests it.

`BlockedBy: 6b` is a file-conflict ordering as well as a logical one: 6b also writes `TieRenderer.java`.

The section below carries the full LilyPond findings. Read them now — they constrain the shape you are defining, and 9b is written assuming this phase left room for them.

### Context this phase needs
`LayoutEngine.calculateTies` (`:827`) iterates `line.findTies()` and calls `ElementColumn.resolveSpan` (`ElementColumn.java:662`), which returns **null** when either endpoint has no column in this line (`:674-676`). `calculateTies` then skips the tie (`:843-845`), so a cross-line tie currently produces no `TieLayout` and is not drawn at all.

The existing geometry (`:852` onward) derives the arc from two note columns sharing one Y, following LilyPond's `tie-formatting-problem.cc`.

**Hit regions need no work.** `LayoutResult` is built per line (`LayoutEngine.layout(Line line)`, `:189`), and `HitRegionBuilder.addTies` (`:448`) reads `layoutResult.getTieLayout(tie)` and registers `tieBoundsSs(tieLayout)`. Each half's bounds come from that half's own geometry, and the `Map<Tie, TieLayout>` key cannot collide across lines. Do not add constraints here.

#### What LilyPond does with a tie broken across a line break
Read from `~/Developer/projects/lilypond/lily/`. Three findings, each with its source, to be written into the comment block at `LayoutEngine.java:852-869` in the style already established there.

**The open end terminates at the staff edge, not at a fabricated note.** In `Tie_formatting_problem::set_column_chord_outline` (`tie-formatting-problem.cc:259-268`), when the bound item has a non-zero `break_status_dir()` — meaning that side of the tie is a line-break column rather than a note head — the outline's limit is taken from the staff's own extent:

```cpp
if (bounds[0]->break_status_dir ())
  {
    Interval iv (Axis_group_interface::staff_extent (
      bounds[0], x_refpoint_, X_AXIS, y_refpoint_, Y_AXIS));
    if (iv.is_empty ())
      iv.add_point (bounds[0]->relative_coordinate (x_refpoint_, X_AXIS));

    chord_outlines_[key].set_minimum_height (iv[-dir]);
  }
```

The unbroken case (the `else` branch) uses the union of the note-head boxes instead. So the open end is bounded by `staff_extent[-dir]`, with the break column's own X as the fallback when the staff extent is empty.

**Correction (phase 13).** This section originally read `staff_extent` as the staff symbol's extent, spanning the whole system, and concluded that "the arc reaches the very edge — past the clef at a line's start and past the terminal barline at its end." That is wrong, and phase 9b built the geometry on it. `Axis_group_interface::staff_extent` (`axis-group-interface.cc:244`) takes the **break column's own** `elements`, keeps those belonging to the staff, and returns their combined X extent — not the staff symbol's. At a system start those elements are the prefatory matter, the clef and key signature; `-dir` for the left bound is `iv[RIGHT]`, so the arc begins at the **right edge of the header**, not at X 0. At a system end the column's element is the bar line and `-dir` is `iv[LEFT]`, so the arc stops at the **left edge of the closing barline**, not at the staff's right margin; the empty-extent fallback to the column's own X is what makes a line with no barline run to the line edge. Phase 13 fixed both sides, and added the `note-head-gap` that `attachment_x_.widen (-x_gap_)` (`:580`) applies to every endpoint, open or attached, which phase 9b also omitted at the open ends.

**Both halves share one arc direction, taken from across the break.** `Tie::get_default_dir` (`tie.cc:100-102`) asks for the head on each side and, when this half has none, reaches into the other half:

```cpp
Grob *one_head = head (me, d);
if (!one_head)
  one_head = head (me->broken_neighbor (d), d);
```

`Tie::get_position` (`tie.cc:60-80`) likewise takes the staff position from whichever head exists, and treats "no head on either side" as a programming error. This is the source for both halves using the same `arcSign()`.

**Each half is a complete arc over its own width, returning to the tie's baseline at the open end.** `Tie_configuration::get_untransformed_bezier` (`tie-configuration.cc:62-71`) computes `Real l = attachment_x_.length()` — the *half's* own span — and calls `slur_shape(l, height_limit_, ratio_)`, whose control points are `(0,0), (indent,h), (w-indent,h), (w,0)` (`bezier-bow.cc:119-132`). Both ends of each half's bezier sit on `y = 0` relative to the tie's position. A broken half therefore does **not** stop mid-arc with a nonzero slope at the staff edge; it rises and comes back down.

The height law is `slur_height(w) = F0_1(w * ratio / height_limit) * height_limit` (`bezier-bow.cc:35-38`) — rising from zero with initial slope `ratio` and saturating at `height_limit` (`tie-details.cc:50-51`: `height-limit` 0.75, `ratio` 0.333). Since each half is narrower than the whole tie, each half is flatter than the unbroken tie would be, and **the two halves have different heights whenever they have different widths.** They are not two pieces of one curve.

`tie-column.cc` contains no break handling at all — the halves are independent formatting problems, one per system.

Rendering reference: [https://musescore.com/user/90375058/scores/30325826](https://musescore.com/user/90375058/scores/30325826)

### Tasks
1. Give `resolveSpan` (or a sibling used only by ties) a result that distinguishes "one endpoint resolved, the other is off this line's edge" from "unresolvable". Do not make it return a fabricated column.

2. Extend `TieLayout` so a half carries which side is open. `TieRenderer.renderTie` (`:85`) reads pre-computed geometry, so the open-ended shape is decided in the layout phase, not the renderer.

3. Adjust `TieRenderer` only as far as the new `TieLayout` shape requires. Do not compute geometry here — phase 9b owns that.

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green). A cross-line tie need not draw correctly yet; it must not break any existing tie.

* * *
## ✅ Phase 9b: Rendering — Arc Geometry
**Status:** Complete **BlockedBy:** 9a **Files:** src/main/java/songscribe/layout/LayoutEngine.java **Recommended model/effort:** Opus 4.8, high — arc geometry with no in-line endpoint to attach to on one side

### Context this phase needs
Phase 9a's section above holds the LilyPond findings — the three sourced facts about how a broken tie is formatted — and the resolution/`TieLayout` shape you build on. **Read that section, including the LilyPond findings, before writing geometry.** You do not need to re-read the LilyPond sources; 9a's section quotes what matters.

`LayoutEngine.calculateTies` (`:827`) and the existing geometry block (`:852` onward) are what you change. `LayoutEngine.java` is large — reach `calculateTies` with `jet_brains_find_symbol`, not by reading the file.

### Tasks
1. Produce geometry for both halves following the three findings: line A's runs from its note to the right staff edge, line B's from the left staff edge to its note; both use the same `arcSign()`; each is a complete arc over its own width, meeting the staff edge back at the tie's baseline rather than mid-arc.

2. Record the LilyPond reasoning in the comment block at `LayoutEngine.java:852-869`, matching how the unbroken case is already documented there.

3. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 9c: Rendering — Tests
**Status:** Complete **BlockedBy:** 9b **Files:** src/test/java/songscribe/layout/CrossLineTieLayoutTest.java **Recommended model/effort:** Sonnet 4.6, medium — three value assertions, deliberately not pixel comparisons

### Context this phase needs
Phases 9a and 9b above define the layout shape and the geometry. Read the comment block 9b left at `LayoutEngine.java:852-869` for the reasoning behind the numbers. Do not edit any production file; 9a and 9b own them. If an assertion cannot be made to pass, report it as a blocker rather than changing the geometry.

### Tasks
1. Add `CrossLineTieLayoutTest` asserting the three geometry facts that are values rather than pixels: both halves report the same `arcSign()`; each half's width is its own span and not the notional whole; each half's curve returns to the tie's baseline at its open end. Appearance is confirmed in phase 13; these three must not depend on someone looking at the screen.

2. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieLayoutTest` (green), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 10: Selecting the Tie
**Status:** Complete **BlockedBy:** 9c **Files:** src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/test/java/songscribe/ui/selection/CrossLineTieSelectionTest.java **Recommended model/effort:** Sonnet 4.6, medium — one production change, precisely scoped, against a method every rendering pass calls

### Context this phase needs
**The original expectation was wrong, and a production change is required.** A run of this phase wrote the tests, confirmed tasks 1 and 3 pass, and found task 2 fails. `CrossLineTieSelectionTest` is on disk with `testSelectionReportsAsPresentOnBothLines` red; the other two tests pass. Do not re-derive this — verify it and fix it.

What the expectation missed: `isOnLine` is **not** the only line-scoped gate. `SelectionCoordinator.isSelected(HitTarget, int lineIndex)` opens with

```java
if (activeLineIndex != lineIndex) {
    return false;
}
```

which returns before the switch ever reaches `case HitTarget.Tie _ -> isSelectedTarget(target)`. `activeLineIndex` names exactly one line — `activateLine(lineIndex)` is called by every click handler with the clicked line's own index. Each line repaints with its own `LineInvariants` fixed to its own index (`LineRenderer.buildInvariants`), and `TieRenderer.determineTieColor` asks `isSelected(target, thatIndex)`. So for a tie clicked on line B, line A's repaint asks `isSelected(tieTarget, A)` and is short-circuited to false. Record equality never gets a chance to answer.

`TieRenderer.determineTieColor` (`:126`) calls `invariants.colorFor(new HitTarget.Tie(tie), …)`, and `HitTarget.Tie` is a record wrapping the `Tie`. A cross-line tie is **one** `Tie` object, so both lines do construct equal records — that part of the expectation held.

Hit regions are already correct — see phase 9's context. Do not touch `HitRegionBuilder`.

Deleting a selected tie is **out of scope**; it is unimplemented for every tie today and belongs to #682.

### Tasks
1. Fix `SelectionCoordinator.isSelected(HitTarget, int lineIndex)` so a cross-line tie reports as selected on **both** lines. Scope the change to `HitTarget.Tie` — every other `HitTarget` kind is genuinely single-line, and the `activeLineIndex` gate is correct for them. Do not remove the gate wholesale.

   Answer the tie case as "this is the selected target **and** this tie is in the line being asked about" — `isSelectedTarget(target)` combined with phase 1's `isIn` against the line at `lineIndex`. Line-scoping through derived parentage is the point: it must answer true for both halves and false for a line the tie is not in. Do not answer on record equality alone, which would report the tie as selected on every line in the song.

2. Keep the switch exhaustive and keep its Javadoc honest — the existing comment explains why there is no `default` arm, and the leading gate's behavior is now kind-dependent, which the doc should say.

3. `CrossLineTieSelectionTest` is already written and needs no new cases: clicking either half selects the same `Tie`; the selection reports as present on both lines; the selection survives a mutation on the other line rather than being cleared as stale. Change it only if a case is wrong, not to make a failure go away.

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieSelectionTest` (green), then `./scripts/test.sh unit` (green — `isSelected` is called by every rendering pass, so a regression here is broad).

* * *
## ✅ Phase 11: Clipboard
**Status:** Complete **BlockedBy:** 1, 4a **Files:** src/main/java/songscribe/ui/clipboard/Fragment.java, src/main/java/songscribe/ui/clipboard/ClipboardManager.java, src/test/java/songscribe/ui/clipboard/FragmentTest.java **Recommended model/effort:** Sonnet 4.6, low — one guard plus a user-facing message

### Context this phase needs
`Fragment.cloneSpans` (`:228-248`) copies a span only when **both** endpoints are inside the captured range:

```java
if (clonedAnchor != null && clonedEnd != null) {
    clonedSpans.add(span.copy(clonedAnchor, clonedEnd));
}
```

Partially-captured spans are dropped silently. That is invisible today because a tie is always wholly within one line; with cross-line ties, copying a range containing one half would silently discard a tie the user can see.

**Decision, revised after implementation.** An earlier revision of this plan said to *refuse* a capture that splits a tie. That was wrong, and it shipped briefly before being reverted. Copy must never refuse: a span is kept only when the range holds **both** its endpoints, and one with an endpoint outside is dropped from the fragment — ties included, exactly like beams and hairpins. Copying a range that clips a tie copies the elements the user selected and nothing more.

The refusal was wrong on two counts. It changed **same-line** copying, which has always dropped a clipped tie and is nothing to do with #493 — a plain same-line tie partially covered by the range has exactly one endpoint in it, so the refusal caught it too. And refusing an entire copy because a tie happens to hang off the edge of the selection is surprising: the user asked for those notes and should get those notes.

Paste needs no separate work: `ScoreViewController.java:1116-1123` already guarantees every clone is inserted before the first `addPastedSpan`, so a paste at a line boundary reaches the tie through phase 4's insertion rule like any other insertion.

Read [Strings](../.agents/guides/strings.md) before writing the message, and [OptionDialogs](../.agents/guides/option-dialogs.md) if it is presented as an alert.

**Completed, then reverted to no production change at all.** The refusal described above was implemented (`rangeSplitsATie`, an `OptionDialogs` warning, and the `alert.clipboard.tie.split` / `alert.title.clipboard.tie.split` keys) and then removed once the same-line consequence surfaced. `Fragment.cloneSpans`'s pre-existing rule — keep a span only when both endpoints are captured — already does what is wanted, so this phase ends with `Fragment.java` and `ClipboardManager.java` carrying only documentation changes.

How it was caught: three pre-existing tests had been rewritten from `Tie` to `Beam` so they would keep passing. Those tests existed to pin the drop-silently behavior, and switching their span type kept the suite green while the behavior underneath changed. They are now back on `Tie`.

`BlockedBy` names 4a because this phase's paste test asserts phase 4a's insertion rule; the original `BlockedBy: 1` told this phase to test behavior it did not depend on.

### Tasks
1. Confirm `Fragment.cloneSpans` already drops a tie with only one endpoint captured, and that `Fragment.capture` neither refuses nor returns null. No production behavior change is required.

2. Keep the rule uniform across span types. A tie gets no special case in the clipboard — `Span` is the level this is decided at.

3. Record the rule in `Fragment.capture`'s Javadoc and the class flow diagram, since the reverted refusal had documented the opposite.

4. Add to `FragmentTest`: capturing a range containing both endpoints copies the tie; capturing a range holding one endpoint drops the tie, still copies the selected elements, and still reaches the clipboard; capturing a range containing neither drops it too. Add one paste test confirming a paste landing between a cross-line tie's endpoints removes the tie by phase 4's rule.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit FragmentTest` (green).

* * *
## ✅ Phase 12: Persistence
**Status:** Complete **BlockedBy:** 1 **Files:** src/main/java/songscribe/io/musicxml/RangeSpanResolver.java, src/test/java/songscribe/io/musicxml/CrossLineTieRoundTripTest.java **Recommended model/effort:** Sonnet 4.6, medium — the write side already works, and phase 1 may make the read side work too

### Context this phase needs
MusicXML writes ties as per-note markers (`<tied type="start">` / `<tied type="stop">`, `MusicXmlNotationsWriter.java:87-98`), not as index pairs, so the write side needs no change for a tie whose notes are in different lines.

`RangeSpanResolver.resolveTie` (`:175-189`) holds `pendingTieStart` across notes and, on seeing the stop, runs `line.addTie(new Tie(pendingTieStart, element))` where `line` is the line current **at stop time**.

Phase 1 likely makes this correct with no change: `addTie` reaches `appendChild`, which adds the tie to the other line of the pair, and both endpoints are attached by then — `finishNote` calls `note.appendStaffElement(currentLine)` (`MusicXmlReader.java:771`) before `resolveTie` (`:787`). This phase's job is to prove that rather than assume it.

Note `MusicXmlSpanIndex.buildSpanIndex:208-209` reads a tie's raw index pair on the **write** side. Phase 6 audits it; confirm here that the round trip is correct end to end.

### Tasks
1. Verify a tie read back from MusicXML with endpoints in different lines lands in both lines' `spans`. If phase 1 already achieves it, record that and change nothing; if not, fix the reader.

2. Confirm the legacy `.mssw` reader is unaffected. `LineIO` resolves span endpoints by index within a single line (`:312-339`), so it cannot express a cross-line tie — confirm it degrades cleanly and never add a new persisted field to that path.

3. Add `CrossLineTieRoundTripTest`: a song with a cross-line tie writes and reads back with the tie attached to both lines and the same two endpoint elements; a same-line tie is unaffected; a chain of ties crossing the break survives.

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieRoundTripTest` (green).

* * *
## ✅ Phase 14: MusicXML Write-Side Tie Markers
**Status:** Complete **BlockedBy:** — **Files:** src/main/java/songscribe/io/musicxml/MusicXmlSpanIndex.java, src/test/java/songscribe/io/musicxml/CrossLineTieRoundTripTest.java **Recommended model/effort:** Opus 4.8, medium — a silent export corruption that the existing round-trip test cannot see

### Context this phase needs
**A seventh tie-reading site, missed by phase 6a's table.** Phase 12's context above already said *"`MusicXmlSpanIndex.buildSpanIndex` reads a tie's raw index pair on the **write** side. Phase 6 audits it"* — but phase 6a's six-site table never listed it, so nobody did. This phase closes that gap. It is the same defect the audit exists to find, one site wider than the table's wording.

`MusicXmlSpanIndex.buildSpanIndex` buckets ties with:

```java
for (var tie : line.findTies()) {
    var anchorIdx = tie.getAnchorElementIndex();   // the ANCHOR's own line
    var endIdx = tie.getEndElementIndex();         // the END's own line

    if (!indicesInRange(anchorIdx, endIdx, count)) {
        continue;
    }

    builders[anchorIdx].tieStart = tie;
    builders[endIdx].tieStop = tie;
}
```

`indicesInRange` (`:130-132`) is `anchorIdx >= 0 && endIdx >= 0 && anchorIdx < count && endIdx < count`. It does **not** require `anchorIdx <= endIdx`.

A cross-line tie is in **both** lines' `findTies()`, and the two indices resolve through two different lines. So each line writes **both** markers into its own array: line A emits a spurious `<tied type="stop"/>` at its own index `endIdx`, and line B a spurious `<tied type="start"/>` at its own index `anchorIdx`, whenever that line has enough elements. The exported file carries unpaired `<tied>` elements, and on re-read the reader can pair a spurious start with a later real stop — inventing a tie nobody wrote and dropping the real one.

**Why `CrossLineTieRoundTripTest` passes anyway.** Its fixtures are one and two notes per line, so the spurious markers land where `MusicXmlReader`'s `pendingTieStart` overwrite masks them. The test is not wrong; it is too small to reach the bug.

The other span types in this method are unaffected: only ties can be cross-line, and beams, tuplets and trills iterate `for (var i = anchorIdx; i <= endIdx; i++)`, which is a no-op on an inverted pair.

### Tasks
1. Resolve the tie pair **receiver-relative** so each line emits only its own half's marker — through `line.anchorIndexOf` / `line.endIndexOf` and the `SpanBound` type from phase 2, matching how phases 6a and 6b converted their sites. A half whose far endpoint is off this line writes one marker, not two.

2. Leave the beam, tuplet, trill, hairpin and ending branches alone. State in a comment why the tie branch alone needs receiver-relative resolution, so the next reader does not "fix" the others to match.

3. Extend `CrossLineTieRoundTripTest` with a fixture that actually reaches the defect: the anchor at a **high** index in line A, and line B holding at least that many elements, so a spurious start would have somewhere to land. Assert the written MusicXML has exactly one `<tied type="start"/>` and one `<tied type="stop"/>` for the tie, and that the re-read song has the same single tie across the same two endpoints.

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieRoundTripTest` (green), then `./scripts/test.sh unit` (green).

* * *
## ✅ Phase 13: Full Gate
**Status:** Complete **BlockedBy:** 14 **Files:** src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/ui/action/ToggleNotationAction.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/test/java/songscribe/e2e/CrossLineTieTest.java, src/test/resources/fixtures/cross-line-tie.musicxml, src/test/java/songscribe/layout/CrossLineTieLayoutTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerTest.java, src/test/java/songscribe/dom/TieInvalidationTest.java **Recommended model/effort:** Opus 4.8, medium — judges whether the halves behave as one tie across every path

### Context this phase needs
The failure this plan risks is a **half-tie**: one line holding a tie the other has dropped, which renders as an arc running off the edge to nothing.

Running the app requires user permission — `./scripts/run.sh` must never be executed without it.

### What task 4 found
Task 4 was carried out as `CrossLineTieTest`, an e2e class driving the real Swing pipeline with robot clicks over a two-line fixture whose first line is full — 28 crotchets, where natural spacing runs out at that line width — because a full line is the only shape this feature occurs in. It found four defects that every unit phase had missed, each because the unit tests entered below the layer that was broken. The fourth was found by looking at the running app, not by the e2e test.

**The feature was unreachable from the UI.** `ToggleNotationAction`'s tie action carried `Flag.REQUIRES_MULTIPLE_SELECTION`. `UIAction.enableFromSelectionSize` is checked *before* `canToggle`, so a single-element boundary selection — the only selection phase 7a's lookup accepts — was rejected before the lookup ever ran. Phase 7a's task 2 said to enable the action for a single-element selection; the predicate was converted and the flag was not, and `CrossLineTieToggleTest` could not see it because it calls the predicate directly. The tie action now takes `Flag.REQUIRES_SELECTION`, with `RangeQueries.canToggleTie` as the real gate; beaming keeps the stricter flag.

**Appending to the anchor's line invalidated nothing.** `Line.addElement(StaffElement)` duplicated `addElement(int, StaffElement)`'s `applyChange` instead of delegating to it, so the append path ran no `isInvalidatedByInsertion` sweep, no tuplet removal and no initial-tempo displacement. This was invisible while every span ended inside its own line — nothing appended past the last element could land between a span's endpoints. A cross-line tie breaks that: everything after its anchor is between the two notes, and appending is exactly how something gets there. `TieInvalidationTest` passed throughout because it calls `addElement(index, element)`. The append overload now resolves its index and delegates.

**Only one half was ever repainted.** `ScoreViewController.songDidChange` invalidates the cached `LayoutResult` of the single line a line-scoped mutation names, and `TieAddition`/`TieRemoval` name one line. For a cross-line tie both lines' rendering changes — one gains the half running off its right edge, the other the half entering from the left — so the far line kept a stale layout and never drew its half. This is the half-tie the plan exists to prevent, arriving by way of the repaint rather than the model, and it was found by looking at the running app: every automated test called `performLayout` on both lines first, which is exactly what hides it. The branch now also invalidates any line holding an endpoint of a span the batch names — through the endpoint elements rather than `Span.isIn`, since on a removal the span is already out of both lists. `ScoreViewControllerTest.SongDidChangeHandling` covers both directions.

**The continuation half was drawn across the staff header.** Phase 9b took LilyPond's `staff_extent` literally and started the open-start half at X 0. LilyPond's staff extent excludes the clef and key signature, which live in a prefix outside the staff; SongScribe draws them on the staff itself, so X 0 is behind them and the arc was drawn over the clef. The half now starts at `HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line)`, and `CrossLineTieLayoutTest` pins it.

**Both open ends were bounded by the wrong thing, and neither took the endpoint gap.** The same misreading of `staff_extent` ran to the other end of the arc. LilyPond bounds the anchor half's open end with `iv[LEFT]` of the closing break column's elements — the **left edge of the closing barline** — falling back to the column's own X only when that column is empty, which is what makes a line ending in no barline run to the line edge. SongScribe ran to `staffRightMarginSs` unconditionally, drawing the arc through the barline glyph; the terminal barline in particular is laid out flush right (`ElementType.terminalFlushRightXSs`), so the two shared pixels. This is reachable because phase 4a deliberately allows a barline between the endpoints — a legal separator does not break the tie. Both limits now come from the line's own edges (`LayoutEngine.lineEndTieLimitXSs`).

Separately, neither open end took the `note-head-gap`. LilyPond applies it in one unconditional step once both attachments are resolved — `conf->attachment_x_.widen (-details_.x_gap_)` (`tie-formatting-problem.cc:580`), `x_gap_` being `note-head-gap`, default 0.2 — with no test for whether an end is a notehead or a break column. So an open end stands off the header and the barline by exactly what an attached end stands off its notehead. Both open ends now take `NOTE_HEAD_GAP_SS`.

`CrossLineTieLayoutTest` covers both, and its fixture was corrected while doing so: it kept `Song`'s auto-added terminal barline on the first line, so its anchor was never that line's last element — not the shape a cross-line tie is ever created in.

### Not covered by the e2e test
- **The playing-tie highlight** across both halves. Driving MIDI playback and sampling the highlight mid-play is inherently timing-dependent; `LineTrackBuilder` is unit-tested by phase 6a.
- **The `.musicxml` round trip.** `E2ETest.roundTrip` goes through `SongIO`, the legacy `.mssw` path, which cannot express a cross-line tie at all. `CrossLineTieRoundTripTest` covers the real format.
- **Copying a range that clips the tie.** Covered by `FragmentTest` and `PasteSpanReconciliationTest`. The one thing e2e would add is "no alert appears", and an alert in e2e hangs the run rather than failing it.

The full e2e suite was run after the three production changes and is green (67 passed) — the action-flag and `Line.addElement` changes are shared paths that `ElementInsertionTest`, `SelectionTest` and `NoteConnectionTest` exercise.

### Tasks
1. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green). Fix any failure here before anything else.

2. Walk the mutation inventory in the contract and confirm every row has a test. No path may leave a tie in one line's `spans` and not the other's.

3. Confirm the derived parentage cannot lie: after any mutation, `span.isIn(L)` ⟹ `L.spans` contains `span`, including after `Song.removeLine`.

4. Ask the user for permission, then drive the app. Create a cross-line tie in both directions; confirm both halves render and meet the staff edges; insert a note and then a barline between the endpoints; delete a note under each half; delete a whole line; pitch-shift each endpoint; click each half and confirm both draw selected; play the song and confirm the playing-tie highlight follows both halves; undo and redo each of those; copy a range that clips the tie and confirm the notes copy with the tie dropped and no alert; and round-trip through `.musicxml`.

   Done as `CrossLineTieTest` (e2e) over `cross-line-tie.musicxml`, plus the three production fixes recorded above. See *What task 4 found* and *Not covered by the e2e test*.

5. Confirm same-line ties, beams, tuplets, hairpins, trills and endings are unchanged by this work — every one of them reads the accessors phase 2 rewrote and the parentage phase 1 replaced.

* * *
## Verification (whole plan)
- `./scripts/compile.sh` reports SUCCESS.
- `./scripts/test.sh unit` is green, with no test disabled or weakened to get there.
- A cross-line tie is present in both lines' `getSpans()`, and no path leaves it in one.
- A `Span` is never attached: its `getParentLine()` is null for its whole life, and `span.isIn(L)` ⟹ `L.spans` contains `span` after every mutation.
- `Span.isIn` allocates nothing and there is no `lines()` method.
- Line A reports `(At(anchorIndex), AFTER_LINE)` and line B `(BEFORE_LINE, At(endIndex))`.
- A tie whose far endpoint was deleted, or whose far line is no longer in the song, reports `ABSENT` — not a direction.
- No arithmetic or indexing runs on a bound that is not `At`.
- `Line.getElementIndex` is unchanged and `elementIndexMap` contains only this line's elements.
- `Span.getAnchorElementIndex()` / `getEndElementIndex()` are unchanged, and every site that compares the pair on a **tie** has been audited.
- Inserting anything that is not a legal separator between the endpoints removes the tie; a barline or repeat does not — for same-line ties as well as cross-line ones.
- Deleting either line, or inserting a line between them, removes the tie from the survivor, and undo-redo-undo returns to the identical state.
- Pitch-shifting one endpoint moves the other.
- Both halves render, meeting the staff edges at the tie's baseline, sharing one arc direction, each a complete arc over its own width — asserted, not only observed.
- Clicking either half selects the whole tie and both halves draw selected.
- The playing-tie highlight fires for elements under both halves.
- Copying a range that holds only one of a tie's two notes copies those notes and drops the tie, with no alert and no refusal — for same-line ties as well as cross-line ones.
- A cross-line tie survives a `.musicxml` round trip attached to both lines.

## Out of scope — recorded, not done
- **Deleting a selected tie.** Unimplemented for every tie today; belongs to #682, which covers direct removal of `LineElement`s by key. A cross-line tie adds one requirement there: the deletion must remove both halves through the tracked path so undo restores both.
- **Cross-line beams, hairpins, tuplets, trills and endings.** The bound type and the derived parentage generalize to them, but the enable logic, geometry and merge behavior are per-type work. No current plan to support them.
- **Refusing a copy that clips a tie.** Tried and reverted — see phase 11. A clipped tie is dropped, like every other span type.
- **Reflow.** No line-splitting or line-merging exists; a cross-line tie arises only from the phase 7 toggle or from a file that already contains one.
- **Ties spanning more than two lines.** LilyPond notes this case as unhandled too (`tie.cc:74-78`).
- **Converting the ~106 `getAnchorElementIndex`/`getEndElementIndex` call sites.** Endpoint-own-line resolution is correct for every non-tie span. Only tie-reading sites are audited, in phase 6.
