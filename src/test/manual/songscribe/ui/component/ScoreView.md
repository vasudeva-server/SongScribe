# Score View

Exercises: `ScoreViewController`, `PasteModeManager`, `Fragment`, `KeyChangeReconciliation`,
`AccidentalReconciliation`

## A key move that strands a mid-line key change

Every check here opens `src/test/resources/fixtures/redundant-key-changes.musicxml`. Its
four lines are: line 1 in C; line 2 in A♭ carrying a mid-line key change to D♭; line 3
inheriting D♭ and carrying a mid-line key change to C; line 4 inheriting C. Both mid-line
key changes are real changes where they stand, and each is left restating what is already
in effect by one of the edits below.

1. Click the staff lines in line 2's clef area to select the whole line, then Delete.
   Line 3 takes line 1's C, and the mid-line C key change it carries disappears together
   with the barline in front of it.
2. Undo check 1. The deleted line, the keys the lines after it went back to, and the
   removed key change and barline all return on one Undo.
3. Select line 2 from its second note through the note after its D♭ key change, Copy,
   deselect, then Paste and click at the end of line 2. Only the two notes land:
   the D♭ key change and its barline are dropped, because D♭ is already running where
   they would have arrived.
4. Select line 3 from its second note through the note after its C key change, Copy,
   deselect, then Paste and click in line 3's first measure. The pasted C key change
   lands with its barline, and line 3's original C key change disappears with the
   barline in front of it, leaving exactly one C key change on the line.
