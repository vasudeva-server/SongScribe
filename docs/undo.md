# Undo and Redo

What survives a step being replayed, and why the replay is arranged the way it
is. For how a batch of changes is recorded in the first place, see
[mutations.md](mutations.md).

## Element identity survives

Undoing or redoing a *modification* never swaps one element instance for another.
The recorded before-state and after-state are copied back **in place**.

That is the property everything else rests on. Spans on the line, the current
selection, and the records still sitting on either stack all hold references to
elements, and they stay valid across arbitrary interleaving of undo and redo
because no instance is ever replaced underneath them. A replacement-based design
would invalidate all three on every step and need each of them to re-resolve.

## The selection follows the edit

Replaying a step mutates the line under whatever the user has selected, and a
selection names elements by position. So the selected range is spliced through
each change in the batch as it replays — sliding or shrinking, and clearing only
when nothing of it survives.

What makes that safe for *other* readers is ordering: the splice runs ahead of
every other reader of the range, so no one ever sees positions that no longer
exist. A subscriber that outranks it and reads the selection is outside the
guarantee.

## Replay runs inside an ordinary bracket

A replay opens a normal modification bracket rather than suspending recording.
Two things follow, both wanted: the resulting batch is delivered like any other,
so views re-lay-out and repaint with no special case; and the inverse changes are
recorded into it, so what is delivered faithfully describes what changed.

The batch a replay produces is kept off the undo stack by a reentrancy guard —
not by suppressing recording, which would cost the repaint. Replay mode is a
separate, orthogonal thing: it stops the model doing companion side-work the
recorded batch already accounts for. See [mutations.md](mutations.md).

## Modified is a position, not a comparison

A document is modified exactly when its position in the history differs from the
position it held at the last save. This is not a comparison of content: editing
and then undoing back returns the document to clean, while arriving at an
identical document by a different route does not.

Two things end that, both deliberately:

- Beyond a bounded history depth the oldest step is evicted. If the evicted step
  was the saved position, the document can no longer reach clean and stays
  modified — it has no way to know it got back.
- A replay that throws is an engine fault, and it leaves the model mid-step,
  matching neither stack. The history is cleared and the document forced
  modified, so the user can still save their work but cannot undo further into a
  state that can no longer be accounted for.

## Where a step's name comes from

The Edit menu names the operation rather than showing a bare "Undo", and the name
is declared by whoever initiated the edit.

The ordinary route is the action template: it runs the action's own work inside a
window during which any bracket opened **synchronously** picks up the pending
name. Modal dialogs block, so they qualify. Anything reaching a bracket off the
stack — queued work, a timer, a background task, a non-modal dialog — opens after
that window has closed, and so declares its name directly instead.

Two paths reach a bracket without going through the template at all: placing a
paste, which is driven by a click rather than by a menu command, and the
last-insertion keys, which are posted straight from the input handler. Both
declare their own name, which is why the save-and-restore of the pending name
lives with the undo engine rather than inside the action template.

Where nothing declares a name, one is derived from the kind of edit the batch's
dominant record describes.

## Why not the toolkit's undo framework

The stack mechanics are the only part Swing's undo framework would replace, and
they are a few lines. Everything hard is specific to this program and would be
required regardless: inverting each kind of record, replaying under the model's
replay mode, tracking the save position by history position, naming a step from
its dominant record, and integrating with this program's message bus rather than
the toolkit's listener model. Wrapping each batch as a toolkit edit would add an
adapter layer without removing any of that.
