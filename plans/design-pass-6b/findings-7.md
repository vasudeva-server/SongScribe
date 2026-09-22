# Findings — step 7, pass 6b, _Accidentals_

Paths are under `src/main/java/songscribe/dom/` unless stated otherwise.

## What it found

Every public and package-private member of the target's eight production
files was checked against `jet_brains_find_referencing_symbols` for a caller
outside `src/test/java/`:

| Member | Production caller |
|---|---|
| `ChainEdit.modified` | `ChainEdit.ofModification` |
| `ChainEdit.removed` | `ChainEdit.of`, `GraceModeManager.abort` |
| `ChainEdit.inserted` | `ChainEdit.of`, `GraceModeManager.enterGraceNote` |
| `ChainEdit.replaced` | `ChainEdit.of`, `ProjectedEdit.walk` |
| `ChainEdit.lineRekeyed` | `ChainEdit.of` |
| `ChainEdit.rekeyingFrom` | `ChainEdit.lineRekeyed`, `AccidentalChain.walkSong` |
| `ChainEdit.lineInserted` | `ChainEdit.of` |
| `ChainEdit.lineRemoved` | `ChainEdit.of` |
| `ChainEdit.of` | `Song.applyChange` |
| `ChainEdit.end` | `Cursor.plus` (via `start.plus(length)`) |
| `AccidentalChain.walk(Song, ChainEdit)` | `Song.applyChange`, `GraceModeManager` |
| `AccidentalChain.walk(AccidentalScanView, ChainEdit)` | `AccidentalChain.walk(Song, ChainEdit)`, `AccidentalChain.walkSong`, `ProjectedEdit.walk` |
| `AccidentalScanView.Live` | `AccidentalChain.walk`, `AccidentalChain.walkSong` |
| `AccidentalScanView.lineCount`, `elementCount`, `ownKey`, `elementAt` | `AccidentalChain` (six call sites, per `findings-3.md`) |
| `ProjectedEdit.inserting` | `PreviewElementInserter.walkAndCalculateInsertion`, `ScoreViewController.tryInsertFragment` |
| `ProjectedEdit.replacing` | `PreviewElementInserter.replacementFits`, `ScoreViewController.tryInsertFragment`, `SelectionActionApplier.fitsWithChainsWalked` |
| `ProjectedEdit.walk` | all three call sites above |
| `Cursor.next`, `Cursor.plus` | `AccidentalChain.Walk` (`run`, `firstUnskippedAtOrAfter`), `ChainEdit.end` |
| `ChainedAccidental.isWritten` | `NoteElement.drawnAccidental` |

Every member checked has at least one caller in `src/main/java/`, so none
exists solely to be called from a test. `Song.withoutMutationTracking`, which
every rewritten test in step 6 uses to build a song without triggering the
walk, sits outside the target — it is genuinely process-wide too:
`TupletLoadPass`, `MigrationPipeline` and `GraceModeManager` all call it in
production.

No setter, probe, or widened field exists anywhere in the target for a test
to swap process-wide state, and no class in the target has an
`initialize()`-without-a-way-back lifecycle.

## What needs deciding

Nothing. No finding of any of the four kinds step 7 fixes exists in this
target.
