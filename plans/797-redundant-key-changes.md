# Redundant Mid-Line Key Changes

| #   | Phase | Status | BlockedBy |
| --- | --- | --- | --- |
| 1   | [The Line Query](#-phase-1-the-line-query) | ✅ Done | — |
| 2   | [Extract Range Deletion](#-phase-2-extract-range-deletion) | ✅ Done | — |
| 3   | [Deletions on a Reached Line](#-phase-3-deletions-on-a-reached-line) | ✅ Done | 1 |
| 4   | [The Three Key Dialog Routes](#-phase-4-the-three-key-dialog-routes) | ✅ Done | 2, 3 |
| 5   | [Line Deletion](#-phase-5-line-deletion) | ✅ Done | 2, 3, 4 |
| 6   | [Paste](#-phase-6-paste) | ✅ Done | 2, 3, 5 |
| 7   | [Contracts and Docs](#-phase-7-contracts-and-docs) | ✅ Done | 4, 5, 6 |
| 8   | [Tests](#-phase-8-tests) | ✅ Done | 7 |

Phases 2, 5 and 6 all write `ScoreViewController.java`, and 4 → 5 → 6 serializes
it. Phase 4 must land before 5 so the sweep has one caller working before it has
three.

## The invariant this establishes

**A `KeyChangeElement` never restates the key in effect immediately before it.**
An edit that would strand one removes it, together with the barline it sits
behind, inside its own modification bracket. Reading a file sweeps the whole song
the same way, silently, so a document written before the rule existed arrives in
memory already repaired.

This is the mid-line counterpart of `Line.setKey`'s normalization, and
`docs/key-signatures.md` already states the reasoning for the line-key half: an
element that restates the running key draws nothing and is invisible on screen
while still sitting in the document. `KeyChangeElement.extent()` re-reads the key
it changes *from* off the line on every call, so a stranded element reports zero
accidentals and zero width, yet is still written to MusicXML by
`MeasureBuilder.java:339` and still refuses the two insertion indices flanking it
in `KeyChangeAction.acceptsInsertionIndex`.

Six edits move a key, and every one of them can strand a key change: changing a
line's own key, inserting a mid-line key change, changing one already written,
deleting elements, deleting a line, and pasting. **Line insertion cannot**
— `ScoreViewController.handleInsertLine` adds an empty `Line`, which inherits its
key and passes the same key on, so no following line's running key moves.

## Conventions

- Read `.claude/guides/contracts.md` before writing any new method or class
  contract, and `.claude/guides/vocabulary.md` before naming anything. **Key**
  names the value and the edit; **key signature** names only what is drawn. The
  names below follow that rule.
- Read `.claude/rules/serena.md` and use the `jet_brains_*` tools for Java
  exploration and refactoring.
- No issue numbers in source, Javadoc or comments.
- Compile with `./scripts/compile.sh --test` after each phase. Every phase must
  be able to report SUCCESS at the moment it ends.
- Snapshot at each phase end:
  `git add -A && git stash store -m "Finished phase N" "$(git stash create)"`
- Do not write a test before Phase 8's list is proposed and approved.

## Two defects Phases 5 and 6 fix

These are prerequisites, not additions: stranding detection rides the
inheritance-chain reach, and neither path has one today. `lineKeyChangeReach` and
`linesInheriting` have exactly three callers — `ScoreViewController` element
deletion and the two mid-line routes in `KeyChangeDialogController`.

- **Line deletion reconciles nothing.** `ScoreViewController.java:1003` is the
  whole of it. Deleting a line that held its own key or a mid-line key change
  changes what every following line inherits, so notes on those lines change
  sounding pitch with no reconciliation and no prompt.
- **Paste reconciles only the destination line.** `tryInsertFragment` builds one
  `InsertionRegion` for the host and stops. `Fragment.capture` widens back over
  the barline in front of a key change precisely so a fragment can carry one, so
  a paste moves the key on every inheriting line with nothing reconciled.

---

## ✅ Phase 1: The Line Query

**Recommended model/effort:** Sonnet, medium.

Add to `Line`:

```java
public List<EffectiveRange> redundantKeyChangeRanges(Key runningKey)
```

Walk from `FIRST_LEGAL_KEY_CHANGE_INDEX` to `effectiveElementCount() - 1`,
tracking a running key that starts at `runningKey`. At each `KeyChangeElement`:
when its key equals the running key, add `effectiveRange(i, i)` to the result;
otherwise set the running key to its key.

The result is ascending and its ranges are disjoint. A caller deleting them must
work from the last to the first, or the earlier indices shift under it.

Contract points to state:

- The parameter is the key the line **will** run in once the caller's edit
  commits, not `getRunningKey()`. The query is pure and pre-mutation like every
  other reconciliation input.
- The running key is not advanced past a redundant element, because it already
  equals it. That is why one forward pass suffices and why removing one range can
  neither create nor hide another.
- The range comes from `effectiveRange` rather than from a local test, so the
  barline pairing has exactly one definition. Whatever manual deletion of that
  element takes, this takes.
- Removing a key change that restates the key in effect **cannot change the key
  in effect at any position** — it deletes a step that steps to the same value.
  So `keyAt`, `keyAtEndOfLine` and the whole inheritance chain are invariant
  under the removal, which is what lets the reach be computed once, before
  anything commits, without the sweep invalidating it.

## ✅ Phase 2: Extract Range Deletion

**Recommended model/effort:** Opus, high — moving live mutation code out of a
1900-line class.

`ScoreViewController.deleteElementRange` (`ScoreViewController.java:1306`) is the
only code that deletes an element range correctly. Its mutation half must become
callable from the key-edit paths.

Extract into `songscribe.ui.edit` — `ElementRangeDeletion` unless
`.claude/guides/contracts.md` and the vocabulary guide point elsewhere — the part
of `deleteElementRange` that runs inside the bracket:

- the `line.effectiveRange(begin, end)` widening;
- the paired-grace-note branch, which falls back to the per-element loop because
  that removal is non-contiguous;
- the x-offset shift over the elements after the range;
- the glissando strip on the element before the range, recorded through
  `modifyElement(SLIDE)`;
- `adjustSyllablesForNeighborChange` and the `adjustExtendsForDeletion` loop;
- `line.removeRange`.

**The accidental commit does not move.** `commitDeletionAccidentals` and
`KeyChangeReconciliation.commit` stay with each caller, because each caller owns
its own reconciliation and must record it before the removal, while the indices
are still pre-removal. `ScoreViewController` keeps doing both and then calls the
extracted unit.

The extracted unit must be called inside an already-open modification bracket and
must open none of its own. `deleteElementRange`'s `label` parameter and its
`withOptionallyNamedModification` call stay in `ScoreViewController`.

State in the extracted contract that it re-widens the range it is handed, and
that handing it an already-widened range is a fixed point.

## ✅ Phase 3: Deletions on a Reached Line

**Recommended model/effort:** Opus, high — the projection is what decides which
notes change pitch.

`AccidentalReconciliation.ModifiedLine` describes one line a key move reaches. It
cannot currently say that the move also deletes something on that line, which is
what a stranded key change is.

1. Give `ModifiedLine` the ranges the edit deletes on that line. Keep `of` and
   `reKeyed` as they are so every existing caller compiles unchanged, and add the
   ranges through a further factory.
2. Have `reconcileLine` omit those indices when it builds its projected sequence,
   and lower `lowestChangedIndex` to the first deleted index. Positions below that
   index are unshifted, so the existing walk start stays valid.
3. Have `lineKeyChangeReach` and `linesInheriting` fill the ranges in from Phase
   1's query, each line asked under the key it will run in — the key those two
   already compute per line.

That puts redundancy detection in one place. Every caller of the reach picks it
up without knowing about it.

State in `reconcileLine`'s contract why a deleted range lowers the walk start:
the notes after it resolve against a context that no longer has the barrier the
key change and its barline provided. `ElementType.cancelsAccidentals()` is what
makes both of them barriers, so removing the pair genuinely moves sounding
pitches — this is not bookkeeping.

Do **not** give `InsertionRegion` a running key. Its comment that an insert or a
delete cannot move the key the line starts in stays true for its own callers; the
combined case belongs to `ModifiedLine`.

## ✅ Phase 4: The Three Key Dialog Routes

**Recommended model/effort:** Opus, high.

`KeyChangeDialogController.changeLineKey`, `changeMidLineKey` and
`insertKeyChange` already build a reach and raise one restatement prompt through
`KeyChangeReconciliation.confirm`. After Phase 3 the reach already carries the
ranges to delete, so the prompt already covers them.

Each of the three commits the deletions inside the bracket it already opens,
after its key move, through Phase 2's unit — last range first, per line. One undo
takes back the whole edit.

`changeMidLineKey` and `insertKeyChange` reach `linesInheriting` directly rather
than through `lineKeyChangeReach`, and their head line is reconciled through an
`InsertionRegion` rather than a `ModifiedLine`. Its stranded ranges therefore ride
in the region rather than in a `ModifiedLine`, and are swept at a moment of the
route's own choosing, ahead of the element edit that moves the indices they name.

No fit check changes. Removing elements only frees room, so a sweep can never
turn an edit `KeyEditFitCalculator` accepted into one that does not fit.

## ✅ Phase 5: Line Deletion

**Recommended model/effort:** Opus, high — this phase changes sounding pitches on
lines the user did not select.

`ScoreViewController.handleDelete`'s line branch (`ScoreViewController.java:1003`)
currently opens a bracket and calls `song.removeLine(lineIndex)`.

Give it the reach it owes: `linesInheriting` from the line before the deleted one,
carrying the key the song runs in once the line is gone, reconciled and prompted
through `KeyChangeReconciliation.confirm` exactly as every other key move is.
Cancelling leaves nothing mutated and no undo step. Deleting line 0 promotes the
next line, which `Song.maintainKeyInvariant` already repairs — read
`repairLineZeroKey` before deciding what key the reach starts from.

The stranded ranges arrive with the reach from Phase 3 and are deleted in the same
bracket, before `removeLine`.

## ✅ Phase 6: Paste

**Recommended model/effort:** Opus, high.

`tryInsertFragment` (`ScoreViewController.java:1453`) reconciles its head line and
stops. Add the tail: `linesInheriting` from the key the fragment leaves the
destination line in, folded into the one confirm the paste already raises through
`AccidentalRestatements.confirm`, so a paste still asks at most once.

The stranded ranges arrive with that tail and are deleted in the paste's existing
bracket. The head's own ranges wait until the paste is done moving elements, and
are shifted by what the insertion moved them by. A fragment's own key change can
also be stranded on arrival, when it lands where that key already runs — Phase 1's
query finds it on the head line once the fragment's elements are in place.

**Paste is not fit-gated on the lines it re-keys.** Its gate measures the
destination line only, and this phase does not add more: a line left overflowing
by a key that moved renders overflowing and flagged, which is the program's
ordinary behaviour everywhere except the four edits `KeyEditFitCalculator` covers.
State that in the doc task rather than leaving a reader to infer it.

## ✅ Phase 7: Contracts and Docs

**Recommended model/effort:** Opus, high. Read `~/.claude/guides/documents.md`
first.

`docs/key-signatures.md`:

- Under *A mid-line change always follows a barline*, state the invariant: a key
  change never restates the key in effect before it, and the edit that would
  strand one removes it with its barline. Say that the pair is removed by whatever
  rule manual deletion uses, so a repeat or a double barline in front of a key
  change goes the same way one does when the user deletes it themselves.
- Under *Changing a key changes pitches*, replace "Four edits move a key and owe
  that reach" with the five that do, and say that line insertion does not because
  an empty line passes its inherited key straight through.
- Add that paste is not fit-gated on the lines it re-keys.

Write the contract rationale into the members it shaped, not into this plan:
`Line.redundantKeyChangeRanges`, `ModifiedLine`'s deleted ranges, `reconcileLine`,
and the extracted deletion unit each carry their own.

## ✅ Phase 8: Tests

**Recommended model/effort:** Opus, high.

The logic is unit-tested in six classes: `dom/StrandedKeyChangeTest`,
`dom/LineDeletionTest`, `layout/AccidentalReconciliationTest`,
`ui/clipboard/FragmentTest`, `ui/edit/KeyChangeReconciliationTest` and
`io/musicxml/MusicXmlStrandedKeyChangeTest`.

The five commit routes are verified by hand. **There is no e2e set for them**:
each runs through a controller that needs the main window and the key dialog, so
what a test could reach is the logic the six classes already cover. The checks
are `KeyChange.md` 11–14 and `ScoreView.md` 1–4, and both read the fixture
`src/test/resources/fixtures/redundant-key-changes.musicxml` — four lines, the
middle two each carrying a mid-line key change that one of the five edits
strands.

Run those eight checks and record the results here: the date, the build, Pass or
Fail per number, and for any failure the gesture, what was expected and what was
observed.
