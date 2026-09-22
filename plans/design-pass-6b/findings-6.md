# Findings — step 6, pass 6b, _Accidentals_

Paths are under `src/main/java/songscribe/dom/` and `src/test/java/songscribe/dom/` unless stated otherwise.

Every existing test in `AccidentalChainTest` and `SongAccidentalWalkTest`, triaged against the floor in `design.md`, plus the cases that floor calls for and no existing case states. One table, per the step's own instruction: a kept test, a rewritten test and an added case are dispositions of the same list.

## The table

| Test | Kind | Disposition | Why |
|---|---|---|---|
| `testWalkSongStampsEveryNoteAsThePolicySays` — 8 whole-song cases (worked example, barline/repeat/line-start intervening, rests and breath marks transparent, tied notes, grace note intervening, key change ends in force, own-line key ends in force, chain crosses a line break) | algorithm | keep | Each case can fail against the policy `AccidentalChain`'s class Javadoc states, and together they cover its named intervening-element and key-change branches. |
| `testPressingNaturalOnTheFifthNoteMakesItACancelAndTheSixthAnAnchor` | algorithm | keep | A `modified` edit's effect, plus the walk's early-stop condition (`spanEnd`) — a distinct assertion no sibling shares. |
| `testDeselectingTheSecondNotesFlatMakesItInKeyAndTheThirdAnAnchor` | algorithm | keep | A `modified` edit reverting a written accidental to the key's own. |
| `testPressingFlatOnTheSeventhNoteMakesItImplicitAndTheNinthACancel` | algorithm | keep | A `modified` edit reaching past an untouched note between two changed ones — the untouched note's own decision not changing is a distinct assertion no sibling shares. |
| `testDeletingTheAnchorLeavesTheSurvivingCancelInKey` | algorithm | rewrite | Same shape as the two rows below — build a song from compact notation, remove one element, `ChainEdit.removed`, walk, `assertRestamps` one tuple, no other assertion. A record case table (song notation, removed index, expected tuples) states the three once. |
| `testDeletingTheInterveningNoteCollapsesTheRepetitionToImplicit` | algorithm | rewrite | Same shape as above — folds into the same case table. |
| `testDeletingANoteReachesPastInterveningOtherPositionNotesToRedecideItsVacatedPosition` | algorithm | rewrite | Same shape as above — folds into the same case table. |
| `testAnAdoptingNoteMovedToAnotherPositionTakesWhatIsInForceThere` | algorithm | keep | A `modified` edit moving a note's position — a different factory and a different promise (`Adopting` sounds what is in force at its new position) than the removal family above. |
| `testInsertingAKeyChangeWalksOnToTheNextKeyChangeRestatingEveryNoteItAlters` | algorithm | keep | An `inserted` edit with `Reach.TO_NEXT_KEY_CHANGE`, plus a distinct "position the new key does not alter is undisturbed" assertion. |
| `testANoteInsertion` | invariant | rewrite | Same shape as the four rows below — build a song, define an edit `Runnable` and an undo `Runnable`, call `assertEditWalksItselfAndUndoRestores`. One `@ParameterizedTest` case table (description, song builder, edit, undo) states all of them once, including the three new cases below. |
| `testARangeDeletion` | invariant | rewrite | Same shape as above — folds into the same case table. |
| `testAPitchChangeToAdopting` | invariant | rewrite | Same shape as above — folds into the same case table. |
| `testAKeyChangeInsertion` | invariant | rewrite | Same shape as above — folds into the same case table. Note: this inserts a `KeyChangeElement`, which is a different `ChainEdit.of` branch from the new "line's own key changed" case below. |
| `testALineDeletion` | invariant | rewrite | Same shape as above — folds into the same case table. The removed line here holds no key and no rekeying element, so `lineRemoved`'s `Reach.TO_NEXT_KEY_CHANGE` branch stays unexercised; see the new case below. |
| *(new)* a line's own key changed on an already-loaded line redecides the notes after it, and undo restores | invariant | add | `Line.setKey` on a line already in the song records `LineKeyChange`, which `ChainEdit.of` maps through `lineRekeyed`. Today that mapping is reached only by `AccidentalChain.walkSong`'s load pass (`rekeyingFrom`, identical `ChainEdit` shape) — never by a live edit. Row in the rewritten `SongAccidentalWalkTest` table. |
| *(new)* removing a line that holds its own key walks on to the next key change | invariant | add | `ChainEdit.lineRemoved`'s `@return` promises `Reach.TO_NEXT_KEY_CHANGE` "when the removed line had its own key or held a key change"; `testALineDeletion`'s line has neither, so the branch is untested. Row in the same table. |
| *(new)* inserting a new line that establishes its own key walks on to the next key change | invariant | add | `ChainEdit.lineInserted`'s `@return` states the same rekey clause for the inserted line's own key; `testAKeyChangeInsertion` inserts a key-change *element* into an existing line, not a new *line* that starts its own key. Row in the same table. |
| *(new)* replacing a note with a key change vacates the note's position and reaches the next key change | invariant | add | `ChainEdit.replaced` has no test anywhere in the repo — only `ChainEdit.of`'s `ElementReplacement` branch and `ProjectedEdit.walk` call it, and neither is exercised. `Line.setElement` records `ElementReplacement`; replacing a note with a `KeyChangeElement` exercises both `replaced`'s vacate clause (the note's position) and its rekey clause (the inserted element ends what is in force) in one case. Row in the same table. |
| *(new)* `ChainEdit.of` answers null for the walk's own restamp | algorithm | add | `of`'s `@return` names this explicitly: a restamp `ElementModification` (fields exactly `{ACCIDENTAL}`, after a note holding a `ChainedAccidental` at the same position) maps to null, which is what stops `Song.applyChange` from re-walking its own write. A direct static-method case, independent of the `Song` integration path. See *What needs deciding* for where it lives. |
| *(new)* a projected edit's restamps agree with a fresh walk once the edit actually lands | invariant | add | `ProjectedEdit`'s own class Javadoc states the promise: "an edit's preview and its result cannot disagree about a note." No test checks this anywhere — `AccidentalFitGateTest` calls `.inserting(...).walk()` but only as an opaque input to `AccidentalFitGate`, and never calls `.replacing(...)` at all. See *What needs deciding* for where it lives. |

