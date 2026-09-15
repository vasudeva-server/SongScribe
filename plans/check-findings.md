# Check Findings — design pass 6 working tree

Scope: the uncommitted diff against `HEAD` (87 production files, 9 test files) plus
`docs/span-invalidation.md`, `docs/key-changes.md`, `docs/mutations.md`,
`docs/hairpin-editing.md` and the four plan files under `plans/`.

No concrete production bug was found. Every moved body was compared with its old
form: the replay bypass, the absent-span no-op, the merge reach, the terminal
checks, the range invariant at every construction site and the detach guard
removal all behave as before or as the plan intended.

---

## Design findings

### 1. The tuplet's invalidation rule is split between `Tuplet` and `Line`'s primitives

**Where.** `Line.java` — the insert primitive (`addElement(int, …)`), the modify
primitive (`modifyElement`), the single-removal and range-removal primitives;
`Tuplet.java`, which overrides only `isInvalidatedByReplacement`;
`LineSpans.removeOverlappingTuplets`.

**What the code does now.** The span registry's sweep asks every span what should
happen to it when an edit lands, and that is the whole story for ties, beams,
hairpins, endings and trills. For tuplets it is half the story. A tuplet answers
"remove me" only for a replacement. For an insertion inside its bracket, or a
deletion strictly inside it, the tuplet says "keep", so each of `Line`'s
primitives removes the tuplet by hand before running the sweep: the insert
primitive looks up the tuplet at the index and removes it if the index is past
the anchor; the two removal primitives and the modify primitive call
`removeOverlappingTuplets`. Each of those four blocks sits inside its own
`if (!song.isReplaying())`, so the "skip while replaying" decision is written four
times in `Line` and a fifth time in `Span.addTo`.

**What is wrong.** "Any structural change inside my bracket removes me" is a fact
about tuplets, and it lives in the class that holds the element list instead of on
the tuplet. Visible consequences:

- `Line`'s primitives are not the "place the element and repair nothing" primitives
  their Javadoc promises; each carries a tuplet special case.
- The modify primitive keeps a replay guard whose own comment says the replayer
  never calls it ("the gate keeps the suppression contract symmetric"). A guard no
  caller can reach, kept for symmetry.
- `ScoreViewController.tryInsertFragment` (around line 1483) carries a comment
  explaining that the insert primitive's tuplet removal "is now redundant but
  harmless" on the paste path. Code explaining that a built-in policy is dead is
  the code naming its own dead weight; the comment also narrates history ("now").
- `SelectionActionApplier.validateSpans` exists solely to call
  `removeOverlappingTuplets` after a batch replacement, with a comment saying beams
  and ties need nothing because the sweep handles them. A UI class knows that one
  span kind is not covered by the model's own sweep.
- `LineSpans.removeOverlappingTuplets`'s Javadoc names callers that do not exist
  (`SelectionCoordinator`, `PreviewElementManager`). Its real callers are
  `Tuplet.applyDisplacementPolicy`, three `Line` primitives and `validateSpans`.
- The next span kind whose survival depends on its interior rather than its
  endpoints gets another block in every primitive.

**Readers traced.** `removeOverlappingTuplets`: `Tuplet.applyDisplacementPolicy`,
`Line.modifyElement`, `Line.removeElement`, `Line.removeRange`,
`SelectionActionApplier.validateSpans`. `findTupletAt`: `Line.addElement` and
`ModificationSession.removeTupletsInvalidatedFrom`.

**Corrected design.** `Tuplet` overrides the insertion and deletion predicates (or
`outcomeFor` outright) to answer "remove" when the change falls strictly inside its
range, next to the replacement predicate that is already there. The insert and two
removal primitives then contain only: terminal check, sweep, post the mutation.
The replay guard moves into `LineSpans.sweep`, whose only callers are those
primitives, so "the sweep is skipped while replaying" is written once. The modify
primitive is the one edit that is not an `ElementChange` shape; the clean form is a
fourth shape, `Modification`, in the sealed interface so the sweep covers field
changes too and the doc's "one funnel" sentence is fully true. `validateSpans`
then disappears, unless a whole-range batch rule is what the product wants, in
which case its comment says so instead of saying the sweep does not cover tuplets.

