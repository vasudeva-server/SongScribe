# Pass 7, Spans — step 6: triage the tests

Test paths are under `src/test/java/songscribe/`. Production paths are under `src/main/java/songscribe/`. HEAD is `5e84eae9`, tree clean.

## What it found

### Scope

The user chose every class the pass added or changed. That is 25 classes and 59 annotated methods. A parameterized method counts once, whatever its number of rows.

| Package | Classes |
|---|---|
| `dom` | `SpanTest`, `LineSpansTest`, `SpanPredicateTest`, `EndingTest`, `EndingSectionRuleTest`, `HairpinTest`, `HairpinLoadPassTest`, `TupletLoadPassTest`, `TupletBeatRuleTest`, `TieRuleTest`, `TieWalkTest`, `SongRepeatSectionTest`, `ChangeReportDeliveryTest`, `ElementChangeTest` |
| `io` | `LoadPassesTest`, `MusicXmlBeamTest`, `MusicXmlTieTest`, `MusicXmlCrossLineSpanTest`, `MusicXmlOffsetRoundTripTest` |
| `layout` | `EndingBracketGeometryTest`, `VoltaAlignmentFixtureTest` |
| `midi` | `MidiRepeatTest`, `MidiTieTest` |
| `ui` | `MusicEditOperationsTest`, `PasteSpanReconciliationTest` |

`FragmentTest`, `LineTest`, `BreathMarkLoadPassTest` and `MusicXmlAnnotationTest` changed only where a rename reached them. Their subjects belong to other rows. I did not triage them.

`LoadMigrationsTest` is the only test file deleted since the baseline. `LoadPassesTest` replaces it.

No test maps to no promise. The table discards nothing.

### What no test reaches

1. `Beam.outcomeFor` **has no case.** The only tests that name `Beam` are `LineSpansTest`, `MusicXmlBeamTest` and `MusicXmlCrossLineSpanTest`. `LineSpansTest` deletes every element of a beam, which is a whole-span deletion. No test deletes one endpoint, replaces a member, or shows an interior deletion keeping the beam. `Beam`'s class comment states the invariant, and `Beam.pairBreaksInvariant` decides an endpoint and an interior grace note differently.
2. `Tuplet.outcomeFor` **has two of four shapes.** `LineSpansTest.unbalancingEdits` holds an insertion and a modification. Both remove the tuplet. No row shows a deletion. No row shows a replacement. No row shows a keep, although the contract names three: an insertion at the anchor's index, an insertion just past the end element, and a swap of one non-duration element for another.
3. `TupletValidator` **has no test of its own.** Its public entry points are `validate`, `validateStated`, `validateDerived` and `validateFrom`. Its nine reasons meet three tests through other classes: `BAD_RATIO` and `TOO_FEW_NOTES` in `TupletLoadPassTest`, and `NO_CONVENTIONAL_SPAN` in `ChangeReportDeliveryTest`. No test reaches `EMPTY_SPAN`, `NOT_NOTATABLE`, `POWER_OF_TWO_RATIO`, `FERMATA`, `BEAT_BARRIER` or `STRUCTURAL_BOUNDARY`. No test shows `STRICT` and `LENIENT` giving different answers.
4. `Ending.spanning` **has no row for the end element's type.** `Ending`'s class comment lists eight invariants. Seven have rows in `EndingTest`. The missing one is "`isValidEnd` accepts the end's type". `ElementType.isEndingTerminal` accepts a single barline, a double barline, a final double barline and a left repeat. It refuses `REPEAT_RIGHT`. `Ending.isValidEnd` also accepts a note or a rest. No test makes `spanning` refuse an end.
5. `LoadPassesTest.testTheReportHoldsOneEntryForEachChangeTheLoadMade` **skips the hairpin pass.** It passes `List.of()` for the pending hairpins (`io/LoadPassesTest.java:99`). `LoadPasses.run` promises the order tuplets, endings, hairpins, breath marks, accidental stamp. The test pins four of the five entries.

