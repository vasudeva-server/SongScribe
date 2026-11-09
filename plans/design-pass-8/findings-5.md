# Design pass 8 — step 5: write the contracts

Target: `dom`: `Tie`, `TieLoadPass`, `TieRule`, as step 4 left them (commit `8d18093e`), with the four members step 4 moved out of them. Paths are under `src/main/java/songscribe/`.

The tests of the target call six members outside the three files: `NoteElement.tie()`, `NoteElement.incomingTie()` and `Line.ownedTies()` (`TieWalkTest`), and `ElementType.isLegalTieSeparator()` through the walk. Their Javadoc is in this step, because a full contract belongs where its tests derive from.

## Counts

Counts are in members (methods, and the enum constants of `Tie.Judgment.Reason`). Each count assumes the structure that the items below propose. Where an item changes a count, the item says so.

|     | write | update | remove |
| --- | --- | --- | --- |
| Total | 2   | 13  | 3   |

| Class | write / update / remove | What the class promises |
|---|---|---|
| `Tie` | 1 / 7 / 3 | A value made of two notes that pass the pitched-note check. Its promises are the walk (where a tie ends), the judgment (whether a tie stays), the reach (which lines an edit redraws) and the arc side. Four of them have tests, and each needs one full contract. Today the walk's contract is on `NoteElement`, the judgment's is split over `holds()` and `judge`, and the arc side has none. |
| `TieRule` | 1 / 0 / 0 | One `ModificationRule`. All of `TieRuleTest` drives `apply`, and `apply` has no Javadoc. The class Javadoc holds the promise that `apply` makes. |
| `TieLoadPass` | 0 / 1 / 0 | One method with a full contract. The class Javadoc states the judgment clause a second time. |
| `ElementType` (moved member) | 0 / 1 / 0 | `isLegalTieSeparator` states the domain rule and its reasons, which stay. Its second paragraph names its readers. |
| `Line` (moved members, and `ownedTies`) | 0 / 2 / 0 | `firstNonSeparatorIndexFrom` has a bound and a null clause that do not match those of its sibling. `ownedTies` names its readers. |
| `NoteElement` (moved member, `tie`, `incomingTie`) | 0 / 2 / 0 | `canCarryTie` needs nothing: `isPitchedNote()` is the whole rule, and the code says it. `tie()` and `incomingTie()` restate the walk, the separator rule and the class's equality fact. |

## What it found

### `Tie` (`dom/Tie.java`)

- **Class Javadoc** (`:49–64`). It holds four paragraphs:
  
  1. The walk rule (the end is the first non-separator after the anchor, perhaps on the next line), and a pointer that says which of `tie()`, `incomingTie()`, `startingOn` and `endingOn` gives which tie. The walk rule is stated a second time in `NoteElement.tie()` (`dom/NoteElement.java:181–184`).
  2. The pitched-note invariant. This is a clause of type 4. `between` (`:82`) is the one constructor route, and `isAbove`, `arcSign`, `holds` and `bothNotesIn` all depend on the invariant.
  3. "No beam holds both notes of a tie; the tie wins over the beam on every route." No method of `Tie` keeps this. `Beam.spanning` refuses a beam over a tied pair, and `TieRule.judge` (`dom/TieRule.java:89–93`) removes a beam when a tie holds. This is a rule that no single type can enforce.
  4. The value fact (nothing stores a tie; equal by its two notes). This is a clause of type 4.
