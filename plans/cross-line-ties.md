# Cross-Line Ties (issue #493)
**Type:** Master plan **Created:** 2026-08-02 **Branch:** TBD (base: `develop`) **Status:** Pending

* * *
## Goal
Support a tie whose two notes sit in different lines: the anchor on the last element of line A, the end on the first element of line B.

The tie is **one** `Tie` **object present in both lines'** `spans` **lists** — half on each. Each line resolves the endpoint it owns to a real index and the far endpoint to a sentinel meaning "off this line's edge", so each line draws its own half running to the staff edge.

```
  line A:  … ● ● ●─────►        anchorIndexOf = 7,               endIndexOf = END_OF_LINE
  line B:  ◄─────● ● …          anchorIndexOf = BEGINNING_OF_LINE, endIndexOf = 0
```

* * *
## Contract every phase is written against

### Parentage: derived from the endpoints, present in both lists
**Decided — do not re-open.** `Span` stops carrying a stored parent line. A span's lines are derived:

```
span.lines() == { anchorElement.getParentLine(), endElement.getParentLine() } \ { null }
```

- `Span.getParentLine()` returns the **anchor's** line — the primary, for the one caller that needs a single answer.
- `Span.isIn(Line)` answers membership, true for **both** lines of a cross-line tie. O(1), two reference comparisons.
- The span is **physically present in both lines'** `spans` **lists**, so every existing read of a line's span list keeps working unchanged.
- `Line.appendChild` (`Line.java:322`) adds the span to `this` line **and** to every other line in the derived set. `removeChild` (`:331`) removes it from all of them. They remain the only writers of `spans`.

Adding to `this` line unconditionally — rather than only to the derived set — preserves today's behavior exactly when both endpoints are detached, so nothing regresses. The invariant is therefore one-directional:

> `span.isIn(L)` ⟹ `L.spans` contains `span`

`StaffElement` is untouched: it keeps the stored `parentLine` field and the existing two-way invariant.

**Why not a stored set of parent lines.** Three structures would have to agree (list A, list B, the set); undo would have to restore all three; and the set would hold a strong reference to a `Line` that `Song.removeLine` had already dropped from the song, with no invariant able to catch it.

**Why not a single owning line with the far line deriving its half.** Line B's `spans` list would not contain the tie, so every sweep in line B would silently miss it — the deletion sweeps (`Line.java:600, 646`), the insertion sweep (`:388`), and `setElement`'s re-pointing (`:444`) — and `LayoutEngine.calculateTies` iterating `line.findTies()` would draw nothing for line B. The failure mode is silence at a dozen read sites.

### Verification already performed — do not repeat
The derivation is only safe if no site attaches a span to a line before its endpoints are in one. Every add site was checked:

| Path | Finding |
| --- | --- |
| Clipboard `Fragment.cloneSpans` (`Fragment.java:227-248`) | Builds `span.copy(...)` into a plain list; never calls a `Line` add method. Endpoints are clones in no line → derived set empty, matching today's `parentLine == null`. `Span.copy`'s doc (`Span.java:309`) states this. |
| MusicXML notes | `finishNote` calls `note.appendStaffElement(currentLine)` (`MusicXmlReader.java:770`) → `line.addElement` (`NoteAccumulator.java:598`) **before** `resolveBeam`/`resolveTie`/`resolveTuplet`/`resolveTrill`/`resolveWedge` (`:786-790`). The `addElement` at `MusicXmlReader.java:820` is a different method (`appendToCurrentLine`, for barlines) and is not a later insert of the note. |
| MusicXML endings | `EndingResolver.buildEnding` documents "Both endpoints are `StaffElement`s already appended to the line." |
| Legacy `.mssw` `LineIO` | Spans built at end-of-line from `line.getElement(pair[0])`/`getElement(pair[1])` — endpoints fetched out of the line, so necessarily attached. |
| Paste | `ScoreViewController.java:1116-1123` is an explicit "Hard ordering constraint: every clone must be inserted before the first `addPastedSpan`", justified by span indices resolving through the anchor's own line. This path already depends on endpoint-derived resolution. |
| UI (`MusicEditOperations`, `TrillAction`, `SelectionCoordinator`) | Endpoints come from `line.getElement(...)`. |
| Undo replay | `Line.java:641`: "Companion removals precede the primary deletion so reverse-order undo re-inserts the elements before re-adding the spans anchored to them." Same engineering at `setElement` (`:445-447`). The element is re-attached before `addTie` runs. |

**Conclusion:** no site attaches a span to a line neither endpoint belongs to.

### Sentinels
Declared on `SpanLookup` (an interface, so no modifiers):

| Constant | Value | Meaning |
| --- | --- | --- |
| `NOT_IN_LINE` | `-1` | No position: the endpoint is unset, or attached to no line |
| `BEGINNING_OF_LINE` | `-2` | The endpoint is in an earlier line; this half enters from the left edge |
| `END_OF_LINE` | `-3` | The endpoint is in a later line; this half exits through the right edge |