### Two defects the tests exposed

1. `TupletValidator.validate` **says the opposite of its code.** Its Javadoc (`dom/TupletValidator.java:198`–`:201`) says the conventional span and the redundancy test "hold under `STRICT` only". The code reaches `NO_CONVENTIONAL_SPAN` and `POWER_OF_TWO_RATIO` under either strictness, and the comment at `:231` says so. `TupletLoadPass` relies on the code: a derived ratio that lands nowhere conventional drops the tuplet in a load. The Javadoc is the wrong one. The fix corrects the Javadoc sentence.
2. `Ending` **names a slide as an element.** `dom/Ending.java:76` says "a grace note, a slide or a breath mark is not content". `dom/Ending.java:433` says "Grace notes, slides and breath marks are transparent here". `ElementType` has no constant with `SLIDE` in its name, because a slide is an attribute of a note. The fix removes the word from both sentences.

### Duplication between test classes

| Repeated logic | Sites |
|---|---|
| A tuplet built as `new TupletRatio(N, new NotatableValue(value, Dots.NONE))`, with the normal-note count declared again in each class | `LineSpansTest:644`, `TupletBeatRuleTest:178` and `:184`, `ChangeReportDeliveryTest:143`, `TupletLoadPassTest:57` and `:61`, `LoadPassesTest:93`, `MusicXmlOffsetRoundTripTest:111` |
| A trill built from two elements of a line | `LineSpansTest` (`Trill::new`), `MusicXmlOffsetRoundTripTest:168`, `ChangeReportDeliveryTest:171`, `FragmentTest:75` |
| The loop that visits each note-on of a MIDI sequence | `MidiTieTest:83`, `MidiRepeatTest:105` |
| A dotted quaver | `MusicXmlBeamTest.dottedQuaver`, a local helper. `testing-unit.md` says to add a missing element to `StaffElementFactory` |

`SpanFactory` builds an ending and a hairpin. It builds no tuplet, trill or beam.

`EndingBracketGeometryTest.standardElements` calls `newInstance()` three times (`:338`, `:341`, `:344`). `testing-unit.md` forbids that call in a test. `StaffElementFactory.bar(ElementType)` exists.

`HairpinLoadPassTest` checks the pending offset in its case table. `TupletLoadPassTest` checks it in a separate test (`testTheAddedTupletTakesThePendingOffset`).

### Where a promise sits

`ModificationRule.apply` promises that a rule runs once, when the outermost bracket closes, and not during a replay or while tracking is suspended. `ModificationSession.endModification` enforces that for every rule, because `ModificationRules` only holds the list. `TieRuleTest` pins the gate in three tests, through the tie rule. `ChangeReportDeliveryTest` pins it again for the report. No `ModificationSessionTest` exists. `ModificationSession` belongs to row 12, _Song_.

Per kind, the edit sweep sits in three places today:

- `EndingTest.sweepCases` holds the ending's.
- `LineSpansTest` holds the tuplet's (`unbalancingEdits`) and the hairpin's (`structuralBarEdits`).
- `Beam` has none.

### What the table checks

The _Kind_ column uses the three kinds of `design.md`: **algorithm**, **invariant across calls**, **known corpus**. Two more entries appear:

- **file guard** is a guard reached by a damaged file. `design.md` allows a test for it.
- **exhaustiveness** is the assertion that a table's rows are the whole domain. `testing-unit.md` requires it.

## The table

