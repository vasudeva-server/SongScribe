## 6. `ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard` (audited 2026-05-21)

Audited via four parallel production-first sub-audits in a single wave: **6A** `SelectionCoordinator` (the 998-LOC selection engine, cross-checked against all 19 referencing test files); **6B** selection data holders + clipboard (`LineSelectionState`, `ElementSelection`, `TupletToggleInfo`, `ClipboardManager`); **6C** `ui/edit` (`GraceModeManager`, `EditModeManager`, `ScoreActions`); **6D** `ui/adjustment` (`VerticalAdjustment`, `HorizontalAdjustment`, `Adjustment`). Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e. 11 production classes (the matrix's "15" counts the 4 `package-info.java` files); 259 behavior rows.

### 6A. `SelectionCoordinator`

| class | behavior | required level | existing test | verdict | action |
|-------|----------|---------------|---------------|---------|--------|
| SelectionCoordinator | registerLineState sets the selection-change callback on the state | unit | none | missing | add unit test: verify callback is wired so that when state fires its selection-change callback, lyricSelection is cleared |
| SelectionCoordinator | unregisterLineState removes state from registry | unit | none | missing | add unit test: register, unregister, assert getLineState returns null |
| SelectionCoordinator | clearLineStates clears all states and resets activeLineIndex to -1 | unit | none | missing | add unit test: register two states, clearLineStates, assert both gone and getActiveLineIndex == -1 |
| SelectionCoordinator | getLineState returns state for registered index, null for unregistered | unit | SelectionCoordinatorLyricSelectionTest (uses getLineState(0)) | adequate | — |
| SelectionCoordinator | getActiveLineIndex returns -1 when no line active, correct index when active | unit | none directly; implicitly exercised by many tests via clearSelection postconditions | inadequate | add explicit test for getActiveLineIndex == -1 before activation and correct index after |
| SelectionCoordinator | activateLine sets activeLineIndex and clears previous line's selection | unit | none directly | missing | add unit test with two registered lines: activate line 0, select some elements, activate line 1, verify line 0 selection is cleared and activeLineIndex == 1 |
| SelectionCoordinator | activateLine clears lyric selection | unit | none directly (selectLyric tests show clearSelection clears lyric; activateLine path not tested) | missing | add unit test: selectLyric, then activateLine, assert hasLyricSelection is false |
| SelectionCoordinator | clearSelection clears active line's element selection and resets activeLineIndex to -1 | unit | DeselectTest.testClearSelectionRemovesAllSelectedElements, testClearSelectionOnSingleElement, testClearSelectionWhenNothingSelected | adequate | — |
| SelectionCoordinator | clearSelection clears lyric selection | unit | SelectionCoordinatorLyricSelectionTest.testSelectLyricClearsElementSelection (reverse direction); no direct test of clearSelection → clears lyric | missing | add test: selectLyric, then clearSelection, assert hasLyricSelection false |
| SelectionCoordinator | selectLyric clears element selection, sets activeLineIndex, and sets lyric selection record | unit | SelectionCoordinatorLyricSelectionTest.testSelectLyricClearsElementSelection | adequate | — |
| SelectionCoordinator | selectLyric on unknown line (not registered) returns -1 activeLineIndex | unit | none | missing | edge case: selectLyric on element whose line is not registered — findLineIndex returns -1; should be documented/tested |
| SelectionCoordinator | clearLyricSelection nulls out lyricSelection | unit | implicit in testSelectLyricClearsElementSelection and testElementSelectionClearsLyricSelection | adequate | — |
| SelectionCoordinator | getLyricSelection returns the selection record | unit | SelectionCoordinatorLyricSelectionTest (asserts getLyricSelection equals expected record) | adequate | — |
| SelectionCoordinator | hasLyricSelection returns false when null, true when set | unit | SelectionCoordinatorLyricSelectionTest | adequate | — |
| SelectionCoordinator | elementSelectionFromClick clears lyricSelection via callback | unit | SelectionCoordinatorLyricSelectionTest.testElementSelectionClearsLyricSelection | adequate | — |
| SelectionCoordinator | isInSelectMode / setInSelectMode getter-setter pair | unit | none | none | trivial boolean field with no branching logic; not worth testing |
| SelectionCoordinator | isElementSelected: returns false when lineIndex != activeLineIndex | unit | none | missing | add test: register two lines, activate line 0, select element on line 0; verify isElementSelected returns false for line 1 |
| SelectionCoordinator | isElementSelected: returns false when state has no element selection | unit | none | missing | add test: activate line with no selection, assert isElementSelected false |
| SelectionCoordinator | isElementSelected: delegates to state when lineIndex matches and state has selection | unit | none direct; indirectly used in e2e SelectionTest via scoreView().isElementSelected | inadequate | add direct unit test |
| SelectionCoordinator | isLineSelected: returns false when lineIndex != activeLineIndex | unit | none | missing | add test similar to isElementSelected cross-line case |
| SelectionCoordinator | isLineSelected: delegates to state.isLineSelected for active line | unit | none unit (e2e SelectionTest.testClickInStaffHeaderSelectsLine covers end-to-end) | wrong-level | add unit test: mock/set up a state that returns isLineSelected=true, verify coordinator.isLineSelected returns true for that index |
| SelectionCoordinator | isGlissandoSelected: returns false for wrong line, delegates to state for correct line | unit | none | missing | add unit test for cross-line guard and delegation |
| SelectionCoordinator | isLyricSelected: returns false when activeLineIndex != lineIndex or no lyric selection | unit | none | missing | add unit tests for both early-return paths |
| SelectionCoordinator | isLyricSelected: returns true only when element reference matches and verse matches | unit | none | missing | add test: selectLyric(e, 2), assert isLyricSelected(e, 2, …) true; false for wrong verse; false for different element |
| SelectionCoordinator | hasGlissandoSelection delegates to active state's hasGlissandoSelection | unit | GlissandoReflectionTest (exercises via selectGlissando + triggerReflection flow) | adequate | — |
| SelectionCoordinator | canDeleteLine: false when no active line | unit | none | missing | add unit test |
| SelectionCoordinator | canDeleteLine: false when active line is not a line-selection (isLineSelected false) | unit | none | missing | add unit test |
| SelectionCoordinator | canDeleteLine: false when line is selected but song has only one line | unit | none | missing | add unit test |
| SelectionCoordinator | canDeleteLine: true when line selected and song has > 1 line | unit | none | missing | add unit test |
| SelectionCoordinator | canChangeTempo: false when no active selection | unit | none | missing | add unit test |
| SelectionCoordinator | canChangeTempo: false when active selection but no single selected element | unit | none | missing | add unit test (multi-element selection) |
| SelectionCoordinator | canChangeTempo: true when exactly one element is selected | unit | none | missing | add unit test |
| SelectionCoordinator | getSelectionSize: 0 when no active selection, delegates to state otherwise | unit | DeselectTest (verifies size 3 → 0) | adequate | — |
| SelectionCoordinator | getSelection: null when no active line or state, delegates to state otherwise | unit | SelectionApplyIntegrationTest (uses getSelection in assertions) | adequate | — |
| SelectionCoordinator | getSingleSelectedElement: null when no active selection, delegates to state otherwise | unit | SelectionCoordinatorLyricSelectionTest.testElementSelectionClearsLyricSelection | adequate | — |
| SelectionCoordinator | getSelectedLine: returns activeLineIndex when that line has a line-selection, -1 otherwise | unit | none | missing | add unit test |
| SelectionCoordinator | getSelectedElements: returns empty list when no selection | unit | none explicit | missing | add test |
| SelectionCoordinator | getSelectedElements: returns correct elements for selection range | unit | none explicit; behavior tested implicitly via applyActionToSelection results | inadequate | add direct test asserting element identity/order |
| SelectionCoordinator | hasActiveSelection: false when no element selection, true when selection exists | unit | SelectionContentTest.testNoSelectionReturnsNoActiveSelection, testWithSelectionReturnsActiveSelection | adequate | — |
| SelectionCoordinator | selectionHasDurations: false when no selection | unit | SelectionContentTest.testNoSelectionHasDurationsReturnsFalse | adequate | — |
| SelectionCoordinator | selectionHasDurations: true when selection contains at least one duration element | unit | SelectionContentTest.testSelectionWithMixedContentHasDurations, testSelectionWithOnlyDurationsHasDurations | adequate | — |
| SelectionCoordinator | selectionHasDurations: false when selection contains only non-durations | unit | SelectionContentTest.testSelectionWithOnlyNonDurationsHasNoDurations | adequate | — |
| SelectionCoordinator | selectionHasRests: false when no selection | unit | none | missing | add test: no selection → selectionHasRests returns false |
| SelectionCoordinator | selectionHasRests: true when selection contains at least one rest | unit | none | missing | add test with a rest in selection |
| SelectionCoordinator | selectionHasRests: false when selection contains only notes (no rests) | unit | none | missing | add test: note-only selection → selectionHasRests false |
| SelectionCoordinator | isApplicableToSelection: false when no selection | unit | SelectionContentTest.testNoSelectionIsNotApplicable | adequate | — |
| SelectionCoordinator | isApplicableToSelection: true when any element in selection matches action | unit | SelectionContentTest.testApplicableActionWithApplicableNotesReturnsTrue, testApplicableActionWithMixedNotesReturnsTrue | adequate | — |
| SelectionCoordinator | isApplicableToSelection: false when no element in selection matches action | unit | SelectionContentTest.testActionNotApplicableToBarlines, testApplicableActionWithNoApplicableNotesReturnsFalse | adequate | — |
| SelectionCoordinator | applicability cache is invalidated when selection changes | unit | none | missing | add test: select range A, check applicability (cached), change selection to range B, check applicability reflects new range — verifies invalidation logic |
| SelectionCoordinator | content cache (hasDurations/hasRests) is invalidated after applyActionToSelection | unit | none explicit | missing | add test: select notes, check selectionHasDurations true, apply duration change that converts notes to rests, check selectionHasRests becomes true after invalidation |
| SelectionCoordinator | applyActionToSelection: no-op when no active selection | unit | none | missing | add test: call applyActionToSelection with no selection, assert no exception and no mutations |
| SelectionCoordinator | applyActionToSelection wraps all mutations in a single modification bracket | unit | ApplyActionToSelectionMutationTest + BatchMutationTest (verify mutations emitted) | adequate | — |
| SelectionCoordinator | applyActionToSelection skips inapplicable elements (ElementReplaceable path) | unit | ApplyActionToSelectionMutationTest.testElementReplaceableSkipsInapplicableElements, BatchMutationTest.testAccidentalSkipsRests | adequate | — |
| SelectionCoordinator | applyActionToSelection with selected=false skips ElementReplaceable actions (continue guard) | unit | SelectionApplyIntegrationTest.testApplyThenRemoveAttribute (tests ElementModifiable; the ElementReplaceable `selected=false → continue` guard is not separately tested) | missing | add test: apply ElementReplaceable with selected=false, assert no elements changed |
| SelectionCoordinator | applyActionToSelection applies ElementReplaceable (duration change, barline change) correctly | unit | ApplyActionToSelectionMutationTest, SelectionApplyIntegrationTest | adequate | — |
| SelectionCoordinator | applyActionToSelection applies ElementModifiable (accidental, fermata, dot, staccato) correctly | unit | BatchMutationTest, SelectionApplyIntegrationTest | adequate | — |
| SelectionCoordinator | applyActionToSelection with EndingEffect.Invalidate: skips replacement when user declines confirm | unit | EndingConfirmsTest (covers the confirm-invalidation path) | adequate | — |
| SelectionCoordinator | applyActionToSelection with EndingEffect.CompensateEnd: applies compensating change when confirmed | unit | EndingConfirmsTest | adequate | — |
| SelectionCoordinator | applyActionToSelection with EndingEffect.CompensateSplit: applies compensating change when confirmed | unit | EndingConfirmsTest | adequate | — |
| SelectionCoordinator | repairBeamings: beam unchanged when all elements remain beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamUnchangedWhenAllElementsRemainBeamable, BatchMutationTest.testBeamDissolvedWhenAllNonBeamable | adequate | — |
| SelectionCoordinator | repairBeamings: beam trimmed from left when start becomes non-beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamTrimmedWhenStartElementBecomesNonBeamable, BatchMutationTest.testBeamShrunkFromStart | adequate | — |
| SelectionCoordinator | repairBeamings: beam trimmed from right when end becomes non-beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamTrimmedWhenEndElementBecomesNonBeamable, BatchMutationTest.testBeamShrunkFromEnd | adequate | — |
| SelectionCoordinator | repairBeamings: beam killed when interior element becomes non-beamable | unit | SelectionCoordinatorValidateSpansTest.testBeamKilledWhenInteriorElementBecomesNonBeamable | adequate | — |
| SelectionCoordinator | repairBeamings: beam killed when trimmed span has fewer than two elements | unit | SelectionCoordinatorValidateSpansTest.testBeamKilledWhenTrimLeavesFewerThanTwoElements, BatchMutationTest.testBeamDissolvedWhenSubgroupTooSmall | adequate | — |
| SelectionCoordinator | validateSpans removes overlapping tuplets | unit | SelectionCoordinatorValidateSpansTest, BatchMutationTest tuplet tests | adequate | — |
| SelectionCoordinator | saveActionStates saves selected+enabled for all managed actions; does nothing if already saved | unit | ReflectionHandlerTest.testNewSelectionSavesState, testSaveRestoreRoundTripPreservesBothSelectedAndEnabled; idempotency tested indirectly by testChangedSelectionDoesNotResave | adequate | — |
| SelectionCoordinator | restoreActionStates restores saved selected+enabled and clears saved map | unit | ReflectionHandlerTest.testClearSelectionRestoresEnabledState, testClearSelectionRestoresSavedState, testSaveRestoreRoundTripPreservesBothSelectedAndEnabled | adequate | — |
| SelectionCoordinator | restoreActionStates is no-op when nothing saved | unit | ReflectionHandlerTest.testNoSelectionNoSavedStateIsNoOp | adequate | — |
| SelectionCoordinator | clearSavedActionStates clears map without restoring | unit | none | missing | add test: save states, manually mutate action, clearSavedActionStates, verify action has mutated value (not restored), and map is empty |
| SelectionCoordinator | restoreActionStatesWithFlag: restores only actions with given flag; clears all saved states | unit | none | missing | add test: save states for two actions (one with DISABLE_IN_GRACE_MODE, one without), call restoreActionStatesWithFlag(DISABLE_IN_GRACE_MODE), verify only flagged action is restored and map is cleared |
| SelectionCoordinator | dragDidStart stores the dragging line and registers global AWT listener | unit | none | none | the AWT Toolkit registration is framework plumbing with no observable logic we own; dragDidStart's body is trivially verifiable only through side effects on the real AWT event queue — e2e-only, and already exercised by SelectionTest.testDragSelect (rubber-band drag) |
| SelectionCoordinator | globalMouseReleasedListener: clears drag rectangle and nulls dragging line on MOUSE_RELEASED | unit | — (e2e SelectionTest.testDragSelect exercises it but never asserts the cleanup) | missing | the cleanup logic (clearDragRectangle + null dragging line) is ours and directly invokable via package-private/reflection — the AWT dispatch is not our risk; write a unit test invoking the listener directly and assert both effects |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: saves on new non-null selection (selection changed from lastReflected) | unit | ReflectionHandlerTest.testNewSelectionSavesState (exercises save via full handler path indirectly) | adequate | — |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: restores when selection becomes null and no glissando | unit | ReflectionHandlerTest.testClearSelectionRestoresSavedState | adequate | — |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: saves (not restores) when selection null but glissando active | unit | GlissandoReflectionTest.SaveRestore.testSaveOccursOnGlissandoSelection | adequate | — |
| SelectionCoordinator | musicSelectionDidChangeSaveRestoreActionStates: no-op when selection unchanged (equals lastReflectedSelection) | unit | none | missing | add test: trigger handler twice with same selection, assert restoreActionStates is not called (saved state remains non-empty or verify action not reverted) |
| SelectionCoordinator | triggerReflection: skips when selection equals lastReflectedSelection | unit | none | missing | add test: call triggerReflection twice with same selection, assert action selected state from first call is not reset on second call (deduplication guard) |
| SelectionCoordinator | triggerReflection: reflects correct selected state onto all reflectable actions for element selection | unit | ReflectionHandlerTest, ReflectionIntegrationTest (comprehensive per-action matrix) | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=true when all applicable elements match | unit | ReflectionHandlerTest.testAllApplicableAllMatchSelected | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=false when any applicable element mismatches | unit | ReflectionHandlerTest.testAllApplicableFirstMismatchDeselected, testAllApplicableSecondMismatchDeselected | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=false when no applicable elements | unit | ReflectionHandlerTest.testAllInapplicableDeselected | adequate | — |
| SelectionCoordinator | triggerReflection: action selected=true when only non-applicable element mismatches (rest in mixed selection) | unit | ReflectionHandlerTest.testNoteAndRestNoteMatchesSelected | adequate | — |
| SelectionCoordinator | triggerReflection: clears lastReflectedSelection and routes to reflectGlissandoSelection when selection null + glissando active | unit | GlissandoReflectionTest.testConnectedGlissandoSelectedReflectsGlissandoAction | adequate | — |
| SelectionCoordinator | triggerReflection: sets lastReflectedSelection and skips glissando path on normal element selection | unit | GlissandoReflectionTest.testNoteWithGlissandoSelectedDoesNotTriggerGlissandoReflection | adequate | — |
| SelectionCoordinator | reflectGlissandoSelection: enables and selects only the matching glissando action | unit | GlissandoReflectionTest.testConnectedGlissandoSelectedReflectsGlissandoAction, testSlideOutSelectedReflectsSlideOutAction | adequate | — |
| SelectionCoordinator | reflectGlissandoSelection: disables non-matching glissando type | unit | GlissandoReflectionTest.testNonMatchingGlissandoToolDisabled | adequate | — |
| SelectionCoordinator | reflectGlissandoSelection: restores all actions on glissando deselection | unit | GlissandoReflectionTest.testClearGlissandoSelectionRestoresState | adequate | — |
| SelectionCoordinator | reflectElement: sets selected on all reflectable actions based on single element | unit | none | missing | add test: reflectElement on a note with known attributes, assert matching actions selected and non-matching deselected |
| SelectionCoordinator | updateGraceNoteActionEnabled: disables grace note action in select mode | unit | none | missing | add test: setInSelectMode(true), call updateGraceNoteActionEnabled(true), assert GRACE_EIGHTH_NOTE_ACTION disabled |
| SelectionCoordinator | updateGraceNoteActionEnabled: enabled only when not in select mode and hasGraceNote | unit | none | missing | add test: setInSelectMode(false), call updateGraceNoteActionEnabled(true), assert enabled; then updateGraceNoteActionEnabled(false), assert disabled |
| SelectionCoordinator | triggerReflection updates grace note action enabled state based on selection content | unit | none | missing | add test: selection containing a grace note triggers updateGraceNoteActionEnabled(true); selection without grace notes triggers false |
| SelectionCoordinator | collectActions lazily discovers reflectable/managed actions via reflection over Actions fields | unit | ReflectionTestHelper bypasses collectActions by injecting; no test of real collectActions path | none | collectActions scans Actions via reflection — that is framework wiring over a well-defined field set; production smoke of the real Actions scan is not warranted as a dedicated unit test |
| SelectionCoordinator | getReflectableActions / getManagedActions are lazily initialized and cached | unit | none | none | pure lazy-init memoization with no branching logic once list is computed; not worth testing directly |

**6A notes (quality concerns):**

The highest-risk gap is the complete absence of tests for `selectionHasRests` — the three branches (no selection, rests present, rests absent) have zero coverage despite `selectionHasRests` being used by `UIAction` to gate the `DISABLE_IN_RESTS_ONLY` flag path. `canDeleteLine` and `canChangeTempo` are similarly entirely untested; both have meaningful conditional logic (song.lineCount() > 1, single-element check) that could silently regress.

`restoreActionStatesWithFlag` (the grace-mode-specific partial restore) has no unit test at all. The method has a distinct code path from `restoreActionStates` — it filters by flag and always clears the map — and a bug there would corrupt action state on grace note completion without any signal from the test suite.

`clearSavedActionStates` also has no test. It is the "commit" path that prevents a stale restore, called from `ScoreViewController.handleDelete`; a test is straightforward.

The `isElementSelected`, `isLineSelected`, and `isGlissandoSelected` cross-line guard (`activeLineIndex != lineIndex → false`) has no direct unit coverage. These guards exist specifically to prevent rendering artifacts on inactive lines, and the only coverage is the e2e `SelectionTest.testClickInStaffHeaderSelectsLine` (which calls `scoreView().isLineSelected` through the full Swing stack).

`triggerReflection`'s deduplication guard (`selection.equals(lastReflectedSelection) → skip`) has no test. If broken, every redundant notification would reset toolbar state unnecessarily.

`reflectElement` (used by `GraceModeManager` to mirror host-note attributes after pairing) has no unit test.

The e2e `SelectionTest.testDragSelect` assertion uses `isGreaterThanOrEqualTo(3)` — a weak lower-bound oracle that cannot detect if the drag yields too many elements selected (off-by-one in hit detection). The exact count should be asserted.

`activateLine`'s behavior of clearing the previous line's selection (the cross-line-switch contract) is entirely untested. All tests use `ReflectionTestHelper` which activates only line 0, so the multi-line path that clears a prior active line is dead code from the test suite's perspective.

### 6B. selection data holders + `ClipboardManager`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ElementSelection` | pure data record (line, begin, end) — no logic | none | — | none | no test needed; pure carrier |
| `TupletToggleInfo` | compact-record guard: throws when `coversExisting=true` and `existing=null` | unit | none | missing | add unit test asserting the `IllegalArgumentException` |
| `TupletToggleInfo` | `canToggle`, `existing`, `coversExisting` fields accessible as record components | none | — | none | pure data accessors; no logic |
| `LineSelectionState` | `clearSelection()` resets all five fields and fires callback | unit | none | missing | add unit test with callback capture and field assertions |
| `LineSelectionState` | `setLineSelected(true)` clears `selectedGlissandoElementIndex` and fires callback | unit | none | missing | add unit test asserting glissando index reset |
| `LineSelectionState` | `setLineSelected(false)` sets `lineSelected=false` and fires callback | unit | none | missing | add unit test |
| `LineSelectionState` | `selectGlissando()` clears element + line selection, sets glissando index, fires callback | unit | `NoteConnectionTest.GlissandoSelection.testClickSelectGlissando` (e2e) | wrong-level | add unit test; e2e is also acceptable as an integration check but the logic can be exercised unit |
| `LineSelectionState` | `isGlissandoSelected(index)` returns true iff index matches stored glissando index | unit | none | missing | add unit test |
| `LineSelectionState` | `hasGlissandoSelection()` returns true iff `selectedGlissandoElementIndex != -1` | unit | `NoteConnectionTest` (e2e, asserts against real LSS) | wrong-level | add unit test |
| `LineSelectionState` | `hasElementSelection()` returns false when no selection, true after click-select | unit | `LineSelectionStateTest.testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing` (indirect) | adequate | — |
| `LineSelectionState` | `isElementSelected(index)` correctly bounds-checks with inclusive range | unit | `NoteConnectionTest.testSelectSourceNote` / `testSelectTargetNote` (e2e) | wrong-level | add unit test; selection range logic is pure state |
| `LineSelectionState` | `getSelectionSize()` returns 0 when no selection, N=(end-begin+1) otherwise | unit | none (only mocked indirectly in other tests) | missing | add unit test |
| `LineSelectionState` | `getSelection()` returns `null` when no element and no line selection | unit | none | missing | add unit test |
| `LineSelectionState` | `getSelection()` returns `ElementSelection` spanning full line when `lineSelected=true` | unit | none | missing | add unit test |
| `LineSelectionState` | `getSelection()` returns `ElementSelection` from element range when element-selected | unit | used via mock in `ApplyToSelectionInterceptTest` / `DynamicMarkingActionTest` | inadequate | tests only stub the return on a mock coordinator; add unit test on LSS directly |
| `LineSelectionState` | `getSingleSelectedElement()` returns null when >1 selected, element when exactly 1 | unit | `SelectionCoordinatorLyricSelectionTest.testElementSelectionClearsLyricSelection` (indirect, asserts coordinator wrapper) | adequate | — |
| `LineSelectionState` | `setSelectionFromClick()` sets begin=end=anchor=index, clears glissando, fires callback | unit | used as setup in `LineSelectionStateTest` and `ToggleConflictTest` but never directly asserted | inadequate | add unit test asserting all five side effects |
| `LineSelectionState` | `extendSelectionTo()` with anchor < index: sets begin=anchor, end=index | unit | used as setup in `LineSelectionStateTest` but never directly asserted on its own | inadequate | add unit test asserting correct [begin,end] ordering in both directions |
| `LineSelectionState` | `extendSelectionTo()` with anchor > index (reversed drag): begin=index, end=anchor | unit | none | missing | add unit test |
| `LineSelectionState` | `extendSelectionTo()` no-op when anchor is -1 | unit | none | missing | add unit test |
| `LineSelectionState` | `extendSelection()` starts new selection when begin=-1 | unit | none | missing | add unit test |
| `LineSelectionState` | `extendSelection()` extends end when selection exists (begin unchanged) | unit | none | missing | add unit test |
| `LineSelectionState` | `resetElementSelection()` sets begin=end=-1 (does not touch lineSelected/glissando), fires callback | unit | none | missing | add unit test |
| `LineSelectionState` | `setSelectionAnchor()` / `getSelectionAnchor()` round-trip | unit | none | missing | add unit test |
| `LineSelectionState` | `selectAll()` excludes auto-maintained terminal (FINAL_DOUBLE_BARLINE) | unit | `LineSelectionStateTest.testSelectAllExcludesAutoMaintainedFinalBarlineOnLastLine` | adequate | — |
| `LineSelectionState` | `selectAll()` excludes REPEAT_RIGHT terminal on last line | unit | `LineSelectionStateTest.testSelectAllExcludesAutoMaintainedRightRepeatTerminalOnLastLine` | adequate | — |
| `LineSelectionState` | `selectAll()` on empty line (only terminal) selects nothing | unit | `LineSelectionStateTest.testSelectAllOnLineWithOnlyFinalBarlineSelectsNothing` | adequate | — |
| `LineSelectionState` | `selectAll()` on non-last line includes all elements | unit | `LineSelectionStateTest.testSelectAllOnNonLastLineIncludesAllElements` | adequate | — |
| `LineSelectionState` | `selectionChangeCallback` fires on every state-mutating call | unit | none | missing | add unit test with a counter callback |
| `LineSelectionState` | `canToggleBeaming()` — size < 2 returns false | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` (indirectly; quarter notes are not beamable so size check and beamable check overlap) | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — non-beamable element in range returns false | unit | `ToggleConflictTest.testChangeDurationToQuarterDisablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — all beamable, no existing beam (add mode) | unit | `ToggleConflictTest.testChangeDurationToEighthBothEnabled` | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — existing beam covering selection (remove mode) | unit | `ToggleConflictTest.testToggleTieOnDisablesBeam` / `testToggleTieOffReenablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleBeaming()` — blocked when would connect same span as existing tie | unit | `ToggleConflictTest.testToggleTieOnDisablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleTie()` — size != 2 returns false and sets canTie=false | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` (size=2 path) + lacks explicit size!=2 assertion | inadequate | add explicit test for size < 2 and size > 2 setting canTie=false |
| `LineSelectionState` | `canToggleTie()` — non-pitched element in range returns false | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTie()` — pitch mismatch returns false | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTie()` — elements in different ties returns false | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTie()` — two same-pitch notes with no tie (add mode): canTie=true, existingTie=null | unit | `ToggleConflictTest.testQuarterNotesBeamingDisabledTieEnabled` + `testToggleTieOnDisablesBeam` | adequate | — |
| `LineSelectionState` | `canToggleTie()` — two notes sharing existing tie (remove mode): canTie=true, existingTie set | unit | `ToggleConflictTest.testToggleTieOffReenablesBeam` (uses getExistingTie) | adequate | — |
| `LineSelectionState` | `canToggleTie()` — blocked when would connect same span as existing beam | unit | `ToggleConflictTest.testToggleBeamOnDisablesTie` | adequate | — |
| `LineSelectionState` | `resetTieState()` clears canTie and existingTie | unit | `ToggleConflictTest.toggleTie()` calls resetTieState but doesn't assert fields directly | inadequate | add unit test asserting `getCanTie()` and `getExistingTie()` return null after reset |
| `LineSelectionState` | `canToggleTuplet()` — size < 2 returns (false, null, false) | unit | `LineSelectionStateTest.testEmptySelectionCannotToggleTuplet` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — non-pitched element returns (false, null, false) | unit | `LineSelectionStateTest.testSelectionContainingNonPitchedElementCannotToggle` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — two pitched notes, no tuplet: (true, null, false) | unit | `LineSelectionStateTest.testTwoPitchedNotesNoTupletCanToggle` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — selection spans two different tuplets: (false, null, false) | unit | `LineSelectionStateTest.testSelectionSpanningTwoDifferentTupletsCannotToggle` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — full coverage of existing triplet: (true, tuplet, true) | unit | `LineSelectionStateTest.testFullCoverageOfTripletReportsCoversExisting` | adequate | — |
| `LineSelectionState` | `canToggleTuplet()` — partial coverage of existing tuplet: (true, tuplet, false) | unit | `LineSelectionStateTest.testPartialCoverageOfTripletDoesNotCoverExisting` | adequate | — |
| `LineSelectionState` | `canToggleTrill()` — returns false when no selection | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTrill()` — returns true when at least one pitched note in range | unit | none | missing | add unit test |
| `LineSelectionState` | `canToggleTrill()` — returns false when selection contains only rests | unit | none | missing | add unit test |
| `LineSelectionState` | `canFlipStemDirection()` — returns false when nothing selected | unit | none | missing | add unit test |
| `LineSelectionState` | `canFlipStemDirection()` — returns true when at least one non-rest in range | unit | none | missing | add unit test |
| `LineSelectionState` | `canFlipStemDirection()` — returns false when selection is all rests | unit | none | missing | add unit test |
| `ClipboardManager` | `addElement()` normalizes FINAL_DOUBLE_BARLINE → DOUBLE_BARLINE | unit | `ClipboardManagerTest.AddElement.testFinalDoubleBarlineNormalizedToDoubleBarline` | adequate | — |
| `ClipboardManager` | `addElement()` passes non-FINAL_DOUBLE_BARLINE through unchanged | unit | `ClipboardManagerTest.AddElement.testDoubleBarlinePassedThrough` + `testNotePassedThrough` | adequate | — |
| `ClipboardManager` | `addElement()` does not mutate original song element | unit | `ClipboardManagerTest.testSongFinalBarlineUntouched` + `testSongRightRepeatTerminalUntouched` | adequate | — |
| `ClipboardManager` | `getSize()` returns correct count after adds | unit | none (only used via `getElement(0)` by index in tests) | missing | add unit test |
| `ClipboardManager` | `isEmpty()` returns true initially, false after add | unit | none | missing | add unit test |
| `ClipboardManager` | `getFirstElement()` / `getLastElement()` return correct elements from multi-element pasteboard | unit | none | missing | add unit test |
| `ClipboardManager` | `clear()` empties pasteboard and isEmpty() returns true | unit | none | missing | add unit test |

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

### 6C. `ui/edit`

| class | behavior | required level | existing test | verdict |  action |
|---|---|---|---|---|---|
| GraceModeManager | `isActive()` returns false when no instance / INACTIVE state | unit | UIActionFlagBehaviorTest stubs `GraceModeManager::isActive` via mockStatic — asserts nothing about GraceModeManager itself | missing | unit test: construct instance, verify isActive() reflects state |
| GraceModeManager | `isInProgress()` returns false in INACTIVE, true in non-INACTIVE states | unit | none | missing | unit test: construct, check INACTIVE→false; transition→true |
| GraceModeManager | `isPendingCancel(element)` returns true only for the active grace note when pendingCancel flag is set | unit | none | missing | unit test: reflectively set pendingCancel, verify per-element identity check |
| GraceModeManager | `getCancelThresholdPx()` / `getConnectThresholdPx()` return -1 when inactive, correct px offsets (±GRACE_SLOP_PX) when layout available | unit | none | missing | unit test: mock layout result, assert threshold math |
| GraceModeManager | `getLockedInsertionXSs()` returns 0 when any required field is null; returns graceColumn.xSs + rightExtent + gap + hostLeftExtent otherwise | unit | none | missing | unit test: null-guard paths and computed value |
| GraceModeManager | `mousePressed()`: returns false when state != INACTIVE or not GRACE_QUAVER selected; returns true and consumes in GRACE_NOTE_INSERT; enters GRACE_NOTE when room check passes | unit | ElementInsertionTest exercises full pipeline via real robot clicks (e2e) | wrong-level | unit test: mock InsertionSpacingCalculator, verify state transitions |
| GraceModeManager | `mouseReleased()`: click (< slop or short duration) → enterGraceNoteInsert; drag-left → finish(cancel); drag-right with eligible host → enterGraceNotePaired | unit | ElementInsertionTest (drag-left, drag-right e2e) — timing-dependent real mouse | wrong-level | unit test: set state to GRACE_NOTE, synthesize events, assert transitions |
| GraceModeManager | `mouseDragged()`: pendingCancel computed from time + left-of-threshold; pendingConnect computed from right-of-threshold + eligible host; glissando added/removed on graceNote | unit | none | missing | unit test: mock layout, verify pendingCancel/pendingConnect flags and glissando mutations |
| GraceModeManager | `mouseClicked()`: consumed when not in GRACE_NOTE_INSERT; justEnteredInsert flag suppresses first click after transition; different-line click → finish(cancel); left-of-grace click → finish(cancel); same-pitch → error + finish; different pitch → insert host + connect glissando + enterGraceNotePaired | unit | ElementInsertionTest (click-click insertion, same-pitch error — e2e) | wrong-level | unit test: mock PreviewElementManager.handleClick, assert state and mutation |
| GraceModeManager | `keyPressed()`: Escape key → finish(cancel=true) | e2e | ElementInsertionTest.GraceNoteCancellation.testEscapeCancels — uses real key dispatch, asserts element count and mode inactive | adequate | — |
| GraceModeManager | drag-left cancel path: exceeds GRACE_SLOP_PX left of grace note → grace note removed | e2e | ElementInsertionTest.GraceNoteCancellation.testDragLeftCancels — real robot drag to cancelThresholdPx, asserts count and mode inactive | adequate | — |
| GraceModeManager | drag-right connect path: exceeds GRACE_SLOP_PX right of grace note with eligible host → paired with glissando | e2e | ElementInsertionTest.GraceNoteDragConnect.testDragConnectToStandaloneNote — real robot drag, asserts grace type, glissando, mode inactive | adequate | — |
| GraceModeManager | Escape cancels grace mode and restores element count | e2e | ElementInsertionTest.GraceNoteCancellation.testEscapeCancels | adequate | — |
| GraceModeManager | same-pitch error: both grace and host would be at same staff position → error dialog + both removed | e2e | ElementInsertionTest.GraceNoteCancellation.testSamePitchError — asserts element count restored and mode inactive | adequate | — |
| GraceModeManager | click-click insertion: grace inserted then host inserted with glissando, mode exits | e2e | ElementInsertionTest.GraceNoteInsertion.testClickClickInsertion — asserts types, glissando, count, mode inactive, actions re-enabled | adequate | — |
| GraceModeManager | toolbar state during grace mode: DISABLE_IN_GRACE_MODE actions disabled (glissando, rest, grace button) | e2e | ElementInsertionTest.GraceNoteCancellation.testEscapeCancels (partial: checks isEnabled in assertAll) | adequate | — |
| GraceModeManager | duration change during grace flow produces host note of new duration | e2e | ElementInsertionTest.GraceNoteInsertion.testDurationChangeDuringFlow — asserts host type and toolbar selection state | adequate | — |
| GraceModeManager | `enterGraceNoteInsert()` aborts with error dialog when host note would not fit on line | unit | none | missing | unit test: mock InsertionSpacingCalculator to return no-fit, assert finish(cancel) called |
| GraceModeManager | `finish(cancel=true)` wraps grace note removal in withModification bracket (mutation emitted) | unit | none | missing | unit test: mock line, verify removeElement called inside modification bracket |
| GraceModeManager | `finish(cancel=false)` resets all state fields and posts GraceModeStateDidChangeNotification(false) | unit | none | missing | unit test: verify state reset and message posted |
| GraceModeManager | `hasEligibleHostNote()` returns true only when next element exists and isPitchedNote | unit | none | missing | unit test: construct line with various next-element types |
| GraceModeManager | `GraceModeStateDidChangeNotification(true)` posted on enterGraceNote; `(false)` posted on finish | unit | none | missing | unit test: mock MessageCenter.post, verify notification payloads |
| GraceModeManager | entering GRACE_NOTE selects QUARTER_NOTE duration and deselects embellishment action groups | unit | none | missing | unit test: set embellishments, enter grace note, verify action group selections |
| EditModeManager | `init()` + static `instance()` — throws RuntimeError if called before init | unit | none | missing | unit test: call instance() without init, expect RuntimeError/IllegalStateException |
| EditModeManager | `makePreviewElement()`: resolves type from DURATION_ACTION_GROUP if selected; falls back to NON_DURATION_ACTION_GROUP; defaults to CROTCHET | unit | none | missing | unit test: mock action groups, verify returned element type |
| EditModeManager | `makePreviewElement(ElementType)`: converts pitched note to rest type when REST_ACTION is selected | unit | none | missing | unit test: mock REST_ACTION.isSelected=true, verify rest type returned |
| EditModeManager | `decorateElement()`: applies selected dot level; skips non-dot decorations for rests; applies accidental; applies accidental-in-parentheses; adds/removes FermataAttachment; clears and resets articulations | unit | none | missing | unit test: mock each action, assert element fields after decoration |
| EditModeManager | `elementWasModified()`: REPEAT_LEFT adjacent to REPEAT_RIGHT → coalesce to REPEAT_LEFT_RIGHT at previous index | unit | none | missing | unit test: construct line with REPEAT_RIGHT then click REPEAT_LEFT, verify coalesced |
| EditModeManager | `elementWasModified()`: REPEAT_RIGHT adjacent to existing REPEAT_LEFT → coalesce | unit | none | missing | unit test: construct line with REPEAT_LEFT then click REPEAT_RIGHT, verify coalesced |
| EditModeManager | `elementWasModified()` returns false for non-repeat preview elements | unit | PreviewElementManagerTestBase stubs this as `thenReturn(false)` — tests PreviewElementManager, not EditModeManager | missing | unit test: direct call with non-repeat preview element |
| EditModeManager | `previewElementDidChange()`: turns off FERMATA_ACTION and ACCIDENTAL_IN_PARENS after insert; calls scoreActions.setPreviewElement with decorated next element; calls drawWidthIfWiderLine and repaint | unit | none | missing | unit test: mock scoreActions and Actions, verify callbacks and action state |
| EditModeManager | `previewElementDidChange()`: starts PlayThread when playInsertedNote=true and inserted element is a note | unit | none | missing | unit test: mock PlayThread construction, verify it starts |
| ScoreActions | `clearSelection()` / `repaint()` / `setPreviewElement()` / `drawWidthIfWiderLine()` / `syncPlaybackPrefs()` / `updatePageLayout()` / `setKeyBindingsEnabled()` — all are pure interface callbacks, no logic | none | — | none | no test warranted; contract tested via ScoreView which implements it |

**6C notes (quality concerns):** The `ui/edit` package has no mirrored test file — `src/test/java/songscribe/ui/edit/` contains only `package-info.java`. Zero unit tests exist for `GraceModeManager` or `EditModeManager`; all coverage is either (a) e2e tests in `ElementInsertionTest` that exercise GraceModeManager's happy paths through real robot clicks, or (b) fixture-level mocks in `PreviewElementManagerTestBase` and `UIActionFlagBehaviorTest` that stub these classes to test other components. The e2e tests for cancellation, drag-connect, click-click insertion, same-pitch error, and duration change are well-structured and assert meaningful model state — they are adequate for their scope — but they do not cover the state machine logic, flag transitions, `pendingCancel`/`pendingConnect` computation, `mouseDragged` glissando mutation, `enterGraceNoteInsert` abort path, `finish` mutation bracketing, `GraceModeStateDidChangeNotification` payloads, or the complete `decorateElement`/`makePreviewElement`/`elementWasModified` branches in `EditModeManager`. The `UIActionFlagBehaviorTest.testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses` uses `mockStatic(GraceModeManager.class)` and stubs `isActive()` — it asserts nothing about `GraceModeManager` itself; it is adequate for its own target (`UIAction` flag interaction) but provides zero coverage here. The biggest gaps are the state-transition logic in `mouseReleased` (click vs. drag detection is time-based and subtle), `mouseDragged` pendingCancel/pendingConnect logic, `enterGraceNoteInsert` abort, `decorateElement` branch coverage, and `elementWasModified` repeat-coalescing — all unit-testable with mocked collaborators.

### 6D. `ui/adjustment`

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| Adjustment | `mousePressed`: ignores event when `enabled=false` | unit | none | missing | Add unit test: mock scoreView, setEnabled(false), fire mousePressed, assert startedDrag remains false and scoreView.setDragDisabled never called |
| Adjustment | `mousePressed`: sets startedDrag=true, captures startPoint, calls startedDrag(), disables ScoreView drag when startedDrag flag survives startedDrag() | unit | none | missing | Add unit test via concrete subclass or spy; assert startedDrag=true and setDragDisabled(true) called |
| Adjustment | `mouseReleased`: ignores event when `enabled=false` | unit | none | missing | Add unit test: verify finishedDrag not called and drag not re-enabled |
| Adjustment | `mouseReleased`: clears startedDrag, calls finishedDrag(), re-enables ScoreView drag | unit | none | missing | Add unit test |
| Adjustment | `mouseDragged`: ignores event when `enabled=false` | unit | none | missing | Add unit test |
| Adjustment | `mouseDragged`: clamps X to [topLeftDragBounds.x, bottomRightDragBounds.x-1] | unit | none | missing | Critical arithmetic: test exact boundary values — at bound, one-past-bound, below bound; assert endPoint.x is clamped precisely |
| Adjustment | `mouseDragged`: clamps Y to [topLeftDragBounds.y, bottomRightDragBounds.y-1] | unit | none | missing | Same — exact value assertions, not just sign |
| Adjustment | `mouseDragged`: skips drag() when startedDrag=false | unit | none | missing | Add unit test |
| HorizontalAdjustment | `startedDrag()`: sets startedDrag=false when no AdjustRect contains startPoint | unit | none | missing | Unit test with populated adjustRects, click outside all — assert startedDrag=false |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: left bound = prev note x + rect.width; right bound = next note x - rect.width | unit | none | missing | Exact arithmetic; mock line with known note positions; assert topLeftDragBounds.x and bottomRightDragBounds.x to exact pixel values |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: left bound = 20 + rect.width when xIndex=0 (no predecessor) | unit | none | missing | Edge case: first note |
| HorizontalAdjustment | `startedDrag()` SINGLE_NOTE: right bound = lineWidth when xIndex = last note | unit | none | missing | Edge case: last note |
| HorizontalAdjustment | `startedDrag()` TO_END_OF_LINE: bounds computation | unit | none | missing | Exact arithmetic |
| HorizontalAdjustment | `startedDrag()` STRETCH_NOTE_SPACING: stretchHelper populated with note x positions; reallocated when too small | unit | none | missing | Assert stretchHelper values equal note xOffsets |
| HorizontalAdjustment | `startedDrag()` GLISSANDO_START: right bound = next AdjustRect's rect.x | unit | none | missing | Exact index lookup |
| HorizontalAdjustment | `startedDrag()` GLISSANDO_END: left bound = prev AdjustRect's rect.x + rect.width | unit | none | missing | |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x NOT adjusted when `!isInteractable` (terminal node) | unit | HorizontalAdjustmentTest.SnapToEndSkipped.testFinalDoubleBarlineTerminalSkipsSnap | inadequate | Existing test only asserts `isInteractable=false` and `snapToEnd=true` on the model; it never exercises `drag()` — endPoint.x is never set and the snap branch is never reached. The test verifies preconditions of the guard, not that drag() actually skips the snap. |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x NOT adjusted when `!isInteractable` (REPEAT_RIGHT terminal) | unit | HorizontalAdjustmentTest.SnapToEndSkipped.testRepeatRightTerminalSkipsSnap | inadequate | Same issue as above — precondition-only assertion, drag() never called |
| HorizontalAdjustment | `drag()` snap-to-end: endPoint.x IS adjusted to `lineWidth - contentWidthPx` when interactable, snapToEnd, and within END_SNAP_LIMIT | unit | none | missing | The actual snap arithmetic is completely untested |
| HorizontalAdjustment | `drag()` SINGLE_NOTE: `note.setXOffsetPx(endPoint.x)` | unit | none | missing | Verify model mutation; exact value |
| HorizontalAdjustment | `drag()` TO_END_OF_LINE: all notes from xIndex forward shifted by (endPoint.x - diffX) | unit | none | missing | Multi-note delta arithmetic; assert each element's x exactly |
| HorizontalAdjustment | `drag()` STRETCH_NOTE_SPACING: each element x = firstX + (stretchHelper[i]-firstX)*ratio; `changeElementSpacingRatio` called | unit | none | missing | Non-trivial float ratio arithmetic |
| HorizontalAdjustment | `drag()` START_OF_LINE: all lines' notes shifted by diff from new x | unit | none | missing | Multi-line cascading offset math |
| HorizontalAdjustment | `drag()` GLISSANDO_START: `glissando.x1Translate += endPoint.x - diffX` | unit | none | missing | |
| HorizontalAdjustment | `drag()` GLISSANDO_END: `glissando.x2Translate += endPoint.x - diffX` | unit | none | missing | |
| HorizontalAdjustment | `drag()` CRESCENDO_START/DIMINUENDO_START: `setX1ShiftSs` incremented by delta | unit | none | missing | |
| HorizontalAdjustment | `drag()` CRESCENDO_END/DIMINUENDO_END: `setX2ShiftSs` incremented by delta | unit | none | missing | |
| HorizontalAdjustment | `drag()`: `song.setModified(true)` called on every drag event | unit | none | missing | |
| HorizontalAdjustment | `finishedDrag()`: draggingRect set to null | unit | none | missing | Trivial but verifiable |
| HorizontalAdjustment | `setEnabled(true)`: adjustRects populated with correct types and counts | unit | none | missing | Verify per type: SINGLE_NOTE count = effectiveElementCount, STRETCH = 1 per line, etc. |
| HorizontalAdjustment | `setEnabled(false)`: adjustRects cleared | unit | none | missing | |
| HorizontalAdjustment | `setEnabled(true)`: GLISSANDO_END rect NOT added for SLIDE_OUT glissando | unit | none | missing | Important conditional: CONNECTED vs SLIDE_OUT |
| HorizontalAdjustment | `findHairpinByAnchor`: returns crescendo/diminuendo matching anchorElementIndex | unit | none | missing | |
| HorizontalAdjustment | `findHairpinByEnd`: returns crescendo/diminuendo matching endElementIndex | unit | none | missing | |
| HorizontalAdjustment | `drag()` diffX calculation: `diffX = draggingRect.rect.x + (rect.width/2)` — midpoint arithmetic | unit | none | missing | Exact mid-point computation drives all delta calculations |
| VerticalAdjustment | `startedDrag()`: sets startedDrag=false when startPoint=null | unit | none | missing | Null guard path |
| VerticalAdjustment | `startedDrag()`: sets startedDrag=false when no AdjustRect contains startPoint | unit | none | missing | |
| VerticalAdjustment | `startedDrag()` ROW_HEIGHT: topLeft.y = noteYPosPx(6, 0); bottomRight.y = Integer.MAX_VALUE | unit | none | missing | Exact bound values |
| VerticalAdjustment | `startedDrag()` TEMPO_CHANGE / FIRST_SECOND_ENDING / TRILL / BEAT_CHANGE: upLeft.y = noteYPosPx(6, line-1); downRight.y = noteYPosPx(-4, line) | unit | none | missing | Exact bound arithmetic |
| VerticalAdjustment | `startedDrag()` ANNOTATION / TUPLET / CRESCENDO_Y / DIMINUENDO_Y: upLeft.y = noteYPosPx(6, line-1); downRight.y = noteYPosPx(-6, line+1) | unit | none | missing | Exact bound arithmetic |
| VerticalAdjustment | `drag()` diffY calculation: `(endPoint.y - dragRect.rect.y) + midPoint.y` | unit | none | missing | Core delta arithmetic driving ALL adjust* calls; should be tested with exact values |
| VerticalAdjustment | `drag()` diffX calculation: `(endPoint.x - dragRect.rect.x) + midPoint.x` — only used internally, rect.y updated | unit | none | missing | |
| VerticalAdjustment | `adjustAttribution(diffY)`: posts LayoutDidChangeNotification with attributionStartYSs + diffY | unit | none | missing | Exact new value |
| VerticalAdjustment | `adjustTopSpace(diffY)`: posts LayoutDidChangeNotification with topPaddingSs + diffY | unit | none | missing | Exact new value |
| VerticalAdjustment | `adjustRowHeight(diffY)`: posts LayoutDidChangeNotification with rowHeightAdjustmentSs + diffY | unit | none | missing | Exact new value |
| VerticalAdjustment | `adjustTempoChange(line, diffY)`: all TempoChangeAttachment.userYOffsetSs incremented by diffY | unit | none | missing | Accumulation for multi-note lines |
| VerticalAdjustment | `adjustBeatChange(line, diffY)`: all BeatChangeAttachment.userYOffsetSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustFirstSecondEnding(line, diffY)`: all Ending.yPositionSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustAnnotation(line, diffY)`: both userYOffsetSs AND legacy yPosPx incremented by diffY | unit | none | missing | Dual-update is a subtle correctness requirement |
| VerticalAdjustment | `adjustAnnotation(line, diffY)`: no-op when dragRect has no AnnotationAttachment | unit | none | missing | Null-guard path |
| VerticalAdjustment | `adjustTrill(line, diffY)`: all Trill.yPositionSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustDynamics(line, diffY)`: Hairpin.yShiftSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustDynamics(line, diffY)`: no-op when dragRect=null or hairpin not found | unit | none | missing | |
| VerticalAdjustment | `adjustTuplet(line, diffY)`: Tuplet.verticalPositionSs incremented by diffY | unit | none | missing | |
| VerticalAdjustment | `adjustTuplet(line, diffY)`: no-op when tuplet not found | unit | none | missing | |
| VerticalAdjustment | `finishedDrag()`: dragRect set to null | unit | none | missing | |
| VerticalAdjustment | `setEnabled(true)`: ATTRIBUTION rect added only when attribution non-empty | unit | none | missing | Conditional enrollment |
| VerticalAdjustment | `setEnabled(true)`: TOP_SPACE rect added only when lineCount > 0 | unit | none | missing | |
| VerticalAdjustment | `setEnabled(true)`: ROW_HEIGHT rect added only when lineCount > 1 | unit | none | missing | |
| VerticalAdjustment | `setEnabled(true)`: per-line rects (TEMPO_CHANGE, ANNOTATION, ENDING, TRILL, BEAT_CHANGE, CRESCENDO_Y, DIMINUENDO_Y, TUPLET) populated for matching elements | unit | none | missing | |
| VerticalAdjustment | `setEnabled(false)`: adjustRects cleared | unit | none | missing | |
| VerticalAdjustment | `getAttributionAdjustRect`: x = sheetWidthPx - HANDLE_SIZE_PX; y = attributionStartYSs | unit | none | missing | Exact pixel placement |
| VerticalAdjustment | `getHeightAdjustRect`: x = 0; y = noteYPosPx(0, line) - HANDLE_SIZE_PX/2 | unit | none | missing | Exact pixel placement (HANDLE_SIZE_PX/2 rounding matters) |
| VerticalAdjustment | `getRangeElementAdjustRect`: x = startNote.x - xOffsetPx; y = bounds.topSs - HANDLE_SIZE_PX | unit | none | missing | Exact geometry |
| VerticalAdjustment | `getRangeElementAdjustRect`: returns false and skips when rangeElement/startNote/endNote is null | unit | none | missing | Null-guard correctness |
| VerticalAdjustment | dynamics handle x = (startX + endX + DYNAMICS_HANDLE_CENTER_BIAS_PX) / 2 | unit | none | missing | Integer midpoint arithmetic with bias constant |
| VerticalAdjustment | tuplet handle x = startNote.x (upper stem) vs startNote.x + TUPLET_LOWER_HANDLE_X_OFFSET_PX (lower stem) | unit | none | missing | Branch on isUpper() |
| VerticalAdjustment | `findHairpinByAnchor`: returns crescendo/diminuendo matching anchorElementIndex | unit | none | missing | |
| VerticalAdjustment | `getLayoutResultForLine`: throws IllegalStateException when mainPanel null or layoutResult null | unit | none | missing | Error-path contracts |
| VerticalAdjustment | `repaint()`: renders all AdjustRect handles with correct colors | none | none | none | Pure Graphics2D rendering, no branching logic worth testing |
| HorizontalAdjustment | `repaint()`: renders all AdjustRect handles | none | none | none | Pure rendering |
| Adjustment | constructor: registers self as MouseListener and MouseMotionListener on scoreView | none | none | none | Pure listener registration — no branching or computation; framework wiring, not our risk |

**6D notes (quality concerns):**

The two existing tests in `HorizontalAdjustmentTest` (`testFinalDoubleBarlineTerminalSkipsSnap` and `testRepeatRightTerminalSkipsSnap`) are **inadequate** in a particularly misleading way: they are correctly named ("SnapToEndSkipped") but they never call `drag()` and never set `endPoint`. They verify only that the model properties which *would* allow the snap to fire happen to be false/true — i.e., they test preconditions on `Song.isInteractable` and `ElementType.snapToEnd`, not the behavior of `drag()` itself. The snap branch in `drag()` is entirely uncovered: a test asserting `endPoint.x` was NOT modified (or WAS modified for an interactable note near the end) would actually exercise the code. The class has zero additional tests for any of the ~10 drag operation types, the bounds computation, the stretchHelper ratio arithmetic, or the `setModified` call.

`VerticalAdjustment` has **zero tests** of any kind despite containing non-trivial logic: the `diffY` calculation `(endPoint.y - dragRect.rect.y) + midPoint.y` drives all model mutations; the `adjustAnnotation` dual-update (both `userYOffsetSs` and legacy `yPosPx`); the conditional HANDLE_SIZE_PX/2 midpoint in `getHeightAdjustRect`; the dynamics center-bias arithmetic; and the `isUpper()` branch in the tuplet handle placement. Any silent regression in these calculations would go undetected.

`Adjustment` (the abstract base) has zero tests for its mouse-event dispatch, the enabled guard, and most critically the X/Y clamping arithmetic in `mouseDragged` — the only shared geometry computation in the hierarchy.

The `HorizontalAdjustment` GLISSANDO_END omission for SLIDE_OUT glissandos (`setEnabled` conditional) is a correctness rule that is completely untested.

The unit-conversion guide identifies `diffY` and `diffX` as values arriving in pixels (`endPoint` is an AWT pixel coordinate) applied as-is to `Ss`-suffixed fields (e.g. `getUserYOffsetSs() + diffY`). This is a potential mixed-unit bug that no test currently guards.

### §6 summary (`ui/selection` + `ui/edit` + `ui/adjustment` + `ui/clipboard`, 11 classes)

**Verdict distribution (259 rows):** 78 adequate, 154 missing, 10 inadequate, 7 wrong-level, 10 none, 0 redundant. `missing` dominates because three of the four packages have little or no dedicated coverage; the well-covered class (`SelectionCoordinator`) accounts for 47 of the 78 `adequate` rows.

**The defining shape: drag/adjustment geometry and selection-predicate logic are the dark zones; only the selection *engine* is well-tested.** Coverage is sharply bimodal — `SelectionCoordinator` (6A) has real, level-appropriate tests for ~half its surface, while `ui/adjustment` (6D) and `ui/edit` (6C) are almost entirely untested, and the few existing adjustment tests are actively misleading.

**Where the real pure-logic risk concentrates:**
- **`ui/adjustment` (6D) is the single largest dark zone** — 72 rows, every non-`none` row `missing`, and the two existing `HorizontalAdjustmentTest` tests are *inadequate* in a misleading way: correctly named (`…SnapToEndSkipped`) but they never call `drag()` or set `endPoint`, so they assert only the model *preconditions* that would gate the snap, never the snap arithmetic. `VerticalAdjustment` and the `Adjustment` base have **zero** tests. All of it is pure geometry — `diffY = (endPoint.y - dragRect.rect.y) + midPoint.y`, the `Adjustment.mouseDragged` X/Y clamping (off-by-one `bound - 1` semantics), per-`AdjustType` drag bounds, stretch-ratio arithmetic, the SLIDE_OUT GLISSANDO_END omission rule — `unit`-level and mockable. This is the highest-leverage surviving-mutant risk in the session.
- **`ui/edit` (6C) has no mirrored test file at all.** `GraceModeManager`/`EditModeManager` are covered only by e2e happy-path robot tests (adequate for their scope) plus fixture mocks that assert nothing about these classes (`UIActionFlagBehaviorTest`'s `mockStatic(GraceModeManager)` stub contributes zero coverage here). The subtle logic — `mouseReleased` time-based click-vs-drag detection, `mouseDragged` pendingCancel/pendingConnect, `enterGraceNoteInsert` abort path, `finish(cancel)` mutation bracketing, `GraceModeStateDidChangeNotification` payloads, and `EditModeManager.decorateElement`/`makePreviewElement`/`elementWasModified` (REPEAT_LEFT+RIGHT → REPEAT_LEFT_RIGHT coalescing) branches — is all unit-testable with mocked collaborators and entirely missing.
- **`SelectionCoordinator` (6A) enablement predicates that gate toolbar flags are dark** even though the class is otherwise well-covered: `selectionHasRests` (gates the `DISABLE_IN_RESTS_ONLY` flag — three branches, zero coverage), `canDeleteLine` (three guards incl. `lineCount() > 1`), `canChangeTempo`, the grace-mode `restoreActionStatesWithFlag`/`clearSavedActionStates`/`reflectElement` paths, and the `triggerReflection` dedup guard (`equals(lastReflectedSelection) → skip`).

**Two systemic gaps recur across 6A and 6B:** (1) **cross-line selection guards** — `isElementSelected`/`isLineSelected`/`isGlissandoSelected`'s `activeLineIndex != lineIndex → false` guard, and `activateLine`'s clear-previous-line contract, are untested because every fixture activates only line 0; (2) **reversed-drag** — `LineSelectionState.extendSelectionTo`'s `anchor > index` branch is uncovered (setups always pass `anchor < index`). Also dark: `clearSelection`/`resetElementSelection` post-conditions, `canToggleTie/Trill`, `canFlipStemDirection`, and the `ClipboardManager` pasteboard accessors (`isEmpty`/`getSize`/`getFirstElement`/`getLastElement`/`clear` — only `addElement` normalization is tested).

**Wrong-level (7): pure in-memory state tested only through the Swing pipeline.** `selectGlissando`/`hasGlissandoSelection`/`isElementSelected` (e2e `NoteConnectionTest` robot clicks), `isLineSelected` (e2e `SelectionTest`), and the `ui/edit` mouse state-machine logic (6C) carry no rendering dependency and should be unit tests. Separately, `e2e SelectionTest.testDragSelect` asserts `isGreaterThanOrEqualTo(3)` — a weak lower-bound oracle that passes even if the rubber-band selects too many elements (inadequate).

**Level distribution holds the rubric:** overwhelmingly `unit` (selection state machines, enablement predicates, adjustment geometry/clamping, clipboard normalization) — all assertable with the `MainFrame.getInstance()` singleton mocked. Genuine `none` (10): `ElementSelection` record components, `TupletToggleInfo` field accessors (its compact-constructor guard is `missing`, not `none`), `ScoreActions` (pure interface — all seven callbacks), the two `repaint(Graphics2D)` methods, and the `Adjustment` constructor (pure listener registration). No behavior in scope genuinely required e2e beyond the robot-driven cases already noted as wrong-level.

**Two classifications resolved during assembly:** (1) the `Adjustment` constructor was downgraded `missing → none` after reading lines 41–45 — it only stores `scoreView` and registers two listeners, no branching or computation; (2) `SelectionCoordinator.globalMouseReleasedListener` was reclassified `e2e/inadequate → unit/missing` — its cleanup body (clear drag rectangle + null the dragging line) is our logic and directly invokable via package-private/reflection, so the AWT dispatch is not the risk.

**No production dead code found** (contrast Sessions 3–5): `ScoreActions` is a live interface implemented by `ScoreView`; `activateLine`'s cross-line path is "dead from the test suite's perspective" — a coverage gap, not unreachable production code.

**Production observation filed as a tracked GitHub issue (#411; do not fix during audit):** `VerticalAdjustment.drag()` derives `diffY` from AWT **pixel** mouse coordinates (`endPoint.y`, from `MouseEvent.getY()`) and adds it — with no `pxToSs` conversion — to staff-space `…Ss` fields (`getAttributionStartYSs`/`getTopPaddingSs`/`getRowHeightAdjustmentSs`/`getUserYOffsetSs`/`getYPositionSs`/`getYShiftSs`/`getVerticalPositionSs`, lines 146–240), while line 207 adds the same value to the legacy pixel field `setYPosPx`. Verified against the unit-conversion guide — off by ~8× at `DEFAULT_PIXELS_PER_STAFF_SPACE = 8.0`, and the `rect.y`-from-`(int) getTopSs()` assignments (line 387+) make the subtraction itself unit-mixed; exact runtime severity needs a repro during remediation.

