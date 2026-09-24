# Pass 7, Spans — step 3: make illegal states unrepresentable

All paths are under `src/main/java/songscribe/` unless they start with `src/test`. Line numbers are from commit `9c82271c`, the tree step 2 left.

**Yield.** If every item below is accepted, the proposal retires **32 runtime guards, 10 of them** `@Nullable`. It introduces **2 types that carry an invariant the code checks at runtime today** (`TupletGrade`, the sealed `EndingValidationResult`), and **3 types that carry an invariant nothing checks** (`BeamLevel`, `Beam.Member`, the bracket number of item 10). Each item states its own share.

## What it found

### The registry holds no span with a detached endpoint

`Span.getAnchorElementIndex` and `getEndElementIndex` (`dom/Span.java:385`, `:397`) answer `null` when an endpoint sits in no line. Every production caller of the two getters reads a span out of a line's registry: `LineSpans.judgeableSpans`, `isMatch`, `rangeOf`, `ModificationSession.removeTupletsInvalidatedFrom`, `PasteSpanReconciliation.reconcile`, `MidiSequenceBuilder.endingCovering`, `RangeQueries.canToggleTuplet`, `MusicEditOperations.hasOverlap`, `ScorePartwiseBuilder.forEachSpanInRange`, `DynamicGrouper.clusterHairpins` and `MeasureBuilder.attachEndings`.

I found no path that leaves a span in a registry with an endpoint outside that registry's line. The paths I checked:

- **A live deletion.** `Line.removeRange` sweeps before it detaches (`dom/Line.java`, `removeRange`). Every kind removes a span whose endpoint the deletion takes: the default rule (`Trill`), `Beam`, `Ending`, `Tuplet` (any overlap), and `Hairpin`, whose reshape removes the original and adds a copy on surviving elements.
- **A replacement.** `Line.setElement` sweeps, then `LineSpans.repoint` moves every endpoint from the old element to the new one.
- **A merge.** `LineSpans.mergeOverlapping` moves endpoints only to `line.getElement(i)` of its own line.
- **Replay.** Undo replays a step in reverse and steps in reverse order, so a span that points at an element is always removed before the element is. `MutationReplayer.applyUndo` states that out-of-order replay is an engine bug.
- **Line removal.** `Song.removeLine` takes the registry with the line, and the elements keep that line as their parent.
- **Detach.** `Line.detach` is private, and `removeRange` is its only caller that removes elements.

Spans outside every registry do exist: `Fragment.cloneSpans` builds clipboard spans over cloned elements that sit in no line. No caller asks one of them for an index while its elements sit in no line. `PasteSpanReconciliation.isRejectedByTarget` asks only after the paste has inserted the elements.

### Guards

| Guard | Site | Producer | Verdict |
|---|---|---|---|
| Replaying → skip the sweep | `LineSpans.sweep`, `dom/LineSpans.java:257` | `MutationReplayer.applyUndo` of an `ElementDeletion` calls `Line.addElement`, which sweeps an insertion. | **Live.** Claimed finding 4. See below. |
| Replaying → skip displacement | `Span.addTo`, `dom/Span.java:178` | `MutationReplayer` re-adds spans. | Live. |
| Endpoint not accepted → `IllegalArgumentException` | `Span.requireAccepted`, `dom/Span.java:118` | No producer found. See item 22. | Decision. |
| Endpoint in no line → `IllegalStateException` | `Span.requireIndexInLine`, `dom/Span.java:332`; `Beam.line`, `dom/Beam.java:119` | A clipboard span, if a caller asked one. No caller does. | Kept: `getAnchorLine` is honestly partial for a span outside every registry. |
| No split → `NoSuchElementException` | `Ending.getSplitElement`, `dom/Ending.java:122` | An `Ending` built without a split. | Kept; item 5 closes the creators. |
| `normalNotes <= 0` → corrupt document | `MeasureMapper.buildTuplet`, `io/musicxml/MeasureMapper.java:1372` | A file whose `<time-modification>` states a `<normal-type>` without a positive `<normal-notes>`. | **Live.** See the correction below. |
| `normalNotes <= 0` → `IllegalArgumentException` | `TupletRatio` constructor | Only `MeasureMapper`, which checks first; the validator makes counts of at least 2. | Kept: it is the type's invariant, and a closed type cannot hold a positive count. |
| Zero ink height → `IllegalStateException` | `Tuplet.measureNumberInkHeightSs` | A tuplet font that fails to load. | Live. |

