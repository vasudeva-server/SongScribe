# Pass 7, Spans — step 4: extraction and placement

All paths are under `src/main/java/songscribe/` unless they start with `src/test`. Line numbers are from commit `d58a1b4c`, the tree step 3 left.

## What it found

### Static bags

The target holds no `*Utils` or `*Helper` class. `TupletValidator` and `TupletLoadPass` are static classes, but each one names a domain operation. No item below goes back to step 2 for that reason. Item 20 is a concept with no owner, the repeat structure of a song.

### Operations on a type that does not own them

- `LineSpans.rangeOf` (`dom/LineSpans.java:113`) reads only the span's two index getters. It reads nothing of the registry. Seven more sites build the same `ElementRange` by hand. Item 1.
- `Span.isSameKindAs` (`dom/Span.java:221`) tests for `Hairpin` by name. Item 3.
- `Tuplet.holdsEnoughNotes` (`dom/Tuplet.java:223`) is a validation rule. Every other tuplet rule is in `TupletValidator`. Item 7.
- `TupletValidator.isBeatBarrier` (`dom/TupletValidator.java:577`) reads the same two attachments that `TempoResolver.beatDefinedAt` reads. Item 9.
- `TupletValidator.buildNotatableByTicks` (`:694`) and the `DOTTED_BEAT_PARTS` division (`:625`) are facts about `NotatableValue` and `BeatUnit`. Items 10 and 11.
- The rule for a well-formed first/second ending is in the editor (`ui/MusicEditOperations.java`, `validateEndingStructure` and `validateEndingRegionContent`). Items 18 and 19.

### Layout and engraving knowledge on span classes

Every reader of these members is in `layout`, `layout/stacking` or `ui/renderer`. No `dom` class reads them, except the span class that declares them.

| Class | Members | Readers |
|---|---|---|
| `Tuplet` | `TUPLET_FONT`, `TUPLET_FONT_SIZE_SS`, `TUPLET_NUMBER_INK_HEIGHT_SS`, `numberInkWidthSs`, `ARM_EXTENSION_SS`, `BRACKET_ARM_HEIGHT_SS`, `MAX_SLOPE_FACTOR`, `MIN_SPAN_WIDTH_SS`, `bracketedHeightSs`, `numberOnlyHeightSs`, `bracketLineOffsetSs`, `bracketLeftEdgeXSs`, `bracketRightEdgeXSs`, `getSpanWidthSs` | `StructuralStacker`, `HitRegionBuilder`, `TupletRenderer` |
| `Trill` | `glyphLeftXSs`, `getSpanWidthSs` | `NoteAttachedStacker`, `HitRegionBuilder`, `TrillRenderer` |
| `Hairpin` | `MINIMUM_LENGTH_SS`, `BOUND_PADDING_SS`, `BACK_TO_BACK_PADDING_SS` | `HairpinEndpoints`, `HorizontalSpacingCalculator` |
| `Ending` | `VOLTA_TICK_HEIGHT_SS`, `LABEL_X_INSET_SS`, `LABEL_Y_OFFSET_SS` | `EndingBracketGeometry`, `EndingRenderer` |
| `Beam` | `estimatedBeamBottomAboveYSs` (private) | `Beam.direction`, through `graceStemTouchesBeamAbove` |

`Tuplet.getSpanWidthSs` and `Trill.getSpanWidthSs` are instance methods that read no instance state. All three callers of `Tuplet.bracketLeftEdgeXSs` pass `StemMetrics.thicknessSs()` as `stemSs` (`StructuralStacker.java:195`, `HitRegionBuilder.java:520`, `TupletRenderer.java:113`).