`-1` keeps its existing meaning — `ArrayList.indexOf`'s "absent" — because `Line.getElementIndex`, `Span.indexInLine` and every existing `>= 0` guard already read it that way.

`Line.getElementIndex` **does not change.** It answers "where is this element in this line", and `elementIndexMap` indexes only this line's own `elements`. A far endpoint is not in that map and must never be added to it. Sentinels are produced by the `SpanLookup` accessors, which know _which endpoint_ was asked for.

**Direction comes from which endpoint, not from line ordering.** An anchor is by definition the earlier endpoint, so an anchor attached somewhere other than this line is in an earlier line. No line-order comparison is needed.

**A detached endpoint stays distinct from a far one.** A deleted endpoint is `NOT_IN_LINE`; an endpoint attached to another line is a directional sentinel. Without that distinction a tie whose note was deleted would draw as entering from the edge.

**Arithmetic on an index is invalid until the sentinels are excluded.** `Line.java:986, 991` computes `end + reach` and `anchor - reach`. Every such expression must reject sentinels first — there is no sentinel value that makes the arithmetic work by itself.

### The mutation inventory
Every way a cross-line tie can be affected, and which phase owns it. Rows marked *pre-existing* are broken for same-line ties today; fixing them is in scope because a cross-line tie makes the breakage visible and unavoidable.

| Mutation | Today | Phase |
| --- | --- | --- |
| Delete an endpoint (either line) | `Span.isInvalidatedBy`, swept per-line; removes from one list only | 4 |
| Range-delete containing an endpoint | Same | 4 |
| Replace an endpoint (`setElement`) | Re-points anchor/end refs (`Line.java:460-468`) | 4 |
| Insert note/rest between the endpoints | **Pre-existing bug:** `Tie` never overrides `isInvalidatedByInsertion`, so nothing removes the tie | 5 |
| Insert barline/repeat between the endpoints | Allowed — falls out of the same override | 5 |
| Delete a whole line | **`Song.removeLine` does no span cleanup at all** | 6 |
| Insert a line between the two lines | Endpoints stop being adjacent; nothing notices | 6 |
| Toggle tie at a line boundary | Not offered | 7 |
| Click a tie arc | Selects via `isOnLine`, single-line only | 8 |
| Delete a selected tie | Not implemented | 8 |
| Pitch-shift an endpoint | `PitchShifter` expands the tie chain by single-line index range | 9 |
| Render both halves | `resolveSpan` returns null; the tie is skipped and not drawn | 10 |
| Copy a range splitting the tie | `Fragment.cloneSpans` drops it silently | 11 |
| Paste at the boundary | Reaches insertion through `addPastedSpan` | 11 |
| MusicXML round trip | Reader attaches to the stop's line only | 12 |
| Undo/redo of all of the above | `attach`/`detach` are the recorded chokepoint | every phase |

Not applicable: there is no transpose, key-change, line-split, line-merge or reflow path in the codebase. Replacing a note with a rest is forbidden by the UI.