| Test | Kind | Disposition | Why |
|---|---|---|---|
| `SpanTest.testRemovingASpanASecondTimeChangesNothingAndThrowsNothing` | invariant across calls | keep | `Span.removeFrom` promises the no-op |
| `SpanTest.testAnAdditionsDisplacementsAreAppliedOnceAcrossUndoAndRedo` | invariant across calls | keep | add, undo and redo agree on one merge |
| `SpanTest.testADeletionAndTheSpanItRemovesAreUndoneAndRedoneTogether` | invariant across calls | keep | one batch carries both |
| `LineSpansTest.testASpanAbsorbsRemovesOrJoins…` (14 rows) | algorithm | keep | the merge policy of each kind |
| `LineSpansTest.testCasesCoverEveryConcreteSpanKind` | exhaustiveness | keep | the leaves of `Span` are the rows of two tables |
| `LineSpansTest.testADeletionJoinsTheSameKindHairpins…` (7 rows) | algorithm | keep | the join and the opposite-kind block |
| `LineSpansTest.testTheJoinedHairpinCarriesTheFirstOnesOffsets` | algorithm | keep | `LineSpans.java:425` states the promise |
| `LineSpansTest.testAHairpinWhoseEndpointsBothSurviveADeletionIsLeftAsItIs` | algorithm | keep | the no-reshape case of the deletion |
| `LineSpansTest.testAnEditThatUnbalancesATupletRemovesIt` (2 rows) | algorithm | rewrite | becomes the tuplet sweep table of item 3, with the keep rows added |
| `LineSpansTest.testAnEditThatBringsAStructuralBarInsideAHairpin…` (derived from `ElementType.values()`) | invariant across calls | keep | remove, report and undo, for every structural bar |
| `LineSpansTest.testADeletionThatTakesEveryElementOfASpan…` (5 rows) | invariant across calls | keep | unreported removal, for every kind |
| `SpanPredicateTest.testEachPredicateAnswersAsItsFactoryStates` (23 rows) | algorithm | keep | endpoint inclusion of each predicate |
| `EndingTest.testSpanningAcceptsAnEndingOrNamesTheReason…` (25 rows) | algorithm | keep | seven of the eight invariants; item 4 adds the eighth |
| `EndingTest.testSpanningChecksWhatStandsInFrontOfTheAnchor` (8 rows) | algorithm | keep | the opening-predecessor rule |
| `EndingTest.testAnEditKeepsOrRemovesTheEndingAsTheInvariantRequires` (13 rows) | algorithm | keep | the ending's sweep |
| `EndingSectionRuleTest.testAnEditThatLeavesTheSplitNoOpenSection…` (6 rows) | algorithm | keep | remove and report, across lines |
| `EndingSectionRuleTest.testUndoRestoresTheEndingAndRedoRemovesItAgain` | invariant across calls | keep | one batch carries the removal |
| `HairpinTest.testSpanningBuildsAHairpinOverTheCandidate…` (11 rows and one row for each bar type) | algorithm | keep | reason and cause of each refusal |
| `HairpinTest.testConvergeWidensOverEverySameKindHairpin…` (4 rows) | algorithm | keep | the fixed point of the widening |
| `HairpinLoadPassTest.testAPendingHairpinIsAddedWithItsShifts…` (2 rows) | file guard | keep | a hairpin a file states and the rule refuses is dropped and reported |
| `TupletLoadPassTest.testAPendingTupletIsAddedWithItsRatio…` (6 rows) | file guard | rewrite | keep three rows: stated, derived, one dropped. The three `TOO_FEW_NOTES` rows move to item 1. Fold the offset check of the next row into the added rows |
| `TupletLoadPassTest.testTheAddedTupletTakesThePendingOffset` | file guard | rewrite | folded into the table above, as `HairpinLoadPassTest` does |
| `TupletBeatRuleTest.testABeatDefiningEditRemovesTheTuplets…` (8 rows) | algorithm | keep | the rule from the redefined place onward |
| `TieRuleTest.testATieStaysOnlyWhileItsNotesShareAPitch` (8 rows) | algorithm | keep | the tie rule |
| `TieRuleTest.testOneUndoRestoresTheEditAndTheTieItRemoved` | invariant across calls | keep | one batch carries the removal |
| `TieRuleTest.testAnEditOnTheNextLineIsJudgedForTheTieCrossingIntoIt` (3 rows) | algorithm | keep | the rule reads across one line break |
| `TieRuleTest.testTheRuleTrustsATieUnderSuspendedTracking` | invariant across calls | keep or move | the gate is `ModificationSession`'s: item 7 |
| `TieRuleTest.testTheRuleDoesNotRunWhileABatchIsReplayed` | invariant across calls | keep or move | item 7 |
| `TieRuleTest.testAnEditThatThrowsIsNotJudgedAndItsBatchIsStillDelivered` | invariant across calls | keep or move | item 7 |
| `TieWalkTest.testATiesEndIsTheFirstElementPastTheSeparators…` (10 rows) | algorithm | keep | the walk of `NoteElement.tie` |
| `TieWalkTest.testATieIsOwnedOnceByEachLineThatDrawsIt` | invariant across calls | keep | `Line.ownedTies` and `incomingTie` agree |
| `TieWalkTest.testAnEditAtALinesEdgeReachesTheLineBesideIt` (10 rows) | algorithm | keep | `Song.tieReach` |
| `SongRepeatSectionTest.testRepeatSectionStartFindsTheOpenerOrStopsAtACloser` (9 rows) | algorithm | keep | the walk back across lines |
| `ChangeReportDeliveryTest.testOneEditDeliversOneReportHoldingEveryRemoval` | invariant across calls | keep | three sources, one report |
| `ChangeReportDeliveryTest.testUndoAndRedoDeliverNoReport` | invariant across calls | keep | `docs/change-report.md` states it |
| `ChangeReportDeliveryTest.testAnEditUnderSuspendedTrackingDeliversNoReport` | invariant across calls | keep | `docs/change-report.md` states it |
| `ElementChangeTest.testTheProjectionAgreesWithItselfAboutEveryElement` (5 rows) | invariant across calls | keep | `projectedIndexOf` and `projectedElements` agree |
| `ElementChangeTest.testProjectionCasesCoverEveryShape` | exhaustiveness | keep | the permitted subclasses are the rows |
| `LoadPassesTest.testTheReportHoldsOneEntryForEachChangeTheLoadMade` | invariant across calls | rewrite | add the dropped hairpin: item 5 |
| `LoadPassesTest.testABareNoteAfterABarlineSoundsWhatIsInForce…` | known corpus | keep | accidental stamp on `accidental-load.musicxml`; its subject is not a span |
| `LoadPassesTest.testAParenthesizedAccidentalReadsAsAPlainOne` | known corpus | keep | same fixture and same subject |
| `MusicXmlBeamTest.testABeamGroupWritesEachLevelAsARunOrAHook` (7 rows) | known corpus | rewrite | the local `dottedQuaver` moves to `StaffElementFactory`: item 8 |
| `MusicXmlTieTest.testATieWithinALineAndATieAcrossALineBreakSurvive…` | invariant across calls | keep | write, then read |
| `MusicXmlTieTest.testOnlyANoteCarryingATieStartIsTied` (3 rows) | file guard | keep | a marker on a rest or a grace note |
| `MusicXmlCrossLineSpanTest.testASpanOpeningOnOneLineAndClosingOnTheNextIsDropped` (3 rows) | file guard | keep | the reader cannot state a cross-line span |
| `MusicXmlCrossLineSpanTest.testTheSameSpanOpeningAndClosingOnOneLineIsRead` (3 rows) | file guard | keep | the control that proves the line break is the cause |
| `MusicXmlOffsetRoundTripTest.testAnElementsXOffsetSurvives…` | invariant across calls | keep | write, then read; not a span |
| `MusicXmlOffsetRoundTripTest.testRelativeXIsIgnoredWhenTheDocumentDoesNotVouchForIt` | file guard | keep | an older document; not a span |
| `MusicXmlOffsetRoundTripTest.testASpansFractionalOffsetSurvives…` (6 rows) | invariant across calls | rewrite | the tuplet and trill builders move to `SpanFactory`: item 8 |
| `EndingBracketGeometryTest.testEachReachableAlignmentAnchorsToTheRuleElement` | algorithm | rewrite | replace three `newInstance()` calls: item 10 |
| `EndingBracketGeometryTest.testABracketClosingOnANoteRunsOnToTheFollowingBar` (6 rows) | algorithm | rewrite | same three calls |
| `EndingBracketGeometryTest.testDeclaredBarTypesKeepTheirKnownGeometry` | known corpus | keep | four literal X values |
| `VoltaAlignmentFixtureTest.testWrittenVoltaAlignmentsMatchTheCheckedInFixture` | known corpus | keep | the golden file a person approved |
| `MidiRepeatTest.testPlaybackFollowsTheRepeatMarkers` (6 rows) | known corpus | rewrite | the note-on loop moves to a shared helper: item 9 |
| `MidiTieTest.testATiedPairSoundsAsOneNote` (4 rows) | invariant across calls | rewrite | same loop: item 9 |
| `MusicEditOperationsTest.testASelectionFromTheFirstElementResolvesLikeAnyOther` (2 rows) | algorithm | keep | `CAN_ADD` and `EXTEND` |
| `MusicEditOperationsTest.testARefusalOfTheRangeIsIneligibleAndAnotherHairpinInTheWayIsBlocked` (3 rows) | algorithm | keep | `INELIGIBLE` and `BLOCKED` |
| `PasteSpanReconciliationTest.testAPastedEndingIsAddedAsTheOneItsRuleBuilt…` (3 rows) | algorithm | keep | accept or name the rule's reason |
| `PasteSpanReconciliationTest.testAPastedHairpinIsAddedAsTheOneItsRuleBuilt…` | algorithm | keep | shifts and offsets carry over |
| `BeamTest.testAnEditKeepsOrRemovesTheBeamAsTheInvariantRequires` (10 rows) | algorithm | add | item 2 |
| `TupletTest.testAnEditKeepsOrRemovesTheTupletAsItsContractStates` (12 rows) | algorithm | add | item 3 replaces the two-row table in `LineSpansTest` |
| `TupletValidatorTest.testValidateNamesTheFirstReasonItFails` (one row for each reason and strictness it applies to) | algorithm | add | item 1 |
| `TupletValidatorTest.testCasesCoverEveryReason` | exhaustiveness | add | item 1 |
| `EndingTest.testSpanningAcceptsAnEndOnlyOnANoteARestOrAnEndingTerminal` (one row for each bar type, plus a note, a rest and a grace note) | algorithm | add | item 4 |