**Claimed finding 4, answered.** The replay check in `LineSpans.sweep` is live, and removing it changes behavior. The sweep runs on every replayed element change, because `MutationReplayer` replays through `Line.addElement`, `removeElement` and `setElement`. It finds work whenever a kind's rule is not the inverse of itself. The batch that needs the check: a song holds an ending whose bracket has a bar strictly inside it that is not the split. The user deletes that bar. `Ending.outcomeFor` keeps the ending, because a deletion takes no endpoint, no split and not all content, so the step records only the `ElementDeletion`. Undo re-inserts the bar through `addElement`. Without the check, the sweep judges an insertion of a bar strictly inside the ending, and `insertionAddsInteriorBar` removes the ending during undo. The editor cannot create such an ending (`MusicEditOperations.validateEndingRegionContent` refuses a bar in either bracket), but `LineIO.createEndingsFromPendingPairs` loads one from a legacy file, since it checks only for a split. Item 5 asks whether that should stay so.

**Correction to the record.** The record's _Outside the target → Row 16_ item says `MeasureMapper.buildTuplet`'s `normalNotes <= 0` check cannot fire. It can. `statedNoteValue` returns `null` only when the file states no `<normal-type>`, and it does not read `<normal-notes>`. So the check is the boundary's corrupt-document error, and that item leaves the record.

**Resolved by step 2.** `TupletLoadPass.Change.Removed.reason` is no longer `@Nullable`. `TupletValidator.validateFrom` takes an `ElementLocation`, not two `int`s. `Tuplet.hasValidSpan` and the four replacement hooks no longer exist.

## What needs deciding

### The span registry

**1. An endpoint of a span in a registry always sits in that registry's line.** The _What it found_ section above gives the evidence. <span id="rd-c2">_Correct structure:_ the index queries on a registry span are total</span>.

- `Span.getAnchorElementIndex` and `getEndElementIndex` are deleted. Every caller asks `requireAnchorElementIndex` and `requireEndElementIndex`, which still throw for a span outside every registry.
- `Span.IndexPredicate` tests an `ElementRange`. `ElementRange.contains` and `overlaps` already exist. The `null` branches in `containing`, `overlapping` and `isAt` (`dom/Span.java:416`, `:429`, `:502`) are deleted, and with them the "half-detached span" paragraphs in `overlapping`, `overlappingBeyondEndpoint` and `LineSpans.removeOverlapping`.
- `LineSpans.rangeOf` returns an `ElementRange`, never `null`. `requireRangeOf` (`dom/LineSpans.java:146`) and `judgeableSpans` (`:110`) are deleted. The sweep and `ElementChange.removesConfirmableSpan` ask every span. The `range != null` filter in `mergeOverlapping` (`:390`) is deleted.
- `Tuplet.outcomeFor`'s throw (`dom/Tuplet.java:267`) is deleted.
- Outside `dom`, the null or range checks are deleted in `PasteSpanReconciliation.reconcile` (`ui/clipboard/PasteSpanReconciliation.java:194`), `ScorePartwiseBuilder.forEachSpanInRange` (`io/musicxml/ScorePartwiseBuilder.java:746`), `DynamicGrouper.clusterHairpins` (`layout/stacking/DynamicGrouper.java:155`), `MeasureBuilder.attachEndings` (`io/musicxml/MeasureBuilder.java:634`) and `HairpinEndpoints.compute` (`layout/HairpinEndpoints.java:108`). The comments at those sites that describe a detached or other-line endpoint are deleted with the checks.
- `SpanPredicateTest` loses its `null`-index rows.

