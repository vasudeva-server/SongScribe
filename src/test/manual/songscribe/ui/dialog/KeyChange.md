# Key Change

Exercises: `KeyChangeDialog`, `KeyChangeDialogController`, `KeyChangeSite`, `KeyCellRenderer`

## The combo opens on the key in effect

1. Open the dialog on a line whose running key is D major. The combo lists all
   fifteen signatures and shows D major.
2. Open it on a key signature standing in the middle of a line. The combo shows the
   key that signature carries.
3. Open it at a position with no signature on it. The combo shows the key inherited
   at that position.

## An unchanged OK writes nothing

4. OK is enabled the moment the dialog opens, and stays enabled whatever is chosen.
5. On a line that already establishes a key of its own, press OK without touching
   the combo. The dialog closes, the score is unchanged, Undo offers no new step,
   and the title bar shows no unsaved-changes mark on an otherwise clean document.
   Check 10 is the one line where this is not so.
6. Reopen it, choose a different key, choose the original back, and press OK. Same
   result: no undo step, no change.
7. Choose a different key and press OK. The score shows that signature and Undo
   offers exactly one new step.

## An existing mid-line signature is changed in place

8. Double-click a key signature standing in the middle of a line, choose a
   different key and press OK. That signature carries the new key; no second
   signature and no extra barline appear in front of it, and the notes after it
   read in the new key.
9. Do the same where the line after it inherits its key. That line's header
   changes to the new key as well, and one Undo takes both lines back.

## A line that inherits its key can take one of its own

10. Open the dialog on the header of a line that inherits its key, leave the combo
    on the key it already shows, and press OK. The score is unchanged to look at,
    but Undo offers one new step named *Add Key Change* — the line now establishes that
    key rather than following the line before it. Changing the earlier line's key
    afterwards no longer moves this one.