## What needs deciding

Items 1 to 5 add or change cases. Items 6 and 7 say where a promise sits. Items 8 to 11 remove repeated test logic. Item 12 corrects two comments.

### 1. Add `TupletValidatorTest`

The class calls `validate`, `validateStated` and `validateDerived`. It states, for each of the nine `Reason` values, one input that gives it and the strictness it applies under. It adds one row that gives a valid result. It adds `testCasesCoverEveryReason`, which asserts that the rows name every `Reason`, so a tenth reason fails the build.

The arrangements are compact-notation lines. A fermata, a barline, a repeat, an interior breath mark and a beat change each need one.

**The question inside this item.** A row for a reason needs a real input. If no caller can produce `EMPTY_SPAN` or `NOT_NOTATABLE`, the row cannot be written, and the reason is dead code. Then <span id="rd-c1">the correct structure is to delete the reason from the enum and from `messageKey`, with its `Strings` key. I decide which after I try to build each input</span>. I report each reason that has no input before I delete anything.

If this item is accepted, `TupletLoadPassTest` loses its three `TOO_FEW_NOTES` rows and keeps one dropped row. The load pass then shows that it reports the validator's reason, and the validator's own table holds the arithmetic.

`TupletValidator.validateFrom` gets no direct test. `TupletBeatRuleTest` reaches it through every beat change, across lines.

