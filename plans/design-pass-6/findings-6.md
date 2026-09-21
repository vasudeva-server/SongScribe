# Design Pass 6 — Step 6: Test triage

**State:** reviewed. The gate is resolved; *Resolved at the gate* records each decision, and *Execution* is what is left to do. A new session starts at *Execution* step 1.

Kinds, from `design.md` *The testing floor*: **A** real algorithm, **I** invariant spanning several calls, **C** known-correct corpus. An empty *Kind* is a discard.

Classes triaged: the record's three (`LineTest`, `LineDeletionTest`, `StrandedKeyChangeTest`); the three this pass added (`StaffElementRunTest`, `LineSpansTest`, `BreathMarkLoadPassTest`); and `KeyChangeActionTest`, because step 5 changed `KeyChangeAction.acceptsInsertionIndex`. Then the promises the record carries to step 6: `check-findings.md` #23 and the step 5 behavior changes.

## The table

Every row is approved as it stands. Line numbers are those of the tree at `5676df1b`.

| # | Test | Kind | Disposition | Why |
|---|---|---|---|---|
| 1 | `LineTest:54`, `:69` — a non-pitched run strips the predecessor's glissando and leaves its fall | A | rewrite | Two methods, one repair, one field (`getSlide`). One table: every `Slide` value, derived from `Slide.values()`, crossed with the three edits that change a note's follower — a non-pitched run inserted after it, a pitched run inserted after it (the glissando re-targets and stays), and a `deleteRange` of its follower. The deletion strip has no test today. The expected slide comes from `Slide`'s own Javadoc: `GLISSANDO` has a target, `FALL` has none. |
| 2 | `LineTest:85` — a pitched run inside a grace-host pair remakes the melisma | — | discard | No production path inserts at the slot in front of a paired grace note's host, and `insertRun`'s contract now states that none may (*Resolved* 1). |
| 3 | `LineTest:138`, row "a run of notes interrupts the word" | — | discard | Duplicate. `LyricRunTest.interruptingElements` asserts the same thing for a note inserted through the same `insertRun`. |
| 4 | `LineTest:138`, row "a run mixing a transparent element with a note interrupts the word" | A | keep | The case that tells a whole-run judgement from an element-by-element one. |
| 5 | `LineTest:138`, row "a run carrying no syllable slot leaves the word whole" | A | keep | `LyricRunTest` covers one transparent element; this is a run of them. |
| 6 | `LineTest:206` — `canInsertElementAt` over the six slots of `pairedLine` | A | keep | Rows at index 4 and 5 state the step 5 decision: the slot in front of a paired grace note is open, and the slot in front of its host is not. |
| 7 | `LineTest:212` — the slot past the last element is accepted | A | keep | The append slot. It cannot join the shared table, because `canBearSyllableAt` throws there. |
| 8 | `LineTest:222` — `canBearSyllableAt` over the six elements | A | keep | The grace-pairing half of the rule in `docs/lyrics.md`. |
| 9 | `LineDeletionTest:138` — naming either half of a key-change pair, or a note alone | A | rewrite | The widening belongs to `effectiveRange`, not to `deleteRange`: the test calls `effectiveRange` and then `deleteRange`, so its name states a promise `deleteRange` does not make. The three rows move to row 21's table. |
| 10 | `LineDeletionTest:150` — `deleteRanges` works last to first | I | rewrite | The case is right and it can fail: in ascending order the second range goes past the end of the line. Its `as` text says "both pairs surviving intact", but the assertion is that both pairs are gone. Only the message changes. |
| 11 | *new* — deleting a paired grace note alone keeps its host, and the syllable goes with the grace note | A | add | The checkpoint settled it: a syllable goes with its note, so the host is left an ordinary note with no lyric. It is a row of the `LineDeletionTest` lyric table, beside the rows for the word and melisma promises. |
| 12 | `StrandedKeyChangeTest:238` — `strandedKeyChangeIndices`, six rows | A | keep | Step 7 decides whether the method stays public. If it becomes private, the rows move to `redundantKeyChangeRanges` and assert each result's `range()`. |
| 13 | `StrandedKeyChangeTest:246`, `:298`, `:307` — the key a run leaves off in | A | rewrite | Three methods, eight rows, four cases. `keyAtEndUnder(int, Key)` is `lastKeyChangeKeyFrom` with the running key as the default, and `keyAtEndUnder(Key)` passes `FIRST_LEGAL_KEY_CHANGE_INDEX`. One table on `keyAtEndUnder(int, Key)`: the last of several key changes decides; a key change at `fromIndex` counts; one below `fromIndex` is ignored; with none, the running key. The running key differs from every key in the run, so a missed key change is visible. The whole-run overload is a one-line delegation and gets no row. |
| 14 | `StrandedKeyChangeTest:317` — the range takes the barline, for three bar kinds | — | discard | The expected value is computed by `line.effectiveRange`, the same code the result comes from, so a wrong widening passes. The pairing it checks is `effectiveRange`'s, and moves to row 21 with every bar kind. Rows 15 and 16 already assert that a stranded key change's range includes its barline. |
| 15 | `StrandedKeyChangeTest:330` — ranges are measured against the key passed in | A | rewrite | The case is right. The expected range is computed by `line.effectiveRange`; it becomes `.range()` compared with a literal `ElementRange`. |
| 16 | `StrandedKeyChangeTest:347` — the bounded form counts only from its index | A | rewrite | Same fault and same fix as row 15. |
| 17 | `StaffElementRunTest:115` — the nearest non-grace neighbor | A | keep | |
| 18 | `StaffElementRunTest:154` — `nonGraceRange` | A | keep | |
| 19 | `StaffElementRunTest:183` — `canInsertRunAt` | A | keep | |
| 20 | *new* — `beginIncludingKeyChangeBarline` reaches back over a key change's barline, and not over a host's grace note | A | add | Its contract states the asymmetry against `effectiveBegin`: a copy takes the barline and leaves the grace note. Its one caller is `Fragment.capture`. Two rows beside row 21, on the same fixture. |
| 21 | *new* — `effectiveRange`, the pairing rule | A | add | One table in `StaffElementRunTest`: a host reaches back over its paired grace note; a key change reaches back over its barline; a barline reaches forward over the key change behind it; an end reaches forward over a breath mark; a note alone widens nothing; and a key change behind each bar kind, with a coverage assertion against every `ElementType` whose `isBar()` is true (`StaffElementFactory` has `singleBarline`, `doubleBarline`, `finalDoubleBarline`, `repeatLeft`, `repeatRight`, `repeatLeftRight`). It takes rows 9 and 14. The class Javadoc names three pairs, but no type holds that list, so the table does not claim the pairs are complete. |
| 22 | `LineSpansTest:79`, `:92`, `:112` — what a new span does to the spans of its kind | A | rewrite | The case is right for the two kinds it has. The class Javadoc says "a kind absorbs whatever it overlaps", and that is false: `Tuplet` and `Trill` remove what they overlap (`LineSpans.removeOverlapping`, no test), and `Tie` and `Ending` coexist. One table over every concrete span kind: adding a span that overlaps, and one flush against, a span of its own kind leaves the two merged, only the new one, or both. Coverage is asserted by walking `Span.class.getPermittedSubclasses()` to the leaves, which *Execution* step 1 makes possible. A kind whose two spans cannot overlap by construction gets the flush row only; its leaf still counts for coverage. This replaces the `Absorb` coverage test, because the beam and hairpin rows reach both `Absorb` values. |
| 23 | *new* — `Span.removeFrom` on a span not in the registry changes nothing and throws nothing | I | add | The contract makes the no-op a promise, so that a sweep, a merge and paste reconciliation can remove unconditionally. Arranged as one span removed twice. |
| 24 | *new* — `Span.addTo` does not apply its displacement policy twice across undo and redo | I | add | The form is in *Resolved* 3. |
| 25 | *new* — `removesConfirmableSpan` agrees with the sweep | I | add | For each of the four `ElementChange` shapes (sealed; coverage asserted from `getPermittedSubclasses`), on a line holding an ending and a span that does not ask for a confirmation: `removesConfirmableSpan()` is true exactly when applying the change removes the ending. `Ending` is the only kind whose `requiresInvalidationConfirm` is true. |
| 26 | *new* — the projection agrees with itself | I | add | For each of the four shapes (coverage as in row 25): for every element on the line, `projectedIndexOf` is null exactly when the change removes it, and otherwise `projectedElements().get(projectedIndexOf(e))` is `e`; the inserted or new element stands at `index`; the size is the line's count less what goes plus what comes. No test references `ElementChange` or `ProjectedElements` today. |
| 27 | *new* — `Line.keyAt` | A | add | The inclusive bound, the insertion index `elementCount()`, and the running key when no key change stands at or before the index. Eight production callers, no test. |
| 28 | *new* — `Line.keyAtEndOfLineAfterRemoving` | A | add | A key change surviving after the range decides; otherwise the key in effect before the range; a range from index 0 gives the running key. Step 3 changed its parameter to `EffectiveRange`; no test. |
| 29 | *new* — `Line.addElement(StaffElement)` lands in front of the auto-maintained terminal | I | add | On the last line of a real `Song`, several appends leave the terminal last. Fan-in about 22 files; `detachedLine` has no terminal, so no current test reaches the case. |
| 30 | `KeyChangeActionTest:60`, `:66` — which indices take a key change | A | rewrite | Both methods call `assertAcceptance` with one record, so they are one table. Step 5 added a refusal that no row states: the index in front of a breath mark. It becomes a row. This file is register row 24's (`ui/action`), not the target's. |
| 31 | `TerminalMaintainer`'s three checks (`check-findings.md` #23) | — | no case | Guards: each throws on a request the terminal policy forbids, and no caller makes one (*Resolved* 4). |
| 32 | `ElementRange`'s `begin <= end` throw (`check-findings.md` #23) | — | no case | A guard. `check-findings.md` #23 found that every construction site derives the pair in order. |
| 33 | `AcceptedInsertion.land`, `landWithoutRepairs` | — | no case | `land` assembles `insertRun`'s repair sequence by hand (the record's *Findings raised*, still open), and both end in `EditModeManager.previewElementDidChange`, which is UI state. The repairs are tested through `insertRun`. |
| 34 | *new, manual* — note entry: a click with a grace-note preview inserts nothing | — | add a check | UI: `PreviewElementInserter.handleClick` takes a `LineComponent`. |
| 35 | *new, manual* — grace mode: a press in front of a paired grace note starts grace entry, and a host click completes a new pair | — | add a check | UI. The model half, that the slot is open, is row 6. |
| 36 | *new, manual* — grace mode: the slot between a paired grace note and its host offers no insertion | — | add a check | UI. The model half is row 6. |
| 37 | *new, manual* — grace mode: a host the line has no room for ends the interaction, removes the grace note, and leaves no undo step | — | add a check | UI (`GraceModeManager.enterGraceNotePaired`). |
| 38 | `BreathMarkLoadPassTest:52` — the load pass drops every breath mark not after a duration element, keeps the first of a consecutive pair, reports each removal at its closed-up index, and records nothing | A | keep | One arrangement reaches all four promises its class Javadoc states, and each assertion can fail. |

