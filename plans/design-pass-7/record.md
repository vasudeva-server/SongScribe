# Design Pass — Spans

Run by `/design-pass 7` — register row `7`, *Spans*.

**Target.** `src/main/java/songscribe/dom/`: `Span`, `SpanBound`, `SpanLookup`,
`SpanOutcome`, `LineSpans`, `NoteSpan`, `Beam`, `Crescendo`, `Diminuendo`, `Hairpin`,
`Ending`, `EndingValidationResult`, `Trill`, `Tuplet`, `TupletRatio`, `TupletValidator`,
`TupletLoadPass`. Tests: `src/test/java/songscribe/dom/SpanTest.java`,
`src/test/java/songscribe/dom/LineSpansTest.java`.

The row named `SlideZone`, which pass 5 deleted; its replacement, `Slide`, is a note
attribute on `NoteElement`, not a span. `LineSpans`, `NoteSpan` and `TupletRatio` were
named by no row. The user chose this target at step 0, and row 7's *Where* column now
reads the same.

**Status:** 🔄 in progress

## Baseline

| Measure | Before | After |
|---|---|---|
| Commit | `e40e512f` | |
| Production lines | 4965 | |
| Test lines | 735 | |
| Passing tests (`SpanTest` 3, `LineSpansTest` 35) | 38 | |