* * *
## Status Dashboard
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1 | [Derived Span Parentage](#-phase-1-derived-span-parentage) | ⏳ Pending | Opus 4.8, high |
| 2 | [Endpoint Sentinels](#-phase-2-endpoint-sentinels) | ⏳ Pending | Sonnet 4.6, medium |
| 3 | [Receiver-Relative Accessors](#-phase-3-receiver-relative-accessors) | ⏸️ Blocked by 1, 2 | Opus 4.8, high |
| 4 | [Element Deletion and Replacement](#-phase-4-element-deletion-and-replacement) | ⏸️ Blocked by 3 | Opus 4.8, medium |
| 5 | [Insertion Between the Endpoints](#-phase-5-insertion-between-the-endpoints) | ⏸️ Blocked by 3 | Opus 4.8, medium |
| 6 | [Line Structure Mutations](#-phase-6-line-structure-mutations) | ⏸️ Blocked by 1 | Opus 4.8, medium |
| 7 | [Creating a Cross-Line Tie](#-phase-7-creating-a-cross-line-tie) | ⏸️ Blocked by 1 | Sonnet 4.6, medium |
| 8 | [Selecting and Deleting the Tie](#-phase-8-selecting-and-deleting-the-tie) | ⏸️ Blocked by 1, 10 | Opus 4.8, medium |
| 9 | [Pitch Shift Across the Break](#-phase-9-pitch-shift-across-the-break) | ⏸️ Blocked by 1 | Sonnet 4.6, medium |
| 10 | [Rendering the Two Halves](#-phase-10-rendering-the-two-halves) | ⏸️ Blocked by 3 | Opus 4.8, high |
| 11 | [Clipboard](#-phase-11-clipboard) | ⏸️ Blocked by 1 | Sonnet 4.6, low |
| 12 | [Persistence](#-phase-12-persistence) | ⏸️ Blocked by 1 | Sonnet 4.6, medium |
| 13 | [Full Gate](#-phase-13-full-gate) | ⏸️ Blocked by all | Opus 4.8, medium |

* * *
## ⏳ Phase 1: Derived Span Parentage
**Status:** Pending **BlockedBy:** — **Files:** src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/test/java/songscribe/dom/ParentLinePropagationTest.java, src/test/java/songscribe/dom/SpanParentageTest.java **Recommended model/effort:** Opus 4.8, high — changes a documented invariant that `attach`/`detach` and undo replay both depend on

### Context this phase needs
`LineElement.java:73-77` documents `parentLine == L ⟺ that list of L contains this`. `parentLine` (`:79`) has a package-private setter (`:167`) written only by `Line.attach` (`:288`) and `Line.detach` (`:299`).

Span parentage is barely consulted. Exactly **two** production callers ask a `Span` (rather than a `StaffElement`) for its line:

- `Line.detach`'s re-parent guard (`:307`) — `element.getParentLine() != this` returns early.
- `SelectionCoordinator.isOnLine` (`:590`) — documents "every root kind — staff element or span — answers through the same field", and is a hot path: it runs after every mutation and deliberately replaced an O(n) `elements.indexOf` scan.

Everything else in the caller list is a `StaffElement`. `Span.indexInLine` (`:229`) reads the *endpoint's* line, not the span's.

`isOnLine` is the "click selects both halves" requirement in disguise: it must answer true for a cross-line tie asked about *either* line, which a single field cannot do.

### Tasks
1. Remove the stored parent line for spans. Add `Span.lines()` (or an equivalent private derivation) and `Span.isIn(Line)`, both computed from `anchorElement.getParentLine()` / `endElement.getParentLine()`. Override `getParentLine()` on `Span` to return the anchor's line.

2. Rewrite `Line.appendChild` (`:322`) to add the span to `this` line and to every other line in the derived set, and `removeChild` (`:331`) to remove it from all of them. These stay the only writers of `spans`.

3. Rewrite `detach`'s guard (`:307`) for the multi-line case. It exists to make re-parenting order-independent, so whatever replaces it must keep that property.

4. Change `SelectionCoordinator.isOnLine` (`:590`) to use `isIn` for spans. Keep it O(1) — its Javadoc explains why, and that reasoning still holds.

5. Widen `removeInvalidatedSpan`'s documented "no-op when the span is no longer in this line" contract (`Line.java:1552-1556`) to "no longer in any of its lines", and make the per-type removals honor it.

6. Update the `LineElement.java:44-77` diagram and invariant text: `StaffElement` keeps the two-way stored invariant; `Span` gets the one-directional derived one from the contract above.

7. Update `ParentLinePropagationTest`. The `WhenSpanAddedOrRemoved` assertions (`:258-309`) pass unchanged if `getParentLine()` returns the anchor's line. `testAttachingASpanToAnotherLineBeforeRemovalWins` (`:311-324`) encodes the behavior being removed — under derivation, attaching does not *move* a span — so it must be replaced by a test of the new rule rather than deleted.

8. Add `SpanParentageTest`: a tie whose endpoints are in two lines is in both `getSpans()` lists and `isIn` is true for both; `getParentLine()` is the anchor's line; a tie with both endpoints detached belongs to no line; detaching one endpoint drops that line from the derived set; undo and redo of the attach and the detach preserve all of it.

9. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — this touches parentage for every span type).

* * *
## ⏳ Phase 2: Endpoint Sentinels
**Status:** Pending **BlockedBy:** — **Files:** src/main/java/songscribe/dom/SpanLookup.java, src/main/java/songscribe/dom/Span.java, src/test/java/songscribe/dom/SpanPredicateSentinelTest.java **Recommended model/effort:** Sonnet 4.6, medium — three constants and three predicates, but each predicate needs its sentinel case reasoned through

### Context this phase needs
The three predicates, all in `Span.java`:

```java
// :279  containing
anchorIndex >= 0 && endIndex >= 0 && anchorIndex <= elementIndex && elementIndex <= endIndex

// :292  overlapping — deliberately unguarded today
anchorIndex <= end && endIndex >= begin

// :299  exactly
spanAnchorIndex == anchorIndex && spanEndIndex == endIndex
```

`containing`'s `>= 0` guard currently means "has a position". With sentinels it would reject every cross-line half as though it were detached.

### Tasks
1. Declare `NOT_IN_LINE`, `BEGINNING_OF_LINE` and `END_OF_LINE` on `SpanLookup` with the contract's values, each with Javadoc stating what it means for a caller.

2. `containing`: replace the `>= 0` guard with a `NOT_IN_LINE` rejection, and treat `BEGINNING_OF_LINE` as "before every index" and `END_OF_LINE` as "after every index". A cross-line half must report **true** for every element it passes over, not just its attached endpoint.

3. `overlapping`: apply the same unbounded reading. It is unguarded against `NOT_IN_LINE` today; keep that behavior unless task 5's tests show it produces a false match for a detached span, in which case report it rather than changing it here.

4. `exactly`: a cross-line half must not match a query for a span genuinely anchored at this line's first or last element. Compare sentinels as distinct values — never coerce them to `0` or `lastIndex`.

5. Add `SpanPredicateSentinelTest` driving each predicate directly with every combination of real index, `NOT_IN_LINE`, `BEGINNING_OF_LINE` and `END_OF_LINE` on each side. These are pure functions, so no `Line` fixture is needed.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit SpanPredicateSentinelTest SpanLookupTest` (green).

* * *
## ⏳ Phase 3: Receiver-Relative Accessors
**Status:** Pending **BlockedBy:** 1, 2 **Files:** src/main/java/songscribe/dom/Line.java, src/test/java/songscribe/dom/SpanLookupTest.java **Recommended model/effort:** Opus 4.8, high — every span query in the app reads these two methods, and the merge arithmetic silently misreads a sentinel

### Context this phase needs
`Line.anchorIndexOf` and `endIndexOf` are each a single call to `getElementIndex(span.getAnchorElement())` / `getElementIndex(span.getEndElement())`. `getElementIndex` is `@Nullable`-accepting and returns `NOT_IN_LINE` for a null element and for any element not in this line, with no direction. The contract those two implement is stated on `SpanLookup.anchorIndexOf` — extend it there when the sentinels gain direction, not only on the `Line` overrides.

`mergeOverlappingSpans` (`:972`) reads both accessors at `:978-979` and then computes `end + reach` and `anchor - reach` at `:986` and `:991`. `reach` is `0` for beams and `SPAN_ADJACENCY_REACH` for hairpins. Ties never merge, but these predicates receive sentinels as soon as the accessors can return them.

### Tasks
1. Rewrite both accessors to the shape in the contract:

  ```java
  @Override
  public int anchorIndexOf(Span span) {
      var anchor = span.getAnchorElement();

      if (anchor == null || anchor.getParentLine() == null) {
          return NOT_IN_LINE;
      }

      var index = getElementIndex(anchor);

      // Attached, but not here: an anchor is the earlier endpoint, so it's behind us.
      return index >= 0 ? index : BEGINNING_OF_LINE;
  }
  ```

  `endIndexOf` mirrors it with `END_OF_LINE`.

2. Assert the direction inference rather than trusting it: a span in this line's `spans` whose anchor is attached elsewhere must have that anchor in an earlier line. Use the project's existing failure mechanism for a broken invariant; do not add a silent fallback.

3. Guard the merge arithmetic at `:986` and `:991`. A candidate span with a sentinel endpoint has no finite bound to add `reach` to — exclude it from absorption before the arithmetic runs. Leaving it would let a wrapped or negative bound swallow an unrelated span.

4. Leave `Span.getAnchorElementIndex()` / `getEndElementIndex()` (`Span.java:243, 251`) alone. They resolve through each endpoint's own line, which is what phases 9 and 10 need and what `toIndexString()` (`:266`) uses for the legacy `.mssw` writer.

5. Extend `SpanLookupTest`: for a tie attached to two lines, line A reports `(anchorIndex, END_OF_LINE)` and line B reports `(BEGINNING_OF_LINE, endIndex)`; each line reports the tie as containing every element from its attached endpoint to its edge; and a tie whose far endpoint has been deleted reports `NOT_IN_LINE`, not a sentinel.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — every span query reads these).

* * *
## ⏳ Phase 4: Element Deletion and Replacement
**Status:** Pending **BlockedBy:** 3 **Files:** src/main/java/songscribe/dom/Line.java, src/test/java/songscribe/dom/CrossLineTieDeletionTest.java **Recommended model/effort:** Opus 4.8, medium — the failure is a span left pointing at a detached element, which no current test would catch

### Context this phase needs
`removeRange` (`:626`) sweeps **this line's** `spans` at `:644-649`:

```java
var invalidated = spans.stream()
    .filter(r -> !(r instanceof Hairpin))
    .filter(r -> r.isInvalidatedBy(deletedElements)
        || r.isInvalidatedByDeletion(deletedElements, this))
    .toList();
invalidated.forEach(this::removeInvalidatedSpan);
```

`Span.isInvalidatedBy` (`Span.java:119`) tests whether either endpoint is among the deleted elements. `removeElement` and the ending sweep at `:390` run equivalent sweeps.

Because phase 1 puts the tie in both lists, both lines' sweeps now see it. What still needs proving is that removal reaches both lists and that undo restores both.

`setElement` (`:444`) is the easy case and is worth a test rather than a change: its re-pointing loop (`:460-468`) walks this line's `spans` swapping `oldElement` for the replacement, and since the tie is one object in two lists, a single pass fixes both halves.

### Tasks
1. Confirm removal of an invalidated cross-line tie reaches **both** lines through `removeChild`, via the same tracked-modification path the single-line case uses, so undo restores both halves.

2. Cover every sweep site, not only `removeRange`: `removeElement`, `setElement` (`:444`), and the ending sweep at `:390`.

3. Add `CrossLineTieDeletionTest`: delete the anchor note in line A, assert the tie is gone from both lines; delete the end note in line B, same; range-delete a run containing one endpoint, same; replace an endpoint via `setElement` and assert the surviving tie points at the replacement in both lines; and assert undo restores the tie into both lines and redo removes it from both.

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).

* * *
## ⏳ Phase 5: Insertion Between the Endpoints
**Status:** Pending **BlockedBy:** 3 **Files:** src/main/java/songscribe/dom/Tie.java, src/test/java/songscribe/dom/TieInsertionInvalidationTest.java **Recommended model/effort:** Opus 4.8, medium — fixes a pre-existing bug for same-line ties as well, so it touches behavior users already have

### Context this phase needs
`Span.isInvalidatedByInsertion` (`Span.java:127`) defaults to `false`, and `Ending` (`Ending.java:376`) is the **only** subclass that overrides it. `Tie` overrides nothing invalidation-related — only geometry (`arcSign`, `getSpanWidthSs`, `isAbove`, `getContentHeightSs`). So no insertion removes a tie today, of any kind. This is a pre-existing bug for same-line ties; a cross-line tie makes it unavoidable.

The desired rule: inserting a **note or rest** between the two tied notes invalidates the tie; inserting a **barline or repeat** does not, since a tie across a barline is ordinary notation.

For a cross-line tie the "between" region is unusually narrow and must be stated explicitly: it is *append to the end of line A* or *insert at index 0 of line B*. Nothing else in either line lies between the endpoints. Note that "index 0 of line B" is not a plain index test when line B's first element hosts a grace note — `isInsideGraceHostPair`, `precedingGraceNoteIndex` and `nearestNonGraceIndex` already define that boundary and should be reused rather than re-derived.

The sweep that will call the override is `Line.java:388`, which passes `(insertedIndex, insertedType, this)` — so the override receives the receiving line and must use the phase 3 accessors to place itself in it.

### Tasks
1. Override `isInvalidatedByInsertion` on `Tie`. Invalidate when the inserted element's type is a note or a rest and the insertion point falls strictly between the tie's two endpoints as resolved **in the line being asked**; do not invalidate for barlines or repeats.

2. Handle the sentinel cases: in line A the end index is `END_OF_LINE`, so anything inserted after the anchor is between the endpoints; in line B the anchor index is `BEGINNING_OF_LINE`, so anything inserted before the end is. Reuse the phase 2 predicates rather than writing new index arithmetic.

3. Use the existing grace-host helpers for the line B boundary rather than testing `index == 0`.

4. Add `TieInsertionInvalidationTest` covering the same-line case as well as the cross-line one: inserting a note between two tied notes removes the tie; inserting a rest removes it; inserting a barline or repeat leaves it; inserting outside the endpoints leaves it; for a cross-line tie, appending a note to line A or inserting one at the head of line B removes it from **both** lines, and a barline in either position leaves it; undo restores it into both.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — this changes same-line tie behavior, so any existing test that inserts near a tie may move).

* * *
## ⏳ Phase 6: Line Structure Mutations
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/dom/Song.java, src/test/java/songscribe/dom/CrossLineTieLineStructureTest.java **Recommended model/effort:** Opus 4.8, medium — `removeLine` has no span handling at all, and the derived parentage depends on it gaining some

### Context this phase needs
`Song.removeLine` (`:1193`) removes the line from `lines` and maintains the terminal-barline invariant. It performs **no span cleanup and detaches no elements.** It is reachable from a menu command (`ScoreViewController.java:657`) and from undo/redo replay (`MutationReplayer.java:110, 152`).

That is the sharpest edge in this plan. After deleting line B, the tie's end element still reports B as its parent line, so the derived set names a line no longer in the song — the derivation would be lying, and line A would hold a tie running off the edge to nothing.

`Song.addLine(int index, Line)` (`:1130`) inserts a line. Inserting between the two tied lines leaves the endpoints non-adjacent, which no cross-line tie can represent.

### Tasks
1. Make `removeLine` remove every span anchored in the deleted line from the lines that survive it, through the tracked path so undo restores them. Deciding whether that is best expressed as detaching the deleted line's elements or as an explicit span sweep is part of this phase — state the choice and its reason in this section.

2. Make `addLine(int, Line)` invalidate any cross-line tie whose two endpoint lines are no longer adjacent after the insertion.

3. Confirm both behave correctly under replay. `MutationReplayer` reaches `removeLine` for both `LineInsertion` undo and `LineDeletion` redo, so a cleanup that is not replay-aware will double-apply.

4. Add `CrossLineTieLineStructureTest`: deleting line A removes the tie from line B and vice versa; undo restores the line and the tie into both; inserting a line between the two tied lines removes the tie; undo restores it; and a same-line tie in an unrelated line is untouched by any of it.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).

* * *
## ⏳ Phase 7: Creating a Cross-Line Tie
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/test/java/songscribe/ui/selection/CrossLineTieToggleTest.java **Recommended model/effort:** Sonnet 4.6, medium — the enable predicate has four preconditions and a mirror case

### Context this phase needs
From issue #493: selecting the **last** element of a line enables the toggle-tie action when that element is a pitched note and the **first** element of the next line is a pitched note at the same pitch. Selecting the **first** element of a line is the mirror case, pairing with the previous line's last element.

`Selection.Range` (`Selection.java:70`) is `(Line line, int begin, int end, int anchor)` — bound to one line. This feature needs no cross-line selection: the selection stays a single element and the partner note is found in the adjacent line.

`toggleTie` (`MusicEditOperations.java:252-269`) takes `range.begin()`/`range.end()` as the two tie notes, calls `line.findExactTie(beginIndex, endIndex)` and either adds or removes. `RangeQueries.java:159` decides whether the action shows as connect or disconnect.

Phase 1 makes attachment automatic: `line.addTie(tie)` on either line puts it in both lists, because `appendChild` adds to every line in the derived set.

### Tasks
1. Add the boundary-pair lookup: given a single-element selection, return the partner element in the adjacent line when all of these hold — the selected element is the first or last in its line, an adjacent line exists in that direction, both elements are pitched notes, and their pitches are equal. Return null otherwise.

2. Enable the toggle-tie action for a single-element selection exactly when that lookup returns a partner, in both directions.

3. Extend `toggleTie` to handle the boundary case: create `new Tie(anchor, end)` with the anchor being whichever element comes first in document order, and add it through `line.addTie`. Removing an existing cross-line tie goes through `removeTie`, which phase 1 makes remove from both lists.

4. Use `findExactTie` against the owning line to decide add-versus-remove. Phase 2 task 4 makes a cross-line half distinguishable from a same-line tie at the same index, so this must not match a same-line tie ending on the boundary note.

5. Add `CrossLineTieToggleTest`: enabled for a matching boundary pair in both directions; disabled when pitches differ, when either element is unpitched, when the selected element is not at a boundary, and at the first line's start and last line's end; the toggle creates a tie in both lines and toggling again removes it from both.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieToggleTest` (green).

* * *
## ⏳ Phase 8: Selecting and Deleting the Tie
**Status:** Pending **BlockedBy:** 1, 10 **Files:** src/main/java/songscribe/layout/HitRegionBuilder.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/test/java/songscribe/ui/selection/CrossLineTieSelectionTest.java **Recommended model/effort:** Opus 4.8, medium — selection highlighting spans two lines, which nothing in the selection model does today

### Context this phase needs
`HitRegionBuilder.addTies` (`:448`) reads `getTieLayout(tie)` per line and registers a hit region for the arc. Phase 1 makes `isOnLine` answer true for both lines of a cross-line tie, which is what lets a selection drawn on line A also register on line B.

Deleting a selected tie is not implemented for any tie today. It is in scope here because a cross-line tie the user can select but not delete is a dead end, and because the deletion must remove both halves.

### Tasks
1. Confirm clicking either half selects the tie, and that selecting it highlights **both** halves rather than only the clicked one.

2. Confirm the hit region for each half covers only its own line's portion of the staff, and does not extend across the empty part of the line beyond the arc.

3. Implement deletion of a selected tie, removing it from both lines through the tracked path so undo restores both halves.

4. Add `CrossLineTieSelectionTest`: clicking either half selects the same `Tie`; the selection reports as present on both lines; deleting the selection removes the tie from both lines; undo restores it into both and redo removes it again.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).

* * *
## ⏳ Phase 9: Pitch Shift Across the Break
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/ui/component/score/PitchShifter.java, src/test/java/songscribe/ui/component/score/CrossLinePitchShiftTest.java **Recommended model/effort:** Sonnet 4.6, medium — the existing tie-chain expansion is index-based and silently produces the wrong set

### Context this phase needs
`PitchShifter` already moves tied notes together. `PitchShifter.java:289-313` expands each selected note's tie chain to its full transitive closure — chained ties must move as one unit even though each link is its own two-note `Tie` — and does it with:

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
## ⏳ Phase 10: Rendering the Two Halves
**Status:** Pending **BlockedBy:** 3 **Files:** src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/layout/ElementColumn.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/ui/renderer/TieRenderer.java **Recommended model/effort:** Opus 4.8, high — arc geometry with no in-line endpoint to attach to on one side

### Context this phase needs
`LayoutEngine.calculateTies` (`:827`) iterates `line.findTies()` and calls `ElementColumn.resolveSpan` (`ElementColumn.java:662`), which returns **null** when either endpoint has no column in this line (`:674-676`). `calculateTies` then skips the tie (`:843-845`), so a cross-line tie currently produces no `TieLayout` and is not drawn at all.

The existing geometry (`:852` onward) derives the arc from two note columns sharing one Y, following LilyPond's `tie-formatting-problem.cc`.

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

**Both halves share one arc direction, taken from across the break.** `Tie::get_default_dir` (`tie.cc:100-102`) asks for the head on each side and, when this half has none, reaches into the other half:

```cpp
Grob *one_head = head (me, d);
if (!one_head)
  one_head = head (me->broken_neighbor (d), d);
```

`Tie::get_position` (`tie.cc:60-80`) likewise takes the staff position from whichever head exists, and treats "no head on either side" as a programming error. This is the source for both halves using the same `arcSign()`.

**Each half is a complete arc over its own width, returning to the tie's baseline at the open end.** `Tie_configuration::get_untransformed_bezier` (`tie-configuration.cc:62-71`) computes `Real l = attachment_x_.length()` — the *half's* own span — and calls `slur_shape(l, height_limit_, ratio_)`, whose control points are `(0,0), (indent,h), (w-indent,h), (w,0)` (`bezier-bow.cc:119-132`). Both ends of each half's bezier sit on `y = 0` relative to the tie's position. A broken half therefore does **not** stop mid-arc with a nonzero slope at the staff edge; it rises and comes back down.

The height law is `slur_height(w) = F0_1(w * ratio / height_limit) * height_limit` (`bezier-bow.cc:35-38`) — rising from zero with initial slope `ratio` and saturating at `height_limit` (`tie-details.cc:50-51`: `height-limit` 0.75, `ratio` 0.333). Since each half is narrower than the whole tie, each half is flatter than the unbroken tie would be, and **the two halves have different heights whenever they have different widths**. They are not two pieces of one curve.

`tie-column.cc` contains no break handling at all — the halves are independent formatting problems, one per system.

Rendering reference: [https://musescore.com/user/90375058/scores/30325826](https://musescore.com/user/90375058/scores/30325826)

### Tasks
1. Give `resolveSpan` (or a sibling used only by ties) a result that distinguishes "one endpoint resolved, the other is off this line's edge" from "unresolvable". Do not make it return a fabricated column.

2. Extend `TieLayout` so a half carries which side is open. `TieRenderer.renderTie` (`:85`) reads pre-computed geometry, so the open-ended shape is decided in the layout phase, not the renderer.

3. Produce geometry for both halves following the three findings above: line A's runs from its note to the right staff edge, line B's from the left staff edge to its note; both use the same `arcSign()`; each is a complete arc over its own width, meeting the staff edge back at the tie's baseline rather than mid-arc.

4. Record the LilyPond reasoning in the comment block at `LayoutEngine.java:852-869`, matching how the unbroken case is already documented there.

5. Confirm hit regions still resolve — `HitRegionBuilder.java:450` reads `getTieLayout(tie)` and must not produce a region covering the empty part of the staff. Phase 8 depends on this.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green). Visual confirmation happens in phase 13.

* * *
## ⏳ Phase 11: Clipboard
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/ui/clipboard/Fragment.java, src/main/java/songscribe/ui/clipboard/ClipboardManager.java, src/test/java/songscribe/ui/clipboard/FragmentTest.java **Recommended model/effort:** Sonnet 4.6, low — one guard plus a user-facing message

### Context this phase needs
`Fragment.cloneSpans` (`:227-248`) copies a span only when **both** endpoints are inside the captured range:

```java
if (clonedAnchor != null && clonedEnd != null) {
    clonedSpans.add(span.copy(clonedAnchor, clonedEnd));
}
```

Partially-captured spans are dropped silently. That is invisible today because a tie is always wholly within one line; with cross-line ties, copying a range containing one half would silently discard a tie the user can see.

The decision for this plan: **refuse to capture a partial tie** rather than dropping or truncating it.

Paste needs no separate work: `ScoreViewController.java:1116-1123` already guarantees every clone is inserted before the first `addPastedSpan`, so a paste at a line boundary reaches the tie through phase 5's insertion rule like any other insertion.

Read [Strings](./.agents/guides/strings.md) before writing the message, and [OptionDialogs](./.agents/guides/option-dialogs.md) if it is presented as an alert.

### Tasks
1. Detect a capture range that contains exactly one endpoint of a tie, and refuse the capture — the clipboard is left unchanged.

2. Tell the user why, through the project's standard mechanism. The message must say the selection splits a tie, not merely that the copy failed.

3. Scope the refusal to ties. Other span types keep the existing drop-silently behavior; changing them is out of scope for this plan.

4. Add to `FragmentTest`: capturing a range containing both endpoints still copies the tie; capturing a range containing one endpoint refuses and leaves the clipboard unchanged; capturing a range containing neither is unaffected. Add one paste test confirming a paste landing between a cross-line tie's endpoints removes the tie by phase 5's rule.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit FragmentTest` (green).

* * *
## ⏳ Phase 12: Persistence
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/io/musicxml/RangeSpanResolver.java, src/test/java/songscribe/io/musicxml/CrossLineTieRoundTripTest.java **Recommended model/effort:** Sonnet 4.6, medium — the write side already works, and phase 1 may make the read side work too

### Context this phase needs
MusicXML writes ties as per-note markers (`<tied type="start">` / `<tied type="stop">`, `MusicXmlNotationsWriter.java:87-98`), not as index pairs, so the write side needs no change for a tie whose notes are in different lines.

`RangeSpanResolver.resolveTie` (`:175-189`) holds `pendingTieStart` across notes and, on seeing the stop, runs `line.addTie(new Tie(pendingTieStart, element))` where `line` is the line current **at stop time**.

Phase 1 likely makes this correct with no change: `addTie` reaches `appendChild`, which adds the tie to every line in the derived set, and both endpoints are attached by then (`MusicXmlReader.java:770` precedes `:787`). This phase's job is to prove that rather than assume it.

### Tasks
1. Verify a tie read back from MusicXML with endpoints in different lines lands in both lines' `spans`. If phase 1 already achieves it, record that and change nothing; if not, fix the reader.

2. Confirm the legacy `.mssw` reader is unaffected. `LineIO` resolves span endpoints by index within a single line (`:312-339`), so it cannot express a cross-line tie — confirm it degrades cleanly and never add a new persisted field to that path.

3. Add `CrossLineTieRoundTripTest`: a song with a cross-line tie writes and reads back with the tie attached to both lines and the same two endpoint elements; a same-line tie is unaffected; a chain of ties crossing the break survives.

4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieRoundTripTest` (green).

* * *
## ⏳ Phase 13: Full Gate
**Status:** Pending **BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 **Files:** — **Recommended model/effort:** Opus 4.8, medium — judges whether the halves behave as one tie across every path

### Context this phase needs
The failure this plan risks is a **half-tie**: one line holding a tie the other has dropped, which renders as an arc running off the edge to nothing.

Running the app requires user permission — `./scripts/run.sh` must never be executed without it.

### Tasks
1. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green). Fix any failure here before anything else.

2. Walk the mutation inventory in the contract and confirm every row has a test. No path may leave a tie in one line's `spans` and not the other's.

3. Confirm the derived parentage cannot lie: after any mutation, `span.isIn(L)` ⟹ `L.spans` contains `span`, including after `Song.removeLine`.

4. Ask the user for permission, then drive the app. Create a cross-line tie in both directions; confirm both halves render and meet the staff edges; insert a note and then a barline between the endpoints; delete a note under each half; delete a whole line; pitch-shift each endpoint; click each half and delete the tie; undo and redo each of those; copy a range splitting the tie and confirm the refusal; and round-trip through `.musicxml`.

5. Confirm same-line ties, beams, tuplets, hairpins, trills and endings are unchanged by this work — every one of them reads the accessors phase 3 rewrote and the parentage phase 1 replaced.

* * *
## Verification (whole plan)
- `./scripts/compile.sh` reports SUCCESS.
- `./scripts/test.sh unit` is green, with no test disabled or weakened to get there.
- A cross-line tie is present in both lines' `getSpans()`, and no path leaves it in one.
- `Span` has no stored parent line, and `span.isIn(L)` ⟹ `L.spans` contains `span` after every mutation.
- Line A reports `(anchorIndex, END_OF_LINE)` and line B `(BEGINNING_OF_LINE, endIndex)`.
- A tie whose far endpoint was deleted reports `NOT_IN_LINE`, not a sentinel.
- `Line.getElementIndex` is unchanged and `elementIndexMap` contains only this line's elements.
- Inserting a note or rest between the endpoints removes the tie; inserting a barline or repeat does not — for same-line ties as well as cross-line ones.
- Deleting either line, or inserting a line between them, removes the tie from the survivor.
- Pitch-shifting one endpoint moves the other.
- Both halves render, meeting the staff edges at the tie's baseline, sharing one arc direction, each a complete arc over its own width.
- Clicking either half selects the whole tie; deleting the selection removes both halves; undo/redo restores both.
- Copying a range that splits a tie is refused with a message naming the reason.
- A cross-line tie survives a `.musicxml` round trip attached to both lines.

## Out of scope — recorded, not done
- **Cross-line beams, hairpins, tuplets, trills and endings.** The sentinel contract and the derived parentage generalize to them, but the enable logic, geometry and merge behavior are per-type work.
- **Truncating or dropping a partially-copied tie.** Phase 11 refuses instead.
- **Reflow.** No line-splitting or line-merging exists; a cross-line tie arises only from the phase 7 toggle or from a file that already contains one.
- **Ties spanning more than two lines.** LilyPond notes this case as unhandled too (`tie.cc:74-78`).