## Test classes not triaged here

- `KeyChangeStrandingTest`, `InheritedKeyPropagationTest`: they call `effectiveRange` and `deleteRange` only to arrange a song. Their subject is key propagation across lines, which pass 0 settled.
- `MusicXmlCrossLineSpanTest`: this pass added it, but its subject is the MusicXML reader. It goes to the register's *Carry-forward findings*, tagged to row 16 (*Execution* step 6).

## Resolved at the gate

Each item states the decision; the user's words are quoted where they decided it.

**1. No insertion lands between a paired grace note and its host.** The user: *"Should be an invariant in insertRun's contract."* Every route that inserts already refuses that slot — note entry and paste through `InsertionPointMode.updateTarget` (`InsertionPointMode.java:536`) and `canInsertRunAt`, grace mode on the press (`GraceModeManager.java:359`) and at the host (`PreviewElementInserter.java:211`), paste over a selection because `deleteRange` strips the glissando of the note in front of the range first, and the key-change dialog because `KeyChangeAction` accepted the slot. So:

- `EditableRun.insertRun` gets an `@invariant`: `index` satisfies `canInsertElementAt`, so no insertion lands between a paired grace note and its host.
- The branch in `LyricRun.repairNeighborsAfterInsertion(int, List)` that remakes the melisma (`LyricRun.java:370–378`: the comment and the `isPairedGraceNote(firstInsertedIndex - 1)` block) is dead and goes, with the clause of its Javadoc that names it (`:334–335`, "and the grace-host melisma of a pair the insertion landed inside"). `syncGraceHostMelisma` stays: grace mode calls it directly.
- Table row 2 is discarded.

