# Messages Panel

Exercises: `ReportPanel`, `LintCallout`, `LineComponent`, `LineSelectionHandler`

## Showing a finding in the score

Every check here starts from a new song built with these edits, which give one lint mark of
each kind: on line 1, insert a left repeat with no right repeat after it; insert three notes;
give the first note the syllable "la" and leave the second without one; on the third note,
add an annotation reading "D.C. al Fine". Add lines below until line 1 can be scrolled out
of the window. The panel lists three lint rows.

1. Scroll line 1 out of view, then click the repeat's row. The score scrolls until the left
   repeat is in view, and the repeat's tooltip shows with its arrow pointing at the bar,
   reading as a hover on the bar does.
2. Click the missing-lyric row. The tooltip shows with its arrow pointing at the "?" under
   the second note.
3. Click the annotation's row. The tooltip shows with its arrow pointing at the annotation
   text.
4. With a row selected, Shift-click and Command-click another row. Only the clicked row is
   selected, and the tooltip shows for its mark alone.
5. With a row selected and the list focused, press Down and Up. The selection moves one row
   at a time, and the tooltip moves to each newly selected row's mark.
6. With line 1 already in view, click a lint row. The score does not scroll.

## Hiding the tooltip

7. With a row's tooltip showing, scroll the score with the wheel. The tooltip hides and the
   row is deselected.
8. With a row's tooltip showing, zoom the score. The tooltip hides and the row is
   deselected.
9. With a row's tooltip showing, switch to another application. The tooltip hides, and on
   return the row is deselected.
10. With a row's tooltip showing, rest the pointer on a lint mark in the score until its
    hover tooltip appears. The row's tooltip hides, the row is deselected, and only the
    hover tooltip shows.
11. With a row's tooltip showing, hide the panel with its button, then show it again. The
    tooltip hid with the panel, and no row is selected.
12. Click a row, then move the pointer out of the list. The row is deselected and the
    tooltip hides.
13. With a row's tooltip showing, make an edit in the score. The panel rebuilds its rows, no
    row is selected, and no tooltip shows.
14. Delete the end note of a tie, so the panel lists a change row, then select a lint row
    and then the change row. The tooltip hides and the change row stays selected.