**What it touches.** `Tuplet.java` (one or two overrides), `ElementChange.java`
(one new shape if `Modification` is added), `Line.java` (four primitives shrink),
`LineSpans.java` (`sweep` gains the guard; `removeOverlappingTuplets` loses three
callers and its Javadoc is rewritten), `ScoreViewController.java` (one comment
block), `SelectionActionApplier.java` (one method and its comment).
`docs/span-invalidation.md` already describes the target state. No test in scope
exercises the pre-sweep tuplet removal, so nothing in the suite is deleted.

**What it costs to leave.** Every future primitive or edit shape re-implements the
tuplet rule by hand; every reader of `Line` learns that the sweep is complete for
five kinds and not the sixth; the paste path keeps explaining why a model policy
is a no-op there. Recommendation: do it. It finishes the sweep the pass set out to
build. Confidence high.

### 2. `DetachedLyricRun` implements `EditableRun` for no reader, and `Fragment.run()` copies a list to protect against an edit nobody makes

**Where.** `DetachedLyricRun.java` lines 39 and 70–86; `Fragment.java`, `run()`
(around line 518); `EditableRun.java` class Javadoc lines 41–44 against the method
contracts at lines 88–94, 132–133, 183–184.

**What the code does now.** Phase 3 gave the clipboard's detached run three edit
primitives so it could implement the new `EditableRun` interface, and made
`Fragment.run()` hand it a fresh `ArrayList` copy because, its Javadoc says, the
run edits the list it is given.

**What is wrong.** Nothing edits a detached run. `Fragment.run()` returns
`StaffElementRun`, so the edits are unreachable through it; every production
caller of `insertRun`, `deleteRange` and `deleteRanges` holds a `Line`; and the
three primitives on `DetachedLyricRun` have no reference anywhere, production or
test. The three methods, the copy on every paste, and the Javadoc paragraph
explaining the copy all exist to satisfy the interface. The interface's own
contract shows the strain: its class Javadoc says it promises nothing about undo
and that a detached run belongs to no document, while its three edit methods
still say "must be called inside a modification bracket", "records into the open
bracket" and "throws if the song has no bracket or the range covers the song's
terminal". A caller holding a detached run is told about a song it does not have.

**Corrected design.** Two designs leave the code sound, and which one is a product
question: is a clipboard fragment ever meant to be edited in place? If yes,
`Fragment.run()` returns `EditableRun` so the capability is reachable and the copy
is justified. If no, `DetachedLyricRun` goes back to `LyricRun`, its three
primitives and the copy and its paragraph go, and `EditableRun` stays as the single
home of the three edit sequences with `Line` its only implementor (or collapses
into `Line`; with one implementor the interface is a Javadoc distinction, not a
variation axis). Either way the three method contracts lose the bracket, recording
and terminal clauses, which belong on `Line`'s primitives (finding 8).

**What it touches.** `DetachedLyricRun.java`, `Fragment.java`, `EditableRun.java`,
and three `{@link DetachedLyricRun}` sentences in `LyricRun`, `StaffElementRun`,
`EditableRun`. No test changes.

**What it costs to leave.** Every reader of `Fragment.run()` is told the run edits
the list, keeps the copy, and looks for the edit that never comes. Recommendation:
remove the implementor unless a paste-time edit is planned. Confidence high on the
no-reader fact, medium on collapsing the interface.

### 3. `Span.addTo(LineSpans)` makes every caller unwrap a line so the span can wrap it back

**Where.** `LineSpans.line()` (lines 82–85); `Span.addTo` and `removeFrom`
(`var line = spans.line(); if (!line.getSong().isReplaying()) …`); `Tuplet` and
`Trill` displacement policies (`rangeIn(spans.line())`).

**What the code does now.** Roughly forty call sites write
`span.addTo(line.spans())`. The span then asks the registry for its line back, and
the line for its song, to decide whether to skip the displacement policy.
`LineSpans.line()` has exactly those four readers, all inside span kinds.

**What is wrong.** A caller holding a `Line` has to reach into it for the registry
so the span can reach back out for the line. The accessor exists only for that
round trip. The registry's own write primitives are already package-private, so
nothing outside `dom` needs to hold a `LineSpans` to add a span.

**Corrected design.** `Span.addTo(Line)` and `removeFrom(Line)`; inside,
`line.spans().add(this, record)`. `LineSpans.line()` and the four reach-backs go;
`rangeIn` takes the line it already needs. `line.spans()` stays for the lookup
queries, which genuinely are the registry's.

**What it touches.** `Span.java`, six kind classes, `LineSpans.java`,
`MutationReplayer.java` (twenty-four arms drop `.spans()`), and the call sites a
reference lookup on `Span/addTo` and `Span/removeFrom` lists, about twenty-five
files; `SongFactory`, `EndingBracketGeometryTest`, `VoltaAlignmentFixtureTest` one
line each.

