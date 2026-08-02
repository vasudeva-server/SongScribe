# Span Endpoint Index Resolution (issue #722)
**Created:** 2026-08-02 **Branch:** `722-index-resolution` (base: `develop`)

Collapse the near-identical "iterate spans, filter by type, resolve both endpoint indices, compare" loops into one resolver, so the span-query logic exists once instead of a dozen times.

This is **step 1 of issue #722 only**. Step 2 — the per-pass `SpanIndex` snapshot that would let layout, render, MIDI and MusicXML resolve every endpoint once per pass instead of once per query — is deliberately **out of scope** and filed as its own issue. The issue itself disclaims performance as the motivation ("at realistic line sizes the cost is negligible"), and the snapshot introduces a staleness failure mode that no unit test can catch. This plan therefore adds no new state, no cache, and no invalidation.

One commit at the end.

> **Line numbers** were read from the working tree on 2026-08-02 and are accurate as of the start of Phase 1. Issue #722's own line numbers are stale — it predates commit `0e306577` (`RangeElement` → `Span`) and the `Line.java` restructuring that shrank the file from ~2700 to 1838 lines. Re-locate by symbol name if anything has drifted.

* * *
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1 | [Span predicate and SpanLookup](#-phase-1-span-predicate-and-spanlookup) | ✅ Complete | — |
| 2 | [Line adopts SpanLookup](#-phase-2-line-adopts-spanlookup) | ✅ Complete | — |
| 3 | [Ending migration and MusicEditOperations](#-phase-3-ending-migration-and-musiceditoperations) | ✅ Complete | — |
| 4 | [Tests](#-phase-4-tests) | ✅ Complete | — |
| 5 | [Gate and commit](#-phase-5-gate-and-commit) | ✅ Complete | — |

* * *
## The shape being built

```
                    ┌──────────────────────────────────────────────┐
                    │  Span.java                                   │
                    │                                              │
                    │  @FunctionalInterface IndexPredicate         │
                    │      boolean test(int anchor, int end)       │
                    │                                              │
                    │  static containing(i)    GUARDED  >= 0       │
                    │  static overlapping(b,e) UNGUARDED           │
                    │  static exactly(a,e)     exact equality      │
                    │                                              │
                    │  boolean matches(IndexPredicate)             │
                    │  boolean overlaps(b,e) → matches(overlapping)│
                    └────────────────────┬─────────────────────────┘
                                         │ used by
                    ┌────────────────────▼─────────────────────────┐
                    │  SpanLookup.java  (interface)                │
                    │                                              │
                    │  ABSTRACT — the only thing an implementor    │
                    │  supplies:                                   │
                    │      List<Span> getSpans()                   │
                    │      int anchorIndexOf(Span)                 │
                    │      int endIndexOf(Span)                    │
                    │                                              │
                    │  THE ONLY TWO METHODS THAT ITERATE:          │
                    │      findSpans(Class, IndexPredicate)        │
                    │      findFirstSpan(Class, IndexPredicate)    │
                    │                                              │
                    │  findSpans(Class)  → findSpans(C, always)    │
                    │  hasSpan(Class, p) → findFirstSpan != null   │
                    │                                              │
                    │  17 typed queries, each ONE line delegating  │
                    │  to the four above with a factory predicate  │
                    └────────────────────┬─────────────────────────┘
                                         │ implements
                    ┌────────────────────▼─────────────────────────┐
                    │  Line.java                                   │
                    │      getSpans()      — already exists        │
                    │      anchorIndexOf() — span.getAnchorEl.Idx  │
                    │      endIndexOf()    — span.getEndElementIdx │
                    │                                              │
                    │  ~250 lines of query bodies deleted          │
                    └──────────────────────────────────────────────┘
```

`Line` is the only implementor. The interface earns its place by lifting ~250 lines of pure query logic out of `Line.java` — which issue #722 calls "the contended file" at 1838 lines — separating "the line as a mutable element container" from "query spans by index". A static `SpanQueries` helper taking `List<Span>` was considered and rejected: it would force ~40 call sites to `SpanQueries.findBeamAt(line.getSpans(), i)`.

## The one behavior change in this plan

`Span.containing` carries a `>= 0` guard on both indices. Nine of the ten containment call sites already guard today. The single one that does not is `LineEndingSupport.findEndingAt`, so **exactly one method changes behavior**: an ending whose anchor or end element no longer resolves to a position (it was detached from the line) stops being reported as covering an element.

That is the intended semantics — a span with a dangling endpoint contains nothing — and it reaches production through two callers:

- `MidiSequenceBuilder:216` — `findEndingAt(endings, noteIndex)`
- `MusicEditOperations:888` — `isInsideAnyEnding(endings, i)`

Phase 4 tests it explicitly. `Span.overlapping` must stay **unguarded** to match today's `Span.overlaps` (`Span.java:259`); guarding it would stop `Line.removeTrillsOverlapping` (`:1652`) and `Line.addTrill` (`:1641`) from cleaning up a half-detached trill.

## Dead methods being removed

These five public methods have **zero** callers in `src/main/java` — verified by `rg` for both qualified and unqualified forms, and there are no Kotlin sources. Rather than port dead code onto the new interface and write fresh tests for it, `jet_brains_safe_delete` them along with their tests.

| Method | File | Tests to delete |
| --- | --- | --- |
| `findSpansAt(int)` | `Line.java:1758` | `LineMutationTest` `FindSpansAt` nested class (`:1968-2029`) |
| `isStartOfAnyBeam(int)` | `Line.java:842` | 4 cases in `LineBeamTest` |
| `isEndOfAnyBeam(int)` | `Line.java:855` | 4 cases in `LineBeamTest` |
| `isStartOfAnyEnding(List,int)` + `(Line,int)` | `LineEndingSupport.java:71, 82` | `LineEndingSupportTest` `Row 29` block |
| `isEndOfAnyEnding(List,int)` + `(Line,int)` | `LineEndingSupport.java:87, 98` | `LineEndingSupportTest` `Row 30` block |

Because those four `isStartOf…`/`isEndOf…` methods are the *only* consumers of a "does this index equal the anchor / the end" test, `Span` needs **three** predicate factories, not five — `startingAt` and `endingAt` would have no users and are not created.

* * *
## ✅ Phase 1: Span predicate and SpanLookup
**Status:** Complete
**BlockedBy:** —
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/SpanLookup.java
**Recommended model/effort:** Opus 4.8, high — the guard semantics are a real behavior decision and this phase fixes the API every later phase writes against.

### Context this phase needs
`Span` (`src/main/java/songscribe/dom/Span.java`) stores element *references* (`anchorElement` / `endElement`). `getAnchorElementIndex()` (`:243`) and `getEndElementIndex()` (`:251`) convert a reference to a position via the private `indexInLine` (`:225`), which asks the element for its parent line and calls `Line.getElementIndex`. Both return `-1` when the endpoint is unset or its element sits in no line.

This phase is purely additive — nothing is deleted and no existing file outside `Span.java` is touched, so it compiles on its own.

### Design to implement

**1.** `Span.IndexPredicate` — a nested functional interface on `Span`, following `Ending.EndingEffect`'s precedent for nesting. Java has no built-in two-int predicate.

```java
/** A test on a span's resolved anchor and end element indices. */
@FunctionalInterface
public interface IndexPredicate {
    boolean test(int anchorIndex, int endIndex);
}
```

**2. Three static factories on `Span`:**

| Factory | Semantics |
| --- | --- |
| `containing(int elementIndex)` | `anchor >= 0 && end >= 0 && anchor <= elementIndex && elementIndex <= end` |
| `overlapping(int begin, int end)` | `anchor <= end && spanEnd >= begin` — **no** `>= 0` guard |
| `exactly(int anchorIndex, int endIndex)` | `anchor == anchorIndex && spanEnd == endIndex` |

**3. An instance method so a predicate can be applied to one span:**

```java
public boolean matches(IndexPredicate predicate) {
    return predicate.test(getAnchorElementIndex(), getEndElementIndex());
}
```

**4.** `src/main/java/songscribe/dom/SpanLookup.java` — every span query as a default method over three abstract accessors.

```java
public interface SpanLookup {

    /** The spans to query, in line order. */
    List<Span> getSpans();

    /** The anchor element's position in the line, or -1 when unresolvable. */
    int anchorIndexOf(Span span);

    /** The end element's position in the line, or -1 when unresolvable. */
    int endIndexOf(Span span);

    // --- the only two methods that iterate ---------------------------------

    default <T extends Span> List<T> findSpans(Class<T> type, Span.IndexPredicate matches)
    default <T extends Span> @Nullable T findFirstSpan(Class<T> type, Span.IndexPredicate matches)

    // --- expressed via those two -------------------------------------------

    default <T extends Span> List<T> findSpans(Class<T> type)
    default boolean hasSpan(Class<? extends Span> type, Span.IndexPredicate matches)

    // --- typed queries, all one line ---------------------------------------

    default List<Beam> findBeamsOverlapping(int begin, int end)
    default @Nullable Beam findBeamAt(int elementIndex)
    default boolean sameBeamAt(int firstIndex, int secondIndex)
    default List<Tie> findTies()
    default @Nullable Tie findTieAt(int elementIndex)
    default @Nullable Tie findExactTie(int anchorIndex, int endIndex)
    default boolean sameTieAt(int firstIndex, int secondIndex)
    default @Nullable Tuplet findTupletAt(int elementIndex)
    default List<Tuplet> findTupletsOverlapping(int begin, int end)
    default List<Crescendo> getCrescendos()
    default List<Diminuendo> getDiminuendos()
    default boolean isInHairpinRange(int noteIndex)
    default List<Trill> findTrillsOverlapping(int beginIndex, int endIndex)
    default boolean hasTrillOverlapping(int beginIndex, int endIndex)
    default List<Ending> findEndings()
    default @Nullable Ending findEndingAt(int elementIndex)
    default boolean isInsideAnyEnding(int elementIndex)
}
```

**Critically:** `findSpans(Class, IndexPredicate)` and `findFirstSpan` must resolve indices through `anchorIndexOf(span)` / `endIndexOf(span)` — **not** through `span.getAnchorElementIndex()` — because those two accessors are the whole extension point of the interface.

Predicate mapping for the typed queries, matching today's bodies exactly:

| Query | Body |
| --- | --- |
| `findBeamsOverlapping(b,e)` | `findSpans(Beam.class, Span.overlapping(b, e))` |
| `findBeamAt(i)` | `findFirstSpan(Beam.class, Span.containing(i))` |
| `sameBeamAt(a,b)` | unchanged — two `findBeamAt` calls, reference-equal, keep the `//noinspection ObjectEquality` |
| `findTies()` | `findSpans(Tie.class)` |
| `findTieAt(i)` | `findFirstSpan(Tie.class, Span.containing(i))` |
| `findExactTie(a,e)` | `findFirstSpan(Tie.class, Span.exactly(a, e))` |
| `sameTieAt(a,b)` | unchanged — two `findTieAt` calls, keep the `//noinspection ObjectEquality` |
| `findTupletAt(i)` | `findFirstSpan(Tuplet.class, Span.containing(i))` |
| `findTupletsOverlapping(b,e)` | `findSpans(Tuplet.class, Span.overlapping(b, e))` |
| `getCrescendos()` | `findSpans(Crescendo.class)` |
| `getDiminuendos()` | `findSpans(Diminuendo.class)` |
| `isInHairpinRange(i)` | `hasSpan(Hairpin.class, Span.containing(i))` |
| `findTrillsOverlapping(b,e)` | `findSpans(Trill.class, Span.overlapping(b, e))` |
| `hasTrillOverlapping(b,e)` | `hasSpan(Trill.class, Span.overlapping(b, e))` |
| `findEndings()` | `findSpans(Ending.class)` |
| `findEndingAt(i)` | `findFirstSpan(Ending.class, Span.containing(i))` |
| `isInsideAnyEnding(i)` | `hasSpan(Ending.class, Span.containing(i))` |

### Tasks
1. Add `Span.IndexPredicate`, the three static factories, and `Span.matches(IndexPredicate)` to `src/main/java/songscribe/dom/Span.java`. Reimplement the existing `Span.overlaps(int, int)` (`:259`) as `return matches(overlapping(begin, end));` so the overlap expression exists in exactly one place.

2. Javadoc the guard asymmetry on the factories themselves, since it is the plan's only behavior change:
   - `containing` — state that a span with an unresolvable endpoint (`-1`) contains nothing, so a half-detached span is not reported as covering an element.
   - `overlapping` — state that it deliberately does **not** guard, so the trill removal sweeps (`Line.addTrill`, `Line.removeTrillsOverlapping`) still find and clean up a half-detached span.

3. Create `src/main/java/songscribe/dom/SpanLookup.java` with the three abstract accessors and every default method in the table above. `findSpans(Class)` and `findFirstSpan` are the only two loops in the file.

4. Move the Javadoc from each `Line` method onto its `SpanLookup` counterpart rather than rewriting it. Two carry information that must survive verbatim:
   - `findExactTie` (`Line.java:886-892`) — why it exists at all: it disambiguates chained ties that share an endpoint note, which `findTieAt` cannot.
   - `hasTrillOverlapping` (`Line.java:1668-1671`) — that it short-circuits and allocates no intermediate list. Routing it through `hasSpan` → `findFirstSpan` preserves that; routing it through `findSpans(...).isEmpty()` would not.

5. Run `./scripts/compile.sh` and report SUCCESS.

* * *
## ✅ Phase 2: Line adopts SpanLookup
**Status:** Complete
**BlockedBy:** 1
**Files:** src/main/java/songscribe/dom/Line.java, src/test/java/songscribe/dom/LineMutationTest.java, src/test/java/songscribe/dom/LineBeamTest.java
**Recommended model/effort:** Opus 4.8, high — the `mergeOverlappingSpans` rewrite is the one piece of real logic in this plan, and the safe-deletes need judgment if a usage turns up.

### Context this phase needs
Phase 1 added `Span.IndexPredicate` with factories `Span.containing(int)`, `Span.overlapping(int,int)`, `Span.exactly(int,int)`, the instance method `Span.matches(IndexPredicate)`, and the interface `songscribe.dom.SpanLookup` carrying `findSpans(Class)`, `findSpans(Class, IndexPredicate)`, `findFirstSpan(Class, IndexPredicate)`, `hasSpan(Class, IndexPredicate)` and seventeen typed queries.

`Line` is declared `public class Line implements LyricRun` (`Line.java:62`) — adding `SpanLookup` introduces no conflict; there is no existing `findFirstSpan`, `hasSpan`, `anchorIndexOf` or `endIndexOf` anywhere in `src/main/java`.

`Line.getSpans()` (`:1748`) already satisfies the interface's `getSpans()` signature exactly (`List<Span>`).

### Tasks
1. Declare `Line implements LyricRun, SpanLookup` and add the two accessors:

   ```java
   @Override
   public int anchorIndexOf(Span span) {
       return span.getAnchorElementIndex();
   }

   @Override
   public int endIndexOf(Span span) {
       return span.getEndElementIndex();
   }
   ```

2. Cache the unmodifiable spans view. `getSpans()` (`:1748`) currently builds a fresh `Collections.unmodifiableList(spans)` wrapper per call; today's query loops read the private `spans` field directly, but the interface defaults must all go through `getSpans()`, which would make every query allocate. Assign the view once to a `private final List<Span> spansView` initialized beside the `spans` field and return it. `Collections.unmodifiableList` returns a live view, so it tracks every later mutation of `spans` — caching it is safe.

3. **Delete** these twelve now-inherited method bodies from `Line.java`. Every caller is unaffected: the inherited defaults have identical signatures.

   `findBeamsOverlapping` (:822), `findTieAt` (:870), `findExactTie` (:893), `sameTieAt` (:909), `findTies` (:944), `findTupletAt` (:953), `findTupletsOverlapping` (:971), `getCrescendos` (:988), `getDiminuendos` (:992), `findBeamAt` (:1001), `sameBeamAt` (:1020), `isInHairpinRange` (:1538), `findTrillsOverlapping` (:1661), `hasTrillOverlapping` (:1672), `findSpans(Class)` (:1780).

   Leave `Line.getSpans()` (:1748) — it is the interface's own accessor. Leave `Line.getFirstTrill` (:1606), which already goes through `findSpans(Trill.class)`. Leave `Line.adjustHairpinsForDeletion` (:1279): its loop builds post-deletion survivor bookkeeping, not a filter.

4. `jet_brains_safe_delete` the three dead `Line` methods — `findSpansAt` (:1758), `isStartOfAnyBeam` (:842), `isEndOfAnyBeam` (:855) — and remove their tests: the `FindSpansAt` nested class in `LineMutationTest` (`:1968-2029`) and the four `isStartOfAnyBeam` / four `isEndOfAnyBeam` cases in `LineBeamTest`. If safe-delete reports a production usage, **stop and report it** — the "zero main callers" finding is what justifies the deletion.

5. Rewrite the two loops inside `Line.mergeOverlappingSpans` (:1080). The first (:1096) accumulates merged bounds via `Math.min` / `Math.max` seeded with `anchorIdx` / `endIdx`. Because every span matching the first predicate satisfies `anchor <= anchorIdx`, `min().orElse(anchorIdx)` is exactly today's accumulation — and symmetrically for the end:

   ```java
   // How far past an endpoint an existing span may sit and still be absorbed.
   var reach = absorbAdjacent ? SPAN_ADJACENCY_REACH : 0;

   var mergedAnchorIdx = findSpans(type, (anchor, end) -> anchor <= anchorIdx && anchorIdx <= end + reach)
       .stream().mapToInt(Span::getAnchorElementIndex).min().orElse(anchorIdx);
   var mergedEndIdx = findSpans(type, (anchor, end) -> anchor - reach <= endIdx && endIdx <= end)
       .stream().mapToInt(Span::getEndElementIndex).max().orElse(endIdx);
   ```

   Both must be computed **before** the `setAnchorElement` / `setEndElement` calls at :1113/:1117, which mutate the very indices the predicates read.

   The second loop (the `subsumedSpans` stream at :1122) becomes `findSpans(type, (anchor, end) -> anchor >= finalMergedAnchor && end <= finalMergedEnd)`, which also drops today's double `type.cast(re)` re-resolution. It still runs **after** the setters, as today.

   These two predicates are inline lambdas, not factories — no other call site wants a reach-adjusted containment test, and naming them would be speculative.

   `mergeOverlappingSpans` runs before the new span is appended to `spans` — both callers, `addBeaming` (:1063) and `addHairpin` (:1231), call `appendChild` afterwards — so the span being merged is not in its own search. Re-verify this before relying on it.

6. Run `./scripts/compile.sh`, then `./scripts/test.sh unit LineHairpinMergeTest LineBeamTest LineTieTest LineTupletTest LineTrillTest LineIsInHairpinRangeTest LineMutationTest LineQueryTest LineHairpinDeletionTest`. These ~2500 lines are the direct regression net for every body just deleted; `LineHairpinMergeTest` in particular is the expected-result check on the `mergeOverlappingSpans` rewrite. All must be green. If any fails, fix the production code — do not adjust expectations.

* * *
## ✅ Phase 3: Ending migration and MusicEditOperations
**Status:** Complete
**BlockedBy:** 2
**Files:** src/main/java/songscribe/layout/LineEndingSupport.java, src/main/java/songscribe/ui/renderer/EndingRenderer.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java, src/main/java/songscribe/io/musicxml/MusicXmlSpanIndex.java, src/main/java/songscribe/io/LineIO.java
**Recommended model/effort:** Sonnet 4.6, medium — mechanical receiver swaps against an API Phase 1 fixed, with one exact call-site list.

### Context this phase needs
Phase 1 put `findEndings()`, `findEndingAt(int)` and `isInsideAnyEnding(int)` on `songscribe.dom.SpanLookup`; Phase 2 made `Line` implement it. So `line.findEndings()`, `line.findEndingAt(i)` and `line.isInsideAnyEnding(i)` all work now.

`LineEndingSupport` (`src/main/java/songscribe/layout/LineEndingSupport.java`, 120 lines) is a static helper class in `layout/` that exists only because there was no place on the DOM to hang ending queries. Leaving it in place would mean ending queries live in a different package from every other span query — the exact duplication issue #722 is about.

`isStartOfAnyEnding` and `isEndOfAnyEnding` (both overloads each) have **zero** main-code callers and are deleted, not migrated — see "Dead methods being removed" above.

`findEndingReplacementEffect` (`:109`) stays: it is not an index query, it maps endings through `checkReplacement`. Its three callers (`SelectionCoordinator:1044`, `PreviewElementManager:2006`, `LineEndingSupportTest`) are untouched. Keep the class name `LineEndingSupport` — it is still ending support for a line, and renaming would churn three more files for no gain.

### Tasks
1. In `src/main/java/songscribe/layout/LineEndingSupport.java`, delete `findEndings` (`:37`), both `findEndingAt` overloads (`:42`, `:56`), both `isInsideAnyEnding` overloads (`:61`, `:66`), both `isStartOfAnyEnding` overloads (`:71`, `:82`) and both `isEndOfAnyEnding` overloads (`:87`, `:98`). Rewrite `findEndingReplacementEffect`'s body (`:114`) to use `line.findEndings()`. Update the class Javadoc (`:31`), which currently reads "Static helpers for querying `Ending` elements on a `Line`" — it now holds one replacement-effect check. Drop the `List` and `Nullable` imports if they go unused.

2. Update the seven main-code call sites of `LineEndingSupport.findEndings(line)` to `line.findEndings()`, removing the now-unused `songscribe.layout.LineEndingSupport` import from each file where nothing else uses it:

   | File | Line |
   | --- | --- |
   | `ui/renderer/EndingRenderer.java` | `:84`, `:130` |
   | `ui/MusicEditOperations.java` | `:885` |
   | `midi/MidiSequenceBuilder.java` | `:138`, `:209` |
   | `io/musicxml/MusicXmlSpanIndex.java` | `:293` |
   | `io/LineIO.java` | `:129` |

3. Update the two main-code call sites of the deleted list-taking overloads:
   - `midi/MidiSequenceBuilder.java:216` — `LineEndingSupport.findEndingAt(endings, noteIndex)` → `line.findEndingAt(noteIndex)`. The local `endings` list built at `:209` may become unused; if the loop body has no other use for it, delete it, otherwise leave it.
   - `ui/MusicEditOperations.java:888` — `LineEndingSupport.isInsideAnyEnding(endings, i)` → `line.isInsideAnyEnding(i)`, same check on the `endings` local from `:885`.

   Both of these now go through `Span.containing`, which guards on `>= 0`. That is the plan's one intended behavior change: an ending whose anchor or end element has been detached from the line no longer reports as covering an element.

4. In `src/main/java/songscribe/ui/MusicEditOperations.java`, delete the private helper `nearbySpans` (`:519`) and inline its two call sites in `findHairpinsNearSelection` (`:511-512`):

   ```java
   return new HairpinScan(
       line.findSpans(Crescendo.class, Span.overlapping(scanBegin, scanEnd)),
       line.findSpans(Diminuendo.class, Span.overlapping(scanBegin, scanEnd)));
   ```

   The `HairpinScan` record (`:491`) needs **no** change: `nearbySpans(line.getCrescendos(), …)` returns `List<Crescendo>` today and `findSpans(Crescendo.class, …)` returns `List<Crescendo>` too. Add `songscribe.dom.Span` to the imports; `Crescendo` and `Diminuendo` are already imported. Drop the `Hairpin` import if `nearbySpans` was its only user.

5. In the same file, leave the hairpin-extension loop at `:693` as a loop — it accumulates `spanBegin`/`spanEnd` and early-returns, so a boolean predicate cannot express it. Delete only the now-obsolete comment on `:694` (*"Each index accessor scans the line for the element, so read them once."*), which issue #722 cites as a hand-rolled workaround; keep the two local reads it justified, since they are still two accessor calls each used twice.

6. Run `./scripts/compile.sh` and report SUCCESS.

* * *
## ✅ Phase 4: Tests
**Status:** Complete
**BlockedBy:** 3
**Files:** src/test/java/songscribe/dom/SpanLookupTest.java, src/test/java/songscribe/layout/LineEndingSupportTest.java, src/test/java/songscribe/io/musicxml/MusicXmlEndingRoundTripTest.java, src/test/java/songscribe/io/musicxml/MusicXmlReaderLenienceTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerTest.java
**Recommended model/effort:** Sonnet 4.6, medium — new coverage for three predicate factories plus a mechanical migration of eleven existing call sites.

### Context this phase needs
Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` before writing anything. Mirror the fixture style of `src/test/java/songscribe/dom/LineMutationTest.java`, which already builds lines with spans, and `src/test/java/songscribe/layout/EndingLineFixture.java`, which builds ending-carrying lines.

The API under test: `Span.IndexPredicate` with `Span.containing(int)`, `Span.overlapping(int,int)`, `Span.exactly(int,int)`; `Span.matches(IndexPredicate)`; and `songscribe.dom.SpanLookup` (implemented by `Line`) carrying `findSpans(Class)`, `findSpans(Class, IndexPredicate)`, `findFirstSpan`, `hasSpan` and seventeen typed queries.

`LineEndingSupportTest`'s ending fixtures — `EndingLineFixture.primary()` / `.secondary()` and the local `oneEndingList()` (`:128-149`) — all attach their endings to a real `Line` via `line.addElement` + `line.addSpan`, so every index resolves `>= 0` and the `containing` guard does **not** flip their expectations. Confirm this still holds; if a fixture stops attaching, the guard change would silently break those cases.

### Tasks
1. Create `src/test/java/songscribe/dom/SpanLookupTest.java` covering the three predicate factories directly against a `Line` with known spans:
   - `containing` — at the anchor, in the interior, at the end, one before, one after.
   - `overlapping` — at each boundary of `[begin, end]` and just outside both.
   - `exactly` — distinguishing two chained ties that share an endpoint note, which is the case `findTieAt` cannot resolve and the reason `findExactTie` exists.

2. Add the guard cases, which are the only behavior this refactor changes. Build a span, remove its anchor element from the line with `Line.removeElement`, and **assert `getAnchorElementIndex()` returns -1 first** so the test fails loudly if that precondition stops holding. Then assert:
   - `line.findEndingAt(i)` and `line.isInsideAnyEnding(i)` return null / false for a half-detached ending — the tightening.
   - `line.findTrillsOverlapping(b, e)` and `line.hasTrillOverlapping(b, e)` **still find** a half-detached trill — the deliberate non-tightening that keeps the removal sweeps working.

3. Add one case pinning `hasTrillOverlapping`'s documented short-circuit: with two overlapping trills present it returns `true` without materializing a list. Asserting the result plus keeping the Javadoc claim is enough — do not add allocation instrumentation.

4. Rewrite `src/test/java/songscribe/layout/LineEndingSupportTest.java` against the new home. The `findEndings` (Row 25) and `findEndingAt` (Row 26) blocks move to exercise `line.findEndings()` / `line.findEndingAt(i)`, keeping their existing boundary constants and expectations — `BEFORE_START`, `AT_START`, `INSIDE`, `AT_END`, `AFTER_END` are good coverage and must survive. The `isInsideAnyEnding` (Row 28) block moves the same way. **Delete** the `isStartOfAnyEnding` (Row 29) and `isEndOfAnyEnding` (Row 30) blocks — those methods no longer exist. Keep the `findEndingReplacementEffect` (Row 31) block exactly as it is. Update the class Javadoc at `:36`, which enumerates the old method list. The empty-list cases (`findEndingAt(List.of(), …)`) have no equivalent — replace each with a line that has no endings.

5. Update the remaining `LineEndingSupport.findEndings(...)` test call sites to `…findEndings()` and drop the import from each file: `MusicXmlEndingRoundTripTest` (`:88`, `:127`, `:168`, `:226`, `:261`, `:299`, `:337`), `MusicXmlReaderLenienceTest` (`:567`, `:701`), `ScoreViewControllerTest` (`:653`).

6. Run `./scripts/compile.sh`, then `./scripts/test.sh unit`. Both must be green.

* * *
## ✅ Phase 5: Gate and commit
**Status:** Complete
**BlockedBy:** 4
**Files:** —
**Recommended model/effort:** Sonnet 4.6, low — verification sweep and commit.

### Tasks
1. Confirm no `for` loop anywhere in `src/main/java/` both iterates a span collection and resolves an endpoint index inside the body:

   ```bash
   rg -n -U 'for \(.*\{[^}]*getAnchorElementIndex' src/main/java
   ```

   Read the survivors. Exactly two are expected and correct: `Line.adjustHairpinsForDeletion` (post-deletion survivor bookkeeping, not a filter) and `MusicEditOperations:693` (accumulates bounds and early-returns). Anything else is a missed call site — fix it.

2. Confirm the deletions took: `rg -n 'findSpansAt|isStartOfAnyBeam|isEndOfAnyBeam|isStartOfAnyEnding|isEndOfAnyEnding' src/` returns nothing.

3. Run `./scripts/compile.sh` and `./scripts/test.sh unit` one final time on the complete change. Both must be green.

4. Commit with the `/commit-commands:commit` skill. Title: `refactor: collapse span endpoint index resolution into one resolver`. Body as a bullet list — the `Span.IndexPredicate` seam, the `SpanLookup` extraction out of `Line.java`, the ending-query migration off `LineEndingSupport`, the five dead methods removed, and the one behavior change (`findEndingAt` now skips an ending with an unresolvable endpoint). Final line on its own: `refs #722` — this closes step 1 only; step 2 has its own issue.

* * *
## Verification (whole plan)
- `./scripts/compile.sh` reports SUCCESS after every phase.
- `./scripts/test.sh unit` is green after Phase 2 (targeted), Phase 4 (full) and Phase 5 (full).
- The only production behavior change is `findEndingAt` / `isInsideAnyEnding` skipping an ending with an unresolvable endpoint, reached via `MidiSequenceBuilder:216` and `MusicEditOperations:888`, and covered by a Phase 4 test.
- `Span.overlapping` remains unguarded: `Line.addTrill` and `Line.removeTrillsOverlapping` still clean up a half-detached trill.
- No `SpanIndex`, no cached field on any DOM object, no invalidation logic — the snapshot is step 2's job and is out of scope here.

## Out of scope — filed as #725
Step 2 of #722: build a `SpanIndex` snapshot per layout / render / MIDI / MusicXML pass so every endpoint resolves once instead of once per query. The `SpanLookup` interface this plan creates is exactly the seam it plugs into — `SpanIndex implements SpanLookup` backed by precomputed `int[]` arrays, with zero duplication of the query logic. Issue #725 carries the full call-site inventory and the invalidation proof.
