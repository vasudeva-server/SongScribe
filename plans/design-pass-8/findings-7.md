# Design pass 8 — step 7: fix test-only surface

Target: `dom`: `Tie`, `TieLoadPass`, `TieRule`, as step 6 left them (stash `c2184983`), with the members step 4 moved to `ElementType`, `Line` and `NoteElement`. Paths are under `src/main/java/songscribe/`.

## What it found

No test-only surface. Each of the four kinds was checked:

- **Genuinely test-only.** Every non-private member of the three files has a production caller:
  - `Tie.startingOn` — `NoteElement.tie`, `Tie.endingOn`, `ui/selection/RangeQueries`.
  - `Tie.endingOn` — `NoteElement.incomingTie`, `RangeQueries`.
  - `Tie.judge(NoteElement)` — `TieRule`, `TieLoadPass`, `RangeQueries.canToggleTie`.
  - `Tie.reach` — `ui/component/ScoreViewController`.
  - `Tie.beamHoldingBoth` — `TieRule`, `RangeQueries`.
  - `Tie.bothNotesIn` (package-private) — `Beam.holdsTiedPair`, `Tie.beamHoldingBoth`.
  - `Tie.holds` (package-private) — `Tie.judge`.
  - `Tie.arcSign` — `layout/LayoutEngine.tieLayout`, `layout/stacking/NoteAttachedStacker.seedTieBounds`; `Tie.isAbove` — `io/musicxml/ScorePartwiseBuilder.addTie`.
  - `Tie.anchor`, `Tie.end`, `equals`, `hashCode` — many production readers; `NoteElement.isTiedTo` compares with `equals`.
  - `Tie.Judgment.Broken.entry` — `TieRule`, `TieLoadPass`.
  - `TieLoadPass.run` — `SongLoad`; `TieRule.apply` — the modification rules.
  - The moved members `ElementType.isLegalTieSeparator`, `Line.firstNonSeparatorIndexFrom`, `Line.lastNonSeparatorIndexBefore` and `NoteElement.canCarryTie` each have production callers (the walk, `Line.untieEndingIn`, `Line.enteringTie`, `RangeQueries.boundaryTieAt`, `Line.addTie`, `TieLoadPass`).
  - `Tie.toString` has no explicit caller. It is the `Object` override that string concatenation and `Line.TiePlacement.toString` reach, not surface added for a test.
- **A replaceable process-global.** The three files hold no static mutable state, no setter and no probe.
- **Misnamed internal API.** None: no member is a test hook in the shape of production code.
- **Lifecycle.** None of the three classes has an `initialize()` or registers with anything process-global.

The tests reach no production internals by reflection: no test in `src/test/java/songscribe/dom/` calls `setAccessible`, `getDeclaredField` or `getDeclaredMethod`. The fixtures step 6 wrote arrange state only through public API. The one non-obvious call, `Song.withoutMutationTracking` in the line-insertion row of `TieRuleTest`, has a production caller (`ui/edit/GraceModeManager`).

## What needs deciding

Nothing. Confirm that step 7 closes with no change.