**What it costs to leave.** Every new caller learns the unwrap-and-rewrap shape,
and the accessor stays as a reach-through. Recommendation: do it. Confidence
medium; it is a judgement about which object the verb belongs on, and the
accessor's reader list is what decides it.

### 4. "Where does this span sit in this line" is answered three different ways

**Where.** `LineSpans.mergeOverlapping` (lines 430–469) and its comment saying
`indexOr`'s fallback "is unreachable here"; `Span.rangeIn`;
`RangeQueries.java:512`; `HairpinEndpoints.java:108–109`;
`LineInvariants.java:149–150`; `ElementColumn.java:742, 748`.

**What the code does now.** The registry answers `anchorIndexOf(span)` and
`endIndexOf(span)` separately, and every caller that wants a span's extent asks
both on the same line. Inside `mergeOverlapping`, a predicate builds the range of
each candidate to filter, then throws it away, so the caller re-resolves each
survivor and has to write a fallback it documents as unreachable. `Span.rangeIn`
is a third route via `line.getElementIndex` directly, which bypasses the
span-bound semantics and silently yields the range `(-1, -1)` for a span whose
endpoints are not in the line, because the record's invariant only checks
`begin <= end`.

**What is wrong.** One query, three implementations, and the worst form of guard
from the design guide: an unreachable branch that returns a plausible default.

**Corrected design.** One method on `LineSpans`, `rangeOf(span)`, returning the
span's `ElementRange` when both bounds resolve and a clear absent answer
otherwise. `mergeOverlapping` filters over span-and-range pairs and never
re-resolves; `rangeIn` goes; the four external pairs become one call each.

**What it touches.** `LineSpans.java`, `Span.java`, `Tuplet.java`, `Trill.java`
and the four callers above. The return shape for "absent" is the one open
question. Confidence high on the duplication.

### 5. `TupletValidator.Result` holds three fields for a two-state idea, and the tuplet ratio work is not in the plan

**Where.** `TupletValidator.java:137–149`. The tree also carries
`TupletRatio.java`, `NotatableValue.java` and matching changes in `Tuplet`,
`TupletLoadPass`, `MeasureMapper`, `NoteBuilder`, `LineTrackBuilder`, none of
which any phase of `class-design.md` names.

**What the code does now.** The record is `(boolean valid, @Nullable Reason,
@Nullable TupletRatio)` with a compact constructor that throws when `valid`
disagrees with which nullable is present. Readers: `valid()` in
`ModificationSession`, `PasteSpanReconciliation`, `RangeQueries`; `ratio()` and
`reason()` in `TupletLoadPass`.

**What is wrong.** The runtime guard is the type admitting it cannot carry its own
invariant. Every new reader must remember to check `valid` before `ratio`.

**Corrected design.** `sealed interface Result permits Valid(TupletRatio ratio),
Invalid(Reason reason)`. The boolean, both nullables and the guard disappear; each
reader becomes a pattern match.

**What it touches.** `TupletValidator` (record, `invalid` helper, two construction
sites), `TupletLoadPass`, `ModificationSession`, `PasteSpanReconciliation`,
`RangeQueries`, one site each; tests naming `Result` re-pointed. Recommendation:
do it. Confidence high on the shape. The separate question is whether the tuplet
ratio work belongs in this tree at all; that is asked below.

### 6. `ElementChange.Deletion` is a class for a reason that is not true

**Where.** `ElementChange.java:186–190`.

**What the code does now.** The Javadoc says "Not a record: `deletedElements` is
derived from the line rather than stored, and a record would have to carry it as a
component." A record may declare any method; only its state must be components.

**What to do.** `record Deletion(Line line, ElementRange range)` with
`deletedElements()` as a method, the same shape as `Insertion` and `Replacement`.
The hand-written field, constructor and accessor go. Confidence high.

### 7. `Line` fetches a concrete `Song`, and the test fixture grows a stub per forwarding method (for the record)

**Where.** `UnitTest.java`, `minimalSongMock()`; `plans/design-pass-6/findings-1-2.md`
line 235, which records that the dependence stays concrete.

**What the code does now.** The shared mock now stubs a fourth `Song` method
(`applyChange`) before a `Line` can be built, because `Line.applyChange` forwards
there unconditionally.