Retires 6 `@Nullable` and 13 runtime checks. <span id="rd-c3">_Question:_ do you know of a path that leaves a registered span with an endpoint outside its line?</span> If one exists, this invariant is false. The correct structure is then to remove such a span at the point that detaches its endpoint, and this item changes to that.

**2. The tuplet walk accepts an end it can never receive.** `TupletValidator.validateFrom` handles a tuplet whose end sits in no line, in another line, or before its anchor: `endIndexIn` (`dom/TupletValidator.java:450`), `OpenSpan.endIndex` (`:474`), `closesAt` (`:496`) and `isStructuralBoundary` (`:617`). Its two callers cannot supply one. `ModificationSession` supplies registry tuplets (item 1). `TupletLoadPass` supplies pending tuplets, and both readers guarantee the end: `LineIO` through `DocumentValidation.requireIndexInRange`, which refuses an end before the anchor, and `MeasureMapper` through `endsOnStartLine`. <span id="rd-c4">_Correct structure:_ `Anchored<T>` carries the end's index in the anchor's line as an `int`, and each caller computes it</span>. `endIndexIn` is deleted, `OpenSpan.endIndex` is an `int`, and the `@invariant` clause about an end in no line, another line or before the anchor is deleted. Retires 3 `@Nullable` and 2 runtime checks.

### Parameters a caller can get wrong

<span id="rd-c6">**3. A span's judgement takes a line that its change already names.** </span>`Span.outcomeFor(ElementChange, Line)`, `Hairpin.hairpinOutcomeFor` and `Ending.deletionBreaksBrackets` take a `Line`. Every `ElementChange` carries its own `line()`, and both callers pass that same line. `Ending.checkReplacement(StaffElement, StaffElement, Line)` (`dom/Ending.java:329`) takes two transposable elements and a line, and both of its callers hold an `ElementChange.Replacement`. _Correct structure:_ `outcomeFor(ElementChange)` and `hairpinOutcomeFor(ElementChange)` read `change.line()`. `checkReplacement(ElementChange.Replacement)` and `Tuplet.replacementUnbalances(ElementChange.Replacement)` take the replacement.

<span id="rd-c7">**4. Queries on a span take the line the span is in**</span> (decided at step 2, item 13). `Ending.getSplitIndex(Line)`, `getSplitElement(Line)`, `getOpeningElementIndex(Line)` and `Tuplet.isNumberOnly(Line)` (`dom/Ending.java:107`, `:119`, `:169`; `dom/Tuplet.java:207`) lose the parameter. `Beam.line()` moves to `Span` as a `protected` query, and all three kinds use it. The callers change with it: `MeasureBuilder`, `MidiSequenceBuilder` (two sites), `EndingBracketGeometry` (two), `EndingConfirms`, `HitRegionBuilder`, `StructuralStacker` and `TupletRenderer`.

### Endings

**5. Every ending creator must remember to check for a split** (decided at step 2, item 13: a factory). `Ending`'s constructor (`dom/Ending.java:90`) is `public` and accepts any pair. `LineIO` and `MeasureMapper` call `hasRepeatSplit(Line, int, int)` first (`:136`), and the editor relies on its own validation. <span id="rd-c9">_Correct structure:_ `static @Nullable Ending spanning(Line line, ElementRange span)` answers "no ending here" when the range holds no split</span>. The constructor becomes `private`, and `createCopy` uses it. `hasRepeatSplit` and its two adjacent `int`s are deleted. The two readers and the editor go through the factory. If item 6 is also accepted, the editor's validation builds the `Ending`.

_Question:_ what must the factory check?

