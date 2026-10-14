# Song Tempo

Read `Tempo` first.

## Membership

- `Tempo`
- `TempoMarking`
- `TempoResolver`
- `TempoChangeAttachment`
- `SongTempoMark`
- `TempoIO`
- `TempoDidChangeNotification`
- `TempoChangeAction`
- `TempoChangeController`
- `TempoChangeDialog`
- `TempoSection`
- `SongTempoMarkRenderer`
- `TempoChangeRenderer`

## Rules

- Make the command that adds a tempo change on the song's first element unavailable, and add no
  guard or first-element tracking to the document model, because tracking would need undo brackets
  that span a transfer between elements and would break on emptied lines and cross-line
  displacement.