**What is wrong.** Every forwarding method added to `Line` adds a stub to the
fixture; that is the hidden-dependency tell. What `Line` needs from its song is a
narrow set: post a mutation, ask replay and suspension state, ask key inheritance,
ask terminal policy. Not introduced by this pass, but demonstrated by it.
Recorded for a later pass rather than proposed here. Confidence medium.

---

## Contract findings

### Proposed changes to existing contracts

### 8. `EditableRun`'s three edit methods promise a bracket and a song the interface says it does not have

Symptom of finding 2, listed here because it is a change to an existing contract.
`EditableRun.insertRun`, `deleteRange`, `deleteRanges` keep the Javadoc they had on
`Line`: "must be called inside a modification bracket", "records into the open
bracket", "throws if the song has neither a bracket nor suspended tracking, or if
the range covers the song-owned terminal". The class Javadoc says the interface
promises nothing about undo. Change: strip those clauses from the three methods;
state once on the interface that when the run is a `Line`, every primitive posts
through the song's bracket and `Line`'s primitive contracts say what they throw.
Also `StaffElementRun.redundantKeyChangeRanges` still says
"`{@link Line#deleteRanges}` discharges that obligation" for a member now on
`EditableRun`. Confidence high.

### 9. `Song.applyChange` states its throw condition less precisely than the two forwarders that cite it

