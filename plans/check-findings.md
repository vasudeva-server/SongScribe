# Check Findings — redundant mid-line key changes

Reviewed: the uncommitted working tree on `797-redundant-key-changes` — eleven
production files, ten test files, two design notes and the plan.

No confirmed production bug. The implementation matches its contracts at every
index boundary I traced by hand, including the three most likely to hide an
off-by-one: the order the sweeps run in relative to each edit's own mutation, the
shift the paste applies to its own line's ranges, and the forward scan that finds
several restating signatures in a row.

What follows is one structural finding that accounts for most of the awkwardness,
a handful of smaller structural ones, then contract and test findings.

---

## 1. Withdrawn: the glissando strip is correct

This was reported as a possible silent loss on file open, and it is not one. A
glissando lives on its source element and connects forward to the element
immediately following it. That following element is precisely the first element
of the range being deleted, so deleting the range deletes the glissando's target,
and the glissando goes with it. It does not re-target whatever ends up adjacent
afterwards.

That holds for the load-time sweep too: when the deleted pair is a key signature
and the barline in front of it, a glissando on the note before that barline had
the barline as its target, and the target is going.

No change. The finding is withdrawn and the test proposed for it is dropped.

---

## 2. Design: the first line of a key move is modelled as a different kind of thing from the others

Both design-level agents arrived at this one independently, from different
directions. I checked it against the code and agree.

**Where:** `KeyChangeReconciliation.KeyMove` and `KeyChangeReconciliation.Confirmed`
(`src/main/java/songscribe/ui/edit/KeyChangeReconciliation.java`),
`AccidentalReconciliation.InsertionRegion` and `AccidentalReconciliation.ModifiedLine`
(`src/main/java/songscribe/layout/AccidentalReconciliation.java`), and the six
places that use them: `ScoreViewController.java:1025`, `:1341`, `:1578`, `:1679`,
`KeyChangeDialogController.java:290`, `:380`, `:488`.

**What the code does now.** Six kinds of edit can move the key a line is in:
setting a line's own key, writing a new mid-line key signature, changing one
already there, deleting a selection of elements, deleting a whole line, and
pasting. Each affects a run of lines — the one the user acted on, plus every line
after it that inherits its key — and each now owes three things across that run:
work out which notes need an accidental written in or taken out, ask the user once
about it, and delete the key signatures the move leaves saying nothing.

That run of lines is described by two different record types. The first line — the
one the user actually acted on — is described by `InsertionRegion` when the edit
adds or removes elements there, and by `ModifiedLine` when it does not. Every
other line is always a `ModifiedLine`.

**What's wrong with it.** Almost every rough edge in this change traces back to
that split:

- **The same new field had to be added to both types, and the code says so.**
  `InsertionRegion`'s new `strandedRanges` field carries a Javadoc line reading
  "This is `ModifiedLine#deletedRanges()` for a line described as an insertion
  instead of as an in-place modification." One fact, two homes, documented rather
  than removed.
- **The removal step became two methods that six callers must sequence by hand.**
  `sweepReach()` covers the inheriting lines and `sweepHead()` covers the first
  one. Across the six call sites they appear in three different orders, and the
  paste calls one of them a hundred lines after the other, in a shifted variant.
  Nothing in the types says which are needed or in what order. A seventh
  key-moving edit added later that forgets `sweepReach()` will compile, pass, and
  leave behind exactly the invisible key signatures this whole change exists to
  remove — and because such a signature draws nothing, there is nothing on screen
  to notice.
- **"Nothing here" and "look somewhere else" share one value.** `headStranded()`
  returns an empty list both when the first line strands nothing and when there is
  no projected first line at all — in which case its ranges are in list entry 0
  instead. The Javadoc has to explain this; the types cannot.
- **Position 0 of a list is being used as a type tag.** `headChanges()` returns
  entry 0 and carries an `@invariant` saying the answer is "meaningful only for a
  move with a projected head" — a warning that the answer is wrong in some cases,
  with no way for a caller to detect those cases. `linesToRecord()` drops entry 0
  by index, and the `HeadOwner` enum is a third mechanism encoding the same one
  fact. Both current callers do have a projected first line, so nothing is broken
  today.