- `startingOn` (`:88–104`). The first sentence says what the method does. Its "whether or not `anchor` is tied" part is the class fact of paragraph 1. The `@return` null clause is correct. `TieWalkTest` calls `NoteElement.tie()`, which gives `Tie.startingOn(this)` when the note is tied. So the walk's full contract is on `tie()` today, and `startingOn` has none.
- `endingOn` (`:106–129`). It has the same first sentence. Its `@return` and `@invariant` are correct.
- `bothNotesIn` (`:131–141`) and `beamHoldingBoth` (`:143–151`). Each one says "The anchor need not be tied yet". Paragraph 1 of the class Javadoc already states this for every `Tie`. The `@param range` precondition of `bothNotesIn` is a clause of type 1, and it stays.
- `holds()` (`:161–169`). It has one caller, the private `judge(NoteElement, StaffElement)` (`:236`). Its first sentence defines when a tie holds. This is a domain promise, and it is the half of `judge`'s contract that `judge`'s `@return` reaches only through a link to a package-private method. Its second sentence is the time precondition (U4), and `judge` (`:213–215`) states it again.
- `Judgment` (`:171–174`). It names its three readers: the edit, the load and the user's request. The fact under it is a clause of type 3. The toggle, the rule and the load must agree, or the toggle offers a tie that the rule then removes. Step 1 found that defect (E♯ to F).
- `Judgment.Reason.NOT_HELD` (`:196`) states the `holds()` definition a second time.
- `Judgment.Reason.DAMAGED_ANCHOR` (`:199–203`). Its second sentence names the one class that gives the reason and the one method that never does. The fact that `judge` never gives it is part of `judge`'s `@return`, which lists only the other two reasons.
- `judge(NoteElement)` (`:213–231`). The precondition is a clause of type 1, and it stays here, at the public route. "It need not be tied" is the class fact again. The `@return` is close to a full contract. But it defines "holds" by a link to `holds()`, and it does not say that `DAMAGED_ANCHOR` never comes back. The record (step 6) says that the toggle's test derives from `judge`. So `judge` needs a full contract that a test author can use without the body.
- `reach` (`:287–340`). This is a full contract, and `TieWalkTest` derives from it. Three things are not in the form `contracts.md` states:
  
  - `@param mutation` (`:312`) says nothing.
  - The second paragraph (`:291–295`) gives the motivation and three examples. Only its first sentence (a line draws the half of a tie that crosses into it) is a fact a caller needs.
  - Its only caller, `ScoreViewController.songDidChange` (`ui/component/ScoreViewController.java:484`), asks after the mutation landed. The `@return` clauses ("no longer both in the line", "no longer in that line") depend on that, but no clause states it as a precondition.
- `isAbove` (`:397–400`). It has no Javadoc, and it needs none. Its body comment (`:398`) restates the sign convention of `arcSign`'s first paragraph.
- `arcSign` (`:402–414`). The first paragraph (the meaning of the sign) is correct. The second paragraph names three readers. The constraint under it is a clause of type 3: drawing and export must agree on the side. The third paragraph (`.opposite()` "below", "do not re-derive ±1 per branch") is about the body. It is a warning against a fix, so it is a body comment, not Javadoc. The rule in `tieArcDirection` (`:416–454`) has no contract, and the record (step 6) says that it needs a test.
- `tieArcDirection` reads only the left note's staff position when neither note has a stem (`:441–442`). It relies on the tie holding. A `Tie` from `startingOn` need not hold.
- `between`, `anchor`, `end`, `endAfter`, `anchorBefore`, the reach origins, `equals`, `hashCode` and `toString` need nothing.

### `TieRule` (`dom/TieRule.java`)

- **Class Javadoc** (`:37–43`). It holds the whole promise of `apply`: which lines it judges, what a broken tie and a held tie each cause, and that the tie wins. Its last sentence names the load stage, which is a reader and not a promise of this class.
- `apply` (`:46–68`) has no Javadoc. `TieRuleTest` drives it through edits in a modification bracket. Each clause that a case of `TieRuleTest` comes from needs to be in its full contract:
  
  - It judges each tied note of the lines the batch touched and of the line before each.
  - A broken tie is removed and reported, with its reason, on the lines of the `Broken`.
  - A held tie removes the beam that holds both of its notes, and that removal is reported under the beam reason.
