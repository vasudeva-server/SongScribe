# Editor support for back-to-back hairpins and rest endpoints (Closes #743)

**Type:** Master plan  <br>
**Created:** 2026-08-08  <br>
**Status:** In Progress

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Hairpin.Kind enum](#-phase-1-hairpinkind-enum) | ✅ Complete | — |
| 2 | [getKind() as the single type test](#-phase-2-getkind-as-the-single-type-test) | ✅ Complete | — |
| 3 | [Line and Span predicates](#-phase-3-line-and-span-predicates) | ✅ Complete | — |
| 4 | [Type-aware resolution](#-phase-4-type-aware-resolution) | ✅ Complete | — |
| 5 | [Edit chain](#-phase-5-edit-chain) | ✅ Complete | — |
| 6 | [Test tree back to green](#-phase-6-test-tree-back-to-green) | ✅ Complete | — |
| 7 | [HairpinActionStateTest new coverage](#-phase-7-hairpinactionstatetest-new-coverage) | ✅ Complete | — |
| 8 | [Model predicate coverage](#-phase-8-model-predicate-coverage) | ✅ Complete | — |
| 9 | [Action, undo and MusicXML coverage](#-phase-9-action-undo-and-musicxml-coverage) | ✅ Complete | — |
| 10 | [Docs](#-phase-10-docs) | ✅ Complete | — |
| 11 | [Full verification and manual UI check](#-phase-11-full-verification-and-manual-ui-check) | 🔄 In Progress | — |

### Phase dependency graph

```
  1 ──► 2
  │
  ├──► 3 ──► 4 ──► 5 ──► 6 ──┬──► 7 ──┐
  │                          │        │
  │                          ├──► 8 ──┤
  │                          │        │
  │                          └──► 9 ──┤
  │                                   │
  └──────────────► 10 ────────────────┴──► 11
```

---

## ✅ Phase 1: Hairpin.Kind enum

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/io/musicxml/WedgeTypeMapping.java, src/main/java/songscribe/io/musicxml/WedgeResolver.java  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — a single enum move with two known reference sites; the refactoring tool does most of it

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

Goal: the two-value hairpin tag currently living in the MusicXML reader becomes a
public `songscribe.dom.Hairpin.Kind`, so the editor can name a hairpin type
before an instance exists. Nothing else in this phase changes behaviour.

### Tasks

1. Move `Kind` (`src/main/java/songscribe/io/musicxml/WedgeTypeMapping.java:47`,
   currently a package-private nested enum with constants `CRESCENDO`,
   `DIMINUENDO`) into `songscribe.dom.Hairpin`
   (`src/main/java/songscribe/dom/Hairpin.java`) as a public nested enum named
   `Kind`. Prefer serena's `jet_brains_move` so the two reference sites update
   automatically; if the tool refuses a nested-enum move, create the enum by hand,
   delete the original, and fix the references listed in task 3 manually — there
   are only two files.

2. Give `Hairpin.Kind` this exact shape (`Crescendo` and `Diminuendo` are
   `songscribe.dom.Crescendo` / `songscribe.dom.Diminuendo`, both `final class …
   extends Hairpin`, so no import is needed inside `Hairpin`):

   ```java
   /**
    * Which of the two hairpins a crescendo or diminuendo is — a plain two-value tag
    * rather than a {@code Class} token, since a hairpin is always constructed
    * directly ({@code new Crescendo}/{@code new Diminuendo}), never reflectively.
    * <p>
    * It names the type before there is an instance to ask, which is what both the
    * MusicXML reader (holding a wedge open while its end note is awaited) and the
    * hairpin menu (resolving what one item would do) need. Where an instance is in
    * hand, ask it: {@link Hairpin#getKind()}.
    */
   public enum Kind {
       CRESCENDO(Crescendo.class),
       DIMINUENDO(Diminuendo.class);

       private final Class<? extends Hairpin> spanType;

       Kind(Class<? extends Hairpin> spanType) {
           this.spanType = spanType;
       }

       /** The concrete subclass this kind names, for {@code SpanLookup} queries. */
       public Class<? extends Hairpin> spanType() {
           return spanType;
       }

       /** The other kind — the one this may share an endpoint with. */
       public Kind opposite() {
           return this == CRESCENDO ? DIMINUENDO : CRESCENDO;
       }
   }
   ```

   The old Javadoc's sentence about being "used by the reader as a binding tag" is
   deliberately dropped — the wording above already covers both callers.

3. Re-type the two reference sites the move leaves behind:
   - `WedgeTypeMapping.KIND_BY_TOKEN` (line ~57) becomes
     `Map<String, Hairpin.Kind>` with `Hairpin.Kind.CRESCENDO` /
     `Hairpin.Kind.DIMINUENDO` values, and `WedgeTypeMapping.wedgeKind(String)`
     (line ~94) returns `@Nullable Hairpin.Kind`. Update the `{@link WedgeKind}`
     tag in its Javadoc and the `// Reverse map: wedge type token → WedgeKind …`
     comment above the map.
   - `WedgeResolver` (`src/main/java/songscribe/io/musicxml/WedgeResolver.java`):
     drop the `import songscribe.io.musicxml.WedgeTypeMapping.WedgeKind;` (line 32),
     re-type the `pendingStartWedgeKind` (line ~64) and `pendingWedgeKind`
     (line ~74) fields and the `buildHairpin` `kind` parameter (line ~200) to
     `Hairpin.Kind`, and change the `kind == WedgeKind.CRESCENDO` test in
     `buildHairpin` (line ~203) to `Hairpin.Kind.CRESCENDO`.

4. Leave `WedgeTypeMapping.wedgeType(Hairpin)`'s `instanceof` chain alone for now —
   Phase 2 collapses it along with the three other type-test sites.

5. Run `./scripts/compile.sh` and confirm it reports SUCCESS.

---

## ✅ Phase 2: getKind() as the single type test

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Crescendo.java, src/main/java/songscribe/dom/Diminuendo.java, src/main/java/songscribe/io/musicxml/WedgeTypeMapping.java, src/main/java/songscribe/ui/renderer/HairpinRenderer.java, src/main/java/songscribe/undo/OpNames.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/test/java/songscribe/ui/renderer/HairpinRendererTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — one abstract method, two one-line overrides, four mechanical call-site collapses

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

Phase 1 gave the codebase a name for "which of the two hairpins." Four places
still answer that question their own way. `getKind()` makes it one answer, and
deletes an unreachable `null` branch on the way.

### Tasks

1. Add to `songscribe.dom.Hairpin`:

   ```java
   /**
    * Which of the two hairpins this is. The single answer to that question — use it
    * rather than {@code instanceof} or a {@code getClass()} comparison, so a caller
    * that only needs the type never has to know the class hierarchy.
    */
   public abstract Kind getKind();
   ```

   Implement it in `Crescendo` (`return Kind.CRESCENDO;`) and `Diminuendo`
   (`return Kind.DIMINUENDO;`), each with a one-line `{@inheritDoc}`-style comment
   or none at all.

2. `WedgeTypeMapping.wedgeType(Hairpin)` (line ~76) becomes a switch expression
   over `hairpin.getKind()` returning `MusicXmlTags.WEDGE_CRESCENDO` /
   `MusicXmlTags.WEDGE_DIMINUENDO`. The `@Nullable` annotation and the trailing
   `return null;` go away — the switch is exhaustive over a two-value enum, so the
   "unrecognised hairpin type" case no longer exists. Rewrite the Javadoc
   accordingly and drop the now-unused `Crescendo` / `Diminuendo` imports if
   nothing else in the file uses them.

3. `HairpinRenderer` (`src/main/java/songscribe/ui/renderer/HairpinRenderer.java`):
   - `computeHairpinLines`'s second parameter becomes `Hairpin.Kind kind` instead of
     `boolean isCrescendo`; update its `@param` line.
   - Its body passes `kind == Hairpin.Kind.CRESCENDO` to `HairpinShape.lines`.
     **`HairpinShape.lines` keeps its `boolean`** — `songscribe.shape` is pure
     geometry over doubles and must not take a dependency on `songscribe.dom`; the
     flag there means "tip on the left," not "is a crescendo." Add a one-line
     comment at the call site saying so.
   - `renderSingleHairpin` (line ~120) passes `hairpin.getKind()` in place of
     `hairpin instanceof Crescendo`.

4. `OpNames.deleteHairpinLabel` (`src/main/java/songscribe/undo/OpNames.java:191`):
   replace the `hairpin instanceof Crescendo` ternary with a test on
   `hairpin.getKind() == Hairpin.Kind.CRESCENDO`. The strings it selects are
   unchanged.

5. `PasteSpanReconciliation.fragmentContradictsHairpin`
   (`src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java:392-393`):
   replace `fragmentHairpin.getClass() != hairpin.getClass()` with
   `fragmentHairpin.getKind() != hairpin.getKind()`. Behaviour is identical
   (`Crescendo` and `Diminuendo` are both `final`), but the intent is now stated
   rather than inferred from class identity.

6. `src/test/java/songscribe/ui/renderer/HairpinRendererTest.java`: the eight
   `computeHairpinLines(layout, true|false, invariants)` call sites (lines ~79, 95,
   110, 126, 139, 140, 160, 161) take `Hairpin.Kind.CRESCENDO` /
   `Hairpin.Kind.DIMINUENDO`. Add the `songscribe.dom.Hairpin` import. No test
   names or assertions change — the geometry is untouched.

7. Run `./scripts/compile.sh --test` and confirm SUCCESS, then
   `./scripts/test.sh HairpinRendererTest` and confirm green.

---

## ✅ Phase 3: Line and Span predicates

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/Span.java  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — two small additions plus one one-line substitution, with the bodies given verbatim

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

Two model-level predicates the editor resolution and the deletion-reshaping path
both need. LilyPond ends a hairpin at a rest's left edge rather than past its
glyph (`lily/hairpin.cc:268-271`), so a rest bounds a wedge as legitimately as a
note does; and two opposite-type hairpins may share exactly the one element where
one ends and the next begins.

### Tasks

1. In `src/main/java/songscribe/dom/Line.java`, add `canEndHairpin` directly below
   the two `canAnchorHairpin` overloads (~line 1430), mirroring their shape — a
   public instance overload over the `elements` field and a private static overload
   over a candidate list, so the deletion path can call it against `survivors`:

   ```java
   public boolean canEndHairpin(int index) {
       return canEndHairpin(elements, index);
   }

   private static boolean canEndHairpin(List<? extends StaffElement> candidates, int index) {
       // LilyPond ends a hairpin at a rest's left edge rather than past its glyph
       // (hairpin.cc:268-271), so a rest bounds a wedge as legitimately as a note.
       return candidates.get(index).getType().isDuration();
   }
   ```

   Give both overloads Javadoc in the style of the neighbouring
   `canAnchorHairpin` pair: what may end a hairpin (a pitched note or a rest), and
   that both the menu's eligibility test and the post-deletion reshaping read it so
   the two can never disagree. `ElementType.isDuration()`
   (`src/main/java/songscribe/dom/ElementType.java:418`) is already exactly
   `isPitchedNote() || isRest()` — call it rather than restating the disjunction.

2. In the same file, change `resolveEndIndex` (~line 1449): replace the loop test
   `survivors.get(i).getType().isPitchedNote()` with `canEndHairpin(survivors, i)`.
   Update its Javadoc — a deleted end now moves in to the last surviving *note or
   rest*, not "the last surviving pitched note, which is the only thing a hairpin
   may end on".

3. Update `canAnchorHairpin`'s Javadoc cross-reference so the anchor/end pair reads
   as a matched set (each pointing at the other), and note the asymmetry
   explicitly: a rest may end a hairpin but may never anchor one. LilyPond's rest
   rule is right-side only.

4. In `src/main/java/songscribe/dom/Span.java`, add this predicate factory beside
   the existing `overlapping` / `containing` / `exactly` factories (~line 314):

   ```java
   /**
    * A predicate matching a span that overlaps {@code [begin, end]} by more than a
    * single shared endpoint: its end falls past {@code begin} and its anchor falls
    * before {@code end}.
    * <p>
    * Two hairpins may meet — one ends on the element the next begins on, and the
    * wedges back away from that shared column (LilyPond's back-to-back rule). More
    * than that one element in common is a genuine collision.
    * <p>
    * Off-edge and absent bounds read exactly as in {@link #overlapping}, and
    * {@link SpanBound#isAt} is false for all of them — so a half-detached span
    * matches, and the caller treats it as a collision. That is deliberate: a bound
    * with no position cannot be shown to share only an endpoint.
    */
   public static IndexPredicate overlappingBeyondEndpoint(int begin, int end) {
       // Built once here rather than inside the lambda: this predicate is tested
       // against every span on the line, on every selection change.
       var overlaps = overlapping(begin, end);

       return (anchorBound, endBound) ->
           overlaps.test(anchorBound, endBound)
               && !endBound.isAt(begin)
               && !anchorBound.isAt(end);
   }
   ```

   Derive it from `overlapping` rather than restating the off-edge/absent-bound
   conditions, so that handling stays in one place. `SpanBound.isAt(int)` already
   exists (`src/main/java/songscribe/dom/SpanBound.java:133`).

5. Run `./scripts/compile.sh` and confirm it reports SUCCESS.

---

## ✅ Phase 4: Type-aware resolution

**Status:** Complete  <br>
**BlockedBy:** 1, 3  <br>
**Files:** src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — the decision tree is rewritten with new ordering, a convergence loop and two escape hatches

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

**The problem this phase solves.** `MusicEditOperations.resolveHairpinAction()`
returns one state shared by both hairpin menu items, but back-to-back hairpins
need the two items to say different things about the same selection. With a
crescendo on `[0,4]` and the selection `[4,8]`, the Crescendo item must read
"Extend Crescendo" over span `[0,8]` (which is what `Line.addHairpin` would
absorb anyway), while the Diminuendo item must read "Add Diminuendo" over span
`[4,8]` — the back-to-back gesture. So the resolution becomes type-aware:
`resolveHairpinAction(Hairpin.Kind kind)`, resolved once per menu item. Because
the type is now an input, `EXTEND_CRESCENDO` and `EXTEND_DIMINUENDO` collapse
into a single `EXTEND`.

**The decision tree this phase implements:**

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
  !isExtend && countHairpinColumns(…) < MIN_HAIRPIN_COLUMNS? ─yes─> INELIGIBLE
        │ no
        ▼
  isExtend ? EXTEND : CAN_ADD   over [spanBegin, spanEnd]
```

**Rules this phase encodes.**
- Two hairpins of opposite type may share at most the one element where one ends
  and the next begins; a gap is equally fine. Only more than one element in common
  is a collision. (This also fixes an existing accident where a gap of exactly one
  element was blocked while a gap of two or more already worked, because the
  opposite-type check reused the same-type adjacency scan.)
- A trailing rest counts toward the two-column minimum, so one pitched note plus a
  terminal rest can carry a new hairpin — the `18000/17323.abc` corpus shape.
- A rest still cannot *anchor* a hairpin. `Line.canAnchorHairpin` is unchanged.
- `INELIGIBLE` means **this span cannot carry a hairpin, whatever else is on the
  line**. `BLOCKED` means **another hairpin is the obstacle**. Every return in the
  body follows that rule, including the boundary check's ternary.

**Do not touch:** `Line.addHairpin`'s absorption of adjacent same-type spans (two
touching crescendos are one gesture, and that is correct).

After this phase `./scripts/compile.sh` must be SUCCESS. Phase 5's callers have
not been updated yet, so it will fail until they are — land Phase 5 immediately
after, and do not run `./scripts/test.sh` for either.

### Tasks

1. Collapse the state enum and rename the count constant:
   - `HairpinActionState`: replace `EXTEND_CRESCENDO` and `EXTEND_DIMINUENDO` with
     a single `EXTEND`. Rewrite the enum's class Javadoc to state the rule above —
     `INELIGIBLE` = the span itself cannot carry a hairpin; `BLOCKED` = another
     hairpin is what stops it — then give each constant its per-item meaning
     (`INELIGIBLE` = this item reads "Add …", disabled; `CAN_ADD` = "Add …",
     enabled; `EXTEND` = "Extend …", enabled; `BLOCKED` = "Add …", disabled). The
     resolution is per menu item now, not a joint verdict about both.
   - Rename `MIN_HAIRPIN_NOTES` → `MIN_HAIRPIN_COLUMNS` (value stays 2) and reword
     its comment: a new hairpin needs two columns to slope across, and a terminal
     rest is one of them.

2. Add `countHairpinColumns` next to the existing `countPitchedNotes` (which stays
   exactly as it is, as the inner helper):

   ```java
   /**
    * Counts what a new hairpin has to slope across in {@code [begin, end]}: every
    * pitched note, plus a rest at the end, which bounds a wedge in its own right.
    * Grace notes and interior rests do not count.
    */
   private static int countHairpinColumns(Line line, int begin, int end) {
       var count = countPitchedNotes(line, begin, end);

       if (line.getElement(end).getType().isRest()) {
           count++;
       }

       return count;
   }
   ```

   Then delete three now-dead members: `findHairpinsNearSelection`, the
   `HairpinScan` record, and `isHairpinEligibleSpan` — a type-aware resolution
   needs one typed lookup rather than both lists, and inlines the three eligibility
   checks so they can be applied selectively. Confirm with
   `jet_brains_find_referencing_symbols` that `isHairpinEligibleSpan` has no
   caller outside this file before deleting it (`docs/line-layout.md` names it in
   prose; Phase 10 rewrites that line, so ignore it here).

3. Rewrite `resolveHairpinAction` as `public HairpinResolution
   resolveHairpinAction(Hairpin.Kind kind)` with this body verbatim.
   `line.findSpans` and `line.hasSpan` come from `songscribe.dom.SpanLookup`;
   `Line.SPAN_ADJACENCY_REACH` is the public constant `Line` already exposes.

   ```java
   var range = coordinator.getRange();

   // A slide, ending or hairpin selection is a target rather than a range, and
   // Line.getElement does not bounds check, so this guard has to come before
   // anything that touches an element index.
   if (range == null) {
       return ineligibleHairpinResolution();
   }

   var line = range.line();
   var begin = range.begin();
   var end = range.end();

   var spanBegin = begin;
   var spanEnd = end;
   var isExtend = false;
   var grew = false;

   // Line.addHairpin absorbs same-type spans within SPAN_ADJACENCY_REACH of the
   // span it is handed, so absorbing one can bring a further one into reach.
   // Widen until the union stops growing, or the menu label would promise a
   // narrower hairpin than the model is about to build, and the boundary and
   // opposite-type checks below would run against a span that never exists.
   // Each pass either strictly widens the union or ends the loop, so this
   // terminates in at most one pass per same-type hairpin on the line.
   do {
       var sameType = line.findSpans(
           kind.spanType(),
           Span.overlapping(
               spanBegin - Line.SPAN_ADJACENCY_REACH,
               spanEnd + Line.SPAN_ADJACENCY_REACH));
       isExtend = !sameType.isEmpty();

       var widenedBegin = spanBegin;
       var widenedEnd = spanEnd;

       for (var hairpin : sameType) {
           var anchorIndex = hairpin.getAnchorElementIndex();
           var endIndex = hairpin.getEndElementIndex();

           // A hairpin already covering the whole selection has nothing to extend.
           // Tested against the original selection, never the widened union: a
           // hairpin absorbed on a later pass covers the union by construction,
           // and would block every extension that reached it.
           if (anchorIndex <= begin && endIndex >= end) {
               return blockedHairpinResolution();
           }

           widenedBegin = Math.min(widenedBegin, anchorIndex);
           widenedEnd = Math.max(widenedEnd, endIndex);
       }

       grew = widenedBegin != spanBegin || widenedEnd != spanEnd;
       spanBegin = widenedBegin;
       spanEnd = widenedEnd;
   } while (grew);

   // Only an endpoint the selection supplies has to be one the user could place.
   // An endpoint inherited from the hairpin being extended stays put — including a
   // rest anchor an older build left behind, which is not this action's to correct.
   var anchorComesFromSelection = spanBegin == begin;
   var endComesFromSelection = spanEnd == end;

   // spanEnd rather than end: canAnchorHairpin's lastIndex bounds the grace-note
   // host lookahead, and the hairpin really will reach spanEnd, so the host may
   // legitimately sit anywhere up to it.
   if (anchorComesFromSelection && !line.canAnchorHairpin(begin, spanEnd)) {
       return ineligibleHairpinResolution();
   }

   if (endComesFromSelection && !line.canEndHairpin(end)) {
       return ineligibleHairpinResolution();
   }

   // The union reaches past the selection only because a hairpin widened it, so
   // when extending, that hairpin is the obstacle. With none in play the selection
   // itself crosses the boundary.
   if (line.spansStructuralBoundary(spanBegin, spanEnd)) {
       return isExtend ? blockedHairpinResolution() : ineligibleHairpinResolution();
   }

   // Two hairpins may meet on one shared element; more than that is a collision.
   if (line.hasSpan(
           kind.opposite().spanType(),
           Span.overlappingBeyondEndpoint(spanBegin, spanEnd))) {
       return blockedHairpinResolution();
   }

   if (!isExtend && countHairpinColumns(line, spanBegin, spanEnd) < MIN_HAIRPIN_COLUMNS) {
       return ineligibleHairpinResolution();
   }

   return new HairpinResolution(
       isExtend ? HairpinActionState.EXTEND : HairpinActionState.CAN_ADD, spanBegin, spanEnd);
   ```

   Ordering constraints that must not be disturbed: the `range == null` guard stays
   first; the union must converge before the endpoint, boundary, opposite-type and
   count checks, because all four run against the *resolved* span, not the raw
   selection. The scan reach is `Line.SPAN_ADJACENCY_REACH` on each side because
   that is the neighbourhood `Line.mergeOverlappingSpans` absorbs — the label has to
   stay honest about what the model will do.

4. Rewrite `resolveHairpinAction`'s Javadoc wholesale. The current text documents
   a type-agnostic decision tree, two extend states, and an
   "opposite-type ⇒ BLOCKED" rule — all three are now wrong. Include the
   `INELIGIBLE`/`BLOCKED` rule, and add a paragraph stating that the resolution is
   deliberately recomputed on every call and **must not be cached**: two menu items
   resolving independently costs a handful of O(spans) scans per selection change,
   which is free, while a cache keyed on selection would go stale the moment a
   hairpin is added or undone without the selection moving, leaving both items
   lying about what they will do. Keep the worked-examples section and replace the
   examples with these:
   - Crescendo on `[0,4]`, selection `[4,8]`: `CRESCENDO` → `EXTEND` span `[0,8]`;
     `DIMINUENDO` → `CAN_ADD` span `[4,8]`. The two items legitimately disagree.
   - Crescendo on `[0,4]`, selection `[2,8]`: `DIMINUENDO` → `BLOCKED` — the overlap
     is more than the one shared endpoint.
   - One note plus the rest after it, no hairpin nearby: `CAN_ADD` — the trailing
     rest is the second column.
   - A lone rest: `INELIGIBLE` — a rest cannot anchor a hairpin.

5. Run `./scripts/compile.sh`. It will report FAILURE on `ScoreViewController`,
   `AddHairpinCommand` and `HairpinAction` — that is expected, and Phase 5 fixes
   it. Confirm the only errors are in those three files plus
   `addHairpinToSelection` in this one; anything else is a mistake in this phase.

---

## ✅ Phase 5: Edit chain

**Status:** Complete  <br>
**BlockedBy:** 4  <br>
**Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/message/command/AddHairpinCommand.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/action/HairpinAction.java  <br>
**Recommended model/effort:** Opus 4.8, medium effort — mechanical retyping plus one flag removal whose reasoning matters

Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring.

The kind travels from the menu item to the model. **Do not touch** the
`boolean isCrescendo` parameter on `HairpinShape.lines` — Phase 2 already decided
that boundary.

After this phase `./scripts/compile.sh` must be SUCCESS, but the **test tree will
not compile** until Phase 6 — that is expected, so do not run `./scripts/test.sh`
here.

### Tasks

1. Rewrite `MusicEditOperations.addHairpinToSelection` as
   `public void addHairpinToSelection(Hairpin.Kind kind)`:
   - Call `resolveHairpinAction(kind)`.
   - The guard collapses to
     `resolvedState != HairpinActionState.CAN_ADD && resolvedState != HairpinActionState.EXTEND`;
     the local `extendState` goes away.
   - `opNameKey` still picks add vs extend from `resolvedState ==
     HairpinActionState.CAN_ADD`, and crescendo vs diminuendo from
     `kind == Hairpin.Kind.CRESCENDO`.
   - The construction branch becomes a switch expression, so `added` needs no
     definite-assignment dance:

     ```java
     var added = switch (kind) {
         case CRESCENDO -> {
             var hairpin = new Crescendo(anchorElement, endElement);
             line.addCrescendo(hairpin);
             yield (Hairpin) hairpin;
         }
         case DIMINUENDO -> {
             var hairpin = new Diminuendo(anchorElement, endElement);
             line.addDiminuendo(hairpin);
             yield (Hairpin) hairpin;
         }
     };
     ```

     It must keep calling `addCrescendo`/`addDiminuendo`: `Line.addSpan` is the raw
     adder and skips the merge, which the extend path depends on.
   - Everything downstream is untouched — `stripPointDynamics` over the
     *post-merge* indices, `line.withModification`, the mutation factories. Update
     the `{@link #resolveHairpinAction()}` tag in the method Javadoc to
     `{@link #resolveHairpinAction(Hairpin.Kind)}`.

2. In `src/main/java/songscribe/message/command/AddHairpinCommand.java`, replace
   the `boolean isCrescendo` field, constructor parameter and `isCrescendo()`
   getter with a `Hairpin.Kind kind` field, constructor parameter and `kind()`
   accessor. It stays a class rather than becoming a record — it extends `Message`,
   and records cannot extend a class.

3. In `src/main/java/songscribe/ui/component/ScoreViewController.java`, forward the
   kind at both sites: `handleAddHairpin` (line ~352) becomes
   `operations.addHairpinToSelection(message.kind())`, and
   `ScoreViewController.resolveHairpinAction()` (line ~402) takes a
   `Hairpin.Kind kind` parameter and forwards it.

4. In `src/main/java/songscribe/ui/action/HairpinAction.java`:
   - Replace the `boolean isCrescendo` field with `Hairpin.Kind kind`. The private
     constructor and the `createCrescendoAction` / `createDiminuendoAction`
     factories take the kind (`Hairpin.Kind.CRESCENDO` / `Hairpin.Kind.DIMINUENDO`).
     The three ternaries in the `super(...)` call and the two in `applyLabel` switch
     on `kind == Hairpin.Kind.CRESCENDO`.
   - Rename the public `isCrescendo()` accessor to `getKind()` returning
     `Hairpin.Kind`. Run `jet_brains_find_referencing_symbols` on it first — it is
     public API on the action; the only production caller is `performAction` in the
     same file, but confirm rather than assume.
   - Delete `extendState()` — the type is now an argument, not something to match on.
   - `updateEnabledState()` calls `ctrl.resolveHairpinAction(kind).state()`.
   - `applyHairpinState` becomes:

     ```java
     private void applyHairpinState(MusicEditOperations.HairpinActionState state) {
         var isExtend = state == MusicEditOperations.HairpinActionState.EXTEND;
         applyLabel(isExtend);
         setEnabled(isExtend || state == MusicEditOperations.HairpinActionState.CAN_ADD);
     }
     ```

   - `performAction` posts `new AddHairpinCommand(kind)`.
   - No new or changed user-facing strings: the four `Strings.ACTION_HAIRPIN_*` and
     `ACTION_HAIRPIN_*_EXTEND` keys keep their current meaning.

5. **Remove `Flag.DISABLE_IN_REST_MODE` from `HairpinAction`'s constructor flag
   list** (line ~52), and add a comment above the remaining flags explaining both
   consequences. `UIAction.enableInRestMode()`
   (`src/main/java/songscribe/ui/action/UIAction.java:563-570`) is a conjunction of
   two unrelated conditions:

   ```java
   return !Actions.REST_ACTION.isSelected()                                    // rest tool armed
       && !requireScoreView().getSelectionCoordinator().selectionHasRests();   // selection has a rest
   ```

   The second half greys the item out for exactly the `note, rest` selection this
   feature exists to support, and `super.updateEnabledState()` runs before the
   resolution, so no amount of resolution logic can recover it. Removing the flag
   also drops the first half: **the hairpin items are now available while the rest
   tool is armed.** That is intended — input mode governs what the next *inserted*
   element is, and a hairpin drawn over an existing selection has nothing to do
   with insertion. `resolveHairpinAction` is now the sole authority on whether a
   selection containing rests can carry a hairpin: interior rests are fine, a
   trailing rest is an endpoint, and a leading rest is `INELIGIBLE`. Leave every
   other flag (`REQUIRES_SELECTION`, `DISABLE_WHEN_BAR_SELECTED`,
   `DISABLE_WHEN_PLAYING`, `DISABLE_WHEN_EDITING_TEXT`, `DISABLE_IN_GRACE_MODE`)
   exactly as it is, and do not change `UIAction` or the other two actions that
   carry the flag.

6. Run `./scripts/compile.sh` and confirm it reports SUCCESS. Do not run
   `./scripts/test.sh` — the test tree is knowingly broken until Phase 6.

---

## ✅ Phase 6: Test tree back to green

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Files:** src/test/java/songscribe/ui/HairpinActionStateTest.java, src/test/java/songscribe/ui/action/HairpinActionTest.java, src/test/java/songscribe/ui/MusicEditOperationsNullStateTest.java, src/test/java/songscribe/dom/MusicEditOperationsMutationTest.java, src/test/java/songscribe/undo/MutationLabelTest.java, src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerCommandHandlerTest.java  <br>
**Recommended model/effort:** Opus 4.8, medium effort — mostly mechanical retyping, but four existing expectations genuinely change meaning and must be reasoned about, not just recompiled

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first,
and `.agents/rules/serena.md` for Java exploration. Java test compilation is
all-or-nothing, so every affected test file must land in this one phase; **no new
coverage here** — Phases 7, 8 and 9 add that.

Context this phase depends on (Phases 4 and 5 already landed it):
`MusicEditOperations.resolveHairpinAction(Hairpin.Kind)` and
`MusicEditOperations.addHairpinToSelection(Hairpin.Kind)` now take a
`songscribe.dom.Hairpin.Kind` (`CRESCENDO` / `DIMINUENDO`);
`HairpinActionState.EXTEND_CRESCENDO` and `EXTEND_DIMINUENDO` have collapsed into
one `EXTEND`; `AddHairpinCommand` takes and exposes `kind()` instead of
`isCrescendo()`; `HairpinAction.isCrescendo()` is now `getKind()`;
`HairpinAction` no longer carries `Flag.DISABLE_IN_REST_MODE`;
`ScoreViewController.resolveHairpinAction(Hairpin.Kind)` takes the kind too.

Reuse the existing fixtures rather than inventing new ones; index and count
literals go through the established `IDX_*` / `NOTE_COUNT_*` constants.

### Tasks

1. `src/test/java/songscribe/ui/HairpinActionStateTest.java` — mechanical pass.
   Change `Fixture.resolve()` to `HairpinResolution resolve(Hairpin.Kind kind)`
   calling `ops.resolveHairpinAction(kind)`, and pass the kind at every call site
   (do **not** add a no-arg default overload — the whole point is that the kind is
   an explicit input). Add beside it the assertion helper Phase 7 will lean on:

   ```java
   /** Asserts the resolution for {@code kind} is {@code state} over [spanBegin, spanEnd]. */
   void assertResolves(
       Hairpin.Kind kind,
       HairpinActionState state,
       int spanBegin,
       int spanEnd,
       String because
   ) {
       var resolution = resolve(kind);
       assertThat(resolution.state()).as(because).isEqualTo(state);
       assertThat(resolution.spanBegin()).as(because).isEqualTo(spanBegin);
       assertThat(resolution.spanEnd()).as(because).isEqualTo(spanEnd);
   }
   ```

   `Extension` tests that add a crescendo resolve with `Hairpin.Kind.CRESCENDO`;
   the three `testDiminuendoExtends*` tests resolve with `Hairpin.Kind.DIMINUENDO`.
   `InputGuard.testNoActiveSelectionStateIsIneligible` calls
   `ops.resolveHairpinAction(Hairpin.Kind.CRESCENDO)`. Replace every
   `EXTEND_CRESCENDO` / `EXTEND_DIMINUENDO` expectation with
   `HairpinActionState.EXTEND`. Update the class Javadoc's
   `{@link MusicEditOperations#resolveHairpinAction()}` to
   `{@link MusicEditOperations#resolveHairpinAction(Hairpin.Kind)}` and reword the
   sentence so it says the resolution is per menu item.

2. `HairpinActionStateTest` — the three expectations that genuinely change:
   - `StructuralEligibility.testRestAtEndIsIneligible` (line ~197; fixture
     `crotchet(), crotchet(), crotchetRest()`, selection `[IDX_0, IDX_2]`): rename
     to `testRestAtEndCanAdd`, expect `CAN_ADD` with span `[IDX_0, IDX_2]`, and
     replace the `.as(...)` text with a note that a rest bounds a wedge at its left
     edge, so it may end a hairpin.
   - `InputGuard.testIneligibleResolutionCarriesNoSpan` (line ~162) currently gets
     its `INELIGIBLE` from `fixtureWith(crotchet(), crotchetRest())` over
     `[IDX_0, IDX_1]`, which is now `CAN_ADD`. Swap the fixture for
     `fixtureWithNotes(NOTE_COUNT_3)` with `select(IDX_1, IDX_1)` — a lone note with
     no hairpin nearby still fails the two-column gate — and keep both `-1` span
     assertions unchanged.
   - `Blocking.testOppositeTypeNeighbourBlocksExtension` (line ~471; crescendo
     `[IDX_0, IDX_2]`, diminuendo `[IDX_6, IDX_8]`, selection `[IDX_3, IDX_5]`):
     resolving `CRESCENDO` now yields the union `[IDX_0, IDX_5]`, which the
     diminuendo at `[IDX_6, IDX_8]` does not overlap at all. Rename to something
     like `testOppositeTypeClearOfTheUnionDoesNotBlock`, expect `EXTEND` with span
     `[IDX_0, IDX_5]`, and say why in `.as(...)`. Then add a companion in the same
     nested class that still blocks: same crescendo `[IDX_0, IDX_2]`, diminuendo at
     `[IDX_4, IDX_8]`, selection `[IDX_3, IDX_5]`, resolve `CRESCENDO` → the union
     `[IDX_0, IDX_5]` overlaps that diminuendo by more than one endpoint → `BLOCKED`.

3. `HairpinActionStateTest` — verify by running, and fix only where the new rules
   say so, that these still hold as written (they should):
   `testBothTypesOverlappingSelectionBlocks`, `testSelectionInsideOneHairpinBlocks`,
   `testSelectionExactlyMatchingHairpinBlocks`, `testBlockedResolutionCarriesNoSpan`,
   `testUnionSpanCrossingBoundaryBlocks`, `testStructuralBoundaryInSelectionIsIneligible`,
   `testRestAtBeginIsIneligible`, `testGraceNoteAtEndIsIneligible`,
   `testRestBetweenPitchedNotesCanAdd`, and both `NoCandidates` tests.

4. `src/test/java/songscribe/ui/action/HairpinActionTest.java`:
   - `stubResolution(state)` becomes `stubResolution(Hairpin.Kind kind,
     HairpinActionState state)`, stubbing
     `when(mockEnv().ctrl().resolveHairpinAction(kind))`. Its `hasSpan` test drops
     to `state == CAN_ADD || state == EXTEND`. Keep `STUB_SPAN_END` as is.
   - `assertBothActions(...)` currently calls `stubResolution(state)` once for both
     items; give it a per-kind form — stub `CRESCENDO` and `DIMINUENDO` separately
     so callers can hand the two items different states. The four callers that want
     the same state for both pass it twice.
   - `testCreateCrescendoActionSetsCrescendoTrue` /
     `testCreateDiminuendoActionSetsCrescendoFalse`: rename to
     `testCreateCrescendoActionSetsCrescendoKind` /
     `testCreateDiminuendoActionSetsDiminuendoKind` and assert
     `action.getKind()` equals `Hairpin.Kind.CRESCENDO` / `Hairpin.Kind.DIMINUENDO`.
   - `ActionPerformed`'s two tests assert `captor.getValue().kind()` equals the
     expected `Hairpin.Kind`; keep the mutant-catching comment on the diminuendo one,
     reworded for the kind.
   - `LabelAndEnabledPerState.testExtendCrescendoRelabelsOnlyTheCrescendoAction` and
     `testExtendDiminuendoRelabelsOnlyTheDiminuendoAction`: with a per-item
     resolution these become "the crescendo item resolves `EXTEND` while the
     diminuendo item resolves `BLOCKED`" (and the mirror). Restate them that way —
     crescendo reads "Extend Crescendo" and is enabled, diminuendo reads
     "Add Diminuendo" and is disabled. Every `stubResolution` call in
     `EnabledState` and `MusicSelectionDidChange` needs a kind argument; those
     tests only exercise the crescendo action, so `Hairpin.Kind.CRESCENDO` suffices
     — but stub both kinds wherever `assertBothActions` is used.
   - `EnabledState.testDisabledInRestMode` (line ~211) asserts that a selection
     containing rests disables the action. Phase 5 removed
     `Flag.DISABLE_IN_REST_MODE` from `HairpinAction` precisely so a hairpin can end
     on a rest, so this test now states the opposite of the intended behaviour.
     Replace it with **two** tests, each carrying a comment that the resolution, not
     a flag, decides rest eligibility now:
     `testSelectionContainingRestsStaysEnabledWhenResolutionCanAdd`, and
     `testStaysEnabledWhileRestToolIsArmed` (arm `Actions.REST_ACTION`, stub
     `CAN_ADD`, assert enabled) — the second half of the flag's behaviour that the
     removal also drops.

5. Retype the remaining `boolean` call sites to `Hairpin.Kind`, adding the
   `songscribe.dom.Hairpin` import where needed. `true` → `Hairpin.Kind.CRESCENDO`,
   `false` → `Hairpin.Kind.DIMINUENDO`. Where a test has a local `crescendo`
   boolean or parameter feeding these calls, re-type that local/parameter to
   `Hairpin.Kind` rather than converting at the call site.
   - `src/test/java/songscribe/ui/MusicEditOperationsNullStateTest.java` line ~263
     (`addHairpinToSelection(true)`).
   - `src/test/java/songscribe/undo/MutationLabelTest.java` line ~315.
   - `src/test/java/songscribe/dom/MusicEditOperationsMutationTest.java` lines
     ~505, ~577, ~591, ~627, ~654 (line ~505 is inside a method taking a
     `crescendo` boolean — re-type that parameter).
   - `src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java` lines ~734,
     ~747 (both inside the `HairpinExecution` nested class).
   - `src/test/java/songscribe/ui/component/ScoreViewControllerCommandHandlerTest.java`
     line ~296 (`new AddHairpinCommand(crescendo)` — re-type the `crescendo`
     parameter it comes from).

6. Run `./scripts/compile.sh` (SUCCESS), then `./scripts/test.sh` and confirm the
   full unit suite is green before declaring the phase done. If
   `LineHairpinDeletionTest` or `LineHairpinMergeTest` fail, that is real fallout
   from `Line.resolveEndIndex` now accepting a rest as an end — fix the expectation
   to match the new rule rather than reverting the model change, and report which
   tests you touched.

---

## ✅ Phase 7: HairpinActionStateTest new coverage

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/ui/HairpinActionStateTest.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — sixteen new cases whose expected spans depend on reasoning through the resolution's converge-then-check ordering

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

`MusicEditOperations.resolveHairpinAction(Hairpin.Kind)` resolves per menu item:
it unions the selection with any same-type hairpin within
`Line.SPAN_ADJACENCY_REACH`, repeating until the union stops growing, then checks
only the endpoints the selection itself supplies, the structural boundary over the
union, an opposite-type hairpin overlapping the union by more than one shared
endpoint (`Span.overlappingBeyondEndpoint`), and — for adds only — a two-column
minimum in which a trailing rest counts as a column.

`HairpinActionStateTest` already provides everything needed: `fixtureWithNotes(int)`,
`fixtureWith(StaffElement...)`, the `crotchet()` / `crotchetRest()` /
`graceQuaver()` element builders, `Fixture.addCrescendo(anchor, end)` /
`addDiminuendo(anchor, end)` (raw `addSpan`, no merge), `Fixture.select(begin, end)`,
`Fixture.resolve(Hairpin.Kind)` and the `Fixture.assertResolves(...)` helper Phase 6
added. Write each row as its own named `@Test` calling `assertResolves` — the names
and the `because` text are what a failure report shows. Add `IDX_*` and
`NOTE_COUNT_*` constants where a needed value is missing (e.g. `IDX_7`, `IDX_9`,
`IDX_10`, `NOTE_COUNT_10`, `NOTE_COUNT_12`) rather than writing a bare literal.

### Tasks

1. Add a nested class `BackToBack` (mirroring the existing `@Nested` +
   `@SuppressWarnings("PackageVisibleInnerClass")` shape) with one test per row.
   All rows use an all-crotchet line long enough for the indices involved:

   | Setup | Resolve | Expected |
   |---|---|---|
   | crescendo `[0,4]`, select `[4,8]` | `DIMINUENDO` | `CAN_ADD`, span `[4,8]` |
   | crescendo `[0,4]`, select `[4,8]` | `CRESCENDO` | `EXTEND`, span `[0,8]` |
   | diminuendo `[4,8]`, select `[0,4]` | `CRESCENDO` | `CAN_ADD`, span `[0,4]` |
   | crescendo `[0,4]`, select `[2,8]` | `DIMINUENDO` | `BLOCKED` (overlap beyond one endpoint) |
   | crescendo `[0,4]`, select `[6,9]` | `DIMINUENDO` | `CAN_ADD` (one-element gap at 5) |
   | diminuendo `[0,3]`, crescendo `[6,9]`, select `[3,6]` | `DIMINUENDO` | `EXTEND`, span `[0,6]` |
   | diminuendo `[0,3]`, crescendo `[5,9]`, select `[3,6]` | `DIMINUENDO` | `BLOCKED` |

   The first two rows are the point of the whole change and belong together in the
   file with a comment saying so: one selection, two menu items, two different
   honest answers. The one-element-gap row also pins a fix — before this change a
   gap of exactly one element was blocked while a gap of two or more already worked.

2. Add a nested class `UnionConvergence` covering the loop added in Phase 4. Both
   rows need a line of at least 20 elements:

   | Setup | Resolve | Expected |
   |---|---|---|
   | crescendo `[0,4]`, crescendo `[10,14]`, crescendo `[15,18]`, select `[5,9]` | `CRESCENDO` | `EXTEND`, span `[0,18]` |
   | same three crescendos, plus diminuendo `[16,19]`, select `[5,9]` | `CRESCENDO` | `BLOCKED` |

   Row 1 is the convergence itself: one pass reaches `[0,14]`, and the crescendo at
   `[15,18]` is one element past it, so a single-pass union would stop short and the
   menu would promise a narrower hairpin than `Line.addHairpin` builds. Row 2 is why
   it matters — the diminuendo only collides with the *converged* union, so a
   single-pass resolution would let the user create an overlapping pair. Say both of
   those in the `because` text.

3. Add a nested class `RestEndpoints`, same shape:

   | Setup | Resolve | Expected |
   |---|---|---|
   | `crotchet(), crotchet(), crotchetRest()`; select `[0,2]` | either kind | `CAN_ADD`, span `[0,2]` |
   | `crotchet(), crotchetRest()`; select `[0,1]` | either kind | `CAN_ADD`, span `[0,1]` |
   | `crotchetRest()` alone; select `[0,0]` | either kind | `INELIGIBLE` |
   | crescendo `[0,3]` on a line whose element 4 is a rest; select `[4,4]` | `CRESCENDO` | `EXTEND`, span `[0,4]` |
   | line whose element 4 is a rest, crescendo `[5,8]`; select `[4,4]` | `CRESCENDO` | `INELIGIBLE` — a rest cannot anchor |
   | crescendo `[0,5]` on a line whose element 3 is a rest; select `[3,8]` | `CRESCENDO` | `EXTEND`, span `[0,8]` |

   Row 2 is the `18000/17323.abc` corpus shape — a diminuendo over a single note
   ending on the next rest — so use `Hairpin.Kind.DIMINUENDO` for it and say so in
   the `because` text. Row 5 is the asymmetry that must not regress: LilyPond's
   rest rule is right-side only.

4. Add to the existing `StructuralEligibility` nested class one test for the
   grace-note anchor widening: a line of crotchets with a `graceQuaver()` at index
   2 whose host is index 3, a crescendo on `[3,6]`, selection `[2,2]` →
   `CRESCENDO` → `EXTEND` span `[2,6]`. Phase 4 passes `spanEnd` rather than `end`
   as `canAnchorHairpin`'s `lastIndex`, so the grace note's host is now within
   reach and the grace note may anchor the extension; with the old `end` it could
   not. Say that in the `because` text, and leave `testLoneGraceNoteIsIneligible`
   (no hairpin nearby, so `spanEnd == end`) untouched as the contrast.

5. Run `./scripts/test.sh HairpinActionStateTest` and confirm green, then
   `./scripts/test.sh` for the full unit suite and confirm green.

---

## ✅ Phase 8: Model predicate coverage

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/dom/SpanBoundPredicateTest.java, src/test/java/songscribe/dom/LineHairpinDeletionTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — one nested class in an established matrix pattern plus two deletion cases

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

Phase 3 added two model predicates that only Phase 7 exercises, and it exercises
them through three layers of UI. Per the one-behavior-one-level rubric in
`testing-common.md`, both belong here.

### Tasks

1. In `src/test/java/songscribe/dom/SpanBoundPredicateTest.java`, add a nested
   class `OverlappingBeyondEndpointEveryBoundCombination` following the shape of
   the three that already exist (`ContainingEveryBoundCombination`,
   `OverlappingEveryBoundCombination`, `ExactlyEveryBoundCombination`): an
   `EXPECTED` matrix run by `assertMatrix` over `ANCHOR_CASES × END_CASES`, so
   every combination of `At`, `BEFORE_LINE`, `AFTER_LINE` and `ABSENT` is pinned.

2. In the same nested class, add three named cases spelling out the rule the matrix
   encodes:
   - a span whose end is exactly at `begin` is **not** matched — that is the shared
     endpoint two hairpins may legally have;
   - a span whose anchor is exactly at `end` is **not** matched, the mirror case;
   - a span overlapping by two or more elements **is** matched.

3. Add a fourth named case for the `ABSENT` anchor, asserting it **is** matched, and
   give it a comment stating the decision: `SpanBound.isAt` is false for every
   `Unpositioned` value, so a half-detached span cannot be shown to share only an
   endpoint and is treated as a collision. This is the conservative answer and is
   deliberate — contrast it with
   `OverlappingEveryBoundCombination.testAbsentAnchorIsFoundWhereContainingRejectsIt`,
   which exists because `overlapping` must keep finding such spans for the removal
   sweeps.

4. In `src/test/java/songscribe/dom/LineHairpinDeletionTest.java`, add a test that
   deleting a hairpin's end note when a rest survives immediately after it leaves
   the hairpin ending on that rest — the positive case for the `canEndHairpin`
   substitution inside `Line.resolveEndIndex`. Build the line with a trailing rest
   if the file's existing helper produces notes only.

5. In the same file, add the negative case: delete such that nothing which can end a
   hairpin survives in the range (only grace notes and non-durations remain), and
   assert the hairpin is removed — `resolveEndIndex` returning `-1`.

6. Run `./scripts/test.sh SpanBoundPredicateTest` and
   `./scripts/test.sh LineHairpinDeletionTest`, then `./scripts/test.sh` for the
   full unit suite. All green.

---

## ✅ Phase 9: Action, undo and MusicXML coverage

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** src/test/java/songscribe/ui/action/HairpinActionTest.java, src/test/java/songscribe/dom/MusicEditOperationsMutationTest.java, src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java, src/test/java/songscribe/io/musicxml/MusicXmlHairpinRoundTripTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — five focused tests added to existing fixtures, each with its setup and expectation spelled out

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

Context: `MusicEditOperations.resolveHairpinAction(songscribe.dom.Hairpin.Kind)`
now resolves per menu item, so a crescendo and a diminuendo may be enabled
simultaneously over one selection and end up sharing the element where one ends
and the next begins. `HairpinActionState` has a single `EXTEND` constant.
`addHairpinToSelection` takes a `Hairpin.Kind`. `Line.resolveEndIndex` now moves a
deleted hairpin end in to the last surviving *note or rest*. Phase 6 already made
the first three files compile and pass — this phase only adds tests.

### Tasks

1. `src/test/java/songscribe/ui/action/HairpinActionTest.java` — add the test this
   change exists for, in the `LabelAndEnabledPerState` nested class. Stub
   `resolveHairpinAction(Hairpin.Kind.CRESCENDO)` → `EXTEND` and
   `resolveHairpinAction(Hairpin.Kind.DIMINUENDO)` → `CAN_ADD` (use the per-kind
   `stubResolution` helper Phase 6 introduced), build both actions via
   `HairpinAction.createCrescendoAction(mainFrame())` /
   `createDiminuendoAction(mainFrame())`, call `updateEnabledState()` on each, and
   assert that **both** are enabled and that their `Action.NAME` values are
   `Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND)` and
   `Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO)` respectively. Name it something
   like `testBackToBackSelectionEnablesExtendCrescendoAndAddDiminuendoTogether`.

2. `src/test/java/songscribe/dom/MusicEditOperationsMutationTest.java` — add a test
   that the model really does hold two hairpins sharing one element. Following the
   pattern of the existing `testAddDynamicsEmitsOneAddition` (line ~500) and
   `testAddHairpinStripsPointDynamicsAcrossMergedRangeNotJustSelection` (line ~570):
   put a crescendo on the line, select a range whose first element is that
   crescendo's end element, call
   `env.operations().addHairpinToSelection(Hairpin.Kind.DIMINUENDO)`, and assert
   that exactly one `DiminuendoAddition` mutation is emitted and that the
   crescendo's anchor and end indices are unchanged.

3. `src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java` — add to the
   `ElementMutations` nested class (alongside
   `testDeletingHairpinEndpointRestoresTheOriginalSpanOnUndo` at line ~184) a test
   that deleting a hairpin's end note when a rest survives after it round-trips
   through `assertRoundTrip`. Build the line with a trailing rest rather than
   `songWithNotes(int)` if that helper only produces notes. Phase 8 asserts the
   reshaping rule itself; this test asserts that replaying it is lossless. Then
   confirm `testDeletingHairpinEndpointRestoresTheOriginalSpanOnUndo` (line ~184)
   and `testDeletingElementBetweenHairpinsRestoresBothOnUndo` (line ~196) still
   pass — both use all-note lines, so they should be unaffected; report if not.

4. `src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java` — add to the
   `HairpinExecution` nested class a round-trip for the back-to-back add itself:
   crescendo already on the line, add a diminuendo anchored on the crescendo's end
   element, and `assertRoundTrip`. Undo must leave the crescendo intact with its
   original indices.

5. `src/test/java/songscribe/io/musicxml/MusicXmlHairpinRoundTripTest.java` — the
   critical gap. Back-to-back hairpins become user-creatable for the first time in
   this change, and both sides of the io layer already handle them *deliberately*:
   `MusicXmlHairpinWriter.writeHairpinWedges` emits every stop wedge before every
   start wedge at a given note, and `WedgeResolver.resolveWedge` applies a pending
   stop before a pending start unless the note is opening the first hairpin. Reverse
   either and `WedgeResolver.handleWedge`'s overlap guard drops the second hairpin
   with nothing but a `LOG.warn` — the file saves clean and reopens short a hairpin.
   Nothing currently pins either ordering. Add two tests beside the existing
   `testBackToBackCrescendosLoadAsOneMergedCrescendo` and
   `testOverlappingWedgeStartIsDropped`:
   - `testBackToBackOppositeHairpinsRoundTrip` — crescendo `[0,4]` plus diminuendo
     `[4,8]`, written and re-read; both survive with their original anchor and end
     indices, and they still share element 4. Use the file's `assertHairpinEquals`.
   - `testStopWedgePrecedesStartWedgeOnASharedNote` — assert on the emitted XML that
     at the shared note the `<wedge type="stop">` element appears before the
     `<wedge type="diminuendo">`. Use the file's `wedgeAttribute` helper and follow
     `testCrescendoShiftsAreOnCorrectWedgesInOutput` for how it inspects output.

6. Run `./scripts/test.sh` and confirm the full unit suite is green.

---

## ✅ Phase 10: Docs

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Files:** docs/hairpin-editing.md, docs/line-layout.md, docs/clipboard.md, .agents/guides/messages.md, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/test/java/songscribe/layout/HairpinEndpointsTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — one new doc plus four prose edits, with the content specified

### Tasks

1. Create `docs/hairpin-editing.md`, the long-term home for the editor's hairpin
   rules. `docs/line-layout.md` is a layout doc and is the wrong place for a
   six-gate editor decision tree. Include:
   - The decision-tree diagram from Phase 4 verbatim.
   - The `INELIGIBLE` vs `BLOCKED` rule: `INELIGIBLE` = this span cannot carry a
     hairpin whatever else is on the line; `BLOCKED` = another hairpin is the
     obstacle.
   - The back-to-back rule: two opposite-type hairpins may share exactly the one
     element where one ends and the next begins, and `Span.overlappingBeyondEndpoint`
     is what permits it. More than one shared element is a collision.
   - The rest rule and its asymmetry: a hairpin may **end** on a rest
     (`Line.canEndHairpin`, LilyPond `hairpin.cc:268-271`) but may never **anchor**
     on one (`Line.canAnchorHairpin` unchanged). A trailing rest counts toward the
     two-column minimum.
   - Why the union converges rather than scanning once, and that the resolution is
     deliberately uncached.
   - The corpus figures and a reference to issue #743.

2. Rewrite the second bullet at `docs/line-layout.md:190` ("Example 8: Dynamics
   placement"). It currently says both endpoint rules are "not reachable from the
   editor yet" and names three gates (`MusicEditOperations.isHairpinEligibleSpan`
   requiring a pitched end, `resolveHairpinAction` blocking an opposite-type
   neighbour, `Line.addHairpin` absorbing a same-type one). All of that is now
   false: `isHairpinEligibleSpan` no longer exists, `resolveHairpinAction` is
   type-aware and takes a `Hairpin.Kind`, and a hairpin may end on a rest. Replace
   it with a bullet saying both rules are now reachable from the editor, keeping the
   corpus figures (back-to-back hairpins in 25 files, rest-bounded ones in two) and
   the reference to issue #743, and pointing at `docs/hairpin-editing.md` for the
   rules rather than restating them. Leave the following bullet about the
   unimplemented `Item::is_non_musical` barline rule exactly as it is.

3. `.agents/guides/messages.md:33-72` uses a command class as its worked example for
   the whole message-command convention, with a `boolean isCrescendo` field,
   constructor parameter, getter and `operations.addDynamicsToSelection(message.isCrescendo())`
   handler call. No `AddDynamicsCommand` exists — the class it is modelled on is
   `AddHairpinCommand`, which this plan converts to `Hairpin.Kind kind` / `kind()`.
   Retarget the example to `AddHairpinCommand` with the enum shape, so the guide
   stops teaching the pattern the codebase just replaced. Change nothing else about
   the guide's structure or its surrounding prose.

4. Add a clause to the hairpin-reconciliation rationale in **both** places it is
   written: `src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java:97`
   and `docs/clipboard.md:192`. Both say a crescendo and a diminuendo "say opposite
   things," which is why a fragment hairpin of a different type removes the
   destination's. That remains true for hairpins that **overlap** — which is all the
   reconciler ever sees, since it only judges a *straddled* destination hairpin — but
   two opposite-type hairpins may now legally abut, sharing one element. Say so, so
   the rule does not read as absolute. Change no code and no reconciliation
   behaviour.

5. In `src/test/java/songscribe/layout/HairpinEndpointsTest.java`, find the
   comments in the `RestAsEndElement` and `BackToBackHairpins` nested classes that
   explain the editor cannot reach these configurations. Those statements are now
   false. Replace each with a note that the test drives `HairpinEndpoints.compute`
   directly because it is a geometry test, and point at
   `songscribe.ui.HairpinActionStateTest` for the editor path and
   `docs/hairpin-editing.md` for the rules. Change no test code and no assertions —
   the geometry is unchanged.

6. Leave `plans/510-lilypond-hairpins.md` completely alone — it is a historical
   record, even though it describes the old gates.

7. Run `./scripts/compile.sh --test` and confirm SUCCESS.

---

## 🔄 Phase 11: Full verification and manual UI check

**Status:** In Progress — tasks 1-2 done and green; tasks 3-4 await user approval  <br>
**BlockedBy:** 7, 8, 9, 10  <br>
**Files:** —  <br>
**Recommended model/effort:** Opus 4.8, medium effort — judging whether observed UI behaviour matches the intended engraving result

### Tasks

1. Run `./scripts/compile.sh` — must report SUCCESS.

2. Run `./scripts/test.sh` — the full unit suite must be green. Pay particular
   attention to fallout in `HairpinActionStateTest`, `HairpinActionTest`,
   `HairpinRendererTest`, `SpanBoundPredicateTest`, `MusicEditOperationsMutationTest`,
   `MutationReplayerRoundTripTest`, `MusicXmlHairpinRoundTripTest`,
   `PasteSpanReconciliationTest`, `LineHairpinDeletionTest`, `LineHairpinMergeTest`
   and `ScoreViewControllerCommandHandlerTest`. `LineHairpinMergeTest` needs no
   changes — same-type merging is untouched — but confirm its
   `AbuttingHairpinMerge` different-type non-merge case still passes; it is now
   load-bearing for back-to-back hairpins.

3. Ask the user for permission, then run `./scripts/run.sh` and walk these checks,
   reporting the result of each:
   - Select 4 notes, Add Crescendo. Then select from its last note through 4 more
     notes: the Crescendo item must read "Extend Crescendo" and the Diminuendo item
     "Add Diminuendo", **both enabled**. Choose Add Diminuendo — the two wedges must
     meet at the shared notehead centre with a small gap
     (`Hairpin.BACK_TO_BACK_PADDING_SS` on each side), not overlap on the notehead.
   - One Undo removes the diminuendo and leaves the crescendo intact.
   - Save that file as MusicXML, close it, reopen it: **both** hairpins must come
     back, still sharing their element.
   - Select a note and the rest after it, Add Diminuendo — the wedge stops at the
     rest's left edge rather than running past the glyph.
   - Select a note followed by **two** rests — both items are disabled, because a
     hairpin ends on at most one rest. Shortening the selection to the note and the
     first rest enables them again.
   - Select a lone rest immediately after an existing crescendo — the item reads
     "Extend Crescendo"; applying it ends the wedge at that rest.
   - Select a lone rest immediately *before* an existing crescendo — both items are
     disabled, because a rest cannot anchor a hairpin.
   - Select a range that straddles the middle of an existing crescendo — both items
     are disabled.
   - Arm the rest tool, then select notes — the hairpin items must be **enabled**.
     This is the deliberate consequence of dropping `Flag.DISABLE_IN_REST_MODE`.

4. `src/test/java/songscribe/e2e/HairpinSelectionTest.java` exercises hairpin
   hit-testing. Back-to-back wedges produce two hit rects separated by
   `2 × Hairpin.BACK_TO_BACK_PADDING_SS`, so selection should stay unambiguous —
   but an **e2e run requires explicit user approval**. Ask before running
   `./scripts/test.sh e2e HairpinSelectionTest`, and skip it if the user declines.