Discard: none. Nothing in either file pins a private helper's decomposition, a guard no caller can reach, or a duplicate of a case already asserted.

## What needs deciding

### 1. Two new test files, or fold the two orphaned contracts into existing ones?

`ChainEdit` and `ProjectedEdit` are both named in row 6b's target, and each has a promise no existing file states as its own: `ChainEdit.of`'s null-for-restamp clause, and `ProjectedEdit`'s preview-matches-result invariant. `AccidentalChainTest`'s own class Javadoc scopes it to `AccidentalChain#walk` and `#walkSong`'s promise, not `ChainEdit`'s or `ProjectedEdit`'s.

<span id="rd-c1">**Option A.** Add `ChainEditTest.java` and `ProjectedEditTest.java`</span>, one class per promise, matching the file-per-class pattern `AccidentalChainTest` already sets for `AccidentalChain`.

- Con: two more files resident in `src/test/java/songscribe/dom/`, alongside the two the record's _Target_ section named at step 0.

**Option B.** Add both cases to `SongAccidentalWalkTest`, since it already exercises `ChainEdit.of` and (for the `ProjectedEdit` case) can compare a projected walk against the same edit applied for real.

- Con: `SongAccidentalWalkTest`'s own class Javadoc scopes it to `Song#applyChange`'s promise; a `ChainEdit.of` case built by hand, without going through `Song`, and a `ProjectedEdit` case built before anything commits, both sit outside that promise.

### 2. The record case table's row shape

Both rewrites (the three-test removal family in `AccidentalChainTest`, and all five of `SongAccidentalWalkTest`) need a row shape settled before the rewrite is written:

- The removal family's expected values are `NoteElement` identities pulled from the built song, not literals — the table most likely carries the removed index and the _indices_ of the expected notes, resolved against the built song inside the test method, the same way `typesOf` already resolves the parameterized song cases above it.
- <span id="rd-c2">`SongAccidentalWalkTest`'s row needs an edit and an undo that close over objects the song-building step creates</span> (a `Line`, an inserted element) — most likely a `Function<Song, Runnable>` pair per row rather than a bare `Runnable`, so each case's lambda can capture what it needs from the song the row itself builds.

Neither changes what is tested; both are settled at execution, not here.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: ok
    by: user
    at: "2026-09-22T02:24:06.132Z"
  rd-c2:
    body: this
    by: user
    at: "2026-09-22T02:25:35.192Z"
