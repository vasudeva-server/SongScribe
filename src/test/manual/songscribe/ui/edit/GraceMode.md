# Grace Mode

Exercises: `GraceModeManager`, `PreviewElementInserter`, `AcceptedInsertion`,
`PreviewElementManager`

## Entry beside a grace-host pair

Every check here starts from a line that holds a grace note paired with the note after it,
with a grace-note action selected.

1. Press in front of the paired grace note. A new grace note appears there and grace entry
   starts. Click to place its host: the new grace note and the new host form a second pair
   in front of the first, and the first pair is unchanged.
2. Move the pointer over the slot between the paired grace note and its host. No insertion
   marker appears there, and a press there inserts nothing.

## A host with no room

3. On a line with room for a grace note but not for the host after it, press to place a
   grace note, then click to place its host. The interaction ends, the grace note disappears, and Edit > Undo shows
   no new step.
