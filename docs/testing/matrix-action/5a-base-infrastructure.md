### 5A. base/infrastructure — UIAction, SelectableUIAction, StickyUIAction, ControlAction, ActionGroup, DurationActionGroup, NonDurationActionGroup, Actions, ModeAction, LaunchAction, DialogOpenAction

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| UIAction | Constructor rejects null mainFrame | unit | `UIActionFlagBehaviorTest.testConstructorRejectsNullMainFrame` | adequate | keep | — |
| UIAction | `setFlags` disables on construction when REQUIRES_SELECTION / REQUIRES_SINGLE_SELECTION / REQUIRES_MULTIPLE_SELECTION / DISABLE_WHEN_SONG_EMPTY | unit | — | missing | write test: construct with each of those four flags; assert `isEnabled() == false` | ⬜ |
| UIAction | `hasFlag` detects a set vs. unset flag | unit | `UIActionFlagBehaviorTest` (implicit via updateEnabledState calls) | adequate (indirect) | keep | — |
| UIAction | `updateEnabledState` returns false and disables when `getScoreView()` is null | unit | — | missing | write test: stub `getScoreView()` → null; assert returns false and `isEnabled() == false` | ⬜ |
| UIAction | `enableInAdjustmentMode` — DISABLE_IN_ADJUSTMENT_MODE + adjustment mode disables; no flag or non-adjustment mode enables | unit | `UIActionReflectableGuardTest.testNonReflectableWithSelectionRunsNormalLogic` (DISABLE_IN_ADJUSTMENT_MODE only) | adequate | keep | — |
| UIAction | `enableInSelectMode` — DISABLE_IN_SELECT_MODE + isInSelectMode disables | unit | — | missing | write test: stub `isInSelectMode()` → true; assert false; and false when not in select mode | ⬜ |
| UIAction | `enableFromSelectionSize` — REQUIRES_SELECTION: size 0 → false, size > 0 → true | unit | — | missing | write test for each size-flag variant (see below rows) | ⬜ |
| UIAction | `enableFromSelectionSize` — REQUIRES_EMPTY_SELECTION: size 0 → true, size > 0 → false | unit | — | missing | write test | ⬜ |
| UIAction | `enableFromSelectionSize` — REQUIRES_SINGLE_SELECTION: size 1 → true, size ≠ 1 → false | unit | — | missing | write test | ⬜ |
| UIAction | `enableFromSelectionSize` — REQUIRES_OPTIONAL_SINGLE_SELECTION: size 0 or 1 → true, size > 1 → false | unit | — | missing | write test | ⬜ |
| UIAction | `enableFromSelectionSize` — REQUIRES_MULTIPLE_SELECTION: size > 1 → true, size ≤ 1 → false | unit | `TupletActionTest` (indirect — size=2 case only) | inadequate | write direct test covering both branches | ⬜ |
| UIAction | `enableFromSelectionSize` — REQUIRES_OPTIONAL_MULTIPLE_SELECTION: size 0 or > 1 → true, size 1 → false | unit | — | missing | write test | ⬜ |
| UIAction | `enableInRestMode` — DISABLE_IN_REST_MODE + REST_ACTION.isSelected → false | unit | — | missing | write test: stub `REST_ACTION.isSelected()==true`; assert false | ⬜ |
| UIAction | `enableInRestMode` — DISABLE_IN_REST_MODE + selectionHasRests → false | unit | — | missing | write test: stub `selectionHasRests()==true`; assert false | ⬜ |
| UIAction | `enableInRestMode` — no flag → always true | unit | — | missing | write test | ⬜ |
| UIAction | `enableFromPlaybackState` — DISABLE_WHEN_PLAYING + isPlaying → false; no flag → true | unit | — | missing | write test mocking `PlaybackController.isPlaying()` | ⬜ |
| UIAction | `enableFromDialogVisibility` — OPENS_DIALOG + dialog visible → false; dialog closed → true; no flag → true | unit | `UIActionFlagBehaviorTest` (3 tests) | adequate | keep | — |
| UIAction | `enableFromGraceModeState` — DISABLE_IN_GRACE_MODE + `GraceModeManager.isActive()` → false | unit | `UIActionFlagBehaviorTest.testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses` (combined with OPENS_DIALOG) | inadequate | write isolated test for DISABLE_IN_GRACE_MODE alone | ⬜ |
| UIAction | `enableFromTextEditingState` — DISABLE_WHEN_EDITING_TEXT + editing → false; not editing → true | unit | `EditLyricActionTest.testEnableFromTextEditingState*` (via EditLyricAction subclass) | adequate | keep | — |
| UIAction | `enableFromBarSelection` — DISABLE_WHEN_BAR_SELECTED + no active selection + bar selected → false | unit | `EnableFromSelectionTest` (3 tests) | adequate | keep | — |
| UIAction | `enableFromSelection` — non-Reflectable, no active selection → true | unit | `EnableFromSelectionTest.testNoSelectionReturnsTrue` | adequate | keep | — |
| UIAction | `enableFromSelection` — non-Reflectable + DISABLE_WHEN_BAR_SELECTED + selection has durations → true / no durations → false | unit | `EnableFromSelectionTest` (2 tests) | adequate | keep | — |
| UIAction | `enableFromSelection` — non-Reflectable + no DISABLE_WHEN_BAR_SELECTED → true | unit | `EnableFromSelectionTest.testNonReflectableWithoutFlagReturnsTrue` | adequate | keep | — |
| UIAction | `enableFromSelection` — Reflectable, `isApplicableToSelection()` true/false | unit | `EnableFromSelectionTest` (2 tests) | adequate | keep | — |
| UIAction | `enableFromDurationSelection` — ENABLE_WHEN_DURATION_SELECTED + active selection → defers (true) | unit | `EnableFromSelectionTest.testEnableFromDurationSelectionDefersWithActiveSelection` | adequate | keep | — |
| UIAction | `enableFromDurationSelection` — ENABLE_WHEN_DURATION_SELECTED + no selection + grace/glissando/slide-out selected → false | unit | — | missing | write test: set `DURATION_ACTION_GROUP` to `GRACE_EIGHTH_NOTE_ACTION`; assert false; repeat for GLISSANDO_ACTION and SLIDE_OUT_ACTION | ⬜ |
| UIAction | `enableFromDurationSelection` — ENABLE_WHEN_DURATION_SELECTED + no selection + normal duration selected → true | unit | — | missing | write test: set `DURATION_ACTION_GROUP` to `QUARTER_NOTE_ACTION`; assert true | ⬜ |
| UIAction | `enableFromSongState` — DISABLE_WHEN_SONG_EMPTY + song empty → false; non-empty → true | unit | — | missing | write test mocking `score.isInitialized()` and `score.getSong().isEmpty()` | ⬜ |
| UIAction | `enableFromSongState` — no flag → true regardless | unit | — | missing | write test | ⬜ |
| UIAction | `applyToSelectionIfActive` — non-Reflectable → false | unit | `ApplyToSelectionInterceptTest.testNonReflectableReturnsFalse` | adequate | keep | — |
| UIAction | `applyToSelectionIfActive` — Reflectable + no selection → false, no dispatch | unit | `ApplyToSelectionInterceptTest.testReflectableWithNoSelectionReturnsFalse` | adequate | keep | — |
| UIAction | `applyToSelectionIfActive` — Reflectable + active selection → true, dispatches selected state | unit | `ApplyToSelectionInterceptTest` (2 tests: selected=false, selected=true) | adequate | keep | — |
| UIAction | `perform(source)` — delegates to `actionPerformed` with correct ActionEvent | unit | — | missing | write test: override `actionPerformed` in anonymous subclass; assert it was called with correct source | ⬜ |
| UIAction | `setIcon` with null → no-op; SVG path → puts LARGE_ICON_KEY; tagged unicode → puts FONT_ICON_KEY and FONT_KEY | none | — | none | pure display wiring, no branching logic worth testing | — |
| UIAction | `@Handler` methods call `updateEnabledState` (modeDidChange, musicSelectionDidChange, etc.) | none | — | none | trivial delegation to `updateEnabledState`; no independent logic | — |
| UIAction | `dialogVisibilityDidChange` — only calls `updateEnabledState` when OPENS_DIALOG flag present | unit | — | missing | write test: action without flag, post notification; assert `isEnabled()` unchanged (or use spy to verify no call) | ⬜ |
| SelectableUIAction | `isSelected()` defaults false on construction | unit | `ActionsResetOnDocumentLoadTest` (implicit via `isSelected()` assertion post-reset) | inadequate | write direct test: construct `SelectableUIAction` subclass; assert `isSelected() == false` immediately after construction | ⬜ |
| SelectableUIAction | `setSelected(true/false)` updates `SELECTED_KEY` putValue | unit | `DynamicMarkingActionTest.ActionGroupBehavior` (indirect via ActionGroup) | adequate | keep | — |
| SelectableUIAction | `reset()` sets selected to false | unit | `ActionsResetOnDocumentLoadTest` (via resetToDefaults which calls group reset, not action reset directly) | inadequate | write direct test: `action.setSelected(true); action.reset(); assertThat(action.isSelected()).isFalse()` | ⬜ |
| SelectableUIAction | `prefsKey` ctor: `setSelected(Prefs.getBoolean(prefsKey))` when key non-null | unit | — | missing | write test with mocked `Prefs.getBoolean()` returning true; assert `isSelected() == true` | ⬜ |
| StickyUIAction | `doActionPerformed` — source is JRootPane + already selected → returns false (no toggle) | unit | — | missing | write test: create StickyUIAction subclass, `setSelected(true)`, call `doActionPerformed` with JRootPane source; assert returns false and `isSelected()` still true | ⬜ |
| StickyUIAction | `doActionPerformed` — source is JRootPane + not selected → calls `toggleOnKeyboardShortcut`, returns true | unit | — | missing | write test: source JRootPane, `isSelected()==false`; assert returns true and `isSelected()` toggled to true | ⬜ |
| StickyUIAction | `doActionPerformed` — source is NOT JRootPane → always calls toggleOnKeyboardShortcut, returns true | unit | — | missing | write test: source is e.g. a JButton; assert returns true | ⬜ |
| ControlAction | `actionPerformed` — posts `ControlDidChangeNotification` with correct control | unit | — | missing | write test: subscribe handler, call `actionPerformed`; assert correct notification posted | ⬜ |
| ControlAction | Factory methods bind correct `Control` enum value | unit | — | missing | write test asserting `createMouseControlAction().control == Control.MOUSE` etc. | ⬜ |
| ActionGroup | `setSelected(action, true)` deselects previous, updates `selected` | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testSelectingOneActionDeselectsPrevious` | adequate | keep | — |
| ActionGroup | `setSelected(action, false)` on currently-selected clears `selected` | unit | — | missing | write test: `group.setSelected(a, true); group.setSelected(a, false); assertThat(group.getSelected()).isNull()` | ⬜ |
| ActionGroup | `setSelected(action, true)` captures `previousSelected` | unit | — | missing | write test: select A; select B; assert `getPreviousSelected() == A` | ⬜ |
| ActionGroup | `clearSelection` sets `selected` to null, deselects action | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testClearSelectionClearsAll` | adequate | keep | — |
| ActionGroup | `reset` with default action → selects default | unit | `ActionsResetOnDocumentLoadTest` (via `DURATION_ACTION_GROUP.reset` → quarter note) | adequate (indirect) | keep | — |
| ActionGroup | `reset` without default → clears selection | unit | `ActionsResetOnDocumentLoadTest` (e.g. ACCIDENTAL_ACTION_GROUP.reset clears) | adequate (indirect) | keep | — |
| ActionGroup | `add` is idempotent (adding same action twice doesn't duplicate) | unit | — | missing | write test: add same action twice; assert `getActions().size() == 1` | ⬜ |
| ActionGroup | `insert(index, action)` inserts at correct index; idempotent | unit | — | missing | write test | ⬜ |
| ActionGroup | `remove(action)` removes from list and detaches property listener | unit | — | missing | write test: add action, remove it; select it; assert `getSelected() == null` (listener detached) | ⬜ |
| ActionGroup | `contains(action)` true after add, false otherwise | unit | — | missing | write test | ⬜ |
| ActionGroup | `anySelected` — true when at least one action selected | unit | — | missing | write test: group with two Selectable actions; select one; assert true; clear; assert false | ⬜ |
| ActionGroup | `selectNext` — wraps from last to first | unit | — | missing | write test | ⬜ |
| ActionGroup | `selectNext` — advances to next when selected is not last | unit | — | missing | write test | ⬜ |
| ActionGroup | `selectNext` — when selected is null → selects first | unit | — | missing | write test | ⬜ |
| ActionGroup | `select(action, source)` — calls `perform(source)` only when selection changes | unit | — | missing | write test: select same action twice; verify perform called once (first call); second call no-op | ⬜ |
| ActionGroup | `propertyChange` — external `putValue(SELECTED_KEY, true)` on a member is intercepted and enforces exclusivity | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testSelectingOneActionDeselectsPrevious` (only via `setSelected`; `propertyChange` not exercised directly) | inadequate | write test: directly call `action.putValue(SELECTED_KEY, true)` and assert the group's previous selection is cleared | ⬜ |
| ActionGroup | `isSelected(action)` returns true only for currently selected action | unit | `DynamicMarkingActionTest.ActionGroupBehavior.testSelectingOneActionDeselectsPrevious` | adequate | keep | — |
| DurationActionGroup | `barWasSelected` handler clears duration selection at HIGH_PRIORITY | unit | — | missing | write test: select a duration action; post `BarWasSelectedNotification`; assert `DURATION_ACTION_GROUP.getSelected() == null` | ⬜ |
| NonDurationActionGroup | `durationWasSelected` handler clears non-duration selection at HIGH_PRIORITY | unit | — | missing | write test: select a non-duration action; post `DurationWasSelectedNotification`; assert `NON_DURATION_ACTION_GROUP.getSelected() == null` | ⬜ |
| Actions | Static initializer sets QUARTER_NOTE as default for DURATION_ACTION_GROUP | unit | `ActionsResetOnDocumentLoadTest` (verifies reset goes to quarter note) | adequate | keep | — |
| Actions | `resetToDefaults` resets all groups and standalones on `DocumentDidLoadNotification` | unit | `ActionsResetOnDocumentLoadTest.testDocumentDidLoadResetsAllActionStateToDefaults` | adequate | keep | — |
| Actions | `getAppMenuActions` returns all `AppMenuAction` fields with correct native titles | unit | `ActionsAppMenuTest` (3 tests) | adequate | keep | — |
| Actions | `getAppMenuActions` result is cached (same list instance) | unit | `ActionsAppMenuTest.testGetAppMenuActionsReturnsCachedList` | adequate | keep | — |
| ModeAction | Factory methods bind correct `Mode` value | unit | — | missing | write test: `createSelectModeAction(mf).getMode() == Mode.SELECT` etc. | ⬜ |
| ModeAction | `actionPerformed` — posts `ModeDidChangeNotification` with self as source | unit | — | missing | write test: subscribe handler; call `actionPerformed`; assert message posted with correct `ModeAction` instance | ⬜ |
| ModeAction | `toggleOnKeyboardShortcut` toggles when source is JRootPane | unit | — | missing | write test: `setSelected(false)`, call `actionPerformed` with JRootPane source; assert `isSelected() == true` | ⬜ |
| ModeAction | Hard-wired flags: DISABLE_WHEN_PLAYING + DISABLE_IN_GRACE_MODE | unit | — | missing | write test: assert both flags present via `hasFlag()` | ⬜ |
| LaunchAction | `actionPerformed` — spawns ProcessBuilder using current process info plus `app.command` | none | — | none | involves `ProcessHandle` and OS process spawning; untestable without real process machinery; risk is `IOException` silently swallowed (see notes) | — |
| LaunchAction | `App` enum maps correct command suffix (sb/ss) | none | — | none | pure data constant; no logic | — |
| DialogOpenAction | Constructor auto-sets `OPENS_DIALOG` flag | unit | `UIActionFlagBehaviorTest.testDialogOpenActionAutoSetsOpensDialogFlag` | adequate | keep | — |
| DialogOpenAction | Constructor derives `actionCommand` via `toKebabCase(name)` | unit | — | missing | write test: `new DialogOpenAction(mf, "Song Settings", ...)`: assert `getActionCommand().equals("song-settings")` | ⬜ |
| DialogOpenAction | `getDialog` lazy-initializes on first call and caches | unit | — | missing | write test: call `getDialog()` twice; assert both return same non-null instance; also test reflective instantiation failure path returns null | ⬜ |
| DialogOpenAction | `actionPerformed` calls `dialog.setVisible(true)` | none | — | none | pure Swing delegation, no logic | — |

**5A notes (quality concerns):**

**Highest-risk gaps:**

1. `UIAction.enableFromSelectionSize` — the six selection-size flag variants (`REQUIRES_SELECTION`, `REQUIRES_EMPTY_SELECTION`, `REQUIRES_SINGLE_SELECTION`, `REQUIRES_OPTIONAL_SINGLE_SELECTION`, `REQUIRES_MULTIPLE_SELECTION`, `REQUIRES_OPTIONAL_MULTIPLE_SELECTION`) have **zero direct unit coverage**. Each branch is a small predicate (`size == 1`, `size > 1`, etc.) that a surviving mutant (e.g. `>` → `>=`) would trivially expose. The only indirect coverage is `TupletActionTest` which happens to exercise `REQUIRES_MULTIPLE_SELECTION` with size=2 but does not test the boundary (size=1 → false).

2. `UIAction.enableFromDurationSelection` grace/glissando/slide-out branches — the three identity comparisons (`duration != GRACE_EIGHTH_NOTE_ACTION`, `!= GLISSANDO_ACTION`, `!= SLIDE_OUT_ACTION`) are untested at unit level. The e2e test in `ElementInsertionTest` exercises this path but at wrong level for a pure-logic predicate.

3. `DurationActionGroup.barWasSelected` and `NonDurationActionGroup.durationWasSelected` — the mutual-exclusion handlers (both marked `HIGH_PRIORITY`) have **no unit tests** verifying the deselection contract. This is the core correctness guarantee of these two classes.

4. `StickyUIAction.doActionPerformed` — the sticky toggle-suppression logic (JRootPane source + already selected → return false without toggling) has **zero test coverage**.

5. `ActionGroup.propertyChange` — the re-entrancy guard (`selectLevel > 0`) and the external `putValue(SELECTED_KEY, …)` path are exercised only via `setSelected` in `DynamicMarkingActionTest`; the `propertyChange` path itself is never directly tested, meaning the exclusivity wiring via the property listener is unverified.

**Inadequate tests:**

- `UIActionFlagBehaviorTest.testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses` combines `OPENS_DIALOG` and `DISABLE_IN_GRACE_MODE` but never tests `DISABLE_IN_GRACE_MODE` in isolation — if `enableFromGraceModeState` were dropped, this test would still fail for a different reason (`OPENS_DIALOG` path), masking the regression.
- `SelectableUIAction` initial-false default and `reset()` are exercised only via `ActionsResetOnDocumentLoadTest` through the global `Actions` singletons; a direct construction test is needed to distinguish a regression in `SelectableUIAction` itself from a regression in `Actions.resetToDefaults`.

**Name mismatch:**

- `UIActionFlagBehaviorTest.testDialogOpenActionAutoSetsOpensDialogFlag` has a stale comment above the test that reads "-- DialogOpenAction does NOT auto-set OPENS_DIALOG --" but the test title and body assert the opposite (that it does auto-set). This is a doc-comment bug — the test itself is correct.

**Production bug / observation worth filing:**

- `LaunchAction.actionPerformed` silently swallows all exceptions, including `IOException` when the spawned process fails to start. The user gets no feedback. This is worth a GitHub issue: add at minimum a `LOG.error` (or user-facing notification) in the catch block.
- `UIAction.dialogVisibilityDidChange` only calls `updateEnabledState()` when `hasFlag(OPENS_DIALOG)`. This is intentional per the source comment, but there is no test asserting the negative case (an action *without* `OPENS_DIALOG` is unaffected by this notification), leaving this guard invisible to tests.

