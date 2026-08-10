# The last-insertion key family

In edit mode, four plain keys act on **the element just placed** rather than on a selection:

| Key | Command | Operation | Falls through to |
| --- | --- | --- | --- |
| `b` | `ToggleBeamWithPreviousCommand` | `MusicEditOperations.toggleBeamWithPredecessor` | `TOGGLE_BEAM_ACTION` |
| `t` | `ToggleTieWithPreviousCommand` | `MusicEditOperations.toggleTieWithPredecessor` | `TOGGLE_TIE_ACTION` |
| Shift+G | `ToggleGlissandoWithPreviousCommand` | `SlideOperations.toggleGlissandoWithPredecessor` | `GLISSANDO_ACTION` |
| `f` | `ToggleFallOnLastInsertionCommand` | `SlideOperations.toggleFallOnLastInsertion` | `FALL_ACTION` |

They share two pieces of machinery — `ScoreInputHandler.registerLastInsertionBinding` and
`ScoreViewController.handleLastInsertionCommand` — so a fifth key is a binding, a command class,
a handler and an operation, with no new plumbing. This note records the three things that are not
obvious from reading any one of them.

---

## Flow

```
KEY pressed, ScoreView focused (WHEN_FOCUSED input map)
  │
  ├─ registerLastInsertionBinding(targetAction, XxxCommand::new)
  │    keystroke = targetAction.getAccelerator()
  │    isEnabled = mode == EDIT && !graceInProgress && !pasteInProgress
  │       ├── false ─► key not consumed ─► root pane ─► targetAction's accelerator
  │       └── true  ─► MessageCenter.post(XxxCommand)
  │
  └─ ScoreViewController.handleXxx
        └── handleLastInsertionCommand(targetAction, operation)
              ├── PlaybackController.isPlaying ──────────────► beep
              ├── EditModeManager.getLastInsertion() == null ─► beep
              └── UndoController.withPendingOpNameResult(targetAction.getUndoOpName(), …)
                    └── the operation, whose EditResult decides the response:
                          ├── MODIFIED ─► EditModeManager.setLastInsertion(line, index)
                          ├── REFUSED  ─► beep
                          └── REPORTED ─► say nothing; a dialog is already up
```

---

## 1. The target is re-pointed after the fact, not armed ahead of time

`EditModeManager` holds two slots. `armInsertion` fills `pendingInsertion`; the next
`SongDidChangeNotification` promotes it to `lastInsertion` and clears the pending slot
(`EditModeManager.songDidChange`). That promotion is unconditional — **any** song change consumes
whatever is pending, not just the one the arm was meant for. So an arm made before an operation
decides would survive a refusal and be adopted by the next unrelated edit, silently re-pointing
the key at an element the user never placed.

Placement has to arm, because it names its target from inside a bracket that has not closed yet.
The keys do not: message posting is synchronous, so by the time an operation returns, its bracket
has closed and the notification has already been delivered. The handler therefore *observes* the
outcome and calls `EditModeManager.setLastInsertion` on `MODIFIED`, writing the visible slot
directly.

That is what makes the keys repeat — press `t` twice and the pair ties, then unties — and it is
why the operations themselves touch neither slot. `armInsertion` is private to `EditModeManager`
for the same reason: the only caller left is `previewElementDidChange`, the placement path.

`previewElementDidChange` still has to gate its own arm: it skips arming while mutation tracking
is suspended, because grace-note placement posts nothing to consume the arm.

The three outcomes are `EditResult`, and an operation must be able to tell them apart honestly —
returning a literal "modified" it has not checked would beep on nothing, or leave the key pointed
at an element the edit never touched.

## 2. Disabled so the key falls through

The binding lives on the focused `ScoreView` (`WHEN_FOCUSED`), not on the action, because the
target action is *disabled* in edit mode and Swing skips a disabled binding in the window input
map — it could never even report failure.

That precedence is why the mode guard is an **`isEnabled` override** and not an early return from
`actionPerformed`. Swing invokes an enabled binding and stops searching, so a guard that ran and
did nothing would still swallow the key — leaving select mode, where the score view also holds
focus, with no working shortcut at all. Reporting the binding as *disabled* lets the search
continue to the root pane, where the action's own accelerator lives.

The guard is `mode == EDIT && !graceModeInProgress && !pasteModeInProgress`. Grace and paste mode
own the keyboard while in progress, matching the precedence the Escape branch establishes.

Because the fall-through only lands on the right command while the two keystrokes are identical,
the binding is registered with `targetAction.getAccelerator()` rather than a keystroke written out
again here. Change the menu shortcut and the key follows it.

## 3. Undo labels: read them off the action

These commands are posted straight from the input handler, bypassing the `UIAction` template that
normally sets the Tier-A undo op-name. Without help, the step falls through to
`UndoController.opNameKey`'s mutation-kind fallback and the key labels the *same edit* differently
from the menu action that also performs it — "Undo Beaming" against the menu's "Undo Toggle Beam".

`handleLastInsertionCommand` brackets the operation with `UndoController.withPendingOpName`,
passing `targetAction.getUndoOpName()`. The action is the single declaration of that label, so the
key and the menu entry agree by construction — including for glissando and fall, where the action
answers `null` because `SlideOperations` labels its own bracket via `OpNames.addSlideLabel` /
`OpNames.deleteSlideLabel` and must not have a static label imposed on it.

`MutationLabelTest.testTieKeyLabelsTheUndoStepTheSameWayTheMenuActionDoes` and its beam twin
pin this down by driving the real handler and asserting the real label.

---

## Adding a fifth key

1. A `Message` subclass in `songscribe.message.command`.
2. One `registerLastInsertionBinding` call in `ScoreInputHandler.installKeyBindings`, passing the
   menu action the key falls through to. Check the key is not in `KEY_CODES`.
3. A `@Handler` in `ScoreViewController` delegating to `handleLastInsertionCommand`, passing that
   same action and a function that performs the edit and returns its `EditResult`. Nothing else
   is needed: the label (§3), the beep and the re-pointing (§1) all follow from the action and
   the outcome.
4. A static operation taking `(Line, int)` — no coordinator, no singleton, unit-testable against
   a bare `Song`/`Line` — that reports what it actually did.
5. A row in each of the two key tables: `LastInsertionKeyBindings.keys()` in
   `ScoreInputHandlerTest` (which keystroke, which command) and `lastInsertionKeys()` in
   `ScoreViewControllerCommandHandlerTest` (which operation, which handler). Both files run
   every scenario over their table, so a row is all the wiring coverage the key needs; add it
   to `selfReportingKeys()` as well if the operation can report its own error.
