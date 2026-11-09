# Design pass 8 — step 6: triage the tests

Target: `dom`: `Tie`, `TieLoadPass`, `TieRule`, as step 5 left them (commit `05b0722b`), with the members step 4 moved to `ElementType`, `Line` and `NoteElement`. Test paths are under `src/test/java/songscribe/`, production paths under `src/main/java/songscribe/`.

## What it found

### The tests today

| Class | Test methods | Rows |
| --- | --- | --- |
| `dom/TieWalkTest` | 4   | walk 10, separators 6 (one per non-duration type), owned ties 1, reach 16 |
| `dom/TieRuleTest` | 4   | edits 11, beam 1, undo 1, cross-line 4 |
| `dom/TieLoadPassTest` | 1   | 8   |

### Facts the table depends on

- **One judgment is tested in three places.** `Tie.judge` decides `Holds`, `NOT_HELD` and `NO_FOLLOWING_NOTE`, and nothing calls it directly in a test. `TieRuleTest.editCases` (`dom/TieRuleTest.java:145–177`) reaches it through an edit and the rule. `TieLoadPassTest.tieCases` (`dom/TieLoadPassTest.java:95–138`) reaches it through the load. The cross-line `where` of a broken tie is asserted only through the load (`:119–125`). The toggle (`ui/selection/RangeQueries.canToggleTie`) is the third caller of `judge`, and it has no test of its own.
- **The E♯-to-F defect is a case of** `judge`**.** Since step 4 the toggle asks `Tie.judge`, so the toggle cannot offer a tie that the rule then removes. The design now carries that agreement. The case that stays is the judgment itself: two notes that sound one pitch at two staff positions do not hold. No test asserts it.
- `TieWalkTest` **reaches the walk through** `NoteElement.tie()` **and** `incomingTie()`**.** Step 5 put the walk's contract on `Tie.startingOn` and `Tie.endingOn`. The two `NoteElement` methods only add a gate on the stored tie. The fixtures write `~` only to pass that gate.
- **The walk table and the separator table overlap.** `walkCases` rows "past a barline", "past a breath mark" and "none when the walk reaches a key change" (`dom/TieWalkTest.java:105–108`, `:117–118`) are rows of `testATieWalksPastEveryNonDurationTypeThatIsALegalSeparator`, which takes every non-duration type from `ElementType.values()`.
- **The walk's null rows assert one direction only.** A null row asserts that the anchor has no tie. No row asserts that the note past the blocker has no incoming tie, so `endingOn`'s "no line before that" clause and its null result have no case.
- `Line.ownedTies()` **promises "the half entering from the left edge first".** The one fixture (`dom/TieWalkTest.java:171–183`) has no line that holds both an entering half and a tie of its own, so the order clause has no case.
- `Tie.reach` **covers 8 of the 16 mutation kinds.** The rows build `ElementInsertion`, `ElementDeletion`, `ElementReplacement`, `ElementModification`, `TieAddition`, `TieRemoval`, `SpanAddition` (a beam) and `LineInsertion`. `SpanRemoval`, a non-beam `SpanAddition`, `LineDeletion`, `LineKeyChange`, `LineLayoutChange`, `MetadataChange`, `FontChange`, `LayoutChange` and `LyricsChange` have no row, and nothing fails when a mutation kind is added.
- **Four** `TieRuleTest` **rows test what** `TieRule` **does not do.** "deleting the end note…" (`:174–175`) and the three silent rows of `crossLineCases` (`:274–282`) test `Line.untieEndingIn`, which `Line.removeRange` (`dom/Line.java:502`) and `Song.removeLine` (`dom/Song.java:706`) call. No other test reaches `untieEndingIn`. "converting the anchor to a grace note…" (`:165–168`) tests `StaffElement.convertedFrom`.
- `testOneUndoRestoresTheEditAndTheTieItRemoved` **tests a promise of every rule.** `EndingSectionRuleTest`, `SongStartRuleTest` and `HairpinTest` each pin the same promise for their own rule. `ModificationSessionTest` holds the tests of the gate on every rule, and the register gives that class to row 12.
- `TieRule.apply` **judges the line before an inserted line.** No row inserts a line, so that part of the "line that `batch` touched" clause has no case.
- **I found no input for which the** `LineDeletion` **arm of** `TieRule.touchedLine` **(**`dom/TieRule.java:81`**) changes anything.** A stored tie from the last note of the line before the deleted line ends on the deleted line's first non-separator, because a tie whose walk finds nothing there is broken, and no bracket closes with a broken tie stored. `Song.removeLine` unties that tie through `untieEndingIn` before the rule runs. `Song.removeLine`'s Javadoc (`dom/Song.java:694–696`) says the rule judges "a tied last note of the line before whose walk found no note in the removed line"; by the argument above, no such stored tie exists. This is not confirmed by a run.
- `Beam.spanning` **reaches the tie check only through** `holdsTiedPair` **(**`dom/Beam.java`**).** A rest in the range is refused as unbeamable before the tie check, so of the two cases the record names (a walk that reaches a rest or a grace note), only the grace note can reach the tie check.
- `TieLoadPass.run` **promises that it changes no element to make a pending tie hold.** No row asserts it.