- `TieRuleTest.testOneUndoRestoresTheEditAndTheTieItRemoved` asserts that one undo restores the removal. That is a promise of every `ModificationRule`: a rule's changes join the bracket's batch. It is not a promise of `TieRule`.
- Two cases of `TieRuleTest` assert something that `TieRule` does not do. "deleting the end note unties the anchor and reports nothing" (`:174`) and the three silent cases in `crossLineCases` (`:274–282`) are the deletion's untie. "converting the anchor to a grace note leaves nothing tied" (`:165`) is `StaffElement.convertedFrom`. They belong to step 6. I list them under the record's _Findings raised_.
- The body comment at `:88` ("The tie wins over a beam…") restates the class Javadoc. The comment at `:80` is a clause of type 3, and it stays.

### `TieLoadPass` (`dom/TieLoadPass.java`)

- **Class Javadoc** (`:29–34`). The purpose sentence stays. The second paragraph has two clauses. "Stays by the same judgment as a tie an edit leaves" is the first `@effects` of `run` a second time. "Nothing is repaired into something the file did not say" is a domain promise of `run`, and no clause of `run` states it.
- `run` (`:40–52`). The full contract is correct, and `TieLoadPassTest` derives from it.

### The moved members, and the members that `TieWalkTest` derives from

- `ElementType.isLegalTieSeparator()` (`dom/ElementType.java`). The first paragraph states the rule and the reasons for its two exceptions. These are clauses of type 5 and type 3. The second paragraph names `RangeQueries.canToggleTie` and the walk. `canToggleTie` still reads the predicate (`ui/selection/RangeQueries.java:153–155`). It refuses a three-element selection whose middle element is not a separator. But it then asks `Tie.judge` and compares the end (`:160`). When the middle element is not a separator, the walk ends on it, so the end comparison refuses the selection already. The two cannot disagree, and the check at `:153` is redundant. The redundant check is a step 3 matter for the row that owns `ui/selection`. I list it under the record's _Findings raised_.
- `Line.firstNonSeparatorIndexFrom(int)` (`dom/Line.java:567–574`). The `@param` says "at or above 0" with no upper bound. Its sibling says `0..elementCount()`. The `@return` uses a double negative ("no element … is other than").
- `Line.lastNonSeparatorIndexBefore(int)` needs nothing.
- `NoteElement.canCarryTie()` needs nothing.
- `NoteElement.tie()` (`dom/NoteElement.java:175–196`). It holds the walk's full contract. It also restates the separator rule after a dash (`:182–183`), and the value fact of `Tie` ("Each call makes a new `Tie`…").
- `NoteElement.incomingTie()` (`:198–216`). Its prose ("Walks back…") says how it finds the tie. The `@return` and `@invariant` already state the whole promise.
- `Line.ownedTies()` (`dom/Line.java`). The ownership rule and the order in `@return` are a full contract, and `TieWalkTest` derives from it. The paragraph "The one statement of that ownership rule. Layout calls it once per line…" names its readers.
- `Line.addTie` (`dom/Line.java:514–516`) is outside the target. Its Javadoc says "Nothing here judges the tie: `Tie#judge` does, for an edit and for a load." The fact a caller needs is that `addTie` does not judge the tie. The names of the readers are not that fact.

### Signature changes

None. No member of the target has more than four parameters, two transposable parameters of one type, or a `boolean` that selects a mode. No name misdescribes its member.

## What needs deciding

### 1. Where the walk's full contract lives

The walk is in `Tie.startingOn` and `Tie.endingOn`. The full contract is on `NoteElement.tie()` and `NoteElement.incomingTie()`, because `TieWalkTest` calls those two. The record (step 6) says that `startingOn` and `endingOn` need their own tests.

