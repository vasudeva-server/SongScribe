# Cross-Line Ties (issue #493)
**Type:** Master plan **Created:** 2026-08-02 **Branch:** TBD (base: `develop`) **Status:** Pending

* * *
## Goal
Support a tie whose two notes sit in different lines: the anchor on the last element of line A, the end on the first element of line B.

The tie is **one** `Tie` **object held in both lines'** `spans` **lists** — half on each. Each line resolves the endpoint it owns to a real index and the far endpoint to a sentinel meaning "off this line's edge", so each line draws its own half running to the staff edge.

```
  line A:  … ● ● ●─────►        anchorIndexOf = 7,               endIndexOf = END_OF_LINE
  line B:  ◄─────● ● …          anchorIndexOf = BEGINNING_OF_LINE, endIndexOf = 0
```

* * *
## Contract every phase is written against
**Sentinels.** Declared on `SpanLookup` (an interface, so no modifiers):

| Constant | Value | Meaning |
| --- | --- | --- |
| `NOT_IN_LINE` | `-1` | No position: the endpoint is unset, or attached to no line |
| `BEGINNING_OF_LINE` | `-2` | The endpoint is in an earlier line; this half enters from the left edge |
| `END_OF_LINE` | `-3` | The endpoint is in a later line; this half exits through the right edge |

`-1` keeps its existing meaning — `ArrayList.indexOf`'s "absent" — because `Line.getElementIndex`, `Span.indexInLine` and every existing `>= 0` guard already read it that way.

`Line.getElementIndex` **does not change.** It answers "where is this element in this line", and `elementIndexMap` indexes only this line's own `elements`. A far endpoint is not in that map and must never be added to it. Sentinels are produced by the `SpanLookup` accessors, which know _which endpoint_ was asked for.

**Direction comes from which endpoint, not from line ordering.** An anchor is by definition the earlier endpoint, so an anchor attached somewhere other than this line is in an earlier line. No line-order comparison is needed.

`getParentLine() == null` **keeps the three states distinct.** A deleted endpoint is `NOT_IN_LINE`; an endpoint attached to another line is a directional sentinel. Without this check a tie whose note was deleted would draw as entering from the edge.

**Arithmetic on an index is invalid until the sentinels are excluded.** `Line.java:986,991` computes `end + reach` and `anchor - reach`. Every such expression must reject sentinels first — there is no sentinel value that makes the arithmetic work by itself.

