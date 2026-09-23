# Pass 7, Spans — steps 1 and 2

All paths are under `src/main/java/songscribe/` unless they start with `src/test` or `docs`. Line numbers are from commit `e40e512f`.

## What it found

### The target

| Type | Lines | What it is |
|---|---|---|
| `dom/Span` | 607 | Sealed base of every span: two endpoints, registry entry and exit, the invalidation hooks, the index predicates. Extends `LineElement`. |
| `dom/NoteSpan` | 80 | Sealed base of `Tie` and `Beam`: narrows both endpoints to `NoteElement`. |
| `dom/SpanBound` | 163 | Where one endpoint sits relative to the asking line: `At(index)`, `BEFORE_LINE`, `AFTER_LINE`, `ABSENT`. |
| `dom/SpanOutcome` | 65 | `KEEP`, `REMOVE`, and the hairpin-only `Reshape(range)`. |
| `dom/SpanLookup` | 279 | Interface: every span query, stated over `getSpans`, `anchorIndexOf`, `endIndexOf`. |
| `dom/LineSpans` | 569 | The one implementor of `SpanLookup`: one line's span list, the sweep, the merge, `repoint`. |
| `dom/Beam` | 455 | A beam group: direction, stack count, level membership, stub direction, French shortening. |
| `dom/Hairpin` | 518 | Sealed base of `Crescendo` and `Diminuendo`: endpoint rules, reshape, user shifts, `Kind`. |
| `dom/Crescendo`, `dom/Diminuendo` | 56 each | Each supplies its `Kind`, a copy factory, and two mutation records. Nothing else. |
| `dom/Ending` | 469 | A first/second-ending bracket: split bar, opening element, replacement effects, bracket ranges. |
| `dom/EndingValidationResult` | 95 | The editor's verdict on an ending selection. |
| `dom/Trill` | 140 | A trill over one or more notes: its own Y offset and glyph geometry. |
| `dom/Tuplet` | 430 | A tuplet: grade, ratio, bracket geometry and font, invalidation rule. |
| `dom/TupletRatio` | 45 | `(normalNotes, NotatableValue noteValue)`. |
| `dom/TupletValidator` | 700 | Whether a span can carry a tuplet of grade N, and the ratio it stands for. |
| `dom/TupletLoadPass` | 238 | After a load, completes or drops every tuplet. |

`SlideZone`, which the register named, does not exist. Pass 5 deleted it. Its replacement, `dom/Slide`, is an attribute of `NoteElement` and is not a span.

### Invariants the types do not carry

`Span`

- The two endpoints are `StaffElement`. The narrowing to notes for `Beam` and `Tie` is a runtime check (`acceptsEndpoint`, `Span.java:113`; `requireAccepted`, `:122`) plus a cast in each of `NoteSpan`'s two getters.
- `setAnchorElement` and `setEndElement` are `public` (`Span.java:87`, `:104`). The only callers are `LineSpans.repoint` and `LineSpans.mergeOverlapping`, both in `dom`. Any other code can re-point a span with no mutation recorded.
- Every kind except `Tie` lies in one line. No type says so. `Beam.line()` (`Beam.java:89`) and `Tuplet.outcomeFor` (`Tuplet.java:310`) throw when it is false. `Ending.getSplitIndex(Line)`, `getSplitElement(Line)`, `getOpeningElementIndex(Line)` and `Tuplet.isNumberOnly(Line)`, `hasValidSpan(Line)` take the line as a parameter, so a caller can pass a line the span is not in.
- Invalidation has two mechanisms. `outcomeFor` (`Span.java:342`) is overridden by `Tuplet` and `Hairpin`. Four boolean hooks (`:264`–`:299`), bridged by the default `outcomeFor`, are overridden by `Beam` (replacement), `Ending` (insertion, deletion, replacement) and `Tie` (insertion, replacement). `Trill` overrides neither. The Javadoc at `:257` already tells a new kind to override `outcomeFor` instead.
- `Span` extends `LineElement` and inherits `parentLine`, `parentElement`, `children`, `userXOffsetSs` and `userYOffsetSs`. For a span, `parentLine` and `parentElement` are always `null` and `children` is always empty (the `LineElement` class Javadoc states the first). No production code sets either user offset on a span: the setters' only production callers are `NoteMapper.map`, `SongIO`, `HeaderMapper`, `ReadAnnotation` and `FormatMigrator`, all on attachments or notes. Each kind has its own offset fields instead: `Hairpin` `x1ShiftSs`, `x2ShiftSs`, `yShiftSs` (`double`, `Hairpin.java:79`–`:85`), `Trill.yPositionSs` (`int`, `Trill.java:44`), `Ending.yPositionSs` (`int`, `Ending.java:117`), `Tuplet.verticalPositionSs` (`int`, `Tuplet.java:105`). `VerticalStackingCalculator.applyDecorationOffsets` (`layout/stacking/VerticalStackingCalculator.java:301`–`:307`) adds the inherited offset and the kind's own field together.

