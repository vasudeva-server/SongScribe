### 1B. `Line`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| Line | `addElement(e)` inserts before auto-maintained terminal when present, else appends | unit | — | missing | write test: lands at `count-1` w/ terminal, `count` otherwise | ✅ |
| Line | `addElement(0, e)` migrates initial TempoChangeAttachment to new first element (line 0) | unit | — | missing | write test asserting migration | ✅ |
| Line | `addElement(i, e)` rejects FINAL_DOUBLE_BARLINE on non-last line / non-end index | unit | `LineMutationTest.TerminalGuards` (3) | adequate | keep | — |
| Line | `addElement(i, e)` removes tuplet spanning insertion index | unit | — | missing | write test | ✅ |
| Line | `addElement(i, e)` removes Endings invalidated by insertion | unit | `LineMutationTest.EndingInvalidationConditions` | adequate | keep | — |
| Line | `removeElement(i)` fires `ElementDeletion`, shrinks list | unit | `LineMutationTest.RemoveElement.testFiresSingleElementDeletion` | adequate | keep | — |
| Line | `removeElement(i)` removes range elements invalidated by anchor/end deletion | unit | `LineMutationTest.RemoveElement` (2) | adequate | keep | — |
| Line | `removeElement(i)` rejects auto-maintained terminal removal | unit | `LineMutationTest.TerminalGuards.testRemoveFinalBarlineOnLastLineThrows` | adequate | keep | — |
| Line | `removeRange(a,b)` fires `ElementRangeDeletion` w/ correct indices/list | unit | `LineMutationTest.RemoveRange` (2) | adequate | keep | — |
| Line | `removeRange(a,b)` removes range elements invalidated by range deletion | unit | `LineMutationTest.RemoveRange` (2) | adequate | keep | — |
| Line | `removeRange(a,b)` rejects range including auto-maintained terminal | unit | `LineMutationTest.TerminalGuards.testRemoveRangeIncluding...` | adequate | keep | — |
| Line | `setElement(i, e)` fires `ElementReplacement` | unit | — | missing | write test asserting single replacement w/ correct old/new | ✅ |
| Line | `setElement` updates surviving range elements' anchor/end refs after swap | unit | — | missing | write test: tie anchored at 0; `setElement(0,new)`; assert `tie.anchor==new` | ✅ |
| Line | `setElement` guard: FINAL_DOUBLE_BARLINE on non-last line/position | unit | `LineMutationTest.TerminalGuards` (2) | adequate | keep | — |
| Line | `setElement` removes Endings invalidated by replacement | unit | `LineMutationTest.EndingInvalidationConditions.*SetElement*` (6) | adequate | keep | — |
| Line | `modifyElement` clones before mutation (pre-snapshot for `ElementModification.beforeElement`) | unit | — | missing | write test asserting pre-mutation snapshot | ✅ |
| Line | `modifyElement` w/ DURATION_AFFECTING field removes overlapping tuplets | unit | — | missing | write test | ✅ |
| Line | `effectiveElementCount()` excludes trailing auto-maintained terminal | unit | (only e2e helper, never asserted) | missing | write unit test comparing `elementCount` vs `effectiveElementCount` | ✅ |
| Line | `isInHairpinRange(i)` — inside/outside/boundary of cresc/dim | unit | `LineIsInHairpinRangeTest` (5) | adequate | keep | — |
| Line | `addBeaming` — no overlap: added as-is | unit | `BeamToggleTest.ToggleBeam.testToggleBeamOn` (via MusicEditOperations) | adequate | keep | — |
| Line | `addBeaming` — adjacent/overlapping merge (absorb shared endpoint) | unit | — | missing | write test: [0,2]+[2,4]→[0,4] | ✅ |
| Line | `addBeaming` — subsumed beam removed when new span covers it | unit | — | missing | write test: [1,3]+[0,4]→[0,4] | ✅ |
| Line | `removeBeaming` — absent beam is no-op | unit | — | missing | write test | ✅ |
| Line | `isStartOfAnyBeam`/`isEndOfAnyBeam` | unit | — | missing | write tests | ✅ |
| Line | `findBeamAt(i)` — beam in range else null | unit | `BeamToggleTest`/`BatchMutationTest` | adequate | keep | — |
| Line | `findBeamsOverlapping(a,b)` | unit | — | missing | write test | ✅ |
| Line | `addTie` merge: adjacent/overlapping ties merge | unit | — | missing | write test: [0,1]+[1,2]→[0,2] | ⬜ |
| Line | `findTieAt(i)` | unit | `TieToggleTest.testTieCreationAndRemoval` | adequate | keep | — |
| Line | `findTies()` | unit | — | missing | write test | ⬜ |
| Line | `addTuplet`/`removeTuplet` fire `TupletAddition`/`TupletRemoval` | unit | `MusicEditOperationsMutationTest.testToggleTuplet*` | adequate | keep | — |
| Line | `findTupletAt(i)` | unit | `SelectionCoordinatorValidateSpansTest` (setup only) | inadequate | write dedicated return-value test | ⬜ |
| Line | `findTupletsOverlapping(a,b)` | unit | — | missing | write test | ⬜ |
| Line | `removeOverlappingTuplets(a,b)` — one `TupletRemoval` per tuplet | unit | — | missing | write test | ⬜ |
| Line | `addCrescendo`/`addDiminuendo` — same-type hairpin merge | unit | `DynamicsMarkingTest` (e2e, non-merging) | wrong-level | write unit test for merge; e2e smoke fine to keep | ⬜ |
| Line | `isInsideGraceHostPair(i)` | unit | — | missing | write test | ⬜ |
| Line | `isPairedGraceNote(i)` — grace w/ CONNECTED glissando | unit | (indirect via isHostOfPairedGraceNote) | missing | write direct test | ⬜ |
| Line | `precedingGraceNoteIndex(i)` | unit | — | missing | write test | ⬜ |
| Line | `isHostOfPairedGraceNote(i)` | unit | `LineGraceNotePairingTest` (5) | adequate | keep | — |
| Line | `keyExists(pitchType)` | unit | — | missing | write test (flats/sharps, various counts) | ⬜ |
| Line | `setKeyAccidentalCount` fires `LineKeyChange`; no-op when unchanged | unit | — | missing | write test | ⬜ |
| Line | `setKeyType` fires `LineKeyChange` | unit | `LayoutEngineTest` (setup only) | inadequate | write test asserting mutation | ⬜ |
| Line | `attachInitialTempoIfNeeded()` | unit | — | missing | write test | ⬜ |
| Line | `changeElementSpacingRatio(f)` fires `LineLayoutChange` w/ accumulated ratio | unit | — | missing | write test | ⬜ |
| Line | legacy Y-pos setters (`setTempoChangeYPosPx`/`setBeatChangeYPosPx`/`setLyricsYPosSs`/`setFirstSecondEndingYPosPx`/`setTrillYPosPx`) | none | — | none | trivial setters | — |
| Line | `adjustSyllablesForNeighborChange` — insertion breaks BEGIN/MIDDLE chain; deletion preserves | unit | `LineMutationTest.SyllableAdjustment` (8) | adequate | keep | — |
| Line | `adjustSyllablesForSuccessorAfterInsertion` — MIDDLE→BEGIN, END→SINGLE | unit | `LineMutationTest.SyllableAdjustmentOnInsertion` (6) | adequate | keep | — |
| Line | `adjustExtendsForDeletion` — START cascade-clear, CONTINUE heal, STOP promote | unit | `LineMutationTest.ExtendAdjustment` (9) | adequate | keep | — |
| Line | `adjustExtendsForInsertion` — START→NONE, CONTINUE→STOP, STOP/NONE no-op | unit | `LineMutationTest.ExtendAdjustmentOnInsertion` (6) | adequate | keep | — |
| Line | `backfillSyllabic()` — normalize chain after legacy load (idempotent) | unit | — | missing | write test on stale markers | ⬜ |
| Line | `setSyllableBoundary(...)` — derive syllabic + propagate to next | unit | — | missing | write test | ⬜ |
| Line | `adjustNeighborsForLyricDeletion(...)` | unit | — | missing | write test | ⬜ |
| Line | `deriveSyllabic(prev, this)` — pure 4-quadrant truth table | unit | `SyllabicDerivationTest` (implicit via callers) + `LineMutationTest.SyllableAdjustment` | adequate | keep (4-case fn well-exercised by callers) | — |
| Line | `applyChange` — throws ISE outside bracket when tracking active | unit | `LineMutationTest.LineConstructorInvariants.testApplyChangeThrowsWhenNotInBracket` | adequate | keep | — |
| Line | `applyChange` — runs mutator directly when tracking suspended | unit | — | missing | write test via `withoutMutationTracking` | ⬜ |
| Line | `hasEndingInvalidatedByDeletion`/`...ByInsertion` pre-flight checks | unit | — | missing | write tests | ⬜ |
| Line | `findRangeElementsAt(i)` | unit | — | missing | write test | ⬜ |
| Line | `findRangeElements(Class)` | unit | `BatchMutationTest` | adequate | keep | — |
| Line | `getFirstTempoChange()` — 0 for line 0; first index otherwise | unit | — | missing | write test | ⬜ |
| Line | `getFirstBeatChange()`/`getFirstTrill()`/`isAnnotation()` | unit | — | missing | write test each | ⬜ |
| Line | parentLine propagation on add (`setLine`/`setParentLine`) | unit | `ParentLinePropagationTest` (3) | adequate | keep | — |
| Line | `addElement[0]` fires `ElementInsertion` | unit | — | missing | write test | ⬜ |

**1B notes (quality concerns):** Beam/tie **merge logic** — the most complex code in `addBeaming`/`addTie` — is entirely untested (`BeamToggleTest`/`TieToggleTest` only build non-overlapping 2-element spans and test through `MusicEditOperations`). `SyllabicDerivationTest` is a **name-mismatch**: it tests `StaffElement.getLyricForVerse`/`Lyric` field storage, not `Line` derivation; harmless but misnamed and arguably misfiled. `LineGraceNotePairingTest` covers only `isHostOfPairedGraceNote`; its three companion predicates are untested. `addCrescendo`/`addDiminuendo` merge is covered only by a non-overlapping e2e smoke (`DynamicsMarkingTest`) — wrong level for the merge logic.

