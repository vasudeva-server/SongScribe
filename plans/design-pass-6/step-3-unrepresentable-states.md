# Step 3 — Make Illegal States Unrepresentable

Source: `plans/design-pass-6/findings-3.md`. Read the finding named in a task before
carrying it out; the tasks below state what changes, the findings document states the
site-by-site evidence.

Where a task and `findings-3.md` disagree on a declaring class, a path, an overload or a
site list, the task is right.

**Only Phase 15 writes a new test.** In every other phase, test files change only where a
type change breaks compilation. Phase 15 both chooses the test list and implements it.

**Do not compile to certify a phase.** Phase 16 is the single gate. A phase that leaves
the tree non-compiling is expected — work happens on a branch, and the end state is what
gets checked.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1a | [Line Element Lookup Totality](#-phase-1a-line-element-lookup-totality) | ✅ Complete | — |
| 1b | [Dead Membership Checks](#-phase-1b-dead-membership-checks) | ✅ Complete | — |
| 2 | [Span Endpoint Queries](#-phase-2-span-endpoint-queries) | ✅ Complete | — |
| 3a | [Run Index Queries and Widened Ranges](#-phase-3a-run-index-queries-and-widened-ranges) | ✅ Complete | — |
| 3b | [Widened Range Test Fixtures](#-phase-3b-widened-range-test-fixtures) | ✅ Complete | — |
| 4 | [Span Outcomes and Line Spans](#-phase-4-span-outcomes-and-line-spans) | ✅ Complete | — |
| 4b | [Lyric Run Indices](#-phase-4b-lyric-run-indices) | ✅ Complete | — |
| 5 | [Grace Mode Enablement Default](#-phase-5-grace-mode-enablement-default) | ✅ Complete | — |
| 6 | [Swing Index Boundary](#-phase-6-swing-index-boundary) | ✅ Complete | — |
| 7 | [Legacy IO and String Indices](#-phase-7-legacy-io-and-string-indices) | ✅ Complete | — |
| 8 | [Lyric Layout Records](#-phase-8-lyric-layout-records) | ✅ Complete | — |
| 9a | [MusicXML Cross-Line Spans](#-phase-9a-musicxml-cross-line-spans) | ✅ Complete | — |
| 9b | [MusicXML Index Conversions](#-phase-9b-musicxml-index-conversions) | ✅ Complete | — |
| 10 | [Layout Indices](#-phase-10-layout-indices) | ✅ Complete | — |
| 11a | [Render Frame Types](#-phase-11a-render-frame-types) | ✅ Complete | — |
| 11b | [Playing and Frame Index Conversions](#-phase-11b-playing-and-frame-index-conversions) | ✅ Complete | — |
| 12a | [Grace Mode State](#-phase-12a-grace-mode-state) | ✅ Complete | — |
| 12 | [Edit and Selection Indices](#-phase-12-edit-and-selection-indices) | ✅ Complete | — |
| 13 | [MIDI Indices](#-phase-13-midi-indices) | ✅ Complete | — |
| 14 | [Dead Code Removal](#-phase-14-dead-code-removal) | ✅ Complete | — |
| 15 | [Test Proposal](#-phase-15-test-proposal) | ✅ Complete | — |
| 16 | [Compile and Test Gate](#-phase-16-compile-and-test-gate) | ✅ Complete | — |
| 17 | [Manual UI Verification](#-phase-17-manual-ui-verification) | ✅ Complete | — |

---

## ✅ Phase 1a: Line Element Lookup Totality

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/ElementChange.java, src/main/java/songscribe/layout/HorizontalSpacingCalculator.java, src/main/java/songscribe/ui/dialog/AttachmentTarget.java, src/main/java/songscribe/ui/renderer/RenderingUtils.java, src/main/java/songscribe/ui/renderer/StaffElementRenderer.java, src/main/java/songscribe/ui/component/score/LineSelectionHandler.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Fable, low — decides what each of seven call sites answers when the element is absent, and writes the contract every later phase relies on.

`Line.getElementIndex(StaffElement)` (near `Line.java:648`) answers `-1` for an element
that is not on the line, and `Line` has no membership query. A staff element's parent line
is written only by `Line.attach` and `Line.detach`, `LineElement.propagateParentLine`
reaches only children, and no `Attachment` or `Articulation` is a `StaffElement`, so the
parent pointer is an exact membership test. This phase makes the lookup total over the
line's own elements, adds the membership query, and converts the callers that can
genuinely be handed a foreign element.

### Tasks

1. Add `Line.contains(StaffElement element)`, answering `element.getParentLine() == this`.
   Its Javadoc states why the parent pointer is exact (the reason above), and that this is
   the query a caller asks when it does not know whether the element is on this line.
2. Make `getElementIndex` throw `IllegalArgumentException` when `!contains(element)`, before
   the map lookup. Delete the `-1` return and the Javadoc clause promising it.
3. Write the contract on `Line.getElementIndex(StaffElement)` before changing any caller.
   It has fan-in across thirty files, so it earns the deep form: `@return` the element's
   position, `@throws IllegalArgumentException` naming the exact condition
   (`element.getParentLine() != this`), and an `@invariant` that the result is always a
   valid index into the line. State that `contains` is the guard method a caller uses when
   it does not know whether the element is on this line, and `{@link}` the two together.
4. Convert the six sites that can genuinely be handed an element from another line so they
   ask `contains` first, and decide what each answers in the absent branch — the findings
   document names the sites but not their answers, so that judgement is this task's. Each
   reason is given so the guard is not mistaken for dead code:
   - `RenderingUtils.getDecorationColor` — reached by the insertion preview, which is on no
     line.
   - `StaffElementRenderer` `isNoteBeamed` (near `:439`) — the insertion preview reaches it
     through `renderNote` before `renderAccidental`.
   - `LineSelectionHandler` (near `:612`) — an element hit on another line's component.
   - `SelectionCoordinator.isSelected` (near `:478`) — a clicked element on a different line
     than `range.line()`.
   - `ScoreViewController` (near `:1120`) — the selected target's owner may be on another
     line.
   - `AttachmentTarget.forElement` (near `:67`) — accepts any element.
5. In `src/main/java/songscribe/dom/ElementChange.java` (near `:117` and `:223`), ask
   `contains` before the lookup — a span endpoint can sit on the neighboring line. Leave the
   shape of the result alone; Phase 4 converts `projectedIndexOf` to return
   `@Nullable Integer` and will revisit these two sites.
6. In `HorizontalSpacingCalculator.hairpinReservationFloorSs` (near `:1242–1248`), return
   `0` unless the line `contains` both columns' elements, then ask `hasSpan` as it does now.
   An insertion preview's column is on no line. Delete the reversed-pair check and its
   comment. Update the Javadoc near `:1219–1220` to say that a pair with an element off the
   line reserves nothing.
7. Leave every site that assumes the element is present exactly as it is — they now fail at
   the lookup instead of later. This covers `PitchShifter`, `EndingConfirms`,
   `KeyChangeElement`, `NoteElement`, `StaffElementRenderer:245`, the twelve `LyricEditor`
   sites, `Song`, `MeasureMapper`, `Ending`, `LineComponent.elementIndexOf`,
   `EditLyricAction`, `Span.indexInLine`, and the tests `EndingBracketGeometryTest`,
   `InheritanceChainTest` and `SongAccidentalWalkTest`.

---

## ✅ Phase 1b: Dead Membership Checks

**Status:** Complete  <br>
**BlockedBy:** 1a  <br>
**Files:** src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/Tie.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/layout/HairpinEndpoints.java, src/main/java/songscribe/layout/ElementColumnBuilder.java, src/main/java/songscribe/ui/dialog/AttachmentTarget.java, src/main/java/songscribe/ui/dialog/KeyChangeDialogController.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Sonnet, low — deletions and comment edits whose reachability analysis is already done; each task names the site and the reason it is dead.

After Phase 1a, `Line.getElementIndex(StaffElement)` throws when the element is not on the
line and never returns `-1`. Every check below tests for a condition its own caller cannot produce, and the
reason is given per site. Delete exactly what each task names — do not widen a deletion, and
do not add a replacement check.

### Tasks

1. In `src/main/java/songscribe/dom/LineElement.java`, delete the sentence in the
   `parentLine` field Javadoc crediting "`Line.detach`'s `!= this` guard" with keeping the
   pointer and the list in step. No such guard exists. Replace nothing — the surrounding
   prose stands on its own.
2. In `src/main/java/songscribe/ui/dialog/AttachmentTarget.java`, replace the class Javadoc
   sentence saying that binding the element and line separately "is what allowed a stale
   element to reach `Line.modifyElement(-1, …)`" with the present-tense reason: a target
   whose element is not on its line cannot be built. Do not narrate that anything changed.
3. Delete the six checks that no caller can reach, each with the reason it is dead:
   - `ScoreViewController` near `:935` — the line is `element.getParentLine()` at `:928`.
   - `ScoreViewController` near `:1035–1036` (slide delete) — the element is still on this
     line; delete the `elementIndex >= 0` test and the `-1` in its comment.
   - `ModificationSession` near `:492` — the line is `owner.getParentLine()` at `:482`;
     delete the `elementIndex < 0` half **only**. The song-membership half stays here and is
     converted by Phase 4.
   - `AttachmentTarget` near `:49` — the constructor throws the same exception itself.
   - `KeyChangeDialogController` near `:122` — the key change and the line both come from
     the call that opened the dialog; delete the check and its error log.
   - `Tie` near `:229–231` — `Line.setElement` builds the replacement from its own line;
     delete the `replacedIndex < 0` half and its comment. The bounds test stays.
4. In `src/main/java/songscribe/dom/Beam.java` near `:134–139`, delete the comment about
   another line's `-1`. The bounds test itself stays.
5. In `src/main/java/songscribe/layout/HairpinEndpoints.java` near `:107`, rewrite the
   comment so it no longer names `-1`.
6. In `src/main/java/songscribe/layout/ElementColumnBuilder.java`, delete the two-argument
   `buildColumn(StaffElement, Line)` overload. Confirm it has no callers with
   `mcp__serena__jet_brains_find_referencing_symbols` first; the three-argument overload
   `buildColumn(element, line, index)` is the live one and stays.

---

## ✅ Phase 2: Span Endpoint Queries

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b  <br>
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Tie.java, src/main/java/songscribe/dom/TupletValidator.java, src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/SpanLookup.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/io/musicxml/MeasureBuilder.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java, src/main/java/songscribe/layout/LayoutEngine.java, src/main/java/songscribe/layout/ElementColumnBuilder.java, src/main/java/songscribe/layout/EndingBracketGeometry.java, src/main/java/songscribe/layout/HairpinEndpoints.java, src/main/java/songscribe/layout/stacking/NoteAttachedStacker.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/component/score/PreviewElementInserter.java, src/main/java/songscribe/ui/component/score/LineRenderer.java, src/test/java/songscribe/io/musicxml/MusicXmlRoundTripSupport.java  <br>
**Recommended model/effort:** Fable, low — nine call sites where absence is real and sixty-one where it is not; the rename is mechanical but inseparable from the judgement that follows it, since the wrong split crashes the load path.

`Span.indexInLine` is private static and returns `-1` when the endpoint's
`getParentLine()` is null. `getAnchorElementIndex()` and `getEndElementIndex()` are public,
each a bare `return indexInLine(...)`, declared **only** on `Span` — none of the nine
subtypes (`Hairpin`, `Crescendo`, `Diminuendo`, `Trill`, `Tuplet`, `NoteSpan`, `Tie`,
`Beam`, `Ending`) overrides either, so the IDE rename reaches every site.

Absence is real: `Tuplet.hasValidSpan` is contracted for it ("a load-time validator should
reject any tuplet for which this returns false"), and `Hairpin.outcomeFor` branches on it
today. The end state is four methods — a throwing pair for callers that assume presence,
and a nullable pair keeping the plain name so that a site left untouched fails NullAway
rather than silently passing a sentinel.

### Tasks

1. Write the contract for all four methods before touching any call site. Fan-in is ~68
   sites across ~27 files, so these earn the deep form. `requireAnchorElementIndex()` and
   `requireEndElementIndex()`: `@return` the endpoint's index within its own line, and
   `@throws IllegalStateException` when the endpoint sits in no line because a removal
   detached it — the span's own state, not the caller's argument, which is why it is not
   `IllegalArgumentException`.
   `getAnchorElementIndex()` and `getEndElementIndex()`: `@return` the index, or `{@code
   null}` when the endpoint sits in no line. State on each pair which kind of caller it is
   for — the `require` pair for the edit, layout and render paths, the plain pair for the
   load and validation paths — and `{@link}` each to its counterpart.
2. Rename `Span/getAnchorElementIndex` to `requireAnchorElementIndex` and
   `Span/getEndElementIndex` to `requireEndElementIndex` using
   `mcp__serena__jet_brains_rename` with `rename_in_comments: false`. Run the two renames
   **before** adding the new methods, so no name collides. All ~68 sites follow
   mechanically.
3. After the renames, run `git status --porcelain | grep -v '\.java$'` and restore any
   Markdown the rename rewrote. `plans/design-pass-6/findings-3.md`,
   `findings-1-2.md`, `class-design.md` and `record.md` record what was found and decided,
   and rewriting a name in them turns a sentence naming both names into a tautology.
4. Change `Span.indexInLine` to return `@Nullable Integer`, returning `null` where it
   returns `-1` today, and delegating to `Line.getElementIndex` for the present case. Update
   its Javadoc so it no longer names `-1`.
5. Implement `requireAnchorElementIndex()` and `requireEndElementIndex()` over
   `indexInLine`, throwing when it answers null, per the contract written in task 1.
6. Add the new `getAnchorElementIndex()` and `getEndElementIndex()`, each returning
   `@Nullable Integer` straight from `indexInLine`.
7. Move these nine sites — and only these — from the `require` pair back to the plain pair,
   adding a null test at each. Each genuinely handles an absent endpoint:
   - `Hairpin.outcomeFor` (near `:144–150`) — replace the `anchorIndex < 0 || endIndex < 0`
     test with a null test. Delete the comment near `:161–162` saying the indices are "known
     good here, which the nullable accessors cannot express"; the nullable accessors now
     express it, and the `@Nullable` type carries what the sentence was for.
   - `Tuplet.hasValidSpan` (near `:256–257`) only. `Tuplet.isNumberOnly` (near `:244–245`)
     decides how a tuplet is drawn and is asked with both endpoints present, so it stays on
     the `require` pair.
   - `TupletValidator` near `:396` and `:446`.
   - `TupletLoadPass` near `:196–197` — the surrounding comment about reading the span
     before `removeFrom` detaches it stays; it states a present-tense ordering obligation.
   - `MeasureBuilder` near `:630–631`.
   - `ScorePartwiseBuilder` near `:770–771`.
   - `ModificationSession` near `:550` — the `getAnchorElementIndex() >= 0` test becomes a
     null test. Assign the result to a local rather than calling twice, since `:551` calls
     it again.
8. Leave every other site on the `require` pair. Do not add a null test anywhere the
   rename landed and the value feeds a loop bound, arithmetic, `Math.max`/`Math.min`, an
   equality test, or an `ElementRange` constructor — those callers assume presence, which is
   what the `require` name now states.
9. Update the Javadoc and comments that describe these getters' cross-line behavior so they
   name the right method: `Tie` near `:143–144`, `SpanLookup` near `:55`,
   `ScorePartwiseBuilder` near `:759`, `LineSpans` near `:260`, `HairpinEndpoints` near
   `:106`, and `NoteAttachedStacker` near `:828`.

---

## ✅ Phase 3a: Run Index Queries and Widened Ranges

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b, 2  <br>
**Files:** src/main/java/songscribe/dom/StaffElementRun.java, src/main/java/songscribe/dom/EditableRun.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/KeyChangeStranding.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/ui/component/score/PitchShifter.java, src/main/java/songscribe/ui/action/KeyChangeAction.java, src/main/java/songscribe/ui/clipboard/Fragment.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/dialog/KeyChangeDialogController.java  <br>
**Recommended model/effort:** Fable, low — introduces a type whose whole purpose is to be unforgeable, and splits a stepped query into two directional ones.

### Tasks

1. Replace `StaffElementRun.nearestNonGraceIndex(int fromIndex, int step)` with
   `previousNonGraceIndex(int)` and `nextNonGraceIndex(int)`, each returning
   `@Nullable Integer`. No caller passes a step of `0`, so no third direction exists. Write
   each one's `@return` naming the condition for null before implementing.
2. Change `Beam.neighborBeamCount` to take the `@Nullable Integer` neighbor index already
   found, and answer for its absence in one place. Its callers near `Beam:349`, `:418` and
   `:420` each call the direction they need and pass the result in. `frenchBeamShortening`
   stops passing literals.
3. Update `MusicEditOperations` near `:181–183` to call `previousNonGraceIndex`.
4. Split `RangeQueries.tieAttachmentIndex` (near `:138`) the same way — it also takes a step
   of `-1` today.
5. Change `StaffElementRun.precedingGraceNoteIndex` (near `:276`) to return
   `@Nullable Integer`. `PitchShifter` near `:384–386` tests for null before
   `isSamePitchAsFollower`; delete its comment about a negative index.
6. Change `StaffElementRun.lastNoteIndex()` (near `:397`) to return `@Nullable Integer`,
   null when the line holds no note. `KeyChangeAction` near `:132` tests for null, then
   tests `index > 0 && index <= last`. The Javadoc clause "a line holding no note offers no
   position" near `:110–111` then reads straight off that test.
7. Make the `ElementRange` compact constructor (near `StaffElementRun.java:668`) also throw
   when `begin < 0`, and change the `@invariant` to `0 <= begin <= end`. This is safe only
   after Phase 1a task 6, which stops `HorizontalSpacingCalculator` building `(-1, -1)`. No
   test constructs a negative `begin`.
8. Write the contract for `StaffElementRun.EffectiveRange` before creating it: a `final
   class` with a **private** constructor, so only `StaffElementRun.effectiveRange` can
   create one, exposing its `ElementRange` through `range()`, its only accessor. State on
   the class why it exists — a raw selection range that cuts a grace note from its host or
   a key change from its barline deletes half a pair, and the type is what makes an
   unwidened range unpassable — and why it is a class rather than a record: a public
   record's canonical constructor cannot be less accessible than the record, so a record
   could not restrict construction to `effectiveRange`.
9. Change these to take or return `EffectiveRange`:
   - `EditableRun.deleteRange` and `EditableRun.deleteRanges`
   - `Line.keyAtEndOfLineAfterRemoving` (near `Line.java:314`)
   - both overloads of `StaffElementRun.redundantKeyChangeRanges` — `(int, Key)` and
     `(Key)` — in their returned list
   - `KeyChangeStranding.Stranded`'s ranges
10. Delete the "must be widened" clause at `EditableRun.java:118–122`. The type now carries
    it, so the prose is a second copy.
11. Change `ScoreViewController.tryInsertFragment` to take `@Nullable EffectiveRange`. Its
    only caller passing a range, `handlePaste` near `:1557`, already passes the widened one.
    Delete both re-widenings inside the method: `pasteDisplacementIndex` with
    `widenedDeleteRange` near `:1308–1316`, and `line.effectiveRange(deleteRange)` near
    `:1462`. `successorIndex` is then the index after the range actually deleted, so the
    lookup near `:1466` cannot miss and needs no guard.
12. Fix the `EffectiveRange` call site in
    `src/main/java/songscribe/ui/clipboard/Fragment.java`, which calls the `(Key)` overload
    of `redundantKeyChangeRanges`.

---

## ✅ Phase 3b: Widened Range Test Fixtures

**Status:** Complete  <br>
**BlockedBy:** 3a  <br>
**Files:** src/test/java/songscribe/dom/LineDeletionTest.java, src/test/java/songscribe/dom/StrandedKeyChangeTest.java, src/test/java/songscribe/dom/KeyChangeStrandingTest.java, src/test/java/songscribe/dom/InheritedKeyPropagationTest.java, src/test/java/songscribe/io/musicxml/MusicXmlStrandedKeyChangeTest.java  <br>
**Recommended model/effort:** Sonnet, low — compile-driven fixture edits; the type is already in place and every site is named.

`StaffElementRun.EffectiveRange` is a `final class` with a private constructor, created
only by `StaffElementRun.effectiveRange`. `EditableRun.deleteRange`, `deleteRanges`,
`Line.keyAtEndOfLineAfterRemoving`, both overloads of
`StaffElementRun.redundantKeyChangeRanges` and `KeyChangeStranding.Stranded` now take or
return it instead of a raw `ElementRange`.

**Assert nothing new and delete no assertion.** Every edit here exists to make an existing
test compile against the new type. If a fixture cannot be expressed through
`effectiveRange`, stop and report it rather than weakening the assertion or reaching for a
cast.

### Tasks

1. Route the raw ranges handed to `deleteRanges` in `LineDeletionTest` near `:154–156`
   through `effectiveRange`. `LineDeletionTest` near `:142–143` already does and needs no
   change.
2. Route the expected lists compared with `redundantKeyChangeRanges` in
   `StrandedKeyChangeTest` near `:322–326`, `:339–343` and `:353–357` through
   `effectiveRange`.
3. Route the `KEY_CHANGE_PAIR` field in `KeyChangeStrandingTest` near `:70–71` through
   `effectiveRange`, and update its uses near `:118` and `:135`.
4. Fix the `redundantKeyChangeRanges` call site in `MusicXmlStrandedKeyChangeTest`, which
   calls the `(Key)` overload.
5. Confirm `InheritedKeyPropagationTest` near `:198–199` already goes through
   `effectiveRange` and needs no change. If it compiles untouched, leave it untouched.

---

## ✅ Phase 4: Span Outcomes and Line Spans

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b, 2, 3a  <br>
**Files:** src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/SpanOutcome.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/ElementChange.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/Tuplet.java  <br>
**Recommended model/effort:** Fable, low — a sealed hierarchy split so one span type's answer is unrepresentable for the others, plus an enum replacing two magic reach values.

### Tasks

1. Change `ElementChange.projectedIndexOf` (near `:65`) to return `@Nullable Integer`,
   returning null where it returns `-1` today (near `:117`, `:158`, `:223`). Write the
   `@return` naming the condition — the element is not in the projection — before changing
   the body. `Hairpin.projectedEndpointIndexOf` near `:202–207` and `outcomeForDeletion`
   near `:228` then test for null instead of `< 0`.
2. Add `enum Absorb { OVERLAPPING, ADJACENT }` on `LineSpans`, each value carrying the reach
   it implies. `ADJACENT` reads `SPAN_ADJACENCY_REACH`, which stays the single number it and
   `MusicEditOperations` share. Do not duplicate that number.
3. Change `LineSpans.mergeOverlapping` (near `:463`) to
   `mergeOverlapping(R span, Class<? extends R> type, Absorb absorb)`. `Beam.java` near
   `:453` passes `OVERLAPPING`; `Hairpin.java` near `:118` passes `ADJACENT`. Rewrite the
   Javadoc near `:458–461` to describe the two values rather than the two numbers.
4. Narrow `Span.outcomeFor` to return `SpanOutcome.Simple`. Give `Hairpin` a separate method
   returning a sealed `HairpinOutcome` over `Simple | Reshape`. `Hairpin.java` near `:259`
   stays the only construction of `Reshape`.
5. Change `LineSpans.sweep` to ask hairpins through that new method, and delete
   `collectReshape`'s `instanceof` and throw near `LineSpans.java:374–376`. The sealed type
   now makes a non-hairpin `Reshape` unwriteable, so the throw guards nothing.
6. Override `outcomeFor` in `Hairpin` as a `final` projection of the new method, reading
   `Reshape` as `KEEP`, so no caller of `outcomeFor` receives the default bridge's answer
   for a hairpin, which removes a hairpin that a deleted endpoint only reshapes. State in
   its Javadoc that the sweep is the one reader that acts on a reshape. Leave
   `ElementChange.removesConfirmableSpan` (near `:79–84`) calling `outcomeFor` on every
   span, with no `instanceof Hairpin` test: the override already gives it the answer the
   sweep acts on.
7. Change `ElementChange.Replacement` (near `:136`) to
   `record Replacement(Line line, int index, StaffElement newElement)`, with `oldElement()`
   a method reading `line.getElement(index)` — the shape `Deletion.deletedElements()` near
   `:198` already has. `forReplacement` near `:293` stops passing the old element. Its
   readers call the method and do not otherwise change: `Hairpin` near `:203`, `Tuplet`'s
   replacement arm, `Span`'s default bridge, and `endingEffect`. Keep the class Javadoc
   statement that a `Replacement` is ephemeral — valid only until its line changes.
8. Change `Song.indexOfLine` to return `@Nullable Integer` for a line not in the song.
   `Line.index()` (near `Line.java:156–160`) keeps its throw, for callers holding a line
   known to be in its song.
9. Convert the three sites that call `contains` and then `index()` so each calls
   `indexOfLine` once and tests for null: `Line.nextLineRunningKey` near `Line.java:340`,
   `LineSpans.boundOf` near `:180–185`, and `ModificationSession` near `:492–496` — the
   song-membership half that Phase 1b task 3 deliberately left in place.
10. Change the `runEnd` local in `LineSpans.mergeAdjacentSpans` (near `:396`, `:405`) to
    `@Nullable Integer`, null exactly when `run` is empty. That null test replaces the
    `!run.isEmpty()` test, and the trailing `applySpanRun` runs only when it is not null.
11. Change `Song.firstNonEmptyLineIndex` (near `Song.java:730`, `:741`) to
    `@Nullable Integer`. Its one reader, `Song.firstElement` near `:750`, tests for null.
    Delete `-1` from its Javadoc.
12. Change `Hairpin.resolveBeginIndex` and `resolveEndIndex` (near `Hairpin.java:384`,
    `:398`, `:406`, `:420`) to `@Nullable Integer`, and make `outcomeForDeletion` (near
    `:243–244`) test the two results for null where it tests `< 0` today. Delete `-1` from
    both Javadocs.

---

## ✅ Phase 4b: Lyric Run Indices

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/LyricRun.java, src/main/java/songscribe/dom/StaffElement.java  <br>
**Recommended model/effort:** Fable, low — three `@Nullable Integer` conversions, and one parameter whose "no predecessor" sentinel needs a contract decision.

Read `docs/lyrics.md` before starting.

### Tasks

1. Change `StaffElement.indexOfLyricForVerse` (near `:347`) to `@Nullable Integer`.
   `getLyricForVerse` near `:364` and `putLyric` near `:399` test for null instead of `< 0`.
   Delete `-1` from its Javadoc.
2. Change `LyricRun.nextLyricBearingIndex` (near `:748`), `previousLyricBearingIndex` (near
   `:762`) and `previousTextBearingIndex` (near `:778`) to `@Nullable Integer`, and make
   every reader test for null. Find the readers with
   `mcp__serena__jet_brains_find_referencing_symbols`; `fixSuccessorSyllabic` near `:683`,
   `hasFollowingTextBearingLyric` near `:801` and `endDanglingChainsForVerse` near `:1204`
   are among them. Delete `-1` from the three Javadocs.
3. Replace the `-1` "no predecessor" arguments. `endDanglingChainsForVerse` near `:1204`
   calls `nextLyricBearingIndex(-1, verse)` to mean "the first lyric-bearing index"; add
   `firstLyricBearingIndex(int verse)` for that and call it. `fixSuccessorSyllabic(-1, verse,
   false)` near `:1224`, and the same argument `adjustNeighborsForLyricDeletion` passes, mean
   "the deleted lyric had no predecessor"; make that parameter `@Nullable Integer` and write
   its `@param` naming what null means. Find every caller passing `-1` with
   `mcp__serena__jet_brains_find_referencing_symbols` on `fixSuccessorSyllabic`, and delete
   the comment near `:1221–1223` that explains the sentinel.
4. Delete the comment near `:1206–1207` saying the null branch is "unreachable" but "kept so
   the method stays safe". After task 2, NullAway requires the test; the comment narrates a
   decision.

---

## ✅ Phase 5: Grace Mode Enablement Default

**Status:** Complete  <br>
**BlockedBy:** 3a, 6, 12, 12a  <br>
**Files:** src/main/java/songscribe/ui/action/UIAction.java, src/main/java/songscribe/ui/selection/ActionReflector.java, src/main/java/songscribe/ui/edit/GraceModeManager.java, src/main/java/songscribe/ui/playback/LoopPlaybackAction.java, src/main/java/songscribe/ui/playback/PlayWithRepeatsAction.java, every `UIAction` subclass under src/main/java/songscribe/ui/action/  <br>
**Recommended model/effort:** Fable, low — decides, for every action, whether it may run while a grace note is unpaired; a wrong answer corrupts grace state silently.

Between entering grace mode and pairing, `GraceModeManager` holds the grace note's index,
the host index from `getHostInsertionIndex`, and the notes in `savedAccidentalStates`
across user input, while the grace note itself sits on the line untracked. Any input that
inserts, removes or replaces an element, replays undo, or replaces the document
invalidates what it holds. `GraceModeManager.keyPressed` consumes only Escape, and
`mousePressed` consumes presses only in `GRACE_NOTE_INSERT`. Today an action opts in to
being disabled with `Flag.DISABLE_IN_GRACE_MODE`, and an action that forgets stays live:
`UndoAction`, `RedoAction`, `OpenAction`, `NewAction` and `RevertToSavedAction` are live
today and each corrupts grace state. This phase inverts the default, so an action is
disabled in grace mode unless it declares itself safe.

### Tasks

1. Inventory every `UIAction` with `mcp__serena__jet_brains_type_hierarchy` on `UIAction` at
   unlimited depth, and every reader of the flag with
   `mcp__serena__jet_brains_find_referencing_symbols` on
   `UIAction/Flag/DISABLE_IN_GRACE_MODE`. Nested and anonymous subclasses count; do not stop
   at the files under `ui/action/`.
2. Replace `Flag.DISABLE_IN_GRACE_MODE` with `Flag.ENABLED_IN_GRACE_MODE` in `UIAction`, with
   Javadoc stating that an action lacking it is disabled while `GraceModeManager.isActive()`.
   `enableFromGraceModeState` becomes
   `hasFlag(Flag.ENABLED_IN_GRACE_MODE) || !GraceModeManager.isActive()`.
3. `GraceModeManager.resetState` restores the actions it disabled, which are now the ones
   lacking the flag. Rename `ActionReflector.restoreActionStatesWithFlag` to
   `restoreActionStatesLackingFlag` with `mcp__serena__jet_brains_rename`
   (`rename_in_comments: false`), invert its `hasFlag` test, and rewrite its Javadoc.
4. Remove `Flag.DISABLE_IN_GRACE_MODE` from every constructor that passes it today. Those
   actions are disabled by default now, and none of them gains the new flag.
5. For every action that does not pass the old flag today, decide whether it carries
   `Flag.ENABLED_IN_GRACE_MODE`, and beside the flag state the present-tense reason it is
   safe where the action's name does not make that obvious. Already settled:
   - `UndoAction`, `RedoAction`, `OpenAction`, `NewAction` and `RevertToSavedAction` do not
     carry it.
   - `ElementTypeAction` carries it. Its six `Role.DURATION` notes reach neither
     `applyToSelectionIfActive` nor `replaceTerminalIfSelected` and only re-arm the preview
     duration, and `enterGraceNote` picks the host's duration through
     `DURATION_ACTION_GROUP.select`, which runs `perform`. Without the flag, entering grace
     mode breaks.
   - An action that acts only on a selection is safe. Grace mode runs inside EDIT mode,
     where a lyric is the only selectable thing (`LineSelectionHandler.handleEditModePress`;
     a staff-line hit or alt+click switches to SELECT mode first, `LineComponent:987–990`),
     `SelectionCoordinator.getSelection` answers null for a lyric selection, and
     `getSelectionSize` counts only a range. `CutAction`, `CopyAction` and `DeleteAction`
     are of this kind; `DeleteAction` deletes a syllable and leaves every index in place,
     and `canDeleteLine` is false without a staff-line selection.
   - `AccidentalAction`, `ArticulationAction`, `DotAction`, `DynamicMarkingAction`,
     `NoteOnlyAction`, `FinalDoubleBarlineAction` and the `PreviewElementAction` family act
     on a selection or on the preview. Read each `perform` and decide.
   - `ZoomAction`, `ZoomLevelAction`, `AboutOpenAction`, `PreferencesOpenAction`,
     `SongSettingsOpenAction`, `SaveAction`, `CloseWindowAction` and `QuitAction` carry the
     flag unless their `perform` reaches the document model.
   - `PlayStopAction`, `RewindAction` and the `SequencerAction` family do not edit the line,
     but `PlaybackController` pushes playing indices into `LineComponent` that grace mode is
     about to shift. Decide from what `LineComponent.setPlaying`'s readers do with a stale
     index.
6. `ModeCycleButton` is not a `UIAction`; it disables itself on
   `GraceModeStateDidChangeNotification` and does not change here.

---

## ✅ Phase 6: Swing Index Boundary

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/util/UIUtils.java, src/main/java/songscribe/ui/binding/Controls.java, src/main/java/songscribe/ui/component/BaseLabel.java, src/main/java/songscribe/ui/component/DurationListCellRenderer.java, src/main/java/songscribe/ui/dialog/OtherValueComboBox.java, src/main/java/songscribe/ui/dialog/fontchooser/panes/SizePane.java, src/main/java/songscribe/ui/action/ActionGroup.java, src/main/java/songscribe/ui/menu/TupletMenuItems.java, src/main/java/songscribe/ui/dialog/SongSettingsDateInputRow.java, src/main/java/songscribe/ui/dialog/PreferencesDialog.java  <br>
**Recommended model/effort:** Fable, low — decides where the Swing boundary converts, which is a contract decision; the edits themselves are small.

Swing defines `-1` for two unrelated things, and this phase keeps them distinct and stops
either reaching the domain.

### Tasks

1. Add `COMBO_DISPLAY_INDEX` to `src/main/java/songscribe/util/UIUtils.java`, documenting
   that it is the `ListCellRenderer` index for a closed combo's display cell. Add
   `NO_SELECTED_INDEX` as a **private** constant in
   `src/main/java/songscribe/ui/binding/Controls.java`, documenting that it is
   `JComboBox`/`JList`'s "no selection". They come from different APIs and mean different
   things, so they are never one shared constant; `NO_SELECTED_INDEX` is private because
   `Controls` is the only place that converts it.
2. Use `UIUtils.COMBO_DISPLAY_INDEX` at `BaseLabel` near `:37`, `DurationListCellRenderer`
   near `:123`, and in the `OtherValueComboBox` Javadoc near `:370`.
3. Write the contract for `Controls.itemIndex` and `Controls.selectedIndex` before changing
   them: each becomes `Property<@Nullable Integer>`, reading null for "nothing selected" and
   writing `NO_SELECTED_INDEX` back to Swing. State that the conversion happens here so
   nothing below the view holds a Swing sentinel. Remove the `-1` from their Javadoc near
   `:241`, `:245`, `:247` and `:357`, and from the `OtherValueComboBox` Javadoc near `:175`
   and `:207`.
4. Implement that conversion. Update the two bindings over these views to test for null:
   `SongSettingsDateInputRow` (over `itemIndex`) and `PreferencesDialog` (over
   `selectedIndex`). Confirm the set with
   `mcp__serena__jet_brains_find_referencing_symbols` on both properties rather than
   trusting this list.
5. In `SizePane` near `:93`, ask `contains` instead of testing `DefaultListModel.indexOf`.
   The index is only tested, never used, so no constant is needed.
6. Move the selection painting that `BaseLabel`'s constructor (near `:37–43`) and
   `DurationListCellRenderer.configure` (near `:123–129`) both hold into one `BaseLabel`
   method, and have `configure` call it. This is duplicated logic, not a coincidence of
   shape.
7. In `src/main/java/songscribe/ui/action/ActionGroup.java`: `remove(T)` leaves `selected`
   and `previousSelected` pointing at a removed action, which makes `selectNext`'s
   `index == -1` test near `:145–149` reachable. First run
   `mcp__serena__jet_brains_find_referencing_symbols` on `ActionGroup/remove` and
   `ActionGroup/selectNext`. If neither has a caller, delete both methods and the
   `List.indexOf` test with them rather than repairing dead code. If either has a caller,
   make `remove` clear whichever of `selected` and `previousSelected` is the removed action,
   which makes the `index == -1` test dead, and delete the test.
8. Delete `TupletMenuItems.NO_GRADE` near `:45`. `existingGrade` (near `:81`, `:85`) returns
   `@Nullable Integer`, and `rebuild` near `:63` compares with `Objects.equals`.

---

## ✅ Phase 7: Legacy IO and String Indices

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/util/StringUtils.java, src/main/java/songscribe/util/FileUtils.java, src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/SongIO.java  <br>
**Recommended model/effort:** Fable, low — `SegmentParts` turns an outside API's sentinel into a domain value, and one parser stops duplicating another.

`io/` outside `io/musicxml/` is the legacy read-only path. Never add a persisted field
here.

### Tasks

1. Add `INDEX_NOT_FOUND` to `src/main/java/songscribe/util/StringUtils.java`, documenting
   that it is what `String.indexOf` and `String.lastIndexOf` answer when the needle is
   absent. Use it at `FileUtils` near `:47` and `:61`, and `LineIO` near `:78`.
2. Change `LineIO.LineReader.SegmentParts` (near `:199–214`) to carry
   `@Nullable String data` — the text after the second comma, or null — in place of
   `secondComma`. Its readers use the index only to cut out that text (near `:366–368` and
   `:432–434`), so the field was an outside API's sentinel doing duty as SongScribe's own
   "no data portion".
3. Change `parseTrillPairs` (near `:630–646`) to call `parseSegmentAB` and read `data`. It
   repeats `parseSegmentAB`'s parsing inline today, which is duplicated logic.
4. Change the `byLineIndex` local in `SongIO.parseLegacyAttributionBlob` near `:786` to
   `@Nullable Integer`.
5. Leave the `split(…, -1)` limits alone — they are an argument to `String.split`, not a
   sentinel: `LegacyLyricsImporter:76`, `SongIO:785`, `StringUtils:166` and `:288`,
   `AttributionFormatter:158`.

---

## ✅ Phase 8: Lyric Layout Records

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/layout/LyricLayoutBuilder.java, src/main/java/songscribe/layout/LyricConnectorLayout.java, src/main/java/songscribe/ui/renderer/LyricConnectorRenderer.java  <br>
**Recommended model/effort:** Fable, low — two records replacing fields that are set and reset together; the grouping is the design decision.

Read `docs/lyrics.md` before starting — syllables, hyphen chains and melismas have
project-specific conventions.

### Tasks

1. In `LyricLayoutBuilder.ExtenderState` (near `:499–570`), replace `extenderActive`,
   `extenderStartXSs`, `extenderColumnIndex` and `syllableSpansGraceHostUnion` with one
   `@Nullable ActiveExtender(double startXSs, @Nullable Integer sourceColumnIndex, boolean
   syllableSpansGraceHostUnion)`. A leading continuation is
   `new ActiveExtender(0.0, null, false)`.
2. Replace `pendingHyphenStartXSs` (`-1` near `:197`, `-1.0` near `:506`) and
   `pendingHyphenColumnIndex` with one `@Nullable PendingHyphen(double startXSs, int
   columnIndex)`.
3. Update the readers near `:196–215`, `:428–429`, `:460`, `:479`, `:488`, `:494`, `:521`
   and `:563` to read the records. `emitDanglingHyphen`'s loop start near `:479` is then only
   ever `columnIndex + 1` of a present hyphen, so it needs no absent case.
4. Change `LyricConnectorLayout.sourceElementIndex` to `@Nullable Integer` and delete
   `LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX` near `:43–50`. Update the readers in
   `LyricLayoutBuilder` and `LyricConnectorRenderer`.

---

## ✅ Phase 9a: MusicXML Cross-Line Spans

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/io/musicxml/MeasureMapper.java  <br>
**Recommended model/effort:** Fable, low — a reader-contract change on the load path, and a nullable conversion whose readers must be found.

Read `docs/musicxml-object-model.md` first. Only SongScribe-authored files are read —
foreign input is rejected at the provenance gate — so no effort goes into tolerating
arbitrary MusicXML.

### Tasks

1. `MeasureMapper.mapPart` collects every note of the part, across all lines, into one list
   (near `:260–268`), so a resolver can pair a start note on one line with an end note on
   another. Make `resolveBeams` (near `:1238`), `resolveTrills` (near `:1407`) and
   `resolveTuplets` (near `:1314`) / `buildTuplet` each check the start note's line against
   the end note's and, when they differ, drop the span with a warning. A span across two
   lines cannot be represented (`LineSpans.requireRangeOf` throws for it), so the reader
   builds no span and reads the rest of the document. This applies to tuplets too, although
   a tuplet's unclosed start or orphan stop still throws `DocumentValidation.corrupt`.
   `Tuplet.hasValidSpan(line)` does not catch this, because it reads each endpoint's index
   on that endpoint's own line.
2. Leave `resolveHairpins` (near `:1044`) and `resolveEndings` (near `:638`) alone — each
   starts over at every line, so neither can pair across lines.
3. Leave the throw in `LineSpans.requireRangeOf` (near `:133`) in place. It states that a
   cross-line span cannot be represented, which is still true. Do not edit `LineSpans.java`.
4. Change `MeasureMapper.lineStartMeasureIndex` (near `:211`, `:215`) to
   `@Nullable Integer`, and make its readers in this file test for null.

---

## ✅ Phase 9b: MusicXML Index Conversions

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/io/musicxml/MusicXmlSerializer.java, src/main/java/songscribe/io/musicxml/MeasureBuilder.java, src/main/java/songscribe/io/musicxml/ScorePartwiseBuilder.java, src/main/java/songscribe/dom/TupletLoadPass.java  <br>
**Recommended model/effort:** Sonnet, medium — three `@Nullable Integer` conversions and one constant deletion, each with its readers named.

For each owner below, confirm its readers with
`mcp__serena__jet_brains_find_referencing_symbols` rather than trusting the line numbers,
make each reader test for null, and delete `-1` from the owner's Javadoc.

### Tasks

1. Change `MusicXmlSerializer.indexOfChild` and `lastIndexOfChild` (near `:863`, `:873`) to
   `@Nullable Integer`. Both live on the nested `MetronomeReorderingWriter` class, so their
   name paths are `MusicXmlSerializer/MetronomeReorderingWriter/indexOfChild` and
   `.../lastIndexOfChild`. `orderedCalls` near `:849` tests both for null before `add` and
   `remove`.
2. Change `MeasureBuilder.positionOf` (near `:815`, `:829`) to `@Nullable Integer`, and make
   its caller in `ScorePartwiseBuilder` test for null.
3. Delete `TupletLoadPass.NO_POPULATED_LINE` near `:64`. `firstPopulatedLineIndex` (near
   `:219–231`) returns `@Nullable Integer`, and `settleTuplets` near `:172` tests for null.
4. Do not touch `MeasureMapper.java`. Its conversions belong to Phase 9a.

---

## ✅ Phase 10: Layout Indices

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b, 2  <br>
**Files:** src/main/java/songscribe/layout/LayoutHitTester.java, src/main/java/songscribe/layout/LayoutResult.java, src/main/java/songscribe/layout/SweepRange.java, src/main/java/songscribe/layout/AttributionContent.java, src/main/java/songscribe/layout/SpringSpacer.java, src/main/java/songscribe/layout/ElementColumn.java, src/main/java/songscribe/layout/ElementColumnBuilder.java, src/main/java/songscribe/layout/HorizontalSpacingCalculator.java, src/main/java/songscribe/layout/OpticalSpacing.java, src/main/java/songscribe/ui/component/score/PreviewElementManager.java, src/main/java/songscribe/ui/component/score/LineSelectionHandler.java  <br>
**Recommended model/effort:** Fable, low — one extracted record and several conversions; `SpringSpacer`'s inner scan needs a named result type rather than three travelling locals.

Read `.claude/guides/spatial-units.md` before touching anything measured in staff-spaces
or pixels.

### Tasks

1. Change `LayoutHitTester.findElementAtXSs` (near `:175`, `:192`, `:212`) to
   `@Nullable Integer`, and `LayoutResult.findElementAtXSs`, which delegates to it (Javadoc
   near `LayoutResult.java:662`). In `PreviewElementManager`, assign null in grace mode near
   `:609` and test for null near `:615` and `:624`.
2. Change the `begin` and `end` locals in `SweepRange.overlapping` (near `:154`, `:155`,
   `:161`, `:169`) to `@Nullable Integer`. Update the caller in `LineSelectionHandler`.
3. Change `AttributionContent.firstSubAttributionIndex` (near `:216`, `:226`) to
   `@Nullable Integer`.
4. In `SpringSpacer.compress`, `worstGap` (near `:157`) travels with `worstViolationSs` and
   `worstAtStrut`. Extract the inner scan (near `:161–185`) into a method returning
   `@Nullable Clamp(int gap, boolean atStrut)`, null when no free gap violates a bound. The
   `worstGap < 0` test near `:189` becomes a null test.
5. Delete `ElementColumn.NO_BEAM_GROUP` near `:61`. `beamGroupId` (near `:92`) becomes
   `@Nullable Integer`, `sharesBeamGroupWith` (near `:603`) tests for null, and
   `ElementColumnBuilder` near `:229` passes null. Update `OpticalSpacing`, the other caller
   of `sharesBeamGroupWith`.
6. Leave every `-1` in `LayoutEngine`, `BeamScoring` and `StackingUtils` alone — they are
   signs and directions, not sentinels.

---

## ✅ Phase 11a: Render Frame Types

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b, 2, 3a, 4  <br>
**Files:** src/main/java/songscribe/ui/renderer/ElementFrame.java, src/main/java/songscribe/ui/renderer/LineInvariants.java, src/main/java/songscribe/ui/renderer/SlideRenderer.java, src/main/java/songscribe/ui/renderer/TieRenderer.java, src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java, src/main/java/songscribe/ui/renderer/RenderingUtils.java, src/main/java/songscribe/ui/renderer/StaffElementRenderer.java, src/main/java/songscribe/ui/renderer/RenderContext.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/component/score/LineRenderer.java, src/main/java/songscribe/ui/component/score/ScoreComponent.java, src/main/java/songscribe/ui/component/score/TextPanel.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/layout/HitRegionBuilder.java  <br>
**Recommended model/effort:** Fable, low — a shared record created once per paint, a method split that makes an ignored parameter unpassable, a count replacing an off-by-one level, and one real defect.

### Tasks

1. Replace `ElementFrame`'s `previewShiftFromIndex` (`-1` for none) and `previewShiftSs`
   with one `@Nullable PreviewShift(int fromIndex, double shiftSs)` record, created once per
   paint at line level and shared by reference into every per-element frame. Delete
   `hasPreviewShift()`. Add `PreviewShift.displaces(int index)` and use it in place of the
   `hasPreviewShift() && index >= previewShiftFromIndex()` expression repeated at
   `SlideRenderer` near `:344` and `LineRenderer` near `:324`, `:460` and `:558` — that
   repeated conditional is duplicated logic and must not survive in four places.
2. Split `LineInvariants.colorFor(HitTarget, int)` into `colorFor(HitTarget)` for every
   variant and `colorFor(HitTarget.Element, int elementIndex)` for the element variant.
   `TieRenderer` near `:159` and `BeamGroupRenderer` near `:139–140` pass a placeholder
   index today, and `SlideRenderer` near `:202` passes one that is ignored; after the split
   none of them can pass an index at all. `RenderingUtils.decorationColor` near `:167`
   branches on the variant. Delete `LineInvariants.NO_ELEMENT_INDEX` near `:75–80`.
3. Delete `RenderContext.getPlayingLine` and `getPlayingNote` (Javadoc near
   `RenderContext.java:81`, `:86`) with their `ScoreView` implementations near
   `:1414–1426`. Both return a hard-coded `-1`, `ScoreView` is the only implementation, and
   nothing calls either. Run `mcp__serena__jet_brains_find_referencing_symbols` on
   `RenderContext/getSelectedLine` too: it also reports no callers, so delete it rather than
   converting it, and only convert it to `@Nullable Integer` if that lookup finds one.
4. Change `ScoreComponent.contentXPx` (near `:143`, Javadoc near `:271`) from `float` to
   `@Nullable Float`. `-1` means "center on own width" today, and `resolveContentX` tests
   `>= 0`, so a legitimately negative shared X — a lyric union wider than the line, from
   `TextPanel:164` — is silently taken for "unset" and each component centers on its own
   width. That is the defect being fixed: `resolveContentX` tests for null, and `TextPanel`
   near `:170–172` passes null.
5. Change `Beam.beamLevel` to `beamStackCount()`, returning the count rather than
   `maxBeamCount - 1`. `Beam.estimatedBeamBottomAboveYSs` near `:252` and `HitRegionBuilder`
   near `:722` add 1 back today and stop doing so; `BeamGroupRenderer` near `:199` tests for
   `-1` today. `BeamGroupRenderer.renderBeams` near `:114` returns early when the count is
   `0`, and `doDrawBeams` loses its `level` parameter, which it reads only for that test.

---

## ✅ Phase 11b: Playing and Frame Index Conversions

**Status:** Complete  <br>
**BlockedBy:** 8, 11a  <br>
**Files:** src/main/java/songscribe/ui/renderer/ElementFrame.java, src/main/java/songscribe/ui/renderer/LineInvariants.java, src/main/java/songscribe/ui/renderer/StaffElementRenderer.java, src/main/java/songscribe/ui/renderer/LyricTextRenderer.java, src/main/java/songscribe/ui/renderer/RenderingUtils.java, src/main/java/songscribe/ui/renderer/BeamGroupRenderer.java, src/main/java/songscribe/ui/renderer/LyricConnectorRenderer.java, src/main/java/songscribe/ui/component/score/LineRenderer.java, src/main/java/songscribe/ui/component/score/PreviewElementOverlay.java, src/main/java/songscribe/ui/component/score/LineComponent.java, src/main/java/songscribe/ui/component/score/PreviewOverlayRegistry.java, src/main/java/songscribe/ui/playback/PlaybackController.java  <br>
**Recommended model/effort:** Sonnet, medium — `@Nullable Integer` conversions over owners whose readers are already enumerated, and one record whose shape is named here; no contract decision.

For each owner below, confirm its readers with
`mcp__serena__jet_brains_find_referencing_symbols` rather than trusting the line numbers,
make each reader test for null, and delete `-1` from the owner's Javadoc. Introduce no type
beyond `Playing` and change no method signature beyond what is named here.

### Tasks

1. Change `ElementFrame.currentElementIndex` (near `:46`, `:48`, `:88`, `:96–101`) to
   `@Nullable Integer`, and update its readers: `StaffElementRenderer` near `:416`,
   `LyricTextRenderer` near `:91`, `RenderingUtils` near `:126` and `:167`, and
   `PreviewElementOverlay` near `:193`. `RenderingUtils.getDecorationColor`'s first branch
   tests this for null — Phase 1a left that site asking `contains`, and this task supplies
   the null test it needs.
2. Delete `LineInvariants.NO_VERSE` near `:72–73`. `activelyEditedVerse` (near `:135`,
   `:262–267`, `:585`, `:628–630`) becomes `@Nullable Integer`, and so do `LineRenderer`
   near `:177` and `LyricConnectorRenderer` near `:103`.
3. Add `record Playing(int noteIndex, @Nullable Integer graceNoteIndex)` on
   `LineInvariants`, with Javadoc stating that a grace index exists only while a note is
   playing, which is why the pair is one record rather than two fields. Replace
   `playingNoteIndex` and `playingGraceNoteIndex` (near `:589–590`, and the `Builder`'s
   copies) with one `@Nullable Playing playing`, and the getters `getPlayingNoteIndex` and
   `getPlayingGraceNoteIndex` (Javadoc near `:458`, `:463`) with `playing()`.
   `isElementPlaying` and `isLyricSpanPlaying` test for null, then compare.
4. Replace `LineComponent.setPlayingIndices(int, int)` (near `:321`) with
   `setPlaying(@Nullable Playing)`, and its two playing fields (near `:140–144`, `:318–337`)
   with one `@Nullable Playing`, compared with `Objects.equals` before the repaint.
   `LineComponent.pressHitIndex` (near `:174`, `:179`, `:1017`) and
   `LineComponent.elementIndexOf` (near `:782`, `:797`, `:818`) become `@Nullable Integer`.
5. In `PlaybackController`, pass `null` where `setPlayingIndices(-1, -1)` is called today at
   `:221` and `:276`, and `new Playing(noteIndex, line.precedingGraceNoteIndex(noteIndex))`
   at `:229`. Change `previousPlayingLine` (near `:106`, `:218`, `:273`, `:279`) to
   `@Nullable Integer`.
6. Change the `startSubBeam` local in `BeamGroupRenderer.doDrawBeams` (near `:243`, `:247`,
   `:251`, `:255`) to `@Nullable Integer`.
7. In `PreviewOverlayRegistry` near `:153`, the comment says restoring the preview "resets
   the insertion index to -1". `restorePreviewElement` drops the hover (`hover = null`).
   Make the comment say that.

---

## ✅ Phase 12a: Grace Mode State

**Status:** Complete  <br>
**BlockedBy:** 10  <br>
**Files:** src/main/java/songscribe/ui/edit/GraceModeManager.java, src/main/java/songscribe/ui/component/score/PreviewElementManager.java  <br>
**Recommended model/effort:** Fable, low — one record replacing four fields that are set and cleared together; the grouping is the design decision.

### Tasks

1. In `GraceModeManager` (fields near `:98–108`), `graceNote`, `graceNoteIndex`, `graceLine`
   and `graceLineComponent` are all assigned in `enterGraceNote` and all cleared in
   `resetState`. Replace the four with one
   `@Nullable ActiveGrace(NoteElement note, int index, Line line, LineComponent component)`
   record, present exactly while the state is not `INACTIVE`. Write the record's Javadoc
   stating that, so a present record proves all four. `cachedHostInsertion` stays a separate
   field: it exists only in `GRACE_NOTE_INSERT`.
2. Update every reader of the four fields to read the record, including
   `getGraceLineComponent`, `getHostInsertionIndex`, `isMouseLeftOfGraceNote`,
   `isMouseRightOfGraceNote` and `enterGraceNotePaired`. Find them with
   `mcp__serena__jet_brains_find_referencing_symbols` on each field rather than trusting
   this list.
3. Convert `hostPreviewGraceIndexOn`, `internalGetCancelThresholdPx` and
   `internalGetConnectThresholdPx` (near `:214`, `:218`, `:223`, `:227`, `:285`, `:294`,
   `:820`, `:874`, `:933`, `:939`, `:948`, `:954`, `:960`) to `@Nullable Integer`, and make
   `PreviewElementManager`, which reads `hostPreviewGraceIndexOn`, test for null. Delete
   `-1` from their Javadoc.

---

## ✅ Phase 12: Edit and Selection Indices

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b, 2, 3a, 10, 11a, 11b, 12a  <br>
**Files:** src/main/java/songscribe/ui/edit/InsertionPointMode.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/ui/SlideOperations.java, src/main/java/songscribe/ui/component/LyricTargetResolver.java, src/main/java/songscribe/ui/component/LyricEditor.java, src/main/java/songscribe/ui/action/EditLyricAction.java, src/main/java/songscribe/ui/component/score/NoteDragHandler.java, src/main/java/songscribe/ui/component/score/InsertionMarkerOverlay.java, src/main/java/songscribe/ui/component/score/PreviewElementManager.java, src/main/java/songscribe/ui/component/score/GraceGlissandoPreviewOverlay.java, src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/component/score/LineComponent.java  <br>
**Recommended model/effort:** Sonnet, medium — mechanical `@Nullable Integer` conversions over owners whose readers are already enumerated; no new type and no contract decision.

For every owner below, find its readers with
`mcp__serena__jet_brains_find_referencing_symbols` rather than trusting the line numbers,
make each test for null, and delete `-1` from the owner's Javadoc.

### Tasks

1. In `InsertionPointMode`, first run `mcp__serena__jet_brains_find_referencing_symbols` on
   `InsertionPointMode/getTargetIndex`. It reports no callers, so delete `getTargetIndex`,
   the `targetIndex` field and `NO_INDEX` (near `:216`) together, along with the `-1` named
   in the Javadoc near `:278`. Convert them to `@Nullable Integer` instead only if that
   lookup finds a caller.
2. Convert `MusicEditOperations.validateEndingStructure` (near `:855`, `:858`, `:871`,
   `:879`, `:888`, `:901`, `:907`).
3. Convert `SelectionCoordinator.activeLineIndex`, `findLineIndex` and `getSelectedLine`
   (near `:105`, `:106`, `:173`, `:190`, `:255`, `:657`, `:670`, `:743`, `:746`, `:753`).
   `getSelectedLine` is read by `ScoreViewController` and `ScoreView`; those two files belong
   to other phases, so make only the null-test change they need.
4. Convert `RangeQueries.nonGraceBegin`, `nonGraceEnd` and `glissandoSourceIndex` (near
   `:168`, `:173`, `:189`, `:194`, `:208`, `:366`, `:405`, `:409`, `:420`, `:425`, `:428`).
   `nonGraceBegin` and `nonGraceEnd` are read by `MusicEditOperations`,
   `glissandoSourceIndex` by `SlideOperations`. `tieAttachmentIndex` is **not** this phase's
   — it is split into directional queries by Phase 3a.
5. Convert `LyricTargetResolver.resolveLyricTarget`, `findNextEligibleIndex` and
   `findPreviousEligibleIndex` (near `:42`, `:47`, `:52`, `:65`, `:70`, `:80`). Readers are
   `ScoreView`, `EditLyricAction`, `LineComponent` and `LyricEditor`; touch only what the
   null test needs in files owned by other phases.
6. Convert `NoteDragHandler.dragElementIndex` (near `:61`, `:319`).
7. Convert `InsertionMarkerOverlay.targetIndex` (near `:56`, `:59`).
8. Convert `PreviewElementManager.graceHostPreviewSourceIndexOn` (near `:370`, `:378`),
   read by `GraceGlissandoPreviewOverlay`.

---

## ✅ Phase 13: MIDI Indices

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/midi/SlideMidiHelper.java, src/main/java/songscribe/midi/MidiSequenceBuilder.java, src/main/java/songscribe/midi/LineTrackBuilder.java, src/main/java/songscribe/ui/playback/MidiController.java, src/main/java/songscribe/ui/playback/PlayThread.java  <br>
**Recommended model/effort:** Sonnet, medium — two field conversions, one named constant, and the removal of parameters whose every branch is dead.

### Tasks

1. Add a package-private `NO_TIMESTAMP` constant to
   `src/main/java/songscribe/ui/playback/MidiController.java`, documenting that Java Sound's
   `Receiver.send` defines it as "no timestamp". Use it at `MidiController` near `:239`,
   `:268`, `:318` and `PlayThread` near `:89`, `:93`, `:98`, `:114`, `:132`. It must be its
   own constant, not shared with any other API's `-1`.
2. Change `SlideMidiHelper.currentSensitivity` (near `:75–76`, `:152–153`, compared at
   `:275`) to `@Nullable Integer`.
3. Change `SlideMidiHelper.pendingGracePitch` (near `:78–79`, `:210–214`) to
   `@Nullable Integer`: `consumePendingGracePitch` returns `@Nullable Integer`, and
   `hasPendingGracePitch` near `:203` reads the field for null. Update `LineTrackBuilder`,
   which calls both.
4. Delete `MidiSequenceBuilder.buildSequence`'s `endLine` and `endNote` parameters and
   `buildSequenceWithRepeats`'s `endLine`, along with every branch that reads them and the
   Javadoc near `:105–106` and `:212`. Both callers — `buildFullSequence` near `:82` and
   `buildFromNoteToEnd` near `:96` — pass `-1`, so those branches are unreachable.
5. Leave the `-1` at `MidiSequenceBuilder` near `:292` alone: it is a restart one before
   index 0, not a sentinel.

---

## ✅ Phase 14: Dead Code Removal

**Status:** Complete  <br>
**BlockedBy:** 2, 3a, 4  <br>
**Files:** src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/BeatAt.java, src/main/java/songscribe/dom/TempoResolver.java, src/main/java/songscribe/dom/TupletValidator.java, src/main/java/songscribe/util/Utils.java, src/test/java/songscribe/io/musicxml/MusicXmlRoundTripSupport.java  <br>
**Recommended model/effort:** Sonnet, low — deletions whose reference lookups are already done; each task states what to confirm before removing.

Before each deletion, confirm no callers with
`mcp__serena__jet_brains_find_referencing_symbols`. Report and stop if a lookup disagrees
with what the task states, rather than deleting anyway.

### Tasks

1. Delete `Song.APPEND` near `Song.java:147`. Its only use is `addLine(Line)` calling
   `addLine(APPEND, line)` near `:888`, and `addLine(int, Line)` mapping it to `lines.size()`
   near `:937`. Make `addLine(Line)` pass `lines.size()` and delete both the constant and the
   mapping.
2. Delete `Utils.arrayIndexOf` near `util/Utils.java:51`.
3. Delete the `BeatAt` record. Its `lineIndex` and `elementIndex` have no readers, and the
   record's Javadoc claims callers use them to recognize a beat barrier when none does. Make
   `TempoResolver.resolveBeatAt` and `Song.resolveBeatAt` (near `Song.java:441`) return the
   `BeatUnit` directly, and make `TupletValidator` near `:184` and `:372` stop calling
   `.beat()`. `BeatAt.NO_DEFINING_EVENT` goes with it. Then rename both `beatUnitAt`
   methods to `beatUnitAt` with `mcp__serena__jet_brains_rename` (`rename_in_comments:
   false`), so neither carries the deleted type's name; the `{@link}` sites in
   `TupletValidator` and `TempoResolver` follow the rename.
4. Delete all four overloads of `MusicXmlRoundTripSupport.assertSpanEquals`
   (`src/test/java/songscribe/io/musicxml/MusicXmlRoundTripSupport.java:239–272`). None has
   a caller.

---

## ✅ Phase 15: Test Proposal

**Status:** Complete  <br>
**BlockedBy:** 1a, 2, 3a, 4  <br>
**Files:** src/test/java/songscribe/  <br>
**Recommended model/effort:** Fable, low — decides which promises the type system now carries and which still need a test, then implements the surviving list.

No existing test asserts a `-1`, so none changes behavior. The test files Phases 3b and 14
touch change only to compile. This phase decides whether anything new is owed.

### Tasks

1. List the contracts this step changed or created: `Line.contains` and
   `Line.getElementIndex`, `Span`'s four endpoint queries,
   `StaffElementRun.previousNonGraceIndex` / `nextNonGraceIndex` / `precedingGraceNoteIndex`
   / `lastNoteIndex` / `EffectiveRange`, `ElementRange`'s widened invariant,
   `LineSpans.mergeOverlapping` with `Absorb`, `Hairpin`'s `HairpinOutcome`,
   `ElementChange.Replacement`, `Song.indexOfLine`, `LyricRun.firstLyricBearingIndex` and
   its three nullable siblings, `GraceModeManager.ActiveGrace`, `LineInvariants.Playing`,
   `UIAction.Flag.ENABLED_IN_GRACE_MODE`'s default, and `MeasureMapper`'s cross-line
   drop.
2. For each, ask which of the three kinds in `~/.claude/guides/design.md` it falls under —
   a real algorithm with logic worth checking, an invariant spanning several calls, or
   behavior with a known-correct corpus. Anything that fits none of the three does not belong
   in the suite. A promise the type now carries — that only `effectiveRange` can build an
   `EffectiveRange`, that a non-hairpin cannot answer `Reshape`, that a present `ActiveGrace`
   holds all four fields — gets no test, because the compiler enforces it. Of the guards
   this step adds, only `MeasureMapper`'s cross-line drop may be proposed for a test,
   as a guard reached by file input; `Line.getElementIndex`'s throw, the `require` pair's
   throw and `ElementRange`'s `begin < 0` check get none.
3. Report the surviving list as the one table `design.md` requires, with a *Test* and a
   *Kind* column, and include any existing test that should be deleted with the reason. The
   user has approved implementing whatever this table holds, so the table is a record of
   what you chose rather than a gate — do not wait on it.
4. Implement every test the table names, and delete the tests it names for deletion. Read
   `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` first and follow
   them. Write only under `src/test/java/songscribe/`. Report the table alongside the files
   you wrote, so the user can see which promises you decided the type system already
   carries.

---

## ✅ Phase 16: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 1a, 1b, 2, 3a, 3b, 4, 4b, 5, 6, 7, 8, 9a, 9b, 10, 11a, 11b, 12a, 12, 13, 14, 15  <br>
**Files:** —  <br>
**Recommended model/effort:** Fable, low — a failure here is information about which call sites the design did not account for, and reading it that way is the task.

### Tasks

1. Run `./scripts/compile.sh --test` and fix every error before going further. Never use
   `./gradlew`, `gradle`, `javac` or `java -cp`. Treat each failure as a call site the
   conversion did not reach, not as a reason to loosen a type back to `int`.
2. Run `./scripts/test.sh LineDeletionTest StrandedKeyChangeTest KeyChangeStrandingTest
   InheritedKeyPropagationTest` and report the result. A run naming more than four classes
   is denied by `.claude/hooks/no-full-test-suite.sh`.
3. Run `./scripts/test.sh InheritanceChainTest SongAccidentalWalkTest
   EndingBracketGeometryTest MusicXmlStrandedKeyChangeTest` and report the result.
4. Run `./scripts/test.sh LyricRunTest StaffElementLyricsTest` and report the result.
5. Report SUCCESS or the failing output verbatim. Do not report a passing build or suite as
   evidence that the work is correct — green proves integration, never correctness.

---

## ✅ Phase 17: Manual UI Verification

**Status:** Complete  <br>
**BlockedBy:** 16  <br>
**Files:** —  <br>
**Recommended model/effort:** Opus, low — the user drives; this phase only prepares the list and records what they report.

This step changes rendering, selection, playback highlighting, grace mode, preview overlays
and two dialogs. Nothing below is observable in the suite.

### Tasks

1. Ask the user to run the application. Do **not** execute `./scripts/run.sh` without their
   permission.
2. Give them this list to exercise, and record what they report:
   - **Grace mode** — enter grace mode, then confirm Undo, Redo, New, Open and Revert to
     Saved are all disabled, that every menu item still enabled is one Phase 5 gave the
     flag, and that choosing a duration still works while in the mode.
   - **Playback** — play a song and confirm the playing note and playing grace note
     highlight and clear correctly at the start and end of playback.
   - **Preview and insertion** — hover to place an insertion preview and confirm the
     preview's shift, its decoration color, and the insertion marker all render as before.
   - **Selection** — click elements on several lines, confirm selection highlighting is
     correct, and confirm clicking an element on one line while a range is selected on
     another behaves as before.
   - **Lyrics** — type a hyphen chain and a melisma within a line and confirm
     extenders, hyphens and connectors draw correctly. Open the editor on a note with no
     lyric and leave it empty with Tab, Shift-Tab, Enter and a click elsewhere, and clear an
     existing syllable and press Tab; none of these may crash.
   - **Beams** — confirm beamed groups, sub-beams and beam-group spacing render unchanged,
     including a group with no beams.
   - **Dialogs** — open the Key Change dialog and Preferences, change a combo selection in
     each, and confirm the value commits.
   - **Score centering** — open a song whose lyrics are wider than the staff and confirm the
     line still centers correctly.
3. Fix anything they report, then return to Phase 16's gate.