- <span id="rd-c8">**A split exists** — today's rule</span>. A legacy ending whose bracket holds a bar other than the split still loads. That ending is the state that makes the sweep's replay check live, and `Ending`'s class Javadoc ("They can span multiple measures") stays true.
- **The editor's full structure** — each bracket holds content and no bar except the split, and `isValidEnd` accepts the split and end pair. That legacy ending is dropped on load with a warning, the same way a split-less one is dropped today. The factory and `MusicEditOperations.validateEndingStructure` then share one rule, and the class Javadoc changes to one measure per bracket.

**6. An ending verdict is a** `boolean` **with two indices that mean nothing when it is** `false`**.** `EndingValidationResult` (`dom/EndingValidationResult.java:28`–`:30`) holds `valid`, `spanStart` and `spanEnd`, and `invalid()` carries `0, 0`. `MusicEditOperations.makeFirstSecondEnding` (`ui/MusicEditOperations.java:1037`) reads the selection again from the coordinator to find the line the indices belong to, and returns early when there is none (`:1040`). <span id="rd-c10">_Correct structure:_ `sealed interface EndingValidationResult`, with `Invalid` as a one-constant enum and `record Valid(Line line, ElementRange span)`.</span> `makeFirstSecondEnding(Valid)` reads the line from the verdict, so its coordinator lookup and early return are deleted. `FirstSecondEndingAction` and `ScoreViewController` match on `Valid`. If item 5 is also accepted, `Valid` carries the `Ending` that the factory built. Retires 1 runtime check.

**7.** `Ending.openingElementIndex` **returns its argument for an index outside the line** (`dom/Ending.java:186`). That is the "arbitrary default" form of a guard. Its callers pass the anchor's own index and the start of a selection, and neither can be out of range. <span id="rd-c11">_Correct structure:_ delete the check</span>. Retires 1.

**8.** `MusicEditOperations.checkPrecedingElement` **tests a type it has already settled.** `openingElementIndex` answers a bar or the anchor itself. `hasEnclosingRepeat` has already refused a selection whose nearest preceding bar is a right repeat, a double barline or a final double barline. So the type test and the final `invalid()` (`ui/MusicEditOperations.java:1030`) cannot fail. <span id="rd-c12">_Correct structure:_ when the opening element is not the anchor, the result is `valid(precedingElementIndex, selectionEnd)`. </span>The type test, the unreachable `invalid()` and its comment are deleted. Retires 2.

**9.** `EndingEffect.None` **is a record with an** `INSTANCE` **constant** (`dom/Ending.java:64`). A record's constructor is public, so any caller can make a second `None`. <span id="rd-c13">_Correct structure:_ `enum None implements EndingEffect { INSTANCE }`. </span>The `case Ending.EndingEffect.None _` patterns in `SelectionActionApplier` and `PreviewElementInserter` still compile.

**10.** `layout/BracketRange.number` **is an** `int` **that is 1 or 2.** It came from `Ending` at step 2 and sits in row 18's package now. <span id="rd-c14">_Correct structure:_ an enum of the two brackets that supplies the label</span>. `hasClosingStroke` states a fact about the bracket, not a mode, and stays a `boolean`.

### Tuplets