`LyricRun` is register row 9's type; the edit crosses the target's files by the user's decision.

**2. A deletion's lyric promises go through a domain checkpoint now (route a).** The user asked whether "checkpoint" meant a test. It does not: it is a contract proposal in the four-part format of `.claude/skills/design-pass/reference/classification.md` — *Proposing*, *Today*, *Also defensible*, *Where it came from* — which the user accepts, corrects or rejects. The tests follow from the promises settled. The user chose route a, with this instruction: *"be careful. A contract must only state what callers need to know, they must not be an expression of the implementation in prose."*

So each proposal states what a caller of `deleteRange` sees in the lyrics after the deletion — what "hel-" becomes when the note after it goes, what happens to a melisma whose start, carrier or close goes, and where the syllable of a paired grace note deleted alone goes. It never names `adjustSyllablesForNeighborChange`, `adjustExtendsForDeletion`, `transferLyrics`, or the order they run in.

**3. Row 24 is a unit undo-and-redo round trip.** The user: *"ok"* to this form. `MutationReplayer.applyUndo` and `applyRedo` take a `ScoreView` and read only `getSong()` and `setFonts` from it; `Song.withReplay` is public; `UnitTest` installs a recording bus that receives each batch; `InheritedKeyPropagationTest` builds a real `Song`. The test adds a crescendo flush against another on a real song, takes the batch from the bus, undoes it in reverse, then redoes it inside `song.withModification(() -> song.withReplay(...))`, with a `ScoreView` stub that returns the song, and asserts one merged crescendo. `UndoController.performRedo` is the production form of the same bracket.

