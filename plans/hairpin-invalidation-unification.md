# Unify span invalidation around a projection, and move hairpin policy onto Hairpin

**Type:** Master plan  <br>
**Created:** 2026-08-08  <br>
**Status:** Pending

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Hairpin rule relocation](#-phase-1-hairpin-rule-relocation) | ⏳ Pending | — |
| 2 | [Change and outcome vocabulary](#-phase-2-change-and-outcome-vocabulary) | ⏳ Pending | — |
| 3 | [Span.outcomeFor and the three existing overriders](#-phase-3-spanoutcomefor-and-the-three-existing-overriders) | ⏳ Pending | — |
| 4 | [Hairpin.outcomeFor](#-phase-4-hairpinoutcomefor) | ⏳ Pending | — |
| 5 | [Line's unified sweep](#-phase-5-lines-unified-sweep) | ⏳ Pending | — |
| 6 | [Delete the superseded hooks](#-phase-6-delete-the-superseded-hooks) | ⏳ Pending | — |
| 7 | [Hairpin invalidation coverage](#-phase-7-hairpin-invalidation-coverage) | ⏳ Pending | — |
| 8 | [Docs](#-phase-8-docs) | ⏳ Pending | — |
| 9 | [Full verification and manual UI check](#-phase-9-full-verification-and-manual-ui-check) | ⏳ Pending | — |

### Phase dependency graph

```
  1 ──┐
      ├──► 4 ──► 5 ──┬──► 6 ──► 7 ──┐
  2 ──► 3 ──┘        │              │
                     └──► 8 ────────┴──► 9
```

---

## Shared contract

Every phase from 3 onward codes against the two types Phase 2 creates. Their exact
shape, so no phase has to invent or rediscover it:

`src/main/java/songscribe/dom/ElementChange.java`

```java
public sealed interface ElementChange {

    /** The line's elements as they will be once this change is applied. */
    List<StaffElement> projectedElements();

    /** The position of {@code element} in {@link #projectedElements}, or -1 if it is not there. */
    int projectedIndexOf(StaffElement element);

    record Insertion(int index, StaffElement inserted,
                     List<StaffElement> projectedElements,
                     Map<StaffElement, Integer> projectedIndices) implements ElementChange { }

    record Replacement(int index, StaffElement oldElement, StaffElement newElement,
                       List<StaffElement> projectedElements,
                       Map<StaffElement, Integer> projectedIndices) implements ElementChange { }

    record Deletion(List<StaffElement> deletedElements,
                    List<StaffElement> projectedElements,
                    Map<StaffElement, Integer> projectedIndices) implements ElementChange { }
}
```

`src/main/java/songscribe/dom/SpanOutcome.java`

```java
public sealed interface SpanOutcome {

    /** The span is unaffected. */
    record Keep() implements SpanOutcome { }

    /** The span cannot survive the change and must be removed. */
    record Remove() implements SpanOutcome { }

    /**
     * The span survives over {@code [begin, end]}, as indices into the change's
     * {@link ElementChange#projectedElements}.
     */
    record Reshape(int begin, int end) implements SpanOutcome { }
}
```

The new hook, added to `Span` in Phase 3:

```java
public SpanOutcome outcomeFor(ElementChange change, Line line)
```

**Invariants every phase must preserve:**

- **Decisions are made before the change lands.** Undo replays a step's mutations in
  reverse, so a companion span mutation must be recorded *before* the primary element
  mutation. Deciding after the fact is wrong: once `Line.setElement` has re-pointed a
  span at the new element, a removal recorded then carries a span pointing at the wrong
  element, and undo restores it broken. Everything is decided against the projection,
  never against the mutated line.
- **A replacement re-points, it does not delete.** `Line.setElement` points any span
  whose anchor or end was the replaced element at the replacement. So under
  `ElementChange.Replacement`, a span whose endpoint is `oldElement` must be read as
  having that endpoint become `newElement` — *not* as having lost an endpoint. Getting
  this wrong silently removes every tie, tuplet and ending whose endpoint is edited.
- **Every sweep is skipped while `song.isReplaying()`** — the recorded batch already
  carries the companion removals, and re-deriving them double-applies.
- **A reshape is a tracked removal plus a tracked addition of a copy**, because spans
  have no modification mutation.
- **Tie, Tuplet and Ending behaviour must not change.** They only ever answer `Keep` or
  `Remove`.

**Two questions already investigated and settled — do not re-open:**

- `Line.mergeOverlappingSpans` (`Line.java:1050`) is **generic**, not hairpin-specific:
  `Line.addBeaming` calls it for `Beam` with `absorbAdjacent=false` (`Line.java:1030`)
  and `Line.addHairpin` calls it for hairpins with `true` (`Line.java:1218`). It stays
  on `Line`.
- `Line.SPAN_ADJACENCY_REACH` (`Line.java:156`, value 1) is read by that generic method
  at `Line.java:1058`. It stays public on `Line`. `MusicEditOperations` and
  `Line.mergeAdjacentSpans` keep reading it from there.

---

## ⏳ Phase 1: Hairpin rule relocation

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — a pure move of five static methods with their Javadoc, plus delegation; no behaviour changes and the existing suite gates it

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

`songscribe.dom.Line` currently holds the rules for what may anchor or end a hairpin.
That is knowledge about hairpins, and it belongs on `Hairpin`. The methods already have
the right shape — they take a candidate element list rather than reading the line's own
field — because the hairpin menu asks "could a hairpin end at index 7?" before any
hairpin instance exists. As statics on `Hairpin` they serve that caller directly.

**This phase changes no behaviour.** Every existing test must pass unchanged at the end
of it.

### Tasks

1. Move these five members from `src/main/java/songscribe/dom/Line.java` to
   `src/main/java/songscribe/dom/Hairpin.java`, carrying their Javadoc and inline
   comments verbatim, and widening each from `private static` to `public static`:
   - `canAnchorHairpin(List<? extends StaffElement> candidates, int index, int lastIndex)`
     (`Line.java:1428`) → `Hairpin.canAnchorAt(List<? extends StaffElement> candidates, int index, int lastIndex)`
   - `canEndHairpin(List<? extends StaffElement> candidates, int index)`
     (`Line.java:1462`) → `Hairpin.canEndAt(List<? extends StaffElement> candidates, int index)`
   - `precedingDurationIsPitchedNote(List<? extends StaffElement> candidates, int index)`
     (`Line.java:1483`) → same name on `Hairpin`, kept **private**
   - `resolveBeginIndex(Hairpin hairpin, int first, int last, List<? extends StaffElement> survivors)`
     (`Line.java:1379`) → `Hairpin.resolveBeginIndex(int first, int last, List<? extends StaffElement> candidates)`,
     an **instance** method (it already takes the hairpin as its first parameter — drop
     that parameter and read `this`)
   - `resolveEndIndex(Hairpin hairpin, int first, int last, List<? extends StaffElement> survivors)`
     (`Line.java:1537`) → `Hairpin.resolveEndIndex(int first, int last, List<? extends StaffElement> candidates)`,
     likewise an instance method

   Prefer serena's `jet_brains_move`; if it refuses a move that also changes the
   signature, move by hand and fix the call sites named in tasks 2 and 3.

2. In `Line.java`, keep the two public instance conveniences —
   `canAnchorHairpin(int index, int lastIndex)` (`Line.java:1408`) and
   `canEndHairpin(int index)` (`Line.java:1444`) — but reduce each body to a delegation,
   e.g. `return Hairpin.canEndAt(elements, index);`. Rewrite their Javadoc to say the
   rule lives on `Hairpin` and that these exist only so a caller holding a `Line` and an
   index need not reach for the element list. Update the two remaining internal callers:
   `Line.survivingSpanOf` (`Line.java:1327`) calls the relocated
   `hairpin.resolveBeginIndex(...)` / `hairpin.resolveEndIndex(...)`, and
   `Line.hairpinSurvivesReplacement` (`Line.java:1509`) calls `Hairpin.canAnchorAt` /
   `Hairpin.canEndAt`.

3. Also move `Line.hairpinSurvivesReplacement(Hairpin hairpin, int index, StaffElement replacement)`
   (`Line.java:1509`) onto `Hairpin` as an instance method
   `survivesReplacement(List<? extends StaffElement> candidates, int index, StaffElement replacement)`,
   dropping the `hairpin` parameter in favour of `this` and taking the candidate list
   instead of reading `Line.elements`. Its only caller is
   `Hairpin.isInvalidatedByReplacement` in the same file, which becomes
   `return !survivesReplacement(line.elementList(), replacedIndex, newElement);`.

   `Line` has no public accessor returning the element list. Add one:

   ```java
   /** This line's elements, unmodifiable — for callers that reason over the whole list. */
   public List<StaffElement> elementList() {
       return Collections.unmodifiableList(elements);
   }
   ```

   `java.util.Collections` is already imported (`Line.java:23`).

4. In `src/main/java/songscribe/ui/MusicEditOperations.java`, the resolution calls
   `line.canAnchorHairpin(begin, spanEnd)` and `line.canEndHairpin(end)`. Leave those
   call sites as they are — they read well and task 2 kept the conveniences — but update
   the two Javadoc tags in `resolveHairpinAction`'s doc comment that currently read
   `{@link Line#canAnchorHairpin}` and `{@link Line#canEndHairpin}` so they point at
   `{@link Hairpin#canAnchorAt}` and `{@link Hairpin#canEndAt}`, since that is where the
   rule now lives.

5. Run `./scripts/compile.sh --test` (SUCCESS), then `./scripts/test.sh` and confirm the
   full unit suite is green. Nothing should need changing in any test — if a test fails,
   the move altered behaviour and must be corrected rather than the test.

---

## ⏳ Phase 2: Change and outcome vocabulary

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/ElementChange.java, src/main/java/songscribe/dom/SpanOutcome.java  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — two new self-contained files whose exact shape is given in the plan's Shared contract section

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

Create the two types the rest of the plan codes against. Nothing references them yet, so
this phase compiles standalone and can run alongside any other.

### Tasks

1. Create `src/main/java/songscribe/dom/ElementChange.java` exactly as given in the
   plan's **Shared contract** section: a `public sealed interface ElementChange` with the
   two accessors and the three permitted records `Insertion`, `Replacement`, `Deletion`.
   Implement `projectedIndexOf(StaffElement)` once as a default method reading
   `projectedIndices()`, returning `-1` when absent, so the three records do not each
   restate it.

2. Give the interface and each record real Javadoc. The interface documents that it
   describes a **pending** change — the line has not been mutated yet — and that
   `projectedElements` is what the line will look like afterwards, which is the single
   thing a span is asked to judge itself against. Each record documents its own fields.
   `Replacement` must carry the warning that a replacement **re-points** a span's
   endpoint rather than deleting it: `oldElement` is absent from `projectedElements` and
   `newElement` sits at `index`, and a span whose endpoint was `oldElement` is to be read
   as having that endpoint become `newElement`.

3. Add static factory methods on `ElementChange` that build each record from a line's
   element list, so callers never assemble the projection or the index map by hand:
   `forInsertion(List<StaffElement> elements, int index, StaffElement inserted)`,
   `forReplacement(List<StaffElement> elements, int index, StaffElement replacement)`,
   `forDeletion(List<StaffElement> elements, List<StaffElement> deletedElements)`.
   Each builds the projected list, then the `StaffElement → Integer` index map in one
   pass over it. Use `IdentityHashMap` — `StaffElement` does not override `equals`, and
   two distinct elements of the same type must not collide. Build the map with the
   first-wins semantics the existing deletion code relies on (`computeIfAbsent`).

4. Create `src/main/java/songscribe/dom/SpanOutcome.java` exactly as given in the
   **Shared contract** section: a `public sealed interface SpanOutcome` permitting
   `Keep`, `Remove` and `Reshape(int begin, int end)`. Add `static SpanOutcome keep()`
   and `static SpanOutcome remove()` returning shared constant instances, so the two
   stateless cases are not allocated per span per edit. Document that `Reshape`'s indices
   are positions in the change's `projectedElements`, not in the pre-change line.

5. Run `./scripts/compile.sh` and confirm SUCCESS.

---

## ⏳ Phase 3: Span.outcomeFor and the three existing overriders

**Status:** Pending  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/dom/Span.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — the base default has to reproduce three separate legacy behaviours exactly, and the replacement-re-points rule is easy to get subtly wrong

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

`songscribe.dom.Span` currently exposes four boolean predicates, one per edit shape:

- `isInvalidatedBy(List<StaffElement> deletedElements)` (`Span.java:141`) — base rule,
  true when the anchor or end element is among the deleted
- `isInvalidatedByInsertion(int insertedIndex, ElementType insertedType, Line line)`
  (`Span.java:149`) — default false; overridden by `Tie` (`Tie.java:167`) and `Ending`
  (`Ending.java:376`)
- `isInvalidatedByDeletion(List<StaffElement> deletedElements, Line line)`
  (`Span.java:157`) — default false, "beyond what `isInvalidatedBy` already detects";
  overridden by `Ending` only (`Ending.java:236`)
- `isInvalidatedByReplacement(StaffElement oldElement, StaffElement newElement, Line line)`
  (`Span.java:165`) — default false; overridden by `Tuplet` (`Tuplet.java:312`), `Tie`
  (`Tie.java:203`), `Ending` (`Ending.java:359`) and `Hairpin`

A boolean can only ever say "remove me", which is why hairpins — whose answer to a
deletion is usually "reshape me to [a, b]" — were excluded from the sweep entirely
rather than overriding it. This phase adds the wider hook.

**Add, do not replace.** `Line` still calls the four old predicates and must keep
compiling and behaving identically; Phase 5 switches it over and Phase 6 deletes them.
Every `outcomeFor` written here delegates to the existing predicate bodies rather than
reimplementing them, so no behaviour can drift.

### Tasks

1. In `src/main/java/songscribe/dom/Span.java`, add the new hook with a default that
   reproduces exactly what `Line` does today:

   ```java
   public SpanOutcome outcomeFor(ElementChange change, Line line) {
       return switch (change) {
           case ElementChange.Insertion insertion ->
               isInvalidatedByInsertion(insertion.index(), insertion.inserted().getType(), line)
                   ? SpanOutcome.remove() : SpanOutcome.keep();
           case ElementChange.Replacement replacement ->
               isInvalidatedByReplacement(replacement.oldElement(), replacement.newElement(), line)
                   ? SpanOutcome.remove() : SpanOutcome.keep();
           case ElementChange.Deletion deletion ->
               isInvalidatedBy(deletion.deletedElements())
                       || isInvalidatedByDeletion(deletion.deletedElements(), line)
                   ? SpanOutcome.remove() : SpanOutcome.keep();
       };
   }
   ```

   Note the deletion arm ORs the two predicates, matching `Line.removeElement`
   (`Line.java:677-678`) and `Line.removeRange` (`Line.java:720-721`). Document that the
   default is a bridge over the per-shape predicates, that a subclass overriding
   `outcomeFor` need not implement any of them, and that `Reshape` is available to a
   subclass whose span survives an edit in a different shape.

2. Write the class-level Javadoc for the hook stating the contract every implementor is
   held to: the decision is made **before** the change lands, against
   `change.projectedElements()` and never against the line's current elements; and under
   `ElementChange.Replacement` a span whose endpoint is `oldElement` is to be read as
   having that endpoint become `newElement`, because `Line.setElement` re-points it —
   treating the old element as deleted would remove every span whose endpoint is edited.

3. Leave `Tie`, `Tuplet` and `Ending` **without** an `outcomeFor` override. Confirm by
   inspection that the Phase 3 task 1 default already routes each of them to the exact
   predicate it overrides today, and record that finding in the phase's final report. Do
   not add pass-through overrides that would only restate the default.

4. Verify `Span.requiresInvalidationConfirm()` (`Span.java:175`, overridden by `Ending`
   at `Ending.java:216`) is untouched and still reachable — `Line.hasEndingInvalidatedByInsertion`
   and `Line.hasEndingInvalidatedByDeletion` depend on it and are rewired in Phase 5.

5. Run `./scripts/compile.sh --test` (SUCCESS), then `./scripts/test.sh` and confirm the
   full unit suite is green. Nothing calls `outcomeFor` yet, so this is purely a check
   that adding it broke nothing.

---

## ⏳ Phase 4: Hairpin.outcomeFor

**Status:** Pending  <br>
**BlockedBy:** 1, 3  <br>
**Files:** src/main/java/songscribe/dom/Hairpin.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — three change shapes with different answers, and the deletion arm must reproduce `Line.survivingSpanOf`'s reshaping semantics precisely

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

Context this phase depends on, already landed:

- Phase 1 moved the hairpin rules onto `songscribe.dom.Hairpin` as
  `public static boolean canAnchorAt(List<? extends StaffElement> candidates, int index, int lastIndex)`,
  `public static boolean canEndAt(List<? extends StaffElement> candidates, int index)`,
  and the instance methods `int resolveBeginIndex(int first, int last, List<? extends StaffElement> candidates)`
  and `int resolveEndIndex(int first, int last, List<? extends StaffElement> candidates)`
  (each returning -1 when nothing in the range can hold that endpoint), plus
  `boolean survivesReplacement(List<? extends StaffElement> candidates, int index, StaffElement replacement)`.
  `Line.elementList()` returns the line's elements as an unmodifiable list.
- Phase 2 created `songscribe.dom.ElementChange` and `songscribe.dom.SpanOutcome` — see
  this plan's **Shared contract** section for their exact shape.
- Phase 3 added `Span.outcomeFor(ElementChange change, Line line)` with a default that
  bridges to the old per-shape predicates.

### Tasks

1. Override `outcomeFor(ElementChange change, Line line)` on `Hairpin`, switching on the
   change's three shapes. Resolve this hairpin's own anchor and end indices in the
   **pre-change** line first (`getAnchorElementIndex()` / `getEndElementIndex()`), and
   return `SpanOutcome.keep()` immediately when either is negative — a hairpin whose
   endpoints are not both in this line is not this change's to judge, matching the guard
   `Line.adjustHairpinsForDeletion` applies today.

2. **Insertion and replacement arms.** Both ask the same question: are this hairpin's two
   endpoints still endpoints the user could have placed, in the projected element list?
   Return `SpanOutcome.remove()` when not, `SpanOutcome.keep()` otherwise. Resolve each
   endpoint's projected position with `change.projectedIndexOf(...)`, applying the
   replacement re-pointing rule — under `ElementChange.Replacement`, an endpoint that is
   `oldElement` resolves to the projected index of `newElement`. Then test
   `Hairpin.canAnchorAt(projected, anchorIndex, endIndex)` and
   `Hairpin.canEndAt(projected, endIndex)`. Never `Reshape` here: an insertion or a
   replacement removes no element, so there is nothing to pull an endpoint in to.

3. **Deletion arm.** Reproduce what `Line.survivingSpanOf` (`Line.java:1327`) does today,
   reading it first and preserving its semantics exactly:
   - Walk this hairpin's pre-deletion index range, mapping each element through
     `change.projectedIndexOf(...)`; skip the ones that are absent (deleted). Record the
     first and last projected positions found. If none survive, return
     `SpanOutcome.remove()`.
   - Call `resolveBeginIndex(first, last, projected)` and
     `resolveEndIndex(first, last, projected)`.
   - Return `SpanOutcome.remove()` when either is negative or `begin >= end` — one
     element, or none, leaves no gesture to draw. Otherwise return
     `new SpanOutcome.Reshape(begin, end)`.
   - **Return `Reshape` even when the span is unchanged**, rather than `Keep`. Phase 5's
     merge pass needs every surviving hairpin's projected span, not only the moved ones,
     and `Line.applySpanRun` (`Line.java:1591`) already emits no mutation for a run of one
     whose endpoints both survive. State this in the method's Javadoc — it is the one
     place a hairpin deliberately departs from the `Keep`-means-untouched reading.

4. Rewrite `Hairpin.isInvalidatedByReplacement` (added in earlier uncommitted work on
   this branch) to delegate to the new method:
   `return outcomeFor(ElementChange.forReplacement(line.elementList(), line.getElementIndex(oldElement), newElement), line) instanceof SpanOutcome.Remove;`,
   keeping its existing guard that returns false when `getElementIndex(oldElement)` is
   negative. `Line` still calls the old predicate until Phase 5 switches it, so behaviour
   must stay identical across this phase — do not delete the override here; Phase 6 does
   that once nothing calls it.

5. Run `./scripts/compile.sh --test` (SUCCESS), then `./scripts/test.sh` and confirm the
   full unit suite is green — in particular `HairpinInvalidationTest`,
   `LineHairpinDeletionTest` and `LineHairpinMergeTest`. The deletion arm is not reached
   by production code yet (Phase 5 wires it), so a failure here means the replacement
   delegation in task 4 changed behaviour.

---

## ⏳ Phase 5: Line's unified sweep

**Status:** Pending  <br>
**BlockedBy:** 4  <br>
**Files:** src/main/java/songscribe/dom/Line.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — four call sites, an ordering constraint undo depends on, and the hairpin merge has to keep receiving exactly the input it gets today

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

Context this phase depends on, already landed: `Span.outcomeFor(ElementChange, Line)`
exists with a default bridging to the old predicates, `Hairpin` overrides it (returning
`Reshape` or `Remove` for deletions and `Keep` or `Remove` otherwise), and
`songscribe.dom.ElementChange` / `songscribe.dom.SpanOutcome` exist with the factories
`ElementChange.forInsertion/forReplacement/forDeletion` — see this plan's **Shared
contract** section.

`Line` today runs three different sweeps and excludes hairpins from one of them. This
phase makes it run one.

### Tasks

1. Add a single private helper that every primitive calls before applying its change:

   ```java
   private void applySpanOutcomes(ElementChange change)
   ```

   It computes `span.outcomeFor(change, this)` for every span in `spans` **first**,
   collecting the results, and only then acts on them — never iterating `spans` while
   removing from it. `Remove` goes through `removeInvalidatedSpan(span)`
   (`Line.java`, the typed-removal switch) so the removal emits its proper mutation.
   `Keep` does nothing. `Reshape` is collected per hairpin type for task 3.

   Return early when `spans.isEmpty()`, before any `ElementChange` is built. The
   projection is a full copy of the element list and most songs hold no spans at all;
   `adjustHairpinsForDeletion` has this same early-out today (`Line.java:1272`) and the
   reason must survive the refactor.

2. Wire the four call sites, each still guarded by `if (!song.isReplaying())` and each
   still running **before** the primary mutation is applied, so reverse-order undo
   restores the element before re-adding the spans:
   - `Line.addElement(int index, StaffElement element)` — replace the
     `isInvalidatedByInsertion` stream at `Line.java:454-457` with
     `applySpanOutcomes(ElementChange.forInsertion(elementList(), index, element))`.
     Leave the tuplet handling above it and the initial-tempo displacement below it
     exactly as they are.
   - `Line.setElement(int index, StaffElement element)` — replace the
     `isInvalidatedByReplacement` stream at `Line.java:521-524` with
     `applySpanOutcomes(ElementChange.forReplacement(elementList(), index, element))`.
     Leave the anchor/end re-pointing loop inside the mutator untouched: it is what makes
     a surviving span follow its replaced endpoint.
   - `Line.removeElement(int index)` — replace both the `adjustHairpinsForDeletion` call
     (`Line.java:671`) and the filtered invalidation stream (`Line.java:675-680`) with
     one `applySpanOutcomes(ElementChange.forDeletion(elementList(), List.of(deleted)))`.
   - `Line.removeRange(int from, int to)` — same substitution for `Line.java:714` and
     `Line.java:718-723`.

   Both `!(r instanceof Hairpin)` filters (`Line.java:676` and `Line.java:719`) go away
   with those streams — hairpins are now first-class participants.

3. Keep the hairpin merge, which is genuinely a `Line` concern because no span knows its
   siblings. `applySpanOutcomes` groups the `Reshape` results by hairpin class into the
   existing `HairpinSpan` record (`Line.java:1248`) and then calls the existing
   `mergeAdjacentSpans(List<HairpinSpan>, List<? extends StaffElement>)`
   (`Line.java:1563`) per type, passing `change.projectedElements()` where `survivors` is
   passed today. `mergeAdjacentSpans` and `applySpanRun` (`Line.java:1591`) keep their
   current bodies. Preserve the existing iteration order so the merge sees hairpins in
   the same order it does today.

4. Delete `Line.adjustHairpinsForDeletion` (`Line.java:1266`) and
   `Line.survivingSpanOf` (`Line.java:1327`) — Phase 4 moved the per-hairpin decision
   onto `Hairpin.outcomeFor`, and task 1 replaced the orchestration. Confirm with
   `jet_brains_find_referencing_symbols` that neither has another caller before deleting.

5. Rewire the two confirm-prompt helpers, which currently call the old predicates
   directly: `Line.hasEndingInvalidatedByInsertion` (`Line.java:1909-1910`) and
   `Line.hasEndingInvalidatedByDeletion` (`Line.java:1875-1877`). Each keeps its
   `.filter(Span::requiresInvalidationConfirm)` and changes its `anyMatch` to build the
   matching `ElementChange` and test
   `span.outcomeFor(change, this) instanceof SpanOutcome.Remove`. Build the change once
   outside the stream, not per span.

6. Run `./scripts/compile.sh --test` (SUCCESS), then `./scripts/test.sh` and confirm the
   full unit suite is green. `LineHairpinDeletionTest`, `LineHairpinMergeTest`,
   `HairpinInvalidationTest`, `TieInvalidationTest`, `TupletInvalidationTest`,
   `EndingInvalidationTest`, `SpanInvalidationTest` and `MutationReplayerRoundTripTest`
   are the ones that will catch a mistake here. A tie, tuplet or ending disappearing on
   an edit that used to leave it alone means the replacement re-pointing rule was applied
   as a deletion — re-read the **Shared contract** section.

---

## ⏳ Phase 6: Delete the superseded hooks

**Status:** Pending  <br>
**BlockedBy:** 5  <br>
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Tie.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Hairpin.java, src/test/java/songscribe/layout/SpanInvalidationTest.java, src/test/java/songscribe/dom/TieInvalidationTest.java, src/test/java/songscribe/dom/TupletInvalidationTest.java, src/test/java/songscribe/layout/EndingInvalidationTest.java  <br>
**Recommended model/effort:** Opus 4.8, medium effort — mechanical in shape, but it spans production and test files that must land together because Java test compilation is all-or-nothing

Read `.agents/rules/serena.md` for Java exploration, and
`.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` before touching
any test.

Context this phase depends on, already landed: `Line` no longer calls any of the four
per-shape predicates — it calls `Span.outcomeFor(ElementChange, Line)` exclusively (see
this plan's **Shared contract** section for the types). The predicates now have no
production caller outside the `Span.outcomeFor` default and the classes that override
them.

The four boolean hooks are the API this whole plan exists to retire. Leaving them public
invites a future span type to implement the narrow contract and quietly miss reshaping.

**Production and tests must land in this one phase**: the tests below call the
predicates directly, and Java test compilation is all-or-nothing, so deleting the methods
without updating the tests leaves the tree uncompilable.

### Tasks

1. In `src/main/java/songscribe/dom/Span.java`, reduce the four predicates —
   `isInvalidatedBy` (`Span.java:141`), `isInvalidatedByInsertion` (`:149`),
   `isInvalidatedByDeletion` (`:157`), `isInvalidatedByReplacement` (`:165`) — to
   `protected` visibility, keeping them as the implementation the `outcomeFor` default
   dispatches to. Do **not** delete them: they are how `Tie`, `Tuplet` and `Ending`
   express their rules, and rewriting those three into `outcomeFor` overrides would be a
   behaviour risk this plan has no reason to take. Update each Javadoc to say it is an
   implementation detail of the default `outcomeFor` and that new span types should
   override `outcomeFor` instead. Keep `requiresInvalidationConfirm` public.

2. Narrow the matching overrides to `protected` in `src/main/java/songscribe/dom/Tie.java`
   (`:167`, `:203`), `src/main/java/songscribe/dom/Tuplet.java` (`:312`) and
   `src/main/java/songscribe/dom/Ending.java` (`:236`, `:359`, `:376`). Change no logic
   in any of them.

3. In `src/main/java/songscribe/dom/Hairpin.java`, delete the
   `isInvalidatedByReplacement` override outright — unlike the other three, its body is
   now a pure delegation to `outcomeFor`, which is the method `Line` calls. Confirm with
   `jet_brains_find_referencing_symbols` that nothing else calls it first.

4. Update the four test files that call the predicates directly so they go through
   `outcomeFor` instead, asserting against `SpanOutcome` rather than a boolean. Keep
   every test's name, fixture and intent; only the call and the assertion change. A test
   asserting `isInvalidatedByX(...)` is true becomes an assertion that the outcome
   `isInstanceOf(SpanOutcome.Remove.class)`, and false becomes
   `isInstanceOf(SpanOutcome.Keep.class)`. Add a small helper per file that builds the
   right `ElementChange` from the fixture line rather than repeating the factory call in
   every test:
   - `src/test/java/songscribe/layout/SpanInvalidationTest.java` — five tests around
     `isInvalidatedBy` (anchor deleted, end deleted, both deleted, middle deleted,
     external deleted)
   - `src/test/java/songscribe/dom/TieInvalidationTest.java` — the insertion and
     replacement predicate tests; leave its `SetElementWiring` and cross-line nested
     classes alone, since those drive `Line` and are unaffected
   - `src/test/java/songscribe/dom/TupletInvalidationTest.java`
   - `src/test/java/songscribe/layout/EndingInvalidationTest.java`

5. Run `./scripts/compile.sh --test` (SUCCESS), then `./scripts/test.sh` and confirm the
   full unit suite is green.

---

## ⏳ Phase 7: Hairpin invalidation coverage

**Status:** Pending  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/dom/HairpinInvalidationTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — new cases in an established fixture, each with its setup and expected outcome spelled out

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

`src/test/java/songscribe/dom/HairpinInvalidationTest.java` exists on this branch and
covers replacement only. Its fixture is a four-element line — `CROTCHET`, `CROTCHET`,
`CROTCHET`, `CROTCHET_REST`, plus the auto-maintained `FINAL_DOUBLE_BARLINE` terminal —
with a crescendo from index 0 (`ANCHOR_INDEX`) to the rest at index 3 (`END_INDEX`), and
constants `INTERIOR_INDEX = 1` and `BEFORE_END_INDEX = 2`. It has a `SetElementWiring`
nested class that drives `Line.setElement` against a mocked `MessageCenter` and asserts
the recorded mutation order.

Phase 6 converted the direct-predicate tests in the *other* invalidation test files to
`SpanOutcome`; this file's existing tests call `crescendo.isInvalidatedByReplacement(...)`
through a private `isInvalidatedByReplacing(int, ElementType)` helper, and Phase 6 deleted
that override from `Hairpin`.

The bug this coverage pins, confirmed against pre-Phase-5 code: inserting a rest at the
index of a hairpin's end rest pushes that rest into second place in its run, leaving the
hairpin ending on the second of two rests, which `Hairpin.canEndAt` forbids. Nothing
objected, because `Hairpin` had no insertion hook at all.

### Tasks

1. Re-point the file's existing `isInvalidatedByReplacing(int index, ElementType type)`
   helper at the new API: build `ElementChange.forReplacement(line.elementList(), index, type.newInstance())`
   and return whether `crescendo.outcomeFor(change, line)` is a `SpanOutcome.Remove`.
   Every existing test keeps its name, its fixture and its `.as(...)` text. Add a
   companion helper `isInvalidatedByInserting(int index, ElementType type)` built the same
   way from `ElementChange.forInsertion(...)`.

2. Add a nested class `WhenAnElementIsInserted` (mirroring the file's existing
   `@Nested` + `@SuppressWarnings("PackageVisibleInnerClass")` shape) with one test per
   row:

   | Insert at | Type | Expected | Why |
   |---|---|---|---|
   | `END_INDEX` | `CROTCHET_REST` | Remove | the end rest is pushed into second place in its run |
   | `END_INDEX` | `CROTCHET` | Keep | a note before the end rest leaves it first in its run |
   | `INTERIOR_INDEX` | `CROTCHET_REST` | Keep | an interior rest is what a wedge crosses, not where it stops |
   | `ANCHOR_INDEX` | `CROTCHET_REST` | Keep | the inserted rest lands before the anchor, which still holds |

3. Add a `SpanOutcome.Reshape` case to the file: build a fixture where a deletion pulls
   the crescendo's end in, call `crescendo.outcomeFor(ElementChange.forDeletion(...), line)`
   directly, and assert the outcome is a `Reshape` carrying the expected projected
   `begin` and `end`. Assert on the record's components, not just its type — the indices
   are the part a future change can silently break, and they are positions in the
   projected list rather than in the pre-deletion line.

4. Extend the existing `SetElementWiring` nested class with an insertion counterpart,
   `AddElementWiring`, proving the outcome reaches the model and undo: insert a
   `CROTCHET_REST` at `END_INDEX` inside `song.withModification(...)`, then assert the
   crescendo is gone from `line.findSpans(Crescendo.class)`, that a `CrescendoRemoval`
   was recorded, and that it precedes the `ElementInsertion` in the captured mutation
   list — reverse-order undo re-adds the hairpin only once the line is back to its
   previous shape. Reuse the file's existing `mockMessageCenter` /
   `closeMessageCenterMock` / `captureSingleDidChange` helpers.

5. Run `./scripts/test.sh HairpinInvalidationTest` and confirm green, then
   `./scripts/test.sh` for the full unit suite and confirm green.

---

## ⏳ Phase 8: Docs

**Status:** Pending  <br>
**BlockedBy:** 5  <br>
**Files:** docs/span-invalidation.md, docs/hairpin-editing.md  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — one new doc plus one rewritten section, with the content specified

### Tasks

1. Create `docs/span-invalidation.md`, the design note for the framework this plan
   builds. `docs/` holds subsystem design notes explaining why a subsystem is built the
   way it is; there is no existing doc for span invalidation, and it now has a real
   contract worth writing down. Include:
   - The three element primitives and the single hook they all funnel through:
     `Line.addElement`, `Line.setElement`, `Line.removeElement` / `Line.removeRange` →
     `Line.applySpanOutcomes` → `Span.outcomeFor(ElementChange, Line)`.
   - The projection idea: each primitive builds the element list as it will be, and the
     span judges itself against that rather than against a description of the edit. Note
     that a future primitive needs only a new `ElementChange` case, not a new predicate on
     every span class.
   - The three outcomes — `Keep`, `Remove`, `Reshape(begin, end)` — and that `Reshape`'s
     indices are positions in the projected list. Note that `Hairpin` is the only type
     that returns `Reshape`, and that it returns one for every surviving hairpin on a
     deletion rather than `Keep`, because `Line`'s merge pass needs every surviving
     hairpin's projected span.
   - Why decisions are made **before** the change lands: undo replays a step's mutations
     in reverse, so a companion span mutation recorded after the primary one would carry
     a span pointing at the wrong element and undo would restore it broken.
   - That a replacement **re-points** a span's endpoint rather than deleting it, and that
     reading the old element as deleted would remove every span whose endpoint is edited.
   - That the sweep is skipped during `song.isReplaying()`, and that a reshape is
     expressed as a tracked removal plus a tracked addition of a copy because spans have
     no modification mutation.
   - What stays on `Line` and why: the cross-span hairpin merge
     (`Line.mergeAdjacentSpans` / `applySpanRun`), because no span knows its siblings;
     the generic `Line.mergeOverlappingSpans`, which serves beams as well as hairpins; and
     `Line.SPAN_ADJACENCY_REACH`, which that generic method reads.

2. In `docs/hairpin-editing.md`, replace the section headed
   "Replacing an endpoint removes the hairpin" wholesale. It documents the interim
   `Hairpin.isInvalidatedByReplacement` / `Line.hairpinSurvivesReplacement` pair, both of
   which are gone. The replacement should say that a hairpin is revalidated after **every**
   element change through `Hairpin.outcomeFor`, that an insertion or a replacement leaving
   an endpoint invalid removes it while a deletion reshapes it to the nearest valid
   endpoints, and that the rules themselves — `Hairpin.canAnchorAt` and
   `Hairpin.canEndAt` — are the same ones the menu reads, so the model can never hold a
   shape the menu would refuse to create. Point at `docs/span-invalidation.md` for the
   framework rather than restating it.

3. Elsewhere in `docs/hairpin-editing.md`, correct any remaining reference to
   `Line.canEndHairpin`, `Line.canAnchorHairpin` or `Line.resolveEndIndex` as the home of
   the rules — the rules are now `Hairpin.canEndAt`, `Hairpin.canAnchorAt` and
   `Hairpin.resolveEndIndex`, with thin delegating conveniences left on `Line`. Change no
   other content, and do not touch the "at most one rest" rule, which is unchanged.

4. Verify no other file under `docs/` names the removed members. Check
   `docs/line-layout.md` and `docs/clipboard.md` in particular, and fix any stale
   reference found; report if there are none.

---

## ⏳ Phase 9: Full verification and manual UI check

**Status:** Pending  <br>
**BlockedBy:** 6, 7, 8  <br>
**Files:** —  <br>
**Recommended model/effort:** Opus 4.8, medium effort — judging whether observed editor behaviour matches the intended invariant

### Tasks

1. Run `./scripts/compile.sh --test` — must report SUCCESS.

2. Run `./scripts/test.sh` — the full unit suite must be green. Pay particular attention
   to `SpanInvalidationTest`, `TieInvalidationTest`, `TupletInvalidationTest`,
   `EndingInvalidationTest`, `HairpinInvalidationTest`, `LineHairpinDeletionTest`,
   `LineHairpinMergeTest`, `HairpinActionStateTest`, `MusicXmlHairpinRoundTripTest`,
   `MutationReplayerRoundTripTest` and `PasteSpanReconciliationTest`. Report the pass
   count and compare it against the pre-change baseline of 7277 passed, 1 skipped —
   the count must have grown, never shrunk.

3. Confirm by inspection that no production file outside `songscribe.dom` calls any of
   the four narrowed predicates, using `jet_brains_find_referencing_symbols` on
   `Span/isInvalidatedBy`, `Span/isInvalidatedByInsertion`, `Span/isInvalidatedByDeletion`
   and `Span/isInvalidatedByReplacement`. Report anything found.

4. Ask the user for permission, then run `./scripts/run.sh` and walk these checks,
   reporting the result of each. They exercise the paths unit tests cannot reach — the
   real editor, real undo, real save/reload:
   - Draw a crescendo over four notes. Select its last note and replace it with a rest —
     the crescendo must shorten rather than vanish or end past the glyph.
   - Draw a hairpin ending on a rest. Replace that rest with a grace note — the hairpin
     must disappear, and one Undo must bring it back with its original endpoints.
   - Draw a hairpin ending on a rest, then insert a second rest just before that rest —
     the hairpin must disappear, and one Undo must bring it back. This is the bug the
     plan exists to close.
   - Replace a hairpin's anchor note with a rest — the hairpin must disappear, since a
     rest may never anchor one.
   - Delete an element in the middle of a hairpin — the hairpin must survive unchanged.
   - Paste a fragment over a hairpin's end element — the hairpin must shorten to the last
     valid surviving element rather than vanish.
   - Delete the elements separating two same-type hairpins — they must merge into one, as
     they do today. This is the cross-span merge the refactor deliberately left on `Line`.
   - Draw a tie, a tuplet and an ending, then edit an element at each one's endpoint —
     each must behave exactly as it did before this change. Any difference is a
     regression in the `outcomeFor` default.

5. `src/test/java/songscribe/e2e/HairpinSelectionTest.java` exercises hairpin
   hit-testing and is unaffected in principle, but an **e2e run requires explicit user
   approval**. Ask before running `./scripts/test.sh e2e HairpinSelectionTest`, and skip
   it if the user declines.
