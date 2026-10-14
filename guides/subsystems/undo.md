# Undo

Read `ModificationSession` first.

## Membership

- `ModificationSession`
- `Mutation`
- `ModificationRule`
- `ModificationRules`
- `TieRule`
- `EndingSectionRule`
- `SongDidChangeNotification`
- `UndoStateDidChangeNotification`
- `UndoController`
- `MutationReplayer`
- `ReplayTarget`
- `FontTarget`
- `OpNames`

## Rules

- Record every state change made inside a modification bracket as a `Mutation`, never by
  writing a collection or field directly, because undo replays the recorded batch and a
  change it did not record is silently missing from the round trip.
- Open a bracket outside the `UIAction` dispatch with `UndoController.withPendingOpName` or the
  labeled `Song.withModification`, because a bracket that opens after dispatch has returned
  finds no name and falls back to the label derived from its dominant record.
- Judge state that no single change can judge in a `ModificationRule`, never in a record
  emitter, because only the close of the outermost bracket sees the whole edit.
- Add a `ModificationRule` only for state that cannot be judged against the projection of its
  own line, because the set is closed and nothing registers a rule from outside the model.
- Read the element selection after `SelectionCoordinator.revalidateElementSelection` has run
  for a notification, never before, because the selection holds positions that the batch has
  shifted.
