# Check findings — branch `design-pass-6b` since `develop`

Scope: the three commits of this branch. Production scope is 26 files, test scope is 6 files, doc scope is the `plans/design-pass-6b/` records, the register, and the five `docs/*.md` files the changed Javadocs name.

No production bug was found. The Design axis found no structural flaw. The Correctness & Efficiency axis found no defect.

## Contract and doc findings

### Finding 1. A Javadoc links to a type this branch deleted

- **Where:** `src/main/java/songscribe/layout/AccidentalFitGate.java:92`, the contract of the method that decides whether an edited note's accidentals fit on the line.
- **Now:** The sentence says every note named by a restamp is stamped "with `{@link AccidentalState.Stamped}`". This branch deleted that type. A chained accidental is now the state itself, and the restamp stamps the note through its own `stamp()` method.
- **Wrong:** Nothing in the build checks a Javadoc link, so the dead link stays. A reader who follows it finds nothing, or believes the deleted type still exists.
- **Change:** Reword the sentence to say the note is stamped through the restamp's `stamp()` method.

### Finding 2. Two comments describe a call that is no longer there

- **Where:** `src/main/java/songscribe/ui/component/ScoreViewController.java:1206-1208` and `:1365-1367`.
- **Now:** Each comment says that `keyAt`'s bound is inclusive, so the code asks one index below, and that index 0 holds no key change so asking about it is the same question. The line under each comment now calls `keyBefore`, which this branch added for exactly that question.
- **Wrong:** The comment explains an off-by-one adjustment that the code no longer makes. A reader looks for the "one index lower" and does not find it.
- **Change:** Delete the sentences about `keyAt`'s bound. Keep the first sentence of the deletion comment, which states why the key after the removal matters.

### Finding 3. The key-changes design note contradicts the `lineInserted` contract

- **Where:** `docs/key-changes.md:237-239`, and `src/main/java/songscribe/dom/ChainEdit.java`, the factory `lineInserted`.
- **Now:** The doc says "inserting a line moves no key, because the new line is empty". The factory's contract says the edit reaches the next key change when the inserted line has its own key, and the new test "a line inserted with its own key" exercises this. Undo of a line removal inserts a line that is not empty and may carry its own key.
- **Wrong:** The doc states a rule the code does not follow. A reader who trusts the doc expects no note past an inserted line to change state, and the code changes them.
- **Change:** Reword the doc: a new empty line moves no key; a line inserted with its own key, or with a key change on it, moves the key for every later line that inherits.

### Finding 4. The `keyBefore` contract omits the invariant its loop relies on

- **Where:** `src/main/java/songscribe/dom/Line.java:305-315`, the method that answers the key in force just before an index.
- **Now:** The contract promises the last key change "at an index below `index`". The loop stops before index 0, so a key change at index 0 is never seen. That is correct only because a key change is never at index 0, an invariant `KeyChangeElement`'s class Javadoc states and `keyAt`'s contract cites.
- **Wrong:** A caller who reads only `keyBefore`'s contract cannot see why index 0 is skipped. The sibling `keyAt` states it. The two contracts disagree on what they tell the reader.
- **Change:** Add the sentence `keyAt` has: index 0 never holds a key change, see the position invariant on `KeyChangeElement`.

### Finding 5. Two walk contracts still say "under the policy above"

- **Where:** `src/main/java/songscribe/dom/AccidentalChain.java`, the summaries of `walk(Song, ChainEdit)` and `walk(AccidentalScanView, ChainEdit)`.
- **Now:** Both say the walk decides notes "under the policy above". This branch cut the *The policy* section and replaced it with a citation to `ChainedAccidental`.
- **Wrong:** The reader looks up for a section that is not there.
- **Change:** Say "under the rule in `ChainedAccidental`'s class Javadoc".

### Finding 6. A new predicate has no `@return`

- **Where:** `src/main/java/songscribe/dom/ElementType.java:446-452`, `isTransparentInChain()`.
- **Now:** The Javadoc is one summary sentence. The method returns a boolean. Every other predicate on this enum carries `@return`.
- **Change:** Add `@return {@code true} for a rest or a breath mark`.