### 2. Add a sweep table for `Beam.outcomeFor`

Rows, each on a line of quavers with a beam over an interior range:

1. Deleting the anchor removes the beam, with the endpoint-deleted reason.
2. Deleting the end removes it.
3. Deleting an interior member keeps it.
4. Replacing an interior note with a crotchet removes it.
5. <span id="rd-c2">Replacing an interior note with a rest removes it.</span>
6. <span id="rd-c3">Replacing an interior note with a grace note keeps it</span>.
7. <span id="rd-c4">Replacing an endpoint with a grace note removes it.</span>
8. Replacing a note outside the range keeps it.
9. An insertion keeps it.
10. A modification keeps it.

### 3. <span id="rd-c5">Complete the sweep table for `Tuplet.outcomeFor`</span>

The two rows in `LineSpansTest.unbalancingEdits` grow to twelve (item 6 says where they live). Rows:

- Insertion at the anchor's index keeps the tuplet.
- Insertion strictly inside removes it.
- Insertion at the end element's index removes it.
- Insertion just past the end element keeps it.
- Deleting an interior member removes it. A deletion before the bracket keeps it.
- Replacing a member with a note of another value removes it.
- Replacing a note with a rest of the same value removes it.
- Replacing a barline with another barline keeps it.
- Replacing an element outside the bracket keeps it.
- A dot added to a member removes it. A lyric change keeps it.

