# Design Pass — `dom`: `Tie`, `TieLoadPass`, `TieRule`

Run by `/design-pass 8` — register row `8`, *Ties*.

**Status:** 🔄 in progress

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ✅ | with 2 | — | |
| 2 Class design | ✅ | ✅ | — | 1b, 2 now, 3a, 4 confirmed, 5, 6b (keep the kind check), 7 and 8 confirmed |
| 3 Unrepresentable states | ✅ | ✅ | — | items 1–6 as proposed (3: the throw is dead); 7 option b, `DAMAGED_ANCHOR` noted as the load's only |
| 4 Extraction | ⏳ | ⏳ | — | |
| 5 Contracts | ⏳ | ⏳ | — | |
| 6 Test triage | ⏳ | ⏳ | — | |
| 7 Test-only surface | ⏳ | ⏳ | — | |
| 8a Suite gate | ⏳ | n/a | — | |
| 8b Visual gate | ⏳ | n/a | — | |
| 9 Coverage | ⏳ | n/a | — | |
| 10 Mutation | ⏳ | n/a | — | opportunistic |
| 11 Adversarial review | ⏳ | n/a | — | complete only when the user says it passed |
| 12 Close out | ⏳ | ⏳ | — | |

*Gate* is the state of the step's findings document: ⏳ not written · 📖 open for
review · ✅ resolved. A step whose *Gate* is not ✅ has made no code change, so a
resume that finds one 📖 reads the marked-up document rather than the tree.

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

## Target

| | Before | After |
|---|---|---|
| Commit | `77e93179` | |
| Production | `src/main/java/songscribe/dom/Tie.java`, `TieLoadPass.java`, `TieRule.java` — 621 lines | |
| Tests | `src/test/java/songscribe/dom/TieLoadPassTest.java`, `TieRuleTest.java`, `TieWalkTest.java` — 731 lines | |
| Passing tests | 60 | |

The row named only `Tie`; at step 0 the user extended it to `TieLoadPass` and
`TieRule`, which no register row named.

## Findings claimed

None. The register's *Carry-forward findings* held no item tagged for pass 8.

## Findings raised

Raised at steps 1–2, each for the step whose axis it is:

- **Step 3.** Every item raised for step 3 was resolved at step 3 (see its row).
- **Step 4.** Placement of the walk's statics: `isLegalSeparator(ElementType)`,
  `firstNonSeparatorIndexFrom` and `lastNonSeparatorIndexBefore` over an element list,
  `endAfter` and `anchorBefore` over a line and an index.
- **Step 4.** `TieLoadPass.run` adds every tie and then removes the broken ones in a second
  loop, and calls `tieableAnchor` twice for each pending tie (`dom/TieLoadPass.java:51–71`).
  `Tie.judge` does not read whether the anchor is tied, so one loop can judge first and add
  only what holds.
- **Step 5.** `holds()`'s time precondition (U4) is stated twice: in the Javadoc of
  `Tie.holds()` and of `Tie.judge(NoteElement)`.
- **Step 5.** `TieRule.apply` has no contract of its own, and all of `TieRuleTest` derives from
  it.
- **Step 5.** `Tie.arcSign`'s Javadoc names its readers ("the renderer, the skyline seeder and
  the MusicXML export"), and the algorithm has no full contract.
- **Step 6.** The toggle's tie rule now goes through `Tie.judge(NoteElement)`; the
  enharmonic defect (E♯ to F was offered, then removed as not held) needs its test, and so do
  `Tie.startingOn` and `Tie.endingOn`.
- **Step 6.** `Tie.arcSign` / `tieArcDirection` is an algorithm with no test in the target.
- **Step 12 (docs).** `guides/subsystems/tie-placement.md` lists only `Tie` and `BezierBow`;
  `TieLoadPass` and `TieRule` are listed in `span-invalidation.md` and `undo.md` only.
- **Row 16.** Whether the MusicXML reader can give two pending ties on one anchor, which is the
  only producer for `Line.addTie`'s and `Line.removeTie`'s early returns.
