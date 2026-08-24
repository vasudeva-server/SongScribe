# Annotation

Exercises: `AnnotationDialog`, `AnnotationController`

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

## The vertical offset survives an undo

9. On an annotation read from a document that placed it by hand (a non-zero
   `relative-y` in the MusicXML, or a migrated `.mssw`), edit the note it sits on
   and press Undo. The annotation stays where it was placed rather than jumping to
   the layout-calculated position. Redo, then Undo again: still there.

   That an edit through this dialog cannot move it needs no check — an annotation
   carries no position, so there is nothing an edit here could move. Save and
   reload is covered by `MusicXmlAnnotationTest`.