- **Two nearly parallel walks.** `AccidentalReconciliation.reconcile` and
  `AccidentalReconciliation.reconcileLine` both build a projected element list, and
  both had to gain the identical "skip these indices" treatment in this change. The
  next per-line fact will have to be added to both again.

**The corrected design.** One record describing one line and everything an edit
does to it: the line, the key it will run in afterwards, the notes changed on it,
the elements inserted and the range replaced (both possibly absent), and the ranges
removed. A key move is then a plain list of those in song order. The nullable first
entry, `headStranded()`, `HEAD_LINE_INDEX`, `HeadOwner` and the `@invariant` on
`headChanges()` all stop existing; `reconcile` becomes one walk instead of two; and
`commit` and the sweep become one method each over the list. "Is this the first
line" is answered by whether an entry carries an insertion — a fact about the
entry rather than about its position.

**What genuinely does not collapse.** The paste has to remove its own line's
stranded signatures *after* it inserts, because the element it measures its
trailing spacing against can itself be inside one of those ranges. Under the
unified design that becomes an index shift on one entry rather than a second
public sweep method every caller has to reason about — but I have not worked out
the exact shape, and I am not going to propose a flag to paper over it. That part
needs a design pass I have not done.

**What the change touches.** Four production files (`AccidentalReconciliation.java`,
`KeyChangeReconciliation.java`, `ScoreViewController.java`,
`KeyChangeDialogController.java`), the six call sites above, and two test classes
(`AccidentalReconciliationTest`, `KeyChangeReconciliationTest`) that build these
values directly. Roughly half of `KeyChangeReconciliationTest` stops being needed,
because the distinction it exists to test — which sweep covers which line —
disappears.

**Recommendation: do it.** The invariant this whole branch establishes is currently
enforced by six callers each remembering to make two calls in an order nothing
checks, with no visible symptom when one is missed.

**If it is left alone:** each key-moving edit added later copies one of the six
existing orderings and hopes it picked the matching one; the ordering rule stays
spread across four method comments that have to be kept in agreement by hand; and
`headChanges()` stays a method that answers confidently and wrongly for half its
possible inputs, guarded by a comment. Correcting it removes six members and
replaces a prose protocol with a single call.

**Worth saying:** this change already removed one instance of exactly this problem
— the `ReachReconciler` callback and its two near-identical private implementations
in two different controllers are gone, folded into one method. That was the right
move. The finding is that it stopped one step short.

---

## 3. Design: the deletion routine re-derives a range its caller already computed, and promises something it does not deliver

**Where:** `Line.deleteRange` (`src/main/java/songscribe/dom/Line.java:1020`),
`Line.deleteRanges` (`:1086`), `ScoreViewController.deleteElementRange` (`:1322`).

**What the code does now.** A deletion is never quite the range the user selected:
a key signature drags the barline in front of it, a note drags a breath mark after
it, and a small grace note cannot outlive the note it decorates. `Line.effectiveRange`
computes the real range and returns it as an `EffectiveRange` record. But
`deleteRange` takes two loose `int`s and computes the widening again itself. On a
plain delete the widening is therefore computed three times — once to ask the user
about it, once in the controller, once inside `deleteRange`. And `deleteRanges`,
the batch form, takes a list of `EffectiveRange`, unpacks each back into two ints,
and hands them over to be widened again.

**What's wrong with it.**

- Two adjacent same-typed parameters that a call site can transpose without the
  compiler noticing is exactly what the project's Java rules say must be a record
   — and the record already exists and is already what every producer returns.
- To make the round trip safe, `deleteRange`'s contract claims (`Line.java:1013`)
  that the widening is a fixed point, so handing back an already-widened range
  deletes exactly that range. **That is false for the grace-note case.** Widening
  reaches back over a grace note by returning `begin - 1`; feeding `begin - 1` back
  makes the paired-grace-note test answer false, so the deletion takes the
  contiguous path instead of the element-by-element path — and it is the
  element-by-element path that hands a grace note's lyric syllable back to the note
  it decorated. Same elements removed, different lyrics left behind.
