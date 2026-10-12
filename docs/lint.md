# Lint

How the user learns what is wrong with the song as it is now. Some songs are
legal to build but wrong to sing or play: a repeat that nothing opens, a note no
verse gives a syllable, a Fine that nothing sends the player back to. The model
accepts each of these, because the user builds a song one element at a time and
passes through such states on the way. The linter points at them until they are
fixed.

## A lint rule reports and never repairs

A lint rule reads the song and never changes it. That is what separates it from
a rule at the close of an edit ([mutations.md](mutations.md#rules-at-the-close-of-an-edit)),
which repairs the song — removes a tie that no longer reaches a note, an ending
that no longer closes a section — and reports what it changed
([change-report.md](change-report.md)). A lint rule cannot know what the user
meant, so it leaves the fix to the user and says only what is wrong.

The two also differ in time. A change report is an event: what one edit or load
did, replaced by the next. The lint report is a state: every finding for the song
as it is now, replaced whenever the song changes.

## The rules

**Balanced repeats.** Every repeat section is opened once and closed once, by
repeats, and no section opens inside another. The elements are read in song
order across every line; a line break neither opens nor closes a section.

- The song start opens a section, but only for the first right repeat before any
  left repeat or closing bar. In `A :| B :|` the first `:|` plays back to the
  start and the second has nothing to play back to.
- A closing bar that does not play back, such as `||`, ends the open section,
  the song-start section included. In `A || B :|` the `:|` has no opener.
- A `:|:` closes the open section as a right repeat, then opens a new one.
  `|: A :|: B :|` is balanced; in `|: A :|: B` the `:|:` opens a section that
  nothing closes.
- A left repeat inside an open section is nested. In `|: |: :| :|` the second
  `|:` is flagged, and the two right repeats are taken to close the inner and the
  outer section.
- A left repeat whose section a closing bar or the end of the song ends first is
  not closed.

These semantics agree with how playback finds where a right repeat loops back
to.

**Missing lyric.** In the active verse, every slot is sung on some syllable. A slot is an element a syllable can go on: a note or a grace note, but
not a note whose paired grace note in front of it takes its syllable. A slot is
sung when it holds a lyric for the verse, when a lyric span runs over it — a
melisma, or a word whose next syllable stands on a later note, crossing a line
break if it must and stopping at a repeat ([lyrics.md](lyrics.md)) — or when a
tie from the note before it reaches it, so it continues a note already sung.
Only the active verse is checked — after an edit, on a save, and on a load —
because the score shows and edits that verse alone: a finding in another verse
would have nothing to point at and no way to be fixed. A verse exists only as the
verse number its lyrics carry, so the check starts with the first syllable of the
active verse; until then the song has no finding.

**Unbalanced Fine.** A *Fine* is an annotation whose whole text is "fine" in any
case. A *reference* is any other annotation that holds "fine" as a whole word, in
any case: `D.C. al Fine`, `D.C. al fine` and `al Fine.` are references, `Finest`
is not. A Fine must stand on a bar, must not stand on the last element of the
song — the song ends there anyway — and must have a reference after it. A
reference must have a Fine before it. One reference answers every Fine before it.

## One linter, run at once

One linter for the process owns the lint report of the open document. The UI
asks it for the report and repaints when it announces a new one; the linter knows
nothing of the UI.

A run happens at once after every edit, undo and redo, and whenever a document
is installed — a new song, or one just loaded, linted before anything is told of
the load. So the report is current whenever anything reads it, and nothing ever
asks whether it is stale. Only the document's song is linted: a song being read
from a file is never announced as changed.

A run applies every rule in a fixed order, and a new rule runs with no change to
the linter. The report lists the findings by rule, and within a rule in song
order.

## Every finding is an error

There is no severity. Every finding is an error, and what it blocks comes from
its rule's flags:

- Every rule blocks a save. Save, Save As, and the save offered when a modified
  document is closed are each refused while a finding stands: an alert says why,
  the Messages panel opens, and no file is written, so a close or a quit is
  canceled.
- The balanced-repeats rule also disables Play while playback with repeats is on,
  because the repeats cannot be followed. Play's tooltip then gives the reason.
  With repeats off, Play is not affected, and a running playback can always be
  stopped. The repeats setting cannot change while playback runs, so a running
  playback never starts following repeats the rule has found broken.

## How a finding is shown

**In the score.** Each finding says what the score draws for it, in the color the
score uses for anything wrong with the document:

- an offending element — a repeat bar — is drawn in that color;
- an offending annotation — a Fine or a reference — gets a sawtooth squiggle
  under its text, just below the baseline. The text and its element keep their
  own colors;
- a slot missing a lyric gets a "?" in the lyric font, in the lyric row, exactly
  where a syllable on that slot would stand. It takes no space: a "?" is never
  wider than a note, so the line is spaced as if the slot had no syllable, and
  whether an edit fits never depends on a finding. The pointer treats a "?" as lyric text: no note preview shows
  over it, and a double-click on it opens the lyric editor on that slot.

A squiggle and a "?" are always in the lint color. On an offending element, playing,
selection and hover each win over the lint color, in that order. The
selection wins because it is what the next Delete removes, and the user must see
exactly that.

**In the Messages panel.** Lint findings are listed after the changes of the
latest edit or load, in song order, each under a heading that starts with ❗
([change-report.md](change-report.md#the-panel-shows-lint-findings-too)). Each
finding reads as its heading, the line and element it names, and its
description. The rule that found it writes both the heading and the
description, and chooses the heading for each finding, so one rule can list
different kinds of breach under different headings. The panel never interprets
a finding.

**From the panel to the score.** One row at a time can be selected. Selecting a
finding's row scrolls the score, when it must, until the mark's item is in view,
and shows the mark's tooltip there, as a hover on it would. The tooltip hides
when the selection clears or moves to a change row. It also hides on its own
when the score scrolls or zooms, when the window loses focus, and when a hover
tooltip appears in the score, and then the row is deselected, so a selected
row always has its tooltip on screen. Hiding the panel clears its selection, and
so does a new report, because the panel rebuilds its rows.

**A tooltip on the mark.** While the pointer rests on a mark, the tooltip every
toolbar button uses appears, after the same delay, under the item the pointer is
on, and stays until the pointer leaves that item or presses a button. An arrow on
its edge points at the item's center. The arrow stands near the tooltip's left
end, and it moves along the edge when the tooltip is pushed sideways to stay on
the screen. Where the tooltip does not fit below the item, it goes above, with
the arrow on its bottom edge. It reads as the panel's rows for that mark do — the heading over
the description, one pair per finding — but without the ❗ and without the line
and element, since the pointer already shows where the finding is.

## A finding names elements

A finding names the element it was found on, as the element itself, not as a
position. Between an edit and the next run it therefore stays on its element, even
when the edit moved that element. A position is worked out only when the panel
shows the finding; a finding whose element the edit removed is not listed, and
the run that follows the edit replaces it.