### 4. <span id="rd-c6">Add end-type rows to `Ending.spanning`</span>

One parameterized test over the end element's type. The bar rows come from `ElementType.values()`, so a new bar type reaches the test. The three other rows are a note, a rest and a grace note. The accepted set is written in the test as literals: a note, a rest, a single barline, a double barline, a final double barline and a left repeat. `REPEAT_RIGHT` and `REPEAT_LEFT_RIGHT` are refused.

**The question inside this item.** The reason key `spanning` gives for a refused end. I read the `spanning` contract at the time I write the test. If the contract does not name the reason, I amend the contract first and show you the amendment.

`EndingBracketGeometryTest` filters its rows through `Ending.isValidEnd`, which no production class outside `Ending` calls. Step 7 decides that surface. These rows give the accepted set a home that does not need the public method.

### 5. <span id="rd-c7">Put a dropped hairpin in the load-pass composition test</span>

`testTheReportHoldsOneEntryForEachChangeTheLoadMade` gets one pending hairpin that the rule refuses. Its entry sits between the ending entry and the breath-mark entry, which is the order `LoadPasses.run` promises. A pass dropped from `run`, or moved after the breath-mark pass, then fails this test.

### 6. Where a kind's edit sweep lives

<span id="rd-c8">**Option A**</span>**: each kind's sweep sits in that kind's test class.** `BeamTest` holds the beam's. `TupletTest` holds the tuplet's. `HairpinTest` receives `structuralBarEdits`. `EndingTest` already holds the ending's. `LineSpansTest` keeps what the registry does when a span is added: the merge rows, the hairpin joins, and the whole-span deletion.

- Pro: the four kinds share one layout. A kind's spanning rule and its sweep are in one class.
- Con: no real downside.

**Option B: the sweeps that are in** `LineSpansTest` **stay there, and the beam and tuplet tables join them.** `EndingTest` keeps the ending's.

- Pro: the file that already holds two sweeps holds the next two.
- Con: three kinds have their sweep in `LineSpansTest` and one has it in `EndingTest`, as today.

### 7. Where the three replay-gate tests live

`TieRuleTest.testTheRuleTrustsATieUnderSuspendedTracking`, `testTheRuleDoesNotRunWhileABatchIsReplayed` and `testAnEditThatThrowsIsNotJudgedAndItsBatchIsStillDelivered` pin a gate that `ModificationSession.endModification` enforces for every rule. `TieRuleTest` states them as the tie rule's.

