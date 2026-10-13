# Messages Panel

Exercises: `ReportPanel`, `LintCallout`, `LintCalloutController`, `LineComponent`,
`LineSelectionHandler`, `LintToolTipPlacement`, `MainFrame`

## Showing a finding in the score

Every check here starts from a new song built with these edits, which give one lint mark of
each kind: on line 1, insert four notes, the third and fourth on the same pitch, and tie the
third to the fourth; insert a left repeat between the first and second notes, with no right
repeat after it; give the first and second notes the syllable "la" and leave the third without
one; on the fourth note, add an annotation reading "D.C. al Fine". Add lines below until line 1
can be scrolled out of the window. The panel lists three lint rows.

1. Scroll line 1 out of view, then click the repeat's row. The score scrolls until all of
   line 1 is in view, and the repeat's tooltip shows below the bar, its left edge on the
   bar's left edge, reading as a hover on the bar does.
2. Click the missing-lyric row. The tooltip shows below the "?" under the third note, its
   left edge on the "?"'s left edge.
3. Click the annotation's row. The tooltip shows below the annotation text, its left edge on
   the text's left edge.
4. With a row selected, Shift-click and Command-click another row. Only the clicked row is
   selected, and the tooltip shows for its mark alone.
5. With a row selected and the list focused, press Down and Up. The selection moves one row
   at a time, and the tooltip moves to each newly selected row's mark.
6. With all of line 1 already in view, click a lint row. The score does not scroll.
7. Scroll until only the top part of line 1 is in view, then click the missing-lyric row.
   The score scrolls until all of line 1 is in view, not only the "?".
32. Zoom in until the annotation is out of view to the right while the start of line 1 is in
    view, then click the annotation's row. The score scrolls right until the annotation is in
    view, with a small space between it and the right edge of the window, and the tooltip
    shows below the annotation, its left edge on the annotation's left edge.

## Hiding the tooltip

In each step that starts with a row's tooltip showing, keep the pointer in the list until the
step is done, because the selection clears when the pointer leaves the list.

8. With a row's tooltip showing, zoom the score with the keyboard. The tooltip hides and the
   row is deselected.
9. With a row's tooltip showing, switch to another application. The tooltip hides, and on
   return the row is deselected.
10. With a row's tooltip showing, press Shift-Command-M to hide the panel, then press it
    again to show the panel. The tooltip hid with the panel, and no row is selected.
11. Click a row, then move the pointer out of the list. The row is deselected and the
    tooltip hides.
12. Click the annotation's row, then press Command-Z, which undoes the annotation. The panel
    lists two lint rows, no row is selected, and no tooltip shows.
13. Insert a rest between the two tied notes, which breaks the tie and makes the panel list
    a change row. Then select a lint row and then the change row. The tooltip hides and the
    change row stays selected.
33. Click the repeat's row, then press Command-Z, which undoes the annotation. The repeat's
    row is still listed, but it is deselected and the tooltip hides.

## Marks in the score

14. The repeat, the missing lyric and the annotation each show a distinct mark, and each
    mark is drawn in the lint color.
15. On a repeat bar that has a lint mark, playing, selecting and hovering each draw the bar
    in their own color instead of the lint color.
16. The "?" of a missing lyric takes no horizontal space: the notes and lyrics around it
    do not move when the mark appears or disappears.
17. Double-clicking the "?" opens the lyric editor on that note's syllable.

## Hover tooltip

18. Resting the pointer on a lint mark shows its tooltip below the mark's item, its left edge
    on the item's left edge. Every hover shows the same standard tooltip.
19. A mark near the bottom of the screen shows its tooltip above the item, its left edge on
    the item's left edge.
20. A mark near the right edge of the screen shows its tooltip pushed left, back onto the
    screen.
21. Rest the pointer on a lint mark until its hover tooltip appears, then zoom the score with
    the keyboard. The hover tooltip hides.
22. Click the second note to select it. Rest the pointer on the "?" under the third note until
    its hover tooltip appears, then press Delete. The hover tooltip hides.

## Save with findings

23. With findings listed, Save shows the confirmation. Choosing "No" writes nothing, and
    the Messages panel keeps its rows and selection.
24. With findings listed, Save As shows the confirmation. Choosing "No" writes nothing,
    and the Messages panel keeps its rows and selection.
25. With findings listed and the song modified, closing the window shows the confirmation
    on the save. Choosing "No" writes nothing, and the Messages panel keeps its rows and
    selection.

## Play and repeats

26. While repeats are unbalanced, Play is disabled and its tooltip gives the reason.

## Panel contents

27. When the song has both change rows and lint rows, the change rows are listed before the
    lint rows.
28. Opening a song whose file has findings shows the panel by itself.

## Start of the song

29. A bar line cannot be placed at the start of the song, and neither can a repeat.
30. Pasting a fragment that starts with a bar line at the start of the song pastes the rest
    of the fragment and lists a change row for the dropped bar line.
31. Deleting the notes before a bar line, so that the bar line is first in the song, flags
    the bar line, and Play is disabled while repeats are on.