- **a.** The full contract goes on `startingOn` and `endingOn`. The walk, the separator link and the one-line-break limit go on `startingOn`. The `@invariant` that each inverts the other goes on `endingOn`. `tie()` keeps only its null clause for an untied note, and its `@invariant`. `incomingTie()` keeps only its `@return` and `@invariant`. Step 6 then decides whether the walk cases call `startingOn` directly. The class Javadoc of `Tie` drops the walk rule of paragraph 1, and keeps the fact that a `Tie` exists whether or not its anchor is tied.
- **b.** The full contract stays on `tie()` and `incomingTie()`, and `startingOn` and `endingOn` link to it. Then a test of `startingOn` on an untied note derives from a contract on a method that gives `null` for that note.

The counts above assume **a**. With **b**, `startingOn` is an update and not a write, so `Tie` is 0 / 8 / 3.

### 2. The walk, confirmed as a domain promise

```
`Tie.startingOn(NoteElement)` (with item 1a)

  Proposing: a tie from a note ends on the first element after it that is
  not a legal separator. A legal separator is a barline or a breath mark,
  other than a final double barline. A key change is never one. The walk
  crosses one line break, and no more. When that first element is a rest,
  a grace note, or anything other than a pitched note, the note has no tie
  to end on. A line that holds only separators also stops the walk.

  Today: the implementation does this, and TieWalkTest pins each case. The
  promise came from the existing Javadoc of NoteElement.tie() and
  isLegalTieSeparator, and from the code. It did not come from a source on
  notation.

  Also defensible: a grace note between two tied notes does not break the
  tie; the tie passes over it to the main note. Engravers do sometimes draw
  a tie past a grace note that ornaments the second note. Today the grace
  note breaks the tie, and the edit "insert a grace note between them" in
  TieRuleTest reports NO_FOLLOWING_NOTE. I propose to keep today's rule,
  because a tie that passes over a sounding note is a different notation.
  But this is a call I cannot make.
```

### 3. When a tie holds, and the full contract of `judge`

`judge(NoteElement)` gets the full contract. It states when a tie holds, in its own words and not through a link to `holds()`. It keeps the time precondition, and it says that `DAMAGED_ANCHOR` never comes back. `holds()` then has no Javadoc. `NOT_HELD` links to `judge` and does not restate the definition. `DAMAGED_ANCHOR` keeps only what the reason means.

```
`Tie.judge(NoteElement)`

  Proposing: a tie holds when its two notes sit at one staff position and
  sound one alteration. The written duration, the written accidental, the
  stem and the beam play no part. Two notes that sound one pitch but are
  spelled differently (E♯ and F) do not hold.

  Today: the implementation does this (holds(), :166–169). The promise came
  from the code and from step 1's finding about the toggle.

  Also defensible: a tie holds when the two notes sound one MIDI pitch.
  Then E♯ to F holds. But both notes of a tie are usually spelled alike, and
  a key change already stops the walk (item 2). I propose to keep today's
  rule, but this is a call I cannot make.
```

### 4. The beam rule moves to a guide

"No beam holds both notes of a tie; the tie wins over the beam on every route" is a rule that no single type enforces (`Beam.spanning`, `TieRule.apply`, and the toggle). `contracts.md` sends such a rule to `guides/subsystems/`.

- **a.** The rule moves to _Rules_ in `guides/subsystems/tie-placement.md`, in the form "a rule, because what breaks". `Tie`'s class Javadoc drops it. `TieRule.apply`'s full contract keeps its own half (a held tie removes the beam), and `Beam.spanning`'s contract keeps the other half.
- **b.** The rule moves to _Rules_ in `guides/subsystems/span-invalidation.md`, which already holds `TieRule` and `Beam` as members. Everything else is as in **a**.

The record's step 12 item already says that `tie-placement.md` lists only `Tie` and `BezierBow`. With **a**, `tie-placement.md` gets a _Rules_ section now. Step 12 then adds the members.

### 5. `Tie.Judgment`'s reason for being one judgment