`SpanLookup` **/** `LineSpans`

- `SpanLookup` has one implementor, `LineSpans` (`LineSpans.java:52`). Nothing refers to the interface as a type: every caller reaches the queries through `Line.spanRegistry()`, which returns `LineSpans`.
- `LineSpans.getSpans` promises "the order they were added" (`LineSpans.java:113`). See claimed finding 3.

`Hairpin`**,** `Crescendo`**,** `Diminuendo`

- A hairpin's kind is stated twice: by the `Kind` enum (`Hairpin.java:498`) and by the subclass. `Kind.spanType()` (`:509`) maps the enum back to the class. `Crescendo` and `Diminuendo` differ only in the `Kind` they return and the mutation records they build.

**Span mutation records** (`message/mutation`, row 13's package)

- Ten record types record a span entering or leaving a registry: `SpanAddition`, `SpanRemoval`, and an addition and a removal each for beam, tie, tuplet, crescendo and diminuendo. Each is `(Line, <span>)`, and `MutationReplayer.applyUndo`/`applyRedo` replay all ten the same way (`addTo`/`removeFrom`). The one reader that tells them apart is `UndoController.opName` (`undo/UndoController.java:585`–`:588`), for the fallback undo label. `Trill` and `Ending` use the generic pair, whose label key `action.edit.op.span` reads "Ending" (`strings.properties:138`). I found no trill step that reaches the fallback: `TrillAction` runs through the action template, which declares its own name.

`Ending`

- The class invariant — a repeat-closing bar strictly inside the span (`Ending.java:35`) — is kept by every creator calling `hasRepeatSplit` first (`io/LineIO.java:581`, `io/musicxml/MeasureMapper.java:724`) or by the editor's validation. The constructor (`:126`) is `public` and accepts any pair. `getSplitElement` throws when the invariant is broken (`:174`).
- `bracketRanges` (`:118`) is layout output stored on the model. `layout/EndingBracketGeometry` writes it, `ui/renderer/EndingRenderer` reads it.
- `EndingEffect.None` is a record with an `INSTANCE` constant (`:97`). A record's constructor is public, so any caller can make another `None`. `SpanOutcome.Simple` states this exact reason for being an enum (`SpanOutcome.java:39`).

`EndingValidationResult`

- A `boolean valid` with three more fields (`EndingValidationResult.java:51`–`:54`). An invalid result carries `PrecedingAction.NONE`, `0`, `0` (`:47`), readable as though they meant something.
- `PrecedingAction` and `getPrecedingAction` have no reader. The enum's own Javadoc says production code does not branch on it (`:32`–`:38`).

`Tuplet`**,** `TupletRatio`**,** `TupletValidator`**,** `TupletLoadPass`

- `Tuplet.ratio` is `@Nullable` and not `final` (`Tuplet.java:104`). It is `null` only for a tuplet a reader built with `withUnresolvedRatio` (`:138`; callers `io/LineIO.java:413`, `io/musicxml/MeasureMapper.java:1379`), until `TupletLoadPass` calls `resolveRatio` (`:392`) or drops the tuplet. After the load pass no tuplet is unresolved, but five readers still branch on `null`: `NoteBuilder.buildTimeModification` (`io/musicxml/NoteBuilder.java:453`, with a `LOG.warn` placeholder path), `NoteBuilder.performedTicks`, `LineTrackBuilder.getElementDurationWithTuplet`, `PasteSpanReconciliation.isRejectedByTarget`, `TupletLoadPass.settleTuplets`. The comment at `:100` says the ratio is "set once and never change[s] afterwards".
- `grade` is a bare `int` (`:103`). The range 2..7 lives on `TupletValidator` (`MIN_GRADE`, `MAX_GRADE`, `TupletValidator.java:95`, `:98`). The constructor accepts any `int`.
- `TupletRatio.noteValue` is a `NotatableValue`, which is `(ElementType type, Dots dots)` (`dom/NotatableValue.java`). `ElementType` admits bars, rests and key changes, so the one reader that needs the value goes `ratio.noteValue().type().noteValue()` and handles a `null` it cannot get (`NoteBuilder.java:454`). `TupletValidator.buildNotatableByTicks` builds each entry by instantiating a note and casting it (`TupletValidator.java:684`). `NotatableValue` is named by no register row. Its users are `TupletRatio`, `TupletValidator` and `MeasureMapper`.
- `TupletValidator.SpanContext` (`:164`) carries `beatTicks` and `beatDotted`, which together are a `BeatUnit`, and three adjacent booleans.
- `TupletLoadPass.Change.Removed.reason` is `@Nullable` (`TupletLoadPass.java:88`), but `Removed` is built only from a `Result.Invalid` (`:205`–`:207`), whose reason is never `null` (`TupletValidator.java:156`).

`Beam`

- A beam names its members by line index. `noteAt(int)` (`Beam.java:110`), `noteTypeInLevel(int noteIndex, int level)` (`:295`), `stubRight(int)` (`:333`) and `frenchBeamShortening(int)` (`:399`) all take a line index. Every caller loops from `requireAnchorElementIndex()` to `requireEndElementIndex()` and asks per index: `Beam.direction`, `Beam.graceStemTouchesBeamAbove`, `LayoutEngine.calculateBeams`, `LayoutEngine.computeBeamElementGeometry`, `BeamGroupRenderer.drawBeam`, `BeamGroupRenderer.doDrawBeams`, `ScorePartwiseBuilder.beamValues`. See claimed finding 2.
- `noteTypeInLevel(int, int)` has two adjacent `int` parameters. The beam level is a bare `int` in 0..2, with `LEVEL_COUNT` (`:59`) as its bound.

### `@Nullable`

| Site | Answer |
|---|---|
| `Span.getAnchorLine`, `getEndLine` (`Span.java:160`, `:165`) | Honestly partial: an endpoint can sit in no line. |
| `Span.getAnchorElementIndex`, `getEndElementIndex` (`:457`, `:469`), private `indexInLine` (`:383`) | Honestly partial, same reason. |
| `SpanLookup.findFirstSpan`, `findBeamAt`, `findTieAt`, `findExactTie`, `findTieBetween`, `findTupletAt`, `findEndingAt` | Honestly partial lookups. |
| `LineSpans.rangeOf` (`LineSpans.java:157`), private `otherLineOf` (`:250`) | Honestly partial: an endpoint can sit off this line. |
| `Tuplet.ratio`, `getRatio` (`Tuplet.java:104`, `:373`) | Constructible before usable: `null` only between a read and the load pass. |
| `TupletLoadPass.Change.Removed.reason` (`TupletLoadPass.java:88`) | None of the three: no producer of `null` exists. |
| `TupletValidator.OpenSpan.endIndex` (`:440`), `isStructuralBoundary`'s `endIndex` (`:582`) | Exist only for a tuplet whose end sits in no line; see the guards table. |
| `TupletValidator.divisionFailure`, `placementFailure`, `chooseNormalNotes` (private) | Honestly partial: "no failure", "no span". |
| `TupletLoadPass.firstPopulatedLineIndex` (private) | Honestly partial. |
| `Hairpin.projectedEndpointIndexOf`, `resolveBeginIndex`, `resolveEndIndex` (private) | Honestly partial. |
| `Beam.neighborBeamCount`'s parameter (private) | Honestly partial: the line ran out. |

### Guards

| Guard | Site | Who can produce the value |
|---|---|---|
| Endpoint not accepted → `IllegalArgumentException` | `Span.requireAccepted`, `Span.java:122` | Through `repoint`: a replacement of a beam or tie note by a non-note. `Line.setElement` sweeps before it re-points, and `Beam.isInvalidatedByReplacement` removes the beam when the replacement is not a note (`Beam.java:143`). `Tie`'s rule is row 8's and I did not read it. Through `copy`: `Fragment.cloneSpans` copies onto clones of the same elements. No producer found for `Beam`. |
| Endpoint in no line → `IllegalStateException` | `Span.requireIndexInLine`, `:398`; `Beam.line`, `Beam.java:92` | A span still reachable after `Line.removeElement` detached an endpoint. Which of the 19 production files that call `requireAnchorElementIndex` can hold such a span is not established. |
| No split bar → `NoSuchElementException` | `Ending.getSplitElement`, `Ending.java:174` | An `Ending` built without a split. The public constructor admits one; every production creator checks first. |
| Anchor index out of range → returns the argument | `Ending.openingElementIndex`, `Ending.java:238` | Callers pass `requireAnchorElementIndex()` and a selection index (`MusicEditOperations.checkPrecedingElement`). No producer of an out-of-range index found. This is the "returns an arbitrary default" form. |
| Unreachable `invalid()` | `MusicEditOperations.checkPrecedingElement`, final `return` | Its own comment says unreachable today. |
| Endpoints not both in this line → `IllegalArgumentException` | `LineSpans.requireRangeOf`, `LineSpans.java:177` | A line-local span added to a line it is not in. No producer found. |
| Replaying → return | `LineSpans.sweep`, `LineSpans.java:383` | Claimed finding 4. |
| Replaying → skip the displacement policy | `Span.addTo`, `Span.java:191` | `MutationReplayer` re-adds spans in twelve arms. Live; `SpanTest` covers it. |
| Tuplet across lines → `IllegalStateException` | `Tuplet.outcomeFor`, `Tuplet.java:310` | A tuplet whose ends sit in two lines. `MeasureMapper.buildTuplet` refuses one; `LineIO` builds tuplets per line. No producer found. |
| `normalNotes <= 0` → `IllegalArgumentException` | `TupletRatio` constructor | `MeasureMapper.buildTuplet` checks the same condition first and throws "corrupt". `TupletValidator` makes `normalNotes` only from spans of more than one unit. |
| Tuplet end in no line → judge on the anchor alone | `TupletLoadPass.java:200`; `TupletValidator.OpenSpan.closesAt`, `isStructuralBoundary` | A freshly read tuplet whose end sits in no line. Both readers add both endpoints before the pass. No producer found. |
| Zero ink height → `IllegalStateException` | `Tuplet.measureNumberInkHeightSs`, `Tuplet.java:81` | A tuplet font that fails to load. |

`LineSpans.remove` returning when the span is absent (`:338`) is not a guard. It is a stated promise that three kinds of caller rely on (`Span.removeFrom`'s Javadoc).

### Fan-in of public members

Production files that call each member, outside its own file. Test callers are left out.

| Type | Member | Callers |
|---|---|---|
| `Span` | `getAnchorElement`, `getEndElement` | about 13 files each |
| | `setAnchorElement`, `setEndElement` | `LineSpans` only |
| | `isIn` | `ScoreViewController`, `SelectionCoordinator` |
| | `getAnchorLine`, `getEndLine` | `LineSpans`, `Beam`, `Song`, `ScoreView` |
| | `addTo` | 10 files (`MutationReplayer` 12 sites, `MusicEditOperations` 12, `MeasureMapper` 8, `LineIO` 7) |
| | `removeFrom` | 9 files (`MutationReplayer` 12, `LineSpans` 8, `ScoreViewController` 7, `MusicEditOperations` 6) |
| | `requireAnchorElementIndex`, `requireEndElementIndex` | 19 and 14 files |
| | `getAnchorElementIndex`, `getEndElementIndex` | `Tuplet`, `TupletValidator`, `TupletLoadPass`, `ModificationSession`, `ScorePartwiseBuilder`, `MeasureBuilder` |
| | `outcomeFor` | `LineSpans.sweep`, `ElementChange.removesConfirmableSpan` |
| | `requiresInvalidationConfirm` | `ElementChange.removesConfirmableSpan` |
| | `copy` | `LineSpans.applySpanRun`, `Fragment.cloneSpans` |
| | `containing` | `SpanLookup`, `Tie`, `MidiSequenceBuilder`, `MusicEditOperations.hasOverlap`, `LineInvariants` |
| | `overlapping` | `SpanLookup`, `LineSpans`, `MusicEditOperations` |
| | `overlappingBeyondEndpoint`, `strictlyContaining` | one caller each |
| | `exactly` | `SpanLookup`, `RangeQueries`, `HorizontalSpacingCalculator` |
| `SpanLookup` | `findSpans(type)` | 10 files (`FormatMigrator` 6 sites) |
| | `findBeamAt` | 9 files |
| | `findTies`, `findEndings`, `findTupletAt` | 5 files each |
| | `findTieAt` | `MusicEditOperations`, `LineTrackBuilder`, `LineInvariants` |
| | `findExactTie`, `sameTieAt` | `MusicEditOperations`, `RangeQueries` |
| | `findSpans(type, predicate)`, `hasSpan` | 2 files each |
| | `findFirstSpan`, `sameBeamAt`, `findTieBetween`, `findTiesTouching`, `isInsideHairpin`, `hasTrillOverlapping` | 1 file each |
| | `findEndingAt`, `isInsideAnyEnding` | **none** |
| `LineSpans` | `anchorIndexOf`, `endIndexOf` | about 12 files each |
| | `getSpans` | `Song`, `Fragment`, `PasteSpanReconciliation`, `SelectionCoordinator` |
| | `rangeOf` | `Tuplet`, `HairpinEndpoints` |
| | `removeOverlapping` | `Trill`, `Tuplet`, `TrillAction` |
| `Beam` | `noteAt` | `LayoutEngine`, `BeamGroupRenderer` |
| | `direction` | `NoteElement`, `BeamGroupRenderer`, `LayoutEngine` |
| | `noteTypeInLevel` | `BeamGroupRenderer`, `ScorePartwiseBuilder` |
| | `beamStackCount`, `stubRight` | 2 files each |
| | `frenchBeamShortening`, `LEVEL_COUNT` | 1 file each |
| `Hairpin` | `getKind` | `LineSpans`, `PasteSpanReconciliation`, `WedgeTypeMapping`, `HairpinRenderer`, `OpNames` |
| | `Kind` | 11 files |
| | `canAnchorAt`, `canEndAt`, `hasEnoughColumns` | `MusicEditOperations.resolveHairpinAction` |
| | shift getters and setters | `FormatMigrator`, `MeasureMapper`, `LineIO`, `DirectionBuilder`, `VerticalStackingCalculator` |
| | `HAIRPIN_OPENING_HEIGHT_SS` | own file only |
| `Ending` | `getSplitIndex` | `MidiSequenceBuilder`, `MeasureBuilder` |
| | `getSplitElement` | `EndingBracketGeometry`, `EndingConfirms` |
| | `hasRepeatSplit` | `LineIO`, `MeasureMapper` |
| | `EndingEffect` | `ElementChange`, `EndingConfirms`, `PreviewElementInserter`, `SelectionActionApplier` |
| | `checkReplacement` | `ElementChange` |
| | `getBracketRanges` / `setBracketRanges` | `EndingRenderer` / `EndingBracketGeometry` |
| `EndingValidationResult` | whole type | `MusicEditOperations`, `ScoreViewController`, `FirstSecondEndingAction` |
| | `getPrecedingAction` | **none** |
| `Trill` | `glyphLeftXSs` | `HitRegionBuilder`, `TrillRenderer`, `NoteAttachedStacker` |
| | `Trill(anchor)` | `MeasureMapper.resolveTrills` |
| `Tuplet` | `getGrade` | 10 files |
| | `getRatio` | `TupletLoadPass`, `PasteSpanReconciliation`, `NoteBuilder`, `LineTrackBuilder` |
| | `withUnresolvedRatio`, `hasValidSpan` | `LineIO`, `MeasureMapper` |
| | bracket geometry (`isNumberOnly`, `anchorStemUp`, `bracket*`) | `StructuralStacker`, `TupletRenderer`, `HitRegionBuilder` |
| | `isResolved` | **none** |
| `TupletValidator` | `validateFrom` | `TupletLoadPass`, `ModificationSession` |
| | `describeSpan`, `validate`, `validateStated` | `PasteSpanReconciliation`, `RangeQueries`, `TupletLoadPass` |
| | `validateDerived` | `MusicEditOperations` |
| `TupletLoadPass` | `run` | `LoadMigrations` |
| | `Report.empty` | own file only |

### Design docs

- `docs/span-invalidation.md` — the sweep, the three answers, the projection, replay, the registry and the displacement policies.
- `docs/hairpin-editing.md` — the hairpin rules the framework hosts.
- Span content also sits in `docs/clipboard.md` (pasting inside a span, per-kind table), `docs/line-layout.md` (stacking order), `docs/layout-geometry.md` (tuplet bracket arms), `docs/musicxml-object-model.md` (span passes), `docs/mutations.md` and `docs/undo.md`.

### Tests

| Class | Cases | What it covers |
| --- | --- | --- |
| `src/test/java/songscribe/dom/SpanTest.java` | 3   | A removal repeated is a no-op; an addition's displacements are applied once across undo and redo; a deletion and the span it removes undo and redo together. |
| `src/test/java/songscribe/dom/LineSpansTest.java` | 35  | Displacement per kind (17 rows plus a coverage check over the sealed leaves); hairpin joins across a deletion (5 rows, plus 2 single tests); an edit that unbalances a tuplet removes it (2 rows); endpoint placement (6 rows plus a coverage check); a cross-line tie sits in both registries. |

`./scripts/test.sh SpanTest` also runs `MusicXmlCrossLineSpanTest` (6 cases), which is row 16's. `layout/EndingBracketGeometryTest` reads `Ending.isValidEnd` but its subject is layout.

### The claimed carry-forward findings, as the tree has them now

1. `Tuplet.getNoteValue()` no longer exists. The ratio is now a `TupletRatio` whose value is a `NotatableValue(ElementType, Dots)`. The finding stands in a new form: the written value is still an `ElementType`, not a `NoteValue`, and `NoteBuilder.buildTimeModification` still converts through `ElementType.noteValue()`. Item 6 below.
2. `BeamGroupRenderer.beamedNoteAt` no longer exists. `Beam.noteAt(int)` took its place and narrows through `StaffElementRun.noteAt`, which throws `IllegalStateException` for a non-note. A beam still names its members by line index. Item 7 below.
3. `LineSpans.getSpans` **order.** Unchanged. It is a contract question, for step 5.
4. `LineSpans.sweep`**'s replay check.** Unchanged (`LineSpans.java:383`). It is a guard, for step 3.
5. `Ending.hasRepeatSplit(Line, int, int)`**.** Unchanged. It is a signature question, for step 3; the invariant it protects is item 13's.

## Decisions

Every item below is resolved, and each _Correct structure_ is the decision. Where an item changes a file that another row owns, the item names the row, and this pass makes the change there too.

### Question 1 — does each concept have exactly one type?

**1.** `SpanLookup` **and** `LineSpans` **are one concept.** An interface with one implementor, which no code names as a type. The reason its Javadoc gives — that the iterate-filter-resolve loop exists in one place — holds the same in one class. _Correct structure:_ <span id="rd-c1">move the queries into `LineSpans` and delete `SpanLookup`.</span>

**2. A hairpin's kind is stated twice.** The `Kind` enum and the subclass say the same thing, and `Kind.spanType()` exists only to turn one into the other. _Correct structure:_ <span id="rd-c2">`Hairpin` becomes a final class with a `Kind` field set by its constructor</span>. `Crescendo`, `Diminuendo` and `Kind.spanType()` are deleted. A same-kind query filters on `getKind()` instead of on a class; `LineSpans.mergeOverlapping`, which takes a `Class`, takes a same-kind test instead. The callers that name the classes change with it: `HairpinRenderer`, `WedgeTypeMapping`, `LineIO`, `MeasureMapper`, `MusicEditOperations`, `OpNames`, and `Span`'s `permits` clause. The mutation-record part is item 3.

**3. Ten mutation records are one concept.** All ten are `(Line, span)` and replay the same way; they differ only in the fallback undo label. <span id="rd-c3">_Correct structure:_ `SpanAddition` and `SpanRemoval` only</span>. `UndoController.opName` names the step from the span's kind with an exhaustive switch over the sealed hierarchy, so a trill and an ending get labels of their own. `Span.additionRecordFor` and `removalRecordFor` and their overrides are deleted, and `LineSpans.add` and `remove` build the record. `TieAddition` and `TieRemoval` leave the span records with item 15. This reaches `message/mutation` (row 13), `undo` (row 15) and `ChainEdit`'s exhaustive switch (row 6b, complete). New string keys follow the strings guide.

**4. Invalidation has two mechanisms.**<span id="rd-c4">_Correct structure:_ `outcomeFor` is the only hook</span>. The four boolean hooks and `isInvalidatedByChange` are deleted. The rule every kind shares — a deletion that takes an endpoint removes the span — becomes one `protected` query on `Span`, and the default `outcomeFor` is that rule alone, which is all `Trill` needs. `Beam` and `Ending` each state their rule as one switch over the change, the way `Tuplet` does. `Tie`'s rule becomes the attribute rule of item 15.

**5. An unresolved tuplet is a second concept living inside** `Tuplet`**.** It exists only between a read and the load pass, and it costs a nullable ratio in five readers that can never see one. <span id="rd-c5">_Correct structure:_ a reader never builds a `Tuplet`. It builds a `PendingTuplet`</span> — anchor, end, grade, the ratio the file stated if any, the vertical offset — which is not a span and sits in no registry. `TupletLoadPass` is the one place a read tuplet becomes a `Tuplet`, and it validates and adds it or drops it. `Tuplet.ratio` becomes non-null and `final`. `withUnresolvedRatio`, `resolveRatio`, `isResolved` and the unresolved branch of `createCopy` are deleted, and so are the `null` branches in `NoteBuilder` (both), `LineTrackBuilder`, `PasteSpanReconciliation` and `TupletLoadPass`. This reaches `io/LineIO` (row 17), `io/musicxml/MeasureMapper` (row 16) and `io/LoadMigrations`. The pending tuplets travel from each reader to the load pass on the path `accidentalsConverted` already takes. `MeasureMapper.map` returns a record of both to `SongMapper.map`, and `SongIO.DocumentReader` gives them to `SongLoader.load` beside `accidentalsConverted()`. `LoadMigrations.run` takes them. `LineIO.LineReader.pendingTupletData`, an `Object[]` list, becomes the `PendingTuplet` list. Three things change with it:

- `TupletValidator.validateFrom` finds each tuplet through `findTupletAt` on the registry, and `ModificationSession` shares the walk. The walk takes the spans it judges from its caller.
- The walk opens one span at a time. <span id="rd-c21">Nothing checks the pending tuplets for overlap: the writer cannot produce two overlapping tuplets.</span>
- `FormatMigrator.migratePixelsToStaffSpace` reads tuplet offsets from the registry (`FormatMigrator.java:114`). It converts the pending tuplets' offsets instead.

**6.** `NotatableValue` **holds an** `ElementType` **where it means a note value.**<span id="rd-c7">_Correct structure:_ `NotatableValue(NoteValue value, Dots dots)`</span>. `NoteBuilder.buildTimeModification` loses its `null` path; `TupletValidator.buildNotatableByTicks` builds from `NoteValue` with no instance and no cast; `MeasureMapper.statedNoteValue` returns a `NoteValue`. `NotatableValue` joins row 7's _Where_, since its only concern is the tuplet ratio. It is not `BeatUnit`: `BeatUnit` allows one dot, and a ratio's value allows two.

**7. A beam's members have no type; they are line indices.**<span id="rd-c8">_Correct structure:_ `Beam` hands out its notes</span>: its members, the non-grace notes from anchor to end in order, narrowed once; and, as a separate query, the grace notes inside the span. The per-note queries (`noteTypeInLevel`, `stubRight`, `frenchBeamShortening`) take a member rather than a line index. `noteAt(int)` is deleted. The callers listed in the inventory iterate notes. `LayoutEngine` already maps element to column, and `ScorePartwiseBuilder.beamValues` walks runs of notes. The level parameter is item 14's.

- `direction`, `LayoutEngine.calculateBeams` (both loops) and `ScorePartwiseBuilder.applyBeams` iterate the members, and their `isGraceNote()` skips are deleted. `graceStemTouchesBeamAbove` iterates the grace notes.
- A sub-beam run at level L is a run of consecutive members that are all in level L. That gives the runs the index walk gives today, because a grace note is in level L exactly when both its member neighbors are. `noteTypeInLevel`'s grace-note branch is deleted, with its Javadoc at `Beam.java:287`–`:289`.
- A grace note is always strictly inside the span: every creator puts both endpoints on beamable notes (`toggleBeaming` through `nonGraceRange`, `toggleBeamWithPredecessor` by refusing a grace target, the reader because `applyBeams` never writes a `<beam>` on a grace note), and `isInvalidatedByReplacement` removes a beam whose endpoint becomes one. Its nearest member neighbors therefore lie inside the span.

**8.** `Ending` **holds layout output.** `bracketRanges` is computed by layout and read by the renderer, but it is state on the model, stale after any edit until layout runs again. <span id="rd-c10">_Correct structure:_ the bracket ranges go into the layout result beside the ending's decoration layout</span>, and `Ending.bracketRanges`, `getBracketRanges` and `setBracketRanges` are deleted. `Ending.BracketRange` moves to `layout` with them. This reaches `layout` (row 18), `ui/renderer` (row 19) and `shape/EndingBracketShape` (row 28).

**9. A span's user offset is one concept in five places.** The inherited `LineElement` offsets are never set on a span; each kind has its own field, with three names and two types. _Correct_ <span id="rd-c11">_structure:_ a span's vertical offset is `LineElement`'s `userYOffsetSs`</span>. `Trill.yPositionSs`, `Ending.yPositionSs`, `Tuplet.verticalPositionSs` and `Hairpin.yShiftSs` are deleted. `Hairpin` keeps `x1ShiftSs` and `x2ShiftSs`, because its two ends move apart, which one X offset cannot say. The readers, the writers, `FormatMigrator` and `VerticalStackingCalculator` change with it (rows 16, 17, 18). The `int` fields become `double`, the type the spatial-units guide sets for every spatial field.

**10. A span inherits tree state it can never hold.** `parentLine`, `parentElement` and `children` are always `null`, `null` and empty on a span, and `getParentLine()` on a span answers "in no line" while `isIn` answers otherwise. <span id="rd-c12">_Correct structure:_ the tree state moves off `LineElement` onto the part of the hierarchy that is in the tree (`StaffElement` and the children hung on one), and `LineElement` keeps only what every element has: the user offsets</span>. `Span` then inherits nothing it cannot hold. This reaches row 5's `LineElement`, `StaffElement`, `Attachment` and `Articulation` (row 5 is complete). I did not check every caller that asks a `LineElement` for its parent line, so the full set of callers is not established.

**11. Four members are dead.** `SpanLookup.findEndingAt`, `SpanLookup.isInsideAnyEnding`, `Tuplet.isResolved`, and `EndingValidationResult.PrecedingAction` with `getPrecedingAction`. <span id="rd-c13">_Correct structure:_ delete them</span>. `MidiSequenceBuilder.endingCovering`'s Javadoc links `findEndingAt` and is reworded.

**12. The registry does not own its writes to span endpoints.**<span id="rd-c14">_Correct structure:_ `Span.setAnchorElement` and `setEndElement` become package-private</span>, so that only `LineSpans` can re-point a span. Its two callers already are the registry.

**15. A tie is not a span.** A tie joins a note to the next note and never more than two notes, like a glissando, which pass 5 made the note attribute `Slide`. Its end is always the first element after the anchor that is not a legal separator: a creation allows at most one separator between the two notes, and a non-separator insertion removes the tie. _Correct structure:_ a tie is an attribute of its anchor note, "tied to the next note", and the end note is derived. The rule "the next note sounds the same pitch, with only legal separators between" replaces `Tie`'s two invalidation hooks and runs where they run today: on an element change, and on a line insertion or removal, where `Song.removeSpansBetweenNonAdjacentLines` runs today. `isLegalSeparator`, `isAbove` and `arcSign` move with the attribute. With no span across a line, these are deleted: the other-line branch of `LineSpans.appendChild` and `removeChild`, `otherLineOf`, `judgeableSpans`, `Tie.resolveIn` and `Tie.Endpoints`, and `Span`'s `permits Tie`. `TieAddition` and `TieRemoval` become the attribute's mutation records. The MusicXML reader sets the attribute on a note that carries a `<tied>` start and does not pair it with a stop, because the end is derived. The writer never writes a stop that is not on the start's next note, so the reader does not check for one. This reaches `Tie` (row 8), `Song` (row 12), `message/mutation` (row 13), and every tie reader: the renderer, `LineTrackBuilder`, the MusicXML writer and reader, and `MusicEditOperations`. I did not read those readers, so the full set of callers is not established. <span id="rd-c22">This pass makes the change, including the part in `Tie`, which is row 8's file.</span>

**16.** `NoteSpan` **has one subtype.** With item 15, `Beam` is the only span whose endpoints are notes. _Correct structure:_ `NoteSpan` folds into `Beam`, which narrows its own endpoints.

**17.** `SpanBound` **states what a nullable index states.** `BEFORE_LINE` and `AFTER_LINE` exist only for an endpoint in another line, which only a cross-line tie has. With item 15, a bound is `At(index)` or `ABSENT`: the same answer as `Span.getAnchorElementIndex`, a `@Nullable Integer`. `LineSpans.anchorIndexOf` and `endIndexOf` then answer what the span's own index getters answer, because the asking line is always the span's line. _Correct structure:_ `SpanBound` is deleted, and so are `anchorIndexOf` and `endIndexOf`. Every caller reads the span's index getters. The callers are in `SpanLookup`, `LineSpans`, `Span`, `Tie`, `DynamicGrouper`, `PasteSpanReconciliation`, `ElementColumn`, `MusicEditOperations`, `TieRenderer`, `ScorePartwiseBuilder` and `LineInvariants`.

### Question 2 — what varies?

Per kind: the displacement policy (absorb overlapping, absorb adjacent, remove overlapping, coexist), the invalidation rule, whether a removal asks for confirmation (`Ending` only), the endpoint type (notes only, for `Beam`), and the user-adjustable state. With item 15, no span crosses a line. The hierarchy is shaped around the kind, and each variation is a member of it.

**13. What varies, and how it is carried.**

- <span id="rd-c17">_Line-locality has no type_</span>_._ With item 15, every span lies in one line, so no intermediate class is needed. The `Line` parameters on `Ending` and `Tuplet` are removed in step 3 by deriving the line from the anchor, the way `Beam.line()` does, and the two runtime throws stay.
- <span id="rd-c18">_The displacement policy is a value._</span> Each kind returns one constant of a `Displacement` enum — coexist, replace overlapping, absorb overlapping, absorb adjacent — from an abstract method, so `Ending`'s coexistence is stated rather than inherited from an empty method. `Span.addTo` switches on it once, with one same-kind test for every kind; after item 2 that test compares `Hairpin`'s `Kind`. `applyDisplacementPolicy` and its four overrides are deleted, and `LineSpans.Absorb` folds into the enum.
- <span id="rd-c19">_The registry is per line._</span> With item 15, no span sits in two registries.
- `Ending`_'s split invariant has no factory._ The public constructor accepts any pair. <span id="rd-c16">The correct structure is a factory on `Ending` that answers "no ending here" for a span with no split</span>, so no creator can skip the check; the two readers and the editor go through it. Step 3 carries it out.

### Question 3 — which type owns each invariant step 1 found uncarried?

**14.** <span id="rd-c20">**The owners**</span>**.** This is step 3's input.

| Invariant | Owner |
| --- | --- |
| A beam's endpoints are notes | `Beam` (item 16; runtime in `NoteSpan` today) |
| A tie joins a note to the next note of the same pitch | the tie attribute (item 15) |
| An ending has a split bar | `Ending` (item 13) |
| A tuplet's grade is 2..7 | `Tuplet`, through a grade type; `TupletValidator` holds the range today |
| A tuplet's ratio is known | `Tuplet` (item 5) |
| A ratio's value is a note value | `NotatableValue` (item 6) |
| Every element inside a beam is a note | `Beam` (item 7) |
| A beam level is 0..2 | `Beam`, through a level type |
| An ending verdict is valid or invalid, never both | `EndingValidationResult`, as a sealed pair |
| A span is recorded when it is re-pointed | `LineSpans` (item 12) |

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: ok
    by: user
    at: "2026-09-22T21:56:47.607Z"
  rd-c2:
    body: ok
    by: user
    at: "2026-09-22T21:57:18.490Z"
  rd-c3:
    body: ok
    by: user
    at: "2026-09-22T21:57:39.624Z"
  rd-c4:
    body: ok
    by: user
    at: "2026-09-22T21:58:10.374Z"
  rd-c5:
    body: ok
    by: user
    at: "2026-09-22T21:58:45.669Z"
  rd-c6:
    body: i don't know, you tell me
    by: user
    at: "2026-09-22T21:59:13.916Z"
  rd-c7:
    body: ok
    by: user
    at: "2026-09-22T22:24:35.354Z"
  rd-c8:
    body: ok
    by: user
    at: "2026-09-22T22:25:35.732Z"
  rd-c9:
    body: pros and cons?
    by: user
    at: "2026-09-22T22:26:28.144Z"
  rd-c10:
    body: ok
    by: user
    at: "2026-09-22T22:27:10.775Z"
  rd-c11:
    body: ok
    by: user
    at: "2026-09-22T23:43:50.798Z"
  rd-c12:
    body: ok
    by: user
    at: "2026-09-22T23:44:19.781Z"
  rd-c13:
    body: ok
    by: user
    at: "2026-09-22T23:44:28.372Z"
  rd-c14:
    body: ok
    by: user
    at: "2026-09-22T23:44:45.129Z"
  rd-c16:
    body: ok
    by: user
    at: "2026-09-22T23:46:46.003Z"
  rd-c17:
    body: confirmed
    by: user
    at: "2026-09-22T23:47:04.408Z"
  rd-c18:
    body: elaborate
    by: user
    at: "2026-09-22T23:47:17.609Z"
  rd-c19:
    body: What is the alternative?
    by: user
    at: "2026-09-22T23:47:35.579Z"
  rd-c20:
    body: confirmed
    by: user
    at: "2026-09-22T23:48:12.601Z"
