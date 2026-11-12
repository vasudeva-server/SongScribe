# Design Pass — `dom`: `Tie`, `TieLoadPass`, `TieRule`

Run by `/design-pass 8` — register row `8`, *Ties*.

**Status:** 🔄 in progress

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ✅ | with 2 | — | |
| 2 Class design | ✅ | ✅ | — | |
| 3 Unrepresentable states | ✅ | ✅ | — | |
| 4 Extraction | ✅ | ✅ | — | |
| 5 Contracts | ✅ | ✅ | — | the beam rule goes in no guide: `Beam.spanning` and `TieRule.apply` each state their half |
| 6 Test triage | ✅ | ✅ | — | |
| 7 Test-only surface | ✅ | ✅ | — | |
| 8a Suite gate | ✅ | n/a | — | |
| 8b Visual gate | ✅ | n/a | — | |
| 9 Coverage | ✅ | n/a | — | full unit suite: 1894 passed |
| 10 Mutation | ✅ | n/a | — | skipped |
| 11 Adversarial review | ⏳ | n/a | — | complete only when the user says it passed |
| 12 Close out | ⏳ | ⏳ | — | |

*Gate* is the state of the step's findings document: ⏳ not written · 📖 open for
review · ✅ resolved. A step whose *Gate* is not ✅ has made no code change, so a
resume that finds one 📖 reads the marked-up document rather than the tree.

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

*Notes* holds, while a step is in progress, where the step stands, so that a
resume can pick it up. Once the step is complete, it holds only what a later step
or a resume acts on, such as a decision a later step depends on or a count a
later gate compares against. Delete everything else from the cell when the step
completes, because the decisions are in the tree. A finding for a later step goes
in *Findings raised*, not here.

## Target

| | Before | After |
|---|---|---|
| Commit | `77e93179` | |
| Production | `src/main/java/songscribe/dom/Tie.java`, `TieLoadPass.java`, `TieRule.java` — 621 lines | |
| Tests | `src/test/java/songscribe/dom/TieLoadPassTest.java`, `TieRuleTest.java`, `TieWalkTest.java` — 731 lines | |
| Passing tests | 60 | |

The row named only `Tie`; at step 0 the user extended it to `TieLoadPass` and
`TieRule`, which no register row named.

Step 4 moved four members of the tie rules into `ElementType`, `Line` and `NoteElement` (see
*Findings raised*). The target is still the three files, and those four members go with it.

## Findings claimed

None. The register's *Carry-forward findings* held no item tagged for pass 8.

## Findings raised

Each item is listed under the step whose axis it is:

- **Step 3.** Every item raised for step 3 was resolved at step 3 (see its row).
- **Step 4.** Every item raised for step 4 was resolved at step 4 (see its row).
- **Steps 5 and 6.** Step 4 moved four members of the tie rules out of the three target files.
  Their Javadoc and the tests on them belong to this pass:
  - `ElementType.isLegalTieSeparator()` (`dom/ElementType.java`), from `Tie.isLegalSeparator`.
    `TieWalkTest`'s case "every non-duration type as a separator" tests it.
  - `Line.firstNonSeparatorIndexFrom(int)` and `Line.lastNonSeparatorIndexBefore(int)`
    (`dom/Line.java`), from the two `Tie` statics over an element list.
  - `NoteElement.canCarryTie()` (`dom/NoteElement.java`), from `Tie.canAnchor`.

  Step 4 also made `Tie.bothNotesIn(ElementRange)` and `Tie.beamHoldingBoth()` instance
  members, and gave `Tie.Judgment.Holds` the component `tie`. Their Javadoc is unchanged from
  the static forms; "the anchor need not be tied yet" is one clause step 5 judges again.
- **Step 5.** `holds()`'s time precondition (U4) is stated twice: in the Javadoc of
  `Tie.holds()` and of `Tie.judge(NoteElement)`.
- **Step 5.** `TieRule.apply` has no contract of its own, and all of `TieRuleTest` derives from
  it.
- **Step 5.** `Tie.arcSign`'s Javadoc names its readers ("the renderer, the skyline seeder and
  the MusicXML export"), and the algorithm has no full contract.
- **Step 5.** The Javadoc that moved at step 4 onto `ElementType.isLegalTieSeparator` still
  names its readers (`RangeQueries.canToggleTie`, the walk behind `NoteElement.tie()`).
- **Step 6.** Every item raised for step 6 was resolved at step 6 (see its row).
- **Step 11.** I found no input for which the `LineDeletion` arm of `TieRule.touchedLine`
  (`dom/TieRule.java:81`) changes anything. A stored tie from the last note of the line before
  a deleted line ends on the deleted line's first non-separator, and `Song.removeLine` unties it
  through `Line.untieEndingIn` before the rule runs. If that holds, the arm, the "for a line
  deletion" clause of `TieRule.apply`'s contract and the sentence about the tie rule in
  `Song.removeLine`'s Javadoc (`dom/Song.java:694–696`) all go. Not confirmed by a run.
- **Row for `ui/selection`, step 3.** `RangeQueries.canToggleTie` (`ui/selection/RangeQueries.java:153–155`)
  refuses a three-element selection whose middle element is not a legal tie separator. The
  `Tie.judge` end comparison at `:160` already refuses that selection, so the check is redundant.
- **Step 12 (docs).** `guides/subsystems/tie-placement.md` lists only `Tie` and `BezierBow`;
  `TieLoadPass` and `TieRule` are listed in `span-invalidation.md` and `undo.md` only.
- **Row 16.** Whether the MusicXML reader can give two pending ties on one anchor, which is the
  only producer for `Line.addTie`'s and `Line.removeTie`'s early returns.