## The table

_Kind_: **A** a real algorithm · **I** an invariant spanning several calls · **C** behavior with a known-correct corpus.

### `dom/TieWalkTest`

| Test | Kind | Disposition | Why |
|---|---|---|---|
| A tie's end is the first element past the legal separators when that element is a note (`walkCases`) | A | rewrite | Call `Tie.startingOn` and `Tie.endingOn`, where the contract is, on fixtures with no `~`. Every row asserts both directions: a held row asserts `endingOn(end)` equals the tie; a null row gains a nullable *far note* (the note past the blocker) and asserts `endingOn(farNote)` is null. |
| — rows "past a barline", "past a breath mark", "none when the walk reaches a key change" | — | discard | Duplicates of rows of the separator table. |
| A tie walks past every non-duration type that is a legal separator, and stops at the others | C | rewrite | Same change of route as the walk table, and the same two-direction assertion. The domain stays `ElementType.values()`. The corpus is the separator rule confirmed at step 5. |
| A tie is owned once by each line that draws it | I | rewrite | The fixture's second line gains a tie of its own, so the assertion also covers the entering half coming first. |
| An edit at a line's edge reaches the line beside it (`reachCases`) | A | keep | Each row is a clause of `reach`'s contract. |
| — `reachCases` cover every mutation kind | A | add | Rows for the nine missing kinds (above), and a test that asserts the table's mutation classes are exactly the leaves of `Mutation`. See item 3. |

### `Tie.judge` — new table

The rows come from `judge`'s contract: two notes hold when they sit at one staff position and sound one alteration; duration, written accidental, stem and beam play no part; `where` is the anchor's line and the line of the element the walk reaches. Where the table goes is item 1.

| Test | Kind | Disposition | Why |
| --- | --- | --- | --- |
| Two bare notes at one staff position hold | A   | add | The base case of the judgment. |
| Two notes at one staff position with other durations and other stems hold | A   | add | The contract says duration and stem play no part. |
| An anchor that sounds another alteration is not held | A   | add | Moved from `TieRuleTest` "a sharp on the anchor removes it". |
| An end that sounds another alteration is not held | A   | add | Moved from `TieRuleTest` "a sharp on the end removes it". |
| An end at another staff position is not held | A   | add | Moved from `TieRuleTest` "replacing the end with a note at another pitch". |
| E♯ to F, one pitch at two staff positions, is not held | A   | add | The defect step 1 found; no test asserts it. |
| A walk that reaches a rest gives no following note, on the anchor's line | A   | add | Moved from `TieLoadPassTest` "a tie with no note to end on". |
| A walk that reaches a grace note gives no following note | A   | add | Moved from `TieRuleTest` "a grace note inserted between them". |
| A walk that runs out of song gives no following note, on the anchor's line only | A   | add | The one row whose `where` names one line through `ChangeLines.on`. |
| Across a line break, an end at another pitch is not held, naming both lines | A   | add | Moved from `TieLoadPassTest`. |
| Across a line break, an end at the anchor's pitch holds | A   | add | Moved from `TieLoadPassTest`. |