`Song.applyChange`'s `@throws` says "if called outside a modification bracket",
while its prose says a suspended mutator runs without a bracket. `Line.applyChange`
(lines 176–186) delegates to it, says the promises are the song's, and then
restates the precise two-condition clause ("neither an open bracket nor suspended
tracking"); `Line.setKey` (lines 214–215) restates it a third time. A file reader,
which runs under suspension with no bracket, cannot tell from the owner's tag that
it will not be thrown at. Change: put the two-condition clause on
`Song.applyChange`; `Line.applyChange` and `Line.setKey` link instead of
restating. Confidence high.

### Missing or wrong contracts

### 10. `Line.getElements(ElementRange)` has a line comment where six caller files need a contract

`Line.java:532–538`. No `@return`, no `@throws`, and nothing saying the result is a
live view of the line's list. `ElementChange.Deletion.deletedElements` relies on
that liveness. A caller that keeps the list across an edit gets a
`ConcurrentModificationException` with no warning. Write: returns an unmodifiable
live view valid until the elements next change; throws `IndexOutOfBoundsException`
when the range reaches past the element count. Confidence high.

### 11. `Line.removeElement` and `removeRange` say they exist for replay, and three edit paths call them

`Line.java:540–545, 578–584`: "An edit deletes through `deleteRange` instead; this
is for replaying a recorded mutation." `EditableRun.deleteRange` calls
`removeRange`, `deleteElement` calls `removeElement` twice, and
`TerminalMaintainer.maintainOnLastLineChange` calls `removeElement`. A reader
adding a caller would conclude the sweep matters only for replay. Reword: the
primitive beneath `deleteRange`, also what replay calls. Confidence high.

### 12. `Span.addTo` and `removeFrom` say the record goes "into the open bracket"

`Span.java:186–187, 212–213`. Under suspended tracking, which is how the loaders
call `addTo`, nothing is recorded; with tracking on and no bracket, the call
throws from `Song.applyChange`. Neither is stated, so a loader cannot tell it needs
no bracket and an editor cannot tell it needs one. Fix by linking: posts through
the line's song, see `Song#applyChange` for recording, suspension and what is
thrown. Confidence high.

### 13. `LineSpans.removeOverlappingTuplets` and `removeTrillsOverlapping` have stale or missing contracts

`LineSpans.java:493–511`. The first names callers that do not exist and claims to
emit a `TupletRemoval` itself, which is now the tuplet's own record. The second is
a one-line summary with no bracket precondition. Write one shape for both: what is
removed, that each removal is that span's own recorded mutation via
`Span#removeFrom`, and the bracket rule by link. Resolves with finding 1 for the
first; the second needs writing regardless. Confidence high.

### 14. The `ElementChange` factories opened to the UI have no `@param`, `@return` or `@throws`

`ElementChange.java:162–175, 241–273`. `forReplacement` calls `line.getElement(index)`
at build time, so a UI caller handing it the element count gets
`IndexOutOfBoundsException` from the factory, unstated. `Replacement.endingEffect`
returns a value with no `@return`. Add index ranges, `@return`, and the throw on
`forReplacement`. Confidence high.

### 15. The terminal policy is stated four times

`TerminalMaintainer.java:47–55, 197–262` and `Song`'s three delegations. "A final
double barline may only be appended at the end of the last line" appears in the
class Javadoc, the check's summary, its `@throws`, and again in `Song`'s
`@throws`; the other two checks repeat the pattern. The class doc holds the
policy; each check's `@throws` names its condition once; `Song`'s one-line
delegations keep a summary and `@see` and drop the restated `@throws`. Confidence
high.

### 16. `Line.keyAtEndOfLineAfterRemoving` duplicates a passage of `docs/key-changes.md`

`Line.java:314–317` and `docs/key-changes.md:248–251` both explain that a deletion
computes its reach unconditionally because removing no key change leaves the end
key where it was. The Javadoc cites the doc with `{@code}` rather than a link. Cut
the Javadoc to the promise and link the doc for the why. Confidence medium.

### 17. `LineSpans`' class Javadoc duplicates the new section of `docs/span-invalidation.md`

`LineSpans.java:34–53` and the doc's "What stays with the registry" both state in
full that a span enters or leaves only through itself, each kind carrying its own
policy and record, and that the registry keeps the hairpin merge. Trim the class
doc to what no doc holds (the one-directional membership invariant and cross-line
double-listing) and link the doc. Confidence medium.

### 18. The register's deferred item on `StaffElementRun.getElement` was deleted without being done

`plans/design-pass-register.md` dropped the "→ Pass 6" item saying `getElement`
states no range and no `@throws`. `getElement` is unchanged
(`/** The element at {@code index}. */`), and the new `EditableRun.addElement(int,
StaffElement)` likewise states no valid range for `index` (it is called up to
`elementCount()` by `insertRun`). Step 5 of the pass owns contracts, so this is a
note that the claim is still open, and the register line should come back until it
is closed. Confidence high.

### 19. Stale prose left by the pass

- `ScoreViewController.java:1472, 1477`: the paste comment names `addPastedSpan`
  twice; that method no longer exists, the merge is `Span.addTo`'s policy. Line
  1483 narrates history ("now redundant"). Part of finding 1.
- `Span.java:266`: "the only entry point `Line` uses"; the sweep is now the
  registry's.
- `UnitTest.java`, `minimalSongMock()`: "now that `Line.applyChange` forwards to
  it unconditionally" narrates a change. Drop "now that" for "since".
- `plans/design-pass-6/class-design.md:6` says "Status: In Progress" while every
  phase is complete.
- Phase 4 and Phase 7 file lists name `docs/hairpin-editing.md` and
  `docs/mutations.md`; neither was changed. Both were read against the new shape
  and neither says the line guards a bracket or hosts the merge rules, so no
  rewrite was needed; the record should say the reread found nothing to change.

### 20. Carried signature finding: `nearestNonGraceIndex(int fromIndex, int step)`

`findings-1-2.md` I4 already records that `step` is a direction selected by a bare
`-1`/`1`. The move onto `StaffElementRun` preserved it. Belongs to step 3 of the
pass; noted so it is not lost.

### 21. Questionable: rename `Line.spans()`

Every call site reads `line.spans().getSpans()` or `line.spans().findTieAt(…)`; a
caller reading `spans()` expects the spans and gets the registry. `spanRegistry()`
would say what comes back. Low confidence; readability only, nothing behaves
wrongly. Asked below.

---

## Correctness & Efficiency

### 22. `removesConfirmableSpan` walks the span list twice when once decides it

`ElementChange.java:75–86`. It first checks that no span asks for confirmation and
returns false, then filters by the same predicate and asks each survivor. When none
match, the second expression is already false. Delete the pre-check; the contract
sentence "false when the line carries no such span" stays true. Confidence high.

---

## Test Conformance

### 23. No test in scope asserts any contract this pass wrote

The test diff is mechanical re-pointing. Promises with no test: `ElementRange`'s
`begin <= end` constructor throw (only a gap if a caller can hand it a reversed
pair; every construction site checked derives the pair in order, so today the
guard is unreachable and needs no test); `Span.addTo`'s displacement policy and
replay skip; `Span.removeFrom`'s no-op on an absent span; `removesConfirmableSpan`;
the three `TerminalMaintainer` checks. `record.md` shows step 6 (test triage) has
not run, so this is the state of a pass mid-way, not a finished increment skipping
tests. Recorded so step 6 has the list; nothing to fix in this review.

The re-pointed tests are correct: `deleteRange` is always handed the widened
range, and `LineTest` resolves unchanged against the moved members.
