# Score View

Exercises: `ScoreViewController`, `PasteModeManager`, `Fragment`, `KeyChangeReconciliation`,
`AccidentalReconciliation`

## A key move that strands a mid-line signature

Every check here opens `src/test/resources/fixtures/redundant-key-changes.musicxml`. Its
four lines are: line 1 in C; line 2 in A♭ carrying a mid-line signature to D♭; line 3
inheriting D♭ and carrying a mid-line signature to C; line 4 inheriting C. Both mid-line
signatures are real changes where they stand, and each is left restating what is already
in effect by one of the edits below.

1. Click the staff lines in line 2's clef area to select the whole line, then Delete.
   Line 3 takes line 1's C, and the mid-line C signature it carries disappears together
   with the barline in front of it.
2. Undo check 1. The deleted line, the keys the lines after it went back to, and the
   removed signature and barline all return on one Undo.
3. Select line 2 from its second note through the note after its D♭ signature, Copy,
   deselect, then Paste and click at the end of line 2. Only the two notes land:
   the D♭ signature and its barline are dropped, because D♭ is already running where
   they would have arrived.
4. Select line 3 from its second note through the note after its C signature, Copy,
   deselect, then Paste and click in line 3's first measure. The pasted C signature
   lands with its barline, and line 3's original C signature disappears with the
   barline in front of it, leaving exactly one C signature on the line.
