# Check Findings — design-pass-6 branch against develop

Scope: every file the branch changed since it forked from `develop` — 194 production
files, 22 test files, `docs/lyrics.md`, `docs/span-invalidation.md` and the plan files
under `plans/design-pass-6/`. Four axes ran over four slices of the production code
(`dom` line and span types; `dom` runs and edits with `undo` and `message`; the UI
interaction packages with `binding`; everything else) and two slices of the tests.

Confidence is stated per finding. "Checked by me" means I read the code after the
reviewing agent reported it.

---

## Production bug

### Finding 1 — Deleting a note or a rest after a mid-word melisma splits the word

**Where.** `src/main/java/songscribe/dom/LyricRun.java:150`, in the private method that
settles a hyphenated word across the gap a deletion leaves (`settleWordAcross`). The same
faulty question is asked at `LyricRun.java:836` (the method that ends a word whose next
syllable is gone, `endDanglingWord`), inside the method that re-letters the syllable after
an edited one (`fixSuccessorSyllabic`), and at
`src/main/java/songscribe/ui/component/LyricChainEditor.java:86` (the lyric editor's "am I
inside a hyphenated word" check).

**What the code does now.** A melisma is stored as a syllable followed by text-less
"carrier" lyrics on the notes the extender runs under. When the repair code looks
*backward* for the previous syllable it skips carriers (`previousTextBearingIndex`). When
it looks *forward* it does not: `hasFollowingTextBearingLyric` inspects only the first
element after the syllable that holds any lyric, and answers "no syllable follows" when
that element is a carrier.

**What is wrong.** Take one word, "Glo-ri", where "Glo" is sung over three notes and a
rest stands before "ri":

    Glo (begins word, starts melisma) · carrier · carrier · rest · ri (ends word)

Delete the rest. Nothing of the word or the melisma was deleted, and `docs/lyrics.md`
says so plainly: "Deleting a rest or a barline from inside a word changes nothing."
The code finds "Glo" as the syllable before the gap, asks whether a syllable follows it,
sees the carrier, answers no, and ends the word on "Glo". "ri" is then re-lettered as a
word of its own. The notator sees the hyphen between "Glo" and "ri" disappear. The same
happens when a note *under* the extender is deleted, and for a paired grace note in the
middle of a word, because its host note holds a carrier. Copying a range that contains
such a word goes through `endDanglingWord` and ends the word early in the copy.

The lyric record permits the state: `LineDeletionTest` itself builds a `MIDDLE` syllable
followed by a carrier.

**Would a test in scope have caught it?** No. Every melisma case in
`LineDeletionTest.lyricCases()` uses a single-syllable word ("la"), and every word case
has no melisma, so the two chains are never exercised together.

**What to do instead.** Give the forward direction the same query the backward direction
has: a private `nextTextBearingIndex(index, verse)` that skips carriers, mirroring
`previousTextBearingIndex`. `hasFollowingTextBearingLyric` becomes "`nextTextBearingIndex`
is not null", and `fixSuccessorSyllabic` re-letters the element that query returns rather
than giving up at a carrier. That fixes all four sites at once, because they share the two
methods. Add `LyricCase` rows where the word carries the melisma: a rest deleted between
the carriers and the next syllable, a carrier deleted from inside, and the paired-grace
form.

Confidence: high. Checked by me against the method bodies and the doc.

---

## Design findings

### Finding 2 — "No index" is `null` inside four classes and `-1` again at their edge

**Where.**
- `src/main/java/songscribe/ui/component/score/NoteDragHandler.java:103-106`
  (`getDragElementIndex()`), read once at `LineComponent.java:1002`.
- `src/main/java/songscribe/ui/selection/SelectionCoordinator.java:192-194`
  (`getActiveLineIndex()`), read from `ScoreInputHandler.java:432`, `ScoreView.java:671`,
  `LineSelectionHandler.java:574` and `AttachmentDialogController.java:279`.
- `src/main/java/songscribe/ui/component/LyricEditor.java:1369-1375` (two private lookup
  helpers) and the three methods they feed (`advanceWithIndex`, `openIndexOrDismiss`,
  `breakChainCommitAndAdvance`).
- `src/main/java/songscribe/ui/component/score/InsertionMarkerOverlay.java:72`
  (`setTarget(line, int index)`), called with `-1` from `InsertionPointMode.java:501`.

**What the code does now.** The branch replaced the `-1` "no index" value with a nullable
`Integer` almost everywhere. These four classes made the field nullable and then convert
it back to `-1` at the accessor another class calls (`return dragElementIndex == null ?
-1 : dragElementIndex;`). `SelectionCoordinator` does it for one accessor and not for its
sibling: `getSelectedLine()` returns `null` for "none", `getActiveLineIndex()` returns
`-1`, off the same field.

**What is wrong.** The compiler forces a caller to handle `null`; it cannot force a check
for `-1`. Each class already held the safe value and discarded it at the one point where a
wrong index does damage — the hand-over to the class about to use it. Three concrete
symptoms:

- `InsertionMarkerOverlay.targetIndex` is declared nullable, but clearing the marker
  stores `-1`, so a `targetIndex == null` test can never see the cleared state. It is
  harmless today only because the line field is nulled too and is tested first.
- `NoteDragHandler` keeps a `dragActive` boolean that is set and cleared together with
  `dragElementIndex`. It is a second copy of "is the index present", kept in step by hand,
  and `LineComponent.released()` must consult it to decode the `-1`.
- `RangeQueries.java:336`: the summary sentence of `glissandoSourceIndex` still says "or
  -1 when the range is ineligible"; the method now returns `null`.

`plans/design-pass-6/step-3-unrepresentable-states.md:821-824` left the
`getActiveLineIndex()` call sites "to other phases"; no later phase took them. The other
three sites are in no plan.

**Corrected design.** `null` crosses the boundary.
- `NoteDragHandler.getDragElementIndex()` returns `@Nullable Integer`; `dragActive` is
  deleted and `isDragActive()` becomes `dragElementIndex != null`.
- `SelectionCoordinator.getActiveLineIndex()` returns `@Nullable Integer`, like its
  sibling.
- `LyricEditor`'s two helpers return `@Nullable Integer` and the three methods they feed
  take one, testing `!= null` where they test `>= 0`.
- `InsertionMarkerOverlay` gets `setTarget(LineComponent, int)` and `clearTarget()`, so a
  null line paired with a real index — which the single setter permits and nothing
  produces — cannot be written.
- The `RangeQueries` sentence is corrected.

**What the change touches.** 4 production classes change one signature each; call sites:
1 in `LineComponent`, 4 for the active-line accessor, 9 inside `LyricEditor`, 1 in
`InsertionPointMode`. No test changes: none of these classes has a test.

**Recommendation.** Do it. It completes the conversion the rest of the branch made, at the
four places where the old encoding still reaches a consumer.

Confidence: high. `NoteDragHandler` and `InsertionMarkerOverlay` checked by me; the other
two were reported independently by the Design agent and the Contract agent.

### Finding 3 — Each span type decides for itself what a detached end means, and one forgot

**Where.** `src/main/java/songscribe/dom/Ending.java:333`, `:363`, `:452` (the three
methods that say whether a deletion, a replacement or an insertion invalidates a first- or
second-ending bracket), against `Tie.java:148-158`, `Tuplet.java:305-311` and
`Hairpin.java:165-172`.

**What the code does now.** Before an edit lands, every span on the line is asked whether
the edit invalidates it. A span whose end is attached to no line cannot be affected.
`Tie`, `Tuplet` and `Hairpin` each test for that state first and answer "keep". `Ending`
calls `requireAnchorElementIndex()` / `requireEndElementIndex()`, which throw
`IllegalStateException` in that state. `Ending.isInvalidatedByInsertion` then tests
`anchorIndex >= 0 && endIndex >= 0`, which can never be false, because the accessors it
just called throw rather than return a negative number.

**What is wrong.** One rule — "a span with an end in no line is not affected by an edit to
this line" — is written three times in three subclasses and missing from the fourth. The
`LineSpans` class comment describes a span with detached ends sitting in a registry as a
legitimate state. The reviewing agent could not build an input that leaves an `Ending` in
that state today, so nothing is known to crash; if one is ever reachable, the next edit on
that line fails with an uncaught exception instead of leaving the bracket alone. The next
span type added will have to remember the rule a fifth time.

**Corrected design.** The rule lives once, in the registry. The state the four types share
is "an end this line cannot place" (`SpanBound.ABSENT`: a detached endpoint, or one in a
line the song no longer holds). "No range on this line" is the wrong test, because a tie
may cross to the next line and must still be judged. `LineSpans.judgeableSpans()` yields
only the spans whose two ends the line can both place, and the two callers that put an
edit to spans — `LineSpans.sweep` and `ElementChange.removesConfirmableSpan` — iterate it,
which also covers the hairpin path the sweep enters through `hairpinOutcomeFor`. No span
type sees the state: `Tie.resolveIn` is total, `Hairpin` reads its indices through the
throwing accessors as `Ending` does, and `Ending`'s two dead `>= 0` tests go. A `Tuplet`
with an end in a different line throws `IllegalStateException`; a tuplet never crosses a
line.

**What the change touches.** `LineSpans.java` (one query added, the sweep loop),
`ElementChange.java` (one call), `Tie.java`, `Tuplet.java`, `Hairpin.java` (one guard
removed each), `Ending.java` (two dead tests removed), `Span.java` (the precondition stated
on `outcomeFor`). No test changes.

**Recommendation.** Do it. It removes the one way the four span types disagree.

Confidence: medium. The asymmetry and the dead tests are checked by me. The `Tie` comment
names the reachable case: a cross-line tie whose far line the song no longer holds.

### Finding 4 — A tuplet's note count is a bare number whose valid range lives elsewhere

**Where.** `src/main/java/songscribe/dom/Tuplet.java` (the `grade` field and both
constructors) and `src/main/java/songscribe/dom/TupletValidator.java:310`.

**What the code does now.** A tuplet's printed number (3 for a triplet) is an `int`. Any
`int` is accepted: `new Tuplet(anchor, end, 99, ratio)` compiles and runs. The legal range,
2 to 7, is two constants in `TupletValidator`, tested in one private method. Both file
readers (`MeasureMapper.buildTuplet`, and `LineIO` for legacy files) pass the number
straight from the file into the constructor, and a pass over the loaded document later
drops any tuplet whose number is out of range. `RangeQueries.validGradesFor` loops over
the same two constants to build the tuplet menu.

**What is wrong.** The set of legal values is small and known, the branch gave the ratio a
type of its own (`TupletRatio`) for exactly this reason, and the number beside it was left
raw. A tuplet object can exist in a state every consumer would call illegal, and each
consumer relies on a pass having run first. The reviewing agent judged this deliberate,
because full validation needs the whole document. That is true of *placement* and
*notatability*; it is not true of the range, which needs nothing but the number.

**Corrected design.** A `TupletGrade` record whose constructor rejects a value outside 2
to 7, owning `MIN`/`MAX` and the "every legal grade" sequence that `validGradesFor`
iterates. `Tuplet` holds a `TupletGrade`. The two readers convert at the boundary: a
number outside the range produces the same "tuplet dropped" load warning the load pass
produces today. `TupletValidator.divisionFailure` loses its range branch and
`Reason.BAD_RATIO` loses that producer.

**What the change touches.** One new record; `Tuplet.java`, `TupletValidator.java`,
`TupletLoadPass.java`, `MeasureMapper.java`, `LineIO.java`, `RangeQueries.java`,
`MusicEditOperations.createValidatedTuplet`, `TupletMenuItems`, plus the tuplet fixtures
in `SpanTest`, `LineSpansTest` and `ElementChangeTest`. Any test of the out-of-range
branch of the validator is removed, not rewritten.

**Recommendation.** Do it, in the same spirit as `TupletRatio`. I did not read every
reader of `getGrade()`; the count above comes from the agent's trace and the two
references I checked.

Confidence: medium.

### Finding 5 — The same "which color method" branch is written in two renderers

**Where.** `src/main/java/songscribe/ui/renderer/RenderingUtils.java:169-176`
(`decorationColor`) and `src/main/java/songscribe/ui/renderer/StaffElementRenderer.java:415-419`
(`renderAccidental`).

**What the code does now.** The class that owns coloring rules, `LineInvariants`, has two
methods: one colors an element by selection only, the other also knows about playback and
hover and needs the element's index. A preview element being dragged has no index. Both
renderers therefore write the same three lines: "if the frame has an index call the
second method, otherwise the first".

**What is wrong.** The rule "an element with no index gets selection coloring only" is
stated twice, outside the class that owns coloring. A change to it must be made in both
places, and missing one shows up as a color that differs between a note and its
accidental.

**Corrected design.** `LineInvariants` gets one method that takes the element and the
frame's nullable index and makes the choice. The two renderers call it. The two-argument
public overload becomes private.

**What the change touches.** `LineInvariants.java`, and the 2 call sites. These are the
only two callers of the pattern (reference search by the agent).

**Recommendation.** Do it.

Confidence: high on the duplication (agent's reference search; not re-run by me).

### Finding 6 — One of the five "does this edit invalidate me" hooks has no user

**Where.** `src/main/java/songscribe/dom/Span.java`, `isInvalidatedByModification`
(around line 292).

**What the code does now.** `Span` offers five overridable questions, one per kind of
edit. Four are overridden by `Beam`, `Tie` or `Ending`. The one about an in-place
modification of an element is overridden by no span type; `Tuplet`, the only type that
cares about a modification, overrides the dispatching method (`outcomeFor`) instead.

**What is wrong.** Nothing breaks. A reader adding a span type sees a documented extension
point and must work out that it is unused and that the class's own advice is to override
`outcomeFor`. It is generality with no caller.

**What to do instead.** Delete the hook; the dispatcher answers "keep" for a modification.
One method and one `switch` arm in `Span.java`; no call sites.

Confidence: high. Checked by me: a symbol search over `dom/` finds only the declaration.

---

## Contract findings

### Finding 7 — Proposed change to an existing contract: the MusicXML reader says it rejects what it drops

**Where.** `src/main/java/songscribe/io/musicxml/MeasureMapper.java:107-108` (class
comment), against `endsOnStartLine` (about line 753) and the comment on `resolveTuplets`
(line 1323).

**What it promises now.** "A beam, a tuplet bracket or a trill whose two ends land on
different lines is a span no line can hold, so the pass that pairs it rejects the document
as corrupt."

**What the code does.** It logs a warning, skips that one span and loads the rest. The
method comment 1200 lines further down says so: "dropped with a warning, as a beam or a
trill is". `MusicXmlCrossLineSpanTest` asserts the drop.

**Why it matters.** A reader of the class comment believes such a file is refused. Two
things are possible, and they are different decisions: the class comment is stale and
should say "drops the span and logs a warning"; or the drop is the mistake, since only
SongScribe-authored files are read, SongScribe cannot write such a span, and the same
class rejects a dangling tuplet as corrupt. See the question on this finding.

Whichever way it goes, the rule is stated once, on the class, and the restatement on
`resolveTuplets` is cut. `MusicXmlCrossLineSpanTest`'s class comment claims "with a
warning" and asserts no warning; nothing reads that log line, so the comment is trimmed to
what is asserted rather than a log assertion added.

Confidence: high on the contradiction. Checked by me.

### Finding 8 — Public members nothing calls

- `src/main/java/songscribe/ui/playback/PlaybackController.java:150-158`:
  `getPreviousPlayingLine()` and `setPreviousPlayingLine()`. The branch changed their type
  from `int` to nullable `Integer`. No production code and no test calls either; the class
  reads and writes the field directly. Delete both.
- `src/main/java/songscribe/layout/ElementColumn.java` (about line 103):
  `getBeamGroupId()`. Same story: re-typed and re-documented by the branch, called by
  nothing; `sharesBeamGroupWith` reads the field. Delete it.

Confidence: high. Checked by me: all three reference searches return nothing.

### Finding 9 — Contracts that contradict themselves or omit an error

- `src/main/java/songscribe/ui/dialog/PreferencesDialog.java:241-258`, `indexToProgram`:
  the `@param` line describes `null` as "when the list has no selection", a handled input;
  the `@throws` line says `null` raises `IllegalStateException`. The prose above them has
  it right (the list always has a selection). A caller who reads the `@param` passes `null`
  and crashes. Reword the `@param` as the precondition.
- `src/main/java/songscribe/dom/Beam.java:178-180`, `direction()`: `@throws` names a
  detached anchor only; the body also throws for a detached end. A caller who checks the
  anchor still gets the exception. Name both.
- `src/main/java/songscribe/ui/edit/GraceModeManager.java:259`,
  `getHostInsertionIndex()`: outside grace mode it used to return a meaningless 0 and now
  throws `IllegalStateException`. The comment states the precondition and not the
  consequence. Add the `@throws`.
- `src/main/java/songscribe/ui/MusicEditOperations.java:401`, `toggleTuplet`: throws
  `IllegalStateException` for three different conditions (selection is a strict part of an
  existing tuplet; the toggle info says it cannot toggle; fewer than two non-grace
  elements). The comment mentions the first, in passing, with no `@throws`. Name all three.
- `src/main/java/songscribe/ui/selection/Selection.java:106-116`, `Selection.Range`
  constructor: tests `range.begin() < 0` and documents a `@throws` for it. `ElementRange`
  already rejects a negative begin, so the test cannot fire. A reader cannot tell which of
  the two documented failures is real. Delete the test and its clause.
- `src/main/java/songscribe/dom/Span.java:156`: the comment on `getAnchorLine()` links to
  `LineSpans#appendChild`, which is private. Point it at a reachable member or drop the
  link.

Confidence: high on each (mechanical). The `Selection.Range` and `Beam` items are agent
reports I did not re-read.

### Finding 10 — A paste method takes two values that must agree

**Where.** `src/main/java/songscribe/ui/component/ScoreViewController.java:1332`,
`tryInsertFragment(line, insertIndex, deleteRange)`.

**What the code does now.** When a paste replaces a selection, `insertIndex` must equal the
start of `deleteRange`; only the comment says so. The body reads `insertIndex` in some
places and the range in others.

**What is wrong.** A caller that passes a mismatched pair gets no error and a wrong key or
spacing calculation. Both current callers pass a matching pair.

**What to do instead.** A small sealed value for "where the paste goes" with two shapes —
at an index, or replacing a range — built by each caller; the method derives the index
from it. One method, 2 call sites.

Confidence: medium (agent report; not re-read by me).

### Finding 11 — Five parameters where four is the limit

`src/main/java/songscribe/layout/InsertionSpacingCalculator.java` (about line 379),
`calculateFragmentInsertion(line, fragment, insertIndex, deleteRange, lyricRenderMetrics)`.
The project rule requires a record parameter above four. `insertIndex` and `deleteRange`
are the same coupled pair as in Finding 10, so the value type proposed there replaces two
of the five and brings the count to four.

### Finding 12 — Missing `@return` / `@param` tags, and one unfinished sentence

Mechanical; the tag is what the IDE shows at a call site.

- `dom/Tuplet.java:370` `getRatio()` — no `@return` (it must say what `null` means).
- `dom/Tuplet.java:280-306` `outcomeFor` override — no `@return`; `Hairpin`'s override has one.
- `ui/renderer/LineInvariants.java`, both `colorFor` overloads — no `@return`.
- `ui/MusicEditOperations.java`: `toggleBeamWithPredecessor` (:171), `toggleTieInRange`
  (:259), `createValidatedTuplet` (:463), `resolveHairpinAction` (:687) — no `@return`;
  the first, third and fourth also lack `@param`.
- `ui/component/LyricTargetResolver.java`, `resolveLyricTarget` — no `@return`.
- `io/LineIO.java:136-142`, private record `SegmentParts(a, b, data)` — `@param` for `data`
  only. It has one caller; drop the tag and keep the summary sentence.
- `binding/Binding.java:38`, `@param <T>`: "nullable when the target's is" stops
  mid-clause. Its siblings say "declared `@Nullable` when absence is a value the graph
  carries".

### Finding 13 — One sentence repeated on three sibling methods

`src/main/java/songscribe/dom/TerminalMaintainer.java:200-263`: each of the three
`checkTerminalPolicyFor…` methods opens with "Returns without checking while the policy is
bypassed". The class comment already states when the policy is off. Drop the three copies.

Confidence: low weight; nothing has drifted yet.

---

## Test Conformance

No test in scope was found that passes while the code is broken, and none that asserts
something no contract promises.

### Finding 14 — A promised repair of `deleteRange` has no test anywhere

**Where.** `src/main/java/songscribe/dom/EditableRun.java`, `deleteRange`; its test is
`src/test/java/songscribe/dom/LineDeletionTest.java`.

**The clause.** One of the method's four stated invariants: "the note in front of the range
keeps no glissando into it."

**What is wrong.** `LineDeletionTest` covers the three lyric invariants and not this one,
and no other test sets up a glissando into a deleted note (the repair method has two
production callers and no test caller). The design does not enforce it — it is an explicit
repair step — and a notator reaches it by drawing a glissando from A to B and deleting B.
If the step were lost, A would keep a glissando that points at nothing, and nothing would
fail.

**What to do.** One case: a note with a glissando into the next note, delete the next note,
assert the glissando is gone. (The rows Finding 1 adds belong to the same test.)

Confidence: high.

---

## Behavior change to confirm

### Finding 15 — A batch barline change no longer removes a tuplet it did not touch

**Where.** `src/main/java/songscribe/ui/selection/SelectionActionApplier.java`; the branch
deleted its `validateSpans` step and `Line.removeOverlappingTuplets`.

**Before.** After any batch replacement over a selection, every tuplet that overlapped the
*selection* was removed. Select a triplet plus the barline after it, apply "double
barline": only the barline is replaced, and the triplet bracket vanished.

**Now.** Each single replacement asks each tuplet whether it is affected. A tuplet goes
only when a replaced element is inside it and its type or dots changed. In the example the
triplet stays.

For every case where a note inside the tuplet changes (five traced), old and new agree.
The new rule is strictly narrower, and it is the one `Tuplet.outcomeFor`'s comment states.
No plan file or test names this change, so it is listed for confirmation rather than as a
defect.

---

## Reported by an agent, checked, and not a finding

- `KeyChangeDialogController.editKeyChange` lost a "beep and log when the double-clicked
  key change is not on the line" guard. The one caller passes a key change it hit-tested
  on the same line in the same event, so the guarded case cannot occur; the guard was dead.
- The first Design report on slice 1 claimed four unused hooks on `Span`. It came from a
  wildcard symbol search that returned nothing in error; three of the four are overridden.
  Finding 6 is what remains.
- The second re-lettering call at the end of `settleWordAcross` looked redundant. It is
  not: it is the call that reaches the syllable after the gap. Finding 1's fix makes the
  first call reach it too; the duplicate work that results is one no-op comparison.
