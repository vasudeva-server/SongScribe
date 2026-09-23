# Design Pass 7, Step 2 — Execute the Span Class Design

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Tie as a Note Attribute](#-phase-1-tie-as-a-note-attribute) | ✅ Complete | — |
| 2a | [Tie Callers Across a Line Break](#-phase-2a-tie-callers-across-a-line-break) | ✅ Complete | — |
| 2b | [Tie Rule at Bracket Close](#-phase-2b-tie-rule-at-bracket-close) | ✅ Complete | — |
| 2c | [Tie Queries in dom](#-phase-2c-tie-queries-in-dom) | ✅ Complete | — |
| 2d | [Tie Reach Across a Line Break](#-phase-2d-tie-reach-across-a-line-break) | ✅ Complete | — |
| 3 | [Tie Callers Within a Line](#-phase-3-tie-callers-within-a-line) | ✅ Complete | — |
| 4 | [One Registry, Line-Local Spans](#-phase-4-one-registry-line-local-spans) | ✅ Complete | — |
| 5 | [Span Kind Machinery](#-phase-5-span-kind-machinery) | ✅ Complete | — |
| 6 | [Span Kind Callers and Dead Verdict Members](#-phase-6-span-kind-callers-and-dead-verdict-members) | ✅ Complete | — |
| 7 | [Pending Tuplets and the Note-Value Ratio](#-phase-7-pending-tuplets-and-the-note-value-ratio) | ✅ Complete | — |
| 8 | [Pending Tuplet Plumbing](#-phase-8-pending-tuplet-plumbing) | ✅ Complete | — |
| 9 | [A Beam Hands Out Its Notes](#-phase-9-a-beam-hands-out-its-notes) | ✅ Complete | — |
| 10 | [Tree State Off LineElement](#-phase-10-tree-state-off-lineelement) | ✅ Complete | — |
| 11 | [Span Offsets and the Offset Round Trip](#-phase-11-span-offsets-and-the-offset-round-trip) | ✅ Complete | — |
| 12 | [Ending Bracket Ranges in the Layout Result](#-phase-12-ending-bracket-ranges-in-the-layout-result) | ✅ Complete | — |
| 13 | [Design Docs](#-phase-13-design-docs) | ✅ Complete | — |
| 14 | [Test Tree](#-phase-14-test-tree) | ✅ Complete | — |
| 15 | [Compile and Test Gate](#-phase-15-compile-and-test-gate) | ✅ Complete | — |
| 16 | [Manual UI Verification](#-phase-16-manual-ui-verification) | ✅ Complete | — |

## Rules for every phase

Every phase starts by reading this section and follows it throughout.

1. The working directory is
   `/Users/aparajita/Developer/projects/SongScribe-worktrees/design-pass-7`. Every path in
   this plan is relative to it; Java paths are under `src/main/java/songscribe/` unless they
   start with `src/`, `docs/` or `plans/`.
2. Read `.claude/rules/serena.md` and follow it: serena's `jet_brains_*` tools for all Java
   exploration, `jet_brains_find_referencing_symbols` before deciding where a symbol is used,
   and `Edit` for every code change. Search only inside `src/` and `docs/`.
3. Read `.claude/rules/java.md` before writing Java. Every number in logic is a named constant.
   Comments explain why and describe the code as it is; never write what changed, what was
   removed, or what an earlier design did.
4. Before writing or changing a Javadoc contract, read `.claude/guides/contracts.md`. Before
   adding or removing a `@Nullable`, read `.claude/guides/null-handling.md`. Before adding or
   removing a user-facing string, read `.claude/guides/strings.md`.
5. Line numbers in this plan are from commit `9c96f4f8` and drift as phases land. Locate every
   member by symbol, never by line.
6. Do not run `./scripts/compile.sh` or `./scripts/test.sh`. The tree compiles only when every
   implementation phase has landed; Phase 15 is the one compile.
7. Do not edit anything under `src/test/`. Phase 14 owns the test tree.
8. Never stage, commit, stash or amend.
9. The guards, parameter lists and constructor checks listed in
   `plans/design-pass-7/record.md` under *Findings raised → For step 3* stay as they are,
   except where a task below deletes the member that holds one. `Ending`'s factory and the
   `Line` parameters on `Ending` and `Tuplet` also stay; a later step owns them.
10. If a symbol a task names does not exist, or a caller set differs from the one the task
    states, report it in your final message and do the rest of the phase. Do not widen the
    change to cover it.

## ✅ Phase 1: Tie as a Note Attribute

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/NoteElement.java, src/main/java/songscribe/dom/Tie.java, src/main/java/songscribe/dom/NoteSpan.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/SpanLookup.java, src/main/java/songscribe/message/mutation/TieAddition.java, src/main/java/songscribe/message/mutation/TieRemoval.java, src/main/java/songscribe/undo/MutationReplayer.java  <br>
**Recommended model/effort:** Opus, high — new domain API with deep contracts, a derived cross-line relation, and an invalidation rule that must be recorded before the change it answers

### Tasks

1. Read *Rules for every phase*. Then read `docs/span-invalidation.md`, `docs/mutations.md`,
   `docs/undo.md`, and these members, which are the model this phase follows: `NoteElement`'s
   slide storage (`slide`, `getSlide`, `setSlide`, `removeSlide`), `StaffElementRun.hasGlissandoInto`,
   `LyricRun.removeGlissandoInto`, `LyricRun.repairNeighborsAfterInsertion`,
   `EditableRun.deleteRange`, `Line.setElement`, `LineSpans.sweep`, `ElementChange` and its four
   variants, `Song.addLine`, `Song.removeLine`, `Song.removeSpansBetweenNonAdjacentLines`, and all
   of `dom/Tie.java`.
2. The domain facts this phase builds on:
   - A tie joins a note to the next note. Its end is the first element after the anchor that is
     not a legal separator (`Tie.isLegalSeparator`: an element with no duration, other than a
     final double barline or a key change).
   - A tie crosses a line break: when the anchor's line ends before a non-separator, the walk
     continues at the start of the next line in the song. `MusicEditOperations.toggleBoundaryTie`
     creates such a tie, and the MusicXML reader reads one.
   - A tie joins two notes whose `ElementType.kind()` is `Kind.NOTE`. No other kind — a grace
     note, a rest, anything else — can be a tie's anchor or its end.
   - The tie rule: a tied note stays tied only while its end is a `Kind.NOTE` note at the same
     staff position that sounds the same alteration. An insertion of a same-pitch note between
     the anchor and its end keeps the tie, which then ends on the inserted note. Phase 2b states
     where and when the rule runs.
3. Write the contracts first, before any implementation, in `NoteElement` (full depth: the
   renderer, layout, MIDI, MusicXML and editor all rely on them):
   - `isTiedToNext()` — whether this note stores a tie.
   - `@Nullable Tie tie()` — the tie that starts on this note, with its end derived as above, or
     `null` when this note stores no tie. State that the end may be in the next line.
   - `@Nullable Tie incomingTie()` — the tie that ends on this note: walk back over legal
     separators, into the previous line when this line starts first; the first non-separator is
     the candidate anchor, and the answer is its `tie()` when that tie ends on this note.
4. Rewrite `dom/Tie.java` as `public record Tie(NoteElement anchor, NoteElement end)`. It is not a
   `Span`, nothing stores it, and nothing registers it; `NoteElement.tie()` makes one on demand.
   Its class Javadoc states that two ties made from the same two notes are equal, because
   `NoteElement` compares by identity. Keep, with their bodies and Javadoc: the static
   `isLegalSeparator(ElementType)`, `isAbove()`, `arcSign()` and the private `tieArcDirection()`,
   reading `anchor()` and `end()`. Delete the constructor over `NoteSpan`, `additionRecordFor`,
   `removalRecordFor`, `createNoteCopy`, `Endpoints`, `resolveIn`, `isInvalidatedByInsertion` and
   `isInvalidatedByReplacement`. Put the same-pitch test from task 2 on `Tie` as a static
   query over two notes, so the tie rule and nothing else calls it.
5. Store the tie on `NoteElement` as one private field, "tied to the next note", beside `slide`.
   It is note state: carry it through the same copy path `slide` takes (`copyStateFrom` and
   whatever else copies `slide`), so a replacement built from a copy of a tied note is tied too.
   The raw setters are package-private and are called only by the recorded route in task 6.
6. Record a tie through `Line`: `Line.addTie(NoteElement anchor)` and
   `Line.removeTie(NoteElement anchor)`, each one `applyChange` with its record and the raw flag
   write inside the mutator, the way `LineSpans.add` records a span. Contract both
   (preconditions: `anchor` is in this line; `removeTie` on an untied note records nothing).
7. Retype `message/mutation/TieAddition` and `TieRemoval` to `(Line line, NoteElement anchor)`.
   Each implements `Mutation` and `LineScopedMutation` and no longer implements `SpanMutation`.
   In `undo/MutationReplayer.applyUndo` and `applyRedo`, the two arms call `Line.addTie` or
   `Line.removeTie` on the record's line in the direction the arm replays, as the span arms call
   `addTo` and `removeFrom`.
8. Delete `Song.removeSpansBetweenNonAdjacentLines`.
9. Remove `Tie` from the span hierarchy: `NoteSpan` permits `Beam` only; delete every
    `Tie`-specific sentence from `Span`'s and `NoteSpan`'s Javadoc.
10. Delete the tie queries from `dom/SpanLookup.java`: `findTies`, `findTieAt`, `findExactTie`,
    `findTieBetween`, `findTiesTouching`, `sameTieAt`. Their callers move to `NoteElement.tie()`
    and `incomingTie()` in Phases 2a and 3.
11. In `dom/LineSpans.java`, delete the cross-line machinery: `otherLineOf`, and the other-line
    branches of `appendChild` and `removeChild` (each becomes a plain add or remove on `spans`).
    Rewrite the class Javadoc and the `spans` field comment so they state that every span lies in
    one line and sits in that line's registry only.

## ✅ Phase 2a: Tie Callers Across a Line Break

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/layout/ElementColumn.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/ui/component/score/LineRenderer.java, src/main/java/songscribe/ui/renderer/TieRenderer.java, src/main/java/songscribe/ui/renderer/LineInvariants.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/hit/HitTarget.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/ui/component/score/PitchShifter.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Opus, high — each caller decides which half of a line-crossing tie it owns, which the old per-line bounds decided for it

### Tasks

1. Read *Rules for every phase*. Read `dom/Tie.java` and the `tie()`, `incomingTie()` and
   `isTiedToNext()` contracts on `dom/NoteElement.java`, and `Line.addTie` / `Line.removeTie`.
   These are the whole tie API; `Tie` is a record `(NoteElement anchor, NoteElement end)`, equal
   when made from the same two notes. No `SpanBound`, `anchorIndexOf`, `endIndexOf` or registry
   tie query may appear in the code this phase writes.
2. The ownership rule every drawing and hit-testing caller follows: a line owns the tie that
   starts on one of its notes, drawn to its end or, when the end is in the next line, to this
   line's right edge; and it owns the incoming half of a tie whose anchor is in the previous
   line, drawn from its left edge to the end note.
3. `layout/ElementColumn.resolveTie` and `layout/LayoutEngine.calculateTies` / `tieLayout`:
   iterate the line's notes, take `note.tie()` and `note.incomingTie()` under the rule in task 2,
   and resolve columns from the notes' own positions. `LayoutResult` keeps its `Map<Tie, TieLayout>`;
   a line's layout carries one entry per tie or tie half it owns.
4. `ui/component/score/LineRenderer.renderTies` and `previewShiftStartOf`,
   `ui/renderer/TieRenderer` (`renderTie`, `determineTieColor`, `endpointColor`) and
   `ui/renderer/LineInvariants` (`playingTieSpan`, `playingTieAnchorBound`, `playingTieEndBound`):
   replace the stored `SpanBound`s with the notes of the playing `Tie`; an endpoint is colored
   when its note is in this line.
5. `midi/LineTrackBuilder.addNoteMessages`: a note sounds a note-on when `incomingTie()` is
   `null`, and a note-off when `tie()` is `null`. Delete the registry lookup and the comment that
   explains the per-line bounds.
6. `ui/selection/SelectionCoordinator.isSelected` and `ui/component/ScoreView.repaintTieHalves`:
   compare ties by record equality, and repaint the anchor's line and the end's line.
7. `hit/HitTarget.Tie`: keeps wrapping a `dom.Tie`. Its `owner()` answers the anchor note, and
   state in its Javadoc that a tie is owned by its anchor note. Check each caller of `owner()` for
   a tie target (`SelectionCoordinator.revalidateDecorationSelection` → `isOnLine`) and make a
   tie target on either of its two lines read as on that line.
8. `ui/MusicEditOperations`: `toggleTieInRange`, `toggleBoundaryTie`, `modifyStemDirection` and
   `toggleBeamWithPredecessor` use `Line.addTie` / `Line.removeTie` and `NoteElement.tie()`.
   "The notes at these two indices are joined by the same tie" is
   `first.tie() != null && first.tie().equals(second.incomingTie())` over the two notes.
9. `ui/selection/RangeQueries`: `tieEntryIndex`, `tieExitIndex`, `canToggleTie` and
   `shouldConnectTieSelection` use `Tie.isLegalSeparator`, `NoteElement.tie()` and
   `incomingTie()`.
10. `ui/component/score/PitchShifter.queueTieSpan` and `addTiePartnersOf`: a note's tie partners
    are `tie().end()` and `incomingTie().anchor()`.
11. `ui/component/ScoreViewController.mutatedSpans` and `spanReaches` exist to repaint the second
    line of a span that crosses a line break, and only a tie ever did. Every span now lies in one
    line, so delete the span part. Phase 2d owns the repaint of a tie that crosses a line
    break. Nothing then reads `message/mutation/SpanMutation`; Phase 6 deletes it.

## ✅ Phase 2b: Tie Rule at Bracket Close

**Status:** Complete  <br>
**BlockedBy:** 1, 2a  <br>
**Files:** src/main/java/songscribe/dom/Tie.java, src/main/java/songscribe/dom/NoteElement.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/ModificationSession.java  <br>
**Recommended model/effort:** Opus, high — moves the tie rule to the close of the outermost modification bracket and writes the user-confirmed tie contract; the hook must record into a still-open batch and stay out of replay and suspended tracking

### Tasks

1. Read *Rules for every phase*, `docs/mutations.md` and `docs/undo.md`. Then read all of
   `dom/Tie.java`; `NoteElement`'s `isTiedToNext`, `tie`, `incomingTie`, `tieToNext`, `untie`,
   `copySubtypeStateFrom`, `applySubtypeConversionFrom` and class Javadoc; `Line`'s `addTie`,
   `removeTie`, `applyTieRule`, both `applyTieRuleIntoNextLine`, `judgeTie` and the four change
   primitives that call `applyTieRule`; `Song.addLine`, `Song.removeLine` and `Song.applyChange`;
   and `ModificationSession`'s `endModification`, `withReplay` and `withoutMutationTracking`.
2. The contract. The user confirmed every clause below. Write it as Javadoc on the members named
   before you change any code:
   - **`Tie`**: a tie joins two notes whose `ElementType.kind()` is `Kind.NOTE`. No other kind
     can be its anchor or its end. The compact constructor throws `IllegalArgumentException` when
     either note's kind is not `Kind.NOTE`, so no `Tie` that breaks the rule can exist.
   - **`NoteElement.isTiedToNext()`**: `true` only on a `Kind.NOTE` note. `Line.addTie` states
     this as a precondition and does not check it: its callers convert at the boundary
     (`RangeQueries.canToggleTie` for the user, and the two file readers in Phase 3).
     `copySubtypeStateFrom` and `applySubtypeConversionFrom` carry the flag only when the note
     that receives it is `Kind.NOTE`, so a note converted to a grace note is not tied.
   - **`NoteElement.tie()`**: also `null` when the element the walk reaches is not a `Kind.NOTE`
     note.
   - **The tie rule** runs one time, when the outermost modification bracket closes, before the
     batch is delivered and while the bracket is still open, so each removal is recorded in the
     same batch. It judges every tied note in each touched line, and in the line before each
     touched line. A touched line is a line that a `LineScopedMutation` in the batch names, the
     line of a `LineInsertion`, and, for a `LineDeletion`, the line that now sits at its
     `lineIndex`, when there is one. A tie stays only when its anchor and its end are both
     `Kind.NOTE` notes at the same staff position that sound the same alteration; otherwise
     `Line.removeTie` removes it. The removals come after the changes that broke the ties, and
     reverse-order undo is still correct: undo restores the ties first, against the final
     state, where every anchor is still in its line. A tie that the batch added is judged like
     any other.
   - The rule does not run during replay, which applies the removals the batch recorded, or
     while mutation tracking is suspended, which covers a file load; a loaded tie is trusted.
   - Every change and every walk restamp in the bracket has landed when the rule runs, so every
     note is stamped. The pitch test has no `AccidentalState.Adopting` clause.
3. `Tie`: replace `soundsSamePitch` with the rule's test over an anchor and the element its walk
   reaches: both are `Kind.NOTE` notes, at the same staff position, sounding the same
   alteration. It stays package-private, and the rule is its only caller.
4. `Line`: delete `applyTieRule(ElementChange)`, both `applyTieRuleIntoNextLine` overloads, and
   their calls in the four change primitives. Add one package-private query that judges every
   tied note of this line against the element its walk reaches, and removes each tie that fails
   through `removeTie`. Rewrite each primitive's Javadoc that names the tie rule or a companion
   tie change.
5. `Song`: delete the tie-rule calls in `addLine` and `removeLine`, and rewrite their Javadoc and
   pseudo-code. Add a package-private method that takes the batch, finds the touched lines as
   task 2 defines them, and runs the `Line` query of task 4 over each touched line and the line
   before it.
6. `ModificationSession.endModification`: when the outermost bracket is about to close, and the
   batch is not empty, and the song is neither replaying nor suspended, call the `Song` method of
   task 5 before the depth reaches zero, so its removals land in the batch that is delivered.
7. Rewrite `NoteElement`'s class Javadoc paragraph on the tie rule to state task 2.
8. Read `ui/edit/GraceModeManager`'s insertion with tracking suspended. Report in your final
   message what it does to a tie whose anchor is the note before the inserted grace note. Do not
   change `GraceModeManager`.

## ✅ Phase 2c: Tie Queries in dom

**Status:** Complete  <br>
**BlockedBy:** 2b  <br>
**Files:** src/main/java/songscribe/dom/Tie.java, src/main/java/songscribe/dom/NoteElement.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/component/score/PitchShifter.java, src/main/java/songscribe/ui/component/score/LineRenderer.java  <br>
**Recommended model/effort:** Sonnet, medium — moves three queries onto the types they describe, removes one duplicated walk, and makes a stem flip follow ties across a line break, with every target named below

### Tasks

1. Read *Rules for every phase*. Read `dom/Tie.java`, `RangeQueries.areTied`, `tiePartnersOf`,
   `tieEntryIndex`, `tieExitIndex` and `boundaryTieAt`, and `LayoutEngine.tiesDrawnOn`.
2. Each query goes on the type whose value it is a function of:
   - `RangeQueries.areTied(first, second)` becomes `NoteElement.isTiedTo(NoteElement next)`:
     this note's tie ends on `next`.
   - `RangeQueries.tiePartnersOf(note)` becomes `NoteElement.tiePartners()`: the end of this
     note's tie and the anchor of its incoming tie, where each exists.
   - `LayoutEngine.tiesDrawnOn(line)` becomes `Line.ownedTies()`, with the ownership rule in its
     Javadoc: the tie that starts on each of this line's notes, and the incoming tie whose anchor
     is in the previous line.
   Move each Javadoc with its query. Update every caller: `RangeQueries.canToggleTie` and
   `shouldConnectTieSelection`; `MusicEditOperations.toggleBeamWithPredecessor`,
   `toggleTieInRange`, `toggleBoundaryTie` (with its `{@link}`) and `modifyStemDirection`;
   `PitchShifter.buildPitchShiftGroup`; `LayoutEngine.calculateTies` (with its `{@link}`) and
   `LineRenderer.renderTies` (with its `{@link}`).
3. **The walk exists once.** `RangeQueries.tieEntryIndex` and `tieExitIndex` repeat the walk in
   `Tie.firstNonSeparatorFrom` and `Tie.lastNonSeparatorIndexBefore`. Make the `Tie` walk queries
   public, with an index form of `firstNonSeparatorFrom` for the entry side, and build
   `firstNonSeparatorFrom` on it. Delete `tieEntryIndex` and `tieExitIndex`; `boundaryTieAt` and
   its Javadoc use the `Tie` queries. Before you delete them, compare each one with the `Tie`
   query that replaces it. If they answer differently for any input, report the difference and
   keep the `RangeQueries` behavior at that call site. `ScoreViewController.lineEnteredByTieFrom`
   also calls `tieExitIndex`; Phase 2d deletes that method.
4. **A stem flip follows the tie into the next line**, as `PitchShifter.buildPitchShiftGroup`
   does. `MusicEditOperations.modifyStemDirection` flips every note in `tiePartners()`, following
   the partners transitively, in whichever line each one is. Record each partner's change on
   that partner's own line, inside the same modification bracket, so the flip is one undo step.
   Delete the check that skips a partner outside the selected line, and rewrite the Javadoc to
   state that a flip reaches every note joined by ties.

## ✅ Phase 2d: Tie Reach Across a Line Break

**Status:** Complete  <br>
**BlockedBy:** 2b, 2c  <br>
**Files:** src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Sonnet, medium — adds one model query in the form `keyMoveReach` already has, and swaps the view's tie reach onto it

### Tasks

1. Read *Rules for every phase*. Read `Song.keyMoveReach`, and `ScoreViewController`'s
   `linesReached`, `keyMoveReach`, `crossLineTieReach`, `lineEnteredByTieFrom` and the panel loop
   that reads them.
2. A line draws the half of a tie that crosses a line break, so an edit near one edge of a line
   can change what the line beside it draws while the edit's record names only its own line.
   Examples are deleting a tied last note, inserting a same-pitch note after an anchor, and
   flipping the stem of a note of a crossing tie.
3. Write the contract first: `Song.tieReach(Mutation)`, beside `keyMoveReach`, returns the lines
   other than the one a record names whose drawn ties the record can change. For a record that
   names line L at a position p — an insertion's or a replacement's index, a deletion's first
   index, a modification's index, a `TieAddition`'s or `TieRemoval`'s anchor index:
   - L's next line, when there is one and p is at or after L's last non-separator, or L has no
     non-separator;
   - L's previous line, when there is one and p is at or before L's first non-separator, or L
     has no non-separator.
   Every other record reaches no line. Use the public `Tie` walk queries from Phase 2c.
4. `ScoreViewController`: replace `crossLineTieReach` with `linesReached` over
   `message.getSong()::tieReach`, as `keyMoveReach` does. Delete `crossLineTieReach` and
   `lineEnteredByTieFrom`, and update every Javadoc that names them.

## ✅ Phase 3: Tie Callers Within a Line

**Status:** Complete  <br>
**BlockedBy:** 1, 2a, 2b, 2c, 2d  <br>
**Files:** src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java, src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/layout/stacking/NoteAttachedStacker.java, src/main/java/songscribe/layout/HitRegionBuilder.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/ui/clipboard/Fragment.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/layout/LayoutResultBuilder.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Sonnet, medium — each site maps onto the tie API Phase 1 defined, with the mapping stated per site

### Tasks

1. Read *Rules for every phase*. Read `dom/Tie.java` (a record `(NoteElement anchor, NoteElement end)`,
   not a span, joining two `Kind.NOTE` notes only), the `tie()`, `incomingTie()` and
   `isTiedToNext()` contracts on `dom/NoteElement.java`, and `Line.addTie` / `Line.removeTie`.
   `Line.addTie`'s precondition is that the anchor is `Kind.NOTE`, and a reader is a boundary: it
   converts, so it never calls `addTie` on any other kind.
2. `io/musicxml/MeasureMapper.resolveTies`: for each `Kind.NOTE` note whose `<tied>` markers
   (`NoteMapper.tieMarker`) carry a start, call `Line.addTie(note)` on the note's line; a start on
   any other kind is ignored. Do not pair a start with a stop: the end is derived, and the writer
   writes a stop only on the start's next note. Delete the open-start state, the stop handling
   and both warnings.
3. `io/musicxml/ScorePartwiseBuilder.applyTies` and `addTie`: for each note, write the start
   `<tie>` and `<tied>` when `note.tie()` is not `null` (orientation from `Tie.isAbove()`), and
   the stop when `note.incomingTie()` is not `null`. Delete the per-line bound resolution and the
   comment that explains it.
4. `io/LineIO.LineReader.createTiesFromPendingPairs`: for each pending pair whose anchor is a
   `Kind.NOTE` note, call `Line.addTie` on it; a pair on any other kind is ignored, and the
   pair's end index is not used.
5. `layout/stacking/NoteAttachedStacker.seedTieBounds` and `layout/HitRegionBuilder.addTies`:
   iterate the line's notes and their `tie()` / `incomingTie()`, reading `arcSign()` on the record.
   Take the tie set from the `LayoutResult` entries where one exists, so these two agree with
   `LayoutEngine` on which ties a line owns.
6. `ui/clipboard/PasteSpanReconciliation.reconcile`: delete the `case Tie` branch and every other
   tie decision in the paste path. A tie is note state and travels with the pasted notes, and the
   tie rule judges every tie a paste touches when the paste's bracket closes (Phase 2b), including
   a tie that spans the paste point. `ui/clipboard/Fragment.capture`: reword the Javadoc sentence
   about a clipped tie.
7. `layout/LayoutResult` and `LayoutResultBuilder`: keep `Map<Tie, TieLayout>`; update any
   Javadoc that calls a tie a span.
8. `ui/component/ScoreViewController.deleteSelectedTarget`: the `HitTarget.Tie(var tie)` arm calls
   `line.removeTie(tie.anchor())` on the anchor's line inside the same `withModification` with
   `OpNames.removeTieLabel()`.

## ✅ Phase 4: One Registry, Line-Local Spans

**Status:** Complete  <br>
**BlockedBy:** 2a, 3  <br>
**Files:** src/main/java/songscribe/dom/SpanLookup.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/SpanBound.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/NoteSpan.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/ElementChange.java, src/main/java/songscribe/layout/stacking/DynamicGrouper.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java, src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Sonnet, medium — a fold, two deletions and a predicate rewrite whose exact semantics are stated below

### Tasks

1. Read *Rules for every phase*. Every span now lies in one line and sits only in that line's
   registry: a tie is no longer a span. So an endpoint of a span in line L's registry is either in
   L or in no line (detached).
2. Fold `dom/SpanLookup.java` into `dom/LineSpans.java`: move each remaining method onto
   `LineSpans` as a public method with its Javadoc, reworded where it names `SpanLookup`. Do not
   move `findEndingAt` or `isInsideAnyEnding`, which nothing calls; delete them. `LineSpans` no
   longer implements anything. Delete `SpanLookup.java`.
3. Delete `dom/SpanBound.java`, and `LineSpans.anchorIndexOf`, `endIndexOf` and `boundOf`.
4. Rewrite `Span.IndexPredicate` as `boolean test(@Nullable Integer anchorIndex, @Nullable Integer endIndex)`,
   where `null` is an endpoint in no line. `LineSpans`'s query loop passes
   `span.getAnchorElementIndex()` and `span.getEndElementIndex()`. Each factory keeps its current
   answer for an in-line or absent bound:
   - `containing(i)`: both non-null, `anchor <= i <= end`.
   - `overlapping(range)`: `(anchor == null || anchor <= range.end()) && end != null && end >= range.begin()`.
   - `overlappingBeyondEndpoint(range)`: `overlapping(range)`, and not `end == range.begin()`,
     and not `anchor == range.end()`, where a `null` bound equals nothing.
   - `strictlyContaining(i)`: `containing(i)`, and neither bound equals `i`.
   - `exactly(range)`: both non-null, `anchor == range.begin()` and `end == range.end()`.
   Delete every sentence in these Javadocs about off-edge bounds, halves or neighboring lines.
5. `LineSpans.rangeOf` builds its range from the span's own two index getters.
   `LineSpans.judgeableSpans` keeps the spans whose two index getters are both non-null.
   `ElementChange.removesConfirmableSpan` keeps calling `judgeableSpans`; update its Javadoc if it
   names a bound, and delete its sentence about a tie that an insertion displaces: a tie is not a
   span.
6. Move every remaining caller of the deleted members to the span's own index getters: at least
   `layout/stacking/DynamicGrouper.clusterHairpins` and `ui/clipboard/PasteSpanReconciliation.reconcile`
   (each `instanceof SpanBound.At(var i)` becomes a non-null check). Run
   `jet_brains_find_referencing_symbols` on `SpanBound`, `LineSpans/anchorIndexOf` and
   `LineSpans/endIndexOf` before deleting them, and report any caller outside this phase's
   **Files**.
7. `Span`: rewrite the Javadoc of `isIn`, `getAnchorLine`, `requireAnchorElementIndex`,
   `requireEndElementIndex`, `getAnchorElementIndex` and `getEndElementIndex` without cross-line
   spans or `LineSpans#anchorIndexOf`. `setAnchorElement` and `setEndElement` become
   package-private.
8. Fold `dom/NoteSpan.java` into `dom/Beam.java`: `Beam extends Span` with a
   `Beam(NoteElement, NoteElement)` constructor, covariant `getAnchorElement()` /
   `getEndElement()` returning `NoteElement`, the `acceptsEndpoint` override admitting only
   `NoteElement`, and `createCopy` narrowing to notes. Delete `NoteSpan.java`; `Span` permits
   `Beam` in its place; `Span`'s class Javadoc names `Beam` as the one narrowing.
9. `midi/MidiSequenceBuilder.endingCovering`: reword its Javadoc so it states what it returns
   without linking the deleted `findEndingAt`.

## ✅ Phase 5: Span Kind Machinery

**Status:** Complete  <br>
**BlockedBy:** 4  <br>
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Displacement.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Crescendo.java, src/main/java/songscribe/dom/Diminuendo.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Trill.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/undo/UndoController.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Opus, high — one invalidation hook, one displacement value, and one hairpin type replace three per-kind mechanisms; the contracts on `Span` change

### Tasks

1. Read *Rules for every phase*, `docs/span-invalidation.md` and `docs/hairpin-editing.md`.
2. **One hairpin type.** `Hairpin` becomes a `final` class with a `Kind` field set by a
   `Hairpin(Kind kind, StaffElement anchor, StaffElement end)` constructor; `getKind()` returns
   it. `createCopy` builds a `Hairpin` of the same kind and copies the shifts. Delete
   `createHairpin`, `Kind.spanType()` and the `permits` clause. Delete `dom/Crescendo.java` and
   `dom/Diminuendo.java`. `Span` permits `Hairpin` as a final leaf.
3. **The displacement policy is a value.** Write the contract first: a new enum
   `dom/Displacement` with `COEXIST`, `REPLACE_OVERLAPPING`, `ABSORB_OVERLAPPING` and
   `ABSORB_ADJACENT`; the two absorbing constants carry their reach (`0` and
   `LineSpans.SPAN_ADJACENCY_REACH`), from `LineSpans.Absorb`, which is deleted. `Span` declares
   `protected abstract Displacement displacement()`. Each kind returns one constant: `Beam`
   `ABSORB_OVERLAPPING`, `Hairpin` `ABSORB_ADJACENT`, `Tuplet` and `Trill` `REPLACE_OVERLAPPING`,
   `Ending` `COEXIST`.
4. `Span` gets one same-kind test used by every displacement: same concrete class, and for a
   hairpin the same `Kind`. `Span.addTo` switches on `displacement()` once, outside replay:
   `REPLACE_OVERLAPPING` removes every same-kind span overlapping this span's range;
   `ABSORB_*` calls `LineSpans.mergeOverlapping` with the constant's reach. `mergeOverlapping`
   and the replacing removal take the same-kind test instead of a `Class`. Keep the public
   `LineSpans.removeOverlapping(Class, ElementRange)` for `ui/action/TrillAction`, its other
   caller. Delete `applyDisplacementPolicy` and its four overrides.
5. **One invalidation hook.** Delete `isInvalidatedBy`, `isInvalidatedByInsertion`,
   `isInvalidatedByDeletion`, `isInvalidatedByReplacement` and `isInvalidatedByChange` from
   `Span` and every override. Add one `protected` query on `Span` for the rule every kind shares:
   a deletion whose deleted elements include the anchor or the end. The default `outcomeFor` is
   that rule alone (`Trill` relies on it). Rewrite `outcomeFor`'s Javadoc: it is the one hook,
   and nothing bridges to it.
6. `Beam.outcomeFor`: one switch over the change — a deletion answers the shared rule, a
   replacement answers today's `Beam.isInvalidatedByReplacement` body, an insertion or a
   modification keeps. `Ending.outcomeFor`: an insertion answers today's
   `isInvalidatedByInsertion`; a deletion answers the shared rule or today's
   `isInvalidatedByDeletion`; a replacement answers
   `checkReplacement(...) instanceof EndingEffect.Invalidate`; a modification keeps. Confirm that
   `Tuplet.outcomeFor`'s deletion case already covers the shared rule, and `Hairpin`'s override
   stays.
7. **Two span records.** Delete `Span.additionRecordFor`, `removalRecordFor` and every override.
   `LineSpans.add(Span)` and `remove(Span)` build `new SpanAddition(line, span)` and
   `new SpanRemoval(line, span)` themselves; `addTo` and `removeFrom` stop passing a record.
8. `undo/UndoController.opName`: one arm `case SpanAddition` / `SpanRemoval` resolves the label
   from the span through a private exhaustive switch over the sealed `Span` hierarchy: `Beam` →
   `ACTION_EDIT_OP_BEAMING`, `Tuplet` → `ACTION_EDIT_OP_TUPLET`, `Hairpin` → by `Kind`
   (`ACTION_EDIT_OP_CRESCENDO`, `ACTION_EDIT_OP_DIMINUENDO`), `Trill` → `ACTION_EDIT_OP_TRILL`,
   `Ending` → `ACTION_EDIT_OP_ENDING`. Delete the arms for the eight per-kind records. In
   `strings.properties`, rename `action.edit.op.span` to `action.edit.op.ending` and add
   `action.edit.op.trill = Trill`, each in sorted position.

## ✅ Phase 6: Span Kind Callers and Dead Verdict Members

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Files:** src/main/java/songscribe/message/mutation/BeamingAddition.java, src/main/java/songscribe/message/mutation/BeamingRemoval.java, src/main/java/songscribe/message/mutation/TupletAddition.java, src/main/java/songscribe/message/mutation/TupletRemoval.java, src/main/java/songscribe/message/mutation/CrescendoAddition.java, src/main/java/songscribe/message/mutation/CrescendoRemoval.java, src/main/java/songscribe/message/mutation/DiminuendoAddition.java, src/main/java/songscribe/message/mutation/DiminuendoRemoval.java, src/main/java/songscribe/message/mutation/Mutation.java, src/main/java/songscribe/undo/MutationReplayer.java, src/main/java/songscribe/dom/ChainEdit.java, src/main/java/songscribe/ui/renderer/HairpinRenderer.java, src/main/java/songscribe/io/musicxml/WedgeTypeMapping.java, src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/undo/OpNames.java, src/main/java/songscribe/dom/EndingValidationResult.java, src/main/java/songscribe/message/mutation/SpanMutation.java, src/main/java/songscribe/message/mutation/SpanAddition.java, src/main/java/songscribe/message/mutation/SpanRemoval.java  <br>
**Recommended model/effort:** Sonnet, medium — deletions and call-site updates onto the types Phase 5 produced

### Tasks

1. Read *Rules for every phase*. Phase 5 left: one `final class Hairpin` with a
   `Hairpin(Hairpin.Kind, StaffElement, StaffElement)` constructor (no `Crescendo` or
   `Diminuendo`), and `SpanAddition` / `SpanRemoval` as the only span records.
2. Delete the eight per-kind span records in `message/mutation/`: `BeamingAddition`,
   `BeamingRemoval`, `TupletAddition`, `TupletRemoval`, `CrescendoAddition`, `CrescendoRemoval`,
   `DiminuendoAddition`, `DiminuendoRemoval`. Remove them from `Mutation`'s `permits` clause.
3. `undo/MutationReplayer.applyUndo` and `applyRedo`: delete the arms for the eight records; the
   `SpanAddition` / `SpanRemoval` arms and the tie arms stay. `dom/ChainEdit.of`: remove the eight
   from its null-returning arm.
4. Delete `message/mutation/SpanMutation.java`, which nothing reads, and remove it from
   `SpanAddition`'s and `SpanRemoval`'s `implements` clauses. Run
   `jet_brains_find_referencing_symbols` on it first and report any reader.
5. Replace every `new Crescendo(a, e)` with `new Hairpin(Hairpin.Kind.CRESCENDO, a, e)` and every
   `new Diminuendo(a, e)` with the `DIMINUENDO` form: `io/LineIO` (`createCrescendosFromPending`,
   `createDiminuendosFromPending`), `io/musicxml/MeasureMapper.buildHairpin`,
   `ui/MusicEditOperations.addHairpinToSelection`.
6. `ui/renderer/HairpinRenderer.renderHairpinsFromLine`: read the hairpin layouts once by
   `Hairpin.class` and take each hairpin's kind from `getKind()`. `io/musicxml/WedgeTypeMapping`
   and `undo/OpNames`: reword Javadoc that links `Crescendo` or `Diminuendo`.
7. Delete `EndingValidationResult.PrecedingAction` and `getPrecedingAction`, which nothing reads,
   with the field and the constructor argument. Update the constructions in
   `ui/MusicEditOperations.checkPrecedingElement` and `EndingValidationResult`'s `INVALID`
   constant. Delete the enum's Javadoc with it.

## ✅ Phase 7: Pending Tuplets and the Note-Value Ratio

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Files:** src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/PendingTuplet.java, src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/dom/TupletValidator.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/NotatableValue.java, src/main/java/songscribe/dom/TupletRatio.java, src/main/java/songscribe/io/LoadMigrations.java  <br>
**Recommended model/effort:** Opus, high — a new load-time type, a validator walk that takes its spans from its caller, and new contracts on the load path

### Tasks

1. Read *Rules for every phase*. Read all of `dom/Tuplet.java`, `dom/TupletLoadPass.java`,
   `dom/TupletValidator.java`, `dom/NotatableValue.java`, `dom/TupletRatio.java`,
   `io/LoadMigrations.java`, `ModificationSession.removeTupletsInvalidatedFrom`, and the two
   reader sites that build a tuplet today: `io/LineIO.LineReader.createTupletsFromPending` and
   `io/musicxml/MeasureMapper.buildTuplet`. Phase 8 changes the readers; this phase changes
   what they build.
2. Write the contracts first: `PendingTuplet`, the new `TupletLoadPass.run`, the new
   `LoadMigrations.run`, and the new `TupletValidator.validateFrom`.
3. **`NotatableValue` holds a note value.** It becomes `record NotatableValue(NoteValue value, Dots dots)`.
   `TupletValidator.buildNotatableByTicks` computes each entry's ticks from `NoteValue.ticks()`
   and the dots, with no note instance and no cast. Update `notatableTicks` and every other use
   in the validator.
4. **A reader builds a `PendingTuplet`, never a `Tuplet`.** New
   `record PendingTuplet(StaffElement anchor, StaffElement end, int grade, @Nullable TupletRatio statedRatio, double yOffsetSs)`
   in `dom`. It is not a span and sits in no registry. `yOffsetSs` is in staff spaces and becomes
   the tuplet's `userYOffsetSs`.
5. **`Tuplet`'s ratio is known.** `ratio` is `final` and non-null; `getRatio()` is total. Delete
   `withUnresolvedRatio`, the private three-argument constructor, `resolveRatio`, `isResolved`,
   and the unresolved branch of `createCopy`. Correct the field comment so it is true. Delete
   `verticalPositionSs`, its getter and setter, and its copy in `createCopy`: a tuplet's vertical
   offset is the inherited `userYOffsetSs`, which `Span.copy` already carries. Delete
   `hasValidSpan` when its only callers are the two readers; the load pass drops a pending tuplet
   whose span is not valid.
6. **The validator walk takes its spans from its caller.** `TupletValidator.validateFrom` no
   longer calls `findTupletAt`: its caller supplies, per element, the tuplet anchored there (its
   anchor, end and grade). `ModificationSession.removeTupletsInvalidatedFrom` supplies the
   registry's tuplets; `TupletLoadPass` supplies the pending tuplets. The walk opens one span at
   a time and does not check the pending tuplets for overlap: the writer cannot produce two
   overlapping tuplets. Each `Verdict` names what the caller supplied.
7. **`TupletLoadPass.run(Song, List<PendingTuplet>)`** is the one place a read tuplet becomes a
   `Tuplet`. For each verdict, under `song.withoutMutationTracking` as today: a stated ratio is
   judged by `validateStated` and a missing one takes the derived ratio; a valid pending tuplet
   becomes `new Tuplet(anchor, end, grade, ratio)` with `setUserYOffsetSs(yOffsetSs)` and is added
   to the anchor's line; an invalid or unplaceable one is reported as dropped. The report keeps
   its dropped and derived-ratio counts.
8. `io/LoadMigrations.run` takes the pending tuplets beside `accidentalsConverted` and passes them
   to `TupletLoadPass.run`.

## ✅ Phase 8: Pending Tuplet Plumbing

**Status:** Complete  <br>
**BlockedBy:** 3, 4, 6, 7  <br>
**Files:** src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/SongIO.java, src/main/java/songscribe/io/SongLoader.java, src/main/java/songscribe/io/MigrationPipeline.java, src/main/java/songscribe/io/FormatMigrator.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/SongMapper.java, src/main/java/songscribe/io/musicxml/NoteBuilder.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/layout/stacking/VerticalStackingCalculator.java  <br>
**Recommended model/effort:** Sonnet, medium — threading one list along the path `accidentalsConverted` already takes, and deleting branches the non-null ratio makes dead

### Tasks

1. Read *Rules for every phase*. Read `dom/PendingTuplet.java`, the `TupletLoadPass.run`,
   `LoadMigrations.run` and `NotatableValue` contracts, and `Tuplet.getRatio()` (now non-null).
   A reader never calls `new Tuplet` or `addTo` for a tuplet.
2. **Legacy path.** `io/LineIO.LineReader`: `pendingTupletData` becomes a `List<PendingTuplet>`
   kept for the whole document (not cleared per line). `createTupletsFromPending` resolves each
   entry's anchor and end elements in its line and appends a `PendingTuplet` with no stated ratio.
   Correct or delete the stale per-entry format comment. Expose the list.
   `io/SongIO.DocumentReader` exposes it beside `accidentalsConverted()` (an empty list when the
   document has no `lineReader`). `io/SongLoader.load` passes it to `LoadMigrations.run`.
3. `io/FormatMigrator.migratePixelsToStaffSpace` converts each pending tuplet's `yOffsetSs` from
   pixels to staff spaces, dividing by `DocumentScale.PIXELS_PER_STAFF_SPACE` with no rounding,
   instead of reading tuplets from the registry. Reach the list through the context
   `io/MigrationPipeline` already passes from `SongIO.DocumentReader.getSong`; add it to that
   context.
4. **MusicXML path.** `io/musicxml/MeasureMapper`: `buildTuplet` returns a `PendingTuplet`
   collected on the mapper; `statedNoteValue` returns `@Nullable NoteValue`; the stated ratio is
   `new TupletRatio(normalNotes, new NotatableValue(value, dots))`. `MeasureMapper.map` returns a
   record of `accidentalsConverted` and the pending tuplets. `io/musicxml/SongMapper.map` passes
   both to `LoadMigrations.run`.
5. Delete the null-ratio branches: `NoteBuilder.buildTimeModification` (the value is
   `ratio.noteValue().value()`; delete the `LOG.warn` placeholder path, and
   `largestPowerOfTwoBelowGrade` if nothing else calls it), `NoteBuilder.performedTicks`,
   `LineTrackBuilder.getElementDurationWithTuplet`, and
   `PasteSpanReconciliation.isRejectedByTarget` (always `validateStated`).
6. A tuplet's vertical offset is `userYOffsetSs`: `ScorePartwiseBuilder.applyTuplets` writes
   `tuplet.getUserYOffsetSs()`; `VerticalStackingCalculator.applyDecorationOffsets` deletes its
   `Tuplet` case, since the inherited offset is already added.

## ✅ Phase 9: A Beam Hands Out Its Notes

**Status:** Complete  <br>
**BlockedBy:** 3, 5, 8  <br>
**Files:** src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java  <br>
**Recommended model/effort:** Opus, high — the beam's per-note queries and three callers' index walks are rewritten over members, preserving sub-beam runs exactly

### Tasks

1. Read *Rules for every phase*. Read all of `dom/Beam.java`, `LayoutEngine.calculateBeams` and
   `computeBeamElementGeometry`, `BeamGroupRenderer.drawBeam`, `doDrawBeams` and `renderBeams`,
   and `ScorePartwiseBuilder.applyBeams` and `beamValues`.
2. Write the contracts first, then implement: `Beam` hands out its members — the non-grace notes
   from anchor to end, in order, each a `NoteElement` — and, as a separate query, the grace notes
   inside the span. `noteTypeInLevel`, `stubRight` and `frenchBeamShortening` take a member rather
   than a line index. Delete `noteAt(int)`. The level parameter stays an `int`.
3. Facts the rewrite relies on:
   - A grace note is always strictly inside the span: every creator puts both endpoints on
     beamable notes, and the invalidation rule removes a beam whose endpoint becomes a grace note.
   - A sub-beam run at level L is a run of consecutive members that are all in level L. That gives
     the runs the index walk gives today, because a grace note is in level L exactly when both its
     member neighbors are. Delete `noteTypeInLevel`'s grace-note branch and its Javadoc.
4. `direction`, both loops of `LayoutEngine.calculateBeams`, and `ScorePartwiseBuilder.applyBeams`
   iterate the members; delete their `isGraceNote()` skips. `graceStemTouchesBeamAbove` iterates
   the grace notes. `LayoutEngine` gets each member's column from the element-to-column map it
   already has.
5. `BeamGroupRenderer.doDrawBeams` and `drawBeam`, and `ScorePartwiseBuilder.beamValues`, find
   runs over member positions in the member list, not over line indices.

## ✅ Phase 10: Tree State Off LineElement

**Status:** Complete  <br>
**BlockedBy:** 2a, 2c, 4, 9  <br>
**Files:** src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/StaffElement.java, src/main/java/songscribe/dom/Attachment.java, src/main/java/songscribe/dom/Articulation.java, src/main/java/songscribe/dom/NoteElement.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/hit/HitTarget.java  <br>
**Recommended model/effort:** Opus, high — the element tree's ownership moves between classes and its class-level invariants are rewritten

### Tasks

1. Read *Rules for every phase*. Read all of `dom/LineElement.java` (its class Javadoc states the
   tree invariants), `StaffElement.addAttachment`, `removeAttachment`, `clearAttachments`,
   `NoteElement.addArticulation`, `removeArticulation`, `clearArticulations`, `Line.attach`,
   `Line.detach`, `Attachment.owner`, `Articulation.owner`, and
   `SelectionCoordinator.isOnLine` / `revalidateDecorationSelection`.
2. The structure: `LineElement` keeps only what every element has, the two user offsets and
   `copyBaseStateFrom`. Its subclasses `Span`, `SongTempoMark` and `Attribution` are never in the
   element tree and inherit no tree state.
   - `StaffElement` holds `parentLine`, written only by `Line.attach` and `Line.detach`.
   - `Attachment` and `Articulation` each hold their host, set and cleared by the host's add and
     remove methods. A child's line is derived from its host (`null` when it has no host), never
     stored, so nothing pushes a line down to children.
   - Delete from `LineElement`: `parentLine`, `parentElement`, `children`, `getParentLine`,
     `setParentLine`, `propagateParentLine`, `getParentElement`, `setParentElement`,
     `getChildren`, `addChild`, `removeChild`.
3. Write the class Javadoc of `LineElement`, `StaffElement`, `Attachment` and `Articulation` so
   each states its own part of the invariant: a staff element's line is the line whose elements
   hold it; a child's line is its host's.
4. Move every caller of the deleted members onto the owning type. The callers of `getParentLine`
   all hold a `StaffElement` except `SelectionCoordinator.isOnLine`, whose argument is
   `HitTarget.Selectable.owner()`, typed `LineElement`. Rewrite `isOnLine` as one switch over what
   an owner can be — a staff element, an attachment, an articulation, a span — and narrow
   `HitTarget.Selectable.owner()`'s return type if that makes the switch exhaustive.
5. `Span`: delete the Javadoc sentences about the inherited `parentLine` being `null`, and state
   `isIn` from the endpoints alone.

## ✅ Phase 11: Span Offsets and the Offset Round Trip

**Status:** Complete  <br>
**BlockedBy:** 5, 8, 9  <br>
**Files:** src/main/java/songscribe/dom/Trill.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/io/FormatMigrator.java, src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/NoteMapper.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java, src/main/java/songscribe/io/musicxml/DirectionBuilder.java, src/main/java/songscribe/io/musicxml/MeasureBuilder.java, src/main/java/songscribe/io/musicxml/MusicXmlUnits.java, src/main/java/songscribe/io/musicxml/ProxyMusicAccess.java, src/main/java/songscribe/layout/stacking/VerticalStackingCalculator.java  <br>
**Recommended model/effort:** Sonnet, medium — field deletions and read/write sites moved onto the inherited offset, and two stated defect fixes

### Tasks

1. Read *Rules for every phase* and `.claude/guides/spatial-units.md`.
2. **A span's vertical offset is `LineElement.userYOffsetSs`.** Delete `Trill.yPositionSs`,
   `Ending.yPositionSs` and `Hairpin.yShiftSs`, with their getters, setters and copies in
   `createCopy`. `Hairpin` keeps `x1ShiftSs` and `x2ShiftSs`. Every writer calls
   `setUserYOffsetSs` and every reader calls `getUserYOffsetSs`: `io/LineIO`
   (`createTrillsFromPendingPairs`, the hairpin readers), `io/musicxml/MeasureMapper`
   (`resolveTrills`, `applyHairpinShifts`), `ScorePartwiseBuilder.addTrillStart`,
   `DirectionBuilder.buildHairpinStartDirection`, `FormatMigrator.migratePixelsToStaffSpace` and
   `migrateLineLevelOffsets`. `VerticalStackingCalculator.applyDecorationOffsets` keeps only the
   hairpin's two X shifts in its switch.
3. `FormatMigrator.migratePixelsToStaffSpace` converts every span offset by dividing by
   `DocumentScale.PIXELS_PER_STAFF_SPACE` with no rounding; delete the `(int) Math.round` casts.
4. **Defect: the MusicXML reader rounds offsets to whole staff spaces.** The writer writes each
   offset exactly through `MusicXmlUnits.shiftTenths`, and `ProxyMusicAccess.tenthsToSs` rounds it
   on the way back, so a fractional hairpin shift or annotation offset changes on every save and
   load. Read each of these through `MusicXmlUnits.tenthsToExactSs` as a `double`:
   `MeasureMapper.annotationOf`, the three reads in `MeasureMapper.applyWedges`,
   `NoteMapper.tupletMarker` and `NoteMapper.trillMarker`. The records and parameters they fill
   become `double`: `TrillMarker.yPositionSs`, `TupletMarker.verticalPositionSs`,
   `WedgeStart.x1ShiftSs` and `yShiftSs`, `buildHairpin`'s `x2ShiftSs`, `OpenTrill.yPositionSs`.
   Delete `ProxyMusicAccess.tenthsToSs` and `MusicXmlUnits.tenthsToSs`, and correct the
   `ProxyMusicAccess` class Javadoc that lists it.
5. **Defect: an ending's vertical offset is not saved.** `FormatMigrator.migrateLineLevelOffsets`
   gives a legacy ending an offset that layout uses, and no MusicXML writer or reader carries it,
   so the first save loses it. Find the code that writes `<ending>` (start with
   `io/musicxml/MeasureBuilder` and `jet_brains_find_referencing_symbols` on `Ending` inside
   `io/musicxml/`): write the ending's `getUserYOffsetSs()` through
   `Ending.setRelativeY(MusicXmlUnits.shiftTenths(...))` on the start `<ending>`, and read it back
   through `tenthsToExactSs` into `setUserYOffsetSs` where the reader builds the ending. If the
   writer or reader sits in a file outside this phase's **Files**, edit it and report it.

## ✅ Phase 12: Ending Bracket Ranges in the Layout Result

**Status:** Complete  <br>
**BlockedBy:** 5, 11  <br>
**Files:** src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/BracketRange.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/layout/DecorationLayouts.java, src/main/java/songscribe/ui/renderer/EndingRenderer.java, src/main/java/songscribe/shape/EndingBracketShape.java  <br>
**Recommended model/effort:** Opus, high — decides where per-ending layout output lives in the layout result, which the renderer then reads

### Tasks

1. Read *Rules for every phase*. Read `Ending.BracketRange`, `Ending.bracketRanges`,
   `getBracketRanges`, `setBracketRanges`, `layout/EndingBracketGeometry.computeBracketRanges`, its
   caller `StructuralStacker.stackEndings`, `LayoutResult.DecorationLayout` and its variants,
   `DecorationLayouts`, `ui/renderer/EndingRenderer.renderEndings`, and
   `shape/EndingBracketShape.points`.
2. The bracket ranges are layout output. They go into the layout result beside the ending's
   decoration layout, where `EndingRenderer` reads them from the same place it reads the ending's
   decoration layout. Choose the carrier inside the layout result, and write its contract before
   implementing it.
3. Move `Ending.BracketRange` to `layout` as its own type, with `inkRightXSs()` and `label()`.
   Delete `Ending.bracketRanges`, `getBracketRanges` and `setBracketRanges`.
4. `EndingBracketGeometry.computeBracketRanges` returns the ranges, and `StructuralStacker.stackEndings`
   stores them in the layout result; correct the comment there that says they are stored on the
   `Ending`. `EndingRenderer` and `EndingBracketShape` read the moved type.

## ✅ Phase 13: Design Docs

**Status:** Complete  <br>
**BlockedBy:** 1, 2a, 2b, 2c, 2d, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12  <br>
**Files:** docs/span-invalidation.md, docs/mutations.md, docs/undo.md, docs/clipboard.md, docs/line-layout.md, docs/layout-geometry.md, docs/musicxml-object-model.md, docs/hairpin-editing.md  <br>
**Recommended model/effort:** Opus, high — the docs state the subsystem's shape, which this plan changed throughout

### Tasks

1. Read *Rules for every phase* and the *Design Docs* section of `CLAUDE.md`: a doc states
   concepts and how work flows, not class or method names, and describes the design as it is now.
2. Read each file in this phase's **Files** in full. Find and rewrite every passage that no longer
   holds:
   - A tie is a note attribute with a derived end, not a span; it joins two notes of kind
     `NOTE` only; it crosses a line break; a tie records its own addition and removal.
   - The tie rule runs once, when the outermost modification bracket closes, over the lines the
     edit touched and the line before each. Its removals are recorded after the changes that
     broke the ties, which is the one exception to the rule that a record removing dependent
     state comes before the change that prompted it; state why undo is still correct.
   - An edit near a line's edge can change the half of a tie that the line beside it draws, and
     the model answers which lines an edit reaches that way, as it does for a key move.
   - Every span lies in one line and sits in one registry.
   - Each span kind states its displacement policy as one value, and invalidation has one hook.
   - Span changes are recorded by one addition record and one removal record.
   - A reader builds pending tuplets, and the load pass is the one place they become tuplets.
   - An ending's bracket ranges are layout output.
3. Each claim of the form "every X" or "only Y" that you write, check against the code before the
   sentence is finished; write the weaker true sentence where the code disagrees.

## ✅ Phase 14: Test Tree

**Status:** Complete  <br>
**BlockedBy:** 1, 2a, 2b, 2c, 2d, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12  <br>
**Files:** src/test/java/songscribe/  <br>
**Recommended model/effort:** Opus, high — runs in the main session, because it stops for the user's approval before any test changes

### Tasks

1. Read `.claude/guides/testing-common.md`, `.claude/guides/testing-unit.md`, and the testing
   floor in `~/.claude/guides/design.md`.
2. Find every test source that references a deleted or changed member, with
   `jet_brains_find_referencing_symbols` over the members Phases 1–12, 2a–2d included, deleted
   or retyped. Known
   today: `src/test/java/songscribe/dom/LineSpansTest.java`, `SpanTest.java`, `LineTest.java`,
   `ElementChangeTest.java`, `SongFactory.java`,
   `src/test/java/songscribe/e2e/E2ETest.java`,
   `src/test/java/songscribe/io/musicxml/MusicXmlCrossLineSpanTest.java` and
   `MusicXmlRoundTripSupport.java`.
3. Propose one table to the user — each affected test, its kind from the testing floor, and
   whether it is kept as is, rewritten to the new contract, or deleted, with the reason — and wait
   for approval. Write no test code before it.
4. Apply what the user approved.

## ✅ Phase 15: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 13, 14  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — runs two scripts and reports their output

### Tasks

1. Run `./scripts/compile.sh --test`. It must report SUCCESS. On FAILURE, report every error with
   its file and line; the errors are information about call sites the phases did not account for.
2. Run `./scripts/test.sh SpanTest LineSpansTest LineTest ElementChangeTest`. Report the passing
   count per class and every failure with its output.

## ✅ Phase 16: Manual UI Verification

**Status:** Complete  <br>
**BlockedBy:** 15  <br>
**Files:** —  <br>
**Recommended model/effort:** — the user runs the app and checks each item

### Tasks

1. The user checks, in the running app:
   - A tie inside a line and a tie across a line break draw, select, delete, and undo. Undo after
     a tie is removed by an edit restores it.
   - A sharp pressed on either note of a tie removes the tie, and one undo brings back both the
     accidental and the tie.
   - A same-pitch note inserted between two tied notes keeps the tie, which then ends on the
     inserted note. A grace note or a different pitch inserted there removes it.
   - A pitch drag that moves both notes of a tie keeps the tie.
   - With a tie across a line break: deleting the tied last note of the line redraws the next
     line's half at once. Flipping the stem of either note also flips the other note's stem, in
     one undo step, and both lines redraw the tie at once.
   - A beam group with grace notes, sub-beams, a partial-beam stub and French beaming draws as
     before.
   - First and second endings draw their brackets and labels.
   - A crescendo and a diminuendo draw, merge with a same-kind neighbor, and undo.
   - A trill draws; the undo menu names a trill step "Trill" and an ending step "Ending".
   - A legacy `.mssw` file with tuplets, hairpin shifts and an ending offset loads with them in
     place; after a save to MusicXML and a reload, they are still in place.
