# Key Change

Exercises: `KeyChangeDialog`, `KeyChangeDialogController`, `KeyChangeSite`, `KeyCellRenderer`,
`UIUtils.fitPopupToScreen`

## The combo opens on the key in effect

1. Open the dialog on a line whose running key is D major. The combo lists all
   fifteen signatures and shows D major.
2. Open it on a key change standing in the middle of a line. The combo shows the
   key that key change carries.
3. Open it at a position with no key change on it. The combo shows the key inherited
   at that position.

## The popup fits the display

4. Open the combo. The popup fits entirely on screen, clear of the menu bar and
   the Dock, and does not cover the whole display.
5. It scrolls whenever it cannot show all fifteen at once, and opens with the key
   in effect visible without scrolling to it.
6. Drag the dialog to a second monitor of a different height and open the combo
   again. The number of keys shown follows that monitor.

## An unchanged OK writes nothing

7. OK is enabled the moment the dialog opens, and stays enabled whatever is chosen.
8. On a line that already establishes a key of its own, press OK without touching
   the combo. The dialog closes, the score is unchanged, Undo offers no new step,
   and the title bar shows no unsaved-changes mark on an otherwise clean document.
   Check 13 is the one line where this is not so.
9. Reopen it, choose a different key, choose the original back, and press OK. Same
   result: no undo step, no change.
10. Choose a different key and press OK. The score shows that signature and Undo
    offers exactly one new step.

## An existing mid-line key change is changed in place

11. Double-click a key change standing in the middle of a line, choose a
    different key and press OK. That key change carries the new key; no second
    key change and no extra barline appear in front of it, and the notes after it
    read in the new key.
12. Do the same where the line after it inherits its key. That line's header
    changes to the new key as well, and one Undo takes both lines back.

## A line that inherits its key can take one of its own

13. Open the dialog on the header of a line that inherits its key, leave the combo
    on the key it already shows, and press OK. The score is unchanged to look at,
    but Undo offers one new step named *Add Key Change* — the line now establishes that
    key rather than following the line before it. Changing the earlier line's key
    afterwards no longer moves this one.

## A key that strands a mid-line key change removes it

Checks 14–17 open `src/test/resources/fixtures/redundant-key-changes.musicxml`, whose
line 2 is in A♭ and carries a mid-line key change to D♭, and whose line 3 inherits D♭
and carries a mid-line key change to C.

14. Double-click line 2's header and choose D♭. The mid-line D♭ key change and the
    barline in front of it disappear, line 2 reads in D♭ throughout, and line 3's
    header still shows D♭.
15. Undo check 14. The line key, the mid-line key change and its barline all come
    back on one Undo, and Redo puts all three away again.
16. Double-click line 2's mid-line D♭ key change and choose A♭ — the key already in
    effect in front of it. The key change and its barline disappear rather than the
    key change being rewritten, and line 3's header changes to A♭.
17. Write a new key change into line 2's first measure, choosing D♭. It appears
    with a barline of its own, and the D♭ key change that was already further along
    the line disappears with its barline, leaving exactly one D♭ key change on the
    line.
