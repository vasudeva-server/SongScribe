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
  ├─ registerLastInsertionBinding(keyStroke, XxxCommand::new)
  │    isEnabled = mode == EDIT && !graceInProgress && !pasteInProgress
  │       ├── false ─► key not consumed ─► root pane ─► the action's accelerator
  │       └── true  ─► MessageCenter.post(XxxCommand)
  │
  └─ ScoreViewController.handleXxx
        └── handleLastInsertionCommand(undoOpName, shouldBeep)
              ├── PlaybackController.isPlaying ──────────────► beep
              ├── EditModeManager.getLastInsertion() == null ─► beep
              └── setPendingOpName(undoOpName)
                    └── shouldBeep.test(insertion)
                          └── the operation
                                ├── refuses ─► true  ─► beep
                                └── commits ─► armInsertion(line, index), mutate
                    restore prior pending op-name (finally)
```

---

## 1. Arming only on committing paths

`EditModeManager` holds two slots. `armInsertion` fills `pendingInsertion`; the next
`SongDidChangeNotification` promotes it to `lastInsertion` and clears the pending slot
(`EditModeManager.songDidChange`). That promotion is unconditional — **any** song change consumes
whatever is pending, not just the one the arm was meant for.

So an arm made before the operation decides would survive a refusal and be adopted by the next
unrelated edit, silently re-pointing the key at an element the user never placed. Hence the rule:

> `armInsertion` is called **inside** the branch that mutates, immediately before the mutation —
> never in the handler, and never ahead of the eligibility gate.

`handleLastInsertionCommand` therefore never arms. It leaves that to the operation, which is the
only party that knows whether a bracket will open. `EditModeManager.previewElementDidChange`
observes the same rule from the other side: it skips arming while mutation tracking is suspended,
because grace-note placement posts nothing to consume the arm.

The keys re-arm the same target they acted on, which is what makes them repeat — press `t` twice
and the pair ties then unties.

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

## 3. Undo labels: pass the op-name

These commands are posted straight from the input handler, bypassing the `UIAction` template that
normally sets the Tier-A undo op-name. Without help, the step falls through to
`UndoController.opNameKey`'s mutation-kind fallback and the key labels the *same edit* differently
from the menu action that also performs it — "Undo Beaming" against the menu's "Undo Toggle Beam".

`handleLastInsertionCommand` takes the op-name and brackets the operation with
`UndoController.setPendingOpName`, restoring the prior value in a `finally`, exactly as
`UIAction.actionPerformed` does. Each key passes:

- **Tier A** — a resolved `Strings` label, matching what its menu action declares
  (`ACTION_EDIT_OP_TOGGLE_BEAM`, `ACTION_EDIT_OP_TOGGLE_TIE`).
- **Tier B** — `null`, when the operation labels its own bracket. `SlideOperations` does, via
  `OpNames.addSlideLabel` / `OpNames.deleteSlideLabel`, so glissando and fall must not have a
  static label imposed on them. `ToggleNotationAction` passes `null` for the same two, for the
  same reason.

`MutationLabelTest.testTieKeyLabelsTheUndoStepTheSameWayTheMenuActionDoes` and its beam twin
pin this down by driving the real handler and asserting the real label.

---

## Adding a fifth key

1. A `Message` subclass in `songscribe.message.command`.
2. One `registerLastInsertionBinding` call in `ScoreInputHandler.installKeyBindings`. Check the
   key is not in `KEY_CODES`, and bump `expectedBindingCount` in `ScoreInputHandlerTest`.
3. A `@Handler` in `ScoreViewController` delegating to `handleLastInsertionCommand`, passing the
   op-name (§3) and a predicate that returns *whether a beep is still owed* — `false` when the
   operation already gave feedback of its own. The `SlideOperations.Result` three-way
   (`MODIFIED` / `REFUSED` / `REPORTED`) exists for exactly that: `REPORTED` has already put an
   error dialog on screen, and beeping as it is dismissed reads as a second, separate failure.
4. A static operation taking `(Line, int)` — no coordinator, unit-testable against a bare
   `Song`/`Line` — that returns whether it changed the line and arms per §1.