## Test Conformance findings

### Finding 7. Deleting a mid-line key change on its own has no test

- **Where:** `src/test/java/songscribe/dom/SongAccidentalWalkTest.java`, the case table `walkCases()`.
- **Now:** Eleven cases record one edit each and check that the song is left as a fresh walk would decide it, and that undo restores every note. A key change is inserted, replaced in, altered in place, and removed together with its whole line. No case removes only the key change element.
- **Wrong:** `ChainEdit.removed` promises to reach the next key change "when any removed element ends what is in force". If a plain deletion stopped treating a removed key change that way, every note after it would keep a state decided against the wrong key, and nothing in the suite would fail.
- **Change:** Add one case: a line with a barline, a key change and a note on each side; the edit removes the key change; the undo puts it back.

### Finding 8. Test fixtures place key changes where the domain forbids them

- **Where:** `SongAccidentalWalkTest.keyChangeInsertionCase` (key change added at index 0), `AccidentalChainTest.testInsertingAKeyChange…` (index 0), and the compact notations `F + "f K:ONE_SHARP …"` in `AccidentalChainTest.editCases`, `SongAccidentalWalkTest.keyChangeAlteredCase`, `lineRemovalWithKeyChangeCase`, `elementReplacementCase`, and `ProjectedEditTest.fragmentWithKeyChangeCase` (key change directly after a note).
- **Now:** `KeyChangeElement`'s class Javadoc states the position invariant: a key change is never at index 0 and always follows a barline or a repeat. Every production path keeps it. These fixtures build lines that break it.
- **Wrong:** Nothing fails today, because the walk reads the key change element itself and resets what is in force, so the missing barline changes no answer. The risk is in what the tests prove: they pass over states the application never produces, so a future rule that reads the invariant (for example, a walk that treats the barline before a key change as intervening) would be tested against inputs it never sees in production. Confidence is medium.
- **Change:** Put a barline before each key change in these fixtures, and insert key changes at index 1 after a barline rather than at index 0. `SongFactory.compactSong` accepts a barline token, so no helper changes.

### Finding 9. An inserted line with no key of its own has no case (lower confidence)

- **Where:** `SongAccidentalWalkTest.walkCases()`.
- **Now:** The doc singles out an empty inserted line as the one edit in its set that moves no key. The suite tests only the line-with-own-key case.
- **Wrong:** The stated boundary of `ChainEdit.lineInserted`, no reach when the line has no key and no key change, is untested. Nothing observable is unguarded today because other cases already show the walk stopping at the untouched note.
- **Change:** Add one case that inserts an empty line without a key and expects nothing to change. This depends on how Finding 3 resolves the doc.

## Questionable findings

These are agent findings I believe should not be acted on as stated. Each is presented for the user to confirm or reopen.

### Finding 10. The *The walk* section of `AccidentalChain`'s class Javadoc narrates the algorithm

The Contract agent reports that the section describes private `Walk` fields (`key`, `inForce`, `open`, `previous`, `span`) and a four-step loop, which `contracts.md` says does not belong in a contract. The agent also claims step 9 never judged the section. That claim is wrong: `findings-9.md:7-10` names the section and records that step 5 settled it as contract prose. The user decided this at steps 5 and 9. I list it so it is not silently dropped.

### Finding 11. `ChainType.decide` takes three adjacent `Accidental` parameters

The Contract agent reports the signature rule (two same-typed adjacent parameters need a record) and cites the pass's own note that a transposition of `sounding` and `inForce` compiles and inverts `ANCHOR` and `CANCEL`. The user decided to keep the signature at the step 2 gate (`record.md`, item 7). The method has one caller. I list it so it is not silently dropped.

### Finding 12. The exception message "Note is not stamped" in `NoteElement.chainedAccidental()`

The Contract agent reports the message uses vocabulary the branch retired. The verb survives: `Restamp.stamp()` stamps a note, and the class Javadocs still say "stamped". I believe the message is still accurate and does not need a change.