### `Tie.arcSign` — new table

| Test | Kind | Disposition | Why |
| --- | --- | --- | --- |
| A held tie's side follows the stems, then the staff position | C   | add | Eleven rows: the nine stem pairs (up, down, none on each note), with the no-stem pair at a position above, on, and below the middle line. Each row asserts `arcSign()` and `isAbove()`. A coverage test asserts that the rows hold all nine stem pairs. The corpus is the arc-side rule confirmed at step 5, and the test's Javadoc names it. |

### `dom/TieRuleTest`

| Test | Kind | Disposition | Why |
|---|---|---|---|
| A tie stays only while its notes share a pitch (`editCases`) | I | rewrite | The rows become one per kind of edit that changes what the rule judges, and the judgment's own varieties move to the `judge` table. The test is renamed for the rule's promise: an edit's broken ties are removed and reported, on the line edited. |
| — "moving one note to another pitch removes it" | I | keep | A modification. |
| — "moving both notes to one new pitch keeps it" | I | keep | Two mutations in one batch; the rule judges the end state. |
| — "a same-pitch note inserted between them keeps it, ending on that note" | I | keep | The rule keeps a tie whose end moved. |
| — "a note at another pitch inserted between them removes it" | I | keep | An insertion. |
| — "replacing the end with a rest removes it, with no note following" | I | keep | A replacement. |
| — "a sharp on the anchor", "a sharp on the end", "a grace note inserted", "replacing the end with a note at another pitch" | — | discard here | They move to the `judge` table. |
| — "converting the anchor to a grace note leaves nothing tied and reports nothing" | — | discard here | A case of `StaffElement.convertedFrom`. See item 5. |
| — "deleting the end note unties the anchor and reports nothing" | — | discard here | A case of `Line.untieEndingIn`. See item 4. |
| A tied note inserted inside a beam keeps its tie, and the beam goes | I | keep | `apply`'s beam clause, with its report. |
| One undo restores the edit and the tie the rule removed | — | discard here | A promise of every rule. See item 6. |
| An edit on the next line is judged for the tie crossing into it (`crossLineCases`) | I | rewrite | The row "moving the next line's first note to another pitch" stays. The three silent deletion rows go to item 4. A row is added: inserting a line after the anchor's line, whose first note is at another pitch, removes the tie across the first and the inserted line. That row is the case of the "line insertion" part of the touched-line clause. |

### `dom/TieLoadPassTest`

| Test | Kind | Disposition | Why |
| --- | --- | --- | --- |
| A pending tie is added only while it holds, and every other one is reported (`tieCases`) | I   | keep | Rows "holds", "on a rest is damaged", "to another pitch is not held", "damaged and broken in reader order", "held beside removed". |
| — rows "no note to end on", "across a line break to another pitch", "across a line break at its pitch" | —   | discard | They test `judge`'s reasons and `where`, which the `judge` table holds. `run` promises only that it reports the entry `judge` gives, and the not-held row shows that. |
| — the pass changes no element to make a pending tie hold | I   | add | See item 7. |

### `dom/BeamTest`

| Test | Kind | Disposition | Why |
| --- | --- | --- | --- |
| A range whose tied note's walk reaches a grace note inside the range is beamed | A   | add | A row of `acceptedTiedRanges`, for the step 4 fix. `TiedRangeCase` gains the line's elements, because the row needs a grace note in the line. See item 8. |

## What needs deciding

1. **Where the** `judge` **and** `arcSign` **tables go, and what** `TieWalkTest` **becomes.** Both are tables of `Tie`'s own algorithms, and `TieWalkTest` already holds two more (the walk and `reach`).
  
  - **a.** Rename `TieWalkTest` to `TieTest`, mirroring `Tie`. It holds the walk, the separators, `judge`, `reach` and `arcSign`. `testATieIsOwnedOnceByEachLineThatDrawsIt` moves to `LineTest`, because it derives from `Line.ownedTies()`.
  - **b.** Keep `TieWalkTest` for the walk and `ownedTies`, and add `TieJudgeTest` and `TieArcTest`. `reach` stays in `TieWalkTest`, whose name then does not name it.
  
  `Line` is row 6, which is ✅. With **a**, decide whether the `ownedTies` move happens now or goes to the register.
