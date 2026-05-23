### 7A. Score-view control plane (ScoreViewController + input dispatch)

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| ScoreViewController | `deleteNote` removes a single element and decrements element count | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteRemovesOneElement` | adequate | keep |
| ScoreViewController | `deleteNote` removes preceding paired grace note and returns count 2 | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteRemovesPrecedingPairedGraceNote` | adequate | keep |
| ScoreViewController | `deleteNote` does NOT remove an unpaired (standalone) grace note | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteDoesNotRemoveUnpairedGraceNote` | adequate | keep |
| ScoreViewController | `deleteNote` strips incoming glissando from the previous note | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteRemovesGlissandoFromPreviousNote` | adequate | keep |
| ScoreViewController | `deleteNote` shifts subsequent elements by the correct offset when a paired grace note is also deleted | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteShiftsSubsequentElementsWhenGraceNoteRemoved` | adequate | keep |
| ScoreViewController | `deleteSelection` loop skips the grace-note index to avoid double-processing | unit | `ScoreViewControllerTest.DeleteNote.testSelectionLoopSkipsGraceNoteIndex` | adequate | keep |
| ScoreViewController | `deleteSelection` loop terminates correctly when grace pair straddles selection start | unit | `ScoreViewControllerTest.DeleteNote.testSelectionLoopWithGraceNoteAtSelectionStart` | adequate | keep |
| ScoreViewController | `deleteSelection` removes grace note that precedes the selection | unit | `ScoreViewControllerTest.DeleteNote.testSelectionLoopWithGraceNoteBeforeSelection` | adequate | keep |
| ScoreViewController | `deleteNote` breaks syllable relation when deleted note is a syllable terminus | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNoteBreaksSyllableRelationWhenDeletedNoteIsTerminus` | adequate | keep |
| ScoreViewController | `deleteNote` preserves syllable relation when deleted note is a chain member (not terminus) | unit | `ScoreViewControllerTest.DeleteNote.testDeleteNotePreservesSyllableRelationWhenDeletedNoteIsChainMember` | adequate | keep |
| ScoreViewController | `handleDelete` on lyric selection removes the lyric and clears the lyric selection | unit | `ScoreViewControllerTest.DeleteLyric.testDeleteRemovesSelectedLyricAndClearsLyricSelection` | inadequate | strengthen — invokes `handleDelete` via raw reflection instead of widening to package-private; also only covers the lyric branch |
| ScoreViewController | `handleDelete` on element selection with contiguous range: strips glissando from preceding note, shifts elements, cleans syllable relations, removes range | unit | — | missing | write unit test (widen `handleDelete` to package-private; use `ReflectionTestHelper`) |
| ScoreViewController | `handleDelete` on element selection with paired-grace-note at selection start: falls back to per-element loop via `deleteSelection` | unit | — | missing | write unit test |
| ScoreViewController | `handleDelete` on glissando selection: removes the glissando from the source element | unit | — | missing | write unit test |
| ScoreViewController | `handleDelete` when no element or glissando selection and `canDeleteLine()` is true: removes the selected line | unit | — | missing | write unit test |
| ScoreViewController | `handleDelete` confirms before deleting when selection invalidates an ending | unit | — | missing | write unit test (mock `EndingConfirms.confirmInvalidation()` returning false → no deletion) |
| ScoreViewController | `handleCopy` copies selected element range into `ClipboardManager` (correct bounds) | unit | — | missing | write unit test |
| ScoreViewController | `handleCopy` is a no-op when there is no active element selection | unit | — | missing | write unit test |
| ScoreViewController | `handleCut` copies selection then deletes it (delegates to `handleCopy` + `handleDelete`) | unit | — | missing | write unit test |
| ScoreViewController | `handlePaste` is a TODO-only stub — `PasteboardOpCommand(PASTE)` is a silent no-op | unit | — | missing | record as known defect; write a test that asserts clipboard contents are inserted once implemented |
| ScoreViewController | `handlePasteboardOp` routes CUT/COPY/DELETE/PASTE to correct handler | unit | — | missing | write unit test (mock `score.isFocusOwner()` true/false; verify each branch) |
| ScoreViewController | `handlePasteboardOp` is a no-op when `score.isFocusOwner()` returns false | unit | — | missing | write unit test |
| ScoreViewController | `handleDeselect` calls `score.deselect()` only when score has focus | unit | — | missing | write unit test (both focus states) |
| ScoreViewController | `handleSelectLine` calls `state.selectAll()` and notifies score when active selection exists | unit | — | missing | write unit test |
| ScoreViewController | `handleSelectLine` is a no-op when no active selection | unit | — | missing | write unit test |
| ScoreViewController | `handleInsertLine` inserts line at `selectedLine + shift` when a line is selected | unit | — | missing | write unit test |
| ScoreViewController | `handleInsertLine` with `shift == ADD` inserts line at end regardless of selection | unit | — | missing | write unit test |
| ScoreViewController | `handleInsertLine` shows an error dialog when no line is selected and shift != ADD | unit | — | missing | write unit test (mock `OptionDialogs`) |
| ScoreViewController | `modeDidChange`: clears selection when mode != SELECT | unit | — | missing | write unit test |
| ScoreViewController | `modeDidChange`: syncs preview element on EDIT entry | unit | — | missing | write unit test (verify `score.setPreviewElement` called) |
| ScoreViewController | `modeDidChange`: enables/disables horizontal and vertical adjustment controls per mode | unit | — | missing | write unit test |
| ScoreViewController | `hasLineLayoutMutation` returns true for `LineScopedMutation`, `LineInsertion`, `LineDeletion`; false otherwise | unit | `ScoreViewControllerTest.LayoutInvalidation` (6 tests) | adequate | keep |
| ScoreViewController | `hasFullRelayoutMutation` returns true for `FontChange`, `MetadataChange`, `LayoutChange`; false for others | unit | — | missing | write unit test |
| ScoreViewController | `songDidChange` calls `lineComponent.invalidateLayout()` only for the target line when mutation is line-scoped with a non-null target | unit | — | missing | write unit test |
| ScoreViewController | `songDidChange` calls `score.viewChanged()` when full-relayout mutation is present | unit | — | missing | write unit test |
| ScoreViewController | `songDidChange` restarts repaint debounce timer | unit | — | missing | write unit test (capture timer restart; verify score.repaint() eventually called) |
| ScoreViewController | `canToggleTuplet` returns cached value when cache is populated; delegates to `operations` when null | unit | — | missing | write unit test |
| ScoreViewController | `warmTupletCache` is invoked on `MusicSelectionDidChangeNotification` before HIGH_PRIORITY handlers | unit | — | missing | write unit test (verify priority ordering: cache is warm when TupletAction reads it) |
| ScoreViewController | `prefsDidChange(LOOP_PLAYBACK)` calls `scoreActions.syncPlaybackPrefs()` | unit | — | missing | write unit test |
| ScoreViewController | `prefsDidChange(PAGE_SIZE)` calls `scoreActions.updatePageLayout()` when score is initialized | unit | — | missing | write unit test |
| ScoreViewController | `prefsDidChange(ALL)` triggers both `syncPlaybackPrefs` and `updatePageLayout` | unit | — | missing | write unit test |
| ScoreViewController | `textEditingDidChange` disables key bindings when editing; enables when not | unit | — | missing | write unit test (both true/false) |
| ScoreViewController | `handleToggleBeam` delegates to `operations.toggleBeaming()` and emits one `BeamingAddition` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleBeamEmitsOneBeamingAddition` | adequate | keep |
| ScoreViewController | `handleToggleBeam` is a no-op when there is no active selection | unit | — | missing | write unit test |
| ScoreViewController | `handleToggleTie` delegates to `operations.toggleTie()` and emits one `TieAddition` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTieEmitsOneTieAddition` | adequate | keep |
| ScoreViewController | `handleToggleTuplet` emits a `TupletAddition` for a fresh triplet | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTupletEmitsOneTupletAddition` | adequate | keep |
| ScoreViewController | `handleToggleTuplet` emits `TupletRemoval` + `TupletAddition` when changing tuplet grade | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTupletEmitsRemovalAndAdditionForGradeChange` | adequate | keep |
| ScoreViewController | `handleAddDynamics` emits `CrescendoAddition` or `DiminuendoAddition` per flag | unit | `ScoreViewControllerCommandHandlerTest.testHandleAddDynamicsEmitsOneAddition` | adequate | keep |
| ScoreViewController | `handleRemoveDynamics` emits at least one removal mutation | unit | `ScoreViewControllerCommandHandlerTest.testHandleRemoveDynamicsEmitsRemovals` | inadequate | strengthen — asserts only `isNotEmpty()`; should assert specific removal mutation types and count |
| ScoreViewController | `handleToggleTrill` emits one `RangeElementAddition` | unit | `ScoreViewControllerCommandHandlerTest.testHandleToggleTrillEmitsOneNotificationWithSingleRangeElementAddition` | adequate | keep |
| ScoreViewController | `handleFlipStemDirection` emits one `ElementModification` per selected note with `ElementField.UPPER` | unit | `ScoreViewControllerCommandHandlerTest.testHandleFlipStemDirectionEmitsOneNotificationWithModificationsPerNote` | adequate | keep |
| ScoreViewController | `handleFirstSecondEnding` emits `ElementInsertion` + `RangeElementAddition(Ending)` when cached result is valid | unit | `ScoreViewControllerCommandHandlerTest.testHandleFirstSecondEndingEmitsOneNotificationWithBarlineAndEnding` | adequate | keep |
| ScoreViewController | `handleFirstSecondEnding` is a no-op when cached result is null or invalid | unit | — | missing | write unit test |
| ScoreViewState | All getters/setters (mode, control, horizontalAdjustment, verticalAdjustment) | none | — | — | no test needed — pure data holder |
| ScoreInputHandler | `mouseClicked` with non-BUTTON1 event: no focus request | e2e | `SelectionTest.ClickAndMode` (implicit) | wrong-level | lower to unit — mock callback, construct real `MouseEvent`, assert `requestFocusInWindow` not called |
| ScoreInputHandler | `mouseClicked` with BUTTON1: calls `callback.requestFocusInWindow()` | e2e | `SelectionTest.ClickAndMode` (implicit) | wrong-level | lower to unit — mock callback, construct real `MouseEvent`, assert `requestFocusInWindow` called |
| ScoreInputHandler | `mousePressed` / `mouseReleased`: shows popup when `isPopupTrigger()` | e2e | — | missing | write unit test (mock callback; stub popup non-null; verify `popup.show()` called) |
| ScoreInputHandler | `mousePressed` / `mouseReleased`: is a no-op when `isPopupTrigger()` is false | unit | — | missing | write unit test |
| ScoreInputHandler | `keyPressed(ALT)`: clears preview element and sets `altPressed=true`, triggers repaint | e2e | — | missing | write unit test (mock callback; verify `repaint()` called) |
| ScoreInputHandler | `keyPressed(ESCAPE)` in SELECT mode with no active text editing: posts `DeselectCommand` | e2e | `SelectionTest.BasicSelection.testClickEmptySpaceDeselects` (indirect) | wrong-level | lower to unit — mock callback returning `Mode.SELECT`; verify `MessageCenter.post(DeselectCommand)` |
| ScoreInputHandler | `keyPressed(ESCAPE)` when grace mode is in progress: delegates to grace mode manager | unit | — | missing | write unit test |
| ScoreInputHandler | `keyReleased(ALT)`: sets `altPressed=false` | unit | — | missing | write unit test |
| ScoreInputHandler | `KeyAction.handlePitchAdjustment` UP: decrements staff position if above lower bound | unit | — | missing | write unit test (mock callback returning EDIT/KEYBOARD; verify staffPosition decremented) |
| ScoreInputHandler | `KeyAction.handlePitchAdjustment` DOWN: increments staff position if below upper bound | unit | — | missing | write unit test |
| ScoreInputHandler | `KeyAction.handlePitchAdjustment`: no-op when mode != EDIT or control != KEYBOARD | unit | — | missing | write unit test |
| ScoreInputHandler | `installKeyBindings` registers one binding per key code in component input/action maps | unit | — | missing | write unit test (construct real `JComponent`; verify map sizes and action types) |
| InputUtils | `CustomDocumentFilter.insertString` rejects text not matching regex; emits beep | unit | — | missing | write unit test (construct filter with pattern; invoke `insertString`; verify beep and no insertion) |
| InputUtils | `CustomDocumentFilter.insertString` accepts text matching regex | unit | — | missing | write unit test |
| InputUtils | `CustomDocumentFilter.replace` rejects non-matching replacement; accepts matching | unit | — | missing | write unit test |
| InputUtils | `DecimalDocumentFilter.insertString` rejects insertion that would make document non-decimal | unit | — | missing | write unit test |
| InputUtils | `DecimalDocumentFilter.replace` rejects replacement that would make document non-decimal | unit | — | missing | write unit test |
| InputUtils | `DecimalDocumentFilter` accepts partial decimal in progress (e.g. "1.", "0.5") | unit | — | missing | write unit test |
| InputUtils | `RegexFormatter.stringToValue` throws `ParseException` when input does not match pattern | unit | — | missing | write unit test |
| InputUtils | `RegexFormatter.stringToValue` returns value when input matches | unit | — | missing | write unit test |
| InputUtils | `addNumericFilter(component)` installs integer-only document filter on `JTextField` | unit | — | missing | write unit test |
| InputUtils | `addNumericFilter(component, true)` installs decimal-allowing filter on `JTextField` | unit | — | missing | write unit test |
| InputUtils | `addInputFilter` on `JSpinner` installs `RegexFormatter` on the spinner's text field | unit | — | missing | write unit test |
| InputHandlerCallback | Interface — pure abstraction with no logic | none | — | — | no test needed |

