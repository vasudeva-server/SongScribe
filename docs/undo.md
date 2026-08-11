# Undo / Redo — Architectural Reference

What the undo/redo engine promises the rest of the application, and the design
decisions behind its shape (issue #14). The promises below span subsystems — the
model records, the engine replays, the selection follows — so no single class can
state them; `UndoController` and `MutationReplayer` cite this document rather than
paraphrasing it.

------------------------------------------------------------------------

## What the engine guarantees

**Round trip.** Undoing a step leaves the document in exactly the state it held
before that step's edit; redoing leaves it in exactly the state it held after.
This holds for any sequence of undos and redos, not just one.

The engine can only restore what was recorded, so this promise is one half of a
bargain with the model: the other half is the **complete-emission invariant** in
[`mutations.md`](./mutations.md) — every state change made inside a modification
bracket is recorded as a `Mutation` in that bracket's batch. A helper that drops
dependent state without routing it through a tracked removal breaks undo, and
breaks it silently, because the batch still replays cleanly with the unrecorded
change simply missing.

**Element identity.** Undo and redo never swap one `StaffElement` instance for
another where the recorded change was a modification: state is copied back in
place. Everything holding a reference to an element — spans on the line, the
current selection, mutations still sitting on either stack — therefore stays
valid across arbitrary undo/redo interleaving. This is why `ElementModification`
carries before and after clones rather than a replacement element, and why
replay uses `copyStateFrom` rather than `setElement`.

**A live selection.** Replaying a step mutates the line under whatever the user
has selected, and the selection names elements by index. `SelectionCoordinator`
splices the selected range through each mutation of the batch as it replays, so
the range goes on naming the same surviving elements — sliding or shrinking as
needed, and clearing only when nothing of it survives. What makes that safe for
*other* subscribers is ordering: the splice runs from
`ScoreViewController.songDidChange` at `TUPLET_INFO_CACHE_PRIORITY`, ahead of
every other reader of the range, so no handler ever sees indices that no longer
exist. A subscriber that outranks that priority and reads the selection is
outside the guarantee.

**One edit, one Undo.** A step is one outermost modification bracket, however
many mutations it accumulated and however deeply it nested. What counts as one
edit is decided by whoever opens the outermost bracket — a dialog that commits
five fields in one bracket is one Undo, and that is a design choice made at the
call site, not by the engine.

**A named edit.** The Edit menu names the operation rather than showing a bare
"Undo": the op-name its initiator declared (see *Where an undo step's op-name
comes from* below), or a name derived from the kind of edit the step's dominant
mutation records.

**A truthful modified flag.** The document is modified exactly when its position
in the history differs from the position at the last save. Clean is a *position*,
not a comparison of content: editing and undoing back returns to clean, while
reaching an identical document by another route does not.

Two things end that promise, both deliberately. Beyond
`UndoController.UNDO_STACK_MAX_DEPTH` steps the oldest is evicted, and if the
evicted step was the saved position, the document can no longer reach clean and
stays modified. A replay that throws is an engine bug that leaves the model
mid-step and matching neither stack; the history is cleared and the document
forced modified, so the user can still save their work but cannot undo further
into a state the engine can no longer account for.

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

**Tier A — `UIAction`.** `actionPerformed` is a `final` template. It runs the
subclass's `performAction` hook inside `UndoController.withPendingOpName(getUndoOpName(),
…)`, which sets the name and restores the prior one in a `finally`. Any modification
bracket that opens *synchronously* inside that dispatch window captures the pending name
as it goes from depth 0 to depth 1. Modal dialogs block, so they qualify. A bracket
opened off-stack — `invokeLater`, a `Timer`, a `SwingWorker`, or a non-modal dialog —
opens after the `finally` has already restored `pendingOpName`.

Two paths reach a bracket without going through the template — paste placement, driven
by a click or Return rather than a Cmd+V dispatch, and the last-insertion keys, posted
straight from `ScoreInputHandler` (see `last-insertion-keys.md`). Both call
`withPendingOpName` themselves, which is why the save/restore lives on `UndoController`
rather than inside the template.

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