**11. A tuplet's grade is an** `int` **whose range 2..7 is checked at runtime, and the range has a second owner.** `Tuplet.grade` (`dom/Tuplet.java:99`), `PendingTuplet.grade`, `Anchored.grade`, `TupletLoadPass.Change.grade`, `validate`, `validateStated` and `numberInkWidthSs` take an `int`. `TupletValidator.divisionFailure` checks the range against `MIN_GRADE` and `MAX_GRADE` (`dom/TupletValidator.java:304`). `ui/action/TupletAction.Tuplet` (`ui/action/TupletAction.java:40`) is an enum of the same six values, and `RangeQueries.validGradesFor` loops the two constants (`ui/selection/RangeQueries.java:460`). <span id="rd-c15">_Correct structure:_ `dom.TupletGrade`, an enum from `DUPLET` to `SEPTUPLET` with `number()`</span>. It replaces `TupletAction.Tuplet`, `MIN_GRADE`, `MAX_GRADE`, `COMPOUND_DUPLET_GRADE` and every `int` grade above. It also reaches `ToggleTupletCommand.getTupletSize`, `MusicEditOperations.toggleTuplet`, `TupletMenuItems.existingGrade`, `RangeQueries.validGradesFor` (a `Set<TupletGrade>`), and the readers of `number()`: `NoteBuilder`, `LineTrackBuilder`, `TupletRenderer`, `HitRegionBuilder` and `MigrationWindow`. `Reason.BAD_RATIO` then means only N equals M. The MusicXML reader converts `<actual-notes>` and treats a number outside 2..7 as a corrupt document, since the writer never writes one. Retires 1 runtime check.

_Question:_ what does the legacy reader do with a grade outside 2..7 (`io/LineIO.java:424`)? Today the load pass drops it, and the migration window lists it as a bad ratio.

- **Corrupt document.** The load fails, as for an index out of range.
- <span id="rd-c16">**Drop with a warning**</span>**.** The reader drops the tuplet and logs it. The migration window no longer lists it, because a `Change` cannot hold a grade that `TupletGrade` cannot express.

**12.** `TupletLoadPass.Change` **holds four adjacent** `int`**s** (`dom/TupletLoadPass.java:87`, `:91`): line, begin, end and grade. <span id="rd-c17">_Correct structure:_ `(int lineIndex, ElementRange span, TupletGrade grade, …)`.</span> The verdict already carries the span as an `ElementRange`. `MigrationWindow.describe` reads it.

**13.** `TupletValidator.SpanContext` **splits a** `BeatUnit` **into two fields** (`dom/TupletValidator.java:159`): `beatTicks` and `beatDotted`. `OpenSpan` repeats the split. `conventionalSpans(int noteValueTicks, int beatTicks, boolean)` (`:631`) then takes two transposable `int`s. <span id="rd-c18">_Correct structure:_ `SpanContext` and `OpenSpan` hold a `BeatUnit`, `conventionalSpans(int, BeatUnit)`, and `chooseNormalNotes` takes the `BeatUnit`.</span>

**14.** `SpanContext` **holds three adjacent** `boolean`**s that map one to one onto** `Reason`**s.** `hasFermata`, `crossesBeatBarrier` and `crossesStructuralBoundary` become `FERMATA`, `BEAT_BARRIER` and `STRUCTURAL_BOUNDARY` in `placementFailure`. Every combination is legal, so no state is illegal. The risk is a transposition at the two constructor calls, `describeSpan` and `OpenSpan.close`. <span id="rd-c19">_Correct structure:_ `SpanContext` holds the placement reasons it found as a `Set<Reason>`.</span> `placementFailure` keeps its order: the fermata under either strictness, then the barrier and the boundary under strict only.

**15.** `placementFailure` **takes a** `boolean` **that its caller derives from** `Strictness` (`dom/TupletValidator.java:207`, `:327`). <span id="rd-c20">_Correct structure:_ it takes the `Strictness`.</span>

**16.** `Tuplet.bracketLeftEdgeXSs` **takes a mode** `boolean` **that all three callers read off the same tuplet** (`dom/Tuplet.java:169`). `TupletRenderer`, `StructuralStacker` and `HitRegionBuilder` each pass `tuplet.anchorStemUp()`. <span id="rd-c21">_Correct structure:_ an instance query that reads `anchorStemUp()` itself</span>. Where this geometry lives is a step 4 question, and the parameter goes whichever class owns it.

### Beams