2. **The split of the judgment out of** `TieRuleTest` **and** `TieLoadPassTest`**.** Confirm that the judgment's varieties go to the `judge` table and that each caller's table keeps only what that caller adds (for the rule: which lines, which edits, the removal, the report, the beam; for the load: damaged, reader order, added or dropped). The alternative keeps the rows where they are and adds the E♯-to-F row to `editCases`; then the judgment stays tested in two callers and not at `judge`, which the toggle also calls.
3. **Enumerating** `Tie.reach` **over every mutation kind.** The switch in `reachOrigin` is exhaustive, so the compiler forces a decision for a new kind; the coverage test forces a _tested_ decision. Confirm the nine added rows and the coverage test over the leaves of `Mutation`, or tell me to leave `reach` at its present rows.
4. **Where the deletion's untie is tested.** The four rows (one in `editCases`, three in `crossLineCases`) become one table, "a deletion that takes a tie's end unties the anchor and reports nothing", with rows for `Line.removeRange` within a line, `Line.removeRange` of the next line's first note, and `Song.removeLine` of the end's line with a note at another pitch and at the anchor's pitch sliding up. The last row is the one where a retarget would hold, so it stays. Kind **I**.
  
  - **a.** In `LineDeletionTest`. Its class Javadoc today is about `deleteRange`'s lyrics "as against the `removeRange` primitive beneath it", so it widens to name the untie.
  - **b.** In `LineTest`, next to the other `removeRange` behavior.
  
  `Song.removeLine` is row 12's. Decide whether its two rows go in now or to the register for row 12.
5. **The grace-note conversion row.** `StaffElement.convertedFrom` keeps `NoteElement`'s invariant that only a note that can carry a tie is tied. Findings 1–2 recorded the structure that makes that state unrepresentable: tie state only on a type that can carry a tie (row 5, ✅). Decide:
  
  - **a.** Move the row to a test of `convertedFrom`, for as long as the invariant is a code fact and not a type fact.
  - **b.** Put the structural fix on the register for row 5, and discard the row now. Until that fix lands, nothing asserts the invariant.
6. **The undo test.** One undo restoring a rule's removal is a promise of `ModificationSession`, not of `TieRule`. Decide whether to move it now into `ModificationSessionTest` (row 12's), with the tie rule as its probe as the other three tests there already use, or to put it on the register for row 12. Either way, the same promise pinned in `EndingSectionRuleTest`, `SongStartRuleTest` and `HairpinTest` goes on the register for row 12 as duplicates to triage.
7. `TieLoadPass.run`**'s no-repair clause.** I propose that the not-held row also asserts that both notes keep their staff positions and alterations. The only failure it catches is a repair added to `run`. Confirm, or tell me the clause needs no case.
8. **The** `BeamTest` **row.** I did not find which route asks `Beam.spanning` while a stored tie's walk reaches a grace note. Inside one bracket it can happen before the tie rule runs, if an edit inserts the grace note and the same bracket then asks for a beam. Step 4, item 4 fixed the behavior. If no route reaches the state, the row tests an input no caller supplies and does not belong. Tell me which route you had in mind, or whether the row goes.
9. **The** `LineDeletion` **arm of** `TieRule.touchedLine`**.** If my argument above is right, no case can show that arm doing anything, so it gets no test, and it is a production finding: the arm, the "for a line deletion" clause of `apply`'s contract, and the sentence in `Song.removeLine`'s Javadoc all go. The axis is step 3's, which is closed. Decide whether I record it in the record's _Findings raised_ for step 11, act on it now, or whether you know of a case I missed.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: You decide
    by: user
    at: "2026-11-09T17:47:50.670Z"
    scope: document