- No production caller can reach that today: every range fed back through
  `deleteRanges` is a barline-and-signature pair, and a key signature is never a
  grace note's host. So this is a promise the code does not keep rather than a bug.
  The new test case named "an already-widened range is a fixed point"
  (`LineDeletionTest.java:134`) checks only the barline half, which makes the
  promise look covered.

**What to do instead.** `public void deleteRange(EffectiveRange range)`, with
`Line.effectiveRange` as the only thing that produces one.
`reconcileAndConfirmDeletion` already has the `EffectiveRange` and currently throws
it away — pass it down. Inside, decide the grace-note case on the already-widened
begin rather than on a raw index. The fixed-point clause then has nothing left to
promise and is deleted, and `deleteRanges` stops unpacking and repacking. Three
call sites, one signature, one test case rewritten.

---

## 4. Design: the barline-pairing rule now has two definitions, and both docs claim it has one

**Where:** `Fragment.withoutRedundantKeyChanges`
(`src/main/java/songscribe/ui/clipboard/Fragment.java:320`), against
`Line.effectiveBegin` / `Line.effectiveEnd`.

**What the code does now.** When a clipboard fragment is pasted somewhere its key
signature would say nothing, that signature is dropped from the fragment before
anything lands. The code drops index `i` and index `i - 1`, assuming `i - 1` is the
barline.

**What's wrong with it.** `Line` is the one place that says what an element is
paired with, and it says two things this code does not: that the element before a
key signature is taken only *if it actually is* a barline or repeat, and that a
breath mark after an element goes with it. `Fragment` hardcodes `i - 1` with no
test and takes nothing after. Meanwhile the new section of `docs/key-signatures.md`
says "The pair goes by the same pairing a manual deletion uses… there is one rule,
not one for the user and another for the sweep", and the method's own Javadoc
repeats it. Neither is true.

Nothing breaks today, because `Fragment.capture` guarantees the barline is there.
What breaks is the next change: add an element kind that pairs with a key
signature, update `Line`, and the paste path silently goes on doing the old thing.
The new `FragmentTest` builds fragments by hand rather than through `capture`, so
the suite would not catch it either.

**What to do instead.** The concept both classes need is "a run of staff elements
and the pairs inside it". The codebase already has this shape for lyrics: `Line`
implements `LyricRun`, and `DetachedLyricRun` is the same interface over a plain
list for clipboard content. Do the same here — one type owning `effectiveBegin`,
`effectiveEnd` and the two scans currently parked as statics on `KeyChangeElement`
(`strandedIndices`, `lastKeyFrom`), with `Line` and `Fragment` both delegating to
it. Those two statics were the author already noticing the missing concept: their
Javadoc says "Asked of a plain element list rather than of a line, because the two
callers hold different runs." Naming the run finishes that thought.

---

## 5. Design: the two file readers each spell out the same two-step repair, in the right order, by hand

**Where:** `Song.loadFrom` (`src/main/java/songscribe/dom/Song.java:414`) and
`SongMapper.map` (`src/main/java/songscribe/io/musicxml/SongMapper.java:95`).

**What the code does now.** Both readers call `rebuildInheritedKeysAfterParsing()`
and then `removeStrandedKeyChangesAfterParsing()`, each with its own two-sentence
comment explaining why that order. The order is a genuine precondition — the
stranding sweep reads each line's running key, which only the first call settles —
but nothing in the types enforces it.

**What's wrong with it.** Two readers, two hand-written orderings, two comments to
keep in agreement, and — see finding 11 — the behaviour has to be proven twice,
where only one of the two proofs exists. A third reader, or a rewrite of either,
gets no compiler or test signal if it puts the calls the wrong way round or drops
the second one. The failure mode is precisely the invisible-signature state this
feature exists to prevent.

**What to do instead.** One method on `Song` that performs both in the fixed order,
with the ordering rule stated once in its Javadoc, and both readers calling that.
Two production files, plus the sweep's own contract.

---

## 6. Design: two different types in this subsystem are both named `KeyMove`