**17. A beam level is a bare** `int` **in 0..2 that nothing checks.** `Beam.noteTypeInLevel(NoteElement, int)` (`dom/Beam.java:340`) and `LEVEL_COUNT` (`:67`). `ScorePartwiseBuilder.beamValues` indexes an array by level, and `BeamGroupRenderer.doDrawBeams` recurses on `recursionLevel + 1` until `noteTypeInLevel` answers `false`. The step 2 owners table put this on `Beam`. <span id="rd-c22">_Correct structure:_ `BeamLevel`, an enum of the three levels</span>, with the MusicXML beam number and the next level, which is `null` after the last. `LEVEL_COUNT` is deleted. Retires nothing, because nothing checks the range today. The type makes a fourth level unwriteable.

**18.** `stubRight` **and** `frenchBeamShortening` **accept any note and answer silently for a non-member.** Both find the member with `members.indexOf(member)` (`dom/Beam.java:367`, `:433`). A note that is not a member gets position `-1` and a plausible wrong answer. Every caller passes a member today. <span id="rd-c23">_Correct structure:_ `members()` returns `List<Beam.Member>`. `Member` is a final nested class with a private constructor, which holds the note and its position</span>. `stubRight`, `frenchShortening` and the level test are queries on the member. The note that is not a member cannot be passed, and the `indexOf` scans are deleted.

### Smaller types

**19.** `Displacement.reach` **is** `@Nullable`**, and** `absorbReach` **throws for the two constants that absorb nothing** (`dom/Displacement.java:57`, `:74`). Its one caller, `Span.addTo`, calls it only in the absorbing arm. <span id="rd-c24">_Correct structure:_ `Displacement` holds no reach.</span> `Span.addTo`'s switch passes `0` in the `ABSORB_OVERLAPPING` arm and `LineSpans.SPAN_ADJACENCY_REACH` in the `ABSORB_ADJACENT` arm. Retires 1 `@Nullable` and 1 throw.

**20.** `PasteSpanReconciliation.reconcile` **keys hairpins by class.** `keptStraddledHairpinTypes` is a `Set<Class<?>>` (`ui/clipboard/PasteSpanReconciliation.java:220`). Since step 2 made `Hairpin` one final class, every hairpin has the same class. The behavior is the same in every case I traced, because a destination hairpin is kept only when the fragment holds no hairpin of the other kind. <span id="rd-c25">_Correct structure:_ an `EnumSet<Hairpin.Kind>`, filtered by `getKind()`.</span> The `default -> { }` arm of the switch over the sealed `Span` (`:224`) is deleted, so a new kind fails to compile there.

**21.** `PasteSpanReconciliation.dropTupletsRejectedByTarget` **returns early while replaying** (`ui/clipboard/PasteSpanReconciliation.java:280`). Its one caller is `ScoreViewController.tryInsertFragment`, a user paste. The replay depth rises only inside `UndoController.performUndo` and `performRedo`, which replay mutations and never paste. <span id="rd-c26">_Correct structure:_ delete the check and its comment</span>. Retires 1.

**22.** `Span.requireAccepted` **guards the one endpoint narrowing, and no producer was found** (`dom/Span.java:118`). `repoint` runs after the sweep has removed a beam whose note became a non-note. A merge moves a beam's endpoints only onto other beams' endpoints. `Fragment.cloneSpans` maps notes to their clones. The guard is what makes `Beam`'s two casts safe.

- **Keep it.** `Beam` is the one narrowing, and the check sits at every write.
- <span id="rd-c27">**A type parameter,** `Span<E extends StaffElement>`</span>**.** `Beam` extends `Span<NoteElement>`, so the constructor, the getters and `createCopy` need no cast, and `acceptsEndpoint` and `requireAccepted` are deleted. `LineSpans.repoint` and `Fragment.cloneSpans` hold elements as `StaffElement`, so each of them needs a checked narrowing for a beam. The runtime check moves to those two sites and does not go away.

_Question:_ which one?

### Outside the target