**7A notes (quality concerns):**

The most significant defect confirmed by this audit is `handlePaste()`, which is a body-only TODO comment — every `PasteboardOpCommand(PASTE)` is silently swallowed with no effect, making the paste feature completely non-functional. Sessions 5 and 6 flagged `PasteAction` as a silent no-op; this audit confirms the root cause is in `ScoreViewController.handlePaste()`. There is no existing test for it, and the correct action is to both document the defect and write a failing test that guards against the stub remaining once the implementation lands.

Two broader quality concerns apply across the suite. First, the sole `handleDelete` unit test (`DeleteLyric`) invokes the private method via raw `java.lang.reflect` rather than widening it to package-private — this couples the test to the internal method name and access level and means a rename or access-change silently breaks the test at runtime rather than compile-time. Second, `testHandleRemoveDynamicsEmitsRemovals` asserts only `isNotEmpty()` on the mutation list, which would pass if the implementation emitted unrelated mutation types; it should assert specific removal-mutation classes and count.

The `ScoreInputHandler` key-press and mouse-click behaviors are logically unit-testable (the `InputHandlerCallback` interface is already designed for mocking) but are either untested or buried in e2e at the wrong level. `InputUtils` filter logic — particularly the `DecimalDocumentFilter` prospective-text validation and `CustomDocumentFilter` regex gating — is pure state-logic with no Swing dependencies and is entirely untested, representing a concrete gap in a subsystem that guards all text input fields across the application.