`Song.KeyMove` (private, `Song.java:1465`) means "the line indices a mutation's key
move is felt from". `KeyChangeReconciliation.KeyMove` (public, `:92`) means "the
lines an edit re-keys and what it deletes on each". They live in the same subsystem
and are discussed in the same design note. Rename the new public one — `Reach` is
the word the surrounding prose already uses throughout. It disappears entirely if
finding 2 is taken.

---

## 7. Design: seven positional components, three of them same-typed lists passed empty

**Where:** `AccidentalReconciliation.InsertionRegion`
(`src/main/java/songscribe/layout/AccidentalReconciliation.java:226`), which this
change grew from six components to seven.

At `KeyChangeDialogController.java:340` the call reads:

```java
region = new AccidentalReconciliation.InsertionRegion(
    line, ownRange.begin(), new InsertionSpacingCalculator.DeletedRange(...),
    List.of(), List.of(), List.of(), strandedAfter);
```

Three consecutive `List.of()` arguments meaning three different things — the
elements arriving, the accidentals they carried in their source, and the spans
coming with them. `List.of()` infers its type from the target, so the compiler
cannot catch a transposition of any two, and a reader cannot tell which is which
without opening the declaration. The project's Java rules require a parameter
object past four parameters, and separately for two or more transposable
same-typed parameters; both apply.

**What to do.** Group the three "what is arriving" lists into one record. They are
already the three fields of a clipboard `Fragment`, which is where two of the five
call sites get them from, so the concept exists. That takes the constructor to four
components and makes every argument's role visible at the call site.

---

## 8. Design: a guard whose rejected value no caller can produce, contradicting the contract of the query that feeds it

**Where:** `AccidentalReconciliation.java:265` — the `InsertionRegion` constructor.

It throws `IllegalArgumentException` if any range it is handed begins before the
first index the projection re-reads. The ranges come from
`Line.redundantKeyChangeRanges(fromIndex, key)`, whose contract explicitly promises
(`Line.java:495`) that "The first may begin one index below `fromIndex`, when a key
signature stands at `fromIndex` itself and the barline it sits behind is taken with
it." So one contract advertises a value the other rejects.

I traced all four construction sites and none can produce it: `Line.effectiveEnd`
already pulls a following key signature *into* a deletion's range, and
`Line.canInsertElementAt` refuses the slot between a barline and its key signature,
which is what the paste insertion point is checked against on every mouse move. So
the guard is dead, and it is a throwing dead guard with no `@throws` documenting it
— the next legitimate caller of the bounded query discovers the restriction by
crashing.

**What to do.** Decide which contract is right and delete the other side. Since the
query's clause accurately describes what it returns, I recommend deleting the
guard. If it is kept as a tripwire, it needs a `@throws` naming the condition, and
`redundantKeyChangeRanges` needs a clause saying callers must not ask at an index a
key signature stands at.

---

## 9. Design: a redundant walk-start lowering, and a contract that credits it with work it does not do

**Where:** `AccidentalReconciliation.reconcileLine` (`:865` and `:884`).

**What the code does now.** When a line loses a key signature, the notes after it
must be re-checked, because a key signature and a barline both reset accidentals.
`reconcileLine` handles this three ways: it leaves the removed elements out of the
projection (this is the part that matters), it adds "no ranges removed" to an
early-return short-circuit, and it lowers the point where the re-check starts to
the first removed index.

**What's wrong with it.** The second and third cannot fire. A line only ever
carries removed ranges via `ModifiedLine.reKeyed`, and `linesInheriting` — the only
production producer — returns early rather than adding a line whose running key
does not move. And when the key does move, the line above already sets the walk
start to 0 and already disqualifies the short-circuit. Meanwhile the method's
Javadoc asserts "The lowered walk start is what protects them, not bookkeeping the
walk could skip." That sentence credits the lowering with protecting notes that are
in fact protected by the moved key.

