`plans/ui-dialog-interface.md` is written, uncommitted. 11 phases.

## How it's shaped

**Phase 1 prototypes on `KeyChangeDialog`, not `FontDialog`.** The plan's canonical "small example" is `FontDialog`, but the inventory shows it's pure wiring with zero domain reach — it can't exercise a validate/apply interface. `KeyChangeDialog` is 98 lines and exhibits the whole problem: `getData()` reaches `requireScoreView()` and `getSong().getLine(...)`, `setData()` posts via `postWithModification`. Phase 1 ends at a **checkpoint** — eleven classes get rewritten against that shape, so it's agreed before rollout starts.

**I resolved one open design question in the plan** rather than leaving it to be discovered. §5.2 states the free function as `apply(Song, Input)` — but if the dialog calls that, the dialog needs the `Song`, which defeats the decoupling. The resolution: the free function keeps that shape, and the **caller that opens the dialog** binds the song, handing the dialog a back end that already holds it. Both statements stay true and no domain type reaches the dialog. It's flagged as a task, not buried.

**The real coupling turned out to be inherited, not per-dialog.** `BaseDialog` supplies `getSong()`, `getScoreView()` and `requireScoreView()` to every subclass — which is *why* nothing forces the record boundary today. Phase 2 removes them, and the compiler then enumerates the work for Phases 3–5. Phases 3, 4, 5 and 6 all block only on 2 and touch disjoint files, so they parallelize.

**Phase 8 has a prediction that's worth watching.** `SongSettingsDialogTest` is the biggest test file (889 lines, 45 tests) and its own class doc says it deliberately extends `UnitTest` rather than `MainFrameMockTest` — meaning it already tests pure logic. I've called it the likeliest survivor, with an instruction to say so if that's wrong, because it changes the phase's shape. Similarly the three `BaseDialog*` test files (52 tests) are marked **keep** — they cover the blocking counter, geometry and tab selection, which Phase 2 deliberately doesn't touch.

## Two things I corrected from the inventory

The sweep reported no AssertJ-Swing "anywhere in the tree" — but it only searched `ui/dialog`. There are 14 e2e files, 11 using it, including an existing `DialogsTest.java`. Phase 9 extends that rather than building a harness.

And the final verification says plainly that these numbers measure architectural correction, not a contract pass, with an instruction not to present either as the other — the distinction that D10 turns on.
