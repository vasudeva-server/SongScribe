### 6B. selection data holders + `ClipboardManager`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| `ElementSelection` | pure data record (line, begin, end) — no logic | none | — | none | no test needed; pure carrier | — |
| `TupletToggleInfo` | compact-record guard: throws when `coversExisting=true` and `existing=null` | unit | none | missing | add unit test asserting the `IllegalArgumentException` | ✅ |
| `TupletToggleInfo` | `canToggle`, `existing`, `coversExisting` fields accessible as record components | none | — | none | pure data accessors; no logic | — |
| `LineSelectionState` | `clearSelection()` resets all five fields and fires callback | unit | none | missing | add unit test with callback capture and field assertions | ✅ |
| `LineSelectionState` | `setLineSelected(true)` clears `selectedGlissandoElementIndex` and fires callback | unit | none | missing | add unit test asserting glissando index reset | ✅ |
| `LineSelectionState` | `setLineSelected(false)` sets `lineSelected=false` and fires callback | unit | none | missing | add unit test | ✅ |
| `LineSelectionState` | `selectGlissando()` clears element + line selection, sets glissando index, fires callback | unit | `NoteConnectionTest.GlissandoSelection.testClickSelectGlissando` (e2e) | wrong-level | add unit test; e2e is also acceptable as an integration check but the logic can be exercised unit | ✅ |
| `LineSelectionState` | `isGlissandoSelected(index)` returns true iff index matches stored glissando index | unit | none | missing | add unit test | ✅ |
| `LineSelectionState` | `hasGlissandoSelection()` returns true iff `selectedGlissandoElementIndex != -1` | unit | `NoteConnectionTest` (e2e, asserts against real LSS) | wrong-level | add unit test | ✅ |
| `LineSelectionState` | `hasElementSelection()` returns false when no selection, true after click-select | unit | `LineSelectionStateTest.testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing` (indirect) | adequate | — | — |
| `LineSelectionState` | `isElementSelected(index)` correctly bounds-checks with inclusive range | unit | `NoteConnectionTest.testSelectSourceNote` / `testSelectTargetNote` (e2e) | wrong-level | add unit test; selection range logic is pure state | ⬜ |
| `LineSelectionState` | `getSelectionSize()` returns 0 when no selection, N=(end-begin+1) otherwise | unit | none (only mocked indirectly in other tests) | missing | add unit test | ⬜ |
| `LineSelectionState` | `getSelection()` returns `null` when no element and no line selection | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `getSelection()` returns `ElementSelection` spanning full line when `lineSelected=true` | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `getSelection()` returns `ElementSelection` from element range when element-selected | unit | used via mock in `ApplyToSelectionInterceptTest` / `DynamicMarkingActionTest` | inadequate | tests only stub the return on a mock coordinator; add unit test on LSS directly | ⬜ |
| `LineSelectionState` | `getSingleSelectedElement()` returns null when >1 selected, element when exactly 1 | unit | `SelectionCoordinatorLyricSelectionTest.testElementSelectionClearsLyricSelection` (indirect, asserts coordinator wrapper) | adequate | — | — |
| `LineSelectionState` | `setSelectionFromClick()` sets begin=end=anchor=index, clears glissando, fires callback | unit | used as setup in `LineSelectionStateTest` and `ToggleConflictTest` but never directly asserted | inadequate | add unit test asserting all five side effects | ⬜ |
| `LineSelectionState` | `extendSelectionTo()` with anchor < index: sets begin=anchor, end=index | unit | used as setup in `LineSelectionStateTest` but never directly asserted on its own | inadequate | add unit test asserting correct [begin,end] ordering in both directions | ⬜ |
| `LineSelectionState` | `extendSelectionTo()` with anchor > index (reversed drag): begin=index, end=anchor | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `extendSelectionTo()` no-op when anchor is -1 | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `extendSelection()` starts new selection when begin=-1 | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `extendSelection()` extends end when selection exists (begin unchanged) | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `resetElementSelection()` sets begin=end=-1 (does not touch lineSelected/glissando), fires callback | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `setSelectionAnchor()` / `getSelectionAnchor()` round-trip | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `selectAll()` excludes auto-maintained terminal (FINAL_DOUBLE_BARLINE) | unit | `LineSelectionStateTest.testSelectAllExcludesAutoMaintainedFinalBarlineOnLastLine` | adequate | — | — |
| `LineSelectionState` | `selectAll()` excludes REPEAT_RIGHT terminal on last line | unit | `LineSelectionStateTest.testSelectAllExcludesAutoMaintainedRightRepeatTerminalOnLastLine` | adequate | — | — |
| `LineSelectionState` | `selectAll()` on empty line (only terminal) selects nothing | unit | `LineSelectionStateTest.testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing` | adequate | — | — |
| `LineSelectionState` | `selectAll()` on non-last line includes all elements | unit | `LineSelectionStateTest.testSelectAllOnNonLastLineIncludesAllElements` | adequate | — | — |
| `LineSelectionState` | `selectionChangeCallback` fires on every state-mutating call | unit | none | missing | add unit test with a counter callback | ⬜ |
| `LineSelectionState` | `canToggleBeaming()` — size < 2 returns false | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` (indirectly; quarter notes are not beamable so size check and beamable check overlap) | adequate | — | — |
| `LineSelectionState` | `canToggleBeaming()` — non-beamable element in range returns false | unit | `ToggleConflictTest.testChangeDurationToQuarterDisablesBeam` | adequate | — | — |
| `LineSelectionState` | `canToggleBeaming()` — all beamable, no existing beam (add mode) | unit | `ToggleConflictTest.testChangeDurationToEighthBothEnabled` | adequate | — | — |
| `LineSelectionState` | `canToggleBeaming()` — existing beam covering selection (remove mode) | unit | `ToggleConflictTest.testToggleTieOnDisablesBeam` / `testToggleTieOffReenablesBeam` | adequate | — | — |
| `LineSelectionState` | `canToggleBeaming()` — blocked when would connect same span as existing tie | unit | `ToggleConflictTest.testToggleTieOnDisablesBeam` | adequate | — | — |
| `LineSelectionState` | `canToggleTie()` — size != 2 returns false and sets canTie=false | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` (size=2 path) + lacks explicit size!=2 assertion | inadequate | add explicit test for size < 2 and size > 2 setting canTie=false | ⬜ |
| `LineSelectionState` | `canToggleTie()` — non-pitched element in range returns false | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canToggleTie()` — pitch mismatch returns false | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canToggleTie()` — elements in different ties returns false | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canToggleTie()` — two same-pitch notes with no tie (add mode): canTie=true, existingTie=null | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` + `testToggleTieOnDisablesBeam` | adequate | — | — |
| `LineSelectionState` | `canToggleTie()` — two notes sharing existing tie (remove mode): canTie=true, existingTie set | unit | `ToggleConflictTest.testToggleTieOffReenablesBeam` (uses getExistingTie) | adequate | — | — |
| `LineSelectionState` | `canToggleTie()` — blocked when would connect same span as existing beam | unit | `ToggleConflictTest.testToggleBeamOnDisablesTie` | adequate | — | — |
| `LineSelectionState` | `resetTieState()` clears canTie and existingTie | unit | `ToggleConflictTest.toggleTie()` calls resetTieState but doesn't assert fields directly | inadequate | add unit test asserting `getCanTie()` and `getExistingTie()` return null after reset | ⬜ |
| `LineSelectionState` | `canToggleTuplet()` — size < 2 returns (false, null, false) | unit | `LineSelectionStateTest.testEmptySelectionCannotToggleTuplet` | adequate | — | — |
| `LineSelectionState` | `canToggleTuplet()` — non-pitched element returns (false, null, false) | unit | `LineSelectionStateTest.testSelectionContainingNonPitchedElementCannotToggle` | adequate | — | — |
| `LineSelectionState` | `canToggleTuplet()` — two pitched notes, no tuplet: (true, null, false) | unit | `LineSelectionStateTest.testTwoPitchedNotesNoTupletCanToggle` | adequate | — | — |
| `LineSelectionState` | `canToggleTuplet()` — selection spans two different tuplets: (false, null, false) | unit | `LineSelectionStateTest.testSelectionSpanningTwoDifferentTupletsCannotToggle` | adequate | — | — |
| `LineSelectionState` | `canToggleTuplet()` — full coverage of existing triplet: (true, tuplet, true) | unit | `LineSelectionStateTest.testFullCoverageOfTripletReportsCoversExisting` | adequate | — | — |
| `LineSelectionState` | `canToggleTuplet()` — partial coverage of existing tuplet: (true, tuplet, false) | unit | `LineSelectionStateTest.testPartialCoverageOfTripletDoesNotCoverExisting` | adequate | — | — |
| `LineSelectionState` | `canToggleTrill()` — returns false when no selection | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canToggleTrill()` — returns true when at least one pitched note in range | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canToggleTrill()` — returns false when selection contains only rests | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canFlipStemDirection()` — returns false when nothing selected | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canFlipStemDirection()` — returns true when at least one non-rest in range | unit | none | missing | add unit test | ⬜ |
| `LineSelectionState` | `canFlipStemDirection()` — returns false when selection is all rests | unit | none | missing | add unit test | ⬜ |
| `ClipboardManager` | `addElement()` normalizes FINAL_DOUBLE_BARLINE → DOUBLE_BARLINE | unit | `ClipboardManagerTest.AddElement.testFinalDoubleBarlineNormalizedToDoubleBarline` | adequate | — | — |
| `ClipboardManager` | `addElement()` passes non-FINAL_DOUBLE_BARLINE through unchanged | unit | `ClipboardManagerTest.AddElement.testDoubleBarlinePassedThrough` + `testNotePassedThrough` | adequate | — | — |
| `ClipboardManager` | `addElement()` does not mutate original song element | unit | `ClipboardManagerTest.testSongFinalBarlineUntouched` + `testSongRightRepeatTerminalUntouched` | adequate | — | — |
| `ClipboardManager` | `getSize()` returns correct count after adds | unit | none (only used via `getElement(0)` by index in tests) | missing | add unit test | ⬜ |
| `ClipboardManager` | `isEmpty()` returns true initially, false after add | unit | none | missing | add unit test | ⬜ |
| `ClipboardManager` | `getFirstElement()` / `getLastElement()` return correct elements from multi-element pasteboard | unit | none | missing | add unit test | ⬜ |
| `ClipboardManager` | `clear()` empties pasteboard and isEmpty() returns true | unit | none | missing | add unit test | ⬜ |