**What to do.** The Javadoc is wrong and must be corrected either way — it tells
the next reader that a line of code is load-bearing when it is not. Whether to also
delete the lowering and the short-circuit term is a judgement call I would rather
put to you than make: removing them is honest about what runs, but it makes
correctness depend on a non-local invariant ("a re-keyed line's key always actually
moved") that today is enforced only by one early return in `linesInheriting`. My
own preference is to correct the Javadoc, keep the lowering, and fix the two tests
that currently reach it — see finding 12.

---

## Contract findings

### 10. `Line.deleteRange` tells the file loader it is doing something illegal

`Line.java:1017` says the method throws if no modification bracket is open. The
underlying `Line.applyChange` actually throws only if the song has "neither an open
modification bracket **nor suspended tracking**" — and the sibling `deleteRanges`,
twelve lines further down, states it correctly. The new load-time repair,
`Song.removeStrandedKeyChangesAfterParsing`, runs with tracking suspended and no
bracket, and reaches `deleteRange` through `deleteRanges`. A caller who reads the
contract concludes the load path is forbidden and opens a bracket to comply — which
is exactly what a load must not do, since it would create an undo entry and mark a
freshly opened document as modified. One method's Javadoc.

### 11. Smaller contract gaps

- **`ScoreViewController.tryInsertFragment`** — the `@return` lists `INSERTED`,
  `LINE_FULL` and `EMPTY`. It can also return `CANCELLED`, and this change added a
  new way to reach it. Worse, this change made `INSERTED` ambiguous: when the whole
  fragment turns out to be a key signature restating the key already running, the
  fragment reduces to nothing and the method returns `INSERTED` having placed no
  element at all. Both callers branch on `INSERTED` to do cleanup, and that happens
  to be right — but a caller cannot learn it from the contract.
- **`AccidentalRestatements.accidentalsClearedBy`** — now silently drops notes that
  are not yet on their destination line (a pasted note). The reasoning is sound and
  is written as a comment inside the loop; the `@return` still promises the notes
  the line loses an accidental from, with no mention that some are omitted.
- **`Fragment.withoutRedundantKeyChanges`** — `@return` says "this fragment when it
  strands nothing, and a reduced one otherwise". It never says the reduction can
  empty the fragment entirely, which is a case its only caller explicitly branches
  on.
- **`Line.removeElement`** — got a doc comment for the first time in this change,
  with no `@param index` and no `@throws` for the `IllegalStateException` it raises
  two lines in. `Line.removeRange` next door is missing the same `@throws`.
- **`Line.redundantKeyChangeRanges(int, Key)`** — `@param fromIndex` says nothing
  about what happens below `FIRST_LEGAL_KEY_CHANGE_INDEX`. The sibling
  `lastKeyChangeKeyFrom` twenty lines below documents exactly that question.
- **`AccidentalReconciliation.InsertionRegion`** — documents six of its seven
  components and has never had an `@param line`. Pre-existing; mechanical to fix
  while the record is being edited anyway.
- **`KeyChangeReconciliation.KeyMove.headStranded()`** — public, with exactly one
  reader thirty lines below it in the same file. Make it private, or inline it.

### 12. The design notes and the Javadoc now hold the same promises twice

Six passages added to `docs/key-signatures.md` and `docs/clipboard.md` restate a
specific member's promise rather than the shape a reader cannot recover from any
one class. The rule is that a doc keeps what no contract can hold — which piece
owns what, and why the set is complete — and the Javadoc links to it rather than
paraphrasing. The pairs, matched on the claim rather than the wording:

| Doc passage | Also written in full at |
| --- | --- |
| Removing a restating change cannot move the key at any position | `Line.redundantKeyChangeRanges` **and** `KeyChangeElement.strandedIndices` — three copies |
| A key change and a barline both cancel accidentals, so removing the pair moves a barrier | `AccidentalReconciliation.reconcileLine` |
| A fragment carries the key it was copied under; where stranded it is never placed | `Fragment.withoutRedundantKeyChanges` — three copies, counting `clipboard.md` |
| Paste is not gated on the lines it re-keys | `ScoreViewController.tryInsertFragment` |
| The enumeration "Six edits move a key…" and the five-step sequence | `KeyChangeReconciliation`'s class Javadoc, same six items in the same order |
| A keyed line stops the walk; inserting a line breaks that | `Song.KeyMove`'s `@param firstStoppableIndex` |

The last one is doubly misplaced: `Song.KeyMove` is a **private** record with one
reader, so a full contract clause on it is depth spent where nothing relies on it,
and its second copy sits where nothing can keep it honest.

---

## Test conformance

### 13. The legacy `.mssw` read path gained the repair and has no test

`Song.loadFrom` now calls `removeStrandedKeyChangesAfterParsing()`. Legacy
SongWriter files are the files most likely to carry a stranded signature, since
they predate the rule entirely. The identical call in the MusicXML reader *is*
tested, by two round-trip cases in `MusicXmlStrandedKeyChangeTest`. Nothing plays
that role for `loadFrom`.

What slips through: if the two calls are ever put the wrong way round in
`loadFrom`, or the second is dropped, a legacy file loads carrying an invisible key
signature that draws nothing, refuses the two insertion slots beside it, and is
written back out on every save — with nothing on screen to say so, and no test to
catch the regression. The testing guides permit a new `.mssw` fixture where the
subject is the legacy reader itself, which this is.

Note that finding 5 changes the shape of this test: with one consolidated method,
each reader needs a test proving it calls that method, rather than each reader
having to re-prove the whole behaviour.

### 14. Two tests construct a document state the invariant forbids, and by passing they hold finding 9's dead code in place

`AccidentalReconciliationTest.testAReKeyedLineCarriesTheRangesItsNewKeyStrandsAndAnUntouchedOneCarriesNone`
and `testADeletedRangeIsLeftOutOfTheProjectionSoTheNotesAfterItKeepTheirPitch`
(`:132`, `:145`) both use the `barrierLine` fixture, which builds a line already
running in the key its own mid-line signature restates — the exact state this whole
change makes impossible — and then re-key it to the key it is already in. No edit
and no file load can produce that. In production every re-keyed line has a key that
actually moved, which is why finding 9's lowering never runs.

Rewriting them so the key genuinely moves needs care rather than a mechanical
edit: the fixture deliberately uses a key that alters no pitch, so that every
assertion turns on the barrier rather than on the key. A moved key has to be chosen
that still alters nothing at the staff position the fixture uses.

### 15. `KeyChangeReconciliationTest.testSweepingTheHeadOfAMoveWithNoProjectedHeadChangesNothing`

The three routes with no projected first line — a line-key change, a line deletion,
and a paste-replace's inner deletion — all call `sweepReach()` only; none calls
`sweepHead()`. The test asserts that calling it anyway does nothing, which pins a
defensive early return no caller reaches. Nothing slips through if it goes. It
disappears along with the method if finding 2 is taken.

### 16. A test rebuilds an algorithm a helper added in this same change already provides

`AccidentalReconciliationTest.threeLineSong` (`:103`) is line-for-line the same
construction as `SongFactory.buildSong` — a new shared helper introduced in this
very change, moved out of the MusicXML round-trip support specifically so tests in
different packages could share it, and already used correctly by
`InheritedKeyPropagationTest`. `SongFactory` is public and imports cleanly from the
layout package. Two copies of "build a song from stubs, replace line 0, settle the
key invariant" now exist in one change; one will drift.

---

## Findings I do not think are worth acting on

**The load sweep re-derives the inherited-key chain once per removed range.**
`Line.applyChange`'s suspended-tracking branch unconditionally re-runs the key
invariant, so opening a legacy file with several stranded signatures walks the
inheritance chain once per removal, recomputing values that are already correct —
the whole premise of this feature is that such a removal moves no key. The
suggested fix was to route the load sweep through the lower-level primitives
instead. **I recommend against that**: those primitives skip the x-offset gap-fill
and the span and lyric repairs, which the load path does need. The work is bounded,
happens once per file open, and is zero for any file this program wrote.

---

## Plan and manual-test record

- **Phase 8 is marked Done but its own required output is missing.** The phase says
  to run the eight manual checks and record here "the date, the build, Pass or Fail
  per number, and for any failure the gesture, what was expected and what was
  observed." Neither the plan nor the two manual-test documents contains a date, a
  build or a Pass/Fail line. The five interactive commit routes are where this
  feature meets the user, and the plan explicitly says they have no automated
  coverage.
- **`plans/797-redundant-key-changes.md:46`** reads "Six paths can move a key.
  **Five** can strand a key change and are covered here:" and then lists six. The
  sentence cannot be read as written.
