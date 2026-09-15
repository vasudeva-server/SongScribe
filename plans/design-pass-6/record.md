# Design Pass — `dom`: `Line`, `ElementChange`, `ProjectedElements`, `StaffElementRun`

Run by `/design-pass 6` — register row `6`, *Line*.

**Status:** 🔄 in progress

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ✅ | with 2 | — | `findings-1-2.md` |
| 2 Class design | 🔄 | 📖 | [class-design.md](./class-design.md) | |
| 3 Unrepresentable states | ⏳ | ⏳ | — | |
| 4 Extraction | ⏳ | ⏳ | — | |
| 5 Contracts | ⏳ | ⏳ | — | |
| 6 Test triage | ⏳ | ⏳ | — | |
| 7 Test-only surface | ⏳ | ⏳ | — | |
| 8a Suite gate | ⏳ | n/a | — | |
| 8b Visual gate | ⏳ | n/a | — | |
| 9 Diagrams | ⏳ | ⏳ | — | |
| 10 Coverage | ⏳ | n/a | — | |
| 11 Mutation | ⏳ | n/a | — | opportunistic |
| 12 Adversarial review | ⏳ | n/a | — | complete only when the user says it passed |
| 13 Close out | ⏳ | ⏳ | — | |

*Gate* is the state of the step's findings document: ⏳ not written · 📖 open for
review · ✅ resolved. A step whose *Gate* is not ✅ has made no code change, so a
resume that finds one 📖 reads the marked-up document rather than the tree.

*Plan* links the step's execution plan once it has one, and is where a resume
picks up — take the first phase that plan's dashboard does not mark ✅. `—` means
the step is small enough to hold in one sitting.

## Target

Production: `src/main/java/songscribe/dom/Line.java`, `ElementChange.java`,
`ProjectedElements.java`, `StaffElementRun.java`.
Tests: `src/test/java/songscribe/dom/LineTest.java`, `LineDeletionTest.java`,
`StrandedKeyChangeTest.java` (exercises `StaffElementRun`'s key queries).

## Baseline

| | Before |
|---|---|
| Commit | `13075b18` |
| Production lines | 2766 (Line 2055, ElementChange 209, ProjectedElements 69, StaffElementRun 433) |
| Test lines | 751 (LineTest 230, LineDeletionTest 162, StrandedKeyChangeTest 359) |
| Passing tests | 68 (`LineTest` + `LineDeletionTest` 47, `StrandedKeyChangeTest` 21) |

## Findings claimed

Carry-forward items taken from the register at step 0, and what each turned out
to be. Deleted from the register when claimed.

- `StaffElementRun.getElement` is contracted as *"The element at `index`."* — no
  range, no `@return`, no `@throws`. `Line`'s implementation is a bare
  `elements.get(index)`, so out of bounds throws by inheritance from the field's
  type rather than by promise, and any caller guarding on `elementCount()` is
  guessing.

## Findings raised

Anything surfaced that was not this target's to fix, and anything a step turned
up that belongs to a later step's axis — the latter is picked up by that step's
findings document rather than argued where it was found. Harvested to the
register at step 13, before this file is deleted.