**6B notes (quality concerns):**

**Pure data holders:** `ElementSelection` is a pure 3-field record with no logic; correct verdict is `none` for all its components. `TupletToggleInfo` is 95% a data carrier but has a compact-constructor guard (throws on `coversExisting=true, existing=null`) — that one invariant is worth a unit test.

**Highest-risk missing tests:**

1. `LineSelectionState.clearSelection()` and `resetElementSelection()` are called constantly by the coordinator but their post-conditions (which fields are zeroed, callback fires) are never directly asserted. A stray bug there would silently corrupt selection state everywhere.
2. `extendSelectionTo()` reversed-drag branch (`anchor > elementIndex` → `begin=elementIndex, end=anchor`) is completely uncovered — the test setup always passes `anchor < end`.
3. `canToggleTie()` failure paths (non-pitched element, pitch mismatch, elements in different ties) have no direct unit coverage — the happy path is tested but none of the four early-return conditions are.
4. `canToggleTrill()` and `canFlipStemDirection()` have zero tests at any level.
5. `ClipboardManager` pasteboard accessors (`isEmpty`, `getSize`, `getFirstElement`, `getLastElement`, `clear`) are never asserted in tests; `ClipboardManagerTest` exercises only `addElement` normalization.

**Weak tests noted:**

- `testEmptySelectionCannotToggleTuplet` in `LineSelectionStateTest`: the name says "empty selection" but the state object was never given a selection — `selectionBegin` defaults to -1. The test is correct but tests the default-state condition, not a deliberate "was selected, then cleared" scenario.
- The `fullCoverageOfTriplet` test uses `.extracting(tuplet -> tuplet != null ? tuplet.getGrade() : 0)` rather than a direct `.isEqualTo(expectedTuplet)` — the null guard in the lambda is superfluous since `isNotNull()` was already asserted; minor style noise, not a correctness risk.
- `ToggleConflictTest.testToggleTieOffReenablesBeam`: calls `state.canToggleTie()` internally via the `toggleTie()` helper in order to populate `existingTie`, which means `resetTieState()` semantics are exercised but never directly asserted on `getCanTie()` / `getExistingTie()`.

**e2e coverage classified wrong-level:** `hasGlissandoSelection`, `isElementSelected`, and `selectGlissando` side-effects are only tested through full Swing robot clicks in `NoteConnectionTest`. All three are pure in-memory state changes with no rendering dependency and should have unit tests.

