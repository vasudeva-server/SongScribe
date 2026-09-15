# Design Pass 6 — Step 2: Class Design of `Line`

**Type:** Step plan  <br>
**Parent:** `plans/design-pass-6/record.md` → step 2  <br>
**Created:** 2026-09-14  <br>
**Status:** Complete  <br>
**BlockedBy:** —

Every phase writes `src/main/java/songscribe/dom/Line.java`, so the phases run in
order. No phase compiles or runs tests; Phase 8 is the single gate. Every phase
follows `.claude/rules/serena.md`: `jet_brains_*` tools for exploration, `Edit`
for code changes, `jet_brains_rename` with `rename_in_comments: false` for
renames. Paths below are relative to the repository root; Java paths omit the
`src/main/java/songscribe/` prefix unless they are in the test tree.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [ElementRange](#-phase-1-elementrange) | ✅ Complete | — |
| 2 | [Run queries, deletions and relocations](#-phase-2-run-queries-deletions-and-relocations) | ✅ Complete | — |
| 3 | [EditableRun](#-phase-3-editablerun) | ✅ Complete | — |
| 4 | [LineSpans and per-kind span operations](#-phase-4-linespans-and-per-kind-span-operations) | ✅ Complete | — |
| 5 | [Re-point span callers](#-phase-5-re-point-span-callers) | ✅ Complete | — |
| 6 | [Ending predictions on ElementChange](#-phase-6-ending-predictions-on-elementchange) | ✅ Complete | — |
| 7 | [Song façade and terminal policy](#-phase-7-song-façade-and-terminal-policy) | ✅ Complete | — |
| 8 | [Gate](#-phase-8-gate) | ✅ Complete | — |

---

## ✅ Phase 1: ElementRange

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** dom/StaffElementRun.java, dom/Line.java, dom/ElementChange.java, dom/ProjectedEdit.java, dom/KeyChangeStranding.java, undo/MutationReplayer.java, ui/component/ScoreViewController.java, ui/component/score/PreviewElementInserter.java, ui/selection/SelectionActionApplier.java, ui/selection/RangeQueries.java, ui/selection/Selection.java, ui/action/TrillAction.java, ui/dialog/KeyChangeDialogController.java, ui/MusicEditOperations.java, src/test/java/songscribe/dom/LineDeletionTest.java, src/test/java/songscribe/dom/StrandedKeyChangeTest.java, src/test/java/songscribe/dom/KeyChangeStrandingTest.java, src/test/java/songscribe/dom/InheritedKeyPropagationTest.java, plans/design-pass-6/findings-1-2.md, plans/design-pass-6/class-design.md  <br>
**Recommended model/effort:** Fable, low effort — introduces the one type for an inclusive element range and writes its contract; the signature sweep that follows is mechanical but every call site has to be judged for a transposed pair.

### Tasks

1. Rename the nested record `StaffElementRun.EffectiveRange` (`dom/StaffElementRun.java` L419) to `ElementRange` with `jet_brains_rename`, `rename_in_comments: false`. It stays nested in `StaffElementRun`. Afterwards run `git status --porcelain | grep -v '\.java$'` and restore every Markdown line the rename rewrote in `plans/design-pass-6/findings-1-2.md` and `plans/design-pass-6/class-design.md` so that they still name both the old and the new name where they record the rename.
2. Give `ElementRange` a compact canonical constructor that throws `IllegalArgumentException` when `begin > end`. Add `int size()` (`end - begin + 1`), `boolean contains(int index)`, and a static factory `ElementRange.single(int index)` for the one-element range. Keep `shiftedBy`.
3. Write `ElementRange`'s contract before converting anything to it: it is the inclusive `[begin, end]` element range; `begin <= end` is an invariant carried by the constructor; it says nothing about whether the range is widened — `effectiveRange` (task 4) is what produces a widened one. Fan-in is six production files plus three tests, so this earns `@param`, `@invariant`, `@throws` on the constructor and `@return` on every accessor.
4. Change `StaffElementRun.effectiveRange(int begin, int end)` to `effectiveRange(ElementRange range)` returning the widened `ElementRange`. Update the two callers inside the interface (`redundantKeyChangeRanges` builds `effectiveRange(ElementRange.single(index))`) and every caller outside it — find them with `jet_brains_find_referencing_symbols` on `StaffElementRun/effectiveRange`; the known ones are `ui/dialog/KeyChangeDialogController.java`, `ui/component/ScoreViewController.java`, `ui/selection/Selection.java`, `src/test/java/songscribe/dom/LineDeletionTest.java`, `src/test/java/songscribe/dom/InheritedKeyPropagationTest.java`.
5. Convert every `(int, int)` inclusive-range pair on `Line` to a single `ElementRange` parameter, and update every caller found with `jet_brains_find_referencing_symbols`:
   - `getElements(int start, int end)` L787 → `getElements(ElementRange range)`, body `elements.subList(range.begin(), range.end() + 1)`. Callers: `dom/ProjectedEdit.java`, `dom/ElementChange.java` (`Deletion.deletedElements`), `ui/selection/SelectionActionApplier.java`, `ui/action/TrillAction.java`, `ui/component/ScoreViewController.java`, `ui/selection/RangeQueries.java`.
   - `removeRange(int from, int to)` L845 → `removeRange(ElementRange range)`. The guard loop becomes `IntStream.rangeClosed(range.begin(), range.end())`; the `ElementRangeDeletion` record it posts keeps its own `(from, to)` components — that record belongs to `message/mutation` and is not changed here. Caller: `undo/MutationReplayer.java` (`applyRedo`'s `ElementRangeDeletion` arm).
   - `keyAtEndOfLineAfterRemoving(int begin, int end)` L431 → takes `ElementRange`. Caller: `ScoreViewController.deletionStranding` L1217, which already holds a `bounds` range — pass it whole.
   - `spansStructuralBoundary(int begin, int end)` L1738 → takes `ElementRange`. Caller: `ui/MusicEditOperations.java` `resolveHairpinAction`.
   - `removeOverlappingTuplets(int begin, int end)` L1759 → takes `ElementRange`. Internal callers at L760, L815, L854, L1472 pass `ElementRange.single(index)` or the range they hold; external caller `SelectionActionApplier.validateSpans` L368.
   - `removeTrillsOverlapping(int beginIndex, int endIndex)` L1817 → takes `ElementRange`. Caller: `ui/action/TrillAction.java` L138.
   - `hasEndingInvalidatedByDeletion(int from, int to)` L1972 → takes `ElementRange`. Callers: `ScoreViewController` L970, L1183, L1428.
6. Convert `ElementChange.forDeletion(Line line, int from, int to)` and the `Deletion` constructor to take `ElementRange range`; `Deletion` stores the range instead of two ints, `deletedCount()` becomes `range.size()`, `projectedIndexOf`'s in-range test becomes `range.contains(current)`, and `projectedElements` reads `range.begin()`. Callers: `Line.removeElement` L820 (`ElementRange.single(index)`), `Line.removeRange` L859, `Line.hasEndingInvalidatedByDeletion` L1973.
7. Every raw `new StaffElementRun.ElementRange(a, b)` construction outside the interface is now a call site to judge: `ScoreViewController.tryInsertFragment` L1390 rebuilds a range from another range's `begin()`/`end()` — pass the range itself; `PreviewElementInserter.replacementFits` L214 and `SelectionActionApplier.fitsWithChainsWalked` L317 construct `(index, index)` and `(begin, end)` for `ProjectedEdit.replacing` — use `ElementRange.single` where the two ints are equal. Leave `ProjectedEdit.replacing`'s parameter type as the renamed record.
8. Rewrite the Javadoc of `Line.deleteRange` (L871–897) so it states that `range` must be the widened range `effectiveRange` returns: the type no longer says so, and a caller handing it a raw selection deletes half a pair.
9. Update the Javadoc of `StaffElementRun.redundantKeyChangeRanges` and `LineDeletionTest` L153–154 where they name or construct the record, so the tests compile against the new constructor and factory. Do not add tests; step 6 of the pass triages them.

---

## ✅ Phase 2: Run queries, deletions and relocations

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** dom/StaffElementRun.java, dom/Line.java, io/LegacyLineOffsets.java  <br>
**Recommended model/effort:** Sonnet, medium effort — bodies move unchanged onto the interface with two mechanical rewrites; deletions are of members already established as dead.

### Tasks

1. Move these `Line` members to `StaffElementRun` as `default` methods, keeping each Javadoc with its body: `isEmpty` L1065, `lastNoteIndex` L1082, `isInsideGraceHostPair` L1132, `precedingGraceNoteIndex` L1140, `isSamePitchAsFollower` L1166, `isKeyChangeAt` L1187, `isKeyChangeBarlineAt` L1200 (as a `private` interface method — it stays private), `canReplaceElementAt` L1222, `canInsertElementAt` L1261, `canBearSyllableAt` L1276, `nearestNonGraceIndex` L1331, `spansStructuralBoundary` L1738 (already taking `ElementRange` after Phase 1). Place the pairing-related ones (`isInsideGraceHostPair`, `precedingGraceNoteIndex`, `isKeyChangeAt`, `isKeyChangeBarlineAt`, `canReplaceElementAt`, `canInsertElementAt`) under the interface's `Pairing` section and the rest in a new `Element queries` section after it.
2. While moving, rewrite every direct read of the `elements` field in those bodies: `elements.get(i)` → `getElement(i)`, `elements.size()` → `elementCount()`, `elements.isEmpty()` → `elementCount() == 0`. Nothing else in a body may change. Every `{@link #...}` in the moved Javadoc that pointed at a `Line` member now points at the interface member of the same name; fix any that pointed at a member that stays on `Line` (`{@link Line#...}`).
3. Delete `Line.isAnnotation` L1765, `getFirstTrill` L1771, `getFirstBeatChange` L1779, and the two banner comments `// Range Element Management (Phase 4+)` L1786–1788 and `// Beam Group Management (Phase 4+)` L2051–2053.
4. Delete the early return and its comment in `Line.detach` L504–512 (`if (element.getParentLine() != this) { return; }`), keeping the `elementIndexMap = null` write and the comment above it. Then rewrite the comment in `Line.setElement` L722–724 that explains the detach-before-attach order in terms of that guard: the order still matters — a self-replace must end with the element attached — but the guard it cites no longer exists.
5. Delete the `if (run.isEmpty()) { return; }` at the top of `Line.applySpanRun` L1703–1705.
6. Move `BEAT_CHANGE_DEFAULT_Y_SS` L144, `ENDING_DEFAULT_Y_SS` L161, `TRILL_DEFAULT_Y_SS` L166 from `Line` into `io/LegacyLineOffsets.java` as `private static final double` constants read by its `DEFAULTS` initializer, and remove the `import songscribe.dom.Line` there if nothing else in the file uses it. Drop the `// -24px`, `// 50px`, `// -25px`, `// -27px` trailing comments from all four Y constants, including `LYRICS_DEFAULT_Y_SS`, which stays on `Line`.
7. Replace the Javadoc of `Line.lyricsYPosSs` L150–155 with a description of the field as it is: the vertical position of this line's lyric rows below the staff, in staff spaces, shared by every verse on the line.
8. Declare `Line` `final`.

---

## ✅ Phase 3: EditableRun

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** dom/EditableRun.java, dom/Line.java, dom/DetachedLyricRun.java, ui/clipboard/Fragment.java  <br>
**Recommended model/effort:** Fable, low effort — a new interface with a contract that separates structural editing from mutation recording, and a second implementor whose storage may not admit the operations.

### Tasks

1. Create `dom/EditableRun.java`: `public interface EditableRun extends LyricRun` with three abstract primitives — `void addElement(int index, StaffElement element)`, `void removeElement(int index)`, `void removeRange(ElementRange range)` — and, moved from `Line` with their bodies and Javadoc unchanged, the defaults `insertRun` L676, `deleteRange` L898, `deleteRanges` L950, and the private interface methods `deleteRangeElementwise` L961 and `deleteElement` L980. Every member those bodies call is on `LyricRun`, `StaffElementRun` or one of the three primitives; if one is not, stop and report it rather than adding a fourth primitive.
2. Write the interface contract before implementing: an `EditableRun` is a run whose elements can be inserted and removed and that repairs the lyric chains and glissandi the edit breaks. It promises nothing about undo. The clause "must be called inside a modification bracket, records into it" belongs to `Line`'s three primitives, and stays on their Javadoc, not on the interface — a detached run records nothing. `insertRun` (fan-in 2), `deleteRange` (1) and `deleteRanges` (2) keep the contracts they have.
3. `Line` implements `EditableRun` instead of `LyricRun`; its `addElement(int, StaffElement)`, `removeElement`, `removeRange` gain `@Override`; the five moved members are deleted from `Line`.
4. `DetachedLyricRun` implements `EditableRun`. Before writing its three primitives, check with `jet_brains_find_symbol` (`include_body: true`) how `ui/clipboard/Fragment.run()` and `Fragment.capture` build the `elements` list handed to the record: if it is an immutable list (`List.copyOf`, `List.of`, `toList()`), a `removeRange` on it throws `UnsupportedOperationException`, so change the producer to hand over a mutable list — the record's own Javadoc says the list is used live. Implement the primitives on `elements` directly; they record nothing, as `modifyElement` there already does not.

---

## ✅ Phase 4: LineSpans and per-kind span operations

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Files:** dom/LineSpans.java, dom/Line.java, dom/Span.java, dom/NoteSpan.java, dom/Tie.java, dom/Beam.java, dom/Tuplet.java, dom/Trill.java, dom/Ending.java, dom/Hairpin.java, dom/Crescendo.java, dom/Diminuendo.java, undo/MutationReplayer.java, docs/span-invalidation.md, plans/design-pass-register.md  <br>
**Recommended model/effort:** Fable, low effort — extracts the span registry into its own owner and dissolves the per-kind add/remove methods into the span kinds, each carrying its own merge policy and mutation record.

### Tasks

1. Create `dom/LineSpans.java`: `public final class LineSpans implements SpanLookup`, one per line, constructed as `new LineSpans(this)` by `Line`'s field initializer and exposed through `public LineSpans spans()`. `Line` stops implementing `SpanLookup`. `LineSpans` holds `private final Line line` with a `Line line()` accessor, the `spans` list and its unmodifiable view (from `Line` L89–97), and these members moved from `Line` with their Javadoc: `getSpans` L1893, `anchorIndexOf` L1898, `endIndexOf` L1903, `boundOf` L1932, `otherLineOf` L526, `appendChild` L555, `removeChild` L570, `applySpanOutcomes` L1597 renamed `sweep(ElementChange change)`, `collectReshape` L1628, `HairpinSpan` L1571, `mergeAdjacentSpans` L1674, `applySpanRun` L1702, `mergeOverlappingSpans` L1371, `bothResolved` L1435, `ResolvedIndexPredicate` L1422, `removeOverlappingTuplets` L1759, `removeTrillsOverlapping` L1817, and the constant `SPAN_ADJACENCY_REACH` L176. Where a moved body reads `this` as the line (`span.outcomeFor(change, this)`, `song.contains(this)`, `index()`), it now reads `line`; where it reads `elements.get(i)` it reads `line.getElement(i)`; where it reads `song` it reads `line.getSong()`. `appendChild`/`removeChild` reach the other line's list through `otherLine.spans().spans` — the field is private but the class is the same.
2. Move the anchor re-pointing loop from `Line.setElement` L729–739 into `LineSpans` as `void repoint(StaffElement oldElement, StaffElement newElement)`, called from inside `setElement`'s mutator where the loop was.
3. Give `LineSpans` two package-private generic primitives that every kind calls: `void add(Span span, Mutation addition)` — `line.applyChange(addition, () -> appendChild(span))` — and `void remove(Span span, Mutation removal)` — looks `span` up in `spans`, returns without recording when absent, otherwise `line.applyChange(removal, () -> removeChild(span, index))`. This is the one place the absent-span early return exists.
4. On `Span`, declare `public void addTo(LineSpans spans)` and `public void removeFrom(LineSpans spans)` with defaults that emit `SpanAddition(spans.line(), this)` / `SpanRemoval(spans.line(), this)` through the two primitives. Write their contract: `addTo` adds this span to the line's registry applying the kind's own displacement policy and recording the kind's own mutation; `removeFrom` removes it if present and is a no-op otherwise — the promise `Line.removeInvalidatedSpan`'s Javadoc L1852–1858 makes today, stated once here. State that a kind with a displacement policy skips it while `spans.line().getSong().isReplaying()`, because the recorded batch already carries the companion mutations.
5. Override per kind, each moved from the corresponding `Line` method and then deleted from `Line`:
   - `Tie`: `TieAddition`/`TieRemoval`, no policy (from `addTie` L1298, `removeTie` L1305).
   - `Beam`: `BeamingAddition`/`BeamingRemoval`; unless replaying, `spans.mergeOverlapping(this, Beam.class, 0)` first (from `addBeaming` L1348, `removeBeaming` L1445).
   - `Tuplet`: `TupletAddition`/`TupletRemoval`; unless replaying, `spans.removeOverlappingTuplets(new ElementRange(anchorIndex, endIndex))` first (from `addTuplet` L1464, `removeTuplet` L1482).
   - `Hairpin` (sealed over `Crescendo`, `Diminuendo`): unless replaying, `spans.mergeOverlapping(this, getClass()-appropriate type, LineSpans.SPAN_ADJACENCY_REACH)` first, then the kind's record — `CrescendoAddition`/`CrescendoRemoval` on `Crescendo`, `DiminuendoAddition`/`DiminuendoRemoval` on `Diminuendo` (from `addHairpin` L1532, `removeHairpin` L1551, `addCrescendo`/`removeCrescendo` L1501–1510, `addDiminuendo`/`removeDiminuendo` L1517–1526). Put the shared merge step on `Hairpin` and only the record on each subclass; the merge type is the concrete hairpin class.
   - `Trill`: default records; unless replaying, `spans.removeTrillsOverlapping(...)` over its own endpoints first (from `addTrill` L1806).
   - `Ending`: defaults.
   `mergeOverlappingSpans(span, type, remover, absorbAdjacent)` becomes `<R extends Span> void mergeOverlapping(R span, Class<? extends R> type, int reach)`: the `remover` parameter goes — every subsumed span is removed by `subsumed.removeFrom(this)` — and `absorbAdjacent` goes with it, `reach` being what the two callers pass. `applySpanRun`'s closing `switch (reshaped)` becomes `reshaped.addTo(this)`; `sweep`'s `doomed.forEach(this::removeInvalidatedSpan)` becomes `doomed.forEach(span -> span.removeFrom(this))`.
6. Delete from `Line`: `addSpan` L1795, `removeSpan` L1830, `removeInvalidatedSpan` L1860, `addPastedSpan` L1881, `canAnchorHairpin` L1652, `canEndHairpin` L1663, and the `spans`/`spansView` fields. `Line`'s four primitives call `spans.sweep(change)` where they called `applySpanOutcomes`; `addElement(int, …)` L635–639 uses `spans.findTupletAt(index)` and `tuplet.removeFrom(spans)`; `modifyElement`, `removeElement`, `removeRange` call `spans.removeOverlappingTuplets(...)`.
7. In `undo/MutationReplayer.java`, keep the per-record arms of `applyUndo` L145–158 and `applyRedo`'s counterparts, but each arm becomes `span.addTo(line.spans())` or `span.removeFrom(line.spans())` on the record's span. The arms stay distinct because the records are distinct types.
8. Reread `docs/span-invalidation.md` and `docs/hairpin-editing.md`. They state concepts, not class names; where either says the *line* asks its spans, sweeps, or hosts the merge rules, and that owner is now the line's span registry, rewrite the sentence to state the current shape. Do not add class or method names.
9. Append to the Notes column of row 7 in `plans/design-pass-register.md`: pass 6 added `addTo`/`removeFrom` to `Span` and every subclass, each carrying its own merge policy and mutation record, so the per-kind add/remove methods no longer exist on `Line`.

---

## ✅ Phase 5: Re-point span callers

**Status:** Complete  <br>
**BlockedBy:** 4  <br>
**Files:** dom/Song.java, dom/Tie.java, dom/Span.java, dom/NoteElement.java, dom/TupletValidator.java, dom/ModificationSession.java, dom/TupletLoadPass.java, io/LineIO.java, io/FormatMigrator.java, io/musicxml/MeasureMapper.java, io/musicxml/MeasureBuilder.java, io/musicxml/NoteBuilder.java, io/musicxml/ScorePartwiseBuilder.java, layout/, midi/, ui/, src/test/java/songscribe/  <br>
**Recommended model/effort:** Sonnet, medium effort — mechanical re-pointing of call sites to the new owners; every rewrite has exactly one target form.

### Tasks

1. For each member of `SpanLookup` (`getSpans`, `anchorIndexOf`, `endIndexOf`, `findSpans` ×2, `findFirstSpan`, `hasSpan`, `findBeamAt`, `sameBeamAt`, `findTies`, `findTieAt`, `findExactTie`, `findTieBetween`, `findTiesTouching`, `sameTieAt`, `findTupletAt`, `findTupletsOverlapping`, `isInsideHairpin`, `findTrillsOverlapping`, `hasTrillOverlapping`, `findEndings`, `findEndingAt`, `isInsideAnyEnding`), run `jet_brains_find_referencing_symbols` on `SpanLookup/<member>` in `dom/SpanLookup.java` and on the former `Line/<member>` overrides, and rewrite every call whose receiver is a `Line` from `line.<member>(...)` to `line.spans().<member>(...)`. A receiver already typed `SpanLookup` or `LineSpans` is left alone. A local that repeats `line.spans()` more than once in a method is assigned once (`var spans = line.spans();`) per the accessor-chain rule in `.claude/rules/java.md`. Known caller files: `dom/Song.java` (`removeSpansBetweenNonAdjacentLines`), `dom/Tie.java`, `layout/stacking/DynamicGrouper.java`, `layout/HairpinEndpoints.java`, `layout/ElementColumn.java`, `midi/MidiSequenceBuilder.java`, `midi/LineTrackBuilder.java`, `io/musicxml/ScorePartwiseBuilder.java`, `ui/MusicEditOperations.java`, `ui/clipboard/PasteSpanReconciliation.java`, `ui/clipboard/Fragment.java`, `ui/component/score/LineRenderer.java`, `ui/renderer/LineInvariants.java`, `ui/renderer/TieRenderer.java`, `ui/selection/RangeQueries.java`, `ui/selection/SelectionCoordinator.java`; the lookup finds the rest.
2. Rewrite every caller of the deleted per-kind methods, found with `jet_brains_find_referencing_symbols` before Phase 4 deleted them or by compiling nothing and searching for the names with the same tool on the span kinds: `line.addBeaming(b)` → `b.addTo(line.spans())`, `line.removeBeaming(b)` → `b.removeFrom(line.spans())`, and the same for `addTie`/`removeTie`, `addTuplet`/`removeTuplet`, `addCrescendo`/`removeCrescendo`, `addDiminuendo`/`removeDiminuendo`, `addTrill`, `addSpan`, `removeSpan`, `removeInvalidatedSpan`, `addPastedSpan`. Known callers: `ui/MusicEditOperations.java` (`toggleBeaming`, `toggleBeamWithPredecessor`, `toggleTuplet`, `makeFirstSecondEnding`, `addHairpinToSelection`), `ui/component/ScoreViewController.java` (`deleteSelectedTarget`, `tryInsertFragment`), `ui/component/score/PreviewElementInserter.java` (`insertElement`, `modifyExistingElement`), `dom/ModificationSession.java` (`removeTupletsInvalidatedFrom`), `dom/TupletLoadPass.java` (`settleTuplets`), `ui/action/TrillAction.java`, `io/LineIO.java` (`addNoteSpanFromPair`, `createTupletsFromPending`, `createEndingsFromPendingPairs`, `createTrillsFromPendingPairs`), `io/musicxml/MeasureMapper.java` (`buildEnding`, `resolveTrills`), `dom/Song.java` (`removeSpansBetweenNonAdjacentLines`), `src/test/java/songscribe/layout/VoltaAlignmentFixtureTest.java`, `src/test/java/songscribe/layout/EndingBracketGeometryTest.java`. Javadoc `{@link Line#addBeaming}`-style references in `MusicEditOperations` L154 and `PasteSpanReconciliation` L115 are rewritten to the `addTo`/`removeFrom` form.
3. `ui/MusicEditOperations.java` `resolveHairpinAction`: `line.canAnchorHairpin(begin, spanEnd)` → `Hairpin.canAnchorAt(line.getElements(), begin, spanEnd)`; `line.canEndHairpin(i)` → `Hairpin.canEndAt(line.getElements(), i)`; `Line.SPAN_ADJACENCY_REACH` (L638 Javadoc, L708, L709) → `LineSpans.SPAN_ADJACENCY_REACH`.
4. `ui/action/TrillAction.java` L138 and `ui/selection/SelectionActionApplier.java` `validateSpans` L368: `line.removeTrillsOverlapping(range)` / `line.removeOverlappingTuplets(range)` → the same on `line.spans()`.
5. `dom/Span.java` L347 Javadoc names `Line#addTrill` and `Line#removeTrillsOverlapping`; point it at `Trill#addTo` and `LineSpans#removeTrillsOverlapping`.

---

## ✅ Phase 6: Ending predictions on ElementChange

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Files:** dom/ElementChange.java, dom/Line.java, ui/component/ScoreViewController.java, ui/component/score/PreviewElementInserter.java, ui/selection/SelectionActionApplier.java  <br>
**Recommended model/effort:** Fable, low effort — moves a prediction onto the value it is a function of and opens a package-internal type to the UI layer, which is a contract decision.

### Tasks

1. Add `Line line()` to the `ElementChange` interface; `Insertion` and `Replacement` already supply it as a record component, `Deletion` gains the accessor over its field.
2. Move `Line.removesConfirmableSpan` L1990 onto `ElementChange` as `default boolean removesConfirmableSpan()`: over `line().spans().getSpans()`, false when no span answers `Span::requiresInvalidationConfirm`, otherwise true when any such span's `outcomeFor(this, line())` is `SpanOutcome.Simple.REMOVE`. The supplier indirection goes: the change is already built, and building one costs a record — the projection it holds is built lazily and this query never asks for it. Write the contract: the answer is the same one the sweep will act on, so a prompt and the edit it precedes cannot disagree; only spans that ask to be confirmed are consulted.
3. Add `Ending.EndingEffect endingEffect()` to `Replacement` only, from `Line.findEndingReplacementEffect` L2009: over `line().spans().findEndings()`, each asked `checkReplacement(oldElement(), newElement(), line())`, first non-`None` wins, else `None.INSTANCE`.
4. Make the three factories `public` and add `public static Insertion forInsertion(Line line, int index, ElementType insertedType)` that builds the element with `insertedType.newInstance()`, carrying over the comment from `Line.hasEndingInvalidatedByInsertion` L2027–2029 that the endings read no more of an inserted element than its type.
5. Delete `Line.hasEndingInvalidatedByDeletion` L1972, `removesConfirmableSpan`, `findEndingReplacementEffect`, both `hasEndingInvalidatedByInsertion` overloads L2026–2049, and rewrite the callers:
   - `ScoreViewController.handleDelete` L970 and `confirmEndingInvalidatedByDeletion` L1183: `ElementChange.forDeletion(line, range).removesConfirmableSpan()`.
   - `ScoreViewController.tryInsertFragment` L1428: the same over `deleteRange`; L1433: `insertedTypes.stream().anyMatch(type -> ElementChange.forInsertion(line, insertIndex, type).removesConfirmableSpan())`, carrying the comment from L2039–2042 that every type is tested at the pre-insertion index.
   - `PreviewElementInserter.insertElement` L405: `ElementChange.forInsertion(line, xIndex, previewElement.getType()).removesConfirmableSpan()`; `modifyExistingElement` L451: `ElementChange.forReplacement(line, elementIndex, replacement).endingEffect()`.
   - `SelectionActionApplier.decideChanges` L248: `ElementChange.forReplacement(line, i, replacement).endingEffect()`.
6. Rewrite the `ElementChange` class Javadoc so it states both readers of a change — the span sweep and the confirmation prompts — without narrating that the second used to live on `Line`.

---

## ✅ Phase 7: Song façade and terminal policy

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Files:** dom/Line.java, dom/Song.java, dom/TerminalMaintainer.java, dom/ModificationSession.java, ui/MusicEditOperations.java, ui/component/LyricChainEditor.java, ui/component/LyricEditor.java, ui/component/ScoreViewController.java, ui/component/score/PitchShifter.java, ui/component/score/PreviewElementInserter.java, ui/dialog/AttachmentDialogController.java, ui/edit/GraceModeManager.java, ui/edit/PasteModeManager.java, ui/action/TrillAction.java, ui/SlideOperations.java, ui/selection/SelectionActionApplier.java, undo/MutationReplayer.java, src/test/java/songscribe/, plans/design-pass-register.md  <br>
**Recommended model/effort:** Fable, low effort — retires a duplicated bracket guard, traces a nullable label to its producers, and states the terminal policy once on the type that owns it.

### Tasks

1. Reduce `Line.applyChange` L228–248 to `song.applyChange(mutation, mutator)`. `Song.applyChange` already runs the mutator and returns under suspended tracking, throws `IllegalStateException` outside a bracket (via `ModificationSession.applyChange`), and restores the key invariant in both cases, so the suspended branch and the throw in `Line` are a second copy. Rewrite `Line.applyChange`'s Javadoc to say it posts through the song's bracket and cite `Song#applyChange` for the promises; keep `@throws IllegalStateException` since the song still throws.
2. Make `Song.maintainKeyInvariant` `private` and delete the Javadoc paragraph that says it is package-private for `Line.applyChange`'s suspended branch. Its `@Nullable Key startingKeyBefore` parameter stays nullable: `Song.applyChange` passes `startingKey()`, which is null for an empty song and for an unkeyed line 0 while a document is being built — the comment above that call states why. Delete the sentence in `Song.applyChange`'s Javadoc that says the suspended path in `Line#applyChange` runs no walk, and rewrite the comment in `ModificationSession.applyChange` that says it "mirrors the guard in `Line.applyChange`" to state its own rule without the mirror.
3. Delete the four bracket overloads on `Line`: `withModification(Runnable)` L253, `withModificationResult` L264, `withModification(String, Runnable)` L272, `withOptionallyNamedModification` L287. Run `jet_brains_find_referencing_symbols` on each (test tree included) and rewrite every caller to the same call on `line.getSong()`. Known callers of the first three: `ui/MusicEditOperations.java` (`toggleBeaming`, `toggleBeamWithPredecessor` ×2, `toggleTuplet` ×2, `makeFirstSecondEnding`, `modifyStemDirection`, `addHairpinToSelection`, `toggleTieInRange`), `ui/component/LyricChainEditor.java`, `ui/edit/GraceModeManager.java`, `ui/action/TrillAction.java`, `ui/component/LyricEditor.java` (eight sites), `ui/component/score/PitchShifter.java`, `ui/component/score/PreviewElementInserter.java` (`handleClick` ×2), `ui/dialog/AttachmentDialogController.java`, `ui/SlideOperations.java`, `ui/component/ScoreViewController.java` (`deleteSelectedTarget`, `deleteAttachment`), `ui/edit/PasteModeManager.java`. Where a method already holds `line.getSong()` in a local, reuse it.
4. `withOptionallyNamedModification` has two callers, `ScoreViewController.deleteElementRange` L1247 and `SelectionActionApplier.apply` L174, each holding a `@Nullable String` label. Trace each label to every producer with `jet_brains_find_referencing_symbols` on the enclosing method. Where every producer can supply a label, make the parameter non-null and call `song.withModification(label, body)`. Where a producer genuinely has none, that caller branches between `Song`'s two overloads at its own boundary. Do not add the nullable overload to `Song`. Report which of the two outcomes each caller took.
5. Add three methods to `TerminalMaintainer`, exposed on `Song` by delegation exactly as `Song.isAutoMaintainedTerminal` delegates today, each throwing `IllegalStateException` with the message the corresponding `Line` check throws now:
   - `checkTerminalPolicyForInsertion(Line line, int index, ElementType incoming)` — from `Line.addElement(int, …)` L624–630: `FINAL_DOUBLE_BARLINE` may only be appended at `line.elementCount()` of the last line.
   - `checkTerminalPolicyForReplacement(Line line, int index, ElementType incoming)` — from `Line.setElement` L694–705: `FINAL_DOUBLE_BARLINE` may only replace the last element of the last line, and the auto-maintained terminal may only be replaced by a type for which `ElementType.isValidSongTerminal()` holds.
   - `checkTerminalPolicyForRemoval(Line line, ElementRange range)` — from `Line.removeElement` L807 and `removeRange` L846: no position in `range` may hold the auto-maintained terminal.
   All three are bypassed — return without checking — when `song.isMutationTrackingSuspended() || song.isInAutoMaintenance() || song.isReplaying()`, the condition `Line.isTerminalGuardBypassed` L608 states; put that condition in one private method on `TerminalMaintainer` with the Javadoc from L601–607. Fan-in for each is `Line` alone, so each gets an accurate name and its `@throws`; the policy itself — when the song's terminal may be touched, and by what — is stated once in `TerminalMaintainer`'s class Javadoc.
6. `Line`'s primitives call the three checks first: `addElement(int, …)` calls the insertion check, `setElement` the replacement check, `removeElement` the removal check with `ElementRange.single(index)`, `removeRange` the removal check with its range. Delete `isTerminalGuardBypassed` L608, `guardsTerminalAt` L619, `TERMINAL_NOT_REMOVABLE` L86, and the four inline checks. Before deleting, confirm with `jet_brains_find_referencing_symbols` that nothing else reads `guardsTerminalAt` or `TERMINAL_NOT_REMOVABLE`.
7. Reread `docs/mutations.md`; where it describes the line as guarding or checking a bracket of its own, rewrite to the current shape — the song's bracket is the only one — without naming classes.
8. Append to the Notes column of row 12 in `plans/design-pass-register.md`: pass 6 moved the terminal-write policy onto `TerminalMaintainer` as three checks `Song` exposes, and made `Song.maintainKeyInvariant` private.
9. Rewrite `Line`'s class Javadoc (L28–82) to describe the class as it now is: an element run that belongs to a song and posts mutations, holding its key, its elements, its lyric offset and its span registry. Delete any sentence describing a member that moved in Phases 2–7. Check every universal claim in it and in the `elementIndexMap` Javadoc L130–133 (which writes go through `attach`/`detach`) against the primitives as they now read.

---

## ✅ Phase 8: Gate

**Status:** Complete  <br>
**BlockedBy:** 7  <br>
**Recommended model/effort:** Sonnet, medium effort — compile, fix what the compiler reports against the phases above, run the named tests, snapshot.

### Tasks

1. Run `./scripts/compile.sh --test`. Fix every error by finishing the phase whose task it belongs to — a caller a phase's lookup missed, a Javadoc `{@link}` to a moved member — never by adding an overload, an adapter, or a delegating method on `Line` to satisfy a caller. A test that names a deleted or renamed member is re-pointed to the new owner; no test is added, rewritten in substance, or deleted here — step 6 of the pass owns test triage.
2. Run `./scripts/test.sh LineTest LineDeletionTest StrandedKeyChangeTest KeyChangeStrandingTest`, then `./scripts/test.sh InheritedKeyPropagationTest VoltaAlignmentFixtureTest EndingBracketGeometryTest MusicXmlStrandedKeyChangeTest`. A failure is read against the phase that changed the code it exercises; never rerun with flags and never treat one as pre-existing.
3. Snapshot with one Bash call: `git add -A && git stash store -m "design pass 6: step 2" "$(git stash create)"`.
4. Report: the compile and test output verbatim, which callers the compiler found that the phases' lookups did not, and the outcome of Phase 7 task 4 for each of the two label callers. A green run proves integration only; do not report it as evidence the design is right.
