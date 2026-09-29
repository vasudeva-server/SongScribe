# Design Pass 7, Step 4 — Extraction and Placement

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Span Range and Range Relations](#-phase-1-span-range-and-range-relations) | ✅ Complete | — |
| 2 | [Well-Formed Endings and the Ending Load Pass](#-phase-2-well-formed-endings-and-the-ending-load-pass) | ✅ Complete | — |
| 3 | [Repeat Sections and Playback](#-phase-3-repeat-sections-and-playback) | ✅ Complete | — |
| 4 | [Tuplet Validator and Time Scaling](#-phase-4-tuplet-validator-and-time-scaling) | ✅ Complete | — |
| 5 | [Layout and Engraving Moves](#-phase-5-layout-and-engraving-moves) | ✅ Complete | — |
| 6 | [dom Callers](#-phase-6-dom-callers) | ✅ Complete | — |
| 7 | [Callers Outside dom](#-phase-7-callers-outside-dom) | ✅ Complete | — |
| 8 | [Ending Load Wiring and Migration Report](#-phase-8-ending-load-wiring-and-migration-report) | ✅ Complete | — |
| 9 | [Test Tree Fix-ups](#-phase-9-test-tree-fix-ups) | ✅ Complete | — |
| 10 | [New Tests](#-phase-10-new-tests) | ✅ Complete | — |
| 11 | [Replacement as a Set of Pairs](#-phase-11-replacement-as-a-set-of-pairs) | ✅ Complete | — |
| 12 | [One Ending Rule and Ending Separation](#-phase-12-one-ending-rule-and-ending-separation) | ✅ Complete | — |
| 13 | [The Registry's Decision](#-phase-13-the-registrys-decision) | ✅ Complete | — |
| 14 | [Editor Callers of Replacement](#-phase-14-editor-callers-of-replacement) | ✅ Complete | — |
| 15 | [Span Invalidation Doc](#-phase-15-span-invalidation-doc) | ✅ Complete | — |
| 16 | [Ending Separation and Compound Replacement Tests](#-phase-16-ending-separation-and-compound-replacement-tests) | ✅ Complete | — |
| 17 | [Compile and Test Gate](#-phase-17-compile-and-test-gate) | ✅ Complete | — |
| 18 | [Manual UI Verification](#-phase-18-manual-ui-verification) | ✅ Complete | — |

## Rules for every phase

Every phase starts by reading this section and follows it throughout.

1. The working directory is
   `/Users/aparajita/Developer/projects/SongScribe-worktrees/design-pass-7`. Every path in
   this plan is relative to it. A Java path is under `src/main/java/songscribe/` unless it
   starts with `src/`, `docs/` or `plans/`.
2. Read `.claude/rules/serena.md` and follow it. Use serena's `jet_brains_*` tools for all
   Java exploration. Use `jet_brains_find_referencing_symbols` before you decide where a
   symbol is used. Make every code change with `Edit` or `Write`. Search only inside `src/`
   and `docs/`. If a symbol is not in the places named, report it and stop searching. Do
   not widen the search.
3. Read `.claude/rules/java.md` before you write Java. Every number in logic is a named
   constant (0, 1, -1 and multiplication or division by 2 are exempt). A comment explains
   why, and describes the code as it is now. Never write what changed, what was removed,
   or what an earlier design did.
4. Before you write or change a Javadoc contract, read `.claude/guides/contracts.md`.
   Before you add or remove a `@Nullable`, read `.claude/guides/null-handling.md`. Before
   you add or change a user-facing string, read `.claude/guides/strings.md`.
5. Line numbers in this plan are from commit `3698e098`. They drift as phases land. Find
   every member by its symbol, never by its line.
6. Do not run `./scripts/compile.sh` or `./scripts/test.sh`. The tree compiles only when
   every implementation phase is done. Phase 17 is the one compile.
7. Do not edit anything under `src/test/`, except in Phases 9, 10 and 16.
8. Never stage, commit, stash or amend.
9. If a symbol that a task names does not exist, or a caller set differs from the one the
   task states, report it in your final message and do the rest of the phase. Do not
   widen the change to cover it.
10. `ElementRange` is the record `StaffElementRun.ElementRange` in
    `dom/StaffElementRun.java`: an inclusive range `[begin, end]` with
    `0 <= begin <= end`. It cannot be empty.

---

## ✅ Phase 1: Span Range and Range Relations

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/StaffElementRun.java, src/main/java/songscribe/dom/Hairpin.java  <br>
**Recommended model/effort:** Opus, high — contracts for a query that about twenty callers read, and for two index relations whose boundary semantics decide an ending defect.

### Tasks

1. Write the contract of a new method on `Span` (`dom/Span.java`):
   `public final ElementRange range()`. It returns the span's range in its line, from the
   anchor's index to the end's index. It reads `requireAnchorElementIndex()` and
   `requireEndElementIndex()`, and it throws what they throw. Read their Javadoc and state
   the same conditions in `range()`'s `@throws`. Many callers rely on this method, so the
   contract is deep: postconditions, the line the indices are in, and the errors.
2. Implement `Span.range()` as
   `new ElementRange(requireAnchorElementIndex(), requireEndElementIndex())`.
3. Write the contracts of two new methods on `ElementRange` (`dom/StaffElementRun.java`).
   They are two different relations. Their Javadoc says which question each one answers:
   - `public boolean containsExcludingBounds(int index)`: whether the element at `index`
     is inside the range and is neither bound. The test is `begin < index < end`.
   - `public boolean insertionLandsInside(int index)`: whether an element inserted at
     `index` lands inside the range. `index` is an insertion point in the line before the
     insertion. An insertion at an index goes in front of the element that is now at that
     index (see `ElementChange.Insertion.projectedIndexOf`). So an insertion at `begin`
     lands in front of the range, and an insertion at `end` lands inside it. The test is
     `begin < index <= end`.
4. Implement both methods.
5. Change `Span.strictlyContaining(int elementIndex)` to return
   `spanRange -> spanRange.containsExcludingBounds(elementIndex)`. Keep its Javadoc
   paragraph about text dynamics on a hairpin bound.
6. Change `Span.isSameKindAs(Span<?> other)` so it compares the concrete class only
   (`other.getClass() == getClass()`), and remove `final`. It stays package-private. Its
   Javadoc says that it is the test every `Displacement` applies, and that a subclass may
   override it to narrow the test. Remove the sentence about `Hairpin.Kind` from it.
7. In `dom/Hairpin.java`, override `isSameKindAs`. It returns
   `super.isSameKindAs(other)` and the test that `other`'s `Hairpin.Kind` is this
   hairpin's kind. Its Javadoc says that a crescendo never displaces a diminuendo.
8. Read the Javadoc of `Displacement` (`dom/Displacement.java`). It must stay true with
   the override. If a sentence in it names the old test in `Span`, report it. Do not edit
   `Displacement.java`.

---

## ✅ Phase 2: Well-Formed Endings and the Ending Load Pass

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/PendingEnding.java, src/main/java/songscribe/dom/EndingLoadPass.java, src/main/java/songscribe/io/LoadMigrations.java  <br>
**Recommended model/effort:** Opus, high — the class invariant of an ending, one factory that the editor and both readers share, the sweep rules for deletion and replacement, and a new load pass.

Every attached ending is well formed. This is the class invariant. Three routes can
create an ending: the editor, the MusicXML reader and the legacy reader. All three go
through `Ending.spanning`, which applies the whole rule. A reader does not call
`spanning` itself. It records a `PendingEnding`, and the new `EndingLoadPass` builds or
drops each one. The edit sweep (`Ending.outcomeFor`) removes an ending when an edit would
break the rule.

**The rule.** An ending over `[anchor, end]` of a line is well formed when all of these
are true:

- The anchor is a note or a rest (`ElementType.isDuration()`) or a `SINGLE_BARLINE`. Any
  other bar refuses the ending.
- In front of a note or rest anchor, the first element that is not a grace note, a breath
  mark or a key change (`isNonContentElement()`) is a `SINGLE_BARLINE`, a note or a rest,
  or the anchor starts its line. An ending does not start immediately after `||`, `|:`,
  `:|` or `:|:`.
- A bar strictly inside the range has a type that `closesRepeat()`. The first such bar is
  the split.
- `Ending.isValidEnd(splitType, endType)` accepts the split and the end.
- Each bracket holds no bar and at least one note or rest (`isDuration()`). A grace note,
  a slide or a breath mark is not content.

**The brackets.**

| Bracket | First element | Last element |
|---|---|---|
| First | The anchor, when it is a note or a rest. The element after the anchor, when the anchor is a `SINGLE_BARLINE` | The element before the split |
| Second | The element after the split | The end, when it is a note or a rest. The element before the end, when the end is a bar |

The rule makes each bracket hold at least one element, so each bracket is an
`ElementRange`.

The factory carries no minimum element count. The bracket rule sets it: a note or rest
anchor and a note or rest end give anchor, split and end, which is three, so `6 :| 7` is
an ending. The editor does not keep `MIN_CONTENT_ELEMENTS` (`ui/MusicEditOperations.java`)
or any other count of its own. The editor also refuses a second closing bar. The bracket rule
implies that too, because the second one is a bar inside a bracket.

### Tasks

1. Rewrite the class Javadoc `@invariant` of `Ending` (`dom/Ending.java`) to state the
   whole rule above. It names `spanning` as the one way to create an ending, and
   `outcomeFor` as what removes an ending that an edit would make ill formed.
2. Write the contract of `Ending.firstBracket()` and `Ending.secondBracket()`. Each one is
   `public ElementRange` over the ending's line, as the bracket table above states. The
   invariant makes each one total. Implement both.
3. Add one private rule that says whether a bracket holds content: at least one element
   whose type `isDuration()`. The creation rule, the deletion rule and the replacement
   rule all read it. Choose its parameter so that the deletion rule can ask about the
   elements that are left after a deletion. Name it for what it answers.
4. Narrow `Ending.opensOnItsOwn` from `type.isBar()` to `SINGLE_BARLINE`. Change
   `openingElementIndex(Line, int)` so that it walks back to a preceding `SINGLE_BARLINE`
   only from a note or rest anchor. An anchor that is a bar is its own opening element. A
   walk that meets any other element answers the anchor. The editor anchors a new ending
   on the opening element, and `EndingBracketGeometry` starts the first bracket there.
   The walk back and the test in front of a note or rest anchor share one private helper,
   which finds the first element in front of an index that is not
   `isNonContentElement()`. Update the Javadoc of `getOpeningElementIndex` and
   `openingElementIndex` to match. The paragraph about asking twice stays true: keep it.
   The MusicXML writer (`MeasureBuilder.attachEndingAnchor`) has no `REPEAT_LEFT` anchor
   case.
5. Rewrite the contract of `Ending.spanning(Line line, ElementRange span)`. It returns an
   ending over `span` when the rule above holds, and `null` otherwise. The editor and the
   load pass both call it. Remove the words that it checks only for a split.
6. Implement `spanning` with the whole rule. It reads the split through
   `closingRepeatsBetween`. It reads the brackets through the same code as tasks 2 and 3,
   so creation and the sweep cannot disagree on where a bracket starts.
7. Rewrite `deletionBreaksBrackets(List<StaffElement> deletedElements)` so that it reads
   `firstBracket()` and `secondBracket()`. The deletion breaks the ending when it takes
   the split, or when a bracket holds no content after the deletion (task 3's rule over
   the bracket's elements that are not deleted). This fixes the case where the first
   bracket starts at a note anchor: line `A X :| Y ||` with the ending over all five, and
   `X` deleted, keeps the ending because `A` is still content.
8. Rewrite `checkReplacement(ElementChange.Replacement replacement)`:
   - Condition 1, the anchor is replaced. The ending stays only when the new type is an
     anchor that the rule accepts (a note, a rest or `SINGLE_BARLINE`), and
     the first bracket still holds content with the new anchor. So a `SINGLE_BARLINE`
     anchor replaced by a `DOUBLE_BARLINE` answers `Invalidate`. A note anchor that was the
     only content of the first bracket and is replaced by a bar answers `Invalidate`.
   - Conditions 2 and 3, the split and the end, keep their current answers.
   - A new condition, any other element strictly inside the ending. A replacement whose
     new element is a bar answers `Invalidate`, as an insertion of a bar does. A
     replacement that leaves its bracket with no content answers `Invalidate`, as a
     deletion does.
   - Before condition 1: a replacement of the anchor or of an element in front of it that
     leaves a note or rest anchor with a refused bar in front of it answers `Invalidate`.
     The test reads `replacement.projectedElements()`.
   - Any other element outside the ending answers `EndingEffect.None.INSTANCE`.
   In `outcomeFor`, an insertion or a deletion also removes the ending when it leaves a
   note or rest anchor with a refused bar in front of it. The test reads
   `change.projectedElements()` at the anchor's projected index. Update the comments on
   each condition, and the Javadoc of `outcomeFor`, to state these rules. Do not touch
   `insertionAddsInteriorBar`. Phase 6 changes it.
9. Create `dom/PendingEnding.java`, a record on the pattern of `dom/PendingTuplet.java`:
   `PendingEnding(StaffElement anchor, StaffElement end, double yOffsetSs)`. Its Javadoc
   says that it is an ending as a reader found it in a file, before the load pass judges
   it. It is not a span, and nothing outside the load path sees one. `yOffsetSs` becomes
   the ending's `getUserYOffsetSs()`.
10. Write the contract of a new `dom/EndingLoadPass.java`, on the pattern of
    `dom/BreathMarkLoadPass.java`: a final class with a private constructor and
    `public static Report run(Song song, List<PendingEnding> pendingEndings)`. For each
    pending ending, in order, it asks `Ending.spanning` over the range from the anchor's
    index to the end's index in the anchor's line. It adds an ending that `spanning`
    returns, with the pending `yOffsetSs`. It drops one that `spanning` refuses. It logs a
    drop with `LOG.warn` and reports it. `Report` holds a `List<Removed>`, with `empty()`
    and `isEmpty()` as `BreathMarkLoadPass.Report` has. `Removed(int lineIndex,
    ElementRange span)` holds the line and the range of a dropped ending, in the line as
    the reader built it. Implement it.
11. Change `io/LoadMigrations.java`:
    - Add a record component `EndingLoadPass.Report endingReport`, after `tupletReport`.
      Document it as the other two components are documented.
    - `run` takes `List<PendingEnding> pendingEndings` after `pendingTuplets`. It runs the
      passes in this order: tuplets, endings, breath marks, then
      `song.stampAccidentalsAfterParsing()`. State in `run`'s Javadoc why endings run
      before breath marks: the breath-mark pass removes elements, and each pass reports
      the indices of the line that it saw. No ending the rule accepts has a breath mark as
      an endpoint, so the breath-mark pass cannot break one.
    - `isEmpty` is also `false` when an ending was dropped.
    Phase 8 changes the two callers of `run` (`io/SongLoader.java` and
    `io/musicxml/SongMapper.java`). Do not edit them.

---

## ✅ Phase 3: Repeat Sections and Playback

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java  <br>
**Recommended model/effort:** Opus, high — a new public query on `Song` that the editor and playback share, and a rewrite of the playback repeat walk with a `repeating` flag and line-crossing jumps.

A `:|` (`REPEAT_RIGHT`) closes the section that the nearest `|:` (`REPEAT_LEFT`), `:|:`
(`REPEAT_LEFT_RIGHT`) or song start opens, when no `:|`, `||` (`DOUBLE_BARLINE`) or final
barline (`FINAL_DOUBLE_BARLINE`) is between them. A `:|` with no open section is legal.
The editor lets a user write one, as in `A :| B :|` or `A || B :|`. Playback ignores it.
The editor does not refuse it, and a load does not change it.

### Tasks

1. Write the contract of a new method on `Song` (`dom/Song.java`):
   `public @Nullable ElementLocation repeatSectionStart(ElementLocation location)`.
   - It returns the first element of the repeat section that is open just before
     `location`. That is the element after the section's `|:` or `:|:`, or the song's
     first element when the song start opens the section.
   - The backward walk starts at the element before `location` and crosses lines. It
     stops at a `:|`, a `||` or a final barline, and then it returns `null`. Every other
     element is skipped.
   - When the opener is the last element of its line, the answer is the first element of
     the next line that has elements. Such a line exists, because `location` comes after
     the opener.
   - The song start is the first element of `firstNonEmptyLineIndex()`. A leading empty
     line is skipped.
   - State that `ElementLocation` is `dom/ElementLocation.java`,
     `record ElementLocation(int lineIndex, int elementIndex)`.
   The editor and playback both rely on it, so the contract is deep.
2. Implement `repeatSectionStart`. Its walk is the body of
   `MusicEditOperations.hasEnclosingRepeat` in `ui/MusicEditOperations.java` (read it;
   do not edit that file). The difference is the answer: a location, not a boolean.
3. Rewrite the repeat-marker handling in `MidiSequenceBuilder.buildSequenceWithRepeats`
   (`midi/MidiSequenceBuilder.java`):
   - The repeat-marker test reads `noteType.closesRepeat()`, both where it builds
     `isEndingClosingMarker` and where it guards the handler.
   - On a closing marker when `repeating` is `false`, ask
     `song.repeatSectionStart(new ElementLocation(lineIndex, noteIndex))`. On `null`,
     ignore the marker: do not set `repeating`, and play on. Otherwise set `repeating`,
     and continue from the returned location, so that the loop's increment lands on the
     returned element.
   - Delete the backward search loop (`repeatSearchLoop`) and the `noteIndex = -1` case
     for the song start. `repeatSectionStart` answers both.
   - A `:|:` is a closing marker, so it asks the same question. Its opening half still
     opens a section, because `repeatSectionStart` treats `:|:` as an opener.
   - A jump to another line must continue on that line. Today the outer loop binds
     `line`, `noteCount`, `builder` and `endings` once per line. After a jump to an
     earlier line, the inner loop keeps the old line's values with the new `noteIndex`,
     so a section that opens on an earlier line replays the wrong elements. Restructure
     the loops so that a jump rebinds all four for the line it lands on.
   - Keep the ending handling after the marker handler as it is. Phase 7 changes
     `endingCovering` and `addLineSkippingFirstEndings` in this file. Do not edit them.
4. Update the Javadoc of `buildSequenceWithRepeats`. Remove "This implements the same
   logic as the old ScoreView.addLineToTrack method." State that a `:|` with no open
   section plays on.

---

## ✅ Phase 4: Tuplet Validator and Time Scaling

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/TupletValidator.java, src/main/java/songscribe/dom/TempoResolver.java, src/main/java/songscribe/dom/NotatableValue.java, src/main/java/songscribe/dom/BeatUnit.java, src/main/java/songscribe/dom/Tuplet.java  <br>
**Recommended model/effort:** Opus, high — one accumulator replaces two ways to measure a span, a validation rule moves into it under both strictnesses, and a scaling query gets two rounding modes.

### Tasks

1. Write the contract of a new method on `Tuplet` (`dom/Tuplet.java`):
   `public long performedTicks(long writtenTicks, RoundingMode rounding)`. It returns
   `writtenTicks × normalNotes ÷ grade`, where `normalNotes` is `getRatio().normalNotes()`
   and `grade` is `getGrade().number()`, rounded by `rounding`. With
   `RoundingMode.UNNECESSARY` an inexact result throws `ArithmeticException`. Two callers
   rely on it with two modes, so state both. Implement it. Phase 7 changes the callers
   (`io/musicxml/NoteBuilder.performedTicks` and `midi/LineTrackBuilder.scaledOffset`). Do
   not edit them. Do not make other changes to `Tuplet.java`. Phases 5 and 6 own the rest
   of its changes.
2. Write the contract of a new package-private method on `TempoResolver`
   (`dom/TempoResolver.java`), beside `beatDefinedAt`:
   `static boolean splitsBeat(StaffElement element, BeatUnit runningBeat)`. It answers
   what `TupletValidator.isBeatBarrier` answers today: a metric modulation always splits
   the beat, and a tempo marking splits it only when its note value differs from
   `runningBeat`. It reads the two attachments through `beatDefinedAt`'s precedence, so
   the two methods cannot disagree. One line of Javadoc is enough, plus the precedence
   sentence. Implement it. Update `beatDefinedAt`'s Javadoc sentence about
   `TupletValidator` so that it names `splitsBeat`.
3. Write the contract of a new method on `NotatableValue` (`dom/NotatableValue.java`):
   `public static @Nullable NotatableValue withTicks(int ticks)`. It returns the note
   value and dots whose `ticks()` equals `ticks`, or `null` when none does. Most tick
   counts have no written value, so the `null` is honest. Move `NOTATABLE_BY_TICKS` and
   `buildNotatableByTicks` from `TupletValidator` into `NotatableValue` as private
   members. `TupletValidator` reads `NotatableValue.withTicks`.
4. Write the contract of a new method on `BeatUnit` (`dom/BeatUnit.java`):
   `public int binaryDivisionRootTicks()`. An undotted beat is its own root. A dotted beat
   first divides into three, so its root is a third of it. Move `DOTTED_BEAT_PARTS` from
   `TupletValidator` into `BeatUnit`. In `TupletValidator.conventionalSpans`, replace the
   `divisionRoot` calculation with `beat.binaryDivisionRootTicks()`. The halving loop and
   the multiples stay in `conventionalSpans`, because they are the tuplet rule.
5. Design one private accumulator in `TupletValidator`. It holds the running beat, the
   placement reasons, the written ticks and the count of pitched notes
   (`ElementType.isPitchedNote()`: every note except a grace note). It has one method per
   element. The beat barrier test calls `TempoResolver.splitsBeat`. Both entry points
   start from a beat that includes the anchor's own definition, because
   `TempoResolver.beatUnitAt` starts its walk at the anchor. Keep that start.
6. Make `describeSpan` feed the accumulator the elements of the range. Make
   `validateFrom` feed it the elements of the forward walk, in place of
   `OpenSpan.accumulate`. Delete `writtenTicks`, `hasFermata`, `crossesBeatBarrier`,
   `crossesStructuralBoundary` and `isBeatBarrier`. Delete what is left of `OpenSpan`
   when the accumulator covers it.
7. Move the "enough notes" rule into the validator. Add a private constant to
   `TupletValidator` for the minimum, with the value of `Tuplet.MIN_NON_REST_NOTES` and a
   name for what it counts now (pitched notes). The rule applies under both `Strictness` values, as the fermata rule does. When the
   span holds fewer pitched notes than the minimum, the result is invalid with
   `Reason.TOO_FEW_NOTES`. Add the count to `SpanContext`, and document the new
   component. With this count, a tuplet over one note, a grace note and a rest is
   refused, and so is one over three rests. Do not delete `Tuplet.holdsEnoughNotes` or
   `Tuplet.MIN_NON_REST_NOTES`. Phase 6 deletes them and the pre-check in
   `TupletLoadPass.place`.
8. Where a span fails more than one rule, keep the order in which the validator reports
   reasons today, and put `TOO_FEW_NOTES` where the fermata rule's reason is reported.
   State the order in the Javadoc of the method that returns the reason, if that Javadoc
   states an order today.

---

## ✅ Phase 5: Layout and Engraving Moves

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 4  <br>
**Files:** src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/Trill.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/engraving/TupletMetrics.java, src/main/java/songscribe/engraving/HairpinMetrics.java, src/main/java/songscribe/engraving/BeamMetrics.java, src/main/java/songscribe/engraving/StemMetrics.java, src/main/java/songscribe/layout/TupletGeometry.java, src/main/java/songscribe/layout/TrillGeometry.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/HairpinEndpoints.java, src/main/java/songscribe/layout/HorizontalSpacingCalculator.java, src/main/java/songscribe/layout/HitRegionBuilder.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/layout/stacking/NoteAttachedStacker.java, src/main/java/songscribe/ui/renderer/TupletRenderer.java, src/main/java/songscribe/ui/renderer/TrillRenderer.java, src/main/java/songscribe/ui/renderer/EndingRenderer.java  <br>
**Recommended model/effort:** Sonnet, medium — moves of members to destinations the plan names, with one decided signature change and reader updates.

`engraving` imports only `smufl`. It cannot name a `dom` type, and it holds no font. A
member goes to `engraving` when its Javadoc gives a LilyPond source, on the pattern of
`engraving/BeamMetrics.java`. Every other member goes to `layout`. Each new class is a
final class with a private constructor, and each has a short class Javadoc that says what
it holds. Move each member's Javadoc with it. Before you move a member, run
`jet_brains_find_referencing_symbols` on it, and update every reader it finds.

### Tasks

1. Create `engraving/TupletMetrics.java`. Move `Tuplet.ARM_EXTENSION_SS`,
   `Tuplet.BRACKET_ARM_HEIGHT_SS` and `Tuplet.MAX_SLOPE_FACTOR` into it.
2. Create `layout/TupletGeometry.java`. Move these `Tuplet` members into it:
   `TUPLET_FONT_SIZE_SS`, `TUPLET_FONT`, `TUPLET_NUMBER_INK_HEIGHT_SS`,
   `measureNumberInkHeightSs`, `numberInkWidthSs`, `MIN_SPAN_WIDTH_SS`,
   `bracketedHeightSs`, `numberOnlyHeightSs`, `bracketLineOffsetSs`,
   `bracketRightEdgeXSs`, `bracketLeftEdgeXSs` and `getSpanWidthSs`.
   - `bracketLeftEdgeXSs` becomes
     `static double bracketLeftEdgeXSs(Tuplet tuplet, double anchorXSs, double noteheadWidthSs)`.
     It reads `tuplet.anchorStemUp()`, and it reads `StemMetrics.thicknessSs()` itself.
     The `stemSs` parameter is deleted. All three callers pass `StemMetrics.thicknessSs()`
     today (`StructuralStacker`, `HitRegionBuilder`, `TupletRenderer`).
   - `getSpanWidthSs` becomes static. It reads no instance state.
   - `Tuplet` keeps `isNumberOnly` and `anchorStemUp`. Its `LocalFonts` and
     `TextMeasurement` imports go when nothing in it reads them.
   - Readers: `layout/stacking/StructuralStacker.java`, `layout/HitRegionBuilder.java`,
     `ui/renderer/TupletRenderer.java`.
3. Create `layout/TrillGeometry.java`. Move `Trill.glyphLeftXSs` and
   `Trill.getSpanWidthSs` into it as static methods. Both read `Trill.TRILL_GLYPH_WIDTH_SS`.
   `Trill` keeps `TRILL_GLYPH_WIDTH_SS`, the glyph height, `intrinsicWidthSs` and
   `intrinsicHeightSs`. Readers: `layout/stacking/NoteAttachedStacker.java`,
   `layout/HitRegionBuilder.java`, `ui/renderer/TrillRenderer.java`.
4. Create `engraving/HairpinMetrics.java`. Move `Hairpin.MINIMUM_LENGTH_SS`,
   `BOUND_PADDING_SS` and `BACK_TO_BACK_PADDING_SS` into it. `Hairpin` keeps
   `HAIRPIN_OPENING_HEIGHT_SS` and `MIN_COLUMNS`. Readers include
   `layout/HairpinEndpoints.java` and `layout/HorizontalSpacingCalculator.java`, in code
   and in `{@link}` tags.
5. Move `Ending.VOLTA_TICK_HEIGHT_SS`, `LABEL_X_INSET_SS` and `LABEL_Y_OFFSET_SS` into
   `layout/EndingBracketGeometry.java`, which already holds the label font. Readers
   include `EndingBracketGeometry` itself and `ui/renderer/EndingRenderer.java`.
6. Add `public static double upwardStackBottomYSs(StaffPosition highestHead, int stackCount)`
   to `engraving/BeamMetrics.java`. Its body is the body of the private
   `Beam.estimatedBeamBottomAboveYSs(int topStaffPosition)`: the head's Y, less
   `StemMetrics.STEM_LENGTH_SS`, plus `beamStackHeightSs(stackCount)`. Move the Javadoc
   paragraph about why it is an estimate with it.
7. Add `public static double graceStemTopYSs(StaffPosition head)` to
   `engraving/StemMetrics.java`: the head's Y less `GRACE_NOTE_STEM_LENGTH_SS`.
8. In `dom/Beam.java`, delete `estimatedBeamBottomAboveYSs`. `graceStemTouchesBeamAbove`
   reads `BeamMetrics.upwardStackBottomYSs` and `StemMetrics.graceStemTopYSs`. Change its
   parameter from the `int` staff position to a `StaffPosition` when the caller in
   `Beam.direction` has one, or can build one. `Beam.direction` keeps
   `GRACE_STEM_BEAM_CLEARANCE_SS` and the decision.
9. Run `jet_brains_find_referencing_symbols` on each moved member again. No reader may
   name the old owner. `src/test/` must not name one either. If a test names one, report
   it for Phase 9.

---

## ✅ Phase 6: dom Callers

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 4, 5  <br>
**Files:** src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/TupletLoadPass.java  <br>
**Recommended model/effort:** Sonnet, medium — call-site updates to queries that Phases 1, 2 and 4 created, deletions, and two private helpers whose shape the tasks state.

These queries exist when this phase starts:

- `Span.range()`, `public final ElementRange range()` in `dom/Span.java`: the span's
  range from its anchor's index to its end's index.
- `ElementRange.insertionLandsInside(int index)` in `dom/StaffElementRun.java`:
  `begin < index <= end`, for an insertion point.
- `TupletValidator` in `dom/TupletValidator.java` applies the "enough notes" rule and
  answers `Reason.TOO_FEW_NOTES` itself.

### Tasks

1. Delete `LineSpans.rangeOf` (`dom/LineSpans.java`). Each of its readers in the file
   reads `span.range()`: `mergeOverlapping` (two sites), `removeOverlappingSameKind` (two
   sites) and `isMatch`. Update the class Javadoc sentence that links `#rangeOf`, so that
   it names `Span#range`. Phase 7 updates the readers outside `dom`.
2. `LineSpans.HairpinSpan` and `LineSpans.PlacedSpan` are one shape: a span with a range.
   Replace both with one private record, `SpanAt<S extends Span<?>>(S span, ElementRange range)`.
   Each use states in its own Javadoc or comment what its range is over: a
   `HairpinSpan`'s range is over the projection, and a `PlacedSpan`'s range is over the
   line.
3. `Hairpin.hairpinOutcomeFor` (`dom/Hairpin.java`) reads `range()` where it builds a
   range from the two indices by hand.
4. In `dom/Tuplet.java`:
   - `outcomeFor` reads `range()` in place of `change.line().spanRegistry().rangeOf(this)`.
   - Delete `holdsEnoughNotes` and `MIN_NON_REST_NOTES`. `TupletValidator` holds the rule
     and its own constant. Fix the `{@link #holdsEnoughNotes}` in `outcomeFor`'s Javadoc so that it
     names the rule in `TupletValidator` (`Reason#TOO_FEW_NOTES`).
5. In `dom/Ending.java`:
   - `getSplitElement` reads `range()` in place of the range it builds by hand.
   - `insertionAddsInteriorBar` returns
     `insertedType.isBar() && range().insertionLandsInside(insertedIndex)`. This fixes a
     defect: a bar inserted at the end index goes in front of the end element, which is
     inside the ending. Replace the comment in it with one that states this.
6. In `dom/Beam.java`:
   - `notesInSpan` reads `line.getElements(range())`.
   - `replacementBreaksInvariant` reads `range().contains(...)`.
   - Add a private `Member.neighborCarries(int offset, BeamLevel level)`. It answers
     whether the member at `position + offset` carries `level`
     (`BeamLevel.isCarriedBy`), and `false` past either end of the group. In `stubRight`,
     the stub test is `carries(level) && !neighborCarries(-1, level) && !neighborCarries(1, level)`.
     The direction rule after it keeps `beamCountAt`, and so does `frenchShortening`.
7. `ModificationSession.registryTupletAnchoredAt` (`dom/ModificationSession.java`) asks
   `findFirstSpan(Tuplet.class, Span.anchoredAt(index))` on the registry, in place of
   `findTupletAt` plus an anchor test.
8. In `dom/TupletLoadPass.java`:
   - Delete `firstPopulatedLineIndex`. `placeTuplets` calls `song.firstNonEmptyLineIndex()`.
   - Delete the `Tuplet.holdsEnoughNotes` pre-check in `place`. The validator answers
     `TOO_FEW_NOTES`.
   - Delete the exception clause in `Change.Removed`'s Javadoc. Every reason is the
     validator's.

---

## ✅ Phase 7: Callers Outside dom

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5  <br>
**Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/io/musicxml/NoteBuilder.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/layout/stacking/DynamicGrouper.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/HairpinEndpoints.java  <br>
**Recommended model/effort:** Sonnet, medium — call-site updates and deletions against queries that Phases 1 to 4 created.

These queries exist when this phase starts:

- `Span.range()`, `public final ElementRange range()` in `dom/Span.java`.
  `LineSpans.rangeOf` does not exist.
- `Ending.spanning(Line, ElementRange)` in `dom/Ending.java` applies the whole rule for a
  well-formed ending. `Ending.firstBracket()` and `Ending.secondBracket()` return each
  bracket's `ElementRange`.
- `Song.repeatSectionStart(ElementLocation)` in `dom/Song.java` returns the first element
  of the repeat section open just before the location, or `null` when none is open.
- `Tuplet.performedTicks(long writtenTicks, RoundingMode rounding)` in `dom/Tuplet.java`.

### Tasks

1. In `ui/MusicEditOperations.java`:
   - `addHairpinToSelection` reads `range()` where it builds the range by hand.
   - `canMakeFirstSecondEnding` asks
     `song.repeatSectionStart(new ElementLocation(lineIndex, begin)) != null` in place of
     `hasEnclosingRepeat`. Delete `hasEnclosingRepeat`.
   - `canMakeFirstSecondEnding` no longer calls `validateEndingStructure`. `Ending.spanning`
     applies the rule. Delete `validateEndingStructure`, `validateEndingRegionContent`
     and `MIN_CONTENT_ELEMENTS`. `canMakeFirstSecondEnding` keeps the terminal extension,
     the overlap test, the repeat-section question, `Ending.openingElementIndex` and the
     `spanning` call. Renumber or rewrite its stage comments to match.
   - `hasOverlap` takes the selection's `ElementRange` and returns
     `spans.hasSpan(Ending.class, Span.overlapping(range))`. Delete its loop and the
     comment about per-index cost.
   - `modifyStemDirection` iterates `beam.members()` in place of the walk from the anchor
     to the end that skips grace notes. `applyStemChange` takes an index, so each member
     gives `line.getElementIndex(member.note())`.
2. `RangeQueries.canToggleTuplet` (`ui/selection/RangeQueries.java`) reads
   `firstTuplet.range()` in place of `spans.rangeOf(firstTuplet)`.
3. In `ui/clipboard/PasteSpanReconciliation.java`:
   - `reconcile` reads `range()` where it reads both indices.
   - `isRejectedByTarget` reads `range()`. Delete its check for a negative or reversed
     index and the comment about a malformed span. The index queries are total on a
     registered span, so no caller can produce that state.
4. In `midi/MidiSequenceBuilder.java`:
   - `endingCovering` returns the first ending whose `range().contains(elementIndex)`.
     Delete its hand-built range and the `Span.containing` predicate.
   - `addLineSkippingFirstEndings` skips from `ending.firstBracket().begin()` through
     `ending.getSplitIndex()`, clipped to `[lineStart, lineEnd]` as now.
   - Do not touch `buildSequenceWithRepeats`. Phase 3 owns it.
5. `midi/LineTrackBuilder.java`: delete `scaledOffset`. Its callers ask
   `tuplet.performedTicks(writtenTicks, RoundingMode.HALF_UP)`.
6. `io/musicxml/NoteBuilder.java`: `performedTicks` asks
   `tuplet.performedTicks(writtenTicks, RoundingMode.UNNECESSARY)` and converts the result
   with `Math.toIntExact`. Put a `catch (ArithmeticException e)` around the call. It
   throws a new `ArithmeticException` with the current message that names `DIVISIONS`,
   with `e` as its cause. Keep the method's Javadoc paragraph about `DIVISIONS`.
7. `io/musicxml/ScorePartwiseBuilder.java`: `SpanAction.apply` takes
   `(Line line, T span, ElementRange range)` in place of two `int`s.
   `forEachSpanInRange` passes `span.range()`. Update every lambda and method reference
   that implements `SpanAction`, and the Javadoc of `SpanAction`.
8. `layout/stacking/StructuralStacker.java`: `collectNonRestSpannedColumns` reads
   `range()` where it reads both indices.
9. `layout/stacking/DynamicGrouper.java`: `clusterHairpins` reads `hairpin.range()`.
   `Member.OfHairpin` holds that range.
10. `layout/EndingBracketGeometry.java`: `computeBracketRanges` reads `range()`. Delete
    its check that returns an empty list when an index is not in the line, and the
    `@return` words about it. The index queries are total on a registered span.
11. `layout/HairpinEndpoints.java`: `compute` reads `hairpin.range()` in place of
    `line.spanRegistry().rangeOf(hairpin)`. If a comment in it describes an endpoint "in
    whichever line", report it.

---

## ✅ Phase 8: Ending Load Wiring and Migration Report

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/SongMapper.java, src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/SongIO.java, src/main/java/songscribe/io/SongLoader.java, src/main/java/songscribe/ui/dialog/MigrationWindow.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Sonnet, medium — reader wiring on the existing pending-tuplet pattern, and a migration-window group on the existing pattern.

These types exist when this phase starts:

- `dom/PendingEnding.java`: `record PendingEnding(StaffElement anchor, StaffElement end, double yOffsetSs)`.
- `dom/EndingLoadPass.java`: `EndingLoadPass.run(Song, List<PendingEnding>)` returns a
  `Report` with `removed()`, a `List<Removed>`. `Removed(int lineIndex, ElementRange span)`.
  The pass logs each drop.
- `io/LoadMigrations.java`: the record has an `endingReport` component, and
  `run(Song song, List<PendingTuplet> pendingTuplets, List<PendingEnding> pendingEndings, boolean accidentalsConverted)`.

A reader does not build an ending. It records a `PendingEnding`, as it records a
`PendingTuplet` for a tuplet. Follow how each reader carries its pending tuplets to
`LoadMigrations.run`, and carry pending endings the same way.

### Tasks

1. `io/musicxml/MeasureMapper.java`: `buildEnding` records a `PendingEnding` over the
   anchor, the end and `yOffsetSs`, in place of `Ending.spanning`, `setUserYOffsetSs` and
   `addTo`. Its `LOG.warn` goes: `EndingLoadPass` logs drops. Its `line == null` early
   return stays. Update its Javadoc. Add the pending endings to `MeasureMapper.Result`, as
   `pendingTuplets` is there.
2. `io/musicxml/SongMapper.java`: pass the mapped pending endings to
   `LoadMigrations.run`.
3. `io/LineIO.java`: `LineReader.createEndingsFromPendingPairs` keeps its
   `DocumentValidation.requireIndexInRange` check. For each pair it records a
   `PendingEnding` over `line.getElement(pair[0])` and `line.getElement(pair[1])`, with a
   `yOffsetSs` of 0, in place of `Ending.spanning` and `addTo`. Its `LOG.warn` and the
   comment about pre-#306 endings go. `EndingLoadPass` drops a split-less ending. Expose
   the pending endings as `LineReader.pendingTuplets()` exposes pending tuplets.
4. `io/SongIO.java`: `DocumentReader` gathers the pending endings from its line readers
   and exposes `pendingEndings()`, as `pendingTuplets()` does. Its Javadoc says that the
   list is empty before `getSong()` runs.
5. `io/SongLoader.java`: pass `reader.pendingEndings()` to `LoadMigrations.run`.
6. `ui/dialog/MigrationWindow.java`: add a group for dropped endings, after the tuplet
   groups and before the breath-mark group. It shows only when the report is not empty.
   Each leaf shows the line and the element range, 1-based, as `describe(TupletLoadPass.Change)`
   does. The leaf's detail says that the ending is not a valid first/second ending. In
   `show`, the suppressed-window `LOG.info` also counts dropped endings. Update the class
   Javadoc sentence that names only tuplets, so that it also names endings.
7. `src/main/resources/songscribe/strings.properties`: add the keys the group needs, in
   sorted position in the `dialog.migration` group: a group title with a count, on the
   pattern of `dialog.migration.tuplets.removed`, and the reason text, on the pattern of
   the `dialog.migration.reason.*` keys. Reuse `dialog.migration.change` for the leaf
   when its pattern fits.

---

## ✅ Phase 9: Test Tree Fix-ups

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/test/java/songscribe/dom/SpanTest.java, src/test/java/songscribe/dom/LineSpansTest.java, src/test/java/songscribe/dom/ElementChangeTest.java, src/test/java/songscribe/layout/EndingBracketGeometryTest.java, src/test/java/songscribe/layout/VoltaAlignmentFixtureTest.java, src/test/java/songscribe/io/musicxml/MusicXmlOffsetRoundTripTest.java  <br>
**Recommended model/effort:** Sonnet, low — test-tree call-site updates, and fixture checks against a rule this task states in full.

Read `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` first.

### Tasks

1. `SpanTest.crescendoRanges` and `LineSpansTest.rangesOf` read `span.range()` in place of
   the range they build by hand. `Span.range()` is `public final ElementRange range()` in
   `dom/Span.java`.
2. `Ending.spanning(Line, ElementRange)` now refuses an ending that is not well formed.
   The rule: the anchor is a note, a rest or `SINGLE_BARLINE`. In front of a note or rest
   anchor is a `SINGLE_BARLINE`, a note, a rest, or the start of the line. A bar
   strictly inside closes a repeat (the first one is the split).
   `Ending.isValidEnd(splitType, endType)` accepts the split and the end. The first
   bracket runs from the anchor (from the element after it, when the anchor is a
   `SINGLE_BARLINE`) to the element before the split. The second bracket
   runs from the element after the split to the end (to the element before it, when the
   end is a bar). Each bracket
   holds no bar and at least one note or rest. Check each fixture that calls
   `Ending.spanning` against this rule: `LineSpansTest.newEnding`,
   `ElementChangeTest.fixtureLine`, `EndingBracketGeometryTest.fixtureOf`,
   `VoltaAlignmentFixtureTest.endingCase` and `MusicXmlOffsetRoundTripTest.spanOffsetCases`.
3. For each fixture that the rule refuses, change the fixture's line so that the ending
   is well formed and the test still checks what its name and Javadoc say. If no such
   change keeps the test's purpose, report it and do not change that fixture.
4. If Phase 5 reported a test that names a moved member, update that test to name the new
   owner.

---

## ✅ Phase 10: New Tests

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4  <br>
**Files:** src/test/java/songscribe/dom/EndingTest.java, src/test/java/songscribe/dom/SongRepeatSectionTest.java, src/test/java/songscribe/midi/MidiRepeatTest.java, src/test/java/songscribe/dom/TupletLoadPassTest.java  <br>
**Recommended model/effort:** Sonnet, medium — table tests derived from contracts that Phases 2, 3 and 4 wrote.

The user approved exactly these five tests. Write no other test. Read
`.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` first. Each test
is one parameterized method over a table of cases. Build songs and lines with the
factories in `src/test/java/songscribe/dom/` (`SongFactory`, `StaffElementFactory`).
Derive every expected value from the contract in the Javadoc of the method under test,
not from its implementation. Before you write each test method, check whether a sibling
of the same shape is next to it. If one is, the two are rows in one table.

### Tasks

1. `dom/SongRepeatSectionTest.java`, one table over `Song.repeatSectionStart`. Each row
   is a song, a query location and the expected location or `null`. Rows: a `|:` opener
   in the query's line; a `:|:` opener; an opener on an earlier line; no opener, so the
   song's first element; a leading empty line before the first element; an opener that
   is the last element of its line; a stop at `:|`; a stop at `||`; a stop at a final
   barline.
2. `midi/MidiRepeatTest.java`, one table over playback order. Follow
   `src/test/java/songscribe/midi/MidiTieTest.java` for how a test builds a sequence and
   reads its notes. Each row is a song and the expected order of notes. Rows:
   `|: A :| B` plays A A B; `A :| B` plays A A B; `A :| B :|` plays A A B; `A || B :|`
   plays A B; `|: A :|: B :| C` plays A A B B C; `|: A` on one line and `B :| C` on the
   next plays A B A B C.
3. `dom/EndingTest.java`, a table over `Ending.spanning`. Each row is a line, a range and
   whether an ending results. Rows: each anchor type (a note at the start of the line, a
   rest and `SINGLE_BARLINE` accepted; `REPEAT_LEFT`, `DOUBLE_BARLINE`,
   `REPEAT_LEFT_RIGHT` and `FINAL_DOUBLE_BARLINE` refused); no split; a bar in the first
   bracket; a bar in the second bracket; a bracket that holds only a grace note; a split
   and end pair that `Ending.isValidEnd` refuses and one it accepts. A second table over
   `spanning` states what stands in front of a note or rest anchor: a note after `|` or
   after a note (accepted); a note after `||`, `|:`, `:|` or `:|:` (refused); a rest after
   `:|:` (refused); a note after `||` and a grace note (refused).
4. `dom/EndingTest.java`, a second table over the sweep. Each row starts from an ending
   that `spanning` accepts, applies one edit to the line, and states whether the ending
   is kept. For a kept row, also assert that `spanning` accepts the ending's range in the
   edited line. Rows: line `A X :| Y ||` with the ending over all five and `X` deleted
   (kept); the only content of the second bracket deleted (removed); a bar inserted at
   the end index (removed); a bar inserted at the anchor index (kept); a note inserted
   inside a bracket (kept); a content note inside a bracket replaced by a bar (removed);
   a `SINGLE_BARLINE` anchor replaced by a `DOUBLE_BARLINE` (removed); a note anchor that
   is the only content of the first bracket replaced by a `SINGLE_BARLINE` (removed); a
   `:|` inserted in front of a note anchor (removed); the note in front of a note anchor
   replaced by a `||` (removed); the only note between a `|:` and a note anchor deleted
   (removed); a `SINGLE_BARLINE` anchor after a `:|:` replaced by a note (removed). Find how existing tests
   apply an edit through the line's change path (`src/test/java/songscribe/dom/ElementChangeTest.java`
   and `BatchReplay.java`), and apply edits the same way.
5. `dom/TupletLoadPassTest.java`: add rows to `loadCases`. A span of three rests is
   dropped with `TupletValidator.Reason.TOO_FEW_NOTES`. A span of a note, a grace note
   and a rest is dropped with `TOO_FEW_NOTES`.

---

## ✅ Phase 11: Replacement as a Set of Pairs

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/ElementChange.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/Hairpin.java  <br>
**Recommended model/effort:** Opus, high — the contract of a change shape that every span kind judges, and the undo order of a multi-element replacement.

A replacement holds one or more `(index, newElement)` pairs at distinct indices. A
replacement of one element is a set of one. There is no other replacement shape. A
replaced endpoint is re-pointed at its new element, as it is now.

### Tasks

1. In `dom/ElementChange.java`, change `Replacement` to
   `record Replacement(Line line, NavigableMap<Integer, StaffElement> newElements)`. The
   compact constructor throws `IllegalArgumentException` when the map is empty, and
   `IndexOutOfBoundsException` when a key is not an index of `line`. It stores an
   unmodifiable copy (`Collections.unmodifiableNavigableMap(new TreeMap<>(...))`). Add
   `static Replacement single(Line line, int index, StaffElement newElement)`.
2. Delete `Replacement.index()` (the record component), `newElement()` and
   `oldElement()`. Add these members, each with a contract:
   - `@Nullable StaffElement replacementOf(StaffElement element)`: the element that takes
     `element`'s place, or `null` when this replacement does not replace `element`.
   - `Replacement with(int index, StaffElement newElement)`: this replacement with one
     more pair. It throws `IllegalArgumentException` when `index` is already replaced.
3. Rewrite `Replacement.projectedElements()`: the projected element at a position is the
   new element there, or the line's element when the position is not replaced. Rewrite
   `projectedIndexOf`: a new element answers its index (ask this first, so a self-replace
   keeps its position); a replaced element answers `null`; any other element answers its
   current index. Nothing moves.
4. Move `Hairpin.projectedEndpointIndexOf` to `ElementChange` as a default method,
   `@Nullable Integer projectedEndpointIndexOf(StaffElement endpoint)`. It reads a replaced
   endpoint as its replacement (`replacementOf`), and asks `projectedIndexOf` otherwise.
   Its Javadoc states the re-pointing rule, as `Span.outcomeFor`'s paragraph "A replacement
   re-points" does. `Hairpin` calls the moved method.
5. Rewrite the Javadoc of `Replacement` and of the `ElementChange` interface for a
   replacement of one or more elements. Do not touch `Replacement.endingEffect`. Phase 13
   replaces it.
6. In `dom/Line.java`, add `public void replace(ElementChange.Replacement replacement)`.
   It throws `IllegalArgumentException` when `replacement.line()` is not this line. It
   checks `song.checkTerminalPolicyForReplacement` for every pair before it changes
   anything. It calls `spans.sweep(replacement)` once. Then, for each pair in index order,
   it applies one `ElementReplacement` record with the mutator that `setElement` has now
   (detach, set, attach, `spans.repoint`). Move the comments about the order of the sweep
   and the detach into `replace`. Write its contract, with `@throws` and `@effects`.
7. `Line.setElement(int, StaffElement)` becomes
   `replace(ElementChange.Replacement.single(this, index, element))`. Its Javadoc points
   to `replace` for the rules.
8. In `dom/Beam.java`, `replacementBreaksInvariant` answers `true` when any pair breaks
   the rule. The rule for one pair is the current body, in a private method that takes
   the index and the new element.
9. In `dom/Tuplet.java`, the replacement arm of `outcomeFor` answers `true` when any pair
   is in the range and unbalances the group. `replacementUnbalances` takes
   `(StaffElement oldElement, StaffElement newElement)`. Update the Javadoc of
   `outcomeFor` where it speaks of "a replacement".
10. Do not edit `dom/Ending.java`, `ui/` or `undo/MutationReplayer.java`. Phase 12 and
    Phase 14 change the first two. `MutationReplayer` replays one record at a time through
    `setElement`, and the sweep does nothing during replay.

---

## ✅ Phase 12: One Ending Rule and Ending Separation

**Status:** Complete  <br>
**BlockedBy:** 11  <br>
**Files:** src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/EndingLoadPass.java  <br>
**Recommended model/effort:** Opus, high — the class invariant of an ending, one predicate that creation and every edit share, and the compensation rule the prompts read.

**The separation rule.** A note or rest (`isDuration()`) stands strictly between an
ending's end and the opening element of the next ending on the line. A grace note, a
slide or a breath mark does not count, as in a bracket. The rule compares the next
ending's opening element (see `openingElementIndex`), not its stored anchor. Examples:
when ending A ends on note 3, ending B cannot anchor on note 3 or on note 4; B cannot open
on a `|` directly after note 3; B can open on a `|` after note 4.

**The edit rule.** An edit removes an ending exactly when the ending is not well formed in
the projection. For a replacement, the prompts also need to know whether a compensating
pair would keep the ending. A well-formed projection answers `None`. An ill-formed one
answers:

| Replaced | Condition | Answer |
| --- | --- | --- |
| The split and not the end | The projection with the end set to the matching type is well formed | `CompensateEnd` |
| The end and not the split | The projection with the split set to the matching type is well formed | `CompensateSplit` |
| Anything else | — | `Invalidate` |

The matching end for a new split: `REPEAT_RIGHT` takes `SINGLE_BARLINE`, and
`REPEAT_LEFT_RIGHT` takes `REPEAT_RIGHT`. The matching split for a new end: a
`REPEAT_LEFT_RIGHT` split becomes `REPEAT_RIGHT`, and any other split becomes
`REPEAT_LEFT_RIGHT`. These are the types that `checkReplacement` uses now.

### Tasks

1. In `dom/Ending.java`, add one private predicate,
   `isWellFormed(List<StaffElement> elements, int anchorIndex, int endIndex)`. It is the
   whole class invariant for one ending, as `spanning` applies it now, over a list. The
   helpers it reads that take a `Line` take the element list instead
   (`closingRepeatsBetween`, `isWellFormedBracket`, and any other one that you find).
   `firstBracket()`, `secondBracket()` and `getSplitElement()` stay as queries on the
   attached ending over its line.
2. Add a package-private static predicate,
   `isSeparated(List<StaffElement> elements, int earlierEndIndex, int laterAnchorIndex)`.
   It finds the later ending's opening element over `elements`, and answers whether a note
   or rest stands strictly between `earlierEndIndex` and that element. It answers `false`
   when the opening element is at or before `earlierEndIndex`. It uses the same
   content test as `holdsContent`. Give `openingElementIndex` a form over an element list,
   and make the `Line` form call it.
3. Add a line to the class Javadoc `@invariant` for the separation rule. Change the
   paragraph above the tags: `outcomeFor` removes an ending that an edit makes ill formed
   in the projection, and the span registry removes the later of two endings that an edit
   leaves back to back.
4. `spanning` asks `isWellFormed` over `line.getElements()`. It then checks the candidate
   against each ending in `line.spanRegistry().findEndings()`: an ending that starts
   before the candidate must be separated from the candidate, and the candidate must be
   separated from an ending that starts after it. Each overlap fails this check. Update
   its contract: it also returns `null` when an ending over `span` is not separated from
   an ending already on the line.
5. Rewrite `outcomeFor`. It answers `REMOVE` when `change.projectedEndpointIndexOf` is
   `null` for the anchor or the end, or when `isWellFormed` refuses the projection at
   those two indices. The answer is the same for all four change kinds. Rewrite its
   Javadoc to state that rule, and delete the list of cases.
6. Add a package-private `EndingEffect effectOf(ElementChange.Replacement replacement)`
   that applies the edit rule table above. It builds a compensated projection with
   `replacement.with(index, type.newInstance())`. Write its contract.
7. Delete `checkReplacement`. Then run `jet_brains_find_referencing_symbols` on
   `anchorKeepsItsOpening`, `deletionBreaksBrackets`, `survivorsOf`, `afterReplacement` and
   `insertionAddsInteriorBar`, and delete each one that has no caller left. Report any one
   that still has a caller.
8. In `EndingEffect`, add
   `sealed interface Compensation extends EndingEffect permits CompensateEnd, CompensateSplit`
   with `ElementChange.Replacement applyTo(ElementChange.Replacement replacement)`: the
   replacement with the compensating pair added (the ending's end or split, replaced by a
   new instance of the record's type). `EndingEffect` permits `None`, `Invalidate` and
   `Compensation`. Rewrite the Javadoc of the two records: a confirmed compensation lands
   in the same change as the replacement it compensates.
9. In `dom/EndingLoadPass.java`, state in the Javadoc of `run` that the pass judges
   pending endings in order, so it drops the later ending of a pair that overlaps or is
   back to back, because `spanning` sees the endings already added.

---

## ✅ Phase 13: The Registry's Decision

**Status:** Complete  <br>
**BlockedBy:** 12  <br>
**Files:** src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/ElementChange.java  <br>
**Recommended model/effort:** Opus, high — one decision that the sweep acts on and both prompts read, with a rule between siblings that no span can apply.

### Tasks

1. In `dom/LineSpans.java`, add a package-private record `SweepDecision` that holds the
   spans the change removes and the hairpin reshapes by kind (the two collections that
   `sweep` builds now). Add a package-private `SweepDecision decide(ElementChange change)`
   that holds the loop from `sweep`.
2. After the loop, `decide` applies the separation rule. It takes the endings that the
   loop did not remove, in the order of their projected anchor index
   (`change.projectedEndpointIndexOf`). It walks them in pairs. When
   `Ending.isSeparated` refuses a pair over `change.projectedElements()`, it removes the
   later ending, and the next pair starts from the earlier one. It builds the projection
   only when two or more endings survive the loop.
3. `sweep` keeps its early return, calls `decide`, and acts on the decision as it does
   now. Update the Javadoc of `sweep`: the decision is made first so that the prompts read
   the same answer, and the rule between endings is a property of the collection, as the
   hairpin merge is.
4. In `dom/ElementChange.java`, `removesConfirmableSpan` answers from
   `line().spanRegistry().decide(this)`: whether a removed span `requiresInvalidationConfirm`.
   Update its Javadoc.
5. Delete `Replacement.endingEffect()`. Add
   `List<Ending.EndingEffect> endingEffects()`. For each ending on the line, in line
   order: the answer is `ending.effectOf(this)`, and when that is `None` but the decision
   removes the ending, the answer is `Invalidate`. The list holds only the answers that
   are not `None`. Its contract states: one effect for each affected ending; for a
   replacement of one element, at most one, because the separation invariant puts the
   first content element in front of an ending outside every other ending.

---

## ✅ Phase 14: Editor Callers of Replacement

**Status:** Complete  <br>
**BlockedBy:** 13  <br>
**Files:** src/main/java/songscribe/ui/selection/SelectionActionApplier.java, src/main/java/songscribe/ui/component/score/PreviewElementInserter.java, src/main/java/songscribe/ui/EndingConfirms.java, src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Opus, medium — two editor paths move to one compound change, with the prompts and the cancel rule this task states in full.

### Tasks

1. In `ui/MusicEditOperations.java`, delete `hasOverlap` and the overlap stage of
   `canMakeFirstSecondEnding`. `Ending.spanning` refuses an overlap. Renumber the stage
   comments that remain.
2. In `ui/EndingConfirms.java`, delete `applyCompensatingEndChange`,
   `applyCompensatingSplitChange` and `applyCompensatingChange`. The confirm methods stay.
3. In `ui/selection/SelectionActionApplier.java`, split `decideChanges` in two:
   - `decideReplacement` for an `ElementReplaceable` action. It builds one
     `ElementChange.Replacement` over every admitted element of the selection (with
     `createReplacement`). It asks `endingEffects()` once, and shows the prompt for each
     effect in order: `confirmInvalidation`, `confirmCompensateEnd`, or
     `confirmCompensateSplit` with the type of the new element at that ending's end
     (`replacementOf(ending.getEndElement())`). A declined prompt cancels the whole action:
     the method returns `null`, and nothing changes. Each confirmed `Compensation` is
     applied with `applyTo`. It returns the final replacement, or `null` when no element
     was admitted.
   - `decideModifications` for an `ElementModifiable` action: the current modification
     branch.
   `PendingChange` loses its `compensation` component. For a replacement, the fit gate
   reads one `PendingChange` for each pair of the final replacement, so it measures the
   line as the action will leave it. The apply pass calls `line.replace(replacement)` once,
   inside the modification bracket. Update the Javadoc of `apply` (pass 1 and the
   comments about the order of a compensation).
4. In `ui/component/score/PreviewElementInserter.java`, `modifyExistingElement` builds
   `ElementChange.Replacement.single(line, elementIndex, replacement)`, asks
   `endingEffects()`, and shows the prompt for the one effect. A decline returns. A
   confirmed compensation is applied with `applyTo`. The `setElement` call becomes
   `line.replace(...)` with that replacement. The beam removal stays between the prompt and
   the replacement, as its comment says.

---

## ✅ Phase 15: Span Invalidation Doc

**Status:** Complete  <br>
**BlockedBy:** 13  <br>
**Files:** docs/span-invalidation.md  <br>
**Recommended model/effort:** Sonnet, medium — prose that states concepts, from rules this phase states in full.

The doc states concepts, not class or method names. Read the whole doc first.

### Tasks

1. "Spans judge a projection": hairpins and endings read the projection. Endings, like
   hairpins, are rare, so the frugality argument stays.
2. "Replacement re-points": a replacement is one or more elements replaced at once. A
   compensation that keeps an ending well formed is part of the same change.
3. "What stays with the registry": add a third property of the collection. No two endings
   are back to back, and when an edit leaves two endings back to back, the registry
   removes the later one.
4. "One funnel" or "Decisions are made before the change lands": the registry decides
   first and acts afterwards, and the prompts read the same decision, so a prompt and its
   edit cannot disagree.

---

## ✅ Phase 16: Ending Separation and Compound Replacement Tests

**Status:** Complete  <br>
**BlockedBy:** 11, 12, 13, 14  <br>
**Files:** src/test/java/songscribe/dom/EndingTest.java, src/test/java/songscribe/dom/ElementChangeTest.java, src/test/java/songscribe/dom/EndingLoadPassTest.java  <br>
**Recommended model/effort:** Sonnet, medium — table rows derived from contracts that Phases 11, 12 and 13 wrote.

The user approved exactly these tests. Write no other test. Read
`.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` first. Build songs
and lines with the factories in `src/test/java/songscribe/dom/` (`SongFactory`,
`StaffElementFactory`). Derive every expected value from the contract in the Javadoc of
the method under test, not from its implementation. Before you write a test method, check
whether a sibling of the same shape is next to it. If one is, the two are rows in one
table.

In the rows below, "A" is an ending that is already on the line, and "B" is a second
ending. "Separated" means the rule at the top of Phase 12.

### Tasks

1. Test code that no longer compiles: `ElementChangeTest.confirmReplacement` and
   `ElementChangeTest.projectionCases` call the `Replacement` constructor. They call
   `ElementChange.Replacement.single` instead. A confirm row that reads `endingEffect()`
   reads `endingEffects()`. Keep what each test checks.
2. `EndingTest`, the table over `spanning` (`spanningCases`). Add rows, each with A on the
   line before `spanning` is asked:
   - Refused: the candidate anchors on the `|` that ends A.
   - Refused: A ends on a note, and the candidate anchors on the next note.
   - Refused: A ends on a note, and the candidate opens on the `|` directly after it.
   - Refused: only a grace note stands between A's end and the candidate's opening
     element.
   - Refused: the candidate ends before A with nothing between the candidate's end and
     A's opening element.
   - Refused: the candidate overlaps A.
   - Accepted: one note stands between A's end and the candidate's opening element.
3. `EndingTest`, the sweep table (`sweepCases`). Add rows:
   - A and B are separated by one note, and that note is deleted: B is removed and A is
     kept.
   - A and B are separated by one note, and that note is replaced by a `|`: B is removed
     and A is kept.
   - An ending with split `:|` and a note end. The split is replaced by `:|:`, as one
     replacement with every compensation `endingEffects()` returns applied. The ending is
     kept, and its end is still the same note.
   - An ending with split `:|:` and end `:|`. One replacement sets the split to `:|` and
     the end to `:|`. The ending is removed.
   For a kept row, the table already asserts that `spanning` accepts the ending's range
   in the edited line. Extend that assertion so it runs with the other endings on the
   line in place.
4. `EndingTest`, a new table over `ElementChange.Replacement.endingEffects()`. Each row is
   a line, a replacement and the expected list of effects:
   - The split only, `:|` → `:|:`, with end `|`: one `CompensateEnd` with `REPEAT_RIGHT`.
   - The end only, `|` → `:|`, with split `:|`: one `CompensateSplit` with
     `REPEAT_LEFT_RIGHT`.
   - The split only, `:|` → `:|:`, with a note end: an empty list.
   - The split and the end, set to a pair `Ending.isValidEnd` refuses: one `Invalidate`.
   - One replacement that changes the split of A and the split of B: two effects, in line
     order.
   - A and B are separated by one note, and that note is replaced by a `|`: one
     `Invalidate` for B.
5. `ElementChangeTest.projectionCases`: add a row for a replacement with two pairs.
6. The `ElementChangeTest` test that checks `removesConfirmableSpan` against what the sweep
   then removes (`testRemovesConfirmableSpanAgreesWithWhatTheSweepThenRemoves`): add a
   row where the sweep removes an ending only because the change leaves it back to back
   with an earlier ending.
7. New `dom/EndingLoadPassTest.java`, one test. A song with two pending endings back to
   back: `EndingLoadPass.run` adds the first, drops the second, and its report holds one
   `Removed` for the second ending's line and range. Follow
   `src/test/java/songscribe/dom/TupletLoadPassTest.java` for how a test builds pending
   spans and reads the report.

---

## ✅ Phase 17: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 1–16  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — runs the build and the named tests, and reports.

### Tasks

1. Run `./scripts/compile.sh --test`. It must report SUCCESS. On a failure, read the
   error. If it is a caller that a phase missed, and the fix is the call-site change the
   phase's tasks state, make that fix. Otherwise report the error and stop.
2. Run each of these. A run names at most four classes. Each run must be green:
   - `./scripts/test.sh EndingTest SongRepeatSectionTest MidiRepeatTest TupletLoadPassTest`
   - `./scripts/test.sh SpanTest LineSpansTest SpanPredicateTest ElementChangeTest`
   - `./scripts/test.sh EndingBracketGeometryTest VoltaAlignmentFixtureTest MusicXmlOffsetRoundTripTest PackageDependencyTest`
   - `./scripts/test.sh MidiTieTest LoadMigrationsTest MusicEditOperationsTest BeatUnitTest`
   - `./scripts/test.sh EndingLoadPassTest LineTest TieRuleTest ProjectedEditTest`
   - `./scripts/test.sh SongAccidentalWalkTest InheritedKeyPropagationTest`
3. On a failure, read the output. Do not rerun with flags. Do not stash to compare. Report
   the failing test, the assertion and the cause you find. Fix it only when the cause is a
   missed call site. Report every other failure and stop.
4. Report the result of each command in the final message.

---

## ✅ Phase 18: Manual UI Verification

**Status:** Complete  <br>
**BlockedBy:** 17  <br>
**Files:** —  <br>
**Recommended model/effort:** the user — each check needs the running app, and some need listening to playback.

The user starts the app with `./scripts/run.sh` and does each check. Ask the user for
the result of each check, and record it in the final message.

### Tasks

1. Endings, creation. Select `A :| B` followed by a final barline, with nothing before
   `A` on the line: the first/second ending action is enabled, and the brackets draw over
   `A` and over `B`. On line `C | A :| B` followed by a final barline, select from the `|`
   through the final barline: the ending is created, and the first bracket starts at `A`.
   Undo the ending. Select from `A` through the final barline: the ending anchors on the
   `|`, and it draws the same. On line `C |: A :| B` followed by a final barline, select
   from the `|:` through the final barline: the action is disabled. Select from `A`
   through the final barline: the action is disabled, because `A` follows the `|:`. Do
   these two checks again with `||` in place of `|:`.
2. Endings, edits. On line `A X :| Y ||` with an ending over all five, delete `X`: no
   confirmation shows, and the ending stays. Insert a barline in front of the ending's
   last element: the ending is removed. Replace a note inside a bracket with a barline:
   the ending is removed. Undo restores the ending each time.
3. Endings, separation. Make ending A that ends on a `|`, followed by one note. Select
   from the note after A's `|`: the first/second ending action is disabled. Make ending B
   after that note instead, then delete the note: a confirmation shows, B is removed, and
   A stays. Undo restores the note and B.
4. Endings, selection replacement. On an ending with split `:|:` and end `:|`, select from
   the split through the end and apply the right repeat: one confirmation shows, and the
   ending is removed on Yes. Do it again and answer No: nothing changes. On an ending with
   split `:|` whose end is a note, change the split to `:|:`: no confirmation shows, and the
   note is still there.
5. Tuplets. Select three eighth rests: the tuplet action is disabled. Select a note, a
   grace note and a rest: the tuplet action is disabled. Select three eighth notes: the
   tuplet action is enabled.
6. Playback. Play `A :| B :|`: A plays twice, then B once, with no third A. Play
   `A || B :|`: A then B, each once. Play a song with `|: A :| B`: A twice, then B. Play
   a song with `|: A` on one line and `B :| C` on the next: A B A B C.
7. Rendering. Open a song with tuplets (bracketed and number-only), trills, hairpins,
   first/second endings and beamed grace notes. Compare them with the same song on
   `develop`: nothing moves.
8. Migration report. Ask the executing session to make a copy of a saved `.musicxml`
   file that has a first/second ending, and to move that ending's `type="stop"` one
   measure earlier, so that the second bracket holds no note. Open the copy: the
   migration window lists one dropped ending, with its line and elements. The score shows
   no bracket there.
