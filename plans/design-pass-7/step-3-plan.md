# Design Pass 7, Step 3 — Make Illegal States Unrepresentable

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Span Framework](#-phase-1-span-framework) | ✅ Complete | — |
| 2 | [Beam Members and Levels](#-phase-2-beam-members-and-levels) | ✅ Complete | — |
| 3 | [Tuplet Grade and the Validator Walk](#-phase-3-tuplet-grade-and-the-validator-walk) | ✅ Complete | — |
| 4 | [Ending Construction and Verdict](#-phase-4-ending-construction-and-verdict) | ✅ Complete | — |
| 5 | [Span Kinds and dom Callers](#-phase-5-span-kinds-and-dom-callers) | ✅ Complete | — |
| 6 | [io Callers](#-phase-6-io-callers) | ✅ Complete | — |
| 7 | [layout, midi, message and undo Callers](#-phase-7-layout-midi-message-and-undo-callers) | ✅ Complete | — |
| 8 | [ui Callers](#-phase-8-ui-callers) | ✅ Complete | — |
| 9 | [Test Tree](#-phase-9-test-tree) | ✅ Complete | — |
| 10 | [Compile and Test Gate](#-phase-10-compile-and-test-gate) | ✅ Complete | — |
| 11 | [Manual UI Verification](#-phase-11-manual-ui-verification) | ✅ Complete | — |

## Rules for every phase

Every phase starts by reading this section and follows it throughout.

1. The working directory is
   `/Users/aparajita/Developer/projects/SongScribe-worktrees/design-pass-7`. Every path in
   this plan is relative to it; Java paths are under `src/main/java/songscribe/` unless they
   start with `src/`, `docs/` or `plans/`.
2. Read `.claude/rules/serena.md` and follow it: serena's `jet_brains_*` tools for all Java
   exploration, `jet_brains_find_referencing_symbols` before deciding where a symbol is used,
   and `Edit` for every code change. Search only inside `src/` and `docs/`. If a symbol cannot
   be found in the places named, report it and stop searching; do not widen the search.
3. Read `.claude/rules/java.md` before writing Java. Every number in logic is a named constant
   (0, 1, -1 and multiplication or division by 2 excepted). Comments explain why and describe
   the code as it is; never write what changed, what was removed, or what an earlier design
   did.
4. Before writing or changing a Javadoc contract, read `.claude/guides/contracts.md`. Before
   adding or removing a `@Nullable`, read `.claude/guides/null-handling.md`. Before adding or
   removing a user-facing string, read `.claude/guides/strings.md`.
5. Line numbers in this plan are from commit `9c82271c` and drift as phases land. Locate every
   member by symbol, never by line.
6. Do not run `./scripts/compile.sh` or `./scripts/test.sh`. The tree compiles only when every
   implementation phase has landed; Phase 10 is the one compile.
7. Do not edit anything under `src/test/`. Phase 9 owns the test tree.
8. Never stage, commit, stash or amend.
9. `Span` becomes generic in Phase 1. In every file a phase writes, a type use of `Span` with no
   type argument becomes `Span<?>` (`List<Span>` → `List<Span<?>>`, `Class<? extends Span>` →
   `Class<? extends Span<?>>` where the compiler requires it). A `{@link Span}` in Javadoc stays
   as it is.
10. If a symbol a task names does not exist, or a caller set differs from the one the task
    states, report it in your final message and do the rest of the phase. Do not widen the
    change to cover it.

---

## ✅ Phase 1: Span Framework

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/Displacement.java, src/main/java/songscribe/ui/clipboard/Fragment.java  <br>
**Recommended model/effort:** Opus, high — the generic type parameter and its narrowing are a type-system design with capture at three call sites; the total index predicates change a public API every span query rests on.

### Tasks

1. **The endpoint type is a type parameter.** Rewrite `Span`'s declaration as
   `public abstract sealed class Span<E extends StaffElement> extends LineElement implements LineContent permits Beam, Hairpin, Tuplet, Trill, Ending`.
   - The constructor is `protected Span(Class<E> endpointType, E anchorElement, E endElement)`
     and stores `endpointType` in a `private final Class<E>` field.
   - `anchorElement` and `endElement` are `E`. `getAnchorElement()` and `getEndElement()` return
     `E` and are `public final`. `setAnchorElement(E)` and `setEndElement(E)` stay
     package-private and assign without a check.
   - `copy(E newAnchor, E newEnd)` returns `Span<E>` and `createCopy(E, E)` is
     `protected abstract Span<E> createCopy(E newAnchor, E newEnd)`.
   - Delete `acceptsEndpoint` and `requireAccepted`. Rewrite the class Javadoc's
     *Endpoint typing* paragraph: a kind states its endpoint type as the type argument and
     passes its class to the constructor; constructor, getters and `copy` are typed, and the
     three places a bare `StaffElement` becomes an endpoint — `LineSpans.repoint`,
     `LineSpans.mergeOverlapping` and `Fragment.cloneSpans` — narrow it through
     `narrowEndpoint`. `Beam` (`Span<NoteElement>`) is the one kind whose endpoint type is
     narrower than `StaffElement`.
   - Add `public final E narrowEndpoint(StaffElement element)`. Write its contract first: it
     returns `element` as this kind's endpoint type; `@throws IllegalArgumentException` when
     `element` is not an instance of it, naming the kind and the element. Implement it with
     `endpointType.isInstance` and `endpointType.cast`.
   - `isSameKindAs(Span<?> other)`, `IndexPredicate` and every static factory take `Span<?>`
     where they take a span.
2. **The index queries of a registry span are total.** An endpoint of a span in a line's
   registry always sits in that line: a live deletion sweeps first and every kind removes a
   span whose endpoint it takes, a replacement re-points, a merge moves endpoints only within
   the line, and replay undoes in reverse order.
   - Delete `getAnchorElementIndex()` and `getEndElementIndex()`. Keep
     `requireAnchorElementIndex()`, `requireEndElementIndex()`, `requireIndexInLine` and
     `indexInLine`: a span outside every registry (a clipboard span over cloned elements) still
     has an endpoint in no line. Rewrite their Javadoc so it names that case and no longer
     points at the deleted getters.
   - `IndexPredicate` becomes `boolean test(ElementRange spanRange)`, the span's inclusive range
     from anchor to end. Rewrite the factories over `ElementRange` (`StaffElementRun.ElementRange`,
     which has `contains(int)` and `overlaps(ElementRange)`): `containing(i)` → `r.contains(i)`;
     `overlapping(range)` → the span's anchor at or before `range.end()` and its end at or after
     `range.begin()` (use `r.overlaps(range)` only after reading its contract and confirming it
     is that inclusive test); `overlappingBeyondEndpoint(range)` → overlapping, and
     `r.end() != range.begin()`, and `r.begin() != range.end()`; `strictlyContaining(i)` →
     `r.contains(i)` and `i` is neither `r.begin()` nor `r.end()`; `exactly(range)` →
     `r.equals(range)`; `anchoredAt(i)` → `r.begin() == i`; `endingAt(i)` → `r.end() == i`.
     Delete `isAt`. Delete every sentence in these factories' Javadoc about a `null` endpoint or
     a half-detached span.
3. **`Span.line()`.** Add `protected final Line line()`: the anchor's line; `@throws
   IllegalStateException` when the anchor sits in no line (a span outside every registry). Its
   body is today's private `Beam.line()` (`dom/Beam.java:119`); Phase 2 deletes that one.
4. **A judgement reads its line from the change.** `outcomeFor(ElementChange change, Line line)`
   becomes `public SpanOutcome.Simple outcomeFor(ElementChange change)`. Every `ElementChange`
   carries `line()`. Rewrite its Javadoc: delete the `@param line` and the sentences that
   describe `line` being passed; state that `change.line()` is the line being changed, still in
   its pre-change state. Delete the paragraph that says the questions are put only to
   `judgeableSpans`.
5. **`Displacement` holds no reach.** In `dom/Displacement.java` delete the `reach` field, the
   constructor and `absorbReach()`; the constants take no arguments. In `Span.addTo`, the switch
   passes `0` in the `ABSORB_OVERLAPPING` arm and `LineSpans.SPAN_ADJACENCY_REACH` in the
   `ABSORB_ADJACENT` arm, as two separate arms.
6. **`LineSpans` over total ranges.** In `dom/LineSpans.java`:
   - `rangeOf(Span<?> span)` returns `ElementRange`, never `null`:
     `new ElementRange(span.requireAnchorElementIndex(), span.requireEndElementIndex())`.
     Rewrite its Javadoc (`@throws IllegalStateException` when an endpoint sits in no line).
   - Delete `requireRangeOf` and `judgeableSpans`. Callers of `requireRangeOf` call `rangeOf`.
   - `isMatch` tests `matches.test(rangeOf(span))`.
   - `sweep` iterates `spans` directly (it only reads while it collects `doomed` and
     `reshapedByKind`; removal happens after the loop), calls `span.outcomeFor(change)` and
     `hairpin.hairpinOutcomeFor(change)` (Phase 5 changes `Hairpin`'s signature to
     `hairpinOutcomeFor(ElementChange)`).
   - `mergeOverlapping` becomes `<E extends StaffElement> void mergeOverlapping(Span<E> span, int reach)`.
     Delete the `range != null` filter and its comment block; every same-kind span has a range.
     The two setter calls become
     `span.setAnchorElement(span.narrowEndpoint(line.getElement(mergedAnchorIdx)))` and the same
     for the end.
   - `repoint` narrows: for each span, call a private generic helper
     `<E extends StaffElement> void repointEndpoints(Span<E> span, StaffElement oldElement, StaffElement newElement)`
     that sets `span.narrowEndpoint(newElement)` on each endpoint that is `oldElement`.
   - `removeOverlappingSameKind(Span<?> span)` uses `rangeOf(span)`.
   - Rewrite the class Javadoc's last paragraph and the `spans` field comment: every endpoint of
     a span in this registry sits in this line. Delete the sentences about a span with a
     detached endpoint.
7. **`Fragment.cloneSpans` narrows.** In `ui/clipboard/Fragment.java`, `cloneSpans` copies each
   span through a private generic helper
   `<E extends StaffElement> Span<E> cloneOnto(Span<E> span, <the clone map>)` that calls
   `span.copy(span.narrowEndpoint(<clone of anchor>), span.narrowEndpoint(<clone of end>))`.
   Every `Span` type use in the file becomes `Span<?>`.

---

## ✅ Phase 2: Beam Members and Levels

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/BeamLevel.java, src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java  <br>
**Recommended model/effort:** Opus, high — two new types that carry invariants, and the recursive renderer's signature is redesigned around them.

### Tasks

1. **`Beam` is a `Span<NoteElement>`.** `public final class Beam extends Span<NoteElement>`; the
   constructor calls `super(NoteElement.class, anchorElement, endElement)`. Delete the two
   covariant getter overrides, `acceptsEndpoint`, and the private `line()` (`Span.line()` from
   Phase 1 replaces it). `createCopy(NoteElement, NoteElement)` returns `new Beam(...)` with no
   cast. `outcomeFor(ElementChange change)` takes the change only. Rewrite the class Javadoc
   paragraph on endpoint narrowing: the type argument states it.
2. **`BeamLevel`.** Create `dom/BeamLevel.java`: `public enum BeamLevel` with `EIGHTH`,
   `SIXTEENTH`, `THIRTY_SECOND`, each holding its MusicXML beam number (1, 2, 3) as a named
   field. Write its contract first, then implement:
   - `int number()` — the MusicXML `<beam number>`, 1 for the primary beam;
   - `boolean isCarriedBy(ElementType type)` — `type.beamCount() >= number()`;
   - `@Nullable BeamLevel next()` — the next inner level, `null` after `THIRTY_SECOND`.
   Delete `Beam.LEVEL_COUNT`; its readers use `BeamLevel.values()`.
3. **`Beam.Member`.** A member is a note this beam joins, together with its place among the
   members. Write the contracts first, then implement:
   - `public static final class Member` nested in `Beam`, with a private constructor, holding
     the note, its position among the members, and the members' notes in order.
   - `members()` returns `List<Member>`, built once per call in line order, unmodifiable, never
     empty. `graceNotes()` stays `List<NoteElement>`.
   - On `Member`: `NoteElement note()`; `boolean carries(BeamLevel level)` (today's
     `noteTypeInLevel`); `boolean stubRight()`; `int frenchShortening()` (today's
     `frenchBeamShortening`). Neighbours are read by position, so the `members.indexOf(member)`
     scans are deleted. In `stubRight`, the loop over levels past the primary one iterates
     `BeamLevel` values from `SIXTEENTH` rather than an `int` from 2.
   - Delete `noteTypeInLevel`, `stubRight(NoteElement)` and `frenchBeamShortening(NoteElement)`
     from `Beam`. `direction()`, `beamStackCount()` and `graceStemTouchesBeamAbove` read
     `member.note()`.
   - Move the Javadoc of the three deleted methods onto the member's queries, adjusted to the
     member as receiver.
4. **`BeamGroupRenderer.doDrawBeams`.** Replace its fifteen parameters' position pairs with one
   private record `MemberRun(int first, int last)` nested in `BeamGroupRenderer`: the outer notes
   (today a `java.awt.Point outerNotes`), the run being drawn (`beginPosition`,
   `endPosition`) and the previous run (`prevBeginPosition`, `prevEndPosition`). The level is a
   `BeamLevel` in place of `int recursionLevel`; the recursion passes `level.next()` and does not
   recurse when it is `null`. `members` is `List<Beam.Member>`; `member.carries(level)` replaces
   `beam.noteTypeInLevel(...)`, and `member.note()` is passed where a note is needed.
   `drawBeam` takes the `BeamLevel` and reads `number()` where it needs arithmetic on the level.
   Update `renderBeams`, the one caller, to build the first call.

---

## ✅ Phase 3: Tuplet Grade and the Validator Walk

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/TupletGrade.java, src/main/java/songscribe/dom/TupletValidator.java  <br>
**Recommended model/effort:** Opus, high — a new domain type replaces the grade arithmetic in the validator, and the walk's end handling is restated.

### Tasks

1. **`TupletGrade`.** Create `dom/TupletGrade.java`: `public enum TupletGrade` with `DUPLET`,
   `TRIPLET`, `QUADRUPLET`, `QUINTUPLET`, `SEXTUPLET`, `SEPTUPLET`, each holding its printed
   number 2..7 as a field. The constant names are what `TupletAction` turns into menu names, so
   they are exactly these. Write the contract first, then implement:
   - `int number()` — the printed tuplet number;
   - `static @Nullable TupletGrade of(int number)` — the grade printing `number`, `null` for a
     number outside 2..7. The two file readers are its only callers.
2. **The validator takes a `TupletGrade`.** In `dom/TupletValidator.java`:
   - Delete `MIN_GRADE`, `MAX_GRADE` and `COMPOUND_DUPLET_GRADE`. `chooseNormalNotes` tests
     `grade == TupletGrade.DUPLET`.
   - `validate(SpanContext, TupletGrade, Strictness)`,
     `validateDerived(Line, ElementRange, TupletGrade, Strictness)`,
     `validateStated(SpanContext, TupletGrade, TupletRatio, Strictness)`. Arithmetic reads
     `grade.number()`.
   - `divisionFailure(int writtenTicks, TupletGrade grade)` loses the range check. The `Reason`
     comment on `BAD_RATIO` becomes "N == M"; `validateStated` is its one producer.
   - `record Anchored<T>(T tuplet, int endIndex, TupletGrade grade)`: `endIndex` is the index of
     the tuplet's last element in the anchor's line, at or after the anchor.
3. **`SpanContext` holds a `BeatUnit` and a set of reasons.** `record SpanContext(int writtenTicks, BeatUnit beat, Set<Reason> placementReasons)`,
   with a compact constructor that stores `Set.copyOf(placementReasons)`. `placementReasons`
   holds any of `FERMATA`, `BEAT_BARRIER`, `STRUCTURAL_BOUNDARY` found in the span.
   - `describeSpan` builds the set from `hasFermata`, `crossesBeatBarrier` and
     `crossesStructuralBoundary`.
   - `placementFailure(SpanContext context, Strictness strictness)`: `FERMATA` when present,
     then `BEAT_BARRIER`, then `STRUCTURAL_BOUNDARY`, the last two under `Strictness.STRICT`
     only — the order today's method has. Delete the `var strict` local in `validate`.
   - `conventionalSpans(int noteValueTicks, BeatUnit beat)` and
     `chooseNormalNotes(NavigableSet<Integer> spans, TupletGrade grade, BeatUnit beat)` read
     `beat.ticks()` and `beat.dotted()`.
4. **The walk's end is an index.** In `validateFrom` and `OpenSpan`:
   - Delete `endIndexIn`. `OpenSpan` takes its end from `anchored.endIndex()` as an `int`
     field, holds the `BeatUnit` it opened against, and accumulates an `EnumSet<Reason>` in
     place of its three `boolean`s; `close` builds the `SpanContext` from them.
   - `closesAt(index)` is `index == endIndex`.
   - `isStructuralBoundary(ElementType type, int index, int endIndex)`:
     `index != endIndex && (type.isBar() || type.isBreathMark())`.
   - Rewrite `validateFrom`'s Javadoc: `tuplets` supplies each tuplet's end index in the
     anchor's line, at or after the anchor. Replace the `@invariant` clause: a verdict's span
     runs from the element the tuplet was supplied at to its `endIndex`. Delete the text about an
     end in no line, in another line or before the anchor.

---

## ✅ Phase 4: Ending Construction and Verdict

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/EndingValidationResult.java, src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Opus, high — a factory that becomes the one way to build an ending, and the editor's verdict type redesigned around it.

### Tasks

1. **The factory.** In `dom/Ending.java` (`Ending` now `extends Span<StaffElement>`):
   - Write the contract first: `public static @Nullable Ending spanning(Line line, ElementRange span)`
     returns an ending anchored on `span.begin()` and ending on `span.end()` of `line`, or
     `null` when no bar strictly inside `span` closes a repeat. It is the one way to build an
     ending outside this class.
   - Implement it over today's `closingRepeatsBetween`, which takes `(Line, ElementRange)`.
   - The constructor becomes
     `private Ending(StaffElement anchorElement, StaffElement endElement)` and calls
     `super(StaffElement.class, anchorElement, endElement)`. `createCopy` uses it.
   - Delete `hasRepeatSplit`. Rewrite the class `@invariant` sentence about creators: every
     creator outside the class goes through `spanning`.
2. **The verdict is a sealed pair.** Rewrite `dom/EndingValidationResult.java` as
   `public sealed interface EndingValidationResult permits EndingValidationResult.Invalid, EndingValidationResult.Valid`:
   - `enum Invalid implements EndingValidationResult { INSTANCE }` — no ending can be made from
     the selection;
   - `record Valid(Line line, Ending ending) implements EndingValidationResult` — `ending` is
     built over `line`'s elements and not yet added.
   Write the Javadoc of each as its contract. Delete the class, its fields and its factories.
3. **The editor builds through the factory.** In `ui/MusicEditOperations.java`:
   - `canMakeFirstSecondEnding()` returns `EndingValidationResult.Invalid.INSTANCE` at each
     stage that fails today. After stage 3, compute the anchor index as
     `Ending.openingElementIndex(line, begin)`, then
     `var ending = Ending.spanning(line, new ElementRange(anchorIndex, end))`, and return
     `Invalid.INSTANCE` when it is `null`, otherwise `new Valid(line, ending)`.
   - Delete `checkPrecedingElement`. Its type test and final `invalid()` cannot fail:
     `openingElementIndex` answers a bar or the anchor, and `hasEnclosingRepeat` has already
     refused a selection whose nearest preceding bar is a right repeat, a double barline or a
     final double barline.
   - `makeFirstSecondEnding(EndingValidationResult.Valid valid)` adds `valid.ending()` to
     `valid.line()` inside `withModification`. Delete its coordinator lookup and its early
     return.
   - `hasOverlap(Line line, int begin, int end)` tests each ending's
     `line.spanRegistry().rangeOf(ending)` against `Span.containing(i)` through
     `IndexPredicate.test(ElementRange)` (Phase 1's signature).

---

## ✅ Phase 5: Span Kinds and dom Callers

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4  <br>
**Files:** src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Trill.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/ElementChange.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/LineContent.java, src/main/java/songscribe/dom/Attachment.java, src/main/java/songscribe/dom/SpanOutcome.java, src/main/java/songscribe/dom/PendingTuplet.java, src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/dom/ModificationSession.java, docs/span-invalidation.md  <br>
**Recommended model/effort:** Sonnet, medium — every shape is decided by Phases 1–4; this phase applies it across the dom kinds and callers.

### Tasks

1. **`Hairpin`, `Trill`, `Tuplet` are `Span<StaffElement>`.** Each declaration names the type
   argument, each constructor passes `StaffElement.class` as the first `super` argument, and
   `createCopy` returns `Span<StaffElement>`.
2. **Judgements take the change only.** `Hairpin.outcomeFor(ElementChange)` and
   `hairpinOutcomeFor(ElementChange)`, `Tuplet.outcomeFor(ElementChange)` and
   `Ending.outcomeFor(ElementChange)` read `change.line()` where they read `line`. Delete each
   `@param line`.
   - `Tuplet.outcomeFor` reads `change.line().spanRegistry().rangeOf(this)`, which is never
     `null`; delete the `null` check, its throw and the `@throws` clause.
   - `Tuplet.replacementUnbalances(ElementChange.Replacement replacement)` takes the
     replacement.
   - `Ending.checkReplacement(ElementChange.Replacement replacement)` takes the replacement and
     reads `oldElement()`, `newElement()` from it. `Ending.deletionBreaksBrackets(List<StaffElement>)`
     loses its `Line` and reads `line()`.
3. **Queries read the span's own line.** `Ending.getSplitIndex()`, `getSplitElement()`,
   `getOpeningElementIndex()` and `Tuplet.isNumberOnly()` lose their `Line` parameter and read
   `line()` (Phase 1's `protected final Line line()` on `Span`). Delete their `@param line`.
4. **`Ending` odds.** `EndingEffect.None` becomes `enum None implements EndingEffect { INSTANCE }`.
   Delete `openingElementIndex`'s `if (!line.hasIndex(anchorIndex)) return anchorIndex;` and
   the `@return` wording about that case; its callers pass the anchor's index and a selection
   start, which are always in range.
5. **`Tuplet` grade and bracket edge.** `grade` is a `TupletGrade` (`dom/TupletGrade.java`,
   Phase 3): the field, the constructor parameter and `getGrade()`.
   `numberInkWidthSs(TupletGrade grade)` measures `String.valueOf(grade.number())`.
   `bracketLeftEdgeXSs` becomes an instance method
   `public double bracketLeftEdgeXSs(double anchorXSs, double stemSs, double noteheadWidthSs)`
   that reads `anchorStemUp()`; delete its `@param anchorStemUp`.
6. **`ElementChange`.** `removesConfirmableSpan` streams `line.spanRegistry().getSpans()` and
   calls `span.outcomeFor(this)`. `Replacement.endingEffect()` calls
   `ending.checkReplacement(this)`.
7. **The load pass.** `PendingTuplet.grade` is a `TupletGrade`.
   `TupletLoadPass.Change` and its two records hold `(int lineIndex, ElementRange span, TupletGrade grade, …)`:
   the interface's `beginIndex()` and `endIndex()` become `ElementRange span()`, and
   `grade()` returns `TupletGrade`. `place` builds them from `verdict.span()`.
   `pendingAnchoredAt` builds
   `new TupletValidator.Anchored<>(pending, line.getElementIndex(pending.end()), pending.grade())`.
8. **`ModificationSession`.** `enclosingAnchorIndex` reads
   `enclosingTuplet.requireAnchorElementIndex()`. `registryTupletAnchoredAt` builds
   `new TupletValidator.Anchored<>(tuplet, tuplet.requireEndElementIndex(), tuplet.getGrade())`.
9. **`Span<?>` in the rest of `dom`.** Apply rule 9 to `Line`, `LineElement`, `LineContent`,
   `Attachment` and `SpanOutcome`.
10. **`docs/span-invalidation.md`**, *What stays with the registry*: the sentence "both of its
    endpoints are in that line, or an endpoint an edit has detached is in no line at all" becomes
    "both of its endpoints are in that line". Search the file for `judgeableSpans`, "detached",
    `outcomeFor(` and a judgement's `line` argument, and make each passage state the code as
    Phases 1–5 leave it.

---

## ✅ Phase 6: io Callers

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4  <br>
**Files:** src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/MeasureBuilder.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java, src/main/java/songscribe/io/musicxml/NoteBuilder.java  <br>
**Recommended model/effort:** Sonnet, medium — applies the grade conversion, the ending factory and the total index queries at the file boundary.

### Tasks

1. **Readers convert the grade.** `LineIO.LineReader.createTupletsFromPending`: convert the
   parsed `int` through `TupletGrade.of`. On `null`, `LOG.warn` a message naming the grade and
   skip that tuplet; otherwise build the `PendingTuplet` with the `TupletGrade`.
   `MeasureMapper.buildTuplet`: convert `marker.actualNotes()` through `TupletGrade.of`; on
   `null`, throw `DocumentValidation.corrupt(LOG, …)` stating that `<actual-notes>` is outside
   2..7. Keep the `normalNotes <= 0` check; it is the corrupt-document error for a damaged
   `<time-modification>`.
2. **Readers build endings through the factory.** `LineIO.LineReader.createEndingsFromPendingPairs`
   and `MeasureMapper.buildEnding` call
   `Ending.spanning(line, new ElementRange(anchorIndex, endIndex))`; on `null`, keep today's
   warning and skip; otherwise add the returned ending (and in `MeasureMapper` set its user Y
   offset first).
3. **Writers read total indices.** `ScorePartwiseBuilder.forEachSpanInRange` reads
   `span.requireAnchorElementIndex()` and `span.requireEndElementIndex()` and delete the
   `null` and range check, with the Javadoc paragraph that defends skipping a span.
   `MeasureBuilder.attachEndings` does the same and deletes its "malformed song" comment and
   check. `MeasureBuilder.attachEndings` calls `ending.getSplitIndex()`.
4. **Beams in the writer.** `ScorePartwiseBuilder.applyBeams` and `beamValues` iterate
   `List<Beam.Member>` (`dom/Beam.java`, Phase 2). `beamValues` returns an
   `EnumMap<BeamLevel, String>` (or an array sized `BeamLevel.values().length` indexed by
   `level.number() - 1`, whichever `applyBeams` reads more directly), uses `BeamLevel.EIGHTH`
   in place of `PRIMARY_BEAM_LEVEL`, `member.carries(level)` in place of `noteTypeInLevel`, and
   `member.stubRight()`; the `<beam number>` written is `level.number()`.
5. **Grade in the writer.** `NoteBuilder.buildTimeModification` writes
   `tuplet.getGrade().number()`; `NoteBuilder.performedTicks` reads `grade.number()`.
6. Apply rule 9 to `LineIO` and `ScorePartwiseBuilder`.

---

## ✅ Phase 7: layout, midi, message and undo Callers

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4  <br>
**Files:** src/main/java/songscribe/layout/HairpinEndpoints.java, src/main/java/songscribe/layout/stacking/DynamicGrouper.java, src/main/java/songscribe/layout/ElementColumn.java, src/main/java/songscribe/layout/ElementColumnBuilder.java, src/main/java/songscribe/layout/HorizontalSpacingCalculator.java, src/main/java/songscribe/layout/stacking/StackingUtils.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/layout/HitRegionBuilder.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/BracketRange.java, src/main/java/songscribe/shape/EndingBracketShape.java, src/main/java/songscribe/ui/renderer/EndingRenderer.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/message/mutation/SpanAddition.java, src/main/java/songscribe/message/mutation/SpanRemoval.java, src/main/java/songscribe/undo/UndoController.java  <br>
**Recommended model/effort:** Sonnet, medium — call-site updates whose shapes Phases 1–4 decide, plus one small enum decided in the findings.

### Tasks

1. **Total ranges in layout.** `HairpinEndpoints.compute`: `rangeOf(hairpin)` is never `null`;
   delete the check and the comment above it about "whichever line".
   `DynamicGrouper.clusterHairpins` reads `requireAnchorElementIndex()` and
   `requireEndElementIndex()` and deletes the `null` check.
2. **A beam column holds its beam.** `ElementColumn.beamGroupId` becomes
   `private @Nullable Beam beam`, set through `setBeam(@Nullable Beam beam)`;
   `sharesBeamGroupWith` compares `beam != null && beam == other.beam`. Update the class Javadoc
   link. `ElementColumnBuilder.buildColumnForBeam` sets the beam.
3. **Beams in layout.** `LayoutEngine.calculateBeams` iterates `List<Beam.Member>` and reads
   `member.note()`, `member.stubRight()` and `member.frenchShortening()`.
4. **Tuplet geometry calls.** `StructuralStacker` and `HitRegionBuilder` call
   `tuplet.isNumberOnly()` and
   `tuplet.bracketLeftEdgeXSs(anchorXSs, StemMetrics.thicknessSs(), <notehead width>)`.
   `HitRegionBuilder` calls `Tuplet.numberInkWidthSs(tuplet.getGrade())` unchanged in form.
5. **Ending geometry calls.** `EndingBracketGeometry` calls `ending.getSplitElement()` and
   `ending.getOpeningElementIndex()`.
6. **The bracket number is an enum.** In `layout/BracketRange.java` add a nested
   `public enum Bracket { FIRST, SECOND }` with `String label()` ("1." and "2."). The record
   component `int number` becomes `Bracket bracket`; `label()` on the record delegates to it.
   `EndingBracketGeometry.firstBracketRange` and `secondBracketRange` pass `Bracket.FIRST` and
   `Bracket.SECOND`. Update `EndingRenderer` and `EndingBracketShape` wherever they read
   `number()`.
7. **midi.** `MidiSequenceBuilder` calls `ending.getSplitIndex()` at both sites;
   `endingCovering` tests `covers.test(<the ending's range>)` with the range from
   `line.spanRegistry().rangeOf(ending)` or from the two `require…Index()` calls, whichever the
   method has in reach, and its Javadoc no longer mentions a `null` endpoint.
   `LineTrackBuilder.getElementDurationWithTuplet` reads `tuplet.getGrade().number()`.
8. Apply rule 9 to `HorizontalSpacingCalculator`, `StackingUtils`, `StructuralStacker`,
   `SpanAddition`, `SpanRemoval`, `UndoController` and every other file this phase writes.

---

## ✅ Phase 8: ui Callers

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4  <br>
**Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/action/FirstSecondEndingAction.java, src/main/java/songscribe/ui/action/TupletAction.java, src/main/java/songscribe/message/command/ToggleTupletCommand.java, src/main/java/songscribe/ui/menu/TupletMenuItems.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/EndingConfirms.java, src/main/java/songscribe/ui/dialog/MigrationWindow.java, src/main/java/songscribe/ui/renderer/TupletRenderer.java  <br>
**Recommended model/effort:** Sonnet, medium — call-site updates whose shapes Phases 1–4 decide.

### Tasks

1. **The tuplet grade in the UI.** Delete the nested enum `TupletAction.Tuplet`; `TupletAction`
   holds a `TupletGrade` (`dom/TupletGrade.java`) and its six factories pass
   `TupletGrade.DUPLET` … `TupletGrade.SEPTUPLET`. `getName` reads the constant's `name()` as
   today. `ToggleTupletCommand` carries a `TupletGrade` in place of the `int` size, and its
   getter returns it. `MusicEditOperations.toggleTuplet` takes a `TupletGrade`.
   `TupletMenuItems.existingGrade` returns `@Nullable TupletGrade`. `RangeQueries.validGradesFor`
   returns `Set<TupletGrade>`, looping `TupletGrade.values()`, and whatever record or field
   carries it (`validGrades()`) changes type with it. `MigrationWindow` shows
   `change.grade().number()`, and `span().begin() + 1`, `span().end() + 1` where it showed the
   begin and end indices.
2. **`RangeQueries.canToggleTuplet`.** `coversExisting` is
   `firstTuplet != null && line.spanRegistry().rangeOf(firstTuplet).equals(grouped)` (use the
   `LineSpans` the method already holds).
3. **The ending verdict in the UI.** `FirstSecondEndingAction.validate` disables the action when
   `!(cachedResult instanceof EndingValidationResult.Valid)`.
   `ScoreViewController.handleFirstSecondEnding` calls `operations.makeFirstSecondEnding(valid)`
   when the cached result is a `Valid valid`. `EndingConfirms` calls
   `cs.ending().getSplitElement()`.
4. **`PasteSpanReconciliation`.**
   - `reconcile`: read `requireAnchorElementIndex()` and `requireEndElementIndex()`; delete
     the `null` check and its comment. `keptStraddledHairpinTypes` becomes
     `EnumSet<Hairpin.Kind> keptStraddledHairpinKinds`, added from `hairpin.getKind()` and
     tested with `fragmentHairpin.getKind()`. Delete the `default -> { }` arm of the switch over
     the sealed `Span` hierarchy, so a new kind does not compile there.
   - `dropTupletsRejectedByTarget`: delete the `song.isReplaying()` early return and its
     comment. Its one caller, `ScoreViewController.tryInsertFragment`, is a user paste, and the
     replay depth rises only inside `UndoController.performUndo` and `performRedo`.
   - `isRejectedByTarget` passes `tuplet.getGrade()` as a `TupletGrade`.
5. **Other span calls.** `MusicEditOperations` calls every `outcomeFor`, `getSplit…`,
   `isNumberOnly` and grade API at its new signature; its beam code that reads members uses
   `Beam.Member.note()`. `TupletRenderer` calls `tuplet.isNumberOnly()`,
   `tuplet.bracketLeftEdgeXSs(anchorXSs, StemMetrics.thicknessSs(), <notehead width>)` and
   `tuplet.getGrade().number()`.
6. Apply rule 9 to `ScoreViewController`, `RangeQueries`, `SelectionCoordinator`,
   `MusicEditOperations`, `PasteSpanReconciliation` and every other file this phase writes.

---

## ✅ Phase 9: Test Tree

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8  <br>
**Files:** src/test/java/songscribe/dom/ElementChangeTest.java, src/test/java/songscribe/dom/LineSpansTest.java, src/test/java/songscribe/dom/SpanPredicateTest.java, src/test/java/songscribe/dom/SpanTest.java, src/test/java/songscribe/dom/TupletLoadPassTest.java, src/test/java/songscribe/io/musicxml/MusicXmlOffsetRoundTripTest.java, src/test/java/songscribe/io/musicxml/MusicXmlCrossLineSpanTest.java, src/test/java/songscribe/io/musicxml/MusicXmlRoundTripSupport.java, src/test/java/songscribe/layout/EndingBracketGeometryTest.java, src/test/java/songscribe/layout/VoltaAlignmentFixtureTest.java, src/test/java/songscribe/ui/clipboard/FragmentTest.java, src/test/java/  <br>
**Recommended model/effort:** Sonnet, medium — mechanical: bring the test tree to the new signatures and delete what pinned retired guards.

### Tasks

1. This phase writes under `src/test/` only, and rule 7 does not apply to it. Read
   `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` first. Write no new
   test; step 6 of the design pass decides new tests.
2. For every production symbol Phases 1–8 changed or deleted, find its test references with
   `jet_brains_find_referencing_symbols` and update each to the new signature: `Span<?>`,
   `IndexPredicate.test(ElementRange)`, `outcomeFor(ElementChange)`, `TupletGrade`,
   `TupletValidator.Anchored`'s `int endIndex`, `SpanContext`, `TupletLoadPass.Change`'s
   `span()`, `Ending.spanning` (a test that built an ending with `new Ending(...)` builds it
   through `Ending.spanning`), `EndingValidationResult.Valid`/`Invalid`, `Beam.Member`,
   `BeamLevel`, `BracketRange.Bracket`, and the `Line`-less `Ending` and `Tuplet` queries.
3. `SpanPredicateTest`: delete every row whose anchor or end index is `null`, and the `null`
   handling in its case record; each remaining row states an `ElementRange`.
4. Delete any test whose only subject is a guard Phases 1–8 deleted: a `null` endpoint index, a
   detached-span skip, an out-of-range `openingElementIndex`, `Displacement.absorbReach`, a grade
   outside 2..7 reaching the validator, the replay early return in
   `dropTupletsRejectedByTarget`. List each deleted test in the final message with the guard it
   pinned.

---

## ✅ Phase 10: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 9  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, medium — runs the gate and fixes what it reports in the file that holds each fault.

### Tasks

1. Run `./scripts/compile.sh --test`. On FAILURE, fix each error in the file that holds it,
   following the shape the phase that owns that file states, and run it again until SUCCESS.
   Report every fix in the final message with its file.
2. Run `./scripts/test.sh SpanTest LineSpansTest SpanPredicateTest TupletLoadPassTest`, then
   `./scripts/test.sh FragmentTest ElementChangeTest EndingBracketGeometryTest MusicXmlOffsetRoundTripTest`,
   then `./scripts/test.sh VoltaAlignmentFixtureTest`. Every run must pass. On a failure, read the
   output, fix the cause in production or test code, and run that command again. Do not rerun
   with extra flags.

---

## ✅ Phase 11: Manual UI Verification

**Status:** Complete  <br>
**BlockedBy:** 10  <br>
**Files:** —  <br>
**Recommended model/effort:** — the user runs this phase.

### Tasks

1. The user runs the app (`./scripts/run.sh`) and checks:
   - the Tuplet menu offers duplet through septuplet, checks the one a selected tuplet has, and
     creates and removes each;
   - Make First/Second Ending is enabled only on a valid selection, creates the ending once,
     and undo and redo restore it;
   - beamed groups with 16th and 32nd notes draw their sub-beams and partial-beam stubs on the
     same side as before, grace notes inside a beam stay outside it, and French beaming stems
     stop where they did;
   - pasting over a straddled hairpin, tuplet, beam and ending behaves as before;
   - a legacy `.mssw` file and a MusicXML file with tuplets and endings open, and a save and
     reopen keeps them.