These members stay where they are, because they are model facts: `Tuplet.isNumberOnly` and `anchorStemUp` (they read the beams and the stem direction), `Hairpin.MIN_COLUMNS` (an editing rule), `Hairpin.HAIRPIN_OPENING_HEIGHT_SS` and the trill glyph height (each one answers `IntrinsicHeight`, which row 5 put on `dom`), `Trill.TRILL_GLYPH_WIDTH_SS` and `intrinsicWidthSs` (the width of the trill's own glyph, read from the same SMuFL bounding box as its height), and `Beam.GRACE_STEM_BEAM_CLEARANCE_SS` (the threshold of the direction rule).

`engraving` imports only `smufl`. It cannot name a `Tuplet` or a `Direction`.

### Logic written twice

| Logic | Sites |
| --- | --- |
| A span's range from its two indices | `LineSpans.rangeOf`; `Ending.getSplitElement` (`dom/Ending.java:145`); `Hairpin.hairpinOutcomeFor` (`dom/Hairpin.java:159`–`:163`); `MusicEditOperations.addHairpinToSelection` (`ui/MusicEditOperations.java:559`); `MidiSequenceBuilder.endingCovering` (`midi/MidiSequenceBuilder.java:186`); `PasteSpanReconciliation.isRejectedByTarget` (`ui/clipboard/PasteSpanReconciliation.java:309`); `SpanTest.crescendoRanges`; `LineSpansTest.rangesOf` |
| A span's facts, measured element by element | `TupletValidator.describeSpan` through `writtenTicks`, `hasFermata`, `crossesBeatBarrier`, `crossesStructuralBoundary` (`:528`–`:596`); `TupletValidator.OpenSpan.accumulate` and the walk in `validateFrom` (`:417`–`:448`, `:498`–`:512`) |
| Written ticks scaled by a tuplet's M:N | `NoteBuilder.performedTicks` (`io/musicxml/NoteBuilder.java`), exact or it throws; `LineTrackBuilder.scaledOffset` (`midi/LineTrackBuilder.java:100`), rounded half up |
| The first line that has elements | `TupletLoadPass.firstPopulatedLineIndex` (`dom/TupletLoadPass.java:265`); `Song.firstNonEmptyLineIndex` |
| The elements under each bracket of an ending | `Ending.deletionBreaksBrackets` (`dom/Ending.java:309`–`:317`); `MusicEditOperations.validateEndingStructure` (`ui/MusicEditOperations.java:915`, `:921`); `MidiSequenceBuilder.addLineSkippingFirstEndings` (`midi/MidiSequenceBuilder.java:359`–`:361`) |
| A bar that closes a repeat | `ElementType.closesRepeat`; `MidiSequenceBuilder.java:244` and `:248` |
| A beam's members | `Beam.members`; `MusicEditOperations.modifyStemDirection` (`ui/MusicEditOperations.java:1106`–`:1112`) |
| A tuplet anchored at an index | `LineSpans.findFirstSpan(Tuplet.class, Span.anchoredAt(index))`; `ModificationSession.registryTupletAnchoredAt` |
| An ending that overlaps a range | `LineSpans.hasSpan(Ending.class, Span.overlapping(range))`; `MusicEditOperations.hasOverlap` (`ui/MusicEditOperations.java:955`) |
| Whether a neighbour carries a beam level | `BeamLevel.isCarriedBy`; `Beam.Member.stubRight` (`dom/Beam.java:386`) |

### Defects

**The sweep removes an ending that the editor would create.** `Ending.deletionBreaksBrackets` counts the first bracket's content from `anchor + 1` (`dom/Ending.java:310`). The editor counts it from the anchor when the anchor is a note (`ui/MusicEditOperations.java:910`–`:915`). The anchor is a note when no bar comes before the selection, because `Ending.openingElementIndex` then keeps the note. The failure:

1. Line: note A, note X, right repeat, note Y, final barline. The user makes an ending over all five. It anchors on A.
2. The user deletes X.
3. The sweep finds no content in the first bracket, and it asks the user to confirm that the ending goes. The first bracket still holds A, and the editor accepts the same ending over A, the repeat, Y and the barline.

The second bracket agrees in both places: both exclude the end.

**The editor makes a tuplet that the next load drops.** `RangeQueries.canToggleTuplet` requires two non-grace elements, and its Javadoc says that rests are welcome. The validator does not apply `Tuplet.holdsEnoughNotes`. Only `TupletLoadPass.place` applies it (`dom/TupletLoadPass.java:222`). So a triplet over three eighth rests passes the editor, and the load pass drops a tuplet like it with `TOO_FEW_NOTES`. I did not trace whether the MusicXML writer and reader carry a tuplet over rests to the load pass.

**A bar inserted in front of an ending's terminal element does not remove the ending.** `Ending.insertionAddsInteriorBar` (`dom/Ending.java:412`) tests `insertedIndex < endIndex`. An insertion at an index goes in front of the element now at that index (`ElementChange.Insertion.projectedIndexOf`). So an insertion at `endIndex` puts the bar between the second bracket's content and the terminal element, and the ending stays with a bar in its second bracket. The editor refuses that bar when it creates an ending (`validateEndingRegionContent`). I did not check whether an editor path stops this insertion before the sweep sees it. Item 2 fixes it.

**Playback replays a `:|` that has no open section.** A `:|` closes the section that the nearest `|:`, `:|:` or song start opens, with no `:|`, `||` or final barline between them. The editor lets a user write a `:|` with no open section, as in `A :| B :|` or `A || B :|`. Playback must ignore such a `:|`. `MidiSequenceBuilder.buildSequenceWithRepeats` replays it from the nearest repeat sign of any kind, or from the song start, and it walks back across a `||`. Item 20 fixes it.

**Playback replays the wrong elements when a repeat section opens on an earlier line.** `MidiSequenceBuilder.buildSequenceWithRepeats` binds `line`, `noteCount`, `builder` and `endings` once per pass of its outer loop. A jump back sets `lineIndex` and `noteIndex` and continues the inner loop, which still holds the old line's values. So for `|: A` on one line and `B :| C` on the next, the replay reads the second line at indices from the first line, and then continues from the line after the one it jumped to. Item 20 fixes it.

**A replacement puts a bar in a bracket and keeps the ending.** `Ending.checkReplacement` (`dom/Ending.java`) tests only the anchor, the split and the end. For any other element it answers `None`. So a note inside a bracket that is replaced by a barline or a repeat stays under the ending. Condition 1 also accepts any bar as a new anchor. So a `SINGLE_BARLINE` anchor that is replaced by a `||` keeps the ending, and so does a note anchor that is replaced by a bar and was the only content of the first bracket. I did not check whether an editor path stops these replacements before the sweep sees them. Item 18 fixes it.

**The editor anchors an ending on the bar that ends the previous one.** `MusicEditOperations.canMakeFirstSecondEnding` tests overlap over `[begin, end]`, and then `Ending.openingElementIndex` moves the anchor back to the `|` in front of `begin`. The overlap test never sees that `|`. An ending may end on a `SINGLE_BARLINE` (`isEndingTerminal`) and anchor on one. So when ending A ends on `|` at index k and the user selects from k + 1, the new ending anchors on A's end. Item 25 fixes it.

**The load pass accepts overlapping endings.** `EndingLoadPass` asks only `Ending.spanning`, and `spanning` reads no other ending. Item 25 fixes it.

**An edit can leave two endings back to back.** Ending A ends on `|`, one note follows, and ending B opens after it. Deleting that note leaves each ending well formed by its own rule, so both stay. Item 27 fixes it.

**`Replacement.endingEffect` drops an effect.** Its Javadoc says at most one ending reports an effect. Ending A ends on note k and ending B anchors on note k + 1, which the anchor rule accepts. Replacing note k with `:|` gives A `CompensateSplit` and B `Invalidate`. `findFirst()` returns A's effect only. Item 25 makes the claim true for one replaced element; item 28 states it for a set.

**A split change deletes a note that ends the ending.** `Ending.checkReplacement`, condition 2, answers `CompensateEnd` for `:|` → `:|:` and for `:|:` → `:|` without reading the end. `EndingConfirms.applyCompensatingChange` then replaces the end with a new bar. When the end is a note, `isValidEnd` accepts it with either split, so no compensation is needed, and the note is lost. Item 26 fixes it.

**A selection replacement is judged against a line it does not produce.** `SelectionActionApplier.decideChanges` asks each element's `endingEffect` against the unchanged line. `apply` then replaces the elements one at a time, each with its compensation. Ending A has split `:|:` and end `:|`. The user selects both and applies `:|`. The split answers `CompensateEnd(end → |)`; the end answers `None`, because its split is still `:|:`. Apply sets the end to `|`, the split to `:|`, and the end back to `:|`. The last replacement answers `CompensateSplit`, which `outcomeFor` keeps, so an ending with split `:|` and end `:|` stays attached. Item 29 fixes it.

### Left from step 3, item 1

Step 3 made the index queries total on a registered span. Two checks that item deletes were missed. They go with this step, under that decision:

- `EndingBracketGeometry.computeBracketRanges` (`layout/EndingBracketGeometry.java:93`) returns an empty list when an index is not in the line, and its `@return` says so.
- `PasteSpanReconciliation.isRejectedByTarget` (`ui/clipboard/PasteSpanReconciliation.java:302`–`:307`) returns `false` for a negative or reversed index, with a comment about a malformed span.

## Decisions

Every item is accepted as its correct structure states. The execution plan is [step-4-plan.md](step-4-plan.md).

### The span's range

**1. A span's range is a query on the span.** `LineSpans.rangeOf` reads nothing of the registry, so the registry is not its owner. _Correct structure:_ `public final ElementRange range()` on `Span`, which reads the two index getters and throws what they throw. `LineSpans.rangeOf` is deleted.

- The hand-built ranges in the table above read `range()`. This includes `isRejectedByTarget`, whose dead check goes with it.
- The readers of `rangeOf` read `range()`: `LineSpans` (four sites), `Tuplet.outcomeFor`, `MusicEditOperations.hasOverlap`, `HairpinEndpoints.compute`, `RangeQueries.canToggleTuplet`.
- The sites that read both indices and then loop or compare read `range()` too: `Beam.notesInSpan` (`line.getElements(range())`), `Beam.replacementBreaksInvariant` (`range().contains`), `Ending.insertionAddsInteriorBar`, `StructuralStacker.collectNonRestSpannedColumns`, `PasteSpanReconciliation.reconcile`, `DynamicGrouper.clusterHairpins` (`Member.OfHairpin` holds the range), `EndingBracketGeometry.computeBracketRanges`, and `ScorePartwiseBuilder.SpanAction`, which takes an `ElementRange` in place of two `int`s.

**2. Two tests of "inside a range" are written by hand, and they are different relations.** `Span.strictlyContaining` (`dom/Span.java:426`) asks about an element in the line: the two bounds are outside, so the test is `begin < index < end`. `Ending.insertionAddsInteriorBar` asks about an insertion point: an insertion at `begin` lands in front of the range, and an insertion at `end` lands inside it, so the test is `begin < index <= end`. The code tests `< end`, which is the third defect above. _Correct structure:_ two relations on `ElementRange`:

- `containsExcludingBounds(int index)`. `Span.strictlyContaining` returns `range -> range.containsExcludingBounds(index)`.
- `insertionLandsInside(int index)`. `Ending.insertionAddsInteriorBar` asks `range().insertionLandsInside(insertedIndex)`.

`ElementRange` is row 6's.

### The span framework

**3.** `Span.isSameKindAs` **knows about** `Hairpin`**.** The base class tests for one subclass by name, so the rule for hairpins is in `Span`. _Correct structure:_ `Span.isSameKindAs` compares the concrete class only, and is not `final`. `Hairpin` overrides it and adds the kind test. `Displacement`'s Javadoc stays true.

**4.** `LineSpans.HairpinSpan` **and** `PlacedSpan` **are one shape** (`dom/LineSpans.java:203`, `:391`). Each one is a span with a range. `HairpinSpan`'s range is over the projection, and `PlacedSpan`'s range is over the line. The type says neither. _Correct structure:_ one private record, `SpanAt<S extends Span<?>>(S span, ElementRange range)`. Each use states in its own Javadoc what its range is over.

### Tuplets

**5.** `TupletLoadPass.firstPopulatedLineIndex` **is** `Song.firstNonEmptyLineIndex`**.** Both return the first line with an element, or `null`. `Line.isEmpty` is `elementCount() == 0`. _Correct structure:_ delete the private copy. `placeTuplets` calls `song.firstNonEmptyLineIndex()`.

**6. A tuplet's time scaling is written twice, with two rounding rules.** Both sites compute written × M ÷ N from the tuplet's ratio and grade. The MusicXML writer requires an exact result and throws otherwise. MIDI rounds each offset half up. _Correct structure:_ `Tuplet.performedTicks(long writtenTicks, RoundingMode rounding)`. `NoteBuilder` passes `RoundingMode.UNNECESSARY`, which throws `ArithmeticException` on an inexact result as the writer does now. `LineTrackBuilder` passes `RoundingMode.HALF_UP`. `Tuplet` is the owner, because it holds both the grade and the ratio. The writer's message that names `DIVISIONS` stays in `NoteBuilder`, in a catch around the call. This reaches rows 16 and 27.

**7. The "enough notes" rule is outside the validator.** This is the second defect above. _Correct structure:_ `TupletValidator` applies the rule under both strictnesses, as it applies the fermata rule, and answers `TOO_FEW_NOTES`. The count joins `SpanContext`, and item 8's accumulator makes it. `Tuplet.holdsEnoughNotes` and `MIN_NON_REST_NOTES` move to `TupletValidator`. The pre-check in `TupletLoadPass.place` is deleted, and so is the exception clause in `Change.Removed`'s Javadoc. The editor, the paste check and the load pass then apply one rule.

The rule counts pitched notes (`isPitchedNote`: every note except a grace note). Today it counts every element that is not a rest, so a grace note, a bar and a breath mark each count. With the new count, a tuplet over one note, a grace note and a rest is refused.

**8. The validator measures a span in two ways.** `describeSpan` runs four helpers over the range. The forward walk accumulates the same facts one element at a time in `OpenSpan`. Both read the beat barrier through `isBeatBarrier` and `beatDefinedAt` in the same order. Both start from a beat that includes the anchor's own definition, because `TempoResolver.beatUnitAt` starts its walk at the anchor. _Correct structure:_ one private accumulator, which holds the beat, the reasons, the written ticks and item 7's count, and has one method per element. `describeSpan` feeds it the range. `validateFrom` feeds it the walk. `writtenTicks`, `hasFermata`, `crossesBeatBarrier` and `crossesStructuralBoundary` are deleted.

**9. A beat barrier is** `TempoResolver`**'s rule.** `isBeatBarrier` reads `BeatChangeAttachment` and `TempoChangeAttachment` again. `beatDefinedAt`'s Javadoc says that it is "the single definition of that precedence". _Correct structure:_ `TempoResolver.splitsBeat(StaffElement element, BeatUnit runningBeat)`, beside `beatDefinedAt`. This reaches row 11.

**10. A written value found by its ticks is** `NotatableValue`**'s query.** _Correct structure:_ `static @Nullable NotatableValue withTicks(int ticks)` on `NotatableValue`, which holds the table. `NOTATABLE_BY_TICKS` and `buildNotatableByTicks` leave `TupletValidator`. The `null` is honest: most tick counts have no written value.

**11. Where a beat starts to divide in two is** `BeatUnit`**'s fact.** `conventionalSpans` halves down from a start length, the division root. An undotted beat (a quarter in 4/4) is its own root. A dotted beat (a dotted quarter in 6/8) first divides into three, so its root is a third of it (an eighth). The calculation reads only `beat.ticks()` and `beat.dotted()`. _Correct structure:_ `BeatUnit.binaryDivisionRootTicks()`. `DOTTED_BEAT_PARTS` moves with it. `conventionalSpans` keeps the halving loop and the multiples, because they are the tuplet rule. This reaches row 4.

**12.** `ModificationSession.registryTupletAnchoredAt` **restates a registry query.** It finds the tuplet that covers the index and then tests the anchor. _Correct structure:_ `findFirstSpan(Tuplet.class, Span.anchoredAt(index))`. This is row 12's file.

### Layout and engraving knowledge

**13. The tuplet's bracket geometry is layout.** _Correct structure:_ a new `layout/TupletGeometry`, beside `EndingBracketGeometry` and `HairpinEndpoints`. It takes every `Tuplet` member in the table above, except the three LilyPond numbers that go to `engraving` (see the table after item 16). `TUPLET_NUMBER_INK_HEIGHT_SS` goes with `TUPLET_FONT`, because `measureNumberInkHeightSs` measures it from that font. `bracketLeftEdgeXSs(Tuplet tuplet, double anchorXSs, double noteheadWidthSs)` reads `tuplet.anchorStemUp()` and `StemMetrics.thicknessSs()` itself, so the `stemSs` parameter is deleted. The span-width queries become static. `Tuplet` keeps `isNumberOnly` and `anchorStemUp`. This reaches rows 18 and 19.

**14. The trill glyph's placement is layout.** _Correct structure:_ a new `layout/TrillGeometry` that takes `glyphLeftXSs` and the span width. Both read `Trill.TRILL_GLYPH_WIDTH_SS`. `Trill` keeps the glyph's width and height, `intrinsicWidthSs` and `intrinsicHeightSs`.

**15. The hairpin's LilyPond lengths are engraving.** _Correct structure:_ `MINIMUM_LENGTH_SS`, `BOUND_PADDING_SS` and `BACK_TO_BACK_PADDING_SS` move to a new `engraving/HairpinMetrics`.

**16. The volta's lengths are layout.** _Correct structure:_ `VOLTA_TICK_HEIGHT_SS`, `LABEL_X_INSET_SS` and `LABEL_Y_OFFSET_SS` move to `layout/EndingBracketGeometry`, which already holds the label font and the label bounds.

For items 13 to 16, a constant goes to `engraving` when its Javadoc gives a LilyPond source, on the pattern of `BeamMetrics`. The `engraving` package Javadoc says it holds "the engraving measurements the program draws with … mostly ported from LilyPond". Every other member goes to `layout`, because `engraving` cannot import `dom` and imports no fonts.

| Member | Destination | Reason |
| --- | --- | --- |
| `Tuplet.ARM_EXTENSION_SS`, `BRACKET_ARM_HEIGHT_SS`, `MAX_SLOPE_FACTOR` | `engraving/TupletMetrics` | LilyPond values |
| `Hairpin.MINIMUM_LENGTH_SS`, `BOUND_PADDING_SS`, `BACK_TO_BACK_PADDING_SS` | `engraving/HairpinMetrics` | LilyPond values |
| `Tuplet.TUPLET_FONT`, `TUPLET_FONT_SIZE_SS`, `TUPLET_NUMBER_INK_HEIGHT_SS`, `numberInkWidthSs` | `layout/TupletGeometry` | The font, and measurements of it |
| `Tuplet.MIN_SPAN_WIDTH_SS` and the functions | `layout/TupletGeometry` | A collision-region floor, and functions that take a `Tuplet` |
| `Trill.glyphLeftXSs`, `getSpanWidthSs` | `layout/TrillGeometry` | Functions of column positions |
| `Ending.VOLTA_TICK_HEIGHT_SS`, `LABEL_X_INSET_SS`, `LABEL_Y_OFFSET_SS` | `layout/EndingBracketGeometry` | No LilyPond source. The label offsets place text in the label font |

**17. A beam's height estimate is engraving.** `Beam.estimatedBeamBottomAboveYSs` computes where the lowest beam of an upward stack sits above a head. It reads only engraving values: `Staff.HALF_SPACE_SS`, `StemMetrics.STEM_LENGTH_SS` and `BeamMetrics.beamStackHeightSs`. `graceStemTouchesBeamAbove` computes a grace stem's tip from `StemMetrics.GRACE_NOTE_STEM_LENGTH_SS`. _Correct structure:_ `BeamMetrics.upwardStackBottomYSs(StaffPosition highestHead, int stackCount)` and `StemMetrics.graceStemTopYSs(StaffPosition head)`. `Beam.direction` keeps the clearance and the decision. This reaches row 3.

### Endings

**18. The elements under each bracket are** `Ending`**'s.** This is the first defect above. _Correct structure:_ `Ending.firstBracket()` and `secondBracket()` return the `ElementRange` under each bracket. Item 19 makes every attached ending hold a note or a rest in each bracket, so neither bracket is empty and both queries are total. The second runs from the element after the split to the element before the end. One private rule says whether a bracket holds content. `deletionBreaksBrackets` reads the brackets, and so does the editor (item 19). `MidiSequenceBuilder.addLineSkippingFirstEndings` skips from the start of the first bracket through the split. `EndingBracketGeometry` reads columns, not elements, and does not change.

The first bracket runs to the element before the split. Where it starts depends on the anchor:

| Anchor | First bracket |
| --- | --- |
| A note or a rest | Starts at the anchor. Valid only when the first content element in front of it is a `SINGLE_BARLINE`, a note or a rest, or when the anchor starts its line |
| `SINGLE_BARLINE` | Starts at the element after the anchor. The bar opens the bracket from outside it |
| Any other bar | No ending. `\|\|`, `\|:`, `:\|`, `:\|:` and the final bar each open, close or end a section, and an ending does not start on one or immediately after one |

Grace notes, breath marks and key changes are not content elements, so the test in front of a note or rest anchor passes over them.

`Ending.opensOnItsOwn` accepts only `SINGLE_BARLINE`, so `openingElementIndex` applies the same rule. `openingElementIndex` walks back only from a note or rest anchor, and only to a `SINGLE_BARLINE`. The editor anchors a new ending on that element, and `EndingBracketGeometry` starts the first bracket there. An anchor that is a bar is its own opening element. `openingElementIndex` and the test in front of an anchor share one private walk back.

`Ending.checkReplacement` applies the same rule. This is the fifth defect above. Its condition 1 accepts a new anchor only when the table accepts it: a note, a rest or `SINGLE_BARLINE`. A replacement that puts a bar strictly inside the ending, at an element that is not the split or the end, removes the ending, as an insertion does (item 2). A replacement that leaves a bracket with no content removes the ending, as a deletion does, through the same private rule.

Edits keep the rule for the element in front of the anchor. `outcomeFor` tests it against the line after an insertion or a deletion (`projectedElements`), and `checkReplacement` tests it after a replacement and answers `Invalidate`. An edit that leaves `||`, `|:`, `:|` or `:|:` in front of a note or rest anchor removes the ending. The MusicXML writer has no `REPEAT_LEFT` anchor case.

**19. The rule for a well-formed ending is in the editor.** `validateEndingStructure`, `validateEndingRegionContent` and `MIN_CONTENT_ELEMENTS` state what a first/second ending may be: at least four content elements, one bar that closes a repeat before the end, a split and end pair that `isValidEnd` accepts, an anchor that item 18's table accepts, and content in each bracket. Each of these is statable without the editor. A bracket holds content when it holds a note or a rest; a grace note, a slide or a breath mark is not content. _Correct structure:_

- `Ending.spanning` is the one factory. It applies the whole rule for the editor and for both readers, and returns `null` otherwise. The class invariant states the rule, so every attached ending is well formed.
- `spanning` does not count content elements. The bracket rule implies the minimum of four: a note or rest anchor gives content, the split, content and the end, and a bar anchor gives five. It does not test for a second closing bar either, because that bar is a bar inside a bracket.
- A reader does not call `spanning`. It records a `PendingEnding(anchor, end, yOffsetSs)`, as it records a `PendingTuplet`. A new `EndingLoadPass`, on the pattern of `TupletLoadPass` and `BreathMarkLoadPass`, builds each pending ending or drops it with a `LOG.warn`, and reports each drop by line and range.
- `LoadMigrations` carries the pass's report and runs the passes in the order tuplets, endings, breath marks. `MigrationWindow` lists the dropped endings in their own group, with one reason: not a valid first/second ending.
- `canMakeFirstSecondEnding` keeps the selection work: the terminal extension and the question to `Song` about an open section (item 20). `spanning` tests overlap (item 25). `validateEndingStructure`, `validateEndingRegionContent` and `MIN_CONTENT_ELEMENTS` are deleted.

**20. The repeat structure of a song has no owner, and playback applies the wrong rule.** This is the last defect above. Two walks find the section that a position is in:

| Element found by the backward walk | `MusicEditOperations.hasEnclosingRepeat` (`ui/MusicEditOperations.java:974`) | `MidiSequenceBuilder.buildSequenceWithRepeats` |
| --- | --- | --- |
| `REPEAT_LEFT`, `REPEAT_LEFT_RIGHT` | Opens the section | Replays from here |
| `REPEAT_RIGHT` | Closes the previous section, so no section is open | Replays from the element after it |
| `DOUBLE_BARLINE`, `FINAL_DOUBLE_BARLINE` | Closes the previous section, so no section is open | Ignored. The walk continues back |
| The start of the song | Opens the section | Replays from here |

The editor's column is the musical rule. A `:|` that follows another `:|` needs a `|:` after the first one, which the editor writes as `:|:`. A `||` is a Fine. In a song where every `:|` has an open section, the two walks agree. They disagree only on a `:|` that has no open section.

_Correct structure:_

- `Song` owns the repeat structure through `public @Nullable ElementLocation repeatSectionStart(ElementLocation location)`. It returns the first element of the repeat section that is open just before `location`: the element after its `|:` or `:|:`, or the song's first element when the start of the song opens the section. The backward walk stops at a `:|`, a `||` or a final barline, and then no section is open and the query returns `null`. The walk is the body of `hasEnclosingRepeat`, which is deleted. When the opener is the last element of its line, the answer is on a following line.
- `canMakeFirstSecondEnding` asks `song.repeatSectionStart(new ElementLocation(lineIndex, begin)) != null`.
- `MidiSequenceBuilder` asks `repeatSectionStart` with the location of each `:|`, and its own backward walk is deleted. On `null`, playback ignores the `:|` and plays on. Otherwise playback continues from the returned element, so the `noteIndex = -1` case for the song start is deleted. The same is true of the closing half of a `:|:`. Its opening half still opens a section. A jump to another line rebinds the line, its element count, its track builder and its endings, which fixes the defect of a section that opens on an earlier line.
- The editor does not refuse a `:|` that has no open section, and a load does not change one. The song keeps what the user wrote.

**21.** `MidiSequenceBuilder` **restates** `closesRepeat()` (`midi/MidiSequenceBuilder.java:244`, `:248`). _Correct structure:_ `noteType.closesRepeat()` at both sites. `endingCovering` asks `ending.range().contains(elementIndex)` (item 1). This is row 27's file.

### Editor callers

**22.** `MusicEditOperations.hasOverlap` **tests each index against each ending.** The question is whether an ending overlaps the selection. _Correct structure:_ `spanning` answers it (item 25), and `hasOverlap` is deleted.

**23.** `MusicEditOperations.modifyStemDirection` **restates** `Beam.members()` (`ui/MusicEditOperations.java:1106`–`:1112`). It walks from the anchor to the end and skips grace notes. _Correct structure:_ it iterates `beam.members()`. `applyStemChange` takes an index, so each member gives `line.getElementIndex(member.note())`.

### Beams

**24.** `Beam.Member.stubRight` **restates** `BeamLevel.isCarriedBy` **over beam counts.** It compares a neighbour's `beamCount()` with `level.number()` (`dom/Beam.java:386`). `number()` is documented as the MusicXML beam number. _Correct structure:_ a private `Member.neighborCarries(int offset, BeamLevel level)`, which answers `false` past either end of the group. `stubRight` asks `carries(level)` and `!neighborCarries` on each side. `frenchShortening` compares counts across three notes, not levels, and keeps `beamCountAt`.

### Ending separation and compound replacement

**25. Two endings are never back to back.** _Correct structure:_ a note or rest stands strictly between an ending's end and the opening element of the next ending on the line. A grace note, a slide or a breath mark does not count, as in a bracket. The rule compares the next ending's opening element, not its stored anchor. So when A ends on note 3, B cannot anchor on note 3 or note 4, and B cannot open on a `|` directly after note 3.

- The rule is a private predicate on `Ending` over an element list and two indices. It is a line in the class invariant.
- `spanning` checks the candidate against the ending before it and the ending after it on the line. Every overlap fails the check, so `MusicEditOperations.hasOverlap` is deleted.
- `EndingLoadPass` judges pending endings in file order, so it drops the later ending of a back-to-back pair and reports it.
- With the invariant, one replaced element affects at most one ending: a replacement reaches an ending only at its own elements or at the first content element in front of its anchor, and the invariant puts that element in the gap.

**26. The ending rule is written twice.** `spanning` states it over the line. `outcomeFor` and `checkReplacement` state it again as one branch for each change shape, and their Javadoc says they remove an ending exactly when the change leaves it ill formed. _Correct structure:_ one private predicate on `Ending` judges an ending over an element list, its anchor index and its end index. `spanning` asks it over the line. `outcomeFor` asks it over `change.projectedElements()` at the projected indices of the anchor and the end, for all four change kinds; a deleted endpoint has no projected index, so the ending goes. Compensation is a rule on top, for a replacement only:

A well-formed projection answers `None`. An ill-formed one answers:

| Replaced | Condition | Answer |
| --- | --- | --- |
| The split and not the end | The projection with the end set to the matching type is well formed | `CompensateEnd` |
| The end and not the split | The projection with the split set to the matching type is well formed | `CompensateSplit` |
| Anything else | — | `Invalidate` |

The matching types are the ones condition 2 and condition 3 state now. A compensation is offered only when the projection is ill formed, so a note end never asks for one.

**27. A rule between endings belongs to the registry.** `docs/span-invalidation.md` says no span knows its siblings. _Correct structure:_ `LineSpans` splits the sweep into a decision and an action. The decision collects every span's answer and then examines the endings that survive, in line order, over the projection. It marks the later ending of each pair that item 25's predicate refuses. `sweep` acts on the decision. `ElementChange.removesConfirmableSpan` and the ending effects read the same decision, so a prompt and its edit cannot disagree.

**28. A replacement is a set of pairs.** _Correct structure:_ `ElementChange.Replacement` holds one or more `(index, newElement)` pairs. A single replacement is a set of one. There is no fifth change kind, which would need a second copy of every span's replacement rule. `Beam` and `Tuplet` apply their rule to each pair. `Hairpin` and `Ending` already judge the projection. `Line` applies a set with one sweep and one `ElementReplacement` record for each pair, so undo does not change. `Replacement.endingEffects()` returns one effect for each affected ending, because a set can reach two endings.

**29. A compensation is part of the change it compensates.** _Correct structure:_ a confirmed compensation adds its pair to the replacement, and the whole set lands as one change. `SelectionActionApplier` builds one replacement over every admitted element of the selection. It asks for the effects once and shows the usual prompt for each affected ending. A declined prompt cancels the whole action. A replacement that sets both the split and the end to a pair `isValidEnd` refuses answers `Invalidate`, with the usual prompt. `PreviewElementInserter` does the same for its one element. `EndingConfirms` keeps the prompts, and its methods that apply a compensation are deleted.
