# Element Index Resolution (issue #725)
**Type:** Master plan
**Created:** 2026-08-02
**Branch:** `722-index-resolution` (base: `develop`)
**Status:** Complete

* * *
## Goal

`Line.getElementIndex(element)` (`src/main/java/songscribe/dom/Line.java:688`) is
`elements.indexOf(element)` — a linear scan. Every span endpoint resolution bottoms out
in it (106 `getAnchorElementIndex`/`getEndElementIndex` call sites, 44 direct
`getElementIndex` call sites), and the renderers call it **per note** on the repaint path.

Make it O(1) with a lazily-built identity map inside `Line`, invalidated at the
`attach`/`detach` chokepoint. No caller changes. No lifetime contract outside `Line`.

```
  findBeamAt(i)  →  iterate spans, resolve 2 endpoints each

  BEFORE:  O(spans) × 2 × O(elements/2)   ≈  15 × 2 × 25  =  750 comparisons
  AFTER:   O(spans) × 2 hash gets         ≈               =   30 gets
```

* * *
## Status Dashboard
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1   | [The Element Index](#-phase-1-the-element-index) | ✅ Complete | Opus 4.8, high |
| 2   | [Index Consistency Tests](#-phase-2-index-consistency-tests) | ✅ Complete | Opus 4.8, medium |
| 3   | [SpanLookup Fast Path and Dead Code](#-phase-3-spanlookup-fast-path-and-dead-code) | ✅ Complete | Sonnet 4.6, low |
| 4   | [SpanLookupTest Gaps](#-phase-4-spanlookuptest-gaps) | ✅ Complete | Sonnet 4.6, medium |
| 5   | [Merge Predicate Coverage](#-phase-5-merge-predicate-coverage) | ✅ Complete | Sonnet 4.6, low |
| 6   | [Loose Test-Quality Fixes](#-phase-6-loose-test-quality-fixes) | ✅ Complete | Sonnet 4.6, low |
| 7   | [Receiver-Relative Span Endpoints](#-phase-7-receiver-relative-span-endpoints) | ✅ Complete | Opus 4.8, medium |
| 8   | [Measurement and Full Gate](#-phase-8-measurement-and-full-gate) | ✅ Complete | Opus 4.8, medium |

* * *
## ✅ Phase 1: The Element Index
**Status:** Complete
**BlockedBy:** —
**Files:** src/main/java/songscribe/dom/Line.java
**Recommended model/effort:** Opus 4.8, high — one field whose correctness rests on an invalidation invariant that must hold across five mutation paths and undo/redo replay

### Context this phase needs

`Line` holds `private final List<StaffElement> elements = new ArrayList<>();`.
`getElementIndex` (`:688`) is `elements.indexOf(element)`.

`elements` is written at **exactly five** places, all inside `Line`, all inside an
`applyChange` mutator lambda:

| Site | Method | Write |
| --- | --- | --- |
| `:305` | `addElement(StaffElement)` | `elements.add(index, element)` |
| `:377` | `addElement(int, StaffElement)` | `elements.add(index, element)` |
| `:413` | `setElement(int, StaffElement)` | `elements.set(index, element)` |
| `:567` | `removeElement(int)` | `elements.remove(index)` |
| `:613` | `removeRange(int, int)` | `elements.subList(from, to + 1).clear()` |

The `:613` site writes through a **sublist view**, so a text search for `elements.remove`
or `elements.clear` does not find it. Do not rely on such a search to re-derive this list.

Every one of the five already passes through `attach(LineElement)` (`:256`) or
`detach(LineElement)` (`:262`) — the parentage chokepoint commit `e491b4e9` established.
That is where invalidation goes.

`attach`/`detach` also run for **spans** (`appendChild`/`removeChild`, `:281`/`:290`).
Span mutations never touch `elements`, and invalidating on them turns one O(n) map build
into O(spans × n) on the deletion path — `removeRange` calls `removeOverlappingTuplets`
(`:594`), `adjustHairpinsForDeletion` (`:595`) and a span-removal sweep (`:604`) **before**
its own element write. The invalidation must therefore be type-guarded.

`LineElement`'s subclasses are `Articulation`, `Attribution`, `Attachment`, `Clef`,
`KeySignature`, `Span` and `StaffElement`. Only `StaffElement` instances go in `elements`,
so `instanceof StaffElement` identifies element mutations exactly. `attach` calls
`element.propagateParentLine(this)`, which recurses on `LineElement` and never re-enters
`Line.attach`, so there is no re-entry to guard against.

`adjustHairpinsForDeletion` (`:1107-1112`) already builds a local
`HashMap<StaffElement, Integer>` of survivor positions using `computeIfAbsent`. Read it —
it is the same design in the small, including the first-wins semantics task 2 requires.
Leave it alone; it indexes the *post-deletion* order, which the line's own index cannot
answer.

### Tasks

1. Add the field, with a class-level ASCII diagram in its Javadoc showing what invalidates
   it:

   ```java
   /**
    * Lazily-built position index over {@link #elements}, or null when it must be rebuilt.
    * <p>
    * Identity-keyed, matching {@code ArrayList.indexOf}'s semantics: no {@code StaffElement}
    * overrides {@code equals}/{@code hashCode}, and an override added later must not silently
    * change what an element's position means.
    * <p>
    * {@code volatile} for safe publication: the map is shared mutable state where the bare
    * list was not, and {@code IdentityHashMap}'s internal table is not {@code final}, so a
    * reader on another thread could otherwise see a non-null reference to a map whose
    * contents are not yet visible.
    *
    * <pre>
    *   elements written  ──► attach/detach(StaffElement) ──► elementIndexMap = null
    *        :305 :377 :413 :567 :613
    *
    *   spans written     ──► attach/detach(Span) ────────► no effect
    *        appendChild/removeChild                         (spans do not move elements)
    *
    *   getElementIndex() ──► null? rebuild : cached lookup
    * </pre>
    */
   private volatile @Nullable Map<StaffElement, Integer> elementIndexMap = null;
   ```

2. Rewrite `getElementIndex` (`:688`) to build the map lazily and answer from it. Build
   with **`putIfAbsent`**, not `put`: `ArrayList.indexOf` returns the *first* matching
   position and a forward `put` would retain the *last*, so an element appearing twice
   would resolve to the wrong position. Assign the fully-built local to the field, never a
   partially-filled map. Return `-1` when the key is absent — which covers both an element
   belonging to another line and a null element, matching `ArrayList.indexOf`'s behavior
   today (`IdentityHashMap` permits null keys and returns null for an absent one).

3. Invalidate at the chokepoint. In **both** `attach` (`:256`) and `detach` (`:262`), as the
   **first** statement — above `detach`'s `getParentLine() != this` early return at `:265`,
   so a re-parent cannot skip it. Over-invalidating costs a rebuild; under-invalidating
   returns a wrong index.

   ```java
   // Only staff elements sit in `elements`; span parentage cannot move an index.
   if (element instanceof StaffElement) {
       elementIndexMap = null;
   }
   ```

4. Close the two mutable-view leaks that would let a caller reorder `elements` behind the
   map's back. Add a held `elementsView` field mirroring `spansView` (`:77`) — `getElements()`
   (`:525`) is on the repaint path, so it must not allocate a wrapper per call — and have
   `getElements(int, int)` (`:530`) wrap its `subList` per call, since its bounds vary.
   `Line.java:613` mutates through exactly the mechanism `:530` hands out.

5. Add throwaway instrumentation, to be read in phase 8 and **deleted before commit**.
   Three static counters, dumped per repaint only when `DEBUG=1`: `getElementIndex` call
   count, the running sum of `elements.size()` at call time, and rebuild count. The
   pre-change cost is then `sum / 2` comparisons and the post-change cost is
   `calls + rebuilds × avg size`, so no before-run is needed. Mark the block with a
   `// TODO: remove before commit` comment naming phase 8.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — this
   change is under every existing test, so the whole suite is the gate).

* * *
## ✅ Phase 2: Index Consistency Tests
**Status:** Complete
**BlockedBy:** 1
**Files:** src/test/java/songscribe/dom/LineElementIndexTest.java
**Recommended model/effort:** Opus 4.8, medium — this test *is* the correctness argument for phase 1; a fixture that never mutates makes it vacuous

### Context this phase needs

Phase 1 gave `Line` a lazily-built, identity-keyed position index over `elements`,
invalidated in `attach`/`detach` when the argument is a `StaffElement`.

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` before
writing anything. `src/test/java/songscribe/dom/SpanLookupTest.java` is the sibling test
for line fixtures — read it for the `UnitTest` base class and the `song.withReplay(...)`
technique.

The expected result this whole phase expresses:

> After **any** mutation, `line.getElementIndex(e)` equals `e`'s true position in
> `line.getElements()` for every element, and `-1` for every element not in the line.

### Tasks

1. Create `LineElementIndexTest` extending `UnitTest`, with one shared helper every case
   calls:

   ```java
   /** The cache never lies: every element resolves to its true list position. */
   private void assertIndicesConsistent(Line line) {
       var elements = line.getElements();

       for (var i = 0; i < elements.size(); i++) {
           assertThat(line.getElementIndex(elements.get(i))).isEqualTo(i);
       }
   }
   ```

2. Drive all five mutation paths, asserting the invariant after each: `addElement` (append),
   `addElement(int, …)` (insert before existing elements, so later indices shift),
   `setElement`, `removeElement`, and `removeRange` — the last is the site that writes
   through a sublist view and would be missed by a naive invalidation.

3. Assert the invariant holds after **undo** and again after **redo** for at least an insert
   and a range deletion, so replay through the same paths is pinned.

4. Cover the remaining `getElementIndex` branches: an element belonging to a *different*
   line returns `-1`; a null element returns `-1`; and a `setElement` self-replace
   (`element == oldElement`, the case `Line.java:413` explicitly handles) leaves indices
   correct — that case exercises `detach`'s early return, which is why phase 1 puts the
   invalidation above it.

5. Assert both `getElements()` and `getElements(int, int)` reject mutation, so the guarantee
   phase 1 task 4 adds is pinned rather than assumed.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit LineElementIndexTest`
   (green).

* * *
## ✅ Phase 3: SpanLookup Fast Path and Dead Code
**Status:** Complete
**BlockedBy:** —
**Files:** src/main/java/songscribe/dom/SpanLookup.java, src/main/java/songscribe/dom/Span.java, src/test/java/songscribe/dom/SpanLookupTest.java
**Recommended model/effort:** Sonnet 4.6, low — one loop, two deletions, mechanical test updates

### Context this phase needs

`SpanLookup.findSpans(Class)` (`SpanLookup.java:85`) delegates to the predicate-taking
overload with an always-true predicate. Java evaluates arguments eagerly, so
`matches.test(anchorIndexOf(span), endIndexOf(span))` resolves **both endpoints of every
span of the requested type** and discards them. Every unfiltered query routes through it:
`findTies`, `getCrescendos`, `getDiminuendos`, `findEndings`, and every direct
`findSpans(X.class)` call site.

### Tasks

1. Give `findSpans(Class)` (`:85`) its own loop that filters by type and resolves no
   endpoint positions. Preserve `getSpans()` order exactly — `findFirstSpan` (`:70`)
   depends on it.

2. Update the interface Javadoc (`:33-35`), which says only two methods iterate; it becomes
   three.

3. Fix `getSpans()`'s Javadoc (`:39`): it claims "in line order", which is false — `Line`
   only ever appends to `spans`, so the real order is the order spans were added, including
   undo/redo replay order. Reword to "in the order they were added".

4. Delete `Span.overlaps(int, int)` (`Span.java:259`) and `Span.matches(IndexPredicate)`
   (`:314`) with their Javadoc. Both resolve positions off the span's own parent line,
   bypassing the `SpanLookup` seam. Confirm with `jet_brains_find_referencing_symbols` that
   nothing outside `SpanLookupTest` calls either before deleting. Leave `toIndexString()`
   (`:266`) alone — it resolves off the span by design and the legacy `.mssw` writer uses it.

5. Rewrite every use of the deleted methods in `SpanLookupTest` to drive the predicate
   directly (`Span.containing(i).test(anchorIndex, endIndex)`) or to go through a
   `SpanLookup` receiver.

6. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit SpanLookupTest`
   (green).

* * *
## ✅ Phase 4: SpanLookupTest Gaps
**Status:** Complete
**BlockedBy:** 3
**Files:** src/test/java/songscribe/dom/SpanLookupTest.java
**Recommended model/effort:** Sonnet 4.6, medium — four test cases, two of which need the half-detached-span fixture technique

### Context this phase needs

Phase 3 gave `findSpans(Class)` its own loop. Nothing yet asserts that loop returns the
same spans, in the same order, as the delegation it replaced.

Three further gaps mean a deletion from production code fails no test today.

The technique for a half-detached span, already used in the `GuardBehavior` group: add the
span, then remove the endpoint element inside `song.withReplay(...)` so the invalidation
sweep that would delete the span is skipped.

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

### Tasks

1. Add a parity case: for each span type, `findSpans(type)` equals
   `findSpans(type, (anchorIndex, endIndex) -> true)` element-for-element **in order**. The
   fixture must include a half-detached span, since that is the case where an
   endpoint-resolving path could diverge from one that resolves nothing.

2. `GuardBehavior` group: every existing case detaches the span's **anchor**. Add the mirror
   case that detaches the **end** element while leaving the anchor valid, and assert the
   half-detached span stops being reported as containing anything. Without it, deleting
   `endIndex >= 0` from `Span.containing` (`Span.java:289`) fails no test.

3. `ExactlyPredicate` group: the two existing ties differ in **both** endpoints, so the
   negative case has both halves of `Span.exactly`'s conjunction false at once and `&&` is
   indistinguishable from `||`. Add a third tie matching one endpoint but not the other and
   assert `findExactTie` rejects it.

4. `testTwoOverlappingTrillsReturnTrueWithoutMaterializingAList` (around `:316`): the name
   promises the query short-circuits without allocating, which the test does not check and
   its own comment concedes. The boolean half is already covered in `LineTrillTest`. Rename
   it to what it actually asserts, or delete it as redundant.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit SpanLookupTest`
   (green).

* * *
## ✅ Phase 5: Merge Predicate Coverage
**Status:** Complete
**BlockedBy:** —
**Files:** src/test/java/songscribe/dom/LineBeamTest.java, src/test/java/songscribe/dom/LineHairpinMergeTest.java
**Recommended model/effort:** Sonnet 4.6, low — added cases in existing groups, no production code touched

### Context this phase needs

`Line.mergeOverlappingSpans` (`Line.java:904-942`) runs whenever the user adds a beam,
crescendo or diminuendo. It widens the new span to swallow same-type spans it touches, then
removes every same-type span the widened range subsumes. Its two absorption predicates are:

- absorbed at the anchor: `candidateAnchor <= anchorIdx && anchorIdx <= candidateEnd + reach`
- absorbed at the end: `candidateAnchor - reach <= endIdx && endIdx <= candidateEnd`

Each currently has one of its branches never taken by any test, so either `&&` could be
relaxed to `||` without a failure.

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first. Do not
modify production code in this phase.

### Tasks

1. In `LineBeamTest` (beam merge), add a candidate that fails only the **first** half of
   each conjunction and one that fails only the **second**, asserting it is not absorbed.

2. Do the same in `LineHairpinMergeTest` (hairpin merge), which runs with
   `absorbAdjacent = true` so `reach` is `SPAN_ADJACENCY_REACH` rather than `0`.

3. Gate: `./scripts/compile.sh` (SUCCESS), then
   `./scripts/test.sh unit LineBeamTest LineHairpinMergeTest` (green).

* * *
## ✅ Phase 6: Loose Test-Quality Fixes
**Status:** Complete
**BlockedBy:** —
**Files:** src/test/java/songscribe/layout/LineEndingSupportTest.java, src/test/java/songscribe/dom/LineTieTest.java
**Recommended model/effort:** Sonnet 4.6, low — two added test cases in existing groups, no production code touched

### Context this phase needs

Both are tests that pass whether or not the production code is right. Read
`.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

### Tasks

1. `LineEndingSupportTest`, around `:265` (`testTwoEndingsAffectedReturnsFirstNonNoneEffect`):
   the production method (`src/main/java/songscribe/layout/LineEndingSupport.java:44`) walks
   every ending, works out what replacing an element would do to each, discards the "no
   effect" answers, and returns the first real one. The test builds two endings where the
   replacement invalidates **both**, so the discarding step is never exercised — delete it
   and the test still passes, while in production a replacement that leaves the first ending
   alone and invalidates the second would return "no effect" and the second ending would
   silently survive. Add a case where the first ending is genuinely unaffected and assert the
   second ending's effect is what comes back.

2. `LineTieTest`, the `SameTieAt` group: `sameTieAt` returns true only when one and the same
   tie object covers both given notes. The existing cases use two ties with a gap between
   them, never two sharing a note — the configuration that matters, because ties, unlike
   beams, deliberately never merge at a shared endpoint. Add a case with ties covering notes
   0–1 and 1–2 and assert `sameTieAt(0, 2)` is false. Without it, loosening the method to "is
   there a tie spanning between these two notes" would break nothing.

3. `Span.getContentWidthPx()` (`src/main/java/songscribe/dom/Span.java:184`) is executed by
   no test. It is a one-line `ScaleContext.ssToPx(getContentWidthSs())` wrapper, and
   `getContentWidthSs` is already exercised at `StructuralTierStackingTest:735`. Add a single
   case asserting the conversion, in whichever existing span test file fits the fixture best.

4. Gate: `./scripts/compile.sh` (SUCCESS), then
   `./scripts/test.sh unit LineEndingSupportTest LineTieTest` (green), plus the test class
   touched by task 3.

* * *
## ✅ Phase 7: Receiver-Relative Span Endpoints
**Status:** Complete
**BlockedBy:** 1
**Files:** src/main/java/songscribe/dom/Line.java, src/test/java/songscribe/dom/SpanLookupTest.java
**Recommended model/effort:** Opus 4.8, medium — a two-line change whose risk is entirely in the caller trace and the state it alters

### Context this phase needs

`Line` implements `SpanLookup`, but its two endpoint accessors do not consult the receiver:

```java
// Line.java:1525, :1530
@Override public int anchorIndexOf(Span span) { return span.getAnchorElementIndex(); }
@Override public int endIndexOf(Span span)    { return span.getEndElementIndex(); }
```

`Span.getAnchorElementIndex()` (`Span.java:243`) resolves through `indexInLine` (`:230`),
which asks the **endpoint element** which line *it* belongs to and indexes into that line.
So for a span sitting in line A's `spans` list whose anchor element has been reparented to
line B, `lineA.anchorIndexOf(span)` returns a position **in line B** — a plausible-looking
non-negative number that no caller can distinguish from a real one.

`Span.overlapping` (`Span.java:300`) is deliberately unguarded against `-1`, so this feeds
straight into `findBeamsOverlapping`, `findTupletsOverlapping` and `findTrillsOverlapping`.

The paste and line-split paths are where cross-line spans arise transiently.

### Tasks

1. Run `jet_brains_find_referencing_symbols` on `Line/anchorIndexOf` and `Line/endIndexOf`,
   and on `Span/getAnchorElementIndex` and `Span/getEndElementIndex`. Identify any caller
   that could hold a span whose endpoint belongs to a different line, and whether it depends
   on the other line's index. If one does, **stop and report** rather than changing the
   accessors — the rest of this phase assumes none does.

2. Assuming task 1 is clean, change both accessors to resolve against the receiver:

   ```java
   @Override public int anchorIndexOf(Span span) { return getElementIndex(span.getAnchorElement()); }
   @Override public int endIndexOf(Span span)    { return getElementIndex(span.getEndElement()); }
   ```

   This is behavior-identical for every span whose endpoints are in this line, and for a null
   endpoint (`getElementIndex(null)` returns `-1`, as `indexInLine` does). It differs only in
   the cross-line case, where `-1` — "not in this line" — is the correct answer for a lookup
   asked about its own line.

3. Leave `Span.getAnchorElementIndex()` / `getEndElementIndex()` themselves alone. They stay
   for `toIndexString()` (`Span.java:266`), which the legacy `.mssw` writer uses.

4. Add a case to `SpanLookupTest` pinning the new behavior: build a span in one line, reparent
   its anchor element to a second line, and assert the first line's `anchorIndexOf` returns
   `-1` and that the span is no longer reported as containing or overlapping anything. Use
   `song.withReplay(...)` so the invalidation sweep that would delete the span is skipped.

5. Gate: `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh unit` (green — the change
   is under every span query, so the whole suite is the gate).

* * *
## ✅ Phase 8: Measurement and Full Gate
**Status:** Complete — measured ≈12.4× less index work (996,600 → 80,100 est. operations, 68 rebuilds over 78,358 lookups); manual app check passed except reflow, which the app does not yet support
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7
**Files:** src/main/java/songscribe/dom/Line.java
**Recommended model/effort:** Opus 4.8, medium — judges whether the combined result is coherent and drives the checks no unit test can make

### Context this phase needs

The failure mode this plan risks is a **missed invalidation**: an element mutation that does
not clear `elementIndexMap`, leaving spans anchored to stale positions. Phase 1 places the
invalidation at the `attach`/`detach` chokepoint and phase 2 tests all five paths, so this
phase re-verifies the chokepoint still holds against the code as it now stands.

Running the app requires user permission — `./scripts/run.sh` must never be executed without
it.

### Tasks

1. Run the full unit suite: `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit`
   (green). Any failure is fixed here before anything else proceeds.

2. Re-verify the chokepoint against the current code rather than against this plan: every
   write to `Line.elements` passes through `attach` or `detach` with a `StaffElement`
   argument. Search for writes through **views** as well as direct calls — `Line.java:613`
   uses `elements.subList(...).clear()`. Report any site that does not, rather than working
   around it.

3. Confirm `elementIndexMap` is read and written only inside `Line`, and that no caller
   holds a reference to it or to a map derived from it.

4. Ask the user for permission, then drive the app with `DEBUG=1 ./scripts/run.sh`. Load a
   representative score and exercise beams, ties, tuplets, hairpins, trills and endings:
   verify each renders on load, that editing each updates the score immediately with no span
   disappearing or jumping, that undo and redo restore them correctly, and that a save/load
   round trip through `.musicxml` preserves all six.

5. Read the phase 1 counters from the same session and report three numbers: estimated
   comparisons before (`sum / 2`), estimated cost after (`calls + rebuilds × average size`),
   and the rebuild count. If rebuilds are high relative to calls, name the path driving them
   rather than concluding the change failed.

6. Delete the phase 1 instrumentation (the counters and their `DEBUG=1` dump), then re-run
   `./scripts/compile.sh` and `./scripts/test.sh unit` to confirm the tree is clean without
   it.

* * *
## Verification (whole plan)

- `./scripts/compile.sh` reports SUCCESS.

- `./scripts/test.sh unit` is green, with no test disabled or weakened to get there.

- `LineElementIndexTest` asserts `getElementIndex` agrees with the element's true position
  after every one of the five mutation paths, after undo, and after redo.

- `getElementIndex` returns `-1` for an element in another line and for a null element.

- `getElements()` and `getElements(int, int)` both reject mutation.

- Span attach/detach does **not** invalidate the element index.

- `Span.overlaps` and `Span.matches` no longer exist; `Span.toIndexString` is unchanged.

- `Line.anchorIndexOf` / `endIndexOf` resolve against the receiving line, and a span whose
  endpoint belongs to another line resolves to `-1`.

- `findSpans(Class)` resolves no endpoint positions and is asserted equal, in order, to the
  predicate-taking form.

- The phase 1 instrumentation is deleted; no counter or `DEBUG=1` dump remains in `Line`.

- The manual app check in phase 8 passes: all six span types render, edit, undo/redo and
  survive a save/load round trip.

## Out of scope — recorded, not done

- **`SpanIndex`** — a per-pass snapshot resolving every span endpoint into parallel arrays.
  Would replace the remaining span-list iteration (~30 hash gets per query) with array reads,
  at the cost of a snapshot-lifetime contract reaching roughly 25 files. Revisit only if
  phase 8's numbers show the remaining iteration matters.

From the parked review at `plans/span-index-resolution-review.md`:

- **Finding 4** — `src/main/java/songscribe/layout/LineEndingSupport.java` is now a class
  holding one static method. Raised as a question, not a defect.

- `src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java:180` — one pass over
  five span types with different side effects per type; converting it would mean a fourth
  predicate and five passes instead of one.