The Javadoc of `Judgment` keeps its purpose ("whether a tie stays"). It replaces the names of its three readers with the clause of type 3: the edit, the load and the toggle get one answer, so the toggle never offers a tie that the rule removes when the bracket closes. This is the defect step 1 found. Confirm that this clause stays.

### 6. `TieRule.apply`'s full contract, and the class Javadoc

`apply` gets a full contract from the clauses in _What it found_. The class Javadoc keeps only its purpose: the rule that judges the ties an edit leaves. The load-stage sentence goes. The body comment at `:88` goes.

The undo clause is not in `apply`'s contract, because every `ModificationRule` promises it. So `testOneUndoRestoresTheEditAndTheTieItRemoved` derives from no contract of `TieRule`. Step 6 decides where that test goes. Confirm that the undo clause stays off `apply`. If you want it on `apply`, `apply` then restates a promise of `ModificationRule`.

### 7. `TieLoadPass`: the repair promise goes onto `run`

The class Javadoc keeps its purpose sentence. Its "same judgment" clause goes, because the first `@effects` of `run` states it. "Nothing is repaired into something the file did not say" becomes a clause of `run`'s full contract: a pending tie that the judgment refuses is dropped, and no anchor, end or pitch changes to make it hold. Confirm that this is the promise. It came from the existing class Javadoc and step 3's item 7.

### 8. `Tie.reach`

- `@param mutation` goes.
- The motivation paragraph keeps its first sentence only. The three examples go.
- A clause of type 1 is written: `reach` is asked after `mutation` landed, and every position it reads is in the line as it is then.

### 9. `Tie.arcSign`'s full contract

`arcSign` gets a full contract for the rule in `tieArcDirection`. The paragraph that names the readers becomes the clause of type 3 (drawing and export read one side, so they agree). The `.opposite()` paragraph becomes a body comment in `arcSign`. The comment in `isAbove` goes.

```
`Tie.arcSign()`

  Proposing: when both notes have a stem, the tie is below when both stems
  are up, and above otherwise (both down, or one each way). When only one
  note has a stem, the tie is on the side away from that stem. When neither
  note has a stem, the tie is above a note above the middle line, below a
  note below it, and above a note on the middle line.

  Today: the implementation does this (:416–454). The promise came from the
  code. I did not check it against a source on notation.

  Also defensible: a note on the middle line with no stem gets its tie
  below. Also, with one stem each way, the tie goes on the side away from
  the first note's stem. I propose today's rule, but this is a call I
  cannot make.
```

The no-stem branch reads only the left note's staff position. For a tie that holds, both notes share it. Decide which of these is the correct structure:

- **a.** The contract states a clause of type 1: `arcSign` is asked only of a tie that holds. Every caller today asks of a stored tie, and the rule judged that tie when the last bracket closed.
- **b.** `arcSign` promises a side for every `Tie`, and the no-stem branch states which note it reads. Then the promise covers a tie that `startingOn` gives for a pair that does not hold.

### 10. The members outside the three files

Each of these is an update. Confirm them.

- `ElementType.isLegalTieSeparator()`: the paragraph that names the readers goes. The first paragraph stays.
- `Line.firstNonSeparatorIndexFrom(int)`: the `@param` bound becomes `0..elementCount()`, as its sibling's is, and the `@return` drops its double negative.
- `Line.ownedTies()`: the paragraph that names Layout and "everything downstream" goes.
- `NoteElement.tie()` and `NoteElement.incomingTie()`: as item 1 decides.
- `Line.addTie` (outside the target): its Javadoc keeps the fact that it does not judge the tie, and drops the names of the readers. Decide whether to make this change now or to record it for the pass that owns `Line`.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: All of the proposed contracts are implementation in prose. Something is wrong, because doesn't contracts.md specifically forbid that? A contract is only supposed to express what a caller *must* know in order to provide proper inputs and consume outputs.
    by: user
    at: "2026-11-09T03:17:17.730Z"
    scope: document