* * *
## Status Dashboard
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1   | [Span Parentage Across Two Lines](#-phase-1-span-parentage-across-two-lines) | ⏳ Pending | Opus 4.8, high |
| 2   | [Endpoint Sentinels](#-phase-2-endpoint-sentinels) | ⏳ Pending | Sonnet 4.6, medium |
| 3   | [Receiver-Relative Accessors](#-phase-3-receiver-relative-accessors) | ⏸️ Blocked by 1, 2 | Opus 4.8, high |
| 4   | [Deletion Across Both Lines](#-phase-4-deletion-across-both-lines) | ⏸️ Blocked by 3 | Opus 4.8, medium |
| 5   | [Creating a Cross-Line Tie](#-phase-5-creating-a-cross-line-tie) | ⏸️ Blocked by 1 | Sonnet 4.6, medium |
| 6   | [Rendering the Two Halves](#-phase-6-rendering-the-two-halves) | ⏸️ Blocked by 3 | Opus 4.8, high |
| 7   | [Clipboard: Refuse Partial Capture](#-phase-7-clipboard-refuse-partial-capture) | ⏸️ Blocked by 1 | Sonnet 4.6, low |
| 8   | [Persistence](#-phase-8-persistence) | ⏸️ Blocked by 1 | Sonnet 4.6, medium |
| 9   | [Full Gate](#-phase-9-full-gate) | ⏸️ Blocked by 1, 2, 3, 4, 5, 6, 7, 8 | Opus 4.8, medium |

* * *
## ⏳ Phase 1: Span Parentage Across Two Lines
**Status:** Pending **BlockedBy:** — **Files:** src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/Span.java, src/test/java/songscribe/dom/SpanParentageTest.java **Recommended model/effort:** Opus 4.8, high — changes a documented invariant that `attach`/`detach` and undo replay both depend on
### Context this phase needs
`LineElement.java:73-77` documents:

> `parentLine == L ⟺ that list of L contains this`

`parentLine` (`:79`) is one field with a package-private setter (`:167`) written only by `Line.attach` (`:288`) and `Line.detach` (`:299`).

A tie in two lines' `spans` lists breaks this directly. The second `attach` overwrites the first line's pointer; `detach` from the first line then hits its `getParentLine() != this` guard (`Line.java:308`) and returns without removing anything, leaving the tie in that line's list forever with `parentLine` naming the other line. That guard is what makes re-parenting order-independent, so it cannot simply be dropped.
### Tasks
1. Run `jet_brains_find_referencing_symbols` on `LineElement/getParentLine` and `LineElement/setParentLine`. Separate the callers that operate on a `Span` from those that operate on a `StaffElement` — only the span half is in scope.
  
2. Decide between the two models and record the choice in this phase's section:
  
  - **Derive** — a span's line membership is a function of its endpoints (`anchorElement.getParentLine()` / `endElement.getParentLine()`), and `Span` stops carrying `parentLine` at all. Cannot go out of sync; requires every span caller found in task 1 to have an endpoint-based answer.
    
  - **Multi-line membership** — spans keep an explicit set of parent lines maintained by `attach`/`detach`. Smaller blast radius; two structures to keep consistent through undo replay.
    
  
  Prefer **derive** if task 1 shows the span callers can all be answered from endpoints. If any caller needs the pointer when both endpoints are detached, that rules it out — report which caller and take the other model.
  
3. Implement the chosen model. `attach`/`detach` remain the only writers of whatever replaces the field for spans.
  
4. Update the `LineElement:44-77` diagram and invariant text. The `StaffElement` half is unchanged; the `Span` half now permits membership in two lines.
  
5. Add `SpanParentageTest`: a tie attached to two lines is in both `getSpans()` lists; detaching from one leaves it in the other with correct parentage; undo and redo of both the attach and the detach preserve that.
  
6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — this touches parentage for every span type).
  

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
1. Declare `NOT_IN_LINE`, `BEGINNING_OF_LINE` and `END_OF_LINE` on `SpanLookup` with the values in the contract above, each with Javadoc stating what it means for a caller.
  
2. `containing`: replace the `>= 0` guard with a `NOT_IN_LINE` rejection, and treat `BEGINNING_OF_LINE` as "before every index" and `END_OF_LINE` as "after every index". A cross-line half must report **true** for every element it passes over, not just its attached endpoint.
  
3. `overlapping`: apply the same unbounded reading. It is unguarded against `NOT_IN_LINE` today; keep that behavior unless task 5's tests show it produces a false match for a detached span, in which case report it rather than changing it here.
  
4. `exactly`: a cross-line half must not match a query for a span genuinely anchored at this line's first or last element. Compare sentinels as distinct values — never coerce them to `0` or `lastIndex`.
  
5. Add `SpanPredicateSentinelTest` driving each predicate directly with every combination of real index, `NOT_IN_LINE`, `BEGINNING_OF_LINE` and `END_OF_LINE` on each side. These are pure functions, so no `Line` fixture is needed.
  
6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit SpanPredicateSentinelTest SpanLookupTest` (green).
  

* * *
## ⏳ Phase 3: Receiver-Relative Accessors
**Status:** Pending **BlockedBy:** 1, 2 **Files:** src/main/java/songscribe/dom/Line.java, src/test/java/songscribe/dom/SpanLookupTest.java **Recommended model/effort:** Opus 4.8, high — every span query in the app reads these two methods, and the merge arithmetic silently misreads a sentinel
### Context this phase needs
`Line.anchorIndexOf`/`endIndexOf` currently resolve through a private `indexOfEndpoint(@Nullable StaffElement)` that returns `NOT_IN_LINE` for any endpoint not in this line, with no direction.

`mergeOverlappingSpans` (`Line.java:972`) reads both accessors at `:978-979` and then computes `end + reach` and `anchor - reach` at `:986` and `:991`. `reach` is `0` for beams and `SPAN_ADJACENCY_REACH` for hairpins. Ties never merge, but these predicates receive sentinels as soon as the accessors can return them.
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
  
4. Leave `Span.getAnchorElementIndex()`/`getEndElementIndex()` (`Span.java:243`, `:251`) alone. They resolve through each endpoint's own line, which is what phase 6 needs and what `toIndexString()` (`:266`) uses for the legacy `.mssw` writer.
  
5. Extend `SpanLookupTest`: for a tie attached to two lines, line A reports `(anchorIndex, END_OF_LINE)` and line B reports `(BEGINNING_OF_LINE, endIndex)`; each line reports the tie as containing every element from its attached endpoint to its edge; and a tie whose far endpoint has been deleted reports `NOT_IN_LINE`, not a sentinel.
  
6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — every span query reads these).
  

* * *
## ⏳ Phase 4: Deletion Across Both Lines
**Status:** Pending **BlockedBy:** 3 **Files:** src/main/java/songscribe/dom/Line.java, src/test/java/songscribe/dom/CrossLineTieDeletionTest.java **Recommended model/effort:** Opus 4.8, medium — the failure is a span left pointing at a detached element, which no current test would catch
### Context this phase needs
`removeRange` (`Line.java:626`) sweeps **this line's** `spans` at `:644-649`:

```java
var invalidated = spans.stream()
    .filter(r -> !(r instanceof Hairpin))
    .filter(r -> r.isInvalidatedBy(deletedElements)
        || r.isInvalidatedByDeletion(deletedElements, this))
    .toList();
invalidated.forEach(this::removeInvalidatedSpan);
```

`Span.isInvalidatedBy` (`Span.java:119`) tests whether either endpoint is among the deleted elements. `removeElement` and `setElement` (`:446`) run equivalent sweeps.

A cross-line tie is in both lines' lists, so deleting line B's endpoint makes line B's sweep remove it from line B — but line A still holds it, now with a detached end element.
### Tasks
1. Make removal of an invalidated cross-line tie remove it from **both** lines, through the same tracked-modification path the single-line case uses, so undo restores both halves.
  
2. Cover every sweep site, not only `removeRange`: `removeElement`, `setElement` (`:446`), and the ending sweep at `:390`. Deleting the far line's note must not leave a half behind at any of them.
  
3. Add `CrossLineTieDeletionTest`: delete the anchor note in line A, assert the tie is gone from both lines; delete the end note in line B, same; range-delete a run containing one endpoint, same; and assert undo restores the tie into both lines and redo removes it from both.
  
4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green).
  

* * *
## ⏳ Phase 5: Creating a Cross-Line Tie
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/test/java/songscribe/ui/selection/CrossLineTieToggleTest.java **Recommended model/effort:** Sonnet 4.6, medium — the enable predicate has four preconditions and a mirror case
### Context this phase needs
From issue #493: selecting the **last** element of a line enables the toggle-tie action when that element is a pitched note and the **first** element of the next line is a pitched note at the same pitch. Selecting the **first** element of a line is the mirror case, pairing with the previous line's last element.

`Selection.Range` (`Selection.java:70`) is `(Line line, int begin, int end, int anchor)` — bound to one line. This feature needs no cross-line selection: the selection stays a single element and the partner note is found in the adjacent line.

`toggleTie` (`MusicEditOperations.java:252-269`) takes `range.begin()`/`range.end()` as the two tie notes, calls `line.findExactTie(beginIndex, endIndex)` and either adds or removes. `RangeQueries.java:159` decides whether the action shows as connect or disconnect.
### Tasks
1. Add the boundary-pair lookup: given a single-element selection, return the partner element in the adjacent line when all of these hold — the selected element is the first or last in its line, an adjacent line exists in that direction, both elements are pitched notes, and their pitches are equal. Return null otherwise.
  
2. Enable the toggle-tie action for a single-element selection exactly when that lookup returns a partner, in both directions.
  
3. Extend `toggleTie` to handle the boundary case: create `new Tie(anchor, end)` with the anchor being whichever element comes first in document order, and attach it to both lines. Removing an existing cross-line tie must detach it from both.
  
4. Use `findExactTie` against the owning line to decide add-versus-remove. Phase 2 task 4 makes a cross-line half distinguishable from a same-line tie at the same index, so this must not match a same-line tie ending on the boundary note.
  
5. Add `CrossLineTieToggleTest`: enabled for a matching boundary pair in both directions; disabled when pitches differ, when either element is unpitched, when the selected element is not at a boundary, and at the first line's start and last line's end; the toggle creates a tie in both lines and toggling again removes it from both.
  
6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieToggleTest` (green).
  

* * *
## ⏳ Phase 6: Rendering the Two Halves
**Status:** Pending **BlockedBy:** 3 **Files:** src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/layout/ElementColumn.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/ui/renderer/TieRenderer.java **Recommended model/effort:** Opus 4.8, high — arc geometry with no in-line endpoint to attach to on one side
### Context this phase needs
`LayoutEngine.calculateTies` (`:827`) iterates `line.findTies()` and calls `ElementColumn.resolveSpan` (`ElementColumn.java:662`), which returns **null** when either endpoint has no column in this line (`:674-676`). `calculateTies` then skips the tie (`:843-845`), so a cross-line tie currently produces no `TieLayout` and is not drawn at all.

The existing geometry (`:852` onward) derives the arc from two note columns sharing one Y, following LilyPond's `tie-formatting-problem.cc`. A cross-line half has only one note column; the other end terminates at the staff edge.

LilyPond source is at `~/Developer/projects/lilypond/lily/`. Rendering reference: [https://musescore.com/user/90375058/scores/30325826](https://musescore.com/user/90375058/scores/30325826)
### Tasks
1. Read LilyPond's handling of a tie broken across a line break — start from `tie-formatting-problem.cc` and `tie-column.cc` — and record in this phase's section where the outer end terminates horizontally and how the arc's vertical shape differs from an unbroken tie. The existing comment block at `LayoutEngine.java:852-869` is the model for how that reasoning gets written down.
  
2. Give `resolveSpan` (or a sibling used only by ties) a result that distinguishes "one endpoint resolved, the other is off this line's edge" from "unresolvable". Do not make it return a fabricated column.
  
3. Extend `TieLayout` so a half carries which side is open. `TieRenderer.renderTie` (`:85`) reads pre-computed geometry, so the open-ended shape is decided in the layout phase, not the renderer.
  
4. Produce geometry for both halves: line A's runs from its note to the right staff edge, line B's from the left staff edge to its note. Both use the same `arcSign()` so the two halves read as one tie.
  
5. Confirm hit regions still resolve — `HitRegionBuilder.java:450` reads `getTieLayout(tie)` and must not produce a region covering the empty part of the staff.
  
6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green). Visual confirmation happens in phase 9.
  

* * *
## ⏳ Phase 7: Clipboard: Refuse Partial Capture
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/ui/clipboard/Fragment.java, src/main/java/songscribe/ui/clipboard/ClipboardManager.java, src/test/java/songscribe/ui/clipboard/FragmentTest.java **Recommended model/effort:** Sonnet 4.6, low — one guard plus a user-facing message
### Context this phase needs
`Fragment.cloneSpans` (`:228-249`) copies a span only when **both** endpoints are inside the captured range:

```java
if (clonedAnchor != null && clonedEnd != null) {
    clonedSpans.add(span.copy(clonedAnchor, clonedEnd));
}
```

Partially-captured spans are dropped silently. That is invisible today because a tie is always wholly within one line; with cross-line ties, copying a range containing one half would silently discard a tie the user can see.

The decision for this plan: **refuse to capture a partial tie** rather than dropping or truncating it.

Read [Strings](./.agents/guides/strings.md) before writing the message, and [OptionDialogs](./.agents/guides/option-dialogs.md) if it is presented as an alert.
### Tasks
1. Detect a capture range that contains exactly one endpoint of a tie, and refuse the capture — the clipboard is left unchanged.
  
2. Tell the user why, through the project's standard mechanism. The message must say the selection splits a tie, not merely that the copy failed.
  
3. Scope the refusal to ties. Other span types keep the existing drop-silently behavior; changing them is out of scope for this plan.
  
4. Add to `FragmentTest`: capturing a range containing both endpoints still copies the tie; capturing a range containing one endpoint refuses and leaves the clipboard unchanged; capturing a range containing neither is unaffected.
  
5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit FragmentTest` (green).
  

* * *
## ⏳ Phase 8: Persistence
**Status:** Pending **BlockedBy:** 1 **Files:** src/main/java/songscribe/io/musicxml/RangeSpanResolver.java, src/test/java/songscribe/io/musicxml/CrossLineTieRoundTripTest.java **Recommended model/effort:** Sonnet 4.6, medium — the write side already works; the read side attaches to one line
### Context this phase needs
MusicXML writes ties as per-note markers (`<tied type="start">` / `<tied type="stop">`, `MusicXmlNotationsWriter.java:87-98`), not as index pairs, so the write side needs no change for a tie whose notes are in different lines.

The read side does. `RangeSpanResolver.resolveTie` (`:175-189`) holds `pendingTieStart` across notes and, on seeing the stop, runs `line.addTie(new Tie(pendingTieStart, element))` where `line` is the line current **at stop time**. For a cross-line tie that attaches the tie to the end's line only, leaving the anchor's line without its half.
### Tasks
1. Attach a tie read back from MusicXML to both endpoints' lines when they differ, matching what phase 5 produces when the user creates one.
  
2. Confirm the legacy `.mssw` reader is unaffected, or report what it does with a cross-line tie. Never add a new persisted field to that path.
  
3. Add `CrossLineTieRoundTripTest`: a song with a cross-line tie writes and reads back with the tie attached to both lines and the same two endpoint elements; a same-line tie is unaffected.
  
4. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit CrossLineTieRoundTripTest` (green).
  

* * *
## ⏳ Phase 9: Full Gate
**Status:** Pending **BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8 **Files:** — **Recommended model/effort:** Opus 4.8, medium — judges whether the halves behave as one tie across every path
### Context this phase needs
The failure this plan risks is a **half-tie**: one line holding a tie the other has dropped, which renders as an arc running off the edge to nothing.

Running the app requires user permission — `./scripts/run.sh` must never be executed without it.
### Tasks
1. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green). Fix any failure here before anything else.
  
2. Confirm no path can leave a tie in one line's `spans` and not the other's: every `attach`/`detach` of a `Tie`, every deletion sweep from phase 4, the toggle from phase 5, and the reader from phase 8.
  
3. Ask the user for permission, then drive the app. Create a cross-line tie in both directions; confirm both halves render and meet at the staff edges; delete a note under each half; undo and redo each of those; copy a range splitting the tie and confirm the refusal; and round-trip through `.musicxml`.
  
4. Confirm same-line ties, beams, tuplets, hairpins, trills and endings are unchanged by this work — every one of them reads the accessors phase 3 rewrote.
  

* * *
## Verification (whole plan)
- `./scripts/compile.sh` reports SUCCESS.
  
- `./scripts/test.sh unit` is green, with no test disabled or weakened to get there.
  
- A cross-line tie is present in both lines' `getSpans()`, and no path leaves it in one.
  
- Line A reports `(anchorIndex, END_OF_LINE)` and line B `(BEGINNING_OF_LINE, endIndex)`.
  
- A tie whose far endpoint was deleted reports `NOT_IN_LINE`, not a sentinel.
  
- `Line.getElementIndex` is unchanged and `elementIndexMap` contains only this line's elements.
  
- Both halves render, meeting the staff edges, and undo/redo restores both.
  
- Copying a range that splits a tie is refused with a message naming the reason.
  
- A cross-line tie survives a `.musicxml` round trip attached to both lines.
  
## Out of scope — recorded, not done
- **Cross-line beams, hairpins, tuplets, trills and endings.** The sentinel contract and the parentage model generalize to them, but the enable logic, geometry and merge behavior are per-type work.
  
- **Truncating or dropping a partially-copied tie.** Phase 7 refuses instead.
  
- **Reflow.** No line-splitting exists; a cross-line tie arises only from the phase 5 toggle or from a file that already contains one.
