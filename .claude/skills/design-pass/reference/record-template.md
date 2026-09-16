## The record template

`plans/design-pass-<pass>/record.md`, created at step 0:

```markdown
# Design Pass — `<target>`

Run by `/design-pass <row number>` — register row `<n>`, *<System>*.

**Status:** ⏳ not started · 🔄 in progress · ✅ complete

| Step | Status | Gate | Plan | Notes |
|---|---|---|---|---|
| 1 Inventory | ⏳ | with 2 | — | |
| 2 Class design | ⏳ | ⏳ | — | |
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

## Findings claimed

Carry-forward items taken from the register at step 0, and what each turned out
to be. Deleted from the register when claimed.

## Findings raised

Anything surfaced that was not this target's to fix, and anything a step turned
up that belongs to a later step's axis — the latter is picked up by that step's
findings document rather than argued where it was found. Harvested to the
register at step 13, before this file is deleted.
```