`./scripts/test.sh SpanTest` also runs `MusicXmlCrossLineSpanTest` (6 cases, row 16's), because
the Gradle filter matches the name as a suffix. Count the two classes apart.

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ✅ | ✅ | — | `findings-1-2.md`, *What it found* |
| 2 Class design | ✅ | ✅ | [step-2-plan.md](step-2-plan.md) | `findings-1-2.md`, *Decisions*. Settled while planning: `TieAddition`/`TieRemoval` stay as the tie's records, over `(Line, NoteElement)`; `Tie` is a derived record `(anchor, end)`. Two defects fixed in the plan's Phase 11: the MusicXML reader rounds offsets, and an ending's offset is not saved |
| 3 Unrepresentable states | ✅ | ✅ | [step-3-plan.md](step-3-plan.md) | `findings-3.md`: all 24 items accepted. Item 5, the factory checks only that a split exists. Item 11, the legacy reader drops an out-of-range grade with a warning. Item 22, `Span<E extends StaffElement>`. No known path leaves a registered span with a detached endpoint (item 1) |
| 4 Extraction | ✅ | ✅ | [step-4-plan.md](step-4-plan.md) | `findings-4.md`. Settled while executing, item 18: an ending anchors only on a note, a rest or a `SINGLE_BARLINE`, and a note or rest anchor needs a `SINGLE_BARLINE`, a note, a rest or the start of its line in front of it. The editor and the bracket geometry share that rule, and `volta-alignments.musicxml` holds one line per valid opening |
| 5 Contracts | ✅ | ✅ | [step-5-plan.md](step-5-plan.md) | `findings-5.md`: all 9 items accepted. Item 3, a pasted ending goes through `Ending.spanning`, and a refused one is removed and reported. Item 9, a hairpin never ends on a rest; a `Hairpin` factory judges every hairpin, including a new `HairpinLoadPass` |
| 6 Test triage | ✅ | ✅ | — | `findings-6.md`: 12 items accepted, no test discarded. Scope: every class the pass touched, in every package. Item 2, the Beam sweep has seven rows, and `Beam.pairBreaksInvariant` judges only whether the new element is beamable. Item 6, each kind's sweep sits in its own test class. Item 7, `ModificationSessionTest` holds the replay gate. No `TupletValidator.Reason` is dead: each has an input |
| 7 Test-only surface | ✅ | ✅ | — | `findings-7.md`: 5 items. Item 1, `EndingBracketGeometryTest` lists its closing types as literals, and `Ending.isValidEnd` is private. Item 2, `EndingSectionRuleTest` states each expected report location. Item 3, `StaffElementRun.nextNonGraceIndex` deleted; the Beam rework left it without a caller. Items 4 and 5 done in this pass: `UndoController.setPendingOpName`, `ChainEdit.lineInserted` and `lineRemoved` are private |
| 8a Suite gate | ⏳ | n/a | — | |
| 8b Visual gate | ⏳ | n/a | — | |
| 9 Diagrams | ⏳ | ⏳ | — | |
| 10 Coverage | ⏳ | n/a | — | |
| 11 Mutation | ⏳ | n/a | — | opportunistic |
| 12 Adversarial review | ⏳ | n/a | — | complete only when the user says it passed |
| 13 Close out | ⏳ | ⏳ | — | |

*Gate* is the state of the step's findings document: ⏳ not written · 📖 open for
review · ✅ resolved. A step whose *Gate* is not ✅ has made no code change, so a
resume that finds one 📖 reads the marked-up document rather than the tree.

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

## Findings claimed

Carry-forward items taken from the register at step 0, and what each turned out
to be. Deleted from the register when claimed.

1. `Tuplet.getNoteValue()` returns a `@Nullable ElementType`, standing in for a
   pitched note's written value. `NoteBuilder.buildTimeModification` (L442) converts
   through `ElementType.noteValue()`. Now that `dom.NoteValue` exists,
   `Tuplet.getNoteValue()` should hold one directly.
2. `Beam` names its members by index, so `BeamGroupRenderer.beamedNoteAt` narrows each
   end to a `NoteElement` with a throw nothing can reach. A beam that hands out its
   notes retires it.
3. `LineSpans.getSpans` promises "the order they were added", and every span query
   iterates in that order. Only the tie queries depend on it: `findTieAt` takes the
   first match, and a note can hold a tie in and a tie out. Every reader of hairpins
   either sorts by position or asks only whether one exists, and pass 6 deleted the
   sort in `applySpanRun` that tried to keep hairpin order across undo, which re-adds a
   span at the end of the list. Row 7 decides whether the registry promises an order
   at all, or whether the tie queries state the one they need.
4. `LineSpans.sweep` (`dom/LineSpans.java:383`) returns at once when
   `line.getSong().isReplaying()`. A batch replays whole and in order, so the companion
   span changes are already applied at each replayed element change, and the sweep
   would find nothing to do. No test can make the check fail. Row 7 decides whether the
   check is dead, or names the batch that needs it; row 15 owns the replay flag and can
   answer what a batch carries.
5. `Ending.hasRepeatSplit(Line, int anchorIndex, int endIndex)` takes two indices of
   one type, in an order the caller must know. `MeasureMapper:710` is the reader-side
   caller. Row 7 judges whether an ending's span should be the argument.

What each turned out to be:

1. `Tuplet.getNoteValue()` is gone; the value now sits in `TupletRatio` as a
   `NotatableValue(ElementType, Dots)`, still an `ElementType`. Step 2, item 6.
2. `BeamGroupRenderer.beamedNoteAt` is gone; `Beam.noteAt(int)` took its place and the
   beam still names members by line index. Step 2, item 7.
3. The registry promises no order. The MusicXML file does not depend on it. Step 5,
   item 4.
4. The check is live: undo of a deletion re-inserts one element at a time, through lines
   the rules would judge. Step 5, item 8.
5. Unchanged; the caller is now `MeasureMapper.java:724`. Step 3 (signatures); the
   invariant is step 2, item 13.

## Findings raised

Anything surfaced that was not this target's to fix, and anything a step turned
up that belongs to a later step's axis — the latter is picked up by that step's
findings document rather than argued where it was found. Harvested to the
register at step 13, before this file is deleted.

**For step 3 (types, guards, signatures)** — from step 1:

- The guards in `findings-1-2.md` with no producer found: `Ending.openingElementIndex`'s
  range check (`Ending.java:238`, returns its argument), the unreachable final
  `invalid()` in `MusicEditOperations.checkPrecedingElement`,
  `LineSpans.requireRangeOf`, `Tuplet.outcomeFor`'s cross-line throw, the
  tuplet-end-in-no-line branches (`TupletLoadPass.java:200`,
  `TupletValidator.OpenSpan.closesAt`, `isStructuralBoundary`), and the `TupletRatio`
  check that `MeasureMapper.buildTuplet` repeats.
- Adjacent same-type parameters: `Ending.hasRepeatSplit(Line, int, int)`,
  `Beam.noteTypeInLevel(int, int)`, `TupletValidator.validateFrom(Song, int, int, …)`,
  `TupletLoadPass.Change`'s four `int`s, `Ending.checkReplacement` and the replacement
  hooks' `(StaffElement, StaffElement)`, `Tuplet.bracketLeftEdgeXSs`'s mode `boolean`.
- The `Line` parameter on `Ending.getSplitIndex`, `getSplitElement`,
  `getOpeningElementIndex`, `Tuplet.isNumberOnly`, `hasValidSpan`.
- `EndingValidationResult`'s `boolean` and default fields; `EndingEffect.None` as a
  record with `INSTANCE`; `Ending.BracketRange`'s `int number` and `boolean`;
  `TupletValidator.SpanContext`'s `beatTicks` + `beatDotted` (a `BeatUnit`) and three
  booleans; `TupletLoadPass.Change.Removed.reason` nullable with no `null` producer;
  `Tuplet`'s `int` grade; `Beam`'s `int` level.

**For step 4 (extraction and placement)** — from step 1:

- Layout and engraving knowledge on `dom` span classes: `Tuplet` (a `java.awt.Font`,
  `TextMeasurement`, bracket edges and heights), `Trill` (glyph geometry), `Hairpin`
  (LilyPond lengths and paddings), `Ending` (volta constants,
  `BracketRange.inkRightXSs`).
- `LineSpans.HairpinSpan` and `LineSpans.PlacedSpan` are one shape.
- `MusicEditOperations.hasOverlap` tests `Span.containing` per index where
  `Span.overlapping` answers the question; `MidiSequenceBuilder.endingCovering`
  restates `findEndingAt` over a pre-filtered list.

**For step 5 (contracts)** — from step 1:

- Claimed finding 3 (`getSpans` order).
- Stale Javadoc: `TupletValidator.validateFrom` names `Line.addTuplet` (`:359`), which
  does not exist; `SpanBound` names "`Line`'s span merge" (`:29`) and links
  `Line#anchorIndexOf` (`:160`), which does not exist; `Span.getAnchorElementIndex`
  cites `Hairpin#outcomeFor` as a model caller (`:448`), but `Hairpin` calls the
  `require` form; `Tuplet`'s comment says the ratio is set once (`:100`) while
  `resolveRatio` sets it later.
- History in comments: `Span.isInvalidatedByChange` ("before `outcomeFor` existed"),
  `EndingValidationResult.PrecedingAction` ("Since issue #306 removed …").

**For step 4 (extraction and placement)** — from step 3:

- `Beam.noteTypeInLevel` reads only the member's `ElementType`; the level test belongs to
  the type, not to the beam.
- `new ElementRange(span.requireAnchorElementIndex(), span.requireEndElementIndex())` recurs
  at `MusicEditOperations.addHairpinToSelection`, `SpanTest` and `LineSpansTest`: a span's
  range is a query on the span.
- `ModificationSession.registryTupletAnchoredAt` restates `findFirstSpan(Tuplet.class,
  Span.anchoredAt(index))` as `findTupletAt` plus an anchor test.

**For step 5 (contracts)** — from step 3:

- If `findings-3.md` item 1 is accepted, `requireAnchorElementIndex` and
  `requireEndElementIndex` have no nullable twin to be named against.
- If item 1 is rejected, the comments that describe an endpoint "in whichever line" are
  stale: `HairpinEndpoints.compute`, the `ScorePartwiseBuilder.forEachSpanInRange` Javadoc,
  and the `LineSpans` class Javadoc.

**For step 7 (test-only surface)** — from step 6:

- `EndingBracketGeometryTest` filters its rows through `Ending.isValidEnd`. No production
  class outside `Ending` calls it. `EndingTest.testSpanningAcceptsAnEndOnlyOnANoteARestOrAnEndingTerminal`
  states the accepted end types, so the geometry test no longer needs the public method.

**For row 12 (Song)** — from step 6:

- `ModificationSessionTest` (`dom`) holds the three tests of the gate that
  `ModificationSession.endModification` puts on every rule, through the tie rule. Row 12 owns
  `ModificationSession` and may rewrite them.

**For the accidental and load rows** — from step 6:

- `LoadPassesTest.testABareNoteAfterABarlineSoundsWhatIsInForce…` and
  `testAParenthesizedAccidentalReadsAsAPlainOne` test the accidental stamp on
  `accidental-load.musicxml`. Their subject is not a span.
- `TupletValidator.validateFrom` has no direct test. `TupletBeatRuleTest` reaches it.
