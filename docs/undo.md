# Undo / Redo — Architectural Reference

Background and rationale for the undo/redo engine (issue #14). The implementation
plan lives in `plans/14-undo-redo.md`; this document captures the design
decisions behind it — the "why" an implementer does not need in front of them,
but which explains the shape of the engine after the fact.

------------------------------------------------------------------------

## Runtime flow

### Recording a forward edit

```
 forward edit                UndoController                    Song / Line

 user edits ──▶ Song.endModification ──▶ SongDidChangeNotification
                                          │  (applyingReplay == false)
                                          ▼
                                   push UndoStep(mutations, opName) onto undoStack
                                   clear redoStack
                                   evict oldest if size > undoStackMaxDepth
                                   post UndoStateDidChangeNotification
```

### Edit-menu label (`composeLabel`, per direction)

```
        step = stack.peek()
        step == null            ──▶ "Undo" / "Redo"            (empty stack)
        step.opName != null     ──▶ "Undo <declared op-name>"  (declared, verbatim)
        step.opName == null     ──▶ "Undo <type-based label>"  (fallback via
                                    opNameKey(dominantMutation(step.mutations)))
```

### Undo

```
        peek step from undoStack
        applyingReplay = true
        song.withModification(() -> song.withReplay(() ->
            for m in reverse(step): MutationReplayer.applyUndo(scoreView, m)))
        applyingReplay = false          ──▶ posts a SongDidChangeNotification
                                            (handler sees applyingReplay==true → ignores)
                                            (ScoreViewController still repaints from it)
        on success: pop from undoStack, push onto redoStack
        recompute modified vs clean
        post UndoStateDidChangeNotification
```

### Redo

```
        peek step from redoStack
        applyingReplay = true
        song.withModification(() -> song.withReplay(() ->
            for m in forward(step): MutationReplayer.applyRedo(scoreView, m)))
        applyingReplay = false
        on success: pop from redoStack, push onto undoStack
        recompute modified vs clean
        post UndoStateDidChangeNotification
```

## Where an undo step's op-name comes from

Two tiers declare the name that ends up on an `UndoStep`.

**Tier A — `UIAction`.** `actionPerformed` is a `final` template. It sets
`UndoController.setPendingOpName(getUndoOpName())`, calls the subclass's
`performAction` hook, and restores the prior pending name in a `finally`. Any
modification bracket that opens *synchronously* inside that dispatch window captures
the pending name as it goes from depth 0 to depth 1. Modal dialogs block, so they
qualify. A bracket opened off-stack — `invokeLater`, a `Timer`, a `SwingWorker`, or a
non-modal dialog — opens after the `finally` has already cleared `pendingOpName`.

**Tier B — the labeled overload.** Those off-stack sites call
`withModification(String label, Runnable)` and declare their name directly.

**Inside the bracket.** On the 0 → 1 depth transition the session captures
`label != null ? label : pendingOpName`. Each `applyChange(mutation, mutator)` runs
its mutator and appends the mutation to the accumulated list, throwing if the depth is
0 (no open bracket). When the outermost bracket closes, the depth returns to 0 and the
session posts `SongDidChangeNotification(accumulatedMutations, capturedOpName)`.

------------------------------------------------------------------------

## Why a normal bracket during replay (not `withoutMutationTracking`)

The replay bracket posts a `SongDidChangeNotification` so
`ScoreViewController.songDidChange` repaints/relayouts for free, and the helpers
record inverse mutations into the batch so the notification faithfully describes
what changed.

The **reentrancy guard** (`applyingReplay`) keeps the replay batch from being
pushed as a new step — this is the "production-safe suppression distinct from the
test-only `withoutMutationTracking`" the issue asks for. The **replay mode**
(`song.withReplay`) is orthogonal: it suppresses companion side-work and bypasses
guards inside the model.

`withoutMutationTracking` is a full-suspension mechanism (records nothing at all;
used by production file-load — `MusicXmlReader`, `SongIO`, `ScoreView.setSong` —
**and** test setup, not "tests-only") and is not used by the engine.
`MessageCenter.post` is synchronous, so the guard is race-free on the EDT.

------------------------------------------------------------------------

## Why a custom controller (not `javax.swing.undo.UndoManager`)

The codebase uses no `javax.swing.undo` type today, and this engine deliberately
does not adopt one. `UndoManager` / `UndoableEdit` would replace only the stack
mechanics — a few lines of `ArrayDeque` here — while every hard part is required
regardless and is specific to this app: per-type mutation inversion
(`MutationReplayer`), replay under the model's replay mode, reference-based
save-point / `modified` tracking, dominant-mutation step labels, and integration
with the **MBassador** message bus rather than Swing's `UndoableEditListener`
model. Wrapping each `SongDidChangeNotification` batch as an `UndoableEdit` would
add an adapter layer without removing any of that work. A thin custom controller
over the existing `Mutation` foundation is the smaller, more direct design.
