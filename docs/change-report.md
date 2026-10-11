# The Change Report

How the user learns what the song changed on its own. An edit can break more
than it touches — a deleted note takes a beam with it, a retyped bar strands an
ending, a rest put between two tied notes breaks the tie — and a file can hold content
the model will not accept. In every such case the model makes the change, and
the report tells the user afterwards what it made and why.

For how the changes themselves are decided, see
[span-invalidation.md](span-invalidation.md) and
[mutations.md](mutations.md#rules-at-the-close-of-an-edit).

## One way to tell the user

The report is the one way an automatic change reaches the user. No edit asks
for consent before it removes something, and no kind of change has an alert of
its own. The edit goes ahead, the report follows it, and undo reverses the edit
and everything it did on its own in one step. So there is one place that decides
how a change reads, and one panel the user learns to read. The panel sits at the
right side of the main window, where the user can keep it open beside the score.

A report is a list of entries in the order they were recorded. Each entry names
the **kind** of change — beams removed, tuplets updated, accidentals converted —
gives a **description** of why, and says **where**: the line it happened on, or
two lines when it crosses a line break, as a tie does. A change with no place in
the song, such as a conversion applied to the whole file, has no location.

An entry says where by line, never by element. It describes something that is
gone — a span removed, a mark dropped — so there is no element left to point at,
and the elements around it may have moved by the time the user reads it.

The reporter records the line itself, not its number. The number is fixed only
when the edit or load is over, so it describes the song as the edit left it: a
line added or removed in front of the recorded one later in the same edit does
not make the entry wrong. A finished report holds numbers only, and no part of
the model.

## The reporter writes the words

The code that makes a change builds its entry, text and all. It is the only code
that knows what happened: a span's own rule knows whether an endpoint was deleted
or the notes inside it changed, the tuplet pass knows which ratio it derived, the
ending check knows which bar broke it. Neither the report nor the panel knows
any of that. The panel shows each entry as one item: its kind's heading, its
location, and its description. A new kind of change therefore needs no change to
the panel — only an entry recorded where the change is made.

The one thing the panel does add is presentation of the location: entries hold
lines as the model counts them, and the panel alone turns them into the line
numbers the user reads.

## What reports into it

Every removal of a span or a tie that the user did not directly request, made
while tracking is on, is reported. A removal the user asked for — deleting a
selected span, deleting every element a span covers, toggling a beam or a tie
off, deleting the note a tie ends on — is not, since the user already knows it
happened. A change made while tracking is suspended, as it is for a load,
reports only what a load pass or the reader records into the load's report;
anything else it removes goes unreported, as below.

- **The funnel.** Each span an element change leaves invalid is removed with the
  reason the span's rule gives. A span whose elements a deletion takes whole is
  not among them: it goes with its elements, unreported.
- **Displacements.** A span replaced by a new one of the same kind added over it
  is reported as replaced.
- **The rules at the close of an edit.** Each tie that no longer reaches a note
  it can end on, or whose notes no longer sound the same pitch; each beam that an
  edit leaves holding both notes of a tie (`TieRule`), since a beam never holds a
  tied pair; and each ending whose split no longer closes an open repeat section.
- **Paste.** A span of the target the paste lands inside is removed; a span of
  the pasted fragment that would land inside one of the same kind is not pasted;
  a pasted tuplet, beam, ending or hairpin that is not valid where it lands is
  not pasted — a beam that would hold both notes of a tie among them, which
  `Beam.spanning` refuses. Each is reported, though a span that was not pasted was
  never in the song.
- **The load passes.** A tuplet the file did not time is given a ratio, and one
  that cannot stand is dropped; an ending that is not valid where the file puts
  it is dropped; a hairpin that is not valid where the file puts it is dropped; a
  breath mark that follows no note or rest is dropped; accidentals
  written in an older form are converted. Dropping a breath mark can take a
  tuplet that holds it with it, and that removal is not reported, because it
  happens under suspended tracking through the funnel rather than in a pass.
- **The MusicXML reader.** A damaged span mark in a file of ours — a start or stop
  that does not pair with its partner on one line, a wedge bound to no note, a
  tuplet whose ratio cannot be read, or a tie, beam or slide mark on an element
  that cannot carry it — is dropped, keeping its notes, with one entry per drop.
  The rule is in [musicxml-object-model.md](musicxml-object-model.md).

A stranded key change — a mid-line key change that restates the key already in
effect — is removed without a report. It carries no information the document
does not already hold, so there is nothing to tell. A span absorbed into a
neighbor of the same kind — two hairpins or two beams merged into one — goes
unreported for the same reason: the merged span covers every element it did.

## Two routes to one panel

An edit and a load reach the same panel by different routes, because they run
under different recording modes. In both, the report travels with the
notification that announces the change, and a finished report never changes: only
a collector records into one.

**An edit** runs inside a modification bracket. The outermost bracket holds the
edit's report; every reporter records into it through the song, wherever in the
edit it runs, and the report is delivered once for the whole edit, with its
change notification (`ModificationSession.deliver`). The report is empty when the
edit changed nothing on its own. The panel replaces its change entries with the
report after every edit, so they always show the most recent change to the song,
and an empty report clears them. The panel never opens by itself after an edit;
the user keeps it open if they want to watch it.

**Undo and redo** each deliver an empty report. A replay re-applies what the edit
recorded and judges nothing: no rule at the close of an edit runs, and no
companion work that could remove something runs, so nothing records. After an
undo or a redo the panel shows no change entries. A change made while tracking is
suspended records nothing into an edit's report, since there is no edit;
[above](#what-reports-into-it) says what a load reports instead.

**A load** runs with tracking suspended, so there is no bracket to record into.
The reader holds no report either: the entries it makes travel out of it beside the
song it read. The load passes are handed a report of their own, run in order over
the freshly read song, and the reader's entries are recorded after theirs; the
finished report travels with the load notification.
When it is not empty, the song is marked modified — the user should save the
repaired version. The panel opens by itself when the load's report is not empty
or the loaded song has lint findings; otherwise it stays as the user had it.

## The panel shows lint findings too

The panel lists two things with different lifetimes. The change entries are an
event: each edit, undo, redo or load replaces them. The lint findings
([lint.md](lint.md)) are a state: everything wrong with the song as it is now,
replaced after every lint run. An update to one never clears the other, so an
edit somewhere else keeps the lint findings in view.

Changes come first, under headings that start with 💬; lint findings follow,
under headings that start with ❗. Neither kind of heading is drawn in a color of
its own. Within each kind, the items are in song order: by line, then by
element. A change names lines only, so it sorts at the start of its first line,
and a change with no place sorts before every line. A lint finding's location is
the line and element it names, numbered when the panel shows it.

## Showing the panel

The Window menu's Messages item shows the panel when it is hidden and hides it
when it is shown, and is checked while the panel is shown. Showing the panel
leaves keyboard focus where it was. The list takes focus only when the user
clicks it; hiding the panel while the list has focus moves focus to the score.