**23.** `BeamGroupRenderer.doDrawBeams` **takes fifteen parameters** (`ui/renderer/BeamGroupRenderer.java:192`, row 19). They include five adjacent `int`s, two `boolean`s, and a `java.awt.Point outerNotes` whose `x` and `y` are the first and last member positions. <span id="rd-c28">_Correct structure:_ one range type over member positions in place of each pair: the outer notes, the current run and the previous run. The level is a `BeamLevel` (item 17). If item 18 is accepted, the positions index `List<Beam.Member>`.</span>

**24.** `ElementColumn.beamGroupId` **is a beam's anchor index used as the beam's identity** (`layout/ElementColumnBuilder.java:219`, row 18). Its one reader is `ElementColumn.sharesBeamGroupWith`, which compares for equality. <span id="rd-c29">_Correct structure:_ the column holds its `@Nullable Beam` and compares by identity</span>. The index stands in for the identity only because no two beams in a line share an anchor.

---
roughdraft: "1.0"
comments:
  rd-c2:
    body: agreed
    by: user
    at: "2026-09-23T23:28:22.160Z"
  rd-c3:
    body: no
    by: user
    at: "2026-09-23T23:28:40.249Z"
  rd-c4:
    body: agreed
    by: user
    at: "2026-09-23T23:29:18.889Z"
  rd-c6:
    body: agree to fix
    by: user
    at: "2026-09-23T23:30:12.457Z"
  rd-c7:
    body: agree to fix
    by: user
    at: "2026-09-23T23:30:58.463Z"
  rd-c8:
    body: this
    by: user
    at: "2026-09-23T23:33:47.473Z"
  rd-c9:
    body: agreed
    by: user
    at: "2026-09-23T23:34:27.314Z"
  rd-c10:
    body: agreed
    by: user
    at: "2026-09-23T23:35:16.576Z"
  rd-c11:
    body: agreed
    by: user
    at: "2026-09-23T23:39:11.713Z"
  rd-c12:
    body: agreed
    by: user
    at: "2026-09-23T23:40:04.801Z"
  rd-c13:
    body: agreed
    by: user
    at: "2026-09-23T23:40:39.317Z"
  rd-c14:
    body: agreed
    by: user
    at: "2026-09-23T23:41:43.472Z"
  rd-c15:
    body: agreed
    by: user
    at: "2026-09-23T23:42:36.650Z"
  rd-c16:
    body: this
    by: user
    at: "2026-09-23T23:42:56.585Z"
  rd-c17:
    body: agreed
    by: user
    at: "2026-09-23T23:43:19.478Z"
  rd-c18:
    body: agreed
    by: user
    at: "2026-09-23T23:43:53.690Z"
  rd-c19:
    body: agreed
    by: user
    at: "2026-09-23T23:44:32.649Z"
  rd-c20:
    body: agreed
    by: user
    at: "2026-09-23T23:44:50.724Z"
  rd-c21:
    body: agreed
    by: user
    at: "2026-09-23T23:45:30.937Z"
  rd-c22:
    body: agreed
    by: user
    at: "2026-09-23T23:48:06.456Z"
  rd-c23:
    body: agreed
    by: user
    at: "2026-09-23T23:48:52.803Z"
  rd-c24:
    body: agreed
    by: user
    at: "2026-09-23T23:49:41.887Z"
  rd-c25:
    body: agreed
    by: user
    at: "2026-09-23T23:50:14.165Z"
  rd-c26:
    body: agreed
    by: user
    at: "2026-09-23T23:50:47.662Z"
  rd-c27:
    body: this
    by: user
    at: "2026-09-23T23:54:52.408Z"
  rd-c28:
    body: agreed
    by: user
    at: "2026-09-23T23:55:24.899Z"
  rd-c29:
    body: agreed
    by: user
    at: "2026-09-23T23:55:43.559Z"
  rd-c30:
    body: The span-related issues most likely arise out of ties being moved out spans, because ties were the only span that could be cross-line.
    by: user
    at: "2026-09-23T23:55:46.869Z"
    scope: document