<span id="rd-c9">**Option A:**</span> **move them now to a new** `ModificationSessionTest`**.** The tie rule stays the probe, because its removal is visible in the song. The class comment of `TieRuleTest` then states only the tie rule.

- Pro: the gate has one test, in the class of the code that enforces it.
- Con: `ModificationSession` belongs to row 12, which has not run. The new class tests a class that row's pass has not yet designed, so that pass may rewrite it.

**Option B: leave them in** `TieRuleTest` **and carry the finding to row 12.** I rewrite the class comment so that it says the tests run the session's gate through the tie rule.

- Pro: nothing lands in row 15's files before that row's pass.
- Con: a reader of `TieRuleTest` finds three tests whose promise is not the tie's, until row 12's pass moves them.

### 8. <span id="rd-c10">Builders for a tuplet, a trill, a beam and a dotted quaver</span>

`SpanFactory` gains `tripletOver`, `dupletOver`, `trillOver` and `beamOver`, each over a line and a range. The normal-note count of a triplet and a duplet is declared once, in `SpanFactory`. `StaffElementFactory` gains `dottedQuaver`. Every site in the _Duplication_ table calls the builder. `ChangeReportDeliveryTest.trillOver` and `MusicXmlBeamTest.dottedQuaver` are deleted.

`UnitTest.crescendoOver` wraps `SpanFactory.hairpinOver`. Both spellings appear in `SpanTest` and `LineSpansTest`. I leave `crescendoOver`; it has its own callers.

### 9. One helper for the note-on loop of a MIDI sequence

The loop at `MidiTieTest:83` and `MidiRepeatTest:105` becomes one test helper in the `midi` test package that returns the pitch of each note-on, in order. `MidiTieTest` counts them. `MidiRepeatTest` maps them to letters.

### 10. Replace `newInstance()` in `EndingBracketGeometryTest`

`standardElements` builds its elements with `StaffElementFactory.bar(type)` for a bar and `crotchet()` for a note. A closing type that is neither raises an error in the test.

### 11. Fold the offset check into `TupletLoadPassTest`

`testTheAddedTupletTakesThePendingOffset` is deleted. `pendingOver` receives the offset in every row, and the two rows that add a tuplet assert it. This is the shape `HairpinLoadPassTest` has.

### 12. Correct two comments

Both are defects, so the fix is made in this pass after the gate. Neither needs a decision on its shape.

1. `TupletValidator.validate` (`:198`–`:201`): the conventional span and the redundancy test hold under both strictnesses. Only the beat barrier and the structural boundary hold under `STRICT` alone.
2. `Ending` (`:76`, `:433`): remove "a slide" and "slides".

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: ok
    by: user
    at: "2026-09-30T00:31:39.064Z"
  rd-c2:
    body: Pointless, this is not possible in the UI.
    by: user
    at: "2026-09-30T00:32:48.519Z"
  rd-c3:
    body: The UI does not allow replacement by a grace note, pointless.
    by: user
    at: "2026-09-30T00:36:42.332Z"
  rd-c4:
    body: Not allowed by the UI, pointless.
    by: user
    at: "2026-09-30T00:36:59.207Z"
  rd-c5:
    body: ok
    by: user
    at: "2026-09-30T00:38:01.626Z"
  rd-c6:
    body: ok
    by: user
    at: "2026-09-30T01:00:15.434Z"
  rd-c7:
    body: ok
    by: user
    at: "2026-09-30T01:00:38.146Z"
  rd-c8:
    body: this
    by: user
    at: "2026-09-30T01:01:11.087Z"
  rd-c9:
    body: this
    by: user
    at: "2026-09-30T01:01:56.163Z"
  rd-c10:
    body: ok
    by: user
    at: "2026-09-30T01:02:18.185Z"
  rd-c11:
    body: Decisions 9-12 accepted
    by: user
    at: "2026-09-30T01:04:11.984Z"
    scope: document
