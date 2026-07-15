# Undo / Redo — Architectural Reference

Background and rationale for the undo/redo engine (issue #14). The implementation
plan lives in `plans/14-undo-redo.md`; this document captures the design
decisions behind it — the "why" an implementer does not need in front of them,
but which explains the shape of the engine after the fact.

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
