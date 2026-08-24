# Tempo Change

Exercises: `TempoChangeDialog`, `TempoChangeController`

## Opening and lifetime

1. The dialog opens and populates from the current selection.
2. OK commits, and the score reflects it.
3. Opening, populating and committing a second time in the same session behaves as the first.

## An unchanged OK writes nothing

4. Open the dialog on an element that already carries one. OK is enabled, as it is
   throughout.
5. Press OK without touching anything. The dialog closes, the score is unchanged,
   Undo offers no new step, and the title bar shows no unsaved-changes mark on an
   otherwise clean document.
6. Reopen it, change a control, change it back to what the element carries, and
   press OK. Same result: no undo step, no change.
7. Change a control and leave it changed. OK commits, and Undo offers exactly one
   new step.
8. Open the dialog on an element that carries none. Add always writes, since
   anything the controls describe is a change.

## No tuplet is lost to an unchanged commit

9. As for Beat Change: a tempo change carries the beat, so an unchanged commit would
   re-validate the same tuplets. Neither Cancel nor a changed-and-reverted OK may
   drop one or raise the warning.