**4. The terminal checks are unreachable guards.** The review's premise was wrong. The user: *"The UI does not allow a final double bar line to be selected at all unless a final write-repeat is selected at the end of the song."* `FinalDoubleBarlineAction.admit` returns the element only when `Song.isTerminalOfItsSong(element)`. No mid-song insertion of a final double barline happens, and row 31 stays "no case".

**5. `Span` is sealed in this step.** The user: *"now."* It is register row 7's file.

**6. Scope.** The user: *"ok"* to triage `MusicXmlCrossLineSpanTest` under row 16, and *"don't carry it forward, do it now"* for `BreathMarkLoadPassTest` — table row 38.

## Execution

Read `.claude/rules/serena.md`, `~/.claude/guides/design.md`, `.claude/guides/contracts.md`, `.claude/guides/testing-common.md`, `.claude/guides/testing-unit.md` and `.claude/guides/testing-manual.md` first. The pass never commits. Snapshot at the end with the command in `.claude/skills/design-pass/SKILL.md`.

1. **Seal `Span`.** `Span.java:56` becomes `public abstract sealed class Span extends LineElement permits NoteSpan, Hairpin, Tuplet, Trill, Ending`. `Tuplet` (`Tuplet.java:45`), `Trill` (`Trill.java:32`) and `Ending` (`Ending.java:43`) are `public class` today and have no subclasses (`jet_brains_type_hierarchy`), so each becomes `final`. Check that no test subclasses or anonymously extends one of them.
2. **`insertRun`'s invariant and the dead branch** — *Resolved* 1. `insertRun`'s `@invariant` states the promise, not how callers keep it.
3. **The deletion checkpoint** — *Resolved* 2. Before writing it, read `EditableRun.deleteRange`, `LyricRun`'s deletion repairs, and `docs/lyrics.md`, so *Today* is accurate. Present it in the terminal, one proposal per promise, and stop for the user's answers. Then write the settled promises into `deleteRange`'s contract (the method callers call), and into `docs/lyrics.md` where they state a rule about chains rather than about one method. A rejected proposal is a finding, not a weaker contract.
4. **The tests**, by class:
   - `LineTest`: rows 1, 2, 3.
   - `LineDeletionTest`: rows 9, 10, 11, and one row per promise settled in step 3.
   - `StrandedKeyChangeTest`: rows 13–16.
   - `StaffElementRunTest`: rows 20, 21.
   - `LineSpansTest`: row 22.
   - New cases: rows 23 and 24 (`Span`), 25 and 26 (`ElementChange`), 27–29 (`Line`). Put each in the test class of the type whose contract it states; create `SpanTest` and `ElementChangeTest` where none exists. Row 29 needs a real `Song`, not `detachedLine`.
   - `KeyChangeActionTest`: row 30.

   Apply the sibling check in `testing-unit.md` before each `@Test`: a same-shape sibling makes it a row in a `record` case table. Every expected value comes from a contract, never from calling the code under test.
5. **Manual checks** — rows 34–37. Create `src/test/manual/songscribe/ui/edit/GraceMode.md` with an `Exercises:` header naming `GraceModeManager`, `PreviewElementInserter`, `AcceptedInsertion`, `PreviewElementManager`, and rows 35–37 as checks 1–3. Add row 34 to `src/test/manual/songscribe/ui/component/ScoreView.md` under a new `## Note entry` heading, numbered after that file's highest check, and add `PreviewElementInserter` to its header.
6. **Register.** Add a line to *Carry-forward findings* in `plans/design-pass-register.md`: `MusicXmlCrossLineSpanTest` was added by pass 6 and is untriaged; tagged to row 16.
7. **Compile and run.** `./scripts/compile.sh --test`, then `./scripts/test.sh` naming the changed and new classes, at most four per command.
8. **Record.** In `record.md`, step 6 becomes ✅ with gate ✅, and the notes name this file. Then snapshot.
